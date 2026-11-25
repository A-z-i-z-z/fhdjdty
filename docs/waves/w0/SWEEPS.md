# Wave 0: recipes for the two mechanical agents, SWEEP and WORDS (draft for `docs/waves/w0-contract.md`)

Written 2026-11-25 against the tree at commit `344829f`. Every line number, count and quoted line below comes from a command run for this
draft; line numbers are only a hint, because DESIGN, STREAM, NAV, CONFIG and ROBOT edit files before these two agents run. **Find a site by
its quoted text, never by its line number.** Package of everything is `dev.pocketagent.app`; `APP` below means
`app/src/main/java/dev/pocketagent/app`. Plan features: 0.5 and 0.6 (SWEEP), 0.11 (WORDS), plus the testTags of 0.3.

Order of the whole wave: DESIGN, STREAM, NAV, STAGE, CONFIG, ROBOT, TEXT (parallel) -> integration compile -> **SWEEP** (batches S1 to S6 in
parallel) -> integration compile -> **WORDS** (batches W1 to W10 in parallel) -> integration compile -> lead runs Part 3.

## 0. Rules for both agents

1. You are a small model. You do not design, you do not decide, you do not improve. Each rule below is an exact search text and an exact
   replacement. If a site does not match its rule exactly, **leave it and write it in your report** (format in 0.4).
2. Only edit the files of your batch (1.1 for SWEEP, 2.1 for WORDS). One file belongs to exactly one batch. SWEEP never touches `ui/theme/`,
   `ui/text/`, `ui/nav/`, `ui/pixel/`, `ui/components/`, `runtime/`, `voice/` or `app/src/test`. WORDS never touches `ui/text/`, `ui/theme/` (no site there: its six `getString` are
   SharedPreferences), `ui/nav/`, `ui/pixel/`; it touches `runtime/RuntimeManager.kt` and `voice/VoiceEngine.kt` only in batch W10b, `ui/components/` only in batch W4, and
   `app/src/test` only the test classes of 2.8.
3. No Gradle, no adb, no git. You may use ripgrep (`rg --no-config`) and read files.
4. Behaviour never changes. Layout structure never changes (no new `Box`/`Column`, no moved children). Texts never change. Number values never change
   (a `10.dp` stays `10.dp`; only a literal that is exactly equal to a token is replaced by that token).
5. Never reformat, never reorder imports, never remove an unused import except the ones a rule names, never touch `@OptIn(...)` lines.
6. When you add an import, add it once, after the last existing import line, in the form given. Check first with `rg -n "^import <x>$" <file>`.
7. Kotlin comments: never write a slash followed by a star inside a comment (Kotlin nests block comments, see `AGENTS.md`).

### 0.1 Preconditions (run first; if one fails, STOP and report which one)

SWEEP needs these (made by DESIGN, ROBOT, STREAM):
```
rg -n "fun PocketSheet\("            APP/ui/components/PocketSheet.kt
rg -n "fun ActionSheet\(|class SheetAction\("   APP/ui/components/PocketSheet.kt
rg -n "fun ConfirmDialog\("           APP/ui/components/ConfirmDialog.kt
rg -n "object PetNotes|enum class PetNoteKind"  APP/ui/components/PetNote.kt
rg -n "object PixelShapes"            APP/ui/components/PixelShapes.kt
rg -n "object Space|object Corner|object PixelType"  APP/ui/theme/Tokens.kt
rg -n "object Dur|object Loop|object Flourish|class MotionScheme|val Pocket.motion"  APP/ui/theme/Motion.kt
rg -n "enum class HapticCue|fun rememberHaptics"  APP/ui/theme/Haptics.kt
rg -n "object RobotTags"              APP/ui/RobotTags.kt
```
WORDS needs these (made by TEXT, SWEEP):
```
rg -n "fun t\(|fun tn\(|object Texts"   APP/ui/text
rg -c "<string name=\"eng_" app/src/main/res/values/strings_engine.xml      # about 56 keys (more with the label keys)
rg -n "fun ConfirmDialog\(" APP/ui/components/ConfirmDialog.kt           # SWEEP has run
```
Inside one SWEEP batch do the recipes in this order: R1, R2, R3, R4, R5, R7, then R6 last (R7 quotes the code as it was at `344829f`; R6 changes `dp` literals afterwards).
In the markdown tables of this file `\|` is only the markdown escape for a pipe; in a command type `|`.

### 0.2 Import forms (exact; both agents)

| Symbol | Import line |
|---|---|
| `Space`, `Corner`, `PixelType`, `Flourish`, `Loop`, `Dur`, `HapticCue`, `rememberHaptics` | `import dev.pocketagent.app.ui.theme.<Name>` |
| `Pocket.motion` | `import dev.pocketagent.app.ui.theme.motion` |
| `PocketSheet`, `ActionSheet`, `SheetAction`, `ConfirmDialog`, `PetNotes`, `PetNoteKind`, `PixelShapes` | `import dev.pocketagent.app.ui.components.<Name>` |
| `RobotTags` | `import dev.pocketagent.app.ui.RobotTags` |
| `testTag` | `import androidx.compose.ui.platform.testTag` |
| `t`, `tn`, `Texts` | `import dev.pocketagent.app.ui.text.t` / `.tn` / `.Texts` |
| `R` (in `data/`, `hermes/`, `service/`, `routines/`) | `import dev.pocketagent.app.R` |

### 0.3 Never changes (both)

`ui/text/*` (the function itself calls `stringResource`; the lint exempts it), every `values*/*.xml`, every `.pxl`, manifest, Gradle files, scenario
files, anything in `docs/`. String ids passed around as `Int` (`TabSpec(.., R.string.ui_nav_home)`, `HermesTab(R.string.ctl_tab_soul, ..)`,
the `when` tables that return `R.string.x`, `EFFORT_CHIPS`, `ROUTINE_TEMPLATES`): they stay `R.string.x`; only the place that finally shows them
changes (WORDS). `sp` values, colours, `fontSize`, `Modifier.weight`, `0.dp`. `PocketDialog(` calls (signature unchanged). `RenameDialog`.

### 0.4 Report format (each agent returns one block per file)

```
FILE ui/chat/ChatInput.kt  EDITED 14  LEFT 3
LEFT line-text-quoted | reason (no token | not exact | no context | unknown shape)
```

---

# PART 1. SWEEP

Runs after DESIGN and STREAM, in six parallel batches. Files NOT in any batch below are never edited by SWEEP (this includes `ChatScreen.kt`,
`ChatMessages.kt`, `Markdown*.kt`, STREAM's new files except the one line of S6, `AppRoot.kt`, `Nav.kt`, `RuntimeSection.kt`, the privacy row of
`BehaviourSection.kt`).

## 1.1 Batches (no file in two batches; sizes counted at `344829f`: literal `N.dp` / stringResource / total edits expected)

