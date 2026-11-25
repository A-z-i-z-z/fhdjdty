# Wave 0, stage 1: what the ten build agents reported

Written from the workflow result on 2026-11-25. Each area was built by a Sonnet agent, reviewed once by an Opus reviewer, and the findings were applied once. Nothing was compiled in this stage.

## BUILD

**Review verdict:** Mostly sound and close to the contract. Variants, gates, version scheme, signing resolution, twin overlays, the R8 rules, keep.xml, CI signing isolation and the five tests all match sections 1.2, 3.1 and 5.1. I found no compile-level problem in the agent's Kotlin tests. The Gradle DSL spellings the agent already flagged are INTEGRATE's to confirm. One real safety hole needs fixing before tonight's install over the owner's app: if `dumpsys` fails or does not match, the script treats the owner's app as not installed, and that skips every guard. The rest is minor: a CI syntax-check loop that hides failures, a test that only passes with LF line endings, release notes that invite installing an APK that would re-extract the Linux image, and one silent signing fallback. Ownership was respected, and the scope drops are only the reported cut-first B4 and the documented deviations. (5 findings, 0 blockers; fix: applied)

**Summary:** All five findings applied. The major one (local-build.ps1 treating "could not ask the phone" as "not installed") is fixed. The four minors are fixed too: the shell-check loop in build.yml, the CRLF-sensitive test regex, the release notes, and the silent fallback to the committed key. I re-read build.gradle.kts and the changed parts of local-build.ps1 as my own compiler. build.yml parses as YAML, and local-build.ps1 parses with 0 errors. Nothing was compiled or run. The other area files (tools/build-ids.ps1, the tools/ci scripts, the twin overlays, proguard rules, the other tests) were not touched in this pass and are unchanged from the earlier build.

**Files:**

- app/build.gradle.kts
- app/proguard-rules.pro
- app/src/main/res/raw/keep.xml
- app/src/twin/res/values/strings_build.xml
- app/src/twin/res/values-tr/strings_build.xml
- app/src/twin/res/values-fr/strings_build.xml
- app/src/twin/res/drawable/twin_badge.xml
- app/src/twin/res/drawable/twin_fg_newroz.xml
- app/src/twin/res/drawable/twin_fg_pet.xml
- app/src/twin/res/drawable/twin_fg_flame.xml
- app/src/twin/res/drawable/twin_fg_zagros.xml
- app/src/twin/res/drawable/twin_fg_kawa.xml
- app/src/twin/res/drawable/twin_fg_roj.xml
- app/src/twin/res/mipmap-anydpi-v26/ (all 12 launcher icon xml files)
- app/src/twin/res/xml/shortcuts.xml
- tools/build-ids.ps1
- tools/local-build.ps1
- tools/ci/apk-size.sh
- tools/ci/verify-apk.sh
- tools/ci/sign-owner.sh
- tools/ci/test-apk-size.sh
- tools/size-budget.txt
- .github/workflows/build.yml
- .gitignore
- app/src/test/java/dev/pocketagent/app/BuildContractTest.kt
- app/src/test/java/dev/pocketagent/app/ProguardRulesTest.kt
- app/src/test/java/dev/pocketagent/app/TwinOverlayTest.kt
- app/src/test/java/dev/pocketagent/app/LocalBuildSafetyTest.kt
- app/src/test/java/dev/pocketagent/app/SizeBudgetFileTest.kt

**Deviations:**

- Finding 1 (major, local-build.ps1): fixed as proposed. Get-InstalledPackageInfo now returns Known (exit 0 and not timed out). Assert-OwnerInstallAllowed refuses when Known is false. When dumpsys says not installed, it asks `pm list packages` as a second source and refuses unless that call succeeded and no line equals exactly package:<owner package>. It also refuses if pm lists the package while dumpsys did not describe it. Only then does it print the fresh-install NOTICE. Invoke-CheckSigning still uses Exists alone, but it only reads and never installs.
- Finding 5 (app/build.gradle.kts): done as the lazy variant plus a small widening. releaseRequested now also matches the last path segment assemble, build and bundle (so :app:assemble counts). When the key is partly set or its keystore file is missing and the task names did not say release, resolveOwnerKey records ownerKeyProblem instead of returning silently. A doFirst on every package*Release and sign*ReleaseBundle task then throws it, so the committed key can never sign a release without a message. The error text is unchanged. This relies on `tasks.matching{}.configureEach{ doFirst{} }`, which could not be compiled here.
- Finding 2 (build.yml shell loop): fixed as proposed, in an equivalent multi-line form (bash -n or sh -n, then `|| { echo ...; exit 1; }`). Earlier deviation kept: bash -n for scripts whose shebang says bash, sh -n for the rest.
- Finding 3 (LocalBuildSafetyTest): fixed by normalising CRLF to LF before the regex, in the one test that matches across lines. The other tests in that class work line by line, so CRLF does not affect them.
- Finding 4 (release notes): added the suggested line to notes.md, with $OWNER_APK expanded by the heredoc.
- Earlier deviations still stand. The gradle task still copies rootfs/smoke/mock_llm.py and runtime/practice-start.sh. If the second file is missing, Gradle's InputFile check fails and so does Test-Launcher. baseline-prof.txt is cut. Test method names are camelCase. The Shell syntax checks step uses bash -n for bash scripts.

**Requests:**

- INTEGRATE: confirm BuildConfig.VERSION_CODE of the twin equals max(CI_RUN*100+seq, 1). The `variant.outputs.forEach { it.versionCode.set(...) }` spelling, the `sourceSets.getByName("debug").res.srcDir("src/twin/res")` call and `manifestPlaceholders[...]` in build types are unverified against AGP 9.5.2.
- INTEGRATE: compile app/build.gradle.kts. Check the new lines: the script-level `var ownerKeyProblem`, `tasks.matching { ... }.configureEach { doFirst { throw ... } }`, and `gradle.startParameter.taskNames.any { raw -> ... }`. The first release build's missing_rules.txt goes into app/proguard-rules.pro as -dontwarn lines. Raise gradle.properties -Xmx to 4g only if R8 runs out of memory.
- INTEGRATE: after the first green release build, fill owner_baseline_bytes and owner_ceiling_bytes (baseline + 1048576) in tools/size-budget.txt.
- NAV: keep app/src/main/AndroidManifest.xml free of the literal string dev.pocketagent.app and keep android:label="@string/app_name" on <application> and on all six activity-alias entries. TwinOverlayTest.theManifestNamesNoPackage asserts both.
- ROBOT-P: runtime/practice-start.sh must exist, with LF endings and #!/bin/sh. The Gradle assets task, local-build.ps1 Test-Launcher and the CI sh -n step all need it. Also needed in CI: python3 rootfs/smoke/mock_llm.py --selftest and python3 tools/art/craft_art.py --check.
- ROBOT-T / TEXT: the CI unit job calls tools/lint-ui.ps1 -Check and -SelfTest, tools/strings-check.ps1, tools/make-test-language.ps1 -Check, tools/phone-test.ps1 -SelfTest and tools/phone-perf.ps1 -SelfTest. All must exist and run under pwsh 7 on Linux.
- lead: the CI secret names are AGIRI_KEYSTORE_B64, AGIRI_KEYSTORE_PASSWORD, AGIRI_KEY_ALIAS, AGIRI_KEY_PASSWORD and optionally AGIRI_LINEAGE_B64. Never set the secrets without the lineage, because Android refuses an update signed with a new key. AGENTS.md should record the three variants, the version scheme (owner (10000+run)*100+seq, twin run*100+seq) and the local-build.ps1 switches. AGENTS.md is not my file.
- lead (optional): add `*.ps1 text eol=lf` or crlf to .gitattributes if wanted. .gitattributes is not my file. The test no longer depends on it.

**Not done:**

- baseline-prof.txt (B4, cut first, no consumer).
- Gradle has no signing-lineage support. AGIRI_SIGNING_LINEAGE is read only by tools/ci/sign-owner.sh, and S6 is not run in Wave 0.
- gradle.properties -Xmx left unchanged (only if R8 needs more).

**Unverified:**

- Nothing was compiled or run. That includes the Gradle script (the new lazy-failure hook included), the Kotlin tests and the CI steps. The AGP 9.5.2 DSL spellings, R8 acceptance of the rules and the twin's BuildConfig version code are all unconfirmed.
- The new pm list packages confirmation in Assert-OwnerInstallAllowed was never run against a phone. Its logic is simple, but adb output formats on One UI are untested. verify-apk.sh and sign-owner.sh were only syntax-checked.
- build.yml is valid YAML after the edits, but no Actions run exists. The pwsh and python steps depend on scripts other agents write.
- Whether the committed stretched language is current (local-build.ps1 would otherwise exit 3) depends on TEXT's output.

## ROBOT-P

**Review verdict:** ROBOT-P's work can be integrated. I found no blocker or major problem. All files are in its ownership list. Every registry symbol it provides has the exact contract name and shape: PracticeModel 3.10, the DebugSeeder Spec/parse/run grammar with the IS_TWIN guard, RobotTags 3.12 (50 constants, no *_CONFIRM, CHAT_ATTACH and CHAT_MIC added), PracticeBanner, the rbt_ strings and the plural. RuntimeSection uses only the symbols CONFIG and DESIGN provide in the registry: GuestPrefs.get/practiceModel/setPracticeModel/practiceMinutesLeft, RuntimeManager.STILL_STARTING and onGuestOptionsChanged (it runs async on the runtime scope), MainViewModel.shareLogsIntent (suspend), DiagnosticsExtras, cfg_diag_exporting/export_failed, PocketSwitchRow(modifier) and ConfirmDialog(body). Because ConfirmDialog is imported explicitly, it wins over the same-package CtlUi one.

Compile checks done by reading:
- `catch (_: X)` is already used in the tree.
- pluralStringResource is already used elsewhere without an opt-in.
- PixelIcon.ARCHIVE, Pocket.colors.warning, Muted(size) and the CText parameters all exist.
- The local `intent` var smart-casts.
- The `when` over the sealed Spec is exhaustive.
- Block comments are balanced and none is nested.
- The Usage(200, ...) literals coerce to Long.

Data and safety:
- The practice model binds 127.0.0.1 only.
- Its files live in /opt/agiri/practice: outside HERMES_HOME, not in a backup, ignored by the pre-wave code, so rollback is safe.
- No credential or provider file is touched.
- Seeding is IS_TWIN-gated, not TEST_HOOKS-gated.

Python and shell:
- I checked the guest's Python 3.14 in the rootfs tarball: HTTPServer has allow_reuse_port = False, so on Linux a port that is already taken does make the model print "cannot listen" and exit, and start.sh exits 71 as smoke-test.sh needs.
- The request log keeps "POST" and then "stream", "n_msgs" in the order smoke-test.sh's sed reads.
- The start script's grep matches the model's "listening on PORT" line.
- --watch-parent works with the exec'd Hermes, which keeps the shell's pid.

