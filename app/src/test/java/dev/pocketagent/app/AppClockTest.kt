package dev.pocketagent.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset

class AppClockTest {
    @After fun release() {
        AppClock.unpin()
    }

    @Test fun pinFreezesAndUnpinReleases() {
        assertNull(AppClock.pinned.value)
        val pin = 1_800_000_000_000L
        AppClock.pin(pin)
        assertEquals(pin, AppClock.pinned.value)
        assertEquals(pin, AppClock.nowMillis())
        assertEquals(pin, AppClock.nowMillis())
        assertEquals(pin, AppClock.now(ZoneOffset.UTC).toInstant().toEpochMilli())
        AppClock.unpin()
        assertNull(AppClock.pinned.value)
        val real = System.currentTimeMillis()
        assertTrue(Math.abs(AppClock.nowMillis() - real) < 1_000L)
    }

    @Test fun parseUsesZone() {
        val utc = ZoneId.of("UTC")
        val plusThree = ZoneOffset.ofHours(3)
        val a = AppClock.parse("2027-03-21T20:30", utc)
        val b = AppClock.parse("2027-03-21T20:30", plusThree)
        assertNotNull(a)
        assertNotNull(b)
        assertEquals(3 * 3_600_000L, a!! - b!!)
        // An explicit offset ignores the zone.
        assertEquals(AppClock.parse("2027-03-21T20:30:00Z", plusThree), a)
        assertEquals(AppClock.parse("2027-03-21T23:30:00+03:00", utc), a)
        // Seconds are read.
        assertEquals(a + 45_000L, AppClock.parse("2027-03-21T20:30:45", utc))
    }

    @Test fun unreadableTextIsNull() {
        assertNull(AppClock.parse("", ZoneOffset.UTC))
        assertNull(AppClock.parse("   ", ZoneOffset.UTC))
        assertNull(AppClock.parse("yesterday", ZoneOffset.UTC))
        assertNull(AppClock.parse("2027-13-45T99:99", ZoneOffset.UTC))
    }
}
