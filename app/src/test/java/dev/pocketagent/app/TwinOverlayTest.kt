package dev.pocketagent.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File

/**
 * The twin overlays (app/src/twin/res) must stay in step with the owner's resources, and nothing in the main
 * manifest may name the package, or the twin's different package id would break it (Wave 0 contract, 1.2 and 3.1).
 */
class TwinOverlayTest {
    private val appDir: File? = listOf(File("."), File("app"), File("../app")).firstOrNull { File(it, "src/main/AndroidManifest.xml").isFile }

    private fun main(path: String): File { Assume.assumeTrue("app directory not reachable", appDir != null); return File(appDir, "src/main/$path") }
    private fun twin(path: String): File { Assume.assumeTrue("app directory not reachable", appDir != null); return File(appDir, "src/twin/$path") }

    private fun attrs(text: String, name: String): List<String> =
        Regex("android:$name=\"([^\"]*)\"").findAll(text).map { it.groupValues[1] }.toList()

    private val ownerPackage = "dev.pocketagent.app"
    private val twinPackage = "dev.pocketagent.app.debug"

    @Test fun shortcutsOverlayMatchesMain() {
        val m = main("res/xml/shortcuts.xml").readText()
        val t = twin("res/xml/shortcuts.xml").readText()
        for (attr in listOf("shortcutId", "action", "targetClass", "icon", "shortcutShortLabel", "shortcutLongLabel", "enabled")) {
            assertEquals("attribute $attr", attrs(m, attr), attrs(t, attr))
        }
        val mainPackages = attrs(m, "targetPackage")
        val twinPackages = attrs(t, "targetPackage")
        assertTrue(mainPackages.isNotEmpty())
        assertTrue("main shortcuts target the owner's package", mainPackages.all { it == ownerPackage })
        assertEquals(mainPackages.size, twinPackages.size)
        assertTrue("twin shortcuts target the twin", twinPackages.all { it == twinPackage })
        // the class stays the namespace class, not a class of the twin's package id
        assertTrue(attrs(t, "targetClass").all { it.startsWith("$ownerPackage.") && !it.startsWith("$twinPackage.") })
    }

    @Test fun everyLauncherIconHasATwinOverlay() {
        val mainDir = main("res/mipmap-anydpi-v26")
        val twinDir = twin("res/mipmap-anydpi-v26")
        val mainNames = mainDir.list()!!.sorted()
        val twinNames = twinDir.list()!!.sorted()
        assertEquals(12, mainNames.size)
        assertEquals(mainNames, twinNames)
        for (name in twinNames) {
            val m = File(mainDir, name).readText()
            val t = File(twinDir, name).readText()
            // only the foreground changes
            assertEquals("background of $name", attrs(m, "drawable").first(), attrs(t, "drawable").first())
            assertEquals("monochrome of $name", attrs(m, "drawable").last(), attrs(t, "drawable").last())
            val fg = Regex("<foreground[^>]*android:drawable=\"@drawable/(twin_fg_[a-z]+)\"").find(t)
            assertTrue("$name: foreground must be a twin_fg drawable", fg != null)
            val fgFile = twin("res/drawable/${fg!!.groupValues[1]}.xml")
            assertTrue("$name: ${fgFile.name} must exist", fgFile.isFile)
            // the twin foreground is the owner's own foreground plus the badge
            val ownerFg = Regex("<foreground[^>]*android:drawable=\"@drawable/([a-z_]+)\"").find(m)!!.groupValues[1]
            val layered = fgFile.readText()
            assertTrue("${fgFile.name} must contain $ownerFg", layered.contains("@drawable/$ownerFg\""))
            assertTrue("${fgFile.name} must contain the badge", layered.contains("@drawable/twin_badge\""))
        }
        assertTrue(twin("res/drawable/twin_badge.xml").isFile)
    }

    @Test fun labelsAreOverriddenInAllThreeLocales() {
        val expected = setOf("app_name", "srf_ask_agiri", "srf_tile_label")
        val texts = listOf("values", "values-tr", "values-fr").map { folder ->
            val f = twin("res/$folder/strings_build.xml")
            assertTrue("${f.path} must exist", f.isFile)
            val map = Regex("<string name=\"([^\"]+)\">([^<]*)</string>").findAll(f.readText()).associate { it.groupValues[1] to it.groupValues[2] }
            assertEquals("keys in $folder", expected, map.keys)
            map
        }
        assertEquals(texts[0], texts[1])
        assertEquals(texts[0], texts[2])
        assertTrue(texts[0]["app_name"]!!.contains("Test"))
        // the twin's entries differ from the owner's, so the two apps can be told apart in menus
        val ownerAsk = Regex("name=\"srf_ask_agiri\">([^<]*)<").find(main("res/values/strings_surface.xml").readText())!!.groupValues[1]
        assertTrue(texts[0]["srf_ask_agiri"] != ownerAsk)
    }

    @Test fun theManifestNamesNoPackage() {
        val manifest = main("AndroidManifest.xml").readText()
        assertFalse("the manifest must use relative class names and placeholders only", manifest.contains(ownerPackage))
        // every launcher entry shows the app name, which the twin overrides
        val application = Regex("<application\\b[^>]*>").find(manifest)!!.value
        assertTrue(application.contains("android:label=\"@string/app_name\""))
        val aliases = Regex("<activity-alias\\b[^>]*>").findAll(manifest).map { it.value }.toList()
        assertEquals(6, aliases.size)
        for (a in aliases) assertTrue("alias label: $a", a.contains("android:label=\"@string/app_name\""))
        // authority and process names derive from the package id
        assertTrue(manifest.contains("android:authorities=\"\${applicationId}.fileprovider\""))
        assertFalse(manifest.contains("android:authorities=\"dev."))
    }

    @Test fun theBuildScriptAttachesTheOverlayOnlyWithoutOwnerDebug() {
        Assume.assumeTrue("app directory not reachable", appDir != null)
        val gradle = File(appDir, "build.gradle.kts").readText()
        assertTrue(gradle.contains("src/twin/res"))
        assertTrue(Regex("if\\s*\\(\\s*!ownerDebug\\s*\\)\\s*\\{[^}]*src/twin/res").containsMatchIn(gradle))
        // overlays do not live in src/debug: that folder is the stretched test language's, shared by both debug variants
        assertFalse(File(appDir, "src/debug/res/xml/shortcuts.xml").exists())
        assertFalse(File(appDir, "src/debug/res/drawable/twin_badge.xml").exists())
    }
}
