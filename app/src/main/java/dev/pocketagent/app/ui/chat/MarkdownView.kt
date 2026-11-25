package dev.pocketagent.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.R
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconView
import dev.pocketagent.app.ui.theme.Pocket
import kotlinx.coroutines.delay

/**
 * Renders chat Markdown: headings, bold/italic/strike, inline code, fenced code (copy button, horizontal scroll),
 * lists, quotes, tables, rules and clickable links. The text is parsed once per distinct string.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Pocket.colors.onBotBubble,
    fontSize: TextUnit = 15.sp,
    onRunCode: ((lang: String, code: String) -> Unit)? = null,
) {
    val blocks = remember(text) { parseMarkdown(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEachIndexed { index, block -> key(index) { MdBlockView(block, color, fontSize, onRunCode) } }
    }
}

/**
 * The blocks of a streamed message (see [MarkdownStream]): every block is keyed by its absolute index, so a block that
 * moves from [tail] to [stable] keeps its composition. Only the last tail block shows the soft edge of [fadeLast] characters.
 */
@Composable
internal fun MdBlocks(
    stable: List<MdBlock>,
    tail: List<MdBlock>,
    color: Color,
    fontSize: TextUnit,
    onRunCode: ((lang: String, code: String) -> Unit)?,
    fadeLast: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        stable.forEachIndexed { index, block -> key(index) { MdBlockView(block, color, fontSize, onRunCode) } }
        val n = stable.size
        tail.forEachIndexed { i, block ->
            key(n + i) { MdBlockView(block, color, fontSize, onRunCode, if (i == tail.lastIndex) fadeLast else 0) }
        }
    }
}

@Composable
private fun MdBlockView(
    block: MdBlock,
    color: Color,
    fontSize: TextUnit,
    onRunCode: ((lang: String, code: String) -> Unit)?,
    fadeLast: Int = 0,
) {
    val c = Pocket.colors
    when (block) {
        is MdBlock.Paragraph -> Text(
            rememberAnnotated(block.spans, fadeLast, color), color = color, fontSize = fontSize, lineHeight = fontSize * 1.45f,
        )
        is MdBlock.Heading -> {
            val size = when (block.level) { 1 -> fontSize * 1.35f; 2 -> fontSize * 1.2f; 3 -> fontSize * 1.1f; else -> fontSize }
            Text(
                rememberAnnotated(block.spans, fadeLast, color), color = color, fontSize = size, fontWeight = FontWeight.Bold,
                lineHeight = size * 1.3f, modifier = Modifier.padding(top = 4.dp),
            )
        }
        is MdBlock.Item -> Row(Modifier.padding(start = (block.depth * 16).dp)) {
            Text(
                block.marker, color = color.copy(alpha = 0.75f), fontSize = fontSize,
                modifier = Modifier.width(if (block.marker.length > 2) 28.dp else 20.dp),
            )
            Text(
                rememberAnnotated(block.spans, fadeLast, color), color = color, fontSize = fontSize, lineHeight = fontSize * 1.4f,
                modifier = Modifier.weight(1f),
            )
        }
        is MdBlock.Code -> CodeBlock(block, onRunCode)
        is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(c.accent))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                block.blocks.forEachIndexed { i, b -> key(i) { MdBlockView(b, color.copy(alpha = 0.85f), fontSize, onRunCode) } }
            }
        }
        is MdBlock.Table -> TableView(block, color, fontSize)
        MdBlock.Rule -> Box(Modifier.fillMaxWidth().height(1.dp).background(c.outline))
    }
}

@Composable
private fun rememberAnnotated(spans: List<MdSpan>, fadeLast: Int = 0, baseColor: Color = Color.Unspecified): AnnotatedString {
    val c = Pocket.colors
    val uri = LocalUriHandler.current
    return remember(spans, c.codeBg, c.accent, uri, fadeLast, baseColor) {
        buildSpans(spans, c.codeBg, c.accent, uri, fadeLast, baseColor)
    }
}

private fun isSafeUrl(url: String): Boolean =
    url.startsWith("http://", true) || url.startsWith("https://", true) || url.startsWith("mailto:", true)

