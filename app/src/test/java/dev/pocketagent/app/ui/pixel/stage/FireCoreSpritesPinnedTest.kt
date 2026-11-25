package dev.pocketagent.app.ui.pixel.stage

import dev.pocketagent.app.ui.pixel.Sprite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * The five fire sprites are the core of the living fire and are never edited: this test pins their size,
 * palette letters, frame counts, frame times and a SHA-256 of every frame's rows. The first half needs no
 * golden file (the numbers are the contract's); the second half compares with
 * `src/test/resources/fire/core-sprites.golden`, which INTEGRATE writes by running the tests once with
 * `FIRE_GOLDEN_WRITE` set to the ABSOLUTE path of that file (the tests run in a copy, so a relative path
 * would land in the copy). The test is skipped, not failed, while the golden does not exist.
 */
class FireCoreSpritesPinnedTest {
    private val sprites: SpriteSource by lazy { TestSprites.all() }
    private val names = listOf("fire_cold", "fire_out", "fire_low", "fire_mid", "fire_high")

    @Test fun sizesFramesAndTimesAreTheContractsNumbers() {
        val frameCounts = listOf(1, 4, 6, 8, 8)
        val frameTimes = listOf(1000, 260, 130, 110, 95)
        for ((i, name) in names.withIndex()) {
            val s = sprites[name]
            assertEquals(name, 32, s.width)
            assertEquals(name, 36, s.height)
            assertEquals(name, frameCounts[i], s.frames.size)
            for (f in s.frames) assertEquals("$name frame time", frameTimes[i], f.durationMs)
        }
    }

    @Test fun paletteLettersAreTheFilesOwn() {
        val expected = "oshwWaAlLgrfmyc".toSet() + 'S'
        for (name in names) {
            assertEquals(name, expected, sprites[name].palette.map { it.char }.toSet())
        }
    }

    @Test fun flameLettersAreInThePalette() {
        val letters = sprites["fire_mid"].palette.map { it.char }.toSet()
        for (c in FlameShape.FLAME_LETTERS) assertTrue("flame letter $c", c in letters)
    }

    private fun describe(sprite: Sprite): String {
        val md = MessageDigest.getInstance("SHA-256")
        for ((fi, frame) in sprite.frames.withIndex()) {
            val rows = StringBuilder()
            for (y in 0 until sprite.height) {
                for (x in 0 until sprite.width) {
                    val idx = frame.indices[y * sprite.width + x]
                    rows.append(if (idx < 0) '.' else sprite.palette[idx].char)
                }
                rows.append('\n')
            }
            rows.append("--").append(fi).append('\n')
            md.update(rows.toString().toByteArray(Charsets.UTF_8))
        }
        val hex = md.digest().joinToString("") { String.format(Locale.ROOT, "%02x", it) }
        val times = sprite.frames.joinToString(",") { it.durationMs.toString() }
        val letters = sprite.palette.map { it.char }.sorted().joinToString("")
        return "sprite ${sprite.name} ${sprite.width}x${sprite.height} frames=${sprite.frames.size} times=$times palette=$letters sha256=$hex"
    }

    private fun currentGolden(): String = names.joinToString("\n", postfix = "\n") { describe(sprites[it]) }

    @Test fun matchesTheGolden() {
        val current = currentGolden()
        val writeTo = System.getenv("FIRE_GOLDEN_WRITE")
        if (!writeTo.isNullOrBlank()) {
            val file = File(writeTo)
            assertTrue("FIRE_GOLDEN_WRITE must be an absolute path, it is '$writeTo'", file.isAbsolute)
            file.parentFile?.mkdirs()
            file.writeText(current, Charsets.UTF_8)
            return
        }
        val stream = javaClass.getResourceAsStream("/fire/core-sprites.golden")
        Assume.assumeTrue("fire/core-sprites.golden is not there yet (INTEGRATE writes it)", stream != null)
        val golden = stream!!.use { it.readBytes().toString(Charsets.UTF_8) }.replace("\r\n", "\n")
        assertEquals("the fire sprites changed: nobody edits fire.pxl or build_fire_pxl", golden.trimEnd(), current.trimEnd())
    }
}
