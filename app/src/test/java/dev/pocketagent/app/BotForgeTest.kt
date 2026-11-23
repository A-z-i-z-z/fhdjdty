package dev.pocketagent.app

import dev.pocketagent.app.data.BotForge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BotForgeTest {
    @Test fun extractsSingleValidBlockAndCleansText() {
        val raw = """
            Here is your new companion!

            ```pocket-bot
            {"name":"Zana","tagline":"Wise astronomer","avatar":"OWL","color":"#3FA7B5","systemPrompt":"You are Zana, a calm astronomer."}
            ```

            Tap below to chat!
        """.trimIndent()

        val res = BotForge.extract(raw)
        assertEquals(1, res.bots.size)
        val b = res.bots[0]
        assertEquals("Zana", b.name)
        assertEquals("Wise astronomer", b.tagline)
        assertEquals("OWL", b.avatar)
        assertEquals(0xFF3FA7B5L, b.color)
        assertEquals("You are Zana, a calm astronomer.", b.systemPrompt)
        assertEquals("Here is your new companion!\n\nTap below to chat!", res.cleanedText)
    }

    @Test fun invalidAvatarAndColorFallBackGracefully() {
        val raw = """
            ```pocket-bot
            {"name":"Spark","avatar":"UNICORN","color":"not-a-color","system_prompt":"Be playful."}
            ```
        """.trimIndent()
        val res = BotForge.extract(raw)
        assertEquals(1, res.bots.size)
        assertEquals("NEWT", res.bots[0].avatar)
        assertEquals(BotForge.DEFAULT_COLOR, res.bots[0].color)
        assertEquals("Be playful.", res.bots[0].systemPrompt)
        assertEquals("", res.cleanedText)
    }

    @Test fun invalidJsonOrMissingFieldsAreLeftUntouched() {
        val raw = """
            ```pocket-bot
            {broken json}
            ```
            ```pocket-bot
            {"name":"","systemPrompt":"missing name"}
            ```
        """.trimIndent()
        val res = BotForge.extract(raw)
        assertTrue(res.bots.isEmpty())
        assertEquals(raw, res.cleanedText)
    }

    @Test fun extractsMultipleBlocksInOrder() {
        val raw = """
            ```pocket-bot
            {"name":"A","avatar":"FOX","color":"#FF0000","systemPrompt":"A prompt"}
            ```
            Intro
            ```pocket-bot
            {"name":"B","avatar":"WOLF","color":"#00FF00","systemPrompt":"B prompt"}
            ```
        """.trimIndent()
        val res = BotForge.extract(raw)
        assertEquals(listOf("A", "B"), res.bots.map { it.name })
        assertEquals("Intro", res.cleanedText)
    }
}
