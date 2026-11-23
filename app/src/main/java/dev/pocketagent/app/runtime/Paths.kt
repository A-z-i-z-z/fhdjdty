package dev.pocketagent.app.runtime

import android.content.Context
import java.io.File

/** All on-device locations in one place. Everything lives in app-private storage. */
class Paths(context: Context) {
    // Canonical on purpose: on Android filesDir is /data/user/0/<pkg>/files, where /data/user/0 is a
    // symlink to /data/data. Every path handed to the extractor, the launcher or compared against
    // /proc entries must be the resolved one, or prefix checks and process matching silently fail.
    val files: File = context.filesDir.canonicalFile
    val nativeLibDir: File = File(context.applicationInfo.nativeLibraryDir).canonicalFile
    val cacheDir: File = context.cacheDir.canonicalFile

    val rootfs = File(files, "rootfs")                    // guest "/"
    val rootfsStaging = File(files, "rootfs.new")         // extraction target; renamed to [rootfs] when complete
    val rootfsStamp = File(files, "rootfs.stamp")         // build id of a COMPLETE rootfs; absent => not valid
    val userStatePreserve = File(files, "userstate.preserve") // HERMES_HOME user data parked while the rootfs is replaced

    val runtimeDir = File(files, "runtime")
    val launcher = File(runtimeDir, "pocketagent-proot.sh")
    val state = File(runtimeDir, "state")                 // PA_STATE: scratch, fake /proc, last-launch.txt
    val lastLaunch = File(state, "last-launch.txt")
    val selfTestState = File(runtimeDir, "state-selftest") // separate PA_STATE so a self-test never overwrites the real launch record
    val lastSelfTestFile = File(selfTestState, "last-selftest.txt")
    val terminalState = File(runtimeDir, "state-terminal") // separate PA_STATE for the terminal tab: never touches the gateway's last-launch.txt
    val terminalHistory = File(files, "terminal/history.json")
    val chatDir = File(files, "chat")                    // bots.json, conversations.json, messages/<id>.json

    val logs = File(files, "logs")
    val runtimeLog = File(logs, "runtime.log")            // stdout+stderr of the proot/Hermes process
    val diagnosticsDir = File(cacheDir, "diagnostics")

    val secrets = File(files, "secrets")
    val apiKeyFile = File(secrets, "api_key")
    val serviceAccountFile = File(secrets, "service-account.json")
    val userCredentialsFile = File(secrets, "authorized-user.json")
    val geminiKeyFile = File(secrets, "gemini_api_key")

    val prootBin = File(nativeLibDir, "libproot.so")
    val prootLoader = File(nativeLibDir, "libproot-loader.so")

    // Inside the extracted rootfs (host view of guest paths)
    val hermesHome = File(rootfs, "root/.hermes")
    val hermesEnv = File(hermesHome, ".env")
    val hermesConfig = File(hermesHome, "config.yaml")
    val hermesLogs = File(hermesHome, "logs")
    val guestSecretsDir = File(hermesHome, "secrets")
    val guestServiceAccount = File(guestSecretsDir, "vertex-sa.json")
    val guestAdcFile = File(rootfs, ProviderConfig.GUEST_ADC_RELATIVE)

    companion object {
        const val API_HOST = "127.0.0.1"
        const val DEFAULT_API_PORT = 8642
    }
}
