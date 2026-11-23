package dev.pocketagent.app.voice

import android.content.Context
import dev.pocketagent.app.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

sealed interface TtsModelState {
    data object NotInstalled : TtsModelState
    data class Downloading(
        val fileIndex: Int,
        val fileCount: Int,
        val fileName: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : TtsModelState {
        val fraction: Float get() = if (totalBytes <= 0L) 0f else (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    }
    data class Installed(val totalBytes: Long) : TtsModelState
    data class Error(val message: String) : TtsModelState
}

/**
 * Manages the on-device Supertonic 3 INT8 multilingual TTS model (~139 MB, 31 languages including EN/FR/TR)
 * stored under `filesDir/tts/supertonic-3-int8/` (outside rootfs, so rootfs upgrades preserve it).
 */
class SupertonicModelStore(context: Context) {
    val modelDir: File = File(File(context.filesDir, "tts"), "supertonic-3-int8")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<TtsModelState>(inspectDisk())
    val state: StateFlow<TtsModelState> = _state.asStateFlow()

    private val dlLock = Any()
    @Volatile private var downloadJob: Job? = null
    @Volatile private var activeCancel: AtomicBoolean? = null
    @Volatile private var activeConn: HttpURLConnection? = null

    fun isInstalled(): Boolean = REQUIRED_FILES.all { f ->
        val file = File(modelDir, f.name)
        file.isFile && file.length() >= f.minBytes
    }

    fun refresh() {
        if (downloadJob?.isActive == true && activeCancel?.get() != true) return
        _state.value = inspectDisk()
    }

    private fun inspectDisk(): TtsModelState =
        if (isInstalled()) TtsModelState.Installed(REQUIRED_FILES.sumOf { File(modelDir, it.name).length() })
        else TtsModelState.NotInstalled

    fun startDownload(onInstalled: () -> Unit = {}) {
        val myCancel = AtomicBoolean(false)
        synchronized(dlLock) {
            if (downloadJob?.isActive == true && activeCancel?.get() != true) return
            activeCancel = myCancel
        }
        var currentJob: Job? = null
        currentJob = scope.launch {
            var currentPart: File? = null
            try {
                modelDir.mkdirs()
                val totalExpected = REQUIRED_FILES.sumOf { it.approxBytes }
                var completedBytes = 0L
                for ((idx, spec) in REQUIRED_FILES.withIndex()) {
                    if (!isActive || myCancel.get()) throw CancellationException("cancelled")
                    val dst = File(modelDir, spec.name)
                    if (dst.isFile && dst.length() >= spec.minBytes) {
                        completedBytes += dst.length()
                        continue
                    }
                    val part = File(modelDir, spec.name + ".part")
                    currentPart = part
                    if (!myCancel.get()) {
                        _state.value = TtsModelState.Downloading(idx + 1, REQUIRED_FILES.size, spec.name, completedBytes, totalExpected)
                    }
                    downloadFile(BASE_URL + spec.name, part, myCancel) { fileBytes ->
                        if (!myCancel.get()) {
                            _state.value = TtsModelState.Downloading(
                                idx + 1, REQUIRED_FILES.size, spec.name,
                                completedBytes + fileBytes, maxOf(totalExpected, completedBytes + fileBytes),
                            )
                        }
                    }
                    if (!isActive || myCancel.get()) {
                        part.delete()
                        throw CancellationException("cancelled")
                    }
                    if (part.length() < spec.minBytes) {
                        part.delete()
                        throw IllegalStateException("${spec.name} downloaded incomplete (${part.length()} bytes)")
                    }
                    if (dst.exists()) dst.delete()
                    if (!part.renameTo(dst)) {
                        part.copyTo(dst, overwrite = true)
                        part.delete()
                    }
                    currentPart = null
                    completedBytes += dst.length()
                }
                if (myCancel.get()) throw CancellationException("cancelled")
                val finalState = inspectDisk()
                _state.value = finalState
                if (finalState is TtsModelState.Installed) {
                    AppLog.i("TTS", "Supertonic 3 INT8 installed (${finalState.totalBytes} bytes)")
                    onInstalled()
                }
            } catch (_: CancellationException) {
                runCatching { currentPart?.delete() }
                if (activeCancel === myCancel || activeCancel == null) {
                    _state.value = inspectDisk()
                }
            } catch (e: Throwable) {
                runCatching { currentPart?.delete() }
                if (myCancel.get()) {
                    if (activeCancel === myCancel || activeCancel == null) {
                        _state.value = inspectDisk()
                    }
                } else {
                    AppLog.e("TTS", "Supertonic 3 download failed", e)
                    _state.value = TtsModelState.Error(e.message ?: e.javaClass.simpleName)
                }
            } finally {
                synchronized(dlLock) {
                    if (downloadJob === currentJob) downloadJob = null
                    if (activeCancel === myCancel) activeCancel = null
                }
            }
        }
        synchronized(dlLock) {
            if (activeCancel === myCancel) downloadJob = currentJob else currentJob.cancel()
        }
    }

    fun cancelDownload() {
        val job: Job?
        val conn: HttpURLConnection?
        synchronized(dlLock) {
            activeCancel?.set(true)
            activeCancel = null
            job = downloadJob
            downloadJob = null
            conn = activeConn
            activeConn = null
        }
        job?.cancel()
        if (conn != null) {
            scope.launch { runCatching { conn.disconnect() } }
        }
        runCatching {
            modelDir.listFiles { f -> f.name.endsWith(".part") }?.forEach { it.delete() }
        }
        _state.value = inspectDisk()
    }

    fun deleteModel() {
        cancelDownload()
        runCatching { modelDir.deleteRecursively() }
        _state.value = TtsModelState.NotInstalled
    }

    private fun downloadFile(
        urlStr: String,
        dst: File,
        cancelFlag: AtomicBoolean,
        onProgress: (Long) -> Unit,
    ) {
        var current = urlStr
        var redirects = 0
        var conn: HttpURLConnection? = null
        try {
            while (redirects < 8) {
                if (cancelFlag.get()) throw CancellationException("cancelled")
                val c = (URL(current).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 20_000
                    readTimeout = 45_000
                    setRequestProperty("User-Agent", "Agiri/1.0 (Android)")
                }
                synchronized(dlLock) {
                    if (cancelFlag.get()) {
                        runCatching { c.disconnect() }
                        throw CancellationException("cancelled")
                    }
                    activeConn = c
                }
                val code = c.responseCode
                if (cancelFlag.get()) {
                    runCatching { c.disconnect() }
                    throw CancellationException("cancelled")
                }
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: throw IllegalStateException("HTTP $code without Location")
                    c.disconnect()
                    current = URL(URL(current), loc).toString()
                    redirects++
                    continue
                }
                if (code !in 200..299) {
                    c.disconnect()
                    throw IllegalStateException("HTTP $code downloading ${dst.name}")
                }
                conn = c
                break
            }
            val active = conn ?: throw IllegalStateException("Too many redirects")
            val buf = ByteArray(64 * 1024)
            var written = 0L
            var lastReport = 0L
            active.inputStream.use { input ->
                FileOutputStream(dst).use { out ->
                    while (true) {
                        if (cancelFlag.get() || Thread.currentThread().isInterrupted) {
                            throw CancellationException("cancelled")
                        }
                        val n = input.read(buf)
                        if (n <= 0) break
                        if (cancelFlag.get()) throw CancellationException("cancelled")
                        out.write(buf, 0, n)
                        written += n
                        if (written - lastReport >= 256 * 1024) {
                            lastReport = written
                            if (!cancelFlag.get()) onProgress(written)
                        }
                    }
                    out.flush()
                }
            }
            if (!cancelFlag.get()) onProgress(written)
        } finally {
            synchronized(dlLock) {
                if (activeConn === conn) activeConn = null
            }
            runCatching { conn?.disconnect() }
        }
    }

    data class ModelFileSpec(val name: String, val approxBytes: Long, val minBytes: Long)

    companion object {
        const val BASE_URL = "https://huggingface.co/csukuangfj2/sherpa-onnx-supertonic-3-tts-int8-2026-05-11/resolve/main/"
        const val APPROX_TOTAL_MB = 139

        val REQUIRED_FILES: List<ModelFileSpec> = listOf(
            ModelFileSpec("tts.json", 8_253L, 1_000L),
            ModelFileSpec("unicode_indexer.bin", 262_144L, 100_000L),
            ModelFileSpec("voice.bin", 517_168L, 100_000L),
            ModelFileSpec("duration_predictor.int8.onnx", 3_700_147L, 1_000_000L),
            ModelFileSpec("vocoder.int8.onnx", 25_991_073L, 10_000_000L),
            ModelFileSpec("text_encoder.int8.onnx", 36_416_150L, 10_000_000L),
            ModelFileSpec("vector_estimator.int8.onnx", 78_400_833L, 30_000_000L),
        )
    }
}
