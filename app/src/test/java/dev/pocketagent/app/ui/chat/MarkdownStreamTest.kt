package dev.pocketagent.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class MarkdownStreamTest {
    // ---- the property: after every piece, the stream equals parsing the whole text so far ----

    private val templates = listOf(
        // weight 3: blank lines end blocks
        "" to 3, "" to 0,
        "# Heading **bold**" to 2, "## Second heading" to 1,
        "A paragraph with **bold**, *italic*, `code` and a [link](https://example.org/x)." to 4,
        "Another line of the same paragraph, with ~~strike~~ text" to 3,
        "plain words" to 3,
        "- bullet item" to 3, "* star bullet" to 1, "  - nested bullet" to 2, "    continuation of the item" to 2,
        "1. numbered item" to 2, "2) second kind of number" to 1,
        "> quoted line" to 2, "> > nested quote" to 1,
        "| a | b |" to 2, "|---|---|" to 2, "| 1 | 2 |" to 2,
        "---" to 1,
        "```kotlin" to 2, "```" to 2, "~~~" to 1, "val x = 1" to 2, "    indented code" to 1,
        "emoji \uD83D\uDE00 and \uD83D\uDE80 text" to 2,
        "text|with|bars" to 1,
        "line with trailing spaces   " to 1,
    ).flatMap { (line, weight) -> List(weight) { line } }

    private fun document(rnd: Random): String {
        val n = 4 + rnd.nextInt(28)
        val sb = StringBuilder()
        for (i in 0 until n) {
            sb.append(templates[rnd.nextInt(templates.size)])
            if (i < n - 1) {
                when (rnd.nextInt(12)) {
                    0 -> sb.append("\r\n")
                    1 -> sb.append('\r')
                    else -> sb.append('\n')
                }
            }
        }
        if (rnd.nextInt(4) == 0) sb.append('\n')
        return sb.toString()
    }

    @Test fun randomSplitsGiveTheSameBlocksAsParsingWhole() {
        for (seed in 0 until 400) {
            val rnd = Random(1_000L + seed)
            val doc = document(rnd)
            val cuts = (0 until 25).map { rnd.nextInt(doc.length + 1) }.sorted()
            val stream = MarkdownStream()
            var pos = 0
            for (cut in cuts + doc.length) {
                stream.append(doc.substring(pos, cut))   // an empty piece is allowed
                pos = cut
                val expected = parseMarkdown(doc.substring(0, pos))
                assertEquals("seed $seed after ${pos} characters of:\n${doc.replace("\r", "\\r").replace("\n", "\\n\n")}", expected, stream.snapshot.blocks)
            }
            assertEquals(doc.length, stream.length)
        }
    }

    @Test fun stableBlocksOnlyGrow() {
        for (seed in 0 until 100) {
            val rnd = Random(9_000L + seed)
            val doc = document(rnd)
            val stream = MarkdownStream()
            var previous = emptyList<MdBlock>()
            var pos = 0
            while (pos < doc.length) {
                val next = minOf(doc.length, pos + 1 + rnd.nextInt(9))
                stream.append(doc.substring(pos, next))
                pos = next
                val stable = stream.snapshot.stable
                assertTrue("stable shrank (seed $seed)", stable.size >= previous.size)
                for (i in previous.indices) assertEquals("stable block $i changed (seed $seed)", previous[i], stable[i])
                previous = stable
            }
        }
    }

    @Test fun finishedBlocksAreParsedOnce() {
        val text = (0 until 400).joinToString("\n\n") { "p$it" } + "\n"
        val lines = text.split('\n').size
        val stream = MarkdownStream()
        var pos = 0
        while (pos < text.length) {
            val next = minOf(text.length, pos + 7)
            stream.append(text.substring(pos, next))
            pos = next
        }
        assertEquals(parseMarkdown(text), stream.snapshot.blocks)
        assertTrue("parsed ${stream.parsedLines} lines for $lines", stream.parsedLines < 4L * lines)
    }

    @Test fun crlfSplitAcrossPieces() {
        val stream = MarkdownStream()
        stream.append("first paragraph\r")
        stream.append("\n\r")
        stream.append("\nsecond paragraph")
        assertEquals(parseMarkdown("first paragraph\r\n\r\nsecond paragraph"), stream.snapshot.blocks)
        assertEquals(2, stream.snapshot.blocks.size)
    }

    @Test fun blankLineInsideFenceDoesNotEndTheBlock() {
        val stream = MarkdownStream()
        stream.append("```kotlin\nval a = 1\n\n")
        stream.append("val b = 2\n```\nafter")
        val blocks = stream.snapshot.blocks
        assertEquals(MdBlock.Code("kotlin", "val a = 1\n\nval b = 2"), blocks[0])
        assertEquals(2, blocks.size)
    }

    @Test fun unclosedFenceStaysInTail() {
        val stream = MarkdownStream()
        stream.append("intro\n\n```bash\nls -la\n")
        assertEquals(listOf<MdBlock>(MdBlock.Paragraph(listOf(MdSpan("intro")))), stream.snapshot.stable)
        assertEquals(listOf<MdBlock>(MdBlock.Code("bash", "ls -la\n")), stream.snapshot.tail)
        stream.append("more output\n")
        assertEquals(1, stream.snapshot.stable.size)
        assertEquals(1, stream.snapshot.tail.size)
        assertEquals(parseMarkdown("intro\n\n```bash\nls -la\nmore output\n"), stream.snapshot.blocks)
    }

    @Test fun paragraphBecomesTableWhenSeparatorArrives() {
        val stream = MarkdownStream()
        stream.append("| name | value |\n")
        assertTrue(stream.snapshot.blocks.single() is MdBlock.Paragraph)
        stream.append("|---|---|\n")
        assertTrue(stream.snapshot.blocks.single() is MdBlock.Table)
        stream.append("| a | 1 |\n")
        val table = stream.snapshot.blocks.single() as MdBlock.Table
        assertEquals(1, table.rows.size)
    }

    @Test fun listItemContinuationJoinsLate() {
        val stream = MarkdownStream()
        stream.append("- first item\n")
        assertEquals(1, stream.snapshot.blocks.size)
        stream.append("  continues here\n")
        val item = stream.snapshot.blocks.single() as MdBlock.Item
        assertEquals("first item\ncontinues here", item.spans.joinToString("") { it.text })
        stream.append("- second\n")
        assertEquals(2, stream.snapshot.blocks.size)
    }

    @Test fun syncAppendsWhenTextExtends() {
        val stream = MarkdownStream()
        stream.sync("Hello **wor")
        val before = stream.parsedLines
        stream.sync("Hello **world** and more")
        assertEquals(parseMarkdown("Hello **world** and more"), stream.snapshot.blocks)
        assertTrue("extending the text must not start over", stream.length == "Hello **world** and more".length)
        assertTrue(stream.parsedLines > before)
    }

    @Test fun syncReplacesWhenTextDiffers() {
        val stream = MarkdownStream()
        stream.sync("# Title\n\nsome text that was streamed")
        stream.sync("# Title\n\nsome text that was rewritten")
        assertEquals(parseMarkdown("# Title\n\nsome text that was rewritten"), stream.snapshot.blocks)
        // A shorter text (the engine trimmed the end): the stream follows.
        stream.sync("# Title")
        assertEquals(parseMarkdown("# Title"), stream.snapshot.blocks)
        stream.sync("anything", upTo = 3)
        assertEquals(parseMarkdown("any"), stream.snapshot.blocks)
        assertEquals(3, stream.length)
    }

    @Test fun syncIsIdempotent() {
        val stream = MarkdownStream()
        val a = stream.sync("one\n\ntwo\n\nthree", 12)
        val parsed = stream.parsedLines
        val b = stream.sync("one\n\ntwo\n\nthree", 12)
        assertTrue("same snapshot instance", a === b)
        assertEquals(parsed, stream.parsedLines)
        assertEquals(12, stream.length)
    }
}
