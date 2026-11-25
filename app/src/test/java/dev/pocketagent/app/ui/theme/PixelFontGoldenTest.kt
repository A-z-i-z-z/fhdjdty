package dev.pocketagent.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Proof that Wave 0 changed no letter that existed before it. The golden file holds, one line per character, the
 * width and the twelve rows of every glyph of the pixel font as it was before Wave 0 (line format:
 * U+XXXX, a space, the width, a space, the rows joined by the bar character).
 *
 * Making the golden (once, by the integration step): check out the commit before Wave 0 in a scratch worktree, add
 * only this test file and its folder, and run this test with the environment variable PIXELFONT_GOLDEN_WRITE set
 * to 1. The test then writes the file instead of comparing. Commit the file; every later run compares.
 */
class PixelFontGoldenTest {
    private fun lineOf(ch: Char): String? {
        val g = PixelFont.glyph(ch) ?: return null
        return "U+%04X %d %s".format(ch.code, g.width, g.rows.joinToString("|"))
    }

    private fun goldenFile(): File {
        val candidates = listOf(File("src/test/resources/$NAME"), File("app/src/test/resources/$NAME"))
        return candidates.firstOrNull { it.isFile }
            ?: candidates.firstOrNull { it.parentFile?.parentFile?.isDirectory == true }
            ?: candidates.first()
    }

    @Test
    fun v1GlyphsUnchanged() {
        if (System.getenv("PIXELFONT_GOLDEN_WRITE") == "1") {
            val file = goldenFile()
            file.parentFile?.mkdirs()
            val lines = (0x20..0x2FFF).mapNotNull { lineOf(it.toChar()) }
            file.writeText(lines.joinToString("\n") + "\n", Charsets.UTF_8)
            return
        }
        val resource = javaClass.getResourceAsStream("/$NAME")
        val file = goldenFile()
        val text = when {
            resource != null -> resource.use { String(it.readBytes(), Charsets.UTF_8) }
            file.isFile -> file.readText(Charsets.UTF_8)
            else -> {
                fail(
                    "$NAME is missing. Make it once from the commit before Wave 0: a scratch worktree of that commit with " +
                        "only this test added, run with PIXELFONT_GOLDEN_WRITE=1, then commit the file.",
                )
                return
            }
        }
        // CI checks the file out with LF, this PC with CRLF: the comparison must not depend on it.
        val golden = text.replace("\r\n", "\n").replace('\r', '\n').split('\n').filter { it.isNotBlank() }
        assertNotNull(golden)
        assertEquals("the golden file is not empty", false, golden.isEmpty())
        for (line in golden) {
            val code = line.substringBefore(' ').removePrefix("U+").toInt(16)
            val now = lineOf(code.toChar())
            assertEquals("glyph U+%04X changed or vanished".format(code), line, now)
        }
    }

    private companion object {
        const val NAME = "pixelfont-v1.golden"
    }
}
