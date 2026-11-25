package dev.pocketagent.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.R
import dev.pocketagent.app.data.Bot
import dev.pocketagent.app.data.ChatRepository
import dev.pocketagent.app.data.Conversation
import dev.pocketagent.app.ui.components.ButtonKind
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconView
import dev.pocketagent.app.ui.components.PocketDialog
import dev.pocketagent.app.ui.components.PocketTextField
import dev.pocketagent.app.ui.pixel.Avatar
import dev.pocketagent.app.ui.pixel.avatarIdOf
import dev.pocketagent.app.ui.theme.AnimLevel
import dev.pocketagent.app.ui.theme.Pocket
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Small pieces shared by the chat list, the chat screen and the home screen (agent CHAT).

// ---- insets ------------------------------------------------------------------------------------------------------

/**
 * Bottom space the system keeps for itself: the navigation bar, or the gesture-navigation handle area (some
 * Android versions report that only as a system-gesture inset). Content must not sit under it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun bottomSafeInsets(): WindowInsets =
    WindowInsets.navigationBars.union(WindowInsets.mandatorySystemGestures.only(WindowInsetsSides.Bottom))

// ---- bots -------------------------------------------------------------------------------------------------------

internal fun botColor(bot: Bot?): Color = if (bot == null) Color(0xFF8A8A8A.toInt()) else Color(bot.color.toInt())

/** A bot's avatar; a grey newt when the bot was deleted. */
@Composable
internal fun BotAvatar(bot: Bot?, size: Dp, modifier: Modifier = Modifier, animated: Boolean = false) {
    Avatar(avatarIdOf(bot?.avatar), botColor(bot), modifier, size, animated)
}

/** One avatar for a 1:1 chat, a small cluster of up to three for a group. */
@Composable
internal fun AvatarStack(bots: List<Bot?>, size: Dp, modifier: Modifier = Modifier) {
    when {
        bots.isEmpty() -> BotAvatar(null, size, modifier)
        bots.size == 1 -> BotAvatar(bots[0], size, modifier)
        else -> {
            val shown = bots.take(3)
            val sub = size * 0.64f
            val span = size - sub
            Box(modifier.size(size)) {
                shown.forEachIndexed { i, b ->
                    val (fx, fy) = when {
                        shown.size == 2 -> if (i == 0) 0f to 0f else 1f to 1f
                        i == 0 -> 0f to 0f
                        i == 1 -> 1f to 0f
                        else -> 0.5f to 1f
                    }
                    BotAvatar(b, sub, Modifier.offset(span * fx, span * fy))
                }
            }
        }
    }
}

/** The title shown for [conv]: its own, else the member names, else [untitled]. */
internal fun titleOf(conv: Conversation, botsById: Map<String, Bot>, untitled: String): String {
    if (conv.title.isNotBlank()) return conv.title
    val names = conv.botIds.mapNotNull { botsById[it]?.name }
    return if (names.isEmpty()) untitled else names.joinToString(", ")
}

@Composable
internal fun conversationTitle(conv: Conversation, botsById: Map<String, Bot>): String =
    titleOf(conv, botsById, stringResource(R.string.ui_chats_untitled))

/** The latest 1:1 conversation with [botId], or a new one. */
internal fun ChatRepository.openOneToOne(botId: String): Conversation =
    conversations.value.firstOrNull { it.botIds == listOf(botId) } ?: createConversation(listOf(botId))

/** True unless an engine call reported `false` (ignored). Works whether the call returns Unit or Boolean. */
internal fun Any?.accepted(): Boolean = this != false

/** Reactive inbox for Android ACTION_SEND payloads handed into a chat (works even if that chat is already open). */
internal data class SharedChatPayload(
    val text: String? = null,
    val attachment: dev.pocketagent.app.data.Attachment? = null,
)

internal object ChatShareInbox {
    private val _pending = kotlinx.coroutines.flow.MutableStateFlow<Map<String, List<SharedChatPayload>>>(emptyMap())
    val pending: kotlinx.coroutines.flow.StateFlow<Map<String, List<SharedChatPayload>>> = _pending

    fun enqueue(convId: String, text: String?, attachment: dev.pocketagent.app.data.Attachment?) {
        if (text.isNullOrBlank() && attachment == null) return
        val item = SharedChatPayload(text = text?.takeIf { it.isNotBlank() }, attachment = attachment)
        while (true) {
            val cur = _pending.value
            val next = cur + (convId to ((cur[convId] ?: emptyList()) + item))
            if (_pending.compareAndSet(cur, next)) return
        }
    }

    fun take(convId: String): List<SharedChatPayload> {
        while (true) {
            val cur = _pending.value
            val list = cur[convId] ?: return emptyList()
            if (_pending.compareAndSet(cur, cur - convId)) return list
        }
    }
}

// ---- time -------------------------------------------------------------------------------------------------------

private const val DAY_MS = 24L * 60 * 60 * 1000

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

@Composable
internal fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** "12:30" in the app language's style. */
@Composable
internal fun clockText(ms: Long): String {
    val locale = currentLocale()
    val fmt = remember(locale) { DateFormat.getTimeInstance(DateFormat.SHORT, locale) }
    return fmt.format(Date(ms))
}

