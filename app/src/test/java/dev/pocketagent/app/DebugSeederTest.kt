package dev.pocketagent.app

import dev.pocketagent.app.DebugSeeder.Spec
import dev.pocketagent.app.data.BudgetMode
import dev.pocketagent.app.runtime.PracticeModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DebugSeederTest {
    @Test fun parsesEveryForm() {
        assertEquals(Spec.Chats(120), DebugSeeder.parse("chats:120"))
        assertEquals(Spec.Chat(200, 400), DebugSeeder.parse("chat:200:400"))
        assertEquals(Spec.Routine(2), DebugSeeder.parse("routine:2"))
        assertEquals(Spec.Usage(200, BudgetMode.WARN), DebugSeeder.parse("usage:200:WARN"))
        assertEquals(Spec.Usage(100, BudgetMode.BLOCK), DebugSeeder.parse("usage:100:BLOCK"))
        assertEquals(Spec.Usage(0, BudgetMode.OFF), DebugSeeder.parse("usage:0:OFF"))
        assertEquals(Spec.Clear, DebugSeeder.parse("clear"))
    }

    @Test fun toleratesSurroundingSpaceAndModeCase() {
        assertEquals(Spec.Chats(5), DebugSeeder.parse("  chats:5  "))
        assertEquals(Spec.Usage(50, BudgetMode.WARN), DebugSeeder.parse("usage:50:warn"))
    }

    @Test fun rejectsAnythingElse() {
        val bad = listOf(
            "", " ", "chats", "chats:", "chats:0", "chats:-1", "chats:+3", "chats:1.5", "chats:abc", "chats:501", "chats:1:2",
            "chat", "chat:5", "chat:0:10", "chat:10:0", "chat:2001:10", "chat:10:20001", "chat:1:2:3",
            "routine", "routine:0", "routine:1441", "routine:x", "routine:1:2",
            "usage", "usage:100", "usage:100:MAYBE", "usage:-1:WARN", "usage:2000000001:WARN", "usage::WARN",
            "clear:1", "clear:", "Clear", "CHATS:5", "chats:99999999999999999999", "seed:5", "../chats:5",
        )
        for (text in bad) assertNull("'$text' must not parse", DebugSeeder.parse(text))
    }

    @Test fun acceptsTheLimits() {
        assertEquals(Spec.Chats(DebugSeeder.MAX_CHATS), DebugSeeder.parse("chats:${DebugSeeder.MAX_CHATS}"))
        assertEquals(
            Spec.Chat(DebugSeeder.MAX_MESSAGES, DebugSeeder.MAX_CHARS),
            DebugSeeder.parse("chat:${DebugSeeder.MAX_MESSAGES}:${DebugSeeder.MAX_CHARS}"),
        )
        assertEquals(Spec.Routine(DebugSeeder.MAX_MINUTES), DebugSeeder.parse("routine:${DebugSeeder.MAX_MINUTES}"))
        assertEquals(Spec.Chats(1), DebugSeeder.parse("chats:1"))
    }

    @Test fun messageTextHasExactlyTheAskedLength() {
        for (n in listOf(1, 2, 5, 17, 18, 19, 40, 400, 3999)) {
            for (i in listOf(0, 1, 7, 199)) {
                val t = DebugSeeder.messageText(i, n)
                assertEquals("index $i, $n chars", n, t.length)
                assertFalse("no trailing space", t.endsWith(" "))
            }
        }
        assertEquals("", DebugSeeder.messageText(0, 0))
    }

    @Test fun messageTextIsDeterministicAndNumbered() {
        assertEquals(DebugSeeder.messageText(3, 120), DebugSeeder.messageText(3, 120))
        assertTrue(DebugSeeder.messageText(3, 120).startsWith("Seed message 4. "))
        assertNotEquals(DebugSeeder.messageText(0, 120), DebugSeeder.messageText(1, 120))
        // Never a practice keyword: a seeded chat must not trigger the scripted model's magic words.
        val text = (0 until 30).joinToString(" ") { DebugSeeder.messageText(it, 300) }
        for (kw in PracticeModel.KEYWORDS) {
            assertFalse("keyword $kw in seeded text", Regex("(?<![A-Za-z0-9_])$kw(?![A-Za-z0-9_])").containsMatchIn(text))
        }
    }

    @Test fun onlyTheTwinMaySeed() {
        DebugSeeder.requireTwin(true)
        try {
            DebugSeeder.requireTwin(false)
            fail("a build that is not the twin must refuse")
        } catch (_: IllegalStateException) {
        }
    }

    @Test fun seededIdsHaveTheDocumentedPrefix() {
        assertEquals("seed-", DebugSeeder.SEED_PREFIX)
    }
}
