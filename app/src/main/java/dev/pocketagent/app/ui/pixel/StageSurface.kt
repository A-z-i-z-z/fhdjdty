package dev.pocketagent.app.ui.pixel

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.pocketagent.app.ui.pixel.stage.PixelStage
import dev.pocketagent.app.ui.pixel.stage.StageGrid

/**
 * Puts a [PixelStage] on the screen: the stage is drawn by plain code into an int array, copied into a
 * bitmap and drawn with ONE nearest-neighbour image call at a whole-number scale (see [StageGrid]).
 *
 * Remember one per surface. It owns the stage and a ring of three ARGB_8888 bitmaps. The render thread may
 * still be reading the bitmap of the previous frame while the next one is prepared, so a bitmap that was
 * drawn last is never written to: each new frame goes into the next one of the ring. Bitmaps are never
 * recycled (a recycled bitmap in a pending display list crashes); [release] only drops the references.
 */
class StageSurface {
    private var stage: PixelStage? = null
    private val ring = arrayOfNulls<Bitmap>(3)
    private val ringImages = arrayOfNulls<ImageBitmap>(3)
    private var next = 0
    private var shown = -1
    private var lastStamp: Any? = null

    /**
     * Draws the stage of [grid] into [scope]. [grid] null draws nothing. [stamp] says what the picture shows:
     * when it equals the previous call's stamp (and the stage size is the same) nothing is rendered and the
     * previous bitmap is drawn again; otherwise the stage is cleared, [render] fills it and it goes into the
     * next bitmap of the ring.
     */
    fun draw(scope: DrawScope, grid: StageGrid?, stamp: Any, render: (PixelStage) -> Unit) {
        if (grid == null) return
        val w = grid.width
        val h = grid.height
        val have = stage
        val st: PixelStage
        if (have != null && have.width == w && have.height == h) {
            st = have
        } else {
            st = PixelStage(w, h)
            stage = st
            for (i in ring.indices) { ring[i] = null; ringImages[i] = null }
            next = 0
            shown = -1
            lastStamp = null
        }
        if (shown < 0 || stamp != lastStamp) {
            st.clear()
            render(st)
            var bmp = ring[next]
            if (bmp == null) {
                bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                ring[next] = bmp
                ringImages[next] = bmp.asImageBitmap()
            }
            bmp.setPixels(st.pixels, 0, w, 0, 0, w, h)
            shown = next
            next = (next + 1) % ring.size
            lastStamp = stamp
        }
        val image = ringImages[shown] ?: return
        scope.drawImage(
            image,
            IntOffset.Zero,
            IntSize(w, h),
            IntOffset(grid.offsetX, grid.offsetY),
            IntSize(w * grid.scale, h * grid.scale),
            filterQuality = FilterQuality.None,
        )
    }

    /** Drops the stage and the bitmaps (they are collected once the render thread is done with them). */
    fun release() {
        stage = null
        for (i in ring.indices) { ring[i] = null; ringImages[i] = null }
        next = 0
        shown = -1
        lastStamp = null
    }
}

@Composable
fun rememberStageSurface(): StageSurface {
    val surface = remember { StageSurface() }
    DisposableEffect(surface) { onDispose { surface.release() } }
    return surface
}
