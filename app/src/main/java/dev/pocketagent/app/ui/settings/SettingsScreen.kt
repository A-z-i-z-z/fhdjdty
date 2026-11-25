package dev.pocketagent.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.pocketagent.app.ui.text.t
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.R
import dev.pocketagent.app.ui.LocalNavigator
import dev.pocketagent.app.ui.components.ButtonKind
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconView
import dev.pocketagent.app.ui.components.PixelPanel
import dev.pocketagent.app.ui.components.PocketDialog
import dev.pocketagent.app.ui.components.PocketTopBar
import dev.pocketagent.app.ui.theme.AppearanceStore
import dev.pocketagent.app.ui.theme.Pocket
import androidx.compose.ui.platform.testTag
import dev.pocketagent.app.ui.RobotTags

private enum class Section(val key: String, val title: Int, val icon: PixelIcon) {
    PROVIDER("provider", R.string.ctl_set_provider, PixelIcon.KEY),
    USAGE("usage", R.string.ctl_set_usage, PixelIcon.CHART),
    BEHAVIOUR("behaviour", R.string.ctl_set_behaviour, PixelIcon.BELL),
    APPEARANCE("appearance", R.string.ctl_set_appearance, PixelIcon.PALETTE),
    VOICE("voice", R.string.ctl_set_voice, PixelIcon.VOLUME),
    LANGUAGE("language", R.string.ctl_set_language, PixelIcon.GLOBE),
    BACKUP("backup", R.string.ctl_set_backup, PixelIcon.ARCHIVE),
    RUNTIME("runtime", R.string.ctl_set_runtime, PixelIcon.PLAY),
    DIAGNOSTICS("diagnostics", R.string.ctl_set_diagnostics, PixelIcon.BUG),
    ABOUT("about", R.string.ctl_set_about, PixelIcon.INFO),
}

/**
 * Settings as a list of sections; one is open at a time. [section] ("provider", "usage", "behaviour", "appearance", "language", "backup",
 * "runtime", "diagnostics", "about") opens and scrolls to that section.
 */