The tests can pass (I traced messageText's lengths, the parse rejections and the reflection count of 50) and they prove real properties. Below are four minor findings. The first one touches the owner's data in a small way; the other three are robustness points. (4 findings, 0 blockers; fix: applied)

**Summary:** I fixed all four review findings (all minors, no blockers or majors). None was refuted. Nothing was compiled or run on a phone. Besides re-reading the touched code top to bottom, I ran `sh -n runtime/practice-start.sh`, which passes. The four fixes: (1) The magic-words hint in `RuntimeSection.kt` (`PracticeSwitch`) now drops MEMORY, FORGE and BALLAST unless `BuildConfig.IS_TWIN`. (2) `PracticeModel.install` now checks the first existing ancestor of the practice folder before `mkdirs()`, then checks the full path again afterwards. The pre-check only applies when that ancestor lies at or below the image root, so a rootfs that does not exist yet is not rejected. (3) `DebugSeeder.run` now has a KDoc line saying it blocks (off the main thread only) and may throw `IllegalStateException`. I also lowered `MAX_CHARS` from 20,000 to 4,000; `chat:200:400` is still within range, and `DebugSeederTest` reads the constant. The matching request for NAV is below. (4) `practice-start.sh` now also searches the fixed `/root/.hermes/tools/python-*/bin/python3` and `/root/.hermes/installs/*/environments/*/venv/bin/python` paths after the `$HERMES_HOME` ones.

**Files:**

- rootfs/smoke/mock_llm.py
- runtime/practice-start.sh
- .gitattributes
- app/src/main/java/dev/pocketagent/app/runtime/PracticeModel.kt
- app/src/main/java/dev/pocketagent/app/DebugSeeder.kt
- app/src/main/java/dev/pocketagent/app/ui/RobotTags.kt
- app/src/main/java/dev/pocketagent/app/ui/settings/RuntimeSection.kt
- app/src/main/java/dev/pocketagent/app/ui/settings/PracticeBanner.kt
- app/src/main/res/values/strings_robot.xml
- app/src/test/java/dev/pocketagent/app/PracticeModelTest.kt
- app/src/test/java/dev/pocketagent/app/RobotTagsTest.kt
- app/src/test/java/dev/pocketagent/app/DebugSeederTest.kt

**Deviations:**

- RobotTags: constants beyond the registry text: HERMES_TAB_USER, HERMES_TAB_CONFIG, HERMES_TAB_LOGS (3.12 lists all six hermes.tab.* tags) and PRACTICE_BANNER = practice.banner. Additions only; no registry constant renamed or removed.
- DebugSeeder: Spec.Usage is Usage(limit: Long, mode: BudgetMode) (the registry gave no types). Seeded ids start seed-, but ChatRepository has no add-with-id call, so createConversation is called and the id is replaced inside updateConversation's lambda; the orphan slot under the first random id stays in memory only.
- DebugSeeder.run(app, spec) delegates to an internal run(app, spec, isTwin) overload so tests can reach the twin guard; the public signature is unchanged.
- DebugSeeder.MAX_CHARS lowered from 20_000 to 4_000 (review finding 3). The grammar and the contract example chat:200:400 are unaffected.
- RuntimeSection (review finding 1): while practice is on, a non-twin build lists the magic words without MEMORY, FORGE and BALLAST; the twin lists all 15. The string rbt_practice_words and the registry are unchanged.
- practice-start.sh waits for the line listening on <PORT> in its own freshly truncated practice.log (plus kill -0) instead of polling /health with curl. Exit codes 70, 71 and (extra) 64 for a bad port. Python lookup: $HERMES_HOME paths first, then the fixed /root/.hermes paths of 3.10, then python3 on PATH.
- mock_llm.py: without --media-dir the picture goes to the temp folder, not next to the script (keeps the repo tree clean).
- PracticeBanner colours: surface fill, warning border, normal text, for contrast in all themes.
- PracticeModel.install additionally throws IOException when the practice folder resolves outside paths.rootfs, now checked before mkdirs on the deepest existing ancestor and again after it. installInto re-applies owner-only mode to start.sh without counting it as a write.

**Requests:**

- NAV (AppShell): place PracticeBanner(modifier) from dev.pocketagent.app.ui.settings; it has no height or width opinion beyond horizontal padding, so NAV positions it (top, below the status bar).
- NAV (DebugLinks): call DebugSeeder.parse(text), then DebugSeeder.run(app, spec) OFF the main thread, because it blocks (up to 5 s wait for the chat data, thousands of writes for a big chat:). For example `app.appScope.launch(Dispatchers.IO) { runCatching { DebugSeeder.run(app, spec) }.onSuccess { AppLog.i("Seed", it) }.onFailure { AppLog.w("Seed", "seed failed", it) } }`. An unparsable seed value goes to rejected. run throws IllegalStateException on a non-twin build and for clear before chats are loaded.
- ROBOT-T: the seed log line formats are exactly: chat <id> (a single chat, id seed-<16 hex>), chats <n> newest <id>, routine <id> at <epoch ms>, usage <limit> <MODE>, clear chats=<n> routines=<m>. For {{latestChat}} match ^chat (seed-\S+); a plain chats line starts with chats and must not match. Seeded chats of chats:<n> have no messages (title, preview and time only). chat:<m>:<c> allows at most 2000 messages of 4000 characters.
- ROBOT-T: practice.log, requests.jsonl and media/ are in files/rootfs/opt/agiri/practice/ on the host; practice.log is truncated at every launch; an exit 71 from start.sh shows in runtime.log. The robot must not type MEMORY, FORGE or BALLAST on the owner package.
- DESIGN: PocketSwitchRow must have `modifier: Modifier = Modifier` after `subtitle` (RuntimeSection calls it with `modifier =`); ui.components.ConfirmDialog must have parameters named title, body, confirmLabel, onConfirm, onDismiss, danger (RuntimeSection uses all by name).
- SWEEP S5 / INTEGRATE: RuntimeSection imports dev.pocketagent.app.ui.components.ConfirmDialog explicitly, which wins over the same-package ui.settings.ConfirmDialog of CtlUi.kt until S5 deletes that one.
- INTEGRATE / BUILD: build.gradle.kts must copy rootfs/smoke/mock_llm.py as asset practice_model.py and runtime/practice-start.sh as asset practice-start.sh (3.1); PracticeModel.install fails with IOException (state Error: 'Practice model could not be installed') if they are missing.
- CONFIG (not mine, for the record): rootfs/smoke/fixtures/practice.env and practice.yaml are untracked files that belong to CONFIG, not to this area.

**Not done:**

- Nothing from my scope was cut. No cut-list item applied (the ROBOT-P files have no no-consumer symbols).

**Unverified:**

- Nothing was compiled: the Kotlin was only read through (imports, nullability, Compose context, exhaustive when, init order). Look first at integration: the early `if (!on) return` before produceState in PracticeBanner; the smart cast of the local `intent` var in the Export logs button; `data object Clear` (Kotlin 2.4); the new ancestor walk in PracticeModel.install (it only does file operations, no Android types).
- The three JVM tests (PracticeModelTest, RobotTagsTest, DebugSeederTest) were not run. RobotTagsTest pins at least 50 constants. No test covers PracticeModel.install or DebugSeeder.run itself, as both need Android types.
- practice-start.sh was run on Windows git-bash (dash and bash, python3 shim) in the earlier pass, and only `sh -n` was repeated now. It was not run under mksh or proot. The exit 71 (port taken) path could not be exercised on Windows.
- seedChats and seedChat call ChatRepository from the thread DebugLinks.apply uses. Their cost was not measured, and they now rely on NAV running them on Dispatchers.IO (request above).
- The id swap through updateConversation relies on its current behaviour (the lambda result replaces the whole conversation).
- ui.components.ConfirmDialog (with `body`) and PocketSwitchRow(modifier) are DESIGN's and do not exist in the tree yet.

## ROBOT-T

**Review verdict:** ROBOT-T's files are not ready for a run on the owner's app. They stay inside ROBOT-T's ownership, and all three -SelfTest runs print SELFTEST OK under Windows PowerShell 5.1 (I re-ran them). Both Kotlin tests look compilable. The 5.2 guards (Assert-PaTwinTarget as the first statement of the nine ops, the safe-scenario rules, the type filter on the owner package) are present, but four places in the practice-mode and system-settings paths can still leave the owner's phone changed or delete an owner chat. Each needs its fix before `phone-perf -Owner` or `phone-test -Owner` runs tonight.
1. The robot can leave practice mode switched on.
2. A failed restore deletes the backup of the owner's animation and font scales.
3. The alarm guard lets an unreadable `dumpsys alarm` through as "nothing due".
4. P4's cleanup deletes whatever chat happens to be open.

Two behaviour problems come next. On the owner package the Running check reads the on-screen label, which is translated, so it never matches in Turkish or French. And the cut check never reads CutProbe's `AgiriCut` logcat lines, even though contract 3.5 names ROBOT's cut check as their consumer; the report does not mention this. Smaller items are listed below. (10 findings, 1 blockers; fix: applied)

**Summary:** All ten review findings are applied. The blocker and the five majors are fixed and the four minors are fixed. No finding was refuted and nothing is left as a request. The self tests of phone-test, phone-perf and lint-ui all print SELFTEST OK under Windows PowerShell 5.1. I also drove the new code against fake adb and log fixtures. Nothing was compiled and nothing ran on a phone, with real adb, or under pwsh 7. The two Kotlin tests were not run; I re-read both for compile errors.

Fixes, by finding:
1. **Blocker, practice mode left on.** The intent is now recorded before the switch is tapped. phone-perf sets `$practiceOn = $true` before `Set-PaPracticeMode $true`. `Invoke-PaPracticeOn` sets `PracticeTurnedOn` first, so the `finally` in phone-test and phone-perf always switches practice off. `Set-PaPracticeSwitch` also reads the switch again after the tap and throws if it did not change (minor finding 7).
2. **Scales restore.** `Set-PaSystemScales -Restore` now checks the exit code of every `settings put` and reads each value back. The backup file is deleted only when all four match. Otherwise it throws and the file stays. Writing the backup throws if `settings get` fails or returns something odd; only `null` maps to 1.0. I tested this with a fake device.
3. **Alarm guard.** `Test-PaAlarmDue` returns due when the text has no `nowELAPSED=` or no `Alarm{`. `Test-PaOwnerAlarmGuard` also returns due on a timeout or non-zero exit. Self-test cases were updated and extended: other-packages-only gives not due, and 'nothing here' and an empty text give due.
4. **P4 cleanup.**
   - The English 'Hermes' tap and the English 'Delete' tap are gone.
   - `wait` has a new `captureChat` field. After `link NEW_CHAT` it stores the new chat's id from the last `I/Nav: top=chat:<id>` line in `{{var:p4chat}}`.
   - The first cleanup step is `expect` with `navIs: chat:{{var:p4chat}}` and `chatHas: LONG 2000`. It proves the current route and that the text is in `files/chat/messages/<id>.json`, read-only through `grep`.
   - The scenario has `cleanupChain: true`, so the first failed cleanup step ends the cleanup.
   - Delete is the last `sheet.action.<i>`. It is found with the new selector `tagRe` plus `idx: -1` (the last match), then `dialog.confirm`. No language-dependent text is used.
   - The captured variable is removed after the scenario.
5. **State labels.** `Get-PaCurrentState` reads the language-independent `I/State` line of app.log on every package. Only if the log is unreadable does the owner fall back to the UI label, mapped from Turkish and French to the English word by `ConvertTo-PaEnglishState`. That function is built from char codes so phone-lib.ps1 stays pure ASCII.
6. **CutProbe.** In the font 2.0 cut pass, the `a11y` op now reads `AgiriCut:W` from logcat (new `Get-PaCutProbeTexts` / `ConvertFrom-PaCutProbe`). Each text becomes a `CUT screen=... "<text>"` line, filtered through `cut-allow.json` using the text as the id. logcat is drained afterwards so the next screen sees only its own lines. A self-test case covers the parser.
8. **`Get-PaLatestChat`.** It now reads the `conversations` array of the `{v, conversations}` document.
9. **sweep-stretched-and-mirrored.** It has a `cleanup` step that resets language and layout direction.
10. **PhoneToolsSafetyTest.** A new test flags writes or kills in `Invoke-PaRunAs` calls and requires every caller of `Invoke-PaRunAs` to be a guarded function or one of three named read-only readers.

Other changes: `ScenarioFilesTest` now accepts `tagRe`, `navIs` and `chatHas`, and gains two tests (P4's guarded cleanup, and the language reset in the sweep's cleanup). `tools/scenarios/README.md` documents the new fields (`captureChat`, `navIs`, `chatHas`, `tagRe`, negative `idx`, `cleanupChain`).

**Files:**

- tools/phone-lib.ps1
- tools/phone-test.ps1
- tools/phone-perf.ps1
- tools/lint-ui.ps1
- tools/scenarios/README.md
- tools/scenarios/routes.json
- tools/scenarios/a11y-allow.json
- tools/scenarios/cut-allow.json
- tools/scenarios/leak-allow.txt
- tools/scenarios/smoke-launch.json
- tools/scenarios/smoke-tabs-labels.json
- tools/scenarios/smoke-hermes-running.json
- tools/scenarios/smoke-terminal-version.json
- tools/scenarios/smoke-settings-walk.json
- tools/scenarios/design-gallery.json
- tools/scenarios/sweep-labels.json
- tools/scenarios/sweep-stretched-and-mirrored.json
- tools/scenarios/nav-state-restore.json
- tools/scenarios/perf/P1-home-scroll.json
- tools/scenarios/perf/P4-stream2000.json
- tools/scenarios/perf/P5-transitions.json
- tools/scenarios/perf/P6-coldstart.json
- tools/scenarios/perf/P7-home-idle.json
- tools/scenarios/fixtures/dump-sample.xml
- tools/scenarios/fixtures/gfxinfo-sample.txt
- docs/perf/w0.md
- app/src/test/java/dev/pocketagent/app/ScenarioFilesTest.kt
- app/src/test/java/dev/pocketagent/app/PhoneToolsSafetyTest.kt

**Deviations:**

- Safe flag: ROBOT.md used `owner: true`; the contract's `"safe": true` is used. `-Owner` runs only safe scenarios.
- PhoneToolsSafetyTest and ScenarioFilesTest list exactly the 9 `Invoke-PaOp*` guarded functions (wipe, pref, seed, killGuest, expectFile, restore, install, pick, adb). `expectFile` is guarded too, which goes beyond the contract's list. The safe-scenario ban list also includes `expectFile`.
- New ops beyond the contract's table, in my own vocabulary: `restore` (guarded; pushes a host file into the twin's files/), `scanDump`, `eachRoute`, `measure`. The vocabulary list sits in phone-lib.ps1 and in ScenarioFilesTest, and a test keeps them equal.
- New step and scenario fields beyond the contract: `navTop`, `cache`, `holdMs`, `cutCheck`, `vars`, `cleanup`, plus, in this round, `captureChat` (wait), `navIs` and `chatHas` (expect), `tagRe` and negative `idx` (selectors) and `cleanupChain` (scenario).
- `scanDump` runs tools/strings-check.ps1 -ScanDump with leak-allow.txt and collects the leaks; the draft's `-Var marker` regex parameter is gone.
- lint-ui rawTween patterns are `\btween\s*[<(]` and `\bkeyframes\s*[<({]` instead of `tween\(` and `keyframes\(`, because the real code calls `tween<Float>(`. Counts are per match; the directText pattern is the contract's.
- phone-lib.ps1 compiles the process helper type lazily on the first adb call, not at load, so -SelfTest under Linux CI never runs Add-Type.
- The font 2.0 cut check is a second pass of a scenario flagged `cutCheck: true` (only sweep-labels), not a runner op. It is skipped with a note on the owner package unless -AllowSystemSettings is given.
- A leftover system-scales backup is restored at the start of every run, including -Owner without -AllowSystemSettings. This restores the owner's own values and is the only `settings put` allowed in that case. If the restore cannot be proven, it now throws and keeps the backup.
- phone-perf.ps1 also accepts -Out and -Adb. An explicit -Scenario list overrides -Mode. The Memory mode records only one snapshot of the current state; the phased M0 to M4 runs and BALLAST are cut.
- lint-ui-baseline.json is not written (INTEGRATE runs -Init after the compile), so `lint-ui.ps1 -Check` exits 2 until then.
- ScenarioFilesTest.coreSuiteHasTheFirstSet asserts at least 9 core scenarios, not the plan's 20, because the twin-only files are cut-first.
- P4 on the owner package: NEW_CHAT may reuse an existing empty plain Hermes chat (AppRoot reuse rule). Our typed message makes it non-empty, so deleting it afterwards loses nothing. If the run fails before the message was sent, the chat guard (`chatHas`) does not hold and the chat is deliberately left, because it is empty and is reused by the next NEW_CHAT.
- P4's delete step takes the LAST `sheet.action.<i>` of the chat menu (delete is the last entry in ChatDialogs.kt today). If STREAM appends an action after it, the guarded chat is still the only thing that can be affected, and the dialog.confirm tap would simply time out.

