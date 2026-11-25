package dev.pocketagent.app.ui.pixel.stage

import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** One precomputed glow image: [px] is [w] x [h] ARGB, its centre is [halfW] and [halfH] from the top-left. */
class GlowImage(val px: IntArray, val w: Int, val h: Int, val halfW: Int, val halfH: Int)

/** The star field of one scene size: positions, phases and twinkle rates (parallel arrays). */
class StarField(
    val x: IntArray, val y: IntArray, val phase: FloatArray, val rate: FloatArray,
)

/**
 * The procedural parts of the home scene that need no sprite: the dithered night sky, the stars, the glow
 * around the fire and the 4x4 Bayer matrix they are dithered with. Pure functions of their arguments.
 */
object SceneArt {
    const val GLOW_LEVELS = 8

    private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    /** Ordered-dither threshold of pixel (x, y), between 0 and 1. */
    fun bayer(x: Int, y: Int): Float = (BAYER[((y and 3) shl 2) or (x and 3)] + 0.5f) / 16f

    /** The sky as [w] x [h] opaque ARGB: nine colour bands from [top] to [bottom], dithered between bands. */
    fun sky(w: Int, h: Int, top: Int, bottom: Int): IntArray {
        val n = 9
        val cols = IntArray(n) { Argb.lerp(top, bottom, it / (n - 1f)) }
        val px = IntArray(w * h)
        for (y in 0 until h) {
            val tt = y / (h - 1).coerceAtLeast(1).toFloat()
            val f = tt.pow(1.15f) * (n - 1)
            val i0 = f.toInt()
            val frac = f - i0
            for (x in 0 until w) {
                val up = frac > 0.55f && (frac - 0.55f) / 0.45f > bayer(x, y)
                px[y * w + x] = cols[min(n - 1, i0 + if (up) 1 else 0)]
            }
        }
        return px
    }

    /** 46 stars over a scene [w] wide whose ground starts at logical row [g]; the same seed every time. */
    fun stars(w: Int, g: Int): StarField {
        val n = 46
        val r = java.util.Random(4)
        val x = IntArray(n)
        val y = IntArray(n)
        val phase = FloatArray(n)
        val rate = FloatArray(n)
        val maxY = (g * 0.72f).toInt().coerceAtLeast(6)
        for (k in 0 until n) {
            x[k] = 2 + r.nextInt((w - 4).coerceAtLeast(1))
            y[k] = 2 + r.nextInt(maxY - 2)
            phase[k] = r.nextFloat() * 6.2832f
            rate[k] = 0.6f + (k % 5) * 0.2f
        }
        return StarField(x, y, phase, rate)
    }

    /** Quantised warm glow: dithered bands of [color], bigger for higher [level] (0..7). */
    fun glow(level: Int, color: Int): GlowImage {
        val scale = 0.35f + 0.65f * level / (GLOW_LEVELS - 1)
        val rx = (58f * scale).roundToInt()
        val ry = (30f * scale).roundToInt()
        val w = 2 * rx + 1
        val h = 2 * ry + 1
        val levels = floatArrayOf(0f, 0.07f, 0.14f, 0.24f, 0.36f)
        val rgb = color and 0xFFFFFF
        val px = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val dx = (x - rx) / rx.toFloat()
                val dy = (y - ry) / ry.toFloat()
                val d = sqrt(dx * dx + dy * dy)
                if (d >= 1f) continue
                val f = (1f - d) * 4f
                val i0 = f.toInt()
                val lv = min(4, i0 + if (f - i0 > bayer(x, y)) 1 else 0)
                val a = (levels[lv] * 255f).roundToInt()
                px[y * w + x] = (a shl 24) or rgb
            }
        }
        return GlowImage(px, w, h, rx, ry)
    }
}
