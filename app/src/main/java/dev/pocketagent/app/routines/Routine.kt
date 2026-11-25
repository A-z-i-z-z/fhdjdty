package dev.pocketagent.app.routines

import dev.pocketagent.app.data.BotPresets

/** How a routine repeats: every selected weekday at a clock time, every N minutes, or one single time. */
enum class RoutineKind { DAILY, INTERVAL, ONCE }

/**
 * A scheduled prompt. At the scheduled time the app sends [prompt] to [botId] in the routine's own chat ([convId]),
 * waking Hermes if it sleeps; the answer reaches the user as a notification.
 *
 * [days] is a bit mask for DAILY: bit 0 = Monday ... bit 6 = Sunday. [intervalMinutes] is 15..10080 for INTERVAL.
 * [onceAt] is wall-clock millis for ONCE. [model] / [effort] "" mean the bot's / Hermes's default.
 * [lastStatus] is "", "ok", "failed" or "skipped".
 *
 * [armedAt] is the moment from which missed runs count (set when the routine is created, switched on or its schedule is
 * edited), so that a new or re-enabled routine never "catches up" for a slot that passed before it existed.
 */
data class Routine(
    val id: String,
    val name: String,
    val prompt: String,
    val botId: String = BotPresets.HERMES_ID,
    val kind: RoutineKind = RoutineKind.DAILY,
    val hour: Int = 8,
    val minute: Int = 0,
    val days: Int = 0x7F,
    val intervalMinutes: Int = 60,
    val onceAt: Long = 0L,
    val enabled: Boolean = true,
    val convId: String = "",
    val model: String = "",
    val effort: String = "",
    val lastRunAt: Long = 0L,
    val lastStatus: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val armedAt: Long = 0L,
) {
    companion object {
        const val STATUS_OK = "ok"
        const val STATUS_FAILED = "failed"
        const val STATUS_SKIPPED = "skipped"
    }
}
