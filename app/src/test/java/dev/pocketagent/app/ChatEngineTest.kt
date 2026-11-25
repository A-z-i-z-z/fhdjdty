package dev.pocketagent.app

import dev.pocketagent.app.api.ChatEvent
import dev.pocketagent.app.api.ChatOutcome
import dev.pocketagent.app.api.ChatRequest
import dev.pocketagent.app.api.HttpResult
import dev.pocketagent.app.api.StreamHandle
import dev.pocketagent.app.data.BotPresets
import dev.pocketagent.app.data.BudgetMode
import dev.pocketagent.app.data.ChatBackend
import dev.pocketagent.app.data.ChatEngine
import dev.pocketagent.app.data.ChatRepository
import dev.pocketagent.app.data.EngineEvent
import dev.pocketagent.app.data.GroupMode
import dev.pocketagent.app.data.ModelIds
import dev.pocketagent.app.data.Role
import dev.pocketagent.app.data.UsageBudget
import dev.pocketagent.app.data.UsageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import dev.pocketagent.app.text.TestTexts
import org.junit.Before

class ChatEngineTest {
    @Before fun installTexts() { TestTexts.installEnglish() }
    @get:Rule val tmp = TemporaryFolder()

    /** Scripted transport: [script] gets the request and returns the events to emit plus how the stream ends. */
    private class Fake(val script: (ChatRequest, StreamHandle) -> Pair<List<ChatEvent>, ChatOutcome>) : ChatBackend {
        val requests = CopyOnWriteArrayList<ChatRequest>()
        override fun stream(request: ChatRequest, handle: StreamHandle, onEvent: (ChatEvent) -> Unit): ChatOutcome {
            requests += request
            val (events, outcome) = script(request, handle)
            events.forEach(onEvent)
            return outcome
        }
    }

    private fun setup(backend: Fake, provider: String = "vertex", ready: Boolean = true): Triple<ChatRepository, ChatEngine, File> {
        val dir = File(tmp.root, "chat")
        val repo = ChatRepository(dir, CoroutineScope(Dispatchers.IO), 20L)
        runBlocking { repo.loaded.first { it } }
        val engine = ChatEngine(repo, backend, { _, _, _ -> HttpResult(200, "{}", 0) }, {}, {}, { provider }, { ready }, CoroutineScope(Dispatchers.IO))
        return Triple(repo, engine, dir)
    }

    private fun idle(engine: ChatEngine, convId: String) {
        val end = System.currentTimeMillis() + 5000
        while (engine.busy.value.contains(convId)) {
            if (System.currentTimeMillis() > end) throw AssertionError("engine still busy")
            Thread.sleep(5)
        }
    }

    private fun speakerOf(r: ChatRequest): String = Regex("You are ([^ :]+)").find(r.messages.first().content)!!.groupValues[1]

    private val ok: (List<ChatEvent>) -> Pair<List<ChatEvent>, ChatOutcome> = { it + ChatEvent.Finish("stop", null) + ChatEvent.Done to ChatOutcome.Completed }

    @Test fun singleChatStreamsIntoOneBotMessage() {
        val fake = Fake { _, _ -> ok(listOf(ChatEvent.Delta("Hel"), ChatEvent.Delta("lo"))) }
        val (repo, engine, _) = setup(fake)
        val c = repo.createConversation(listOf("hermes"))
        engine.send(c.id, "  hi there  ")
        assertTrue("busy is set synchronously", engine.busy.value.contains(c.id))
        idle(engine, c.id)

        val msgs = repo.messagesSnapshot(c.id)
        assertEquals(listOf(Role.USER, Role.BOT), msgs.map { it.role })
        assertEquals("hi there", msgs[0].text)
        assertEquals("Hello", msgs[1].text)
        assertEquals("hermes", msgs[1].botId)
        assertFalse(msgs[1].streaming)
        assertNotNull(msgs[1].durationMs)
        assertEquals("hi there", repo.conversation(c.id)!!.title)
        assertEquals("Hello", repo.conversation(c.id)!!.lastPreview)
        assertTrue(engine.speaking.value.isEmpty())

        val req = fake.requests.single()
        assertEquals("pure Hermes: virtual model, no provider", "hermes-agent", req.toJson().getString("model"))
        assertFalse(req.toJson().has("provider"))
        assertFalse(req.toJson().has("model_options"))
        assertNull("history is sent in the body, never through the session header", req.sessionId)
        assertEquals(listOf("system", "user"), req.messages.map { it.role })
    }

    @Test fun existingTitleIsKept() {
        val fake = Fake { _, _ -> ok(listOf(ChatEvent.Delta("x"))) }
        val (repo, engine, _) = setup(fake)
        val c = repo.createConversation(listOf("hermes"), "My title")
        engine.send(c.id, "something else")
        idle(engine, c.id)
        assertEquals("My title", repo.conversation(c.id)!!.title)
    }

    @Test fun botWithOwnModelAndEffortGoesOutWithProvider() {
        val fake = Fake { _, _ -> ok(listOf(ChatEvent.Delta("ok"))) }
        val (repo, engine, _) = setup(fake, provider = "vertex")
        repo.saveBot(BotPresets.coder.copy(model = "gemini-4-neon", effort = "low"))
        val c = repo.createConversation(listOf("coder"))
        engine.send(c.id, "go")
        idle(engine, c.id)
        val j = fake.requests.single().toJson()
        assertEquals("google/gemini-4-neon", j.getString("model"))
        assertEquals("vertex", j.getString("provider"))
        assertEquals("low", j.getJSONObject("model_options").getString("reasoning_effort"))
        assertTrue(j.getBoolean("stream"))
    }

