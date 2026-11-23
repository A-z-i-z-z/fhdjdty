package dev.pocketagent.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.ui.components.ButtonKind
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconView
import dev.pocketagent.app.ui.components.PocketChip
import dev.pocketagent.app.ui.components.PocketSwitchRow
import dev.pocketagent.app.ui.theme.AppearanceStore
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.voice.SUPERTONIC_VOICES
import dev.pocketagent.app.voice.SoundCue
import dev.pocketagent.app.voice.SoundPack
import dev.pocketagent.app.voice.SupertonicModelStore
import dev.pocketagent.app.voice.TtsEngineMode
import dev.pocketagent.app.voice.TtsModelState
import kotlin.math.roundToInt

/**
 * Settings > Voice & Sound:
 * - On-device Supertonic 3 INT8 neural TTS downloader, status, voice picker (F1-F5 / M1-M5), speed & quality steps.
 * - Curated CC0 Sound Effects pack picker (Hearth / Studio / Silent), volume & preview cues.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VoiceSoundSection() {
    val ctx = LocalContext.current
    val app = remember { PocketApp.from(ctx) }
    val prefs = app.voicePrefs
    val settings by prefs.settings.collectAsState()
    val modelState by app.ttsModels.state.collectAsState()
    val speakingId by app.voice.speakingId.collectAsState()
    val synthesizing by app.voice.synthesizing.collectAsState()
    val voiceError by app.voice.lastError.collectAsState()
    val ap by AppearanceStore.get(ctx).appearance.collectAsState()
    val c = Pocket.colors
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { app.ttsModels.refresh() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ---- Supertonic 3 INT8 Neural TTS card ----
        CText(stringResource(R.string.ctl_voice_neural_title), size = 14.sp, weight = FontWeight.SemiBold)
        CtlCard(color = c.bgDeep, border = if (modelState is TtsModelState.Installed) c.success else c.outline) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelIconView(
                    PixelIcon.VOLUME,
                    tint = if (modelState is TtsModelState.Installed) c.success else c.accent,
                    size = 20.dp,
                )
                Column(Modifier.weight(1f)) {
                    CText("Supertonic 3 INT8 (99M)", size = 14.sp, weight = FontWeight.Bold)
                    Muted(stringResource(R.string.ctl_voice_neural_sub), size = 12.sp)
                }
            }
            when (val ms = modelState) {
                TtsModelState.NotInstalled -> {
                    Muted(stringResource(R.string.ctl_voice_not_installed, SupertonicModelStore.APPROX_TOTAL_MB), size = 12.sp)
                    PixelButton(
                        stringResource(R.string.ctl_voice_download, SupertonicModelStore.APPROX_TOTAL_MB),
                        onClick = { app.ttsModels.startDownload() },
                        modifier = Modifier.fillMaxWidth(),
                        icon = PixelIcon.DOWN,
                    )
                }
                is TtsModelState.Downloading -> {
                    val mbDone = ms.downloadedBytes / (1024f * 1024f)
                    val mbTotal = ms.totalBytes / (1024f * 1024f)
                    CText(
                        stringResource(R.string.ctl_voice_downloading, ms.fileIndex, ms.fileCount, mbDone.roundToInt(), mbTotal.roundToInt()),
                        size = 12.sp,
                        mono = true,
                    )
                    LinearProgressIndicator(
                        progress = { ms.fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = c.accent,
                        trackColor = c.surfaceHigh,
                    )
                    PixelButton(
                        stringResource(R.string.cancel),
                        onClick = { app.ttsModels.cancelDownload() },
                        modifier = Modifier.fillMaxWidth(),
                        kind = ButtonKind.GHOST,
                    )
                }
                is TtsModelState.Installed -> {
                    val mb = (ms.totalBytes / (1024f * 1024f)).roundToInt()
                    CText(stringResource(R.string.ctl_voice_installed, mb), color = c.success, size = 12.sp, weight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val playingSample = speakingId == "sample"
                        PixelButton(
                            stringResource(
                                when {
                                    playingSample && synthesizing -> R.string.ctl_voice_synth
                                    playingSample -> R.string.btn_stop
                                    else -> R.string.ctl_voice_test
                                },
                            ),
                            onClick = {
                                if (playingSample) app.voice.stop()
                                else app.voice.speakSample(ap.language)
                            },
                            modifier = Modifier.weight(1f),
                            icon = if (playingSample) PixelIcon.STOP else PixelIcon.PLAY,
                        )
                        PixelButton(
                            stringResource(R.string.ui_act_delete),
                            onClick = { confirmDelete = true },
                            kind = ButtonKind.DANGER,
                            icon = PixelIcon.TRASH,
                        )
                    }
                }
                is TtsModelState.Error -> {
                    ErrorText(ms.message)
                    PixelButton(
                        stringResource(R.string.ui_chat_retry),
                        onClick = { app.ttsModels.startDownload() },
                        modifier = Modifier.fillMaxWidth(),
                        icon = PixelIcon.REFRESH,
                    )
                }
            }
        }

        // ---- TTS Engine preference ----
        CText(stringResource(R.string.ctl_voice_engine), size = 14.sp, weight = FontWeight.SemiBold)
        ChipRow {
            PocketChip(
                stringResource(R.string.ctl_voice_engine_auto),
                settings.ttsEngine == TtsEngineMode.AUTO,
                { prefs.update { it.copy(ttsEngine = TtsEngineMode.AUTO) } },
            )
            PocketChip(
                stringResource(R.string.ctl_voice_engine_system),
                settings.ttsEngine == TtsEngineMode.SYSTEM,
                { prefs.update { it.copy(ttsEngine = TtsEngineMode.SYSTEM) } },
            )
        }
        if (modelState !is TtsModelState.Installed || settings.ttsEngine == TtsEngineMode.SYSTEM) {
            val playingSample = speakingId == "sample"
            PixelButton(
                stringResource(
                    when {
                        playingSample && synthesizing -> R.string.ctl_voice_synth
                        playingSample -> R.string.btn_stop
                        else -> R.string.ctl_voice_test
                    },
                ),
                onClick = {
                    if (playingSample) app.voice.stop()
                    else app.voice.speakSample(ap.language)
                },
                modifier = Modifier.fillMaxWidth(),
                icon = if (playingSample) PixelIcon.STOP else PixelIcon.PLAY,
                kind = ButtonKind.SECONDARY,
            )
        }
        voiceError?.let { err -> ErrorText(err) }

        // ---- Supertonic 3 Speaker Voice ----
        CText(stringResource(R.string.ctl_voice_speaker), size = 14.sp, weight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SUPERTONIC_VOICES.forEach { v ->
                PocketChip(
                    v.label,
                    selected = settings.voiceSid == v.sid,
                    onClick = { prefs.update { it.copy(voiceSid = v.sid) } },
                )
            }
        }

        // ---- Speech Speed ----
        var speed by remember(settings.speed) { mutableFloatStateOf(settings.speed) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CText(stringResource(R.string.ctl_voice_speed), size = 14.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Muted("${"%.2f".format(speed)}×")
        }
        Slider(
            value = speed,
            onValueChange = { speed = it },
            onValueChangeFinished = {
                val snapped = (speed * 20f).roundToInt() / 20f
                prefs.update { it.copy(speed = snapped) }
            },
            valueRange = 0.85f..1.35f,
            steps = 9,
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.outline),
        )

        // ---- Quality Steps ----
        CText(stringResource(R.string.ctl_voice_quality), size = 14.sp, weight = FontWeight.SemiBold)
        ChipRow {
            PocketChip(stringResource(R.string.ctl_voice_q_fast), settings.qualitySteps <= 3, { prefs.update { it.copy(qualitySteps = 3) } })
            PocketChip(stringResource(R.string.ctl_voice_q_balanced), settings.qualitySteps in 4..6, { prefs.update { it.copy(qualitySteps = 5) } })
            PocketChip(stringResource(R.string.ctl_voice_q_high), settings.qualitySteps >= 7, { prefs.update { it.copy(qualitySteps = 8) } })
        }

        PocketSwitchRow(
            stringResource(R.string.ctl_voice_autospeak),
            settings.autoSpeak,
            { on -> prefs.update { it.copy(autoSpeak = on) } },
            subtitle = stringResource(R.string.ctl_voice_autospeak_sub),
        )

        // ---- Sound Effects Library ----
        CText(stringResource(R.string.ctl_sfx_title), size = 14.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
        Muted(stringResource(R.string.ctl_sfx_sub), size = 12.sp)
        ChipRow {
            PocketChip(
                stringResource(R.string.ctl_sfx_hearth),
                settings.soundPack == SoundPack.HEARTH,
                { prefs.update { it.copy(soundPack = SoundPack.HEARTH) }; app.sounds.play(SoundCue.COMPLETE, SoundPack.HEARTH) },
                icon = PixelIcon.HEARTH,
            )
            PocketChip(
                stringResource(R.string.ctl_sfx_studio),
                settings.soundPack == SoundPack.STUDIO,
                { prefs.update { it.copy(soundPack = SoundPack.STUDIO) }; app.sounds.play(SoundCue.COMPLETE, SoundPack.STUDIO) },
                icon = PixelIcon.MUSIC,
            )
            PocketChip(
                stringResource(R.string.ctl_sfx_off),
                settings.soundPack == SoundPack.OFF,
                { prefs.update { it.copy(soundPack = SoundPack.OFF) } },
                icon = PixelIcon.MUTE,
            )
        }

        if (settings.soundPack != SoundPack.OFF) {
            var vol by remember(settings.soundVolume) { mutableFloatStateOf(settings.soundVolume) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                CText(stringResource(R.string.ctl_sfx_volume), size = 14.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Muted("${(vol * 100).roundToInt()}%")
            }
            Slider(
                value = vol,
                onValueChange = { vol = it },
                onValueChangeFinished = {
                    val snapped = (vol * 20f).roundToInt() / 20f
                    prefs.update { it.copy(soundVolume = snapped) }
                    app.sounds.play(SoundCue.COMPLETE)
                },
                valueRange = 0.2f..1.0f,
                steps = 7,
                colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.outline),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PocketChip(stringResource(R.string.ctl_sfx_preview_done), false, { app.sounds.play(SoundCue.COMPLETE) }, icon = PixelIcon.CHECK)
                PocketChip(stringResource(R.string.ctl_sfx_preview_forge), false, { app.sounds.play(SoundCue.FORGE) }, icon = PixelIcon.FORGE)
                PocketChip(stringResource(R.string.ctl_sfx_preview_spark), false, { app.sounds.play(SoundCue.SPARK) }, icon = PixelIcon.SPARK)
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.ctl_voice_delete_title),
            text = stringResource(R.string.ctl_voice_delete_body),
            confirmLabel = stringResource(R.string.ui_dlg_delete_confirm),
            danger = true,
            onConfirm = {
                confirmDelete = false
                app.voice.releaseNeuralModel()
                app.ttsModels.deleteModel()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
