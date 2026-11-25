package dev.pocketagent.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.R
import dev.pocketagent.app.runtime.GuestPrefs
import dev.pocketagent.app.ui.RobotTags
import dev.pocketagent.app.ui.components.PixelPanel
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.Space
import kotlinx.coroutines.delay
import dev.pocketagent.app.ui.text.t
import dev.pocketagent.app.ui.text.tn

/** How often the minutes left are read again. */
private const val MINUTES_REFRESH_MS = 30_000L

/**
 * A small bar that says the practice model is on (replies are scripted, nothing is spent). Draws nothing while the switch is off.
 * On a build that is not the twin the practice model switches itself off after two hours, so the bar also shows the minutes left.
 * NAV places it once over every screen (top, below the status bar); it takes no touches.
 */
@Composable
fun PracticeBanner(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val prefs = remember { GuestPrefs.get(ctx) }
    val on by prefs.practiceModel.collectAsState()
    if (!on) return
    val minutesLeft by produceState(prefs.practiceMinutesLeft(), prefs) {
        while (true) {
            value = prefs.practiceMinutesLeft()
            delay(MINUTES_REFRESH_MS)
        }
    }
    val c = Pocket.colors
    PixelPanel(modifier.padding(horizontal = Space.l).testTag(RobotTags.PRACTICE_BANNER), color = c.surface, border = c.warning) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.m, vertical = Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
            CText(t(R.string.rbt_practice_on), color = c.text, size = 12.sp, weight = FontWeight.SemiBold)
            minutesLeft?.let { left ->
                CText(tn(R.plurals.rbt_practice_left, left, left), color = c.textMuted, size = 11.sp)
            }
        }
    }
}
