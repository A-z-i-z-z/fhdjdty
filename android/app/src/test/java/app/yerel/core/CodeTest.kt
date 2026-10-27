package app.yerel.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeTest {
    /** Stands in for the WebView: reports a syntax error when the code contains "SYNTAX". */
    private class FakeJs : JsSandbox {
        val ran = mutableListOf<String>()
        override fun run(code: String, timeoutMs: Long): JsRun {
            ran += code
            return if ("throw" in code) JsRun(listOf("a"), null, "Error: boom (line 2)") else JsRun(listOf("3"), "undefined", null)
        }

        override fun syntaxError(code: String) = if ("SYNTAX" in code) "SyntaxError: Unexpected token (line 1)" else null
    }

    @Test
    fun extractsFencedBlocks() {
        val md = "Here:\n\n```html\n<p>hi</p>\n```\n\ntext\n\n~~~js\nconsole.log(1)\n~~~\n\n```svg\n<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>"
        val blocks = CodeBlocks.extract(md)
        assertEquals(listOf(CodeLang.HTML, CodeLang.JAVASCRIPT, CodeLang.SVG), blocks.map { it.kind })
        assertEquals("<p>hi</p>", blocks[0].code)
        assertEquals("console.log(1)", blocks[1].code)
        // unclosed last fence of a cut-off answer still counts
        assertTrue(blocks[2].code.startsWith("<svg"))
    }

    @Test
    fun detectsLanguageFromContent() {
        assertEquals(CodeLang.SVG, CodeLang.of("xml", "<svg viewBox='0 0 1 1'/>"))
        assertEquals(CodeLang.HTML, CodeLang.of("", "<!DOCTYPE html><html></html>"))
        assertEquals(CodeLang.OTHER, CodeLang.of("rust"))
        assertTrue(CodeLang.SVG.previewable && CodeLang.HTML.previewable && !CodeLang.JSON.previewable)
    }

    @Test
    fun checksJson() {
        assertNull(CodeCheck.json("""{"a": [1, 2, {"b": null}]}"""))
        val err = CodeCheck.json("{\n  \"a\": 1,\n  \"b\": \n}")
        assertTrue(err!!, err.startsWith("JSON"))
        assertTrue(err, err.contains("line"))
    }

    @Test
    fun checksSvg() {
        val ok = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"><circle cx="5" cy="5" r="4"/></svg>"""
        assertTrue(CodeCheck.svg(ok).isEmpty())
        val broken = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\">\n<circle cx=\"5\" cy=\"5\" r=\"4\">\n</svg>"
        val p = CodeCheck.svg(broken)
        assertEquals(1, p.size)
        assertTrue(p[0], p[0].contains("line"))
        val noNs = CodeCheck.svg("""<svg viewBox="0 0 1 1"></svg>""")
        assertTrue(noNs.any { "xmlns" in it })
    }

    @Test
    fun checksHtmlStructure() {
        val ok = """<!DOCTYPE html>
            |<html><head><meta charset="utf-8"><style>body { color: red; }</style></head>
            |<body><ul><li>one<li>two</ul><p>text<br><img src="x.png">
            |<script>if (a < b) { console.log("<div>"); }</script></body></html>""".trimMargin()
        assertEquals(emptyList<String>(), CodeCheck.html(ok, FakeJs()))
        val bad = "<div>\n<span>text</div>\n<section>"
        val p = CodeCheck.html(bad)
        assertTrue(p.joinToString(), p.any { "<span>" in it && "line 2" in it })
        assertTrue(p.joinToString(), p.any { "<section>" in it && "never closed" in it })
        val badScript = CodeCheck.html("<p>x</p>\n<script>\nSYNTAX\n</script>", FakeJs())
        assertTrue(badScript.joinToString(), badScript.single().startsWith("<script> at line 2"))
        val badStyle = CodeCheck.html("<style>a { color: red;</style>")
        assertTrue(badStyle.joinToString(), badStyle.single().contains("not closed"))
    }

    @Test
    fun checksCss() {
        assertTrue(CodeCheck.css("a{b:c} /* { */ d{content:'}'}").isEmpty())
        assertEquals(1, CodeCheck.css("a{b:c}}").size)
    }

    @Test
    fun codeTools() {
        val js = FakeJs()
        val runner = ToolRunner(FakeHttp(), FakeMemory(), { ToolConfig() }, js = js)
        val r = runner.run("run_javascript", JSONObject().put("code", "console.log(1+2)").toString())
        assertTrue(r.ok)
        assertEquals("3", JSONObject(r.content).getJSONArray("console").getString(0))
        val e = runner.run("run_javascript", JSONObject().put("code", "throw new Error('boom')").toString())
        assertTrue(!e.ok && JSONObject(e.content).getString("error").contains("boom"))
        val c = runner.run("check_code", JSONObject().put("language", "json").put("code", "{\"a\":}").toString())
        assertTrue(c.ok)
        assertEquals(false, JSONObject(c.content).getBoolean("ok"))
        assertTrue(ToolCatalog.byName("run_javascript")!!.coding)
    }

    private fun answer(content: String, stop: String = "eos") = JSONObject()
        .put("message", JSONObject().put("role", "assistant").put("content", content))
        .put("stats", JSONObject().put("gen_tokens", 5).put("gen_ms", 100.0).put("stop", stop))
        .toString()

    private class Scripted(val replies: MutableList<String>) : LlmBackend {
        val seen = mutableListOf<Pair<JSONArray, JSONObject>>()
        override fun generate(messagesJson: String, toolsJson: String, paramsJson: String, onUpdate: (String) -> Unit): String {
            seen += JSONArray(messagesJson) to JSONObject(paramsJson)
            return replies.removeAt(0)
        }
    }

    @Test
    fun autoCheckAsksForAFix() {
        val broken = "```svg\n<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 9 9\"><rect></svg>\n```"
        val fixed = "```svg\n<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 9 9\"><rect/></svg>\n```"
        val backend = Scripted(mutableListOf(answer(broken), answer(fixed)))
        val tools = ToolRunner(FakeHttp(), FakeMemory(), { ToolConfig() })
        val cfg = AgentConfig("sys", "[]", "{}", 1, autoCheck = true)
        val added = AgentRunner(backend, tools) { false }.run(listOf(ChatMessage(role = Role.USER, content = "draw")), cfg) {}
        assertEquals(listOf(Role.ASSISTANT, Role.USER, Role.ASSISTANT), added.map { it.role })
        assertTrue(added[1].auto)
        assertTrue(added[1].content.contains("SVG"))
        val second = backend.seen[1].first
        assertEquals("user", second.getJSONObject(second.length() - 1).getString("role"))
        assertEquals(fixed, added.last().content)
    }

    @Test
    fun autoCheckGivesUpAfterMaxRepairs() {
        val broken = "```json\n{\"a\":}\n```"
        val backend = Scripted(mutableListOf(answer(broken), answer(broken), answer(broken)))
        val tools = ToolRunner(FakeHttp(), FakeMemory(), { ToolConfig() })
        val cfg = AgentConfig("sys", "[]", "{}", 1, autoCheck = true, maxRepairs = 2)
        val added = AgentRunner(backend, tools) { false }.run(listOf(ChatMessage(role = Role.USER, content = "json")), cfg) {}
        assertEquals(5, added.size)
        assertEquals(3, backend.seen.size)
    }

    @Test
    fun continuesATruncatedAnswer() {
        val backend = Scripted(mutableListOf(answer(" world")))
        val tools = ToolRunner(FakeHttp(), FakeMemory(), { ToolConfig() })
        val last = ChatMessage(role = Role.ASSISTANT, content = "hello", stats = GenStats(genTokens = 10, genMs = 50.0, stop = "length"))
        val history = listOf(ChatMessage(role = Role.USER, content = "hi"), last)
        val out = AgentRunner(backend, tools) { false }.continueLast(history, AgentConfig("sys", "[]", "{}", 1)) {}!!
        assertEquals("hello world", out.content)
        assertEquals(last.id, out.id)
        assertEquals(15, out.stats!!.genTokens)
        assertEquals(true, backend.seen[0].second.getBoolean("continue"))
        val msgs = backend.seen[0].first
        assertEquals("assistant", msgs.getJSONObject(msgs.length() - 1).getString("role"))
    }
}
