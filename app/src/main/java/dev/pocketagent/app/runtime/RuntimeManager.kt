package dev.pocketagent.app.runtime

import android.content.Context
import android.net.ConnectivityManager
import android.os.StatFs
import dev.pocketagent.app.BuildConfig
import dev.pocketagent.app.api.HermesApi
import dev.pocketagent.app.util.AppLog
import dev.pocketagent.app.util.FileUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Owns the rootfs and the proot/Hermes child process and exposes the state machine
 * NotInstalled -> Extracting -> Starting -> Running -> Stopped/Error.
 */
class RuntimeManager(
    private val context: Context,
    val paths: Paths,
    val settings: Settings,
    val secrets: SecretStore,
    val wakeLocks: WakeLocks,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _state = MutableStateFlow<RuntimeState>(RuntimeState.NotInstalled)
    val state: StateFlow<RuntimeState> = _state

    private val historyLock = Any()
    private val history = ArrayList<StateEntry>()
    private val exits = ArrayList<ExitRecord>()

    @Volatile private var process: Process? = null
    @Volatile private var processStart = 0L
    @Volatile private var stopRequested = false
    @Volatile var lastLaunchEnv: Map<String, String> = emptyMap(); private set
    @Volatile var lastLaunchCommand: List<String> = emptyList(); private set
    @Volatile var lastExtractStats: String = ""; private set
    @Volatile var lastSelfTest: String = ""; private set

    val api = HermesApi { secrets.apiKey() }

    init {
        refreshInstalledState()
    }

    // ---- observable history (for diagnostics) --------------------------------------------------
    fun stateHistory(): List<StateEntry> = synchronized(historyLock) { history.toList() }
    fun exitRecords(): List<ExitRecord> = synchronized(historyLock) { exits.toList() }

    private fun setState(s: RuntimeState, record: Boolean = true) {
        val prev = _state.value
        _state.value = s
        // Extraction progress updates are not history entries (only the first one is).
        val skip = s is RuntimeState.Extracting && prev is RuntimeState.Extracting
        if (record && !skip) {
            synchronized(historyLock) {
                history += StateEntry(System.currentTimeMillis(), s.label())
                while (history.size > 200) history.removeAt(0)
            }
            AppLog.i("State", s.label())
        }
    }

    // ---- bundled build facts -------------------------------------------------------------------
    fun assetInfo(name: String): Map<String, String> = try {
        context.assets.open(name).bufferedReader().readLines()
            .mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it) to l.substring(it + 1) } }.toMap()
    } catch (_: Exception) { emptyMap() }

    val rootfsInfo: Map<String, String> by lazy { assetInfo("rootfs.info") }
    val prootInfo: Map<String, String> by lazy { assetInfo("proot.info") }
    private val expectedBuildId: String get() = rootfsInfo["build_id"] ?: "unknown"

    fun isRootfsValid(): Boolean =
        paths.rootfsStamp.isFile && paths.rootfsStamp.readText().trim() == expectedBuildId &&
            File(paths.rootfs, "usr/bin").isDirectory && !paths.rootfsStaging.exists()

    fun refreshInstalledState() {
        if (_state.value.isBusyOrRunning) return
        if (isRootfsValid()) {
            if (_state.value is RuntimeState.NotInstalled || _state.value is RuntimeState.Error) setState(RuntimeState.Stopped("ready"))
        } else {
            setState(RuntimeState.NotInstalled)
        }
    }

    // ---- public operations ----------------------------------------------------------------------
    fun start() {
        scope.launch {
            mutex.withLock {
                if (_state.value.isBusyOrRunning && process?.isAlive == true) return@withLock
                wakeLocks.acquire("start")
                try {
                    stopRequested = false
                    if (!ensureRootfs()) return@withLock
                    launchHermes()
                } catch (e: Exception) {
                    AppLog.e("Runtime", "start failed", e)
                    setState(RuntimeState.Error("${e.javaClass.simpleName}: ${e.message}"))
                } finally {
                    wakeLocks.release("start")
                }
            }
        }
    }

    fun stop() {
        stopRequested = true // lets a running extraction / health wait notice immediately
        scope.launch { mutex.withLock { stopInternal("user") } }
    }

    fun restart() {
        scope.launch {
            mutex.withLock { stopInternal("restart") }
            delay(500)
            start()
        }
    }

    /** Deletes the extracted rootfs (forces a fresh extraction on the next start). */
    fun resetRootfs() {
        scope.launch {
            mutex.withLock {
                stopInternal("reset")
                paths.rootfsStamp.delete()
                FileUtil.deleteRecursivelyNoFollow(paths.rootfsStaging)
                FileUtil.deleteRecursivelyNoFollow(paths.rootfs)
                setState(RuntimeState.NotInstalled)
            }
        }
    }

    // ---- extraction -----------------------------------------------------------------------------
    private fun ensureRootfs(): Boolean {
        if (rootfsInfo["stub_rootfs"] == "true") {
            setState(RuntimeState.Error("This build contains no rootfs (stub build). Install the APK produced by CI."))
            return false
        }
        if (isRootfsValid()) return true

        setState(RuntimeState.Extracting(0f, "preparing"))
        val need = rootfsInfo["unpacked_bytes"]?.toLongOrNull() ?: 0L
        val free = try { StatFs(paths.files.path).availableBytes } catch (_: Exception) { Long.MAX_VALUE }
        if (need > 0 && free < need + need / 8) {
            setState(RuntimeState.Error("Not enough free storage: need about ${FileUtil.formatBytes(need + need / 8)}, have ${FileUtil.formatBytes(free)}"))
            return false
        }
        // Anything left from an earlier attempt is by definition incomplete.
        paths.rootfsStamp.delete()
        FileUtil.deleteRecursivelyNoFollow(paths.rootfsStaging)
        FileUtil.deleteRecursivelyNoFollow(paths.rootfs)

        val t0 = System.currentTimeMillis()
        try {
            context.assets.openFd("rootfs.tar.xz").use { afd ->
                afd.createInputStream().use { input ->
                    val stats = TarExtractor(AndroidFsOps()).extractTarXz(
                        input, afd.length, paths.rootfsStaging,
                        isCancelled = { stopRequested },
                    ) { p ->
                        val frac = if (p.compressedTotal > 0) (p.compressedRead.toFloat() / p.compressedTotal).coerceIn(0f, 1f) else 0f
                        setState(RuntimeState.Extracting(frac, "${p.entries} entries"), record = false)
                    }
                    lastExtractStats = stats.toString()
                }
            }
            if (!paths.rootfsStaging.renameTo(paths.rootfs)) throw java.io.IOException("could not move extracted rootfs into place")
            FileUtil.writeAtomic(paths.rootfsStamp, expectedBuildId.toByteArray(), 420)
            AppLog.i("Extract", "done in ${(System.currentTimeMillis() - t0) / 1000}s: $lastExtractStats")
            return true
        } catch (e: InterruptedException) {
            FileUtil.deleteRecursivelyNoFollow(paths.rootfsStaging)
            setState(RuntimeState.Stopped("extraction cancelled"))
            return false
        } catch (e: Exception) {
            AppLog.e("Extract", "failed", e)
            // Leave nothing that could be mistaken for a complete rootfs.
            paths.rootfsStamp.delete()
            FileUtil.deleteRecursivelyNoFollow(paths.rootfsStaging)
            setState(RuntimeState.Error("Extraction failed: ${e.javaClass.simpleName}: ${e.message}"))
            return false
        }
    }

    // ---- start / monitor --------------------------------------------------------------------------
    private suspend fun launchHermes() {
        setState(RuntimeState.Starting("preparing"))
        if (rootfsInfo["stub_rootfs"] == "true" || prootInfo["stub_proot"] == "true") {
            setState(RuntimeState.Error("Stub build: no proot binary bundled."))
            return
        }
        if (!paths.prootBin.exists()) {
            setState(RuntimeState.Error("proot binary missing: ${paths.prootBin}"))
            return
        }
        paths.runtimeDir.mkdirs(); paths.state.mkdirs(); paths.logs.mkdirs()
        context.assets.open("pocketagent-proot.sh").use { i -> FileOutputStream(paths.launcher).use { o -> i.copyTo(o) } }
        FileUtil.chmod(paths.launcher, 448)

        HermesConfig.apply(context, paths, settings, secrets.apiKey())

        val env = LinkedHashMap<String, String>()
        env["PA_PROOT"] = paths.prootBin.path
        env["PA_LOADER"] = paths.prootLoader.path
        env["PA_ROOTFS"] = paths.rootfs.path
        env["PA_STATE"] = paths.state.path
        env["PA_DNS"] = dnsServers().joinToString(" ")
        env["PA_TZ"] = java.util.TimeZone.getDefault().id
        if (settings.noSeccomp) env["PA_NO_SECCOMP"] = "1"
        env["PATH"] = "/system/bin:/system/xbin"
        env["TMPDIR"] = File(paths.state, "tmp").also { it.mkdirs() }.path

        val cmd = listOf("/system/bin/sh", paths.launcher.path, "--", "hermes", "gateway", "run", "--replace", "-v")
        lastLaunchEnv = env; lastLaunchCommand = cmd

        rotateRuntimeLog()
        val header = "=== ${java.util.Date()} start build=${BuildConfig.GIT_COMMIT} noSeccomp=${settings.noSeccomp}\n=== cmd: ${cmd.joinToString(" ")}\n"
        paths.runtimeLog.appendText(header)

        val pb = ProcessBuilder(cmd)
        pb.environment().clear(); pb.environment().putAll(env)
        pb.directory(paths.state)
        pb.redirectErrorStream(true)
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(paths.runtimeLog))
        setState(RuntimeState.Starting("launching proot"))
        val p = pb.start()
        process = p
        processStart = System.currentTimeMillis()
        AppLog.i("Runtime", "process started (pid ${pidOf(p)})")
        scope.launch { watchExit(p) }

        scope.launch { waitForHealth(p) }
    }

    /** Runs outside the start/stop mutex so that Stop stays responsive while Hermes boots. */
    private suspend fun waitForHealth(p: Process) {
        // Python 3.14 + imports under proot on a phone can take a minute.
        setState(RuntimeState.Starting("waiting for Hermes API"))
        val deadline = System.currentTimeMillis() + 240_000
        while (System.currentTimeMillis() < deadline) {
            if (!p.isAlive || stopRequested) return // watchExit sets the resulting state
            val h = api.health()
            if (h.ok) {
                val m = api.models()
                if (m.code == 401) {
                    setState(RuntimeState.Error("Hermes is up but rejected our API key (HTTP 401): ${m.body.take(200)}"))
                    return
                }
                AppLog.i("Runtime", "health OK after ${(System.currentTimeMillis() - processStart) / 1000}s; /v1/models -> ${m.code}")
                setState(RuntimeState.Running)
                return
            }
            delay(1000)
        }
        if (p.isAlive && !stopRequested)
            setState(RuntimeState.Error("Hermes did not become healthy within 240 s (see the runtime log in diagnostics)"))
    }

    private fun watchExit(p: Process) {
        val code = try { p.waitFor() } catch (_: InterruptedException) { -1 }
        val rec = ExitRecord(System.currentTimeMillis(), code, System.currentTimeMillis() - processStart, stopRequested)
        synchronized(historyLock) { exits += rec; while (exits.size > 30) exits.removeAt(0) }
        AppLog.i("Runtime", "child exited: ${rec.describe()}")
        if (process === p) process = null
        val cur = _state.value
        when {
            stopRequested -> setState(RuntimeState.Stopped("stopped"))
            cur is RuntimeState.Starting || cur == RuntimeState.Running ->
                setState(RuntimeState.Error("Hermes exited unexpectedly: ${rec.describe()}" + (ExitRecord.hint(code)?.let { " - $it" } ?: "")))
            else -> {}
        }
    }

    private fun stopInternal(reason: String) {
        val p = process
        stopRequested = true
        if (p != null && p.isAlive) {
            AppLog.i("Runtime", "stopping ($reason)")
            p.destroy()
            if (!p.waitFor(8, TimeUnit.SECONDS)) {
                AppLog.w("Runtime", "did not exit after SIGTERM, killing")
                p.destroyForcibly()
                p.waitFor(5, TimeUnit.SECONDS)
            }
        }
        process = null
        if (_state.value.isBusyOrRunning) setState(RuntimeState.Stopped("stopped ($reason)"))
    }

    private fun rotateRuntimeLog() {
        try {
            if (paths.runtimeLog.length() > 4 * 1024 * 1024) {
                val old = File(paths.logs, "runtime.log.1")
                old.delete(); paths.runtimeLog.renameTo(old)
            }
        } catch (_: Exception) { }
    }

    private fun dnsServers(): List<String> {
        val out = LinkedHashSet<String>()
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val lp = cm.getLinkProperties(cm.activeNetwork)
            lp?.dnsServers?.forEach { a ->
                val h = a.hostAddress ?: return@forEach
                if (!a.isLinkLocalAddress && !h.contains('%')) out += h
            }
        } catch (e: Exception) { AppLog.w("Runtime", "could not read DNS servers", e) }
        out += "1.1.1.1"; out += "8.8.8.8" // fallbacks: always usable even if the network's own resolver is private
        return out.toList()
    }

    private fun pidOf(p: Process): String = try {
        val f = p.javaClass.getDeclaredField("pid"); f.isAccessible = true; f.get(p).toString()
    } catch (_: Throwable) { "?" }

    fun processAlive(): Boolean = process?.isAlive == true
    fun processUptimeMillis(): Long = if (process?.isAlive == true) System.currentTimeMillis() - processStart else 0L

    /**
     * Runs a short command under proot (same launcher, same env as the real start) and keeps the
     * output for the diagnostics dump. Answers "does proot + the rootfs work at all on this device?"
     * independently of Hermes.
     */
    fun selfTest(onDone: (String) -> Unit = {}) {
        scope.launch {
            val out = StringBuilder()
            try {
                if (!isRootfsValid()) { out.append("rootfs not installed yet; press Start first.\n") }
                else if (!paths.prootBin.exists()) { out.append("proot binary missing\n") }
                else {
                    paths.runtimeDir.mkdirs(); paths.state.mkdirs()
                    context.assets.open("pocketagent-proot.sh").use { i -> FileOutputStream(paths.launcher).use { o -> i.copyTo(o) } }
                    val script = "echo uname=\$(uname -a); echo id=\$(id); head -n 2 /etc/os-release; " +
                        "echo cpu-proc-readable=\$(cat /proc/stat >/dev/null 2>&1 && echo yes || echo no); " +
                        "ls -ld /tmp /dev/shm /root; echo hostname=\$(hostname); " +
                        "for p in /root/.hermes/tools/python-*/bin/python3; do echo python=\$p; \$p -c 'import sys,platform,ssl;print(sys.version);print(platform.platform());print(ssl.OPENSSL_VERSION)'; done; " +
                        "hermes --version 2>&1 | head -n 3; echo SELFTEST-DONE"
                    val env = LinkedHashMap<String, String>()
                    env["PA_PROOT"] = paths.prootBin.path; env["PA_LOADER"] = paths.prootLoader.path
                    env["PA_ROOTFS"] = paths.rootfs.path; env["PA_STATE"] = paths.state.path
                    env["PA_DNS"] = dnsServers().joinToString(" "); env["PATH"] = "/system/bin:/system/xbin"
                    if (settings.noSeccomp) env["PA_NO_SECCOMP"] = "1"
                    val pb = ProcessBuilder("/system/bin/sh", paths.launcher.path, "--", "/bin/sh", "-c", script)
                    pb.environment().clear(); pb.environment().putAll(env); pb.redirectErrorStream(true)
                    val t0 = System.currentTimeMillis()
                    val p = pb.start()
                    val reader = Thread { try { out.append(p.inputStream.bufferedReader().readText()) } catch (_: Exception) {} }
                    reader.start()
                    val finished = p.waitFor(150, TimeUnit.SECONDS)
                    if (!finished) { p.destroyForcibly(); out.append("\n[timed out after 150 s]\n") }
                    reader.join(2000)
                    out.append("\n[exit value ${if (finished) p.exitValue() else -1} after ${(System.currentTimeMillis() - t0) / 1000}s; noSeccomp=${settings.noSeccomp}]\n")
                }
            } catch (e: Exception) {
                out.append("self-test failed to run: ${e.javaClass.simpleName}: ${e.message}\n")
            }
            lastSelfTest = "${java.util.Date()}\n$out"
            AppLog.i("SelfTest", out.toString().take(1500))
            onDone(lastSelfTest)
        }
    }
}
