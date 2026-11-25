package dev.pocketagent.app.ui.pixel.pet

import dev.pocketagent.app.ui.pixel.PetArt
import dev.pocketagent.app.ui.pixel.Sprite

/**
 * Every animation Pêt can play. The numbers (frames, total milliseconds) are the sprite table of the Wave 1a
 * contract (section 3.10) and are the same as the ones in the art generator; a test in the art area compares them
 * with the shipped file. [fallback] is what is drawn when the sprite is missing or was rejected by the art review
 * (the chain always ends at [IDLE]). The first seven are the sprites of the old mascot file, which always exist.
 */
enum class PetAnim(
    val sprite: String,
    val frames: Int,
    val durationMs: Int,
    val loops: Boolean,
    val fallback: PetAnim?,
) {
    IDLE("pet_idle", 8, 2860, true, null),
    THINK("pet_thinking", 7, 1650, true, IDLE),
    TALK("pet_talking", 8, 880, true, IDLE),
    HAPPY("pet_happy", 9, 1300, false, IDLE),
    SLEEP("pet_sleeping", 6, 3500, true, IDLE),
    ERROR("pet_error", 6, 1380, true, IDLE),
    WAVE("pet_waving", 8, 1220, false, IDLE),
    BLINK("pet_blink", 4, 290, false, IDLE),
    TAIL("pet_tail", 6, 600, false, IDLE),
    WARM("pet_warm", 7, 1620, false, IDLE),
    YAWN("pet_yawn", 6, 1420, false, IDLE),
    STRETCH("pet_stretch", 6, 1370, false, IDLE),
    FIREFLY("pet_firefly", 8, 1180, false, IDLE),
    POKE("pet_poke", 8, 810, false, IDLE),
    READ("pet_read", 6, 1400, true, THINK),
    WRITE("pet_write", 7, 1080, false, IDLE),
    CARRY("pet_carry", 6, 1400, true, IDLE),
    STARTLED("pet_startled", 6, 560, false, IDLE),
    DANCE("pet_dance", 8, 850, true, HAPPY),
    LISTEN("pet_listen", 6, 1480, true, THINK),
    SHELTER("pet_shelter", 6, 960, true, IDLE),
    SNOW("pet_snow", 8, 1250, false, IDLE);

    /** The first seven (IDLE to WAVE): the sprites of the old mascot file, which always exist. */
    val isClassic: Boolean get() = ordinal < 7
}

/** Which animations can be shown right now. */
object PetSet {
    /** IDLE THINK TALK HAPPY SLEEP ERROR WAVE: always available. */
    val CLASSIC: Set<PetAnim> = PetAnim.entries.filter { it.isClassic }.toSet()

    /**
     * [CLASSIC] plus every new animation whose clip the art reader accepts (`PetArt.clip` is not null: the sprite
     * exists and has the frame count of the table). Never throws: a source that fails for a name counts as "missing".
     */
    fun availableIn(find: (String) -> Sprite?): Set<PetAnim> {
        val safeFind: (String) -> Sprite? = { name ->
            try {
                find(name)
            } catch (e: RuntimeException) {
                null
            }
        }
        val out = LinkedHashSet<PetAnim>(CLASSIC)
        for (a in PetAnim.entries) {
            if (a.isClassic) continue
            val ok = try {
                PetArt.clip(a.sprite, safeFind) != null
            } catch (e: RuntimeException) {
                false
            }
            if (ok) out.add(a)
        }
        return out
    }
}
