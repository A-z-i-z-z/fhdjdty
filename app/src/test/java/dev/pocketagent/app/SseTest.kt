package dev.pocketagent.app

import dev.pocketagent.app.api.ChatEvent
import dev.pocketagent.app.api.ChatStreamDecoder
import dev.pocketagent.app.api.SseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The stream below is the shape observed from a real `hermes gateway` v0.23.3 API server. */
class SseTest {
    private val observed = """
data: {"id": "chatcmpl-1", "object": "chat.completion.chunk", "created": 1, "model": "hermes-agent", "choices": [{"index": 0, "delta": {"role": "assistant"}, "finish_reason": null}]}

: keepalive

event: hermes.tool.progress
data: {"tool": "terminal", "emoji": "x", "label": "echo hello-from-tool", "toolCallId": "call_1", "status": "running"}

event: hermes.tool.progress
data: {"tool": "terminal", "toolCallId": "call_1", "status": "completed"}

data: {"id": "chatcmpl-1", "object": "chat.completion.chunk", "created": 1, "model": "hermes-agent", "choices": [{"index": 0, "delta": {"content": "\n\ntool-result-seen: "}, "finish_reason": null}]}

data: {"id": "chatcmpl-1", "object": "chat.completion.chunk", "created": 1, "model": "hermes-agent", "choices": [{"index": 0, "delta": {"reasoning_content": "thinking"}, "finish_reason": null}]}

data: {"id": "chatcmpl-1", "object": "chat.completion.chunk", "created": 1, "model": "hermes-agent", "choices": [{"index": 0, "delta": {}, "finish_reason": "stop"}], "usage": {"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0}}

data: [DONE]

""".trimStart('\n')

    private fun decodeAll(text: String): List<ChatEvent> {
        val p = SseParser()
        val out = ArrayList<ChatEvent>()
        for (line in text.split("\n")) p.feed(line)?.let { out += ChatStreamDecoder.decode(it) }
        return out
    }

    @Test fun decodesObservedHermesStream() {
        val ev = decodeAll(observed)
        assertEquals(ChatEvent.Tool("terminal", "echo hello-from-tool", "running", "call_1"), ev[0])
        assertEquals(ChatEvent.Tool("terminal", "", "completed", "call_1"), ev[1])
        assertEquals(ChatEvent.Delta("\n\ntool-result-seen: "), ev[2])
        assertEquals(ChatEvent.Reasoning("thinking"), ev[3])
        assertEquals(ChatEvent.Finish("stop", null), ev[4])
        assertEquals(ChatEvent.Done, ev[5])
        assertEquals(6, ev.size)
    }

    @Test fun roleOnlyAndKeepaliveProduceNothing() {
        val ev = decodeAll("data: {\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\"},\"finish_reason\":null}]}\n\n: keepalive\n\n")
        assertTrue(ev.isEmpty())
    }

    @Test fun streamErrorChunk() {
        val ev = decodeAll("data: {\"error\": {\"message\": \"admission_conflict\"}}\n\n")
        assertEquals("admission_conflict", (ev.single() as ChatEvent.StreamError).message)
    }

    @Test fun multiLineDataIsJoined() {
        val p = SseParser()
        p.feed("data: a"); p.feed("data: b")
        assertEquals("a\nb", p.feed("")!!.data)
    }
}