**Requests:**

- INTEGRATE: run `powershell -NoProfile -ExecutionPolicy Bypass -File tools\lint-ui.ps1 -Init` after the integration compile, then `-Check`. It exits 2 until the baseline exists.
- INTEGRATE: run `tools\phone-test.ps1 -SelfTest`, `tools\phone-perf.ps1 -SelfTest`, `tools\lint-ui.ps1 -SelfTest`; all pass offline here. pwsh 7 was not available, so the Linux CI run is unverified.
- INTEGRATE or TEXT: extend tools/scenarios/leak-allow.txt (regexes for texts that stay English) after the first stretched-language sweep.
- STREAM and SWEEP: the tags of section 3.12 must exist, or scenarios fall back to English `any` selectors: chat.input, chat.send, chat.menu, chats.new, chats.row.<i>, terminal.input, terminal.run, settings.section.runtime, settings.runtime.practice, settings.runtime.state. P4 additionally relies on `sheet.action.<i>` (DESIGN's ActionSheet) and `dialog.confirm`; the chat menu's delete entry must stay the LAST action of the sheet, or STREAM must tell ROBOT-T.
- NAV: keep the app.log line ` I/Nav: top=<route key>` with no spaces in the key (e.g. `top=settings:runtime`, `top=chat:<id>`); `wait navTop`, `captureChat` and `expect navIs` read the last such line. `link debug` extras used: route, reset, lang, rtl, seed, with reset=1 applied before the others.
- CONFIG-R: `Set-PaPracticeMode` proves practice mode from the last `practice=on|off` in app.log, and Hermes state from the `I/State: <label>` line (English label such as Running). Keep both in app.log (not only runtime.log).
- ROBOT-P or DESIGN: the robot expects `settings.runtime.practice` on a container whose first checkable descendant carries the checked state (PocketSwitchRow modifier), and the tags gallery, gallery.sheet, gallery.dialog, gallery.note, sheet, dialog, dialog.cancel, petnote.
- CutProbe (DESIGN): the cut check reads `W/AgiriCut` lines; the owner's debuggable build emits them only if BuildConfig.TEST_HOOKS is true there. If not, the owner-package cut pass finds only node-bound cuts.
- Lead: the twin-only scenario files (the other 30 of the plan, perf P2 and P3) are not written; they are cut-first and need the first stage-1 compile and the twin.

**Not done:**

- The twin-only scenario files (practice-switch, chat-*, budget, approval, notification, idle-stop, restore into a wiped twin and the rest) and perf P2/P3: cut-first per ruling 11.10 and contract 5.2; no code consumes them.
- `-Mode Memory` phases M0 to M4 and the BALLAST driving: only one snapshot of the current state exists. The `pick` file-picker op and `notification` reply are best-effort and flagged fragile.
- tools/lint-ui-baseline.json: left to INTEGRATE (-Init).
- strings_robot.xml and the Kotlin RobotTags/PracticeModel work belong to ROBOT-P and were not touched.

**Unverified:**

- Nothing ran on a real phone, with real adb, or under pwsh 7 on Linux.
- The Kotlin tests ScenarioFilesTest and PhoneToolsSafetyTest (including the three new tests) were not compiled or run; they were checked by eye. The ScenarioFilesTest JSON checks were also approximated by loading all scenario JSON in Python, which parsed.
- The shapes of real `uiautomator dump`, `dumpsys gfxinfo framestats`, `dumpsys alarm` and `logcat -v brief` output are hand-written fixtures or fake adb responses. Real ones may differ, for example the `Alarm{...}` entry format, or an `AgiriCut` line format other than `W/AgiriCut( pid): text`.
- The new captureChat / navIs / chatHas and the system-scales restore were exercised against fake adb functions only. `grep -c -F` through run-as on the owner's phone (toybox grep) and `files/chat/messages/<id>.json` being the real path of a chat's messages are read from the source, not tried.
- The practice-switch re-check after the tap and the 6 s wait are untested on a device.

## DESIGN

**Review verdict:** Mostly sound. I read every file DESIGN touched against the contract (sections 1, 2.3, 3, 5.3, 11) and the area file. Nothing touches the owner's data, credentials, provider setup or money. The registry names and signatures match: PocketSheet/PocketSheetScope, ActionSheet/SheetAction, ConfirmDialog, PocketDialog, PetNotes/PetNoteHost, PocketSwitchRow(modifier last), PocketTopBar(ember last), PixelText(+2 trailing), CutProbe gated on TEST_HOOKS, Pocket.motion, Pocket.anim (effective level), HapticCue/rememberHaptics, PocketColors.glow/scrim at the end, and the PixelIconView mirroring and EnumLabels fallback. The v1 PixelFont lines are untouched and defV2() is called last in init. All files are inside DESIGN's ownership, and no scope was dropped. I found no compile errors on reading; the Compose API uses look right, and robotRoot is NAV's, still to come.

Two real defects need fixing before INTEGRATE:
- **Dialog scrim (every dialog):** PocketDialog turns off the platform dim but draws its own scrim inside the padded box. Every dialog in the app will therefore show an undimmed frame and undimmed system bars.
- **Glyph-count test:** PixelFontTest's count assertion can never pass, because the font has 149 glyphs and the test demands more than 150.

One minor layout issue: with the keyboard open, a tall PocketSheet can slide under the status bar.

I hand-checked every expected value in TokensTest, MotionTest, HapticsTest, PixelShapesTest, PressAndSheetMathTest, PetNoteQueueTest, PanelVariantTest and PixelTitleTest; they should pass. The kerning pairs pass their test as written. Note that the test checks for touching strokes, not overlap. (3 findings, 0 blockers; fix: applied)

**Summary:** I applied the three review findings (two majors, one minor) and re-read the edited files. Nothing was compiled or run. PocketDialog now draws its scrim over the whole window, the sheet keeps clear of the status bar, and the PixelFontTest bound is corrected. All other work of the DESIGN area is as in the earlier report.

**Files:**

- app/src/main/java/dev/pocketagent/app/ui/theme/Motion.kt
- app/src/main/java/dev/pocketagent/app/ui/theme/SystemMotion.kt
- app/src/main/java/dev/pocketagent/app/ui/theme/Haptics.kt
- app/src/main/java/dev/pocketagent/app/ui/theme/Tokens.kt
- app/src/main/java/dev/pocketagent/app/ui/theme/PocketTheme.kt
- app/src/main/java/dev/pocketagent/app/ui/theme/Appearance.kt
- app/src/main/java/dev/pocketagent/app/ui/theme/Palettes.kt
- app/src/main/java/dev/pocketagent/app/ui/theme/PixelFont.kt
- app/src/main/java/dev/pocketagent/app/ui/components/PixelShapes.kt
- app/src/main/java/dev/pocketagent/app/ui/components/PixelPress.kt
- app/src/main/java/dev/pocketagent/app/ui/components/DitherScrim.kt
- app/src/main/java/dev/pocketagent/app/ui/components/PocketSheet.kt
- app/src/main/java/dev/pocketagent/app/ui/components/ConfirmDialog.kt
- app/src/main/java/dev/pocketagent/app/ui/components/PetNote.kt
- app/src/main/java/dev/pocketagent/app/ui/components/SwipeRow.kt
- app/src/main/java/dev/pocketagent/app/ui/components/Skeleton.kt
- app/src/main/java/dev/pocketagent/app/ui/components/EmberLine.kt
- app/src/main/java/dev/pocketagent/app/ui/components/DesignGallery.kt
- app/src/main/java/dev/pocketagent/app/ui/components/CutProbe.kt
- app/src/main/java/dev/pocketagent/app/ui/components/PixelPanel.kt
- app/src/main/java/dev/pocketagent/app/ui/components/PixelButton.kt
- app/src/main/java/dev/pocketagent/app/ui/components/PixelText.kt
- app/src/main/java/dev/pocketagent/app/ui/components/PixelIcon.kt
- app/src/main/java/dev/pocketagent/app/ui/components/PocketChip.kt
- app/src/main/java/dev/pocketagent/app/ui/components/PocketSwitchRow.kt
- app/src/main/java/dev/pocketagent/app/ui/components/PocketDialog.kt
- app/src/main/java/dev/pocketagent/app/ui/components/PocketTopBar.kt
- app/src/main/java/dev/pocketagent/app/ui/components/TypingIndicator.kt
- app/src/main/java/dev/pocketagent/app/ui/components/EmptyState.kt
- app/src/main/java/dev/pocketagent/app/ui/components/SectionHeader.kt
- app/src/main/java/dev/pocketagent/app/ui/components/README.md
- app/src/main/res/values/strings_design.xml
- app/src/test/java/dev/pocketagent/app/ui/theme/TokensTest.kt
- app/src/test/java/dev/pocketagent/app/ui/theme/MotionTest.kt
- app/src/test/java/dev/pocketagent/app/ui/theme/HapticsTest.kt
- app/src/test/java/dev/pocketagent/app/ui/theme/PaletteContrastTest.kt
- app/src/test/java/dev/pocketagent/app/ui/theme/PixelFontTest.kt
- app/src/test/java/dev/pocketagent/app/ui/theme/PixelFontGoldenTest.kt
- app/src/test/java/dev/pocketagent/app/ui/components/PixelShapesTest.kt
- app/src/test/java/dev/pocketagent/app/ui/components/PressAndSheetMathTest.kt
- app/src/test/java/dev/pocketagent/app/ui/components/PetNoteQueueTest.kt
- app/src/test/java/dev/pocketagent/app/ui/components/PanelVariantTest.kt
- app/src/test/java/dev/pocketagent/app/ui/components/PixelTitleTest.kt

**Deviations:**

- Review finding 1 (major, PocketDialog scrim) was fixed as the reviewer proposed. The scrim now sits in an outer Box with the robot root and fillMaxSize, outside all padding. The inner BoxWithConstraints gets systemBarsPadding() before imePadding() and padding(Space.xl). The Dialog now uses DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false), the same setup as PocketSheet. The panel's maxHeight is now measured inside the system bars. This departs from the earlier report's note that PocketDialog keeps the default decorFitsSystemWindows.
- Review finding 2 (major, PixelFontTest) was fixed. The guard in everyGlyphRowHasItsWidth is now chars.size >= 140. The font has 113 def glyphs, 35 accent glyphs and the space glyph, which is 149 characters, so the old bound of more than 150 could never hold. I did not pin the exact count of 149, so that adding a glyph later does not break the test.
- Review finding 3 (minor, PocketSheet) was fixed. The Box holding the panel now has windowInsetsPadding(WindowInsets.statusBars) before imePadding(), with the statusBars import added. The panel can no longer slide under the status bar while the keyboard is open.
- The deviations of the first report (scrim step derived from sheet position, bottom corners hidden below the screen edge, KERN_PAIRS with 12 pairs, pixelSurface before clip, extra non-registry symbols, plain English text in DesignGallery, merged semantics in SwipeRow, Tokens.kt left unchanged) still stand.

