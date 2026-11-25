package dev.pocketagent.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.pocketagent.app.ui.AppRoot
import dev.pocketagent.app.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    // The screens get the same instance through viewModel() (the activity is their ViewModelStoreOwner).
    private val vm: MainViewModel by viewModels()

    private companion object {
        // Runs the debug link of a build with test hooks (it may write many files when it seeds test data). It belongs
        // to the process, not to the activity: a link that arrives on a cold start must survive a recreation of the
        // activity (rotation, a second start) before the chats have loaded, because the intent is consumed at once.
        val debugScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A restored activity (process death, rotation) gets its original intent back: it was consumed already.
        if (savedInstanceState == null) handleIncomingIntent(intent)
        setContent { AppRoot() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // The state kept for every screen on the stack and every tab root goes into this bundle: Android fails an app
        // whose saved state passes about one megabyte, so the robot reads the size from the log.
        if (BuildConfig.TEST_HOOKS) logSavedStateSize(outState)
    }

    override fun onResume() {
        super.onResume()
        vm.runtime.refreshInstalledState()
        vm.refreshBattery()
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        // Never replay a consumed intent from Recents (applies to every kind below).
        if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return

        // The debug link of a build with test hooks comes first (a release build never gets a request from it).
        // It is inert unless it carries the token from files/debug-link.token (see DebugLinks).
        val debugRequest = DebugLinks.parse(intent, java.io.File(filesDir, DebugLinks.TOKEN_FILE))
        if (debugRequest != null) {
            runDebugLink(debugRequest)
            intent.action = Intent.ACTION_MAIN // consumed: Recents / recreation must not replay it
            return
        }

        // Notification, launcher shortcut, Quick Settings tile: a navigation request for AppRoot.
        val link = AppLinks.parse(intent)
        if (link != null) {
            PocketApp.from(this).pendingLink.value = link
            intent.action = Intent.ACTION_MAIN // consumed: Recents / recreation must not replay it
            return
        }

        // "Ask Agiri" in the text-selection menu: the selected text goes through the same flow as a share.
        val selected = AppLinks.processText(intent.action, intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT), intent.flags)
        if (selected != null) {
            PocketApp.from(this).incomingShare.value = IncomingShare(text = selected, uri = null, mime = "text/plain")
            intent.action = Intent.ACTION_MAIN
            return
        }

        if (intent.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotEmpty() }
        val streamUri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        val mime = intent.type
        if (text != null || streamUri != null) {
            PocketApp.from(this).incomingShare.value = IncomingShare(text = text, uri = streamUri, mime = mime)
            // Clear action so Android Recents / Activity recreation never replays a consumed share.
            intent.action = Intent.ACTION_MAIN
        }
    }

    /** Applies a debug link once the chats are loaded (seeding and opening a chat need them), then opens its route. */
    private fun runDebugLink(request: DebugRequest) {
        val app = PocketApp.from(this)
        debugScope.launch {
            try {
                app.chat.loaded.first { it }
                val route = DebugLinks.apply(app, request)
                if (route != null) app.pendingLink.value = AppLink.DebugOpen(route)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Debug", "debug link failed", e)
            }
        }
    }

    private fun logSavedStateSize(state: Bundle) {
        try {
            val parcel = Parcel.obtain()
            try {
                parcel.writeBundle(state)
                AppLog.i("Nav", "saved state ${parcel.dataSize()} bytes")
            } finally {
                parcel.recycle()
            }
        } catch (e: Exception) {
            AppLog.w("Nav", "could not measure the saved state", e)
        }
    }
}
