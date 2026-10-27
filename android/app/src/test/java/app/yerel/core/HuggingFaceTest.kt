package app.yerel.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HuggingFaceTest {
    @Test
    fun parsesLinks() {
        assertEquals(HfRef.Repo("Qwen/Qwen3-0.6B-GGUF"), HuggingFace.parse("https://huggingface.co/Qwen/Qwen3-0.6B-GGUF"))
        assertEquals(HfRef.Repo("Qwen/Qwen3-0.6B-GGUF"), HuggingFace.parse("huggingface.co/Qwen/Qwen3-0.6B-GGUF/"))
        assertEquals(HfRef.Repo("unsloth/LFM3-1.7B-GGUF", "Q4_K_M"), HuggingFace.parse("unsloth/LFM3-1.7B-GGUF:Q4_K_M"))
        assertEquals(
            HfRef.File("LiquidAI/LFM3-1.7B-GGUF", "main", "LFM3-1.7B-Q4_0.gguf"),
            HuggingFace.parse("https://huggingface.co/LiquidAI/LFM3-1.7B-GGUF/blob/main/LFM3-1.7B-Q4_0.gguf?download=true"),
        )
        assertEquals(
            HfRef.File("a/b", "main", "sub dir/x.gguf"),
            HuggingFace.parse("https://hf.co/a/b/resolve/main/sub%20dir/x.gguf"),
        )
        assertEquals(HfRef.Repo("a/b", revision = "dev"), HuggingFace.parse("https://huggingface.co/a/b/tree/dev"))
        assertEquals(HfRef.Repo("a/b"), HuggingFace.parse("  \"https://huggingface.co/a/b#files\" "))
        assertNull(HuggingFace.parse("https://example.com/a/b"))
        assertNull(HuggingFace.parse("https://huggingface.co/datasets/a/b"))
        assertNull(HuggingFace.parse("https://huggingface.co/collections/LiquidAI/lfm3"))
        assertNull(HuggingFace.parse("hello"))
        assertNull(HuggingFace.parse(""))
    }

    @Test
    fun buildsUrls() {
        assertEquals("https://huggingface.co/api/models/a/b/tree/main?recursive=true", HuggingFace.treeUrl("a/b"))
        assertEquals(
            "https://huggingface.co/a/b/resolve/main/dir/x%20y.gguf?download=true",
            HuggingFace.fileUrl("a/b", "main", "dir/x y.gguf"),
        )
    }

    private val tree = """
        [
          {"type":"file","path":"README.md","size":100},
          {"type":"directory","path":"old"},
          {"type":"file","path":"Model-Q8_0.gguf","size":10,"lfs":{"size":800}},
          {"type":"file","path":"Model-Q4_K_M.gguf","size":10,"lfs":{"size":500}},
          {"type":"file","path":"Model-Q4_0.gguf","size":10,"lfs":{"size":450}},
          {"type":"file","path":"mmproj-Model-F16.gguf","size":10,"lfs":{"size":300}},
          {"type":"file","path":"big/Model-F16-00001-of-00002.gguf","size":10,"lfs":{"size":5000}},
          {"type":"file","path":"Model-UD-IQ4_XS.gguf","size":10,"lfs":{"size":400}}
        ]
    """.trimIndent()

    @Test
    fun parsesTreeAndPicks() {
        val files = HuggingFace.parseTree(tree)
        assertEquals(6, files.size)
        assertEquals(listOf(400L, 450L, 500L, 800L, 5000L, 300L), files.map { it.size })
        assertEquals("Model-Q4_0.gguf", HuggingFace.pick(files, null)?.path)
        assertEquals("Model-Q8_0.gguf", HuggingFace.pick(files, "q8_0")?.path)
        assertEquals("Model-UD-IQ4_XS.gguf", HuggingFace.pick(files, "IQ4_XS")?.path)
        assertEquals("Model-Q4_0.gguf", HuggingFace.pick(files, "Q3_K_S")?.path)
        assertNull(HuggingFace.pick(files.filter { it.isMmproj }, null))
    }

    @Test
    fun labelsQuants() {
        assertEquals("Q4_K_M", quantLabel("Qwen3-4B-Q4_K_M.gguf"))
        assertEquals("UD-Q4_K_XL", quantLabel("LFM3-1.7B-UD-Q4_K_XL.gguf"))
        assertEquals("BF16", quantLabel("model-bf16.gguf"))
        assertEquals("", quantLabel("model.gguf"))
    }

    @Test
    fun catalogReposParse() {
        for (m in Catalog.models) {
            assertEquals(m.repo, HuggingFace.parse(m.repo)?.repo)
        }
    }
}
