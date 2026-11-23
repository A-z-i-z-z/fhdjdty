package dev.pocketagent.app.ui

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.ChatLine
import dev.pocketagent.app.ImportResult
import dev.pocketagent.app.LineKind
import dev.pocketagent.app.MainViewModel
import dev.pocketagent.app.R
import dev.pocketagent.app.runtime.ApiEndpoint
import dev.pocketagent.app.runtime.AuthMode
import dev.pocketagent.app.runtime.ProviderConfig
import dev.pocketagent.app.runtime.RuntimeState
import dev.pocketagent.app.runtime.SaParse
import dev.pocketagent.app.BuildConfig
import androidx.compose.foundation.layout.fillMaxSize

@Composable
fun AppScreen(vm: MainViewModel) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val ctx = LocalContext.current
            val state by vm.state.collectAsState()

            LazyColumn(
                modifier = Modifier.statusBarsPadding().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Spacer(Modifier.height(4.dp)); Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall) }
                item { RuntimeCard(vm, state) }
                item { SetupCard(vm) }
                item { Text(stringResource(R.string.chat_title), style = MaterialTheme.typography.titleMedium) }
                items(vm.chat, key = { it.id }) { ChatBubble(it) }
                item { ChatInput(vm, state) }
                item { DebugCard(vm) }
                item { Spacer(Modifier.height(24.dp)) }
            }
            vm.importResult?.let { ImportResultDialog(it) { vm.dismissImportResult() } }
            vm.diagMessage?.let { m ->
                val text = when {
                    m.startsWith("truncated:") -> {
                        val p = m.split(":"); stringResource(R.string.diag_copied_truncated, p[2].toInt(), p[1].toInt())
                    }
                    else -> stringResource(R.string.diag_copied, m.substringAfter(":").toInt())
                }
                AlertDialog(
                    onDismissRequest = { vm.clearDiagMessage() },
                    confirmButton = { TextButton(onClick = { vm.clearDiagMessage() }) { Text(stringResource(R.string.ok)) } },
                    text = { Text(text) },
                )
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun RuntimeCard(vm: MainViewModel, state: RuntimeState) {
    val ctx = LocalContext.current
    SectionCard(stringResource(R.string.runtime_title)) {
        val (label, detail) = when (state) {
            RuntimeState.NotInstalled -> stringResource(R.string.state_not_installed) to ""
            is RuntimeState.Extracting -> stringResource(R.string.state_extracting, (state.fraction * 100).toInt()) to state.detail
            is RuntimeState.Starting -> stringResource(R.string.state_starting) to state.step
            RuntimeState.Running -> stringResource(R.string.state_running) to ""
            is RuntimeState.Stopped -> stringResource(R.string.state_stopped) to state.reason
            is RuntimeState.Error -> stringResource(R.string.state_error) to state.reason
        }
        Text(label, style = MaterialTheme.typography.titleLarge)
        if (detail.isNotEmpty()) SelectionContainer { Text(detail, fontSize = 13.sp) }
        if (state is RuntimeState.Extracting) LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth())
        else if (state is RuntimeState.Starting) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val canStart = state is RuntimeState.NotInstalled || state is RuntimeState.Stopped || state is RuntimeState.Error
            Button(onClick = { vm.start() }, enabled = canStart) {
                Text(stringResource(if (state is RuntimeState.NotInstalled) R.string.btn_install_start else R.string.btn_start))
            }
            OutlinedButton(onClick = { vm.stop() }, enabled = state.isBusyOrRunning || state is RuntimeState.Error) { Text(stringResource(R.string.btn_stop)) }
            OutlinedButton(onClick = { vm.restart() }, enabled = state == RuntimeState.Running) { Text(stringResource(R.string.btn_restart)) }
        }
        val selfTestActive by vm.selfTestActive.collectAsState()
        if (state is RuntimeState.Starting && state.step.startsWith("still starting")) {
            Text(stringResource(R.string.slow_start_hint), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        if (selfTestActive) {
            Text(stringResource(R.string.selftest_running_hint), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else if (state is RuntimeState.Error) {
            Text(stringResource(R.string.error_hint), fontSize = 13.sp)
        }
        if (!vm.ignoringBattery) {
            Text(stringResource(R.string.battery_hint), fontSize = 13.sp)
            OutlinedButton(onClick = { ctx.startActivity(vm.batteryOptimizationIntent()) }) { Text(stringResource(R.string.btn_battery)) }
        }
        Text(stringResource(R.string.build_info, BuildConfig.VERSION_NAME, BuildConfig.GIT_COMMIT), fontSize = 11.sp)
    }
}

@Composable
private fun SetupCard(vm: MainViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.importFromUri(it) } }
    var showPaste by remember { mutableStateOf(false) }
    SectionCard(stringResource(R.string.setup_title)) {
        Text(stringResource(R.string.setup_mode), style = MaterialTheme.typography.labelLarge)
        ModeRow(vm.authMode == AuthMode.API_KEY, stringResource(R.string.mode_api_key)) { vm.selectMode(AuthMode.API_KEY) }
        ModeRow(vm.authMode == AuthMode.SERVICE_ACCOUNT, stringResource(R.string.mode_sa)) { vm.selectMode(AuthMode.SERVICE_ACCOUNT) }
        ModeRow(vm.authMode == AuthMode.USER_CREDENTIALS, stringResource(R.string.mode_user)) { vm.selectMode(AuthMode.USER_CREDENTIALS) }

        when (vm.authMode) {
            AuthMode.API_KEY -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = vm.endpoint == ApiEndpoint.STUDIO, onClick = { vm.selectEndpoint(ApiEndpoint.STUDIO) },
                        label = { Text(stringResource(R.string.endpoint_studio)) })
                    FilterChip(selected = vm.endpoint == ApiEndpoint.VERTEX_EXPRESS, onClick = { vm.selectEndpoint(ApiEndpoint.VERTEX_EXPRESS) },
                        label = { Text(stringResource(R.string.endpoint_express)) })
                }
                Text(stringResource(if (vm.endpoint == ApiEndpoint.STUDIO) R.string.endpoint_studio_hint else R.string.endpoint_express_hint), fontSize = 12.sp)
                OutlinedTextField(
                    value = vm.apiKeyInput, onValueChange = { vm.apiKeyInput = it }, singleLine = true,
                    label = { Text(stringResource(if (vm.hasStoredApiKey) R.string.field_api_key_stored else R.string.field_api_key)) },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (vm.hasStoredApiKey) TextButton(onClick = { vm.clearApiKey() }) { Text(stringResource(R.string.btn_remove_key)) }
            }
            AuthMode.SERVICE_ACCOUNT, AuthMode.USER_CREDENTIALS -> {
                Text(stringResource(if (vm.authMode == AuthMode.SERVICE_ACCOUNT) R.string.sa_hint else R.string.user_hint), fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }) { Text(stringResource(R.string.btn_import_file)) }
                    OutlinedButton(onClick = { showPaste = true }) { Text(stringResource(R.string.btn_paste_json)) }
                }
                val have = if (vm.authMode == AuthMode.SERVICE_ACCOUNT) vm.hasServiceAccount else vm.hasUserCredentials
                Text(if (have) stringResource(R.string.cred_imported, vm.saSummary) else stringResource(R.string.cred_none), fontSize = 13.sp)
                if (have) TextButton(onClick = { if (vm.authMode == AuthMode.SERVICE_ACCOUNT) vm.removeServiceAccount() else vm.removeUserCredentials() }) {
                    Text(stringResource(R.string.btn_remove_cred))
                }
            }
        }

        OutlinedTextField(value = vm.model, onValueChange = { vm.model = it }, singleLine = true,
            label = { Text(stringResource(R.string.field_model)) }, modifier = Modifier.fillMaxWidth())
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(ProviderConfig.suggestions(vm.authMode, vm.endpoint)) { id ->
                FilterChip(selected = vm.model == id, onClick = { vm.model = id }, label = { Text(id, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) })
            }
        }
        val showRegionProject = vm.authMode != AuthMode.API_KEY || vm.endpoint == ApiEndpoint.VERTEX_EXPRESS
        if (showRegionProject) {
            OutlinedTextField(value = vm.region, onValueChange = { vm.region = it }, singleLine = true,
                label = { Text(stringResource(R.string.field_region)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = vm.projectId, onValueChange = { vm.projectId = it }, singleLine = true,
                label = { Text(stringResource(if (vm.authMode == AuthMode.USER_CREDENTIALS) R.string.field_project_required else R.string.field_project)) },
                modifier = Modifier.fillMaxWidth())
        }
        val problems = vm.problems()
        problems.forEach { p ->
            Text(stringResource(when (p) {
                ProviderConfig.Problem.NO_API_KEY -> R.string.problem_no_key
                ProviderConfig.Problem.NO_PROJECT_FOR_USER_CREDENTIALS -> R.string.problem_no_project
                ProviderConfig.Problem.EMPTY_MODEL -> R.string.problem_no_model
                ProviderConfig.Problem.INVALID_PROJECT -> R.string.problem_bad_project
                ProviderConfig.Problem.INVALID_REGION -> R.string.problem_bad_region
            }), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        val needCredFile = (vm.authMode == AuthMode.SERVICE_ACCOUNT && !vm.hasServiceAccount) || (vm.authMode == AuthMode.USER_CREDENTIALS && !vm.hasUserCredentials)
        if (needCredFile) Text(stringResource(R.string.problem_no_credentials), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        Button(onClick = { vm.saveSettings() }, enabled = problems.isEmpty()) { Text(stringResource(R.string.btn_save_apply)) }
        vm.settingsMessage?.let { Text(stringResource(if (it == "restarting") R.string.settings_restarting else R.string.settings_saved), fontSize = 13.sp) }
    }
    if (showPaste) PasteDialog(vm) { showPaste = false }
}

@Composable
private fun ModeRow(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, Modifier.padding(start = 4.dp), fontSize = 14.sp)
    }
}

@Composable
private fun PasteDialog(vm: MainViewModel, onClose: () -> Unit) {
    var reveal by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { vm.pasteText = ""; onClose() },
        title = { Text(stringResource(R.string.paste_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.paste_hint), fontSize = 13.sp)
                OutlinedTextField(
                    value = vm.pasteText, onValueChange = { vm.pasteText = it },
                    minLines = 6, maxLines = 10, modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    visualTransformation = if (reveal) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = reveal, onCheckedChange = { reveal = it }); Text(stringResource(R.string.paste_reveal), Modifier.padding(start = 8.dp), fontSize = 13.sp)
                }
            }
        },
        confirmButton = { TextButton(enabled = vm.pasteText.isNotBlank(), onClick = { vm.importPasted(); onClose() }) { Text(stringResource(R.string.btn_import)) } },
        dismissButton = { TextButton(onClick = { vm.pasteText = ""; onClose() }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ImportResultDialog(r: ImportResult, onClose: () -> Unit) {
    val body = when (r) {
        is ImportResult.ServiceAccount -> stringResource(R.string.import_ok_sa, r.projectId, r.maskedEmail) + if (r.repaired) "\n" + stringResource(R.string.import_repaired) else ""
        is ImportResult.UserCredentials -> stringResource(R.string.import_ok_user, r.masked) + if (r.repaired) "\n" + stringResource(R.string.import_repaired) else ""
        is ImportResult.Failure -> when (r.code) {
            SaParse.Code.EMPTY -> stringResource(R.string.import_err_empty)
            SaParse.Code.NOT_JSON -> stringResource(R.string.import_err_not_json, r.detail)
            SaParse.Code.NOT_OBJECT -> stringResource(R.string.import_err_not_object)
            SaParse.Code.WRONG_TYPE -> stringResource(R.string.import_err_type, r.detail)
            SaParse.Code.MISSING_FIELDS -> stringResource(R.string.import_err_missing, r.detail)
            SaParse.Code.BAD_PRIVATE_KEY -> stringResource(R.string.import_err_key)
            null -> stringResource(R.string.import_err_unreadable)
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(if (r is ImportResult.Failure) R.string.import_failed else R.string.import_done)) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.ok)) } },
    )
}

