package dev.pocketagent.app.data

/**
 * The timing policy of the engine's writes while an answer streams (Wave 0, STREAM): a write at once when the last one is at
 * least [intervalMs] old (the leading edge, as before), and exactly one trailing write [trailingMs] after the last change when
 * the changes stop inside the interval, so the last piece of text never waits for the next one.
 *
 * Pure: time is a parameter and nothing here sleeps. Not thread-safe: the caller (the engine's `Turn`) holds its lock around
 * every call.
 */
internal class StreamPush(val intervalMs: Long = 60L, val trailingMs: Long = 60L) {
    enum class Action { PUSH, ARM, NONE }

    private var lastPushAt = Long.MIN_VALUE / 4
    private var lastChangeAt = Long.MIN_VALUE / 4
    private var pending = false
    private var timerArmed = false

    /**
     * A piece of text or reasoning arrived at [nowMs]. PUSH: write now (>= intervalMs since the last write). ARM: remember it and
     * start the one trailing timer (none was running). NONE: remembered, the running timer will see it.
     */
    fun onChange(nowMs: Long): Action {
        lastChangeAt = nowMs
        if (nowMs - lastPushAt >= intervalMs) {
            lastPushAt = nowMs
            pending = false
            return Action.PUSH
        }
        pending = true
        if (timerArmed) return Action.NONE
        timerArmed = true
        return Action.ARM
    }

    /**
     * The trailing timer woke at [nowMs]: -1 = nothing pending (the timer ends); 0 = write now (the timer ends); n > 0 = the last
     * change was too recent, sleep n ms and call again (n = lastChange + trailingMs - nowMs).
     */
    fun onTimer(nowMs: Long): Long {
        if (!pending) {
            timerArmed = false
            return -1L
        }
        val due = lastChangeAt + trailingMs
        if (nowMs < due) return due - nowMs
        pending = false
        lastPushAt = nowMs
        timerArmed = false
        return 0L
    }

    /** A write happened outside the policy (a forced push): nothing is pending any more. */
    fun onForced(nowMs: Long) {
        lastPushAt = nowMs
        pending = false
    }
}
