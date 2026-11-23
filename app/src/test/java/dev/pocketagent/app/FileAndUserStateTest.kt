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

    @Test fun interruptedUpdateDoesNotLetShippedDefaultsOverwriteParkedData() {
        // 1st attempt: user data parked, rootfs replaced, app dies before the stamp is written.
        val home = tmp.newFolder("h1"); val parking = File(tmp.root, "park2")
        File(home, "memories").mkdirs(); File(home, "memories/real").writeText("USER DATA")
        File(home, "state.db").writeText("real-db")
        UserState.park(home, parking)
        // fresh rootfs with shipped defaults, parked data NOT yet restored; next start re-parks from it
        val fresh = tmp.newFolder("h2")
        File(fresh, "memories").mkdirs(); File(fresh, "memories/shipped").writeText("default")
        File(fresh, "state.db").writeText("empty-default-db"); File(fresh, "hermes-agent").mkdirs()
        UserState.park(fresh, parking)
        assertEquals("USER DATA", File(parking, "memories/real").readText())
        assertFalse(File(parking, "memories/shipped").exists())
        assertEquals("real-db", File(parking, "state.db").readText())
        val third = tmp.newFolder("h3")
        UserState.restore(parking, third)
        assertEquals("USER DATA", File(third, "memories/real").readText())
        assertEquals("real-db", File(third, "state.db").readText())
    }

    @Test fun allowlistNamesOnlyUserState() {
        for (n in listOf("sessions", "memories", "skills", "state.db", "state.db-wal", "kanban.db-shm", "auth.json", "SOUL.md", "plugins"))
            assertTrue(n, UserState.isUserState(n))
        for (n in listOf("hermes-agent", "installs", "tools", "cache", "logs", "secrets", ".env", "config.yaml", "gateway.lock", "state", "runtime",
            "log_hash_salt", ".l2s..log_hash_salt.1", "unknown-new-thing", "state.db.quarantine.lock", ".skills_prompt_snapshot.json"))
            assertFalse(n, UserState.isUserState(n))
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
