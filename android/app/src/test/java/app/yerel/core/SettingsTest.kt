package app.yerel.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTest {
    @Test
    fun roundTrip() {
        val s = AppSettings(
            nCtx = 8192, kvTypeK = "q4_0", temperature = 0.3f, disabledTools = setOf("fetch_url"),
            hfToken = "hf_x", lastModelId = "m1", seed = 42,
        )
        assertEquals(s, AppSettings.fromJson(JSONObject(s.toJson().toString())))
        assertEquals(AppSettings(), AppSettings.fromJson(JSONObject()))
    }

    @Test
    fun presetOverridesSampling() {
        val preset = SamplingPreset(temperature = 0.1f, topK = 50)
        val on = JSONObject(AppSettings().genParamsJson(preset, true))
        assertEquals(0.1, on.getDouble("temp"), 1e-6)
        assertEquals(50, on.getInt("top_k"))
        assertEquals(0.95, on.getDouble("top_p"), 1e-6)
        val off = JSONObject(AppSettings(useModelPreset = false).genParamsJson(preset, false))
        assertEquals(0.7, off.getDouble("temp"), 1e-6)
        assertEquals(false, off.getBoolean("enable_thinking"))
    }

    @Test
    fun threads() {
        assertEquals(6, AppSettings().resolvedThreads(8))
        assertEquals(2, AppSettings().resolvedThreads(2))
        assertEquals(3, AppSettings(nThreads = 3).resolvedThreads(8))
        assertEquals(6, JSONObject(AppSettings().contextParamsJson(8)).getInt("n_threads"))
    }

    @Test
    fun conversationRoundTrip() {
        val c = Conversation(
            title = "t",
            messages = listOf(
                ChatMessage(role = Role.USER, content = "merhaba 😀"),
                ChatMessage(
                    role = Role.ASSISTANT, content = "", toolCalls = listOf(ToolCall("1", "calculator", "{}")),
                    stats = GenStats(promptTokens = 10, genTokens = 5, genMs = 500.0, stop = "eos"),
                ),
            ),
        )
        val back = Conversation.fromJson(JSONObject(c.toJson().toString()))
        assertEquals(c, back)
        assertEquals(10.0, back.messages[1].stats!!.tokensPerSecond, 1e-9)
    }
}
