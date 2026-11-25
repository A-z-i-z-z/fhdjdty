package dev.pocketagent.app.runtime

import java.net.URI

/**
 * How Hermes authenticates to its LLM provider. The first three are Google; the other four are the
 * Phase 3 providers. Verified against Hermes v0.23.3 (see notes per mode and [ProviderConfig]).
 */
enum class AuthMode(
    /** True for the three Google modes (they share the region / project fields and the Vertex credential files). */
    val google: Boolean = true,
) {
    /** Hermes `gemini` provider + GEMINI_API_KEY. Fastest path; the default. */
    API_KEY,
    /** Hermes `vertex` provider + VERTEX_CREDENTIALS_PATH (service-account JSON). */
    SERVICE_ACCOUNT,
    /** Hermes `vertex` provider via google.auth.default() + an authorized_user ADC file. */
    USER_CREDENTIALS,
    /** Hermes `openrouter` provider + OPENROUTER_API_KEY. */
    OPENROUTER(google = false),
    /** Hermes `anthropic` provider (native Messages API) + ANTHROPIC_API_KEY. */
    ANTHROPIC(google = false),
    /** Hermes `openai-api` provider (api.openai.com) + OPENAI_API_KEY. */
    OPENAI(google = false),
    /** Hermes `custom` provider: any OpenAI-compatible endpoint (model.base_url) + an optional key. */
    CUSTOM(google = false);

    /** True when this mode is authenticated by a typed API key (Gemini key for [API_KEY], one key per other provider). */
    val usesApiKey: Boolean get() = this == API_KEY || !google
}

/** Only meaningful in [AuthMode.API_KEY]. */
enum class ApiEndpoint { STUDIO, VERTEX_EXPRESS }

data class ProviderInputs(
    val mode: AuthMode,
    val endpoint: ApiEndpoint,
    val model: String,
    val region: String,
    val projectId: String,
    /** The Gemini key; only used in [AuthMode.API_KEY]. */
    val geminiApiKey: String = "",
    /** Hermes `agent.reasoning_effort`; "" = not set (the block is not written and Hermes's own default, "medium", applies). */
    val reasoningEffort: String = "",
    /** The API key of the active non-Google provider (OpenRouter, Anthropic, OpenAI, custom); "" = none. */
    val providerKey: String = "",
    /** Base URL of the [AuthMode.CUSTOM] provider, as typed (validated by [ProviderConfig.normalizeBaseUrl]). */
    val baseUrl: String = "",
)

object ProviderConfig {
    const val EXPRESS_HOST = "https://aiplatform.googleapis.com"
    const val GUEST_SA_PATH = "/root/.hermes/secrets/vertex-sa.json"
    /** google.auth.default() looks here when GOOGLE_APPLICATION_CREDENTIALS is unset (HOME=/root in the guest). */
    const val GUEST_ADC_RELATIVE = "root/.config/gcloud/application_default_credentials.json"

    // Defaults per surface. `gemini` provider ids are native ("gemini-4-hydrogen"); the `vertex`
    // provider needs the "google/" publisher prefix (Hermes docs, guides/google-vertex.md and
    // google-gemini.md "Model IDs").
    const val DEFAULT_MODEL_STUDIO = "gemini-4-hydrogen"
    const val DEFAULT_MODEL_EXPRESS = "gemini-4-hydrogen"
    const val DEFAULT_MODEL_VERTEX = "google/gemini-4-argon"

    // Phase 3 providers. Every id below comes from Hermes's own curated lists
    // (hermes_cli/models_catalog_static.py: OPENROUTER_MODELS, _PROVIDER_MODELS["anthropic"] and ["openai-api"]).
    /** OpenRouter ids are "vendor/model". */
    const val DEFAULT_MODEL_OPENROUTER = "google/gemini-3.8-flash"
    /** Anthropic ids are native and plain. */
    const val DEFAULT_MODEL_ANTHROPIC = "claude-sonnet-5"
    /** OpenAI ids are plain. */
    const val DEFAULT_MODEL_OPENAI = "gpt-5.4-mini"

