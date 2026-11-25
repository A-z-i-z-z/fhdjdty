package dev.pocketagent.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pocketagent.app.R
import dev.pocketagent.app.ui.pixel.Mascot
import dev.pocketagent.app.ui.pixel.MascotMood
import dev.pocketagent.app.ui.theme.Corner
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.Space
import dev.pocketagent.app.ui.theme.Type
import dev.pocketagent.app.ui.theme.motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import dev.pocketagent.app.ui.text.t

// Pêt's note: the one small message at the bottom of the screen that replaces the platform toast. Any code
// calls PetNotes.show; one PetNoteHost (placed once by the shell) draws it.

enum class PetNoteKind { INFO, SUCCESS, ERROR }

/** One note: [hasAction] is true when it carries a labelled action. */
class PetNote(
    val id: Long,
    val text: String,
    val kind: PetNoteKind,
    val actionLabel: String?,
    val onAction: (() -> Unit)?,
    val postedAt: Long,
) {
    val hasAction: Boolean get() = actionLabel != null && onAction != null
}

/**
 * The rules of the note queue, pure (time comes in as [clock]). One note is visible; at most [MAX_WAITING] wait
 * (the oldest one without an action is dropped first); the same text and kind within [DUPLICATE_MS] of the
 * last one is ignored; the visible time is [displayMillis]; the timer is paused while an overlay (a sheet or
 * a dialog) is open; with no host attached nothing becomes visible, and a note older than [STALE_MS] when a
 * host attaches is dropped. Not thread-safe: PetNotes locks around it.
 */
class PetNoteQueue(private val clock: () -> Long) {
    private val waiting = ArrayList<PetNote>()
    private var nextId = 1L
    private var hostAttached = false
    private var overlays = 0
    private var elapsed = 0L
    private var lastTick = 0L
    private var lastText: String? = null
    private var lastKind: PetNoteKind = PetNoteKind.INFO
    private var lastAt = 0L

    /** The note on screen, or null. */
    var visible: PetNote? = null
        private set

    val waitingCount: Int get() = waiting.size

    /** Adds a note. Returns it, or null when it was ignored as a duplicate. */
    fun offer(text: String, kind: PetNoteKind, actionLabel: String? = null, onAction: (() -> Unit)? = null): PetNote? {
        val now = clock()
        if (lastText == text && lastKind == kind && now - lastAt in 0..DUPLICATE_MS) return null
        lastText = text
        lastKind = kind
        lastAt = now
        val note = PetNote(nextId++, text, kind, actionLabel, onAction, now)
        waiting.add(note)
        while (waiting.size > MAX_WAITING) {
            val drop = waiting.indexOfFirst { !it.hasAction }
            waiting.removeAt(if (drop >= 0) drop else 0)
        }
        promote()
        return note
    }

    /** A host was composed: drops notes older than [STALE_MS] and shows the first of the rest. */
    fun attach() {
        hostAttached = true
        val now = clock()
        waiting.removeAll { now - it.postedAt > STALE_MS }
        promote()
    }

    /** The host left: the visible note goes back to the front of the queue. */
    fun detach() {
        hostAttached = false
        visible?.let { waiting.add(0, it) }
        visible = null
    }

    /** Time passes: expires the visible note when its time is up (never while an overlay is open). */
    fun tick() {
        val now = clock()
        val current = visible
        if (current != null && overlays == 0) {
            elapsed += (now - lastTick).coerceAtLeast(0L)
            if (elapsed >= displayMillis(current.text, current.hasAction)) {
                visible = null
                promote()
                return
            }
        }
        lastTick = now
    }

    /** The visible note was dismissed (tap, or its action was used). */
    fun dismissVisible() {
        if (visible == null) return
        visible = null
        promote()
    }

    fun overlayOpened() {
        tick()
        overlays++
    }

    fun overlayClosed() {
        if (overlays > 0) overlays--
        lastTick = clock()
    }

    fun clear() {
        waiting.clear()
        visible = null
    }

    private fun promote() {
        if (visible != null || !hostAttached || waiting.isEmpty()) return
        visible = waiting.removeAt(0)
        elapsed = 0L
        lastTick = clock()
    }

    companion object {
        const val MAX_WAITING = 3
        const val DUPLICATE_MS = 1000L
        const val STALE_MS = 8000L

        /** How long a note stays: 6 seconds with an action, else 2.2 s plus 40 ms a character, between 2.5 and 5 s. */
        fun displayMillis(text: String, hasAction: Boolean): Int =
            if (hasAction) 6000 else (2200 + 40 * text.length).coerceIn(2500, 5000)
    }
}

