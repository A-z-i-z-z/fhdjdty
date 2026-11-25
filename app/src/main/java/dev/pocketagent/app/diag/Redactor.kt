package dev.pocketagent.app.diag

/**
 * Removes secrets from text before it is logged, copied or shared.
 *
 * Two layers: (1) literal secrets that were registered at runtime (API server key, the
 * API key of every provider, every sensitive field of the service-account JSON), replaced wherever they occur,
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

        private val ENV_SECRET_LINE = Regex(
            "^([ \\t]*(?:export[ \\t]+)?[A-Za-z0-9_]*(?:KEY|TOKEN|SECRET|PASSWORD|PASSWD)[A-Za-z0-9_]*[ \\t]*=[ \\t]*)([^\\r\\n]*)$",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
        )

        /**
         * Masks the value of every `NAME=value` line of an env file whose NAME contains KEY, TOKEN, SECRET or PASSWORD, whatever the
         * value's length (the literal and generic rules ignore values shorter than 6 characters, e.g. a short LAN token). Comment
         * lines and empty values are left alone. The replacement is chosen so that no later generic rule matches it again.
         */
        fun maskEnvSecrets(text: String): String =
            ENV_SECRET_LINE.replace(text) { m ->
                val v = m.groupValues[2]
                if (v.isEmpty()) m.value else m.groupValues[1] + "(${v.length} chars, hidden)"
            }

        private val RULES: List<Rule> = listOf(
            Rule(Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----")) {
                tag("private-key", it.value.length)
            },
            // A PEM header whose end marker was cut off (truncated log line).
            Rule(Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----[A-Za-z0-9+/=\\\\\\s]{20,}")) {
                tag("private-key-partial", it.value.length)
            },
            Rule(Regex("(\"(?:private_key_id|private_key|client_email|client_id|client_secret|refresh_token)\"\\s*:\\s*\")([^\"]*)(\")")) {
                it.groupValues[1] + tag(it.groupValues[0].substringAfter('"').substringBefore('"'), it.groupValues[2].length) + it.groupValues[3]
            },
            Rule(Regex("[A-Za-z0-9._-]+@[A-Za-z0-9-]+\\.iam\\.gserviceaccount\\.com")) { tag("sa-email", it.value.length) },
            Rule(Regex("ya29\\.[A-Za-z0-9._\\-]{10,}")) { tag("google-access-token", it.value.length) },
            Rule(Regex("eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]*")) { tag("jwt", it.value.length) },
            Rule(Regex("AIza[0-9A-Za-z_\\-]{30,}")) { tag("google-api-key", it.value.length) },
            Rule(Regex("AQ\\.[A-Za-z0-9_\\-]{20,}")) { tag("google-api-key", it.value.length) },
            // Other providers' keys (Phase 3). Anthropic and OpenRouter first, then the OpenAI shapes (sk-..., sk-proj-...).
            Rule(Regex("(?<![A-Za-z0-9])sk-ant-[A-Za-z0-9_\\-]{16,}")) { tag("anthropic-api-key", it.value.length) },
            Rule(Regex("(?<![A-Za-z0-9])sk-or-[A-Za-z0-9_\\-]{16,}")) { tag("openrouter-api-key", it.value.length) },
            Rule(Regex("(?<![A-Za-z0-9])sk-(?:proj-|svcacct-|admin-)?[A-Za-z0-9_\\-]{32,}")) { tag("openai-api-key", it.value.length) },
            Rule(Regex("GOCSPX-[A-Za-z0-9_\\-]{10,}")) { tag("oauth-client-secret", it.value.length) },
            Rule(Regex("1//[A-Za-z0-9_\\-]{20,}")) { tag("oauth-refresh-token", it.value.length) },
            // User info of a URL (https://user:password@host): a safety net for logs, the value is never legitimate output.
            Rule(Regex("(?i)(https?://)[^/\\s@]+@")) { it.groupValues[1] + "[REDACTED userinfo]@" },
            Rule(Regex("(?i)(bearer\\s+)([A-Za-z0-9._~+/=\\-]{6,})")) { it.groupValues[1] + tag("bearer", it.groupValues[2].length) },
            Rule(Regex("(?i)(API_SERVER_KEY\\s*[=:]\\s*)(\\S+)")) { it.groupValues[1] + tag("api-server-key", it.groupValues[2].length) },
            Rule(Regex("(?i)((?:api[_-]?key|secret|token|password|passwd|authorization)[\"']?\\s*[:=]\\s*[\"']?)([^\\s\"',;}]{6,})")) {
                it.groupValues[1] + tag("secret", it.groupValues[2].length)
            },
        )
    }
}