    /**
     * Name of the environment variable that carries the custom endpoint's key. config.yaml points at it with
     * `model.key_env` (hermes_cli/runtime_provider_backends.py:187-188, runtime_provider_custom.py:48-77), so the
     * key itself never appears in config.yaml.
     */
    const val CUSTOM_KEY_ENV = "AGIRI_CUSTOM_API_KEY"

    /** Stands in for a stored key while the form is validated in memory (the real key is not read during composition). */
    const val STORED_KEY_PLACEHOLDER = "stored"

    /** OpenRouter keys must start with this or Hermes ignores them (hermes_cli/auth.py:319-321, 337-352). */
    const val OPENROUTER_KEY_PREFIX = "sk-or-"

    fun defaultModel(mode: AuthMode, endpoint: ApiEndpoint): String = when (mode) {
        AuthMode.API_KEY -> if (endpoint == ApiEndpoint.STUDIO) DEFAULT_MODEL_STUDIO else DEFAULT_MODEL_EXPRESS
        AuthMode.SERVICE_ACCOUNT, AuthMode.USER_CREDENTIALS -> DEFAULT_MODEL_VERTEX
        AuthMode.OPENROUTER -> DEFAULT_MODEL_OPENROUTER
        AuthMode.ANTHROPIC -> DEFAULT_MODEL_ANTHROPIC
        AuthMode.OPENAI -> DEFAULT_MODEL_OPENAI
        AuthMode.CUSTOM -> ""   // the endpoint decides what it serves: the user types it
    }

    /** Model chips: the Gemini 4 family first (Neon, Carbon, Argon, Hydrogen), then the Flash ids. Other providers: ids from Hermes's own lists. */
    fun suggestions(mode: AuthMode, endpoint: ApiEndpoint): List<String> = when (mode) {
        AuthMode.API_KEY -> listOf(
            "gemini-4-neon", "gemini-4-carbon", "gemini-4-argon", "gemini-4-hydrogen",
            "gemini-3.8-flash", "gemini-3.7-flash")
        AuthMode.SERVICE_ACCOUNT, AuthMode.USER_CREDENTIALS -> listOf(
            "google/gemini-4-neon", "google/gemini-4-carbon", "google/gemini-4-argon", "google/gemini-4-hydrogen",
            "google/gemini-3.8-flash", "google/gemini-3.7-flash", "google/gemini-3.1-pro-preview")
        AuthMode.OPENROUTER -> listOf(
            "anthropic/claude-sonnet-5.5", "anthropic/claude-haiku-5.5", "openai/gpt-6.2-sol", "google/gemini-4-argon",
            "google/gemini-3.8-flash", "deepseek/deepseek-v4-pro", "moonshotai/kimi-k3", "z-ai/glm-5.3")
        AuthMode.ANTHROPIC -> listOf(
            "claude-sonnet-5", "claude-opus-5", "claude-fable-5", "claude-opus-4-8", "claude-sonnet-4-6", "claude-haiku-4-5-20251001")
        AuthMode.OPENAI -> listOf(
            "gpt-6.2-sol", "gpt-6.2-astra", "gpt-5.5", "gpt-5.4", "gpt-5.4-mini", "gpt-4.1", "gpt-4o-mini")
        AuthMode.CUSTOM -> emptyList()
    }

    /** Values Hermes v0.23.3 accepts for `agent.reasoning_effort` (cli-config.yaml.example). "" in the app means "not set". */
    val REASONING_EFFORTS = listOf("none", "minimal", "low", "medium", "high", "xhigh")

