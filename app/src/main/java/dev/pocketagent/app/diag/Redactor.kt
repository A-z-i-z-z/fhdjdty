package dev.pocketagent.app.diag

/**
 * Removes secrets from text before it is logged, copied or shared.
 *
 * Two layers: (1) literal secrets that were registered at runtime (API server key,
 * every sensitive field of the service-account JSON), replaced wherever they occur,
 * and (2) generic patterns (PEM blocks, bearer tokens, Google access tokens, JSON
 * fields named like secrets) as a safety net for things we did not register.
 * Replacements keep the *shape* ("[REDACTED label len=N]") so a dump still shows
 * that a value was present and roughly how long it was.
 */
class Redactor {
    private val literals = LinkedHashMap<String, String>()

    @Synchronized
    fun addSecret(label: String, value: String?) {
        if (value == null) return
        val v = value.trim()
        if (v.length < 6) return
        literals[v] = label
        // The same secret as it would appear JSON-escaped (newlines as \n).
        val escaped = v.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\"", "\\\"")
        if (escaped != v) literals[escaped] = label
    }

    @Synchronized
    fun clear() = literals.clear()

    @Synchronized
    fun redact(input: String): String {
        var s = input
        // Longest first so that a secret containing another is replaced as a whole.
        for ((secret, label) in literals.entries.sortedByDescending { it.key.length }) {
            if (s.contains(secret)) s = s.replace(secret, tag(label, secret.length))
        }
        for (rule in RULES) s = rule.apply(s)
        return s
    }

    private class Rule(val regex: Regex, val replace: (MatchResult) -> String) {
        fun apply(s: String): String = regex.replace(s, replace)
    }

    companion object {
        fun tag(label: String, len: Int) = "[REDACTED $label len=$len]"

        private val RULES: List<Rule> = listOf(
            Rule(Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----")) {
                tag("private-key", it.value.length)
            },
            // A PEM header whose end marker was cut off (truncated log line).
            Rule(Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----[A-Za-z0-9+/=\\\\\\s]{20,}")) {
                tag("private-key-partial", it.value.length)
            },
            Rule(Regex("(\"(?:private_key_id|private_key|client_email|client_id)\"\\s*:\\s*\")([^\"]*)(\")")) {
                it.groupValues[1] + tag(it.groupValues[0].substringAfter('"').substringBefore('"'), it.groupValues[2].length) + it.groupValues[3]
            },
            Rule(Regex("[A-Za-z0-9._-]+@[A-Za-z0-9-]+\\.iam\\.gserviceaccount\\.com")) { tag("sa-email", it.value.length) },
            Rule(Regex("ya29\\.[A-Za-z0-9._\\-]{10,}")) { tag("google-access-token", it.value.length) },
            Rule(Regex("eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]*")) { tag("jwt", it.value.length) },
            Rule(Regex("AIza[0-9A-Za-z_\\-]{30,}")) { tag("google-api-key", it.value.length) },
            Rule(Regex("(?i)(bearer\\s+)([A-Za-z0-9._~+/=\\-]{6,})")) { it.groupValues[1] + tag("bearer", it.groupValues[2].length) },
            Rule(Regex("(?i)(API_SERVER_KEY\\s*[=:]\\s*)(\\S+)")) { it.groupValues[1] + tag("api-server-key", it.groupValues[2].length) },
            Rule(Regex("(?i)((?:api[_-]?key|secret|token|password|passwd|authorization)[\"']?\\s*[:=]\\s*[\"']?)([^\\s\"',;}]{6,})")) {
                it.groupValues[1] + tag("secret", it.groupValues[2].length)
            },
        )
    }
}
