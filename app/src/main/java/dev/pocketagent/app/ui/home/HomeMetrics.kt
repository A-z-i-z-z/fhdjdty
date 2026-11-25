package dev.pocketagent.app.ui.home

import java.time.DateTimeException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.round
import kotlin.math.roundToInt

/** The four parts of a day that Home's greeting and the spoken label of the hero tell apart. */
enum class DayPart { MORNING, AFTERNOON, EVENING, NIGHT }

/** 5..11 morning, 12..17 afternoon, 18..21 evening, every other hour (also one outside 0..23) night: the old greeting. */
fun dayPartOf(hour: Int): DayPart = when (hour) {
    in 5..11 -> DayPart.MORNING
    in 12..17 -> DayPart.AFTERNOON
    in 18..21 -> DayPart.EVENING
    else -> DayPart.NIGHT
}

/** The GLSL smoothstep: 0 at or below [edge0], 1 at or above [edge1], 3t^2 - 2t^3 between. NaN gives 0. A zero-width range is a step at [edge1]. */
fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    if (x.isNaN() || edge0.isNaN() || edge1.isNaN()) return 0f
    if (edge1 == edge0) return if (x >= edge1) 1f else 0f
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** The numbers of Home's collapsing hero. Pure: pixels and densities come in as parameters. */
object HomeMetrics {
    const val HERO_FRACTION = 0.46f
    const val HERO_MIN_DP = 300f
    const val HERO_MAX_DP = 440f

    /** Content height of the collapsed strip, below the status bar. */
    const val STRIP_DP = 96f

    /** The prompt card covers the hero's lower edge by this much. */
    const val OVERLAP_DP = 28f

    /** Bottom band of the hero with no scene object in it: greeting and status text (56) plus the overlap (28). */
    const val BAND_DP = 84f

    /** Logical rows a fully visible fire needs (the same number as SceneGeometry.FIRE_FOOTPRINT_ROWS). */
    const val FIRE_ROWS = 38
    const val COLLAPSE_STEPS = 32

    /** The greeting fades out and the strip's title fades in over the same range of the collapse, so the band is never blank. */
    const val FADE_FROM = 0.35f
    const val FADE_TO = 0.75f

    /** 46 percent of the container, between 300 and 440 dp; a NaN or non-positive container gives 300. */
    fun heroFullDp(containerHeightDp: Float): Float {
        if (containerHeightDp.isNaN() || containerHeightDp <= 0f) return HERO_MIN_DP
        return (HERO_FRACTION * containerHeightDp).coerceIn(HERO_MIN_DP, HERO_MAX_DP)
    }

    /** The collapsed strip in pixels: the status bar plus the taller of 96 dp and the rows the fire needs. */
    fun stripPx(statusBarPx: Int, density: Float, gridScale: Int): Int {
        val byDp = (STRIP_DP * density).roundToInt()
        val byFire = FIRE_ROWS * gridScale
        return statusBarPx + max(byDp, byFire)
    }

    /** How far the list scrolls while the hero collapses; at least 1 so a division by it is safe. */
    fun collapseRangePx(heroFullPx: Int, stripPx: Int): Int = max(1, heroFullPx - stripPx)

    /** 0 at the top, 1 once the hero is gone: from the list's first visible item and its scroll offset. */
    fun collapseOf(firstIndex: Int, firstOffsetPx: Int, rangePx: Int): Float {
        if (rangePx <= 0) return 1f
        if (firstIndex > 0) return 1f
        return (firstOffsetPx.toFloat() / rangePx.toFloat()).coerceIn(0f, 1f)
    }

    /** At most 33 distinct values (multiples of 1/32); NaN gives 0. Ties go to the even step (kotlin.math.round). */
    fun quantize(collapse: Float): Float {
        if (collapse.isNaN()) return 0f
        return (round(collapse.coerceIn(0f, 1f) * COLLAPSE_STEPS) / COLLAPSE_STEPS).coerceIn(0f, 1f)
    }

    /** 1 while the hero is open, 0 once the fade is over. */
    fun greetingAlpha(collapse: Float): Float = 1f - smoothstep(FADE_FROM, FADE_TO, collapse)

    /** 0 while the hero is open, 1 once the fade is over; always `1 - greetingAlpha`. */
    fun stripAlpha(collapse: Float): Float = smoothstep(FADE_FROM, FADE_TO, collapse)
}

/** The short date in the hero. */
object HomeFormat {
    /**
     * Formats [now] with [bestPattern] in [locale]. Production passes the platform's best pattern for the skeleton "EEEdMMM"
     * (the platform call lives in the composable, not here). A pattern that cannot be used, or one that gives nothing,
     * falls back to the ISO date (2027-01-15).
     */
    fun date(now: ZonedDateTime, locale: Locale, bestPattern: String): String {
        val iso = now.toLocalDate().toString()
        return try {
            val out = DateTimeFormatter.ofPattern(bestPattern, locale).format(now)
            if (out.isBlank()) iso else out
        } catch (_: IllegalArgumentException) {
            iso
        } catch (_: DateTimeException) {
            iso
        }
    }
}

/**
 * The text colour on the hero's bottom band. The band colour is the one `HomeSky.iconsDark` looks at while the hero is
 * collapsed, so the ink and the status-bar icons turn dark and light at the same band colour (the same Rec.601 luma and
 * the same limit, [DARK_INK_LUMA]).
 */
object HeroInk {
    /** Light ink for a dark band. */
    val LIGHT: Int = 0xFFF4EBDD.toInt()

    /** Dark ink for a bright band. */
    val DARK: Int = 0xFF1B1220.toInt()

    /** A band at or above this Rec.601 luma gets dark ink (and dark status-bar icons). */
    const val DARK_INK_LUMA = 0.40f

    /** Rec.601 luma of an ARGB colour, 0..1; the alpha is ignored. */
    fun luma(argb: Int): Float {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return ((0.299 * r + 0.587 * g + 0.114 * b) / 255.0).toFloat()
    }

    fun pick(bandArgb: Int): Int = if (luma(bandArgb) >= DARK_INK_LUMA) DARK else LIGHT

    /** The WCAG contrast ratio of two colours (alpha ignored), 1..21. */
    fun contrast(aArgb: Int, bArgb: Int): Double {
        val la = relativeLuminance(aArgb)
        val lb = relativeLuminance(bArgb)
        val hi = max(la, lb)
        val lo = kotlin.math.min(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    /** True when [ink] on [bandArgb] has a contrast ratio below 4.5: the caller draws a one-unit shadow in the opposite colour. */
    fun needsShadow(ink: Int, bandArgb: Int): Boolean = contrast(ink, bandArgb) < 4.5

    private fun relativeLuminance(argb: Int): Double {
        val r = channel((argb shr 16) and 0xFF)
        val g = channel((argb shr 8) and 0xFF)
        val b = channel(argb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun channel(v: Int): Double {
        val s = v / 255.0
        return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
    }
}
