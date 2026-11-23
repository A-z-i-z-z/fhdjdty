package dev.pocketagent.app.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persisted ring-buffer log. Two files (current + previous) of at most [MAX_BYTES]
 * each, so the log survives process death and is bounded. Never log secrets: callers
 * pass text through [redactor] which is installed by the diagnostics layer.
 */
object AppLog {
    private const val MAX_BYTES = 256 * 1024
    private var dir: File? = null
    private val lock = Any()
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /** Applied to every line before it is written. */
    @Volatile var redactor: (String) -> String = { it }

    fun init(context: Context) {
        synchronized(lock) {
            dir = File(context.filesDir, "logs").also { it.mkdirs() }
        }
        i("App", "log opened (pid ${android.os.Process.myPid()})")
    }

    private fun current(): File? = dir?.let { File(it, "app.log") }
    private fun previous(): File? = dir?.let { File(it, "app.log.1") }

    fun i(tag: String, msg: String) = write("I", tag, msg, null)
    fun w(tag: String, msg: String, t: Throwable? = null) = write("W", tag, msg, t)
    fun e(tag: String, msg: String, t: Throwable? = null) = write("E", tag, msg, t)

    private fun write(level: String, tag: String, msg: String, t: Throwable?) {
        val line = buildString {
            append(fmt.format(Date())).append(' ').append(level).append('/').append(tag).append(": ").append(msg)
            if (t != null) append(" | ").append(t.javaClass.simpleName).append(": ").append(t.message)
        }.let(redactor)
        try { android.util.Log.println(when (level) { "E" -> 6; "W" -> 5; else -> 4 }, "PocketAgent/$tag", line) } catch (_: Throwable) {}
        synchronized(lock) {
            val cur = current() ?: return
            try {
                if (cur.length() > MAX_BYTES) {
                    previous()?.delete()
                    cur.renameTo(previous()!!)
                }
                cur.appendText(line + "\n")
                if (t != null) {
                    val st = t.stackTraceToString().lines().take(12).joinToString("\n") { "    $it" }
                    cur.appendText(redactor(st) + "\n")
                }
            } catch (_: Throwable) { }
        }
    }

    /** Last [maxLines] lines across the two files, oldest first. */
    fun tail(maxLines: Int): String = synchronized(lock) {
        val lines = ArrayList<String>()
        previous()?.takeIf { it.exists() }?.let { lines += it.readLines() }
        current()?.takeIf { it.exists() }?.let { lines += it.readLines() }
        lines.takeLast(maxLines).joinToString("\n")
    }
}
