package dev.pocketagent.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import dev.pocketagent.app.ui.text.Texts

/**
 * Foreground service with a persistent notification while the runtime is extracting, starting or
 * running. It does no work itself; its purpose is to raise the process priority so Android and
 * One UI are less eager to freeze or kill the proot child processes. The notification also says how many
 * chats are answering right now.
 */
class AgentService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var lastStartId = 0
    private var lastText: String? = null
    private var inForeground = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, Texts.get(localized(), R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
        )
        startForeground(NOTIF_ID, build(Texts.get(localized(), R.string.notif_starting)))
        lastText = Texts.get(localized(), R.string.notif_starting)
        val app = PocketApp.from(this)
        val rt = app.runtime
        job = scope.launch {
            // The runtime owns the decision: it asks for the foreground while an operation is in flight or a
            // child process is alive, so transient Stopped/Error states in between cannot end the service.
            combine(rt.foregroundNeeded, rt.state, app.engine.busy) { fg, s, busy -> Triple(fg, s, busy.size) }.collect { (fg, s, answering) ->
                // `fg` may be a stale emission: re-read the live value right before acting on it.
                if (!fg && !rt.foregroundNeeded.value) {
                    AppLog.i("Service", "nothing running (${s.label()}); leaving foreground")
                    stopForeground(true)
                    inForeground = false
                    stopSelf(lastStartId) // no-op when a newer start command arrived meanwhile
                    return@collect
                }
                val l = localized()
                val text = when (s) {
                    is RuntimeState.Running ->
                        if (answering > 0) Texts.getQuantity(l, R.plurals.prs_notif_running_answering, answering, answering)
                        else Texts.get(l, R.string.notif_running)
                    is RuntimeState.Extracting -> Texts.get(l, R.string.notif_extracting, (s.fraction * 100).toInt())
                    else -> Texts.get(l, R.string.notif_starting)
                }
                if (!inForeground) { inForeground = true; lastText = text; startForeground(NOTIF_ID, build(text)) } // restarted while still alive
                else if (text != lastText) { lastText = text; nm.notify(NOTIF_ID, build(text)) } // only when the text changed
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        // Every startForegroundService() call must be answered with startForeground(), including when this
        // instance is already running (it may have left the foreground since). The collector below ends it
        // again if nothing needs it.
        startForeground(NOTIF_ID, build(lastText ?: Texts.get(localized(), R.string.notif_starting)))
        inForeground = true
        if (intent?.action == ACTION_STOP) {
            val app = PocketApp.from(this)
            app.presence.noteUserStop() // the user asked for this: no auto-start until the next process start
            app.runtime.stop()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel(); scope.cancel()
        super.onDestroy()
    }

    /** Resources in the language chosen inside the app (the service itself only follows the system language). */
    private fun localized(): Context = withAppLanguage()

    private fun build(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, AgentService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(Texts.get(this, R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            // Its own group, so Android does not bundle it with the reply notifications (those have groups and summaries of their own).
            .setGroup(NotificationGroups.GROUP_RUNTIME)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, Texts.get(localized(), R.string.action_stop), stop)
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
