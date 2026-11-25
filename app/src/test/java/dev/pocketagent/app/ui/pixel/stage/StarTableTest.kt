package dev.pocketagent.app.ui.pixel.stage

import dev.pocketagent.app.ui.pixel.stage.SkyTestKit.utc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class StarTableTest {
    private class RawStar(val ra: Double, val dec: Double, val mag: Double, val tint: Int, val nameId: Int)

    private fun u16(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }

    /** Writes a table the way tools/art/stars.py lays it out (little endian, 12 byte header, 8 byte records, names). */
    private fun encode(stars: List<RawStar>, names: List<String>, version: Int = 1, declaredCount: Int = stars.size): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("AGST".toByteArray(Charsets.US_ASCII))
        out.write(version)
        out.write(45)
        u16(out, declaredCount)
        u16(out, names.size)
        u16(out, 0)
        for (s in stars) {
            u16(out, Math.floorMod(Math.round(s.ra / 360.0 * 65536.0).toInt(), 65536))
            u16(out, Math.round(s.dec / 90.0 * 32767.0).toInt() and 0xFFFF)
            out.write(Math.round(s.mag * 10.0).toInt() and 0xFF)
            out.write(s.tint)
            u16(out, s.nameId)
        }
        for (n in names) {
            val b = n.toByteArray(Charsets.UTF_8)
            out.write(b.size)
            out.write(b)
        }
        return out.toByteArray()
    }

    private val threeStars = listOf(
        RawStar(101.2871, -16.7161, -1.46, 0, 1),
        RawStar(37.9546, 89.2641, 2.0, 1, 2),
        RawStar(0.0, 0.0, 4.5, 2, 0),
    )

    @Test fun parsesSyntheticTable() {
        val t = StarTable.parse(encode(threeStars, listOf("Sirius", "Polaris")))
        assertEquals(3, t.size)
        assertEquals(101.2871f, t.raDeg[0], 0.006f)
        assertEquals(-16.7161f, t.decDeg[0], 0.003f)
        assertEquals(-1.5f, t.mag[0], 1e-4f)
        assertEquals(0.toByte(), t.tint[0])
        assertEquals("Sirius", t.nameOf(0))
        assertEquals(37.9546f, t.raDeg[1], 0.006f)
        assertEquals(89.2641f, t.decDeg[1], 0.003f)
        assertEquals(2.0f, t.mag[1], 1e-4f)
        assertEquals(1.toByte(), t.tint[1])
        assertEquals("Polaris", t.nameOf(1))
        assertEquals(0f, t.raDeg[2], 1e-4f)
        assertEquals(0f, t.decDeg[2], 1e-4f)
        assertEquals(4.5f, t.mag[2], 1e-4f)
        assertEquals(2.toByte(), t.tint[2])
        assertNull(t.nameOf(2))
        assertEquals(listOf("Sirius", "Polaris"), t.names)
    }

    @Test fun badInputsGiveEmpty() {
        val good = encode(threeStars, listOf("Sirius", "Polaris"))
        assertTrue(StarTable.parse(good).size == 3)
        assertSame(StarTable.EMPTY, StarTable.parse(ByteArray(0)))
        assertSame(StarTable.EMPTY, StarTable.parse(good.copyOf(11)))
        assertSame(StarTable.EMPTY, StarTable.parse(good.copyOf(12 + 3 * 8 - 1))) // truncated records
        assertSame(StarTable.EMPTY, StarTable.parse(good.copyOf(good.size - 2))) // truncated names
        val badMagic = good.copyOf()
        badMagic[0] = 'X'.code.toByte()
        assertSame(StarTable.EMPTY, StarTable.parse(badMagic))
        assertSame(StarTable.EMPTY, StarTable.parse(encode(threeStars, listOf("Sirius", "Polaris"), version = 2)))
        assertSame(StarTable.EMPTY, StarTable.parse(encode(threeStars, listOf("Sirius", "Polaris"), declaredCount = 4)))
        assertSame(StarTable.EMPTY, StarTable.parse(encode(threeStars, listOf("Sirius")))) // star 2 names a missing entry
        assertSame(StarTable.EMPTY, StarTable.parse(encode(listOf(RawStar(1.0, 1.0, 1.0, 3, 0)), emptyList()))) // tint 3
        assertSame(StarTable.EMPTY, StarTable.parse(encode(threeStars, listOf("", "Polaris")))) // empty name
        assertSame(StarTable.EMPTY, StarTable.parse(encode(emptyList(), emptyList()))) // no stars
        assertEquals(0, StarTable.EMPTY.size)
    }

    private fun assetFile(): File? =
        listOf("src/main/assets/sky/stars.bin", "app/src/main/assets/sky/stars.bin").map { File(it) }.firstOrNull { it.isFile }

    @Test fun realAsset() {
        val f = assetFile()
        Assume.assumeTrue("assets/sky/stars.bin not produced yet", f != null)
        val bytes = f!!.readBytes()
        assertTrue("under 12 KB", bytes.size < 12 * 1024)
        val t = StarTable.parse(bytes)
        assertTrue("800 to 1000 stars, got ${t.size}", t.size in 800..1000)
        for (i in 1 until t.size) assertTrue("sorted by magnitude at $i", t.mag[i] >= t.mag[i - 1] - 1e-4f)
        assertEquals(-1.46f, t.mag[0], 0.05f)
        assertEquals(101.29f, t.raDeg[0], 0.03f)
        assertEquals(-16.72f, t.decDeg[0], 0.03f)
        var polaris = false
        val tints = HashSet<Int>()
        for (i in 0 until t.size) {
            tints.add(t.tint[i].toInt())
            val id = t.nameId[i].toInt() and 0xFFFF
            assertTrue("nameId $id out of range", id <= t.names.size)
            if (t.nameOf(i) == "Polaris") polaris = true
        }
        assertTrue("Polaris is in the table", polaris)
        assertEquals(setOf(0, 1, 2), tints)
        for (n in t.names) assertTrue(n.isNotEmpty() && n.length <= 24)
    }

    private val siriusTable: StarTable get() = StarTable.parse(encode(listOf(RawStar(101.2871, -16.7161, -1.46, 0, 1)), listOf("Sirius")))

    @Test fun projectionCache() {
        val layer = StarLayer(siriusTable)
        val base = utc(2027, 1, 15, 20)
        val f = SkyTestKit.frame(base, SkyTestKit.OSLO_LAT, SkyTestKit.OSLO_LON)
        layer.update(f, 155, 52)
        assertEquals(1, layer.projections)
        layer.update(f, 155, 52)
        assertEquals("same frame, same size: no recompute", 1, layer.projections)
        layer.update(SkyTestKit.frame(base + 20_000L, SkyTestKit.OSLO_LAT, SkyTestKit.OSLO_LON), 155, 52)
        assertEquals("same minute: no recompute", 1, layer.projections)
        layer.update(SkyTestKit.frame(base + 60_000L, SkyTestKit.OSLO_LAT, SkyTestKit.OSLO_LON), 155, 52)
        assertEquals("next minute", 2, layer.projections)
        layer.update(SkyTestKit.frame(base + 60_000L, SkyTestKit.OSLO_LAT, SkyTestKit.OSLO_LON), 160, 52)
        assertEquals("new width", 3, layer.projections)
        layer.update(SkyTestKit.frame(base + 60_000L, SkyTestKit.OSLO_LAT, SkyTestKit.OSLO_LON), 160, 60)
        assertEquals("new horizon row", 4, layer.projections)
        layer.update(SkyTestKit.frame(base + 60_000L, SkyTestKit.OSLO_LAT + 0.01, SkyTestKit.OSLO_LON), 160, 60)
        assertEquals("a place change below 0.1 degree rounds to the same key", 4, layer.projections)
        layer.update(SkyTestKit.frame(base + 60_000L, SkyTestKit.OSLO_LAT + 0.5, SkyTestKit.OSLO_LON), 160, 60)
        assertEquals("a new place", 5, layer.projections)
    }

    @Test fun sirius() {
        val layer = StarLayer(siriusTable)
        val f = SkyTestKit.frame(utc(2027, 1, 15, 20), SkyTestKit.OSLO_LAT, SkyTestKit.OSLO_LON)
        layer.update(f, 155, 52)
        assertEquals(1, layer.visibleCount)
        assertEquals(0, layer.starIndex[0])
        assertEquals(53f, layer.screenX[0].toFloat(), 1f)
        assertEquals(47f, layer.screenY[0].toFloat(), 1f)
    }

    @Test fun starBelowTheHorizonIsNotVisible() {
        // Sirius at Oslo in the small hours: it set about 02:20 UTC
        val layer = StarLayer(siriusTable)
        layer.update(SkyTestKit.frame(utc(2027, 1, 16, 4), SkyTestKit.OSLO_LAT, SkyTestKit.OSLO_LON), 155, 52)
        assertEquals(0, layer.visibleCount)
    }

    @Test fun dimStarsAreDroppedByTheMagnitudeLimit() {
        val dim = StarTable.parse(encode(listOf(RawStar(101.2871, -16.7161, 5.0, 1, 0)), emptyList()))
        val layer = StarLayer(dim)
        layer.update(SkyTestKit.frame(utc(2027, 1, 15, 20), SkyTestKit.OSLO_LAT, SkyTestKit.OSLO_LON), 155, 52)
        assertEquals(0, layer.visibleCount)
    }

    @Test fun rotatesWestward() {
        // a star on the meridian, south of the zenith; an hour later the sky has turned 15 degrees and the star is in the west
        val table = StarTable.parse(encode(listOf(RawStar(323.8, 0.0, 1.0, 1, 0)), emptyList()))
        val layer = StarLayer(table)
        layer.update(SkyTestKit.frame(utc(2027, 1, 15, 12), SkyTestKit.ISTANBUL_LAT, SkyTestKit.ISTANBUL_LON), 155, 52)
        assertEquals(1, layer.visibleCount)
        val x0 = layer.screenX[0]
        assertTrue("on the meridian, x $x0", x0 in 76..78)
        layer.update(SkyTestKit.frame(utc(2027, 1, 15, 13), SkyTestKit.ISTANBUL_LAT, SkyTestKit.ISTANBUL_LON), 155, 52)
        assertEquals(1, layer.visibleCount)
        val moved = layer.screenX[0] - x0
        assertTrue("moved right by $moved", moved in 13..17)
    }

    @Test fun alphaRule() {
        // daylight: nothing; the brightest star shows first at dusk, a magnitude 4.5 star needs about 10 degrees of depression
        assertEquals(0f, StarLayer.alphaFor(-1.46f, 0f, 1f), 1e-6f)
        assertTrue(StarLayer.alphaFor(-1.46f, 2f, 1f) > 0f)
        assertEquals(0f, StarLayer.alphaFor(4.5f, 9.9f, 1f), 1e-6f)
        assertTrue(StarLayer.alphaFor(4.5f, 11f, 1f) > 0f)
        assertEquals(1f, StarLayer.alphaFor(4.5f, 20f, 1f), 1e-6f)
        assertEquals(0.5f, StarLayer.alphaFor(4.5f, 20f, 0.5f), 1e-6f)
        assertEquals(StarLayer.TINT_ARGB.size, 3)
    }
}