    @Test fun anOverrideFromAnotherProviderIsNotSentAndExplainedOnce() {
        val fake = Fake { _, _ -> ok(listOf(ChatEvent.Delta("ok"))) }
        val (repo, engine, _) = setup(fake, provider = "anthropic")
        repo.saveBot(BotPresets.coder.copy(model = "gemini-4-hydrogen"))
        val c = repo.createConversation(listOf("coder"))
        engine.send(c.id, "one"); idle(engine, c.id)
        engine.send(c.id, "two"); idle(engine, c.id)
        for (r in fake.requests) {
            assertEquals("", r.model)
            assertNull(r.provider)
            assertFalse(r.toJson().has("provider"))
        }
        val notes = repo.messagesSnapshot(c.id).filter { it.role == dev.pocketagent.app.data.Role.NOTE && it.text.contains("another provider") }
        assertEquals(1, notes.size)
    }

    @Test fun modelIdsFollowTheProvider() {
        assertEquals("google/gemini-4-neon", ModelIds.normalize("vertex", "gemini-4-neon"))
        assertEquals("google/gemini-4-neon", ModelIds.normalize("vertex", "google/gemini-4-neon"))
        assertEquals("gemini-4-neon", ModelIds.normalize("gemini", "google/gemini-4-neon"))
        assertEquals("x", ModelIds.normalize("other", "x"))
        assertEquals("", ModelIds.normalize("vertex", "  "))
    }

    @Test fun toolRunsAreOneMessageUpdatedInPlaceAndTextAfterThemIsANewBubble() {
        val fake = Fake { _, _ ->
            ok(listOf(
                ChatEvent.Delta("Let me look."),
                ChatEvent.Tool("terminal", "ls -la", "running", "call_1"),
                ChatEvent.Tool("terminal", "", "completed", "call_1"),
                ChatEvent.Delta("\n\nThree files."),
                ChatEvent.Reasoning("hmm"),
            ))
        }
        val (repo, engine, _) = setup(fake)
        val c = repo.createConversation(listOf("hermes"))
        engine.send(c.id, "list files")
        idle(engine, c.id)
        val msgs = repo.messagesSnapshot(c.id)
        assertEquals(listOf(Role.USER, Role.BOT, Role.TOOL, Role.BOT), msgs.map { it.role })
        assertEquals("Let me look.", msgs[1].text)
        assertEquals("terminal", msgs[2].toolName)
        assertEquals("ls -la", msgs[2].text)
        assertEquals("completed", msgs[2].toolStatus)
        assertEquals("Three files.", msgs[3].text)
        assertEquals("hmm", msgs[3].reasoning)
        assertTrue(msgs.none { it.streaming })
    }

    @Test fun httpErrorBecomesAnErrorMessageWithStatusAndBody() {
        val fake = Fake { _, _ -> emptyList<ChatEvent>() to ChatOutcome.HttpError(401, "{\"error\":\"bad key\"}") }
        val (repo, engine, _) = setup(fake)
        val c = repo.createConversation(listOf("hermes"))
        engine.send(c.id, "hello")
        idle(engine, c.id)
        val msgs = repo.messagesSnapshot(c.id)
        assertEquals(listOf(Role.USER, Role.ERROR), msgs.map { it.role })
        assertEquals("HTTP 401: {\"error\":\"bad key\"}", msgs[1].text)
        assertEquals("hermes", msgs[1].botId)
    }

    @Test fun errorsAreRedactedWithTheSharedRedactor() {
        dev.pocketagent.app.diag.Diag.redactor.addSecret("test-key", "TOPSECRETVALUE123")
        try {
            val fake = Fake { _, _ -> emptyList<ChatEvent>() to ChatOutcome.TransportError("failed with TOPSECRETVALUE123 in it") }
            val (repo, engine, _) = setup(fake)
            val c = repo.createConversation(listOf("hermes"))
            engine.send(c.id, "hello")
            idle(engine, c.id)
            val err = repo.messagesSnapshot(c.id).last()
            assertEquals(Role.ERROR, err.role)
            assertFalse(err.text.contains("TOPSECRETVALUE123"))
        } finally { dev.pocketagent.app.diag.Diag.redactor.clear() }
    }

    @Test fun notRunningFailsFastWithoutCallingTheApi() {
        val fake = Fake { _, _ -> ok(listOf(ChatEvent.Delta("never"))) }
        val (repo, engine, _) = setup(fake, ready = false)
        val c = repo.createConversation(listOf("hermes"))
        assertFalse("not running: ignored, the caller keeps the text", engine.send(c.id, "hello"))
        assertFalse(engine.regenerateLast(c.id))
        assertFalse(engine.editAndResend(c.id, "x", "y"))
        assertFalse(engine.busy.value.contains(c.id))
        assertTrue(fake.requests.isEmpty())
        assertTrue("nothing was added to the chat", repo.messagesSnapshot(c.id).isEmpty())
    }

