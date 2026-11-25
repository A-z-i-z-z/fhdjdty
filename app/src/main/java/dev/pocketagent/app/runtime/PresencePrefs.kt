package dev.pocketagent.app.runtime

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How present Hermes is on the phone: when it wakes up on its own, what it tells you, when it rests. */
data class PresenceSettings(
    /** Light the fire when the app comes to the foreground (once per process, see `Presence`). */
    val autoStart: Boolean = true,
    /** Sending while Hermes is asleep starts it and delivers the message. */
    val wakeOnSend: Boolean = true,
    val notifyReplies: Boolean = true,
    val notifyApprovals: Boolean = true,
    /** One automatic restart after an unexpected exit. */
    val autoRestart: Boolean = true,
    /** 0 = never; otherwise 5..240 minutes without activity while the app is in the background. */
    val idleStopMinutes: Int = 0,
) {
    /** The same settings with an out-of-range idle limit repaired (anything below 5 minutes but above 0 becomes 5). */
    fun sanitized(): PresenceSettings {
        val m = when {
            idleStopMinutes <= 0 -> 0
            else -> idleStopMinutes.coerceIn(MIN_IDLE_MINUTES, MAX_IDLE_MINUTES)
        }
        return if (m == idleStopMinutes) this else copy(idleStopMinutes = m)
    }

    companion object {
        const val MIN_IDLE_MINUTES = 5
        const val MAX_IDLE_MINUTES = 240
    }
}

/** Persists [PresenceSettings] in SharedPreferences "presence" and exposes them as a StateFlow. */
class PresencePrefs private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val lock = Any()
    private val state = MutableStateFlow(load())
    val settings: StateFlow<PresenceSettings> = state.asStateFlow()

    fun update(change: (PresenceSettings) -> PresenceSettings) {
        synchronized(lock) {
            val next = change(state.value).sanitized()
            if (next == state.value) return
            state.value = next
            prefs.edit()
                .putBoolean(K_AUTO_START, next.autoStart)
                .putBoolean(K_WAKE_ON_SEND, next.wakeOnSend)
                .putBoolean(K_NOTIFY_REPLIES, next.notifyReplies)
                .putBoolean(K_NOTIFY_APPROVALS, next.notifyApprovals)
                .putBoolean(K_AUTO_RESTART, next.autoRestart)
                .putInt(K_IDLE_STOP, next.idleStopMinutes)
                .apply()
        }
    }

    private fun load(): PresenceSettings {
        val d = PresenceSettings()
        return try {
            PresenceSettings(
                autoStart = prefs.getBoolean(K_AUTO_START, d.autoStart),
                wakeOnSend = prefs.getBoolean(K_WAKE_ON_SEND, d.wakeOnSend),
                notifyReplies = prefs.getBoolean(K_NOTIFY_REPLIES, d.notifyReplies),
                notifyApprovals = prefs.getBoolean(K_NOTIFY_APPROVALS, d.notifyApprovals),
                autoRestart = prefs.getBoolean(K_AUTO_RESTART, d.autoRestart),
                idleStopMinutes = prefs.getInt(K_IDLE_STOP, d.idleStopMinutes),
            ).sanitized()
        } catch (_: Exception) {
            // A key holding a value of the wrong type (a hand-edited or restored file) must never stop the app from starting.
            d
        }
    }

    companion object {
        private const val PREFS = "presence"
        private const val K_AUTO_START = "autoStart"
        private const val K_WAKE_ON_SEND = "wakeOnSend"
        private const val K_NOTIFY_REPLIES = "notifyReplies"
        private const val K_NOTIFY_APPROVALS = "notifyApprovals"
        private const val K_AUTO_RESTART = "autoRestart"
        private const val K_IDLE_STOP = "idleStopMinutes"

        @Volatile private var instance: PresencePrefs? = null

        fun get(context: Context): PresencePrefs =
            instance ?: synchronized(this) {
                instance ?: PresencePrefs(context.applicationContext).also { instance = it }
            }
    }
}
