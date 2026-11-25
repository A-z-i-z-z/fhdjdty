package dev.pocketagent.app.ui.nav

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf

/** One change asked for by the debug link extra "nav": "back=0,shared=1,reset=1". Null means: leave as it is. */
internal data class NavFlagChange(val back: Boolean?, val shared: Boolean?, val reset: Boolean)

/**
 * Reads the value of the debug link extra "nav": comma separated pairs "back=0|1", "shared=0|1" and "reset=1". Null when
 * the text is blank, a pair is unknown or its value is not a switch. Pure, so it is unit tested.
 */
internal fun parseNavFlags(text: String?): NavFlagChange? {
    if (text == null || text.isBlank()) return null
    var back: Boolean? = null
    var shared: Boolean? = null
    var reset = false
    var pairs = 0
    for (raw in text.split(',')) {
        val part = raw.trim()
        if (part.isEmpty()) continue
        val eq = part.indexOf('=')
        if (eq <= 0) return null
        val key = part.substring(0, eq).trim().lowercase()
        val value = when (part.substring(eq + 1).trim().lowercase()) {
            "1", "true", "on" -> true
            "0", "false", "off" -> false
            else -> return null
        }
        when (key) {
            "back" -> back = value
            "shared" -> shared = value
            "reset" -> reset = value
            else -> return null
        }
        pairs++
    }
    return if (pairs == 0) null else NavFlagChange(back, shared, reset)
}

/**
 * Runtime switches of the navigation, changed only by the debug link of a build with test hooks (extra "nav"). The
 * defaults are what a normal build does.
 *
 * - [sharedElements]: avatars and titles travel between a list row and the screen it opens (experiment S3 passed).
 * - [backGestureDrivesScreen]: reserved for a phone whose back gesture reports progress. Experiment S1 failed on the
 *   owner's phone (the gesture arrives as one key press at the end), and Wave 0 has no seekable back machinery
 *   (lead ruling 11.5), so the switch exists and does nothing: a plain back handler pops the stack and the pop
 *   transition plays after the gesture.
 */
internal object NavFlags {
    @Volatile var backGestureDrivesScreen: Boolean = false
    @Volatile var sharedElements: Boolean = true

    /** Bumped on every change so that the composition that reads the flags notices. */
    val revision: MutableIntState = mutableIntStateOf(0)

    /** [sharedElements], read so that a composable that calls it follows the debug switch. */
    fun sharedElementsOn(): Boolean = revision.intValue >= 0 && sharedElements

    fun reset() {
        backGestureDrivesScreen = false
        sharedElements = true
        bump()
    }

    fun apply(change: NavFlagChange) {
        if (change.reset) {
            backGestureDrivesScreen = false
            sharedElements = true
        }
        change.back?.let { backGestureDrivesScreen = it }
        change.shared?.let { sharedElements = it }
        bump()
    }

    private fun bump() {
        revision.intValue = revision.intValue + 1
    }
}
