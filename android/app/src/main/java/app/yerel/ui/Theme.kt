package app.yerel.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yerel.core.AppSettings

data class Accent(val name: String, val light: Color, val dark: Color)

val Accents = listOf(
    Accent("İndigo", Color(0xFF5458D8), Color(0xFF9DA0FF)),
    Accent("Zümrüt", Color(0xFF0E8F6E), Color(0xFF4FD1A5)),
    Accent("Okyanus", Color(0xFF0B7FC1), Color(0xFF6CC4F5)),
    Accent("Gün batımı", Color(0xFFD9620B), Color(0xFFFFA45C)),
    Accent("Gül", Color(0xFFD0285A), Color(0xFFFF8BA7)),
    Accent("Lavanta", Color(0xFF8B4FD8), Color(0xFFC9A3FF)),
    Accent("Mono", Color(0xFF111111), Color(0xFFEDEDED)),
)

private fun mix(a: Color, b: Color, amount: Float) = a.copy(alpha = amount).compositeOver(b)

private fun scheme(accent: Accent, dark: Boolean): ColorScheme {
    return if (dark) {
        val bg = Color(0xFF151517)
        val p = accent.dark
        darkColorScheme(
            primary = p,
            onPrimary = if (accent.name == "Mono") Color.Black else Color(0xFF0B0B12),
            primaryContainer = mix(p, bg, 0.22f),
            onPrimaryContainer = mix(p, Color.White, 0.35f),
            secondary = mix(p, Color(0xFFB8B8C0), 0.4f),
            onSecondary = Color.Black,
            secondaryContainer = Color(0xFF2A2A2E),
            onSecondaryContainer = Color(0xFFE6E6EA),
            tertiary = Color(0xFFFFB86B),
            background = bg,
            onBackground = Color(0xFFECECEF),
            surface = bg,
            onSurface = Color(0xFFECECEF),
            surfaceVariant = Color(0xFF26262A),
            onSurfaceVariant = Color(0xFFA9A9B2),
            surfaceContainerLowest = Color(0xFF101012),
            surfaceContainerLow = Color(0xFF1B1B1E),
            surfaceContainer = Color(0xFF202023),
            surfaceContainerHigh = Color(0xFF29292D),
            surfaceContainerHighest = Color(0xFF323237),
            outline = Color(0xFF4A4A52),
            outlineVariant = Color(0xFF2E2E33),
            error = Color(0xFFFF8A80),
            errorContainer = Color(0xFF3B1C1C),
            onErrorContainer = Color(0xFFFFDAD6),
        )
    } else {
        val bg = Color(0xFFFFFFFF)
        val p = accent.light
        lightColorScheme(
            primary = p,
            onPrimary = Color.White,
            primaryContainer = mix(p, bg, 0.12f),
            onPrimaryContainer = mix(p, Color.Black, 0.75f),
            secondary = mix(p, Color(0xFF55555F), 0.35f),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFF0F0F3),
            onSecondaryContainer = Color(0xFF1C1C21),
            tertiary = Color(0xFFB45309),
            background = bg,
            onBackground = Color(0xFF111114),
            surface = bg,
            onSurface = Color(0xFF111114),
            surfaceVariant = Color(0xFFF2F2F5),
            onSurfaceVariant = Color(0xFF5E5E68),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF8F8FA),
            surfaceContainer = Color(0xFFF3F3F6),
            surfaceContainerHigh = Color(0xFFEDEDF1),
            surfaceContainerHighest = Color(0xFFE6E6EB),
            outline = Color(0xFFC9C9D1),
            outlineVariant = Color(0xFFE6E6EB),
            error = Color(0xFFC62828),
            errorContainer = Color(0xFFFDECEA),
            onErrorContainer = Color(0xFF5F1412),
        )
    }
}

private val AppTypography = Typography().let { t ->
    t.copy(
        bodyLarge = t.bodyLarge.copy(fontSize = 16.sp, lineHeight = 25.sp, letterSpacing = 0.1.sp),
        bodyMedium = t.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.Medium),
    )
}

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

val MonoStyle = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)

@Composable
fun isDark(settings: AppSettings) = when (settings.themeMode) {
    1 -> false
    2 -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun YerelTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = isDark(settings)
    val colors = if (settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val ctx = LocalContext.current
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        scheme(Accents.getOrElse(settings.accent) { Accents[0] }, dark)
    }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * settings.fontScale)) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
