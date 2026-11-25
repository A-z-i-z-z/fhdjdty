package dev.pocketagent.app.ui.home

import dev.pocketagent.app.ui.theme.AnimLevel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HomeEntranceTest {
    @Before fun before() {
        HomeEntrance.reset()
    }

    @After fun after() {
        HomeEntrance.reset()
    }

    @Test fun shouldPlayTable() {
        // not played, full animations, no pinned clock: plays
        assertTrue(HomeEntrance.shouldPlay(false, AnimLevel.FULL, clockPinned = false, testHooks = false))
        assertTrue(HomeEntrance.shouldPlay(false, AnimLevel.FULL, clockPinned = false, testHooks = true))
        // reduced animations still play the (short) entrance
        assertTrue(HomeEntrance.shouldPlay(false, AnimLevel.REDUCED, clockPinned = false, testHooks = false))
        // once per process
        assertFalse(HomeEntrance.shouldPlay(true, AnimLevel.FULL, clockPinned = false, testHooks = false))
        // animations off
        assertFalse(HomeEntrance.shouldPlay(false, AnimLevel.OFF, clockPinned = false, testHooks = false))
        // a pinned clock skips it only in a build with test hooks
        assertFalse(HomeEntrance.shouldPlay(false, AnimLevel.FULL, clockPinned = true, testHooks = true))
        assertTrue(HomeEntrance.shouldPlay(false, AnimLevel.FULL, clockPinned = true, testHooks = false))
    }

    @Test fun markAndReset() {
        assertFalse(HomeEntrance.played)
        HomeEntrance.markPlayed()
        assertTrue(HomeEntrance.played)
        HomeEntrance.markPlayed()
        assertTrue(HomeEntrance.played)
        HomeEntrance.reset()
        assertFalse(HomeEntrance.played)
    }

    @Test fun staggerBudget() {
        val fastMs = 150   // the FAST duration of the motion tokens
        assertTrue(HomeEntrance.MAX_STAGGERED * HomeEntrance.STAGGER_MS + fastMs <= 400)
        assertEquals(900, HomeEntrance.REVEAL_MS)
        assertEquals(36, HomeEntrance.STAGGER_MS)
        assertEquals(6, HomeEntrance.MAX_STAGGERED)
    }
}
