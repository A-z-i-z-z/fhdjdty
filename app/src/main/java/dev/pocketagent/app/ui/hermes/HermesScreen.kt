package dev.pocketagent.app.ui.hermes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.hermes.HermesDoc
import dev.pocketagent.app.runtime.RuntimeState
import dev.pocketagent.app.ui.LocalNavigator
import dev.pocketagent.app.ui.Route
import dev.pocketagent.app.ui.RobotTags
import dev.pocketagent.app.ui.components.ButtonKind
import dev.pocketagent.app.ui.components.ConfirmDialog
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelPanel
import dev.pocketagent.app.ui.components.PocketChip
import dev.pocketagent.app.ui.components.PocketTopBar
import dev.pocketagent.app.ui.pixel.Mascot
import dev.pocketagent.app.ui.pixel.MascotMood
import dev.pocketagent.app.ui.settings.CText
import dev.pocketagent.app.ui.settings.ChipRow
import dev.pocketagent.app.ui.settings.Muted
import dev.pocketagent.app.ui.settings.StatusDot
import dev.pocketagent.app.ui.settings.isInstalled
import dev.pocketagent.app.ui.settings.mainVm
import dev.pocketagent.app.ui.settings.runtimeColor
import dev.pocketagent.app.ui.settings.runtimeLabel
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.text.t

private const val HERMES_VERSION = "0.23.3"

private enum class HermesTab(val label: Int, val icon: PixelIcon) {
    SOUL(R.string.ctl_tab_soul, PixelIcon.SOUL),
    MEMORY(R.string.ctl_tab_memory, PixelIcon.MEMORY),
    USER(R.string.ctl_tab_user, PixelIcon.USER),
    SKILLS(R.string.ctl_tab_skills, PixelIcon.SKILL),
    CONFIG(R.string.ctl_tab_config, PixelIcon.GEAR),
    LOGS(R.string.ctl_tab_logs, PixelIcon.SCROLL),
}