    /**
     * The value of `model.provider` in Hermes's config.yaml. Each name is a registry id or plugin profile name:
     * gemini (hermes_cli/auth.py:185-186), vertex (:241), openai-api (:179), anthropic (:210-211),
     * openrouter (plugins/model-providers/openrouter/__init__.py:207-208), custom
     * (plugins/model-providers/custom/__init__.py:95-97).
     */
    fun providerName(i: ProviderInputs): String = when (i.mode) {
        AuthMode.API_KEY -> "gemini"
        AuthMode.SERVICE_ACCOUNT, AuthMode.USER_CREDENTIALS -> "vertex"
        AuthMode.OPENROUTER -> "openrouter"
        AuthMode.ANTHROPIC -> "anthropic"
        AuthMode.OPENAI -> "openai-api"
        AuthMode.CUSTOM -> "custom"
    }

    /**
     * Express mode: the bare host routes to us-central1, where global-only models (Gemini 4 Argon)
     * are unreachable; with a project the project-scoped form is used (Hermes docs google-gemini.md).
     * For the custom provider this is the (normalised) endpoint URL, null while it is invalid or empty.
     * Every other mode uses Hermes's built-in base URL, so null ("provider default").
     */
    fun baseUrl(i: ProviderInputs): String? = when {
        i.mode == AuthMode.API_KEY && i.endpoint == ApiEndpoint.VERTEX_EXPRESS ->
            if (i.projectId.isBlank()) EXPRESS_HOST
            else "$EXPRESS_HOST/v1beta1/projects/${i.projectId.trim()}/locations/${i.region.trim().ifEmpty { "global" }}"
        i.mode == AuthMode.CUSTOM -> normalizeBaseUrl(i.baseUrl)
        else -> null
    }

    enum class Problem {
        NO_API_KEY, NO_PROJECT_FOR_USER_CREDENTIALS, EMPTY_MODEL, INVALID_PROJECT, INVALID_REGION, INVALID_REASONING_EFFORT,
        /** The key is present but cannot work with this provider (OpenRouter keys start with sk-or-). */
        INVALID_API_KEY_FORMAT,
        /** Custom endpoint without an address. */
        NO_BASE_URL,
        /** Custom endpoint address that is not acceptable (see [normalizeBaseUrl]). */
        INVALID_BASE_URL,
        /** Only reported by [savedProblems]: the saved mode needs an imported credentials file that is not there. */
        NO_CREDENTIALS,
    }

    private val PROJECT_RE = Regex("^[a-z0-9_-]+$")
    private val REGION_RE = Regex("^[a-z0-9-]+$")

    fun problems(i: ProviderInputs): List<Problem> = buildList {
        if (i.model.isBlank()) add(Problem.EMPTY_MODEL)
        when (i.mode) {
            AuthMode.API_KEY -> if (i.geminiApiKey.isBlank()) add(Problem.NO_API_KEY)
            AuthMode.OPENROUTER, AuthMode.ANTHROPIC, AuthMode.OPENAI -> {
                val k = i.providerKey.trim()
                if (k.isEmpty()) add(Problem.NO_API_KEY)
                else if (i.mode == AuthMode.OPENROUTER && k != STORED_KEY_PLACEHOLDER && !k.startsWith(OPENROUTER_KEY_PREFIX)) add(Problem.INVALID_API_KEY_FORMAT)
            }
            AuthMode.CUSTOM -> {
                // The key is optional here: local servers (Ollama, LM Studio, vLLM) need none.
                if (i.baseUrl.isBlank()) add(Problem.NO_BASE_URL)
                else if (normalizeBaseUrl(i.baseUrl) == null) add(Problem.INVALID_BASE_URL)
            }
            AuthMode.SERVICE_ACCOUNT, AuthMode.USER_CREDENTIALS -> {}
        }
        if (i.mode.google) {
            // authorized_user credentials carry no project (agent/vertex_adapter.py: google.auth.default()).
            if (i.mode == AuthMode.USER_CREDENTIALS && i.projectId.isBlank()) add(Problem.NO_PROJECT_FOR_USER_CREDENTIALS)
            // Project and region end up in .env / config.yaml / a URL, so only plain identifiers are accepted.
            if (i.projectId.isNotBlank() && !PROJECT_RE.matches(i.projectId.trim())) add(Problem.INVALID_PROJECT)
            val usesRegion = i.mode != AuthMode.API_KEY || i.endpoint == ApiEndpoint.VERTEX_EXPRESS
            if (usesRegion && i.region.isNotBlank() && !REGION_RE.matches(i.region.trim())) add(Problem.INVALID_REGION)
        }
        if (i.reasoningEffort.isNotBlank() && i.reasoningEffort.trim() !in REASONING_EFFORTS) add(Problem.INVALID_REASONING_EFFORT)
    }

