package dev.pocketagent.app.runtime

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption

/** File-system operations that differ between Android (android.system.Os) and plain JVM (tests). */
interface FsOps {
    fun symlink(target: String, link: File)
    fun hardLink(existing: File, link: File)
    fun chmod(file: File, mode: Int)
}

class NioFsOps : FsOps {
    override fun symlink(target: String, link: File) {
        Files.createSymbolicLink(link.toPath(), java.nio.file.Paths.get(target))
    }
    override fun hardLink(existing: File, link: File) { Files.createLink(link.toPath(), existing.toPath()) }
    override fun chmod(file: File, mode: Int) {
        val perms = HashSet<java.nio.file.attribute.PosixFilePermission>()
        val p = java.nio.file.attribute.PosixFilePermission.values()
        // order: OWNER_READ, OWNER_WRITE, OWNER_EXECUTE, GROUP_*, OTHERS_*
        for (i in 0 until 9) if (mode and (1 shl (8 - i)) != 0) perms.add(p[i])
        try { Files.setPosixFilePermissions(file.toPath(), perms) } catch (_: Exception) { }
    }
}

data class ExtractProgress(val compressedRead: Long, val compressedTotal: Long, val entries: Long, val currentPath: String)

class ExtractStats(var files: Long = 0, var dirs: Long = 0, var symlinks: Long = 0, var hardlinks: Long = 0, var hardlinkCopies: Long = 0, var skipped: Long = 0, var bytes: Long = 0) {
    override fun toString() = "files=$files dirs=$dirs symlinks=$symlinks hardlinks=$hardlinks (copied=$hardlinkCopies) skipped=$skipped bytes=$bytes"
}

/**
 * Extracts a .tar.xz into [dest], preserving symlinks, hard links (copied when link(2) is
 * refused), modes and modification times; supports long names (PAX/GNU) via commons-compress.
 * Everything is confined to [dest]: entries that would escape it abort the extraction.
 */
class TarExtractor(private val fs: FsOps) {

    fun extractTarXz(
        source: InputStream,
        compressedTotal: Long,
        dest: File,
        isCancelled: () -> Boolean = { false },
        onProgress: (ExtractProgress) -> Unit = {},
    ): ExtractStats {
        val counting = CountingInputStream(source.buffered(1 shl 20))
        val stats = ExtractStats()
        dest.mkdirs()
        val destCanonical = dest.canonicalFile
        val dirModes = ArrayList<Pair<File, Int>>()
        var entries = 0L
        var lastReport = 0L
        val buf = ByteArray(256 * 1024)

        XZCompressorInputStream(counting, false, 64 * 1024 * 1024 /* memory limit in KiB */).use { xz ->
            TarArchiveInputStream(xz, "UTF-8").use { tar -> // names are UTF-8 (e.g. ca-certificates has non-ASCII file names)
                var e: TarArchiveEntry? = tar.nextEntry
                while (e != null) {
                    if (isCancelled()) throw InterruptedException("extraction cancelled")
                    val rel = normalise(e.name)
                    if (rel.isNotEmpty()) {
                        val out = File(dest, rel)
                        confine(destCanonical, out, rel)
                        out.parentFile?.let { if (!it.exists()) it.mkdirs() }
                        when {
                            e.isDirectory -> {
                                if (!out.isDirectory) { out.delete(); out.mkdirs() }
                                dirModes += out to e.mode
                                stats.dirs++
                            }
                            e.isSymbolicLink -> {
                                removeExisting(out)
                                fs.symlink(e.linkName, out)
                                stats.symlinks++
                            }
                            e.isLink -> {
                                removeExisting(out)
                                val target = File(dest, normalise(e.linkName))
                                confine(destCanonical, target, e.linkName)
                                try { fs.hardLink(target, out); stats.hardlinks++ }
                                catch (_: Exception) {
                                    Files.copy(target.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING)
                                    fs.chmod(out, fileMode(e.mode)); stats.hardlinks++; stats.hardlinkCopies++
                                }
                            }
                            e.isFile -> {
                                removeExisting(out)
                                FileOutputStream(out).use { o ->
                                    while (true) {
                                        val n = tar.read(buf)
                                        if (n < 0) break
                                        o.write(buf, 0, n); stats.bytes += n
                                    }
                                }
                                fs.chmod(out, fileMode(e.mode))
                                val mt = e.modTime?.time ?: 0L
                                if (mt > 0) out.setLastModified(mt)
                                stats.files++
                            }
                            else -> stats.skipped++ // devices, fifos: recreated by proot / the kernel at runtime
                        }
                    }
                    entries++
                    val now = System.nanoTime()
                    if (now - lastReport > 200_000_000L) {
                        lastReport = now
                        onProgress(ExtractProgress(counting.count, compressedTotal, entries, rel))
                    }
                    e = tar.nextEntry
                }
            }
        }
        // Directory modes last, so read-only directories do not block extraction of their children.
        for ((d, m) in dirModes.asReversed()) fs.chmod(d, dirMode(m))
        onProgress(ExtractProgress(compressedTotal, compressedTotal, entries, ""))
        return stats
    }

    private fun fileMode(m: Int) = (m and 0x1FF) or 0x180            // keep rwx bits, force owner rw, drop setuid/setgid/sticky
    private fun dirMode(m: Int) = (m and 0x3FF) or 0x1C0             // keep rwx + sticky, force owner rwx

    private fun removeExisting(f: File) {
        if (Files.exists(f.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            if (f.isDirectory && !Files.isSymbolicLink(f.toPath())) f.deleteRecursively() else f.delete()
        }
    }

    companion object {
        /** Strips leading "./" and "/" and rejects ".." components. Empty string means the root itself. */
        fun normalise(name: String): String {
            val parts = name.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
            if (parts.any { it == ".." }) throw IOException("unsafe path in archive: $name")
            return parts.joinToString("/")
        }

        private fun confine(rootCanonical: File, f: File, label: String) {
            // Lexical check only (parent directories we create are real directories; archive
            // symlinks are never followed because we do not create entries below them).
            val p = f.absoluteFile.toPath().normalize()
            if (!p.startsWith(rootCanonical.toPath())) throw IOException("path escapes extraction root: $label")
        }
    }
}

private class CountingInputStream(inp: InputStream) : FilterInputStream(inp) {
    var count = 0L
    override fun read(): Int { val r = super.read(); if (r >= 0) count++; return r }
    override fun read(b: ByteArray, off: Int, len: Int): Int { val r = super.read(b, off, len); if (r > 0) count += r; return r }
    override fun skip(n: Long): Long { val r = super.skip(n); if (r > 0) count += r; return r }
}
