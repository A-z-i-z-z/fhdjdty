package dev.pocketagent.app.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import dev.pocketagent.app.R
import dev.pocketagent.app.ui.nav.robotRoot
import dev.pocketagent.app.ui.theme.Corner
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.Space
import dev.pocketagent.app.ui.theme.Type
import dev.pocketagent.app.ui.theme.designUnitPx
import dev.pocketagent.app.ui.theme.motion
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// The one bottom sheet of the app (pattern 4): it rises with the glide spring, follows the finger by its grabber
// and title, flings away by velocity, and sits over a dither scrim. Its own window, so it also covers the bars.

/** Where a released drag ends. */
enum class SheetOutcome { OPEN, CLOSE }

/** Pure: the decision of a released drag. */
object SheetPhysics {
    const val FLING_PX_PER_SEC = 1200f
    const val CLOSE_FRACTION = 0.35f

    /**
     * [offsetPx] is how far the sheet was pulled down, [velocityPxPerSec] positive downward. CLOSE when the fling
     * is faster than 1200 px/s downward, or when it is not flung upward that fast and was pulled further than
     * 35 % of [heightPx].
     */
    fun settle(offsetPx: Float, velocityPxPerSec: Float, heightPx: Float): SheetOutcome =
        if (velocityPxPerSec > FLING_PX_PER_SEC ||
            (velocityPxPerSec > -FLING_PX_PER_SEC && offsetPx > CLOSE_FRACTION * heightPx)
        ) SheetOutcome.CLOSE else SheetOutcome.OPEN
}

/** Pure: the step 0 to 4 of the dither scrim for a sheet that is [openFraction] open (0 closed, 1 open). */
fun scrimStepFor(openFraction: Float): Int {
    if (openFraction.isNaN()) return 0
    return (openFraction.coerceIn(0f, 1f) * DitherPattern.STEPS).roundToInt()
}

/**
 * What a sheet's content can call: everything of a column, plus [close], which slides the sheet away, then calls
 * the sheet's onDismiss, then [then].
 */
interface PocketSheetScope : ColumnScope {
    fun close(then: () -> Unit = {})
}

private class SheetScopeImpl(
    private val column: ColumnScope,
    private val onClose: (() -> Unit) -> Unit,
) : PocketSheetScope, ColumnScope by column {
    override fun close(then: () -> Unit) = onClose(then)
}

private fun bottomSafeInsets(): WindowInsets =
    WindowInsets.navigationBars.union(WindowInsets.mandatorySystemGestures.only(WindowInsetsSides.Bottom))

/**
 * A bottom sheet with an optional [title]. The content is bounded in height ([maxHeightFraction] of the screen)
 * and is not wrapped in a scroll: put a LazyColumn with weight(1f, fill = false) in it when it can be long. The
 * sheet pads itself for the navigation bar and the keyboard. Tags: sheet, sheet.scrim, sheet.handle,
 * sheet.title.
 */
