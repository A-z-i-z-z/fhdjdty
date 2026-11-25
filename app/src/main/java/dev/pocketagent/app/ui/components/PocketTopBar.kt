package dev.pocketagent.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pocketagent.app.R
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.Space

/**
 * Screen header: status-bar inset, optional back button, optional leading slot (e.g. an Avatar), title
 * (pixel font when Appearance.pixelFontTitles is on, one line, stepping down in size before it is ever
 * cut), optional subtitle, and action buttons on the right. Sits on bgDeep with a 2 dp border line
 * underneath. With [ember] a thin line of fire burns on that border line (the assistant is working). Tags:
 * top.back, top.title.
 */
@Composable
fun PocketTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    ember: Boolean = false,
) {
    val c = Pocket.colors
    val pixelTitle = Pocket.appearance.pixelFontTitles
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.bgDeep)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                PixelIconButton(
                    PixelIcon.BACK, onBack,
                    modifier = Modifier.testTag("top.back"),
                    contentDescription = stringResource(R.string.design_back),
                )
            } else {
                Spacer(Modifier.width(Space.m))
            }
            if (leading != null) {
                leading()
                Spacer(Modifier.width(Space.s))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Space.xs, vertical = 6.dp)
                    .testTag("top.title")
                    .semantics { heading() },
                verticalArrangement = Arrangement.Center,
            ) {
                if (pixelTitle) {
                    PixelTitle(
                        text = title, color = c.text,
                        shadow = if (c.isDark) Color(0x99000000) else null,
                    )
                } else {
                    Text(
                        text = title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            actions()
            Spacer(Modifier.width(Space.xs))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(Space.xxs)
                .background(c.outline.copy(alpha = 0.8f)),
        ) {
            if (ember) EmberLine(active = true, modifier = Modifier.wrapContentHeight(align = Alignment.Bottom, unbounded = true))
        }
    }
}
