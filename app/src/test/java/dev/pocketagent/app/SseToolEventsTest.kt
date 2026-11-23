package dev.pocketagent.app

import dev.pocketagent.app.api.ChatEvent
import dev.pocketagent.app.api.ChatMessage
import dev.pocketagent.app.api.ChatRequest
import dev.pocketagent.app.api.ChatStreamDecoder
import dev.pocketagent.app.api.SseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Decoding of the named events and the request body (shapes from Hermes v0.23.3 api_server_openai_routes.py). */
class SseToolEventsTest {
    private fun decodeAll(text: String): List<ChatEvent> {
        val p = SseParser()
        val out = ArrayList<ChatEvent>()
        for (line in text.split("\n")) p.feed(line)?.let { out += ChatStreamDecoder.decode(it) }
        return out
    }

    @Test fun toolProgressCarriesNamePreviewStatusAndCallId() {
        val ev = decodeAll(
            "event: hermes.tool.progress\n" +
                "data: {\"tool\": \"terminal\", \"emoji\": \"x\", \"label\": \"ls -la /root\", \"toolCallId\": \"call_9\", \"status\": \"running\"}\n\n" +
                "event: hermes.tool.progress\n" +
                "data: {\"tool\": \"terminal\", \"toolCallId\": \"call_9\", \"status\": \"completed\"}\n\n"
        )
        assertEquals(listOf<ChatEvent>(
            ChatEvent.Tool("terminal", "ls -la /root", "running", "call_9"),
            ChatEvent.Tool("terminal", "", "completed", "call_9"),
        ), ev)
    }

    @Test fun reasoningDeltaIsSeparateFromAnswerText() {
        val ev = decodeAll(
            "data: {\"choices\":[{\"index\":0,\"delta\":{\"reasoning_content\":\"let me think\"},\"finish_reason\":null}]}\n\n" +
                "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"42\"},\"finish_reason\":null}]}\n\n"
        )
        assertEquals(listOf<ChatEvent>(ChatEvent.Reasoning("let me think"), ChatEvent.Delta("42")), ev)
    }

    @Test fun statusEvent() {
        val ev = decodeAll("event: hermes.status\ndata: {\"kind\": \"lifecycle\", \"text\": \"Provider is busy, retrying in 5s\"}\n\n")
        assertEquals(listOf<ChatEvent>(ChatEvent.Status("lifecycle", "Provider is busy, retrying in 5s")), ev)
        assertTrue("empty status text is dropped", decodeAll("event: hermes.status\ndata: {\"kind\": \"x\", \"text\": \"\"}\n\n").isEmpty())
    }

    @Test fun approvalRequestEvent() {
        val raw = "{\"event\":\"approval.request\",\"run_id\":\"chatcmpl-abc\",\"command\":\"rm -rf build\",\"description\":\"recursive delete\"," +
            "\"choices\":[\"once\",\"session\",\"deny\"],\"request_id\":\"r1\"}"
        val a = decodeAll("event: approval.request\ndata: $raw\n\n").single() as ChatEvent.Approval
        assertEquals("chatcmpl-abc", a.runId)
        assertEquals("rm -rf build", a.command)
        assertEquals("recursive delete", a.description)
        assertEquals(listOf("once", "session", "deny"), a.choices)
        assertEquals("r1", a.requestId)
    }

    @Test fun failedTurnChunkYieldsTheErrorAndTheFinish() {
        val ev = decodeAll(
            "data: {\"id\":\"c\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"error\"}],\"error\":{\"message\":\"upstream 429\",\"type\":\"agent_error\"},\"hermes\":{\"error_code\":\"rate_limit\"}}\n\n"
        )
        assertEquals(2, ev.size)
        assertEquals("upstream 429", (ev[0] as ChatEvent.StreamError).message)
        assertEquals(ChatEvent.Finish("error", "rate_limit"), ev[1])
    }

    @Test fun unknownNamedEventsAreIgnored() {
        assertTrue(decodeAll("event: hermes.something\ndata: {\"a\":1}\n\n").isEmpty())
    }

    // ---- request body ------------------------------------------------------------------------------------------------

    @Test fun plainRequestUsesTheVirtualModelAndNoProvider() {
        val j = ChatRequest(listOf(ChatMessage("system", "s"), ChatMessage("user", "u"))).toJson()
        assertEquals("hermes-agent", j.getString("model"))
        assertTrue(j.getBoolean("stream"))
        assertFalse(j.has("provider"))
        assertFalse(j.has("model_options"))
        val m = j.getJSONArray("messages")
        assertEquals(2, m.length())
        assertEquals("system", m.getJSONObject(0).getString("role"))
        assertEquals("u", m.getJSONObject(1).getString("content"))
    }

    @Test fun modelNeedsAProviderAndEffortGoesIntoModelOptions() {
        val j = ChatRequest(listOf(ChatMessage("user", "u")), model = "google/gemini-4-neon", provider = "vertex", reasoningEffort = "low").toJson()
        assertEquals("google/gemini-4-neon", j.getString("model"))
        assertEquals("vertex", j.getString("provider"))
        assertEquals("low", j.getJSONObject("model_options").getString("reasoning_effort"))
        // A bare model is ignored by Hermes, so a provider without a model is not sent either.
        val k = ChatRequest(listOf(ChatMessage("user", "u")), model = "", provider = "vertex", reasoningEffort = "high").toJson()
        assertEquals("hermes-agent", k.getString("model"))
        assertFalse(k.has("provider"))
        assertEquals("high", k.getJSONObject("model_options").getString("reasoning_effort"))
    }

    @Test fun jsonNullsAreNotTheTextNull() {
        val ev = decodeAll(
            "data: {\"choices\":[{\"index\":0,\"delta\":{\"reasoning_content\":null,\"content\":null},\"finish_reason\":\"error\"}],\"hermes\":{\"error_code\":null}}\n\n"
        )
        assertEquals(listOf<ChatEvent>(ChatEvent.Finish("error", null)), ev)
    }
}
