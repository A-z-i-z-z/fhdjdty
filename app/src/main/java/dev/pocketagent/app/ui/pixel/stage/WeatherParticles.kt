package dev.pocketagent.app.ui.pixel.stage

import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * The seeded numbers behind rain, snow and lightning. Pure Kotlin, nothing allocated per frame: a DropSet holds
 * arrays made once, and every position is a function of the drop's index, the time and the stage size.
 */

/**
 * Up to [capacity] drops with a fixed seed: where each one starts (a fraction of the width), how fast it falls (a
 * fraction between the slowest and the fastest speed), its phase, its streak length (2 or 3 pixels), its sway
 * (1 to 3 pixels) and whether it is a big flake (15 per cent are 2 by 2). The same seed gives the same arrays.
 */
class DropSet(capacity: Int = 96, val seed: Long = 20261125L) {
    val capacity: Int = capacity.coerceIn(0, 4096)
    val xFrac: FloatArray = FloatArray(this.capacity)
    val speedFrac: FloatArray = FloatArray(this.capacity)
    val phase: FloatArray = FloatArray(this.capacity)
    val lengthPx: IntArray = IntArray(this.capacity)
    val swayPx: FloatArray = FloatArray(this.capacity)
    val big: BooleanArray = BooleanArray(this.capacity)

    init {
        val r = Random(seed)
        for (i in 0 until this.capacity) {
            xFrac[i] = r.nextFloat()
            speedFrac[i] = r.nextFloat()
            phase[i] = r.nextFloat()
            lengthPx[i] = 2 + r.nextInt(2)
            swayPx[i] = 1f + 2f * r.nextFloat()
            big[i] = r.nextFloat() < BIG_FRACTION
        }
    }

    /** How many of the set a layer uses on a stage [width] wide: [base] for 155 pixels, scaled and capped. */
    fun scaledCount(base: Int, width: Int): Int {
        if (base <= 0 || width <= 0) return 0
        return (base * width / REFERENCE_WIDTH).roundToInt().coerceIn(0, capacity)
    }

    /** The fall speed of drop [i] in stage pixels per second. */
    fun speedOf(i: Int, minPxPerSec: Float, maxPxPerSec: Float): Float =
        minPxPerSec + (maxPxPerSec - minPxPerSec) * speedFrac[i]

    /**
     * The row of drop [i] at [timeMs] on a fall of [span] rows (0 until span): it starts at its phase and
     * wraps. [speedMul] scales the speed (heavy rain faster, far rain slower).
     */
    fun yAt(i: Int, timeMs: Long, span: Int, minPxPerSec: Float, maxPxPerSec: Float, speedMul: Float = 1f): Int {
        if (span <= 0) return 0
        val travelled = speedOf(i, minPxPerSec, maxPxPerSec).toDouble() * speedMul * (timeMs / 1000.0) + phase[i].toDouble() * span
        return Math.floorMod(floor(travelled).toLong(), span.toLong()).toInt()
    }

    /**
     * The column of drop [i] on row [y]: its start column plus [driftPerPx] times [y] (the slant), plus a sway of
     * [swayPx] amplitude at [swayRate] radians per second when [swayRate] is above 0; wrapped into 0 until [width].
     */
    fun xAt(i: Int, width: Int, y: Int, driftPerPx: Float, timeMs: Long = 0L, swayRate: Float = 0f): Int {
        if (width <= 0) return 0
        var x = xFrac[i].toDouble() * width + driftPerPx.toDouble() * y
        if (swayRate > 0f) x += swayPx[i] * sin(phase[i] * TWO_PI + (timeMs / 1000.0) * swayRate)
        return Math.floorMod(floor(x).toLong(), width.toLong()).toInt()
    }

    private companion object {
        const val BIG_FRACTION = 0.15f
        const val REFERENCE_WIDTH = 155f
        const val TWO_PI = 6.2831855f
    }
}

/** The integer hash and the slot arithmetic of far lightning. Slots are 20 seconds long. */
internal object WeatherLightning {
    const val SLOT_MS = 20_000L
    const val FLASH_AT_MS = 4_000L
    const val BRIGHT_END_MS = 80L
    const val DIM_START_MS = 150L
    const val DIM_END_MS = 250L

    /** splitmix64 finaliser: the same on every platform. */
    fun mix(x: Long): Long {
        var z = x + -7046029254386353131L
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        return z xor (z ushr 31)
    }

    fun slotOf(timeMs: Long): Long = Math.floorDiv(timeMs, SLOT_MS)

    /** Half of the slots have a flash. */
    fun flashes(slot: Long, seed: Long): Boolean = ((mix(seed xor mix(slot)) ushr 11) % 100L) < 50L

    /** Where the flash is over the width of the stage, 0.15 to 0.85. */
    fun fraction(slot: Long, seed: Long): Float {
        val h = mix(mix(seed xor mix(slot)) xor 0x5DEECE66DL)
        return (0.15 + 0.70 * ((h ushr 11) % 10001L) / 10000.0).toFloat()
    }
}

/**
 * The frame of far lightning at [timeMs]: 0 none, 1 bright (0 to 80 ms after the flash begins), 2 dim (150 to 250
 * ms). The flash begins 4000 ms into a 20 000 ms slot, in about half of the slots, so two flashes are never closer
 * than 20 seconds; the dark gap between bright and dim is frame 0.
 */
internal fun lightningPhase(timeMs: Long, seed: Long): Int {
    val slot = WeatherLightning.slotOf(timeMs)
    if (!WeatherLightning.flashes(slot, seed)) return 0
    val local = Math.floorMod(timeMs, WeatherLightning.SLOT_MS) - WeatherLightning.FLASH_AT_MS
    return when {
        local < 0L -> 0
        local < WeatherLightning.BRIGHT_END_MS -> 1
        local < WeatherLightning.DIM_START_MS -> 0
        local < WeatherLightning.DIM_END_MS -> 2
        else -> 0
    }
}
