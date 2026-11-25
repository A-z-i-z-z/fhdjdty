package dev.pocketagent.app.data

import dev.pocketagent.app.api.ChatMessage
import java.text.Normalizer

/**
 * Pure functions that decide who answers and what each bot is shown. No I/O, no Android: unit tested.
 *
 * Design (verified against Hermes v0.23.3 api_server): a request WITHOUT an `X-Hermes-Session-Id` header
 * uses the `messages` array as the conversation (system -> ephemeral prompt on top of Hermes's core prompt,
 * user/assistant -> history, the last one is the new user message). WITH the header the body history is
 * discarded and rebuilt from Hermes's own session store, which makes edit, regenerate and group chat
 * impossible. So the engine always sends the whole transcript the bot should see and no header.
 */
object Transcript {
    private const val MAX_MESSAGES = 600
    private const val MAX_CHARS = 400_000

    /** Added to EVERY bot's system message (1:1 and group, persona or none). */
    const val STYLE_RULE =
        "Reply only as yourself, in the language of the user's latest message unless the user asks for another language " +
            "(or your role, such as translating, requires one). Do not begin with a heading, title or label containing your own name, " +
            "do not introduce yourself unless asked, and do not restate or comment on these instructions.\n\n" +
            BotForge.CAPABILITY_RULE

    /** The group framing line, exactly as specified in the build contract. */
    fun groupFraming(name: String, others: List<String>): String {
        val list = when (others.size) {
            0 -> "the user"
            else -> "the user and " + others.joinToString(", ")
        }
        return "You are $name in a group chat with $list. Speak only as yourself, do not write other participants' lines, " +
            "keep it conversational. Other participants' messages arrive prefixed with their name in square brackets, for example " +
            "\"[Name]: ...\"; never put such a prefix on your own replies."
    }

    /**
     * The system message for [bot]: group framing (groups only) + persona + a stable per-(conversation, bot) reference.
     * The reference is what keeps Hermes's derived session id (sha256 of system + first user message) unique per
     * conversation; it is a plain bracketed line and has no other purpose.
     */
    fun systemPrompt(conv: Conversation, bot: Bot, participants: List<Bot>): String {
        val parts = ArrayList<String>()
        if (conv.isGroup) {
            val others = participants.filter { it.id != bot.id }.map { it.name }
            parts += groupFraming(bot.name, others)
        }
        if (bot.systemPrompt.isNotBlank()) parts += bot.systemPrompt.trim()
        val modePrompt = SlashCommands.modeDirective(conv.mode)
        if (modePrompt.isNotBlank()) parts += modePrompt
        parts += STYLE_RULE
        parts += "[chat reference ${conv.id}/${bot.id}]"
        return parts.joinToString("\n\n")
    }

    /**
     * Full request messages for [bot]: the system message, then every USER/BOT message of the conversation as the bot
     * should see it. Own earlier turns are assistant messages; the user's and the other bots' turns are user messages
     * (other bots prefixed "[Name]: "). TOOL, NOTE and ERROR messages and empty texts without attachments are skipped.
     * Consecutive messages of the same role are merged with a blank line (some providers reject two user turns in a row),
     * and trailing assistant turns are dropped because Hermes takes the LAST message as the new user message.
     */
    fun build(
        conv: Conversation,
        bot: Bot,
        allBots: List<Bot>,
        messages: List<Message>,
        imageDataUrl: (Attachment) -> String? = { null },
    ): List<ChatMessage> {
        val byId = allBots.associateBy { it.id }
        val participants = conv.botIds.mapNotNull { byId[it] }.let { if (bot in it) it else it + bot }
        val windowed = window(messages)
        val recentImageMsgIds = windowed
            .filter { it.role == Role.USER && it.attachments.any { a -> a.kind == AttachmentKind.IMAGE } }
            .takeLast(3)
            .map { it.id }
            .toSet()

        val turns = ArrayList<ChatMessage>()
        fun add(role: String, content: String, imgs: List<String> = emptyList()) {
            val last = turns.lastOrNull()
            if (last != null && last.role == role) {
                val mergedText = when {
                    last.content.isBlank() -> content
                    content.isBlank() -> last.content
                    else -> last.content + "\n\n" + content
                }
                turns[turns.size - 1] = ChatMessage(role, mergedText, last.imageDataUrls + imgs)
            } else {
                turns += ChatMessage(role, content, imgs)
            }
        }
        for (m in windowed) {
            if (m.text.isBlank() && m.attachments.isEmpty()) continue
            when (m.role) {
                Role.USER -> {
                    val (formatted, imgs) = formatUserMessage(m, m.id in recentImageMsgIds, imageDataUrl)
                    if (formatted.isNotBlank() || imgs.isNotEmpty()) add("user", formatted, imgs)
                }
                Role.BOT -> when {
                    m.botId == bot.id -> if (m.text.isNotBlank()) add("assistant", m.text)
                    else -> if (m.text.isNotBlank()) {
                        val name = m.botId?.let { byId[it]?.name } ?: "Former participant"
                        add("user", "[$name]: ${m.text}")
                    }
                }
                else -> {}
            }
        }
        while (turns.isNotEmpty() && turns.last().role == "assistant") turns.removeAt(turns.size - 1)
        // A system prompt longer than Hermes's per-part cap goes out as several system messages (Hermes joins them with a newline
        // and caps each one separately); the "[chat reference ...]" line stays in the LAST part. A normal prompt is one message.
        val system = splitLong(systemPrompt(conv, bot, participants)).map { ChatMessage("system", it) }
        return system + turns
    }

