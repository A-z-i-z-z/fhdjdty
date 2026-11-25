package dev.pocketagent.app.ui.pixel.stage

import java.io.File
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.EnumMap
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/** What a special day does to the picture. LANTERNS does not exist in this wave (its dates wait for a later wave). */
enum class DayEffect { SUN_GOLD, METEORS, MOON_GLOW, ECLIPSE, PAPER_HAT, RIDGE_FIRES, FIREWORKS }

/** Who a list of days belongs to: the sky itself, the app, or a culture the user switches on. */
enum class DayGroup { SKY, APP, CULTURE }

/** A list of days with one switch. [defaultOn] is the default for a new install. */
class DayList(val id: String, val group: DayGroup, val defaultOn: Boolean)

/** One line of the day table: it is active on the days its [rule] names, when its [list] is on, with [effect] at [strength] (0 to 1). */
class SceneDay(val id: String, val list: String, val rule: String, val effect: DayEffect, val strength: Float) {
    internal val compiled: SceneCalendar.Rule? = SceneCalendar.Rules.compile(rule)
}

/** A day the user added: month 1 to 12, day 1 to 31, a name of 1 to 24 characters (at most 12 of them are kept). */
data class UserDay(val month: Int, val day: Int, val name: String)

/** The days that are active at an instant: their ids, and the strongest strength of each effect. */
class ActiveDays(val ids: List<String>, val strength: Map<DayEffect, Float>)

/**
 * The calendar of special days. The table [DAYS_DATA] has one line per day (`list|id|rule|effect|strength`), so
 * adding a day is one line. Rules: `fixed:MM-DD`, `fixed:MM-DD..MM-DD` (may cross the new year), `always`,
 * `sunlon:<deg>` (the local day on which the sun's apparent longitude crosses the value), `moon:new` or
 * `moon:full` (the local day of that syzygy), `dates:YYYY-MM-DD+N,...` (N days from each date), `anniversary` (the
 * month and day of the first launch, from the next year on) and `eclipse` (a day with an eclipse of [Eclipses]).
 * A bad line is skipped. Every date and every crossing is judged in the zone that is passed in (the phone's).
 */
object SceneCalendar {
    internal sealed interface Rule {
        class Fixed(val start: Int, val end: Int) : Rule

        object Always : Rule

        class SunLon(val deg: Double) : Rule

        class Syzygy(val full: Boolean) : Rule

        class Dates(val starts: List<LocalDate>, val lengths: List<Int>) : Rule

        object Anniversary : Rule

        object EclipseDay : Rule
    }

    /** Parsing of the rule texts. A nested object, so the day table can be built while [SceneCalendar] itself is still starting. */
    internal object Rules {
        fun key(month: Int, day: Int): Int = month * 100 + day

        private fun parseKey(text: String): Int? {
            val parts = text.split("-")
            if (parts.size != 2) return null
            val m = parts[0].toIntOrNull() ?: return null
            val d = parts[1].toIntOrNull() ?: return null
            return try {
                MonthDay.of(m, d)
                key(m, d)
            } catch (e: DateTimeException) {
                null
            }
        }

        fun compile(rule: String): Rule? {
            if (rule == "always") return Rule.Always
            if (rule == "anniversary") return Rule.Anniversary
            if (rule == "eclipse") return Rule.EclipseDay
            val colon = rule.indexOf(':')
            if (colon <= 0) return null
            val body = rule.substring(colon + 1)
            return when (rule.substring(0, colon)) {
                "fixed" -> {
                    val range = body.split("..")
                    when (range.size) {
                        1 -> parseKey(range[0])?.let { Rule.Fixed(it, it) }
                        2 -> {
                            val s = parseKey(range[0])
                            val e = parseKey(range[1])
                            if (s != null && e != null) Rule.Fixed(s, e) else null
                        }
                        else -> null
                    }
                }
                "sunlon" -> body.toDoubleOrNull()?.takeIf { it >= 0.0 && it < 360.0 }?.let { Rule.SunLon(it) }
                "moon" -> when (body) {
                    "new" -> Rule.Syzygy(false)
                    "full" -> Rule.Syzygy(true)
                    else -> null
                }
                "dates" -> {
                    val starts = ArrayList<LocalDate>()
                    val lengths = ArrayList<Int>()
                    for (item in body.split(",")) {
                        val plus = item.indexOf('+')
                        if (plus <= 0) return null
                        val date = try {
                            LocalDate.parse(item.substring(0, plus))
                        } catch (e: DateTimeException) {
                            return null
                        }
                        val n = item.substring(plus + 1).toIntOrNull() ?: return null
                        if (n < 1 || n > 31) return null
                        starts.add(date)
                        lengths.add(n)
                    }
                    if (starts.isEmpty()) null else Rule.Dates(starts, lengths)
                }
                else -> null
            }
        }
    }

