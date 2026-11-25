package dev.pocketagent.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role as SemRole
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.data.Conversation
import dev.pocketagent.app.data.Message
import dev.pocketagent.app.data.Role
import dev.pocketagent.app.ui.LocalNavigator
import dev.pocketagent.app.ui.Route
import dev.pocketagent.app.ui.components.EmptyState
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconButton
import dev.pocketagent.app.ui.components.PocketTopBar
import dev.pocketagent.app.ui.theme.Pocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "Saved messages": every starred message of every chat, newest first. Tap opens the chat at that message. */
@Composable
fun StarredScreen() {
    val ctx = LocalContext.current
    val nav = LocalNavigator.current
    val app = remember { PocketApp.from(ctx) }
    val repo = app.chat
    val scope = rememberCoroutineScope()
    val bots by repo.bots.collectAsState()
    val botsById = remember(bots) { bots.associateBy { it.id } }
    val untitled = stringResource(R.string.ui_chats_untitled)
    val you = stringResource(R.string.ui_starred_you)
    val deletedBot = stringResource(R.string.ui_chat_deleted_bot)

    // Blocking disk reads: off the main thread. Unstarring removes the row at once; the repository is updated in the background.
    val loaded by produceState<List<Pair<Conversation, Message>>?>(null) {
        value = withContext(Dispatchers.IO) { repo.starredMessages() }
    }
    var removed by remember { mutableStateOf(emptySet<String>()) }
    val shown = loaded?.filter { (_, m) -> m.id !in removed }

    Column(Modifier.fillMaxSize()) {
        PocketTopBar(title = stringResource(R.string.ui_starred_title), onBack = { if (!nav.back()) nav.tab(Route.Chats) })
        when {
            shown == null -> Box(Modifier.weight(1f).fillMaxWidth())
            shown.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(stringResource(R.string.ui_starred_empty_title), stringResource(R.string.ui_starred_empty_body))
            }
            else -> LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(shown, key = { (cv, m) -> cv.id + ":" + m.id }) { (cv, m) ->
                    val speaker = if (m.role == Role.USER) you else botsById[m.botId]?.name ?: deletedBot
                    StarredRow(
                        msg = m, speaker = speaker,
                        title = titleOf(cv, botsById, untitled),
                        members = cv.botIds.map { botsById[it] },
                        onOpen = {
                            ChatScrollTarget.set(cv.id, m.id)
                            nav.go(Route.Chat(cv.id))
                        },
                        onUnstar = {
                            removed = removed + m.id
                            // setStarred loads the chat's file when it is not in memory yet: never on the main thread, and
                            // not tied to this screen's lifetime (leaving right away must not drop the change).
                            scope.launch(Dispatchers.IO + NonCancellable) { repo.setStarred(cv.id, m.id, false) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StarredRow(
    msg: Message,
    speaker: String,
    title: String,
    members: List<dev.pocketagent.app.data.Bot?>,
    onOpen: () -> Unit,
    onUnstar: () -> Unit,
) {
    val c = Pocket.colors
    val preview = remember(msg.text) { markdownToPlain(msg.text, 240) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp)
            .clickable(role = SemRole.Button, onClick = onOpen)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarStack(members, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(relativeTime(msg.createdAt), color = c.textMuted, fontSize = 12.sp)
            }
            Text(speaker, color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                preview.ifBlank { stringResource(R.string.ui_chat_empty_reply) },
                color = c.textMuted, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
        }
        PixelIconButton(
            PixelIcon.STAR, onClick = onUnstar, contentDescription = stringResource(R.string.ui_act_unstar), tint = c.accent,
        )
    }
}
