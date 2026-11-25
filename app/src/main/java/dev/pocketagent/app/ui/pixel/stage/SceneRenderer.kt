package dev.pocketagent.app.ui.pixel.stage

import dev.pocketagent.app.ui.pixel.HearthState
import dev.pocketagent.app.ui.pixel.MascotMood
import dev.pocketagent.app.ui.pixel.Sprite
import dev.pocketagent.app.ui.pixel.SpriteRaster
import dev.pocketagent.app.ui.pixel.spriteName
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draws the home scene onto a [PixelStage]: a night over the Zagros, a stone ring with a hand-keyed fire,
 * drifting embers and Pet. One logical pixel of the stage is one pixel of the art; the caller scales the stage
 * to the screen with whole numbers (see [StageGrid]). A statement-for-statement port of the old drawing code of
 * HearthScene (scale 1, no offset), so the picture is the same.
 *
 * Not thread-safe: one instance per surface, one thread at a time. The sprites are looked up once, in the
 * constructor, which throws when one is missing.
 */
class SceneRenderer(sprites: SpriteSource) {
    private val far = sprites["scene_far"]
    private val near = sprites["scene_near"]
    private val ground = sprites["scene_ground"]
    private val fore = sprites["scene_fore"]
    private val moon = sprites["scene_moon"]
    private val fireCold = sprites["fire_cold"]
    private val fireOut = sprites["fire_out"]
    private val fireLow = sprites["fire_low"]
    private val fireMid = sprites["fire_mid"]
    private val fireHigh = sprites["fire_high"]
    private val pets: List<Sprite> = MascotMood.entries.map { sprites[it.spriteName()] }
    private val embers = EmberSet(40, 21L)

    // caches that depend on the size or the palette; rebuilt only when their inputs change, never per frame
    private var skyPx: IntArray? = null
    private var skyW = 0
    private var skyH = 0
    private var skyTop = 0
    private var skyBottom = 0

    private var starsCache: StarField? = null
    private var starsW = 0
    private var starsG = 0

    private var farPx: IntArray? = null
    private var farKey: IntArray? = null

    private val glowImgs = arrayOfNulls<GlowImage>(SceneArt.GLOW_LEVELS)
    private var glowColor = 0

