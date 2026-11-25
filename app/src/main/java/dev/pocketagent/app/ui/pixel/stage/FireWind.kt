package dev.pocketagent.app.ui.pixel.stage

import kotlin.math.abs
import kotlin.math.floor

/**
 * Wind on the fire, in one place. Wind is -1..1 and positive blows towards the right of the screen. The
 * flame leans (its upper rows shear sideways, the tip most), the sparks drift.
 */
object FireWind {
    /**
     * How many pixels row [row] of the flame sprite moves sideways at [wind]: 0 outside rows 0..22 and for a
     * wind under 0.1, else `sign(wind) * round(|wind| * 3 * (22 - row) / 22)` with halves rounded up (never
     * `roundToInt` of a negative number: it would round -1.5 to -1). Wind 1: row 0 gives 3, row 11 gives 2,
     * row 22 gives 0.
     */
    fun shift(row: Int, wind: Float): Int {
        if (row < 0 || row > FireTuning.SHEAR_ROWS) return 0
        if (wind.isNaN()) return 0
        val a = abs(wind).coerceAtMost(1f)
        if (a < 0.1f) return 0
        val cells = floor(a * FireTuning.SHEAR_MAX_PX * (FireTuning.SHEAR_ROWS - row) / FireTuning.SHEAR_ROWS + 0.5f).toInt()
        return if (wind < 0f) -cells else cells
    }

    /** The wind of the weather plus the wind of the tilt, clamped to -1..1; a NaN counts as 0. */
    fun total(weatherWind: Float, tiltWind: Float): Float {
        val a = if (weatherWind.isNaN()) 0f else weatherWind
        val b = if (tiltWind.isNaN()) 0f else tiltWind
        return (a + b).coerceIn(-1f, 1f)
    }

    /** How far a spark of age [u] (0..1 of its life) with [gain] has drifted sideways, in cells; linear in age. */
    fun sparkDrift(wind: Float, u: Float, gain: Float): Float = wind * FireTuning.SPARK_WIND_CELLS * gain * u
}

/** Tilting the phone as wind. The accelerometer reads the sideways acceleration in m/s squared. */
object TiltWind {
    /** -1: the flame leans to the raised edge of the phone, like a real flame. Flip it to change the sense. */
    const val SIGN = -1f

    /** Below this (m/s squared) a tilt is not a tilt. */
    const val DEAD_ZONE = 1.0f

    /** A tilt this far beyond the dead zone is the full wind. */
    const val FULL_RANGE = 3.5f

    /** `SIGN * sign(ax) * clamp((|ax| - 1.0) / 3.5, 0, 1)`: 0 in the dead zone, 1 from 4.5 m/s squared. */
    fun fromAccelX(ax: Float): Float {
        if (ax.isNaN()) return 0f
        val mag = ((abs(ax) - DEAD_ZONE) / FULL_RANGE).coerceIn(0f, 1f)
        if (mag == 0f) return 0f
        return if (ax > 0f) SIGN * mag else -SIGN * mag
    }

    /** One step of a low pass: `alpha = dt / (tau + dt)`. A [dtMs] of 0 or less changes nothing. */
    fun smooth(prev: Float, sample: Float, dtMs: Long, tauMs: Long = 250L): Float {
        if (dtMs <= 0L) return prev
        val alpha = dtMs.toFloat() / (tauMs + dtMs).toFloat()
        return prev + alpha * (sample - prev)
    }
}

/** What the tilt sensor needs from the platform: the Android side implements it, the tests fake it. */
interface TiltApi {
    /** Starts reporting (acceleration x in m/s squared, time in ms); false when there is no sensor. */
    fun register(onAccelX: (Float, Long) -> Unit): Boolean

    fun unregister()
}

/** When the tilt sensor may run: the screen of the fire is on top and awake, and every setting agrees. */
object FireTiltPolicy {
    /**
     * True only when Home is on top, the lifecycle is started, the animation level is FULL, the fire is not
     * classic, the tilt switch is on and the phone has a sensor. Every other combination is false.
     */
    fun shouldRun(
        homeOnTop: Boolean, lifecycleStarted: Boolean, fullAnimation: Boolean,
        classic: Boolean, tiltEnabled: Boolean, hasSensor: Boolean,
    ): Boolean = homeOnTop && lifecycleStarted && fullAnimation && !classic && tiltEnabled && hasSensor
}

/**
 * Registers with [api] when told to run and unregisters when told to stop, and turns the samples into a
 * smoothed [wind]. [setRunning] says whether the registration changed (the driver writes the log line).
 * Samples arrive on the sensor's thread, so [wind] and [registered] are volatile.
 */
class FireTiltController(private val api: TiltApi) {
    @Volatile var registered: Boolean = false
        private set

    @Volatile var wind: Float = 0f

    @Volatile private var lastSampleMs: Long = NO_SAMPLE

    /** Returns true when the registration changed; asking for what already is changes nothing. */
    fun setRunning(run: Boolean): Boolean {
        if (run == registered) return false
        if (run) {
            lastSampleMs = NO_SAMPLE
            wind = 0f
            registered = true
            if (!api.register { ax, tMs -> onSample(ax, tMs) }) {
                registered = false
                return false
            }
        } else {
            api.unregister()
            registered = false
            wind = 0f
            lastSampleMs = NO_SAMPLE
        }
        return true
    }

    private fun onSample(ax: Float, tMs: Long) {
        if (!registered) return
        val target = TiltWind.fromAccelX(ax)
        val last = lastSampleMs
        wind = if (last == NO_SAMPLE) target else TiltWind.smooth(wind, target, (tMs - last).coerceIn(1L, 1_000L))
        lastSampleMs = tMs
        // A late sample that raced with setRunning(false) must not leave wind behind after the sensor stopped.
        if (!registered) wind = 0f
    }

    private companion object {
        const val NO_SAMPLE = Long.MIN_VALUE
    }
}
