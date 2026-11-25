package dev.pocketagent.app.ui.text

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import dev.pocketagent.app.R
import dev.pocketagent.app.ui.theme.AppearanceStore
import dev.pocketagent.app.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * The one place that turns a text id into text, for code that has no Composable to ask: a click handler, a
 * notification, a service, the chat engine, the terminal. Composables call [t] and [tn] (TextsCompose.kt),
 * which end up here too, so every text follows one rule.
 *
 * Layers, first answer wins: the app resources in the chosen language, then the app resources in English
 * (the values folder). A layer whose template does not fit the arguments is skipped. If nothing answers the
 * result is the entry name (never blank, never an exception) and the miss is logged once. With no arguments
 * the template is returned as written (as Resources.getString does, so a double percent stays double).
 *
 * Language packs (a files/lang folder) are not part of Wave 0: [reloadPacks] only drops the caches and bumps
 * [revision], so readers recompose.
 */
object Texts {
    private const val TAG = "Text"
    private const val PROTECTED_ASSET = "text/protected-keys.txt"

    private val revisionState = MutableStateFlow(0)

    /** Changes whenever the layers were rebuilt; [t] and [tn] read it through LocalTextsRevision. */
    val revision: StateFlow<Int> = revisionState.asStateFlow()

    @Volatile private var app: Context? = null
    @Volatile private var namesIntact: Boolean = true
    @Volatile private var protectedTexts: ProtectedTexts = ProtectedTexts(emptySet(), emptyList())
    @Volatile private var lastLocale: Locale = Locale.ENGLISH
    @Volatile private var testTexts: LayeredTexts? = null
    @Volatile private var testNameOf: ((Int) -> String?)? = null

    /** One layered lookup per locale tag. */
    private val layered = ConcurrentHashMap<String, LayeredTexts>()

    /** Entry names by id, for the fallback text and the log. */
    private val names = ConcurrentHashMap<Int, String>()
    private val reported: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // ---- set-up ---------------------------------------------------------------------------------------------

    /** Idempotent. Called by PocketApp.onCreate right after the log is open. */
    fun install(context: Context) {
        val appContext = context.applicationContext ?: context
        app = appContext
        try {
            val lines = appContext.assets.open(PROTECTED_ASSET).bufferedReader(Charsets.UTF_8).use { it.readLines() }
            protectedTexts = ProtectedTexts.parse(lines)
        } catch (e: Exception) {
            AppLog.w(TAG, "protected-keys.txt could not be read", e)
        }
        // The names of the entries must survive the build: they are the keys of every later layer.
        namesIntact = try {
            appContext.resources.getResourceEntryName(R.string.app_name) == "app_name"
        } catch (_: Exception) {
            false
        }
        if (!namesIntact) AppLog.w(TAG, "resource entry names did not survive the build; later layers are off")
    }

    /** After a file of a language layer changed. No packs exist in Wave 0, so only the caches are dropped. */
    @Suppress("UNUSED_PARAMETER")
    fun reloadPacks(context: Context) {
        layered.clear()
        revisionState.update { it + 1 }
    }

    // ---- language ---------------------------------------------------------------------------------------------

    /** The language the user chose in the app, else the language of [context]. */
    fun localeOf(context: Context): Locale {
        val test = testTexts
        if (test != null) return test.locale
        val chosen = try {
            AppearanceStore.get(context).appearance.value.language
        } catch (_: Exception) {
            ""
        }
        val fromChoice = if (chosen.isBlank()) null else Locale.forLanguageTag(chosen).takeIf { it.language.isNotEmpty() }
        val locale = fromChoice ?: context.resources.configuration.locales[0]
        lastLocale = locale
        return locale
    }

    /** The chosen language for code that has no Context. Needs [install]; without it the JVM default. */
    fun currentLocale(): Locale {
        val test = testTexts
        if (test != null) return test.locale
        val a = app ?: return Locale.getDefault()
        return localeOf(a)
    }

    // ---- texts ------------------------------------------------------------------------------------------------

    /** For click handlers, notifications and services: the text [id] in the chosen language. */
    fun get(context: Context, @StringRes id: Int, vararg args: Any): String =
        getAt(context, localeOf(context), id, args)

    fun getQuantity(context: Context, @PluralsRes id: Int, count: Int, vararg args: Any): String =
        getQuantityAt(context, localeOf(context), id, count, args)

    /** For the engine, the terminal and data classes: no Context. Needs [install]; before it the result is "#id". */
    fun text(@StringRes id: Int, vararg args: Any): String {
        val test = testTexts
        if (test != null) return safely(id) { test.string(keyFor(id, null), args) }
        val a = app ?: return "#$id"
        return get(a, id, *args)
    }

