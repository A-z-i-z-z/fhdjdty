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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pocketagent.app.R
import dev.pocketagent.app.ui.theme.Pocket

/**
 * Screen header: status-bar inset, optional back button, optional leading slot (e.g. an Avatar), title
 * (pixel font when Appearance.pixelFontTitles is on, one line with "..." if too long), optional subtitle,
 * and action buttons on the right. Sits on bgDeep with a 2 dp border line underneath.
 */
@Composable
fun PocketTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
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
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                PixelIconButton(PixelIcon.BACK, onBack, contentDescription = stringResource(R.string.design_back))
            } else {
                Spacer(Modifier.width(12.dp))
            }
            if (leading != null) {
                leading()
                Spacer(Modifier.width(8.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .semantics { heading() },
                verticalArrangement = Arrangement.Center,
            ) {
                if (pixelTitle) {
                    PixelText(
                        text = title, color = c.text, pixel = 2.dp, maxLines = 1,
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
            Spacer(Modifier.width(4.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(c.outline.copy(alpha = 0.8f)),
        )
    }
}
