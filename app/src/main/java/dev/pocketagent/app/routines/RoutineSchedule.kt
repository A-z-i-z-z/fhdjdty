package dev.pocketagent.app.routines

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** A routine that should run now: [slot] is the scheduled time it belongs to, [tooLate] means it is better skipped. */
data class DueRun(val routine: Routine, val slot: Long, val lateMs: Long, val tooLate: Boolean)

/**
 * The schedule arithmetic of [Routine]s. Pure (no Android classes, no clock): everything takes the time as a parameter, so
 * the unit tests can pin any day. Dates are computed with java.time in the given zone, which makes the daylight-saving
 * days behave: a clock time that does not exist (spring forward) runs at the same moment shifted by the length of the gap,
 * a clock time that happens twice (fall back) runs once, at its first occurrence.
 */
object RoutineSchedule {
    const val MIN_INTERVAL_MINUTES = 15
    const val MAX_INTERVAL_MINUTES = 10_080
    const val ALL_DAYS = 0x7F
    const val WEEKDAYS = 0b0011111   // Monday..Friday
    const val WEEKEND = 0b1100000    // Saturday, Sunday

    /** A missed run (of any kind) is still made up when it is less than this late; later ones are skipped. */
    const val MAX_LATE_MS = 6L * 60 * 60 * 1000

    private const val MINUTE_MS = 60_000L
    private const val SEARCH_DAYS = 8L

    fun clampInterval(minutes: Int): Int = minutes.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)

    private fun intervalMs(r: Routine): Long = clampInterval(r.intervalMinutes) * MINUTE_MS

    /** The moment missed runs are counted from: the newest of the last run, the arming time and the creation time. */
    fun baseline(r: Routine): Long = maxOf(r.lastRunAt, r.armedAt, r.createdAt)

    /** True when bit [day] (0 = Monday .. 6 = Sunday) is set in [mask]. */
    fun hasDay(mask: Int, day: Int): Boolean = day in 0..6 && (mask shr day) and 1 == 1

    fun toggleDay(mask: Int, day: Int): Int = (mask xor (1 shl day)) and ALL_DAYS

    private fun dayBit(d: LocalDate): Int = d.dayOfWeek.value - 1

    /** The instant of [r]'s clock time on [date] in [zone] (see the class comment for the DST days). */
    private fun slotOn(date: LocalDate, r: Routine, zone: ZoneId): Long =
        ZonedDateTime.of(date, LocalTime.of(r.hour.coerceIn(0, 23), r.minute.coerceIn(0, 59)), zone).toInstant().toEpochMilli()

    /**
     * The next run strictly after [afterMillis], or null: the routine is disabled, a DAILY routine has no day selected, or
     * a ONCE routine lies in the past. An INTERVAL routine counts from its last run (or from its creation / arming time
     * when it never ran); when that moment is long past, the next point of the same grid after [afterMillis] is returned.
     */
    fun nextRun(r: Routine, afterMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (!r.enabled) return null
        return when (r.kind) {
            RoutineKind.DAILY -> nextDaily(r, afterMillis, zone)
            RoutineKind.INTERVAL -> {
                val step = intervalMs(r)
                val base = baseline(r)
                val first = base + step
                if (first > afterMillis) first else base + step * ((afterMillis - base) / step + 1)
            }
            RoutineKind.ONCE -> r.onceAt.takeIf { it > 0L && it > afterMillis }
        }
    }

    private fun nextDaily(r: Routine, afterMillis: Long, zone: ZoneId): Long? {
        val mask = r.days and ALL_DAYS
        if (mask == 0) return null
        val today = Instant.ofEpochMilli(afterMillis).atZone(zone).toLocalDate()
        // One day back as well: harmless on normal days, and it keeps odd zone jumps correct.
        var off = -1L
        while (off <= SEARCH_DAYS) {
            val d = today.plusDays(off)
            if (hasDay(mask, dayBit(d))) {
                val t = slotOn(d, r, zone)
                if (t > afterMillis) return t
            }
            off++
        }
        return null
    }

    /** The latest scheduled moment at or before [atMillis] for a DAILY routine, ignoring whether it already ran. */
    private fun previousDaily(r: Routine, atMillis: Long, zone: ZoneId): Long? {
        val mask = r.days and ALL_DAYS
        if (mask == 0) return null
        val today = Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate()
        var off = 1L
        while (off >= -SEARCH_DAYS) {
            val d = today.plusDays(off)
            if (hasDay(mask, dayBit(d))) {
                val t = slotOn(d, r, zone)
                if (t <= atMillis) return t
            }
            off--
        }
        return null
    }

    /**
     * Whether [r] has a run that was due at [now] and has not been made yet, and how late it is. Null when nothing is due
     * (disabled, nothing scheduled since the baseline, still in the future). Several missed slots count as one run.
     */
    fun dueRun(r: Routine, now: Long, zone: ZoneId = ZoneId.systemDefault()): DueRun? {
        if (!r.enabled) return null
        val base = baseline(r)
        val slot: Long = when (r.kind) {
            RoutineKind.DAILY -> previousDaily(r, now, zone)
            RoutineKind.INTERVAL -> {
                val step = intervalMs(r)
                if (base + step > now) null else base + step * ((now - base) / step)
            }
            RoutineKind.ONCE -> r.onceAt.takeIf { it in 1..now }
        } ?: return null
        if (slot <= base) return null
        val late = now - slot
        return DueRun(r, slot, late, tooLate = late > MAX_LATE_MS)
    }

    fun due(routines: List<Routine>, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<DueRun> =
        routines.mapNotNull { dueRun(it, now, zone) }

    /** The earliest [nextRun] over all [routines], or null. */
    fun earliestNext(routines: List<Routine>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? =
        routines.mapNotNull { nextRun(it, now, zone) }.minOrNull()

    /**
     * When the one alarm should ring: [dueDelayMs] after [now] when a run is waiting to be made up (after a reboot, or
     * when the app opens), else the earliest next run. Null when there is nothing to schedule.
     */
    fun alarmAt(routines: List<Routine>, now: Long, zone: ZoneId = ZoneId.systemDefault(), dueDelayMs: Long = 1_500L): Long? =
        if (due(routines, now, zone).isNotEmpty()) now + dueDelayMs else earliestNext(routines, now, zone)

    /** All weekday numbers (0 = Monday) set in [mask]. */
    fun daysOf(mask: Int): List<Int> = (0..6).filter { hasDay(mask, it) }
}
