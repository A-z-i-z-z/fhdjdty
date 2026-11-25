package dev.pocketagent.app.runtime

import android.content.Context
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The practice model: a scripted OpenAI-compatible server (one python file, shipped as the asset [ASSET_SCRIPT]) that runs INSIDE
 * the Linux guest beside Hermes while the "Practice model" switch is on, so the whole app can be driven for zero tokens. The guest
 * command ([guestCommand]) runs [ASSET_START] (a small shell script), which starts the model and then `exec`s Hermes.
 *
 * Everything lives in `/opt/agiri/practice` of the guest (host side [hostDir]): outside HERMES_HOME, so it is not in a backup and
 * not in `UserState`; it is regenerated at every practice start. Practice mode never reads or writes the owner's provider setup:
 * the generated `.env` and `config.yaml` of practice come from `ProviderConfig.renderPracticeEnv` and `renderPracticeYaml`.
 *
 * JVM-loadable: no Android type appears in a constant or an initialiser, so unit tests can read every constant here.
 */
object PracticeModel {
    /** `model.default` of the practice configuration. The python file also lists `mock-model`, which the CI fixtures use. */
    const val MODEL_ID = "practice-model"

    /** `model.provider` of the practice configuration AND the per-request `provider` while the switch is on. */
    const val PROVIDER = "custom"

    /** Written into the practice configuration. Not a secret: the scripted model ignores it. */
    const val API_KEY = "practice"

    /** Asset names (BUILD copies `rootfs/smoke/mock_llm.py` and `runtime/practice-start.sh` next to the launcher). */
    const val ASSET_SCRIPT = "practice_model.py"
    const val ASSET_START = "practice-start.sh"

    /** File names inside [hostDir] (and the guest directory). */
    const val HOST_SCRIPT_NAME = "practice_model.py"
    const val HOST_START_NAME = "start.sh"

    const val GUEST_DIR = "/opt/agiri/practice"
    const val GUEST_START = "/opt/agiri/practice/start.sh"
    const val GUEST_REQUEST_LOG = "/opt/agiri/practice/requests.jsonl"

    /** The port the CI smoke test gives the practice model (phase 3). */
    const val SMOKE_PORT = 18080

    /** The magic words of the python file (its `KEYWORDS` line), as a list. `PracticeModelTest` keeps the two equal. */
    val KEYWORDS: List<String> = listOf(
        "RUN_TOOL", "ASK_APPROVAL", "LONG", "THINK", "SLOW", "FAIL", "MEMORY", "FORGE", "MEDIA",
        "PROMPT_HAS", "PROMPT_SIZE", "BALLAST", "TOOLS", "STATS", "PAUSE",
    )

    /** `http://127.0.0.1:<port>/v1`. [IllegalArgumentException] unless [port] is 1 to 65535. */
    fun baseUrl(port: Int): String {
        require(port in 1..65535) { "port out of range: $port" }
        return "http://127.0.0.1:$port/v1"
    }

    /** `files/rootfs/opt/agiri/practice`: the host's view of [GUEST_DIR]. */
    fun hostDir(paths: Paths): File = File(paths.rootfs, "opt/agiri/practice")

    /** The command the launcher runs in the guest after `--`: the start script, which starts the model and then Hermes. */
    fun guestCommand(port: Int): List<String> = listOf("sh", GUEST_START, port.toString())

    /**
     * Writes both assets into [hostDir] (atomic, only when the bytes differ, `start.sh` owner-only) and returns the directory.
     * [IOException] on any failure (a missing asset, a directory that cannot be made, a folder that leads out of the Linux image).
     */
    fun install(context: Context, paths: Paths): File {
        val dir = hostDir(paths)
        val script = context.assets.open(ASSET_SCRIPT).use { it.readBytes() }
        val start = context.assets.open(ASSET_START).use { it.readBytes() }
        // The folder is inside the guest: if a symlink there led out of the image, nothing may be created or written through it.
        // Check the deepest part that already exists BEFORE mkdirs, and the whole path again after it.
        val root = paths.rootfs.canonicalFile.path + File.separator
        var existing: File? = dir
        while (existing != null && !existing.exists()) existing = existing.parentFile
        // (An ancestor above the image root means the image root itself does not exist yet: nothing there can be a link out of it.)
        if (existing != null && existing.absoluteFile.path.startsWith(paths.rootfs.absoluteFile.path)) {
            val p = existing.canonicalFile.path
            if (p + File.separator != root && !p.startsWith(root)) throw IOException("practice folder is outside the Linux image: ${dir.path}")
        }
        dir.mkdirs()
        if (!dir.canonicalFile.path.startsWith(root)) throw IOException("practice folder is outside the Linux image: ${dir.path}")
        installInto(dir, script, start)
        return dir
    }

    /**
     * Pure file logic of [install]. Returns true when anything was (re)written. A file whose bytes already match is left alone.
     * Never leaves a temporary file behind.
     */
    internal fun installInto(dir: File, script: ByteArray, start: ByteArray): Boolean {
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) throw IOException("cannot create ${dir.path}")
        val wroteScript = writeIfDifferent(File(dir, HOST_SCRIPT_NAME), script, executable = false)
        val wroteStart = writeIfDifferent(File(dir, HOST_START_NAME), start, executable = true)
        return wroteScript || wroteStart
    }

    private fun writeIfDifferent(target: File, bytes: ByteArray, executable: Boolean): Boolean {
        if (target.isFile && target.length() == bytes.size.toLong() && target.readBytes().contentEquals(bytes)) {
            if (executable) ownerOnly(target, executable = true)
            return false
        }
        val tmp = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
        try {
            tmp.writeBytes(bytes)
            ownerOnly(tmp, executable)
            try {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: IOException) {
                // A file system without atomic moves: a plain replace is still better than leaving the old file.
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
        return true
    }

    /** Read and write for the owner only (0600), plus execute for the owner when [executable] (0700). */
    private fun ownerOnly(f: File, executable: Boolean) {
        f.setReadable(false, false); f.setReadable(true, true)
        f.setWritable(false, false); f.setWritable(true, true)
        f.setExecutable(false, false); if (executable) f.setExecutable(true, true)
    }
}
