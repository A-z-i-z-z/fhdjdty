package dev.pocketagent.app.ui.routines

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.R
import dev.pocketagent.app.routines.Routine
import dev.pocketagent.app.routines.RoutineKind
import dev.pocketagent.app.routines.RoutineSchedule
import dev.pocketagent.app.ui.components.PixelPanel
import dev.pocketagent.app.ui.theme.Pocket
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

// Small shared pieces of the routine screens (agent ROUTINES): text styles, the one-tap templates, and the
// formatting of schedules and times in the language the app currently shows.

/** A ready-made routine offered on the empty state and below the list. All texts are string resources. */
internal class RoutineTemplate(
    val nameRes: Int,
    val descRes: Int,
    val promptRes: Int,
    val kind: RoutineKind,
    val hour: Int,
    val minute: Int,
    val days: Int,
    val intervalMinutes: Int = 60,
)

internal val ROUTINE_TEMPLATES = listOf(
    RoutineTemplate(R.string.rtn_tpl_morning_name, R.string.rtn_tpl_morning_desc, R.string.rtn_tpl_morning_prompt,
        RoutineKind.DAILY, 8, 0, RoutineSchedule.ALL_DAYS),
    RoutineTemplate(R.string.rtn_tpl_journal_name, R.string.rtn_tpl_journal_desc, R.string.rtn_tpl_journal_prompt,
        RoutineKind.DAILY, 21, 0, RoutineSchedule.ALL_DAYS),
    RoutineTemplate(R.string.rtn_tpl_weekly_name, R.string.rtn_tpl_weekly_desc, R.string.rtn_tpl_weekly_prompt,
        RoutineKind.DAILY, 18, 0, 1 shl 6),
    RoutineTemplate(R.string.rtn_tpl_kurdish_name, R.string.rtn_tpl_kurdish_desc, R.string.rtn_tpl_kurdish_prompt,
        RoutineKind.DAILY, 9, 0, RoutineSchedule.ALL_DAYS),
)

/** Interval choices of the editor, in minutes. */
internal val INTERVAL_PRESETS = listOf(15, 30, 60, 120, 180, 360, 720, 1440, 2880, 10080)

// ---- text atoms ------------------------------------------------------------------------------------------------------

@Composable
internal fun RText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Pocket.colors.text,
    size: TextUnit = 15.sp,
    weight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    mono: Boolean = false,
    align: TextAlign? = null,
) {
    Text(
        text, modifier, color = color, fontSize = size, fontWeight = weight,
        fontFamily = if (mono) FontFamily.Monospace else null,
        maxLines = maxLines, overflow = overflow, textAlign = align,
    )
}

@Composable
internal fun RMuted(text: String, modifier: Modifier = Modifier, size: TextUnit = 13.sp, maxLines: Int = Int.MAX_VALUE) =
    RText(text, modifier, color = Pocket.colors.textMuted, size = size, maxLines = maxLines)

/** A bordered pixel panel with padded, evenly spaced content. */
@Composable
internal fun RCard(
    modifier: Modifier = Modifier,
    color: Color = Pocket.colors.surface,
    border: Color = Pocket.colors.outline,
    content: @Composable ColumnScope.() -> Unit,
) {
    PixelPanel(modifier.fillMaxWidth(), color = color, border = border) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** A horizontally scrollable row of chips. */
@Composable
internal fun RChipRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = content,
    )
}

internal fun showToast(ctx: Context, message: String) {
    try { Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show() } catch (_: Exception) { }
}

// ---- formatting --------------------------------------------------------------------------------------------------------

@Composable
internal fun currentLocale(): Locale = LocalConfiguration.current.locales.get(0) ?: Locale.getDefault()

/** "08:00" or "8:00 AM", as the language of the app writes times. */
@Composable
internal fun clockText(hour: Int, minute: Int): String {
    val locale = currentLocale()
    return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        .format(java.time.LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)))
}

/** Short weekday name for [day] (0 = Monday .. 6 = Sunday). */
@Composable
internal fun dayName(day: Int, style: TextStyle = TextStyle.SHORT): String =
    DayOfWeek.of(day.coerceIn(0, 6) + 1).getDisplayName(style, currentLocale())

@Composable
internal fun intervalText(minutes: Int): String {
    val m = RoutineSchedule.clampInterval(minutes)
    return when {
        m % 1440 == 0 -> stringResource(R.string.rtn_unit_d, m / 1440)
        m % 60 == 0 -> stringResource(R.string.rtn_unit_h, m / 60)
        m > 60 -> stringResource(R.string.rtn_unit_hm, m / 60, m % 60)
        else -> stringResource(R.string.rtn_unit_min, m)
    }
}

/** The weekday part of a DAILY schedule: "Every day", "Weekdays", "Weekend" or the list "Mon, Wed". */
@Composable
internal fun daysText(mask: Int): String {
    val m = mask and RoutineSchedule.ALL_DAYS
    val dayNames = RoutineSchedule.daysOf(m).map { dayName(it) }
    return when (m) {
        RoutineSchedule.ALL_DAYS -> stringResource(R.string.rtn_every_day)
        RoutineSchedule.WEEKDAYS -> stringResource(R.string.rtn_weekdays)
        RoutineSchedule.WEEKEND -> stringResource(R.string.rtn_weekend)
        0 -> stringResource(R.string.rtn_no_day)
        else -> dayNames.joinToString(", ")
    }
}

/** A one-line description of when [r] runs. */
@Composable
internal fun scheduleText(r: Routine): String {
    val locale = currentLocale()
    return when (r.kind) {
        RoutineKind.DAILY -> stringResource(R.string.rtn_sched_daily, daysText(r.days), clockText(r.hour, r.minute))
        RoutineKind.INTERVAL -> stringResource(R.string.rtn_sched_interval, intervalText(r.intervalMinutes))
        RoutineKind.ONCE -> {
            val dt = Instant.ofEpochMilli(r.onceAt).atZone(ZoneId.systemDefault())
            val text = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).format(dt)
            stringResource(R.string.rtn_sched_once, text)
        }
    }
}

/** "Today, 08:00", "Tomorrow, 08:00", "Wed, 08:00" (within a week) or a full date and time. */
@Composable
internal fun whenText(millis: Long, nowMillis: Long): String {
    val locale = currentLocale()
    val zone = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(millis).atZone(zone)
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(dt)
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val diff = ChronoUnit.DAYS.between(today, dt.toLocalDate())
    return when {
        diff == 0L -> stringResource(R.string.rtn_when_today, time)
        diff == 1L -> stringResource(R.string.rtn_when_tomorrow, time)
        diff in 2L..6L -> stringResource(R.string.rtn_when_day, dayName(dt.dayOfWeek.value - 1), time)
        diff == -1L -> stringResource(R.string.rtn_when_yesterday, time)
        else -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).format(dt)
    }
}

/** The long date used by the one-time picker, e.g. "Tuesday, 24 November 2026". */
@Composable
internal fun longDateText(epochDay: Long): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(currentLocale()).format(java.time.LocalDate.ofEpochDay(epochDay))
