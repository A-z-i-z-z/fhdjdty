package dev.pocketagent.app.ui.pixel.stage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FireTiltTest {
    private class FakeApi(val hasSensor: Boolean = true) : TiltApi {
        var registers = 0
        var unregisters = 0
        var callback: ((Float, Long) -> Unit)? = null

        override fun register(onAccelX: (Float, Long) -> Unit): Boolean {
            registers++
            if (!hasSensor) return false
            callback = onAccelX
            return true
        }

        override fun unregister() {
            unregisters++
            callback = null
        }
    }

    @Test fun policyIsTheAndOfSix() {
        // bits: home on top, lifecycle started, full animation, classic, tilt enabled, has sensor
        var runs = 0
        for (bits in 0 until 64) {
            val homeOnTop = bits and 1 != 0
            val started = bits and 2 != 0
            val full = bits and 4 != 0
            val classic = bits and 8 != 0
            val tilt = bits and 16 != 0
            val sensor = bits and 32 != 0
            val expected = homeOnTop && started && full && !classic && tilt && sensor
            assertEquals("bits $bits", expected, FireTiltPolicy.shouldRun(homeOnTop, started, full, classic, tilt, sensor))
            if (expected) runs++
        }
        assertEquals("exactly one combination runs", 1, runs)
    }

    @Test fun registersOnceWhenRunning() {
        val api = FakeApi()
        val c = FireTiltController(api)
        assertFalse(c.registered)
        assertTrue("the first start is a change", c.setRunning(true))
        assertTrue(c.registered)
        assertEquals(1, api.registers)
        assertNotNull(api.callback)
    }

    @Test fun setRunningTwiceRegistersOnce() {
        val api = FakeApi()
        val c = FireTiltController(api)
        assertTrue(c.setRunning(true))
        assertFalse("nothing changes the second time", c.setRunning(true))
        assertEquals(1, api.registers)
        assertEquals(0, api.unregisters)
    }

    @Test fun unregistersWhenPolicyFalse() {
        val api = FakeApi()
        val c = FireTiltController(api)
        c.setRunning(true)
        assertTrue(c.setRunning(false))
        assertFalse(c.registered)
        assertEquals(1, api.unregisters)
        assertNull(api.callback)
        // asking again for what already is does nothing
        assertFalse(c.setRunning(false))
        assertEquals(1, api.unregisters)
        // and it can start again
        assertTrue(c.setRunning(true))
        assertEquals(2, api.registers)
    }

    @Test fun neverRegistersWhenNotAllowed() {
        val api = FakeApi()
        val c = FireTiltController(api)
        for (bits in 0 until 64) {
            val run = FireTiltPolicy.shouldRun(
                bits and 1 != 0, bits and 2 != 0, bits and 4 != 0, bits and 8 != 0, bits and 16 != 0, bits and 32 != 0,
            )
            if (!run) c.setRunning(false)
        }
        assertEquals(0, api.registers)
        assertEquals(0, api.unregisters)
        assertFalse(c.registered)
    }

    @Test fun aPhoneWithoutASensorNeverRegisters() {
        val api = FakeApi(hasSensor = false)
        val c = FireTiltController(api)
        assertFalse("no change when the platform refuses", c.setRunning(true))
        assertFalse(c.registered)
        assertEquals(0f, c.wind, 0f)
        // a later stop has nothing to unregister
        assertFalse(c.setRunning(false))
        assertEquals(0, api.unregisters)
    }

    @Test fun windUpdatesFromSamples() {
        val api = FakeApi()
        val c = FireTiltController(api)
        c.setRunning(true)
        val cb = api.callback!!
        // the first sample is taken as it is: tilted so the left edge is raised, the flame leans right
        cb(-4.5f, 1_000L)
        assertEquals(1f, c.wind, 1e-6f)
        // a sample the other way 250 ms later: half way (alpha 0.5)
        cb(4.5f, 1_250L)
        assertEquals(0f, c.wind, 1e-6f)
        cb(4.5f, 1_500L)
        assertEquals(-0.5f, c.wind, 1e-6f)
        // a flat phone is calm
        repeat(40) { cb(0f, 1_500L + (it + 1) * 250L) }
        assertEquals(0f, c.wind, 1e-3f)
    }

    @Test fun oddTimestampsStayInRange() {
        val api = FakeApi()
        val c = FireTiltController(api)
        c.setRunning(true)
        val cb = api.callback!!
        cb(-9f, 5_000L)
        cb(9f, 5_000L)          // the same instant: a step of at least 1 ms
        cb(9f, 1_000L)          // time going backwards
        cb(9f, 9_000_000L)      // a very long gap
        assertTrue(c.wind in -1f..1f)
    }

    @Test fun unregisterOnDispose() {
        val api = FakeApi()
        val c = FireTiltController(api)
        c.setRunning(true)
        val cb = api.callback!!
        cb(-4.5f, 100L)
        assertEquals(1f, c.wind, 1e-6f)
        // the driver's onDispose: stop, and the wind goes with it
        c.setRunning(false)
        assertEquals(1, api.unregisters)
        assertEquals(0f, c.wind, 0f)
        // a late sample that was already on its way is ignored
        cb(-4.5f, 200L)
        assertEquals(0f, c.wind, 0f)
        // starting again begins fresh, without a smoothing step from the old time
        c.setRunning(true)
        api.callback!!(-4.5f, 100_000L)
        assertEquals(1f, c.wind, 1e-6f)
    }

    @Test fun startingClearsAStaleWind() {
        val api = FakeApi()
        val c = FireTiltController(api)
        c.wind = 0.8f // a value left over from somewhere: a start must not carry it into the first frames
        assertTrue(c.setRunning(true))
        assertEquals(0f, c.wind, 0f)
    }
}
