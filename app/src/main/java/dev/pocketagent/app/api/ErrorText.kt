package dev.pocketagent.app.api

import org.json.JSONArray
import org.json.JSONObject

/** What could be read out of an error answer: a short human message when there is one, and the machine words next to it. */
data class ParsedError(val message: String?, val type: String? = null, val code: String? = null) {
    /** The best short text for a person: the message, else the code, else the type, else null. */
    val best: String? get() = message ?: code ?: type
}

/**
 * Turns the many shapes of an error answer into one short readable line. Pure (no Android classes), so it is unit-tested.
 *
 * Shapes handled: `{"error":{"message":..,"type":..,"code":..}}`, `{"error":"text"}`, `{"detail":"text"}` (also a list of
 * `{"msg":..}` items or an object with a message), `{"message":"text"}`, plain text, and an HTML page (its title, if short).
 * A message that itself carries a nested JSON or Python-dict error ("Error code: 400 - {'error': {'message': '...'}}") is
 * reduced to the inner message. The result never contains line breaks and is at most [MAX_CHARS] characters. The raw body is
 * NOT kept here: callers log it (redacted) and show only this line.
 */
object ApiErrorText {
    const val MAX_CHARS = 240

    fun parse(body: String?): ParsedError {
        val text = body?.trim().orEmpty()
        if (text.isEmpty()) return ParsedError(null)
        if (text.startsWith("{")) {
            val obj = try { JSONObject(text) } catch (_: Exception) { null }
            if (obj != null) return fromObject(obj)
        } else if (text.startsWith("[")) {
            val arr = try { JSONArray(text) } catch (_: Exception) { null }
            if (arr != null) return ParsedError(clean(fromArray(arr)))
        }
        if (looksLikeHtml(text)) return ParsedError(clean(htmlTitle(text)))
        return ParsedError(clean(firstLine(text)))
    }

    private fun fromObject(o: JSONObject): ParsedError {
        val err = if (o.isNull("error")) null else o.opt("error")
        if (err is JSONObject) {
            return ParsedError(
                message = clean(textOf(err.opt("message")) ?: textOf(err.opt("detail"))),
                type = textOf(err.opt("type"))?.take(80),
                code = textOf(err.opt("code"))?.take(80),
            )
        }
        if (err is String) return ParsedError(clean(err))
        val detail = if (o.isNull("detail")) null else o.opt("detail")
        if (detail != null) {
            val d = when (detail) {
                is JSONArray -> fromArray(detail)
                is JSONObject -> textOf(detail.opt("message")) ?: textOf(detail.opt("msg"))
                else -> textOf(detail)
            }
            val m = clean(d)
            if (m != null) return ParsedError(m)
        }
        val message = clean(textOf(o.opt("message")))
        return ParsedError(message, type = textOf(o.opt("type"))?.take(80), code = textOf(o.opt("code"))?.take(80))
    }

    private fun fromArray(a: JSONArray): String? {
        for (i in 0 until a.length()) {
            val item = a.opt(i)
            val t = when (item) {
                is JSONObject -> textOf(item.opt("msg")) ?: textOf(item.opt("message"))
                else -> textOf(item)
            }
            if (!t.isNullOrBlank()) return t
        }
        return null
    }

    /** A JSON value as text; null for JSON null, a missing value and blank text. */
    private fun textOf(v: Any?): String? = when (v) {
        null, JSONObject.NULL -> null
        is String -> v.takeIf { it.isNotBlank() }
        is JSONObject, is JSONArray -> null
        else -> v.toString()
    }

    private fun looksLikeHtml(t: String): Boolean {
        val head = t.take(300).lowercase()
        return head.startsWith("<!doctype") || head.contains("<html") || head.startsWith("<head") || head.startsWith("<body")
    }

    private fun htmlTitle(t: String): String? {
        val m = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(t) ?: return null
        val title = m.groupValues[1].replace(Regex("\\s+"), " ").trim()
        return title.takeIf { it.isNotEmpty() && it.length <= 80 }
    }

    private fun firstLine(t: String): String? = t.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }

    /** An inner `'message': '...'` (JSON or Python repr) of a text that wraps another error, or null. */
    private fun innerMessage(t: String): String? {
        if (!t.contains('{')) return null
        val m = Regex("([\"'])message\\1\\s*:\\s*([\"'])(.*?)\\2\\s*(?=[,}])").find(t) ?: return null
        return m.groupValues[3].takeIf { it.isNotBlank() }
    }

    /** Collapses whitespace, unwraps a nested message, cuts to [MAX_CHARS]; null for nothing readable left. */
    private fun clean(raw: String?): String? {
        if (raw == null) return null
        var s = innerMessage(raw) ?: raw
        s = s.replace(Regex("[\\p{Cc}\\s]+"), " ").trim()
        if (s.isEmpty()) return null
        if (s.length > MAX_CHARS) {
            var cut = s.take(MAX_CHARS - 1)
            if (cut.isNotEmpty() && cut.last().isHighSurrogate()) cut = cut.dropLast(1)
            s = cut.trimEnd() + "…"
        }
        return s
    }
}