    /** The inputs of the SAVED setup (what Hermes will really be started with): reads the settings and the stored keys. Not for composition. */
    fun savedInputs(settings: Settings, secrets: SecretStore): ProviderInputs =
        settings.providerInputs(secrets.geminiApiKey(), secrets.providerKey(settings.authMode))

    /**
     * Everything that makes the SAVED setup unusable for chatting: [problems] of [savedInputs], plus a missing
     * imported credentials file in the two Vertex modes. Empty = complete. Reads files: call off the main thread.
     */
    fun savedProblems(settings: Settings, secrets: SecretStore): List<Problem> = buildList {
        addAll(problems(savedInputs(settings, secrets)))
        when (settings.authMode) {
            AuthMode.SERVICE_ACCOUNT -> if (!secrets.hasServiceAccount) add(Problem.NO_CREDENTIALS)
            AuthMode.USER_CREDENTIALS -> if (!secrets.hasUserCredentials) add(Problem.NO_CREDENTIALS)
            AuthMode.API_KEY, AuthMode.OPENROUTER, AuthMode.ANTHROPIC, AuthMode.OPENAI, AuthMode.CUSTOM -> {}
        }
    }

    /**
     * Problems that make a start pointless or unsafe. Missing credentials are NOT blocking: Hermes boots without them.
     * An invalid custom address IS blocking (it would be written into config.yaml).
     */
    fun blocking(p: List<Problem>) = p.filter {
        it == Problem.EMPTY_MODEL || it == Problem.INVALID_PROJECT || it == Problem.INVALID_REGION ||
            it == Problem.INVALID_REASONING_EFFORT || it == Problem.INVALID_BASE_URL
    }

    // ---- custom endpoint address --------------------------------------------------------------------------

    private const val MAX_URL_LENGTH = 300
    /** What may appear in the address at all: no quotes, spaces, '@' (credentials), '?', '#', '=', '&', backslashes or control characters. */
    private val URL_CHARS_RE = Regex("^[A-Za-z0-9._~:/%+\\[\\]-]+$")
    private val HOST_RE = Regex("^(\\[[0-9a-f:.]+]|[a-z0-9]([a-z0-9.-]{0,251}[a-z0-9])?)$")
    private val IPV4_RE = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")

