package dev.pocketagent.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.pocketagent.app.R
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.Type

/**
 * The one confirm dialog of the app. [danger] makes the confirm button red. [onConfirm] is called on the
 * confirm button; with [closeOnConfirm] the dialog then asks to be closed ([onDismiss]), otherwise the caller
 * closes it itself (what the settings and routines variants did). The cancel button says "Cancel" unless
 * [dismissLabel] gives other words. Tags: dialog.confirm, dialog.cancel.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
    closeOnConfirm: Boolean = false,
    dismissLabel: String? = null,
) {
    val cancel = dismissLabel ?: stringResource(R.string.cancel)
    PocketDialog(
        title = title,
        onDismiss = onDismiss,
        confirm = {
            PixelButton(
                text = confirmLabel,
                onClick = {
                    onConfirm()
                    if (closeOnConfirm) onDismiss()
                },
                kind = if (danger) ButtonKind.DANGER else ButtonKind.PRIMARY,
                modifier = Modifier.testTag("dialog.confirm"),
            )
        },
        dismiss = {
            PixelButton(
                text = cancel,
                onClick = onDismiss,
                kind = ButtonKind.GHOST,
                modifier = Modifier.testTag("dialog.cancel"),
            )
        },
    ) {
        Text(body, color = Pocket.colors.text, style = Type.body)
    }
}
