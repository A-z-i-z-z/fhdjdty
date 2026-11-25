package dev.pocketagent.app

import android.content.Intent
import dev.pocketagent.app.ui.Route
import dev.pocketagent.app.ui.decodeRoute
import dev.pocketagent.app.ui.encode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLinksTest {
    private val fromHistory = Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY

    // ---- AppLinks.parseParts ---------------------------------------------------------------------------------

    @Test fun openChatNeedsAConversationId() {
        assertEquals(AppLink.OpenChat("abc123"), AppLinks.parseParts(AppLinks.ACTION_OPEN_CHAT, "abc123", 0))
        assertEquals(AppLink.OpenChat("abc123"), AppLinks.parseParts(AppLinks.ACTION_OPEN_CHAT, "  abc123 ", 0))
        assertNull(AppLinks.parseParts(AppLinks.ACTION_OPEN_CHAT, null, 0))
        assertNull(AppLinks.parseParts(AppLinks.ACTION_OPEN_CHAT, "", 0))
        assertNull(AppLinks.parseParts(AppLinks.ACTION_OPEN_CHAT, "   ", 0))
    }

    @Test fun simpleActionsMapToLinks() {
        assertEquals(AppLink.NewChat, AppLinks.parseParts(AppLinks.ACTION_NEW_CHAT, null, 0))
        assertEquals(AppLink.Terminal, AppLinks.parseParts(AppLinks.ACTION_TERMINAL, null, 0))
        assertEquals(AppLink.Routines, AppLinks.parseParts(AppLinks.ACTION_ROUTINES, null, 0))
        assertEquals(AppLink.Usage, AppLinks.parseParts(AppLinks.ACTION_USAGE, null, 0))
        // A stray conversation id does not change the other actions.
        assertEquals(AppLink.NewChat, AppLinks.parseParts(AppLinks.ACTION_NEW_CHAT, "abc", 0))
    }

    @Test fun otherActionsAreNotLinks() {
        assertNull(AppLinks.parseParts(null, "abc", 0))
        assertNull(AppLinks.parseParts(Intent.ACTION_MAIN, "abc", 0))
        assertNull(AppLinks.parseParts(Intent.ACTION_SEND, "abc", 0))
        assertNull(AppLinks.parseParts("dev.pocketagent.app.SOMETHING_ELSE", "abc", 0))
    }

    @Test fun anIntentReplayedFromRecentsIsIgnored() {
        assertNull(AppLinks.parseParts(AppLinks.ACTION_OPEN_CHAT, "abc", fromHistory))
        assertNull(AppLinks.parseParts(AppLinks.ACTION_NEW_CHAT, null, fromHistory))
        assertNull(AppLinks.parseParts(AppLinks.ACTION_TERMINAL, null, fromHistory or Intent.FLAG_ACTIVITY_NEW_TASK))
        // Other flags alone do not matter.
        assertEquals(AppLink.Routines, AppLinks.parseParts(AppLinks.ACTION_ROUTINES, null, Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    @Test fun parseOfNullIsNull() {
        assertNull(AppLinks.parse(null))
    }

    @Test fun actionNamesAreTheDocumentedOnes() {
        assertEquals("dev.pocketagent.app.OPEN_CHAT", AppLinks.ACTION_OPEN_CHAT)
        assertEquals("dev.pocketagent.app.NEW_CHAT", AppLinks.ACTION_NEW_CHAT)
        assertEquals("dev.pocketagent.app.TERMINAL", AppLinks.ACTION_TERMINAL)
        assertEquals("dev.pocketagent.app.ROUTINES", AppLinks.ACTION_ROUTINES)
        assertEquals("conv_id", AppLinks.EXTRA_CONV_ID)
        assertEquals("dev.pocketagent.app.DEBUG", AppLinks.ACTION_DEBUG)
    }

    @Test fun theDebugActionIsNotALinkOfTheNormalParser() {
        // Only DebugLinks reads it (in a build with test hooks); the normal parser never makes a link of it.
        assertNull(AppLinks.parseParts(AppLinks.ACTION_DEBUG, "abc", 0))
        assertEquals(Route.Gallery, AppLink.DebugOpen(Route.Gallery).route)
    }

    // ---- AppLinks.processText --------------------------------------------------------------------------------

    @Test fun selectedTextIsTrimmed() {
        assertEquals("hello world", AppLinks.processText(Intent.ACTION_PROCESS_TEXT, "  hello world \n", 0))
    }

    @Test fun selectedTextRejectsEmptyWrongActionAndRecents() {
        assertNull(AppLinks.processText(Intent.ACTION_PROCESS_TEXT, null, 0))
        assertNull(AppLinks.processText(Intent.ACTION_PROCESS_TEXT, "   ", 0))
        assertNull(AppLinks.processText(Intent.ACTION_SEND, "hello", 0))
        assertNull(AppLinks.processText(null, "hello", 0))
        assertNull(AppLinks.processText(Intent.ACTION_PROCESS_TEXT, "hello", fromHistory))
    }

    @Test fun selectedTextIsCapped() {
        val long = "x".repeat(AppLinks.MAX_PROCESS_TEXT + 500)
        assertEquals(AppLinks.MAX_PROCESS_TEXT, AppLinks.processText(Intent.ACTION_PROCESS_TEXT, long, 0)!!.length)
    }

    @Test fun requestCodesDifferPerChat() {
        assertEquals(AppLinks.requestCodeFor("abc"), AppLinks.requestCodeFor("abc"))
        assertNotEquals(AppLinks.requestCodeFor("abc"), AppLinks.requestCodeFor("abd"))
    }

    // ---- Route encode / decode --------------------------------------------------------------------------------

    private fun roundTrip(r: Route) = assertEquals(r, decodeRoute(r.encode()))

    @Test fun newRoutesRoundTrip() {
        roundTrip(Route.Routines)
        roundTrip(Route.RoutineEditor(null))
        roundTrip(Route.RoutineEditor("r1a2b3c4"))
        roundTrip(Route.Starred)
        roundTrip(Route.Gallery)
        assertEquals("gallery", Route.Gallery.encode())
    }

    @Test fun newRouteEncodings() {
        assertEquals("routines", Route.Routines.encode())
        assertEquals("routinenew", Route.RoutineEditor(null).encode())
        assertEquals("routine:r1", Route.RoutineEditor("r1").encode())
        assertEquals("starred", Route.Starred.encode())
    }

    @Test fun routinesAndRoutineEditorDoNotCollide() {
        assertEquals(Route.Routines, decodeRoute("routines"))
        assertEquals(Route.RoutineEditor(null), decodeRoute("routinenew"))
        assertEquals(Route.RoutineEditor("x"), decodeRoute("routine:x"))
    }

    @Test fun existingRoutesStillRoundTrip() {
        roundTrip(Route.Home)
        roundTrip(Route.Chats)
        roundTrip(Route.Chat("c1"))
        roundTrip(Route.Bots)
        roundTrip(Route.BotEditor(null))
        roundTrip(Route.BotEditor("b1"))
        roundTrip(Route.Hermes)
        roundTrip(Route.Terminal)
        roundTrip(Route.Settings(null))
        roundTrip(Route.Settings("usage"))
    }

    @Test fun unknownRouteDecodesToNull() {
        assertNull(decodeRoute("nope"))
        assertNull(decodeRoute(""))
    }
}
