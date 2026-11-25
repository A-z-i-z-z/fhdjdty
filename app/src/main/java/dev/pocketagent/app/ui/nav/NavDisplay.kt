@file:OptIn(ExperimentalSharedTransitionApi::class)

package dev.pocketagent.app.ui.nav

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import dev.pocketagent.app.BuildConfig
import dev.pocketagent.app.ui.NavEntry
import dev.pocketagent.app.ui.Navigator
import dev.pocketagent.app.ui.encode
import dev.pocketagent.app.ui.theme.AnimLevel
import dev.pocketagent.app.ui.theme.Dur
import dev.pocketagent.app.ui.theme.Ease
import dev.pocketagent.app.ui.theme.MotionScheme
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.Space
import dev.pocketagent.app.ui.theme.motion
import dev.pocketagent.app.util.AppLog

/**
 * The screen area of the shell: shows the top entry of [nav] and moves between entries (push, pop, tab switch).
 *
 * - Every entry's own state (what its screen keeps with rememberSaveable, a list's scroll position) is held in one
 *   SaveableStateHolder under the entry id. It is dropped when the entry has left the stack and its content has left
 *   the composition. The five tab roots have stable ids and are never dropped, so a tab comes back as it was left.
 * - One SharedTransitionLayout surrounds the area (unless the flag [NavFlags.sharedElements] is off) so that
 *   [navSharedElement] and [navSharedBounds] work in the entries.
 * - Back is not handled here. The back gesture arrives as a plain back press (experiment S1), and the pop motion plays
 *   once the stack has changed.
 */
@Composable
internal fun NavDisplay(
    nav: Navigator,
    modifier: Modifier = Modifier,
    content: @Composable (NavEntry) -> Unit,
) {
    val motion = Pocket.motion
    val sign = slideSign(LocalLayoutDirection.current == LayoutDirection.Rtl)
    val density = LocalDensity.current
    val holder = rememberSaveableStateHolder()
    val tracker = remember { MotionTracker() }
    val entries = nav.entries
    tracker.observe(entries.map { it.id })
    // Entries that left the stack without ever being composed again (several screens dropped at once by a tab switch,
    // a link or a reset) would keep their saved state for the life of the process: drop it here.
    val known = remember { mutableSetOf<Long>() }
    SideEffect {
        val now = entries.map { it.id }.toSet()
        for (gone in known) {
            if (gone !in now && !NavStack.isRootId(gone)) holder.removeState(gone)
        }
        known.clear()
        known.addAll(now)
    }
    val top = entries.last()
    val revision = NavFlags.revision.intValue
    val useShared = remember(revision) { NavFlags.sharedElements }

    if (BuildConfig.TEST_HOOKS) {
        LaunchedEffect(top.id) {
            val stack = nav.entries.joinToString(",") { "${it.id}|${it.route.encode()}" }
            AppLog.i("Nav", "top=${top.route.encode()} id=${top.id} stack=[$stack]")
        }
    }

    if (useShared) {
        SharedTransitionLayout(modifier = modifier) {
            NavHost(nav, top, tracker, motion, sign, density, holder, this, Modifier.fillMaxSize(), content)
        }
    } else {
        NavHost(nav, top, tracker, motion, sign, density, holder, null, modifier, content)
    }
}

/** Remembers the stack of the last composition so that the transition can tell a push from a pop. */
private class MotionTracker {
    private var lastIds: List<Long> = emptyList()

    /** What the latest change of the top entry was. Kept until the top changes again. */
    var kind: NavMotionKind = NavMotionKind.NONE
        private set

    /** Idempotent: calling it again with the same stack changes nothing. */
    fun observe(ids: List<Long>) {
        if (ids == lastIds) return
        val oldTop = lastIds.lastOrNull()
        val newTop = ids.lastOrNull()
        if (oldTop != null && newTop != null && oldTop != newTop) kind = navMotionKind(lastIds, ids)
        lastIds = ids
    }
}

@Composable
private fun NavHost(
    nav: Navigator,
    top: NavEntry,
    tracker: MotionTracker,
    motion: MotionScheme,
    sign: Int,
    density: Density,
    holder: SaveableStateHolder,
    sharedScope: SharedTransitionScope?,
    modifier: Modifier,
    content: @Composable (NavEntry) -> Unit,
) {
    AnimatedContent(
        targetState = top,
        modifier = modifier,
        transitionSpec = { navTransform(tracker.kind, motion, sign, density) },
        label = "nav",
        contentKey = { it.id },
    ) { entry ->
        val animatedScope: AnimatedVisibilityScope = this
        EntryHost(nav, entry, holder, sharedScope, animatedScope, content)
    }
}

/**
 * One entry's content with its saved state. The DisposableEffect comes BEFORE the SaveableStateProvider on purpose:
 * disposal runs in reverse order, so the provider has saved the state by the time the state is removed.
 */
@Composable
private fun EntryHost(
    nav: Navigator,
    entry: NavEntry,
    holder: SaveableStateHolder,
    sharedScope: SharedTransitionScope?,
    animatedScope: AnimatedVisibilityScope,
    content: @Composable (NavEntry) -> Unit,
) {
    val id = entry.id
    DisposableEffect(id) {
        onDispose {
            if (!NavStack.isRootId(id) && nav.entries.none { it.id == id }) holder.removeState(id)
        }
    }
    holder.SaveableStateProvider(id) {
        CompositionLocalProvider(
            LocalSharedTransitionScope provides sharedScope,
            LocalNavAnimatedScope provides animatedScope,
            LocalNavEntry provides entry,
        ) {
            content(entry)
        }
    }
}

/**
 * The motion of one change (design language, patterns 7 and 8). Push: the new screen slides in over a short distance
 * with a fade while the old one fades and moves a little the other way. Pop: the same in reverse, the leaving screen
 * on top. Tab switch: a cross-fade. REDUCED: fades only. OFF: nothing. [sign] mirrors the slide in a right-to-left layout.
 */
private fun navTransform(kind: NavMotionKind, motion: MotionScheme, sign: Int, density: Density): ContentTransform {
    val level = motion.level
    if (level == AnimLevel.OFF || kind == NavMotionKind.NONE) {
        return ContentTransform(EnterTransition.None, ExitTransition.None)
    }
    if (level == AnimLevel.REDUCED || kind == NavMotionKind.TAB) {
        return ContentTransform(fadeIn(motion.fast()), fadeOut(motion.fast()))
    }
    val far = with(density) { Space.xl.roundToPx() } * sign
    val near = with(density) { Space.s.roundToPx() } * sign
    val arrive = motion.custom<IntOffset>(Dur.BASE, Ease.enter)
    val depart = motion.custom<IntOffset>(Dur.BASE, Ease.standard)
    val fadeArrive = motion.custom<Float>(Dur.BASE, Ease.enter)
    return if (kind == NavMotionKind.PUSH) {
        ContentTransform(
            slideInHorizontally(arrive) { far } + fadeIn(fadeArrive),
            slideOutHorizontally(depart) { -near } + fadeOut(motion.fast()),
        )
    } else {
        ContentTransform(
            slideInHorizontally(arrive) { -near } + fadeIn(fadeArrive),
            slideOutHorizontally(depart) { far } + fadeOut(motion.fast()),
            targetContentZIndex = -1f,
        )
    }
}
