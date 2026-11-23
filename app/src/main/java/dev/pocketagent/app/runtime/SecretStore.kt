package dev.pocketagent.app.runtime

import dev.pocketagent.app.diag.Diag
import dev.pocketagent.app.util.AppLog
import dev.pocketagent.app.util.FileUtil
import java.security.SecureRandom

/**
 * Secrets, all in app-private storage as 0600 files: the API-server key (random per install), the
 * Gemini API key, the imported service-account JSON and the imported authorized_user JSON.
 */
class SecretStore(private val paths: Paths, private val settings: Settings) {

    init { FileUtil.chmod(paths.secrets.also { it.mkdirs() }, 448 /* 0700 */); registerKnownSecrets() }

    @Synchronized
    fun apiKey(): String {
        val f = paths.apiKeyFile
        if (f.isFile) {
            val k = f.readText().trim()
            if (k.length >= 32) { Diag.redactor.addSecret("api-server-key", k); return k }
        }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val key = bytes.joinToString("") { "%02x".format(it) }
        FileUtil.writeAtomic(f, key.toByteArray())
        Diag.redactor.addSecret("api-server-key", key)
        AppLog.i("Secrets", "generated a new API server key (length ${key.length})")
        return key
    }

    // ---- Gemini API key ---------------------------------------------------------------------------
    fun geminiApiKey(): String = if (paths.geminiKeyFile.isFile) paths.geminiKeyFile.readText().trim() else ""
    val hasGeminiApiKey: Boolean get() = geminiApiKey().isNotEmpty()

    fun setGeminiApiKey(raw: String) {
        val k = raw.filterNot { it.isWhitespace() }
        if (k.isEmpty()) { paths.geminiKeyFile.delete(); return }
        FileUtil.writeAtomic(paths.geminiKeyFile, k.toByteArray())
        Diag.redactor.addSecret("gemini-api-key", k)
        AppLog.i("Secrets", "Gemini API key stored (length ${k.length})")
    }

    // ---- credential JSON ----------------------------------------------------------------------------
    val hasServiceAccount: Boolean get() = paths.serviceAccountFile.isFile
    val hasUserCredentials: Boolean get() = paths.userCredentialsFile.isFile

    /** Validates and stores either kind of credentials file. Never logs the content. */
    fun importCredentials(raw: String): SaParse {
        val r = ServiceAccountParser.parse(raw)
        when (r) {
            is SaParse.Ok -> {
                FileUtil.writeAtomic(paths.serviceAccountFile, r.jsonText.toByteArray(Charsets.UTF_8))
                settings.saProjectId = r.projectId
                settings.saMaskedEmail = r.maskedEmail
                register(r)
                AppLog.i("Secrets", "service account imported (project ${r.projectId}, repaired=${r.repaired})")
            }
            is SaParse.OkUser -> {
                FileUtil.writeAtomic(paths.userCredentialsFile, r.jsonText.toByteArray(Charsets.UTF_8))
                settings.userCredMasked = r.masked
                register(r)
                AppLog.i("Secrets", "authorized_user credentials imported (repaired=${r.repaired})")
            }
            is SaParse.Fail -> AppLog.w("Secrets", "credentials rejected: ${r.code} ${r.detail}")
        }
        return r
    }

    fun removeServiceAccount() {
        paths.serviceAccountFile.delete(); paths.guestServiceAccount.delete()
        settings.saProjectId = ""; settings.saMaskedEmail = ""
    }

    fun removeUserCredentials() {
        paths.userCredentialsFile.delete(); paths.guestAdcFile.delete()
        settings.userCredMasked = ""
    }

    private fun registerKnownSecrets() {
        try {
            if (paths.apiKeyFile.isFile) Diag.redactor.addSecret("api-server-key", paths.apiKeyFile.readText().trim())
            if (paths.geminiKeyFile.isFile) Diag.redactor.addSecret("gemini-api-key", paths.geminiKeyFile.readText().trim())
            for (f in listOf(paths.serviceAccountFile, paths.userCredentialsFile)) {
                if (!f.isFile) continue
                when (val r = ServiceAccountParser.parse(f.readText())) {
                    is SaParse.Ok -> register(r)
                    is SaParse.OkUser -> register(r)
                    else -> {}
                }
            }
        } catch (_: Exception) { }
    }

    private fun register(r: SaParse.Ok) {
        Diag.redactor.addSecret("sa-private-key", r.privateKey)
        Diag.redactor.addSecret("sa-private-key-id", r.privateKeyId)
        Diag.redactor.addSecret("sa-client-email", r.clientEmail)
        Diag.redactor.addSecret("sa-client-id", r.clientId)
        // Also the PEM body alone (what is left when only part of the key shows up in a log).
        val body = r.privateKey.lines().filter { it.isNotBlank() && !it.startsWith("-----") }.joinToString("")
        Diag.redactor.addSecret("sa-private-key-body", body)
    }

    private fun register(r: SaParse.OkUser) {
        Diag.redactor.addSecret("oauth-refresh-token", r.refreshToken)
        Diag.redactor.addSecret("oauth-client-secret", r.clientSecret)
        Diag.redactor.addSecret("oauth-client-id", r.clientId)
    }
}
