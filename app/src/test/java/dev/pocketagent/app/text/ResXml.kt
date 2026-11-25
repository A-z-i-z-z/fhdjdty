package dev.pocketagent.app.text

import org.w3c.dom.Comment
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** One entry of a strings file: a string (one form named "_") or a plurals (forms by quantity). */
class ResEntry(
    val file: String,
    val name: String,
    val isPlurals: Boolean,
    val translatable: Boolean,
    /** The translator note directly above the entry, or null. */
    val note: String?,
    /** Texts as Android would deliver them (escapes resolved, whitespace collapsed). */
    val forms: Map<String, String>,
    /** The raw text of every form, as written between the tags. */
    val raw: Map<String, String>,
)

/**
 * Reads the string resources from the source tree the way aapt2 does closely enough for tests. Tests that use
 * it start with Assume.assumeTrue(ResXml.resDir() != null), because the Windows test run works on a copy.
 */
object ResXml {
    fun resDir(): File? =
        listOf(File("src/main/res"), File("app/src/main/res"), File("../app/src/main/res")).firstOrNull { File(it, "values").isDirectory }

    fun mainDir(): File? = resDir()?.parentFile

    /** The folders values (English), values-tr, values-fr by language tag; "en" is the plain values folder. */
    fun languageDirs(): Map<String, File> {
        val res = resDir() ?: return emptyMap()
        val out = LinkedHashMap<String, File>()
        out["en"] = File(res, "values")
        for (lang in listOf("tr", "fr")) {
            val d = File(res, "values-$lang")
            if (d.isDirectory) out[lang] = d
        }
        return out
    }

    fun stringsFiles(dir: File): List<File> =
        (dir.listFiles { f -> f.isFile && f.name.startsWith("strings") && f.name.endsWith(".xml") } ?: emptyArray()).sortedBy { it.name }

    fun parse(file: File): List<ResEntry> {
        val factory = DocumentBuilderFactory.newInstance()
        val doc = factory.newDocumentBuilder().parse(file)
        val out = ArrayList<ResEntry>()
        var child: Node? = doc.documentElement.firstChild
        while (child != null) {
            if (child is Element && (child.tagName == "string" || child.tagName == "plurals")) {
                val name = child.getAttribute("name")
                val translatable = child.getAttribute("translatable") != "false"
                val note = noteAbove(child)
                val forms = LinkedHashMap<String, String>()
                val raw = LinkedHashMap<String, String>()
                if (child.tagName == "string") {
                    raw["_"] = child.textContent
                    forms["_"] = unescape(child.textContent)
                } else {
                    var item: Node? = child.firstChild
                    while (item != null) {
                        if (item is Element && item.tagName == "item") {
                            raw[item.getAttribute("quantity")] = item.textContent
                            forms[item.getAttribute("quantity")] = unescape(item.textContent)
                        }
                        item = item.nextSibling
                    }
                }
                out += ResEntry(file.name, name, child.tagName == "plurals", translatable, note, forms, raw)
            }
            child = child.nextSibling
        }
        return out
    }

    /** The "t:" note comment that sits directly above [element] with no blank line between. */
    private fun noteAbove(element: Element): String? {
        var prev: Node? = element.previousSibling
        while (prev != null) {
            if (prev is Comment) {
                val m = Regex("^\\s*t:\\s*(.+?)\\s*$", RegexOption.DOT_MATCHES_ALL).find(prev.data)
                return m?.groupValues?.get(1)
            }
            if (prev.nodeType == Node.TEXT_NODE && prev.textContent.isBlank() && prev.textContent.count { it == '\n' } < 2) {
                prev = prev.previousSibling
                continue
            }
            return null
        }
        return null
    }

    /** aapt2's reading of a string value: quotes keep spaces, backslash escapes, other whitespace collapses and is trimmed. */
    fun unescape(text: String): String {
        val sb = StringBuilder()
        var inQuote = false
        var i = 0
        var pendingSpace = false
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' && i + 1 < text.length -> {
                    if (pendingSpace && sb.isNotEmpty()) sb.append(' ')
                    pendingSpace = false
                    val n = text[i + 1]
                    when (n) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'u' -> {
                            val hex = text.substring(i + 2, minOf(i + 6, text.length))
                            val code = hex.toIntOrNull(16)
                            if (code != null && hex.length == 4) {
                                sb.append(code.toChar())
                                i += 4
                            } else {
                                sb.append('u')
                            }
                        }
                        else -> sb.append(n)
                    }
                    i += 2
                    continue
                }
                c == '"' -> {
                    inQuote = !inQuote
                }
                c.isWhitespace() && !inQuote -> {
                    pendingSpace = true
                }
                else -> {
                    if (pendingSpace && sb.isNotEmpty()) sb.append(' ')
                    pendingSpace = false
                    sb.append(c)
                }
            }
            i++
        }
        return sb.toString()
    }

    /** Sorted placeholder tokens of a text: "%1$d" or "%d"; "%%" is not one. */
    fun placeholders(text: String): List<String> =
        Regex("%(?:(\\d+)\\$)?[-#+0,(]*\\d*(?:\\.\\d+)?([a-zA-Z])").findAll(text.replace("%%", "")).map {
            val idx = it.groupValues[1]
            if (idx.isEmpty()) "%" + it.groupValues[2] else "%" + idx + "$" + it.groupValues[2]
        }.toList().sorted()
}