private fun buildSpans(
    spans: List<MdSpan>,
    codeBg: Color,
    linkColor: Color,
    uri: UriHandler,
    fadeLast: Int = 0,
    baseColor: Color = Color.Unspecified,
): AnnotatedString =
    buildAnnotatedString {
        for (s in spans) {
            val style = SpanStyle(
                fontWeight = if (s.bold) FontWeight.Bold else null,
                fontStyle = if (s.italic) FontStyle.Italic else null,
                fontFamily = if (s.code) FontFamily.Monospace else null,
                background = if (s.code) codeBg else Color.Unspecified,
                textDecoration = if (s.strike) TextDecoration.LineThrough else null,
                fontSize = if (s.code) 13.5.sp else TextUnit.Unspecified,
            )
            val url = s.url
            if (url != null && isSafeUrl(url)) {
                val link = LinkAnnotation.Url(
                    url,
                    TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                    LinkInteractionListener { try { uri.openUri(url) } catch (_: Exception) { /* no app to open it */ } },
                )
                withLink(link) { withStyle(style) { append(s.text) } }
            } else {
                withStyle(style) { append(s.text) }
            }
        }
        // The soft edge of a streaming message: the newest characters are drawn lighter, the oldest of them almost opaque.
        if (fadeLast > 0 && baseColor != Color.Unspecified) {
            val len = length
            val n = minOf(fadeLast, len, StreamSmoother.FADE_ALPHAS.size)
            for (k in 0 until n) {
                addStyle(
                    SpanStyle(color = baseColor.copy(alpha = baseColor.alpha * StreamSmoother.FADE_ALPHAS[k])),
                    len - 1 - k, len - k,
                )
            }
        }
    }

@Composable
private fun CodeBlock(block: MdBlock.Code, onRunCode: ((lang: String, code: String) -> Unit)?) {
    val c = Pocket.colors
    val ctx = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }
    val highlighted = remember(block.code, block.lang, c.isDark, c.textMuted) {
        SyntaxHighlight.highlight(block.code, block.lang, c)
    }
    val runnable = onRunCode != null && SyntaxHighlight.isRunnable(block.lang, block.code)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(c.codeBg).border(1.dp, c.outline.copy(alpha = 0.45f), RoundedCornerShape(6.dp))) {
        Row(
            Modifier.fillMaxWidth().background(c.bgDeep.copy(alpha = 0.65f)).padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                block.lang.ifBlank { "code" }, color = c.textMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f), maxLines = 1,
            )
            if (runnable && onRunCode != null) {
                val runLabel = stringResource(R.string.ui_chat_code_run)
                Row(
                    Modifier
                        .clickable(onClickLabel = runLabel, role = Role.Button) { onRunCode(block.lang, block.code) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PixelIconView(PixelIcon.TERMINAL, tint = c.accent, size = 14.dp)
                    Text(runLabel, color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            val label = stringResource(R.string.ui_chat_code_copy)
            Box(
                Modifier.size(38.dp).clickable(onClickLabel = label, role = Role.Button) {
                    copyToClipboard(ctx, block.code)
                    dev.pocketagent.app.PocketApp.from(ctx).sounds.play(dev.pocketagent.app.voice.SoundCue.TAP)
                    copied = true
                },
                contentAlignment = Alignment.Center,
            ) {
                PixelIconView(if (copied) PixelIcon.CHECK else PixelIcon.COPY, tint = if (copied) c.success else c.textMuted, size = 16.dp)
            }
        }
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Text(
                highlighted, color = c.text, fontSize = 12.5.sp, fontFamily = FontFamily.Monospace, lineHeight = 18.sp,
                softWrap = false, modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
            )
        }
    }
}

@Composable
private fun TableView(table: MdBlock.Table, color: Color, fontSize: TextUnit) {
    val c = Pocket.colors
    val cols = maxOf(table.header.size, table.rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
    Column(Modifier.horizontalScroll(rememberScrollState()).border(1.dp, c.outline)) {
        MdTableRow(table.header, true, cols, color, fontSize)
        table.rows.forEachIndexed { i, r -> key(i) { MdTableRow(r, false, cols, color, fontSize) } }
    }
}

@Composable
private fun MdTableRow(cells: List<List<MdSpan>>, header: Boolean, cols: Int, color: Color, fontSize: TextUnit) {
    val c = Pocket.colors
    Row(Modifier.background(if (header) c.surfaceHigh else Color.Transparent)) {
        for (col in 0 until cols) {
            val spans = cells.getOrNull(col).orEmpty()
            Box(Modifier.width(112.dp).border(0.5.dp, c.outline).padding(6.dp)) {
                Text(
                    rememberAnnotated(spans), color = color, fontSize = fontSize * 0.9f,
                    fontWeight = if (header) FontWeight.Bold else null,
                )
            }
        }
    }
}
