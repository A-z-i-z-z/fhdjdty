package dev.pocketagent.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import dev.pocketagent.app.AppLinks
import dev.pocketagent.app.R
import dev.pocketagent.app.diag.Diag
import dev.pocketagent.app.ui.theme.AppearanceStore
import dev.pocketagent.app.util.AppLog
import java.util.Locale

/** A context whose resources follow the language chosen inside the app (Settings > Language), not only the system's. */
internal fun Context.withAppLanguage(): Context = try {
    val lang = AppearanceStore.get(this).appearance.value.language
    if (lang.isBlank()) this
    else {
        val cfg = Configuration(resources.configuration)
        cfg.setLocale(Locale.forLanguageTag(lang))
        createConfigurationContext(cfg)
    }
} catch (_: Exception) {
    this
}

/**
 * Posts the notifications of the app: replies (with an inline reply and "mark as read"), approval requests (with "allow
 * once" / "deny"), quiet failure notes and routine failures. One notification per conversation and kind: the
 * conversation id is the notification TAG, the kind is the notification id, so a later reply replaces the earlier one.
 *
 * All texts come from string resources in the language chosen inside the app. A posting that Android refuses (notifications
 * switched off, permission revoked) is logged and ignored: it must never break the chat.
 */
class ReplyNotifier(private val context: Context) {
    private val appContext: Context get() = context.applicationContext ?: context

    /** Serialises post, cancel and summary sync (they come from the main thread, Dispatchers.Default and engine coroutines). */
    private val lock = Any()

    /** Children posted lately (tag + id -> post time and group), guarded by [lock]; see [syncSummaries]. */
    private val recentChildren = HashMap<Pair<String?, Int>, Pair<Long, String>>()

    /** `NotificationManager.areNotificationsEnabled()`: false when the user switched the app's notifications off. */
    fun notificationsEnabled(): Boolean = try {
        NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    } catch (_: Exception) {
        false
    }

