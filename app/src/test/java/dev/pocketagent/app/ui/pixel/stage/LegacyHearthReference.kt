package dev.pocketagent.app.ui.pixel.stage

import dev.pocketagent.app.ui.pixel.HearthState
import dev.pocketagent.app.ui.pixel.MascotMood
import dev.pocketagent.app.ui.pixel.Sprite
import dev.pocketagent.app.ui.pixel.spriteName
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Copy of HearthScene.kt, PixelImage.kt (SpriteBitmaps.build) and Fire.kt (EmberSet, drawEmbers) at commit
 * 344829f; delete when a wave changes the picture on purpose.
 *
 * This is the oracle of the parity test: today's drawing code, translated only in its types. The Compose
 * DrawScope calls became calls on a plain ARGB canvas of device size, Compose colours became ARGB ints and
 * the palette fields come from a ScenePalette. Every Float expression, constant and the order of statements
 * is kept as it was; do not tidy anything here. The only code shared with the new renderer is
 * Argb.over, Argb.withAlpha and Argb.scaleAlpha.
 */
internal object LegacyHearthReference {
    private const val ART_W = 176
    private const val GROUND_H = 34
    private const val GLOW_LEVELS = 8

    private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
    private fun bayer(x: Int, y: Int): Float = (BAYER[((y and 3) shl 2) or (x and 3)] + 0.5f) / 16f

    private val STAR_DIM = 0xFF6E6496.toInt()
    private val STAR_MID = 0xFFBEB8DC.toInt()
    private val STAR_BRIGHT = 0xFFFFF6DC.toInt()
    private val STAR_CROSS = 0xFF4A3E6C.toInt()
    private val SMOKE_COLOR = 0xFF9A92AC.toInt()

