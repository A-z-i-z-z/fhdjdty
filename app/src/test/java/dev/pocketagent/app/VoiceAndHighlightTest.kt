package dev.pocketagent.app

import dev.pocketagent.app.data.Bot
import dev.pocketagent.app.data.BotForge
import dev.pocketagent.app.data.BotPresets
import dev.pocketagent.app.data.BotVoiceStyle
import dev.pocketagent.app.data.ChatJson
import dev.pocketagent.app.data.ChatMode
import dev.pocketagent.app.data.ChatRepository
import dev.pocketagent.app.data.Conversation
import dev.pocketagent.app.data.Message
import dev.pocketagent.app.data.Role
import dev.pocketagent.app.data.SlashCommands
import dev.pocketagent.app.runtime.RuntimeState
import dev.pocketagent.app.ui.chat.ChatScrollTarget
import dev.pocketagent.app.ui.chat.SyntaxHighlight
import dev.pocketagent.app.ui.chat.chatUsageTotals
import dev.pocketagent.app.ui.chat.compactTokens
import dev.pocketagent.app.ui.chat.cycleIndex
import dev.pocketagent.app.ui.chat.exportFileName
import dev.pocketagent.app.ui.chat.ExportLabels
import dev.pocketagent.app.ui.chat.exportMarkdown
import dev.pocketagent.app.ui.chat.findMatchingMessageIds
import dev.pocketagent.app.ui.chat.inputAvailable
import dev.pocketagent.app.ui.chat.markdownToPlain
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
import java.io.File
import java.util.Locale

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

    @Test fun markdownToPlainHandlesFlattenedSingleLineCodeBlockPreviews() {
        val flattened = "```bash uname -a ``` Bu komut sistemin çekirdek sürümünü gösterir."
        val plain = markdownToPlain(flattened)
        assertTrue("preview must not vanish when starting with flattened fence: '$plain'", plain.contains("uname -a"))
        assertTrue(plain.contains("çekirdek sürümünü"))
        assertFalse(plain.contains("```"))
    }

    @Test fun ttsCleanerDropsCodeOnlyAndPreservesSnakeCaseAndContractions() {
        assertEquals("", TtsTextCleaner.cleanForSpeech("```bash\nuname -a\n```"))
        val snake = TtsTextCleaner.cleanForSpeech("Check `user_name` and **hello_world**")
        assertTrue("snake_case should preserve word break: '$snake'", snake.contains("user name"))
        assertTrue(snake.contains("hello world"))

        // Turkish words overlapping English stopwords ("can", "not") must respect Turkish appLang
        assertEquals("tr", TtsTextCleaner.detectSupertonicLang("Can geldi, not defteri ac", "tr"))
        // French contractions with apostrophes must match French words ("c'est", "d'aujourd'hui")
        assertEquals("fr", TtsTextCleaner.detectSupertonicLang("C'est l'homme d'aujourd'hui, n'est-ce pas?", "en"))
    }

    @Test fun syntaxHighlightRejectsCommentOnlyShellBlocks() {
        assertFalse(SyntaxHighlight.isRunnable("bash", "# only a comment\n   # another comment"))
        assertEquals("", SyntaxHighlight.toTerminalCommand("bash", "# only a comment"))
    }

    @Test fun forkConversationAlwaysInsertsSpaceBeforeTrimmedSuffix() {
        val repo = ChatRepository(tmp.newFolder("chat-fork"), CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), debounceMs = 10_000L)
        val conv = repo.createConversation(listOf("hermes"), "Sadece uname -a")
        // Android aapt2 trims leading whitespace in <string> resources, so runtime receives "(dal)"
        val forked = repo.forkConversation(conv.id, upToMessageId = null, titleSuffix = "(dal)")!!
        assertEquals("Sadece uname -a (dal)", forked.title)
        val forkedAgain = repo.forkConversation(forked.id, upToMessageId = null, titleSuffix = "(dal)")!!
        assertEquals("Sadece uname -a (dal)", forkedAgain.title)
    }

    @Test fun userResetBotVoiceToDefaultSurvivesRestartWhileLegacyFileMigratesOnce() {
        val dir = tmp.newFolder("chat-voice-mig")
        // 1) Legacy bots.json without voiceSid key -> gets signature voice once
        val legacyJson = """{"v":1,"bots":[${
            ChatJson.botToJson(BotPresets.rebel).apply { remove("voiceSid"); remove("voiceStyle") }
        }]}"""
        File(dir, "bots.json").writeText(legacyJson)
        val repo1 = ChatRepository(dir, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), debounceMs = 10_000L)
        assertEquals(BotPresets.rebel.voiceSid, repo1.bot("rebel")!!.voiceSid)
        assertEquals(BotPresets.rebel.voiceStyle, repo1.bot("rebel")!!.voiceStyle)

        // 2) User intentionally edits Rebel back to Follow Default (-1) + Natural (DEFAULT)
        repo1.saveBot(repo1.bot("rebel")!!.copy(voiceSid = -1, voiceStyle = BotVoiceStyle.DEFAULT))
        repo1.flush()

        // 3) Reopen repository -> user's choice (-1 / DEFAULT) must survive!
        val repo2 = ChatRepository(dir, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), debounceMs = 10_000L)
        assertEquals(-1, repo2.bot("rebel")!!.voiceSid)
        assertEquals(BotVoiceStyle.DEFAULT, repo2.bot("rebel")!!.voiceStyle)
    }

    @Test fun inputStaysUsableWhileHermesSleepsOnlyWhenWakeOnSendIsOn() {
        // Running: usable, unless the saved setup is incomplete.
        assertTrue(inputAvailable(RuntimeState.Running, setupIncomplete = false, wakeOnSend = false, runtimeInstalled = true))
        assertFalse(inputAvailable(RuntimeState.Running, setupIncomplete = true, wakeOnSend = true, runtimeInstalled = true))
        // Asleep, failed or starting: only with wake-on-send and an installed rootfs (the engine's canWake).
        for (st in listOf(RuntimeState.Stopped("x"), RuntimeState.Error("x"), RuntimeState.Starting("x"))) {
            assertTrue(inputAvailable(st, setupIncomplete = false, wakeOnSend = true, runtimeInstalled = true))
            assertFalse(inputAvailable(st, setupIncomplete = false, wakeOnSend = false, runtimeInstalled = true))
            assertFalse(inputAvailable(st, setupIncomplete = true, wakeOnSend = true, runtimeInstalled = true))
            // A failed extraction leaves an Error with no rootfs: the Start card must stay.
            assertFalse(inputAvailable(st, setupIncomplete = false, wakeOnSend = true, runtimeInstalled = false))
        }
        // A fresh install and an extraction keep the "asleep" card whatever the setting says.
        assertFalse(inputAvailable(RuntimeState.NotInstalled, setupIncomplete = false, wakeOnSend = true, runtimeInstalled = true))
        assertFalse(inputAvailable(RuntimeState.Extracting(0.5f, ""), setupIncomplete = false, wakeOnSend = true, runtimeInstalled = true))
    }

    @Test fun findInChatMatchesTextIgnoringCaseInChronologicalOrder() {
        val msgs = listOf(
            Message("a", "c", Role.USER, null, "Light the FIRE please", 1L),
            Message("b", "c", Role.NOTE, null, "fire note is not searched", 2L),
            Message("c", "c", Role.BOT, "hermes", "The fire is lit.", 3L),
            Message("d", "c", Role.TOOL, "hermes", "echo fire", 4L, toolName = "terminal"),
            Message("e", "c", Role.BOT, "hermes", "Nothing here", 5L),
        )
        assertEquals(listOf("a", "c", "d"), findMatchingMessageIds(msgs, "fire"))
        assertEquals(listOf("a", "c", "d"), findMatchingMessageIds(msgs, "  FiRe  "))
        assertTrue(findMatchingMessageIds(msgs, "").isEmpty())
        assertTrue(findMatchingMessageIds(msgs, "   ").isEmpty())
        assertTrue(findMatchingMessageIds(msgs, "volcano").isEmpty())
    }

    @Test fun matchCursorWrapsAround() {
        assertEquals(1, cycleIndex(0, 3, 1))
        assertEquals(0, cycleIndex(2, 3, 1))
        assertEquals(2, cycleIndex(0, 3, -1))
        assertEquals(0, cycleIndex(-1, 0, -1))
        assertEquals(0, cycleIndex(5, 0, 1))
    }

    @Test fun tokenCountsAreCompactAndLocalized() {
        assertEquals("0", compactTokens(-5L, Locale.US))
        assertEquals("950", compactTokens(950L, Locale.US))
        assertEquals("1k", compactTokens(1_000L, Locale.US))
        assertEquals("1.2k", compactTokens(1_234L, Locale.US))
        assertEquals("10k", compactTokens(9_999L, Locale.US))
        assertEquals("12k", compactTokens(12_345L, Locale.US))
        assertEquals("999k", compactTokens(999_499L, Locale.US))
        assertEquals("1M", compactTokens(999_500L, Locale.US))
        assertEquals("1.2M", compactTokens(1_234_567L, Locale.US))
        assertEquals("1,2k", compactTokens(1_234L, Locale("tr")))
        assertEquals("1,2M", compactTokens(1_234_567L, Locale.FRANCE))
    }

    @Test fun chatUsageTotalsCountOnlyMessagesThatCarryTokens() {
        val msgs = listOf(
            Message("u", "c", Role.USER, null, "hi", 1L),
            Message("b1", "c", Role.BOT, "hermes", "a", 2L, tokensIn = 100L, tokensOut = 20L),
            Message("t", "c", Role.TOOL, "hermes", "x", 3L),
            Message("b2", "c", Role.BOT, "hermes", "b", 4L, tokensIn = 150L, tokensOut = 30L),
            Message("b3", "c", Role.BOT, "hermes", "c", 5L),
        )
        val t = chatUsageTotals(msgs)
        assertEquals(2, t.replies)
        assertEquals(250L, t.tokensIn)
        assertEquals(50L, t.tokensOut)
        assertFalse(t.isEmpty)
        assertTrue(chatUsageTotals(emptyList()).isEmpty)
    }

    @Test fun exportBuildsMarkdownAndSafeFileNames() {
        val msgs = listOf(
            Message("u", "c", Role.USER, null, "Hello", 1L),
            Message("b", "c", Role.BOT, "hermes", "Hi there", 2L),
            Message("e", "c", Role.ERROR, null, "boom", 3L),
        )
        val md = exportMarkdown("My chat", msgs) { "Hermes" }
        assertTrue(md.startsWith("# My chat"))
        assertTrue(md.contains("**You:** Hello"))
        assertTrue(md.contains("**Hermes:** Hi there"))
        assertTrue(md.contains("**Error:** boom"))

        val fr = exportMarkdown("Chat", msgs, ExportLabels(you = "Vous", error = "Erreur")) { "Hermes" }
        assertTrue(fr.contains("**Vous:** Hello"))
        assertTrue(fr.contains("**Erreur:** boom"))
        assertFalse(fr.contains("**You:**"))

        assertEquals("My_chat.md", exportFileName("My chat"))
        assertEquals("Sohbet_Dengbêj.md", exportFileName("Sohbet: Dengbêj"))
        assertEquals("chat.md", exportFileName("   "))
        assertEquals("chat.md", exportFileName("///"))
        assertFalse(exportFileName("../../etc/passwd").contains("/"))
        assertTrue(exportFileName("x".repeat(200)).length <= 51)
    }

    @Test fun scrollTargetIsConsumedOnlyIfNotReplaced() {
        ChatScrollTarget.set("c1", "m1")
        val first = ChatScrollTarget.pending.value!!
        assertEquals("c1", first.convId)
        assertEquals("m1", first.messageId)
        ChatScrollTarget.set("c2", "m9")
        ChatScrollTarget.consume(first) // replaced meanwhile: must not clear the newer request
        assertEquals("m9", ChatScrollTarget.pending.value?.messageId)
        ChatScrollTarget.consume(ChatScrollTarget.pending.value!!)
        assertEquals(null, ChatScrollTarget.pending.value)
    }
}