    @Test fun inStreamErrorAndFinishAreBothReportedAndTheRoundStops() {
        val fake = Fake { _, _ ->
            listOf<ChatEvent>(ChatEvent.StreamError("boom", "{\"error\":{\"message\":\"boom\"}}"), ChatEvent.Finish("error", "x"), ChatEvent.Done) to ChatOutcome.Completed
        }
        val (repo, engine, _) = setup(fake)
        repo.saveBot(BotPresets.rebel)
        val c = repo.createConversation(listOf("hermes", "rebel"))
        engine.send(c.id, "hello")
        idle(engine, c.id)
        assertEquals("second bot is not asked after a failure", 1, fake.requests.size)
        val errors = repo.messagesSnapshot(c.id).filter { it.role == Role.ERROR }
        assertEquals(1, errors.size)   // the finish_reason after an error does not add a second error
        assertTrue(errors[0].text.contains("boom"))
    }

    @Test fun groupAllInTurnBotsAnswerInOrderAndSeeEachOther() {
        val fake = Fake { r, _ -> ok(listOf(ChatEvent.Delta("reply from ${speakerOf(r)}"))) }
        val (repo, engine, _) = setup(fake)
        val c = repo.createConversation(listOf("rebel", "coder"))
        engine.send(c.id, "what now?")
        idle(engine, c.id)
        assertEquals(listOf("Rebel", "Coder"), fake.requests.map { speakerOf(it) })
        val second = fake.requests[1]
        assertEquals(listOf("system", "user"), second.messages.map { it.role })
        assertEquals("what now?\n\n[Rebel]: reply from Rebel", second.messages[1].content)
        val msgs = repo.messagesSnapshot(c.id)
        assertEquals(listOf(Role.USER, Role.BOT, Role.BOT), msgs.map { it.role })
        assertEquals(listOf(null, "rebel", "coder"), msgs.map { it.botId })
    }

    @Test fun groupMentionOnlyAsksOnlyMentionedBotsElseTheFirst() {
        val fake = Fake { r, _ -> ok(listOf(ChatEvent.Delta("hi from ${speakerOf(r)}"))) }
        val (repo, engine, _) = setup(fake)
        val c = repo.createConversation(listOf("rebel", "coder", "dengbej"))
        repo.setGroupMode(c.id, GroupMode.MENTION_ONLY)
        engine.send(c.id, "@coder fix this")
        idle(engine, c.id)
        assertEquals(listOf("Coder"), fake.requests.map { speakerOf(it) })
        engine.send(c.id, "anyone?")
        idle(engine, c.id)
        assertEquals(listOf("Coder", "Rebel"), fake.requests.map { speakerOf(it) })
        engine.send(c.id, "just you", onlyBotIds = listOf("dengbej"))
        idle(engine, c.id)
        assertEquals(3, fake.requests.size)
        assertEquals("dengbej", repo.messagesSnapshot(c.id).last().botId)
    }

    @Test fun regenerateLastReplacesTheAnswers() {
        var n = 0
        val fake = Fake { _, _ -> ok(listOf(ChatEvent.Delta("answer ${++n}"))) }
        val (repo, engine, _) = setup(fake)
        val c = repo.createConversation(listOf("hermes"))
        engine.send(c.id, "question")
        idle(engine, c.id)
        engine.regenerateLast(c.id)
        idle(engine, c.id)
        val msgs = repo.messagesSnapshot(c.id)
        assertEquals(listOf("question", "answer 2"), msgs.map { it.text })
        // The regenerated request must not contain the discarded answer.
        assertEquals(listOf("system", "user"), fake.requests[1].messages.map { it.role })
    }

    @Test fun editAndResendTruncatesAndRetitles() {
        var n = 0
        val fake = Fake { _, _ -> ok(listOf(ChatEvent.Delta("answer ${++n}"))) }
        val (repo, engine, _) = setup(fake)
        val c = repo.createConversation(listOf("hermes"))
        engine.send(c.id, "first question")
        idle(engine, c.id)
        engine.send(c.id, "second question")
        idle(engine, c.id)
        val firstId = repo.messagesSnapshot(c.id)[0].id
        engine.editAndResend(c.id, firstId, "edited first question")
        idle(engine, c.id)
        val msgs = repo.messagesSnapshot(c.id)
        assertEquals(listOf("edited first question", "answer 3"), msgs.map { it.text })
        assertEquals("edited first question", repo.conversation(c.id)!!.title)
        assertEquals(listOf("system", "user"), fake.requests.last().messages.map { it.role })
    }

    @Test fun editKeepsARenamedTitle() {
        val fake = Fake { _, _ -> ok(listOf(ChatEvent.Delta("a"))) }
        val (repo, engine, _) = setup(fake)
        val c = repo.createConversation(listOf("hermes"))
        engine.send(c.id, "original")
        idle(engine, c.id)
        repo.renameConversation(c.id, "Custom")
        engine.editAndResend(c.id, repo.messagesSnapshot(c.id)[0].id, "changed")
        idle(engine, c.id)
        assertEquals("Custom", repo.conversation(c.id)!!.title)
    }

