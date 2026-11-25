package dev.pocketagent.app.data

import android.content.Context
import dev.pocketagent.app.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Unsent message text per conversation, so a half-typed message survives leaving the chat and the app process dying.
 * One small JSON file (`filesDir/chat/drafts.json`), written atomically and debounced; read when the store is created.
 * A blank draft is the same as no draft. Safe to call from any thread.
 */
class DraftStore internal constructor(
    private val file: File,
    private val scope: CoroutineScope,
    private val debounceMs: Long = 600L,
) {
    constructor(context: Context) : this(File(context.filesDir, "chat/drafts.json"), CoroutineScope(SupervisorJob() + Dispatchers.IO))

    private val lock = Any()
    private val ioLock = Any()
    private val _drafts = MutableStateFlow<Map<String, String>>(emptyMap())
    private var dirty = false                   // guarded by lock
    private var pending: Job? = null            // guarded by lock
    @Volatile private var writesSuspended = false

    /** convId -> unsent text (blank drafts are removed). */
    val drafts: StateFlow<Map<String, String>> = _drafts

    init { load() }

    /** The unsent text of [convId], "" when there is none. */
    operator fun get(convId: String): String = _drafts.value[convId] ?: ""

    /** Stores [text] as the draft of [convId]; blank text removes the draft. */
    operator fun set(convId: String, text: String) {
        synchronized(lock) {
            val cur = _drafts.value
            if (text.isBlank()) {
                if (!cur.containsKey(convId)) return
                _drafts.value = cur - convId
            } else {
                val t = if (text.length > MAX_CHARS) text.substring(0, MAX_CHARS) else text
                if (cur[convId] == t) return
                _drafts.value = cur + (convId to t)
            }
            dirty = true
        }
        schedule()
    }

    /** Forgets the drafts of conversations that no longer exist. Call only after the chat repository has loaded. */
    fun prune(validConvIds: Set<String>) {
        synchronized(lock) {
            val cur = _drafts.value
            val next = cur.filterKeys { it in validConvIds }
            if (next.size == cur.size) return
            _drafts.value = next
            dirty = true
        }
        schedule()
    }

    /** Writes pending changes now. Blocking: call off the main thread. */
    fun flush() {
        if (writesSuspended) return
        synchronized(ioLock) {
            if (writesSuspended) return
            val text = synchronized(lock) {
                if (!dirty) return
                dirty = false
                encode(_drafts.value)
            }
            try { Fs.writeAtomic(file, text) } catch (e: Exception) {
                synchronized(lock) { dirty = true }
                AppLog.e("Drafts", "could not write ${file.name}", e)
            }
        }
    }

    /** After this call nothing is written any more and pending writes are dropped (used right before a restore). */
    fun suspendWrites() {
        writesSuspended = true
        synchronized(lock) { pending?.cancel(); pending = null }
        synchronized(ioLock) { }   // waits for a write already running; later flushes see the flag inside ioLock
    }

    private fun schedule() {
        if (writesSuspended) return
        synchronized(lock) {
            if (pending != null) return
            pending = scope.launch {
                delay(debounceMs)
                synchronized(lock) { pending = null }
                try { flush() } catch (e: Throwable) { AppLog.e("Drafts", "background write failed", e) }
            }
        }
    }

    private fun load() {
        val text = try { Fs.readTextStrict(file) ?: return } catch (e: java.io.IOException) {
            writesSuspended = true   // exists but cannot be read: never write over it this run
            AppLog.e("Drafts", "${file.name} exists but cannot be read; leaving it untouched", e)
            return
        }
        try {
            val o = JSONObject(text).getJSONObject("drafts")
            val map = LinkedHashMap<String, String>()
            for (k in o.keys()) {
                if (k.isBlank() || o.isNull(k)) continue
                val v = o.optString(k, "")
                if (v.isNotBlank()) map[k] = v
            }
            synchronized(lock) { _drafts.value = map }
        } catch (e: Exception) {
            AppLog.e("Drafts", "${file.name} is corrupt (${e.javaClass.simpleName}); keeping a .bad copy and starting empty", e)
            try { Files.copy(file.toPath(), File(file.absoluteFile.parentFile, file.name + ".bad").toPath(), StandardCopyOption.REPLACE_EXISTING) } catch (_: Exception) { }
        }
    }

    private fun encode(map: Map<String, String>): String =
        JSONObject().put("v", 1).put("drafts", JSONObject().also { o -> for ((k, v) in map) o.put(k, v) }).toString(1)

    companion object {
        /** A draft is cut at this length (a safety bound for the file, far beyond anything typed). */
        const val MAX_CHARS = 100_000
    }
}
