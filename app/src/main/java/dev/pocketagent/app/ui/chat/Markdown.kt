package dev.pocketagent.app.ui.chat

import androidx.compose.runtime.Immutable

// A small Markdown parser for chat messages (agent CHAT). Pure Kotlin: no Android or Compose state, so it is
// cheap to call from remember(text). It never throws: whatever it cannot understand stays plain text, which is
// what a half-streamed message needs (an unclosed ``` fence is simply a code block that is still growing).

/** One run of inline text with a uniform style. */
@Immutable
data class MdSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strike: Boolean = false,
    val url: String? = null,
)

sealed interface MdBlock {
    @Immutable data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock
    @Immutable data class Paragraph(val spans: List<MdSpan>) : MdBlock
    @Immutable data class Code(val lang: String, val code: String) : MdBlock
    /** One list entry; nested lists are flattened with a [depth]. [marker] is "•" or "1.". */
    @Immutable data class Item(val depth: Int, val marker: String, val spans: List<MdSpan>) : MdBlock
    @Immutable data class Quote(val blocks: List<MdBlock>) : MdBlock
    @Immutable data class Table(val header: List<List<MdSpan>>, val rows: List<List<List<MdSpan>>>) : MdBlock
    data object Rule : MdBlock
}

private val FENCE = Regex("^\\s{0,3}(`{3,}|~{3,})\\s*([\\w+#.-]*).*$")
private val HEADING = Regex("^\\s{0,3}(#{1,6})\\s+(.*)$")
private val RULE = Regex("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$")
private val ITEM = Regex("^(\\s*)([-*+]|\\d{1,9}[.)])\\s+(.*)$")
private val QUOTE = Regex("^\\s{0,3}>\\s?(.*)$")
private val TABLE_SEP = Regex("^\\s*\\|?\\s*:?-+:?\\s*(\\|\\s*:?-+:?\\s*)*\\|?\\s*$")
private val BR = Regex("(?i)<br\\s*/?>")

fun parseMarkdown(src: String): List<MdBlock> =
    parseBlocks(src.replace("\r\n", "\n").replace('\r', '\n').split('\n'))

private fun parseBlocks(lines: List<String>): List<MdBlock> {
    val out = ArrayList<MdBlock>()
    val para = StringBuilder()
    fun flushPara() {
        if (para.isNotEmpty()) { out += MdBlock.Paragraph(parseInline(para.toString())); para.clear() }
    }
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (line.isBlank()) { flushPara(); i++; continue }

        val fence = FENCE.matchEntire(line)
        if (fence != null) {
            flushPara()
            val marker = fence.groupValues[1]
            val lang = fence.groupValues[2]
            val code = ArrayList<String>()
            i++
            while (i < lines.size && !isClosingFence(lines[i], marker)) { code += lines[i]; i++ }
            if (i < lines.size) i++ // the closing fence
            out += MdBlock.Code(lang, code.joinToString("\n"))
            continue
        }

        val heading = HEADING.matchEntire(line)
        if (heading != null) {
            flushPara()
            val text = heading.groupValues[2].trim().trimEnd('#').trim()
            out += MdBlock.Heading(heading.groupValues[1].length, parseInline(text))
            i++; continue
        }

        if (RULE.matches(line)) { flushPara(); out += MdBlock.Rule; i++; continue }

        if (QUOTE.matches(line)) {
            flushPara()
            val inner = ArrayList<String>()
            while (i < lines.size) {
                val q = QUOTE.matchEntire(lines[i]) ?: break
                inner += q.groupValues[1]; i++
            }
            out += MdBlock.Quote(parseBlocks(inner))
            continue
        }

        if (line.contains('|') && i + 1 < lines.size && lines[i + 1].contains('|') && TABLE_SEP.matches(lines[i + 1])) {
            flushPara()
            val header = splitRow(line)
            i += 2
            val rows = ArrayList<List<List<MdSpan>>>()
            while (i < lines.size && lines[i].isNotBlank() && lines[i].contains('|')) { rows += splitRow(lines[i]); i++ }
            out += MdBlock.Table(header, rows)
            continue
        }

        val item = ITEM.matchEntire(line)
        if (item != null) {
            flushPara()
            val indent = item.groupValues[1].replace("\t", "    ").length
            val rawMarker = item.groupValues[2]
            val marker = if (rawMarker[0].isDigit()) rawMarker.dropLast(1) + "." else "•"
            val text = StringBuilder(item.groupValues[3])
            i++
            // Continuation lines: indented, not another item, not a fence.
            while (i < lines.size && lines[i].isNotBlank() && lines[i].startsWith("  ") &&
                !ITEM.matches(lines[i]) && !FENCE.matches(lines[i])
            ) { text.append('\n').append(lines[i].trim()); i++ }
            out += MdBlock.Item((indent / 2).coerceAtMost(4), marker, parseInline(text.toString()))
            continue
        }

        if (para.isNotEmpty()) para.append('\n')
        para.append(line.trim())
        i++
    }
    flushPara()
    return out
}

private fun isClosingFence(line: String, marker: String): Boolean {
    val t = line.trim()
    return t.length >= marker.length && t.all { it == marker[0] }
}

private fun splitRow(line: String): List<List<MdSpan>> {
    var t = line.trim()
    if (t.startsWith("|")) t = t.substring(1)
    if (t.endsWith("|")) t = t.dropLast(1)
    return t.split('|').map { parseInline(it.trim()) }
}

// ---- inline ----------------------------------------------------------------------------------------------------

private const val ESCAPABLE = "\\`*_{}[]()#+-.!|~>"

