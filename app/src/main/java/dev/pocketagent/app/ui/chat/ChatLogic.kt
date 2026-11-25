package dev.pocketagent.app.ui.chat

import dev.pocketagent.app.data.Message
import dev.pocketagent.app.data.Role
import dev.pocketagent.app.runtime.RuntimeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.DecimalFormatSymbols
import java.util.Locale

// Small pure decisions of the chat and home screens (agent CHAT). Kept free of Compose so a JVM unit test can reach them.

// ---- the input is always there ----------------------------------------------------------------------------------------

/**
 * True when the text input of a chat (or of the quick-talk field on Home) is usable: the saved setup is complete and
 * Hermes is running, or it is asleep / starting and wake-on-send may light the fire for the sender. Same rule as the
 * engine's `canWake` (wake-on-send AND an installed rootfs): [runtimeInstalled] is `RuntimeManager.isRootfsValid()`.
 * A fresh install, a failed extraction (an Error without a rootfs) and an extraction in progress keep the "asleep"
 * card with its Start button, since a send would be refused there.
 */
internal fun inputAvailable(
    runtime: RuntimeState,
    setupIncomplete: Boolean,
    wakeOnSend: Boolean,
    runtimeInstalled: Boolean,
): Boolean {
    if (setupIncomplete) return false
    return when (runtime) {
        RuntimeState.Running -> true
        is RuntimeState.Stopped, is RuntimeState.Error, is RuntimeState.Starting -> wakeOnSend && runtimeInstalled
        RuntimeState.NotInstalled, is RuntimeState.Extracting -> false
    }
}

// ---- find in chat -----------------------------------------------------------------------------------------------------

/** Ids of the messages (oldest first) whose text contains [query], ignoring case. Same message kinds as the chat list search. */
internal fun findMatchingMessageIds(msgs: List<Message>, query: String): List<String> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val out = ArrayList<String>()
    for (m in msgs) {
        if ((m.role == Role.USER || m.role == Role.BOT || m.role == Role.TOOL) && m.text.contains(q, ignoreCase = true)) out += m.id
    }
    return out
}

/** Moves [current] by [delta] inside 0 until [count], wrapping around; 0 when there is nothing to move over. */
internal fun cycleIndex(current: Int, count: Int, delta: Int): Int =
    if (count <= 0) 0 else Math.floorMod(current + delta, count)

/**
 * A request to open a chat scrolled to one message (from the saved messages, a search result ...). Process-local:
 * the chat screen consumes it as soon as the message is on screen.
 */
internal object ChatScrollTarget {
    data class Target(val convId: String, val messageId: String, val nonce: Long)

    private val _pending = MutableStateFlow<Target?>(null)
    val pending: StateFlow<Target?> = _pending

    fun set(convId: String, messageId: String) {
        _pending.value = Target(convId, messageId, System.nanoTime())
    }

    /** Clears [target] unless a newer request replaced it meanwhile. */
    fun consume(target: Target) {
        _pending.compareAndSet(target, null)
    }
}

// ---- usage numbers ------------------------------------------------------------------------------------------------------

/** 950 -> "950", 1234 -> "1.2k", 12345 -> "12k", 1234567 -> "1.2M" (decimal separator of [locale]). */
internal fun compactTokens(n: Long, locale: Locale = Locale.getDefault()): String {
    val v = if (n < 0L) 0L else n
    val sep = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    fun oneDecimal(x: Double, suffix: String): String {
        val tenths = Math.round(x * 10.0)
        val whole = tenths / 10L
        val frac = tenths % 10L
        return if (frac == 0L) "$whole$suffix" else "$whole$sep$frac$suffix"
    }
    return when {
        v < 1_000L -> v.toString()
        v < 10_000L -> oneDecimal(v / 1_000.0, "k")
        v < 999_500L -> ((v + 500L) / 1_000L).toString() + "k"
        else -> oneDecimal(v / 1_000_000.0, "M")
    }
}

/** What a chat has used so far: answered turns and the tokens of all of them. */
internal data class ChatUsageTotals(val replies: Int, val tokensIn: Long, val tokensOut: Long) {
    val isEmpty: Boolean get() = replies == 0 && tokensIn == 0L && tokensOut == 0L
}

internal fun chatUsageTotals(msgs: List<Message>): ChatUsageTotals {
    var replies = 0
    var tin = 0L
    var tout = 0L
    for (m in msgs) {
        if (m.tokensIn > 0L || m.tokensOut > 0L) {
            replies++
            tin += m.tokensIn
            tout += m.tokensOut
        }
    }
    return ChatUsageTotals(replies, tin, tout)
}

// ---- export ----------------------------------------------------------------------------------------------------------------

/** The words an exported chat uses for its speakers; English defaults, the chat screen passes translated ones. */
internal data class ExportLabels(
    val you: String = "You",
    val tool: String = "Tool",
    val error: String = "Error",
    /** Speaker name of a BOT message whose bot no longer exists. */
    val bot: String = "Bot",
)

/** The whole chat as Markdown. [botName] gives the speaker label of a BOT message. */
internal fun exportMarkdown(
    title: String,
    msgs: List<Message>,
    labels: ExportLabels = ExportLabels(),
    botName: (Message) -> String,
): String = buildString {
    appendLine("# $title")
    appendLine()
    msgs.forEach { m ->
        when (m.role) {
            Role.USER -> appendLine("**${labels.you}:** ${m.text}")
            Role.BOT -> appendLine("**${botName(m)}:** ${m.text}")
            Role.TOOL -> appendLine("> ${labels.tool} `${m.toolName ?: labels.tool.lowercase()}`: ${m.text}")
            Role.NOTE -> appendLine("_${m.text}_")
            Role.ERROR -> appendLine("**${labels.error}:** ${m.text}")
        }
        appendLine()
    }
}.trim()

/** A file name for an exported chat: letters, digits, '-' and '_' only, at most 48 characters, always ending in `.md`. */
internal fun exportFileName(title: String): String {
    val cleaned = buildString {
        for (ch in title.trim()) append(if (ch.isLetterOrDigit() || ch == '-') ch else '_')
    }.replace(Regex("_+"), "_").take(48).trim('_', '-')
    return (cleaned.ifBlank { "chat" }) + ".md"
}
