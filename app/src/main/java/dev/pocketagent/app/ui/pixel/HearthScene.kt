package dev.pocketagent.app.ui.pixel

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.pocketagent.app.ui.theme.AnimLevel
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.PocketColors
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class HearthState { COLD, KINDLING, BLAZING, SMOKE }

// ---------------------------------------------------------------------------------------------------
// The scene lives on a logical pixel grid. The art layers (scene.pxl) are 176 logical px wide; the grid
// width is whatever fits the Canvas at an integer device-pixel size `p` (p = ceil(width / 176), so the
// grid is at most 176 wide and 150+ on phones). Everything is bottom-anchored, the sky grows upwards.
// Layer order: sky bands, stars, moon, far ridge, near ridge + trees, ground, fire glow, newt, fire,
// smoke, embers, foreground grass. tools/pixel-preview.ps1 shows the sprites; the composition was checked
// with a Python port of this file (same algorithms).
// ---------------------------------------------------------------------------------------------------

private const val ART_W = 176
private const val GROUND_H = 34
private const val GLOW_LEVELS = 8

private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
private fun bayer(x: Int, y: Int): Float = (BAYER[((y and 3) shl 2) or (x and 3)] + 0.5f) / 16f

private val STAR_DIM = Color(0xFF6E6496)
private val STAR_MID = Color(0xFFBEB8DC)
private val STAR_BRIGHT = Color(0xFFFFF6DC)
private val STAR_CROSS = Color(0xFF4A3E6C)
private val SMOKE_COLOR = Color(0xFF9A92AC)

// far-ridge tint ramp (1 darkest .. 4 lightest): moonlit violet, mixed a little with the theme's bg
private val FAR_RAMP = longArrayOf(0xFF1F1838, 0xFF2D2552, 0xFF443D7A, 0xFF6B67AA)

private fun lerpArgb(a: Int, b: Int, t: Float): Int {
    fun ch(shift: Int): Int {
        val x = (a ushr shift) and 0xFF
        val y = (b ushr shift) and 0xFF
        return (x + (y - x) * t).roundToInt().coerceIn(0, 255)
    }
    return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

/** Sprite lookups of the scene (resolved once, after the bank is initialised). */
private class SceneSprites {
    val far = SpriteBank.get("scene_far")
    val near = SpriteBank.get("scene_near")
    val ground = SpriteBank.get("scene_ground")
    val fore = SpriteBank.get("scene_fore")
    val moon = SpriteBank.get("scene_moon")
    val fireCold = SpriteBank.get("fire_cold")
    val fireOut = SpriteBank.get("fire_out")
    val fireLow = SpriteBank.get("fire_low")
    val fireMid = SpriteBank.get("fire_mid")
    val fireHigh = SpriteBank.get("fire_high")
    val pets: List<Sprite> = MascotMood.entries.map { SpriteBank.get(it.spriteName()) }
    val embers = EmberSet(40, 21L)
}

/** Bitmaps that depend on the size or the theme; rebuilt only when their inputs change, never per frame. */
private class SceneCache {
    private var sky: ImageBitmap? = null
    private var skyW = 0
    private var skyH = 0
    private var skyTop = 0
    private var skyBottom = 0

    var starX = IntArray(0)
    var starY = IntArray(0)
    var starPhase = FloatArray(0)
    var starRate = FloatArray(0)
    private var starW = 0
    private var starG = 0

    private var farImg: ImageBitmap? = null
    private var farKey: IntArray? = null

    private val glowImgs = arrayOfNulls<ImageBitmap>(GLOW_LEVELS)
    private val glowHalfW = IntArray(GLOW_LEVELS)
    private val glowHalfH = IntArray(GLOW_LEVELS)
    private var glowColor = 0

    fun sky(w: Int, h: Int, top: Int, bottom: Int): ImageBitmap {
        val have = sky
        if (have != null && skyW == w && skyH == h && skyTop == top && skyBottom == bottom) return have
        val n = 9
        val cols = IntArray(n) { lerpArgb(top, bottom, it / (n - 1f)) }
        val px = IntArray(w * h)
        for (y in 0 until h) {
            val tt = y / (h - 1).coerceAtLeast(1).toFloat()
            val f = tt.pow(1.15f) * (n - 1)
            val i0 = f.toInt()
            val frac = f - i0
            for (x in 0 until w) {
                val up = frac > 0.55f && (frac - 0.55f) / 0.45f > bayer(x, y)
                px[y * w + x] = cols[min(n - 1, i0 + if (up) 1 else 0)]
            }
        }
        val img = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
        sky = img; skyW = w; skyH = h; skyTop = top; skyBottom = bottom
        return img
    }

    fun stars(w: Int, g: Int) {
        if (starW == w && starG == g) return
        val n = 46
        val r = java.util.Random(4)
        starX = IntArray(n); starY = IntArray(n); starPhase = FloatArray(n); starRate = FloatArray(n)
        val maxY = (g * 0.72f).toInt().coerceAtLeast(6)
        for (k in 0 until n) {
            starX[k] = 2 + r.nextInt((w - 4).coerceAtLeast(1))
            starY[k] = 2 + r.nextInt(maxY - 2)
            starPhase[k] = r.nextFloat() * 6.2832f
            starRate[k] = 0.6f + (k % 5) * 0.2f
        }
        starW = w; starG = g
    }

    fun far(sprite: Sprite, ramp: IntArray): ImageBitmap {
        val have = farImg
        val key = farKey
        if (have != null && key != null && key.contentEquals(ramp)) return have
        val img = SpriteBitmaps.get(sprite, 0, false, 0, ramp)
        farImg = img; farKey = ramp
        return img
    }

    /** Quantised warm glow: dithered bands of [color], bigger for higher [level] (0..7). */
    fun glow(level: Int, color: Int): ImageBitmap {
        if (glowColor != color) {
            for (i in glowImgs.indices) glowImgs[i] = null
            glowColor = color
        }
        glowImgs[level]?.let { return it }
        val scale = 0.35f + 0.65f * level / (GLOW_LEVELS - 1)
        val rx = (58f * scale).roundToInt()
        val ry = (30f * scale).roundToInt()
        val w = 2 * rx + 1
        val h = 2 * ry + 1
        val levels = floatArrayOf(0f, 0.07f, 0.14f, 0.24f, 0.36f)
        val rgb = color and 0xFFFFFF
        val px = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val dx = (x - rx) / rx.toFloat()
                val dy = (y - ry) / ry.toFloat()
                val d = sqrt(dx * dx + dy * dy)
                if (d >= 1f) continue
                val f = (1f - d) * 4f
                val i0 = f.toInt()
                val lv = min(4, i0 + if (f - i0 > bayer(x, y)) 1 else 0)
                val a = (levels[lv] * 255f).roundToInt()
                px[y * w + x] = (a shl 24) or rgb
            }
        }
        val img = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
        glowImgs[level] = img; glowHalfW[level] = rx; glowHalfH[level] = ry
        return img
    }

    fun glowRx(level: Int) = glowHalfW[level]
    fun glowRy(level: Int) = glowHalfH[level]
}

