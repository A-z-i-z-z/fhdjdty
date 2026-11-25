package dev.pocketagent.app.ui.pixel.stage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class SceneCalendarTest {
    private val utc: ZoneId = ZoneOffset.UTC
    private val skyOnly = setOf("sky")

    private fun ids(date: LocalDate, lists: Set<String>, zone: ZoneId = utc, user: List<UserDay> = emptyList(), first: Long? = null): List<String> =
        SceneCalendar.active(date, zone, lists, user, first).ids

    /** The dates in [from] to [to] on which [id] is active. */
    private fun datesOf(id: String, from: LocalDate, to: LocalDate, lists: Set<String>, zone: ZoneId = utc): List<LocalDate> {
        val out = ArrayList<LocalDate>()
        var d = from
        while (!d.isAfter(to)) {
            if (ids(d, lists, zone).contains(id)) out.add(d)
            d = d.plusDays(1)
        }
        return out
    }

    @Test fun dataParses() {
        assertEquals(15, SceneCalendar.DAYS.size)
        val seen = HashSet<String>()
        val known = SceneCalendar.LISTS.map { it.id }.toSet()
        for (d in SceneCalendar.DAYS) {
            assertTrue("duplicate id ${d.id}", seen.add(d.id))
            assertTrue("unknown list ${d.list}", d.list in known)
            assertNotNull("rule of ${d.id} compiles", d.compiled)
            assertTrue(d.strength > 0f && d.strength <= 1f)
        }
        assertEquals(listOf("sky", "app", "newroz", "midsummer", "newyear", "bonfirenight"), SceneCalendar.LISTS.map { it.id })
        assertEquals(setOf("sky", "app"), SceneCalendar.LISTS.filter { it.defaultOn }.map { it.id }.toSet())
        assertEquals(DayGroup.SKY, SceneCalendar.LISTS.first { it.id == "sky" }.group)
        assertEquals(DayGroup.APP, SceneCalendar.LISTS.first { it.id == "app" }.group)
        for (l in SceneCalendar.LISTS.filter { it.id != "sky" && it.id != "app" }) assertEquals(DayGroup.CULTURE, l.group)
        // the two calendars of uncertain dates are not part of this wave
        assertTrue(SceneCalendar.DAYS.none { it.id == "lny" || it.id == "diwali" })
        assertTrue(DayEffect.values().none { it.name == "LANTERNS" })
    }

    @Test fun parserSkipsBadLinesAndKeepsGoodOnes() {
        val text = """
            # a comment
            sky|ok1|fixed:02-01|SUN_GOLD|0.5

            sky|ok2|always|METEORS|1
            sky|badeffect|always|LANTERNS|1
            sky|badstrength|always|METEORS|2
            sky|badrule|fixed:02-30|METEORS|1
            sky|badrule2|nonsense|METEORS|1
            sky|badfields|always|METEORS
            sky|ok1|always|METEORS|1
            sky|baddates|dates:2027-13-01+3|METEORS|1
            sky|badlon|sunlon:400|METEORS|1
            sky|badmoon|moon:half|METEORS|1
            sky|ok3|dates:2027-02-06+3,2028-01-26+3|METEORS|1
        """.trimIndent()
        val days = SceneCalendar.parse(text)
        assertEquals(listOf("ok1", "ok2", "ok3"), days.map { it.id })
        assertEquals(0.5f, days[0].strength, 0f)
        assertEquals(DayEffect.SUN_GOLD, days[0].effect)
    }

    @Test fun fixedAcrossNewYear() {
        val lists = setOf("newyear")
        assertEquals(
            listOf(LocalDate.of(2027, 12, 31), LocalDate.of(2028, 1, 1)),
            datesOf("newyear", LocalDate.of(2027, 12, 29), LocalDate.of(2028, 1, 3), lists),
        )
        assertEquals(
            listOf(LocalDate.of(2027, 11, 5)),
            datesOf("bonfirenight", LocalDate.of(2027, 11, 3), LocalDate.of(2027, 11, 7), setOf("bonfirenight")),
        )
        assertEquals(
            listOf(LocalDate.of(2027, 6, 23), LocalDate.of(2027, 6, 24)),
            datesOf("midsummer", LocalDate.of(2027, 6, 21), LocalDate.of(2027, 6, 26), setOf("midsummer")),
        )
        assertEquals(
            listOf(LocalDate.of(2027, 8, 11), LocalDate.of(2027, 8, 12), LocalDate.of(2027, 8, 13)),
            datesOf("perseids", LocalDate.of(2027, 8, 9), LocalDate.of(2027, 8, 15), skyOnly),
        )
        assertEquals(
            listOf(LocalDate.of(2027, 1, 3), LocalDate.of(2027, 1, 4)),
            datesOf("quadrantids", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 6), skyOnly),
        )
    }

    @Test fun equinoxExactlyOneDay() {
        assertEquals(listOf(LocalDate.of(2027, 3, 20)), datesOf("equinox-mar", LocalDate.of(2027, 3, 19), LocalDate.of(2027, 3, 21), skyOnly))
        assertEquals(listOf(LocalDate.of(2026, 9, 23)), datesOf("equinox-sep", LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 24), skyOnly))
    }

    @Test fun equinoxIsJudgedInThePhonesZone() {
        // the March 2027 equinox is 20:25 UTC on the 20th: 23:25 on the 20th in Istanbul, 00:25 on the 21st in Dubai
        val istanbul = ZoneId.of("Europe/Istanbul")
        val dubai = ZoneId.of("Asia/Dubai")
        val from = LocalDate.of(2027, 3, 19)
        val to = LocalDate.of(2027, 3, 22)
        assertEquals(listOf(LocalDate.of(2027, 3, 20)), datesOf("equinox-mar", from, to, skyOnly, istanbul))
        assertEquals(listOf(LocalDate.of(2027, 3, 21)), datesOf("equinox-mar", from, to, skyOnly, dubai))
    }

    @Test fun solsticeDec2026() {
        assertEquals(listOf(LocalDate.of(2026, 12, 21)), datesOf("solstice-dec", LocalDate.of(2026, 12, 19), LocalDate.of(2026, 12, 23), skyOnly))
        assertEquals(listOf(LocalDate.of(2027, 6, 21)), datesOf("solstice-jun", LocalDate.of(2027, 6, 19), LocalDate.of(2027, 6, 23), skyOnly))
    }

    @Test fun fullMoonDay() {
        assertEquals(listOf(LocalDate.of(2025, 3, 14)), datesOf("fullmoon", LocalDate.of(2025, 3, 13), LocalDate.of(2025, 3, 15), skyOnly))
        // a month holds one full moon day, and 12 or 13 of them make a year
        var count = 0
        var d = LocalDate.of(2027, 1, 1)
        while (d.year == 2027) {
            if (ids(d, skyOnly).contains("fullmoon")) count++
            d = d.plusDays(1)
        }
        assertTrue("full moon days in 2027: $count", count == 12 || count == 13)
    }

    @Test fun newMoonRuleWorks() {
        val days = SceneCalendar.parse("sky|nm|moon:new|MOON_GLOW|1")
        fun on(date: LocalDate) = SceneCalendar.active(date, utc, skyOnly, emptyList(), null, days).ids.contains("nm")
        assertTrue(on(LocalDate.of(2024, 4, 8)))
        assertFalse(on(LocalDate.of(2024, 4, 7)))
        assertFalse(on(LocalDate.of(2024, 4, 9)))
    }

    @Test fun datesTable() {
        val days = SceneCalendar.parse("x|lny|dates:2027-02-06+3,2028-01-26+3|SUN_GOLD|1")
        fun on(date: LocalDate) = SceneCalendar.active(date, utc, setOf("x"), emptyList(), null, days).ids.contains("lny")
        assertFalse(on(LocalDate.of(2027, 2, 5)))
        assertTrue(on(LocalDate.of(2027, 2, 6)))
        assertTrue(on(LocalDate.of(2027, 2, 7)))
        assertTrue(on(LocalDate.of(2027, 2, 8)))
        assertFalse(on(LocalDate.of(2027, 2, 9)))
        assertTrue(on(LocalDate.of(2028, 1, 27)))
    }

    @Test fun listsOffByDefault() {
        // 21 March: nothing of Newroz unless its list is on
        assertTrue(ids(LocalDate.of(2027, 3, 21), setOf("app")).none { it.startsWith("newroz") })
        assertFalse(ids(LocalDate.of(2027, 3, 21), SceneCalendar.defaultLists(false)).any { it.startsWith("newroz") })
        val on = ids(LocalDate.of(2027, 3, 21), SceneCalendar.defaultLists(false) + "newroz")
        assertTrue(on.contains("newroz"))
        assertTrue(on.contains("newroz-ridge"))
        // an existing install keeps the quiet fires on any night
        val quiet = SceneCalendar.active(LocalDate.of(2027, 5, 2), utc, SceneCalendar.defaultLists(true), emptyList(), null)
        assertEquals(listOf("newroz-ridge"), quiet.ids)
        assertEquals(0.5f, quiet.strength[DayEffect.RIDGE_FIRES]!!, 0f)
        // the festival is stronger than the quiet fires and wins
        val festive = SceneCalendar.active(LocalDate.of(2027, 3, 21), utc, SceneCalendar.defaultLists(true), emptyList(), null)
        assertEquals(1f, festive.strength[DayEffect.RIDGE_FIRES]!!, 0f)
    }

    @Test fun userDay() {
        val mine = listOf(UserDay(7, 4, "July"))
        val withApp = SceneCalendar.active(LocalDate.of(2027, 7, 4), utc, setOf("sky", "app"), mine, null)
        assertTrue(withApp.ids.contains("user:July"))
        assertEquals(1f, withApp.strength[DayEffect.PAPER_HAT]!!, 0f)
        assertFalse(SceneCalendar.active(LocalDate.of(2027, 7, 4), utc, setOf("sky"), mine, null).ids.contains("user:July"))
        assertFalse(SceneCalendar.active(LocalDate.of(2027, 7, 5), utc, setOf("sky", "app"), mine, null).ids.contains("user:July"))
        assertTrue(SceneCalendar.validUserDay(UserDay(2, 29, "Leap")))
        assertFalse(SceneCalendar.validUserDay(UserDay(2, 30, "Never")))
        assertFalse(SceneCalendar.validUserDay(UserDay(13, 1, "Never")))
        assertFalse(SceneCalendar.validUserDay(UserDay(1, 1, "")))
        assertFalse(SceneCalendar.validUserDay(UserDay(1, 1, "x".repeat(25))))
        assertTrue(SceneCalendar.validUserDay(UserDay(1, 1, "x".repeat(24))))
    }

    @Test fun anniversary() {
        val first = LocalDate.of(2026, 3, 21).toEpochDay()
        assertFalse("not on the day itself", ids(LocalDate.of(2026, 3, 21), setOf("app"), first = first).contains("anniversary"))
        assertTrue(ids(LocalDate.of(2027, 3, 21), setOf("app"), first = first).contains("anniversary"))
        assertTrue(ids(LocalDate.of(2028, 3, 21), setOf("app"), first = first).contains("anniversary"))
        assertFalse(ids(LocalDate.of(2027, 3, 22), setOf("app"), first = first).contains("anniversary"))
        assertFalse(ids(LocalDate.of(2027, 3, 21), setOf("app"), first = null).contains("anniversary"))
        assertFalse(ids(LocalDate.of(2027, 3, 21), setOf("sky"), first = first).contains("anniversary"))
        // a first launch on 29 February is celebrated on 28 February in a year that has none
        val leap = LocalDate.of(2024, 2, 29).toEpochDay()
        assertTrue(ids(LocalDate.of(2025, 2, 28), setOf("app"), first = leap).contains("anniversary"))
        assertFalse(ids(LocalDate.of(2025, 3, 1), setOf("app"), first = leap).contains("anniversary"))
        assertTrue(ids(LocalDate.of(2028, 2, 29), setOf("app"), first = leap).contains("anniversary"))
        assertFalse(ids(LocalDate.of(2028, 2, 28), setOf("app"), first = leap).contains("anniversary"))
    }

    @Test fun eclipseDayFollowsTheTable() {
        assertTrue(ids(LocalDate.of(2028, 12, 31), skyOnly).contains("eclipse"))
        assertTrue(ids(LocalDate.of(2026, 3, 3), skyOnly).contains("eclipse"))
        assertFalse(ids(LocalDate.of(2026, 3, 4), skyOnly).contains("eclipse"))
        assertFalse(ids(LocalDate.of(2028, 12, 31), setOf("app")).contains("eclipse"))
        assertEquals(1f, SceneCalendar.active(LocalDate.of(2028, 12, 31), utc, skyOnly, emptyList(), null).strength[DayEffect.ECLIPSE]!!, 0f)
    }

    @Test fun migration() {
        assertEquals(setOf("sky", "app", "newroz"), SceneCalendar.defaultLists(true))
        assertEquals(setOf("sky", "app"), SceneCalendar.defaultLists(false))
    }

    @Test fun existingInstall() {
        val dir = Files.createTempDirectory("agiri-install").toFile()
        try {
            assertFalse("empty folder", SceneCalendar.existingInstall(dir))
            File(dir, "chat").mkdirs()
            File(dir, "chat/bots.json").writeText("[]")
            assertFalse("only bots.json (every fresh install writes it)", SceneCalendar.existingInstall(dir))
            File(dir, "chat/conversations.json").writeText("[]")
            assertTrue("conversations.json", SceneCalendar.existingInstall(dir))
            File(dir, "chat/conversations.json").delete()
            assertFalse(SceneCalendar.existingInstall(dir))
            File(dir, "rootfs.stamp").writeText("x")
            assertTrue("rootfs.stamp", SceneCalendar.existingInstall(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun strongestStrengthPerEffectWins() {
        val days = SceneCalendar.parse("a|one|always|METEORS|0.3\na|two|always|METEORS|0.9\na|three|always|SUN_GOLD|0.2")
        val r = SceneCalendar.active(LocalDate.of(2027, 1, 1), utc, setOf("a"), emptyList(), null, days)
        assertEquals(listOf("one", "two", "three"), r.ids)
        assertEquals(0.9f, r.strength[DayEffect.METEORS]!!, 0f)
        assertEquals(0.2f, r.strength[DayEffect.SUN_GOLD]!!, 0f)
    }
}
