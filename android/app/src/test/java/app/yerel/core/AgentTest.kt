package app.yerel.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class AgentTest {
    private class ScriptedBackend(val replies: MutableList<(JSONArray) -> String>) : LlmBackend {
        val seen = mutableListOf<JSONArray>()
        override fun generate(messagesJson: String, toolsJson: String, paramsJson: String, onUpdate: (String) -> Unit): String {
            val msgs = JSONArray(messagesJson)
            seen += msgs
            onUpdate("""{"message":{"role":"assistant","content":"..."},"n_gen":1,"tps":10}""")
            return replies.removeAt(0)(msgs)
        }
    }

    private fun answer(content: String, calls: String? = null, stop: String = "eos") = JSONObject()
        .put("message", JSONObject("""{"role":"assistant","content":${JSONObject.quote(content)}${calls?.let { ",\"tool_calls\":$it" } ?: ""}}"""))
        .put("stats", JSONObject().put("gen_tokens", 5).put("gen_ms", 100.0).put("stop", stop))
        .toString()

    private val tools = ToolRunner(FakeHttp(), FakeMemory(), { ToolConfig() })
    private val config = AgentConfig("sys", ToolCatalog.toolsJson(listOf("calculator")), "{}", 4)

    @Test
    fun runsToolLoop() {
        val backend = ScriptedBackend(
            mutableListOf(
                { _ -> answer("", """[{"type":"function","function":{"name":"calculator","arguments":"{\"expression\":\"17*23\"}"}}]""") },
                { msgs ->
                    val tool = msgs.getJSONObject(msgs.length() - 1)
                    assertEquals("tool", tool.getString("role"))
                    assertTrue(tool.getString("content").contains("391"))
                    val call = msgs.getJSONObject(msgs.length() - 2).getJSONArray("tool_calls").getJSONObject(0)
                    assertEquals(call.getString("id"), tool.getString("tool_call_id"))
                    answer("Sonuç 391.")
                },
            ),
        )
        val events = mutableListOf<AgentEvent>()
        val history = listOf(ChatMessage(role = Role.USER, content = "17*23 kaç?"))
        val added = AgentRunner(backend, tools) { false }.run(history, config) { events += it }
        assertEquals(listOf(Role.ASSISTANT, Role.TOOL, Role.ASSISTANT), added.map { it.role })
        assertEquals("Sonuç 391.", added.last().content)
        assertEquals("17*23 = 391", added[1].reasoning)
        assertTrue(events.any { it is AgentEvent.ToolStarted })
        assertTrue(events.first() is AgentEvent.Partial)
        assertEquals("system", backend.seen[0].getJSONObject(0).getString("role"))
    }

    @Test
    fun stopsAtMaxSteps() {
        val loop = { _: JSONArray -> answer("", """[{"id":"x","type":"function","function":{"name":"get_datetime","arguments":"{}"}}]""") }
        val backend = ScriptedBackend(MutableList(10) { loop })
        val added = AgentRunner(backend, tools) { false }
            .run(listOf(ChatMessage(role = Role.USER, content = "?")), config.copy(maxSteps = 2)) {}
        assertEquals(4, added.size)
        assertEquals(2, backend.seen.size)
    }

    @Test
    fun trimsOldestOnOverflow() {
        val backend = ScriptedBackend(
            mutableListOf(
                { _ -> """{"error":"context_overflow","prompt_tokens":5000,"n_ctx":4096}""" },
                { msgs ->
                    assertEquals(2, msgs.length()) // system + the newest user message
                    answer("tamam")
                },
            ),
        )
        val history = listOf(
            ChatMessage(role = Role.USER, content = "eski"),
            ChatMessage(role = Role.ASSISTANT, content = "eski cevap"),
            ChatMessage(role = Role.USER, content = "yeni"),
        )
        val events = mutableListOf<AgentEvent>()
        AgentRunner(backend, tools) { false }.run(history, config) { events += it }
        assertEquals(AgentEvent.Trimmed(2), events.filterIsInstance<AgentEvent.Trimmed>().single())
    }

    @Test
    fun overflowWithNothingToDropReportsError() {
        val backend = ScriptedBackend(mutableListOf({ _ -> """{"error":"context_overflow"}""" }))
        val added = AgentRunner(backend, tools) { false }
            .run(listOf(ChatMessage(role = Role.USER, content = "uzun")), config) {}
        assertTrue(added.single().error!!.contains("bağlam"))
    }

    @Test
    fun cancelDropsToolCalls() {
        val backend = ScriptedBackend(
            mutableListOf({ _ -> answer("yarım", """[{"id":"a","type":"function","function":{"name":"calculator","arguments":"{}"}}]""", stop = "cancel") }),
        )
        val added = AgentRunner(backend, tools) { false }
            .run(listOf(ChatMessage(role = Role.USER, content = "x")), config) {}
        assertEquals(1, added.size)
        assertTrue(added[0].toolCalls.isEmpty())
    }

    @Test
    fun engineMessagesFormat() {
        val history = listOf(
            ChatMessage(role = Role.USER, content = "a"),
            ChatMessage(role = Role.ASSISTANT, content = "", reasoning = "think", toolCalls = listOf(ToolCall("c1", "calculator", ""))),
            ChatMessage(role = Role.TOOL, content = "{}", toolCallId = "c1", toolName = "calculator"),
            ChatMessage(role = Role.ASSISTANT, content = "", error = "boom"),
            ChatMessage(role = Role.ASSISTANT, content = "b"),
        )
        val arr = JSONArray(Prompt.engineMessages("", history))
        assertEquals(4, arr.length())
        val call = arr.getJSONObject(1)
        assertEquals("think", call.getString("reasoning_content"))
        assertEquals("{}", call.getJSONArray("tool_calls").getJSONObject(0).getJSONObject("function").getString("arguments"))
        assertEquals("c1", arr.getJSONObject(2).getString("tool_call_id"))
    }

    @Test
    fun strayThinkingMovesToReasoning() {
        val m = EngineJson.parseMessage(org.json.JSONObject("""{"content":"Still thinking.\n</think>\n\n12","reasoning_content":"Okay"}"""))!!
        assertEquals("12", m.content)
        assertEquals("Okay\nStill thinking.", m.reasoning)
        val n = EngineJson.parseMessage(org.json.JSONObject("""{"content":"24</think>24"}"""))!!
        assertEquals("24", n.content)
        assertEquals("24", n.reasoning)
        val plain = EngineJson.parseMessage(org.json.JSONObject("""{"content":"a <b> c"}"""))!!
        assertEquals("a <b> c", plain.content)
    }

    @Test
    fun systemPromptIncludesMemories() {
        val s = Prompt.systemPrompt("Base", listOf(MemoryItem(id = "12345678-abcd", text = "Adı Deniz")), Date(0), toolsOn = true)
        assertTrue(s.startsWith("Base\n\nBugünün tarihi: 1 Ocak 1970, Perşembe."))
        assertTrue(s.contains("- 12345678: Adı Deniz"))
        assertTrue(s.contains("remember"))
    }
}
