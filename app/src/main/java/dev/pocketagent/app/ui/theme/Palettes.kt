package dev.pocketagent.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Every colour the UI uses. Semantics worth knowing:
 *  - accent: fill colour of primary buttons / switches / selected chips (always paired with onAccent).
 *  - accentSoft: the accent as TEXT or ICON colour on bg/surface (>= 4.5:1). Lighter than accent in dark
 *    variants, deeper than accent in light variants.
 *  - fireLow/fireMid/fireHigh/ember: warm fire ramp, the same hue family in every theme.
 *  - codeBg is chosen so that plain `text` is readable on it.
 *  - error/success/warning are readable as text on bg and surface.
 */
@Immutable
data class PocketColors(
    val isDark: Boolean,
    val bg: Color, val bgDeep: Color, val surface: Color, val surfaceHigh: Color, val outline: Color,
    val text: Color, val textMuted: Color, val accent: Color, val accentSoft: Color, val onAccent: Color,
    val fireLow: Color, val fireMid: Color, val fireHigh: Color, val ember: Color,
    val userBubble: Color, val onUserBubble: Color, val botBubble: Color, val onBotBubble: Color,
    val toolBubble: Color, val onToolBubble: Color, val codeBg: Color, val error: Color, val success: Color, val warning: Color,
)

/**
 * Resolves the palette for a theme in its dark or light variant, with an optional accent override.
 * Palettes were contrast-checked (text >= 7:1 on bg, body/muted text >= 4.5:1 on every surface,
 * onAccent >= 4.5:1 on accent, bubbles >= 4.5:1) for all 6 themes x 2 variants x 8 accents.
 */
fun paletteFor(theme: ThemeId, dark: Boolean, accent: AccentId): PocketColors {
    val base = when (theme) {
        ThemeId.NEWROZ -> if (dark) Palettes.newrozDark else Palettes.newrozLight
        ThemeId.ZAGROS -> if (dark) Palettes.zagrosDark else Palettes.zagrosLight
        ThemeId.SEMENDER -> if (dark) Palettes.semenderDark else Palettes.semenderLight
        ThemeId.NERGIZ -> if (dark) Palettes.nergizDark else Palettes.nergizLight
        ThemeId.KAWA -> if (dark) Palettes.kawaDark else Palettes.kawaLight
        ThemeId.ROJ -> if (dark) Palettes.rojDark else Palettes.rojLight
    }
    val set = when (accent) {
        AccentId.THEME -> return base
        AccentId.FLAME -> if (dark) Palettes.flameDark else Palettes.flameLight
        AccentId.SAFFRON -> if (dark) Palettes.saffronDark else Palettes.saffronLight
        AccentId.MEADOW -> if (dark) Palettes.meadowDark else Palettes.meadowLight
        AccentId.SKY -> if (dark) Palettes.skyDark else Palettes.skyLight
        AccentId.ROSE -> if (dark) Palettes.roseDark else Palettes.roseLight
        AccentId.NEWT -> if (dark) Palettes.newtDark else Palettes.newtLight
        AccentId.SNOW -> if (dark) Palettes.snowDark else Palettes.snowLight
    }
    return base.copy(
        accent = set.accent, accentSoft = set.soft, onAccent = set.onAccent,
        userBubble = set.userBubble, onUserBubble = set.onUserBubble,
    )
}

/** Accent / soft (text-safe) / onAccent / user bubble / text on user bubble. */
private class AccentSet(val accent: Color, val soft: Color, val onAccent: Color, val userBubble: Color, val onUserBubble: Color)

private object Palettes {
    val newrozDark = PocketColors(
        isDark = true,
        bg = Color(0xFF1D1220),
        bgDeep = Color(0xFF130A16),
        surface = Color(0xFF2B1C2D),
        surfaceHigh = Color(0xFF3A2739),
        outline = Color(0xFF6A4A62),
        text = Color(0xFFF7EAD3),
        textMuted = Color(0xFFBFA9A2),
        accent = Color(0xFFF28A2E),
        accentSoft = Color(0xFFFFC46B),
        onAccent = Color(0xFF2B1206),
        fireLow = Color(0xFFC9561B),
        fireMid = Color(0xFFF28A2E),
        fireHigh = Color(0xFFFFD76A),
        ember = Color(0xFFFF7A2E),
        userBubble = Color(0xFF9C3E12),
        onUserBubble = Color(0xFFFFF1DC),
        botBubble = Color(0xFF2D2030),
        onBotBubble = Color(0xFFF7EAD3),
        toolBubble = Color(0xFF1F1B33),
        onToolBubble = Color(0xFFCFC8EC),
        codeBg = Color(0xFF120A15),
        error = Color(0xFFFF8272),
        success = Color(0xFF9ED67F),
        warning = Color(0xFFFFD05E),
    )

