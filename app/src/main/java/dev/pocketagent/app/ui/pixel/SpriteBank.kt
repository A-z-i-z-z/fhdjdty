package dev.pocketagent.app.ui.pixel

import android.content.Context
import android.util.Log
import dev.pocketagent.app.ui.pixel.stage.SpriteSource
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Registry of every sprite in the .pxl files under assets/pixel/.
 *
 * Initialisation: [init] parses all files once (safe to call repeatedly, from any thread). The pixel
 * composables call it themselves with their Context, so UI code never has to. [preload] starts the same work
 * on a background thread without waiting: the application calls it at start so that the first screen finds
 * the bank ready. [init], [get], [find] and [all] wait for a running load (they share one lock), so no caller
 * needs to know whether the load happened. [get]/[find] outside composition work after [init] or [preload]
 * has run anywhere in the process; as a last resort they try to reach the Application context through the
 * platform, and fail with a clear IllegalStateException if that is impossible.
 *
 * A file with a syntax error is skipped as a whole, logged with `Log.e` (tag PocketAgent/Pixel) and listed
 * in [loadErrors]; the other files still load. Asking for a sprite of the broken file then throws.
 */
object SpriteBank : SpriteSource {
    private const val TAG = "PocketAgent/Pixel"
    private val lock = Any()
    private val preloadStarted = AtomicBoolean(false)

    @Volatile private var sprites: Map<String, Sprite>? = null

    @Volatile private var errors: List<String> = emptyList()

    /** Problems found while loading (file:line: message). Empty when everything is fine. */
    val loadErrors: List<String> get() = errors

    /** True once every file was parsed (with or without errors). */
    val isReady: Boolean get() = sprites != null

    fun init(context: Context) {
        if (sprites != null) return
        synchronized(lock) {
            if (sprites != null) return
            load(context.applicationContext ?: context)
        }
    }

    /**
     * Starts loading on a daemon thread called "pixel-preload" and returns at once. Safe to call again (the
     * second call does nothing) and never throws: a failure is logged and [init] will try again when asked.
     */
    fun preload(context: Context) {
        try {
            if (sprites != null) return
            if (!preloadStarted.compareAndSet(false, true)) return
            val app = context.applicationContext ?: context
            val thread = Thread({
                try {
                    init(app)
                } catch (e: Throwable) {
                    Log.e(TAG, "preload failed", e)
                }
            }, "pixel-preload")
            thread.isDaemon = true
            thread.start()
        } catch (e: Throwable) {
            preloadStarted.set(false)
            Log.e(TAG, "cannot start preload", e)
        }
    }

    override fun get(name: String): Sprite = find(name) ?: throw IllegalArgumentException(
        "Unknown sprite '$name'" + if (errors.isEmpty()) "" else " (load errors: ${errors.joinToString("; ")})",
    )

    fun find(name: String): Sprite? = all()[name]

    /** Every loaded sprite by name. */
    fun all(): Map<String, Sprite> {
        sprites?.let { return it }
        val app = currentApplication()
            ?: throw IllegalStateException("SpriteBank.init(context) has not been called yet")
        init(app)
        return sprites ?: emptyMap()
    }

    private fun load(context: Context) {
        val assets = context.assets
        val names = (assets.list("pixel") ?: emptyArray()).filter { it.endsWith(".pxl") }.sorted()
        val files = names.map { file ->
            file to { assets.open("pixel/$file").bufferedReader(Charsets.UTF_8).use { it.readText() } }
        }
        val result = SpriteLoader.load(files) { file, e ->
            if (e is PxlException) Log.e(TAG, e.message ?: "$file: unreadable") else Log.e(TAG, "cannot read $file", e)
        }
        errors = result.errors
        sprites = result.sprites
    }

    private fun currentApplication(): Context? = try {
        Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as? Context
    } catch (e: Throwable) {
        null
    }
}
