package app.yerel.core

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONTokener
import org.xml.sax.SAXParseException
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/** A fenced code block of a Markdown answer. */
data class CodeBlock(val lang: String, val code: String) {
    val kind get() = CodeLang.of(lang, code)
}

enum class CodeLang(val label: String, val ext: String, val mime: String) {
    HTML("HTML", "html", "text/html"),
    SVG("SVG", "svg", "image/svg+xml"),
    JAVASCRIPT("JavaScript", "js", "text/javascript"),
    JSON("JSON", "json", "application/json"),
    CSS("CSS", "css", "text/css"),
    XML("XML", "xml", "text/xml"),
    PYTHON("Python", "py", "text/x-python"),
    KOTLIN("Kotlin", "kt", "text/plain"),
    OTHER("Kod", "txt", "text/plain");

    /** Can be shown in the in-app browser. */
    val previewable get() = this == HTML || this == SVG
    val runnable get() = this == JAVASCRIPT
    val checkable get() = this in setOf(HTML, SVG, JAVASCRIPT, JSON, CSS, XML)

    companion object {
        fun of(lang: String, code: String = ""): CodeLang {
            val l = lang.trim().lowercase()
            val head = code.trimStart().take(200).lowercase()
            return when {
                l in setOf("html", "htm", "xhtml") -> HTML
                l == "svg" || (l in setOf("xml", "") && head.startsWith("<svg")) -> SVG
                l in setOf("js", "javascript", "mjs", "node", "jsx") -> JAVASCRIPT
                l in setOf("json", "jsonc") -> JSON
                l in setOf("css", "scss") -> CSS
                l == "xml" -> XML
                l in setOf("py", "python", "python3") -> PYTHON
                l in setOf("kt", "kotlin") -> KOTLIN
                l.isEmpty() && (head.startsWith("<!doctype html") || head.startsWith("<html")) -> HTML
                else -> OTHER
            }
        }
    }
}

object CodeBlocks {
    private val FENCE = Regex("^ {0,3}(```+|~~~+)\\s*([^\\s`]*)[^\\n]*$")

    /** Fenced blocks in order; an unclosed last fence (cut-off answer) counts too. */
    fun extract(markdown: String): List<CodeBlock> {
        val out = mutableListOf<CodeBlock>()
        var fence: String? = null
        var lang = ""
        val buf = StringBuilder()
        for (line in markdown.lines()) {
            val m = FENCE.matchEntire(line)
            if (fence == null) {
                if (m != null) {
                    fence = m.groupValues[1]
                    lang = m.groupValues[2]
                    buf.setLength(0)
                }
            } else if (m != null && m.groupValues[1].startsWith(fence) && m.groupValues[2].isEmpty()) {
                out += CodeBlock(lang, buf.toString().trimEnd('\n'))
                fence = null
            } else {
                buf.append(line).append('\n')
            }
        }
        if (fence != null && buf.isNotBlank()) out += CodeBlock(lang, buf.toString().trimEnd('\n'))
        return out
    }

    /** A complete HTML page for the in-app preview. */
    fun previewHtml(block: CodeBlock): String = when (block.kind) {
        // no viewport-relative heights: the WebView can be laid out at height 0 first
        CodeLang.SVG -> """<!doctype html><html><head><meta charset="utf-8">
            |<meta name="viewport" content="width=device-width, initial-scale=1">
            |<style>html,body{margin:0;background:#eeeeee}body{padding:12px}
            |svg{display:block;margin:0 auto;max-width:100%;height:auto;background:#fff}</style></head><body>${withViewBox(block.code)}</body></html>""".trimMargin()
        else -> if (Regex("<html|<!doctype", RegexOption.IGNORE_CASE).containsMatchIn(block.code)) block.code else
            """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"></head>
            |<body>${block.code}</body></html>""".trimMargin()
    }

    private val SVG_OPEN = Regex("<svg\\b[^>]*>", RegexOption.IGNORE_CASE)

