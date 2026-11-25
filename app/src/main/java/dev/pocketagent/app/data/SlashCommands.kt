package dev.pocketagent.app.data

/**
 * Slash commands and operating modes for the chat input bar. Pure Kotlin, unit tested.
 */
object SlashCommands {
    enum class SlashCategory { MODE, ACTION, SECRET }

    data class SlashCommandDef(
        val name: String,
        val usage: String,
        val category: SlashCategory,
        val aliases: List<String> = emptyList(),
    )

    data class ParsedSlash(val name: String, val arg: String)

    val all: List<SlashCommandDef> = listOf(
        SlashCommandDef("plan", "/plan [task]", SlashCategory.MODE),
        SlashCommandDef("goal", "/goal [objective]", SlashCategory.MODE),
        SlashCommandDef("ultracode", "/ultracode [task]", SlashCategory.MODE),
        SlashCommandDef("loop", "/loop [2-5] [task]", SlashCategory.MODE),
        SlashCommandDef("normal", "/normal", SlashCategory.MODE),
        SlashCommandDef("forge", "/forge <bot idea>", SlashCategory.ACTION),
        SlashCommandDef("skills", "/skills [query]", SlashCategory.ACTION),
        SlashCommandDef("remember", "/remember <fact>", SlashCategory.ACTION),
        SlashCommandDef("memory", "/memory", SlashCategory.ACTION),
        SlashCommandDef("find", "/find <text>", SlashCategory.ACTION),
        SlashCommandDef("theme", "/theme <name>", SlashCategory.ACTION),
        SlashCommandDef("export", "/export", SlashCategory.ACTION),
        SlashCommandDef("clear", "/clear", SlashCategory.ACTION),
        SlashCommandDef("help", "/help", SlashCategory.ACTION),
        // Secret easter egg commands (not shown in autocomplete until discovered)
        SlashCommandDef("pet", "/pet", SlashCategory.SECRET),
        SlashCommandDef("newroz", "/newroz", SlashCategory.SECRET),
        SlashCommandDef("agir", "/agir", SlashCategory.SECRET, aliases = listOf("fire")),
        SlashCommandDef("roll", "/roll [d20|coin]", SlashCategory.SECRET),
    )

    val visible: List<SlashCommandDef> = all.filter { it.category != SlashCategory.SECRET }

    private val SLASH_RE = Regex("^/([a-zA-Z]+)(?:\\s+([\\s\\S]*))?$")

    fun parse(input: String): ParsedSlash? {
        val t = input.trim()
        if (!t.startsWith("/")) return null
        val m = SLASH_RE.matchEntire(t) ?: return null
        val cmd = m.groupValues[1].lowercase()
        if (all.none { it.name == cmd || cmd in it.aliases }) return null
        return ParsedSlash(cmd, m.groupValues[2].trim())
    }

    /** Prefix autocomplete candidates while typing the command token (before the first space). */
    fun suggestions(input: String): List<SlashCommandDef> {
        if (!input.startsWith("/")) return emptyList()
        if (input.any { it.isWhitespace() }) return emptyList()
        val prefix = input.drop(1).lowercase()
        return visible.filter { it.name.startsWith(prefix) }
    }

    /** Operational system directive injected into [Transcript.systemPrompt] when a chat mode is active. */
    fun modeDirective(mode: ChatMode): String = when (mode) {
        ChatMode.NORMAL -> ""
        ChatMode.PLAN ->
            "ACTIVE MODE: PLAN. Before implementing or running destructive actions, think like an architect: " +
                "clarify assumptions, break the problem into clear ordered steps, highlight risks and trade-offs, and end with concrete next actions."
        ChatMode.GOAL ->
            "ACTIVE MODE: GOAL. Treat the user's request as an autonomous mission: break it into verifiable milestones, " +
                "use your tools (terminal, files, memory, skills) step by step to achieve and verify every criterion, and report clearly what was verified."
        ChatMode.ULTRACODE ->
            "ACTIVE MODE: ULTRACODE. Operate as a staff software engineer: inspect existing code/files before editing, " +
                "write clean minimal changes without duplication, run tests or verification commands in your shell when possible, and state exact paths and observed results."
        ChatMode.LOOP ->
            "ACTIVE MODE: LOOP. Deliver thorough, self-critiqued iterations: check your own answer for gaps, edge cases, and clarity, " +
                "and refine it cleanly across passes."
    }

    /** Parses optional leading pass count `2..5` (default 3) followed by the prompt text. */
    fun parseLoopArgs(arg: String): Pair<Int, String> {
        val t = arg.trim()
        if (t.isEmpty()) return 3 to ""
        val first = t.substringBefore(' ')
        val count = first.toIntOrNull()
        return if (count != null && t.contains(' ')) {
            count.coerceIn(2, 5) to t.substringAfter(' ').trim()
        } else {
            3 to t
        }
    }
}