| Batch | Files | What is in it |
|---|---|---|
| **S1 chat sheets** | `ui/chat/ChatCommon.kt`, `AttachmentUi.kt`, `NewChatSheet.kt`, `SkillPickerSheet.kt`, `ChatQuickBar.kt` | delete private `ConfirmDialog`, `SheetAction`, `ActionSheet`; 4 sheets to `PocketSheet`; copy toast; ember loop; literals of AttachmentUi and ChatQuickBar |
| **S2 chat list and input** | `ui/chat/ChatListScreen.kt`, `ChatInput.kt`, `SlashPalette.kt`, `ChatSearch.kt`, `StarredScreen.kt` (plus `ChatLogic.kt`, `SyntaxHighlight.kt`: read only, expected 0 edits) | import changes for the moved dialog and sheet, one ConfirmDialog call, haptics, tweens, tags `chat.*`, literals of ChatListScreen, ChatInput, SlashPalette |
| **S3 home and bots** | `ui/home/HomeScreen.kt`, `HomeParts.kt`, `ui/bots/BotsScreen.kt`, `BotShareUi.kt`, `BotEditorScreen.kt` | toast, haptics, tweens, 3 ConfirmDialog sites, 3 toast kinds, tags, literals of HomeScreen, HomeParts, BotEditorScreen |
| **S4 hermes and routines** | `ui/hermes/HermesScreen.kt`, `HermesTabs.kt`, `HermesDocs.kt`, `TerminalScreen.kt` (not `ConsoleAnsi.kt`, `HermesEditorsViewModel.kt`), `ui/routines/RoutineEditorScreen.kt`, `RoutinesScreen.kt`, `RoutineUiParts.kt` | delete private ConfirmDialog, 5 ConfirmDialog sites, `showToast` body, 1 toast kind, tags, literals of RoutinesScreen |
| **S5 settings** | `ui/settings/AppearanceSection.kt`, `BackupSection.kt`, `BehaviourSection.kt` (NOT the privacy row), `CtlUi.kt`, `ProviderSection.kt`, `SettingsScreen.kt`, `UsageSection.kt`, `VoiceSoundSection.kt` (**not** `RuntimeSection.kt`: ROBOT owns it) | delete `ConfirmDialog` of CtlUi, `toast` body, 3 ConfirmDialog sites, tags, literals of AppearanceSection |
| **S6 chat state, one line** | `ui/chat/ChatScreenState.kt` only, and only the body of `say` (STREAM's file; STREAM draft section 7 allows exactly this) | see 1.2 last row |

## 1.2 Recipe R1: toasts (12 sites in the survey; SWEEP owns 4, plus 2 wrapper bodies)

New API (DESIGN section 3.7): `PetNotes.show(text: String, kind: PetNoteKind = PetNoteKind.INFO, actionLabel: String? = null, onAction: (() -> Unit)? = null)`, never throws,
needs no Context. `enum class PetNoteKind { INFO, SUCCESS, ERROR }`. Add the two imports of 0.2 and delete `import android.widget.Toast` when the file has no other `Toast`.
Write text arguments with `ctx.getString(...)` (WORDS turns them into `Texts.get` later). Do not use `stringResource` in a lambda.

| File (batch) | Before (quoted, line at 344829f) | After |
|---|---|---|
| `ui/home/HomeScreen.kt` (S3), line 274 | `Toast.makeText(ctx, R.string.ui_send_not_accepted, Toast.LENGTH_SHORT).show()` | `PetNotes.show(ctx.getString(R.string.ui_send_not_accepted), PetNoteKind.ERROR)` |
| `ui/chat/ChatCommon.kt` (S1), line 241 (in `copyToClipboard`) | `if (Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, ctx.getString(R.string.ui_chat_copied), Toast.LENGTH_SHORT).show()` | `if (Build.VERSION.SDK_INT < 33) PetNotes.show(ctx.getString(R.string.ui_chat_copied), PetNoteKind.SUCCESS)` |
| `ui/routines/RoutineUiParts.kt` (S4), lines 119-121 | `internal fun showToast(ctx: Context, message: String) {` / `    try { Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show() } catch (_: Exception) { }` / `}` | `@Suppress("UNUSED_PARAMETER")` line above, then `internal fun showToast(ctx: Context, message: String, kind: PetNoteKind = PetNoteKind.INFO) {` / `    PetNotes.show(message, kind)` / `}` |
| `ui/settings/CtlUi.kt` (S5), lines 204-206 | `internal fun toast(ctx: Context, message: String) {` / `    try { Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show() } catch (_: Exception) {}` / `}` | `@Suppress("UNUSED_PARAMETER")` line above, then `internal fun toast(ctx: Context, message: String, kind: PetNoteKind = PetNoteKind.INFO) {` / `    PetNotes.show(message, kind)` / `}` |
| `ui/chat/ChatScreenState.kt` (S6), `fun say(text: String)` | the body is a single `Toast.makeText(<context>, text, Toast.LENGTH_SHORT).show()` | `PetNotes.show(text)`; remove `import android.widget.Toast` if unused. **Only if** `rg -n "Toast\.makeText" ui/chat/ChatScreenState.kt` finds exactly that one line; if STREAM already converted it, do nothing and say so |

The 15 callers of `toast(` and `showToast(` stay as they are, **except these four, which get the ERROR kind as a last argument** (find by the quoted text):

| File | Quoted call | After |
|---|---|---|
| `ui/bots/BotShareUi.kt` (S3) line 116 | `} catch (_: Exception) { toast(ctx, failedMsg) }` (the one after `toast(ctx, savedMsg)`) | `toast(ctx, failedMsg, PetNoteKind.ERROR)` |
| same, line 126 | `text.length > MAX_SHARE_CHARS -> toast(ctx, tooBigMsg)` | `toast(ctx, tooBigMsg, PetNoteKind.ERROR)` |
| same, line 130 | `} catch (_: Exception) { toast(ctx, failedMsg) }` (the one after `ctx.startActivity(Intent.createChooser`) | `toast(ctx, failedMsg, PetNoteKind.ERROR)` |
| `ui/hermes/HermesTabs.kt` (S4) line 120 | `else toast(ctx, ctx.getString(R.string.ctl_skill_delete_failed, err))` | `else toast(ctx, ctx.getString(R.string.ctl_skill_delete_failed, err), PetNoteKind.ERROR)` |

Add `import dev.pocketagent.app.ui.components.PetNoteKind` to those two files. Not SWEEP's: `AppRoot.kt:296` (NAV), `ChatScreen.kt` lines 300, 382, 395, 454, 525, 967, 1014 (STREAM). After S1 to S6: `rg -n "Toast\.makeText" APP/ui` must find nothing at all.

## 1.3 Recipe R2: the one confirm dialog (3 private copies deleted)

New `ConfirmDialog` (package `ui.components`): `ConfirmDialog(title: String, body: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = false, closeOnConfirm: Boolean = false, dismissLabel: String? = null)`.
The only differences from the three old ones: the argument is named `body` (the settings and routines copies called it `text`), and `closeOnConfirm = true` reproduces the chat copy (which called `onDismiss()` after `onConfirm()`) while the default `false` reproduces the other two (the caller closes the dialog).

**Delete** (the whole function with its `@Composable` line; keep everything around it):
1. `ui/chat/ChatCommon.kt` (S1): `internal fun ConfirmDialog(title: String, body: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = true)` (about lines 313-327). Keep `RenameDialog` right below it.
2. `ui/settings/CtlUi.kt` (S5): `internal fun ConfirmDialog(title: String, text: String, confirmLabel: String, ...danger: Boolean = false)` (about lines 149-161).
3. `ui/routines/RoutineEditorScreen.kt` (S4): `private fun ConfirmDialog(...)` (about lines 409-424; its body ends with `RText(text)`).

**Calls** (every call below: rename the argument `text =` to `body =` unless the row says otherwise, and add `import dev.pocketagent.app.ui.components.ConfirmDialog`; where the file already has `import dev.pocketagent.app.ui.settings.ConfirmDialog`, **replace** that line instead of adding):

| File (batch) | Call (quoted start) | Edit |
|---|---|---|
| `ui/chat/ChatListScreen.kt` (S2) line 239 | `        ConfirmDialog(` / `            title = stringResource(R.string.ui_dlg_delete_title), body = ...` | already `body =`; **add** `closeOnConfirm = true, danger = true,` as new arguments after `confirmLabel = ...,` (the deleted copy defaulted to `danger = true` and closed itself); add the import; also add `import dev.pocketagent.app.ui.components.ActionSheet` and `import dev.pocketagent.app.ui.components.SheetAction` (used at lines 221-229 without import today) |
| `ui/bots/BotShareUi.kt` (S3) line 211 | `ConfirmDialog(` / `title = stringResource(R.string.ctl_bots_import_title, list.size),` / `text = stringResource(R.string.ctl_bots_import_body, names),` | `text =` to `body =`; replace import at line 34 |
| `ui/bots/BotEditorScreen.kt` (S3) lines 384 and 389 | `if (showDiscard) ConfirmDialog(` and `if (showDelete && existing != null) ConfirmDialog(` | `text =` to `body =` in both; replace import at line 67 |
| `ui/hermes/HermesTabs.kt` (S4) line 110 | `ConfirmDialog(` / `title = stringResource(R.string.ctl_skill_delete_title, s.name), text = stringResource(R.string.ctl_skill_delete_body),` | `text =` to `body =`; replace import at line 53 |
| `ui/hermes/HermesDocs.kt` (S4) line 196 | `if (confirmSave) ConfirmDialog(` | `text =` to `body =`; replace import at line 40 |
| `ui/hermes/HermesScreen.kt` (S4) line 164 | `if (confirmLeave) ConfirmDialog(` | `text =` to `body =`; replace import at line 45 |
| `ui/routines/RoutineEditorScreen.kt` (S4) lines 327 and 332 | `if (showDiscard) ConfirmDialog(` and `if (showDelete && existing != null) ConfirmDialog(` | `text =` to `body =` in both (the second has `text = stringResource(R.string.rtn_delete_body),` on its own line); add the import |
| `ui/settings/BackupSection.kt` (S5) line 197 | `ConfirmDialog(` / `title = stringResource(R.string.ctl_bak_restore_confirm_title),` / `text = stringResource(` (three lines, `R.string.ctl_bak_restore_confirm_body, made, ...` inside) | `text =` to `body =`; add the import (same package today, no import) |
| `ui/settings/UsageSection.kt` (S5) line 197 | `if (confirmClear) ConfirmDialog(` | `text =` to `body =`; add the import |
| `ui/settings/VoiceSoundSection.kt` (S5) line 285 | `        ConfirmDialog(` / `title = stringResource(R.string.ctl_voice_delete_title),` / `text = stringResource(R.string.ctl_voice_delete_body),` | `text =` to `body =`; add the import |

**Not SWEEP's, needs the same two edits by ROBOT** (REQUEST, section 4): `ui/settings/RuntimeSection.kt` line 115 `if (confirmReset) ConfirmDialog(` ... `text = stringResource(R.string.ctl_rt_reset_body),`: `text =` to `body =` and `import dev.pocketagent.app.ui.components.ConfirmDialog`. An explicit import wins over the same-package function, so ROBOT can do it before or after S5 deletes the old one. STREAM does its own two calls in `ChatScreen.kt` (lines 988, 996).

Example (real, `BotEditorScreen.kt:384`):
```kotlin
// before
    if (showDiscard) ConfirmDialog(
        title = stringResource(R.string.ctl_discard_title), text = stringResource(R.string.ctl_discard_body),
        confirmLabel = stringResource(R.string.ctl_discard), danger = true,
        onConfirm = { showDiscard = false; nav.back() }, onDismiss = { showDiscard = false },
    )
// after
    if (showDiscard) ConfirmDialog(
        title = stringResource(R.string.ctl_discard_title), body = stringResource(R.string.ctl_discard_body),
        confirmLabel = stringResource(R.string.ctl_discard), danger = true,
        onConfirm = { showDiscard = false; nav.back() }, onDismiss = { showDiscard = false },
    )
```

## 1.4 Recipe R3: the one sheet (`ActionSheet`, `SheetAction` deleted; 4 `ModalBottomSheet` to `PocketSheet`)

`PocketSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier, title: String? = null, maxHeightFraction: Float = 0.92f, content: @Composable PocketSheetScope.() -> Unit)`. The scope is a `ColumnScope`, so rows can be written directly. The sheet pads itself for the navigation bar and keyboard, draws its own grabber, shape and scrim.

**Delete in `ui/chat/ChatCommon.kt` (S1):** `internal class SheetAction(...)` (line 274) and the whole `internal fun ActionSheet(title: String?, actions: List<SheetAction>, onDismiss: () -> Unit)` with its `@OptIn(ExperimentalMaterial3Api::class)`/`@Composable` lines (lines 276-311), and the comment line `/** A bottom sheet with a list of actions: easy to reach with one hand. */`. Then remove from that file only these imports **if `rg -c` shows no other use in the file**: `androidx.compose.material3.ModalBottomSheet`, `androidx.compose.material3.rememberModalBottomSheetState`, `androidx.compose.material3.BottomSheetDefaults`. Keep `bottomSafeInsets()` (used by `AppRoot.kt`). Callers `ActionSheet(...)`/`SheetAction(...)` in `ChatListScreen.kt` get the two imports (see R2). STREAM's `ChatScreen*.kt` already import them.

**Convert the four `ModalBottomSheet(` calls.** In each: replace the whole `ModalBottomSheet( ... ) {` header (arguments `onDismissRequest`, `sheetState`, `containerColor`, `contentColor`, `shape`, `dragHandle`) by the `PocketSheet(` header given, delete the `.windowInsetsPadding(bottomSafeInsets())` line or segment of the inner `Column` modifier, leave everything else (including every `onDismiss()` call, every inner `verticalScroll`, `heightIn`, `LazyColumn`) untouched. Then delete the imports `ModalBottomSheet`, `rememberModalBottomSheetState`, `BottomSheetDefaults` and (if no longer used in the file) `windowInsetsPadding` and `RoundedCornerShape`.

| File (batch), quoted start | New header | Inner change |
|---|---|---|
| `ui/chat/AttachmentUi.kt` (S1) `internal fun AttachSheet(` line 63; `ModalBottomSheet(` line 70 | `PocketSheet(onDismiss = onDismiss, title = stringResource(R.string.ui_attach_title)) {` | `Column(Modifier.fillMaxWidth().windowInsetsPadding(bottomSafeInsets()).padding(bottom = 14.dp)) {` becomes `Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {`; delete the `Text(stringResource(R.string.ui_attach_title), color = c.textMuted, fontSize = 13.sp, ...)` heading (3 lines; it is now `title`); keep the three `AttachRow(...)` lines; delete the now unused `val c = Pocket.colors` of `AttachSheet` |
| `ui/chat/NewChatSheet.kt` (S1) line 71 | `PocketSheet(onDismiss = onDismiss) {` | `Column(Modifier.fillMaxWidth().windowInsetsPadding(bottomSafeInsets()).padding(horizontal = 16.dp).padding(bottom = 12.dp)) {` becomes `Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {` (heading stays: it is a 20 sp bold line, not a small title) |
| `ui/chat/SkillPickerSheet.kt` (S1) line 108 | `PocketSheet(onDismiss = onDismiss) {` | in the inner `Column(` modifier delete the line `.windowInsetsPadding(bottomSafeInsets())` |
| `ui/chat/ChatQuickBar.kt` (S1) `ChatControlsSheet`, line 187 | `PocketSheet(onDismiss = onDismiss) {` | in the inner `Column(` modifier delete the line `.windowInsetsPadding(bottomSafeInsets())`; keep `.heightIn(max = 560.dp)` and `.verticalScroll(rememberScrollState())` |

Example (real, `AttachmentUi.kt:62-87`):
```kotlin
// before
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttachSheet(onCamera: () -> Unit, onGallery: () -> Unit, onDocument: () -> Unit, onDismiss: () -> Unit) {
    val c = Pocket.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface, contentColor = c.text,
        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.textMuted) },
    ) {
        Column(Modifier.fillMaxWidth().windowInsetsPadding(bottomSafeInsets()).padding(bottom = 14.dp)) {
            Text(stringResource(R.string.ui_attach_title), color = c.textMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            AttachRow(PixelIcon.CAMERA, stringResource(R.string.ui_attach_camera)) { onDismiss(); onCamera() }
            AttachRow(PixelIcon.IMAGE, stringResource(R.string.ui_attach_gallery)) { onDismiss(); onGallery() }
            AttachRow(PixelIcon.FILE, stringResource(R.string.ui_attach_doc)) { onDismiss(); onDocument() }
        }
    }
}
// after
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttachSheet(onCamera: () -> Unit, onGallery: () -> Unit, onDocument: () -> Unit, onDismiss: () -> Unit) {
    PocketSheet(onDismiss = onDismiss, title = stringResource(R.string.ui_attach_title)) {
        Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
            AttachRow(PixelIcon.CAMERA, stringResource(R.string.ui_attach_camera)) { onDismiss(); onCamera() }
            AttachRow(PixelIcon.IMAGE, stringResource(R.string.ui_attach_gallery)) { onDismiss(); onGallery() }
            AttachRow(PixelIcon.FILE, stringResource(R.string.ui_attach_doc)) { onDismiss(); onDocument() }
        }
    }
}
```
(The real declaration is written over several lines; keep its layout, change only what the table says.)

## 1.5 Recipe R4: haptics (5 call sites in SWEEP's files; 3 more are STREAM's)

New: `HapticCue { TICK, CLICK, SEND, RISE, DOUBLE, HOLD, STOKE, PULSE }`, `@Composable fun rememberHaptics(): HapticPlayer` with `fun play(cue: HapticCue)`. In each file: replace the line `val haptic = LocalHapticFeedback.current` by `val haptics = rememberHaptics()`; replace every use as below; delete `import androidx.compose.ui.hapticfeedback.HapticFeedbackType` and `import androidx.compose.ui.platform.LocalHapticFeedback` (nothing else in these files uses them: checked with `rg -n "HapticFeedbackType|LocalHapticFeedback"`).

| File (batch) | Before | After |
|---|---|---|
| `ui/chat/ChatListScreen.kt` (S2) line 265 and 277 (in `ConversationRow`) | `val haptic = LocalHapticFeedback.current` ... `onLongClick = onLongClick?.let { l -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); l() } },` | `val haptics = rememberHaptics()` ... `onLongClick = onLongClick?.let { l -> { haptics.play(HapticCue.HOLD); l() } },` |
| `ui/home/HomeScreen.kt` (S3) line 96 | `val haptic = LocalHapticFeedback.current` | `val haptics = rememberHaptics()` |
| same, line 174 (overdrive, 7 quick taps) | `haptic.performHapticFeedback(HapticFeedbackType.LongPress)` | `haptics.play(HapticCue.HOLD)` |
| same, line 177 (a normal tap on the fire) | `haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)` | `haptics.play(HapticCue.STOKE)` |
| same, line 264 (quick-talk send button) | `haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)` | `haptics.play(HapticCue.TICK)` |

## 1.6 Recipe R5: durations and easing in SWEEP's files (8 sites)

`val motion = Pocket.motion` goes next to the existing `val c = Pocket.colors` of the same composable (add `import dev.pocketagent.app.ui.theme.motion`; also `Flourish`/`Loop` where used). A `motion` value must be read in the composable body, never inside a lambda or `LaunchedEffect`; the lambda then uses the captured value. DESIGN table 3.10: 90 to 110 ms `tap()`, up to 170 `fast()`, 180 to 250 `base()`, 400 and 520 `scene()`. If the compiler cannot infer the type, write `motion.fast<Float>()`.

| File (batch), line | Before | After |
|---|---|---|
| `ui/home/HomeScreen.kt` (S3) 184 (inside `scope.launch { stoke.snapTo(1.2f) ... }` in an `onClick`) | `stoke.animateTo(0f, tween(1200))` | `stoke.animateTo(0f, motion.custom<Float>(Flourish.STOKE))` |
| same 198 | `enter = fadeIn(tween(150)),` | `enter = fadeIn(motion.fast()),` |
| same 199 | `exit = fadeOut(tween(250)),` | `exit = fadeOut(motion.base()),` |
| `ui/home/HomeParts.kt` (S3) 68 (`PixelProgressBar`) | `val smooth by animateFloatAsState(target, if (anim == AnimLevel.OFF) snap<Float>() else tween<Float>(400), label = "progress")` | `val smooth by animateFloatAsState(target, motion.scene<Float>(), label = "progress")` (keep `val anim`, it is still used at line 79) |
| same 70 | `val slide = t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "slide")` | `val slide = t.animateFloat(0f, 1f, motion.loop<Float>(Loop.SLIDE, RepeatMode.Reverse, LinearEasing), label = "slide")` |
| `ui/chat/ChatInput.kt` (S2) 101 (`SparkBurst`, inside `LaunchedEffect(trigger)`) | `progress.animateTo(1f, tween(520, easing = LinearOutSlowInEasing))` | `progress.animateTo(1f, motion.scene<Float>())` |
| `ui/chat/ChatCommon.kt` (S1) 263 (`GeneratingEmber`) | `animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "alpha",` | `animationSpec = motion.loop<Float>(Loop.EMBER, RepeatMode.Reverse), label = "alpha",` |
| `ui/chat/SlashPalette.kt` (S2) 162 (`NewrozCelebrationOverlay`, inside `LaunchedEffect(trigger)`) | `progress.animateTo(1f, tween(2400, easing = LinearEasing))` | `progress.animateTo(1f, motion.custom<Float>(Flourish.SHOWER, LinearEasing))` |

After the edit remove an import (`tween`, `snap`, `infiniteRepeatable`, `LinearOutSlowInEasing`) only when `rg -c` shows no other use in the file. **Left alone and reported** (a hold or poll time, no token exists): `ChatListScreen.kt:96 delay(250)`, `HomeScreen.kt:122 delay(1900)`, `:123 delay(2400)`, `:124 delay(4200)`, `RoutinesScreen.kt:78 delay(5_000)`. Never touch `AnimatedVisibility(...)` without a spec, `Pocket.anim == AnimLevel.X` branches, or `animateItem`.

## 1.7 Recipe R6: literal values in the ten files with the most of them

Chosen by the lint's own counts (tween + `RoundedCornerShape` + literal duration + `N.dp`), SWEEP-owned files only (STREAM's `ChatMessages.kt` 88, `ChatScreen.kt` 35 and `MarkdownView.kt` 29 are excluded; their owner migrates them):

