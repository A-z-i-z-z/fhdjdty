package dev.pocketagent.app.service

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The buttons of the notifications: "Reply" (inline text), "Mark as read" and the approval answers. Not exported: only
 * the PendingIntents built by [ReplyNotifier] reach it. Every extra is validated before anything happens: the conversation
 * must still exist, an approval choice must be one of the pending approval's choices, a reply must not be blank (and is
 * capped at [PresenceLogic.MAX_REPLY_CHARS]).
 *
 * The process may have been created just for this broadcast, so the work runs asynchronously ([goAsync]) after the chat index
 * has loaded.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val i = intent ?: return
        val action = i.action ?: return
        if (action != ACTION_REPLY && action != ACTION_MARK_READ && action != ACTION_APPROVAL && action != ACTION_DISMISSED) return
        val app = context.applicationContext as? PocketApp ?: return
        if (action == ACTION_DISMISSED) {
            // The user swiped a notification away: if that was the last one of its group, its summary goes too.
            try { app.presence.notifier.syncSummaries() } catch (e: Throwable) { AppLog.w("Notify", "summary sync after a dismissal failed", e) }
            return
        }
        val convId = i.getStringExtra(EXTRA_CONV_ID)
        val choice = i.getStringExtra(EXTRA_CHOICE)
        val runId = i.getStringExtra(EXTRA_RUN_ID)
        val requestId = i.getStringExtra(EXTRA_REQUEST_ID)
        val reply: CharSequence? = if (action == ACTION_REPLY) RemoteInput.getResultsFromIntent(i)?.getCharSequence(KEY_REPLY) else null
        val pending = goAsync()
        scope.launch {
            try {
                withTimeoutOrNull(LOAD_TIMEOUT_MS) { app.chat.loaded.first { it } }
                handle(app, action, convId, reply, choice, runId, requestId)
            } catch (e: Throwable) {
                AppLog.e("Notify", "notification action $action failed", e)
            } finally {
                pending?.finish()
            }
        }
    }

    private fun handle(
        app: PocketApp, action: String, convId: String?, reply: CharSequence?, choice: String?,
        runId: String?, requestId: String?,
    ) {
        if (!PresenceLogic.validConvId(convId)) return
        val id = convId ?: return
        val notifier = app.presence.notifier
        val conv = app.chat.conversation(id)
        if (conv == null) {
            // The chat was deleted after the notification was posted.
            notifier.cancelFor(id)
            return
        }
        when (action) {
            ACTION_REPLY -> {
                val text = PresenceLogic.cleanReply(reply)
                // Ends the "sending" spinner of the inline reply. A pending approval notification stays: it is announced only once.
                notifier.cancelReply(id)
                if (text == null) return
                val accepted = app.engine.send(id, text)
                if (accepted) {
                    app.chat.markRead(id)
                } else {
                    AppLog.w("Notify", "inline reply was not accepted (chat busy or Hermes unavailable)")
                    notifier.postSendFailed(id, notifier.displayTitle(botName(app, id), conv.title, conv.isGroup))
                }
            }
            ACTION_MARK_READ -> {
                app.chat.markRead(id)
                notifier.cancelReply(id)
            }
            ACTION_APPROVAL -> {
                if (deviceLocked(app)) {
                    // Never answer an approval while the phone is locked (older Android versions ignore the
                    // "authentication required" flag of the button). The notification stays for later.
                    AppLog.w("Notify", "approval answer ignored (the device is locked)")
                    return
                }
                val waiting = app.engine.approvals.value[id]
                if (waiting != null && choice != null &&
                    PresenceLogic.approvalMatches(waiting.runId, waiting.requestId, runId, requestId) &&
                    PresenceLogic.validApprovalChoice(choice, waiting.choices)
                ) {
                    app.engine.resolveApproval(id, choice)
                } else {
                    AppLog.w("Notify", "approval answer ignored (no pending approval, another request, or a choice it does not offer)")
                }
                notifier.cancelApproval(id)
            }
        }
    }

    private fun deviceLocked(context: Context): Boolean = try {
        context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == true
    } catch (_: Exception) {
        false
    }

    private fun botName(app: PocketApp, convId: String): String? {
        val c = app.chat.conversation(convId) ?: return null
        return c.botIds.firstOrNull()?.let { app.chat.bot(it)?.name }
    }

    companion object {
        const val ACTION_REPLY = "dev.pocketagent.app.NOTIF_REPLY"
        const val ACTION_MARK_READ = "dev.pocketagent.app.NOTIF_MARK_READ"
        const val ACTION_APPROVAL = "dev.pocketagent.app.NOTIF_APPROVAL"
        /** A notification was dismissed by the user (its delete intent): only the group summaries are updated. */
        const val ACTION_DISMISSED = "dev.pocketagent.app.NOTIF_DISMISSED"
        const val EXTRA_CONV_ID = "conv_id"
        const val EXTRA_CHOICE = "choice"
        /** The request an approval notification showed; a tap only counts while that very request is waiting. */
        const val EXTRA_RUN_ID = "run_id"
        const val EXTRA_REQUEST_ID = "request_id"
        /** The RemoteInput key of the inline reply. */
        const val KEY_REPLY = "reply_text"

        private const val LOAD_TIMEOUT_MS = 10_000L
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
