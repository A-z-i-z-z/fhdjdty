package dev.pocketagent.app.ui.components

import androidx.compose.animation.core.FiniteAnimationSpec
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import dev.pocketagent.app.R
import dev.pocketagent.app.ui.nav.robotRoot
import dev.pocketagent.app.ui.theme.AnimLevel
import dev.pocketagent.app.ui.theme.Corner
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.Space
import dev.pocketagent.app.ui.theme.Type
import dev.pocketagent.app.ui.theme.designUnitPx
import dev.pocketagent.app.ui.theme.motion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import dev.pocketagent.app.ui.text.t
import dev.pocketagent.app.util.AppLog

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

@Composable
private fun bottomSafeInsets(): WindowInsets =
    WindowInsets.navigationBars.union(WindowInsets.mandatorySystemGestures.only(WindowInsetsSides.Bottom))

/**
 * A bottom sheet with an optional [title]. The content is bounded in height ([maxHeightFraction] of the screen)
 * and is not wrapped in a scroll: put a LazyColumn with weight(1f, fill = false) in it when it can be long. The
 * sheet pads itself for the navigation bar and the keyboard. Tags: sheet, sheet.scrim, sheet.handle,
 * sheet.title.
 *
 * How it moves (see [SheetSlide]): with animations on it starts one height below the screen and glides up;
 * with animations off it is at rest from the first frame. Its place is a FRACTION of its own height that the
 * layer turns into pixels at draw time, so nothing about being visible waits for a measurement. The motion runs
 * in an effect keyed only by the count of started motions (a layout pass cannot cancel it) and is finished by force,
 * with a line in the app log, when no frame moves it for [SheetSlide.STALL_MS].
 * For a screen reader the dimmed area is a button "Close", and the sheet is a pane with its own name.
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
    val currentDismiss by rememberUpdatedState(onDismiss)
    val animated = motion.level != AnimLevel.OFF
    val slideState = remember { mutableStateOf(SheetSlide.opening(animated)) }
    val heightState = remember { mutableIntStateOf(0) }
    val afterClose = remember { ArrayList<() -> Unit>() }
    // The scrim only needs to know its step (0 to 4), not every frame of the slide.
    val scrimStep by remember {
        derivedStateOf { scrimStepFor(slideState.value.openFraction(heightState.intValue.toFloat())) }
    }
    val drive by remember { derivedStateOf { slideState.value.drive } }

    val requestClose = remember<(() -> Unit) -> Unit> {
        { then ->
            val next = slideState.value.closing(heightState.intValue.toFloat())
            if (next != null) {
                afterClose.add(then)
                slideState.value = next
            }
        }
    }

    val unitPx = designUnitPx(density.density)
    val cut = with(density) { (Corner.sheet * unitPx).toDp() }
    val grabberWidth = with(density) { (12 * unitPx).toDp() }
    val grabberHeight = with(density) { (2 * unitPx).toDp() }
    val closeLabel = t(R.string.ui_dlg_close)
    val scrimLabel = t(R.string.design_scrim_close)
    val paneName = title ?: t(R.string.design_sheet_pane)

    val dragState = rememberDraggableState { delta -> slideState.value = slideState.value.dragged(delta) }
    val dragStrip = Modifier.draggable(
        state = dragState,
        orientation = Orientation.Vertical,
        onDragStopped = { velocity ->
            val now = slideState.value
            if (now.phase != SheetPhase.CLOSING) {
                val h = heightState.intValue.toFloat()
                when (SheetPhysics.settle(now.dragPx, velocity, h)) {
                    SheetOutcome.CLOSE -> requestClose {}
                    SheetOutcome.OPEN -> slideState.value = now.releasedOpen(h)
                }
            }
        },
    )

    PocketWindow(
        onDismissRequest = { requestClose {} },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        HoldPetNotesWhileOpen()
        val none = remember { MutableInteractionSource() }

        // The motion of the sheet: opening, coming home after a short drag, closing. Keyed by the count of started
        // motions only, never by a measurement, so a layout pass (the height going to 0 and back while the window or
        // the insets are laid out) cannot cancel it and leave the sheet half way. The window shares the frame clock
        // of the caller's composition; if frames still stop, playSlide's watchdog finishes the motion by force.
        LaunchedEffect(drive) {
            val from = slideState.value
            val spec = if (animated) motion.glide<Float>() else null
            if (from.phase == SheetPhase.CLOSING) {
                playSlide(
                    from.hidden, from.closeTarget, spec,
                    apply = { v -> slideState.value = slideState.value.exitFrame(v) },
                    onStall = ::logStall,
                )
                currentDismiss()
                val after = afterClose.toList()
                afterClose.clear()
                after.forEach { it() }
            } else {
                if (from.hidden > 0f) {
                    playSlide(
                        from.hidden, 0f, spec,
                        apply = { v -> slideState.value = slideState.value.enterFrame(v) },
                        onStall = ::logStall,
                    )
                }
                slideState.value = slideState.value.rested()
            }
        }

        BoxWithConstraints(
            Modifier.robotRoot().semantics { isTraversalGroup = true }.fillMaxSize(),
        ) {
            val maxPanelHeight = maxHeight * maxHeightFraction.coerceIn(0.2f, 1f)
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag("sheet.scrim")
                    // Read after the sheet itself, which has the default index 0.
                    .semantics { traversalIndex = 1f }
                    .clickable(interactionSource = none, indication = null, onClickLabel = scrimLabel, role = Role.Button) {
                        requestClose {}
                    }
                    .semantics { contentDescription = scrimLabel },
            ) {
                if (window != null) DitherScrim(step = scrimStep, modifier = Modifier.fillMaxSize())
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
                        .onSizeChanged { heightState.intValue = it.height }
                        .graphicsLayer { translationY = slideState.value.translationPx(size.height) }
                        .semantics {
                            isTraversalGroup = true
                            paneTitle = paneName
                            dismiss(label = scrimLabel) {
                                requestClose {}
                                true
                            }
                        }
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

private fun logStall() {
    AppLog.w("Sheet", "no frame moved the sheet for ${SheetSlide.STALL_MS} ms; the motion was finished by force")
}

/**
 * Moves a value from [from] to [to] with [spec] and hands every frame to [apply]. With no spec (animations off), or
 * when there is nothing to move, it applies [to] at once and needs no frame at all. A watchdog looks for PROGRESS:
 * every [stallMs] it checks that at least one frame arrived since its last look. If none did, whether the clock never
 * ticked or stopped half way, the motion is cancelled, [onStall] is called and [apply] gets [to], so a sheet can
 * neither stay (partly) below the screen nor refuse to close. [apply] gets [to] again at the end of a normal motion.
 */
internal suspend fun playSlide(
    from: Float,
    to: Float,
    spec: FiniteAnimationSpec<Float>?,
    apply: (Float) -> Unit,
    stallMs: Long = SheetSlide.STALL_MS,
    onStall: () -> Unit = {},
) {
    if (spec == null || from == to) {
        apply(to)
        return
    }
    coroutineScope {
        var frames = 0
        val run = launch {
            animate(initialValue = from, targetValue = to, animationSpec = spec) { v, _ ->
                frames++
                apply(v)
            }
        }
        val watchdog = launch {
            var seen = frames
            while (true) {
                delay(stallMs)
                if (frames == seen) {
                    onStall()
                    run.cancel()
                    break
                }
                seen = frames
            }
        }
        run.join()
        watchdog.cancel()
        apply(to)
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
