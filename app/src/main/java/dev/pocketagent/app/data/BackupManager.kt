package dev.pocketagent.app.data

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import dev.pocketagent.app.BuildConfig
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.RestartActivity
import dev.pocketagent.app.runtime.Paths
import dev.pocketagent.app.service.AppForeground
import dev.pocketagent.app.util.AppLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A problem with a backup file or with writing one. The message is plain English and safe to show (it never holds a secret).
 * [restartNeeded] is true only when a restore had already started replacing data: the app must be restarted.
 */
class BackupException(
    message: String, val restartNeeded: Boolean = false, val reason: BackupReason = BackupReason.OTHER,
) : Exception(message)

/**
 * Why a backup or restore failed, in a form the UI can turn into a text in the user's language. The English message of the
 * exception stays for logs; [OTHER] means "no translation known, show the message as it is".
 */
enum class BackupReason {
    NOT_BACKUP, NEWER_VERSION, TOO_LARGE, TOO_MANY_FILES, UNSAFE, DAMAGED, CANNOT_READ, CANNOT_WRITE,
    BUSY, TERMINAL_RUNNING, UNPACK_FAILED, SAFETY_COPY_FAILED, HALFWAY, OTHER,
}

/**
 * Backup and restore of everything the user made: chats, bots, routines, usage numbers, drafts, a few preference files and
 * (optionally) attachments and Hermes's SOUL.md, memory and skills, in ONE zip file.
 *
 * Credentials are never part of a backup: nothing from `files/secrets`, no `.env`, no `config.yaml`, no auth or key files, no
 * provider settings. The file logic lives in [BackupCore] (plain `File` and stream arguments, covered by BackupManagerTest);
 * this class only connects it to the running app. It reads the app's members lazily (never in the constructor).
 */
class BackupManager(private val app: PocketApp) {
    data class Options(val includeAttachments: Boolean = true, val includeHermes: Boolean = true)

    data class Summary(
        val createdAt: Long, val appVersion: String, val conversations: Int, val bots: Int,
        val routines: Int, val files: Int, val bytes: Long,
    )

    private val paths: Paths by lazy { Paths(app) }

    private companion object {
        /** How long [restartApp] waits for [RestartActivity] to kill this process before it ends itself. */
        const val FALLBACK_KILL_MS = 5_000L
    }

    /** Writes the backup zip to [out] (the stream stays open: the caller closes it). Blocking: call off the main thread. */
    fun export(out: OutputStream, options: Options = Options()): Summary {
        flushAll()
        val hermes = paths.hermesHome.takeIf { it.isDirectory }
        val sources = BackupCore.Sources(File(paths.files, "chat"), hermes, readPrefs())
        return BackupCore.export(out, sources, options, BuildConfig.VERSION_NAME)
    }

    /** Reads the whole file and checks it without writing anything. Throws [BackupException] when it is not a usable backup. */
    fun inspect(input: InputStream): Summary = BackupCore.scan(input, null).toSummary()

