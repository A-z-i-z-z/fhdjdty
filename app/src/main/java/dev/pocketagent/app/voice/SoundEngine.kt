package dev.pocketagent.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import dev.pocketagent.app.R
import dev.pocketagent.app.util.AppLog
import java.util.concurrent.ConcurrentHashMap

enum class SoundCue {
    /** Calm harmonic chime when an agent finishes responding (inspired by OpenCode). */
    COMPLETE,
    /** Gentle tactile whoosh/tap on sending a message. */
    SEND,
    /** Quiet wooden tick when a tool step completes. */
    TOOL,
    /** Dampened low tone on error. */
    ERROR,
    /** Soft alert chime when Hermes requests command permission. */
    APPROVAL,
    /** Warm celebratory chord on Bot Forge / Newroz overdrive. */
    FORGE,
    /** Delicate crystalline note when Hermes updates Memory. */
    MEMORY,
    /** Cozy hearth spark feedback when tapping Pet or the fire. */
    SPARK,
    /** Subtle crisp snap on copy / fork / switch. */
    TAP,
    /** Soft double-pip when voice recording begins. */
    MIC,
}

/**
 * Curated CC0 SoundPool engine (uisfx zen/organic/studio packs, ~73 KB total).
 * Respects system ringer mode, never interrupts background music, and never crashes on unavailable audio.
 */
class SoundEngine(
    private val context: Context,
    private val prefs: VoicePrefs,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val pool: SoundPool? = runCatching {
        SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
    }.getOrNull()

    private val hearthIds = ConcurrentHashMap<SoundCue, Int>()
    private val studioIds = ConcurrentHashMap<SoundCue, Int>()
    private val loaded = ConcurrentHashMap.newKeySet<Int>()

    init {
        pool?.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loaded += sampleId
        }
        loadAll()
    }

    private fun loadAll() {
        val p = pool ?: return
        val hearthMap = mapOf(
            SoundCue.COMPLETE to R.raw.sfx_hearth_complete,
            SoundCue.SEND to R.raw.sfx_hearth_send,
            SoundCue.TOOL to R.raw.sfx_hearth_tool,
            SoundCue.ERROR to R.raw.sfx_hearth_error,
            SoundCue.APPROVAL to R.raw.sfx_hearth_approval,
            SoundCue.FORGE to R.raw.sfx_hearth_forge,
            SoundCue.MEMORY to R.raw.sfx_hearth_memory,
            SoundCue.SPARK to R.raw.sfx_hearth_spark,
            SoundCue.TAP to R.raw.sfx_hearth_tap,
            SoundCue.MIC to R.raw.sfx_hearth_mic,
        )
        val studioMap = mapOf(
            SoundCue.COMPLETE to R.raw.sfx_studio_complete,
            SoundCue.SEND to R.raw.sfx_studio_send,
            SoundCue.TOOL to R.raw.sfx_studio_tool,
            SoundCue.ERROR to R.raw.sfx_studio_error,
            SoundCue.APPROVAL to R.raw.sfx_studio_approval,
            SoundCue.FORGE to R.raw.sfx_studio_forge,
            SoundCue.MEMORY to R.raw.sfx_studio_memory,
            SoundCue.SPARK to R.raw.sfx_studio_spark,
            SoundCue.TAP to R.raw.sfx_studio_tap,
            SoundCue.MIC to R.raw.sfx_studio_mic,
        )
        runCatching {
            hearthMap.forEach { (cue, res) -> hearthIds[cue] = p.load(context, res, 1) }
            studioMap.forEach { (cue, res) -> studioIds[cue] = p.load(context, res, 1) }
        }.onFailure { AppLog.w("Sound", "could not preload sounds: ${it.message}") }
    }

    fun play(cue: SoundCue, forcePack: SoundPack? = null) {
        val cfg = prefs.settings.value
        val pack = forcePack ?: cfg.soundPack
        if (pack == SoundPack.OFF) return
        if (forcePack == null && audioManager?.ringerMode == AudioManager.RINGER_MODE_SILENT) return
        val vol = cfg.soundVolume.coerceIn(0f, 1f)
        if (vol <= 0.01f) return
        val p = pool ?: return
        val sid = when (pack) {
            SoundPack.HEARTH -> hearthIds[cue]
            SoundPack.STUDIO -> studioIds[cue]
            SoundPack.OFF -> null
        } ?: return
        if (sid !in loaded) return
        val gain = when (cue) {
            SoundCue.TOOL, SoundCue.TAP -> vol * 0.65f
            SoundCue.SPARK, SoundCue.SEND -> vol * 0.78f
            else -> vol
        }
        runCatching { p.play(sid, gain, gain, 1, 0, 1f) }
    }
}
