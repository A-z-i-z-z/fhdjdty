package dev.pocketagent.app.ui.nav

import dev.pocketagent.app.ui.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavTagsTest {
    @Test fun screenTagsAreDistinct() {
        // One route of each of the 12 kinds (kotlin-reflect is not on the classpath: listed by hand).
        val kinds = listOf(
            Route.Home, Route.Chats, Route.Chat("a"), Route.Bots, Route.BotEditor("b"), Route.Hermes,
            Route.Terminal, Route.Settings("usage"), Route.Routines, Route.RoutineEditor("r"), Route.Starred, Route.Gallery,
        )
        val tags = kinds.map { NavTags.screen(it) }
        assertEquals(12, tags.toSet().size)
        assertTrue(tags.all { it.startsWith("screen.") && it.length > "screen.".length })
        // The tag names a kind of screen, not one chat or bot.
        assertEquals(NavTags.screen(Route.Chat("a")), NavTags.screen(Route.Chat("z")))
        assertEquals(NavTags.screen(Route.BotEditor(null)), NavTags.screen(Route.BotEditor("z")))
        assertEquals(NavTags.screen(Route.Settings(null)), NavTags.screen(Route.Settings("usage")))
        assertEquals(NavTags.screen(Route.RoutineEditor(null)), NavTags.screen(Route.RoutineEditor("z")))
    }

    @Test fun theNamedTagsAreTheDocumentedOnes() {
        assertEquals("nav.bar", NavTags.BAR)
        assertEquals("tab.home", NavTags.TAB_HOME)
        assertEquals("tab.chats", NavTags.TAB_CHATS)
        assertEquals("tab.bots", NavTags.TAB_BOTS)
        assertEquals("tab.hermes", NavTags.TAB_HERMES)
        assertEquals("tab.settings", NavTags.TAB_SETTINGS)
        assertEquals("share.dialog", NavTags.SHARE_DIALOG)
        assertEquals("share.new", NavTags.SHARE_NEW)
        assertEquals("share.item", NavTags.SHARE_ITEM)
        assertEquals("screen.home", NavTags.screen(Route.Home))
        assertEquals("screen.boteditor", NavTags.screen(Route.BotEditor(null)))
        assertEquals("screen.routineeditor", NavTags.screen(Route.RoutineEditor(null)))
        assertEquals("screen.gallery", NavTags.screen(Route.Gallery))
    }
}
