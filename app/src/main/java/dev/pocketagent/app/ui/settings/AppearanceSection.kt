package dev.pocketagent.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import dev.pocketagent.app.ui.text.t
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconView
import dev.pocketagent.app.ui.components.PixelPanel
import dev.pocketagent.app.ui.components.PocketChip
import dev.pocketagent.app.ui.components.PocketSwitchRow
import dev.pocketagent.app.ui.pixel.PixelImage
import dev.pocketagent.app.ui.theme.AccentId
import dev.pocketagent.app.ui.theme.AnimLevel
import dev.pocketagent.app.ui.theme.AppIconManager
import dev.pocketagent.app.ui.theme.AppIconStyle
import dev.pocketagent.app.ui.theme.AppearanceStore
import dev.pocketagent.app.ui.theme.BubbleStyle
import dev.pocketagent.app.ui.theme.DarkMode
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.ThemeId
import dev.pocketagent.app.ui.theme.paletteFor
import dev.pocketagent.app.voice.SoundCue
import kotlin.math.roundToInt
import dev.pocketagent.app.ui.theme.Space
import dev.pocketagent.app.ui.theme.Corner

/** Theme cards with a live mini preview, accent swatches, dark mode, text size, animations, mascot, bubbles, fonts. */
@Composable
internal fun AppearanceSection() {
    val ctx = LocalContext.current
    val store = remember { AppearanceStore.get(ctx) }
    val ap by store.appearance.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val dark = when (ap.darkMode) { DarkMode.DARK -> true; DarkMode.LIGHT -> false; DarkMode.SYSTEM -> systemDark }
    val c = Pocket.colors

    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        // ---- themes ----
        CText(t(R.string.ctl_ap_theme), size = 14.sp, weight = FontWeight.SemiBold)
        ThemeId.entries.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { id ->
                    ThemeCard(id, dark, ap.accent, selected = ap.theme == id, modifier = Modifier.weight(1f)) { store.update { it.copy(theme = id) } }
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }

        // ---- accent ----
        CText(t(R.string.ctl_ap_accent), size = 14.sp, weight = FontWeight.SemiBold)
        AccentId.entries.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                row.forEach { a ->
                    val swatch = paletteFor(ap.theme, dark, a).accent
                    val on = ap.accent == a
                    val label = t(accentLabel(a))
                    Column(
                        Modifier.weight(1f).selectable(selected = on, role = Role.RadioButton) { store.update { it.copy(accent = a) } }.padding(vertical = Space.xs),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        Box(
                            Modifier.size(44.dp).background(swatch).border(if (on) 3.dp else 1.dp, if (on) c.text else c.outline),
                            contentAlignment = Alignment.Center,
                        ) { if (on) PixelIconView(PixelIcon.CHECK, tint = paletteFor(ap.theme, dark, a).onAccent, size = 22.dp) }
                        CText(label, size = 11.sp, color = if (on) c.text else c.textMuted, maxLines = 1)
                    }
                }
            }
        }

        // ---- dark mode ----
        CText(t(R.string.ctl_ap_dark), size = 14.sp, weight = FontWeight.SemiBold)
        ChipRow {
            PocketChip(t(R.string.ctl_ap_dark_system), ap.darkMode == DarkMode.SYSTEM, { store.update { it.copy(darkMode = DarkMode.SYSTEM) } })
            PocketChip(t(R.string.ctl_ap_dark_dark), ap.darkMode == DarkMode.DARK, { store.update { it.copy(darkMode = DarkMode.DARK) } })
            PocketChip(t(R.string.ctl_ap_dark_light), ap.darkMode == DarkMode.LIGHT, { store.update { it.copy(darkMode = DarkMode.LIGHT) } })
        }

        // ---- text size ----
        var scale by remember(ap.textScale) { mutableFloatStateOf(ap.textScale) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CText(t(R.string.ctl_ap_text_size), size = 14.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Muted("${(scale * 100).roundToInt()}%")
        }
        Slider(
            value = scale, onValueChange = { scale = it },
            onValueChangeFinished = { val v = (scale * 20).roundToInt() / 20f; store.update { it.copy(textScale = v) } },
            valueRange = 0.85f..1.3f, steps = 8,
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.outline),
        )
        PixelPanel(Modifier.fillMaxWidth(), color = c.bgDeep) {
            CText(t(R.string.ctl_ap_sample), size = 15.sp, modifier = Modifier.padding(Space.m))
        }

        // ---- animations ----
        CText(t(R.string.ctl_ap_anim), size = 14.sp, weight = FontWeight.SemiBold)
        ChipRow {
            PocketChip(t(R.string.ctl_ap_anim_full), ap.animations == AnimLevel.FULL, { store.update { it.copy(animations = AnimLevel.FULL) } })
            PocketChip(t(R.string.ctl_ap_anim_reduced), ap.animations == AnimLevel.REDUCED, { store.update { it.copy(animations = AnimLevel.REDUCED) } })
            PocketChip(t(R.string.ctl_ap_anim_off), ap.animations == AnimLevel.OFF, { store.update { it.copy(animations = AnimLevel.OFF) } })
        }

        // ---- bubbles ----
        CText(t(R.string.ctl_ap_bubbles), size = 14.sp, weight = FontWeight.SemiBold)
        ChipRow {
            PocketChip(t(R.string.ctl_ap_bubble_pixel), ap.bubbleStyle == BubbleStyle.PIXEL, { store.update { it.copy(bubbleStyle = BubbleStyle.PIXEL) } })
            PocketChip(t(R.string.ctl_ap_bubble_soft), ap.bubbleStyle == BubbleStyle.SOFT, { store.update { it.copy(bubbleStyle = BubbleStyle.SOFT) } })
        }

        PocketSwitchRow(t(R.string.ctl_ap_mascot), ap.showMascot, { v -> store.update { it.copy(showMascot = v) } }, subtitle = t(R.string.ctl_ap_mascot_sub))
        PocketSwitchRow(t(R.string.ctl_ap_pixel_font), ap.pixelFontTitles, { v -> store.update { it.copy(pixelFontTitles = v) } }, subtitle = t(R.string.ctl_ap_pixel_font_sub))

        // ---- customizable app launcher icon ----
        var activeIcon by remember { mutableStateOf(AppIconManager.current(ctx)) }
        val switchedMsg = t(R.string.ctl_icon_switched)
        CText(t(R.string.ctl_ap_icon), size = 14.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(top = Space.xs))
        Muted(t(R.string.ctl_ap_icon_sub), size = 12.sp)
        AppIconStyle.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                row.forEach { style ->
                    val on = activeIcon == style
                    PixelPanel(
                        Modifier
                            .weight(1f)
                            .selectable(selected = on, role = Role.RadioButton) {
                                if (!on && AppIconManager.apply(ctx, style)) {
                                    activeIcon = style
                                    PocketApp.from(ctx).sounds.play(SoundCue.TAP)
                                    toast(ctx, switchedMsg)
                                }
                            },
                        color = if (on) c.surfaceHigh else c.bgDeep,
                        border = if (on) c.accent else c.outline,
                        corner = Corner.chip,
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = Space.s, horizontal = Space.xs),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(if (on) 2.dp else 1.dp, if (on) c.accent else c.outline, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                PixelImage(style.bgSprite, Modifier.size(54.dp))
                                PixelImage(style.fgSprite, Modifier.size(44.dp))
                            }
                            CText(
                                t(iconLabel(style)),
                                size = 11.5.sp,
                                weight = if (on) FontWeight.Bold else FontWeight.Medium,
                                color = if (on) c.accent else c.text,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun iconLabel(style: AppIconStyle): Int = when (style) {
    AppIconStyle.NEWROZ -> R.string.ctl_icon_newroz
    AppIconStyle.PET -> R.string.ctl_icon_pet
    AppIconStyle.FLAME -> R.string.ctl_icon_flame
    AppIconStyle.ZAGROS -> R.string.ctl_icon_zagros
    AppIconStyle.KAWA -> R.string.ctl_icon_kawa
    AppIconStyle.ROJ -> R.string.ctl_icon_roj
}

private fun accentLabel(a: AccentId): Int = when (a) {
    AccentId.THEME -> R.string.ctl_accent_theme
    AccentId.FLAME -> R.string.ctl_accent_flame
    AccentId.SAFFRON -> R.string.ctl_accent_saffron
    AccentId.MEADOW -> R.string.ctl_accent_meadow
    AccentId.SKY -> R.string.ctl_accent_sky
    AccentId.ROSE -> R.string.ctl_accent_rose
    AccentId.NEWT -> R.string.ctl_accent_newt
    AccentId.SNOW -> R.string.ctl_accent_snow
}

internal fun themeNameRes(id: ThemeId): Int = when (id) {
    ThemeId.NEWROZ -> R.string.eng_theme_newroz
    ThemeId.ZAGROS -> R.string.eng_theme_zagros
    ThemeId.SEMENDER -> R.string.eng_theme_semender
    ThemeId.NERGIZ -> R.string.eng_theme_nergiz
    ThemeId.KAWA -> R.string.eng_theme_kawa
    ThemeId.ROJ -> R.string.eng_theme_roj
}

@Composable
internal fun themeName(id: ThemeId): String = t(themeNameRes(id))

private fun themeDesc(id: ThemeId): Int = when (id) {
    ThemeId.NEWROZ -> R.string.ctl_theme_newroz
    ThemeId.ZAGROS -> R.string.ctl_theme_zagros
    ThemeId.SEMENDER -> R.string.ctl_theme_semender
    ThemeId.NERGIZ -> R.string.ctl_theme_nergiz
    ThemeId.KAWA -> R.string.ctl_theme_kawa
    ThemeId.ROJ -> R.string.ctl_theme_roj
}

/** One theme: a tiny chat mock painted with that theme's own palette, plus its name and mood. */
@Composable
private fun ThemeCard(id: ThemeId, dark: Boolean, accent: AccentId, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = paletteFor(id, dark, accent)
    val c = Pocket.colors
    val name = themeName(id)
    PixelPanel(
        modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        color = p.surface, border = if (selected) c.accent else c.outline,
    ) {
        Column(Modifier.fillMaxWidth().padding(Space.s), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.fillMaxWidth().background(p.bg).padding(6.dp), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Box(Modifier.width(58.dp).height(16.dp).background(p.botBubble), contentAlignment = Alignment.CenterStart) {
                    CText(t(R.string.eng_preview_hi), color = p.onBotBubble, size = 8.sp, modifier = Modifier.padding(horizontal = Space.xs))
                }
                Box(Modifier.align(Alignment.End).width(44.dp).height(16.dp).background(p.userBubble), contentAlignment = Alignment.CenterStart) {
                    CText(t(R.string.eng_preview_hello), color = p.onUserBubble, size = 8.sp, modifier = Modifier.padding(horizontal = Space.xs))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.size(8.dp).background(p.fireLow)); Box(Modifier.size(8.dp).background(p.fireMid))
                    Box(Modifier.size(8.dp).background(p.fireHigh)); Box(Modifier.size(8.dp).background(p.accent))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    CText(name, color = p.text, size = 14.sp, weight = FontWeight.SemiBold)
                    CText(t(themeDesc(id)), color = p.textMuted, size = 11.sp)
                }
                if (selected) PixelIconView(PixelIcon.CHECK, tint = c.accent, size = 20.dp)
            }
        }
    }
}

/** System / English / Türkçe / Français; applies immediately. */
@Composable
internal fun LanguageSection() {
    val ctx = LocalContext.current
    val store = remember { AppearanceStore.get(ctx) }
    val ap by store.appearance.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Muted(t(R.string.ctl_lang_intro))
        ChoiceRow(ap.language == "", t(R.string.ctl_lang_system), { store.update { it.copy(language = "") } }, subtitle = t(R.string.ctl_lang_system_sub))
        ChoiceRow(ap.language == "en", "English", { store.update { it.copy(language = "en") } })
        ChoiceRow(ap.language == "tr", "Türkçe", { store.update { it.copy(language = "tr") } })
        ChoiceRow(ap.language == "fr", "Français", { store.update { it.copy(language = "fr") } })
    }
}
