package app.yerel.core

import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.InputStream

/** The parts of a GGUF header the app shows and uses for memory estimates. */
data class GgufInfo(
    val arch: String = "",
    val name: String = "",
    val sizeLabel: String = "",
    val fileType: Int = -1,
    val ctxTrain: Int = 0,
    val nLayer: Int = 0,
    val nEmbd: Int = 0,
    // K+V elements stored per token, summed over full-attention and sliding-window layers
    val kvElemsFull: Long = 0,
    val kvElemsSwa: Long = 0,
    val swaWindow: Int = 0,
    val hasChatTemplate: Boolean = false,
    val templateTools: Boolean = false,
    val templateThinking: Boolean = false,
    val isMmproj: Boolean = false,
) {
    val quantName get() = fileTypeName(fileType)

    fun toJson() = JSONObject().apply {
        put("arch", arch)
        put("name", name)
        put("size_label", sizeLabel)
        put("file_type", fileType)
        put("ctx_train", ctxTrain)
        put("n_layer", nLayer)
        put("n_embd", nEmbd)
        put("kv_full", kvElemsFull)
        put("kv_swa", kvElemsSwa)
        put("swa_window", swaWindow)
        put("template", hasChatTemplate)
        put("tools", templateTools)
        put("thinking", templateThinking)
        put("mmproj", isMmproj)
    }

    companion object {
        fun fromJson(o: JSONObject) = GgufInfo(
            arch = o.optString("arch"),
            name = o.optString("name"),
            sizeLabel = o.optString("size_label"),
            fileType = o.optInt("file_type", -1),
            ctxTrain = o.optInt("ctx_train"),
            nLayer = o.optInt("n_layer"),
            nEmbd = o.optInt("n_embd"),
            kvElemsFull = o.optLong("kv_full"),
            kvElemsSwa = o.optLong("kv_swa"),
            swaWindow = o.optInt("swa_window"),
            hasChatTemplate = o.optBoolean("template"),
            templateTools = o.optBoolean("tools"),
            templateThinking = o.optBoolean("thinking"),
            isMmproj = o.optBoolean("mmproj"),
        )
    }
}

class GgufException(message: String) : Exception(message)

fun fileTypeName(t: Int): String = when (t) {
    0 -> "F32"; 1 -> "F16"; 2 -> "Q4_0"; 3 -> "Q4_1"; 7 -> "Q8_0"; 8 -> "Q5_0"; 9 -> "Q5_1"
    10 -> "Q2_K"; 11 -> "Q3_K_S"; 12 -> "Q3_K_M"; 13 -> "Q3_K_L"; 14 -> "Q4_K_S"; 15 -> "Q4_K_M"
    16 -> "Q5_K_S"; 17 -> "Q5_K_M"; 18 -> "Q6_K"; 19 -> "IQ2_XXS"; 20 -> "IQ2_XS"; 21 -> "Q2_K_S"
    22 -> "IQ3_XS"; 23 -> "IQ3_XXS"; 24 -> "IQ1_S"; 25 -> "IQ4_NL"; 26 -> "IQ3_S"; 27 -> "IQ3_M"
    28 -> "IQ2_S"; 29 -> "IQ2_M"; 30 -> "IQ4_XS"; 31 -> "IQ1_M"; 32 -> "BF16"; 36 -> "TQ1_0"
    37 -> "TQ2_0"; 38 -> "MXFP4"
    else -> ""
}

/** Reads the metadata section of a GGUF file without touching the tensor data. */
object GgufReader {
    private const val MAX_STRING = 16 * 1024 * 1024

