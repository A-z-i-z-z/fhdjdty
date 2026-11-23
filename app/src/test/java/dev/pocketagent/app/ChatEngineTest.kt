package dev.pocketagent.app

import dev.pocketagent.app.api.ChatEvent
import dev.pocketagent.app.api.ChatOutcome
import dev.pocketagent.app.api.ChatRequest
import dev.pocketagent.app.api.HttpResult
import dev.pocketagent.app.api.StreamHandle
import dev.pocketagent.app.data.BotPresets
import dev.pocketagent.app.data.ChatBackend
import dev.pocketagent.app.data.ChatEngine
import dev.pocketagent.app.data.ChatRepository
import dev.pocketagent.app.data.GroupMode
import dev.pocketagent.app.data.ModelIds
import dev.pocketagent.app.data.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

class ChatEngineTest {
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
}
