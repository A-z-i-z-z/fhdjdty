package dev.pocketagent.app.ui.chat

import androidx.compose.runtime.Immutable

// Incremental Markdown for a message that is still arriving (Wave 0, STREAM). Pure Kotlin, main thread only.
//
// The whole-text parser (Markdown.kt) is cheap for a finished message, but a streaming one would re-parse and re-compose
// every block on every frame. The stream keeps the blocks that can no longer change ("stable": append-only) and re-parses
// only the open end ("tail"). The property the tests enforce: after every piece, stable + tail equals parseMarkdown of
// everything appended so far.

/** The blocks of a streamed text: [stable] only ever grows (and is a new list only when it grew), [tail] is the open end. */
@Immutable
class MdSnapshot internal constructor(val stable: List<MdBlock>, val tail: List<MdBlock>) {
    /** [stable] followed by [tail] (a new list: for tests, not for the UI). */
    val blocks: List<MdBlock> get() = stable + tail

    companion object {
        val EMPTY: MdSnapshot = MdSnapshot(emptyList(), emptyList())
    }
}

class MarkdownStream {
    private val raw = StringBuilder()
    private var heldCr = false
    private var stablePublished: List<MdBlock> = emptyList()
    private var tailText = ""

    var snapshot: MdSnapshot = MdSnapshot.EMPTY
        private set

    /** Raw characters appended so far. */
    val length: Int get() = raw.length

    /** Lines handed to the parser so far (a counter for tests). */
    internal var parsedLines: Long = 0L

    /** Adds [piece] to the text and returns the new snapshot. */
    fun append(piece: String): MdSnapshot {
        if (piece.isEmpty()) return snapshot
        raw.append(piece)
        // A carriage return at the end of one piece may be the first half of a CR LF that ends the next one.
        var p = piece
        if (heldCr && p.startsWith("\n")) p = p.substring(1)
        heldCr = piece.endsWith("\r")
        val s = p.replace("\r\n", "\n").replace('\r', '\n')
        tailText += s
        if (s.indexOf('\n') >= 0) {
            val lines = tailText.split('\n')
            val complete = lines.subList(0, lines.size - 1)
            val m = complete.size
            parsedLines += m
            val parsed = parseMarkdownLines(complete)
            var k = 0
            while (k < parsed.blocks.size && parsed.endLines[k] < m) k++
            if (k > 0) stablePublished = stablePublished + parsed.blocks.subList(0, k)
            val from = if (k < parsed.blocks.size) parsed.startLines[k] else m
            tailText = lines.subList(from, lines.size).joinToString("\n")
        }
        val tailLines = tailText.split('\n')
        parsedLines += tailLines.size
        snapshot = MdSnapshot(stablePublished, parseMarkdownLines(tailLines).blocks)
        return snapshot
    }

    /** Starts over with [text]. */
    fun replace(text: String): MdSnapshot {
        raw.setLength(0)
        heldCr = false
        stablePublished = emptyList()
        tailText = ""
        snapshot = MdSnapshot.EMPTY
        return append(text)
    }

    /**
     * Brings the stream to exactly full[0 until upTo]: appends the new piece when [full] still starts with what was appended
     * (compared character by character), otherwise [replace]s. Idempotent: safe to call during composition.
     */
    fun sync(full: String, upTo: Int = full.length): MdSnapshot {
        val end = upTo.coerceIn(0, full.length)
        val have = raw.length
        if (end >= have && rawIsPrefixOf(full)) {
            return if (end == have) snapshot else append(full.substring(have, end))
        }
        return replace(full.substring(0, end))
    }

    private fun rawIsPrefixOf(full: String): Boolean {
        val n = raw.length
        if (n > full.length) return false
        for (i in 0 until n) if (raw[i] != full[i]) return false
        return true
    }
}
