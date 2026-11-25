package dev.pocketagent.app

import dev.pocketagent.app.diag.ExitKind
import dev.pocketagent.app.diag.ExitReason
import dev.pocketagent.app.diag.ExitReasons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExitReasonsTest {
    private val main = "dev.pocketagent.app"

    private fun exit(ts: Long, reason: Int, process: String = main, status: Int = 0, pid: Int = 100) =
        ExitReason(ts, pid, process, reason, status, 100, 1234, 5678, "desc")

    @Test fun namesAndKinds() {
        assertEquals("EXIT_SELF", ExitReasons.name(1))
        assertEquals("SIGNALED", ExitReasons.name(2))
        assertEquals("CRASH", ExitReasons.name(4))
        assertEquals("CRASH_NATIVE", ExitReasons.name(5))
        assertEquals("ANR", ExitReasons.name(6))
        assertEquals("USER_REQUESTED", ExitReasons.name(10))
        assertEquals("PACKAGE_UPDATED", ExitReasons.name(16))
        assertEquals("UNKNOWN", ExitReasons.name(99))
        assertEquals("UNKNOWN", ExitReasons.name(0))

        assertEquals(ExitKind.CRASH, ExitReasons.kind(4))
        assertEquals(ExitKind.NATIVE_CRASH, ExitReasons.kind(5))
        assertEquals(ExitKind.ANR, ExitReasons.kind(6))
        assertEquals(ExitKind.START_FAILED, ExitReasons.kind(7))
        assertEquals(ExitKind.LOW_MEMORY, ExitReasons.kind(3))
        for (r in listOf(2, 9, 14)) assertEquals(ExitKind.KILLED, ExitReasons.kind(r))
        for (r in listOf(10, 11)) assertEquals(ExitKind.USER, ExitReasons.kind(r))
        assertEquals(ExitKind.UPDATED, ExitReasons.kind(16))
        assertEquals(ExitKind.SELF, ExitReasons.kind(1))
        for (r in listOf(0, 8, 12, 13, 15, 17, 99)) assertEquals(ExitKind.OTHER, ExitReasons.kind(r))

        for (r in listOf(4, 5, 6, 7)) assertTrue(ExitReasons.isCrash(r))
        for (r in listOf(1, 2, 3, 8, 9, 10, 11, 12, 13, 14, 15, 16, 99)) assertFalse(ExitReasons.isCrash(r))
    }

    @Test fun decideBaselinesOnFirstRun() {
        val all = listOf(exit(500, 4), exit(900, 5), exit(700, 2))
        val d = ExitReasons.decide(all, main, null)
        assertTrue(d.offer.isEmpty())
        assertEquals(900L, d.newSeenUpTo)
        // No history at all: the baseline is 0, so any later crash is newer.
        val none = ExitReasons.decide(emptyList(), main, null)
        assertTrue(none.offer.isEmpty())
        assertEquals(0L, none.newSeenUpTo)
    }

    @Test fun decideOffersOnlyMainProcessCrashes() {
        val all = listOf(
            exit(100, 4, process = "$main:restart"),            // another process of this app
            exit(200, 4, process = "dev.pocketagent.app.debug"), // the twin's package never mixes in
            exit(300, 4),
            exit(400, 6),
            exit(350, 5),
            exit(500, 7),
        )
        val d = ExitReasons.decide(all, main, 0L)
        assertNull(d.newSeenUpTo)
        assertEquals(listOf(500L, 400L, 350L, 300L), d.offer.map { it.timestamp })
    }

    @Test fun decideRespectsSeenUpTo() {
        val all = listOf(exit(300, 4), exit(400, 4), exit(500, 5))
        assertEquals(listOf(500L), ExitReasons.decide(all, main, 400L).offer.map { it.timestamp })
        // Equal to the mark counts as seen.
        assertTrue(ExitReasons.decide(all, main, 500L).offer.isEmpty())
        assertEquals(3, ExitReasons.decide(all, main, 299L).offer.size)
    }

    @Test fun signaledAndUserRequestedAreNeverOffered() {
        val all = listOf(
            exit(100, 2), exit(110, 3), exit(120, 9), exit(130, 10), exit(140, 11),
            exit(150, 14), exit(160, 16), exit(170, 1), exit(180, 13),
        )
        assertTrue(ExitReasons.decide(all, main, 0L).offer.isEmpty())
    }

    @Test fun describeLineHasReasonAndStatus() {
        val line = ExitReasons.describe(exit(1_700_000_000_000L, 5, status = 11, pid = 4242)) { "T<$it>" }
        assertTrue(line, line.startsWith("T<1700000000000>"))
        assertTrue(line, line.contains("reason=CRASH_NATIVE(5)"))
        assertTrue(line, line.contains("status=11"))
        assertTrue(line, line.contains("pid=4242"))
        assertTrue(line, line.contains("process=$main"))
        assertTrue(line, line.contains("pss=1234KB"))
        assertTrue(line, line.contains("rss=5678KB"))
        assertTrue(line, line.contains("desc=desc"))
        assertFalse(line.contains("\n"))
    }
}