    fun read(input: InputStream): GgufInfo {
        val r = LeReader(BufferedInputStream(input, 1 shl 16))
        if (r.u32() != 0x46554747L) { // "GGUF"
            throw GgufException("not a GGUF file")
        }
        val version = r.u32()
        if (version < 2 || version > 3) {
            throw GgufException("unsupported GGUF version $version")
        }
        r.u64() // tensor count
        val nKv = r.u64()
        if (nKv < 0 || nKv > 100_000) {
            throw GgufException("invalid metadata count")
        }

        val values = HashMap<String, Any>()
        repeat(nKv.toInt()) {
            val key = r.string()
            val type = r.u32().toInt()
            values[key] = r.value(type, keep = isUseful(key)) ?: Unit
        }
        return toInfo(values)
    }

    // large arrays (tokens, merges, scores) are skipped, not stored
    private fun isUseful(key: String) = !key.startsWith("tokenizer.ggml.")

    private fun toInfo(v: Map<String, Any>): GgufInfo {
        val arch = v["general.architecture"] as? String ?: ""
        fun num(key: String): Long? = (v["$arch.$key"] as? Number)?.toLong()
        fun perLayer(key: String, n: Int): List<Long>? = when (val x = v["$arch.$key"]) {
            is Number -> List(n) { x.toLong() }
            is List<*> -> x.map { (it as? Number)?.toLong() ?: 0L }
            else -> null
        }

        val nLayer = (num("block_count") ?: 0L).toInt()
        val nEmbd = (num("embedding_length") ?: 0L).toInt()
        val headsQ = perLayer("attention.head_count", nLayer) ?: List(nLayer) { 0L }
        val headsKv = perLayer("attention.head_count_kv", nLayer) ?: headsQ
        val swaPattern = (v["$arch.attention.sliding_window_pattern"] as? List<*>)?.map { it == true }
        val swaWindow = (num("attention.sliding_window") ?: 0L).toInt()

        var full = 0L
        var swa = 0L
        for (il in 0 until nLayer) {
            val hkv = headsKv.getOrElse(il) { 0L }
            if (hkv <= 0) continue // recurrent / convolution layer
            val hq = headsQ.getOrElse(il) { hkv }.coerceAtLeast(1)
            val headDim = nEmbd / hq
            val kLen = num("attention.key_length") ?: headDim.toLong()
            val vLen = num("attention.value_length") ?: headDim.toLong()
            val elems = hkv * (kLen + vLen)
            if (swaWindow > 0 && swaPattern?.getOrNull(il) == true) swa += elems else full += elems
        }

        val template = v["tokenizer.chat_template"] as? String
        return GgufInfo(
            arch = arch,
            name = v["general.name"] as? String ?: "",
            sizeLabel = v["general.size_label"] as? String ?: "",
            fileType = (v["general.file_type"] as? Number)?.toInt() ?: -1,
            ctxTrain = (num("context_length") ?: 0L).toInt(),
            nLayer = nLayer,
            nEmbd = nEmbd,
            kvElemsFull = full,
            kvElemsSwa = swa,
            swaWindow = swaWindow,
            hasChatTemplate = template != null,
            templateTools = template?.contains("tools") == true,
            templateThinking = template?.let { "enable_thinking" in it || "<think>" in it } == true,
            isMmproj = arch == "clip" || v.keys.any { it.startsWith("clip.") },
        )
    }

    private class LeReader(private val input: InputStream) {
        private val buf = ByteArray(8)

        private fun fill(n: Int) {
            var off = 0
            while (off < n) {
                val r = input.read(buf, off, n - off)
                if (r < 0) throw EOFException("truncated GGUF header")
                off += r
            }
        }

        fun u8(): Int { fill(1); return buf[0].toInt() and 0xFF }
        fun u16(): Int { fill(2); return (buf[0].toInt() and 0xFF) or ((buf[1].toInt() and 0xFF) shl 8) }
        fun u32(): Long {
            fill(4)
            return (buf[0].toLong() and 0xFF) or ((buf[1].toLong() and 0xFF) shl 8) or
                ((buf[2].toLong() and 0xFF) shl 16) or ((buf[3].toLong() and 0xFF) shl 24)
        }
        fun u64(): Long {
            fill(8)
            var x = 0L
            for (i in 7 downTo 0) x = (x shl 8) or (buf[i].toLong() and 0xFF)
            return x
        }

