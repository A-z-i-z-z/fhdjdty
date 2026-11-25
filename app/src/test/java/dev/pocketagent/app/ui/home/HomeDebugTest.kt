package dev.pocketagent.app.ui.home

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class HomeDebugTest {
    @Before fun before() {
        HomeDebug.pinWoodpile(null, enabled = true)
    }

    @After fun after() {
        HomeDebug.pinWoodpile(null, enabled = true)
    }

    @Test fun pinAndClear() {
        assertNull(HomeDebug.woodpile.value)
        HomeDebug.pinWoodpile(3, enabled = true)
        assertEquals(3, HomeDebug.woodpile.value)
        HomeDebug.pinWoodpile(0, enabled = true)
        assertEquals(0, HomeDebug.woodpile.value)
        HomeDebug.pinWoodpile(null, enabled = true)
        assertNull(HomeDebug.woodpile.value)
    }

    @Test fun levelIsClampedToTheSpriteFrames() {
        HomeDebug.pinWoodpile(9, enabled = true)
        assertEquals(5, HomeDebug.woodpile.value)
        HomeDebug.pinWoodpile(-4, enabled = true)
        assertEquals(0, HomeDebug.woodpile.value)
    }

    @Test fun inertWithoutTestHooks() {
        HomeDebug.pinWoodpile(2, enabled = false)
        assertNull(HomeDebug.woodpile.value)
        HomeDebug.pinWoodpile(4, enabled = true)
        HomeDebug.pinWoodpile(null, enabled = false)   // a clear without hooks does nothing either
        assertEquals(4, HomeDebug.woodpile.value)
    }
}
