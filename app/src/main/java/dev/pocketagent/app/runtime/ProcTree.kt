package dev.pocketagent.app.runtime

import java.io.File

/**
 * Process bookkeeping through /proc. Needed because proot's --kill-on-exit only kills the guest when the
 * guest's root process exits; it does NOT take the guest down when proot itself is killed (verified: after
 * SIGKILL of proot the traced child keeps running, reparented to init), and proot ignores SIGTERM.
 * Guest processes are ordinary host processes of our uid, so we can find and signal them ourselves.
 */
object ProcTree {
    /** Marker present in the environment of every process started through the launcher (see pocketagent-proot.sh). */
    const val GUEST_MARKER = "PA_IN_PROOT=1"

    private fun pids(): List<Int> = File("/proc").list()?.mapNotNull { it.toIntOrNull() } ?: emptyList()

    /** (pid -> ppid) of every readable process. */
    fun parentMap(): Map<Int, Int> {
        val m = HashMap<Int, Int>()
        for (pid in pids()) {
            try {
                val stat = File("/proc/$pid/stat").readText()
                // "pid (comm) S ppid ..." where comm may contain spaces and parentheses
                val rest = stat.substring(stat.lastIndexOf(')') + 2).split(' ')
                m[pid] = rest[1].toInt()
            } catch (_: Exception) { }
        }
        return m
    }

    fun descendants(root: Int): List<Int> {
        val parents = parentMap()
        val out = ArrayList<Int>()
        var frontier = listOf(root)
        while (frontier.isNotEmpty()) {
            val next = parents.filter { it.value in frontier }.keys.filter { it !in out && it != root }
            out += next
            frontier = next
        }
        return out
    }

    /** Processes (of our uid, readable) whose environment carries the guest marker: guest processes, also orphaned ones. */
    fun guestProcesses(): List<Int> = pids().filter { pid ->
        try { File("/proc/$pid/environ").readBytes().toString(Charsets.ISO_8859_1).split('\u0000').contains(GUEST_MARKER) }
        catch (_: Exception) { false }
    }

    /** proot processes started from [prootPath] (own uid). */
    fun prootProcesses(prootPath: String): List<Int> = pids().filter { pid ->
        try { File("/proc/$pid/cmdline").readBytes().toString(Charsets.ISO_8859_1).split('\u0000').firstOrNull() == prootPath }
        catch (_: Exception) { false }
    }

    fun signal(pid: Int, sig: Int) {
        try { android.os.Process.sendSignal(pid, sig) } catch (_: Exception) { }
    }

    const val SIGTERM = 15
    const val SIGKILL = 9
}