    val newrozLight = PocketColors(
        isDark = false,
        bg = Color(0xFFFCF3E4),
        bgDeep = Color(0xFFF3E4CB),
        surface = Color(0xFFFFFAF0),
        surfaceHigh = Color(0xFFF7E9D2),
        outline = Color(0xFFC9A987),
        text = Color(0xFF2A1A24),
        textMuted = Color(0xFF6B5560),
        accent = Color(0xFFE5701F),
        accentSoft = Color(0xFFA8470E),
        onAccent = Color(0xFF2B1206),
        fireLow = Color(0xFFC9561B),
        fireMid = Color(0xFFE8741C),
        fireHigh = Color(0xFFF2B233),
        ember = Color(0xFFE8501C),
        userBubble = Color(0xFFF7CF9E),
        onUserBubble = Color(0xFF2A1A24),
        botBubble = Color(0xFFFFF8EA),
        onBotBubble = Color(0xFF2A1A24),
        toolBubble = Color(0xFFE9E4F2),
        onToolBubble = Color(0xFF3B3358),
        codeBg = Color(0xFFF0E0C6),
        error = Color(0xFFB3261E),
        success = Color(0xFF2E7D32),
        warning = Color(0xFF8A5A00),
    )

    val zagrosDark = PocketColors(
        isDark = true,
        bg = Color(0xFF0F1724),
        bgDeep = Color(0xFF0A101A),
        surface = Color(0xFF182235),
        surfaceHigh = Color(0xFF22304A),
        outline = Color(0xFF4A6286),
        text = Color(0xFFE8EEF7),
        textMuted = Color(0xFFA3B4CC),
        accent = Color(0xFFF2B01E),
        accentSoft = Color(0xFFFFD36B),
        onAccent = Color(0xFF1B1405),
        fireLow = Color(0xFFC4571A),
        fireMid = Color(0xFFF0912B),
        fireHigh = Color(0xFFFFD27A),
        ember = Color(0xFFFF8A3A),
        userBubble = Color(0xFF34507F),
        onUserBubble = Color(0xFFF2F6FC),
        botBubble = Color(0xFF1C2940),
        onBotBubble = Color(0xFFE8EEF7),
        toolBubble = Color(0xFF17303A),
        onToolBubble = Color(0xFFC3E3EA),
        codeBg = Color(0xFF0A111C),
        error = Color(0xFFFF8585),
        success = Color(0xFF7FD6A0),
        warning = Color(0xFFFFC857),
    )

    val zagrosLight = PocketColors(
        isDark = false,
        bg = Color(0xFFEEF3F9),
        bgDeep = Color(0xFFE0E8F2),
        surface = Color(0xFFF9FBFE),
        surfaceHigh = Color(0xFFE3EBF6),
        outline = Color(0xFF9DB0CB),
        text = Color(0xFF14202F),
        textMuted = Color(0xFF4F6178),
        accent = Color(0xFFE9A10F),
        accentSoft = Color(0xFF8A5A00),
        onAccent = Color(0xFF1B1405),
        fireLow = Color(0xFFC4571A),
        fireMid = Color(0xFFE07A1C),
        fireHigh = Color(0xFFF0B030),
        ember = Color(0xFFE0601E),
        userBubble = Color(0xFFC9D9F2),
        onUserBubble = Color(0xFF14202F),
        botBubble = Color(0xFFF9FBFE),
        onBotBubble = Color(0xFF14202F),
        toolBubble = Color(0xFFD9ECEF),
        onToolBubble = Color(0xFF1E3D45),
        codeBg = Color(0xFFDDE6F1),
        error = Color(0xFFB3261E),
        success = Color(0xFF1F7A45),
        warning = Color(0xFF8A5A00),
    )

