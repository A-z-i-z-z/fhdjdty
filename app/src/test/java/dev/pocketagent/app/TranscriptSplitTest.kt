package dev.pocketagent.app

import dev.pocketagent.app.data.Bot
import dev.pocketagent.app.data.BotPresets
import dev.pocketagent.app.data.Conversation
import dev.pocketagent.app.data.GroupMode
import dev.pocketagent.app.data.Message
import dev.pocketagent.app.data.Role
import dev.pocketagent.app.data.Transcript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hermes cuts one text part of a message at 65,536 characters, so the app sends longer texts as several parts of at most 60,000
 * ([Transcript.splitLong]); Hermes joins the parts of one message with a newline.
 */
class TranscriptSplitTest {
    private val max = Transcript.MAX_PART_CHARS

    @Test fun constantIsSixtyThousand() { assertEquals(60_000, max) }

    @Test fun shortTextIsOnePart() {
        assertEquals(listOf(""), Transcript.splitLong(""))
        assertEquals(listOf("hello"), Transcript.splitLong("hello"))
        val exactly = "a".repeat(max)
        assertEquals(listOf(exactly), Transcript.splitLong(exactly))
        assertEquals(2, Transcript.splitLong(exactly + "b").size)
    }

    @Test fun partsAreWithinMax() {
        val inputs = listOf(
            "a".repeat(250_000),                                  // no break at all: hard cuts
            "word ".repeat(60_000),                               // spaces only
            "line of text\n".repeat(30_000),                      // newlines only
            "x".repeat(max - 1) + "\n" + "y".repeat(max * 2),     // a newline far too early for the window of the second part
        )
        for (t in inputs) {
            val parts = Transcript.splitLong(t)
            assertTrue(parts.size >= 2)
            for (p in parts) assertTrue("part of ${p.length}", p.length in 1..max)
        }
        // a small max exercises the same rules on small data
        for (m in listOf(2, 3, 7, 10)) {
            val parts = Transcript.splitLong("ab cd\nef ghijkl mn\n\nopq rstuvwxyz ".repeat(5), m)
            for (p in parts) assertTrue("max $m part ${p.length}", p.length in 1..m)
        }
    }

    @Test fun rejoinWithNewlineIsLossless() {
        // cuts happen at newlines when the window has one: joining with "\n" gives the original text back exactly
        val text = (1..20_000).joinToString("\n") { "line number $it with some words" }
        val parts = Transcript.splitLong(text)
        assertTrue(parts.size >= 3)
        assertEquals(text, parts.joinToString("\n"))
        // the same with a small max
        val small = "abcdef\n".repeat(40)
        assertEquals(small, Transcript.splitLong(small, 25).joinToString("\n"))
    }

    @Test fun aRunExactlyMaxLongBeforeANewlineIsCutAtTheNewlineAndJoinsBack() {
        val text = "a".repeat(40) + "\n" + "b".repeat(30)
        val parts = Transcript.splitLong(text, 40)
        assertEquals(listOf("a".repeat(40), "b".repeat(30)), parts)
        assertEquals(text, parts.joinToString("\n"))
        // a newline as the very last character is never the cut (the rest would be empty)
        val trailing = "a".repeat(40) + "\n"
        val p2 = Transcript.splitLong(trailing, 40)
        for (p in p2) assertTrue(p.length in 1..40)
        assertEquals(trailing, p2.joinToString(""))
    }

    @Test fun spaceAndHardCutsOnlyChangeWhitespace() {
        fun squash(s: String) = s.filterNot { it == '\n' || it == ' ' }
        val spaced = "word ".repeat(70_000)
        assertEquals(squash(spaced), squash(Transcript.splitLong(spaced).joinToString("\n")))
        val hard = "0123456789".repeat(30_000)
        val parts = Transcript.splitLong(hard)
        assertEquals(hard, parts.joinToString(""))                    // hard cuts lose nothing at all
        assertEquals(listOf(max, max, max, max, max), parts.map { it.length })
        // a space is chosen over a hard cut and stays at the end of the left part (the join then reads "a... \nb...")
        val one = "a".repeat(40) + " " + "b".repeat(40)
        assertEquals(listOf("a".repeat(40) + " ", "b".repeat(40)), Transcript.splitLong(one, 60))
    }

