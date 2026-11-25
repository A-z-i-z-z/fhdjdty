package dev.pocketagent.app.service

import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.data.EngineEvent
import dev.pocketagent.app.data.PendingApproval
import dev.pocketagent.app.runtime.ProviderConfig
import dev.pocketagent.app.runtime.RuntimeState
import dev.pocketagent.app.ui.chat.markdownToPlain
import dev.pocketagent.app.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * The pure decisions of [Presence]: no Android objects, so they run in a JVM unit test (`PresenceLogicTest`).
 */
object PresenceLogic {
    const val MAX_REPLY_CHARS = 4000
    const val PREVIEW_CHARS = 300
    const val MAX_CONV_ID_CHARS = 200
    const val RESTART_DELAY_MS = 2_000L
    const val IDLE_CHECK_MS = 30_000L
    const val MAX_AUTO_RESTARTS = 2
    const val RESTART_WINDOW_MS = 10 * 60_000L

    /** Choices a pending approval offers when Hermes did not list any (the same fallback the approval card uses). */
    val DEFAULT_APPROVAL_CHOICES = listOf("once", "deny")

    /**
     * Light the fire when the app comes to the foreground: only once per process ([alreadyHandled]), only when the user has not
     * stopped Hermes in this process, the rootfs is installed, the runtime is `Stopped` (never an Error, never a fresh
     * install) and the saved provider setup has no problems.
     */
    fun shouldAutoStart(
        autoStart: Boolean, alreadyHandled: Boolean, userStopped: Boolean,
        rootfsValid: Boolean, state: RuntimeState, setupComplete: Boolean,
    ): Boolean = autoStart && !alreadyHandled && !userStopped && rootfsValid && state is RuntimeState.Stopped && setupComplete

    /** A reply or approval is worth a notification unless the user is looking at that very chat right now. */
    fun shouldNotify(enabled: Boolean, appInForeground: Boolean, activeConvId: String?, convId: String): Boolean =
        enabled && (!appInForeground || activeConvId != convId)

    /** Plain one-paragraph preview of a reply (Markdown stripped) of at most [PREVIEW_CHARS] characters. */
    fun preview(text: String): String {
        val plain = markdownToPlain(text, PREVIEW_CHARS + 1)
        return if (plain.length > PREVIEW_CHARS) plain.take(PREVIEW_CHARS - 1).trimEnd() + "…" else plain
    }

    /** Notification title: the bot's name; group chats "bot · chat title"; [untitled] when there is nothing else. */
    fun titleFor(botName: String?, convTitle: String, isGroup: Boolean, untitled: String): String {
        val name = botName?.trim().orEmpty()
        val title = convTitle.trim()
        return when {
            isGroup && name.isNotEmpty() && title.isNotEmpty() -> "$name · $title"
            name.isNotEmpty() -> name
            title.isNotEmpty() -> title
            else -> untitled
        }
    }

    fun validConvId(convId: String?): Boolean = !convId.isNullOrBlank() && convId.length <= MAX_CONV_ID_CHARS

    /** The text of an inline reply: trimmed, null when blank, cut at [MAX_REPLY_CHARS] (never in the middle of a surrogate pair). */
    fun cleanReply(raw: CharSequence?): String? {
        val t = raw?.toString()?.trim().orEmpty()
        if (t.isEmpty()) return null
        if (t.length <= MAX_REPLY_CHARS) return t
        var cut = t.substring(0, MAX_REPLY_CHARS)
        if (cut.last().isHighSurrogate()) cut = cut.dropLast(1)
        return cut.trimEnd().ifEmpty { null }
    }

    fun effectiveChoices(choices: List<String>): List<String> = choices.ifEmpty { DEFAULT_APPROVAL_CHOICES }

    /** True only for a choice the pending approval actually offers. */
    fun validApprovalChoice(choice: String?, offered: List<String>): Boolean =
        !choice.isNullOrEmpty() && choice in effectiveChoices(offered)

