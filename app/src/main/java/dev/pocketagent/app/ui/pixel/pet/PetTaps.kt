package dev.pocketagent.app.ui.pixel.pet

/**
 * Counts taps on Pêt. Seven taps in a chain (each at most [chainMs] after the one before) are the Newroz
 * overdrive. The count never resets, so the odd and even taps keep alternating as they always did.
 */
class PetTaps(private val chainMs: Long = DEFAULT_CHAIN_MS) {
    /** [count] taps so far (this one included), [streak] taps in the current chain (0 right after an overdrive). */
    class Count(val count: Int, val streak: Int, val overdrive: Boolean)

    private var count = 0
    private var streak = 0
    private var last = 0L
    private var hasLast = false

    fun tap(nowMs: Long): Count {
        streak = if (hasLast && nowMs - last <= chainMs) streak + 1 else 1
        last = nowMs
        hasLast = true
        count++
        val overdrive = streak >= OVERDRIVE_TAPS
        if (overdrive) streak = 0
        return Count(count, streak, overdrive)
    }

    companion object {
        const val DEFAULT_CHAIN_MS = 1200L
        const val OVERDRIVE_TAPS = 7
    }
}

/** The bubble texts Pêt can say when tapped; the app turns them into string resources. */
enum class PetQuip { CLASSIC_1, CLASSIC_2, CLASSIC_3, CLASSIC_4, OVERDRIVE, SLEEPY, NIGHT, MORNING, RAIN, SNOW, READING, WRITING, OOPS }

object PetQuips {
    private val CLASSIC = arrayOf(PetQuip.CLASSIC_1, PetQuip.CLASSIC_2, PetQuip.CLASSIC_3, PetQuip.CLASSIC_4)

    /**
     * Order: overdrive; then what Pêt is doing ([anim]); then, for an odd [count] only, the time of day (morning,
     * deep night); else the four classic lines in turn (`(count - 1) % 4`).
     */
    fun pick(world: PetWorld, anim: PetAnim, count: Int, overdrive: Boolean): PetQuip {
        if (overdrive) return PetQuip.OVERDRIVE
        when (anim) {
            PetAnim.SLEEP -> return PetQuip.SLEEPY
            PetAnim.WRITE -> return PetQuip.WRITING
            PetAnim.READ -> return PetQuip.READING
            PetAnim.SHELTER -> return PetQuip.RAIN
            PetAnim.SNOW -> return PetQuip.SNOW
            PetAnim.STARTLED, PetAnim.ERROR -> return PetQuip.OOPS
            else -> {}
        }
        if (count % 2 != 0) {
            if (world.morning) return PetQuip.MORNING
            if (world.deepNight) return PetQuip.NIGHT
        }
        return CLASSIC[Math.floorMod(count - 1, CLASSIC.size)]
    }
}

/** What a tap on Pêt asks the screen to do besides showing the bubble. */
enum class PetTapAction { NONE, OPEN_WAITING_RESULT }

class PetTapResult(val quip: PetQuip, val overdrive: Boolean, val action: PetTapAction)
