# Wave 0, stage 5: what INTEGRATE reported

Written from the workflow result on 2026-11-25. Three Sonnet agents in sequence; nothing was installed on the phone.

## compile

**Green:** True

**Summary:** Both debug variants assemble. The twin is dev.pocketagent.app.debug and the owner-debug build is dev.pocketagent.app. Both are debuggable and target SDK 28. Only four compile errors showed up, not hundreds. Nothing was installed and adb was never run. Unit tests were not run, as instructed.

**Evidence:**

```
Twin, from tools\local-build.ps1 (no flags), final lines:
APK: ...\app\build\outputs\apk\debug\app-debug.apk
PACKAGE: dev.pocketagent.app.debug
VERSION: 0.2.8-local1-test (801)
CERT-SHA256: eba87775bc0c6774f3555fbb5e5e021af684b0a84f1bcd330537047795ac34df
Total 55,2s, exit 0.
Gradle: BUILD SUCCESSFUL in 49s. The stretched test language check printed "stretched test language is up to date".

Owner-debug, from tools\local-build.ps1 -OwnerDebug, final lines:
PACKAGE: dev.pocketagent.app
VERSION: 0.2.8-local1 (1000801)
CERT-SHA256: eba87775bc0c6774f3555fbb5e5e021af684b0a84f1bcd330537047795ac34df
Total 35,0s, exit 0.

Independent check with build-tools 36.0.0 aapt2 dump badging:
- twin: package dev.pocketagent.app.debug, versionCode 801, versionName 0.2.8-local1-test, targetSdkVersion 28, application-debuggable present, label "Agirî Test".
- owner-debug: package dev.pocketagent.app, versionCode 1000801, versionName 0.2.8-local1, targetSdkVersion 28, application-debuggable present, label "Agirî".

The twin APK is also saved at dist\integrate\twin.apk and the owner-debug one at dist\integrate\owner-debug.apk. The Kotlin compile log has 0 lines starting with "e: " in the final run.
```

**Fixes:**

- app/build.gradle.kts: added `import java.util.Properties` and changed `java.util.Properties()` to `Properties()` in resolveOwnerKey. Inside a Gradle Kotlin script `java` resolves to the java extension, so the compiler reported an unresolved reference to `util` and a failed type inference on the `.use {}` block next to it.
- app/src/main/java/dev/pocketagent/app/ui/components/PocketSheet.kt: marked `bottomSafeInsets()` @Composable. WindowInsets.navigationBars and mandatorySystemGestures are @Composable getters in this Compose version. Its only call site is already inside a composable.
- app/src/main/java/dev/pocketagent/app/ui/components/SwipeRow.kt: removed `import androidx.compose.foundation.layout.matchParentSize`. matchParentSize is a BoxScope member, so the top-level import does not exist. The call at line 139 is inside a Box and still compiles.
- tools/local-build.ps1: Test-AdbReady returns $false at once when the environment variable AGIRI_NO_ADB=1 is set, so the build never asks the phone for its installed version code and assumes local build number 1. I added this so this step could not touch the phone. Without the variable the behaviour is unchanged, and no guard of the script or the phone tools was weakened.

**Registry deviations:**


**Still red:**


**Notes:**

- tools\local-build.ps1 and the rest of the new build tooling (build-ids.ps1, the make-test-language -Check call, the aapt2 checks, the CR check and the parts check) ran correctly the first time, so they needed no fixes. It still finds JDK 21 (Android Studio jbr) and the SDK (android-37.2, build-tools 36.0.0) the way the old script did.
- Version codes: the parts are phase0-8, so the default ciRun is 8. The twin is 801 and the owner-debug build is (10000+8)*100+1 = 1000801. AGIRI_NO_ADB=1 is why localSeq was 1 in both. The next steps should keep setting it, or the script will query the phone through adb.
- The shared output path app\build\outputs\apk\debug\app-debug.apk currently holds the owner-debug APK, because it was built last. The twin copy is dist\integrate\twin.apk. The script checks the package of whatever is at that path before any install.
- The only build warning noticed: `'fun srcDir(srcDir: Any): Any' is deprecated. Use directories mutable set instead` at app/build.gradle.kts line 271 (`sourceSets.getByName("debug").res.srcDir("src/twin/res")`). It is a warning and the twin overlay works (label "Agirî Test"), so I left it.
- Only the debug variants were built, as asked. The release build (R8, `-Release`) has not been compiled, so missing_rules.txt dontwarn lines, owner_baseline_bytes and owner_ceiling_bytes in tools/size-budget.txt, and the `releaseRequested` and `ownerKeyProblem` paths of build.gradle.kts still need that run.
- The build left `docs/waves/PROGRESS.md` modified in git status. I did not edit it, so someone else did, probably the lead. My own edits are the four files listed under fixes. dist\integrate\ holds my helper scripts and logs and is git-ignored.
- A Windows PowerShell 5.1 redirect (`*>`) of the script's output works for logging. Kotlin errors land wrapped at about 120 columns in dist\local-build.log.err. dist\integrate\errs.sh unwraps them, and dist\integrate\b.ps1 -Mode twin|owner-debug -Tag <n> runs a build and writes the error list to dist\integrate\e-<n>.txt.