    /**
     * Replaces the app's data with the backup. Blocking (can take a while). The caller restarts the process afterwards
     * ([restartApp]). Nothing changes when this throws a [BackupException] whose `restartNeeded` is false.
     */
    fun restore(input: InputStream): Summary {
        if (app.engine.busy.value.isNotEmpty()) throw BackupException("A chat is answering right now. Wait for it to finish, then try again.", reason = BackupReason.BUSY)
        if (app.terminal.running.value) throw BackupException("A terminal command is running. Wait for it to finish, then try again.", reason = BackupReason.TERMINAL_RUNNING)

        val backupDir = File(paths.files, "backup").also { it.mkdirs() }
        val staging = File(backupDir, "staging")
        BackupCore.deleteTree(staging)

        // 1. Unpack and validate everything into a staging directory. Nothing of the live data is touched yet.
        val scan = try {
            BackupCore.scan(input, staging)
        } catch (e: BackupException) {
            BackupCore.deleteTree(staging); throw e
        } catch (e: Throwable) {
            BackupCore.deleteTree(staging)
            throw BackupException("Could not unpack the backup: ${e.javaClass.simpleName}: ${e.message}", reason = BackupReason.UNPACK_FAILED)
        }

        // 2. A safety copy of what is on the phone now.
        val pre = File(backupDir, "pre-restore.zip")
        val preTmp = File(backupDir, "pre-restore.zip.tmp")
        try {
            FileOutputStream(preTmp).use { export(it, Options(includeAttachments = true, includeHermes = true)) }
            Files.move(preTmp.toPath(), pre.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Throwable) {
            BackupCore.deleteTree(staging); preTmp.delete()
            throw BackupException("Could not save a safety copy of your current data first, so nothing was changed (${e.message}).", reason = BackupReason.SAFETY_COPY_FAILED)
        }

        // 3. From here on the data is replaced: stop Hermes, stop every writer, swap, write the preferences.
        try { app.presence.noteUserStop() } catch (e: Throwable) { AppLog.w("Backup", "noteUserStop failed", e) }
        stopHermes()
        try {
            app.chat.suspendWrites(); app.usage.suspendWrites(); app.drafts.suspendWrites(); app.routines.suspendWrites()
            val rt = app.runtime
            val hermesTarget = if (rt.isRootfsValid() && paths.hermesHome.isDirectory) paths.hermesHome else paths.userStatePreserve
            BackupCore.swapIn(staging, File(paths.files, "chat"), hermesTarget, scan.manifest)
            applyPrefs(staging)
        } catch (e: Throwable) {
            AppLog.e("Backup", "restore failed halfway", e)
            throw BackupException(
                "The restore stopped halfway (${e.message}). Restart the app. A copy of your previous data is kept inside the app as backup/pre-restore.zip.",
                restartNeeded = true, reason = BackupReason.HALFWAY,
            )
        } finally {
            BackupCore.deleteTree(staging)
        }
        AppLog.i("Backup", "restored a backup made by ${scan.manifest.app}: ${scan.files} files, ${scan.bytes} bytes")
        return scan.toSummary()
    }

    /**
     * Starts the app again from scratch (the data on disk is what the next process reads). [RestartActivity] runs in its own
     * process: it kills this one and starts [MainActivity] again, which a new main process then serves. If that does not happen
     * within a few seconds this process ends itself, so a restore never leaves a process alive with its writes suspended.
     *
     * When the app is not in the foreground (the user left during the restore) Android silently blocks starting an activity, so
     * instead a "Restore finished, tap to open" notification is posted (synchronously) and the process ends at once.
     */
    fun restartApp() {
        if (!AppForeground.isForeground.value) {
            try { app.presence.notifier.postRestoreFinished() } catch (e: Throwable) { AppLog.w("Backup", "could not post the restore notification", e) }
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(0)
            return
        }
        try {
            app.startActivity(
                Intent(app, RestartActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(RestartActivity.EXTRA_MAIN_PID, android.os.Process.myPid()),
            )
        } catch (e: Throwable) {
            AppLog.w("Backup", "could not start the restart activity", e)
        }
        Handler(Looper.getMainLooper()).postDelayed({
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(0)
        }, FALLBACK_KILL_MS)
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private fun flushAll() {
        try { app.chat.flush() } catch (e: Throwable) { AppLog.w("Backup", "chat flush failed", e) }
        try { app.usage.flush() } catch (e: Throwable) { AppLog.w("Backup", "usage flush failed", e) }
        try { app.drafts.flush() } catch (e: Throwable) { AppLog.w("Backup", "drafts flush failed", e) }
        try { app.routines.flush() } catch (e: Throwable) { AppLog.w("Backup", "routines flush failed", e) }
    }

    private fun stopHermes() {
        val rt = app.runtime
        if (!rt.state.value.isBusyOrRunning && !rt.processAlive()) return
        rt.stop()
        val end = SystemClock.elapsedRealtime() + 20_000
        while (SystemClock.elapsedRealtime() < end) {
            if (!rt.state.value.isBusyOrRunning && !rt.processAlive()) return
            try { Thread.sleep(100) } catch (_: InterruptedException) { return }
        }
        AppLog.w("Backup", "Hermes did not stop within 20 s; continuing")
    }

    private fun readPrefs(): Map<String, Map<String, *>> =
        BackupCore.PREF_SCHEMA.keys.associateWith { name -> app.getSharedPreferences(name, android.content.Context.MODE_PRIVATE).all }

    private fun applyPrefs(staging: File) {
        for (name in BackupCore.PREF_SCHEMA.keys) {
            val f = File(staging, "prefs/$name.json")
            if (!f.isFile) continue
            val values = BackupCore.prefsFromJson(name, f.readText(Charsets.UTF_8))
            val ed = app.getSharedPreferences(name, android.content.Context.MODE_PRIVATE).edit().clear()
            for (v in values) {
                when (v.type) {
                    'b' -> ed.putBoolean(v.key, v.value as Boolean)
                    'i' -> ed.putInt(v.key, v.value as Int)
                    'l' -> ed.putLong(v.key, v.value as Long)
                    'f' -> ed.putFloat(v.key, v.value as Float)
                    's' -> ed.putString(v.key, v.value as String)
                }
            }
            if (!ed.commit()) throw IOException("could not write the $name preferences")
        }
    }

    private fun BackupCore.ScanResult.toSummary() = Summary(
        createdAt = manifest.created, appVersion = manifest.app, conversations = manifest.conversations,
        bots = manifest.bots, routines = manifest.routines, files = files, bytes = bytes,
    )
}

/**
 * The file logic of [BackupManager]: which names may go into a backup and come out of it, the zip writer and the validating
 * reader, and the swap into the live directories. No Android classes, so the unit tests drive it with temp directories.
 */
internal object BackupCore {
    const val FORMAT = 1
    const val MANIFEST = "manifest.json"
    const val DEFAULT_MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024
    const val DEFAULT_MAX_ENTRIES = 200_000
    private const val MAX_MANIFEST_BYTES = 64 * 1024
    private const val MAX_NAME_CHARS = 240
    private const val MAX_SEGMENT_CHARS = 200
    private const val MAX_DEPTH = 10
    private const val MAX_EXPORT_FILE_BYTES = 256L * 1024 * 1024
    private const val MAX_COUNT_FILE_BYTES = 100L * 1024 * 1024
    private const val MAX_PREFS_FILE_BYTES = 256 * 1024

    /** The JSON files that sit directly in `files/chat`. */
    val CHAT_INDEX_FILES = listOf("bots.json", "conversations.json", "routines.json", "usage.json", "drafts.json")

    /**
     * Which SharedPreferences are saved and which keys and types they may hold ('b' boolean, 'i' int, 'l' long, 'f' float, 's' string).
     * A schema, not "everything": a restored file can then never put a value of the wrong type under a key the app reads
     * (that would crash the next start). Provider settings and secrets are deliberately absent.
     */
    val PREF_SCHEMA: Map<String, Map<String, Char>> = mapOf(
        "appearance" to mapOf(
            "theme" to 's', "accent" to 's', "darkMode" to 's', "textScale" to 'f', "animations" to 's', "showMascot" to 'b',
            "bubbleStyle" to 's', "language" to 's', "pixelFontTitles" to 'b',
        ),
        "voice_sound" to mapOf(
            "ttsEngine" to 's', "voiceSid" to 'i', "speed" to 'f', "qualitySteps" to 'i', "autoSpeak" to 'b',
            "soundPack" to 's', "soundVolume" to 'f',
        ),
        "presence" to mapOf(
            "autoStart" to 'b', "wakeOnSend" to 'b', "notifyReplies" to 'b', "notifyApprovals" to 'b', "autoRestart" to 'b',
            "idleStopMinutes" to 'i',
        ),
    )

    data class Limits(val maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES, val maxEntries: Int = DEFAULT_MAX_ENTRIES)

    class Sources(val chatDir: File, val hermesHome: File?, val prefs: Map<String, Map<String, *>>)

    class Manifest(
        val format: Int, val app: String, val created: Long, val conversations: Int, val bots: Int, val routines: Int,
        val attachments: Boolean, val hermes: Boolean, val files: Int, val bytes: Long,
    )

    class ScanResult(val manifest: Manifest, val files: Int, val bytes: Long, val skipped: Int)

    class PrefValue(val key: String, val type: Char, val value: Any)

    enum class Kind { MANIFEST, CHAT_INDEX, CHAT_MESSAGE, ATTACHMENT, PREFS, HERMES }

    sealed interface Check {
        data class Accept(val kind: Kind) : Check
        data object Skip : Check
        data class Reject(val reason: String) : Check
    }

    private val MESSAGE_FILE = Regex("[A-Za-z0-9_-]{1,100}\\.json")
    private val FLAT_NAME = Regex("[A-Za-z0-9._-]{1,200}")
    private val SOFT_SECRET_STEM = Regex("(^|[-_.])(credentials?|secrets?|api[-_]?keys?|auth|tokens?|passwords?|private[-_]?keys?)([-_.]|$)")
    private val HARD_SECRET_NAMES = setOf(
        ".env", "config.yaml", "config.yml", "auth.json", "secrets", "api_key", "service-account.json", "authorized-user.json",
        "gemini_api_key", "vertex-sa.json", "credentials.json", "token.json", "id_rsa", "id_ed25519", ".netrc",
    )
    private val SECRET_SUFFIXES = listOf(".pem", ".key", ".p12", ".pfx", ".jks", ".keystore")

    /** Names that must never travel in a backup, wherever they sit. */
    fun isHardSecret(segment: String): Boolean {
        val s = segment.lowercase()
        return s in HARD_SECRET_NAMES || s.startsWith(".env") || SECRET_SUFFIXES.any { s.endsWith(it) }
    }

    /** A looser test for Hermes's own files (not Markdown): `api-key.txt`, `token`, `my_credentials.yaml`... */
    private fun isSoftSecret(fileName: String): Boolean {
        val lower = fileName.lowercase()
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return false
        return SOFT_SECRET_STEM.containsMatchIn(lower.substringBeforeLast('.', lower))
    }

    /**
     * Decides what a zip entry name means. [Check.Reject] = the name is unsafe (traversal, absolute, odd characters) or in an
     * unknown top-level folder: the whole archive is refused. [Check.Skip] = a known folder but a file we neither write nor
     * accept (secrets, hidden files, unknown names): ignored. Export and restore use this same function.
     */
    fun checkEntry(rawName: String): Check {
        val name = rawName.removeSuffix("/")
        if (name.isEmpty() || name.length > MAX_NAME_CHARS) return Check.Reject("an entry name is empty or too long")
        if (name.startsWith("/") || name.contains('\\') || name.any { it.code < 32 || it.code == 127 }) return Check.Reject("an entry name is not a plain relative path")
        val parts = name.split('/')
        if (parts.size > MAX_DEPTH) return Check.Reject("an entry is nested too deeply")
        if (parts.any { it.isEmpty() || it == "." || it == ".." || it.length > MAX_SEGMENT_CHARS || it.contains(':') }) return Check.Reject("an entry name leaves its folder")
        return when (parts[0]) {
            MANIFEST -> if (parts.size == 1) Check.Accept(Kind.MANIFEST) else Check.Reject("an entry name is not allowed")
            "chat" -> when {
                parts.size == 2 && parts[1] in CHAT_INDEX_FILES -> Check.Accept(Kind.CHAT_INDEX)
                parts.size == 3 && parts[1] == "messages" && MESSAGE_FILE.matches(parts[2]) -> Check.Accept(Kind.CHAT_MESSAGE)
                else -> Check.Skip
            }
            "attachments" ->
                if (parts.size == 2 && FLAT_NAME.matches(parts[1]) && !parts[1].startsWith(".") && !isHardSecret(parts[1])) Check.Accept(Kind.ATTACHMENT)
                else Check.Skip
            "prefs" ->
                if (parts.size == 2 && parts[1].endsWith(".json") && parts[1].removeSuffix(".json") in PREF_SCHEMA) Check.Accept(Kind.PREFS)
                else Check.Skip
            "hermes" -> checkHermes(parts)
            else -> Check.Reject("it holds an unknown folder")
        }
    }

    private fun checkHermes(parts: List<String>): Check {
        if (parts.size < 2) return Check.Skip
        if (parts.any { it.startsWith(".") || it == "__pycache__" || isHardSecret(it) }) return Check.Skip
        val file = parts.last()
        if (file.endsWith(".bak") || file.endsWith(".pyc") || file.endsWith(".tmp")) return Check.Skip
        return when (parts[1]) {
            "SOUL.md" -> if (parts.size == 2) Check.Accept(Kind.HERMES) else Check.Skip
            "memories" ->
                if (parts.size == 3 && (file.endsWith(".md") || file.endsWith(".txt")) && !isSoftSecret(file)) Check.Accept(Kind.HERMES) else Check.Skip
            "skills" ->
                if (parts.size >= 3 && !isSoftSecret(file)) Check.Accept(Kind.HERMES) else Check.Skip
            else -> Check.Skip
        }
    }

    // ---- export ---------------------------------------------------------------------------------------------------

    private class PlanItem(val name: String, val file: File?, val bytes: ByteArray?)

    fun export(
        out: OutputStream, src: Sources, options: BackupManager.Options, appVersion: String,
        now: Long = System.currentTimeMillis(), limits: Limits = Limits(),
    ): BackupManager.Summary {
        val plan = ArrayList<PlanItem>()
        fun addFile(name: String, f: File) {
            if (checkEntry(name) !is Check.Accept) return
            if (!isRegular(f) || f.length() > MAX_EXPORT_FILE_BYTES) return
            plan += PlanItem(name, f, null)
        }
        // chat
        for (n in CHAT_INDEX_FILES) addFile("chat/$n", File(src.chatDir, n))
        listFlat(File(src.chatDir, "messages")).forEach { addFile("chat/messages/${it.name}", it) }
        if (options.includeAttachments) listFlat(File(src.chatDir, "attachments")).forEach { addFile("attachments/${it.name}", it) }
        // preferences (only the schema's keys, with matching types)
        for ((name, keys) in PREF_SCHEMA) {
            val all = src.prefs[name] ?: continue
            val json = prefsToJson(all, keys)
            if (json != null) plan += PlanItem("prefs/$name.json", null, json.toByteArray(Charsets.UTF_8))
        }
        // Hermes
        val hh = src.hermesHome
        if (options.includeHermes && hh != null) {
            addFile("hermes/SOUL.md", File(hh, "SOUL.md"))
            listFlat(File(hh, "memories")).forEach { addFile("hermes/memories/${it.name}", it) }
            walkSkills(File(hh, "skills"), "hermes/skills", 0) { name, f -> addFile(name, f) }
        }
        if (plan.size + 1 > limits.maxEntries) throw BackupException("There are too many files to back up (${plan.size}).", reason = BackupReason.TOO_MANY_FILES)

        var total = 0L
        val shield = object : FilterOutputStream(out) {
            override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len) }
            override fun close() { try { flush() } catch (_: IOException) { } }
        }
        val summary = ZipOutputStream(BufferedOutputStream(shield, 64 * 1024)).use { zos ->
            zos.setLevel(6)
            val buf = ByteArray(32 * 1024)
            for (item in plan) {
                val e = ZipEntry(item.name)
                item.file?.let { e.time = it.lastModified() }
                zos.putNextEntry(e)
                if (item.bytes != null) {
                    zos.write(item.bytes); total += item.bytes.size
                } else {
                    java.io.FileInputStream(item.file!!).use { ins ->
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > limits.maxTotalBytes) throw BackupException("Everything together is larger than ${limits.maxTotalBytes / (1024 * 1024)} MiB, which is more than a backup may hold.", reason = BackupReason.TOO_LARGE)
                            zos.write(buf, 0, n)
                        }
                    }
                }
                zos.closeEntry()
            }
            val conversations = countItems(File(src.chatDir, "conversations.json"), "conversations")
            val bots = countItems(File(src.chatDir, "bots.json"), "bots")
            val routines = countItems(File(src.chatDir, "routines.json"), "routines")
            val manifest = JSONObject()
                .put("format", FORMAT).put("app", appVersion).put("created", now)
                .put("conversations", conversations).put("bots", bots).put("routines", routines)
                .put("attachments", options.includeAttachments).put("hermes", options.includeHermes && hh != null)
                .put("files", plan.size).put("bytes", total)
            zos.putNextEntry(ZipEntry(MANIFEST))
            zos.write(manifest.toString(1).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            BackupManager.Summary(now, appVersion, conversations, bots, routines, plan.size, total)
        }
        return summary
    }

