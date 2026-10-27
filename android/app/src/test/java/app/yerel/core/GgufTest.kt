package app.yerel.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class GgufTest {
    @Test
    fun readsDenseHeader() {
        val bytes = GgufBuilder()
            .string("general.architecture", "qwen3")
            .string("general.name", "Qwen3 0.6B")
            .string("general.size_label", "0.6B")
            .u32("general.file_type", 7)
            .u32("qwen3.context_length", 40960)
            .u32("qwen3.block_count", 28)
            .u32("qwen3.embedding_length", 1024)
            .u32("qwen3.attention.head_count", 16)
            .u32("qwen3.attention.head_count_kv", 8)
            .u32("qwen3.attention.key_length", 128)
            .u32("qwen3.attention.value_length", 128)
            .stringArray("tokenizer.ggml.tokens", List(5000) { "tok$it" })
            .string("tokenizer.chat_template", "{% if tools %}...{% endif %}{% if enable_thinking %}<think>{% endif %}")
            .build()
        val info = GgufReader.read(ByteArrayInputStream(bytes))
        assertEquals("qwen3", info.arch)
        assertEquals("Qwen3 0.6B", info.name)
        assertEquals("Q8_0", info.quantName)
        assertEquals(40960, info.ctxTrain)
        assertEquals(28, info.nLayer)
        assertEquals(28L * 8 * 256, info.kvElemsFull)
        assertEquals(0L, info.kvElemsSwa)
        assertTrue(info.hasChatTemplate)
        assertTrue(info.templateTools)
        assertTrue(info.templateThinking)
        assertFalse(info.isMmproj)
        // 28 layers * 8 heads * 256 (k+v) * 4096 tokens * 2 bytes = 448 MiB at f16
        assertEquals(448L shl 20, estimateKvBytes(info, 4096, "f16", "f16"))
        assertEquals((448L shl 20) * 17 / 32, estimateKvBytes(info, 4096, "q8_0", "q8_0"))
    }

    @Test
    fun hybridLayersWithoutKvAreSkipped() {
        // LFM-style: per-layer kv heads, 0 for convolution layers
        val kv = listOf(0, 0, 8, 0, 0, 8, 0, 8)
        val bytes = GgufBuilder()
            .string("general.architecture", "lfm2")
            .u32("lfm2.block_count", kv.size)
            .u32("lfm2.embedding_length", 2048)
            .u32("lfm2.attention.head_count", 32)
            .u32Array("lfm2.attention.head_count_kv", kv)
            .build()
        val info = GgufReader.read(ByteArrayInputStream(bytes))
        assertEquals(3L * 8 * (64 + 64), info.kvElemsFull)
    }

    @Test
    fun slidingWindowLayersUseWindow() {
        val bytes = GgufBuilder()
            .string("general.architecture", "gemma3")
            .u32("gemma3.block_count", 4)
            .u32("gemma3.embedding_length", 1024)
            .u32("gemma3.attention.head_count", 4)
            .u32("gemma3.attention.head_count_kv", 1)
            .u32("gemma3.attention.key_length", 256)
            .u32("gemma3.attention.value_length", 256)
            .u32("gemma3.attention.sliding_window", 512)
            .boolArray("gemma3.attention.sliding_window_pattern", listOf(true, true, true, false))
            .build()
        val info = GgufReader.read(ByteArrayInputStream(bytes))
        assertEquals(512L, info.kvElemsFull)
        assertEquals(3L * 512, info.kvElemsSwa)
        val est = estimateKvBytes(info, 8192, "f16", "f16", nBatch = 256)
        assertEquals((512L * 8192 + 1536L * (512 + 256)) * 2, est)
    }

    @Test(expected = GgufException::class)
    fun rejectsNonGguf() {
        GgufReader.read(ByteArrayInputStream("not a model at all".toByteArray()))
    }

    @Test
    fun readsRealFileWhenAvailable() {
        val path = System.getenv("YEREL_TEST_GGUF") ?: return
        val info = File(path).inputStream().use { GgufReader.read(it) }
        assertTrue(info.arch.isNotEmpty())
        assertTrue(info.nLayer > 0)
        println("GGUF $path -> $info")
    }

    @Test
    fun formatsBytes() {
        assertEquals("1.50 GB", formatBytes(3L shl 29))
        assertEquals("640 MB", formatBytes(640L shl 20))
    }
}
