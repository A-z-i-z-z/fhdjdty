package dev.pocketagent.app.ui.pixel.stage

import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File

/** The core of the stage must stay plain Kotlin so it runs in JVM tests and can be reused by a widget later. */
class StageCoreIsPureTest {
    private fun pixelDir(): File? =
        listOf(
            "src/main/java/dev/pocketagent/app/ui/pixel",
            "app/src/main/java/dev/pocketagent/app/ui/pixel",
        ).map { File(it) }.firstOrNull { it.isDirectory }

    private val forbidden = listOf("import android.", "import androidx.", "import java.awt.")

    @Test fun noAndroidImportsInStage() {
        val dir = pixelDir()
        Assume.assumeTrue("ui/pixel sources not found from the test's working directory", dir != null)
        val files = ArrayList<File>()
        File(dir!!, "stage").walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.forEach { files.add(it) }
        for (name in listOf("Sprite.kt", "SpriteLoader.kt", "SpriteRaster.kt")) files.add(File(dir, name))
        assertTrue("no stage files found", files.size >= 3)
        val bad = ArrayList<String>()
        for (f in files) {
            assertTrue("${f.path} is missing", f.isFile)
            f.readLines(Charsets.UTF_8).forEachIndexed { i, line ->
                val t = line.trimStart()
                if (forbidden.any { t.startsWith(it) }) bad.add("${f.name}:${i + 1}: $t")
            }
        }
        assertTrue("the stage core must not import platform code: $bad", bad.isEmpty())
    }
}
