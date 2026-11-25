package dev.pocketagent.app.ui.pixel

import android.graphics.Bitmap
import dev.pocketagent.app.ui.pixel.stage.PixelStage
import dev.pocketagent.app.ui.pixel.stage.StageGrid

/**
 * A copy of the stage as an ARGB_8888 bitmap, every stage pixel [scale] x [scale] pixels (nearest neighbour).
 * Never a hardware bitmap, so the caller can read or recycle it. For code that needs a picture outside a
 * DrawScope, such as a home-screen widget.
 */
fun PixelStage.toBitmap(scale: Int = 1): Bitmap {
    val s = scale.coerceAtLeast(1)
    val w = width * s
    val h = height * s
    val out = IntArray(w * h)
    scaleInto(out, w, h, StageGrid(s, width, height, 0, 0))
    return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
}