    /** The (allow, deny) choices the two notification buttons answer with; null = no such button. */
    fun approvalButtons(offered: List<String>): Pair<String?, String?> {
        val eff = effectiveChoices(offered)
        val allow = if ("once" in eff) "once" else eff.firstOrNull { it != "deny" }
        val deny = if ("deny" in eff) "deny" else null
        return allow to deny
    }

    /** True only for the very request a notification showed: both ids must be present and equal to the waiting approval's. */
    fun approvalMatches(waitingRunId: String, waitingRequestId: String, runId: String?, requestId: String?): Boolean =
        runId != null && requestId != null && runId == waitingRunId && requestId == waitingRequestId

    /**
     * Restart after an unexpected exit only while the Error that exit left is still on screen and the user did not press
     * Stop in the meantime ([stopsNow] differs from [stopsAtExit]).
     */
    fun shouldRestartAfterExit(autoRestart: Boolean, state: RuntimeState, stopsAtExit: Int, stopsNow: Int): Boolean =
        autoRestart && state is RuntimeState.Error && stopsAtExit == stopsNow

    /** At most [max] automatic restarts within [windowMs]. Thread-safe. */
    class RestartBudget(private val max: Int = MAX_AUTO_RESTARTS, private val windowMs: Long = RESTART_WINDOW_MS) {
        private val times = ArrayList<Long>()

        /** Records a restart at [now] and returns true when the budget allows it; false (nothing recorded) when it is used up. */
        @Synchronized fun tryAcquire(now: Long): Boolean {
            times.removeAll { now - it >= windowMs || it > now }
            if (times.size >= max) return false
            times += now
            return true
        }
    }

    /** The moment the idle clock starts: the latest of the last chat activity, the app leaving the screen and Hermes coming up. */
    fun idleReference(lastActivityAt: Long, backgroundedAt: Long, runningSince: Long): Long =
        maxOf(lastActivityAt, backgroundedAt, runningSince)

    /** Stop Hermes to save battery: running, app in the background, nothing busy, and quiet for at least [idleMinutes]. */
    fun shouldIdleStop(
        idleMinutes: Int, state: RuntimeState, appInForeground: Boolean, anyChatBusy: Boolean,
        terminalRunning: Boolean, referenceAt: Long, now: Long,
    ): Boolean = idleMinutes > 0 && state == RuntimeState.Running && !appInForeground && !anyChatBusy && !terminalRunning &&
        now - referenceAt >= idleMinutes * 60_000L
}

/**
 * Makes Hermes feel present: lights the fire when the app opens, restarts it once after a crash, rests it when idle and
 * tells the user about replies and approvals while the app is elsewhere. Everything it needs it reads from [app] lazily
 * (at [install] time and later), never in its constructor.
 */
