package dev.pocketagent.app

import dev.pocketagent.app.data.ThoughtFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThoughtFilterTest {
    @Test fun recognisesTheChunksObservedFromGemini4OnVertex() {
        assertTrue(ThoughtFilter.isThoughtBlock("**Summarizing Newroz Meaning**\n\n I'm distilling the essence of Newroz into a single sentence for the user.\n\n"))
        assertTrue(ThoughtFilter.isThoughtBlock("**Recounting Newroz's Significance**\n\nI'm reflecting on the core meaning of Newroz, how it embodies renewal.\n\n"))
        assertTrue("two summaries in one chunk", ThoughtFilter.isThoughtBlock("**A Title**\n\nFirst thought paragraph here, long enough.\n\n**Another**\n\nSecond thought paragraph, also long.\n\n"))
    }

    @Test fun realAnswerPiecesAreNeverThoughts() {
        assertFalse(ThoughtFilter.isThoughtBlock("Ate" + "şin ba" + "şinda toplanilan o eski gecelerde"))
        assertFalse("answer that starts with a heading but streams on", ThoughtFilter.isThoughtBlock("**Result**\n\nHere is the answer text that goes on and on"))
        assertFalse("bold title only", ThoughtFilter.isThoughtBlock("**Title**\n\n"))
        assertFalse("short body", ThoughtFilter.isThoughtBlock("**Title**\n\nshort.\n\n"))
        assertFalse("no bold title", ThoughtFilter.isThoughtBlock("Plain paragraph that is long enough to pass the length test.\n\n"))
        assertFalse("title not on its own line", ThoughtFilter.isThoughtBlock("**Title** and more text on the same line, long enough.\n\n"))
        assertFalse(ThoughtFilter.isThoughtBlock(""))
        assertFalse("bold in the middle", ThoughtFilter.isThoughtBlock("Some text first **Title**\n\nbody body body body body body.\n\n"))
    }
}
