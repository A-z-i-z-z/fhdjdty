package dev.pocketagent.app.routines

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.data.BotPresets
import dev.pocketagent.app.data.BudgetMode
import dev.pocketagent.app.data.EngineEvent
import dev.pocketagent.app.data.Role
import dev.pocketagent.app.diag.Diag
import dev.pocketagent.app.runtime.RuntimeAwait
import dev.pocketagent.app.runtime.RuntimeState
import dev.pocketagent.app.service.AppForeground
import dev.pocketagent.app.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import dev.pocketagent.app.ui.text.Texts

/**
 * Runs the [Routine]s. One exact alarm (`RTC_WAKEUP`, allowed while idle) is kept for the earliest next run; when it rings,
 * [RoutineAlarmReceiver] calls [onAlarm], which sends the prompt of every due routine into that routine's own chat through
 * the [dev.pocketagent.app.data.ChatEngine] (the engine wakes Hermes when it sleeps) and sets the next alarm. The answer reaches
 * the user through the reply notification and the unread badge.
 *
 * Works when the process was dead: `PocketApp.onCreate` has run, chat data may still be loading ([dev.pocketagent.app.data.ChatRepository.loaded]);
 * a run waits for it. Nothing here reads [app] members in a property initialiser (they may not exist yet when this is built).
 *
 * If a run had to wake Hermes while the app was in the background, Hermes is stopped again once nothing is busy; that is not
 * a user stop, so the auto-start of the next app launch is unaffected.
 */
