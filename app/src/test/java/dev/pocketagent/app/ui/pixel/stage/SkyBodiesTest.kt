package dev.pocketagent.app.ui.pixel.stage

import dev.pocketagent.app.ui.pixel.stage.SkyTestKit.OSLO_LAT
import dev.pocketagent.app.ui.pixel.stage.SkyTestKit.OSLO_LON
import dev.pocketagent.app.ui.pixel.stage.SkyTestKit.frame
import dev.pocketagent.app.ui.pixel.stage.SkyTestKit.utc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class SkyBodiesTest {
    private val size = SkyBodies.MOON_SIZE

    private fun litCount(img: IntArray): Int = img.count { (it ushr 24) == 255 }

    private fun litColumns(img: IntArray): List<Int> {
        val out = ArrayList<Int>()
        for (i in img.indices) if ((img[i] ushr 24) == 255) out.add(i % size - size / 2)
        return out
    }

    @Test fun moonSixteenShapes() {
        val images = (0 until 16).map { SkyBodies.moonImage(it, it in 1..7, true, 0f) }
        // all sixteen differ (the waxing half is lit on the right, the waning half on the left)
        assertEquals(16, images.map { it.toList() }.toSet().size)
        for (img in images) assertEquals(size * size, img.size)
        assertEquals(0, litCount(images[0]))
        for (i in 1..15) assertTrue("phase $i has a lit part", litCount(images[i]) > 0)
        for (i in 1..7) assertEquals("phase $i and ${16 - i} are mirror images", litCount(images[i]), litCount(images[16 - i]))
        for (i in 0..15) assertTrue(litCount(images[8]) >= litCount(images[i]))
        // the dark disc is always there as earthshine, nothing outside the disc
        val disc = images[0].count { it != 0 }
        assertTrue("disc of about 100 pixels, got $disc", disc in 90..110)
        for (img in images) assertEquals(disc, img.count { it != 0 })
        assertEquals(0, images[0][0])
    }

    @Test fun moonHemisphere() {
        // looking south from the northern hemisphere a waxing moon is lit on the right half, a waning moon on the left
        assertTrue(litColumns(SkyBodies.moonImage(4, true, true, 0f)).all { it > 0 })
        assertTrue(litColumns(SkyBodies.moonImage(12, false, true, 0f)).all { it < 0 })
        // looking north from the southern hemisphere it is the other way round
        assertTrue(litColumns(SkyBodies.moonImage(4, true, false, 0f)).all { it < 0 })
        assertTrue(litColumns(SkyBodies.moonImage(12, false, false, 0f)).all { it > 0 })
        // the quarters are equal halves of the disc
        assertEquals(litCount(SkyBodies.moonImage(4, true, true, 0f)), litCount(SkyBodies.moonImage(12, false, true, 0f)))
    }

    @Test fun redMoonAndCache() {
        val pale = SkyBodies.moonImage(8, false, true, 0f)
        val red = SkyBodies.moonImage(8, false, true, 1f)
        assertEquals(0xFFFFF6DC.toInt(), pale[(size / 2) * size + size / 2])
        assertEquals(0xFF8A3A2A.toInt(), red[(size / 2) * size + size / 2])
        assertSame(pale, SkyBodies.moonImage(8, false, true, 0f))
        assertSame(red, SkyBodies.moonImage(8, true, false, 1f)) // the full moon has no side: waxing and northern flags that give the same side share a key
    }

    // ---- skyBodyAt ----

    private val boxW = 1080f
    private val boxH = 1050f

    private fun deviceOf(stagePacked: Int): FloatArray {
        val grid = StageGrid.forScene(boxW, boxH)!!
        val x = stagePacked and 0xFFFF
        val y = stagePacked ushr 16
        return floatArrayOf(grid.offsetX + (x + 0.5f) * grid.scale, grid.offsetY + (y + 0.5f) * grid.scale)
    }

    private fun horizon(): Int = SkyProjection.horizonRow(StageGrid.forScene(boxW, boxH)!!.height - 34)

    private fun siriusTable(): StarTable {
        val out = ByteArrayOutputStream()
        out.write("AGST".toByteArray(Charsets.US_ASCII))
        out.write(1)
        out.write(45)
        out.write(1)
        out.write(0)
        out.write(1)
        out.write(0)
        out.write(0)
        out.write(0)
        val ra = Math.round(101.2871 / 360.0 * 65536.0).toInt()
        val dec = Math.round(-16.7161 / 90.0 * 32767.0).toInt() and 0xFFFF
        out.write(ra and 0xFF)
        out.write(ra shr 8)
        out.write(dec and 0xFF)
        out.write(dec shr 8)
        out.write(-15 and 0xFF)
        out.write(0)
        out.write(1)
        out.write(0)
        out.write(6)
        out.write("Sirius".toByteArray(Charsets.UTF_8))
        return StarTable.parse(out.toByteArray())
    }

    @Test fun tableForTheTapTestParses() {
        assertEquals(1, siriusTable().size)
        assertEquals("Sirius", siriusTable().nameOf(0))
    }

    @Test fun tapOnASiriusPixelNamesTheStar() {
        val f = frame(utc(2027, 1, 15, 20), OSLO_LAT, OSLO_LON)
        val rd = DoubleArray(2)
        SkyClock.precess(101.2871, -16.7161, f.epochMs, rd)
        val aa = SkyClock.altAz(rd[0], rd[1], f.lstDeg, f.latDeg)
        val packed = SkyProjection.project(aa.azDeg, aa.altDeg, f.facingAz, StageGrid.forScene(boxW, boxH)!!.width, horizon())
        assertTrue(packed != SkyProjection.NONE)
        val d = deviceOf(packed)
        val hit = skyBodyAt(f, siriusTable(), boxW, boxH, d[0], d[1])
        assertNotNull(hit)
        assertEquals(0, hit!!.kind)
        assertEquals("Sirius", hit.name)
        // a tap one stage pixel away still counts, one ten pixels away does not
        assertEquals(0, skyBodyAt(f, siriusTable(), boxW, boxH, d[0] + 7f, d[1])!!.kind)
        assertNull(skyBodyAt(f, siriusTable(), boxW, boxH, d[0] + 70f, d[1]))
    }

    @Test fun tapOnTheMoonAndOnEmptySky() {
        val f = frame(utc(2025, 3, 14, 23), 45.0, 10.0)
        assertTrue(f.moon.altDeg > 30f)
        val grid = StageGrid.forScene(boxW, boxH)!!
        val packed = SkyProjection.project(f.moon.azDeg.toDouble(), f.moon.altDeg.toDouble(), f.facingAz, grid.width, horizon())
        assertTrue(packed != SkyProjection.NONE)
        val d = deviceOf(packed)
        val hit = skyBodyAt(f, StarTable.EMPTY, boxW, boxH, d[0], d[1])
        assertNotNull(hit)
        assertEquals(2, hit!!.kind)
        assertEquals(f.moonPhase16, hit.phase16)
        // the top left corner of the sky holds nothing
        assertNull(skyBodyAt(f, StarTable.EMPTY, boxW, boxH, 5f, 5f))
        // and a surface that is too small gives nothing at all
        assertNull(skyBodyAt(f, StarTable.EMPTY, 4f, 4f, 2f, 2f))
    }

    @Test fun tapOnThePlanetAndTheSun() {
        val night = frame(utc(2025, 1, 16, 2), 45.0, 10.0)
        val mars = night.planets.firstOrNull { it.id == 2 }
        assertNotNull(mars)
        val grid = StageGrid.forScene(boxW, boxH)!!
        val packed = SkyProjection.project(mars!!.azDeg.toDouble(), mars.altDeg.toDouble(), night.facingAz, grid.width, horizon())
        assertTrue(packed != SkyProjection.NONE)
        val d = deviceOf(packed)
        val hit = skyBodyAt(night, StarTable.EMPTY, boxW, boxH, d[0], d[1])
        assertNotNull(hit)
        assertEquals(1, hit!!.kind)
        assertEquals(2, hit.planetId)
        // the fixed day sky has a sun at altitude 52 above the south
        val day = frame(utc(2027, 3, 21, 12), 41.0, 29.0, mode = SkyMode.DAY)
        val sunPacked = SkyProjection.project(day.sun.azDeg.toDouble(), day.sun.altDeg.toDouble(), day.facingAz, grid.width, horizon())
        val s = deviceOf(sunPacked)
        val sunHit = skyBodyAt(day, StarTable.EMPTY, boxW, boxH, s[0], s[1])
        assertNotNull(sunHit)
        assertEquals(3, sunHit!!.kind)
    }

    @Test fun starsAreNotHitInDaylight() {
        // the same instant as the tap test, where Sirius is up (about 8 degrees) and is hit at night; the fixed day sky
        // has no darkness, so the star must not be hit now (the sun stands at altitude 52, far from it)
        val f = frame(utc(2027, 1, 15, 20), OSLO_LAT, OSLO_LON, mode = SkyMode.DAY)
        val rd = DoubleArray(2)
        SkyClock.precess(101.2871, -16.7161, f.epochMs, rd)
        val aa = SkyClock.altAz(rd[0], rd[1], f.lstDeg, f.latDeg)
        val grid = StageGrid.forScene(boxW, boxH)!!
        val packed = SkyProjection.project(aa.azDeg, aa.altDeg, f.facingAz, grid.width, horizon())
        assertTrue(packed != SkyProjection.NONE)
        val d = deviceOf(packed)
        assertNull(skyBodyAt(f, siriusTable(), boxW, boxH, d[0], d[1]))
        // the same table and place at night does find it
        val night = frame(utc(2027, 1, 15, 20), OSLO_LAT, OSLO_LON)
        assertNotNull(skyBodyAt(night, siriusTable(), boxW, boxH, d[0], d[1]))
    }
}
