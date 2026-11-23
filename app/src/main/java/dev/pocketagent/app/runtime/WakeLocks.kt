package dev.pocketagent.app.runtime

import android.content.Context
import android.os.PowerManager
import dev.pocketagent.app.util.AppLog

/** Ref-counted partial wake lock with a safety timeout, so a bug can never pin the CPU awake forever. */
class WakeLocks(context: Context) {
    // Created on first use: constructing WakeLocks (at app start) then touches no system service, which also lets
    // the unit tests build a RuntimeManager without a PowerManager.
    private val lock: PowerManager.WakeLock by lazy {
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PocketAgent:work").apply { setReferenceCounted(true) }
    }

    fun acquire(reason: String, timeoutMs: Long = 15 * 60_000L) {
        try { lock.acquire(timeoutMs); AppLog.i("Wake", "acquire ($reason)") } catch (e: Exception) { AppLog.w("Wake", "acquire failed", e) }
    }

    fun release(reason: String) {
        try { if (lock.isHeld) lock.release(); AppLog.i("Wake", "release ($reason)") } catch (_: Exception) { }
    }

    val isHeld: Boolean get() = lock.isHeld
}
