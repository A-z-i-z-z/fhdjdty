package dev.pocketagent.app.data

/**
 * Gemini 4 on Vertex's OpenAI-compatible endpoint delivers its "thought summaries" as ordinary `delta.content` chunks (Hermes
 * v0.23.3 does not recognise them): each summary is ONE whole chunk shaped `**Short Title**\n\nFirst-person English paragraph.\n\n`,
 * and they arrive before the real answer. Observed with `google/gemini-4-hydrogen` at medium, low and high effort; the only
 * Hermes-side switch (`reasoning_effort: none`) also forces the floor thinking level, so the engine filters client-side instead.
 *
 * The test is deliberately narrow: a chunk is a thought summary only when it is a whole `**Title**` line followed by a blank line
 * and a body of at least 20 characters, and the chunk ends with a blank line (real answers stream in small pieces that do not).
 * The engine applies it only at the very start of a message bubble and puts a match into `Message.reasoning`.
 */
object ThoughtFilter {
    private val BLOCK = Regex("^\\s*\\*\\*[^*\\n]{3,100}\\*\\*[ \\t]*\\n\\n[\\s\\S]{20,}$")

    fun isThoughtBlock(delta: String): Boolean = delta.endsWith("\n\n") && BLOCK.containsMatchIn(delta)
}
