package app.yerel.core

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The native engine as seen by the agent loop; blocking, called from the engine thread. */
interface LlmBackend {
    fun generate(messagesJson: String, toolsJson: String, paramsJson: String, onUpdate: (String) -> Unit): String
}

/** A parsed assistant message from the engine (final or partial). */
data class ParsedMessage(val content: String, val reasoning: String, val toolCalls: List<ToolCall>)

data class EngineResult(val message: ParsedMessage?, val stats: GenStats?, val error: String?)

object EngineJson {
    fun parseMessage(o: JSONObject?): ParsedMessage? {
        if (o == null) return null
        val calls = o.optJSONArray("tool_calls")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val tc = arr.optJSONObject(i) ?: return@mapNotNull null
                val fn = tc.optJSONObject("function") ?: return@mapNotNull null
                val args = fn.opt("arguments")
                ToolCall(
                    id = tc.optString("id"),
                    name = fn.optString("name"),
                    arguments = when (args) {
                        is String -> args
                        null -> ""
                        else -> args.toString()
                    },
                )
            }
        } ?: emptyList()
        val content = o.opt("content").let { if (it is String) it else "" }
        return ParsedMessage(content, o.optString("reasoning_content"), calls)
    }

    fun parseResult(json: String): EngineResult = try {
        val o = JSONObject(json)
        EngineResult(parseMessage(o.optJSONObject("message")), GenStats.fromJson(o.optJSONObject("stats")), o.optStringOrNull("error"))
    } catch (e: Exception) {
        EngineResult(null, null, "invalid engine response: ${e.message}")
    }

    data class Partial(val message: ParsedMessage, val nGen: Int, val tps: Double)

    fun parsePartial(json: String): Partial? = try {
        val o = JSONObject(json)
        parseMessage(o.optJSONObject("message"))?.let { Partial(it, o.optInt("n_gen"), o.optDouble("tps", 0.0)) }
    } catch (e: Exception) {
        null
    }
}

object Prompt {
    fun systemPrompt(base: String, memories: List<MemoryItem>, now: Date, toolsOn: Boolean, locale: Locale = Locale("tr")): String {
        val sb = StringBuilder(base.trim())
        // day precision only: a changing system prompt would invalidate the whole KV cache every turn
        val date = SimpleDateFormat("d MMMM yyyy, EEEE", locale).format(now)
        sb.append("\n\nBugünün tarihi: ").append(date).append('.')
        if (memories.isNotEmpty()) {
            sb.append("\n\nKullanıcı hakkında hafızada kayıtlı bilgiler (id: bilgi):")
            for (m in memories) sb.append("\n- ").append(m.id.take(8)).append(": ").append(m.text)
        }
        if (toolsOn) {
            sb.append(
                "\n\nAraçların var. Güncel bilgi, hesaplama veya web sayfası gerektiğinde araç çağır; " +
                    "araç sonucunu kullanarak cevap ver ve kaynak URL'lerini belirt. Kullanıcı kalıcı bir bilgi " +
                    "paylaşırsa (adı, tercihleri) remember aracıyla kaydet.",
            )
        }
        return sb.toString()
    }

    /** OpenAI-format messages for the engine. Failed or empty messages are left out. */
    fun engineMessages(system: String, history: List<ChatMessage>): String {
        val arr = JSONArray()
        if (system.isNotBlank()) arr.put(JSONObject().put("role", "system").put("content", system))
        for (m in history) {
            if (m.error != null && m.content.isEmpty() && m.toolCalls.isEmpty()) continue
            when (m.role) {
                Role.USER -> arr.put(JSONObject().put("role", "user").put("content", m.content))
                Role.ASSISTANT -> {
                    if (m.content.isEmpty() && m.toolCalls.isEmpty()) continue
                    val o = JSONObject().put("role", "assistant").put("content", m.content)
                    if (m.reasoning.isNotEmpty()) o.put("reasoning_content", m.reasoning)
                    if (m.toolCalls.isNotEmpty()) {
                        o.put("tool_calls", JSONArray(m.toolCalls.map { tc ->
                            JSONObject().put("id", tc.id).put("type", "function").put(
                                "function", JSONObject().put("name", tc.name).put("arguments", tc.arguments.ifBlank { "{}" }),
                            )
                        }))
                    }
                    arr.put(o)
                }
                Role.TOOL -> {
                    val o = JSONObject().put("role", "tool").put("content", m.content)
                    m.toolCallId?.let { o.put("tool_call_id", it) }
                    m.toolName?.let { o.put("name", it) }
                    arr.put(o)
                }
            }
        }
        return arr.toString()
    }

