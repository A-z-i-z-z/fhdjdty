package dev.pocketagent.app.ui.components

import android.util.Log
import dev.pocketagent.app.BuildConfig

/**
 * The phone robot's cut check: a pixel text that had to end in "..." says so here, with the first 60 characters
 * of what was cut. The log line is `AgiriCut` at warning level. It exists only in builds with test hooks;
 * elsewhere the call does nothing.
 */
object CutProbe {
    fun report(text: String) {
        if (BuildConfig.TEST_HOOKS) {
            try {
                Log.w("AgiriCut", text.take(60))
            } catch (_: Throwable) {
                // A probe must never break drawing.
            }
        }
    }
}
