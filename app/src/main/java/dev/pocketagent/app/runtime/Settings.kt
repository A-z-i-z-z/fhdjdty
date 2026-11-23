package dev.pocketagent.app.runtime

import android.content.Context
import android.content.SharedPreferences

/** Non-secret user settings (auth mode, model id, region, project id, debug toggles). */
class Settings(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var authMode: AuthMode
        get() = try { AuthMode.valueOf(sp.getString("auth_mode", AuthMode.API_KEY.name)!!) } catch (_: Exception) { AuthMode.API_KEY }
        set(v) = sp.edit().putString("auth_mode", v.name).apply()

    var endpoint: ApiEndpoint
        get() = try { ApiEndpoint.valueOf(sp.getString("endpoint", ApiEndpoint.STUDIO.name)!!) } catch (_: Exception) { ApiEndpoint.STUDIO }
        set(v) = sp.edit().putString("endpoint", v.name).apply()

    private fun modelKey(mode: AuthMode, endpoint: ApiEndpoint) = when (mode) {
        AuthMode.API_KEY -> if (endpoint == ApiEndpoint.STUDIO) "model_studio" else "model_express"
        else -> "model_vertex"
    }

    fun modelFor(mode: AuthMode, endpoint: ApiEndpoint): String =
        sp.getString(modelKey(mode, endpoint), null)?.takeIf { it.isNotBlank() } ?: ProviderConfig.defaultModel(mode, endpoint)

    fun setModelFor(mode: AuthMode, endpoint: ApiEndpoint, v: String) =
        sp.edit().putString(modelKey(mode, endpoint), v.trim()).apply()

    /** Model of the currently selected mode. */
    val model: String get() = modelFor(authMode, endpoint)

    var region: String
        get() = sp.getString("region", DEFAULT_REGION) ?: DEFAULT_REGION
        set(v) = sp.edit().putString("region", v.trim()).apply()

    /** Optional for service accounts/express mode, required for user credentials. */
    var projectId: String
        get() = sp.getString("project_id", "") ?: ""
        set(v) = sp.edit().putString("project_id", v.trim()).apply()

    /** PROOT_NO_SECCOMP=1: slower, but avoids Android seccomp killing syscalls proot's filter interferes with. */
    var noSeccomp: Boolean
        get() = sp.getBoolean("no_seccomp", false)
        set(v) = sp.edit().putBoolean("no_seccomp", v).apply()

    /** Non-secret facts about imported credentials, for display only. */
    var saProjectId: String
        get() = sp.getString("sa_project", "") ?: ""
        set(v) = sp.edit().putString("sa_project", v).apply()

    var saMaskedEmail: String
        get() = sp.getString("sa_email_masked", "") ?: ""
        set(v) = sp.edit().putString("sa_email_masked", v).apply()

    var userCredMasked: String
        get() = sp.getString("user_cred_masked", "") ?: ""
        set(v) = sp.edit().putString("user_cred_masked", v).apply()

    /** Hermes `agent.reasoning_effort`; "" = not set. An unknown stored value reads as "not set". */
    var reasoningEffort: String
        get() = (sp.getString("reasoning_effort", "") ?: "").takeIf { it in ProviderConfig.REASONING_EFFORTS } ?: ""
        set(v) = sp.edit().putString("reasoning_effort", v).apply()

    fun providerInputs(geminiApiKey: String): ProviderInputs =
        ProviderInputs(authMode, endpoint, model, region, projectId, geminiApiKey, reasoningEffort)

    companion object {
        const val DEFAULT_REGION = "global"
    }
}
