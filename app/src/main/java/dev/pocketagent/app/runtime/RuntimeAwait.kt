package dev.pocketagent.app.runtime

import kotlinx.coroutines.flow.StateFlow

/** Starts Hermes on demand and waits until it answers: what "wake-on-send" needs. */
object RuntimeAwait {
    /** How long a terminal-looking state right after [RuntimeManager.start] is treated as the stale state from before the start. */
    private const val START_GRACE_MS = 5_000L
    private const val POLL_MS = 100L

    /**
     * Starts the runtime if it is not busy or running and blocks until it is Running. Returns false when it ended in
     * Error or Stopped, when [cancelled] became true, or after [timeoutMs]. Never call on the main thread.
     */
    fun awaitRunning(runtime: RuntimeManager, timeoutMs: Long, cancelled: () -> Boolean): Boolean =
        awaitRunning(runtime.state, { runtime.start() }, timeoutMs, cancelled)

    /**
     * The logic of [awaitRunning] on a bare state flow and start function (so a JVM test can drive it). [start] must return
     * quickly (as [RuntimeManager.start] does: it only launches a coroutine); the state may still show its old value for a
     * moment afterwards, which is why a terminal state counts as "ended" only once the state object has changed, progress
     * (Extracting/Starting) was seen, or [START_GRACE_MS] have passed.
     */
    internal fun awaitRunning(
        state: StateFlow<RuntimeState>,
        start: () -> Unit,
        timeoutMs: Long,
        cancelled: () -> Boolean,
        graceMs: Long = START_GRACE_MS,
        pollMs: Long = POLL_MS,
    ): Boolean {
        val t0 = System.nanoTime()
        fun elapsedMs() = (System.nanoTime() - t0) / 1_000_000
        if (cancelled()) return false
        val initial = state.value
        if (initial == RuntimeState.Running) return true
        var sawProgress = initial.isBusyOrRunning
        var startedAt = -1L
        if (!initial.isBusyOrRunning) {
            try { start() } catch (_: Exception) { return false }
            startedAt = elapsedMs()
        }
        while (true) {
            if (cancelled()) return false
            val s = state.value
            when {
                s == RuntimeState.Running -> return true
                s.isBusyOrRunning -> sawProgress = true
                // NotInstalled, Stopped or Error:
                sawProgress -> return false
                startedAt >= 0 && (s !== initial || elapsedMs() - startedAt > graceMs) -> return false
                startedAt < 0 -> return false   // never started by us and nothing is progressing
            }
            if (elapsedMs() >= timeoutMs) return false
            try { Thread.sleep(pollMs) } catch (_: InterruptedException) { return false }
        }
    }
}
