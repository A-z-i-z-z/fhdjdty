package dev.pocketagent.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.R
import dev.pocketagent.app.data.Bot
import dev.pocketagent.app.data.ChatMode
import dev.pocketagent.app.data.Conversation
import dev.pocketagent.app.data.GroupMode
import dev.pocketagent.app.runtime.ProviderConfig
import dev.pocketagent.app.ui.components.ButtonKind
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconView
import dev.pocketagent.app.ui.components.PocketChip
import dev.pocketagent.app.ui.theme.Pocket

/**
 * Slim participant bar shown directly under the top bar in group chats:
 * shows each bot's avatar, glows warmly around whoever is speaking, tap to @mention, plus a quick Members/Mode chip.
 */
@Composable
internal fun GroupParticipantBar(
    members: List<Bot>,
    speakingBotId: String?,
    groupMode: GroupMode,
    onMentionBot: (Bot) -> Unit,
    onEditMembers: () -> Unit,
) {
    val c = Pocket.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        members.forEach { bot ->
            val speaking = bot.id == speakingBotId
            Row(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (speaking) c.accentSoft else c.surfaceHigh)
                    .border(if (speaking) 1.5.dp else 1.dp, if (speaking) c.fireMid else c.outline, RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button) { onMentionBot(bot) }
                    .padding(start = 4.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BotAvatar(bot, 22.dp, animated = speaking)
                Spacer(Modifier.width(6.dp))
                Text(
                    bot.name,
                    color = if (speaking) c.accent else c.text,
                    fontSize = 12.sp,
                    fontWeight = if (speaking) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
                if (speaking) {
                    Spacer(Modifier.width(4.dp))
                    GeneratingEmber(size = 12.dp)
                }
            }
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(c.bgDeep)
                .border(1.dp, c.outline, RoundedCornerShape(16.dp))
                .clickable(role = Role.Button) { onEditMembers() }
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PixelIconView(PixelIcon.GROUP, tint = c.textMuted, size = 14.dp)
            Text(
                stringResource(if (groupMode == GroupMode.MENTION_ONLY) R.string.ui_group_bar_mention else R.string.ui_group_bar_all),
                color = c.textMuted, fontSize = 11.sp,
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.outline.copy(alpha = 0.5f)))
}

/** Compact banner shown whenever per-chat Model or Thinking Effort overrides are active. */
@Composable
internal fun ChatOverridesBanner(
    modelOverride: String,
    effortOverride: String,
    onOpenControls: () -> Unit,
    onClear: () -> Unit,
) {
    if (modelOverride.isBlank() && effortOverride.isBlank()) return
    val c = Pocket.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.surfaceHigh)
            .clickable(role = Role.Button) { onOpenControls() }
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PixelIconView(PixelIcon.SLIDERS, tint = c.accent, size = 14.dp)
        val summary = buildList {
            if (modelOverride.isNotBlank()) add(modelOverride.substringAfterLast('/'))
            if (effortOverride.isNotBlank()) {
                val efLabel = effortLabelRes(effortOverride)?.let { stringResource(it) } ?: effortOverride
                add("${stringResource(R.string.ui_ctrl_effort_short)}: $efLabel")
            }
        }.joinToString(" · ")
        Text(
            summary,
            color = c.text,
            fontSize = 11.5.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.ui_ctrl_reset),
            color = c.accent,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable(role = Role.Button) { onClear() }
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * In-chat Quick Controls bottom sheet: 1-tap switch for Mode, per-chat Model, per-chat Thinking Effort,
 * plus shortcuts to the Skill Picker and Conversation Forking.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ChatControlsSheet(
    conv: Conversation,
    modelSuggestions: List<String>,
    onSelectMode: (ChatMode) -> Unit,
    onSelectOverrides: (model: String, effort: String) -> Unit,
    onOpenSkills: () -> Unit,
    onForkChat: () -> Unit,
    onDismiss: () -> Unit,
    /** Turns and tokens of this chat; the section is hidden while there are none. */
    usage: ChatUsageTotals = ChatUsageTotals(0, 0L, 0L),
) {
    val c = Pocket.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        contentColor = c.text,
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.outline) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(bottomSafeInsets())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelIconView(PixelIcon.SLIDERS, tint = c.accent, size = 20.dp)
                Text(stringResource(R.string.ui_ctrl_title), color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }

            // ---- Operating Mode ----
            Text(stringResource(R.string.ui_ctrl_mode), color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val modes = listOf(
                    ChatMode.NORMAL to R.string.ui_ctrl_mode_normal,
                    ChatMode.PLAN to R.string.ui_mode_banner_plan,
                    ChatMode.GOAL to R.string.ui_mode_banner_goal,
                    ChatMode.ULTRACODE to R.string.ui_mode_banner_ultracode,
                    ChatMode.LOOP to R.string.ui_mode_banner_loop,
                )
                modes.forEach { (m, labelRes) ->
                    PocketChip(
                        stringResource(labelRes),
                        selected = conv.mode == m,
                        onClick = { onSelectMode(m) },
                        icon = if (m == ChatMode.NORMAL) null else PixelIcon.BOLT,
                    )
                }
            }

            // ---- Per-chat Model ----
            Text(stringResource(R.string.ui_ctrl_model), color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PocketChip(
                    stringResource(R.string.effort_default),
                    selected = conv.modelOverride.isBlank(),
                    onClick = { onSelectOverrides("", conv.effortOverride) },
                )
                modelSuggestions.forEach { m ->
                    val short = m.substringAfterLast('/')
                    PocketChip(
                        short,
                        selected = conv.modelOverride == m || conv.modelOverride == short,
                        onClick = { onSelectOverrides(m, conv.effortOverride) },
                    )
                }
            }

            // ---- Per-chat Thinking Effort ----
            Text(stringResource(R.string.ui_ctrl_effort), color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PocketChip(
                    stringResource(R.string.effort_default),
                    selected = conv.effortOverride.isBlank(),
                    onClick = { onSelectOverrides(conv.modelOverride, "") },
                )
                ProviderConfig.REASONING_EFFORTS.forEach { ef ->
                    val label = effortLabelRes(ef)?.let { stringResource(it) } ?: ef.replaceFirstChar { it.uppercase() }
                    PocketChip(
                        label,
                        selected = conv.effortOverride == ef,
                        onClick = { onSelectOverrides(conv.modelOverride, ef) },
                    )
                }
            }

            if (!usage.isEmpty) {
                val locale = currentLocale()
                Text(stringResource(R.string.ui_usage_title), color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelIconView(PixelIcon.CHART, tint = c.accent, size = 18.dp)
                    Text(
                        stringResource(
                            R.string.ui_usage_totals, usage.replies,
                            compactTokens(usage.tokensIn, locale), compactTokens(usage.tokensOut, locale),
                        ),
                        color = c.text, fontSize = 14.sp,
                    )
                }
            }

            Hairline()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelButton(
                    stringResource(R.string.ui_ctrl_skills),
                    onClick = { onDismiss(); onOpenSkills() },
                    modifier = Modifier.weight(1f),
                    icon = PixelIcon.SKILL,
                    kind = ButtonKind.SECONDARY,
                )
                PixelButton(
                    stringResource(R.string.ui_ctrl_fork),
                    onClick = { onDismiss(); onForkChat() },
                    modifier = Modifier.weight(1f),
                    icon = PixelIcon.FORK,
                    kind = ButtonKind.SECONDARY,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun effortLabelRes(effort: String): Int? = when (effort.trim().lowercase()) {
    "none" -> R.string.effort_none
    "minimal" -> R.string.effort_minimal
    "low" -> R.string.effort_low
    "medium" -> R.string.effort_medium
    "high" -> R.string.effort_high
    "xhigh" -> R.string.effort_xhigh
    else -> null
}
