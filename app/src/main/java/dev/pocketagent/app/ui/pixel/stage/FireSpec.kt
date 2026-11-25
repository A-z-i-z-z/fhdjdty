package dev.pocketagent.app.ui.pixel.stage

import dev.pocketagent.app.ui.pixel.HearthState
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A light-up or a put-out of the fire in progress. [startMs] is on the scene clock; [frozenElapsedMs] pins the
 * elapsed time (the debug link shows a transition frozen at a fraction of its length).
 */
data class FireTransition(val kind: Kind, val startMs: Long, val frozenElapsedMs: Long? = null) {
    enum class Kind { LIGHT, DOUSE }

    /** Milliseconds since the start, never negative; the frozen value when there is one. */
    fun elapsed(timeMs: Long): Long = frozenElapsedMs ?: max(0L, timeMs - startMs)

    /** True while the transition has not run its length yet (900 ms to light, 700 ms to put out). */
    fun active(timeMs: Long): Boolean {
        val length = when (kind) {
            Kind.LIGHT -> FireTuning.LIGHT_MS
            Kind.DOUSE -> FireTuning.DOUSE_MS
        }
        return elapsed(timeMs) < length
    }
}

/**
 * What the scene needs to know about the living fire besides the hearth state. [level] 0..1 comes from the
 * feed, [wind] -1..1 is positive towards the right of the screen, [holdProgress] 0..1 is the ring of a held
 * finger. A NaN is read as 0. With [classic] true everything else is ignored and the picture is the old one.
 */
data class FireParams(
    val classic: Boolean = true,
    val level: Float = 0f,
    val wind: Float = 0f,
    val holdProgress: Float = 0f,
    val transition: FireTransition? = null,
) {
    companion object {
        /** Today's fire. */
        val CLASSIC: FireParams = FireParams()
    }
}

/** The five fire sprites of `fire.pxl`; [spriteName] is the name the sprite source knows. */
enum class FireSprite(val spriteName: String) {
    COLD("fire_cold"), OUT("fire_out"), LOW("fire_low"), MID("fire_mid"), HIGH("fire_high"),
}

/**
 * The result of [FireResolve.resolve]: one reusable holder, so the draw path allocates nothing.
 * [intensity] drives the glow, [edge] 0..1 the living edge, [level] the effective work level, [boost] the
 * stoke and celebration boost (0..1.5), [sparkBase] the number of sparks before the boost is added.
 */
class FireSpec {
    var sprite: FireSprite = FireSprite.COLD
    var intensity: Float = 0f
    var edge: Float = 0f
    var level: Float = 0f
    var boost: Float = 0f
    var sparkBase: Int = 0
}

/** Every number of the living fire in one place. The taste numbers are [HIGH_AT], [IDLE_INTENSITY], [SIDE_LICKS]. */
object FireTuning {
    const val LIGHT_MS = 900L
    const val DOUSE_MS = 700L
    const val HOLD_MS = 700L

    /** From this effective level on a burning fire shows `fire_high`, under it `fire_mid`. */
    const val HIGH_AT = 0.30f

    /** The intensity of a burning fire with an idle agent (level 0); it rises to 1 at level 1. */
    const val IDLE_INTENSITY = 0.70f

    /** The most a spark drifts sideways in its life at wind 1 and gain 1, in cells. */
    const val SPARK_WIND_CELLS = 12f

    /** The flame rows 0..SHEAR_ROWS lean with the wind; the tip (row 0) leans most. */
    const val SHEAR_ROWS = 22
    const val SHEAR_MAX_PX = 3f

    const val RING_DOTS = 20
    const val RING_RX = 21
    const val RING_RY = 13

    /** The ring is centred this many rows above the hearth base. */
    const val RING_CY_UP = 14

    /** Seeds of the living edge: tongues, left licks, right licks. */
    const val SEED_TONGUE = 11
    const val SEED_LICK_LEFT = 23
    const val SEED_LICK_RIGHT = 29

    /** Side licks along the flame: LOOK may switch them off. */
    const val SIDE_LICKS = true

    /** The spark pool of the living fire (the classic fire keeps today's embers). */
    const val SPARK_CAPACITY = 32
    const val SPARK_SEED = 31L
}

/**
 * Turns the hearth state, the work level and the transitions into the sprite, intensity, edge and sparks of
 * the fire. Pure: the same inputs give the same answer, and [resolve] allocates nothing.
 *
 * Classic ([FireParams.classic]) is today's table exactly. The living fire (all rows of the table in the
 * contract): COLD cold; KINDLING the same sprites and intensity as today plus an edge from 15 percent;
 * BLAZING `fire_mid` at rest and `fire_high` from [FireTuning.HIGH_AT], intensity 0.70 to 1, edge 0.25 to 1;
 * SMOKE as today; a LIGHT or DOUSE transition replaces all of it while it runs.
 */
object FireResolve {
    private const val SMOKE_SLOT_MS = 170L