    private fun lerpArgb(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int): Int {
            val x = (a ushr shift) and 0xFF
            val y = (b ushr shift) and 0xFF
            return (x + (y - x) * t).roundToInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** The device-sized canvas: stands in for DrawScope.px and DrawScope.drawSpriteAt. */
    private class RefCanvas(val w: Int, val h: Int) {
        val pixels = IntArray(w * h)

        fun rect(argb: Int, x: Float, y: Float, rw: Float, rh: Float) {
            val x0 = max(0, x.roundToInt())
            val y0 = max(0, y.roundToInt())
            val x1 = min(w, (x + rw).roundToInt())
            val y1 = min(h, (y + rh).roundToInt())
            for (yy in y0 until y1) {
                for (xx in x0 until x1) {
                    val i = yy * w + xx
                    pixels[i] = Argb.over(pixels[i], argb)
                }
            }
        }

        fun image(px: IntArray, iw: Int, ih: Int, x: Int, y: Int, scale: Int, alpha: Float) {
            val a255 = Argb.alpha255(alpha)
            for (sy in 0 until ih) {
                for (sx in 0 until iw) {
                    val c0 = px[sy * iw + sx]
                    if ((c0 ushr 24) == 0) continue
                    val c = Argb.scaleAlpha(c0, a255)
                    val dx0 = x + sx * scale
                    val dy0 = y + sy * scale
                    for (yy in max(0, dy0) until min(h, dy0 + scale)) {
                        for (xx in max(0, dx0) until min(w, dx0 + scale)) {
                            val i = yy * w + xx
                            pixels[i] = Argb.over(pixels[i], c)
                        }
                    }
                }
            }
        }
    }

    private class LegacyEmberSet(val capacity: Int, seed: Long) {
        val spawnX = FloatArray(capacity)
        val periodMs = FloatArray(capacity)
        val phase = FloatArray(capacity)
        val swayAmp = FloatArray(capacity)
        val swayFreq = FloatArray(capacity)
        val drift = FloatArray(capacity)
        val big = BooleanArray(capacity)

        init {
            val r = java.util.Random(seed)
            for (i in 0 until capacity) {
                spawnX[i] = r.nextFloat() * 2f - 1f
                periodMs[i] = 2200f + r.nextFloat() * 2800f
                phase[i] = r.nextFloat()
                swayAmp[i] = 1.5f + r.nextFloat() * 3.5f
                swayFreq[i] = 0.8f + r.nextFloat() * 1.4f
                drift[i] = r.nextFloat() * 1.6f - 0.5f
                big[i] = r.nextFloat() < 0.22f
            }
        }
    }

    /** Body of SpriteBitmaps.build with no tint: one ARGB int per sprite pixel. */
    private fun buildFrame(sprite: Sprite, frame: Int, ramp: IntArray?): IntArray {
        val pal = IntArray(sprite.palette.size) { sprite.palette[it].argb }
        if (ramp != null && ramp.isNotEmpty()) {
            for (k in 0 until 4) {
                val slot = sprite.rampSlots[k]
                if (slot >= 0) pal[slot] = ramp[min(k, ramp.size - 1)]
            }
        }
        val src = sprite.frames[frame].indices
        return IntArray(src.size) { val s = src[it]; if (s < 0) 0 else pal[s] }
    }

    private class Glow(val px: IntArray, val w: Int, val h: Int, val halfW: Int, val halfH: Int)

    /** Everything one render needs: the canvas, the resolved sprites and the caches of SceneCache. */
    private class Run(sprites: dev.pocketagent.app.ui.pixel.stage.SpriteSource, val canvas: RefCanvas, val palette: ScenePalette) {
        val far = sprites["scene_far"]
        val near = sprites["scene_near"]
        val ground = sprites["scene_ground"]
        val fore = sprites["scene_fore"]
        val moon = sprites["scene_moon"]
        val fireCold = sprites["fire_cold"]
        val fireOut = sprites["fire_out"]
        val fireLow = sprites["fire_low"]
        val fireMid = sprites["fire_mid"]
        val fireHigh = sprites["fire_high"]
        val pets: List<Sprite> = MascotMood.entries.map { sprites[it.spriteName()] }
        val embers = LegacyEmberSet(40, 21L)

        var starX = IntArray(0)
        var starY = IntArray(0)
        var starPhase = FloatArray(0)
        var starRate = FloatArray(0)

        private val glowImgs = arrayOfNulls<Glow>(GLOW_LEVELS)

        fun plain(sprite: Sprite, frame: Int): IntArray = buildFrame(sprite, frame, null)

        fun sky(w: Int, h: Int, top: Int, bottom: Int): IntArray {
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
            return px
        }

        fun stars(w: Int, g: Int) {
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
        }

        fun glow(level: Int, color: Int): Glow {
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
            val img = Glow(px, w, h, rx, ry)
            glowImgs[level] = img
            return img
        }
    }

    /**
     * The picture of HearthScene as one ARGB array of device size (0 where nothing was drawn). [t] is the
     * clock after the REDUCED halving; [particles] is `anim == FULL`.
     */
    fun render(
        sprites: SpriteSource, widthPx: Int, heightPx: Int, state: HearthState, progress: Float?, mood: MascotMood?,
        stokeBoost: Float, celebration: Boolean, t: Long, particles: Boolean, palette: ScenePalette,
    ): IntArray {
        val canvas = RefCanvas(widthPx, heightPx)
        val run = Run(sprites, canvas, palette)
        run.drawHearth(
            widthPx.toFloat(), heightPx.toFloat(), t, state, progress, mood, particles,
            palette.skyTop, palette.skyBottom, palette.farRamp, palette.glow, stokeBoost.coerceIn(0f, 1.5f), celebration,
        )
        return canvas.pixels
    }

    private fun Run.drawHearth(
        wpx: Float, hpx: Float, t: Long, state: HearthState, progress: Float?, moodOverride: MascotMood?,
        particles: Boolean, skyTop: Int, skyBottom: Int, farRamp: IntArray, glowColor: Int,
        stokeBoost: Float, celebration: Boolean,
    ) {
        val s = this
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
        canvas.image(sky(wl, g + 8, skyTop, skyBottom), wl, g + 8, ox, oy, p, 1f)
        stars(wl, g)
        for (k in starX.indices) {
            val tw = (sin(t * 0.0014f * starRate[k] + starPhase[k]) + 1f) * 0.5f
            val lvl = if (tw < 0.35f) 0 else if (tw < 0.75f) 1 else 2
            val col = if (lvl == 0) STAR_DIM else if (lvl == 1) STAR_MID else STAR_BRIGHT
            val x = ox + starX[k] * p
            val y = oy + starY[k] * p
            canvas.rect(col, x.toFloat(), y.toFloat(), pf, pf)
            if (k % 9 == 0 && lvl > 0) {
                canvas.rect(STAR_CROSS, x - pf, y.toFloat(), pf, pf); canvas.rect(STAR_CROSS, x + pf, y.toFloat(), pf, pf)
                canvas.rect(STAR_CROSS, x.toFloat(), y - pf, pf, pf); canvas.rect(STAR_CROSS, x.toFloat(), y + pf, pf, pf)
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
        canvas.image(plain(s.moon, 0), s.moon.width, s.moon.height, ox + moonX * p, oy + moonY * p, p, 1f)
        canvas.image(buildFrame(s.far, 0, farRamp), s.far.width, s.far.height, ox + artX * p, oy + (g - 38) * p, p, 1f)
        canvas.image(plain(s.near, 0), s.near.width, s.near.height, ox + artX * p, oy + (g - 14) * p, p, 1f)
        if (state == HearthState.BLAZING && particles) {
            drawDistantNewrozFires(t, ox, oy, p, artX, g)
        }
        canvas.image(plain(s.ground, 0), s.ground.width, s.ground.height, ox + artX * p, oy + g * p, p, 1f)

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
            val img = glow(lvl, glowColor)
            val cx = fx
            val cy = fbase - 8
            canvas.image(img.px, img.w, img.h, ox + (cx - img.halfW) * p, oy + (cy - img.halfH) * p, p, (0.55f + 0.45f * eff).coerceAtMost(1f))
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
        canvas.image(plain(pet, pet.frameAt(petT)), pet.width, pet.height, ox + (fx - 43) * p, oy + (fbase - 31) * p, p, 1f)
        canvas.image(plain(fire, fire.frameAt(t)), fire.width, fire.height, ox + (fx - 16) * p, oy + (fbase - 35) * p, p, 1f)

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
                val high = if (celebration) 0xFFFEBD11.toInt() else palette.fireHigh
                val mid = if (celebration) 0xFF278E43.toInt() else palette.fireMid
                val low = if (celebration) 0xFFED1C24.toInt() else palette.fireLow
                drawEmbers(
                    s.embers, count, t, pf,
                    originX = (ox + fx * p).toFloat(), originY = (oy + (fbase - 12 - (14f * intensity).toInt()) * p).toFloat(),
                    halfWidthCells = 5f + 4f * intensity + boost * 3f, riseCells = 20f + 26f * intensity + boost * 14f,
                    high = high, mid = mid, low = low,
                    strength = (0.5f + 0.5f * intensity + boost * 0.25f).coerceAtMost(1f),
                )
            }
            if (state == HearthState.BLAZING || state == HearthState.KINDLING) {
                drawFireflies(t, ox, oy, p, wl, g)
            }
        }

        // ---- foreground grass ----
        canvas.image(plain(s.fore, 0), s.fore.width, s.fore.height, ox + artX * p, oy + (hl - 11) * p, p, 1f)
    }

    /** Soft stepped pixel glow around the crescent moon. */
    private fun Run.drawMoonHalo(ox: Int, oy: Int, p: Int, cx: Int, cy: Int) {
        val pf = p.toFloat()
        val halo = 0xFFFFF2C8.toInt()
        for (dy in -10..10) {
            for (dx in -10..10) {
                val d = sqrt((dx * dx + dy * dy).toFloat())
                if (d > 10.5f || d < 4.2f) continue
                val norm = 1f - (d - 4.2f) / 6.3f
                if (norm > bayer(cx + dx, cy + dy) * 1.15f) {
                    canvas.rect(Argb.withAlpha(halo, 0.08f + norm * 0.09f), (ox + (cx + dx) * p).toFloat(), (oy + (cy + dy) * p).toFloat(), pf, pf)
                }
            }
        }
    }

    /** Tiny warm distant Newroz bonfires twinkling along the far Zagros hillside ridge when blazing. */
    private fun Run.drawDistantNewrozFires(t: Long, ox: Int, oy: Int, p: Int, artX: Int, g: Int) {
        val pf = p.toFloat()
        val spots = intArrayOf(34, -2, 74, -4, 152, -3)
        var i = 0
        while (i < spots.size) {
            val sx = artX + spots[i]
            val sy = g + spots[i + 1]
            val flick = (sin(t * 0.008f + i * 2.1f) + 1f) * 0.5f
            canvas.rect(Argb.withAlpha(palette.fireLow, 0.25f + flick * 0.2f), (ox + (sx - 1) * p).toFloat(), (oy + sy * p).toFloat(), pf * 3f, pf)
            canvas.rect(if (flick > 0.45f) palette.fireHigh else palette.fireMid, (ox + sx * p).toFloat(), (oy + sy * p).toFloat(), pf, pf)
            i += 2
        }
    }

    /** Gentle pulsing golden alpine fireflies hovering over the meadow flanks. */
    private fun Run.drawFireflies(t: Long, ox: Int, oy: Int, p: Int, wl: Int, g: Int) {
        val pf = p.toFloat()
        val base = 0xFFFFEA8A.toInt()
        for (k in 0 until 6) {
            val anchorX = if (k < 3) (wl * (0.10f + k * 0.09f)) else (wl * (0.80f + (k - 3) * 0.06f))
            val anchorY = g + 6f + (k % 3) * 5f
            val fx = (anchorX + sin(t * 0.0007f + k * 1.9f) * 5.5f).roundToInt()
            val fy = (anchorY + sin(t * 0.0011f + k * 2.7f) * 2.8f).roundToInt()
            val pulse = (sin(t * 0.0022f + k * 1.4f) + 1f) * 0.5f
            if (pulse < 0.25f) continue
            val a = ((pulse - 0.25f) / 0.75f).coerceIn(0f, 1f)
            canvas.rect(Argb.withAlpha(palette.fireMid, a * 0.25f), (ox + (fx - 1) * p).toFloat(), (oy + fy * p).toFloat(), pf * 3f, pf)
            canvas.rect(Argb.withAlpha(base, a * 0.9f), (ox + fx * p).toFloat(), (oy + fy * p).toFloat(), pf, pf)
        }
    }

    /** Occasional quiet pixel shooting star streaking across the upper night sky every ~14 seconds. */
    private fun Run.drawShootingStar(t: Long, ox: Int, oy: Int, p: Int, wl: Int, g: Int) {
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
            canvas.rect(Argb.withAlpha(col, ta), (ox + tx * p).toFloat(), (oy + ty * p).toFloat(), pf, pf)
        }
    }

