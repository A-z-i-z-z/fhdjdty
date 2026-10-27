package app.yerel.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date
import java.util.TimeZone

class ToolsTest {
    private val http = FakeHttp()
    private val memory = FakeMemory()
    private var config = ToolConfig()
    private val runner = ToolRunner(
        http, memory, { config },
        now = { Date(1_761_559_200_000L) }, // 2025-10-27 10:00 UTC
        timeZone = { TimeZone.getTimeZone("Europe/Istanbul") },
    )

    @Test
    fun toolsJsonListsEnabled() {
        val arr = JSONArray(ToolCatalog.toolsJson(listOf("calculator", "web_search")))
        assertEquals(2, arr.length())
        assertEquals("web_search", arr.getJSONObject(0).getJSONObject("function").getString("name"))
        val params = arr.getJSONObject(1).getJSONObject("function").getJSONObject("parameters")
        assertEquals("expression", params.getJSONArray("required").getString(0))
    }

    @Test
    fun calculator() {
        val r = runner.run("calculator", """{"expression":"7+5"}""")
        assertTrue(r.ok)
        assertEquals("12", JSONObject(r.content).getString("result"))
        assertFalse(runner.run("calculator", """{"expression":"1/0"}""").ok)
        assertFalse(runner.run("calculator", "not json").ok)
        assertFalse(runner.run("nope", "{}").ok)
    }

    @Test
    fun datetime() {
        val r = runner.run("get_datetime", "")
        val o = JSONObject(r.content)
        assertEquals("2025-10-27T13:00:00+03:00", o.getString("iso"))
        assertEquals("Pazartesi", o.getString("weekday"))
        assertEquals("Europe/Istanbul", o.getString("time_zone"))
    }

    @Test
    fun memoryRememberAndForget() {
        val r1 = runner.run("remember", """{"fact":"Kullanıcının adı Deniz."}""")
        assertTrue(r1.ok)
        assertEquals(1, memory.items.size)
        runner.run("remember", """{"fact":"kullanıcının adı deniz."}""")
        assertEquals(1, memory.items.size)
        val id = memory.items[0].id
        assertTrue(runner.run("forget", """{"memory_id":"${id.take(8)}"}""").ok)
        assertTrue(memory.items.isEmpty())
        assertFalse(runner.run("forget", """{"memory_id":"zzz"}""").ok)
    }

    @Test
    fun webSearchFallsBackToLite() {
        http.handler = { method, url, form ->
            when {
                method == "POST" -> {
                    assertEquals("hava durumu", form["q"])
                    HttpResult(202, "", "text/html", url)
                }
                url.startsWith("https://lite.duckduckgo.com/lite/?q=hava+durumu") -> HttpResult(
                    200,
                    "<a href=\"https://mgm.gov.tr/\" class='result-link'>MGM</a><td class='result-snippet'>Tahmin</td>",
                    "text/html", url,
                )
                else -> HttpResult(500, "", null, url)
            }
        }
        val r = runner.run("web_search", """{"query":"hava durumu"}""")
        assertTrue(r.content, r.ok)
        val first = JSONObject(r.content).getJSONArray("results").getJSONObject(0)
        assertEquals("https://mgm.gov.tr/", first.getString("url"))
        assertEquals(2, http.requests.size)
    }

    @Test
    fun webSearchSearxng() {
        config = ToolConfig(searchProvider = "searxng", searxngUrl = "https://searx.example/")
        http.handler = { _, url, _ ->
            assertTrue(url.startsWith("https://searx.example/search?q=kotlin&format=json"))
            HttpResult(200, """{"results":[{"title":"Kotlin","url":"https://kotlinlang.org","content":"Lang"}]}""", "application/json", url)
        }
        val r = runner.run("web_search", """{"query":"kotlin"}""")
        assertTrue(r.ok)
        assertEquals("1 sonuç", r.summary)
    }

    @Test
    fun fetchUrlConvertsHtml() {
        config = ToolConfig(fetchMaxChars = 50)
        http.handler = { _, url, _ ->
            HttpResult(200, "<title>T</title><p>${"x".repeat(80)}</p>", "text/html; charset=utf-8", "$url/final")
        }
        val r = runner.run("fetch_url", """{"url":"https://a.example"}""")
        val o = JSONObject(r.content)
        assertEquals("T", o.getString("title"))
        assertEquals("https://a.example/final", o.getString("url"))
        assertTrue(o.getString("text").endsWith("kısaltıldı]"))
        assertFalse(runner.run("fetch_url", """{"url":"file:///etc/passwd"}""").ok)
    }

    @Test
    fun wikipedia() {
        http.handler = { _, url, _ ->
            when {
                "api.php" in url -> HttpResult(200, """{"query":{"search":[{"title":"Ankara"},{"title":"Ankara Kalesi"}]}}""", "application/json", url)
                "page/summary/Ankara" in url -> HttpResult(
                    200,
                    """{"title":"Ankara","extract":"Türkiye'nin başkenti.","content_urls":{"mobile":{"page":"https://tr.m.wikipedia.org/wiki/Ankara"}}}""",
                    "application/json", url,
                )
                else -> HttpResult(404, "", null, url)
            }
        }
        val r = runner.run("wikipedia", """{"query":"ankara"}""")
        assertTrue(r.ok)
        val o = JSONObject(r.content)
        assertEquals("Türkiye'nin başkenti.", o.getString("summary"))
        assertTrue(http.requests[0].contains("https://tr.wikipedia.org/"))
    }
}
