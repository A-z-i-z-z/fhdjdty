package dev.pocketagent.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

val LocalPocketColors = staticCompositionLocalOf { paletteFor(ThemeId.NEWROZ, true, AccentId.THEME) }
val LocalAppearance = staticCompositionLocalOf { Appearance() }

/** Read-only access to the current theme from any composable. */
object Pocket {
    val colors: PocketColors @Composable get() = LocalPocketColors.current

    /** The settings as stored. [anim] and `motion` (Motion.kt) are what the app obeys. */
    val appearance: Appearance @Composable get() = LocalAppearance.current

    /** The EFFECTIVE animation level: the setting, lowered by the system animator scale and battery saver. */
    val anim: AnimLevel @Composable get() = LocalAnimLevel.current
}

/**
 * Wraps [content] in MaterialTheme (colour scheme mapped from [PocketColors], so stray Material
 * widgets still look right), the Pocket locals, pixel-style press feedback instead of ripples, and a
 * full-size background in the theme's bg colour. [Appearance.textScale] scales every sp in the
 * subtree (done through LocalDensity.fontScale, on top of the system font scale), so screens must not
 * multiply font sizes by textScale themselves.
 */
@Composable
fun PocketTheme(appearance: Appearance, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (appearance.darkMode) {
        DarkMode.SYSTEM -> systemDark
        DarkMode.DARK -> true
        DarkMode.LIGHT -> false
    }
    val colors = remember(appearance.theme, dark, appearance.accent) {
        paletteFor(appearance.theme, dark, appearance.accent)
    }
    val scheme = remember(colors) { colors.toMaterial() }
    val selection = remember(colors) {
        TextSelectionColors(handleColor = colors.accentSoft, backgroundColor = colors.accent.copy(alpha = 0.38f))
    }
    val base = LocalDensity.current
    val density = remember(base.density, base.fontScale, appearance.textScale) {
        Density(base.density, (base.fontScale * appearance.textScale).coerceIn(0.5f, 2.4f))
    }
    SystemBars(colors)
    val system = rememberSystemMotion()
    val level = effectiveAnimLevel(appearance.animations, system.animatorScale, system.powerSave)
    val motion = remember(level) { MotionScheme(level) }
    CompositionLocalProvider(
        LocalPocketColors provides colors,
        LocalAppearance provides appearance,
        LocalDensity provides density,
        LocalAnimLevel provides level,
        LocalMotion provides motion,
    ) {
        MaterialTheme(colorScheme = scheme, typography = PocketTypography, shapes = PocketShapes) {
            CompositionLocalProvider(
                LocalIndication provides PixelIndication,
                LocalContentColor provides colors.text,
                LocalTextSelectionColors provides selection,
            ) {
                Box(Modifier.fillMaxSize().background(colors.bg)) { content() }
            }
        }
    }
}

private val PocketShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(6.dp),
)

private val PocketTypography: Typography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontSize = 28.sp, lineHeight = 34.sp),
        headlineMedium = t.headlineMedium.copy(fontSize = 24.sp, lineHeight = 30.sp),
        headlineSmall = t.headlineSmall.copy(fontSize = 20.sp, lineHeight = 26.sp),
        titleLarge = t.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp),
        titleMedium = t.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp),
        titleSmall = t.titleSmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
        bodyLarge = t.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = t.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
        bodySmall = t.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = t.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp),
        labelMedium = t.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall = t.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
    )
}

private fun PocketColors.toMaterial(): ColorScheme {
    val errorContainer = if (isDark) Color(0xFF4A1A18) else Color(0xFFFFDAD6)
    val onTertiary = if (isDark) bgDeep else text
    return if (isDark) {
        darkColorScheme(
            primary = accent, onPrimary = onAccent,
            primaryContainer = userBubble, onPrimaryContainer = onUserBubble,
            secondary = accentSoft, onSecondary = bgDeep,
            secondaryContainer = surfaceHigh, onSecondaryContainer = text,
            tertiary = fireMid, onTertiary = onTertiary,
            tertiaryContainer = toolBubble, onTertiaryContainer = onToolBubble,
            background = bg, onBackground = text,
            surface = surface, onSurface = text,
            surfaceVariant = surfaceHigh, onSurfaceVariant = textMuted,
            surfaceTint = accent,
            inverseSurface = text, inverseOnSurface = bg, inversePrimary = accentSoft,
            error = error, onError = bgDeep, errorContainer = errorContainer, onErrorContainer = text,
            outline = outline, outlineVariant = outline.copy(alpha = 0.5f), scrim = Color.Black,
            surfaceBright = surfaceHigh, surfaceDim = bgDeep,
            surfaceContainerLowest = bgDeep, surfaceContainerLow = bg, surfaceContainer = surface,
            surfaceContainerHigh = surfaceHigh, surfaceContainerHighest = surfaceHigh,
        )
    } else {
        lightColorScheme(
            primary = accent, onPrimary = onAccent,
            primaryContainer = userBubble, onPrimaryContainer = onUserBubble,
            secondary = accentSoft, onSecondary = bg,
            secondaryContainer = surfaceHigh, onSecondaryContainer = text,
            tertiary = fireMid, onTertiary = onTertiary,
            tertiaryContainer = toolBubble, onTertiaryContainer = onToolBubble,
            background = bg, onBackground = text,
            surface = surface, onSurface = text,
            surfaceVariant = surfaceHigh, onSurfaceVariant = textMuted,
            surfaceTint = accent,
            inverseSurface = text, inverseOnSurface = bg, inversePrimary = accentSoft,
            error = error, onError = bg, errorContainer = errorContainer, onErrorContainer = text,
            outline = outline, outlineVariant = outline.copy(alpha = 0.5f), scrim = Color.Black,
            surfaceBright = surface, surfaceDim = bgDeep,
            surfaceContainerLowest = surface, surfaceContainerLow = bg, surfaceContainer = surface,
            surfaceContainerHigh = surfaceHigh, surfaceContainerHighest = surfaceHigh,
        )
    }
}

/** Transparent edge-to-edge system bars; callers can override status-bar icon polarity for dark hero scenes. */
@Composable
private fun SystemBars(colors: PocketColors) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !colors.isDark
        controller.isAppearanceLightNavigationBars = !colors.isDark
    }
}

/** Overrides only the status-bar icon polarity (e.g. light icons over HomeScreen's always-dark night sky). */
@Composable
fun SystemStatusBarIcons(darkIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkIcons
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
