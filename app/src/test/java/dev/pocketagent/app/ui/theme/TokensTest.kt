package dev.pocketagent.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.roundToInt

/** The tokens are the numbers of the design language, and nothing moved at the default text size. */
class TokensTest {
    @Test
    fun spaceValues() {
        assertEquals(2.dp, Space.xxs)
        assertEquals(4.dp, Space.xs)
        assertEquals(8.dp, Space.s)
        assertEquals(12.dp, Space.m)
        assertEquals(16.dp, Space.l)
        assertEquals(24.dp, Space.xl)
        assertEquals(32.dp, Space.xxl)
        assertEquals(16.dp, Space.gutter)
        assertEquals(48.dp, Space.touch)
        assertEquals(1, Corner.chip)
        assertEquals(2, Corner.card)
        assertEquals(3, Corner.sheet)
        assertEquals(1.5.dp, PixelType.small)
        assertEquals(2.dp, PixelType.title)
        assertEquals(3.dp, PixelType.big)
    }

    @Test
    fun designUnitAtDensities() {
        assertEquals(4, designUnitPx(2.0f))
        assertEquals(5, designUnitPx(2.625f))
        assertEquals(6, designUnitPx(3.0f))
        assertEquals(7, designUnitPx(3.5f))
        assertEquals(1, designUnitPx(0.5f))
        assertEquals(1, designUnitPx(0.1f))
    }

    @Test
    fun pixelFontPxIdenticalAtScaleOne() {
        for (pixel in listOf(1.5f, 2f, 3f)) {
            for (density in listOf(2.0f, 2.625f, 3.0f, 3.5f)) {
                val old = (pixel * density).roundToInt().coerceAtLeast(1)
                assertEquals("pixel $pixel at density $density", old, pixelFontPx(pixel, density, 1f, true))
                assertEquals(old, pixelFontPx(pixel, density, 1f, false))
            }
        }
    }

    @Test
    fun pixelFontPxSteps() {
        // a 2 dp font pixel at density 3.2: 6.4 device pixels at the default size
        assertEquals(5, pixelFontPx(2f, 3.2f, 0.9f, true))
        assertEquals(6, pixelFontPx(2f, 3.2f, 1.0f, true))
        assertEquals(6, pixelFontPx(2f, 3.2f, 1.15f, true))
        assertEquals(8, pixelFontPx(2f, 3.2f, 1.3f, true))
        assertEquals(10, pixelFontPx(2f, 3.2f, 1.8f, true))
        // switched off: the size stays put whatever the text size
        assertEquals(6, pixelFontPx(2f, 3.2f, 1.8f, false))
    }

    @Test
    fun pixelFontPxCapsAtThreeDp() {
        // 2 dp times 1.5 is exactly the cap of 3 dp; 3 dp times 1.5 would be 4.5 and stays at 3
        assertEquals(10, pixelFontPx(2f, 3.2f, 2.0f, true))
        assertEquals(10, pixelFontPx(3f, 3.2f, 2.0f, true))
        // a size that is already above the cap is never made smaller than itself
        assertEquals(13, pixelFontPx(4f, 3.2f, 2.0f, true))
    }

    @Test
    fun bevelFormulasEqualOldOnes() {
        val face = Color(0xFF3A2739)
        assertEquals(lerp(face, Color.White, 0.10f), bevelRim(face, true))
        assertEquals(lerp(face, Color.White, 0.55f), bevelRim(face, false))
        assertEquals(lerp(face, Color.Black, 0.22f), bevelShade(face, true))
        assertEquals(lerp(face, Color.Black, 0.07f), bevelShade(face, false))
    }

    @Test
    fun snapRounds() {
        assertEquals(5f, snapToDesignPx(7.4f, 5), 0f)
        assertEquals(10f, snapToDesignPx(7.6f, 5), 0f)
        assertEquals(0f, snapToDesignPx(0f, 5), 0f)
        assertEquals(-10f, snapToDesignPx(-7.6f, 5), 0f)
        assertEquals(3.3f, snapToDesignPx(3.3f, 0), 0f)
    }
}
