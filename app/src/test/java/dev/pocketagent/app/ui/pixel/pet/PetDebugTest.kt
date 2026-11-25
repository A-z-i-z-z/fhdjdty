package dev.pocketagent.app.ui.pixel.pet

import dev.pocketagent.app.BuildConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PetDebugTest {
    @After fun clear() {
        PetDebug.pin(null)
    }

    @Test fun parseForms() {
        assertEquals(PetPin(PetAnim.READ, null, false), PetDebug.parse("read"))
        assertEquals(PetPin(PetAnim.READ, 3, false), PetDebug.parse("read:3"))
        assertEquals(PetPin(PetAnim.IDLE, 7, false), PetDebug.parse("idle:7"))
        assertEquals(PetPin(null, null, true), PetDebug.parse("classic"))
        assertEquals(PetPin(PetAnim.SHELTER, 0, false), PetDebug.parse(" shelter:0 "))
    }

    @Test fun everyAnimHasItsName() {
        for (a in PetAnim.entries) {
            val name = a.name.map { if (it in 'A'..'Z') it + ('a' - 'A') else it }.joinToString("")
            assertEquals(PetPin(a, null, false), PetDebug.parse(name))
            assertEquals(PetPin(a, a.frames - 1, false), PetDebug.parse("$name:${a.frames - 1}"))
            assertNull("$name:${a.frames}", PetDebug.parse("$name:${a.frames}"))
        }
    }

    @Test fun badValuesAreRejected() {
        val bad = listOf(
            "off", "", " ", "PHONE", "phone", "READ", "Read", "blink:9", "blink:4", "read:", "read:-1", "read:x", "read:+2",
            "read:1:2", ":3", "classic:1", "Classic",
        )
        for (text in bad) assertNull("'$text'", PetDebug.parse(text))
    }

    @Test fun pinGatedByTestHooks() {
        val p = PetPin(PetAnim.BLINK, 1, false)
        PetDebug.pin(p)
        if (BuildConfig.TEST_HOOKS) {
            assertEquals(p, PetDebug.pinned.value)
            PetDebug.pin(null)
            assertNull(PetDebug.pinned.value)
        } else {
            assertNull(PetDebug.pinned.value)
        }
    }
}