    @Test fun stopCancelsTheStreamAndKeepsPartialText() {
        val started = java.util.concurrent.CountDownLatch(1)
        val backend = object : ChatBackend {
            override fun stream(request: ChatRequest, handle: StreamHandle, onEvent: (ChatEvent) -> Unit): ChatOutcome {
                onEvent(ChatEvent.Delta("partial"))
                started.countDown()
                while (!handle.cancelled) Thread.sleep(5)
                return ChatOutcome.Cancelled
            }
        }
        val dir = File(tmp.root, "chat")
        val repo = ChatRepository(dir, CoroutineScope(Dispatchers.IO), 20L)
        runBlocking { repo.loaded.first { it } }
        val engine = ChatEngine(repo, backend, { _, _, _ -> HttpResult(200, "{}", 0) }, {}, {}, { "vertex" }, { true }, CoroutineScope(Dispatchers.IO))
        val c = repo.createConversation(listOf("hermes"))
        engine.send(c.id, "long task")
        assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals("hermes", engine.speaking.value[c.id])
        engine.stop(c.id)
        idle(engine, c.id)
        val msgs = repo.messagesSnapshot(c.id)
        assertEquals(listOf(Role.USER, Role.BOT), msgs.map { it.role })
        assertEquals("partial", msgs[1].text)
        assertFalse(msgs[1].streaming)
    }

    @Test fun aSecondSendWhileBusyIsIgnoredButOtherConversationsRunConcurrently() {
        val gate = java.util.concurrent.CountDownLatch(1)
        val entered = java.util.concurrent.atomic.AtomicInteger(0)
        val backend = ChatBackend { _, _, onEvent ->
            entered.incrementAndGet()
            gate.await(5, java.util.concurrent.TimeUnit.SECONDS)
            onEvent(ChatEvent.Delta("done")); onEvent(ChatEvent.Done)
            ChatOutcome.Completed
        }
        val repo = ChatRepository(File(tmp.root, "chat"), CoroutineScope(Dispatchers.IO), 20L)
        runBlocking { repo.loaded.first { it } }
        val engine = ChatEngine(repo, backend, { _, _, _ -> HttpResult(200, "{}", 0) }, {}, {}, { "vertex" }, { true }, CoroutineScope(Dispatchers.IO))
        val a = repo.createConversation(listOf("hermes"))
        val b = repo.createConversation(listOf("hermes"))
        engine.send(a.id, "one")
        engine.send(a.id, "ignored while busy")
        engine.send(b.id, "other chat")
        val end = System.currentTimeMillis() + 5000
        while (entered.get() < 2 && System.currentTimeMillis() < end) Thread.sleep(5)
        assertEquals("both conversations are streaming at the same time", 2, entered.get())
        assertEquals(setOf(a.id, b.id), engine.busy.value)
        gate.countDown()
        idle(engine, a.id); idle(engine, b.id)
        assertEquals(listOf("one", "done"), repo.messagesSnapshot(a.id).map { it.text })
    }

    @Test fun approvalRequestsAreExposedAndAnswered() {
        val answered = CopyOnWriteArrayList<String>()
        val release = java.util.concurrent.CountDownLatch(1)
        val backend = ChatBackend { _, _, onEvent ->
            onEvent(ChatEvent.Approval("chatcmpl-1", "rm -rf build", "recursive delete", listOf("once", "deny"), "", "{}"))
            release.await(5, java.util.concurrent.TimeUnit.SECONDS)
            onEvent(ChatEvent.Delta("finished")); onEvent(ChatEvent.Done)
            ChatOutcome.Completed
        }
        val repo = ChatRepository(File(tmp.root, "chat"), CoroutineScope(Dispatchers.IO), 20L)
        runBlocking { repo.loaded.first { it } }
        val engine = ChatEngine(repo, backend, { run, choice, _ -> answered += "$run:$choice"; HttpResult(200, "{}", 0) }, {}, {}, { "vertex" }, { true }, CoroutineScope(Dispatchers.IO))
        val c = repo.createConversation(listOf("hermes"))
        engine.send(c.id, "clean up")
        val end = System.currentTimeMillis() + 5000
        while (engine.approvals.value[c.id] == null && System.currentTimeMillis() < end) Thread.sleep(5)
        val p = engine.approvals.value[c.id]!!
        assertEquals("rm -rf build", p.command)
        assertEquals(listOf("once", "deny"), p.choices)
        engine.resolveApproval(c.id, "deny")
        assertNull(engine.approvals.value[c.id])
        release.countDown()
        idle(engine, c.id)
        val e2 = System.currentTimeMillis() + 3000
        while (answered.isEmpty() && System.currentTimeMillis() < e2) Thread.sleep(5)
        assertEquals(listOf("chatcmpl-1:deny"), answered.toList())
        assertTrue(repo.messagesSnapshot(c.id).any { it.role == Role.NOTE && it.text.contains("rm -rf build") })
    }

    @Test fun anEmptyReplyIsExplained() {
        val fake = Fake { _, _ -> ok(emptyList()) }
        val (repo, engine, _) = setup(fake)
        val c = repo.createConversation(listOf("hermes"))
        engine.send(c.id, "hello")
        idle(engine, c.id)
        assertEquals(Role.NOTE, repo.messagesSnapshot(c.id).last().role)
    }

    @Test fun conversationWithOnlyDeletedBotsReportsIt() {
        val fake = Fake { _, _ -> ok(listOf(ChatEvent.Delta("x"))) }
        val (repo, engine, _) = setup(fake)
        val c = repo.createConversation(listOf("ghost"))
        engine.send(c.id, "hello")
        idle(engine, c.id)
        assertTrue(fake.requests.isEmpty())
        assertEquals(Role.ERROR, repo.messagesSnapshot(c.id).last().role)
    }