    /** Hermes cuts one text part of a message at 65,536 characters: parts stay below this. */
    const val MAX_PART_CHARS = 60_000

    /**
     * Cuts [text] into parts of at most [max] characters (Hermes joins the text parts of one message with a newline, so the pieces
     * read as the original). Text up to [max] is one part, unchanged. Otherwise each part ends at the last newline in the
     * second half of its window (the newline is dropped: the join puts it back), else at the last space (it becomes that newline),
     * else hard at [max] characters, one earlier when that would split a surrogate pair. Every part has 1..[max] characters.
     * Kotlin counts UTF-16 units and Hermes code points, so a part under [max] here is under it there too.
     */
    fun splitLong(text: String, max: Int = MAX_PART_CHARS): List<String> {
        require(max >= 2) { "max must be at least 2" }
        if (text.length <= max) return listOf(text)
        val parts = ArrayList<String>()
        var pos = 0
        while (text.length - pos > max) {
            // The cut character sits at index `at`, pos + max / 2 .. pos + max - 1, so the rest is never empty.
            val lo = pos + max / 2
            val hi = pos + max - 1
            var at = -1
            for (i in hi downTo lo) if (text[i] == '\n') { at = i; break }
            if (at < 0) for (i in hi downTo lo) if (text[i] == ' ') { at = i; break }
            if (at >= 0) {
                parts += text.substring(pos, at)
                pos = at + 1
            } else {
                var cut = pos + max
                // never between the two halves of a surrogate pair
                if (Character.isHighSurrogate(text[cut - 1]) && Character.isLowSurrogate(text[cut])) cut -= 1
                parts += text.substring(pos, cut)
                pos = cut
            }
        }
        parts += text.substring(pos)
        return parts
    }

