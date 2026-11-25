package dev.pocketagent.app.service

import dev.pocketagent.app.BuildConfig
import dev.pocketagent.app.ui.pixel.stage.FireTransition
import dev.pocketagent.app.ui.pixel.stage.FireTuning

/**
 * What the debug link pins on the fire (in memory only, test builds only). Every field is optional; null
 * leaves that part of the fire alone.
 *
 * Grammar of the `fire` link extra ([parse]): `off`, or items joined by commas:
 * `level:0..1` (also pins the feed), `wind:-1..1`, `classic`, `living`, `hold:0..1`, `light@0..1` and
 * `douse@0..1` (a transition frozen at that fraction of its length), `light` and `douse` (start one now,
 * taken once by the driver), `haze:on|off`. One bad item rejects the whole text; a repeated item: the last wins.
 */
data class FireDebugPin(
    val level: Float? = null,
    val wind: Float? = null,
    val classic: Boolean? = null,
    val hold: Float? = null,
    val transition: FireTransition? = null,
    val start: FireTransition.Kind? = null,
    val haze: Boolean? = null,
) {
    /** True when nothing is pinned: what `off` parses to. */
    val isEmpty: Boolean
        get() = level == null && wind == null && classic == null && hold == null &&
            transition == null && start == null && haze == null

    companion object {
        /** The pin for [text], or null when the text is bad (also for an empty text). `off` gives an empty pin. */
        fun parse(text: String): FireDebugPin? {
            val whole = text.trim()
            if (whole.isEmpty()) return null
            if (whole == "off") return FireDebugPin()
            var pin = FireDebugPin()
            for (part in whole.split(',')) {
                val item = part.trim()
                pin = when {
                    item == "classic" -> pin.copy(classic = true)
                    item == "living" -> pin.copy(classic = false)
                    item == "light" -> pin.copy(start = FireTransition.Kind.LIGHT)
                    item == "douse" -> pin.copy(start = FireTransition.Kind.DOUSE)
                    item.startsWith("level:") -> pin.copy(level = number(item, "level:", 0f, 1f) ?: return null)
                    item.startsWith("wind:") -> pin.copy(wind = number(item, "wind:", -1f, 1f) ?: return null)
                    item.startsWith("hold:") -> pin.copy(hold = number(item, "hold:", 0f, 1f) ?: return null)
                    item.startsWith("light@") -> pin.copy(transition = frozen(item, "light@", FireTransition.Kind.LIGHT) ?: return null)
                    item.startsWith("douse@") -> pin.copy(transition = frozen(item, "douse@", FireTransition.Kind.DOUSE) ?: return null)
                    item == "haze:on" -> pin.copy(haze = true)
                    item == "haze:off" -> pin.copy(haze = false)
                    else -> return null
                }
            }
            return pin
        }

        private fun number(item: String, prefix: String, lo: Float, hi: Float): Float? {
            val v = item.substring(prefix.length).toFloatOrNull() ?: return null
            return if (v >= lo && v <= hi) v else null
        }

        private fun frozen(item: String, prefix: String, kind: FireTransition.Kind): FireTransition? {
            val fraction = number(item, prefix, 0f, 1f) ?: return null
            val length = when (kind) {
                FireTransition.Kind.LIGHT -> FireTuning.LIGHT_MS
                FireTransition.Kind.DOUSE -> FireTuning.DOUSE_MS
            }
            return FireTransition(kind, startMs = 0L, frozenElapsedMs = (fraction * length).toLong())
        }
    }
}

/**
 * The fire's debug pins: in memory, set by the `fire` link extra, applied only when [BuildConfig.TEST_HOOKS]
 * (release builds ignore every call but [clear]). A pinned `level` also pins [FireFeed]; [clear] releases it.
 * Nothing here is stored.
 */
object FireDebug {
    @Volatile var pin: FireDebugPin = FireDebugPin()

    /**
     * Applies [p] (null or an empty pin clears); ignored unless [enabled]. [apply], [clear] and [takeStart] share
     * this object's monitor, so [takeStart]'s read-modify-write of [pin] can never swallow a new pin.
     */
    @Synchronized
    fun apply(p: FireDebugPin?, enabled: Boolean = BuildConfig.TEST_HOOKS) {
        if (!enabled) return
        val next = p ?: FireDebugPin()
        pin = next
        FireFeed.pin(next.level)
    }

    /** Drops every pin and releases the feed's pin. */
    @Synchronized
    fun clear() {
        pin = FireDebugPin()
        FireFeed.pin(null)
    }

    /** The transition the pin asked to start now, once: the driver calls it every frame and gets each start once. */
    @Synchronized
    fun takeStart(): FireTransition.Kind? {
        val kind = pin.start ?: return null
        pin = pin.copy(start = null)
        return kind
    }
}
