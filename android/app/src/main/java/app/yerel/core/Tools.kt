package app.yerel.core

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class HttpResult(val code: Int, val body: String, val contentType: String?, val finalUrl: String)

interface Http {
    fun get(url: String, headers: Map<String, String> = emptyMap(), maxBytes: Int = 2_000_000): HttpResult
    fun postForm(url: String, form: Map<String, String>, headers: Map<String, String> = emptyMap()): HttpResult
}

interface MemoryAccess {
    fun list(): List<MemoryItem>
    fun add(text: String): MemoryItem
    fun remove(id: String): Boolean
}

data class ToolSpec(
    val name: String,
    val title: String,
    val description: String,
    val parameters: JSONObject,
    val needsNetwork: Boolean = false,
)

data class ToolOutcome(val ok: Boolean, val content: String, val summary: String)

private fun schema(vararg props: Triple<String, String, String>, required: List<String> = emptyList()): JSONObject {
    val p = JSONObject()
    for ((name, type, desc) in props) {
        p.put(name, JSONObject().put("type", type).put("description", desc))
    }
    return JSONObject().put("type", "object").put("properties", p).put("required", JSONArray(required))
}

object ToolCatalog {
    val all = listOf(
        ToolSpec(
            "web_search", "Web araması",
            "Search the web for current information, news, prices or facts you are not sure about. " +
                "Returns titles, URLs and snippets.",
            schema(Triple("query", "string", "Search query"), required = listOf("query")),
            needsNetwork = true,
        ),
        ToolSpec(
            "fetch_url", "Sayfa oku",
            "Download a web page and return its readable text. Use after web_search to read a result.",
            schema(Triple("url", "string", "Full http(s) URL"), required = listOf("url")),
            needsNetwork = true,
        ),
        ToolSpec(
            "wikipedia", "Wikipedia",
            "Look up a topic on Wikipedia and return the summary of the best matching article.",
            schema(
                Triple("query", "string", "Topic to look up"),
                Triple("lang", "string", "Wikipedia language code, e.g. tr or en"),
                required = listOf("query"),
            ),
            needsNetwork = true,
        ),
        ToolSpec(
            "calculator", "Hesap makinesi",
            "Evaluate a math expression exactly, e.g. (17*23)/4 or sqrt(2)^3. Use it instead of mental math.",
            schema(Triple("expression", "string", "Expression with + - * / % ^ ! and sqrt, sin, log, ln, pi, e"), required = listOf("expression")),
        ),
        ToolSpec(
            "get_datetime", "Tarih ve saat",
            "Get the current local date, time, weekday and time zone.",
            schema(),
        ),
        ToolSpec(
            "remember", "Hafızaya kaydet",
            "Save a lasting fact about the user or their preferences to long-term memory, e.g. their name.",
            schema(Triple("fact", "string", "The fact to remember, one short sentence"), required = listOf("fact")),
        ),
        ToolSpec(
            "forget", "Hafızadan sil",
            "Delete a saved memory by its id, when the user asks to forget it or it is wrong.",
            schema(Triple("memory_id", "string", "Id of the memory shown in the system prompt"), required = listOf("memory_id")),
        ),
    )

    fun byName(name: String) = all.firstOrNull { it.name == name }

    /** OpenAI-style tools array for the enabled tools. */
    fun toolsJson(enabled: Collection<String>): String {
        val arr = JSONArray()
        for (t in all) {
            if (t.name !in enabled) continue
            arr.put(
                JSONObject().put("type", "function").put(
                    "function",
                    JSONObject().put("name", t.name).put("description", t.description).put("parameters", t.parameters),
                ),
            )
        }
        return arr.toString()
    }
}

data class ToolConfig(
    val searchProvider: String = "duckduckgo",
    val searxngUrl: String = "",
    val fetchMaxChars: Int = 6000,
    val defaultWikiLang: String = "tr",
)

