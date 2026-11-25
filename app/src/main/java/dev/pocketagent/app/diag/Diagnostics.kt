package dev.pocketagent.app.diag

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.system.Os
import android.system.OsConstants
import dev.pocketagent.app.BuildConfig
import dev.pocketagent.app.runtime.ExitRecord
import dev.pocketagent.app.runtime.RuntimeManager
import dev.pocketagent.app.util.AppLog
import dev.pocketagent.app.util.FileUtil
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Builds the text bundle that is the primary debugging tool for failures on the device. */
class Diagnostics(private val context: Context, private val rt: RuntimeManager) {
    private val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US)
    private fun t(ms: Long) = ts.format(Date(ms))

    class Result(val text: String, val truncated: Boolean, val fullLength: Int)

    /** Complete dump (for "Share as file"). */
    fun buildFull(): String = redact(assemble(logLines = 1500, apiLines = 60, cap = 400_000))

    /**
     * Dump sized for the clipboard (and messengers/GitHub comments, which choke around 65,536 characters):
     * the important sections come first; bulky log sections are shortened step by step until the whole
     * dump fits, so the end of the dump is never simply cut off.
     */
    fun buildForClipboard(maxChars: Int = CLIPBOARD_LIMIT): Result {
        var full = ""
        for ((lines, cap) in listOf(400 to 10_000, 200 to 6_000, 100 to 3_500, 50 to 2_000, 25 to 1_000)) {
            full = redact(assemble(logLines = lines, apiLines = minOf(lines, 40), cap = cap))
            if (full.length <= maxChars) {
                val note = if (lines < 400) "NOTE: log sections shortened to fit the clipboard (${cap} chars each); use 'Share as file' for the full dump.\n" else ""
                return Result(note + full, false, full.length)
            }
        }
        val cut = full.substring(0, maxChars - 200)
        return Result(cut + "\n\n[TRUNCATED: clipboard limit $maxChars chars, dump was ${full.length} chars. Use 'Share as file' for the complete dump.]\n", true, full.length)
    }

    private fun redact(s: String) = Diag.redactor.redact(s)

    private fun assemble(logLines: Int, apiLines: Int, cap: Int): String {
        val sb = StringBuilder()
        fun h(title: String) { sb.append("\n==== ").append(title).append(" ====\n") }
        // Each section is redacted BEFORE it is capped. Head-oriented sections (state, launch header, self-test...)
        // keep their first characters; log tails (tail = true) keep their last.
        fun safe(title: String, tail: Boolean = false, block: () -> String) {
            h(title)
            val text = try { block() } catch (e: Throwable) { "(section failed: ${e.javaClass.simpleName}: ${e.message})" }
            sb.append(SectionFormat.redactThenCap(Diag.redactor, text, cap, keepHead = !tail)).append('\n')
        }
        val now = System.currentTimeMillis()
        sb.append("Agirî diagnostics\ngenerated: ${t(now)}\n")
        sb.append("state now: ${rt.state.value.label()}; process alive: ${rt.processAlive()}; self-test: ${if (rt.selfTestStartedAt > 0) "RUNNING since ${t(rt.selfTestStartedAt)} (wait, then copy again)" else "idle"}\n")

        safe("App") {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            val ai = context.applicationInfo
            """
            package: ${context.packageName}
            version: ${pi.versionName} (code ${pi.longVersionCode})
            git commit: ${BuildConfig.GIT_COMMIT}
            build type: ${BuildConfig.BUILD_TYPE}  debuggable=${(ai.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0}
            targetSdk: ${ai.targetSdkVersion}  minSdk: ${ai.minSdkVersion ?: "?"}
            installed: ${t(pi.firstInstallTime)}  updated: ${t(pi.lastUpdateTime)}
            nativeLibraryDir: ${ai.nativeLibraryDir}
            pid: ${android.os.Process.myPid()}  uid: ${android.os.Process.myUid()}
            """.trimIndent()
        }

        safe("Device") {
            val page = try { Os.sysconf(OsConstants._SC_PAGESIZE) } catch (_: Throwable) { -1L }
            """
            model: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}, ${Build.PRODUCT})
            android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}, codename ${Build.VERSION.CODENAME})
            security patch: ${Build.VERSION.SECURITY_PATCH}
            build: ${Build.DISPLAY}
            fingerprint: ${Build.FINGERPRINT}
            supported ABIs: ${Build.SUPPORTED_ABIS.joinToString()}
            page size: $page bytes
            cpu cores: ${Runtime.getRuntime().availableProcessors()}
            kernel: ${System.getProperty("os.version")}
            """.trimIndent()
        }

        safe("Runtime state") {
            val hist = rt.stateHistory().joinToString("\n") { "  ${t(it.timeMillis)}  ${it.label}" }
            "current: ${rt.state.value.label()}\nprocess alive: ${rt.processAlive()} (uptime ${rt.processUptimeMillis() / 1000}s)\nhistory (oldest first):\n$hist"
        }

        safe("Settings and provider configuration") {
            val s = rt.settings
            val inputs = dev.pocketagent.app.runtime.ProviderConfig.savedInputs(s, rt.secrets)
            val probs = dev.pocketagent.app.runtime.ProviderConfig.savedProblems(s, rt.secrets)
            // Presence only, never a value: every key is also registered with the redactor.
            val otherKeys = listOf(
                dev.pocketagent.app.runtime.AuthMode.OPENROUTER, dev.pocketagent.app.runtime.AuthMode.ANTHROPIC,
                dev.pocketagent.app.runtime.AuthMode.OPENAI, dev.pocketagent.app.runtime.AuthMode.CUSTOM,
            ).joinToString(" ") { "${it.name.lowercase()}=${if (rt.secrets.hasProviderKey(it)) "present" else "absent"}" }
            """
            configured: ${dev.pocketagent.app.runtime.ProviderConfig.describe(inputs)}
            config problems: ${if (probs.isEmpty()) "none" else probs.joinToString()}
            PROOT_NO_SECCOMP toggle: ${s.noSeccomp}
            service account imported: ${rt.secrets.hasServiceAccount}  project=${s.saProjectId.ifEmpty { "-" }} email=${s.saMaskedEmail.ifEmpty { "-" }}
            user (authorized_user) credentials imported: ${rt.secrets.hasUserCredentials}  ${s.userCredMasked}
            Gemini API key: ${if (rt.secrets.hasGeminiApiKey) "present (${rt.secrets.geminiApiKey().length} chars)" else "absent"}
            other provider API keys: $otherKeys  custom base url: ${dev.pocketagent.app.runtime.ProviderConfig.baseUrlForReport(s.customBaseUrl)}
            API server key: ${if (rt.paths.apiKeyFile.isFile) "present (${rt.paths.apiKeyFile.length()} bytes)" else "not generated yet"}
            guest credential files: sa=${rt.paths.guestServiceAccount.isFile} adc=${rt.paths.guestAdcFile.isFile}
            """.trimIndent()
        }

        safe("Child process exits") {
            val e = rt.exitRecords()
            if (e.isEmpty()) "(none recorded)" else e.joinToString("\n") { r ->
                "  ${t(r.timeMillis)}  ${r.describe()}" + (ExitRecord.hint(r.rawExitValue)?.let { "\n      hint: $it" } ?: "")
            }
        }

        safe("Historical process exit reasons (ActivityManager)") {
            if (Build.VERSION.SDK_INT < 30) "(needs Android 11+)" else {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val list = am.getHistoricalProcessExitReasons(context.packageName, 0, 20)
                if (list.isEmpty()) "(none)" else list.joinToString("\n") { i ->
                    "  ${t(i.timestamp)}  reason=${reasonName(i.reason)}(${i.reason}) status=${i.status} importance=${i.importance} pss=${i.pss}KB rss=${i.rss}KB pid=${i.pid}  desc=${i.description}"
                }
            }
        }

        safe("Memory samples and Android process-killer settings", tail = true) {
            val samples = rt.memorySamples().takeLast(40)
            val gs = { name: String -> try { android.provider.Settings.Global.getString(context.contentResolver, name) ?: "(absent)" } catch (e: Throwable) { "(unreadable: ${e.javaClass.simpleName})" } }
            buildString {
                appendLine("settings_enable_monitor_phantom_procs = ${gs("settings_enable_monitor_phantom_procs")}")
                appendLine("cached_apps_freezer = ${gs("cached_apps_freezer")}")
                appendLine("max_phantom_processes (device_config) not readable by apps; see exit reasons above.")
                appendLine("guest process count and total RSS, every ~30 s while Hermes runs (oldest first):")
                if (samples.isEmpty()) append("  (no samples)") else samples.forEach { appendLine("  ${t(it.timeMillis)}  processes=${it.guestProcesses}  rss=${FileUtil.formatBytes(it.rssKb * 1024)}") }
            }
        }
        safe("Launch command and environment") {
            val p = rt.paths
            buildString {
                appendLine("process command (app -> sh): ${rt.lastLaunchCommand.joinToString(" ")}")
                appendLine("environment given to sh:"); rt.lastLaunchEnv.forEach { (k, v) -> appendLine("  $k=$v") }
                appendLine("resolved proot command line (written by the launcher):")
                append(if (p.lastLaunch.isFile) p.lastLaunch.readText().trim() else "(not launched yet)")
            }
        }

        safe("Self-test output (proot sanity + DNS/HTTPS probes from inside the guest)") {
            val since = rt.selfTestStartedAt
            val running = if (since > 0) "*** SELF-TEST IS STILL RUNNING (since ${t(since)}, ${(System.currentTimeMillis() - since) / 1000} s ago): wait for it to finish, then copy diagnostics again. Output below, if any, is from an EARLIER run. ***\n" else ""
            running + rt.lastSelfTest.ifEmpty { rt.paths.lastSelfTestFile.takeIf { it.isFile }?.readText() ?: "(not run yet; it runs automatically after any error, or use Debug > Run proot self-test)" } }
        safe("runtime.log: first lines of the latest start") { FileUtil.headFromLastMarker(rt.paths.runtimeLog, " start build=", 60) }
        safe("proot + Hermes stdout/stderr (runtime.log, tail $logLines)", tail = true) { FileUtil.tail(rt.paths.runtimeLog, logLines) }

        safe("Power and network") {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val lp = cm.getLinkProperties(cm.activeNetwork)
            """
            ignoring battery optimizations: ${pm.isIgnoringBatteryOptimizations(context.packageName)}
            power save mode: ${pm.isPowerSaveMode}  device idle: ${pm.isDeviceIdleMode}
            battery: ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%
            wake lock held by app: ${rt.wakeLocks.isHeld}
            active network DNS: ${lp?.dnsServers?.joinToString { it.hostAddress ?: "?" }}  private DNS: ${lp?.isPrivateDnsActive}
            """.trimIndent()
        }

        safe("Memory and storage") {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            val st = StatFs(context.filesDir.path)
            """
            RAM: total ${FileUtil.formatBytes(mi.totalMem)}, available ${FileUtil.formatBytes(mi.availMem)}, low=${mi.lowMemory}, threshold ${FileUtil.formatBytes(mi.threshold)}
            app data dir free: ${FileUtil.formatBytes(st.availableBytes)} of ${FileUtil.formatBytes(st.totalBytes)}
            JVM heap: used ${FileUtil.formatBytes(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())} max ${FileUtil.formatBytes(Runtime.getRuntime().maxMemory())}
            /proc/meminfo head: ${FileUtil.tail(File("/proc/meminfo"), 3).replace("\n", " | ")}
            """.trimIndent()
        }

        safe("Bundled runtime") {
            val p = rt.paths
            fun fileLine(f: File) = if (f.exists()) "${f.path}  ${f.length()} bytes  exec=${f.canExecute()}" else "${f.path}  MISSING"
            buildString {
                appendLine("rootfs.info (APK):"); rt.rootfsInfo.forEach { (k, v) -> appendLine("  $k=$v") }
                appendLine("proot.info (APK):"); rt.prootInfo.forEach { (k, v) -> appendLine("  $k=$v") }
                appendLine("rootfs stamp on disk: ${if (p.rootfsStamp.isFile) p.rootfsStamp.readText().trim() else "ABSENT (not installed)"}  valid=${rt.isRootfsValid()}")
                appendLine("rootfs staging dir exists: ${p.rootfsStaging.exists()}")
                appendLine("last extraction: ${rt.lastExtractStats.ifEmpty { "(none this process)" }}")
                appendLine(fileLine(p.prootBin)); appendLine(fileLine(p.prootLoader)); appendLine(fileLine(p.launcher))
                appendLine("native lib dir listing: ${p.nativeLibDir.list()?.sorted()?.joinToString()}")
                val guestId = File(p.rootfs, "etc/pocketagent-rootfs")
                appendLine("in-rootfs build info: ${if (guestId.isFile) guestId.readText().trim().replace("\n", " ") else "missing"}")
                for (rel in listOf("usr/bin/env", "bin/bash", "root/.local/bin/hermes", "root/.hermes/hermes-agent/hermes", "etc/resolv.conf")) {
                    val f = File(p.rootfs, rel)
                    appendLine("  rootfs/$rel: ${if (java.nio.file.Files.exists(f.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) "exists" else "MISSING"}")
                }
                append("  resolv.conf: ${File(p.rootfs, "etc/resolv.conf").takeIf { it.isFile }?.readText()?.trim()?.replace("\n", "; ")}")
            }
        }

        safe("App log (tail $logLines)", tail = true) { AppLog.tail(logLines) }
        safe("HERMES_HOME/logs", tail = true) {
            val dir = rt.paths.hermesLogs
            val files = dir.walkTopDown().filter { it.isFile && it.length() > 0 }.sortedByDescending { it.lastModified() }.take(6).toList()
            if (files.isEmpty()) "(no log files under ${dir.path})" else buildString {
                files.forEach { f ->
                    appendLine("--- ${f.relativeTo(dir)}  ${f.length()} bytes  modified ${t(f.lastModified())}")
                    appendLine(FileUtil.tail(f, maxOf(30, logLines / 3)))
                }
            }
        }

        safe("Recent API requests/responses", tail = true) { ApiLog.snapshot().takeLast(apiLines).joinToString("\n").ifEmpty { "(none)" } }

        safe("Hermes .env (secrets redacted)") {
            val f = rt.paths.hermesEnv
            // Values of KEY/TOKEN/SECRET/PASSWORD variables are masked here whatever their length (the redactor ignores short ones).
            if (f.isFile) Redactor.maskEnvSecrets(f.readText()) else "(missing)"
        }
        safe("Hermes config.yaml") {
            val f = rt.paths.hermesConfig
            if (f.isFile) FileUtil.tail(f, 60) else "(missing)"
        }

        sb.append("\n==== end of diagnostics ====\n")
        return sb.toString()
    }

    private fun reasonName(r: Int) = when (r) {
        1 -> "EXIT_SELF"; 2 -> "SIGNALED"; 3 -> "LOW_MEMORY"; 4 -> "CRASH"; 5 -> "CRASH_NATIVE"; 6 -> "ANR"
        7 -> "INITIALIZATION_FAILURE"; 8 -> "PERMISSION_CHANGE"; 9 -> "EXCESSIVE_RESOURCE_USAGE"; 10 -> "USER_REQUESTED"
        11 -> "USER_STOPPED"; 12 -> "DEPENDENCY_DIED"; 13 -> "OTHER"; 14 -> "FREEZER"; 15 -> "PACKAGE_STATE_CHANGE"
        16 -> "PACKAGE_UPDATED"; else -> "UNKNOWN"
    }

    /** Writes the full dump to cache/diagnostics and returns the file (for sharing). */
    fun writeFullToFile(): File {
        val dir = rt.paths.diagnosticsDir.also { it.mkdirs() }
        dir.listFiles()?.filter { it.name.startsWith("pocketagent-diagnostics-") }?.sortedBy { it.lastModified() }?.dropLast(3)?.forEach { it.delete() }
        val name = "pocketagent-diagnostics-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
        val f = File(dir, name)
        f.writeText(buildFull())
        AppLog.i("Diag", "full dump written (${f.length()} bytes)")
        return f
    }

    companion object { const val CLIPBOARD_LIMIT = 60_000 }
}
