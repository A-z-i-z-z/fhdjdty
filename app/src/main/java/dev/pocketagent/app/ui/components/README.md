# Design system: how to use it (screen authors)

The rules behind these components are in `docs/design-language.md` (binding). This file is the quick reference.

Everything reads colours from `Pocket.colors` (a `PocketColors`), so it follows theme, dark/light and
accent. Never hard-code colours. The app root wraps everything once:

```kotlin
val ap by AppearanceStore.get(ctx).appearance.collectAsState()
PocketTheme(ap) { /* nav host */ }          // paints bg, sets Material scheme, ripple -> pixel press, provides the motion
```

## Rules of thumb
- **Body text** = Material `Text` (system font, `Type.body`, `color = Pocket.colors.text`). Code and tool output:
  `Type.mono` on `colors.codeBg`. Other roles: `Type.title`, `Type.reading` (long answers), `Type.label`.
- **Titles / labels** = `PixelText` or, for a one-line title that must never be cut, `PixelTitle`.
- Do **not** multiply font sizes by `textScale`: `PocketTheme` already scales every sp (and the user's system font scale).
- `colors.accent` is a fill (use `colors.onAccent` on it). `colors.accentSoft` is the accent as text/icon colour.
  `colors.glow` is firelight for ambient effects, `colors.scrim` the colour behind sheets and dialogs.
- **Tokens, not numbers.** Gaps and paddings are `Space.xxs/xs/s/m/l/xl/xxl`, `Space.gutter` (screen margin) and
  `Space.touch` (48 dp). Corners are `Corner.chip/card/sheet` (steps of the design pixel). New code uses these names;
  a number is never changed to fit a token, only exact equals are replaced.
- **Motion has names.** Read `val motion = Pocket.motion` in the composable body (never inside a lambda) and ask for
  `motion.tap() fast() base() slow() scene() glide() snappy() bouncy() pop()`, `motion.custom(Dur.X)` or
  `motion.loop(Loop.X)`. No raw `tween(`. The scheme already knows the effective level (the app setting, lowered by the
  system animation scale and battery saver), so do not branch on it to pick a spec; use `motion.loops`, `motion.particles`
  where something must not run at all. `Pocket.anim` is that effective level too; `Pocket.appearance.animations` is only
  the raw setting.
- **Touch has names.** `val haptics = rememberHaptics()` then `haptics.play(HapticCue.TICK | CLICK | SEND | RISE | DOUBLE
  | HOLD | STOKE | PULSE)`. Never call `performHapticFeedback` yourself.
- **No toasts.** `PetNotes.show("Copied", PetNoteKind.SUCCESS)` (any thread); errors use `PetNoteKind.ERROR`.
- Touch targets are >= 48 dp everywhere; give icon-only buttons a `contentDescription` (without one the spoken name of the
  icon is used).
- Icons come in 12 px sprites: draw them at 24 dp (default), 36 or 48 dp so pixels stay crisp. In a right-to-left layout
  back, send, play and share are drawn mirrored by `PixelIconView`.

## Components
```kotlin
PocketTopBar("Chats", onBack = { nav.back() }, subtitle = "3 bots", ember = working,   // ember: the thin line of fire
    leading = { Avatar(AvatarId.NEWT, color, size = 32.dp) },
    actions = { PixelIconButton(PixelIcon.SEARCH, { }, contentDescription = "Search") })

PixelPanel(Modifier.fillMaxWidth()) { Column(Modifier.padding(Space.l)) { Text("Hello") } }
PixelPanel(variant = PanelVariant.INSET) { ... }       // FLAT, RAISED (default), INSET, GLOWING, FLOATING
PixelPanel(color = Pocket.colors.surfaceHigh, border = Pocket.colors.accent, corner = Corner.sheet) { ... }

PixelButton("Send", onClick = { }, icon = PixelIcon.SEND)                       // PRIMARY: ember glow; every kind sinks when pressed
PixelButton("Cancel", onClick = { }, kind = ButtonKind.SECONDARY)               // GHOST, DANGER too
PixelIconButton(PixelIcon.TRASH, onClick = { }, contentDescription = "Delete", tint = Pocket.colors.error)

PocketTextField(value, { value = it }, Modifier.fillMaxWidth(), label = "Name", singleLine = true)
PocketTextField(prompt, { prompt = it }, label = "System prompt", minLines = 4, monospace = true)
PocketTextField(key, { key = it }, label = "API key", password = true, singleLine = true)

Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {                    // chips: 36 dp visible, 48 dp touch
    PocketChip("Low", selected = effort == "low", onClick = { effort = "low" })
}
SectionHeader("Appearance")                                                      // ember dot + pixel label + dotted rule
PocketSwitchRow("Animations", checked, { checked = it }, subtitle = "Fire, embers, hopping dots")

TypingIndicator()                                                                // three hopping embers (put in a bot bubble)
EmptyState("No chats yet", "Start one with Hermes.", action = { PixelButton("New chat", { }) })
Skeleton(Modifier.fillMaxWidth(), height = 16.dp)                                // dither-shimmer placeholder, no spinners
EmberLine(active = working)                                                      // 2u line of fire; keeps its height when idle
SwipeRow(startActions = listOf(SwipeAction(PixelIcon.PIN, "Pin", color) { pin() })) { Row() }   // also custom a11y actions
```

### Dialogs and sheets (one of each)
```kotlin
if (show) ConfirmDialog("Delete chat?", "This cannot be undone.", "Delete", onConfirm = { delete() },
    onDismiss = { show = false }, danger = true, closeOnConfirm = true)            // tags dialog.confirm, dialog.cancel

if (show) PocketDialog("Rename", onDismiss = { show = false },
    dismiss = { PixelButton("Cancel", { show = false }, kind = ButtonKind.GHOST) },
    confirm = { PixelButton("Save", { save() }) }) { PocketTextField(...) }

if (show) PocketSheet(onDismiss = { show = false }, title = "Attach") {          // content is NOT scrolled for you
    Row(Modifier.clickable { close { pick() } }) { ... }                         // close(then): slides away, onDismiss, then
}
if (show) ActionSheet("Chat", listOf(SheetAction(PixelIcon.PIN, "Pin") { pin() }), onDismiss = { show = false })
```
Sheets and dialogs sit over the dither scrim (pattern 6); a sheet drags by its grabber and title, flings by velocity, and
closes on the scrim, back or the grabber's "close" action. While one is open, notes wait.

Every window of its own goes through `PocketWindow` (never call `Dialog`, `Popup`, `AlertDialog`, `ModalBottomSheet` or
`DropdownMenu` directly): a Compose dialog is a separate window that gets the PHONE's context, configuration and
resources again, so `PocketWindow` hands the app's own (language, font scale, layout direction) back inside. A sheet's
place is plain data (`SheetSlide`, pure and unit-tested) and is a fraction of its own height, so it is visible from the
first frame with animations off and never depends on a measurement; with animations on it glides in from one height down.
The motion's effect is keyed only by the count of started motions. If frames stop while it runs (no frame for 1.5 s), it is
finished by force and `Sheet` writes a warning to the app log; a sheet that pops in after about 1.5 s instead of gliding
therefore means the frame clock of the window is the real cause.

## PixelText and PixelTitle
`PixelText("Newroz piroz be", color = ..., pixel = 2.dp)` draws the bitmap font: 5x7 capitals on a 12-row grid (the box is
12 font pixels high: room for accents above and descenders below, so it centres on the capitals). It wraps like `Text`
(`maxLines = 1` gives a "..." ellipsis and tells the robot's cut check), `center = true` centres lines in the width it gets,
`shadow = Color(0x99000000)` adds a 1-pixel drop shadow. `pixel = PixelType.small` (1.5 dp) is a good small size (section
labels), `PixelType.title` (2 dp) for titles, `PixelType.big` (3 dp) for big headings. The glyph pixel follows the text-size
setting in three steps (`followTextScale = false` to pin it); at the default text size nothing changes. `kerning = true`
pulls the listed pairs one column closer (off by default). Covers A-Z a-z 0-9, ASCII punctuation, Turkish / Kurdish /
French letters, ligatures, guillemets and a few symbols; other characters fall back to the system font.
`PixelTitle(text)` is the one-line title: it tries the scaled size, the plain size and three quarters of it, and only then
falls back to the system font, so it is never cut while half the bar is free.

## Other pieces
- `PixelShape(corner, pixel)` stepped-corner `Shape` for `clip` / `background`; `PixelShapes.chip/card/sheet`.
- `PixelIcon.<NAME>.spriteName` = `icon_<name>`; `PixelIconView(icon, tint = ..., size = 24.dp)`.
- Press feedback: pressable pixel surfaces sink one design pixel and spring back (pattern 1, `rememberPixelPress`);
  `clickable { }` elsewhere inside `PocketTheme` shows a flat warm pixel tint instead of a ripple.
- `DesignGallery()` shows everything of this package on one screen; it is reachable only through the debug link in builds
  with test hooks.
- Status/navigation bars are coloured by `PocketTheme` (bgDeep). `PocketTopBar` pads for the status bar; add
  `Modifier.navigationBarsPadding()` to your bottom bar if the root is edge-to-edge.
