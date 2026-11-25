package dev.pocketagent.app.ui.nav

import dev.pocketagent.app.ui.Route
import dev.pocketagent.app.ui.rank
import dev.pocketagent.app.ui.showsBar
import dev.pocketagent.app.ui.tabIndex
import org.junit.Assert.assertEquals
import org.junit.Test

class NavMotionTest {
    @Test fun pushPopTabNone() {
        assertEquals(NavMotionKind.PUSH, navMotionKind(listOf(2L), listOf(2L, 100L)))
        assertEquals(NavMotionKind.PUSH, navMotionKind(listOf(2L, 100L), listOf(2L, 100L, 101L)))
        assertEquals(NavMotionKind.POP, navMotionKind(listOf(2L, 100L), listOf(2L)))
        assertEquals(NavMotionKind.POP, navMotionKind(listOf(2L, 100L, 101L), listOf(2L, 100L)))
        assertEquals(NavMotionKind.TAB, navMotionKind(listOf(1L), listOf(3L)))
        assertEquals(NavMotionKind.NONE, navMotionKind(listOf(2L, 100L), listOf(2L, 100L)))
        assertEquals(NavMotionKind.NONE, navMotionKind(emptyList(), listOf(1L)))
        assertEquals(NavMotionKind.NONE, navMotionKind(listOf(1L), emptyList()))
    }

    @Test fun deepLinkIntoDetailIsPush() {
        // The stack was rebuilt: neither top is on the other stack, the new one is deeper.
        assertEquals(NavMotionKind.PUSH, navMotionKind(listOf(1L), listOf(2L, 100L)))
        assertEquals(NavMotionKind.PUSH, navMotionKind(listOf(1L), listOf(1L, 100L, 101L)))
    }

    @Test fun detailToRootByTabIsPop() {
        assertEquals(NavMotionKind.POP, navMotionKind(listOf(2L, 100L), listOf(3L)))
        assertEquals(NavMotionKind.POP, navMotionKind(listOf(1L, 100L, 101L), listOf(1L)))
    }

    @Test fun replaceOfDetailIsPush() {
        assertEquals(NavMotionKind.PUSH, navMotionKind(listOf(2L, 100L), listOf(2L, 101L)))
    }

    private class Expect(val route: Route, val bar: Boolean, val tab: Int, val rank: Int)

    @Test fun everyRouteHasBarAndTabIndexAndRank() {
        val table = listOf(
            Expect(Route.Home, true, 0, 0),
            Expect(Route.Chats, true, 1, 0),
            Expect(Route.Chat("a"), false, 1, 1),
            Expect(Route.Bots, true, 2, 0),
            Expect(Route.BotEditor(null), false, 2, 1),
            Expect(Route.BotEditor("b"), false, 2, 1),
            Expect(Route.Hermes, true, 3, 0),
            Expect(Route.Terminal, false, 3, 1),
            Expect(Route.Settings(null), true, 4, 0),
            Expect(Route.Settings("usage"), true, 4, 0),
            Expect(Route.Routines, false, 0, 1),
            Expect(Route.RoutineEditor(null), false, 0, 1),
            Expect(Route.RoutineEditor("r"), false, 0, 1),
            Expect(Route.Starred, false, 1, 1),
            Expect(Route.Gallery, false, 0, 1),
        )
        for (e in table) {
            assertEquals("showsBar ${e.route}", e.bar, e.route.showsBar())
            assertEquals("tabIndex ${e.route}", e.tab, e.route.tabIndex())
            assertEquals("rank ${e.route}", e.rank, e.route.rank())
        }
    }

    @Test fun slideSignFollowsLayoutDirection() {
        assertEquals(1, slideSign(rtl = false))
        assertEquals(-1, slideSign(rtl = true))
    }
}
