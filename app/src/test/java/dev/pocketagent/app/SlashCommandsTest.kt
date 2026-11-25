package dev.pocketagent.app

import dev.pocketagent.app.data.ChatMode
import dev.pocketagent.app.data.SlashCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlashCommandsTest {
    @Test fun parsesCommandsAndArgs() {
        val p1 = SlashCommands.parse("  /plan   build a cache  ")!!
        assertEquals("plan", p1.name)
        assertEquals("build a cache", p1.arg)

        val p2 = SlashCommands.parse("/PET")!!
        assertEquals("pet", p2.name)
        assertEquals("", p2.arg)

        assertNull(SlashCommands.parse("hello /plan"))
        assertNull(SlashCommands.parse("/unknowncmd foo"))
        assertNull(SlashCommands.parse("/"))
    }

    @Test fun suggestionsFilterByPrefixAndHideSecretCommands() {
        val allVisible = SlashCommands.suggestions("/")
        assertTrue(allVisible.any { it.name == "plan" })
        assertTrue(allVisible.any { it.name == "forge" })
        assertFalse(allVisible.any { it.name == "pet" })
        assertFalse(allVisible.any { it.name == "newroz" })
        assertFalse(allVisible.any { it.name == "roll" })

        assertEquals(listOf("plan"), SlashCommands.suggestions("/pl").map { it.name })
        assertTrue(SlashCommands.suggestions("/plan hello").isEmpty())
        assertTrue(SlashCommands.suggestions("/p").none { it.name == "pet" })
    }

    @Test fun loopArgsParseCountAndClamp() {
        assertEquals(3 to "check this", SlashCommands.parseLoopArgs("check this"))
        assertEquals(4 to "improve code", SlashCommands.parseLoopArgs("4 improve code"))
        assertEquals(5 to "deep pass", SlashCommands.parseLoopArgs("99 deep pass"))
        assertEquals(2 to "fast pass", SlashCommands.parseLoopArgs("1 fast pass"))
        assertEquals(3 to "", SlashCommands.parseLoopArgs("   "))
    }

    @Test fun modeDirectivesCoverNonNormalModes() {
        assertEquals("", SlashCommands.modeDirective(ChatMode.NORMAL))
        assertTrue(SlashCommands.modeDirective(ChatMode.PLAN).contains("PLAN"))
        assertTrue(SlashCommands.modeDirective(ChatMode.GOAL).contains("GOAL"))
        assertTrue(SlashCommands.modeDirective(ChatMode.ULTRACODE).contains("ULTRACODE"))
        assertTrue(SlashCommands.modeDirective(ChatMode.LOOP).contains("LOOP"))
    }

    @Test fun findCommandSearchesInsideTheChat() {
        val p = SlashCommands.parse("/find  hearth fire ")!!
        assertEquals("find", p.name)
        assertEquals("hearth fire", p.arg)
        assertEquals(listOf("find"), SlashCommands.suggestions("/fi").map { it.name })
        assertTrue(SlashCommands.suggestions("/f").any { it.name == "forge" })
        assertTrue(SlashCommands.suggestions("/f").any { it.name == "find" })
        assertFalse(SlashCommands.suggestions("/f").any { it.name == "fire" })
        assertEquals("find", SlashCommands.parse("/find")?.name)
    }
}