| # | File (batch) | `N.dp` total | of those equal to a token | other |
|---|---|---:|---:|---|
| 1 | `ui/chat/ChatQuickBar.kt` (S1) | 45 | 24 | 4 `RoundedCornerShape(16.dp)` |
| 2 | `ui/chat/AttachmentUi.kt` (S1) | 45 | 13 | 2 `RoundedCornerShape(4.dp)` (the sheet shape is deleted by R3) |
| 3 | `ui/home/HomeScreen.kt` (S3) | 36 | 25 | tweens (R5) |
| 4 | `ui/settings/AppearanceSection.kt` (S5) | 39 | 21 | 2 `RoundedCornerShape(14.dp)`, `corner = 1` at line 163 |
| 5 | `ui/chat/ChatInput.kt` (S2) | 36 | 30 | `RoundedCornerShape(16.dp)` line 174, `(4.dp)` line 302 |
| 6 | `ui/chat/ChatListScreen.kt` (S2) | 36 | 23 | |
| 7 | `ui/bots/BotEditorScreen.kt` (S3) | 36 | 21 | |
| 8 | `ui/routines/RoutinesScreen.kt` (S4) | 27 | 10 | |
| 9 | `ui/chat/SlashPalette.kt` (S2) | 23 | 14 | |
| 10 | `ui/home/HomeParts.kt` (S3) | 21 | 6 | `pixel = 3.dp` line 55 |

**Only these ten files get rules R6.a to R6.d. Every other SWEEP file gets none of them.**

**R6.a dp to Space.** Replace a literal only if it is exactly one of the values below **and** it is inside one of the five allowed forms. Anything else stays and is reported as `LEFT dp <value> (form)`.

| Literal | Token | |
|---|---|---|
| `2.dp` | `Space.xxs` | |
| `4.dp` | `Space.xs` | |
| `8.dp` | `Space.s` | |
| `12.dp` | `Space.m` | |
| `16.dp` | `Space.l`, but `Space.gutter` when it is the value of `horizontal =`, `start =` or `end =` | |
| `24.dp` | `Space.xl` | |
| `32.dp` | `Space.xxl` | |
| `48.dp` | `Space.touch`, only in form 5 | |

Allowed forms: (1) any argument of `padding(`, `PaddingValues(`, `contentPadding =`; (2) the argument of `Arrangement.spacedBy(`; (3) the argument of `width(`, `height(` or `size(` **only when the call is the modifier of a `Spacer(`**; (4) an argument of `offset(`; (5) `min =` of `heightIn(` / `widthIn(`, `minWidth =` / `minHeight =` of `defaultMinSize(` / `sizeIn(`. Never replaced: `size(`/`width(`/`height(` of anything but a `Spacer`, `border(` widths, `pixel = ` arguments (R6.c), `1.dp`, `1.5.dp`, `3.dp`, `5.dp`, `6.dp`, `10.dp`, `14.dp` and every other number, `0.dp`, a value inside a string or comment, a value in a `when`/`if` branch of a `border(if (on) 3.dp else 1.dp, ...)`.
Add `import dev.pocketagent.app.ui.theme.Space` once per file.

Real examples (each from the tree):
```kotlin
// HomeScreen.kt:154-155
contentPadding = PaddingValues(bottom = 24.dp),            ->  PaddingValues(bottom = Space.xl)
verticalArrangement = Arrangement.spacedBy(16.dp),         ->  Arrangement.spacedBy(Space.l)
// HomeScreen.kt:217   (10.dp stays: no token)
Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp))
   ->  Column(Modifier.padding(horizontal = Space.gutter), verticalArrangement = Arrangement.spacedBy(10.dp))
// HomeScreen.kt:258
Spacer(Modifier.width(4.dp))                                ->  Spacer(Modifier.width(Space.xs))
// HomeScreen.kt:200   (all sides: Space.m)
.statusBarsPadding().padding(12.dp)                         ->  .statusBarsPadding().padding(Space.m)
// ChatInput.kt:197   (both are 8: Space.s)
.padding(horizontal = 8.dp, vertical = 8.dp)                ->  .padding(horizontal = Space.s, vertical = Space.s)
// ChatInput.kt:206   (Spacer)
Spacer(Modifier.width(2.dp))                                ->  Spacer(Modifier.width(Space.xxs))
// ChatListScreen.kt:279   (10.dp stays)
.padding(horizontal = 16.dp, vertical = 10.dp)              ->  .padding(horizontal = Space.gutter, vertical = 10.dp)
// ChatListScreen.kt:282   NOT replaced: 48.dp is a size argument, not form 5
AvatarStack(members, 48.dp)
// HomeScreen.kt:190   NOT replaced: no token
Modifier.fillMaxWidth().height(280.dp)
// AppearanceSection.kt:172 and 174   NOT replaced: size of a Box, border width, no token
.size(54.dp)   .border(if (on) 2.dp else 1.dp, ...)
```

**R6.b `RoundedCornerShape(` to a stepped shape** (design language 2.1: stepped corners everywhere). **LEAD DECISION D1** (a visible change; if the lead says no, skip this whole block and report every site as LEFT). Only these sites, find by quoted text:

| File | Quoted | After |
|---|---|---|
| `ChatQuickBar.kt` (4 sites) | `.clip(RoundedCornerShape(16.dp))` and `.border(<w>, <colour>, RoundedCornerShape(16.dp))` | `.clip(PixelShapes.card)` and `.border(<w>, <colour>, PixelShapes.card)` |
| `ChatInput.kt` line 174 | `Modifier.clip(RoundedCornerShape(16.dp)).background(c.surfaceHigh)` | `Modifier.clip(PixelShapes.card).background(c.surfaceHigh)` |
| `ChatInput.kt` line 302 | `.clip(RoundedCornerShape(4.dp)).background(c.codeBg).padding(8.dp)` | `.clip(PixelShapes.chip).background(c.codeBg).padding(Space.s)` |
| `AttachmentUi.kt` lines 176, 178 | `.clip(RoundedCornerShape(4.dp))` and `.border(1.dp, c.outline, RoundedCornerShape(4.dp))` | `.clip(PixelShapes.chip)` and `.border(1.dp, c.outline, PixelShapes.chip)` |
| `SkillPickerSheet.kt` lines 188, 199 (S1, not one of the ten, but the same rule because the sheet conversion touches it) | `.clip(RoundedCornerShape(4.dp))` | `.clip(PixelShapes.chip)` |
| `AppearanceSection.kt` lines 173, 174 | `.clip(RoundedCornerShape(14.dp))` and `.border(if (on) 2.dp else 1.dp, if (on) c.accent else c.outline, RoundedCornerShape(14.dp))` | `.clip(PixelShapes.card)` and `.border(if (on) 2.dp else 1.dp, if (on) c.accent else c.outline, PixelShapes.card)` |

Delete `import androidx.compose.foundation.shape.RoundedCornerShape` only when no use is left in the file. Any other `RoundedCornerShape(` in SWEEP's files is left and reported.

**R6.c named pixel and corner arguments.** `pixel = 3.dp` to `pixel = PixelType.big` (only `HomeParts.kt:55`); `corner = 1` to `corner = Corner.chip` (only `AppearanceSection.kt:163` and `SkillPickerSheet.kt:172`). Add the import of the token. Nothing else.

**R6.d durations** are done in R5; there is no other literal duration in the ten files.

## 1.8 Recipe R7: test tags (names from ROBOT's `RobotTags`; the literal text where ROBOT has no constant)

Add `Modifier.testTag(...)` to an existing modifier chain (put it **last** in the chain, after `weight`, `fillMaxWidth`, `padding`). Where a composable has no `modifier` parameter, do not add one: report it. Imports: `testTag` and `RobotTags` (0.2).

