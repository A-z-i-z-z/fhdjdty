package dev.pocketagent.app

import dev.pocketagent.app.runtime.UserState
import dev.pocketagent.app.util.FileUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class FileAndUserStateTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun deletesDirectoriesWithoutOwnerWriteAndNeverFollowsSymlinks() {
        val outside = tmp.newFolder("outside"); File(outside, "keep").writeText("k")
        val root = tmp.newFolder("tree")
        val ro = File(root, "ro").also { it.mkdirs() }
        File(ro, "f").writeText("x")
        Files.createSymbolicLink(File(ro, "escape").toPath(), outside.toPath())
        ro.setWritable(false); ro.setExecutable(false)
        assertNull(FileUtil.deleteRecursivelyNoFollow(root))
        assertFalse(root.exists())
        assertEquals("k", File(outside, "keep").readText())
    }

    @Test fun deleteOrThrowNamesThePath() {
        val dir = tmp.newFolder("t2")
        FileUtil.deleteOrThrow(dir)
        assertFalse(dir.exists())
    }

    @Test fun readLimitedStopsAtLimit() {
        assertEquals(10, FileUtil.readLimited(ByteArray(1000).inputStream(), 10).size)
        assertEquals(5, FileUtil.readLimited(ByteArray(5).inputStream(), 10).size)
    }

    @Test fun userStateSurvivesRootfsReplacement() {
        val home = tmp.newFolder("old-home")
        File(home, "sessions").mkdirs(); File(home, "sessions/s1.json").writeText("chat")
        File(home, "state.db").writeText("db"); File(home, "memories").mkdirs(); File(home, "memories/m").writeText("mem")
        File(home, "hermes-agent").mkdirs(); File(home, "hermes-agent/code.py").writeText("old code")
        File(home, ".env").writeText("SECRET=1"); File(home, "gateway.lock").writeText("pid")
        val parking = File(tmp.root, "park")
        val moved = UserState.park(home, parking)
        assertTrue(moved.containsAll(listOf("sessions", "state.db", "memories")))
        assertFalse("install artifacts are not parked", moved.contains("hermes-agent") || moved.contains(".env") || moved.contains("gateway.lock"))

        val fresh = tmp.newFolder("new-home")
        File(fresh, "hermes-agent").mkdirs(); File(fresh, "hermes-agent/code.py").writeText("new code")
        File(fresh, "memories").mkdirs(); File(fresh, "memories/shipped-default").writeText("d")
        UserState.restore(parking, fresh)
        assertEquals("chat", File(fresh, "sessions/s1.json").readText())
        assertEquals("db", File(fresh, "state.db").readText())
        assertEquals("mem", File(fresh, "memories/m").readText())
        assertFalse("user's version replaces the shipped default", File(fresh, "memories/shipped-default").exists())
        assertEquals("new code", File(fresh, "hermes-agent/code.py").readText())
        assertFalse(parking.exists())
    }
}
