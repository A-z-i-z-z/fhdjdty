package dev.pocketagent.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class FireFeedCoreTest {
    private var now = 0L
    private fun core() = FireFeedCore { now }

    @Test fun stateEmitsOnEventsOnly() {
        now = 1_000L
        val c = core()
        c.turnStarted("a")
        val first = c.state.value
        assertEquals(setOf("a"), first.turns)
        // time passes: the state object is the same, only the level read from it moves
        now += 30_000L
        assertSame(first, c.state.value)
        assertEquals(FireMath.TURN_FLOOR, c.level(), 0f)
        // events that change nothing publish nothing
        c.turnEnded("nobody")
        c.toolFinished("a", "never-started")
        assertSame(first, c.state.value)
    }

    @Test fun levelFollowsClock() {
        now = 0L
        val c = core()
        c.kick(0.8f)
        assertEquals(0.8f, c.level(), 1e-6f)
        now = 9_000L
        assertEquals(0.4f, c.level(), 1e-5f)
        now = 60_000L
        assertEquals(0f, c.level(), 0f)
    }

    @Test fun pinOverridesAndReleases() {
        now = 0L
        val c = core()
        c.turnStarted("a")
        assertEquals(0.55f, c.level(), 1e-6f)
        c.pin(0.2f)
        assertEquals(0.2f, c.level(), 0f)
        c.pin(5f)
        assertEquals(1f, c.level(), 0f)
        c.pin(-2f)
        assertEquals(0f, c.level(), 0f)
        c.pin(null)
        assertEquals(0.55f, c.level(), 1e-6f)
        c.pin(Float.NaN)
        assertEquals(0.55f, c.level(), 1e-6f)
    }

    @Test fun resetClearsEverything() {
        now = 0L
        val c = core()
        c.turnStarted("a")
        c.toolStarted("a", "1")
        c.pin(0.9f)
        assertEquals(1, c.toolsRunning.value)
        c.reset()
        assertEquals(FireFeedState(), c.state.value)
        assertEquals(0, c.toolsRunning.value)
        assertEquals(0f, c.level(), 0f)
    }

    @Test fun toolsRunningAgreesWithTheState() {
        now = 0L
        val c = core()
        assertEquals(0, c.toolsRunning.value)
        c.turnStarted("a")
        c.toolStarted("a", "1")
        c.toolStarted("a", "2")
        c.toolStarted("b", "1")
        assertEquals(3, c.toolsRunning.value)
        c.toolStarted("a", "1")
        assertEquals(3, c.toolsRunning.value)
        c.toolFinished("a", "1")
        assertEquals(2, c.toolsRunning.value)
        c.turnEnded("a")
        assertEquals(1, c.toolsRunning.value)
        assertEquals(c.state.value.tools.size, c.toolsRunning.value)
        c.turnEnded("b")
        assertEquals(0, c.toolsRunning.value)
    }

    @Test fun textThenEndLeavesNoFloor() {
        now = 0L
        val c = core()
        repeat(5) { c.textArrived("a") }
        assertEquals(setOf("a"), c.state.value.turns)
        c.turnEnded("a")
        assertTrue(c.state.value.turns.isEmpty())
        assertTrue(c.level() > 0f)
        now = 60_000L
        assertEquals(0f, c.level(), 0f)
    }

    @Test fun fourThreadsTenThousandCallsEndConsistent() {
        val c = FireFeedCore { System.nanoTime() / 1_000_000L }
        val bad = AtomicBoolean(false)
        val done = AtomicBoolean(false)
        val reader = Thread {
            while (!done.get()) {
                if (c.toolsRunning.value < 0) bad.set(true)
                val level = c.level()
                if (level < 0f || level > 1f) bad.set(true)
            }
        }
        reader.start()
        val workers = (0 until 4).map { w ->
            Thread {
                val conv = "conv$w"
                for (i in 0 until 2_500) {
                    c.turnStarted(conv)
                    c.toolStarted(conv, "call${i % 5}")
                    c.textArrived(conv)
                    c.toolFinished(conv, "call${i % 5}")
                }
                c.turnEnded(conv)
            }
        }
        workers.forEach { it.start() }
        workers.forEach { it.join() }
        done.set(true)
        reader.join()
        assertFalse("a reader saw an impossible value", bad.get())
        assertTrue(c.state.value.turns.isEmpty())
        assertTrue(c.state.value.tools.isEmpty())
        assertEquals(0, c.toolsRunning.value)
    }
}
