package dev.pocketagent.app.ui.text

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/*
 * The Compose side of the text layer. It lives in its own file so that a unit test that calls Texts never
 * has to load Compose. Every screen reads its words through t() and tn(); both follow the language the
 * shell provides (LocaleScope), so a language change recomposes the readers.
 */

/** Changes when the text layers were rebuilt, so that [t] and [tn] readers recompose. */
val LocalTextsRevision: ProvidableCompositionLocal<Int> = compositionLocalOf { 0 }

/** The language in force in this composition (what LocaleScope provides). */
@Composable
fun appLocale(): Locale = LocalConfiguration.current.locales[0]

/** The text [id], formatted with [args]. The same call as stringResource, through the layers of [Texts]. */
@Suppress("UNUSED_EXPRESSION")
@Composable
fun t(@StringRes id: Int, vararg args: Any): String {
    LocalTextsRevision.current
    val context = LocalContext.current
    val locale = appLocale()
    return Texts.getAt(context, locale, id, args)
}

/**
 * The plural text [id] for [count]. When the text shows the number, pass the count again in [args]:
 * tn(R.plurals.x, n, n).
 */
@Suppress("UNUSED_EXPRESSION")
@Composable
fun tn(@PluralsRes id: Int, count: Int, vararg args: Any): String {
    LocalTextsRevision.current
    val context = LocalContext.current
    val locale = appLocale()
    return Texts.getQuantityAt(context, locale, id, count, args)
}

/** Provides the text revision to everything below. NAV places it inside LocaleScope. */
@Composable
fun ProvideTexts(content: @Composable () -> Unit) {
    val revision by Texts.revision.collectAsState()
    CompositionLocalProvider(LocalTextsRevision provides revision) { content() }
}