@Composable
fun PocketSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    maxHeightFraction: Float = 0.92f,
    content: @Composable PocketSheetScope.() -> Unit,
) {
    val c = Pocket.colors
    val motion = Pocket.motion
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val currentDismiss by rememberUpdatedState(onDismiss)
    var heightPx by remember { mutableIntStateOf(0) }
    var translatePx by remember { mutableFloatStateOf(0f) }
    var started by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }

    val requestClose: (() -> Unit) -> Unit = { then ->
        if (!closing) {
            closing = true
            scope.launch {
                animate(
                    initialValue = translatePx,
                    targetValue = maxOf(heightPx.toFloat(), translatePx),
                    animationSpec = motion.glide(),
                ) { v, _ -> translatePx = v }
                currentDismiss()
                then()
            }
        }
    }

    LaunchedEffect(heightPx > 0) {
        if (heightPx > 0 && !started) {
            started = true
            val h = heightPx.toFloat()
            translatePx = h
            animate(initialValue = h, targetValue = 0f, animationSpec = motion.glide()) { v, _ ->
                if (!closing) translatePx = v
            }
        }
    }

    val unitPx = designUnitPx(density.density)
    val cut = with(density) { (Corner.sheet * unitPx).toDp() }
    val grabberWidth = with(density) { (12 * unitPx).toDp() }
    val grabberHeight = with(density) { (2 * unitPx).toDp() }
    val closeLabel = stringResource(R.string.ui_dlg_close)

    val dragState = rememberDraggableState { delta ->
        if (!closing) translatePx = (translatePx + delta).coerceAtLeast(0f)
    }
    val dragStrip = Modifier.draggable(
        state = dragState,
        orientation = Orientation.Vertical,
        onDragStopped = { velocity ->
            if (!closing) {
                when (SheetPhysics.settle(translatePx, velocity, heightPx.toFloat())) {
                    SheetOutcome.CLOSE -> requestClose {}
                    SheetOutcome.OPEN -> animate(initialValue = translatePx, targetValue = 0f, animationSpec = motion.glide()) { v, _ ->
                        if (!closing) translatePx = v
                    }
                }
            }
        },
    )

    Dialog(
        onDismissRequest = { requestClose {} },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        HoldPetNotesWhileOpen()
        val none = remember { MutableInteractionSource() }

        BoxWithConstraints(Modifier.robotRoot().fillMaxSize()) {
            val maxPanelHeight = maxHeight * maxHeightFraction.coerceIn(0.2f, 1f)
            val openFraction = if (!started || heightPx <= 0) 0f else 1f - translatePx / heightPx
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag("sheet.scrim")
                    .clickable(interactionSource = none, indication = null) { requestClose {} },
            ) {
                if (window != null) DitherScrim(step = scrimStepFor(openFraction), modifier = Modifier.fillMaxSize())
            }
            Box(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).imePadding(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                PixelPanel(
                    modifier = modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .heightIn(max = maxPanelHeight)
                        .offset(y = cut)
                        .onSizeChanged { heightPx = it.height }
                        .graphicsLayer { translationY = if (started) translatePx else size.height }
                        .testTag("sheet")
                        .pointerInput(Unit) { detectTapGestures { } },
                    color = c.surface,
                    border = c.outline,
                    corner = Corner.sheet,
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .testTag("sheet.handle")
                                .then(dragStrip)
                                .clickable(onClickLabel = closeLabel, role = Role.Button) { requestClose {} }
                                .padding(top = Space.s, bottom = if (title == null) Space.s else Space.xxs),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.size(width = grabberWidth, height = grabberHeight).background(c.outline))
                            if (title != null) {
                                Text(
                                    text = title, color = c.textMuted, style = Type.label, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 6.dp)
                                        .testTag("sheet.title")
                                        .semantics { heading() },
                                )
                            }
                        }
                        Column(
                            Modifier
                                .weight(1f, fill = false)
                                .fillMaxWidth()
                                .windowInsetsPadding(bottomSafeInsets())
                                .padding(bottom = cut + Space.m),
                        ) {
                            SheetScopeImpl(this, requestClose).content()
                        }
                    }
                }
            }
        }
    }
}

/** One row of an [ActionSheet]. */
class SheetAction(val icon: PixelIcon, val label: String, val danger: Boolean = false, val onClick: () -> Unit)

/** A bottom sheet with a list of actions: easy to reach with one hand. Rows are tagged sheet.action.0, .1 and so on. */
@Composable
fun ActionSheet(title: String?, actions: List<SheetAction>, onDismiss: () -> Unit) {
    val c = Pocket.colors
    PocketSheet(onDismiss = onDismiss, title = title?.takeIf { it.isNotBlank() }) {
        actions.forEachIndexed { i, a ->
            val tint = if (a.danger) c.error else c.text
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("sheet.action.$i")
                    .clickable(role = Role.Button) { close { a.onClick() } }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelIconView(a.icon, tint = tint, size = 22.dp)
                Spacer(Modifier.width(Space.l))
                Text(a.label, color = tint, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
