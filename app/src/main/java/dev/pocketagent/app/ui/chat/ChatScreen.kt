package dev.pocketagent.app.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.data.Conversation
import dev.pocketagent.app.ui.LocalNavigator
import dev.pocketagent.app.ui.Route
import dev.pocketagent.app.ui.components.PocketTopBar
import dev.pocketagent.app.ui.theme.Pocket
import kotlinx.coroutines.delay

/**
 * One conversation: message list (bottom-anchored, sticks to the newest message only while you are at the
 * bottom), input bar, menus. Resolves the conversation and hands over to [ChatBody].
 */
@Composable
fun ChatScreen(convId: String) {
    val ctx = LocalContext.current
    val nav = LocalNavigator.current
    val repo = remember { PocketApp.from(ctx).chat }
    val convs by repo.conversations.collectAsState()
    val loaded by repo.loaded.collectAsState()
    val conv = convs.firstOrNull { it.id == convId }
    val convNow by rememberUpdatedState(conv)
    var everSeen by remember(convId) { mutableStateOf(false) }

    // A deleted conversation closes the screen. A brand new one may need a moment to show up in the list.
    LaunchedEffect(convId, conv != null, loaded) {
        if (conv != null) everSeen = true
        else if (loaded) {
            delay(if (everSeen) 0L else 1500L)
            if (convNow == null && !nav.back()) nav.tab(Route.Chats)
        }
    }

    if (conv == null) {
        Column(Modifier.fillMaxSize()) {
            PocketTopBar(title = "", onBack = { if (!nav.back()) nav.tab(Route.Chats) })
            if (loaded) {
                Text(
                    stringResource(R.string.ui_chat_not_found), color = Pocket.colors.textMuted, fontSize = 15.sp,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
    } else {
        ChatBody(conv)
    }
}

/**
 * The layout of one open chat: top area, message list, input area, and the dialogs. The state lives in [ChatScreenState];
 * this function only watches the shape of the message list and the find state, never the messages themselves.
 */
@Composable
private fun ChatBody(conv: Conversation) {
    val convId = conv.id
    val ctx = LocalContext.current
    val app = remember { PocketApp.from(ctx) }
    val repo = app.chat
    val state = rememberChatScreenState(conv)

    // The chat on screen counts as read and its notification goes away; leaving or pausing hands the "active" mark back.
    LifecycleResumeEffect(convId) {
        repo.setActiveConversation(convId)
        app.presence.notifier.cancelFor(convId)
        onPauseOrDispose {
            if (repo.activeConversation == convId) repo.setActiveConversation(null)
        }
    }

    BackHandler(enabled = state.searching) { state.closeSearch() }

    val shape by state.shape
    val matches by state.matches
    val search = searchViewOf(state.searching, state.searchQuery, matches, state.searchPos, state.flashId)

    Column(Modifier.fillMaxSize().testTag("chat.screen")) {
        ChatTopArea(state, conv, shape, search)
        ChatMessageList(state, conv, search, Modifier.weight(1f).fillMaxWidth())
        ChatInputArea(state, conv)
    }
    ChatDialogs(state, conv, shape)
}
