package dev.pocketagent.app.ui.hermes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.hermes.TermKind
import dev.pocketagent.app.ui.LocalNavigator
import dev.pocketagent.app.ui.Route
import dev.pocketagent.app.ui.components.ButtonKind
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconButton
import dev.pocketagent.app.ui.components.PocketChip
import dev.pocketagent.app.ui.components.PocketTextField
import dev.pocketagent.app.ui.components.PocketTopBar
import dev.pocketagent.app.ui.settings.CText
import dev.pocketagent.app.ui.settings.ChipRow
import dev.pocketagent.app.ui.settings.copyText
import dev.pocketagent.app.ui.settings.isInstalled
import dev.pocketagent.app.ui.settings.mainVm
import dev.pocketagent.app.ui.settings.toast
import dev.pocketagent.app.ui.theme.Pocket

private val QUICK_COMMANDS = listOf(
    "hermes --help", "hermes status", "hermes doctor", "hermes skills list", "hermes memory --help",
    "hermes sessions list", "hermes model", "hermes config", "ls ~/.hermes", "uname -a", "python3 --version",
)

// The console is dark on every theme, like a real terminal.
private val CONSOLE_BG = Color(0xFF11100F)
private val CONSOLE_OUT = Color(0xFFE6DFD3)
private val CONSOLE_MUTED = Color(0xFF9B9082)
private val CONSOLE_ERR = Color(0xFFFF7A6B)
private val CONSOLE_CMD = Color(0xFFF2A33A)

/** A console for the full `hermes` command line and ordinary shell commands inside the Linux guest. */
@Composable
fun TerminalScreen() {
    val ctx = LocalContext.current
    val nav = LocalNavigator.current
    val term = remember { PocketApp.from(ctx).terminal }
    val vm = mainVm()
    val state by vm.state.collectAsState()
    val lines by term.lines.collectAsState()
    val running by term.running.collectAsState()
    val installed = isInstalled(state)
    val c = Pocket.colors

    var input by rememberSaveable { mutableStateOf("") }
    var histPos by remember { mutableIntStateOf(-1) }   // -1 = the line being typed, 0 = newest command
    val listState = rememberLazyListState()
    val copied = stringResource(R.string.ctl_copied)

    // A new list object arrives on every change, also when the 5000-line cap keeps the size constant.
    LaunchedEffect(lines.size, System.identityHashCode(lines), lines.lastOrNull()?.text?.length) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    fun submit(cmd: String) {
        val text = cmd.trim()
        if (text.isEmpty() || running || !installed) return
        term.run(text); input = ""; histPos = -1
    }
    fun browseHistory(older: Boolean) {
        val h = term.history
        if (h.isEmpty()) return
        histPos = if (older) minOf(histPos + 1, h.size - 1) else maxOf(histPos - 1, -1)
        input = if (histPos < 0) "" else h[h.size - 1 - histPos]
    }

    Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        PocketTopBar(
            title = stringResource(R.string.ctl_terminal_title), subtitle = stringResource(R.string.ctl_terminal_subtitle),
            onBack = { if (!nav.back()) nav.go(Route.Hermes) },
            actions = {
                PixelIconButton(
                    PixelIcon.COPY, onClick = {
                        copyText(ctx, "PocketAgent terminal", lines.joinToString("\n") { (if (it.kind == TermKind.CMD) "$ " else "") + it.text }); toast(ctx, copied)
                    },
                    contentDescription = stringResource(R.string.ctl_copy), enabled = lines.isNotEmpty(),
                )
                PixelIconButton(PixelIcon.TRASH, onClick = { term.clear() }, contentDescription = stringResource(R.string.ctl_term_clear), enabled = lines.isNotEmpty() && !running)
            },
        )

        // ---- console ----
        Box(Modifier.weight(1f).fillMaxWidth().background(CONSOLE_BG)) {
            if (lines.isEmpty()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CText(
                        stringResource(if (installed) R.string.ctl_term_welcome else R.string.ctl_term_not_installed),
                        color = CONSOLE_MUTED, size = 13.sp, mono = true,
                    )
                    if (!installed) {
                        PixelButton(stringResource(R.string.ctl_term_go_runtime), onClick = { nav.go(Route.Settings("runtime")) }, kind = ButtonKind.SECONDARY)
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(10.dp)) {
                    items(lines.size) { i ->
                        val l = lines[i]
                        val (prefix, color) = when (l.kind) {
                            TermKind.CMD -> "$ " to CONSOLE_CMD
                            TermKind.ERR -> "" to CONSOLE_ERR
                            TermKind.INFO -> "" to CONSOLE_MUTED
                            TermKind.OUT -> "" to CONSOLE_OUT
                        }
                        CText(
                            prefix + l.text, color = color, size = 12.sp, mono = true, lineHeight = 16.sp,
                        )
                    }
                }
            }
        }

        // ---- quick commands ----
        Column(Modifier.background(c.surface).padding(top = 6.dp)) {
            ChipRow(Modifier.padding(horizontal = 10.dp)) {
                QUICK_COMMANDS.forEach { cmd ->
                    PocketChip(cmd, selected = false, onClick = { submit(cmd) })
                }
            }
            CText(
                stringResource(R.string.ctl_term_hint), color = c.textMuted, size = 11.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            // ---- input ----
            Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).onPreviewKeyEvent { e ->
                        if (e.key == Key.Enter && e.type == KeyEventType.KeyDown) { submit(input); true } else false
                    },
                ) {
                    PocketTextField(
                        input, { input = it; histPos = -1 }, Modifier.fillMaxWidth(),
                        placeholder = stringResource(R.string.ctl_term_placeholder), singleLine = true, monospace = true,
                    )
                }
                PixelIconButton(PixelIcon.UP, onClick = { browseHistory(true) }, contentDescription = stringResource(R.string.ctl_term_prev), enabled = term.history.isNotEmpty())
                PixelIconButton(PixelIcon.DOWN, onClick = { browseHistory(false) }, contentDescription = stringResource(R.string.ctl_term_next), enabled = histPos >= 0)
                if (running) {
                    PixelIconButton(PixelIcon.STOP, onClick = { term.cancel() }, contentDescription = stringResource(R.string.ctl_term_cancel), tint = c.error)
                } else {
                    PixelIconButton(
                        PixelIcon.PLAY, onClick = { submit(input) }, contentDescription = stringResource(R.string.ctl_term_run),
                        tint = if (installed && input.isNotBlank()) c.accent else c.textMuted, enabled = installed && input.isNotBlank(),
                    )
                }
            }
        }
    }
}
