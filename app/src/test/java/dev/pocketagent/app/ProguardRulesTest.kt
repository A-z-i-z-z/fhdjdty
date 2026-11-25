package dev.pocketagent.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File

/** The R8 rules of the optimised owner build (app/proguard-rules.pro): what must be kept, and what must never be weakened. */
class ProguardRulesTest {
    private fun rulesFile(): File? =
        listOf(File("proguard-rules.pro"), File("app/proguard-rules.pro"), File("../app/proguard-rules.pro")).firstOrNull { it.isFile }

    /** The rules without comments and blank lines, whitespace collapsed to single spaces. */
    private fun rules(): List<String> {
        val f = rulesFile()
        Assume.assumeTrue("app/proguard-rules.pro not reachable from the test's working directory", f != null)
        return f!!.readLines()
            .map { it.substringBefore('#').trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotEmpty() }
    }

    private fun has(rules: List<String>, needle: String) = rules.any { it.contains(needle) }

    @Test fun requiredRulesArePresent() {
        val r = rules()
        // readable crash traces without a mapping file
        assertTrue("-dontobfuscate", r.contains("-dontobfuscate"))
        assertTrue("line numbers", has(r, "-keepattributes") && has(r, "LineNumberTable") && has(r, "SourceFile"))
        // sherpa-onnx finds classes, fields and methods from native code by name
        assertTrue("sherpa keep", has(r, "-keep class com.k2fsa.sherpa.onnx.**"))
        assertTrue("native methods", has(r, "-keepclasseswithmembernames") && has(r, "native <methods>"))
        // enums stored by name in preferences or JSON
        assertTrue("app enums", has(r, "-keepclassmembers enum dev.pocketagent.app.**"))
        assertTrue("all enums", r.any { it.startsWith("-keepclassmembers enum * {") && it.contains("valueOf") })
        // the rootfs extractor
        assertTrue("tar", has(r, "-keep class org.apache.commons.compress.archivers.tar.**"))
        assertTrue("xz in compress", has(r, "-keep class org.apache.commons.compress.compressors.xz.**"))
        assertTrue("tukaani", has(r, "-keep class org.tukaani.xz.**"))
    }

    @Test fun nothingWeakensTheBuild() {
        val r = rules()
        for (bad in listOf("-dontshrink", "-dontoptimize", "-ignorewarnings")) {
            assertFalse("$bad must not be used", r.any { it.startsWith(bad) })
        }
        // no blanket silencing of missing classes: every -dontwarn names a package
        for (line in r.filter { it.startsWith("-dontwarn") }) {
            val target = line.removePrefix("-dontwarn").trim()
            assertTrue("blanket -dontwarn: $line", target.isNotEmpty() && target != "**" && target != "*" && target.contains('.'))
        }
        // the release type must not keep everything of the app
        assertFalse("keep of the whole app", r.any { it.startsWith("-keep class dev.pocketagent.app.** {") && it.contains("*;") })
    }

    @Test fun theKeepFileForResourcesNamesTheDynamicOnes() {
        val f = listOf(File("src/main/res/raw/keep.xml"), File("app/src/main/res/raw/keep.xml"), File("../app/src/main/res/raw/keep.xml"))
            .firstOrNull { it.isFile }
        Assume.assumeTrue("keep.xml not reachable", f != null)
        val text = f!!.readText()
        // launcher icons chosen at run time, the sound cues, and every string and plural (the text layer looks ids up by name)
        for (token in listOf("@mipmap/ic_launcher*", "@raw/sfx_*", "@string/*", "@plurals/*", "@drawable/ic_tile_fire", "@drawable/ic_notification")) {
            assertTrue("keep.xml lacks $token", text.contains(token))
        }
        assertFalse("strict shrink mode would drop the unreferenced ones", text.contains("shrinkMode=\"strict\""))
    }

    @Test fun theTwinOverlayIconsSurviveResourceShrinkingInTheOptimisedTwin() {
        // twin-release (-PtwinRelease=true) shrinks resources with the twin overlays attached: the overlay launcher icons are
        // chosen at run time (icon switcher) and must match the keep pattern @mipmap/ic_launcher*
        val dir = listOf(File("src/twin/res/mipmap-anydpi-v26"), File("app/src/twin/res/mipmap-anydpi-v26"), File("../app/src/twin/res/mipmap-anydpi-v26"))
            .firstOrNull { it.isDirectory }
        Assume.assumeTrue("twin overlay not reachable", dir != null)
        val names = dir!!.list()!!.toList()
        assertTrue(names.isNotEmpty())
        assertTrue(names.toString(), names.all { it.startsWith("ic_launcher") })
    }
}
