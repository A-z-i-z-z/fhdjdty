# Design system: how to use it (screen authors)

Everything reads colours from `Pocket.colors` (a `PocketColors`), so it follows theme, dark/light and
accent. Never hard-code colours. The app root wraps everything once:

```kotlin
val ap by AppearanceStore.get(ctx).appearance.collectAsState()
PocketTheme(ap) { /* nav host */ }          // paints bg, sets Material scheme, ripple -> pixel press
```

## Rules of thumb
- **Body text** = Material `Text` (system font, `MaterialTheme.typography.bodyLarge`, `color = Pocket.colors.text`).
  `Text` without `color` falls back to `colors.text` already. Code/tool output: `FontFamily.Monospace` on `colors.codeBg`
  (plain `colors.text` is readable on `codeBg`).
- **Titles / labels** = `PixelText` (see below). Respect `Pocket.appearance.pixelFontTitles` if you build your own title.
- Do **not** multiply font sizes by `textScale`: `PocketTheme` already scales every sp (and the user's system font scale).
- `colors.accent` is a fill (use `colors.onAccent` on it). `colors.accentSoft` is the accent as text/icon colour on `bg`/`surface`.
- Animations: read `Pocket.anim` (`FULL`, `REDUCED`, `OFF`); `OFF` means no motion at all.
- Touch targets are >= 48 dp everywhere; give icon-only buttons a `contentDescription`.
- Icons come in 12 px sprites: draw them at 24 dp (default), 36 or 48 dp so pixels stay crisp.

## Components
```kotlin
PocketTopBar("Chats", onBack = { nav.back() }, subtitle = "3 bots",
    leading = { Avatar(AvatarId.NEWT, color, size = 32.dp) },
    actions = { PixelIconButton(PixelIcon.SEARCH, { }, contentDescription = "Search") })

PixelPanel(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("Hello") } }
PixelPanel(color = Pocket.colors.surfaceHigh, border = Pocket.colors.accent, corner = 3) { ... }  // highlight = false: flat

PixelButton("Send", onClick = { }, icon = PixelIcon.SEND)                       // PRIMARY: ember glow
PixelButton("Cancel", onClick = { }, kind = ButtonKind.SECONDARY)               // GHOST, DANGER too
PixelIconButton(PixelIcon.TRASH, onClick = { }, contentDescription = "Delete", tint = Pocket.colors.error)

PocketTextField(value, { value = it }, Modifier.fillMaxWidth(), label = "Name", singleLine = true)
PocketTextField(prompt, { prompt = it }, label = "System prompt", minLines = 4, monospace = true)
PocketTextField(key, { key = it }, label = "API key", password = true, singleLine = true)

Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {                       // chips: 36 dp visible, 48 dp touch
    PocketChip("Low", selected = effort == "low", onClick = { effort = "low" })
}
SectionHeader("Appearance")                                                      // ember dot + pixel label + dotted rule
PocketSwitchRow("Animations", checked, { checked = it }, subtitle = "Fire, embers, hopping dots")

TypingIndicator()                                                                // three hopping embers (put in a bot bubble)
EmptyState("No chats yet", "Start one with Hermes.", action = { PixelButton("New chat", { }) })

if (show) PocketDialog("Delete chat?", onDismiss = { show = false },
    dismiss = { PixelButton("Cancel", { show = false }, kind = ButtonKind.GHOST) },
    confirm = { PixelButton("Delete", { /* ... */ }, kind = ButtonKind.DANGER) }) { Text("This cannot be undone.") }
```

## PixelText
`PixelText("Newroz piroz be", color = ..., pixel = 2.dp)` draws the bitmap font: 5x7 capitals on a 12-row grid (the box is
12 font pixels high: room for accents above and descenders below, so it centres on the capitals). It wraps like `Text`
(`maxLines = 1` gives a "..." ellipsis), `center = true` centres lines in the width it gets, `shadow = Color(0x99000000)`
adds a 1-pixel drop shadow. `pixel = 1.5.dp` is a good small size (section labels), 2.dp for titles, 3.dp for big headings.
Covers A-Z a-z 0-9, ASCII punctuation, Turkish / Kurdish / French letters; other characters fall back to the system font.

## Other pieces
- `PixelShape(corner = 2, pixel = 2.dp)` stepped-corner `Shape` for `clip` / `background`.
- `PixelIcon.<NAME>.spriteName` = `icon_<name>`; `PixelIconView(icon, tint = ..., size = 24.dp)`.
- Press feedback: `clickable { }` anywhere inside `PocketTheme` shows a flat warm pixel tint instead of a ripple.
- Status/navigation bars are coloured by `PocketTheme` (bgDeep). `PocketTopBar` pads for the status bar; add
  `Modifier.navigationBarsPadding()` to your bottom bar if the root is edge-to-edge.