    private fun isRegular(f: File): Boolean = try { Files.isRegularFile(f.toPath(), LinkOption.NOFOLLOW_LINKS) } catch (_: Exception) { false }

    private fun listFlat(dir: File): List<File> = try {
        if (!dir.isDirectory || Files.isSymbolicLink(dir.toPath())) emptyList() else (dir.listFiles() ?: emptyArray()).sortedBy { it.name }
    } catch (_: Exception) { emptyList() }

    private fun walkSkills(dir: File, prefix: String, depth: Int, add: (String, File) -> Unit) {
        if (depth > 8 || !dir.isDirectory || Files.isSymbolicLink(dir.toPath())) return
        for (k in listFlat(dir)) {
            val n = k.name
            if (n.startsWith(".") || n == "__pycache__") continue
            val rel = "$prefix/$n"
            if (Files.isSymbolicLink(k.toPath())) continue
            if (k.isDirectory) walkSkills(k, rel, depth + 1, add) else add(rel, k)
        }
    }

    /** Number of items in a chat index file: the array under [key], else the first array found, else 0. Never throws. */
    fun countItems(f: File, key: String): Int = try {
        if (!isRegular(f) || f.length() > MAX_COUNT_FILE_BYTES) 0 else {
            val text = f.readText(Charsets.UTF_8).trim()
            if (text.startsWith("[")) JSONArray(text).length()
            else {
                val o = JSONObject(text)
                o.optJSONArray(key)?.length() ?: o.keys().asSequence().mapNotNull { o.optJSONArray(it) }.firstOrNull()?.length() ?: 0
            }
        }
    } catch (_: Exception) { 0 }