    val semenderDark = PocketColors(
        isDark = true,
        bg = Color(0xFF0E0C10),
        bgDeep = Color(0xFF080709),
        surface = Color(0xFF1A171C),
        surfaceHigh = Color(0xFF27222B),
        outline = Color(0xFF5E5230),
        text = Color(0xFFF1EBD8),
        textMuted = Color(0xFFB2A98F),
        accent = Color(0xFFF26A21),
        accentSoft = Color(0xFFF5D130),
        onAccent = Color(0xFF1A0A02),
        fireLow = Color(0xFFC4501A),
        fireMid = Color(0xFFF0782A),
        fireHigh = Color(0xFFF5D130),
        ember = Color(0xFFFF6A2B),
        userBubble = Color(0xFF8E3A10),
        onUserBubble = Color(0xFFFFF0DA),
        botBubble = Color(0xFF1E1A22),
        onBotBubble = Color(0xFFF1EBD8),
        toolBubble = Color(0xFF14201C),
        onToolBubble = Color(0xFFC6DCCB),
        codeBg = Color(0xFF070608),
        error = Color(0xFFFF7A68),
        success = Color(0xFFA3D977),
        warning = Color(0xFFF5D130),
    )

    val semenderLight = PocketColors(
        isDark = false,
        bg = Color(0xFFF6F1DF),
        bgDeep = Color(0xFFE8E0C6),
        surface = Color(0xFFFFFBEF),
        surfaceHigh = Color(0xFFEFE7CD),
        outline = Color(0xFFA89C70),
        text = Color(0xFF1A1710),
        textMuted = Color(0xFF5E5740),
        accent = Color(0xFFEE6A1F),
        accentSoft = Color(0xFFA8410F),
        onAccent = Color(0xFF1A0A02),
        fireLow = Color(0xFFC4501A),
        fireMid = Color(0xFFE8701E),
        fireHigh = Color(0xFFEDB92A),
        ember = Color(0xFFE8501C),
        userBubble = Color(0xFFF1D45A),
        onUserBubble = Color(0xFF1A1710),
        botBubble = Color(0xFFFFFBEF),
        onBotBubble = Color(0xFF1A1710),
        toolBubble = Color(0xFFE1EBDD),
        onToolBubble = Color(0xFF26402D),
        codeBg = Color(0xFFE8E0C6),
        error = Color(0xFFB3261E),
        success = Color(0xFF3A7A1E),
        warning = Color(0xFF7A5A00),
    )

    val nergizDark = PocketColors(
        isDark = true,
        bg = Color(0xFF141B14),
        bgDeep = Color(0xFF0D130D),
        surface = Color(0xFF1E281D),
        surfaceHigh = Color(0xFF2A3829),
        outline = Color(0xFF56704F),
        text = Color(0xFFF0F2DA),
        textMuted = Color(0xFFABB798),
        accent = Color(0xFF7CC85A),
        accentSoft = Color(0xFFA8E080),
        onAccent = Color(0xFF0F1A0A),
        fireLow = Color(0xFFC96A1B),
        fireMid = Color(0xFFF0A020),
        fireHigh = Color(0xFFF7D24A),
        ember = Color(0xFFF2802C),
        userBubble = Color(0xFF3E6B2E),
        onUserBubble = Color(0xFFF3F8E4),
        botBubble = Color(0xFF212D20),
        onBotBubble = Color(0xFFF0F2DA),
        toolBubble = Color(0xFF1B2A2B),
        onToolBubble = Color(0xFFC6E0DD),
        codeBg = Color(0xFF0C110C),
        error = Color(0xFFFF8A7A),
        success = Color(0xFF9ADE7A),
        warning = Color(0xFFF7D24A),
    )

