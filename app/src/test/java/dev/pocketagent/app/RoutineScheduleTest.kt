package dev.pocketagent.app

import dev.pocketagent.app.routines.Routine
import dev.pocketagent.app.routines.RoutineKind
import dev.pocketagent.app.routines.RoutineSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RoutineScheduleTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val utc = ZoneId.of("UTC")

    private fun at(zone: ZoneId, y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0): Long =
        ZonedDateTime.of(y, mo, d, h, mi, s, 0, zone).toInstant().toEpochMilli()

    private fun local(ms: Long, zone: ZoneId): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(zone)

    private val longAgo = at(utc, 2020, 1, 1, 0, 0)

    private fun daily(h: Int, m: Int, days: Int = RoutineSchedule.ALL_DAYS, lastRunAt: Long = 0L, armedAt: Long = 0L, createdAt: Long = longAgo) =
        Routine(id = "r", name = "n", prompt = "p", kind = RoutineKind.DAILY, hour = h, minute = m, days = days,
            lastRunAt = lastRunAt, armedAt = armedAt, createdAt = createdAt)

    private fun interval(minutes: Int, lastRunAt: Long = 0L, createdAt: Long = longAgo, armedAt: Long = 0L) =
        Routine(id = "r", name = "n", prompt = "p", kind = RoutineKind.INTERVAL, intervalMinutes = minutes,
            lastRunAt = lastRunAt, createdAt = createdAt, armedAt = armedAt)

    private fun once(onceAt: Long, createdAt: Long = longAgo, armedAt: Long = 0L, lastRunAt: Long = 0L) =
        Routine(id = "r", name = "n", prompt = "p", kind = RoutineKind.ONCE, onceAt = onceAt,
            createdAt = createdAt, armedAt = armedAt, lastRunAt = lastRunAt)

    // ---- daily: time of day ------------------------------------------------------------------------------------

    @Test fun dailyTimeLaterThanNowRunsToday() {
        val now = at(paris, 2026, 6, 10, 7, 0)
        assertEquals(at(paris, 2026, 6, 10, 8, 30), RoutineSchedule.nextRun(daily(8, 30), now, paris))
    }

    @Test fun dailyTimeEarlierThanNowRunsTomorrow() {
        val now = at(paris, 2026, 6, 10, 9, 0)
        assertEquals(at(paris, 2026, 6, 11, 8, 30), RoutineSchedule.nextRun(daily(8, 30), now, paris))
    }

    @Test fun dailyIsStrictlyAfterNow() {
        val now = at(paris, 2026, 6, 10, 8, 30)
        assertEquals(at(paris, 2026, 6, 11, 8, 30), RoutineSchedule.nextRun(daily(8, 30), now, paris))
        assertEquals(at(paris, 2026, 6, 10, 8, 30), RoutineSchedule.nextRun(daily(8, 30), now - 1, paris))
    }

    @Test fun dailyMidnightAndLastMinute() {
        val now = at(paris, 2026, 6, 10, 12, 0)
        assertEquals(at(paris, 2026, 6, 11, 0, 0), RoutineSchedule.nextRun(daily(0, 0), now, paris))
        assertEquals(at(paris, 2026, 6, 10, 23, 59), RoutineSchedule.nextRun(daily(23, 59), now, paris))
    }

    // ---- daily: weekday masks ----------------------------------------------------------------------------------

    @Test fun calendarFactsUsedByTheTests() {
        assertEquals(DayOfWeek.TUESDAY, LocalDate.of(2026, 11, 24).dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, LocalDate.of(2026, 3, 29).dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, LocalDate.of(2026, 10, 25).dayOfWeek)
    }

    @Test fun weekdaysOnlySkipsTheWeekend() {
        val friday = at(paris, 2026, 11, 27, 20, 0)
        assertEquals(DayOfWeek.FRIDAY, local(friday, paris).dayOfWeek)
        assertEquals(at(paris, 2026, 11, 30, 8, 0), RoutineSchedule.nextRun(daily(8, 0, RoutineSchedule.WEEKDAYS), friday, paris))
    }

    @Test fun weekendOnlyFromAMonday() {
        val monday = at(paris, 2026, 11, 23, 10, 0)
        assertEquals(at(paris, 2026, 11, 28, 8, 0), RoutineSchedule.nextRun(daily(8, 0, RoutineSchedule.WEEKEND), monday, paris))
    }

    @Test fun singleDayLaterTodayAndEarlierToday() {
        val tuesdayMask = 1 shl 1
        val earlyTuesday = at(paris, 2026, 11, 24, 7, 0)
        assertEquals(at(paris, 2026, 11, 24, 9, 0), RoutineSchedule.nextRun(daily(9, 0, tuesdayMask), earlyTuesday, paris))
        val lateTuesday = at(paris, 2026, 11, 24, 10, 0)
        assertEquals(at(paris, 2026, 12, 1, 9, 0), RoutineSchedule.nextRun(daily(9, 0, tuesdayMask), lateTuesday, paris))
    }

    @Test fun sundayIsBitSix() {
        val tuesday = at(paris, 2026, 11, 24, 10, 0)
        assertEquals(at(paris, 2026, 11, 29, 18, 0), RoutineSchedule.nextRun(daily(18, 0, 1 shl 6), tuesday, paris))
    }

    @Test fun noDaySelectedHasNoNextRun() {
        assertNull(RoutineSchedule.nextRun(daily(8, 0, 0), at(paris, 2026, 6, 10, 7, 0), paris))
        // Bits above bit 6 mean nothing.
        assertNull(RoutineSchedule.nextRun(daily(8, 0, 0x80), at(paris, 2026, 6, 10, 7, 0), paris))
    }

    @Test fun disabledHasNoNextRunAndNothingDue() {
        val r = daily(8, 0).copy(enabled = false)
        assertNull(RoutineSchedule.nextRun(r, at(paris, 2026, 6, 10, 7, 0), paris))
        assertNull(RoutineSchedule.dueRun(r, at(paris, 2026, 6, 10, 9, 0), paris))
        assertNull(RoutineSchedule.nextRun(interval(60).copy(enabled = false), 1L))
        assertNull(RoutineSchedule.nextRun(once(5_000_000_000_000L).copy(enabled = false), 1L))
    }

    @Test fun dayHelpers() {
        assertTrue(RoutineSchedule.hasDay(0b0000101, 0))
        assertFalse(RoutineSchedule.hasDay(0b0000101, 1))
        assertTrue(RoutineSchedule.hasDay(0b0000101, 2))
        assertFalse(RoutineSchedule.hasDay(0x7F, 7))
        assertEquals(0b0000100, RoutineSchedule.toggleDay(0b0000101, 0))
        assertEquals(0b0000111, RoutineSchedule.toggleDay(0b0000101, 1))
        assertEquals(listOf(0, 2), RoutineSchedule.daysOf(0b0000101))
        assertEquals(listOf(0, 1, 2, 3, 4), RoutineSchedule.daysOf(RoutineSchedule.WEEKDAYS))
        assertEquals(listOf(5, 6), RoutineSchedule.daysOf(RoutineSchedule.WEEKEND))
    }

    // ---- rollover ----------------------------------------------------------------------------------------------

    @Test fun monthRollover() {
        val now = at(paris, 2026, 1, 31, 23, 0)
        assertEquals(at(paris, 2026, 2, 1, 8, 0), RoutineSchedule.nextRun(daily(8, 0), now, paris))
        val feb = at(paris, 2028, 2, 28, 23, 0)   // 2028 is a leap year
        assertEquals(at(paris, 2028, 2, 29, 8, 0), RoutineSchedule.nextRun(daily(8, 0), feb, paris))
    }

    @Test fun yearRollover() {
        val now = at(paris, 2026, 12, 31, 22, 0)
        assertEquals(at(paris, 2027, 1, 1, 8, 0), RoutineSchedule.nextRun(daily(8, 0), now, paris))
    }

    @Test fun zoneDecidesTheLocalDay() {
        // 23:30 UTC on June 10 is already 01:30 on June 11 in Paris: a 08:00 routine there runs on the 11th, not the 10th.
        val now = at(utc, 2026, 6, 10, 23, 30)
        assertEquals(at(paris, 2026, 6, 11, 8, 0), RoutineSchedule.nextRun(daily(8, 0), now, paris))
        assertEquals(at(utc, 2026, 6, 11, 8, 0), RoutineSchedule.nextRun(daily(8, 0), now, utc))
    }

    // ---- daylight saving ---------------------------------------------------------------------------------------

    @Test fun springForwardGapRunsShiftedByTheGap() {
        // 2026-03-29 in Paris: 02:00 jumps to 03:00, so 02:30 does not exist.
        val now = at(paris, 2026, 3, 28, 12, 0)
        val next = RoutineSchedule.nextRun(daily(2, 30), now, paris)
        assertNotNull(next)
        val z = local(next!!, paris)
        assertEquals(LocalDate.of(2026, 3, 29), z.toLocalDate())
        assertEquals(3, z.hour)
        assertEquals(30, z.minute)
        assertEquals(at(utc, 2026, 3, 29, 1, 30), next)
        // The day after is a normal 02:30.
        assertEquals(at(paris, 2026, 3, 30, 2, 30), RoutineSchedule.nextRun(daily(2, 30), next, paris))
    }

    @Test fun springForwardNormalTimesAreUntouched() {
        val now = at(paris, 2026, 3, 28, 12, 0)
        assertEquals(at(paris, 2026, 3, 29, 8, 0), RoutineSchedule.nextRun(daily(8, 0), now, paris))
        assertEquals(at(utc, 2026, 3, 29, 6, 0), RoutineSchedule.nextRun(daily(8, 0), now, paris))   // CEST is UTC+2
    }

    @Test fun fallBackOverlapRunsOnceAtTheFirstOccurrence() {
        // 2026-10-25 in Paris: 03:00 CEST falls back to 02:00 CET, so 02:30 happens twice.
        val now = at(paris, 2026, 10, 24, 12, 0)
        val first = RoutineSchedule.nextRun(daily(2, 30), now, paris)
        assertEquals(at(utc, 2026, 10, 25, 0, 30), first)
        // While the clock repeats the hour (between the two 02:30), the next run is the following day, not the repeat.
        val betweenTheTwo = at(utc, 2026, 10, 25, 1, 0)
        assertEquals(at(paris, 2026, 10, 26, 2, 30), RoutineSchedule.nextRun(daily(2, 30), betweenTheTwo, paris))
        assertEquals(at(utc, 2026, 10, 26, 1, 30), RoutineSchedule.nextRun(daily(2, 30), betweenTheTwo, paris))
        // Right after the first one as well.
        assertEquals(at(paris, 2026, 10, 26, 2, 30), RoutineSchedule.nextRun(daily(2, 30), first!!, paris))
    }

    @Test fun fallBackDayKeepsOtherTimes() {
        val now = at(paris, 2026, 10, 24, 12, 0)
        assertEquals(at(paris, 2026, 10, 25, 8, 0), RoutineSchedule.nextRun(daily(8, 0), now, paris))
        assertEquals(at(utc, 2026, 10, 25, 7, 0), RoutineSchedule.nextRun(daily(8, 0), now, paris))   // CET is UTC+1
    }

    // ---- interval ----------------------------------------------------------------------------------------------

    @Test fun intervalCountsFromTheLastRun() {
        val last = at(utc, 2026, 6, 10, 8, 0)
        val r = interval(60, lastRunAt = last)
        assertEquals(last + 60 * 60_000L, RoutineSchedule.nextRun(r, last + 10 * 60_000L))
        assertEquals(last + 60 * 60_000L, RoutineSchedule.nextRun(r, last))
    }

    @Test fun intervalOverdueMovesToTheNextPointOfTheGrid() {
        val last = at(utc, 2026, 6, 10, 8, 0)
        val r = interval(60, lastRunAt = last)
        assertEquals(last + 120 * 60_000L, RoutineSchedule.nextRun(r, last + 61 * 60_000L))
        assertEquals(last + 120 * 60_000L, RoutineSchedule.nextRun(r, last + 60 * 60_000L))
        assertEquals(last + 180 * 60_000L, RoutineSchedule.nextRun(r, last + 125 * 60_000L))
    }

    @Test fun intervalThatNeverRanCountsFromCreationOrArming() {
        val created = at(utc, 2026, 6, 10, 8, 0)
        assertEquals(created + 30 * 60_000L, RoutineSchedule.nextRun(interval(30, createdAt = created), created + 1_000L))
        val armed = created + 5 * 60_000L
        assertEquals(armed + 30 * 60_000L, RoutineSchedule.nextRun(interval(30, createdAt = created, armedAt = armed), created + 1_000L))
    }

    @Test fun intervalIsClampedToTheAllowedRange() {
        assertEquals(RoutineSchedule.MIN_INTERVAL_MINUTES, RoutineSchedule.clampInterval(1))
        assertEquals(RoutineSchedule.MAX_INTERVAL_MINUTES, RoutineSchedule.clampInterval(999_999))
        val last = at(utc, 2026, 6, 10, 8, 0)
        assertEquals(last + 15 * 60_000L, RoutineSchedule.nextRun(interval(1, lastRunAt = last), last))
    }

    // ---- once --------------------------------------------------------------------------------------------------

    @Test fun onceInTheFutureAndInThePast() {
        val t = at(paris, 2026, 12, 24, 18, 0)
        assertEquals(t, RoutineSchedule.nextRun(once(t), t - 1, paris))
        assertNull(RoutineSchedule.nextRun(once(t), t, paris))
        assertNull(RoutineSchedule.nextRun(once(t), t + 1, paris))
        assertNull(RoutineSchedule.nextRun(once(0L), 5L, paris))
    }

    // ---- due runs and missed runs -------------------------------------------------------------------------------

    @Test fun dailyDueExactlyAtTheSlotOnly() {
        val yesterdayRun = at(paris, 2026, 6, 9, 8, 0, 3)
        val r = daily(8, 0, lastRunAt = yesterdayRun)
        assertNull(RoutineSchedule.dueRun(r, at(paris, 2026, 6, 10, 7, 59), paris))
        val due = RoutineSchedule.dueRun(r, at(paris, 2026, 6, 10, 8, 0), paris)
        assertNotNull(due)
        assertEquals(at(paris, 2026, 6, 10, 8, 0), due!!.slot)
        assertEquals(0L, due.lateMs)
        assertFalse(due.tooLate)
    }

    @Test fun dailyRunAlreadyMadeIsNotDueAgain() {
        val ranAt = at(paris, 2026, 6, 10, 8, 0, 4)
        val r = daily(8, 0, lastRunAt = ranAt)
        assertNull(RoutineSchedule.dueRun(r, at(paris, 2026, 6, 10, 8, 5), paris))
        assertNull(RoutineSchedule.dueRun(r, at(paris, 2026, 6, 10, 23, 0), paris))
    }

    @Test fun missedByTwoHoursIsMadeUpOnce() {
        val r = daily(8, 0, lastRunAt = at(paris, 2026, 6, 9, 8, 0, 3))
        val due = RoutineSchedule.dueRun(r, at(paris, 2026, 6, 10, 10, 0), paris)
        assertNotNull(due)
        assertEquals(2 * 60 * 60_000L, due!!.lateMs)
        assertFalse(due.tooLate)
    }

    @Test fun missedBySevenHoursIsSkipped() {
        val r = daily(8, 0, lastRunAt = at(paris, 2026, 6, 9, 8, 0, 3))
        val due = RoutineSchedule.dueRun(r, at(paris, 2026, 6, 10, 15, 0), paris)
        assertNotNull(due)
        assertTrue(due!!.tooLate)
    }

    @Test fun severalMissedDaysCountAsOneRunAtTheLatestSlot() {
        val r = daily(8, 0, lastRunAt = at(paris, 2026, 6, 5, 8, 0, 3))
        val due = RoutineSchedule.dueRun(r, at(paris, 2026, 6, 10, 8, 30), paris)
        assertNotNull(due)
        assertEquals(at(paris, 2026, 6, 10, 8, 0), due!!.slot)
        assertFalse(due.tooLate)
    }

    @Test fun aNewRoutineDoesNotCatchUpForASlotBeforeItExisted() {
        val created = at(paris, 2026, 6, 10, 9, 0)
        val r = daily(8, 0, createdAt = created)
        assertNull(RoutineSchedule.dueRun(r, at(paris, 2026, 6, 10, 9, 30), paris))
        assertEquals(at(paris, 2026, 6, 11, 8, 0), RoutineSchedule.nextRun(r, at(paris, 2026, 6, 10, 9, 30), paris))
        assertNotNull(RoutineSchedule.dueRun(r, at(paris, 2026, 6, 11, 8, 0), paris))
    }

    @Test fun reArmingMovesTheBaselineForward() {
        val r = daily(8, 0, lastRunAt = at(paris, 2026, 6, 1, 8, 0, 3), armedAt = at(paris, 2026, 6, 10, 9, 0))
        assertNull(RoutineSchedule.dueRun(r, at(paris, 2026, 6, 10, 9, 30), paris))
    }

    @Test fun intervalDueAfterOneStepAndSkippedWhenTooLate() {
        val last = at(utc, 2026, 6, 10, 8, 0)
        val r = interval(60, lastRunAt = last)
        assertNull(RoutineSchedule.dueRun(r, last + 59 * 60_000L, utc))
        val due = RoutineSchedule.dueRun(r, last + 60 * 60_000L, utc)
        assertNotNull(due)
        assertEquals(last + 60 * 60_000L, due!!.slot)
        assertFalse(due.tooLate)
        // A phone that was off for days still runs it once (the newest grid slot is only minutes late).
        val farLater = RoutineSchedule.dueRun(r, last + 5 * 24 * 60 * 60_000L + 7 * 60_000L, utc)
        assertNotNull(farLater)
        assertFalse(farLater!!.tooLate)
    }

    @Test fun longIntervalMissedByMoreThanSixHoursIsSkipped() {
        val last = at(utc, 2026, 6, 10, 8, 0)
        val step = 2880 * 60_000L   // 2 days
        val r = interval(2880, lastRunAt = last)
        val tooLate = RoutineSchedule.dueRun(r, last + step + 23 * 60 * 60_000L, utc)
        assertNotNull(tooLate)
        assertTrue(tooLate!!.tooLate)
        val ok = RoutineSchedule.dueRun(r, last + step + 5 * 60 * 60_000L, utc)
        assertNotNull(ok)
        assertFalse(ok!!.tooLate)
    }

    @Test fun onceDueAfterItsTimeOnlyIfArmedBefore() {
        val t = at(paris, 2026, 12, 24, 18, 0)
        val armedBefore = once(t, createdAt = t - 100_000L, armedAt = t - 50_000L)
        assertNull(RoutineSchedule.dueRun(armedBefore, t - 1, paris))
        assertNotNull(RoutineSchedule.dueRun(armedBefore, t, paris))
        assertTrue(RoutineSchedule.dueRun(armedBefore, t + RoutineSchedule.MAX_LATE_MS + 1, paris)!!.tooLate)
        // Saved with a time that was already in the past: never due.
        val armedAfter = once(t, createdAt = t + 10_000L, armedAt = t + 10_000L)
        assertNull(RoutineSchedule.dueRun(armedAfter, t + 20_000L, paris))
        // Already ran.
        assertNull(RoutineSchedule.dueRun(once(t, createdAt = t - 100_000L, armedAt = t - 50_000L, lastRunAt = t + 2_000L), t + 60_000L, paris))
    }

    // ---- the alarm ------------------------------------------------------------------------------------------------

    @Test fun alarmIsTheEarliestNextRunOfAllRoutines() {
        val now = at(paris, 2026, 6, 10, 7, 0)
        // lastRunAt = now: nothing is waiting to be made up (an old, never-run routine would count as a missed run).
        val list = listOf(daily(9, 0, lastRunAt = now).copy(id = "a"), daily(8, 0, lastRunAt = now).copy(id = "b"),
            daily(8, 0, days = 0, lastRunAt = now).copy(id = "c"), interval(600, createdAt = now).copy(id = "d"))
        assertEquals(at(paris, 2026, 6, 10, 8, 0), RoutineSchedule.alarmAt(list, now, paris))
        assertEquals(at(paris, 2026, 6, 10, 8, 0), RoutineSchedule.earliestNext(list, now, paris))
    }

    @Test fun alarmIsSoonWhenARunIsWaiting() {
        val now = at(paris, 2026, 6, 10, 9, 0)
        val waiting = daily(8, 0, lastRunAt = at(paris, 2026, 6, 9, 8, 0, 3))
        assertEquals(now + 1_500L, RoutineSchedule.alarmAt(listOf(waiting), now, paris))
        assertEquals(now + 500L, RoutineSchedule.alarmAt(listOf(waiting), now, paris, dueDelayMs = 500L))
    }

    @Test fun noRoutinesNoAlarm() {
        assertNull(RoutineSchedule.alarmAt(emptyList(), 1L, paris))
        assertNull(RoutineSchedule.alarmAt(listOf(daily(8, 0).copy(enabled = false)), 1L, paris))
    }
}
