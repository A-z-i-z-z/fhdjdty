package dev.pocketagent.app.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pocketagent.app.R
import dev.pocketagent.app.data.ChatEngine
import dev.pocketagent.app.data.ChatMode
import dev.pocketagent.app.data.Conversation
import dev.pocketagent.app.ui.LocalNavigator
import dev.pocketagent.app.ui.Route
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconButton
import dev.pocketagent.app.ui.components.PixelIconView
import dev.pocketagent.app.ui.components.PocketTopBar
import dev.pocketagent.app.ui.nav.SharedKeys
import dev.pocketagent.app.ui.nav.navSharedElement
import dev.pocketagent.app.ui.theme.Pocket

/**
 * Everything above the message list: the top bar, the find bar, the group participant bar, the mode banner and the
 * overrides banner. It watches the engine's status and the bots, never the messages (only [shape] for "is there anything").
 */
@Composable
internal fun ChatTopArea(state: ChatScreenState, conv: Conversation, shape: ChatShape, search: SearchView) {
    val c = Pocket.colors
    val nav = LocalNavigator.current
    val repo = state.repo
    val engine = state.engine
    val convId = conv.id
    val bots by repo.bots.collectAsState()
    val busySet by engine.busy.collectAsState()
    val speaking by engine.speaking.collectAsState()
    val statusMap by engine.status.collectAsState()

    val botsById = remember(bots) { bots.associateBy { it.id } }
    val members = conv.botIds.map { botsById[it] }
    val memberBots = members.filterNotNull()
    val isGroup = conv.isGroup
    val generating = convId in busySet
    val speakingBot = speaking[convId]?.let { botsById[it] }
    val title = conversationTitle(conv, botsById)
    // Wake-on-send: the message is shown and the engine is lighting the fire before it delivers it.
    val waking = statusMap[convId] == ChatEngine.STATUS_WAKING
    val subtitle = when {
        waking -> stringResource(R.string.ui_chat_waking)
        generating && !statusMap[convId].isNullOrBlank() -> statusMap[convId].orEmpty()
        generating && speakingBot != null -> stringResource(R.string.ui_chat_typing_who, speakingBot.name)
        generating -> stringResource(R.string.ui_chat_typing)
        isGroup -> memberBots.joinToString(", ") { it.name }
        else -> memberBots.firstOrNull()?.tagline.orEmpty()
    }.takeIf { it.isNotBlank() }
    val hasOverrides = conv.mode != ChatMode.NORMAL || conv.modelOverride.isNotBlank() || conv.effortOverride.isNotBlank()

    Column(Modifier.fillMaxWidth()) {
        PocketTopBar(
            title = title,
            onBack = { if (!nav.back()) nav.tab(Route.Chats) },
            subtitle = subtitle,
            leading = {
                AvatarStack(members, 36.dp, Modifier.navSharedElement(SharedKeys.chatAvatar(convId)))
            },
            actions = {
                if (conv.pinned) PixelIconView(PixelIcon.PIN, tint = c.accent, size = 18.dp)
                if (shape.count > 0) {
                    PixelIconButton(
                        PixelIcon.SEARCH,
                        onClick = { if (state.searching) state.closeSearch() else state.openSearch("") },
                        modifier = Modifier.testTag("chat.find"),
                        contentDescription = stringResource(R.string.ui_chat_find),
                        tint = if (search.active) c.accent else c.text,
                    )
                }
                PixelIconButton(
                    PixelIcon.SLIDERS,
                    onClick = { state.showControls = true },
                    modifier = Modifier.testTag("chat.controls"),
                    contentDescription = stringResource(R.string.ui_ctrl_title),
                    tint = if (hasOverrides) c.accent else c.text,
                )
                PixelIconButton(
                    PixelIcon.MORE,
                    onClick = { state.menu = true },
                    modifier = Modifier.testTag("chat.menu"),
                    contentDescription = stringResource(R.string.ui_chat_more),
                )
            },
        )
        if (search.active) {
            ChatSearchBar(
                query = search.query,
                onQueryChange = { state.searchQuery = it; state.searchPos = Int.MAX_VALUE },
                matchCount = search.matchIds.size,
                position = search.position,
                onPrev = { state.searchPos = cycleIndex(search.position, search.matchIds.size, -1) },
                onNext = { state.searchPos = cycleIndex(search.position, search.matchIds.size, 1) },
                onClose = { state.closeSearch() },
                modifier = Modifier.testTag("chat.search"),
            )
        }
        if (isGroup) {
            GroupParticipantBar(
                members = memberBots,
                speakingBotId = speaking[convId],
                groupMode = conv.groupMode,
                onMentionBot = { b ->
                    val prefix = if (state.input.isBlank() || state.input.endsWith(" ")) "" else " "
                    state.input = "${state.input}$prefix@${mentionName(b)} "
                },
                onEditMembers = { state.showMembers = true },
            )
        }
        ActiveModeBanner(conv.mode) {
            repo.setMode(convId, ChatMode.NORMAL)
            engine.addLocalNote(convId, state.context.getString(R.string.ui_mode_off_note))
        }
        ChatOverridesBanner(
            modelOverride = conv.modelOverride,
            effortOverride = conv.effortOverride,
            onOpenControls = { state.showControls = true },
            onClear = { repo.setOverrides(convId, "", "") },
        )
    }
}
