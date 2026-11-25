package dev.pocketagent.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.pocketagent.app.ui.AppRoot

class MainActivity : ComponentActivity() {
    // The screens get the same instance through viewModel() (the activity is their ViewModelStoreOwner).
    private val vm: MainViewModel by viewModels()

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

    override fun onResume() {
        super.onResume()
        vm.runtime.refreshInstalledState()
        vm.refreshBattery()
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        // Never replay a consumed intent from Recents (applies to every kind below).
        if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return

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
}
