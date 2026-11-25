package dev.pocketagent.app.ui.routines

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pocketagent.app.MainViewModel
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.data.Bot
import dev.pocketagent.app.data.BotPresets
import dev.pocketagent.app.routines.Routine
import dev.pocketagent.app.routines.RoutineKind
import dev.pocketagent.app.routines.RoutineSchedule
import dev.pocketagent.app.runtime.ProviderConfig
import dev.pocketagent.app.ui.LocalNavigator
import dev.pocketagent.app.ui.Route
import dev.pocketagent.app.ui.components.ButtonKind
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconButton
import dev.pocketagent.app.ui.components.PocketChip
import dev.pocketagent.app.ui.components.PocketDialog
import dev.pocketagent.app.ui.components.PocketSwitchRow
import dev.pocketagent.app.ui.components.PocketTextField
import dev.pocketagent.app.ui.components.PocketTopBar
import dev.pocketagent.app.ui.components.SectionHeader
import dev.pocketagent.app.ui.pixel.Avatar
import dev.pocketagent.app.ui.pixel.avatarIdOf
import dev.pocketagent.app.ui.theme.Pocket
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Everything the editor lets you change; compared with the values it started with to know whether anything is unsaved. */
private data class EditorFields(
    val name: String,
    val prompt: String,
    val botId: String,
    val kind: RoutineKind,
    val hour: Int,
    val minute: Int,
    val days: Int,
    val interval: Int,
    val onceDay: Long,
    val enabled: Boolean,
    val model: String,
    val effort: String,
) {
    companion object {
        fun from(r: Routine?): EditorFields {
            val zone = ZoneId.systemDefault()
            val tomorrow = LocalDate.now(zone).plusDays(1).toEpochDay()
            if (r == null) {
                return EditorFields("", "", BotPresets.HERMES_ID, RoutineKind.DAILY, 8, 0, RoutineSchedule.ALL_DAYS, 60, tomorrow, true, "", "")
            }
            val once = if (r.kind == RoutineKind.ONCE && r.onceAt > 0L) Instant.ofEpochMilli(r.onceAt).atZone(zone) else null
            return EditorFields(
                r.name, r.prompt, r.botId, r.kind, once?.hour ?: r.hour, once?.minute ?: r.minute, r.days, r.intervalMinutes,
                once?.toLocalDate()?.toEpochDay() ?: tomorrow, r.enabled, r.model, r.effort,
            )
        }
    }
}

/**
 * Creates or edits a routine: name, prompt, bot, when it runs (every day or some weekdays at a time, every N minutes, or
 * once), the on/off switch, and an optional model and thinking effort for its chat. [routineId] null = a new routine.
 */