/**
 * The home hero: a night over the Zagros, a stone ring with a hand-keyed fire, drifting embers and Pet.
 * Fills the width; give it a height of about 250-320 dp (default 280 dp). [progress] (0..1) drives KINDLING.
 * [mood] overrides the mascot's mood (null = derived: COLD sleeping, KINDLING thinking, BLAZING idle with
 * an occasional happy hop, SMOKE error). Animations follow Pocket.anim: REDUCED is slower and has no
 * particles, OFF is one still frame.
 */
@Composable
fun HearthScene(
    state: HearthState,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    mood: MascotMood? = null,
    stokeBoost: Float = 0f,
    celebration: Boolean = false,
) {
    SpriteBank.init(LocalContext.current)
    val colors = Pocket.colors
    val anim = Pocket.anim
    val clock = rememberClock(anim != AnimLevel.OFF)
    val cache = remember { SceneCache() }
    val sprites = remember { SceneSprites() }

    val skyTop = (if (colors.isDark) colors.bgDeep else Color(0xFF130A16)).toArgb()
    val skyBase = if (colors.isDark) colors.bg else Color(0xFF1D1220)
    val skyBottom = lerp(skyBase, colors.fireLow, 0.16f).toArgb()
    val farRamp = remember(skyBase) { IntArray(4) { lerp(Color(FAR_RAMP[it]), skyBase, 0.2f).toArgb() } }
    val glowColor = colors.fireMid.toArgb() or (0xFF shl 24)

    Spacer(
        modifier
            .fillMaxWidth()
            .height(280.dp)
            .clipToBounds()
            .drawBehind {
                drawHearth(
                    cache, sprites, clock.longValue / (if (anim == AnimLevel.REDUCED) 2 else 1), state, progress, mood,
                    colors, anim == AnimLevel.FULL, skyTop, skyBottom, farRamp, glowColor,
                    stokeBoost.coerceIn(0f, 1.5f), celebration,
                )
            },
    )
}

