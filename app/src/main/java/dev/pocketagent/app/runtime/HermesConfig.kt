package dev.pocketagent.app.runtime

import dev.pocketagent.app.util.FileUtil

/** What [HermesConfig.apply] does with the two credential files in the guest. */
enum class CredentialAction {
    /** Service-account mode: install the service account and delete the ADC file. */
    INSTALL_SA,
    /** Gcloud user credentials: install the ADC file and delete the service account. */
    INSTALL_ADC,
    /** Every other mode (and a Google mode whose file is missing): delete both. */
    REMOVE_BOTH,
    /** Practice model: neither write nor delete either file (the owner's setup is never touched by test code). */
    UNTOUCHED,
}

/** The three results of [HermesConfig.plan]: the text of `.env`, the text of `config.yaml` and what to do with the credential files. */
class GuestFiles(val env: String, val yaml: String, val credentials: CredentialAction)

/**
 * Writes Hermes's `.env` and `config.yaml` inside the rootfs's HERMES_HOME from [ProviderConfig]
 * (single source of truth, golden-file tested against the fixtures the CI smoke test boots) and
 * installs exactly the credential files the selected mode needs, removing the others.
 * In practice mode ([GuestOptions.practice]) the owner's provider setup is not read at all.
 */
object HermesConfig {
    /**
     * Pure: decides what the guest files contain. Practice gives the practice files and [CredentialAction.UNTOUCHED] (no provider key,
     * no credential file written or deleted); every other case gives today's files from [inputs].
     */
    fun plan(
        inputs: ProviderInputs, apiServerKey: String, apiPort: Int, options: GuestOptions,
        hasServiceAccount: Boolean, hasUserCredentials: Boolean,
    ): GuestFiles {
        val practicePort = options.practicePort
        if (practicePort != null) {
            return GuestFiles(
                ProviderConfig.renderPracticeEnv(apiServerKey, apiPort),
                ProviderConfig.renderPracticeYaml(practicePort, options),
                CredentialAction.UNTOUCHED,
            )
        }
        val action = when {
            inputs.mode == AuthMode.SERVICE_ACCOUNT && hasServiceAccount -> CredentialAction.INSTALL_SA
            inputs.mode == AuthMode.USER_CREDENTIALS && hasUserCredentials -> CredentialAction.INSTALL_ADC
            else -> CredentialAction.REMOVE_BOTH
        }
        return GuestFiles(ProviderConfig.renderEnv(apiServerKey, inputs, apiPort), ProviderConfig.renderYaml(inputs, options), action)
    }

    fun apply(
        paths: Paths, settings: Settings, secrets: SecretStore, apiServerKey: String, apiPort: Int,
        options: GuestOptions = GuestOptions(),
    ) {
        paths.hermesHome.mkdirs()
        val files: GuestFiles
        if (options.practice) {
            // The owner's key and credential files are not read: inputs are never built and the files stay as they are.
            files = plan(PRACTICE_INPUTS, apiServerKey, apiPort, options, hasServiceAccount = false, hasUserCredentials = false)
        } else {
            val inputs = ProviderConfig.savedInputs(settings, secrets)
            paths.guestSecretsDir.mkdirs()
            FileUtil.chmod(paths.guestSecretsDir, 448 /* 0700 */)
            files = plan(inputs, apiServerKey, apiPort, options, paths.serviceAccountFile.isFile, paths.userCredentialsFile.isFile)
        }

        // Credential files: only what the mode uses, nothing stale from another mode (practice: hands off).
        when (files.credentials) {
            CredentialAction.INSTALL_SA -> {
                FileUtil.writeAtomic(paths.guestServiceAccount, paths.serviceAccountFile.readBytes())
                paths.guestAdcFile.delete()
            }
            CredentialAction.INSTALL_ADC -> {
                paths.guestAdcFile.parentFile?.mkdirs()
                paths.guestAdcFile.parentFile?.let { FileUtil.chmod(it, 448) }
                FileUtil.writeAtomic(paths.guestAdcFile, paths.userCredentialsFile.readBytes())
                paths.guestServiceAccount.delete()
            }
            CredentialAction.REMOVE_BOTH -> {
                paths.guestServiceAccount.delete()
                paths.guestAdcFile.delete()
            }
            CredentialAction.UNTOUCHED -> {}
        }

        FileUtil.writeAtomic(paths.hermesConfig, files.yaml.toByteArray())
        FileUtil.writeAtomic(paths.hermesEnv, files.env.toByteArray())
    }

    /** Stands in for the provider inputs in practice mode: [plan] ignores it there. It holds no key and no name. */
    private val PRACTICE_INPUTS = ProviderInputs(AuthMode.CUSTOM, ApiEndpoint.STUDIO, PracticeModel.MODEL_ID, "", "")

    /**
     * The command line that starts Hermes through the launcher. [practicePort] null: today's gateway command. Otherwise the guest
     * command of [PracticeModel] (it starts the scripted model on that port and then Hermes).
     */
    fun launchCommand(launcherPath: String, practicePort: Int?): List<String> =
        if (practicePort == null) listOf("/system/bin/sh", launcherPath, "--", "hermes", "gateway", "run", "--replace", "-v")
        else listOf("/system/bin/sh", launcherPath, "--") + PracticeModel.guestCommand(practicePort)
}
