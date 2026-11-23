package dev.pocketagent.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.BuildConfig
import dev.pocketagent.app.MainViewModel
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.runtime.RuntimeState
import dev.pocketagent.app.ui.components.ButtonKind
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelPanel
import dev.pocketagent.app.ui.components.PocketSwitchRow
import dev.pocketagent.app.ui.pixel.Mascot
import dev.pocketagent.app.ui.pixel.MascotMood
import dev.pocketagent.app.ui.theme.Pocket

/** Runtime: status, start/stop/restart, battery hint, seccomp switch, self-test, delete runtime files. */
@Composable
internal fun RuntimeSection(vm: MainViewModel) {
    val ctx = LocalContext.current
    val c = Pocket.colors
    val state by vm.state.collectAsState()
    val selfTestActive by vm.selfTestActive.collectAsState()
    val selfTestText by vm.selfTestText.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val (label, detail) = runtimeLabel(state)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusDot(runtimeColor(state))
            CText(label, size = 18.sp, weight = FontWeight.SemiBold)
        }
        if (detail.isNotEmpty()) SelectionContainer { CText(detail, color = c.textMuted, size = 13.sp) }
        if (state is RuntimeState.Extracting) {
            LinearProgressIndicator(progress = { (state as RuntimeState.Extracting).fraction }, modifier = Modifier.fillMaxWidth(), color = c.accent, trackColor = c.surfaceHigh)
        } else if (state is RuntimeState.Starting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = c.accent, trackColor = c.surfaceHigh)
        }

        val canStart = state is RuntimeState.NotInstalled || state is RuntimeState.Stopped || state is RuntimeState.Error
        PixelButton(
            stringResource(if (state is RuntimeState.NotInstalled) R.string.btn_install_start else R.string.btn_start),
            onClick = { vm.start() }, modifier = Modifier.fillMaxWidth(), icon = PixelIcon.PLAY, enabled = canStart,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelButton(
                stringResource(R.string.btn_stop), onClick = { vm.stop() }, modifier = Modifier.weight(1f),
                icon = PixelIcon.STOP, kind = ButtonKind.SECONDARY, enabled = state.isBusyOrRunning || state is RuntimeState.Error,
            )
            PixelButton(
                stringResource(R.string.btn_restart), onClick = { vm.restart() }, modifier = Modifier.weight(1f),
                icon = PixelIcon.REFRESH, kind = ButtonKind.SECONDARY, enabled = state == RuntimeState.Running,
            )
        }

        if (state is RuntimeState.Starting && (state as RuntimeState.Starting).step.startsWith("still starting")) {
            ErrorText(stringResource(R.string.slow_start_hint))
        }
        if (selfTestActive) {
            ErrorText(stringResource(R.string.selftest_running_hint))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = c.accent, trackColor = c.surfaceHigh)
        } else if (state is RuntimeState.Error) {
            Muted(stringResource(R.string.error_hint))
        }

        if (!vm.ignoringBattery) {
            Muted(stringResource(R.string.battery_hint))
            PixelButton(stringResource(R.string.btn_battery), onClick = { ctx.startActivity(vm.batteryOptimizationIntent()) }, kind = ButtonKind.SECONDARY)
        }

        // ---- advanced ----
        SectionLabel(stringResource(R.string.ctl_rt_advanced))
        PocketSwitchRow(
            stringResource(R.string.debug_no_seccomp), vm.noSeccomp, { vm.updateNoSeccomp(it) },
            subtitle = stringResource(R.string.debug_no_seccomp_hint),
        )
        PixelButton(
            stringResource(R.string.btn_selftest), onClick = { vm.runSelfTest() },
            kind = ButtonKind.SECONDARY, icon = PixelIcon.BUG, enabled = !selfTestActive,
        )
        if (selfTestText.isNotEmpty()) {
            PixelPanel(Modifier.fillMaxWidth(), color = c.codeBg) {
                SelectionContainer { CText(selfTestText, size = 11.sp, mono = true, modifier = Modifier.fillMaxWidth().padding(10.dp)) }
            }
        }
        PixelButton(
            stringResource(R.string.btn_reset_runtime), onClick = { confirmReset = true },
            kind = ButtonKind.DANGER, icon = PixelIcon.TRASH,
        )
        Muted(stringResource(R.string.ctl_rt_reset_hint), size = 12.sp)
    }

    if (confirmReset) ConfirmDialog(
        title = stringResource(R.string.btn_reset_runtime), text = stringResource(R.string.ctl_rt_reset_body),
        confirmLabel = stringResource(R.string.ctl_rt_reset_confirm), danger = true,
        onConfirm = { confirmReset = false; vm.resetRuntime() }, onDismiss = { confirmReset = false },
    )
}

@Composable
private fun SectionLabel(text: String) {
    CText(text, color = Pocket.colors.accent, size = 12.sp, weight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
}

/** Diagnostics: copy or share the redacted dump (messages and behaviour unchanged). */
@Composable
internal fun DiagnosticsSection(vm: MainViewModel) {
    val ctx = LocalContext.current
    val selfTestActive by vm.selfTestActive.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Muted(stringResource(R.string.ctl_diag_explain))
        if (selfTestActive) ErrorText(stringResource(R.string.selftest_running_hint))
        PixelButton(stringResource(R.string.btn_copy_diag), onClick = { vm.copyDiagnostics() }, modifier = Modifier.fillMaxWidth(), icon = PixelIcon.COPY)
        PixelButton(
            stringResource(R.string.btn_share_diag), onClick = { vm.shareDiagnosticsIntent()?.let { ctx.startActivity(it) } },
            modifier = Modifier.fillMaxWidth(), icon = PixelIcon.SHARE, kind = ButtonKind.SECONDARY,
        )
    }
}

/** About: name, version, credits and licences. */
@Composable
internal fun AboutSection() {
    val ctx = LocalContext.current
    val c = Pocket.colors
    val app = remember { PocketApp.from(ctx) }
    var petTaps by remember { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Mascot(
                if (petTaps >= 5) MascotMood.HAPPY else MascotMood.WAVING,
                size = 80.dp,
                modifier = Modifier.clickable { petTaps++ },
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CText(stringResource(R.string.app_name), size = 20.sp, weight = FontWeight.Bold)
                Muted(stringResource(R.string.build_info, BuildConfig.VERSION_NAME, BuildConfig.GIT_COMMIT))
            }
        }
        if (petTaps >= 5) {
            val botsCount by app.chat.bots.collectAsState()
            val convsCount by app.chat.conversations.collectAsState()
            val upMs = app.runtime.processUptimeMillis()
            val upStr = if (upMs <= 0L) "0s" else "${upMs / 60_000}m ${(upMs / 1000) % 60}s"
            CtlCard(color = c.bgDeep, border = c.accent) {
                CText(stringResource(R.string.ctl_about_secret_title), color = c.accent, size = 12.sp, weight = FontWeight.Bold)
                CText(stringResource(R.string.ctl_about_secret_body, botsCount.size, convsCount.size, upStr), size = 13.sp, mono = true)
            }
        }
        CText(stringResource(R.string.ctl_about_what), size = 14.sp)
        SectionLabel(stringResource(R.string.ctl_about_hermes_title))
        CText(stringResource(R.string.ctl_about_hermes), size = 14.sp)
        SectionLabel(stringResource(R.string.ctl_about_pet_title))
        CText(stringResource(R.string.ctl_about_pet), size = 14.sp)
        SectionLabel(stringResource(R.string.ctl_about_licences_title))
        Muted(stringResource(R.string.ctl_about_licences))
    }
}
