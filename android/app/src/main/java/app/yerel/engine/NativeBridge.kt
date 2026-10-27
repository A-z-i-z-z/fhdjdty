package app.yerel.engine

import androidx.annotation.Keep

/** Called from native code while a model loads; return false to abort. */
@Keep
fun interface LoadProgress {
    fun onProgress(progress: Float): Boolean
}

/** Receives streaming generation updates as UTF-8 JSON. */
@Keep
fun interface UpdateListener {
    fun onUpdate(json: ByteArray)
}

/** JNI surface of libyerel.so. Strings travel as UTF-8 bytes; every call except cancel() must run on the engine thread. */
object NativeBridge {
    init {
        System.loadLibrary("yerel")
    }

    external fun initBackends(libDir: ByteArray)
    external fun systemInfo(): ByteArray
    external fun load(path: ByteArray, nGpuLayers: Int, mmap: Boolean, mlock: Boolean, progress: LoadProgress?): ByteArray
    external fun createContext(paramsJson: ByteArray): ByteArray
    external fun modelInfo(): ByteArray
    external fun generate(messages: ByteArray, tools: ByteArray, params: ByteArray, listener: UpdateListener?): ByteArray
    external fun cancel()
    external fun countTokens(text: ByteArray): Int
    external fun unload()
}

internal fun String.utf8() = toByteArray(Charsets.UTF_8)
internal fun ByteArray.utf8() = String(this, Charsets.UTF_8)
