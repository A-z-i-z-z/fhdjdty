# Wave 0, between stage 1 and stage 2: RESOLVE

Written 2026-11-25 by the RESOLVE agent. Input: `stage1-report.md` (ten areas), contract sections 1, 2, 3 and 11, and the real tree at the stage-1 checkpoint. Nothing was compiled; no Gradle, adb or phone. The only edits to the tree are the two listed under "Changes made" below.

## Changes made (the whole list)

| File | Change | Why |
|---|---|---|
| `app/src/main/java/dev/pocketagent/app/ui/components/PocketTopBar.kt` | new last parameter `titleModifier: Modifier = Modifier`, applied to the title `PixelTitle` / `Text` | STREAM request to DESIGN; contract 3.8 says the chat top bar puts `navSharedBounds(SharedKeys.chatTitle(convId))` on the title. A parameter with a default: every existing call is unchanged. |
| `app/src/main/java/dev/pocketagent/app/ui/chat/ChatTopArea.kt` | passes `titleModifier = Modifier.navSharedBounds(SharedKeys.chatTitle(convId))` and imports `navSharedBounds` | the other half of the same request (STREAM's own recipe). It is a no-op when no shared-transition scope is around, when `NavFlags.sharedElements` is false or when animations are off (`navSharedBounds` returns `this`); and no list row carries the same key yet, so nothing travels until SWEEP S2 adds it (5.9 R9). |

## What was checked (all offline)

- **Resources:** all 115 xml files under `app/src/main/res`, `app/src/debug/res`, `app/src/twin/res` parse; no duplicate name in one qualifier folder of one source set; no unescaped apostrophe; the only leading or trailing spaces are the old `ui_fork_suffix` and `ui_skills_picker_prompt` pairs (pre-wave, handled in Kotlin). Every `R.string.x` and `R.plurals.x` that Kotlin (main and test) uses exists, except the nine plural conversions listed under "For WORDS". Every `@string/` in xml exists. Every key of contract 3.14 exists, and `cfg_diag_export_title` / `cfg_diag_export_sub` do not (`#52`). Key prefixes match section 6 in all six new strings files. Every protected key (`assets/text/protected-keys.txt`) exists (`price_*` is a pattern).
- **Kotlin, mechanical:** 1270 `import dev.pocketagent.app...` lines in 298 files: every one resolves to a declared name. No capitalised call and no lower-case top-level or extension function of the project is used in a file that neither imports it nor sits in its package. No unbalanced `{ ( [` in any file. No nested or unterminated block comment. All of `tools/*.ps1` parse (Windows PowerShell 5.1), are pure ASCII and have no BOM. `sh -n` passes on every `runtime/*.sh`, `rootfs/**/*.sh`, `proot/*.sh`, `tools/ci/*.sh`. `build.yml` is valid YAML (jobs unit, proot, rootfs, smoke, apk, release).
- **Signatures against pre-wave (`41e8d4d`):** every changed function signature in a modified file is additive (new trailing parameters with defaults), except the ones STREAM and NAV own and call themselves. Every removed function has no caller left (`reasonName`, `routeTransition`, `pixelTap`, `pixelTick`, `frameAt` and `msUntilNextFrame` moved to `Sprite.kt`).
- **Scripts that ran and passed:** `python rootfs/smoke/mock_llm.py --selftest` (PRACTICE SELFTEST OK), `python tools/art/craft_art.py --check` (ok x3), `phone-test.ps1 -SelfTest`, `phone-perf.ps1 -SelfTest`, `lint-ui.ps1 -SelfTest` (SELFTEST OK x3), `make-test-language.ps1 -Check` (up to date, exit 0). `strings-check.ps1` exits 1 with 18 errors (see "For TRANSLATE" and "For the lead"). `lint-ui.ps1 -Check` exits 2 until `-Init`.
- **Section 3.1 to 3.14 registry:** read below, row by row. Everything that a consumer calls exists in the provider with that name, package and parameter list, except the points in the tables.

## Registry conformance, row by row

| Row | Result |
|---|---|
| 3.1 Build identity | All BuildConfig fields the code reads exist in `app/build.gradle.kts` (`TEST_HOOKS`, `APP_VARIANT`, `IS_TWIN`, `OWNER_PACKAGE`, `CI_RUN`, `SIGNING_KIND`, `DEEP_LINK_SCHEME`, plus the standard ones). Manifest: no literal package name, `android:label="@string/app_name"` on `<application>` and on all six aliases, `${applicationId}` in the file provider. Assets `practice_model.py` and `practice-start.sh` are copied by Gradle from files that exist, and `PracticeModel.ASSET_*` use the same names. `tools/build-ids.ps1` defines the five names; every script that spells a package takes it from there. Twin overlay: the 12 launcher xml names equal the main ones; `app_name`, `srf_ask_agiri`, `srf_tile_label` exist in main and in the overlay. OK |
| 3.2 Command lines | Every parameter of the command table exists in the scripts (`local-build.ps1`, `make-test-language.ps1`, `strings-check.ps1`, `phone-test.ps1`, `phone-perf.ps1`, `lint-ui.ps1`, `craft_art.py`, `mock_llm.py`); every file `build.yml` calls exists. `runtime/practice-start.sh` exists, LF, `#!/bin/sh`, `sh -n` ok. OK |
| 3.3 Navigation | `Navigator`, `NavEntry`, `Route.Gallery`, route keys, `NavFlags` (internal, the two vars with the registry defaults), `NavTags`, `SharedKeys`, `navSharedElement`, `navSharedBounds(key, resize)`, `robotRoot()`, `LocalAppLanguage`, `AppLinks.ACTION_DEBUG`, `AppLink.DebugOpen`, `DebugLinks` (constants, `DebugRequest`, `parse`, `apply`, `stackFor`), `DebugOverrides`, `AppClock`, `DebugSeeder` (object, `Spec`, `parse`, `run`): all as registered. `apply` order is reset, clock, lang, rtl, nav, seed, route. The `I/Nav: top=<key> id=<n> stack=[...]` and `I/Seed: ...` lines are written. `DebugLinks.apply` runs on `MainActivity.debugScope` (IO, process-wide), so the seed is off the main thread. OK |
| 3.4 Tokens, motion, haptics | `Space`, `Corner`, `Border`, `Type`, `PixelType`, `Elevation`, `designUnitPx`, `u`, `snapToDesignPx`, `pixelFontPx`, `bevelRim`, `bevelShade`, `Dur`, `Ease`, `Springs`, `Loop`, `Flourish`, `Pocket.motion` with all named specs, `Pocket.anim`, `HapticCue`, `rememberHaptics`, `HapticPlayer.play`, `PocketColors.glow` and `scrim` (last two fields): all present as registered. OK |
| 3.5 Components | `PocketSheet` / `PocketSheetScope` (with `close`), `ActionSheet`, `SheetAction`, `ConfirmDialog` (parameter names and defaults exactly the registry's), `PocketDialog`, `PetNotes` / `PetNoteKind` / `PetNoteHost`, `PocketSwitchRow(modifier)` last, `PocketTopBar(ember)` and now `titleModifier`, `PixelText(followTextScale, kerning)`, `CutProbe`, `DesignGallery`. `robotRoot()` is applied in the shell root, `PocketDialog` and `PocketSheet`. OK |
| 3.6 Texts | `t`, `tn`, `ProvideTexts`, `LocalTextsRevision`, `appLocale()`, `Texts` (`install`, `get`, `getQuantity`, `text`, `textQuantity`, `localeOf`, `currentLocale`, `reloadPacks`, `revision`, `describe`, `installForTest`), `TestLanguage`, `EnumLabels`, `Mirroring`: present. `ProvideTexts` sits inside `LocaleScope` in `AppRoot`. OK |
| 3.7 Stage | `HearthScene` signature unchanged, `SpriteBank.preload` and `isReady`, `rememberSceneClock(active, maxFps = 30)`, `rememberSpritesReady()`, `rememberClock(active)` delegating: present; `PocketApp` calls `preload`. OK |
| 3.8 Chat | `ChatScreen(convId)` unchanged; `ChatScreenState.say` and `toastNotSent`; chat top bar uses `navSharedElement` on the avatar and, after this step, `navSharedBounds` on the title. OK |
| 3.9 Toast wrappers | Not created yet (SWEEP S4 and S5 create them). `CtlUi.kt` still has the old `toast(ctx, message)` and `RoutineUiParts.kt` the old `showToast(ctx, message)`: expected before SWEEP. |
| 3.10 Practice and guest | `PracticeModel` (all constants and functions; `KEYWORDS` equals the python tuple, 15), `GuestPrefs` / `GuestOptions` (registry members plus the internal test constructor), `ProviderConfig` (`renderModelYaml`, `renderGuardYaml`, `renderYaml(i, o)`, `renderPracticeYaml`, `renderPracticeEnv`, `AGENT_TOOLSETS`, `WITHHELD_TOOLSETS`, `savedProblems`), `HermesConfig` (`plan`, `apply`, `launchCommand`, `GuestFiles`, `CredentialAction`), `RuntimeManager` (`STILL_STARTING`, `onGuestOptionsChanged` including the `Error` case, `lastGuestOptions`, `guestCodeReport`), `PracticeBanner`, fixtures. `RuntimeSection`, `PracticeBanner`, `PrivacyRows`, `PocketApp` and `Diagnostics` call them with the registered forms. `app.log` gets `practice=on practicePort=...` or `practice=off` on every process start line and `I/State: <label>` on every state change, which is what `phone-lib.ps1` reads. OK |
| 3.11 Diagnostics | `Diagnostics.exportLogs/exportLogsIntent`, `MainViewModel.shareLogsIntent` (suspend), `DiagnosticsExtras()`, `CrashReportOffer()`, `WebLookupsRow()`, `PocketApp.guestPrefs/crashReports`, `Transcript.splitLong`, `GuestCodeKind/GuestCodeEntry/GuestCodeScan.summary`, `CrashReports(context, diagnostics)` with `offer` and `evaluate()`: present; `AppRoot` places `CrashReportOffer()` and `PracticeBanner(...)` and `PetNoteHost(...)` once; `RuntimeSection` calls `DiagnosticsExtras()`; `BehaviourSection` calls `WebLookupsRow()`. `CtlUi.kt` still has the internal `CText`, `Muted`, `ErrorText`, `SubHeading`. OK |
| 3.12 Tags | Every tag in the table is written somewhere in code except the ones S1 to S5 write (listed under "For SWEEP"). STREAM's 24 tags, DESIGN's, NAV's, STAGE's, CONFIG's, ROBOT-P's all exist with the exact strings. `ScenarioFilesTest.knownTags` equals the table. Every tag a scenario file uses is in the table. `RobotTags.chatMsg` and `chatTool` have no caller (STREAM writes the same strings literally). OK |
| 3.13 PocketApp | Order 1 to 8 as written; the provider lambda is the review-fix form; `wave0Slots()` has only the CRASH slot; the start log line has `variant=`, `signing=`, `package=`. OK |
| 3.14 Cross-area keys | All exist (see "What was checked"). OK |

## Every request and deviation, with its outcome

Outcome codes: **done** (what and where), **verified** (checked, nothing to change), **deferred** (reason), **fwd X** (forwarded to X; the text is in the list for X).

### BUILD

| Item | Outcome |
|---|---|
| Req INTEGRATE: twin `VERSION_CODE` equals `max(CI_RUN*100+seq, 1)`; AGP 9.5.2 spellings (`variant.outputs.forEach`, `sourceSets.getByName("debug").res.srcDir`, `manifestPlaceholders[...]`) | fwd INTEGRATE |
| Req INTEGRATE: compile `build.gradle.kts` (script-level `var ownerKeyProblem`, `tasks.matching {}.configureEach { doFirst }`, `gradle.startParameter.taskNames`), add `missing_rules.txt` dontwarn lines, `-Xmx` only if R8 runs out | fwd INTEGRATE |
| Req INTEGRATE: fill `owner_baseline_bytes` and `owner_ceiling_bytes` in `tools/size-budget.txt` after the first green release build | fwd INTEGRATE |
| Req NAV: manifest has no literal package, `android:label="@string/app_name"` on `<application>` and all six aliases | verified (manifest read: no literal; labels on `<application>` and six aliases) |
| Req ROBOT-P: `runtime/practice-start.sh` exists, LF, `#!/bin/sh`; CI needs `mock_llm.py --selftest` and `craft_art.py --check` | verified (file exists, 0 CR, shebang ok, `sh -n` ok; both commands exit 0 here) |
| Req ROBOT-T / TEXT: the CI unit job's scripts exist and run under pwsh 7 on Linux | verified for existence and Windows PowerShell 5.1 (self-tests pass, `make-test-language -Check` exit 0); pwsh 7 on Linux cannot be tried here: fwd INTEGRATE (first CI run). `lint-ui.ps1 -Check` exits 2 until `-Init`: fwd INTEGRATE. `strings-check.ps1` exits 1 (18 errors): fwd TRANSLATE and lead |
| Req lead: CI secret names, never set them without the lineage; `AGENTS.md` should record the three variants, the version scheme and the `local-build.ps1` switches | fwd lead |
| Req lead (optional): `*.ps1 text eol=lf` in `.gitattributes` | fwd lead (not needed: the one test that cared normalises CRLF) |
| Dev: gradle assets task copies `mock_llm.py` and `practice-start.sh` | verified (both files exist; asset names equal `PracticeModel.ASSET_SCRIPT` and `ASSET_START`) |
| Dev: `baseline-prof.txt` cut | verified (no consumer) |
| Dev: signing lineage only read by `tools/ci/sign-owner.sh`, S6 not run in Wave 0 | verified (documented, nothing to do) |
| Dev: `releaseRequested` widening, lazy `doFirst` failure for the committed key | fwd INTEGRATE (compile) |

### ROBOT-P

| Item | Outcome |
|---|---|
| Req NAV: place `PracticeBanner(modifier)` | verified (`AppRoot.kt`: `PracticeBanner(Modifier.align(Alignment.TopCenter).statusBarsPadding())`, imported from `ui.settings`) |
| Req NAV: `DebugSeeder.parse` then `run` off the main thread, `onSuccess` log, `onFailure` warn | verified (`MainActivity.runDebugLink` launches on the process-wide IO scope, waits for `chat.loaded`, calls `DebugLinks.apply`; `DebugLinks.runSeed` logs `AppLog.i("Seed", line)` and catches exceptions; a rejected seed goes to `rejected` and a warning) |
| Req ROBOT-T: seed log formats; `{{latestChat}}` matches `^chat (seed-\S+)` | verified (`phone-lib.ps1` uses `I/Seed: chat (\S+)`; "chats 120 newest ..." does not match because of the `s`) |
| Req ROBOT-T: files of the practice model on the host; the robot must not type MEMORY, FORGE, BALLAST on the owner package | verified (`PracticeModel.hostDir` is `files/rootfs/opt/agiri/practice`; `RuntimeSection` hides the three words off the twin) |
| Req DESIGN: `PocketSwitchRow(modifier)` after `subtitle`; `ConfirmDialog` parameter names | verified (both match) |
| Req SWEEP S5 / INTEGRATE: `RuntimeSection` imports `ui.components.ConfirmDialog`, which wins over the same-package `ui.settings.ConfirmDialog` of `CtlUi.kt` until S5 deletes it | fwd SWEEP (S5 deletes `CtlUi.ConfirmDialog`; the explicit import keeps compiling either way) |
| Req INTEGRATE / BUILD: Gradle copies the two practice assets | verified (see BUILD) |
| Req CONFIG: `practice.env` and `practice.yaml` were untracked | verified (committed in the checkpoint; the tree is clean) |
| Dev: `RobotTags` extra constants (`HERMES_TAB_USER/CONFIG/LOGS`, `PRACTICE_BANNER`) | verified (additions only; `RobotTagsTest` and the contract table agree) |
| Dev: `Spec.Usage(limit: Long, mode: BudgetMode)`, `DebugSeeder.run` delegating to an internal overload, `MAX_CHARS` 4000, `RuntimeSection` word list off the twin, `practice-start.sh` waits on the "listening on" line, `mock_llm.py` media folder in temp | verified (self-consistent; `smoke-test.sh` and `HermesConfigPlanTest` use the same launch form `sh /opt/agiri/practice/start.sh <port>`) |

### ROBOT-T

| Item | Outcome |
|---|---|
| Req INTEGRATE: `lint-ui.ps1 -Init` after the compile, then `-Check` | fwd INTEGRATE (decide the DESIGN exemption first, see DESIGN row) |
| Req INTEGRATE: run the three `-SelfTest`s | verified (all three print SELFTEST OK here) |
| Req INTEGRATE or TEXT: extend `leak-allow.txt` after the first stretched-language sweep | fwd INTEGRATE |
| Req STREAM and SWEEP: the 3.12 tags must exist; `sheet.action.<i>` and `dialog.confirm`; the chat menu's delete entry stays the LAST action | verified for STREAM, DESIGN and NAV (`ChatDialogs.kt` ends the menu with Delete; `ActionSheet` tags `sheet.action.$i`); fwd SWEEP for the rest |
| Req NAV: keep `I/Nav: top=<key>` with no spaces; `link debug` extras | verified (`NavDisplay` writes it under `TEST_HOOKS`; `apply` handles route, reset, lang, rtl, seed, nav, reset first) |
| Req CONFIG-R: keep `practice=on|off` and `I/State:` in `app.log` | verified (`RuntimeManager` writes `I/State: <label>` on each change and `... practice=on practicePort=N` or `practice=off` on `process started`) |
| Req ROBOT-P or DESIGN: practice switch tag on the checkable container; gallery tags | verified (`PocketSwitchRow` puts `modifier` first in the chain before `toggleable`, `RuntimeSection` passes `testTag(RobotTags.RUNTIME_PRACTICE)`; gallery tags exist) |
| Req DESIGN: `CutProbe` gated on `TEST_HOOKS`, so the owner-debug build reports | verified (`CutProbe.report` checks `BuildConfig.TEST_HOOKS`; owner-debug has it true) |
| Req lead: the twin-only scenario files and perf P2/P3 are cut-first | fwd lead |
| Dev: `"safe": true` instead of `owner: true`; extra ops and fields; lint-ui `\btween\s*[<(]`; lazy `Add-Type`; second-pass cut check; `-Out`, `-Adb` | verified (all inside ROBOT-T's files; `ScenarioFilesTest` and `PhoneToolsSafetyTest` agree) |

### DESIGN

| Item | Outcome |
|---|---|
| Req INTEGRATE: create `app/src/test/resources/pixelfont-v1.golden` from a worktree of the pre-wave commit | fwd INTEGRATE (needs a Gradle run; not reproducible offline without re-implementing the Kotlin parse) |
| Req INTEGRATE: paste the ids `PaletteContrastTest` prints into `KNOWN_FAILURES` | fwd INTEGRATE |
| Req ROBOT-T lint-ui: exempt `ui/theme/Motion.kt`, `Tokens.kt`, `PocketTheme.kt` (already exempt for tween, shape, duration; `ui/theme/` is exempt for dp) and `ui/components/DesignGallery.kt` | deferred: it is a judgement about what the ratchet counts, and the baseline does not exist yet, so nothing is lost. The three theme files are already exempt. `DesignGallery.kt` is debug-only with literal dp and plain English: INTEGRATE or the lead decides before `lint-ui.ps1 -Init` (put it in `$Rules` as an exempt file of `literalDp`, `rawTween`, `roundedCorner`, or let the baseline hold it) |
| Req NAV: place `PetNoteHost` once; `DesignGallery()` only when `TEST_HOOKS` | verified (`AppRoot.kt`: one `PetNoteHost`, `Route.Gallery -> if (BuildConfig.TEST_HOOKS) DesignGallery() else HomeScreen()`) |
| Req TRANSLATE: `design_note_dismiss`, `design_ember_active` in tr and fr | fwd TRANSLATE |
| Req SWEEP and STREAM: shared `ConfirmDialog` with `closeOnConfirm = true, danger = true`; no own bar insets at call sites | verified for STREAM (`ChatDialogs.kt`); fwd SWEEP (already in SWEEPS.md recipe R2; the "no own bar insets" remark is new) |
| Dev: `PocketDialog` now `decorFitsSystemWindows = false` with scrim over the whole window; `PixelFontTest` bound 140; sheet clears the status bar | verified (consistent with `PocketSheet`; no other area depends on the old behaviour) |
| Dev: STREAM's request for `titleModifier` | done (see "Changes made") |

### NAV

| Item | Outcome |
|---|---|
| Req DESIGN: `Pocket.motion` with `level`, `fast`, `glide`, `custom`; `Dur.BASE`, `Ease.enter`, `Ease.standard`, `Space.xl`, `Space.s`; `PetNotes`, `PetNoteKind`, `PetNoteHost`, `DesignGallery` in `ui.components` | verified (all present) |
| Req ROBOT-P: `DebugSeeder` is an `object` with `parse(text): Spec?` and `run(app, spec): String`; `PracticeBanner` in `ui.settings`; `CrashReportOffer` in `ui.diag` | verified |
| Req INTEGRATE: `ResizeMode.scaleToBounds()`, named `sharedContentState =` / `animatedVisibilityScope =`, `Configuration.SCREENLAYOUT_LAYOUTDIR_*` | fwd INTEGRATE (compile) |
| Req INTEGRATE: `AppRoot.kt` still has five `stringResource(` calls for W4 | fwd WORDS (W4; four more `stringResource` in `BottomTab` and the share dialog are the same five) |
| Req BUILD: no `resValue` needed | verified |
| Dev: `NavFlags` extras, `parseParts(..., isTwin)`, blank `lang` rejected, `reset=1` also resets NavFlags, `Route.Gallery` rank and tab index, `debugScope` process-wide | verified (all additive; tests agree) |
| Unverified: `holder.removeState(id)` semantics, shared elements on the phone, uiautomator exporting tag-only boxes | fwd lead (phone proof) |

### STAGE

| Item | Outcome |
|---|---|
| Req CONFIG-R: `SpriteBank.preload(this)` in `PocketApp` | verified (done by CONFIG-R in `PocketApp.onCreate`) |
| Req CONFIG-D: list `SpriteBank.loadErrors` in `Diagnostics` | verified (`Diagnostics.kt` has it) |
| Req BUILD: `craft_art.py --check` in the CI unit job | verified (`build.yml` calls it; it passes here under Windows Python 3.12) |
| Req ROBOT-P: `*.py text eol=lf` | verified (`.gitattributes`) |
| Req INTEGRATE: first-compile risks (`LocalLifecycleOwner`, `repeatOnLifecycle`, `SpriteSource` fun interface, private nested classes of `LegacyHearthReference`) | fwd INTEGRATE |
| Dev: `EmberSet` internal in `ui.pixel.stage`; extra tests; `craft_art.py --check --out` rejected | verified |

### STREAM

| Item | Outcome |
|---|---|
| Req DESIGN: `PocketTopBar` `titleModifier` | done (see "Changes made") |
| Req ROBOT-P: `RUN_TOOL big`, `LONG`, `PAUSE` as in 3.10 | verified (`mock_llm.py` selftest checks `RUN_TOOL big` (120 lines of 80), `PAUSE` halves; self-test passes) |
| Req ROBOT-T: the tags of 3.12 and the swipe for the message menu | verified (all 24 STREAM tags exist as literals; scenario tags are in the table) |
| Req TRANSLATE: four `stm_` strings and the plural `stm_tool_sheet_truncated` | fwd TRANSLATE |
| Req WORDS W1: convert `stringResource(` / `ctx.getString(` sites in STREAM's eight files; `NumberFormat` with `currentLocale()` may become `appLocale()` | fwd WORDS |
| Req INTEGRATE: first-compile checks (`PetNoteKind`, `HapticPlayer`, `PocketSheetScope.close()`, `VisibilityThreshold`, ...) | fwd INTEGRATE (the symbols all exist with the signatures STREAM uses; only the compiler can confirm `PocketSheetScope : ColumnScope` captured as `sheet`) |
| Dev: `haptics: HapticPlayer` in `ChatScreenState`, `executeSlash` as an extension, `RowLayout.toolIndex`, memory tool cards open the sheet, `ChatMessageList` shifting `chat.tool.<i>` | verified (internal to STREAM; the tag numbering is the contract's) |

### CONFIG-R

| Item | Outcome |
|---|---|
| Req ROBOT-P: `PracticeModel` and `practice-start.sh` exist; request log keeps `POST` and `n_msgs`, `heads` | verified |
| Req DESIGN: `PocketSwitchRow(modifier)` last | verified |
| Req lead: Anthropic mode leaves `security.allow_lazy_installs` out; image has no anthropic extra | fwd lead |
| Req lead: `cronjob_manage` off the list but the terminal tool can still run `hermes cron`; Wave 3's gate must cover it | fwd lead |
| Req slot: only CRASH | verified |
| Dev: `renderGuardYamlFor`, top-level `CredentialAction` and `GuestFiles`, `GuestPrefs` internal constructor, `lastGuestOptions` set at launch and rewrite, `guestConfigLock`, pre-port window case in `currentGuestOptions()`, `PrivacyRows` through `PocketSwitchRow(modifier)`, `TranscriptSplitTest` only | verified (consumers and tests use these forms) |

### CONFIG-D

| Item | Outcome |
|---|---|
| Req ROBOT-P: `DiagnosticsSection` calls `DiagnosticsExtras()` and `vm.shareLogsIntent()` with `cfg_diag_exporting` and `cfg_diag_export_failed` | verified (`RuntimeSection.kt`) |
| Req NAV: `CrashReportOffer()` once | verified (`AppRoot.kt`) |
| Req CONFIG-R: `GuestCodeEntry`, `GuestCodeKind`, `GuestCodeScan.summary`, `lastGuestOptions`, `guestCodeReport`, `GuestPrefs.practiceModel/webKeyless/practiceMinutesLeft`, `PocketApp.crashReports` and the `evaluate()` call off the main thread | verified (all present; `wave0Slots()` launches on IO) |
| Req TRANSLATE: tr and fr for all of `strings_config.xml` incl. `cfg_crash_copy_failed` and the plural `cfg_diag_guest_found` | fwd TRANSLATE |
| Req WORDS W6: `DiagnosticsExtras.kt` string calls (`pluralStringResource` becomes `tn`) | fwd WORDS |
| Dev: extra key, extra helpers, `copyReport` throws, date through `LocalConfiguration.current.locales[0]` (the same value `appLocale()` returns), main-process exits only, no tool-list line, tags as literals | verified (additive; WORDS W6 may switch the locale read to `appLocale()`, optional) |

### TEXT

| Item | Outcome |
|---|---|
| Req WORDS: nine call sites of the ten converted plurals (the tenth, `ui_chat_members_count`, has no caller) | fwd WORDS (found by `R.string` against `<plurals>`: `ctl_bot_chars`, `ctl_bots_count`, `ctl_bots_import_title`, `ctl_bots_imported`, `ctl_chars`, `ctl_usage_turns`, `ui_home_unread`, `ui_new_selected`, `ui_unread_cd`. The tree does not compile until they are converted.) |
| Req WORDS W10: `@Before TestTexts.installEnglish()` in four test classes | fwd WORDS |
| Req INTEGRATE: regenerate both generated folders after every English edit and after TRANSLATE; check the twin on `en-GB`; `-WriteBaseline` once; `-FailOnUnused` once after WORDS | fwd INTEGRATE |
| The four new length errors of `strings-check.ps1` (`rbt_practice_on` 47 characters in kind widget, `rbt_practice_left` 15 in widget, `cfg_diag_guest_title` 33 in title, `cfg_priv_web_sub` 228 in row) | deferred: choosing the kind or shortening a text is an owner decision (see "For the lead"); CI's `strings-check.ps1` step stays red until it is made |
| Req TRANSLATE: `strings_engine.xml` (76 label keys, `eng_rt`, `eng_tts`, the rest) and the two design keys | fwd TRANSLATE |
| Dev: LanguagePack, PackStore, ResourceCatalog cut; 60 icon and 16 avatar keys; interim baseline; generator also writes `values-en`; ordinal sort | verified (no consumer outside TEXT; `EnumLabelsTest` pins the counts) |

## For SWEEP

- **Tags that nobody has written yet** (`RobotTags`, 50 constants and 6 functions; `RobotTagsTest` and `ScenarioFilesTest` already know them). S3: `HOME_STATE`, `HOME_START`, `botsRow(i)` plus `BOTS_EXPORT`, `BOTS_IMPORT` (`BotCard` gains `modifier: Modifier = Modifier` last, contract 3.12 and 5.9 R7). S2: `CHATS_NEW`, `chatsRow(i)`, `CHAT_INPUT`, `CHAT_SEND`, `CHAT_STOP`, `CHAT_ATTACH`, `CHAT_MIC`, `CHAT_APPROVAL_ONCE/SESSION/ALWAYS/DENY`. S1: `CHAT_FORGED_CHAT` ("Chat now" of the forged bot card in `AttachmentUi.kt`). S4: `HERMES_TAB_*` (six), `HERMES_DOC_EDITOR`, `HERMES_DOC_SAVE`, `HERMES_SKILL_NEW/NAME/CREATE/DELETE`, `hermesSkillRow(name)`, `TERMINAL_INPUT/RUN/OUTPUT`. S5: `settingsSection(key)` for the ten sections, `USAGE_TODAY`, `USAGE_BUDGET_LIMIT/WARN/BLOCK`, `BEHAVIOUR_IDLE_MINUTES`, `BACKUP_SAVE/RESTORE/RESULT`. (`chatMsg(i)` and `chatTool(i)` have no caller: STREAM writes `"chat.msg.$index"` and `"chat.tool.$toolIndex"` literally, the same strings.) Use the `RobotTags` constants, not literals, except where a recipe says otherwise.
- **Shared elements, contract 5.9 R9:** `SharedKeys.chatAvatar(convId)` and `SharedKeys.chatTitle(convId)` on the chat-list row (S2, `ChatListScreen.kt`) and the bot keys on the bot card (S3), so the chat top bar's `navSharedElement` and, now, `navSharedBounds` have a partner. At the checkpoint only `ChatTopArea.kt` uses `SharedKeys`; without the partner nothing travels and nothing breaks.
- **`ui.settings.ConfirmDialog` of `CtlUi.kt` (S5):** `RuntimeSection.kt` already imports `ui.components.ConfirmDialog` explicitly, so deleting the old one is safe; `HermesTabs.kt`, `HermesScreen.kt`, `HermesDocs.kt`, `BotEditorScreen.kt`, `BotShareUi.kt` import the old one by name and must get the `ui.components` import (the recipes say so).
- **`ui.chat.ActionSheet` / `SheetAction` / `ConfirmDialog` in `ChatCommon.kt` (S1):** `ChatDialogs.kt` already imports the `ui.components` ones explicitly. `ChatListScreen.kt` (S2) uses the old ones through the package: it needs the `ui.components` imports once S1 deletes them (recipe R3).
- **No own bar insets at `ConfirmDialog` / `PocketSheet` call sites:** DESIGN's `PocketDialog` and `PocketSheet` now lay out inside the system bars (`systemBarsPadding`), so a converted call site must not add its own status or navigation bar padding.
- **Toast wrappers (3.9):** `toast(ctx, message, kind = INFO)` in `CtlUi.kt` and `showToast(ctx, message, kind = INFO)` in `RoutineUiParts.kt` are still the old two-parameter forms in the tree; `ChatScreenState.say` already goes through `PetNotes.show`.
- `ModalBottomSheet(` still appears in `ChatCommon.kt` (the old `ActionSheet`, to be deleted), `ChatQuickBar.kt` (`ChatControlsSheet`), `SkillPickerSheet.kt`, `NewChatSheet.kt`, `AttachmentUi.kt`: the `sheetDirect` lint counts them until S1 converts them.

## For WORDS

- **The tree does not compile until these nine call sites use the plural form (recipe T5, `tn(R.plurals.k, n, n)` and `Texts.getQuantity(ctx, R.plurals.k, n, n)`):** `BotEditorScreen.kt` `ctl_bot_chars`; `BotsScreen.kt` `ctl_bots_count`; `BotShareUi.kt` `ctl_bots_import_title` and `ctl_bots_imported` (`ctx.getString`); `HermesDocs.kt` `ctl_chars`; `UsageSection.kt` `ctl_usage_turns` (three sites); `HomeScreen.kt` `ui_home_unread`; `NewChatSheet.kt` `ui_new_selected`; `ChatListScreen.kt` `ui_unread_cd`. `ui_chat_members_count` has no caller.
- **W1:** the `stringResource(` / `ctx.getString(` sites of STREAM's eight files (`ChatScreenState.kt`, `ChatSlashActions.kt`, `ChatTopArea.kt`, `ChatInputArea.kt`, `ChatDialogs.kt`, `ChatMessageList.kt`, `ToolOutputSheet.kt`, `StreamingMarkdown.kt`); `ToolOutputSheet` uses `NumberFormat` with `currentLocale()` (may become `appLocale()`). `ChatMessages.kt` and `MarkdownView.kt` are W2's.
- **W4:** `AppRoot.kt` has five `stringResource(` calls (the untitled-chat label, the share dialog title and its two buttons, the `BottomTab` label); `PocketTopBar.kt` and `TypingIndicator.kt` of DESIGN.
- **W6:** `DiagnosticsExtras.kt` (`stringResource`, `pluralStringResource` becomes `tn`; the crash dialog's date may use `appLocale()`); `RuntimeSection.kt` and `PracticeBanner.kt` (`pluralStringResource(R.plurals.rbt_practice_left, ...)` becomes `tn`).
- **W10:** add `@Before TestTexts.installEnglish()` (import `dev.pocketagent.app.text.TestTexts`) to `ChatEngineTest`, `RuntimeManagerInitTest`, `HermesHomeTest`, `TerminalSessionTest`, and `@After TestTexts.reset()` where a test would otherwise leak the installed source into other classes.
- `PrivacyRows.kt` (CONFIG-R, W6) also has `stringResource(` calls.

## For TRANSLATE

- **Whole new files, no tr or fr yet** (the 14 parity errors of `strings-check.ps1`): `strings_config.xml` (27 keys, incl. `cfg_crash_copy_failed` and the plural `cfg_diag_guest_found`), `strings_privacy.xml` (3), `strings_robot.xml` (8, incl. the plural `rbt_practice_left`), `strings_stream.xml` (5, incl. the plural `stm_tool_sheet_truncated`; add the forms each language needs, the first number 60,000 selects the plural), `strings_engine.xml` (132: 76 label keys, `eng_rt`, `eng_tts`, the rest; keep brackets, `MEMORY.md`, `USER.md`, `finish_reason`, `error_code`, `HTTP 401`, `proot`).
- **Two keys in an existing file:** `design_note_dismiss`, `design_ember_active` in `strings_design.xml` (one-word screen-reader labels).
- Twin overlay tr and fr (`app/src/twin/res/values-tr`, `values-fr`, `strings_build.xml`) already exist (BUILD).
- After the translations: regenerate both `app/src/debug/res/values-en-rXZ/` and `values-en/` with `powershell -NoProfile -ExecutionPolicy Bypass -File tools\make-test-language.ps1`.

## For INTEGRATE

1. **Compile first.** Spelling to confirm: `app/build.gradle.kts` (AGP 9.5.2: `variant.outputs.forEach { it.versionCode.set(...) }`, `sourceSets.getByName("debug").res.srcDir("src/twin/res")`, `manifestPlaceholders[...]` in build types, script-level `var ownerKeyProblem`, `tasks.matching { ... }.configureEach { doFirst { throw ... } }`, `gradle.startParameter.taskNames`); `NavScopes.kt` (`ResizeMode.scaleToBounds()`, `sharedContentState =`, `animatedVisibilityScope =`); `DebugOverrides` / `AppRoot` (`Configuration.SCREENLAYOUT_LAYOUTDIR_*`); `SceneClock.kt` (`androidx.lifecycle.compose.LocalLifecycleOwner`, `repeatOnLifecycle`); `SpriteSource` fun interface with an operator `get`; `LegacyHearthReference.kt` private nested classes; `ToolOutputSheet.kt` (`PocketSheetScope` as `ColumnScope`, captured as `sheet`); `ChatMessageList.kt` (`VisibilityThreshold`); `PracticeBanner.kt` (early return before `produceState`); `data object Clear` in `DebugSeeder`; `PocketTopBar` (`titleModifier`, this step). The nine plural call sites of "For WORDS" must be converted before the compile can pass.
2. **R8:** first release build's `missing_rules.txt` goes into `app/proguard-rules.pro` as `-dontwarn` lines; `gradle.properties -Xmx` to 4g only if R8 runs out of memory. Then fill `owner_baseline_bytes` and `owner_ceiling_bytes` (baseline + 1048576) in `tools/size-budget.txt`.
3. **Baselines and golden files:** `app/src/test/resources/pixelfont-v1.golden` (scratch worktree of the pre-wave commit with only `PixelFontGoldenTest.kt` added, `PIXELFONT_GOLDEN_WRITE=1`), `PaletteContrastTest.KNOWN_FAILURES` (paste the printed ids; expect outline-on-bg and outline-on-surface), `tools/lint-ui-baseline.json` (`lint-ui.ps1 -Init` then `-Check`; decide first whether `ui/components/DesignGallery.kt` is exempt, see DESIGN), `tools/strings-check.baseline.json` (`strings-check.ps1 -WriteBaseline` once, after TRANSLATE and WORDS and after the four length errors are settled), `tools/scenarios/leak-allow.txt` (after the first stretched-language sweep).
4. **Strings:** run `make-test-language.ps1` (no `-Check`) after every English edit and after TRANSLATE; check on the twin with the in-app language on English and the system language on en-GB that Home says "Home", not the bracketed text, and the twin's launcher label stays "Agirî Test"; run `strings-check.ps1 -FailOnUnused` once after WORDS (the 76 `eng_` and 20 `eng_rt` / `eng_tts` keys are unused until then, plus `chat_needs_setup`, `ui_chats_pinned_cd`, `ui_chat_back`, `ui_chat_members_count`, `ui_attach_preview`, `ui_cmd_skills_header`).
5. **CI first run (pwsh 7 on Linux):** `strings-check.ps1`, `make-test-language.ps1 -Check` (ordinal-sorted hash), `lint-ui.ps1`, `phone-test.ps1 -SelfTest`, `phone-perf.ps1 -SelfTest` have only run under Windows PowerShell 5.1. Whether `allow_lazy_installs: false` makes Hermes print something the smoke grep treats as an error, and whether `/v1/toolsets` enabled equals the ten exactly, are only proven by the smoke job.
6. **Tests that read repo files** (`ProguardRulesTest`, `LocalBuildSafetyTest`, `ScenarioFilesTest`, the TEXT resource tests) `Assume` that the files are reachable; `-Test` runs from a copy, so check that they really ran and were not skipped.
7. `RuntimeSection.kt` imports `ui.components.ConfirmDialog` explicitly: keep that import if the compiler reports an ambiguity before SWEEP S5 deletes the old one.

## For the lead

- **Four length errors in `strings-check.ps1` stop CI's string step** until each gets a decision (the notes in the files say what the text is for): `rbt_practice_on` (the practice banner text, note says up to 2 lines, kind `widget` has room 14: probably kind `body`), `rbt_practice_left` (15 characters in kind `widget`, room 14: kind `body` or a shorter plural), `cfg_priv_web_sub` (228 characters, note says up to 5 lines, kind `row`: probably kind `body`), `cfg_diag_guest_title` (33 characters, note says about 35, kind `title` room 28: a shorter text, or kind `body`). Changing a note's `| kind:` does not change the screen; it only changes what the check expects.
- **`AGENTS.md`** (not touched): record the three variants (owner, twin, owner-debug), the version scheme (owner `(10000+run)*100+seq`, twin `run*100+seq`), the `local-build.ps1` switches (`-Release`, `-OwnerDebug`, `-NoBuild`, `-NoCompile`, `-CheckSigning`, `-AcknowledgeS2`, `-AllowReextract`, `-Serial`); that in the Anthropic mode `security.allow_lazy_installs` is left out of `config.yaml` (ruling 11.8) so Hermes can fetch its SDK on first use; that B21 is only half closed (`cronjob_manage` is off the list, but the terminal tool can still run `hermes cron ...` and the gateway ticks every 60 s: Wave 3's gate has to cover it).
- **CI secrets:** `AGIRI_KEYSTORE_B64`, `AGIRI_KEYSTORE_PASSWORD`, `AGIRI_KEY_ALIAS`, `AGIRI_KEY_PASSWORD`, optionally `AGIRI_LINEAGE_B64`. Never set the secrets without the lineage: Android refuses an update signed with a new key. Gradle itself has no lineage support (`sign-owner.sh` reads `AGIRI_SIGNING_LINEAGE`; S6 is not run in Wave 0).
- Optional: `*.ps1 text eol=lf` (or crlf) in `.gitattributes`.
- The twin-only scenario files (the other 30 of the plan, perf P2 and P3) are not written (ruling 10.8d, second pass after the stage-1 compile and the twin).
- Phone proof still open for NAV (saved-state size, shared elements, tag-only boxes in uiautomator, `removeState` semantics), STAGE (Animations Off, `gfxinfo`), DESIGN (dialog with `decorFitsSystemWindows = false`, sheet with keyboard), CONFIG (practice mode restart, exit 70/71).
- DESIGN's exemption of `DesignGallery.kt` from the lint ratchet (see DESIGN row) is yours or INTEGRATE's to decide before `-Init`.
