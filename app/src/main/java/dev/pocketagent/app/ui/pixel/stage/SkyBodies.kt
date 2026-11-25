package dev.pocketagent.app.ui.pixel.stage

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The shapes of the sun and the moon. The pure part lives here: the sixteen moon images and their colours. The
 * drawing onto a stage (sun disc and halo, moon halo, meteors) is written in the renderer part.
 */
object SkyBodies {
    /** The moon image is [MOON_SIZE] by [MOON_SIZE] pixels, its disc has a radius of 5.8. */
    const val MOON_SIZE = 13
    private const val MOON_RADIUS = 5.8
    const val SUN_SIZE = 9

    private val LIT = 0xFFFFF6DC.toInt()
    private val RED = 0xFF8A3A2A.toInt()
    private val EARTHSHINE = 0xFF2A2F52.toInt()
    private val cache = HashMap<Int, IntArray>()

    /**
     * The moon as [MOON_SIZE] x [MOON_SIZE] ARGB (0 outside the disc): the lit part in pale yellow (turned towards
     * red by [red], 0 to 1, in eighths), the dark part as a faint earthshine so the whole disc stays visible, and
     * phase 0 only that dark disc. [phase16] is 0 new, 4 first quarter, 8 full, 12 last quarter. Looking south from
     * the northern hemisphere the waxing moon is lit on the right; [northern] false (the view faces north) mirrors it.
     * Cached by phase, side and red bucket: do not change the returned array.
     */
    fun moonImage(phase16: Int, waxing: Boolean, northern: Boolean, red: Float): IntArray {
        val p = Math.floorMod(phase16, 16)
        val litOnRight = waxing == northern
        val bucket = (red.coerceIn(0f, 1f) * 8f).roundToInt()
        val key = p or (if (litOnRight) 16 else 0) or (bucket shl 5)
        synchronized(cache) {
            val hit = cache[key]
            if (hit != null) return hit
            val img = buildMoon(p, litOnRight, bucket / 8f)
            cache[key] = img
            return img
        }
    }

    private fun buildMoon(phase16: Int, litOnRight: Boolean, red: Float): IntArray {
        val out = IntArray(MOON_SIZE * MOON_SIZE)
        // rounded so that the quarter phases compare against exactly 0, not a rounding error of cos
        val threshold = Math.round(cos(2.0 * Math.PI * phase16 / 16.0) * 1e9) / 1e9
        val lit = Argb.lerp(LIT, RED, red)
        val dark = Argb.withAlpha(EARTHSHINE, 0.45f)
        val half = MOON_SIZE / 2
        for (dy in -half..half) {
            for (dx in -half..half) {
                val rr = MOON_RADIUS * MOON_RADIUS - dy.toDouble() * dy
                if (dx.toDouble() * dx + dy.toDouble() * dy > MOON_RADIUS * MOON_RADIUS || rr <= 0.0) continue
                val u = dx / sqrt(rr)
                val isLit = if (litOnRight) u > threshold else u < -threshold
                out[(dy + half) * MOON_SIZE + (dx + half)] = if (isLit) lit else dark
            }
        }
        return out
    }
}

/** What a tap on the sky found: [kind] 0 star, 1 planet, 2 moon, 3 sun; [name] the star's proper name; [planetId] 0 to 4 for a planet; [phase16] for the moon. */
class SkyBodyRef(val kind: Int, val name: String?, val planetId: Int, val phase16: Int)

private const val SKY_TAP_RADIUS_STAR = 2.5f
private const val SKY_TAP_RADIUS_PLANET = 3f
private const val SKY_TAP_RADIUS_SUN = 4.5f
private const val SKY_TAP_RADIUS_MOON = 6f
private const val SKY_TAP_NAMED_MAG = 3.0f
private const val SKY_TAP_GROUND_ROWS = 34

/**
 * The body under a tap on the scene's surface of [boxWidthPx] x [boxHeightPx] device pixels, or null. The device
 * point is mapped onto the stage with [StageGrid.forScene]; the sun and the moon are hit anywhere on their disc, a
 * planet or a named star (magnitude 3 or brighter, bright enough to be drawn) within about two and a half stage
 * pixels. Order: sun, moon, planets, then the nearest star. [bandRows] and [skyDy] are the stage's bottom band and
 * the sky's collapse shift (both 0 when the hero is not collapsed and has no band).
 */
fun skyBodyAt(
    frame: SkyFrame, table: StarTable, boxWidthPx: Float, boxHeightPx: Float, tapX: Float, tapY: Float,
    bandRows: Int = 0, skyDy: Int = 0,
): SkyBodyRef? {
    val grid = StageGrid.forScene(boxWidthPx, boxHeightPx) ?: return null
    val sx = (tapX - grid.offsetX) / grid.scale
    val sy = (tapY - grid.offsetY) / grid.scale
    val horizon = SkyProjection.horizonRow(grid.height - SKY_TAP_GROUND_ROWS - bandRows) + skyDy
    fun near(packed: Int, radius: Float): Boolean {
        if (packed == SkyProjection.NONE) return false
        val px = (packed and 0xFFFF) + 0.5f
        val py = (packed ushr 16) + 0.5f
        val dx = sx - px
        val dy = sy - py
        return dx * dx + dy * dy <= radius * radius
    }
    val facing = frame.facingAz
    val w = grid.width
    if (near(SkyProjection.project(frame.sun.azDeg.toDouble(), frame.sun.altDeg.toDouble(), facing, w, horizon), SKY_TAP_RADIUS_SUN)) {
        return SkyBodyRef(3, null, -1, 0)
    }
    if (frame.mode == SkyMode.FOLLOW &&
        near(SkyProjection.project(frame.moon.azDeg.toDouble(), frame.moon.altDeg.toDouble(), facing, w, horizon), SKY_TAP_RADIUS_MOON)
    ) {
        return SkyBodyRef(2, null, -1, frame.moonPhase16)
    }
    for (pl in frame.planets) {
        if (!Planets.visible(pl.altDeg, pl.mag, frame.depressionDeg)) continue
        if (near(SkyProjection.project(pl.azDeg.toDouble(), pl.altDeg.toDouble(), facing, w, horizon), SKY_TAP_RADIUS_PLANET)) {
            return SkyBodyRef(1, null, pl.id, 0)
        }
    }
    var best: SkyBodyRef? = null
    var bestDist = Float.MAX_VALUE
    val rd = DoubleArray(2)
    for (i in 0 until table.size) {
        if (table.mag[i] > SKY_TAP_NAMED_MAG) continue
        val name = table.nameOf(i) ?: continue
        if (StarLayer.alphaFor(table.mag[i], frame.depressionDeg, 1f) <= 0f) continue
        SkyClock.precess(table.raDeg[i].toDouble(), table.decDeg[i].toDouble(), frame.epochMs, rd)
        val aa = SkyClock.altAz(rd[0], rd[1], frame.lstDeg, frame.latDeg)
        val packed = SkyProjection.project(aa.azDeg, aa.altDeg, facing, w, horizon)
        if (!near(packed, SKY_TAP_RADIUS_STAR)) continue
        val dx = sx - ((packed and 0xFFFF) + 0.5f)
        val dy = sy - ((packed ushr 16) + 0.5f)
        val d = dx * dx + dy * dy
        if (d < bestDist) {
            bestDist = d
            best = SkyBodyRef(0, name, -1, 0)
        }
    }
    return best
}
