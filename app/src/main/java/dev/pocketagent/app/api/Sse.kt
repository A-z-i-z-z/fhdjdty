package dev.pocketagent.app.api

import org.json.JSONObject

/** One dispatched server-sent event. [event] is null for the default ("message") type. */
data class SseEvent(val event: String?, val data: String)

/** Incremental SSE line parser (https://html.spec.whatwg.org/multipage/server-sent-events.html). */
class SseParser {
    private var event: String? = null
    private val data = StringBuilder()
    private var hasData = false

    /** Feed one line (without terminator). Returns an event when a blank line completes one. */
    fun feed(line: String): SseEvent? {
        if (line.isEmpty()) {
            if (!hasData && event == null) return null
            val ev = SseEvent(event, data.toString())
            event = null; data.setLength(0); hasData = false
            return ev
        }
        if (line.startsWith(":")) return null // comment / ": keepalive"
        val idx = line.indexOf(':')
        val field = if (idx < 0) line else line.substring(0, idx)
        var value = if (idx < 0) "" else line.substring(idx + 1)
        if (value.startsWith(" ")) value = value.substring(1)
        when (field) {
            "event" -> event = value
            "data" -> { if (hasData) data.append('\n'); data.append(value); hasData = true }
            else -> { /* id, retry: unused */ }
        }
        return null
    }
}

/** What the chat UI cares about, decoded from Hermes's chat.completion.chunk stream. */
sealed class ChatEvent {
    data class Delta(val text: String) : ChatEvent()
    data class Reasoning(val text: String) : ChatEvent()
    /** `event: hermes.tool.progress` — e.g. {"tool":"terminal","label":"ls","status":"running","toolCallId":"call_1"}. */
    data class Tool(val tool: String, val label: String, val status: String, val callId: String) : ChatEvent()
    data class Finish(val reason: String, val errorCode: String?) : ChatEvent()
    /** An error object delivered inside the stream (HTTP status was 200). [message] is Hermes's own text (may be empty); [raw] is the whole chunk, for the log only. */
    data class StreamError(val message: String, val raw: String) : ChatEvent()
    /** `event: hermes.status` — lifecycle/warning text while the stream is quiet (provider wait, retry countdown, fallback switch). */
    data class Status(val kind: String, val text: String) : ChatEvent()
    /**
     * `event: approval.request` — Hermes wants permission to run a flagged command and waits. Answer with
     * `HermesApi.resolveApproval(runId, choice)` where choice is one of [choices] (once, session, always, deny).
     */
    data class Approval(
        val runId: String, val command: String, val description: String,
        val choices: List<String>, val requestId: String, val raw: String,
        /** `execution_generation` of the run (an integer Hermes requires back in the answer); null when the event did not carry one. */
        val generation: Long? = null,
    ) : ChatEvent()
    /**
     * Token counts of the whole turn (all model calls of one request, tool loops included), from the `usage` object on the
     * final chunk. Hermes builds a fresh agent per request, so these are per request, not per session. Emitted only when
     * some count is greater than 0 (a failed agent reports zeros), and always before [Finish].
     */
    data class Usage(val promptTokens: Long, val completionTokens: Long, val totalTokens: Long) : ChatEvent()
    object Done : ChatEvent()
}

/** optString returns the text "null" for a JSON null; this returns [def] instead. */
private fun JSONObject.str(name: String, def: String): String = if (isNull(name)) def else optString(name, def)

object ChatStreamDecoder {
    fun decode(ev: SseEvent): List<ChatEvent> {
        val d = ev.data.trim()
        if (d == "[DONE]") return listOf(ChatEvent.Done)
        if (d.isEmpty()) return emptyList()
        val obj = try { JSONObject(d) } catch (_: Exception) { return emptyList() }
        if (ev.event == "hermes.tool.progress") {
            return listOf(ChatEvent.Tool(
                tool = obj.str("tool", "?"),
                label = obj.str("label", ""),
                status = obj.str("status", ""),
                callId = obj.str("toolCallId", ""),
            ))
        }
        if (ev.event == "hermes.status") {
            val text = obj.str("text", "")
            if (text.isEmpty()) return emptyList()
            return listOf(ChatEvent.Status(obj.str("kind", ""), text))
        }
        if (ev.event == "approval.request") {
            val choices = ArrayList<String>()
            obj.optJSONArray("choices")?.let { a -> for (i in 0 until a.length()) (if (a.isNull(i)) "" else a.optString(i, "")).takeIf { it.isNotEmpty() }?.let { choices += it } }
            return listOf(ChatEvent.Approval(
                runId = obj.str("run_id", ""),
                command = obj.str("command", ""),
                description = obj.str("description", ""),
                choices = choices,
                requestId = obj.str("request_id", ""),
                raw = d,
                generation = if (obj.has("execution_generation") && !obj.isNull("execution_generation")) obj.optLong("execution_generation", -1L).takeIf { it >= 0L } else null,
            ))
        }
        if (ev.event != null && ev.event != "message") return emptyList() // unknown named event: ignore
        val out = ArrayList<ChatEvent>()
        // A failed turn ends with ONE chunk that carries both `error` and `finish_reason`: report the error first, then the finish.
        obj.optJSONObject("error")?.let { out += ChatEvent.StreamError(it.str("message", ""), d) }
        // Token counts ride on the chunk that carries finish_reason (or on a chunk of their own): report them before the finish.
        obj.optJSONObject("usage")?.let { u ->
            val p = u.optLong("prompt_tokens", 0L).coerceAtLeast(0L)
            val c = u.optLong("completion_tokens", 0L).coerceAtLeast(0L)
            if (p > 0L || c > 0L) {
                val t = u.optLong("total_tokens", 0L).let { if (it > 0L) it else p + c }
                out += ChatEvent.Usage(p, c, t)
            }
        }
        val choices = obj.optJSONArray("choices") ?: return out
        for (i in 0 until choices.length()) {
            val c = choices.optJSONObject(i) ?: continue
            val delta = c.optJSONObject("delta")
            if (delta != null) {
                val rc = delta.str("reasoning_content", "")
                if (rc.isNotEmpty()) out += ChatEvent.Reasoning(rc)
                val content = delta.str("content", "")
                if (content.isNotEmpty() && !delta.isNull("content")) out += ChatEvent.Delta(content)
            }
            if (!c.isNull("finish_reason")) {
                val code = obj.optJSONObject("hermes")?.takeIf { !it.isNull("error_code") }?.str("error_code", "")?.takeIf { it.isNotEmpty() }
                out += ChatEvent.Finish(c.str("finish_reason", ""), code)
            }
        }
        return out
    }
}