/** The "Hermes" tab: status, and an inspector for the files and logs that make up the agent. */
@Composable
fun HermesScreen() {
    val ctx = LocalContext.current
    val nav = LocalNavigator.current
    val vm = mainVm()
    val state by vm.state.collectAsState()
    val hvm: HermesEditorsViewModel = viewModel()
    val home = hvm.home
    val installed = isInstalled(state)
    val running = state == RuntimeState.Running || state is RuntimeState.Starting
    val c = Pocket.colors
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmLeave by remember { mutableStateOf(false) }

    val soul = hvm.soul
    val memory = hvm.memory
    val user = hvm.user
    val memUpdates by PocketApp.from(ctx).engine.memoryUpdates.collectAsState()
    LaunchedEffect(installed, memUpdates) {
        // Files appear once the runtime is installed (or after chat updates memory); never overwrite what the user is typing.
        listOf(soul, memory, user).forEach { if (!it.dirty) it.reload() }
    }
    BackHandler(enabled = hvm.anyDirty) { confirmLeave = true }
    val restart: () -> Unit = { vm.restart() }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        PocketTopBar(title = t(R.string.ctl_hermes_title), subtitle = t(R.string.ctl_hermes_subtitle))

        // ---- status ----
        PixelPanel(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (Pocket.appearance.showMascot) {
                        Mascot(
                            when (state) {
                                RuntimeState.Running -> MascotMood.IDLE
                                is RuntimeState.Starting, is RuntimeState.Extracting -> MascotMood.THINKING
                                is RuntimeState.Error -> MascotMood.ERROR
                                else -> MascotMood.SLEEPING
                            },
                            size = 56.dp,
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        CText(t(R.string.ctl_hermes_name, HERMES_VERSION), size = 16.sp, weight = FontWeight.Bold)
                        val (label, detail) = runtimeLabel(state)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusDot(runtimeColor(state))
                            CText(label, size = 14.sp, maxLines = 1)
                        }
                        if (detail.isNotEmpty()) Muted(detail, size = 12.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PixelButton(
                        t(R.string.ctl_open_terminal), onClick = { nav.go(Route.Terminal) },
                        modifier = Modifier.weight(1f), icon = PixelIcon.TERMINAL,
                    )
                    when {
                        state == RuntimeState.Running -> PixelButton(t(R.string.btn_restart), onClick = restart, kind = ButtonKind.SECONDARY, icon = PixelIcon.REFRESH)
                        state is RuntimeState.NotInstalled || state is RuntimeState.Stopped || state is RuntimeState.Error ->
                            PixelButton(t(if (state is RuntimeState.NotInstalled) R.string.btn_install_start else R.string.btn_start), onClick = { vm.start() }, kind = ButtonKind.SECONDARY, icon = PixelIcon.PLAY)
                        else -> Box(Modifier)
                    }
                }
            }
        }

        // ---- tab row ----
        val dirtyOf = mapOf(HermesTab.SOUL to soul.dirty, HermesTab.MEMORY to memory.dirty, HermesTab.USER to user.dirty, HermesTab.SKILLS to hvm.anySkillDirty)
        ChipRow(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            HermesTab.entries.forEach { ht ->
                val label = t(ht.label) + if (dirtyOf[ht] == true) " •" else ""
                PocketChip(label, selected = tab == ht.ordinal, onClick = { tab = ht.ordinal }, modifier = Modifier.testTag(hermesTabTag(ht)), icon = ht.icon)
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (HermesTab.entries[tab.coerceIn(0, HermesTab.entries.size - 1)]) {
                HermesTab.SOUL -> DocEditor(
                    soul, info = infoOf(home, HermesDoc.SOUL, soul.original), explain = t(R.string.ctl_soul_explain),
                    installed = installed, running = running, onRestart = restart,
                )
                HermesTab.MEMORY -> DocEditor(
                    memory, info = infoOf(home, HermesDoc.MEMORY, memory.original), explain = t(R.string.ctl_memory_explain),
                    installed = installed, running = running, onRestart = restart, budget = MEMORY_BUDGET, entries = true,
                )
                HermesTab.USER -> DocEditor(
                    user, info = infoOf(home, HermesDoc.USER, user.original), explain = t(R.string.ctl_user_explain),
                    installed = installed, running = running, onRestart = restart, budget = USER_BUDGET, entries = true,
                )
                HermesTab.SKILLS -> SkillsTab(hvm, installed, running, restart)
                HermesTab.CONFIG -> ConfigTab(home, installed) { nav.go(Route.Settings("provider")) }
                HermesTab.LOGS -> LogsTab(home)
            }
        }
    }

    if (confirmLeave) ConfirmDialog(
        title = t(R.string.ctl_discard_title), body = t(R.string.ctl_discard_body),
        confirmLabel = t(R.string.ctl_discard), danger = true,
        onConfirm = { confirmLeave = false; hvm.discardAll(); if (!nav.back()) nav.tab(Route.Home) }, onDismiss = { confirmLeave = false },
    )
}

/** Guest path and size of a document; recomputed only when the file content changed. */
@Composable
private fun infoOf(home: dev.pocketagent.app.hermes.HermesHome, doc: HermesDoc, version: String): String =
    remember(doc, version) { runCatching { home.docInfo(doc) }.getOrDefault("") }

private fun hermesTabTag(tab: HermesTab): String = when (tab) {
    HermesTab.SOUL -> RobotTags.HERMES_TAB_SOUL
    HermesTab.MEMORY -> RobotTags.HERMES_TAB_MEMORY
    HermesTab.USER -> RobotTags.HERMES_TAB_USER
    HermesTab.SKILLS -> RobotTags.HERMES_TAB_SKILLS
    HermesTab.CONFIG -> RobotTags.HERMES_TAB_CONFIG
    HermesTab.LOGS -> RobotTags.HERMES_TAB_LOGS
}
