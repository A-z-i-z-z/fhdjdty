package dev.pocketagent.app.ui.pixel.stage

import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pre-computed constants of a pool of embers. Every particle is a pure function of the clock, so drawing
 * needs no mutable state and allocates nothing.
 */
internal class EmberSet(val capacity: Int, seed: Long) {
    val spawnX = FloatArray(capacity)
    val periodMs = FloatArray(capacity)
    val phase = FloatArray(capacity)
    val swayAmp = FloatArray(capacity)
    val swayFreq = FloatArray(capacity)
    val drift = FloatArray(capacity)
    val big = BooleanArray(capacity)

    init {
        val r = java.util.Random(seed)
        for (i in 0 until capacity) {
            spawnX[i] = r.nextFloat() * 2f - 1f
            periodMs[i] = 2200f + r.nextFloat() * 2800f
            phase[i] = r.nextFloat()
            swayAmp[i] = 1.5f + r.nextFloat() * 3.5f
            swayFreq[i] = 0.8f + r.nextFloat() * 1.4f
            drift[i] = r.nextFloat() * 1.6f - 0.5f
            big[i] = r.nextFloat() < 0.22f
        }
    }
}

/**
 * Draws the first [count] embers of [set] at time [t] ms onto the stage, one stage pixel per cell. Embers
 * spawn along a line of +-[halfWidthCells] around [originX] at [originY] and rise [riseCells] cells, sway,
 * drift, cool from [high] to [low] and fade out; some flicker off for a moment. The same maths as the
 * DrawScope version in Fire.kt, at a cell size of 1.
 */
internal fun PixelStage.drawEmbers(
    set: EmberSet, count: Int, t: Long,
    originX: Float, originY: Float, halfWidthCells: Float, riseCells: Float,
    high: Int, mid: Int, low: Int, strength: Float = 1f,
) {
    val n = if (count < set.capacity) count else set.capacity
    for (i in 0 until n) {
        if (((t / 70L + i * 5L) % 9L).toInt() == 0) continue
        val u = ((t.toFloat() / set.periodMs[i]) + set.phase[i]) % 1f
        val rise = floor(u * riseCells)
        val sway = sin(u * 6.2832f * set.swayFreq[i] + set.phase[i] * 6.2832f) * set.swayAmp[i] * (0.3f + u)
        val dx = floor(set.spawnX[i] * halfWidthCells + sway + set.drift[i] * u * riseCells * 0.25f)
        var a = strength
        if (u > 0.7f) a *= (1f - u) / 0.3f
        if (u < 0.06f) a *= u / 0.06f
        if (a <= 0.02f) continue
        val base = if (u < 0.3f) high else if (u < 0.65f) mid else low
        val cells = if (set.big[i] && u < 0.5f) 2 else 1
        val color = Argb.withAlpha(base, a)
        val x = (originX + dx).roundToInt()
        val y = (originY - rise).roundToInt()
        if (cells == 1) plot(x, y, color) else fillRect(x, y, cells, cells, color)
    }
}
