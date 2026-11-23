package dev.pocketagent.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import dev.pocketagent.app.data.Bot
import dev.pocketagent.app.data.BotVoiceStyle
import dev.pocketagent.app.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** Per-bot voice character & delivery mood overrides. */
data class VoiceOverride(
    /** -1 = follow global default, 0..4 = female (F1..F5), 5..9 = male (M1..M5). */
    val voiceSid: Int = -1,
    val style: BotVoiceStyle = BotVoiceStyle.DEFAULT,
    /** 0f = derived from global speed + style, else custom speed in 0.85..1.35. */
    val speed: Float = 0f,
)

fun Bot?.voiceOverride(): VoiceOverride? =
    if (this == null) null
    else if (voiceSid < 0 && voiceStyle == BotVoiceStyle.DEFAULT && voiceSpeed <= 0f) null
    else VoiceOverride(voiceSid, voiceStyle, voiceSpeed)

/** Maps [BotVoiceStyle] onto natural cadence speed multiplier, pause duration (s), and system TTS pitch. */
internal data class StyleModulation(
    val speedMultiplier: Float,
    val silenceSec: Float,
    val systemPitch: Float,
)

internal fun styleModulation(style: BotVoiceStyle): StyleModulation = when (style) {
    BotVoiceStyle.DEFAULT -> StyleModulation(1.00f, 0.28f, 1.00f)
    BotVoiceStyle.CALM -> StyleModulation(0.91f, 0.40f, 0.95f)
    BotVoiceStyle.SERIOUS -> StyleModulation(0.95f, 0.22f, 0.87f)
    BotVoiceStyle.HAPPY -> StyleModulation(1.08f, 0.24f, 1.12f)
    BotVoiceStyle.EXCITED -> StyleModulation(1.16f, 0.18f, 1.18f)
    BotVoiceStyle.STORYTELLER -> StyleModulation(0.93f, 0.46f, 0.96f)
}

/**
 * Unified on-device TTS engine:
 * - Primary: Supertonic 3 INT8 via sherpa-onnx (31 languages: EN/FR/TR, 10 female/male voices, per-bot delivery moods).
 * - Automatic fallback: Android system TextToSpeech with pitch/rate modulation so speaking always works immediately.
 */
