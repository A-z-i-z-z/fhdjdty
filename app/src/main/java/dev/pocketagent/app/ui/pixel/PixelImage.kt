package dev.pocketagent.app.ui.pixel

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.pocketagent.app.ui.theme.AnimLevel
import dev.pocketagent.app.ui.theme.Pocket
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Turns sprite frames into [ImageBitmap]s (one pixel per sprite pixel) and keeps them. Plain frames
 * (no tint, no ramp) live on the [Sprite] itself, so the hot path of every animation allocates nothing;
 * tinted/re-coloured variants sit in a small LRU cache.
 */
internal object SpriteBitmaps {
    private class Key(val sprite: Sprite, val frame: Int, val hasTint: Boolean, val tint: Int, val ramp: IntArray?) {
        private val hash: Int = run {
            var h = System.identityHashCode(sprite)
            h = h * 31 + frame
            h = h * 31 + (if (hasTint) tint else 0)
            h = h * 31 + (if (hasTint) 1 else 0)
            h * 31 + (ramp?.contentHashCode() ?: 0)
        }

        override fun hashCode(): Int = hash
        override fun equals(other: Any?): Boolean {
            if (other !is Key) return false
            return other.sprite === sprite && other.frame == frame && other.hasTint == hasTint &&
                other.tint == tint && (ramp contentEquals other.ramp)
        }
    }

    private val cache = object : LruCache<Key, ImageBitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: Key, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun plain(sprite: Sprite, frame: Int): ImageBitmap {
        val have = sprite.bitmaps[frame]
        if (have != null) return have as ImageBitmap
        val bmp = build(sprite, frame, false, 0, null)
        sprite.bitmaps[frame] = bmp
        return bmp
    }

    @Synchronized
    fun get(sprite: Sprite, frame: Int, hasTint: Boolean, tint: Int, ramp: IntArray?): ImageBitmap {
        if (!hasTint && ramp == null) return plain(sprite, frame)
        val key = Key(sprite, frame, hasTint, tint, ramp)
        cache.get(key)?.let { return it }
        val bmp = build(sprite, frame, hasTint, tint, ramp)
        cache.put(key, bmp)
        return bmp
    }

