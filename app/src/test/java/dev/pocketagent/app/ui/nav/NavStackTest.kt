package dev.pocketagent.app.ui.nav

import dev.pocketagent.app.ui.NavEntry
import dev.pocketagent.app.ui.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavStackTest {
    private fun NavStack.ids(): List<Long> = entries.map { it.id }
    private fun NavStack.routes(): List<Route> = entries.map { it.route }

    @Test fun pushGivesIncreasingFreshIds() {
        val s = NavStack()
        assertEquals(listOf(1L), s.ids())
        assertTrue(s.go(Route.Chats))
        assertTrue(s.go(Route.Chat("a")))
        assertTrue(s.go(Route.Hermes))
        assertEquals(listOf(1L, 100L, 101L, 102L), s.ids())
        assertEquals(100L, NavStack.FIRST_FREE_ID)
    }

    @Test fun goOnSameTopIsNoOp() {
        val s = NavStack()
        assertTrue(s.go(Route.Chats))
        val before = s.entries
        assertFalse(s.go(Route.Chats))
        assertEquals(before, s.entries)
        // The id counter did not move either: the next push gets the next id.
        s.go(Route.Hermes)
        assertEquals(101L, s.entries.last().id)
    }

    @Test fun sameRouteTwiceOnStackHasTwoIds() {
        val s = NavStack()
        s.go(Route.Chat("a"))
        s.go(Route.Hermes)
        s.go(Route.Chat("a"))
        val ids = s.ids()
        assertEquals(4, ids.size)
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(listOf(Route.Home, Route.Chat("a"), Route.Hermes, Route.Chat("a")), s.routes())
    }

    @Test fun goHomeResetsToHomeRoot() {
        val s = NavStack(listOf(Route.Chats, Route.Chat("x")))
        assertTrue(s.go(Route.Home))
        assertEquals(listOf(NavEntry(1L, Route.Home)), s.entries)
        // Already there: nothing changes.
        assertFalse(s.go(Route.Home))
    }

    @Test fun backPopsTopAndFalseAtRoot() {
        val s = NavStack()
        s.go(Route.Chats)
        s.go(Route.Chat("a"))
        assertTrue(s.back())
        assertEquals(listOf(Route.Home, Route.Chats), s.routes())
        assertTrue(s.back())
        assertEquals(listOf(Route.Home), s.routes())
        val before = s.entries
        assertFalse(s.back())
        assertEquals(before, s.entries)
    }

    @Test fun tabKeepsStableRootIdPerTab() {
        val s = NavStack()
        s.tab(Route.Chats)
        assertEquals(listOf(2L), s.ids())
        s.tab(Route.Bots)
        assertEquals(listOf(3L), s.ids())
        s.tab(Route.Hermes)
        assertEquals(listOf(4L), s.ids())
        s.tab(Route.Settings(null))
        assertEquals(listOf(5L), s.ids())
        s.tab(Route.Home)
        assertEquals(listOf(1L), s.ids())
        // A settings section is not a tab root: it gets a fresh id.
        s.tab(Route.Settings("usage"))
        assertTrue(s.entries.single().id >= NavStack.FIRST_FREE_ID)
        assertNull(NavStack.rootIdFor(Route.Settings("usage")))
        assertNull(NavStack.rootIdFor(Route.Chat("a")))
        assertNull(NavStack.rootIdFor(Route.Terminal))
        assertNull(NavStack.rootIdFor(Route.Gallery))
    }

    @Test fun tabDropsEverythingAboveRootAndKeepsRootId() {
        val s = NavStack(listOf(Route.Chats))
        s.go(Route.Chat("a"))
        s.go(Route.Hermes)
        s.tab(Route.Chats)
        assertEquals(listOf(NavEntry(2L, Route.Chats)), s.entries)
    }

    @Test fun replaceGivesNewIdAndKeepsDepth() {
        val s = NavStack(listOf(Route.Chats, Route.Chat("a")))
        val oldTop = s.entries.last()
        s.replace(Route.Chat("b"))
        assertEquals(2, s.entries.size)
        assertEquals(Route.Chat("b"), s.entries.last().route)
        assertNotEquals(oldTop.id, s.entries.last().id)
        assertEquals(2L, s.entries.first().id)
        // Equal top: nothing changes.
        val before = s.entries
        s.replace(Route.Chat("b"))
        assertEquals(before, s.entries)
        // On a root it equals tab.
        val r = NavStack()
        r.replace(Route.Bots)
        assertEquals(listOf(NavEntry(3L, Route.Bots)), r.entries)
    }

    @Test fun resetBuildsStackWithRootIdAtBottom() {
        val s = NavStack()
        s.reset(listOf(Route.Chats, Route.Chat("x")))
        assertEquals(2L, s.entries[0].id)
        assertTrue(s.entries[1].id >= NavStack.FIRST_FREE_ID)
        s.reset(emptyList())
        assertEquals(listOf(NavEntry(1L, Route.Home)), s.entries)
        s.reset(listOf(Route.Home, Route.Routines, Route.RoutineEditor(null)))
        assertEquals(listOf(Route.Home, Route.Routines, Route.RoutineEditor(null)), s.routes())
        assertEquals(1L, s.entries[0].id)
        assertEquals(3, s.ids().toSet().size)
    }

    @Test fun encodeDecodeRoundTripKeepsIdsAndCounter() {
        val s = NavStack()
        s.go(Route.Chats)
        s.go(Route.Chat("a"))
        val saved = s.encode()
        assertEquals(listOf("#102", "1|home", "100|chats", "101|chat:a"), saved)
        val back = NavStack.decode(saved)
        assertEquals(s.entries, back.entries)
        back.go(Route.Hermes)
        assertEquals(102L, back.entries.last().id)
    }

    @Test fun decodeOldFormatGivesFreshIds() {
        val s = NavStack.decode(listOf("chats", "chat:x"))
        assertEquals(listOf(Route.Chats, Route.Chat("x")), s.routes())
        assertEquals(2L, s.entries[0].id)
        assertTrue(s.entries[1].id >= NavStack.FIRST_FREE_ID)
    }

    @Test fun decodeDropsUnknownKeysAndFixesBadIds() {
        val s = NavStack.decode(listOf("#5", "1|home", "100|nope", "100|chats", "100|chat:a", "3|bots"))
        assertEquals(listOf(Route.Home, Route.Chats, Route.Chat("a"), Route.Bots), s.routes())
        val ids = s.ids()
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(1L, ids[0])
        assertEquals(100L, ids[1])
        assertTrue(ids.drop(1).all { it >= NavStack.FIRST_FREE_ID })
        // The counter is above every id in use, whatever the saved counter said.
        val highest = ids.max()
        s.go(Route.Hermes)
        assertTrue(s.entries.last().id > highest)
        // A root id on the wrong route is replaced by the id the tab of that route has.
        assertEquals(listOf(1L), NavStack.decode(listOf("#100", "2|home")).ids())
        // Garbage in the id position makes the whole element a key, which is unknown.
        assertEquals(listOf(Route.Home), NavStack.decode(listOf("x1|chats")).routes())
        // Empty gives Home; so does a list with nothing readable; the counter is at least 100.
        assertEquals(listOf(NavEntry(1L, Route.Home)), NavStack.decode(emptyList()).entries)
        val none = NavStack.decode(listOf("#7", "nope"))
        assertEquals(listOf(NavEntry(1L, Route.Home)), none.entries)
        none.go(Route.Chats)
        assertEquals(100L, none.entries.last().id)
    }

    @Test fun isRootIdOnlyForOneToFive() {
        assertFalse(NavStack.isRootId(0))
        assertTrue(NavStack.isRootId(1))
        assertTrue(NavStack.isRootId(5))
        assertFalse(NavStack.isRootId(6))
        assertFalse(NavStack.isRootId(99))
        assertFalse(NavStack.isRootId(100))
    }
}