| File (batch), composable | Control (quoted) | Tag |
|---|---|---|
| `ui/home/HomeScreen.kt` (S3) `StatusCard` | `Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)` | `Text(title, modifier = Modifier.testTag(RobotTags.HOME_STATE), color = ...)` (add the named `modifier` argument; do not reorder the others) |
| same, `StatusCard` | the `PixelButton(stringResource(R.string.ui_home_btn_start), onClick = onStart, modifier = Modifier.fillMaxWidth(), ...)` | `modifier = Modifier.fillMaxWidth().testTag(RobotTags.HOME_START)` |
| `ui/chat/ChatListScreen.kt` (S2) | both `PixelButton(stringResource(R.string.ui_chats_new), ...)` (empty state, line 158, and the floating one, line 209) | add `modifier = Modifier.testTag(RobotTags.CHATS_NEW)` to the first; second becomes `modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag(RobotTags.CHATS_NEW)` |
| same, the three `items(...)` blocks that call `ConversationRow(` (lines 179, 184, 190) | `items(pinned, key = { it.id }) { cv ->` / `items(rest, key = { it.id }) { cv ->` / `items(matchingConvs, key = { "c-" + it.id }) { cv ->` | change to `itemsIndexed(pinned, key = { _, cv -> cv.id }) { i, cv ->` with `modifier = Modifier.testTag(RobotTags.chatsRow(i))` added to the `ConversationRow(` call (named argument, after the positional ones, before `draft =`); the `rest` block uses `RobotTags.chatsRow(pinned.size + i)` (`pinned` is declared just above in the same block; when the pinned block is empty `pinned.size` is 0); the search block uses `chatsRow(i)`. Add `import androidx.compose.foundation.lazy.itemsIndexed` |
| `ui/chat/ChatInput.kt` (S2) `ChatInputBar` | the `PocketTextField(value = text, onValueChange = onTextChange, modifier = Modifier.weight(1f), placeholder = ...` | `modifier = Modifier.weight(1f).testTag(RobotTags.CHAT_INPUT)` |
| same | `PixelIconButton(PixelIcon.SEND, onClick = onSend, contentDescription = ...` | add `modifier = Modifier.testTag(RobotTags.CHAT_SEND)` |
| same | `PixelIconButton(PixelIcon.STOP, onClick = onStop, contentDescription = stringResource(R.string.ui_chat_stop), tint = c.error)` | add `modifier = Modifier.testTag(RobotTags.CHAT_STOP)` |
| same | `PixelIconButton(PixelIcon.CLIP, onClick = onAttachClick, ...)` | `modifier = Modifier.testTag("chat.attach")` (no constant in `RobotTags`; STREAM draft section 9) |
| same | the mic `PixelIconButton(if (listening) PixelIcon.WAVE else PixelIcon.MIC, ...)` | `modifier = Modifier.testTag("chat.mic")` |
| same, `ApprovalCard` | `PixelButton(approvalLabel(ch), onClick = { onChoice(ch) }, modifier = Modifier.weight(1f), kind = ...)` | `modifier = Modifier.weight(1f).testTag("chat.approval.$ch")` (equals `RobotTags.CHAT_APPROVAL_ONCE`, `_SESSION`, `_ALWAYS`, `_DENY` for the four known choices) |
| `ui/hermes/HermesScreen.kt` (S4) | `PocketChip(label, selected = tab == t.ordinal, onClick = { tab = t.ordinal }, icon = t.icon)` | add `modifier = Modifier.testTag(hermesTabTag(t))` with, as a new private function at the end of the file, `private fun hermesTabTag(tab: HermesTab): String = when (tab) { HermesTab.SOUL -> RobotTags.HERMES_TAB_SOUL; HermesTab.MEMORY -> RobotTags.HERMES_TAB_MEMORY; HermesTab.USER -> "hermes.tab.user"; HermesTab.SKILLS -> RobotTags.HERMES_TAB_SKILLS; HermesTab.CONFIG -> "hermes.tab.config"; HermesTab.LOGS -> "hermes.tab.logs" }` (the call site is written `hermesTabTag(t)`, and W7 renames that `t` to `ht`) |
| `ui/hermes/HermesDocs.kt` (S4) `DocEditor` | the raw `PocketTextField(state.text, { state.text = it; state.justSaved = false }, Modifier.fillMaxWidth(), minLines = 12, ...` | second positional argument stays; third becomes `Modifier.fillMaxWidth().testTag(RobotTags.HERMES_DOC_EDITOR)` |
| same | `PixelButton(stringResource(R.string.ctl_save), onClick = { confirmSave = true }, icon = PixelIcon.CHECK, enabled = ...)` | add `modifier = Modifier.testTag(RobotTags.HERMES_DOC_SAVE)` |
| `ui/hermes/HermesTabs.kt` (S4) `SkillsTab` | `PixelButton(stringResource(R.string.ctl_skill_new), onClick = { newError = null; showNew = true }, ...)` | add `modifier = Modifier.testTag(RobotTags.HERMES_SKILL_NEW)` |
| same | `PixelPanel(Modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.ctl_skills_open)) { hvm.openSkill = skill }) {` | `PixelPanel(Modifier.fillMaxWidth().testTag(RobotTags.hermesSkillRow(skill.name)).clickable(...) { ... }) {` |
| same, `NewSkillDialog` | the first `PocketTextField(name, { name = it.take(SkillTemplate.MAX_NAME) }, Modifier.fillMaxWidth(), label = stringResource(R.string.ctl_skill_f_name), ...` | `Modifier.fillMaxWidth().testTag(RobotTags.HERMES_SKILL_NAME)` |
| same | `PixelButton(stringResource(R.string.ctl_skill_create), enabled = nameOk && catOk, onClick = { onCreate(...) })` | add `modifier = Modifier.testTag(RobotTags.HERMES_SKILL_CREATE)` |
| same | `PixelIconButton(PixelIcon.TRASH, onClick = { confirmDelete = current }, contentDescription = stringResource(R.string.ctl_skill_delete), tint = Pocket.colors.error,)` | add `modifier = Modifier.testTag(RobotTags.HERMES_SKILL_DELETE)` |
| `ui/hermes/TerminalScreen.kt` (S4) | `PocketTextField(input, { input = it; histPos = -1 }, Modifier.fillMaxWidth(), placeholder = ...` | `Modifier.fillMaxWidth().testTag(RobotTags.TERMINAL_INPUT)` |
| same | `PixelIconButton(PixelIcon.PLAY, onClick = { submit(input) }, contentDescription = stringResource(R.string.ctl_term_run), ...)` | add `modifier = Modifier.testTag(RobotTags.TERMINAL_RUN)` |
| same | `LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(10.dp)) {` | `LazyColumn(Modifier.fillMaxSize().testTag(RobotTags.TERMINAL_OUTPUT), state = listState, ...` |
| `ui/settings/SettingsScreen.kt` (S5) | the `Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClickLabel = ...` of each section header | `Modifier.fillMaxWidth().testTag(RobotTags.settingsSection(sec.key)).heightIn(min = 56.dp).clickable(...)` (`sec.key` is the existing `Section.key` string: `provider usage behaviour appearance voice language backup runtime diagnostics about`) |
| `ui/settings/UsageSection.kt` (S5) | `TotalsCard(stringResource(R.string.ctl_usage_today), todayTotals, locale, Modifier.weight(1f))` | `Modifier.weight(1f).testTag(RobotTags.USAGE_TODAY)` |
| same | the budget `PocketTextField(limitText, { raw -> ... }, Modifier.fillMaxWidth(), label = stringResource(R.string.ctl_usage_budget_field), ...)` | `Modifier.fillMaxWidth().testTag(RobotTags.USAGE_BUDGET_LIMIT)` (the third argument after the lambda) |
| same | `PocketChip(stringResource(R.string.ctl_usage_mode_warn), selected = ..., onClick = ...)` and `..._mode_block` | add `modifier = Modifier.testTag(RobotTags.USAGE_BUDGET_WARN)` and `...USAGE_BUDGET_BLOCK` |
| `ui/settings/BehaviourSection.kt` (S5) | `ChipRow {` under `SubHeading(stringResource(R.string.ctl_beh_idle_title))` | `ChipRow(Modifier.testTag(RobotTags.BEHAVIOUR_IDLE_MINUTES)) {` (edit nothing else in this file: CONFIG owns the privacy row) |
| `ui/settings/BackupSection.kt` (S5) | `PixelButton(stringResource(R.string.ctl_bak_create), ..., modifier = Modifier.fillMaxWidth(), ...)` | `modifier = Modifier.fillMaxWidth().testTag(RobotTags.BACKUP_SAVE)` |
| same | `PixelButton(stringResource(R.string.ctl_bak_restore), ..., modifier = Modifier.fillMaxWidth(), ...)` | `modifier = Modifier.fillMaxWidth().testTag(RobotTags.BACKUP_RESTORE)` |
| same | `is BackupUi.Done -> CText(u.text, size = 13.sp, color = c.success)` and `ErrorText(u.text)` in `is BackupUi.Failed` | `CText(u.text, Modifier.testTag(RobotTags.BACKUP_RESULT), size = 13.sp, color = c.success)` and `ErrorText(u.text, Modifier.testTag(RobotTags.BACKUP_RESULT))` (`modifier` is the second parameter of both) |
| `ui/bots/BotsScreen.kt` (S3) | `items(bots, key = { it.id }) { bot ->` then `BotCard(bot, selecting = ..., ...)` | `itemsIndexed(bots, key = { _, b -> b.id }) { i, bot ->`; `BotCard` is `private fun BotCard(bot, selecting, checked, onOpen, onChat)` and has no `modifier`: **do not tag**, report `LEFT botsRow (BotCard has no modifier parameter)` |
| `ui/bots/BotShareUi.kt` (S3) | `PixelButton(stringResource(R.string.ctl_bots_export_file), onClick = { exporter.saveToFile(mine) }, modifier = Modifier.weight(1f), ...)` | `modifier = Modifier.weight(1f).testTag(RobotTags.BOTS_EXPORT)` |
| same | `PixelButton(stringResource(R.string.ctl_bots_import_file), onClick = { picker.launch(...) }, modifier = Modifier.weight(1f), ...)` | `modifier = Modifier.weight(1f).testTag(RobotTags.BOTS_IMPORT)` |

Not taggable by SWEEP (reported, REQUEST to DESIGN and ROBOT in section 4): `HERMES_SKILL_DELETE_CONFIRM` and `BACKUP_CONFIRM` (the new `ConfirmDialog` has no modifier or tag parameter; its own tags are `dialog.confirm` and `dialog.cancel`), `botsRow(i)` (no modifier on `BotCard`). The sheet-row tag `sheet.action.<i>` belongs to DESIGN's `ActionSheet`, not to SWEEP.
`ChatMessages.kt` and `ChatScreen.kt` tags (`CHAT_LIST`, `chatMsg`, `CHAT_THOUGHT`, `CHAT_MENU`, `CHAT_FIND`, `CHAT_MEMORY_OPEN`, `CHAT_FORGED_CHAT`) are STREAM's. STREAM and ROBOT both listed the input, send, stop and approval tags for STREAM; the controls live in `ChatInput.kt`, so SWEEP adds them and STREAM must not (two `testTag`s on one node: the inner one is lost).

## 1.9 Recipe R8: the English match at HomeScreen:400

`ui/home/HomeScreen.kt` (S3), in `StatusCard`: `stringResource(if (s.step.startsWith("still starting")) R.string.ui_home_status_slow else R.string.ui_home_status_starting)` -> `s.step.startsWith(RuntimeManager.STILL_STARTING)`; import `dev.pocketagent.app.runtime.RuntimeManager`. **Only if** `rg -n "STILL_STARTING" APP/runtime/RuntimeManager.kt` finds the constant (CONFIG, ROBOT request 3); otherwise leave and report.

## 1.10 What SWEEP never changes (extra to 0.3)

No composable signature (except the added last parameter `kind` of `toast` and `showToast`), no `stringResource(` (WORDS), no `getString(`, no `Toast` in files outside the batch lists, no `remember` key, no `LaunchedEffect` key, no `Modifier.weight`/`fillMaxWidth` order, no `key =` of a lazy item, no data class, no `ViewModel`. `PocketDialog(` and `RenameDialog(` stay. Every `semantics {` block stays. Nothing in `ui/chat/ChatScreen.kt`, `ChatMessages.kt`, `Markdown*.kt`, `StreamSmoother.kt`, `MarkdownStream.kt`, `StreamingMarkdown.kt`, `ToolOutputSheet.kt`, `ChatTopArea.kt`, `ChatMessageList.kt`, `ChatInputArea.kt`, `ChatDialogs.kt`, `ChatSlashActions.kt`.

---

# PART 2. WORDS

Runs last, in ten batches, over every file under `ui/`, `service/`, `routines/`, `data/`, `hermes/` (plan). Two more places hold interface text and are in batch **W10b**, which runs only if TEXT built its group RT (check: `rg -c "eng_rt_" app/src/main/res/values/strings_engine.xml` is above 0): `runtime/RuntimeManager.kt` (runs after CONFIG) and `voice/VoiceEngine.kt`. Do not touch any other file of `runtime/`, `voice/`, `diag/`, `api/`, `util/`.
`ui/text/*` is never edited. New `Texts`/`t`/`tn` are made by TEXT; keys `eng_*` by TEXT in `app/src/main/res/values/strings_engine.xml`.

