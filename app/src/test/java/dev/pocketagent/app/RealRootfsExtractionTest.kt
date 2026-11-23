package dev.pocketagent.app

import dev.pocketagent.app.runtime.NioFsOps
import dev.pocketagent.app.runtime.TarExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * Runs only when POCKETAGENT_ROOTFS_ARCHIVE points at a real rootfs-*.tar.xz (CI does this with the
 * arm64 archive that is about to be embedded in the APK). Extracts it with the app's extractor and
 * with GNU tar and requires identical trees: entry set, symlink targets, sizes, executable bits.
 */
class RealRootfsExtractionTest {
    @get:Rule val tmp = TemporaryFolder()

    private data class Entry(val kind: Char, val target: String?, val size: Long, val exec: Boolean)

    private fun scan(root: File): Map<String, Entry> {
        val out = HashMap<String, Entry>()
        val rp = root.toPath()
        Files.walk(rp).use { s ->
            s.forEach { p: Path ->
                if (p == rp) return@forEach
                val rel = rp.relativize(p).toString()
                val e = if (Files.isSymbolicLink(p)) Entry('l', Files.readSymbolicLink(p).toString(), 0, false)
                else if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) Entry('d', null, 0, false)
                else Entry('f', null, Files.size(p), Files.isExecutable(p))
                out[rel] = e
            }
        }
        return out
    }

    @Test fun matchesGnuTar() {
        val archive = System.getenv("POCKETAGENT_ROOTFS_ARCHIVE")
        assumeTrue("POCKETAGENT_ROOTFS_ARCHIVE not set", !archive.isNullOrEmpty() && File(archive).isFile)
        val ours = tmp.newFolder("ours")
        val ref = tmp.newFolder("ref")
        val f = File(archive!!)
        val t0 = System.currentTimeMillis()
        val stats = f.inputStream().use { TarExtractor(NioFsOps()).extractTarXz(it, f.length(), ours) }
        println("extractor: $stats in ${(System.currentTimeMillis() - t0) / 1000}s")
        val p = ProcessBuilder("tar", "-xJf", f.path, "-C", ref.path, "--no-same-owner").redirectErrorStream(true).start()
        val log = p.inputStream.bufferedReader().readText()
        assertEquals("tar failed: $log", 0, p.waitFor())

        val a = scan(ours); val b = scan(ref)
        assertTrue("no entries?", a.size > 1000)
        assertEquals("entry sets differ", b.keys.sorted(), a.keys.sorted())
        val bad = a.filter { (k, v) -> v != b[k] }.keys.take(10)
        assertTrue("entries differ: $bad", bad.isEmpty())
        assertTrue("expected symlinks in a rootfs", a.values.count { it.kind == 'l' } > 50)
        // make the temp folder deletable (read-only dirs)
        ours.walkBottomUp().forEach { it.setWritable(true) }; ref.walkBottomUp().forEach { it.setWritable(true) }
    }
}
