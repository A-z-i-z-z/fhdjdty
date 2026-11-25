package dev.pocketagent.app.ui.pixel

import kotlin.math.min

/**
 * Sprite frame to plain ARGB ints (one per pixel, 0 = transparent). The arithmetic that used to sit inside
 * SpriteBitmaps.build, with no Android types, so the stage and the unit tests can use it.
 */
object SpriteRaster {
    /**
     * [ramp] replaces the palette chars '1'..'4' (slot k gets ramp[min(k, ramp.size - 1)]); [hasTint] makes
     * every pixel the colour of [tint] with its alpha scaled by the tint's alpha.
     */
    fun argb(sprite: Sprite, frame: Int, hasTint: Boolean = false, tint: Int = 0, ramp: IntArray? = null): IntArray {
        val pal = IntArray(sprite.palette.size) { sprite.palette[it].argb }
        if (ramp != null && ramp.isNotEmpty()) {
            for (k in 0 until 4) {
                val slot = sprite.rampSlots[k]
                if (slot >= 0) pal[slot] = ramp[min(k, ramp.size - 1)]
            }
        }
        if (hasTint) {
            val ta = tint ushr 24
            for (i in pal.indices) {
                val a = (((pal[i] ushr 24) * ta) + 127) / 255
                pal[i] = (a shl 24) or (tint and 0xFFFFFF)
            }
        }
        val src = sprite.frames[frame].indices
        return IntArray(src.size) { val s = src[it]; if (s < 0) 0 else pal[s] }
    }

    /** The plain frame, built once and kept on the sprite. The result is shared: never mutate it. */
    fun plain(sprite: Sprite, frame: Int): IntArray {
        val have = sprite.rasters[frame]
        if (have != null) return have
        val px = argb(sprite, frame)
        sprite.rasters[frame] = px
        return px
    }
}
