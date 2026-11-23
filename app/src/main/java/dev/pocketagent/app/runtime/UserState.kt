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
    /**
     * ALLOWLIST of what counts as user state (anything else in HERMES_HOME is either an install artifact that
     * must come from the new build, or volatile/regenerated, and is never carried over):
     *  - sessions, memories, skills, cron, hooks, pairing, pending_messages, kanban, plugins: user-created content
     *  - SOUL.md, auth.json: the user's persona file and Hermes's own credential/auth store
     *  - state.db, kanban.db, response_store.db, runs_idempotency.db (+ -wal/-shm): chat history and run ledgers
     * Not carried: hermes-agent, installs, tools, cache, logs, secrets (regenerated from app storage), .env,
     * config.yaml (regenerated), locks/pid/sockets, runtime/, state/, install_id, log_hash_salt and the
     * `.l2s.*` link-emulation files (they belong to pairs that must stay together).
     */
    private val NAMES = setOf(
        "sessions", "memories", "skills", "cron", "hooks", "pairing", "pending_messages", "kanban", "plugins",
        "SOUL.md", "auth.json",
    )
    private val DB = Regex("^(state|kanban|response_store|runs_idempotency)\\.db(-wal|-shm)?$")

    fun isUserState(name: String): Boolean = name in NAMES || DB.matches(name)

    /**
     * Moves user state out of [hermesHome] into [parking]. If [parking] already holds an entry of that name it is the
     * user's REAL data (an earlier, interrupted update parked it) and the copy in [hermesHome] is a shipped default
     * of a fresh rootfs: the parked copy wins and the home copy is dropped. Returns the names handled.
     */
    fun park(hermesHome: File, parking: File): List<String> {
        val moved = ArrayList<String>()
        val entries = hermesHome.list() ?: return moved
        val todo = entries.filter { isUserState(it) }
        if (todo.isEmpty()) return moved
        parking.mkdirs()
        for (n in todo) {
            val src = File(hermesHome, n); val dst = File(parking, n)
            if (Files.exists(dst.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) FileUtil.deleteOrThrow(src)
            else Files.move(src.toPath(), dst.toPath(), StandardCopyOption.ATOMIC_MOVE)
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
            if (Files.exists(dst.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) FileUtil.deleteOrThrow(dst)
            Files.move(src.toPath(), dst.toPath(), StandardCopyOption.ATOMIC_MOVE)
            restored += n
        }
        FileUtil.deleteRecursivelyNoFollow(parking)
        return restored
    }
}
