package dev.pocketagent.app

import dev.pocketagent.app.runtime.RuntimeAwait
import dev.pocketagent.app.runtime.RuntimeState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class RuntimeAwaitTest {
    private fun await(
        state: MutableStateFlow<RuntimeState>, start: () -> Unit, timeoutMs: Long = 3000,
        cancelled: () -> Boolean = { false }, grace: Long = 300,
    ) = RuntimeAwait.awaitRunning(state, start, timeoutMs, cancelled, graceMs = grace, pollMs = 5)

    @Test fun alreadyRunningDoesNotStartAnything() {
        val starts = AtomicInteger()
        assertTrue(await(MutableStateFlow(RuntimeState.Running), { starts.incrementAndGet() }))
        assertEquals(0, starts.get())
    }

    @Test fun aStoppedRuntimeIsStartedAndWaitedFor() {
        val state = MutableStateFlow<RuntimeState>(RuntimeState.Stopped("ready"))
        val starts = AtomicInteger()
        val ok = await(state, {
            starts.incrementAndGet()
            Thread {
                Thread.sleep(30); state.value = RuntimeState.Starting("launching proot")
                Thread.sleep(30); state.value = RuntimeState.Running
            }.start()
        })
        assertTrue(ok)
        assertEquals(1, starts.get())
    }

    @Test fun anAlreadyStartingRuntimeIsNotStartedASecondTime() {
        val state = MutableStateFlow<RuntimeState>(RuntimeState.Starting("waiting for Hermes API (3 s)"))
        val starts = AtomicInteger()
        Thread { Thread.sleep(40); state.value = RuntimeState.Running }.start()
        assertTrue(await(state, { starts.incrementAndGet() }))
        assertEquals(0, starts.get())
    }

    @Test fun anExtractionInProgressIsWaitedFor() {
        val state = MutableStateFlow<RuntimeState>(RuntimeState.Extracting(0.3f, "files"))
        Thread { Thread.sleep(20); state.value = RuntimeState.Starting("x"); Thread.sleep(20); state.value = RuntimeState.Running }.start()
        assertTrue(await(state, { throw AssertionError("must not start") }))
    }

    @Test fun anErrorAfterProgressEndsTheWaitAtOnce() {
        val state = MutableStateFlow<RuntimeState>(RuntimeState.Stopped("ready"))
        val t0 = System.currentTimeMillis()
        val ok = await(state, {
            Thread { Thread.sleep(20); state.value = RuntimeState.Starting("preparing"); Thread.sleep(20); state.value = RuntimeState.Error("boom") }.start()
        }, grace = 10_000)
        assertFalse(ok)
        assertTrue("did not wait out the grace period", System.currentTimeMillis() - t0 < 3000)
    }

    @Test fun aNewErrorStateRightAfterTheStartEndsTheWaitWithoutProgress() {
        val state = MutableStateFlow<RuntimeState>(RuntimeState.Error("old"))
        val t0 = System.currentTimeMillis()
        val ok = await(state, { state.value = RuntimeState.Error("Invalid configuration") }, grace = 10_000)
        assertFalse(ok)
        assertTrue(System.currentTimeMillis() - t0 < 3000)
    }

    @Test fun aStartThatChangesNothingEndsAfterTheGracePeriod() {
        val state = MutableStateFlow<RuntimeState>(RuntimeState.Stopped("ready"))
        val t0 = System.currentTimeMillis()
        assertFalse(await(state, { }, grace = 150))
        assertTrue(System.currentTimeMillis() - t0 >= 150)
    }

    @Test fun cancellingEndsTheWait() {
        val state = MutableStateFlow<RuntimeState>(RuntimeState.Starting("x"))
        val stop = java.util.concurrent.atomic.AtomicBoolean(false)
        Thread { Thread.sleep(40); stop.set(true) }.start()
        assertFalse(await(state, { }, timeoutMs = 10_000, cancelled = { stop.get() }))
        assertFalse("cancelled before it began", await(state, { throw AssertionError("must not start") }, cancelled = { true }))
    }

    @Test fun theTimeoutEndsTheWait() {
        val state = MutableStateFlow<RuntimeState>(RuntimeState.Starting("x"))
        val t0 = System.currentTimeMillis()
        assertFalse(await(state, { }, timeoutMs = 100))
        assertTrue(System.currentTimeMillis() - t0 >= 100)
    }

    @Test fun aStartFunctionThatThrowsMeansFailure() {
        val state = MutableStateFlow<RuntimeState>(RuntimeState.Stopped("x"))
        assertFalse(await(state, { throw IllegalStateException("no") }))
    }
}
