package dev.pocketagent.app

import dev.pocketagent.app.api.ChatEvent
import dev.pocketagent.app.api.ChatOutcome
import dev.pocketagent.app.api.ChatRequest
import dev.pocketagent.app.api.HttpResult
import dev.pocketagent.app.api.StreamHandle
import dev.pocketagent.app.data.BudgetMode
import dev.pocketagent.app.data.ChatBackend
import dev.pocketagent.app.data.ChatEngine
import dev.pocketagent.app.data.ChatRepository
import dev.pocketagent.app.data.EngineEvent
import dev.pocketagent.app.data.Role
import dev.pocketagent.app.data.UsageBudget
import dev.pocketagent.app.data.UsageStore
import dev.pocketagent.app.text.TestTexts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * The engine side of the Wave 0 phone-proof defects: approvals that can be answered and stopped (E1, E2), readable error lines (E3)
 * and practice turns that stay out of the usage numbers (E5). The pure parts are in ApiErrorTextTest and ApprovalContractTest.
 */
class EngineProofFixesTest {
    @Before fun installTexts() { TestTexts.installEnglish() }
    @After fun resetTexts() { TestTexts.reset() }
    @get:Rule val tmp = TemporaryFolder()

    private class Fake(val script: () -> Pair<List<ChatEvent>, ChatOutcome>) : ChatBackend {
        val requests = CopyOnWriteArrayList<ChatRequest>()
        override fun stream(request: ChatRequest, handle: StreamHandle, onEvent: (ChatEvent) -> Unit): ChatOutcome {
            requests += request
            val (events, outcome) = script()
            events.forEach(onEvent)
            return outcome
        }
    }

    private class Env(val repo: ChatRepository, val engine: ChatEngine, val usage: UsageStore)

    private fun env(
        backend: ChatBackend,
        approve: (String, String, String?, Long?) -> HttpResult = { _, _, _, _ -> HttpResult(200, "{}", 0) },
        isPractice: () -> Boolean = { false },
        interruptRun: (String) -> Boolean = { true },
    ): Env {
        val repo = ChatRepository(File(tmp.root, "chat"), CoroutineScope(Dispatchers.IO), 20L)
        runBlocking { repo.loaded.first { it } }
        val usage = UsageStore(File(tmp.root, "usage.json"), CoroutineScope(Dispatchers.IO))
        val engine = ChatEngine(
            repo, backend, approve, {}, {}, { "vertex" }, { true }, CoroutineScope(Dispatchers.IO),
            usage = usage, isPractice = isPractice, interruptRun = interruptRun,
        )
        return Env(repo, engine, usage)
    }

    private fun idle(engine: ChatEngine, convId: String) {
        val end = System.currentTimeMillis() + 5000
        while (engine.busy.value.contains(convId)) {
            if (System.currentTimeMillis() > end) throw AssertionError("engine still busy")
            Thread.sleep(5)
        }
    }