/** Chat list style: now / 5 min / 12:30 / Yesterday / 24.11.2026. */
@Composable
internal fun relativeTime(ms: Long): String {
    val locale = currentLocale()
    val now = System.currentTimeMillis()
    val diff = now - ms
    return when {
        diff < 60_000L -> stringResource(R.string.ui_time_now)
        diff < 3_600_000L -> stringResource(R.string.ui_time_min, (diff / 60_000L).toInt())
        sameDay(ms, now) -> clockText(ms)
        sameDay(ms, now - DAY_MS) -> stringResource(R.string.ui_time_yesterday)
        else -> DateFormat.getDateInstance(DateFormat.SHORT, locale).format(Date(ms))
    }
}

/** Day separator in a chat: Today / Yesterday / 24 Nov 2026. */
@Composable
internal fun dayLabel(ms: Long): String {
    val locale = currentLocale()
    val now = System.currentTimeMillis()
    return when {
        sameDay(ms, now) -> stringResource(R.string.ui_chat_today)
        sameDay(ms, now - DAY_MS) -> stringResource(R.string.ui_chat_yesterday)
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(ms))
    }
}

internal fun sameCalendarDay(a: Long, b: Long): Boolean = sameDay(a, b)

/** A moment in the near future: "08:00" today, "Tomorrow, 08:00", else "24.11.2026, 08:00". */
@Composable
internal fun dayTimeLabel(ms: Long): String {
    val locale = currentLocale()
    val now = System.currentTimeMillis()
    val clock = clockText(ms)
    return when {
        sameDay(ms, now) -> clock
        sameDay(ms, now + DAY_MS) -> stringResource(R.string.ui_time_day_at, stringResource(R.string.ui_time_tomorrow), clock)
        else -> stringResource(R.string.ui_time_day_at, DateFormat.getDateInstance(DateFormat.SHORT, locale).format(Date(ms)), clock)
    }
}

// ---- clipboard and sharing ------------------------------------------------------------------------------------

internal fun copyToClipboard(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Agirî", text))
    // Android 13+ shows its own confirmation.
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, ctx.getString(R.string.ui_chat_copied), Toast.LENGTH_SHORT).show()
}

internal fun shareText(ctx: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    try {
        ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        // No app can share text: nothing to do.
    }
}

// ---- generating ember --------------------------------------------------------------------------------------------

/** A small pulsing ember: this conversation is answering right now. */
@Composable
internal fun GeneratingEmber(modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val c = Pocket.colors
    val anim = Pocket.anim
    val t = rememberInfiniteTransition(label = "generating")
    val a = t.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "alpha",
    )
    PixelIconView(
        PixelIcon.SPARK,
        modifier.graphicsLayer { alpha = if (anim == AnimLevel.OFF) 1f else a.value },
        tint = c.ember, size = size,
    )
}

// ---- sheets and dialogs -----------------------------------------------------------------------------------------

internal class SheetAction(val icon: PixelIcon, val label: String, val danger: Boolean = false, val onClick: () -> Unit)

/** A bottom sheet with a list of actions: easy to reach with one hand. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActionSheet(title: String?, actions: List<SheetAction>, onDismiss: () -> Unit) {
    val c = Pocket.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        contentColor = c.text,
        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.textMuted) },
    ) {
        Column(Modifier.fillMaxWidth().windowInsetsPadding(bottomSafeInsets()).padding(bottom = 12.dp)) {
            if (!title.isNullOrBlank()) {
                Text(
                    title, color = c.textMuted, fontSize = 13.sp, maxLines = 1,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            actions.forEach { a ->
                val tint = if (a.danger) c.error else c.text
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        .clickable(role = Role.Button) { onDismiss(); a.onClick() }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PixelIconView(a.icon, tint = tint, size = 22.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(a.label, color = tint, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
internal fun ConfirmDialog(
    title: String, body: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit,
    danger: Boolean = true,
) {
    PocketDialog(
        title = title, onDismiss = onDismiss,
        confirm = {
            PixelButton(confirmLabel, onClick = { onConfirm(); onDismiss() }, kind = if (danger) ButtonKind.DANGER else ButtonKind.PRIMARY)
        },
        dismiss = { PixelButton(stringResource(R.string.ui_dlg_cancel), onClick = onDismiss, kind = ButtonKind.GHOST) },
    ) {
        Text(body, color = Pocket.colors.text, fontSize = 15.sp)
    }
}

@Composable
internal fun RenameDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    PocketDialog(
        title = stringResource(R.string.ui_dlg_rename_title), onDismiss = onDismiss,
        confirm = {
            PixelButton(
                stringResource(R.string.ui_dlg_save),
                onClick = { onSave(text.trim()); onDismiss() }, enabled = text.isNotBlank(),
            )
        },
        dismiss = { PixelButton(stringResource(R.string.ui_dlg_cancel), onClick = onDismiss, kind = ButtonKind.GHOST) },
    ) {
        PocketTextField(
            value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.ui_dlg_rename_field), singleLine = true,
        )
    }
}

/** Small section label used inside lists (system font: it may be long). */
@Composable
internal fun ListLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text, color = Pocket.colors.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
internal fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Pocket.colors.outline.copy(alpha = 0.5f)))
}
