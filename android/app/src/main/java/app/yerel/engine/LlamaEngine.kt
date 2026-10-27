package app.yerel.engine

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import app.yerel.core.AppSettings
import app.yerel.core.LlmBackend
import app.yerel.core.ModelEntry
import app.yerel.core.contextParamsJson
import app.yerel.core.loadParamsJson
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Facts about the loaded model reported by llama.cpp. */
data class LoadedModelInfo(
    val name: String,
    val arch: String,
    val description: String,
    val nParams: Long,
    val nCtxTrain: Int,
    val nCtx: Int,
    val recurrent: Boolean,
    val supportsThinking: Boolean,
    val supportsTools: Boolean,
    /** the device the layers run on, e.g. "CPU" or "QUALCOMM Adreno(TM) 750 (GPUOpenCL)" */
    val device: String = "CPU",
    val deviceNote: String = "",
    val hasMtp: Boolean = false,
    /** active speculative decoding methods, empty when off */
    val spec: String = "",
    val specNote: String = "",
) {
    companion object {
        fun parse(json: String): LoadedModelInfo? = try {
            val o = JSONObject(json)
            if (o.has("error")) null else LoadedModelInfo(
                name = o.optString("name"),
                arch = o.optString("arch"),
                description = o.optString("desc"),
                nParams = o.optLong("n_params"),
                nCtxTrain = o.optInt("n_ctx_train"),
                nCtx = o.optInt("n_ctx"),
                recurrent = o.optBoolean("recurrent"),
                supportsThinking = o.optBoolean("supports_thinking"),
                supportsTools = o.optBoolean("supports_tools"),
                device = o.optString("device", "CPU"),
                deviceNote = o.optString("device_note"),
                hasMtp = o.optBoolean("has_mtp"),
                spec = o.optString("spec"),
                specNote = o.optString("spec_note"),
            )
        } catch (e: Exception) {
            null
        }
    }
}