    private const val DAYS_DATA = """
sky|equinox-mar|sunlon:0|SUN_GOLD|0.6
sky|solstice-jun|sunlon:90|SUN_GOLD|1
sky|equinox-sep|sunlon:180|SUN_GOLD|0.6
sky|solstice-dec|sunlon:270|SUN_GOLD|1
sky|quadrantids|fixed:01-03..01-04|METEORS|0.8
sky|perseids|fixed:08-11..08-13|METEORS|1
sky|geminids|fixed:12-13..12-14|METEORS|1
sky|fullmoon|moon:full|MOON_GLOW|1
sky|eclipse|eclipse|ECLIPSE|1
app|anniversary|anniversary|PAPER_HAT|1
newroz|newroz-ridge|always|RIDGE_FIRES|0.5
newroz|newroz|fixed:03-20..03-22|RIDGE_FIRES|1
midsummer|midsummer|fixed:06-23..06-24|RIDGE_FIRES|1
newyear|newyear|fixed:12-31..01-01|FIREWORKS|1
bonfirenight|bonfirenight|fixed:11-05..11-05|FIREWORKS|1
"""

    val LISTS: List<DayList> = listOf(
        DayList("sky", DayGroup.SKY, true),
        DayList("app", DayGroup.APP, true),
        DayList("newroz", DayGroup.CULTURE, false),
        DayList("midsummer", DayGroup.CULTURE, false),
        DayList("newyear", DayGroup.CULTURE, false),
        DayList("bonfirenight", DayGroup.CULTURE, false),
    )

    val DAYS: List<SceneDay> = parse(DAYS_DATA)

    /** At most this many user days are kept. */
    const val MAX_USER_DAYS = 12

    /** Parses a day table; blank lines and lines that start with a hash are ignored, a bad line is skipped. */
    fun parse(text: String): List<SceneDay> {
        val out = ArrayList<SceneDay>()
        val seen = HashSet<String>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val f = line.split("|")
            if (f.size != 5) continue
            val list = f[0].trim()
            val id = f[1].trim()
            if (id.isEmpty() || list.isEmpty() || !seen.add(id)) continue
            val effect = DayEffect.values().firstOrNull { it.name == f[3].trim() } ?: continue
            val strength = f[4].trim().toFloatOrNull() ?: continue
            if (strength < 0f || strength > 1f) continue
            val day = SceneDay(id, list, f[2].trim(), effect, strength)
            if (day.compiled == null) continue
            out.add(day)
        }
        return out
    }

    private fun mod360(x: Double): Double = x - 360.0 * floor(x / 360.0)

    /** True when [angleOf] passes [target] (degrees, increasing in time) between [startMs] and [endMs]: below at the start, at or above at the end. */
    private fun crosses(target: Double, startMs: Long, endMs: Long, angleOf: (Long) -> Double): Boolean {
        val a0 = angleOf(startMs)
        val a1 = angleOf(endMs)
        val span = mod360(a1 - a0)
        val x = mod360(target - a0)
        return x > 0.0 && x <= span
    }

    private fun matches(day: SceneDay, date: LocalDate, zone: ZoneId, firstLaunchEpochDay: Long?): Boolean {
        val rule = day.compiled ?: return false
        return when (rule) {
            Rule.Always -> true
            is Rule.Fixed -> {
                val k = Rules.key(date.monthValue, date.dayOfMonth)
                if (rule.start <= rule.end) k >= rule.start && k <= rule.end else k >= rule.start || k <= rule.end
            }
            is Rule.SunLon -> {
                val s = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val e = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                crosses(rule.deg, s, e) { SkyClock.solarLongitudeDeg(it) }
            }
            is Rule.Syzygy -> {
                val s = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val e = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                crosses(if (rule.full) 180.0 else 0.0, s, e) { SkyClock.elongationDeg(it) }
            }
            is Rule.Dates -> {
                var hit = false
                for (i in rule.starts.indices) {
                    val first = rule.starts[i]
                    if (!date.isBefore(first) && date.isBefore(first.plusDays(rule.lengths[i].toLong()))) hit = true
                }
                hit
            }
            Rule.Anniversary -> {
                if (firstLaunchEpochDay == null) {
                    false
                } else {
                    val launch = LocalDate.ofEpochDay(firstLaunchEpochDay)
                    val leapDay = launch.monthValue == 2 && launch.dayOfMonth == 29
                    val dayOfMonth = if (leapDay && !date.isLeapYear) 28 else launch.dayOfMonth
                    date.year > launch.year && date.monthValue == launch.monthValue && date.dayOfMonth == dayOfMonth
                }
            }
            Rule.EclipseDay -> Eclipses.TABLE.any { Instant.ofEpochMilli(it.epochMs).atZone(zone).toLocalDate() == date }
        }
    }

    /**
     * The days that are active on [date] (the date in [zone]) when the lists in [lists] are on. A user day counts
     * when the `app` list is on. [firstLaunchEpochDay] is the local day count of the first launch, null if unknown.
     * [days] is the table to read (the shipped one unless a test brings its own).
     */
    fun active(
        date: LocalDate, zone: ZoneId, lists: Set<String>, user: List<UserDay>, firstLaunchEpochDay: Long?,
        days: List<SceneDay> = DAYS,
    ): ActiveDays {
        val ids = ArrayList<String>()
        val strength = EnumMap<DayEffect, Float>(DayEffect::class.java)
        for (d in days) {
            if (d.list !in lists) continue
            if (!matches(d, date, zone, firstLaunchEpochDay)) continue
            ids.add(d.id)
            strength[d.effect] = max(strength[d.effect] ?: 0f, d.strength)
        }
        if ("app" in lists) {
            for (u in user) {
                if (u.month == date.monthValue && u.day == date.dayOfMonth) {
                    ids.add("user:" + u.name)
                    strength[DayEffect.PAPER_HAT] = 1f
                }
            }
        }
        return ActiveDays(ids, strength)
    }

    /** The lists that are on before the user chooses: the sky's and the app's, plus Newroz on an install that already had the old ridge fires. */
    fun defaultLists(existingInstall: Boolean): Set<String> {
        val out = LinkedHashSet<String>()
        for (l in LISTS) if (l.defaultOn) out.add(l.id)
        if (existingInstall) out.add("newroz")
        return out
    }

    /**
     * Whether this app has been used before: the Linux image is installed (`rootfs.stamp`) or a conversation list
     * exists. `chat/bots.json` does not count: the first run writes it on every fresh install.
     */
    fun existingInstall(filesDir: File): Boolean =
        File(filesDir, "rootfs.stamp").exists() || File(filesDir, "chat/conversations.json").exists()

    /** True when the user day may be stored: month and day exist in some year and the name has 1 to 24 characters. */
    fun validUserDay(u: UserDay): Boolean {
        if (u.name.isEmpty() || u.name.length > 24) return false
        return try {
            MonthDay.of(u.month, u.day)
            true
        } catch (e: DateTimeException) {
            false
        }
    }
}

