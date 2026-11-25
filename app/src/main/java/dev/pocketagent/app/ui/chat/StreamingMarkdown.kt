package dev.pocketagent.app.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.ui.theme.AnimLevel
import dev.pocketagent.app.ui.theme.Pocket
import kotlinx.coroutines.flow.first

/** How many of the newest characters a streaming message draws with a soft edge (see [StreamSmoother.FADE_ALPHAS]). */
private const val FADE_CHARS = 8

/**
 * The text of a bot message. While it streams, a [StreamSmoother] releases it frame by frame and a [MarkdownStream] keeps the
 * blocks that are finished, so only the open end is parsed and drawn again. A message that is already complete when it is
 * first drawn (an old one, scrolled into view) takes the cheap path: one parse, no frame loop.
 *
 * Only this composable reads the per-frame state, so only it recomposes while the answer grows.
 */
@Composable
internal fun StreamingMarkdown(
    text: String,
    streaming: Boolean,
    createdAtMs: Long,
    modifier: Modifier = Modifier,
    color: Color = Pocket.colors.onBotBubble,
    fontSize: TextUnit = 15.sp,
    onRunCode: ((lang: String, code: String) -> Unit)? = null,
) {
    val first = remember { StreamSmoother.initialShown(text.length, System.currentTimeMillis() - createdAtMs) }
    // Decided once per row: a finished message stays on the cheap path, a message that was drawn while streaming stays on the other.
    val staticMode = remember { !streaming && first == text.length }
    if (staticMode) {
        val blocks = remember(text) { parseMarkdown(text) }
        MdBlocks(blocks, emptyList(), color, fontSize, onRunCode, 0, modifier)
    } else {
        StreamingBody(text, streaming, first, modifier, color, fontSize, onRunCode)
    }
}

@Composable
private fun StreamingBody(
    text: String,
    streaming: Boolean,
    firstShown: Int,
    modifier: Modifier,
    color: Color,
    fontSize: TextUnit,
    onRunCode: ((lang: String, code: String) -> Unit)?,
) {
    val level = Pocket.anim
    val smoother = remember { StreamSmoother().also { it.reset(firstShown) } }
    val stream = remember { MarkdownStream() }
    val shownState = remember { mutableIntStateOf(firstShown) }
    val textNow by rememberUpdatedState(text)
    val finishedNow by rememberUpdatedState(!streaming)

    LaunchedEffect(level) {
        if (level == AnimLevel.OFF) {
            smoother.reset(textNow.length)
            shownState.intValue = textNow.length
            return@LaunchedEffect
        }
        while (true) {
            // Idle: no frame callbacks until the text and what is shown differ.
            snapshotFlow { shownState.intValue != textNow.length }.first { it }
            while (shownState.intValue != textNow.length) {
                val t = withFrameNanos { it }
                shownState.intValue = smoother.step(t, textNow, finishedNow)
            }
        }
    }

    val upTo = if (level == AnimLevel.OFF) text.length else minOf(shownState.intValue, text.length)
    val snap = stream.sync(text, upTo)
    val fade = if (level == AnimLevel.FULL && (streaming || upTo < text.length)) FADE_CHARS else 0
    MdBlocks(snap.stable, snap.tail, color, fontSize, onRunCode, fade, modifier)
}
