package dev.pocketagent.app.text

import dev.pocketagent.app.ui.text.resolveAppLocale
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * Defect S2 of the Wave 0 phone proof: text in a dialog window was in the PHONE's language while the app was set
 * to English. The language the app chose must win over the configuration of the window that asks.
 */
class AppLocaleTest {
    private val turkishPhone = Locale.forLanguageTag("tr-TR")

    @Test
    fun theChosenLanguageBeatsThePhonesConfiguration() {
        assertEquals("en", resolveAppLocale("en", turkishPhone).language)
        assertEquals("fr", resolveAppLocale("fr", turkishPhone).language)
        assertEquals("tr", resolveAppLocale("tr", Locale.ENGLISH).language)
    }

    @Test
    fun noChoiceMeansTheSystemLanguage() {
        assertEquals(turkishPhone, resolveAppLocale("", turkishPhone))
        assertEquals(turkishPhone, resolveAppLocale("   ", turkishPhone))
    }

    @Test
    fun aTagThatNamesNoLanguageIsIgnored() {
        assertEquals(turkishPhone, resolveAppLocale("und", turkishPhone))
        assertEquals(turkishPhone, resolveAppLocale("-", turkishPhone))
        assertEquals(turkishPhone, resolveAppLocale("???", turkishPhone))
    }

    @Test
    fun theStretchedTestLanguageKeepsItsRegion() {
        val l = resolveAppLocale("en-XZ", turkishPhone)
        assertEquals("en", l.language)
        assertEquals("XZ", l.country)
        assertEquals("en-XZ", l.toLanguageTag())
    }

    @Test
    fun aTagWithSpacesAroundItStillWorks() {
        assertEquals("fr", resolveAppLocale(" fr ", turkishPhone).language)
    }
}
