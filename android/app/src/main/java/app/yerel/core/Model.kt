package app.yerel.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key) else null

inline fun <T> JSONArray.mapObjects(fn: (JSONObject) -> T): List<T> =
    (0 until length()).mapNotNull { i -> optJSONObject(i)?.let(fn) }

/** Sampling values a model card recommends, applied over the global settings when enabled. */
data class SamplingPreset(
    val temperature: Float? = null,
    val topK: Int? = null,
    val topP: Float? = null,
    val minP: Float? = null,
    val repeatPenalty: Float? = null,
) {
    fun toJson() = JSONObject().apply {
        temperature?.let { put("temperature", it.toDouble()) }
        topK?.let { put("top_k", it) }
        topP?.let { put("top_p", it.toDouble()) }
        minP?.let { put("min_p", it.toDouble()) }
        repeatPenalty?.let { put("repeat_penalty", it.toDouble()) }
    }

    companion object {
        fun fromJson(o: JSONObject?): SamplingPreset? = o?.let {
            SamplingPreset(
                temperature = if (it.has("temperature")) it.getDouble("temperature").toFloat() else null,
                topK = if (it.has("top_k")) it.getInt("top_k") else null,
                topP = if (it.has("top_p")) it.getDouble("top_p").toFloat() else null,
                minP = if (it.has("min_p")) it.getDouble("min_p").toFloat() else null,
                repeatPenalty = if (it.has("repeat_penalty")) it.getDouble("repeat_penalty").toFloat() else null,
            )
        }
    }
}

/** A GGUF file the app can load: copied/downloaded into app storage, or linked through a content URI. */
data class ModelEntry(
    val id: String = newId(),
    val name: String,
    val path: String? = null,
    val uri: String? = null,
    val sizeBytes: Long,
    val source: String,
    val info: GgufInfo? = null,
    val preset: SamplingPreset? = null,
    val addedAt: Long = System.currentTimeMillis(),
) {
    val isLinked get() = uri != null

    fun toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        path?.let { put("path", it) }
        uri?.let { put("uri", it) }
        put("size", sizeBytes)
        put("source", source)
        info?.let { put("info", it.toJson()) }
        preset?.let { put("preset", it.toJson()) }
        put("added", addedAt)
    }

    companion object {
        fun fromJson(o: JSONObject) = ModelEntry(
            id = o.getString("id"),
            name = o.getString("name"),
            path = o.optStringOrNull("path"),
            uri = o.optStringOrNull("uri"),
            sizeBytes = o.optLong("size"),
            source = o.optString("source"),
            info = o.optJSONObject("info")?.let(GgufInfo::fromJson),
            preset = SamplingPreset.fromJson(o.optJSONObject("preset")),
            addedAt = o.optLong("added"),
        )
    }
}

data class ToolCall(val id: String, val name: String, val arguments: String) {
    fun toJson() = JSONObject().put("id", id).put("name", name).put("arguments", arguments)

    companion object {
        fun fromJson(o: JSONObject) = ToolCall(o.optString("id"), o.optString("name"), o.optString("arguments"))
    }
}

