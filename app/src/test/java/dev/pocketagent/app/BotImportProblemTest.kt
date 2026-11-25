package dev.pocketagent.app

import dev.pocketagent.app.data.BotShare
import dev.pocketagent.app.ui.bots.BotImportProblem
import dev.pocketagent.app.ui.bots.botImportProblem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The bot import shows a translated reason. It finds the reason by reading the English sentence of the error from
 * [BotShare.decode]; these tests feed real decode failures through it, so a reworded sentence fails here instead of
 * silently showing English text in the Turkish and French UI.
 */
class BotImportProblemTest {
    private fun problemOf(text: String): BotImportProblem {
        val msg = try { BotShare.decode(text); null } catch (e: IllegalArgumentException) { e.message }
        return botImportProblem(msg)
    }

    private fun doc(vararg bots: String) = """{"agiri_bots":1,"bots":[${bots.joinToString(",")}]}"""

    @Test fun commonFailuresAreRecognised() {
        assertEquals(BotImportProblem.EMPTY, problemOf("   "))
        assertEquals(BotImportProblem.NOT_JSON, problemOf("hello, not json"))
        assertEquals(BotImportProblem.NOT_BOTS, problemOf("""{"something":1}"""))
        assertEquals(BotImportProblem.NOT_BOTS, problemOf("""{"agiri_bots":0,"bots":[]}"""))
        assertEquals(BotImportProblem.NEWER, problemOf("""{"agiri_bots":99,"bots":[]}"""))
        assertEquals(BotImportProblem.MALFORMED, problemOf("""{"agiri_bots":1}"""))
        assertEquals(BotImportProblem.NONE, problemOf(doc()))
        assertEquals(BotImportProblem.MALFORMED, problemOf(doc("5")))
    }

    @Test fun limitFailuresAreRecognised() {
        val many = Array(BotShare.MAX_BOTS + 1) { """{"name":"b$it"}""" }
        assertEquals(BotImportProblem.TOO_MANY, problemOf(doc(*many)))
        assertEquals(BotImportProblem.FIELD, problemOf(doc("""{"name":""}""")))
        assertEquals(BotImportProblem.FIELD, problemOf(doc("""{"name":"${"n".repeat(BotShare.MAX_NAME + 1)}"}""")))
        assertEquals(BotImportProblem.FIELD, problemOf(doc("""{"name":"a","tagline":"${"t".repeat(BotShare.MAX_TAGLINE + 1)}"}""")))
        assertEquals(BotImportProblem.FIELD, problemOf(doc("""{"name":"a","systemPrompt":"${"p".repeat(BotShare.MAX_PROMPT + 1)}"}""")))
    }

    @Test fun unknownMessagesFallBackToOther() {
        assertEquals(BotImportProblem.OTHER, botImportProblem("something nobody planned for"))
        assertEquals(BotImportProblem.OTHER, botImportProblem(null))
    }
}