private fun DrawScope.drawHearth(
    cache: SceneCache, s: SceneSprites, t: Long, state: HearthState, progress: Float?, moodOverride: MascotMood?,
    colors: PocketColors, particles: Boolean, skyTop: Int, skyBottom: Int, farRamp: IntArray, glowColor: Int,
    stokeBoost: Float, celebration: Boolean,
) {
    val wpx = size.width
    val hpx = size.height
    if (wpx < 8f || hpx < 8f) return
    val p = ceil(wpx / ART_W).toInt().coerceAtLeast(1)
    val pf = p.toFloat()
    val wl = ceil(wpx / p).toInt()
    val hl = ceil(hpx / p).toInt()
    val ox = ((wpx - wl * p) / 2f).roundToInt()
    val oy = (hpx - hl * p).roundToInt()
    val g = hl - GROUND_H                       // top of the ground, logical y
    val artX = (wl - ART_W) / 2                  // art layers are centred on the grid

    // ---- sky and stars ----
    drawSpriteAt(cache.sky(wl, g + 8, skyTop, skyBottom), ox, oy, p)
    cache.stars(wl, g)
    for (k in cache.starX.indices) {
        val tw = (sin(t * 0.0014f * cache.starRate[k] + cache.starPhase[k]) + 1f) * 0.5f
        val lvl = if (tw < 0.35f) 0 else if (tw < 0.75f) 1 else 2
        val col = if (lvl == 0) STAR_DIM else if (lvl == 1) STAR_MID else STAR_BRIGHT
        val x = ox + cache.starX[k] * p
        val y = oy + cache.starY[k] * p
        px(col, x.toFloat(), y.toFloat(), pf, pf)
        if (k % 9 == 0 && lvl > 0) {
            px(STAR_CROSS, x - pf, y.toFloat(), pf, pf); px(STAR_CROSS, x + pf, y.toFloat(), pf, pf)
            px(STAR_CROSS, x.toFloat(), y - pf, pf, pf); px(STAR_CROSS, x.toFloat(), y + pf, pf, pf)
        }
    }

    // ---- shooting star (occasional, FULL animation only) ----
    if (particles) {
        drawShootingStar(t, ox, oy, p, wl, g)
    }

    // ---- moon halo, moon and ridges ----
    val moonX = (wl * 0.76f).toInt()
    val moonY = 20
    drawMoonHalo(ox, oy, p, moonX + 7, moonY + 7)
    drawSpriteAt(SpriteBitmaps.plain(s.moon, 0), ox + moonX * p, oy + moonY * p, p)
    drawSpriteAt(cache.far(s.far, farRamp), ox + artX * p, oy + (g - 38) * p, p)
    drawSpriteAt(SpriteBitmaps.plain(s.near, 0), ox + artX * p, oy + (g - 14) * p, p)
    if (state == HearthState.BLAZING && particles) {
        drawDistantNewrozFires(t, ox, oy, p, artX, g, colors)
    }
    drawSpriteAt(SpriteBitmaps.plain(s.ground, 0), ox + artX * p, oy + g * p, p)

    // ---- state: fire sprite, glow, mascot ----
    val fx = (wl * 0.6f).toInt()
    val fbase = hl - 3
    val intensity: Float
    val fire: Sprite
    when (state) {
        HearthState.COLD -> { intensity = 0f; fire = s.fireCold }
        HearthState.KINDLING -> {
            val pr = (progress ?: 0.5f).coerceIn(0f, 1f)
            intensity = 0.18f + 0.72f * pr
            fire = if (pr < 0.15f) s.fireOut else if (pr < 0.5f) s.fireLow else if (pr < 0.85f) s.fireMid else s.fireHigh
        }
        HearthState.BLAZING -> { intensity = 1f; fire = s.fireHigh }
        HearthState.SMOKE -> {
            val slot = (t / 170L).toInt()
            val on = ((slot * 7 + slot / 3) % 5 + 5) % 5 < 3
            intensity = if (on) 0.3f else 0.1f
            fire = if (on) s.fireLow else s.fireOut
        }
    }

    val boost = (stokeBoost + if (celebration) 0.5f else 0f).coerceIn(0f, 1.5f)
    if (intensity > 0.01f || boost > 0.05f) {
        val eff = (intensity + boost * 0.45f).coerceIn(0f, 1.25f)
        var lvl = (eff * (GLOW_LEVELS - 1)).roundToInt()
        if (particles) {
            val h = (((t / 90L).toInt() * 1103515245) shr 16) and 3
            lvl += if (h == 0) -1 else if (h == 3) 1 else 0
        }
        lvl = lvl.coerceIn(0, GLOW_LEVELS - 1)
        val img = cache.glow(lvl, glowColor)
        val cx = fx
        val cy = fbase - 8
        drawSpriteAt(img, ox + (cx - cache.glowRx(lvl)) * p, oy + (cy - cache.glowRy(lvl)) * p, p, (0.55f + 0.45f * eff).coerceAtMost(1f))
    }

    var m = moodOverride ?: when (state) {
        HearthState.COLD -> MascotMood.SLEEPING
        HearthState.KINDLING -> MascotMood.THINKING
        HearthState.BLAZING -> MascotMood.IDLE
        HearthState.SMOKE -> MascotMood.ERROR
    }
    var petT = t
    if (moodOverride == null && state == HearthState.BLAZING && particles) {
        val ph = t % 9000L
        if (ph >= 7600L) { m = MascotMood.HAPPY; petT = ph - 7600L }
    }
    val pet = s.pets[m.ordinal]
    drawSpriteAt(SpriteBitmaps.plain(pet, pet.frameAt(petT)), ox + (fx - 43) * p, oy + (fbase - 31) * p, p)
    drawSpriteAt(SpriteBitmaps.plain(fire, fire.frameAt(t)), ox + (fx - 16) * p, oy + (fbase - 35) * p, p)

    // ---- smoke and embers (FULL animation only) ----
    if (particles) {
        when (state) {
            HearthState.COLD -> drawSmoke(t, 2, false, ox, oy, p, fx, fbase - 8)
            HearthState.SMOKE -> drawSmoke(t, 6, true, ox, oy, p, fx, fbase - 8)
            HearthState.KINDLING -> if ((progress ?: 0.5f) < 0.3f) drawSmoke(t, 2, false, ox, oy, p, fx, fbase - 8)
            HearthState.BLAZING -> {}
        }
        if (state != HearthState.COLD || boost > 0.05f || celebration) {
            val baseCount = if (state == HearthState.SMOKE) 4 else (3f + 21f * intensity).roundToInt()
            val count = (baseCount + (boost * 14f).roundToInt() + if (celebration) 14 else 0).coerceAtMost(s.embers.capacity)
            val high = if (celebration) Color(0xFFFEBD11) else colors.fireHigh
            val mid = if (celebration) Color(0xFF278E43) else colors.fireMid
            val low = if (celebration) Color(0xFFED1C24) else colors.fireLow
            drawEmbers(
                s.embers, count, t, pf,
                originX = (ox + fx * p).toFloat(), originY = (oy + (fbase - 12 - (14f * intensity).toInt()) * p).toFloat(),
                halfWidthCells = 5f + 4f * intensity + boost * 3f, riseCells = 20f + 26f * intensity + boost * 14f,
                high = high, mid = mid, low = low,
                strength = (0.5f + 0.5f * intensity + boost * 0.25f).coerceAtMost(1f),
            )
        }
        if (state == HearthState.BLAZING || state == HearthState.KINDLING) {
            drawFireflies(t, ox, oy, p, wl, g, colors)
        }
    }

    // ---- foreground grass ----
    drawSpriteAt(SpriteBitmaps.plain(s.fore, 0), ox + artX * p, oy + (hl - 11) * p, p)
}

