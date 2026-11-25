package dev.pocketagent.app

import dev.pocketagent.app.runtime.PresenceSettings
import dev.pocketagent.app.runtime.RuntimeState
import dev.pocketagent.app.service.PresenceLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceLogicTest {
    private val stopped = RuntimeState.Stopped("ready")

    private fun autoStart(
        autoStart: Boolean = true, handled: Boolean = false, userStopped: Boolean = false,
        rootfs: Boolean = true, state: RuntimeState = stopped, setup: Boolean = true,
    ) = PresenceLogic.shouldAutoStart(autoStart, handled, userStopped, rootfs, state, setup)

    @Test fun autoStartsWhenEverythingIsInPlace() {
        assertTrue(autoStart())
    }

    @Test fun autoStartNeedsEveryCondition() {
        assertFalse(autoStart(autoStart = false))
        assertFalse(autoStart(handled = true))
        assertFalse(autoStart(userStopped = true))
        assertFalse(autoStart(rootfs = false))
        assertFalse(autoStart(setup = false))
    }

    @Test fun autoStartNeverTouchesErrorBusyOrFreshInstall() {
        assertFalse(autoStart(state = RuntimeState.Error("boom")))
        assertFalse(autoStart(state = RuntimeState.NotInstalled))
        assertFalse(autoStart(state = RuntimeState.Running))
        assertFalse(autoStart(state = RuntimeState.Starting("x")))
        assertFalse(autoStart(state = RuntimeState.Extracting(0.5f, "x")))
    }

    @Test fun notifiesUnlessTheChatIsOnScreen() {
        assertTrue(PresenceLogic.shouldNotify(true, appInForeground = false, activeConvId = null, convId = "a"))
        assertTrue(PresenceLogic.shouldNotify(true, appInForeground = false, activeConvId = "a", convId = "a"))
        assertTrue(PresenceLogic.shouldNotify(true, appInForeground = true, activeConvId = "b", convId = "a"))
        assertTrue(PresenceLogic.shouldNotify(true, appInForeground = true, activeConvId = null, convId = "a"))
        assertFalse(PresenceLogic.shouldNotify(true, appInForeground = true, activeConvId = "a", convId = "a"))
        assertFalse(PresenceLogic.shouldNotify(false, appInForeground = false, activeConvId = null, convId = "a"))
    }

    @Test fun restartBudgetAllowsTwoPerTenMinutes() {
        val b = PresenceLogic.RestartBudget()
        val t0 = 1_000_000L
        assertTrue(b.tryAcquire(t0))
        assertTrue(b.tryAcquire(t0 + 60_000))
        assertFalse(b.tryAcquire(t0 + 120_000))
        assertFalse(b.tryAcquire(t0 + 599_999))
        // The first restart leaves the window after 10 minutes.
        assertTrue(b.tryAcquire(t0 + 600_000))
        assertFalse(b.tryAcquire(t0 + 600_001))
    }

    @Test fun restartBudgetIgnoresTimesFromTheFuture() {
        val b = PresenceLogic.RestartBudget(max = 1, windowMs = 1000)
        assertTrue(b.tryAcquire(5000))
        // The clock jumped back: the old record must not block forever.
        assertTrue(b.tryAcquire(100))
    }

    @Test fun idleStopNeedsAQuietBackgroundedRunningHermes() {
        val ref = 0L
        val now = 31 * 60_000L
        fun stop(
            minutes: Int = 30, state: RuntimeState = RuntimeState.Running, fg: Boolean = false, busy: Boolean = false,
            term: Boolean = false, at: Long = now,
        ) = PresenceLogic.shouldIdleStop(minutes, state, fg, busy, term, ref, at)
        assertTrue(stop())
        assertFalse(stop(minutes = 0))
        assertFalse(stop(state = stopped))
        assertFalse(stop(fg = true))
        assertFalse(stop(busy = true))
        assertFalse(stop(term = true))
        assertFalse(stop(at = 29 * 60_000L))
        assertTrue(stop(at = 30 * 60_000L))
    }

    @Test fun idleClockStartsAtTheLatestEvent() {
        assertEquals(500L, PresenceLogic.idleReference(100L, 500L, 300L))
        assertEquals(900L, PresenceLogic.idleReference(900L, 500L, 0L))
        assertEquals(700L, PresenceLogic.idleReference(0L, 0L, 700L))
    }

    @Test fun previewStripsMarkdownAndIsCapped() {
        assertEquals("Hello world and a link", PresenceLogic.preview("**Hello** world and [a link](https://example.com)"))
        assertEquals("", PresenceLogic.preview("   "))
        val long = "word ".repeat(200)
        val p = PresenceLogic.preview(long)
        assertTrue(p.length <= PresenceLogic.PREVIEW_CHARS)
        assertTrue(p.endsWith("…"))
        val exact = "a".repeat(PresenceLogic.PREVIEW_CHARS)
        assertEquals(exact, PresenceLogic.preview(exact))
    }

    @Test fun titleFollowsTheBotAndGroupRules() {
        assertEquals("Rebel", PresenceLogic.titleFor("Rebel", "Plan my trip", false, "Untitled"))
        assertEquals("Rebel · Plan my trip", PresenceLogic.titleFor("Rebel", "Plan my trip", true, "Untitled"))
        assertEquals("Rebel", PresenceLogic.titleFor("Rebel", "", true, "Untitled"))
        assertEquals("Plan my trip", PresenceLogic.titleFor(null, "Plan my trip", false, "Untitled"))
        assertEquals("Untitled", PresenceLogic.titleFor(null, "  ", false, "Untitled"))
        assertEquals("Untitled", PresenceLogic.titleFor("  ", "", true, "Untitled"))
    }

    @Test fun replyTextIsValidatedAndCapped() {
        assertNull(PresenceLogic.cleanReply(null))
        assertNull(PresenceLogic.cleanReply("   \n "))
        assertEquals("hello", PresenceLogic.cleanReply("  hello \n"))
        val long = "x".repeat(PresenceLogic.MAX_REPLY_CHARS + 500)
        assertEquals(PresenceLogic.MAX_REPLY_CHARS, PresenceLogic.cleanReply(long)!!.length)
        // A cut in the middle of a surrogate pair drops the half character.
        val emoji = "x".repeat(PresenceLogic.MAX_REPLY_CHARS - 1) + "🔥" + "tail"
        val cut = PresenceLogic.cleanReply(emoji)!!
        assertEquals(PresenceLogic.MAX_REPLY_CHARS - 1, cut.length)
        assertFalse(cut.last().isHighSurrogate())
    }

    @Test fun conversationIdsAreChecked() {
        assertTrue(PresenceLogic.validConvId("0123456789abcdef"))
        assertFalse(PresenceLogic.validConvId(null))
        assertFalse(PresenceLogic.validConvId(""))
        assertFalse(PresenceLogic.validConvId("  "))
        assertFalse(PresenceLogic.validConvId("a".repeat(PresenceLogic.MAX_CONV_ID_CHARS + 1)))
    }

    @Test fun approvalChoicesMustBeOffered() {
        val offered = listOf("once", "session", "always", "deny")
        assertTrue(PresenceLogic.validApprovalChoice("once", offered))
        assertTrue(PresenceLogic.validApprovalChoice("deny", offered))
        assertFalse(PresenceLogic.validApprovalChoice("sudo", offered))
        assertFalse(PresenceLogic.validApprovalChoice("", offered))
        assertFalse(PresenceLogic.validApprovalChoice(null, offered))
        assertFalse(PresenceLogic.validApprovalChoice("always", listOf("once", "deny")))
        // Hermes listed nothing: the approval card's fallback applies.
        assertTrue(PresenceLogic.validApprovalChoice("once", emptyList()))
        assertFalse(PresenceLogic.validApprovalChoice("always", emptyList()))
    }

    @Test fun approvalButtonsFollowTheOfferedChoices() {
        assertEquals("once" to "deny", PresenceLogic.approvalButtons(listOf("once", "session", "always", "deny")))
        assertEquals("once" to "deny", PresenceLogic.approvalButtons(emptyList()))
        assertEquals("session" to "deny", PresenceLogic.approvalButtons(listOf("session", "deny")))
        assertEquals("once" to null, PresenceLogic.approvalButtons(listOf("once")))
        assertEquals(null to "deny", PresenceLogic.approvalButtons(listOf("deny")))
    }

    @Test fun approvalTapMustMatchTheRequestTheNotificationShowed() {
        assertTrue(PresenceLogic.approvalMatches("run1", "req1", "run1", "req1"))
        assertFalse(PresenceLogic.approvalMatches("run1", "req2", "run1", "req1"))
        assertFalse(PresenceLogic.approvalMatches("run2", "req1", "run1", "req1"))
        // An old notification without the ids never answers anything.
        assertFalse(PresenceLogic.approvalMatches("run1", "req1", null, null))
        assertFalse(PresenceLogic.approvalMatches("run1", "req1", "run1", null))
        assertFalse(PresenceLogic.approvalMatches("", "", null, null))
    }

    @Test fun restartOnlyFromTheErrorTheExitLeft() {
        val err = RuntimeState.Error("exit 1")
        assertTrue(PresenceLogic.shouldRestartAfterExit(true, err, 0, 0))
        assertFalse(PresenceLogic.shouldRestartAfterExit(false, err, 0, 0))
        // The user pressed Stop during the delay: the state is Stopped and the stop counter moved.
        assertFalse(PresenceLogic.shouldRestartAfterExit(true, stopped, 0, 1))
        assertFalse(PresenceLogic.shouldRestartAfterExit(true, stopped, 0, 0))
        assertFalse(PresenceLogic.shouldRestartAfterExit(true, err, 0, 1))
        // Somebody already started it again.
        assertFalse(PresenceLogic.shouldRestartAfterExit(true, RuntimeState.Starting("x"), 0, 0))
        assertFalse(PresenceLogic.shouldRestartAfterExit(true, RuntimeState.Running, 0, 0))
    }

    @Test fun idleLimitIsRepaired() {
        assertEquals(0, PresenceSettings(idleStopMinutes = 0).sanitized().idleStopMinutes)
        assertEquals(0, PresenceSettings(idleStopMinutes = -4).sanitized().idleStopMinutes)
        assertEquals(5, PresenceSettings(idleStopMinutes = 1).sanitized().idleStopMinutes)
        assertEquals(60, PresenceSettings(idleStopMinutes = 60).sanitized().idleStopMinutes)
        assertEquals(240, PresenceSettings(idleStopMinutes = 9999).sanitized().idleStopMinutes)
    }

    @Test fun defaultsMatchTheContract() {
        val d = PresenceSettings()
        assertTrue(d.autoStart && d.wakeOnSend && d.notifyReplies && d.notifyApprovals && d.autoRestart)
        assertEquals(0, d.idleStopMinutes)
    }
}
