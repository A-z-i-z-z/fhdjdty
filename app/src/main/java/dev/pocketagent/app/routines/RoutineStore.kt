package dev.pocketagent.app.routines

import android.content.Context
import dev.pocketagent.app.data.BotPresets
import dev.pocketagent.app.data.Fs
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
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** JSON form of the routine list (`{"version":1,"routines":[...]}`). Tolerant on read, strict on write. */
internal object RoutineJson {
    const val VERSION = 1

    private fun JSONObject.str(name: String): String? = if (!has(name) || isNull(name)) null else optString(name)

    fun encode(list: List<Routine>): String {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        return JSONObject().put("version", VERSION).put("routines", arr).toString(2)
    }

    fun toJson(r: Routine): JSONObject = JSONObject()
        .put("id", r.id).put("name", r.name).put("prompt", r.prompt).put("botId", r.botId)
        .put("kind", r.kind.name).put("hour", r.hour).put("minute", r.minute).put("days", r.days)
        .put("intervalMinutes", r.intervalMinutes).put("onceAt", r.onceAt).put("enabled", r.enabled)
        .put("convId", r.convId).put("model", r.model).put("effort", r.effort)
        .put("lastRunAt", r.lastRunAt).put("lastStatus", r.lastStatus)
        .put("createdAt", r.createdAt).put("armedAt", r.armedAt)

    /** Throws (a JSONException) when [text] is not a routine document at all; single broken entries are dropped. */
    fun decode(text: String): List<Routine> {
        val root = JSONObject(text)
        val arr = root.optJSONArray("routines") ?: JSONArray()
        val out = ArrayList<Routine>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            fromJson(o)?.let { out += it }
        }
        return out.distinctBy { it.id }
    }

    fun fromJson(o: JSONObject): Routine? {
        val id = o.str("id")?.takeIf { it.isNotBlank() } ?: return null
        val kind = try { RoutineKind.valueOf(o.str("kind") ?: "") } catch (_: Exception) { RoutineKind.DAILY }
        return Routine(
            id = id,
            name = o.str("name") ?: "",
            prompt = o.str("prompt") ?: "",
            botId = o.str("botId")?.takeIf { it.isNotBlank() } ?: BotPresets.HERMES_ID,
            kind = kind,
            hour = if (o.has("hour")) o.optInt("hour", 8).coerceIn(0, 23) else 8,
            minute = if (o.has("minute")) o.optInt("minute", 0).coerceIn(0, 59) else 0,
            days = if (o.has("days")) o.optInt("days", RoutineSchedule.ALL_DAYS) and RoutineSchedule.ALL_DAYS else RoutineSchedule.ALL_DAYS,
            intervalMinutes = RoutineSchedule.clampInterval(if (o.has("intervalMinutes")) o.optInt("intervalMinutes", 60) else 60),
            onceAt = o.optLong("onceAt", 0L),
            enabled = if (o.has("enabled")) o.optBoolean("enabled", true) else true,
            convId = o.str("convId") ?: "",
            model = o.str("model") ?: "",
            effort = o.str("effort") ?: "",
            lastRunAt = o.optLong("lastRunAt", 0L),
            lastStatus = o.str("lastStatus") ?: "",
            createdAt = o.optLong("createdAt", 0L),
            armedAt = o.optLong("armedAt", 0L),
        )
    }
}

/**
 * The routines, kept in one small JSON file (`filesDir/chat/routines.json`, written atomically, a corrupt file is kept as
 * `.bad`). The file is read synchronously when the store is created (it is a few kilobytes), so a broadcast that wakes the
 * process can use the routines at once; writes are debounced and happen in [scope].
 *
 * [save] normalises the routine (clamped numbers, trimmed name) and arms it: a new routine, a routine that is switched
 * on, and a routine whose schedule changed start counting missed runs from now (see [Routine.armedAt]). A caller that sets
 * [Routine.armedAt] itself keeps its value.
 */