/** Soft stepped pixel glow around the crescent moon. */
private fun DrawScope.drawMoonHalo(ox: Int, oy: Int, p: Int, cx: Int, cy: Int) {
    val pf = p.toFloat()
    val halo = Color(0xFFFFF2C8)
    for (dy in -10..10) {
        for (dx in -10..10) {
            val d = sqrt((dx * dx + dy * dy).toFloat())
            if (d > 10.5f || d < 4.2f) continue
            val norm = 1f - (d - 4.2f) / 6.3f
            if (norm > bayer(cx + dx, cy + dy) * 1.15f) {
                px(halo.copy(alpha = 0.08f + norm * 0.09f), (ox + (cx + dx) * p).toFloat(), (oy + (cy + dy) * p).toFloat(), pf, pf)
            }
        }
    }
}

/** Tiny warm distant Newroz bonfires twinkling along the far Zagros hillside ridge when blazing. */
private fun DrawScope.drawDistantNewrozFires(t: Long, ox: Int, oy: Int, p: Int, artX: Int, g: Int, colors: PocketColors) {
    val pf = p.toFloat()
    val spots = intArrayOf(34, -2, 74, -4, 152, -3)
    var i = 0
    while (i < spots.size) {
        val sx = artX + spots[i]
        val sy = g + spots[i + 1]
        val flick = (sin(t * 0.008f + i * 2.1f) + 1f) * 0.5f
        px(colors.fireLow.copy(alpha = 0.25f + flick * 0.2f), (ox + (sx - 1) * p).toFloat(), (oy + sy * p).toFloat(), pf * 3f, pf)
        px(if (flick > 0.45f) colors.fireHigh else colors.fireMid, (ox + sx * p).toFloat(), (oy + sy * p).toFloat(), pf, pf)
        i += 2
    }
}

