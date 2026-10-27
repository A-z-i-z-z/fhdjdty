package app.yerel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

enum class Screen { CHAT, MODELS, SETTINGS, MEMORY }

/** Top-level navigation: a chat home with full-screen pages for models, settings and memory. */
@Composable
fun YerelRoot(
    vm: AppViewModel,
    incomingLink: String?,
    onLinkConsumed: () -> Unit,
    requestedScreen: Screen? = null,
    onScreenConsumed: () -> Unit = {},
) {
    var screen by rememberSaveable { mutableStateOf(Screen.CHAT) }
    var link by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val toast by vm.toast.collectAsState()
    val pendingImport by vm.pendingImport.collectAsState()

    LaunchedEffect(incomingLink) {
        if (incomingLink != null) {
            link = incomingLink
            screen = Screen.MODELS
            onLinkConsumed()
        }
    }
    LaunchedEffect(requestedScreen) {
        if (requestedScreen != null) {
            screen = requestedScreen
            onScreenConsumed()
        }
    }
    LaunchedEffect(pendingImport) {
        if (pendingImport != null) screen = Screen.MODELS
    }
    LaunchedEffect(toast) {
        toast?.let {
            vm.consumeToast()
            snackbar.showSnackbar(it)
        }
    }
    BackHandler(enabled = screen != Screen.CHAT) {
        screen = Screen.CHAT
    }

    AnimatedContent(screen, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) }, label = "screen") { s ->
        when (s) {
            Screen.CHAT -> ChatScreen(vm, snackbar, onOpen = { screen = it })
            Screen.MODELS -> ModelsScreen(vm, snackbar, initialLink = link, onBack = { link = null; screen = Screen.CHAT })
            Screen.SETTINGS -> SettingsScreen(vm, onBack = { screen = Screen.CHAT }, onMemory = { screen = Screen.MEMORY })
            Screen.MEMORY -> MemoryScreen(vm, onBack = { screen = Screen.CHAT })
        }
    }
}
