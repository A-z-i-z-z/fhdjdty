package dev.pocketagent.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File

/**
 * The weather can leave the phone through exactly one door. These tests read the sources and fail when a second
 * door appears: another file that opens a connection, another place that names the weather host, another caller
 * of the transport.
 */
class WeatherIsolationTest {
    private fun mainDir(): File? =
        listOf(
            "src/main/java/dev/pocketagent/app",
            "app/src/main/java/dev/pocketagent/app",
        ).map { File(it) }.firstOrNull { it.isDirectory }

    private fun sources(): List<File> =
        mainDir()!!.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.toList()

    private fun filesContaining(text: String): List<String> =
        sources().filter { it.readText(Charsets.UTF_8).contains(text) }.map { it.name }.sorted()

    private fun occurrences(text: String): Int =
        sources().sumOf { f -> Regex.escape(text).toRegex().findAll(f.readText(Charsets.UTF_8)).count() }

    @Test fun onlyKnownFilesOpenConnections() {
        Assume.assumeTrue("sources not found from the test's working directory", mainDir() != null)
        val allowed = setOf("HermesApi.kt", "SupertonicModelStore.kt", "WeatherStore.kt")
        val found = filesContaining("openConnection")
        assertTrue("files that open a connection: $found", allowed.containsAll(found))
        assertFalse("the pure weather file must not open a connection", found.contains("Weather.kt"))
        assertFalse(found.contains("Places.kt"))
    }

    @Test fun theWeatherHostIsNamedInOnePlace() {
        Assume.assumeTrue("sources not found from the test's working directory", mainDir() != null)
        assertEquals(listOf("Weather.kt"), filesContaining("api.met.no"))
    }

    @Test fun theTransportIsCalledFromOnePlace() {
        Assume.assumeTrue("sources not found from the test's working directory", mainDir() != null)
        // the one call of the transport, inside WeatherClient.fetch
        assertEquals(1, occurrences("transport.get("))
        assertEquals(listOf("Weather.kt"), filesContaining("transport.get("))
        // the one caller of WeatherClient.fetch, inside WeatherEngine.refreshIfDue
        assertEquals(1, occurrences("WeatherClient.fetch("))
        assertEquals(listOf("Weather.kt"), filesContaining("WeatherClient.fetch("))
    }

    @Test fun pureFilesImportNoPlatformCode() {
        Assume.assumeTrue("sources not found from the test's working directory", mainDir() != null)
        val names = setOf("Places.kt", "Weather.kt")
        val bad = ArrayList<String>()
        for (f in sources().filter { it.name in names }) {
            f.readLines(Charsets.UTF_8).forEachIndexed { i, line ->
                val t = line.trimStart()
                if (t.startsWith("import android.") || t.startsWith("import androidx.")) bad.add("${f.name}:${i + 1}")
            }
        }
        assertTrue("platform imports in pure files: $bad", bad.isEmpty())
    }
}
