package dev.pocketagent.app.ui.pixel.stage

import dev.pocketagent.app.ui.pixel.stage.SkyTestKit.ISTANBUL_LAT
import dev.pocketagent.app.ui.pixel.stage.SkyTestKit.ISTANBUL_LON
import dev.pocketagent.app.ui.pixel.stage.SkyTestKit.LONDON_LAT
import dev.pocketagent.app.ui.pixel.stage.SkyTestKit.LONDON_LON
import dev.pocketagent.app.ui.pixel.stage.SkyTestKit.OSLO_LAT
import dev.pocketagent.app.ui.pixel.stage.SkyTestKit.OSLO_LON
import dev.pocketagent.app.ui.pixel.stage.SkyTestKit.SYDNEY_LAT
import dev.pocketagent.app.ui.pixel.stage.SkyTestKit.SYDNEY_LON
import dev.pocketagent.app.ui.pixel.stage.SkyTestKit.TROMSO_LAT
import dev.pocketagent.app.ui.pixel.stage.SkyTestKit.TROMSO_LON
import dev.pocketagent.app.ui.pixel.stage.SkyTestKit.frame
import dev.pocketagent.app.ui.pixel.stage.SkyTestKit.inputs
import dev.pocketagent.app.ui.pixel.stage.SkyTestKit.utc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SkyFramesTest {
    private fun channel(c: Int, shift: Int): Int = (c ushr shift) and 0xFF

    private fun maxChannelJump(a: Int, b: Int): Int =
        maxOf(Math.abs(channel(a, 16) - channel(b, 16)), Math.abs(channel(a, 8) - channel(b, 8)), Math.abs(channel(a, 0) - channel(b, 0)))

    @Test fun nightModeIsNull() {
        assertNull(SkyFrames.build(inputs(utc(2027, 3, 21, 12), ISTANBUL_LAT, ISTANBUL_LON, mode = SkyMode.NIGHT)))
    }

    @Test fun nightMomentEqualsClassicPalette() {
        for (palette in listOf(TestPalettes.DARK, TestPalettes.LIGHT)) {
            val f = frame(utc(2027, 3, 21, 0), ISTANBUL_LAT, ISTANBUL_LON, night = palette)
            assertTrue("sun is well below the horizon: ${f.sun.altDeg}", f.sun.altDeg < -30f)
            assertEquals(SkyMoment.NIGHT, f.moment)
            assertEquals(palette.skyTop, f.skyTop)
            assertEquals(palette.skyBottom, f.skyBottom)
            assertTrue(palette.farRamp.contentEquals(f.farRamp))
            assertEquals(0f, f.lift, 0f)
            assertEquals(0f, f.daylight, 0f)
            assertFalse(f.iconsDark)
        }
    }

    @Test fun dayIsBlueAndBright() {
        for (accent in SkyTestKit.ACCENTS) {
            val day = frame(utc(2027, 3, 21, 8, 30), ISTANBUL_LAT, ISTANBUL_LON, accent = accent)
            assertTrue("sun high: ${day.sun.altDeg}", day.sun.altDeg > 25f)
            assertEquals(SkyMoment.DAY, day.moment)
            assertTrue("blue above red", channel(day.skyTop, 0) > channel(day.skyTop, 16))
            assertTrue("luma above 0.4", SkyPalette.luma(day.skyTop) > 0.4f)
            assertTrue("icons dark in the day for accent ${Integer.toHexString(accent)}", day.iconsDark)
            val fixed = frame(utc(2027, 3, 21, 12), ISTANBUL_LAT, ISTANBUL_LON, mode = SkyMode.DAY, accent = accent)
            assertTrue(fixed.iconsDark)
            assertEquals(0.8125f, fixed.lift, 0f)
            // the same DAY colours straight from the palette, and a dusk and a night for the other polarity
            val dusk = SkyPalette.colors(TestPalettes.DARK, MomentBlend(SkyMoment.DUSK, SkyMoment.DUSK, 0f), accent, false, 0f)
            assertTrue(SkyPalette.luma(dusk.skyTop) < SkyPalette.ICONS_DARK_LUMA)
        }
        val night = frame(utc(2027, 3, 21, 0), ISTANBUL_LAT, ISTANBUL_LON)
        assertFalse(night.iconsDark)
        val dusk = frame(utc(2027, 3, 21, 17), ISTANBUL_LAT, ISTANBUL_LON)
        assertEquals(SkyMoment.DUSK, dusk.moment)
        assertFalse(dusk.iconsDark)
    }

    @Test fun colourContinuity() {
        for (evening in listOf(false, true)) {
            var prevTop = 0
            var prevBottom = 0
            var first = true
            var alt = -25.0
            while (alt <= 25.0) {
                val c = SkyPalette.colors(TestPalettes.DARK, SkyMoments.at(alt, evening), SkyTestKit.ACCENTS[0], false, 0f)
                if (!first) {
                    assertTrue("top jumps at $alt evening=$evening", maxChannelJump(prevTop, c.skyTop) <= 6)
                    assertTrue("bottom jumps at $alt evening=$evening", maxChannelJump(prevBottom, c.skyBottom) <= 6)
                }
                prevTop = c.skyTop
                prevBottom = c.skyBottom
                first = false
                alt += 0.1
            }
        }
    }

    @Test fun noJumpAtMeridian() {
        class Case(val lat: Double, val lon: Double, val y: Int, val mo: Int, val d: Int)
        val cases = listOf(
            Case(OSLO_LAT, OSLO_LON, 2026, 12, 21), Case(OSLO_LAT, OSLO_LON, 2027, 6, 21), Case(LONDON_LAT, LONDON_LON, 2027, 6, 21),
            Case(TROMSO_LAT, TROMSO_LON, 2026, 12, 21), Case(TROMSO_LAT, TROMSO_LON, 2027, 6, 21),
        )
        for (c in cases) {
            val base = utc(c.y, c.mo, c.d)
            var prev: SkyFrame? = null
            // one calendar day, without the sky's day list: a solstice's gold tint (a day effect) switches at local
            // midnight and is not what this test measures (the meridian)
            for (minute in 0 until 1440) {
                val f = frame(base + minute * 60_000L, c.lat, c.lon, lists = setOf("app"))
                val p = prev
                if (p != null) {
                    assertTrue("top jumps at minute $minute (${c.lat}, ${c.y}-${c.mo}-${c.d})", maxChannelJump(p.skyTop, f.skyTop) <= 6)
                    assertTrue("bottom jumps at minute $minute (${c.lat}, ${c.y}-${c.mo}-${c.d})", maxChannelJump(p.skyBottom, f.skyBottom) <= 6)
                    assertTrue("lift jumps at minute $minute", Math.abs(p.lift - f.lift) <= 1f / 16f + 1e-6f)
                }
                prev = f
            }
        }
    }

    @Test fun liftIsInSixteenths() {
        var minute = 0
        while (minute < 1440) {
            val f = frame(utc(2027, 3, 21) + minute * 60_000L, ISTANBUL_LAT, ISTANBUL_LON)
            val x = f.lift * 16f
            assertEquals(Math.round(x).toFloat(), x, 1e-4f)
            assertTrue(f.lift >= 0f && f.lift <= 1f)
            minute += 7
        }
    }

    @Test fun blackNightIsBlackAtNight() {
        val f = frame(utc(2027, 3, 21, 0), ISTANBUL_LAT, ISTANBUL_LON, blackNight = true)
        assertEquals(0xFF000000.toInt(), f.skyTop)
        assertEquals(0xFF000000.toInt(), f.skyBottom)
        for (c in f.farRamp) assertEquals(0xFF000000.toInt(), c)
    }

    @Test fun blackNightDayUnchanged() {
        val on = frame(utc(2027, 3, 21, 12), ISTANBUL_LAT, ISTANBUL_LON, mode = SkyMode.DAY, blackNight = true)
        val off = frame(utc(2027, 3, 21, 12), ISTANBUL_LAT, ISTANBUL_LON, mode = SkyMode.DAY, blackNight = false)
        assertEquals(off.skyTop, on.skyTop)
        assertEquals(off.skyBottom, on.skyBottom)
        assertTrue(off.farRamp.contentEquals(on.farRamp))
        assertEquals(off.lift, on.lift, 0f)
        val sun = frame(utc(2027, 3, 21, 9), ISTANBUL_LAT, ISTANBUL_LON, blackNight = true)
        assertEquals(frame(utc(2027, 3, 21, 9), ISTANBUL_LAT, ISTANBUL_LON).skyTop, sun.skyTop)
    }

    @Test fun seasonTable() {
        assertEquals(Season.WINTER, frame(utc(2027, 1, 15, 12), 59.9, 10.7).season)
        assertEquals(Season.SUMMER, frame(utc(2027, 1, 15, 12), -33.9, 151.2).season)
        assertEquals(Season.SPRING, frame(utc(2027, 4, 10, 12), 41.0, 29.0).season)
        assertEquals(Season.AUTUMN, frame(utc(2027, 4, 10, 12), -33.9, 151.2).season)
        assertEquals(Season.SUMMER, frame(utc(2027, 1, 15, 12), 1.35, 103.8).season)
        assertNull(frame(utc(2027, 1, 15, 12), 59.9, 10.7, seasons = SeasonSetting.OFF).season)
        assertEquals(Season.AUTUMN, frame(utc(2027, 1, 15, 12), 59.9, 10.7, seasons = SeasonSetting.AUTUMN).season)
    }

    @Test fun deterministic() {
        val a = frame(utc(2027, 3, 21, 17, 40), ISTANBUL_LAT, ISTANBUL_LON, lists = setOf("sky", "app", "newroz"))
        val b = frame(utc(2027, 3, 21, 17, 40), ISTANBUL_LAT, ISTANBUL_LON, lists = setOf("sky", "app", "newroz"))
        assertEquals(a.debugLine(), b.debugLine())
        assertEquals(a.skyTop, b.skyTop)
        assertEquals(a.skyBottom, b.skyBottom)
        assertTrue(a.farRamp.contentEquals(b.farRamp))
        assertEquals(a.lift, b.lift, 0f)
        assertEquals(a.moonPhase16, b.moonPhase16)
        assertEquals(a.moonIllum, b.moonIllum, 0f)
        assertEquals(a.sun.altDeg, b.sun.altDeg, 0f)
        assertEquals(a.moon.azDeg, b.moon.azDeg, 0f)
        assertEquals(a.ridgeFires, b.ridgeFires)
        assertEquals(a.dayIds, b.dayIds)
        assertEquals(a.planets.size, b.planets.size)
    }

    @Test fun dayModeHasNoStars() {
        val f = frame(utc(2027, 3, 21, 22), ISTANBUL_LAT, ISTANBUL_LON, mode = SkyMode.DAY)
        assertEquals(SkyMode.DAY, f.mode)
        assertEquals(1f, f.daylight, 0f)
        assertEquals(0f, f.depressionDeg, 0f)
        assertEquals(0f, StarLayer.alphaFor(-1.46f, f.depressionDeg, 1f), 0f)
        assertEquals(52f, f.sun.altDeg, 0f)
        assertEquals(SkyMoment.DAY, f.moment)
        assertTrue(f.planets.isEmpty())
        assertTrue(f.moon.altDeg < -80f)
    }

    @Test fun facingNorthInSouth() {
        assertEquals(0f, frame(utc(2027, 1, 15, 12), SYDNEY_LAT, SYDNEY_LON).facingAz, 0f)
        assertEquals(180f, frame(utc(2027, 1, 15, 12), OSLO_LAT, OSLO_LON).facingAz, 0f)
    }

    @Test fun daysFlow() {
        val istanbul = ZoneId.of("Europe/Istanbul")
        val dubai = ZoneId.of("Asia/Dubai")
        val equinox = utc(2027, 3, 20, 20, 25)
        val onList = setOf("sky", "app", "newroz")
        // Istanbul (+03): the equinox is at 23:25 on 20 March, so 20 March carries the gold and 21 March does not
        assertTrue(frame(equinox, ISTANBUL_LAT, ISTANBUL_LON, zone = istanbul).sunGold)
        assertFalse(frame(utc(2027, 3, 21, 12), ISTANBUL_LAT, ISTANBUL_LON, zone = istanbul).sunGold)
        // Dubai (+04): 00:25 on 21 March
        assertTrue(frame(equinox, 25.2, 55.3, zone = dubai).sunGold)
        assertFalse(frame(utc(2027, 3, 20, 12), 25.2, 55.3, zone = dubai).sunGold)
        // festive ridge fires on 21 March with Newroz on, in either zone
        assertEquals(2, frame(utc(2027, 3, 21, 12), ISTANBUL_LAT, ISTANBUL_LON, zone = istanbul, lists = onList).ridgeFires)
        assertEquals(2, frame(utc(2027, 3, 21, 12), 25.2, 55.3, zone = dubai, lists = onList).ridgeFires)
        val f = frame(utc(2027, 3, 21, 12), ISTANBUL_LAT, ISTANBUL_LON, zone = istanbul, lists = onList)
        assertTrue(f.dayIds.contains("newroz"))
        assertTrue(f.dayIds.contains("newroz-ridge"))
        // quiet fires on any other date, none without the list
        assertEquals(1, frame(utc(2027, 5, 2, 12), ISTANBUL_LAT, ISTANBUL_LON, zone = istanbul, lists = onList).ridgeFires)
        assertEquals(0, frame(utc(2027, 3, 21, 12), ISTANBUL_LAT, ISTANBUL_LON, zone = istanbul).ridgeFires)
    }

    @Test fun meteorShowersAndFireworks() {
        assertEquals(3500, frame(utc(2027, 8, 12, 23, 30), 45.0, 10.0).meteorPeriodMs)
        assertEquals(14000, frame(utc(2027, 8, 20, 23, 30), 45.0, 10.0).meteorPeriodMs)
        assertEquals(14000, frame(utc(2027, 8, 12, 23, 30), 45.0, 10.0, lists = setOf("app")).meteorPeriodMs)
        assertTrue(frame(utc(2027, 12, 31, 23), 45.0, 10.0, lists = setOf("sky", "newyear")).fireworks)
        assertFalse(frame(utc(2027, 12, 31, 23), 45.0, 10.0).fireworks)
        assertTrue(frame(utc(2025, 3, 14, 20), 45.0, 10.0).moonGlow)
        assertFalse(frame(utc(2025, 3, 20, 20), 45.0, 10.0).moonGlow)
    }

    @Test fun moonFields() {
        val waxing = frame(utc(2025, 3, 10, 21), 45.0, 10.0)
        assertTrue(waxing.moonWaxing)
        assertTrue(waxing.moonPhase16 in 5..7)
        val waning = frame(utc(2025, 3, 18, 21), 45.0, 10.0)
        assertFalse(waning.moonWaxing)
        assertTrue(waning.moonPhase16 in 9..11)
        val full = frame(utc(2025, 3, 14, 7), 45.0, 10.0)
        assertEquals(8, full.moonPhase16)
        assertTrue(full.moonIllum > 0.999f)
    }

    @Test fun eclipseRedMoon() {
        val total = utc(2028, 12, 31, 16, 52)
        assertTrue(frame(total, 45.0, 10.0).moonRed > 0.99f)
        assertEquals(0.5f, frame(total + 30 * 60_000L, 45.0, 10.0).moonRed, 0.05f)
        assertEquals(0f, frame(total + 90 * 60_000L, 45.0, 10.0).moonRed, 0f)
        assertEquals(0f, frame(total, 45.0, 10.0, lists = setOf("app")).moonRed, 0f)
        // a penumbral eclipse leaves the moon pale
        assertEquals(0f, frame(utc(2027, 2, 20, 23, 13), 45.0, 10.0).moonRed, 0f)
    }

    @Test fun moonlightOnlyAtNight() {
        val night = frame(utc(2025, 3, 14, 23), 45.0, 10.0)
        assertTrue(night.moon.altDeg > 10f)
        assertTrue("moonlight ${night.moonLight}", night.moonLight > 0.8f)
        val noon = frame(utc(2025, 3, 14, 11), 45.0, 10.0)
        assertEquals(0f, noon.moonLight, 1e-6f)
    }

    @Test fun petHatFromAnniversaryAndUserDay() {
        val first = LocalDate.of(2026, 3, 21).toEpochDay()
        val onDay = utc(2027, 3, 21, 12)
        assertTrue(frame(onDay, 45.0, 10.0, firstLaunchEpochDay = first).petHat)
        assertFalse(frame(onDay, 45.0, 10.0, lists = setOf("sky"), firstLaunchEpochDay = first).petHat)
        assertFalse(frame(utc(2027, 3, 22, 12), 45.0, 10.0, firstLaunchEpochDay = first).petHat)
        assertFalse(frame(onDay, 45.0, 10.0).petHat)
        assertTrue(frame(utc(2027, 7, 4, 12), 45.0, 10.0, userDays = listOf(UserDay(7, 4, "July"))).petHat)
    }

    @Test fun debugLineShape() {
        val line = frame(utc(2027, 3, 21, 12), ISTANBUL_LAT, ISTANBUL_LON).debugLine()
        assertTrue(line, line.startsWith("mode=FOLLOW moment=DAY a=DAY b=DAY blend=0.00 sun="))
        assertTrue(line, line.contains(" season=SPRING "))
        assertTrue(line, line.contains(" days="))
    }

    @Test fun sunAndMoonAreGeometric() {
        // noon at the equator on an equinox: the sun stands about overhead
        val f = frame(utc(2027, 3, 20, 12), 0.0, 0.0)
        assertEquals(90f, f.sun.altDeg, 3f)
    }
}
