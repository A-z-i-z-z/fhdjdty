package dev.pocketagent.app.ui.pixel.stage

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Where everything of the home scene sits, in logical stage rows and columns. This is the one source of
 * geometry (ruling R5 of the Wave 1a contract): the fire, Pet and the weather build thin views from it and
 * nobody computes `(width * 0.6f).toInt()` or `height - 34` anywhere else.
 *
 * [height] is the whole stage, the band included; [bandRows] is the ground-only band below the hearth.
 * With `bandRows = 0`, `collapse = 0` and `reveal = 1` every field equals the constant of the old picture
 * (see SceneGeometryTest), so the classic output is unchanged.
 *
 * [landDy], [ridgeDy] and [skyDy] are the rows that the land, the ridges and the sky are moved down by the
 * collapse of the hero and by its entrance. The sky gradient itself is never moved by them.
 *
 * Pure Kotlin: no Android types, nothing is allocated except the one instance.
 */
class SceneGeometry private constructor(
    val width: Int,
    val height: Int,
    val bandRows: Int,
    val groundTop: Int,
    val farRidgeTop: Int,
    val nearRidgeTop: Int,
    val foreTop: Int,
    val hearthX: Int,
    val hearthBase: Int,
    val artX: Int,
    val horizonRow: Int,
    val landDy: Int,
    val ridgeDy: Int,
    val skyDy: Int,
    val woodpileX: Int,
    val woodpileY: Int,
    val showProps: Boolean,
) {
    override fun toString(): String =
        "SceneGeometry(${width}x$height band=$bandRows ground=$groundTop far=$farRidgeTop near=$nearRidgeTop " +
            "fore=$foreTop hearth=$hearthX/$hearthBase artX=$artX horizon=$horizonRow " +
            "dy=$landDy/$ridgeDy/$skyDy wood=$woodpileX/$woodpileY props=$showProps)"

    companion object {
        /** Height of the ground art (the sprite `scene_ground`), in rows. */
        const val GROUND_H = 34

        /** Rows the fire sprite and its footprint need above the hearth base. */
        const val FIRE_FOOTPRINT_ROWS = 38

        /** Rows the land is lifted from at reveal 0 (the entrance slides it up by this much). */
        const val ENTER_ROWS = 48

        /** Width of the scene art (far, near, ground, fore sprites); the art is centred on the stage. */
        private const val ART_W = 176

        /**
         * [collapse] 0..1 (0 = the hero is open, 1 = collapsed to the band) and [reveal] 0..1 (1 = the
         * entrance is over) are clamped; a NaN counts as the neutral value (0 and 1).
         */
        fun of(width: Int, height: Int, bandRows: Int = 0, collapse: Float = 0f, reveal: Float = 1f): SceneGeometry {
            val band = max(0, bandRows)
            val q = if (collapse.isNaN()) 0f else collapse.coerceIn(0f, 1f)
            val rv = if (reveal.isNaN()) 1f else reveal.coerceIn(0f, 1f)

            val g0 = height - GROUND_H - band
            val cDy = (q * band).roundToInt()
            val ridgeC = max((0.6f * cDy).roundToInt(), cDy - 10)
            val enter = ((1f - smoothstep(0f, 0.8f, rv)) * ENTER_ROWS).roundToInt()
            val landDy = cDy + enter
            val ridgeDy = ridgeC + enter
            val skyDy = (0.35f * cDy).roundToInt()

            val hearthX = (width * 0.6f).toInt()
            val hearthBase = height - 3 - band + landDy
            return SceneGeometry(
                width = width,
                height = height,
                bandRows = band,
                groundTop = g0 + landDy,
                farRidgeTop = g0 - 38 + ridgeDy,
                nearRidgeTop = g0 - 14 + ridgeDy,
                foreTop = height - 11 - band + landDy,
                hearthX = hearthX,
                hearthBase = hearthBase,
                artX = (width - ART_W) / 2,
                horizonRow = g0 - 24 + skyDy,
                landDy = landDy,
                ridgeDy = ridgeDy,
                skyDy = skyDy,
                woodpileX = hearthX + 18,
                woodpileY = hearthBase - 15,
                showProps = q < 0.75f,
            )
        }

        private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
            val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
    }
}

/**
 * The fire as the renderer sees it: three hooks called in this order from [SceneRenderer]. The class that
 * implements it (FireLayerImpl, owned by the fire area) also holds today's classic fire code, moved verbatim.
 * Every hook draws into [stage] only, reads [state] (hearth, palette, progress, boost, celebration, the
 * fire params and the wind of the weather) and must not allocate in proportion to pixels or particles.
 */
interface FireLayer {
    /** Step 9: the glow on the ground and the sky around the fire. */
    fun glow(stage: PixelStage, geo: SceneGeometry, state: SceneState, timeMs: Long, quality: SceneQuality)

    /** Step 11: the flame sprite, its living edge, the wind lean and the hold ring. */
    fun flame(stage: PixelStage, geo: SceneGeometry, state: SceneState, timeMs: Long, quality: SceneQuality)

    /** Step 11c, after the scene's own smoke: the sparks (in the classic fire, today's embers). */
    fun sparks(stage: PixelStage, geo: SceneGeometry, state: SceneState, timeMs: Long, quality: SceneQuality)
}
