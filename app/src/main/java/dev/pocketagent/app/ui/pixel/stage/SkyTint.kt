package dev.pocketagent.app.ui.pixel.stage

import dev.pocketagent.app.ui.pixel.Sprite
import dev.pocketagent.app.ui.pixel.SpriteRaster
import kotlin.math.roundToInt

/**
 * Tints whole sprites towards a colour, so the art that was baked for the night can serve the day, and draws
 * the dithered horizon at the bottom of the stage. Pure Kotlin: no Android types.
 *
 * A tint is described by a step from 0 to 16 (the sky's `lift` quantised to 1/16): step 0 returns the baked
 * raster itself (the same array the classic picture blits, so the classic night is pixel-identical), higher
 * steps return a cached copy. A result is shared and must never be mutated.
 *
 * The cache is keyed by sprite identity, frame, step, colour and base amount, holds at most [CACHE_LIMIT]
 * entries (when it is full it starts again), and is guarded by a lock, so it is safe to call from any thread.
 */
object SkyTint {
    /** Tint colour and base amount of the near ridge (opaque RGB; the amount is multiplied by the lift). */
    const val NEAR_TINT: Int = 0x2F4A44
    const val NEAR_BASE: Float = 0.35f

    /** Tint colour and base amount of the ground. */
    const val GROUND_TINT: Int = 0x56684A
    const val GROUND_BASE: Float = 0.50f

    /** Tint colour and base amount of the foreground grass. */
    const val FORE_TINT: Int = 0x3A4A34
    const val FORE_BASE: Float = 0.50f

    /** How far a prop or the woodpile is darkened at night: lerp to black by this times step / 16. */
    const val DIM_MAX: Float = 0.45f

    /** Number of quantisation steps of the lift. */
    const val STEPS: Int = 16

    /** Share of the page colour in the last four rows, from the row four from the bottom to the last row. */
    private val HORIZON_COVER = floatArrayOf(0.15f, 0.40f, 0.65f, 0.90f)

    const val CACHE_LIMIT: Int = 96

    private const val MODE_TINT = 0
    private const val MODE_DIM = 1

