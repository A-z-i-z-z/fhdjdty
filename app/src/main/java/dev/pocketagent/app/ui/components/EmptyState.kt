package dev.pocketagent.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.pocketagent.app.ui.pixel.Mascot
import dev.pocketagent.app.ui.pixel.MascotMood
import dev.pocketagent.app.ui.theme.Pocket

/**
 * Friendly placeholder for empty lists: Pêt the newt (or a spark icon when the mascot is switched off
 * in Appearance), a title, a body text and an optional action (usually a PixelButton).
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val c = Pocket.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (Pocket.appearance.showMascot) {
            Mascot(MascotMood.IDLE, size = 96.dp)
        } else {
            PixelIconView(PixelIcon.SPARK, tint = c.fireMid, size = 48.dp)
        }
        Spacer(Modifier.height(16.dp))
        if (Pocket.appearance.pixelFontTitles) {
            PixelText(
                text = title,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
                color = c.text,
                pixel = 2.dp,
                center = true,
            )
        } else {
            Text(
                text = title, color = c.text, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = body, color = c.textMuted, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth(),
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}
