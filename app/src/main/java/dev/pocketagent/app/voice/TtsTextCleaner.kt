package dev.pocketagent.app.voice

/**
 * Prepares Markdown chat replies for natural spoken synthesis (Supertonic 3 or system TTS):
 * removes fenced code blocks, markdown syntax and raw URLs, detects EN/FR/TR language, and splits cleanly.
 */
object TtsTextCleaner {
    private val FENCED_CODE = Regex("```[\\s\\S]*?```")
    private val INLINE_CODE = Regex("`([^`]+)`")
    private val MD_LINK = Regex("\\[([^\\]]+)]\\([^)]+\\)")
    private val MD_IMAGE = Regex("!\\[([^\\]]*)]\\([^)]+\\)")
    private val URL_RE = Regex("https?://\\S+")
    private val MD_HEADING = Regex("(?m)^#{1,6}\\s+")
    private val MD_QUOTE = Regex("(?m)^>\\s*")
    private val MD_LIST = Regex("(?m)^\\s*([-*+]|\\d+\\.)\\s+")
    private val MD_RULE = Regex("(?m)^\\s*[-*_]{3,}\\s*$")
    private val MD_TABLE_SEP = Regex("(?m)^\\|?[\\s:|-]+\\|?$")
    private val SNAKE_UNDERSCORE = Regex("(?<=[\\p{L}\\p{N}])_(?=[\\p{L}\\p{N}])")
    private val MD_EMPH = Regex("[*_~]{1,3}")
    private val SINGLE_NL_PAUSE = Regex("(?<=[\\p{L}\\p{N}])\\n(?=[\\p{L}\\p{N}])")
    private val MULTI_SPACE = Regex("[ \\t]{2,}")
    private val MULTI_NL = Regex("\\n{2,}")

    private val TR_UNIQUE = setOf('ğ', 'Ğ', 'ş', 'Ş', 'ı', 'İ')
    private val TR_SHARED = setOf('ç', 'Ç', 'ö', 'Ö', 'ü', 'Ü')
    // Strictly French diacritics; circumflex vowels (î, ê, û) are shared with Kurdish names (Agirî, Pêt, Dengbêj)
    // and therefore never trigger French by themselves.
    private val FR_UNIQUE = setOf('é', 'è', 'à', 'ù', 'œ', 'É', 'È', 'À')

    private val TR_WORDS = setOf(
        "bir", "ve", "bu", "için", "ile", "olarak", "daha", "çok", "ama", "veya", "ben", "sen",
        "merhaba", "evet", "hayır", "nasıl", "neden", "şimdi", "burada", "gibi", "değil", "var",
        "tamam", "harika", "oldu", "olur", "güzel", "lütfen", "teşekkürler", "sadece", "bugün", "bence",
    )
    private val FR_WORDS = setOf(
        "le", "la", "les", "des", "une", "un", "je", "suis", "pour", "dans", "avec", "est", "sont",
        "mais", "vous", "nous", "bonjour", "merci", "très", "aussi", "cette", "tout", "comme", "plus",
        "être", "pas", "aujourd", "hui", "bien", "oui", "non", "elle", "qui", "que", "sur", "aux",
    )
    // Avoid words that collide with common Turkish words ("can", "not")
    private val EN_WORDS = setOf(
        "the", "and", "is", "are", "was", "for", "with", "this", "that", "from", "have", "your",
        "you", "hello", "what", "when", "where", "how", "will", "but", "here", "into", "they", "their", "about",
    )

    private const val MAX_SPEECH_CHARS = 2400