## tests

**Green:** True

**Summary:** Unit tests are green in both variants. Twin and owner-debug each ran 793 tests, with 4 failures and 3 skipped. All 4 failures are the known Windows symlink tests, and both runs exit 2. The first run had 3 unexpected failures. Two were first-run data (palette ids, golden) and one was a wrong test regex. All three are fixed. The pixel-font golden test and the scene parity test (exact equality) both pass. Nothing was installed, adb was never run, and no git command changed the tree or index.

**Evidence:**

```
First run, twin: "tests: 793, failed: 7 (4 expected on Windows, 3 unexpected), skipped: 3". The 3 unexpected failures were:
1. ScenarioFilesTest > opVocabularyMatchesTheScript. Message: "tools/phone-lib.ps1 and ScenarioFilesTest must list the same ops expected:<[... scale, a11y, restore ...]> but was:<[... scale, restore ...]>".
2. PaletteContrastTest > outlinesAndAccentAtThree. Message: "New contrast failures (fix the colour, or paste into KNOWN_FAILURES): KAWA/dark/FLAME:outline-on-bg ..." (215 ids).
3. PixelFontGoldenTest > v1GlyphsUnchanged. Message: "pixelfont-v1.golden is missing. Make it once from the commit before Wave 0 ...".
Golden creation, in the scratch worktree copy of commit 344829f with only PixelFontGoldenTest.kt added and PIXELFONT_GOLDEN_WRITE=1: "gradle exit 0", "BUILD SUCCESSFUL in 28s", file pixelfont-v1.golden written (135 lines, 9999 bytes).
Final twin run (tools\local-build.ps1 -Test): exit 2, "tests: 793, failed: 4 (4 expected on Windows, 0 unexpected), skipped: 3 (variant: twin ...)".
Final owner-debug run (-Test -OwnerDebug): exit 2, "tests: 793, failed: 4 (4 expected on Windows, 0 unexpected), skipped: 3 (variant: owner-debug ...)".
Per-suite counts from the result XML, all with failures=0 and errors=0:
- SceneRendererParityTest: 3 tests
- PixelFontGoldenTest: 1
- PaletteContrastTest: 8
- ScenarioFilesTest: 13
- ProguardRulesTest: 3
- LocalBuildSafetyTest: 6
- ResourceParityTest: 1
None of these seven suites skipped, so the repo-reading tests really ran (the "tests that read repo files" point in stage1-resolution.md, list "For INTEGRATE", item 6).
```

**Fixes:**

- app/src/test/java/dev/pocketagent/app/ScenarioFilesTest.kt: the test was wrong, not the script. Both ops regexes were '([A-Za-z]+)' and could not match the op 'a11y', which is in PaOps in tools/phone-lib.ps1. Changed both to '([A-Za-z0-9]+)'. No expectation was weakened: the vocabulary set still has to equal the script's list exactly.
- app/src/test/java/dev/pocketagent/app/ui/theme/PaletteContrastTest.kt: filled KNOWN_FAILURES with the 215 ids the first run printed: 88 outline-on-bg, 88 outline-on-surface, 39 accent-on-bg. The KDoc comment now says the list was filled in. No colour was changed.
- app/src/test/resources/pixelfont-v1.golden (new, untracked): made once from commit 344829f (pre-wave code) in the scratch worktree copy, with PixelFontGoldenTest.kt as the only added file and PIXELFONT_GOLDEN_WRITE=1. The golden test now passes against the new font code without editing the golden. No other app file was changed in this step.

**Registry deviations:**


**Still red:**


**Notes:**