## 2.1 Batches (no file in two batches; counts at `344829f`: `stringResource(` / `getString(` with `R.string`)

| Batch | Files | Sites |
|---|---|---|
| **W1 chat, the split screen** | `ui/chat/ChatScreen.kt` (57 / 29) and every file STREAM made: `ChatScreenState.kt`, `ChatSlashActions.kt`, `ChatTopArea.kt`, `ChatMessageList.kt`, `ChatInputArea.kt`, `ChatDialogs.kt`, `ToolOutputSheet.kt`, `StreamingMarkdown.kt` (find them: `rg -l "stringResource\(\|getString\(" APP/ui/chat` minus the files of W2 and W3) | about 86 + 29, UI literals of 2.5 (memory, theme) |
| **W2 chat A** | `ChatListScreen.kt` (34), `ChatMessages.kt` (20), `ChatCommon.kt` (14 / 1), `StarredScreen.kt` (8), `MarkdownView.kt` (2), `ChatSearch.kt` (6) | 84 + 1; plural `ui_unread_cd` |
| **W3 chat B** | `ChatInput.kt` (21), `ChatQuickBar.kt` (16), `NewChatSheet.kt` (12), `AttachmentUi.kt` (10), `SkillPickerSheet.kt` (8), `SlashPalette.kt` (7) | 74; plural `ui_new_selected` |
| **W4 home, bots, shell, components** | `ui/home/HomeScreen.kt` (35), `HomeParts.kt` (2), `ui/bots/BotEditorScreen.kt` (61 / 1), `BotShareUi.kt` (16 / 8), `BotsScreen.kt` (16), `ui/AppRoot.kt` (5 / 1), `ui/components/PocketTopBar.kt` (1), `TypingIndicator.kt` (1), and DESIGN's new files with `stringResource(` (`PocketSheet.kt`, `ConfirmDialog.kt`, `PetNote.kt`, `Skeleton.kt`, `EmberLine.kt`, `DesignGallery.kt`; find with `rg -l "stringResource\(" APP/ui/components`) | about 150; plurals `ui_home_unread`, `ctl_bots_count`, `ctl_bot_chars`, `ctl_bots_import_title`, `ctl_bots_imported` |
| **W5 settings A** | `ui/settings/SettingsScreen.kt` (31), `AppearanceSection.kt` (28), `ProviderSection.kt` (44), `CtlUi.kt` (7), `BehaviourSection.kt` (23) | 133; theme names and preview literals (2.5) |
| **W6 settings B** | `UsageSection.kt` (31), `VoiceSoundSection.kt` (34), `RuntimeSection.kt` (32), `BackupSection.kt` (20 / 6) | 123; plural `ctl_usage_turns` |
| **W7 hermes** | `ui/hermes/HermesTabs.kt` (43 / 2), `HermesDocs.kt` (32), `HermesScreen.kt` (13), `TerminalScreen.kt` (15) (not `ConsoleAnsi.kt`, `HermesEditorsViewModel.kt`) | 105; plural `ctl_chars`; local `t` rename (2.7) |
| **W8 routines screens** | `ui/routines/RoutineEditorScreen.kt` (63), `RoutinesScreen.kt` (31), `RoutineUiParts.kt` (15) | 109; local `t` rename (2.7) |
| **W9 service and routines engine** | `service/ReplyNotifier.kt` (0 / 29), `AgentService.kt` (0 / 9 + 1 bare `getString` + 1 plural), `FireTileService.kt` (0 / 2, one with an `Int` variable), `routines/RoutineScheduler.kt` (0 / 9) | 50 |
| **W10 engine literals and tests** | `data/ChatEngine.kt`, `data/AttachmentStore.kt`, `hermes/TerminalSession.kt`, `hermes/HermesHome.kt` and the test classes of 2.8. **W10b** (conditional): `runtime/RuntimeManager.kt`, `voice/VoiceEngine.kt` | tables 2.4 |

Every other file under the five folders was checked: they hold no `stringResource(`, no `getString(` of a string resource and no interface literal (`service/Presence.kt`, `NotificationActionReceiver.kt`, `NotificationGroups.kt`, `routines/RoutineStore.kt` and the rest of `data/`: nothing to do, except the three UI literal groups of 2.5).

## 2.2 Recipe T1: composables (814 `stringResource(` in 37 files)

| Today | Becomes |
|---|---|
| `stringResource(R.string.x)` | `t(R.string.x)` |
| `stringResource(R.string.x, a, b)` | `t(R.string.x, a, b)` (same arguments, same order) |
| `stringResource(if (c) R.string.a else R.string.b)` (31 sites) | `t(if (c) R.string.a else R.string.b)` |
| `stringResource(labelRes)`, `stringResource(t.label)`, `stringResource(u.labelRes)`, `stringResource(slashDescRes(cmd.name))`, `stringResource(when (p) { ... })` | `t(<the same expression>)` |
| the multi-line forms `stringResource(` followed by a newline (`BackupSection.kt:199`, `VoiceSoundSection.kt:120,171`, `BotEditorScreen.kt:303`, `HomeScreen.kt:253,381`, `ChatInput.kt:210`, `ChatQuickBar.kt:270`, `HermesTabs.kt:271`, `ProviderSection.kt:167`) | replace only the word `stringResource(` by `t(`; leave the next lines |
| `import androidx.compose.ui.res.stringResource` | delete the line; add `import dev.pocketagent.app.ui.text.t` |
| a mention in a comment (`AppRoot.kt:132` KDoc: "Makes `stringResource` (and anything reading the locals below)...") | leave |

Real examples:
```kotlin
// ChatListScreen.kt:221
SheetAction(PixelIcon.PIN, stringResource(if (cv.pinned) R.string.ui_act_unpin else R.string.ui_act_pin)) { ... }
SheetAction(PixelIcon.PIN, t(if (cv.pinned) R.string.ui_act_unpin else R.string.ui_act_pin)) { ... }
// HermesScreen.kt:138-139  (the lambda parameter `t` is renamed first, see 2.6)
val label = stringResource(t.label) + if (dirtyOf[t] == true) " •" else ""
val label = t(ht.label) + if (dirtyOf[ht] == true) " •" else ""
// AppRoot.kt:444
val label = stringResource(tab.label)      ->      val label = t(tab.label)
```
`pluralStringResource(` does not occur today (0 sites).

## 2.3 Recipe T2: code without a composable (96 `getString(R.string...` plus 5 with a variable id, 1 `getQuantityString`)

Rule: `<receiver>.getString(<id>, args...)` becomes `Texts.get(<receiver>, <id>, args...)`: the receiver moves to the first argument, nothing else changes. Add `import dev.pocketagent.app.ui.text.Texts`. Never touch `prefs.getString`, `sp.getString`, `o.getString`, `c.getString(0)`, `Settings.Global.getString`: they are not texts (7 such lines remain in the five folders: `ui/theme/Appearance.kt` six, `data/AttachmentStore.kt:198`).

| Receiver form (all real) | Example before | After |
|---|---|---|
| `ctx.` | `ctx.getString(R.string.ui_attach_failed, e.message ?: "share")` | `Texts.get(ctx, R.string.ui_attach_failed, e.message ?: "share")` |
| `app.` (`RoutineScheduler`, 9 sites, lines 221 to 316) | `fail(r, app.getString(R.string.rtn_fail_empty))` | `fail(r, Texts.get(app, R.string.rtn_fail_empty))` |
| `l.` (`ReplyNotifier` 28 sites, `AgentService` 3, `FireTileService` 2) | `l.getString(R.string.prs_approval_title, botName)` | `Texts.get(l, R.string.prs_approval_title, botName)` |
| `localized().` (`AgentService` 45, 47, 48, 82, 120) | `localized().getString(R.string.notif_starting)` | `Texts.get(localized(), R.string.notif_starting)` |
| `appContext.withAppLanguage().` (`ReplyNotifier.kt:85`) | `appContext.withAppLanguage().getString(R.string.prs_untitled_chat)` | `Texts.get(appContext.withAppLanguage(), R.string.prs_untitled_chat)` |
| bare `getString(` inside a Service (`AgentService.kt:111`, in `private fun build`, not inside a lambda) | `.setContentTitle(getString(R.string.app_name))` | `.setContentTitle(Texts.get(this, R.string.app_name))` |
| variable id (5 sites) | `FireTileService.kt:82` `l.getString(subtitle)`; `BotShareUi.kt:91` `ctx.getString(res)`; `BackupSection.kt:229` `ctx.getString(res)`; `ChatScreen*` `ctx.getString(` followed by a newline and a `when` (the mode label, `ChatScreen.kt:543`); `ctx.getString(if (kotlin.random.Random.nextBoolean()) R.string.ui_cmd_roll_coin_heads else R.string.ui_cmd_roll_coin_tails)` (`ChatScreen.kt:671`) | `Texts.get(l, subtitle)`; `Texts.get(ctx, res)`; `Texts.get(ctx, res)`; `Texts.get(ctx,` then the same `when (...) {...}` lines then `)`; `Texts.get(ctx, if (kotlin.random.Random.nextBoolean()) R.string.ui_cmd_roll_coin_heads else R.string.ui_cmd_roll_coin_tails)` |
| quantity (1 site, `AgentService.kt:66`) | `l.resources.getQuantityString(R.plurals.prs_notif_running_answering, answering, answering)` | `Texts.getQuantity(l, R.plurals.prs_notif_running_answering, answering, answering)` |
| the `Int` overload of `Toast.makeText(ctx, R.string.x, ...)` | none are left after SWEEP and STREAM (verify with `rg "Toast\.makeText"`: 0) | report if found |

Where a site had `e.message` or another nullable value as an argument it already had `?: ...` (checked); if you meet one without, leave it and report.
`ReplyNotifier` calls `withAppLanguage()` itself; leave those calls, `Texts.get` also follows the chosen language (TEXT 3.1).

## 2.4 Recipe T3: the engine's literal texts

`Texts.text(@StringRes id, vararg args)` needs no Context (engine, terminal, data classes); with a Context in the class use `Texts.get(context, ...)`. Add `import dev.pocketagent.app.R` and `import dev.pocketagent.app.ui.text.Texts`. Keys are TEXT's (draft-TEXT section 5.1). Keep every `Diag.redactor.redact(...)` and every `addIssue(...)` argument exactly as is. Arguments are positional (`%1$d`, `%2$s`), in the order given.

### 2.4.1 `data/ChatEngine.kt` (W10; STREAM edited `Turn` first: find by the quoted text)

| Quoted old text | New |
|---|---|
| `"This chat has no bots (they were deleted). Add a bot to the chat first."` (4 sites, in `addIssue(convId, Role.ERROR, null, ...)`) | `Texts.text(R.string.eng_no_bots)` |
| `"Loop mode: pass 1/$totalPasses"` | `Texts.text(R.string.eng_loop_pass, 1, totalPasses)` |
| `"Loop mode: pass $pass/$totalPasses — refining…"` | `Texts.text(R.string.eng_loop_refine, pass, totalPasses)` |
| `Diag.redactor.redact("Could not send your answer to Hermes: ${r.describe()}")` | `Diag.redactor.redact(Texts.text(R.string.eng_approval_send_failed, r.describe()))` |
| `Diag.redactor.redact("Internal error: ${e.javaClass.simpleName}: ${e.message}")` | `Diag.redactor.redact(Texts.text(R.string.eng_internal_error, "${e.javaClass.simpleName}: ${e.message}"))` |
| `"Hermes is not running. Start it from the home screen, wait until it says Running, then try again."` | `Texts.text(R.string.eng_hermes_not_running)` |
| `"Hermes could not start. Open the home screen to see why."` | `Texts.text(R.string.eng_hermes_start_failed)` |
| `"Bot not found (it was deleted)."` | `Texts.text(R.string.eng_bot_not_found)` |
| `"The daily token budget ($limit tokens) is reached, so this message was not sent to the model. " + "Raise or turn off the limit under Settings > Usage, or try again tomorrow."` (two string pieces joined by `+`: replace both) | `Texts.text(R.string.eng_budget_blocked, limit)` |
| `"The model \"${chosenModel.trim()}\" set for this chat or bot belongs to another provider, " + "so the default model of the current provider is used instead. Change it in the chat controls or the bot editor."` | `Texts.text(R.string.eng_model_foreign, chosenModel.trim())` |
| `"Hermes asks permission before it runs this and is waiting for an answer:\n" + (if (cmd.isNotBlank()) cmd else desc) + (if (cmd.isNotBlank() && desc.isNotBlank()) "\n($desc)" else "")` | `Texts.text(R.string.eng_approval_pending, (if (cmd.isNotBlank()) cmd else desc) + (if (cmd.isNotBlank() && desc.isNotBlank()) "\n($desc)" else ""))` |
| `"The answer was cut off (length limit reached)."` | `Texts.text(R.string.eng_cut_off)` |
| `"The run ended abnormally: finish_reason=${ev.reason}" + (ev.errorCode?.let { " error_code=$it" } ?: "")` | `(ev.errorCode?.let { Texts.text(R.string.eng_run_abnormal_code, ev.reason, it) } ?: Texts.text(R.string.eng_run_abnormal, ev.reason))` |
| `"Daily token budget reached: ${b.dailyTokens} tokens used today. " + "Hermes keeps answering; change the limit under Settings > Usage."` | `Texts.text(R.string.eng_budget_warn, b.dailyTokens)` |
| `"The bot returned an empty reply."` | `Texts.text(R.string.eng_empty_reply)` |

