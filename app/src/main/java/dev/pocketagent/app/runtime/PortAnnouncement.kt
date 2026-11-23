package dev.pocketagent.app.runtime

import java.io.File
import java.io.RandomAccessFile

/**
 * The proof that a listener on our port is OUR Hermes: the child printed "API server listening on
 * http://127.0.0.1:<port>" in ITS OWN output during THIS launch. The log file is appended across runs, so only the
 * bytes written after [offset] (the file length recorded right before launching) count, and the port must be
 * followed by a non-digit (8642 must not match 86421).
 */
object PortAnnouncement {
    fun found(text: String, host: String, port: Int): Boolean =
        Regex("API server listening on http://" + Regex.escape(host) + ":" + port + "(?!\\d)").containsMatchIn(text)

    /** Reads the bytes appended after [offset] (at most [maxBytes], the early part is what matters). */
    fun foundInFileSince(file: File, offset: Long, host: String, port: Int, maxBytes: Int = 4 * 1024 * 1024): Boolean {
        if (!file.isFile) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val len = raf.length()
                if (len <= offset) return false
                raf.seek(offset)
                val buf = ByteArray(minOf((len - offset), maxBytes.toLong()).toInt())
                raf.readFully(buf)
                found(String(buf, Charsets.UTF_8), host, port)
            }
        } catch (_: Exception) { false }
    }
}