    val nergizLight = PocketColors(
        isDark = false,
        bg = Color(0xFFFBF8E8),
        bgDeep = Color(0xFFF0EBCF),
        surface = Color(0xFFFFFEF5),
        surfaceHigh = Color(0xFFF3EFD3),
        outline = Color(0xFFB5B88A),
        text = Color(0xFF23301C),
        textMuted = Color(0xFF5A6648),
        accent = Color(0xFF5BAA3C),
        accentSoft = Color(0xFF2F6B1F),
        onAccent = Color(0xFF0F2208),
        fireLow = Color(0xFFD9761E),
        fireMid = Color(0xFFE8941A),
        fireHigh = Color(0xFFF3C21E),
        ember = Color(0xFFE8701C),
        userBubble = Color(0xFFF7E27A),
        onUserBubble = Color(0xFF2B2505),
        botBubble = Color(0xFFFFFEF5),
        onBotBubble = Color(0xFF23301C),
        toolBubble = Color(0xFFE1EFD6),
        onToolBubble = Color(0xFF254A1A),
        codeBg = Color(0xFFEDE9C9),
        error = Color(0xFFB3261E),
        success = Color(0xFF2F7A2A),
        warning = Color(0xFF8A6100),
    )

    val kawaDark = PocketColors(
        isDark = true,
        bg = Color(0xFF17171A),
        bgDeep = Color(0xFF0F0F11),
        surface = Color(0xFF232327),
        surfaceHigh = Color(0xFF303036),
        outline = Color(0xFF5E5E68),
        text = Color(0xFFECE8E2),
        textMuted = Color(0xFFA7A39D),
        accent = Color(0xFFFF6A1A),
        accentSoft = Color(0xFFFF9A55),
        onAccent = Color(0xFF1A0A02),
        fireLow = Color(0xFFD84A12),
        fireMid = Color(0xFFFF7A1F),
        fireHigh = Color(0xFFFFC24A),
        ember = Color(0xFFFF5A14),
        userBubble = Color(0xFFA63D10),
        onUserBubble = Color(0xFFFFF0E4),
        botBubble = Color(0xFF26262B),
        onBotBubble = Color(0xFFECE8E2),
        toolBubble = Color(0xFF1E2630),
        onToolBubble = Color(0xFFC3D2E2),
        codeBg = Color(0xFF0D0D0F),
        error = Color(0xFFFF7B6B),
        success = Color(0xFF8FD68A),
        warning = Color(0xFFFFC24A),
    )

    val kawaLight = PocketColors(
        isDark = false,
        bg = Color(0xFFECE9E4),
        bgDeep = Color(0xFFDDD9D2),
        surface = Color(0xFFF8F6F2),
        surfaceHigh = Color(0xFFE4E0D9),
        outline = Color(0xFF8F8B84),
        text = Color(0xFF1C1B1D),
        textMuted = Color(0xFF57534E),
        accent = Color(0xFFE85A10),
        accentSoft = Color(0xFFB03F08),
        onAccent = Color(0xFF1A0A02),
        fireLow = Color(0xFFD84A12),
        fireMid = Color(0xFFF0661A),
        fireHigh = Color(0xFFF5A623),
        ember = Color(0xFFE84A10),
        userBubble = Color(0xFFF2C3A0),
        onUserBubble = Color(0xFF1C1B1D),
        botBubble = Color(0xFFF8F6F2),
        onBotBubble = Color(0xFF1C1B1D),
        toolBubble = Color(0xFFD9DEE6),
        onToolBubble = Color(0xFF27323F),
        codeBg = Color(0xFFDDD9D2),
        error = Color(0xFFB3261E),
        success = Color(0xFF27702C),
        warning = Color(0xFF8A5A00),
    )

    val rojDark = PocketColors(
        isDark = true,
        bg = Color(0xFF231510),
        bgDeep = Color(0xFF170D0A),
        surface = Color(0xFF331F18),
        surfaceHigh = Color(0xFF432A21),
        outline = Color(0xFF7A5242),
        text = Color(0xFFFBEBD0),
        textMuted = Color(0xFFC9AD97),
        accent = Color(0xFFE2503C),
        accentSoft = Color(0xFFFF8F7A),
        onAccent = Color(0xFF1F0805),
        fireLow = Color(0xFFC8501A),
        fireMid = Color(0xFFF0902A),
        fireHigh = Color(0xFFF7C93A),
        ember = Color(0xFFFF6A30),
        userBubble = Color(0xFFA5301F),
        onUserBubble = Color(0xFFFFF1E0),
        botBubble = Color(0xFF382219),
        onBotBubble = Color(0xFFFBEBD0),
        toolBubble = Color(0xFF1C2E22),
        onToolBubble = Color(0xFFCCE6CE),
        codeBg = Color(0xFF160C09),
        error = Color(0xFFFF8878),
        success = Color(0xFF8FD68A),
        warning = Color(0xFFF7C93A),
    )

