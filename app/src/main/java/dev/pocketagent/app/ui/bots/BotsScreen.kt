package dev.pocketagent.app.ui.bots

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.data.Bot
import dev.pocketagent.app.data.BotPresets
import dev.pocketagent.app.ui.LocalNavigator
import dev.pocketagent.app.ui.RobotTags
import dev.pocketagent.app.ui.Route
import dev.pocketagent.app.ui.components.ButtonKind
import dev.pocketagent.app.ui.components.EmptyState
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconButton
import dev.pocketagent.app.ui.components.PixelIconView
import dev.pocketagent.app.ui.components.PixelPanel
import dev.pocketagent.app.ui.components.PocketTopBar
import dev.pocketagent.app.ui.components.SectionHeader
import dev.pocketagent.app.ui.nav.SharedKeys
import dev.pocketagent.app.ui.nav.navSharedBounds
import dev.pocketagent.app.ui.nav.navSharedElement
import dev.pocketagent.app.ui.pixel.Avatar
import dev.pocketagent.app.ui.pixel.avatarIdOf
import dev.pocketagent.app.ui.settings.CText
import dev.pocketagent.app.ui.settings.Muted
import dev.pocketagent.app.ui.settings.toast
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.text.t
import dev.pocketagent.app.ui.text.tn

/** The "Bots" tab: every bot with its colour, quick chat, templates and multi-select for group chats. */
@Composable
fun BotsScreen() {
    val ctx = LocalContext.current
    val nav = LocalNavigator.current
    val repo = remember { PocketApp.from(ctx).chat }
    val bots by repo.bots.collectAsState()
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(listOf<String>()) }
    val c = Pocket.colors
    val addedMsg = t(R.string.ctl_bots_template_added)

    // Forget selected bots that were deleted in the meantime.
    val picked = selected.filter { id -> bots.any { it.id == id } }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        PocketTopBar(
            title = t(R.string.ctl_bots_title),
            subtitle = tn(R.plurals.ctl_bots_count, bots.size, bots.size),
            actions = {
                PixelIconButton(
                    PixelIcon.GROUP, onClick = { selecting = !selecting; selected = emptyList() },
                    contentDescription = t(if (selecting) R.string.ctl_bots_select_off else R.string.ctl_bots_select_on),
                    tint = if (selecting) c.accent else c.text,
                )
                PixelIconButton(
                    PixelIcon.PLUS, onClick = { nav.go(Route.BotEditor(null)) },
                    contentDescription = t(R.string.ctl_bots_new),
                )
            },
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Muted(t(R.string.ctl_bots_intro)) }
            if (selecting) {
                item {
                    PixelPanel(Modifier.fillMaxWidth(), color = c.accentSoft, border = c.accent) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CText(t(R.string.ctl_bots_select_hint), size = 14.sp)
                            PixelButton(
                                t(R.string.ctl_bots_start_group, picked.size),
                                onClick = {
                                    val conv = repo.createConversation(picked)
                                    selecting = false; selected = emptyList()
                                    nav.go(Route.Chat(conv.id))
                                },
                                icon = PixelIcon.GROUP, enabled = picked.size >= 2,
                            )
                        }
                    }
                }
            }
            if (bots.isEmpty()) {
                item {
                    EmptyState(
                        t(R.string.ctl_bots_empty_title), t(R.string.ctl_bots_empty_body),
                        action = { PixelButton(t(R.string.ctl_bots_new), onClick = { nav.go(Route.BotEditor(null)) }, icon = PixelIcon.PLUS) },
                    )
                }
            }
            itemsIndexed(bots, key = { _, b -> b.id }) { i, bot ->
                BotCard(
                    bot, selecting = selecting, checked = bot.id in picked,
                    onOpen = {
                        if (selecting) selected = if (bot.id in picked) picked - bot.id else picked + bot.id
                        else nav.go(Route.BotEditor(bot.id))
                    },
                    onChat = {
                        val conv = repo.createConversation(listOf(bot.id))
                        nav.go(Route.Chat(conv.id))
                    },
                    modifier = Modifier.testTag(RobotTags.botsRow(i)),
                )
            }
            item { BotShareSection() }
            item { SectionHeader(t(R.string.ctl_bots_templates)) }
            item { Muted(t(R.string.ctl_bots_templates_hint)) }
            items(BotPresets.templates.size, key = { "template-$it" }) { i ->
                val tpl = BotPresets.templates[i]
                TemplateRow(tpl) {
                    repo.saveBot(tpl.copy(id = repo.newId(), builtIn = false, createdAt = System.currentTimeMillis()))
                    toast(ctx, addedMsg)
                }
            }
        }
    }
}

@Composable
private fun BotCard(bot: Bot, selecting: Boolean, checked: Boolean, onOpen: () -> Unit, onChat: () -> Unit, modifier: Modifier = Modifier) {
    val c = Pocket.colors
    val chatLabel = t(R.string.ctl_bots_chat_with, bot.name)
    Box(modifier.fillMaxWidth().navSharedBounds(SharedKeys.botCard(bot.id)).clickable(onClickLabel = t(R.string.ctl_bots_open), onClick = onOpen)) {
        PixelPanel(
            Modifier.fillMaxWidth(),
            color = if (checked) c.accentSoft else c.surface,
            border = if (checked) c.accent else c.outline,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Avatar(avatarIdOf(bot.avatar), Color(bot.color), Modifier.navSharedElement(SharedKeys.botAvatar(bot.id)), size = 48.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    CText(bot.name.ifBlank { "?" }, size = 16.sp, weight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (bot.tagline.isNotBlank()) Muted(bot.tagline, size = 13.sp)
                    if (bot.model.isNotBlank()) {
                        CText(
                            bot.model, color = c.onAccent, size = 11.sp, mono = true, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.background(c.accent).padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
                if (selecting) {
                    Box(
                        Modifier.padding(end = 10.dp).size(26.dp).border(2.dp, if (checked) c.accent else c.outline),
                        contentAlignment = Alignment.Center,
                    ) { if (checked) PixelIconView(PixelIcon.CHECK, tint = c.accent, size = 20.dp) }
                } else {
                    PixelIconButton(PixelIcon.CHATS, onClick = onChat, contentDescription = chatLabel, tint = c.accent)
                }
            }
        }
    }
}

@Composable
private fun TemplateRow(template: Bot, onAdd: () -> Unit) {
    PixelPanel(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Avatar(avatarIdOf(template.avatar), Color(template.color), size = 40.dp)
            Column(Modifier.weight(1f)) {
                CText(template.name, size = 15.sp, weight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Muted(template.tagline, size = 12.sp)
            }
            PixelButton(t(R.string.ctl_bots_add), onClick = onAdd, icon = PixelIcon.PLUS, kind = ButtonKind.SECONDARY)
        }
    }
}
