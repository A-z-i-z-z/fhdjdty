package dev.pocketagent.app.ui.pixel.stage

import dev.pocketagent.app.ui.pixel.PxlParser
import dev.pocketagent.app.ui.pixel.Sprite
import dev.pocketagent.app.ui.pixel.SpriteRaster
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SpriteRasterTest {
    private fun sprite(text: String): Sprite = PxlParser.parse("t.pxl", text.trimIndent() + "\n")[0]

    private val dot = """
        palette
        . none
        k #102030
        o #80FF8800
        sprite dot 2 2
        frame 50
        ko
        .k
        frame
        ..
        kk
    """

    private val ramped = """
        palette
        . none
        k #102030
        1 #FF111111
        2 #FF222222
        3 #FF333333
        4 #FF444444
        sprite ridge 5 1
        frame
        k1234
    """

    @Test fun plainMatchesPaletteIndices() {
        val s = sprite(dot)
        assertArrayEquals(
            intArrayOf(0xFF102030.toInt(), 0x80FF8800.toInt(), 0, 0xFF102030.toInt()),
            SpriteRaster.argb(s, 0),
        )
        assertArrayEquals(intArrayOf(0, 0, 0xFF102030.toInt(), 0xFF102030.toInt()), SpriteRaster.argb(s, 1))
    }

    @Test fun rampReplacesCharsOneToFour() {
        val s = sprite(ramped)
        val ramp = intArrayOf(0xFFA00001.toInt(), 0xFFA00002.toInt(), 0xFFA00003.toInt(), 0xFFA00004.toInt())
        assertArrayEquals(
            intArrayOf(0xFF102030.toInt(), ramp[0], ramp[1], ramp[2], ramp[3]),
            SpriteRaster.argb(s, 0, ramp = ramp),
        )
        // without a ramp the palette's own colours stay
        assertArrayEquals(
            intArrayOf(0xFF102030.toInt(), 0xFF111111.toInt(), 0xFF222222.toInt(), 0xFF333333.toInt(), 0xFF444444.toInt()),
            SpriteRaster.argb(s, 0),
        )
    }

    @Test fun rampShorterThanFourRepeatsLast() {
        val s = sprite(ramped)
        val ramp = intArrayOf(0xFFB00001.toInt(), 0xFFB00002.toInt())
        assertArrayEquals(
            intArrayOf(0xFF102030.toInt(), ramp[0], ramp[1], ramp[1], ramp[1]),
            SpriteRaster.argb(s, 0, ramp = ramp),
        )
    }

    @Test fun emptyRampChangesNothing() {
        val s = sprite(ramped)
        assertArrayEquals(SpriteRaster.argb(s, 0), SpriteRaster.argb(s, 0, ramp = IntArray(0)))
    }

    @Test fun tintScalesAlphaAndReplacesRgb() {
        val s = sprite(dot)
        val out = SpriteRaster.argb(s, 0, hasTint = true, tint = 0x80ABCDEF.toInt())
        // opaque pixel: alpha 255 * 128 / 255 = 128; half-transparent pixel: (128 * 128 + 127) / 255 = 64
        assertArrayEquals(intArrayOf(0x80ABCDEF.toInt(), 0x40ABCDEF, 0, 0x80ABCDEF.toInt()), out)
    }

    @Test fun plainIsCachedOnTheSprite() {
        val s = sprite(dot)
        val first = SpriteRaster.plain(s, 0)
        assertSame(first, SpriteRaster.plain(s, 0))
        assertArrayEquals(SpriteRaster.argb(s, 0), first)
        // another frame has its own array
        val second = SpriteRaster.plain(s, 1)
        assertArrayEquals(SpriteRaster.argb(s, 1), second)
        assertSame(second, SpriteRaster.plain(s, 1))
    }
}
