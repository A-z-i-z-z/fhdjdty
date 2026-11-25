package dev.pocketagent.app.ui.pixel.pet

import org.junit.Assert.assertEquals
import org.junit.Test

class PetQuipsTest {
    private val noon = PetWorld(sunAltitudeDeg = 40f, localMinutes = 12 * 60)
    private val morning = PetWorld(sunAltitudeDeg = 5f, localMinutes = 7 * 60)
    private val deepNight = PetWorld(sunAltitudeDeg = -30f, localMinutes = 2 * 60)

    @Test fun orderTable() {
        // overdrive first, whatever Pêt is doing and whatever the hour is
        assertEquals(PetQuip.OVERDRIVE, PetQuips.pick(deepNight, PetAnim.SLEEP, 7, true))
        // then what Pêt does
        assertEquals(PetQuip.SLEEPY, PetQuips.pick(noon, PetAnim.SLEEP, 1, false))
        assertEquals(PetQuip.WRITING, PetQuips.pick(noon, PetAnim.WRITE, 1, false))
        assertEquals(PetQuip.READING, PetQuips.pick(noon, PetAnim.READ, 1, false))
        assertEquals(PetQuip.RAIN, PetQuips.pick(noon, PetAnim.SHELTER, 1, false))
        assertEquals(PetQuip.SNOW, PetQuips.pick(noon, PetAnim.SNOW, 1, false))
        assertEquals(PetQuip.OOPS, PetQuips.pick(noon, PetAnim.STARTLED, 1, false))
        assertEquals(PetQuip.OOPS, PetQuips.pick(noon, PetAnim.ERROR, 2, false))
        // the pose beats the hour
        assertEquals(PetQuip.SLEEPY, PetQuips.pick(morning, PetAnim.SLEEP, 1, false))
        assertEquals(PetQuip.READING, PetQuips.pick(deepNight, PetAnim.READ, 1, false))
        // then the hour (odd taps only)
        assertEquals(PetQuip.MORNING, PetQuips.pick(morning, PetAnim.IDLE, 1, false))
        assertEquals(PetQuip.NIGHT, PetQuips.pick(deepNight, PetAnim.IDLE, 3, false))
        // else the classic lines
        assertEquals(PetQuip.CLASSIC_1, PetQuips.pick(noon, PetAnim.IDLE, 1, false))
    }

    @Test fun classicRotation() {
        val expected = listOf(PetQuip.CLASSIC_1, PetQuip.CLASSIC_2, PetQuip.CLASSIC_3, PetQuip.CLASSIC_4)
        for (count in 1..12) {
            assertEquals("tap $count", expected[(count - 1) % 4], PetQuips.pick(noon, PetAnim.IDLE, count, false))
        }
    }

    @Test fun oddOnlyContextual() {
        assertEquals(PetQuip.CLASSIC_2, PetQuips.pick(morning, PetAnim.IDLE, 2, false))
        assertEquals(PetQuip.CLASSIC_4, PetQuips.pick(deepNight, PetAnim.HAPPY, 4, false))
        assertEquals(PetQuip.MORNING, PetQuips.pick(morning, PetAnim.HAPPY, 5, false))
        assertEquals(PetQuip.NIGHT, PetQuips.pick(deepNight, PetAnim.WAVE, 9, false))
    }

    @Test fun thirteenQuips() {
        assertEquals(13, PetQuip.entries.size)
    }
}
