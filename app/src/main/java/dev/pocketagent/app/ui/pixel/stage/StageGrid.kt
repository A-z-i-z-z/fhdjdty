package dev.pocketagent.app.ui.pixel.stage

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * How a stage of [width] x [height] pixels sits on a surface: every stage pixel is [scale] x [scale] device
 * pixels, the first one at ([offsetX], [offsetY]) (both are 0 or negative when the grid overhangs the surface).
 */
class StageGrid(val scale: Int, val width: Int, val height: Int, val offsetX: Int, val offsetY: Int) {
    companion object {
        /**
         * The whole-number rule of the home scene: the art is 176 pixels wide, so the scale is the smallest whole
         * number that makes it cover the width; the grid is the surface divided by that, rounded up. Centred
         * horizontally, bottom-anchored. Null when the surface is smaller than 8 px in either direction.
         */
        fun forScene(widthPx: Float, heightPx: Float): StageGrid? {
            if (widthPx < 8f || heightPx < 8f) return null
            val scale = ceil(widthPx / SceneRenderer.ART_W).toInt().coerceAtLeast(1)
            val width = ceil(widthPx / scale).toInt()
            val height = ceil(heightPx / scale).toInt()
            val offsetX = ((widthPx - width * scale) / 2f).roundToInt()
            val offsetY = (heightPx - height * scale).roundToInt()
            return StageGrid(scale, width, height, offsetX, offsetY)
        }
    }
}