- The 4 expected Windows symlink failures are FileAndUserStateTest > deletesDirectoriesWithoutOwnerWriteAndNeverFollowsSymlinks, TarExtractorTest > preservesSymlinksHardlinksModesAndMtime, TarExtractorTest > worksWhenDestinationSitsUnderASymlinkedDirectory and TarExtractorTest > stillRejectsEscapesWithSymlinkedDestination. All four fail with java.nio.file.FileSystemException (Turkish message "Gereken ayrıcalık ..."), which is the missing symlink privilege.
- The 3 skipped tests are all Assume skips and unrelated to the wave. GuestCodeScanTest > symlinkIsListedNotFollowed (no symlink privilege), PracticeModelTest > installIntoMakesTheStartScriptRunnableByTheOwner (Windows has no execute bit) and RealRootfsExtractionTest > matchesGnuTar (POCKETAGENT_ROOTFS_ARCHIVE not set).
- SceneRendererParityTest (3 tests) passed with exact equality against LegacyHearthReference.kt. That file was not edited. The phone check with Animations Off stays as the second witness.
- app/src/test/resources/ is a new untracked directory holding only pixelfont-v1.golden. The lead must commit it. The golden file uses LF line endings. The test reads it with text.lines(), so CRLF would also work.
- The scratch worktree dist/wt-golden (detached at 344829f, with PixelFontGoldenTest.kt added, untracked there) is still registered in git worktree list. dist/wt was not touched. The lead may remove dist/wt-golden with `git worktree remove --force dist/wt-golden`. I did not, to avoid another tree-changing git command. The temp copy %TEMP%\agiri-golden can also be deleted.
- The files modified before my step are still modified and were not edited by me: app/build.gradle.kts, PocketSheet.kt, SwipeRow.kt, docs/waves/PROGRESS.md and tools/local-build.ps1.
- Helper scripts and logs are in dist\integrate\ (git-ignored). t.ps1 runs -Test or -Test -OwnerDebug, golden.ps1 made the golden, palette-ids.txt holds the 215 ids, and test-1.txt through test-3.txt are the run outputs. Test XML results are in %TEMP%\agiri-test-copy\app\build\test-results\testDebugUnitTest.
- Not run in this step, as instructed: the release (R8) build, the lint, strings and the other script checks of contract 8.4 step 3, and anything on the phone.

## checks

**Green:** True

**Summary:** The optimised (R8) build is green and every check of contract 8.4 steps 1, 3 and 4 passes, except the checks that need Linux pwsh 7, zip or the real smoke job. Nothing was installed, adb was never run and no git command changed the tree. The final twin build, owner-debug build and -Test (both variants) were run again after my edits and are green. The tests exit 2 in both variants with 793 tests, 4 expected Windows symlink failures, 0 unexpected and 3 Assume skips. I changed no Kotlin. I changed 4 note comments in English strings files, regenerated the stretched language, and wrote the size budget, the lint baseline and the strings baseline. I did not add -FailOnUnused to build.yml because it is not clean.

**Evidence:**

```
RELEASE (tools\local-build.ps1 -Release, AGIRI_NO_ADB=1, run twice, second after the string edits): "APK: ...\app\build\outputs\apk\release\app-release.apk / PACKAGE: dev.pocketagent.app / VERSION: 0.2.8-local1 (1000801) / CERT-SHA256: eba87775bc0c6774f3555fbb5e5e021af684b0a84f1bcd330537047795ac34df / size 229.8 MB / Total 15,1s", exit 0. R8 (:app:minifyReleaseWithR8) and resource shrinking ran; no missing_rules.txt was produced, so proguard-rules.pro needed no new -dontwarn lines. gradle.properties stays at -Xmx3g.
verify-apk.sh: release "package: dev.pocketagent.app / version: 0.2.8-local1 (1000801) / debuggable: 0 / targetSdk: 28 / verify-apk: ... ok". Twin "package: dev.pocketagent.app.debug / (801) / debuggable: 1 ... ok". Owner-debug "package: dev.pocketagent.app / (1000801) / debuggable: 1 ... ok".
apk-size.sh (release): apk_bytes=240969904 rootfs_bytes=227984352 app_bytes=12985552 dex_bytes=1949250 native_bytes=9628983. After writing tools/size-budget.txt (baseline 12985552, ceiling 14034128 = baseline + 1048576): "ceiling_bytes=14034128 verdict=ok". Twin: app_bytes=27845256, never gated.
lint-ui.ps1 -Init then -Check: "rawTween 0, toast 0, roundedCorner 19, literalDuration 10, literalDp 636, directText 0, leftRight 0, caseChange 26, hapticDirect 0, sheetDirect 0 / lint-ui: ok" (DesignGallery.kt counted in the baseline: 13 literalDp and 2 in another rule; no exemption added).
strings-check.ps1: before: errors=4 (the four length errors). After kind fix and -WriteBaseline: "strings-check: languages=en,fr,tr keys=1103 errors=0 warnings=939 (baseline 939)", exit 0. -FailOnUnused: "errors=6 warnings=933", exit 1 (chat_needs_setup, ui_chats_pinned_cd, ui_chat_back, ui_chat_members_count, ui_attach_preview, ui_cmd_skills_header), so the flag was NOT added to build.yml.
make-test-language.ps1 -Check: "up to date", exit 0 (after regeneration).
craft_art.py --check: ok fire.pxl, scene.pxl, appicon.pxl, exit 0. mock_llm.py --selftest: "PRACTICE SELFTEST OK". py_compile ok. -SelfTest of phone-test, phone-perf and lint-ui: SELFTEST OK, exit 0 each. sh -n / bash -n ok on all 11 *.sh (CI's own rule: bash shebang uses bash -n). sh runtime/test-launcher.sh sh: "LAUNCHER TEST PASSED". test-apk-size.sh: "SKIPPED (zip or unzip is not installed)" with the real zip missing; with a Python stand-in for zip on PATH: 20 ok lines, "test-apk-size: all passed".
FINAL (all after my edits): twin build "APK ... PACKAGE: dev.pocketagent.app.debug VERSION: 0.2.8-local1-test (801) ... Total 20,0s" exit 0. Owner-debug build "PACKAGE: dev.pocketagent.app VERSION: 0.2.8-local1 (1000801) ... Total 19,9s" exit 0. -Test twin: "tests: 793, failed: 4 (4 expected on Windows, 0 unexpected), skipped: 3 (variant: twin ...)" exit 2. -Test -OwnerDebug: "tests: 793, failed: 4 (4 expected on Windows, 0 unexpected), skipped: 3 (variant: owner-debug ...)" exit 2.
SWEEPS Part 3 results are in the notes. build.yml: read top to bottom, no change needed (see notes).
```

