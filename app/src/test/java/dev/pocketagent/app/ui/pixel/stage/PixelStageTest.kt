package dev.pocketagent.app.ui.pixel.stage

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PixelStageTest {
    private val c = 0xFF336699.toInt()

    private fun at(stage: PixelStage, x: Int, y: Int) = stage.pixels[y * stage.width + x]

    @Test fun newStageIsTransparentAndSizeIsChecked() {
        val s = PixelStage(4, 3)
        assertEquals(12, s.pixels.size)
        assertEquals(0, s.pixels.count { it != 0 })
        assertThrows(IllegalArgumentException::class.java) { PixelStage(0, 3) }
        assertThrows(IllegalArgumentException::class.java) { PixelStage(3, -1) }
    }

    @Test fun plotIsClippedAndBlends() {
        val s = PixelStage(2, 2)
        s.plot(-1, 0, c); s.plot(0, -1, c); s.plot(2, 0, c); s.plot(0, 2, c)
        assertEquals(0, s.pixels.count { it != 0 })
        s.plot(1, 1, c)
        assertEquals(c, at(s, 1, 1))
        s.plot(1, 1, 0x80FFFFFF.toInt())
        assertEquals(Argb.over(c, 0x80FFFFFF.toInt()), at(s, 1, 1))
    }

    @Test fun fillRectClipsToBounds() {
        val s = PixelStage(4, 3)
        s.fillRect(-2, -1, 4, 3, c)          // covers x 0..1, y 0..1
        for (y in 0 until 3) for (x in 0 until 4) {
            val want = if (x <= 1 && y <= 1) c else 0
            assertEquals("pixel $x,$y", want, at(s, x, y))
        }
        s.fillRect(3, 2, 5, 5, c)             // only the corner pixel is inside
        assertEquals(c, at(s, 3, 2))
        val before = s.pixels.copyOf()
        s.fillRect(0, 0, 0, 3, 0xFFFFFFFF.toInt())
        s.fillRect(0, 0, 3, -1, 0xFFFFFFFF.toInt())
        s.fillRect(10, 10, 2, 2, 0xFFFFFFFF.toInt())
        assertArrayEquals(before, s.pixels)
    }

    @Test fun blitClipsNegativeOffsets() {
        val s = PixelStage(4, 3)
        val src = IntArray(6) { 0xFF000001.toInt() + it }      // 3 x 2, indices 0..5
        s.blit(src, 3, 2, -1, -1)
        // only src(1,1) = index 4 and src(2,1) = index 5 land inside, at (0,0) and (1,0)
        assertEquals(0xFF000005.toInt(), at(s, 0, 0))
        assertEquals(0xFF000006.toInt(), at(s, 1, 0))
        assertEquals(2, s.pixels.count { it != 0 })
    }

    @Test fun blitClipsAtTheFarEdges() {
        val s = PixelStage(4, 3)
        val src = IntArray(6) { 0xFF000001.toInt() + it }
        s.blit(src, 3, 2, 3, 2)
        // only src(0,0) lands inside, at (3,2)
        assertEquals(0xFF000001.toInt(), at(s, 3, 2))
        assertEquals(1, s.pixels.count { it != 0 })
        s.blit(src, 3, 2, 4, 0)
        s.blit(src, 3, 2, 0, 3)
        s.blit(src, 3, 2, -3, 0)
        assertEquals(1, s.pixels.count { it != 0 })
    }

    @Test fun blitSkipsTransparentSource() {
        val s = PixelStage(2, 1)
        s.clear(0xFF0000FF.toInt())
        // pixel 0 is "transparent" but carries a colour; it must leave the destination alone
        s.blit(intArrayOf(0x00FF0000, 0xFF00FF00.toInt()), 2, 1, 0, 0)
        assertEquals(0xFF0000FF.toInt(), at(s, 0, 0))
        assertEquals(0xFF00FF00.toInt(), at(s, 1, 0))
    }

    @Test fun blitAppliesGlobalAlphaToSourceAlpha() {
        val red = intArrayOf(0xFFFF0000.toInt())
        val onTransparent = PixelStage(1, 1)
        onTransparent.blit(red, 1, 1, 0, 0, 0.5f)
        assertEquals(0x80FF0000.toInt(), at(onTransparent, 0, 0))
        val onBlue = PixelStage(1, 1)
        onBlue.clear(0xFF0000FF.toInt())
        onBlue.blit(red, 1, 1, 0, 0, 0.5f)
        assertEquals(0xFF80007F.toInt(), at(onBlue, 0, 0))
        val nothing = PixelStage(1, 1)
        nothing.blit(red, 1, 1, 0, 0, 0f)
        assertEquals(0, at(nothing, 0, 0))
    }

    @Test fun clearZeroes() {
        val s = PixelStage(3, 3)
        s.fillRect(0, 0, 3, 3, c)
        s.clear()
        assertEquals(0, s.pixels.count { it != 0 })
        s.clear(0x11223344)
        assertEquals(9, s.pixels.count { it == 0x11223344 })
    }

    @Test fun scaleIntoPlacesBlocksWithOffsetAndClips() {
        val a = 0xFF0000AA.toInt()
        val b = 0xFF00AA00.toInt()
        val cc = 0xFFAA0000.toInt()
        val d = 0xFFAAAA00.toInt()
        val s = PixelStage(2, 2)
        s.plot(0, 0, a); s.plot(1, 0, b); s.plot(0, 1, cc); s.plot(1, 1, d)
        val mark = 0x11111111
        val dst = IntArray(5 * 6) { mark }
        s.scaleInto(dst, 5, 6, StageGrid(3, 2, 2, -1, 2))
        // block A covers x -1..1 and y 2..4, B x 2..4 and y 2..4, C x -1..1 and y 5..7, D x 2..4 and y 5..7
        for (y in 0 until 6) for (x in 0 until 5) {
            val want = when {
                y < 2 -> mark
                y <= 4 -> if (x <= 1) a else b
                else -> if (x <= 1) cc else d
            }
            assertEquals("device pixel $x,$y", want, dst[y * 5 + x])
        }
    }

    @Test fun scaleIntoCopiesAndLeavesTheOutsideAlone() {
        val s = PixelStage(1, 1)
        s.plot(0, 0, 0x80102030.toInt())
        val dst = IntArray(4 * 4) { 0x7F7F7F7F }
        s.scaleInto(dst, 4, 4, StageGrid(2, 1, 1, 1, 1))
        for (y in 0 until 4) for (x in 0 until 4) {
            val inside = x in 1..2 && y in 1..2
            assertEquals("device pixel $x,$y", if (inside) 0x80102030.toInt() else 0x7F7F7F7F, dst[y * 4 + x])
        }
        // a grid that lies completely outside changes nothing
        val before = dst.copyOf()
        s.scaleInto(dst, 4, 4, StageGrid(2, 1, 1, 9, 0))
        s.scaleInto(dst, 4, 4, StageGrid(2, 1, 1, 0, -9))
        assertArrayEquals(before, dst)
    }

    @Test fun scaleOneIsACopy() {
        val s = PixelStage(3, 2)
        for (i in s.pixels.indices) s.pixels[i] = 0xFF000000.toInt() or i
        val dst = IntArray(6)
        s.scaleInto(dst, 3, 2, StageGrid(1, 3, 2, 0, 0))
        assertArrayEquals(s.pixels, dst)
    }
}
