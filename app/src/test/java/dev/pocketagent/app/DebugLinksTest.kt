package dev.pocketagent.app

import android.content.Intent
import dev.pocketagent.app.ui.Route
import dev.pocketagent.app.ui.decodeRoute
import dev.pocketagent.app.ui.showsBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class DebugLinksTest {
    private val utc: ZoneId = ZoneId.of("UTC")
    private val action = AppLinks.ACTION_DEBUG

    private fun parse(vararg extras: Pair<String, String?>, twin: Boolean = false): DebugRequest? =
        DebugLinks.parseParts(action, mapOf(*extras), 0, debug = true, zone = utc, isTwin = twin)

    private fun parseOk(vararg extras: Pair<String, String?>, twin: Boolean = false): DebugRequest {
        val r = parse(*extras, twin = twin)
        assertNotNull(r)
        return r!!
    }

    @Test fun releaseBuildIgnoresTheAction() {
        val extras = mapOf(DebugLinks.EXTRA_ROUTE to "chats", DebugLinks.EXTRA_RESET to "1", DebugLinks.EXTRA_LANG to "tr")
        assertNull(DebugLinks.parseParts(action, extras, 0, debug = false, zone = utc))
        assertNull(DebugLinks.parseParts(action, emptyMap(), 0, debug = false, zone = utc))
        assertNull(DebugLinks.parse(null))
        assertNull(DebugLinks.parse(null, debug = false))
    }

    @Test fun wrongActionIsNull() {
        val extras = mapOf(DebugLinks.EXTRA_ROUTE to "chats")
        assertNull(DebugLinks.parseParts(AppLinks.ACTION_OPEN_CHAT, extras, 0, debug = true, zone = utc))
        assertNull(DebugLinks.parseParts(Intent.ACTION_MAIN, extras, 0, debug = true, zone = utc))
        assertNull(DebugLinks.parseParts(null, extras, 0, debug = true, zone = utc))
        // An intent replayed from Recents is ignored.
        assertNull(DebugLinks.parseParts(action, extras, Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY, debug = true, zone = utc))
        // Other flags alone do not matter.
        assertNotNull(DebugLinks.parseParts(action, extras, Intent.FLAG_ACTIVITY_NEW_TASK, debug = true, zone = utc))
    }

    @Test fun routeKeysParse() {
        val keys = listOf(
            "home", "chats", "chat:abc123", "bots", "botnew", "bot:b1", "hermes", "terminal", "settings", "settings:usage",
            "routines", "routinenew", "routine:r1", "starred", "gallery",
        )
        for (key in keys) {
            val r = parseOk(DebugLinks.EXTRA_ROUTE to key)
            assertEquals(key, decodeRoute(key), r.route)
            assertTrue(key, r.rejected.isEmpty())
        }
        val bad = parseOk(DebugLinks.EXTRA_ROUTE to "nope")
        assertNull(bad.route)
        assertEquals(listOf(DebugLinks.EXTRA_ROUTE), bad.rejected)
    }

    @Test fun clockForms() {
        val local = LocalDateTime.of(2027, 3, 21, 20, 30).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(Setting.Set(local), parseOk(DebugLinks.EXTRA_CLOCK to "2027-03-21T20:30").clock)
        assertEquals(Setting.Set(local + 15_000L), parseOk(DebugLinks.EXTRA_CLOCK to "2027-03-21T20:30:15").clock)
        assertEquals(Setting.Set(local - 3 * 3_600_000L), parseOk(DebugLinks.EXTRA_CLOCK to "2027-03-21T20:30:00+03:00").clock)
        assertEquals(Setting.Set(local), parseOk(DebugLinks.EXTRA_CLOCK to "2027-03-21T20:30:00Z").clock)
        assertEquals(Setting.Clear, parseOk(DebugLinks.EXTRA_CLOCK to "off").clock)
        val bad = parseOk(DebugLinks.EXTRA_CLOCK to "tomorrow at noon")
        assertEquals(Setting.Keep, bad.clock)
        assertEquals(listOf(DebugLinks.EXTRA_CLOCK), bad.rejected)
    }

    @Test fun langAndRtlForms() {
        assertEquals(Setting.Set("tr"), parseOk(DebugLinks.EXTRA_LANG to "tr").language)
        assertEquals(Setting.Set("en-XZ"), parseOk(DebugLinks.EXTRA_LANG to "en-XZ").language)
        assertEquals(Setting.Set("en-XZ"), parseOk(DebugLinks.EXTRA_LANG to "en_XZ").language)
        assertEquals(Setting.Clear, parseOk(DebugLinks.EXTRA_LANG to "system").language)
        val blank = parseOk(DebugLinks.EXTRA_LANG to "  ")
        assertEquals(Setting.Keep, blank.language)
        assertEquals(listOf(DebugLinks.EXTRA_LANG), blank.rejected)
        assertEquals(listOf(DebugLinks.EXTRA_LANG), parseOk(DebugLinks.EXTRA_LANG to "!!").rejected)

        for (w in listOf("1", "true", "on", "TRUE")) assertEquals(w, Setting.Set(true), parseOk(DebugLinks.EXTRA_RTL to w).rtl)
        for (w in listOf("0", "false", "off")) assertEquals(w, Setting.Set(false), parseOk(DebugLinks.EXTRA_RTL to w).rtl)
        assertEquals(Setting.Clear, parseOk(DebugLinks.EXTRA_RTL to "auto").rtl)
        val bad = parseOk(DebugLinks.EXTRA_RTL to "sideways")
        assertEquals(Setting.Keep, bad.rtl)
        assertEquals(listOf(DebugLinks.EXTRA_RTL), bad.rejected)
    }

    @Test fun resetFlagAndKeep() {
        val none = parseOk()
        assertFalse(none.reset)
        assertNull(none.route)
        assertEquals(Setting.Keep, none.clock)
        assertEquals(Setting.Keep, none.language)
        assertEquals(Setting.Keep, none.rtl)
        assertNull(none.seed)
        assertNull(none.nav)
        assertTrue(none.rejected.isEmpty())
        assertTrue(parseOk(DebugLinks.EXTRA_RESET to "1").reset)
        assertFalse(parseOk(DebugLinks.EXTRA_RESET to "0").reset)
        assertEquals(listOf(DebugLinks.EXTRA_RESET), parseOk(DebugLinks.EXTRA_RESET to "maybe").rejected)
        // Missing and null extras are the same thing.
        assertEquals(Setting.Keep, parseOk(DebugLinks.EXTRA_CLOCK to null).clock)
    }

    @Test fun seedOnlyOnTheTwinAndNavFlagsMustBeReadable() {
        val twin = parseOk(DebugLinks.EXTRA_SEED to "chats:120", twin = true)
        assertEquals("chats:120", twin.seed)
        assertTrue(twin.rejected.isEmpty())
        val other = parseOk(DebugLinks.EXTRA_SEED to "chats:120", twin = false)
        assertNull(other.seed)
        assertEquals(listOf(DebugLinks.EXTRA_SEED), other.rejected)

        assertEquals("back=0,shared=0", parseOk(DebugLinks.EXTRA_NAV to "back=0,shared=0").nav)
        val bad = parseOk(DebugLinks.EXTRA_NAV to "sparkle=1")
        assertNull(bad.nav)
        assertEquals(listOf(DebugLinks.EXTRA_NAV), bad.rejected)
    }

    @Test fun tokenMustMatchExactly() {
        val t = "0123456789abcdef0123456789abcdef"
        assertTrue(DebugLinks.tokenAccepted(t, t))
        assertTrue("whitespace around either side does not matter", DebugLinks.tokenAccepted(" $t\n", "$t\n"))
        assertFalse(DebugLinks.tokenAccepted(null, t))
        assertFalse(DebugLinks.tokenAccepted(t, null))
        assertFalse(DebugLinks.tokenAccepted(null, null))
        assertFalse(DebugLinks.tokenAccepted("", t))
        assertFalse(DebugLinks.tokenAccepted(t.dropLast(1), t))
        assertFalse(DebugLinks.tokenAccepted(t + "0", t))
        assertFalse(DebugLinks.tokenAccepted("f" + t.drop(1), t))
        // an empty or too short token file never accepts anything, not even an empty extra
        assertFalse(DebugLinks.tokenAccepted("", ""))
        assertFalse(DebugLinks.tokenAccepted("abc", "abc"))
    }

    @Test fun tokenFileIsMadeOnceAndKept() {
        val dir = java.nio.file.Files.createTempDirectory("agiri-token").toFile()
        try {
            val file = java.io.File(dir, DebugLinks.TOKEN_FILE)
            DebugLinks.ensureToken(dir, debug = false)
            assertFalse("a build without test hooks writes nothing", file.exists())
            DebugLinks.ensureToken(dir, debug = true)
            val first = DebugLinks.readToken(file)
            assertNotNull(first)
            assertTrue(Regex("[0-9a-f]{32}").matches(first!!))
            DebugLinks.ensureToken(dir, debug = true)
            assertEquals("the token is never rotated", first, DebugLinks.readToken(file))
            // a damaged file is replaced
            file.writeText("x")
            assertNull(DebugLinks.readToken(file))
            DebugLinks.ensureToken(dir, debug = true)
            assertNotNull(DebugLinks.readToken(file))
            assertNull(DebugLinks.readToken(java.io.File(dir, "missing")))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun stackForEveryRoute() {
        val routes = listOf(
            Route.Home, Route.Chats, Route.Chat("c1"), Route.Bots, Route.BotEditor(null), Route.BotEditor("b1"), Route.Hermes,
            Route.Terminal, Route.Settings(null), Route.Settings("usage"), Route.Routines, Route.RoutineEditor(null),
            Route.RoutineEditor("r1"), Route.Starred, Route.Gallery,
        )
        for (r in routes) {
            val stack = DebugLinks.stackFor(r)
            assertEquals(r.toString(), r, stack.last())
            assertTrue(r.toString(), stack.first().showsBar())
        }
        assertEquals(listOf(Route.Home), DebugLinks.stackFor(Route.Home))
        assertEquals(listOf(Route.Chats, Route.Chat("c1")), DebugLinks.stackFor(Route.Chat("c1")))
        assertEquals(listOf(Route.Chats, Route.Starred), DebugLinks.stackFor(Route.Starred))
        assertEquals(listOf(Route.Bots, Route.BotEditor(null)), DebugLinks.stackFor(Route.BotEditor(null)))
        assertEquals(listOf(Route.Hermes, Route.Terminal), DebugLinks.stackFor(Route.Terminal))
        assertEquals(listOf(Route.Home, Route.Routines), DebugLinks.stackFor(Route.Routines))
        assertEquals(listOf(Route.Home, Route.Routines, Route.RoutineEditor("r1")), DebugLinks.stackFor(Route.RoutineEditor("r1")))
        assertEquals(listOf(Route.Settings(null)), DebugLinks.stackFor(Route.Settings(null)))
        assertEquals(listOf(Route.Settings(null), Route.Settings("usage")), DebugLinks.stackFor(Route.Settings("usage")))
        assertEquals(listOf(Route.Home, Route.Gallery), DebugLinks.stackFor(Route.Gallery))
    }
}
