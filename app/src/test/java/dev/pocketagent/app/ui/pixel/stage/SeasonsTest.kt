package dev.pocketagent.app.ui.pixel.stage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The pure rule of the seasons. The overlays (snow, flowers, tufts, leaves) are drawn by the renderer part; their
 * tests (deterministic, only on opaque ground, four seasons differ) arrive with it.
 */
class SeasonsTest {
    private fun on(month: Int, day: Int = 15): LocalDate = LocalDate.of(2027, month, day)

    @Test fun northernMonths() {
        val expected = listOf(
            Season.WINTER, Season.WINTER, Season.SPRING, Season.SPRING, Season.SPRING, Season.SUMMER,
            Season.SUMMER, Season.SUMMER, Season.AUTUMN, Season.AUTUMN, Season.AUTUMN, Season.WINTER,
        )
        for (m in 1..12) assertEquals("month $m", expected[m - 1], Seasons.seasonFor(on(m), 52.0, SeasonSetting.AUTO))
    }

    @Test fun southernMonthsAreShiftedBySix() {
        val north = listOf(
            Season.WINTER, Season.WINTER, Season.SPRING, Season.SPRING, Season.SPRING, Season.SUMMER,
            Season.SUMMER, Season.SUMMER, Season.AUTUMN, Season.AUTUMN, Season.AUTUMN, Season.WINTER,
        )
        val flip = mapOf(Season.WINTER to Season.SUMMER, Season.SUMMER to Season.WINTER, Season.SPRING to Season.AUTUMN, Season.AUTUMN to Season.SPRING)
        for (m in 1..12) assertEquals("month $m", flip[north[m - 1]], Seasons.seasonFor(on(m), -33.9, SeasonSetting.AUTO))
    }

    @Test fun tableFromTheContract() {
        assertEquals(Season.WINTER, Seasons.seasonFor(LocalDate.of(2027, 1, 15), 59.9, SeasonSetting.AUTO))
        assertEquals(Season.SUMMER, Seasons.seasonFor(LocalDate.of(2027, 1, 15), -33.9, SeasonSetting.AUTO))
        assertEquals(Season.SPRING, Seasons.seasonFor(LocalDate.of(2027, 4, 10), 41.0, SeasonSetting.AUTO))
        assertEquals(Season.AUTUMN, Seasons.seasonFor(LocalDate.of(2027, 4, 10), -33.9, SeasonSetting.AUTO))
        assertEquals(Season.SUMMER, Seasons.seasonFor(LocalDate.of(2027, 1, 15), 1.35, SeasonSetting.AUTO))
    }

    @Test fun tropicsAreSummerAllYear() {
        for (m in 1..12) {
            assertEquals(Season.SUMMER, Seasons.seasonFor(on(m), 14.9, SeasonSetting.AUTO))
            assertEquals(Season.SUMMER, Seasons.seasonFor(on(m), -14.9, SeasonSetting.AUTO))
        }
        assertEquals(Season.WINTER, Seasons.seasonFor(on(1), 15.0, SeasonSetting.AUTO))
        assertEquals(Season.SUMMER, Seasons.seasonFor(on(1), -15.0, SeasonSetting.AUTO))
    }

    @Test fun fixedSettingsWinAndOffIsNull() {
        assertNull(Seasons.seasonFor(on(1), 59.9, SeasonSetting.OFF))
        assertEquals(Season.SPRING, Seasons.seasonFor(on(1), 59.9, SeasonSetting.SPRING))
        assertEquals(Season.SUMMER, Seasons.seasonFor(on(1), 59.9, SeasonSetting.SUMMER))
        assertEquals(Season.AUTUMN, Seasons.seasonFor(on(7), -33.9, SeasonSetting.AUTUMN))
        assertEquals(Season.WINTER, Seasons.seasonFor(on(7), 1.0, SeasonSetting.WINTER))
    }

    @Test fun lookKnobsAreOnByDefault() {
        for (s in Season.values()) assertTrue("$s overlay is on", Seasons.enabled(s))
    }

    @Test fun monthBoundariesAreByCalendarMonth() {
        assertEquals(Season.WINTER, Seasons.seasonFor(LocalDate.of(2027, 2, 28), 45.0, SeasonSetting.AUTO))
        assertEquals(Season.SPRING, Seasons.seasonFor(LocalDate.of(2027, 3, 1), 45.0, SeasonSetting.AUTO))
        assertEquals(Season.SPRING, Seasons.seasonFor(LocalDate.of(2027, 5, 31), 45.0, SeasonSetting.AUTO))
        assertEquals(Season.SUMMER, Seasons.seasonFor(LocalDate.of(2027, 6, 1), 45.0, SeasonSetting.AUTO))
        assertEquals(Season.AUTUMN, Seasons.seasonFor(LocalDate.of(2027, 9, 1), 45.0, SeasonSetting.AUTO))
        assertEquals(Season.WINTER, Seasons.seasonFor(LocalDate.of(2027, 12, 1), 45.0, SeasonSetting.AUTO))
    }
}
