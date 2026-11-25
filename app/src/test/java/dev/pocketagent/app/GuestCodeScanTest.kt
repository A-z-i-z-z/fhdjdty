package dev.pocketagent.app

import dev.pocketagent.app.runtime.GuestCodeEntry
import dev.pocketagent.app.runtime.GuestCodeKind
import dev.pocketagent.app.runtime.GuestCodeScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

/** The report of code Hermes runs from its own folders: listing only, never following a link, never throwing. */
class GuestCodeScanTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun home() = tmp.newFolder("hermes-home")

    private fun write(home: File, rel: String, text: String = "x") {
        val f = File(home, rel); f.parentFile!!.mkdirs(); f.writeText(text)
    }

    @Test fun emptyHomeGivesNothing() {
        val h = home()
        assertTrue(GuestCodeScan.scan(h).isEmpty())
        File(h, "hooks").mkdirs(); File(h, "plugins").mkdirs()
        assertTrue(GuestCodeScan.scan(h).isEmpty())
        assertEquals("none", GuestCodeScan.summary(emptyList()))
        // a home that does not exist at all
        assertTrue(GuestCodeScan.scan(File(h, "missing")).isEmpty())
    }

    @Test fun hookDirectoryIsCompleteWithBothFiles() {
        val h = home()
        write(h, "hooks/demo/HOOK.yaml", "name: demo"); write(h, "hooks/demo/handler.py", "def handle(): pass")
        write(h, "hooks/half/HOOK.yaml")
        val got = GuestCodeScan.scan(h)
        assertEquals(listOf("hooks/demo", "hooks/half"), got.map { it.path })
        val demo = got[0]
        assertEquals(GuestCodeKind.HOOK, demo.kind); assertEquals("demo", demo.name)
        assertTrue(demo.complete); assertEquals(2, demo.files); assertEquals("name: demo".length + "def handle(): pass".length.toLong(), demo.bytes)
        assertFalse(demo.symlink); assertTrue(demo.newestModified > 0L)
        assertFalse(got[1].complete)
    }

    @Test fun pluginWithoutInitIsIncomplete() {
        val h = home()
        write(h, "plugins/p1/plugin.yaml"); write(h, "plugins/p1/__init__.py"); write(h, "plugins/p1/sub/more.py")
        write(h, "plugins/p2/plugin.yaml")
        val got = GuestCodeScan.scan(h).associateBy { it.path }
        assertEquals(GuestCodeKind.PLUGIN, got.getValue("plugins/p1").kind)
        assertTrue(got.getValue("plugins/p1").complete); assertEquals(3, got.getValue("plugins/p1").files)
        assertFalse(got.getValue("plugins/p2").complete); assertEquals(1, got.getValue("plugins/p2").files)
        // a hook needs HOOK.yaml and handler.py: plugin files do not make a hook complete, and the other way round
        write(h, "hooks/x/plugin.yaml"); write(h, "hooks/x/__init__.py")
        assertFalse(GuestCodeScan.scan(h).single { it.path == "hooks/x" }.complete)
    }

    @Test fun looseFilesAreIncompleteEntries() {
        val h = home()
        write(h, "hooks/loose.py", "abc")
        val e = GuestCodeScan.scan(h).single()
        assertEquals("hooks/loose.py", e.path); assertFalse(e.complete); assertEquals(1, e.files); assertEquals(3L, e.bytes)
    }

    @Test fun appWrittenIsExcluded() {
        val h = home()
        write(h, "hooks/ours/HOOK.yaml"); write(h, "hooks/theirs/HOOK.yaml"); write(h, "plugins/ours/plugin.yaml")
        val got = GuestCodeScan.scan(h, appWritten = setOf("hooks/ours"))
        assertEquals(listOf("hooks/theirs", "plugins/ours"), got.map { it.path })
    }

    @Test fun symlinkIsListedNotFollowed() {
        val h = home()
        val outside = tmp.newFolder("outside"); File(outside, "secret.txt").writeText("secret")
        try { Files.createSymbolicLink(File(h, "hooks").also { it.mkdirs() }.toPath().resolve("link"), outside.toPath()) }
        catch (e: Exception) { Assume.assumeNoException("symbolic links need a privilege on this machine", e) }
        val e = GuestCodeScan.scan(h).single()
        assertEquals("hooks/link", e.path); assertTrue(e.symlink); assertEquals(0, e.files); assertFalse(e.complete)
        assertEquals("hooks/link (symlink)", GuestCodeScan.summary(listOf(e)))
        assertEquals("secret", File(outside, "secret.txt").readText())
    }

    @Test fun namesAreSanitisedAndCapped() {
        val h = home()
        write(h, "hooks/Bad;Name \$x/HOOK.yaml")
        val long = "a".repeat(70)
        write(h, "plugins/$long/plugin.yaml")
        val got = GuestCodeScan.scan(h)
        assertEquals("Bad?Name ?x", got.single { it.kind == GuestCodeKind.HOOK }.name)
        assertEquals("hooks/Bad?Name ?x", got.single { it.kind == GuestCodeKind.HOOK }.path)
        assertEquals(60, got.single { it.kind == GuestCodeKind.PLUGIN }.name.length)
        assertEquals("Ab._ -9", GuestCodeScan.cleanName("Ab._ -9"))
        assertEquals("a??b", GuestCodeScan.cleanName("a\néb"))
    }

    @Test fun entryAndFileCapsHold() {
        val h = home()
        File(h, "hooks").mkdirs()
        for (i in 0 until GuestCodeScan.MAX_ENTRIES + 20) File(h, "hooks/h%03d".format(i)).mkdirs()
        assertEquals(GuestCodeScan.MAX_ENTRIES, GuestCodeScan.scan(h).size)
        // many files in one entry: the count stops at the cap and the scan still returns
        val h2 = tmp.newFolder("hermes-home-2")
        val big = File(h2, "plugins/big").also { it.mkdirs() }
        for (i in 0 until GuestCodeScan.MAX_FILES_PER_ENTRY + 50) File(big, "f$i").writeText("")
        val e = GuestCodeScan.scan(h2).single { it.path == "plugins/big" }
        assertEquals(GuestCodeScan.MAX_FILES_PER_ENTRY, e.files)
    }

    @Test fun summaryReadsPlainly() {
        val a = GuestCodeEntry(GuestCodeKind.HOOK, "x", "hooks/x", 2, 10L, 1L, true, false)
        val b = GuestCodeEntry(GuestCodeKind.PLUGIN, "y", "plugins/y", 1, 5L, 1L, false, false)
        assertEquals("hooks/x (2 files), plugins/y (1 file, incomplete)", GuestCodeScan.summary(listOf(a, b)))
        assertNotNull(GuestCodeScan.summary(listOf(a)))
    }

    @Test fun resultIsSortedByPath() {
        val h = home()
        write(h, "plugins/a/plugin.yaml"); write(h, "hooks/z/HOOK.yaml"); write(h, "hooks/b/HOOK.yaml")
        assertEquals(listOf("hooks/b", "hooks/z", "plugins/a"), GuestCodeScan.scan(h).map { it.path })
    }
}