**Requests:**

- INTEGRATE: create app/src/test/resources/pixelfont-v1.golden once from a scratch worktree of commit 344829f with only PixelFontGoldenTest.kt added, run with PIXELFONT_GOLDEN_WRITE=1, then commit the file. Until then PixelFontGoldenTest.v1GlyphsUnchanged fails with that instruction.
- INTEGRATE: run PaletteContrastTest once and paste the ids it prints into PaletteContrastTest.KNOWN_FAILURES, which is empty now. Expect outline-on-bg and outline-on-surface entries.
- ROBOT-T lint-ui: exclude ui/theme/Motion.kt, Tokens.kt, PocketTheme.kt and ui/components/DesignGallery.kt from the literal tween, dp, shape and string counts. The gallery is a debug-only screen with literal dp and English text.
- NAV: place PetNoteHost(...) once, importing PetNotes, PetNoteKind and PetNoteHost from dev.pocketagent.app.ui.components. AppShell must render DesignGallery() only when BuildConfig.TEST_HOOKS. PocketDialog and PocketSheet already apply robotRoot() from dev.pocketagent.app.ui.nav.
- TRANSLATE: add tr and fr for design_note_dismiss and design_ember_active in strings_design.xml (one-word screen-reader labels; notes are in the file).
- SWEEP and STREAM: when you delete the old private ConfirmDialog of ChatCommon.kt, use the shared ConfirmDialog with closeOnConfirm = true and danger = true, as in the area file. Every dialog now lays out inside the system bars (systemBarsPadding), so a call site should not add its own bar insets.

**Not done:**

- No golden file (pixelfont-v1.golden) exists yet, so PixelFontGoldenTest fails by design until INTEGRATE creates it.
- PaletteContrastTest.KNOWN_FAILURES is empty by design until INTEGRATE has run the test once.
- Nothing translated into values-tr or values-fr; the strings files of those folders belong to TRANSLATE.

**Unverified:**

- Nothing was compiled or run: no Gradle, no unit tests, no phone.
- PocketDialog with decorFitsSystemWindows = false: the scrim over the bars, the keyboard resize (systemBarsPadding then imePadding) and the dialog position on a real Android 17 device are untested.
- PocketSheet: the status-bar inset together with the keyboard is untested, in particular a sheet with a text field and long content.
- The count of 149 glyphs for the PixelFontTest bound comes from reading PixelFont.kt, not from running the test.

## NAV

**Review verdict:** NAV's files mostly follow the contract, but one line will not compile. I found nothing that touches the owner's data, credentials or provider setup. Every debug hook is gated on BuildConfig.TEST_HOOKS, and seeding needs IS_TWIN in both DebugLinks.parseParts and DebugLinks.runSeed. The manifest edit is only enableOnBackInvokedCallback, shortcuts.xml is untouched, and there is no seekable back code (ruling 11.5). The registry signatures (DebugRequest, DebugLinks.parse/apply/stackFor, NavFlags, NavTags, Route.Gallery, the Navigator API) match section 3.3.

The compile error: I checked the Compose animation 1.13.0 classes.jar in the Gradle cache. `ResizeMode.ScaleToBounds()` does not exist there; the companion only has `scaleToBounds(...)` and `RemeasureToBounds`. The named arguments `sharedContentState =` and `animatedVisibilityScope =` that the agent was unsure about do exist in 1.13.

I traced every unit test against the code and all should pass: NavStack ids and codec, motion kinds, tags, DebugLinks forms, AppClock. N8 is cut and was reported, and nothing else from the area file or the amendments is missing. Beyond the compile error, there are two minor behaviour issues, listed below. (3 findings, 0 blockers; fix: applied)

**Summary:** All three review findings were applied. I could not compile or run anything, so I re-read the edited files as a compiler would. (1) NavScopes.kt line 89 now reads `ResizeMode.scaleToBounds()`, lower-case with default arguments. This replaces the `ScaleToBounds()` call that does not exist in Compose animation 1.13. (2) NavDisplay now drops saved state for entries that left the stack without being composed again. A `known` id set and a `SideEffect` call `holder.removeState(id)` for every id that is gone and is not a root id (`NavStack.isRootId`). The `DisposableEffect` in EntryHost stays as a second safety net. I added the `SideEffect` import. (3) The debug link in MainActivity now runs on a process-wide scope. `debugScope` moved into a private companion object, and `onDestroy` and the `cancel` import are removed. A link that arrives on a cold start now survives the activity being recreated before the chats load. Nothing else in the area changed, and the earlier report still holds for the rest.

**Files:**

- app/src/main/java/dev/pocketagent/app/DebugLinks.kt
- app/src/main/java/dev/pocketagent/app/DebugOverrides.kt
- app/src/main/java/dev/pocketagent/app/AppClock.kt
- app/src/main/java/dev/pocketagent/app/AppLinks.kt
- app/src/main/java/dev/pocketagent/app/MainActivity.kt
- app/src/main/AndroidManifest.xml
- app/src/main/java/dev/pocketagent/app/ui/AppRoot.kt
- app/src/main/java/dev/pocketagent/app/ui/Nav.kt
- app/src/main/java/dev/pocketagent/app/ui/nav/NavDisplay.kt
- app/src/main/java/dev/pocketagent/app/ui/nav/NavFlags.kt
- app/src/main/java/dev/pocketagent/app/ui/nav/NavMotion.kt
- app/src/main/java/dev/pocketagent/app/ui/nav/NavScopes.kt
- app/src/main/java/dev/pocketagent/app/ui/nav/NavStack.kt
- app/src/main/java/dev/pocketagent/app/ui/nav/NavTags.kt
- app/src/test/java/dev/pocketagent/app/AppLinksTest.kt
- app/src/test/java/dev/pocketagent/app/AppClockTest.kt
- app/src/test/java/dev/pocketagent/app/DebugLinksTest.kt
- app/src/test/java/dev/pocketagent/app/ui/nav/NavMotionTest.kt
- app/src/test/java/dev/pocketagent/app/ui/nav/NavStackTest.kt
- app/src/test/java/dev/pocketagent/app/ui/nav/NavTagsTest.kt

**Deviations:**

- NavFlags (ui.nav): beyond the two registry vars it has `revision: MutableIntState`, `sharedElementsOn()`, `reset()`, `apply(NavFlagChange)` and `parseNavFlags(text)`. The registry vars keep their exact names, types and defaults.
- DebugLinks.parseParts has an extra trailing parameter `isTwin: Boolean = BuildConfig.IS_TWIN`. It is internal and exists for tests. `parse` keeps the registry signature.
- A debug link with a blank `lang` extra puts `lang` into `rejected`.
- A top-level `reset=1` also calls `NavFlags.reset()`.
- DebugLinks.apply calls `DebugSeeder.parse(text)` and `DebugSeeder.run(app, spec)` as members of an `object DebugSeeder` in package `dev.pocketagent.app`. The registry does not say whether it is an object or top-level functions.
- AppShell: the bar height given to `PetNoteHost` is measured after the bar's inset padding. The host adds `navigationBarsPadding()` itself.
- Route.Gallery has tabIndex 0, `showsBar()` false and rank 1.
- MainActivity: `debugScope` is now a private companion val, not an instance field. This follows the review fix, so the link outlives the activity. No `app.appScope` was used because `PocketApp.appScope` is private.

**Requests:**

- DESIGN: `Pocket.motion` / `MotionScheme` (package app.ui.theme) must offer `level`, `fast<T>()`, `glide<T>()` and `custom<T>(durationMs: Int, easing: Easing)`, with `Dur.BASE`, `Ease.enter`, `Ease.standard` and `Space.xl`/`Space.s`, as in registry 3.4. `PetNotes`, `PetNoteKind`, `PetNoteHost` and `DesignGallery` must be in package `app.ui.components`.
- ROBOT-P: `DebugSeeder` must be an `object` in package `dev.pocketagent.app` with `fun parse(text: String): Spec?` and `fun run(app: PocketApp, spec: Spec): String`. `PracticeBanner` must be in `app.ui.settings`. `CrashReportOffer` must be in `app.ui.diag`.
- INTEGRATE: if the compiler objects to `ResizeMode.scaleToBounds()` or to the named `sharedContentState =` / `animatedVisibilityScope =` arguments in NavScopes.kt, adjust the spelling to the Compose 1.13 one. The behaviour is the same. Also check `Configuration.SCREENLAYOUT_LAYOUTDIR_*` in DebugOverrides.
- INTEGRATE: AppRoot.kt still has five `stringResource(` calls, so WORDS W4 can convert them mechanically.
- BUILD: no `resValue` is needed (`shortcuts.xml` is unchanged, per ruling 29). Nothing else is requested of BUILD.

**Not done:**

- N8, the ember arc between tabs (cut first): not built.
- Seekable back (N6 gesture path): not written, per ruling 11.5. `NavFlags.backGestureDrivesScreen` stays a documented no-op and a plain BackHandler pops the stack.
- `res/xml/shortcuts.xml`: not edited, per ruling 29.
- `Route.Gallery` content is not mine: AppShell calls DESIGN's `DesignGallery()` when TEST_HOOKS is on.

**Unverified:**

- Nothing was compiled or run. Registry names from DESIGN, ROBOT and CONFIG (`Pocket.motion`, `MotionScheme`, `PetNotes`, `PetNoteHost`, `DesignGallery`, `PracticeBanner`, `CrashReportOffer`, `DebugSeeder`) are used as the registry writes them.
- The reviewer checked `ResizeMode.scaleToBounds()` against the cached animation 1.13 aar. I did not re-check it myself.
- Whether `holder.removeState(id)` on an id that is still composed (the screen leaving) really only stops it from being saved, and leaves the exit animation alone. This follows the SaveableStateHolder contract and needs the phone to confirm.
- Tab-root and stack saved state returning scrolled lists (proof scenarios a, b, c, g), shared elements travelling, and the saved-state size (the `I/Nav: saved state <n> bytes` line) all need the phone.
- Whether uiautomator exports the tags of tag-only Boxes is unknown (contract 10.5).
- The unit tests (NavStackTest, NavMotionTest, NavTagsTest, DebugLinksTest, AppClockTest, AppLinksTest) were hand-traced, not executed.

## STAGE

**Review verdict:** PASS. I found no blocker and no major problem. I read every file the agent touched in full: the git diffs of SpriteBank, HearthScene, PixelImage and Fire, plus all new main, test and tools/art files. I checked them against contract sections 1, 2.5, 3.7, 5.5 and 11, and against docs/waves/w0/STAGE.md.

**Owner's data and safety rules (1.2, 1.3).** Nothing here touches stored data, preferences, credentials or the network. The rollback rule is respected: no stored format changes.

**Registry.** Every registry symbol matches exactly:
- `HearthScene` keeps its signature and tag `stage.hearth`; `hearthSkyTop` is unchanged.
- `SpriteBank` implements `SpriteSource` and adds `preload` and `isReady`. The `lock` is declared before `sprites`, and `preload` sets its started flag before starting the thread.
- `rememberSceneClock(active, maxFps = 30): LongState`, `rememberSpritesReady(): State<Boolean>`, and `rememberClock` delegating to the new clock.
- The stage core in `ui/pixel/stage/` has no android, androidx or java.awt imports.

