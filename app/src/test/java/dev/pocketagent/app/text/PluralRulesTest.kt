package dev.pocketagent.app.text

import dev.pocketagent.app.ui.text.PluralRules
import org.junit.Assert.assertEquals
import org.junit.Test

class PluralRulesTest {
    private fun cats(lang: String): List<String> = listOf(0L, 1L, 2L, 5L, 11L, 21L, 22L).map { PluralRules.category(lang, it) }

    @Test fun table() {
        assertEquals(listOf("other", "one", "other", "other", "other", "other", "other"), cats("en"))
        assertEquals(cats("en"), cats("tr"))
        assertEquals(cats("en"), cats("ku"))
        assertEquals(listOf("one", "one", "other", "other", "other", "other", "other"), cats("fr"))
        assertEquals(cats("fr"), cats("pt"))
        assertEquals(listOf("many", "one", "few", "many", "many", "one", "few"), cats("ru"))
        assertEquals(List(7) { "other" }, cats("id"))
        assertEquals(List(7) { "other" }, cats("ja"))
    }

    @Test fun regionAndCaseAreIgnored() {
        assertEquals("one", PluralRules.category("FR", 1))
        assertEquals("one", PluralRules.category("fr-CA", 0))
        assertEquals("one", PluralRules.category("en_GB", 1))
    }

    @Test fun russianTeens() {
        assertEquals("many", PluralRules.category("ru", 12))
        assertEquals("many", PluralRules.category("ru", 111))
        assertEquals("few", PluralRules.category("ru", 103))
        assertEquals("one", PluralRules.category("ru", 101))
    }
}