    @Test fun sendReportsWhetherItWasAccepted() {
        val gate = java.util.concurrent.CountDownLatch(1)
        val backend = ChatBackend { _, _, onEvent -> gate.await(5, java.util.concurrent.TimeUnit.SECONDS); onEvent(ChatEvent.Delta("x")); onEvent(ChatEvent.Done); ChatOutcome.Completed }
        val repo = ChatRepository(File(tmp.root, "chat"), CoroutineScope(Dispatchers.IO), 20L)
        runBlocking { repo.loaded.first { it } }
        val engine = ChatEngine(repo, backend, { _, _, _ -> HttpResult(200, "{}", 0) }, {}, {}, { "vertex" }, { true }, CoroutineScope(Dispatchers.IO))
        val c = repo.createConversation(listOf("hermes"))
        assertFalse("empty text", engine.send(c.id, "   "))
        assertTrue(engine.send(c.id, "one"))
        assertFalse("busy", engine.send(c.id, "two"))
        assertFalse("busy", engine.regenerateLast(c.id))
        gate.countDown()
        idle(engine, c.id)
        assertEquals(listOf("one", "x"), repo.messagesSnapshot(c.id).map { it.text })
    }

    @Test fun aFailedApprovalAnswerPutsTheCardBackAndSaysSo() {
        val release = java.util.concurrent.CountDownLatch(1)
        val backend = ChatBackend { _, _, onEvent ->
            onEvent(ChatEvent.Approval("", "rm -rf x", "d", listOf("once", "deny"), "", "{}"))
            release.await(5, java.util.concurrent.TimeUnit.SECONDS)
            onEvent(ChatEvent.Done)
            ChatOutcome.Completed
        }
        val repo = ChatRepository(File(tmp.root, "chat"), CoroutineScope(Dispatchers.IO), 20L)
        runBlocking { repo.loaded.first { it } }
        val engine = ChatEngine(repo, backend, { _, _, _ -> HttpResult(-1, "", 0, "invalid run id") }, {}, {}, { "vertex" }, { true }, CoroutineScope(Dispatchers.IO))
        val c = repo.createConversation(listOf("hermes"))
        engine.send(c.id, "go")
        val end = System.currentTimeMillis() + 5000
        while (engine.approvals.value[c.id] == null && System.currentTimeMillis() < end) Thread.sleep(5)
        engine.resolveApproval(c.id, "once")
        val e2 = System.currentTimeMillis() + 5000
        while (repo.messagesSnapshot(c.id).none { it.role == Role.ERROR } && System.currentTimeMillis() < e2) Thread.sleep(5)
        assertNotNull("card is back", engine.approvals.value[c.id])
        assertTrue(repo.messagesSnapshot(c.id).any { it.role == Role.ERROR && it.text.contains("invalid run id") })
        release.countDown()
        idle(engine, c.id)
        assertNull("card is gone once the turn is over", engine.approvals.value[c.id])
    }

    private val thought1 = "**Recounting Newroz's Significance**\n\nI'm reflecting on the core meaning of Newroz and how it embodies renewal.\n\n"
    private val thought2 = "**Weaving the Narrative**\n\nMy thoughts are focused on the ancient legend of Kawa the blacksmith.\n\n"

    @Test fun thoughtSummariesAtTheStartGoToReasoningNotToTheAnswer() {
        val fake = Fake { _, _ -> ok(listOf(ChatEvent.Delta(thought1), ChatEvent.Delta(thought2), ChatEvent.Delta("Newroz, bahar"), ChatEvent.Delta("in gelisi."))) }
        val (repo, engine, _) = setup(fake)
        val c = repo.createConversation(listOf("hermes"))
        engine.send(c.id, "Newroz nedir?")
        idle(engine, c.id)
        val bot = repo.messagesSnapshot(c.id).single { it.role == Role.BOT }
        assertEquals("Newroz, baharin gelisi.", bot.text)
        assertTrue(bot.reasoning.contains("Recounting Newroz") && bot.reasoning.contains("Weaving the Narrative"))
    }

    @Test fun aThoughtShapedChunkAfterRealTextStaysText() {
        val fake = Fake { _, _ -> ok(listOf(ChatEvent.Delta("Intro. "), ChatEvent.Delta(thought1))) }
        val (repo, engine, _) = setup(fake)
        val c = repo.createConversation(listOf("hermes"))
        engine.send(c.id, "hi")
        idle(engine, c.id)
        val bot = repo.messagesSnapshot(c.id).single { it.role == Role.BOT }
        assertTrue(bot.text.startsWith("Intro. **Recounting"))
        assertEquals("", bot.reasoning)
    }

    @Test fun ifTheWholeBubbleLooksLikeThoughtsItIsKeptAsTheAnswer() {
        val fake = Fake { _, _ -> ok(listOf(ChatEvent.Delta(thought1))) }
        val (repo, engine, _) = setup(fake)
        val c = repo.createConversation(listOf("hermes"))
        engine.send(c.id, "hi")
        idle(engine, c.id)
        val bot = repo.messagesSnapshot(c.id).single { it.role == Role.BOT }
        assertTrue(bot.text.startsWith("**Recounting Newroz"))
        assertEquals("", bot.reasoning)
    }