    // ---- preferences as JSON -------------------------------------------------------------------------------------

    /** `{"v":1,"values":{"key":{"t":"b","v":true},...}}`, only schema keys whose value has the schema's type; null when nothing is left. */
    fun prefsToJson(all: Map<String, *>, schema: Map<String, Char>): String? {
        val values = JSONObject()
        for ((key, type) in schema) {
            val v = all[key] ?: continue
            val ok = when (type) { 'b' -> v is Boolean; 'i' -> v is Int; 'l' -> v is Long; 'f' -> v is Float; 's' -> v is String; else -> false }
            if (!ok) continue
            val jv: Any = if (v is Float) v.toDouble() else v
            values.put(key, JSONObject().put("t", type.toString()).put("v", jv))
        }
        if (values.length() == 0) return null
        return JSONObject().put("v", 1).put("values", values).toString(1)
    }

    /** The typed values of a saved preferences file for [prefsName]; keys outside the schema or of the wrong type are dropped. */
    fun prefsFromJson(prefsName: String, text: String): List<PrefValue> {
        val schema = PREF_SCHEMA[prefsName] ?: return emptyList()
        val values = try { JSONObject(text).getJSONObject("values") } catch (e: Exception) { throw BackupException("A settings file in the backup is damaged.", reason = BackupReason.DAMAGED) }
        val out = ArrayList<PrefValue>()
        for ((key, type) in schema) {
            val o = values.optJSONObject(key) ?: continue
            if (o.optString("t") != type.toString() || !o.has("v") || o.isNull("v")) continue
            val raw = o.get("v")
            val v: Any = when (type) {
                'b' -> (raw as? Boolean) ?: continue
                'i' -> (raw as? Number)?.toLong()?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt() ?: continue
                'l' -> (raw as? Number)?.toLong() ?: continue
                'f' -> (raw as? Number)?.toFloat()?.takeIf { !it.isNaN() && !it.isInfinite() } ?: continue
                's' -> (raw as? String)?.takeIf { it.length <= 10_000 } ?: continue
                else -> continue
            }
            out += PrefValue(key, type, v)
        }
        return out
    }