    /** Converts Markdown text into clean, natural plain prose for speech synthesis. */
    fun cleanForSpeech(raw: String): String {
        if (raw.isBlank()) return ""
        var s = raw
            .replace("\r\n", "\n")
            .replace(FENCED_CODE, ". ")
            .replace(MD_IMAGE, "$1")
            .replace(MD_LINK, "$1")
            .replace(INLINE_CODE, "$1")
            .replace(URL_RE, "")
            .replace(MD_RULE, "\n")
            .replace(MD_TABLE_SEP, "\n")
            .replace(MD_HEADING, "")
            .replace(MD_QUOTE, "")
            .replace(MD_LIST, "")
            .replace("|", ", ")
            .replace(SNAKE_UNDERSCORE, " ")
            .replace(MD_EMPH, "")
        // Strip emoji/pictographs and control characters that confuse phoneme indexers
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val charCount = Character.charCount(cp)
            val keep = cp == '\n'.code || cp == '\t'.code || (cp >= 0x20 && cp < 0x2600) ||
                (cp in 0x2000..0x206F) || Character.isLetterOrDigit(cp)
            if (keep) sb.appendCodePoint(cp)
            else if (sb.isNotEmpty() && sb.last() != ' ') sb.append(' ')
            i += charCount
        }
        s = sb.toString()
            .replace(MULTI_NL, ". ")
            .replace(SINGLE_NL_PAUSE, ". ")
            .replace('\n', ' ')
            .replace(MULTI_SPACE, " ")
            .replace("..", ".")
            .replace(". .", ".")
            .trim()
        if (s.none { it.isLetterOrDigit() }) return ""
        return if (s.length <= MAX_SPEECH_CHARS) s else s.take(MAX_SPEECH_CHARS).substringBeforeLast(' ', s.take(MAX_SPEECH_CHARS))
    }

    /**
     * Picks the best Supertonic 3 language tag ("tr", "fr", "en") from [text] and fallback [appLang].
     * Supertonic 3 supports 31 languages including "en", "fr", "tr" and "na" (auto).
     */
    fun detectSupertonicLang(text: String, appLang: String = ""): String {
        val sample = text.take(600)
        val pref = appLang.lowercase().take(2)
        var trScore = if (pref == "tr") 1 else 0
        var frScore = if (pref == "fr") 1 else 0
        var enScore = if (pref == "en") 1 else 0
        for (ch in sample) {
            if (ch in TR_UNIQUE) trScore += 4
            else if (ch in TR_SHARED) trScore += 1
            if (ch in FR_UNIQUE) frScore += 3
        }
        // Split on all non-letters (including apostrophes) so French contractions like "c'est", "d'un" match cleanly.
        val words = sample.lowercase().split(Regex("[^\\p{L}]+")).filter { it.length >= 2 }
        for (w in words) {
            if (w in TR_WORDS) trScore += 2
            if (w in FR_WORDS) frScore += 2
            if (w in EN_WORDS) enScore += 2
        }
        if (trScore >= 3 && trScore >= frScore && trScore >= enScore) return "tr"
        if (frScore >= 3 && frScore > trScore && frScore >= enScore) return "fr"
        if (enScore >= 3 && enScore >= trScore && enScore >= frScore) return "en"
        return when (pref) {
            "tr" -> if (trScore >= enScore && trScore >= frScore) "tr" else if (frScore > enScore) "fr" else "en"
            "fr" -> if (frScore >= enScore && frScore >= trScore) "fr" else if (trScore > enScore) "tr" else "en"
            else -> if (trScore > enScore && trScore >= frScore) "tr" else if (frScore > enScore) "fr" else "en"
        }
    }

    /** Splits clean prose into sentence-aligned chunks <= [maxChunkChars] for low-latency streaming synthesis. */
    fun splitSentences(text: String, maxChunkChars: Int = 220): List<String> {
        val clean = text.trim()
        if (clean.isEmpty()) return emptyList()
        if (clean.length <= maxChunkChars) return listOf(clean)
        val rawSentences = clean.split(Regex("(?<=[.!?…])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        val out = ArrayList<String>()
        val buf = StringBuilder()
        fun flush() {
            val s = buf.toString().trim()
            if (s.isNotEmpty()) out += s
            buf.setLength(0)
        }
        for (sent in rawSentences) {
            if (sent.length > maxChunkChars) {
                flush()
                var rem = sent
                while (rem.length > maxChunkChars) {
                    val cut = rem.lastIndexOf(',', maxChunkChars).takeIf { it > maxChunkChars / 2 }
                        ?: rem.lastIndexOf(' ', maxChunkChars).takeIf { it > maxChunkChars / 3 }
                        ?: maxChunkChars
                    out += rem.substring(0, cut).trim()
                    rem = rem.substring(cut).trimStart(',', ' ')
                }
                if (rem.isNotEmpty()) out += rem
            } else if (buf.isEmpty()) {
                buf.append(sent)
            } else if (buf.length + 1 + sent.length <= maxChunkChars) {
                buf.append(' ').append(sent)
            } else {
                flush()
                buf.append(sent)
            }
        }
        flush()
        return out
    }
}