**Fixes:**

- app/src/main/res/values/strings_config.xml: cfg_diag_guest_title note kind title to body (the note says about 35 characters, the room of a title is 28; this only changes what strings-check expects, not the screen). The text is unchanged.
- app/src/main/res/values/strings_privacy.xml: cfg_priv_web_sub note kind row to body (the note itself says up to 5 lines, 228 characters). The text is unchanged.
- app/src/main/res/values/strings_robot.xml: rbt_practice_on and rbt_practice_left note kinds widget to body (the notes say a 2-line banner and about 20 characters, the widget room is 14). The texts are unchanged. Together these three files settle the four length errors that stopped CI's strings step.
- app/src/debug/res/values-en-rXZ/*.xml and app/src/debug/res/values-en/*.xml (12 + 12 files): regenerated by tools\make-test-language.ps1; only the source-hash header line changed, because the hash covers the note comments.
- tools/size-budget.txt: owner_baseline_bytes=12985552 and owner_ceiling_bytes=14034128 (baseline + 1048576) from the first green R8 build.
- tools/lint-ui-baseline.json (new, untracked): written by lint-ui.ps1 -Init.
- tools/strings-check.baseline.json: rewritten by strings-check.ps1 -WriteBaseline (warnings 939, 8 known length violations).
- Not changed, nothing needed: app/proguard-rules.pro (R8 produced no missing_rules.txt), gradle.properties, .github/workflows/build.yml, every Kotlin file, LegacyHearthReference.kt, dist/wt.

**Registry deviations:**


**Still red:**


**Notes:**

- COMMIT LIST for the lead (untracked or modified by this integrate step): tools/lint-ui-baseline.json and app/src/test/resources/pixelfont-v1.golden (new; CI's lint step exits 2 and the PixelFont test fails without them), tools/size-budget.txt, tools/strings-check.baseline.json, the 3 English strings files above, the 24 regenerated language files. docs/waves/w0/final-review.md appeared in the tree during my step; it is not mine. The git worktree dist/wt-golden from the earlier step is still registered.
- SWEEPS Part 3 counts that differ from what Part 3 expects: S-6 and W-7 print empty counts through ripgrep's $ anchor because the source files have CRLF (they pass with --crlf or a Python equivalent: every file that calls ConfirmDialog/ActionSheet/SheetAction or t/tn/Texts has its import, and no import is unused). S-12 lists 12 files, 2 more than the 10 expected: ui/settings/PracticeBanner.kt and ui/chat/ToolOutputSheet.kt (files of ROBOT and STREAM, not SWEEP's). S-9 prints exactly 12 (D1 = no, matches). S-10 prints exactly the 5 holds. W-3: 1 line, ui/text/Texts.kt:244 (exempt folder). W-6: 11 lines instead of 7, because ui/theme/Appearance.kt has 7 pref reads (haptics added) not 6, AttachmentStore.kt:200 is 1, and 3 lines are in ui/text (TextSource.kt KDoc, Texts.kt:244, ResourceTextSource.kt:11). W-9: 2 KDoc comment lines in data/ChatEngine.kt (91, 374), not texts. W-12: 1 KDoc line, hermes/HermesHome.kt:62. W-13: 1 line, ui/components/README.md:43 (example snippet). W-18: 7 declarations in files that also call t(: HomeParts.kt:71, ChatCommon.kt:258, ChatMessages.kt:435, ChatMessageList.kt:121, TypingIndicator.kt:56, Skeleton.kt:65, BackupSection.kt:87 (catch t); none in the four files named in 2.7, and the compile is green. W-19: 5 files instead of 4 (TestTextsTest.kt as well). Counts that match: S-1 0, S-2 0, S-3 1, S-4 2 (both in ui/components), S-5 0, S-7 0, S-8 0, S-11 as listed (plus the six HERMES_TAB constants), W-1/W-1b 0, W-2 0, W-4 0, W-5 0, W-8 0, W-10 0, W-11 0, W-14 0, W-15 0, W-16 0, W-17 0.
- build.yml read as CI runs it: every called script exists; shell scripts are called through sh or have mode 100755 in the index (build-proot.sh, build-rootfs.sh, smoke-test.sh); all .sh are LF; version-code expressions in the verify step match app/build.gradle.kts ((10000+run)*100 owner, run*100 twin, localSeq 0); artifact names Agiri-RUN.apk, Agiri-RUN-test.apk, SHA256SUMS.txt, size-report.txt, rootfs-arm64.info and proot-build.info are consistent between the staging, the sign step, the checksum step and the release job; the secret-less path (sign-owner.sh copies, signing=committed, Gradle signs with the committed key) needs no secret; the tools' default paths use Join-Path or forward slashes (a scan of lint-ui, strings-check, make-test-language, phone-test, phone-perf, phone-lib and build-ids found backslashes only in messages and comments, and LOCALAPPDATA is guarded). Linux pwsh 7 itself could not be run here: the first CI run is the proof for the five .ps1 steps.
- smoke test read against rootfs/smoke/mock_llm.py, runtime/practice-start.sh and the fixtures: ports (8642, mock 18080), the guest path /opt/agiri/practice, the python search path (same venv glob as phase 1), the 'practice model listening on N' line that start.sh greps, the requests.jsonl fields (POST, n_msgs, heads), the title phrase, and the hello-from-tool command all agree. The /v1/toolsets answer shape ({data:[{name,enabled}]}) matches Hermes's api_server.py, all ten allowed names are in CONFIGURABLE_TOOLSETS, and _RECENTLY_SHIPPED_TOOLSETS is empty. Unproven risk: Hermes adds every plugin toolset that is not default-off, so if the image ships a plugin toolset the exact-equality assertion would report it as extra; only the smoke job can show this.
- Known pre-existing unused keys (the reason -FailOnUnused is not in build.yml): chat_needs_setup, ui_chats_pinned_cd, ui_chat_back, ui_chat_members_count, ui_attach_preview, ui_cmd_skills_header. The eng_ and rt keys are all used now. They need deleting from the three languages by the lead or a later wave, then the flag can go in.
- R8 first build: no -dontwarn lines were required and -Xmx was not raised. The R8 build has only been compiled and structurally verified (package, not debuggable, targetSdk 28, rootfs Stored, signature). S2 (Hermes start, neural voice, backup and restore, icon switch under R8) still needs the twin on the phone. The R8 output keeps names (-dontobfuscate), so the sherpa-onnx JNI keep rule is the first thing to look at if speech breaks.
- Build-script notes: the debug builds still print the warning that srcDir(...) in app/build.gradle.kts line 271 is deprecated (it works). The shared APK path app\build\outputs\apk\debug\app-debug.apk currently holds the owner-debug build, and ...\release\app-release.apk the R8 build; copies are in dist\integrate\ (twin-final.apk, owner-debug-final.apk, release-final.apk, git-ignored). The helper scripts and logs there (b.ps1, t.ps1, rel.ps1, final.ps1, sweeps.sh, chk.py, shim\apksigner, shim2\zip) are git-ignored; AGIRI_NO_ADB=1 must stay set or local-build queries the phone. I removed rootfs/smoke/__pycache__ (made by my py_compile) and the extracted Hermes source in dist\integrate.