fun parseInline(src: String): List<MdSpan> {
    val out = ArrayList<MdSpan>()
    inlineInto(BR.replace(src, "\n"), MdSpan(""), out)
    return merge(out)
}

private fun inlineInto(s: String, base: MdSpan, out: MutableList<MdSpan>) {
    val sb = StringBuilder()
    fun flush() { if (sb.isNotEmpty()) { out += base.copy(text = sb.toString()); sb.clear() } }
    var i = 0
    while (i < s.length) {
        val ch = s[i]
        var handled = false

        if (ch == '\\' && i + 1 < s.length && s[i + 1] in ESCAPABLE) {
            sb.append(s[i + 1]); i += 2; continue
        }

        if (ch == '`') {
            var n = 1
            while (i + n < s.length && s[i + n] == '`') n++
            val fence = "`".repeat(n)
            val end = s.indexOf(fence, i + n)
            if (end > i + n) {
                flush()
                var code = s.substring(i + n, end)
                if (code.length > 2 && code.startsWith(" ") && code.endsWith(" ")) code = code.substring(1, code.length - 1)
                out += base.copy(text = code, code = true, url = null)
                i = end + n
            } else { sb.append(fence); i += n }
            continue
        }

        if (ch == '*' || ch == '_') {
            val d = ch.toString()
            if (s.startsWith(d.repeat(3), i) && canOpen(s, i, 3, ch)) {
                val close = findClose(s, i + 3, d.repeat(3), ch)
                if (close > i + 3) {
                    flush(); inlineInto(s.substring(i + 3, close), base.copy(bold = true, italic = true), out)
                    i = close + 3; handled = true
                }
            }
            if (!handled && s.startsWith(d.repeat(2), i) && canOpen(s, i, 2, ch)) {
                val close = findClose(s, i + 2, d.repeat(2), ch)
                if (close > i + 2) {
                    flush(); inlineInto(s.substring(i + 2, close), base.copy(bold = true), out)
                    i = close + 2; handled = true
                }
            }
            if (!handled && canOpen(s, i, 1, ch)) {
                val close = findClose(s, i + 1, d, ch)
                if (close > i + 1) {
                    flush(); inlineInto(s.substring(i + 1, close), base.copy(italic = true), out)
                    i = close + 1; handled = true
                }
            }
            if (!handled) { sb.append(ch); i++ }
            continue
        }

        if (ch == '~' && s.startsWith("~~", i) && i + 2 < s.length && !s[i + 2].isWhitespace()) {
            val close = s.indexOf("~~", i + 2)
            if (close > i + 2) {
                flush(); inlineInto(s.substring(i + 2, close), base.copy(strike = true), out)
                i = close + 2; continue
            }
        }

        if (ch == '[' && base.url == null) {
            val mid = s.indexOf("](", i + 1)
            if (mid > i) {
                val closeParen = s.indexOf(')', mid + 2)
                val label = s.substring(i + 1, mid)
                if (closeParen > mid && !label.contains('\n')) {
                    val url = s.substring(mid + 2, closeParen).trim().substringBefore(' ')
                    if (url.isNotEmpty()) {
                        flush(); inlineInto(label, base.copy(url = url), out)
                        i = closeParen + 1; continue
                    }
                }
            }
        }

        if (ch == 'h' && base.url == null && (s.startsWith("http://", i) || s.startsWith("https://", i)) &&
            (i == 0 || !s[i - 1].isLetterOrDigit())
        ) {
            var end = i
            while (end < s.length && !s[end].isWhitespace() && s[end] != '<' && s[end] != '>') end++
            var url = s.substring(i, end)
            while (url.isNotEmpty() && (url.last() in ".,;:!?'\"" || (url.last() == ')' && url.count { it == ')' } > url.count { it == '(' }))) {
                url = url.dropLast(1)
            }
            if (url.length > 8) {
                flush(); out += base.copy(text = url, url = url)
                i += url.length; continue
            }
        }

        sb.append(ch); i++
    }
    flush()
}

/** An emphasis delimiter run of [len] at [i] may open when text follows it and, for '_', it starts a word. */
private fun canOpen(s: String, i: Int, len: Int, ch: Char): Boolean {
    val next = i + len
    if (next >= s.length || s[next].isWhitespace() || s[next] == ch) return false
    if (ch == '_' && i > 0 && s[i - 1].isLetterOrDigit()) return false
    return true
}

private fun findClose(s: String, from: Int, delim: String, ch: Char): Int {
    var j = from
    while (j < s.length) {
        val c = s[j]
        if (c == '\\') { j += 2; continue }
        if (c == '`') { // skip over inline code
            val e = s.indexOf('`', j + 1)
            if (e > 0) { j = e + 1; continue }
        }
        if (s.startsWith(delim, j) && j > from && !s[j - 1].isWhitespace()) {
            val after = j + delim.length
            if (ch == '_' && after < s.length && s[after].isLetterOrDigit()) { j++; continue }
            if (delim.length == 1) {
                // A single delimiter must not be half of a double one.
                if (s[j - 1] == ch) { j++; continue }
                if (after < s.length && s[after] == ch) { j += 2; continue }
            }
            return j
        }
        j++
    }
    return -1
}

private fun merge(spans: List<MdSpan>): List<MdSpan> {
    val out = ArrayList<MdSpan>()
    for (s in spans) {
        if (s.text.isEmpty()) continue
        val last = out.lastOrNull()
        if (last != null && last.bold == s.bold && last.italic == s.italic && last.code == s.code &&
            last.strike == s.strike && last.url == s.url && !s.code
        ) out[out.size - 1] = last.copy(text = last.text + s.text) else out += s
    }
    return out
}
