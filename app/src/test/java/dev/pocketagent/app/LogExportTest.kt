package dev.pocketagent.app

import dev.pocketagent.app.diag.LogExport
import dev.pocketagent.app.diag.Redactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

class LogExportTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun unzip(bytes: ByteArray): LinkedHashMap<String, String> {
        val out = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                out[e.name] = String(z.readBytes(), Charsets.UTF_8)
            }
        }
        return out
    }

    private fun zipOf(sources: List<LogExport.Source>, redact: (String) -> String = { it }, max: Int = 1_000_000): LinkedHashMap<String, String> {
        val bos = ByteArrayOutputStream()
        LogExport.zip(bos, sources, redact, max)
        return unzip(bos.toByteArray())
    }

    @Test fun zipHasFlatNamedEntries() {
        val m = zipOf(listOf(
            LogExport.Source("app.log") { "one" },
            LogExport.Source("runtime.log") { "two" },
            LogExport.Source("report.txt") { "three" },
        ))
        assertEquals(listOf("app.log", "runtime.log", "report.txt"), m.keys.toList())
        assertEquals("one", m["app.log"])
        assertEquals("three", m["report.txt"])
        assertTrue(m.keys.none { it.contains('/') || it.contains('\\') })
    }

    @Test fun everyEntryIsRedacted() {
        val secret = "s3cr3t-value-0123456789abcdef"
        val r = Redactor()
        r.addSecret("api-server-key", secret)
        val bearer = "Authorization: Bearer abc.DEF-123_xyz~tokenvalue"
        val text = "log line with $secret in it\n$bearer\nAPI_SERVER_KEY=$secret\n"
        val sources = listOf("app.log", "app.log.1", "runtime.log", "runtime.log.1", "last-launch.txt", "report.txt")
            .map { name -> LogExport.Source(name) { text } }
        val m = zipOf(sources, redact = { r.redact(it) })
        assertEquals(6, m.size)
        for ((name, body) in m) {
            assertFalse("$name leaks the secret", body.contains(secret))
            assertFalse("$name leaks the token", body.contains("abc.DEF-123_xyz"))
            assertTrue("$name keeps the surrounding text", body.contains("log line with"))
        }
    }

    @Test fun missingSourceIsSkipped() {
        val m = zipOf(listOf(
            LogExport.Source("app.log") { "kept" },
            LogExport.Source("app.log.1") { null },
            LogExport.Source("runtime.log.1") { null },
        ))
        assertEquals(listOf("app.log"), m.keys.toList())
    }

    @Test fun throwingSourceBecomesANoteNotAFailure() {
        val m = zipOf(listOf(
            LogExport.Source("a.txt") { throw java.io.IOException("secret-detail-never-in-the-zip") },
            LogExport.Source("b.txt") { "ok" },
        ))
        assertEquals(listOf("a.txt", "b.txt"), m.keys.toList())
        assertTrue(m["a.txt"]!!.contains("IOException"))
        assertFalse(m["a.txt"]!!.contains("secret-detail"))
    }

    @Test fun bigEntryKeepsItsTail() {
        val body = (1..2000).joinToString("\n") { "line-$it" }
        val m = zipOf(listOf(LogExport.Source("app.log") { body }), max = 500)
        val got = m["app.log"]!!
        assertTrue(got.contains("line-2000"))
        assertFalse(got.contains("line-1\n"))
        assertTrue(got.startsWith("[...cut, last 500 of ${body.length} chars...]"))
        assertEquals(500, got.substringAfter("]\n").length)
    }

    @Test fun redactionHappensBeforeTheCut() {
        // A secret that straddles the cut must not survive as a fragment: it is replaced first, then the text is cut.
        val secret = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val r = Redactor()
        r.addSecret("k", secret)
        val body = "x".repeat(100) + secret + "y".repeat(10)
        val got = zipOf(listOf(LogExport.Source("a") { body }), redact = { r.redact(it) }, max = 30)["a"]!!
        assertFalse(got.contains("OPQRSTUVWXYZ0123456789"))
    }

    @Test fun entryNamesMustBeFlat() {
        val bos = ByteArrayOutputStream()
        val bad = runCatching { LogExport.zip(bos, listOf(LogExport.Source("../x") { "a" }), { it }) }
        assertTrue(bad.exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun envAndYamlAreNeverSources() {
        val logs = tmp.newFolder("logs")
        val state = tmp.newFolder("state")
        File(logs, "app.log").writeText("app")
        File(logs, "runtime.log").writeText("rt")
        File(logs, ".env").writeText("API_SERVER_KEY=do-not-export")
        File(logs, "config.yaml").writeText("api_key: do-not-export")
        val last = File(state, "last-launch.txt").also { it.writeText("launch") }
        val sources = LogExport.sources(logs, last) { "report" }
        assertEquals(
            listOf("app.log", "app.log.1", "runtime.log", "runtime.log.1", "last-launch.txt", "report.txt"),
            sources.map { it.entryName },
        )
        val m = zipOf(sources)
        // The two files that exist plus the last launch line and the report; the previous logs are absent and skipped.
        assertEquals(listOf("app.log", "runtime.log", "last-launch.txt", "report.txt"), m.keys.toList())
        assertTrue(m.values.none { it.contains("do-not-export") })
        assertEquals("app", m["app.log"])
        assertEquals("report", m["report.txt"])
    }

    @Test fun readTailReturnsNullForAMissingFileAndTheEndOfABigOne() {
        assertEquals(null, LogExport.readTail(File(tmp.root, "nope.log")))
        val f = File(tmp.root, "big.log")
        f.writeText((1..1000).joinToString("\n") { "row-$it" })
        val tail = LogExport.readTail(f, maxBytes = 200)
        assertNotNull(tail)
        assertTrue(tail!!.endsWith("row-1000"))
        assertTrue(tail.length <= 200)
        // Cut at a line start: no half line at the beginning.
        assertTrue(tail.startsWith("row-"))
    }
}