class RoutineScheduler(private val app: PocketApp) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _nextRunAt = MutableStateFlow<Long?>(null)
    private val observing = AtomicBoolean(false)
    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val activeRuns = AtomicInteger(0)
    @Volatile private var wokeHermes = false

    /** The earliest next run over all enabled routines (null when none is scheduled). */
    val nextRunAt: StateFlow<Long?> = _nextRunAt

    // ---- alarm ---------------------------------------------------------------------------------------------------

    /** Recomputes the earliest run and sets (or cancels) the one alarm. Cheap; safe to call from anywhere, any time. */
    fun rescheduleAll() {
        ensureObserving()
        try { arm() } catch (e: Throwable) { AppLog.e("Routines", "could not set the routine alarm", e) }
    }

    private fun ensureObserving() {
        if (!observing.compareAndSet(false, true)) return
        scope.launch {
            // Any change to the routines (the editor, a finished run) moves the alarm; the short delay folds bursts together.
            app.routines.routines.collectLatest {
                delay(250)
                try { arm() } catch (e: Throwable) { AppLog.e("Routines", "could not set the routine alarm", e) }
            }
        }
    }

    private fun arm() {
        val now = System.currentTimeMillis()
        val list = app.routines.routines.value
        _nextRunAt.value = RoutineSchedule.earliestNext(list, now)
        val at = RoutineSchedule.alarmAt(list, now)
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = alarmIntent(app)
        if (at == null) { am.cancel(pi); return }
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            // Exact alarms are allowed at targetSdk 28; if a future system disagrees, an inexact one is better than none.
            AppLog.w("Routines", "exact alarm refused, using an inexact one", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    // ---- running -------------------------------------------------------------------------------------------------

    /**
     * Called by [RoutineAlarmReceiver] (and by tests of the wiring): runs every routine that is due and sets the next alarm.
     * [done] is called once the prompts have been handed to the engine (at most about 8 seconds later), so the receiver can
     * finish its `goAsync()` window.
     */
    fun onAlarm(done: () -> Unit = {}) {
        val finished = AtomicBoolean(false)
        fun finish() {
            if (finished.compareAndSet(false, true)) {
                try { done() } catch (e: Throwable) { AppLog.w("Routines", "done callback failed", e) }
            }
        }
        scope.launch {
            val guard = launch { delay(HANDOFF_LIMIT_MS); finish() }
            try {
                val now = System.currentTimeMillis()
                val handoffs = RoutineSchedule.due(app.routines.routines.value, now).map { startRun(it, manual = false) }
                if (handoffs.isNotEmpty()) withTimeoutOrNull(HANDOFF_LIMIT_MS - 1_000L) { handoffs.forEach { it.await() } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                AppLog.e("Routines", "alarm handling failed", e)
            } finally {
                try { arm() } catch (e: Throwable) { AppLog.e("Routines", "could not set the routine alarm", e) }
                guard.cancel()
                finish()
            }
        }
    }

    /** From the UI: runs routine [id] now, whatever its schedule says. */
    fun runNow(id: String) {
        val r = app.routines.get(id) ?: return
        startRun(DueRun(r, System.currentTimeMillis(), 0L, false), manual = true)
    }

    /** What a run that was handed to the engine leaves behind for [watch]. */
    private class Accepted(
        val routineId: String, val convId: String,
        val failedSeen: AtomicBoolean, val events: Job,
    )

    /** Starts one run; the result completes when the prompt has been handed to the engine (or the run gave up). */
    private fun startRun(d: DueRun, manual: Boolean): CompletableDeferred<Unit> {
        val handoff = CompletableDeferred<Unit>()
        val id = d.routine.id
        if (!inFlight.add(id)) {
            // The previous run of this routine is still being watched. A scheduled slot that falls due meanwhile is used up
            // (like the engine's "busy" skip): without this the slot would stay due and the alarm would ring again and
            // again until the earlier run ends. Manual taps just do nothing.
            if (!manual) consumeSlotWhileRunning(id)
            handoff.complete(Unit)
            return handoff
        }
        activeRuns.incrementAndGet()
        scope.launch {
            try {
                var accepted: Accepted? = null
                app.wakeLocks.acquire("routine:$id", LOCK_MS)
                try {
                    accepted = prepareAndSend(d, manual)
                } finally {
                    app.wakeLocks.release("routine:$id")
                    handoff.complete(Unit)
                }
                accepted?.let { watch(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                AppLog.e("Routines", "run of routine $id failed", e)
                setStatus(id, Routine.STATUS_FAILED)
            } finally {
                inFlight.remove(id)
                if (activeRuns.decrementAndGet() == 0 && wokeHermes) {
                    wokeHermes = false
                    scope.launch { stopIfIdle() }
                }
            }
        }
        return handoff
    }

    /**
     * A scheduled slot came due while the routine's previous run is still in flight: count the slot as used (the baseline
     * moves to now) so [RoutineSchedule.dueRun] stops reporting it and the alarm moves on to the next real slot.
     * The running run keeps its own status. A one-time routine stays enabled with no next run, which the list shows as "Done".
     */
    private fun consumeSlotWhileRunning(id: String) {
        val r = app.routines.get(id) ?: return
        app.routines.save(r.copy(armedAt = System.currentTimeMillis()))
        AppLog.i("Routines", "routine $id came due while its previous run is still answering: slot skipped")
    }

    /** Deletes routine [id] and releases its chat (an ordinary chat again), then moves the alarm. */
    fun deleteRoutine(id: String) {
        val r = app.routines.get(id)
        if (r != null && r.convId.isNotEmpty()) {
            try {
                // Only when the chat still points at this routine (it may have been reassigned since).
                if (app.chat.conversation(r.convId)?.routineId == id) app.chat.setRoutine(r.convId, "")
            } catch (e: Throwable) {
                AppLog.w("Routines", "could not release the chat of deleted routine $id", e)
            }
        }
        app.routines.delete(id)
        rescheduleAll()
    }

    /** Marks the run, makes sure the chat exists, wakes Hermes if needed and sends. Null when nothing was sent. */
    private suspend fun prepareAndSend(d: DueRun, manual: Boolean): Accepted? {
        val store = app.routines
        val now = System.currentTimeMillis()
        val r0 = store.get(d.routine.id) ?: return null     // deleted in the meantime
        val onceDone = !manual && r0.kind == RoutineKind.ONCE
        if (!manual && d.tooLate) {
            // Too late to be useful: skip it and count from now on. The notification would only add noise.
            store.save(r0.copy(lastStatus = Routine.STATUS_SKIPPED, armedAt = now, enabled = if (onceDone) false else r0.enabled))
            AppLog.i("Routines", "routine ${r0.id} skipped: ${d.lateMs / 60_000} min late")
            return null
        }
        // Count the run first: whatever happens next, this slot is used up and the alarm moves on.
        store.save(r0.copy(lastRunAt = now, lastStatus = "", enabled = if (onceDone) false else r0.enabled))
        val r = store.get(r0.id) ?: return null
        if (r.prompt.isBlank()) { fail(r, Texts.get(app, R.string.rtn_fail_empty)); return null }

        val loaded = withTimeoutOrNull(CHAT_LOAD_MS) { app.chat.loaded.first { it } }
        if (loaded == null) { fail(r, Texts.get(app, R.string.rtn_fail_chats)); return null }

        if (app.usage.budget.value.mode == BudgetMode.BLOCK && app.usage.overBudget()) {
            setStatus(r.id, Routine.STATUS_SKIPPED)
            notifyFailure(r, Texts.get(app, R.string.rtn_fail_budget))
            return null
        }

        val convId = ensureConversation(r)
        val rt = app.runtime
        if (rt.state.value != RuntimeState.Running) {
            if (!rt.isRootfsValid()) { fail(r, Texts.get(app, R.string.rtn_fail_not_installed)); return null }
            wokeHermes = true
            if (!app.presencePrefs.settings.value.wakeOnSend) {
                // The engine will not wake Hermes on its own; do it here.
                val up = withContext(Dispatchers.IO) { RuntimeAwait.awaitRunning(rt, START_TIMEOUT_MS) { false } }
                if (!up) { fail(r, Texts.get(app, R.string.rtn_fail_start)); return null }
            }
        }

        val failedSeen = AtomicBoolean(false)
        // Subscribe before sending so a quick failure cannot slip by.
        val events = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            app.engine.events.collect { ev -> if (ev is EngineEvent.TurnFailed && ev.convId == convId) failedSeen.set(true) }
        }
        val accepted = app.engine.send(convId, r.prompt)
        if (!accepted) {
            events.cancel()
            if (convId in app.engine.busy.value) {
                setStatus(r.id, Routine.STATUS_SKIPPED)   // the previous run of this routine is still answering
            } else {
                fail(r, Texts.get(app, R.string.rtn_fail_send))
            }
            return null
        }
        AppLog.i("Routines", "routine ${r.id} sent to chat $convId")
        return Accepted(r.id, convId, failedSeen, events)
    }

    /** The routine's own chat: created on the first run, kept in step with the routine's bot, model and effort afterwards. */
    private fun ensureConversation(r: Routine): String {
        val chat = app.chat
        val bot = chat.bot(r.botId) ?: chat.bot(BotPresets.HERMES_ID)
        val botIds = listOf(bot?.id ?: BotPresets.HERMES_ID)
        val existing = if (r.convId.isNotEmpty()) chat.conversation(r.convId) else null
        val conv = existing ?: chat.createConversation(botIds, r.name.ifBlank { Texts.get(app, R.string.rtn_default_chat_title) })
        if (existing != null && existing.botIds != botIds) chat.setConversationBots(existing.id, botIds)
        if (conv.routineId != r.id) chat.setRoutine(conv.id, r.id)
        chat.setOverrides(conv.id, r.model, r.effort)
        if (conv.id != r.convId) app.routines.get(r.id)?.let { app.routines.save(it.copy(convId = conv.id)) }
        return conv.id
    }

    /** Waits for the turn to end, then records ok / failed and stops Hermes again if this run woke it. */
    private suspend fun watch(a: Accepted) {
        val ended = withTimeoutOrNull(WATCH_LIMIT_MS) { app.engine.busy.first { a.convId !in it } } != null
        a.events.cancel()
        if (!ended) return
        val tail = withContext(Dispatchers.IO) {
            val msgs = app.chat.messagesSnapshot(a.convId)
            val idx = msgs.indexOfLast { it.role == Role.USER }
            if (idx < 0) msgs else msgs.drop(idx + 1)
        }
        val lastError = tail.lastOrNull { it.role == Role.ERROR }
        val failed = lastError != null || tail.none { it.role == Role.BOT && it.text.isNotBlank() }
        setStatus(a.routineId, if (failed) Routine.STATUS_FAILED else Routine.STATUS_OK)
        if (failed && !a.failedSeen.get()) {
            val r = app.routines.get(a.routineId)
            if (r != null) notifyFailure(r, lastError?.text ?: Texts.get(app, R.string.rtn_fail_no_answer))
        }
    }

    private suspend fun stopIfIdle() {
        delay(STOP_GRACE_MS)   // a late event (notification, speech) of the last turn must not race the stop
        if (AppForeground.isForeground.value) return
        if (app.engine.busy.value.isNotEmpty() || app.terminal.running.value) return
        if (app.runtime.state.value != RuntimeState.Running) return
        AppLog.i("Routines", "routine run is over and the app is in the background: stopping Hermes again")
        app.runtime.stop()
    }

    private fun setStatus(id: String, status: String) {
        app.routines.get(id)?.let { if (it.lastStatus != status) app.routines.save(it.copy(lastStatus = status)) }
    }

    private fun fail(r: Routine, reason: String) {
        setStatus(r.id, Routine.STATUS_FAILED)
        notifyFailure(r, reason)
    }

    private fun notifyFailure(r: Routine, reason: String) {
        try {
            val name = r.name.ifBlank { Texts.get(app, R.string.rtn_default_chat_title) }
            app.presence.notifier.postRoutineFailure(name, Diag.redactor.redact(reason).take(200))
        } catch (e: Throwable) {
            AppLog.w("Routines", "could not post the failure notification", e)
        }
    }

    companion object {
        const val ACTION_ALARM = "dev.pocketagent.app.ROUTINE_ALARM"
        private const val ALARM_REQUEST_CODE = 7101
        /** goAsync() gives a receiver about 10 seconds; the hand-over must be done well before. */
        private const val HANDOFF_LIMIT_MS = 8_000L
        private const val LOCK_MS = 10 * 60_000L
        private const val CHAT_LOAD_MS = 30_000L
        private const val START_TIMEOUT_MS = 6 * 60_000L
        private const val WATCH_LIMIT_MS = 45 * 60_000L
        private const val STOP_GRACE_MS = 3_000L

        /** The one immutable PendingIntent of the routine alarm (the same object for set and cancel). */
        internal fun alarmIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context, ALARM_REQUEST_CODE,
                Intent(context, RoutineAlarmReceiver::class.java).setAction(ACTION_ALARM),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