    /** Small models write width/height without a viewBox; adding one lets the image scale to the screen. */
    fun withViewBox(svg: String): String {
        val m = SVG_OPEN.find(svg) ?: return svg
        val tag = m.value
        if (Regex("\\bviewBox\\s*=", RegexOption.IGNORE_CASE).containsMatchIn(tag)) return svg
        fun size(name: String) = Regex("(?<![\\w-])$name\\s*=\\s*[\"']?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(?:px)?[\"'\\s/>]").find(tag)?.groupValues?.get(1)
        val w = size("width") ?: return svg
        val h = size("height") ?: return svg
        return svg.replaceRange(m.range, "<svg viewBox=\"0 0 $w $h\"" + tag.substring(4))
    }
}

/** Runs JavaScript in a sandbox; the Android implementation uses an offline WebView. Blocking. */
interface JsSandbox {
    fun run(code: String, timeoutMs: Long = 4000): JsRun

    /** null when the code parses, else the syntax error with its line. */
    fun syntaxError(code: String): String?
}

data class JsRun(val logs: List<String>, val result: String?, val error: String?, val timedOut: Boolean = false) {
    val ok get() = error == null && !timedOut

    fun toJson(): String = org.json.JSONObject()
        .put("ok", ok)
        .put("console", JSONArray(logs.take(200)))
        .put("result", result ?: org.json.JSONObject.NULL)
        .put("error", error ?: (if (timedOut) "timed out (infinite loop or too slow)" else org.json.JSONObject.NULL))
        .toString()
}

/** Static checks small models trip over: unbalanced HTML, broken SVG/XML, invalid JSON, JS syntax. */
object CodeCheck {
    fun check(lang: CodeLang, code: String, js: JsSandbox? = null): List<String> = when (lang) {
        CodeLang.JSON -> listOfNotNull(json(code))
        CodeLang.XML -> listOfNotNull(xml(code))
        CodeLang.SVG -> svg(code)
        CodeLang.HTML -> html(code, js)
        CodeLang.CSS -> css(code)
        CodeLang.JAVASCRIPT -> listOfNotNull(js?.syntaxError(code))
        else -> emptyList()
    }

    private fun lineOf(code: String, index: Int) = code.substring(0, index.coerceIn(0, code.length)).count { it == '\n' } + 1

    fun json(code: String): String? = try {
        val t = JSONTokener(code)
        t.nextValue()
        if (t.more() && t.nextClean() != 0.toChar()) "JSON: unexpected text after the value" else null
    } catch (e: JSONException) {
        val at = Regex("at character (\\d+)").find(e.message ?: "")?.groupValues?.get(1)?.toIntOrNull()
        "JSON: ${e.message?.substringBefore(" at character")}" + (at?.let { " (line ${lineOf(code, it)})" } ?: "")
    }

    private fun parseXml(code: String): org.w3c.dom.Document {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = true
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        val b = f.newDocumentBuilder()
        b.setErrorHandler(null)
        return b.parse(org.xml.sax.InputSource(StringReader(code.trim())))
    }

    fun xml(code: String): String? = try {
        parseXml(code)
        null
    } catch (e: SAXParseException) {
        "XML line ${e.lineNumber}: ${e.message}"
    } catch (e: Exception) {
        "XML: ${e.message}"
    }

    fun svg(code: String): List<String> {
        val doc = try {
            parseXml(code)
        } catch (e: SAXParseException) {
            return listOf("SVG is not well-formed XML, line ${e.lineNumber}: ${e.message}")
        } catch (e: Exception) {
            return listOf("SVG: ${e.message}")
        }
        val root = doc.documentElement
        val out = mutableListOf<String>()
        if (root.localName != "svg" && root.nodeName != "svg") out += "the root element must be <svg>, found <${root.nodeName}>"
        if (root.namespaceURI != "http://www.w3.org/2000/svg") out += "<svg> needs xmlns=\"http://www.w3.org/2000/svg\""
        if (!root.hasAttribute("viewBox") && !(root.hasAttribute("width") && root.hasAttribute("height"))) {
            out += "<svg> has neither a viewBox nor width and height, it may render empty"
        }
        return out
    }

