package dev.pocketagent.app.ui.text

/**
 * The names of texts that no later layer (a language pack, a translation tool) may replace: the words of
 * decisions, deletions and warnings. The list is the file text/protected-keys.txt in the assets; the
 * strings check makes sure every exact name in it exists.
 */
class ProtectedTexts(val exact: Set<String>, val prefixes: List<String>) {
    /** True for an exact name or for a name that starts with one of the prefixes. */
    fun isProtected(name: String): Boolean = name in exact || prefixes.any { name.startsWith(it) }

    val size: Int get() = exact.size + prefixes.size

    companion object {
        /** One name per line; "#" starts a comment line; a trailing underscore-star ("price_*") is a prefix. */
        fun parse(lines: List<String>): ProtectedTexts {
            val exact = LinkedHashSet<String>()
            val prefixes = ArrayList<String>()
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                if (line.endsWith("*")) {
                    val prefix = line.dropLast(1)
                    if (prefix.isNotEmpty()) prefixes += prefix
                } else {
                    exact += line
                }
            }
            return ProtectedTexts(exact, prefixes)
        }
    }
}
