# UI debt survey: mechanical counts for Agirî (Kotlin under app/src/main/java)

Date of the survey: 2026-11-25. Read-only: no tracked file was changed; no Gradle, adb or phone access; `dist\wt` was not touched.

## Method

- Counts come from ripgrep 15.1.0 run with `--no-config`, over every `.kt` file under `app/src/main/java` (132 files). The tables count occurrences (`--count-matches`), not lines. A first pass used `-c` (lines); the tables were rebuilt after the switch, so the numbers below are the rebuilt ones.
- Column 12b counts `semantics {` / `semantics(` blocks. The bare word also matches import lines (61 import lines contain `semantics`), so the import lines are excluded this way.
- Column 5 is the literal pattern `\b(Basic)?AlertDialog\(|\bDialog\(`. The app does not use `AlertDialog` or `BasicAlertDialog`. Its dialogs go through its own wrapper `PocketDialog(` (`ui/components/PocketDialog.kt:37`), which contains the one bare `Dialog(` (`PocketDialog.kt:45`). The 16 `PocketDialog(` call sites are listed in section B, not counted in column 5.
- Columns 11 (left/right), 12a (`Modifier.testTag(`) and 12d (`contentDescription = null`) are zero in the Kotlin code. The res XML has no `layout_marginLeft`, `layout_marginRight`, `paddingLeft`, `paddingRight` or `gravity="left|right"` either (checked in section E).
- Column F counts `.uppercase(`, `.lowercase(`, `.capitalize(` and `replaceFirstChar`. Many are logic (parsing, matching), not display. Section F lists every line.
- The per-file table shows only files with at least one non-zero count (76 of 132 files). The package totals include all 132.
- Package folders: `util/` (2 files) is not in the requested list and gets its own row. Top-level files of `dev/pocketagent/app/` are `root`. Files directly under `ui/` (`AppRoot.kt`, `Nav.kt`) are `ui (root)`.

## Column key

