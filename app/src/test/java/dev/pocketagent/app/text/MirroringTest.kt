package dev.pocketagent.app.text

import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.text.Mirroring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MirroringTest {
    @Test fun namesAreRealIcons() {
        val names = PixelIcon.values().map { it.name }.toSet()
        for (m in Mirroring.MIRRORED) assertTrue("$m is not a PixelIcon", m in names)
    }

    @Test fun onlyTheDirectionalOnesTurn() {
        assertEquals(setOf("BACK", "SEND", "PLAY", "SHARE"), Mirroring.MIRRORED)
        assertTrue(Mirroring.iconMirrors("BACK"))
        assertFalse(Mirroring.iconMirrors("HEARTH"))
        assertFalse(Mirroring.iconMirrors("back"))
        assertFalse(Mirroring.iconMirrors("NOT_AN_ICON"))
    }
}
