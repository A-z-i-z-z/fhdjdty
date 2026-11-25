package dev.pocketagent.app

import dev.pocketagent.app.hermes.AnsiLine
import dev.pocketagent.app.hermes.AnsiParser
import dev.pocketagent.app.hermes.AnsiSpan
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
import dev.pocketagent.app.text.TestTexts
import org.junit.Before

class TerminalSessionTest {
    @Before fun installTexts() { TestTexts.installEnglish() }
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
            TermLine("hello", TermKind.OUT, listOf(AnsiSpan(0, 5, 2, false))),    // the green colour is kept as a span
            TermLine("oops", TermKind.ERR),
            TermLine("progress 100%", TermKind.OUT),
        ), s.lines.value)
        assertFalse(s.running.value)
    }

    // ---- ANSI colours ---------------------------------------------------------------------------------------------------

    @Test fun sgrColoursAndBoldBecomeSpansAndEverythingElseIsStripped() {
        val p = AnsiParser()
        val l = p.parse("plain \u001B[1;31mERR\u001B[0m ok \u001B[2K\u001B[92mgreen\u001B[39m!")
        assertEquals("plain ERR ok green!", l.text)
        assertEquals(
            listOf(AnsiSpan(6, 9, 1, true), AnsiSpan(13, 18, 10, false)),
            l.spans,
        )
        assertEquals(AnsiLine("", emptyList()), p.parse(""))
        assertEquals(AnsiLine("no colour", emptyList()), p.parse("no colour"))
    }

    @Test fun styleCarriesToTheNextLineUntilReset() {
        val p = AnsiParser()
        val a = p.parse("\u001B[34mblue starts")
        val b = p.parse("still blue\u001B[0m done")
        assertEquals(listOf(AnsiSpan(0, 11, 4, false)), a.spans)
        assertEquals(listOf(AnsiSpan(0, 10, 4, false)), b.spans)
        assertEquals("still blue done", b.text)
        p.reset()
        assertEquals(emptyList<AnsiSpan>(), p.parse("fresh").spans)
    }

    @Test fun extendedColoursMapToTheNearestOfSixteen() {
        val p = AnsiParser()
        assertEquals(listOf(AnsiSpan(0, 1, 9, false)), p.parse("\u001B[38;5;9mx").spans)          // index below 16: as is
        assertEquals(listOf(AnsiSpan(0, 1, 9, false)), p.parse("\u001B[38;2;255;10;10my").spans)  // true colour: nearest = bright red
        assertEquals(1, AnsiParser.from256(124))                                                   // dark red of the colour cube
        assertEquals(-1, AnsiParser.from256(300))
        assertEquals(0, AnsiParser.from256(232))                                                   // darkest grey
        assertEquals(7, AnsiParser.from256(255))                                                   // lightest grey: nearest is the light grey
        // A background colour and its arguments must not turn into foreground codes.
        assertEquals(emptyList<AnsiSpan>(), AnsiParser().parse("\u001B[48;5;196mtext\u001B[0m").spans)
        assertEquals("text", AnsiParser().parse("\u001B[48;2;1;2;3mtext").text)
    }

    @Test fun carriageReturnsDropTheColoursOfTheOverwrittenText() {
        val l = AnsiParser().parse("\u001B[31m10%\r\u001B[32m100%")
        assertEquals("100%", l.text)
        assertEquals(listOf(AnsiSpan(0, 4, 2, false)), l.spans)
    }

    @Test fun commandsAreWrappedForColoursOnlyWhenAsked() {
        assertEquals(listOf("bash", "-c", "ls"), TerminalSession.guestCommand("ls", false))
        val c = TerminalSession.guestCommand("ls --color=auto", true)
        assertEquals(listOf("env", "-u", "NO_COLOR"), c.take(3))
        assertEquals(listOf("bash", "-c", "ls --color=auto"), c.takeLast(3))
        assertTrue(c.contains("FORCE_COLOR=1"))
        assertTrue(c.contains("TERM=xterm-256color"))
    }

    @Test fun theColourSwitchChangesWhatReachesTheGuest() {
        val seen = CopyOnWriteArrayList<List<String>>()
        val s = TerminalSession(
            TerminalSession.GuestExec { c, _, _ -> seen += c; 0 }, { true }, File(tmp.root, "h2.json"), CoroutineScope(Dispatchers.IO), colorsDefault = true,
        )
        assertTrue(s.colors.value)
        s.run("one"); waitFor { !s.running.value && s.lines.value.isNotEmpty() }
        s.setColors(false)
        s.run("two"); waitFor { !s.running.value && seen.size == 2 }
        assertEquals("env", seen[0].first())
        assertEquals(listOf("bash", "-c", "two"), seen[1])
    }

    @Test fun aRedactedLineLosesItsSpansButKeepsItsText() {
        dev.pocketagent.app.diag.Diag.redactor.addSecret("k2", "SECRETCOLOURVALUE")
        try {
            val s = session { _, out, _ -> out("\u001B[31mtoken SECRETCOLOURVALUE here\u001B[0m", false); 0 }
            s.run("x")
            done(s)
            val l = s.lines.value.last { it.kind == TermKind.OUT }
            assertFalse(l.text.contains("SECRETCOLOURVALUE"))
            assertTrue(l.spans.isEmpty())
        } finally { dev.pocketagent.app.diag.Diag.redactor.clear() }
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
