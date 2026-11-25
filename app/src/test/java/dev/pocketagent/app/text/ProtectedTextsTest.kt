package dev.pocketagent.app.text

import dev.pocketagent.app.ui.text.ProtectedTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File

class ProtectedTextsTest {
    @Test fun parseAndMatch() {
        val p = ProtectedTexts.parse(
            listOf("# a comment", "", "  ok  ", "ui_dlg_delete_title", "price_*", "#ignored_name", "   ", "x_*"),
        )
        assertEquals(setOf("ok", "ui_dlg_delete_title"), p.exact)
        assertEquals(listOf("price_", "x_"), p.prefixes)
        assertTrue(p.isProtected("ok"))
        assertTrue(p.isProtected("ui_dlg_delete_title"))
        assertTrue(p.isProtected("price_monthly"))
        assertTrue(p.isProtected("x_anything"))
        assertFalse(p.isProtected("ok_button"))
        assertFalse(p.isProtected("ui_dlg_delete_title_extra"))
        assertFalse(p.isProtected("ignored_name"))
        assertFalse(p.isProtected("price"))
    }

    @Test fun aLoneStarIsNotAPrefixOfEverything() {
        val p = ProtectedTexts.parse(listOf("*"))
        assertFalse(p.isProtected("anything"))
    }

    @Test fun shippedFileNamesExist() {
        val main = ResXml.mainDir()
        Assume.assumeTrue("src/main not reachable from the test's working directory", main != null)
        val file = File(main, "assets/text/protected-keys.txt")
        Assume.assumeTrue("protected-keys.txt not found", file.isFile)
        val protected = ProtectedTexts.parse(file.readLines())
        val dir = ResXml.languageDirs()["en"]!!
        val keys = ResXml.stringsFiles(dir).flatMap { f -> ResXml.parse(f).map { it.name } }.toSet()
        val missing = protected.exact.filter { it !in keys }
        assertTrue("protected names that do not exist: $missing", missing.isEmpty())
        assertTrue("the list is not empty", protected.exact.isNotEmpty())
        // the words of an approval can never be swapped by a later layer
        assertTrue(protected.isProtected("eng_approval_pending"))
        assertTrue(protected.isProtected("ui_chat_appr_deny"))
    }
}
