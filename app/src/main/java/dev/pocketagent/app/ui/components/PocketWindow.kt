package dev.pocketagent.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The one door to a window of its own for every sheet and dialog of the app.
 *
 * A Compose Dialog is a separate window with a separate AndroidComposeView, and that view provides context,
 * configuration, resources, density and layout direction again from the dialog's OWN context: the phone's
 * language, the system font scale. Everything the shell set up outside (the language chosen in the app, the
 * text size, a forced layout direction of a test build) would stop at the window's edge, and stringResource and
 * t() inside the window would speak the phone's language. So the values of the composition that opens the window
 * are captured here, outside, and given again inside. Never call Dialog, Popup or a Material sheet or menu
 * directly: go through this.
 */
@Composable
internal fun PocketWindow(
    onDismissRequest: () -> Unit,
    properties: DialogProperties,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val resources = LocalResources.current
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        CompositionLocalProvider(
            LocalContext provides context,
            LocalConfiguration provides configuration,
            LocalResources provides resources,
            LocalLayoutDirection provides layoutDirection,
            LocalDensity provides density,
        ) { content() }
    }
}
