# Wave 0: the fixes for the defects of the phone proof (2026-11-25)

The defects are listed in `phone-proof.md`. Three areas were fixed side by side (each written by
Sonnet, read by Opus, repaired), then integrated (compile, unit tests, checks), then the whole diff
was read by Opus and repaired. **Nothing in this file has been tried on the phone yet:** the phone
was away. What the proof on the twin must look at is at the end.


## Area SHEET

### The fix report

CAUSE
- **S1, sheet invisible at animation scale 1** (`ui/components/PocketSheet.kt`, original version in HEAD). I could not reproduce this without a device. This is the most probable cause, from reading the code.
  - The sheet started hidden (`started = false`, so `translationY = size.height`).
  - It became visible only through a `LaunchedEffect(heightPx > 0)` that ran `animate(h -> 0, motion.glide())`.
  - That effect was declared in the caller's composition, outside the `Dialog`, and keyed on a measured value, so everything depended on the animation clock reaching it.
  - At scale 0, `Pocket.motion` gives `snap()`, so the sheet jumped to rest and the bug stayed hidden. At scale 1 the real spring runs. Nothing hit the log, so the hang was silent.
  - `PocketDialog` runs its animations inside the window and works at scale 1. That supports this reading, but it is not proven.
  - The scrim is a full-screen clickable that does not depend on that animation. That is why it ate every touch while the sheet was invisible.
- **S2, Turkish text in dialogs** (`ui/components/PocketSheet.kt` and `PocketDialog.kt` called `Dialog(` directly).
  - A Compose `Dialog` is its own window with its own `AndroidComposeView`. That view provides `LocalContext`, `LocalConfiguration`, `LocalResources`, `LocalLayoutDirection` and `LocalDensity` again from the dialog's own context.
  - So the in-app language set in `ui/AppRoot.kt` (`LocaleScope`) stopped at the window edge. `stringResource` and `t()` (through `appLocale()`, which read `LocalConfiguration`) then used the phone's language.
  - The "OK" button of "Diagnostics copied" was affected too: it is a `t(R.string.ok)` call that runs inside the dialog's composition.
- **S3, scrim without a spoken label.** The sheet scrim had `clickable` with no label, role or description. The dialog scrim had the same gap.

CHANGED
- `ui/components/PocketWindow.kt` (new). The single door to a window of its own. It captures context, configuration, resources, layout direction and density outside the `Dialog` and provides them again inside.
- `ui/components/SheetSlide.kt` (new). The sheet's position as plain, Android-free data. It holds the phase (ENTERING, OPEN, CLOSING), `hidden` as a fraction of the sheet's own height, `dragPx` and a `drive` counter. Pure functions cover opening, drag, release, close, rest and the stall check.
- `ui/components/PocketSheet.kt` (rewritten):
  - Visibility no longer depends on a measurement or on `started`. With animations off the sheet is at rest from the first frame. With them on it starts one height down and glides up. The layer turns the fraction into pixels at draw time.
  - The motion runs in a `LaunchedEffect(drive)` inside the window, keyed only on the count of started motions.
  - `playSlide` is now `internal` and takes `stallMs`. A watchdog finishes the motion by force after 1500 ms if no frame moved anything, so the sheet can neither stay invisible nor refuse to close.
  - The scrim has a click label, `Role.Button`, `contentDescription` "Close" and `traversalIndex = 1f`. The sheet is a traversal group with `paneTitle` (the title, or "Bottom sheet") and a `dismiss` action.
  - It uses `PocketWindow`. The tags `sheet`, `sheet.scrim`, `sheet.handle` and `sheet.title` are unchanged.
- `ui/components/PocketDialog.kt`: uses `PocketWindow`. The scrim has a click label, `Role.Button` and `contentDescription`.
- `ConfirmDialog.kt` needed no change, because it goes through `PocketDialog`.
- `ui/text/AppLocale.kt` (new): the pure `resolveAppLocale(language, configurationLocale)`. The chosen app language wins; an empty or invalid tag falls back to the configuration.
- `ui/text/TextsCompose.kt`: `appLocale()` now uses `LocalAppLanguage` first, then the configuration. Composition locals still flow into a dialog, so `t()` and `tn()` stay right even without the re-provide. I added the missing imports (`remember`, `LocalAppLanguage`); they were absent in the tree.
- `ui/AppRoot.kt`: no change needed. `LocaleScope` already exposes the overridden locals and `LocalAppLanguage`, including the debug override.
- `res/values`, `values-tr` and `values-fr` `strings_design.xml`: added `design_scrim_close` (Close / Kapat / Fermer) and `design_sheet_pane` (Bottom sheet / Alt panel / Panneau du bas). Nothing was added to the generated rXZ or `values-en` folders.
- `ui/components/README.md`: a short note on `PocketWindow` and `SheetSlide`.

TESTS
Nothing has been compiled or run, because the rules forbid Gradle here. Both files were checked by reading only.
- `app/src/test/.../ui/components/SheetSlideTest.kt` (new, about 20 tests). It covers:
  - animations on: starts one height down, glides in, overshoot is clamped;
  - animations off: at rest from the first frame, no measurement needed;
  - height not yet measured: 0, NaN and negative heights;
  - drag, release and dismiss: no jump on release, a second close request does nothing, a closing sheet ignores drags, close from a deep pull, close while still entering;
  - re-open: a new sheet is fresh again;
  - `playSlide`: with no spec it applies at once with no frame clock; with a dead frame clock it is finished by force, for both opening and closing; with a ticking clock it plays the whole glide.
- `app/src/test/.../text/AppLocaleTest.kt` (new): the chosen language beats the phone's, empty means the system language, a tag naming no language is ignored, `en-XZ` keeps its region, spaces are trimmed.
- `PressAndSheetMathTest` is unchanged and still valid.

REQUESTS
- No required changes in files I do not own. `Motion.kt` needs none.
- The grep for `Dialog(`, `AlertDialog(`, `ModalBottomSheet(`, `Popup(`, `DropdownMenu(` and their imports found no other uses in the app, including the debug and twin source sets. Every dialog and sheet goes through `PocketDialog`, `ConfirmDialog`, `PocketSheet` or `ActionSheet`. Nothing to list.
- Optional, in other people's files: the robot needs a scenario that opens a sheet at animation scale 1 and expects the tag `sheet` (the tool finding in the proof report). Also, `SettingsScreen.kt:135`, `BotShareUi.kt:232` and `SettingsScreen.kt:212` build the dialog's "OK" button without tag `dialog.confirm`. That tag gap is why the proof robot had to tap it by text.

NOT DONE
- The exact trigger of S1 is unproven, as stated above. The fix removes every dependency that could cause it instead of relying on one theory.
- Non-Compose `Texts.get(context, ...)` and `Texts.text(...)` read the language from `AppearanceStore`, not from `DebugOverrides.language`. That only matters for a debug-link language override on engine, service or notification text. Not touched, because it was not part of S2.

RISKS (what the phone proof must look at)
- **Sheets at scale 1.** Open each sheet (New chat, Chat Controls, tool output, message menu). It must rise visibly. If the watchdog fires (the sheet pops in after about 1.5 s), the frame clock of the window really is the cause. Then check back, scrim tap, the grabber, a drag with fling, and a short drag that returns.
- **Closing at scale 1.** `onDismiss` must run after the slide. A sheet's `close { action }` must run its action after `onDismiss`.
- **Opening and closing while a keyboard is up.** The `imePadding` path is unchanged but is where measurement matters most.
- **Language inside dialogs.** With the phone on Turkish and the app on English, all dialog and sheet text must be English. Repeat for French, `en-XZ` and rtl=1; layout direction and density are now re-provided inside the window. The "Diagnostics copied" OK button should say OK.
- **TalkBack and uiautomator.** The scrim should read "Close, button". The sheet should be announced as a pane. In the uiautomator dump, the scrim now has content-desc "Close". The dialog scrim now has a label as well, so the spoken-label sweep should no longer flag it.
- **Package cycle.** `TextsCompose.kt` in `ui.text` now imports `dev.pocketagent.app.ui.LocalAppLanguage`. This is the same module and compiles. If someone wants `ui.text` free of `ui`, move that local into `ui/text`. That needs a change in `AppRoot.kt`, which I did not make.
- **Compile risk.** Nothing was compiled. Most likely to need a fix: the `MonotonicFrameClock` fakes in `SheetSlideTest`, and `playSlide` call sites, where I kept `isStalled` and `apply` as named arguments because `stallMs` comes after them.

