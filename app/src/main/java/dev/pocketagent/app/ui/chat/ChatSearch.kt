package dev.pocketagent.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.R
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconButton
import dev.pocketagent.app.ui.theme.Pocket

// "Find in chat" (agent CHAT): the slim search bar under the chat's top bar. The matching itself is in ChatLogic.kt, the scrolling
// and the accent outline of the current match are in ChatScreen.

/**
 * @param position 0-based index of the current match, -1 when there is none.
 * Arrow up goes to the previous (older) match, arrow down to the next (newer) one; the keyboard's search key goes up.
 */
@Composable
internal fun ChatSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchCount: Int,
    position: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Pocket.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val hint = stringResource(R.string.ui_find_hint)
    Row(
        modifier.fillMaxWidth().background(c.surface).padding(start = 12.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.weight(1f).heightIn(min = 40.dp).background(c.bgDeep).border(2.dp, c.accent)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) Text(hint, color = c.textMuted, fontSize = 15.sp, maxLines = 1)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 15.sp),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onPrev() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = hint },
            )
        }
        Spacer(Modifier.width(6.dp))
        val countText = when {
            query.isBlank() -> ""
            matchCount == 0 -> stringResource(R.string.ui_find_none)
            else -> stringResource(R.string.ui_find_count, position + 1, matchCount)
        }
        if (countText.isNotEmpty()) {
            Text(
                countText,
                color = if (matchCount == 0) c.error else c.textMuted,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        PixelIconButton(
            PixelIcon.UP, onClick = onPrev, contentDescription = stringResource(R.string.ui_find_prev),
            tint = if (matchCount > 0) c.text else c.outline, enabled = matchCount > 0,
        )
        PixelIconButton(
            PixelIcon.DOWN, onClick = onNext, contentDescription = stringResource(R.string.ui_find_next),
            tint = if (matchCount > 0) c.text else c.outline, enabled = matchCount > 0,
        )
        PixelIconButton(PixelIcon.CLOSE, onClick = onClose, contentDescription = stringResource(R.string.ui_find_close))
    }
}
