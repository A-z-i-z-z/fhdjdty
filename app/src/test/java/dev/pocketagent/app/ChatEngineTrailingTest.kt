package dev.pocketagent.app

import dev.pocketagent.app.api.ChatEvent
import dev.pocketagent.app.api.ChatOutcome
import dev.pocketagent.app.api.ChatRequest
import dev.pocketagent.app.api.HttpResult
import dev.pocketagent.app.api.StreamHandle
import dev.pocketagent.app.data.ChatBackend
import dev.pocketagent.app.data.ChatEngine
import dev.pocketagent.app.data.ChatRepository
import dev.pocketagent.app.data.Message
import dev.pocketagent.app.data.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The engine writes the last piece of text of a stream even when no further piece comes (a trailing write 60 ms after the
 * last change), and the lock and the cancel of that timer do not disturb the end of a turn.
 */
class ChatEngineTrailingTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Sends [first] at once, waits for [release], then sends [rest] and ends the stream normally. */
    private class Gated(val first: List<ChatEvent>, val release: CountDownLatch?, val rest: List<ChatEvent>) : ChatBackend {
        override fun stream(request: ChatRequest, handle: StreamHandle, onEvent: (ChatEvent) -> Unit): ChatOutcome {
            first.forEach(onEvent)
            release?.await(10, TimeUnit.SECONDS)
            rest.forEach(onEvent)
            onEvent(ChatEvent.Finish("stop", null))
            onEvent(ChatEvent.Done)
            return ChatOutcome.Completed
        }
    }

    private fun setup(backend: ChatBackend): Triple<ChatRepository, ChatEngine, File> {
        val dir = File(tmp.root, "chat")
        val repo = ChatRepository(dir, CoroutineScope(Dispatchers.IO), 20L)
        runBlocking { repo.loaded.first { it } }
        val engine = ChatEngine(
            repo, backend, { _, _, _ -> HttpResult(200, "{}", 0) }, {}, {}, { "vertex" }, { true }, CoroutineScope(Dispatchers.IO),
        )
        return Triple(repo, engine, dir)
    }

    private fun idle(engine: ChatEngine, convId: String) {
        val end = System.currentTimeMillis() + 5000
        while (engine.busy.value.contains(convId)) {
            if (System.currentTimeMillis() > end) throw AssertionError("engine still busy")
            Thread.sleep(5)
        }
    }

    private fun bot(repo: ChatRepository, convId: String): Message? =
        repo.messagesSnapshot(convId).firstOrNull { it.role == Role.BOT }

    private fun deltas(vararg t: String): List<ChatEvent> = t.map { ChatEvent.Delta(it) }

    @Test fun lastPieceBeforeAPauseIsPublishedWhileStreamIsStillOpen() {
        val latch = CountDownLatch(1)
        val (repo, engine, _) = setup(Gated(deltas("a", "b"), latch, emptyList()))
        val c = repo.createConversation(listOf("hermes"))
        try {
            engine.send(c.id, "hi")
            val end = System.currentTimeMillis() + 1000
            var text = ""
            while (System.currentTimeMillis() < end) {
                text = bot(repo, c.id)?.text.orEmpty()
                if (text == "ab") break
                Thread.sleep(10)
            }
            assertEquals("the trailing write must publish the second piece without a third one", "ab", text)
            assertTrue("the stream is still open", bot(repo, c.id)?.streaming == true)
        } finally {
            latch.countDown()
        }
        idle(engine, c.id)
        assertEquals("ab", bot(repo, c.id)?.text)
    }

    @Test fun finalTextAndStreamingFlagAfterFinish() {
        val (repo, engine, _) = setup(Gated(deltas("x", "y", "z", "!"), null, deltas("?")))
        val c = repo.createConversation(listOf("hermes"))
        engine.send(c.id, "hi")
        idle(engine, c.id)
        val msg = bot(repo, c.id)
        assertNotNull(msg)
        assertEquals("xyz!?", msg!!.text)
        assertFalse(msg.streaming)
    }

    @Test fun noWriteAfterBubbleClosed() {
        val (repo, engine, _) = setup(Gated(deltas("one", " two"), null, emptyList()))
        val c = repo.createConversation(listOf("hermes"))
        engine.send(c.id, "hi")
        idle(engine, c.id)
        val closed = repo.messagesSnapshot(c.id)
        assertEquals("one two", closed.first { it.role == Role.BOT }.text)
        // The timer of the last burst was cancelled with the turn: nothing is written afterwards.
        Thread.sleep(250)
        assertEquals(closed, repo.messagesSnapshot(c.id))
        assertFalse(closed.first { it.role == Role.BOT }.streaming)
    }
}
