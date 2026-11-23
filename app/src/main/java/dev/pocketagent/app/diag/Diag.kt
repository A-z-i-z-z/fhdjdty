package dev.pocketagent.app.diag

import dev.pocketagent.app.util.AppLog

/** Process-wide holder of the [Redactor] so that every log path uses the same registered secrets. */
object Diag {
    val redactor = Redactor()

    fun install() {
        AppLog.redactor = { redactor.redact(it) }
    }
}
