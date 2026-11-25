package dev.pocketagent.app.ui.routines

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.data.Bot
import dev.pocketagent.app.routines.Routine
import dev.pocketagent.app.routines.RoutineKind
import dev.pocketagent.app.routines.RoutineSchedule
import dev.pocketagent.app.ui.LocalNavigator
import dev.pocketagent.app.ui.Route
import dev.pocketagent.app.ui.components.ButtonKind
import dev.pocketagent.app.ui.components.EmptyState
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconButton
import dev.pocketagent.app.ui.components.PixelIconView
import dev.pocketagent.app.ui.components.PixelPanel
import dev.pocketagent.app.ui.components.PocketSwitchRow
import dev.pocketagent.app.ui.components.PocketTopBar
import dev.pocketagent.app.ui.components.SectionHeader
import dev.pocketagent.app.ui.pixel.Avatar
import dev.pocketagent.app.ui.pixel.avatarIdOf
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.Space
import kotlinx.coroutines.delay
import dev.pocketagent.app.ui.text.t

/**
 * The list of routines: schedule, next run, last result, an on/off switch, "run now" and a shortcut to the routine's own
 * chat. With no routines it shows Pêt and four one-tap templates; the templates stay available below the list.
 */
@Composable
fun RoutinesScreen() {
    val ctx = LocalContext.current
    val app = remember { PocketApp.from(ctx) }
    val nav = LocalNavigator.current
    val c = Pocket.colors
    val routines by app.routines.routines.collectAsState()
    val busy by app.engine.busy.collectAsState()
    val bots by app.chat.bots.collectAsState()
    val convs by app.chat.conversations.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var notificationsOn by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            notificationsOn = try { app.presence.notifier.notificationsEnabled() } catch (_: Throwable) { true }
            delay(5_000)
        }
    }

    val startedMsg = t(R.string.rtn_started)
    val nextRuns = routines.associate { it.id to RoutineSchedule.nextRun(it, now) }
    val soonest = routines.filter { nextRuns[it.id] != null }.minByOrNull { nextRuns[it.id]!! }
    val subtitle = soonest?.let { s ->
        t(R.string.rtn_next_subtitle, whenText(nextRuns[s.id]!!, now), s.name.ifBlank { t(R.string.rtn_unnamed) })
    }

    fun leave() { if (!nav.back()) nav.tab(Route.Home) }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        PocketTopBar(
            title = t(R.string.rtn_title),
            subtitle = subtitle,
            onBack = { leave() },
            actions = {
                PixelIconButton(
                    PixelIcon.PLUS, onClick = { nav.go(Route.RoutineEditor(null)) },
                    contentDescription = t(R.string.rtn_add),
                )
            },
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            if (!notificationsOn) {
                RCard(color = c.surfaceHigh, border = c.warning) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PixelIconView(PixelIcon.BELL, tint = c.warning, size = 24.dp)
                        RText(t(R.string.rtn_notif_off_title), weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    }
                    RMuted(t(R.string.rtn_notif_off_body))
                    PixelButton(
                        t(R.string.rtn_notif_off_button),
                        onClick = {
                            try {
                                ctx.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            } catch (_: Exception) { }
                        },
                        kind = ButtonKind.SECONDARY, icon = PixelIcon.GEAR,
                    )
                }
            }

            if (routines.isEmpty()) {
                EmptyState(
                    title = t(R.string.rtn_empty_title),
                    body = t(R.string.rtn_empty_body),
                )
            } else {
                routines.forEach { r ->
                    val bot = bots.firstOrNull { it.id == r.botId }
                    val running = r.convId.isNotEmpty() && r.convId in busy
                    RoutineCard(
                        r = r, bot = bot, running = running, next = nextRuns[r.id], now = now,
                        hasChat = r.convId.isNotEmpty() && convs.any { it.id == r.convId },
                        onOpen = { nav.go(Route.RoutineEditor(r.id)) },
                        onToggle = { on ->
                            app.routines.save(r.copy(enabled = on))
                            app.routineScheduler.rescheduleAll()
                        },
                        onRun = {
                            app.routineScheduler.runNow(r.id)
                            showToast(ctx, startedMsg)
                        },
                        onOpenChat = { nav.go(Route.Chat(r.convId)) },
                    )
                }
            }

            SectionHeader(t(R.string.rtn_ideas))
            ROUTINE_TEMPLATES.forEach { tpl ->
                val name = t(tpl.nameRes)
                val prompt = t(tpl.promptRes)
                val sample = Routine(id = "", name = name, prompt = prompt, kind = tpl.kind, hour = tpl.hour, minute = tpl.minute,
                    days = tpl.days, intervalMinutes = tpl.intervalMinutes)
                TemplateCard(
                    title = name, description = t(tpl.descRes), schedule = scheduleText(sample),
                    onClick = {
                        val r = sample.copy(id = app.routines.newId(), createdAt = System.currentTimeMillis())
                        app.routines.save(r)
                        app.routineScheduler.rescheduleAll()
                        nav.go(Route.RoutineEditor(r.id))
                    },
                )
            }
            Box(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun RoutineCard(
    r: Routine,
    bot: Bot?,
    running: Boolean,
    next: Long?,
    now: Long,
    hasChat: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRun: () -> Unit,
    onOpenChat: () -> Unit,
) {
    val c = Pocket.colors
    PixelPanel(Modifier.fillMaxWidth(), color = c.surface, border = if (r.enabled) c.outline else c.outline.copy(alpha = 0.5f)) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = t(R.string.rtn_edit), onClick = onOpen)
                    .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                if (bot != null) Avatar(avatarIdOf(bot.avatar), Color(bot.color), size = 40.dp)
                else PixelIconView(PixelIcon.CLOCK, tint = c.accent, size = 36.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                    RText(
                        r.name.ifBlank { t(R.string.rtn_unnamed) }, size = 16.sp, weight = FontWeight.Bold,
                        color = if (r.enabled) c.text else c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    RMuted(scheduleText(r), size = 12.5.sp)
                }
                PixelIconView(PixelIcon.EDIT, tint = c.textMuted, size = 20.dp)
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val nextLine = when {
                    running -> t(R.string.rtn_running_now)
                    // A one-time routine that ran (or whose time passed and was skipped) is switched off by the scheduler: "Done".
                    !r.enabled && r.kind == RoutineKind.ONCE && r.onceAt > 0L &&
                        (r.lastRunAt >= r.onceAt || (r.lastStatus == Routine.STATUS_SKIPPED && r.armedAt >= r.onceAt)) ->
                        t(R.string.rtn_done)
                    !r.enabled -> t(R.string.rtn_paused)
                    next != null -> t(R.string.rtn_next, whenText(next, now))
                    r.kind == RoutineKind.ONCE && r.lastRunAt > 0L -> t(R.string.rtn_done)
                    else -> t(R.string.rtn_nothing_scheduled)
                }
                RText(nextLine, size = 13.sp, color = if (running) c.accent else c.text, weight = if (running) FontWeight.SemiBold else null)
                if (r.lastRunAt > 0L) {
                    val (label, color) = when (r.lastStatus) {
                        Routine.STATUS_OK -> t(R.string.rtn_status_ok) to c.success
                        Routine.STATUS_FAILED -> t(R.string.rtn_status_failed) to c.error
                        Routine.STATUS_SKIPPED -> t(R.string.rtn_status_skipped) to c.warning
                        else -> t(R.string.rtn_status_sent) to c.textMuted
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).background(color))
                        RMuted(t(R.string.rtn_last_run, whenText(r.lastRunAt, now), label), size = 12.sp)
                    }
                }
            }
            PocketSwitchRow(
                title = t(R.string.rtn_active),
                checked = r.enabled,
                onCheckedChange = onToggle,
            )
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                PixelButton(
                    t(R.string.rtn_run_now), onClick = onRun, modifier = Modifier.weight(1f),
                    icon = PixelIcon.PLAY, kind = ButtonKind.SECONDARY, enabled = !running,
                )
                PixelButton(
                    t(R.string.rtn_open_chat), onClick = onOpenChat, modifier = Modifier.weight(1f),
                    icon = PixelIcon.CHATS, kind = ButtonKind.GHOST, enabled = hasChat,
                )
            }
        }
    }
}

@Composable
private fun TemplateCard(title: String, description: String, schedule: String, onClick: () -> Unit) {
    val c = Pocket.colors
    PixelPanel(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = t(R.string.rtn_tpl_use), onClick = onClick),
        color = c.surface,
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            PixelIconView(PixelIcon.CLOCK, tint = c.fireMid, size = 36.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                RText(title, size = 15.sp, weight = FontWeight.SemiBold)
                RMuted(description, size = 12.5.sp)
                RText(schedule, size = 12.sp, color = c.accent)
            }
            PixelIconView(PixelIcon.PLUS, tint = c.textMuted, size = 20.dp)
        }
    }
}