@Composable
private fun ChatBubble(line: ChatLine) {
    val (prefix, color) = when (line.kind) {
        LineKind.USER -> stringResource(R.string.chat_you) to MaterialTheme.colorScheme.primary
        LineKind.ASSISTANT -> stringResource(R.string.chat_agent) to MaterialTheme.colorScheme.onSurface
        LineKind.TOOL -> stringResource(R.string.chat_tool) to MaterialTheme.colorScheme.tertiary
        LineKind.ERROR -> stringResource(R.string.chat_error) to MaterialTheme.colorScheme.error
        LineKind.INFO -> "" to MaterialTheme.colorScheme.outline
    }
    SelectionContainer {
        Text(
            if (prefix.isEmpty()) line.text else "$prefix ${line.text}",
            color = color,
            fontFamily = if (line.kind == LineKind.TOOL || line.kind == LineKind.ERROR) FontFamily.Monospace else FontFamily.Default,
            fontSize = if (line.kind == LineKind.TOOL) 12.sp else 15.sp,
        )
    }
}

@Composable
private fun ChatInput(vm: MainViewModel, state: RuntimeState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state == RuntimeState.Running && vm.savedConfigIncomplete()) Text(stringResource(R.string.chat_needs_setup), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        OutlinedTextField(value = vm.chatInput, onValueChange = { vm.chatInput = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(if (state == RuntimeState.Running) R.string.chat_hint else R.string.chat_hint_not_running)) },
            minLines = 1, maxLines = 5)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.send() }, enabled = state == RuntimeState.Running && !vm.sending && vm.chatInput.isNotBlank() && !vm.savedConfigIncomplete()) { Text(stringResource(R.string.btn_send)) }
            if (vm.sending) OutlinedButton(onClick = { vm.cancelSend() }) { Text(stringResource(R.string.btn_cancel_request)) }
            OutlinedButton(onClick = { vm.newChat() }, enabled = !vm.sending) { Text(stringResource(R.string.btn_new_chat)) }
        }
    }
}

@Composable
private fun DebugCard(vm: MainViewModel) {
    val ctx = LocalContext.current
    SectionCard(stringResource(R.string.debug_title)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = vm.noSeccomp, onCheckedChange = { vm.updateNoSeccomp(it) })
            Column(Modifier.padding(start = 8.dp)) {
                Text(stringResource(R.string.debug_no_seccomp))
                Text(stringResource(R.string.debug_no_seccomp_hint), fontSize = 12.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.copyDiagnostics() }) { Text(stringResource(R.string.btn_copy_diag)) }
            OutlinedButton(onClick = { vm.shareDiagnosticsIntent()?.let { ctx.startActivity(it) } }) { Text(stringResource(R.string.btn_share_diag)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val selfTestActive by vm.selfTestActive.collectAsState()
            OutlinedButton(onClick = { vm.runSelfTest() }, enabled = !selfTestActive) { Text(stringResource(R.string.btn_selftest)) }
            OutlinedButton(onClick = { vm.resetRuntime() }) { Text(stringResource(R.string.btn_reset_runtime)) }
        }
        val selfTestText by vm.selfTestText.collectAsState()
        if (selfTestText.isNotEmpty()) SelectionContainer { Text(selfTestText, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
    }
}