    /** Creates (or refreshes the names of) the three channels. Idempotent and cheap. */
    fun ensureChannels() {
        try {
            val l = appContext.withAppLanguage()
            val nm = appContext.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_REPLIES, l.getString(R.string.prs_channel_replies), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = l.getString(R.string.prs_channel_replies_desc) }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_APPROVALS, l.getString(R.string.prs_channel_approvals), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = l.getString(R.string.prs_channel_approvals_desc) }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ROUTINES, l.getString(R.string.prs_channel_routines), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = l.getString(R.string.prs_channel_routines_desc) }
            )
        } catch (e: Exception) {
            AppLog.w("Notify", "could not create the notification channels", e)
        }
    }

    /** The notification title of a conversation: the bot's name (group chats: "bot · chat title"), with a localized fallback. */
    fun displayTitle(botName: String?, convTitle: String, isGroup: Boolean): String =
        PresenceLogic.titleFor(botName, convTitle, isGroup, appContext.withAppLanguage().getString(R.string.prs_untitled_chat))

    /** Removes everything this class posted for [convId]: called when the chat is opened. */
    fun cancelFor(convId: String) {
        if (convId.isEmpty()) return
        cancelOwn(convId, ID_REPLY, ID_APPROVAL, ID_NOTICE, ID_FAILED)
    }

    /**
     * Removes the reply, failure and notice notifications of [convId] but NOT a pending approval: "mark as read" and an inline
     * reply must not take away the only way to answer an approval from the notification shade (it is announced only once).
     */
    fun cancelReply(convId: String) {
        if (convId.isEmpty()) return
        cancelOwn(convId, ID_REPLY, ID_NOTICE, ID_FAILED)
    }

    /** Removes only the approval notification of [convId] (the approval was answered, or timed out). */
    fun cancelApproval(convId: String) {
        if (convId.isEmpty()) return
        cancelOwn(convId, ID_APPROVAL)
    }

    /** Removes every notification posted by this class (the foreground service's own notification is left alone). */
    fun cancelAll() {
        try {
            val nm = appContext.getSystemService(NotificationManager::class.java) ?: return
            synchronized(lock) {
                val gone = HashSet<Pair<String?, Int>>()
                for (sbn in nm.activeNotifications) {
                    val tag = sbn.tag
                    if (tag != null && sbn.id in OWN_IDS) { nm.cancel(tag, sbn.id); gone += tag to sbn.id }
                }
                // Children posted a moment ago may not be listed yet: cancel them by key too.
                for (key in recentChildren.keys.toList()) {
                    val tag = key.first
                    if (tag != null && key.second in OWN_IDS) { nm.cancel(tag, key.second); gone += key }
                }
                recentChildren.clear()
                syncSummaries(gone)
            }
        } catch (e: Exception) {
            AppLog.w("Notify", "cancelAll failed", e)
        }
    }

    /** Cancels the notifications [ids] of [convId], then takes the group summary away when that was the last child of its group. */
    private fun cancelOwn(convId: String, vararg ids: Int) {
        try {
            val nm = NotificationManagerCompat.from(appContext)
            synchronized(lock) {
                for (id in ids) { nm.cancel(convId, id); recentChildren.remove(convId to id) }
                syncSummaries(ids.map { convId to it }.toSet())
            }
        } catch (e: Exception) {
            AppLog.w("Notify", "cancel failed", e)
        }
    }

    /** Children posted less than [IN_FLIGHT_MS] ago, as the plan's "may be in flight" list; expired records are dropped. */
    private fun inFlightChildren(): List<NotificationGroups.Active> {
        val now = SystemClock.elapsedRealtime()
        val iter = recentChildren.entries.iterator()
        val out = ArrayList<NotificationGroups.Active>()
        while (iter.hasNext()) {
            val (key, rec) = iter.next()
            if (now - rec.first > IN_FLIGHT_MS) iter.remove() else out += NotificationGroups.Active(key.first, key.second, rec.second, false)
        }
        return out
    }

    /**
     * Makes the summaries match the children that are really on screen: a group with children but no summary gets one, a group
     * whose last child is gone loses its summary. The truth is `activeNotifications` (children can also be dismissed by the user
     * or cancelled by a tap), minus [justCancelled]: Android removes those asynchronously, so they may still be listed.
     * Called after every cancel, when a child is dismissed, and when the app comes to the foreground.
     */
    fun syncSummaries(justCancelled: Set<Pair<String?, Int>> = emptySet()) {
        try {
            val nm = appContext.getSystemService(NotificationManager::class.java) ?: return
            synchronized(lock) {
                val active = nm.activeNotifications.map {
                    NotificationGroups.Active(it.tag, it.id, it.notification?.group, (it.notification?.flags ?: 0) and Notification.FLAG_GROUP_SUMMARY != 0)
                }
                // A child posted a moment ago may not be listed yet, and cancelling its group's summary would take it along.
                val plan = NotificationGroups.plan(active, justCancelled, inFlightChildren())
                for (g in plan.cancel) NotificationGroups.summaryId(g)?.let { nm.cancel(NotificationGroups.SUMMARY_TAG, it) }
                for (g in plan.post) postSummary(g)
            }
        } catch (e: Exception) {
            AppLog.w("Notify", "could not update the group summaries", e)
        }
    }

    /**
     * A finished reply. [title] is the bot's name (group chats: "bot · chat title"), [text] the plain preview. A chat that
     * belongs to a routine uses the routines channel.
     */
    fun postReply(convId: String, title: String, text: String, routine: Boolean) {
        if (convId.isEmpty()) return
        val l = appContext.withAppLanguage()
        val channel = if (routine) CHANNEL_ROUTINES else CHANNEL_REPLIES
        val safeText = Diag.redactor.redact(text)
        val b = base(channel)
            .setContentTitle(title)
            .setContentText(safeText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(safeText))
            .setContentIntent(AppLinks.chatPendingIntent(appContext, convId))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPublicVersion(publicVersion(channel, l, l.getString(R.string.prs_public_reply)))
            // A second answer of a group chat updates the notification without ringing again.
            .setOnlyAlertOnce(isShowing(convId, ID_REPLY))
            .addAction(replyAction(l, convId))
            .addAction(
                NotificationCompat.Action.Builder(0, l.getString(R.string.prs_action_mark_read), actionIntent(NotificationActionReceiver.ACTION_MARK_READ, convId, "", false))
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                    .setShowsUserInterface(false)
                    .build()
            )
        post(convId, ID_REPLY, b.build())
    }

    /** A quiet note: the turn of [convId] failed while the user was elsewhere. */
    fun postFailure(convId: String, title: String, routine: Boolean) {
        if (convId.isEmpty()) return
        val l = appContext.withAppLanguage()
        val channel = if (routine) CHANNEL_ROUTINES else CHANNEL_REPLIES
        val b = base(channel)
            .setContentTitle(title)
            .setContentText(l.getString(R.string.prs_failed_text))
            .setContentIntent(AppLinks.chatPendingIntent(appContext, convId))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPublicVersion(publicVersion(channel, l, l.getString(R.string.prs_public_failed)))
            .setSilent(true)
        // Its own id: in a group chat an earlier bot's reply (with its preview and Reply action) must stay in the shade.
        post(convId, ID_FAILED, b.build())
    }

    /** A reply typed into a notification could not be delivered. */
    fun postSendFailed(convId: String, title: String) {
        if (convId.isEmpty()) return
        val l = appContext.withAppLanguage()
        val b = base(CHANNEL_REPLIES)
            .setContentTitle(title)
            .setContentText(l.getString(R.string.prs_send_failed_text))
            .setContentIntent(AppLinks.chatPendingIntent(appContext, convId))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPublicVersion(publicVersion(CHANNEL_REPLIES, l, l.getString(R.string.prs_public_failed)))
        post(convId, ID_NOTICE, b.build())
    }

    /**
     * Hermes asks permission to run [command]. [allowChoice] and [denyChoice] are the choices (from the pending approval)
     * that the two buttons answer with; a button whose choice is null is left out. [runId] and [requestId] identify the
     * request the notification shows: a tap is applied only while that very request is still the one waiting.
     *
     * The buttons require the device to be unlocked (`setAuthenticationRequired`), so nobody can allow a command from the
     * lock screen without the PIN.
     */
    fun postApproval(
        convId: String, botName: String, command: String, allowChoice: String?, denyChoice: String?,
        runId: String, requestId: String,
    ) {
        if (convId.isEmpty()) return
        val l = appContext.withAppLanguage()
        val text = Diag.redactor.redact(command).take(MAX_APPROVAL_CHARS)
        val b = base(CHANNEL_APPROVALS)
            .setContentTitle(l.getString(R.string.prs_approval_title, botName))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(AppLinks.chatPendingIntent(appContext, convId))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPublicVersion(publicVersion(CHANNEL_APPROVALS, l, l.getString(R.string.prs_public_approval)))
        if (allowChoice != null) {
            b.addAction(approvalAction(l.getString(R.string.prs_action_allow), convId, allowChoice, runId, requestId))
        }
        if (denyChoice != null) {
            b.addAction(approvalAction(l.getString(R.string.prs_action_deny), convId, denyChoice, runId, requestId))
        }
        post(convId, ID_APPROVAL, b.build())
    }

    private fun approvalAction(label: String, convId: String, choice: String, runId: String, requestId: String): NotificationCompat.Action =
        NotificationCompat.Action.Builder(
            0, label,
            actionIntent(NotificationActionReceiver.ACTION_APPROVAL, convId, choice, false, choice, runId, requestId),
        )
            .setAuthenticationRequired(true)
            .setShowsUserInterface(false)
            .build()

    /** A routine did not run or failed. Opens the app (its routines list is one tap away). */
    fun postRoutineFailure(routineName: String, reason: String) {
        val l = appContext.withAppLanguage()
        val name = routineName.trim().take(MAX_ROUTINE_NAME_CHARS).ifEmpty { l.getString(R.string.prs_routine_unnamed) }
        val text = Diag.redactor.redact(reason).trim().take(MAX_REASON_CHARS)
        val open = PendingIntent.getActivity(
            appContext, REQUEST_OPEN_ROUTINES,
            Intent(appContext, dev.pocketagent.app.MainActivity::class.java)
                .setAction(AppLinks.ACTION_ROUTINES)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = base(CHANNEL_ROUTINES)
            .setContentTitle(l.getString(R.string.prs_routine_failed_title, name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPublicVersion(publicVersion(CHANNEL_ROUTINES, l, l.getString(R.string.prs_public_failed)))
        post("routine:$name", ID_ROUTINE_FAILED, b.build())
    }

    /**
     * A restore finished while the app was in the background (Android blocks starting an activity from there, so the app cannot
     * restart itself). Tapping opens the app. Synchronous on purpose: the caller ends the process right afterwards.
     */
    @SuppressLint("MissingPermission")
    fun postRestoreFinished() {
        try {
            ensureChannels()
            if (!notificationsEnabled()) return
            val l = appContext.withAppLanguage()
            val open = PendingIntent.getActivity(
                appContext, REQUEST_OPEN_AFTER_RESTORE,
                Intent(appContext, dev.pocketagent.app.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(appContext, CHANNEL_REPLIES)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(l.getString(R.string.app_name))
                .setContentText(l.getString(R.string.ctl_bak_restored_notif))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build()
            NotificationManagerCompat.from(appContext).notify(TAG_RESTORE, ID_RESTORED, n)
        } catch (e: Exception) {
            AppLog.w("Notify", "could not post the restore-finished notification", e)
        }
    }

    // ---- building blocks -----------------------------------------------------------------------------------------

    private fun base(channel: String): NotificationCompat.Builder =
        NotificationCompat.Builder(appContext, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            // An explicit group of its own (never bundled with the ongoing runtime notification); the summary is posted by post().
            .setGroup(NotificationGroups.groupForChannel(channel))
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            // Swiping a notification away may leave its group without children: then the summary goes too.
            .setDeleteIntent(dismissIntent())

    /** Sent when the user dismisses one of the notifications: the group summaries are brought up to date. */
    private fun dismissIntent(): PendingIntent {
        val intent = Intent(appContext, NotificationActionReceiver::class.java)
            .setAction(NotificationActionReceiver.ACTION_DISMISSED)
            .setData(Uri.parse("agiri://notification/dismissed"))
        return PendingIntent.getBroadcast(appContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** The silent group summary of [group] (its children alert, it does not). Updated in place when it already exists. */
    private fun postSummary(group: String) {
        val id = NotificationGroups.summaryId(group) ?: return
        val l = appContext.withAppLanguage()
        val (channel, label) = when (group) {
            NotificationGroups.GROUP_APPROVALS -> CHANNEL_APPROVALS to l.getString(R.string.prs_channel_approvals)
            NotificationGroups.GROUP_ROUTINES -> CHANNEL_ROUTINES to l.getString(R.string.prs_channel_routines)
            else -> CHANNEL_REPLIES to l.getString(R.string.prs_channel_replies)
        }
        val open = PendingIntent.getActivity(
            appContext, REQUEST_OPEN_APP, Intent(appContext, dev.pocketagent.app.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(appContext, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(l.getString(R.string.app_name))
            .setContentText(label)
            .setContentIntent(open)
            .setGroup(group)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setOnlyAlertOnce(true)
            // No auto-cancel: Android would cancel the summary on tap and, with it, every reply of its group. cancelFor and the
            // foreground sync remove the summary once its children are gone.
            .setShowWhen(false)
            .setSilent(true)
            .setDeleteIntent(dismissIntent())
            .build()
        try {
            NotificationManagerCompat.from(appContext).notify(NotificationGroups.SUMMARY_TAG, id, n)
        } catch (e: SecurityException) {
            AppLog.w("Notify", "Android refused the summary notification (permission?)", e)
        } catch (e: Exception) {
            AppLog.w("Notify", "could not post the summary notification", e)
        }
    }

    /** What the lock screen shows when the user hides sensitive notification content. */
    private fun publicVersion(channel: String, l: Context, text: String): Notification =
        NotificationCompat.Builder(appContext, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(l.getString(R.string.app_name))
            .setContentText(text)
            .build()

    private fun replyAction(l: Context, convId: String): NotificationCompat.Action {
        val input = RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY)
            .setLabel(l.getString(R.string.prs_reply_label))
            .build()
        return NotificationCompat.Action.Builder(0, l.getString(R.string.prs_action_reply), actionIntent(NotificationActionReceiver.ACTION_REPLY, convId, "", true))
            .addRemoteInput(input)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            // An inline reply can make Hermes run tools: ask for the unlock first (defence in depth).
            .setAuthenticationRequired(true)
            .build()
    }

    /**
     * A broadcast to [NotificationActionReceiver]. The data Uri (action, conversation, choice) keeps the PendingIntents of
     * different chats and buttons apart. [mutable] is needed only for the reply: Android fills the typed text into it.
     */
    private fun actionIntent(
        action: String, convId: String, discriminator: String, mutable: Boolean, choice: String? = null,
        runId: String? = null, requestId: String? = null,
    ): PendingIntent {
        val uri = Uri.Builder().scheme("agiri").authority("notification").appendPath(action).appendPath(convId).appendPath(discriminator.ifEmpty { "-" })
        // A new request of the same chat gets its own PendingIntent, never the extras of the old one.
        if (runId != null) uri.appendPath(runId.ifEmpty { "-" })
        if (requestId != null) uri.appendPath(requestId.ifEmpty { "-" })
        val intent = Intent(appContext, NotificationActionReceiver::class.java)
            .setAction(action)
            .setData(uri.build())
            .putExtra(NotificationActionReceiver.EXTRA_CONV_ID, convId)
        if (choice != null) intent.putExtra(NotificationActionReceiver.EXTRA_CHOICE, choice)
        if (runId != null) intent.putExtra(NotificationActionReceiver.EXTRA_RUN_ID, runId)
        if (requestId != null) intent.putExtra(NotificationActionReceiver.EXTRA_REQUEST_ID, requestId)
        val mutability = if (mutable && Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(appContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutability)
    }

    private fun isShowing(tag: String, id: Int): Boolean = try {
        appContext.getSystemService(NotificationManager::class.java)?.activeNotifications?.any { it.id == id && it.tag == tag } == true
    } catch (_: Exception) {
        false
    }

    @SuppressLint("MissingPermission")
    private fun post(tag: String, id: Int, n: Notification) {
        ensureChannels()
        if (!notificationsEnabled()) return
        try {
            synchronized(lock) {
                NotificationManagerCompat.from(appContext).notify(tag, id, n)
                // Remember the child: it is not in activeNotifications yet (Android enqueues it), and a concurrent cancel must
                // not remove its group's summary meanwhile (that would cancel this new child too).
                n.group?.let { recentChildren[tag to id] = SystemClock.elapsedRealtime() to it }
                // Post the summary unconditionally. A summary that is already there is updated in place (it is silent, so
                // nothing rings twice).
                n.group?.let { postSummary(it) }
            }
        } catch (e: SecurityException) {
            AppLog.w("Notify", "Android refused the notification (permission?)", e)
        } catch (e: Exception) {
            AppLog.w("Notify", "could not post the notification", e)
        }
    }

    companion object {
        const val CHANNEL_REPLIES = "replies"
        const val CHANNEL_APPROVALS = "approvals"
        const val CHANNEL_ROUTINES = "routines"

        /** Notification ids (the conversation id is the tag). The foreground service uses id 1 without a tag. */
        private const val ID_REPLY = 101
        private const val ID_APPROVAL = 102
        private const val ID_NOTICE = 103
        private const val ID_ROUTINE_FAILED = 104
        private const val ID_FAILED = 105
        private val OWN_IDS = setOf(ID_REPLY, ID_APPROVAL, ID_NOTICE, ID_ROUTINE_FAILED, ID_FAILED)

        /** "Restore finished" (posted just before the process ends; ungrouped, so not in [OWN_IDS]). Tag [TAG_RESTORE]. */
        private const val ID_RESTORED = 106
        private const val TAG_RESTORE = "agiri.restore"

        /** How long a freshly posted child counts as live even when `activeNotifications` does not list it yet. */
        private const val IN_FLIGHT_MS = 2_000L

        private const val REQUEST_OPEN_ROUTINES = 7001
        private const val REQUEST_OPEN_APP = 7002
        private const val REQUEST_OPEN_AFTER_RESTORE = 7003
        private const val MAX_APPROVAL_CHARS = 600
        private const val MAX_ROUTINE_NAME_CHARS = 80
        private const val MAX_REASON_CHARS = 300
    }
}