@Composable
fun RoutineEditorScreen(routineId: String?) {
    val ctx = LocalContext.current
    val app = remember { PocketApp.from(ctx) }
    val nav = LocalNavigator.current
    val c = Pocket.colors
    val vm: MainViewModel = viewModel()
    val all by app.routines.routines.collectAsState()
    val bots by app.chat.bots.collectAsState()
    val busy by app.engine.busy.collectAsState()

    val existing: Routine? = remember(routineId) { routineId?.let { app.routines.get(it) } }
    val live: Routine? = routineId?.let { id -> all.firstOrNull { it.id == id } }
    val init = remember(routineId) { EditorFields.from(existing) }

    var name by rememberSaveable(routineId) { mutableStateOf(init.name) }
    var prompt by rememberSaveable(routineId) { mutableStateOf(init.prompt) }
    var botId by rememberSaveable(routineId) { mutableStateOf(init.botId) }
    var kindName by rememberSaveable(routineId) { mutableStateOf(init.kind.name) }
    var hour by rememberSaveable(routineId) { mutableIntStateOf(init.hour) }
    var minute by rememberSaveable(routineId) { mutableIntStateOf(init.minute) }
    var days by rememberSaveable(routineId) { mutableIntStateOf(init.days) }
    var interval by rememberSaveable(routineId) { mutableIntStateOf(init.interval) }
    var onceDay by rememberSaveable(routineId) { mutableLongStateOf(init.onceDay) }
    var enabled by rememberSaveable(routineId) { mutableStateOf(init.enabled) }
    var model by rememberSaveable(routineId) { mutableStateOf(init.model) }
    var effort by rememberSaveable(routineId) { mutableStateOf(init.effort) }
    var showDiscard by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    val kind = remember(kindName) { try { RoutineKind.valueOf(kindName) } catch (_: Exception) { RoutineKind.DAILY } }
    val current = EditorFields(name.trim(), prompt.trim(), botId, kind, hour, minute, days, interval, onceDay, enabled, model.trim(), effort)
    val dirty = current != init
    val missing = routineId != null && existing == null

    val zone = ZoneId.systemDefault()
    val nowMs = System.currentTimeMillis()
    val onceAtMillis = remember(onceDay, hour, minute) {
        LocalDate.ofEpochDay(onceDay).atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59)).atZone(zone).toInstant().toEpochMilli()
    }
    val noDays = kind == RoutineKind.DAILY && (days and RoutineSchedule.ALL_DAYS) == 0
    val onceInPast = kind == RoutineKind.ONCE && enabled && onceAtMillis <= nowMs
    val valid = name.isNotBlank() && prompt.isNotBlank() && !noDays && !onceInPast
    val canSave = valid && (dirty || existing == null)

    val savedMsg = stringResource(R.string.rtn_saved)
    val startedMsg = stringResource(R.string.rtn_started)

    fun build(): Routine {
        val base = live ?: existing ?: Routine(id = app.routines.newId(), name = "", prompt = "")
        return base.copy(
            name = name.trim(), prompt = prompt.trim(), botId = botId, kind = kind, hour = hour, minute = minute, days = days,
            intervalMinutes = interval, onceAt = if (kind == RoutineKind.ONCE) onceAtMillis else base.onceAt,
            enabled = enabled, model = model.trim(), effort = effort,
        )
    }

    fun save() {
        app.routines.save(build())
        app.routineScheduler.rescheduleAll()
        showToast(ctx, savedMsg)
        nav.back()
    }

    fun leave() { if (dirty) showDiscard = true else nav.back() }
    BackHandler(enabled = dirty) { showDiscard = true }

    Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        PocketTopBar(
            title = stringResource(if (existing == null) R.string.rtn_new_title else R.string.rtn_edit_title),
            subtitle = if (dirty) stringResource(R.string.rtn_unsaved) else null,
            onBack = { leave() },
            actions = {
                PixelIconButton(
                    PixelIcon.CHECK, onClick = { save() },
                    contentDescription = stringResource(R.string.rtn_save), tint = if (canSave) c.accent else c.textMuted,
                    enabled = canSave,
                )
            },
        )
        if (missing) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RText(stringResource(R.string.rtn_missing))
                PixelButton(stringResource(R.string.rtn_back), onClick = { nav.back() }, kind = ButtonKind.SECONDARY)
            }
        } else Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- what ----
            SectionHeader(stringResource(R.string.rtn_section_what))
            PocketTextField(
                name, { name = it.take(60) }, Modifier.fillMaxWidth(),
                label = stringResource(R.string.rtn_name), placeholder = stringResource(R.string.rtn_name_hint), singleLine = true,
            )
            PocketTextField(
                prompt, { prompt = it.take(4000) }, Modifier.fillMaxWidth(),
                label = stringResource(R.string.rtn_prompt), placeholder = stringResource(R.string.rtn_prompt_hint), minLines = 5,
            )
            RMuted(stringResource(R.string.rtn_prompt_help), size = 12.sp)

            SectionHeader(stringResource(R.string.rtn_section_bot))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                bots.forEach { b -> BotChoice(b, selected = b.id == botId, onClick = { botId = b.id }) }
            }

            // ---- when ----
            SectionHeader(stringResource(R.string.rtn_section_when))
            RChipRow {
                PocketChip(stringResource(R.string.rtn_kind_daily), selected = kind == RoutineKind.DAILY, onClick = { kindName = RoutineKind.DAILY.name }, icon = PixelIcon.CLOCK)
                PocketChip(stringResource(R.string.rtn_kind_interval), selected = kind == RoutineKind.INTERVAL, onClick = { kindName = RoutineKind.INTERVAL.name }, icon = PixelIcon.REFRESH)
                PocketChip(stringResource(R.string.rtn_kind_once), selected = kind == RoutineKind.ONCE, onClick = { kindName = RoutineKind.ONCE.name }, icon = PixelIcon.BELL)
            }
            RCard {
                when (kind) {
                    RoutineKind.DAILY -> {
                        RText(stringResource(R.string.rtn_time), size = 13.sp, weight = FontWeight.SemiBold, color = c.textMuted)
                        TimeStepper(hour, minute, onHour = { hour = it }, onMinute = { minute = it })
                        RText(stringResource(R.string.rtn_days), size = 13.sp, weight = FontWeight.SemiBold, color = c.textMuted)
                        RChipRow {
                            PocketChip(stringResource(R.string.rtn_every_day), selected = days == RoutineSchedule.ALL_DAYS, onClick = { days = RoutineSchedule.ALL_DAYS })
                            PocketChip(stringResource(R.string.rtn_weekdays), selected = days == RoutineSchedule.WEEKDAYS, onClick = { days = RoutineSchedule.WEEKDAYS })
                            PocketChip(stringResource(R.string.rtn_weekend), selected = days == RoutineSchedule.WEEKEND, onClick = { days = RoutineSchedule.WEEKEND })
                        }
                        RChipRow {
                            for (d in 0..6) {
                                PocketChip(dayName(d), selected = RoutineSchedule.hasDay(days, d), onClick = { days = RoutineSchedule.toggleDay(days, d) })
                            }
                        }
                    }
                    RoutineKind.INTERVAL -> {
                        RText(stringResource(R.string.rtn_every), size = 13.sp, weight = FontWeight.SemiBold, color = c.textMuted)
                        val choices = (INTERVAL_PRESETS + interval).distinct().sorted()
                        RChipRow {
                            choices.forEach { m -> PocketChip(intervalText(m), selected = interval == m, onClick = { interval = m }) }
                        }
                        RMuted(stringResource(R.string.rtn_interval_help), size = 12.sp)
                    }
                    RoutineKind.ONCE -> {
                        RText(stringResource(R.string.rtn_date), size = 13.sp, weight = FontWeight.SemiBold, color = c.textMuted)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            PixelIconButton(PixelIcon.DOWN, onClick = { onceDay -= 1 }, contentDescription = stringResource(R.string.rtn_prev_day))
                            RText(longDateText(onceDay), Modifier.weight(1f), size = 15.sp, weight = FontWeight.SemiBold, align = TextAlign.Center)
                            PixelIconButton(PixelIcon.UP, onClick = { onceDay += 1 }, contentDescription = stringResource(R.string.rtn_next_day))
                        }
                        val today = LocalDate.now(zone).toEpochDay()
                        RChipRow {
                            PocketChip(stringResource(R.string.rtn_today), selected = onceDay == today, onClick = { onceDay = today })
                            PocketChip(stringResource(R.string.rtn_tomorrow), selected = onceDay == today + 1, onClick = { onceDay = today + 1 })
                            PocketChip(stringResource(R.string.rtn_in_a_week), selected = onceDay == today + 7, onClick = { onceDay = today + 7 })
                        }
                        RText(stringResource(R.string.rtn_time), size = 13.sp, weight = FontWeight.SemiBold, color = c.textMuted)
                        TimeStepper(hour, minute, onHour = { hour = it }, onMinute = { minute = it })
                    }
                }
            }
            val preview = Routine(
                id = "preview", name = "", prompt = "", kind = kind, hour = hour, minute = minute, days = days,
                intervalMinutes = interval, onceAt = onceAtMillis, createdAt = nowMs, armedAt = nowMs,
            )
            val nextAt = RoutineSchedule.nextRun(preview, nowMs)
            when {
                noDays -> RText(stringResource(R.string.rtn_need_day), color = c.error, size = 13.sp)
                onceInPast -> RText(stringResource(R.string.rtn_once_past), color = c.error, size = 13.sp)
                nextAt != null && enabled -> RText(stringResource(R.string.rtn_first_run, whenText(nextAt, nowMs)), color = c.accent, size = 13.sp)
            }
            PocketSwitchRow(
                title = stringResource(R.string.rtn_active), checked = enabled, onCheckedChange = { enabled = it },
                subtitle = stringResource(R.string.rtn_active_help),
            )

            // ---- model ----
            SectionHeader(stringResource(R.string.rtn_section_model))
            RMuted(stringResource(R.string.rtn_model_help))
            PocketTextField(
                model, { model = it }, Modifier.fillMaxWidth(), label = stringResource(R.string.rtn_model),
                placeholder = stringResource(R.string.rtn_model_default), singleLine = true, monospace = true,
            )
            val suggestions = remember(vm.authMode, vm.endpoint) { ProviderConfig.suggestions(vm.authMode, vm.endpoint) }
            RChipRow {
                PocketChip(stringResource(R.string.effort_default), selected = model.isBlank(), onClick = { model = "" })
                suggestions.forEach { id -> PocketChip(id.substringAfterLast('/'), selected = model == id, onClick = { model = id }) }
            }
            RText(stringResource(R.string.rtn_effort), size = 14.sp, weight = FontWeight.SemiBold)
            RChipRow {
                EFFORT_CHOICES.forEach { (value, label) ->
                    PocketChip(stringResource(label), selected = effort == value, onClick = { effort = value })
                }
            }

            // ---- actions ----
            PixelButton(
                stringResource(R.string.rtn_save), onClick = { save() }, modifier = Modifier.fillMaxWidth(),
                icon = PixelIcon.CHECK, enabled = canSave,
            )
            if (name.isBlank()) RMuted(stringResource(R.string.rtn_need_name), size = 12.sp)
            else if (prompt.isBlank()) RMuted(stringResource(R.string.rtn_need_prompt), size = 12.sp)
            val liveConv = live?.convId.orEmpty()
            if (existing != null) {
                val running = liveConv.isNotEmpty() && liveConv in busy
                PixelButton(
                    stringResource(R.string.rtn_run_now),
                    onClick = { app.routineScheduler.runNow(existing.id); showToast(ctx, startedMsg) },
                    modifier = Modifier.fillMaxWidth(), icon = PixelIcon.PLAY, kind = ButtonKind.SECONDARY,
                    enabled = !dirty && !running,
                )
                if (dirty) RMuted(stringResource(R.string.rtn_save_first), size = 12.sp)
                if (liveConv.isNotEmpty() && app.chat.conversation(liveConv) != null) {
                    PixelButton(
                        stringResource(R.string.rtn_open_chat), onClick = { nav.go(Route.Chat(liveConv)) },
                        modifier = Modifier.fillMaxWidth(), icon = PixelIcon.CHATS, kind = ButtonKind.GHOST,
                    )
                }
                PixelButton(
                    stringResource(R.string.rtn_delete), onClick = { showDelete = true },
                    modifier = Modifier.fillMaxWidth(), icon = PixelIcon.TRASH, kind = ButtonKind.DANGER,
                )
            }
            Box(Modifier.size(24.dp))
        }
    }

    if (showDiscard) ConfirmDialog(
        title = stringResource(R.string.rtn_discard_title), text = stringResource(R.string.rtn_discard_body),
        confirmLabel = stringResource(R.string.rtn_discard), danger = true,
        onConfirm = { showDiscard = false; nav.back() }, onDismiss = { showDiscard = false },
    )
    if (showDelete && existing != null) ConfirmDialog(
        title = stringResource(R.string.rtn_delete_title, existing.name.ifBlank { stringResource(R.string.rtn_unnamed) }),
        text = stringResource(R.string.rtn_delete_body),
        confirmLabel = stringResource(R.string.rtn_delete), danger = true,
        onConfirm = {
            showDelete = false
            app.routineScheduler.deleteRoutine(existing.id)
            nav.back()
        },
        onDismiss = { showDelete = false },
    )
}