class Presence(private val app: PocketApp) {
    val notifier: ReplyNotifier by lazy { ReplyNotifier(app) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val installed = AtomicBoolean(false)
    private val restartBudget = PresenceLogic.RestartBudget()

    @Volatile private var userStopped = false
    /** Counts every deliberate stop: a restart that was planned before a stop must not run after it. */
    private val userStopCount = AtomicInteger(0)
    @Volatile private var autoStartHandled = false
    @Volatile private var backgroundedAt = System.currentTimeMillis()
    @Volatile private var runningSince = 0L

    private val settings get() = app.presencePrefs.settings.value

    /** Wires the foreground tracker, the notifications, auto-start, auto-restart and the idle stop. Safe to call twice. */
    fun install() {
        if (!installed.compareAndSet(false, true)) return
        AppForeground.install(app)
        notifier.ensureChannels()
        scope.launch { guarded("foreground") { watchForeground() } }
        scope.launch { guarded("events") { watchEvents() } }
        scope.launch { guarded("approvals") { watchApprovals() } }
        scope.launch { guarded("exits") { watchExits() } }
        scope.launch { guarded("state") { watchState() } }
        scope.launch { guarded("idle") { watchIdle() } }
    }

    /**
     * Tells Presence that the user stopped Hermes on purpose (home button, tile, notification): no auto-start until the next
     * process start. An idle stop or a routine's own stop is NOT a user stop.
     */
    fun noteUserStop() {
        userStopped = true
        userStopCount.incrementAndGet()
    }

    private suspend fun guarded(what: String, body: suspend () -> Unit) {
        try { body() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Throwable) { AppLog.e("Presence", "$what watcher died", e) }
    }

    // ---- auto-start ----------------------------------------------------------------------------------------------

    private suspend fun watchForeground() {
        AppForeground.isForeground.collect { fg ->
            if (!fg) { backgroundedAt = System.currentTimeMillis(); return@collect }
            // Catch-all: a summary whose children went away while no code of ours ran (a tap on a routine notification...).
            notifier.syncSummaries()
            if (autoStartHandled) return@collect
            autoStartHandled = true
            try { maybeAutoStart() } catch (e: Exception) { AppLog.w("Presence", "auto-start check failed", e) }
        }
    }

    private fun maybeAutoStart() {
        val rt = app.runtime
        val setupComplete = try { ProviderConfig.savedProblems(rt.settings, rt.secrets).isEmpty() } catch (e: Exception) { false }
        val go = PresenceLogic.shouldAutoStart(
            autoStart = settings.autoStart, alreadyHandled = false, userStopped = userStopped,
            rootfsValid = rt.isRootfsValid(), state = rt.state.value, setupComplete = setupComplete,
        )
        if (go) {
            AppLog.i("Presence", "auto-start: the app came to the foreground and Hermes is asleep")
            rt.start()
        }
    }

    // ---- state tracking --------------------------------------------------------------------------------------------

    private suspend fun watchState() {
        var wasRunning = false
        app.runtime.state.collect { s ->
            val running = s == RuntimeState.Running
            if (running && !wasRunning) runningSince = System.currentTimeMillis()
            wasRunning = running
        }
    }

    // ---- auto-restart ----------------------------------------------------------------------------------------------

    private suspend fun watchExits() {
        var seen = app.runtime.unexpectedExits.value
        app.runtime.unexpectedExits.collect { n ->
            if (n <= seen) { seen = n; return@collect }
            seen = n
            val stopsAtExit = userStopCount.get()
            scope.launch { guarded("restart") { restartAfterExit(stopsAtExit) } }
        }
    }

    private suspend fun restartAfterExit(stopsAtExit: Int) {
        if (!settings.autoRestart) return
        delay(PresenceLogic.RESTART_DELAY_MS)
        val rt = app.runtime
        // Only the Error the exit left may be restarted. A Stopped state here means somebody stopped it on purpose (the user,
        // an idle stop), a Starting/Running state that somebody (the user, a wake-on-send) already started it again.
        if (!PresenceLogic.shouldRestartAfterExit(settings.autoRestart, rt.state.value, stopsAtExit, userStopCount.get())) return
        if (!restartBudget.tryAcquire(System.currentTimeMillis())) {
            AppLog.w("Presence", "Hermes exited unexpectedly again; the automatic restarts of the last 10 minutes are used up")
            return
        }
        AppLog.i("Presence", "restarting Hermes after an unexpected exit")
        rt.start()
    }

    // ---- idle stop ---------------------------------------------------------------------------------------------------

    private suspend fun watchIdle() {
        app.presencePrefs.settings.map { it.idleStopMinutes }.distinctUntilChanged().collectLatest { minutes ->
            if (minutes <= 0) return@collectLatest
            while (true) {
                delay(PresenceLogic.IDLE_CHECK_MS)
                try { checkIdle(minutes) } catch (e: Exception) { AppLog.w("Presence", "idle check failed", e) }
            }
        }
    }

    private fun checkIdle(minutes: Int) {
        val rt = app.runtime
        val engine = app.engine
        val ref = PresenceLogic.idleReference(engine.lastActivityAt, backgroundedAt, runningSince)
        val stop = PresenceLogic.shouldIdleStop(
            idleMinutes = minutes, state = rt.state.value, appInForeground = AppForeground.isForeground.value,
            anyChatBusy = engine.busy.value.isNotEmpty(), terminalRunning = app.terminal.running.value,
            referenceAt = ref, now = System.currentTimeMillis(),
        )
        if (!stop) return
        AppLog.i("Presence", "idle for $minutes min in the background: stopping Hermes to save battery")
        // Not a user stop: the next time the app comes to the foreground the fire is lit again.
        autoStartHandled = false
        rt.stop()
    }

    // ---- notifications -----------------------------------------------------------------------------------------------

    private suspend fun watchEvents() {
        app.engine.events.collect { ev ->
            try {
                when (ev) {
                    is EngineEvent.TurnCompleted -> onTurnCompleted(ev)
                    is EngineEvent.TurnFailed -> onTurnFailed(ev)
                    else -> Unit
                }
            } catch (e: Exception) {
                AppLog.w("Presence", "could not handle an engine event", e)
            }
        }
    }

    private fun onTurnCompleted(ev: EngineEvent.TurnCompleted) {
        val wanted = PresenceLogic.shouldNotify(settings.notifyReplies, AppForeground.isForeground.value, app.chat.activeConversation, ev.convId)
        if (!wanted) return
        val preview = PresenceLogic.preview(ev.text)
        if (preview.isBlank()) return
        val conv = app.chat.conversation(ev.convId) ?: return
        val botName = app.chat.bot(ev.botId)?.name
        notifier.postReply(ev.convId, notifier.displayTitle(botName, conv.title, conv.isGroup), preview, conv.routineId.isNotEmpty())
    }

    private fun onTurnFailed(ev: EngineEvent.TurnFailed) {
        val wanted = PresenceLogic.shouldNotify(settings.notifyReplies, AppForeground.isForeground.value, app.chat.activeConversation, ev.convId)
        if (!wanted) return
        val conv = app.chat.conversation(ev.convId) ?: return
        // The event does not say which bot failed: in a group chat the first bot's name could be wrong, so the title is the
        // chat's title there.
        val botName = if (conv.isGroup) null else conv.botIds.firstOrNull()?.let { app.chat.bot(it)?.name }
        notifier.postFailure(ev.convId, notifier.displayTitle(botName, conv.title, conv.isGroup), conv.routineId.isNotEmpty())
    }

    private suspend fun watchApprovals() {
        var known = emptyMap<String, String>() // convId -> run/request key of the approval already announced
        app.engine.approvals.collect { map ->
            for ((convId, p) in map) {
                val key = p.runId + "/" + p.requestId
                if (known[convId] != key) {
                    val posted = try { notifyApproval(p) } catch (e: Exception) { AppLog.w("Presence", "could not post the approval notification", e); false }
                    // A new request replaced the one the shade still shows and is not announced itself (the user is looking at
                    // the chat, or notifications are off): the old notification would answer the new request, so it goes.
                    if (!posted && convId in known) notifier.cancelApproval(convId)
                }
            }
            // An approval that was answered (in the app, or from the notification) or that ended: its notification goes too.
            for (convId in known.keys) if (convId !in map) notifier.cancelApproval(convId)
            known = map.mapValues { (_, p) -> p.runId + "/" + p.requestId }
        }
    }

    /** True when a notification was posted for [p]. */
    private fun notifyApproval(p: PendingApproval): Boolean {
        val wanted = PresenceLogic.shouldNotify(settings.notifyApprovals, AppForeground.isForeground.value, app.chat.activeConversation, p.convId)
        if (!wanted) return false
        val conv = app.chat.conversation(p.convId)
        val name = app.chat.bot(p.botId)?.name ?: notifier.displayTitle(null, conv?.title.orEmpty(), false)
        val (allow, deny) = PresenceLogic.approvalButtons(p.choices)
        notifier.postApproval(p.convId, name, p.command.ifBlank { p.description }, allow, deny, p.runId, p.requestId)
        return true
    }
}
