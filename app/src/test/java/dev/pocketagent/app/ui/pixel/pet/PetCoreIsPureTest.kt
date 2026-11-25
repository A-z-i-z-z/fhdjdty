package dev.pocketagent.app.ui.pixel.pet

import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File

/** Pêt's core stays plain Kotlin so it runs in JVM tests: no Android, no Compose, no AWT, no unseeded randomness. */
class PetCoreIsPureTest {
    private fun petDir(): File? =
        listOf(
            "src/main/java/dev/pocketagent/app/ui/pixel/pet",
            "app/src/main/java/dev/pocketagent/app/ui/pixel/pet",
        ).map { File(it) }.firstOrNull { it.isDirectory }

    private val pureFiles = listOf("PetAnim.kt", "PetWorld.kt", "PetBrain.kt", "PetLayer.kt", "PetTaps.kt", "PetDebug.kt")
    private val forbiddenImports = listOf("import android.", "import androidx.", "import java.awt.", "import java.util.Random")
    private val forbiddenText = listOf("Math.random", "System.currentTimeMillis", "System.nanoTime")

    @Test fun noPlatformCodeInTheCore() {
        val dir = petDir()
        Assume.assumeTrue("pet sources not found from the test's working directory", dir != null)
        val bad = ArrayList<String>()
        for (name in pureFiles) {
            val f = File(dir!!, name)
            assertTrue("$name is missing", f.isFile)
            f.readLines(Charsets.UTF_8).forEachIndexed { i, line ->
                val t = line.trimStart()
                if (forbiddenImports.any { t.startsWith(it) }) bad.add("$name:${i + 1}: $t")
                if (!t.startsWith("*") && !t.startsWith("//") && forbiddenText.any { t.contains(it) }) bad.add("$name:${i + 1}: $t")
            }
        }
        assertTrue("the Pêt core must stay pure: $bad", bad.isEmpty())
    }
}