    /** Grey puffs rising from the coals: octagons of 1-4 logical px radius that grow, drift right and fade. */
    private fun Run.drawSmoke(t: Long, n: Int, strong: Boolean, ox: Int, oy: Int, p: Int, fx: Int, fy: Int) {
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
            val c = Argb.withAlpha(SMOKE_COLOR, a)
            val xi = floor(xs).toInt()
            val yi = floor(ys).toInt()
            canvas.rect(c, (ox + (xi - r + 1) * p).toFloat(), (oy + (yi - r) * p).toFloat(), (2 * r - 1) * pf, pf)
            canvas.rect(c, (ox + (xi - r) * p).toFloat(), (oy + (yi - r + 1) * p).toFloat(), (2 * r + 1) * pf, (2 * r - 1) * pf)
            canvas.rect(c, (ox + (xi - r + 1) * p).toFloat(), (oy + (yi + r) * p).toFloat(), (2 * r - 1) * pf, pf)
        }
    }

    private fun Run.drawEmbers(
        set: LegacyEmberSet, count: Int, t: Long, unit: Float,
        originX: Float, originY: Float, halfWidthCells: Float, riseCells: Float,
        high: Int, mid: Int, low: Int, strength: Float = 1f,
    ) {
        val n = if (count < set.capacity) count else set.capacity
        for (i in 0 until n) {
            if (((t / 70L + i * 5L) % 9L).toInt() == 0) continue
            val u = ((t.toFloat() / set.periodMs[i]) + set.phase[i]) % 1f
            val rise = floor(u * riseCells)
            val sway = sin(u * 6.2832f * set.swayFreq[i] + set.phase[i] * 6.2832f) * set.swayAmp[i] * (0.3f + u)
            val dx = floor(set.spawnX[i] * halfWidthCells + sway + set.drift[i] * u * riseCells * 0.25f)
            var a = strength
            if (u > 0.7f) a *= (1f - u) / 0.3f
            if (u < 0.06f) a *= u / 0.06f
            if (a <= 0.02f) continue
            val base = if (u < 0.3f) high else if (u < 0.65f) mid else low
            val cells = if (set.big[i] && u < 0.5f) 2f else 1f
            canvas.rect(Argb.withAlpha(base, a), originX + dx * unit, originY - rise * unit, unit * cells, unit * cells)
        }
    }
}