@Composable
fun SettingsScreen(section: String?) {
    val vm = mainVm()
    val nav = LocalNavigator.current
    val c = Pocket.colors
    var open by rememberSaveable { mutableStateOf(section) }
    val listState = rememberLazyListState()
    val state by vm.state.collectAsState()

    LaunchedEffect(section) {
        val idx = Section.entries.indexOfFirst { it.key == section }
        if (idx >= 0) { open = section; listState.animateScrollToItem(idx) }
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        PocketTopBar(
            title = t(R.string.ctl_settings_title),
            onBack = if (section != null) ({ nav.back(); Unit }) else null,
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), state = listState,
            contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Section.entries.forEach { sec ->
                item(key = sec.key) {
                    val isOpen = open == sec.key
                    PixelPanel(Modifier.fillMaxWidth(), border = if (isOpen) c.accent else c.outline) {
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().testTag(RobotTags.settingsSection(sec.key)).heightIn(min = 56.dp)
                                    .clickable(role = Role.Button, onClickLabel = t(if (isOpen) R.string.ctl_collapse else R.string.ctl_expand)) {
                                        open = if (isOpen) null else sec.key
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                PixelIconView(sec.icon, tint = c.accent)
                                Column(Modifier.weight(1f)) {
                                    CText(t(sec.title), size = 16.sp, weight = FontWeight.SemiBold)
                                    Muted(summaryOf(sec, vm, state), size = 12.sp)
                                }
                                PixelIconView(if (isOpen) PixelIcon.UP else PixelIcon.DOWN, tint = c.textMuted)
                            }
                            if (isOpen) {
                                Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                                    when (sec) {
                                        Section.PROVIDER -> ProviderSection(vm)
                                        Section.USAGE -> UsageSection()
                                        Section.BEHAVIOUR -> BehaviourSection(vm)
                                        Section.BACKUP -> BackupSection()
                                        Section.APPEARANCE -> AppearanceSection()
                                        Section.VOICE -> VoiceSoundSection()
                                        Section.LANGUAGE -> LanguageSection()
                                        Section.RUNTIME -> RuntimeSection(vm)
                                        Section.DIAGNOSTICS -> DiagnosticsSection(vm)
                                        Section.ABOUT -> AboutSection()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    vm.importResult?.let { ImportResultDialog(it) { vm.dismissImportResult() } }
    vm.diagMessage?.let { m ->
        val text = if (m.startsWith("truncated:")) {
            val p = m.split(":"); t(R.string.diag_copied_truncated, p[2].toInt(), p[1].toInt())
        } else t(R.string.diag_copied, m.substringAfter(":").toInt())
        PocketDialog(
            title = t(R.string.ctl_set_diagnostics), onDismiss = { vm.clearDiagMessage() },
            confirm = { PixelButton(t(R.string.ok), onClick = { vm.clearDiagMessage() }) },
        ) { CText(text) }
    }
}

/** One short line under each section title: the current value. */
@Composable
private fun summaryOf(sec: Section, vm: dev.pocketagent.app.MainViewModel, state: dev.pocketagent.app.runtime.RuntimeState): String {
    val ctx = LocalContext.current
    return when (sec) {
        Section.PROVIDER -> vm.model
        Section.USAGE -> {
            val app = dev.pocketagent.app.PocketApp.from(ctx)
            val days = app.usage.days.collectAsState().value
            if (days.isEmpty()) t(R.string.ctl_usage_summary_none)
            else t(R.string.ctl_usage_summary_today, UsageMath.compact(UsageMath.forDay(days, java.time.LocalDate.now()).total, dev.pocketagent.app.ui.chat.currentLocale()))
        }
        Section.BEHAVIOUR -> {
            val s = dev.pocketagent.app.PocketApp.from(ctx).presencePrefs.settings.collectAsState().value
            val on = listOf(s.autoStart, s.wakeOnSend, s.notifyReplies, s.notifyApprovals, s.autoRestart).count { it }
            t(R.string.ctl_behaviour_summary, on, 5)
        }
        Section.BACKUP -> t(R.string.ctl_backup_summary)
        Section.APPEARANCE -> {
            val ap = AppearanceStore.get(ctx).appearance.collectAsState().value
            themeName(ap.theme)
        }
        Section.VOICE -> {
            val app = dev.pocketagent.app.PocketApp.from(ctx)
            val vs = app.voicePrefs.settings.collectAsState().value
            val installed = app.ttsModels.state.collectAsState().value is dev.pocketagent.app.voice.TtsModelState.Installed
            val ttsLabel = if (installed && vs.ttsEngine != dev.pocketagent.app.voice.TtsEngineMode.SYSTEM) "Supertonic 3" else t(R.string.ctl_voice_engine_system)
            val sfxLabel = when (vs.soundPack) {
                dev.pocketagent.app.voice.SoundPack.HEARTH -> t(R.string.ctl_sfx_hearth)
                dev.pocketagent.app.voice.SoundPack.STUDIO -> t(R.string.ctl_sfx_studio)
                dev.pocketagent.app.voice.SoundPack.OFF -> t(R.string.ctl_sfx_off)
            }
            "$ttsLabel · $sfxLabel"
        }
        Section.LANGUAGE -> {
            val ap = AppearanceStore.get(ctx).appearance.collectAsState().value
            languageName(ap.language)
        }
        Section.RUNTIME -> runtimeLabel(state).first
        Section.DIAGNOSTICS -> t(R.string.ctl_diag_summary)
        Section.ABOUT -> t(R.string.build_info, dev.pocketagent.app.BuildConfig.VERSION_NAME, dev.pocketagent.app.BuildConfig.GIT_COMMIT)
    }
}

@Composable
internal fun languageName(code: String): String = when (code) {
    "en" -> "English"
    "tr" -> "Türkçe"
    "fr" -> "Français"
    else -> t(R.string.ctl_lang_system)
}

@Composable
internal fun ImportResultDialog(r: dev.pocketagent.app.ImportResult, onClose: () -> Unit) {
    val body = when (r) {
        is dev.pocketagent.app.ImportResult.ServiceAccount ->
            t(R.string.import_ok_sa, r.projectId, r.maskedEmail) + if (r.repaired) "\n" + t(R.string.import_repaired) else ""
        is dev.pocketagent.app.ImportResult.UserCredentials ->
            t(R.string.import_ok_user, r.masked) + if (r.repaired) "\n" + t(R.string.import_repaired) else ""
        is dev.pocketagent.app.ImportResult.Failure -> when (r.code) {
            dev.pocketagent.app.runtime.SaParse.Code.EMPTY -> t(R.string.import_err_empty)
            dev.pocketagent.app.runtime.SaParse.Code.NOT_JSON -> t(R.string.import_err_not_json, r.detail)
            dev.pocketagent.app.runtime.SaParse.Code.NOT_OBJECT -> t(R.string.import_err_not_object)
            dev.pocketagent.app.runtime.SaParse.Code.WRONG_TYPE -> t(R.string.import_err_type, r.detail)
            dev.pocketagent.app.runtime.SaParse.Code.MISSING_FIELDS -> t(R.string.import_err_missing, r.detail)
            dev.pocketagent.app.runtime.SaParse.Code.BAD_PRIVATE_KEY -> t(R.string.import_err_key)
            null -> t(R.string.import_err_unreadable)
        }
    }
    PocketDialog(
        title = t(if (r is dev.pocketagent.app.ImportResult.Failure) R.string.import_failed else R.string.import_done),
        onDismiss = onClose,
        confirm = { PixelButton(t(R.string.ok), onClick = onClose, kind = ButtonKind.PRIMARY) },
    ) { CText(body) }
}