Files are in `app\src\`:
- `main\java\dev\pocketagent\app\ui\components\` (`PocketWindow.kt`, `SheetSlide.kt`, `PocketSheet.kt`, `PocketDialog.kt`, `README.md`)
- `main\java\dev\pocketagent\app\ui\text\` (`AppLocale.kt`, `TextsCompose.kt`)
- `main\res\values*\strings_design.xml`
- `test\java\dev\pocketagent\app\ui\components\SheetSlideTest.kt`
- `test\java\dev\pocketagent\app\text\AppLocaleTest.kt`


### The review (ok)

- **minor**, `app/src/main/java/dev/pocketagent/app/ui/components/PocketSheet.kt`: The playSlide watchdog only catches a motion that never moved at all. It checks isStalled() once, after STALL_MS, against the start value. If frames stop after the spring has taken even one step (say hidden goes 1.0 -> 0.97 and then no more frames come), isStalled is false, the watchdog ends, and run.join() waits forever. The sheet then sits almost all the way below the screen while the scrim swallows every touch, which is defect S1 again. The report's promise that the sheet 'can neither stay invisible nor refuse to close' only holds for a frame clock that never ticked at all. *Fix asked:* Make the watchdog look for progress, not only for a first move. Loop: remember last = current hidden; delay(stallMs); if hidden == last, cancel run and break; else last = hidden and loop again. Or add a hard deadline as well: if run is still active after stallMs + 1500 ms, cancel it. Either way apply(to) runs afterwards, as it does now. Add one test with a clock that ticks twice and then awaits cancellation, and check that the slide still ends at `to`.
- **minor**, `app/src/main/java/dev/pocketagent/app/ui/components/PocketSheet.kt`: One part of the stated S1 reasoning is wrong, and it changes what the phone proof means. A Compose Dialog composes its content as a child composition of the caller's Recomposer, so a LaunchedEffect inside the window and one outside both use the same frame clock. Moving the effect 'inside the window' therefore changes nothing about frames. What really changed: the effect is now keyed only on `drive`, so a height blip (heightPx going to 0 and back during window or inset layout) can no longer cancel the opening glide and leave it stuck because of the `started` guard. That is the most likely real cause, and the new code does remove it. If the real cause was the frame clock after all, the only thing that saves the sheet is the watchdog: the sheet would pop in after 1.5 s with no glide. *Fix asked:* No code change needed for this, apart from the first finding. Correct the comment above LaunchedEffect(drive) and the README note so they do not claim a different clock inside the window. Tell the phone proof that a sheet which pops in after about 1.5 s instead of gliding means the cause is still open (and app.log could log a line when the watchdog fires, so the proof can see it).
- **minor**, `app/src/main/res/values/strings_design.xml`: Two new keys (design_scrim_close, design_sheet_pane) were added without regenerating the debug folders values-en and values-en-rXZ, as the rules require (they are generated). After the merge, the next local build will refuse to run: tools/local-build.ps1 Test-StretchedLanguage runs make-test-language.ps1 -Check and stops when the folders are stale. StretchedLanguageTest and strings-check will see the gap too. *Fix asked:* Lead: after merging, run powershell -NoProfile -ExecutionPolicy Bypass -File tools\make-test-language.ps1 once, then build. No hand edits.
- **minor**, `app/src/main/java/dev/pocketagent/app/ui/components/PocketDialog.kt`: For S3, the dialog's scrim is the clickable BoxWithConstraints that is the PARENT of the panel. Now that it has contentDescription "Close" and Role.Button, TalkBack reads 'Close, button' first in every ConfirmDialog, before the title and the question, because a parent comes before its children. traversalIndex cannot put a parent after its own child. It is not worse than the unlabelled button before, but the sheet's ordering (the content read first) is not reached here. *Fix asked:* Do it the way PocketSheet does: put the clickable, labelled scrim in its own full-size sibling Box placed before the padded BoxWithConstraints (which is then no longer clickable), give that Box semantics { traversalIndex = 1f }, and give the outer Box isTraversalGroup = true. Optionally give the PixelPanel semantics { paneTitle = title } so the dialog is announced by its title.

Reviewer's notes:

I checked the working tree by reading the code; nothing was built or run. Files: app/src/main/java/dev/pocketagent/app/ui/components/{PocketWindow,SheetSlide,PocketSheet,PocketDialog}.kt, ui/text/{AppLocale,TextsCompose}.kt, res/values*/strings_design.xml, and the tests app/src/test/java/dev/pocketagent/app/ui/components/SheetSlideTest.kt and app/src/test/java/dev/pocketagent/app/text/AppLocaleTest.kt.

S1: the new structure is sound and I found no blocker.
- The visible position is a fraction that the layer turns into pixels at draw time.
- With animations off, the sheet is at rest from the first frame (the robot runs at scale 0, so nothing changes there).
- With animations on, it starts one height down and glides up in LaunchedEffect(drive). The key is only the count of started motions, so a measured height can no longer cancel the glide.
- A second close request returns null, so a double tap on an action, or Back while closing, is ignored. A drag while closing is ignored too.
- Closing calls onDismiss and then the queued `then` actions, in the same order as before.
- Re-open starts fresh, because remember is reset when the sheet leaves composition. Rotation with the activity recreated re-enters normally; a resize without recreation adapts, since the fraction does not depend on a fixed height.
- If a motion is cancelled by a new drive, apply(to) is not reached (join throws), and the phase guards cover it anyway.

S2: the analysis is right.
- A Dialog's own AndroidComposeView provides context, configuration and resources again, so the override stopped at the window edge.
- PocketWindow re-provides context, configuration, resources, layout direction and density from outside the window.
- LocalView is not re-provided and stays the dialog's own, so DialogWindowProvider still works, and BackHandler finds the dialog's dispatcher through the view first.
- LocalizedContext wraps the Activity, so activity-result launchers (the attachment sheet) still find it.
- Texts.getAt resolves through the locale passed in, so t() is right through appLocale -> LocalAppLanguage even without the re-provide.
- Grep confirms no other Dialog(, AlertDialog(, ModalBottomSheet(, Popup( or DropdownMenu( in the app outside PocketWindow.

S3: the sheet's scrim gets a label, Role.Button and traversalIndex 1, and the panel becomes a traversal group with paneTitle and a dismiss action.

Area and rules: the changes stay inside the area's files, and the strings have all three languages (rXZ and values-en untouched; the lead must regenerate them, see findings).

Tests: they are real. The pure SheetSlide rules cover animations on and off, an unmeasured height, drag, dismiss and re-open. playSlide is tested with no spec, with a dead clock and with a ticking clock, using a MonotonicFrameClock in runBlocking. One case is missing: frames that stop after a partial move (finding 1).

Compile: I read everything for compile risk and found none: the imports match the use, internal playSlide is visible to tests in the same module, and LocalResources already exists (AppRoot uses it).


### The repair

CAUSE
- S1, sheet invisible at animation scale 1 (`ui/components/PocketSheet.kt`):
  - I could not reproduce this without a device, so the cause is still a reading of the code, not proof.
  - The old sheet started hidden and became visible only through a `LaunchedEffect` keyed on the measured height (`heightPx > 0`).
  - The reviewer is right that moving the effect "inside the window" changes nothing about frames. A Compose `Dialog` is a child composition of the caller's Recomposer, so both sides use the same frame clock. I had wrongly claimed otherwise.
  - The most likely real cause is that the height can go to 0 and back during window or inset layout. That cancels the opening glide and leaves the sheet stuck below the screen.
  - The new effect is keyed only on `drive`, the count of started motions, so a layout pass can no longer cancel it. That removes this cause.
  - If the frame clock is the real cause after all, only the watchdog saves the sheet.
- S2, Turkish text in dialogs: a Compose `Dialog` is its own window with its own `AndroidComposeView`. That view provides context, configuration, resources, layout direction and density again from the phone's context. `PocketWindow` now provides the app's own values again inside. `t()` and `tn()` follow `LocalAppLanguage` first, so they stay right even without the re-provide.
- S3, scrim without a spoken label: both the sheet scrim and the dialog scrim had a bare `clickable` with no label, role or description.

CHANGED
- `ui/components/PocketSheet.kt`:
  - `playSlide` now looks for progress. `isStalled` is gone. A frame counter wraps `apply`, and every `stallMs` the watchdog checks that at least one frame arrived since its last look. If none did, whether the clock never ticked or stopped half way, it calls `onStall`, cancels the animation and applies `to`.
  - `playSlide` has a new optional `onStall` parameter. `PocketSheet` passes a private `logStall()`, which writes a warning to the app log (`AppLog.w("Sheet", ...)`).
  - The comment above the effect and the KDoc are corrected. They no longer claim the window has a different clock.
- `ui/components/PocketDialog.kt` (S3 ordering fix, finding 4):
  - The outer box is now a traversal group.
  - The clickable, labelled scrim is its own full-size sibling Box before the padded box, with `traversalIndex = 1f`. The padded `BoxWithConstraints` is no longer clickable and no longer the scrim's parent.
  - Taps in the padding fall through to the scrim. The panel's own empty click still absorbs taps on the panel.
  - The panel has `paneTitle = title`, so the dialog is announced by its title.
  - New imports: `isTraversalGroup`, `paneTitle`, `traversalIndex`.
- `ui/components/SheetSlide.kt`: removed `isStalled` (now unused) and updated the `STALL_MS` comment.
- `ui/components/README.md`: added a note that a sheet popping in after about 1.5 s means the window's frame clock is the real cause.
- `res/values*/strings_design.xml`: untouched this round.

TESTS
- `SheetSlideTest` is adjusted to the new `playSlide` signature.
- New: `framesThatStopAfterAPartialMoveAreFinishedByForceToo`.
  - It uses a `TickThenStopClock` that delivers two frames and then awaits cancellation.
  - It asserts that exactly two frames were delivered, `onStall` ran once, and the slide ends at rest.
- New: `framesThatStopAfterAPartialMoveStillLetTheSheetClose`.
- The dead-clock test also asserts `onStall` fired once, and the ticking-clock test asserts it did not fire.
- Nothing was compiled or run, because Gradle is not allowed in this stage.

REQUESTS
- Finding 3 (stale generated folders): after merging, run `powershell -NoProfile -ExecutionPolicy Bypass -File tools\make-test-language.ps1` once before the next local build. I did not run it, because other agents are editing string files at the same time and the output would go stale again. The new keys `design_scrim_close` and `design_sheet_pane` already exist in `values`, `values-tr` and `values-fr`.
- Unchanged from the earlier report: the robot needs a scenario that opens a sheet at animation scale 1 and expects the tag `sheet`.
- Unchanged from the earlier report: the OK buttons at `SettingsScreen.kt:135`, `SettingsScreen.kt:212` and `BotShareUi.kt:232` lack the tag `dialog.confirm`.

NOT DONE
- The exact trigger of S1 is still unproven.
- `Texts.get(context, ...)` (non-Compose) still ignores the debug-link language override. It is outside S2.

RISKS (for the phone proof)
- Sheets at scale 1:
  - Open New chat, Chat Controls, tool output and the message menu. Each must rise visibly.
  - A sheet that pops in after about 1.5 s instead of gliding means the frame clock is still the cause. `app.log` then has the line "Sheet: no frame moved the sheet for 1500 ms".
  - Also check Back, a scrim tap, the grabber, a drag with fling, a short drag that returns, and close with the keyboard up.
- Dialogs: ConfirmDialog should read "Close, button" after its title and question, and be announced by its title as a pane.
- Language inside dialogs: with the phone on Turkish and the app on English, all dialog and sheet text must be English. Repeat for French, `en-XZ` and rtl=1.
- Compile risk: nothing was compiled. The test clocks and the `::logStall` reference are the most likely places to need a fix.

Files are in `app\src\`:
- `main\java\dev\pocketagent\app\ui\components\PocketSheet.kt`
- `main\java\dev\pocketagent\app\ui\components\PocketDialog.kt`
- `main\java\dev\pocketagent\app\ui\components\SheetSlide.kt`
- `main\java\dev\pocketagent\app\ui\components\README.md`
- `test\java\dev\pocketagent\app\ui\components\SheetSlideTest.kt`


## Area ENGINE

### The fix report

All six defects are fixed in code, but none of it has been compiled or run. This stage forbade Gradle and the phone, so every claim below comes from reading the code and the Hermes source.

The working tree already held most of this work when I started, uncommitted, apparently from an earlier interrupted run of the same task. I checked each piece against the Hermes source and the existing tests, and I made no new edits this time.

### CAUSE

**E1 (approvals unanswerable).** `HermesApi.resolveApproval` sent only `{choice, request_id}`. In session-authority mode (always on in this image) Hermes requires exactly these three keys, or it answers 400 `invalid_params`:
- `request_id`;
- `execution_generation`, an integer equal to the run's generation;
- `choice`, one of `once`, `session`, `always`, `deny`.

The contract, with file and line, in `dist/hermes-src/root/.hermes/hermes-agent/gateway/`:
- `platforms/api_server_authority_runs.py:117-136` (`respond_run`):
  - line 123 enforces the exact key set;
  - line 125 checks the run is started and the generation is an `int` equal to `row['generation']`, else 409 `stale_generation`;
  - the prompt id must still be pending, else 409 `approval_not_pending`.
- `platforms/api_server_runs.py:1358-1372` (`_handle_run_approval`): when `session_authority` is on, the body goes straight to `respond_run`.
- The generation comes from the `approval.request` SSE event itself:
  - `session_pending_controls.py:27-29` puts `execution_generation` in the prompt;
  - `session_api_turn.py:305-309` forwards every prompt field, with `prompt_id` renamed `request_id`;
  - `api_server.py:89-100` keeps all of them.
- Fallback source: `GET /v1/runs/{id}` returns `execution_generation` and `pending_controls` (`api_server_authority_runs.py:97, 88`).
- Allowed choices are set in `api_server.py:83-87`. The notification action goes through the same `engine.resolveApproval`, so it is fixed as well.

**E2 (card stuck, session blocked about 3 minutes).**
- After a failed answer the card was restored but the failure only went into the chat as a late error bubble.
- Stop only closed our SSE stream. Hermes's agent thread stays blocked on the approval wait (`tools/approval_gateway_wait.py:72-99`) until it is interrupted or `approvals.timeout` runs out. The default timeout is 300 s (`tools/approval_context.py:327`); the observed ~3 min is the same effect.
- The next message to that chat session queued behind the blocked run.

**E3 (raw chunk in the chat).** `ChatEngine` showed `ev.raw`, the whole chunk, for every `StreamError` (the old line 646). The message was never used. The HTTP-error path showed the raw body the same way.

**E4 (tool sheet shows no output).** Hermes does not send tool output on this API.
- `platforms/api_server_openai_routes.py:965-987`: `_on_tool_start` sends tool, emoji, label (a command preview), `toolCallId` and `running`. `_on_tool_complete` receives `function_result` but sends only tool, `toolCallId` and `completed`.
- So the output never reaches the app. The model's answer is the only place it appears.

**E5 (practice tokens in the owner's numbers).** `ChatEngine` recorded usage for every turn. The practice flag was never consulted, neither for the day and model totals nor for the budget block and warning.

**E6 (card squeezes the input).** The approval card had no height limit.

### CHANGED (my area)
- `api/ApprovalContract.kt` (new): the exact body builder, the allowed choices, a parser for the run state, and tests for "stale" versus "gone" answers.
- `api/ErrorText.kt` (new): `ApiErrorText` turns the usual error shapes into one short line (no line breaks, at most 240 characters, nested `'message': '…'` unwrapped).
- `api/HermesApi.kt`:
  - `resolveApproval(runId, choice, requestId, generation)` sends the three keys;
  - if the id or generation is missing, it reads them once from `GET /v1/runs/{id}`;
  - on 409 `stale_generation` it retries once with the fresh generation;
  - new `runState` and `stopRun` (`POST /v1/runs/{id}/stop`).
- `api/Sse.kt`: `ChatEvent.Approval.generation`; `StreamError.message` now defaults to an empty string, never the whole chunk.
- `data/ChatEngine.kt`:
  - `PendingApproval` gains `generation` and `errorLine`. A failed answer puts the card back with a readable reason and it can be answered again.
  - `stop()` while a permission request waits now denies it, asks Hermes to stop the run, then closes the stream. Events that arrive after Stop are dropped, and the deny and stop calls are guarded so the stream is always closed.
  - Stream errors and HTTP errors become one readable line. The raw text goes only to the redacted log.
  - New `isPractice` and `interruptRun` constructor parameters. The default `isPractice` is `GuestPrefs.practiceActive`.
  - A practice turn keeps its per-message numbers but never touches `UsageStore`, the budget block or the budget warning.
- `ui/chat/ChatInput.kt`: the card shows `errorLine` (tag `chat.approval.error`).
- `ui/chat/ChatInputArea.kt`: the card sits in a height-limited scroll box, 40% of screen height, or 22% with the keyboard open.
- `ui/chat/ToolOutputSheet.kt`: the area is labelled "Command", the button now says "Copy command", and an honest note says Hermes sends no tool output. The old "live" line is removed.
- `res/values*/strings_engine.xml` and `strings_chat.xml`: new strings in English, Turkish and French.
  - Engine strings: `eng_approval_card_error`, `eng_approval_gone`, `eng_err_reason`, `eng_err_unknown`, `eng_err_http`, `eng_err_http_plain`.
  - Chat strings: `ui_chat_tool_command`, `ui_chat_tool_copy_command`, `ui_chat_tool_no_output_note`.
- Existing tests were adjusted to the 4-argument approve lambda and the new error text (`ChatEngineTest`, `ChatEngineTrailingTest`).

### TESTS
- New `ApprovalContractTest` covers the exact three-key body and the integer generation, the event decoding (including a missing or null generation), the run-state parser, and stale versus gone.
- New `ApiErrorTextTest` has 15 tests for the error shapes, nested messages, HTML, truncation and empty input.
- New `EngineProofFixesTest` covers:
  - the generation and request id reaching the approve call;
  - a failed answer showing a card line, then a repeat answer working;
  - the Stop order deny, then stop, then cancel, with nothing added afterwards;
  - Stop still ending the stream when both calls throw, and plain Stop unchanged;
  - an error chunk giving one line with no raw JSON;
  - HTTP errors from JSON, `{"detail"}`, text and HTML;
  - practice turns not counted and not blocked by the budget, while real turns are still counted and warned.

### REQUESTS (files I do not own)
- `PocketApp.kt`, `ChatEngine(...)` call: pass `isPractice = { runtime.lastGuestOptions.practice || GuestPrefs.practiceActive }`, the same test as the provider lambda. The engine default (`GuestPrefs.practiceActive` alone) works, but this is more accurate in the few seconds around a switch.
- Optional, in `rootfs/smoke/mock_llm.py` (not mine to change): the FAIL reply has an `error.message`. If the phone proof still shows no message for FAIL, Hermes is hiding it in `finish_chunk`; the app then shows the plain "reported an error without saying why" sentence.

### NOT DONE
- Nothing was compiled or run. Beyond that, the only part I skipped is the approval notification: its receiver needed no edit because it calls `engine.resolveApproval`.
- I did not prove that the second approval card after "hello again" came from Hermes resuming the unanswered tool turn. The mock only reads the last user message, so it is not the mock.

### RISKS (what the phone proof must look at)
1. ASK_APPROVAL, then each of Allow once, Allow this session, Always allow and Deny. Expect HTTP 200 and the turn continuing; with Deny, the command must not run.
2. A failing answer: the card must keep its error line and stay answerable. Stop must then free the chat, and the next message must go through with no 3-minute wait and no repeated card.
3. A stale-generation retry, and the fallback to `GET /v1/runs/{id}` when the event carries no generation.
4. FAIL in the practice model: one readable line, with Retry and Copy still working.
5. A plain Stop during streaming must still keep the partial text. Events after Stop are now dropped, so an in-flight usage chunk is lost; this is acceptable but worth a look.
6. Settings > Usage must not grow after practice turns, while a real turn still counts.
7. The approval card with the keyboard open, and a very long command (the card scrolls and the input keeps its height).
8. The `/v1/runs/{id}/stop` call on a `chatcmpl-*` run id. The Hermes source says aliases are handled (`run_admission`), but this is untested.


### The review (needs-fixes)

- **major**, `app/src/main/java/dev/pocketagent/app/data/ChatEngine.kt`: Stop does not reach Hermes while an approval answer is still being sent, so E2 can still happen when the answer is slow or fails. resolveApproval() removes the card from _approvals before its network call starts. That call can take up to 15 s to read, plus a 5 s GET and one retry. If the user presses Stop during that time, stop() finds no waiting card and takes the plain path, r.handle?.cancel(): the stream closes and stopRun is never called. In session-authority mode the disconnect does not interrupt the authority's agent (that is the 'SSE client disconnected' then 3-minute block seen on the phone). When the answer then fails, runs[convId] is already gone, so the failure branch only adds a chat line, and Hermes keeps waiting until approvals.timeout (300 s, tools/approval_context.py). If the answer succeeds instead, the approved command runs after the user pressed Stop. *Fix asked:* Track answers in flight: add `private val answering = ConcurrentHashMap<String, PendingApproval>()`. In resolveApproval() put p in it before scope.launch and remove it in a finally block. In stop() use `val waiting = _approvals.value[convId] ?: answering[convId]`, and when the request came from `answering` skip the deny but still call interruptRun(runId) before handle.cancel(). In the failure branch of resolveApproval, also call interruptRun(p.runId) when run == null, not only when run.stop is true. A stop on an ended run is harmless: Hermes returns the run projection or not_found. Add a test: an approve lambda blocked on a latch, then stop(), then assert interruptRun ran before the stream's cancel.
- **minor**, `app/src/main/java/dev/pocketagent/app/data/ChatEngine.kt`: Stop sends deny first and only then the interrupt. A deny lets Hermes's agent resume, so it starts another model call (real tokens with a real provider) before the interrupt arrives. Those tokens are never counted, because events after Stop are now dropped, Usage included. Hermes's interrupt alone already ends the approval wait without running the command: tools/approval_gateway_wait.py `_poll_event` returns 'interrupted' ('Any interrupt ... ends the wait fail-closed: the command does not run'), and stop_run calls authority.interrupt (gateway/platforms/api_server_authority_runs.py, stop_run, status 'started'). *Fix asked:* Make interruptRun return Boolean, true when HttpResult.ok (`{ runId -> api.stopRun(runId).ok }`). In stop(), call interruptRun first and send the deny only when it returned false. Update the expected order in EngineProofFixesTest (stop, then cancel; deny only when stop fails).
- **minor**, `app/src/main/java/dev/pocketagent/app/api/HermesApi.kt`: While a card waits, Stop closes the stream only after resolveApproval('deny') and stopRun have both returned. Each uses postJson with a 15 s read timeout, and the deny may add a 5 s GET and a retry. With a hung or slow Hermes, which is exactly the E2 situation, Stop can take close to a minute while the chat stays busy and the input is blocked. *Fix asked:* Give the calls made on Stop short timeouts. Add a timeout parameter to postJson (for example open(path, "POST", true, 3000, 4000) for stopRun and for the deny sent on Stop). Alternatively, wrap the deny and stop part of stop() in withTimeoutOrNull(5000) and always call handle.cancel() afterwards.
- **minor**, `app/src/main/res/values/strings_stream.xml`: ToolOutputSheet no longer uses stm_tool_sheet_live or stm_tool_sheet_copy (owned by STREAM, in three locales). tools/strings-check.ps1 now warns that they are unused, and fails with -FailOnUnused. 'Copy output' would also be the wrong label now. *Fix asked:* Request to the lead or the STREAM owner: delete stm_tool_sheet_live and stm_tool_sheet_copy from values, values-tr and values-fr strings_stream.xml, then regenerate values-en-rXZ with tools/make-test-language.ps1.

Reviewer's notes:

I checked the Hermes contract in dist/hermes-src myself, and the report quotes it correctly:
- respond_run (api_server_authority_runs.py:115-136): line 123 requires the exact key set, line 125 requires an int equal to row['generation']; refusals map to 400 for invalid_params and 409 for the rest (store_error_response, lines 18-34).
- The execution_generation in the approval.request event comes from session_pending_controls.py:27-29 and session_api_turn.py:305-309. GET /v1/runs/{id} also returns it (line 97).
- The choices once, session, always and deny are in api_server.py:83-87. respond_run does not check the choice in authority mode.
- chatcmpl run ids resolve through _run_admission_aliases, which are popped when the turn ends, so stopRun must come before the stream closes. The code does this.

E4 is correct: _on_tool_complete (api_server_openai_routes.py:978-983) drops function_result.

E3 is correct: the old engine showed ev.raw. Hermes's error finish chunk carries error.message and error.type (lines 1231-1234); a crash chunk carries none, so the "without saying why" line is right there.

E5: practice is decided once at turn start, and the budget block, warning and statistics are all skipped. The PocketApp request (isPractice = { runtime.lastGuestOptions.practice || GuestPrefs.practiceActive }) is sensible and should be done by the owner of PocketApp.kt.

E6: the card has no fillMaxHeight, weight or lazy list in the vertical direction, so verticalScroll inside heightIn is safe.

The tests are real: they use a fake backend, a real ChatRepository and a real UsageStore. Nothing outside the area was edited. The new strings exist in all three locales. The notification path goes through engine.resolveApproval unchanged.


### The repair

All four review findings are handled, but nothing was compiled or run, because Gradle was off limits in this stage. The three code fixes (the major and two minors) are reasoned from the code only; the fourth finding is a request to the lead, below. The earlier E1 to E6 fixes are unchanged from the report you quoted. Only these two files changed in main code and one in tests.

## CAUSE

**Major (Stop during an answer in flight).** `resolveApproval()` removed the card before its network call started. A Stop pressed during that call therefore saw no card and took the plain path of closing our stream only. Hermes was never asked to interrupt the run and kept waiting until `approvals.timeout`. This was the E2 symptom again.

**Minor 1 (deny before interrupt).** A deny lets Hermes's agent resume, so it can start another model call before the interrupt arrives. Those tokens are dropped from usage because events after Stop are ignored. The interrupt alone already ends the wait without running the command (`tools/approval_gateway_wait.py` `_poll_event`, and `stop_run` in `api_server_authority_runs.py`). I accepted this finding as written.

**Minor 2 (slow Stop).** Stop waited for the deny and the interrupt in turn. Each had a 15 s read timeout, and the deny could add a 5 s GET and one retry, so Stop could take about a minute against a hung Hermes.

**Minor 3 (unused strings).** Agreed; it is in files I do not own (see REQUESTS).

## CHANGED

`app/src/main/java/dev/pocketagent/app/data/ChatEngine.kt`:
- **In-flight tracking:**
  - New `answering` map (`ConcurrentHashMap<String, PendingApproval>`). `resolveApproval()` puts the request in before the call and removes it in a `finally`, after the card has been put back on failure, so Stop always sees either the card or the in-flight entry.
  - `stop()` now uses `waiting ?: answering[convId]`.
- **Interrupt first:**
  - `interruptRun` now returns `Boolean`, true when Hermes accepted it. Stop calls it first.
  - A deny is sent only if a card was still showing and the interrupt failed. If the answer was already in flight, no deny is sent.
  - Then the stream is closed.
- **Bounded calls:**
  - New `boundedCall()` runs a blocking call on another coroutine and waits at most `STOP_CALL_MS` (5 s). Both calls on Stop go through it, so `handle.cancel()` is always reached and a failure or timeout is only logged.
- **Failed answer:**
  - If the answer fails and the run ended or was stopped, `interruptRun(runId)` is now called in both cases. Before, it was only called when the run had been stopped.
  - If the run ended, the error line is still added to the chat.
- The secondary constructor now passes `{ runId -> api.stopRun(runId).ok }`.

`app/src/main/java/dev/pocketagent/app/api/HermesApi.kt`:
- `postJson` takes optional `connectMs` and `readMs`, defaulting to 5000 and 15000.
- `stopRun` uses 2000 and 4000. The approval answer keeps its normal timeouts, since the deny on Stop is now bounded by the engine.

## TESTS

All in `app/src/test/java/dev/pocketagent/app/EngineProofFixesTest.kt`:
- The helper's `interruptRun` is now `(String) -> Boolean`, default true, and the existing lambdas return true.
- The Stop test is renamed `stopWhileAPermissionRequestWaitsStopsTheRunFirstAndClosesTheStreamWithoutADeny`. It now expects `stop:<run>`, then `cancel`, with no deny.
- New `stopDeniesTheRequestOnlyWhenHermesRefusesTheInterrupt` expects `stop`, then `approve:...:deny:...`, then `cancel`.
- New `stopWhileAnAnswerIsStillBeingSentInterruptsTheRunBeforeTheStreamCloses`:
  - The approve lambda blocks on a latch while the answer is "in flight".
  - It then calls `stop()` and expects `approve:once`, then `stop:<run>`, then `cancel`, with no deny.

## REQUESTS

1. For the STREAM owner or the lead, in `values`, `values-tr` and `values-fr` `strings_stream.xml`:
   - delete `stm_tool_sheet_live` and `stm_tool_sheet_copy`;
   - then regenerate `values-en-rXZ` with `tools/make-test-language.ps1`.
2. For the owner of `PocketApp.kt`, in the `ChatEngine(...)` call: pass `isPractice = { runtime.lastGuestOptions.practice || GuestPrefs.practiceActive }`. This request carries over from the first report; the engine default already works.

## NOT DONE

- Nothing was compiled or run.
- I did not touch the approval-notification receiver, because it goes through `engine.resolveApproval`.

## RISKS

The phone proof needs to check these:
1. **Stop while a card waits.** Hermes receives `POST /v1/runs/<id>/stop` and answers 200, the command does not run, and the chat's next message goes through immediately.
2. **Stop mid-answer.** Press Stop right after tapping Allow. The log must show the stop call even though the card was already gone.
3. **Stop on a `chatcmpl-*` run id.** The Hermes source says aliases resolve until the turn ends, and the stop is sent before the stream closes, but this is untested. If Hermes answers 404 or `not_found`, the deny fallback runs.
4. **Stop duration.** With Hermes hung, Stop should take at most about 5 s per call.
5. **Unchanged paths.** A plain Stop during streaming still keeps the partial text, and each of the four answers (once, session, always, deny) still gets HTTP 200.


## Area MISC

### The fix report

Both defects are fixed in source, but the generated language folders are still the old ones. Nothing was run: no Gradle, no PowerShell and no phone, as instructed.

**Run this before any build, or CI will fail on both checks:**
`powershell -NoProfile -ExecutionPolicy Bypass -File tools\make-test-language.ps1`
It rewrites all 24 generated files in `app/src/debug/res/values-en` and `values-en-rXZ`. Then run `tools\make-test-language.ps1 -Check`, `tools\strings-check.ps1` and `tools\make-test-language.ps1 -SelfTest`; all three should pass.

**CAUSE**

*M1, the stretched name in a normal English interface*
- `tools/make-test-language.ps1` used to skip the twin overlay's names (`app_name`, `srf_ask_agiri`, `srf_tile_label`) in `values-en` only, not in `values-en-rXZ`. So exactly those three keys existed in the stretched folder but not in the plain English one.
- I compared the key sets of the 12 file pairs, and exactly those three differ.
- The resolution rule I relied on is that Android matches a folder by language only. A request for `en` or `en-CA` accepts `values-en-rXZ`, because the region of a folder is ignored in matching. Among the folders that match:
  - the region-less `values-en` beats `values-en-rXZ`;
  - either of them beats the plain `values` folder.
- A key in both folders is safe, because `values-en` answers. A key in neither is safe, because the default answers. A key only in `values-en-rXZ` leaks.
- That is why `t(R.string.app_name)` at `RuntimeSection.kt:223` showed "[ÅgîrîÅî]". The same leak hit the Quick Settings tile label and the "Ask Agirî" text-selection entry, and it hits the twin build too. The twin's own `values/app_name` also loses to the stretched folder.
- The region-less-beats-region rule is the reviewer's reading plus your phone proof, where every other key came out plain English. I did not verify it in the Android source.

*M2, the About mascot*
- At `RuntimeSection.kt:220` the mascot was a plain `Modifier.clickable { petTaps++ }`, with no label and no role.

**CHANGED**
- `tools/make-test-language.ps1`:
  - The skip list now applies to both folders, so they always hold the same keys.
  - New functions `Get-FolderKeys` and `Test-SameKeys`; `Test-Fresh` (so `-Check`) fails if the key sets differ.
  - Self-test gains five assertions: the stretched folder skips the twin names, both folders hold the same keys, an injected stretched-only key is reported, `Test-Fresh` reports it, and regenerating clears it.
  - `AlgoVersion` goes from 3 to 4, which makes every generated file count as stale until you regenerate.
  - The header explains the rule.
- `tools/strings-check.ps1`:
  - It reads the twin overlay names and no longer expects them in the stretched folder.
  - It errors if the stretched folder holds a twin name, a key that `values-en` lacks, or the reverse.
  - Header doc updated.
- `RuntimeSection.kt`:
  - The mascot is now `.clickable(role = Role.Button).semantics { contentDescription = t(R.string.ctl_about_pet_desc) }`, with three new imports.
  - There is deliberately no click label, so the five-tap secret is not revealed.
- `strings_control.xml` in `values`, `values-tr` and `values-fr`: new key `ctl_about_pet_desc`.
  - English "Pêt, the newt mascot", Turkish "Pêt, semender maskot", French "Pêt, la mascotte triton".
  - The English one has a translator note, so the strings-check warning ratchet does not worsen.

**TESTS**
- New `app/src/test/java/dev/pocketagent/app/text/StretchedLanguageTest.kt` with four tests:
  - The two generated folders hold the same keys.
  - No twin overlay name is in either generated folder.
  - `app_name` is never in the stretched folder.
  - `values-en` equals the default English text exactly, form by form.
- It will fail until the folders are regenerated, which is intended.
- `TwinOverlayTest` and `ResourceParityTest` are unchanged and should still pass.

**REQUESTS**
- None are required. Nothing in `DebugLinks.kt` needs to change, because the `en-XZ` tag and the `lang` extra are untouched.
- Optional hardening: if the phone proof still shows stretched text in a normal English screen, move the stretched language to a distinct language code such as `b+qaa`. That would touch `TestLanguage.TAG`, `DebugLinksTest`, the scenario files and the strings-check tag. English plural rules would be lost under that code.

**NOT DONE**
- I did not regenerate the folders (stage rule).
- First-list items 3 to 8 are outside my files, and I left them:
  - 3: the gallery sheet scrim has no label.
  - 4 and 5: bidi isolation in chat, Hermes, Usage and the terminal.
  - 6: the switch thumb in a mirrored layout.
  - 7: stored bot data and the gallery.
  - 8: smoothness.
- Second-list items I did not touch:
  - The Turkish text in dialog windows, and the dialog "OK" button with no tag.
  - The Pêt note on copy.

**RISKS, what the phone proof must look at**
- Settings > About in a debug build, with the app language set to English while the phone is tr-TR/en-CA: the name must read "Agirî" (owner-debug) or "Agirî Test" (twin).
- The launcher label, the Quick Settings tile and the text-selection menu entry must show the plain names.
- The stretched sweep must still bracket everything except those three names. Bracketed text is the proof that the `values-en` rule still holds.
- Turn on TalkBack: the About mascot should announce "Pêt, the newt mascot, button" and five taps should still open the stats.
- A `phone-test` run of `sweep-labels` should no longer report the three mascot findings.

Changed or added files, all under ``:
- `tools\make-test-language.ps1`
- `tools\strings-check.ps1`
- `app\src\main\java\dev\pocketagent\app\ui\settings\RuntimeSection.kt`
- `app\src\main\res\values\strings_control.xml`
- `app\src\main\res\values-tr\strings_control.xml`
- `app\src\main\res\values-fr\strings_control.xml`
- `app\src\test\java\dev\pocketagent\app\text\StretchedLanguageTest.kt`


### The review (ok)

- **minor**, `app/src/main/res/values-tr/strings_control.xml`: The new Turkish label 'Pêt, semender maskot' is missing the possessive ending, so a native speaker hears it as two nouns side by side. 'Pêt, semender maskotu' is the natural form. *Fix asked:* Change ctl_about_pet_desc in values-tr to: <string name="ctl_about_pet_desc">Pêt, semender maskotu</string>

Reviewer's notes:

I checked this myself from the diff, the generated folders and the scripts, not from the report.

(1) The stated cause is right. The current app/src/debug/res/values-en-rXZ has app_name, srf_ask_agiri and srf_tile_label. The generated values-en has none of them, because the old generator skipped the twin names only for the plain copy. Every other key is in both folders. That matches the phone proof: every other key came out plain English, and app_name came out stretched.

How Android picks the folder: it matches by language and ignores the region ("Don't consider country and variants when deciding matches"), so values-en-rXZ is a candidate for every English request. Among the candidates, a folder that names the language beats the plain values folder. The region-less values-en beats rXZ: the region-less folder stands for the main English, and XZ is an unknown region with no parent. The proof confirms that last rule in practice.

The fix: the skip list now applies to both generated folders, through the same $skip from Get-TwinNames, which reads every twin values* folder. Test-SameKeys inside -Check, the new strings-check rules and StretchedLanguageTest all enforce that the two folders hold the same keys. A key that is missing from both falls back to the plain values folder, which is safe. Owner-debug, which has no twin overlay, gets the main app_name "Agirî". The twin gets its overlay in values, values-tr and values-fr.

Nothing in this path depends on recomposition, rotation or the animation scale: resources resolve the same way every time.

M2: the Mascot is PixelImage(modifier.size). Grep finds no semantics or content description in PixelImage, so nothing is merged twice. clickable(role = Role.Button) plus semantics { contentDescription } on the same node gives "Pêt, the newt mascot, button". There is no click label, so the five-tap secret stays hidden, and five taps still count.

(2) Nothing that passed in the proof breaks. leak-allow.txt already allows ^Agir, so the sweep will not flag the plain names in stretched mode. a11y-allow.json has no mascot entry to remove.

(3) Every change stays inside the area's files. No generated folder was edited by hand. The new key has English, Turkish and French, and the English one has a translator note.

(4) The tests are real. ResXml.mainDir() is src/main, so its parent reaches debug/res and twin/res. The tests compare file:key sets and the delivered texts form by form. They fail until the folders are regenerated, which is intended.

The integrator must run these before building or pushing, or CI fails on strings-check and -Check (AlgoVersion is now 4):
powershell -NoProfile -ExecutionPolicy Bypass -File tools\make-test-language.ps1
Then run tools\make-test-language.ps1 -Check, tools\strings-check.ps1 and tools\make-test-language.ps1 -SelfTest.

(5) Not applicable: this is area MISC, not ENGINE.


### The repair

CAUSE

- M1, the stretched name in a normal English interface (fixed in source, not yet regenerated).
  - `tools/make-test-language.ps1` skipped the twin overlay names (`app_name`, `srf_ask_agiri`, `srf_tile_label`) only when it wrote `values-en`. It still wrote them into `values-en-rXZ`. So those three keys existed in the stretched folder but not in the plain English one.
  - The resolution rule I relied on: Android matches a folder by language and ignores the region. A request for `en` or `en-CA` therefore accepts `values-en-rXZ`. Among the matching folders, the region-less `values-en` beats `values-en-rXZ`, and either beats the plain `values`.
  - A key in both folders is safe, because `values-en` answers. A key in neither is safe, because `values` answers. A key only in `values-en-rXZ` leaks. That is how `t(R.string.app_name)` at `RuntimeSection.kt` line 223 showed the stretched text. The Quick Settings tile label and the "Ask Agirî" selection entry leaked the same way, and so did the twin build.
  - The region-less-beats-region part comes from the reviewer's reading plus the phone proof, where every other key came out plain English. I did not verify it in the Android source.
- M2, the About mascot. At `RuntimeSection.kt` line 220 it was a plain `Modifier.clickable { petTaps++ }`, with no label and no role.

CHANGED

- `tools/make-test-language.ps1`:
  - The skip list now applies to both generated folders, so they always hold the same keys.
  - New `Get-FolderKeys` and `Test-SameKeys`. `Test-Fresh` (so `-Check`) fails if the key sets differ.
  - Five new self-test assertions.
  - `AlgoVersion` goes from 3 to 4, so every generated file counts as stale until it is regenerated.
- `tools/strings-check.ps1`: it reads the twin overlay names and no longer expects them in the stretched folder. It errors if the stretched folder holds a twin name, or if its keys differ from `values-en`.
- `app/src/main/java/dev/pocketagent/app/ui/settings/RuntimeSection.kt`: the mascot now uses `.clickable(role = Role.Button).semantics { contentDescription = t(R.string.ctl_about_pet_desc) }`, with three new imports. There is no click label, so the five-tap secret stays hidden.
- `strings_control.xml` in `values`, `values-tr` and `values-fr`: new key `ctl_about_pet_desc`.
  - English: "Pêt, the newt mascot".
  - French: "Pêt, la mascotte triton".
  - Turkish: "Pêt, semender maskotu". This follows the reviewer's minor finding. I added the possessive ending and agree with it.

TESTS

- New `app/src/test/java/dev/pocketagent/app/text/StretchedLanguageTest.kt` with four tests:
  - The two generated folders hold the same keys.
  - No twin overlay name is in either generated folder.
  - `app_name` is not in the stretched folder.
  - `values-en` equals the default English text exactly.
- These fail until the folders are regenerated, which is intended. Nothing was run in this stage: no Gradle, no PowerShell and no phone.

REQUESTS

- None needed. `DebugLinks.kt` stays as it is, because the `en-XZ` tag and the `lang` extra are unchanged.
- Integrator, before any build or push (otherwise `strings-check` and `-Check` fail on CI):
  1. `powershell -NoProfile -ExecutionPolicy Bypass -File tools\make-test-language.ps1`. This rewrites all 24 generated files in `app/src/debug/res/values-en` and `values-en-rXZ`.
  2. `tools\make-test-language.ps1 -Check`
  3. `tools\strings-check.ps1`
  4. `tools\make-test-language.ps1 -SelfTest`
- Optional hardening, only if the phone proof still shows leaks: move the stretched language to a distinct code such as `b+qaa`. That would touch `TestLanguage.TAG`, `DebugLinksTest`, the scenario files and the strings-check tag. English plural rules would be lost under that code.

NOT DONE

- I did not regenerate the folders (stage rule).
- Items 3 to 8 of the first defect list are outside my files:
  - 3: the gallery sheet scrim has no label.
  - 4 and 5: bidi isolation in chat, Hermes, Usage and the terminal.
  - 6: the switch thumb in a mirrored layout.
  - 7: stored bot data and the gallery.
  - 8: smoothness.
- Second-list items I did not touch: Turkish text in dialog windows, the dialog "OK" button with no tag, and the Pêt note on copy.

RISKS (what the phone proof must look at)

- Settings > About in a debug build, with the app language set to English while the phone is tr-TR or en-CA. The name must read "Agirî" on owner-debug and "Agirî Test" on the twin.
- The launcher label, the Quick Settings tile and the text-selection entry must show the plain names.
- The stretched sweep must still bracket everything except those three names.
- With TalkBack on, the mascot must announce "Pêt, the newt mascot, button", and five taps must still open the stats.
- A `phone-test` run of `sweep-labels` should no longer report the three mascot findings.

Files changed, all under ``:
- `tools\make-test-language.ps1`
- `tools\strings-check.ps1`
- `app\src\main\java\dev\pocketagent\app\ui\settings\RuntimeSection.kt`
- `app\src\main\res\values\strings_control.xml`
- `app\src\main\res\values-tr\strings_control.xml`
- `app\src\main\res\values-fr\strings_control.xml`
- `app\src\test\java\dev\pocketagent\app\text\StretchedLanguageTest.kt`


## Integration

- **Tests:** 874 unit tests: 870 passed, 4 failed, 3 skipped. The 4 failures are the known Windows symlink tests (FileAndUserStateTest x1, TarExtractorTest x3), so exit code 2 counts as passed. Before this work 808 passed. The twin, owner-debug and twin builds all compiled on the first try. make-test-language -Check and -SelfTest pass. strings-check: errors 0, warnings 939 (baseline 939, not raised). lint-ui: ok (baseline untouched). runtime/ and rootfs/ did not change, so the launcher test and the mock self-test were not run.
- **Changed:** Applied: ENGINE request, PocketApp.kt (ChatEngine call now passes isPractice = { runtime.lastGuestOptions.practice || GuestPrefs.practiceActive }). ENGINE request, deleted the unused keys stm_tool_sheet_live and stm_tool_sheet_copy from values, values-tr and values-fr strings_stream.xml. Regenerated app/src/debug/res/values-en and values-en-rXZ with tools/make-test-language.ps1 (run twice: after the MISC change and after my two string-file edits). Fixes I made: (1) app/src/main/res/values/strings_chat.xml: three new keys (ui_chat_tool_command, ui_chat_tool_copy_command, ui_chat_tool_no_output_note) had no translator note, which pushed warnings to 942 over the 939 baseline, so I added notes. My first note used the kind 'label', which strings-check rejects, so I changed it to 'chip'. There were no compile fixes and no test fixes. Not applied: the optional robot scenario for sheets at animation scale 1 and the dialog.confirm tags on the OK buttons at SettingsScreen.kt:135 and :212 and BotShareUi.kt:232. Final builds: the owner-debug APK was built in the middle, and the twin was rebuilt last. The twin APK is at app\build\outputs\apk\debug\app-debug.apk (dev.pocketagent.app.debug, version 0.2.8-local1-test, code 801, SHA-256 c88f506738041c2a456a346a14c85f67e5d6586f0524db37a94e1fde031a0891). Nothing was installed and the phone was not touched.
- **Open:** The phone proof still has to check everything the three reports list under RISKS. The most important items: (a) sheets at animation scale 1 must rise visibly, and a sheet popping in after about 1.5 s with the log line 'Sheet: no frame moved the sheet for 1500 ms' means the real cause is still open; (b) dialog and sheet text in the app language when the phone is set to another language; (c) approvals with all four choices (HTTP 200 expected), plus Stop while a card waits or while an answer is in flight, and the stop call on a chatcmpl-* run id; (d) the stretched sweep must show plain app names (Agirî or Agirî Test) in English. Still unproven: the exact trigger of S1 and the premise that the region-less values-en beats values-en-rXZ in Android's resolution (support so far is only the earlier proof and the reviewer's reading). The Turkish possessive fix 'Pêt, semender maskotu' was already in place. Non-Compose Texts.get(context, ...) still ignores the debug-link language override (outside S2). Items 3 to 8 of the MISC list (gallery sheet scrim label, bidi isolation, switch thumb under mirroring, stored bot data, smoothness) are not handled. The unused-key and plural warnings in strings-check remain at the baseline.

## The review of the whole diff (needs-fixes)

- **major**, `app/src/main/java/dev/pocketagent/app/data/ChatEngine.kt`: Stop while a permission card waits can now finish the turn as a normal reply. stop() calls interruptRun first and closes our stream only after that call returns. Once Hermes accepts the interrupt it ends the turn on its own side, so the SSE stream can end with [DONE] before handle.cancel() runs. streamCompletion then returns ChatOutcome.Completed (HermesApi.kt:259-262 checks handle.cancelled only after the read loop), and if Hermes closes the stream without [DONE] it returns TransportError instead. Turn.onEvent drops the events but finishLocked does not drop the outcome. With Completed it calls repo.bumpUnread and emits TurnCompleted, which posts a reply notification and raises the unread count for the bubble written before the card. If that bubble had no text, it adds the eng_empty_reply NOTE. With TransportError it adds an ERROR line ('stream ended without [DONE]'). Before the fix, Stop cancelled the stream at once and the outcome was always Cancelled. EngineProofFixesTest does not catch this: its backends wait for handle.cancelled and return Cancelled, so the realistic path (Hermes ends the stream after the interrupt) is never tested. The 'nothing is added after Stop' assertion therefore passes for the wrong reason. *Fix asked:* At the top of Turn.finishLocked, replace the outcome: `val outcome = if (run.stop) ChatOutcome.Cancelled else outcome` (rename the parameter to `raw`), so a stopped turn never bumps unread, never emits TurnCompleted or TurnFailed and never adds the empty-reply or transport-error line. Tools that were running are then marked interrupted, as they should be. Add a test whose backend sends the approval event, waits until interruptRun has been called (a latch set inside the interruptRun lambda), then emits Finish and Done and returns ChatOutcome.Completed without waiting for cancel. Assert that no BOT, NOTE or ERROR message is added after the Stop, that unread is unchanged, and that no TurnCompleted event is emitted. Add a second case that returns TransportError.
- **minor**, `app/src/main/java/dev/pocketagent/app/data/ChatEngine.kt`: In generate(), `val practice = isPractice()` is read before ensureReady(), but a send that wakes Hermes decides practice versus real only during the launch. Scenario on any build except the twin: practice mode expires after two hours, or the switch is turned off while Hermes is stopped by idle stop or an error. runtime.lastGuestOptions still says practice from the last run, so isPractice() returns true. The wake then starts Hermes on the owner's real provider (the launch enforces the expiry and rewrites lastGuestOptions), and the turn runs with real tokens. Those tokens are never recorded in UsageStore, and the BLOCK budget was skipped for this turn. The provider() lambda is read after ensureReady and is therefore right; only the usage and budget decision is stale. *Fix asked:* Keep the early check, so a turn that is clearly blocked does not light the fire. After `if (!ensureReady(run, convId, bot.id)) return false`, read it again: `val practiceNow = isPractice()`. If `practice && !practiceNow`, repeat the budget block check (the same block as above, with `!practiceNow`). Pass `practiceNow` to Turn(...). Add a test: isPractice returns true on the first call and false afterwards, with an over-budget BLOCK store. The turn must be blocked, or else counted when the budget allows it.
- **minor**, `app/src/main/res/values/strings_stream.xml`: The tool card's button stm_tool_card_open ('Open full output') and the sheet's fallback title stm_tool_sheet_fallback_title ('Tool output') still promise output. The sheet they open now says plainly that Hermes sends no output, only the command. This is not a defect of the fix, but the E4 honesty fix is only half done at the entry point the user taps. *Fix asked:* Lead or STREAM owner: reword stm_tool_card_open to 'Show the command' / 'Komutu göster' / 'Afficher la commande', and the fallback title to 'Tool call' / 'Araç çağrısı' / 'Appel d’outil', in values, values-tr and values-fr. Then regenerate values-en and values-en-rXZ with tools/make-test-language.ps1. Optional; it can wait for Wave 1.

### What the reviewer checked, and what the proof on the twin must watch

Method: I read the whole diff of the app code (generated folders skipped), ApprovalContract.kt, ErrorText.kt, PocketWindow.kt, SheetSlide.kt, the rewritten PocketSheet.kt, AppLocale.kt, the relevant parts of RuntimeManager, GuestPrefs and HermesApi.streamCompletion, and the Stop tests in EngineProofFixesTest. Nothing was built or run.

Owner data and settings when this build is later installed over the owner's app: no risk found.
- Nothing stored changes format. PendingApproval with its two new fields lives only in memory.
- UsageStore is only skipped on practice turns; nothing in it is migrated or rewritten.
- No preference keys change.
- The generated Hermes configuration is not touched.
- The approval body changes only what is sent on the wire, and it matches the Hermes source as the ENGINE reviewer verified.
- The removed strings are unused, and every new key has English, Turkish and French.

Regressions against what the proof passed:
- The robot runs at animation scale 0. There, SheetSlide.opening(false) is at rest from the first frame and playSlide applies the end value at once, so the earlier sheet results stand.
- The PocketDialog scrim moved to a sibling Box. Taps in the padding still fall through, because the padded box has no pointer node there, and the panel keeps its empty click.
- appLocale() now prefers LocalAppLanguage. That local carries the debug override, so en-XZ and rtl behave as before.
- The only real regression is finding 1: Stop while a card waits can now count as a completed turn.

What the phone proof on the twin must watch most closely:
1. **Stop while an approval card waits** (ASK_APPROVAL, then Stop).
   - No reply notification and no unread count may appear.
   - No 'empty reply' NOTE and no 'stream ended without [DONE]' ERROR may be added.
   - app.log must show POST /v1/runs/*/stop with 200.
   - The next message in that chat must go through at once, without the roughly 3-minute block and without a repeated card.
   - Repeat with Stop pressed right after tapping Allow (the answer still on its way).
   - Check what Hermes answers to the stop for a chatcmpl-* run id. A 404 there means the deny fallback runs every time.
2. **All four choices** (once, session, always, deny) must get HTTP 200 and the turn must continue. With deny, the command must not run.
3. **Sheets at animation scale 1** (New chat, Chat Controls, tool output, message menu).
   - Each sheet must glide up visibly.
   - grep app.log for 'Sheet: no frame moved the sheet'. Any hit means S1's real cause is still open, even if the sheet appears.
   - Also check closing, a scrim tap, Back, a fling, and opening with the keyboard up.
4. **Language inside windows**: phone on Turkish, app on English. ConfirmDialog, every sheet and the 'Diagnostics copied' OK must be English. Repeat with French, en-XZ and rtl=1; layout direction is now re-provided inside the window.
5. **Practice usage**: Settings > Usage must not move after practice turns. One real Hydrogen turn at low effort must still count.
6. **Names**: in English the About name, the launcher label, the tile and 'Ask Agirî' must show plain names on the twin ('Agirî Test'). This is the first real proof that values-en wins over values-en-rXZ.
7. **TalkBack**:
   - Each sheet should be announced as a pane, and its scrim read as 'Close, button'.
   - A ConfirmDialog should be announced by its title, with 'Close, button' read after the content.
   - The About mascot should read 'Pêt, the newt mascot, button', and five taps must still open the stats.

Smaller things, not blocking:
- After a 'gone' failure the card comes back with buttons that can only fail again. The line tells the user to press Stop, which is acceptable.
- The optional dialog.confirm tags on the OK buttons at SettingsScreen.kt:135 and :212 and BotShareUi.kt:232 are still missing. The robot keeps tapping them by text.


## The last repair

All three findings are fixed. The twin build, the owner-debug build and the strings and lint checks all pass. Unit tests give exit code 2 with only the four known Windows symlink tests failing (879 run, 3 skipped). The phone was not touched and nothing was committed.

**What I changed**
- `app/src/main/java/dev/pocketagent/app/data/ChatEngine.kt`, finding 1 (major):
  - In `Turn.finishLocked`, the outcome is forced to Cancelled whenever `run.stop` is set. A stopped turn can no longer bump unread, emit `TurnCompleted` or `TurnFailed`, or add the empty-reply or transport-error line, even if Hermes ends the stream first. Tools that were running are marked interrupted.
- `app/src/main/java/dev/pocketagent/app/data/ChatEngine.kt`, finding 2 (minor):
  - `generate()` now reads `isPractice()` once before `ensureReady()` for the early budget block, and again after it. The second reading is the one passed to `Turn`.
  - If the turn was practice early and is real after the wake, the BLOCK budget check runs again, so the turn is blocked or counted correctly.
  - The budget check is now a small helper, `budgetBlocks(...)`.
- Finding 3 (minor):
  - I reworded `stm_tool_card_open` to "Show the command" / "Komutu göster" / "Afficher la commande".
  - I reworded `stm_tool_sheet_fallback_title` to "Tool call" / "Araç çağrısı" / "Appel d'outil". These are in `values`, `values-tr` and `values-fr` (`strings_stream.xml`).
  - I regenerated `values-en` and `values-en-rXZ` with `tools/make-test-language.ps1`.
- `app/src/test/java/dev/pocketagent/app/EngineProofFixesTest.kt`: five new tests.
  - Stop, where the backend waits for `interruptRun` and then ends the stream itself with Finish, Done and Completed. It asserts no BOT, NOTE or ERROR line after Stop, unread stays 0, and no `TurnCompleted` or `TurnFailed` event.
  - The same with `TransportError` and no `[DONE]`.
  - Stop on an empty-bubble turn adds no empty-reply note.
  - A wake that flips from practice to real, with an over-budget BLOCK store, is blocked and the day total is unchanged.
  - The same flip with a budget that allows it is counted.

I did not run the new Stop tests against the old code to see them fail.

**What I declined**
- I did not add the optional `dialog.confirm` tags on the OK buttons at `SettingsScreen.kt:135`, `:212` and `BotShareUi.kt:232`. They are marked non-blocking, and the robot already taps those buttons by text.

**Tests and checks**
- `strings-check.ps1` exits 0 (errors=0, warnings=939 against a baseline of 939).
- `lint-ui.ps1 -Check` reports ok.
- `make-test-language.ps1 -Check` reports up to date.

**Behaviour changed that the phone proof had already passed**
- **Stop while a card waits:** the outcome is now forced to Cancelled when the user pressed Stop.
  - Re-prove point 1 of the reviewer's watch list: Stop with ASK_APPROVAL, and Stop right after tapping Allow.
  - Check for no reply notification, no unread count, no empty-reply NOTE, no "stream ended without [DONE]" ERROR, and POST `/v1/runs/*/stop` returning 200 in `app.log`.
  - A plain Stop during normal streaming behaves as before, because it was already Cancelled.
- **Practice usage:** the practice-or-real decision now happens after the launch. Re-prove point 5: Settings > Usage must not move after practice turns, and one real turn at low effort must still count. A wake after the practice mode expired is the new path.
- **Names:** the tool card's spoken click label ("Show the command") and the fallback sheet title ("Tool call") changed. The card's click label is read by TalkBack only (point 7). The title is visible only when a tool has no name.


## The proof on the phone

not run: the phone is away. It is run on the test twin when the phone is back.
