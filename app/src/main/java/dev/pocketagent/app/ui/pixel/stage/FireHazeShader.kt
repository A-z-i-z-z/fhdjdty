package dev.pocketagent.app.ui.pixel.stage

import kotlin.math.floor
import kotlin.math.sin

/**
 * The heat haze over the flame, as AGSL text (Android 13 and later builds a runtime shader from it; the
 * Android side lives elsewhere, this file is only the text and the maths around it).
 *
 * Uniforms: `content` the layer the shader sits on; `fire` the device pixels of the flame's centre column
 * (x) and of the hearth base line (y), in layer coordinates; `scale` device pixels per scene pixel; `time`
 * seconds; `strength` 0..1 from [FireHazeCurve.strength].
 *
 * The coordinate is snapped to scene pixels. Inside the box 28 scene pixels wide (14 either side of the
 * flame) and 35 high (from 55 to 20 rows above the base line) each row is sampled up to one whole scene
 * pixel to the side, by a sine of the row and the time; outside it the content is sampled where it is.
 */
object FireHazeShader {
    const val SOURCE: String = """
uniform shader content;
uniform float2 fire;
uniform float scale;
uniform float time;
uniform float strength;

half4 main(float2 p) {
    float2 q = floor(p / scale) * scale;
    float inX = step(abs(q.x - fire.x), 14.0 * scale);
    float inY = step(fire.y - 55.0 * scale, q.y) * step(q.y, fire.y - 20.0 * scale);
    float row = floor(q.y / scale);
    float w = floor(sin(row * 0.9 + time * 5.0) * strength + 0.5);
    float2 shifted = q + float2(w * scale, 0.0) + scale * 0.5;
    return content.eval(mix(p, shifted, inX * inY));
}
"""
}

/** How strong the haze is for a work level, and when it switches on and off (with a gap, so it does not flicker). */
object FireHazeCurve {
    const val ON_AT = 0.5f
    const val OFF_AT = 0.35f

    private const val EDGE0 = 0.35f
    private const val EDGE1 = 0.9f

    /** smoothstep(0.35, 0.9, level): 0 at 0.35 and under, 1 at 0.9 and over. */
    fun strength(level: Float): Float {
        if (level.isNaN()) return 0f
        val t = ((level - EDGE0) / (EDGE1 - EDGE0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** The next on or off: on from [ON_AT] up, off from [OFF_AT] down, unchanged in between. */
    fun next(active: Boolean, level: Float): Boolean {
        if (level.isNaN()) return false
        return if (active) level > OFF_AT else level >= ON_AT
    }

    /** What the shader does to one row, in whole scene pixels (-1, 0 or 1): the same formula, for tests. */
    fun shiftCells(row: Int, timeSeconds: Float, strength: Float): Int =
        floor(sin(row * 0.9f + timeSeconds * 5f) * strength + 0.5f).toInt()
}
