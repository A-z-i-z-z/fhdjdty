package dev.pocketagent.app.data

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Small file helpers with no Android dependencies (so they run in plain JVM unit tests). */
internal object Fs {
    /** Writes [bytes] to a temp file in the same directory, syncs it (unless [sync] is false), then renames it over [dest]. */
    fun writeAtomic(dest: File, bytes: ByteArray, sync: Boolean = true) {
        val dir = dest.absoluteFile.parentFile ?: throw java.io.IOException("no parent directory for ${dest.path}")
        dir.mkdirs()
        val tmp = File(dir, ".${dest.name}.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(tmp).use { it.write(bytes); it.flush(); if (sync) try { it.fd.sync() } catch (_: Exception) { } }
            try {
                Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: Exception) {
                Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    fun writeAtomic(dest: File, text: String, sync: Boolean = true) = writeAtomic(dest, text.toByteArray(Charsets.UTF_8), sync)

    /** Same as [writeAtomic], but first keeps the previous contents next to it as `<name>.bak`. */
    fun writeAtomicWithBackup(dest: File, text: String) {
        if (dest.isFile) {
            try {
                Files.copy(dest.toPath(), File(dest.absoluteFile.parentFile, dest.name + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (_: Exception) { /* a failed backup must not block the save */ }
        }
        writeAtomic(dest, text)
    }

    /** The file's text, null when it does not exist, and an IOException when it exists but cannot be read (so callers never mistake that for "empty"). */
    fun readTextStrict(f: File): String? = if (!f.exists()) null else f.readText(Charsets.UTF_8)

    fun readTextOrNull(f: File): String? = try { if (f.isFile) f.readText(Charsets.UTF_8) else null } catch (_: Exception) { null }
}
