package dev.pocketagent.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The refactor of Markdown.kt (line ranges for the stream parser) changed no result of the parser. */
class MarkdownBlocksTest {
    private fun p(text: String) = MdBlock.Paragraph(listOf(MdSpan(text)))

    @Test fun heading() {
        assertEquals(listOf<MdBlock>(MdBlock.Heading(1, listOf(MdSpan("Title")))), parseMarkdown("# Title"))
        assertEquals(listOf<MdBlock>(MdBlock.Heading(3, listOf(MdSpan("Sub")))), parseMarkdown("### Sub ###"))
    }

    @Test fun paragraphsJoinLinesAndSplitOnBlankLines() {
        assertEquals(listOf<MdBlock>(p("a\nb")), parseMarkdown("a\nb"))
        assertEquals(listOf<MdBlock>(p("a"), p("b")), parseMarkdown("a\n\nb"))
    }

    @Test fun inlineStyles() {
        assertEquals(
            listOf<MdBlock>(MdBlock.Paragraph(listOf(MdSpan("b", bold = true)))),
            parseMarkdown("**b**"),
        )
        assertEquals(
            listOf<MdBlock>(MdBlock.Paragraph(listOf(MdSpan("x "), MdSpan("y", code = true)))),
            parseMarkdown("x `y`"),
        )
    }

    @Test fun items() {
        assertEquals(listOf<MdBlock>(MdBlock.Item(0, "•", listOf(MdSpan("x")))), parseMarkdown("- x"))
        assertEquals(listOf<MdBlock>(MdBlock.Item(0, "1.", listOf(MdSpan("y")))), parseMarkdown("1. y"))
    }

    @Test fun nestedItemsAreFlattenedWithADepth() {
        assertEquals(
            listOf<MdBlock>(MdBlock.Item(0, "•", listOf(MdSpan("a"))), MdBlock.Item(1, "•", listOf(MdSpan("b")))),
            parseMarkdown("- a\n  - b"),
        )
    }

    @Test fun fencedCode() {
        assertEquals(listOf<MdBlock>(MdBlock.Code("kt", "val x")), parseMarkdown("```kt\nval x\n```"))
    }

    @Test fun unclosedFenceIsACodeBlockThatKeepsGrowing() {
        assertEquals(listOf<MdBlock>(MdBlock.Code("", "abc")), parseMarkdown("```\nabc"))
    }

    @Test fun rule() {
        assertEquals(listOf<MdBlock>(MdBlock.Rule), parseMarkdown("---"))
    }

    @Test fun quote() {
        assertEquals(listOf<MdBlock>(MdBlock.Quote(listOf(p("q")))), parseMarkdown("> q"))
    }

    @Test fun table() {
        assertEquals(
            listOf<MdBlock>(
                MdBlock.Table(
                    header = listOf(listOf(MdSpan("a")), listOf(MdSpan("b"))),
                    rows = listOf(listOf(listOf(MdSpan("1")), listOf(MdSpan("2")))),
                ),
            ),
            parseMarkdown("| a | b |\n|---|---|\n| 1 | 2 |"),
        )
    }

    @Test fun mixedDocument() {
        val blocks = parseMarkdown("# T\n\ntext\n\n- one\n- two\n\n```\ncode\n```\n\n---")
        assertEquals(
            listOf(
                MdBlock.Heading(1, listOf(MdSpan("T"))), p("text"),
                MdBlock.Item(0, "•", listOf(MdSpan("one"))), MdBlock.Item(0, "•", listOf(MdSpan("two"))),
                MdBlock.Code("", "code"), MdBlock.Rule,
            ),
            blocks,
        )
    }

    @Test fun emptyInputHasNoBlocks() {
        assertTrue(parseMarkdown("").isEmpty())
        assertTrue(parseMarkdown("\n\n  \n").isEmpty())
    }

    @Test fun rangesCoverEveryBlock() {
        val text = "intro\nmore\n\n# H\n- a\n  more a\n- b\n> q1\n> q2\n```x\nc\n```\n| a | b |\n|---|---|\n| 1 | 2 |\n\nend"
        val lines = text.split('\n')
        val parsed = parseMarkdownLines(lines)
        assertEquals(parseMarkdown(text), parsed.blocks)
        assertEquals(parsed.blocks.size, parsed.startLines.size)
        assertEquals(parsed.blocks.size, parsed.endLines.size)
        for (i in parsed.blocks.indices) {
            assertTrue("block $i is not empty", parsed.startLines[i] < parsed.endLines[i])
            assertTrue("block $i ends inside the input", parsed.endLines[i] <= lines.size)
            if (i + 1 < parsed.blocks.size) {
                assertTrue("blocks $i and ${i + 1} overlap", parsed.endLines[i] <= parsed.startLines[i + 1])
            }
        }
        // The first block is the two-line paragraph, the last one ends with the input.
        assertEquals(0, parsed.startLines[0])
        assertEquals(2, parsed.endLines[0])
        assertEquals(lines.size, parsed.endLines.last())
    }

    @Test fun markdownToPlainStillFlattensCodeFences() {
        assertEquals("val x = 1", markdownToPlain("```kotlin\nval x = 1\n```"))
        assertEquals("Title and text", markdownToPlain("# Title\n\nand text"))
    }
}
