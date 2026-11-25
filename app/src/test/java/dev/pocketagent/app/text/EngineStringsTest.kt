package dev.pocketagent.app.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File

/** The texts that used to be written into the code (res/values/strings_engine.xml): notes, quoting, voice. */
class EngineStringsTest {
    private fun engineFile(): File? {
        val dir = ResXml.languageDirs()["en"] ?: return null
        return File(dir, "strings_engine.xml").takeIf { it.isFile }
    }

    private fun entries(): List<ResEntry> {
        val f = engineFile()
        Assume.assumeTrue("strings_engine.xml not reachable from the test's working directory", f != null)
        return ResXml.parse(f!!)
    }

    @Test fun notesPresent() {
        val missing = entries().filter { it.translatable && it.note.isNullOrBlank() }.map { it.name }
        assertTrue("keys without a translator note: $missing", missing.isEmpty())
    }

    @Test fun everyKeyHasThePrefix() {
        val wrong = entries().map { it.name }.filter { !it.startsWith("eng_") }
        assertTrue("keys without the eng_ prefix: $wrong", wrong.isEmpty())
    }

    @Test fun unescapedQuotesAbsent() {
        val f = engineFile()
        Assume.assumeTrue("strings_engine.xml not reachable", f != null)
        val withoutComments = f!!.readText().replace(Regex("(?s)<!--.*?-->"), "")
        val bad = ArrayList<String>()
        for (m in Regex("(?s)<(string|item)\\b[^>]*>(.*?)</\\1>").findAll(withoutComments)) {
            val body = m.groupValues[2]
            if (Regex("(?<!\\\\)['\"]").containsMatchIn(body)) bad += body.take(50)
            if (body != body.trim()) bad += "leading or trailing space: " + body.take(50)
        }
        assertTrue("texts with an unescaped quote or a space at an edge (aapt2 trims or fails): $bad", bad.isEmpty())
    }

    @Test fun noExclamationMarks() {
        val bad = entries().filter { e -> e.name != "eng_preview_hi" && e.forms.values.any { it.contains('!') } }.map { it.name }
        assertTrue("exclamation marks are allowed only in eng_preview_hi: $bad", bad.isEmpty())
        val hi = entries().first { it.name == "eng_preview_hi" }
        assertEquals("Hi!", hi.forms["_"])
    }

    @Test fun placeholdersAreNumbered() {
        val bad = entries().filter { e ->
            e.forms.values.any { text -> ResXml.placeholders(text).count { !it.contains('$') } > 1 }
        }.map { it.name }
        assertTrue("texts with two or more unnumbered placeholders: $bad", bad.isEmpty())
    }
}