class VoiceEngine(
    private val context: Context,
    val modelStore: SupertonicModelStore,
    val prefs: VoicePrefs,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ttsLock = Any()

    @Volatile private var supertonic: OfflineTts? = null
    @Volatile private var systemTts: TextToSpeech? = null
    @Volatile private var systemTtsReady = false
    @Volatile private var activeTrack: AudioTrack? = null
    private val cancelled = AtomicBoolean(false)
    @Volatile private var speakJob: Job? = null

    private val _speakingId = MutableStateFlow<String?>(null)
    private val _synthesizing = MutableStateFlow(false)
    private val _lastError = MutableStateFlow<String?>(null)

    /** Message id currently being spoken ("sample" / "bot-sample" for previews), or null. */
    val speakingId: StateFlow<String?> = _speakingId.asStateFlow()
    /** True while Supertonic 3 is initializing/synthesizing before first audio arrives. */
    val synthesizing: StateFlow<Boolean> = _synthesizing.asStateFlow()
    /** Last user-visible TTS error, if any. */
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun isUsingSupertonic(): Boolean {
        val mode = prefs.settings.value.ttsEngine
        return mode != TtsEngineMode.SYSTEM && modelStore.isInstalled()
    }

    /** Speaks [rawMarkdown] tagged with [id]. Tapping the same [id] while speaking toggles Stop. */
    fun toggleSpeak(id: String, rawMarkdown: String, appLang: String, override: VoiceOverride? = null) {
        if (_speakingId.value == id) {
            stop()
            return
        }
        speak(id, rawMarkdown, appLang, override)
    }

    fun speak(id: String, rawMarkdown: String, appLang: String, override: VoiceOverride? = null) {
        stop()
        val clean = TtsTextCleaner.cleanForSpeech(rawMarkdown)
        if (clean.isBlank()) return
        val cfg = prefs.settings.value
        val mod = styleModulation(override?.style ?: BotVoiceStyle.DEFAULT)
        val effectiveSid = if (override != null && override.voiceSid >= 0) override.voiceSid else cfg.voiceSid
        val baseSpeed = if (override != null && override.speed > 0f) override.speed else cfg.speed
        val effectiveSpeed = (baseSpeed * mod.speedMultiplier).coerceIn(0.75f, 1.50f)
        val resolvedLang = appLang.ifBlank { Locale.getDefault().language }
        val lang = TtsTextCleaner.detectSupertonicLang(clean, resolvedLang)
        _lastError.value = null
        cancelled.set(false)
        _speakingId.value = id

        val useNeural = cfg.ttsEngine != TtsEngineMode.SYSTEM && modelStore.isInstalled()
        if (useNeural) {
            speakJob = scope.launch {
                var handedToSystem = false
                try {
                    _synthesizing.value = true
                    speakWithSupertonic(clean, lang, effectiveSid, effectiveSpeed, mod.silenceSec, cfg.qualitySteps)
                } catch (e: Throwable) {
                    if (!cancelled.get()) {
                        AppLog.e("TTS", "Supertonic synthesis failed, falling back to system TTS", e)
                        _lastError.value = e.message ?: e.javaClass.simpleName
                        handedToSystem = true
                        speakWithSystem(id, clean, lang, effectiveSpeed, mod.systemPitch)
                    }
                } finally {
                    if (!handedToSystem) {
                        _synthesizing.value = false
                        if (_speakingId.value == id) _speakingId.value = null
                    }
                }
            }
        } else {
            speakWithSystem(id, clean, lang, effectiveSpeed, mod.systemPitch)
        }
    }

    fun speakSample(appLang: String) {
        val code = appLang.ifBlank { Locale.getDefault().language }.lowercase(Locale.ROOT).take(2)
        val sample = when (code) {
            "tr" -> "Merhaba! Ben Agirî. Newroz ateşi yanıyor, tüm sesler doğrudan cihazında üretiliyor."
            "fr" -> "Bonjour ! Je suis Agirî. Le feu de Newroz brûle, et cette voix est synthétisée hors ligne sur votre téléphone."
            else -> "Hello! I am Agirî. The Newroz hearth is blazing, and this neural voice is running offline right on your phone."
        }
        speak("sample", sample, code)
    }

    fun speakBotSample(botName: String, appLang: String, override: VoiceOverride) {
        val name = botName.trim().ifBlank { "Agirî" }
        val code = appLang.ifBlank { Locale.getDefault().language }.lowercase(Locale.ROOT).take(2)
        val sample = when (code) {
            "tr" -> when (override.style) {
                BotVoiceStyle.HAPPY -> "Harika! Ben $name, seninle konuşmak çok keyifli!"
                BotVoiceStyle.EXCITED -> "Hey! Ben $name! Hadi harika bir şeyler yapalım!"
                BotVoiceStyle.SERIOUS -> "Ben $name. Net, doğrudan ve dikkatli çalışırım."
                BotVoiceStyle.CALM -> "Merhaba, ben $name. Sakin ol, adım adım halledelim."
                BotVoiceStyle.STORYTELLER -> "Ateşin başına hoş geldin. Ben $name, dağların eski hikâyelerini getirdim."
                BotVoiceStyle.DEFAULT -> "Merhaba, ben $name. Sana nasıl yardımcı olabilirim?"
            }
            "fr" -> when (override.style) {
                BotVoiceStyle.HAPPY -> "Super ! Je suis $name, ravi de discuter avec toi !"
                BotVoiceStyle.EXCITED -> "Salut ! Je suis $name ! Allons créer quelque chose d'incroyable !"
                BotVoiceStyle.SERIOUS -> "Je suis $name. Précis, direct et concentré sur les faits."
                BotVoiceStyle.CALM -> "Bonjour, je suis $name. Prenons notre temps, étape par étape."
                BotVoiceStyle.STORYTELLER -> "Bienvenue près du feu. Je suis $name, gardien des récits des montagnes."
                BotVoiceStyle.DEFAULT -> "Bonjour, je suis $name. Comment puis-je vous aider ?"
            }
            else -> when (override.style) {
                BotVoiceStyle.HAPPY -> "Wonderful! I am $name, so happy to chat with you today!"
                BotVoiceStyle.EXCITED -> "Hey! I am $name! Let's build something extraordinary right now!"
                BotVoiceStyle.SERIOUS -> "I am $name. Direct, focused, and grounded in facts."
                BotVoiceStyle.CALM -> "Hello, I am $name. Take a breath, we will solve this step by step."
                BotVoiceStyle.STORYTELLER -> "Welcome by the Newroz fire. I am $name, keeper of the mountain tales."
                BotVoiceStyle.DEFAULT -> "Hello, I am $name. How can I help you today?"
            }
        }
        speak("bot-sample", sample, code, override)
    }

    fun stop() {
        cancelled.set(true)
        speakJob?.cancel()
        speakJob = null
        activeTrack?.let { tr ->
            runCatching {
                if (tr.playState == AudioTrack.PLAYSTATE_PLAYING) tr.pause()
                tr.flush()
                tr.stop()
                tr.release()
            }
        }
        activeTrack = null
        runCatching { systemTts?.stop() }
        _synthesizing.value = false
        _speakingId.value = null
    }

    fun releaseNeuralModel() {
        stop()
        synchronized(ttsLock) {
            runCatching { supertonic?.release() }
            supertonic = null
        }
    }

    private fun ensureSupertonic(): OfflineTts {
        supertonic?.let { return it }
        synchronized(ttsLock) {
            supertonic?.let { return it }
            val dir = modelStore.modelDir
            val sup = OfflineTtsSupertonicModelConfig(
                durationPredictor = File(dir, "duration_predictor.int8.onnx").absolutePath,
                textEncoder = File(dir, "text_encoder.int8.onnx").absolutePath,
                vectorEstimator = File(dir, "vector_estimator.int8.onnx").absolutePath,
                vocoder = File(dir, "vocoder.int8.onnx").absolutePath,
                ttsJson = File(dir, "tts.json").absolutePath,
                unicodeIndexer = File(dir, "unicode_indexer.bin").absolutePath,
                voiceStyle = File(dir, "voice.bin").absolutePath,
            )
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    supertonic = sup,
                    numThreads = threads,
                    debug = false,
                    provider = "cpu",
                ),
            )
            val t0 = System.currentTimeMillis()
            val engine = OfflineTts(assetManager = null, config = config)
            AppLog.i("TTS", "Supertonic 3 loaded in ${System.currentTimeMillis() - t0} ms (sr=${engine.sampleRate()}, speakers=${engine.numSpeakers()})")
            supertonic = engine
            return engine
        }
    }

    private fun speakWithSupertonic(
        cleanText: String,
        lang: String,
        voiceSid: Int,
        speed: Float,
        silenceSec: Float,
        qualitySteps: Int,
    ) {
        val tts = ensureSupertonic()
        if (cancelled.get()) return
        val sr = tts.sampleRate().coerceAtLeast(16_000)
        val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            .coerceAtLeast(sr)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sr)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        activeTrack = track
        try {
            track.play()
            val chunks = TtsTextCleaner.splitSentences(cleanText, maxChunkChars = 220)
            val sid = voiceSid.coerceIn(0, (tts.numSpeakers() - 1).coerceAtLeast(0))
            val genCfg = GenerationConfig(
                sid = sid,
                speed = speed,
                numSteps = qualitySteps,
                extra = mapOf(
                    "lang" to lang,
                    "speed" to speed.toString(),
                    "num_steps" to qualitySteps.toString(),
                    "silence_duration" to silenceSec.toString(),
                ),
            )
            for (chunk in chunks) {
                if (cancelled.get()) break
                val t0 = System.currentTimeMillis()
                // Use generateWithConfig (no JNI Function1 callback) to stay immune to Kotlin 2.x
                // invokedynamic lambda bridge erasure mismatches in sherpa-onnx's native GetMethodID.
                val audio = tts.generateWithConfig(chunk, genCfg)
                val synthMs = System.currentTimeMillis() - t0
                if (cancelled.get()) break
                val samples = audio.samples
                val audioSec = samples.size.toFloat() / sr.toFloat()
                if (audioSec > 0.05f) {
                    AppLog.i(
                        "TTS",
                        String.format(
                            Locale.US,
                            "Supertonic chunk (%s, sid=%d, spd=%.2f): %.2fs in %dms (RTF=%.2f)",
                            lang, sid, speed, audioSec, synthMs, (synthMs / 1000f) / audioSec,
                        ),
                    )
                }
                if (samples.isNotEmpty()) {
                    _synthesizing.value = false
                    var offset = 0
                    val frameStep = 4096
                    while (offset < samples.size && !cancelled.get()) {
                        val n = minOf(frameStep, samples.size - offset)
                        track.write(samples, offset, n, AudioTrack.WRITE_BLOCKING)
                        offset += n
                    }
                }
            }
            if (!cancelled.get()) {
                runCatching { Thread.sleep(160) }
            }
        } finally {
            runCatching {
                track.stop()
                track.release()
            }
            if (activeTrack === track) activeTrack = null
        }
    }

    private fun speakWithSystem(id: String, text: String, lang: String, speed: Float, pitch: Float) {
        val locale = when (lang) {
            "tr" -> Locale("tr", "TR")
            "fr" -> Locale.FRENCH
            else -> Locale.US
        }
        val runSpeak: (TextToSpeech) -> Unit = { tts ->
            if (!cancelled.get()) {
                tts.language = locale
                tts.setSpeechRate(speed)
                tts.setPitch(pitch)
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { _synthesizing.value = false }
                    override fun onDone(utteranceId: String?) { if (_speakingId.value == id) _speakingId.value = null }
                    @Deprecated("Deprecated in Android API")
                    override fun onError(utteranceId: String?) { if (_speakingId.value == id) _speakingId.value = null }
                })
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            }
        }
        val existing = systemTts
        if (existing != null && systemTtsReady) {
            runSpeak(existing)
            return
        }
        _synthesizing.value = true
        var created: TextToSpeech? = null
        created = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                systemTtsReady = true
                created?.let {
                    systemTts = it
                    runSpeak(it)
                }
            } else {
                _synthesizing.value = false
                if (_speakingId.value == id) _speakingId.value = null
            }
        }
    }
}
