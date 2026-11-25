package dev.pocketagent.app

import dev.pocketagent.app.api.ChatMessage
import dev.pocketagent.app.api.ChatRequest
import dev.pocketagent.app.data.Transcript
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A user or assistant text longer than Hermes's per-part cap goes out as several text parts; short texts and system messages do not change. */
class ChatRequestSplitTest {
    private val max = Transcript.MAX_PART_CHARS

    private fun messages(vararg m: ChatMessage): JSONArray = ChatRequest(messages = m.toList()).toJson().getJSONArray("messages")

    private fun textParts(content: JSONArray): List<String> =
        (0 until content.length()).map { content.getJSONObject(it) }.filter { it.getString("type") == "text" }.map { it.getString("text") }

    @Test fun aLongUserMessageBecomesSeveralTextParts() {
        val text = "word ".repeat(26_000)                       // 130,000 characters
        val msg = messages(ChatMessage("user", text)).getJSONObject(0)
        val parts = textParts(msg.getJSONArray("content"))
        assertEquals(3, parts.size)
        for (p in parts) assertTrue(p.length in 1..max)
        // Hermes joins the parts with a newline: nothing but the cut spaces may change
        assertEquals(text.filterNot { it == ' ' }, parts.joinToString("\n").filterNot { it == ' ' || it == '\n' })
    }

    @Test fun aShortMessageStaysAPlainString() {
        val arr = messages(ChatMessage("user", "0123456789"), ChatMessage("assistant", "short answer"))
        assertEquals("0123456789", arr.getJSONObject(0).getString("content"))
        assertEquals("short answer", arr.getJSONObject(1).getString("content"))
    }

    @Test fun aLongAssistantMessageIsSplitToo() {
        val arr = messages(ChatMessage("assistant", "x".repeat(max + 1)))
        assertEquals(2, arr.getJSONObject(0).getJSONArray("content").length())
    }

    @Test fun systemMessagesAreNeverTouchedHere() {
        // Transcript.build already split them: a system message that reaches the request stays one string
        val long = "s".repeat(max * 2)
        val arr = messages(ChatMessage("system", long), ChatMessage("user", "hi"))
        assertEquals(long, arr.getJSONObject(0).getString("content"))
    }

    @Test fun aLongTextWithImagesKeepsTheImagesAfterAllTextParts() {
        val text = "line\n".repeat(30_000)                      // 150,000 characters
        val msg = messages(ChatMessage("user", text, listOf("data:image/jpeg;base64,AAA="))).getJSONObject(0)
        val content = msg.getJSONArray("content")
        val types = (0 until content.length()).map { content.getJSONObject(it).getString("type") }
        assertEquals(listOf("text", "text", "text", "image_url"), types)
        assertEquals(text, textParts(content).joinToString("\n"))   // cuts at newlines: lossless
    }

    @Test fun anImageWithoutTextKeepsItsPlaceholder() {
        val content = messages(ChatMessage("user", "  ", listOf("data:image/jpeg;base64,AAA="))).getJSONObject(0).getJSONArray("content")
        assertEquals("Analyze the attached image(s).", content.getJSONObject(0).getString("text"))
        assertEquals("image_url", content.getJSONObject(1).getString("type"))
    }
}