    private class Key(
        val sprite: Sprite, val frame: Int, val step: Int, val rgb: Int, val baseBits: Int, val mode: Int,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Key) return false
            return sprite === other.sprite && frame == other.frame && step == other.step && rgb == other.rgb &&
                baseBits == other.baseBits && mode == other.mode
        }

        override fun hashCode(): Int {
            var h = System.identityHashCode(sprite)
            h = h * 31 + frame
            h = h * 31 + step
            h = h * 31 + rgb
            h = h * 31 + baseBits
            h = h * 31 + mode
            return h
        }
    }

    private val lock = Any()
    private val cache = HashMap<Key, IntArray>()

    /** Number of cached rasters (for tests). */
    fun cacheSize(): Int = synchronized(lock) { cache.size }

    /** Empties the cache (for tests). */
    fun clearCache() {
        synchronized(lock) { cache.clear() }
    }

    /** The base amount that belongs to one of the three layer colours; 0.5 for any other colour. */
    fun baseFor(tintRgb: Int): Float = when (tintRgb and 0xFFFFFF) {
        NEAR_TINT -> NEAR_BASE
        GROUND_TINT -> GROUND_BASE
        FORE_TINT -> FORE_BASE
        else -> 0.5f
    }

    /**
     * Frame [frame] of [sprite] with every pixel's rgb blended towards [tint] by `baseFor(tint) * step16 / 16`;
     * the alpha of each pixel is kept. [step16] is clamped to 0..16; step 0 returns the baked raster.
     */
    fun tinted(sprite: Sprite, frame: Int, tint: Int, step16: Int): IntArray =
        tinted(sprite, frame, tint, step16, baseFor(tint))

    /** Same as the four-argument form with an explicit [base] amount (0..1). */
    fun tinted(sprite: Sprite, frame: Int, tint: Int, step16: Int, base: Float): IntArray {
        val step = step16.coerceIn(0, STEPS)
        if (step == 0 || base <= 0f) return SpriteRaster.plain(sprite, frame)
        val amount = (base * step / STEPS).coerceIn(0f, 1f)
        val rgb = tint and 0xFFFFFF
        return cached(Key(sprite, frame, step, rgb, base.toBits(), MODE_TINT)) { src -> blend(src, rgb, amount) }
    }

    /**
     * Frame [frame] of [sprite] with the rgb lerped to black by `0.45 * step16 / 16`, alpha kept (props and the
     * woodpile, which are baked in daylight: the caller passes `round((1 - daylight) * 16)`). Step 0 returns
     * the baked raster.
     */
    fun dimmed(sprite: Sprite, frame: Int, step16: Int): IntArray {
        val step = step16.coerceIn(0, STEPS)
        if (step == 0) return SpriteRaster.plain(sprite, frame)
        val amount = DIM_MAX * step / STEPS
        return cached(Key(sprite, frame, step, 0, DIM_MAX.toBits(), MODE_DIM)) { src -> blend(src, 0, amount) }
    }

    /**
     * The dithered horizon: in the last four rows of [stage] a pixel becomes [pageColor] when the Bayer value
     * of its position is below the share of that row (0.15, 0.40, 0.65, 0.90). With the 4x4 Bayer matrix the
     * real cover of a row also depends on `y and 3`: the last row is 100 per cent page colour when `y and 3`
     * is 0 or 2 and 75 per cent when it is 1 or 3 (the matrix rows 12 4 14 6 and 15 7 13 5 each hold one value
     * above the 0.90 share), so it is never below 75 per cent and never above 100. Does nothing for
     * `pageColor == 0`; nothing else changes. The page colour is drawn opaque.
     */
    fun horizon(stage: PixelStage, pageColor: Int) {
        if (pageColor == 0) return
        val argb = pageColor or OPAQUE
        val w = stage.width
        val h = stage.height
        val first = if (h - HORIZON_COVER.size < 0) 0 else h - HORIZON_COVER.size
        for (y in first until h) {
            val cover = HORIZON_COVER[HORIZON_COVER.size - (h - y)]
            val row = y * w
            for (x in 0 until w) {
                if (SceneArt.bayer(x, y) < cover) stage.pixels[row + x] = argb
            }
        }
    }

    private const val OPAQUE: Int = -0x1000000 // 0xFF000000 as a signed int

    private inline fun cached(key: Key, make: (IntArray) -> IntArray): IntArray {
        synchronized(lock) {
            val have = cache[key]
            if (have != null) return have
        }
        val built = make(SpriteRaster.plain(key.sprite, key.frame))
        synchronized(lock) {
            val have = cache[key]
            if (have != null) return have
            if (cache.size >= CACHE_LIMIT) cache.clear()
            cache[key] = built
        }
        return built
    }

    /** Blends the rgb of every pixel of [src] towards [rgb] by [amount]; the alpha byte is kept. */
    private fun blend(src: IntArray, rgb: Int, amount: Float): IntArray {
        val tr = (rgb ushr 16) and 0xFF
        val tg = (rgb ushr 8) and 0xFF
        val tb = rgb and 0xFF
        val out = IntArray(src.size)
        for (i in src.indices) {
            val c = src[i]
            val a = c ushr 24
            if (a == 0) {
                out[i] = c
                continue
            }
            val r = ((c ushr 16) and 0xFF)
            val g = ((c ushr 8) and 0xFF)
            val b = (c and 0xFF)
            val nr = (r + (tr - r) * amount).roundToInt().coerceIn(0, 255)
            val ng = (g + (tg - g) * amount).roundToInt().coerceIn(0, 255)
            val nb = (b + (tb - b) * amount).roundToInt().coerceIn(0, 255)
            out[i] = (a shl 24) or (nr shl 16) or (ng shl 8) or nb
        }
        return out
    }
}
