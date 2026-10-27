package app.yerel.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Markdown blocks the chat renders. Unclosed code fences (while streaming) still render as code. */
sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class Code(val lang: String, val code: String) : MdBlock()
    data class ListItem(val ordered: Boolean, val marker: String, val depth: Int, val text: String) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock()
    data object Rule : MdBlock()
}

object MarkdownParser {
    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val BULLET = Regex("^(\\s*)([-*+•])\\s+(.*)$")
    private val ORDERED = Regex("^(\\s*)(\\d{1,3})[.)]\\s+(.*)$")
    private val RULE = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")
    private val TABLE_SEP = Regex("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$")

    fun parse(text: String): List<MdBlock> {
        val out = mutableListOf<MdBlock>()
        val lines = text.replace("\r\n", "\n").split('\n')
        val para = StringBuilder()
        fun flush() {
            if (para.isNotBlank()) out += MdBlock.Paragraph(para.toString().trim())
            para.clear()
        }
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trimStart()
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                flush()
                val fence = trimmed.take(3)
                val lang = trimmed.drop(3).trim()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith(fence)) {
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(lines[i])
                    i++
                }
                out += MdBlock.Code(lang, code.toString())
                i++
                continue
            }
            if (trimmed.isEmpty()) { flush(); i++; continue }
            val heading = HEADING.find(trimmed)
            if (heading != null) {
                flush()
                out += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim().trimEnd('#').trim())
                i++
                continue
            }
            if (RULE.matches(line)) { flush(); out += MdBlock.Rule; i++; continue }
            if (trimmed.startsWith("|") && i + 1 < lines.size && TABLE_SEP.matches(lines[i + 1])) {
                flush()
                val header = cells(trimmed)
                val rows = mutableListOf<List<String>>()
                i += 2
                while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                    rows += cells(lines[i].trim())
                    i++
                }
                out += MdBlock.Table(header, rows)
                continue
            }
            if (trimmed.startsWith(">")) {
                flush()
                val q = StringBuilder()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    if (q.isNotEmpty()) q.append('\n')
                    q.append(lines[i].trimStart().removePrefix(">").removePrefix(" "))
                    i++
                }
                out += MdBlock.Quote(q.toString())
                continue
            }
            val bullet = BULLET.find(line)
            val ordered = if (bullet == null) ORDERED.find(line) else null
            if (bullet != null || ordered != null) {
                flush()
                val m = bullet ?: ordered!!
                val depth = (m.groupValues[1].replace("\t", "    ").length / 2).coerceAtMost(4)
                val item = StringBuilder(m.groupValues[3])
                i++
                // lazy continuation lines belong to the item
                while (i < lines.size && lines[i].isNotBlank() && lines[i].startsWith("  ") &&
                    BULLET.find(lines[i]) == null && ORDERED.find(lines[i]) == null
                ) {
                    item.append(' ').append(lines[i].trim())
                    i++
                }
                out += MdBlock.ListItem(ordered != null, if (ordered != null) m.groupValues[2] + "." else "•", depth, item.toString())
                continue
            }
            if (para.isNotEmpty()) para.append('\n')
            para.append(line)
            i++
        }
        flush()
        return out
    }

    private fun cells(row: String) = row.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }
}

private val INLINE = Regex(
    "(`+)(.+?)\\1" + // code
        "|\\*\\*(.+?)\\*\\*|__(.+?)__" + // bold
        "|~~(.+?)~~" + // strike
        "|(?<![\\w*])\\*(?!\\s)(.+?)(?<!\\s)\\*(?!\\*)|(?<![\\w_])_(?!\\s)(.+?)(?<!\\s)_(?![\\w_])" + // italic
        "|\\[([^\\]]+)]\\(([^)\\s]+)\\)" + // link
        "|(https?://[^\\s<>()\\[\\]]*[^\\s<>()\\[\\].,;:!?'\"])", // bare url
)

data class InlineColors(val code: Color, val codeBg: Color, val link: Color)

