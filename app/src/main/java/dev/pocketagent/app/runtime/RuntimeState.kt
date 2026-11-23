package dev.pocketagent.app.runtime

sealed class RuntimeState {
    object NotInstalled : RuntimeState()
    data class Extracting(val fraction: Float, val detail: String) : RuntimeState()
    data class Starting(val step: String) : RuntimeState()
    object Running : RuntimeState()
    data class Stopped(val reason: String) : RuntimeState()
    data class Error(val reason: String) : RuntimeState()

    /** Short machine-readable label (used in the state history and the diagnostics dump). */
    fun label(): String = when (this) {
        NotInstalled -> "NotInstalled"
        is Extracting -> "Extracting(%.0f%%)".format(fraction * 100)
        is Starting -> "Starting($step)"
        Running -> "Running"
        is Stopped -> "Stopped($reason)"
        is Error -> "Error($reason)"
    }

    val isBusyOrRunning: Boolean get() = this is Extracting || this is Starting || this == Running
}

data class StateEntry(val timeMillis: Long, val label: String)

/** One child-process termination, kept for diagnostics. */
data class ExitRecord(
    val timeMillis: Long,
    val rawExitValue: Int,
    val uptimeMillis: Long,
    val requestedByUser: Boolean,
) {
    /** Java reports death-by-signal as 128+signal. */
    val signal: Int? get() = if (rawExitValue in 129..192) rawExitValue - 128 else null

    fun describe(): String {
        val sig = signal
        val base = "exit value $rawExitValue" + (sig?.let { " (signal $it ${signalName(it)})" } ?: "")
        return base + ", uptime ${uptimeMillis / 1000}s" + if (requestedByUser) ", stop requested by user" else ""
    }

    companion object {
        fun signalName(s: Int) = when (s) {
            1 -> "SIGHUP"; 2 -> "SIGINT"; 4 -> "SIGILL"; 6 -> "SIGABRT"; 7 -> "SIGBUS"; 9 -> "SIGKILL"
            11 -> "SIGSEGV"; 13 -> "SIGPIPE"; 15 -> "SIGTERM"
            31 -> "SIGSYS (seccomp killed a syscall)"
            else -> "?"
        }
        fun hint(raw: Int): String? = when (raw) {
            137 -> "SIGKILL: Android phantom-process killer / memory limiter, or force-stop. Check the historical process exit reasons section."
            159 -> "SIGSYS: seccomp killed a syscall. Enable 'Disable proot seccomp filter' in Debug and restart."
            139 -> "SIGSEGV inside proot or the guest."
            127 -> "command not found (launcher or hermes missing)."
            126 -> "not executable (proot binary or script not executable on this device)."
            else -> null
        }
    }
}
