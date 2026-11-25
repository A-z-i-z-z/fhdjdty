package dev.pocketagent.app.service

import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * How hard the agent is working, as the fire sees it. [heat] is the stored warmth (0..1) as it was at
 * [heatAtMs]; it halves every [FireMath.HALF_LIFE_MS]. [turns] holds the conversations that have a turn open
 * and [tools] the running tool calls (keys `"conv|callId"`); while either set is not empty the fire never
 * falls under a floor (see [FireMath.floorOf]). Pure data: no clock, no Android.
 */
data class FireFeedState(
    val heat: Float = 0f,
    val heatAtMs: Long = 0L,
    val tools: Set<String> = emptySet(),
    val turns: Set<String> = emptySet(),
)

/**
 * The maths of the feed: one table of weights and pure transitions from a state to the next state. Every
 * function takes the time as a parameter. Removing a floor (a tool finished, a turn ended) first bakes the
 * level of the old state into [FireFeedState.heat], so the fire eases down from where it was.
 *
 * Reference values: 0.70 after 9 s is 0.35; 0.70 after 60 s is 0.0069, which is under [CUT] and so exactly 0.
 */
object FireMath {
    const val HALF_LIFE_MS = 9_000L
    const val CUT = 0.01f
    const val TURN_FLOOR = 0.35f
    const val TOOL_FLOOR = 0.90f
    const val TEXT_CAP = 0.70f
    const val TEXT_STEP = 0.03f
    const val START_KICK = 0.55f
    const val TOOL_KICK = 0.90f

    /** heat * 2^(-age / half-life); an age of 0 or less returns [heat]; a result under [CUT] returns 0. */
    fun decayed(heat: Float, ageMs: Long): Float {
        if (ageMs <= 0L) return heat
        val r = (heat.toDouble() * 2.0.pow(-ageMs.toDouble() / HALF_LIFE_MS)).toFloat()
        return if (r < CUT) 0f else r
    }

    /** The level the fire may not fall under: a running tool, else an open turn, else nothing. */
    fun floorOf(s: FireFeedState): Float = when {
        s.tools.isNotEmpty() -> TOOL_FLOOR
        s.turns.isNotEmpty() -> TURN_FLOOR
        else -> 0f
    }

    /** The level at [nowMs], 0..1: the larger of the floor and the decayed heat. */
    fun levelAt(s: FireFeedState, nowMs: Long): Float =
        max(floorOf(s), decayed(s.heat, nowMs - s.heatAtMs)).coerceIn(0f, 1f)

    /**
     * The first millisecond at which the level is 0 for good: null while a floor holds, [FireFeedState.heatAtMs]
     * when the heat is already under [CUT], else the moment the decayed heat drops under [CUT].
     */
    fun settledAtMs(s: FireFeedState): Long? {
        if (floorOf(s) > 0f) return null
        if (s.heat < CUT) return s.heatAtMs
        val exact = HALF_LIFE_MS * ln(s.heat.toDouble() / CUT.toDouble()) / ln(2.0)
        var at = s.heatAtMs + ceil(exact).toLong()
        // float rounding at the very edge: step on until the decay really reads 0 (a few iterations at most)
        var guard = 0
        while (decayed(s.heat, at - s.heatAtMs) >= CUT && guard < 8) {
            at++
            guard++
        }
        return at
    }

    /** A turn of [conv] starts: heat to at least [START_KICK], and [conv] holds the turn floor. */
    fun turnStarted(s: FireFeedState, conv: String, nowMs: Long): FireFeedState {
        val d = decayed(s.heat, nowMs - s.heatAtMs)
        return s.copy(heat = max(d, START_KICK), heatAtMs = nowMs, turns = s.turns + conv)
    }

    /**
     * A piece of text (an answer delta or a thought summary) arrived: opens the turn (idempotent) and adds
     * [TEXT_STEP] to the heat, never above [TEXT_CAP] through text; a heat already at or above the cap is left
     * alone, so it goes on decaying.
     */
    fun textArrived(s: FireFeedState, conv: String, nowMs: Long): FireFeedState {
        val turns = if (conv in s.turns) s.turns else s.turns + conv
        val d = decayed(s.heat, nowMs - s.heatAtMs)
        if (d >= TEXT_CAP) return if (turns === s.turns) s else s.copy(turns = turns)
        return s.copy(heat = min(TEXT_CAP, d + TEXT_STEP), heatAtMs = nowMs, turns = turns)
    }

    /** A tool call is running (a repeated running event only kicks again): heat to at least [TOOL_KICK], floor. */
    fun toolStarted(s: FireFeedState, conv: String, callId: String, nowMs: Long): FireFeedState {
        val d = decayed(s.heat, nowMs - s.heatAtMs)
        return s.copy(heat = max(d, TOOL_KICK), heatAtMs = nowMs, tools = s.tools + toolKey(conv, callId))
    }

    /** A tool call is over: the floor goes, the level it gave is baked into the heat. No-op for an unknown call. */
    fun toolFinished(s: FireFeedState, conv: String, callId: String, nowMs: Long): FireFeedState {
        val key = toolKey(conv, callId)
        if (key !in s.tools) return s
        return s.copy(heat = levelAt(s, nowMs), heatAtMs = nowMs, tools = s.tools - key)
    }

    /**
     * The turn of [conv] is over (answer done, cancelled or failed): its turn floor and every tool key that
     * starts with `"conv|"` go, the level they gave is baked into the heat. Other conversations are untouched.
     */
    fun turnEnded(s: FireFeedState, conv: String, nowMs: Long): FireFeedState {
        val prefix = "$conv|"
        val hadTurn = conv in s.turns
        val keptTools = s.tools.filterNot { it.startsWith(prefix) }.toSet()
        if (!hadTurn && keptTools.size == s.tools.size) return s
        return s.copy(heat = levelAt(s, nowMs), heatAtMs = nowMs, turns = s.turns - conv, tools = keptTools)
    }

    /** Raises the heat to at least [level] (0..1); never lowers it. A NaN is ignored. */
    fun kick(s: FireFeedState, level: Float, nowMs: Long): FireFeedState {
        if (level.isNaN()) return s
        val d = decayed(s.heat, nowMs - s.heatAtMs)
        return s.copy(heat = max(d, level.coerceIn(0f, 1f)), heatAtMs = nowMs)
    }

    /** The key of a tool call in [FireFeedState.tools]. */
    fun toolKey(conv: String, callId: String): String = "$conv|$callId"
}
