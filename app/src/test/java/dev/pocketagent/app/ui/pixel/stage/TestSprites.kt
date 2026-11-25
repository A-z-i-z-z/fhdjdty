package dev.pocketagent.app.ui.pixel.stage

import dev.pocketagent.app.ui.pixel.PxlParser
import dev.pocketagent.app.ui.pixel.Sprite
import org.junit.Assume
import java.io.File

/**
 * The shipped sprites for tests that need real art: reads the pxl files of the assets folder with the
 * parser (same directory lookup as PxlParserTest). A test that calls [all] is skipped, not failed, when the
 * folder is not there (the tests can run from a copy that has no assets).
 */
internal object TestSprites {
    private fun assetDir(): File? =
        listOf("src/main/assets/pixel", "app/src/main/assets/pixel").map { File(it) }.firstOrNull { it.isDirectory }

    private val loaded: Map<String, Sprite>? by lazy {
        val dir = assetDir()
        if (dir == null) {
            null
        } else {
            val out = LinkedHashMap<String, Sprite>()
            val files = dir.listFiles { f -> f.name.endsWith(".pxl") }!!.sortedBy { it.name }
            for (f in files) {
                for (s in PxlParser.parse(f.name, f.readText(Charsets.UTF_8))) out.putIfAbsent(s.name, s)
            }
            out
        }
    }

    fun all(): SpriteSource {
        val map = loaded
        Assume.assumeTrue("assets/pixel not found from the test's working directory", map != null)
        return SpriteSource { name -> map!![name] ?: throw IllegalArgumentException("Unknown sprite '$name'") }
    }
}

/** Fixed palettes, so the pure tests do not need Compose's colour maths. */
internal object TestPalettes {
    private val FAR_RAMP = intArrayOf(0xFF1F1838.toInt(), 0xFF2D2552.toInt(), 0xFF443D7A.toInt(), 0xFF6B67AA.toInt())

    val DARK = ScenePalette(
        skyTop = 0xFF12081C.toInt(), skyBottom = 0xFF2A1630.toInt(), farRamp = FAR_RAMP.copyOf(),
        glow = 0xFFF7691A.toInt(), fireLow = 0xFFD9381E.toInt(), fireMid = 0xFFF7691A.toInt(), fireHigh = 0xFFFFC857.toInt(),
    )

    val LIGHT = ScenePalette(
        skyTop = 0xFF130A16.toInt(), skyBottom = 0xFF2B1B2E.toInt(), farRamp = FAR_RAMP.copyOf(),
        glow = 0xFFF7691A.toInt(), fireLow = 0xFFD9381E.toInt(), fireMid = 0xFFF7691A.toInt(), fireHigh = 0xFFFFC857.toInt(),
    )
}
