package dev.pocketagent.app.util

import android.system.Os
import java.io.File
import java.io.RandomAccessFile

object FileUtil {
    /** Last [maxLines] lines of a text file without reading all of it (reads at most [maxBytes] from the end). */
    fun tail(file: File, maxLines: Int, maxBytes: Int = 256 * 1024): String {
        if (!file.isFile) return "(missing: ${file.path})"
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val len = raf.length()
                val start = maxOf(0L, len - maxBytes)
                raf.seek(start)
                val buf = ByteArray((len - start).toInt())
                raf.readFully(buf)
                var text = String(buf, Charsets.UTF_8)
                if (start > 0) text = text.substringAfter('\n', text)
                text.lines().takeLast(maxLines).joinToString("\n")
            }
        } catch (e: Exception) { "(unreadable: ${e.javaClass.simpleName}: ${e.message})" }
    }

    /** First [n] lines starting at the most recent line containing [marker] (reads at most the last 2 MB). */
    fun headFromLastMarker(file: File, marker: String, n: Int): String {
        if (!file.isFile) return "(missing: ${file.path})"
        return try {
            val text = tail(file, Int.MAX_VALUE, 2 * 1024 * 1024)
            val idx = text.lastIndexOf(marker)
            val from = if (idx < 0) 0 else text.lastIndexOf('\n', idx) + 1
            text.substring(from).lines().take(n).joinToString("\n")
        } catch (e: Exception) { "(unreadable: ${e.message})" }
    }

    /** Keeps the first [cap] characters, marking the cut (for head-oriented sections). */
    fun capHead(s: String, cap: Int): String = if (s.length <= cap) s else s.take(cap) + "\n[...cut, first $cap of ${s.length} chars...]"

    /** Keeps the last [cap] characters, marking the cut. */
    fun capTail(s: String, cap: Int): String = if (s.length <= cap) s else "[...cut, last $cap of ${s.length} chars...]\n" + s.takeLast(cap)

    fun chmod(file: File, mode: Int) {
        try { Os.chmod(file.path, mode) } catch (_: Throwable) { }
    }

    /** Atomically replace [dest] with [bytes], creating it with [mode]. */
    fun writeAtomic(dest: File, bytes: ByteArray, mode: Int = 384 /* 0600 */) {
        dest.parentFile?.mkdirs()
        // Unique temp name: callers may race (start path, self-test, UI) and must never share a ".tmp".
        val tmp = File(dest.parentFile, ".${dest.name}.${android.os.Process.myPid()}.${System.nanoTime()}.tmp")
        try {
            tmp.writeBytes(bytes)
            chmod(tmp, mode)
            if (!tmp.renameTo(dest)) {
                dest.delete()
                if (!tmp.renameTo(dest)) throw java.io.IOException("cannot replace ${dest.name}")
            }
        } finally { if (tmp.exists()) tmp.delete() }
    }

    /**
     * Deletes [f] without following symlinks. Directories created by guest tooling may lack owner
     * write/execute (e.g. mode 0555), which makes unlink fail inside them, so each directory gets
     * u+rwx before it is emptied. Returns the first path that could not be removed, or null.
     */
    fun deleteRecursivelyNoFollow(f: File): String? {
        val path = f.toPath()
        if (!java.nio.file.Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return null
        var firstFailure: String? = null
        val isLink = java.nio.file.Files.isSymbolicLink(path)
        if (!isLink && f.isDirectory) {
            try {
                java.nio.file.Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"))
            } catch (_: Exception) { chmod(f, 448) }
            f.listFiles()?.forEach { c -> deleteRecursivelyNoFollow(c)?.let { if (firstFailure == null) firstFailure = it } }
        }
        if (!f.delete() && java.nio.file.Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS) && firstFailure == null) firstFailure = f.path
        return firstFailure
    }

    /** Like [deleteRecursivelyNoFollow] but throws naming the first undeletable path. */
    fun deleteOrThrow(f: File) {
        val bad = deleteRecursivelyNoFollow(f)
        if (bad != null || java.nio.file.Files.exists(f.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
            throw java.io.IOException("cannot delete ${bad ?: f.path}")
    }

    /** Reads at most [limit] bytes (InputStream.readNBytes needs API 33; minSdk is 26). */
    fun readLimited(s: java.io.InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() < limit) {
            val n = s.read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    fun formatBytes(b: Long): String = when {
        b >= 1L shl 30 -> "%.2f GiB".format(b / (1L shl 30).toDouble())
        b >= 1L shl 20 -> "%.1f MiB".format(b / (1L shl 20).toDouble())
        b >= 1L shl 10 -> "%.1f KiB".format(b / 1024.0)
        else -> "$b B"
    }
}
