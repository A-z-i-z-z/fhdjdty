package dev.pocketagent.app.runtime

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** Which of Hermes's two code folders an entry sits in: `hooks` (run without any enablement) or `plugins` (run when enabled). */
enum class GuestCodeKind { HOOK, PLUGIN }

/**
 * One thing in `hooks/` or `plugins/` of HERMES_HOME that the app did not write. [path] is relative to HERMES_HOME (`hooks/x`),
 * with the name cleaned for display. [complete]: a hook has `HOOK.yaml` and `handler.py`, a plugin `plugin.yaml` and
 * `__init__.py` (an incomplete one is not run). [files] counts regular files, stopping at [GuestCodeScan.MAX_FILES_PER_ENTRY].
 */
data class GuestCodeEntry(
    val kind: GuestCodeKind,
    val name: String,
    val path: String,
    val files: Int,
    val bytes: Long,
    val newestModified: Long,
    val complete: Boolean,
    val symlink: Boolean,
)

/**
 * Lists the code Hermes may run from its own folders (Wave 0: list only, nothing is deleted or changed). Both folders survive
 * updates ([UserState]). Pure file reads; never follows a symbolic link and never throws.
 */
object GuestCodeScan {
    const val MAX_ENTRIES = 100
    const val MAX_FILES_PER_ENTRY = 2000
    private const val MAX_NAME = 60

    private val FOLDERS = listOf(
        Triple("hooks", GuestCodeKind.HOOK, listOf("HOOK.yaml", "handler.py")),
        Triple("plugins", GuestCodeKind.PLUGIN, listOf("plugin.yaml", "__init__.py")),
    )

    /**
     * Entries of `hooks/` and `plugins/` under [hermesHome], sorted by path, at most [MAX_ENTRIES]. [appWritten] holds relative paths
     * (`hooks/x`) that the app wrote itself; they are left out. A loose file directly in either folder is an incomplete entry.
     */
    fun scan(hermesHome: File, appWritten: Set<String> = emptySet()): List<GuestCodeEntry> {
        val out = ArrayList<GuestCodeEntry>()
        try {
            for ((folder, kind, required) in FOLDERS) {
                val dir = File(hermesHome, folder)
                val names = try { dir.list() } catch (_: Exception) { null } ?: continue
                names.sort()
                for (raw in names) {
                    if (out.size >= MAX_ENTRIES) return out.sortedBy { it.path }
                    if ("$folder/$raw" in appWritten) continue
                    val entry = try { entryFor(File(dir, raw), kind, folder, raw, required) } catch (_: Exception) { null } ?: continue
                    out += entry
                }
            }
        } catch (_: Exception) {
            // never throws: what was found so far is returned
        }
        return out.sortedBy { it.path }
    }

    private fun entryFor(f: File, kind: GuestCodeKind, folder: String, raw: String, required: List<String>): GuestCodeEntry? {
        val p: Path = f.toPath()
        val clean = cleanName(raw)
        val path = "$folder/$clean"
        val attrs = try { Files.readAttributes(p, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS) } catch (_: IOException) { return null }
        val modified = attrs.lastModifiedTime().toMillis()
        if (attrs.isSymbolicLink) return GuestCodeEntry(kind, clean, path, 0, 0L, modified, false, true)
        if (attrs.isRegularFile) return GuestCodeEntry(kind, clean, path, 1, attrs.size(), modified, false, false)
        if (!attrs.isDirectory) return GuestCodeEntry(kind, clean, path, 0, 0L, modified, false, false)

        var files = 0
        var bytes = 0L
        var newest = modified
        try {
            Files.walkFileTree(p, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, a: BasicFileAttributes): FileVisitResult {
                    if (a.isRegularFile) {
                        files++; bytes += a.size()
                        val m = a.lastModifiedTime().toMillis()
                        if (m > newest) newest = m
                        if (files >= MAX_FILES_PER_ENTRY) return FileVisitResult.TERMINATE
                    }
                    return FileVisitResult.CONTINUE
                }
                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
            })
        } catch (_: Exception) {
            // an unreadable folder keeps what was counted so far
        }
        val complete = required.all { r ->
            try { Files.readAttributes(File(f, r).toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).isRegularFile } catch (_: Exception) { false }
        }
        return GuestCodeEntry(kind, clean, path, files, bytes, newest, complete, false)
    }

    /** Only `[A-Za-z0-9._ -]` survives (anything else becomes `?`), at most 60 characters: the name is shown and logged. */
    internal fun cleanName(raw: String): String {
        val sb = StringBuilder()
        for (ch in raw) {
            if (sb.length >= MAX_NAME) break
            sb.append(if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '.' || ch == '_' || ch == ' ' || ch == '-') ch else '?')
        }
        return sb.toString()
    }

    /** One plain line: `hooks/x (2 files), plugins/y (1 file, incomplete)`; `none` when the list is empty. */
    fun summary(entries: List<GuestCodeEntry>): String {
        if (entries.isEmpty()) return "none"
        return entries.joinToString(", ") { e ->
            val bits = ArrayList<String>()
            if (e.symlink) bits += "symlink" else bits += if (e.files == 1) "1 file" else "${e.files} files"
            if (!e.complete && !e.symlink) bits += "incomplete"
            "${e.path} (${bits.joinToString(", ")})"
        }
    }
}
