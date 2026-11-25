package dev.pocketagent.app.hermes

import dev.pocketagent.app.data.Fs
import dev.pocketagent.app.diag.Diag
import dev.pocketagent.app.runtime.RuntimeManager
import dev.pocketagent.app.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import dev.pocketagent.app.R
import dev.pocketagent.app.ui.text.Texts

enum class TermKind { CMD, OUT, ERR, INFO }

/**
 * One line of the console. [spans] are the coloured / bold runs of [text] (empty for plain text and for every line the app
 * writes itself); offsets are character offsets into [text].
 */
data class TermLine(val text: String, val kind: TermKind, val spans: List<AnsiSpan> = emptyList())

/** A coloured or bold run of a terminal line: [fg] is 0..15 (ANSI colours, 8..15 = bright) or -1 for the default colour. */
data class AnsiSpan(val start: Int, val end: Int, val fg: Int, val bold: Boolean)

/** A line with its escape sequences removed and the colours they asked for kept as [spans]. */
data class AnsiLine(val text: String, val spans: List<AnsiSpan>)

/**
 * Turns terminal output into [AnsiLine]s. Understands the SGR colour codes (16 colours, bright colours, bold; 256-colour and
 * true-colour requests are mapped to the nearest of the 16); every other escape sequence (cursor movement, erase, window
 * titles, character sets) is removed, and so are stray control characters. A carriage return acts like a terminal's: "50%\r100%"
 * shows "100%". An instance keeps the colour state between calls, so a colour that starts on one line carries to the next;
 * use one instance per output stream, from one thread.
 */
class AnsiParser {
    private var fg = -1
    private var bold = false

    fun reset() { fg = -1; bold = false }

    fun parse(s: String): AnsiLine {
        if (s.isEmpty()) return AnsiLine("", emptyList())
        val sb = StringBuilder(s.length)
        val spans = ArrayList<AnsiSpan>()
        var runStart = -1
        var runFg = -1
        var runBold = false
        var pendingCr = false

        fun closeRun() {
            if (runStart >= 0 && sb.length > runStart) spans += AnsiSpan(runStart, sb.length, runFg, runBold)
            runStart = -1
        }

        fun literal(fromIdx: Int, toIdx: Int) {
            for (i in fromIdx until toIdx) {
                val ch = s[i]
                if (ch == '\r') { pendingCr = true; continue }
                if (pendingCr) { sb.setLength(0); spans.clear(); runStart = -1; pendingCr = false }
                val code = ch.code
                if (code <= 8 || code == 0x0B || code == 0x0C || code in 0x0E..0x1F || code == 0x7F) continue
                if (fg != -1 || bold) {
                    if (runStart >= 0 && (runFg != fg || runBold != bold)) closeRun()
                    if (runStart < 0) { runStart = sb.length; runFg = fg; runBold = bold }
                } else if (runStart >= 0) closeRun()
                sb.append(ch)
            }
        }

        var pos = 0
        if (s.indexOf('\u001B') >= 0) {
            for (m in TOKEN.findAll(s)) {
                literal(pos, m.range.first)
                val finalByte = m.groups[3]?.value
                if (finalByte == "m" && m.groupValues[2].isEmpty()) applySgr(m.groupValues[1])
                pos = m.range.last + 1
            }
        }
        literal(pos, s.length)
        closeRun()
        return AnsiLine(sb.toString(), spans)
    }

