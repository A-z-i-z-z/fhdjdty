package dev.pocketagent.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import dev.pocketagent.app.ui.nav.robotRoot
import dev.pocketagent.app.ui.theme.Corner
import dev.pocketagent.app.ui.theme.Dur
import dev.pocketagent.app.ui.theme.Ease
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.Space
import dev.pocketagent.app.ui.theme.motion

/**
 * Modal dialog drawn as a pixel panel: title in the pixel font, scrollable [content], and a button row
 * ([dismiss] then [confirm], usually PixelButtons) right-aligned at the bottom. Tapping outside or
 * pressing back calls [onDismiss]. Up to 420 dp wide; resizes with the keyboard. Pattern 5: a dither scrim
 * fills in behind it (in four steps over Dur.FAST, the platform dim is switched off) and the panel pops in;
 * there is no exit animation. Tags: dialog, dialog.title.
 */
@Composable
fun PocketDialog(
    title: String,
    onDismiss: () -> Unit,
    confirm: (@Composable () -> Unit)? = null,
    dismiss: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Pocket.colors
    val motion = Pocket.motion
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val none = remember { MutableInteractionSource() }
        // The platform dims the window behind a dialog; the dither scrim replaces that. When the window cannot
        // be reached the platform dim stays and the own scrim is skipped.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }

        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val scrimStep by animateIntAsState(
            targetValue = if (shown) SCRIM_STEPS else 0,
            animationSpec = motion.custom(Dur.FAST, Ease.steps(SCRIM_STEPS)),
            label = "dialogScrim",
        )
        val pop by animateFloatAsState(
            targetValue = if (shown) 1f else 0f,
            animationSpec = motion.pop(),
            label = "dialogPop",
        )

        HoldPetNotesWhileOpen()

        // The scrim sits in the outer box, outside every padding, so it covers the whole window including the bars.
        Box(Modifier.robotRoot().fillMaxSize()) {
            if (window != null) DitherScrim(step = scrimStep, modifier = Modifier.fillMaxSize())
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = none, indication = null, onClick = onDismiss)
                    .systemBarsPadding()
                    .imePadding()
                    .padding(Space.xl),
                contentAlignment = Alignment.Center,
            ) {
                PixelPanel(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .heightIn(max = maxHeight)
                        .graphicsLayer {
                            val k = pop
                            scaleX = POP_FROM + (1f - POP_FROM) * k
                            scaleY = POP_FROM + (1f - POP_FROM) * k
                            alpha = k
                        }
                        .testTag("dialog")
                        .clickable(interactionSource = none, indication = null, onClick = {}),
                    color = c.surfaceHigh,
                    border = c.accent,
                    corner = Corner.sheet,
                ) {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp)) {
                        PixelText(
                            text = title, color = c.text, pixel = 2.dp, maxLines = 3,
                            modifier = Modifier.testTag("dialog.title").semantics { heading() },
                        )
                        Spacer(Modifier.height(Space.m))
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            content = content,
                        )
                        if (confirm != null || dismiss != null) {
                            Spacer(Modifier.height(Space.l))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Space.s, Alignment.End),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                dismiss?.invoke()
                                confirm?.invoke()
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val SCRIM_STEPS = 4
private const val POP_FROM = 0.9f
