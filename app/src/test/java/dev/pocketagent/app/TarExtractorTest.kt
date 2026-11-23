package dev.pocketagent.app

import dev.pocketagent.app.runtime.NioFsOps
import dev.pocketagent.app.runtime.TarExtractor
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files

class TarExtractorTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun archive(build: (TarArchiveOutputStream) -> Unit): ByteArray {
        val bos = ByteArrayOutputStream()
        XZCompressorOutputStream(bos).use { xz ->
            TarArchiveOutputStream(xz).also { it.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX) }.use { t -> build(t) }
        }
        return bos.toByteArray()
    }

    private fun TarArchiveOutputStream.dir(name: String, mode: Int = 0x1ED) {
        val e = TarArchiveEntry("$name/"); e.mode = mode; putArchiveEntry(e); closeArchiveEntry()
    }
    private fun TarArchiveOutputStream.file(name: String, data: ByteArray, mode: Int = 0x1A4, mtime: Long = 1_700_000_000_000L) {
        val e = TarArchiveEntry(name); e.size = data.size.toLong(); e.mode = mode; e.modTime = java.util.Date(mtime)
        putArchiveEntry(e); write(data); closeArchiveEntry()
    }
    private fun TarArchiveOutputStream.symlink(name: String, target: String) {
        val e = TarArchiveEntry(name, TarArchiveEntry.LF_SYMLINK); e.linkName = target; putArchiveEntry(e); closeArchiveEntry()
    }
    private fun TarArchiveOutputStream.hardlink(name: String, target: String) {
        val e = TarArchiveEntry(name, TarArchiveEntry.LF_LINK); e.linkName = target; putArchiveEntry(e); closeArchiveEntry()
    }

    private fun extract(bytes: ByteArray): java.io.File {
        val dest = tmp.newFolder("root")
        TarExtractor(NioFsOps()).extractTarXz(bytes.inputStream(), bytes.size.toLong(), dest)
        return dest
    }

    @Test fun preservesSymlinksHardlinksModesAndMtime() {
        val longName = "usr/share/" + "d".repeat(120) + "/" + "f".repeat(110) + ".txt"
        val bytes = archive { t ->
            t.dir("."); t.dir("./usr"); t.dir("./usr/bin"); t.dir("./etc")
            t.file("./usr/bin/tool", "#!/bin/sh\necho hi\n".toByteArray(), mode = 0x1ED)
            t.file("./etc/conf", "x".toByteArray(), mode = 0x180)
            t.symlink("./bin", "usr/bin")                       // relative dir symlink
            t.symlink("./usr/bin/tool-abs", "/usr/bin/tool")     // absolute symlink must stay absolute
            t.symlink("./dangling", "does/not/exist")
            t.hardlink("./usr/bin/tool-hard", "./usr/bin/tool")
            t.file("./$longName", "long".toByteArray())
        }
        val root = extract(bytes)
        val tool = root.resolve("usr/bin/tool")
        assertTrue(tool.readText().contains("echo hi"))
        assertTrue(tool.canExecute())
        assertEquals(1_700_000_000_000L, tool.lastModified())
        assertEquals("usr/bin", Files.readSymbolicLink(root.resolve("bin").toPath()).toString())
        assertEquals("/usr/bin/tool", Files.readSymbolicLink(root.resolve("usr/bin/tool-abs").toPath()).toString())
        assertEquals("does/not/exist", Files.readSymbolicLink(root.resolve("dangling").toPath()).toString())
        assertEquals(tool.readText(), root.resolve("usr/bin/tool-hard").readText())
        assertTrue(Files.isSameFile(tool.toPath(), root.resolve("usr/bin/tool-hard").toPath()))
        assertEquals("long", root.resolve(longName).readText())
        assertFalse(root.resolve("etc/conf").canExecute())
    }

    @Test fun readOnlyDirectoryDoesNotBlockChildren() {
        val bytes = archive { t ->
            t.dir("./ro", mode = 0x16D) // 0555
            t.file("./ro/child", "c".toByteArray())
        }
        val root = extract(bytes)
        assertEquals("c", root.resolve("ro/child").readText())
        root.resolve("ro").setWritable(true) // let the TemporaryFolder rule clean up
    }

    @Test fun rejectsPathTraversal() {
        val bytes = archive { t -> t.file("../evil", "x".toByteArray()) }
        try { extract(bytes); fail("expected IOException") } catch (e: IOException) { assertTrue(e.message!!.contains("unsafe")) }
        assertFalse(tmp.root.resolve("evil").exists())
    }

    @Test fun reportsProgress() {
        val bytes = archive { t -> for (i in 0 until 50) t.file("./f$i", ByteArray(1000) { it.toByte() }) }
        val dest = tmp.newFolder("p")
        var last = -1L
        TarExtractor(NioFsOps()).extractTarXz(bytes.inputStream(), bytes.size.toLong(), dest) { last = it.compressedRead }
        assertEquals(bytes.size.toLong(), last)
    }
}
