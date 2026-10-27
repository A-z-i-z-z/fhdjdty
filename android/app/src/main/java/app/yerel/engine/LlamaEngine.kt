package app.yerel.engine

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import app.yerel.core.AppSettings
import app.yerel.core.LlmBackend
import app.yerel.core.ModelEntry
import app.yerel.core.contextParamsJson
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
            )
        } catch (e: Exception) {
            null
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

    /** Loads [entry] (if not already loaded with the same options) and (re)creates its context. */
    suspend fun ensureLoaded(entry: ModelEntry, settings: AppSettings) = withContext(dispatcher) {
        ensureInit()
        val wantLoad = settings.loadKey()
        if (modelId != entry.id || loadKey != wantLoad || _status.value is EngineStatus.Failed) {
            unloadNow()
            abortLoad = false
            _status.value = EngineStatus.Loading(entry.id, 0f)
            val path = try {
                resolvePath(entry)
            } catch (e: Exception) {
                fail(entry.id, "Model dosyası açılamadı: ${e.message}")
            }
            var last = 0f
            val err = NativeBridge.load(path.utf8(), 0, settings.useMmap, settings.useMlock) { p ->
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