    fun resolve(
        out: FireSpec, hearth: HearthState, progress: Float?, stokeBoost: Float, celebration: Boolean,
        fire: FireParams, timeMs: Long,
    ): FireSpec {
        val stoke = stokeOf(stokeBoost)
        val classic = fire.classic
        val leff = if (classic) 0f else leffOf(fire.level, stoke)
        var boost = (stoke + if (celebration) 0.5f else 0f).coerceIn(0f, 1.5f)
        val intensity = intensityOf(hearth, progress, stokeBoost, celebration, fire, timeMs)
        val tr = activeTransition(fire, timeMs)

        var sprite = FireSprite.COLD
        var edge = 0f
        var sparkBase = 0
        if (tr != null) {
            val e = tr.elapsed(timeMs)
            when (tr.kind) {
                FireTransition.Kind.LIGHT -> {
                    val f = e.toFloat() / FireTuning.LIGHT_MS
                    sprite = LIGHT_STEPS[(e / 225L).toInt().coerceIn(0, 3)]
                    edge = 0.8f * f
                    boost = max(boost, 0.8f * (1f - f))
                }
                FireTransition.Kind.DOUSE -> {
                    val f = e.toFloat() / FireTuning.DOUSE_MS
                    sprite = DOUSE_STEPS[(e / 175L).toInt().coerceIn(0, 3)]
                    edge = 0.8f * (1f - f)
                }
            }
            sparkBase = (4f + 16f * intensity).roundToInt()
        } else {
            when (hearth) {
                HearthState.COLD -> {
                    sprite = FireSprite.COLD
                    sparkBase = (3f + 21f * intensity).roundToInt()
                }
                HearthState.KINDLING -> {
                    val pr = progressOf(progress)
                    sprite = kindlingSprite(pr)
                    if (!classic && pr >= 0.15f) edge = 0.6f * pr
                    sparkBase = (3f + 21f * intensity).roundToInt()
                }
                HearthState.BLAZING -> {
                    if (classic) {
                        sprite = FireSprite.HIGH
                        sparkBase = (3f + 21f * intensity).roundToInt()
                    } else {
                        sprite = if (leff >= FireTuning.HIGH_AT) FireSprite.HIGH else FireSprite.MID
                        edge = 0.25f + 0.75f * leff
                        sparkBase = (4f + 16f * leff).roundToInt()
                    }
                }
                HearthState.SMOKE -> {
                    sprite = if (smokeOn(timeMs)) FireSprite.LOW else FireSprite.OUT
                    sparkBase = 4
                }
            }
        }
        out.sprite = sprite
        out.intensity = intensity
        out.edge = edge.coerceIn(0f, 1f)
        out.level = leff
        out.boost = boost
        out.sparkBase = sparkBase
        return out
    }

    /** The [FireSpec.intensity] that [resolve] gives for the same arguments (it feeds Pet's fire level). */
    fun intensityOf(
        hearth: HearthState, progress: Float?, stokeBoost: Float, celebration: Boolean,
        fire: FireParams, timeMs: Long,
    ): Float {
        val tr = activeTransition(fire, timeMs)
        if (tr != null) {
            val e = tr.elapsed(timeMs).toFloat()
            return when (tr.kind) {
                FireTransition.Kind.LIGHT -> 0.15f + 0.75f * (e / FireTuning.LIGHT_MS)
                FireTransition.Kind.DOUSE -> 0.9f - 0.8f * (e / FireTuning.DOUSE_MS)
            }
        }
        return when (hearth) {
            HearthState.COLD -> 0f
            HearthState.KINDLING -> 0.18f + 0.72f * progressOf(progress)
            HearthState.BLAZING ->
                if (fire.classic) 1f
                else FireTuning.IDLE_INTENSITY + (1f - FireTuning.IDLE_INTENSITY) * leffOf(fire.level, stokeOf(stokeBoost))
            HearthState.SMOKE -> if (smokeOn(timeMs)) 0.3f else 0.1f
        }
    }

    private val LIGHT_STEPS = arrayOf(FireSprite.OUT, FireSprite.LOW, FireSprite.MID, FireSprite.HIGH)
    private val DOUSE_STEPS = arrayOf(FireSprite.HIGH, FireSprite.MID, FireSprite.LOW, FireSprite.OUT)

    private fun activeTransition(fire: FireParams, timeMs: Long): FireTransition? {
        if (fire.classic) return null
        val tr = fire.transition ?: return null
        return if (tr.active(timeMs)) tr else null
    }

    private fun stokeOf(stokeBoost: Float): Float = if (stokeBoost.isNaN()) 0f else stokeBoost.coerceIn(0f, 1.5f)

    /** The effective level: the work level, lifted by a stoke (a full stoke gives 0.9). NaN level counts as 0. */
    private fun leffOf(level: Float, stoke: Float): Float {
        val l = if (level.isNaN()) 0f else level.coerceIn(0f, 1f)
        val s = minOf(1f, stoke / 1.5f) * 0.9f
        return max(l, s)
    }

    private fun progressOf(progress: Float?): Float = (progress ?: 0.5f).let { if (it.isNaN()) 0.5f else it }.coerceIn(0f, 1f)

    private fun kindlingSprite(pr: Float): FireSprite = when {
        pr < 0.15f -> FireSprite.OUT
        pr < 0.5f -> FireSprite.LOW
        pr < 0.85f -> FireSprite.MID
        else -> FireSprite.HIGH
    }

    /** The old flicker of the smoking fire: on three of five 170 ms slots in a fixed shuffled order. */
    private fun smokeOn(timeMs: Long): Boolean {
        val slot = (timeMs / SMOKE_SLOT_MS).toInt()
        return ((slot * 7 + slot / 3) % 5 + 5) % 5 < 3
    }
}
