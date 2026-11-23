package dev.pocketagent.app.api

import dev.pocketagent.app.diag.ApiLog
import dev.pocketagent.app.diag.Diag
import dev.pocketagent.app.runtime.Paths
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class HttpResult(val code: Int, val body: String, val millis: Long, val error: String? = null) {
    val ok get() = code in 200..299
    /** Verbatim "HTTP 401: {...}" or the transport error. */
    fun describe(): String = if (error != null) error else "HTTP $code: $body"
}

sealed class ChatOutcome {
    object Completed : ChatOutcome()
    object Cancelled : ChatOutcome()
    /** Non-2xx answer: status and body verbatim. */
    data class HttpError(val code: Int, val body: String) : ChatOutcome()
    data class TransportError(val message: String) : ChatOutcome()
}

/** One message of a chat request. [role] is system, user or assistant. */
data class ChatMessage(val role: String, val content: String)

/**
 * A streaming chat request. Hermes v0.23.3 (api_server_openai_routes.py): `system` messages become an ephemeral system
 * prompt on top of Hermes's core prompt; user/assistant messages are the history and the LAST one is the new user message.
 *
 * - [model] "" or "hermes-agent" means "Hermes's configured model". A real model id is only honoured together with
 *   [provider] (a bare model is ignored by default: `direct_model_requests` is off).
 * - [reasoningEffort] goes out as `model_options.reasoning_effort` (none/minimal/low/medium/high/xhigh/max/ultra).
 * - [sessionId] sends `X-Hermes-Session-Id`. Careful: with that header Hermes IGNORES the history in [messages] and
 *   rebuilds it from its own session store (only the last message is used). ChatEngine therefore leaves it null.
 */
data class ChatRequest(
    val messages: List<ChatMessage>,
    val model: String = "",
    val provider: String? = null,
    val reasoningEffort: String? = null,
    val sessionId: String? = null,
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        val m = model.trim()
        o.put("model", if (m.isEmpty()) VIRTUAL_MODEL else m)
        if (!provider.isNullOrBlank() && m.isNotEmpty() && m != VIRTUAL_MODEL) o.put("provider", provider.trim())
        o.put("stream", true)
        if (!reasoningEffort.isNullOrBlank()) o.put("model_options", JSONObject().put("reasoning_effort", reasoningEffort.trim()))
        o.put("messages", JSONArray().also { a -> messages.forEach { a.put(JSONObject().put("role", it.role).put("content", it.content)) } })
        return o
    }

    companion object {
        /** The name Hermes advertises on /v1/models; it means "gateway default". */
        const val VIRTUAL_MODEL = "hermes-agent"
    }
}

/** Cancels one running stream; every concurrent stream has its own handle. */
class StreamHandle {
    @Volatile internal var conn: HttpURLConnection? = null
    @Volatile var cancelled: Boolean = false
        private set

    fun cancel() {
        cancelled = true
        try { conn?.disconnect() } catch (_: Exception) { }
    }
}

