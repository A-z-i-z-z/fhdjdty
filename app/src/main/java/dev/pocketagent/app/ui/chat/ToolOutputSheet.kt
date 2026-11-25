package dev.pocketagent.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.R
import dev.pocketagent.app.data.Message
import dev.pocketagent.app.ui.components.ButtonKind
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PocketSheet
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.Space
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.text.NumberFormat
import dev.pocketagent.app.ui.text.t
import dev.pocketagent.app.ui.text.tn

/** True while a TOOL message is still running (its card and the sheet show the same status). */
internal fun isToolRunning(msg: Message): Boolean = msg.streaming || msg.toolStatus.orEmpty().lowercase() == "running"

/** True when a TOOL message was stopped or failed. */
internal fun isToolFailed(msg: Message): Boolean {
    val status = msg.toolStatus.orEmpty().lowercase()
    return status.contains("interrupt") || status.contains("error") || status.contains("fail")
}

/**
 * The whole output of one tool call in a bottom sheet (the card in the chat only shows its first line). The sheet watches
 * the one message, so the text grows live while the tool runs, and closes itself when the message is gone.
 */
@Composable
internal fun ToolOutputSheet(messageId: String, messages: StateFlow<List<Message>>, onDismiss: () -> Unit) {
    val flow = remember(messageId, messages) {
        messages.map { list -> list.firstOrNull { it.id == messageId } }.distinctUntilChanged()
    }
    val current by flow.collectAsState(initial = messages.value.firstOrNull { it.id == messageId })
    val msg = current
    LaunchedEffect(msg == null) { if (msg == null) onDismiss() }
    if (msg == null) return

    val ctx = LocalContext.current
    val c = Pocket.colors
    val running = isToolRunning(msg)
    val failed = isToolFailed(msg)
    val title = msg.toolName ?: t(R.string.stm_tool_sheet_fallback_title)
    val chunks = remember(msg.text) { ToolOutputChunks.split(msg.text) }
    val total = msg.text.length
    val truncated = total > ToolOutputChunks.MAX_CHARS
    val statusText = t(
        when {
            running -> R.string.ui_chat_tool_running
            failed -> R.string.ui_chat_tool_stopped
            else -> R.string.ui_chat_tool_done
        },
    )
    val numbers = NumberFormat.getIntegerInstance(currentLocale())

    PocketSheet(onDismiss = onDismiss, title = title) {
        // PocketSheetScope is a ColumnScope (a DSL marker): the inner Column and Row cannot reach it implicitly.
        val sheet = this
        Column(Modifier.fillMaxWidth().testTag("chat.tool.sheet").padding(horizontal = Space.gutter)) {
            Text(statusText, color = if (failed) c.error else c.textMuted, fontSize = 12.sp)
            if (chunks.isEmpty()) {
                Text(
                    t(R.string.ui_chat_no_output), color = c.textMuted, fontSize = 13.sp,
                    modifier = Modifier.padding(top = Space.m),
                )
            } else {
                // What Hermes's stream carries for a tool call is its name and a preview of the command, never what it printed.
                Text(
                    t(R.string.ui_chat_tool_command), color = c.textMuted, fontSize = 12.sp,
                    modifier = Modifier.padding(top = Space.m),
                )
                SelectionContainer(Modifier.weight(1f, fill = false)) {
                    LazyColumn(
                        Modifier.padding(top = Space.xxs).fillMaxWidth().background(c.codeBg).testTag("chat.tool.output"),
                    ) {
                        items(chunks.size) { index ->
                            Text(
                                chunks[index], color = c.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 16.sp,
                                modifier = Modifier.padding(horizontal = Space.s),
                            )
                        }
                    }
                }
            }
            if (truncated) {
                Text(
                    tn(
                        R.plurals.stm_tool_sheet_truncated, ToolOutputChunks.MAX_CHARS,
                        numbers.format(ToolOutputChunks.MAX_CHARS), numbers.format(total),
                    ),
                    color = c.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = Space.s),
                )
            }
            // Said plainly, so an empty area is never mistaken for a tool that printed nothing (Hermes does not send tool output on this API).
            Text(
                t(R.string.ui_chat_tool_no_output_note), color = c.textMuted, fontSize = 12.sp,
                modifier = Modifier.padding(top = Space.s).testTag("chat.tool.note"),
            )
            Row(
                Modifier.fillMaxWidth().padding(vertical = Space.m),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                PixelButton(
                    t(R.string.ui_chat_tool_copy_command),
                    onClick = { copyToClipboard(ctx, msg.text) },
                    modifier = Modifier.testTag("chat.tool.copy"),
                    icon = PixelIcon.COPY,
                )
                PixelButton(
                    t(R.string.ui_dlg_close),
                    onClick = { sheet.close() },
                    modifier = Modifier.testTag("chat.tool.close"),
                    kind = ButtonKind.GHOST,
                )
            }
        }
    }
}

/** Cuts a long tool output into pieces a lazy list can lay out one at a time. Pure. */
internal object ToolOutputChunks {
    const val MAX_CHARS = 60_000
    const val CHUNK_CHARS = 2_000

    /**
     * text.take(maxChars) (one char less when that would split a surrogate pair) cut into pieces of at most [chunk] chars, each
     * ending after a newline when one exists in the last quarter of the piece. join("") of the result == the kept prefix.
     * "" -> emptyList().
     */
    fun split(text: String, maxChars: Int = MAX_CHARS, chunk: Int = CHUNK_CHARS): List<String> {
        if (text.isEmpty() || maxChars <= 0 || chunk <= 0) return emptyList()
        var end = minOf(text.length, maxChars)
        if (end < text.length && end > 0 && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
        val out = ArrayList<String>()
        var start = 0
        while (start < end) {
            var stop = minOf(start + chunk, end)
            if (stop < end) {
                // Keep a surrogate pair together.
                if (text[stop - 1].isHighSurrogate() && text[stop].isLowSurrogate()) stop--
                // Prefer ending after a newline that sits in the last quarter of the piece.
                val floor = start + (stop - start) * 3 / 4
                val nl = text.lastIndexOf('\n', stop - 1)
                if (nl >= floor && nl + 1 > start) stop = nl + 1
            }
            if (stop <= start) stop = minOf(start + chunk, end).coerceAtLeast(start + 1)
            out += text.substring(start, stop)
            start = stop
        }
        return out
    }
}