    private fun build(sprite: Sprite, frame: Int, hasTint: Boolean, tint: Int, ramp: IntArray?): ImageBitmap {
        val pal = IntArray(sprite.palette.size) { sprite.palette[it].argb }
        if (ramp != null && ramp.isNotEmpty()) {
            for (k in 0 until 4) {
                val slot = sprite.rampSlots[k]
                if (slot >= 0) pal[slot] = ramp[min(k, ramp.size - 1)]
            }
        }
        if (hasTint) {
            val ta = tint ushr 24
            for (i in pal.indices) {
                val a = (((pal[i] ushr 24) * ta) + 127) / 255
                pal[i] = (a shl 24) or (tint and 0xFFFFFF)
            }
        }
        val src = sprite.frames[frame].indices
        val px = IntArray(src.size) { val s = src[it]; if (s < 0) 0 else pal[s] }
        return Bitmap.createBitmap(px, sprite.width, sprite.height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

/** Largest integer scale that fits (or, if even 1x does not fit, the exact fraction). */
internal fun fitScale(availW: Float, availH: Float, w: Int, h: Int): Float {
    val s = min(availW / w, availH / h)
    return if (s >= 1f) floor(s) else s
}

/** Draws [img] centred in the current DrawScope at the largest integer scale, nearest neighbour. */
internal fun DrawScope.drawSpriteFit(img: ImageBitmap) {
    val s = fitScale(size.width, size.height, img.width, img.height)
    if (s <= 0f) return
    val dw = (img.width * s).roundToInt().coerceAtLeast(1)
    val dh = (img.height * s).roundToInt().coerceAtLeast(1)
    drawImage(
        image = img,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(img.width, img.height),
        dstOffset = IntOffset(((size.width - dw) / 2f).roundToInt(), ((size.height - dh) / 2f).roundToInt()),
        dstSize = IntSize(dw, dh),
        alpha = 1f,
        filterQuality = FilterQuality.None,
    )
}

/** Draws [img] with its top-left at pixel (x, y), every sprite pixel becoming scale x scale device pixels. */
internal fun DrawScope.drawSpriteAt(img: ImageBitmap, x: Int, y: Int, scale: Int, alpha: Float = 1f) {
    drawImage(
        image = img,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(img.width, img.height),
        dstOffset = IntOffset(x, y),
        dstSize = IntSize(img.width * scale, img.height * scale),
        alpha = alpha,
        filterQuality = FilterQuality.None,
    )
}

/** A rect in device pixels (no allocation). */
internal fun DrawScope.px(color: Color, x: Float, y: Float, w: Float, h: Float) {
    drawRect(color, Offset(x, y), Size(w, h))
}

/**
 * Preferred size = the sprite at 3 dp per pixel, but whatever the caller's modifier (or the parent)
 * fixes wins. Lets `PixelImage(sprite)` work without a size.
 */
private fun Modifier.preferSpriteSize(w: Int, h: Int): Modifier = layout { measurable, c ->
    val unit = 3.dp.roundToPx()
    val tw = (w * unit).coerceIn(c.minWidth, c.maxWidth)
    val th = (h * unit).coerceIn(c.minHeight, c.maxHeight)
    val p = measurable.measure(Constraints.fixed(tw, th))
    layout(tw, th) { p.placeRelative(0, 0) }
}

/**
 * The frame index of [sprite] right now. A draw-phase value: read `.intValue` inside a draw lambda and
 * only the draw is invalidated when the frame changes, never the composition.
 *
 * With a fixed frame, [playing] = false or animations OFF it stays at the fixed frame / frame 0.
 * Animations REDUCED run at half speed. [offsetMs] shifts the start (de-synchronises neighbours).
 */
@Composable
internal fun rememberSpriteFrame(sprite: Sprite, fixedFrame: Int?, playing: Boolean, offsetMs: Long = 0L): IntState {
    val anim = Pocket.anim
    val state = remember(sprite) { mutableIntStateOf(0) }
    val run = fixedFrame == null && playing && anim != AnimLevel.OFF && sprite.frames.size > 1
    LaunchedEffect(sprite, run, anim, offsetMs) {
        if (!run) {
            state.intValue = 0
            return@LaunchedEffect
        }
        val slow = if (anim == AnimLevel.REDUCED) 2 else 1
        var start = -1L
        while (true) {
            val now = withFrameMillis { it }
            if (start < 0) start = now
            val t = (now - start) / slow + offsetMs
            val idx = sprite.frameAt(t)
            if (state.intValue != idx) state.intValue = idx
            val wait = sprite.msUntilNextFrame(t).toLong() * slow - 24L
            if (wait > 8L) delay(wait)
        }
    }
    return state
}

/**
 * A clock in milliseconds for scenes that animate many things at once: one coroutine, about 30 updates
 * per second, read inside draw lambdas only. Stays 0 when [active] is false.
 */
@Composable
internal fun rememberClock(active: Boolean): LongState {
    val s = remember { mutableLongStateOf(0L) }
    LaunchedEffect(active) {
        if (!active) {
            s.longValue = 0L
            return@LaunchedEffect
        }
        val t0 = withFrameMillis { it }
        while (true) {
            val t = withFrameMillis { it }
            s.longValue = t - t0
            delay(22)
        }
    }
    return s
}

/**
 * Crisp nearest-neighbour sprite. The sprite is centred and scaled by the largest integer factor that fits
 * the given size (default size = 3 dp per sprite pixel). Animates through its frames when [frame] == null,
 * [playing] and animations are on (REDUCED = half speed, OFF = frame 0).
 *
 * [tint]: every opaque pixel is drawn in this colour (monochrome icons). [ramp]: replaces the palette
 * chars '1'..'4' (darkest to lightest).
 */
@Composable
fun PixelImage(
    sprite: Sprite,
    modifier: Modifier = Modifier,
    frame: Int? = null,
    tint: Color? = null,
    ramp: List<Color>? = null,
    playing: Boolean = true,
) {
    val current = rememberSpriteFrame(sprite, frame, playing)
    val hasTint = tint != null
    val tintArgb = tint?.toArgb() ?: 0
    val rampArgb = remember(ramp) { ramp?.map { it.toArgb() }?.toIntArray() }
    Spacer(
        modifier
            .preferSpriteSize(sprite.width, sprite.height)
            .drawBehind {
                val f = (frame ?: current.intValue).coerceIn(0, sprite.frames.size - 1)
                drawSpriteFit(SpriteBitmaps.get(sprite, f, hasTint, tintArgb, rampArgb))
            },
    )
}

/** Same by name; loads the sprite bank on first use. */
@Composable
fun PixelImage(
    name: String,
    modifier: Modifier = Modifier,
    frame: Int? = null,
    tint: Color? = null,
    ramp: List<Color>? = null,
    playing: Boolean = true,
) {
    SpriteBank.init(LocalContext.current)
    val sprite = remember(name) { SpriteBank.get(name) }
    PixelImage(sprite, modifier, frame, tint, ramp, playing)
}
