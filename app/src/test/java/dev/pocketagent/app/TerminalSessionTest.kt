package dev.pocketagent.app

import dev.pocketagent.app.hermes.AnsiStrip
import dev.pocketagent.app.hermes.TermKind
import dev.pocketagent.app.hermes.TermLine
import dev.pocketagent.app.hermes.TerminalSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class TerminalSessionTest {
    @get:Rule val tmp = TemporaryFolder()

    // ---- ANSI stripping ----------------------------------------------------------------------------------------------------

    @Test fun stripsColoursCursorMovesAndTitles() {
        assertEquals("red and bold", AnsiStrip.strip("\u001B[31mred\u001B[0m and \u001B[1;4mbold\u001B[0m"))
        assertEquals("done", AnsiStrip.strip("\u001B[2K\u001B[1Gdone"))
        assertEquals("text", AnsiStrip.strip("\u001B]0;window title\u0007text"))
        assertEquals("text", AnsiStrip.strip("\u001B]8;;http://x\u001B\\text\u001B]8;;\u001B\\"))
        assertEquals("a b", AnsiStrip.strip("a\u001B(B b"))
        assertEquals("bracketed", AnsiStrip.strip("\u001B[?2004hbracketed\u001B[?2004l"))
    }

    @Test fun carriageReturnsActLikeATerminalAndControlCharsGo() {
        assertEquals("100%", AnsiStrip.strip("10%\r50%\r100%"))
        assertEquals("line", AnsiStrip.strip("line\r"))
        assertEquals("ab", AnsiStrip.strip("a\u0007b"))
        assertEquals("tab\there", AnsiStrip.strip("tab\there"))
        assertEquals("", AnsiStrip.strip(""))
        assertEquals("plain ünïcode ê", AnsiStrip.strip("plain ünïcode ê"))
    }

    // ---- session ---------------------------------------------------------------------------------------------------------------

    private fun session(
        installed: Boolean = true, history: File = File(tmp.root, "history.json"),
        exec: (List<String>, (String, Boolean) -> Unit, AtomicBoolean) -> Int,
    ) = TerminalSession(TerminalSession.GuestExec { c, o, k -> exec(c, o, k) }, { installed }, history, CoroutineScope(Dispatchers.IO))

    private fun waitFor(timeoutMs: Long = 5000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("condition not met within ${timeoutMs}ms")
            Thread.sleep(10)
        }
    }

    private fun done(s: TerminalSession) = waitFor { !s.running.value && s.lines.value.isNotEmpty() }

    @Test fun runsThroughBashCAndStreamsOutAndErrorLines() {
        val seen = CopyOnWriteArrayList<List<String>>()
        val s = session { cmd, out, _ ->
            seen += cmd
            out("\u001B[32mhello\u001B[0m", false); out("oops", true); out("progress 10%\rprogress 100%", false)
            0
        }
        s.run("  echo hello  ")
        done(s)
        assertEquals(listOf("bash", "-c", "echo hello"), seen.single())
        assertEquals(listOf(
            TermLine("$ echo hello", TermKind.CMD),
            TermLine("hello", TermKind.OUT),
            TermLine("oops", TermKind.ERR),
            TermLine("progress 100%", TermKind.OUT),
        ), s.lines.value)
        assertFalse(s.running.value)
    }

    @Test fun nonZeroExitIsReported() {
        val s = session { _, _, _ -> 3 }
        s.run("false")
        done(s)
        assertEquals(TermLine("[exit code 3]", TermKind.INFO), s.lines.value.last())
    }

    @Test fun refusesWhenTheRootfsIsNotInstalled() {
        var called = false
        val s = session(installed = false) { _, _, _ -> called = true; 0 }
        s.run("ls")
        done(s)
        assertFalse(called)
        assertEquals(TermKind.CMD, s.lines.value.first().kind)
        assertEquals(TermKind.INFO, s.lines.value.last().kind)
        assertTrue(s.lines.value.last().text.contains("not installed"))
    }

    @Test fun cancelReachesTheRunnerAndOnlyOneCommandRunsAtATime() {
        val started = java.util.concurrent.CountDownLatch(1)
        val runs = java.util.concurrent.atomic.AtomicInteger(0)
        val s = session { _, _, cancel ->
            runs.incrementAndGet()
            started.countDown()
            while (!cancel.get()) Thread.sleep(5)
            143
        }
        s.run("sleep 100")
        assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS))
        assertTrue(s.running.value)
        s.run("second")                       // refused while the first runs
        assertTrue(s.lines.value.any { it.kind == TermKind.INFO && it.text.contains("still running") })
        s.cancel()
        waitFor { !s.running.value }
        assertEquals(1, runs.get())
        assertEquals(TermLine("[cancelled]", TermKind.INFO), s.lines.value.last())
    }

    @Test fun linesAreCappedAtFiveThousand() {
        val s = session { _, out, _ -> repeat(6200) { out("line $it", false) }; 0 }
        s.run("spam")
        waitFor(10_000) { !s.running.value && s.lines.value.size >= 5000 }
        assertEquals(TerminalSession.MAX_LINES, s.lines.value.size)
        assertEquals("line 6199", s.lines.value.last().text)
        s.clear()
        assertTrue(s.lines.value.isEmpty())
    }

    @Test fun historyKeepsTheLastHundredWithoutDuplicatesAndSurvivesARestart() {
        val file = File(tmp.root, "terminal/history.json")
        val s = session(history = file) { _, _, _ -> 0 }
        for (i in 1..105) { s.run("cmd $i"); waitFor { !s.running.value } }
        s.run("cmd 50"); waitFor { !s.running.value }          // moves an old entry to the end instead of duplicating it
        assertEquals(100, s.history.size)
        assertEquals("cmd 50", s.history.last())
        assertEquals(1, s.history.count { it == "cmd 50" })
        assertFalse(s.history.contains("cmd 1"))
        waitFor { session(history = file) { _, _, _ -> 0 }.history == s.history }   // the file ends up holding the newest list
    }

    @Test fun aCorruptHistoryFileStartsEmpty() {
        val f = File(tmp.root, "history.json").also { it.writeText("not json at all") }
        val s = session(history = f) { _, _, _ -> 0 }
        assertTrue(s.history.isEmpty())
    }

    @Test fun emptyCommandsAreIgnored() {
        val s = session { _, _, _ -> 0 }
        s.run("   ")
        assertTrue(s.lines.value.isEmpty())
        assertTrue(s.history.isEmpty())
    }

    @Test fun aThrowingRunnerIsReportedNotCrashed() {
        val s = session { _, _, _ -> throw IllegalStateException("kaboom") }
        s.run("x")
        done(s)
        assertTrue(s.lines.value.last().text.contains("kaboom"))
        assertFalse(s.running.value)
    }

    @Test fun outputAndEchoedCommandAreRedacted() {
        dev.pocketagent.app.diag.Diag.redactor.addSecret("k", "TOPSECRETTERMVALUE")
        try {
            val s = session { _, out, _ -> out("GEMINI_API_KEY=TOPSECRETTERMVALUE", false); out("also TOPSECRETTERMVALUE", true); 0 }
            s.run("echo TOPSECRETTERMVALUE")
            done(s)
            assertTrue(s.lines.value.size >= 3)
            assertTrue(s.lines.value.none { it.text.contains("TOPSECRETTERMVALUE") })
        } finally { dev.pocketagent.app.diag.Diag.redactor.clear() }
    }
}
