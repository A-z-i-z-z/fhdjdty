package dev.pocketagent.app

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pocketagent.app.diag.Diagnostics
import dev.pocketagent.app.runtime.ApiEndpoint
import dev.pocketagent.app.runtime.AuthMode
import dev.pocketagent.app.runtime.ProviderConfig
import dev.pocketagent.app.runtime.ProviderInputs
import dev.pocketagent.app.runtime.RuntimeState
import dev.pocketagent.app.runtime.SaParse
import dev.pocketagent.app.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Result of an import attempt; the UI maps it to string resources. Holds no secret values. */
sealed class ImportResult {
    data class ServiceAccount(val projectId: String, val maskedEmail: String, val repaired: Boolean) : ImportResult()
    data class UserCredentials(val masked: String, val repaired: Boolean) : ImportResult()
    data class Failure(val code: SaParse.Code?, val detail: String) : ImportResult()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val pa = PocketApp.from(app)  // the chat engine and repository live there now (agent CHAT/ENGINE)
    val runtime = pa.runtime
    val diagnostics: Diagnostics = pa.diagnostics
    val state: StateFlow<RuntimeState> = runtime.state
    private val settings = runtime.settings
    private val secrets = runtime.secrets

    // ---- setup form -------------------------------------------------------------------------------
    var authMode by mutableStateOf(settings.authMode); private set
    var endpoint by mutableStateOf(settings.endpoint); private set
    var model by mutableStateOf(settings.model)
    var region by mutableStateOf(settings.region)
    var projectId by mutableStateOf(settings.projectId)
    var reasoningEffort by mutableStateOf(settings.reasoningEffort)   // "" = default (not written to config.yaml)
    var apiKeyInput by mutableStateOf("")                 // never pre-filled; cleared after saving
    var hasStoredApiKey by mutableStateOf(secrets.hasGeminiApiKey); private set
    var hasServiceAccount by mutableStateOf(secrets.hasServiceAccount); private set
    var hasUserCredentials by mutableStateOf(secrets.hasUserCredentials); private set
    var saSummary by mutableStateOf(summary()); private set
    var importResult by mutableStateOf<ImportResult?>(null); private set
    var pasteText by mutableStateOf("")                   // cleared right after every import attempt
    var settingsMessage by mutableStateOf<String?>(null); private set

    private fun summary() = listOfNotNull(
        settings.saProjectId.takeIf { it.isNotEmpty() && secrets.hasServiceAccount }?.let { "SA project $it, ${settings.saMaskedEmail}" },
        settings.userCredMasked.takeIf { it.isNotEmpty() && secrets.hasUserCredentials },
    ).joinToString(" | ")

    fun selectMode(m: AuthMode) { authMode = m; model = settings.modelFor(m, endpoint) }
    fun selectEndpoint(e: ApiEndpoint) { endpoint = e; model = settings.modelFor(authMode, e) }

    /** Pure function of in-memory state (called during composition: no file or binder access here). */
    fun problems() = ProviderConfig.problems(ProviderInputs(
        authMode, endpoint, model, region, projectId,
        geminiApiKey = apiKeyInput.ifBlank { if (hasStoredApiKey) "stored" else "" },
        reasoningEffort = reasoningEffort))

    /** True when the saved configuration lacks credentials/valid fields, so chatting would fail. */
    fun savedConfigIncomplete(): Boolean = problemsOfSaved

    private var problemsOfSaved by mutableStateOf(ProviderConfig.problems(settings.providerInputs(secrets.geminiApiKey())).isNotEmpty())

    /** Persists the form. Returns the problems found (settings are still saved). Restarts a running gateway. */
    fun saveSettings(): List<ProviderConfig.Problem> {
        settings.authMode = authMode; settings.endpoint = endpoint
        settings.setModelFor(authMode, endpoint, model)
        settings.region = region; settings.projectId = projectId; settings.reasoningEffort = reasoningEffort
        if (apiKeyInput.isNotBlank()) { secrets.setGeminiApiKey(apiKeyInput); apiKeyInput = ""; hasStoredApiKey = secrets.hasGeminiApiKey }
        val probs = ProviderConfig.problems(settings.providerInputs(secrets.geminiApiKey()))
        problemsOfSaved = probs.isNotEmpty()
        AppLog.i("Setup", "settings saved: ${ProviderConfig.describe(settings.providerInputs(secrets.geminiApiKey()))} problems=$probs")
        if (state.value == RuntimeState.Running || state.value is RuntimeState.Starting) {
            AppLog.i("Setup", "runtime active: restarting to apply settings")
            restart()
            settingsMessage = "restarting"
        } else settingsMessage = "saved"
        return probs
    }

