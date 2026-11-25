package dev.pocketagent.app.ui.pixel

// TEMPORARY STAND-IN written by INTEGRATE of slice A so that slice A compiles on its own. The real file belongs to
// ART-PET (slice B, docs/waves/w1/ART-PET.md 3.6) and replaces this one when the slices are merged.

enum class PetClipKind { SHOT, LOOP }

class PetClipSpec(
    val name: String,
    val kind: PetClipKind,
    val frames: Int,
    val intro: Int,
    val outro: Int,
    val totalMs: Int,
    val airborne: Set<Int>,
)

class PetClip(val sprite: Sprite, val spec: PetClipSpec) {
    /** First frame of the repeating part. */
    val loopStart: Int get() = spec.intro

    /** Exclusive end of the repeating part. */
    val loopEnd: Int get() = spec.frames - spec.outro

    /** Frame showing [msSinceStart] into a one-shot; null when the clip is over (at or past its total). */
    fun frameAtShot(msSinceStart: Long): Int? {
        if (msSinceStart < 0L) return 0
        var t = msSinceStart
        for (i in sprite.frames.indices) {
            val d = sprite.frames[i].durationMs
            if (t < d) return i
            t -= d
        }
        return null
    }

    /** Intro once, then the loop part for ever. */
    fun frameAtLoop(msSinceStart: Long): Int {
        val n = sprite.frames.size
        val a = loopStart.coerceIn(0, n - 1)
        val b = loopEnd.coerceIn(a + 1, n)
        var t = if (msSinceStart < 0L) 0L else msSinceStart
        for (i in 0 until a) {
            val d = sprite.frames[i].durationMs.toLong()
            if (t < d) return i
            t -= d
        }
        var loopMs = 0L
        for (i in a until b) loopMs += sprite.frames[i].durationMs
        if (loopMs <= 0L) return a
        t %= loopMs
        for (i in a until b) {
            val d = sprite.frames[i].durationMs.toLong()
            if (t < d) return i
            t -= d
        }
        return b - 1
    }

    /** Frame of the outro [msSinceOutro] into it; null when over. */
    fun frameAtOutro(msSinceOutro: Long): Int? {
        val n = sprite.frames.size
        var t = if (msSinceOutro < 0L) 0L else msSinceOutro
        for (i in loopEnd until n) {
            val d = sprite.frames[i].durationMs.toLong()
            if (t < d) return i
            t -= d
        }
        return null
    }
}

object PetArt {
    const val W = 32
    const val H = 32
    const val GROUND_ROW = 31
    const val SHADOW = "pet_shadow"
    const val SHADOW_W = 48
    const val SHADOW_H = 5
    const val SHADOW_DX = -18
    const val SHADOW_DY = 29
    const val HEAD = "pet_head"
    const val SMALL = "pet_small"
    const val PEEK = "pet_peek"
    const val HEAD_NEUTRAL = 0
    const val HEAD_BLINK = 1
    const val HEAD_HAPPY = 2
    const val HEAD_WORRIED = 3

    private fun shot(name: String, frames: Int, totalMs: Int, airborne: Set<Int> = emptySet()) =
        PetClipSpec(name, PetClipKind.SHOT, frames, 0, 0, totalMs, airborne)

    private fun loop(name: String, frames: Int, totalMs: Int) =
        PetClipSpec(name, PetClipKind.LOOP, frames, 1, 1, totalMs, emptySet())

    val SPECS: List<PetClipSpec> = listOf(
        shot("pet_blink", 4, 290),
        shot("pet_tail", 6, 600),
        shot("pet_warm", 7, 1620),
        shot("pet_yawn", 6, 1420),
        shot("pet_stretch", 6, 1370),
        shot("pet_firefly", 8, 1180),
        shot("pet_poke", 8, 810),
        loop("pet_read", 6, 1400),
        shot("pet_write", 7, 1080),
        loop("pet_carry", 6, 1400),
        shot("pet_startled", 6, 560, setOf(1, 2, 3)),
        shot("pet_dance", 8, 850, setOf(1, 2, 4, 5, 6)),
        loop("pet_listen", 6, 1480),
        loop("pet_shelter", 6, 960),
        shot("pet_snow", 8, 1250),
    )

    val NAMES: List<String> = SPECS.map { it.name }

    fun spec(name: String): PetClipSpec? = SPECS.firstOrNull { it.name == name }

    /** Null when the sprite is missing or its frame count differs from the spec. */
    fun clip(name: String, find: (String) -> Sprite?): PetClip? {
        val spec = spec(name) ?: return null
        val sprite = find(name) ?: return null
        if (sprite.frames.size != spec.frames) return null
        return PetClip(sprite, spec)
    }

    fun shadowFrame(intensity: Float): Int = when {
        intensity < 0.15f -> 0
        intensity < 0.45f -> 1
        intensity < 0.8f -> 2
        else -> 3
    }
}
