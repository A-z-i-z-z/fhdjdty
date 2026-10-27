package app.yerel.core

import org.json.JSONArray
import java.net.URLDecoder
import java.net.URLEncoder

/** What a pasted Hugging Face link or "owner/repo[:quant]" points at. */
sealed class HfRef {
    abstract val repo: String

    data class Repo(override val repo: String, val quantHint: String? = null, val revision: String = "main") : HfRef()
    data class File(override val repo: String, val revision: String, val path: String) : HfRef()
}

data class HfFile(val path: String, val size: Long) {
    val fileName get() = path.substringAfterLast('/')
    val quant get() = quantLabel(fileName)
    val isMmproj get() = fileName.contains("mmproj", ignoreCase = true)
    val isSplitPart get() = SPLIT_RE.containsMatchIn(fileName)
}

private val NAME_RE = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,95}$")
private val SPLIT_RE = Regex("-\\d{5}-of-\\d{5}\\.gguf$", RegexOption.IGNORE_CASE)
private val QUANT_RE = Regex(
    "(?i)(?<![A-Za-z0-9])(UD-)?(IQ[1-4]_(?:XXS|XS|S|M|NL)|Q[2-8]_K(?:_(?:S|M|L|XL))?|Q[4-8]_[01]|TQ[12]_0|MXFP4|BF16|F16|F32)(?![A-Za-z0-9])"
)

fun quantLabel(fileName: String): String =
    QUANT_RE.findAll(fileName.removeSuffix(".gguf")).lastOrNull()?.value?.uppercase() ?: ""

object HuggingFace {
    const val HOST = "https://huggingface.co"

    /** Parses links like huggingface.co/owner/repo/(blob|resolve|tree)/rev/path, hf.co/..., or owner/repo:Q4_0. */
    fun parse(input: String): HfRef? {
        var s = input.trim().trim('"', '\'', '<', '>')
        if (s.isEmpty()) return null
        s = s.substringBefore('#')
        val query = s.substringAfter('?', "")
        s = s.substringBefore('?')
        s = s.removePrefix("hf://")
        val lower = s.lowercase()
        val prefixes = listOf("https://", "http://")
        var path = prefixes.fold(s) { acc, p -> if (acc.lowercase().startsWith(p)) acc.substring(p.length) else acc }
        val host = path.substringBefore('/').lowercase()
        val isUrl = lower.startsWith("http") || host.endsWith("huggingface.co") || host == "hf.co"
        if (isUrl) {
            if (!(host == "huggingface.co" || host == "www.huggingface.co" || host == "hf.co")) return null
            path = path.substringAfter('/', "")
        }
        val parts = path.trim('/').split('/').filter { it.isNotEmpty() }.map { decode(it) }
        if (parts.size < 2) return null
        if (parts[0] in setOf("datasets", "spaces", "api", "docs", "collections", "models")) return null

        var repoName = parts[1]
        var quantHint: String? = null
        if (parts.size == 2 && repoName.contains(':')) {
            quantHint = repoName.substringAfter(':').ifEmpty { null }
            repoName = repoName.substringBefore(':')
        }
        if (!NAME_RE.matches(parts[0]) || !NAME_RE.matches(repoName)) return null
        val repo = "${parts[0]}/$repoName"
        if (parts.size >= 4 && parts[2] in setOf("blob", "resolve")) {
            val file = parts.drop(4).joinToString("/")
            if (file.isEmpty()) return HfRef.Repo(repo, revision = parts[3])
            return HfRef.File(repo, parts[3], file)
        }
        if (parts.size >= 4 && parts[2] == "tree") {
            return HfRef.Repo(repo, revision = parts[3])
        }
        if (quantHint == null && query.isNotEmpty()) {
            quantHint = query.split('&').firstOrNull { it.startsWith("quant=") }?.substringAfter('=')
        }
        return HfRef.Repo(repo, quantHint)
    }

    fun treeUrl(repo: String, revision: String = "main") =
        "$HOST/api/models/$repo/tree/${encode(revision)}?recursive=true"

    fun fileUrl(repo: String, revision: String, path: String) =
        "$HOST/$repo/resolve/${encode(revision)}/${path.split('/').joinToString("/") { encode(it) }}?download=true"

    /** GGUF files from the /api/models/{repo}/tree response, model weights before projectors. */
    fun parseTree(json: String): List<HfFile> {
        val arr = JSONArray(json)
        val files = arr.mapObjects { o ->
            if (o.optString("type") != "file") return@mapObjects null
            val path = o.optString("path")
            if (!path.endsWith(".gguf", ignoreCase = true)) return@mapObjects null
            val size = o.optJSONObject("lfs")?.optLong("size")?.takeIf { it > 0 } ?: o.optLong("size")
            HfFile(path, size)
        }.filterNotNull()
        return files.sortedWith(compareBy<HfFile> { it.isMmproj }.thenBy { it.size })
    }

