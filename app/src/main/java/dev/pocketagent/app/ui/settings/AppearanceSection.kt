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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.R
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconView
import dev.pocketagent.app.ui.components.PixelPanel
import dev.pocketagent.app.ui.components.PocketChip
import dev.pocketagent.app.ui.components.PocketSwitchRow
import dev.pocketagent.app.ui.theme.AccentId
import dev.pocketagent.app.ui.theme.AnimLevel
import dev.pocketagent.app.ui.theme.AppearanceStore
import dev.pocketagent.app.ui.theme.BubbleStyle
import dev.pocketagent.app.ui.theme.DarkMode
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.ThemeId
import dev.pocketagent.app.ui.theme.paletteFor
import kotlin.math.roundToInt

/** Theme cards with a live mini preview, accent swatches, dark mode, text size, animations, mascot, bubbles, fonts. */
@Composable
internal fun AppearanceSection() {
    val ctx = LocalContext.current
    val store = remember { AppearanceStore.get(ctx) }
    val ap by store.appearance.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val dark = when (ap.darkMode) { DarkMode.DARK -> true; DarkMode.LIGHT -> false; DarkMode.SYSTEM -> systemDark }
    val c = Pocket.colors

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ---- themes ----
        CText(stringResource(R.string.ctl_ap_theme), size = 14.sp, weight = FontWeight.SemiBold)
        ThemeId.entries.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { id ->
                    ThemeCard(id, dark, ap.accent, selected = ap.theme == id, modifier = Modifier.weight(1f)) { store.update { it.copy(theme = id) } }
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }

        // ---- accent ----
        CText(stringResource(R.string.ctl_ap_accent), size = 14.sp, weight = FontWeight.SemiBold)
        AccentId.entries.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { a ->
                    val swatch = paletteFor(ap.theme, dark, a).accent
                    val on = ap.accent == a
                    val label = stringResource(accentLabel(a))
                    Column(
                        Modifier.weight(1f).selectable(selected = on, role = Role.RadioButton) { store.update { it.copy(accent = a) } }.padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
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
        CText(stringResource(R.string.ctl_ap_dark), size = 14.sp, weight = FontWeight.SemiBold)
        ChipRow {
            PocketChip(stringResource(R.string.ctl_ap_dark_system), ap.darkMode == DarkMode.SYSTEM, { store.update { it.copy(darkMode = DarkMode.SYSTEM) } })
            PocketChip(stringResource(R.string.ctl_ap_dark_dark), ap.darkMode == DarkMode.DARK, { store.update { it.copy(darkMode = DarkMode.DARK) } })
            PocketChip(stringResource(R.string.ctl_ap_dark_light), ap.darkMode == DarkMode.LIGHT, { store.update { it.copy(darkMode = DarkMode.LIGHT) } })
        }

        // ---- text size ----
        var scale by remember(ap.textScale) { mutableFloatStateOf(ap.textScale) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CText(stringResource(R.string.ctl_ap_text_size), size = 14.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Muted("${(scale * 100).roundToInt()}%")
        }
        Slider(
            value = scale, onValueChange = { scale = it },
            onValueChangeFinished = { val v = (scale * 20).roundToInt() / 20f; store.update { it.copy(textScale = v) } },
            valueRange = 0.85f..1.3f, steps = 8,
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.outline),
        )
        PixelPanel(Modifier.fillMaxWidth(), color = c.bgDeep) {
            CText(stringResource(R.string.ctl_ap_sample), size = 15.sp, modifier = Modifier.padding(12.dp))
        }

        // ---- animations ----
        CText(stringResource(R.string.ctl_ap_anim), size = 14.sp, weight = FontWeight.SemiBold)
        ChipRow {
            PocketChip(stringResource(R.string.ctl_ap_anim_full), ap.animations == AnimLevel.FULL, { store.update { it.copy(animations = AnimLevel.FULL) } })
            PocketChip(stringResource(R.string.ctl_ap_anim_reduced), ap.animations == AnimLevel.REDUCED, { store.update { it.copy(animations = AnimLevel.REDUCED) } })
            PocketChip(stringResource(R.string.ctl_ap_anim_off), ap.animations == AnimLevel.OFF, { store.update { it.copy(animations = AnimLevel.OFF) } })
        }

        // ---- bubbles ----
        CText(stringResource(R.string.ctl_ap_bubbles), size = 14.sp, weight = FontWeight.SemiBold)
        ChipRow {
            PocketChip(stringResource(R.string.ctl_ap_bubble_pixel), ap.bubbleStyle == BubbleStyle.PIXEL, { store.update { it.copy(bubbleStyle = BubbleStyle.PIXEL) } })
            PocketChip(stringResource(R.string.ctl_ap_bubble_soft), ap.bubbleStyle == BubbleStyle.SOFT, { store.update { it.copy(bubbleStyle = BubbleStyle.SOFT) } })
        }

        PocketSwitchRow(stringResource(R.string.ctl_ap_mascot), ap.showMascot, { v -> store.update { it.copy(showMascot = v) } }, subtitle = stringResource(R.string.ctl_ap_mascot_sub))
        PocketSwitchRow(stringResource(R.string.ctl_ap_pixel_font), ap.pixelFontTitles, { v -> store.update { it.copy(pixelFontTitles = v) } }, subtitle = stringResource(R.string.ctl_ap_pixel_font_sub))
    }
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

private fun themeName(id: ThemeId): String = when (id) {
    ThemeId.NEWROZ -> "Newroz"
    ThemeId.ZAGROS -> "Zagros"
    ThemeId.SEMENDER -> "Semender"
    ThemeId.NERGIZ -> "Nergiz"
    ThemeId.KAWA -> "Kawa"
    ThemeId.ROJ -> "Roj"
}

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
        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.fillMaxWidth().background(p.bg).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.width(58.dp).height(16.dp).background(p.botBubble), contentAlignment = Alignment.CenterStart) {
                    CText("Hi!", color = p.onBotBubble, size = 8.sp, modifier = Modifier.padding(horizontal = 4.dp))
                }
                Box(Modifier.align(Alignment.End).width(44.dp).height(16.dp).background(p.userBubble), contentAlignment = Alignment.CenterStart) {
                    CText("Hello", color = p.onUserBubble, size = 8.sp, modifier = Modifier.padding(horizontal = 4.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.size(8.dp).background(p.fireLow)); Box(Modifier.size(8.dp).background(p.fireMid))
                    Box(Modifier.size(8.dp).background(p.fireHigh)); Box(Modifier.size(8.dp).background(p.accent))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    CText(name, color = p.text, size = 14.sp, weight = FontWeight.SemiBold)
                    CText(stringResource(themeDesc(id)), color = p.textMuted, size = 11.sp)
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Muted(stringResource(R.string.ctl_lang_intro))
        ChoiceRow(ap.language == "", stringResource(R.string.ctl_lang_system), { store.update { it.copy(language = "") } }, subtitle = stringResource(R.string.ctl_lang_system_sub))
        ChoiceRow(ap.language == "en", "English", { store.update { it.copy(language = "en") } })
        ChoiceRow(ap.language == "tr", "Türkçe", { store.update { it.copy(language = "tr") } })
        ChoiceRow(ap.language == "fr", "Français", { store.update { it.copy(language = "fr") } })
    }
}
