package dev.pocketagent.app

import dev.pocketagent.app.data.UsageDay
import dev.pocketagent.app.ui.settings.UsageMath
import dev.pocketagent.app.ui.settings.UsageTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class UsageMathTest {
    private val today = LocalDate.of(2026, 11, 24)

    private fun row(day: String, model: String, turns: Int, i: Long, o: Long) = UsageDay(day, model, turns, i, o)

    private val rows = listOf(
        row("2026-11-24", "", 2, 1000, 200),
        row("2026-11-24", "google/gemini-4-hydrogen", 1, 500, 100),
        row("2026-11-23", "", 3, 3000, 900),
        row("2026-11-12", "google/gemini-4-hydrogen", 4, 4000, 800),
        row("not-a-date", "", 9, 9, 9),
    )

    @Test fun sumsEverything() {
        assertEquals(UsageTotals(19, 8509, 2009), UsageMath.sum(rows))
        assertEquals(2009L + 8509L, UsageMath.sum(rows).total)
    }

    @Test fun oneDayAndTheLastDays() {
        assertEquals(UsageTotals(3, 1500, 300), UsageMath.forDay(rows, today))
        assertEquals(UsageTotals.ZERO, UsageMath.forDay(rows, today.minusDays(5)))
        val last = UsageMath.lastDays(rows, today, 14)
        assertEquals(14, last.size)
        assertEquals(today, last.last().day)
        assertEquals(today.minusDays(13), last.first().day)
        assertEquals(UsageTotals(3, 3000, 900), last[12].totals)                                  // yesterday
        assertEquals(UsageTotals(4, 4000, 800), last.first { it.day == LocalDate.of(2026, 11, 12) }.totals)
        assertEquals(UsageTotals(6, 4500, 1200), UsageMath.sumLast(rows, today, 7))               // today + yesterday only
    }

    @Test fun perModelBusiestFirst() {
        val m = UsageMath.byModel(rows)
        assertEquals(listOf("google/gemini-4-hydrogen", ""), m.map { it.model })
        assertEquals(UsageTotals(14, 4009, 1109), m[1].totals)
        assertEquals(UsageTotals(5, 4500, 900), m[0].totals)
    }

    @Test fun damagedRowsAreIgnoredByDayLookups() {
        assertNull(UsageMath.parseDay("garbage"))
        assertEquals(today, UsageMath.parseDay("2026-11-24"))
    }

    @Test fun compactNumbers() {
        val l = Locale.US
        assertEquals("0", UsageMath.compact(0, l))
        assertEquals("950", UsageMath.compact(950, l))
        assertEquals("1.2k", UsageMath.compact(1234, l))
        assertEquals("1k", UsageMath.compact(1000, l))
        assertEquals("10k", UsageMath.compact(9999, l))
        assertEquals("12k", UsageMath.compact(12345, l))
        assertEquals("999k", UsageMath.compact(999_999, l))
        assertEquals("3.4M", UsageMath.compact(3_400_000, l))
        assertEquals("50M", UsageMath.compact(50_000_000, l))
        assertEquals("1.5B", UsageMath.compact(1_500_000_000L, l))
        assertEquals("0", UsageMath.compact(-5, l))
    }
}
