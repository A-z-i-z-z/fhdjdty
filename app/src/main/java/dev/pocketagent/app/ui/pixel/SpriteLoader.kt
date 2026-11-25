package dev.pocketagent.app.ui.pixel

/**
 * The loading rules of the sprite bank, without Android: a file with a problem is skipped as a whole and
 * reported, the other files still load, and a sprite name that two files define keeps the first one.
 */
object SpriteLoader {
    class Result(val sprites: Map<String, Sprite>, val errors: List<String>)

    /**
     * [files] is a list of (file name, reader of its text), in the order given (the bank passes them sorted
     * by name). A [PxlException] message is kept as it is ("file:line: msg"); any other exception becomes
     * "file: ClassName: message". [onError] is called once for every file that failed (for logging).
     */
    fun load(
        files: List<Pair<String, () -> String>>,
        onError: (file: String, e: Throwable) -> Unit = { _, _ -> },
    ): Result {
        val found = LinkedHashMap<String, Sprite>()
        val origin = HashMap<String, String>()
        val problems = ArrayList<String>()
        for ((file, read) in files) {
            try {
                val text = read()
                val parsed = PxlParser.parse(file, text)
                for (s in parsed) {
                    val other = origin[s.name]
                    if (other != null) {
                        problems.add("$file: sprite '${s.name}' is already defined in $other")
                    } else {
                        found[s.name] = s
                        origin[s.name] = file
                    }
                }
            } catch (e: PxlException) {
                problems.add(e.message ?: "$file: unreadable")
                onError(file, e)
            } catch (e: Exception) {
                problems.add("$file: ${e.javaClass.simpleName}: ${e.message}")
                onError(file, e)
            }
        }
        return Result(found, problems)
    }
}