    /**
     * Validates and normalises the base URL of the custom provider; null = not acceptable. It ends up in config.yaml,
     * so it is strict: https, or plain http only for this phone and private networks (localhost, 127.x, 10.x, 172.16-31.x,
     * 192.168.x, 169.254.x, 100.64-127.x, IPv6 loopback / unique-local / link-local, *.local, *.localhost); a host name or
     * address, an optional port 1..65535 and a path; no user name or password, no query, no fragment, no "..". The
     * result has the scheme and host in lower case and no trailing slash.
     */
    fun normalizeBaseUrl(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty() || s.length > MAX_URL_LENGTH || !URL_CHARS_RE.matches(s)) return null
        val uri = try { URI(s) } catch (_: Exception) { return null }
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
        val host = uri.host?.lowercase() ?: return null
        if (!HOST_RE.matches(host) || host.contains("..")) return null
        val port = uri.port
        if (port != -1 && port !in 1..65535) return null
        if (scheme == "http" && !isPrivateHost(host)) return null
        val path = (uri.rawPath ?: "").trimEnd('/')
        if (path.split('/').any { it == ".." || it == "." }) return null
        return scheme + "://" + host + (if (port != -1) ":$port" else "") + path
    }

    /** The custom base URL as it may appear in a report: its validated form; a rejected value (it may hold credentials) is never shown. */
    fun baseUrlForReport(raw: String): String =
        normalizeBaseUrl(raw) ?: if (raw.isBlank()) "(none)" else "(invalid, not shown, ${raw.trim().length} chars)"

    /** True for hosts that are on this phone or on a private network, where plain http is acceptable. [host] is lower case, IPv6 in brackets. */
    internal fun isPrivateHost(host: String): Boolean {
        val h = host.removeSurrounding("[", "]")
        if (h == "localhost" || h.endsWith(".localhost") || h.endsWith(".local")) return true
        if (host.startsWith("[")) {
            return h == "::1" || h.startsWith("fe80:") || ((h.startsWith("fc") || h.startsWith("fd")) && h.contains(':'))
        }
        val m = IPV4_RE.matchEntire(h) ?: return false
        val octets = m.groupValues.drop(1)
        // "010.0.0.1" is ambiguous (octal in some resolvers): not treated as an address at all.
        if (octets.any { it.length > 1 && it.startsWith("0") }) return false
        val n = octets.map { it.toInt() }
        if (n.any { it > 255 }) return false
        val (a, b) = n
        return a == 127 || a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) ||
            (a == 169 && b == 254) || (a == 100 && b in 64..127)
    }

    // ---- rendering ----------------------------------------------------------------------------------------

    fun yamlEscape(v: String): String = v.filter { it >= ' ' }.replace("\\", "\\\\").replace("\"", "\\\"")
    private fun envValue(v: String): String = v.filter { it >= ' ' }.trim()

    private fun StringBuilder.keyLine(name: String, key: String) {
        if (key.isBlank()) appendLine("# $name is not set yet (enter it in the app)")
        else appendLine("$name=${envValue(key)}")
    }

    /** Complete .env content. Everything is regenerated from scratch, so no variable of another mode can linger. */
    fun renderEnv(apiServerKey: String, i: ProviderInputs, apiPort: Int = Paths.DEFAULT_API_PORT): String = buildString {
        appendLine("# Generated by PocketAgent every time the runtime starts. Do not edit here.")
        appendLine("# The API server gives full terminal access to whoever holds the key: loopback only.")
        appendLine("API_SERVER_ENABLED=true")
        appendLine("API_SERVER_HOST=127.0.0.1")
        appendLine("API_SERVER_PORT=$apiPort")
        appendLine("API_SERVER_KEY=${envValue(apiServerKey)}")
        when (i.mode) {
            AuthMode.API_KEY -> {
                if (i.geminiApiKey.isBlank()) appendLine("# GEMINI_API_KEY is not set yet (enter it in the app)")
                else appendLine("GEMINI_API_KEY=${envValue(i.geminiApiKey)}")
                baseUrl(i)?.let { appendLine("GEMINI_BASE_URL=$it") }
            }
            AuthMode.SERVICE_ACCOUNT -> appendLine("VERTEX_CREDENTIALS_PATH=$GUEST_SA_PATH")
            AuthMode.USER_CREDENTIALS ->
                appendLine("# ADC authorized_user file at ~/.config/gcloud/application_default_credentials.json (VERTEX_CREDENTIALS_PATH and GOOGLE_APPLICATION_CREDENTIALS intentionally unset)")
            // Registry api_key_env_vars: openrouter (plugins/model-providers/openrouter/__init__.py:208 `env_vars`),
            // anthropic (hermes_cli/auth.py:210-211), openai-api (:179). The base URLs stay at Hermes's defaults.
            AuthMode.OPENROUTER -> keyLine("OPENROUTER_API_KEY", i.providerKey)
            AuthMode.ANTHROPIC -> keyLine("ANTHROPIC_API_KEY", i.providerKey)
            AuthMode.OPENAI -> keyLine("OPENAI_API_KEY", i.providerKey)
            AuthMode.CUSTOM ->
                if (i.providerKey.isBlank()) appendLine("# $CUSTOM_KEY_ENV is not set (fine for servers that need no key)")
                else appendLine("$CUSTOM_KEY_ENV=${envValue(i.providerKey)}")
        }
    }

    fun renderYaml(i: ProviderInputs): String = buildString {
        appendLine("# Generated by PocketAgent every time the runtime starts. Edit the fields in the app instead.")
        appendLine("model:")
        appendLine("  default: \"${yamlEscape(i.model.trim())}\"")
        appendLine("  provider: ${providerName(i)}")
        if (i.mode == AuthMode.CUSTOM) {
            // Bare `provider: custom` + model.base_url + model.key_env (hermes_cli/runtime_provider_backends.py:136-188).
            // Only an address that passed normalizeBaseUrl is ever written.
            normalizeBaseUrl(i.baseUrl)?.let { appendLine("  base_url: \"${yamlEscape(it)}\"") }
            if (i.providerKey.isNotBlank()) appendLine("  key_env: \"$CUSTOM_KEY_ENV\"")
        }
        if (i.mode == AuthMode.SERVICE_ACCOUNT || i.mode == AuthMode.USER_CREDENTIALS) {
            appendLine("vertex:")
            if (i.projectId.isNotBlank()) appendLine("  project_id: \"${yamlEscape(i.projectId.trim())}\"")
            appendLine("  region: \"${yamlEscape(i.region.trim().ifEmpty { Settings.DEFAULT_REGION })}\"")
        }
        // Only a level from the fixed list is ever written; "not set" leaves the block out (Hermes default applies).
        val effort = i.reasoningEffort.trim()
        if (effort in REASONING_EFFORTS) {
            appendLine("agent:")
            appendLine("  reasoning_effort: \"$effort\"")
        }
    }

    /**
     * Hosts the configured mode must be able to reach (DNS + TLS), for the self-test probes. The names end up in a shell
     * script, so only plain host names are returned (never an IP literal, never a plain-http endpoint).
     */
    fun probeHosts(mode: AuthMode, endpoint: ApiEndpoint, region: String, customBaseUrl: String): List<String> {
        val r = region.trim().takeIf { REGION_RE.matches(it) } ?: "global"
        val aiplatform = if (r == "global") "aiplatform.googleapis.com" else "$r-aiplatform.googleapis.com"
        return when (mode) {
            AuthMode.API_KEY -> if (endpoint == ApiEndpoint.STUDIO) listOf("generativelanguage.googleapis.com") else listOf("aiplatform.googleapis.com")
            AuthMode.SERVICE_ACCOUNT, AuthMode.USER_CREDENTIALS -> listOf(aiplatform, "oauth2.googleapis.com")
            AuthMode.OPENROUTER -> listOf("openrouter.ai")
            AuthMode.ANTHROPIC -> listOf("api.anthropic.com")
            AuthMode.OPENAI -> listOf("api.openai.com")
            AuthMode.CUSTOM -> {
                val url = normalizeBaseUrl(customBaseUrl)
                val host = if (url != null && url.startsWith("https://")) URI(url).host?.lowercase() else null
                if (host != null && Regex("^[a-z0-9.-]+$").matches(host) && IPV4_RE.matchEntire(host) == null) listOf(host) else emptyList()
            }
        }
    }

    /** One line for diagnostics: what is configured, without any secret. */
    fun describe(i: ProviderInputs): String {
        val key = if (i.mode.google) i.geminiApiKey else i.providerKey
        return "mode=${i.mode} provider=${providerName(i)} model=${i.model} base_url=${baseUrl(i) ?: "(provider default)"} " +
            "region=${i.region} project=${i.projectId.ifBlank { "(none)" }} reasoning_effort=${i.reasoningEffort.ifBlank { "(default)" }} api_key=${if (key.isBlank()) "absent" else "present(len=${key.length})"}"
    }
}