data class DeviceInfo(val name: String, val description: String, val kind: String, val memTotal: Long) {
    companion object {
        fun parse(json: String): List<DeviceInfo> = try {
            val a = org.json.JSONArray(json)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                DeviceInfo(o.optString("name"), o.optString("desc"), o.optString("kind"), o.optLong("mem_total"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

sealed class EngineStatus {
    data object Empty : EngineStatus()
    data class Loading(val modelId: String, val progress: Float) : EngineStatus()
    data class Ready(val modelId: String, val info: LoadedModelInfo?) : EngineStatus()
    data class Failed(val modelId: String, val message: String) : EngineStatus()
}

class EngineException(message: String) : Exception(message)

/**
 * Owns the native model and context. All native work runs on one thread with a large stack
 * (template rendering is recursive), exposed as [dispatcher].
 */
class LlamaEngine(private val context: Context) : LlmBackend {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(null, r, "llama-engine", 16L shl 20) }
    val dispatcher = executor.asCoroutineDispatcher()

    private val _status = MutableStateFlow<EngineStatus>(EngineStatus.Empty)
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    private var initialized = false
    private var modelId: String? = null
    private var loadKey: String? = null
    private var contextKey: String? = null
    private var linkedFd: ParcelFileDescriptor? = null
    @Volatile private var abortLoad = false

    /** llama.cpp backend/CPU feature summary, available after [warmUp]. */
    @Volatile var systemInfo: String = ""
        private set

    private fun ensureInit() {
        if (initialized) return
        NativeBridge.initBackends(context.applicationInfo.nativeLibraryDir.utf8())
        systemInfo = NativeBridge.systemInfo().utf8()
        initialized = true
    }

    /** Loads the native libraries and CPU backend off the main thread. */
    suspend fun warmUp() = withContext(dispatcher) { ensureInit() }

    /** Accelerators found on this phone ("gpu", "npu"), loading their backends on first use. */
    suspend fun probeDevices(): List<DeviceInfo> = withContext(dispatcher) {
        ensureInit()
        prepareHexagon()
        NativeBridge.loadBackend("gpu".utf8())
        NativeBridge.loadBackend("npu".utf8())
        DeviceInfo.parse(NativeBridge.devices().utf8())
    }

    /**
     * The NPU kernels (HTP skels) run on the DSP, which loads them from a directory on ADSP_LIBRARY_PATH;
     * they ship inside the APK next to the other native libraries and are copied out once per app version.
     */
    private fun prepareHexagon() {
        val dir = File(context.filesDir, "htp")
        try {
            val apks = listOfNotNull(context.applicationInfo.sourceDir) + (context.applicationInfo.splitSourceDirs ?: emptyArray())
            for (apk in apks) {
                java.util.zip.ZipFile(apk).use { zip ->
                    for (e in zip.entries()) {
                        val name = e.name.substringAfterLast('/')
                        if (!e.name.startsWith("lib/arm64-v8a/") || !name.startsWith("libggml-htp-")) continue
                        val out = File(dir, name)
                        if (out.length() == e.size) continue
                        dir.mkdirs()
                        zip.getInputStream(e).use { input -> out.outputStream().use { input.copyTo(it) } }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "cannot extract HTP skels", e)
        }
        val paths = listOf(dir.path, context.applicationInfo.nativeLibraryDir, "/vendor/dsp/cdsp", "/vendor/lib/rfsa/adsp", "/system/lib/rfsa/adsp", "/vendor/dsp", "/dsp")
        try {
            android.system.Os.setenv("ADSP_LIBRARY_PATH", paths.joinToString(";"), true)
        } catch (e: Exception) {
            Log.w(TAG, "cannot set ADSP_LIBRARY_PATH", e)
        }
    }

    /** Loads [entry] (if not already loaded with the same options) and (re)creates its context. */
    suspend fun ensureLoaded(entry: ModelEntry, settings: AppSettings, draft: ModelEntry? = null) = withContext(dispatcher) {
        ensureInit()
        val draftPath = draft?.takeIf { it.id != entry.id && settings.specMode in setOf("auto", "draft") }?.path
        val wantLoad = settings.loadKey(draftPath)
        if (modelId != entry.id || loadKey != wantLoad || _status.value is EngineStatus.Failed) {
            unloadNow()
            abortLoad = false
            _status.value = EngineStatus.Loading(entry.id, 0f)
            val path = try {
                resolvePath(entry)
            } catch (e: Exception) {
                fail(entry.id, "Model dosyası açılamadı: ${e.message}")
            }
            if (settings.device == "npu") prepareHexagon()
            var last = 0f
            val err = NativeBridge.load(path.utf8(), settings.loadParamsJson(draftPath).utf8()) { p ->
                if (p - last >= 0.02f || p >= 1f) {
                    last = p
                    _status.value = EngineStatus.Loading(entry.id, p)
                }
                !abortLoad
            }.utf8()
            if (err.isNotEmpty()) {
                closeLinked()
                fail(entry.id, err)
            }
            modelId = entry.id
            loadKey = wantLoad
            contextKey = null
        }
        val wantCtx = settings.contextKey()
        if (contextKey != wantCtx) {
            _status.value = EngineStatus.Loading(entry.id, 1f)
            val cpus = Runtime.getRuntime().availableProcessors()
            val err = NativeBridge.createContext(settings.contextParamsJson(cpus).utf8()).utf8()
            if (err.isNotEmpty()) {
                contextKey = null
                fail(entry.id, err)
            }
            contextKey = wantCtx
        }
        val info = LoadedModelInfo.parse(NativeBridge.modelInfo().utf8())
        _status.value = EngineStatus.Ready(entry.id, info)
        info
    }

    private fun fail(id: String, message: String): Nothing {
        Log.w(TAG, "load failed: $message")
        _status.value = EngineStatus.Failed(id, message)
        throw EngineException(message)
    }

    private fun resolvePath(entry: ModelEntry): String {
        entry.path?.let { p ->
            if (!File(p).canRead()) throw EngineException("dosya bulunamadı ($p)")
            return p
        }
        val uri = Uri.parse(entry.uri ?: throw EngineException("model yolu yok"))
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: throw EngineException("izin yok")
        linkedFd = pfd
        return "/proc/self/fd/${pfd.fd}"
    }

    private fun closeLinked() {
        try { linkedFd?.close() } catch (_: Exception) {}
        linkedFd = null
    }

    private fun unloadNow() {
        if (modelId != null) NativeBridge.unload()
        closeLinked()
        modelId = null
        loadKey = null
        contextKey = null
        _status.value = EngineStatus.Empty
    }

    suspend fun unload() {
        abortLoad = true
        NativeBridge.cancel()
        withContext(dispatcher) { unloadNow() }
    }

    /** Thread-safe: stops a running generation at the next token. */
    fun cancel() {
        abortLoad = true
        NativeBridge.cancel()
    }

    val loadedModelId get() = (status.value as? EngineStatus.Ready)?.modelId

    /** Must be called on [dispatcher]. */
    override fun generate(messagesJson: String, toolsJson: String, paramsJson: String, onUpdate: (String) -> Unit): String =
        NativeBridge.generate(messagesJson.utf8(), toolsJson.utf8(), paramsJson.utf8()) { onUpdate(it.utf8()) }.utf8()

    /** Must be called on [dispatcher]. */
    fun countTokens(text: String) = NativeBridge.countTokens(text.utf8())

    companion object {
        private const val TAG = "LlamaEngine"
    }
}
