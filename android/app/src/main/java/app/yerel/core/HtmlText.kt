package app.yerel.core

import java.net.URLDecoder

data class SearchResult(val title: String, val url: String, val snippet: String)

/** Turns web pages into plain text a small model can read, and parses search result pages. */
object HtmlText {
    private val DROP_BLOCKS = Regex(
        "(?is)<(script|style|noscript|svg|template|iframe|head|nav|footer|form)\\b[^>]*>.*?</\\1\\s*>"
    )
    private val COMMENTS = Regex("(?s)<!--.*?-->")
    private val BREAKS = Regex("(?i)<\\s*(br|/p|/div|/h[1-6]|/tr|/section|/article|/blockquote|/pre|/table|hr)\\b[^>]*>")
    private val LIST_ITEM = Regex("(?i)<\\s*li\\b[^>]*>")
    private val HEADING = Regex("(?i)<\\s*h[1-6]\\b[^>]*>")
    private val TAGS = Regex("(?s)<[^>]+>")
    private val TITLE = Regex("(?is)<title[^>]*>(.*?)</title>")
    private val ENTITY = Regex("&(#x[0-9a-fA-F]+|#\\d+|[a-zA-Z]+);")
    private val SPACES = Regex("[ \\t\\u00A0]+")
    private val BLANK_LINES = Regex("\\n{3,}")

    private val NAMED = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "ndash" to "–", "mdash" to "—", "hellip" to "…", "laquo" to "«", "raquo" to "»", "copy" to "©",
        "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“", "middot" to "·", "bull" to "•",
        "ccedil" to "ç", "Ccedil" to "Ç", "ouml" to "ö", "Ouml" to "Ö", "uuml" to "ü", "Uuml" to "Ü",
    )

    fun decodeEntities(s: String): String = ENTITY.replace(s) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith("#x") || e.startsWith("#X") -> e.substring(2).toIntOrNull(16)?.let { cp(it) } ?: m.value
            e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { cp(it) } ?: m.value
            else -> NAMED[e] ?: m.value
        }
    }

    private fun cp(code: Int) = if (Character.isValidCodePoint(code)) String(Character.toChars(code)) else ""

    fun title(html: String): String =
        TITLE.find(html)?.groupValues?.get(1)?.let { decodeEntities(it).trim().replace(Regex("\\s+"), " ") } ?: ""

    fun toText(html: String, maxChars: Int = Int.MAX_VALUE): String {
        var s = COMMENTS.replace(html, " ")
        s = DROP_BLOCKS.replace(s, " ")
        s = HEADING.replace(s, "\n## ")
        s = LIST_ITEM.replace(s, "\n- ")
        s = BREAKS.replace(s, "\n")
        s = TAGS.replace(s, " ")
        s = decodeEntities(s)
        s = s.lines().joinToString("\n") { SPACES.replace(it, " ").trim() }
        s = BLANK_LINES.replace(s, "\n\n").trim()
        return if (s.length > maxChars) s.take(maxChars).trimEnd() + "\n[… kısaltıldı]" else s
    }

    private fun clean(fragment: String) = decodeEntities(TAGS.replace(fragment, "")).replace(Regex("\\s+"), " ").trim()

    private val DDG_LINK = Regex("(?is)<a[^>]+class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>")
    private val DDG_LINK_ALT = Regex("(?is)<a[^>]+href=\"([^\"]+)\"[^>]*class=\"result__a\"[^>]*>(.*?)</a>")
    private val DDG_SNIPPET = Regex("(?is)class=\"result__snippet\"[^>]*>(.*?)</(?:a|div|td)>")
    private val LITE_LINK = Regex("(?is)<a[^>]+href=\"([^\"]+)\"[^>]*class=['\"]result-link['\"][^>]*>(.*?)</a>")
    private val LITE_LINK_ALT = Regex("(?is)<a[^>]+class=['\"]result-link['\"][^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>")
    private val LITE_SNIPPET = Regex("(?is)<td[^>]+class=['\"]result-snippet['\"][^>]*>(.*?)</td>")

    /** Real target of a DuckDuckGo redirect link (//duckduckgo.com/l/?uddg=...). */
    fun unwrapDdgUrl(href: String): String {
        val h = decodeEntities(href)
        val q = h.substringAfter("uddg=", "")
        if (q.isNotEmpty()) {
            return try { URLDecoder.decode(q.substringBefore('&'), "UTF-8") } catch (e: Exception) { h }
        }
        return if (h.startsWith("//")) "https:$h" else h
    }

    fun parseDuckDuckGo(html: String, max: Int = 8): List<SearchResult> {
        val links = DDG_LINK.findAll(html).toList().ifEmpty { DDG_LINK_ALT.findAll(html).toList() }
        val snippets = DDG_SNIPPET.findAll(html).map { clean(it.groupValues[1]) }.toList()
        val lite = links.isEmpty()
        val items = if (!lite) links else LITE_LINK.findAll(html).toList().ifEmpty { LITE_LINK_ALT.findAll(html).toList() }
        val snips = if (!lite) snippets else LITE_SNIPPET.findAll(html).map { clean(it.groupValues[1]) }.toList()
        return items.mapIndexedNotNull { idx, m ->
            val url = unwrapDdgUrl(m.groupValues[1])
            if (url.contains("duckduckgo.com/y.js") || url.contains("ad_provider")) return@mapIndexedNotNull null
            SearchResult(clean(m.groupValues[2]), url, snips.getOrElse(idx) { "" })
        }.distinctBy { it.url }.take(max)
    }
}