    @Test fun eachBubbleAfterAToolCallFiltersItsOwnLeadingThoughts() {
        val fake = Fake { _, _ ->
            ok(listOf(ChatEvent.Delta(thought1), ChatEvent.Tool("terminal", "ls", "running", "c1"), ChatEvent.Tool("terminal", "", "completed", "c1"),
                ChatEvent.Delta(thought2), ChatEvent.Delta("Three files.")))
        }
        val (repo, engine, _) = setup(fake)
        val c = repo.createConversation(listOf("hermes"))
        engine.send(c.id, "list")
        idle(engine, c.id)
        val msgs = repo.messagesSnapshot(c.id)
        val bots = msgs.filter { it.role == Role.BOT }
        assertEquals(listOf("Three files."), bots.filter { it.text.isNotBlank() }.map { it.text })
        assertTrue(msgs.none { it.text.startsWith("**") })
    }

    // ---- Phase 3: usage, wake-on-send, unread, budget ------------------------------------------------------------

    /** An engine with the Phase 3 hooks; [eng] lets an awaitReady lambda look at the engine it belongs to. */
    private class Env(val repo: ChatRepository, val engine: ChatEngine, val usage: UsageStore, val events: CopyOnWriteArrayList<EngineEvent>)

    private var eng: ChatEngine? = null

    private fun env(
        backend: ChatBackend,
        ready: () -> Boolean = { true },
        canWake: () -> Boolean = { false },
        awaitReady: (() -> Boolean) -> Boolean = { ready() },
    ): Env {
        val repo = ChatRepository(File(tmp.root, "chat"), CoroutineScope(Dispatchers.IO), 20L)
        runBlocking { repo.loaded.first { it } }
        val usage = UsageStore(File(tmp.root, "usage.json"), CoroutineScope(Dispatchers.IO))
        val engine = ChatEngine(
            repo, backend, { _, _, _ -> HttpResult(200, "{}", 0) }, {}, {}, { "vertex" }, ready, CoroutineScope(Dispatchers.IO),
            canWake = canWake, awaitReady = awaitReady, usage = usage,
        )
        eng = engine
        val events = CopyOnWriteArrayList<EngineEvent>()
        CoroutineScope(Dispatchers.Unconfined).launch { engine.events.collect { events += it } }
        return Env(repo, engine, usage, events)
    }

