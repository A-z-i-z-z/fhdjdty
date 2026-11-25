package dev.pocketagent.app

import dev.pocketagent.app.data.DraftStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DraftStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val file get() = File(tmp.root, "chat/drafts.json")
    private fun store(debounce: Long = 20L) = DraftStore(file, CoroutineScope(Dispatchers.IO), debounce)

    private fun waitFor(timeoutMs: Long = 5000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("condition not met within ${timeoutMs}ms")
            Thread.sleep(10)
        }
    }

    @Test fun aDraftIsReadBackAndBlankTextRemovesIt() {
        val s = store()
        assertEquals("", s["c1"])
        s["c1"] = "half a thought"
        assertEquals("half a thought", s["c1"])
        assertEquals(mapOf("c1" to "half a thought"), s.drafts.value)
        s["c1"] = "   \n "
        assertEquals("", s["c1"])
        assertTrue(s.drafts.value.isEmpty())
        s["never-existed"] = ""   // no crash, no entry
        assertTrue(s.drafts.value.isEmpty())
    }

    @Test fun draftsSurviveARestart() {
        val a = store()
        a["c1"] = "one\nwith a second line and ünïcode ê"
        a["c2"] = "two "
        a.flush()
        val b = store()
        assertEquals("one\nwith a second line and ünïcode ê", b["c1"])
        assertEquals("trailing space is kept", "two ", b["c2"])
        b["c1"] = ""
        b.flush()
        assertEquals(mapOf("c2" to "two "), store().drafts.value)
    }

    @Test fun writesAreDebouncedAndAtomic() {
        val s = store(debounce = 100L)
        s["c1"] = "a"; s["c1"] = "ab"; s["c1"] = "abc"
        assertFalse("not written while still typing", file.exists())
        waitFor { file.isFile }
        assertEquals("abc", store()["c1"])
        assertTrue(file.parentFile.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test fun pruneForgetsDeletedChats() {
        val s = store()
        s["keep"] = "k"; s["gone"] = "g"
        s.prune(setOf("keep", "other"))
        assertEquals(mapOf("keep" to "k"), s.drafts.value)
        s.flush()
        assertEquals(mapOf("keep" to "k"), store().drafts.value)
    }

    @Test fun suspendedWritesNeverReachTheDisk() {
        val s = store()
        s["c1"] = "before"
        s.flush()
        val before = file.readText()
        s.suspendWrites()
        s["c1"] = "after"
        s["c2"] = "more"
        s.flush()
        Thread.sleep(120)
        assertEquals(before, file.readText())
        assertEquals("still usable in memory", "after", s["c1"])
    }

    @Test fun aCorruptFileIsKeptAsBadAndTheStoreStartsEmpty() {
        file.parentFile.mkdirs()
        file.writeText("not json at all")
        val s = store()
        assertTrue(s.drafts.value.isEmpty())
        assertEquals("not json at all", File(file.parentFile, "drafts.json.bad").readText())
        s["c1"] = "fresh"
        s.flush()
        assertEquals("fresh", store()["c1"])
    }

    @Test fun anAbsurdlyLongDraftIsCut() {
        val s = store()
        s["c1"] = "x".repeat(DraftStore.MAX_CHARS + 500)
        assertEquals(DraftStore.MAX_CHARS, s["c1"].length)
    }
}