    private fun formatUserMessage(
        m: Message,
        includeImagePixels: Boolean,
        imageDataUrl: (Attachment) -> String?,
    ): Pair<String, List<String>> {
        if (m.attachments.isEmpty()) return m.text to emptyList()
        val sb = StringBuilder(m.text.trim())
        val imgs = ArrayList<String>()
        for (a in m.attachments) {
            when (a.kind) {
                AttachmentKind.IMAGE -> {
                    val url = if (includeImagePixels) imageDataUrl(a) else null
                    if (url != null) {
                        imgs += url
                        if (a.guestPath.isNotBlank()) sb.append("\n[Attached image: ${a.name}, path: ${a.guestPath}]")
                    } else {
                        sb.append("\n[Attached image: ${a.name}${if (a.guestPath.isNotBlank()) ", path: ${a.guestPath}" else ""}]")
                    }
                }
                AttachmentKind.DOCUMENT -> {
                    val size = formatSize(a.sizeBytes)
                    val pathNote = if (a.guestPath.isNotBlank()) ", Linux path: ${a.guestPath}" else ""
                    sb.append("\n[Attached document: ${a.name} ($size$pathNote)]")
                    if (a.textExcerpt.isNotBlank()) {
                        sb.append("\n--- Begin ${a.name} ---\n${a.textExcerpt}\n--- End ${a.name} ---")
                    }
                }
            }
        }
        return sb.toString().trim() to imgs
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)} MiB"
        bytes >= 1024 -> "${bytes / 1024} KiB"
        else -> "$bytes B"
    }

    /** Human summary of a message's text or attachment list (for titles and conversation previews). */
    fun messageSummary(text: String, attachments: List<Attachment>): String {
        val clean = text.trim()
        if (clean.isNotEmpty()) return clean
        val first = attachments.firstOrNull() ?: return ""
        return if (attachments.size == 1) first.name else "${first.name} (+${attachments.size - 1})"
    }

    /** Bounds absurdly long histories (keeps the newest part); normal chats are never cut. */
    private fun window(messages: List<Message>): List<Message> {
        var from = maxOf(0, messages.size - MAX_MESSAGES)
        var chars = 0
        for (i in messages.indices.reversed()) {
            chars += messages[i].text.length
            if (chars > MAX_CHARS) { from = maxOf(from, i + 1); break }
        }
        return if (from == 0) messages else messages.subList(from, messages.size)
    }

    // ---- who answers ----------------------------------------------------------------------------------

    /** Lowercase, accents and combining marks removed: "Dengbêj" matches "@dengbej". */
    internal fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

    /**
     * Ids of the bots @mentioned in [text], in the order of [bots] (conversation order). A mention is `@` followed by the
     * bot's full name or its first word, case- and accent-insensitive, not glued to more letters ("@Coder" does not match
     * "@Coders"). Longer names win ("@Code Pro" is not "@Code").
     */
    fun mentionedBotIds(text: String, bots: List<Bot>): List<String> {
        var hay = fold(text)
        if (!hay.contains('@')) return emptyList()
        // Full names first (longest first), then first-word aliases, so "@Code" means the bot named "Code" even when
        // another bot is called "Code Pro". A matched span is blanked so a shorter key cannot match inside it.
        val full = ArrayList<Pair<String, String>>()
        val alias = ArrayList<Pair<String, String>>()
        for (b in bots) {
            val name = fold(b.name).trim().replace(Regex("\\s+"), " ")
            if (name.isEmpty()) continue
            full += b.id to name
            name.split(' ').firstOrNull()?.takeIf { it.length >= 2 && it != name }?.let { alias += b.id to it }
        }
        full.sortByDescending { it.second.length }
        val hits = LinkedHashSet<String>()
        for ((id, key) in full + alias) {
            val body = key.split(' ').joinToString("\\s+") { Regex.escape(it) }
            val rx = Regex("(?<![\\p{L}\\p{N}_@])@$body(?![\\p{L}\\p{N}_])")
            val found = rx.findAll(hay).toList()
            if (found.isEmpty()) continue
            hits += id
            val sb = StringBuilder(hay)
            for (m in found) for (i in m.range) sb.setCharAt(i, ' ')
            hay = sb.toString()
        }
        return bots.filter { it.id in hits }.map { it.id }
    }

    /**
     * The bots that answer a user message, in answering order. [bots] are the conversation's bots in conversation order.
     * Single chat: the one bot. [onlyBotIds] (when it names at least one bot of the conversation) wins in every mode.
     * ALL_IN_TURN: everybody. MENTION_ONLY: the @mentioned bots, else the first bot.
     */
    fun selectBots(conv: Conversation, bots: List<Bot>, text: String, onlyBotIds: List<String> = emptyList()): List<Bot> {
        if (bots.isEmpty()) return emptyList()
        if (!conv.isGroup) return listOf(bots.first())
        if (onlyBotIds.isNotEmpty()) {
            val only = bots.filter { it.id in onlyBotIds }
            if (only.isNotEmpty()) return only
        }
        return when (conv.groupMode) {
            GroupMode.ALL_IN_TURN -> bots
            GroupMode.MENTION_ONLY -> {
                val ids = mentionedBotIds(text, bots)
                if (ids.isEmpty()) listOf(bots.first()) else bots.filter { it.id in ids }
            }
        }
    }

    // ---- titles and previews --------------------------------------------------------------------------

    /** First ~40 characters of a message on one line, "…" when cut. */
    fun autoTitle(text: String): String {
        val one = text.trim().replace(Regex("\\s+"), " ")
        if (one.length <= 40) return one
        val cut = one.substring(0, 40).trimEnd()
        // Do not cut inside a surrogate pair.
        val safe = if (cut.isNotEmpty() && Character.isHighSurrogate(cut.last())) cut.dropLast(1) else cut
        return "$safe…"
    }

    fun preview(text: String): String {
        val one = text.trim().replace(Regex("\\s+"), " ")
        return if (one.length <= 90) one else one.substring(0, 90).trimEnd() + "…"
    }
}