    val rojLight = PocketColors(
        isDark = false,
        bg = Color(0xFFFBF0D8),
        bgDeep = Color(0xFFF0DFBC),
        surface = Color(0xFFFFF8E6),
        surfaceHigh = Color(0xFFF5E6C4),
        outline = Color(0xFFC4A26A),
        text = Color(0xFF2B1D12),
        textMuted = Color(0xFF6A5640),
        accent = Color(0xFFC8321F),
        accentSoft = Color(0xFF9E1F14),
        onAccent = Color(0xFFFFF6E6),
        fireLow = Color(0xFFC8501A),
        fireMid = Color(0xFFEE8A1C),
        fireHigh = Color(0xFFF7C93A),
        ember = Color(0xFFE8461F),
        userBubble = Color(0xFFF3C94E),
        onUserBubble = Color(0xFF2B1D12),
        botBubble = Color(0xFFFFF8E6),
        onBotBubble = Color(0xFF2B1D12),
        toolBubble = Color(0xFFDCEBD2),
        onToolBubble = Color(0xFF1F4A22),
        codeBg = Color(0xFFEFDDB6),
        error = Color(0xFFB3261E),
        success = Color(0xFF2E7D32),
        warning = Color(0xFF8A5A00),
    )

    val flameDark = AccentSet(Color(0xFFEE6B2C), Color(0xFFFF9A6B), Color(0xFF2B1206), Color(0xFF9A3A12), Color(0xFFFFF1E6))
    val flameLight = AccentSet(Color(0xFFEE6B2C), Color(0xFFB03A08), Color(0xFF2B1206), Color(0xFFF8CDB0), Color(0xFF2B1206))
    val saffronDark = AccentSet(Color(0xFFF2AE1E), Color(0xFFFFD36B), Color(0xFF1B1405), Color(0xFF7A5A0A), Color(0xFFFFF4D6))
    val saffronLight = AccentSet(Color(0xFFE39A0E), Color(0xFF8A5A00), Color(0xFF1B1405), Color(0xFFF6DC9A), Color(0xFF1B1405))
    val meadowDark = AccentSet(Color(0xFF6CC24A), Color(0xFFA0E07C), Color(0xFF0F1A0A), Color(0xFF2F6B2A), Color(0xFFEEF8E6))
    val meadowLight = AccentSet(Color(0xFF4FA83A), Color(0xFF2D6A1E), Color(0xFF0F1A0A), Color(0xFFCBE8B8), Color(0xFF0F1A0A))
    val skyDark = AccentSet(Color(0xFF4DA3F0), Color(0xFF8CC8FF), Color(0xFF04121F), Color(0xFF1F5A9C), Color(0xFFEAF4FF))
    val skyLight = AccentSet(Color(0xFF3B8FE0), Color(0xFF1A5CA8), Color(0xFF04121F), Color(0xFFC4DEF8), Color(0xFF04121F))
    val roseDark = AccentSet(Color(0xFFEE6A9A), Color(0xFFFF9FBF), Color(0xFF2A0A16), Color(0xFFA02A5A), Color(0xFFFFEAF2))
    val roseLight = AccentSet(Color(0xFFE2578C), Color(0xFFA3225A), Color(0xFF2A0A16), Color(0xFFF7C6D8), Color(0xFF2A0A16))
    val newtDark = AccentSet(Color(0xFFE9D23A), Color(0xFFFFEB7A), Color(0xFF1F1A02), Color(0xFF6E5E00), Color(0xFFFFF8D0))
    val newtLight = AccentSet(Color(0xFFDDC21E), Color(0xFF6E5E00), Color(0xFF1F1A02), Color(0xFFF3E58A), Color(0xFF1F1A02))
    val snowDark = AccentSet(Color(0xFFDCE6F2), Color(0xFFF4F8FD), Color(0xFF101722), Color(0xFF4A6485), Color(0xFFF4F8FD))
    val snowLight = AccentSet(Color(0xFF8FA8C6), Color(0xFF34506F), Color(0xFF101722), Color(0xFFD7E2F0), Color(0xFF101722))
}
