package dev.pocketagent.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import dev.pocketagent.app.MainActivity
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.runtime.RuntimeState
import dev.pocketagent.app.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Foreground service with a persistent notification while the runtime is extracting, starting or
 * running. It does no work itself; its purpose is to raise the process priority so Android and
 * One UI are less eager to freeze or kill the proot child processes.
 */
class AgentService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
        )
        startForeground(NOTIF_ID, build(getString(R.string.notif_starting)))
        val rt = PocketApp.from(this).runtime
        job = scope.launch {
            rt.state.collect { s ->
                val text = when (s) {
                    is RuntimeState.Running -> getString(R.string.notif_running)
                    is RuntimeState.Extracting -> getString(R.string.notif_extracting, (s.fraction * 100).toInt())
                    is RuntimeState.Starting -> getString(R.string.notif_starting)
                    else -> null
                }
                if (text == null) {
                    AppLog.i("Service", "runtime not active (${s.label()}); leaving foreground")
                    stopForeground(true)
                    stopSelf()
                } else {
                    nm.notify(NOTIF_ID, build(text))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) PocketApp.from(this).runtime.stop()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel(); scope.cancel()
        super.onDestroy()
    }

    private fun build(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, AgentService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.action_stop), stop).build())
            .build()
    }

    companion object {
        private const val CHANNEL = "runtime"
        private const val NOTIF_ID = 1
        const val ACTION_STOP = "dev.pocketagent.app.STOP"

        fun ensureRunning(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, AgentService::class.java))
        }
    }
}
