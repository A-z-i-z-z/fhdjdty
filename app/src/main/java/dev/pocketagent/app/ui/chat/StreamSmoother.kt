package dev.pocketagent.app.ui.chat

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Decides, frame by frame, how much of a streamed answer is shown (Wave 0, STREAM). The network delivers text in
 * bursts; drawing every burst at once makes the answer jump. The smoother releases characters at a rate that follows
 * the backlog (about a third of a second behind the newest text, never slower than [minCharsPerSecond]) and flushes
 * everything within [flushMs] once the answer is finished.
 *
 * Pure Kotlin: no Android, no Compose; time is a parameter. Not thread-safe (main thread).
 */
class StreamSmoother(
    val backlogSeconds: Double = 0.35,
    val minCharsPerSecond: Double = 40.0,
    val flushMs: Long = 150L,
    val maxStepSeconds: Double = 0.1,
    val maxMarkerRun: Int = 8,
) {
    /** Characters released so far; always a safe cut of the last target seen, 0..target.length. */
    var shown: Int = 0
        private set

    private var carry = 0.0
    private var lastNanos = UNSET
    private var finishedAt = UNSET

    /** Starts over at [shown] characters; also clears the fractional carry, the last frame time and the finish time. */
    fun reset(shown: Int = 0) {
        this.shown = shown.coerceAtLeast(0)
        carry = 0.0
        lastNanos = UNSET
        finishedAt = UNSET
    }

    /** True while something of a target of [targetLength] characters is not shown yet. */
    fun hasBacklog(targetLength: Int): Boolean = shown != targetLength

    /**
     * One frame. Returns the new [shown]. [target] only ever grows, except that the engine may shorten it once at the end
     * (then [shown] is clamped).
     */
    fun step(nowNanos: Long, target: CharSequence, finished: Boolean): Int {
        val len = target.length
        if (shown > len) {
            shown = len
            carry = 0.0
        }
        if (finished) {
            if (finishedAt == UNSET) finishedAt = nowNanos
        } else {
            finishedAt = UNSET
        }
        val dt = if (lastNanos == UNSET) 0.0 else ((nowNanos - lastNanos) / 1e9).coerceIn(0.0, maxStepSeconds)
        lastNanos = nowNanos
        val backlog = len - shown
        if (backlog <= 0) {
            // Idle: the first frame after a pause releases nothing.
            carry = 0.0
            lastNanos = UNSET
            return shown
        }
        var remainingFlushSeconds = 0.0
        if (finished) {
            val sinceFinish = nowNanos - finishedAt
            if (sinceFinish >= flushMs * 1_000_000L) {
                shown = len
                carry = 0.0
                return shown
            }
            remainingFlushSeconds = flushMs / 1000.0 - sinceFinish / 1e9
        }
        var speed = max(backlog / backlogSeconds, minCharsPerSecond)
        if (finished) speed = max(speed, backlog / max(remainingFlushSeconds, 0.016))
        carry += speed * dt
        val whole = floor(carry).toInt()
        if (whole > 0) {
            carry -= whole
            shown = safeCut(target, min(shown + whole, len), finished, maxMarkerRun)
        }
        return shown
    }

    companion object {
        private const val UNSET = Long.MIN_VALUE

        /** A row that is already older than [replayWindowMs] when first drawn shows everything at once; a fresh one starts at 0. */
        fun initialShown(textLength: Int, ageMs: Long, replayWindowMs: Long = 1_500L): Int =
            if (ageMs > replayWindowMs) textLength.coerceAtLeast(0) else 0

        /**
         * Nearest allowed cut at or after [proposed]: not between a high and a low surrogate; unless [finished], not inside a
         * run of the same marker character from the emphasis markers and the backtick (extended to the run's end, at most
         * [maxMarkerRun] more), and not inside a run of the heading character that starts a line. Never above target.length.
         */
        fun safeCut(target: CharSequence, proposed: Int, finished: Boolean, maxMarkerRun: Int = 8): Int {
            val len = target.length
            var cut = proposed.coerceIn(0, len)
            if (cut in 1 until len && target[cut - 1].isHighSurrogate() && target[cut].isLowSurrogate()) cut++
            if (finished) return cut
            if (cut in 1 until len) {
                val before = target[cut - 1]
                if (before == target[cut] && isMarker(before)) {
                    var extra = 0
                    while (cut < len && target[cut] == before && extra < maxMarkerRun) {
                        cut++
                        extra++
                    }
                } else if (before == '#' && target[cut] == '#' && startsLine(target, cut - 1)) {
                    var extra = 0
                    while (cut < len && target[cut] == '#' && extra < maxMarkerRun) {
                        cut++
                        extra++
                    }
                }
            }
            return cut
        }

        private fun isMarker(ch: Char): Boolean = ch == '*' || ch == '_' || ch == '~' || ch == '`'

        /** True when the run of '#' that contains [index] begins a line (or the text). */
        private fun startsLine(target: CharSequence, index: Int): Boolean {
            var s = index
            while (s > 0 && target[s - 1] == '#') s--
            return s == 0 || target[s - 1] == '\n'
        }

        /** Alpha of the k-th character from the end, k = 0 the newest. */
        val FADE_ALPHAS: FloatArray = floatArrayOf(0.15f, 0.25f, 0.35f, 0.5f, 0.65f, 0.8f, 0.9f, 0.97f)
    }
}
