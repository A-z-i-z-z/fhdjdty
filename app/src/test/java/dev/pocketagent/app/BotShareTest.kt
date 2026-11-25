package dev.pocketagent.app

import dev.pocketagent.app.data.Bot
import dev.pocketagent.app.data.BotPresets
import dev.pocketagent.app.data.BotShare
import dev.pocketagent.app.data.BotVoiceStyle
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BotShareTest {
    private fun bad(text: String): String {
        try { BotShare.decode(text) } catch (e: IllegalArgumentException) { return e.message ?: "" }
        fail("expected an IllegalArgumentException for: ${text.take(80)}")
        return ""
    }

    private fun doc(vararg bots: String) = """{"agiri_bots":1,"bots":[${bots.joinToString(",")}]}"""

    @Test fun roundTripKeepsEverythingButTheIdentity() {
        val a = BotPresets.dengbej.copy(model = "gemini-4-neon", effort = "low", voiceSpeed = 1.1f)
        val b = BotPresets.coder
        val out = BotShare.decode(BotShare.encode(listOf(a, b)))
        assertEquals(2, out.size)
        for ((src, got) in listOf(a to out[0], b to out[1])) {
            assertEquals("", got.id)
            assertFalse(got.builtIn)
            assertEquals(src.name, got.name); assertEquals(src.avatar, got.avatar); assertEquals(src.color, got.color)
            assertEquals(src.tagline, got.tagline); assertEquals(src.systemPrompt, got.systemPrompt)
            assertEquals(src.model, got.model); assertEquals(src.effort, got.effort)
            assertEquals(src.voiceSid, got.voiceSid); assertEquals(src.voiceStyle, got.voiceStyle); assertEquals(src.voiceSpeed, got.voiceSpeed)
        }
        assertEquals(BotVoiceStyle.STORYTELLER, out[0].voiceStyle)
    }

    @Test fun theDocumentCarriesNoIds() {
        val j = JSONObject(BotShare.encode(listOf(BotPresets.hermes, BotPresets.rebel)))
        assertEquals(1, j.getInt("agiri_bots"))
        val bots = j.getJSONArray("bots")
        for (i in 0 until bots.length()) {
            val o = bots.getJSONObject(i)
            assertFalse(o.has("id")); assertFalse(o.has("builtIn")); assertFalse(o.has("createdAt"))
            for (k in listOf("name", "avatar", "color", "tagline", "systemPrompt", "model", "effort", "voiceSid", "voiceStyle", "voiceSpeed")) assertTrue(k, o.has(k))
        }
        assertTrue(BotShare.FILE_SUFFIX == ".agiri-bots.json")
    }

    @Test fun anImportedBotNeverComesBackAsBuiltIn() {
        val got = BotShare.decode(BotShare.encode(listOf(BotPresets.hermes.copy(builtIn = true)))).single()
        assertFalse(got.builtIn)
        assertEquals("", got.id)
    }

    @Test fun missingOptionalFieldsGetDefaults() {
        val b = BotShare.decode(doc("""{"name":"  Mini  "}""")).single()
        assertEquals("Mini", b.name)
        assertEquals("NEWT", b.avatar)
        assertEquals(0xFFE8661CL, b.color)
        assertEquals("", b.tagline); assertEquals("", b.systemPrompt); assertEquals("", b.model); assertEquals("", b.effort)
        assertEquals(-1, b.voiceSid); assertEquals(BotVoiceStyle.DEFAULT, b.voiceStyle); assertEquals(0f, b.voiceSpeed)
    }

    @Test fun nonsenseValuesAreSanitisedNotTrusted() {
        val b = BotShare.decode(doc("""{"name":"X","effort":"turbo","voiceSid":42,"voiceStyle":"LOUD","voiceSpeed":9,"avatar":"../../etc"}""")).single()
        assertEquals("", b.effort)
        assertEquals(-1, b.voiceSid)
        assertEquals(BotVoiceStyle.DEFAULT, b.voiceStyle)
        assertEquals(1.5f, b.voiceSpeed)
        assertEquals("etc", b.avatar)
        assertEquals("low", BotShare.decode(doc("""{"name":"X","effort":"low"}""")).single().effort)
    }

    @Test fun limitsAreEnforced() {
        assertTrue(bad(doc("""{"name":"${"n".repeat(61)}"}""")).contains("name"))
        BotShare.decode(doc("""{"name":"${"n".repeat(60)}"}"""))   // exactly at the limit is fine
        assertTrue(bad(doc("""{"name":"a","tagline":"${"t".repeat(201)}"}""")).contains("tagline"))
        BotShare.decode(doc("""{"name":"a","tagline":"${"t".repeat(200)}"}"""))
        assertTrue(bad(doc("""{"name":"a","systemPrompt":"${"p".repeat(20_001)}"}""")).contains("instructions"))
        BotShare.decode(doc("""{"name":"a","systemPrompt":"${"p".repeat(20_000)}"}"""))
        val many = (1..51).map { """{"name":"b$it"}""" }.toTypedArray()
        assertTrue(bad(doc(*many)).contains("50"))
        assertEquals(50, BotShare.decode(doc(*many.copyOf(50).requireNoNulls())).size)
    }

    @Test fun notABotsFileIsRefusedWithAShortReason() {
        assertTrue(bad("").isNotBlank())
        assertTrue(bad("hello").isNotBlank())
        assertTrue(bad("[1,2,3]").isNotBlank())
        assertTrue(bad("""{"bots":[{"name":"a"}]}""").contains("Agiri"))
        assertTrue(bad("""{"agiri_bots":2,"bots":[{"name":"a"}]}""").contains("newer"))
        assertTrue(bad("""{"agiri_bots":1}""").isNotBlank())
        assertTrue(bad("""{"agiri_bots":1,"bots":[]}""").isNotBlank())
        assertTrue(bad(doc("""{"tagline":"no name"}""")).contains("name"))
        assertTrue(bad(doc("""{"name":"   "}""")).contains("name"))
        assertTrue(bad("""{"agiri_bots":1,"bots":["x"]}""").isNotBlank())
        assertTrue("a short reason", bad("hello").length < 100)
    }

    @Test fun aByteOrderMarkAndSurroundingWhitespaceAreTolerated() {
        val text = "﻿  \n" + BotShare.encode(listOf(BotPresets.rebel)) + "\n  "
        assertEquals("Rebel", BotShare.decode(text).single().name)
    }

    @Test fun decodedBotsGetFreshCreationTimes() {
        val out: List<Bot> = BotShare.decode(BotShare.encode(listOf(BotPresets.rebel, BotPresets.coder)))
        assertTrue(out[0].createdAt > 0)
        assertNull(out.firstOrNull { it.id.isNotEmpty() })
    }
}
