package dev.pocketagent.app.ui.pixel.stage

import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pre-computed constants of the sparks of the living fire: a pool like [EmberSet], but with a wind gain per
 * spark. Every spark is a pure function of the clock and the wind, so drawing needs no mutable state.
 * Ranges: [spawnX] -1..1, [periodMs] 1500..3800, [swayAmp] 0.8..2.6, [swayFreq] 0.9..2.1, [windGain]
 * 0.5..1.3, [big] for 18 percent.
 */
internal class SparkSet(val capacity: Int, seed: Long) {
    val spawnX = FloatArray(capacity)
    val periodMs = FloatArray(capacity)
    val phase = FloatArray(capacity)
    val swayAmp = FloatArray(capacity)
    val swayFreq = FloatArray(capacity)
    val windGain = FloatArray(capacity)
    val big = BooleanArray(capacity)

    init {
        val r = Random(seed)
        for (i in 0 until capacity) {
            spawnX[i] = r.nextFloat() * 2f - 1f
            periodMs[i] = 1500f + r.nextFloat() * 2300f
            phase[i] = r.nextFloat()
            swayAmp[i] = 0.8f + r.nextFloat() * 1.8f
            swayFreq[i] = 0.9f + r.nextFloat() * 1.2f
            windGain[i] = 0.5f + r.nextFloat() * 0.8f
            big[i] = r.nextFloat() < 0.18f
        }
    }
}

/**
 * Draws the first [count] sparks of [set] at time [t] ms, one stage pixel per cell. A spark of age `u` (0..1
 * of its life, `u = ((t / period) + phase) % 1`) is at
 * `x = originX + floor(spawnX * halfWidthCells + sway + FireWind.sparkDrift(wind, u, gain))` and
 * `y = originY - floor(riseCells * (1 - (1 - u)^2))`; it fades in over the first 6 percent and out over the
 * last 30, cools from [high] to [mid] at u 0.3 and to [low] at 0.65, is two cells big (if it is a big one)
 * while u is under 0.35, and flickers off for a moment now and then. Positions are a pure function of [t]
 * and [wind]: a change of wind moves a spark at most `12 * gain` cells over its life, which is why the driver
 * smooths the wind it passes in.
 */
internal fun PixelStage.drawSparks(
    set: SparkSet, count: Int, t: Long,
    originX: Float, originY: Float, halfWidthCells: Float, riseCells: Float, wind: Float,
    high: Int, mid: Int, low: Int, strength: Float = 1f,
) {
    val n = if (count < set.capacity) count else set.capacity
    for (i in 0 until n) {
        if (((t / 80L + i * 7L) % 11L).toInt() == 0) continue
        val u = ((t.toFloat() / set.periodMs[i]) + set.phase[i]) % 1f
        val inv = 1f - u
        val rise = floor(riseCells * (1f - inv * inv))
        val sway = sin(u * 6.2832f * set.swayFreq[i] + set.phase[i] * 6.2832f) * set.swayAmp[i] * (0.3f + u)
        val dx = floor(set.spawnX[i] * halfWidthCells + sway + FireWind.sparkDrift(wind, u, set.windGain[i]))
        var a = strength
        if (u > 0.7f) a *= (1f - u) / 0.3f
        if (u < 0.06f) a *= u / 0.06f
        if (a <= 0.02f) continue
        val base = if (u < 0.3f) high else if (u < 0.65f) mid else low
        val cells = if (set.big[i] && u < 0.35f) 2 else 1
        val color = Argb.withAlpha(base, a)
        val x = (originX + dx).roundToInt()
        val y = (originY - rise).roundToInt()
        if (cells == 1) plot(x, y, color) else fillRect(x, y, cells, cells, color)
    }
}

/** How many sparks the fire sends and where, from the resolved [FireSpec] numbers (one place for the layer and the tests). */
internal object FireSparkPlan {
    /** `min(capacity, sparkBase + round(boost * 14) + 14 if celebration)`. */
    fun count(sparkBase: Int, boost: Float, celebration: Boolean, capacity: Int = FireTuning.SPARK_CAPACITY): Int {
        val n = sparkBase + (boost * 14f).roundToInt() + if (celebration) 14 else 0
        return n.coerceIn(0, capacity)
    }

    /** The spawn row: 12 rows above the base, up to 14 more with the intensity. */
    fun originY(base: Int, intensity: Float): Float = (base - 12 - (14f * intensity).toInt()).toFloat()

    fun halfWidth(intensity: Float, boost: Float): Float = 4f + 3f * intensity + 3f * boost

    fun rise(intensity: Float, boost: Float): Float = 16f + 22f * intensity + 12f * boost

    fun strength(intensity: Float, boost: Float): Float = (0.55f + 0.45f * intensity + 0.25f * boost).coerceAtMost(1f)
}