class ToolRunner(
    private val http: Http,
    private val memory: MemoryAccess,
    private val config: () -> ToolConfig,
    private val now: () -> Date = { Date() },
    private val timeZone: () -> TimeZone = { TimeZone.getDefault() },
) {
    private val ua = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36",
        "Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.8",
    )

    fun run(name: String, argumentsJson: String): ToolOutcome {
        val args = try {
            if (argumentsJson.isBlank()) JSONObject() else JSONObject(argumentsJson)
        } catch (e: Exception) {
            return fail("arguments are not valid JSON: ${e.message}")
        }
        return try {
            when (name) {
                "web_search" -> webSearch(args.optString("query"))
                "fetch_url" -> fetchUrl(args.optString("url"))
                "wikipedia" -> wikipedia(args.optString("query"), args.optString("lang").ifBlank { config().defaultWikiLang })
                "calculator" -> calculator(args.optString("expression"))
                "get_datetime" -> datetime()
                "remember" -> remember(args.optString("fact"))
                "forget" -> forget(args.optString("memory_id"))
                else -> fail("unknown tool '$name'")
            }
        } catch (e: Exception) {
            fail("${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun fail(msg: String) = ToolOutcome(false, JSONObject().put("error", msg).toString(), msg)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun webSearch(query: String): ToolOutcome {
        if (query.isBlank()) return fail("query is empty")
        val cfg = config()
        val results: List<SearchResult> = if (cfg.searchProvider == "searxng" && cfg.searxngUrl.isNotBlank()) {
            val base = cfg.searxngUrl.trimEnd('/')
            val r = http.get("$base/search?q=${enc(query)}&format=json", ua)
            if (r.code !in 200..299) return fail("SearXNG returned HTTP ${r.code}")
            JSONObject(r.body).optJSONArray("results")?.mapObjects {
                SearchResult(it.optString("title"), it.optString("url"), it.optString("content"))
            }?.take(8) ?: emptyList()
        } else {
            var r = http.postForm("https://html.duckduckgo.com/html/", mapOf("q" to query, "kl" to "tr-tr"), ua)
            var parsed = if (r.code in 200..299) HtmlText.parseDuckDuckGo(r.body) else emptyList()
            if (parsed.isEmpty()) {
                r = http.get("https://lite.duckduckgo.com/lite/?q=${enc(query)}", ua)
                parsed = if (r.code in 200..299) HtmlText.parseDuckDuckGo(r.body) else emptyList()
            }
            parsed
        }
        if (results.isEmpty()) return fail("no results (the search service may be blocking requests)")
        val arr = JSONArray()
        results.forEach { arr.put(JSONObject().put("title", it.title).put("url", it.url).put("snippet", it.snippet)) }
        return ToolOutcome(true, JSONObject().put("query", query).put("results", arr).toString(), "${results.size} sonuç")
    }

    private fun fetchUrl(url: String): ToolOutcome {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return fail("only http(s) URLs are supported")
        val r = http.get(url, ua)
        if (r.code !in 200..299) return fail("HTTP ${r.code}")
        val type = r.contentType?.lowercase() ?: ""
        val max = config().fetchMaxChars
        val (title, text) = when {
            "html" in type || r.body.trimStart().startsWith("<") -> HtmlText.title(r.body) to HtmlText.toText(r.body, max)
            "json" in type || "text" in type || type.isEmpty() -> "" to r.body.take(max)
            else -> return fail("unsupported content type $type")
        }
        val out = JSONObject().put("url", r.finalUrl).put("title", title).put("text", text)
        return ToolOutcome(true, out.toString(), title.ifEmpty { r.finalUrl }.take(80))
    }

    private fun wikipedia(query: String, lang: String): ToolOutcome {
        if (query.isBlank()) return fail("query is empty")
        val l = lang.lowercase().filter { it.isLetter() }.take(10).ifEmpty { "tr" }
        val search = http.get(
            "https://$l.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=3&srsearch=${enc(query)}", ua,
        )
        if (search.code !in 200..299) return fail("Wikipedia returned HTTP ${search.code}")
        val hits = JSONObject(search.body).optJSONObject("query")?.optJSONArray("search")
            ?.mapObjects { it.optString("title") } ?: emptyList()
        if (hits.isEmpty()) return fail("no Wikipedia article found for '$query'")
        val title = hits.first()
        val summary = http.get("https://$l.wikipedia.org/api/rest_v1/page/summary/${enc(title.replace(' ', '_'))}", ua)
        if (summary.code !in 200..299) return fail("Wikipedia summary returned HTTP ${summary.code}")
        val s = JSONObject(summary.body)
        val out = JSONObject()
            .put("title", s.optString("title", title))
            .put("summary", s.optString("extract"))
            .put("url", s.optJSONObject("content_urls")?.optJSONObject("mobile")?.optString("page") ?: "")
            .put("other_matches", JSONArray(hits.drop(1)))
        return ToolOutcome(true, out.toString(), s.optString("title", title))
    }

    private fun calculator(expression: String): ToolOutcome {
        if (expression.isBlank()) return fail("expression is empty")
        return try {
            val v = Calculator.format(Calculator.evaluate(expression))
            ToolOutcome(true, JSONObject().put("expression", expression).put("result", v).toString(), "$expression = $v")
        } catch (e: CalcException) {
            fail(e.message ?: "invalid expression")
        }
    }

    private fun datetime(): ToolOutcome {
        val tz = timeZone()
        val d = now()
        fun f(p: String, loc: Locale = Locale.ROOT) = SimpleDateFormat(p, loc).apply { timeZone = tz }.format(d)
        val out = JSONObject()
            .put("iso", f("yyyy-MM-dd'T'HH:mm:ssXXX"))
            .put("date", f("d MMMM yyyy", Locale("tr")))
            .put("weekday", f("EEEE", Locale("tr")))
            .put("time", f("HH:mm"))
            .put("time_zone", tz.id)
        return ToolOutcome(true, out.toString(), "${f("d MMMM yyyy HH:mm", Locale("tr"))}")
    }

    private fun remember(fact: String): ToolOutcome {
        val text = fact.trim()
        if (text.isEmpty()) return fail("fact is empty")
        if (text.length > 500) return fail("fact is too long, keep it under 500 characters")
        val existing = memory.list().firstOrNull { it.text.equals(text, ignoreCase = true) }
        val item = existing ?: memory.add(text)
        return ToolOutcome(true, JSONObject().put("saved", true).put("memory_id", item.id).toString(), text.take(60))
    }

    private fun forget(id: String): ToolOutcome {
        if (id.isBlank()) return fail("memory_id is empty")
        val match = memory.list().firstOrNull { it.id == id || it.id.startsWith(id) }
            ?: return fail("no memory with id '$id'")
        memory.remove(match.id)
        return ToolOutcome(true, JSONObject().put("deleted", true).put("memory_id", match.id).toString(), match.text.take(60))
    }
}
