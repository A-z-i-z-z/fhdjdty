package dev.pocketagent.app.runtime

import android.system.Os
import java.io.File

/** Uses android.system.Os so that sticky bits and exact modes are honoured. */
class AndroidFsOps : FsOps {
    override fun symlink(target: String, link: File) = Os.symlink(target, link.path)
    override fun hardLink(existing: File, link: File) = Os.link(existing.path, link.path)
    override fun chmod(file: File, mode: Int) {
        try { Os.chmod(file.path, mode) } catch (_: Exception) { }
    }
}