    /**
     * Draws one frame at time [timeMs] into [stage]. It does not clear the stage first (the caller does) and
     * draws nothing when the stage is smaller than 8 px wide or [GROUND_H] + 10 px high.
     */
    fun render(stage: PixelStage, state: SceneState, timeMs: Long, quality: SceneQuality = SceneQuality.FULL) {
        val wl = stage.width
        val hl = stage.height
        if (wl < 8 || hl < GROUND_H + 10) return
        val t = timeMs
        val particles = quality.particles
        val palette = state.palette
        val hearth = state.hearth
        val progress = state.progress
        val moodOverride = state.mood
        val celebration = state.celebration
        val stokeBoost = state.stokeBoost.coerceIn(0f, 1.5f)
        val g = hl - GROUND_H                       // top of the ground, logical y
        val artX = (wl - ART_W) / 2                  // art layers are centred on the grid

        // ---- sky and stars ----
        stage.blit(sky(wl, g + 8, palette.skyTop, palette.skyBottom), wl, g + 8, 0, 0)
        val field = starField(wl, g)
        for (k in field.x.indices) {
            val tw = (sin(t * 0.0014f * field.rate[k] + field.phase[k]) + 1f) * 0.5f
            val lvl = if (tw < 0.35f) 0 else if (tw < 0.75f) 1 else 2
            val col = if (lvl == 0) STAR_DIM else if (lvl == 1) STAR_MID else STAR_BRIGHT
            val x = field.x[k]
            val y = field.y[k]
            stage.plot(x, y, col)
            if (k % 9 == 0 && lvl > 0) {
                stage.plot(x - 1, y, STAR_CROSS); stage.plot(x + 1, y, STAR_CROSS)
                stage.plot(x, y - 1, STAR_CROSS); stage.plot(x, y + 1, STAR_CROSS)
            }
        }

        // ---- shooting star (occasional, FULL quality only) ----
        if (particles) {
            drawShootingStar(stage, t, wl, g)
        }

        // ---- moon halo, moon and ridges ----
        val moonX = (wl * 0.76f).toInt()
        val moonY = 20
        drawMoonHalo(stage, moonX + 7, moonY + 7)
        stage.blit(moon, 0, moonX, moonY)
        stage.blit(farPixels(palette.farRamp), far.width, far.height, artX, g - 38)
        stage.blit(near, 0, artX, g - 14)
        if (hearth == HearthState.BLAZING && particles) {
            drawDistantNewrozFires(stage, t, artX, g, palette)
        }
        stage.blit(ground, 0, artX, g)

        // ---- state: fire sprite, glow, mascot ----
        val fx = (wl * 0.6f).toInt()
        val fbase = hl - 3
        val intensity: Float
        val fire: Sprite
        when (hearth) {
            HearthState.COLD -> { intensity = 0f; fire = fireCold }
            HearthState.KINDLING -> {
                val pr = (progress ?: 0.5f).coerceIn(0f, 1f)
                intensity = 0.18f + 0.72f * pr
                fire = if (pr < 0.15f) fireOut else if (pr < 0.5f) fireLow else if (pr < 0.85f) fireMid else fireHigh
            }
            HearthState.BLAZING -> { intensity = 1f; fire = fireHigh }
            HearthState.SMOKE -> {
                val slot = (t / 170L).toInt()
                val on = ((slot * 7 + slot / 3) % 5 + 5) % 5 < 3
                intensity = if (on) 0.3f else 0.1f
                fire = if (on) fireLow else fireOut
            }
        }

        val boost = (stokeBoost + if (celebration) 0.5f else 0f).coerceIn(0f, 1.5f)
        if (intensity > 0.01f || boost > 0.05f) {
            val eff = (intensity + boost * 0.45f).coerceIn(0f, 1.25f)
            var lvl = (eff * (SceneArt.GLOW_LEVELS - 1)).roundToInt()
            if (particles) {
                val h = (((t / 90L).toInt() * 1103515245) shr 16) and 3
                lvl += if (h == 0) -1 else if (h == 3) 1 else 0
            }
            lvl = lvl.coerceIn(0, SceneArt.GLOW_LEVELS - 1)
            val img = glow(lvl, palette.glow)
            val cx = fx
            val cy = fbase - 8
            stage.blit(img.px, img.w, img.h, cx - img.halfW, cy - img.halfH, (0.55f + 0.45f * eff).coerceAtMost(1f))
        }

        var m = moodOverride ?: when (hearth) {
            HearthState.COLD -> MascotMood.SLEEPING
            HearthState.KINDLING -> MascotMood.THINKING
            HearthState.BLAZING -> MascotMood.IDLE
            HearthState.SMOKE -> MascotMood.ERROR
        }
        var petT = t
        if (moodOverride == null && hearth == HearthState.BLAZING && particles) {
            val ph = t % 9000L
            if (ph >= 7600L) { m = MascotMood.HAPPY; petT = ph - 7600L }
        }
        val pet = pets[m.ordinal]
        stage.blit(pet, pet.frameAt(petT), fx - 43, fbase - 31)
        stage.blit(fire, fire.frameAt(t), fx - 16, fbase - 35)

        // ---- smoke and embers (FULL quality only) ----
        if (particles) {
            when (hearth) {
                HearthState.COLD -> drawSmoke(stage, t, 2, false, fx, fbase - 8)
                HearthState.SMOKE -> drawSmoke(stage, t, 6, true, fx, fbase - 8)
                HearthState.KINDLING -> if ((progress ?: 0.5f) < 0.3f) drawSmoke(stage, t, 2, false, fx, fbase - 8)
                HearthState.BLAZING -> {}
            }
            if (hearth != HearthState.COLD || boost > 0.05f || celebration) {
                val baseCount = if (hearth == HearthState.SMOKE) 4 else (3f + 21f * intensity).roundToInt()
                val count = (baseCount + (boost * 14f).roundToInt() + if (celebration) 14 else 0).coerceAtMost(embers.capacity)
                val high = if (celebration) CELEBRATION_HIGH else palette.fireHigh
                val mid = if (celebration) CELEBRATION_MID else palette.fireMid
                val low = if (celebration) CELEBRATION_LOW else palette.fireLow
                stage.drawEmbers(
                    embers, count, t,
                    originX = fx.toFloat(), originY = (fbase - 12 - (14f * intensity).toInt()).toFloat(),
                    halfWidthCells = 5f + 4f * intensity + boost * 3f, riseCells = 20f + 26f * intensity + boost * 14f,
                    high = high, mid = mid, low = low,
                    strength = (0.5f + 0.5f * intensity + boost * 0.25f).coerceAtMost(1f),
                )
            }
            if (hearth == HearthState.BLAZING || hearth == HearthState.KINDLING) {
                drawFireflies(stage, t, wl, g, palette)
            }
        }

        // ---- foreground grass ----
        stage.blit(fore, 0, artX, hl - 11)
    }

