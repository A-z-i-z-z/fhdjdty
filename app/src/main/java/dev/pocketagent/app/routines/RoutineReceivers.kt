package dev.pocketagent.app.routines

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.util.AppLog
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The routine alarm rings here (not exported). The process may have been dead: [PocketApp] has been created by now, and
 * the work is handed to the scheduler, which keeps `goAsync()` open only until the prompts are handed to the chat engine.
 * A wake lock covers the gap between the broadcast and that hand-over.
 */
class RoutineAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != RoutineScheduler.ACTION_ALARM) return
        val app = PocketApp.from(context)
        val pending = goAsync()
        val released = AtomicBoolean(false)
        app.wakeLocks.acquire("routine-alarm", 30_000L)
        fun finish() {
            if (released.compareAndSet(false, true)) {
                app.wakeLocks.release("routine-alarm")
                try { pending.finish() } catch (_: Throwable) { }
            }
        }
        try {
            app.routineScheduler.onAlarm { finish() }
        } catch (e: Throwable) {
            AppLog.e("Routines", "alarm receiver failed", e)
            finish()
        }
    }
}

/**
 * Re-arms the alarm after a reboot, an app update and a clock or time-zone change (an alarm does not survive a reboot or
 * an update). Runs that fell due while the phone was off are made up by the alarm that this sets (see
 * [RoutineSchedule.alarmAt]).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            PocketApp.from(context).routineScheduler.rescheduleAll()
        } catch (e: Throwable) {
            AppLog.e("Routines", "could not re-arm the routine alarm (${intent?.action})", e)
        }
    }
}
