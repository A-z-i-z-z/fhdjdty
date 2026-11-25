package dev.pocketagent.app.text

import dev.pocketagent.app.R
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.pixel.AvatarId
import dev.pocketagent.app.ui.text.EnumLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

/** A control with no description of its own speaks EnumLabels; an enum entry without a key would speak its English name. */
class EnumLabelsTest {
    private fun stringId(name: String): Int? = try {
        R.string::class.java.getField(name).getInt(null)
    } catch (_: NoSuchFieldException) {
        null
    }

    @Test fun everyIconAndAvatarHasAKey() {
        for (icon in PixelIcon.values()) {
            val id = EnumLabels.icon(icon.name)
            assertNotNull("no label key for icon ${icon.name}", id)
            assertEquals("the key of icon ${icon.name} is not eng_icon_${icon.name.lowercase()}", stringId("eng_icon_" + icon.name.lowercase()), id)
        }
        for (avatar in AvatarId.values()) {
            val id = EnumLabels.avatar(avatar.name)
            assertNotNull("no label key for avatar ${avatar.name}", id)
            assertEquals("the key of avatar ${avatar.name} is not eng_avatar_${avatar.name.lowercase()}", stringId("eng_avatar_" + avatar.name.lowercase()), id)
        }
    }

    @Test fun unknownNamesGiveNull() {
        assertNull(EnumLabels.icon("NOT_AN_ICON"))
        assertNull(EnumLabels.avatar("NOT_AN_AVATAR"))
        assertNull(EnumLabels.icon(""))
    }

    @Test fun theNameIsMatchedWithoutRegardToCase() {
        assertEquals(EnumLabels.icon("HEARTH"), EnumLabels.icon("hearth"))
        assertEquals(EnumLabels.avatar("NEWT"), EnumLabels.avatar("Newt"))
    }

    @Test fun everyLabelKeyExistsInTheEnglishFile() {
        val dir = ResXml.languageDirs()["en"]
        Assume.assumeTrue("res folders not reachable from the test's working directory", dir != null)
        val keys = ResXml.stringsFiles(dir!!).flatMap { f -> ResXml.parse(f).map { it.name } }.toSet()
        val missing = ArrayList<String>()
        for (icon in PixelIcon.values()) if (("eng_icon_" + icon.name.lowercase()) !in keys) missing += icon.name
        for (avatar in AvatarId.values()) if (("eng_avatar_" + avatar.name.lowercase()) !in keys) missing += avatar.name
        assertTrue("label keys missing from strings_engine.xml: $missing", missing.isEmpty())
        // no stray label key for an entry that no longer exists
        val icons = PixelIcon.values().map { it.name.lowercase() }.toSet()
        val avatars = AvatarId.values().map { it.name.lowercase() }.toSet()
        val stray = keys.filter { it.startsWith("eng_icon_") && it.removePrefix("eng_icon_") !in icons } +
            keys.filter { it.startsWith("eng_avatar_") && it.removePrefix("eng_avatar_") !in avatars }
        assertTrue("label keys for entries that do not exist: $stray", stray.isEmpty())
    }
}