    private fun sky(w: Int, h: Int, top: Int, bottom: Int): IntArray {
        val have = skyPx
        if (have != null && skyW == w && skyH == h && skyTop == top && skyBottom == bottom) return have
        val px = SceneArt.sky(w, h, top, bottom)
        skyPx = px; skyW = w; skyH = h; skyTop = top; skyBottom = bottom
        return px
    }

    private fun starField(w: Int, g: Int): StarField {
        val have = starsCache
        if (have != null && starsW == w && starsG == g) return have
        val field = SceneArt.stars(w, g)
        starsCache = field; starsW = w; starsG = g
        return field
    }

    private fun farPixels(ramp: IntArray): IntArray {
        val have = farPx
        val key = farKey
        if (have != null && key != null && key.contentEquals(ramp)) return have
        val px = SpriteRaster.argb(far, 0, false, 0, ramp)
        farPx = px; farKey = ramp.copyOf()
        return px
    }

    private fun glow(level: Int, color: Int): GlowImage {
        if (glowColor != color) {
            for (i in glowImgs.indices) glowImgs[i] = null
            glowColor = color
        }
        glowImgs[level]?.let { return it }
        val img = SceneArt.glow(level, color)
        glowImgs[level] = img
        return img
    }

    /** Soft stepped pixel glow around the crescent moon. */
    private fun drawMoonHalo(stage: PixelStage, cx: Int, cy: Int) {
        for (dy in -10..10) {
            for (dx in -10..10) {
                val d = sqrt((dx * dx + dy * dy).toFloat())
                if (d > 10.5f || d < 4.2f) continue
                val norm = 1f - (d - 4.2f) / 6.3f
                if (norm > SceneArt.bayer(cx + dx, cy + dy) * 1.15f) {
                    stage.plot(cx + dx, cy + dy, Argb.withAlpha(HALO, 0.08f + norm * 0.09f))
                }
            }
        }
    }

    /** Tiny warm distant Newroz bonfires twinkling along the far Zagros hillside ridge when blazing. */
    private fun drawDistantNewrozFires(stage: PixelStage, t: Long, artX: Int, g: Int, palette: ScenePalette) {
        val spots = intArrayOf(34, -2, 74, -4, 152, -3)
        var i = 0
        while (i < spots.size) {
            val sx = artX + spots[i]
            val sy = g + spots[i + 1]
            val flick = (sin(t * 0.008f + i * 2.1f) + 1f) * 0.5f
            stage.fillRect(sx - 1, sy, 3, 1, Argb.withAlpha(palette.fireLow, 0.25f + flick * 0.2f))
            stage.plot(sx, sy, if (flick > 0.45f) palette.fireHigh else palette.fireMid)
            i += 2
        }
    }

