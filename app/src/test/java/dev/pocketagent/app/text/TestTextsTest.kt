package dev.pocketagent.app.text

import android.content.ContextWrapper
import dev.pocketagent.app.R
import dev.pocketagent.app.ui.text.Texts
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.util.Locale

/** The test helper itself, and the promise of Texts under test: the Context is never used. */
class TestTextsTest {
    @Before fun install() = TestTexts.installEnglish()

    @After fun reset() = TestTexts.reset()

    private fun xmlText(name: String): String {
        val dir = ResXml.languageDirs()["en"]
        Assume.assumeTrue("res folders not reachable from the test's working directory", dir != null)
        for (f in ResXml.stringsFiles(dir!!)) {
            val hit = ResXml.parse(f).firstOrNull { it.name == name }
            if (hit != null) return hit.forms["_"] ?: ""
        }
        throw AssertionError("$name is not in the English strings files")
    }

    @Test fun englishRoundTrip() {
        assertEquals(xmlText("eng_no_bots"), Texts.text(R.string.eng_no_bots))
        assertEquals("This chat has no bots (they were deleted). Add a bot to the chat first.", Texts.text(R.string.eng_no_bots))
        assertEquals("Loop mode: pass 2/3", Texts.text(R.string.eng_loop_pass, 2, 3))
        assertEquals("[exit code 7]", Texts.text(R.string.eng_term_exit_code, 7))
    }

    @Test fun escapesAreResolved() {
        // \' in the XML is a plain apostrophe, \" a quote, \n a line break
        assertTrue(Texts.text(R.string.eng_rt_stamp_unreadable, "a", "b").contains("file's permissions"))
        assertTrue(Texts.text(R.string.eng_model_foreign, "m1").contains("\"m1\""))
        assertEquals("Hermes asks permission before it runs this and is waiting for an answer:\nls -l", Texts.text(R.string.eng_approval_pending, "ls -l"))
    }

    @Test fun getIgnoresTheContext() {
        // RuntimeManagerInitTest builds its Context from ContextWrapper(null), whose resources throw
        val text = Texts.get(ContextWrapper(null), R.string.eng_rt_stamp_unreadable, "a", "b")
        assertTrue(text, text.startsWith("Cannot read a: b. The runtime was NOT reinstalled"))
    }

    @Test fun getQuantityIgnoresTheContext() {
        val ctx = ContextWrapper(null)
        assertEquals("1 bot", Texts.getQuantity(ctx, R.plurals.ctl_bots_count, 1, 1))
        assertEquals("2 bots", Texts.getQuantity(ctx, R.plurals.ctl_bots_count, 2, 2))
        assertEquals("0 bots", Texts.getQuantity(ctx, R.plurals.ctl_bots_count, 0, 0))
        assertEquals("Import 1 bot?", Texts.textQuantity(R.plurals.ctl_bots_import_title, 1, 1))
        assertEquals("Import 4 bots?", Texts.textQuantity(R.plurals.ctl_bots_import_title, 4, 4))
    }

    @Test fun theChosenLanguageIsEnglishUnderTest() {
        assertEquals(Locale.ENGLISH, Texts.currentLocale())
        assertEquals(Locale.ENGLISH, Texts.localeOf(ContextWrapper(null)))
    }

    @Test fun resetGoesBackToTheHashId() {
        TestTexts.reset()
        // before install the context-free lookup never throws and never answers blank
        assertEquals("#" + R.string.eng_no_bots, Texts.text(R.string.eng_no_bots))
        TestTexts.installEnglish()
        assertEquals(xmlText("eng_no_bots"), Texts.text(R.string.eng_no_bots))
    }

    @Test fun anUnknownIdAnswersWithItsNumberAndNeverThrows() {
        val text = Texts.text(0x7f7f7f7f)
        assertTrue(text, text.startsWith("#"))
    }
}
