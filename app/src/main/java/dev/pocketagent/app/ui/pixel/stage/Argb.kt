package dev.pocketagent.app.ui.pixel.stage

import kotlin.math.roundToInt

/**
 * Colour maths on packed ARGB ints (straight, not premultiplied alpha). Pure Kotlin: no Android types, so
 * it runs in JVM unit tests and in anything that draws without a Canvas.
 */
object Argb {
    fun pack(a: Int, r: Int, g: Int, b: Int): Int =
        ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    /** 0..1 to 0..255 the way `Color.toArgb` does it (round half up, clamped). */
    fun alpha255(alpha: Float): Int = (alpha.coerceIn(0f, 1f) * 255f + 0.5f).toInt()

    /** [rgb] (its own alpha byte is ignored) with the alpha [alpha] (0..1). */
    fun withAlpha(rgb: Int, alpha: Float): Int = (alpha255(alpha) shl 24) or (rgb and 0xFFFFFF)

    /** Multiplies the alpha byte of [argb] by [alpha255] / 255 (rounded), keeps the colour. */
    fun scaleAlpha(argb: Int, alpha255: Int): Int {
        val a = (((argb ushr 24) * alpha255) + 127) / 255
        return (a shl 24) or (argb and 0xFFFFFF)
    }

    /** Opaque blend of [a] towards [b] by [t], each channel rounded. */
    fun lerp(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int): Int {
            val x = (a ushr shift) and 0xFF
            val y = (b ushr shift) and 0xFF
            return (x + (y - x) * t).roundToInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Straight-alpha source-over: [src] drawn on top of [dst]. */
    fun over(dst: Int, src: Int): Int {
        val sa = src ushr 24
        if (sa == 255) return src
        if (sa == 0) return dst
        val da = dst ushr 24
        if (da == 0) return src
        if (da == 255) {
            val inv = 255 - sa
            val r = (((src ushr 16) and 0xFF) * sa + ((dst ushr 16) and 0xFF) * inv + 127) / 255
            val g = (((src ushr 8) and 0xFF) * sa + ((dst ushr 8) and 0xFF) * inv + 127) / 255
            val b = ((src and 0xFF) * sa + (dst and 0xFF) * inv + 127) / 255
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val oa = sa + (da * (255 - sa) + 127) / 255
        val denom = oa * 255
        val half = denom / 2
        val dw = da * (255 - sa)
        val sw = sa * 255
        val r = (((src ushr 16) and 0xFF) * sw + ((dst ushr 16) and 0xFF) * dw + half) / denom
        val g = (((src ushr 8) and 0xFF) * sw + ((dst ushr 8) and 0xFF) * dw + half) / denom
        val b = ((src and 0xFF) * sw + (dst and 0xFF) * dw + half) / denom
        return (oa shl 24) or (r shl 16) or (g shl 8) or b
    }
}
