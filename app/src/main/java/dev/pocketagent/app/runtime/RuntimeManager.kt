package dev.pocketagent.app.runtime

import android.content.Context
import android.net.ConnectivityManager
import android.os.SystemClock
import android.os.StatFs
import dev.pocketagent.app.BuildConfig
import dev.pocketagent.app.api.HermesApi
import dev.pocketagent.app.service.AgentService
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
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import dev.pocketagent.app.R
import dev.pocketagent.app.ui.text.Texts

/** One launch of proot+Hermes. Exits of superseded runs must never be attributed to the current one. */
class RunHandle(val generation: Int, val process: Process, val port: Int, val logOffset: Long) {
    val startElapsed: Long = SystemClock.elapsedRealtime()
    val startWall: Long = System.currentTimeMillis()
    val stopRequested = AtomicBoolean(false)
}

data class MemSample(val timeMillis: Long, val guestProcesses: Int, val rssKb: Long)

/**
 * Owns the rootfs and the proot/Hermes child process and exposes the state machine
 * NotInstalled -> Extracting -> Starting -> Running -> Stopped/Error. It also owns the foreground service:
 * [foregroundNeeded] is true while any operation is in flight or a child process is alive.
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

    private val opCount = AtomicInteger(0)
    private val _foreground = MutableStateFlow(false)
    /** True while an operation (start/stop/extract) runs or a child process is alive. The service follows this. */
    val foregroundNeeded: StateFlow<Boolean> = _foreground

    private val _unexpectedExits = MutableStateFlow(0)
    /** +1 each time the child process ended without anybody asking for it (see watchExit); Presence restarts on a change. */
    val unexpectedExits: StateFlow<Int> = _unexpectedExits

    private val historyLock = Any()
    private val history = ArrayList<StateEntry>()
    private val exits = ArrayList<ExitRecord>()
    private val memSamples = ArrayList<MemSample>()

    @Volatile private var current: RunHandle? = null
    private val generationCounter = AtomicInteger(0)
    @Volatile private var cancelExtract = false

    @Volatile var apiPort: Int = Paths.DEFAULT_API_PORT; private set
    /** True once the listener on [apiPort] has been proven to be our own Hermes; only then is the Bearer key sent. */
    @Volatile private var apiVerified = false

    @Volatile var lastLaunchEnv: Map<String, String> = emptyMap(); private set
    /**
     * The options of the configuration last written to the guest. Set at the start of every launch (before the configuration is
     * written) and by every rewrite; kept until the next launch or rewrite writes another. Read by the engine's provider lambda
     * (a per-chat model override goes out as the practice provider while this says practice) and by the diagnostics.
     */
    @Volatile var lastGuestOptions: GuestOptions = GuestOptions(); private set
    /**
     * Held while the options are worked out and the configuration is written, by a launch and by a rewrite alike: the two must not
     * interleave (a rewrite landing between a launch's options and its write could put the owner's provider setup into a practice
     * run). A plain monitor, not [mutex], so it can never deadlock with start/stop.
     */
    private val guestConfigLock = Any()
    /** What `hooks/` and `plugins/` of HERMES_HOME held that the app did not write, as of the last launch. */
    @Volatile var guestCodeReport: List<GuestCodeEntry> = emptyList(); private set
    /** The practice port of the run that is alive (or was started last); null for a normal run and after it ended. */
    @Volatile private var runPracticePort: Int? = null
    @Volatile var lastLaunchCommand: List<String> = emptyList(); private set
    @Volatile var lastExtractStats: String = ""; private set
    @Volatile var lastSelfTest: String = ""; private set
    /** Wall-clock start of a running self-test, 0 when idle (shown in the dump so the tester knows to wait). */
    @Volatile var selfTestStartedAt: Long = 0L; private set
    private val _selfTestActive = MutableStateFlow(false)
    val selfTestActive: StateFlow<Boolean> = _selfTestActive
    private val _selfTestText = MutableStateFlow("")
    val selfTestText: StateFlow<String> = _selfTestText
    @Volatile var lastPreservedUserState: String = ""; private set
    private val selfTestRunning = AtomicBoolean(false)

    val api = HermesApi({ apiPort }, { if (apiVerified) secrets.apiKey() else null })

    // No init block here: it must be the LAST member of the class (see the end of the file).

    // ---- history / observability -----------------------------------------------------------------
    fun stateHistory(): List<StateEntry> = synchronized(historyLock) { history.toList() }
    fun exitRecords(): List<ExitRecord> = synchronized(historyLock) { exits.toList() }
    fun memorySamples(): List<MemSample> = synchronized(historyLock) { memSamples.toList() }
    fun processAlive(): Boolean = current?.process?.isAlive == true
    fun processUptimeMillis(): Long = current?.takeIf { it.process.isAlive }?.let { SystemClock.elapsedRealtime() - it.startElapsed } ?: 0L

    private fun recomputeForeground() {
        _foreground.value = opCount.get() > 0 || _state.value.isBusyOrRunning || processAlive()
    }

    private fun beginOp() { opCount.incrementAndGet(); recomputeForeground() }
    private fun endOp() { opCount.decrementAndGet(); recomputeForeground() }

    private fun setState(s: RuntimeState, record: Boolean = true) {
        val prev = _state.value
        _state.value = s
        val skip = !record || (s is RuntimeState.Extracting && prev is RuntimeState.Extracting) ||
            (s is RuntimeState.Starting && prev is RuntimeState.Starting && s.step.substringBefore(" (") == prev.step.substringBefore(" ("))
        if (!skip) {
            synchronized(historyLock) {
                history += StateEntry(System.currentTimeMillis(), s.label())
                while (history.size > 200) history.removeAt(0)
            }
            AppLog.i("State", s.label())
        }
        recomputeForeground()
        if (s is RuntimeState.Error && prev !is RuntimeState.Error) selfTest(auto = true)
    }

    // ---- bundled build facts ------------------------------------------------------------------------
    fun assetInfo(name: String): Map<String, String> = try {
        context.assets.open(name).bufferedReader().readLines()
            .mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it) to l.substring(it + 1) } }.toMap()
    } catch (_: Exception) { emptyMap() }

    val rootfsInfo: Map<String, String> by lazy { assetInfo("rootfs.info") }
    val prootInfo: Map<String, String> by lazy { assetInfo("proot.info") }
    private val expectedBuildId: String get() = rootfsInfo["build_id"] ?: "unknown"

    private val stampErrorLogged = AtomicBoolean(false)

    /** Result of reading rootfs.stamp: [text] is null when the file is absent; [error] is set when it exists but cannot be read. */
    private class StampRead(val text: String?, val error: Exception?)

    private fun readStamp(): StampRead = try {
        val f = paths.rootfsStamp
        when {
            !f.exists() -> StampRead(null, null)
            !f.isFile -> StampRead(null, java.io.IOException("${f.name} exists but is not a regular file"))
            else -> StampRead(f.readText().trim(), null)
        }
    } catch (e: Exception) { StampRead(null, e) }

    /**
     * Lenient on purpose: an unreadable stamp (permissions, I/O error) just means "not valid" here, so that callers such as
     * the constructor (PocketApp.onCreate) can never crash. The Start path does NOT treat it as "not installed": see [ensureRootfs].
     */
    fun isRootfsValid(): Boolean {
        val r = readStamp()
        if (r.error != null) {
            if (stampErrorLogged.compareAndSet(false, true)) AppLog.w("Runtime", "rootfs stamp unreadable, treating the rootfs as not valid", r.error)
            return false
        }
        val stamp = r.text ?: return false
        return stamp == expectedBuildId && File(paths.rootfs, "usr/bin").isDirectory && !paths.rootfsStaging.exists()
    }

    /** Re-derives Stopped/NotInstalled from disk. An Error stays on screen until the next Start or Reset. */
    fun refreshInstalledState() {
        val s = _state.value
        if (s.isBusyOrRunning || s is RuntimeState.Error) return
        if (isRootfsValid()) { if (s is RuntimeState.NotInstalled) setState(RuntimeState.Stopped("ready")) }
        else if (s !is RuntimeState.NotInstalled) setState(RuntimeState.NotInstalled)
        else if (s is RuntimeState.NotInstalled && history.isEmpty()) setState(RuntimeState.NotInstalled)
    }

    // ---- public operations -----------------------------------------------------------------------------
    fun start() {
        beginOp()
        scope.launch {
            try {
                mutex.withLock {
                    if (_state.value.isBusyOrRunning && processAlive()) return@withLock
                    ensureServiceStarted()
                    val token = holdWake("start")
                    var handedOver = false
                    try {
                        cancelExtract = false
                        // A previous run that is still alive (Error state after a health timeout) is superseded.
                        current?.stopRequested?.set(true)
                        current = null // its exit is now a superseded run's exit
                        if (!ensureRootfs()) return@withLock
                        handedOver = launchHermes(token)
                    } catch (e: Exception) {
                        AppLog.e("Runtime", "start failed", e)
                        setState(RuntimeState.Error("${e.javaClass.simpleName}: ${e.message}"))
                    } finally {
                        if (!handedOver) releaseWake(token, "start failed or aborted")
                    }
                }
            } finally { endOp() }
        }
    }

    fun stop() {
        beginOp()
        cancelExtract = true // lets a running extraction notice immediately
        current?.stopRequested?.set(true)
        scope.launch { try { mutex.withLock { stopInternal("user") } } finally { endOp() } }
    }

    fun restart() {
        beginOp()
        scope.launch {
            try { mutex.withLock { stopInternal("restart") } } finally { endOp() }
            delay(500)
            start()
        }
    }

    /** Deletes the extracted rootfs (forces a fresh extraction on the next start). User state is parked first. */
    fun resetRootfs() {
        beginOp()
        scope.launch {
            try {
                mutex.withLock {
                    stopInternal("reset")
                    try {
                        FileUtil.deleteOrThrow(paths.rootfsStamp)
                        parkUserState()
                        FileUtil.deleteOrThrow(paths.rootfsStaging); FileUtil.deleteOrThrow(paths.rootfs)
                        setState(RuntimeState.NotInstalled)
                    } catch (e: Exception) {
                        setState(RuntimeState.Error(Texts.get(context, R.string.eng_rt_delete_failed, "${e.message}")))
                    }
                }
            } finally { endOp() }
        }
    }

    /** Rewrites the guest .env/config.yaml from the current settings (e.g. after the API key was removed). */
    fun rewriteGuestConfigIfInstalled() {
        try {
            if (!isRootfsValid()) return
            synchronized(guestConfigLock) {
                val opts = currentGuestOptions()
                lastGuestOptions = opts
                HermesConfig.apply(paths, settings, secrets, secrets.apiKey(), apiPort, opts)
            }
        } catch (e: Exception) { AppLog.w("Runtime", "could not rewrite guest config", e) }
    }

    /**
     * The options a rewrite uses. While a Hermes process is alive its mode never flips: a run started as the practice model keeps
     * its practice port, a normal run stays normal (the gateway reads config.yaml again for every request, so a rewrite in the
     * other mode would point a live Hermes at a model that is not running). Only the web switch follows the setting live. While
     * stopped, the practice switch decides (the files are regenerated at every start anyway).
     */
    private fun currentGuestOptions(): GuestOptions {
        val prefs = GuestPrefs.get(context)
        // A run is "active" from the first step of its launch (the practice port is picked before the process exists) until it ends.
        val runActive = processAlive() || _state.value is RuntimeState.Starting
        // A launch that has not picked its ports yet (state Starting, no practice port recorded) while the practice switch is on: the
        // rewrite must never build the owner's inputs here. It writes the practice form with a stand-in port; the launch's own write
        // comes last (it holds the same lock) and puts the real port in.
        val prePortWindow = runActive && runPracticePort == null && _state.value is RuntimeState.Starting && prefs.practiceModel.value
        val practicePort = when {
            prePortWindow -> PracticeModel.SMOKE_PORT
            runActive -> runPracticePort
            prefs.practiceModel.value -> PracticeModel.SMOKE_PORT
            else -> null
        }
        return GuestOptions(prefs.webKeyless.value, practicePort)
    }

    /**
     * Applies a changed guest switch (web lookups, practice model). [restartIfRunning] true (the practice switch): a running or
     * starting Hermes is restarted, and a Hermes stopped by an error is started again when the practice switch no longer matches the
     * configuration last written (a failed practice launch must not leave Hermes stopped after the switch is turned off). False
     * (the web switch): only the configuration is rewritten, Hermes picks it up by itself. Does nothing without an installed rootfs.
     * The file work runs on the runtime's own thread, so this is safe to call from a click handler.
     */
    fun onGuestOptionsChanged(restartIfRunning: Boolean) {
        scope.launch {
            try {
                val prefs = GuestPrefs.get(context)   // enforces the expiry
                if (!isRootfsValid()) return@launch
                val s = _state.value
                if (restartIfRunning && (s == RuntimeState.Running || s is RuntimeState.Starting)) { restart(); return@launch }
                if (restartIfRunning && s is RuntimeState.Error && lastGuestOptions.practice != prefs.practiceModel.value) { start(); return@launch }
                rewriteGuestConfigIfInstalled()
            } catch (e: Exception) {
                AppLog.w("Runtime", "could not apply the guest options", e)
            }
        }
    }

    private fun ensureServiceStarted() {
        try { AgentService.ensureRunning(context) }
        catch (e: Exception) { AppLog.w("Runtime", "could not start the foreground service (app in background?)", e) }
    }

    /** One wake-lock reference per start; a token can be released only once, so an old run can never release a newer run's reference. */
    private class WakeToken { val released = AtomicBoolean(false) }
    private fun holdWake(why: String): WakeToken { wakeLocks.acquire(why, 25 * 60_000L); return WakeToken() }
    private fun releaseWake(t: WakeToken, why: String) { if (t.released.compareAndSet(false, true)) wakeLocks.release(why) }

    // ---- rootfs: extraction and user-state preservation --------------------------------------------------
    private fun parkUserState() {
        if (!paths.hermesHome.isDirectory) return
        val moved = UserState.park(paths.hermesHome, paths.userStatePreserve)
        if (moved.isNotEmpty()) { lastPreservedUserState = "parked ${moved.size} entries: ${moved.joinToString()}"; AppLog.i("UserState", lastPreservedUserState) }
    }

    private fun restoreUserState() {
        if (!paths.userStatePreserve.exists()) return
        val r = UserState.restore(paths.userStatePreserve, paths.hermesHome)
        lastPreservedUserState = "restored ${r.size} entries: ${r.joinToString()}"
        AppLog.i("UserState", lastPreservedUserState)
    }

    private fun dirBytes(d: File): Long {
        if (!d.exists()) return 0L
        var total = 0L
        try {
            java.nio.file.Files.walk(d.toPath()).use { st ->
                st.forEach { p -> try { val a = java.nio.file.Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes::class.java, java.nio.file.LinkOption.NOFOLLOW_LINKS); if (a.isRegularFile) total += a.size() } catch (_: Exception) { } }
            }
        } catch (_: Exception) { }
        return total
    }

    private fun ensureRootfs(): Boolean {
        if (rootfsInfo["stub_rootfs"] == "true") {
            setState(RuntimeState.Error(Texts.get(context, R.string.eng_rt_stub_rootfs)))
            return false
        }
        // A stamp that exists but cannot be read must NOT lead to "delete the rootfs and extract again": that would silently throw
        // away guest files outside HERMES_HOME. Refuse to start, say why, and leave everything on disk as it is (Reset is the way out).
        val stampRead = readStamp()
        if (stampRead.error != null) {
            AppLog.e("Runtime", "start refused: rootfs stamp exists but cannot be read; rootfs left untouched", stampRead.error)
            setState(RuntimeState.Error(Texts.get(context, R.string.eng_rt_stamp_unreadable, paths.rootfsStamp.name, "${stampRead.error.javaClass.simpleName}: ${stampRead.error.message}")))
            return false
        }
        if (isRootfsValid()) {
            // The app may have died between "rootfs replaced" and "user state moved back".
            try { restoreUserState() } catch (e: Exception) { AppLog.e("UserState", "restore failed", e) }
            return true
        }

        setState(RuntimeState.Extracting(0f, "preparing"))
        val need = rootfsInfo["unpacked_bytes"]?.toLongOrNull() ?: 0L
        val freeNow = try { StatFs(paths.files.path).availableBytes } catch (_: Exception) { Long.MAX_VALUE }
        // The old rootfs (and a half-extracted staging dir) are deleted BEFORE extracting, so their space counts as available.
        val reclaimable = dirBytes(paths.rootfs) + dirBytes(paths.rootfsStaging)
        val free = if (freeNow == Long.MAX_VALUE) freeNow else freeNow + reclaimable
        if (need > 0 && free < need + need / 8) {
            setState(RuntimeState.Error(Texts.get(context, R.string.eng_rt_no_space, FileUtil.formatBytes(need + need / 8), FileUtil.formatBytes(freeNow), FileUtil.formatBytes(reclaimable))))
            return false
        }
        val t0 = System.currentTimeMillis()
        try {
            paths.rootfsStamp.delete()
            // Chat history, memories, skills... live inside the rootfs: park them, replace the rootfs, move them back.
            parkUserState()
            FileUtil.deleteOrThrow(paths.rootfsStaging)
            FileUtil.deleteOrThrow(paths.rootfs)

            context.assets.openFd("rootfs.tar.xz").use { afd ->
                afd.createInputStream().use { input ->
                    val stats = TarExtractor(AndroidFsOps()).extractTarXz(
                        input, afd.length, paths.rootfsStaging,
                        isCancelled = { cancelExtract },
                    ) { p ->
                        val frac = if (p.compressedTotal > 0) (p.compressedRead.toFloat() / p.compressedTotal).coerceIn(0f, 1f) else 0f
                        setState(RuntimeState.Extracting(frac, "${p.entries} entries"), record = false)
                    }
                    lastExtractStats = stats.toString()
                }
            }
            if (!paths.rootfsStaging.renameTo(paths.rootfs)) throw java.io.IOException("could not move ${paths.rootfsStaging} to ${paths.rootfs}")
            restoreUserState()
            FileUtil.writeAtomic(paths.rootfsStamp, expectedBuildId.toByteArray(), 420)
            AppLog.i("Extract", "done in ${(System.currentTimeMillis() - t0) / 1000}s: $lastExtractStats")
            return true
        } catch (e: InterruptedException) {
            FileUtil.deleteRecursivelyNoFollow(paths.rootfsStaging)
            setState(RuntimeState.Stopped("extraction cancelled"))
            return false
        } catch (e: Exception) {
            AppLog.e("Extract", "failed", e)
            paths.rootfsStamp.delete() // nothing that could be mistaken for a complete rootfs
            FileUtil.deleteRecursivelyNoFollow(paths.rootfsStaging)
            setState(RuntimeState.Error(Texts.get(context, R.string.eng_rt_extract_failed, "${e.javaClass.simpleName}: ${e.message}")))
            return false
        }
    }

    // ---- launch ------------------------------------------------------------------------------------------------
    private fun installLauncher() {
        val bytes = context.assets.open("pocketagent-proot.sh").use { it.readBytes() }
        // Replaced atomically (and only when different): a running `sh` keeps reading its old inode.
        if (!paths.launcher.isFile || !paths.launcher.readBytes().contentEquals(bytes)) FileUtil.writeAtomic(paths.launcher, bytes, 448)
    }

    private fun launchEnv(state: File): LinkedHashMap<String, String> {
        val env = LinkedHashMap<String, String>()
        env["PA_PROOT"] = paths.prootBin.path
        env["PA_LOADER"] = paths.prootLoader.path
        env["PA_ROOTFS"] = paths.rootfs.path
        env["PA_STATE"] = state.path
        env["PA_DNS"] = dnsServers().joinToString(" ")
        env["PA_TZ"] = java.util.TimeZone.getDefault().id
        if (settings.noSeccomp) env["PA_NO_SECCOMP"] = "1"
        env["PATH"] = "/system/bin:/system/xbin"
        env["TMPDIR"] = File(state, "tmp").also { it.mkdirs() }.path
        return env
    }

    /** A free loopback port that is not [exclude] (the practice model must not share the API port). */
    private fun pickFreePort(exclude: Int = -1): Int {
        repeat(20) {
            try {
                ServerSocket(0, 1, InetAddress.getByName(Paths.API_HOST)).use { if (it.localPort != exclude) return it.localPort }
            } catch (_: Exception) { }
        }
        return if (exclude == Paths.DEFAULT_API_PORT) Paths.DEFAULT_API_PORT + 1 else Paths.DEFAULT_API_PORT
    }

    /** Returns true when ownership of the wake lock has been handed to the health-wait coroutine. */
    private suspend fun launchHermes(token: WakeToken): Boolean {
        setState(RuntimeState.Starting("preparing"))
        killStaleGuests("before start")
        if (rootfsInfo["stub_rootfs"] == "true" || prootInfo["stub_proot"] == "true") {
            setState(RuntimeState.Error(Texts.get(context, R.string.eng_rt_stub_proot))); return false
        }
        if (!paths.prootBin.exists()) { setState(RuntimeState.Error(Texts.get(context, R.string.eng_rt_proot_missing, paths.prootBin))); return false }
        // An expired practice flag is turned off here, before the options are read: it must never survive into this launch.
        val guestPrefs = GuestPrefs.get(context)
        guestPrefs.enforceExpiry()
        val practice = guestPrefs.practiceModel.value
        if (!practice) {
            // The practice model has no provider setup to validate; every other run is checked as before.
            val inputs = settings.providerInputs(secrets.geminiApiKey())
            val blocking = ProviderConfig.blocking(ProviderConfig.problems(inputs))
            if (blocking.isNotEmpty()) { setState(RuntimeState.Error(Texts.get(context, R.string.eng_rt_bad_config, blocking))); return false }
        }

        paths.runtimeDir.mkdirs(); paths.state.mkdirs(); paths.logs.mkdirs()
        installLauncher()
        val port = pickFreePort()
        apiPort = port; apiVerified = false
        val practicePort = if (practice) pickFreePort(exclude = port) else null
        val opts = GuestOptions(guestPrefs.webKeyless.value, practicePort)
        // Under the lock a rewrite cannot interleave with this write. Set before the configuration is written: it describes the
        // configuration the guest is about to have (see the field).
        synchronized(guestConfigLock) {
            lastGuestOptions = opts
            runPracticePort = practicePort
            paths.hermesLogs.mkdirs()
            HermesConfig.apply(paths, settings, secrets, secrets.apiKey(), port, opts)
        }

        if (practice) {
            try { PracticeModel.install(context, paths) }
            catch (e: java.io.IOException) {
                AppLog.e("Runtime", "practice model could not be installed", e)
                setState(RuntimeState.Error("Practice model could not be installed: ${e.message}")); return false
            }
        }

        // The code Hermes would run from its own folders that the app did not write: listed, never changed.
        try {
            guestCodeReport = GuestCodeScan.scan(paths.hermesHome)
            if (guestCodeReport.isEmpty()) AppLog.i("Runtime", "no foreign hooks or plugins")
            else AppLog.w("Runtime", "guest code not written by the app: ${GuestCodeScan.summary(guestCodeReport)}")
        } catch (e: Exception) {
            guestCodeReport = emptyList()
            AppLog.w("Runtime", "could not scan hooks and plugins", e)
        }

        val env = launchEnv(paths.state)
        env["PA_CLEAN_STALE"] = "1" // the launcher removes leftover gateway pid/lock/socket files of a killed run
        val cmd = HermesConfig.launchCommand(paths.launcher.path, practicePort)
        lastLaunchEnv = env; lastLaunchCommand = cmd

        rotateRuntimeLog()
        val practiceNote = if (practicePort != null) "practice=on practicePort=$practicePort" else "practice=off"
        val header = "=== ${java.util.Date()} start build=${BuildConfig.GIT_COMMIT} port=$port $practiceNote noSeccomp=${settings.noSeccomp}\n=== cmd: ${cmd.joinToString(" ")}\n"
        paths.runtimeLog.appendText(header)

        // Everything this launch prints lands after this offset (the log is appended across runs).
        val logOffset = paths.runtimeLog.length()
        val pb = ProcessBuilder(cmd)
        pb.environment().clear(); pb.environment().putAll(env)
        pb.directory(paths.state)
        pb.redirectErrorStream(true)
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(paths.runtimeLog))
        setState(RuntimeState.Starting("launching proot"))
        val p = pb.start()
        val handle = RunHandle(generationCounter.incrementAndGet(), p, port, logOffset)
        current = handle
        recomputeForeground()
        AppLog.i("Runtime", "process started (gen ${handle.generation}, pid ${pidOf(p)}, port $port) $practiceNote")
        scope.launch { watchExit(handle) }
        scope.launch { monitor(handle) }
        scope.launch { waitForHealth(handle, token) }
        return true
    }

    /**
     * Runs outside the start/stop mutex so that Stop stays responsive while Hermes boots. Owns [token]'s wake lock.
     * Every state change is guarded by [still]: a Stop (or a newer run) during a blocking call must not be overwritten.
     */
    private suspend fun waitForHealth(h: RunHandle, token: WakeToken) {
        fun still() = h.process.isAlive && !h.stopRequested.get() && current === h
        try {
            var slowDiagnosticsStarted = false
            var apiUpSince = -1L
            while (still()) {
                val elapsed = (SystemClock.elapsedRealtime() - h.startElapsed) / 1000
                if (elapsed > 1200) {
                    if (still()) setState(RuntimeState.Error(Texts.get(context, R.string.eng_rt_not_healthy)))
                    return
                }
                val hr = api.health()
                if (!still()) return
                if (hr.ok) {
                    if (apiUpSince < 0) apiUpSince = elapsed
                    if (!apiVerified) {
                        when (verifyListener(h)) {
                            Verdict.OK -> { apiVerified = true; AppLog.i("Runtime", "listener on port ${h.port} verified as our Hermes") }
                            Verdict.FOREIGN -> { if (still()) setState(RuntimeState.Error(Texts.get(context, R.string.eng_rt_foreign_port, h.port))); return }
                            Verdict.NOT_YET -> {
                                if (elapsed - apiUpSince > 90) {
                                    if (still()) setState(RuntimeState.Error(Texts.get(context, R.string.eng_rt_port_unannounced, h.port)))
                                    return
                                }
                                if (!still()) return
                                setState(RuntimeState.Starting("API answers, waiting for Hermes to announce port ${h.port} (${elapsed} s)"))
                            }
                        }
                    }
                    if (apiVerified) {
                        val m = api.models()
                        if (!still()) return
                        if (m.code == 401) { setState(RuntimeState.Error(Texts.get(context, R.string.eng_rt_key_rejected, m.body.take(200)))); return }
                        AppLog.i("Runtime", "health OK after ${elapsed}s; /v1/models -> ${m.code}")
                        setState(RuntimeState.Running)
                        return
                    }
                } else {
                    if (elapsed > 240) {
                        if (!slowDiagnosticsStarted) {
                            slowDiagnosticsStarted = true
                            AppLog.w("Runtime", "still starting after ${elapsed}s: running the self-test")
                            selfTest(auto = true)
                        }
                        if (still()) setState(RuntimeState.Starting("$STILL_STARTING (${elapsed} s, process alive, no API yet)"))
                    } else if (still()) setState(RuntimeState.Starting("waiting for Hermes API (${elapsed} s)"))
                }
                delay(1000)
            }
        } finally {
            releaseWake(token, "health wait finished")
        }
    }

    private enum class Verdict { OK, FOREIGN, NOT_YET }

    /**
     * Before the Bearer key is ever sent we must know that whatever answers on the port is OUR Hermes:
     * (1) our child announced exactly this port in its own stdout during this launch (a squatter cannot write our log), and
     * (2) where /proc/net/tcp is readable, the listener's uid is ours.
     */
    private fun verifyListener(h: RunHandle): Verdict {
        if (!h.process.isAlive) return Verdict.NOT_YET
        val uid = listenerUid(h.port)
        if (uid != null && uid != android.os.Process.myUid()) return Verdict.FOREIGN
        // Only bytes written since THIS launch count (the log is appended across runs), and the port must end there.
        val announced = PortAnnouncement.foundInFileSince(paths.runtimeLog, h.logOffset, Paths.API_HOST, h.port)
        return if (announced) Verdict.OK else Verdict.NOT_YET
    }

    /** uid owning the LISTEN socket on 127.0.0.1:[port], or null when /proc/net/tcp is unreadable or has no such entry. */
    private fun listenerUid(port: Int): Int? {
        for (f in listOf("/proc/net/tcp", "/proc/net/tcp6")) {
            try {
                for (line in File(f).readLines().drop(1)) {
                    val c = line.trim().split(Regex("\\s+"))
                    if (c.size < 8 || c[3] != "0A") continue
                    val localPort = c[1].substringAfter(':').toInt(16)
                    if (localPort == port) return c[7].toInt()
                }
            } catch (_: Exception) { }
        }
        return null
    }

    private fun watchExit(h: RunHandle) {
        val code = try { h.process.waitFor() } catch (_: InterruptedException) { -1 }
        val rec = ExitRecord(System.currentTimeMillis(), code, SystemClock.elapsedRealtime() - h.startElapsed, h.stopRequested.get())
        synchronized(historyLock) { exits += rec; while (exits.size > 30) exits.removeAt(0) }
        val superseded = current !== h
        AppLog.i("Runtime", "child (gen ${h.generation}) exited: ${rec.describe()}${if (superseded) " [superseded run, state untouched]" else ""}")
        if (superseded) { recomputeForeground(); return }
        current = null
        runPracticePort = null
        apiVerified = false
        val cur = _state.value
        when {
            h.stopRequested.get() -> setState(RuntimeState.Stopped("stopped"))
            cur is RuntimeState.Starting || cur == RuntimeState.Running -> {
                setState(RuntimeState.Error(ExitRecord.hint(code)?.let { Texts.get(context, R.string.eng_rt_exited_hint, rec.describe(), it) } ?: Texts.get(context, R.string.eng_rt_exited, rec.describe())))
                _unexpectedExits.value = _unexpectedExits.value + 1 // after the Error state is visible
            }
            else -> {}
        }
        recomputeForeground()
    }

    /** Every ~30 s: how many guest processes exist and how much memory they use (to attribute a later exit 137). */
    private suspend fun monitor(h: RunHandle) {
        while (h.process.isAlive && current === h) {
            try {
                val pids = ProcTree.guestProcesses().toMutableList()
                pidOf(h.process).toIntOrNull()?.let { if (it !in pids) pids += it }
                var rss = 0L
                for (pid in pids) {
                    try { File("/proc/$pid/status").useLines { ls -> ls.firstOrNull { it.startsWith("VmRSS:") }?.let { rss += it.filter(Char::isDigit).toLongOrNull() ?: 0 } } } catch (_: Exception) { }
                }
                val s = MemSample(System.currentTimeMillis(), pids.size, rss)
                synchronized(historyLock) { memSamples += s; while (memSamples.size > 60) memSamples.removeAt(0) }
                AppLog.i("Mem", "guest processes=${pids.size} rss=${FileUtil.formatBytes(rss * 1024)} state=${_state.value.label()}")
            } catch (_: Exception) { }
            delay(30_000)
        }
    }

    private fun stopInternal(reason: String) {
        val h = current
        h?.stopRequested?.set(true)
        val p = h?.process
        if (p != null && p.isAlive) {
            AppLog.i("Runtime", "stopping ($reason)")
            // proot ignores SIGTERM and does not kill the guest when it dies, so signal the guest's
            // processes directly (Hermes shuts down cleanly on SIGTERM); proot exits when its root
            // tracee does.
            val pid = pidOf(p).toIntOrNull() ?: ProcTree.prootProcesses(paths.prootBin.path).firstOrNull()
            if (pid != null) ProcTree.descendants(pid).forEach { ProcTree.signal(it, ProcTree.SIGTERM) }
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                AppLog.w("Runtime", "guest did not exit after SIGTERM, killing")
                if (pid != null) ProcTree.descendants(pid).forEach { ProcTree.signal(it, ProcTree.SIGKILL) }
                p.destroyForcibly()
                p.waitFor(5, TimeUnit.SECONDS)
            }
        }
        apiVerified = false
        runPracticePort = null
        killStaleGuests("after stop")
        if (_state.value.isBusyOrRunning || _state.value is RuntimeState.Error) setState(RuntimeState.Stopped("stopped ($reason)"))
        recomputeForeground()
    }

    /** Kills leftovers of earlier runs (guest processes orphaned by a killed proot or a swiped-away app). */
    private fun killStaleGuests(why: String) {
        val own = android.os.Process.myPid()
        val victims = (ProcTree.prootProcesses(paths.prootBin.path) + ProcTree.guestProcesses()).distinct().filter { it != own }
        if (victims.isEmpty()) return
        AppLog.w("Runtime", "killing ${victims.size} stale proot/guest processes ($why): $victims")
        victims.forEach { ProcTree.signal(it, ProcTree.SIGKILL) }
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
        if (android.os.Build.VERSION.SDK_INT >= 33) p.pid().toString()
        else { val f = p.javaClass.getDeclaredField("pid"); f.isAccessible = true; f.get(p).toString() }
    } catch (_: Throwable) { "?" }

    // ---- self-test ----------------------------------------------------------------------------------------------
    /** Hosts the configured mode must be able to reach (DNS + TLS), for the self-test probes. */
    private fun probeHosts(): List<String> {
        val s = settings
        return ProviderConfig.probeHosts(s.authMode, s.endpoint, s.region, s.customBaseUrl)
    }

    /**
     * Runs a short probe under proot: same launcher, same environment as the real start (separate PA_STATE so
     * last-launch.txt is not overwritten), plus DNS and HTTPS probes from inside the guest. Runs automatically
     * on every Error and from the Debug button; output goes to [lastSelfTest] and last-selftest.txt.
     */
    fun selfTest(auto: Boolean = false) {
        if (!selfTestRunning.compareAndSet(false, true)) return
        // Counts as an operation: the foreground service (and the process priority) must not drop while it runs.
        beginOp()
        selfTestStartedAt = System.currentTimeMillis(); _selfTestActive.value = true
        scope.launch {
            val out = StringBuffer()
            try {
                if (!isRootfsValid()) out.append("rootfs not installed yet; press Start first.\n")
                else if (!paths.prootBin.exists() || rootfsInfo["stub_rootfs"] == "true") out.append("proot binary or rootfs missing (stub build?)\n")
                else {
                    paths.runtimeDir.mkdirs(); paths.selfTestState.mkdirs()
                    installLauncher()
                    val probes = probeHosts().joinToString("\n") { h ->
                        "echo \"-- $h\"; getent hosts $h || echo \"DNS FAILED for $h\"; " +
                            "curl -sS -I -m 8 -o /dev/null -w \"https $h http=%{http_code} connect=%{time_connect}s total=%{time_total}s\\n\" https://$h/ 2>&1 || echo \"HTTPS FAILED for $h\""
                    }
                    val script = listOf(
                        "echo '== identity'; uname -a; id; head -n 2 /etc/os-release; hostname",
                        "echo '== /proc and dirs'; echo cpu-stat-readable=\$(cat /proc/stat >/dev/null 2>&1 && echo yes || echo no); ls -ld /tmp /dev/shm /root",
                        "echo '== python imports (same check as the CI smoke test)'; for py in /root/.hermes/installs/*/environments/*/venv/bin/python; do echo \"\$py\"; \"\$py\" -c 'import sys,platform,ssl,aiohttp,google.auth,google.oauth2.service_account,google.oauth2.credentials; print(\"IMPORTS-OK\", sys.version.split()[0], platform.release(), ssl.OPENSSL_VERSION)'; done",
                        "echo '== hermes'; hermes --version 2>&1 | head -n 3",
                        "echo '== network probes from inside the guest'; cat /etc/resolv.conf",
                        probes,
                        "echo SELFTEST-DONE",
                    ).joinToString("\n")
                    val env = launchEnv(paths.selfTestState)
                    env["PA_GUEST_MARKER"] = "selftest" // its guests carry PA_IN_PROOT=selftest: killStaleGuests leaves them alone
                    env["PA_SELFTEST"] = "1"            // the launcher must not rewrite files a live runtime uses (resolv.conf)
                    val pb = ProcessBuilder("/system/bin/sh", paths.launcher.path, "--", "/bin/sh", "-c", script)
                    pb.environment().clear(); pb.environment().putAll(env); pb.redirectErrorStream(true)
                    val t0 = SystemClock.elapsedRealtime()
                    val p = pb.start()
                    val reader = Thread { try { p.inputStream.bufferedReader().forEachLine { out.append(it).append('\n') } } catch (_: Exception) { } }
                    reader.start()
                    val finished = p.waitFor(90, TimeUnit.SECONDS)
                    if (!finished) {
                        // Kill the whole probe tree (not just proot, which would orphan the guest processes).
                        pidOf(p).toIntOrNull()?.let { pid -> ProcTree.descendants(pid).forEach { ProcTree.signal(it, ProcTree.SIGKILL) } }
                        p.destroyForcibly()
                        out.append("\n[timed out after 90 s]\n")
                    }
                    reader.join(3000)
                    out.append("\n[exit value ${if (finished) p.exitValue() else -1} after ${(SystemClock.elapsedRealtime() - t0) / 1000}s; noSeccomp=${settings.noSeccomp}; trigger=${if (auto) "automatic (after an error)" else "button"}]\n")
                }
            } catch (e: Exception) {
                out.append("self-test failed to run: ${e.javaClass.simpleName}: ${e.message}\n")
            }
            val text = "${java.util.Date()}\n$out"
            lastSelfTest = text
            try { paths.selfTestState.mkdirs(); paths.lastSelfTestFile.writeText(text) } catch (_: Exception) { }
            AppLog.i("SelfTest", out.toString().take(1500))
            _selfTestText.value = text
            selfTestStartedAt = 0L; _selfTestActive.value = false
            selfTestRunning.set(false)
            endOp()
        }
    }

    // ---- terminal ----------------------------------------------------------------------------------------------
    /**
     * Runs [command] (an argv for the guest, e.g. `bash -c "ls"`) inside the rootfs through the same launcher and environment
     * as the gateway, but with its OWN PA_STATE ([Paths.terminalState]): it never touches the gateway process, its
     * last-launch.txt or the runtime state, and PA_SELFTEST keeps the launcher from rewriting resolv.conf under the running gateway.
     * HOME=/root, HERMES_HOME=/root/.hermes, TERM=dumb, PAGER=cat, NO_COLOR=1; stdin is closed so nothing waits for input.
     *
     * Blocks until the process ends: call from a background thread. [onOutput] receives complete lines (text, isStderr) from two
     * reader threads. Setting [cancel] sends SIGTERM to the process tree, then SIGKILL after 2.5 s. Returns the exit code, or -1
     * when the command could not be started (the reason arrives through [onOutput]).
     *
     * The guest carries the usual PA_IN_PROOT=1 marker, so starting or stopping the gateway (which sweeps stale guests) ends a
     * running terminal command too.
     */
    fun execInGuest(command: List<String>, onOutput: (String, Boolean) -> Unit, cancel: AtomicBoolean): Int {
        if (command.isEmpty()) return -1
        if (rootfsInfo["stub_rootfs"] == "true" || prootInfo["stub_proot"] == "true" || !paths.prootBin.exists()) {
            onOutput(Texts.get(context, R.string.eng_rt_term_stub), true); return -1
        }
        if (!isRootfsValid()) { onOutput(Texts.get(context, R.string.eng_rt_term_not_installed), true); return -1 }
        beginOp()
        wakeLocks.acquire("terminal", 30 * 60_000L)
        try {
            paths.runtimeDir.mkdirs(); paths.terminalState.mkdirs()
            installLauncher()
            val env = launchEnv(paths.terminalState)
            env["PA_SELFTEST"] = "1"
            val cmd = listOf(
                "/system/bin/sh", paths.launcher.path,
                "-E", "HOME=/root", "-E", "HERMES_HOME=/root/.hermes", "-E", "TERM=dumb",
                "-E", "PAGER=cat", "-E", "GIT_PAGER=cat", "-E", "NO_COLOR=1", "--",
            ) + command
            val pb = ProcessBuilder(cmd)
            pb.environment().clear(); pb.environment().putAll(env)
            pb.directory(paths.terminalState)
            val p = try { pb.start() } catch (e: Exception) {
                onOutput(Texts.get(context, R.string.eng_rt_term_shell_failed, "${e.javaClass.simpleName}: ${e.message}"), true); return -1
            }
            try { p.outputStream.close() } catch (_: Exception) { }
            fun pump(stream: java.io.InputStream, isErr: Boolean): Thread = Thread {
                try {
                    val r = java.io.InputStreamReader(stream, Charsets.UTF_8)
                    val sb = StringBuilder(); val buf = CharArray(4096)
                    while (true) {
                        val n = r.read(buf)
                        if (n < 0) break
                        for (i in 0 until n) { if (buf[i] == '\n') { onOutput(sb.toString(), isErr); sb.setLength(0) } else sb.append(buf[i]) }
                        if (sb.length > 64 * 1024) { onOutput(sb.toString(), isErr); sb.setLength(0) } // runaway line without a newline
                    }
                    if (sb.isNotEmpty()) onOutput(sb.toString(), isErr)
                } catch (_: Exception) { }
            }.also { it.isDaemon = true; it.start() }
            val outThread = pump(p.inputStream, false)
            val errThread = pump(p.errorStream, true)
            val pid = pidOf(p).toIntOrNull()
            fun signalTree(sig: Int) {
                if (pid == null) return
                ProcTree.descendants(pid).forEach { ProcTree.signal(it, sig) }
                if (sig == ProcTree.SIGKILL) ProcTree.signal(pid, sig)
            }
            var termAt = 0L
            try {
                while (!p.waitFor(150, TimeUnit.MILLISECONDS)) {
                    if (!cancel.get()) continue
                    val now = SystemClock.elapsedRealtime()
                    if (termAt == 0L) { termAt = now; signalTree(ProcTree.SIGTERM) }
                    else if (now - termAt > 2500) { signalTree(ProcTree.SIGKILL); p.destroyForcibly() }
                }
            } finally {
                if (p.isAlive) { signalTree(ProcTree.SIGKILL); p.destroyForcibly() }
            }
            outThread.join(2000); errThread.join(2000)
            return p.exitValue()
        } finally {
            wakeLocks.release("terminal")
            endOp()
        }
    }

    companion object {
        /** The start of the step text while the process is alive but the API is not up yet (HomeScreen and the runtime section look for it). */
        const val STILL_STARTING = "still starting"
    }

    // ---- construction ---------------------------------------------------------------------------------
    // KEEP THIS BLOCK THE LAST MEMBER OF THE CLASS. Kotlin runs property initialisers and init blocks in textual
    // order. refreshInstalledState() reads rootfsInfo, a `by lazy` property whose delegate field does not exist yet
    // while an init block above its declaration runs: build 8 crashed at start with a NullPointerException in
    // Lazy.getValue() whenever a rootfs was installed. Everything else it reaches (state flow, history, AppLog) was
    // already declared above the old position, and selfTest() cannot be reached from here (it needs the Error
    // state; the constructor only moves between NotInstalled and Stopped). Last place = safe for properties added later.
    // Covered by RuntimeManagerInitTest.
    init { refreshInstalledState() }
}