    // ---- restore: read and validate ---------------------------------------------------------------------------------

    /**
     * Reads a backup zip from [input]. Every entry name is checked ([checkEntry]); sizes are counted while reading (the
     * sizes announced in the zip are not trusted) against [limits]. With [staging] the accepted files are written below it
     * (each target must resolve inside the staging directory); without it nothing is written. Throws [BackupException].
     */
    fun scan(input: InputStream, staging: File?, limits: Limits = Limits()): ScanResult {
        val stagingRoot = staging?.also { it.mkdirs() }?.canonicalFile
        val seen = HashSet<String>()
        var entries = 0
        var total = 0L
        var files = 0
        var payloadBytes = 0L
        var skipped = 0
        var manifest: Manifest? = null
        val buf = ByteArray(32 * 1024)
        try {
            ZipInputStream(BufferedInputStream(input)).use { zin ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    entries++
                    if (entries > limits.maxEntries) throw BackupException("The backup has more than ${limits.maxEntries} entries.", reason = BackupReason.TOO_MANY_FILES)
                    val check = checkEntry(e.name)
                    if (check is Check.Reject) throw BackupException("This backup cannot be used: ${check.reason}.", reason = BackupReason.UNSAFE)
                    if (e.isDirectory) continue
                    if (check is Check.Skip) {
                        skipped++
                        total = drain(zin, buf, total, limits)
                        continue
                    }
                    if (!seen.add(e.name)) throw BackupException("This backup cannot be used: an entry appears twice.", reason = BackupReason.UNSAFE)
                    val kind = (check as Check.Accept).kind
                    if (kind == Kind.MANIFEST) {
                        val bytes = readLimited(zin, MAX_MANIFEST_BYTES + 1)
                        if (bytes.size > MAX_MANIFEST_BYTES) throw BackupException("This backup cannot be used: its manifest is too large.", reason = BackupReason.UNSAFE)
                        total += bytes.size
                        manifest = parseManifest(bytes)
                        continue
                    }
                    val target = stagingRoot?.let { root ->
                        val t = File(root, e.name)
                        val canon = t.canonicalFile
                        if (!canon.path.startsWith(root.path + File.separator)) throw BackupException("This backup cannot be used: an entry leaves its folder.", reason = BackupReason.UNSAFE)
                        canon.parentFile?.mkdirs()
                        canon
                    }
                    var size = 0L
                    val sink: OutputStream? = target?.let { FileOutputStream(it) }
                    try {
                        while (true) {
                            val n = zin.read(buf)
                            if (n < 0) break
                            total += n; size += n
                            if (kind == Kind.PREFS && size > MAX_PREFS_FILE_BYTES) throw BackupException("This backup cannot be used: a settings file is far too large.", reason = BackupReason.UNSAFE)
                            if (total > limits.maxTotalBytes) throw BackupException("The backup is larger than ${limits.maxTotalBytes / (1024 * 1024)} MiB once unpacked, which is more than a backup may hold.", reason = BackupReason.TOO_LARGE)
                            sink?.write(buf, 0, n)
                        }
                    } finally { sink?.close() }
                    files++
                    payloadBytes += size
                }
            }
        } catch (e: BackupException) {
            throw e
        } catch (e: IOException) {
            throw BackupException("Could not read the backup file (${e.message ?: e.javaClass.simpleName}). If the phone is almost full, free some space and try again.", reason = BackupReason.UNPACK_FAILED)
        }
        val m = manifest ?: throw BackupException("This file is not an Agirî backup (it has no manifest).", reason = BackupReason.NOT_BACKUP)
        return ScanResult(m, files, payloadBytes, skipped)
    }

    /** Reads and discards the rest of the current entry, counting it against the limit. */
    private fun drain(zin: ZipInputStream, buf: ByteArray, startTotal: Long, limits: Limits): Long {
        var total = startTotal
        while (true) {
            val n = zin.read(buf)
            if (n < 0) return total
            total += n
            if (total > limits.maxTotalBytes) throw BackupException("The backup is larger than ${limits.maxTotalBytes / (1024 * 1024)} MiB once unpacked, which is more than a backup may hold.", reason = BackupReason.TOO_LARGE)
        }
    }

    private fun readLimited(s: InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() < limit) {
            val n = s.read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    fun parseManifest(bytes: ByteArray): Manifest {
        val o = try { JSONObject(String(bytes, Charsets.UTF_8)) } catch (e: Exception) { throw BackupException("This file is not an Agirî backup (its manifest is damaged).", reason = BackupReason.NOT_BACKUP) }
        val format = o.optInt("format", 0)
        if (format < 1) throw BackupException("This file is not an Agirî backup.", reason = BackupReason.NOT_BACKUP)
        if (format > FORMAT) throw BackupException("This backup was made by a newer version of Agirî. Update the app first.", reason = BackupReason.NEWER_VERSION)
        fun count(key: String) = o.optInt(key, 0).coerceAtLeast(0)
        val app = o.optString("app", "").filter { !it.isISOControl() }.take(40)
        return Manifest(
            format = format, app = app, created = o.optLong("created", 0L).coerceAtLeast(0L),
            conversations = count("conversations"), bots = count("bots"), routines = count("routines"),
            attachments = o.optBoolean("attachments", false), hermes = o.optBoolean("hermes", false),
            files = count("files"), bytes = o.optLong("bytes", 0L).coerceAtLeast(0L),
        )
    }

    // ---- restore: swap the staged data in -----------------------------------------------------------------------------

    /**
     * Replaces the chat data below [chatDir] with what was staged (index files and messages always; attachments only when the
     * backup holds them, so a backup made without attachments never deletes the ones on the phone). Hermes's files are
     * copied over [hermesTarget] file by file (a backup never deletes anything of Hermes); null skips them.
     */
    fun swapIn(staging: File, chatDir: File, hermesTarget: File?, manifest: Manifest) {
        chatDir.mkdirs()
        for (n in CHAT_INDEX_FILES) replace(File(chatDir, n), File(staging, "chat/$n"))
        replace(File(chatDir, "messages"), File(staging, "chat/messages"))
        if (manifest.attachments) replace(File(chatDir, "attachments"), File(staging, "attachments"))
        if (manifest.hermes && hermesTarget != null) overlay(File(staging, "hermes"), hermesTarget)
    }

    private fun replace(dest: File, src: File) {
        deleteTree(dest)
        if (!Files.exists(src.toPath(), LinkOption.NOFOLLOW_LINKS)) return
        dest.absoluteFile.parentFile?.mkdirs()
        Files.move(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun overlay(srcRoot: File, dstRoot: File) {
        if (!srcRoot.isDirectory) return
        val base: Path = srcRoot.toPath()
        Files.walkFileTree(base, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (attrs.isRegularFile) {
                    val rel = base.relativize(file).toString().replace(File.separatorChar, '/')
                    // Same rules as when reading the zip: a name that fails them is never written.
                    if (checkEntry("hermes/$rel") is Check.Accept) {
                        val target = File(dstRoot, rel)
                        target.absoluteFile.parentFile?.mkdirs()
                        Files.copy(file, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                }
                return FileVisitResult.CONTINUE
            }
        })
    }

    /** Deletes a file or directory tree without following symbolic links. A missing path is fine. */
    fun deleteTree(f: File) {
        val p = f.toPath()
        if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) return
        if (!Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) { Files.delete(p); return }
        Files.walkFileTree(p, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult { Files.delete(file); return FileVisitResult.CONTINUE }
            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult { Files.delete(dir); return FileVisitResult.CONTINUE }
        })
    }
}
