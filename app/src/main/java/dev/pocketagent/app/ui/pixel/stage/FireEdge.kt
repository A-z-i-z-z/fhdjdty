package dev.pocketagent.app.ui.pixel.stage

import dev.pocketagent.app.ui.pixel.Sprite
import dev.pocketagent.app.ui.pixel.SpriteRaster
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Where the flame of one sprite frame is, found by scanning rows 0..22 for the flame letters `g r f m y c`
 * of `fire.pxl` (the stones and logs use other letters).
 *
 *  - [top] has one entry per column: the first row whose flame pixel has a flame pixel in the next one or two
 *    rows of the same column (a lone hand-keyed spark pixel does not count), or -1.
 *  - [left] and [right] have one entry per sprite row: the first and the last flame column of that row, or -1
 *    (rows below 22 are always -1).
 */
class FlameShape private constructor(val top: IntArray, val left: IntArray, val right: IntArray) {
    /** The first row of the flame over all columns, or -1 when the frame has no flame. */
    val firstRow: Int = top.filter { it >= 0 }.minOrNull() ?: -1

    companion object {
        /** Rows 0..22 are scanned. */
        const val ROWS = 23

        /** The palette letters that make up the flame. */
        const val FLAME_LETTERS = "grfmyc"

        /** Scans [frame] of [sprite]; allocates the three arrays, so callers keep the result (see [FlameShapes]). */
        fun of(sprite: Sprite, frame: Int): FlameShape {
            val w = sprite.width
            val h = sprite.height
            val indices = sprite.frames[frame].indices
            val isFlameIndex = BooleanArray(sprite.palette.size) { FLAME_LETTERS.indexOf(sprite.palette[it].char) >= 0 }
            val flame = BooleanArray(w * h) { val i = indices[it]; i >= 0 && isFlameIndex[i] }
            val top = IntArray(w) { -1 }
            val left = IntArray(h) { -1 }
            val right = IntArray(h) { -1 }
            val rows = if (h < ROWS) h else ROWS
            for (y in 0 until rows) {
                for (x in 0 until w) {
                    if (!flame[y * w + x]) continue
                    if (left[y] < 0) left[y] = x
                    right[y] = x
                    if (top[x] < 0) {
                        val below1 = y + 1 < h && flame[(y + 1) * w + x]
                        val below2 = y + 2 < h && flame[(y + 2) * w + x]
                        if (below1 || below2) top[x] = y
                    }
                }
            }
            return FlameShape(top, left, right)
        }
    }
}

/** A cache of [FlameShape]s per sprite and frame; one per layer, so no global state. */
internal class FlameShapes {
    private val shapes = IdentityHashMap<Sprite, Array<FlameShape?>>()

    fun get(sprite: Sprite, frame: Int): FlameShape {
        val perFrame = shapes.getOrPut(sprite) { arrayOfNulls<FlameShape>(sprite.frames.size) }
        val have = perFrame[frame]
        if (have != null) return have
        val shape = FlameShape.of(sprite, frame)
        perFrame[frame] = shape
        return shape
    }
}

/** Seeded value noise of the living edge: integers in, floats out, no state. */
object FireNoise {
    /** An integer hash of ([a], [b], [seed]) in 0..1. */
    fun hash01(a: Int, b: Int, seed: Int): Float {
        var h = a * 374761393 + b * 668265263 + seed * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFF) / 65535f
    }

    /**
     * Smooth noise in 0..1 along time: a smoothstep blend of the hashes of the cell [tMs] / [periodMs] and the
     * next one.
     */
    fun value(x: Int, tMs: Long, periodMs: Int, seed: Int): Float {
        val period = periodMs.toLong()
        val k = Math.floorDiv(tMs, period)
        val f = Math.floorMod(tMs, period).toFloat() / periodMs
        val s = f * f * (3f - 2f * f)
        val a = hash01(x, k.toInt(), seed)
        val b = hash01(x, k.toInt() + 1, seed)
        return a + (b - a) * s
    }
}

/**
 * The living edge of the flame (seeded tongues above it, licks at its sides) and the wind lean of the flame
 * sprite. Pure functions of the time, the shape and the wind; nothing is allocated.
 *
 * The edge never plots over a pixel where the sprite is opaque: it is given the raster of the sprite frame
 * and skips every pixel the (leaning) sprite covers, so the order of drawing does not matter.
 */
internal object FireEdge {
    // the palette lines g, r, f, m of fire.pxl (a test compares them with the file)
    val COL_G: Int = 0xFFA81F1A.toInt()
    val COL_R: Int = 0xFFDC3818.toInt()
    val COL_F: Int = 0xFFF7691A.toInt()
    val COL_M: Int = 0xFFFFA726.toInt()

    const val TONGUE_PERIOD_MS = 260
    const val LICK_PERIOD_MS = 200
    private const val MAX_TONGUE_BASE = 4

