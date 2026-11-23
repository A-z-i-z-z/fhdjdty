package dev.pocketagent.app.runtime

import dev.pocketagent.app.util.FileUtil

/**
 * Writes Hermes's `.env` and `config.yaml` inside the rootfs's HERMES_HOME from [ProviderConfig]
 * (single source of truth, golden-file tested against the fixtures the CI smoke test boots) and
 * installs exactly the credential files the selected mode needs, removing the others.
 */
object HermesConfig {
    fun apply(paths: Paths, settings: Settings, secrets: SecretStore, apiServerKey: String) {
        val inputs = settings.providerInputs(secrets.geminiApiKey())
        paths.hermesHome.mkdirs()
        paths.guestSecretsDir.mkdirs()
        FileUtil.chmod(paths.guestSecretsDir, 448 /* 0700 */)

        // Credential files: only what the mode uses, nothing stale from another mode.
        if (inputs.mode == AuthMode.SERVICE_ACCOUNT && paths.serviceAccountFile.isFile) {
            FileUtil.writeAtomic(paths.guestServiceAccount, paths.serviceAccountFile.readBytes())
        } else paths.guestServiceAccount.delete()

        if (inputs.mode == AuthMode.USER_CREDENTIALS && paths.userCredentialsFile.isFile) {
            paths.guestAdcFile.parentFile?.mkdirs()
            paths.guestAdcFile.parentFile?.let { FileUtil.chmod(it, 448) }
            FileUtil.writeAtomic(paths.guestAdcFile, paths.userCredentialsFile.readBytes())
        } else paths.guestAdcFile.delete()

        FileUtil.writeAtomic(paths.hermesConfig, ProviderConfig.renderYaml(inputs).toByteArray())
        FileUtil.writeAtomic(paths.hermesEnv, ProviderConfig.renderEnv(apiServerKey, inputs).toByteArray())
    }
}
