package dev.pocketagent.app.runtime

import dev.pocketagent.app.diag.Diag
import dev.pocketagent.app.util.AppLog
import dev.pocketagent.app.util.FileUtil
import java.security.SecureRandom

/** API-server key (random per install) and the imported service-account JSON, all in app-private storage with 0600 files. */
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

    val hasServiceAccount: Boolean get() = paths.serviceAccountFile.isFile

    /** Validates and stores. Never logs the content. */
    fun importServiceAccount(raw: String): SaParse {
        val r = ServiceAccountParser.parse(raw)
        if (r is SaParse.Ok) {
            FileUtil.writeAtomic(paths.serviceAccountFile, r.jsonText.toByteArray(Charsets.UTF_8))
            settings.saProjectId = r.projectId
            settings.saMaskedEmail = r.maskedEmail
            register(r)
            AppLog.i("Secrets", "service account imported (project ${r.projectId}, repaired=${r.repaired})")
        } else if (r is SaParse.Fail) {
            AppLog.w("Secrets", "service account rejected: ${r.code} ${r.detail}")
        }
        return r
    }

    fun removeServiceAccount() {
        paths.serviceAccountFile.delete()
        paths.guestServiceAccount.delete()
        settings.saProjectId = ""
        settings.saMaskedEmail = ""
    }

    private fun registerKnownSecrets() {
        try {
            if (paths.apiKeyFile.isFile) Diag.redactor.addSecret("api-server-key", paths.apiKeyFile.readText().trim())
            if (paths.serviceAccountFile.isFile) {
                (ServiceAccountParser.parse(paths.serviceAccountFile.readText()) as? SaParse.Ok)?.let { register(it) }
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
}