/** Thinking-effort chips: the stored value ("" = Hermes default) and its label. */
private val EFFORT_CHOICES = listOf(
    "" to R.string.effort_default, "none" to R.string.effort_none, "minimal" to R.string.effort_minimal, "low" to R.string.effort_low,
    "medium" to R.string.effort_medium, "high" to R.string.effort_high, "xhigh" to R.string.effort_xhigh,
)

/** A bot to pick: avatar and name in a small selectable card. */
@Composable
private fun BotChoice(bot: Bot, selected: Boolean, onClick: () -> Unit) {
    val c = Pocket.colors
    Column(
        Modifier
            .width(80.dp)
            .background(if (selected) c.accentSoft else c.surfaceHigh)
            .border(if (selected) 3.dp else 1.dp, if (selected) c.accent else c.outline)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Avatar(avatarIdOf(bot.avatar), Color(bot.color), size = 40.dp)
        RText(
            bot.name, size = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, align = TextAlign.Center,
            weight = if (selected) FontWeight.Bold else null,
        )
    }
}

/** Hour and minute with up / down buttons, and quick minute choices. 24-hour numbers; the chosen time is shown in the phone's style below. */
@Composable
private fun TimeStepper(hour: Int, minute: Int, onHour: (Int) -> Unit, onMinute: (Int) -> Unit) {
    val c = Pocket.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stepper(
                text = String.format(Locale.ROOT, "%02d", hour),
                onUp = { onHour((hour + 1) % 24) }, onDown = { onHour((hour + 23) % 24) },
                upDesc = stringResource(R.string.rtn_hour_up), downDesc = stringResource(R.string.rtn_hour_down),
            )
            RText(":", size = 32.sp, weight = FontWeight.Bold, mono = true)
            Stepper(
                text = String.format(Locale.ROOT, "%02d", minute),
                onUp = { onMinute((minute + 1) % 60) }, onDown = { onMinute((minute + 59) % 60) },
                upDesc = stringResource(R.string.rtn_minute_up), downDesc = stringResource(R.string.rtn_minute_down),
            )
        }
        RChipRow(Modifier.fillMaxWidth()) {
            listOf(0, 15, 30, 45).forEach { m ->
                PocketChip(String.format(Locale.ROOT, ":%02d", m), selected = minute == m, onClick = { onMinute(m) })
            }
        }
        RText(clockText(hour, minute), size = 13.sp, color = c.textMuted)
    }
}

@Composable
private fun Stepper(text: String, onUp: () -> Unit, onDown: () -> Unit, upDesc: String, downDesc: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PixelIconButton(PixelIcon.UP, onClick = onUp, contentDescription = upDesc)
        RText(text, size = 34.sp, weight = FontWeight.Bold, mono = true)
        PixelIconButton(PixelIcon.DOWN, onClick = onDown, contentDescription = downDesc)
    }
}

@Composable
private fun ConfirmDialog(
    title: String, text: String, confirmLabel: String,
    onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = false,
) {
    PocketDialog(
        title = title, onDismiss = onDismiss,
        confirm = { PixelButton(confirmLabel, onClick = onConfirm, kind = if (danger) ButtonKind.DANGER else ButtonKind.PRIMARY) },
        dismiss = { PixelButton(stringResource(R.string.cancel), onClick = onDismiss, kind = ButtonKind.GHOST) },
    ) {
        RText(text)
    }
}
