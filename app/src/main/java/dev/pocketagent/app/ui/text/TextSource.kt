package dev.pocketagent.app.ui.text

import java.util.IllegalFormatException
import java.util.Locale

/*
 * The pure core of the text layer: no android types and no Compose, so a plain JVM test can load it.
 * A text is looked up layer by layer (the layers are TextSource objects); the first layer that has the
 * entry and whose template can be formatted with the given arguments wins. A lookup never throws and
 * never returns a blank string for a missing entry: the last resort is the entry's name.
 */

/** A text entry: [id] is the R.string or R.plurals integer, [name] the entry name such as "ui_nav_home". */
data class TextKey(val id: Int, val name: String)

/** One layer of texts. A null answer means "this layer has no such entry". */
interface TextSource {
    /** The raw template of a string entry, not formatted. */
    fun string(key: TextKey): String?

    /** The raw template of the plural form chosen for [count]. */
    fun plural(key: TextKey, count: Int): String?
}

object TextFormat {
    /** `String.format(locale, template, args)`, or null when the template and the arguments do not fit. */
    fun format(locale: Locale, template: String, args: Array<out Any>): String? = try {
        String.format(locale, template, *args)
    } catch (_: IllegalFormatException) {
        null
    } catch (_: IndexOutOfBoundsException) {
        null
    }
}

/**
 * Resolves texts through [layers], first non-null wins. A layer whose template makes the format fail is
 * skipped. With no arguments the template is returned as it is (as Resources.getString(id) does, so a
 * "%%" stays "%%"). [onMissing] is told when no layer could answer; the result is then the entry name.
 */
class LayeredTexts(
    val locale: Locale,
    private val layers: List<TextSource>,
    private val onMissing: (TextKey) -> Unit = {},
) {
    fun string(key: TextKey, args: Array<out Any>): String {
        for (layer in layers) {
            val template = stringOf(layer, key) ?: continue
            if (args.isEmpty()) return template
            val formatted = TextFormat.format(locale, template, args) ?: continue
            return formatted
        }
        report(key)
        return key.name
    }

    fun plural(key: TextKey, count: Int, args: Array<out Any>): String {
        for (layer in layers) {
            val template = pluralOf(layer, key, count) ?: continue
            if (args.isEmpty()) return template
            val formatted = TextFormat.format(locale, template, args) ?: continue
            return formatted
        }
        report(key)
        return key.name
    }

    private fun stringOf(layer: TextSource, key: TextKey): String? =
        try { layer.string(key) } catch (_: Exception) { null }

    private fun pluralOf(layer: TextSource, key: TextKey, count: Int): String? =
        try { layer.plural(key, count) } catch (_: Exception) { null }

    private fun report(key: TextKey) {
        try { onMissing(key) } catch (_: Exception) { }
    }
}
