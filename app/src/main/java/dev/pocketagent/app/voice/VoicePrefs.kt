package dev.pocketagent.app.voice

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TtsEngineMode { AUTO, SUPERTONIC, SYSTEM }

enum class SoundPack { HEARTH, STUDIO, OFF }

/** Supertonic 3 preset speaker styles (0..9 -> F1..F5, M1..M5). */
data class VoiceStyleInfo(val sid: Int, val code: String, val label: String)

val SUPERTONIC_VOICES: List<VoiceStyleInfo> = listOf(
    VoiceStyleInfo(0, "F1", "Roza (F1)"),
    VoiceStyleInfo(1, "F2", "Nergiz (F2)"),
    VoiceStyleInfo(2, "F3", "Zilan (F3)"),
    VoiceStyleInfo(3, "F4", "Sarya (F4)"),
    VoiceStyleInfo(4, "F5", "Berfin (F5)"),
    VoiceStyleInfo(5, "M1", "Kawa (M1)"),
    VoiceStyleInfo(6, "M2", "Aram (M2)"),
    VoiceStyleInfo(7, "M3", "Baran (M3)"),
    VoiceStyleInfo(8, "M4", "Diyar (M4)"),
    VoiceStyleInfo(9, "M5", "Cem (M5)"),
)

@Immutable
data class VoiceSoundSettings(
    val ttsEngine: TtsEngineMode = TtsEngineMode.AUTO,
    val voiceSid: Int = 0,
    val speed: Float = 1.05f,
    val qualitySteps: Int = 5,
    val autoSpeak: Boolean = false,
    val soundPack: SoundPack = SoundPack.HEARTH,
    val soundVolume: Float = 0.7f,
)

class VoicePrefs private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val settings: StateFlow<VoiceSoundSettings> = _state.asStateFlow()

    fun update(change: (VoiceSoundSettings) -> VoiceSoundSettings) {
        val next = sanitize(change(_state.value))
        if (next == _state.value) return
        _state.value = next
        prefs.edit()
            .putString(K_ENGINE, next.ttsEngine.name)
            .putInt(K_SID, next.voiceSid)
            .putFloat(K_SPEED, next.speed)
            .putInt(K_STEPS, next.qualitySteps)
            .putBoolean(K_AUTO, next.autoSpeak)
            .putString(K_PACK, next.soundPack.name)
            .putFloat(K_VOL, next.soundVolume)
            .apply()
    }

    private fun load(): VoiceSoundSettings {
        val d = VoiceSoundSettings()
        return sanitize(
            VoiceSoundSettings(
                ttsEngine = parse(prefs.getString(K_ENGINE, null), d.ttsEngine),
                voiceSid = prefs.getInt(K_SID, d.voiceSid),
                speed = prefs.getFloat(K_SPEED, d.speed),
                qualitySteps = prefs.getInt(K_STEPS, d.qualitySteps),
                autoSpeak = prefs.getBoolean(K_AUTO, d.autoSpeak),
                soundPack = parse(prefs.getString(K_PACK, null), d.soundPack),
                soundVolume = prefs.getFloat(K_VOL, d.soundVolume),
            ),
        )
    }

    private fun sanitize(s: VoiceSoundSettings): VoiceSoundSettings = s.copy(
        voiceSid = s.voiceSid.coerceIn(0, SUPERTONIC_VOICES.lastIndex),
        speed = (if (s.speed.isNaN()) 1.05f else s.speed).coerceIn(0.8f, 1.4f),
        qualitySteps = s.qualitySteps.coerceIn(2, 10),
        soundVolume = (if (s.soundVolume.isNaN()) 0.7f else s.soundVolume).coerceIn(0f, 1f),
    )

    private inline fun <reified E : Enum<E>> parse(name: String?, def: E): E =
        if (name == null) def else enumValues<E>().firstOrNull { it.name == name } ?: def

    companion object {
        private const val PREFS = "voice_sound"
        private const val K_ENGINE = "ttsEngine"
        private const val K_SID = "voiceSid"
        private const val K_SPEED = "speed"
        private const val K_STEPS = "qualitySteps"
        private const val K_AUTO = "autoSpeak"
        private const val K_PACK = "soundPack"
        private const val K_VOL = "soundVolume"

        @Volatile private var instance: VoicePrefs? = null
        fun get(context: Context): VoicePrefs =
            instance ?: synchronized(this) {
                instance ?: VoicePrefs(context.applicationContext).also { instance = it }
            }
    }
}
