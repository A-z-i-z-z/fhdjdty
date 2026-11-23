package dev.pocketagent.app.runtime

import android.content.Context
import java.io.File

/** All on-device locations in one place. Everything lives in app-private storage. */
class Paths(context: Context) {
    val files: File = context.filesDir
    val nativeLibDir: File = File(context.applicationInfo.nativeLibraryDir)

    val rootfs = File(files, "rootfs")                    // guest "/"
    val rootfsStaging = File(files, "rootfs.new")         // extraction target; renamed to [rootfs] when complete
    val rootfsStamp = File(files, "rootfs.stamp")         // build id of a COMPLETE rootfs; absent => not valid

    val runtimeDir = File(files, "runtime")
    val launcher = File(runtimeDir, "pocketagent-proot.sh")
    val state = File(runtimeDir, "state")                 // PA_STATE: scratch, fake /proc, last-launch.txt
    val lastLaunch = File(state, "last-launch.txt")

    val logs = File(files, "logs")
    val runtimeLog = File(logs, "runtime.log")            // stdout+stderr of the proot/Hermes process
    val diagnosticsDir = File(context.cacheDir, "diagnostics")

    val secrets = File(files, "secrets")
    val apiKeyFile = File(secrets, "api_key")
    val serviceAccountFile = File(secrets, "service-account.json")

    val prootBin = File(nativeLibDir, "libproot.so")
    val prootLoader = File(nativeLibDir, "libproot-loader.so")

    // Inside the extracted rootfs (host view of guest paths)
    val hermesHome = File(rootfs, "root/.hermes")
    val hermesEnv = File(hermesHome, ".env")
    val hermesConfig = File(hermesHome, "config.yaml")
    val hermesLogs = File(hermesHome, "logs")
    val guestSecretsDir = File(hermesHome, "secrets")
    val guestServiceAccount = File(guestSecretsDir, "vertex-sa.json")

    companion object {
        const val GUEST_SERVICE_ACCOUNT = "/root/.hermes/secrets/vertex-sa.json"
        const val API_HOST = "127.0.0.1"
        const val API_PORT = 8642
    }
}