    fun clearApiKey() {
        secrets.setGeminiApiKey(""); hasStoredApiKey = false
        problemsOfSaved = true
        // Do not leave the old key in the guest .env until the next start.
        viewModelScope.launch(Dispatchers.IO) { runtime.rewriteGuestConfigIfInstalled() }
    }
    fun removeServiceAccount() { secrets.removeServiceAccount(); hasServiceAccount = false; saSummary = summary(); recomputeSavedProblems() }
    fun removeUserCredentials() { secrets.removeUserCredentials(); hasUserCredentials = false; saSummary = summary(); recomputeSavedProblems() }

    /** Re-evaluates the SAVED configuration (what chat will use), e.g. after a credential import switched the saved mode. */
    private fun recomputeSavedProblems() {
        problemsOfSaved = ProviderConfig.problems(settings.providerInputs(secrets.geminiApiKey())).isNotEmpty()
    }

    // ---- credential import ---------------------------------------------------------------------------
    fun importFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val text = try {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { s ->
                    val bytes = dev.pocketagent.app.util.FileUtil.readLimited(s, 256 * 1024 + 1)
                    if (bytes.size > 256 * 1024) null else String(bytes, Charsets.UTF_8)
                }
            } catch (e: Exception) { AppLog.w("Import", "could not read picked file: ${e.javaClass.simpleName}"); null }
            handleImport(text)
        }
    }

    fun importPasted() {
        val text = pasteText
        pasteText = "" // clear immediately, whatever happens
        viewModelScope.launch(Dispatchers.IO) { handleImport(text) }
    }

    private fun handleImport(text: String?) {
        val r = if (text == null) ImportResult.Failure(null, "unreadable") else when (val p = secrets.importCredentials(text)) {
            is SaParse.Ok -> {
                hasServiceAccount = true; authMode = AuthMode.SERVICE_ACCOUNT; model = settings.modelFor(authMode, endpoint)
                settings.authMode = authMode
                ImportResult.ServiceAccount(p.projectId, p.maskedEmail, p.repaired)
            }
            is SaParse.OkUser -> {
                hasUserCredentials = true; authMode = AuthMode.USER_CREDENTIALS; model = settings.modelFor(authMode, endpoint)
                settings.authMode = authMode
                ImportResult.UserCredentials(p.masked, p.repaired)
            }
            is SaParse.Fail -> ImportResult.Failure(p.code, p.detail)
        }
        saSummary = summary()
        recomputeSavedProblems()
        importResult = r
    }

    fun dismissImportResult() { importResult = null }

    // ---- runtime ------------------------------------------------------------------------------------------
    fun start() = runtime.start()
    fun stop() = runtime.stop()
    fun restart() = runtime.restart()
    fun resetRuntime() = runtime.resetRootfs()
    var noSeccomp by mutableStateOf(settings.noSeccomp); private set
    fun updateNoSeccomp(v: Boolean) { settings.noSeccomp = v; noSeccomp = v; AppLog.i("Debug", "noSeccomp=$v") }
    val selfTestActive: StateFlow<Boolean> = runtime.selfTestActive
    val selfTestText: StateFlow<String> = runtime.selfTestText
    fun runSelfTest() = runtime.selfTest(auto = false)

    var ignoringBattery by mutableStateOf(true); private set
    /** Called from onResume (not during composition). */
    fun refreshBattery() {
        val pm = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as PowerManager
        ignoringBattery = pm.isIgnoringBatteryOptimizations(getApplication<Application>().packageName)
    }

    fun batteryOptimizationIntent(): Intent {
        val pkg = getApplication<Application>().packageName
        return try {
            Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } catch (_: Exception) {
            Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    // ---- diagnostics ------------------------------------------------------------------------------------------
    var diagMessage by mutableStateOf<String?>(null); private set

    fun copyDiagnostics() {
        viewModelScope.launch(Dispatchers.IO) {
            val r = diagnostics.buildForClipboard()
            val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Agirî diagnostics", r.text))
            diagMessage = if (r.truncated) "truncated:${r.fullLength}:${r.text.length}" else "copied:${r.text.length}"
        }
    }

    fun shareDiagnosticsIntent(): Intent? = try {
        val app = getApplication<Application>()
        val f = diagnostics.writeFullToFile()
        val uri = FileProvider.getUriForFile(app, app.packageName + ".fileprovider", f)
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, "Agirî diagnostics").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            null,
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } catch (e: Exception) { AppLog.e("Diag", "share failed", e); null }

    fun clearDiagMessage() { diagMessage = null }
}
