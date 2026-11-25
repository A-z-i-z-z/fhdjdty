package dev.pocketagent.app.ui.pixel.stage

import kotlin.math.floor

/** Two neighbouring moments and how far the sky is from the first to the second: [t] 0 gives [a], 1 gives [b]. */
class MomentBlend(val a: SkyMoment, val b: SkyMoment, val t: Float)

/**
 * Which look the sky has at a sun altitude. The morning has four keys (night, dawn, sunrise, day), the evening
 * five (day, golden hour, sunset, dusk, night); between two keys the blend follows a smooth step of the
 * altitude. The evening is the time after the sun's meridian passage.
 */
object SkyMoments {
    private val MORNING_KEYS = doubleArrayOf(-18.0, -9.0, -0.8, 12.0)
    private val MORNING = arrayOf(SkyMoment.NIGHT, SkyMoment.DAWN, SkyMoment.SUNRISE, SkyMoment.DAY)
    private val EVENING_KEYS = doubleArrayOf(14.0, 6.0, -0.8, -8.0, -18.0)
    private val EVENING = arrayOf(SkyMoment.DAY, SkyMoment.GOLDEN, SkyMoment.SUNSET, SkyMoment.DUSK, SkyMoment.NIGHT)

    private fun smooth(f: Double): Float = (f * f * (3.0 - 2.0 * f)).toFloat()

    fun at(sunAltDeg: Double, evening: Boolean): MomentBlend {
        if (!evening) {
            if (sunAltDeg <= MORNING_KEYS[0]) return MomentBlend(SkyMoment.NIGHT, SkyMoment.NIGHT, 0f)
            if (sunAltDeg >= MORNING_KEYS[3]) return MomentBlend(SkyMoment.DAY, SkyMoment.DAY, 0f)
            for (i in 0 until 3) {
                val lo = MORNING_KEYS[i]
                val hi = MORNING_KEYS[i + 1]
                if (sunAltDeg >= lo && sunAltDeg < hi) return MomentBlend(MORNING[i], MORNING[i + 1], smooth((sunAltDeg - lo) / (hi - lo)))
            }
            return MomentBlend(SkyMoment.DAY, SkyMoment.DAY, 0f)
        }
        if (sunAltDeg >= EVENING_KEYS[0]) return MomentBlend(SkyMoment.DAY, SkyMoment.DAY, 0f)
        if (sunAltDeg <= EVENING_KEYS[4]) return MomentBlend(SkyMoment.NIGHT, SkyMoment.NIGHT, 0f)
        for (i in 0 until 4) {
            val hi = EVENING_KEYS[i]
            val lo = EVENING_KEYS[i + 1]
            if (sunAltDeg <= hi && sunAltDeg > lo) return MomentBlend(EVENING[i], EVENING[i + 1], smooth((hi - sunAltDeg) / (hi - lo)))
        }
        return MomentBlend(SkyMoment.NIGHT, SkyMoment.NIGHT, 0f)
    }
}

/** The colours of one sky: top and bottom of the gradient, the four far-ridge tints, and how far it is lifted from the night (0..1). */
class SkyColors(val skyTop: Int, val skyBottom: Int, val farRamp: IntArray, val lift: Float)

/**
 * Turns a moment blend into colours. The NIGHT moment is exactly today's palette. Every other moment lerps the
 * palette's night colours towards a tint (a little biased towards the theme accent) by its mix; the lift says how
 * much the ridges and the ground follow.
 */
object SkyPalette {
    class Tint(val top: Int, val bottom: Int, val topMix: Float, val bottomMix: Float, val lift: Float)

    private fun rgb(v: Long): Int = (0xFF000000L or v).toInt()

    val TINTS: Map<SkyMoment, Tint> = mapOf(
        SkyMoment.NIGHT to Tint(0, 0, 0f, 0f, 0f),
        SkyMoment.DAWN to Tint(rgb(0x3A4A8C), rgb(0xE59A7C), 0.75f, 0.70f, 0.35f),
        SkyMoment.SUNRISE to Tint(rgb(0x4F7BC4), rgb(0xFFB86B), 0.85f, 0.85f, 0.50f),
        SkyMoment.DAY to Tint(rgb(0x3D7FD0), rgb(0x9CCBF0), 0.92f, 0.92f, 0.80f),
        SkyMoment.GOLDEN to Tint(rgb(0x4A7CC0), rgb(0xF2C277), 0.88f, 0.85f, 0.55f),
        SkyMoment.SUNSET to Tint(rgb(0x3B4E8F), rgb(0xF27F5A), 0.85f, 0.88f, 0.50f),
        SkyMoment.DUSK to Tint(rgb(0x1F2A5C), rgb(0x7A4E86), 0.75f, 0.70f, 0.30f),
    )

