package dev.pocketagent.app.ui.pixel.stage

import kotlin.math.abs
import kotlin.math.floor

/**
 * Where a star, a planet, the sun or the moon lands on the stage: the strip above the ridges is a panorama
 * of 220 degrees across (centred on the south in the northern hemisphere, on the north in the southern) and 80
 * degrees up to the top row. Not a conformal map: about 0.70 px per degree across and about 0.6 px per degree up
 * on a 155 px stage, so constellations look squashed near the top. [horizonRow] is the mean line of the far ridge
 * (the ground's top row minus 24); what lies below it is hidden by the ridges drawn later.
 */
object SkyProjection {
    const val FOV_AZ_DEG = 220f
    const val ALT_TOP_DEG = 80f
    const val TOP_ROW = 3
    const val HORIZON_BELOW_GROUND = 24
    const val NONE = -1

    fun horizonRow(groundTop: Int): Int = groundTop - HORIZON_BELOW_GROUND

    /** 180 (south) in the northern hemisphere and on the equator, 0 (north) in the southern. */
    fun facingAz(latDeg: Double): Float = if (latDeg >= 0.0) 180f else 0f

    /** Azimuth difference wrapped to -180 to 180. */
    private fun wrap180(x: Double): Double = x - 360.0 * floor((x + 180.0) / 360.0)

    /**
     * The stage pixel of the direction ([azDeg], [altDeg]): `(y shl 16) or x`, or [NONE] when it is outside the strip
     * (more than 110 degrees from the facing direction, below -1 or above 80 degrees, or the stage is too small).
     * Read it back with `packed ushr 16` and `packed and 0xFFFF`.
     */
    fun project(azDeg: Double, altDeg: Double, facingAz: Float, width: Int, horizonRow: Int): Int {
        if (horizonRow < 12 || width < 2) return NONE
        val d = wrap180(azDeg - facingAz)
        if (abs(d) > 110.0 || altDeg < -1.0 || altDeg > 80.0) return NONE
        val x = floor((width - 1) / 2f + d.toFloat() * (width - 1) / 220f + 0.5f).toInt()
        val y = floor(horizonRow - altDeg.toFloat() * (horizonRow - TOP_ROW) / ALT_TOP_DEG + 0.5f).toInt()
        if (x < 0 || x >= width || y < 0 || y > horizonRow + 1) return NONE
        return (y shl 16) or x
    }
}
