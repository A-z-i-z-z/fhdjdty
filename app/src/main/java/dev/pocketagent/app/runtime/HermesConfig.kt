package dev.pocketagent.app.runtime

import android.content.Context
import dev.pocketagent.app.util.FileUtil

/**
 * Produces Hermes's `.env` and `config.yaml` inside the rootfs's HERMES_HOME from the shared
 * templates in runtime/ (the CI smoke test renders the very same templates).
 */
object HermesConfig {
    /** Quotes a value for a double-quoted YAML scalar. Control characters are dropped. */
    fun yamlEscape(v: String): String =
        v.filter { it >= ' ' }.replace("\\", "\\\\").replace("\"", "\\\"")

    fun renderConfig(template: String, model: String, region: String, projectId: String): String {
        val projectLine = if (projectId.isBlank()) "" else "  project_id: \"${yamlEscape(projectId.trim())}\"\n"
        return template
            .replace("{{PROJECT_LINE}}", projectLine)
            .replace("{{MODEL}}", yamlEscape(model.trim()))
            .replace("{{REGION}}", yamlEscape(region.trim().ifEmpty { Settings.DEFAULT_REGION }))
    }

    fun renderEnv(template: String, apiKey: String, saPath: String): String =
        template.replace("{{API_KEY}}", apiKey).replace("{{SA_PATH}}", saPath)

    /** Writes both files (0600) and installs the service-account copy the guest can read. */
    fun apply(context: Context, paths: Paths, settings: Settings, apiKey: String) {
        val tplConfig = context.assets.open("hermes-config.yaml.tpl").bufferedReader().use { it.readText() }
        val tplEnv = context.assets.open("hermes.env.tpl").bufferedReader().use { it.readText() }
        paths.hermesHome.mkdirs()
        paths.guestSecretsDir.mkdirs()
        FileUtil.chmod(paths.guestSecretsDir, 448 /* 0700 */)
        if (paths.serviceAccountFile.isFile) {
            FileUtil.writeAtomic(paths.guestServiceAccount, paths.serviceAccountFile.readBytes())
        } else {
            paths.guestServiceAccount.delete()
        }
        FileUtil.writeAtomic(paths.hermesConfig,
            renderConfig(tplConfig, settings.model, settings.region, settings.projectId).toByteArray())
        FileUtil.writeAtomic(paths.hermesEnv,
            renderEnv(tplEnv, apiKey, Paths.GUEST_SERVICE_ACCOUNT).toByteArray())
    }
}