    private fun waitFor(timeoutMs: Long = 5000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("condition not met within ${timeoutMs}ms")
            Thread.sleep(5)
        }
    }

    private val ok: (List<ChatEvent>) -> Pair<List<ChatEvent>, ChatOutcome> = { it + ChatEvent.Finish("stop", null) + ChatEvent.Done to ChatOutcome.Completed }

    private val approvalEvent = ChatEvent.Approval(
        "chatcmpl-9", "rm -rf x", "d", listOf("once", "session", "always", "deny"), "req-1", "{}", 5L,
    )

    // ---- E1, E2: approvals ----------------------------------------------------------------------------------------------

    @Test fun theAnswerCarriesTheRequestIdAndTheGenerationFromTheEvent() {
        val got = CopyOnWriteArrayList<List<Any?>>()
        val release = CountDownLatch(1)
        val backend = ChatBackend { _, _, onEvent ->
            onEvent(approvalEvent)
            release.await(5, TimeUnit.SECONDS)
            onEvent(ChatEvent.Done)
            ChatOutcome.Completed
        }
        val e = env(backend, approve = { run, choice, rid, gen -> got += listOf(run, choice, rid, gen); HttpResult(200, "{}", 0) })
        val c = e.repo.createConversation(listOf("hermes"))
        e.engine.send(c.id, "go")
        waitFor { e.engine.approvals.value[c.id] != null }
        assertEquals(5L, e.engine.approvals.value[c.id]!!.generation)
        e.engine.resolveApproval(c.id, "always")
        waitFor { got.isNotEmpty() }
        assertEquals(listOf<Any?>("chatcmpl-9", "always", "req-1", 5L), got.single())
        release.countDown()
        idle(e.engine, c.id)
    }

    @Test fun aFailedAnswerShowsAReadableLineOnTheCardAndCanBeRepeated() {
        val release = CountDownLatch(1)
        val answers = AtomicInteger(0)
        val gone = """{"error":{"message":"approval_not_pending","type":"invalid_request_error","code":"approval_not_pending"}}"""
        val backend = ChatBackend { _, _, onEvent ->
            onEvent(approvalEvent)
            release.await(5, TimeUnit.SECONDS)
            onEvent(ChatEvent.Done)
            ChatOutcome.Completed
        }
        val e = env(backend, approve = { _, _, _, _ -> if (answers.incrementAndGet() == 1) HttpResult(409, gone, 1) else HttpResult(200, "{}", 1) })
        val c = e.repo.createConversation(listOf("hermes"))
        e.engine.send(c.id, "go")
        waitFor { e.engine.approvals.value[c.id] != null }
        e.engine.resolveApproval(c.id, "deny")
        waitFor { answers.get() == 1 && e.engine.approvals.value[c.id] != null }
        val back = e.engine.approvals.value[c.id]!!
        assertTrue(back.errorLine!!, back.errorLine!!.contains("no longer waiting"))
        assertFalse("no raw body on the card", back.errorLine!!.contains("{"))
        assertEquals("the generation is kept for the next try", 5L, back.generation)
        assertTrue("the failure is on the card, not repeated as a chat error", e.repo.messagesSnapshot(c.id).none { it.role == Role.ERROR })
        e.engine.resolveApproval(c.id, "deny")
        waitFor { answers.get() == 2 }
        assertNull(e.engine.approvals.value[c.id])
        release.countDown()
        idle(e.engine, c.id)
    }

    @Test fun anotherFailureNamesTheReasonHermesGave() {
        val e = env(ChatBackend { _, _, _ -> ChatOutcome.Completed })
        val line = e.engine.approvalFailureLine(HttpResult(400, """{"error":{"message":"invalid_params","code":"invalid_params"}}""", 1))
        assertEquals("Your answer did not reach Hermes: invalid_params", line)
        assertTrue(e.engine.approvalFailureLine(HttpResult(-1, "", 0, "SocketTimeoutException: timeout")).contains("SocketTimeoutException"))
    }

    @Test fun stopWhileAPermissionRequestWaitsStopsTheRunFirstAndClosesTheStreamWithoutADeny() {
        val order = CopyOnWriteArrayList<String>()
        val sawApproval = CountDownLatch(1)
        val backend = object : ChatBackend {
            override fun stream(request: ChatRequest, handle: StreamHandle, onEvent: (ChatEvent) -> Unit): ChatOutcome {
                onEvent(approvalEvent)
                sawApproval.countDown()
                while (!handle.cancelled) Thread.sleep(5)
                order += "cancel"
                onEvent(ChatEvent.Delta("late text"))   // what Hermes still sent before it noticed: must not reach the chat
                return ChatOutcome.Cancelled
            }
        }
        val e = env(
            backend,
            approve = { run, choice, rid, gen -> order += "approve:$run:$choice:$rid:$gen"; HttpResult(200, "{}", 0) },
            interruptRun = { order += "stop:$it"; true },
        )
        val c = e.repo.createConversation(listOf("hermes"))
        e.engine.send(c.id, "go")
        assertTrue(sawApproval.await(5, TimeUnit.SECONDS))
        waitFor { e.engine.approvals.value[c.id] != null }
        e.engine.stop(c.id)
        assertNull("the card goes at once", e.engine.approvals.value[c.id])
        idle(e.engine, c.id)
        assertEquals("the interrupt alone ends the wait; a deny would let Hermes start another model call",
            listOf("stop:chatcmpl-9", "cancel"), order.toList())
        val roles = e.repo.messagesSnapshot(c.id).map { it.role }
        assertTrue("nothing is added after Stop: $roles", roles.none { it == Role.ERROR || it == Role.BOT })
    }

    @Test fun stopDeniesTheRequestOnlyWhenHermesRefusesTheInterrupt() {
        val order = CopyOnWriteArrayList<String>()
        val sawApproval = CountDownLatch(1)
        val backend = object : ChatBackend {
            override fun stream(request: ChatRequest, handle: StreamHandle, onEvent: (ChatEvent) -> Unit): ChatOutcome {
                onEvent(approvalEvent)
                sawApproval.countDown()
                while (!handle.cancelled) Thread.sleep(5)
                order += "cancel"
                return ChatOutcome.Cancelled
            }
        }
        val e = env(
            backend,
            approve = { run, choice, rid, gen -> order += "approve:$run:$choice:$rid:$gen"; HttpResult(200, "{}", 0) },
            interruptRun = { order += "stop:$it"; false },
        )
        val c = e.repo.createConversation(listOf("hermes"))
        e.engine.send(c.id, "go")
        assertTrue(sawApproval.await(5, TimeUnit.SECONDS))
        waitFor { e.engine.approvals.value[c.id] != null }
        e.engine.stop(c.id)
        idle(e.engine, c.id)
        assertEquals(listOf("stop:chatcmpl-9", "approve:chatcmpl-9:deny:req-1:5", "cancel"), order.toList())
    }

    @Test fun stopWhileAnAnswerIsStillBeingSentInterruptsTheRunBeforeTheStreamCloses() {
        val order = CopyOnWriteArrayList<String>()
        val sawApproval = CountDownLatch(1)
        val answerStarted = CountDownLatch(1)
        val answerGate = CountDownLatch(1)
        val backend = object : ChatBackend {
            override fun stream(request: ChatRequest, handle: StreamHandle, onEvent: (ChatEvent) -> Unit): ChatOutcome {
                onEvent(approvalEvent)
                sawApproval.countDown()
                while (!handle.cancelled) Thread.sleep(5)
                order += "cancel"
                return ChatOutcome.Cancelled
            }
        }
        val e = env(
            backend,
            approve = { _, choice, _, _ ->
                order += "approve:$choice"
                answerStarted.countDown()
                answerGate.await(5, TimeUnit.SECONDS)   // a slow Hermes: the answer is still on its way when Stop is pressed
                HttpResult(200, "{}", 0)
            },
            interruptRun = { order += "stop:$it"; true },
        )
        val c = e.repo.createConversation(listOf("hermes"))
        e.engine.send(c.id, "go")
        assertTrue(sawApproval.await(5, TimeUnit.SECONDS))
        waitFor { e.engine.approvals.value[c.id] != null }
        e.engine.resolveApproval(c.id, "once")
        assertTrue(answerStarted.await(5, TimeUnit.SECONDS))
        assertNull("the card is gone while the answer is sent", e.engine.approvals.value[c.id])
        e.engine.stop(c.id)
        idle(e.engine, c.id)
        answerGate.countDown()
        assertEquals("Hermes is interrupted although no card was left, and no deny is sent",
            listOf("approve:once", "stop:chatcmpl-9", "cancel"), order.toList())
    }

    /**
     * The realistic path: Hermes accepts the interrupt and ends the turn on its own side, so the stream ends ([DONE] or a plain
     * close) before our own cancel. The turn must still count as cancelled: nothing added, no unread, no event.
     */
    private fun stopWhereHermesEndsTheStreamItself(outcome: ChatOutcome, sendDone: Boolean) {
        val interrupted = CountDownLatch(1)
        val sawApproval = CountDownLatch(1)
        val backend = object : ChatBackend {
            override fun stream(request: ChatRequest, handle: StreamHandle, onEvent: (ChatEvent) -> Unit): ChatOutcome {
                onEvent(approvalEvent)
                sawApproval.countDown()
                assertTrue(interrupted.await(5, TimeUnit.SECONDS))
                if (sendDone) { onEvent(ChatEvent.Finish("stop", null)); onEvent(ChatEvent.Done) }
                return outcome   // without waiting for handle.cancelled
            }
        }
        val e = env(backend, interruptRun = { interrupted.countDown(); true })
        val seen = CopyOnWriteArrayList<EngineEvent>()
        val collector = CoroutineScope(Dispatchers.IO).launch(start = CoroutineStart.UNDISPATCHED) { e.engine.events.collect { seen += it } }
        try {
            val c = e.repo.createConversation(listOf("hermes"))
            e.engine.send(c.id, "go")
            assertTrue(sawApproval.await(5, TimeUnit.SECONDS))
            waitFor { e.engine.approvals.value[c.id] != null && e.repo.messagesSnapshot(c.id).any { it.role == Role.NOTE } }
            val before = e.repo.messagesSnapshot(c.id).size
            e.engine.stop(c.id)
            idle(e.engine, c.id)
            val after = e.repo.messagesSnapshot(c.id)
            assertTrue("no BOT, NOTE or ERROR line after Stop: ${after.map { it.role }}",
                after.drop(before).none { it.role == Role.BOT || it.role == Role.NOTE || it.role == Role.ERROR })
            assertEquals("no unread count", 0, e.repo.conversation(c.id)?.unread)
            Thread.sleep(50)
            assertTrue("no completed or failed event: $seen", seen.none { it is EngineEvent.TurnCompleted || it is EngineEvent.TurnFailed })
        } finally {
            collector.cancel()
        }
    }

    @Test fun stopWhereHermesEndsTheStreamWithDoneStillCountsAsCancelled() =
        stopWhereHermesEndsTheStreamItself(ChatOutcome.Completed, sendDone = true)

    @Test fun stopWhereHermesClosesTheStreamWithoutDoneStillCountsAsCancelled() =
        stopWhereHermesEndsTheStreamItself(ChatOutcome.TransportError("stream ended without [DONE]"), sendDone = false)

    @Test fun stopFinishesAnEmptyBubbleTurnWithoutAnEmptyReplyNote() {
        val interrupted = CountDownLatch(1)
        val backend = object : ChatBackend {
            override fun stream(request: ChatRequest, handle: StreamHandle, onEvent: (ChatEvent) -> Unit): ChatOutcome {
                onEvent(approvalEvent)
                assertTrue(interrupted.await(5, TimeUnit.SECONDS))
                onEvent(ChatEvent.Done)
                return ChatOutcome.Completed
            }
        }
        val e = env(backend, interruptRun = { interrupted.countDown(); true })
        val c = e.repo.createConversation(listOf("hermes"))
        e.engine.send(c.id, "go")
        waitFor { e.engine.approvals.value[c.id] != null && e.repo.messagesSnapshot(c.id).any { it.role == Role.NOTE } }
        val notesBefore = e.repo.messagesSnapshot(c.id).count { it.role == Role.NOTE }   // the "permission pending" line of the card
        e.engine.stop(c.id)
        idle(e.engine, c.id)
        assertEquals("no empty-reply note after Stop", notesBefore, e.repo.messagesSnapshot(c.id).count { it.role == Role.NOTE })
    }

    @Test fun stopStillEndsTheStreamWhenTheDenyAndTheStopCallsFail() {
        val sawApproval = CountDownLatch(1)
        val backend = object : ChatBackend {
            override fun stream(request: ChatRequest, handle: StreamHandle, onEvent: (ChatEvent) -> Unit): ChatOutcome {
                onEvent(approvalEvent)
                sawApproval.countDown()
                while (!handle.cancelled) Thread.sleep(5)
                return ChatOutcome.Cancelled
            }
        }
        val e = env(backend, approve = { _, _, _, _ -> throw java.io.IOException("down") }, interruptRun = { throw java.io.IOException("down") })
        val c = e.repo.createConversation(listOf("hermes"))
        e.engine.send(c.id, "go")
        assertTrue(sawApproval.await(5, TimeUnit.SECONDS))
        waitFor { e.engine.approvals.value[c.id] != null }
        e.engine.stop(c.id)
        idle(e.engine, c.id)
        assertFalse(e.engine.isBusy(c.id))
    }

    @Test fun aPlainStopStillClosesTheStreamAtOnce() {
        val started = CountDownLatch(1)
        val order = CopyOnWriteArrayList<String>()
        val backend = object : ChatBackend {
            override fun stream(request: ChatRequest, handle: StreamHandle, onEvent: (ChatEvent) -> Unit): ChatOutcome {
                onEvent(ChatEvent.Delta("partial"))
                started.countDown()
                while (!handle.cancelled) Thread.sleep(5)
                return ChatOutcome.Cancelled
            }
        }
        val e = env(backend, approve = { _, _, _, _ -> order += "approve"; HttpResult(200, "{}", 0) }, interruptRun = { order += "stop"; true })
        val c = e.repo.createConversation(listOf("hermes"))
        e.engine.send(c.id, "go")
        assertTrue(started.await(5, TimeUnit.SECONDS))
        e.engine.stop(c.id)
        idle(e.engine, c.id)
        assertTrue("no permission request waited, so Hermes is not called", order.isEmpty())
        assertEquals("partial", e.repo.messagesSnapshot(c.id).single { it.role == Role.BOT }.text)
    }

    // ---- E3: readable errors --------------------------------------------------------------------------------------------

    private val failChunk = """{"id":"chatcmpl-1","object":"chat.completion.chunk","model":"google/gemini-4-neon","choices":[{"index":0,"delta":{},"finish_reason":"error"}],""" +
        """"usage":{"prompt_tokens":0,"completion_tokens":0,"total_tokens":0},"error":{"message":"practice failure: the FAIL word was used","type":"agent_error"}}"""

    private fun lineOf(script: () -> Pair<List<ChatEvent>, ChatOutcome>): String {
        val e = env(Fake(script))
        val c = e.repo.createConversation(listOf("hermes"))
        e.engine.send(c.id, "hello")
        idle(e.engine, c.id)
        return e.repo.messagesSnapshot(c.id).single { it.role == Role.ERROR }.text
    }

    @Test fun anErrorChunkInTheStreamShowsOneReadableLineNeverTheChunk() {
        val text = lineOf {
            listOf<ChatEvent>(ChatEvent.StreamError("practice failure: the FAIL word was used", failChunk), ChatEvent.Finish("error", null), ChatEvent.Done) to ChatOutcome.Completed
        }
        assertEquals("Hermes reported an error: practice failure: the FAIL word was used", text)
        assertFalse(text.contains("chat.completion.chunk") || text.contains("finish_reason"))
    }

    @Test fun anErrorWithoutAMessageGetsAPlainSentence() {
        val text = lineOf { listOf<ChatEvent>(ChatEvent.StreamError("", failChunk), ChatEvent.Finish("error", null)) to ChatOutcome.Completed }
        assertEquals("Hermes reported an error without saying why. The details are in the app log.", text)
    }

    @Test fun httpErrorsAreReadFromJsonTextAndHtmlAndNeverShowTheBody() {
        fun http(code: Int, body: String) = lineOf { emptyList<ChatEvent>() to ChatOutcome.HttpError(code, body) }
        assertEquals("Hermes answered with an error (HTTP 400): practice failure", http(400, """{"error":{"message":"practice failure","code":"practice_fail"}}"""))
        assertEquals("Hermes answered with an error (HTTP 404): Not found", http(404, """{"detail":"Not found"}"""))
        assertEquals("Hermes answered with an error (HTTP 500): boom", http(500, "boom"))
        assertEquals("Hermes answered with an error (HTTP 502): 502 Bad Gateway", http(502, "<html><head><title>502 Bad Gateway</title></head><body>x</body></html>"))
        assertEquals("Hermes answered with an error (HTTP 500). The details are in the app log.", http(500, ""))
        assertEquals("Hermes answered with an error (HTTP 503). The details are in the app log.", http(503, "<html><body>down</body></html>"))
    }

    // ---- E5: practice turns and the usage numbers -----------------------------------------------------------------------

    @Test fun aPracticeTurnShowsItsTokensOnTheMessageButNeverCountsInTheStatistics() {
        val e = env(Fake { ok(listOf(ChatEvent.Delta("hi"), ChatEvent.Usage(100, 10, 110))) }, isPractice = { true })
        val c = e.repo.createConversation(listOf("hermes"))
        e.engine.send(c.id, "hello")
        idle(e.engine, c.id)
        val bot = e.repo.messagesSnapshot(c.id).single { it.role == Role.BOT }
        assertEquals(100L, bot.tokensIn); assertEquals(10L, bot.tokensOut)
        assertTrue("no day row, no model row", e.usage.days.value.isEmpty())
        assertEquals(0L, e.usage.todayTotal())
    }

    @Test fun aPracticeTurnIsNeitherBlockedNorWarnedByTheBudgetButARealOneIs() {
        val fake = Fake { ok(listOf(ChatEvent.Delta("hi"), ChatEvent.Usage(900, 100, 1000))) }
        val practice = AtomicBoolean(true)
        val e = env(fake, isPractice = { practice.get() })
        e.usage.record("", 600, 500)
        e.usage.setBudget(UsageBudget(1000, BudgetMode.BLOCK))
        val c = e.repo.createConversation(listOf("hermes"))
        assertTrue(e.engine.send(c.id, "practice turn"))
        idle(e.engine, c.id)
        assertEquals("the practice turn ran although the budget is used up", 1, fake.requests.size)
        assertEquals("and added nothing to the day", 1100L, e.usage.todayTotal())
        practice.set(false)
        e.engine.send(c.id, "real turn")
        idle(e.engine, c.id)
        assertEquals("a real turn is still blocked", 1, fake.requests.size)
        e.usage.setBudget(UsageBudget(1200, BudgetMode.WARN))
        practice.set(true)
        e.engine.send(c.id, "another practice turn")
        idle(e.engine, c.id)
        assertEquals(2, fake.requests.size)
        assertTrue("no budget note for a practice turn", e.repo.messagesSnapshot(c.id).none { it.role == Role.NOTE && it.text.contains("budget") })
    }

    @Test fun aSendThatWakesHermesChecksTheBudgetAgainWhenPracticeTurnedOffDuringTheLaunch() {
        val fake = Fake { ok(listOf(ChatEvent.Delta("hi"), ChatEvent.Usage(900, 100, 1000))) }
        val calls = AtomicInteger(0)
        val e = env(fake, isPractice = { calls.getAndIncrement() == 0 })   // practice when the send starts, real after the wake
        e.usage.record("", 600, 500)
        e.usage.setBudget(UsageBudget(1000, BudgetMode.BLOCK))
        val c = e.repo.createConversation(listOf("hermes"))
        e.engine.send(c.id, "go")
        idle(e.engine, c.id)
        assertEquals("blocked: the turn turned out to be real and the budget is used up", 0, fake.requests.size)
        assertEquals(1100L, e.usage.todayTotal())
        assertTrue(e.repo.messagesSnapshot(c.id).any { it.role == Role.ERROR })
    }

    @Test fun aSendThatTurnsOutRealIsCountedWhenTheBudgetAllowsIt() {
        val fake = Fake { ok(listOf(ChatEvent.Delta("hi"), ChatEvent.Usage(90, 10, 100))) }
        val calls = AtomicInteger(0)
        val e = env(fake, isPractice = { calls.getAndIncrement() == 0 })
        e.usage.setBudget(UsageBudget(100_000, BudgetMode.BLOCK))
        val c = e.repo.createConversation(listOf("hermes"))
        e.engine.send(c.id, "go")
        idle(e.engine, c.id)
        assertEquals(1, fake.requests.size)
        assertEquals("counted, because the turn was real", 100L, e.usage.todayTotal())
    }

    @Test fun aRealTurnStillCountsAndWarns() {
        val e = env(Fake { ok(listOf(ChatEvent.Delta("hi"), ChatEvent.Usage(900, 100, 1000))) })
        e.usage.setBudget(UsageBudget(500, BudgetMode.WARN))
        val c = e.repo.createConversation(listOf("hermes"))
        e.engine.send(c.id, "hello")
        idle(e.engine, c.id)
        assertEquals(1000L, e.usage.todayTotal())
        assertTrue(e.repo.messagesSnapshot(c.id).any { it.role == Role.NOTE && it.text.contains("budget") })
    }
}
