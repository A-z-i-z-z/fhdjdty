package dev.pocketagent.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.data.BudgetMode
import dev.pocketagent.app.ui.components.ButtonKind
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PocketChip
import dev.pocketagent.app.ui.components.PocketTextField
import dev.pocketagent.app.ui.theme.Pocket
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import dev.pocketagent.app.ui.components.ConfirmDialog
import androidx.compose.ui.platform.testTag
import dev.pocketagent.app.ui.RobotTags
import dev.pocketagent.app.ui.text.t
import dev.pocketagent.app.ui.text.tn

private const val CHART_DAYS = 14
private const val MAX_BUDGET_DIGITS = 12
private val BUDGET_PRESETS = listOf(50_000L, 200_000L, 1_000_000L)

/**
 * Settings > Usage: tokens today / 7 days / all time, a 14-day bar chart, a per-model table, the daily budget and "Clear statistics".
 * The numbers come from [dev.pocketagent.app.data.UsageStore] (what the model reported for each reply), nothing else.
 */
@Composable
internal fun UsageSection() {
    val ctx = LocalContext.current
    val store = remember { PocketApp.from(ctx).usage }
    val days by store.days.collectAsState()
    val budget by store.budget.collectAsState()
    val c = Pocket.colors
    // The language chosen inside the app (not the phone's): dates and numbers must match the rest of the screen.
    val locale = dev.pocketagent.app.ui.chat.currentLocale()
    val today = remember(days) { LocalDate.now() }
    var confirmClear by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Int?>(null) }

    val todayTotals = remember(days, today) { UsageMath.forDay(days, today) }
    val weekTotals = remember(days, today) { UsageMath.sumLast(days, today, 7) }
    val allTotals = remember(days) { UsageMath.sum(days) }
    val series = remember(days, today) { UsageMath.lastDays(days, today, CHART_DAYS) }
    val models = remember(days) { UsageMath.byModel(days) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Muted(t(R.string.ctl_usage_intro))

        if (days.isEmpty()) {
            Muted(t(R.string.ctl_usage_no_data))
        } else {
            // ---- totals ----
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TotalsCard(t(R.string.ctl_usage_today), todayTotals, locale, Modifier.weight(1f).testTag(RobotTags.USAGE_TODAY))
                TotalsCard(t(R.string.ctl_usage_week), weekTotals, locale, Modifier.weight(1f))
                TotalsCard(t(R.string.ctl_usage_all), allTotals, locale, Modifier.weight(1f))
            }

            // ---- chart ----
            SubHeading(t(R.string.ctl_usage_chart_title))
            val peak = series.maxOf { it.totals.total }.coerceAtLeast(1L)
            val dayFmt = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
            Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                series.forEachIndexed { i, d ->
                    val dayTotals = d.totals
                    val desc = t(R.string.ctl_usage_bar_desc, d.day.format(dayFmt), UsageMath.compact(dayTotals.tokensIn, locale), UsageMath.compact(dayTotals.tokensOut, locale), dayTotals.turns)
                    val isSel = selected == i
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .clickable(onClickLabel = desc) { selected = if (isSel) null else i }
                            .semantics { contentDescription = desc; role = Role.Button },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        if (dayTotals.total <= 0L) {
                            Box(Modifier.fillMaxWidth().height(2.dp).background(if (isSel) c.accent else c.outline))
                        } else {
                            val frac = (dayTotals.total.toFloat() / peak.toFloat()).coerceIn(0.04f, 1f)
                            Column(Modifier.fillMaxWidth().fillMaxHeight(frac).then(if (isSel) Modifier.border(2.dp, c.text) else Modifier)) {
                                if (dayTotals.tokensIn > 0L) Box(Modifier.weight(dayTotals.tokensIn.toFloat().coerceAtLeast(1f)).fillMaxWidth().background(c.accent))
                                if (dayTotals.tokensOut > 0L) Box(Modifier.weight(dayTotals.tokensOut.toFloat().coerceAtLeast(1f)).fillMaxWidth().background(c.fireHigh))
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Muted(series.first().day.format(dayFmt), size = 11.sp)
                Muted(t(R.string.ctl_usage_peak, UsageMath.compact(peak, locale)), size = 11.sp)
                Muted(series.last().day.format(dayFmt), size = 11.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendDot(c.accent, t(R.string.ctl_usage_legend_in))
                LegendDot(c.fireHigh, t(R.string.ctl_usage_legend_out))
            }
            selected?.let { i ->
                series.getOrNull(i)?.let { d ->
                    CText(
                        d.day.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale)) + ": " +
                            t(R.string.ctl_usage_in_out, UsageMath.compact(d.totals.tokensIn, locale), UsageMath.compact(d.totals.tokensOut, locale)) +
                            " · " + tn(R.plurals.ctl_usage_turns, d.totals.turns, d.totals.turns),
                        size = 13.sp, color = c.accent,
                    )
                }
            }

            // ---- per model ----
            SubHeading(t(R.string.ctl_usage_models))
            CtlCard {
                models.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CText(
                            m.model.ifBlank { t(R.string.ctl_usage_model_default) },
                            Modifier.weight(1f), size = 12.sp, mono = m.model.isNotBlank(), maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            CText(t(R.string.ctl_usage_in_out, UsageMath.compact(m.totals.tokensIn, locale), UsageMath.compact(m.totals.tokensOut, locale)), size = 12.sp, weight = FontWeight.SemiBold)
                            Muted(tn(R.plurals.ctl_usage_turns, m.totals.turns, m.totals.turns), size = 11.sp)
                        }
                    }
                }
            }
        }

        // ---- budget ----
        SubHeading(t(R.string.ctl_usage_budget_title))
        Muted(t(R.string.ctl_usage_budget_help), size = 12.sp)
        var limitText by remember(budget.dailyTokens) { mutableStateOf(if (budget.dailyTokens > 0L) budget.dailyTokens.toString() else "") }
        PocketTextField(
            limitText,
            { raw ->
                val digits = raw.filter { it.isDigit() }.take(MAX_BUDGET_DIGITS)
                limitText = digits
                store.setBudget(budget.copy(dailyTokens = digits.toLongOrNull() ?: 0L))
            },
            Modifier.fillMaxWidth().testTag(RobotTags.USAGE_BUDGET_LIMIT), label = t(R.string.ctl_usage_budget_field), singleLine = true, monospace = true,
        )
        ChipRow {
            BUDGET_PRESETS.forEach { v ->
                PocketChip(UsageMath.compact(v, locale), selected = budget.dailyTokens == v, onClick = { store.setBudget(budget.copy(dailyTokens = v)) })
            }
        }
        ChipRow {
            PocketChip(t(R.string.ctl_usage_mode_off), selected = budget.mode == BudgetMode.OFF, onClick = { store.setBudget(budget.copy(mode = BudgetMode.OFF)) })
            PocketChip(t(R.string.ctl_usage_mode_warn), selected = budget.mode == BudgetMode.WARN, onClick = { store.setBudget(budget.copy(mode = BudgetMode.WARN)) }, modifier = Modifier.testTag(RobotTags.USAGE_BUDGET_WARN))
            PocketChip(t(R.string.ctl_usage_mode_block), selected = budget.mode == BudgetMode.BLOCK, onClick = { store.setBudget(budget.copy(mode = BudgetMode.BLOCK)) }, modifier = Modifier.testTag(RobotTags.USAGE_BUDGET_BLOCK))
        }
        if (budget.mode != BudgetMode.OFF && budget.dailyTokens <= 0L) {
            CText(t(R.string.ctl_usage_budget_need_limit), color = c.warning, size = 12.sp)
        }
        if (budget.dailyTokens > 0L) {
            val used = todayTotals.total
            val over = budget.mode != BudgetMode.OFF && used >= budget.dailyTokens
            MeterBar(
                used.toFloat() / budget.dailyTokens.toFloat(),
                if (over) c.error else if (used > budget.dailyTokens * 0.85) c.warning else c.success,
            )
            CText(
                t(R.string.ctl_usage_budget_today, UsageMath.compact(used, locale), UsageMath.compact(budget.dailyTokens, locale)),
                size = 12.sp, color = if (over) c.error else c.textMuted,
            )
            if (over) CText(t(R.string.ctl_usage_budget_over), size = 12.sp, color = c.error)
        }

        PixelButton(
            t(R.string.ctl_usage_clear), onClick = { confirmClear = true },
            kind = ButtonKind.DANGER, icon = PixelIcon.TRASH, enabled = days.isNotEmpty(),
        )
    }

    if (confirmClear) ConfirmDialog(
        title = t(R.string.ctl_usage_clear_title), body = t(R.string.ctl_usage_clear_body),
        confirmLabel = t(R.string.ctl_usage_clear_confirm), danger = true,
        onConfirm = { confirmClear = false; selected = null; store.clear() }, onDismiss = { confirmClear = false },
    )
}

@Composable
private fun TotalsCard(title: String, totals: UsageTotals, locale: Locale, modifier: Modifier = Modifier) {
    val c = Pocket.colors
    CtlCard(modifier, color = c.bgDeep) {
        Muted(title, size = 11.sp)
        CText(UsageMath.compact(totals.total, locale), size = 20.sp, weight = FontWeight.Bold, color = c.accent)
        Muted(t(R.string.ctl_usage_in_out, UsageMath.compact(totals.tokensIn, locale), UsageMath.compact(totals.tokensOut, locale)), size = 11.sp)
        Muted(tn(R.plurals.ctl_usage_turns, totals.turns, totals.turns), size = 11.sp)
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).background(color))
        Muted(label, size = 11.sp)
    }
}
