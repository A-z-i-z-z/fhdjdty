package dev.pocketagent.app.diag

import dev.pocketagent.app.util.FileUtil

/** Redacts a dump section FIRST and only then caps it, so a secret straddling the cut can never survive as a fragment. */
object SectionFormat {
    fun redactThenCap(redactor: Redactor, text: String, cap: Int, keepHead: Boolean): String {
        val clean = redactor.redact(text)
        return if (keepHead) FileUtil.capHead(clean, cap) else FileUtil.capTail(clean, cap)
    }
}
