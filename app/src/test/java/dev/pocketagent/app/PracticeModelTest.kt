package dev.pocketagent.app

import dev.pocketagent.app.runtime.PracticeModel
import dev.pocketagent.app.runtime.ProviderConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PracticeModelTest {
    @get:Rule val tmp = TemporaryFolder()

    /** A file of the repository, or null when the test runs from a copy that does not have it. */
    private fun repoFile(relative: String): File? =
        listOf(File("../$relative"), File(relative)).firstOrNull { it.isFile }

    @Test fun baseUrlPassesTheCustomEndpointValidation() {
        val url = PracticeModel.baseUrl(18081)
        assertEquals("http://127.0.0.1:18081/v1", url)
        assertEquals(url, ProviderConfig.normalizeBaseUrl(url))
        assertEquals("http://127.0.0.1:1/v1", PracticeModel.baseUrl(1))
        assertEquals("http://127.0.0.1:65535/v1", PracticeModel.baseUrl(65535))
    }

    @Test fun baseUrlRejectsBadPorts() {
        for (bad in listOf(0, -1, 65536, Int.MIN_VALUE, Int.MAX_VALUE)) {
            try {
                PracticeModel.baseUrl(bad)
                fail("port $bad should be rejected")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    @Test fun constantsFollowTheRegistry() {
        assertEquals("practice-model", PracticeModel.MODEL_ID)
        assertEquals("custom", PracticeModel.PROVIDER)
        assertEquals("practice", PracticeModel.API_KEY)
        assertEquals(18080, PracticeModel.SMOKE_PORT)
        assertEquals("practice_model.py", PracticeModel.ASSET_SCRIPT)
        assertEquals("practice-start.sh", PracticeModel.ASSET_START)
        assertEquals(PracticeModel.ASSET_SCRIPT, PracticeModel.HOST_SCRIPT_NAME)
        assertEquals("start.sh", PracticeModel.HOST_START_NAME)
        assertEquals("/opt/agiri/practice", PracticeModel.GUEST_DIR)
        assertEquals(PracticeModel.GUEST_DIR + "/" + PracticeModel.HOST_START_NAME, PracticeModel.GUEST_START)
        assertTrue(PracticeModel.GUEST_REQUEST_LOG.startsWith(PracticeModel.GUEST_DIR + "/"))
        // Outside HERMES_HOME on purpose: not in a backup, not in UserState.
        assertFalse(PracticeModel.GUEST_DIR.contains(".hermes"))
        assertEquals(15, PracticeModel.KEYWORDS.size)
        assertEquals(PracticeModel.KEYWORDS.size, PracticeModel.KEYWORDS.toSet().size)
    }

    @Test fun guestCommandShape() {
        val a = PracticeModel.guestCommand(18081)
        assertEquals(listOf("sh", PracticeModel.GUEST_START, "18081"), a)
        // The launcher puts the command after "--": no word may look like an environment assignment.
        assertTrue(a.none { it.contains('=') })
        val b = PracticeModel.guestCommand(40000)
        assertEquals(a.take(2), b.take(2))
        assertEquals("40000", b[2])
    }

    @Test fun installIntoWritesOnlyWhenDifferent() {
        val dir = File(tmp.root, "opt/agiri/practice")
        val script = "print('a')\n".toByteArray()
        val start = "#!/bin/sh\nexit 0\n".toByteArray()

        assertTrue("first call writes", PracticeModel.installInto(dir, script, start))
        assertArrayEquals(script, File(dir, PracticeModel.HOST_SCRIPT_NAME).readBytes())
        assertArrayEquals(start, File(dir, PracticeModel.HOST_START_NAME).readBytes())

        assertFalse("same bytes: nothing written", PracticeModel.installInto(dir, script, start))

        val script2 = "print('b')\n".toByteArray()
        assertTrue("a changed script is written", PracticeModel.installInto(dir, script2, start))
        assertEquals("print('b')\n", File(dir, PracticeModel.HOST_SCRIPT_NAME).readText())
        assertArrayEquals(start, File(dir, PracticeModel.HOST_START_NAME).readBytes())

        val start2 = "#!/bin/sh\nexit 1\n".toByteArray()
        assertTrue("a changed start script is written", PracticeModel.installInto(dir, script2, start2))
        assertFalse(PracticeModel.installInto(dir, script2, start2))

        // Only the two files; no temporary file is left behind, and files the guest made there are not touched.
        File(dir, "requests.jsonl").writeText("{}\n")
        assertFalse(PracticeModel.installInto(dir, script2, start2))
        assertEquals(
            setOf(PracticeModel.HOST_SCRIPT_NAME, PracticeModel.HOST_START_NAME, "requests.jsonl"),
            dir.list()!!.toSet(),
        )
        assertEquals("{}\n", File(dir, "requests.jsonl").readText())
    }

    @Test fun installIntoMakesTheStartScriptRunnableByTheOwner() {
        val dir = File(tmp.root, "p")
        PracticeModel.installInto(dir, "x".toByteArray(), "y".toByteArray())
        val start = File(dir, PracticeModel.HOST_START_NAME)
        val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")
        Assume.assumeFalse("Windows does not keep the execute bit", windows)
        assertTrue(start.canExecute())
        assertTrue(start.canRead())
        assertFalse("the python file is not executable", File(dir, PracticeModel.HOST_SCRIPT_NAME).canExecute())
    }

    @Test fun keywordsMatchThePythonFile() {
        val py = repoFile("rootfs/smoke/mock_llm.py")
        Assume.assumeTrue("mock_llm.py not reachable from the test's working directory", py != null)
        val text = py!!.readText()
        val line = Regex("^KEYWORDS = \\((.*)\\)\\s*$", RegexOption.MULTILINE).find(text)
        assertNotNull("KEYWORDS line in mock_llm.py", line)
        val inPython = Regex("\"([A-Z_]+)\"").findAll(line!!.groupValues[1]).map { it.groupValues[1] }.toList()
        assertEquals(PracticeModel.KEYWORDS.toSet(), inPython.toSet())
        assertEquals(PracticeModel.KEYWORDS.size, inPython.size)
        // Every keyword has a row of the usage table, so token numbers in the app stay predictable.
        for (kw in PracticeModel.KEYWORDS) {
            assertTrue("USAGE row for $kw", Regex("^\\s+\"$kw\":\\s*(\\(|None)", RegexOption.MULTILINE).containsMatchIn(text))
        }
    }

    @Test fun theStartScriptIsPortableShell() {
        val sh = repoFile("runtime/practice-start.sh")
        Assume.assumeTrue("practice-start.sh not reachable from the test's working directory", sh != null)
        val bytes = sh!!.readBytes()
        assertFalse("LF only: mksh does not run a CRLF script", bytes.any { it == '\r'.code.toByte() })
        val text = String(bytes, Charsets.UTF_8)
        assertTrue(text.startsWith("#!/bin/sh\n"))
        // The launcher runs scripts with mksh, CI with dash: no bashisms outside comments.
        val code = text.lineSequence().filterNot { it.trimStart().startsWith("#") }.toList()
        for (bad in listOf("[[", "]]", "<(", "function ", "local ", "source ")) {
            assertFalse("bashism '$bad'", code.any { it.contains(bad) })
        }
        // The contract's exit codes and the final exec.
        assertTrue(text.contains("exit 70") && text.contains("exit 71"))
        assertTrue(text.contains("exec \"\$@\""))
    }
}
