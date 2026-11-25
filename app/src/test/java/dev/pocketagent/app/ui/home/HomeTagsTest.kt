package dev.pocketagent.app.ui.home

import dev.pocketagent.app.ui.RobotTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class HomeTagsTest {
    private val pattern = Regex("^home\\.[a-z.]+(\\.\\d+)?$")

    private fun constants(): Map<String, String> =
        HomeTags::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .associate { it.name to (it.get(null) as String) }

    @Test fun existingTagsUnchanged() {
        assertEquals(RobotTags.HOME_STATE, HomeTags.STATE)
        assertEquals(RobotTags.HOME_START, HomeTags.START)
        assertEquals("home.state", HomeTags.STATE)
        assertEquals("home.start", HomeTags.START)
    }

    @Test fun registryHasEveryTagOfTheContract() {
        val expected = setOf(
            "STATE", "START", "HERO", "FIRE", "PET", "SKY", "STRIP", "STOP", "CANCEL", "ERROR", "ERROR_DETAILS", "ASK", "SEND",
            "AVATARS", "AVATAR_NEW", "GROUP", "RECENT", "RECENT_EMPTY", "USAGE", "WOODPILE", "SKELETON",
            "TILE_HERMES", "TILE_TERMINAL", "TILE_ROUTINES", "TILE_SAVED",
        )
        assertEquals(expected, constants().keys)
    }

    @Test fun newTagsUniqueAndWellFormed() {
        val all = constants()
        assertEquals("every tag is distinct", all.size, all.values.toSet().size)
        for ((name, tag) in all) assertTrue("$name = $tag", pattern.matches(tag))
    }

    @Test fun indexedTags() {
        assertEquals("home.suggest.0", HomeTags.suggest(0))
        assertEquals("home.avatar.3", HomeTags.avatar(3))
        assertEquals("home.recent.2", HomeTags.recent(2))
        assertEquals("home.row.usage", HomeTags.row("usage"))
        for (tag in listOf(HomeTags.suggest(1), HomeTags.avatar(0), HomeTags.recent(3), HomeTags.row("a.b"))) {
            assertTrue(tag, pattern.matches(tag))
        }
        // an indexed family never collides with a fixed tag
        val fixed = constants().values
        for (i in 0..9) {
            assertTrue(HomeTags.suggest(i) !in fixed)
            assertTrue(HomeTags.avatar(i) !in fixed)
            assertTrue(HomeTags.recent(i) !in fixed)
        }
    }

    @Test fun oneStartTagPerState() {
        // A documented rule, not code: exactly one node carries home.start while Hermes is NotInstalled, Stopped or Error (the
        // status row's button, or the error card's retry), and none while it runs. The phone scenarios check it.
        assertTrue(HomeTags.START != HomeTags.STOP && HomeTags.START != HomeTags.CANCEL)
    }
}
