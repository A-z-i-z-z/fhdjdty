package dev.pocketagent.app.text

import dev.pocketagent.app.ui.text.LayeredTexts
import dev.pocketagent.app.ui.text.TextFormat
import dev.pocketagent.app.ui.text.TextKey
import dev.pocketagent.app.ui.text.TextSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** The pure core of the text layer: layers, formatting, the last resort. No Android types are loaded. */
class TextResolverTest {
    private val key = TextKey(1, "ui_nav_home")

    private fun source(strings: Map<String, String> = emptyMap(), plurals: Map<String, Map<Int, String>> = emptyMap()) = object : TextSource {
        override fun string(key: TextKey): String? = strings[key.name]
        override fun plural(key: TextKey, count: Int): String? = plurals[key.name]?.let { it[count] ?: it[-1] }
    }

    private fun layered(vararg layers: TextSource, onMissing: (TextKey) -> Unit = {}) =
        LayeredTexts(Locale.ENGLISH, layers.toList(), onMissing)

    @Test fun firstLayerWins() {
        val a = source(mapOf("ui_nav_home" to "Inicio"))
        val b = source(mapOf("ui_nav_home" to "Home"))
        assertEquals("Inicio", layered(a, b).string(key, emptyArray()))
        assertEquals("Home", layered(b, a).string(key, emptyArray()))
    }

    @Test fun nullFallsThrough() {
        val empty = source()
        val b = source(mapOf("ui_nav_home" to "Home"))
        assertEquals("Home", layered(empty, b).string(key, emptyArray()))
    }

    @Test fun badFormatFallsThrough() {
        // a String does not fit %d: the first layer is skipped and the second answers
        val bad = source(mapOf("ui_nav_home" to "%1\$d things"))
        val good = source(mapOf("ui_nav_home" to "%1\$s things"))
        assertEquals("abc things", layered(bad, good).string(key, arrayOf("abc")))
        // too few arguments for the first layer
        val needsTwo = source(mapOf("ui_nav_home" to "%1\$s and %2\$s"))
        assertEquals("one", layered(needsTwo, source(mapOf("ui_nav_home" to "%1\$s"))).string(key, arrayOf("one")))
        assertNull(TextFormat.format(Locale.ENGLISH, "%1\$d", arrayOf("x")))
        assertNull(TextFormat.format(Locale.ENGLISH, "%1\$s %2\$s", arrayOf("x")))
        assertEquals("3 bots", TextFormat.format(Locale.ENGLISH, "%1\$d bots", arrayOf(3)))
    }

    @Test fun noArgsIsNotFormatted() {
        val s = source(mapOf("ui_nav_home" to "100%% sure"))
        assertEquals("100%% sure", layered(s).string(key, emptyArray()))
        assertEquals("100% sure", layered(s).string(key, arrayOf("unused")))
    }

    @Test fun missingEverywhereReturnsName() {
        val missed = ArrayList<String>()
        val l = layered(source(), source(), onMissing = { missed += it.name })
        assertEquals("ui_nav_home", l.string(key, emptyArray()))
        assertEquals("ui_nav_home", l.plural(key, 2, emptyArray()))
        assertEquals(listOf("ui_nav_home", "ui_nav_home"), missed)
    }

    @Test fun aThrowingLayerIsSkippedAndAThrowingReportDoesNotCrash() {
        val boom = object : TextSource {
            override fun string(key: TextKey): String? = throw IllegalStateException("boom")
            override fun plural(key: TextKey, count: Int): String? = throw IllegalStateException("boom")
        }
        val ok = source(mapOf("ui_nav_home" to "Home"))
        assertEquals("Home", layered(boom, ok).string(key, emptyArray()))
        assertEquals("ui_nav_home", layered(boom, onMissing = { throw IllegalStateException("log failed") }).string(key, emptyArray()))
    }

    @Test fun pluralUsesLayerForm() {
        val first = source(plurals = mapOf("ui_nav_home" to mapOf(1 to "%1\$d bot", -1 to "%1\$d bots")))
        val l = layered(first, source())
        assertEquals("1 bot", l.plural(key, 1, arrayOf(1)))
        assertEquals("5 bots", l.plural(key, 5, arrayOf(5)))
        // a layer without that plural lets the next one answer
        val second = source(plurals = mapOf("ui_nav_home" to mapOf(-1 to "%1\$d Bots")))
        assertEquals("2 Bots", layered(source(), second).plural(key, 2, arrayOf(2)))
        // no arguments: the template as it is
        assertTrue(layered(first).plural(key, 1, emptyArray()).contains("%1\$d"))
    }
}
