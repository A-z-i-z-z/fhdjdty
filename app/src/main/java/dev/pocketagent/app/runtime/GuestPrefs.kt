package dev.pocketagent.app.runtime

import android.content.Context
import android.content.SharedPreferences
import dev.pocketagent.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the guest's generated configuration depends on besides the provider setup. [practicePort] non-null means practice mode:
 * the scripted model listens on that loopback port and nothing of the owner's provider setup is read or written.
 */
data class GuestOptions(
    /** Written as `web.keyless_fallback`. */
    val webKeylessFallback: Boolean = true,
    val practicePort: Int? = null,
) {
    val practice: Boolean get() = practicePort != null
}

/**
 * The two switches that change what Hermes is allowed to do or whom it talks to: free web lookups (default on, as before) and the
 * practice model (default off, zero tokens). SharedPreferences `guest`; it is not in the backup schema, so neither switch travels
 * in a backup.
 *
 * The practice flag is a testing aid and must not be left on by accident: it stores the time it was switched on and, on every build
 * that is not the twin, it is reset at every cold process start (this constructor) and expires after [PRACTICE_MAX_MS]
 * ([enforceExpiry]). The twin keeps it until it is switched off.
 */
class GuestPrefs internal constructor(
    private val sp: SharedPreferences,
    private val isTwin: Boolean = BuildConfig.IS_TWIN,
    now: Long = System.currentTimeMillis(),
) {
    private val lock = Any()

    private val _webKeyless = MutableStateFlow(readBool(K_WEB_KEYLESS, true))
    /** Free web lookups through public services (Hermes `web.keyless_fallback`). Default on. */
    val webKeyless: StateFlow<Boolean> = _webKeyless.asStateFlow()

    private val _practice = MutableStateFlow(false)
    /** The scripted practice model instead of the real provider. Default off. */
    val practiceModel: StateFlow<Boolean> = _practice.asStateFlow()

    init {
        // A cold process start never keeps practice mode on, except on the twin (a test build that is not the owner's daily app).
        var on = readBool(K_PRACTICE, false)
        if (on && !isTwin) {
            on = false
            try { sp.edit().putBoolean(K_PRACTICE, false).remove(K_PRACTICE_SINCE).apply() } catch (_: Exception) { }
        }
        _practice.value = on
        practiceActive = on
        // `now` is only used by tests that want a fixed clock for the stored time stamp below.
        if (on && !sp.contains(K_PRACTICE_SINCE)) try { sp.edit().putLong(K_PRACTICE_SINCE, now).apply() } catch (_: Exception) { }
    }

    private fun readBool(key: String, default: Boolean): Boolean =
        try { sp.getBoolean(key, default) } catch (_: Exception) { default }   // a value of the wrong type (hand-edited file) reads as the default

    private fun readLong(key: String, default: Long): Long =
        try { sp.getLong(key, default) } catch (_: Exception) { default }

    /** Persists at once and updates the flow; does NOT touch Hermes (the caller decides about a restart). */
    fun setWebKeyless(on: Boolean) {
        synchronized(lock) {
            _webKeyless.value = on
            try { sp.edit().putBoolean(K_WEB_KEYLESS, on).apply() } catch (_: Exception) { }
        }
    }

    /**
     * Switches practice mode. Switching it on stores the time (epoch ms) for the expiry. Persists at once and updates the flow and
     * [practiceActive]; the caller then asks the runtime to apply it (`RuntimeManager.onGuestOptionsChanged(true)`).
     */
    fun setPracticeModel(on: Boolean) {
        synchronized(lock) {
            _practice.value = on
            practiceActive = on
            try {
                val e = sp.edit().putBoolean(K_PRACTICE, on)
                if (on) e.putLong(K_PRACTICE_SINCE, System.currentTimeMillis()) else e.remove(K_PRACTICE_SINCE)
                e.apply()
            } catch (_: Exception) { }
        }
    }

    /**
     * Turns practice mode off when it has run longer than [PRACTICE_MAX_MS] (never on the twin). A stored flag without a time stamp
     * counts as expired on a build that is not the twin. Called by [get], by every Hermes launch before it reads the options and by
     * the runtime's options-changed hook, so an expired flag never survives into the next launch.
     */
    fun enforceExpiry(now: Long = System.currentTimeMillis(), isTwin: Boolean = this.isTwin) {
        synchronized(lock) {
            if (!_practice.value) return
            val since = readLong(K_PRACTICE_SINCE, 0L)
            if (!expired(since, now, isTwin)) return
            _practice.value = false
            practiceActive = false
            try { sp.edit().putBoolean(K_PRACTICE, false).remove(K_PRACTICE_SINCE).apply() } catch (_: Exception) { }
        }
    }

    /** Whole minutes (rounded up, 0..120) before practice mode switches itself off; null on the twin or while it is off. */
    fun practiceMinutesLeft(now: Long = System.currentTimeMillis()): Int? {
        if (isTwin || !_practice.value) return null
        val since = readLong(K_PRACTICE_SINCE, 0L)
        val left = PRACTICE_MAX_MS - (now - since)
        if (left <= 0L) return 0
        return ((left + 59_999L) / 60_000L).toInt().coerceIn(0, (PRACTICE_MAX_MS / 60_000L).toInt())
    }

    companion object {
        private const val PREFS = "guest"
        private const val K_WEB_KEYLESS = "web_keyless"
        private const val K_PRACTICE = "practice"
        private const val K_PRACTICE_SINCE = "practice_since"

        /** Practice mode switches itself off after two hours on every build except the twin. */
        const val PRACTICE_MAX_MS = 7_200_000L

        /**
         * Mirrors the practice flag for code that has no Context (the engine's provider lambda, [ProviderConfig.savedProblems]). Set
         * in the constructor from the stored value (after the cold-start reset) and in [setPracticeModel].
         */
        @Volatile var practiceActive: Boolean = false

        @Volatile private var instance: GuestPrefs? = null

        /** The process singleton (like [PresencePrefs.get]); every call enforces the expiry first. */
        fun get(context: Context): GuestPrefs {
            val g = instance ?: synchronized(this) {
                instance ?: GuestPrefs(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)).also { instance = it }
            }
            g.enforceExpiry()
            return g
        }

        /** Pure: true when a practice flag switched on at [sinceMs] is too old at [nowMs]. Never on the twin. */
        fun expired(sinceMs: Long, nowMs: Long, isTwin: Boolean): Boolean = !isTwin && nowMs - sinceMs > PRACTICE_MAX_MS
    }
}
