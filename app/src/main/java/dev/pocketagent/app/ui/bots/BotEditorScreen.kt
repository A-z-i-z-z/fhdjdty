package dev.pocketagent.app.ui.bots

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.data.Bot
import dev.pocketagent.app.data.BotPresets
import dev.pocketagent.app.data.BotVoiceStyle
import dev.pocketagent.app.runtime.ProviderConfig
import dev.pocketagent.app.ui.LocalNavigator
import dev.pocketagent.app.ui.components.ButtonKind
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconButton
import dev.pocketagent.app.ui.components.PixelPanel
import dev.pocketagent.app.ui.components.PocketChip
import dev.pocketagent.app.ui.components.PocketTextField
import dev.pocketagent.app.ui.components.PocketTopBar
import dev.pocketagent.app.ui.components.SectionHeader
import dev.pocketagent.app.ui.pixel.Avatar
import dev.pocketagent.app.ui.pixel.AvatarId
import dev.pocketagent.app.ui.pixel.avatarIdOf
import dev.pocketagent.app.ui.settings.CText
import dev.pocketagent.app.ui.settings.CtlCard
import dev.pocketagent.app.ui.settings.ChipRow
import dev.pocketagent.app.ui.settings.ConfirmDialog
import dev.pocketagent.app.ui.settings.EFFORT_CHIPS
import dev.pocketagent.app.ui.settings.Muted
import dev.pocketagent.app.ui.settings.mainVm
import dev.pocketagent.app.ui.settings.toast
import dev.pocketagent.app.ui.theme.AppearanceStore
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.voice.SUPERTONIC_VOICES
import dev.pocketagent.app.voice.VoiceOverride
import kotlin.math.roundToInt

/** 16 hand-picked colours (ARGB). The first three are the colours of the Kurdish flag. */
private val SWATCHES = listOf(
    0xFFED1C24, 0xFF278E43, 0xFFFEBD11, 0xFFE8661C,
    0xFFC8452D, 0xFFE0607E, 0xFFA23B8C, 0xFF8E5BD0,
    0xFF5B6CE0, 0xFF4A9BE0, 0xFF2FA8A0, 0xFF5FA85A,
    0xFFA9C23F, 0xFFB5835A, 0xFF6E7F99, 0xFFE6ECF2,
)

/** Sentences added to the personality text by the helper chips (instructions for the model, so they stay English). */
private val INSERTS = listOf(
    R.string.ctl_ins_turkish to "Always answer in Turkish, in plain and friendly language.",
    R.string.ctl_ins_kurdish to "Answer in Kurdish (Kurmanji) when the user writes in Kurdish.",
    R.string.ctl_ins_concise to "Be concise: short answers first, details only when asked.",
    R.string.ctl_ins_emoji to "Use emojis sparingly, at most one per message.",
    R.string.ctl_ins_steps to "Explain things step by step and avoid jargon.",
    R.string.ctl_ins_ask to "If a request is unclear, ask one short question before acting.",
)

private const val DEFAULT_COLOR = 0xFFE8661CL

private fun Long.toColor() = Color(this)
private fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

