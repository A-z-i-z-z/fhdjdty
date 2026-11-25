package dev.pocketagent.app.service

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.pocketagent.app.MainActivity
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.runtime.ProviderConfig
import dev.pocketagent.app.runtime.RuntimeState
import dev.pocketagent.app.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import dev.pocketagent.app.ui.text.Texts

/**
 * The Quick Settings tile for the fire. Active = Hermes is running, unavailable while the runtime is unpacking or
 * starting, inactive otherwise. A tap lights the fire or puts it out; when there is nothing to light yet (the runtime
 * is not unpacked, or the provider setup is incomplete) it opens the app instead.
 *
 * The service can run in a process without any UI: [PocketApp] is created for it like for any other component.
 */
class FireTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        val runtime = PocketApp.from(this).runtime
        try { runtime.refreshInstalledState() } catch (e: Exception) { AppLog.w("Tile", "could not refresh the installed state", e) }
        job?.cancel()
        job = scope.launch { runtime.state.collect { render(it) } }
    }

    override fun onStopListening() {
        job?.cancel()
        job = null
        super.onStopListening()
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        val app = PocketApp.from(this)
        val runtime = app.runtime
        when (runtime.state.value) {
            RuntimeState.Running -> {
                app.presence.noteUserStop()
                runtime.stop()
            }
            is RuntimeState.Extracting, is RuntimeState.Starting -> Unit // the tile is unavailable then; ignore a late tap
            RuntimeState.NotInstalled -> openApp()
            is RuntimeState.Stopped, is RuntimeState.Error -> {
                val ready = runtime.isRootfsValid() && ProviderConfig.savedProblems(runtime.settings, runtime.secrets).isEmpty()
                if (ready) runtime.start() else openApp()
            }
        }
    }

    private fun render(state: RuntimeState) {
        val tile = qsTile ?: return
        val (tileState, subtitle) = when (state) {
            RuntimeState.Running -> Tile.STATE_ACTIVE to R.string.srf_tile_on
            is RuntimeState.Extracting, is RuntimeState.Starting -> Tile.STATE_UNAVAILABLE to R.string.srf_tile_wait
            RuntimeState.NotInstalled, is RuntimeState.Stopped, is RuntimeState.Error -> Tile.STATE_INACTIVE to R.string.srf_tile_off
        }
        tile.state = tileState
        val l = withAppLanguage() // follow the language picked inside the app, not only the system's
        tile.label = Texts.get(l, R.string.srf_tile_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_fire)
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = Texts.get(l, subtitle)
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