    private fun applySgr(params: String) {
        if (!params.all { it.isDigit() || it == ';' || it == ':' }) return
        val parts = if (params.isEmpty()) listOf("0") else params.split(';', ':')
        var i = 0
        while (i < parts.size) {
            val n = parts[i].toIntOrNull() ?: 0
            when {
                n == 0 -> { fg = -1; bold = false }
                n == 1 -> bold = true
                n == 22 -> bold = false
                n in 30..37 -> fg = n - 30
                n == 39 -> fg = -1
                n in 90..97 -> fg = n - 90 + 8
                n == 38 || n == 48 -> {
                    // 38;5;N and 38;2;R;G;B set the foreground (48 = background: read and ignored so its arguments are not mistaken for codes).
                    val mode = parts.getOrNull(i + 1)?.toIntOrNull()
                    if (mode == 5) {
                        val idx = parts.getOrNull(i + 2)?.toIntOrNull()
                        if (n == 38 && idx != null) fg = from256(idx)
                        i += 2
                    } else if (mode == 2) {
                        val r = parts.getOrNull(i + 2)?.toIntOrNull()
                        val g = parts.getOrNull(i + 3)?.toIntOrNull()
                        val b = parts.getOrNull(i + 4)?.toIntOrNull()
                        if (n == 38 && r != null && g != null && b != null) fg = nearest16(r, g, b)
                        i += 4
                    }
                }
            }
            i++
        }
    }

    companion object {
        // Regex escapes (\\u001B) rather than raw control characters inside the patterns. Alternatives: an OSC string (window title,
        // hyperlink), a CSI sequence (group 1 = parameters, 2 = intermediates, 3 = final byte), a character-set choice, any other
        // two-character escape.
        private val TOKEN = Regex(
            "\\u001B\\][^\\u0007\\u001B]*(?:\\u0007|\\u001B\\\\)" +
                "|\\u001B\\[([0-?]*)([ -/]*)([@-~])" +
                "|\\u001B[()*+][A-Za-z0-9]" +
                "|\\u001B[@-Z\\\\-_=>]",
        )

        private val XTERM16 = arrayOf(
            intArrayOf(0, 0, 0), intArrayOf(205, 0, 0), intArrayOf(0, 205, 0), intArrayOf(205, 205, 0),
            intArrayOf(0, 0, 238), intArrayOf(205, 0, 205), intArrayOf(0, 205, 205), intArrayOf(229, 229, 229),
            intArrayOf(127, 127, 127), intArrayOf(255, 0, 0), intArrayOf(0, 255, 0), intArrayOf(255, 255, 0),
            intArrayOf(92, 92, 255), intArrayOf(255, 0, 255), intArrayOf(0, 255, 255), intArrayOf(255, 255, 255),
        )
        private val CUBE = intArrayOf(0, 95, 135, 175, 215, 255)

        /** The ANSI colour (0..15) closest to an RGB value. */
        fun nearest16(r: Int, g: Int, b: Int): Int {
            val rr = r.coerceIn(0, 255); val gg = g.coerceIn(0, 255); val bb = b.coerceIn(0, 255)
            var best = 0
            var bestD = Int.MAX_VALUE
            for (k in XTERM16.indices) {
                val c = XTERM16[k]
                val d = (c[0] - rr) * (c[0] - rr) + (c[1] - gg) * (c[1] - gg) + (c[2] - bb) * (c[2] - bb)
                if (d < bestD) { bestD = d; best = k }
            }
            return best
        }

        /** The ANSI colour (0..15) for an xterm 256-colour index; -1 when the index is out of range. */
        fun from256(n: Int): Int = when {
            n in 0..15 -> n
            n in 16..231 -> { val v = n - 16; nearest16(CUBE[v / 36], CUBE[(v / 6) % 6], CUBE[v % 6]) }
            n in 232..255 -> { val g = 8 + (n - 232) * 10; nearest16(g, g, g) }
            else -> -1
        }
    }
}

/** Removes terminal escape sequences (colours, cursor movement, window titles) and stray control characters. */
object AnsiStrip {
    /** Carriage returns act like a terminal: "50%\r100%" shows "100%". Other control characters except tab are dropped. */
    fun strip(s: String): String = AnsiParser().parse(s).text
}

