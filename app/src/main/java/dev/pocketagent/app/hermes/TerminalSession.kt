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

enum class TermKind { CMD, OUT, ERR, INFO }
data class TermLine(val text: String, val kind: TermKind)

/** Removes terminal escape sequences (colours, cursor movement, window titles) and stray control characters. */
object AnsiStrip {
    // Regex escapes (\\u001B) rather than raw control characters inside the patterns.
    private val CSI = Regex("\\u001B\\[[0-?]*[ -/]*[@-~]")
    private val OSC = Regex("\\u001B\\][^\\u0007\\u001B]*(?:\\u0007|\\u001B\\\\)")
    private val OTHER_ESC = Regex("\\u001B[()*+][A-Za-z0-9]|\\u001B[@-Z\\\\-_=>]")
    private val CONTROL = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]")

    /** Carriage returns act like a terminal: "50%\r100%" shows "100%". Other control characters except tab are dropped. */
    fun strip(s: String): String {
        if (s.isEmpty()) return s
        var t = s
        if (t.indexOf('\u001B') >= 0) t = OSC.replace(CSI.replace(t, ""), "").let { OTHER_ESC.replace(it, "") }
        if (t.indexOf('\r') >= 0) t = t.trimEnd('\r').substringAfterLast('\r')
        return CONTROL.replace(t, "")
    }
}

/**
 * A command line inside the Hermes rootfs, one command at a time. Each command runs as `bash -c <command>` through the same
 * launcher the gateway uses ([RuntimeManager.execInGuest]) with HOME=/root, HERMES_HOME as the gateway has it, TERM=dumb,
 * PAGER=cat and NO_COLOR=1; stdin is closed, so anything that waits for input gets end-of-file. The shell is not a login
 * shell on purpose: a login shell resets PATH and `hermes` would no longer be found.
 *
 * Every output line (and the echoed command) goes through the shared secret redactor. Output lines are capped at [MAX_LINES]; the last [MAX_HISTORY] commands are kept in [historyFile]. Starting or stopping the
 * Hermes gateway also ends a running command (the launcher's stale-process cleanup cannot tell them apart).
 */
class TerminalSession internal constructor(
    private val exec: GuestExec,
    private val isInstalled: () -> Boolean,
    private val historyFile: File,
    private val scope: CoroutineScope,
) {
    constructor(runtime: RuntimeManager, historyFile: File) : this(
        GuestExec { cmd, out, cancel -> runtime.execInGuest(cmd, out, cancel) },
        { runtime.isRootfsValid() },
        historyFile,
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    /** How a command reaches the guest; [RuntimeManager.execInGuest] in the app, a fake in tests. */
    internal fun interface GuestExec {
        fun run(command: List<String>, onOutput: (String, Boolean) -> Unit, cancel: AtomicBoolean): Int
    }

    private val _lines = MutableStateFlow<List<TermLine>>(emptyList())
    private val _running = MutableStateFlow(false)
    private val _history = MutableStateFlow<List<String>>(loadHistory())

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
            if (_running.value) { appendNow(listOf(TermLine("A command is still running. Wait for it or cancel it first.", TermKind.INFO))); return }
            _running.value = true
        }
        remember(cmd)
        appendNow(cmd.lines().mapIndexed { i, l -> TermLine(Diag.redactor.redact((if (i == 0) "$ " else "> ") + l), TermKind.CMD) })
        val cancel = AtomicBoolean(false)
        cancelFlag = cancel
        job = scope.launch {
            try {
                if (!isInstalled()) {
                    append(TermLine("The Hermes runtime is not installed yet. Start Hermes once from the home screen so it can set itself up, then try again.", TermKind.INFO))
                    return@launch
                }
                val code = exec.run(listOf("bash", "-c", cmd), { text, isErr -> append(TermLine(clip(Diag.redactor.redact(AnsiStrip.strip(text))), if (isErr) TermKind.ERR else TermKind.OUT)) }, cancel)
                when {
                    cancel.get() -> append(TermLine("[cancelled]", TermKind.INFO))
                    code != 0 -> append(TermLine("[exit code $code]", TermKind.INFO))
                }
            } catch (e: Throwable) {
                AppLog.e("Terminal", "command failed to run", e)
                append(TermLine(Diag.redactor.redact("Could not run the command: ${e.javaClass.simpleName}: ${e.message}"), TermKind.INFO))
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

    private fun clip(s: String) = if (s.length > MAX_LINE_CHARS) s.take(MAX_LINE_CHARS) + "…" else s

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
    }
}
