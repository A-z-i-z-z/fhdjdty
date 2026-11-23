package dev.pocketagent.app.data

import org.json.JSONObject

/**
 * Parses autonomous bot creation blocks (` ```pocket-bot ... ``` `) from model replies and supplies the system
 * instructions for memory updates and bot forging. Pure Kotlin, unit tested.
 */
object BotForge {
    val VALID_AVATARS = setOf(
        "NEWT", "WOLF", "FOX", "OWL", "EAGLE", "GOAT", "PARTRIDGE", "BEE",
        "LYNX", "CAT", "ROBOT", "SUN", "MOUNTAIN", "HORSE", "BEAR", "DRAGON",
    )

    const val DEFAULT_COLOR: Long = 0xFFE8661CL

    const val CAPABILITY_RULE =
        "Capabilities in Agirî: " +
            "(1) Memory: you have a persistent `memory` tool (MEMORY.md and USER.md). When the user asks you to remember something " +
            "or shares durable preferences, identity facts or long-term goals, proactively update memory using the `memory` tool. " +
            "(2) Bot Forge: ONLY when the user explicitly asks you to create, design or forge a new bot/persona (or uses /forge), " +
            "include a fenced code block tagged `pocket-bot` containing a single JSON object with keys " +
            "\"name\" (short name), \"tagline\" (concise subtitle), \"avatar\" (one of NEWT, WOLF, FOX, OWL, EAGLE, GOAT, PARTRIDGE, BEE, LYNX, CAT, ROBOT, SUN, MOUNTAIN, HORSE, BEAR, DRAGON), " +
            "\"color\" (hex like #E8661C), \"systemPrompt\" (rich persona instructions), and optionally \"voice\" (F1..F5 female or M1..M5 male) and \"voiceStyle\" (calm, serious, happy, excited, storyteller), " +
            "plus a brief friendly introduction in the user's language. " +
            "Never emit a `pocket-bot` block unless the user asked to create a bot."

    data class ForgedBotSpec(
        val name: String,
        val tagline: String,
        val avatar: String,
        val color: Long,
        val systemPrompt: String,
        val voiceSid: Int = -1,
        val voiceStyle: BotVoiceStyle = BotVoiceStyle.DEFAULT,
    )

    data class ExtractionResult(
        val cleanedText: String,
        val bots: List<ForgedBotSpec>,
    )

    private val BLOCK_RE = Regex("```pocket-bot\\s*\\r?\\n([\\s\\S]*?)\\r?\\n?```", RegexOption.IGNORE_CASE)

    /** Extracts valid `pocket-bot` JSON specs and removes only valid blocks from [text]. */
    fun extract(text: String): ExtractionResult {
        if (!text.contains("pocket-bot", ignoreCase = true)) return ExtractionResult(text, emptyList())
        val specs = ArrayList<ForgedBotSpec>()
        val out = StringBuilder()
        var cursor = 0
        for (match in BLOCK_RE.findAll(text)) {
            val spec = parseSpec(match.groupValues[1]) ?: continue
            out.append(text, cursor, match.range.first)
            cursor = match.range.last + 1
            specs += spec
        }
        if (specs.isEmpty()) return ExtractionResult(text, emptyList())
        out.append(text, cursor, text.length)
        val cleaned = out.toString()
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        return ExtractionResult(cleaned, specs)
    }

    private fun parseSpec(raw: String): ForgedBotSpec? {
        val obj = try { JSONObject(raw.trim()) } catch (_: Exception) { return null }
        val name = obj.optString("name", "").trim().take(40)
        val systemPrompt = obj.optString("systemPrompt", "").ifBlank { obj.optString("system_prompt", "") }.trim()
        if (name.isEmpty() || systemPrompt.isEmpty()) return null
        val tagline = obj.optString("tagline", "").trim().take(80)
        val avatarRaw = obj.optString("avatar", "NEWT").trim().uppercase()
        val avatar = if (avatarRaw in VALID_AVATARS) avatarRaw else "NEWT"
        val color = parseColor(obj.optString("color", ""))
        val voiceSid = parseVoiceSid(obj.optString("voice", ""), obj.optInt("voiceSid", -1))
        val voiceStyle = parseVoiceStyle(obj.optString("voiceStyle", "").ifBlank { obj.optString("voice_style", "") })
        return ForgedBotSpec(
            name = name, tagline = tagline, avatar = avatar, color = color, systemPrompt = systemPrompt,
            voiceSid = voiceSid, voiceStyle = voiceStyle,
        )
    }

    internal fun parseVoiceSid(voiceCode: String, rawSid: Int): Int {
        if (rawSid in 0..9) return rawSid
        return when (voiceCode.trim().uppercase()) {
            "F1" -> 0; "F2" -> 1; "F3" -> 2; "F4" -> 3; "F5" -> 4
            "M1" -> 5; "M2" -> 6; "M3" -> 7; "M4" -> 8; "M5" -> 9
            "FEMALE" -> 0; "MALE" -> 5
            else -> -1
        }
    }

    internal fun parseVoiceStyle(raw: String): BotVoiceStyle = when (raw.trim().lowercase()) {
        "calm", "gentle", "soft" -> BotVoiceStyle.CALM
        "serious", "formal", "focused" -> BotVoiceStyle.SERIOUS
        "happy", "cheerful", "warm" -> BotVoiceStyle.HAPPY
        "excited", "energetic", "lively" -> BotVoiceStyle.EXCITED
        "storyteller", "poetic", "dengbej" -> BotVoiceStyle.STORYTELLER
        else -> BotVoiceStyle.DEFAULT
    }

    internal fun parseColor(raw: String): Long {
        val hex = raw.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
        if (hex.length == 6 && hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            return (0xFF000000L or hex.toLong(16))
        }
        if (hex.length == 8 && hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            return hex.toLong(16) and 0xFFFFFFFFL
        }
        return DEFAULT_COLOR
    }
}
