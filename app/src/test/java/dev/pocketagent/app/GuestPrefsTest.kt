package dev.pocketagent.app

import android.content.SharedPreferences
import dev.pocketagent.app.runtime.GuestPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * The two guest switches (web lookups, practice model) and the rules that keep the practice flag from being left on by accident.
 * GuestPrefs.practiceActive is process-global: every test resets it, so no order can leak it.
 */
class GuestPrefsTest {
    @Before fun reset() { GuestPrefs.practiceActive = false }
    @After fun resetAgain() { GuestPrefs.practiceActive = false }

    /** A map-backed SharedPreferences; a value of the wrong type throws ClassCastException on read, like Android's. */
    private class FakePrefs {
        val map = LinkedHashMap<String, Any?>()
        val sp: SharedPreferences = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, m, args ->
            when (m.name) {
                "getBoolean" -> map[args[0] as String]?.let { it as Boolean } ?: (args[1] as Boolean)
                "getLong" -> map[args[0] as String]?.let { it as Long } ?: (args[1] as Long)
                "contains" -> map.containsKey(args[0] as String)
                "edit" -> editor()
                else -> null
            }
        } as SharedPreferences

        private fun editor(): SharedPreferences.Editor {
            val pending = LinkedHashMap<String, Any?>(); val removed = LinkedHashSet<String>()
            lateinit var ed: SharedPreferences.Editor
            ed = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, m, args ->
                when (m.name) {
                    "putBoolean", "putLong", "putString" -> { pending[args[0] as String] = args[1]; ed }
                    "remove" -> { removed += args[0] as String; pending.remove(args[0] as String); ed }
                    "apply", "commit" -> { removed.forEach { map.remove(it) }; map.putAll(pending); if (m.name == "commit") true else null }
                    else -> ed
                }
            } as SharedPreferences.Editor
            return ed
        }
    }

    private val hour = 3_600_000L
    private val minute = 60_000L

    @Test fun defaultsAreKeylessOnPracticeOff() {
        val f = FakePrefs()
        val g = GuestPrefs(f.sp, isTwin = false)
        assertTrue(g.webKeyless.value)
        assertFalse(g.practiceModel.value)
        assertFalse(GuestPrefs.practiceActive)
        assertNull(g.practiceMinutesLeft())
    }

    @Test fun valuesPersistAndFlowsUpdate() {
        val f = FakePrefs()
        val g = GuestPrefs(f.sp, isTwin = true)
        g.setWebKeyless(false)
        assertFalse(g.webKeyless.value); assertEquals(false, f.map["web_keyless"])
        g.setPracticeModel(true)
        assertTrue(g.practiceModel.value); assertTrue(GuestPrefs.practiceActive)
        assertEquals(true, f.map["practice"]); assertTrue(f.map["practice_since"] is Long)
        // a new instance (a new process) reads the web switch back; on the twin the practice flag stays on
        val g2 = GuestPrefs(f.sp, isTwin = true)
        assertFalse(g2.webKeyless.value); assertTrue(g2.practiceModel.value); assertTrue(GuestPrefs.practiceActive)
        g2.setPracticeModel(false)
        assertFalse(g2.practiceModel.value); assertFalse(GuestPrefs.practiceActive)
        assertEquals(false, f.map["practice"]); assertFalse(f.map.containsKey("practice_since"))
        // switching the web lookups never touches the practice flag and the other way round
        g2.setWebKeyless(true); assertFalse(g2.practiceModel.value)
    }

    @Test fun wrongTypeFallsBackToDefault() {
        val f = FakePrefs()
        f.map["web_keyless"] = "yes"          // a hand-edited file
        f.map["practice"] = 1
        f.map["practice_since"] = "long ago"
        val g = GuestPrefs(f.sp, isTwin = true)
        assertTrue(g.webKeyless.value); assertFalse(g.practiceModel.value)
        assertNull(g.practiceMinutesLeft())
    }

    @Test fun anotherBuildResetsPracticeAtEveryColdStart() {
        val f = FakePrefs()
        f.map["practice"] = true; f.map["practice_since"] = 1_000L
        val g = GuestPrefs(f.sp, isTwin = false)        // the constructor is the cold process start
        assertFalse(g.practiceModel.value); assertFalse(GuestPrefs.practiceActive)
        assertEquals(false, f.map["practice"]); assertFalse(f.map.containsKey("practice_since"))
        // the web switch survives the reset
        f.map["web_keyless"] = false
        assertFalse(GuestPrefs(f.sp, isTwin = false).webKeyless.value)
    }

    @Test fun twinKeepsPracticeAtColdStart() {
        val f = FakePrefs()
        f.map["practice"] = true; f.map["practice_since"] = 1_000L
        val g = GuestPrefs(f.sp, isTwin = true)
        assertTrue(g.practiceModel.value); assertTrue(GuestPrefs.practiceActive)
        // never expires on the twin, however old
        g.enforceExpiry(now = 1_000L + 100 * hour, isTwin = true)
        assertTrue(g.practiceModel.value)
        assertNull(g.practiceMinutesLeft(now = 1_000L + hour))
    }

    @Test fun expiryIsPureAndExact() {
        assertFalse(GuestPrefs.expired(sinceMs = 0, nowMs = GuestPrefs.PRACTICE_MAX_MS, isTwin = false))
        assertTrue(GuestPrefs.expired(sinceMs = 0, nowMs = GuestPrefs.PRACTICE_MAX_MS + 1, isTwin = false))
        assertFalse(GuestPrefs.expired(sinceMs = 0, nowMs = 1000 * hour, isTwin = true))
        assertEquals(7_200_000L, GuestPrefs.PRACTICE_MAX_MS)
    }

    @Test fun enforceExpiryTurnsPracticeOffAfterTwoHours() {
        val f = FakePrefs()
        val g = GuestPrefs(f.sp, isTwin = false)
        g.setPracticeModel(true)
        val since = f.map["practice_since"] as Long
        g.enforceExpiry(now = since + 119 * minute, isTwin = false)
        assertTrue(g.practiceModel.value); assertTrue(GuestPrefs.practiceActive)
        g.enforceExpiry(now = since + 2 * hour + 1, isTwin = false)
        assertFalse(g.practiceModel.value); assertFalse(GuestPrefs.practiceActive)
        assertEquals(false, f.map["practice"]); assertFalse(f.map.containsKey("practice_since"))
        // already off: nothing happens
        g.enforceExpiry(now = since + 10 * hour, isTwin = false)
        assertFalse(g.practiceModel.value)
    }

    @Test fun aFlagWithoutATimeStampCountsAsExpiredOutsideTheTwin() {
        val f = FakePrefs()
        val g = GuestPrefs(f.sp, isTwin = true)             // loads the flag on
        f.map["practice"] = true
        g.setPracticeModel(true)
        f.map.remove("practice_since")
        g.enforceExpiry(now = 5 * hour, isTwin = false)
        assertFalse(g.practiceModel.value)
    }

    @Test fun minutesLeftCountsDownAndNeverLeavesTheRange() {
        val f = FakePrefs()
        val g = GuestPrefs(f.sp, isTwin = false)
        assertNull(g.practiceMinutesLeft())
        g.setPracticeModel(true)
        val since = f.map["practice_since"] as Long
        assertEquals(120, g.practiceMinutesLeft(now = since))
        assertEquals(120, g.practiceMinutesLeft(now = since - 5 * minute))          // a clock that went back
        assertEquals(60, g.practiceMinutesLeft(now = since + hour))
        assertEquals(1, g.practiceMinutesLeft(now = since + 119 * minute + 30_000))  // rounded up
        assertEquals(0, g.practiceMinutesLeft(now = since + 3 * hour))
    }
}
