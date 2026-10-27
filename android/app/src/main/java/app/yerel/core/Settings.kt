package app.yerel.core

import org.json.JSONArray
import org.json.JSONObject

const val DEFAULT_SYSTEM_PROMPT =
    "Sen Yerel adında, tamamen kullanıcının telefonunda ve çevrimdışı çalışan yardımsever bir yapay zekâ " +
        "asistanısın. Kullanıcı hangi dilde yazarsa o dilde, açık ve öz cevap ver. Emin olmadığın bilgileri " +
        "uydurma; gerekirse araçlarını kullan."

/** Every user-adjustable option. Changes to the "context" group recreate the llama context. */
data class AppSettings(
    // context and memory
    val nCtx: Int = 4096,
    val nBatch: Int = 256,
    val nThreads: Int = 0, // 0 = automatic
    val kvTypeK: String = "q8_0",
    val kvTypeV: String = "q8_0",
    val flashAttn: Int = -1,
    val useMmap: Boolean = true,
    val useMlock: Boolean = false,
    // sampling
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val minP: Float = 0.05f,
    val repeatPenalty: Float = 1.05f,
    val repeatLastN: Int = 64,
    val maxTokens: Int = 1024,
    val seed: Long = -1,
    val useModelPreset: Boolean = true,
    // behaviour
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val thinking: Boolean = true,
    val reasoningBudget: Int = -1,
    val agentEnabled: Boolean = true,
    val disabledTools: Set<String> = emptySet(),
    val maxAgentSteps: Int = 6,
    val memoryEnabled: Boolean = true,
    val searchProvider: String = "duckduckgo", // duckduckgo | searxng
    val searxngUrl: String = "",
    val fetchMaxChars: Int = 6000,
    val hfToken: String = "",
    // appearance
    val themeMode: Int = 0, // 0 system, 1 light, 2 dark
    val accent: Int = 0,
    val dynamicColor: Boolean = false,
    val fontScale: Float = 1.0f,
    val showStats: Boolean = true,
    // lifecycle
    val unloadInBackground: Boolean = false,
    val lastModelId: String? = null,
) {
    fun contextKey() = listOf(nCtx, nBatch, nThreads, kvTypeK, kvTypeV, flashAttn).joinToString("|")
    fun loadKey() = listOf(useMmap, useMlock).joinToString("|")

    fun resolvedThreads(cpuCount: Int): Int =
        if (nThreads > 0) nThreads else (cpuCount - 2).coerceIn(2, 6)

    fun toJson() = JSONObject().apply {
        put("n_ctx", nCtx)
        put("n_batch", nBatch)
        put("n_threads", nThreads)
        put("kv_k", kvTypeK)
        put("kv_v", kvTypeV)
        put("flash_attn", flashAttn)
        put("mmap", useMmap)
        put("mlock", useMlock)
        put("temp", temperature.toDouble())
        put("top_k", topK)
        put("top_p", topP.toDouble())
        put("min_p", minP.toDouble())
        put("repeat_penalty", repeatPenalty.toDouble())
        put("repeat_last_n", repeatLastN)
        put("max_tokens", maxTokens)
        put("seed", seed)
        put("model_preset", useModelPreset)
        put("system_prompt", systemPrompt)
        put("thinking", thinking)
        put("reasoning_budget", reasoningBudget)
        put("agent", agentEnabled)
        put("disabled_tools", JSONArray(disabledTools.toList()))
        put("max_steps", maxAgentSteps)
        put("memory", memoryEnabled)
        put("search", searchProvider)
        put("searxng", searxngUrl)
        put("fetch_max", fetchMaxChars)
        put("hf_token", hfToken)
        put("theme", themeMode)
        put("accent", accent)
        put("dynamic", dynamicColor)
        put("font_scale", fontScale.toDouble())
        put("stats", showStats)
        put("unload_bg", unloadInBackground)
        lastModelId?.let { put("last_model", it) }
    }

    companion object {
        fun fromJson(o: JSONObject): AppSettings {
            val d = AppSettings()
            fun f(key: String, def: Float) = if (o.has(key)) o.getDouble(key).toFloat() else def
            return AppSettings(
                nCtx = o.optInt("n_ctx", d.nCtx),
                nBatch = o.optInt("n_batch", d.nBatch),
                nThreads = o.optInt("n_threads", d.nThreads),
                kvTypeK = o.optString("kv_k", d.kvTypeK),
                kvTypeV = o.optString("kv_v", d.kvTypeV),
                flashAttn = o.optInt("flash_attn", d.flashAttn),
                useMmap = o.optBoolean("mmap", d.useMmap),
                useMlock = o.optBoolean("mlock", d.useMlock),
                temperature = f("temp", d.temperature),
                topK = o.optInt("top_k", d.topK),
                topP = f("top_p", d.topP),
                minP = f("min_p", d.minP),
                repeatPenalty = f("repeat_penalty", d.repeatPenalty),
                repeatLastN = o.optInt("repeat_last_n", d.repeatLastN),
                maxTokens = o.optInt("max_tokens", d.maxTokens),
                seed = o.optLong("seed", d.seed),
                useModelPreset = o.optBoolean("model_preset", d.useModelPreset),
                systemPrompt = o.optString("system_prompt", d.systemPrompt),
                thinking = o.optBoolean("thinking", d.thinking),
                reasoningBudget = o.optInt("reasoning_budget", d.reasoningBudget),
                agentEnabled = o.optBoolean("agent", d.agentEnabled),
                disabledTools = o.optJSONArray("disabled_tools")?.let { a ->
                    (0 until a.length()).map { a.getString(it) }.toSet()
                } ?: d.disabledTools,
                maxAgentSteps = o.optInt("max_steps", d.maxAgentSteps),
                memoryEnabled = o.optBoolean("memory", d.memoryEnabled),
                searchProvider = o.optString("search", d.searchProvider),
                searxngUrl = o.optString("searxng", d.searxngUrl),
                fetchMaxChars = o.optInt("fetch_max", d.fetchMaxChars),
                hfToken = o.optString("hf_token", d.hfToken),
                themeMode = o.optInt("theme", d.themeMode),
                accent = o.optInt("accent", d.accent),
                dynamicColor = o.optBoolean("dynamic", d.dynamicColor),
                fontScale = f("font_scale", d.fontScale),
                showStats = o.optBoolean("stats", d.showStats),
                unloadInBackground = o.optBoolean("unload_bg", d.unloadInBackground),
                lastModelId = o.optStringOrNull("last_model"),
            )
        }
    }
}

/** Generation parameters sent to the native engine. */
fun AppSettings.genParamsJson(preset: SamplingPreset?, thinkingEnabled: Boolean): String {
    val p = if (useModelPreset) preset else null
    return JSONObject().apply {
        put("temp", (p?.temperature ?: temperature).toDouble())
        put("top_k", p?.topK ?: topK)
        put("top_p", (p?.topP ?: topP).toDouble())
        put("min_p", (p?.minP ?: minP).toDouble())
        put("repeat_penalty", (p?.repeatPenalty ?: repeatPenalty).toDouble())
        put("repeat_last_n", repeatLastN)
        put("max_tokens", maxTokens)
        put("seed", seed)
        put("enable_thinking", thinkingEnabled)
        put("reasoning_budget", reasoningBudget)
    }.toString()
}

fun AppSettings.contextParamsJson(cpuCount: Int): String {
    val threads = resolvedThreads(cpuCount)
    return JSONObject().apply {
        put("n_ctx", nCtx)
        put("n_batch", nBatch)
        put("n_ubatch", nBatch)
        put("n_threads", threads)
        put("n_threads_batch", threads)
        put("type_k", kvTypeK)
        put("type_v", kvTypeV)
        put("flash_attn", flashAttn)
    }.toString()
}