/** Minimal client for Hermes's OpenAI-compatible API server on loopback. */
class HermesApi(
    /** Port of the CURRENT launch (random per launch). */
    private val port: () -> Int,
    /** The Bearer key, or null while the listener on [port] has not been proven to be our own Hermes. */
    private val apiKey: () -> String?,
) {
    private fun base() = "http://${Paths.API_HOST}:${port()}"
    @Volatile private var legacyHandle = StreamHandle()

    fun health(): HttpResult = get("/health", auth = false, timeoutMs = 3000)
    fun models(): HttpResult = get("/v1/models", auth = true, timeoutMs = 5000)
    fun capabilities(): HttpResult = get("/v1/capabilities", auth = true, timeoutMs = 5000)
    fun skills(): HttpResult = get("/v1/skills", auth = true, timeoutMs = 10_000)
    fun toolsets(): HttpResult = get("/v1/toolsets", auth = true, timeoutMs = 10_000)
    fun sessions(): HttpResult = get("/api/sessions", auth = true, timeoutMs = 10_000)
    fun modelOptions(): HttpResult = get("/api/model/options", auth = true, timeoutMs = 15_000)

    private fun get(path: String, auth: Boolean, timeoutMs: Int): HttpResult {
        val t0 = System.nanoTime()
        return try {
            val c = open(path, "GET", auth, timeoutMs, timeoutMs)
            val code = c.responseCode
            val body = readAll(if (code >= 400) c.errorStream else c.inputStream)
            c.disconnect()
            val ms = (System.nanoTime() - t0) / 1_000_000
            ApiLog.add("GET $path -> $code ${ms}ms" + if (code >= 400) " body=${trim(body)}" else "")
            HttpResult(code, body, ms)
        } catch (e: Exception) {
            val ms = (System.nanoTime() - t0) / 1_000_000
            ApiLog.add("GET $path -> ${e.javaClass.simpleName}: ${e.message} ${ms}ms")
            HttpResult(-1, "", ms, "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /**
     * Answers an `approval.request` event (see [ChatEvent.Approval]): POST /v1/runs/{runId}/approval.
     * [choice] is once, session, always or deny. The run id comes from the stream, so it is checked before it goes into a URL.
     */
    fun resolveApproval(runId: String, choice: String, requestId: String? = null): HttpResult {
        if (!Regex("^[A-Za-z0-9_.:-]{1,128}$").matches(runId)) return HttpResult(-1, "", 0, "invalid run id")
        val body = JSONObject().put("choice", choice).also { if (!requestId.isNullOrBlank()) it.put("request_id", requestId) }.toString()
        val t0 = System.nanoTime()
        return try {
            val c = open("/v1/runs/$runId/approval", "POST", true, 5000, 15_000)
            c.setRequestProperty("Content-Type", "application/json")
            c.doOutput = true
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val text = readAll(if (code >= 400) c.errorStream else c.inputStream)
            c.disconnect()
            val ms = (System.nanoTime() - t0) / 1_000_000
            ApiLog.add("POST /v1/runs/*/approval choice=$choice -> $code ${ms}ms" + if (code >= 400) " body=${trim(text)}" else "")
            HttpResult(code, text, ms)
        } catch (e: Exception) {
            val ms = (System.nanoTime() - t0) / 1_000_000
            ApiLog.add("POST /v1/runs/*/approval -> ${e.javaClass.simpleName}: ${e.message} ${ms}ms")
            HttpResult(-1, "", ms, "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** Cancels the stream started with the legacy [streamChat]. Streams started with [streamCompletion] use their own [StreamHandle]. */
    fun cancelChat() = legacyHandle.cancel()

    /**
     * Legacy single-chat call kept for MainViewModel: sends only the new user message plus `X-Hermes-Session-Id`, so
     * Hermes rebuilds history from its own session store. Blocks until the stream ends; call from a background thread.
     */
    fun streamChat(userText: String, sessionId: String, model: String, onEvent: (ChatEvent) -> Unit): ChatOutcome {
        val handle = StreamHandle().also { legacyHandle = it }
        val req = ChatRequest(listOf(ChatMessage("user", userText)), model = model, sessionId = sessionId)
        return streamCompletion(req, handle, onEvent)
    }

    /**
     * POST /v1/chat/completions with stream=true. Blocks until the stream ends; call from a background thread. [handle]
     * cancels this stream (and only this one), so several conversations can stream at the same time.
     */
    fun streamCompletion(request: ChatRequest, handle: StreamHandle, onEvent: (ChatEvent) -> Unit): ChatOutcome {
        val t0 = System.nanoTime()
        val body = request.toJson().toString()
        val chars = request.messages.sumOf { it.content.length }
        val desc = "POST /v1/chat/completions (stream, ${request.messages.size} msgs, $chars chars" +
            (request.model.ifBlank { null }?.let { ", model=$it" } ?: "") + (request.reasoningEffort?.let { ", effort=$it" } ?: "") +
            (request.sessionId?.let { ", session=$it" } ?: "") + ")"
        var conn: HttpURLConnection? = null
        try {
            if (handle.cancelled) return ChatOutcome.Cancelled
            // Read timeout is generous: Hermes retries unavailable providers for minutes and
            // sends ": keepalive" comments every 10 s, which also reset this timer.
            conn = open("/v1/chat/completions", "POST", true, 5000, 180_000).also {
                it.setRequestProperty("Content-Type", "application/json")
                it.setRequestProperty("Accept", "text/event-stream")
                request.sessionId?.let { sid -> it.setRequestProperty("X-Hermes-Session-Id", sid) }
                it.doOutput = true
                it.setChunkedStreamingMode(0)
            }
            handle.conn = conn
            if (handle.cancelled) return ChatOutcome.Cancelled
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = readAll(conn.errorStream)
                ApiLog.add("$desc -> $code ${(System.nanoTime() - t0) / 1_000_000}ms body=${trim(err)}")
                return ChatOutcome.HttpError(code, err)
            }
            ApiLog.add("$desc -> $code headers in ${(System.nanoTime() - t0) / 1_000_000}ms")
            val parser = SseParser()
            var events = 0; var chars2 = 0; var tools = 0; var sawDone = false
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    val ev = parser.feed(line) ?: continue
                    for (ce in ChatStreamDecoder.decode(ev)) {
                        events++
                        when (ce) {
                            is ChatEvent.Delta -> chars2 += ce.text.length
                            is ChatEvent.Tool -> tools++
                            is ChatEvent.StreamError -> ApiLog.add("stream error event: ${trim(ce.raw)}")
                            is ChatEvent.Approval -> ApiLog.add("approval requested (run ${ce.runId}, choices ${ce.choices})")
                            is ChatEvent.Done -> sawDone = true
                            else -> {}
                        }
                        onEvent(ce)
                    }
                }
            }
            ApiLog.add("stream ended: events=$events text_chars=$chars2 tool_events=$tools done=$sawDone total=${(System.nanoTime() - t0) / 1_000_000}ms")
            if (handle.cancelled) return ChatOutcome.Cancelled
            // A well-formed Hermes stream always ends with `data: [DONE]`; anything else means the
            // connection or the gateway died mid-answer.
            return if (sawDone) ChatOutcome.Completed
            else ChatOutcome.TransportError("stream ended without [DONE] (connection or gateway died mid-answer; $chars2 characters received)")
        } catch (e: Exception) {
            if (handle.cancelled) { ApiLog.add("stream cancelled by user"); return ChatOutcome.Cancelled }
            ApiLog.add("stream failed: ${e.javaClass.simpleName}: ${e.message} after ${(System.nanoTime() - t0) / 1_000_000}ms")
            return ChatOutcome.TransportError("${e.javaClass.simpleName}: ${e.message}")
        } finally {
            handle.conn = null
            try { conn?.disconnect() } catch (_: Exception) { }
        }
    }

    private fun open(path: String, method: String, auth: Boolean, connectMs: Int, readMs: Int): HttpURLConnection {
        // Loopback only; never through a system/enterprise proxy.
        val c = URL(base() + path).openConnection(java.net.Proxy.NO_PROXY) as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = connectMs
        c.readTimeout = readMs
        c.useCaches = false
        if (auth) {
            val k = apiKey() ?: throw java.io.IOException("refusing to send the API key: the listener on this port is not verified as our Hermes")
            c.setRequestProperty("Authorization", "Bearer $k")
        }
        return c
    }

    private fun readAll(s: java.io.InputStream?): String =
        s?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

    private fun trim(s: String) = Diag.redactor.redact(s).replace('\n', ' ').take(600)
}
