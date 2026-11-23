package dev.pocketagent.app.runtime

import dev.pocketagent.app.util.FileUtil
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * HERMES_HOME lives inside the rootfs (guest /root/.hermes), so replacing the rootfs after an APK
 * update would wipe the user's sessions, memories, skills and databases. Before the old rootfs is
 * deleted, everything that is user state is renamed into a parking directory (same file system,
 * so atomic and instant); after the new rootfs is in place it is moved back. The parking
 * directory is restored on the next start if the app died in between.
 */
object UserState {
    /** Install artifacts and volatile runtime files that must come from the new rootfs / be regenerated. */
    private val SKIP_EXACT = setOf(
        "hermes-agent", "installs", "tools", "cache", "logs", "secrets", "backups", ".env", "config.yaml",
        "gateway.pid", "gateway.lock", "gateway.sock", "gateway_state.json", ".clean_shutdown",
        "runtime", "state", "log_hash_salt", "source-checks", "plugin-update-checks",
    )

    fun isUserState(name: String): Boolean = name !in SKIP_EXACT && !name.startsWith(".l2s.") && !name.endsWith(".lock")

    /** Moves user state out of [hermesHome] into [parking]. Returns the names moved. */
    fun park(hermesHome: File, parking: File): List<String> {
        val moved = ArrayList<String>()
        val entries = hermesHome.list() ?: return moved
        val todo = entries.filter { isUserState(it) }
        if (todo.isEmpty()) return moved
        parking.mkdirs()
        for (n in todo) {
            val src = File(hermesHome, n); val dst = File(parking, n)
            if (java.nio.file.Files.exists(dst.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) FileUtil.deleteOrThrow(dst)
            Files.move(src.toPath(), dst.toPath(), StandardCopyOption.ATOMIC_MOVE)
            moved += n
        }
        return moved
    }

    /** Moves parked state back into [hermesHome] (replacing shipped defaults), then removes [parking]. */
    fun restore(parking: File, hermesHome: File): List<String> {
        val restored = ArrayList<String>()
        val entries = parking.list() ?: return restored
        hermesHome.mkdirs()
        for (n in entries) {
            val src = File(parking, n); val dst = File(hermesHome, n)
            if (java.nio.file.Files.exists(dst.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) FileUtil.deleteOrThrow(dst)
            Files.move(src.toPath(), dst.toPath(), StandardCopyOption.ATOMIC_MOVE)
            restored += n
        }
        FileUtil.deleteRecursivelyNoFollow(parking)
        return restored
    }
}
