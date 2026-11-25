package dev.pocketagent.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolOutputChunksTest {
    @Test fun joinEqualsKeptPrefix() {
        val text = (0 until 500).joinToString("\n") { "line $it of the output" }
        val parts = ToolOutputChunks.split(text, maxChars = 5_000, chunk = 700)
        assertEquals(text.take(5_000), parts.joinToString(""))
        assertTrue(parts.all { it.length <= 700 })
        // Without a cap everything is kept.
        assertEquals(text.take(ToolOutputChunks.MAX_CHARS), ToolOutputChunks.split(text).joinToString(""))
    }

    @Test fun splitsAfterNewlineWhenPossible() {
        val text = "a".repeat(90) + "\n" + "b".repeat(90) + "\n" + "c".repeat(90)
        val parts = ToolOutputChunks.split(text, maxChars = 10_000, chunk = 100)
        assertEquals(text, parts.joinToString(""))
        // The first piece would end inside the b run; the newline sits in its last quarter, so it ends after it.
        assertTrue("first piece ends after the newline", parts[0].endsWith("\n"))
        assertEquals(91, parts[0].length)
    }

    @Test fun neverSplitsSurrogatePair() {
        val pair = "😀"
        // 99 plain characters, then pairs: a cut at 100 would fall between the halves of the first pair.
        val text = "x".repeat(99) + pair.repeat(10)
        val parts = ToolOutputChunks.split(text, maxChars = 10_000, chunk = 100)
        assertEquals(text, parts.joinToString(""))
        for (part in parts) {
            assertFalse(part.last().isHighSurrogate())
            assertFalse(part.first().isLowSurrogate())
        }
        // The cap itself never ends inside a pair either.
        val capped = ToolOutputChunks.split("x".repeat(9) + pair, maxChars = 10, chunk = 100).joinToString("")
        assertEquals("x".repeat(9), capped)
    }

    @Test fun emptyTextGivesNoChunks() {
        assertTrue(ToolOutputChunks.split("").isEmpty())
    }

    @Test fun cutsAtMaxChars() {
        val text = "z".repeat(150_000)
        val parts = ToolOutputChunks.split(text)
        assertEquals(ToolOutputChunks.MAX_CHARS, parts.sumOf { it.length })
        assertEquals(ToolOutputChunks.MAX_CHARS / ToolOutputChunks.CHUNK_CHARS, parts.size)
    }
}
