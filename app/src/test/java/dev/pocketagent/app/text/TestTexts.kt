package dev.pocketagent.app.text

import dev.pocketagent.app.ui.text.PluralRules
import dev.pocketagent.app.ui.text.TextKey
import dev.pocketagent.app.ui.text.TextSource
import dev.pocketagent.app.ui.text.Texts
import java.lang.reflect.Modifier
import java.util.Locale

/**
 * Makes the real English texts available to a unit test: reads app/src/main/res/values/strings*.xml, maps the
 * ids of the generated R.string and R.plurals classes (by reflection) to their entry names, and installs the
 * result as the only text layer of [Texts]. After installEnglish(), Texts.text, Texts.get(context, ...) and
 * Texts.getQuantity(...) answer in English and never touch a Context (a test may pass a wrapper whose
 * resources throw). A test class that formats engine texts calls installEnglish() in a @Before and reset()
 * in an @After.
 */
object TestTexts {
    private val english: EnglishSource by lazy { EnglishSource() }

    fun installEnglish() {
        Texts.installForTest(english, english::nameOf, Locale.ENGLISH)
    }

    fun reset() {
        Texts.resetForTest()
    }

    private class EnglishSource : TextSource {
        private val strings = HashMap<String, String>()
        private val plurals = HashMap<String, Map<String, String>>()
        private val names = HashMap<Int, String>()

        init {
            val dir = ResXml.languageDirs()["en"]
                ?: throw IllegalStateException("app/src/main/res/values is not reachable from the test's working directory")
            for (file in ResXml.stringsFiles(dir)) {
                for (e in ResXml.parse(file)) {
                    if (e.isPlurals) plurals[e.name] = e.forms else strings[e.name] = e.forms["_"] ?: ""
                }
            }
            collectIds("dev.pocketagent.app.R\$string")
            collectIds("dev.pocketagent.app.R\$plurals")
        }

        private fun collectIds(className: String) {
            val cls = try { Class.forName(className) } catch (_: ClassNotFoundException) { return }
            for (f in cls.declaredFields) {
                if (!Modifier.isStatic(f.modifiers) || f.type != Int::class.javaPrimitiveType) continue
                f.isAccessible = true
                names[f.getInt(null)] = f.name
            }
        }

        fun nameOf(id: Int): String? = names[id]

        override fun string(key: TextKey): String? = strings[key.name]

        override fun plural(key: TextKey, count: Int): String? {
            val forms = plurals[key.name] ?: return null
            return forms[PluralRules.category("en", count.toLong())] ?: forms["other"]
        }
    }
}
