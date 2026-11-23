package dev.pocketagent.app

import dev.pocketagent.app.data.Bot
import dev.pocketagent.app.data.BotForge
import dev.pocketagent.app.data.BotVoiceStyle
import dev.pocketagent.app.data.ChatJson
import dev.pocketagent.app.data.ChatMode
import dev.pocketagent.app.data.ChatRepository
import dev.pocketagent.app.data.Conversation
import dev.pocketagent.app.data.Message
import dev.pocketagent.app.data.Role
import dev.pocketagent.app.data.SlashCommands
import dev.pocketagent.app.ui.chat.SyntaxHighlight
import dev.pocketagent.app.voice.TtsTextCleaner
import dev.pocketagent.app.voice.styleModulation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VoiceAndHighlightTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun ttsCleanerStripsMarkdownCodeAndUrls() {
        val md = """
            # Hello **Agirî**!
            Visit [docs](https://example.com/a) or https://example.org/raw.
            ```python
            print("ignored")
            ```
            - Item `one` and *two*
        """.trimIndent()
        val clean = TtsTextCleaner.cleanForSpeech(md)
        assertFalse(clean.contains("print"))
        assertFalse(clean.contains("https://"))
        assertFalse(clean.contains("**"))
        assertTrue(clean.contains("Hello Agirî"))
        assertTrue(clean.contains("docs"))
        assertTrue(clean.contains("Item one and two"))
        assertEquals("", TtsTextCleaner.cleanForSpeech("   "))
    }

    @Test fun ttsLanguageDetectionCoversEnFrTrAndKurdishNames() {
        assertEquals("tr", TtsTextCleaner.detectSupertonicLang("Merhaba, bugün hava çok güzel ve ışık harika."))
        assertEquals("tr", TtsTextCleaner.detectSupertonicLang("Merhaba! Ben Agirî. Pêt ve Dengbêj burada."))
        assertEquals("fr", TtsTextCleaner.detectSupertonicLang("Bonjour, comment allez-vous aujourd'hui ? C'est très bien."))
        assertEquals("fr", TtsTextCleaner.detectSupertonicLang("Bonjour ! Je suis Agirî. Le feu de Newroz brûle."))
        assertEquals("en", TtsTextCleaner.detectSupertonicLang("Hello from the Zagros mountains, the hearth is blazing."))
        assertEquals("en", TtsTextCleaner.detectSupertonicLang("Hello! I am Agirî. Dengbêj and Pêt are sitting by the hearth."))
    }

    @Test fun ttsSentenceSplitterRespectsBoundAndEmpty() {
        assertTrue(TtsTextCleaner.splitSentences("   ").isEmpty())
        val long = "First sentence here. Second sentence is also present! Third question? " +
            "Fourth very long clause, with a comma in the middle, that keeps going past sixty characters easily."
        val chunks = TtsTextCleaner.splitSentences(long, maxChunkChars = 55)
        assertTrue(chunks.size >= 3)
        assertTrue(chunks.all { it.length <= 60 })
    }

    @Test fun voiceStyleModulationDifferentiatesMoods() {
        val calm = styleModulation(BotVoiceStyle.CALM)
        val excited = styleModulation(BotVoiceStyle.EXCITED)
        val serious = styleModulation(BotVoiceStyle.SERIOUS)
        val story = styleModulation(BotVoiceStyle.STORYTELLER)
        assertTrue(excited.speedMultiplier > calm.speedMultiplier)
        assertTrue(excited.systemPitch > serious.systemPitch)
        assertTrue(story.silenceSec > excited.silenceSec)
    }

    @Test fun syntaxHighlightDetectsRunnableCodeAndFormatsCommands() {
        assertTrue(SyntaxHighlight.isRunnable("bash", "$ uname -a\n# comment\necho ok"))
        assertTrue(SyntaxHighlight.isRunnable("python", "print('hi')"))
        assertTrue(SyntaxHighlight.isRunnable("", "hermes status"))
        assertFalse(SyntaxHighlight.isRunnable("kotlin", "fun main() {}"))
        assertFalse(SyntaxHighlight.isRunnable("bash", "   "))

        assertEquals("uname -a\necho ok", SyntaxHighlight.toTerminalCommand("bash", "$ uname -a\n# ignore\necho ok"))
        assertEquals("python3 -c 'print('\"'\"'hi'\"'\"')'", SyntaxHighlight.toTerminalCommand("py", "print('hi')"))
    }

    @Test fun botForgeParsesOptionalVoiceAndMood() {
        val reply = """
            Meet Roza!
            ```pocket-bot
            {"name":"Roza","tagline":"Joyful guide","avatar":"FOX","color":"#FEBD11","voice":"F2","voiceStyle":"happy","systemPrompt":"Be cheerful."}
            ```
        """.trimIndent()
        val res = BotForge.extract(reply)
        assertEquals(1, res.bots.size)
        assertEquals(1, res.bots[0].voiceSid)
        assertEquals(BotVoiceStyle.HAPPY, res.bots[0].voiceStyle)
        assertEquals("Meet Roza!", res.cleanedText)
    }

    @Test fun chatJsonRoundTripsOverridesAndBotVoiceWithBackwardsCompat() {
        val bot = Bot(
            id = "b1", name = "Test", avatar = "OWL", color = 0xFF112233L,
            tagline = "t", systemPrompt = "p", voiceSid = 7, voiceStyle = BotVoiceStyle.STORYTELLER, voiceSpeed = 0.95f,
        )
        val decodedBot = ChatJson.botFromJson(ChatJson.botToJson(bot))!!
        assertEquals(7, decodedBot.voiceSid)
        assertEquals(BotVoiceStyle.STORYTELLER, decodedBot.voiceStyle)
        assertEquals(0.95f, decodedBot.voiceSpeed, 0.001f)

        val conv = Conversation(
            id = "c1", title = "Demo", botIds = listOf("hermes"), createdAt = 1L, updatedAt = 2L,
            mode = ChatMode.PLAN, modelOverride = "google/gemini-4-hydrogen", effortOverride = "low",
        )
        val decodedConv = ChatJson.convFromJson(ChatJson.convToJson(conv))!!
        assertEquals("google/gemini-4-hydrogen", decodedConv.modelOverride)
        assertEquals("low", decodedConv.effortOverride)
        assertEquals(ChatMode.PLAN, decodedConv.mode)
    }

    @Test fun repositoryForkConversationBranchesCleanlyAtTargetMessage() {
        val repo = ChatRepository(tmp.newFolder("chat"), CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), debounceMs = 10_000L)
        val conv = repo.createConversation(listOf("hermes"), "Origin")
        repo.setMode(conv.id, ChatMode.ULTRACODE)
        repo.setOverrides(conv.id, "google/gemini-4-hydrogen", "low")
        val m1 = Message("m1", conv.id, Role.USER, null, "First", 10L)
        val m2 = Message("m2", conv.id, Role.BOT, "hermes", "Reply 1", 20L)
        val m3 = Message("m3", conv.id, Role.USER, null, "Second", 30L)
        repo.addMessage(m1); repo.addMessage(m2); repo.addMessage(m3)

        val forked = repo.forkConversation(conv.id, upToMessageId = "m2", titleSuffix = " (fork)")
        assertNotNull(forked)
        assertNotEquals(conv.id, forked!!.id)
        assertEquals("Origin (fork)", forked.title)
        assertEquals(ChatMode.ULTRACODE, forked.mode)
        assertEquals("google/gemini-4-hydrogen", forked.modelOverride)
        assertEquals("low", forked.effortOverride)

        val forkedMsgs = repo.messagesSnapshot(forked.id)
        assertEquals(2, forkedMsgs.size)
        assertEquals(listOf("First", "Reply 1"), forkedMsgs.map { it.text })
        assertTrue(forkedMsgs.all { it.convId == forked.id && it.id != "m1" && it.id != "m2" })
        assertEquals(3, repo.messagesSnapshot(conv.id).size)
    }

    @Test fun secretAgirSlashCommandParses() {
        assertEquals("agir", SlashCommands.parse("/agir")?.name)
        assertEquals("fire", SlashCommands.parse("/fire")?.name)
    }
}
