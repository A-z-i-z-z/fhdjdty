package app.yerel

import android.content.ComponentCallbacks2
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.yerel.core.HuggingFace
import app.yerel.ui.AppViewModel
import app.yerel.ui.Screen
import app.yerel.ui.YerelRoot
import app.yerel.ui.YerelTheme
import app.yerel.ui.isDark

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private var incomingLink by mutableStateOf<String?>(null)
    private var requestedScreen by mutableStateOf<Screen?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val settings by vm.settings.collectAsState()
            val dark = isDark(settings)
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            YerelTheme(settings) {
                YerelRoot(
                    vm, incomingLink, onLinkConsumed = { incomingLink = null },
                    requestedScreen = requestedScreen, onScreenConsumed = { requestedScreen = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) vm.onBackground()
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL || level == ComponentCallbacks2.TRIM_MEMORY_COMPLETE) vm.onTrimMemory()
    }

    /** Hugging Face links (shared or opened), .gguf files, and the debug-only CI hooks. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (BuildConfig.DEBUG) {
            // CI hooks: am start --ez test_thinking false --es test_prompt '...' / --es test_screen MODELS
            if (intent.hasExtra("test_thinking")) {
                val on = intent.getBooleanExtra("test_thinking", true)
                vm.updateSettings { it.copy(thinking = on) }
            }
            if (intent.hasExtra("test_tools")) {
                val on = intent.getBooleanExtra("test_tools", true)
                vm.updateSettings { it.copy(agentEnabled = on) }
            }
            if (intent.hasExtra("test_max_tokens")) {
                val n = intent.getIntExtra("test_max_tokens", 1024)
                vm.updateSettings { it.copy(maxTokens = n) }
            }
            intent.getStringExtra("test_screen")?.let { name ->
                Screen.entries.firstOrNull { it.name == name }?.let { requestedScreen = it }
            }
            intent.getStringExtra("test_prompt")?.let { requestedScreen = Screen.CHAT; vm.runTestPrompt(it); return }
        }
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
                val url = text?.let { URL_RE.find(it)?.value }
                when {
                    url != null && HuggingFace.parse(url) != null -> incomingLink = url
                    text != null && HuggingFace.parse(text) != null -> incomingLink = text
                    intent.streamUri() != null -> vm.inspectLocal(intent.streamUri()!!)
                    text != null -> incomingLink = text
                }
            }
            Intent.ACTION_VIEW -> {
                val data = intent.data ?: return
                if (data.scheme == "https" || data.scheme == "http") incomingLink = data.toString() else vm.inspectLocal(data)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.streamUri(): Uri? = getParcelableExtra(Intent.EXTRA_STREAM) as? Uri

    companion object {
        private val URL_RE = Regex("https?://\\S+")
    }
}
