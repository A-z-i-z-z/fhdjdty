package dev.pocketagent.app.ui.settings

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.ui.components.ButtonKind
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PocketChip
import dev.pocketagent.app.ui.components.PocketSwitchRow
import dev.pocketagent.app.ui.theme.Pocket

/** The idle-stop choices in minutes (0 = never); a stored value that is not listed gets its own chip. */
private val IDLE_CHOICES = listOf(0, 15, 30, 60, 120)

/**
 * Settings > Behaviour: how present the agent is (start by itself, wake on send, notifications, restart after a crash, rest when
 * idle), the notification permission state and the battery-optimisation hint. Every switch is a field of
 * [dev.pocketagent.app.runtime.PresenceSettings].
 */
@Composable
internal fun BehaviourSection(vm: dev.pocketagent.app.MainViewModel) {
    val ctx = LocalContext.current
    val app = remember { PocketApp.from(ctx) }
    val prefs = app.presencePrefs
    val s by prefs.settings.collectAsState()
    val c = Pocket.colors

    // The notification switch lives in the system settings: look again whenever the user comes back to the app.
    var notificationsOn by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsOn = try { app.presence.notifier.notificationsEnabled() } catch (_: Exception) { true }
        vm.refreshBattery()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Muted(stringResource(R.string.ctl_beh_intro))

        SubHeading(stringResource(R.string.ctl_beh_group_wake))
        PocketSwitchRow(
            stringResource(R.string.ctl_beh_auto_start), s.autoStart, { v -> prefs.update { it.copy(autoStart = v) } },
            subtitle = stringResource(R.string.ctl_beh_auto_start_sub),
        )
        PocketSwitchRow(
            stringResource(R.string.ctl_beh_wake_send), s.wakeOnSend, { v -> prefs.update { it.copy(wakeOnSend = v) } },
            subtitle = stringResource(R.string.ctl_beh_wake_send_sub),
        )
        PocketSwitchRow(
            stringResource(R.string.ctl_beh_auto_restart), s.autoRestart, { v -> prefs.update { it.copy(autoRestart = v) } },
            subtitle = stringResource(R.string.ctl_beh_auto_restart_sub),
        )

        SubHeading(stringResource(R.string.ctl_beh_idle_title))
        Muted(stringResource(R.string.ctl_beh_idle_sub), size = 12.sp)
        val choices = if (s.idleStopMinutes in IDLE_CHOICES) IDLE_CHOICES else (IDLE_CHOICES + s.idleStopMinutes).sorted()
        ChipRow {
            choices.forEach { m ->
                PocketChip(
                    if (m == 0) stringResource(R.string.ctl_beh_idle_never) else stringResource(R.string.ctl_beh_idle_min, m),
                    selected = s.idleStopMinutes == m, onClick = { prefs.update { it.copy(idleStopMinutes = m) } },
                )
            }
        }

        SubHeading(stringResource(R.string.ctl_beh_notif_title))
        PocketSwitchRow(
            stringResource(R.string.ctl_beh_notify_replies), s.notifyReplies, { v -> prefs.update { it.copy(notifyReplies = v) } },
            subtitle = stringResource(R.string.ctl_beh_notify_replies_sub),
        )
        PocketSwitchRow(
            stringResource(R.string.ctl_beh_notify_approvals), s.notifyApprovals, { v -> prefs.update { it.copy(notifyApprovals = v) } },
            subtitle = stringResource(R.string.ctl_beh_notify_approvals_sub),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusDot(if (notificationsOn) c.success else c.warning)
            CText(
                stringResource(if (notificationsOn) R.string.ctl_beh_notif_on else R.string.ctl_beh_notif_off),
                size = 13.sp, color = if (notificationsOn) c.text else c.warning, modifier = Modifier.weight(1f),
            )
        }
        PixelButton(
            stringResource(R.string.ctl_beh_notif_open), onClick = { openNotificationSettings(ctx) },
            kind = if (notificationsOn) ButtonKind.GHOST else ButtonKind.SECONDARY, icon = PixelIcon.BELL,
        )

        SubHeading(stringResource(R.string.ctl_beh_battery_title))
        if (vm.ignoringBattery) {
            Muted(stringResource(R.string.ctl_beh_battery_ok), size = 13.sp)
        } else {
            Muted(stringResource(R.string.battery_hint))
            PixelButton(stringResource(R.string.btn_battery), onClick = { ctx.startActivity(vm.batteryOptimizationIntent()) }, kind = ButtonKind.SECONDARY)
        }
    }
}

/** Opens the system's notification page of this app (falls back to the app's details page). */
private fun openNotificationSettings(ctx: android.content.Context) {
    val pkg = ctx.packageName
    val primary = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, pkg)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        ctx.startActivity(primary)
    } catch (_: Exception) {
        try {
            ctx.startActivity(
                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) { /* no settings app to open: nothing more to do */ }
    }
}
