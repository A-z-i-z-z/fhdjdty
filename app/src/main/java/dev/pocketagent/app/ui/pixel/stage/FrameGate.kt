package dev.pocketagent.app.ui.pixel.stage

/**
 * Lets at most [maxFps] frames per second through, tied to the display's own frame times: on a 60 Hz
 * screen every 2nd frame passes for 30, on 90 Hz every 3rd, on 120 Hz every 4th. [slackNanos] forgives a
 * frame that arrives a little early (jitter), so a 30 fps cadence does not slip to 20. Not thread-safe.
 */
class FrameGate(val maxFps: Int = 30, val slackNanos: Long = 2_000_000L) {
    init {
        require(maxFps in 1..240) { "maxFps must be 1..240, got $maxFps" }
    }

    private val minGapNanos: Long = 1_000_000_000L / maxFps - slackNanos
    private var started = false
    private var lastAccepted = 0L

    /** True for the first call; then true when enough time has passed since the last accepted frame. */
    fun accept(frameTimeNanos: Long): Boolean {
        if (!started || frameTimeNanos - lastAccepted >= minGapNanos) {
            started = true
            lastAccepted = frameTimeNanos
            return true
        }
        return false
    }

    /** Forget the last accepted frame: the next call is accepted again. */
    fun reset() {
        started = false
    }
}
