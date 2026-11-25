package dev.pocketagent.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * A navigation request that arrives from outside the UI: a notification, a launcher shortcut, the Quick Settings
 * tile or an in-app shortcut. [MainActivity] parses the incoming intent into one of these and puts it into
 * `PocketApp.pendingLink`; `AppRoot` consumes it.
 */
sealed interface AppLink {
    data class OpenChat(val convId: String) : AppLink
    data object NewChat : AppLink
    data object Terminal : AppLink
    data object Routines : AppLink
    data object Usage : AppLink
}

object AppLinks {
    const val ACTION_OPEN_CHAT = "dev.pocketagent.app.OPEN_CHAT"
    const val ACTION_NEW_CHAT = "dev.pocketagent.app.NEW_CHAT"
    const val ACTION_TERMINAL = "dev.pocketagent.app.TERMINAL"
    const val ACTION_ROUTINES = "dev.pocketagent.app.ROUTINES"
    const val ACTION_USAGE = "dev.pocketagent.app.USAGE"
    const val EXTRA_CONV_ID = "conv_id"

    private const val SCHEME = "agiri"
    private const val HOST_CHAT = "chat"

    /** The longest text taken from the text-selection menu ("Ask Agiri"). */
    internal const val MAX_PROCESS_TEXT = 20_000

    /**
     * An explicit intent for [MainActivity] that opens the chat [convId]. It carries a distinct data Uri per chat, so
     * PendingIntents of different chats never collapse into one.
     */
    fun chatIntent(context: Context, convId: String): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_CHAT)
            .setData(Uri.Builder().scheme(SCHEME).authority(HOST_CHAT).appendPath(convId).build())
            .putExtra(EXTRA_CONV_ID, convId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun chatPendingIntent(context: Context, convId: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCodeFor(convId),
            chatIntent(context, convId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** The link an incoming intent asks for, or null (also for an intent replayed from Recents). */
    fun parse(intent: Intent?): AppLink? {
        if (intent == null) return null
        val convId = intent.getStringExtra(EXTRA_CONV_ID) ?: intent.data?.takeIf { it.scheme == SCHEME && it.host == HOST_CHAT }?.lastPathSegment
        return parseParts(intent.action, convId, intent.flags)
    }

    /** The pure part of [parse]: no Android objects, so it runs in a JVM unit test. */
    internal fun parseParts(action: String?, convId: String?, flags: Int): AppLink? {
        if ((flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return null
        return when (action) {
            ACTION_OPEN_CHAT -> convId?.trim()?.takeIf { it.isNotEmpty() }?.let { AppLink.OpenChat(it) }
            ACTION_NEW_CHAT -> AppLink.NewChat
            ACTION_TERMINAL -> AppLink.Terminal
            ACTION_ROUTINES -> AppLink.Routines
            ACTION_USAGE -> AppLink.Usage
            else -> null
        }
    }

    /**
     * The text of a text-selection ("Ask Agiri") intent, trimmed and capped, or null when the intent is something else, is
     * replayed from Recents or carries no text. Pure, see [parseParts].
     */
    internal fun processText(action: String?, text: CharSequence?, flags: Int): String? {
        if (action != Intent.ACTION_PROCESS_TEXT) return null
        if ((flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return null
        val t = text?.toString()?.trim().orEmpty()
        if (t.isEmpty()) return null
        return t.take(MAX_PROCESS_TEXT)
    }

    /** A stable request code per chat (the data Uri already differs; this keeps the PendingIntent records apart too). */
    internal fun requestCodeFor(convId: String): Int = convId.hashCode()
}