**Old drawing code vs the new renderer.** I compared `HEAD:HearthScene.kt` line by line with `SceneRenderer.kt` and `LegacyHearthReference.kt`. Every constant, Float expression and statement order is kept. The glow, sky and star caches have the same keys. The embers and celebration colours match. Stage clipping is the same as the old device clipping, because ox and oy are always 0 or less.

**Code that will not compile.** None found:
- Imports, overload resolution (`blit(IntArray, …)` vs `blit(Sprite, …)`, the `drawImage` with named `filterQuality`) and visibility (internal `EmberSet` and `spriteName`) are all correct.
- The private nested classes in `LegacyHearthReference` only expose types that are private in the same object.
- There are no nested-comment traps and no Long literals assigned to an Int.
- lifecycle-runtime-compose 2.11.1 is on the classpath, and `SystemMotion.kt` already imports `androidx.lifecycle.compose.LocalLifecycleOwner`.

**Tests.** I traced the expected numbers by hand and they hold:
- `StageGridTest`, including (1080,735) giving offset -2 because `roundToInt(-2.5f)` is -2.
- `FrameGateTest`: 300, 200 and 150 frames, and the slack case at 31.5 ms.
- `ArgbTest`, `PixelStageTest`, `SpriteRasterTest`.
- `SpriteLoaderTest`: line 5 and "row has 2 chars, expected 3".
- `SceneRendererTest`'s opaque-background check: the ground sprite's transparent rows 0 to 6 all sit under the sky band, which is g+8 rows high.

**Ownership and scope.** The agent touched only its own files. Nothing from the cut list was dropped. `--check`, `--only` and `--out` are implemented, `.gitattributes` covers `*.py` with LF, and `PocketApp` already calls `preload`.

**Not verified.** Nothing was compiled and no test was run. The renderer, `StageSurface` and the clock still need the phone check with Animations Off and a `gfxinfo` run. (1 findings, 0 blockers; fix: applied)

**Summary:** Review applied. The review had one minor finding (platform maths in `craft_art.py` could make `--check` fail on Linux CI). I checked it directly instead of guessing: I ran `python3 tools/art/craft_art.py --check` on Linux (Ubuntu 24.04 under WSL, glibc, Python 3.12) and on Windows (Python 3.12). Both print `ok` for `fire.pxl`, `scene.pxl` and `appicon.pxl` and exit 0. The generated files are therefore byte-identical on both platforms, so no rounding change was needed and the committed art is unchanged. I added a platform note to `tools/art/README.md` that records both runs and gives the fix to use if a platform ever prints `DIFF` (round each sin/pow result, then re-run on both). No Kotlin or Python code changed in this pass. The Kotlin files were not re-read, since nothing in them changed. They stay as in the earlier build and its manual self-compile pass; nothing was compiled and no Kotlin test was run. The filesChanged list is the area's files that differ from HEAD, re-listed from `git status`.

**Files:**

- app/src/main/java/dev/pocketagent/app/ui/pixel/Sprite.kt
- app/src/main/java/dev/pocketagent/app/ui/pixel/SpriteBank.kt
- app/src/main/java/dev/pocketagent/app/ui/pixel/SpriteLoader.kt
- app/src/main/java/dev/pocketagent/app/ui/pixel/SpriteRaster.kt
- app/src/main/java/dev/pocketagent/app/ui/pixel/PixelImage.kt
- app/src/main/java/dev/pocketagent/app/ui/pixel/HearthScene.kt
- app/src/main/java/dev/pocketagent/app/ui/pixel/Fire.kt
- app/src/main/java/dev/pocketagent/app/ui/pixel/StageSurface.kt
- app/src/main/java/dev/pocketagent/app/ui/pixel/SceneClock.kt
- app/src/main/java/dev/pocketagent/app/ui/pixel/PixelStageBitmap.kt
- app/src/main/java/dev/pocketagent/app/ui/pixel/stage/Argb.kt
- app/src/main/java/dev/pocketagent/app/ui/pixel/stage/PixelStage.kt
- app/src/main/java/dev/pocketagent/app/ui/pixel/stage/StageGrid.kt
- app/src/main/java/dev/pocketagent/app/ui/pixel/stage/SceneArt.kt
- app/src/main/java/dev/pocketagent/app/ui/pixel/stage/Embers.kt
- app/src/main/java/dev/pocketagent/app/ui/pixel/stage/SceneModel.kt
- app/src/main/java/dev/pocketagent/app/ui/pixel/stage/SceneRenderer.kt
- app/src/main/java/dev/pocketagent/app/ui/pixel/stage/FrameGate.kt
- app/src/test/java/dev/pocketagent/app/ui/pixel/stage/LegacyHearthReference.kt
- app/src/test/java/dev/pocketagent/app/ui/pixel/stage/TestSprites.kt
- app/src/test/java/dev/pocketagent/app/ui/pixel/stage/ArgbTest.kt
- app/src/test/java/dev/pocketagent/app/ui/pixel/stage/PixelStageTest.kt
- app/src/test/java/dev/pocketagent/app/ui/pixel/stage/StageGridTest.kt
- app/src/test/java/dev/pocketagent/app/ui/pixel/stage/FrameGateTest.kt
- app/src/test/java/dev/pocketagent/app/ui/pixel/stage/SpriteRasterTest.kt
- app/src/test/java/dev/pocketagent/app/ui/pixel/stage/SpriteLoaderTest.kt
- app/src/test/java/dev/pocketagent/app/ui/pixel/stage/StageCoreIsPureTest.kt
- app/src/test/java/dev/pocketagent/app/ui/pixel/stage/SceneRendererParityTest.kt
- app/src/test/java/dev/pocketagent/app/ui/pixel/stage/SceneRendererTest.kt
- tools/art/craft_art.py
- tools/art/pxl.py
- tools/art/contact_sheet.py
- tools/art/README.md

**Deviations:**

- No registry symbol was changed. Registry symbols provided: `SpriteBank.preload`/`isReady`, `rememberSceneClock`, `rememberSpritesReady`, `rememberClock`, `HearthScene`, `hearthSkyTop`, and the stage core.
- `EmberSet` is `internal` in package `ui.pixel.stage` (it was `internal` in `ui.pixel`), as the area file says; Fire.kt imports it.
- `SceneRendererTest` has three extra tests beyond the area file's list (`everyStateDrawsAndHasAnOpaqueBackground`, `lightAndDarkPalettesDiffer`, `aMissingSpriteFailsAtConstruction`). `PixelStageTest` and `SpriteRasterTest` also have a few extra cases. `Assume.assumeTrue` via `TestSprites.all()` (contract 1.1) is used instead of an early return when the assets folder is missing.
- `craft_art.py --check --out` is rejected with argparse's exit 2 (a bad call).
- REFUTED (as a needed code change): the minor finding about `craft_art.py` possibly differing on Linux. I ran `--check` on Linux (Ubuntu 24.04 under WSL, glibc, Python 3.12) and on Windows (Python 3.12). Both print ok for all three files and exit 0, so the committed bytes match on both platforms and no rounding was added. A platform note and the fallback recipe went into tools/art/README.md instead. This check ran with Python 3.12 only. CI's Python version is not checked here.

**Requests:**

