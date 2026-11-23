package dev.pocketagent.app.ui.pixel

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.pocketagent.app.ui.theme.AnimLevel
import dev.pocketagent.app.ui.theme.Pocket
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** Which hand-keyed fire sprite shows at [intensity] (0 = embers only .. 1 = blazing). */
internal fun fireSpriteName(intensity: Float): String = when {
    intensity < 0.15f -> "fire_out"
    intensity < 0.45f -> "fire_low"
    intensity < 0.8f -> "fire_mid"
    else -> "fire_high"
}

/**
 * The Newroz fire: flames on a stone ring, hand-keyed frames (fire.pxl, 32x36 px). [intensity] picks
 * between embers only, a small fire, a good fire and a blaze. Animation follows Pocket.anim.
 */
@Composable
fun Fire(modifier: Modifier = Modifier, intensity: Float = 1f) {
    PixelImage(fireSpriteName(intensity), modifier)
}

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
 * Draws the first [count] embers of [set] at time [t] ms. Cells are [unit] device pixels; coordinates are
 * snapped to that grid. Embers spawn along a line of +-[halfWidthCells] around [originX] at [originY] and
 * rise [riseCells] cells, sway, drift, cool from gold to red and fade out; some flicker off for a moment.
 */
internal fun DrawScope.drawEmbers(
    set: EmberSet, count: Int, t: Long, unit: Float,
    originX: Float, originY: Float, halfWidthCells: Float, riseCells: Float,
    high: Color, mid: Color, low: Color, strength: Float = 1f,
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
        val cells = if (set.big[i] && u < 0.5f) 2f else 1f
        px(base.copy(alpha = a), originX + dx * unit, originY - rise * unit, unit * cells, unit * cells)
    }
}

/**
 * Embers overlay: 1-2 cell squares rising from the bottom edge, snapped to a 3 dp grid, flickering,
 * drifting, in the theme's fire colours. Draws nothing unless animations are FULL. Cheap: a fixed pool,
 * one clock, no allocation per frame. [density] scales the number of particles (1 = about 28).
 */
@Composable
fun Embers(modifier: Modifier = Modifier, density: Float = 1f) {
    val anim = Pocket.anim
    val c = Pocket.colors
    val clock = rememberClock(anim == AnimLevel.FULL)
    val set = remember { EmberSet(40, 7L) }
    val unit = with(LocalDensity.current) { 3.dp.toPx().roundToInt().coerceAtLeast(2).toFloat() }
    Spacer(
        modifier.drawBehind {
            if (anim != AnimLevel.FULL) return@drawBehind
            val count = (28f * density).roundToInt().coerceIn(0, set.capacity)
            drawEmbers(
                set, count, clock.longValue, unit,
                originX = size.width / 2f, originY = size.height - unit,
                halfWidthCells = size.width / unit / 2f * 0.9f, riseCells = size.height / unit * 0.92f,
                high = c.fireHigh, mid = c.fireMid, low = c.fireLow,
            )
        },
    )
}
