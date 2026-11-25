package dev.pocketagent.app.ui.theme

/**
 * Black night (Wave 1a, APPEAR): the dark variants with a pure black background and the panels one step lighter.
 *
 * Returns the receiver itself when [on] is false or the palette is not dark. Otherwise the two background colours
 * become black, the surface becomes the old background and the high surface becomes the old surface. Text, outline,
 * accent, bubbles, code background and fire are unchanged.
 *
 * Contrast cannot get worse: every colour that is drawn on a background is lighter than that background, and each
 * new background is either black or a colour that was already a background of the same palette and is darker than
 * the one it replaces. `BlackNightTest` checks it for all themes and accents.
 *
 * Apply it once, to a palette that comes from `paletteFor` (the shifted surfaces are not stable under a second call).
 * The black is made from the old background with `Color.copy`, so this file needs no Compose import.
 */
fun PocketColors.withBlackNight(on: Boolean = true): PocketColors {
    if (!on || !isDark) return this
    val black = bg.copy(alpha = 1f, red = 0f, green = 0f, blue = 0f)
    return copy(bg = black, bgDeep = black, surface = bg, surfaceHigh = surface)
}