- CONFIG-R PocketApp.kt: per 3.13 item 5 the sprite init becomes `dev.pocketagent.app.ui.pixel.SpriteBank.preload(this)`. Without it nothing breaks: the first composable still calls `init` as before.
- CONFIG-D Diagnostics.kt: optional, list `SpriteBank.loadErrors` (`List<String>`, `file:line: message`) when not empty, per request R2 / #25.
- BUILD build.yml: the `unit` job runs `python3 tools/art/craft_art.py --check` (3.2, #26). The script needs no package. `contact_sheet.py` is not run in CI. The Linux run of `--check` now gives ok (see deviations), so the step can be marked verified on Linux.
- ROBOT-P `.gitattributes`: `*.py text eol=lf` already covers `tools/art/*.py`; the files are LF.
- INTEGRATE: compile first. The risky places are `androidx.lifecycle.compose.LocalLifecycleOwner` and `androidx.lifecycle.repeatOnLifecycle` in `SceneClock.kt` (lifecycle 2.11.1 is on the classpath), `SpriteSource` as a `fun interface` with an operator `get` that `SpriteBank` overrides, and the visibility of `LegacyHearthReference`'s private nested classes.

**Not done:**

- Nothing cut. The Compose lifecycle pause, `PixelStage.toBitmap` and `tools/art/contact_sheet.py` with `pxl.py` are all implemented.

**Unverified:**

- Nothing was compiled and no Kotlin test was run (Gradle not allowed). The self-compile pass was manual, done in the earlier build and not repeated for unchanged Kotlin files in this pass.
- `SceneRendererParityTest` (84 renders) has not run. It proves parity only if `LegacyHearthReference.kt` is a faithful copy of the old code. The phone check with Animations Off remains the second witness.
- Hardware-rendering behaviour of `StageSurface` (ring of three bitmaps, no `recycle`) and the cost of one scene render (budget under 1 ms) can only be seen on the phone with `gfxinfo`.
- `stage.hearth` appearing in `uiautomator dump` depends on NAV's `robotRoot()` at the shell root.
- `SceneClock` behaviour while backgrounded and the 30 per second cap on 60/90/120 Hz screens are tested only through the pure `FrameGate`, not in a running app.

## STREAM

**Review verdict:** STREAM is close, but it will not compile yet. I found two compile errors, both one-line fixes. Nothing in STREAM's files can touch the owner's data, credentials or provider setup, spend money, or weaken the rules in contract 1.2 and 1.3.

**Engine**
- The `Turn` lock, the `StreamPush` timer, the cancel in `finish` and the lock order (Turn, then repository) are correct. No inversion; nothing is written after `closeBubble`, because `bubbleId` is cleared under the lock.
- The `StreamPush` tests and the `ChatEngineTrailingTest` timings check out by hand.

**Streaming text**
- `parseMarkdownLines` records the ranges correctly: paragraphs flush before every other block, and the end-of-input flush uses `lines.size`.
- `MarkdownStream` keeps a block only when a later complete line exists, and its tail re-parse starts from that block's first line. The CR handling matches `parseMarkdown`.
- `StreamSmoother` and `ToolOutputChunks` pass every case in their tests when traced by hand.

**Chat screen split**
- The registry signatures match.
- Only `ChatMessageList` collects `messagesFlow`. `messagesFlow` is declared before `shape` and `matches`.
- All seven toasts go through `say`, with the right kinds. The haptics, `Loop.CARET`, `motion.bouncy()`, `ConfirmDialog(closeOnConfirm = true, danger = true)` and the explicit `ui.components` imports follow DESIGN 3.10.
- The tags match 3.12, with one tag per node.
- No files were touched outside STREAM's ownership.

**Scope**
- The one gap, `navSharedBounds` on the top-bar title, is reported and blocked by `PocketTopBar`. Nothing else was dropped. (4 findings, 2 blockers; fix: applied)

**Summary:** All four findings applied or handled. The two blockers are fixed. In ToolOutputSheet.kt the sheet scope is now captured as `val sheet = this` and the Close button calls `sheet.close()`. ChatMessageList.kt now imports `androidx.compose.animation.core.VisibilityThreshold`. The tool-card index is computed once per list as `toolIdx` (`remember(reversed)`, one `IntArray` pass), replacing the per-row scan, and `toolCardIndex` and the unused `Message` import are removed. The `chat.tool.<i>` numbering is unchanged (0 = newest). The `navSharedBounds` title finding stays open: PocketTopBar has no `titleModifier` today (checked), so the fix is a request to DESIGN. I re-read the touched parts of both files for imports, scope and types. Nothing was compiled or run.

**Files:**

- app/src/main/java/dev/pocketagent/app/data/StreamPush.kt
- app/src/main/java/dev/pocketagent/app/data/ChatEngine.kt
- app/src/main/java/dev/pocketagent/app/ui/chat/ChatScreen.kt
- app/src/main/java/dev/pocketagent/app/ui/chat/ChatScreenState.kt
- app/src/main/java/dev/pocketagent/app/ui/chat/ChatSlashActions.kt
- app/src/main/java/dev/pocketagent/app/ui/chat/ChatTopArea.kt
- app/src/main/java/dev/pocketagent/app/ui/chat/ChatMessageList.kt
- app/src/main/java/dev/pocketagent/app/ui/chat/ChatInputArea.kt
- app/src/main/java/dev/pocketagent/app/ui/chat/ChatDialogs.kt
- app/src/main/java/dev/pocketagent/app/ui/chat/ToolOutputSheet.kt
- app/src/main/java/dev/pocketagent/app/ui/chat/StreamSmoother.kt
- app/src/main/java/dev/pocketagent/app/ui/chat/MarkdownStream.kt
- app/src/main/java/dev/pocketagent/app/ui/chat/StreamingMarkdown.kt
- app/src/main/java/dev/pocketagent/app/ui/chat/Markdown.kt
- app/src/main/java/dev/pocketagent/app/ui/chat/MarkdownView.kt
- app/src/main/java/dev/pocketagent/app/ui/chat/ChatMessages.kt
- app/src/main/res/values/strings_stream.xml
- app/src/test/java/dev/pocketagent/app/data/StreamPushTest.kt
- app/src/test/java/dev/pocketagent/app/ChatEngineTrailingTest.kt
- app/src/test/java/dev/pocketagent/app/ui/chat/StreamSmootherTest.kt
- app/src/test/java/dev/pocketagent/app/ui/chat/MarkdownStreamTest.kt
- app/src/test/java/dev/pocketagent/app/ui/chat/MarkdownBlocksTest.kt
- app/src/test/java/dev/pocketagent/app/ui/chat/ChatShapeTest.kt
- app/src/test/java/dev/pocketagent/app/ui/chat/SearchViewTest.kt
- app/src/test/java/dev/pocketagent/app/ui/chat/RowGroupingTest.kt
- app/src/test/java/dev/pocketagent/app/ui/chat/ToolOutputChunksTest.kt

**Deviations:**

- ChatScreenState: the haptics field is `haptics: HapticPlayer` (from `rememberHaptics()`), not `haptic: HapticFeedback`, because contract 5.6 requires `HapticCue` and `rememberHaptics` in STREAM's files.
- ChatScreenState: `context`, `haptics`, `conversation` and a new `nav: Navigator` are `lateinit var`s set in `rememberChatScreenState`, so the constructor keeps the registry signature. `nav` is an addition: the row actions and `doFork` need it. The class implements `RememberObserver` so its two collectors (shape, matches) start on remember and stop on forget.
- ChatScreenState.executeSlash is an extension function in ChatSlashActions.kt (as the area file says), not a member as in the registry text. Calls `state.executeSlash(p)` are identical.
- RowLayout has one extra trailing field with a default, `toolIndex: Int = -1`, so the card can carry the `chat.tool.<i>` tag. `RowGrouping`, `RowLayout`, `ChatRowActions`, `ChatShape` and `SearchView` live in ChatScreenState.kt.
- ChatTopArea: only `navSharedElement(SharedKeys.chatAvatar(convId))` on the avatar is applied. `navSharedBounds(SharedKeys.chatTitle(convId))` on the title is NOT applied because PocketTopBar (checked again, no `titleModifier` today) cannot take a modifier on its title. Open until DESIGN adds the parameter (see requests).
- StreamingMarkdown adds a static path: a finished message older than the 1.5 s replay window when first drawn is parsed once and drawn with `MdBlocks` (no frame loop). A streaming message at level OFF shows everything at once.
- Streaming row placement follows contract 3.6: the streaming row gets `animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)`, other rows get `placementSpec = null` while the last message streams. The default placement spec is a private spring constant.
- Tool card: click label is the new `stm_tool_card_open`; chevron replaced by `PixelIcon.MORE`; the inline expand state is gone. Memory tool cards also open the sheet.
- `say` and `toastNotSent` as registered. Attach-failed, voice-unavailable, export-failed and send-not-accepted use `PetNoteKind.ERROR`; fork-created and export-saved use `SUCCESS`.
- `ChatDialogs` reads messages through `state.messagesNow()` when a sheet opens, so star state and the controls-sheet usage totals do not update while open.
- Tests use `internal` APIs of the same Gradle module (`StreamPush`, `parseMarkdownLines`, `ToolOutputChunks`, `chatShapeOf`).
- Review round: `toolIdx` in ChatMessageList is computed once per list and still shifts when a new tool message arrives, because `chat.tool.<i>` (0 = newest) requires that; only the per-row scan was removed.

**Requests:**

- DESIGN ui/components/PocketTopBar.kt: add a last parameter `titleModifier: Modifier = Modifier` applied to the title Text/PixelText, so STREAM can put `navSharedBounds(SharedKeys.chatTitle(convId))` on the chat title. After it exists, the change in ChatTopArea.kt is: pass `titleModifier = Modifier.navSharedBounds(SharedKeys.chatTitle(convId))` to PocketTopBar and add `import dev.pocketagent.app.ui.nav.navSharedBounds`. INTEGRATE may do both edits if DESIGN does not.
- ROBOT-P: practice model `RUN_TOOL` and `LONG` must produce what contract 3.10 lists (at least 120 lines of 80 characters; heading, bullets, numbered list, fenced code, table, bold, inline code, link), and `PAUSE` must send `First half of the answer.`, stay silent for 5 s, then ` Second half.`.
- ROBOT-T scenarios: tags STREAM writes are listed in contract 3.12: `chat.screen`, `chat.list`, `chat.msg.<i>`, `chat.msg.user`, `chat.msg.bot`, `chat.msg.bot.live`, `chat.msg.tool`, `chat.msg.error`, `chat.msg.note`, `chat.tool.<i>`, `chat.tool.sheet`, `chat.tool.output`, `chat.tool.copy`, `chat.tool.close`, `chat.thought`, `chat.memory.open`, `chat.pending`, `chat.jump`, `chat.empty`, `chat.search`, `chat.find`, `chat.controls`, `chat.menu`, `chat.approval`. The message menu has no tag of its own and needs `adb shell input swipe x y x y 700` on `chat.msg.bot`.
- TRANSLATE: `strings_stream.xml` has four strings and one plural (`stm_tool_sheet_truncated`, `other` only in English: add the forms each language needs; the first number, 60,000, selects the plural).
- WORDS W1: convert the `stringResource(` and `ctx.getString(` sites in ChatScreenState.kt, ChatSlashActions.kt, ChatTopArea.kt, ChatInputArea.kt, ChatDialogs.kt, ChatMessageList.kt, ToolOutputSheet.kt and StreamingMarkdown.kt. ChatMessages.kt and MarkdownView.kt are W2's. `ToolOutputSheet` uses `pluralStringResource` and `NumberFormat` with `currentLocale()` (W2 or W4 may change that call to `appLocale()`).
- INTEGRATE: first compile checks for this area: `PetNoteKind`/`PetNotes`, `HapticPlayer`/`HapticCue`/`rememberHaptics`/`Loop`/`motion`, `PocketSheet` with `PocketSheetScope.close()` (ToolOutputSheet captures the scope as `sheet`), `ActionSheet`, `SheetAction`, `ConfirmDialog`, `navSharedElement`, `SharedKeys.chatAvatar`, and `androidx.compose.animation.core.VisibilityThreshold` in ChatMessageList.

**Not done:**

- `navSharedBounds(SharedKeys.chatTitle(convId))` on the chat title: left as a no-op until PocketTopBar gets `titleModifier` (request to DESIGN).

**Unverified:**

- Nothing was compiled, run or tested: no Gradle, no unit tests, no phone. All checks were by re-reading.
- The DSL-marker fix (`val sheet = this`, then `sheet.close()`) rests on PocketSheetScope extending ColumnScope as seen in PocketSheet.kt; the compiler has not confirmed it.
- Streaming smoothness, the 60 ms trailing update and the tool sheet on a long output are not tried on the phone.

## CONFIG-R

**Review verdict:** Nearly ready: one major problem to fix before this goes on the owner's phone, plus two minor test gaps. CONFIG-R's files follow the contract closely.

What checks out against the contract:
- Guard block, including ruling 11.8 (the Anthropic mode leaves out `security:`; every other mode and the practice yaml keep it).
- The fixtures.
- `CredentialAction.UNTOUCHED` in practice: `apply` never builds the provider inputs and never touches the credential files.
- `GuestPrefs`: expiry, reset at every cold start, and `practiceActive` set in the constructor.
- `savedProblems` returns empty while practice is on.
- `launchCommand`, `STILL_STARTING`, and `onGuestOptionsChanged` (including the start out of `Error`).
- `PocketApp` in the 3.13 order: `Texts.install`, `guestPrefs` after settings, `crashReports`, `SpriteBank.preload`, the provider lambda of item 6, `wave0Slots()` and the start log line.
- `Transcript.splitLong` with the system split, and the `HermesApi` split.
- `PrivacyRows` and `strings_privacy.xml`.
- `BehaviourSection` (one import and one call).
- `smoke-test.sh` phase 3, rewritten per 5.7.

No compile problem was found. Every registry symbol consumed is now in the tree with matching signatures: `PracticeModel`, `PocketSwitchRow(modifier)`, `Space.s`, `SubHeading`, `SpriteBank.preload`, `Texts.install`, `CrashReports(context, diagnostics)` and `evaluate()`. `RuntimeManager` has only one companion object, and the init block stays last.

I hand-traced the expected values in the new tests (split arithmetic, surrogate pairs, the persona split, the JSON part counts, the GuestPrefs fake); they hold. No file outside CONFIG-R's ownership was touched, and `rootfs/install-hermes.sh` is untouched, per ruling 11.8.

The one major problem is a race in the agent's own launch-window fix. The config is still written without a lock in two places. If someone saves the config (the web switch or "remove key") at the wrong moment, the owner's real provider config and key can land under a Hermes that was started in practice mode. Details and the fix are in the first finding. (3 findings, 0 blockers; fix: applied)

**Summary:** The three review findings are applied. Nothing was compiled or run. The major is fixed in RuntimeManager.kt by a plain monitor `guestConfigLock`, which both a launch's options-and-write and a rewrite's options-and-write now hold. `currentGuestOptions()` has a pre-port window case: while a run is Starting with no practice port recorded and the practice switch is on, it returns the practice options with the stand-in port `PracticeModel.SMOKE_PORT`. A rewrite in that window therefore never builds the owner's inputs or installs credentials, and the launch's locked write always comes last with the real port. `savedProblemsAreEmptyWhilePracticeIsActive` now has a negative control that asserts a non-empty list with the flag off. `HermesConfigPlanTest` has a `practiceLeavesCredentialsUntouched` test, which checks `UNTOUCHED` for every mode; the longer apply-level test keeps its own name. I re-read the edited regions of RuntimeManager.kt and both test files for imports, visibility and suspend context. `HermesConfig.apply` is not suspend, so `synchronized` inside `launchHermes` is fine.

**Files:**

- app/src/main/java/dev/pocketagent/app/runtime/GuestPrefs.kt
- app/src/main/java/dev/pocketagent/app/runtime/GuestCodeScan.kt
- app/src/main/java/dev/pocketagent/app/runtime/ProviderConfig.kt
- app/src/main/java/dev/pocketagent/app/runtime/HermesConfig.kt
- app/src/main/java/dev/pocketagent/app/runtime/RuntimeManager.kt
- app/src/main/java/dev/pocketagent/app/PocketApp.kt
- app/src/main/java/dev/pocketagent/app/data/Transcript.kt
- app/src/main/java/dev/pocketagent/app/api/HermesApi.kt
- app/src/main/java/dev/pocketagent/app/ui/diag/PrivacyRows.kt
- app/src/main/java/dev/pocketagent/app/ui/settings/BehaviourSection.kt
- app/src/main/res/values/strings_privacy.xml
- rootfs/smoke/smoke-test.sh
- rootfs/smoke/fixtures/studio.yaml
- rootfs/smoke/fixtures/studio-effort.yaml
- rootfs/smoke/fixtures/studio-nokey.yaml
- rootfs/smoke/fixtures/express.yaml
- rootfs/smoke/fixtures/vertex-sa.yaml
- rootfs/smoke/fixtures/vertex-user.yaml
- rootfs/smoke/fixtures/practice.yaml
- rootfs/smoke/fixtures/practice.env
- rootfs/smoke/fixtures/studio-nokeyless.yaml
- rootfs/smoke/fixtures/studio-nokeyless.env
- app/src/test/java/dev/pocketagent/app/ProviderConfigTest.kt
- app/src/test/java/dev/pocketagent/app/HermesConfigPlanTest.kt
- app/src/test/java/dev/pocketagent/app/GuestCodeScanTest.kt
- app/src/test/java/dev/pocketagent/app/GuestPrefsTest.kt
- app/src/test/java/dev/pocketagent/app/TranscriptSplitTest.kt
- app/src/test/java/dev/pocketagent/app/ChatRequestSplitTest.kt

**Deviations:**

- ProviderConfig.renderGuardYamlFor(mode, o) is an extra symbol, not in the registry. It implements ruling 11.8: renderGuardYaml() keeps its exact text with security.allow_lazy_installs: false, but renderYaml(i, o) uses the per-mode form, so for AuthMode.ANTHROPIC the security: block is left out. All other modes and the practice yaml keep it.
- AGENT_TOOLSETS and WITHHELD_TOOLSETS are as the registry says; connections is withheld (ten toolsets in, five out), as logged in CONFIG.md D1.
- CredentialAction and GuestFiles are top-level types in HermesConfig.kt (package dev.pocketagent.app.runtime), not nested in HermesConfig; the registry text is ambiguous about nesting.
- GuestPrefs has an internal constructor GuestPrefs(sp, isTwin = BuildConfig.IS_TWIN, now = ...) so tests can pass sp, isTwin and now. The registry-listed public members are unchanged.
- RuntimeManager.lastGuestOptions is set at the start of every launch and by every rewriteGuestConfigIfInstalled, so it always describes the configuration last written (3.10).
- New private RuntimeManager.guestConfigLock (plain monitor, not the coroutine mutex) serialises a launch's options-and-write against a rewrite's options-and-write (review finding 1). Not in the registry; private.
- currentGuestOptions() has an extra pre-port case: while Starting with no practice port recorded and the practice switch on, it returns the practice options with the stand-in port PracticeModel.SMOKE_PORT (review finding 1). A rewrite in that window never builds the owner's inputs; the launch's locked write puts the real port in.
- PrivacyRows tags the switch through PocketSwitchRow(modifier = ...), as the registry says, not through a wrapping Box as CONFIG.md drafted. This depends on DESIGN adding the modifier parameter.
- The 60,000-character split tests are in the new TranscriptSplitTest only; TranscriptTest.kt is not edited.
- GuestPrefs.enforceExpiry() runs after the stub and proot checks in launchHermes, not literally first. It still runs before the options are read.
- Review finding 3: the contract's test name practiceLeavesCredentialsUntouched is now a plan-level test in HermesConfigPlanTest. The file-level test applyInPracticeNeitherReadsTheOwnersKeyNorTouchesTheCredentialFiles keeps its own name beside it, so both levels are covered.

**Requests:**

- ROBOT-P: runtime/practice-start.sh and runtime/PracticeModel.kt must exist. The smoke script copies $REPO/runtime/practice-start.sh, and my code calls PracticeModel.PROVIDER, MODEL_ID, API_KEY, SMOKE_PORT, baseUrl(port), install(context, paths) and guestCommand(port). The log line the smoke script reads must keep the fields "POST" and "n_msgs", and `heads` must be logged (the current mock_llm.py does both).
- DESIGN: PocketSwitchRow must gain `modifier: Modifier = Modifier` as its last parameter. WebLookupsRow in PrivacyRows.kt passes modifier = Modifier.testTag("privacy.webLookups"), and the file does not compile without it.
- lead (AGENTS.md): in the Anthropic mode security.allow_lazy_installs is left out of config.yaml so Hermes can fetch its SDK on first use (ruling 11.8). The image has no anthropic extra, and the smoke test has no Anthropic import check.
- lead (AGENTS.md): B21 is only half closed. cronjob_manage is off the list, but the terminal tool can still run `hermes cron ...` and the gateway ticks every 60 s. Wave 3's gate has to cover it.
- slot: CRASH is the only wave0Slots() line, written in PocketApp.kt. No init-line requests for the next wave.

**Not done:**

- Nothing from my scope was cut. rootfs/install-hermes.sh is untouched, per ruling 11.8.
- TranscriptTest.kt is not extended; the 60,000-character split cases are in TranscriptSplitTest.

**Unverified:**

- No Kotlin was compiled and no test was run. I re-read the edited regions of RuntimeManager.kt and both test files for imports, nullability, init order and suspend context.
- The new negative control assumes that, with the fake prefs, the default setup (API key mode, no key file, non-blank default model) yields at least one problem (NO_API_KEY). I read ProviderConfig.problems and savedProblems and the defaults looked right, but the test never ran.
- The pre-port window has one residual race, not closed by the lock. If the practice switch flips while a launch that began as a normal run is still in its pre-port window, a rewrite could write practice options before the launch's own write. In practice the switch calls restart(), not a rewrite, in that state, and the launch's write still comes last, so the final file is the launch's. Untested.
- The new tests assume the final PracticeModel, CrashReports, SpriteBank.preload and Texts.install match the registry. HermesConfigPlanTest.launchCommandFormsAreExact pins `sh /opt/agiri/practice/start.sh <port>`.
- The smoke-test.sh phase 3 rewrite ran only through `sh -n`. Whether allow_lazy_installs: false makes Hermes print a notice that the smoke grep treats as an error stays unproven until CI.
- Whether /v1/toolsets enabled equals the ten exactly depends on Hermes v0.23.3 reading platform_toolsets.api_server as the source says. I did not boot it.
- Practice-mode behaviour with a real running Hermes (restart on the switch, expiry, failed launch with exit 70 or 71) is untested; there is no phone and no compile.
- The .env fixtures with CRLF in this working tree come from autocrlf. .gitattributes pins the fixtures to LF, and the tests normalise them anyway.

## CONFIG-D

**Review verdict:** CONFIG-D passes review. I found no blocker and no major problem, only one minor robustness issue. Nothing leaks, deletes or rewrites the owner's data, credentials or provider setup. Export writes only into cache/diagnostics. The crash report goes only to the clipboard, built from the already-redacted clipboard dump. The new pref file crash_reports is ignored by the pre-wave code and is not in the backup schema.

Every registry symbol CONFIG-D provides matches 3.7, 3.8 and 3.11: ExitReasons, CrashReports(context, diagnostics, prefs, reader) with offer, evaluate, reportText, copyReport and dismiss, LogExport.Source and zip, exportLogs, exportLogsIntent, the suspend shareLogsIntent, DiagnosticsExtras() and CrashReportOffer().

Symbols it consumes resolve against the current tree:
- GuestPrefs: webKeyless, practiceModel, practiceMinutesLeft, get.
- RuntimeManager: lastGuestOptions, guestCodeReport.
- GuestCodeKind and GuestCodeScan.summary.
- Texts.describe() in object Texts, and SpriteBank.loadErrors.
- CtlUi's CText, Muted and ErrorText, with the parameter names used.
- PixelPanel, PixelButton (including ButtonKind.GHOST) and PocketDialog signatures.
- Paths.logs, lastLaunch, diagnosticsDir; FileUtil.capTail; Redactor.addSecret.
- The cache-path entry diagnostics/ in file_paths.xml.

Compose BOM 2026.11.01 makes pluralStringResource stable. The when-branch smart cast on the nullable exit list compiles. ApplicationExitInfo accessors are used with the right types. No nested-comment trap. strings_config.xml is well-formed and every key has its t-note. Keys used in code all exist.

Both test classes can pass and test real behaviour. ExitReasons and FileUtil load on the JVM because Build is touched only inside read().

Scope is complete against the area file, the #4/#13/#25/#52/#57 rulings and 5.7 [D]: Diagnostics lines, the guest-options, guest-code, texts and sprite-error sections, report.txt in the zip, both cards, the dialog, and the extra error key. The reported deviations are all additive or reasonable. Only owned files were touched. (1 findings, 0 blockers; fix: applied)

**Summary:** The one review finding (minor) is fixed. `Diagnostics.exportLogs()` is now `@Synchronized`, so two exports can no longer write the same `.zip.part` file, and the cleanup of stale `.part` files can no longer delete one that is still being written. The file name format `agiri-logs-yyyyMMdd-HHmmss.zip` is unchanged. A second export in the same second runs after the first and replaces its zip through the existing rename logic. `buildFull()` runs on the same thread inside the lock, so nothing can deadlock. I re-read the edited function and found no compile issues. Nothing was compiled or run, because this stage has no Gradle. The rest of CONFIG-D is as in the earlier report.

**Files:**

- app/src/main/java/dev/pocketagent/app/diag/ExitReasons.kt
- app/src/main/java/dev/pocketagent/app/diag/CrashReports.kt
- app/src/main/java/dev/pocketagent/app/diag/LogExport.kt
- app/src/main/java/dev/pocketagent/app/diag/Diagnostics.kt
- app/src/main/java/dev/pocketagent/app/MainViewModel.kt
- app/src/main/java/dev/pocketagent/app/ui/diag/DiagnosticsExtras.kt
- app/src/main/res/values/strings_config.xml
- app/src/test/java/dev/pocketagent/app/ExitReasonsTest.kt
- app/src/test/java/dev/pocketagent/app/LogExportTest.kt

**Deviations:**

- Extra key beyond the area table: cfg_crash_copy_failed (error line in the crash dialog when the clipboard refuses).
- Extra public helpers not in the registry: LogExport.sources, LogExport.readTail and its constants, ExitReasons.MIN_SDK, and internal formatExitTime and exitSentenceRes in DiagnosticsExtras.kt. No registry signature changed.
- CrashReports.copyReport() keeps the registry form (no return value) and throws when the clipboard refuses, before it dismisses. The dialog catches that, stays open and shows cfg_crash_copy_failed.
- The crash dialog's date uses LocalConfiguration.current.locales[0] directly instead of TEXT's appLocale().
- The exit-reason card shows only the main process's exits and the newest 5; the dump section lists all processes.
- The dump line from ExitReasons.describe adds process=<name>; all other old fields are kept.
- The Diagnostics dump has no tool-list line (it would depend on CONFIG-R's ProviderConfig.AGENT_TOOLSETS).
- Tags are plain string literals, not RobotTags constants.
- Review finding on exportLogs() serialisation: fixed with @Synchronized (no deviation from the registry; the signature is unchanged).

**Requests:**

- ROBOT-P ui/settings/RuntimeSection.kt: DiagnosticsSection must call DiagnosticsExtras() from dev.pocketagent.app.ui.diag, and the Export logs button uses vm.shareLogsIntent() (suspend) with the cfg_diag_exporting and cfg_diag_export_failed keys.
- NAV ui/AppRoot.kt: place CrashReportOffer() once (import dev.pocketagent.app.ui.diag.CrashReportOffer), per 5.4.
- CONFIG-R: GuestCodeEntry, GuestCodeKind {HOOK, PLUGIN}, GuestCodeScan.summary, RuntimeManager.lastGuestOptions and guestCodeReport, GuestPrefs.practiceModel, webKeyless and practiceMinutesLeft(), and PocketApp.crashReports are used as in CONFIG.md 3.6, 3.10 and 3.11; PocketApp must call CrashReports(this, diagnostics) and crashReports.evaluate() off the main thread.
- TRANSLATE: tr and fr for all keys of strings_config.xml, including cfg_crash_copy_failed and the plural cfg_diag_guest_found.
- WORDS W6: DiagnosticsExtras.kt has stringResource and pluralStringResource calls to convert (pluralStringResource maps to tn).

**Not done:**

- Nothing from CONFIG-D's scope was cut.
- No test for CrashReports itself: it needs a Diagnostics, which needs a RuntimeManager. The pure rules behind it are covered by ExitReasonsTest.

**Unverified:**

- Nothing compiled and no test run (no Gradle in this stage).
- Symbols from CONFIG-R, TEXT, STAGE, DESIGN and BUILD that did not exist in the tree yet: GuestPrefs, GuestCodeScan, GuestCodeKind, lastGuestOptions, guestCodeReport, Texts.describe(), BuildConfig.APP_VARIANT, SIGNING_KIND, CI_RUN, TEST_HOOKS, IS_TWIN, DEEP_LINK_SCHEME, Space, PetNotes and PetNoteKind. Names follow the registry; a mismatch will show at integration.
- CtlUi.kt must keep the internal helpers CText, Muted and ErrorText.
- Whether uiautomator exports the tags on the merged-semantics cards (NAV risk 4).
- Whether `am crash` is recorded as REASON_CRASH, for the crash-offer scenario.
- That pluralStringResource and the androidx.annotation RequiresApi import resolve on the current Compose BOM and core-ktx classpath.
- Share of the zip through the chooser on the phone (FileProvider authority follows packageName).

## TEXT

**Review verdict:** TEXT's own files look sound. I found nothing that touches the owner's data, credentials, provider setup or the safety rules of 1.2 and 1.3. The Kotlin should compile against the registry. The forms of t, tn, Texts, TestLanguage, EnumLabels and Mirroring match 3.6. installForTest makes get and getQuantity skip the Context, and TestTextsTest covers ContextWrapper(null). EnumLabels has 60 icons and 16 avatars, which matches the enums. Every eng_ key that SWEEPS.md uses exists. The ten plurals are correct in en, tr and fr. No file outside TEXT's ownership was edited. strings_routines.xml and the section-header keys were not touched. The pack cut is reported and allowed.

The plural call sites are already covered by WORDS recipe T (SWEEPS.md lines 526 to 538). The current red results all belong to other areas: the parity errors, the 4 length errors, and the stretched files now being stale after DESIGN's later edits.

Two real problems remain:
1. The source hash in make-test-language depends on a culture-sensitive sort. CI's Linux pwsh will compute a different hash than Windows 5.1, so the `-Check` step in CI would fail every run.
2. The committed values-en-rXZ folder will very likely be picked by Android for plain "en" and for English regions other than en-US. Both debug variants would then show the bracketed language to anyone using English, including the twin label and the owner-debug launcher label. (2 findings, 0 blockers; fix: applied)

**Summary:** Both review findings are fixed in the TEXT area. All edits are in tools/ and app/src/debug/res. Nothing was compiled under Gradle (none was possible). The Kotlin files are unchanged this session. make-test-language.ps1 -SelfTest passes (28 checks, including the new ones). The generator re-ran and -Check now exits 0. strings-check.ps1 still runs on Windows PowerShell 5.1 and reports 18 errors, none in TEXT's own files: 14 are parity errors for tr/fr files and two design keys that TRANSLATE has not written yet, and 4 are length errors in other areas' keys (see REQUESTS).

Finding 1 (major, file order and hash): fixed.
- Get-EnglishFiles in make-test-language.ps1 now sorts the names with [StringComparer]::Ordinal instead of Sort-Object.
- strings-check.ps1 gets two helpers, Sort-Ordinal and Sort-OrdinalUnique. They replace all five Sort-Object uses: Read-Language file order, language list, parity file names, manifest key list, baseline ids.
- A new -SelfTest case checks that strings.xml sorts before strings_a.xml.

Finding 2 (major, English request picks the bracketed folder): fixed with the reviewer's proposal.
- make-test-language.ps1 now also writes app/src/debug/res/values-en/ (12 files). It is a copy of the English texts with the notes left out, carries the source-hash header, and is part of the stale-file cleanup. A new -EnOutDir parameter sets its location; by default it is the folder next to -OutDir.
- Test-Fresh and -Check now check both folders.
- A request for plain "en" or en-GB now finds a region-less values-en folder. A request for en-XZ still gets values-en-rXZ.
- I also found that a plain English copy in the debug source set would hide the twin overlay (app/src/twin/res/values/strings_build.xml: app_name "Agirî Test", srf_ask_agiri, srf_tile_label). The copy therefore skips every name the twin overlay defines, and skips translatable=false texts. The twin names are part of the hash, so changing the overlay makes the folders stale.
- Because the hash input changed, AlgoVersion went from 2 to 3 and both generated folders were regenerated.
- The interim strings-check.baseline.json was not rewritten (INTEGRATE does that). Only the order of its ids would differ and the ratchet reads it as a set.

**Files:**

- app/src/main/java/dev/pocketagent/app/ui/text/TextSource.kt
- app/src/main/java/dev/pocketagent/app/ui/text/PluralRules.kt
- app/src/main/java/dev/pocketagent/app/ui/text/ProtectedTexts.kt
- app/src/main/java/dev/pocketagent/app/ui/text/Mirroring.kt
- app/src/main/java/dev/pocketagent/app/ui/text/EnumLabels.kt
- app/src/main/java/dev/pocketagent/app/ui/text/ResourceTextSource.kt
- app/src/main/java/dev/pocketagent/app/ui/text/Texts.kt
- app/src/main/java/dev/pocketagent/app/ui/text/TextsCompose.kt
- app/src/main/assets/text/protected-keys.txt
- app/src/main/res/values/strings_engine.xml
- app/src/main/res/values/strings_chat.xml
- app/src/main/res/values-tr/strings_chat.xml
- app/src/main/res/values-fr/strings_chat.xml
- app/src/main/res/values/strings_control.xml
- app/src/main/res/values-tr/strings_control.xml
- app/src/main/res/values-fr/strings_control.xml
- app/src/debug/res/values-en-rXZ/strings.xml
- app/src/debug/res/values-en-rXZ/strings_chat.xml
- app/src/debug/res/values-en-rXZ/strings_config.xml
- app/src/debug/res/values-en-rXZ/strings_control.xml
- app/src/debug/res/values-en-rXZ/strings_design.xml
- app/src/debug/res/values-en-rXZ/strings_engine.xml
- app/src/debug/res/values-en-rXZ/strings_presence.xml
- app/src/debug/res/values-en-rXZ/strings_privacy.xml
- app/src/debug/res/values-en-rXZ/strings_robot.xml
- app/src/debug/res/values-en-rXZ/strings_routines.xml
- app/src/debug/res/values-en-rXZ/strings_stream.xml
- app/src/debug/res/values-en-rXZ/strings_surface.xml
- app/src/debug/res/values-en/strings.xml
- app/src/debug/res/values-en/strings_chat.xml
- app/src/debug/res/values-en/strings_config.xml
- app/src/debug/res/values-en/strings_control.xml
- app/src/debug/res/values-en/strings_design.xml
- app/src/debug/res/values-en/strings_engine.xml
- app/src/debug/res/values-en/strings_presence.xml
- app/src/debug/res/values-en/strings_privacy.xml
- app/src/debug/res/values-en/strings_robot.xml
- app/src/debug/res/values-en/strings_routines.xml
- app/src/debug/res/values-en/strings_stream.xml
- app/src/debug/res/values-en/strings_surface.xml
- tools/strings-check.ps1
- tools/make-test-language.ps1
- tools/strings-kinds.txt
- tools/strings-check.baseline.json
- app/src/test/java/dev/pocketagent/app/text/ResXml.kt
- app/src/test/java/dev/pocketagent/app/text/TestTexts.kt
- app/src/test/java/dev/pocketagent/app/text/TextResolverTest.kt
- app/src/test/java/dev/pocketagent/app/text/PluralRulesTest.kt
- app/src/test/java/dev/pocketagent/app/text/MirroringTest.kt
- app/src/test/java/dev/pocketagent/app/text/ProtectedTextsTest.kt
- app/src/test/java/dev/pocketagent/app/text/ResourceParityTest.kt
- app/src/test/java/dev/pocketagent/app/text/EngineStringsTest.kt
- app/src/test/java/dev/pocketagent/app/text/EnumLabelsTest.kt
- app/src/test/java/dev/pocketagent/app/text/TestTextsTest.kt

**Deviations:**

- LanguagePack.kt (LanguagePack, EnglishCatalog, PackResult, PackProblem, LanguagePacks, PackTextSource), PackStore.kt and ResourceCatalog are not written (cut-first, 2.8; no Wave 0 consumer). Texts.install and Texts.reloadPacks work with no packs. Texts.describe() prints 'packs=off'. LanguagePackTest is not written either.
- EnumLabels holds 60 icon keys and 16 avatar keys (76 label keys), not 59 and 75: PixelIcon has 60 entries. EnumLabelsTest pins one key per enum entry.
- tr and fr files for strings_engine.xml are not written (TRANSLATE does them). ResourceParityTest and strings-check parity fail until TRANSLATE has run; they also fail for the other areas' new files.
- The ten plural conversions break the existing R.string call sites of those keys until WORDS converts them (see REQUESTS). No file of another owner was edited.
- strings-check.baseline.json is an interim file limited to the pre-wave strings files (warnings=996, 8 known length violations). INTEGRATE must rewrite it with -WriteBaseline.
- The check scripts have two extra internal behaviours: a missing baseline means no ratchet and no tolerated length violations, and -WriteBaseline also treats the current length violations as known for that run. The command lines of 3.2 are unchanged.
- make-test-language.ps1 pads a text with too few letters with x so each generated text is at least 1.4 times the English effective length (placeholders count 2). The source-hash covers the English strings files with CRLF normalised, an algorithm version (now 3) and the names the twin overlay defines.
- Review finding 2: the contract names only app/src/debug/res/values-en-rXZ/ as generated. The generator now also writes app/src/debug/res/values-en/ (a plain English copy, debug builds only, never in src/main). It leaves out translatable=false texts and the three names the twin overlay overrides (app_name, srf_ask_agiri, srf_tile_label), so the twin keeps its own label. The reviewer's 'verbatim copy' is therefore not complete by design. If the twin overlay gains a name, the hash changes and -Check reports it as stale.
- Review finding 1: tools/strings-check.ps1 also uses ordinal sorting everywhere (the finding asked for Read-Language only), so its output is identical on Windows PowerShell 5.1 and pwsh 7. strings-check.ps1 has no -SelfTest, so the file-order self-test case was added to make-test-language.ps1 only.

**Requests:**

- WORDS (recipe 3.8 and W11, call sites of the converted plurals): BotsScreen.kt:72 ctl_bots_count; ChatListScreen.kt:336 ui_unread_cd; BotShareUi.kt:212 ctl_bots_import_title; BotShareUi.kt:219 ctl_bots_imported via Texts.getQuantity(ctx, R.plurals.ctl_bots_imported, n, n); HomeScreen.kt:318 ui_home_unread; BotEditorScreen.kt:254 ctl_bot_chars; HermesDocs.kt:158 ctl_chars; NewChatSheet.kt:139 ui_new_selected; UsageSection.kt:127,144,211 ctl_usage_turns. Each becomes tn(R.plurals.k, n, n). ui_chat_members_count has no caller. Until these are converted the app does not compile.
- WORDS W10: add '@Before TestTexts.installEnglish()' (import dev.pocketagent.app.text.TestTexts) to ChatEngineTest, RuntimeManagerInitTest, HermesHomeTest and TerminalSessionTest, and '@After TestTexts.reset()' where a test would otherwise leak the installed source into other classes.
- INTEGRATE: run 'powershell -NoProfile -ExecutionPolicy Bypass -File tools\make-test-language.ps1' (no -Check) after every English strings edit and after TRANSLATE. It now regenerates both app/src/debug/res/values-en-rXZ/ and app/src/debug/res/values-en/. Commit both folders. The committed copies match today's English files and the twin overlay.
- INTEGRATE: check on the twin before the first owner-debug install, with the in-app language picker on English and the system language on en-GB: Home must show 'Home', not '[Höméöé]', and the launcher label must stay 'Agirî Test' on the twin.
- INTEGRATE: run 'tools\strings-check.ps1 -WriteBaseline' once, after TRANSLATE and WORDS, to replace the interim strings-check.baseline.json. The wave's own new length errors need an owner decision first: rbt_practice_on (kind widget, 47 characters, room 14) and rbt_practice_left (15 characters, room 14) in strings_robot.xml; cfg_diag_guest_title (33 characters, kind title, room 28) in strings_config.xml; cfg_priv_web_sub (228 characters, kind row, room 40) in strings_privacy.xml. Fix with a different '| kind:' (body or pet) in the note, or with shorter text.
- INTEGRATE: strings-check.ps1 also warns about unused keys (the 76 eng_ keys and 20 eng_rt/eng_tts keys until WORDS runs, plus chat_needs_setup, ui_chats_pinned_cd, ui_chat_back, ui_chat_members_count, ui_attach_preview, ui_cmd_skills_header). Run -FailOnUnused once after WORDS.
- TRANSLATE: tr and fr for strings_engine.xml (76 label keys, the eng_rt and eng_tts keys, the rest) and for the two new keys in strings_design.xml (design_note_dismiss, design_ember_active); keep brackets, MEMORY.md, USER.md, finish_reason, error_code, HTTP 401, proot. The plural conversions of the ten keys are already in all three languages.

**Not done:**

- LanguagePack.kt, PackStore.kt, ResourceCatalog and LanguagePackTest (cut-first, no consumer); Texts.reloadPacks and install work with an empty pack set.
- tr and fr strings_engine.xml (TRANSLATE).
- W12 capitals (strings_routines.xml and section-header keys) deliberately untouched (Wave 1, lead).
- strings-check.ps1 does not itself check that values-en is fresh; make-test-language.ps1 -Check does that.

**Unverified:**

- Nothing was compiled: Texts.kt, TextsCompose.kt and all Kotlin tests were only re-read. They were not changed this session.
- The ordinal sort fix was run only on Windows PowerShell 5.1. pwsh 7 on Linux is not installed here. Ordinal sorting is culture-independent and gives the same order as today's Windows order, so the CI hash should match the committed headers, but it is untested on Linux.
- The Android resource-selection claim (a region-less values-en wins over values-en-rXZ for every English request except en-XZ) comes from the reviewer's reading of the matching rules. It has not been run on a device or emulator.
- Duplicate-resource behaviour of values-en in the debug source set against the main source set was not run through aapt2. The folders have different qualifiers, so no clash is expected.
