# Wave 0, stages 2 to 4: what the sweeps and the translation reported

Written from the workflow result on 2026-11-25. Nothing was compiled in these stages.

## RESOLVE

**Summary:** I made one small cross-area fix: the chat top bar's title shared-bounds hookup that STREAM requested from DESIGN. Nothing was compiled or run on a phone. The full table of every request and deviation, and the lists for each later stage, are in docs/waves/w0/stage1-resolution.md.

**The fix.** PocketTopBar gets a last parameter `titleModifier: Modifier = Modifier`, applied to the title. ChatTopArea passes `Modifier.navSharedBounds(SharedKeys.chatTitle(convId))`. It is a no-op until SWEEP gives the chat-list row the same key (contract 5.9 R9).

**Other requests.** Every other request and deviation was checked against the real code and needed nothing, or is forwarded:
- **Verified, nothing to change:** the manifest has no literal package and has the labels BUILD asked for. runtime/practice-start.sh exists with LF. Every script CI calls exists. PracticeBanner, CrashReportOffer, PetNoteHost and the DiagnosticsExtras call are placed. DebugLinks seeds off the main thread (IO scope). The `practice=` and `I/State:` lines the robot reads are written. CutProbe is gated on TEST_HOOKS.
- **Deferred:** the lint-ui exemption for DesignGallery (a ratchet-scope judgement, baseline not yet written) and the four strings-check length errors (owner decision).
- **Forwarded:** the rest, to SWEEP, WORDS, TRANSLATE, INTEGRATE or the lead.

**Registry check (3.1 to 3.14).** Every row matches its provider and consumers. Specifically:
- **Symbols:** all 1270 `dev.pocketagent.app` imports resolve to declared names. No capitalised call or project function is used without an import. All `R.string`, `R.plurals` and `@string/` keys exist, except the nine plural call sites below.
- **Tags:** every scenario tag is in the contract table; the 3.12 tags that no code writes yet belong to SWEEP.
- **Structure:** no brace or comment-nesting problems in any Kotlin file.

**Scripts and resource files.** The scripts that ran (Windows PowerShell 5.1 only) came out as follows:
- Pass: the three `-SelfTest`s, `make-test-language -Check`, `mock_llm --selftest` and `craft_art --check`.
- Fail: `strings-check` exits 1 with 18 errors (14 missing tr/fr parity, 4 length). `lint-ui -Check` exits 2 until `-Init`.
- All 115 res xml files parse. All ps1 files parse and are ASCII, and `sh -n` passes on every shell script.