1 stringResource( · 2 getString( (any receiver) · 3 pluralStringResource( or getQuantityString( · 4 Toast.makeText · 5 Dialog( / AlertDialog( · 6 ModalBottomSheet( · 7 tween( / spring( / keyframes( / animate*AsState( · 8 RoundedCornerShape( · 9 durationMillis = N or delay(N) · 10 N.dp · 11 left/right alignment or padding · 12a Modifier.testTag( · 12b semantics blocks · 12c contentDescription (any) · 12d contentDescription = null · F case change

## Totals per package folder (all 132 files)

| Package folder | files | 1 stringResource | 2 getString | 3 plural | 4 Toast | 5 Dialog | 6 ModalBottomSheet | 7 anim | 8 RCS | 9 durations | 10 .dp | 11 left/right | 12a testTag | 12b semantics | 12c cd | 12d cd=null | F case |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| ui/home | 3 | 37 | 0 | 0 | 1 | 0 | 0 | 5 | 0 | 3 | 57 | 0 | 0 | 1 | 3 | 0 | 0 |
| ui/chat | 16 | 215 | 32 | 0 | 8 | 0 | 5 | 5 | 21 | 6 | 387 | 0 | 0 | 5 | 30 | 0 | 14 |
| ui/bots | 3 | 93 | 10 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 62 | 0 | 0 | 2 | 7 | 0 | 7 |
| ui/hermes | 6 | 103 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 57 | 0 | 0 | 1 | 11 | 0 | 4 |
| ui/routines | 3 | 109 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 1 | 45 | 0 | 0 | 0 | 6 | 0 | 0 |
| ui/settings | 10 | 250 | 7 | 0 | 1 | 0 | 0 | 0 | 2 | 0 | 96 | 0 | 0 | 1 | 2 | 0 | 3 |
| ui/components | 12 | 2 | 0 | 0 | 0 | 1 | 0 | 4 | 0 | 0 | 76 | 0 | 0 | 11 | 16 | 0 | 3 |
| ui/theme | 6 | 0 | 6 | 0 | 0 | 0 | 0 | 0 | 5 | 0 | 5 | 0 | 0 | 0 | 0 | 0 | 0 |
| ui/pixel | 6 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 5 | 0 | 0 | 0 | 0 | 0 | 1 |
| ui (root) | 2 | 5 | 1 | 0 | 1 | 0 | 0 | 7 | 0 | 0 | 17 | 0 | 0 | 0 | 0 | 0 | 0 |
| service | 7 | 0 | 40 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| routines | 5 | 0 | 9 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| data | 16 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 12 |
| hermes | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 4 |
| runtime | 16 | 0 | 18 | 0 | 0 | 0 | 0 | 0 | 0 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| diag | 5 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| voice | 5 | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 4 |
| api | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| root | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| util | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| **Total** | 132 | 814 | 129 | 1 | 12 | 1 | 5 | 21 | 28 | 16 | 807 | 0 | 0 | 21 | 75 | 0 | 56 |


## Per-file counts (files with at least one non-zero count)

| File (under app/src/main/java/dev/pocketagent/app/) | 1 stringResource | 2 getString | 3 plural | 4 Toast.makeText | 5 Dialog | 6 ModalBottomSheet | 7 anim/tween/spring/keyframes | 8 RoundedCornerShape | 9 literal durations | 10 `N.dp` | 11 left/right | 12a Modifier.testTag | 12b semantics blocks | 12c contentDescription | 12d contentDescription = null | F case change |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| data/AttachmentStore.kt | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| data/BackupManager.kt | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| data/BotForge.kt | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| data/ChatEngine.kt | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| data/SlashCommands.kt | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| data/Transcript.kt | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| diag/Diagnostics.kt | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| hermes/HermesHome.kt | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 4 |
| hermes/TerminalSession.kt | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| routines/RoutineScheduler.kt | 0 | 9 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| runtime/ProviderConfig.kt | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| runtime/RuntimeManager.kt | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| runtime/ServiceAccount.kt | 0 | 8 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| runtime/Settings.kt | 0 | 10 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| service/AgentService.kt | 0 | 9 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| service/FireTileService.kt | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| service/ReplyNotifier.kt | 0 | 29 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| ui/AppRoot.kt | 5 | 1 | 0 | 1 | 0 | 0 | 7 | 0 | 0 | 17 | 0 | 0 | 0 | 0 | 0 | 0 |
| ui/bots/BotEditorScreen.kt | 61 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 36 | 0 | 0 | 2 | 4 | 0 | 6 |
| ui/bots/BotShareUi.kt | 16 | 9 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 3 | 0 | 0 | 0 | 0 | 0 | 1 |
| ui/bots/BotsScreen.kt | 16 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 23 | 0 | 0 | 0 | 3 | 0 | 0 |
| ui/chat/AttachmentUi.kt | 10 | 0 | 0 | 0 | 0 | 1 | 0 | 3 | 0 | 45 | 0 | 0 | 0 | 4 | 0 | 0 |
| ui/chat/ChatCommon.kt | 14 | 1 | 0 | 1 | 0 | 1 | 1 | 1 | 0 | 13 | 0 | 0 | 0 | 0 | 0 | 0 |
| ui/chat/ChatInput.kt | 21 | 0 | 0 | 0 | 0 | 0 | 1 | 2 | 0 | 36 | 0 | 0 | 0 | 5 | 0 | 0 |
| ui/chat/ChatListScreen.kt | 34 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 36 | 0 | 0 | 1 | 5 | 0 | 0 |
| ui/chat/ChatLogic.kt | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| ui/chat/ChatMessages.kt | 20 | 0 | 0 | 0 | 0 | 0 | 2 | 6 | 1 | 79 | 0 | 0 | 2 | 3 | 0 | 1 |
| ui/chat/ChatQuickBar.kt | 16 | 0 | 0 | 0 | 0 | 1 | 0 | 4 | 0 | 45 | 0 | 0 | 0 | 0 | 0 | 3 |
| ui/chat/ChatScreen.kt | 57 | 31 | 0 | 7 | 0 | 0 | 0 | 0 | 3 | 25 | 0 | 0 | 1 | 5 | 0 | 6 |
| ui/chat/ChatSearch.kt | 6 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 9 | 0 | 0 | 1 | 5 | 0 | 0 |
| ui/chat/MarkdownView.kt | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 2 | 1 | 26 | 0 | 0 | 0 | 0 | 0 | 0 |
| ui/chat/NewChatSheet.kt | 12 | 0 | 0 | 0 | 0 | 1 | 0 | 1 | 0 | 21 | 0 | 0 | 0 | 0 | 0 | 0 |
| ui/chat/SkillPickerSheet.kt | 8 | 0 | 0 | 0 | 0 | 1 | 0 | 2 | 0 | 20 | 0 | 0 | 0 | 1 | 0 | 0 |
| ui/chat/SlashPalette.kt | 7 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 23 | 0 | 0 | 0 | 1 | 0 | 0 |
| ui/chat/StarredScreen.kt | 8 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 9 | 0 | 0 | 0 | 1 | 0 | 0 |
| ui/chat/SyntaxHighlight.kt | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| ui/components/EmptyState.kt | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 8 | 0 | 0 | 2 | 0 | 0 | 0 |
| ui/components/PixelButton.kt | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 13 | 0 | 0 | 1 | 5 | 0 | 1 |
| ui/components/PixelIcon.kt | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 1 | 6 | 0 | 1 |
| ui/components/PixelPanel.kt | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 3 | 0 | 0 | 0 | 0 | 0 | 0 |
| ui/components/PixelText.kt | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 2 | 0 | 0 | 1 | 0 | 0 | 0 |
| ui/components/PocketChip.kt | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 7 | 0 | 0 | 1 | 0 | 0 | 0 |
| ui/components/PocketDialog.kt | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 10 | 0 | 0 | 1 | 0 | 0 | 0 |
| ui/components/PocketSwitchRow.kt | 0 | 0 | 0 | 0 | 0 | 0 | 2 | 0 | 0 | 6 | 0 | 0 | 0 | 0 | 0 | 0 |
| ui/components/PocketTextField.kt | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 6 | 0 | 0 | 1 | 2 | 0 | 0 |
| ui/components/PocketTopBar.kt | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 9 | 0 | 0 | 1 | 1 | 0 | 0 |
| ui/components/SectionHeader.kt | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 9 | 0 | 0 | 1 | 0 | 0 | 1 |
| ui/components/TypingIndicator.kt | 1 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 2 | 0 | 0 | 1 | 2 | 0 | 0 |
| ui/hermes/HermesDocs.kt | 32 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 13 | 0 | 0 | 0 | 2 | 0 | 0 |
| ui/hermes/HermesScreen.kt | 13 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 11 | 0 | 0 | 0 | 0 | 0 | 0 |
| ui/hermes/HermesTabs.kt | 43 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 23 | 0 | 0 | 0 | 1 | 0 | 4 |
| ui/hermes/TerminalScreen.kt | 15 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 10 | 0 | 0 | 1 | 8 | 0 | 0 |
| ui/home/HomeParts.kt | 2 | 0 | 0 | 0 | 0 | 0 | 2 | 0 | 0 | 21 | 0 | 0 | 0 | 0 | 0 | 0 |
| ui/home/HomeScreen.kt | 35 | 0 | 0 | 1 | 0 | 0 | 3 | 0 | 3 | 36 | 0 | 0 | 1 | 3 | 0 | 0 |
| ui/pixel/Avatars.kt | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| ui/pixel/Fire.kt | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| ui/pixel/HearthScene.kt | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| ui/pixel/Mascot.kt | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| ui/pixel/PixelImage.kt | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| ui/routines/RoutineEditorScreen.kt | 63 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 15 | 0 | 0 | 0 | 5 | 0 | 0 |
| ui/routines/RoutineUiParts.kt | 15 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 3 | 0 | 0 | 0 | 0 | 0 | 0 |
| ui/routines/RoutinesScreen.kt | 31 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 27 | 0 | 0 | 0 | 1 | 0 | 0 |
| ui/settings/AppearanceSection.kt | 28 | 0 | 0 | 0 | 0 | 0 | 0 | 2 | 0 | 39 | 0 | 0 | 0 | 0 | 0 | 0 |
| ui/settings/BackupSection.kt | 20 | 7 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| ui/settings/BehaviourSection.kt | 23 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 0 |
| ui/settings/CtlUi.kt | 7 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 13 | 0 | 0 | 0 | 0 | 0 | 0 |
| ui/settings/ProviderSection.kt | 44 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 3 | 0 | 0 | 0 | 0 | 0 | 0 |
| ui/settings/RuntimeSection.kt | 32 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 10 | 0 | 0 | 0 | 0 | 0 | 0 |
| ui/settings/SettingsScreen.kt | 31 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 9 | 0 | 0 | 0 | 0 | 0 | 3 |
| ui/settings/UsageSection.kt | 31 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 10 | 0 | 0 | 1 | 2 | 0 | 0 |
| ui/settings/VoiceSoundSection.kt | 34 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 9 | 0 | 0 | 0 | 0 | 0 | 0 |
| ui/theme/Appearance.kt | 0 | 6 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| ui/theme/PocketTheme.kt | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 5 | 0 | 5 | 0 | 0 | 0 | 0 | 0 | 0 |
| voice/TtsTextCleaner.kt | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| voice/VoiceEngine.kt | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| voice/VoicePrefs.kt | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |


## A. Every Toast.makeText call site (12 lines, `rg -n 'Toast\.makeText'`)

Each call is a direct `Toast.makeText`. Two sites take the text as a parameter: `ui/routines/RoutineUiParts.kt:120` (in `showToast`, line 119) and `ui/settings/CtlUi.kt:205` (in `toast`, line 204).

| file:line | message expression |
|---|---|
| ui/AppRoot.kt:296 | `ctx.getString(R.string.ui_attach_failed, e.message ?: "share")`. English exception text inside a localized string |
| ui/chat/ChatCommon.kt:241 | `ctx.getString(R.string.ui_chat_copied)` (only below API 33) |
| ui/chat/ChatScreen.kt:300 | `ctx.getString(R.string.ui_voice_unavailable)` |
| ui/chat/ChatScreen.kt:382 | `ctx.getString(R.string.ui_fork_created)` |
| ui/chat/ChatScreen.kt:395 | `ctx.getString(R.string.ui_attach_failed, e.message ?: e.javaClass.simpleName)`. English exception text on screen |
| ui/chat/ChatScreen.kt:454-458 | multi-line call: `if (failure == null) ctx.getString(R.string.ui_export_saved) else ctx.getString(R.string.ui_export_failed, failure)` |
| ui/chat/ChatScreen.kt:525 | `R.string.ui_send_not_accepted` (passed directly, no getString) |
| ui/chat/ChatScreen.kt:967 | `failedLabel + (e.message ?: "")`. English exception text after a label |
| ui/chat/ChatScreen.kt:1014 | `ctx.getString(R.string.ui_attach_failed, e.message ?: "camera")` |
| ui/home/HomeScreen.kt:274 | `R.string.ui_send_not_accepted` |
| ui/routines/RoutineUiParts.kt:120 | `message` parameter of `showToast` |
| ui/settings/CtlUi.kt:205 | `message` parameter of `toast` |

Callers of the two wrappers (`rg -n '\b(showToast|toast)\('`; a few of these lines also hold other calls):
`ui/bots/BotsScreen.kt:136` (addedMsg); `ui/bots/BotShareUi.kt:115, 116, 126, 130, 219` (savedMsg, failedMsg, tooBigMsg, a getString of ctl_bots_imported); `ui/bots/BotEditorScreen.kt:186, 356` (duplicatedMsg); `ui/routines/RoutineEditorScreen.kt:165` (savedMsg); `ui/routines/RoutinesScreen.kt:149` (startedMsg); `ui/hermes/HermesTabs.kt:105, 119, 120` (includes ctl_skill_delete_failed); `ui/hermes/TerminalScreen.kt:117` and `ui/hermes/HermesTabs.kt:227, 278` (copy buttons); `ui/settings/AppearanceSection.kt:158` (switchedMsg).

## B. Every dialog and bottom sheet call site (21 sites, plus the wrapper)

Grep: `rg -n '\bDialog\(|ModalBottomSheet\(|PocketDialog\('`. The wrapper declaration `PocketDialog.kt:37` and its internal `Dialog(` at `PocketDialog.kt:45` are not call sites. The 5 `ModalBottomSheet(` are marked MBS. The meaning is taken from the composable and the string key it uses (strings are in the `strings_*.xml` files).

| file:line | composable (line) | asks or shows |
|---|---|---|
| ui/AppRoot.kt:358 | IncomingShareDialog (346) | `PocketDialog`: picker for text shared into Agirî (`ui_share_target_title`) |
| ui/chat/AttachmentUi.kt:70 | AttachSheet (63) | MBS: the attach menu (camera, gallery, document) |
| ui/chat/AttachmentUi.kt:195 | ImagePreviewDialog (192) | `PocketDialog`: full preview of an attached image, titled with its file name |
| ui/chat/ChatCommon.kt:281 | ActionSheet (279) | MBS: generic action list with an optional title |
| ui/chat/ChatCommon.kt:318 | ConfirmDialog (314) | `PocketDialog`: generic confirmation (title and body from the caller) |
| ui/chat/ChatCommon.kt:332 | RenameDialog (330) | `PocketDialog`: rename a chat or bot (`ui_dlg_rename_title`) |
| ui/chat/ChatScreen.kt:975 | ChatBody (149) | `PocketDialog`: select-text dialog for a message (`ui_chat_select_title`) |
| ui/chat/ChatScreen.kt:1056 | ChatBody (149) | `PocketDialog`: confirm before a code block runs in the terminal (`ui_run_term_title`) |
| ui/chat/ChatScreen.kt:1093 | MembersDialog (1088) | `PocketDialog`: group members and mode (`ui_chat_members_title`) |
| ui/chat/ChatQuickBar.kt:187 | ChatControlsSheet (175) | MBS: Chat Controls (mode, model, thinking-effort overrides) |
| ui/chat/NewChatSheet.kt:71 | NewChatSheet (59) | MBS: start a 1:1 or group chat by choosing bots |
| ui/chat/SkillPickerSheet.kt:108 | SkillPickerSheet (65) | MBS: searchable list of Hermes skills |
| ui/bots/BotShareUi.kt:225 | parse (153), inside BotShareUi | `PocketDialog`: bot import error; shows the message text from `BotShare` (English, see C) |
| ui/hermes/HermesDocs.kt:201 | DocEditor (110) | `PocketDialog`: conflict when a Hermes document changed on disk (`ctl_conflict_title`, `ctl_conflict_body`) |
| ui/hermes/HermesDocs.kt:211 | DocEditor (110) | `PocketDialog`: add or edit a memory entry (`ctl_entry_add` / `ctl_entry_edit`) |
| ui/hermes/HermesTabs.kt:195 | NewSkillDialog (189) | `PocketDialog`: create a skill (name, category, error text) |
| ui/routines/RoutineEditorScreen.kt:414 | ConfirmDialog (410, private) | `PocketDialog`: generic confirmation with the caller's title and text |
| ui/settings/ProviderSection.kt:200 | PasteDialog (198) | `PocketDialog`: paste a key or account text; hidden unless "Show text" is on (KDoc at line 193) |
| ui/settings/CtlUi.kt:154 | ConfirmDialog (150, internal) | `PocketDialog`: generic confirmation (title and text from the caller) |
| ui/settings/SettingsScreen.kt:131 | SettingsScreen (61) | `PocketDialog`: text panel titled `ctl_set_diagnostics` |
| ui/settings/SettingsScreen.kt:207 | ImportResultDialog (191) | `PocketDialog`: result of an import, success or failure body |
| ui/components/PocketDialog.kt:45 | PocketDialog (37) | the wrapper's own bare `Dialog(` (the single match for column 5) |

Count: 16 `PocketDialog(` call sites + 5 `ModalBottomSheet(` call sites = 21 (plus the wrapper). Column 5 literal `Dialog(` = 1 (inside the wrapper). `AlertDialog(` and `BasicAlertDialog(` = 0.

## C. Hard-coded user-visible English in non-UI code

Method: every quoted literal in `data/`, `service/`, `routines/`, `hermes/`, `runtime/`, `diag/`, `voice/`, `api/`, `util/` and the top-level files, filtered to literals with a capital or a space (375 lines, saved as `scratchpad/w0/nonui_literals.txt`). The seven named files were checked against all of their quoted literals (`grep -nE '"[^"]{3,}"'`), not only the filtered list. "On screen" means chat text, notification, terminal line, toast, state text on Home or Settings, or copyable diagnostics. Where the UI call site was not followed, the line says "likely" or "not traced".

### Named files

- `data/ChatEngine.kt` (chat NOTE and ERROR texts, all on screen):
  - 209, 231, 261, 281: "This chat has no bots (they were deleted). Add a bot to the chat first." (ERROR)
  - 233: "Loop mode: pass 1/$totalPasses" (NOTE)
  - 237: "Loop mode: pass $pass/$totalPasses — refining…" (NOTE)
  - 304: "Could not send your answer to Hermes: ${r.describe()}" (ERROR)
  - 322: "Internal error: ${e.javaClass.simpleName}: ${e.message}" (ERROR)
  - 375: "Hermes is not running. Start it from the home screen, wait until it says Running, then try again." (ERROR)
  - 389: "Hermes could not start. Open the home screen to see why." (ERROR)
  - 400: "Bot not found (it was deleted)." (ERROR)
  - 406-407: "The daily token budget ($limit tokens) is reached, so this message was not sent to the model. Raise or turn off the limit under Settings > Usage, or try again tomorrow." (ERROR)
  - 423-424: "The model "…" set for this chat or bot belongs to another provider, so the default model of the current provider is used instead. Change it in the chat controls or the bot editor." (NOTE)
  - 592-593: "Hermes asks permission before it runs this and is waiting for an answer:" plus the command or description (NOTE)
  - 602: "The answer was cut off (length limit reached)." (NOTE)
  - 605: "The run ended abnormally: finish_reason=…" (ERROR)
  - 628-629: "Daily token budget reached: N tokens used today. Hermes keeps answering; change the limit under Settings > Usage." (NOTE)
  - 648: "The bot returned an empty reply." (NOTE)
  - 661: "HTTP ${outcome.code}: body" (ERROR, technical)
  - Not on screen: 239-240 (loop prompt sent to the model); 103 "Former participant" (name in the prompt).
- `service/ReplyNotifier.kt`: no English literal reaches a notification. Its 29 texts come from `R.string`. Its remaining literals are tags, channel ids and `agiri://` URIs (347, 418, 461-475), which are not shown.
- `service/Presence.kt`: no English literal. Its hits are an ellipsis (53) and the approval keys `"once"` / `"deny"` (34, 89-90), which are keys, not text (where they are labelled was not traced).
- `hermes/TerminalSession.kt` (terminal INFO lines, on screen):
  - 232: "A command is still running. Wait for it or cancel it first."
  - 242: "The Hermes runtime is not installed yet. Start Hermes once from the home screen so it can set itself up, then try again."
  - 250: "[cancelled]"
  - 251: "[exit code $code]"
  - 255: "Could not run the command: ${e.javaClass.simpleName}: ${e.message}"
  - 236: "$ " and "> " prompt symbols (not prose).
- `routines/RoutineScheduler.kt`: no literal reaches the screen. Its 9 notification texts come from `R.string`. The other literals are `AppLog` lines (for example 215 "routine … skipped: N min late", 259 "routine … sent to chat") and lock names (156, 160), which are logs only.
- `data/BackupManager.kt` (BackupException texts, likely shown on the backup screen):
  - 90: "A chat is answering right now. Wait for it to finish, then try again."
  - 91: "A terminal command is running. Wait for it to finish, then try again."
  - 104: "Could not unpack the backup: …"
  - 115: "Could not save a safety copy of your current data first, so nothing was changed (…)."
  - 130: "The restore stopped halfway (…). Restart the app. A copy of your previous data is kept inside the app as backup/pre-restore.zip."
  - 309-333 (reject reasons, shown through 514 as "This backup cannot be used: <reason>."): "an entry name is empty or too long" (309), "an entry name is not a plain relative path" (310), "an entry is nested too deeply" (312), "an entry name leaves its folder" (313), "an entry name is not allowed" (315), "it holds an unknown folder" (328), and the other reasons in that block.
  - 378: "There are too many files to back up (N)."
  - 400, 545, 569: "Everything together is larger than N MiB …" / "The backup is larger than N MiB once unpacked …"
  - 471: "A settings file in the backup is damaged."
  - 512: "The backup has more than N entries."
  - 514, 521, 525, 533, 544: "This backup cannot be used: …" (an entry appears twice; its manifest is too large; an entry leaves its folder; a settings file is far too large)
  - 556: "Could not read the backup file (…). If the phone is almost full, free some space and try again."
  - 558, 585, 587: "This file is not an Agirî backup (it has no manifest)." / "… (its manifest is damaged)." / "This file is not an Agirî backup."
  - 588: "This backup was made by a newer version of Agirî. Update the app first."
- `runtime/RuntimeManager.kt` (state text on Home and Settings, likely; terminal output, on screen):
  - 227 "Could not delete the runtime: …"; 277 "This build contains no rootfs (stub build). Install the APK produced by CI."; 285-286 "Cannot read …. The runtime was NOT reinstalled, so nothing was deleted. Fix the file's permissions, or use Delete runtime files to reinstall from scratch."
  - 295 `Extracting(0f, "preparing")`; 302 "Not enough free storage: need about … free …"; 320 `Extracting(frac, "N entries")`; 332 `Stopped("extraction cancelled")`; 338 "Extraction failed: …"
  - 373 `Starting("preparing")`; 376 "Stub build: no proot binary bundled."; 378 "proot binary missing: …"; 381 "Invalid configuration: … (fix it under Setup)"; 405 `Starting("launching proot")`
  - 429 "Hermes did not become healthy within 20 minutes (process still alive; see runtime log). Press Stop, then Start."; 439 "Port … is answered by a process that is not our Hermes (another app?)…"; 442 "The API on port … answers /health, but our Hermes never announced that port in its own output…"; 446 `Starting("API answers, waiting for Hermes to announce port …")`; 453 "Hermes is up but rejected our API key (HTTP 401): …"
  - 465 `Starting("still starting (N s, process alive, no API yet)")`, matched by English text in `ui/settings/RuntimeSection.kt:78`; 466 `Starting("waiting for Hermes API (N s)")`; 517 `Stopped("stopped")`; 519 "Hermes exited unexpectedly: …" plus the exit hint; 565 `Stopped("stopped (reason)")`
  - 626-627 and 642-666 self-test report lines (copyable diagnostics, not traced further)
  - 694 "This build contains no proot or rootfs (stub build)." and 696 "The Hermes runtime is not installed yet." (terminal output, on screen); 713 "Could not start the shell: …" (terminal output)
- `runtime/RuntimeState.kt` (state labels and exit reasons, likely shown): 38-39 "exit value N (signal …), uptime Ns, stop requested by user"; 44-54 signal names and hints, for example 50 "SIGKILL: Android phantom-process killer / memory limiter, or force-stop. Check the historical process exit reasons section.", 51 "SIGSYS: seccomp killed a syscall. Enable 'Disable proot seccomp filter' in Debug and restart.", 53 "command not found (launcher or hermes missing).", 54 "not executable (proot binary or script not executable on this device)."; 13-18 `label()` names (diagnostics).

### Other non-UI files

- `data/BotShare.kt`: 45, 46, 47, 48, 50, 51, 52, 53, 54, 58, 60, 61, 63, 65 are import error texts. Line 48 reads "This is not an Agiri bots file." (no î; the rest of the app writes Agirî). They reach the screen only through `ui/bots/BotShareUi.kt:62-70`, which matches them by English text (see D). A text that does not match is shown as it is.
- `data/BotPresets.kt` (bot names and taglines, shown in English on the Bots list, not translated): 17-18 "Hermes" / "The agent itself: tools, memory, skills"; 27-28 "Rebel" / "Blunt, witty, allergic to nonsense"; 49-50 "Coder" / "Senior engineer: code first, proof over opinion"; 68-69 "Dengbêj" / "Kurdish oral epics, told by the fire"; 86-87 "Teacher" / "Patient explanations, one step at a time"; 105-106 "Translator" / "Kurdish, Turkish, English, French"; 121-122 "Critic" / "Devil's advocate: finds the crack"; 139-140 "Planner" / "Calm plans, clear next steps". The system prompts (23-149) are English and are sent to the model, not shown.
- `voice/VoicePrefs.kt:18-27` (voice names, likely shown in the voice picker): "Roza (F1)", "Nergiz (F2)", "Zilan (F3)", "Sarya (F4)", "Berfin (F5)", "Kawa (M1)", "Aram (M2)", "Baran (M3)", "Diyar (M4)", "Cem (M5)".
- `voice/VoiceEngine.kt`: 178-180 demo sentence (spoken, not shown); 186 "Agirî" default name; 190-211 spoken greetings per voice style in Turkish, French and English (spoken, not shown); 459 "System TTS error (…)", 469 "System TTS unavailable", 518 "System TTS init failed (…)" (stored in `_lastError`, likely shown).
- `voice/SupertonicModelStore.kt`: 104 "… downloaded incomplete (N bytes)", 206 "HTTP … without Location", 214 "HTTP … downloading …", 219 "Too many redirects" (exception texts; whether the download card shows them was not traced). 191 "Agiri/1.0 (Android)" is a User-Agent (not shown).
- `data/AttachmentStore.kt`: 50 "Could not decode image", 64 "Could not encode JPEG", 100 "File is larger than N MiB", 105 "Could not open selected document". These reach the screen through the toasts at `AppRoot.kt:296`, `ChatScreen.kt:395` and `ChatScreen.kt:1014` (see A).
- `runtime/TarExtractor.kt`: 71 "extraction cancelled", 144 "unsafe path in archive: …", 152 "path escapes extraction root: …". They are shown through `RuntimeManager.kt:338` "Extraction failed: …".
- `runtime/ServiceAccount.kt`: 16 `"client " + … + ", refresh token " + "******"` (masked text, likely shown in Settings); 87 "client_email (not an e-mail address)" (parse failure text).
- `runtime/ProviderConfig.kt`: 247 "(none)" and "(invalid, not shown, N chars)" (config summary); 353 "mode=… provider=… … (provider default)" (summary line); 273-306 comments written into the generated `.env` (not shown).
- `MainViewModel.kt`: 71 "SA project …, <masked email>" (summary, likely on screen); 215 `ClipData` label "Agirî diagnostics" (copied); 226 share subject "Agirî diagnostics" (share sheet).
- `diag/Diagnostics.kt` (copyable report, English only): 64 "Agirî diagnostics"; 43 "NOTE: log sections shortened to fit the clipboard …"; section titles at 82-235 ("Device", "Runtime state", "Settings and provider configuration", "Child process exits", "Historical process exit reasons (ActivityManager)", "Launch command and environment", "Self-test output …", "Power and network", "Memory and storage", "Bundled runtime", "App log (tail …)", "HERMES_HOME/logs", "Recent API requests/responses", "Hermes .env (secrets redacted)", "Hermes config.yaml"); 245-248 exit reason names.
- `hermes/HermesHome.kt`: 295 default skill description written into SKILL.md: "Use when ... (say in one sentence when Hermes should use this skill)." (likely in the Hermes skill editor); 63 " (not created yet)" (file list, likely); 102, 109, 121-127, 146-166 `require(...)` and exception texts (shown only if the Hermes screen prints them: not traced).
- `data/SlashCommands.kt`: 24 `"/forge <bot idea>"` (usage text, likely in the palette); 65-75 "ACTIVE MODE: …" (sent to the model; whether the banner uses these or resources was not traced).
- `api/HermesApi.kt:63` "Analyze the attached image(s)." (sent to the model when an image has no text; not shown). 163-164 request description (log).
- `data/LegacyBotPrompts.kt`, `data/BotForge.kt:18-26`, `data/Transcript.kt:21-34`: prompt texts sent to the model, not shown. `Transcript.kt:29-30` ("the user") and `Transcript.kt:103` ("Former participant") are labels inside those prompts.
- `data/UsageStore.kt`, `runtime/Settings.kt`, `runtime/UserState.kt`, `runtime/WakeLocks.kt` (tag "PocketAgent:work"), `runtime/ProcTree.kt` (marker), `runtime/PortAnnouncement.kt`, `runtime/TtsTextCleaner.kt`: no user-visible English.

## D. Hard-coded user-visible English inside ui/ outside string resources

Method: `rg` for quoted literals with a capital or a space under `ui/` (65 lines, saved as `scratchpad/w0/ui_literals.txt`), filtered to what is shown or spoken. Animation `label = "…"` names are not visible and are not listed.

- `ui/settings/BackupSection.kt:81` "The chosen location cannot be written." and `:104`, `:123` "The chosen file cannot be read." (BackupException texts, shown on the backup screen, likely)
- `ui/settings/AppearanceSection.kt:216-221` theme names "Newroz", "Zagros", "Semender", "Nergiz", "Kawa", "Roj" (shown, not translated)
- `ui/settings/AppearanceSection.kt:246`, `:249` preview bubbles "Hi!" and "Hello"
- `ui/settings/AppearanceSection.kt:276-278` and `ui/settings/SettingsScreen.kt:184-186` language names "English", "Türkçe", "Français" (endonyms; the English names are hard-coded in both files)
- `ui/settings/VoiceSoundSection.kt:79` "Supertonic 3 INT8 (99M)" (model name)
- `ui/settings/SettingsScreen.kt:158` `ap.theme.name.lowercase().replaceFirstChar { … }` (theme name shown in Settings, from the English enum name)
- `ui/chat/ChatScreen.kt:617`, `:619` "MEMORY.md (…):" and "USER.md (…):" (local note text in the chat)
- `ui/chat/ChatScreen.kt:630` `ThemeId.entries.joinToString(", ") { it.name.lowercase() }` (theme list in a note)
- `ui/chat/ChatLogic.kt:116-120` default labels "You", "Tool", "Error", "Bot" (used in the Markdown export when the caller does not pass localized ones; not traced)
- `ui/hermes/TerminalScreen.kt:61-62` example commands as suggestion chips ("hermes --help", "uname -a", …) (commands, not prose)
- `ui/hermes/TerminalScreen.kt:117` clipboard label "Agirî terminal"
- `ui/chat/ChatCommon.kt:239` clipboard label "Agirî" (not visible)
- `ui/chat/SlashPalette.kt:148-151` chip labels "/plan", "/ultracode", "/forge", "/skills" (commands)
- `ui/settings/RuntimeSection.kt:78` `step.startsWith("still starting")`: logic that depends on an English string made in `runtime/RuntimeManager.kt:465`
- `ui/bots/BotShareUi.kt:62-70` English exception texts matched in code (`m == "The file is empty."`, `m.contains("not valid JSON")` and so on), mapped to a `BotImportProblem` enum (see C, `data/BotShare.kt`)
- `ui/components/PixelButton.kt:178` and `ui/bots/BotEditorScreen.kt:408-409` accessibility labels built from the English enum name (`icon.name.lowercase()`, `id.name.lowercase().replaceFirstChar`): read aloud in English
- `ui/chat/ChatScreen.kt:634` note text with `pick.name.lowercase()` (theme name)
- `ui/pixel/SpriteBank.kt:134-218` asset-parser errors ("bad frame duration", "cannot parse line", "SpriteBank.init(context) has not been called yet"): developer-only
- `ui/Nav.kt:69` `error("No Navigator provided: …")`: developer-only
- `ui/chat/ChatInput.kt:181` "@" and `ui/routines/RoutineEditorScreen.kt:384` ":" are symbols
- `ui/chat/SyntaxHighlight.kt` keyword lists: not UI text

## E. String resource files

Counts from `grep -c` for `<string ` and `<plurals ` (identical in `values-tr` and `values-fr`):

| file | `<string>` | `<plurals>` |
|---|---:|---:|
| values/strings.xml | 112 | 0 |
| values/strings_chat.xml | 283 | 0 |
| values/strings_control.xml | 386 | 0 |
| values/strings_design.xml | 2 | 0 |
| values/strings_presence.xml | 20 | 1 |
| values/strings_routines.xml | 111 | 0 |
| values/strings_surface.xml | 11 | 0 |
| **total** | **925** | **1** |

The same seven file names exist in `values-tr` and `values-fr` with the same counts. Key check (sorted names of `<string>` and `<plurals>`, `comm` in both directions, for each of the 7 files): no key is missing in `values-tr` or `values-fr`, and none is extra in either. Left/right margin, padding or gravity attributes in `app/src/main/res`: 0.

## F. Case changes in code (43 lines, 56 occurrences)

Grep: `rg -n '\.uppercase\(|\.lowercase\(|\.capitalize\(|replaceFirstChar'`. The complete list of lines follows.

```
  app/src/main/java/dev/pocketagent/app/hermes/HermesHome.kt:95:        return out.sortedWith(compareBy({ it.category.lowercase() }, { it.name.lowercase() }))
  app/src/main/java/dev/pocketagent/app/hermes/HermesHome.kt:297:        val title = name.split('-', '_').filter { it.isNotEmpty() }.joinToString(" ").replaceFirstChar { it.uppercase() }
  app/src/main/java/dev/pocketagent/app/diag/Diagnostics.kt:110:            ).joinToString(" ") { "${it.name.lowercase()}=${if (rt.secrets.hasProviderKey(it)) "present" else "absent"}" }
  app/src/main/java/dev/pocketagent/app/voice/VoiceEngine.kt:176:        val code = appLang.ifBlank { Locale.getDefault().language }.lowercase(Locale.ROOT).take(2)
  app/src/main/java/dev/pocketagent/app/voice/VoiceEngine.kt:187:        val code = appLang.ifBlank { Locale.getDefault().language }.lowercase(Locale.ROOT).take(2)
  app/src/main/java/dev/pocketagent/app/voice/TtsTextCleaner.kt:96:        val pref = appLang.lowercase().take(2)
  app/src/main/java/dev/pocketagent/app/voice/TtsTextCleaner.kt:106:        val words = sample.lowercase().split(Regex("[^\\p{L}]+")).filter { it.length >= 2 }
  app/src/main/java/dev/pocketagent/app/runtime/ProviderConfig.kt:232:        val scheme = uri.scheme?.lowercase() ?: return null
  app/src/main/java/dev/pocketagent/app/runtime/ProviderConfig.kt:235:        val host = uri.host?.lowercase() ?: return null
  app/src/main/java/dev/pocketagent/app/runtime/ProviderConfig.kt:344:                val host = if (url != null && url.startsWith("https://")) URI(url).host?.lowercase() else null
  app/src/main/java/dev/pocketagent/app/data/BackupManager.kt:291:        val s = segment.lowercase()
  app/src/main/java/dev/pocketagent/app/data/BackupManager.kt:297:        val lower = fileName.lowercase()
  app/src/main/java/dev/pocketagent/app/data/ChatEngine.kt:57:        val m = model.trim().lowercase()
  app/src/main/java/dev/pocketagent/app/data/ChatEngine.kt:575:                    if (ev.status.lowercase() != "running") {
  app/src/main/java/dev/pocketagent/app/data/AttachmentStore.kt:164:        val ext = safeName.substringAfterLast('.', "").lowercase()
  app/src/main/java/dev/pocketagent/app/data/AttachmentStore.kt:234:    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
  app/src/main/java/dev/pocketagent/app/data/BotForge.kt:71:        val avatarRaw = obj.optString("avatar", "NEWT").trim().uppercase()
  app/src/main/java/dev/pocketagent/app/data/BotForge.kt:84:        return when (voiceCode.trim().uppercase()) {
  app/src/main/java/dev/pocketagent/app/data/BotForge.kt:92:    internal fun parseVoiceStyle(raw: String): BotVoiceStyle = when (raw.trim().lowercase()) {
  app/src/main/java/dev/pocketagent/app/data/SlashCommands.kt:48:        val cmd = m.groupValues[1].lowercase()
  app/src/main/java/dev/pocketagent/app/data/SlashCommands.kt:57:        val prefix = input.drop(1).lowercase()
  app/src/main/java/dev/pocketagent/app/data/Transcript.kt:175:        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
  app/src/main/java/dev/pocketagent/app/ui/bots/BotEditorScreen.kt:408:                            .clickable(onClickLabel = id.name.lowercase().replaceFirstChar { it.uppercase() }) { onPick(id) }
  app/src/main/java/dev/pocketagent/app/ui/bots/BotEditorScreen.kt:409:                            .semantics { contentDescription = id.name.lowercase().replaceFirstChar { it.uppercase() } },
  app/src/main/java/dev/pocketagent/app/ui/bots/BotShareUi.kt:97:        bots[0].name.lowercase().map { if (it in 'a'..'z' || it in '0'..'9') it else '-' }.joinToString("").trim('-').replace(Regex("-+"), "-").take(40)
  app/src/main/java/dev/pocketagent/app/ui/pixel/Avatars.kt:28:internal fun AvatarId.spriteName(): String = "avatar_" + name.lowercase()
  app/src/main/java/dev/pocketagent/app/ui/components/PixelButton.kt:178:    val label = contentDescription ?: icon.name.lowercase()
  app/src/main/java/dev/pocketagent/app/ui/components/PixelIcon.kt:29:    val spriteName: String get() = "icon_" + name.lowercase()
  app/src/main/java/dev/pocketagent/app/ui/chat/ChatMessages.kt:470:    val status = msg.toolStatus.orEmpty().lowercase()
  app/src/main/java/dev/pocketagent/app/ui/hermes/HermesTabs.kt:163:                val q = query.trim().lowercase()
  app/src/main/java/dev/pocketagent/app/ui/hermes/HermesTabs.kt:165:                    it.name.lowercase().contains(q) || it.description.lowercase().contains(q) || it.category.lowercase().contains(q)
  app/src/main/java/dev/pocketagent/app/ui/chat/ChatLogic.kt:136:            Role.TOOL -> appendLine("> ${labels.tool} `${m.toolName ?: labels.tool.lowercase()}`: ${m.text}")
  app/src/main/java/dev/pocketagent/app/ui/chat/ChatScreen.kt:272:        when (ap.language.lowercase().take(2)) {
  app/src/main/java/dev/pocketagent/app/ui/chat/ChatScreen.kt:630:                    val names = ThemeId.entries.joinToString(", ") { it.name.lowercase() }
  app/src/main/java/dev/pocketagent/app/ui/chat/ChatScreen.kt:634:                    engine.addLocalNote(convId, ctx.getString(R.string.ui_cmd_theme_switched, pick.name.lowercase().replaceFirstChar { ch -> ch.uppercase() }))
  app/src/main/java/dev/pocketagent/app/ui/chat/ChatScreen.kt:669:                val spec = arg.lowercase()
  app/src/main/java/dev/pocketagent/app/ui/settings/SettingsScreen.kt:158:            ap.theme.name.lowercase().replaceFirstChar { it.uppercase() }
  app/src/main/java/dev/pocketagent/app/ui/chat/ChatQuickBar.kt:255:                    val label = effortLabelRes(ef)?.let { stringResource(it) } ?: ef.replaceFirstChar { it.uppercase() }
  app/src/main/java/dev/pocketagent/app/ui/chat/ChatQuickBar.kt:301:private fun effortLabelRes(effort: String): Int? = when (effort.trim().lowercase()) {
  app/src/main/java/dev/pocketagent/app/ui/components/SectionHeader.kt:37:    val label = remember(text, locale) { text.uppercase(locale) }
  app/src/main/java/dev/pocketagent/app/ui/chat/SyntaxHighlight.kt:40:        val l = lang.trim().lowercase()
  app/src/main/java/dev/pocketagent/app/ui/chat/SyntaxHighlight.kt:51:        val l = lang.trim().lowercase()
  app/src/main/java/dev/pocketagent/app/ui/chat/SyntaxHighlight.kt:72:        val isJsonOrYaml = lang.trim().lowercase() in setOf("json", "yaml", "yml")
```
