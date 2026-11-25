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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneId

/** One row of the statistics: everything that was spent on [model] ("" = Hermes's own default) on [day] (`yyyy-MM-dd`, device time zone). */
data class UsageDay(val day: String, val model: String, val turns: Int, val tokensIn: Long, val tokensOut: Long)

enum class BudgetMode { OFF, WARN, BLOCK }

/** A daily token limit over all models; [dailyTokens] 0 means no limit. */
data class UsageBudget(val dailyTokens: Long = 0L, val mode: BudgetMode = BudgetMode.OFF)

/**
 * Token statistics per day and model plus the daily budget, kept in one small JSON file (`filesDir/chat/usage.json`,
 * atomic writes, debounced). The file is read when the store is created (it is tiny: at most 400 days are kept). A file that
 * cannot be parsed is copied to `.bad` and the store starts empty. Safe to call from any thread.
 */
class UsageStore internal constructor(
    private val file: File,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val debounceMs: Long = 800L,
) {
    constructor(context: Context) : this(File(context.filesDir, "chat/usage.json"), CoroutineScope(SupervisorJob() + Dispatchers.IO))

    private val lock = Any()
    private val ioLock = Any()
    private val _days = MutableStateFlow<List<UsageDay>>(emptyList())
    private val _budget = MutableStateFlow(UsageBudget())
    private var dirty = false                   // guarded by lock
    private var pending: Job? = null            // guarded by lock
    @Volatile private var writesSuspended = false

    /** Newest day first (rows of one day: most tokens first). At most [MAX_DAYS] days are kept. */
    val days: StateFlow<List<UsageDay>> = _days
    val budget: StateFlow<UsageBudget> = _budget

    init { load() }

    // ---- public API ---------------------------------------------------------------------------------------

    /** Adds one turn to today's row for [model] ("" = Hermes default). A turn without any token is not recorded. */
    fun record(model: String, tokensIn: Long, tokensOut: Long) {
        val tin = tokensIn.coerceAtLeast(0L)
        val tout = tokensOut.coerceAtLeast(0L)
        if (tin == 0L && tout == 0L) return
        val today = dayOf(clock())
        val m = model.trim()
        synchronized(lock) {
            val cur = _days.value
            val i = cur.indexOfFirst { it.day == today && it.model == m }
            val next = if (i >= 0) cur.toMutableList().also {
                val r = it[i]
                it[i] = r.copy(turns = r.turns + 1, tokensIn = r.tokensIn + tin, tokensOut = r.tokensOut + tout)
            } else cur + UsageDay(today, m, 1, tin, tout)
            _days.value = normalize(next)
            dirty = true
        }
        schedule()
    }

    fun setBudget(b: UsageBudget) {
        val clean = UsageBudget(b.dailyTokens.coerceAtLeast(0L), b.mode)
        synchronized(lock) {
            if (_budget.value == clean) return
            _budget.value = clean
            dirty = true
        }
        schedule()
    }

    /** tokensIn + tokensOut of today, all models. */
    fun todayTotal(): Long {
        val today = dayOf(clock())
        return _days.value.sumOf { if (it.day == today) it.tokensIn + it.tokensOut else 0L }
    }

    /** True when a limit is set and active (mode is not OFF) and today's total has reached it. */
    fun overBudget(): Boolean {
        val b = _budget.value
        return b.mode != BudgetMode.OFF && b.dailyTokens > 0L && todayTotal() >= b.dailyTokens
    }

    /** Deletes all statistics. The budget stays. */
    fun clear() {
        synchronized(lock) {
            if (_days.value.isEmpty()) return
            _days.value = emptyList()
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
                encode(_days.value, _budget.value)
            }
            try { Fs.writeAtomic(file, text) } catch (e: Exception) {
                synchronized(lock) { dirty = true }
                AppLog.e("Usage", "could not write ${file.name}", e)
            }
        }
    }

    /** After this call nothing is written any more and pending writes are dropped (used right before a restore). */
    fun suspendWrites() {
        writesSuspended = true
        synchronized(lock) { pending?.cancel(); pending = null }
        synchronized(ioLock) { }   // waits for a write already running; later flushes see the flag inside ioLock
    }

    // ---- persistence ------------------------------------------------------------------------------------------

    private fun schedule() {
        if (writesSuspended) return
        synchronized(lock) {
            if (pending != null) return
            pending = scope.launch {
                delay(debounceMs)
                synchronized(lock) { pending = null }
                try { flush() } catch (e: Throwable) { AppLog.e("Usage", "background write failed", e) }
            }
        }
    }

    private fun load() {
        val text = try { Fs.readTextStrict(file) ?: return } catch (e: java.io.IOException) {
            // Exists but cannot be read: never write over it this run.
            writesSuspended = true
            AppLog.e("Usage", "${file.name} exists but cannot be read; leaving it untouched", e)
            return
        }
        try {
            val o = JSONObject(text)
            val rows = ArrayList<UsageDay>()
            val arr = o.optJSONArray("days")
            if (arr != null) for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                val day = r.optString("day", "")
                if (!DAY_RE.matches(day)) continue
                rows += UsageDay(
                    day = day,
                    model = if (r.isNull("model")) "" else r.optString("model", ""),
                    turns = r.optInt("turns", 0).coerceAtLeast(0),
                    tokensIn = r.optLong("in", 0L).coerceAtLeast(0L),
                    tokensOut = r.optLong("out", 0L).coerceAtLeast(0L),
                )
            }
            val b = o.optJSONObject("budget")
            val budget = if (b == null) UsageBudget() else UsageBudget(
                dailyTokens = b.optLong("dailyTokens", 0L).coerceAtLeast(0L),
                mode = try { BudgetMode.valueOf(b.optString("mode", "OFF")) } catch (_: Exception) { BudgetMode.OFF },
            )
            synchronized(lock) {
                _days.value = normalize(rows)
                _budget.value = budget
            }
        } catch (e: Exception) {
            AppLog.e("Usage", "${file.name} is corrupt (${e.javaClass.simpleName}); keeping a .bad copy and starting empty", e)
            try { Files.copy(file.toPath(), File(file.absoluteFile.parentFile, file.name + ".bad").toPath(), StandardCopyOption.REPLACE_EXISTING) } catch (_: Exception) { }
        }
    }

    companion object {
        const val MAX_DAYS = 400
        private val DAY_RE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

        /** `yyyy-MM-dd` of [millis] in [zone] (the device time zone by default). */
        fun dayOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
            Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toString()

        /** Merges rows with the same day and model, orders them newest day first and keeps only the newest [MAX_DAYS] days. */
        internal fun normalize(rows: List<UsageDay>): List<UsageDay> {
            val merged = LinkedHashMap<Pair<String, String>, UsageDay>()
            for (r in rows) {
                val k = r.day to r.model
                val e = merged[k]
                merged[k] = if (e == null) r else e.copy(turns = e.turns + r.turns, tokensIn = e.tokensIn + r.tokensIn, tokensOut = e.tokensOut + r.tokensOut)
            }
            val keepDays = merged.values.map { it.day }.distinct().sortedDescending().take(MAX_DAYS).toSet()
            return merged.values.filter { it.day in keepDays }
                .sortedWith(compareByDescending<UsageDay> { it.day }.thenByDescending { it.tokensIn + it.tokensOut }.thenBy { it.model })
        }

        internal fun encode(days: List<UsageDay>, budget: UsageBudget): String = JSONObject()
            .put("v", 1)
            .put("budget", JSONObject().put("dailyTokens", budget.dailyTokens).put("mode", budget.mode.name))
            .put("days", JSONArray().also { a ->
                for (d in days) a.put(JSONObject().put("day", d.day).put("model", d.model).put("turns", d.turns).put("in", d.tokensIn).put("out", d.tokensOut))
            })
            .toString(1)
    }
}
