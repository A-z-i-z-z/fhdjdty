package dev.pocketagent.app.text

import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

/**
 * The second line of defence behind tools/strings-check.ps1: English, Turkish and French hold the same files and keys,
 * with the same placeholders. Entries marked translatable="false" are exempt.
 */
class ResourceParityTest {
    @Test fun keysAndPlaceholders() {
        val dirs = ResXml.languageDirs()
        Assume.assumeTrue("res folders not reachable from the test's working directory", dirs.containsKey("en"))
        Assume.assumeTrue("values-tr and values-fr are expected", dirs.containsKey("tr") && dirs.containsKey("fr"))

        val english = ResXml.stringsFiles(dirs.getValue("en")).associate { f -> f.name to ResXml.parse(f).associateBy { it.name } }
        val problems = ArrayList<String>()
        for (lang in listOf("tr", "fr")) {
            val files = ResXml.stringsFiles(dirs.getValue(lang)).associate { f -> f.name to ResXml.parse(f).associateBy { it.name } }
            for (name in english.keys - files.keys) problems += "$lang: file $name is missing"
            for (name in files.keys - english.keys) problems += "$lang: file $name is not in English"
            for ((fileName, enEntries) in english) {
                val own = files[fileName] ?: continue
                for ((key, en) in enEntries) {
                    if (!en.translatable) continue
                    val e = own[key]
                    if (e == null) {
                        problems += "$lang: $fileName:$key is missing"
                        continue
                    }
                    if (e.isPlurals != en.isPlurals) {
                        problems += "$lang: $fileName:$key is a ${if (e.isPlurals) "plurals" else "string"} here and the other kind in English"
                        continue
                    }
                    if (en.isPlurals) {
                        val enOther = ResXml.placeholders(en.forms["other"] ?: "")
                        val own1 = ResXml.placeholders(e.forms["other"] ?: "")
                        if (enOther != own1) problems += "$lang: $fileName:$key other has placeholders $own1, English $enOther"
                        for ((q, text) in e.forms) {
                            val ph = ResXml.placeholders(text).toMutableList()
                            for (p in enOther) ph.remove(p)
                            if (ph.isNotEmpty()) problems += "$lang: $fileName:$key form $q has the extra placeholders $ph"
                        }
                        if (!e.forms.containsKey("other")) problems += "$lang: $fileName:$key has no other form"
                    } else {
                        val a = ResXml.placeholders(en.forms["_"] ?: "")
                        val b = ResXml.placeholders(e.forms["_"] ?: "")
                        if (a != b) problems += "$lang: $fileName:$key has placeholders $b, English $a"
                    }
                }
                for ((key, e) in own) {
                    if (e.translatable && key !in enEntries) problems += "$lang: $fileName:$key is not in English (orphan)"
                }
            }
        }
        assertTrue("string resources are out of step:\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}