    private val VOID = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr",
        "!doctype",
    )
    // elements whose end tag HTML lets you leave out
    private val OPTIONAL_END = setOf("p", "li", "dt", "dd", "option", "tr", "td", "th", "thead", "tbody", "tfoot", "html", "head", "body")
    private val TAG = Regex("<(/?)([a-zA-Z][a-zA-Z0-9-]*|!doctype)\\b[^>]*?(/?)>", RegexOption.IGNORE_CASE)
    private val RAW = Regex("<(script|style)\\b[^>]*>([\\s\\S]*?)</\\1\\s*>", RegexOption.IGNORE_CASE)

    fun html(code: String, js: JsSandbox? = null): List<String> {
        val out = mutableListOf<String>()
        // script and style bodies are not markup; blank them out keeping the line numbers
        val masked = StringBuilder(code)
        for (m in RAW.findAll(code)) {
            val body = m.groups[2]!!
            for (i in body.range) if (masked[i] != '\n') masked.setCharAt(i, ' ')
            val tag = m.groupValues[1].lowercase()
            val line = lineOf(code, body.range.first)
            if (tag == "script" && js != null && !Regex("type\\s*=\\s*[\"']?(module|text/template)", RegexOption.IGNORE_CASE).containsMatchIn(m.value.substringBefore('>'))) {
                js.syntaxError(body.value)?.let { out += "<script> at line $line: $it" }
            } else if (tag == "style") {
                css(body.value).forEach { out += "<style> at line $line: $it" }
            }
        }
        val text = masked.toString()
        if (Regex("<script\\b", RegexOption.IGNORE_CASE).findAll(text).count() > Regex("</script\\s*>", RegexOption.IGNORE_CASE).findAll(text).count()) {
            out += "a <script> element is not closed"
        }
        val stack = ArrayDeque<Pair<String, Int>>()
        for (m in TAG.findAll(text)) {
            val name = m.groupValues[2].lowercase()
            val closing = m.groupValues[1] == "/"
            val selfClosing = m.groupValues[3] == "/"
            if (name in VOID || selfClosing && !closing) continue
            val line = lineOf(text, m.range.first)
            if (!closing) {
                stack.addLast(name to line)
                continue
            }
            val idx = stack.indexOfLast { it.first == name }
            if (idx < 0) {
                out += "line $line: </$name> has no matching <$name>"
                continue
            }
            while (stack.size > idx + 1) {
                val (open, l) = stack.removeLast()
                if (open !in OPTIONAL_END) out += "line $l: <$open> is not closed before </$name> (line $line)"
            }
            stack.removeLast()
        }
        for ((open, l) in stack) if (open !in OPTIONAL_END) out += "line $l: <$open> is never closed"
        return out.take(12)
    }

    fun css(code: String): List<String> {
        var depth = 0
        var line = 1
        var inComment = false
        var quote: Char? = null
        var i = 0
        while (i < code.length) {
            val c = code[i]
            if (c == '\n') line++
            when {
                inComment -> if (c == '*' && code.getOrNull(i + 1) == '/') { inComment = false; i++ }
                quote != null -> if (c == quote && code.getOrNull(i - 1) != '\\') quote = null
                c == '/' && code.getOrNull(i + 1) == '*' -> { inComment = true; i++ }
                c == '"' || c == '\'' -> quote = c
                c == '{' -> depth++
                c == '}' -> if (--depth < 0) return listOf("CSS line $line: '}' without a matching '{'")
            }
            i++
        }
        return when {
            inComment -> listOf("CSS: a /* comment is not closed")
            depth > 0 -> listOf("CSS: $depth '{' not closed")
            else -> emptyList()
        }
    }

    /** Problems in all checkable blocks of an answer, as feedback for the model. */
    fun review(markdown: String, js: JsSandbox?): List<String> {
        val out = mutableListOf<String>()
        val blocks = CodeBlocks.extract(markdown).filter { it.kind.checkable }
        for ((i, b) in blocks.withIndex()) {
            val name = if (blocks.size > 1) "${b.kind.label} block ${i + 1}" else b.kind.label
            check(b.kind, b.code, js).forEach { out += "$name: $it" }
        }
        return out
    }
}