    private val PREFERRED = listOf("Q4_0", "Q4_K_M", "Q4_K_S", "IQ4_NL", "IQ4_XS", "Q5_K_M", "Q6_K", "Q8_0")

    /** The file to preselect: the requested quant, else a phone-friendly 4-bit one. */
    fun pick(files: List<HfFile>, quantHint: String?): HfFile? {
        val weights = files.filter { !it.isMmproj && !it.isSplitPart }
        if (weights.isEmpty()) return null
        if (!quantHint.isNullOrBlank()) {
            weights.firstOrNull { it.quant.equals(quantHint, ignoreCase = true) }?.let { return it }
            weights.firstOrNull { it.fileName.contains(quantHint, ignoreCase = true) }?.let { return it }
        }
        for (q in PREFERRED) {
            weights.firstOrNull { it.quant == q }?.let { return it }
        }
        return weights.first()
    }

    private fun encode(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    private fun decode(s: String) = try { URLDecoder.decode(s, "UTF-8") } catch (e: Exception) { s }
}

/** Curated models that run well on a phone; the file is resolved from the repo at download time. */
data class CatalogModel(
    val title: String,
    val repo: String,
    val quant: String,
    val approxBytes: Long,
    val description: String,
    val preset: SamplingPreset? = null,
    val tags: List<String> = emptyList(),
)

object Catalog {
    private const val MB = 1L shl 20

    val models = listOf(
        CatalogModel(
            "LFM3 1.7B", "LiquidAI/LFM3-1.7B-GGUF", "Q4_0", 1050 * MB,
            "Liquid AI'ın yeni tri-hybrid modeli. Ajan, araç çağırma, 256K bağlam. Türkçe resmi dil değil.",
            SamplingPreset(temperature = 0.1f, topK = 50, topP = 1.0f, minP = 0.0f, repeatPenalty = 1.1f),
            listOf("Yeni", "Araçlar", "Düşünme"),
        ),
        CatalogModel(
            "LFM2.5 1.2B Instruct", "LiquidAI/LFM2.5-1.2B-Instruct-GGUF", "Q4_0", 720 * MB,
            "Hızlı ve hafif hibrit model, telefonlar için tasarlandı.",
            SamplingPreset(temperature = 0.1f, topK = 50, topP = 1.0f, minP = 0.0f, repeatPenalty = 1.05f),
            listOf("Hızlı", "Araçlar"),
        ),
        CatalogModel(
            "Qwen3 0.6B", "Qwen/Qwen3-0.6B-GGUF", "Q8_0", 640 * MB,
            "Çok küçük ama düşünebilen, araç kullanabilen çok dilli model. Test için ideal.",
            SamplingPreset(temperature = 0.6f, topK = 20, topP = 0.95f, minP = 0.0f, repeatPenalty = 1.0f),
            listOf("Küçük", "Araçlar", "Düşünme"),
        ),
        CatalogModel(
            "Qwen3 1.7B", "Qwen/Qwen3-1.7B-GGUF", "Q8_0", 1830 * MB,
            "Dengeli boyut ve kalite, Türkçe dahil çok dilli.",
            SamplingPreset(temperature = 0.6f, topK = 20, topP = 0.95f, minP = 0.0f, repeatPenalty = 1.0f),
            listOf("Araçlar", "Düşünme"),
        ),
        CatalogModel(
            "Qwen3 4B", "Qwen/Qwen3-4B-GGUF", "Q4_K_M", 2500 * MB,
            "Bu listedeki en akıllı model; daha yavaş, ~3 GB RAM ister.",
            SamplingPreset(temperature = 0.6f, topK = 20, topP = 0.95f, minP = 0.0f, repeatPenalty = 1.0f),
            listOf("Güçlü", "Araçlar", "Düşünme"),
        ),
        CatalogModel(
            "Gemma 3 1B", "ggml-org/gemma-3-1b-it-GGUF", "Q4_K_M", 806 * MB,
            "Google'ın küçük sohbet modeli.",
            SamplingPreset(temperature = 1.0f, topK = 64, topP = 0.95f, minP = 0.0f, repeatPenalty = 1.0f),
            listOf("Sohbet"),
        ),
        CatalogModel(
            "Llama 3.2 1B Instruct", "bartowski/Llama-3.2-1B-Instruct-GGUF", "Q4_0", 770 * MB,
            "Meta'nın küçük asistan modeli.",
            tags = listOf("Sohbet", "Araçlar"),
        ),
    )
}
