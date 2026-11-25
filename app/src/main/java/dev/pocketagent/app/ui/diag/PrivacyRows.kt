package dev.pocketagent.app.ui.diag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.ui.components.PocketSwitchRow
import dev.pocketagent.app.ui.settings.SubHeading
import dev.pocketagent.app.ui.theme.Space
import dev.pocketagent.app.ui.text.t

/**
 * Settings > Behaviour, last block: what leaves the phone when the agent looks something up on the web. One switch,
 * [dev.pocketagent.app.runtime.GuestPrefs.webKeyless] (default on, as before). Turning it off rewrites Hermes's configuration at once;
 * Hermes reads it again by itself, so nothing restarts.
 *
 * Kind: a part of a dashboard-kind section (Settings > Behaviour). States: empty, loading, error and offline do not exist here (the
 * switch is local and works offline; the subtitle says what leaves the phone); there is no permission to ask for.
 */
@Composable
fun WebLookupsRow() {
    val ctx = LocalContext.current
    val app = remember { PocketApp.from(ctx) }
    val prefs = app.guestPrefs
    val on by prefs.webKeyless.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        SubHeading(t(R.string.cfg_priv_title))
        PocketSwitchRow(
            title = t(R.string.cfg_priv_web_title),
            checked = on,
            onCheckedChange = { v ->
                prefs.setWebKeyless(v)
                app.runtime.onGuestOptionsChanged(false)
            },
            subtitle = t(R.string.cfg_priv_web_sub),
            modifier = Modifier.testTag("privacy.webLookups"),
        )
    }
}