**Compile break.** The tree will not compile until the nine plural call sites (TEXT's plural conversion) are converted by WORDS.

**Files:**

- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\components\PocketTopBar.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\chat\ChatTopArea.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\docs\waves\w0\stage1-resolution.md

**Left as it was:**

- lint-ui exemption of ui/components/DesignGallery.kt (DESIGN request): a judgement about what the ratchet counts and the baseline does not exist yet; INTEGRATE or the lead decides before lint-ui -Init
- the four strings-check length errors (rbt_practice_on, rbt_practice_left, cfg_diag_guest_title, cfg_priv_web_sub): choosing a different '| kind:' note or shorter text is an owner decision; recommendation in the resolution file
- pixelfont-v1.golden and PaletteContrastTest.KNOWN_FAILURES: need a Gradle run, INTEGRATE
- AGENTS.md, CI secrets, .gitattributes *.ps1 option: not mine (lead)
- all later-stage edits (SWEEP, WORDS, TRANSLATE, INTEGRATE) including the nine plural call sites

**For the integration agent:**

- SWEEP: tags nobody has written yet, in RobotTags: HOME_STATE, HOME_START (S3); CHATS_NEW, chatsRow(i), CHAT_INPUT, CHAT_SEND, CHAT_STOP, CHAT_ATTACH, CHAT_MIC, CHAT_APPROVAL_ONCE/SESSION/ALWAYS/DENY (S2); CHAT_FORGED_CHAT (S1, AttachmentUi); HERMES_TAB_* (six), HERMES_DOC_EDITOR/SAVE, HERMES_SKILL_NEW/NAME/CREATE/DELETE, hermesSkillRow(name), TERMINAL_INPUT/RUN/OUTPUT (S4); settingsSection(key), USAGE_TODAY, USAGE_BUDGET_LIMIT/WARN/BLOCK, BEHAVIOUR_IDLE_MINUTES, BACKUP_SAVE/RESTORE/RESULT (S5); botsRow(i), BOTS_EXPORT, BOTS_IMPORT (S3, BotCard gets a last modifier parameter)
- SWEEP: SharedKeys.chatAvatar/chatTitle on the ChatListScreen row and the bot keys on the bot card (contract 5.9 R9); at the checkpoint only ChatTopArea uses SharedKeys, so shared elements have no partner yet
- SWEEP: ConfirmDialog/ActionSheet/SheetAction of ui.chat.ChatCommon and ui.settings.CtlUi are still declared; ChatDialogs.kt and RuntimeSection.kt import the ui.components ones explicitly (explicit import wins); HermesTabs, HermesScreen, HermesDocs, BotEditorScreen, BotShareUi import ui.settings.ConfirmDialog by name and ChatListScreen uses the ui.chat ActionSheet/SheetAction through its package, so they need the ui.components imports when S1 and S5 delete the old ones
- SWEEP: toast(ctx, message) in CtlUi.kt and showToast(ctx, message) in RoutineUiParts.kt are still the old two-parameter forms (3.9 wants a kind parameter and PetNotes.show); DESIGN dialogs and sheets now lay out inside the system bars, so converted call sites must not add their own bar insets; ModalBottomSheet( remains in ChatCommon, ChatQuickBar, SkillPickerSheet, NewChatSheet, AttachmentUi
- WORDS: the tree does not compile until these nine call sites use the plural form tn(R.plurals.k, n, n) / Texts.getQuantity(ctx, R.plurals.k, n, n): ctl_bot_chars (BotEditorScreen), ctl_bots_count (BotsScreen), ctl_bots_import_title and ctl_bots_imported (BotShareUi, the second via ctx.getString), ctl_chars (HermesDocs), ctl_usage_turns (UsageSection, three sites), ui_home_unread (HomeScreen), ui_new_selected (NewChatSheet), ui_unread_cd (ChatListScreen)
- WORDS: W1 STREAM's eight files (ToolOutputSheet NumberFormat with currentLocale may become appLocale); W4 AppRoot.kt five stringResource calls plus PocketTopBar and TypingIndicator; W6 DiagnosticsExtras (pluralStringResource becomes tn), PracticeBanner (rbt_practice_left plural), PrivacyRows; W10 add @Before TestTexts.installEnglish() to ChatEngineTest, RuntimeManagerInitTest, HermesHomeTest, TerminalSessionTest
- TRANSLATE: no tr/fr yet for strings_config.xml (27 keys incl. the plural cfg_diag_guest_found and cfg_crash_copy_failed), strings_privacy.xml (3), strings_robot.xml (8 incl. the plural rbt_practice_left), strings_stream.xml (5 incl. the plural stm_tool_sheet_truncated), strings_engine.xml (132 keys), and design_note_dismiss and design_ember_active in strings_design.xml; regenerate values-en-rXZ and values-en afterwards with make-test-language.ps1
- INTEGRATE: compile first; confirm the AGP 9.5.2 spellings in app/build.gradle.kts (variant.outputs versionCode.set, sourceSets srcDir src/twin/res, manifestPlaceholders, script-level var ownerKeyProblem, tasks.matching doFirst, startParameter.taskNames), ResizeMode.scaleToBounds() and the named sharedContentState/animatedVisibilityScope arguments in NavScopes.kt, Configuration.SCREENLAYOUT_LAYOUTDIR_*, SceneClock lifecycle imports, SpriteSource fun interface, LegacyHearthReference private nested classes, PocketSheetScope as ColumnScope in ToolOutputSheet, VisibilityThreshold, PracticeBanner early return before produceState
- INTEGRATE: baselines and generated files: pixelfont-v1.golden (scratch worktree of the pre-wave commit, PIXELFONT_GOLDEN_WRITE=1), PaletteContrastTest.KNOWN_FAILURES, lint-ui.ps1 -Init then -Check (exits 2 until then; decide the DesignGallery exemption first), strings-check.ps1 -WriteBaseline after TRANSLATE, WORDS and the four length decisions, leak-allow.txt after the first stretched-language sweep, size-budget.txt numbers after the first release build, proguard dontwarn lines from missing_rules.txt
- INTEGRATE: CI first run needs pwsh 7 on Linux for strings-check, make-test-language -Check (ordinal hash), lint-ui, phone-test/phone-perf -SelfTest (only run under Windows PowerShell 5.1 here); smoke job must prove allow_lazy_installs:false and the /v1/toolsets set; check on the twin with English plus system en-GB that Home says Home and the twin label stays Agiri Test; tests that Assume repo files must really run, not be skipped, under -Test
- lead: strings-check fails on four length errors until decided: rbt_practice_on (47 chars, kind widget room 14, note says up to 2 lines: probably kind body), rbt_practice_left (15 chars, widget room 14), cfg_priv_web_sub (228 chars, row room 40, note says up to 5 lines: probably body), cfg_diag_guest_title (33 chars, title room 28: shorter text or body)
- lead: AGENTS.md must record the three variants, version scheme (owner (10000+run)*100+seq, twin run*100+seq), local-build.ps1 switches, Anthropic mode omitting security.allow_lazy_installs (ruling 11.8), B21 half closed (terminal tool can still run hermes cron, Wave 3 gate); CI secret names AGIRI_KEYSTORE_B64/PASSWORD, AGIRI_KEY_ALIAS/PASSWORD, optional AGIRI_LINEAGE_B64, never set the secrets without the lineage; optional *.ps1 eol rule in .gitattributes
- lead: twin-only scenario files and perf P2/P3 are not written (second pass after the compile and the twin); phone proof still open for NAV saved-state size and shared elements, STAGE with Animations Off and gfxinfo, DESIGN dialog/sheet insets with the keyboard, CONFIG practice restart and exit 70/71

## SWEEP S1

**Summary:** Batch S1 done in all five files. Nothing was compiled; I re-read the diffs as a compiler would. The toast, ember loop, sheet conversions, ConfirmDialog/ActionSheet deletions, the forged-bot tag and the allowed dp/corner tokens are in. RoundedCornerShape( was left alone (5.9).

**Files:**

- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\chat\ChatCommon.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\chat\AttachmentUi.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\chat\NewChatSheet.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\chat\SkillPickerSheet.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\chat\ChatQuickBar.kt

**Left as it was:**

- ChatCommon.kt: no dp-to-Space edits, because it is not one of the ten R6 files.
- NewChatSheet.kt: no dp-to-Space edits, because it is not one of the ten R6 files.
- SkillPickerSheet.kt: no dp-to-Space edits, because it is not one of the ten R6 files. Only the corner = 1 to Corner.chip edit (R6.c) was made.
- AttachmentUi.kt: two RoundedCornerShape(4.dp) in DocumentBadge, left per contract 5.9.
- ChatQuickBar.kt: four RoundedCornerShape(16.dp) in GroupParticipantBar, left per contract 5.9.
- SkillPickerSheet.kt: two RoundedCornerShape(4.dp), left per contract 5.9.
- R6.a dp literals with no token or outside the five forms, reported as LEFT dp: AttachmentUi.kt has 14, 20, 52, 22, 24, 48, 88, 10, 6, 3, 2, 1, 18, 42 and 5 (padding(10.dp), vertical = 6.dp, size args, border widths); ChatQuickBar.kt has 10, 6, 3, 5, 22, 14, 12 (size), 20, 18, 560 (heightIn max) and 1.5; the 10.dp spacedBy in ChatQuickBar.kt is also left.
- No leftover Toast, tween or haptic sites exist in these five files (only ChatCommon had a toast and a tween). SlashPalette and the other R4/R5 sites belong to S2/S3.

**For the integration agent:**

- The scratchpad folder is shared with the other SWEEP batches. Another agent overwrote my helper script `ed.py` there with its own version mid-run. I switched to a private subfolder, and the diffs of all five files look correct afterwards. Other batches may have hit the same clash.
- ChatListScreen.kt (S2) now needs `import dev.pocketagent.app.ui.components.ConfirmDialog`, `ActionSheet` and `SheetAction`, because the chat-package copies are deleted from ChatCommon.kt. S2's git diff shows it is already editing that file.
- Import placement: in AttachmentUi.kt I added `ui.RobotTags` and `ui.components.PocketSheet` next to the existing imports rather than strictly after the last one. The order is not alphabetical, which is harmless.
- Unused imports remain where the recipe did not name them (for example `ExperimentalMaterial3Api` and the `@OptIn` lines, and `clickable`, `Role` and `heightIn` in ChatCommon.kt). They produce warnings only.
- ChatCommon.kt now relies on `Loop`, `motion` and `PetNotes`/`PetNoteKind`, all imported. `bottomSafeInsets()` is kept for AppRoot.

## SWEEP S2

**Summary:** Batch S2 is done. Nothing was compiled; I re-read the full diff as my own compiler. Three files changed: ChatListScreen.kt, ChatInput.kt, SlashPalette.kt. ChatSearch.kt, StarredScreen.kt, ChatLogic.kt and SyntaxHighlight.kt have no recipe site and got 0 edits. Details per file:
- ChatListScreen.kt (R2, R4, R6.a, R7, R9): the ConfirmDialog call gets `closeOnConfirm = true, danger = true`. Imports for ui.components.ActionSheet, SheetAction and ConfirmDialog were added. LocalHapticFeedback is replaced by `rememberHaptics()` with `HapticCue.HOLD`. Both `PixelButton`s get the `CHATS_NEW` tag. The three `items(` blocks became `itemsIndexed(` with `RobotTags.chatsRow(i)`, and `pinned.size + i` for the rest block. R9: `navSharedElement(SharedKeys.chatAvatar(conv.id))` is on the `AvatarStack`, and `navSharedBounds(SharedKeys.chatTitle(conv.id))` is chained after `weight(1f)` on the title `Text`. dp literals became Space tokens in the allowed forms.
- ChatInput.kt (R5, R6.a, R7): `SparkBurst` now uses `motion.scene<Float>()`, and the `tween` and `LinearOutSlowInEasing` imports are removed. Tags added: `CHAT_INPUT`, `CHAT_SEND`, `CHAT_STOP`, `CHAT_ATTACH`, `CHAT_MIC`, and `"chat.approval.$ch"` on the approval buttons. dp literals became Space tokens.
- SlashPalette.kt (R5, R6.a): the overlay animation is now `motion.custom<Float>(Flourish.SHOWER, LinearEasing)`, and the `tween` import is removed. dp literals became Space tokens.
- There is no Toast, haptic, ActionSheet or ConfirmDialog use and no tween in ChatSearch.kt or StarredScreen.kt, and R6 does not list them.

**Files:**

- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\chat\ChatListScreen.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\chat\ChatInput.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\chat\SlashPalette.kt

**Left as it was:**

- ChatListScreen.kt: delay(250) in produceState. A poll time with no token, as the recipe says.
- ChatListScreen.kt: R6.b skipped per contract 5.9, so no RoundedCornerShape( was replaced. ChatInput.kt keeps 2 sites: `.clip(RoundedCornerShape(16.dp)).background(c.surfaceHigh)` and `.clip(RoundedCornerShape(4.dp)).background(c.codeBg)`. The `import RoundedCornerShape` stays.
- ChatListScreen.kt dp left: spacedBy(6.dp), PaddingValues(bottom = 88.dp), heightIn(min = 68.dp and 60.dp), AvatarStack 48/40.dp, width(14.dp), size = 14.dp, defaultMinSize 20.dp, padding(horizontal = 6.dp, vertical = 1.dp), vertical = 10.dp. All have no token or are not an allowed form.
- ChatInput.kt dp left: Box height(2.dp) (not a Spacer), border(2.dp), Spacer width(6.dp), vertical = 6.dp (twice), sizes 48/24/18/20/16.dp, the Canvas dp values of SparkBurst. All have no token or are not an allowed form.
- SlashPalette.kt dp left: heightIn(max = 220.dp), border(2.dp)/(1.dp), spacedBy(10.dp), horizontal = 6.dp, sizes 18/16.dp, 5.dp/14.dp/20.dp inside the Canvas. All have no token or are not an allowed form.
- ChatInput.kt: the approval tag is the literal `"chat.approval.$ch"` as recipe R7 says, not the four RobotTags constants. The result is the same strings for once, session, always and deny.
- ChatSearch.kt and StarredScreen.kt: 0 edits. No Toast, haptic, tween, dialog or sheet site, and not among the ten R6 files, so no dp replacement.

**For the integration agent:**

- R9 overlap with S3: `ConversationRow` is internal and HomeScreen.kt also calls it (the recent-conversations rows). The avatar and title sit inside `ConversationRow`, so my edit already gives the Home rows the same `chatAvatar` and `chatTitle` keys. S3 cannot and must not add them again from outside. Different screens, so a key still appears once per screen.
- ChatListScreen.kt now imports `ui.components.ActionSheet`, `SheetAction` and `ConfirmDialog` explicitly. Until S1 deletes the same-named `internal` copies in ChatCommon.kt, the explicit import wins over the same-package one, so there is no ambiguity.
- ChatInput.kt: motion.scene (480 ms, standard easing) replaces tween(520, LinearOutSlowInEasing), and the overlay now uses Flourish.SHOWER, as the recipe states. At REDUCED animation level the MotionScheme timing is capped at 150 ms, so the burst is shorter there than before. This is the recipe's own result.
- Imports were appended after the last existing import line without sorting, as rule 0.2 says. Nothing was compiled.

## SWEEP S3

**Summary:** SWEEP S3 is done for all five files, and I re-read every changed file as a compiler would. Nothing was compiled. All five files had their R1 to R4, R7 and R8 recipes applied, with R5 and R6 in the three files the contract lists.

- **HomeScreen.kt** (EDITED 40, LEFT 0):
  - Toast became `PetNotes.show(..., PetNoteKind.ERROR)`, and the `Toast`, `tween`, `HapticFeedbackType` and `LocalHapticFeedback` imports are gone.
  - Haptics now use `rememberHaptics()` with HOLD, STOKE and TICK.
  - Tweens became `motion.custom<Float>(Flourish.STOKE)`, `motion.fast()` and `motion.base()`, with `val motion = Pocket.motion` next to `val c`.
  - R8: the "still starting" text match now uses `RuntimeManager.STILL_STARTING`; the constant exists in the companion object.
  - Tags: `RobotTags.HOME_STATE` on the status title and `HOME_START` on the start button.
  - Literal `dp` values became `Space` tokens (`xl`, `l`, `m`, `s`, `xs`, `gutter`) in the allowed forms only.
- **HomeParts.kt** (EDITED 6, LEFT 0): `PixelProgressBar` uses `motion.scene()` and `motion.loop(Loop.SLIDE, Reverse, LinearEasing)`; `pixel = 3.dp` became `PixelType.big`; the one `Spacer` width became `Space.m`. The `snap`, `tween` and `infiniteRepeatable` imports are removed; `anim` is still used.
- **BotsScreen.kt** (EDITED 6, LEFT 0):
  - `itemsIndexed` is used with `testTag(RobotTags.botsRow(i))`. `BotCard` gains `modifier: Modifier = Modifier` as its last parameter, per contract 5.9.
  - R9: `navSharedBounds(SharedKeys.botCard(bot.id))` is on the card and `navSharedElement(SharedKeys.botAvatar(bot.id))` on the avatar.
- **BotShareUi.kt** (EDITED 8, LEFT 0): the `ConfirmDialog` import now points at `ui.components` and `text =` became `body =`. Three `toast` calls got `PetNoteKind.ERROR`: the two `failedMsg` ones and `tooBigMsg`. Tags `BOTS_EXPORT` and `BOTS_IMPORT` are on the two file buttons.
- **BotEditorScreen.kt** (EDITED about 25, LEFT 0):
  - The `ConfirmDialog` import moved from `ui.settings` to `ui.components`, and both calls use `body =`.
  - R9: the live-preview `PixelPanel` carries `navSharedBounds(SharedKeys.botCard(existing.id))` and its `Avatar` carries `navSharedElement(SharedKeys.botAvatar(existing.id))`. Both apply only when `existing != null`, since a new bot has no key.
  - `dp` values became `Space.s`, `Space.m`, `Space.l` and `Space.xs` in allowed forms only.

**Files:**

- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/home/HomeScreen.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/home/HomeParts.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/bots/BotsScreen.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/bots/BotShareUi.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/bots/BotEditorScreen.kt

**Left as it was:**

- HomeScreen.kt R9: the recent-conversation rows use `ConversationRow` from `ui/chat/ChatListScreen.kt` (S2's file). I added no shared keys at the call site. If S2 puts `navSharedElement` and `navSharedBounds` inside `ConversationRow`, Home gets them too.
- HomeScreen.kt: `delay(1900)`, `delay(2400)` and `delay(4200)` stay (hold times, no token). Literal `dp` values with no token also stay: 10, 14, 6, 22, 280, and the `border(2.dp, ...)` width. The `Spacer(Modifier.height(6.dp))` values stay too, since 6 has no token.
- HomeParts.kt: `height(12.dp)`, `size(...)`, `width(96.dp)`, `heightIn(min = 112.dp)` and `Box(Modifier.height(48.dp))` stay (not a `Spacer`, or no token). Paddings of 6, 10 and 14 stay.
- BotEditorScreen.kt: `Box(Modifier.size(24.dp))` stays (`size` of a non-`Spacer`). `height(60.dp)`, `height(40.dp)`, `size(44.dp)`, `padding(14.dp)`, `padding(10.dp)`, `padding(top = 6.dp)`, `spacedBy(6.dp)`, `spacedBy(10.dp)` and `spacedBy(14.dp)` stay (no token). The `border(if (on) 3.dp else 1.dp, ...)` values stay.
- BotsScreen.kt, BotShareUi.kt: R6 literal replacement does not apply (not among the ten files), so their `dp` values are untouched.
- No `RoundedCornerShape(` exists in my five files, so nothing needed reporting under R6.b. `rg Toast` finds nothing in them.
- The `toast(ctx, msg, kind)` signature is S5's job in `CtlUi.kt`. BotShareUi.kt now calls it with a third argument, so it does not compile until S5's change is in.

**For the integration agent:**

- BotShareUi.kt needs S5's three-parameter `toast` in `ui/settings/CtlUi.kt`. BotsScreen.kt, BotEditorScreen.kt and BotShareUi.kt still import `ui.settings.toast`; that import stays valid.
- BotEditorScreen.kt, BotShareUi.kt and HermesTabs (S4) now import `ui.components.ConfirmDialog`. It compiles whether or not S5 has deleted `ui.settings.ConfirmDialog`, because I removed the old import line.
- `BotCard` in BotsScreen.kt applies the shared-bounds modifier before `clickable` and after `fillMaxWidth`. If bounds look wrong at compile or run time, that is the place to look.
- If `Avatar` or `PixelPanel` ever lose a `modifier` parameter, the R9 edits in BotEditorScreen.kt break. Today `Avatar(id, color, modifier, size, animated)` and `PixelPanel(modifier, ...)` match the calls.

## SWEEP S4

**Summary:** Batch S4 is done in all seven files. I did not compile (no Gradle allowed); I re-read the diffs of each file as the compiler would, and the imports, named arguments and removed symbols are consistent. No Toast.makeText remains in the S4 files, and no import of ui.settings.ConfirmDialog remains in hermes/ or routines/.

FILE ui/routines/RoutineUiParts.kt EDITED 4. The `showToast` body now calls PetNotes.show(message, kind) with the new `kind` parameter and the @Suppress line. `android.widget.Toast` is removed and the PetNoteKind and PetNotes imports are added; `Context` stays.
FILE ui/routines/RoutineEditorScreen.kt EDITED 4. The private ConfirmDialog is deleted. The two call sites now use `body =`, and the `ui.components.ConfirmDialog` import is added. `PocketDialog` and `ButtonKind` stay imported and unused-or-used as before.
FILE ui/routines/RoutinesScreen.kt EDITED 7 (R6.a). Seven literals become Space tokens (see the list below), and the Space import is added.
FILE ui/hermes/HermesScreen.kt EDITED 5. The ConfirmDialog import is swapped to ui.components and the call uses `body =`. The tab chip gets `modifier = Modifier.testTag(hermesTabTag(t))`. A private `hermesTabTag` is added at the end of the file, using the RobotTags constants for all six tabs, including HERMES_TAB_USER/CONFIG/LOGS. The testTag and RobotTags imports are added.
FILE ui/hermes/HermesDocs.kt EDITED 5. Import swapped, `body =`, HERMES_DOC_EDITOR on the raw PocketTextField, HERMES_DOC_SAVE on the Save button, testTag and RobotTags imports.
FILE ui/hermes/HermesTabs.kt EDITED 9. Import swapped, `body =`, and the delete-failed toast now passes PetNoteKind.ERROR (import added). The tags added are HERMES_SKILL_DELETE (trash button), HERMES_SKILL_NEW, hermesSkillRow(skill.name) on the PixelPanel before `.clickable`, HERMES_SKILL_NAME and HERMES_SKILL_CREATE.
FILE ui/hermes/TerminalScreen.kt EDITED 3. TERMINAL_INPUT, TERMINAL_RUN and TERMINAL_OUTPUT, plus the testTag and RobotTags imports.

The seven R6.a replacements in RoutinesScreen.kt:
- padding(12.dp) on the scroll column became Space.m.
- spacedBy(12.dp) in three places became Space.m.
- spacedBy(2.dp) in two places became Space.xxs.
- spacedBy(8.dp) on the action row became Space.s.

**Files:**

- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\routines\RoutineUiParts.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\routines\RoutineEditorScreen.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\routines\RoutinesScreen.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\hermes\HermesScreen.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\hermes\HermesDocs.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\hermes\HermesTabs.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\hermes\TerminalScreen.kt

**Left as it was:**

- RoutinesScreen.kt: `Box(Modifier.size(24.dp))` is not a Spacer, so it stays (R6.a).
- RoutinesScreen.kt: `Box(Modifier.size(8.dp))` (status dot) is not a Spacer, so it stays (R6.a).
- RoutinesScreen.kt: `size = 24.dp`, `36.dp`, `40.dp` and `20.dp` are size arguments of icons and avatars, not an allowed form (R6.a).
- RoutinesScreen.kt: `padding(14.dp)`, `start = 14.dp` and `top = 14.dp` have no token, and `spacedBy(10.dp)`, `spacedBy(6.dp)` and `spacedBy(3.dp)` have no token.
- RoutinesScreen.kt: `delay(5_000)` is a poll time with no token, per the recipe.
- RoutinesScreen.kt, R5 (durations and easing): no tween or literal duration sites in S4 files.
- RoutineEditorScreen.kt, RoutineUiParts.kt and the four hermes files: no R6.a literals touched (only RoutinesScreen is in the R6 list).
- RoutineEditorScreen.kt, RoutineUiParts.kt and the four hermes files: no `RoundedCornerShape(` sites. Contract 5.9 says not to replace it anyway.
- The other `toast(` and `showToast(` callers keep the default INFO kind, since only the HermesTabs delete-failed call is listed for ERROR.
- No tag for HERMES_SKILL_DELETE_CONFIRM, because it does not exist (contract 3.12).
- Unused imports left in place as the rules say: `PocketDialog` and `ButtonKind` in RoutineEditorScreen.kt are still used by other code or harmless.

**For the integration agent:**

- Nothing was compiled. The one thing the compiler should check is `PetNotes` and `PetNoteKind` in `ui.components`, as written by DESIGN. RoutineUiParts.kt imports them under those names.
- The files use CRLF line endings. I kept them, and git only warns about LF to CRLF on RoutinesScreen.kt.
- `import dev.pocketagent.app.ui.RobotTags` was added before `ui.Route` in TerminalScreen.kt, and `RobotTags` is a top-level object in package `dev.pocketagent.app.ui`. The imports are not strictly alphabetical, which is harmless.
- `PixelIconButton` and `PixelButton` accept `modifier` as a named parameter in the current signatures, and `PocketChip` takes `modifier` before `icon`. The tag edits use the named form.

## SWEEP S5

**Summary:** Batch S5 is done. I did not compile or run anything, so none of it is checked by a build. Changes by file:
- CtlUi.kt: deleted the private ConfirmDialog. `toast` now takes `kind: PetNoteKind = PetNoteKind.INFO` and calls `PetNotes.show(message, kind)`, with `@Suppress("UNUSED_PARAMETER")` above it. Removed the Toast import and added the PetNoteKind and PetNotes imports.
- BackupSection.kt, UsageSection.kt, VoiceSoundSection.kt: the ConfirmDialog call now uses `body =` instead of `text =`, plus the `ui.components.ConfirmDialog` import.
- Tags: SettingsScreen.kt `settingsSection(sec.key)` on each section header Row, before `heightIn`. UsageSection.kt `USAGE_TODAY`, `USAGE_BUDGET_LIMIT`, `USAGE_BUDGET_WARN`, `USAGE_BUDGET_BLOCK`. BehaviourSection.kt `ChipRow(Modifier.testTag(BEHAVIOUR_IDLE_MINUTES))`, and nothing else in that file (the privacy row is untouched). BackupSection.kt `BACKUP_SAVE`, `BACKUP_RESTORE`, and `BACKUP_RESULT` on both the Done text and the Failed ErrorText. Each of these files got the `testTag` and `RobotTags` imports.
- AppearanceSection.kt (R6): `Arrangement.spacedBy` and `padding` literals of 4, 8 and 12 dp became `Space.xs`, `Space.s` and `Space.m` (12 sites). `corner = 1` became `Corner.chip`. Added the Space and Corner imports.
- No edits needed in ProviderSection.kt (it has no Toast, ConfirmDialog, sheet, haptic or tween site). No haptic, tween or ModalBottomSheet sites exist in the S5 files, and `Toast.makeText` no longer appears in them.

**Files:**

- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/settings/CtlUi.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/settings/BackupSection.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/settings/UsageSection.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/settings/VoiceSoundSection.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/settings/SettingsScreen.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/settings/BehaviourSection.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/settings/AppearanceSection.kt

**Left as it was:**

- AppearanceSection.kt: `.clip(RoundedCornerShape(14.dp))` and `.border(..., RoundedCornerShape(14.dp))` (R6.b skipped, contract 5.9); the `RoundedCornerShape` import stays.
- AppearanceSection.kt: `spacedBy(10.dp)`, `spacedBy(6.dp)` (two sites), `spacedBy(3.dp)`, `padding(6.dp)`, and all `size(...)`, `width(...)`, `height(...)` and `border(...)` dp values. No token exists, or the site is not an allowed form.
- Other `N.dp` literals in CtlUi, UsageSection, SettingsScreen, BehaviourSection, BackupSection, VoiceSoundSection, ProviderSection: R6 applies only to the ten listed files, and AppearanceSection is the only one of them in S5.
- CtlUi.kt: unused imports `ButtonKind`, `PixelButton` and `PocketDialog` after deleting ConfirmDialog. Left in place (rule 5); they only cause warnings and clash with nothing.
- None of the toast callers in S5 files needed the ERROR kind (the four named sites are in S3 and S4).

**For the integration agent:**

- None expected. RuntimeSection.kt already imports ui.components.ConfirmDialog and uses `body =`, so deleting the old ConfirmDialog in CtlUi.kt leaves no unresolved call in the settings package.
- I did not check other packages for imports of `dev.pocketagent.app.ui.settings.ConfirmDialog` (HermesTabs, HermesScreen, HermesDocs, BotEditorScreen, BotShareUi). Those belong to S3 and S4 and must move to the ui.components import.

## WORDS W1

**Summary:** W1 (chat, split screen) done as far as the recipes reach. Converted stringResource( to t(, pluralStringResource( to tn(, and ctx/context/state.context.getString( to Texts.get(receiver, ...) in eight files; StreamingMarkdown.kt had no sites and is unchanged. Engine memory lines (MEMORY.md / USER.md) now use eng_memory_md_used and eng_user_md_used (keys verified in strings_engine.xml). The theme-switched note uses Texts.get(ctx, themeNameRes(pick)) with the import from ui.settings. Removed the stringResource and pluralStringResource imports; added t, tn, Texts and themeNameRes imports after the last import line. Line endings preserved (CRLF in ChatScreen.kt, ChatMessageList.kt, ToolOutputSheet.kt). Nothing compiled, per the rules. Checked by rg: no stringResource/pluralStringResource/getString left in the W1 files; no local val t shadows a t( call (ChatScreenState doSend and ChatMessageList LaunchedEffect have val t but no t( calls in scope).

**Files:**

- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/chat/ChatScreen.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/chat/ChatScreenState.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/chat/ChatSlashActions.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/chat/ChatTopArea.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/chat/ChatMessageList.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/chat/ChatInputArea.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/chat/ChatDialogs.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/chat/ToolOutputSheet.kt

**Left as it was:**

- ui/chat/StreamingMarkdown.kt: no stringResource or getString site in the file, nothing to convert (its only val t is a frame time with no text call).
- ChatScreen.kt:630 named in 5.10 is not present any more (STREAM moved the code); nothing to leave.
- ToolOutputSheet.kt NumberFormat (numbers.format) left on its current locale: the recipe says it 'may become appLocale()' but does not name it as a site, so not changed.

**For the integration agent:**

- ChatSlashActions.kt now imports dev.pocketagent.app.ui.settings.themeNameRes (recipe T4, W5 creates it in AppearanceSection.kt). Not verified that W5 has created it yet; if it is missing the compile fails there.
- Nothing compiled. The Texts.get / t / tn calls assume the TEXT signatures of contract 3.6 (vararg Any); no nullable argument was found in a getString call (ChatDialogs failure is a String? smart-cast to String in the else branch, and result.second is Int), but the integration compile should confirm.
- git diff shows the working-tree changes of earlier stages in these files (for example a titleModifier line in ChatTopArea.kt); they were not made by this batch and were not touched.

## WORDS W2

**Summary:** WORDS batch W2 (chat A) is done. All six files are converted: stringResource( became t( (61 sites in total), the one ctx.getString became Texts.get(ctx, ...), and the one plural (ui_unread_cd) became tn(R.plurals.ui_unread_cd, count, count). The stringResource import was removed from each file and the t, tn and Texts imports were added after the last import line. rg finds no stringResource, getString or pluralStringResource left in the six files. Counts match the sweep table (ChatListScreen 34, ChatMessages 20, ChatCommon 13 plus 1 getString, StarredScreen 8, MarkdownView 2, ChatSearch 6). The t and tn signatures were checked in ui/text/TextsCompose.kt, and count is an Int. Nothing was compiled.

**Files:**

- app/src/main/java/dev/pocketagent/app/ui/chat/ChatListScreen.kt
- app/src/main/java/dev/pocketagent/app/ui/chat/ChatMessages.kt
- app/src/main/java/dev/pocketagent/app/ui/chat/ChatCommon.kt
- app/src/main/java/dev/pocketagent/app/ui/chat/StarredScreen.kt
- app/src/main/java/dev/pocketagent/app/ui/chat/MarkdownView.kt
- app/src/main/java/dev/pocketagent/app/ui/chat/ChatSearch.kt

**Left as it was:**

- ChatCommon.kt GeneratingEmber: val t = rememberInfiniteTransition is kept. The function has no t( call, so the recipe 2.7 rule says leave it.
- ChatMessages.kt BlinkingCaret: val t = rememberInfiniteTransition is kept, for the same reason (no t( call in that function).
- ChatCommon.kt line 238 (copyToClipboard): ctx.getString(R.string.ui_chat_copied) became Texts.get(ctx, R.string.ui_chat_copied). The surrounding line was already changed by SWEEP (a PetNotes.show wrapper), and I kept that form.

**For the integration agent:**

- The git diff against HEAD for ChatCommon.kt also shows removals of SheetAction, ActionSheet, ConfirmDialog, Toast and some imports. These were already in the working tree before this batch (SWEEP and DESIGN work), so they were not made by W2. The integration compile should check them.
- ChatSearch.kt and MarkdownView.kt have LF line endings in the working copy (git warns about CRLF on checkout). I kept each file's existing line endings, so this is not a change from W2.
- Nothing was compiled, per the rules. The integration agent should check that the imports of ChatCommon.kt still resolve (it now imports Texts and t next to the SWEEP imports).

## WORDS W3

**Summary:** W3 (chat B) done. All six files converted: no stringResource( left in ChatInput.kt, ChatQuickBar.kt, NewChatSheet.kt, AttachmentUi.kt, SkillPickerSheet.kt, SlashPalette.kt. 58 stringResource( sites became t( (21+16+11+10+8+7 = 73 total, counting the one plural below). The one plural site NewChatSheet.kt ui_new_selected became tn(R.plurals.ui_new_selected, picked.size, picked.size); the key is a plurals resource in strings_chat.xml. ChatQuickBar.kt effort fallback ef.replaceFirstChar { it.uppercase() } became { it.titlecase(appLocale()) } (T8/capitals rule). Imports: stringResource import removed from all six; t added to all six; tn added to NewChatSheet; appLocale added to ChatQuickBar. Line endings kept per file (ChatInput, ChatQuickBar, NewChatSheet, SlashPalette are CRLF; AttachmentUi and SkillPickerSheet are LF). No getString, no pluralStringResource, no local t shadowing, no left/right sites in this batch. No engine literals in these files.

**Files:**

- app/src/main/java/dev/pocketagent/app/ui/chat/ChatInput.kt
- app/src/main/java/dev/pocketagent/app/ui/chat/ChatQuickBar.kt
- app/src/main/java/dev/pocketagent/app/ui/chat/NewChatSheet.kt
- app/src/main/java/dev/pocketagent/app/ui/chat/AttachmentUi.kt
- app/src/main/java/dev/pocketagent/app/ui/chat/SkillPickerSheet.kt
- app/src/main/java/dev/pocketagent/app/ui/chat/SlashPalette.kt

**Left as it was:**

- Nothing left in W3 files. Not compiled (no Gradle by rule); the R.plurals reference in NewChatSheet relies on strings_chat.xml plurals ui_new_selected, which exists.

**For the integration agent:**

- Not compiled. Integrator should confirm that appLocale() (@Composable, in ui.text) exists with that exact name, and that t/tn signatures match Texts.kt/TextsCompose.kt. ChatQuickBar.kt line 247 calls appLocale() inside a composable-scope lambda (replaceFirstChar is inline).
- Mixed line endings are pre-existing: AttachmentUi.kt and SkillPickerSheet.kt are LF, the other four are CRLF; kept as found. Git warns about LF-to-CRLF on these; not changed by this batch.

## WORDS W4

**Summary:** W4 (stage 3, words) is done for its 15 files. Every stringResource( in them is now t( (none left except a KDoc mention in AppRoot.kt, left per the recipe). The five plural sites are tn(R.plurals...) and Texts.getQuantity(...), each key checked as a <plurals> in strings_control.xml or strings_chat.xml. All ctx.getString sites are Texts.get(ctx, ...). The AvatarGrid (T4) uses EnumLabels.avatar with a t() fallback to id.name. BotsScreen's local t was renamed to tpl (T6). The three comment edits are done. Nothing was compiled; I re-read each file for imports and argument types. No Gradle, adb, git write or phone use.

**Files:**

- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\home\HomeScreen.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\home\HomeParts.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\bots\BotEditorScreen.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\bots\BotShareUi.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\bots\BotsScreen.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\AppRoot.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\components\PocketTopBar.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\components\TypingIndicator.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\components\ConfirmDialog.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\components\EmberLine.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\components\PetNote.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\components\PocketSheet.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\components\Skeleton.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\components\PocketSwitchRow.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\components\PocketDialog.kt

**Left as it was:**

- AppRoot.kt KDoc line 'Makes `stringResource` (and anything reading the locals below)' left as the recipe says (no call site, a comment).

**For the integration agent:**

- Not compiled (by rule). Imports of t, tn, Texts and EnumLabels are added after the last import line (as rule 6 says), not sorted; the old stringResource import line is removed in every file.
- HomeScreen.kt, HomeParts.kt, BotEditorScreen.kt and BotShareUi.kt had CRLF line endings in the working copy before this edit (HEAD stores LF); I kept each file's existing endings. The large git diff stat for those files is that line-ending difference plus the conversion, not a whole-file rewrite by content.
- HomeParts.kt has a local val t in PixelProgressBar, which contains no t( call, so there is no shadowing; the t( calls in BotCard and NewBotCard are in other functions. Skeleton.kt and TypingIndicator.kt have local val t inside nested lambdas, with no t( call inside them.
- The only Texts/tn/t calls that need a composable context are the converted stringResource sites, same as before. The new non-composable sites use Texts.get or Texts.getQuantity: BotShareUi importReasonText and the parse function, BotShareUi toast(ctx, Texts.getQuantity(...)), BotEditorScreen copy name, AppRoot PetNotes.show, HomeScreen PetNotes.show.

## WORDS W5

**Summary:** WORDS batch W5 done. In the five settings files, all 132 stringResource( calls (SettingsScreen 31, AppearanceSection 28, ProviderSection 44, CtlUi 6, BehaviourSection 23) became t( with the arguments unchanged. Each file's stringResource import was replaced by import dev.pocketagent.app.ui.text.t. rg finds no stringResource, getString or pluralStringResource left in the five files. The theme names (recipe T4) are done: AppearanceSection's private themeName became internal themeNameRes(id): Int mapping to eng_theme_* keys plus a @Composable internal themeName(id) = t(themeNameRes(id)); the two preview literals became t(R.string.eng_preview_hi) and t(R.string.eng_preview_hello); SettingsScreen's summaryOf APPEARANCE branch became themeName(ap.theme). All referenced keys exist in values/strings_engine.xml. Nothing was compiled.

**Files:**

- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/settings/SettingsScreen.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/settings/AppearanceSection.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/settings/ProviderSection.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/settings/CtlUi.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/settings/BehaviourSection.kt

**Left as it was:**

- No left/right change: no absolute direction in code in these files; the 'right after' in a ProviderSection.kt comment is a time phrase and stays.
- No ctx.getString, plural or Texts call was needed: none exists in these five files.
- The theme command vocabulary (ThemeId.entries joined for /theme) is not in these files; it belongs to ChatScreen (W1) and stays as the recipe says.

**For the integration agent:**

- Not compiled (per the rules). The conversion is one-to-one with the same vararg Any signature and the same @Composable context, so no argument types change.
- Cross-batch dependency for W1: any chat file that shows a theme name must import dev.pocketagent.app.ui.settings.themeNameRes or themeName, which are now internal in AppearanceSection.kt (they were private before).
- git warns that LF will be replaced by CRLF on these five files; the line endings were already LF before this edit and the script kept them.

## WORDS W6

**Summary:** WORDS batch W6 done for its seven files. Every stringResource( became t(, pluralStringResource( became tn(, and ctx.getString( became Texts.get(ctx, . The four ctl_usage_turns sites became tn(R.plurals.ctl_usage_turns, x, x). The local t in UsageSection (chart loop: val t = d.totals, renamed dayTotals; TotalsCard parameter t, renamed totals) was renamed before the conversion. The old stringResource and pluralStringResource imports were removed, and t, tn and Texts imports were added after the last import line where used. rg confirms no stringResource, pluralStringResource or getString left in these files. Not compiled, per the rules. The git diff also shows SWEEP's earlier uncommitted edits (ConfirmDialog import, testTag additions, body= renames); those were already present and were not touched by this batch.

**Files:**

- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/settings/UsageSection.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/settings/VoiceSoundSection.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/settings/RuntimeSection.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/settings/BackupSection.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/settings/PracticeBanner.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/diag/DiagnosticsExtras.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/ui/diag/PrivacyRows.kt

**Left as it was:**

- DiagnosticsExtras.kt CrashOfferDialog: the optional appLocale() change for the crash date was not made (the contract says 'may'; the locale there is LocalConfiguration.current.locales[0], which is the same value, so behaviour is unchanged). BackupSection.kt catch (t: Throwable) is a catch parameter named t with no t( call inside its block, so it was left as it is.

**For the integration agent:**

- Not compiled (no Gradle by rule). Checks done offline: no stringResource, pluralStringResource or getString left in the seven files; every file that calls t( or tn( or Texts. has its import; the plural sites use tn with the count passed twice; no nullable argument is passed bare (the only e.message uses are in the existing string expressions).
- New import lines were added after the last existing import line, as SWEEPS 0.1 rule 6 asks, so they are not in sorted position. This compiles but is not sorted.
- The rg check for left and right found no absolute direction API in these files, so there was no 2.9 edit here.

## WORDS W7

**Summary:** W7 (hermes) done. In HermesTabs.kt, HermesDocs.kt, HermesScreen.kt and TerminalScreen.kt, every stringResource( became t( (43, 31, 12 and 15 sites; 13 in HermesScreen counts the line-140 site). The one pluralStringResource-shaped site (ctl_chars in HermesDocs.kt, a <plurals> key) became tn(R.plurals.ctl_chars, state.text.length, state.text.length). Two ctx.getString sites in HermesTabs.kt became Texts.get(ctx, R.string.ctl_skill_create_failed, ...) and Texts.get(ctx, R.string.ctl_skill_delete_failed, err) (err is smart-cast to String in the else branch). The local t in HermesScreen.kt's HermesTab.entries.forEach renamed to ht (declaration, label line and chip line only). The androidx stringResource import was removed from all four files; imports added after the last import line: t in all four, tn in HermesDocs.kt, Texts in HermesTabs.kt. Verified by rg: no stringResource, getString, getQuantityString or local t left in the four files. Nothing left unconverted. No Gradle, git index or tree-changing command run; only git diff. HermesDocs.kt keeps CRLF, the others LF as before.

**Files:**

- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\hermes\HermesTabs.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\hermes\HermesDocs.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\hermes\HermesScreen.kt
- C:\Users\PC\Agent's Workspace\Claude\PocketAgent\app\src\main\java\dev\pocketagent\app\ui\hermes\TerminalScreen.kt

**Left as it was:**


**For the integration agent:**

- Not compiled (no Gradle allowed). The conversion is mechanical and checked by rg and by reading the changed lines; the integration compile must still confirm that the t() and Texts.get calls resolve inside their composable or non-composable contexts exactly as the old stringResource and getString calls did.
- The git diff against HEAD also shows SWEEP-era changes already in these files (ConfirmDialog import, the text= to body= rename, the testTag and hermesTabTag lines). These were there before this batch and were not touched by it.

## WORDS W8

**Summary:** W8 (routines screens) done. In RoutineEditorScreen.kt, RoutinesScreen.kt and RoutineUiParts.kt all 101 stringResource( calls became t( (56 / 30 / 15), the androidx stringResource import was removed and import dev.pocketagent.app.ui.text.t added once after the last import line in each file. RoutinesScreen.kt: the local lambda parameter t in ROUTINE_TEMPLATES.forEach was renamed to tpl (T6) with its uses in the same block. No plurals, no getString, no left/right in these files, so nothing else changed. Verified by rg (no stringResource or old text calls left) and by rebuilding each file from the checkpoint b56079d with the expected edits; the only remaining differences are SWEEP's earlier edits already in the working tree (ConfirmDialog body/text, Space tokens, PetNotes/showToast), which are not mine. Nothing compiled (no Gradle). Files use LF, as in HEAD.

**Files:**

- app/src/main/java/dev/pocketagent/app/ui/routines/RoutineEditorScreen.kt
- app/src/main/java/dev/pocketagent/app/ui/routines/RoutinesScreen.kt
- app/src/main/java/dev/pocketagent/app/ui/routines/RoutineUiParts.kt

**Left as it was:**

- No sites left. Not converted by design: the comment-only mentions are none in these files; no plural keys in these files (none of the rtn_* keys is a plural).

**For the integration agent:**

- Not compiled: the integration agent should check the import of dev.pocketagent.app.ui.text.t, and that t is called only from composables (it is @Composable like stringResource was).
- The working tree already carried SWEEP's edits in these files (ConfirmDialog with body= parameter, Space tokens, PetNotes-based showToast with a new kind parameter); those were not touched by me and should be checked at compile time.
- Scratch conversion script is at C:/Users/PC/AppData/Local/Temp/claude/52419fed5478bbf7/e250ad68-f579-48cc-88fd-2307ebf9482b/scratchpad/w8_convert.py (not part of the repo).

## WORDS W9

**Summary:** W9 (service and routines engine) done offline. Converted all getString sites in the four files to Texts.get / Texts.getQuantity per recipe T2 (receiver moved to first argument), added one import of dev.pocketagent.app.ui.text.Texts after the last import line in each file. Counts match the recipe: ReplyNotifier 29, AgentService 8 receiver forms + 1 bare getString (line ~112, `this` is the Service) + 1 getQuantityString (line ~67, Texts.getQuantity), FireTileService 2, RoutineScheduler 9. No stringResource, pluralStringResource, getString or getQuantityString remains in the four files. No nullable argument: botName is String, the name values are non-null, subtitle is Int. Left/right: none in these files. No local named t. CRLF/LF endings were preserved; git diff shows only the changed lines (54 insertions, 50 deletions). No Gradle, no git write, no phone.

**Files:**

- app/src/main/java/dev/pocketagent/app/service/ReplyNotifier.kt
- app/src/main/java/dev/pocketagent/app/service/AgentService.kt
- app/src/main/java/dev/pocketagent/app/service/FireTileService.kt
- app/src/main/java/dev/pocketagent/app/routines/RoutineScheduler.kt

**Left as it was:**


**For the integration agent:**

- Not compiled (as the task requires). Each changed file keeps its R import, which is still used by R.string and R.drawable references.
- The helper script used for the mechanical rewrite is in the scratchpad (w9_convert.py); it is not part of the repository.
- git prints LF-to-CRLF warnings for AgentService.kt, FireTileService.kt and ReplyNotifier.kt. This is pre-existing working-copy line-ending state, not caused by the text changes; the diff is line-limited.

## WORDS W10

**Summary:** W10 done: the engine literals of ChatEngine.kt, AttachmentStore.kt, TerminalSession.kt and HermesHome.kt now use Texts.text / Texts.get with the eng_* keys of recipes 2.4.1 to 2.4.4 (plus the T8 titlecase change in HermesHome). @Before TestTexts.installEnglish() was added to the four test classes (ChatEngineTest, TerminalSessionTest, HermesHomeTest, RuntimeManagerInitTest), with the imports. Every replacement matched on the first try by exact text; no Gradle, no git write, nothing compiled. The eng_* keys used all exist in strings_engine.xml. Re-read as a compiler: imports are added once after the last import line, argument types are as the recipe gives (the nullable e.message is always in a string template). Three non-English literals in these files were left as the recipe says: the loop-review prompt, the HTTP line, the HermesHome skill description default.

**Files:**

- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/data/ChatEngine.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/data/AttachmentStore.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/hermes/TerminalSession.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/main/java/dev/pocketagent/app/hermes/HermesHome.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/test/java/dev/pocketagent/app/ChatEngineTest.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/test/java/dev/pocketagent/app/TerminalSessionTest.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/test/java/dev/pocketagent/app/HermesHomeTest.kt
- C:/Users/PC/Agent's Workspace/Claude/PocketAgent/app/src/test/java/dev/pocketagent/app/RuntimeManagerInitTest.kt

**Left as it was:**

- data/ChatEngine.kt: the loop-review prompt, the HTTP status line (asserted by ChatEngineTest) and the Transcript and other non-UI literals stay, as recipe 2.4.1 says.
- data/AttachmentStore.kt: c.getString(0) (a cursor, not a text) stays.
- hermes/HermesHome.kt: the skill description default ("Use when ...") stays; it is not in the recipe.
- No @After TestTexts.reset() added: recipe 2.8 names only @Before.

**For the integration agent:**

- RuntimeManagerInitTest.kt: I added the @Before installEnglish here, because the W10 row of section 8.2 names the four test classes. Contract line 150 instead says W10b adds it. The W10b agent must NOT add a second @Before to this file, or the test won't compile (duplicate fun installTexts). The lead should confirm the owner.
- The eng_rt_* keys exist in strings_engine.xml, so W10b is not skipped; that batch is not mine.
- Git prints LF-to-CRLF warnings for the edited main files; those are working-copy line-ending notices, not part of this change. The two test files that were CRLF kept CRLF.

## WORDS W10b

**Summary:** W10b done for both files. RuntimeManager.kt: 17 edits (14 RuntimeState.Error / onOutput literals converted to eng_rt_* keys via Texts.get(context, ...), including the exited-with-hint branch and the e.message argument wrapped as a string template; imports R and Texts added after the last import). VoiceEngine.kt: 3 edits (eng_tts_error, eng_tts_unavailable, eng_tts_init_failed via Texts.get(context, ...)), imports R and Texts added. Each replacement was applied by an exact-match script that asserted a single match per site. No stringResource( remains in runtime/ or voice/. No Gradle or git was run; nothing compiled.

**Files:**

- app/src/main/java/dev/pocketagent/app/runtime/RuntimeManager.kt
- app/src/main/java/dev/pocketagent/app/voice/VoiceEngine.kt

**Left as it was:**

- RuntimeManager.kt: "Practice model could not be installed: ${e.message}" (line about 480) | no eng_rt key in the W10b table; report to the lead, not converted
- RuntimeManager.kt: step texts Starting(...), Stopped(...), Extracting and the "stopped (...)" strings | stay English on purpose (HomeScreen and RuntimeSection match "still starting")
- RuntimeManager.kt: "${e.javaClass.simpleName}: ${e.message}" in the start-failed Error (line about 207) and the diagnostics lines ("rootfs not installed yet", "proot binary or rootfs missing") | not in the table; stay
- VoiceEngine.kt: the spoken demo sentences and the Turkish/French/English bot sample texts | stay (recipe: leave the spoken demo sentences)

**For the integration agent:**

- RuntimeManagerInitTest (W10 owns it, not this batch) still needs the @Before TestTexts.installEnglish() from SWEEPS 2.8. Its assert at about line 89 checks the English "Cannot read rootfs.stamp" text, which the converted message now produces through Texts, so it fails until W10 adds @Before.
- Compile not run. Watch for: RuntimeManager.kt imports R and Texts after the last import line (order per SWEEPS 0.2 rule 6, not sorted); m.body is String; ExitRecord.hint(code) is String? and is used inside let; stampRead.error smart cast is unchanged from the original.
- Texts.get is passed plain values (File paths, Int ports, String bodies). The vararg Any formats them the same way the old string templates did.

## TRANSLATE

**Summary:** Translated every missing key into Turkish and French. Each language got 175 keys: 27 in strings_config.xml, 3 in strings_privacy.xml, 8 in strings_robot.xml, 5 in strings_stream.xml, 132 in strings_engine.xml, plus design_note_dismiss and design_ember_active appended to strings_design.xml. The five new files were created under values-tr and values-fr. Placeholders and their order, \' escapes, \" and \n, &gt;, MEMORY.md, USER.md, finish_reason, error_code, HTTP 401, proot, rootfs, Hermes and Agirî are unchanged. Plurals: Turkish one equals other. French gets one and other, plus many on the stm_ plural. The test language was regenerated (12 files). make-test-language.ps1 -Check prints "up to date" and -SelfTest prints "self test ok". All strings XML files I wrote, and the generated debug ones, parse with python's xml parser. strings-check.ps1 reports no parity, placeholder or note errors any more, but it still exits 1 on four English-only length errors that the stage-1 resolution assigns to the lead, so I could not get it to exit 0. Those four were not mine to fix.

**Files:**

- app/src/main/res/values-tr/strings_config.xml
- app/src/main/res/values-tr/strings_privacy.xml
- app/src/main/res/values-tr/strings_robot.xml
- app/src/main/res/values-tr/strings_stream.xml
- app/src/main/res/values-tr/strings_engine.xml
- app/src/main/res/values-tr/strings_design.xml
- app/src/main/res/values-fr/strings_config.xml
- app/src/main/res/values-fr/strings_privacy.xml
- app/src/main/res/values-fr/strings_robot.xml
- app/src/main/res/values-fr/strings_stream.xml
- app/src/main/res/values-fr/strings_engine.xml
- app/src/main/res/values-fr/strings_design.xml
- app/src/debug/res/values-en-rXZ/ (regenerated, 12 files with values-en)

**Left as it was:**

- Four English length errors in strings-check.ps1: cfg_diag_guest_title (33 characters, room 28), cfg_priv_web_sub (228 characters, room 40), rbt_practice_on (47 characters, room 14), rbt_practice_left (15 characters, room 14). Stage-1 resolution (list 'For the lead') says the lead decides these, either by changing the key's kind in the note or by shortening the English. I did not edit English files or the check.
- Length of my own translations against floor(room*1.4): the script prints no tr/fr length errors, but I cannot tell whether it skips tr/fr while the English key already fails. Re-check once the four English errors are settled, especially tr/fr rbt_practice_on, rbt_practice_left, cfg_priv_web_sub and cfg_diag_guest_title, which are long for their current kinds.
- Warnings (about 939, mostly 'no translator note' on older keys) are not mine; the baseline is not written (INTEGRATE does that after the four errors are settled).
- Turkish and French follow the register of the existing files: Turkish uses informal 'sen', French uses 'vous' for status and error lines, matching strings_control and the settings texts (the existing fr files already mix tu and vous).

**For the integration agent:**

- Tools: strings-check.ps1, make-test-language.ps1 -Check and -SelfTest all ran fine under Windows PowerShell 5.1. No tool is broken.
- In the French engine file I kept 'Supprimer les fichiers de l'environnement' (the existing btn_reset_runtime wording) and 'Fournisseur et modèle' as the Setup name. In Turkish I used 'Ayarlar > Kullanım ve bütçe' and 'Çalışma ortamı dosyalarını sil' for the same references. If those app names change, eng_budget_*, eng_rt_stamp_unreadable and eng_rt_bad_config need the same change.
- stm_tool_sheet_truncated: English has only quantity=other. French has one, many and other (all the same text), Turkish has one and other. If the lint complains about a plural quantity the English file lacks, drop the extra items.
