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
    /** An error object delivered inside the stream (HTTP status was 200). */
    data class StreamError(val message: String, val raw: String) : ChatEvent()
    object Done : ChatEvent()
}

object ChatStreamDecoder {
    fun decode(ev: SseEvent): List<ChatEvent> {
        val d = ev.data.trim()
        if (d == "[DONE]") return listOf(ChatEvent.Done)
        if (d.isEmpty()) return emptyList()
        val obj = try { JSONObject(d) } catch (_: Exception) { return emptyList() }
        if (ev.event == "hermes.tool.progress") {
            return listOf(ChatEvent.Tool(
                tool = obj.optString("tool", "?"),
                label = obj.optString("label", ""),
                status = obj.optString("status", ""),
                callId = obj.optString("toolCallId", ""),
            ))
        }
        if (ev.event != null && ev.event != "message") return emptyList() // unknown named event: ignore
        obj.optJSONObject("error")?.let { return listOf(ChatEvent.StreamError(it.optString("message", d), d)) }
        val out = ArrayList<ChatEvent>()
        val choices = obj.optJSONArray("choices") ?: return out
        for (i in 0 until choices.length()) {
            val c = choices.optJSONObject(i) ?: continue
            val delta = c.optJSONObject("delta")
            if (delta != null) {
                val rc = delta.optString("reasoning_content", "")
                if (rc.isNotEmpty()) out += ChatEvent.Reasoning(rc)
                val content = delta.optString("content", "")
                if (content.isNotEmpty() && !delta.isNull("content")) out += ChatEvent.Delta(content)
            }
            if (!c.isNull("finish_reason")) {
                val code = obj.optJSONObject("hermes")?.optString("error_code")?.takeIf { it.isNotEmpty() }
                out += ChatEvent.Finish(c.optString("finish_reason"), code)
            }
        }
        return out
    }
}
