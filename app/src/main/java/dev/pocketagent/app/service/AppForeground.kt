package dev.pocketagent.app.service

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the app is in the foreground: true while at least one activity is started. Replacing an activity (a
 * configuration change that recreates it) starts the new one before the old one stops, so the value does not flicker.
 */
object AppForeground {
    private val state = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = state.asStateFlow()

    private val lock = Any()
    private var started = 0
    private var installed = false

    /** Registers the lifecycle callbacks. Safe to call twice. */
    fun install(app: Application) {
        synchronized(lock) {
            if (installed) return
            installed = true
        }
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) = change(+1)
            override fun onActivityStopped(activity: Activity) = change(-1)
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun change(delta: Int) {
        synchronized(lock) {
            started = (started + delta).coerceAtLeast(0)
            state.value = started > 0
        }
    }
}
