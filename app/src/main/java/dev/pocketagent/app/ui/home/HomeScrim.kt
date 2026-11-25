package dev.pocketagent.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import dev.pocketagent.app.ui.pixel.hearthSkyTop
import dev.pocketagent.app.ui.theme.Pocket

/**
 * How opaque the status-bar scrim of the home list is: 0 while the night-sky scene (the first list item, [sceneSizePx] tall)
 * is at the very top, rising linearly to 1 when the scene's bottom edge has reached the bottom of the status bar, and 1 for
 * good once the scene is no longer the first visible item. [sceneSizePx] <= 0 (not measured yet) counts as "at the top".
 */
internal fun statusScrimAlpha(firstVisibleIndex: Int, firstVisibleOffsetPx: Int, sceneSizePx: Int, statusBarPx: Int): Float {
    if (firstVisibleIndex > 0) return 1f
    if (sceneSizePx <= 0) return 0f
    val span = (sceneSizePx - statusBarPx).coerceAtLeast(1)
    return (firstVisibleOffsetPx.toFloat() / span.toFloat()).coerceIn(0f, 1f)
}

/**
 * A box exactly as tall as the status bar, drawn over the home list, in the colour of the top of the night sky. Invisible while
 * the scene is on screen (the sky runs full-bleed behind the status bar), opaque once content scrolls up to the status bar so it
 * never collides with the clock and icons. The colour is dark in every theme, so the light status-bar icons that Home
 * forces (the sky is dark) stay readable and no icon switching is needed. Only the draw phase reads the scroll position.
 */
@Composable
internal fun BoxScope.HomeStatusScrim(listState: LazyListState) {
    val colors = Pocket.colors
    val statusPx = WindowInsets.statusBars.getTop(LocalDensity.current)
    val alpha by remember(listState, statusPx) {
        derivedStateOf {
            statusScrimAlpha(
                listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset,
                listState.layoutInfo.visibleItemsInfo.firstOrNull()?.takeIf { it.index == 0 }?.size ?: 0, statusPx,
            )
        }
    }
    val color = hearthSkyTop(colors)
    Box(
        Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth()
            .height(with(LocalDensity.current) { statusPx.toDp() })
            .drawBehind { val a = alpha; if (a > 0f) drawRect(color, alpha = a) },
    )
}
