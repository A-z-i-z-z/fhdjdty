package dev.pocketagent.app.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** What the system says about animation: the animator duration scale (0 = off) and battery saver. */
@Immutable
data class SystemMotionState(val animatorScale: Float, val powerSave: Boolean)

internal fun readSystemMotion(context: Context): SystemMotionState {
    val scale = try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    } catch (_: Throwable) {
        1f
    }
    val save = try {
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
    } catch (_: Throwable) {
        false
    }
    return SystemMotionState(scale, save)
}

/**
 * The system animator scale and battery saver, kept up to date: the scale through a content observer, battery
 * saver through its broadcast, both registered and removed with the composition, and read again on resume.
 * PocketTheme calls it once and turns it into the effective animation level.
 */
@Composable
fun rememberSystemMotion(): SystemMotionState {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var state by remember { mutableStateOf(readSystemMotion(context)) }
    DisposableEffect(context, lifecycle) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                state = readSystemMotion(context)
            }
        }
        try {
            resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        } catch (_: Throwable) {
            // The setting stays at what was read; the app keeps working.
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                state = readSystemMotion(context)
            }
        }
        var registered = false
        try {
            context.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
            registered = true
        } catch (_: Throwable) {
            // No battery-saver updates; the value read on resume still applies.
        }
        val onResume = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state = readSystemMotion(context)
        }
        lifecycle.addObserver(onResume)
        onDispose {
            lifecycle.removeObserver(onResume)
            try {
                resolver.unregisterContentObserver(observer)
            } catch (_: Throwable) {
            }
            if (registered) {
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: Throwable) {
                }
            }
        }
    }
    return state
}
