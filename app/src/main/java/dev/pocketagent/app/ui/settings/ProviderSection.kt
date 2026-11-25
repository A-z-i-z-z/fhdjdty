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
import dev.pocketagent.app.ui.text.t
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

/** The explanation shown under the provider chips for a non-Google provider. */
private fun providerHint(mode: AuthMode): Int = when (mode) {
    AuthMode.OPENROUTER -> R.string.prov_hint_openrouter
    AuthMode.ANTHROPIC -> R.string.prov_hint_anthropic
    AuthMode.OPENAI -> R.string.prov_hint_openai
    AuthMode.CUSTOM -> R.string.prov_hint_custom
    AuthMode.API_KEY, AuthMode.SERVICE_ACCOUNT, AuthMode.USER_CREDENTIALS -> R.string.ctl_provider_intro
}

/** The label of the API key field of [mode]. */
private fun providerKeyLabel(mode: AuthMode): Int = when (mode) {
    AuthMode.OPENROUTER -> R.string.prov_field_key_openrouter
    AuthMode.ANTHROPIC -> R.string.prov_field_key_anthropic
    AuthMode.OPENAI -> R.string.prov_field_key_openai
    AuthMode.CUSTOM -> R.string.prov_field_key_custom
    AuthMode.API_KEY, AuthMode.SERVICE_ACCOUNT, AuthMode.USER_CREDENTIALS -> R.string.field_api_key
}

/**
 * "Provider and model": the provider picker (Google first, then OpenRouter, Anthropic, OpenAI and a custom endpoint), then
 * that provider's fields (key, import, model, effort, region, project).
 */
