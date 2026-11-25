package dev.pocketagent.app.ui.nav

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import dev.pocketagent.app.ui.Route

/** Test tags the phone robot reads (they are exported as resource ids by [robotRoot]). One tag per node. */
object NavTags {
    const val BAR = "nav.bar"
    const val TAB_HOME = "tab.home"
    const val TAB_CHATS = "tab.chats"
    const val TAB_BOTS = "tab.bots"
    const val TAB_HERMES = "tab.hermes"
    const val TAB_SETTINGS = "tab.settings"
    const val SHARE_DIALOG = "share.dialog"
    const val SHARE_NEW = "share.new"
    const val SHARE_ITEM = "share.item"

    /** The tag of the host of a screen: one distinct name per kind of route (not per chat or bot). */
    fun screen(route: Route): String = when (route) {
        Route.Home -> "screen.home"
        Route.Chats -> "screen.chats"
        is Route.Chat -> "screen.chat"
        Route.Bots -> "screen.bots"
        is Route.BotEditor -> "screen.boteditor"
        Route.Hermes -> "screen.hermes"
        Route.Terminal -> "screen.terminal"
        is Route.Settings -> "screen.settings"
        Route.Routines -> "screen.routines"
        is Route.RoutineEditor -> "screen.routineeditor"
        Route.Starred -> "screen.starred"
        Route.Gallery -> "screen.gallery"
    }
}

/**
 * Makes every test tag below this node visible to uiautomator as a resource id. Applied once at the root of the shell,
 * and inside every window of its own (a dialog, a sheet), because the setting does not cross into a window.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.robotRoot(): Modifier = semantics { testTagsAsResourceId = true }
