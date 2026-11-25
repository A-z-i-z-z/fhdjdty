package dev.pocketagent.app.ui.pixel

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.pocketagent.app.ui.pixel.stage.FrameGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A clock in milliseconds for scenes that animate many things at once: one coroutine, tied to the display's
 * frames (withFrameNanos) and gated to at most [maxFps] updates per second, so on a 60, 90 or 120 Hz screen
 * the value changes every 2nd, 3rd or 4th frame and never more often. The first value is written on the first
 * frame. Stays 0 while [active] is false. While the screen is not STARTED (the app is in the background) the
 * loop is suspended; when it resumes the time continues from the last value, it does not jump.
 *
 * Read the returned state only inside draw lambdas, so a tick invalidates the draw and never the composition.
 */
@Composable
fun rememberSceneClock(active: Boolean, maxFps: Int = 30): LongState {
    val state = remember { mutableLongStateOf(0L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(active, maxFps, lifecycle) {
        if (!active) {
            state.longValue = 0L
            return@LaunchedEffect
        }
        val gate = FrameGate(maxFps)
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            gate.reset()
            val resumeFrom = state.longValue
            var firstFrame = -1L
            while (true) {
                val now = withFrameNanos { it }
                if (firstFrame < 0L) firstFrame = now
                if (gate.accept(now)) state.longValue = resumeFrom + (now - firstFrame) / 1_000_000L
            }
        }
    }
    return state
}

/**
 * True once the sprite bank has parsed every file. While it is false this loads the bank on a background
 * thread (a no-op wait when a preload is already running) and flips to true afterwards, so drawing code can
 * show nothing for a moment instead of blocking the main thread.
 */
@Composable
fun rememberSpritesReady(): State<Boolean> {
    val context = LocalContext.current
    return produceState(SpriteBank.isReady) {
        if (!value) {
            withContext(Dispatchers.Default) {
                try {
                    SpriteBank.init(context)
                } catch (e: Exception) {
                    Log.e("PocketAgent/Pixel", "sprite bank failed to load", e)
                }
            }
            value = true
        }
    }
}
