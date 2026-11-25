package dev.pocketagent.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class HomeMetricsTest {
    private val eps = 1e-5f

    @Test fun dayPartTable() {
        // reference: the greeting of the old Home screen (5..11, 12..17, 18..21, else night)
        val expected = mapOf(
            4 to DayPart.NIGHT, 5 to DayPart.MORNING, 11 to DayPart.MORNING, 12 to DayPart.AFTERNOON,
            17 to DayPart.AFTERNOON, 18 to DayPart.EVENING, 21 to DayPart.EVENING, 22 to DayPart.NIGHT,
            0 to DayPart.NIGHT, 23 to DayPart.NIGHT, 24 to DayPart.NIGHT, -1 to DayPart.NIGHT,
        )
        for ((hour, part) in expected) assertEquals("hour $hour", part, dayPartOf(hour))
    }

    @Test fun smoothstepShape() {
        assertEquals(0f, smoothstep(0.35f, 0.75f, 0.35f), eps)
        assertEquals(1f, smoothstep(0.35f, 0.75f, 0.75f), eps)
        assertEquals(0f, smoothstep(0.35f, 0.75f, -3f), eps)
        assertEquals(1f, smoothstep(0.35f, 0.75f, 9f), eps)
        assertEquals(0.5f, smoothstep(0.35f, 0.75f, 0.55f), eps)
        // t = 0.25 gives 3 * 0.0625 - 2 * 0.015625 = 0.15625 (and t = 0.75 gives 0.84375)
        assertEquals(0.15625f, smoothstep(0.35f, 0.75f, 0.45f), eps)
        assertEquals(0.84375f, smoothstep(0.35f, 0.75f, 0.65f), eps)
        assertEquals(0f, smoothstep(0f, 1f, Float.NaN), 0f)
    }

    @Test fun smoothstepZeroWidthIsAStep() {
        assertEquals(0f, smoothstep(0.5f, 0.5f, 0.49f), 0f)
        assertEquals(1f, smoothstep(0.5f, 0.5f, 0.5f), 0f)
        assertEquals(1f, smoothstep(0.5f, 0.5f, 0.9f), 0f)
    }

    @Test fun smoothstepIsMonotone() {
        var prev = -1f
        for (i in 0..200) {
            val v = smoothstep(0.1f, 0.9f, i / 200f)
            assertTrue(v >= prev)
            prev = v
        }
    }

    @Test fun heroFullDp() {
        assertEquals(368f, HomeMetrics.heroFullDp(800f), 1e-3f)
        assertEquals(440f, HomeMetrics.heroFullDp(1000f), 0f)   // 460 coerced down
        assertEquals(300f, HomeMetrics.heroFullDp(600f), 0f)    // 276 coerced up
        assertEquals(300f, HomeMetrics.heroFullDp(Float.NaN), 0f)
        assertEquals(300f, HomeMetrics.heroFullDp(0f), 0f)
        assertEquals(300f, HomeMetrics.heroFullDp(-10f), 0f)
    }

    @Test fun bandIsTextBandPlusOverlap() {
        assertEquals(56f, HomeMetrics.BAND_DP - HomeMetrics.OVERLAP_DP, 0f)
        assertTrue(HomeMetrics.STRIP_DP > HomeMetrics.OVERLAP_DP)
    }

    @Test fun stripPx() {
        // 1080 px wide, density 2.625, grid scale 7, status bar 95: 95 + max(252, 266) = 361
        assertEquals(361, HomeMetrics.stripPx(95, 2.625f, 7))
        // 1440 px wide, density 3.5, grid scale 9, status bar 105: 105 + max(336, 342) = 447
        assertEquals(447, HomeMetrics.stripPx(105, 3.5f, 9))
        // the dp side wins when the grid is small: 96 dp at density 3 is 288, 38 rows of 4 px is 152
        assertEquals(24 + 288, HomeMetrics.stripPx(24, 3f, 4))
    }

    @Test fun collapseRange() {
        assertEquals(500, HomeMetrics.collapseRangePx(861, 361))
        assertEquals(1, HomeMetrics.collapseRangePx(300, 361))
        assertEquals(1, HomeMetrics.collapseRangePx(361, 361))
    }

    @Test fun collapseOf() {
        assertEquals(1f, HomeMetrics.collapseOf(1, 0, 500), 0f)
        assertEquals(1f, HomeMetrics.collapseOf(7, 30, 500), 0f)
        assertEquals(0f, HomeMetrics.collapseOf(0, 0, 500), 0f)
        assertEquals(0.5f, HomeMetrics.collapseOf(0, 250, 500), 0f)
        assertEquals(1f, HomeMetrics.collapseOf(0, 900, 500), 0f)
        assertEquals(0f, HomeMetrics.collapseOf(0, -40, 500), 0f)
        assertEquals(1f, HomeMetrics.collapseOf(0, 10, 0), 0f)
        assertEquals(1f, HomeMetrics.collapseOf(0, 10, -5), 0f)
    }

    @Test fun quantizeSteps() {
        val seen = HashSet<Float>()
        for (i in 0..1000) {
            val q = HomeMetrics.quantize(i / 1000f)
            seen.add(q)
            assertEquals("a multiple of 1/32", 0f, q * 32f - Math.round(q * 32f), 1e-6f)
            assertTrue(q in 0f..1f)
        }
        assertEquals(33, seen.size)
        assertEquals(0f, HomeMetrics.quantize(Float.NaN), 0f)
        assertEquals(0f, HomeMetrics.quantize(-2f), 0f)
        assertEquals(1f, HomeMetrics.quantize(7f), 0f)
        assertEquals(0.5f, HomeMetrics.quantize(0.5f), 0f)
        assertEquals(0.25f, HomeMetrics.quantize(0.26f), 0f)
    }

    @Test fun crossFadeNeverEmpty() {
        var prevG = 2f
        var prevS = -1f
        for (i in 0..100) {
            val c = i / 100f
            val g = HomeMetrics.greetingAlpha(c)
            val s = HomeMetrics.stripAlpha(c)
            // the two texts always add up to one, so at least one of them is half visible
            assertEquals("sum at $c", 1f, g + s, 1e-5f)
            assertTrue("blank band at $c", maxOf(g, s) >= 0.5f - 1e-5f)
            assertTrue(g <= prevG + 1e-6f)
            assertTrue(s >= prevS - 1e-6f)
            prevG = g
            prevS = s
        }
        assertEquals(1f, HomeMetrics.greetingAlpha(0f), 0f)
        assertEquals(0f, HomeMetrics.stripAlpha(0f), 0f)
        assertEquals(0f, HomeMetrics.greetingAlpha(1f), 0f)
        assertEquals(1f, HomeMetrics.stripAlpha(1f), 0f)
        assertEquals(1f, HomeMetrics.greetingAlpha(HomeMetrics.FADE_FROM), 1e-6f)
        assertEquals(1f, HomeMetrics.stripAlpha(HomeMetrics.FADE_TO), 1e-6f)
    }

    @Test fun dateFormatFixedPattern() {
        val now = ZonedDateTime.of(2027, 1, 15, 12, 0, 0, 0, ZoneId.of("UTC"))   // a Friday
        assertEquals("Fri 15 Jan", HomeFormat.date(now, Locale.ENGLISH, "EEE d MMM"))
        assertEquals("Cum 15 Oca", HomeFormat.date(now, Locale.forLanguageTag("tr"), "EEE d MMM"))
    }

    @Test fun dateFormatFallsBackToIso() {
        val now = ZonedDateTime.of(2027, 1, 15, 23, 30, 0, 0, ZoneId.of("Europe/Istanbul"))
        assertEquals("2027-01-15", HomeFormat.date(now, Locale.ENGLISH, "EEE d MMM q{"))
        assertEquals("2027-01-15", HomeFormat.date(now, Locale.ENGLISH, ""))
    }

    @Test fun inkByLuminance() {
        val white = 0xFFFFFFFF.toInt()
        assertEquals(HeroInk.DARK, HeroInk.pick(white))
        assertEquals(HeroInk.LIGHT, HeroInk.pick(0xFF130A16.toInt()))
        assertEquals(HeroInk.LIGHT, HeroInk.pick(0xFF1D2A26.toInt()))   // the band colour before the sprites are ready
        // the limit is the one of the status-bar icons: Rec.601 luma 0.40 (gray 101 is 0.396, gray 104 is 0.408)
        assertEquals(HeroInk.LIGHT, HeroInk.pick(0xFF656565.toInt()))
        assertEquals(HeroInk.DARK, HeroInk.pick(0xFF686868.toInt()))
        assertEquals(0xFFF4EBDD.toInt(), HeroInk.LIGHT)
        assertEquals(0xFF1B1220.toInt(), HeroInk.DARK)
        assertEquals(1f, HeroInk.luma(white), 1e-6f)
        assertEquals(0f, HeroInk.luma(0xFF000000.toInt()), 0f)
    }

    @Test fun needsShadowBelowFourPointFive() {
        val white = 0xFFFFFFFF.toInt()
        val nightBand = 0xFF130A16.toInt()
        // reference values recomputed in Python (WCAG): light ink on the night band 16.4, dark ink on white 18.2
        assertEquals(16.417, HeroInk.contrast(HeroInk.LIGHT, nightBand), 0.01)
        assertEquals(18.198, HeroInk.contrast(HeroInk.DARK, white), 0.01)
        assertFalse(HeroInk.needsShadow(HeroInk.LIGHT, nightBand))
        assertFalse(HeroInk.needsShadow(HeroInk.DARK, white))
        // light ink on a white band: 1.18; dark ink on gray 102: 3.17 (a mid-tone band right above the limit)
        assertTrue(HeroInk.needsShadow(HeroInk.LIGHT, white))
        assertEquals(3.169, HeroInk.contrast(HeroInk.DARK, 0xFF666666.toInt()), 0.01)
        assertTrue(HeroInk.needsShadow(HeroInk.DARK, 0xFF686868.toInt()))
    }
}
