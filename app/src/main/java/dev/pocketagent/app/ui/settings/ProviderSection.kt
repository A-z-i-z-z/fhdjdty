package dev.pocketagent.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import dev.pocketagent.app.MainViewModel
import dev.pocketagent.app.R
import dev.pocketagent.app.runtime.ApiEndpoint
import dev.pocketagent.app.runtime.AuthMode
import dev.pocketagent.app.runtime.ProviderConfig
import dev.pocketagent.app.ui.components.ButtonKind
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PocketChip
import dev.pocketagent.app.ui.components.PocketDialog
import dev.pocketagent.app.ui.components.PocketSwitchRow
import dev.pocketagent.app.ui.components.PocketTextField

/** "Provider and model": everything the old Setup card did (modes, key, credential import, model, effort, region, project). */
@Composable
internal fun ProviderSection(vm: MainViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.importFromUri(it) } }
    var showPaste by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Muted(stringResource(R.string.ctl_provider_intro))
        CText(stringResource(R.string.setup_mode), size = 14.sp, weight = FontWeight.SemiBold)
        ChoiceRow(vm.authMode == AuthMode.API_KEY, stringResource(R.string.mode_api_key), { vm.selectMode(AuthMode.API_KEY) })
        ChoiceRow(vm.authMode == AuthMode.SERVICE_ACCOUNT, stringResource(R.string.mode_sa), { vm.selectMode(AuthMode.SERVICE_ACCOUNT) })
        ChoiceRow(vm.authMode == AuthMode.USER_CREDENTIALS, stringResource(R.string.mode_user), { vm.selectMode(AuthMode.USER_CREDENTIALS) })

        when (vm.authMode) {
            AuthMode.API_KEY -> {
                ChipRow {
                    PocketChip(stringResource(R.string.endpoint_studio), selected = vm.endpoint == ApiEndpoint.STUDIO, onClick = { vm.selectEndpoint(ApiEndpoint.STUDIO) })
                    PocketChip(stringResource(R.string.endpoint_express), selected = vm.endpoint == ApiEndpoint.VERTEX_EXPRESS, onClick = { vm.selectEndpoint(ApiEndpoint.VERTEX_EXPRESS) })
                }
                Muted(stringResource(if (vm.endpoint == ApiEndpoint.STUDIO) R.string.endpoint_studio_hint else R.string.endpoint_express_hint), size = 12.sp)
                PocketTextField(
                    vm.apiKeyInput, { vm.apiKeyInput = it }, Modifier.fillMaxWidth(),
                    label = stringResource(if (vm.hasStoredApiKey) R.string.field_api_key_stored else R.string.field_api_key),
                    singleLine = true, password = true,
                )
                if (vm.hasStoredApiKey) PixelButton(stringResource(R.string.btn_remove_key), onClick = { vm.clearApiKey() }, kind = ButtonKind.GHOST, icon = PixelIcon.TRASH)
            }
            AuthMode.SERVICE_ACCOUNT, AuthMode.USER_CREDENTIALS -> {
                Muted(stringResource(if (vm.authMode == AuthMode.SERVICE_ACCOUNT) R.string.sa_hint else R.string.user_hint), size = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelButton(
                        stringResource(R.string.btn_import_file),
                        onClick = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
                        kind = ButtonKind.SECONDARY, icon = PixelIcon.PLUS,
                    )
                    PixelButton(stringResource(R.string.btn_paste_json), onClick = { showPaste = true }, kind = ButtonKind.SECONDARY, icon = PixelIcon.COPY)
                }
                val have = if (vm.authMode == AuthMode.SERVICE_ACCOUNT) vm.hasServiceAccount else vm.hasUserCredentials
                Muted(if (have) stringResource(R.string.cred_imported, vm.saSummary) else stringResource(R.string.cred_none))
                if (have) PixelButton(
                    stringResource(R.string.btn_remove_cred),
                    onClick = { if (vm.authMode == AuthMode.SERVICE_ACCOUNT) vm.removeServiceAccount() else vm.removeUserCredentials() },
                    kind = ButtonKind.GHOST, icon = PixelIcon.TRASH,
                )
            }
        }

        PocketTextField(vm.model, { vm.model = it }, Modifier.fillMaxWidth(), label = stringResource(R.string.field_model), singleLine = true, monospace = true)
        ChipRow {
            ProviderConfig.suggestions(vm.authMode, vm.endpoint).forEach { id ->
                PocketChip(id, selected = vm.model == id, onClick = { vm.model = id })
            }
        }
        CText(stringResource(R.string.field_effort), size = 14.sp, weight = FontWeight.SemiBold)
        ChipRow {
            EFFORT_CHIPS.forEach { (value, label) ->
                PocketChip(stringResource(label), selected = vm.reasoningEffort == value, onClick = { vm.reasoningEffort = value })
            }
        }
        Muted(stringResource(R.string.effort_hint), size = 12.sp)

        val showRegionProject = vm.authMode != AuthMode.API_KEY || vm.endpoint == ApiEndpoint.VERTEX_EXPRESS
        if (showRegionProject) {
            PocketTextField(vm.region, { vm.region = it }, Modifier.fillMaxWidth(), label = stringResource(R.string.field_region), singleLine = true)
            PocketTextField(
                vm.projectId, { vm.projectId = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = stringResource(if (vm.authMode == AuthMode.USER_CREDENTIALS) R.string.field_project_required else R.string.field_project),
            )
        }

        val problems = vm.problems()
        problems.forEach { p ->
            ErrorText(stringResource(when (p) {
                ProviderConfig.Problem.NO_API_KEY -> R.string.problem_no_key
                ProviderConfig.Problem.NO_PROJECT_FOR_USER_CREDENTIALS -> R.string.problem_no_project
                ProviderConfig.Problem.EMPTY_MODEL -> R.string.problem_no_model
                ProviderConfig.Problem.INVALID_PROJECT -> R.string.problem_bad_project
                ProviderConfig.Problem.INVALID_REGION -> R.string.problem_bad_region
                ProviderConfig.Problem.INVALID_REASONING_EFFORT -> R.string.problem_bad_effort
            }))
        }
        val needCredFile = (vm.authMode == AuthMode.SERVICE_ACCOUNT && !vm.hasServiceAccount) || (vm.authMode == AuthMode.USER_CREDENTIALS && !vm.hasUserCredentials)
        if (needCredFile) ErrorText(stringResource(R.string.problem_no_credentials))

        PixelButton(
            stringResource(R.string.btn_save_apply), onClick = { vm.saveSettings() },
            modifier = Modifier.fillMaxWidth(), icon = PixelIcon.CHECK, enabled = problems.isEmpty(),
        )
        vm.settingsMessage?.let { Muted(stringResource(if (it == "restarting") R.string.settings_restarting else R.string.settings_saved)) }
    }
    if (showPaste) PasteDialog(vm) { showPaste = false }
}

/**
 * Paste dialog. Privacy behaviour is unchanged from the first version: the text is hidden unless "Show text" is on,
 * autocorrect is off, and it is cleared from the field right after import or when the dialog closes. A plain
 * Material field is used on purpose: it supports a hidden multi-line value regardless of the themed field.
 */
@Composable
private fun PasteDialog(vm: MainViewModel, onClose: () -> Unit) {
    var reveal by remember { mutableStateOf(false) }
    PocketDialog(
        title = stringResource(R.string.paste_title),
        onDismiss = { vm.pasteText = ""; onClose() },
        confirm = {
            PixelButton(stringResource(R.string.btn_import), enabled = vm.pasteText.isNotBlank(), onClick = { vm.importPasted(); onClose() })
        },
        dismiss = { PixelButton(stringResource(R.string.cancel), onClick = { vm.pasteText = ""; onClose() }, kind = ButtonKind.GHOST) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Muted(stringResource(R.string.paste_hint))
            OutlinedTextField(
                value = vm.pasteText, onValueChange = { vm.pasteText = it },
                minLines = 6, maxLines = 10, modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            )
            PocketSwitchRow(stringResource(R.string.paste_reveal), reveal, { reveal = it })
        }
    }
}