/**
 * A command line inside the Hermes rootfs, one command at a time. Each command runs as `bash -c <command>` through the same
 * launcher the gateway uses ([RuntimeManager.execInGuest]) with HOME=/root, HERMES_HOME as the gateway has it, TERM=dumb,
 * PAGER=cat and NO_COLOR=1; stdin is closed, so anything that waits for input gets end-of-file. The shell is not a login
 * shell on purpose: a login shell resets PATH and `hermes` would no longer be found.
 *
 * With [colors] on (the app's default) the command is started through `env -u NO_COLOR TERM=xterm-256color FORCE_COLOR=1 ...`
 * so programs that honour those variables print colours; the console shows the SGR colours and bold ([TermLine.spans]) and
 * removes every other escape sequence. With it off the output is plain text as before.
 *
 * Every output line (and the echoed command) goes through the shared secret redactor. Output lines are capped at [MAX_LINES]; the last [MAX_HISTORY] commands are kept in [historyFile]. Starting or stopping the
 * Hermes gateway also ends a running command (the launcher's stale-process cleanup cannot tell them apart).
 */
class TerminalSession internal constructor(
    private val exec: GuestExec,
    private val isInstalled: () -> Boolean,
    private val historyFile: File,
    private val scope: CoroutineScope,
    colorsDefault: Boolean = false,
) {
    constructor(runtime: RuntimeManager, historyFile: File) : this(
        GuestExec { cmd, out, cancel -> runtime.execInGuest(cmd, out, cancel) },
        { runtime.isRootfsValid() },
        historyFile,
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
        true,
    )

    /** How a command reaches the guest; [RuntimeManager.execInGuest] in the app, a fake in tests. */
    internal fun interface GuestExec {
        fun run(command: List<String>, onOutput: (String, Boolean) -> Unit, cancel: AtomicBoolean): Int
    }

    private val _lines = MutableStateFlow<List<TermLine>>(emptyList())
    private val _running = MutableStateFlow(false)
    private val _history = MutableStateFlow<List<String>>(loadHistory())
    private val _colors = MutableStateFlow(colorsDefault)

    /** Whether commands are started so that programs print colours (see the class comment). */
    val colors: StateFlow<Boolean> = _colors
    fun setColors(on: Boolean) { _colors.value = on }

    /** Everything shown so far, oldest first (at most 5000 lines). */
    val lines: StateFlow<List<TermLine>> = _lines
    val running: StateFlow<Boolean> = _running
    /** Same list as [history], as a flow. */
    val historyFlow: StateFlow<List<String>> = _history
    /** The last 100 commands, oldest first. */
    val history: List<String> get() = _history.value

    private val lock = Any()
    private val historyWriteLock = Any()
    private val pendingLines = ArrayList<TermLine>()
    private var flushJob: Job? = null
    private var cancelFlag = AtomicBoolean(false)
    @Volatile private var job: Job? = null

    /** Runs [command]; ignored when empty or when a command is already running. */
    fun run(command: String) {
        val cmd = command.trim('\n', '\r', ' ', '\t')
        if (cmd.isEmpty()) return
        synchronized(lock) {
            if (_running.value) { appendNow(listOf(TermLine(Texts.text(R.string.eng_term_busy), TermKind.INFO))); return }
            _running.value = true
        }
        remember(cmd)
        appendNow(cmd.lines().mapIndexed { i, l -> TermLine(Diag.redactor.redact((if (i == 0) "$ " else "> ") + l), TermKind.CMD) })
        val cancel = AtomicBoolean(false)
        cancelFlag = cancel
        job = scope.launch {
            try {
                if (!isInstalled()) {
                    append(TermLine(Texts.text(R.string.eng_term_not_installed), TermKind.INFO))
                    return@launch
                }
                // One parser per stream (each is used by a single reader thread) so a colour started on one line carries to the next.
                val outParser = AnsiParser()
                val errParser = AnsiParser()
                val code = exec.run(guestCommand(cmd, _colors.value), { text, isErr -> append(styledLine(if (isErr) errParser else outParser, text, isErr)) }, cancel)
                when {
                    cancel.get() -> append(TermLine(Texts.text(R.string.eng_term_cancelled), TermKind.INFO))
                    code != 0 -> append(TermLine(Texts.text(R.string.eng_term_exit_code, code), TermKind.INFO))
                }
            } catch (e: Throwable) {
                AppLog.e("Terminal", "command failed to run", e)
                append(TermLine(Diag.redactor.redact(Texts.text(R.string.eng_term_run_failed, "${e.javaClass.simpleName}: ${e.message}")), TermKind.INFO))
            } finally {
                flushNow()
                _running.value = false
            }
        }
    }

    /** Kills the running command and everything it started. */
    fun cancel() { cancelFlag.set(true) }

    fun clear() {
        synchronized(lock) { pendingLines.clear() }
        _lines.value = emptyList()
    }

    // ---- output buffering: many lines per second must not mean many list copies --------------------------------

    /** Parses one output line, redacts it, then caps it. A line the redactor changed loses its colours (offsets would no longer fit). */
    private fun styledLine(parser: AnsiParser, raw: String, isErr: Boolean): TermLine {
        val parsed = parser.parse(raw)
        val redacted = Diag.redactor.redact(parsed.text)
        val spans = if (redacted == parsed.text) parsed.spans else emptyList()
        val kind = if (isErr) TermKind.ERR else TermKind.OUT
        if (redacted.length <= MAX_LINE_CHARS) return TermLine(redacted, kind, spans)
        return TermLine(
            redacted.take(MAX_LINE_CHARS) + "…", kind,
            spans.mapNotNull { if (it.start >= MAX_LINE_CHARS) null else it.copy(end = minOf(it.end, MAX_LINE_CHARS)) },
        )
    }

    private fun append(line: TermLine) {
        synchronized(lock) {
            pendingLines += line
            if (flushJob == null) flushJob = scope.launch { delay(40); flushNow() }
        }
    }

    private fun appendNow(list: List<TermLine>) {
        synchronized(lock) { pendingLines += list }
        flushNow()
    }

    private fun flushNow() {
        synchronized(lock) {
            flushJob = null
            if (pendingLines.isEmpty()) return
            val merged = _lines.value + pendingLines
            pendingLines.clear()
            _lines.value = if (merged.size > MAX_LINES) merged.subList(merged.size - MAX_LINES, merged.size).toList() else merged
        }
    }

    // ---- history -------------------------------------------------------------------------------------------------

    private fun loadHistory(): List<String> = try {
        val text = Fs.readTextOrNull(historyFile)
        if (text == null) emptyList() else {
            val a = JSONArray(text)
            (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }.takeLast(MAX_HISTORY)
        }
    } catch (e: Exception) {
        AppLog.w("Terminal", "history file unreadable, starting empty", e)
        emptyList()
    }

    private fun remember(cmd: String) {
        val cur = _history.value
        if (cur.lastOrNull() == cmd) return
        val next = (cur.filter { it != cmd } + cmd).takeLast(MAX_HISTORY)
        _history.value = next
        // Always writes the newest list (not the one captured here), one writer at a time, so a slow write can never overwrite a newer one.
        scope.launch {
            synchronized(historyWriteLock) {
                try { Fs.writeAtomic(historyFile, JSONArray(_history.value).toString()) } catch (e: Exception) { AppLog.w("Terminal", "could not save history", e) }
            }
        }
    }

    companion object {
        const val MAX_LINES = 5000
        const val MAX_HISTORY = 100
        private const val MAX_LINE_CHARS = 4000

        /** The argument list that starts [cmd] in the guest: plain `bash -c`, or wrapped in `env` so programs print colours. */
        internal fun guestCommand(cmd: String, colors: Boolean): List<String> =
            if (!colors) listOf("bash", "-c", cmd)
            else listOf("env", "-u", "NO_COLOR", "TERM=xterm-256color", "COLORTERM=truecolor", "FORCE_COLOR=1", "CLICOLOR_FORCE=1", "bash", "-c", cmd)
    }
}
