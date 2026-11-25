package dev.pocketagent.app.ui.pixel.stage

import dev.pocketagent.app.ui.pixel.Sprite
import dev.pocketagent.app.ui.pixel.SpriteRaster

/**
 * A framebuffer of [width] x [height] pixels: row-major ARGB ints, straight alpha, 0 = transparent. Every
 * drawing call is clipped to the stage and blends source-over with [Argb.over]. No Android types.
 */
class PixelStage(val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0) { "stage size must be positive: ${width}x$height" }
    }

    val pixels: IntArray = IntArray(width * height)

    fun clear(argb: Int = 0) {
        pixels.fill(argb)
    }

    fun plot(x: Int, y: Int, argb: Int) {
        if (x < 0 || y < 0 || x >= width || y >= height) return
        val i = y * width + x
        pixels[i] = Argb.over(pixels[i], argb)
    }

    fun fillRect(x: Int, y: Int, w: Int, h: Int, argb: Int) {
        if (w <= 0 || h <= 0) return
        val x0 = if (x < 0) 0 else x
        val y0 = if (y < 0) 0 else y
        val x1 = if (x + w > width) width else x + w
        val y1 = if (y + h > height) height else y + h
        if (x0 >= x1 || y0 >= y1) return
        for (yy in y0 until y1) {
            var i = yy * width + x0
            for (xx in x0 until x1) {
                pixels[i] = Argb.over(pixels[i], argb)
                i++
            }
        }
    }

    /** Draws [src] (srcW x srcH ARGB ints) with its top-left at (x, y); [alpha] multiplies the source alpha. */
    fun blit(src: IntArray, srcW: Int, srcH: Int, x: Int, y: Int, alpha: Float = 1f) {
        val a255 = Argb.alpha255(alpha)
        if (a255 == 0) return
        val x0 = if (x < 0) 0 else x
        val y0 = if (y < 0) 0 else y
        val x1 = if (x + srcW > width) width else x + srcW
        val y1 = if (y + srcH > height) height else y + srcH
        if (x0 >= x1 || y0 >= y1) return
        for (yy in y0 until y1) {
            var di = yy * width + x0
            var si = (yy - y) * srcW + (x0 - x)
            for (xx in x0 until x1) {
                val c0 = src[si]
                if ((c0 ushr 24) != 0) {
                    val c = if (a255 == 255) c0 else Argb.scaleAlpha(c0, a255)
                    pixels[di] = Argb.over(pixels[di], c)
                }
                di++
                si++
            }
        }
    }

    /** Draws one frame of [sprite] at (x, y), one sprite pixel per stage pixel. */
    fun blit(sprite: Sprite, frame: Int, x: Int, y: Int, alpha: Float = 1f) {
        blit(SpriteRaster.plain(sprite, frame), sprite.width, sprite.height, x, y, alpha)
    }

    /**
     * Copies the stage into [dst] (dstW x dstH device pixels), nearest neighbour: every stage pixel becomes a
     * grid.scale x grid.scale block whose top-left is (grid.offsetX + sx * scale, grid.offsetY + sy * scale).
     * Blocks are clipped to [dst]; pixels of [dst] outside the stage are left alone; the stage pixels are
     * copied, not blended.
     */
    fun scaleInto(dst: IntArray, dstW: Int, dstH: Int, grid: StageGrid) {
        val scale = grid.scale
        if (scale <= 0) return
        val left = grid.offsetX
        val right = grid.offsetX + width * scale
        val x0 = if (left < 0) 0 else left
        val x1 = if (right > dstW) dstW else right
        if (x0 >= x1) return
        for (sy in 0 until height) {
            val top = grid.offsetY + sy * scale
            val y0 = if (top < 0) 0 else top
            val y1 = if (top + scale > dstH) dstH else top + scale
            if (y0 >= y1) continue
            // build the first device row of this stage row, then copy it to the other rows of the block
            val firstRow = y0 * dstW
            var sx = (x0 - left) / scale
            var xx = x0
            while (xx < x1) {
                val c = pixels[sy * width + sx]
                val blockEnd = left + (sx + 1) * scale
                val stop = if (blockEnd < x1) blockEnd else x1
                while (xx < stop) {
                    dst[firstRow + xx] = c
                    xx++
                }
                sx++
            }
            for (yy in y0 + 1 until y1) {
                System.arraycopy(dst, firstRow + x0, dst, yy * dstW + x0, x1 - x0)
            }
        }
    }
}
