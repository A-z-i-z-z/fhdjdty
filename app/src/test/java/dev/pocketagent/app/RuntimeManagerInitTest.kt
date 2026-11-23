package dev.pocketagent.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import dev.pocketagent.app.runtime.Paths
import dev.pocketagent.app.runtime.RuntimeManager
import dev.pocketagent.app.runtime.RuntimeState
import dev.pocketagent.app.runtime.SecretStore
import dev.pocketagent.app.runtime.Settings
import dev.pocketagent.app.runtime.WakeLocks
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.reflect.Proxy

/**
 * Constructs the real [RuntimeManager] exactly like PocketApp.onCreate does, against a fake Context. Regression test for the
 * build-8 crash: its constructor ran refreshInstalledState() before the `by lazy` rootfsInfo delegate existed, which threw a
 * NullPointerException at app start, but only when a rootfs was already installed (first launch has no stamp file and
 * short-circuits before touching rootfsInfo).
 *
 * The fake context has no AssetManager, so rootfs.info is unreadable and the expected build id falls back to "unknown";
 * the stamp files below use that value.
 */
class RuntimeManagerInitTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun fakeContext(files: File): Context {
        val cache = File(files.parentFile, "cache").also { it.mkdirs() }
        val libs = File(files.parentFile, "lib").also { it.mkdirs() }
        val prefs = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, m, args ->
            when (m.name) { "getString", "getInt", "getLong", "getFloat", "getBoolean" -> args[1]; "contains" -> false; else -> null }
        } as SharedPreferences
        return object : ContextWrapper(null) {
            override fun getFilesDir(): File = files
            override fun getCacheDir(): File = cache
            override fun getApplicationInfo(): ApplicationInfo = ApplicationInfo().also { it.nativeLibraryDir = libs.path }
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
        }
    }

    /** The same sequence of constructors as PocketApp.onCreate. */
    private fun construct(files: File): RuntimeManager {
        val ctx = fakeContext(files)
        val paths = Paths(ctx)
        val settings = Settings(ctx)
        return RuntimeManager(ctx, paths, settings, SecretStore(paths, settings), WakeLocks(ctx))
    }

    private fun filesDir() = tmp.newFolder("app", "files")

    @Test fun coldStartWithInstalledRootfsDoesNotCrash() {
        val files = filesDir()
        File(files, "rootfs/usr/bin").mkdirs()
        File(files, "rootfs.stamp").writeText("unknown\n")
        val rt = construct(files)
        assertEquals(RuntimeState.Stopped("ready"), rt.state.value)
    }

    @Test fun firstLaunchWithoutRootfsIsNotInstalled() {
        val rt = construct(filesDir())
        assertEquals(RuntimeState.NotInstalled, rt.state.value)
    }

    @Test fun staleStampForAnotherBuildIsNotInstalled() {
        val files = filesDir()
        File(files, "rootfs/usr/bin").mkdirs()
        File(files, "rootfs.stamp").writeText("some-older-build\n")
        assertEquals(RuntimeState.NotInstalled, construct(files).state.value)
    }

    @Test fun interruptedExtractionIsNotInstalled() {
        val files = filesDir()
        File(files, "rootfs/usr/bin").mkdirs()
        File(files, "rootfs.new").mkdirs()          // a leftover staging directory
        File(files, "rootfs.stamp").writeText("unknown\n")
        assertEquals(RuntimeState.NotInstalled, construct(files).state.value)
    }
}
