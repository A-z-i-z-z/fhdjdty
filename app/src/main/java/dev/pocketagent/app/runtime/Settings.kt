package dev.pocketagent.app.runtime

import android.content.Context
import android.content.SharedPreferences

/** Non-secret user settings (model id, region, project id, debug toggles). */
class Settings(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var model: String
        get() = sp.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) = sp.edit().putString("model", v.trim()).apply()

    var region: String
        get() = sp.getString("region", DEFAULT_REGION) ?: DEFAULT_REGION
        set(v) = sp.edit().putString("region", v.trim()).apply()

    /** Optional; blank means "use the project embedded in the service account". */
    var projectId: String
        get() = sp.getString("project_id", "") ?: ""
        set(v) = sp.edit().putString("project_id", v.trim()).apply()

    /** PROOT_NO_SECCOMP=1: slower, but avoids Android seccomp killing syscalls proot's filter interferes with. */
    var noSeccomp: Boolean
        get() = sp.getBoolean("no_seccomp", false)
        set(v) = sp.edit().putBoolean("no_seccomp", v).apply()

    /** Start the runtime automatically when the app opens and everything is ready. */
    var autoStart: Boolean
        get() = sp.getBoolean("auto_start", false)
        set(v) = sp.edit().putBoolean("auto_start", v).apply()

    /** Non-secret facts about the imported service account, for display only. */
    var saProjectId: String
        get() = sp.getString("sa_project", "") ?: ""
        set(v) = sp.edit().putString("sa_project", v).apply()

    var saMaskedEmail: String
        get() = sp.getString("sa_email_masked", "") ?: ""
        set(v) = sp.edit().putString("sa_email_masked", v).apply()

    companion object {
        // Hermes's own Vertex catalog (hermes_cli/models_catalog_static.py) lists
        // google/gemini-4-argon as the Gemini 4 model; its silent default is google/gemini-3.8-flash.
        const val DEFAULT_MODEL = "google/gemini-4-argon"
        const val DEFAULT_REGION = "global"
        val MODEL_SUGGESTIONS = listOf(
            "google/gemini-4-argon", "google/gemini-3.8-flash", "google/gemini-3.7-flash",
            "google/gemini-3.1-pro-preview", "google/gemini-2.5-pro",
        )
    }
}
