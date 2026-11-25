package dev.pocketagent.app.service

import dev.pocketagent.app.ui.pixel.stage.FireTransition
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FireDebugTest {
    @Before fun before() {
        FireDebug.clear()
        FireFeed.reset()
    }

    @After fun after() {
        FireDebug.clear()
        FireFeed.reset()
    }

    private fun parsed(text: String): FireDebugPin {
        val pin = FireDebugPin.parse(text)
        assertNotNull("'$text' should parse", pin)
        return pin!!
    }

    @Test fun parseGrammar() {
        val a = parsed("level:0.6,wind:-0.5")
        assertEquals(0.6f, a.level!!, 1e-6f)
        assertEquals(-0.5f, a.wind!!, 1e-6f)
        assertNull(a.classic)
        assertEquals(true, parsed("classic").classic)
        assertEquals(false, parsed("living").classic)
        assertEquals(1f, parsed("hold:1").hold!!, 0f)
        assertEquals(0f, parsed("hold:0").hold!!, 0f)
        assertEquals(FireTransition.Kind.LIGHT, parsed("light").start)
        assertEquals(FireTransition.Kind.DOUSE, parsed("douse").start)
        assertEquals(true, parsed("haze:on").haze)
        assertEquals(false, parsed("haze:off").haze)
        // spaces around items are fine, the last of a repeated item wins
        val b = parsed("living , level:0.2,level:0.9, classic")
        assertEquals(true, b.classic)
        assertEquals(0.9f, b.level!!, 1e-6f)
        // everything at once
        val all = parsed("level:1,wind:1,classic,hold:0.3,light@0.5,light,haze:on")
        assertNotNull(all.level)
        assertNotNull(all.wind)
        assertNotNull(all.classic)
        assertNotNull(all.hold)
        assertNotNull(all.transition)
        assertNotNull(all.start)
        assertNotNull(all.haze)
    }

    @Test fun frozenTransitionsUseAFractionOfTheirLength() {
        val l = parsed("light@0.5").transition!!
        assertEquals(FireTransition.Kind.LIGHT, l.kind)
        assertEquals(450L, l.frozenElapsedMs)
        assertEquals(450L, l.elapsed(123_456L))
        assertTrue(l.active(0L))
        val d = parsed("douse@0.25").transition!!
        assertEquals(FireTransition.Kind.DOUSE, d.kind)
        assertEquals(175L, d.frozenElapsedMs)
        assertEquals(0L, parsed("light@0").transition!!.frozenElapsedMs)
        // a transition frozen at the end is over
        assertFalse(parsed("douse@1").transition!!.active(0L))
    }

    @Test fun offIsAnEmptyPinNotAnError() {
        val off = parsed("off")
        assertTrue(off.isEmpty)
        assertEquals(FireDebugPin(), off)
        assertFalse(parsed("level:0").isEmpty)
    }

    @Test fun badValuesAreRejectedWhole() {
        val bad = listOf(
            "", "  ", "bogus", "level:", "level:x", "level:1.5", "level:-0.1", "wind:-1.5", "wind:2", "hold:1.01",
            "light@1.5", "douse@-0.1", "light@", "light@x", "haze:maybe", "haze:", "level:0.5,bogus",
            "bogus,level:0.5", "level:NaN", "wind:Infinity", "level:0.5,,wind:0.1", "Classic",
        )
        for (text in bad) assertNull("'$text' should be rejected", FireDebugPin.parse(text))
    }

    @Test fun applyIgnoredWithoutTestHooks() {
        FireDebug.apply(FireDebugPin.parse("level:0.9,classic")!!, enabled = false)
        assertTrue(FireDebug.pin.isEmpty)
        assertEquals(0f, FireFeed.level(), 0f)
    }

    @Test fun applyPinsTheFeedAndTheFire() {
        FireDebug.apply(FireDebugPin.parse("level:0.6,wind:-0.5,living")!!, enabled = true)
        assertEquals(0.6f, FireDebug.pin.level!!, 1e-6f)
        assertEquals(-0.5f, FireDebug.pin.wind!!, 1e-6f)
        assertEquals(false, FireDebug.pin.classic)
        assertEquals(0.6f, FireFeed.level(), 1e-6f)
        // a second apply replaces the first, also the feed's pin
        FireDebug.apply(FireDebugPin.parse("wind:0.2")!!, enabled = true)
        assertNull(FireDebug.pin.level)
        assertEquals(0f, FireFeed.level(), 0f)
        // null and off both clear
        FireDebug.apply(FireDebugPin.parse("level:1")!!, enabled = true)
        assertEquals(1f, FireFeed.level(), 0f)
        FireDebug.apply(null, enabled = true)
        assertTrue(FireDebug.pin.isEmpty)
        assertEquals(0f, FireFeed.level(), 0f)
        FireDebug.apply(FireDebugPin.parse("level:1")!!, enabled = true)
        FireDebug.apply(FireDebugPin.parse("off"), enabled = true)
        assertEquals(0f, FireFeed.level(), 0f)
    }

    @Test fun clearReleasesFeedPin() {
        FireDebug.apply(FireDebugPin.parse("level:0.7,hold:0.5")!!, enabled = true)
        assertEquals(0.7f, FireFeed.level(), 1e-6f)
        FireDebug.clear()
        assertTrue(FireDebug.pin.isEmpty)
        assertEquals(0f, FireFeed.level(), 0f)
    }

    @Test fun takeStartHandsOutEachStartOnce() {
        assertNull(FireDebug.takeStart())
        FireDebug.apply(FireDebugPin.parse("living,light,level:0.4")!!, enabled = true)
        assertEquals(FireTransition.Kind.LIGHT, FireDebug.takeStart())
        assertNull(FireDebug.takeStart())
        // the rest of the pin is still there
        assertEquals(0.4f, FireDebug.pin.level!!, 1e-6f)
        assertEquals(false, FireDebug.pin.classic)
        FireDebug.apply(FireDebugPin.parse("douse")!!, enabled = true)
        assertEquals(FireTransition.Kind.DOUSE, FireDebug.takeStart())
    }
}
