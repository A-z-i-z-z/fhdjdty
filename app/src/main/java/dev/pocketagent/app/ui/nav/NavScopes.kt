@file:OptIn(ExperimentalSharedTransitionApi::class)

package dev.pocketagent.app.ui.nav

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import dev.pocketagent.app.ui.NavEntry
import dev.pocketagent.app.ui.theme.AnimLevel
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.motion

/** The one shared transition scope around the screen area; null when shared elements are switched off. */
val LocalSharedTransitionScope: ProvidableCompositionLocal<SharedTransitionScope?> = staticCompositionLocalOf { null }

/** The animated scope of the screen being composed (each entry of the stack has its own); null outside the nav host. */
val LocalNavAnimatedScope: ProvidableCompositionLocal<AnimatedVisibilityScope?> = compositionLocalOf { null }

/** The stack entry whose content is being composed. */
val LocalNavEntry: ProvidableCompositionLocal<NavEntry?> = compositionLocalOf { null }

private data class SharedKey(val kind: String, val id: String)

/**
 * Keys for elements that stay the same thing across two screens. The screen that leaves and the screen that arrives
 * each put the same key on the element, inside their own composition (never inside a dialog or a sheet). A key may
 * appear once per screen.
 */
object SharedKeys {
    /** A chat row's avatar (chat list, home) and the avatar in the chat's top bar. */
    fun chatAvatar(convId: String): Any = SharedKey("chat.avatar", convId)

    /** A chat row's title and the title in the chat's top bar. */
    fun chatTitle(convId: String): Any = SharedKey("chat.title", convId)

    /** A bot card's avatar and the avatar in the editor's header. */
    fun botAvatar(botId: String): Any = SharedKey("bot.avatar", botId)

    /** A bot card as a whole and the editor's header (bounds). */
    fun botCard(botId: String): Any = SharedKey("bot.card", botId)
}

/**
 * Marks this element as one that travels between two screens (it keeps its content and moves and resizes from one
 * place to the other). Returns the modifier unchanged when there is no nav host around, when shared elements are
 * switched off, or when animations are off.
 */
@Composable
fun Modifier.navSharedElement(key: Any): Modifier {
    val scope = LocalSharedTransitionScope.current
    val visibility = LocalNavAnimatedScope.current
    val motion = Pocket.motion
    if (scope == null || visibility == null || !NavFlags.sharedElementsOn() || motion.level == AnimLevel.OFF) return this
    val self = this
    return with(scope) {
        self.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = visibility,
            boundsTransform = BoundsTransform { _, _ -> motion.glide<Rect>() },
        )
    }
}

/**
 * Like [navSharedElement], for a container whose content may differ between the two screens (a card that becomes a
 * header): the bounds travel and the content cross-fades. With [resize] true the content is laid out again at every
 * size on the way; by default it is scaled instead.
 */
@Composable
fun Modifier.navSharedBounds(key: Any, resize: Boolean = false): Modifier {
    val scope = LocalSharedTransitionScope.current
    val visibility = LocalNavAnimatedScope.current
    val motion = Pocket.motion
    if (scope == null || visibility == null || !NavFlags.sharedElementsOn() || motion.level == AnimLevel.OFF) return this
    val self = this
    return with(scope) {
        self.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = visibility,
            boundsTransform = BoundsTransform { _, _ -> motion.glide<Rect>() },
            resizeMode = if (resize) ResizeMode.RemeasureToBounds else ResizeMode.scaleToBounds(),
        )
    }
}
