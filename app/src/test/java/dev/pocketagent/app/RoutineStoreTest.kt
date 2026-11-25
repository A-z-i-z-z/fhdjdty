package dev.pocketagent.app

import dev.pocketagent.app.routines.Routine
import dev.pocketagent.app.routines.RoutineJson
import dev.pocketagent.app.routines.RoutineKind
import dev.pocketagent.app.routines.RoutineStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RoutineStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private var now = 1_000_000L

    private fun file() = File(File(tmp.root, "chat"), "routines.json")

    private fun open(f: File = file()) = RoutineStore(f, CoroutineScope(Dispatchers.IO), clock = { now }, debounceMs = 20L)

    private fun routine(id: String = "a", createdAt: Long = 10L) =
        Routine(id = id, name = "Morning", prompt = "Say hi", createdAt = createdAt)

    private fun waitFor(timeoutMs: Long = 5000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("condition not met within ${timeoutMs}ms")
            Thread.sleep(10)
        }
    }

    @Test fun startsEmptyWithoutAFile() {
        val s = open()
        assertTrue(s.routines.value.isEmpty())
        assertNull(s.get("nope"))
    }

    @Test fun roundTripKeepsEveryField() {
        val s = open()
        val r = Routine(
            id = "x1", name = "Weekly", prompt = "Review my week", botId = "coder", kind = RoutineKind.DAILY, hour = 18, minute = 45,
            days = 0b1000000, intervalMinutes = 120, onceAt = 5_000L, enabled = false, convId = "c9", model = "google/gemini-4-hydrogen",
            effort = "low", lastRunAt = 777L, lastStatus = Routine.STATUS_OK, createdAt = 42L, armedAt = 99L,
        )
        s.save(r)
        s.flush()
        val back = open().get("x1")
        assertEquals(r, back)
    }

    @Test fun newRoutineIsArmedAtSaveTime() {
        val s = open()
        now = 5_000L
        s.save(routine())
        assertEquals(5_000L, s.get("a")!!.armedAt)
    }

    @Test fun scheduleChangeAndSwitchingOnRearm() {
        val s = open()
        now = 1_000L
        s.save(routine())
        now = 2_000L
        s.save(s.get("a")!!.copy(hour = 9))
        assertEquals(2_000L, s.get("a")!!.armedAt)
        now = 3_000L
        s.save(s.get("a")!!.copy(enabled = false))
        assertEquals(2_000L, s.get("a")!!.armedAt)
        now = 4_000L
        s.save(s.get("a")!!.copy(enabled = true))
        assertEquals(4_000L, s.get("a")!!.armedAt)
        now = 5_000L
        s.save(s.get("a")!!.copy(kind = RoutineKind.INTERVAL))
        assertEquals(5_000L, s.get("a")!!.armedAt)
    }

    @Test fun bookkeepingChangesDoNotRearm() {
        val s = open()
        now = 1_000L
        s.save(routine())
        now = 9_000L
        s.save(s.get("a")!!.copy(lastRunAt = 8_000L, lastStatus = Routine.STATUS_OK, convId = "chat1", name = "Renamed", prompt = "New"))
        val r = s.get("a")!!
        assertEquals(1_000L, r.armedAt)
        assertEquals(8_000L, r.lastRunAt)
        assertEquals("chat1", r.convId)
    }

    @Test fun anExplicitArmedAtIsKept() {
        val s = open()
        now = 1_000L
        s.save(routine())
        now = 2_000L
        s.save(s.get("a")!!.copy(hour = 7, armedAt = 1_500L))
        assertEquals(1_500L, s.get("a")!!.armedAt)
        s.save(routine("b").copy(armedAt = 700L))
        assertEquals(700L, s.get("b")!!.armedAt)
    }

    @Test fun valuesAreNormalised() {
        val s = open()
        s.save(routine().copy(name = "  Spaced  ", prompt = "\nhello \n", hour = 99, minute = -4, days = 0xFFFF, intervalMinutes = 1, botId = "", model = " m ", effort = " low "))
        val r = s.get("a")!!
        assertEquals("Spaced", r.name)
        assertEquals("hello", r.prompt)
        assertEquals(23, r.hour)
        assertEquals(0, r.minute)
        assertEquals(0x7F, r.days)
        assertEquals(15, r.intervalMinutes)
        assertEquals("hermes", r.botId)
        assertEquals("m", r.model)
        assertEquals("low", r.effort)
    }

    @Test fun savingTheSameIdReplacesAndDeleteRemoves() {
        val s = open()
        s.save(routine("a"))
        s.save(routine("b", createdAt = 20L))
        s.save(s.get("a")!!.copy(name = "Changed"))
        assertEquals(2, s.routines.value.size)
        assertEquals("Changed", s.get("a")!!.name)
        s.delete("a")
        assertNull(s.get("a"))
        assertEquals(listOf("b"), s.routines.value.map { it.id })
        s.delete("does-not-exist")
        assertEquals(1, s.routines.value.size)
    }

    @Test fun newIdsAreDistinct() {
        val s = open()
        val ids = (1..50).map { s.newId() }.toSet()
        assertEquals(50, ids.size)
        assertTrue(ids.all { it.length == 16 })
    }

    @Test fun writesHappenInTheBackgroundAfterTheDebounce() {
        val s = open()
        s.save(routine())
        waitFor { file().isFile }
        assertEquals("Morning", open().get("a")!!.name)
    }

    @Test fun suspendedStoreWritesNothing() {
        val s = open()
        s.save(routine())
        s.flush()
        val before = file().readText()
        s.suspendWrites()
        s.save(routine("b", createdAt = 20L))
        s.flush()
        Thread.sleep(150)
        assertEquals(before, file().readText())
        assertNotNull(s.get("b"))
    }

    @Test fun corruptFileIsKeptAsBadAndTheStoreStartsEmpty() {
        val f = file()
        f.parentFile.mkdirs()
        f.writeText("{ this is not json")
        val s = open(f)
        assertTrue(s.routines.value.isEmpty())
        assertTrue(File(f.parentFile, "routines.json.bad").isFile)
        s.save(routine())
        s.flush()
        assertEquals(1, open(f).routines.value.size)
    }

    @Test fun decodeIsTolerant() {
        val text = """{"version":1,"routines":[
            {"id":"ok1","name":"A","prompt":"p","kind":"WEIRD","hour":30,"minute":70,"days":255,"intervalMinutes":3},
            {"name":"no id"},
            {"id":"","name":"empty id"},
            {"id":"ok2"},
            {"id":"ok1","name":"duplicate"}
        ]}"""
        val list = RoutineJson.decode(text)
        assertEquals(listOf("ok1", "ok2"), list.map { it.id })
        val a = list[0]
        assertEquals(RoutineKind.DAILY, a.kind)
        assertEquals(23, a.hour)
        assertEquals(59, a.minute)
        assertEquals(0x7F, a.days)
        assertEquals(15, a.intervalMinutes)
        val b = list[1]
        assertEquals("", b.name)
        assertEquals("hermes", b.botId)
        assertEquals(8, b.hour)
        assertEquals(0x7F, b.days)
        assertTrue(b.enabled)
        assertFalse(b.lastStatus.isNotEmpty())
    }
}
