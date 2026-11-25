package dev.pocketagent.app.diag

import dev.pocketagent.app.util.FileUtil
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The "Export logs" zip: a handful of flat text entries, every one redacted before it is cut. Pure apart from the files it is
 * pointed at; the redactor and the output stream are parameters, so the JVM tests cover all of it.
 */
object LogExport {
    /** A zip entry named [entryName] whose text [read] produces; null means "leave this entry out". */
    class Source(val entryName: String, val read: () -> String?)

    const val APP_LOG = "app.log"
    const val APP_LOG_PREVIOUS = "app.log.1"
    const val RUNTIME_LOG = "runtime.log"
    const val RUNTIME_LOG_PREVIOUS = "runtime.log.1"
    const val LAST_LAUNCH = "last-launch.txt"
    const val REPORT = "report.txt"

    /** How much of the end of a log file is read from disk at most. */
    const val MAX_FILE_BYTES = 2 * 1024 * 1024

    /**
     * Writes [sources] as a zip to [out] and closes it. Each text is passed through [redact] FIRST and only then cut to its last
     * [maxCharsPerEntry] characters, so a secret that straddles the cut cannot survive as a fragment. A source that returns null
     * is skipped; one that throws is written as a one-line note (the cause is never the text itself).
     */
    fun zip(out: OutputStream, sources: List<Source>, redact: (String) -> String, maxCharsPerEntry: Int = 1_000_000) {
        val zos = ZipOutputStream(out)
        try {
            for (s in sources) {
                require(s.entryName.isNotEmpty() && s.entryName.none { it == '/' || it == '\\' } && s.entryName != "..") {
                    "entry names are flat: ${s.entryName}"
                }
                val raw: String? = try { s.read() } catch (e: Exception) { "(could not be read: ${e.javaClass.simpleName})" }
                if (raw == null) continue
                val text = FileUtil.capTail(redact(raw), maxCharsPerEntry)
                zos.putNextEntry(ZipEntry(s.entryName))
                zos.write(text.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        } finally {
            zos.close()
        }
    }

    /**
     * The six entries of the export, in order. Only log files, the last launch line and the dump are ever read: never `.env`,
     * `config.yaml` or a credential file. [report] builds the diagnostics dump (already redacted, redacted again by [zip]).
     */
    fun sources(logsDir: File, lastLaunch: File, report: () -> String?): List<Source> = listOf(
        Source(APP_LOG) { readTail(File(logsDir, APP_LOG)) },
        Source(APP_LOG_PREVIOUS) { readTail(File(logsDir, APP_LOG_PREVIOUS)) },
        Source(RUNTIME_LOG) { readTail(File(logsDir, RUNTIME_LOG)) },
        Source(RUNTIME_LOG_PREVIOUS) { readTail(File(logsDir, RUNTIME_LOG_PREVIOUS)) },
        Source(LAST_LAUNCH) { readTail(lastLaunch) },
        Source(REPORT, report),
    )

    /** The last [maxBytes] of a text file (starting at a line start when it was cut), or null when there is no such file. */
    fun readTail(file: File, maxBytes: Int = MAX_FILE_BYTES): String? {
        if (!file.isFile) return null
        return RandomAccessFile(file, "r").use { raf ->
            val len = raf.length()
            val start = maxOf(0L, len - maxBytes)
            raf.seek(start)
            val buf = ByteArray((len - start).toInt())
            raf.readFully(buf)
            val text = String(buf, Charsets.UTF_8)
            if (start > 0) text.substringAfter('\n', text) else text
        }
    }
}
