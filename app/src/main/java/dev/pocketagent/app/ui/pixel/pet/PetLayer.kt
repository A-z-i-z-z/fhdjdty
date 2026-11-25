package dev.pocketagent.app.ui.pixel.pet

import dev.pocketagent.app.ui.pixel.PetArt
import dev.pocketagent.app.ui.pixel.PetClip
import dev.pocketagent.app.ui.pixel.PetClipKind
import dev.pocketagent.app.ui.pixel.Sprite
import dev.pocketagent.app.ui.pixel.stage.PixelStage
import dev.pocketagent.app.ui.pixel.stage.SceneGeometry
import dev.pocketagent.app.ui.pixel.stage.SpriteSource
import dev.pocketagent.app.ui.pixel.stage.StageGrid

/** Where Pêt stands and where a finger can hit it. All numbers come from [SceneGeometry] (one source). */
object PetPlacement {
    /** Pêt's sprite is 32 wide; its box starts 16 left of the feet column. */
    const val BOX = 32
    const val FEET_LEFT_OF_FIRE = 27
    const val GROW = 3

    fun feetX(geo: SceneGeometry): Int = geo.hearthX - FEET_LEFT_OF_FIRE

    fun feetY(geo: SceneGeometry): Int = geo.hearthBase

    /**
     * Device-pixel rectangle `[left, top, right, bottom]` of Pêt's touch area: the 32 by 32 box grown by 3 stage
     * pixels left, top and bottom (not right: the fire wins the overlap), and at least [minPx] wide and high. A box
     * that is too narrow grows to the left only; one that is too low grows up and down by the same amount.
     */
    fun hitRect(grid: StageGrid, geo: SceneGeometry, minPx: Float = 0f): FloatArray {
        val x0 = feetX(geo) - BOX / 2
        val y0 = feetY(geo) - (BOX - 1)
        val s = grid.scale
        var left = (grid.offsetX + (x0 - GROW) * s).toFloat()
        var top = (grid.offsetY + (y0 - GROW) * s).toFloat()
        val right = (grid.offsetX + (x0 + BOX) * s).toFloat()
        var bottom = (grid.offsetY + (y0 + BOX + GROW) * s).toFloat()
        if (right - left < minPx) left = right - minPx
        val h = bottom - top
        if (h < minPx) {
            val extra = (minPx - h) / 2f
            top -= extra
            bottom += extra
        }
        return floatArrayOf(left, top, right, bottom)
    }

    /** True when the device point lies inside [hitRect] (left and top inclusive, right and bottom exclusive). */
    fun hits(xPx: Float, yPx: Float, grid: StageGrid, geo: SceneGeometry, minPx: Float = 0f): Boolean {
        val r = hitRect(grid, geo, minPx)
        return xPx >= r[0] && xPx < r[2] && yPx >= r[1] && yPx < r[3]
    }
}

/**
 * Draws Pêt and the shadow under it into the stage. The seven classic sprites are required (a missing one throws
 * IllegalArgumentException, as the scene itself does); the others are optional: a missing or rejected clip is simply
 * absent from [available] and its animation is drawn as the animation it falls back to.
 */
class PetLayer(sprites: SpriteSource) {
    private val classic: Map<PetAnim, Sprite> = loadClassic(sprites)
    private val clips: Map<PetAnim, PetClip> = loadClips(sprites)
    private val shadow: Sprite? = findSafe(sprites, SHADOW_SPRITE)

    /** What this layer can draw: the classic seven plus every new animation whose clip was found. */
    val available: Set<PetAnim> = LinkedHashSet<PetAnim>(classic.keys).also { it.addAll(clips.keys) }

    /**
     * Draws [pose] with Pêt's feet at (feetX, feetY): the shadow first, then the pet. [timeMs] is the scene time.
     * Everything clips at the stage edge.
     */
    fun draw(stage: PixelStage, pose: PetPose, feetX: Int, feetY: Int, timeMs: Long) {
        val sh = shadow
        if (sh != null && pose.shadow >= 0) {
            val f = pose.shadow.coerceIn(0, sh.frames.size - 1)
            stage.blit(sh, f, feetX + SHADOW_DX, feetY + SHADOW_DY)
        }
        var anim = pose.anim
        while (anim != PetAnim.IDLE && anim !in available) anim = anim.fallback ?: PetAnim.IDLE
        val since = timeMs - pose.startMs
        val clip = clips[anim]
        val sprite: Sprite
        val frame: Int
        if (clip == null) {
            sprite = classic.getValue(anim)
            frame = when {
                pose.holdFrame >= 0 -> pose.holdFrame.coerceAtMost(sprite.frames.size - 1)
                anim.loops -> sprite.frameAt(since)
                else -> sprite.frameAt(since.coerceIn(0L, sprite.totalMs - 1L))
            }
        } else {
            sprite = clip.sprite
            val n = sprite.frames.size
            val ms = since.coerceAtLeast(0L)
            frame = when {
                pose.holdFrame >= 0 -> pose.holdFrame.coerceAtMost(n - 1)
                clip.spec.kind == PetClipKind.LOOP -> clip.frameAtLoop(ms).coerceIn(0, n - 1)
                anim.loops -> sprite.frameAt(ms)
                else -> (clip.frameAtShot(ms) ?: (n - 1)).coerceIn(0, n - 1)
            }
        }
        stage.blit(sprite, frame, feetX - PET_LEFT_OF_FEET, feetY - PET_ABOVE_FEET)
    }

    private companion object {
        const val SHADOW_SPRITE = "pet_shadow"
        const val SHADOW_DX = -34
        const val SHADOW_DY = -2
        const val PET_LEFT_OF_FEET = 16
        const val PET_ABOVE_FEET = 31

        /** Same as SpriteSource.find (which SCENE adds later) but works with and without it: any failure means "absent". */
        fun findSafe(sprites: SpriteSource, name: String): Sprite? =
            try {
                sprites[name]
            } catch (e: RuntimeException) {
                null
            }

        fun loadClassic(sprites: SpriteSource): Map<PetAnim, Sprite> {
            val out = LinkedHashMap<PetAnim, Sprite>()
            for (a in PetAnim.entries) if (a.isClassic) out[a] = sprites[a.sprite]
            return out
        }

        fun loadClips(sprites: SpriteSource): Map<PetAnim, PetClip> {
            val out = LinkedHashMap<PetAnim, PetClip>()
            for (a in PetAnim.entries) {
                if (a.isClassic) continue
                val clip = try {
                    PetArt.clip(a.sprite) { n -> findSafe(sprites, n) }
                } catch (e: RuntimeException) {
                    null
                }
                if (clip != null) out[a] = clip
            }
            return out
        }
    }
}