/** Gentle pulsing golden alpine fireflies hovering over the meadow flanks. */
private fun DrawScope.drawFireflies(t: Long, ox: Int, oy: Int, p: Int, wl: Int, g: Int, colors: PocketColors) {
    val pf = p.toFloat()
    val base = Color(0xFFFFEA8A)
    for (k in 0 until 6) {
        val anchorX = if (k < 3) (wl * (0.10f + k * 0.09f)) else (wl * (0.80f + (k - 3) * 0.06f))
        val anchorY = g + 6f + (k % 3) * 5f
        val fx = (anchorX + sin(t * 0.0007f + k * 1.9f) * 5.5f).roundToInt()
        val fy = (anchorY + sin(t * 0.0011f + k * 2.7f) * 2.8f).roundToInt()
        val pulse = (sin(t * 0.0022f + k * 1.4f) + 1f) * 0.5f
        if (pulse < 0.25f) continue
        val a = ((pulse - 0.25f) / 0.75f).coerceIn(0f, 1f)
        px(colors.fireMid.copy(alpha = a * 0.25f), (ox + (fx - 1) * p).toFloat(), (oy + fy * p).toFloat(), pf * 3f, pf)
        px(base.copy(alpha = a * 0.9f), (ox + fx * p).toFloat(), (oy + fy * p).toFloat(), pf, pf)
    }
}

/** Occasional quiet pixel shooting star streaking across the upper night sky every ~14 seconds. */
private fun DrawScope.drawShootingStar(t: Long, ox: Int, oy: Int, p: Int, wl: Int, g: Int) {
    val cycle = 14000L
    val phase = t % cycle
    if (phase > 720L) return
    val u = phase / 720f
    val seed = ((t / cycle) % 3L).toInt()
    val startX = (wl * (0.18f + seed * 0.16f)).toInt()
    val startY = 16 + seed * 3
    val headX = startX + (u * 34f).roundToInt()
    val headY = startY + (u * 11f).roundToInt()
    if (headY >= g - 16) return
    val pf = p.toFloat()
    val fade = (if (u < 0.15f) u / 0.15f else (1f - u) / 0.85f).coerceIn(0f, 1f)
    for (step in 0..5) {
        val tx = headX - step * 2
        val ty = headY - (step * 2) / 3
        val ta = fade * (1f - step / 6f)
        if (ta <= 0.05f) continue
        val col = if (step == 0) STAR_BRIGHT else if (step <= 2) STAR_MID else STAR_DIM
        px(col.copy(alpha = ta), (ox + tx * p).toFloat(), (oy + ty * p).toFloat(), pf, pf)
    }
}

/** Grey puffs rising from the coals: octagons of 1-4 logical px radius that grow, drift right and fade. */
private fun DrawScope.drawSmoke(t: Long, n: Int, strong: Boolean, ox: Int, oy: Int, p: Int, fx: Int, fy: Int) {
    val pf = p.toFloat()
    for (i in 0 until n) {
        val period = 2800f + i * 410f
        val u = ((t.toFloat() / period) + i.toFloat() / n) % 1f
        val rise = u * (if (strong) 44f else 30f)
        val xs = fx + sin(u * 6.2832f * 1.1f + i * 1.7f) * (2f + u * 5f) + u * 7f
        val ys = fy - rise
        val r = 1 + (u * (if (strong) 3.4f else 1.8f)).toInt()
        val fade = if (u < 0.1f) u * 10f else 1f
        val a = fade * (1f - u) * (if (strong) 0.6f else 0.3f)
        if (a <= 0.02f) continue
        val c = SMOKE_COLOR.copy(alpha = a)
        val xi = floor(xs).toInt()
        val yi = floor(ys).toInt()
        px(c, (ox + (xi - r + 1) * p).toFloat(), (oy + (yi - r) * p).toFloat(), (2 * r - 1) * pf, pf)
        px(c, (ox + (xi - r) * p).toFloat(), (oy + (yi - r + 1) * p).toFloat(), (2 * r + 1) * pf, (2 * r - 1) * pf)
        px(c, (ox + (xi - r + 1) * p).toFloat(), (oy + (yi + r) * p).toFloat(), (2 * r - 1) * pf, pf)
    }
}
