package dev.pocketagent.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The live feed behind the fire: "how hard is the agent working", held as a [FireFeedState] that changes only
 * on events (a turn starts, text arrives, a tool runs or ends), never on the clock. The level at any moment is
 * a pure function of the state and the time ([FireMath.levelAt]), so a reader asks [level] in its draw
 * callback and nothing ticks here.
 *
 * Thread safe: every mutation runs under one short lock and publishes the new state and the tool count
 * together, so [toolsRunning] always agrees with [state]. [clock] gives milliseconds on any monotonic
 * time base; tests bring their own.
 */
class FireFeedCore(private val clock: () -> Long) {
    private val lock = Any()
    private val mutableState = MutableStateFlow(FireFeedState())
    private val mutableTools = MutableStateFlow(0)

    @Volatile private var pinned: Float? = null

    /** The state; it changes only on events. */
    val state: StateFlow<FireFeedState> = mutableState.asStateFlow()

    /** The number of tool keys in [state], never negative; updated together with [state]. */
    val toolsRunning: StateFlow<Int> = mutableTools.asStateFlow()

    /** The level now, 0..1: the pin when one is set, else the decay maths on the current state. */
    fun level(): Float = pinned ?: FireMath.levelAt(mutableState.value, clock())

    fun turnStarted(convId: String) = mutate { FireMath.turnStarted(it, convId, clock()) }
    fun textArrived(convId: String) = mutate { FireMath.textArrived(it, convId, clock()) }
    fun toolStarted(convId: String, callId: String) = mutate { FireMath.toolStarted(it, convId, callId, clock()) }
    fun toolFinished(convId: String, callId: String) = mutate { FireMath.toolFinished(it, convId, callId, clock()) }
    fun turnEnded(convId: String) = mutate { FireMath.turnEnded(it, convId, clock()) }
    fun kick(level: Float) = mutate { FireMath.kick(it, level, clock()) }

    /** Makes the function level return [level] from now on (in memory, for tests and the debug link); null releases it. */
    fun pin(level: Float?) {
        pinned = if (level == null || level.isNaN()) null else level.coerceIn(0f, 1f)
    }

    /** Back to an empty state and no pin. */
    fun reset() {
        synchronized(lock) {
            mutableState.value = FireFeedState()
            mutableTools.value = 0
        }
        pinned = null
    }

    private inline fun mutate(transition: (FireFeedState) -> FireFeedState) {
        synchronized(lock) {
            val before = mutableState.value
            val after = transition(before)
            if (after != before) {
                mutableState.value = after
                mutableTools.value = after.tools.size
            }
        }
    }
}

/** The one feed of the process: the chat engine writes it, the fire and Pet read it. */
object FireFeed {
    val core: FireFeedCore = FireFeedCore { System.nanoTime() / 1_000_000L }

    val state: StateFlow<FireFeedState> get() = core.state
    val toolsRunning: StateFlow<Int> get() = core.toolsRunning

    fun level(): Float = core.level()

    fun turnStarted(convId: String) = core.turnStarted(convId)
    fun textArrived(convId: String) = core.textArrived(convId)
    fun toolStarted(convId: String, callId: String) = core.toolStarted(convId, callId)
    fun toolFinished(convId: String, callId: String) = core.toolFinished(convId, callId)
    fun turnEnded(convId: String) = core.turnEnded(convId)
    fun kick(level: Float) = core.kick(level)
    fun pin(level: Float?) = core.pin(level)
    fun reset() = core.reset()
}