        fun skip(n: Long) {
            var left = n
            while (left > 0) {
                val s = input.skip(left)
                if (s <= 0) {
                    if (input.read() < 0) throw EOFException("truncated GGUF header")
                    left -= 1
                } else {
                    left -= s
                }
            }
        }

        fun string(): String {
            val n = u64()
            if (n < 0 || n > MAX_STRING) throw GgufException("invalid string length")
            val bytes = ByteArray(n.toInt())
            var off = 0
            while (off < bytes.size) {
                val r = input.read(bytes, off, bytes.size - off)
                if (r < 0) throw EOFException("truncated GGUF header")
                off += r
            }
            return String(bytes, Charsets.UTF_8)
        }

        private fun skipString() {
            val n = u64()
            if (n < 0 || n > MAX_STRING) throw GgufException("invalid string length")
            skip(n)
        }

        fun value(type: Int, keep: Boolean): Any? = when (type) {
            0 -> u8()
            1 -> u8().toByte().toInt()
            2 -> u16()
            3 -> u16().toShort().toInt()
            4 -> u32()
            5 -> u32().toInt()
            6 -> Float.fromBits(u32().toInt())
            7 -> u8() != 0
            8 -> if (keep) string() else { skipString(); null }
            9 -> {
                val itemType = u32().toInt()
                val n = u64()
                if (n < 0) throw GgufException("invalid array length")
                if (!keep || n > 4096) {
                    skipArray(itemType, n)
                    null
                } else {
                    List(n.toInt()) { value(itemType, true) }
                }
            }
            10 -> u64()
            11 -> u64()
            12 -> Double.fromBits(u64())
            else -> throw GgufException("unknown metadata type $type")
        }

        private fun skipArray(itemType: Int, n: Long) {
            val fixed = when (itemType) {
                0, 1, 7 -> 1L; 2, 3 -> 2L; 4, 5, 6 -> 4L; 10, 11, 12 -> 8L
                else -> -1L
            }
            if (fixed > 0) {
                skip(fixed * n)
            } else {
                for (i in 0 until n) value(itemType, false)
            }
        }
    }
}

/** Bytes per cache element for the KV cache types offered in settings. */
fun kvTypeBytes(type: String): Double = when (type) {
    "f32" -> 4.0
    "f16", "bf16" -> 2.0
    "q8_0" -> 34.0 / 32
    "q5_1" -> 24.0 / 32
    "q5_0" -> 22.0 / 32
    "q4_1" -> 20.0 / 32
    "q4_0", "iq4_nl" -> 18.0 / 32
    else -> 2.0
}

/** Approximate KV cache size; sliding-window layers only keep their window plus one batch. */
fun estimateKvBytes(info: GgufInfo, nCtx: Int, typeK: String, typeV: String, nBatch: Int = 512): Long {
    val perElem = (kvTypeBytes(typeK) + kvTypeBytes(typeV)) / 2
    val swaTokens = if (info.swaWindow > 0) minOf(nCtx, info.swaWindow + nBatch) else nCtx
    return ((info.kvElemsFull * nCtx + info.kvElemsSwa * swaTokens) * perElem).toLong()
}

fun formatBytes(bytes: Long): String {
    val b = bytes.toDouble()
    return when {
        b >= 1L shl 30 -> String.format(java.util.Locale.ROOT, "%.2f GB", b / (1L shl 30))
        b >= 1L shl 20 -> String.format(java.util.Locale.ROOT, "%.0f MB", b / (1L shl 20))
        b >= 1L shl 10 -> String.format(java.util.Locale.ROOT, "%.0f KB", b / (1L shl 10))
        else -> "$bytes B"
    }
}
