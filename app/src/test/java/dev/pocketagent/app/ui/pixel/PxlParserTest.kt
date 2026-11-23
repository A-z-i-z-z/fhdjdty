package dev.pocketagent.app.ui.pixel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Parser rules, and a check that every shipped .pxl file parses and has the sprites the UI asks for. */
class PxlParserTest {
    private val small = """
        # a comment
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
        palette
        x #FFFFFF
        sprite white 1 1
        frame
        x
    """.trimIndent() + "\n"

    @Test fun parsesPaletteFramesAndDurations() {
        val sprites = PxlParser.parse("t.pxl", small)
        assertEquals(listOf("dot", "white"), sprites.map { it.name })
        val dot = sprites[0]
        assertEquals(2, dot.frames.size)
        assertEquals(50, dot.frames[0].durationMs)
        assertEquals(PxlParser.DEFAULT_FRAME_MS, dot.frames[1].durationMs)
        assertEquals(listOf('k', 'o'), dot.palette.map { it.char })
        assertEquals(0xFF102030.toInt(), dot.palette[0].argb)
        assertEquals(0x80FF8800.toInt(), dot.palette[1].argb)
        assertEquals(listOf(0, 1, -1, 0), dot.frames[0].indices.toList())
        assertEquals(listOf(-1, -1, 0, 0), dot.frames[1].indices.toList())
        // a new palette block replaces the old one for later sprites
        assertEquals(listOf('x'), sprites[1].palette.map { it.char })
    }

    @Test fun frameAtLoopsOverDurations() {
        val dot = PxlParser.parse("t.pxl", small)[0]
        assertEquals(0, dot.frameAt(0))
        assertEquals(0, dot.frameAt(49))
        assertEquals(1, dot.frameAt(50))
        assertEquals(1, dot.frameAt(169))
        assertEquals(0, dot.frameAt(170))
        assertEquals(1, dot.msUntilNextFrame(169).coerceAtMost(1))
    }

    @Test fun errorsNameFileAndLine() {
        val badRow = "palette\nk #000000\nsprite a 3 1\nframe\nkk\n"
        val e1 = runCatching { PxlParser.parse("bad.pxl", badRow) }.exceptionOrNull()
        assertTrue(e1 is PxlException)
        assertTrue(e1!!.message!!, e1.message!!.startsWith("bad.pxl:5:") && "2 chars, expected 3" in e1.message!!)

        val badChar = "palette\nk #000000\nsprite a 2 1\nframe\nkz\n"
        val e2 = runCatching { PxlParser.parse("bad.pxl", badChar) }.exceptionOrNull()
        assertTrue(e2!!.message!!, e2.message!!.startsWith("bad.pxl:5:") && "'z'" in e2.message!!)

        val dup = "palette\nk #000000\nsprite a 1 1\nframe\nk\nsprite a 1 1\nframe\nk\n"
        val e3 = runCatching { PxlParser.parse("dup.pxl", dup) }.exceptionOrNull()
        assertTrue(e3!!.message!!, e3.message!!.startsWith("dup.pxl:6:"))

        val badColour = "palette\nk #12345\n"
        val e4 = runCatching { PxlParser.parse("c.pxl", badColour) }.exceptionOrNull()
        assertTrue(e4!!.message!!, e4.message!!.startsWith("c.pxl:2:"))

        val noFrames = "palette\nk #000000\nsprite a 1 1\n"
        assertTrue(runCatching { PxlParser.parse("n.pxl", noFrames) }.exceptionOrNull() is PxlException)

        val truncated = "palette\nk #000000\nsprite a 1 3\nframe\nk\n"
        assertTrue(runCatching { PxlParser.parse("t.pxl", truncated) }.exceptionOrNull() is PxlException)
    }

    private fun assetDir(): File? =
        listOf("src/main/assets/pixel", "app/src/main/assets/pixel").map { File(it) }.firstOrNull { it.isDirectory }

    @Test fun everyShippedFileParsesAndNamesAreUnique() {
        val dir = assetDir() ?: return   // not run from the module directory: nothing to check
        val files = dir.listFiles { f -> f.name.endsWith(".pxl") }!!.sortedBy { it.name }
        assertFalse("no .pxl files in $dir", files.isEmpty())
        val seen = HashMap<String, String>()
        for (f in files) {
            val sprites = PxlParser.parse(f.name, f.readText(Charsets.UTF_8))
            for (s in sprites) {
                val prev = seen.put(s.name, f.name)
                assertTrue("sprite ${s.name} is in both $prev and ${f.name}", prev == null)
                assertTrue("${f.name}: ${s.name} has no frames", s.frames.isNotEmpty())
            }
        }
        fun need(name: String) = assertTrue("missing sprite $name", seen.containsKey(name))
        for (m in MascotMood.entries) need(m.spriteName())
        for (a in AvatarId.entries) need(a.spriteName())
        listOf("fire_cold", "fire_out", "fire_low", "fire_mid", "fire_high", "scene_far", "scene_near", "scene_ground",
            "scene_fore", "scene_moon", "icon_fg", "icon_bg", "icon_mono", "notif_flame",
            "icon_pet_fg", "icon_pet_bg", "icon_pet_mono",
            "icon_flame_fg", "icon_flame_bg", "icon_flame_mono",
            "icon_zagros_fg", "icon_zagros_bg", "icon_zagros_mono",
            "icon_kawa_fg", "icon_kawa_bg", "icon_kawa_mono",
            "icon_roj_fg", "icon_roj_bg", "icon_roj_mono").forEach { need(it) }
        for (ic in dev.pocketagent.app.ui.components.PixelIcon.entries) need(ic.spriteName)
    }

    @Test fun avatarIdOfIsSafe() {
        assertEquals(AvatarId.NEWT, avatarIdOf(null))
        assertEquals(AvatarId.NEWT, avatarIdOf("nonsense"))
        assertEquals(AvatarId.GOAT, avatarIdOf("goat"))
        assertEquals(AvatarId.PARTRIDGE, avatarIdOf(" Partridge "))
        assertNotNull(AvatarId.valueOf("DRAGON"))
    }
}