/** One eclipse: its greatest moment, whether it is a lunar one, and its kind (0 penumbral, 1 partial, 2 total or annular). */
class Eclipse(val epochMs: Long, val lunar: Boolean, val kind: Int)

/**
 * The eclipses of 2026 to 2030 (UTC instants of greatest eclipse, from the published lists; each is checked
 * against the sun's and the moon's longitudes in the tests). After 2030 there is no eclipse effect.
 */
object Eclipses {
    private fun utc(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        LocalDateTime.of(y, mo, d, h, mi).toInstant(ZoneOffset.UTC).toEpochMilli()

    val TABLE: List<Eclipse> = listOf(
        Eclipse(utc(2026, 2, 17, 12, 12), false, 2),
        Eclipse(utc(2026, 3, 3, 11, 33), true, 2),
        Eclipse(utc(2026, 8, 12, 17, 46), false, 2),
        Eclipse(utc(2026, 8, 28, 4, 13), true, 1),
        Eclipse(utc(2027, 2, 6, 16, 0), false, 2),
        Eclipse(utc(2027, 2, 20, 23, 13), true, 0),
        Eclipse(utc(2027, 7, 18, 16, 3), true, 0),
        Eclipse(utc(2027, 8, 2, 10, 7), false, 2),
        Eclipse(utc(2027, 8, 17, 7, 14), true, 0),
        Eclipse(utc(2028, 1, 12, 4, 13), true, 1),
        Eclipse(utc(2028, 1, 26, 15, 8), false, 2),
        Eclipse(utc(2028, 7, 6, 18, 20), true, 1),
        Eclipse(utc(2028, 7, 22, 2, 56), false, 2),
        Eclipse(utc(2028, 12, 31, 16, 52), true, 2),
        Eclipse(utc(2029, 1, 14, 17, 13), false, 1),
        Eclipse(utc(2029, 6, 12, 4, 6), false, 1),
        Eclipse(utc(2029, 6, 26, 3, 22), true, 2),
        Eclipse(utc(2029, 7, 11, 15, 37), false, 1),
        Eclipse(utc(2029, 12, 5, 15, 3), false, 1),
        Eclipse(utc(2029, 12, 20, 22, 42), true, 2),
        Eclipse(utc(2030, 6, 1, 6, 29), false, 2),
        Eclipse(utc(2030, 6, 15, 18, 33), true, 1),
        Eclipse(utc(2030, 11, 25, 6, 51), false, 2),
        Eclipse(utc(2030, 12, 9, 22, 28), true, 0),
    )

    /**
     * How red the moon is at [epochMs], 0 to 1: 1 at the greatest moment of a partial or total lunar eclipse, falling
     * to 0 over 40 minutes (partial) or 60 minutes (total) on either side. Penumbral eclipses and solar ones give 0.
     */
    fun redAt(epochMs: Long): Float {
        var best = 0f
        for (e in TABLE) {
            if (!e.lunar || e.kind < 1) continue
            val half = (if (e.kind == 2) 60L else 40L) * 60_000L
            val dt = abs(epochMs - e.epochMs)
            if (dt >= half) continue
            best = max(best, 1f - dt.toFloat() / half.toFloat())
        }
        return best
    }
}