data class GenStats(
    val promptTokens: Int = 0,
    val reusedTokens: Int = 0,
    val promptMs: Double = 0.0,
    val genTokens: Int = 0,
    val genMs: Double = 0.0,
    val ctxUsed: Int = 0,
    val nCtx: Int = 0,
    val stop: String = "",
    val draftTokens: Int = 0,
    val draftAccepted: Int = 0,
) {
    val tokensPerSecond get() = if (genMs > 0) genTokens * 1000.0 / genMs else 0.0
    val prefillPerSecond get() = if (promptMs > 0) (promptTokens - reusedTokens) * 1000.0 / promptMs else 0.0

    fun toJson() = JSONObject().apply {
        put("prompt_tokens", promptTokens)
        put("reused_tokens", reusedTokens)
        put("prompt_ms", promptMs)
        put("gen_tokens", genTokens)
        put("gen_ms", genMs)
        put("ctx_used", ctxUsed)
        put("n_ctx", nCtx)
        put("stop", stop)
        if (draftTokens > 0) {
            put("draft_tokens", draftTokens)
            put("draft_accepted", draftAccepted)
        }
    }

    /** Stats of a reply that was generated in two parts (continued after hitting the token limit). */
    fun plus(next: GenStats) = next.copy(
        genTokens = genTokens + next.genTokens,
        genMs = genMs + next.genMs,
        draftTokens = draftTokens + next.draftTokens,
        draftAccepted = draftAccepted + next.draftAccepted,
    )

    companion object {
        fun fromJson(o: JSONObject?) = o?.let {
            GenStats(
                promptTokens = it.optInt("prompt_tokens"),
                reusedTokens = it.optInt("reused_tokens"),
                promptMs = it.optDouble("prompt_ms", 0.0),
                genTokens = it.optInt("gen_tokens"),
                genMs = it.optDouble("gen_ms", 0.0),
                ctxUsed = it.optInt("ctx_used"),
                nCtx = it.optInt("n_ctx"),
                stop = it.optString("stop"),
                draftTokens = it.optInt("draft_tokens"),
                draftAccepted = it.optInt("draft_accepted"),
            )
        }
    }
}

enum class Role(val wire: String) {
    USER("user"), ASSISTANT("assistant"), TOOL("tool");

    companion object {
        fun of(s: String) = entries.firstOrNull { it.wire == s } ?: USER
    }
}

data class ChatMessage(
    val id: String = newId(),
    val role: Role,
    val content: String,
    val reasoning: String = "",
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
    val toolName: String? = null,
    val stats: GenStats? = null,
    val error: String? = null,
    /** written by the app, not the user (automatic code check feedback) */
    val auto: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson() = JSONObject().apply {
        put("id", id)
        put("role", role.wire)
        put("content", content)
        if (reasoning.isNotEmpty()) put("reasoning", reasoning)
        if (toolCalls.isNotEmpty()) put("tool_calls", JSONArray(toolCalls.map { it.toJson() }))
        toolCallId?.let { put("tool_call_id", it) }
        toolName?.let { put("tool_name", it) }
        stats?.let { put("stats", it.toJson()) }
        error?.let { put("error", it) }
        if (auto) put("auto", true)
        put("created", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject) = ChatMessage(
            id = o.optString("id").ifEmpty { newId() },
            role = Role.of(o.optString("role")),
            content = o.optString("content"),
            reasoning = o.optString("reasoning"),
            toolCalls = o.optJSONArray("tool_calls")?.mapObjects(ToolCall::fromJson) ?: emptyList(),
            toolCallId = o.optStringOrNull("tool_call_id"),
            toolName = o.optStringOrNull("tool_name"),
            stats = GenStats.fromJson(o.optJSONObject("stats")),
            error = o.optStringOrNull("error"),
            auto = o.optBoolean("auto"),
            createdAt = o.optLong("created"),
        )
    }
}

data class Conversation(
    val id: String = newId(),
    val title: String,
    val messages: List<ChatMessage> = emptyList(),
    val modelId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
) {
    fun toJson() = JSONObject().apply {
        put("id", id)
        put("title", title)
        modelId?.let { put("model", it) }
        put("created", createdAt)
        put("updated", updatedAt)
        put("messages", JSONArray(messages.map { it.toJson() }))
    }

    companion object {
        fun fromJson(o: JSONObject) = Conversation(
            id = o.getString("id"),
            title = o.optString("title"),
            modelId = o.optStringOrNull("model"),
            createdAt = o.optLong("created"),
            updatedAt = o.optLong("updated"),
            messages = o.optJSONArray("messages")?.mapObjects(ChatMessage::fromJson) ?: emptyList(),
        )
    }
}

data class MemoryItem(val id: String = newId(), val text: String, val createdAt: Long = System.currentTimeMillis()) {
    fun toJson() = JSONObject().put("id", id).put("text", text).put("created", createdAt)

    companion object {
        fun fromJson(o: JSONObject) = MemoryItem(o.getString("id"), o.optString("text"), o.optLong("created"))
    }
}