@Composable
internal fun ProviderSection(vm: MainViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.importFromUri(it) } }
    var showPaste by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // The intro names Gemini: only the Google modes show it (the other providers have their own prov_hint_* text below).
        if (vm.authMode.google) Muted(t(R.string.ctl_provider_intro))
        // Provider first: Google (all three Gemini routes, unchanged) stays first, then the other providers.
        CText(t(R.string.prov_pick), size = 14.sp, weight = FontWeight.SemiBold)
        ChipRow {
            PocketChip(t(R.string.prov_google), selected = vm.authMode.google, onClick = { if (!vm.authMode.google) vm.selectMode(vm.lastGoogleMode) })
            PocketChip(t(R.string.prov_openrouter), selected = vm.authMode == AuthMode.OPENROUTER, onClick = { vm.selectMode(AuthMode.OPENROUTER) })
            PocketChip(t(R.string.prov_anthropic), selected = vm.authMode == AuthMode.ANTHROPIC, onClick = { vm.selectMode(AuthMode.ANTHROPIC) })
            PocketChip(t(R.string.prov_openai), selected = vm.authMode == AuthMode.OPENAI, onClick = { vm.selectMode(AuthMode.OPENAI) })
            PocketChip(t(R.string.prov_custom), selected = vm.authMode == AuthMode.CUSTOM, onClick = { vm.selectMode(AuthMode.CUSTOM) })
        }
        Muted(t(R.string.prov_keys_note), size = 12.sp)

        if (vm.authMode.google) {
            CText(t(R.string.setup_mode), size = 14.sp, weight = FontWeight.SemiBold)
            ChoiceRow(vm.authMode == AuthMode.API_KEY, t(R.string.mode_api_key), { vm.selectMode(AuthMode.API_KEY) })
            ChoiceRow(vm.authMode == AuthMode.SERVICE_ACCOUNT, t(R.string.mode_sa), { vm.selectMode(AuthMode.SERVICE_ACCOUNT) })
            ChoiceRow(vm.authMode == AuthMode.USER_CREDENTIALS, t(R.string.mode_user), { vm.selectMode(AuthMode.USER_CREDENTIALS) })
        }

        when (vm.authMode) {
            AuthMode.API_KEY -> {
                ChipRow {
                    PocketChip(t(R.string.endpoint_studio), selected = vm.endpoint == ApiEndpoint.STUDIO, onClick = { vm.selectEndpoint(ApiEndpoint.STUDIO) })
                    PocketChip(t(R.string.endpoint_express), selected = vm.endpoint == ApiEndpoint.VERTEX_EXPRESS, onClick = { vm.selectEndpoint(ApiEndpoint.VERTEX_EXPRESS) })
                }
                Muted(t(if (vm.endpoint == ApiEndpoint.STUDIO) R.string.endpoint_studio_hint else R.string.endpoint_express_hint), size = 12.sp)
                PocketTextField(
                    vm.apiKeyInput, { vm.apiKeyInput = it }, Modifier.fillMaxWidth(),
                    label = t(if (vm.hasStoredApiKey) R.string.field_api_key_stored else R.string.field_api_key),
                    singleLine = true, password = true,
                )
                if (vm.hasStoredApiKey) PixelButton(t(R.string.btn_remove_key), onClick = { vm.clearApiKey() }, kind = ButtonKind.GHOST, icon = PixelIcon.TRASH)
            }
            AuthMode.SERVICE_ACCOUNT, AuthMode.USER_CREDENTIALS -> {
                Muted(t(if (vm.authMode == AuthMode.SERVICE_ACCOUNT) R.string.sa_hint else R.string.user_hint), size = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelButton(
                        t(R.string.btn_import_file),
                        onClick = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
                        kind = ButtonKind.SECONDARY, icon = PixelIcon.PLUS,
                    )
                    PixelButton(t(R.string.btn_paste_json), onClick = { showPaste = true }, kind = ButtonKind.SECONDARY, icon = PixelIcon.COPY)
                }
                val have = if (vm.authMode == AuthMode.SERVICE_ACCOUNT) vm.hasServiceAccount else vm.hasUserCredentials
                Muted(if (have) t(R.string.cred_imported, vm.saSummary) else t(R.string.cred_none))
                if (have) PixelButton(
                    t(R.string.btn_remove_cred),
                    onClick = { if (vm.authMode == AuthMode.SERVICE_ACCOUNT) vm.removeServiceAccount() else vm.removeUserCredentials() },
                    kind = ButtonKind.GHOST, icon = PixelIcon.TRASH,
                )
            }
            AuthMode.OPENROUTER, AuthMode.ANTHROPIC, AuthMode.OPENAI, AuthMode.CUSTOM -> {
                Muted(t(providerHint(vm.authMode)), size = 12.sp)
                if (vm.authMode == AuthMode.CUSTOM) {
                    PocketTextField(
                        vm.customBaseUrl, { vm.customBaseUrl = it }, Modifier.fillMaxWidth(),
                        label = t(R.string.prov_field_base_url), placeholder = t(R.string.prov_base_url_placeholder),
                        singleLine = true, monospace = true,
                    )
                    if (vm.customBaseUrl.trim().startsWith("http://", ignoreCase = true) && ProviderConfig.normalizeBaseUrl(vm.customBaseUrl) != null) {
                        Muted(t(R.string.prov_http_note), size = 12.sp)
                    }
                }
                // The key is never shown again after saving: "stored" + remove, exactly like the Gemini key.
                PocketTextField(
                    vm.apiKeyInput, { vm.apiKeyInput = it }, Modifier.fillMaxWidth(),
                    label = t(if (vm.hasStoredApiKey) R.string.field_api_key_stored else providerKeyLabel(vm.authMode)),
                    singleLine = true, password = true,
                )
                if (vm.hasStoredApiKey) PixelButton(t(R.string.btn_remove_key), onClick = { vm.clearApiKey() }, kind = ButtonKind.GHOST, icon = PixelIcon.TRASH)
            }
        }

        PocketTextField(vm.model, { vm.model = it }, Modifier.fillMaxWidth(), label = t(R.string.field_model), singleLine = true, monospace = true)
        val suggestions = ProviderConfig.suggestions(vm.authMode, vm.endpoint)
        if (suggestions.isNotEmpty()) ChipRow {
            suggestions.forEach { id ->
                PocketChip(id, selected = vm.model == id, onClick = { vm.model = id })
            }
        }
        CText(t(R.string.field_effort), size = 14.sp, weight = FontWeight.SemiBold)
        ChipRow {
            EFFORT_CHIPS.forEach { (value, label) ->
                PocketChip(t(label), selected = vm.reasoningEffort == value, onClick = { vm.reasoningEffort = value })
            }
        }
        Muted(t(R.string.effort_hint), size = 12.sp)

        val showRegionProject = vm.authMode.google && (vm.authMode != AuthMode.API_KEY || vm.endpoint == ApiEndpoint.VERTEX_EXPRESS)
        if (showRegionProject) {
            PocketTextField(vm.region, { vm.region = it }, Modifier.fillMaxWidth(), label = t(R.string.field_region), singleLine = true)
            PocketTextField(
                vm.projectId, { vm.projectId = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = t(if (vm.authMode == AuthMode.USER_CREDENTIALS) R.string.field_project_required else R.string.field_project),
            )
        }

        val problems = vm.problems()
        problems.forEach { p ->
            ErrorText(t(when (p) {
                ProviderConfig.Problem.NO_API_KEY -> if (vm.authMode.google) R.string.problem_no_key else R.string.prov_problem_no_key
                ProviderConfig.Problem.NO_PROJECT_FOR_USER_CREDENTIALS -> R.string.problem_no_project
                ProviderConfig.Problem.EMPTY_MODEL -> R.string.problem_no_model
                ProviderConfig.Problem.INVALID_PROJECT -> R.string.problem_bad_project
                ProviderConfig.Problem.INVALID_REGION -> R.string.problem_bad_region
                ProviderConfig.Problem.INVALID_REASONING_EFFORT -> R.string.problem_bad_effort
                ProviderConfig.Problem.INVALID_API_KEY_FORMAT -> R.string.prov_problem_bad_key
                ProviderConfig.Problem.NO_BASE_URL -> R.string.prov_problem_no_url
                ProviderConfig.Problem.INVALID_BASE_URL -> R.string.prov_problem_bad_url
                ProviderConfig.Problem.NO_CREDENTIALS -> R.string.problem_no_credentials
            }))
        }
        val needCredFile = (vm.authMode == AuthMode.SERVICE_ACCOUNT && !vm.hasServiceAccount) || (vm.authMode == AuthMode.USER_CREDENTIALS && !vm.hasUserCredentials)
        if (needCredFile) ErrorText(t(R.string.problem_no_credentials))

        PixelButton(
            t(R.string.btn_save_apply), onClick = { vm.saveSettings() },
            modifier = Modifier.fillMaxWidth(), icon = PixelIcon.CHECK, enabled = problems.isEmpty(),
        )
        vm.settingsMessage?.let { Muted(t(if (it == "restarting") R.string.settings_restarting else R.string.settings_saved)) }
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
        title = t(R.string.paste_title),
        onDismiss = { vm.pasteText = ""; onClose() },
        confirm = {
            PixelButton(t(R.string.btn_import), enabled = vm.pasteText.isNotBlank(), onClick = { vm.importPasted(); onClose() })
        },
        dismiss = { PixelButton(t(R.string.cancel), onClick = { vm.pasteText = ""; onClose() }, kind = ButtonKind.GHOST) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Muted(t(R.string.paste_hint))
            OutlinedTextField(
                value = vm.pasteText, onValueChange = { vm.pasteText = it },
                minLines = 6, maxLines = 10, modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            )
            PocketSwitchRow(t(R.string.paste_reveal), reveal, { reveal = it })
        }
    }
}