@Composable
fun BotEditorScreen(botId: String?) {
    val ctx = LocalContext.current
    val repo = remember { PocketApp.from(ctx).chat }
    // After a process restore the bots file may still be loading: resolving the bot now would wrongly say "missing".
    val loaded by repo.loaded.collectAsState()
    if (!loaded) {
        val nav = LocalNavigator.current
        Column(Modifier.fillMaxSize().background(Pocket.colors.bg)) {
            PocketTopBar(title = stringResource(R.string.ctl_bot_edit_title), onBack = { nav.back() })
            Muted(stringResource(R.string.ctl_loading), Modifier.padding(16.dp))
        }
        return
    }
    BotEditorContent(botId)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BotEditorContent(botId: String?) {
    val ctx = LocalContext.current
    val nav = LocalNavigator.current
    val app = remember { PocketApp.from(ctx) }
    val repo = app.chat
    val vm = mainVm()
    val c = Pocket.colors
    val ap by AppearanceStore.get(ctx).appearance.collectAsState()
    val speakingId by app.voice.speakingId.collectAsState()
    val existing: Bot? = remember(botId) { botId?.let { repo.bot(it) } }

    var name by rememberSaveable(botId) { mutableStateOf(existing?.name ?: "") }
    var tagline by rememberSaveable(botId) { mutableStateOf(existing?.tagline ?: "") }
    var avatar by rememberSaveable(botId) { mutableStateOf(existing?.avatar?.let { avatarIdOf(it).name } ?: AvatarId.NEWT.name) }
    var colorArgb by rememberSaveable(botId) { mutableStateOf(existing?.color ?: DEFAULT_COLOR) }
    var prompt by rememberSaveable(botId) { mutableStateOf(existing?.systemPrompt ?: "") }
    var model by rememberSaveable(botId) { mutableStateOf(existing?.model ?: "") }
    var effort by rememberSaveable(botId) { mutableStateOf(existing?.effort ?: "") }
    var voiceSid by rememberSaveable(botId) { mutableIntStateOf(existing?.voiceSid ?: -1) }
    var voiceStyleName by rememberSaveable(botId) { mutableStateOf((existing?.voiceStyle ?: BotVoiceStyle.DEFAULT).name) }
    var voiceSpeed by rememberSaveable(botId) { mutableFloatStateOf(existing?.voiceSpeed ?: 0f) }
    val voiceStyle = remember(voiceStyleName) { runCatching { BotVoiceStyle.valueOf(voiceStyleName) }.getOrDefault(BotVoiceStyle.DEFAULT) }
    var mono by rememberSaveable { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    val dirty = if (existing == null) {
        name.isNotBlank() || tagline.isNotBlank() || prompt.isNotBlank() || model.isNotBlank() || effort.isNotBlank() ||
            voiceSid != -1 || voiceStyle != BotVoiceStyle.DEFAULT || voiceSpeed > 0f
    } else {
        name != existing.name || tagline != existing.tagline || avatar != avatarIdOf(existing.avatar).name ||
            colorArgb != existing.color || prompt != existing.systemPrompt || model != existing.model || effort != existing.effort ||
            voiceSid != existing.voiceSid || voiceStyle != existing.voiceStyle || voiceSpeed != existing.voiceSpeed
    }
    val avatarId = avatarIdOf(avatar)
    val color = colorArgb.toColor()
    val isHermes = existing?.id == BotPresets.HERMES_ID
    val missing = botId != null && existing == null
    val savedMsg = stringResource(R.string.ctl_bot_saved)
    val duplicatedMsg = stringResource(R.string.ctl_bot_duplicated)

    fun leave() { if (dirty) showDiscard = true else nav.back() }
    BackHandler(enabled = dirty) { showDiscard = true }

    fun build(): Bot {
        val base = existing ?: Bot(
            id = repo.newId(), name = "", avatar = AvatarId.NEWT.name, color = DEFAULT_COLOR, tagline = "", systemPrompt = "",
            builtIn = false, createdAt = System.currentTimeMillis(),
        )
        return base.copy(
            name = name.trim(), avatar = avatarId.name, color = colorArgb, tagline = tagline.trim(),
            systemPrompt = prompt, model = model.trim(), effort = effort,
            voiceSid = voiceSid, voiceStyle = voiceStyle, voiceSpeed = voiceSpeed,
        )
    }

    Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        PocketTopBar(
            title = stringResource(if (existing == null) R.string.ctl_bot_new_title else R.string.ctl_bot_edit_title),
            subtitle = if (dirty) stringResource(R.string.ctl_unsaved) else null,
            onBack = { leave() },
            actions = {
                PixelIconButton(
                    PixelIcon.CHECK, onClick = { repo.saveBot(build()); toast(ctx, savedMsg); nav.back() },
                    contentDescription = stringResource(R.string.ctl_save), tint = if (name.isNotBlank() && dirty) c.accent else c.textMuted,
                    enabled = name.isNotBlank() && dirty,
                )
            },
        )
        if (missing) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CText(stringResource(R.string.ctl_bot_missing))
                PixelButton(stringResource(R.string.ctl_back), onClick = { nav.back() }, kind = ButtonKind.SECONDARY)
            }
        } else Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- live preview ----
            PixelPanel(Modifier.fillMaxWidth(), color = c.bgDeep) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(avatarId, color, size = 64.dp, animated = true)
                        Column(Modifier.weight(1f)) {
                            CText(name.ifBlank { stringResource(R.string.ctl_bot_name_placeholder) }, size = 20.sp, weight = FontWeight.Bold,
                                color = if (name.isBlank()) c.textMuted else c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Muted(tagline.ifBlank { stringResource(R.string.ctl_bot_tagline_placeholder) }, size = 13.sp)
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Avatar(avatarId, color, size = 28.dp)
                        PixelPanel(Modifier.weight(1f), color = c.botBubble) {
                            CText(
                                stringResource(R.string.ctl_bot_preview_hi, name.ifBlank { stringResource(R.string.ctl_bot_name_placeholder) }) +
                                    if (tagline.isNotBlank()) " " + tagline else "",
                                color = c.onBotBubble, size = 14.sp, modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }
            }

            // ---- identity ----
            SectionHeader(stringResource(R.string.ctl_bot_section_identity))
            PocketTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), label = stringResource(R.string.ctl_bot_name), singleLine = true)
            PocketTextField(tagline, { tagline = it.take(80) }, Modifier.fillMaxWidth(), label = stringResource(R.string.ctl_bot_tagline),
                placeholder = stringResource(R.string.ctl_bot_tagline_hint), singleLine = true)

            CtlCard {
                CText(stringResource(R.string.ctl_bot_avatar), size = 14.sp, weight = FontWeight.SemiBold)
                AvatarGrid(color, avatarId) { avatar = it.name }
            }
            CtlCard {
                CText(stringResource(R.string.ctl_bot_color), size = 14.sp, weight = FontWeight.SemiBold)
                ColorPicker(colorArgb) { colorArgb = it }
            }

            // ---- personality ----
            SectionHeader(stringResource(R.string.ctl_bot_section_personality))
            Muted(stringResource(R.string.ctl_bot_prompt_help))
            ChipRow {
                INSERTS.forEach { (label, text) ->
                    PocketChip(stringResource(label), selected = prompt.contains(text), onClick = {
                        prompt = if (prompt.contains(text)) prompt.replace(text, "").replace("\n\n\n", "\n\n").trim()
                        else (prompt.trimEnd() + (if (prompt.isBlank()) "" else "\n") + text)
                    }, icon = PixelIcon.SPARK)
                }
            }
            PocketTextField(prompt, { prompt = it }, Modifier.fillMaxWidth(), label = stringResource(R.string.ctl_bot_prompt),
                placeholder = stringResource(R.string.ctl_bot_prompt_hint), minLines = 8, monospace = mono)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Muted(stringResource(R.string.ctl_bot_chars, prompt.length), Modifier.weight(1f), size = 12.sp)
                PocketChip(stringResource(R.string.ctl_bot_mono), selected = mono, onClick = { mono = !mono })
            }

            // ---- voice & delivery mood ----
            SectionHeader(stringResource(R.string.ctl_bot_section_voice))
            Muted(stringResource(R.string.ctl_bot_voice_help))
            CtlCard {
                CText(stringResource(R.string.ctl_bot_voice_female), size = 12.5.sp, weight = FontWeight.SemiBold, color = c.textMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PocketChip(stringResource(R.string.effort_default), selected = voiceSid == -1, onClick = { voiceSid = -1 })
                    SUPERTONIC_VOICES.filter { it.sid in 0..4 }.forEach { v ->
                        PocketChip(v.label, selected = voiceSid == v.sid, onClick = { voiceSid = v.sid })
                    }
                }
                CText(stringResource(R.string.ctl_bot_voice_male), size = 12.5.sp, weight = FontWeight.SemiBold, color = c.textMuted, modifier = Modifier.padding(top = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SUPERTONIC_VOICES.filter { it.sid in 5..9 }.forEach { v ->
                        PocketChip(v.label, selected = voiceSid == v.sid, onClick = { voiceSid = v.sid })
                    }
                }
                CText(stringResource(R.string.ctl_bot_voice_mood), size = 13.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val moods = listOf(
                        BotVoiceStyle.DEFAULT to R.string.ctl_bot_mood_default,
                        BotVoiceStyle.HAPPY to R.string.ctl_bot_mood_happy,
                        BotVoiceStyle.EXCITED to R.string.ctl_bot_mood_excited,
                        BotVoiceStyle.SERIOUS to R.string.ctl_bot_mood_serious,
                        BotVoiceStyle.CALM to R.string.ctl_bot_mood_calm,
                        BotVoiceStyle.STORYTELLER to R.string.ctl_bot_mood_storyteller,
                    )
                    moods.forEach { (st, lbl) ->
                        PocketChip(stringResource(lbl), selected = voiceStyle == st, onClick = { voiceStyleName = st.name })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    CText(stringResource(R.string.ctl_voice_speed), size = 13.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Muted(if (voiceSpeed <= 0f) stringResource(R.string.effort_default) else "${"%.2f".format(voiceSpeed)}×")
                }
                Slider(
                    value = if (voiceSpeed <= 0f) 1.0f else voiceSpeed,
                    onValueChange = { v -> voiceSpeed = (v * 20f).roundToInt() / 20f },
                    valueRange = 0.85f..1.35f,
                    steps = 9,
                    colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.outline),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val previewing = speakingId == "bot-sample"
                    PixelButton(
                        stringResource(if (previewing) R.string.btn_stop else R.string.ctl_bot_voice_test),
                        onClick = {
                            if (previewing) app.voice.stop()
                            else app.voice.speakBotSample(name, ap.language, VoiceOverride(voiceSid, voiceStyle, voiceSpeed))
                        },
                        modifier = Modifier.weight(1f),
                        icon = if (previewing) PixelIcon.STOP else PixelIcon.VOLUME,
                        kind = ButtonKind.SECONDARY,
                    )
                    if (voiceSpeed > 0f) {
                        PixelButton(
                            stringResource(R.string.ui_ctrl_reset),
                            onClick = { voiceSpeed = 0f },
                            kind = ButtonKind.GHOST,
                        )
                    }
                }
            }

            // ---- model ----
            SectionHeader(stringResource(R.string.ctl_bot_section_model))
            Muted(stringResource(R.string.ctl_bot_model_help))
            PocketTextField(model, { model = it }, Modifier.fillMaxWidth(), label = stringResource(R.string.ctl_bot_model),
                placeholder = stringResource(R.string.ctl_bot_model_default), singleLine = true, monospace = true)
            val suggestions = remember(vm.authMode, vm.endpoint) { ProviderConfig.suggestions(vm.authMode, vm.endpoint) }
            ChipRow {
                PocketChip(stringResource(R.string.effort_default), selected = model.isBlank(), onClick = { model = "" })
                suggestions.forEach { id -> PocketChip(id, selected = model == id, onClick = { model = id }) }
            }
            CText(stringResource(R.string.ctl_bot_effort), size = 14.sp, weight = FontWeight.SemiBold)
            ChipRow {
                EFFORT_CHIPS.forEach { (value, label) -> PocketChip(stringResource(label), selected = effort == value, onClick = { effort = value }) }
            }
            Muted(stringResource(R.string.ctl_bot_effort_help), size = 12.sp)

            // ---- actions ----
            PixelButton(
                stringResource(R.string.ctl_save), onClick = { repo.saveBot(build()); toast(ctx, savedMsg); nav.back() },
                modifier = Modifier.fillMaxWidth(), icon = PixelIcon.CHECK, enabled = name.isNotBlank() && dirty,
            )
            if (name.isBlank()) Muted(stringResource(R.string.ctl_bot_name_required), size = 12.sp)
            if (existing != null) {
                PixelButton(
                    stringResource(R.string.ctl_bot_duplicate),
                    onClick = {
                        val copy = build().copy(id = repo.newId(), name = name.trim() + " " + ctx.getString(R.string.ctl_bot_copy_suffix), builtIn = false, createdAt = System.currentTimeMillis())
                        repo.saveBot(copy); toast(ctx, duplicatedMsg); nav.back()
                    },
                    modifier = Modifier.fillMaxWidth(), icon = PixelIcon.COPY, kind = ButtonKind.SECONDARY,
                )
                if (!isHermes) {
                    PixelButton(
                        stringResource(R.string.ctl_bot_delete), onClick = { showDelete = true },
                        modifier = Modifier.fillMaxWidth(), icon = PixelIcon.TRASH, kind = ButtonKind.DANGER,
                    )
                } else Muted(stringResource(R.string.ctl_bot_hermes_note), size = 12.sp)
            }
            Box(Modifier.size(24.dp))
        }
    }

    if (showDiscard) ConfirmDialog(
        title = stringResource(R.string.ctl_discard_title), text = stringResource(R.string.ctl_discard_body),
        confirmLabel = stringResource(R.string.ctl_discard), danger = true,
        onConfirm = { showDiscard = false; nav.back() }, onDismiss = { showDiscard = false },
    )
    if (showDelete && existing != null) ConfirmDialog(
        title = stringResource(R.string.ctl_bot_delete_title, existing.name), text = stringResource(R.string.ctl_bot_delete_body),
        confirmLabel = stringResource(R.string.ctl_bot_delete), danger = true,
        onConfirm = { showDelete = false; repo.deleteBot(existing.id); nav.back() }, onDismiss = { showDelete = false },
    )
}