    /** Gentle pulsing golden alpine fireflies hovering over the meadow flanks. */
    private fun drawFireflies(stage: PixelStage, t: Long, wl: Int, g: Int, palette: ScenePalette) {
        for (k in 0 until 6) {
            val anchorX = if (k < 3) (wl * (0.10f + k * 0.09f)) else (wl * (0.80f + (k - 3) * 0.06f))
            val anchorY = g + 6f + (k % 3) * 5f
            val fx = (anchorX + sin(t * 0.0007f + k * 1.9f) * 5.5f).roundToInt()
            val fy = (anchorY + sin(t * 0.0011f + k * 2.7f) * 2.8f).roundToInt()
            val pulse = (sin(t * 0.0022f + k * 1.4f) + 1f) * 0.5f
            if (pulse < 0.25f) continue
            val a = ((pulse - 0.25f) / 0.75f).coerceIn(0f, 1f)
            stage.fillRect(fx - 1, fy, 3, 1, Argb.withAlpha(palette.fireMid, a * 0.25f))
            stage.plot(fx, fy, Argb.withAlpha(FIREFLY, a * 0.9f))
        }
    }

    /** Occasional quiet pixel shooting star streaking across the upper night sky every ~14 seconds. */
    private fun drawShootingStar(stage: PixelStage, t: Long, wl: Int, g: Int) {
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
        val fade = (if (u < 0.15f) u / 0.15f else (1f - u) / 0.85f).coerceIn(0f, 1f)
        for (step in 0..5) {
            val tx = headX - step * 2
            val ty = headY - (step * 2) / 3
            val ta = fade * (1f - step / 6f)
            if (ta <= 0.05f) continue
            val col = if (step == 0) STAR_BRIGHT else if (step <= 2) STAR_MID else STAR_DIM
            stage.plot(tx, ty, Argb.withAlpha(col, ta))
        }
    }

    /** Grey puffs rising from the coals: octagons of 1-4 logical px radius that grow, drift right and fade. */
    private fun drawSmoke(stage: PixelStage, t: Long, n: Int, strong: Boolean, fx: Int, fy: Int) {
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
            stage.fillRect(xi - r + 1, yi - r, 2 * r - 1, 1, c)
            stage.fillRect(xi - r, yi - r + 1, 2 * r + 1, 2 * r - 1, c)
            stage.fillRect(xi - r + 1, yi + r, 2 * r - 1, 1, c)
        }
    }

    companion object {
        const val ART_W = 176
        const val GROUND_H = 34

        private val STAR_DIM = 0xFF6E6496.toInt()
        private val STAR_MID = 0xFFBEB8DC.toInt()
        private val STAR_BRIGHT = 0xFFFFF6DC.toInt()
        private val STAR_CROSS = 0xFF4A3E6C.toInt()
        private val SMOKE_COLOR = 0xFF9A92AC.toInt()
        private val HALO = 0xFFFFF2C8.toInt()
        private val FIREFLY = 0xFFFFEA8A.toInt()
        private val CELEBRATION_HIGH = 0xFFFEBD11.toInt()
        private val CELEBRATION_MID = 0xFF278E43.toInt()
        private val CELEBRATION_LOW = 0xFFED1C24.toInt()

        /** The sprites the renderer asks its source for, in this order. */
        val REQUIRED_SPRITES: List<String> = listOf(
            "scene_far", "scene_near", "scene_ground", "scene_fore", "scene_moon",
            "fire_cold", "fire_out", "fire_low", "fire_mid", "fire_high",
        ) + MascotMood.entries.map { it.spriteName() }
    }
}