class RoutineStore internal constructor(
    private val file: File,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val debounceMs: Long = 300L,
) {
    constructor(context: Context) : this(
        File(File(context.filesDir, "chat"), "routines.json"),
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    private val lock = Any()
    private val ioLock = Any()
    private val _routines = MutableStateFlow<List<Routine>>(emptyList())
    @Volatile private var dirty = false
    @Volatile private var suspended = false
    @Volatile private var protectedFile = false
    private var pending: Job? = null   // guarded by lock

    /** All routines, oldest first. */
    val routines: StateFlow<List<Routine>> = _routines

    init {
        _routines.value = load()
    }

    private fun load(): List<Routine> {
        val text = try { Fs.readTextStrict(file) ?: return emptyList() } catch (e: java.io.IOException) {
            protectedFile = true
            AppLog.e("Routines", "routines file exists but cannot be read; leaving it untouched", e)
            return emptyList()
        }
        return try {
            RoutineJson.decode(text).sortedBy { it.createdAt }
        } catch (e: Exception) {
            AppLog.e("Routines", "routines file is corrupt (${e.javaClass.simpleName}); keeping a .bad copy and starting empty", e)
            try { Files.copy(file.toPath(), File(file.absoluteFile.parentFile, file.name + ".bad").toPath(), StandardCopyOption.REPLACE_EXISTING) } catch (_: Exception) { }
            emptyList()
        }
    }

    fun get(id: String): Routine? = _routines.value.firstOrNull { it.id == id }

    fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(16)

    /** Adds or replaces (by id). */
    fun save(r: Routine) {
        synchronized(lock) {
            val cur = _routines.value
            val prev = cur.firstOrNull { it.id == r.id }
            val n = normalise(prev, r)
            if (prev == n) return
            _routines.value = if (prev == null) cur + n else cur.map { if (it.id == r.id) n else it }
            dirty = true
        }
        schedule()
    }

    fun delete(id: String) {
        synchronized(lock) {
            val cur = _routines.value
            if (cur.none { it.id == id }) return
            _routines.value = cur.filter { it.id != id }
            dirty = true
        }
        schedule()
    }

    private fun normalise(prev: Routine?, r: Routine): Routine {
        val now = clock()
        var n = r.copy(
            name = r.name.trim(),
            prompt = r.prompt.trim(),
            botId = r.botId.ifBlank { BotPresets.HERMES_ID },
            hour = r.hour.coerceIn(0, 23),
            minute = r.minute.coerceIn(0, 59),
            days = r.days and RoutineSchedule.ALL_DAYS,
            intervalMinutes = RoutineSchedule.clampInterval(r.intervalMinutes),
            model = r.model.trim(),
            effort = r.effort.trim(),
        )
        if (prev == null) {
            if (n.armedAt <= 0L) n = n.copy(armedAt = now)
        } else {
            val rearm = (!prev.enabled && n.enabled) || prev.kind != n.kind || prev.hour != n.hour || prev.minute != n.minute ||
                prev.days != n.days || prev.intervalMinutes != n.intervalMinutes || prev.onceAt != n.onceAt
            if (rearm && n.armedAt == prev.armedAt) n = n.copy(armedAt = now)
        }
        return n
    }

    private fun schedule() {
        synchronized(lock) {
            if (pending != null || suspended) return
            pending = scope.launch {
                delay(debounceMs)
                synchronized(lock) { pending = null }
                try { flush() } catch (e: Throwable) { AppLog.e("Routines", "background write failed", e) }
            }
        }
    }

    /** Writes the routines now when something changed. Blocking: call off the main thread. */
    fun flush() {
        synchronized(ioLock) {
            if (suspended || !dirty) return
            if (protectedFile) { AppLog.w("Routines", "not writing ${file.name}: it could not be read earlier"); return }
            val snapshot = synchronized(lock) { dirty = false; _routines.value }
            try { Fs.writeAtomic(file, RoutineJson.encode(snapshot)) }
            catch (e: Exception) { dirty = true; AppLog.e("Routines", "could not write ${file.name}", e) }
        }
    }

    /** After this nothing is written any more (used right before a backup is restored over the files). */
    fun suspendWrites() {
        suspended = true
        synchronized(lock) { pending?.cancel(); pending = null }
    }
}