    /** How far the theme accent leans into every tint (look knob, 0 to 0.2). */
    const val THEME_BIAS = 0.10f

    /** How far the bright tints turn towards gold on a day of the sky's own list (equinox, solstice). */
    const val SUN_GOLD_WARM = 0.25f

    /** Status-bar icons turn dark over a sky whose Rec.601 luma is at least this. */
    const val ICONS_DARK_LUMA = 0.40f

    private val DAY_RIDGE = intArrayOf(rgb(0x2D3F6E), rgb(0x46609A), rgb(0x6E8DC4), rgb(0xA4BEE6))
    private val GOLD = rgb(0xF2B35A)
    private val BLACK = rgb(0x000000)
    private val SUN_WHITE = rgb(0xFFF4C2)
    private val SUN_ORANGE = rgb(0xFF9A4A)

    private fun warmable(m: SkyMoment): Boolean =
        m == SkyMoment.SUNRISE || m == SkyMoment.DAY || m == SkyMoment.GOLDEN || m == SkyMoment.SUNSET

    private fun topOf(night: ScenePalette, m: SkyMoment, accent: Int): Int {
        val t = TINTS.getValue(m)
        if (t.topMix == 0f) return night.skyTop
        return Argb.lerp(night.skyTop, Argb.lerp(t.top, accent, THEME_BIAS), t.topMix)
    }

    private fun bottomOf(night: ScenePalette, m: SkyMoment, accent: Int, warm: Float): Int {
        val t = TINTS.getValue(m)
        if (t.bottomMix == 0f) return night.skyBottom
        var tint = t.bottom
        if (warm > 0f && warmable(m)) tint = Argb.lerp(tint, GOLD, warm)
        return Argb.lerp(night.skyBottom, Argb.lerp(tint, accent, THEME_BIAS), t.bottomMix)
    }

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /**
     * The colours of [blend]. [warm] (0 to 1) turns the bright tints towards gold. [blackNight] pulls the sky
     * and the far ridge to pure black when the lift is 0 and leaves them alone from a lift of 0.8 up. At lift 0
     * (the NIGHT moment, [blackNight] off) every colour equals [night] exactly. [SkyColors.lift] is not rounded.
     */
    fun colors(night: ScenePalette, blend: MomentBlend, accent: Int, blackNight: Boolean, warm: Float): SkyColors {
        val topA = topOf(night, blend.a, accent)
        val topB = topOf(night, blend.b, accent)
        val botA = bottomOf(night, blend.a, accent, warm)
        val botB = bottomOf(night, blend.b, accent, warm)
        var top = Argb.lerp(topA, topB, blend.t)
        var bottom = Argb.lerp(botA, botB, blend.t)
        val liftA = TINTS.getValue(blend.a).lift
        val liftB = TINTS.getValue(blend.b).lift
        val lift = liftA + (liftB - liftA) * blend.t
        val n = night.farRamp.size
        val ramp = IntArray(n) { i ->
            val dayRidge = DAY_RIDGE[if (i < DAY_RIDGE.size) i else DAY_RIDGE.size - 1]
            Argb.lerp(night.farRamp[i], Argb.lerp(bottom, dayRidge, 0.5f), lift)
        }
        if (blackNight) {
            val w = 1f - smoothstep(0f, 0.8f, lift)
            top = Argb.lerp(top, BLACK, w)
            bottom = Argb.lerp(bottom, BLACK, w)
            for (i in 0 until n) ramp[i] = Argb.lerp(ramp[i], BLACK, w)
        }
        return SkyColors(top, bottom, ramp, lift)
    }

    /** The sun's colour: pale yellow high up, orange at the horizon (full orange at altitude 0, full yellow from 12). */
    fun sunColor(sunAltDeg: Double): Int {
        val k = (sunAltDeg / 12.0).coerceIn(0.0, 1.0)
        return Argb.lerp(SUN_WHITE, SUN_ORANGE, (1.0 - k).toFloat())
    }

    /** Rec.601 luma of an ARGB colour, 0 to 1. */
    fun luma(argb: Int): Float {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return ((0.299 * r + 0.587 * g + 0.114 * b) / 255.0).toFloat()
    }

    /** [x] rounded to the nearest sixteenth (the lift is quantised so tint caches have few keys). */
    internal fun sixteenths(x: Float): Float = floor(x * 16f + 0.5f) / 16f
}
