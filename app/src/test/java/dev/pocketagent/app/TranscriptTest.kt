package dev.pocketagent.app

import dev.pocketagent.app.api.ChatMessage
import dev.pocketagent.app.data.Bot
import dev.pocketagent.app.data.BotPresets
import dev.pocketagent.app.data.Conversation
import dev.pocketagent.app.data.GroupMode
import dev.pocketagent.app.data.Message
import dev.pocketagent.app.data.Role
import dev.pocketagent.app.data.Transcript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptTest {
    private val hermes = BotPresets.hermes
    private val rebel = BotPresets.rebel
    private val coder = BotPresets.coder
    private val dengbej = BotPresets.dengbej
    private val all = listOf(hermes, rebel, coder, dengbej)

    private fun conv(vararg bots: Bot, mode: GroupMode = GroupMode.ALL_IN_TURN) =
        Conversation("c1", "", bots.map { it.id }, 0, 0, groupMode = mode)

    private fun m(id: String, role: Role, text: String, bot: String? = null) = Message(id, "c1", role, bot, text, 0)

    @Test fun singleChatSendsPersonaAndOwnTurnsAsAssistant() {
        val c = conv(rebel)
        val out = Transcript.build(c, rebel, all, listOf(
            m("1", Role.USER, "hi"), m("2", Role.BOT, "yo", "rebel"), m("3", Role.USER, "again"),
        ))
        assertEquals(listOf("system", "user", "assistant", "user"), out.map { it.role })
        assertEquals(listOf("hi", "yo", "again"), out.drop(1).map { it.content })
        val sys = out[0].content
        assertTrue(sys.contains("You are Rebel"))
        assertTrue("not a group: no group framing", !sys.contains("group chat"))
        assertTrue(sys.contains("[chat reference c1/rebel]"))
    }

    @Test fun pureHermesBotHasOnlyTheReferenceInItsSystemMessage() {
        val out = Transcript.build(conv(hermes), hermes, all, listOf(m("1", Role.USER, "hi")))
        assertEquals("[chat reference c1/hermes]", out[0].content)
    }

    @Test fun groupBotsSeeEachOtherAsPrefixedUserTurns() {
        val c = conv(rebel, coder, mode = GroupMode.ALL_IN_TURN)
        val msgs = listOf(
            m("1", Role.USER, "idea: rewrite it in Rust"),
            m("2", Role.BOT, "Bold. Also unnecessary.", "rebel"),
            m("3", Role.TOOL, "ls", "coder"),
            m("4", Role.ERROR, "boom", "coder"),
            m("5", Role.NOTE, "note", null),
        )
        val forCoder = Transcript.build(c, coder, all, msgs)
        assertEquals(listOf("system", "user"), forCoder.map { it.role })
        assertEquals("idea: rewrite it in Rust\n\n[Rebel]: Bold. Also unnecessary.", forCoder[1].content)
        val sys = forCoder[0].content
        assertTrue(sys.startsWith("You are Coder in a group chat with the user and Rebel. Speak only as yourself, do not write other participants' lines, keep it conversational."))
        assertTrue(sys.contains("senior software engineer"))

        // Rebel was not asked yet to see Coder's answer: it sees the user line and its own earlier reply as assistant.
        val withCoder = msgs + m("6", Role.BOT, "Let me measure first.", "coder") + m("7", Role.USER, "so?")
        val forRebel = Transcript.build(c, rebel, all, withCoder)
        assertEquals(listOf("system", "user", "assistant", "user"), forRebel.map { it.role })
        assertEquals("[Coder]: Let me measure first.\n\nso?", forRebel[3].content)
        assertEquals("Bold. Also unnecessary.", forRebel[2].content)
    }

    @Test fun groupFramingListsEveryOtherParticipant() {
        val c = conv(hermes, rebel, coder)
        val sys = Transcript.build(c, hermes, all, listOf(m("1", Role.USER, "x")))[0].content
        assertTrue(sys.startsWith("You are Hermes in a group chat with the user and Rebel, Coder."))
    }

    @Test fun removedBotsAreStillNamedAndEmptyTextIsSkipped() {
        val c = conv(rebel, coder)
        val msgs = listOf(m("1", Role.USER, "hi"), m("2", Role.BOT, "ghost speaking", "gone"), m("3", Role.BOT, "   ", "rebel"))
        val out = Transcript.build(c, coder, all, msgs)
        assertEquals("hi\n\n[Former participant]: ghost speaking", out[1].content)
        assertEquals(2, out.size)
    }

    @Test fun trailingAssistantTurnsAreDroppedBecauseHermesTakesTheLastMessageAsTheUserMessage() {
        val out = Transcript.build(conv(rebel), rebel, all, listOf(m("1", Role.USER, "hi"), m("2", Role.BOT, "yo", "rebel")))
        assertEquals(listOf("system", "user"), out.map { it.role })
    }

    @Test fun historyIsNeverDuplicated() {
        val msgs = listOf(m("1", Role.USER, "a"), m("2", Role.BOT, "b", "rebel"), m("3", Role.USER, "c"))
        val out: List<ChatMessage> = Transcript.build(conv(rebel), rebel, all, msgs)
        assertEquals(3, out.count { it.role != "system" })
    }

    // ---- who answers ------------------------------------------------------------------------------------------------

    @Test fun mentionsAreCaseAndAccentInsensitive() {
        val ids = Transcript.mentionedBotIds("hey @DENGBEJ and @coder, what about it?", all)
        assertEquals(listOf("coder", "dengbej"), ids)   // conversation order, not mention order
        assertEquals(listOf("dengbej"), Transcript.mentionedBotIds("@dengbêj sing", all))
    }

    @Test fun mentionsMustBeWholeWordsStartingWithAt() {
        assertTrue(Transcript.mentionedBotIds("hi @Coders", all).isEmpty())
        assertTrue(Transcript.mentionedBotIds("write to coder@example.com", all).isEmpty())
        assertTrue(Transcript.mentionedBotIds("no mention of rebel here", all).isEmpty())
        assertEquals(listOf("rebel"), Transcript.mentionedBotIds("(@rebel)!", all))
    }

    @Test fun longerNamesWinAndFirstWordWorks() {
        val pro = coder.copy(id = "pro", name = "Code Pro")
        val plain = coder.copy(id = "plain", name = "Code")
        assertEquals(listOf("pro"), Transcript.mentionedBotIds("@Code Pro help", listOf(plain, pro)))
        assertEquals(listOf("plain"), Transcript.mentionedBotIds("@Code help", listOf(plain, pro)))
        assertEquals(listOf("pro"), Transcript.mentionedBotIds("@code", listOf(pro)))   // first word of the name
    }

    @Test fun selectBotsFollowsTheModeAndOnlyBotIds() {
        val g = listOf(rebel, coder, dengbej)
        val all3 = conv(rebel, coder, dengbej, mode = GroupMode.ALL_IN_TURN)
        assertEquals(listOf("rebel", "coder", "dengbej"), Transcript.selectBots(all3, g, "hello", emptyList()).map { it.id })
        assertEquals(listOf("coder"), Transcript.selectBots(all3, g, "hello", listOf("coder")).map { it.id })
        assertEquals(listOf("rebel", "dengbej"), Transcript.selectBots(all3, g, "hello", listOf("dengbej", "rebel", "unknown")).map { it.id })
        assertEquals("unknown only-ids fall back to the normal rule", 3, Transcript.selectBots(all3, g, "hello", listOf("nobody")).size)

        val men = conv(rebel, coder, dengbej, mode = GroupMode.MENTION_ONLY)
        assertEquals(listOf("rebel"), Transcript.selectBots(men, g, "no mention at all", emptyList()).map { it.id })   // first bot
        assertEquals(listOf("coder", "dengbej"), Transcript.selectBots(men, g, "@dengbej and @coder", emptyList()).map { it.id })
        assertEquals(listOf("dengbej"), Transcript.selectBots(men, g, "@coder", listOf("dengbej")).map { it.id })

        val single = conv(coder)
        assertEquals(listOf("coder"), Transcript.selectBots(single, listOf(coder), "@rebel hi", emptyList()).map { it.id })
        assertTrue(Transcript.selectBots(single, emptyList(), "x", emptyList()).isEmpty())
    }

    @Test fun autoTitleIsAboutFortyCharacters() {
        assertEquals("hello", Transcript.autoTitle("  hello  "))
        assertEquals("one two three", Transcript.autoTitle("one\n two\t three"))
        val long = Transcript.autoTitle("abcdefghij".repeat(10))
        assertEquals(41, long.length)
        assertTrue(long.endsWith("…"))
        assertEquals("exactly forty characters here, yes ok.", Transcript.autoTitle("exactly forty characters here, yes ok."))
    }
}