    @Test fun cutsOnlyAtANewlineOrAtASpaceThatStaysAtTheEndOfThePart() {
        // Space cuts never lose a character: the parts joined WITHOUT a separator are the original text, and no word is split.
        val words = (1..30_000).joinToString(" ") { "token$it" }
        val parts = Transcript.splitLong(words)
        assertTrue(parts.size >= 3)
        assertEquals(words, parts.joinToString(""))
        for (p in parts.dropLast(1)) assertTrue("part ends with a space", p.endsWith(" "))
        for (p in parts.drop(1)) assertTrue("part starts at a word", p.startsWith("token"))
        // Hermes's join (a newline between the parts) only inserts newlines after those spaces
        assertEquals(words, parts.joinToString("\n").replace(" \n", " "))
    }

    @Test fun aSpaceInTheSecondHalfBeatsANewlineInTheFirstHalf() {
        // newline in the first half, space in the second half: the space wins (the newline is a worse place to cut)
        val text = "a".repeat(5) + "\n" + "b".repeat(30) + " " + "c".repeat(30)
        assertEquals(listOf("a".repeat(5) + "\n" + "b".repeat(30) + " ", "c".repeat(30)), Transcript.splitLong(text, 40))
        // newline in the first half and no space at all: the newline is the cut
        val nlOnly = "a".repeat(5) + "\n" + "b".repeat(60)
        assertEquals(listOf("a".repeat(5), "b".repeat(40), "b".repeat(20)), Transcript.splitLong(nlOnly, 40))
    }

    @Test fun anUnbrokenRunShorterThanMaxIsNeverSplit() {
        // 'x' run of 30 between spaces, max 40: every cut falls on a space, so the run stays whole
        val text = ("y y " + "x".repeat(30) + " ").repeat(20)
        val parts = Transcript.splitLong(text, 40)
        assertEquals(text, parts.joinToString(""))
        for (p in parts) {
            assertTrue("part of ${p.length}", p.length in 1..40)
            for (run in Regex("x+").findAll(p)) assertEquals("a run was split", 30, run.value.length)
        }
        for (p in parts.dropLast(1)) assertTrue(p.endsWith(" "))
    }

    @Test fun surrogatePairIsNeverSplit() {
        val face = "😀"                                       // one character, two UTF-16 units
        val text = face.repeat(300)
        for (m in listOf(11, 12, 13, 99, 100, 101)) {
            val parts = Transcript.splitLong(text, m)
            assertEquals("max $m", text, parts.joinToString(""))
            for (p in parts) {
                assertTrue("max $m", p.length in 1..m)
                assertFalse("max $m ends inside a pair", Character.isHighSurrogate(p.last()))
                assertFalse("max $m starts inside a pair", Character.isLowSurrogate(p.first()))
            }
        }
    }

    @Test fun rejectsAMaxThatCannotWork() {
        try { Transcript.splitLong("abc", 1); throw AssertionError("expected an IllegalArgumentException") } catch (_: IllegalArgumentException) { }
    }

    // ---- the system prompt ----------------------------------------------------------------------------------------

    private val rebel = BotPresets.rebel
    private fun conv(bot: Bot) = Conversation("c1", "", listOf(bot.id), 0, 0, groupMode = GroupMode.ALL_IN_TURN)
    private fun msg(text: String) = Message("m1", "c1", Role.USER, null, text, 0)

    @Test fun longPersonaBecomesSeveralSystemMessagesWithTheReferenceLast() {
        val persona = "A line of the persona text.\n".repeat(7_000)           // about 196,000 characters
        val bot = rebel.copy(systemPrompt = persona)
        val c = conv(bot)
        val out = Transcript.build(c, bot, listOf(bot), listOf(msg("hi")))
        val system = out.takeWhile { it.role == "system" }
        assertTrue(system.size >= 4)
        assertEquals(listOf("user"), out.drop(system.size).map { it.role })
        for (m in system) assertTrue(m.content.length in 1..max)
        // the reference line is in the LAST part only, and nothing is lost
        assertTrue(system.last().content.endsWith("[chat reference c1/rebel]"))
        assertEquals(1, system.count { it.content.contains("[chat reference") })
        assertEquals(Transcript.systemPrompt(c, bot, listOf(bot)), system.joinToString("\n") { it.content })
    }

    @Test fun shortPromptIsUnchangedByteForByte() {
        val c = conv(rebel)
        val out = Transcript.build(c, rebel, listOf(rebel), listOf(msg("hi")))
        assertEquals(1, out.count { it.role == "system" })
        assertEquals(Transcript.systemPrompt(c, rebel, listOf(rebel)), out[0].content)
        assertTrue(out[0].content.length < max)
    }
}
