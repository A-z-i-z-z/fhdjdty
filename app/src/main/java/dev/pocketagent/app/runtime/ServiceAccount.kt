package dev.pocketagent.app.runtime

import org.json.JSONException
import org.json.JSONObject

/** Result of validating a pasted or picked service-account JSON. Never contains the secret values in messages. */
sealed class SaParse {
    class Ok(
        /** Text to store. Equals the input byte-for-byte unless [repaired]. */
        val jsonText: String,
        val repaired: Boolean,
        val projectId: String,
        val clientEmail: String,
        val privateKeyId: String,
        val clientId: String,
        val privateKey: String,
    ) : SaParse() {
        val maskedEmail: String get() = ServiceAccountParser.maskEmail(clientEmail)
    }

    /** [code] is a stable identifier the UI maps to a string resource; [detail] is non-secret (field names, positions). */
    class Fail(val code: Code, val detail: String = "") : SaParse()

    enum class Code { EMPTY, NOT_JSON, NOT_OBJECT, WRONG_TYPE, MISSING_FIELDS, BAD_PRIVATE_KEY }
}

object ServiceAccountParser {
    private val REQUIRED = listOf("type", "private_key", "client_email", "project_id")

    fun parse(raw: String): SaParse {
        val cleaned = raw.removePrefix("﻿").trim()
        if (cleaned.isEmpty()) return SaParse.Fail(SaParse.Code.EMPTY)

        var obj: JSONObject? = null
        var repaired = false
        try {
            obj = parseObject(cleaned)
        } catch (strict: JSONException) {
            // Repair attempt: terminal copies often break long string values over several
            // lines. A raw line break inside a JSON string is never legal, so dropping it
            // restores what was meant; the private key's PEM body is rebuilt below anyway.
            val fixed = stripLineBreaksInsideStrings(cleaned)
            if (fixed != cleaned) {
                try { obj = parseObject(fixed); repaired = true } catch (_: JSONException) { }
            }
            if (obj == null) {
                return SaParse.Fail(
                    if (cleaned.startsWith("{")) SaParse.Code.NOT_JSON else SaParse.Code.NOT_OBJECT,
                    positionOnly(strict),
                )
            }
        }
        val o = obj!!

        val type = o.optString("type", "")
        val missing = REQUIRED.filter { it != "type" && o.optString(it, "").isBlank() } .toMutableList()
        if (type.isBlank()) missing.add(0, "type")
        if (type.isNotBlank() && type != "service_account") {
            return SaParse.Fail(SaParse.Code.WRONG_TYPE, type.take(40).filter { it.isLetterOrDigit() || it in "_-" })
        }
        if (missing.isNotEmpty()) return SaParse.Fail(SaParse.Code.MISSING_FIELDS, missing.joinToString(", "))

        // Whitespace can never belong to these fields; remove what a wrapped paste added.
        for (k in listOf("project_id", "client_email", "private_key_id", "client_id")) {
            val v = o.optString(k, "")
            val squeezed = v.filterNot { it.isWhitespace() }
            if (squeezed != v) { o.put(k, squeezed); repaired = true }
        }

        val key = o.getString("private_key")
        val rebuilt = rebuildPem(key) ?: return SaParse.Fail(SaParse.Code.BAD_PRIVATE_KEY)
        if (rebuilt != key) { o.put("private_key", rebuilt); repaired = true }

        val email = o.getString("client_email")
        if (!email.contains('@')) return SaParse.Fail(SaParse.Code.MISSING_FIELDS, "client_email (not an e-mail address)")

        return SaParse.Ok(
            jsonText = if (repaired) o.toString(2) + "\n" else raw,
            repaired = repaired,
            projectId = o.getString("project_id"),
            clientEmail = email,
            privateKeyId = o.optString("private_key_id", ""),
            clientId = o.optString("client_id", ""),
            privateKey = o.getString("private_key"),
        )
    }

    private fun parseObject(text: String): JSONObject {
        // org.json is lenient (accepts single quotes, unquoted keys, trailing garbage in
        // some versions); insist on a real object that spans the whole text.
        val o = JSONObject(text)
        require(text.startsWith("{") && text.endsWith("}")) { "trailing text" }
        return o
    }

    private fun positionOnly(e: JSONException): String {
        // org.json messages look like "Expected a ',' or '}' at 120 [character 121 line 3]";
        // keep only the position, never any quoted content.
        val m = Regex("at (\\d+)(?: \\[character (\\d+) line (\\d+)\\])?").find(e.message ?: "")
        return m?.let { "near character ${it.groupValues[1]}" } ?: ""
    }

    /** Drops CR/LF (and nothing else) that occur inside double-quoted JSON strings. */
    internal fun stripLineBreaksInsideStrings(s: String): String {
        val sb = StringBuilder(s.length)
        var inStr = false
        var esc = false
        for (c in s) {
            if (inStr) {
                if (esc) { esc = false; sb.append(c); continue }
                when (c) {
                    '\\' -> { esc = true; sb.append(c) }
                    '"' -> { inStr = false; sb.append(c) }
                    '\n', '\r' -> { /* dropped */ }
                    else -> sb.append(c)
                }
            } else {
                if (c == '"') inStr = true
                sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * Normalises a PEM private key: header/footer kept, body re-wrapped at 64 columns
     * regardless of how line breaks were damaged. Returns null when it is not a PEM
     * private key at all.
     */
    internal fun rebuildPem(key: String): String? {
        val m = Regex("-----BEGIN ([A-Z ]*PRIVATE KEY)-----([\\s\\S]*?)-----END \\1-----").find(key) ?: return null
        val body = m.groupValues[2].replace("\\n", "").filterNot { it.isWhitespace() }
        if (body.length < 100 || !body.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }) return null
        return "-----BEGIN ${m.groupValues[1]}-----\n" + body.chunked(64).joinToString("\n") + "\n-----END ${m.groupValues[1]}-----\n"
    }

    fun maskEmail(email: String): String {
        val at = email.indexOf('@')
        if (at <= 0) return "***"
        val local = email.substring(0, at)
        val masked = if (local.length <= 2) "*".repeat(local.length) else local.first() + "***" + local.last()
        return masked + email.substring(at)
    }
}