    /**
     * Drops the oldest [units] exchanges (a user message and everything up to the next user message),
     * always keeping the latest one. Returns null when nothing more can be dropped.
     */
    fun dropOldest(history: List<ChatMessage>, units: Int = 1): List<ChatMessage>? {
        val starts = history.indices.filter { history[it].role == Role.USER }
        if (starts.size <= units) return null
        return history.subList(starts[units], history.size)
    }
}

sealed class AgentEvent {
    data class Partial(val content: String, val reasoning: String, val toolCalls: List<ToolCall>, val nGen: Int, val tps: Double) : AgentEvent()
    data class Added(val message: ChatMessage) : AgentEvent()
    data class ToolStarted(val call: ToolCall) : AgentEvent()
    data class Trimmed(val droppedMessages: Int) : AgentEvent()
}

data class AgentConfig(
    val systemPrompt: String,
    val toolsJson: String,
    val paramsJson: String,
    val maxSteps: Int,
)

/** Runs generate → tool calls → generate until the model answers without tools. */
class AgentRunner(
    private val backend: LlmBackend,
    private val tools: ToolRunner,
    private val isCancelled: () -> Boolean,
) {
    private var callCounter = 0

    fun run(history: List<ChatMessage>, config: AgentConfig, emit: (AgentEvent) -> Unit): List<ChatMessage> {
        var context = history
        val added = mutableListOf<ChatMessage>()
        var step = 0
        while (step < config.maxSteps.coerceAtLeast(1)) {
            if (isCancelled()) break
            val messages = Prompt.engineMessages(config.systemPrompt, context + added)
            val raw = backend.generate(messages, config.toolsJson, config.paramsJson) { partial ->
                EngineJson.parsePartial(partial)?.let {
                    emit(AgentEvent.Partial(it.message.content, it.message.reasoning, it.message.toolCalls, it.nGen, it.tps))
                }
            }
            val result = EngineJson.parseResult(raw)
            if (result.error == "context_overflow") {
                val trimmed = Prompt.dropOldest(context)
                if (trimmed != null) {
                    emit(AgentEvent.Trimmed(context.size - trimmed.size))
                    context = trimmed
                    continue
                }
                val err = ChatMessage(
                    role = Role.ASSISTANT, content = "",
                    error = "Mesaj bağlam penceresine sığmıyor. Ayarlardan bağlam boyutunu artırın veya mesajı kısaltın.",
                )
                added += err
                emit(AgentEvent.Added(err))
                break
            }
            if (result.error != null || result.message == null) {
                val err = ChatMessage(role = Role.ASSISTANT, content = "", error = result.error ?: "boş cevap")
                added += err
                emit(AgentEvent.Added(err))
                break
            }
            val calls = result.message.toolCalls.map { tc ->
                if (tc.id.isNotBlank()) tc else tc.copy(id = "call_${++callCounter}_${System.nanoTime() % 100000}")
            }
            val stopped = result.stats?.stop == "cancel"
            val assistant = ChatMessage(
                role = Role.ASSISTANT,
                content = result.message.content,
                reasoning = result.message.reasoning,
                toolCalls = if (stopped) emptyList() else calls,
                stats = result.stats,
            )
            added += assistant
            emit(AgentEvent.Added(assistant))
            if (stopped || calls.isEmpty()) break

            for (call in calls) {
                if (isCancelled()) break
                emit(AgentEvent.ToolStarted(call))
                val outcome = tools.run(call.name, call.arguments)
                val toolMsg = ChatMessage(
                    role = Role.TOOL,
                    content = outcome.content,
                    toolCallId = call.id,
                    toolName = call.name,
                    reasoning = outcome.summary,
                    error = if (outcome.ok) null else outcome.summary,
                )
                added += toolMsg
                emit(AgentEvent.Added(toolMsg))
            }
            step++
        }
        return added
    }
}
