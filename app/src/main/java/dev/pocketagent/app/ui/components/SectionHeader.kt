package dev.pocketagent.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.pocketagent.app.ui.theme.Pocket
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max

/**
 * Small-caps section title: an ember dot, the label in the pixel font (upper-cased in the current
 * locale, so Turkish gets its dotted capital I), then a dotted rule filling the rest of the row.
 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    val c = Pocket.colors
    // The app language, not the phone's: "Settings" must not become "SETTİNGS" on a Turkish phone set to English.
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val label = remember(text, locale) { text.uppercase(locale) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(width = 6.dp, height = 8.dp)) {
            val u = max(1f, floor(size.width / 3f))
            val x0 = (size.width - 3 * u) / 2f
            val y0 = (size.height - 4 * u) / 2f
            drawRect(c.fireHigh, Offset(x0 + u, y0), Size(u, u))
            drawRect(c.fireMid, Offset(x0, y0 + u), Size(3 * u, 2 * u))
            drawRect(c.fireLow, Offset(x0 + u, y0 + 3 * u), Size(u, u))
        }
        Spacer(Modifier.width(8.dp))
        PixelText(text = label, color = c.accentSoft, pixel = 1.5.dp, maxLines = 1)
        Spacer(Modifier.width(10.dp))
        Spacer(
            Modifier
                .weight(1f)
                .height(2.dp)
                .drawWithCache {
                    val p = max(1f, Math.round(2.dp.toPx()).toFloat())
                    val dash = PathEffect.dashPathEffect(floatArrayOf(p, p))
                    onDrawBehind {
                        drawLine(
                            color = c.outline, start = Offset(0f, size.height / 2f),
                            end = Offset(size.width, size.height / 2f), strokeWidth = size.height, pathEffect = dash,
                        )
                    }
                },
        )
    }
}
