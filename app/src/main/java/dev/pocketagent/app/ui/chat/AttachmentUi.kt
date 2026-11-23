package dev.pocketagent.app.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.data.Attachment
import dev.pocketagent.app.data.AttachmentKind
import dev.pocketagent.app.data.Bot
import dev.pocketagent.app.ui.components.ButtonKind
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconButton
import dev.pocketagent.app.ui.components.PixelIconView
import dev.pocketagent.app.ui.components.PixelPanel
import dev.pocketagent.app.ui.components.PocketChip
import dev.pocketagent.app.ui.components.PocketDialog
import dev.pocketagent.app.ui.theme.Pocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttachSheet(
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onDocument: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Pocket.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        contentColor = c.text,
        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.textMuted) },
    ) {
        Column(Modifier.fillMaxWidth().windowInsetsPadding(bottomSafeInsets()).padding(bottom = 14.dp)) {
            Text(
                stringResource(R.string.ui_attach_title), color = c.textMuted, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            AttachRow(PixelIcon.CAMERA, stringResource(R.string.ui_attach_camera)) { onDismiss(); onCamera() }
            AttachRow(PixelIcon.IMAGE, stringResource(R.string.ui_attach_gallery)) { onDismiss(); onGallery() }
            AttachRow(PixelIcon.FILE, stringResource(R.string.ui_attach_doc)) { onDismiss(); onDocument() }
        }
    }
}

@Composable
private fun AttachRow(icon: PixelIcon, label: String, onClick: () -> Unit) {
    val c = Pocket.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelIconView(icon, tint = c.accent, size = 22.dp)
        Spacer(Modifier.width(16.dp))
        Text(label, color = c.text, fontSize = 16.sp)
    }
}

@Composable
internal fun PendingAttachmentsStrip(items: List<Attachment>, onRemove: (String) -> Unit) {
    val c = Pocket.colors
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { att ->
            Box(
                Modifier.background(c.surfaceHigh).border(1.dp, c.outline).padding(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (att.kind == AttachmentKind.IMAGE) {
                        val bmp = rememberAttachmentThumb(att.localPath, 140)
                        if (bmp != null) {
                            Image(
                                bitmap = bmp, contentDescription = att.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp).border(1.dp, c.outline),
                            )
                        } else {
                            PixelIconView(PixelIcon.IMAGE, tint = c.accent, size = 24.dp)
                        }
                    } else {
                        PixelIconView(PixelIcon.FILE, tint = c.accent, size = 22.dp)
                    }
                    Column(Modifier.width(88.dp)) {
                        Text(att.name, color = c.text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatAttachmentSize(att.sizeBytes), color = c.textMuted, fontSize = 10.sp)
                    }
                    PixelIconButton(
                        PixelIcon.CLOSE, onClick = { onRemove(att.id) },
                        contentDescription = stringResource(R.string.ui_attach_remove), tint = c.textMuted,
                    )
                }
            }
        }
    }
}

@Composable
internal fun MessageAttachmentsView(attachments: List<Attachment>, onOpenImage: (Attachment) -> Unit) {
    val c = Pocket.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        attachments.forEach { att ->
            if (att.kind == AttachmentKind.IMAGE) {
                val bmp = rememberAttachmentThumb(att.localPath, 480)
                if (bmp != null) {
                    Image(
                        bitmap = bmp, contentDescription = att.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp, max = 210.dp)
                            .border(2.dp, c.outline)
                            .clickable(role = Role.Image) { onOpenImage(att) },
                    )
                } else {
                    DocumentBadge(att, PixelIcon.IMAGE)
                }
            } else {
                DocumentBadge(att, PixelIcon.FILE)
            }
        }
    }
}

@Composable
private fun DocumentBadge(att: Attachment, icon: PixelIcon) {
    val c = Pocket.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(c.bgDeep.copy(alpha = 0.65f))
            .border(1.dp, c.outline, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PixelIconView(icon, tint = c.accentSoft, size = 18.dp)
        Column(Modifier.weight(1f)) {
            Text(att.name, color = c.onUserBubble, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatAttachmentSize(att.sizeBytes), color = c.onUserBubble.copy(alpha = 0.72f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
internal fun ImagePreviewDialog(attachment: Attachment, onDismiss: () -> Unit) {
    val c = Pocket.colors
    val bmp = rememberAttachmentThumb(attachment.localPath, 1400)
    PocketDialog(
        title = attachment.name,
        onDismiss = onDismiss,
        confirm = { PixelButton(stringResource(R.string.ui_dlg_close), onClick = onDismiss) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (bmp != null) {
                Image(
                    bitmap = bmp, contentDescription = attachment.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).background(c.bgDeep).border(1.dp, c.outline),
                )
            }
            Text(
                "${attachment.mime} • ${formatAttachmentSize(attachment.sizeBytes)}",
                color = c.textMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
internal fun ForgedBotCard(
    bot: Bot,
    inCurrentChat: Boolean,
    isGroupChat: Boolean,
    onChatNow: () -> Unit,
    onAddToChat: () -> Unit,
    onEdit: () -> Unit,
) {
    val c = Pocket.colors
    PixelPanel(Modifier.padding(top = 6.dp).fillMaxWidth(), color = c.surfaceHigh, border = c.accent) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BotAvatar(bot, 42.dp, animated = true)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(bot.name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(
                            Modifier.background(c.accent).padding(horizontal = 5.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            PixelIconView(PixelIcon.FORGE, tint = c.onAccent, size = 10.dp)
                            Text(stringResource(R.string.ui_forged_badge), color = c.onAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (bot.tagline.isNotBlank()) {
                        Text(bot.tagline, color = c.textMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PocketChip(stringResource(R.string.ui_forged_chat), false, onChatNow, icon = PixelIcon.CHATS)
                if (isGroupChat && !inCurrentChat) {
                    PocketChip(stringResource(R.string.ui_forged_add), false, onAddToChat, icon = PixelIcon.PLUS)
                }
                PocketChip(stringResource(R.string.ui_forged_edit), false, onEdit, icon = PixelIcon.EDIT)
            }
        }
    }
}

@Composable
private fun rememberAttachmentThumb(path: String, maxPx: Int): ImageBitmap? {
    val ctx = LocalContext.current
    val store = PocketApp.from(ctx).attachments
    val state by produceState<ImageBitmap?>(initialValue = null, path, maxPx) {
        value = withContext(Dispatchers.IO) { store.loadThumbnail(path, maxPx)?.asImageBitmap() }
    }
    return state
}

private fun formatAttachmentSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)}.${(bytes % (1024 * 1024)) * 10 / (1024 * 1024)} MB"
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