object PetNotes {
    private val lock = Any()
    private val queue = PetNoteQueue { System.nanoTime() / 1_000_000L }
    private val state = MutableStateFlow<PetNote?>(null)

    /** The note on screen. The host reads it. */
    internal val visible: StateFlow<PetNote?> = state.asStateFlow()

    /** Shows a note at the bottom of the screen. Any thread; never throws; needs no Context. */
    fun show(text: String, kind: PetNoteKind = PetNoteKind.INFO, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        try {
            if (text.isBlank()) return
            locked { it.offer(text, kind, actionLabel, onAction) }
        } catch (_: Throwable) {
            // A note is a nicety: it must never break the caller.
        }
    }

    /** Removes every note, the visible one included. */
    fun clear() {
        try {
            locked { it.clear() }
        } catch (_: Throwable) {
        }
    }

    internal fun attachHost() = locked { it.attach() }
    internal fun detachHost() = locked { it.detach() }
    internal fun tick() = locked { it.tick() }
    internal fun dismissVisible() = locked { it.dismissVisible() }
    internal fun overlayOpened() = locked { it.overlayOpened() }
    internal fun overlayClosed() = locked { it.overlayClosed() }

    private fun locked(block: (PetNoteQueue) -> Unit) {
        synchronized(lock) {
            block(queue)
            state.value = queue.visible
        }
    }
}

/** While this is in the composition (a sheet or a dialog is open) the notes' timer stands still. */
@Composable
internal fun HoldPetNotesWhileOpen() {
    DisposableEffect(Unit) {
        PetNotes.overlayOpened()
        onDispose { PetNotes.overlayClosed() }
    }
}

private fun PetNoteKind.mood(): MascotMood = when (this) {
    PetNoteKind.INFO -> MascotMood.IDLE
    PetNoteKind.SUCCESS -> MascotMood.HAPPY
    PetNoteKind.ERROR -> MascotMood.ERROR
}

/**
 * Draws the current note. The shell places it once, last in the root box, aligned to the bottom centre above
 * the bottom bar. Until it is composed notes wait in the queue. A note pops in (pattern 2), leaves in a tap's
 * time, is read out politely by screen readers, and a tap dismisses it. Tags: petnote, petnote.action.
 */
@Composable
fun PetNoteHost(modifier: Modifier = Modifier) {
    val c = Pocket.colors
    val motion = Pocket.motion
    DisposableEffect(Unit) {
        PetNotes.attachHost()
        onDispose { PetNotes.detachHost() }
    }
    val note by PetNotes.visible.collectAsState()
    var shown by remember { mutableStateOf<PetNote?>(null) }
    val frac = remember { Animatable(0f) }
    LaunchedEffect(note?.id) {
        val n = note
        if (n != null) {
            if (shown?.id != n.id) {
                shown = n
                frac.snapTo(0f)
            }
            frac.animateTo(1f, motion.pop())
        } else if (shown != null) {
            frac.animateTo(0f, motion.tap())
            shown = null
        }
    }
    LaunchedEffect(note?.id) {
        while (note != null) {
            delay(TICK_MS)
            PetNotes.tick()
        }
    }
    val dismissLabel = t(R.string.design_note_dismiss)
    Box(modifier) {
        val n = shown
        if (n != null) {
            PixelPanel(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter, vertical = Space.s)
                    .graphicsLayer {
                        val k = frac.value
                        scaleX = POP_FROM + (1f - POP_FROM) * k
                        scaleY = POP_FROM + (1f - POP_FROM) * k
                        alpha = k
                    }
                    .testTag("petnote")
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .clickable(onClickLabel = dismissLabel, role = Role.Button) { PetNotes.dismissVisible() },
                color = c.surfaceHigh,
                border = when (n.kind) {
                    PetNoteKind.INFO -> c.outline
                    PetNoteKind.SUCCESS -> c.success
                    PetNoteKind.ERROR -> c.error
                },
                corner = Corner.card,
                variant = PanelVariant.FLOATING,
            ) {
                Row(
                    modifier = Modifier.padding(Space.m),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    Mascot(n.kind.mood(), size = 32.dp)
                    Spacer(Modifier.width(Space.s))
                    Text(
                        text = n.text, color = c.text, style = Type.body, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    val label = n.actionLabel
                    val action = n.onAction
                    if (label != null && action != null) {
                        Spacer(Modifier.width(Space.s))
                        PixelButton(
                            text = label,
                            onClick = { action(); PetNotes.dismissVisible() },
                            kind = ButtonKind.GHOST,
                            modifier = Modifier.testTag("petnote.action"),
                        )
                    }
                }
            }
        }
    }
}

private const val TICK_MS = 100L
private const val POP_FROM = 0.9f