/** All 16 avatars in a 4-column grid, drawn with the current colour. */
@Composable
private fun AvatarGrid(color: Color, selected: AvatarId, onPick: (AvatarId) -> Unit) {
    val c = Pocket.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AvatarId.entries.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { id ->
                    val on = id == selected
                    Box(
                        Modifier.weight(1f).height(60.dp).background(if (on) c.accentSoft else c.surfaceHigh)
                            .border(if (on) 3.dp else 1.dp, if (on) c.accent else c.outline)
                            .clickable(onClickLabel = id.name.lowercase().replaceFirstChar { it.uppercase() }) { onPick(id) }
                            .semantics { contentDescription = id.name.lowercase().replaceFirstChar { it.uppercase() } },
                        contentAlignment = Alignment.Center,
                    ) { Avatar(id, color, size = 44.dp) }
                }
            }
        }
    }
}

/** 16 swatches plus a hue slider for everything in between. */
@Composable
private fun ColorPicker(current: Long, onPick: (Long) -> Unit) {
    val c = Pocket.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SWATCHES.chunked(8).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { argb ->
                    val on = argb == current
                    val desc = stringResource(R.string.ctl_bot_color_n, SWATCHES.indexOf(argb) + 1)
                    Box(
                        Modifier.weight(1f).height(40.dp).background(Color(argb))
                            .border(if (on) 3.dp else 1.dp, if (on) c.text else c.outline)
                            .clickable(onClickLabel = desc) { onPick(argb) }
                            .semantics { contentDescription = desc },
                    )
                }
            }
        }
        val hsv = remember(current) { FloatArray(3).also { android.graphics.Color.colorToHSV(Color(current).toArgb(), it) } }
        Muted(stringResource(R.string.ctl_bot_hue), size = 12.sp)
        Slider(
            value = hsv[0], onValueChange = { h -> onPick(Color.hsv(h.coerceIn(0f, 359.9f), 0.65f, 0.92f).toArgbLong()) },
            valueRange = 0f..360f,
            colors = SliderDefaults.colors(thumbColor = Color(current), activeTrackColor = Color(current), inactiveTrackColor = c.outline),
        )
    }
}
