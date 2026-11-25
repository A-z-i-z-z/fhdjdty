package dev.pocketagent.app.ui.text

/**
 * Plural categories for texts that do not come from Android resources (the platform has its own rules
 * for those). Languages not listed answer "other"; later waves extend the table.
 */
object PluralRules {
    const val ZERO = "zero"
    const val ONE = "one"
    const val TWO = "two"
    const val FEW = "few"
    const val MANY = "many"
    const val OTHER = "other"

    /** "zero", "one", "two", "few", "many" or "other" for [n] in [language] (a lower-case language code). */
    fun category(language: String, n: Long): String {
        val lang = language.lowercase(java.util.Locale.ROOT).substringBefore('-').substringBefore('_')
        return when (lang) {
            "en", "de", "it", "es", "tr", "ku" -> if (n == 1L) ONE else OTHER
            "fr", "pt" -> if (n == 0L || n == 1L) ONE else OTHER
            "ru" -> {
                val m10 = n % 10
                val m100 = n % 100
                when {
                    m10 == 1L && m100 != 11L -> ONE
                    m10 in 2L..4L && m100 !in 12L..14L -> FEW
                    else -> MANY
                }
            }
            else -> OTHER
        }
    }
}
