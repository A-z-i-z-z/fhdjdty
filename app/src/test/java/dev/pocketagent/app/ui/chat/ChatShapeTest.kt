package dev.pocketagent.app.ui.chat

import dev.pocketagent.app.data.Message
import dev.pocketagent.app.data.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun testMessage(
    id: String,
    role: Role,
    botId: String? = null,
    text: String = "t",
    createdAt: Long = 1_000L,
    streaming: Boolean = false,
) = Message(id = id, convId = "c", role = role, botId = botId, text = text, createdAt = createdAt, streaming = streaming)

class ChatShapeTest {
    @Test fun emptyChat() {
        val s = chatShapeOf(emptyList())
        assertEquals(0, s.count)
        assertNull(s.lastId)
        assertNull(s.lastBotId)
        assertFalse(s.lastBotIsLast)
        assertFalse(s.lastStreaming)
    }

    @Test fun lastBotIsNotLastWhenToolFollows() {
        val s = chatShapeOf(
            listOf(
                testMessage("u", Role.USER),
                testMessage("b", Role.BOT, "hermes"),
                testMessage("t", Role.TOOL),
            ),
        )
        assertEquals(3, s.count)
        assertEquals("t", s.lastId)
        assertEquals("b", s.lastBotId)
        assertFalse(s.lastBotIsLast)
    }

    @Test fun notesAreIgnoredForLastBotIsLast() {
        val s = chatShapeOf(
            listOf(
                testMessage("u", Role.USER),
                testMessage("b", Role.BOT, "hermes"),
                testMessage("n", Role.NOTE),
            ),
        )
        assertEquals("n", s.lastId)
        assertEquals("b", s.lastBotId)
        assertTrue(s.lastBotIsLast)
    }

    @Test fun streamingFlagFollowsLastMessage() {
        val streaming = chatShapeOf(listOf(testMessage("u", Role.USER), testMessage("b", Role.BOT, "h", streaming = true)))
        assertTrue(streaming.lastStreaming)
        val done = chatShapeOf(listOf(testMessage("b", Role.BOT, "h", streaming = true), testMessage("u", Role.USER)))
        assertFalse("only the last message counts", done.lastStreaming)
    }
}
