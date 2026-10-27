package app.yerel.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlTextTest {
    @Test
    fun toTextKeepsReadableStructure() {
        val html = """
            <html><head><title>Örnek &amp; Sayfa</title><style>body{}</style></head>
            <body><nav>menu</nav><h1>Başlık</h1><p>Merhaba&nbsp;dünya &#128512;</p>
            <ul><li>bir</li><li>iki</li></ul><script>alert(1)</script><!-- gizli --></body></html>
        """.trimIndent()
        assertEquals("Örnek & Sayfa", HtmlText.title(html))
        val text = HtmlText.toText(html)
        assertEquals("## Başlık\nMerhaba dünya 😀\n\n- bir\n- iki", text)
        assertFalse("alert" in text || "menu" in text || "gizli" in text)
    }

    @Test
    fun truncates() {
        val text = HtmlText.toText("<p>" + "a".repeat(100) + "</p>", 10)
        assertTrue(text.startsWith("aaaaaaaaaa\n"))
        assertTrue(text.endsWith("truncated]"))
    }

    @Test
    fun parsesDuckDuckGoHtml() {
        val html = """
            <div class="result"><h2><a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fa%3Fb%3D1&amp;rut=x">Example <b>A</b></a></h2>
            <a class="result__snippet" href="#">Snippet <b>one</b> &amp; more</a></div>
            <div class="result"><h2><a rel="nofollow" class="result__a" href="https://duckduckgo.com/y.js?ad_provider=x">Ad</a></h2>
            <a class="result__snippet" href="#">ad</a></div>
            <div class="result"><h2><a class="result__a" href="https://second.org/">Second</a></h2>
            <div class="result__snippet">Two</div></div>
        """.trimIndent()
        val r = HtmlText.parseDuckDuckGo(html)
        assertEquals(2, r.size)
        assertEquals(SearchResult("Example A", "https://example.com/a?b=1", "Snippet one & more"), r[0])
        assertEquals("https://second.org/", r[1].url)
    }

    @Test
    fun parsesDuckDuckGoLite() {
        val html = """
            <table><tr><td><a rel="nofollow" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Flite.example%2F" class='result-link'>Lite Result</a></td></tr>
            <tr><td class='result-snippet'>Lite snippet</td></tr></table>
        """.trimIndent()
        val r = HtmlText.parseDuckDuckGo(html)
        assertEquals(listOf(SearchResult("Lite Result", "https://lite.example/", "Lite snippet")), r)
    }
}
