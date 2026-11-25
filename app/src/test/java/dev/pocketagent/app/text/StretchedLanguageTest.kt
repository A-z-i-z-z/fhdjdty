package dev.pocketagent.app.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File

/**
 * Keeps the stretched test language (values-en-rXZ, debug builds) out of a normal English screen (defect M1 of the
 * Wave 0 phone proof).
 *
 * Android accepts values-en-rXZ for every English request ("en", "en-CA", "en-GB": the region of a folder is not
 * part of the match), and a folder that names the language beats the plain values folder. The region-less values-en
 * copy wins over values-en-rXZ. So a key that only values-en-rXZ holds comes out stretched ("[AgirîAî]") on a
 * normal English screen. The two folders must therefore hold exactly the same keys, and the plain copy must carry
 * the English text exactly as the values folder has it. The texts that the twin overlay (src/twin/res) overrides are
 * in neither folder, so the twin's own text answers from its values folder.
 *
 * tools/make-test-language.ps1 writes both folders; tools/strings-check.ps1 and its -Check repeat these rules.
 */
class StretchedLanguageTest {
    private val mainDir: File? = ResXml.mainDir()
    private val debugRes: File? get() = mainDir?.parentFile?.let { File(it, "debug/res") }
    private val twinRes: File? get() = mainDir?.parentFile?.let { File(it, "twin/res") }

    private fun folder(name: String): File? = debugRes?.let { File(it, name) }?.takeIf { it.isDirectory }

    /** file name -> (key -> entry) of one strings folder. */
    private fun read(dir: File): Map<String, Map<String, ResEntry>> =
        ResXml.stringsFiles(dir).associate { f -> f.name to ResXml.parse(f).associateBy { it.name } }

    private fun keysOf(m: Map<String, Map<String, ResEntry>>): Set<String> =
        m.flatMap { (file, entries) -> entries.keys.map { "$file:$it" } }.toSet()

    private fun twinNames(): Set<String> {
        val res = twinRes ?: return emptySet()
        if (!res.isDirectory) return emptySet()
        val names = HashSet<String>()
        for (d in res.listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()) {
            for (f in ResXml.stringsFiles(d)) for (e in ResXml.parse(f)) names += e.name
        }
        return names
    }

    @Test fun theTwoGeneratedFoldersHoldTheSameKeys() {
        val stretched = folder("values-en-rXZ")
        val plain = folder("values-en")
        Assume.assumeTrue("debug folders not reachable from the test's working directory", stretched != null && plain != null)
        val a = keysOf(read(stretched!!))
        val b = keysOf(read(plain!!))
        assertTrue("both generated folders hold keys", a.isNotEmpty() && b.isNotEmpty())
        assertEquals(
            "keys only in values-en-rXZ would leak the stretched text into English; run tools\\make-test-language.ps1",
            emptySet<String>(), a - b,
        )
        assertEquals("keys only in values-en; run tools\\make-test-language.ps1", emptySet<String>(), b - a)
    }

    @Test fun noKeyTheTwinOverlayOverridesIsInAGeneratedFolder() {
        val stretched = folder("values-en-rXZ")
        val plain = folder("values-en")
        Assume.assumeTrue("debug folders not reachable from the test's working directory", stretched != null && plain != null)
        val twin = twinNames()
        Assume.assumeTrue("twin overlay not reachable", twin.isNotEmpty())
        // the three names the overlay is known to carry (TwinOverlayTest checks the overlay itself)
        assertTrue(twin.containsAll(listOf("app_name", "srf_ask_agiri", "srf_tile_label")))
        for (dir in listOf(stretched!!, plain!!)) {
            val keys = read(dir).values.flatMap { it.keys }.toSet()
            assertEquals("${dir.name} must not hold a text the twin overlay overrides", emptySet<String>(), keys intersect twin)
        }
    }

    @Test fun theAppNameCanNeverComeFromTheStretchedLanguage() {
        val stretched = folder("values-en-rXZ")
        Assume.assumeTrue("debug folders not reachable from the test's working directory", stretched != null)
        val keys = read(stretched!!).values.flatMap { it.keys }.toSet()
        assertTrue("app_name is a name, never stretched", "app_name" !in keys)
    }

    @Test fun thePlainCopyIsTheDefaultEnglishExactly() {
        val plain = folder("values-en")
        val english = ResXml.languageDirs()["en"]
        Assume.assumeTrue("folders not reachable from the test's working directory", plain != null && english != null)
        val own = read(plain!!)
        val main = read(english!!)
        val problems = ArrayList<String>()
        for ((file, entries) in own) {
            val mainEntries = main[file]
            if (mainEntries == null) {
                problems += "$file is not in the English values folder"
                continue
            }
            for ((key, e) in entries) {
                val m = mainEntries[key]
                if (m == null) { problems += "$file:$key is not in the English values folder"; continue }
                if (m.isPlurals != e.isPlurals) { problems += "$file:$key is of another kind"; continue }
                if (m.forms != e.forms) problems += "$file:$key differs from the default English text"
            }
        }
        assertTrue("the plain English copy has drifted:\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}
