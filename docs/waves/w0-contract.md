# Wave 0 contract: "Foundation" (written 2026-11-25)

**This contract overrides the area files (`docs/waves/w0/<AREA>.md`) wherever they differ. Every build agent reads this contract first (sections 1, 2, 3, then the amendments of its own area in section 5), then its own area file, and applies the amendments before writing a line.** The area files stay as they are; they remain the source for everything this contract does not mention (scopes, behaviour, tests, risks). `docs/waves/w0/LEAD-DECISIONS.md` is carried into this contract and binding; where this contract and that file differ the contract wins (the lead's own rule), and every such place is marked "(differs from lead)" below. Plan: `docs/phase4-plan.md` section 7.0. Style model: `docs/phase3-plan.md`.

Ten areas build; the eight of stage 1 run as ten agents: BUILD, ROBOT-P, ROBOT-T, DESIGN, NAV, STAGE, STREAM, CONFIG-R, CONFIG-D, TEXT (in parallel; ROBOT and CONFIG are each split in two, 2.2 and 2.7, review fix; everywhere else in this contract "ROBOT" and "CONFIG" without a suffix name the agent that owns the file or symbol in question per 2.2 and 2.7), SWEEP (stage 2, five batches S1 to S5; the draft's S6 is gone, ruling 41), WORDS (stage 3, ten batches W1 to W10 plus W10b), then TRANSLATE (stage 4) and INTEGRATE (stage 5). Rulings in section 4 are cited as `#n`.

## 1. Ground rules

### 1.1 For every build agent

- Kotlin + Compose, packages under `dev.pocketagent.app`. **No new dependencies.** minSdk 26, targetSdk 28 (on purpose). Everything new honours `Pocket.colors`, `Pocket.anim` (the effective level, `#19`) and the pixel components.
- **Own files only** (section 2). Never edit a file you do not own; never "fix" a seam in someone else's file. Use the symbols of section 3 by the exact names and signatures there. If a registry symbol is missing or looks wrong, keep going against the registry and list it under `REQUESTS`.
- **No Gradle, no adb, no git, no phone.** You may run your own scripts' `-SelfTest` and offline checks (PowerShell, python, `sh -n`, `rg`). Never `uninstall`, `pm clear`, `settings put`, never tap or script "Install anyway" on the phone, never change Play Protect or verifier settings, never use a flag that skips verification (G1).
- **English only** in `app/src/main/res/values/`, in **your own strings file** with **your own key prefix** (section 6). Never write `values-tr`/`values-fr` for new keys (TRANSLATE does, stage 4). Exception: TEXT edits existing keys in all three languages (the ten plurals only; capitals written into texts are Wave 1, 5.8).
- Texts: in code you write, use `stringResource(R.string.x)` in composables and `ctx.getString(R.string.x)` in plain code, exactly these forms, so WORDS can convert them mechanically; never build a sentence from pieces; every count noun goes through `<plurals>`; no case change of display text in code (`#62`). Exceptions: TEXT's own files, and DESIGN's one `t(` call at `PixelButton.kt` (`#60`).
- Every new key carries an XML comment note directly above it: `<!-- t: <what it says, where it appears, what each placeholder is, room> | kind: <tab|button|chip|title|row|pet|widget|body> -->`. Apostrophes `\'`; no leading or trailing space in a `<string>` (aapt2 trims it).
- Tokens: in new code use `Space`, `Corner`, `Dur`, `Loop`, `Flourish`, `Pocket.motion`, `HapticCue`, `PetNotes`, `PocketSheet`, `ConfirmDialog`. No raw `tween(`, `Toast.makeText`, `RoundedCornerShape(`, literal duration, or `N.dp` that equals a token in code you write (the ratchet lint counts them).
- Every new screen or part states its kind and its five states where the area file says so (design language 8.16). Clickable things get a `Role` and a spoken label (never an English enum name).
- Unit tests: JUnit 4, JVM only, `org.json` and `isReturnDefaultValues = true` are available. Pure logic stays pure (time, files and prefs are parameters). Tests that read repo files use `Assume.assumeTrue(file.exists())` (`-Test` runs from a copy). A signature you change that a test uses: update the test. Existing tests must stay green.
- Time-bounded: if your part does not finish, cut only items from your own cut list in the area file, and **only items the registry marks "no consumer"**. Never cut: any symbol listed in section 3 as used by another area (`EnumLabels`, `CutProbe`, `DiagnosticsExtras()`, `PetNotes`, `PocketSheet`, `navSharedElement`/`navSharedBounds` as no-ops, `RobotTags`, TEXT group RT, `t`/`tn`/`Texts`). A cut implementation keeps the symbol (an empty or no-op body).
- Final report of every agent: files created or changed; deviations from this contract (name the registry symbol); `REQUESTS`; what you could not verify. Name no account, project id, e-mail or device serial anywhere (the repository is public).

### 1.2 The three build variants and the gates (G1 to G9)

| Variant | Gradle | applicationId | Debuggable | `APP_VARIANT` | `TEST_HOOKS` | Signing |
|---|---|---|---|---|---|---|
| owner | `release` | `dev.pocketagent.app` | no, R8, resource shrinking | `"owner"` | false | private key if present, else committed `sideload` |
| twin | `debug` | `dev.pocketagent.app.debug`, label "Agirî Test", marked icons | yes | `"twin"` | true | committed `sideload` |
| owner-debug | `debug` with `-PownerDebug=true` | `dev.pocketagent.app` (no suffix, no twin overlays) | yes | `"owner-debug"` | true | committed `sideload` |

- **`BuildConfig.TEST_HOOKS`** gates every test hook that reads or navigates (debug link, clock pin, forced language and layout direction, `NavFlags` switches, `I/Nav:` log line, `CutProbe`, the gallery route). Never gate on `BuildConfig.DEBUG` or on the package name.
- **`BuildConfig.IS_TWIN`** (true only for `APP_VARIANT == "twin"`) gates everything that writes test data in bulk: the `seed` link, and (in the robot) `wipe`, backup restore, replacing the Linux image, `pref`, `killGuest`. On owner-debug the robot runs only scenarios marked `"safe": true` (`#17`): they read, navigate, take pictures, and may create a chat through the interface only if they delete it again and only with the practice model switched on through the interface. The robot refuses every other scenario on package `dev.pocketagent.app` and needs `-Owner` to touch it at all.
- The owner's phone gets the R8 build only after S2 has passed in the twin (G5). The twin cannot be installed until the owner taps "Install anyway" once (G1): everything marked "needs the twin" in section 9 waits for that. Before the first Wave 0 install over the owner's app, a read-only copy of its data (chats, bots, memory, settings; no credentials, no Linux image) goes to `dist/owner-backup/` (G6, the lead does it).
- **Rollback (review fix).** Before the first install the lead builds the rollback APK: a worktree of the pre-wave commit `41e8d4d` (docs only since build 32) under `dist\wt-rollback\` (never `dist\wt`), built with that commit's own `local-build.ps1` and `-BuildNumber <owner-debug versionCode + 1>` (the old Gradle uses the build number as version code), the committed key, the same runtime parts. It stays in `dist\rollback\` until the owner's app has run the wave for a day: it is the only way back, because a downgrade needs `-d` or an uninstall and neither is allowed on the owner package. So **no stored format may change in a way the pre-wave code cannot read**; Wave 0's new state (prefs `guest`, `crash_reports`, key `haptics`, the new `Navigator` save format, `files/lang/`) is simply ignored by the old code. The same rule binds every area that touches stored data.
- **The owner's install never changes the Linux image (review fix).** `Assert-OwnerInstallAllowed` (5.1) also compares the APK's rootfs build id with the phone's `files/rootfs.stamp`: a mismatch would start extract, park and restore over the owner's Hermes data, which has never been proven (`AGENTS.md`, "not tried").

### 1.3 The owner's data and provider setup are never touched by test code

- Practice mode never reads, writes or deletes the owner's provider setup, provider secrets or the guest's credential files (service account, ADC): it writes only its own generated `.env` and `config.yaml` (they are regenerated at every start anyway), and the per-install Hermes API key is read as always. It is off by default, shows a banner while on (`PracticeBanner`), and is never in a backup (`GuestPrefs` file `guest` is not in `BackupCore.PREF_SCHEMA`). **On a build that is not the twin it cannot be left on by accident**: the flag carries a timestamp, expires after 2 hours and is reset at every cold process start (3.10 `GuestPrefs`), and the robot never leaves it on (5.2).
- Scripts: `tools/build-ids.ps1` defaults to the twin; the owner package needs an explicit switch; no script contains `uninstall`, `pm clear` (except `wipe` in the robot, twin only), a verifier or low-target-SDK bypass. `settings put` appears **only** in the phone-lib function `Set-PaSystemScales`, which first writes the current values (`font_scale`, `animator_duration_scale`, `transition_animation_scale`, `window_animation_scale`) to `dist\phone-test\system-settings-backup.json` unless that file already exists, always restores in a `finally`, and at the start of every run restores and deletes a leftover backup file; with `-Owner` it runs only when `-AllowSystemSettings` is also given (otherwise functional runs keep the owner's scales and the cut check at font 2.0 is SKIPped with the reason). `-d` only for the twin. Every destructive robot op is guarded by `Assert-PaTwinTarget` (5.2).
- Test code (tests, scenarios, seeders) never uses the owner's chats, keys or credentials; scenario files carry no secret, name or id.

### 1.4 Gotchas that have cost this project time (`AGENTS.md`, "Hard-won facts")

- Kotlin initialises properties and `init` blocks in textual order: declare a property before any `init` or property that reads it (`Navigator.stack` before its `mutableStateOf`; `ChatScreenState.messagesFlow` before `shape`/`matches`; `RuntimeManager.lastGuestOptions` next to `lastLaunchEnv`; `SpriteBank` `lock` before `sprites`; `PixelFont.defV2()` last in `init`).
- Kotlin nests block comments: never write a slash-star (or `*.pxl`, `chat:*`, a path ending in `/*`) inside any comment or KDoc. Write "the strings files", "route keys such as chat:id".
- `0xFF6E6496` is a `Long`: write `.toInt()`. `roundToInt()` of -2.5f is -2. Integer division truncates towards zero. `aapt2` trims whitespace: no leading space in a `<string>`; insert separators in Kotlin.
- `env VAR=...` treats any argument containing `=` as an assignment; Android install paths contain `==`: never put such a path directly after `env`. The twin's `.MainActivity` shorthand resolves to a class that does not exist: always the full class name (`Get-PaComponent`).
- `filesDir` is behind a symlink: compare canonical paths. Shell scripts stay LF (`*.sh`, now `*.py`). POSIX sh only in anything the launcher or guest runs (mksh).
- Never `recycle()` a bitmap that may sit in a display list. `rememberLauncherForActivityResult` is unconditional. A `motion` value is read in the composable body, never in a lambda. `PixelPanel` draws `border` behind `color`: no translucent fill with a bright border. Do not set an opaque `window.statusBarColor`.
- sherpa-onnx: call `generateWithConfig`, never a Kotlin lambda callback. `kotlin-reflect` is not on the classpath: tests list enum entries explicitly. `R` must be reachable from unit tests (TEXT's `TestTexts`).
- PowerShell: scripts must run on Windows PowerShell 5.1 and `pwsh` 7 (CI) unless the file says "5.1 only" (`local-build.ps1`). **`pwsh` is CI only: it is not installed on the owner's PC**, so every local run (INTEGRATE, the lead) is `powershell -NoProfile -ExecutionPolicy Bypass -File tools\<x>.ps1`. Default paths in every script are built with `Join-Path` or written with forward slashes (`app/src/main/res`, `app/src/debug/res/values-en-rXZ`), never with backslash literals, because CI runs the scripts on Linux `pwsh` (review fix); no `&&`, no ternary; UTF-8 without BOM; `-LiteralPath`; PNGs by `screencap` + `adb pull`, never `exec-out`; Git Bash needs `MSYS_NO_PATHCONV=1` before adb paths. ripgrep: `rg --no-config`.
- Before `Texts.install` the context-free `Texts.text` returns `"#<id>"`: tests call `TestTexts.installEnglish()`.

## 2. Ownership

One row per file or folder; each file appears once. "Later" lists edits by a later stage (allowed across stages, never inside one). `S1..S5` are SWEEP batches, `W1..W10b` WORDS batches (section 8).

### 2.1 BUILD (stage 1)

| File | Later | Notes |
|---|---|---|
| `app/build.gradle.kts`, `app/proguard-rules.pro`, `gradle.properties` (only `-Xmx` if R8 needs it) | INTEGRATE (spelling, missing rules) | variants, versions, signing, `TEST_HOOKS`, assets of `#8` |
| `app/src/main/res/raw/keep.xml`, `app/src/main/baseline-prof.txt` (cut-first, no consumer) | | keep adds `@string/*,@plurals/*` as a `tools:keep` attribute value (`#59`) |
| `app/src/twin/res/**`: `values/`, `values-tr/`, `values-fr/` `strings_build.xml` (keys `app_name`, `srf_ask_agiri`, `srf_tile_label`), `drawable/twin_badge.xml`, `drawable/twin_fg_{newroz,pet,flame,zagros,kawa,roj}.xml`, `mipmap-anydpi-v26/ic_launcher*.xml` (12), `xml/shortcuts.xml` | | twin-only source set (1.2); attached to the `debug` source set unless `-PownerDebug`; **not** `app/src/debug/` (differs from the draft, `#1`) |
| `tools/build-ids.ps1`, `tools/local-build.ps1` (5.1 only), `tools/ci/{apk-size,verify-apk,sign-owner,test-apk-size}.sh`, `tools/size-budget.txt` | INTEGRATE fills budget numbers | |
| `.github/workflows/build.yml`, `.gitignore` (append `owner-signing.properties`, `*.jks`, `*.p12`, `lineage*.bin`) | INTEGRATE (one flag, 8.3) | |
| tests `BuildContractTest`, `ProguardRulesTest`, `TwinOverlayTest`, `LocalBuildSafetyTest`, `SizeBudgetFileTest` | | |

### 2.2 ROBOT (stage 1; two agents, review fix)

ROBOT was too big for one session. **ROBOT-P** builds what runs in the app and in the guest; **ROBOT-T** builds the phone tools and the scenarios. The registry symbols stay exactly as written; "ROBOT provides" in section 3 means ROBOT-P for the Kotlin, python, sh, strings and tags below and ROBOT-T for tools, scenarios and their tests. The two meet only through section 3.

**ROBOT-P**

| File | Later | Notes |
|---|---|---|
| `rootfs/smoke/mock_llm.py` (the practice model; CI mock), `runtime/practice-start.sh`, `.gitattributes` (`*.py text eol=lf`) | | |
| `app/.../runtime/PracticeModel.kt` | | JVM-loadable, 3.10 |
| `app/.../DebugSeeder.kt` | | the `seed` link (`#14`) |
| `app/.../ui/RobotTags.kt` | | |
| `app/.../ui/settings/RuntimeSection.kt` | W6 | practice switch, tags, Export logs button, `DiagnosticsExtras()` call |
| `app/.../ui/settings/PracticeBanner.kt` | W6 | |
| `app/src/main/res/values/strings_robot.xml` | TRANSLATE | prefix `rbt_` |
| tests `PracticeModelTest`, `RobotTagsTest`, `DebugSeederTest` | | `PracticePrefsTest` is gone |

**ROBOT-T**

| File | Later | Notes |
|---|---|---|
| `tools/phone-lib.ps1`, `phone-test.ps1`, `phone-perf.ps1`, `lint-ui.ps1`, `lint-ui-baseline.json` | INTEGRATE (`-Init`) | |
| `tools/scenarios/**` (scenario files, `perf/`, `routes.json`, `a11y-allow.json`, `cut-allow.json`, `leak-allow.txt`, `README.md`, `fixtures/`) | | stage 1 writes only the "first set" of 5.2; the other scenario files are cut-first (no code consumes them) |
| `docs/perf/w0.md` (layout only) | lead | |
| tests `ScenarioFilesTest`, `PhoneToolsSafetyTest` (5.2) | | |

`rootfs/smoke/smoke-test.sh` is **not** ROBOT's: CONFIG-R owns it (2.7, `#18`).

### 2.3 DESIGN (stage 1)

| File | Later | Notes |
|---|---|---|
| `ui/theme/{Tokens,Motion,SystemMotion,Haptics,PocketTheme,Appearance,Palettes,PixelFont,PixelIndication}.kt` | | `AppIconManager.kt` untouched |
| `ui/components/`: `PixelShapes.kt`, `PixelPress.kt`, `DitherScrim.kt`, `PocketSheet.kt`, `ConfirmDialog.kt`, `PetNote.kt`, `SwipeRow.kt`, `Skeleton.kt`, `EmberLine.kt`, `DesignGallery.kt`, `CutProbe.kt`, `PixelPanel.kt`, `PixelButton.kt`, `PixelText.kt`, `PixelIcon.kt` (mirroring only), `PocketChip.kt`, `PocketSwitchRow.kt`, `PocketDialog.kt`, `PocketTopBar.kt`, `TypingIndicator.kt`, `EmptyState.kt`, `SectionHeader.kt`, `README.md` | W4 (`PocketTopBar`, `TypingIndicator`, new files with `stringResource(`, three comment edits) | `PocketTextField.kt` untouched |
| `res/values/strings_design.xml` (existing file, new keys prefix `design_`) | TRANSLATE | |
| tests in `app/src/test/.../ui/theme/` and `.../ui/components/` (list in the area file), `app/src/test/resources/pixelfont-v1.golden` | INTEGRATE (golden, `KNOWN_FAILURES`) | |

### 2.4 NAV (stage 1)

| File | Later | Notes |
|---|---|---|
| `ui/Nav.kt`, `ui/AppRoot.kt` | W4 (`AppRoot`) | |
| `MainActivity.kt`, `AppLinks.kt`, `AndroidManifest.xml`, `res/xml/shortcuts.xml` (owned, **no edit** in Wave 0: the twin has its own overlay, `#29`) | | |
| `ui/nav/{NavStack,NavMotion,NavDisplay,NavScopes,NavFlags,NavTags}.kt`, `DebugLinks.kt`, `DebugOverrides.kt`, `AppClock.kt` | | |
| tests `NavStackTest`, `NavMotionTest`, `NavTagsTest`, `DebugLinksTest`, `AppClockTest`, `AppLinksTest` (edited) | | |

### 2.5 STAGE (stage 1)

| File | Later | Notes |
|---|---|---|
| `ui/pixel/{Sprite,SpriteBank,SpriteLoader,SpriteRaster,PixelImage,HearthScene,Fire,StageSurface,SceneClock,PixelStageBitmap}.kt`, `ui/pixel/stage/*` | | `Avatars.kt`, `Mascot.kt` untouched |
| `tools/art/{craft_art.py,pxl.py,contact_sheet.py,README.md}` | | |
| tests `app/src/test/.../ui/pixel/stage/*` (`LegacyHearthReference.kt` first, before any edit), `PxlParserTest` stays unchanged | | |

### 2.6 STREAM (stage 1)

| File | Later | Notes |
|---|---|---|
| `data/ChatEngine.kt` (`Turn` and its companion constant only), `data/StreamPush.kt` | W10 (engine literals) | |
| `ui/chat/`: `ChatScreen.kt`, `ChatScreenState.kt`, `ChatSlashActions.kt`, `ChatTopArea.kt`, `ChatMessageList.kt`, `ChatInputArea.kt`, `ChatDialogs.kt`, `ToolOutputSheet.kt`, `StreamSmoother.kt`, `MarkdownStream.kt`, `StreamingMarkdown.kt` | W1 | |
| `ui/chat/Markdown.kt`, `MarkdownView.kt`, `ChatMessages.kt` | W2 | |
| `res/values/strings_stream.xml` | TRANSLATE | prefix `stm_` |
| tests in `.../ui/chat/`, `.../data/StreamPushTest.kt`, `ChatEngineTrailingTest.kt` | | `ChatEngineTest` unchanged (W10 adds `@Before`) |

### 2.7 CONFIG (stage 1; two agents, review fix)

CONFIG held the guard block, the practice launch, the code scan, exit reasons, the crash offer, log export, the transcript split, `HermesApi`, the smoke script and `PocketApp`: too much for one session, in the most safety-critical files. **CONFIG-R** (runtime and request side) and **CONFIG-D** (diagnostics side) split it. The registry symbols stay as written (3.10 to 3.13); where one side uses the other's symbol the form is in 3.11 ("Cross-split").

**CONFIG-R**

| File | Later | Notes |
|---|---|---|
| `runtime/{ProviderConfig,HermesConfig,GuestPrefs,GuestCodeScan}.kt` | | |
| `runtime/RuntimeManager.kt` | W10b | |
| `PocketApp.kt` (all of 3.13), `data/Transcript.kt`, `api/HermesApi.kt` | | unowned in the plan (lead) |
| `ui/diag/PrivacyRows.kt` (`WebLookupsRow`) | W6 | |
| `ui/settings/BehaviourSection.kt` | S5, then W5 | stage 1: import and `WebLookupsRow()` call only |
| `rootfs/smoke/smoke-test.sh`, `rootfs/smoke/fixtures/*` (six `.yaml` changed, new `practice.*`, `studio-nokeyless.*`) | | not `mock_llm.py` (ROBOT-P) |
| `rootfs/install-hermes.sh` | | line 57 only, if the lead agrees (5.7, section 10) |
| `res/values/strings_privacy.xml` (new, keys `cfg_priv_*`, the privacy row) | TRANSLATE | prefix `cfg_priv_` |
| tests `ProviderConfigTest`, `HermesConfigPlanTest`, `GuestCodeScanTest`, `GuestPrefsTest`, `TranscriptTest`, `TranscriptSplitTest`, `ChatRequestSplitTest` | | `RuntimeManagerInitTest` stays green (W10b adds `@Before`) |

**CONFIG-D**

| File | Later | Notes |
|---|---|---|
| `diag/{Diagnostics,ExitReasons,CrashReports,LogExport}.kt`, `MainViewModel.kt` | | `MainViewModel.kt` unowned in the plan (lead) |
| `ui/diag/DiagnosticsExtras.kt` (also holds `CrashReportOffer`) | W6 | |
| `res/values/strings_config.xml` (every `cfg_` key except `cfg_priv_*`) | TRANSLATE | prefix `cfg_` |
| tests `ExitReasonsTest`, `LogExportTest` | | |

### 2.8 TEXT (stage 1)

| File | Later | Notes |
|---|---|---|
| `ui/text/{TextSource,PluralRules,LanguagePack,ProtectedTexts,Mirroring,EnumLabels,ResourceTextSource,PackStore,Texts,TextsCompose}.kt`, `app/src/main/assets/text/protected-keys.txt` | | `LanguagePack`, `PackStore`, `PackTextSource` have no Wave 0 consumer: **cut-first** (review fix); `Texts.install` and `reloadPacks` keep working with no packs (empty bodies) |
| `res/values/strings_engine.xml` (prefix `eng_`, includes the 75 label keys and group RT) | TRANSLATE | |
| `res/values{,-tr,-fr}/strings_chat.xml`, `strings_control.xml` (the ten plurals only) | | existing keys, all three languages (`#62`); `strings_routines.xml` is NOT touched (its only TEXT edit was W12, capitals, which is Wave 1) |
| `tools/strings-check.ps1`, `tools/make-test-language.ps1`, `tools/strings-kinds.txt`, `tools/strings-check.baseline.json` | INTEGRATE (`-WriteBaseline`) | |
| `app/src/debug/res/values-en-rXZ/**` (generated, committed) | TRANSLATE, INTEGRATE (`-Check`) | debug source set: twin and owner-debug |
| tests `app/src/test/.../text/*` incl. `TestTexts` | W10 (`@Before` in four other classes) | |

### 2.9 SWEEP (stage 2, mechanical; find sites by quoted text, never by line number)

| Batch | Files |
|---|---|
| S1 chat sheets | `ui/chat/ChatCommon.kt`, `AttachmentUi.kt`, `NewChatSheet.kt`, `SkillPickerSheet.kt`, `ChatQuickBar.kt` |
| S2 chat list and input | `ui/chat/ChatListScreen.kt`, `ChatInput.kt`, `SlashPalette.kt`, `ChatSearch.kt`, `StarredScreen.kt` (`ChatLogic.kt`, `SyntaxHighlight.kt`: read only) |
| S3 home and bots | `ui/home/HomeScreen.kt`, `HomeParts.kt`, `ui/bots/BotsScreen.kt`, `BotShareUi.kt`, `BotEditorScreen.kt` |
| S4 hermes and routines | `ui/hermes/HermesScreen.kt`, `HermesTabs.kt`, `HermesDocs.kt`, `TerminalScreen.kt`, `ui/routines/RoutineEditorScreen.kt`, `RoutinesScreen.kt`, `RoutineUiParts.kt` |
| S5 settings | `ui/settings/AppearanceSection.kt`, `BackupSection.kt`, `BehaviourSection.kt` (not the privacy row), `CtlUi.kt`, `ProviderSection.kt`, `SettingsScreen.kt`, `UsageSection.kt`, `VoiceSoundSection.kt` (not `RuntimeSection.kt`) |

Each of these files is edited later by exactly one WORDS batch (section 8.2). No other file is edited by SWEEP.

### 2.10 WORDS, TRANSLATE, INTEGRATE (stages 3, 4, 5)

| Stage | Files |
|---|---|
| WORDS (3) | batches W1 to W10b, section 8.2. Also `app/src/test/.../ChatEngineTest.kt`, `TerminalSessionTest.kt`, `HermesHomeTest.kt`, `RuntimeManagerInitTest.kt` (`@Before TestTexts.installEnglish()` only) |
| TRANSLATE (4) | `values-tr/**` and `values-fr/**` strings files for every key added in Wave 0; regenerates `app/src/debug/res/values-en-rXZ/**` |
| INTEGRATE (5) | any file, seam fixes only (8.4); baselines: `tools/lint-ui-baseline.json`, `tools/strings-check.baseline.json`, `PaletteContrastTest.KNOWN_FAILURES`, `app/src/test/resources/pixelfont-v1.golden`, `tools/size-budget.txt`, `app/proguard-rules.pro` dontwarn lines |

### 2.11 Nobody edits (even where a draft asked)

`data/BackupManager.kt` (rulings `#23`, CONFIG question 2), `data/ChatRepository.kt`, `data/Models.kt`, `service/*` and `routines/*` except WORDS texts, `voice/*` except `VoiceEngine.kt` (W10b), `runtime/Settings.kt`, `Paths.kt`, `RuntimeState.kt`, `ui/pixel/Avatars.kt`, `Mascot.kt`, `ui/chat/ChatLogic.kt`, `SyntaxHighlight.kt`, `ui/hermes/ConsoleAnsi.kt`, `HermesEditorsViewModel.kt`, `ui/home/HomeScrim.kt`, `runtime/pocketagent-proot.sh` (the launcher does not change, G7), `res/xml/file_paths.xml`, `tools/pixel-preview.ps1`, `tools/pxl-to-vector.ps1`, `docs/*` except `docs/perf/w0.md` (ROBOT-T, layout only), `AGENTS.md`, `PROGRESS.md` (the lead).

## 3. Shared-symbol registry

Package `dev.pocketagent.app` is written `app`. One form per symbol; where drafts disagreed the losing side is in section 4 and 5. Spelling of Gradle and AGP DSL may be fixed by INTEGRATE; meaning binds.

### 3.1 Build identity, BuildConfig, manifest placeholders (BUILD provides)

| Symbol | Form | Consumers / behaviour |
|---|---|---|
| Package ids | owner and owner-debug `dev.pocketagent.app`, twin `dev.pocketagent.app.debug`; `namespace` stays `dev.pocketagent.app` | classes, `R`, `BuildConfig`, `.MainActivity`, aliases `.Launcher{Newroz,Pet,Flame,Zagros,Kawa,Roj}`, `.service.FireTileService`, `.RestartActivity` keep their names |
| `app.BuildConfig.TEST_HOOKS: Boolean` | true for the `debug` type (both debug variants), false for release | NAV, DESIGN (`CutProbe`, gallery), ROBOT; 1.2 |
| `app.BuildConfig.APP_VARIANT: String` | `"owner"`, `"twin"`, `"owner-debug"` | CONFIG (log line, Diagnostics), NAV (seed gate), robot (via `app.log`) |
| `app.BuildConfig.IS_TWIN: Boolean` | `APP_VARIANT == "twin"` | NAV, ROBOT (`DebugSeeder`); gates bulk test data |
| `BuildConfig.OWNER_PACKAGE: String` | `"dev.pocketagent.app"` always | tests, twin warning text |
| `BuildConfig.CI_RUN: Int`, `SIGNING_KIND: String` (`"committed"` or `"private"`), `DEEP_LINK_SCHEME: String` (`"agiri"`, twin `"agiri-test"`) | | CONFIG log line and Diagnostics line |
| existing | `GIT_COMMIT`, `VERSION_NAME`, `VERSION_CODE`, `APPLICATION_ID`, `BUILD_TYPE`, `DEBUG` | unchanged; do not gate on `DEBUG` |
| Manifest placeholders | `${deepLinkScheme}`, `${appVariant}` besides `${applicationId}` | none used in the Wave 0 manifest; a future `VIEW agiri://` filter must use `${deepLinkScheme}` |
| Gradle inputs | `-PciRun=<int>` (alias `-PbuildNumber`), `-PlocalSeq=<0..99>`, `-PgitCommit`, `-PsigningKind=private\|committed`, `-Pshrink=true\|false` (default true), `-PownerDebug=true`, `-PownerSigning=<path>`; env `AGIRI_KEYSTORE_FILE/PASSWORD`, `AGIRI_KEY_ALIAS/PASSWORD`, `AGIRI_SIGNING_LINEAGE`. **`-Pperf` and `twin-perf` do not exist** (cut, `#1`) | CI, local-build |
| Version | owner and owner-debug: code `(10000+ciRun)*100+localSeq`, name `0.2.<ciRun>` plus `-local<seq>` when seq>0 (no other suffix); twin: code `ciRun*100+localSeq`, name as owner plus `-test`; no properties: owner 1000000, twin 1 | `local-build.ps1`, `verify-apk.sh`, tests |
| APK assets | `practice_model.py` (copy of `rootfs/smoke/mock_llm.py`) and `practice-start.sh` (copy of `runtime/practice-start.sh`) beside the launcher, in all variants | `PracticeModel.install` (ROBOT) |
| `tools/build-ids.ps1` | `$PaNamespace`, `$PaOwnerPackage`, `$PaTwinPackage`, `$PaMainActivity = 'dev.pocketagent.app.MainActivity'`; `Get-PaComponent([string]$Package,[string]$ClassName)` (a leading `.` gets `$PaNamespace`, not the package); `Test-PaOwnerPackage([string]$Package)` | `local-build.ps1`, `phone-test.ps1`, `phone-perf.ps1`: the only place that spells a package |
| Output lines of `local-build.ps1` | `APK:`, `PACKAGE:`, `VERSION:`, `CERT-SHA256:`, `INSTALLED-CERT-SHA256:`, `COMPILE:`; exit 0 ok, 1 error, 2 expected Windows symlink test failures, 3 refused for safety | robot, lead |
| Log line | `app.log` start line `started Agiri build <GIT_COMMIT> (<VERSION_NAME>) variant=<APP_VARIANT> signing=<SIGNING_KIND> package=<APPLICATION_ID>` (CONFIG writes it) | robot reads `variant=` |

### 3.2 Tool command lines that CI or other tools call

`pwsh` in this table is the CI form. On the owner's PC `pwsh` does not exist: the same file runs there as `powershell -NoProfile -ExecutionPolicy Bypass -File tools\<x>.ps1 <same arguments>`, and `local-build.ps1` (5.1 only) calls `make-test-language.ps1` that way. Only `lint-ui.ps1` and `make-test-language.ps1` take `-Check`; **`strings-check.ps1` has no `-Check` switch** (its plain run is the check).

| Command (from repo root) | Provider | Caller | Exit |
|---|---|---|---|
| `powershell -NoProfile -ExecutionPolicy Bypass -File tools\local-build.ps1 [-Release] [-OwnerDebug] [-Install] [-Start] [-NoBuild] [-NoCompile] [-CheckSigning] [-Test] [-FetchParts [phase0-N]] [-BuildNumber n] [-AcknowledgeS2] [-AllowReextract] [-ListDevices] [-Serial id]` | BUILD | lead, INTEGRATE | 0/1/2/3 |
| `pwsh -NoProfile -File tools/lint-ui.ps1 [-Check] [-Update] [-Init] [-Json] [-SelfTest]` | ROBOT | CI unit job, INTEGRATE | 0 ok, 1 over, 2 baseline missing |
| `pwsh -NoProfile -File tools/strings-check.ps1 [-ResDir] [-Factor 1.4] [-WriteBaseline] [-FailOnUnused] [-Quiet]` and `... -ScanDump <dump.xml> [-Allow <file>]` | TEXT | CI, `phone-test.ps1` (`-ScanDump`), INTEGRATE | 0 ok, 1 error, 2 ratchet worse, 3 bad call, 4 leaks |
| `pwsh -NoProfile -File tools/make-test-language.ps1 [-Check] [-SelfTest] [-Tag en-XZ] [-OutDir]` | TEXT | CI (`-Check`), `local-build.ps1` (`-Check` before every debug build; stale = abort with the command to run), TRANSLATE | 0, 1, 3 stale |
| `powershell ... -File tools\phone-test.ps1 [-Suite core\|smoke\|slow\|all] [-Scenario a,b] [-Owner] [-AllowSystemSettings] [-Package id] [-Serial id] [-Out dir] [-Var k=v] [-NoA11y] [-NoCut] [-StopOnFail] [-List] [-SelfTest]` | ROBOT | lead | 0, 1, 2, 3 |
| `... tools\phone-perf.ps1 [-Mode Smooth\|ColdStart\|Memory\|All] [-Scenario P1,P3] [-Owner] [-AllowSystemSettings] [-Package id] [-Label] [-Repeat n] [-Report a,b] [-Markdown f] [-Enforce] [-LimitMb n] [-SelfTest]` | ROBOT | lead | 0, 1 (`-Enforce`), 2 |
| `python tools/art/craft_art.py [--only fire\|scene\|appicon] [--out dir] [--check]`; `python tools/art/contact_sheet.py ...` | STAGE | CI (`--check`, `python3` on Linux) | 0, 1 |
| `python3 rootfs/smoke/mock_llm.py [--host] --port N [--log F] [--media-dir D] [--watch-parent] [--selftest]` | ROBOT | CI smoke, app (via `start.sh`) | `--selftest` prints `PRACTICE SELFTEST OK` |
| `sh tools/ci/{apk-size.sh <apk> <owner\|twin> <budget> [out], verify-apk.sh <apk> <pkg> <debuggable 0\|1> <versionCode\|->, sign-owner.sh <in> <out>, test-apk-size.sh}` | BUILD | CI | 0, 1, 2 (`build.yml` prepends `$ANDROID_HOME/build-tools/36.0.0` to `PATH` before `verify-apk.sh` and `apk-size.sh`: they call `aapt2` and `apksigner`) |
| `sh runtime/practice-start.sh PORT [COMMAND [ARGS...]]` (inside the guest: `sh /opt/agiri/practice/start.sh PORT`) | ROBOT | `RuntimeManager` (via `HermesConfig.launchCommand`), `smoke-test.sh` phase 3 | 70 no python, 71 mock died or port in use |
| Hermes `GET /v1/toolsets` (Bearer API key) | Hermes | `smoke-test.sh` | asserts that the set of toolsets with `enabled == true` equals `AGENT_TOOLSETS` exactly (no more, no fewer: Hermes also adds plugin, MCP and platform-native toolsets, so "the ten are on and the five are off" would let an extra one pass), and that the five `WITHHELD_TOOLSETS` are not enabled |
| CI `unit` job order | BUILD | | `pwsh --version`; `sh -n` on every `runtime/*.sh`, `rootfs/**/*.sh`, `proot/*.sh`, `tools/ci/*.sh`; `python3 -m py_compile rootfs/smoke/mock_llm.py`; `python3 rootfs/smoke/mock_llm.py --selftest`; `python3 tools/art/craft_art.py --check`; `lint-ui.ps1 -Check`; `strings-check.ps1`; `make-test-language.ps1 -Check`; `phone-test.ps1 -SelfTest`; `phone-perf.ps1 -SelfTest`; `lint-ui.ps1 -SelfTest`; `sh tools/ci/test-apk-size.sh`; `sh runtime/test-launcher.sh sh dash bash mksh`; then `testDebugUnitTest` |

### 3.3 Navigation, flags, link, overrides, clock (NAV provides)

| Symbol | Form | Consumers / behaviour |
|---|---|---|
| `ui.NavEntry` | `@Immutable data class NavEntry(val id: Long, val route: Route)` | ids unique per `Navigator`, never reused, survive process death |
| `ui.Navigator` | `val entries: List<NavEntry>`, `val currentEntry: NavEntry`, `val current: Route`, `val canPop: Boolean`, `fun go(Route)`, `fun back(): Boolean`, `fun tab(Route)`, `fun replace(Route)`, `fun reset(routes: List<Route>)`, `companion val Saver`; `LocalNavigator` unchanged | all screens use only `go/back/tab` (unchanged) |
| `ui.Route.Gallery` (new, key `gallery`) | exists in all builds; `AppShell` renders `DesignGallery()` only when `BuildConfig.TEST_HOOKS`, otherwise Home; `showsBar()` false; reachable only through the debug link | DESIGN's gallery, ROBOT scenario `design-gallery` (`#14`) |
| route keys (`Route.encode()`, debug link `route`) | `home chats chat:<id> bots botnew bot:<id> hermes terminal settings settings:<section> routines routinenew routine:<id> starred gallery` | robot `routes.json` |
| `ui.nav.LocalSharedTransitionScope`, `LocalNavAnimatedScope`, `LocalNavEntry` | as NAV 3.4 | `navSharedElement` only |
| `ui.nav.SharedKeys` | `chatAvatar(convId)`, `chatTitle(convId)`, `botAvatar(botId)`, `botCard(botId): Any` | STREAM (chat top bar), SWEEP S2/S3 |
| `@Composable fun Modifier.navSharedElement(key: Any): Modifier`; `@Composable fun Modifier.navSharedBounds(key: Any, resize: Boolean = false): Modifier` | no-ops (`this`) when scopes are null, `NavFlags.sharedElements` is false or level is OFF; exist even if N5 is cut | STREAM, SWEEP |
| `ui.nav.NavFlags` (internal) | `@Volatile var backGestureDrivesScreen = false` (**differs from lead G8: S1 FAILED on the owner's phone**, `dist/exp-results/S1-S3-navigation.md`; the gesture path stays in the code, switched on by `nav=back=1` or on a phone that sends progress), `@Volatile var sharedElements = true` (S3 passed) (runtime switches, G8); changed only in `TEST_HOOKS` builds by the debug link extra `nav` (`back=0|1,shared=0|1`); `reset=1` restores these defaults | NAV only |
| `ui.nav.NavTags` | `BAR="nav.bar"`, `TAB_HOME/CHATS/BOTS/HERMES/SETTINGS = "tab.home" ...`, `SHARE_DIALOG="share.dialog"`, `SHARE_NEW="share.new"`, `SHARE_ITEM="share.item"`, `fun screen(route: Route): String` (`screen.home chats chat bots boteditor hermes terminal settings routines routineeditor starred gallery`) | ROBOT |
| `@OptIn(ExperimentalComposeUiApi::class) fun Modifier.robotRoot(): Modifier` in `ui.nav` | `semantics { testTagsAsResourceId = true }`; applied once at the shell root, and by DESIGN inside `PocketDialog` and `PocketSheet` (own windows) | DESIGN, robot |
| `ui.LocalAppLanguage: ProvidableCompositionLocal<String>` | language in force, `""` = system, debug override included | TEXT (optional) |
| `AppLinks.ACTION_DEBUG = "dev.pocketagent.app.DEBUG"`; the five existing action strings stay literal; `AppLink.DebugOpen(route)` | explicit-component intents only, no manifest filter | robot, tests |
| `DebugLinks` | `EXTRA_ROUTE="route"`, `EXTRA_CLOCK="clock"`, `EXTRA_LANG="lang"`, `EXTRA_RTL="rtl"`, `EXTRA_RESET="reset"`, `EXTRA_SEED="seed"`, `EXTRA_NAV="nav"`; `DebugRequest(reset: Boolean, route: Route?, clock: Setting<Long>, language: Setting<String>, rtl: Setting<Boolean>, seed: String?, nav: String?, rejected: List<String>)`; `fun parse(intent: Intent?, debug: Boolean = BuildConfig.TEST_HOOKS, zone: ZoneId = ZoneId.systemDefault()): DebugRequest?`; `fun apply(app: PocketApp, req: DebugRequest): Route?`; `fun stackFor(route: Route): List<Route>` | release: `parse` returns null. Order inside `apply`: reset, clock, lang, rtl, nav, seed, then the route. `seed` is accepted only when `BuildConfig.IS_TWIN` (otherwise listed in `rejected`) |
| Debug link command line | `adb shell am start -a dev.pocketagent.app.DEBUG -n <pkg>/dev.pocketagent.app.MainActivity --es route chat:abc --es clock 2027-03-21T20:30 --es lang tr --es rtl 1 --es reset 1 --es seed chats:120 --es nav back=0,shared=0 --es token <T>` (all `--es`; missing extra = keep; `clock` also `off`; `lang` a BCP-47 tag or `system`, the stretched language is `en-XZ`; `rtl` `1/0/auto`). `T` is `adb shell run-as <pkg> cat files/debug-link.token` (trimmed); the app creates that file at its first start and never rotates it; a link without the right token is ignored and logs "debug link refused" | robot `link` op (adds the token itself) |
| `DebugOverrides` | `language: StateFlow<String?>`, `forceRtl: StateFlow<Boolean?>`, `setLanguage`, `setForceRtl`, `clear`; in memory, never persisted | `LocaleScope`; non-composable code ignores it on purpose (`#56`) |
| `AppClock` | `pinned: StateFlow<Long?>`, `nowMillis()`, `now(zone)`, `pin(ms)`, `unpin()`, `parse(text, zone): Long?`; the pinned clock is frozen | no Wave 0 consumer (STAGE's sky reads it in Wave 1) |
| `app.log` line (TEST_HOOKS) | `I/Nav: top=<route key> id=<n> stack=[<id>\|<key>,...]` on every top change; `I/Nav: saved state <n> bytes`; `I/Seed: chat <id>` | robot fallback when a `screen.*` tag is not exported |

`DebugSeeder` (ROBOT, `app` package, called only by `DebugLinks.apply`): `sealed interface Spec` (`Chats(count)`, `Chat(messages, chars)`, `Routine(minutesFromNow)`, `Usage(limit, mode)`, `Clear`), `fun parse(text: String): Spec?` (pure; grammar `chats:<n>`, `chat:<messages>:<chars>`, `routine:<min>`, `usage:<limit>:<WARN\|BLOCK\|OFF>`, `clear`), `fun run(app: PocketApp, spec: Spec): String` (throws `IllegalStateException` unless `BuildConfig.IS_TWIN`; seeded ids start `seed-`; returns the one log line). `DebugLinks.apply` writes that line with `AppLog.i("Seed", line)` (the `I/Seed: chat <id>` line above); `run` itself does not log, so the robot's `{{latestChat}}` always finds it.

### 3.4 Tokens, motion, haptics, system level (DESIGN provides; package `app.ui.theme`)

| Symbol | Form |
|---|---|
| `Space` | `xxs 2, xs 4, s 8, m 12, l 16, xl 24, xxl 32, gutter 16, touch 48` (all `Dp`); `Corner.chip/card/sheet = 1/2/3` (`Int`); `Border`, `Type`, `PixelType.small/title/big = 1.5/2/3 dp`, `Elevation`; `designUnitPx(density)`, `Density.u(steps)`, `snapToDesignPx`, `pixelFontPx`, `bevelRim`, `bevelShade` |
| `Dur` | `TAP 90, FAST 150, BASE 220, SLOW 320, SCENE 480, EPIC 900` (`Int` ms); `Ease.standard/enter/exit`, `Ease.steps(n)`; `Springs`; `Loop.GLOW 1700, TYPING 1000, CARET 520, EMBER 700, SLIDE 1100, SHIMMER 1400, EMBER_LINE 1200`; `Flourish.STOKE 1200, SHOWER 2400` |
| `Pocket.motion` | `val Pocket.motion: MotionScheme @Composable get()`, import `app.ui.theme.motion`; `MotionScheme.level: AnimLevel` (effective), `tap() fast() base() slow() scene() glide() snappy() bouncy() pop()`: `fun <T> x(): FiniteAnimationSpec<T>`; `custom<T>(durationMs: Int, easing: Easing = Ease.standard)`; `loop<T>(periodMs: Int, repeatMode: RepeatMode = RepeatMode.Restart, easing: Easing = LinearEasing): InfiniteRepeatableSpec<T>`; `loops`, `particles`, `parallax`, `shaders`, `sceneFps` |
| `Pocket.anim` | the **effective** level (setting, system animator scale, battery saver); same name and type as before, so STAGE, STREAM and the 20 existing readers need no edit; `Pocket.appearance.animations` is the raw setting |
| Haptics | `enum class HapticCue { TICK, CLICK, SEND, RISE, DOUBLE, HOLD, STOKE, PULSE }`; `@Composable fun rememberHaptics(): HapticPlayer`; `HapticPlayer.play(cue: HapticCue)`; `Appearance.haptics` (no UI, not in backup in Wave 0, `#23`) |
| `PocketColors` | two new fields at the end: `glow`, `scrim` (defaults derived); nothing else changes |

### 3.5 Components (DESIGN provides; package `app.ui.components`)

| Symbol | Form | Consumers |
|---|---|---|
| `PocketSheet` | `@Composable fun PocketSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier, title: String? = null, maxHeightFraction: Float = 0.92f, content: @Composable PocketSheetScope.() -> Unit)`; `interface PocketSheetScope : ColumnScope { fun close(then: () -> Unit = {}) }`; content is bounded in height and **not** wrapped in a scroll (a `LazyColumn` with `weight(1f, fill = false)` works); pads itself for navigation bar and keyboard; tags `sheet`, `sheet.scrim`, `sheet.handle`, `sheet.title`; applies `robotRoot()` | STREAM (`ToolOutputSheet`), SWEEP S1 (4 sheets) |
| `ActionSheet`, `SheetAction` | `class SheetAction(val icon: PixelIcon, val label: String, val danger: Boolean = false, val onClick: () -> Unit)`; `@Composable fun ActionSheet(title: String?, actions: List<SheetAction>, onDismiss: () -> Unit)`; rows tagged `sheet.action.<i>` | STREAM (`ChatDialogs`), SWEEP S2 |
| `ConfirmDialog` | `@Composable fun ConfirmDialog(title: String, body: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = false, closeOnConfirm: Boolean = false, dismissLabel: String? = null)`; tags `dialog.confirm`, `dialog.cancel` (no custom tag parameters, `#66`) | STREAM, SWEEP, ROBOT, CONFIG |
| `PocketDialog` | signature unchanged `(title, onDismiss, confirm = null, dismiss = null, content)`; tags `dialog`, `dialog.title`; applies `robotRoot()` | all |
| `PetNotes` | `enum class PetNoteKind { INFO, SUCCESS, ERROR }`; `object PetNotes { fun show(text: String, kind: PetNoteKind = PetNoteKind.INFO, actionLabel: String? = null, onAction: (() -> Unit)? = null); fun clear() }` (any thread, never throws); `@Composable fun PetNoteHost(modifier: Modifier = Modifier)`; tags `petnote`, `petnote.action` | NAV places the host once; STREAM, SWEEP, NAV call `show` |
| `PocketSwitchRow` | `PocketSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, subtitle: String? = null, modifier: Modifier = Modifier)` | ROBOT (practice row), CONFIG |
| `PocketTopBar` | existing parameters plus `ember: Boolean = false` last; tags `top.back`, `top.title` | all screens |
| `PixelText` | existing plus `followTextScale: Boolean = true`, `kerning: Boolean = false` last; calls `CutProbe.report` when it ellipsizes | all |
| `object CutProbe { fun report(text: String) }` | `Log.w("AgiriCut", text.take(60))` only when `BuildConfig.TEST_HOOKS`; also called by nothing else in Wave 0 (no `CText` hook) | ROBOT's cut check |
| `SwipeRow`, `SwipeAction`, `Skeleton`, `EmberLine`, `PixelTitle`, `PixelPress`, `DitherScrim`, `PanelVariant`, `PixelShapes` | as DESIGN 3.5 to 3.9; no Wave 0 screen uses them; tags `skeleton`, `ember` | none (cut-first allowed) |
| `DesignGallery` | `@Composable fun DesignGallery()`; root tag `gallery`; buttons `gallery.sheet`, `gallery.dialog`, `gallery.note`, `gallery.note.error`, `gallery.note.action` | NAV route, ROBOT |
| `PixelIconView` | draws mirrored (`graphicsLayer scaleX = -1f`) when `LocalLayoutDirection` is RTL and `Mirroring.iconMirrors(icon.name)`; `PixelIconButton` label fallback `EnumLabels.icon(icon.name)?.let { t(it) } ?: icon.name` | TEXT's tables |

### 3.6 Texts (TEXT provides; package `app.ui.text`)

| Symbol | Form | Consumers |
|---|---|---|
| `t`, `tn` | `@Composable fun t(@StringRes id: Int, vararg args: Any): String`; `@Composable fun tn(@PluralsRes id: Int, count: Int, vararg args: Any): String` (the count is passed again in `args` when the text shows it) | WORDS (all UI), DESIGN (one site) |
| `ProvideTexts` | `@Composable fun ProvideTexts(content: @Composable () -> Unit)`; `LocalTextsRevision`; placed by NAV: `LocaleScope(language) { ProvideTexts { PocketTheme(appearance) { AppShell() } } }` | NAV |
| `Texts` | `install(context)`, `get(context, @StringRes id, vararg args)`, `getQuantity(context, @PluralsRes id, count, vararg args)`, `text(id, vararg args)` and `textQuantity(...)` (no Context, need `install`), `localeOf(context): Locale`, **`currentLocale(): Locale`** (non-composable, chosen language, needs `install`), `reloadPacks(context)`, `revision: StateFlow<Int>`, `describe(): String`; `@Composable fun appLocale(): Locale` (**new**, `LocalConfiguration.current.locales[0]`). **After `Texts.installForTest(...)` (what `TestTexts.installEnglish()` calls), `get(context, ...)` and `getQuantity(context, ...)` ignore the Context and resolve through the installed test source; they never touch `context.resources` in that state** (review fix: `RuntimeManagerInitTest` passes `ContextWrapper(null)`, whose `resources` throws) | WORDS, CONFIG (`install`, `describe`) |
| `TestLanguage` | `const val TAG = "en-XZ"`, `isAvailable(context)` | NAV link accepts the tag, robot `-Var lang=en-XZ` |
| `EnumLabels` | `@StringRes fun icon(enumName: String): Int?`, `avatar(enumName: String): Int?`; keys `eng_icon_<enum lowercase>` (59) and `eng_avatar_<enum lowercase>` (16) | DESIGN, WORDS (`BotEditorScreen`); **not cuttable** |
| `Mirroring` | `MIRRORED = {BACK, SEND, PLAY, SHARE}`, `iconMirrors(iconName)` | DESIGN |
| `TextSource`, `LayeredTexts`, `TextKey`, `TextFormat`, `PluralRules`, `LanguagePacks`, `PackTextSource`, `PackStore`, `ProtectedTexts` | pure core and packs, as TEXT 3.2 to 3.5 | TEXT only (no other area uses them in Wave 0) |
| `TestTexts` (test source) | `installEnglish()`, `reset()` | WORDS adds `@Before` in four classes |
| Capitals | display text never changes case in code; the two display sites that did are converted by WORDS: `Texts.currentLocale()` at `HermesHome.kt` skill title, `appLocale()` at `ChatQuickBar.kt` effort fallback (`#62`) | WORDS |
| Engine keys | `eng_*` of TEXT 5.1 (groups CE, TERM, ATT, UI literals, RT, A11Y) are used by WORDS exactly as SWEEPS.md 2.4 and 2.5 | WORDS |

### 3.7 Stage and scene clock (STAGE provides; package `app.ui.pixel`)

| Symbol | Form | Consumers |
|---|---|---|
| `HearthScene(state: HearthState, modifier: Modifier = Modifier, progress: Float? = null, mood: MascotMood? = null, stokeBoost: Float = 0f, celebration: Boolean = false)`, `HearthState`, `hearthSkyTop` | unchanged signature; the picture does not change; tag `stage.hearth` on its `Spacer` | HomeScreen, HomeScrim, AppearanceSection, PocketApp |
| `SpriteBank` | `init`, `get`, `find`, `all`, `loadErrors` unchanged; **new** `preload(context: Context)` (non-blocking, idempotent, never throws), `isReady: Boolean` | CONFIG (`PocketApp`), CONFIG Diagnostics (`loadErrors`) |
| `rememberSceneClock(active: Boolean, maxFps: Int = 30): LongState`, `rememberSpritesReady(): State<Boolean>`, `rememberClock(active)` (delegates) | display-tied, capped at 30 per second | STAGE, `PixelImage` |
| `Mascot`, `MascotMood`, `PixelImage`, `Fire`, `Avatar`, `AvatarId` | unchanged | DESIGN (`PetNote`), all |
| Stage core (`PixelStage`, `SceneRenderer`, `StageGrid`, `FrameGate`, `ScenePalette`, `SceneState`, `SceneQuality`, `Argb`, `EmberSet`) | pure Kotlin in `ui/pixel/stage/`; no other area uses it in Wave 0 | tests |

### 3.8 Chat (STREAM)

| Symbol | Form |
|---|---|
| `ui.chat.ChatScreen(convId: String)` | unchanged, called by `AppRoot` |
| `parseMarkdown`, `markdownToPlain`, `MdBlock`, `MdSpan`, `MarkdownText` | public behaviour unchanged (used by `Presence`, `ChatListScreen`, `StarredScreen`, `VoiceAndHighlightTest`) |
| `ChatScreenState.say(text: String, kind: PetNoteKind = PetNoteKind.INFO)` | calls `PetNotes.show(text, kind)` directly (`#41`); `toastNotSent()` uses `ERROR`; the seven old toasts use SUCCESS or ERROR as DESIGN 3.10 says |
| Engine | `ChatEngine` public API unchanged (`Turn` internals only); `StreamPush`, `StreamSmoother`, `MarkdownStream`, `StreamingMarkdown`, `ToolOutputSheet` are internal to STREAM: no other area uses them |
| Chat top bar | applies `Modifier.navSharedElement(SharedKeys.chatAvatar(convId))` on the avatar and `navSharedBounds(SharedKeys.chatTitle(convId))` on the title directly; **`LocalChatAvatarModifier` does not exist** (`#35`) |
| Tags written by STREAM | 3.12 |

### 3.9 Toast wrappers and moved helpers (SWEEP and WORDS create)

`internal fun toast(ctx: Context, message: String, kind: PetNoteKind = PetNoteKind.INFO)` in `ui/settings/CtlUi.kt` and `internal fun showToast(ctx: Context, message: String, kind: PetNoteKind = PetNoteKind.INFO)` in `ui/routines/RoutineUiParts.kt` (both bodies `PetNotes.show(message, kind)`). `internal fun themeNameRes(id: ThemeId): Int` and `@Composable internal fun themeName(id: ThemeId): String` in `ui/settings/AppearanceSection.kt` (W5), used by `SettingsScreen.kt` (W5) and `ChatSlashActions.kt` (W1). Removed everywhere: `ui.settings.ConfirmDialog`, `ui.chat.ConfirmDialog`, `ui.chat.ActionSheet`, `ui.chat.SheetAction`, the private `ConfirmDialog` of `RoutineEditorScreen.kt`.

### 3.10 Practice model and guest configuration (ROBOT and CONFIG; `#10`, `#11`, G7)

| Symbol | Form | Provider | Consumers |
|---|---|---|---|
| `runtime.PracticeModel` (JVM-loadable: no Android type in a constant or initialiser) | `MODEL_ID = "practice-model"`, `PROVIDER = "custom"`, `API_KEY = "practice"` (not a secret), `ASSET_SCRIPT = "practice_model.py"`, `ASSET_START = "practice-start.sh"`, `HOST_SCRIPT_NAME = "practice_model.py"`, `HOST_START_NAME = "start.sh"`, `GUEST_DIR = "/opt/agiri/practice"`, `GUEST_START = "/opt/agiri/practice/start.sh"`, `GUEST_REQUEST_LOG = "/opt/agiri/practice/requests.jsonl"`, `SMOKE_PORT = 18080`, `val KEYWORDS: List<String>` (15, below), `fun baseUrl(port: Int): String` (`http://127.0.0.1:<port>/v1`, `IllegalArgumentException` unless 1..65535), `fun hostDir(paths: Paths): File` (`File(paths.rootfs, "opt/agiri/practice")`), `fun install(context: Context, paths: Paths): File` (both assets into `hostDir`, atomic, only when bytes differ, `start.sh` mode 0700, `IOException` on failure), `internal fun installInto(dir: File, script: ByteArray, start: ByteArray): Boolean`, `fun guestCommand(port: Int): List<String>` = `listOf("sh", GUEST_START, port.toString())` | ROBOT | CONFIG (`RuntimeManager`, `HermesConfig`, `ProviderConfig`), PocketApp |
| Keywords (python `KEYWORDS = (...)` and Kotlin equal as sets) | `RUN_TOOL ASK_APPROVAL LONG THINK SLOW FAIL MEMORY FORGE MEDIA PROMPT_HAS PROMPT_SIZE BALLAST TOOLS STATS PAUSE`; priority FAIL, ASK_APPROVAL, RUN_TOOL, MEMORY, FORGE, MEDIA, THINK, SLOW, PAUSE, LONG, PROMPT_HAS, PROMPT_SIZE, BALLAST, TOOLS, STATS; `TOOLS` answers `tools: <sorted tool names, comma separated>`; `STATS` answers `requests=<n> titleCalls=<m>` (a title call = a request whose text contains `You name chat sessions`); `PAUSE`: `First half of the answer.`, 5 s silence, ` Second half.`; `RUN_TOOL big` runs a command that prints at least 120 lines of 80 characters; `LONG` text contains a heading, bullets, a numbered list, one fenced code block, a table, bold, inline code and a link; request log lines also carry `heads` (first 60 characters of each message); `/v1/models` entries carry `context_length` and `max_model_len` of 128000 and list `mock-model` and `practice-model` | ROBOT | STREAM scenarios, CONFIG scenarios |
| `runtime/practice-start.sh` | POSIX sh; `PORT [COMMAND [ARGS...]]`, default command `hermes gateway run --replace -v`; python = first of `/root/.hermes/tools/python-*/bin/python3`, `/root/.hermes/installs/*/environments/*/venv/bin/python`, `python3` on PATH (the guest has none on PATH); starts `practice_model.py --port PORT --log requests.jsonl --media-dir media --watch-parent` in its own directory, waits up to 10 s for `/health`, exit 70 or 71 on failure, then `exec`s the command | ROBOT | CONFIG, smoke |
| `runtime.GuestPrefs` | `GuestOptions(webKeylessFallback: Boolean = true, practicePort: Int? = null)` with `practice: Boolean`; `class GuestPrefs { val webKeyless: StateFlow<Boolean>; val practiceModel: StateFlow<Boolean>; fun setWebKeyless(on); fun setPracticeModel(on); fun enforceExpiry(now: Long = System.currentTimeMillis(), isTwin: Boolean = BuildConfig.IS_TWIN); fun practiceMinutesLeft(now: Long = System.currentTimeMillis()): Int?; companion { fun get(context): GuestPrefs; @Volatile var practiceActive: Boolean; const val PRACTICE_MAX_MS = 7_200_000L; fun expired(sinceMs: Long, nowMs: Long, isTwin: Boolean): Boolean } }` (SharedPreferences `guest`; `practiceActive` mirrors the flag for code that has no Context and **is set in the constructor from the stored value (after the cold-start reset below) and in `setPracticeModel`**). **Expiry (review fix):** `setPracticeModel(true)` stores `practice_since` (epoch ms) beside the flag; when `!BuildConfig.IS_TWIN` the flag is **reset to false at every cold process start** (the constructor, reached from `PocketApp.onCreate`) and `enforceExpiry` turns it off once `expired(since, now, isTwin)` (pure: `!isTwin && now - since > PRACTICE_MAX_MS`); `enforceExpiry` is called by `get`, by `RuntimeManager.launchHermes` before it reads the options, and by `onGuestOptionsChanged`, so an expired flag never survives into the next Hermes launch. `practiceMinutesLeft` is null on the twin or while off. The twin keeps the flag until switched off. Every test that sets `GuestPrefs.practiceActive` resets it to false in `@After`. | CONFIG-R | ROBOT-P (`RuntimeSection`, `PracticeBanner`, which shows the minutes left on a non-twin build), `WebLookupsRow`, `PocketApp` |
| `ProviderConfig` | `renderModelYaml`, `renderGuardYaml(o: GuestOptions = GuestOptions())`, `renderYaml(i, o)`, `renderPracticeYaml(port: Int, o)`, `renderPracticeEnv(apiServerKey: String, apiPort: Int)`, `AGENT_TOOLSETS` (10), `WITHHELD_TOOLSETS` (`browser cronjob image_gen tts connections`); `savedProblems(settings, secrets)` returns `emptyList()` while `GuestPrefs.practiceActive` (S5 finding; `ProviderConfigTest` sets `practiceActive = false` in `@Before`, so test order cannot leak the process-global flag) | CONFIG-R | smoke fixtures, tests |
| `HermesConfig` | `plan(inputs, apiServerKey, apiPort, options, hasServiceAccount, hasUserCredentials): GuestFiles`; **`class GuestFiles(val env: String, val yaml: String, val credentials: CredentialAction)` with `enum class CredentialAction { INSTALL_SA, INSTALL_ADC, REMOVE_BOTH, UNTOUCHED }`** (review fix; replaces the draft's two booleans): `INSTALL_SA` installs the service account and deletes the ADC file, `INSTALL_ADC` the reverse, `REMOVE_BOTH` is today's behaviour for the other modes, and **practice gives `UNTOUCHED`: `apply` neither writes nor deletes either credential file** (the draft's "both flags false" would have deleted them, against 1.3); `apply(paths, settings, secrets, apiServerKey, apiPort, options: GuestOptions = GuestOptions())`; `launchCommand(launcherPath: String, practicePort: Int?): List<String>` = today's list when null, else `listOf("/system/bin/sh", launcherPath, "--") + PracticeModel.guestCommand(practicePort)`; in practice `GuestFiles` leaves the guest's credential files alone (`UNTOUCHED`: no install, no delete; `HermesConfigPlanTest` replaces `normalModeInstallsOnlyWhatTheModeNeeds`'s boolean asserts with `credentials == INSTALL_SA` and adds `practiceLeavesCredentialsUntouched`) | CONFIG-R | RuntimeManager |
| `RuntimeManager` | `companion const val STILL_STARTING = "still starting"` (the text built at the old line 465); `fun onGuestOptionsChanged(restartIfRunning: Boolean)`; `@Volatile var lastGuestOptions`, `guestCodeReport`. **`lastGuestOptions` is set at the start of every launch (before the configuration is written) and describes the configuration last written to the guest; it is kept until the next launch or rewrite writes another.** `onGuestOptionsChanged(true)` restarts in `Running` or `Starting` as drafted **and also calls `start()` when the state is `Error` and `lastGuestOptions.practice` differs from the new switch value** (a failed practice launch, exit 70 or 71, must not leave Hermes stopped after the switch is turned off) | CONFIG-R | HomeScreen (S3), RuntimeSection (ROBOT-P), robot |
| Fixtures | `rootfs/smoke/fixtures/practice.{yaml,env}` (port 18080, model `practice-model`, inline `api_key: "practice"`), `studio-nokeyless.{yaml,env}`; six existing `.yaml` get the guard block | CONFIG-R | CI smoke, `ProviderConfigTest` |
| `ui.settings.PracticeBanner` | `@Composable fun PracticeBanner(modifier: Modifier = Modifier)` in `ui/settings/PracticeBanner.kt` (package `dev.pocketagent.app.ui.settings`); draws nothing while `GuestPrefs.practiceModel` is false; on a non-twin build it also shows the minutes left (`GuestPrefs.practiceMinutesLeft()`, plurals key `rbt_practice_left`); tag `practice.banner` | ROBOT-P | NAV (`AppShell`, import `dev.pocketagent.app.ui.settings.PracticeBanner`) |
| Host and guest paths | host `files/rootfs/opt/agiri/practice/{practice_model.py,start.sh,requests.jsonl,media/,practice.log}`; guest `/opt/agiri/practice/...` (outside HERMES_HOME: not in backups, not in `UserState`) | ROBOT | robot `expectFile` |

### 3.11 Diagnostics, export logs, crash offer, privacy row (CONFIG provides)

| Symbol | Form | Consumers |
|---|---|---|
| `Diagnostics.exportLogs(): File`, `exportLogsIntent(): Intent?` | BLOCKING; zip `cache/diagnostics/agiri-logs-<yyyyMMdd-HHmmss>.zip` (`app.log(.1)`, `runtime.log(.1)`, `last-launch.txt`, `report.txt`, redacted), chooser `ACTION_SEND` through the existing FileProvider path | `MainViewModel` |
| `MainViewModel.shareLogsIntent()` | `suspend fun shareLogsIntent(): Intent?` (IO inside; `null` on failure) | ROBOT's button |
| `ui.diag.DiagnosticsExtras()` | `@Composable fun DiagnosticsExtras()`: exit-reason card, guest-code card (no button); tags `diag.exitReasons`, `diag.guestCode`; **not cuttable as a symbol** | ROBOT (`DiagnosticsSection`) |
| `ui.diag.CrashReportOffer()` | `@Composable fun CrashReportOffer()`: a `PocketDialog` while `PocketApp.crashReports.offer` is non-null; tags `crash.offer`, `crash.copy`, `crash.dismiss` | NAV places it once |
| `ui.diag.WebLookupsRow()` | `@Composable fun WebLookupsRow()`; tag `privacy.webLookups`; calls `onGuestOptionsChanged(false)` | `BehaviourSection` (CONFIG) |
| `PocketApp` members | `lateinit var guestPrefs: GuestPrefs; private set`, `lateinit var crashReports: CrashReports; private set` | CONFIG, ROBOT |
| `HermesApi` / `Transcript` | `Transcript.splitLong(text, max = 60_000)`, `MAX_PART_CHARS`; `ChatRequest.toJson` splits user and assistant text parts (CONFIG draft R1); system messages split in `Transcript.build` | CONFIG-R |
| Cross-split, provided by CONFIG-R | `runtime.GuestCodeKind`, `runtime.GuestCodeEntry`, `GuestCodeScan.summary(entries): String` exactly as CONFIG.md 3.6; `RuntimeManager.guestCodeReport: List<GuestCodeEntry>`; `RuntimeManager.lastGuestOptions`; `GuestPrefs.practiceModel` | CONFIG-D (`Diagnostics`, `DiagnosticsExtras`) |
| Cross-split, provided by CONFIG-D (used in 3.13) | `diag.CrashReports(context: Context, diagnostics: Diagnostics)` (the other constructor parameters keep their drafted defaults), `val offer: StateFlow<CrashOffer?>`, `fun evaluate()` (BLOCKING, off the main thread); `Diagnostics`'s constructor is unchanged | CONFIG-R (`PocketApp`) |
| `cfg_priv_*` keys | live in `res/values/strings_privacy.xml` (CONFIG-R), every other `cfg_` key in `strings_config.xml` (CONFIG-D) | TRANSLATE |

### 3.12 Test tags (exact strings; `testTagsAsResourceId` comes from `robotRoot()`; one tag per node, never two on one modifier chain)

| Tag | Written by (file) |
|---|---|
| `RobotTags` constants and functions (ROBOT's `ui/RobotTags.kt`): `settings.runtime.state/start/stop/restart/practice`, `settings.diag.export` | ROBOT `RuntimeSection.kt` |
| `home.state`, `home.start` | S3 `HomeScreen.kt` |
| `chats.new`, `chats.row.<i>` | S2 `ChatListScreen.kt` |
| `chat.input`, `chat.send`, `chat.stop`, `chat.attach` (`CHAT_ATTACH`), `chat.mic` (`CHAT_MIC`), `chat.approval.once/session/always/deny` | S2 `ChatInput.kt` |
| `chat.forged.chat` ("Chat now" of a forged bot card) | S1 `AttachmentUi.kt` |
| `chat.list`, `chat.msg.<i>` (0 = newest, on the list item wrapper), `chat.tool.<i>` (tool card, counted among tool cards), `chat.thought`, `chat.memory.open`, `chat.menu` (the "more" button, replaces the draft's `chat.more`), `chat.find` | STREAM |
| `chat.screen`, `chat.controls`, `chat.search`, `chat.empty`, `chat.msg.user/bot/tool/error/note` (on the bubble inside the row), `chat.msg.bot.live` (replaces `chat.msg.bot` while streaming), `chat.pending`, `chat.jump`, `chat.approval` (wrapper), `chat.tool.sheet/output/copy/close` | STREAM |
| `hermes.tab.soul/memory/user/skills/config/logs`, `hermes.doc.editor/save`, `hermes.skill.new/name/create/delete`, `hermes.skill.row.<name>` | S4 |
| `terminal.input/run/output` | S4 `TerminalScreen.kt` |
| `settings.section.<provider usage behaviour appearance voice language backup runtime diagnostics about>` | S5 `SettingsScreen.kt` |
| `usage.today`, `usage.budget.limit/warn/block`, `behaviour.idle.minutes`, `backup.save/restore/result` | S5 |
| `bots.export`, `bots.import`, `bots.row.<i>` | S3 (`BotCard` gains `modifier: Modifier = Modifier` as its last parameter, a named exception) |
| `nav.bar`, `tab.*`, `screen.*`, `share.dialog/new/item` | NAV |
| `sheet`, `sheet.scrim`, `sheet.handle`, `sheet.title`, `sheet.action.<i>`, `dialog`, `dialog.title`, `dialog.confirm`, `dialog.cancel`, `petnote`, `petnote.action`, `skeleton`, `ember`, `top.back`, `top.title`, `gallery`, `gallery.sheet/dialog/note/note.error/note.action` | DESIGN |
| `stage.hearth` | STAGE |
| `diag.exitReasons`, `diag.guestCode`, `crash.offer/copy/dismiss`, `privacy.webLookups` | CONFIG |
| `practice.banner` | ROBOT |

`RobotTags` has no `HERMES_SKILL_DELETE_CONFIRM` and no `BACKUP_CONFIRM` (scenarios use `dialog.confirm`); it gains `CHAT_ATTACH`, `CHAT_MIC`. `ScenarioFilesTest.everyTagIsKnown` accepts exactly the tags of this table.

### 3.13 `PocketApp.kt`: where init goes (CONFIG writes all of it)

In order, inside `onCreate`:
1. `AppLog.init(this)` (existing; the `:restart` process returns before it).
2. **TEXT:** `dev.pocketagent.app.ui.text.Texts.install(this)` directly after.
3. existing `settings`; then **CONFIG:** `guestPrefs = GuestPrefs.get(this)` (before anything that can call `ProviderConfig.savedProblems`).
4. existing `diagnostics`; then **CONFIG:** `crashReports = CrashReports(this, diagnostics)`.
5. **STAGE:** the old `SpriteBank.init(this)` try-block becomes `dev.pocketagent.app.ui.pixel.SpriteBank.preload(this)`.
6. the engine's `provider = { ... }` lambda: `{ if (runtime.lastGuestOptions.practice || GuestPrefs.practiceActive) PracticeModel.PROVIDER else ProviderConfig.providerName(settings.providerInputs("")) }` (review fix). The switch flips at once but Hermes changes configuration only after its asynchronous restart: while either side says practice, a per-chat or per-bot model override goes out as `provider=custom` (`ModelIds.fits` accepts it) to a Hermes that runs the practice configuration, never as `vertex` or another real provider. `lastGuestOptions` is described in 3.10; `GuestPrefs.practiceActive` is valid from the constructor on (3.10), so the lambda is correct from the first call after step 3.
7. last, after `routineScheduler.rescheduleAll()`: `wave0Slots()`, a private function with one try/catch per slot and these slots only: `slot: CRASH -> appScope.launch(Dispatchers.IO) { crashReports.evaluate() }`. No other area has an init line in Wave 0 (an area that needs one writes `slot: <AREA>: <exact line>` under REQUESTS for the next wave).
8. the start log line of 3.1.

### 3.14 String keys used across areas

`cfg_diag_exporting`, `cfg_diag_export_failed`, `cfg_diag_share_subject` (CONFIG keys; ROBOT's button uses the first two, CONFIG's intent the third); `rbt_export_logs`, `rbt_export_logs_hint`, `rbt_practice_on` (ROBOT keys; `PracticeBanner` and `RuntimeSection`); `eng_*` (TEXT keys, used by WORDS); `design_note_dismiss`, `design_ember_active`; existing `ui_dlg_close`, `ctl_loading`, `cancel`. The draft keys `cfg_diag_export_title` and `cfg_diag_export_sub` are **not created** (`#52`).

## 4. Resolution of every REQUEST

| # | From to | Request | Ruling |
|---|---|---|---|
| 1 | BUILD to NAV | gate debug links by `IS_TWIN` | accepted changed: `TEST_HOOKS` (G3); only `seed` needs `IS_TWIN`. BUILD also drops `-Pperf`/`twin-perf` (three variants, G2) and moves overlays to `app/src/twin/` |
| 2 | BUILD to NAV | future `VIEW` filter uses `${deepLinkScheme}` | accepted (no filter exists in Wave 0) |
| 3 | BUILD to CONFIG | `PocketApp` start log line | accepted (3.1) |
| 4 | BUILD to CONFIG | Diagnostics line `variant / signing key / ci run` | accepted; CONFIG adds also `practice model: on\|off`, `Texts.describe()`, sprite load errors |
| 5 | BUILD to TEXT | `-Check` flags, pwsh, committed generated files, scan only `src/main/res` | accepted (TEXT 3.6 already; the generated tree is `app/src/debug/res/values-en-rXZ/`) |
| 6 | BUILD to ROBOT | dot-source `build-ids.ps1`, default twin, `-Owner` switch, `Get-PaComponent` | accepted |
| 7 | BUILD to lead | `AGENTS.md`, CI secret names | lead |
| 8 | ROBOT to BUILD | assets `practice_model.py`, `practice-start.sh` | accepted (3.1) |
| 9 | ROBOT to BUILD | CI steps (`sh -n`, `py_compile`, selftests) | accepted; list in 3.2 (adds STAGE's `craft_art.py --check`, lint and strings steps) |
| 10 | ROBOT to CONFIG | `RuntimeManager` practice launch, `STILL_STARTING` | accepted changed: CONFIG's `GuestPrefs` and `GuestOptions` carry the switch and port; the guest command is `PracticeModel.guestCommand(port)` (G7); no `PracticePrefs` |
| 11 | ROBOT to CONFIG | `HermesConfig.apply(inputsOverride)` | rejected: practice files come from `renderPracticeYaml/Env`, the owner's inputs are never read |
| 12 | ROBOT to CONFIG | provider lambda follows the switch | accepted changed (3.13 item 6) |
| 13 | ROBOT to CONFIG | fixtures, golden test, Diagnostics line, `shareLogsIntent` | accepted; `shareLogsIntent` is `suspend` (3.11) |
| 14 | ROBOT to NAV | gallery route key; seeding link (lead) | accepted: `Route.Gallery`, key `gallery`; the `seed` extra is NAV's `DebugLinks`, the seeder is ROBOT's `DebugSeeder` (twin only, G4) |
| 15 | ROBOT to DESIGN | `PocketSwitchRow` modifier, `CutProbe`, `PixelText` call | accepted (3.5) |
| 16 | ROBOT to STREAM | tags | accepted changed: STREAM writes `chat.list`, `chat.msg.<i>`, `chat.tool.<i>`, `chat.thought`, `chat.memory.open`, `chat.menu`, `chat.find`; input, send, stop, approvals belong to S2 (`ChatInput.kt`); `chat.forged.chat` to S1 |
| 17 | ROBOT to SWEEP | tags in 10 files | accepted per SWEEPS.md R7 plus: S1 `chat.forged.chat`; S3 `BotCard` modifier; no confirm-dialog tags |
| 18 | ROBOT to lead | `mock_llm.py`, `smoke-test.sh`, `.gitattributes` unowned | `mock_llm.py` and `.gitattributes` ROBOT; `smoke-test.sh` CONFIG (lead) |
| 19 | DESIGN to NAV | `PetNoteHost` placement, toast at `AppRoot` 296, tweens, gallery route | accepted; gate by `TEST_HOOKS` |
| 20 | DESIGN to ROBOT | lint exclusions, fifth and sixth counters | accepted (already in ROBOT 3.6) |
| 21 | DESIGN to TEXT | tolerate missing `design_*` translations | rejected: TRANSLATE precedes every check |
| 22 | DESIGN to SWEEP and STREAM | 3.10 replacements in their files | accepted; STREAM does them in its files in stage 1 |
| 23 | DESIGN to lead | `"haptics"` in `BackupCore.PREF_SCHEMA` | rejected: no Settings row in Wave 0, default FULL; note in `AGENTS.md` |
| 24 | STAGE to CONFIG | R1 `SpriteBank.preload` | accepted (3.13) |
| 25 | STAGE to CONFIG | R2 sprite load errors in Diagnostics | accepted (cut-2 line) |
| 26 | STAGE to BUILD | R3 `craft_art.py --check` in CI | accepted |
| 27 | STAGE to ROBOT | R4 Home parity shot, Animations Off | accepted: scenario `stage-parity` (manual compare), `pref appearance animations` twin only; on owner-debug the shot is taken by hand |
| 28 | STAGE to DESIGN | R5 keep `PixelImage`, `Mascot` stable | accepted |
| 29 | NAV to BUILD | `resValue pa_application_id` for `shortcuts.xml` | rejected: BUILD's twin overlay file; main `shortcuts.xml` stays literal |
| 30 | NAV to BUILD | `Get-PaComponent` for every `am start`, tile | accepted |
| 31 | NAV to BUILD | release keeps `DEBUG == false` | accepted |
| 32 | NAV to ROBOT | `link` command line, 400 ms wait, `I/Nav:` log fallback | accepted (3.3) |
| 33 | NAV to DESIGN | `robotRoot()` in `PocketDialog` and `PocketSheet`; effective `motion.level` | accepted |
| 34 | NAV to TEXT | read `LocalAppLanguage` where needed | accepted |
| 35 | NAV to STREAM | shared modifiers in the chat top bar | accepted changed: direct calls; no `LocalChatAvatarModifier` |
| 36 | NAV to SWEEP | shared modifiers on rows, bot cards, editor header | accepted (recipe R9, section 5.9) |
| 37 | NAV to lead | `shortcuts.xml` owner, toast symbol, tab-root retention | NAV owns the file (no edit); toast resolved (3.5); tab-root state is kept (N7 stays; N8 cut first) |
| 38 | STREAM to DESIGN | `PocketSheet` signature, unbounded content | accepted (3.5; the `PocketSheetScope` receiver is compatible with a `ColumnScope` lambda) |
| 39 | STREAM to DESIGN | tags `top.back`, `top.title` | accepted |
| 40 | STREAM to DESIGN | tags `dialog.confirm`, `dialog.cancel` | accepted |
| 41 | STREAM, SWEEP, DESIGN | who converts the seven chat toasts | STREAM converts all in stage 1 through `say(text, kind)`; SWEEP batch S6 is deleted |
| 42 | STREAM to NAV | `testTagsAsResourceId` at the root; keep `ChatScreen(route.convId)` | accepted |
| 43 | STREAM to SWEEP | tags in `ChatInput.kt` incl. `chat.edit.cancel` | accepted without `chat.edit.cancel` (no scenario uses it) |
| 44 | STREAM to SWEEP | `sheet.action.<i>` on `ActionSheet` | rejected for SWEEP: DESIGN's `ActionSheet` writes them |
| 45 | STREAM to ROBOT | `LONG` content, `PAUSE`, `RUN_TOOL` large output | accepted (3.10) |
| 46 | STREAM to ROBOT | 200-message chat by `run-as` | accepted changed: `seed chat:200:400` link |
| 47 | STREAM to lead | owns `StreamPush.kt`, `strings_stream.xml` | accepted |
| 48 | CONFIG to lead | `api/HermesApi.kt` has no owner | CONFIG owns it (lead) |
| 49 | CONFIG to NAV | `CrashReportOffer()` once over every route | accepted |
| 50 | CONFIG to ROBOT | `heads` log field, `TOOLS`, `STATS` | accepted |
| 51 | CONFIG to lead | `smoke-test.sh` owner | CONFIG (lead) |
| 52 | CONFIG to ROBOT | `DiagnosticsExtras()` call, switch via `GuestPrefs`; the Export logs button | accepted; per the lead ROBOT draws the button and CONFIG owns the zip and intent, so `cfg_diag_export_title` and `cfg_diag_export_sub` are not created |
| 53 | CONFIG to BUILD | nothing for the twin | n/a |
| 54 | CONFIG open questions | `connections` withheld; web switch in backup; crash offer as dialog; owners | withheld accepted (lead D1, logged as a deviation from the plan); web switch not in backup (lead); dialog over any screen (lead); owners as above |
| 55 | TEXT to NAV | `ProvideTexts` inside `LocaleScope` | accepted |
| 56 | TEXT to NAV | debug link sets the saved language | rejected: `DebugOverrides.language` (in memory); `LocaleScope` and so `t()` follow it; `en-XZ` accepted; non-composable code ignores it |
| 57 | TEXT to CONFIG | `Texts.install` after `AppLog.init`; `Texts.describe()` in Diagnostics | accepted |
| 58 | TEXT to BUILD | `.gitignore` the generated language | rejected: it is committed (lead) |
| 59 | TEXT to BUILD | local-build runs the generator; CI steps; release keeps `en` and entry names | accepted changed: `-Check` only, plus `tools:keep` for strings and plurals |
| 60 | TEXT to DESIGN | mirrored icons, `EnumLabels` label fallback, drop `uppercase` in `SectionHeader` | accepted; `SectionHeader` keeps `uppercase(locale)` in Wave 0 (lead) |
| 61 | TEXT to ROBOT | lint counters, scenario `stretched-all` | accepted; the scenario is `sweep-stretched-and-mirrored` |
| 62 | TEXT to lead | factor 1.4, WORDS list, plural fix, tag, presets, packs in backup | all as the lead ruled; capitals: display sites use the chosen locale (`Texts.currentLocale()`, `appLocale()`); `ChatScreen.kt:630` keeps `it.name.lowercase()` (command vocabulary) |
| 63 | SWEEP to ROBOT | `RuntimeSection` `ConfirmDialog` call (`body =`, import) | accepted |
| 64 | SWEEP to ROBOT | `directText` pattern | accepted (5.2) |
| 65 | SWEEP to ROBOT | constants `CHAT_ATTACH`, `CHAT_MIC` | accepted |
| 66 | SWEEP to DESIGN | `confirmTag` parameters | rejected; `dialog.confirm` |
| 67 | SWEEP to DESIGN | `PocketSwitchRow` modifier | accepted |
| 68 | SWEEP to DESIGN, STREAM | toast disagreement | resolved by `#41` |
| 69 | SWEEP to TEXT | test list incomplete | accepted (4 test classes, WORDS) |
| 70 | SWEEP to lead | `AGENTS.md` note | lead |
| 71 | SWEEP open questions | D1 shapes; `ChatScreen:630`; W10b; ordering; kinds; `ChatInput.kt` | D1: skip (lead), `RoundedCornerShape(` only counted; 630 stays (lead); W10b is part of WORDS (lead); stage order settles BehaviourSection; kinds as DESIGN; `ChatInput.kt` is SWEEP's |

## 5. Amendments per area

### 5.1 BUILD
- Three variants and the gates of 1.2: add `TEST_HOOKS`, `APP_VARIANT` in `{owner, twin, owner-debug}`; `IS_TWIN` = twin only; `-PownerDebug=true` gives the `debug` type without suffix, label or overlays, owner version scheme, `SIGNING_KIND="committed"`, `DEEP_LINK_SCHEME="agiri"`, name without `-test`.
- You no longer do: `-Pperf`, `twin-perf`, B6, `-Perf`, `dist/perf-build` ignore line. `BuildContractTest` branches on `APP_VARIANT` for all three rules (review fix; INTEGRATE runs `-Test -OwnerDebug` and must get exit 0 or 2): twin = suffix `.debug`, `IS_TWIN`, code `max(CI_RUN*100+seq,1)`, name `^0\.2\.\d+(-local\d+)?-test$`; owner-debug = no suffix, `!IS_TWIN`, `TEST_HOOKS`, code `(10000+CI_RUN)*100+seq`, name `^0\.2\.\d+(-local\d+)?$` (`seq` parsed from `-local(\d+)` in `VERSION_NAME`, 0 when absent).
- All twin overlays move from `app/src/debug/` to `app/src/twin/res/`, attached by `android.sourceSets.getByName("debug").res.srcDir("src/twin/res")` only when not `ownerDebug`. `app/src/debug/res/values-en-rXZ/` is TEXT's and stays for both debug variants. `TwinOverlayTest` paths follow. Say this exactly in a comment in `build.gradle.kts`.
- Additionally: copy `rootfs/smoke/mock_llm.py` and `runtime/practice-start.sh` into assets (3.1); `keep.xml` adds `@string/*,@plurals/*`; `local-build.ps1` gets `-OwnerDebug` (builds with `-PownerDebug=true`, installs over the owner's app only through `Assert-OwnerInstallAllowed`: same certificate digest, higher version code, never uninstalls, and refuses with exit 3 unless `dist\owner-backup\` contains `chat\` with at least one file and is newer than the installed app's `lastUpdateTime`; **and the APK's rootfs build id (`assets/rootfs-arm64.info` `build_id`, else `dist\runtime-parts\source.txt` `rootfs_build_id`) equals, trimmed, `adb shell run-as dev.pocketagent.app cat files/rootfs.stamp`; a mismatch refuses with exit 3 unless `-AllowReextract` is given (never in Wave 0; the switch exists so the refusal can name it)**), `-AcknowledgeS2` (required for `-Release -Install` on the owner package, G5), `-Test` honours `-OwnerDebug`, and runs `tools\make-test-language.ps1 -Check` before every debug build (stale: print the command, exit 3). `LocalBuildSafetyTest` forbids exactly these case-insensitive tokens in `tools/local-build.ps1`: `uninstall`, `pm clear`, `settings put`, `--bypass-low-target-sdk-block`, `bypass-low-target-sdk`, `verifier`, `package_verifier`; **`-ExecutionPolicy Bypass` is allowed and expected** (the script's own run instruction and 3.2 contain it; the draft's bare token `bypass` is dropped); `-d` appears only on lines that name `$PaTwinPackage`.
- CI: the steps of the `unit` row of 3.2 **exactly** (it overrides the draft 3.5's unit list; in particular `strings-check.ps1` is called with NO `-Check` flag, because the script has none and PowerShell would reject the call and fail every run); the `apk` and `release` jobs as the draft 3.5 (two APKs, `verify-apk.sh`, size report, secrets only in the sign step), with `$ANDROID_HOME/build-tools/36.0.0` prepended to `PATH` before `verify-apk.sh` and `apk-size.sh`. `local-build.ps1` (5.1) calls `make-test-language.ps1 -Check` through `powershell`, never `pwsh`.

### 5.2 ROBOT (bullets marked [P] are ROBOT-P's, [T] ROBOT-T's; [P+T] both)
- [P] You no longer edit `rootfs/smoke/smoke-test.sh` (CONFIG-R owns it, #18); your request for phase 3 is carried out by CONFIG-R (5.7). Your python and sh must keep what that script needs: the request log lines keep the fields `"POST"` and `"n_msgs"` as today (`smoke-test.sh` reads them), `/health` answers, and `start.sh` exits 71 when its port is taken.
- [P] Practice model: you provide `PracticeModel` of 3.10 only. `PracticePrefs`, `inputs`, `effective`, `guestCommand(port, hermesArgv)`, `GUEST_REQUEST_LOG` under HERMES_HOME and `HOST_DIR_NAME` are gone; `PracticePrefsTest` and the draft's `inputsDrop...`, `effective...`, `renderedConfig...` tests are gone, `guestCommandShape` is `["sh", GUEST_START, "<port>"]`, `installIntoWritesOnlyWhenDifferent` and `keywordsMatchThePythonFile` stay. Paths of scenarios 6, 18, 28 read `files/rootfs/opt/agiri/practice/requests.jsonl`.
- [P] `mock_llm.py` and `practice-start.sh` as in 3.10 (new keywords `TOOLS`, `STATS`, `PAUSE`, `RUN_TOOL big`, richer `LONG`, `heads`, `context_length`, python lookup order). The CI fixture loop and phase 1 and 2 keep using the same file on the host.
- [P] Export logs: you write only the button, using `rbt_export_logs`, `rbt_export_logs_hint`, `cfg_diag_exporting`, `cfg_diag_export_failed`, tag `settings.diag.export`, and `scope.launch { vm.shareLogsIntent()?.let { ctx.startActivity(it) } }`. No own zip. Then `DiagnosticsExtras()`.
- [P] `RuntimeSection.kt`: practice switch bound to `GuestPrefs.practiceModel` (`g.setPracticeModel(v); PocketApp.from(ctx).runtime.onGuestOptionsChanged(true)`), row via `PocketSwitchRow(modifier = ...)`; `STILL_STARTING`; `ConfirmDialog` import and `body =`; no other change. New `ui/settings/PracticeBanner.kt`: `@Composable fun PracticeBanner(modifier: Modifier = Modifier)`, a small pixel panel with `rbt_practice_on`, tag `practice.banner`, draws nothing while `GuestPrefs.practiceModel` is false; NAV places it. On a non-twin build the banner also shows the minutes left (`GuestPrefs.practiceMinutesLeft()`; plurals key `rbt_practice_left`, placeholder `%1$d`, in `strings_robot.xml`, with the usual note).
- [P] `DebugSeeder.kt` (3.3) replaces seeding by `run-as`. `RobotTags` changes per 3.12.
- [T] The `seed` op sends the link and fails with a clear message when the variant line of `app.log` is not `twin`; `{{latestChat}}` reads the `I/Seed: chat <id>` log line first. `pref`, `wipe`, `killGuest`, `expectFile`, restore and image replacement stay twin-only through `run-as` and `pm clear`, **each guarded as below**. Scenario files that need these ops check the variant line first.
- [T] **Guards (review fix).** The owner build is debuggable, so `run-as` and `pm clear` work on `dev.pocketagent.app`; only the following keeps a wrongly flagged scenario from wiping the owner's data. `phone-lib.ps1` has `Assert-PaTwinTarget($Package)`, which throws unless `$Package -eq $PaTwinPackage` AND the latest `app.log` start line says `variant=twin`. **The FIRST statement of every op implementation for `wipe`, `pref`, `seed`, `killGuest`, `expectFile`, restore, `install`, `pick` and `adb` calls it**; the `adb` op also refuses `args` that contain `uninstall`, `clear`, `install`, `settings`, `rm` or `run-as`. New JVM test `PhoneToolsSafetyTest` parses `tools/phone-lib.ps1`, `phone-test.ps1` and `phone-perf.ps1` and asserts that every line containing `pm clear`, `settings put`, `run-as` together with `rm`, `cat >` or `cp`, or `adb` with `install` as subcommand sits in a function whose first statement is `Assert-PaTwinTarget` (or, for `settings put`, in `Set-PaSystemScales`, 1.3).
- [T] Scenario files get `"safe": true` (read, navigate, picture; the 8 below make no chat): `smoke-launch`, `smoke-tabs-labels`, `smoke-hermes-running`, `smoke-terminal-version`, `smoke-settings-walk`, `design-gallery`, `sweep-labels`, `sweep-stretched-and-mirrored`. `-Owner` runs only `safe` ones. `ScenarioFilesTest` pins that **a safe scenario contains none of the ops `wipe`, `seed`, `pref`, `killGuest`, `restore`, `install`, `adb`, `pick`, `scale`, no `type` except into `terminal.input` with the exact text `hermes --version` (`smoke-terminal-version` needs it and it only reads), no `link` extra `seed` or `nav`, and is not flagged `practice`**. The `scale` op therefore never appears in a safe file: the font 2.0 cut check of `sweep-*` is a runner step through `Set-PaSystemScales` (1.3) and is SKIPped with the reason under `-Owner` unless `-AllowSystemSettings` is given.
- [T] **First set (stage 1) and cut-first (review fix).** Write in this order: the 8 safe scenarios, `nav-state-restore` (NAV proofs a, b, c, g; twin only: it uses the `nav` link extra and `kill`, so tonight's NAV proofs on owner-debug are made by hand), `routes.json`, `a11y-allow.json`, `cut-allow.json`, `leak-allow.txt`, and perf `P1`, `P4`, `P5`, `P6`, `P7`. Every other scenario file (the twin-only ones of the 39, then `chat-stream-long`, `chat-stream-pause`, `chat-tool-sheet`, `tools-are-short`, `no-title-call`, `export-logs`, `crash-offer`, `guest-code-report`, `web-switch`, `stage-parity`, perf `P2`, `P3`) is cut-first: no code consumes them and none can run before the twin installs. Scenarios that confirm a dialog use `dialog.confirm`; `chat.msg.<i>`, `chat.tool.<i>` stay; the gallery route is `gallery`; `-Var lang=en-XZ`.
- [T] **Running on the owner's package (review fix).** `phone-test` and `phone-perf` with `-Owner`: (a) before turning practice on, refuse with exit 3 when `dumpsys alarm` lists an alarm of `dev.pocketagent.app` due within 30 minutes (an unparsable listing counts as due); (b) turn practice off through the interface in a `finally` and confirm `practice=off` in the next `app.log` start line; (c) type only plain text, `LONG` or `PAUSE`, never another keyword (a whole-word `MEMORY` or `FORGE` writes into the owner's `MEMORY.md` or `bots.json`); (d) `phone-perf -Owner` runs only `P1`, `P5`, `P6`, `P7` and ONE practice chat for `P4`: before the first typed character it confirms `practice=on` in the latest `app.log` start line (`run-as` works on owner-debug) and the `settings.runtime.practice` switch checked, otherwise exit 3; it creates one chat, sends `LONG 2000` once per repeat, deletes that chat through the interface at the end (also in `finally`) and switches practice off. `P2` and `P3` are SKIP on non-twin builds; the draft's `FILL` fallback is removed. On build 32 (no tags, no debug links) only `P1`, `P6` and `P7` run, with `any:` text selectors.
- [T] Committed fixtures (`tools/scenarios/fixtures/`, `dump-sample.xml`, `gfxinfo-sample.txt`) are hand-written, never captured from the owner's app; `docs/perf/w0.md` names the phone model and Android version only, never `ro.serialno`, the adb serial or any chat text (the repository is public). `System.Drawing` is loaded only inside the `expectColor` op, never at dot-source; `-SelfTest` does not call it (CI runs `phone-test.ps1 -SelfTest` under Linux `pwsh`).
- [T] `lint-ui.ps1`: `directText` uses the pattern `getString\(\s*(R\.string\.|if\b|res\b|subtitle\b|$)|(^|[^.\w])getString\(` plus `stringResource\(`, `pluralStringResource\(`, `getQuantityString\(`, in `ui/ service/ routines/ data/ hermes/ runtime/ voice/` except `ui/text/`; `roundedCorner` only counts (no replacement in Wave 0); exemptions of DESIGN's request; baseline is taken by INTEGRATE (`-Init`), not by you.
- [P] Strings: `rbt_*`, the seven keys of the draft plus the plurals `rbt_practice_left`; no `rbt_` text is a translation of a keyword. [T] Lint and scenario scripts run on 5.1 and pwsh 7 (forward-slash default paths, 1.4).

### 5.3 DESIGN
- Additionally: `ui/components/CutProbe.kt` (3.5, gated by `TEST_HOOKS`) and the `PixelText` call; `PocketSwitchRow(modifier)`; `Modifier.robotRoot()` (import from `ui.nav`) inside `PocketDialog` and `PocketSheet`; tags `top.back`, `top.title`; mirroring in `PixelIconView` (you may edit `PixelIcon.kt` for this only); the `PixelButton.kt:178` fallback through `EnumLabels` and `t(`; `DesignGallery` reachable by NAV's route.
- You no longer do: the `BackupCore.PREF_SCHEMA` request (`#23`); any `BuildConfig.DEBUG` gate (use `TEST_HOOKS`).
- `PetNoteHost` placement is NAV's (`#19`); `SectionHeader` keeps `uppercase(locale)`; capitals written into strings are Wave 1. `PaletteContrastTest.KNOWN_FAILURES` and the golden are INTEGRATE's. `ConfirmDialog` has no tag parameters.

### 5.4 NAV
- Gates: `BuildConfig.TEST_HOOKS` replaces `BuildConfig.DEBUG` everywhere in your draft; `seed` only with `IS_TWIN`. `NavFlags` are runtime `@Volatile var`s (3.3) switched by the `nav` extra. `DebugRequest` gets `seed` and `nav`; `apply` takes the `PocketApp`.
- You no longer do: `res/xml/shortcuts.xml` edit, `resValue`, `LocalChatAvatarModifier`, persisting the language (`#29`, `#35`, `#56`). The file stays owned by you, unchanged.
- Additionally: `Route.Gallery` (key `gallery`, `NavTags.screen`, `stackFor` `[Home, Gallery]`, no bar, `tabIndex`/`rank` like a detail route; every `when` over `Route` stays exhaustive; `NavMotionTest` and `NavTagsTest` cover 12 route kinds); `AppShell` z-order: screens, bottom bar, `PracticeBanner()` (top, below the status bar, no layout effect), `CrashReportOffer()`, the share dialog, `PetNoteHost(Modifier.align(Alignment.BottomCenter).imePadding().navigationBarsPadding().padding(bottom = <measured bar height when the bar shows, else 0.dp>))` last; `LocaleScope { ProvideTexts { PocketTheme { AppShell() } } }`; replace the `Toast` at the attach-failed site by `PetNotes.show(msg, PetNoteKind.ERROR)`; migrate your tweens to `Pocket.motion`; keep `${applicationId}.fileprovider`; the only manifest edit is `android:enableOnBackInvokedCallback="true"` (section 7).
- Consumer lines belong to STREAM and SWEEP (`SharedKeys`); you only provide the symbols, as no-ops when the flag is off.
- **Experiment results (`dist/exp-results/S1-S3-navigation.md`, review fix).** S1 FAILED on the owner's phone: Samsung's gesture pad sends a back key, so a Compose `PredictiveBackHandler` receives one event with progress 0.0 (sometimes two), then completion, all at the moment the finger lifts; no system back preview appears. So (a) `NavFlags.backGestureDrivesScreen` defaults to **false** (3.3; this is your own draft's "if S1 fails" branch, section 10 of NAV.md): the shell uses `BackHandler(enabled = nav.canPop || route != Route.Home) { if (!nav.back()) nav.tab(Route.Home) }` as today and the pop transition plays after the gesture ends; (b) the gesture branch (flag on, `PredictiveBackHandler`) must still treat "one event with progress 0.0, then completion" exactly like a button back: no seek, play the transition's own pop spec, then `nav.back()`, and never leave the stack half-popped when the flow ends without a second event or is cancelled; (c) keep `android:enableOnBackInvokedCallback="true"` (S1 showed it does no harm: the callback is registered as an animation callback and fires once); (d) S3 PASSED: `sharedElements` stays `true`, one `SharedTransitionLayout` around the existing `AnimatedContent`, the two scopes as `CompositionLocal`s defaulting to null, keys unique per `convId`/`botId` (the experiment's whole change was about 40 lines; its debug-build jank is the destination screen's first composition, not the shared element, so no frame-time budget is attached to it); a group chat's `AvatarStack` is shared as one box (only a 1:1 chat was tried). (e) `PracticeBanner()` is imported from `dev.pocketagent.app.ui.settings.PracticeBanner` (3.10). (f) `DebugLinks.apply` writes the line `DebugSeeder.run` returns with `AppLog.i("Seed", line)` (3.3); `seed` and `nav` are both twin-or-hooks-gated as in 3.3 and a safe robot scenario never sends them (5.2).

### 5.5 STAGE
- No change to your file. Confirmations: R1 and R2 are accepted (CONFIG edits `PocketApp.kt`); R3 accepted (CI); `Pocket.anim` is the effective level; `AppClock` is not consumed in Wave 0. Keep `LegacyHearthReference.kt` as the first step, before any edit. `contact_sheet.py` stays cut-first. `tools/art/README.md` states which three `.pxl` files are generated.

### 5.6 STREAM
- You no longer do: `LocalChatAvatarModifier`; the `chat.input/send/stop/attach/mic/approval.*/edit.cancel` tags (S2 writes them); the single deferred toast. The `chat.more` tag is `chat.menu`.
- Additionally: convert all seven toasts through `say(text, kind)` and `PetNotes.show` (`#41`); do DESIGN 3.10 in your files (`rememberHaptics`/`HapticCue`, `Loop.CARET`, `motion.bouncy()`, tweens, `ConfirmDialog` with `closeOnConfirm = true, danger = true`, imports `ui.components.ActionSheet/SheetAction/ConfirmDialog`); leave `RoundedCornerShape(` and literal `dp`; put `Modifier.navSharedElement`/`navSharedBounds` on the top-bar avatar and title; `chat.msg.<i>` on the list item wrapper and the kind tags on the bubble inside; `chat.tool.<i>`, `chat.thought`, `chat.memory.open`, `chat.menu`, `chat.find`, `chat.list`; no tag on a node that already has one.
- `ToolOutputSheet` uses `PocketSheet` of 3.5; `ChatEngineTest` stays untouched.

### 5.7 CONFIG (both agents; [R] = CONFIG-R, [D] = CONFIG-D)
- [D] You no longer do: the Export logs button and the keys `cfg_diag_export_title`, `cfg_diag_export_sub` (ROBOT-P draws the button, `#52`). [R] You no longer do: `HermesConfig.launchCommand(launcherPath, practiceScript)`, `guestScript`, `GUEST_PRACTICE_LOG`, `PracticeModel.installInto(context, hermesHome)` and the path `<hermesHome>/agiri` (G7, 3.10).
- [R] `launchHermes`: `GuestPrefs.enforceExpiry()` first; step 4 is `PracticeModel.install(context, paths)`; step 6 is `HermesConfig.launchCommand(paths.launcher.path, practicePort)`; the run header and `app.log` carry `practice=on|off` and the port. `plan` in practice gives `CredentialAction.UNTOUCHED` (3.10): the guest credential files are neither installed nor deleted; `apply` reads provider inputs and files only when not practice. `lastGuestOptions` is set before the configuration is written and kept (3.10).
- [R] Additionally: `GuestPrefs` with expiry and `practiceActive` set in the constructor (3.10) and `savedProblems` returning empty while it is true (`ProviderConfigTest` resets the flag in `@Before`, every test that sets it resets it in `@After`); `RuntimeManager.STILL_STARTING`; `onGuestOptionsChanged` also restarting out of `Error` (3.10); `PocketApp` in the order of 3.13 (`Texts.install`, `guestPrefs`, `crashReports`, `SpriteBank.preload`, the provider lambda of item 6, `wave0Slots()`, the log line); the draft's fixture loop; `HermesApi.kt` split (draft R1). [D] Additionally: `MainViewModel.shareLogsIntent()` as `suspend`; Diagnostics lines of `#4`; `DiagnosticsExtras()` (cards only) and `CrashReportOffer()`.
- [R] **`rootfs/smoke/smoke-test.sh` phase 3 (review fix; CI's smoke gate stands on it).** The practice model now lives in the guest, so phase 3 must not start the host mock: (1) delete the line `python3 "$HERE/mock_llm.py" --port "$MOCK_PORT" --log "$MOCKLOG" ... &`, `MOCK_PID=$!`, the `sleep 1` after it and the `MOCK_PID` kill in `cleanup` (nothing else uses the host mock; phases 1 and 2 do not); (2) before the boot copy `rootfs/smoke/mock_llm.py` to `$ROOTFS/opt/agiri/practice/practice_model.py` and `runtime/practice-start.sh` to `$ROOTFS/opt/agiri/practice/start.sh` (`chmod 700`), and assert that port 18080 is free (`! curl -s -m 2 http://127.0.0.1:18080/health`; proot shares the host network, so a taken port would make `start.sh` exit 71 and the job red); (3) the config comes from the fixtures `practice.yaml` and `practice.env` as drafted; (4) the gateway starts through the launcher with the guest command: `PA_CLEAN_STALE=1 sh "$LAUNCH" -- sh /opt/agiri/practice/start.sh "$MOCK_PORT" >> "$LOG" 2>&1 &` (a second start function beside `start_gateway`, which keeps `hermes gateway run --replace -v` for the other phases); (5) `MOCKLOG="$ROOTFS/opt/agiri/practice/requests.jsonl"` replaces `$WORK/mock.log` for the role check (`grep '"POST"'`, `n_msgs`) and for the no-title grep, and the file is truncated before the boot (the guest mock appends); (6) `/v1/toolsets` assertions as 3.2 (the set equals `AGENT_TOOLSETS`), and the no-title assertion `sleep 5; ! grep -q "You name chat sessions" "$MOCKLOG"` needs `heads` in the log (ROBOT-P).
- [R] `rootfs/install-hermes.sh` line 57: `--extra all --extra vertex --extra anthropic` **if the lead agrees (section 10)**. Reason (checked in `pyproject.toml` of the shipped Hermes): `anthropic` is not in `[all]` and `agent/anthropic_adapter.py` installs the SDK lazily, which the guard's `security.allow_lazy_installs: false` stops; without the extra the shipped Anthropic provider mode silently breaks. With the extra the smoke script also runs `python -c "import anthropic"` in the guest (through the launcher with `--`, the python of `tools/python-*` as in `practice-start.sh`). Without it, CONFIG-R only leaves a comment at `renderGuardYaml` and the mode is documented as unavailable (lead, `AGENTS.md`).
- [R] `BehaviourSection.kt`: one import and one `WebLookupsRow()` call, nothing else. The privacy row's keys are `cfg_priv_*` in `strings_privacy.xml`.
- [R] `connections` withheld, logged as a deviation. B21 stays half closed until Wave 3 (`AGENTS.md`).

### 5.8 TEXT
- You no longer do: tr and fr files for new keys (TRANSLATE, stage 4); the `.gitignore` of the generated language; persisting the debug language.
- Additionally: `Texts.currentLocale()` and `appLocale()` (3.6); the generated language is committed under `app/src/debug/res/values-en-rXZ/` (run your script once in stage 1 if you can); `EnumLabels` (W9) and group RT (W13) are core (consumers exist); `strings-check.ps1` treats a missing tr/fr key as an error as designed (stage 5 runs after TRANSLATE) and scans only `app/src/main/res`; plural conversions (W11) edit existing keys in all three languages; **W12 (capitals written into strings, the `eng_h_*` keys) is NOT done in Wave 0 (lead: Wave 1)**: do not touch `strings_routines.xml` or any section-header key, and create no `eng_h_*` key (it would be an orphan under `-FailOnUnused`, or change the look elsewhere); `Texts.describe()` and `install` as in 3.6.
- `strings_engine.xml` notes are mandatory; `-FailOnUnused` stays off until INTEGRATE runs it once.
- **`Texts` under test (review fix, 3.6):** after `Texts.installForTest(...)`, `get(context, ...)` and `getQuantity(context, ...)` resolve through the installed source and `nameOf` and never read `context.resources`. `TestTextsTest` also covers `Texts.get(ContextWrapper(null), R.string.eng_rt_stamp_unreadable, "a", "b")` returning the English text, and a `getQuantity` twin of it (`RuntimeManagerInitTest` builds its context from `ContextWrapper(null)`).
- `LanguagePack`, `PackStore`, `PackTextSource` and their tests are cut-first (no Wave 0 consumer); `Texts.reloadPacks` and `install` then work with an empty pack set.

### 5.9 SWEEP (stage 2)
- Batches S1 to S5 only. Recipes of `docs/waves/w0/SWEEPS.md` Part 1 apply with these changes:
  - R1: no S6; `ChatScreenState.say` is STREAM's and already done. After S1 to S5, `rg "Toast\.makeText" ui` finds nothing.
  - R6.b is **skipped** (lead): no `RoundedCornerShape(` is replaced; report each remaining site as `LEFT` only. R3 still deletes the sheet shapes it names. R6.a and R6.c stay.
  - R7: no tags for `HERMES_SKILL_DELETE_CONFIRM`, `BACKUP_CONFIRM`; use `RobotTags.CHAT_ATTACH` and `CHAT_MIC` instead of literals; S1 adds `RobotTags.CHAT_FORGED_CHAT` to the "Chat now" button of `ForgedBotCard` in `AttachmentUi.kt` (report if it has no modifier); S3 adds `modifier: Modifier = Modifier` as the last parameter of the private `BotCard` and tags it `RobotTags.botsRow(i)`.
  - New R9, shared elements (flags default on): `ChatListScreen.kt` row: `modifier = Modifier.navSharedElement(SharedKeys.chatAvatar(cv.id))` on the `AvatarStack(` call and `Modifier.navSharedBounds(SharedKeys.chatTitle(cv.id))` on the title `Text(`; `HomeScreen.kt` recent-conversation rows the same; `BotsScreen.kt` `BotCard`: `navSharedElement(SharedKeys.botAvatar(bot.id))` on the avatar and `navSharedBounds(SharedKeys.botCard(bot.id))` on the card; `BotEditorScreen.kt` header the matching two. A key appears once per screen; chain after an existing modifier; if a site is not an exact match, `LEFT` and report. Imports `dev.pocketagent.app.ui.nav.SharedKeys`, `navSharedElement`, `navSharedBounds`.
  - Part 3 check S-9 expects the count of `RoundedCornerShape(` in your files minus only those R3 deleted; S-11 gains `CHAT_FORGED_CHAT` 1 and `botsRow` 1.
- Preconditions of 0.1 use the registry names (`PocketSheet`, `ActionSheet`, `ConfirmDialog`, `PetNotes`, `Space`, `Pocket.motion`, `rememberHaptics`, `RobotTags`, `navSharedElement`).

### 5.10 WORDS (stage 3)
- Batches W1 to W10 and W10b (unconditional: TEXT's group RT is core; if `eng_rt_` keys are absent, skip W10b and report). Recipes of `docs/waves/w0/SWEEPS.md` Part 2 apply with these changes:
  - W1 files: `ChatScreen.kt`, `ChatScreenState.kt`, `ChatSlashActions.kt`, `ChatTopArea.kt`, `ChatMessageList.kt`, `ChatInputArea.kt`, `ChatDialogs.kt`, `ToolOutputSheet.kt`, `StreamingMarkdown.kt`. W2 adds nothing. W4 adds `PocketTopBar.kt`, `TypingIndicator.kt`, DESIGN's new components that use `stringResource(`. W6 adds `ui/diag/DiagnosticsExtras.kt`, `PrivacyRows.kt`, `ui/settings/PracticeBanner.kt`.
  - `ChatScreen.kt:630` stays; the toast kinds stay; `ChatInput.kt` tags were SWEEP's.
  - New recipe T8 (locale): `HermesHome.kt` skill title `replaceFirstChar { it.uppercase() }` becomes `replaceFirstChar { it.titlecase(Texts.currentLocale()) }`; `ChatQuickBar.kt` `ef.replaceFirstChar { it.uppercase() }` becomes `ef.replaceFirstChar { it.titlecase(appLocale()) }`; no other case change is touched.
  - T4 theme names, preview literals, memory-file lines, `themeNameRes`; `AvatarGrid` through `EnumLabels.avatar` (exists now).
  - T7: four test classes get `@Before TestTexts.installEnglish()`. `RuntimeManagerInitTest` builds its context from `ContextWrapper(null)`; that stays green because `Texts.get(context, ...)` ignores the Context once the test source is installed (3.6).
  - **Arguments are never nullable (review fix).** `Texts.get`/`getQuantity` take `vararg args: Any`, so a `String?` is passed as a template, never bare: the first W10b row becomes `Texts.get(context, R.string.eng_rt_delete_failed, "${e.message}")` (`e` is a caught `Exception`, `RuntimeManager.kt:227`). Apply the same to every `e.message` or other nullable argument in SWEEPS.md 2.4 (the rows that already build `"${e.javaClass.simpleName}: ${e.message}"` are strings and stay).
  - Part 3 checks W-1 to W-21 apply; W-20 `-FailOnUnused` is run by INTEGRATE.

## 6. Strings

| Area | File (`app/src/main/res/values/`) | Prefix | Written by | tr and fr by |
|---|---|---|---|---|
| DESIGN | `strings_design.xml` | `design_` | DESIGN | TRANSLATE |
| STREAM | `strings_stream.xml` | `stm_` | STREAM | TRANSLATE |
| CONFIG-D | `strings_config.xml` | `cfg_` (every key except `cfg_priv_*`) | CONFIG-D | TRANSLATE |
| CONFIG-R | `strings_privacy.xml` | `cfg_priv_` | CONFIG-R | TRANSLATE |
| ROBOT-P | `strings_robot.xml` | `rbt_` | ROBOT-P | TRANSLATE |
| TEXT | `strings_engine.xml` | `eng_` | TEXT | TRANSLATE |
| BUILD | `app/src/twin/res/values{,-tr,-fr}/strings_build.xml` | none (overrides `app_name`, `srf_ask_agiri`, `srf_tile_label`, same value in all three) | BUILD | not translated |
| NAV, STAGE, SWEEP, WORDS | none | none | | |
| TEXT (existing keys) | `strings_chat.xml`, `strings_control.xml` (the ten plurals only) | existing | TEXT, all three languages | TEXT |

- **TRANSLATE rule (lead):** every key added in Wave 0 gets Turkish and French in the same wave, by stage 4, never by the area. Work list = the English keys missing from `values-tr` and `values-fr` (the parity error list of `strings-check.ps1`). Same file names; plurals with the language's forms (tr `one` and `other` equal, fr `one` covers 0 and 1); placeholders identical; protected texts untouched; length within `floor(room * 1.4)`; reuse words already in the app (Settings, Home, Usage, Provider, Runtime); keep brackets, `MEMORY.md`, `finish_reason`, `HTTP 401`, `proot`, product names; no exclamation marks except `eng_preview_hi`; the note of each key is the brief. Two translators (tr, fr) work in parallel on disjoint folders.
- **Committed stretched language:** tag `en-XZ`, folder `app/src/debug/res/values-en-rXZ/`, made by `tools\make-test-language.ps1` from the final English files (stage 4), checked in CI with `-Check`; it is not in `src/main`, so release builds never contain it.

## 7. Manifest (NAV writes all of it; collected from every draft)

| Where | Entry |
|---|---|
| `<application>` | add `android:enableOnBackInvokedCallback="true"` (N6, S1) |
| nothing else | `supportsRtl="true"` already present; FileProvider authority already `${applicationId}.fileprovider` and `cache-path diagnostics/` already mapped; no new component, permission, filter or `<queries>`; the five link actions and the notification actions stay literal; `profileinstaller` merges its own receiver; `res/xml/shortcuts.xml` in `main` is unchanged |

The merged twin manifest must show `package="dev.pocketagent.app.debug"`, provider authority `dev.pocketagent.app.debug.fileprovider`, process `dev.pocketagent.app.debug:restart`.

## 8. Stages after stage 1

### 8.1 SWEEP (stage 2): batches S1 to S5 of 2.9, run in parallel after every stage 1 agent has finished; no compile in between. Recipes: `docs/waves/w0/SWEEPS.md` Part 1 with the amendments of 5.9. Report format of its 0.4.

### 8.2 WORDS (stage 3), batches in parallel after SWEEP

| Batch | Files |
|---|---|
| W1 chat, split screen | `ChatScreen.kt`, `ChatScreenState.kt`, `ChatSlashActions.kt`, `ChatTopArea.kt`, `ChatMessageList.kt`, `ChatInputArea.kt`, `ChatDialogs.kt`, `ToolOutputSheet.kt`, `StreamingMarkdown.kt` |
| W2 chat A | `ChatListScreen.kt`, `ChatMessages.kt`, `ChatCommon.kt`, `StarredScreen.kt`, `MarkdownView.kt`, `ChatSearch.kt` |
| W3 chat B | `ChatInput.kt`, `ChatQuickBar.kt`, `NewChatSheet.kt`, `AttachmentUi.kt`, `SkillPickerSheet.kt`, `SlashPalette.kt` |
| W4 home, bots, shell, components | `HomeScreen.kt`, `HomeParts.kt`, `BotEditorScreen.kt`, `BotShareUi.kt`, `BotsScreen.kt`, `ui/AppRoot.kt`, `PocketTopBar.kt`, `TypingIndicator.kt`, DESIGN's new components with `stringResource(`, the three comment edits |
| W5 settings A | `SettingsScreen.kt`, `AppearanceSection.kt`, `ProviderSection.kt`, `CtlUi.kt`, `BehaviourSection.kt` |
| W6 settings B | `UsageSection.kt`, `VoiceSoundSection.kt`, `RuntimeSection.kt`, `BackupSection.kt`, `ui/diag/*`, `ui/settings/PracticeBanner.kt` |
| W7 hermes | `HermesTabs.kt`, `HermesDocs.kt`, `HermesScreen.kt`, `TerminalScreen.kt` |
| W8 routines screens | `RoutineEditorScreen.kt`, `RoutinesScreen.kt`, `RoutineUiParts.kt` |
| W9 service | `service/ReplyNotifier.kt`, `AgentService.kt`, `FireTileService.kt`, `routines/RoutineScheduler.kt` |
| W10 engine literals and tests | `data/ChatEngine.kt`, `data/AttachmentStore.kt`, `hermes/TerminalSession.kt`, `hermes/HermesHome.kt` (also T8), the four test classes. W10b: `runtime/RuntimeManager.kt`, `voice/VoiceEngine.kt` |

### 8.3 TRANSLATE (stage 4)
- Inputs: final English `values/strings*.xml`; `tools/strings-check.ps1` parity errors as the work list; the notes; the glossary rule of section 6; protected-keys list.
- Outputs: `values-tr/` and `values-fr/` files for every missing key (existing files extended; new files `strings_engine.xml`, `strings_config.xml`, `strings_privacy.xml`, `strings_robot.xml`, `strings_stream.xml`); regenerated `app/src/debug/res/values-en-rXZ/`.
- Checks, run by the agent with `powershell -NoProfile -ExecutionPolicy Bypass -File tools\<x>.ps1` (`pwsh` is CI only): `strings-check.ps1` exit 0 (2 means a ratchet got worse: fix), `make-test-language.ps1 -Check` exit 0, `make-test-language.ps1 -SelfTest`. A second agent reads each language for length, placeholders and tone (report only).

### 8.4 INTEGRATE (stage 5, one agent)
Commands, in this order (repo root, none installs anything):
1. `powershell -NoProfile -ExecutionPolicy Bypass -File tools\local-build.ps1` (twin), then `... -OwnerDebug`, then `... -Release` (R8; fill `missing_rules.txt` dontwarn lines into `proguard-rules.pro`, run `tools/ci/apk-size.sh` and write the baseline numbers into `tools/size-budget.txt`).
2. `... local-build.ps1 -Test` and `... -Test -OwnerDebug` (exit 0 or 2 only).
3. Every PowerShell script as `powershell -NoProfile -ExecutionPolicy Bypass -File tools\<x>.ps1` (`pwsh` is not installed on this PC; all of them work on 5.1): `lint-ui.ps1 -Init` then `-Check`; `strings-check.ps1 -WriteBaseline` then plain (no `-Check`), then `-FailOnUnused` once (add the flag to `build.yml` only if it is clean); `make-test-language.ps1 -Check`; `python tools/art/craft_art.py --check`; `python rootfs/smoke/mock_llm.py --selftest`; `-SelfTest` of `phone-test`, `phone-perf`, `lint-ui`; `sh -n` on every shell script; `sh runtime/test-launcher.sh sh`; `sh tools/ci/test-apk-size.sh` (skip with a note if `zip` is missing); the ripgrep checks of SWEEPS.md Part 3.
4. First-run data: `PaletteContrastTest.KNOWN_FAILURES` from the printed ids; `pixelfont-v1.golden` made once in a scratch worktree of commit `344829f` under `dist/wt-golden/` (never `dist/wt`) with `PIXELFONT_GOLDEN_WRITE=1`.

It may fix itself: Gradle and AGP DSL spelling, imports, visibility, `@OptIn`, exhaustive `when` branches, init order, name clashes, missing `internal`, opt-in warnings, argument renames forced by this contract, the first-run data above, one flag in `build.yml`; **after any edit of an English strings file it re-runs `powershell -NoProfile -ExecutionPolicy Bypass -File tools\make-test-language.ps1` (no `-Check`) to regenerate `app/src/debug/res/values-en-rXZ/`**, because `local-build.ps1` refuses (exit 3) every build while the committed stretched language is stale. It must not: change a registry signature, change behaviour or looks, delete or weaken a test, edit `LegacyHearthReference.kt` to make parity pass, touch `dist/wt` or the phone, commit. It reports: every fix by file, every registry deviation, every red test it could not make green in three tries, every `LEFT` it noticed, each unproven fallback of the experiments.

## 9. Proof (plan line 758; G2, G4, G5, G9)

| Item | Where |
|---|---|
| CI green: unit tests, lint ratchet, strings check, stretched language check, `craft_art --check`, mock selftest, proot, rootfs, smoke (guard block boots, `/v1/toolsets` enabled set equals the ten exactly, no title call, phase 3 through the guest practice model on a free port 18080; the Anthropic SDK imports in the guest if the lead adds the extra, 5.7), two APKs with verified package, version code, debuggable flag and size | CI |
| `docs/perf/w0.md` before and after, debug numbers (build 32 before, owner-debug after), `P1`, `P5`, `P6`, `P7` and `P4` (one practice chat, 5.2) | tonight on owner-debug (before numbers taken from build 32 first: `P1`, `P6`, `P7` only); `P2`, `P3` need the twin |
| same numbers on the optimised (R8) build and the budgets; S2 (Hermes start, neural voice, backup and restore, icon switch under R8); S4 compile filter | needs the twin (waits for the owner; G5, G9) |
| robot at least twenty scenarios green on the practice model; the 14 debt items closed or filed; speech, Hermes, backup, restore in the optimised build | needs the twin (G4); tonight only the `safe` scenarios (smoke 5, gallery, sweeps); the twin-only scenario files are written after the first stage-1 compile (cut-first, 5.2) |
| a practice turn in a chat with a per-chat model override reaches the mock (request log) and no other host | needs the twin (unproven: S5 did not try `provider=custom` with a Google model id) |
| whole app in the stretched language with no plain English left and no label cut (`-ScanDump`, cut check) | tonight on owner-debug (debug link, in memory; the font 2.0 half of the cut check and the animation scales for stable dumps need `-AllowSystemSettings`, which only the lead gives, 1.3) |
| mirrored screenshot of every screen; every control the robot met has a spoken label | tonight on owner-debug (`rtl=1`, a11y check in `sweep-labels`) |
| the agent's tool list has no browser, scheduler, picture or speech tool; a practice turn makes no title call | CI (smoke) plus needs the twin (`tools-are-short`, `no-title-call`) |
| memory numbers of 0.12 (S7) | needs the twin |
| the owner's app moved to the private key with nothing lost, and the twin beside it (S6) | needs the owner (key, lineage, the one "Install anyway" tap) |
| "apart from motion nothing looks different": Home parity shots (animations Off; "before" from build 32 taken before installing owner-debug), the lead's look at the gallery in dark and light, NAV proofs (state kept, back gesture S1 = the "button-like" path with `NavFlags.backGestureDrivesScreen` off, shared elements S3 with `NavFlags` switches) | tonight on owner-debug, by hand (`nav-state-restore` is twin only) |
| `/loop`, approval card and notification, inline reply, budget warn and block, idle stop, auto-restart, bot export and import, skill create and delete, terminal colours, `SOUL.md` edit, image update, restore into a wiped twin | needs the twin |
| camera shutter, spoken dictation, a provider with a real key | stay manual |

## 10. Open points for the lead

1. **The dotted capital İ.** DESIGN proves the pixel font draws it, and the case-change list shows no chat-title site; the cause of "Ikiniz de..." in `dist/phone-shots/09_group_chat.png` is not found. Wave 0 only guarantees that display code uses the chosen locale (`#62`). After the first Turkish robot shot, decide: a one-line fix where the shot shows it, or leave it to Wave 1. Not decidable without the phone.
2. **`seed` as app code.** I read "Seeding: a debug link, twin only" as an in-app seeder (`DebugSeeder`, ROBOT) called by NAV's link, because the owner-debug build is debuggable and `run-as` would have worked anyway. If you meant `run-as` seeding, delete `DebugSeeder`, the `seed` extra and test `DebugSeederTest`; nothing else depends on it but scenarios 17, 18, 22, 31, perf P2 to P4.
3. **Three additions that follow from your words but are not in them:** `PracticeBanner` (the "banner while on"), the `dist\owner-backup\` non-empty check and `-AcknowledgeS2` in `local-build.ps1` (G5, G6 as guards). Remove them if you prefer to do these by hand.
4. **`-Pperf` is cut** because you fixed three variants; optimised numbers before S2 passes are only possible with `-Release` on the twin when it exists.
5. Whether `uiautomator` exports tags of containers with no text (NAV risk 4) can only be seen on the phone; the `I/Nav:` log line is the fallback, and a scenario that fails only for that reason is a finding, not a build error.
6. **S1 and S3 results (`dist/exp-results/S1-S3-navigation.md`).** S1 FAILED on this phone: Samsung's gesture pad sends a back key, so the app gets one event with progress 0.0 and then completion at finger lift. NAV keeps `NavFlags.backGestureDrivesScreen`, but its default is now **false** (differs from lead G8, which said "default on" before the experiment; NAV's own draft says "if S1 fails: false"), and the gesture path must treat that event shape exactly like a button back (5.4). S3 PASSED: `sharedElements` stays on. The experiment ran on a twin built with targetSdk 35 (Play Protect blocks 28 to 33), so it says nothing about targetSdk 28 behaviour beyond "the OS never delivers progress on this phone".
7. **Plan 0.11 formatters** (dates, numbers, sizes and money through the platform's formatters with the chosen language) are not built in Wave 0: no area owns them and `FileUtil.formatBytes` and the date formatting pass through unchanged. Filed for Wave 1, logged as a deviation from the plan.
8. **Decisions that only the lead can make (from the second review).** (a) `-AllowSystemSettings` for tonight's owner-debug runs: without it the animation scales stay as the owner has them (Home may keep `uiautomator dump` from going idle; dumps are retried 3 times) and the font 2.0 cut check is skipped. (b) Whether to add `--extra anthropic` at `rootfs/install-hermes.sh` line 57 (5.7): it changes the rootfs build id of every CI build, tonight's local builds (parts of `phase0-8`) do not carry it; if declined, the Anthropic provider mode is unavailable behind the guard and `AGENTS.md` must say so. (c) The rollback APK of 1.2 is built by the lead before the first install. (d) The twin-only scenario files and perf `P2`, `P3` are cut-first (5.2): say if a second ROBOT-T agent should write them once the stage-1 compile is green. (e) Practice expiry cannot reach a Hermes that is already running in practice inside a long-lived process: the flag expires, but the configuration changes only at the next Hermes launch (3.10); the banner's minutes-left line and the robot's `finally` are the guards.

## 11. The lead's rulings on section 10 (binding; they override everything above where they differ)

1. **İ (10.1):** decided after the first Turkish shot on the phone; no agent chases it now.
2. **`seed` (10.2):** confirmed as written: an in-app `DebugSeeder`, reached by NAV's debug link, twin only.
3. **The three guards (10.3):** confirmed: `PracticeBanner`, the non-empty `dist\owner-backup\` check, `-AcknowledgeS2`.
4. **`-Pperf` (10.4):** stays cut.
5. **`NavFlags.backGestureDrivesScreen` (10.6):** default `false`, confirmed. Because S1 failed, NAV writes **no seekable back machinery** in Wave 0: a plain `BackHandler` pops the stack and the pop transition plays after the gesture. The flag stays in `NavFlags` as a documented no-op so the registry does not change. `sharedElements` defaults to `true`.
6. **Formatters (10.7):** Wave 1; logged as a deviation from plan 0.11.
7. **`-AllowSystemSettings` (10.8a):** only the lead passes it, tonight, and the lead checks afterwards that the four values are back.
8. **No change to the Linux image in Wave 0 (10.8b).** `rootfs/install-hermes.sh` is not edited. Instead CONFIG-R writes `security.allow_lazy_installs: false` in every credential mode **except** the Anthropic mode, where the line is left out (Hermes's default), because that mode needs its SDK installed on first use. The fixtures and `ProviderConfigTest` say so. The smoke test has no Anthropic import check.
9. **Rollback (10.8c):** the lead builds the rollback APK when the first install is prepared.
10. **Scenarios (10.8d):** ROBOT-T writes the scenario engine, the eight safe scenarios, the perf tool and the lint first. The twin-only scenario files are written by a second pass once the stage-1 compile is green.
11. **Practice expiry (10.8e):** accepted as described.
12. **Git:** build agents never run a git command that changes the tree or the index (no add, commit, checkout, stash, reset, restore, clean). Reading (`git diff`, `git log`, `git show`) is fine. Other agents are editing other files in the same tree at the same time.