fun inlineMarkdown(text: String, colors: InlineColors): AnnotatedString = buildAnnotatedString {
    appendInline(text, colors)
}

private fun AnnotatedString.Builder.appendInline(text: String, colors: InlineColors) {
    var pos = 0
    for (m in INLINE.findAll(text)) {
        if (m.range.first < pos) continue
        append(text.substring(pos, m.range.first))
        val g = m.groups
        when {
            g[2] != null -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = colors.codeBg, color = colors.code, fontSize = 0.92.em)) {
                append(" " + g[2]!!.value + " ")
            }
            g[3] != null || g[4] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInline((g[3] ?: g[4])!!.value, colors) }
            g[5] != null -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { appendInline(g[5]!!.value, colors) }
            g[6] != null || g[7] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInline((g[6] ?: g[7])!!.value, colors) }
            g[8] != null -> withLink(LinkAnnotation.Url(g[9]!!.value, TextLinkStyles(SpanStyle(color = colors.link, textDecoration = TextDecoration.Underline)))) {
                append(g[8]!!.value)
            }
            g[10] != null -> withLink(LinkAnnotation.Url(g[10]!!.value, TextLinkStyles(SpanStyle(color = colors.link)))) {
                append(g[10]!!.value)
            }
        }
        pos = m.range.last + 1
    }
    append(text.substring(pos))
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyLarge, color: Color = MaterialTheme.colorScheme.onSurface) {
    val blocks = remember(text) { MarkdownParser.parse(text) }
    val scheme = MaterialTheme.colorScheme
    val colors = InlineColors(code = scheme.onSurface, codeBg = scheme.surfaceContainerHighest, link = scheme.primary)
    SelectionContainer(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (b in blocks) {
                when (b) {
                    is MdBlock.Heading -> Text(
                        inlineMarkdown(b.text, colors),
                        style = when (b.level) {
                            1 -> MaterialTheme.typography.titleLarge
                            2 -> MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp)
                            else -> MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp)
                        },
                        color = color,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    is MdBlock.Paragraph -> Text(inlineMarkdown(b.text, colors), style = style, color = color)
                    is MdBlock.Code -> CodeBlockView(b.lang, b.code)
                    is MdBlock.ListItem -> Row(Modifier.padding(start = (b.depth * 16).dp)) {
                        Text(
                            b.marker, style = style, color = if (b.ordered) color else scheme.onSurfaceVariant,
                            modifier = Modifier.widthIn(min = if (b.ordered) 22.dp else 16.dp),
                        )
                        Text(inlineMarkdown(b.text, colors), style = style, color = color)
                    }
                    is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                        Box(Modifier.width(3.dp).fillMaxHeight().background(scheme.outline, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(10.dp))
                        Text(inlineMarkdown(b.text, colors), style = style, color = scheme.onSurfaceVariant)
                    }
                    is MdBlock.Table -> MdTable(b, colors, style)
                    MdBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = 4.dp), color = scheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun MdTable(t: MdBlock.Table, colors: InlineColors, style: TextStyle) {
    val scheme = MaterialTheme.colorScheme
    val nCols = maxOf(t.header.size, t.rows.maxOfOrNull { it.size } ?: 0)
    val widths = (0 until nCols).map { c ->
        val chars = (listOf(t.header) + t.rows).maxOf { it.getOrNull(c)?.length ?: 0 }
        (chars * 8 + 24).coerceIn(64, 240).dp
    }
    Surface(shape = RoundedCornerShape(12.dp), color = scheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            listOf(t.header).plus(t.rows).forEachIndexed { r, row ->
                Row(Modifier.background(if (r == 0) scheme.surfaceContainerHigh else Color.Transparent)) {
                    for (c in 0 until nCols) {
                        Text(
                            inlineMarkdown(row.getOrElse(c) { "" }, colors),
                            style = style.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = if (r == 0) FontWeight.SemiBold else null),
                            modifier = Modifier.width(widths[c]).padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
                if (r < t.rows.size) HorizontalDivider(color = scheme.outlineVariant)
            }
        }
    }
}
