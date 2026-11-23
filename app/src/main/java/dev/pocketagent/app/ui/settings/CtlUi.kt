package dev.pocketagent.app.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pocketagent.app.MainViewModel
import dev.pocketagent.app.R
import dev.pocketagent.app.runtime.RuntimeState
import dev.pocketagent.app.ui.components.ButtonKind
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconView
import dev.pocketagent.app.ui.components.PixelPanel
import dev.pocketagent.app.ui.components.PocketDialog
import dev.pocketagent.app.ui.theme.Pocket

// Small helpers shared by the Bots, Hermes, Terminal and Settings screens (agent CONTROL).

/** Thinking-effort chips: the value stored ("" = not set, Hermes default) and its label. */
internal val EFFORT_CHIPS = listOf(
    "" to R.string.effort_default, "none" to R.string.effort_none, "minimal" to R.string.effort_minimal, "low" to R.string.effort_low,
    "medium" to R.string.effort_medium, "high" to R.string.effort_high, "xhigh" to R.string.effort_xhigh,
)

/** The activity-scoped view model (the same instance the activity holds). */
@Composable
internal fun mainVm(): MainViewModel = viewModel()

/** Themed text with the sizes the control screens use. */
@Composable
internal fun CText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Pocket.colors.text,
    size: TextUnit = 15.sp,
    weight: FontWeight? = null,
    mono: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    Text(
        text, modifier, color = color, fontSize = size, fontWeight = weight,
        fontFamily = if (mono) FontFamily.Monospace else null,
        maxLines = maxLines, overflow = overflow, lineHeight = lineHeight,
    )
}

@Composable
internal fun Muted(text: String, modifier: Modifier = Modifier, size: TextUnit = 13.sp) =
    CText(text, modifier, color = Pocket.colors.textMuted, size = size)

@Composable
internal fun ErrorText(text: String, modifier: Modifier = Modifier) =
    CText(text, modifier, color = Pocket.colors.error, size = 13.sp)

/** A bordered pixel panel with padded, evenly spaced content. */
@Composable
internal fun CtlCard(
    modifier: Modifier = Modifier,
    color: Color = Pocket.colors.surface,
    border: Color = Pocket.colors.outline,
    content: @Composable ColumnScope.() -> Unit,
) {
    PixelPanel(modifier.fillMaxWidth(), color = color, border = border) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** A single-select row (radio behaviour) with a check mark and an accent border when selected. */
@Composable
internal fun ChoiceRow(selected: Boolean, title: String, onClick: () -> Unit, subtitle: String? = null) {
    val c = Pocket.colors
    PixelPanel(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        color = if (selected) c.accentSoft else c.surface,
        border = if (selected) c.accent else c.outline,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CText(title, size = 15.sp, weight = if (selected) FontWeight.SemiBold else null)
                if (subtitle != null) Muted(subtitle, size = 12.sp)
            }
            if (selected) PixelIconView(PixelIcon.CHECK, tint = c.accent)
        }
    }
}

/** A horizontally scrollable row (chips, suggestions). */
@Composable
internal fun ChipRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = content,
    )
}

/** Small square status dot. */
@Composable
internal fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).background(color))
}

@Composable
internal fun ConfirmDialog(
    title: String, text: String, confirmLabel: String,
    onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = false,
) {
    PocketDialog(
        title = title, onDismiss = onDismiss,
        confirm = { PixelButton(confirmLabel, onClick = onConfirm, kind = if (danger) ButtonKind.DANGER else ButtonKind.PRIMARY) },
        dismiss = { PixelButton(stringResource(R.string.cancel), onClick = onDismiss, kind = ButtonKind.GHOST) },
    ) {
        CText(text)
    }
}

/** Runtime state as (label, detail). */
@Composable
internal fun runtimeLabel(state: RuntimeState): Pair<String, String> = when (state) {
    RuntimeState.NotInstalled -> stringResource(R.string.state_not_installed) to ""
    is RuntimeState.Extracting -> stringResource(R.string.state_extracting, (state.fraction * 100).toInt()) to state.detail
    is RuntimeState.Starting -> stringResource(R.string.state_starting) to state.step
    RuntimeState.Running -> stringResource(R.string.state_running) to ""
    is RuntimeState.Stopped -> stringResource(R.string.state_stopped) to state.reason
    is RuntimeState.Error -> stringResource(R.string.state_error) to state.reason
}

@Composable
internal fun runtimeColor(state: RuntimeState): Color = when (state) {
    RuntimeState.Running -> Pocket.colors.success
    is RuntimeState.Error -> Pocket.colors.error
    is RuntimeState.Starting, is RuntimeState.Extracting -> Pocket.colors.warning
    else -> Pocket.colors.textMuted
}

/** True once the Linux system is on disk, so the terminal and the Hermes files can be used. */
internal fun isInstalled(state: RuntimeState): Boolean = state !is RuntimeState.NotInstalled && state !is RuntimeState.Extracting

internal fun copyText(ctx: Context, label: String, text: String) {
    try {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    } catch (_: Exception) {
        // A clipboard that refuses (very large text, background app) must not crash the screen.
    }
}

internal fun shareText(ctx: Context, subject: String, text: String) {
    try {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, text.takeLast(200_000))
        ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        // No share target or an oversized payload: ignore.
    }
}

internal fun toast(ctx: Context, message: String) {
    try { Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
}
