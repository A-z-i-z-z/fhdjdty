package app.yerel.core

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Writes minimal GGUF v3 headers for tests. */
class GgufBuilder {
    private val kvs = mutableListOf<ByteArray>()

    private fun le(size: Int, fill: ByteBuffer.() -> Unit): ByteArray =
        ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply(fill).array()

    private fun str(s: String): ByteArray {
        val b = s.toByteArray(Charsets.UTF_8)
        return le(8) { putLong(b.size.toLong()) } + b
    }

    fun string(key: String, value: String) = apply { kvs += str(key) + le(4) { putInt(8) } + str(value) }
    fun u32(key: String, value: Int) = apply { kvs += str(key) + le(4) { putInt(4) } + le(4) { putInt(value) } }
    fun f32(key: String, value: Float) = apply { kvs += str(key) + le(4) { putInt(6) } + le(4) { putFloat(value) } }
    fun bool(key: String, value: Boolean) = apply { kvs += str(key) + le(4) { putInt(7) } + byteArrayOf(if (value) 1 else 0) }

    fun u32Array(key: String, values: List<Int>) = apply {
        kvs += str(key) + le(4) { putInt(9) } + le(4) { putInt(4) } + le(8) { putLong(values.size.toLong()) } +
            values.fold(ByteArray(0)) { acc, v -> acc + le(4) { putInt(v) } }
    }

    fun boolArray(key: String, values: List<Boolean>) = apply {
        kvs += str(key) + le(4) { putInt(9) } + le(4) { putInt(7) } + le(8) { putLong(values.size.toLong()) } +
            values.map { if (it) 1.toByte() else 0.toByte() }.toByteArray()
    }

    fun stringArray(key: String, values: List<String>) = apply {
        kvs += str(key) + le(4) { putInt(9) } + le(4) { putInt(8) } + le(8) { putLong(values.size.toLong()) } +
            values.fold(ByteArray(0)) { acc, v -> acc + str(v) }
    }

    fun build(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(le(4) { putInt(0x46554747) })
        out.write(le(4) { putInt(3) })
        out.write(le(8) { putLong(0) })
        out.write(le(8) { putLong(kvs.size.toLong()) })
        kvs.forEach { out.write(it) }
        return out.toByteArray()
    }
}

class FakeHttp : Http {
    val requests = mutableListOf<String>()
    var handler: (method: String, url: String, form: Map<String, String>) -> HttpResult =
        { _, url, _ -> HttpResult(404, "", null, url) }

    override fun get(url: String, headers: Map<String, String>, maxBytes: Int): HttpResult {
        requests += "GET $url"
        return handler("GET", url, emptyMap())
    }

    override fun postForm(url: String, form: Map<String, String>, headers: Map<String, String>): HttpResult {
        requests += "POST $url"
        return handler("POST", url, form)
    }
}

class FakeMemory : MemoryAccess {
    val items = mutableListOf<MemoryItem>()
    override fun list() = items.toList()
    override fun add(text: String) = MemoryItem(text = text).also { items += it }
    override fun remove(id: String) = items.removeIf { it.id == id }
}