    private fun waitFor(timeoutMs: Long = 5000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("condition not met within ${timeoutMs}ms")
            Thread.sleep(5)
        }
    }

    @Test fun usageIsWrittenOnTheLastBotMessageAndRecordedInTheStore() {
        val fake = Fake { _, _ -> ok(listOf(ChatEvent.Delta("Hi"), ChatEvent.Usage(1200, 340, 1540))) }
        val e = env(fake)
        val c = e.repo.createConversation(listOf("hermes"))
        e.engine.send(c.id, "hello")
        idle(e.engine, c.id)
        val bot = e.repo.messagesSnapshot(c.id).single { it.role == Role.BOT }
        assertEquals(1200L, bot.tokensIn)
        assertEquals(340L, bot.tokensOut)
        assertEquals("pure Hermes: no model id", "", bot.model)
        val row = e.usage.days.value.single()
        assertEquals(1, row.turns); assertEquals(1200L, row.tokensIn); assertEquals(340L, row.tokensOut); assertEquals("", row.model)
        assertEquals(1540L, e.usage.todayTotal())
        waitFor { e.events.any { it is EngineEvent.TurnCompleted } }
        val done = e.events.filterIsInstance<EngineEvent.TurnCompleted>().single()
        assertEquals(1200L, done.tokensIn); assertEquals(340L, done.tokensOut)
        assertEquals(listOf(EngineEvent.TurnStarted(c.id, "hermes")), e.events.filterIsInstance<EngineEvent.TurnStarted>())
    }

    @Test fun theModelOfABotMessageAndTheStatisticsRowIsTheIdThatWasSent() {
        val fake = Fake { _, _ -> ok(listOf(ChatEvent.Delta("ok"), ChatEvent.Usage(10, 5, 15))) }
        val e = env(fake)
        e.repo.saveBot(BotPresets.coder.copy(model = "gemini-4-neon"))
        val c = e.repo.createConversation(listOf("coder"))
        e.engine.send(c.id, "go")
        idle(e.engine, c.id)
        assertEquals("google/gemini-4-neon", e.repo.messagesSnapshot(c.id).single { it.role == Role.BOT }.model)
        assertEquals("google/gemini-4-neon", e.usage.days.value.single().model)
    }

    @Test fun usageOfATurnWithoutTextIsOnlyRecordedInTheStore() {
        val fake = Fake { _, _ ->
            ok(listOf(ChatEvent.Tool("terminal", "ls", "running", "c1"), ChatEvent.Tool("terminal", "", "completed", "c1"), ChatEvent.Usage(100, 20, 120)))
        }
        val e = env(fake)
        val c = e.repo.createConversation(listOf("hermes"))
        e.engine.send(c.id, "list")
        idle(e.engine, c.id)
        assertTrue(e.repo.messagesSnapshot(c.id).all { it.tokensIn == 0L && it.tokensOut == 0L })
        assertEquals(120L, e.usage.todayTotal())
    }

    @Test fun aGroupTurnRecordsOneRowPerBotAndTheTokensStayOnTheirOwnMessages() {
        var n = 0
        val fake = Fake { _, _ -> ok(listOf(ChatEvent.Delta("a"), ChatEvent.Usage(100L * (++n), 10, 0))) }
        val e = env(fake)
        val c = e.repo.createConversation(listOf("rebel", "coder"))
        e.engine.send(c.id, "hi")
        idle(e.engine, c.id)
        val bots = e.repo.messagesSnapshot(c.id).filter { it.role == Role.BOT }
        assertEquals(listOf(100L, 200L), bots.map { it.tokensIn })
        assertEquals(2, e.usage.days.value.single().turns)
        assertEquals(300L + 20L, e.usage.todayTotal())
    }

    @Test fun aFinishedBotTurnBumpsTheUnreadCountUnlessTheChatIsOnScreen() {
        val fake = Fake { _, _ -> ok(listOf(ChatEvent.Delta("answer"))) }
        val e = env(fake)
        val c = e.repo.createConversation(listOf("hermes"))
        e.engine.send(c.id, "one")
        idle(e.engine, c.id)
        assertEquals(1, e.repo.conversation(c.id)!!.unread)
        e.repo.setActiveConversation(c.id)
        assertEquals("opening the chat clears it", 0, e.repo.conversation(c.id)!!.unread)
        e.engine.send(c.id, "two")
        idle(e.engine, c.id)
        assertEquals("on screen: no bump", 0, e.repo.conversation(c.id)!!.unread)
        e.repo.setActiveConversation(null)
        e.engine.send(c.id, "three")
        idle(e.engine, c.id)
        assertEquals(1, e.repo.conversation(c.id)!!.unread)
    }

    @Test fun aFailedTurnDoesNotBumpUnread() {
        val fake = Fake { _, _ -> emptyList<ChatEvent>() to ChatOutcome.HttpError(500, "boom") }
        val e = env(fake)
        val c = e.repo.createConversation(listOf("hermes"))
        e.engine.send(c.id, "x")
        idle(e.engine, c.id)
        assertEquals(0, e.repo.conversation(c.id)!!.unread)
    }

    @Test fun wakeOnSendAcceptsTheMessageStartsHermesAndThenDelivers() {
        val ready = java.util.concurrent.atomic.AtomicBoolean(false)
        val sawWaking = java.util.concurrent.atomic.AtomicBoolean(false)
        val fake = Fake { _, _ -> ok(listOf(ChatEvent.Delta("good morning"))) }
        val e = env(fake, ready = { ready.get() }, canWake = { true }, awaitReady = { _ ->
            if (eng!!.status.value.values.contains(ChatEngine.STATUS_WAKING)) sawWaking.set(true)
            assertTrue("nothing is sent before Hermes is up", fake.requests.isEmpty())
            ready.set(true)
            true
        })
        val c = e.repo.createConversation(listOf("hermes"))
        assertTrue("accepted although Hermes is asleep", e.engine.send(c.id, "wake up"))
        idle(e.engine, c.id)
        assertTrue("the UI saw the waking status", sawWaking.get())
        assertTrue("the status is gone afterwards", e.engine.status.value.isEmpty())
        assertEquals(listOf(Role.USER, Role.BOT), e.repo.messagesSnapshot(c.id).map { it.role })
        assertEquals(1, fake.requests.size)
    }

    @Test fun wakeOnSendThatFailsLeavesAnErrorAndSendsNothing() {
        val fake = Fake { _, _ -> ok(listOf(ChatEvent.Delta("never"))) }
        val e = env(fake, ready = { false }, canWake = { true }, awaitReady = { _ -> false })
        val c = e.repo.createConversation(listOf("hermes"))
        assertTrue(e.engine.send(c.id, "hello"))
        idle(e.engine, c.id)
        val msgs = e.repo.messagesSnapshot(c.id)
        assertEquals(listOf(Role.USER, Role.ERROR), msgs.map { it.role })
        assertTrue(msgs[1].text.contains("could not start"))
        assertTrue(fake.requests.isEmpty())
        assertTrue(e.engine.status.value.isEmpty())
        waitFor { e.events.any { it is EngineEvent.TurnFailed } }
    }

    @Test fun stoppingWhileWaitingForHermesEndsTheRunQuietly() {
        val entered = java.util.concurrent.CountDownLatch(1)
        val fake = Fake { _, _ -> ok(listOf(ChatEvent.Delta("never"))) }
        val e = env(fake, ready = { false }, canWake = { true }, awaitReady = { cancelled ->
            entered.countDown()
            while (!cancelled()) Thread.sleep(5)
            false
        })
        val c = e.repo.createConversation(listOf("hermes"))
        assertTrue(e.engine.send(c.id, "hello"))
        assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(ChatEngine.STATUS_WAKING, e.engine.status.value[c.id])
        assertTrue(e.engine.isBusy(c.id))
        e.engine.stop(c.id)
        idle(e.engine, c.id)
        assertEquals("no error for a stop the user asked for", listOf(Role.USER), e.repo.messagesSnapshot(c.id).map { it.role })
        assertTrue(fake.requests.isEmpty())
        assertFalse(e.engine.isBusy(c.id))
    }

    @Test fun withoutPermissionToWakeASleepingHermesIsStillRefused() {
        val fake = Fake { _, _ -> ok(listOf(ChatEvent.Delta("never"))) }
        val e = env(fake, ready = { false }, canWake = { false })
        val c = e.repo.createConversation(listOf("hermes"))
        assertFalse(e.engine.send(c.id, "hello"))
        assertFalse(e.engine.runLoop(c.id, "hello"))
        assertTrue(e.repo.messagesSnapshot(c.id).isEmpty())
    }

    @Test fun regenerateAndEditAreAcceptedWhileHermesCanBeWoken() {
        val ready = java.util.concurrent.atomic.AtomicBoolean(true)
        var n = 0
        val fake = Fake { _, _ -> ok(listOf(ChatEvent.Delta("answer ${++n}"))) }
        val e = env(fake, ready = { ready.get() }, canWake = { true }, awaitReady = { _ -> ready.set(true); true })
        val c = e.repo.createConversation(listOf("hermes"))
        e.engine.send(c.id, "question")
        idle(e.engine, c.id)
        ready.set(false)
        assertTrue(e.engine.regenerateLast(c.id))
        idle(e.engine, c.id)
        ready.set(false)
        assertTrue(e.engine.editAndResend(c.id, e.repo.messagesSnapshot(c.id).first().id, "question 2"))
        idle(e.engine, c.id)
        assertEquals(listOf("question 2", "answer 3"), e.repo.messagesSnapshot(c.id).map { it.text })
    }

    @Test fun aBlockedBudgetStopsTheTurnBeforeItIsSent() {
        val fake = Fake { _, _ -> ok(listOf(ChatEvent.Delta("never"))) }
        val e = env(fake)
        e.usage.record("", 600, 500)
        e.usage.setBudget(UsageBudget(1000, BudgetMode.BLOCK))
        val c = e.repo.createConversation(listOf("hermes"))
        assertTrue("accepted: the user message is shown", e.engine.send(c.id, "more please"))
        idle(e.engine, c.id)
        val msgs = e.repo.messagesSnapshot(c.id)
        assertEquals(listOf(Role.USER, Role.ERROR), msgs.map { it.role })
        assertTrue(msgs[1].text.contains("budget") && msgs[1].text.contains("Settings > Usage"))
        assertTrue(fake.requests.isEmpty())
        // Raising the limit lets the same chat go on.
        e.usage.setBudget(UsageBudget(100_000, BudgetMode.BLOCK))
        assertTrue(e.engine.regenerateLast(c.id))
        idle(e.engine, c.id)
        assertEquals(1, fake.requests.size)
    }

    @Test fun aBlockedBudgetDoesNotLightTheFire() {
        val awaited = java.util.concurrent.atomic.AtomicBoolean(false)
        val fake = Fake { _, _ -> ok(listOf(ChatEvent.Delta("never"))) }
        val e = env(fake, ready = { false }, canWake = { true }, awaitReady = { _ -> awaited.set(true); true })
        e.usage.record("", 2000, 0)
        e.usage.setBudget(UsageBudget(1000, BudgetMode.BLOCK))
        val c = e.repo.createConversation(listOf("hermes"))
        e.engine.send(c.id, "hello")
        idle(e.engine, c.id)
        assertFalse(awaited.get())
    }

    @Test fun aWarnBudgetLeavesExactlyOneNoteWhenTheLimitIsCrossed() {
        val fake = Fake { _, _ -> ok(listOf(ChatEvent.Delta("answer"), ChatEvent.Usage(400, 200, 600))) }
        val e = env(fake)
        e.usage.setBudget(UsageBudget(1000, BudgetMode.WARN))
        val c = e.repo.createConversation(listOf("hermes"))
        fun budgetNotes() = e.repo.messagesSnapshot(c.id).count { it.role == Role.NOTE && it.text.contains("budget") }
        e.engine.send(c.id, "one"); idle(e.engine, c.id)
        assertEquals("600 of 1000: no note yet", 0, budgetNotes())
        e.engine.send(c.id, "two"); idle(e.engine, c.id)
        assertEquals("1200 of 1000: crossed", 1, budgetNotes())
        e.engine.send(c.id, "three"); idle(e.engine, c.id)
        assertEquals("only once", 1, budgetNotes())
        assertEquals("a WARN budget never blocks", 3, fake.requests.size)
        assertTrue(e.usage.overBudget())
    }

    @Test fun lastActivityMovesWhenARunStartsAndEnds() {
        val gate = java.util.concurrent.CountDownLatch(1)
        val backend = ChatBackend { _, _, onEvent -> gate.await(5, java.util.concurrent.TimeUnit.SECONDS); onEvent(ChatEvent.Delta("x")); onEvent(ChatEvent.Done); ChatOutcome.Completed }
        val e = env(backend)
        val before = e.engine.lastActivityAt
        Thread.sleep(20)
        val c = e.repo.createConversation(listOf("hermes"))
        e.engine.send(c.id, "hi")
        assertTrue(e.engine.isBusy(c.id))
        assertTrue(e.engine.lastActivityAt > before)
        val started = e.engine.lastActivityAt
        Thread.sleep(20)
        gate.countDown()
        idle(e.engine, c.id)
        assertTrue(e.engine.lastActivityAt > started)
        assertFalse(e.engine.isBusy(c.id))
    }
}
