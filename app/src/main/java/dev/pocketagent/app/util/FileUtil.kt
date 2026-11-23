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

    fun chmod(file: File, mode: Int) {
        try { Os.chmod(file.path, mode) } catch (_: Throwable) { }
    }

    /** Atomically replace [dest] with [bytes], creating it with [mode]. */
    fun writeAtomic(dest: File, bytes: ByteArray, mode: Int = 384 /* 0600 */) {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        tmp.writeBytes(bytes)
        chmod(tmp, mode)
        if (!tmp.renameTo(dest)) {
            dest.delete()
            if (!tmp.renameTo(dest)) throw java.io.IOException("cannot replace ${dest.name}")
        }
    }

    fun deleteRecursivelyNoFollow(f: File) {
        // File.delete() on a symlink removes the link itself; never descend into symlinked dirs.
        val isLink = try { java.nio.file.Files.isSymbolicLink(f.toPath()) } catch (_: Throwable) { false }
        if (!isLink && f.isDirectory) {
            f.listFiles()?.forEach { deleteRecursivelyNoFollow(it) }
        }
        f.delete()
    }

    fun formatBytes(b: Long): String = when {
        b >= 1L shl 30 -> "%.2f GiB".format(b / (1L shl 30).toDouble())
        b >= 1L shl 20 -> "%.1f MiB".format(b / (1L shl 20).toDouble())
        b >= 1L shl 10 -> "%.1f KiB".format(b / 1024.0)
        else -> "$b B"
    }
}