**Stay literal on purpose** (technical or sent to the model; TEXT 5.1): `"HTTP ${outcome.code}: ${Diag.redactor.redact(outcome.body)}"` (asserted by `ChatEngineTest`), `ev.raw`, the loop prompt `"Critically review your previous response ..."`, `"Former participant"`, everything in `Transcript.kt`, `BotForge.kt`, `LegacyBotPrompts.kt`, `SlashCommands.kt`, `BotPresets.kt` (names, taglines, prompts), `AppLog` lines.

### 2.4.2 `hermes/TerminalSession.kt` (W10; no Context: `Texts.text`)

| Quoted old text | New |
|---|---|
| `TermLine("A command is still running. Wait for it or cancel it first.", TermKind.INFO)` | `TermLine(Texts.text(R.string.eng_term_busy), TermKind.INFO)` |
| `TermLine("The Hermes runtime is not installed yet. Start Hermes once from the home screen so it can set itself up, then try again.", TermKind.INFO)` | `TermLine(Texts.text(R.string.eng_term_not_installed), TermKind.INFO)` |
| `TermLine("[cancelled]", TermKind.INFO)` | `TermLine(Texts.text(R.string.eng_term_cancelled), TermKind.INFO)` |
| `TermLine("[exit code $code]", TermKind.INFO)` | `TermLine(Texts.text(R.string.eng_term_exit_code, code), TermKind.INFO)` |
| `TermLine(Diag.redactor.redact("Could not run the command: ${e.javaClass.simpleName}: ${e.message}"), TermKind.INFO)` | `TermLine(Diag.redactor.redact(Texts.text(R.string.eng_term_run_failed, "${e.javaClass.simpleName}: ${e.message}")), TermKind.INFO)` |

Leave `"$ "` and `"> "` (prompt symbols).

### 2.4.3 `data/AttachmentStore.kt` (W10; field `context`)

`IllegalStateException("Could not decode image")` -> `IllegalStateException(Texts.get(context, R.string.eng_attach_decode))`; `IllegalStateException("Could not encode JPEG")` -> `...eng_attach_encode`; `IllegalArgumentException("File is larger than ${MAX_DOC_BYTES / (1024 * 1024)} MiB")` -> `IllegalArgumentException(Texts.get(context, R.string.eng_attach_too_large, MAX_DOC_BYTES / (1024 * 1024)))`; `IllegalStateException("Could not open selected document")` -> `...eng_attach_open`. (They reach the screen through the `ui_attach_failed` toast.) Leave `c.getString(0)` (line 198, a cursor).

### 2.4.4 `hermes/HermesHome.kt` (W10)

