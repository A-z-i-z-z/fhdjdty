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

/** Minimal client for Hermes's OpenAI-compatible API server on loopback. */
class HermesApi(private val apiKey: () -> String?) {
    private val base = "http://${Paths.API_HOST}:${Paths.API_PORT}"
    @Volatile private var activeStream: HttpURLConnection? = null
    @Volatile private var cancelRequested = false

    fun health(): HttpResult = get("/health", auth = false, timeoutMs = 3000)
    fun models(): HttpResult = get("/v1/models", auth = true, timeoutMs = 5000)

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

    fun cancelChat() {
        cancelRequested = true
        try { activeStream?.disconnect() } catch (_: Exception) { }
    }

    /**
     * POST /v1/chat/completions with stream=true. Only the new user message is sent: with an
     * `X-Hermes-Session-Id` header Hermes rebuilds history from its own session store.
     * Blocks until the stream ends; call from a background thread.
     */
    fun streamChat(userText: String, sessionId: String, model: String, onEvent: (ChatEvent) -> Unit): ChatOutcome {
        cancelRequested = false
        val t0 = System.nanoTime()
        val body = JSONObject()
            .put("model", model)
            .put("stream", true)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", userText)))
            .toString()
        var conn: HttpURLConnection? = null
        try {
            // Read timeout is generous: Hermes retries unavailable providers for minutes and
            // sends ": keepalive" comments every 10 s, which also reset this timer.
            conn = open("/v1/chat/completions", "POST", true, 5000, 180_000).also {
                it.setRequestProperty("Content-Type", "application/json")
                it.setRequestProperty("Accept", "text/event-stream")
                it.setRequestProperty("X-Hermes-Session-Id", sessionId)
                it.doOutput = true
                it.setChunkedStreamingMode(0)
            }
            activeStream = conn
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = readAll(conn.errorStream)
                ApiLog.add("POST /v1/chat/completions (stream, ${userText.length} chars) -> $code ${(System.nanoTime() - t0) / 1_000_000}ms body=${trim(err)}")
                return ChatOutcome.HttpError(code, err)
            }
            ApiLog.add("POST /v1/chat/completions (stream, ${userText.length} chars, session=$sessionId) -> $code headers in ${(System.nanoTime() - t0) / 1_000_000}ms")
            val parser = SseParser()
            var events = 0; var chars = 0; var tools = 0; var sawDone = false
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    val ev = parser.feed(line) ?: continue
                    for (ce in ChatStreamDecoder.decode(ev)) {
                        events++
                        when (ce) {
                            is ChatEvent.Delta -> chars += ce.text.length
                            is ChatEvent.Tool -> tools++
                            is ChatEvent.StreamError -> ApiLog.add("stream error event: ${trim(ce.raw)}")
                            is ChatEvent.Done -> sawDone = true
                            else -> {}
                        }
                        onEvent(ce)
                    }
                }
            }
            ApiLog.add("stream ended: events=$events text_chars=$chars tool_events=$tools done=$sawDone total=${(System.nanoTime() - t0) / 1_000_000}ms")
            return ChatOutcome.Completed
        } catch (e: Exception) {
            if (cancelRequested) { ApiLog.add("stream cancelled by user"); return ChatOutcome.Cancelled }
            ApiLog.add("stream failed: ${e.javaClass.simpleName}: ${e.message} after ${(System.nanoTime() - t0) / 1_000_000}ms")
            return ChatOutcome.TransportError("${e.javaClass.simpleName}: ${e.message}")
        } finally {
            activeStream = null
            try { conn?.disconnect() } catch (_: Exception) { }
        }
    }

    private fun open(path: String, method: String, auth: Boolean, connectMs: Int, readMs: Int): HttpURLConnection {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = connectMs
        c.readTimeout = readMs
        c.useCaches = false
        if (auth) apiKey()?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        return c
    }

    private fun readAll(s: java.io.InputStream?): String =
        s?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

    private fun trim(s: String) = Diag.redactor.redact(s).replace('\n', ' ').take(600)
}
