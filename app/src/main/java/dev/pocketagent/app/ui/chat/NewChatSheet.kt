package dev.pocketagent.app.ui.chat

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.data.GroupMode
import dev.pocketagent.app.ui.LocalNavigator
import dev.pocketagent.app.ui.Route
import dev.pocketagent.app.ui.components.ButtonKind
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconView
import dev.pocketagent.app.ui.components.PocketChip
import dev.pocketagent.app.ui.components.PocketSwitchRow
import dev.pocketagent.app.ui.components.PocketTextField
import dev.pocketagent.app.ui.theme.Pocket

/**
 * Bottom sheet to start a conversation: tap a bot for a 1:1 chat, or switch to "Group chat", tick two or more
 * bots, choose who answers and optionally name the group. [onCreated] gets the new conversation's id.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewChatSheet(initialGroup: Boolean, onDismiss: () -> Unit, onCreated: (convId: String) -> Unit) {
    val ctx = LocalContext.current
    val nav = LocalNavigator.current
    val repo = remember { PocketApp.from(ctx).chat }
    val bots by repo.bots.collectAsState()
    val c = Pocket.colors
    var group by remember { mutableStateOf(initialGroup) }
    var selected by remember { mutableStateOf(listOf<String>()) }
    var mode by remember { mutableStateOf(GroupMode.ALL_IN_TURN) }
    var title by remember { mutableStateOf("") }
    val picked = selected.filter { id -> bots.any { it.id == id } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        contentColor = c.text,
        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.textMuted) },
    ) {
        Column(Modifier.fillMaxWidth().windowInsetsPadding(bottomSafeInsets()).padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
            Text(stringResource(R.string.ui_new_title), color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(4.dp))
            PocketSwitchRow(
                title = stringResource(R.string.ui_new_group), checked = group, onCheckedChange = { group = it },
                subtitle = stringResource(R.string.ui_new_group_sub),
            )
            Text(
                stringResource(if (group) R.string.ui_new_pick_many else R.string.ui_new_pick_one),
                color = c.textMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            if (group && bots.size < 2) {
                Text(stringResource(R.string.ui_new_need_bots), color = c.text, fontSize = 15.sp, modifier = Modifier.padding(vertical = 8.dp))
                PixelButton(
                    stringResource(R.string.ui_new_make_bot), onClick = { onDismiss(); nav.go(Route.BotEditor(null)) },
                    icon = PixelIcon.PLUS, kind = ButtonKind.SECONDARY,
                )
            } else {
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    bots.forEach { bot ->
                        val on = bot.id in picked
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .clickable(role = if (group) Role.Checkbox else Role.Button) {
                                    if (group) selected = if (on) picked - bot.id else picked + bot.id
                                    else onCreated(repo.createConversation(listOf(bot.id)).id)
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BotAvatar(bot, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(bot.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (bot.tagline.isNotBlank()) {
                                    Text(bot.tagline, color = c.textMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (group) {
                                Box(
                                    Modifier.size(24.dp).border(2.dp, if (on) c.accent else c.outline),
                                    contentAlignment = Alignment.Center,
                                ) { if (on) PixelIconView(PixelIcon.CHECK, tint = c.accent, size = 18.dp) }
                            }
                        }
                    }
                }
            }
            if (group && bots.size >= 2) {
                Text(stringResource(R.string.ui_new_mode), color = c.textMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PocketChip(stringResource(R.string.ui_new_mode_all), mode == GroupMode.ALL_IN_TURN, { mode = GroupMode.ALL_IN_TURN }, Modifier.weight(1f))
                    PocketChip(stringResource(R.string.ui_new_mode_mention), mode == GroupMode.MENTION_ONLY, { mode = GroupMode.MENTION_ONLY }, Modifier.weight(1f))
                }
                PocketTextField(
                    value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    label = stringResource(R.string.ui_new_title_field), singleLine = true,
                )
                Spacer(Modifier.size(10.dp))
                PixelButton(
                    stringResource(R.string.ui_new_create) + if (picked.isNotEmpty()) " (" + stringResource(R.string.ui_new_selected, picked.size) + ")" else "",
                    onClick = {
                        val conv = repo.createConversation(picked, title.trim().ifBlank { null })
                        repo.setGroupMode(conv.id, mode)
                        onCreated(conv.id)
                    },
                    modifier = Modifier.fillMaxWidth(), icon = PixelIcon.GROUP, enabled = picked.size >= 2,
                )
            }
        }
    }
}
