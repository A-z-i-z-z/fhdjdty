package app.yerel.core

import org.json.JSONArray
import org.json.JSONObject

const val DEFAULT_SYSTEM_PROMPT =
    "You are Yerel, a helpful AI assistant that runs entirely offline on the user's phone. " +
        "Always reply in the language the user writes in, clearly and concisely. " +
        "Do not make up facts you are not sure about; use your tools when they help."

/** The default of earlier versions; replaced by [DEFAULT_SYSTEM_PROMPT] when it was never edited. */
private const val OLD_DEFAULT_SYSTEM_PROMPT =
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
    // compute device and speculative decoding (both reload the model)
    val device: String = "cpu", // cpu | gpu | npu
    val gpuLayers: Int = 999,
    val specMode: String = "auto", // off | auto | ngram | draft | mtp
    val draftModelId: String? = null,
    val nDraft: Int = 3,
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
    // coding
    val codeMode: Boolean = false,
    val codeAutoCheck: Boolean = true,
    val previewNetwork: Boolean = false,
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
    fun loadKey(draftPath: String?) =
        listOf(useMmap, useMlock, device, gpuLayers, specMode, draftPath ?: "", nDraft).joinToString("|")

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
        put("device", device)
        put("gpu_layers", gpuLayers)
        put("spec", specMode)
        draftModelId?.let { put("draft_model", it) }
        put("n_draft", nDraft)
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
        put("code_mode", codeMode)
        put("code_check", codeAutoCheck)
        put("preview_net", previewNetwork)
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
                device = o.optString("device", d.device),
                gpuLayers = o.optInt("gpu_layers", d.gpuLayers),
                specMode = o.optString("spec", d.specMode),
                draftModelId = o.optStringOrNull("draft_model"),
                nDraft = o.optInt("n_draft", d.nDraft),
                temperature = f("temp", d.temperature),
                topK = o.optInt("top_k", d.topK),
                topP = f("top_p", d.topP),
                minP = f("min_p", d.minP),
                repeatPenalty = f("repeat_penalty", d.repeatPenalty),
                repeatLastN = o.optInt("repeat_last_n", d.repeatLastN),
                maxTokens = o.optInt("max_tokens", d.maxTokens),
                seed = o.optLong("seed", d.seed),
                useModelPreset = o.optBoolean("model_preset", d.useModelPreset),
                systemPrompt = o.optString("system_prompt", d.systemPrompt)
                    .let { if (it == OLD_DEFAULT_SYSTEM_PROMPT) DEFAULT_SYSTEM_PROMPT else it },
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
                codeMode = o.optBoolean("code_mode", d.codeMode),
                codeAutoCheck = o.optBoolean("code_check", d.codeAutoCheck),
                previewNetwork = o.optBoolean("preview_net", d.previewNetwork),
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

/**
 * Generation parameters sent to the native engine. Code mode samples close to greedy, which keeps
 * small models from drifting in syntax, and allows longer answers for whole files.
 */
fun AppSettings.genParamsJson(preset: SamplingPreset?, thinkingEnabled: Boolean, code: Boolean = false): String {
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
        if (code) {
            put("temp", minOf(0.2f, p?.temperature ?: temperature).toDouble())
            put("top_k", 20)
            put("top_p", 0.9)
            put("repeat_penalty", 1.0)
            put("max_tokens", maxOf(maxTokens, 4096))
        }
    }.toString()
}

fun AppSettings.loadParamsJson(draftPath: String?): String = JSONObject().apply {
    put("device", device)
    put("n_gpu_layers", gpuLayers)
    put("use_mmap", useMmap)
    put("use_mlock", useMlock)
    put("spec_mode", specMode)
    draftPath?.let { put("draft_path", it) }
    put("n_draft", nDraft)
}.toString()

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