`return guestPath(doc) + if (f.isFile) " (${formatSize(f.length())})" else " (not created yet)"` -> `... else " " + Texts.text(R.string.eng_doc_not_created)` (the key's English text is `(not created yet)` with its brackets; TEXT 5.1).

### 2.4.5 Conditional batch W10b (only when group RT exists in `strings_engine.xml`)

`runtime/RuntimeManager.kt` has the field `context`; each row is `RuntimeState.Error("<old>")` -> `RuntimeState.Error(Texts.get(context, R.string.<key>, args))`. Texts that stay English because other code matches them: the `Starting("...")`, `Stopped("...")` and `Extracting(...)` step texts (`HomeScreen.kt:400` and `RuntimeSection.kt:78` match `"still starting"`), `ExitRecord.describe()` and `hint()`.

| Quoted old text (start) | Key and arguments |
|---|---|
| `"Could not delete the runtime: ${e.message}"` | `eng_rt_delete_failed`, `e.message` |
| `"This build contains no rootfs (stub build). Install the APK produced by CI."` | `eng_rt_stub_rootfs` |
| `"Cannot read ${paths.rootfsStamp.name}: ${...javaClass.simpleName}: ${...message}. " + "The runtime was NOT reinstalled, ..."` | `eng_rt_stamp_unreadable`, `paths.rootfsStamp.name`, `"${stampRead.error.javaClass.simpleName}: ${stampRead.error.message}"` |
| `"Not enough free storage: need about ${FileUtil.formatBytes(need + need / 8)}, have ${FileUtil.formatBytes(freeNow)} free plus ${FileUtil.formatBytes(reclaimable)} reclaimable from the old runtime"` | `eng_rt_no_space`, the three `FileUtil.formatBytes(...)` values |
| `"Extraction failed: ${e.javaClass.simpleName}: ${e.message}"` | `eng_rt_extract_failed`, `"${e.javaClass.simpleName}: ${e.message}"` |
| `"Stub build: no proot binary bundled."` | `eng_rt_stub_proot` |
| `"proot binary missing: ${paths.prootBin}"` | `eng_rt_proot_missing`, `paths.prootBin` |
| `"Invalid configuration: $blocking (fix it under Setup)"` | `eng_rt_bad_config`, `blocking` |
| `"Hermes did not become healthy within 20 minutes (process still alive; see runtime log). Press Stop, then Start."` | `eng_rt_not_healthy` |
| `"Port ${h.port} is answered by a process that is not our Hermes (another app?). Press Start to retry on a new port."` | `eng_rt_foreign_port`, `h.port` |
| `"The API on port ${h.port} answers /health, but our Hermes never announced ... Press Start to retry."` (one long line) | `eng_rt_port_unannounced`, `h.port` |
| `"Hermes is up but rejected our API key (HTTP 401): ${m.body.take(200)}"` | `eng_rt_key_rejected`, `m.body.take(200)` |
| `"Hermes exited unexpectedly: ${rec.describe()}" + (ExitRecord.hint(code)?.let { " - $it" } ?: "")` | `(ExitRecord.hint(code)?.let { Texts.get(context, R.string.eng_rt_exited_hint, rec.describe(), it) } ?: Texts.get(context, R.string.eng_rt_exited, rec.describe()))` |
| `onOutput("This build contains no proot or rootfs (stub build).", true)` | `onOutput(Texts.get(context, R.string.eng_rt_term_stub), true)` |
| `onOutput("The Hermes runtime is not installed yet.", true)` | `onOutput(Texts.get(context, R.string.eng_rt_term_not_installed), true)` |
| `onOutput("Could not start the shell: ${e.javaClass.simpleName}: ${e.message}", true)` | `onOutput(Texts.get(context, R.string.eng_rt_term_shell_failed, "${e.javaClass.simpleName}: ${e.message}"), true)` |

`voice/VoiceEngine.kt` (field `context`): `_lastError.value = "System TTS error ($errorCode)"` -> `Texts.get(context, R.string.eng_tts_error, errorCode)`; `"System TTS unavailable"` -> `Texts.get(context, R.string.eng_tts_unavailable)`; `"System TTS init failed ($status)"` -> `Texts.get(context, R.string.eng_tts_init_failed, status)`. Leave the spoken demo sentences (lines 178 to 211).

## 2.5 Recipe T4: interface literals inside `ui/` (11 places) and the theme names

| File (batch) | Quoted old | New |
|---|---|---|
| `ui/settings/AppearanceSection.kt` (W5) | `CText("Hi!", color = p.onBotBubble, size = 8.sp, ...)` and `CText("Hello", color = p.onUserBubble, ...)` | `CText(t(R.string.eng_preview_hi), ...)` and `CText(t(R.string.eng_preview_hello), ...)` |
| same | `private fun themeName(id: ThemeId): String = when (id) { ThemeId.NEWROZ -> "Newroz" ... ThemeId.ROJ -> "Roj" }` (lines 215 to 222) | replace by two functions: `internal fun themeNameRes(id: ThemeId): Int = when (id) { ThemeId.NEWROZ -> R.string.eng_theme_newroz; ThemeId.ZAGROS -> R.string.eng_theme_zagros; ThemeId.SEMENDER -> R.string.eng_theme_semender; ThemeId.NERGIZ -> R.string.eng_theme_nergiz; ThemeId.KAWA -> R.string.eng_theme_kawa; ThemeId.ROJ -> R.string.eng_theme_roj }` and `@Composable internal fun themeName(id: ThemeId): String = t(themeNameRes(id))`. `ThemeCard` keeps `val name = themeName(id)` |
| `ui/settings/SettingsScreen.kt` (W5) `summaryOf`, `Section.APPEARANCE ->` | `ap.theme.name.lowercase().replaceFirstChar { it.uppercase() }` | `themeName(ap.theme)` (same package, no import) |
| `ui/chat/ChatScreen*` (W1; today `ChatScreen.kt:617,619`, after STREAM `ChatSlashActions.kt`) | `appendLine("MEMORY.md (${MemoryFormat.usedChars(memEntries)}/${MemoryFormat.MEMORY_LIMIT}):")` and the `USER.md` twin with `usrEntries` / `USER_LIMIT` | `appendLine(Texts.get(ctx, R.string.eng_memory_md_used, MemoryFormat.usedChars(memEntries), MemoryFormat.MEMORY_LIMIT))` and `appendLine(Texts.get(ctx, R.string.eng_user_md_used, MemoryFormat.usedChars(usrEntries), MemoryFormat.USER_LIMIT))` |
| same (line 634) | `ctx.getString(R.string.ui_cmd_theme_switched, pick.name.lowercase().replaceFirstChar { ch -> ch.uppercase() })` | `Texts.get(ctx, R.string.ui_cmd_theme_switched, Texts.get(ctx, themeNameRes(pick)))`; add `import dev.pocketagent.app.ui.settings.themeNameRes` |
| same (line 630) | `ThemeId.entries.joinToString(", ") { it.name.lowercase() }` | **leave**: this lists the words the user types after `/theme` (the command vocabulary, `arg.trim()` is compared with `it.name`); TEXT's draft proposed changing it, it is a disagreement for the lead (4.3) |
| `ui/bots/BotEditorScreen.kt` (W4) `AvatarGrid`, only if `rg -n "fun avatar\(" APP/ui/text/EnumLabels.kt` finds `EnumLabels.avatar` (TEXT cut item W9) | `.clickable(onClickLabel = id.name.lowercase().replaceFirstChar { it.uppercase() }) { onPick(id) }` and `.semantics { contentDescription = id.name.lowercase().replaceFirstChar { it.uppercase() } }` | `val avatarLabel = EnumLabels.avatar(id.name)?.let { t(it) } ?: id.name` declared inside `row.forEach { id ->` before the `Box(`; both places use `avatarLabel`; import `dev.pocketagent.app.ui.text.EnumLabels` |

Leave in English (TEXT 3.8 "Not changed"): language endonyms ("English", "Türkçe", "Français" in `AppearanceSection.kt:276-278` and `SettingsScreen.kt:184-186`), `"Supertonic 3"`, `"Supertonic 3 INT8 (99M)"`, `ExportLabels` defaults in `ChatLogic.kt:116-120`, `BackupException` and `BotShare` messages and the code in `BotShareUi.kt:62-70` that matches them, bot preset names and taglines, voice names (`VoicePrefs.kt`), command chips (`/plan`), example terminal commands, clipboard labels (`"Agirî"`, `"Agirî terminal"`), `"@"`, `":"`.

## 2.6 Recipe T5: plurals (11 sites, 10 keys; only if the keys are `<plurals>` now)

Check first, per key: `rg -n "<plurals name=\"<key>\"" app/src/main/res/values/strings_chat.xml app/src/main/res/values/strings_control.xml`. If the key is still a `<string>`, use T1 instead and say so. Form: `stringResource(R.string.k, n)` -> `tn(R.plurals.k, n, n)` (the count is passed twice: once to choose the form, once for the `%1$d`); `ctx.getString(R.string.k, n)` -> `Texts.getQuantity(ctx, R.plurals.k, n, n)`. Import `tn`.

| Key | Site (quoted) | After |
|---|---|---|
| `ui_new_selected` | `NewChatSheet.kt:139` `stringResource(R.string.ui_new_selected, picked.size)` | `tn(R.plurals.ui_new_selected, picked.size, picked.size)` |
| `ui_unread_cd` | `ChatListScreen.kt:336` `stringResource(R.string.ui_unread_cd, count)` | `tn(R.plurals.ui_unread_cd, count, count)` |
| `ui_home_unread` | `HomeScreen.kt:318` `stringResource(R.string.ui_home_unread, unreadTotal)` | `tn(R.plurals.ui_home_unread, unreadTotal, unreadTotal)` |
| `ctl_bots_count` | `BotsScreen.kt:72` `stringResource(R.string.ctl_bots_count, bots.size)` | `tn(R.plurals.ctl_bots_count, bots.size, bots.size)` |
| `ctl_bot_chars` | `BotEditorScreen.kt:254` `stringResource(R.string.ctl_bot_chars, prompt.length)` | `tn(R.plurals.ctl_bot_chars, prompt.length, prompt.length)` |
| `ctl_chars` | `HermesDocs.kt:158` `stringResource(R.string.ctl_chars, state.text.length)` | `tn(R.plurals.ctl_chars, state.text.length, state.text.length)` |
| `ctl_usage_turns` | `UsageSection.kt:127, 144, 211` `stringResource(R.string.ctl_usage_turns, d.totals.turns)`, `m.totals.turns`, `t.turns` | `tn(R.plurals.ctl_usage_turns, <x>, <x>)` with `<x>` the same expression |
| `ctl_bots_import_title` | `BotShareUi.kt:212` `stringResource(R.string.ctl_bots_import_title, list.size)` | `tn(R.plurals.ctl_bots_import_title, list.size, list.size)` |
| `ctl_bots_imported` | `BotShareUi.kt:219` `ctx.getString(R.string.ctl_bots_imported, list.size)` | `Texts.getQuantity(ctx, R.plurals.ctl_bots_imported, list.size, list.size)` |
| `ui_chat_members_count` | no use today | nothing |

## 2.7 Recipe T6: a local named `t` in the scope of a new `t(`

`t(` is a function; a local variable, parameter or lambda parameter named `t` in the same scope shadows it. Rename the local (declaration and every use inside its scope) **before** converting the calls in that scope. Real sites in the folders of WORDS (`rg -n "\b(val|var) t\b|\bt:\s|[{(,]\s*t\s*->"`):

| File | Declaration | Rename to |
|---|---|---|
| `ui/hermes/HermesScreen.kt:137` | `HermesTab.entries.forEach { t ->` (uses `t.label`, `dirtyOf[t]`, `t.ordinal`, `t.icon`) | `ht` |
| `ui/routines/RoutinesScreen.kt:157` | `ROUTINE_TEMPLATES.forEach { t ->` (uses `t.nameRes`, `t.promptRes`, `t.kind`, `t.hour`, `t.minute`, `t.days`, `t.intervalMinutes`, `t.descRes`) | `tpl` |
| `ui/settings/UsageSection.kt:205` | `private fun TotalsCard(title: String, t: UsageTotals, locale: Locale, ...)` (uses `t.total`, `t.tokensIn`, `t.tokensOut`, `t.turns`) | `totals` |
| `ui/settings/UsageSection.kt:92` | `val t = d.totals` | `dayTotals` (check its scope: it is inside the chart `forEach`; rename all uses there) |
| `ui/bots/BotsScreen.kt:133` | `val t = BotPresets.templates[i]` | `tpl` (its scope has `TemplateRow(t)` and `t.copy(...)` only; rename anyway) |
| `ui/chat/ChatScreen.kt:500, 689` (after STREAM: in `ChatMessageList.kt` and `ChatScreenState.kt`) | `val t = myTarget ?: return@LaunchedEffect`; `val t = input.trim()` | `target`, `trimmed` (only if a `t(` or `Texts` call is in the same scope; otherwise leave) |
| `ui/chat/ChatCommon.kt:260`, `ChatMessages.kt:452`, `ui/home/HomeParts.kt:69` | `val t = rememberInfiniteTransition(...)` | leave (no text call in those functions); rename only if the check below finds one |

Check after each batch: `rg -n "\bt\(" <file>` and for each hit scroll up to the function start for a declaration named exactly `t`.

## 2.8 Recipe T7: tests that assert English engine text (W10)

Without `Texts.install`, `Texts.text(...)` returns `"#<id>"`. In each class below add `import org.junit.Before` and the import of `TestTexts` (its package is declared by TEXT: `rg -n "object TestTexts" app/src/test`) and, as the first member of the class body, `@Before fun installTexts() { TestTexts.installEnglish() }`. None of the four has a `@Before` today.

| Test class (path under `app/src/test/java/dev/pocketagent/app/`) | Asserts (line) |
|---|---|
| `ChatEngineTest.kt` | `:131` `it.text.contains("another provider")`; `:633` `msgs[1].text.contains("could not start")` |
| `TerminalSessionTest.kt` | `:163` `TermLine("[exit code 3]", TermKind.INFO)`; `:190` `it.text.contains("still running")`; `:194` `TermLine("[cancelled]", TermKind.INFO)` |
| `HermesHomeTest.kt` | `:169` `contains("not created yet")` |
| `RuntimeManagerInitTest.kt` (only with W10b) | `:89` `s.reason.contains("Cannot read rootfs.stamp") && s.reason.contains("NOT reinstalled")` |

(TEXT's list named three of these files; `ChatEngineTest:633` and the three `TerminalSessionTest` asserts were found by `rg` over `app/src/test`.)

## 2.9 Left and right (`start` and `end`)

Result of the search: `rg -n -i "left|right" APP/ui APP/service APP/routines APP/data APP/hermes` finds **no code** that uses an absolute direction (no `AbsoluteAlignment`, `absolutePadding`, `Arrangement.Left`/`Right`, `Alignment.CenterLeft` and the like, no `TextAlign.Left`/`Right`, no `paddingLeft`). Confirmed in `app/src/main/res` too. So WORDS has **no code edit** for this item. Words in comments only; change them only in files that WORDS edits anyway, only the three below, nothing else:

| File | Quoted comment | New |
|---|---|---|
| `ui/components/PocketSwitchRow.kt:38` | `title (+ optional subtitle) on the left, a pixel toggle on the right.` | `... on the start side, a pixel toggle on the end side.` |
| `ui/components/PocketDialog.kt:33` | `right-aligned at the bottom` | `end-aligned at the bottom` |
| `ui/components/PocketTopBar.kt:34` | `action buttons on the right` | `action buttons on the end side` |

(These three files are DESIGN's and belong to batch W4; if DESIGN rewrote the comment already, skip.) Do not touch `ui/theme/PixelFont.kt:226-227` (the arrow glyph comments "arrow right/left" describe the picture), `ui/hermes/ConsoleAnsi.kt` (`brighter`), or any "right now" in comments.

## 2.10 What WORDS never changes (extra to 0.3)

No resource file. No text that is not in the tables above (an English literal you find that is not listed: report it, do not convert it). No `ui/text/*`. No `ExportLabels`, `BackupException`, `BotShare`, `BotPresets`, `Transcript`, `BotForge`, `LegacyBotPrompts`, `SlashCommands` mode prompts, `Diagnostics`, `ProviderConfig` summaries, `AppLog` strings. No `.uppercase(`/`.lowercase(`/`replaceFirstChar` other than the four sites of 2.5 (the other 40 are logic or the case rule of TEXT's W12). No `SectionHeader` change (TEXT writes the capitals into the strings; DESIGN drops the `uppercase(locale)` call). No glued sentence is repaired (`NewChatSheet.kt:139`, `UsageSection.kt:127`, `ChatMessages.kt:205,209`, `ChatListScreen.kt:312`, `HermesScreen.kt:138`, `BotEditorScreen.kt:355`, `SettingsScreen.kt:194,196`: mechanical conversion only; TEXT risk 7).

---

# PART 3. Checklist for the lead (ripgrep; run from the repository root)

Run in Git Bash. `r` is `rg --no-config`. Every command prints the lines that violate the rule: **no output is a pass**, except where a
count is given. The number after "today" is what the command printed on the tree at `344829f` (it proves the command runs and shows the size of the job).
Folders: `ui service routines data hermes` unless the line says otherwise; `runtime` and `voice` are added only when batch W10b ran.

```sh
cd "app/src/main/java/dev/pocketagent/app"
r="rg --no-config"
```

## 3.1 After SWEEP

```sh
# S-1  no platform toast left anywhere in ui (also proves NAV and STREAM did theirs)                         today 18 lines
$r -n 'Toast\.makeText|import android\.widget\.Toast' ui
# S-2  no Material sheet except inside PocketSheet.kt                                                         today 30
$r -n 'ModalBottomSheet|rememberModalBottomSheetState|BottomSheetDefaults' ui --glob '!ui/components/PocketSheet.kt'
# S-3  exactly ONE ConfirmDialog function, in ui/components/ConfirmDialog.kt                                  today 3 (expect 1)
$r -n 'fun ConfirmDialog\(' ui
# S-4  exactly TWO lines (ActionSheet, SheetAction), both under ui/components/                                today 2 (in ui/chat; expect 2 in ui/components)
$r -n 'fun ActionSheet\(|class SheetAction\(' ui
# S-5  no import of the deleted settings.ConfirmDialog                                                        today 5
$r -n 'import dev\.pocketagent\.app\.ui\.settings\.ConfirmDialog' ui
# S-6  every file that calls the three moved names imports them (list the files, then count imports per file)
$r -l 'ConfirmDialog\(|ActionSheet\(|SheetAction\(' ui --glob '!ui/components/*' | while read f; do
  echo "$f: $($r -c 'import dev\.pocketagent\.app\.ui\.components\.(ConfirmDialog|ActionSheet|SheetAction)$' "$f")"; done
#      expect ChatListScreen.kt: 3, every ConfirmDialog-only file: 1, ChatScreen*.kt / ChatDialogs.kt: STREAM's imports; a file with 0 is wrong
# S-7  no direct haptic call outside the haptics file and the components                                      today 24
$r -n 'performHapticFeedback|HapticFeedbackType|LocalHapticFeedback' ui --glob '!ui/theme/Haptics.kt' --glob '!ui/components/*'
# S-8  no raw tween or keyframes in SWEEP's folders                                                           today 7
$r -n '\btween\(|\bkeyframes\(' ui/home ui/bots ui/hermes ui/routines ui/settings ui/chat/AttachmentUi.kt ui/chat/ChatCommon.kt ui/chat/ChatInput.kt ui/chat/ChatListScreen.kt ui/chat/ChatQuickBar.kt ui/chat/NewChatSheet.kt ui/chat/SkillPickerSheet.kt ui/chat/SlashPalette.kt ui/chat/StarredScreen.kt
# S-9  RoundedCornerShape in SWEEP's files: 0 when D1 = yes; when D1 = no exactly 12 (ChatQuickBar 4, ChatInput 2, AttachmentUi 2, SkillPickerSheet 2, AppearanceSection 2)   today 15
$r -n 'RoundedCornerShape\(' ui/home ui/bots ui/hermes ui/routines ui/settings ui/chat/AttachmentUi.kt ui/chat/ChatCommon.kt ui/chat/ChatInput.kt ui/chat/ChatListScreen.kt ui/chat/ChatQuickBar.kt ui/chat/NewChatSheet.kt ui/chat/SkillPickerSheet.kt ui/chat/SlashPalette.kt
# S-10 literal durations left: exactly the 5 reported hold times (ChatListScreen 96, HomeScreen 122 123 124, RoutinesScreen 78)   today 5
$r -n 'durationMillis\s*=\s*[0-9]|\bdelay\(\s*[0-9]' ui/home ui/bots ui/hermes ui/routines ui/settings ui/chat/ChatListScreen.kt
# S-11 test tags: 37 mentions in the files of 1.8 (list them, then compare with the table of 1.8)
$r -o --no-filename 'RobotTags\.[A-Za-z_]+|testTag\("[^"]+"' ui/home/HomeScreen.kt ui/chat/ChatListScreen.kt ui/chat/ChatInput.kt ui/hermes ui/settings/SettingsScreen.kt ui/settings/UsageSection.kt ui/settings/BehaviourSection.kt ui/settings/BackupSection.kt ui/bots | sort | uniq -c
#      expect: HOME_STATE 1, HOME_START 1, CHATS_NEW 2, chatsRow 3, CHAT_INPUT 1, CHAT_SEND 1, CHAT_STOP 1, "chat.attach" 1, "chat.mic" 1, "chat.approval.$ch" 1,
#      HERMES_TAB_SOUL/MEMORY/SKILLS 1 each, HERMES_DOC_EDITOR 1, HERMES_DOC_SAVE 1, HERMES_SKILL_NEW 1, hermesSkillRow 1, HERMES_SKILL_NAME 1,
#      HERMES_SKILL_CREATE 1, HERMES_SKILL_DELETE 1, TERMINAL_INPUT 1, TERMINAL_RUN 1, TERMINAL_OUTPUT 1, settingsSection 1, USAGE_TODAY 1,
#      USAGE_BUDGET_LIMIT 1, USAGE_BUDGET_WARN 1, USAGE_BUDGET_BLOCK 1, BEHAVIOUR_IDLE_MINUTES 1, BACKUP_SAVE 1, BACKUP_RESTORE 1, BACKUP_RESULT 2, BOTS_EXPORT 1, BOTS_IMPORT 1
# S-12 Space tokens appear only in the ten files of 1.7 (every other SWEEP file must be absent from this list)
$r -l 'Space\.(xxs|xs|s|m|l|xl|xxl|gutter|touch)\b' ui/home ui/bots ui/hermes ui/routines ui/settings ui/chat --glob '!ui/chat/ChatMessages.kt' --glob '!ui/chat/ChatScreen*.kt'
#      expect exactly: ChatQuickBar, AttachmentUi, HomeScreen, AppearanceSection, ChatInput, ChatListScreen, BotEditorScreen, RoutinesScreen, SlashPalette, HomeParts
```
```powershell
# S-13 the ratchet (the lead runs -Update once after the compile; the baseline only goes down)
powershell -NoProfile -ExecutionPolicy Bypass -File tools\lint-ui.ps1 -Check      # exit 0; rules toast, sheetDirect, hapticDirect at 0 (outside what STREAM, NAV, STAGE still own)
```
S-14: integration compile of the app and the unit tests: green (the compile is also the proof that no call still passes `text =` to the new `ConfirmDialog`).

## 3.2 After WORDS

```sh
# W-1  no direct text call in a composable                                                                   today 780 lines
$r -n 'stringResource\(|pluralStringResource\(' ui service routines data hermes --glob '!ui/text/*'
#      with W10b also: $r -n 'stringResource\(' runtime voice          (0 today as well)
# W-2  no import of the old functions                                                                         today 37
$r -n 'import androidx\.compose\.ui\.res\.(stringResource|pluralStringResource)' ui service routines data hermes
# W-3  no Context getString of a resource (the lint rule; includes the variable-id forms)                     today 97
$r -n '\bgetString\(\s*(R\.|if\b|res\b|subtitle\b|$)' ui service routines data hermes
# W-4  no getQuantityString                                                                                   today 1
$r -n 'getQuantityString\(' ui service routines data hermes
# W-5  no bare getString( (AgentService.kt:111)                                                               today 1
$r -n '(^|[^.\w])getString\(' ui service routines data hermes
# W-6  the 7 lines that are NOT texts and stay: ui/theme/Appearance.kt x6 (prefs) and data/AttachmentStore.kt (cursor)   today 103; expect 7
$r -n '\.getString\(' ui service routines data hermes
# W-7  every file that calls t( tn( Texts. has its import (list, then check by eye; an import without a call is wrong too)
$r -l '\bt\(|\btn\(|Texts\.' ui service routines data hermes --glob '!ui/text/*' | while read f; do
  echo "$f: t-import=$($r -c 'import dev\.pocketagent\.app\.ui\.text\.t$' "$f") tn-import=$($r -c 'import dev\.pocketagent\.app\.ui\.text\.tn$' "$f") Texts-import=$($r -c 'import dev\.pocketagent\.app\.ui\.text\.Texts$' "$f")"; done
# W-8  the 10 plural keys are no longer read as strings (when they are plurals now)                           today 11
$r -n 'R\.string\.(ui_new_selected|ui_chat_members_count|ui_unread_cd|ui_home_unread|ctl_bots_count|ctl_bot_chars|ctl_chars|ctl_usage_turns|ctl_bots_import_title|ctl_bots_imported)\b' ui service routines data hermes
# W-9  engine literals gone from data/                                                                        today 20
$r -n 'This chat has no bots|Loop mode: pass|Could not send your answer|Internal error:|Hermes is not running|Hermes could not start|Bot not found|The daily token budget|Daily token budget reached|belongs to another provider|Hermes asks permission|was cut off|ended abnormally|returned an empty reply' data
# W-10 terminal literals gone                                                                                 today 5
$r -n 'A command is still running|runtime is not installed yet|\[cancelled\]|\[exit code|Could not run the command' hermes
# W-11 attachment literals gone                                                                               today 4
$r -n 'Could not decode image|Could not encode JPEG|File is larger than|Could not open selected document' data
# W-12                                                                                                        today 2
$r -n 'not created yet' hermes
# W-13 UI literals gone (preview bubbles, memory file lines, theme names)                                     today 10
$r -n '"Hi!"|"Hello"|"MEMORY\.md |"USER\.md |"Newroz"|"Zagros"|"Semender"|"Nergiz"|"Roj"' ui
# W-14 the theme case change is gone (SettingsScreen, ChatScreen slash code)                                  today 4
$r -n 'name\.lowercase\(\)\.replaceFirstChar' ui
# W-15 no absolute left/right API (it was 0 before and must stay 0)                                           today 0
$r -n 'Absolute|absolutePadding|Arrangement\.(Left|Right)|Alignment\.(Center|Top|Bottom)(Left|Right)|TextAlign\.(Left|Right)|padding(Left|Right)' ui service routines data hermes
# W-16 no English match for "still starting" left in ui (after S3 and ROBOT)                                  today 2
$r -n '"still starting"' ui
# W-17 Texts.text( (the context-free form) only where there is no Context at hand                             today 0
$r -n 'Texts\.text\(' ui service routines
# W-18 a local called t next to a t( call: print the declarations in every file that calls t(
$r -l '\bt\(' ui service routines data hermes --glob '!ui/text/*' | while read f; do $r -n '\b(val|var) t\b|\bt:\s|[{(,]\s*t\s*->' "$f" | sed "s|^|$f:|"; done
#      expect no line from the files named in 2.7 (HermesScreen, RoutinesScreen, UsageSection, BotsScreen)
# W-19 the test classes got the @Before
$r -l 'TestTexts\.installEnglish' ../../../../../test       # 4 files (3 without W10b)
```
```powershell
# W-20
powershell -NoProfile -ExecutionPolicy Bypass -File tools\lint-ui.ps1 -Check            # directText at 0 (needs the pattern fix of 4.1), leftRight 0
powershell -NoProfile -ExecutionPolicy Bypass -File tools\strings-check.ps1 -FailOnUnused   # every eng_ key and every label key is used
```
W-21: integration compile; unit tests including the four of 2.8; phone: the robot scenario `sweep-stretched-and-mirrored` shows no plain English on Home, Chats, Chat, Bots, Hermes, Terminal and Settings.

---

# 4. Requests, conflicts and open questions found while writing

## 4.1 Requests to other areas (exact)

| To | File | Change |
|---|---|---|
| ROBOT | `ui/settings/RuntimeSection.kt` | line 115 `if (confirmReset) ConfirmDialog(`: argument `text =` to `body =` and add `import dev.pocketagent.app.ui.components.ConfirmDialog`. (DESIGN 3.10 lists this site for SWEEP; the plan table gives the file to ROBOT. SWEEP does not touch it. Until ROBOT does this, the app does not compile once S5 deletes the old function.) Also line 78 uses `RuntimeManager.STILL_STARTING` (already in the ROBOT draft) |
| ROBOT | `tools/lint-ui.ps1` | rule `directText`: replace `\.getString\(` by `\.getString\(\s*(R\.string\.\|if\b\|res\b\|subtitle\b\|$)\|(^\|[^.\w])getString\(` and add `getQuantityString\(`; the old pattern counts 7 lines that are not texts (`ui/theme/Appearance.kt` prefs x6, `data/AttachmentStore.kt:198` cursor) and could never reach 0. Keep `ui/text/**` exempt |
| ROBOT, STREAM | tags | the tags `chat.input`, `chat.send`, `chat.stop`, the four `chat.approval.*` and `chat.attach`, `chat.mic` are on controls in `ui/chat/ChatInput.kt`, which is SWEEP's file, so SWEEP adds them (1.8). STREAM must not add the same tags. ROBOT: add constants `CHAT_ATTACH = "chat.attach"`, `CHAT_MIC = "chat.mic"` to `RobotTags` so SWEEP's two literals can be replaced later |
| DESIGN | `ui/components/ConfirmDialog.kt` | optional parameter `confirmTag: String? = null` (and `dismissTag`) applied with `Modifier.testTag`, so `RobotTags.HERMES_SKILL_DELETE_CONFIRM` and `BACKUP_CONFIRM` can be set; otherwise ROBOT's scenarios 25 and 30 must use `dialog.confirm` |
| DESIGN | `ui/components/PocketSwitchRow.kt` | `modifier: Modifier = Modifier` (ROBOT request 8); SWEEP does not need it, but it blocks tags on switch rows later |
| DESIGN, STREAM | toasts in `ChatScreen*.kt` | the drafts disagree: DESIGN says STREAM converts all seven toasts to `PetNotes.show(...)` with the ERROR kind; STREAM says `ChatScreenState.say` keeps one `Toast.makeText` for SWEEP to replace (S6). Pick one; S6 does nothing when STREAM already converted. If STREAM keeps `say(text)`, the ERROR kind of `ChatScreen:395, 525, 967, 1014` is lost (the signature has no kind): STREAM should give it `kind: PetNoteKind = PetNoteKind.INFO` |
| TEXT | `strings_engine.xml` and `tools/strings-check.ps1` | none new; note that TEXT's list of tests to fix (3.8 / section 8 last row) misses `ChatEngineTest:633` and `TerminalSessionTest:163, 190, 194` (2.8) |
| LEAD | AGENTS.md Hard-won facts | after Wave 0: "SWEEP and WORDS find sites by text, not line number; a `ConfirmDialog` or `ActionSheet` call needs `import dev.pocketagent.app.ui.components.*` because same-package copies are gone" |

## 4.2 Symbols these two agents create (other areas depend on them)

`internal fun toast(ctx: Context, message: String, kind: PetNoteKind = PetNoteKind.INFO)` (`ui/settings/CtlUi.kt`); `internal fun showToast(ctx: Context, message: String, kind: PetNoteKind = PetNoteKind.INFO)` (`ui/routines/RoutineUiParts.kt`); `internal fun themeNameRes(id: ThemeId): Int` and `@Composable internal fun themeName(id: ThemeId): String` (`ui/settings/AppearanceSection.kt`, package `ui.settings`; used by `SettingsScreen.kt` and by the chat slash code in W1); removed: `ui.settings.ConfirmDialog`, `ui.chat.ConfirmDialog`, `ui.chat.ActionSheet`, `ui.chat.SheetAction`, the private `ConfirmDialog` of `RoutineEditorScreen.kt`.

## 4.3 Open questions for the lead

1. **D1:** may SWEEP turn the 14 `RoundedCornerShape(` of its files into `PixelShapes.chip/card` (stepped corners are the design language, but it is a visible change that the plan's "nothing looks different" does not mention)? Default in 1.7 is yes.
2. `ChatScreen.kt:630` (`/theme` usage note lists the typeable theme names): TEXT wants the case change removed; the names are the command vocabulary, so 2.5 leaves it. Agree?
3. Batch W10b (runtime and voice texts) is outside the plan's WORDS definition; run it after CONFIG only if TEXT keeps group RT (it is TEXT's CUT 2). OK?
4. S5 and CONFIG both edit `BehaviourSection.kt` (CONFIG: the privacy row; SWEEP: one `ChipRow(` line and imports): they must not run at the same time; the order in the header (CONFIG before SWEEP) is enough.
5. `PetNotes` kinds for the 15 `toast(` callers: DESIGN's table keeps them at INFO except four ERROR sites; saved/created/started/copied would read better as SUCCESS. Left as DESIGN says (no change); a later pass can add the kind.
6. The drafts of STREAM and ROBOT both ask the same owner for different things in `ChatInput.kt`; this recipe takes the tags (1.8). If the lead moves the file to STREAM, strike R7 rows 5 to 10 of the `ChatInput.kt` block.
