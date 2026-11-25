package dev.pocketagent.app.ui.pixel.stage

import org.junit.Assert.assertEquals
import org.junit.Test

class ArgbTest {
    @Test fun packPutsChannelsInPlace() {
        assertEquals(0x80123456.toInt(), Argb.pack(0x80, 0x12, 0x34, 0x56))
        assertEquals(0xFF000000.toInt(), Argb.pack(255, 0, 0, 0))
    }

    @Test fun overOpaqueSourceReplaces() {
        assertEquals(0xFFAABBCC.toInt(), Argb.over(0xFF112233.toInt(), 0xFFAABBCC.toInt()))
        assertEquals(0xFFAABBCC.toInt(), Argb.over(0, 0xFFAABBCC.toInt()))
    }

    @Test fun overTransparentSourceKeepsDestination() {
        assertEquals(0xFF112233.toInt(), Argb.over(0xFF112233.toInt(), 0x00FFFFFF))
        assertEquals(0x40112233, Argb.over(0x40112233, 0))
    }

    @Test fun overHalfAlphaRoundsHalfUp() {
        assertEquals(0xFF80007F.toInt(), Argb.over(0xFF0000FF.toInt(), 0x80FF0000.toInt()))
    }

    @Test fun overOnTransparentDestinationKeepsSource() {
        assertEquals(0x80112233.toInt(), Argb.over(0, 0x80112233.toInt()))
    }

    @Test fun overTwoTranslucentLayersGrowsAlphaAndKeepsColour() {
        // 50 % red on 50 % red: alpha 128 + 64 = 192, colour stays red
        assertEquals(0xC0FF0000.toInt(), Argb.over(0x80FF0000.toInt(), 0x80FF0000.toInt()))
    }

    @Test fun withAlphaMatchesToArgb() {
        assertEquals(64, Argb.withAlpha(0xFF123456.toInt(), 0.25f) ushr 24)
        assertEquals(128, Argb.withAlpha(0xFF123456.toInt(), 0.5f) ushr 24)
        assertEquals(255, Argb.withAlpha(0x00123456, 1f) ushr 24)
        assertEquals(0, Argb.withAlpha(0xFF123456.toInt(), -1f) ushr 24)
        assertEquals(255, Argb.withAlpha(0xFF123456.toInt(), 2f) ushr 24)
        // the colour is kept, the old alpha byte is ignored
        assertEquals(0x80123456.toInt(), Argb.withAlpha(0xFF123456.toInt(), 0.5f))
    }

    @Test fun scaleAlphaRoundsAndKeepsColour() {
        assertEquals(0x80FF8800.toInt(), Argb.scaleAlpha(0xFFFF8800.toInt(), 128))
        assertEquals(0xFFFF8800.toInt(), Argb.scaleAlpha(0xFFFF8800.toInt(), 255))
        assertEquals(0x00FF8800, Argb.scaleAlpha(0xFFFF8800.toInt(), 0))
        assertEquals(0x40FF8800, Argb.scaleAlpha(0x80FF8800.toInt(), 128))
    }

    @Test fun lerpEndpointsAndMiddle() {
        assertEquals(0xFF000000.toInt(), Argb.lerp(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0f))
        assertEquals(0xFFFFFFFF.toInt(), Argb.lerp(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 1f))
        assertEquals(0xFF808080.toInt(), Argb.lerp(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f))
        // the result is always opaque, whatever the inputs say
        assertEquals(0xFF102030.toInt(), Argb.lerp(0x00102030, 0x20405060, 0f))
    }
}