    /** The longest tongue at [edge] (0..1): `1 + round(4 * edge)` cells. */
    fun maxTongue(edge: Float): Int = 1 + (MAX_TONGUE_BASE * edge.coerceIn(0f, 1f)).roundToInt()

    /**
     * Draws the edge of [shape] (a frame of the 32 by 36 sprite whose top-left is ([originX], [originY]) on the
     * stage) at time [timeMs]. [raster] is that frame's plain pixels (width [spriteW], height [spriteH]), used
     * to leave covered pixels alone; [wind] is the lean of the sprite, so the edge leans with it.
     * Draws nothing for an [edge] of 0 or less.
     */
    fun draw(
        stage: PixelStage, shape: FlameShape, raster: IntArray, spriteW: Int, spriteH: Int,
        originX: Int, originY: Int, timeMs: Long, edge: Float, wind: Float,
    ) {
        if (edge.isNaN() || edge <= 0f) return
        val e = edge.coerceAtMost(1f)
        val maxLen = maxTongue(e)
        val centre = (shape.top.size - 1) / 2f
        for (x in shape.top.indices) {
            val top = shape.top[x]
            if (top < 0) continue
            val n = FireNoise.value(x shr 1, timeMs, TONGUE_PERIOD_MS, FireTuning.SEED_TONGUE)
            val prof = (1f - abs(x - centre) / 9f).coerceIn(0.15f, 1f)
            val len = floor(n * prof * (maxLen + 0.999f)).toInt()
            for (k in 1..len) {
                val row = top - k
                val px = originX + x + FireWind.shift(if (row < 0) 0 else row, wind)
                val py = originY + row
                if (covered(raster, spriteW, spriteH, px - originX, row, wind)) continue
                if (k == 1) {
                    stage.plot(px, py, COL_F)
                } else if (k == len) {
                    if (SceneArt.bayer(px, py) < 0.7f) stage.plot(px, py, Argb.withAlpha(COL_G, 0.75f))
                } else {
                    stage.plot(px, py, COL_R)
                }
            }
        }
        if (FireTuning.SIDE_LICKS && shape.firstRow >= 0) {
            val threshold = 0.68f - 0.25f * e
            val last = if (spriteH - 1 < FlameShape.ROWS - 1) spriteH - 1 else FlameShape.ROWS - 1
            for (row in shape.firstRow..last) {
                val l = shape.left[row]
                if (l < 0) continue
                val r = shape.right[row]
                val dx = FireWind.shift(row, wind)
                val nl = FireNoise.value(40 + row, timeMs, LICK_PERIOD_MS, FireTuning.SEED_LICK_LEFT)
                if (nl > threshold) {
                    val px = originX + l - 1 + dx
                    if (!covered(raster, spriteW, spriteH, px - originX, row, wind)) {
                        stage.plot(px, originY + row, Argb.withAlpha(COL_G, 0.8f))
                    }
                }
                val nr = FireNoise.value(40 + row, timeMs, LICK_PERIOD_MS, FireTuning.SEED_LICK_RIGHT)
                if (nr > threshold) {
                    val px = originX + r + 1 + dx
                    if (!covered(raster, spriteW, spriteH, px - originX, row, wind)) {
                        stage.plot(px, originY + row, Argb.withAlpha(COL_R, 0.8f))
                    }
                }
            }
        }
    }

    /**
     * Draws one frame of [sprite] at ([x], [y]) with its rows 0..22 shifted sideways by [FireWind.shift]. When
     * no row shifts (wind under 0.1, or one that rounds to 0) it is exactly the plain `stage.blit`.
     */
    fun blitLeaned(stage: PixelStage, sprite: Sprite, frame: Int, x: Int, y: Int, wind: Float) {
        if (FireWind.shift(0, wind) == 0) {
            stage.blit(sprite, frame, x, y)
            return
        }
        val raster = SpriteRaster.plain(sprite, frame)
        val w = sprite.width
        for (row in 0 until sprite.height) {
            val dx = FireWind.shift(row, wind)
            val rowStart = row * w
            for (col in 0 until w) {
                val c = raster[rowStart + col]
                if ((c ushr 24) != 0) stage.plot(x + col + dx, y + row, c)
            }
        }
    }

    /**
     * True when the sprite, leaning with [wind], has an opaque-or-translucent pixel at ([cx], [cy]) relative to
     * the sprite's top-left: the pixel that lands there is the one [FireWind.shift] columns to the left.
     */
    private fun covered(raster: IntArray, w: Int, h: Int, cx: Int, cy: Int, wind: Float): Boolean {
        if (cy < 0 || cy >= h) return false
        val sx = cx - FireWind.shift(cy, wind)
        if (sx < 0 || sx >= w) return false
        return (raster[cy * w + sx] ushr 24) != 0
    }
}
