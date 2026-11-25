package dev.pocketagent.app.ui.pixel.stage

/**
 * The fire's place on the stage: a thin view of [SceneGeometry] (ruling R5), so nothing computes the hearth
 * position a second time. [fx] is the fire's centre column, [base] the row of the feet line; the sprite
 * (32 by 36) sits at ([spriteX], [spriteY]).
 */
class FireGeometry(val geo: SceneGeometry) {
    val fx: Int = geo.hearthX
    val base: Int = geo.hearthBase
    val spriteX: Int = fx - 16
    val spriteY: Int = base - 35

    /**
     * The touch box of the fire in device pixels relative to the surface: the logical box of
     * [HIT_W] by [HIT_H] around the stones (left edge `fx - HIT_LEFT`, top edge `base - HIT_UP`) times
     * `grid.scale` plus the grid offsets, grown around its centre to at least [minPx] in each direction.
     */
    fun hitRect(grid: StageGrid, minPx: Int): FireHit {
        val s = grid.scale
        var left = grid.offsetX + (fx - HIT_LEFT) * s
        var top = grid.offsetY + (base - HIT_UP) * s
        var right = left + HIT_W * s
        var bottom = top + HIT_H * s
        val growX = minPx - (right - left)
        if (growX > 0) {
            left -= growX / 2
            right += growX - growX / 2
        }
        val growY = minPx - (bottom - top)
        if (growY > 0) {
            top -= growY / 2
            bottom += growY - growY / 2
        }
        return FireHit(left, top, right, bottom)
    }

    companion object {
        /** The hit box in logical pixels: 24 by 30 around the stones; Pet's box starts one pixel inside it. */
        const val HIT_W = 24
        const val HIT_H = 30
        const val HIT_LEFT = 12
        const val HIT_UP = 29
    }
}

/** A rectangle in device pixels; [right] and [bottom] are exclusive. */
data class FireHit(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}
