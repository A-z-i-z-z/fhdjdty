package dev.pocketagent.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.pocketagent.app.ui.pixel.Mascot
import dev.pocketagent.app.ui.pixel.MascotMood
import dev.pocketagent.app.ui.theme.Corner
import dev.pocketagent.app.ui.theme.Pocket
import dev.pocketagent.app.ui.theme.Space
import dev.pocketagent.app.ui.theme.Type

/*
 * The design gallery: every component, variant and state of this package on one scrolling screen, for the look
 * proof (dark and light, mirrored, double text size, the stretched language) and for the phone robot. It is
 * reached only through the debug link and only in builds with test hooks, so its words are plain English and
 * not part of the translated strings. Tags: gallery, and one button per overlay.
 *
 * Kind: a reference screen for developers (a scrolling list of specimens); it has no loading, empty or error
 * state of its own, because every specimen is built from fixed data.
 */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesignGallery() {
    val c = Pocket.colors
    var sheet by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(false) }
    var chipA by remember { mutableStateOf(true) }
    var chipB by remember { mutableStateOf(false) }
    var switchA by remember { mutableStateOf(true) }
    var switchB by remember { mutableStateOf(false) }
    var actions by remember { mutableIntStateOf(0) }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .testTag("gallery")
            .padding(bottom = Space.xxl),
    ) {
        PocketTopBar(title = "Design gallery", subtitle = "Components, variants, states", onBack = {}, ember = true)

        GalleryHeader("Panels")
        FlowRow(
            Modifier.padding(horizontal = Space.gutter),
            horizontalArrangement = Arrangement.spacedBy(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            PanelVariant.entries.forEach { v ->
                PixelPanel(Modifier.size(width = 104.dp, height = 64.dp), variant = v, corner = Corner.card) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(v.name.lowercase(), color = c.text, style = Type.label)
                    }
                }
            }
        }

        GalleryHeader("Buttons")
        FlowRow(
            Modifier.padding(horizontal = Space.gutter),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            PixelButton("Primary", onClick = {}, icon = PixelIcon.SEND)
            PixelButton("Secondary", onClick = {}, kind = ButtonKind.SECONDARY)
            PixelButton("Ghost", onClick = {}, kind = ButtonKind.GHOST)
            PixelButton("Danger", onClick = {}, kind = ButtonKind.DANGER, icon = PixelIcon.TRASH)
            PixelButton("Disabled", onClick = {}, enabled = false)
        }

        GalleryHeader("Icon buttons (back, send, play and share mirror)")
        Row(Modifier.padding(horizontal = Space.gutter)) {
            listOf(PixelIcon.BACK, PixelIcon.SEND, PixelIcon.PLAY, PixelIcon.SHARE, PixelIcon.GEAR, PixelIcon.TRASH).forEach {
                PixelIconButton(it, onClick = {}, contentDescription = it.name.lowercase())
            }
        }

        GalleryHeader("Chips and switches")
        Row(Modifier.padding(horizontal = Space.gutter), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            PocketChip("Selected", selected = chipA, onClick = { chipA = !chipA })
            PocketChip("Plain", selected = chipB, onClick = { chipB = !chipB }, icon = PixelIcon.STAR)
        }
        PocketSwitchRow("Switch on", switchA, { switchA = it }, subtitle = "With a subtitle")
        PocketSwitchRow("Switch off", switchB, { switchB = it })

        GalleryHeader("Typing, skeleton and the ember line")
        Row(
            Modifier.padding(horizontal = Space.gutter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            TypingIndicator()
            Skeleton(Modifier.width(160.dp), height = 24.dp)
        }
        Spacer(Modifier.height(Space.s))
        EmberLine(active = true, modifier = Modifier.padding(horizontal = Space.gutter))
        Spacer(Modifier.height(Space.s))
        EmberLine(active = false, modifier = Modifier.padding(horizontal = Space.gutter))

        GalleryHeader("Swipe row (drag it)")
        SwipeRow(
            modifier = Modifier.padding(horizontal = Space.gutter),
            startActions = listOf(SwipeAction(PixelIcon.PIN, "Pin", c.accent) { actions++ }),
            endActions = listOf(SwipeAction(PixelIcon.TRASH, "Delete", c.error, danger = true) { actions++ }),
        ) {
            PixelPanel(Modifier.fillMaxWidth(), color = c.surfaceHigh) {
                Text("Swiped $actions times", color = c.text, style = Type.body, modifier = Modifier.padding(Space.l))
            }
        }

        GalleryHeader("Pixel titles that do not fit step down")
        Column(Modifier.padding(horizontal = Space.gutter), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            listOf(300, 200, 140, 90).forEach { w ->
                Box(Modifier.width(w.dp).background(c.surface).padding(Space.xs)) {
                    PixelTitle("A rather long chat title")
                }
            }
        }

        GalleryHeader("Pixel font")
        Column(Modifier.padding(horizontal = Space.gutter), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            SPECIMEN.forEach { line -> PixelText(line, pixel = 1.5.dp) }
            PixelText("AVATAR Tango LT Yo. V. r, kerned", pixel = 2.dp, kerning = true)
            PixelText("AVATAR Tango LT Yo. V. r, plain", pixel = 2.dp)
        }

        GalleryHeader("Empty state")
        EmptyState(title = "Nothing here yet", body = "Pêt waits by the fire.")

        GalleryHeader("Notes, sheet and dialog")
        FlowRow(
            Modifier.padding(horizontal = Space.gutter),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            PixelButton("Sheet", onClick = { sheet = true }, modifier = Modifier.testTag("gallery.sheet"))
            PixelButton("Dialog", onClick = { dialog = true }, kind = ButtonKind.SECONDARY, modifier = Modifier.testTag("gallery.dialog"))
            PixelButton(
                "Note", onClick = { PetNotes.show("A small note from Pêt, in the corner of the screen.") },
                kind = ButtonKind.SECONDARY, modifier = Modifier.testTag("gallery.note"),
            )
            PixelButton(
                "Error note", onClick = { PetNotes.show("Something did not work.", PetNoteKind.ERROR) },
                kind = ButtonKind.SECONDARY, modifier = Modifier.testTag("gallery.note.error"),
            )
            PixelButton(
                "Note with action",
                onClick = { PetNotes.show("Saved.", PetNoteKind.SUCCESS, actionLabel = "Undo") { actions++ } },
                kind = ButtonKind.SECONDARY, modifier = Modifier.testTag("gallery.note.action"),
            )
        }

        GalleryHeader("Colours")
        Row(Modifier.padding(horizontal = Space.gutter), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            listOf(c.bg, c.bgDeep, c.surface, c.surfaceHigh, c.outline, c.accent, c.fireLow, c.fireMid, c.fireHigh, c.glow, c.scrim).forEach {
                Box(Modifier.size(24.dp).background(it))
            }
        }
        Row(Modifier.padding(horizontal = Space.gutter, vertical = Space.s)) {
            Mascot(MascotMood.IDLE, size = 64.dp)
            Mascot(MascotMood.HAPPY, size = 64.dp)
            Mascot(MascotMood.ERROR, size = 64.dp)
        }
    }

    if (sheet) {
        PocketSheet(onDismiss = { sheet = false }, title = "A sheet") {
            Text(
                "Drag the grabber down, tap the scrim, or press back.", color = c.text, style = Type.body,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = Space.s),
            )
            PixelButton("Close", onClick = { close() }, modifier = Modifier.padding(horizontal = 20.dp))
        }
    }
    if (dialog) {
        ConfirmDialog(
            title = "A dialog", body = "A confirm dialog with a dither scrim behind it.", confirmLabel = "Do it",
            onConfirm = { PetNotes.show("Done.", PetNoteKind.SUCCESS) }, onDismiss = { dialog = false },
            closeOnConfirm = true,
        )
    }
}

@Composable
private fun GalleryHeader(text: String) {
    SectionHeader(text)
}

private val SPECIMEN = listOf(
    "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
    "abcdefghijklmnopqrstuvwxyz",
    "0123456789 !\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}~",
    "ÇŞĞÜÖİ çşğüö ıİ ÊÎÛÉÈÀÂÔÙËÏ êîûéèàâôùëï",
    "ÆæŒœŸÿ «» €£°± ↑↓ →←×✓•·",
)
