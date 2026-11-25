package dev.pocketagent.app.ui.pixel.stage

import dev.pocketagent.app.ui.pixel.PxlException
import dev.pocketagent.app.ui.pixel.SpriteLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SpriteLoaderTest {
    private fun file(name: String, text: String): Pair<String, () -> String> = name to { text.trimIndent() + "\n" }

    private val dotA = """
        palette
        k #102030
        sprite dot 1 1
        frame
        k
    """

    private val dotB = """
        palette
        k #FFFFFF
        sprite dot 2 2
        frame
        kk
        kk
    """

    private val other = """
        palette
        k #102030
        sprite other 1 1
        frame
        k
    """

    private val broken = """
        palette
        k #102030
        sprite wide 3 1
        frame
        kk
    """

    @Test fun brokenFileIsSkippedOthersLoad() {
        val seen = ArrayList<String>()
        val r = SpriteLoader.load(
            listOf(file("a.pxl", dotA), file("b.pxl", broken), file("c.pxl", other)),
        ) { f, e -> seen.add("$f:${e.javaClass.simpleName}") }
        assertEquals(setOf("dot", "other"), r.sprites.keys)
        assertEquals(1, r.errors.size)
        assertTrue(r.errors[0], r.errors[0].startsWith("b.pxl:"))
        assertEquals(listOf("b.pxl:PxlException"), seen)
    }

    @Test fun duplicateNameAcrossFilesIsReported() {
        val r = SpriteLoader.load(listOf(file("a.pxl", dotA), file("b.pxl", dotB)))
        assertEquals(listOf("b.pxl: sprite 'dot' is already defined in a.pxl"), r.errors)
        // the first one wins
        assertEquals(1, r.sprites.getValue("dot").width)
        assertEquals(1, r.sprites.size)
    }

    @Test fun unreadableFileIsReportedWithClassName() {
        val unreadable: Pair<String, () -> String> = "x.pxl" to { throw IOException("boom") }
        val r = SpriteLoader.load(listOf(unreadable, file("a.pxl", dotA)))
        assertEquals(listOf("x.pxl: IOException: boom"), r.errors)
        assertEquals(setOf("dot"), r.sprites.keys)
    }

    @Test fun errorKeepsFileLineMessage() {
        val r = SpriteLoader.load(listOf(file("bad.pxl", broken)))
        assertEquals(0, r.sprites.size)
        assertEquals(1, r.errors.size)
        val msg = r.errors[0]
        assertTrue(msg, msg.startsWith("bad.pxl:5: "))
        assertTrue(msg, msg.contains("row has 2 chars, expected 3"))
        // the same text the parser itself throws
        val direct = runCatching { dev.pocketagent.app.ui.pixel.PxlParser.parse("bad.pxl", broken.trimIndent() + "\n") }
            .exceptionOrNull()
        assertTrue(direct is PxlException)
        assertEquals(direct!!.message, msg)
    }

    @Test fun filesLoadInTheOrderGiven() {
        val r = SpriteLoader.load(listOf(file("z.pxl", other), file("a.pxl", dotA)))
        assertEquals(listOf("other", "dot"), r.sprites.keys.toList())
        assertEquals(0, r.errors.size)
    }
}