    fun textQuantity(@PluralsRes id: Int, count: Int, vararg args: Any): String {
        val test = testTexts
        if (test != null) return safely(id) { test.plural(keyFor(id, null), count, args) }
        val a = app ?: return "#$id"
        return getQuantity(a, id, count, *args)
    }

    /** One line for the diagnostics. */
    fun describe(): String {
        val test = testTexts
        val locale = test?.locale ?: lastLocale
        val layers = when {
            test != null -> "test"
            locale.language == Locale.ENGLISH.language && locale.country.isEmpty() -> "res"
            else -> "res+en"
        }
        return "texts: locale=${locale.toLanguageTag()} layers=$layers namesIntact=$namesIntact " +
            "protected=${protectedTexts.size} packs=off"
    }

    // ---- the entry points of t() and tn(), and of the functions above -----------------------------------

    /** The text [id] in [locale]. [context] only supplies the resources and may be a localised wrapper. */
    internal fun getAt(context: Context, locale: Locale, id: Int, args: Array<out Any>): String {
        val test = testTexts
        if (test != null) return safely(id) { test.string(keyFor(id, null), args) }
        return safely(id) { layeredFor(context, locale).string(keyFor(id, context), args) }
    }

    internal fun getQuantityAt(context: Context, locale: Locale, id: Int, count: Int, args: Array<out Any>): String {
        val test = testTexts
        if (test != null) return safely(id) { test.plural(keyFor(id, null), count, args) }
        return safely(id) { layeredFor(context, locale).plural(keyFor(id, context), count, args) }
    }

    // ---- internals --------------------------------------------------------------------------------------------

    private inline fun safely(id: Int, block: () -> String): String = try {
        block()
    } catch (e: Exception) {
        if (reported.add("#$id")) AppLog.w(TAG, "text lookup failed for #$id", e)
        "#$id"
    }

    private fun keyFor(id: Int, context: Context?): TextKey {
        val cached = names[id]
        if (cached != null) return TextKey(id, cached)
        val found = nameOf(id, context)
        if (found != null) {
            names[id] = found
            return TextKey(id, found)
        }
        return TextKey(id, "#$id")
    }

    private fun nameOf(id: Int, context: Context?): String? {
        val nameOfTest = testNameOf
        if (testTexts != null && nameOfTest != null) return nameOfTest(id)
        val resources: Resources = (context ?: app)?.resources ?: return null
        return try {
            resources.getResourceEntryName(id)
        } catch (_: Exception) {
            null
        }
    }

    private fun layeredFor(context: Context, locale: Locale): LayeredTexts {
        val tag = locale.toLanguageTag()
        val known = layered[tag]
        if (known != null) return known
        val base: Context = app ?: context.applicationContext ?: context
        val layers = ArrayList<TextSource>(2)
        layers += ResourceTextSource(resourcesIn(base, locale))
        if (tag != Locale.ENGLISH.toLanguageTag()) layers += ResourceTextSource(resourcesIn(base, Locale.ENGLISH))
        val made = LayeredTexts(locale, layers) { reportMissing(it) }
        return layered.putIfAbsent(tag, made) ?: made
    }

    private fun resourcesIn(base: Context, locale: Locale): Resources {
        val conf = Configuration(base.resources.configuration)
        conf.setLocale(locale)
        return base.createConfigurationContext(conf).resources
    }

    private fun reportMissing(key: TextKey) {
        if (reported.add(key.name)) AppLog.w(TAG, "no text for ${key.name}")
    }

    // ---- tests ------------------------------------------------------------------------------------------------

    /**
     * Installs [source] as the only layer, resolved with [locale]; [nameOf] maps an id to its entry name. After this
     * call every lookup ignores its Context (a test may hand in a wrapper whose resources throw).
     */
    internal fun installForTest(source: TextSource, nameOf: (Int) -> String?, locale: Locale) {
        names.clear()
        reported.clear()
        testNameOf = nameOf
        lastLocale = locale
        testTexts = LayeredTexts(locale, listOf(source)) { reportMissing(it) }
    }

    internal fun resetForTest() {
        testTexts = null
        testNameOf = null
        names.clear()
        layered.clear()
        reported.clear()
    }
}

/** The stretched test language of the debug builds (feature 0.11): English with longer words, in brackets. */
object TestLanguage {
    const val TAG = "en-XZ"

    /** True only where the folder values-en-rXZ is part of the build (the two debug variants). */
    fun isAvailable(context: Context): Boolean = try {
        val base = context.applicationContext ?: context
        val conf = Configuration(base.resources.configuration)
        conf.setLocale(Locale.forLanguageTag(TAG))
        base.createConfigurationContext(conf).resources.getString(R.string.ui_nav_home).startsWith("[")
    } catch (_: Exception) {
        false
    }
}
