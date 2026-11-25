# Survey: package name and Android surface (dev.pocketagent.app)

Scope: branch ccr-bb6d7bcc-9eojyu, working tree clean at the start. Read-only survey: no tracked file was changed, Gradle was not run, no phone was used. Every line number below was taken from a command run for this survey. Git-tracked files only (`git ls-files`), so `docs/` and `dist/` are excluded as asked, except that the root `AGENTS.md` and `README.md` are included because they are outside `docs/`.

Reading order: A (every occurrence), B (manifest), C (res/xml), D (names built at run time), E (navigator and deep links), F (build and CI), G (name-based lookups a shrinker could break).

---

## A. Every occurrence of the literal `dev.pocketagent.app`

Totals (from the grep run):

- Total occurrences in tracked files outside docs/ and dist/: **1181**.
  - In tracked code, res XML, Gradle, tools/, runtime/ and rootfs/ files, and tests: 1167. The files with hits are the .kt sources (main and test), `app/build.gradle.kts`, `app/src/main/res/xml/shortcuts.xml`, `tools/local-build.ps1`, `runtime/pocketagent-proot.sh`, `runtime/test-launcher.sh` and `rootfs/smoke/smoke-test.sh`. `.github/workflows/build.yml`, the manifest and the other res XML files contain no literal package name (the manifest uses `${applicationId}`).
  - In root `AGENTS.md` (13) and `README.md` (1): 14.
- Of the 1167 code and script lines: **167 are `package` declarations** and **885 are `import` lines**, all in .kt files. Their rows are not listed, as asked.
- The remaining **115 code/script lines** and the **14 root-doc lines** are listed below. The Kind column is a short label assigned by pattern on the line text and path (for example: namespace, applicationId, intent action, component name, process name, fully qualified class reference, comment, test literal, documentation text). Rows that are fully qualified class references in code are inline calls such as `dev.pocketagent.app.ui.chat.currentLocale()`, not imports.

Kind counts for the 115 code/script rows plus 14 doc rows are printed in the table footer.

| File | Line | Line text (trimmed) | Kind |
|---|---|---|---|
| app/build.gradle.kts | 77 | `namespace = "dev.pocketagent.app"` | namespace |
| app/build.gradle.kts | 83 | `applicationId = "dev.pocketagent.app"` | applicationId |
| app/src/main/java/dev/pocketagent/app/AppLinks.kt | 22 | `const val ACTION_OPEN_CHAT = "dev.pocketagent.app.OPEN_CHAT"` | intent action |
| app/src/main/java/dev/pocketagent/app/AppLinks.kt | 23 | `const val ACTION_NEW_CHAT = "dev.pocketagent.app.NEW_CHAT"` | intent action |
| app/src/main/java/dev/pocketagent/app/AppLinks.kt | 24 | `const val ACTION_TERMINAL = "dev.pocketagent.app.TERMINAL"` | intent action |
| app/src/main/java/dev/pocketagent/app/AppLinks.kt | 25 | `const val ACTION_ROUTINES = "dev.pocketagent.app.ROUTINES"` | intent action |
| app/src/main/java/dev/pocketagent/app/AppLinks.kt | 26 | `const val ACTION_USAGE = "dev.pocketagent.app.USAGE"` | intent action |
| app/src/main/java/dev/pocketagent/app/MainViewModel.kt | 146 | `val bytes = dev.pocketagent.app.util.FileUtil.readLimited(s, 256 * 1024 + 1)` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/PocketApp.kt | 113 | `try { dev.pocketagent.app.ui.pixel.SpriteBank.init(this) } catch (e: Throwable) { AppLog.e("App", "sprite bank failed to load", e) }` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/RestartActivity.kt | 45 | `/** True when [processName] (e.g. `dev.pocketagent.app:restart`) is the process of [RestartActivity]. */` | process name |
| app/src/main/java/dev/pocketagent/app/diag/Diagnostics.kt | 104 | `val inputs = dev.pocketagent.app.runtime.ProviderConfig.savedInputs(s, rt.secrets)` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/diag/Diagnostics.kt | 105 | `val probs = dev.pocketagent.app.runtime.ProviderConfig.savedProblems(s, rt.secrets)` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/diag/Diagnostics.kt | 108 | `dev.pocketagent.app.runtime.AuthMode.OPENROUTER, dev.pocketagent.app.runtime.AuthMode.ANTHROPIC,` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/diag/Diagnostics.kt | 109 | `dev.pocketagent.app.runtime.AuthMode.OPENAI, dev.pocketagent.app.runtime.AuthMode.CUSTOM,` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/diag/Diagnostics.kt | 112 | `configured: ${dev.pocketagent.app.runtime.ProviderConfig.describe(inputs)}` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/diag/Diagnostics.kt | 118 | `other provider API keys: $otherKeys  custom base url: ${dev.pocketagent.app.runtime.ProviderConfig.baseUrlForReport(s.customBaseUrl)}` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/routines/RoutineScheduler.kt | 40 | `* the [dev.pocketagent.app.data.ChatEngine] (the engine wakes Hermes when it sleeps) and sets the next alarm. The answer reaches` | comment (KDoc) |
| app/src/main/java/dev/pocketagent/app/routines/RoutineScheduler.kt | 43 | `* Works when the process was dead: `PocketApp.onCreate` has run, chat data may still be loading ([dev.pocketagent.app.data.ChatRepository.loaded]);` | comment (KDoc) |
| app/src/main/java/dev/pocketagent/app/routines/RoutineScheduler.kt | 324 | `const val ACTION_ALARM = "dev.pocketagent.app.ROUTINE_ALARM"` | intent action |
| app/src/main/java/dev/pocketagent/app/service/AgentService.kt | 127 | `const val ACTION_STOP = "dev.pocketagent.app.STOP"` | intent action |
| app/src/main/java/dev/pocketagent/app/service/NotificationActionReceiver.kt | 119 | `const val ACTION_REPLY = "dev.pocketagent.app.NOTIF_REPLY"` | intent action |
| app/src/main/java/dev/pocketagent/app/service/NotificationActionReceiver.kt | 120 | `const val ACTION_MARK_READ = "dev.pocketagent.app.NOTIF_MARK_READ"` | intent action |
| app/src/main/java/dev/pocketagent/app/service/NotificationActionReceiver.kt | 121 | `const val ACTION_APPROVAL = "dev.pocketagent.app.NOTIF_APPROVAL"` | intent action |
| app/src/main/java/dev/pocketagent/app/service/NotificationActionReceiver.kt | 123 | `const val ACTION_DISMISSED = "dev.pocketagent.app.NOTIF_DISMISSED"` | intent action |
| app/src/main/java/dev/pocketagent/app/service/ReplyNotifier.kt | 284 | `Intent(appContext, dev.pocketagent.app.MainActivity::class.java)` | component class reference |
| app/src/main/java/dev/pocketagent/app/service/ReplyNotifier.kt | 311 | `Intent(appContext, dev.pocketagent.app.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),` | component class reference |
| app/src/main/java/dev/pocketagent/app/service/ReplyNotifier.kt | 361 | `appContext, REQUEST_OPEN_APP, Intent(appContext, dev.pocketagent.app.MainActivity::class.java),` | component class reference |
| app/src/main/java/dev/pocketagent/app/ui/bots/BotShareUi.kt | 51 | `/** The kinds of problem [dev.pocketagent.app.data.BotShare.decode] reports, so the UI can show them in the user's language. */` | comment (KDoc) |
| app/src/main/java/dev/pocketagent/app/ui/chat/ChatCommon.kt | 146 | `val attachment: dev.pocketagent.app.data.Attachment? = null,` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/chat/ChatCommon.kt | 153 | `fun enqueue(convId: String, text: String?, attachment: dev.pocketagent.app.data.Attachment?) {` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/chat/MarkdownView.kt | 185 | `dev.pocketagent.app.PocketApp.from(ctx).sounds.play(dev.pocketagent.app.voice.SoundCue.TAP)` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/chat/StarredScreen.kt | 108 | `members: List<dev.pocketagent.app.data.Bot?>,` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/hermes/ConsoleAnsi.kt | 28 | `* The 16 ANSI colours as the console shows them (indexes as in [dev.pocketagent.app.hermes.AnsiSpan.fg]): red, green, yellow` | comment (KDoc) |
| app/src/main/java/dev/pocketagent/app/ui/hermes/HermesScreen.kt | 173 | `private fun infoOf(home: dev.pocketagent.app.hermes.HermesHome, doc: HermesDoc, version: String): String =` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/home/HomeScreen.kt | 360 | `stringResource(R.string.ui_home_usage_today, compactTokens(todayTokens, dev.pocketagent.app.ui.chat.currentLocale())),` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/settings/BackupSection.kt | 195 | `val locale = dev.pocketagent.app.ui.chat.currentLocale()` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/settings/BehaviourSection.kt | 37 | `* [dev.pocketagent.app.runtime.PresenceSettings].` | comment (KDoc) |
| app/src/main/java/dev/pocketagent/app/ui/settings/BehaviourSection.kt | 40 | `internal fun BehaviourSection(vm: dev.pocketagent.app.MainViewModel) {` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/settings/SettingsScreen.kt | 140 | `private fun summaryOf(sec: Section, vm: dev.pocketagent.app.MainViewModel, state: dev.pocketagent.app.runtime.RuntimeState): String {` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/settings/SettingsScreen.kt | 145 | `val app = dev.pocketagent.app.PocketApp.from(ctx)` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/settings/SettingsScreen.kt | 148 | `else stringResource(R.string.ctl_usage_summary_today, UsageMath.compact(UsageMath.forDay(days, java.time.LocalDate.now()).total, dev.pocketagent.app.ui.chat.currentLoc...` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/settings/SettingsScreen.kt | 151 | `val s = dev.pocketagent.app.PocketApp.from(ctx).presencePrefs.settings.collectAsState().value` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/settings/SettingsScreen.kt | 161 | `val app = dev.pocketagent.app.PocketApp.from(ctx)` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/settings/SettingsScreen.kt | 163 | `val installed = app.ttsModels.state.collectAsState().value is dev.pocketagent.app.voice.TtsModelState.Installed` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/settings/SettingsScreen.kt | 164 | `val ttsLabel = if (installed && vs.ttsEngine != dev.pocketagent.app.voice.TtsEngineMode.SYSTEM) "Supertonic 3" else stringResource(R.string.ctl_voice_engine_system)` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/settings/SettingsScreen.kt | 166 | `dev.pocketagent.app.voice.SoundPack.HEARTH -> stringResource(R.string.ctl_sfx_hearth)` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/settings/SettingsScreen.kt | 167 | `dev.pocketagent.app.voice.SoundPack.STUDIO -> stringResource(R.string.ctl_sfx_studio)` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/settings/SettingsScreen.kt | 168 | `dev.pocketagent.app.voice.SoundPack.OFF -> stringResource(R.string.ctl_sfx_off)` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/settings/SettingsScreen.kt | 178 | `Section.ABOUT -> stringResource(R.string.build_info, dev.pocketagent.app.BuildConfig.VERSION_NAME, dev.pocketagent.app.BuildConfig.GIT_COMMIT)` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/settings/SettingsScreen.kt | 191 | `internal fun ImportResultDialog(r: dev.pocketagent.app.ImportResult, onClose: () -> Unit) {` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/settings/SettingsScreen.kt | 193 | `is dev.pocketagent.app.ImportResult.ServiceAccount ->` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/settings/SettingsScreen.kt | 195 | `is dev.pocketagent.app.ImportResult.UserCredentials ->` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/settings/SettingsScreen.kt | 197 | `is dev.pocketagent.app.ImportResult.Failure -> when (r.code) {` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/settings/SettingsScreen.kt | 198 | `dev.pocketagent.app.runtime.SaParse.Code.EMPTY -> stringResource(R.string.import_err_empty)` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/settings/SettingsScreen.kt | 199 | `dev.pocketagent.app.runtime.SaParse.Code.NOT_JSON -> stringResource(R.string.import_err_not_json, r.detail)` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/settings/SettingsScreen.kt | 200 | `dev.pocketagent.app.runtime.SaParse.Code.NOT_OBJECT -> stringResource(R.string.import_err_not_object)` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/settings/SettingsScreen.kt | 201 | `dev.pocketagent.app.runtime.SaParse.Code.WRONG_TYPE -> stringResource(R.string.import_err_type, r.detail)` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/settings/SettingsScreen.kt | 202 | `dev.pocketagent.app.runtime.SaParse.Code.MISSING_FIELDS -> stringResource(R.string.import_err_missing, r.detail)` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/settings/SettingsScreen.kt | 203 | `dev.pocketagent.app.runtime.SaParse.Code.BAD_PRIVATE_KEY -> stringResource(R.string.import_err_key)` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/settings/SettingsScreen.kt | 208 | `title = stringResource(if (r is dev.pocketagent.app.ImportResult.Failure) R.string.import_failed else R.string.import_done),` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/settings/UsageSection.kt | 52 | `* The numbers come from [dev.pocketagent.app.data.UsageStore] (what the model reported for each reply), nothing else.` | comment (KDoc) |
| app/src/main/java/dev/pocketagent/app/ui/settings/UsageSection.kt | 62 | `val locale = dev.pocketagent.app.ui.chat.currentLocale()` | fully qualified class reference |
| app/src/main/java/dev/pocketagent/app/ui/theme/AppIconManager.kt | 17 | `NEWROZ("dev.pocketagent.app.LauncherNewroz", "icon_fg", "icon_bg"),` | component name (launcher alias) |
| app/src/main/java/dev/pocketagent/app/ui/theme/AppIconManager.kt | 18 | `PET("dev.pocketagent.app.LauncherPet", "icon_pet_fg", "icon_pet_bg"),` | component name (launcher alias) |
| app/src/main/java/dev/pocketagent/app/ui/theme/AppIconManager.kt | 19 | `FLAME("dev.pocketagent.app.LauncherFlame", "icon_flame_fg", "icon_flame_bg"),` | component name (launcher alias) |
| app/src/main/java/dev/pocketagent/app/ui/theme/AppIconManager.kt | 20 | `ZAGROS("dev.pocketagent.app.LauncherZagros", "icon_zagros_fg", "icon_zagros_bg"),` | component name (launcher alias) |
| app/src/main/java/dev/pocketagent/app/ui/theme/AppIconManager.kt | 21 | `KAWA("dev.pocketagent.app.LauncherKawa", "icon_kawa_fg", "icon_kawa_bg"),` | component name (launcher alias) |
| app/src/main/java/dev/pocketagent/app/ui/theme/AppIconManager.kt | 22 | `ROJ("dev.pocketagent.app.LauncherRoj", "icon_roj_fg", "icon_roj_bg"),` | component name (launcher alias) |
| app/src/main/res/xml/shortcuts.xml | 11 | `android:action="dev.pocketagent.app.NEW_CHAT"` | intent action (shortcut) |
| app/src/main/res/xml/shortcuts.xml | 12 | `android:targetPackage="dev.pocketagent.app"` | component (targetPackage) |
| app/src/main/res/xml/shortcuts.xml | 13 | `android:targetClass="dev.pocketagent.app.MainActivity"/>` | component (targetClass) |
| app/src/main/res/xml/shortcuts.xml | 22 | `android:action="dev.pocketagent.app.ROUTINES"` | intent action (shortcut) |
| app/src/main/res/xml/shortcuts.xml | 23 | `android:targetPackage="dev.pocketagent.app"` | component (targetPackage) |
| app/src/main/res/xml/shortcuts.xml | 24 | `android:targetClass="dev.pocketagent.app.MainActivity"/>` | component (targetClass) |
| app/src/main/res/xml/shortcuts.xml | 33 | `android:action="dev.pocketagent.app.TERMINAL"` | intent action (shortcut) |
| app/src/main/res/xml/shortcuts.xml | 34 | `android:targetPackage="dev.pocketagent.app"` | component (targetPackage) |
| app/src/main/res/xml/shortcuts.xml | 35 | `android:targetClass="dev.pocketagent.app.MainActivity"/>` | component (targetClass) |
| app/src/test/java/dev/pocketagent/app/AppLinksTest.kt | 38 | `assertNull(AppLinks.parseParts("dev.pocketagent.app.SOMETHING_ELSE", "abc", 0))` | intent action (test) |
| app/src/test/java/dev/pocketagent/app/AppLinksTest.kt | 54 | `assertEquals("dev.pocketagent.app.OPEN_CHAT", AppLinks.ACTION_OPEN_CHAT)` | intent action (test) |
| app/src/test/java/dev/pocketagent/app/AppLinksTest.kt | 55 | `assertEquals("dev.pocketagent.app.NEW_CHAT", AppLinks.ACTION_NEW_CHAT)` | intent action (test) |
| app/src/test/java/dev/pocketagent/app/AppLinksTest.kt | 56 | `assertEquals("dev.pocketagent.app.TERMINAL", AppLinks.ACTION_TERMINAL)` | intent action (test) |
| app/src/test/java/dev/pocketagent/app/AppLinksTest.kt | 57 | `assertEquals("dev.pocketagent.app.ROUTINES", AppLinks.ACTION_ROUTINES)` | intent action (test) |
| app/src/test/java/dev/pocketagent/app/ChatEngineTest.kt | 131 | `val notes = repo.messagesSnapshot(c.id).filter { it.role == dev.pocketagent.app.data.Role.NOTE && it.text.contains("another provider") }` | test literal |
| app/src/test/java/dev/pocketagent/app/ChatEngineTest.kt | 181 | `dev.pocketagent.app.diag.Diag.redactor.addSecret("test-key", "TOPSECRETVALUE123")` | test literal |
| app/src/test/java/dev/pocketagent/app/ChatEngineTest.kt | 191 | `} finally { dev.pocketagent.app.diag.Diag.redactor.clear() }` | test literal |
| app/src/test/java/dev/pocketagent/app/ChatRepositoryTest.kt | 224 | `val old = dev.pocketagent.app.data.LegacyBotPrompts.byId.getValue("dengbej").first()` | test literal |
| app/src/test/java/dev/pocketagent/app/ChatRepositoryTest.kt | 225 | `val edited = BotPresets.rebel.copy(systemPrompt = dev.pocketagent.app.data.LegacyBotPrompts.byId.getValue("rebel").first() + "\nAlso: love cats.")` | test literal |
| app/src/test/java/dev/pocketagent/app/ChatRepositoryTest.kt | 226 | `val custom = BotPresets.coder.copy(id = "my-coder", systemPrompt = dev.pocketagent.app.data.LegacyBotPrompts.byId.getValue("coder").first())` | test literal |
| app/src/test/java/dev/pocketagent/app/ChatRepositoryTest.kt | 227 | `File(dir, "bots.json").writeText(dev.pocketagent.app.data.ChatJson.encodeBots(listOf(BotPresets.hermes, BotPresets.dengbej.copy(systemPrompt = old), edited, custom)))` | test literal |
| app/src/test/java/dev/pocketagent/app/ChatRepositoryTest.kt | 284 | `val plain = dev.pocketagent.app.data.ChatJson.msgToJson(msg(c.id, "m3", Role.USER, "hi")).toString()` | test literal |
| app/src/test/java/dev/pocketagent/app/ChatRepositoryTest.kt | 286 | `val plainConv = dev.pocketagent.app.data.ChatJson.convToJson(dev.pocketagent.app.data.Conversation("x", "t", listOf("hermes"), 1, 1)).toString()` | test literal |
| app/src/test/java/dev/pocketagent/app/ChatRepositoryTest.kt | 288 | `val old = dev.pocketagent.app.data.ChatJson.convFromJson(org.json.JSONObject("""{"id":"o","title":"Old","botIds":["hermes"],"createdAt":5}"""))!!` | test literal |
| app/src/test/java/dev/pocketagent/app/ChatRepositoryTest.kt | 290 | `val oldMsg = dev.pocketagent.app.data.ChatJson.msgFromJson(org.json.JSONObject("""{"id":"x","role":"BOT","text":"t","createdAt":1}"""), "c")!!` | test literal |
| app/src/test/java/dev/pocketagent/app/ChatRepositoryTest.kt | 356 | `assertTrue(id, dev.pocketagent.app.data.LegacyBotPrompts.byId.getValue(id).none { it.trim() == BotPresets.templates.first { t -> t.id == id }.systemPrompt.trim() })` | test literal |
| app/src/test/java/dev/pocketagent/app/HermesHomeTest.kt | 212 | `dev.pocketagent.app.diag.Diag.redactor.addSecret("t", "SUPERSECRETKEYVALUE")` | test literal |
| app/src/test/java/dev/pocketagent/app/HermesHomeTest.kt | 218 | `} finally { dev.pocketagent.app.diag.Diag.redactor.clear() }` | test literal |
| app/src/test/java/dev/pocketagent/app/HermesHomeTest.kt | 231 | `dev.pocketagent.app.diag.ApiLog.add("GET /health -> 200 3ms")` | test literal |
| app/src/test/java/dev/pocketagent/app/ModelIdsTest.kt | 54 | `val moved = AttachmentStore.resolveIn(dir, "/data/user/10/dev.pocketagent.app/files/chat/attachments/abc.jpg")` | path string (test) |
| app/src/test/java/dev/pocketagent/app/ModelIdsTest.kt | 60 | `assertNull(AttachmentStore.resolveIn(dir, "/data/data/dev.pocketagent.app/files/secrets/"))` | path string (test) |
| app/src/test/java/dev/pocketagent/app/RestartProcessTest.kt | 9 | `assertTrue(RestartProcess.isRestartProcess("dev.pocketagent.app:restart"))` | process name |
| app/src/test/java/dev/pocketagent/app/RestartProcessTest.kt | 10 | `assertTrue(RestartProcess.isRestartProcess(" dev.pocketagent.app:restart "))` | process name |
| app/src/test/java/dev/pocketagent/app/RestartProcessTest.kt | 14 | `assertFalse(RestartProcess.isRestartProcess("dev.pocketagent.app"))` | process name (test) |
| app/src/test/java/dev/pocketagent/app/RestartProcessTest.kt | 15 | `assertFalse(RestartProcess.isRestartProcess("dev.pocketagent.app:other"))` | process name (test) |
| app/src/test/java/dev/pocketagent/app/RestartProcessTest.kt | 16 | `assertFalse(RestartProcess.isRestartProcess("dev.pocketagent.app:restart2"))` | process name |
| app/src/test/java/dev/pocketagent/app/TerminalSessionTest.kt | 148 | `dev.pocketagent.app.diag.Diag.redactor.addSecret("k2", "SECRETCOLOURVALUE")` | test literal |
| app/src/test/java/dev/pocketagent/app/TerminalSessionTest.kt | 156 | `} finally { dev.pocketagent.app.diag.Diag.redactor.clear() }` | test literal |
| app/src/test/java/dev/pocketagent/app/TerminalSessionTest.kt | 241 | `dev.pocketagent.app.diag.Diag.redactor.addSecret("k", "TOPSECRETTERMVALUE")` | test literal |
| app/src/test/java/dev/pocketagent/app/TerminalSessionTest.kt | 248 | `} finally { dev.pocketagent.app.diag.Diag.redactor.clear() }` | test literal |
| app/src/test/java/dev/pocketagent/app/ui/pixel/PxlParserTest.kt | 109 | `for (ic in dev.pocketagent.app.ui.components.PixelIcon.entries) need(ic.spriteName)` | test literal |
| rootfs/smoke/smoke-test.sh | 38 | `# /data/app/~~h-vFz...==/dev.pocketagent.app-UKIp...==/lib/arm64/libproot.so) and `env` takes any argument` | comment (shell) |
| runtime/pocketagent-proot.sh | 11 | `# /data/app/~~h-vFzy...==/dev.pocketagent.app-UKIp...==/lib/arm64/libproot.so. `env` treats ANY` | comment (shell) |
| runtime/test-launcher.sh | 7 | `# (.../~~xx==/dev.pocketagent.app-yy==/lib/arm64/libproot.so: the path contains "="). The launcher is run` | comment (shell) |
| runtime/test-launcher.sh | 23 | `LIBDIR="$TMP/~~h-vFzyO2kFEAxYJt-QO6ug==/dev.pocketagent.app-UKIpKf_m23aztVt8VabgPA==/lib/arm64"` | comment (shell) |
| tools/local-build.ps1 | 59 | `$AppId = 'dev.pocketagent.app'` | applicationId (script variable) |
| tools/local-build.ps1 | 435 | `(@('dev.pocketagent.app.TarExtractorTest', 'dev.pocketagent.app.FileAndUserStateTest') -contains [string]$suite.name) -and` | test filter (script) |
| README.md | 3 | `A sideloaded native Android app (`dev.pocketagent.app`) that runs [Hermes Agent](https://github.com/NousResearch/hermes-agent)` | documentation text |
| AGENTS.md | 14 | `**"Agirî"**, keeping `applicationId = "dev.pocketagent.app"` intact so APK updates preserve` | documentation text |
| AGENTS.md | 100 | `Confirmed official name **"Agirî"** (keeping `applicationId = "dev.pocketagent.app"`). Added,` | documentation text |
| AGENTS.md | 232 | `2. Rules that hold for the whole run: never uninstall `dev.pocketagent.app` and never install over it` | documentation text |
| AGENTS.md | 295 | `adb shell run-as dev.pocketagent.app cat files/logs/app.log        # app log (secrets redacted)` | documentation text |
| AGENTS.md | 296 | `adb shell run-as dev.pocketagent.app cat files/logs/runtime.log    # what Hermes/proot printed` | documentation text |
| AGENTS.md | 297 | `adb shell run-as dev.pocketagent.app cat files/runtime/state/last-launch.txt   # exact command line` | documentation text |
| AGENTS.md | 307 | ``cat file / run-as dev.pocketagent.app sh -c 'cat > files/...'`.` | documentation text |
| AGENTS.md | 345 | ``/data/app/~~<random>==/dev.pocketagent.app-<random>==/lib/arm64`. `env` (toybox and GNU alike)` | documentation text |
| AGENTS.md | 412 | `entries so `adb shell am start -n dev.pocketagent.app/.MainActivity` and in-app intents never` | documentation text |
| AGENTS.md | 431 | `alarm against a dead process, press Home and use `adb shell am kill dev.pocketagent.app`.` | documentation text |
| AGENTS.md | 444 | ``adb shell am start -a dev.pocketagent.app.TERMINAL -n dev.pocketagent.app/.MainActivity`; the tile with` | documentation text |
| AGENTS.md | 445 | ``cmd statusbar add-tile / click-tile / remove-tile dev.pocketagent.app/.service.FireTileService`` | documentation text |
| AGENTS.md | 582 | `1. ~~The app name~~ — **Confirmed: "Agirî"** (keeping `applicationId = "dev.pocketagent.app"`).` | documentation text |

Counts by kind: fully qualified class reference 39, test literal 20, documentation text 14, intent action 11, comment (KDoc) 6, component name (launcher alias) 6, intent action (test) 5, process name 4, comment (shell) 4, component class reference 3, intent action (shortcut) 3, component (targetPackage) 3, component (targetClass) 3, path string (test) 2, process name (test) 2, namespace 1, applicationId 1, applicationId (script variable) 1, test filter (script) 1. Rows listed: 129 (115 code/script + 14 root docs).

---

## B. AndroidManifest.xml surface

File: `app/src/main/AndroidManifest.xml` (200 lines). Namespace of the package-relative names: `dev.pocketagent.app` (from `namespace` in build.gradle.kts:77).

### Permissions (lines 4-11)

| Line | Permission |
|---|---|
| 4 | android.permission.INTERNET |
| 5 | android.permission.ACCESS_NETWORK_STATE |
| 6 | android.permission.FOREGROUND_SERVICE |
| 7 | android.permission.WAKE_LOCK |
| 8 | android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS |
| 9 | android.permission.POST_NOTIFICATIONS |
| 10 | android.permission.RECORD_AUDIO |
| 11 | android.permission.RECEIVE_BOOT_COMPLETED |

No CAMERA permission (deliberate, see AGENTS.md "Hard-won facts"). No `foregroundServiceType` on the service.

### `<queries>` (lines 13-20)

- intent action `android.speech.RecognitionService` (line 15)
- intent action `android.intent.action.TTS_SERVICE` (line 18)

### `<application>` (lines 22-33)

`android:name=".PocketApp"`, `allowBackup="false"` (line 24), `fullBackupContent="@xml/backup_rules"` (25), `extractNativeLibs="true"` (26), `networkSecurityConfig="@xml/network_security_config"` (30), `theme="@style/Theme.PocketAgent"` (32), `largeHeap="true"` (33). Default process (no `android:process`).

### Activities

| Line | Component | Attributes that matter | Intent filters |
|---|---|---|---|
| 35-62 | `.MainActivity` | `exported="true"` (37), `launchMode="singleTop"` (38), `windowSoftInputMode="adjustResize"`, `configChanges` list. Always enabled. | (a) action `android.intent.action.SEND`, category DEFAULT, data mimeType `text/plain` (41-45). (b) SEND, DEFAULT, `image/*` (46-50). (c) SEND, DEFAULT, `*/*` (51-55). (d) `android.intent.action.PROCESS_TEXT`, DEFAULT, mimeType `text/plain`, filter label `@string/srf_ask_agiri` (57-61). No MAIN/LAUNCHER filter (the launcher icons carry it). |
| 156-162 | `.RestartActivity` | `process=":restart"` (158), `exported="false"` (159), `noHistory="true"`, `excludeFromRecents="true"`, translucent theme. No filters. | none |

### Activity aliases (launcher icons), lines 65-153

All six alias to `.MainActivity` (`targetActivity`), all `exported="true"`, each with the MAIN action and LAUNCHER category, and each with `<meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts"/>`.

| Line | Alias | enabled (line) | icon / roundIcon |
|---|---|---|---|
| 65-78 | `.LauncherNewroz` | **true** (67) | `@mipmap/ic_launcher` / `ic_launcher_round` |
| 80-93 | `.LauncherPet` | false (82) | `ic_launcher_pet` / `_round` |
| 95-108 | `.LauncherFlame` | false (97) | `ic_launcher_flame` / `_round` |
| 110-123 | `.LauncherZagros` | false (112) | `ic_launcher_zagros` / `_round` |
| 125-138 | `.LauncherKawa` | false (127) | `ic_launcher_kawa` / `_round` |
| 140-153 | `.LauncherRoj` | false (142) | `ic_launcher_roj` / `_round` |

The aliases have `android:label="@string/app_name"` each.

### Services

| Line | Component | Attributes | Filters |
|---|---|---|---|
| 164 | `.service.AgentService` | `exported="false"` | none |
| 167-176 | `.service.FireTileService` (Quick Settings tile) | `exported="true"` (169), `icon="@drawable/ic_tile_fire"`, `label="@string/srf_tile_label"`, `permission="android.permission.BIND_QUICK_SETTINGS_TILE"` (172) | action `android.service.quicksettings.action.QS_TILE` (174) |

### Receivers

| Line | Component | Attributes | Filters |
|---|---|---|---|
| 179 | `.routines.RoutineAlarmReceiver` | `exported="false"` | none (explicit intent with action `dev.pocketagent.app.ROUTINE_ALARM`, see D) |
| 180-187 | `.routines.BootReceiver` | `exported="false"` | actions `android.intent.action.BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED` (182-185) |
| 190 | `.service.NotificationActionReceiver` | `exported="false"` | none (explicit intents, see D) |

### Provider

| Line | Provider | Attributes |
|---|---|---|
| 192-198 | `androidx.core.content.FileProvider` | `authorities="${applicationId}.fileprovider"` (194), `exported="false"`, `grantUriPermissions="true"`, meta-data `android.support.FILE_PROVIDER_PATHS` -> `@xml/file_paths` (197). Resolves to `dev.pocketagent.app.fileprovider`. |

No `<service>` or `<receiver>` in another manifest (there is only one manifest, the app module's).

---

## C. res/xml files

Four files: `app/src/main/res/xml/`.

1. **shortcuts.xml** (37 lines): static launcher shortcuts, referenced by the six aliases. Three shortcuts, each with an explicit intent (`targetPackage="dev.pocketagent.app"`, `targetClass="dev.pocketagent.app.MainActivity"`):
   - `new_chat`: action `dev.pocketagent.app.NEW_CHAT` (line 11), targetPackage line 12, targetClass line 13.
   - `routines`: action `dev.pocketagent.app.ROUTINES` (22), targetPackage (23), targetClass (24).
   - `terminal`: action `dev.pocketagent.app.TERMINAL` (33), targetPackage (34), targetClass (35).
   The labels are `@string/srf_sc_*`, icons `@drawable/ic_shortcut_chat|routines|terminal`. The file's comment says the intents are explicit because MainActivity is always enabled.
2. **file_paths.xml** (6 lines, FileProvider paths): `cache-path name="diagnostics" path="diagnostics/"` (3), `cache-path name="camera" path="camera/"` (4), `files-path name="attachments" path="chat/attachments/"` (5).
3. **backup_rules.xml** (6 lines): `full-backup-content` excludes `domain="file" path="."` (4) and `domain="sharedpref" path="."` (5). Note: the manifest sets `allowBackup="false"`, so these rules only matter if backup is turned on.
4. **network_security_config.xml** (10 lines): base `cleartextTrafficPermitted="false"` (4); domain-config allowing cleartext for `127.0.0.1` (6) and `localhost` (7), `includeSubdomains="false"`.

---

## D. Names the Kotlin code builds or uses at run time

### D1. Intent actions defined by the app (all literal strings, none derived from the package name at run time)

| File:line | Constant | Value |
|---|---|---|
| AppLinks.kt:22 | ACTION_OPEN_CHAT | `dev.pocketagent.app.OPEN_CHAT` |
| AppLinks.kt:23 | ACTION_NEW_CHAT | `dev.pocketagent.app.NEW_CHAT` |
| AppLinks.kt:24 | ACTION_TERMINAL | `dev.pocketagent.app.TERMINAL` |
| AppLinks.kt:25 | ACTION_ROUTINES | `dev.pocketagent.app.ROUTINES` |
| AppLinks.kt:26 | ACTION_USAGE | `dev.pocketagent.app.USAGE` (parsed, but no sender in the code) |
| service/AgentService.kt:127 | ACTION_STOP | `dev.pocketagent.app.STOP` |
| service/NotificationActionReceiver.kt:119 | ACTION_REPLY | `dev.pocketagent.app.NOTIF_REPLY` |
| service/NotificationActionReceiver.kt:120 | ACTION_MARK_READ | `dev.pocketagent.app.NOTIF_MARK_READ` |
| service/NotificationActionReceiver.kt:121 | ACTION_APPROVAL | `dev.pocketagent.app.NOTIF_APPROVAL` |
| service/NotificationActionReceiver.kt:123 | ACTION_DISMISSED | `dev.pocketagent.app.NOTIF_DISMISSED` |
| routines/RoutineScheduler.kt:324 | ACTION_ALARM | `dev.pocketagent.app.ROUTINE_ALARM` |

Also the shortcut actions in shortcuts.xml (section C) use the same strings as AppLinks.

Standard Android actions used by the code (not app-defined): `Intent.ACTION_SEND` (MainViewModel.kt:225, BotShareUi.kt:128, ChatCommon.kt:245, CtlUi.kt:196), `Intent.ACTION_MAIN` (MainActivity.kt:46,54,70), `Intent.ACTION_PROCESS_TEXT` (AppLinks.kt:79), `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` (ChatScreen.kt:289 and 347), `Settings.ACTION_APP_NOTIFICATION_SETTINGS` (RoutinesScreen.kt:119; BehaviourSection.kt:117 via AndroidSettings), `ACTION_APPLICATION_DETAILS_SETTINGS` (BehaviourSection.kt:124), `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (MainViewModel.kt:202), `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (MainViewModel.kt:204). Also `NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ` (ReplyNotifier.kt:200), which is a constant, not an intent action.

### D2. Deep-link scheme and URIs

- Scheme `agiri`, host `chat`: `agiri://chat/<convId>` built in AppLinks.kt:29-30 and 42. Used only with action `ACTION_OPEN_CHAT`.
- Scheme `agiri`, authority `notification`: `agiri://notification/<action>/<convId>/<discriminator>[/<runId>][/<requestId>]`, built in ReplyNotifier.kt:418 and used as the data URI of the notification action broadcasts (see D5).

### D3. Notification channels (ids are literal; the user-visible names come from strings)

| File:line | Channel id | Importance |
|---|---|---|
| service/AgentService.kt:125 (`CHANNEL`) and :44-45 | `runtime` | LOW |
| service/ReplyNotifier.kt:461 (`CHANNEL_REPLIES`) and :66-67 | `replies` | DEFAULT |
| service/ReplyNotifier.kt:462 (`CHANNEL_APPROVALS`) and :70-71 | `approvals` | HIGH |
| service/ReplyNotifier.kt:463 (`CHANNEL_ROUTINES`) and :74-75 | `routines` | DEFAULT |

### D4. Notification groups and summary tags

- Groups (literal, service/NotificationGroups.kt): `agiri.runtime` (13), `agiri.replies` (14), `agiri.approvals` (15), `agiri.routines` (16). The runtime group is set at AgentService.kt:116.
- Summary tag: `agiri.summary` (NotificationGroups.kt:22). Summary ids 111, 112, 113 (NotificationGroups.kt:24-26) for replies, approvals, routines.
- Restore tag: `agiri.restore` (ReplyNotifier.kt:475), id 106 (ReplyNotifier.kt:474).
- Notification ids: AgentService.kt:126 `NOTIF_ID = 1`; ReplyNotifier.kt:466-470 ids 101 (reply), 102 (approval), 103 (notice), 104 (routine failed), 105 (failed).

### D5. Pending-intent request codes (requestCode argument)

| File:line | Code | Target |
|---|---|---|
| service/AgentService.kt:101-103 | 0 | open MainActivity (`getActivity`) |
| service/AgentService.kt:105-106 | 1 | stop (`getService`, ACTION_STOP) |
| service/FireTileService.kt:89 | 0 | open MainActivity (Android 14+ branch) |
| service/ReplyNotifier.kt:348 | 0 | dismiss broadcast to NotificationActionReceiver |
| service/ReplyNotifier.kt:430 | 0 | notification action broadcasts (reply, mark read, approval); distinguished by the data URI, not by the request code |
| service/ReplyNotifier.kt:480 | 7001 (`REQUEST_OPEN_ROUTINES`) | routines open |
| service/ReplyNotifier.kt:481 | 7002 (`REQUEST_OPEN_APP`) | open app |
| service/ReplyNotifier.kt:482 | 7003 (`REQUEST_OPEN_AFTER_RESTORE`) | open app after a restore |
| AppLinks.kt:49, 87 | `convId.hashCode()` | chat open (derived from the conversation id, not from the package) |
| routines/RoutineScheduler.kt:325, 337 | 7101 (`ALARM_REQUEST_CODE`) | routine alarm broadcast |

### D6. Shared-preferences file names (all literal, Context.MODE_PRIVATE)

| File:line | Name | Keys (from the same file) | In the backup schema? |
|---|---|---|---|
| runtime/Settings.kt:8 | `settings` | auth_mode, endpoint, model keys, region, project_id, no_seccomp, sa_project, sa_email_masked, user_cred_masked, reasoning_effort, custom_base_url | no (BackupCore.PREF_SCHEMA has no `settings`) |
| ui/theme/Appearance.kt:89 (`PREFS`) | `appearance` | theme, accent, darkMode, textScale, animations, showMascot, bubbleStyle, language, pixelFontTitles (Appearance.kt:90-98) | yes |
| voice/VoicePrefs.kt:89 (`PREFS`) | `voice_sound` | ttsEngine, voiceSid, speed, qualitySteps, autoSpeak, soundPack, soundVolume (VoicePrefs.kt:90-96) | yes |
| runtime/PresencePrefs.kt:80 (`PREFS`) | `presence` | autoStart, wakeOnSend, notifyReplies, notifyApprovals, autoRestart, idleStopMinutes (PresencePrefs.kt:81-86) | yes |

Backup schema: `BackupCore.PREF_SCHEMA` at data/BackupManager.kt:244-256 lists `appearance`, `voice_sound`, `presence` only.

### D7. FileProvider authority

- Manifest: `${applicationId}.fileprovider` (manifest line 194), resolves to `dev.pocketagent.app.fileprovider`.
- Code: derived from the running package, not literal: `"${context.packageName}.fileprovider"` (data/AttachmentStore.kt:32) and `app.packageName + ".fileprovider"` (MainViewModel.kt:223).

### D8. Other package-derived names

- `RestartProcess` suffix `:restart` (RestartActivity.kt:42-46), matches the manifest `process=":restart"` (manifest line 158). Process name comparison uses the running process name.
- `AppIconManager` (ui/theme/AppIconManager.kt:17-22): the alias class names are literal full names (`dev.pocketagent.app.LauncherNewroz`, `...LauncherPet`, `...LauncherFlame`, `...LauncherZagros`, `...LauncherKawa`, `...LauncherRoj`); the ComponentName is built as `ComponentName(context.packageName, style.aliasClass)`, so the package part is derived and the class part literal.
- `BuildConfig.APPLICATION_ID` is not used. `context.packageName` is used in AttachmentStore.kt:32, MainViewModel.kt:223, Diagnostics.kt:68, RoutinesScreen.kt:120 (`Settings.EXTRA_APP_PACKAGE`), BehaviourSection.kt:117 and 124, AppIconManager.kt (ComponentName).

### D9. Intent extras and other string keys

- `AppLinks.EXTRA_CONV_ID = "conv_id"` (AppLinks.kt:27). `NotificationActionReceiver.EXTRA_CONV_ID = "conv_id"` (NotificationActionReceiver.kt:124), `EXTRA_CHOICE = "choice"` (125), `EXTRA_RUN_ID = "run_id"` (127), `EXTRA_REQUEST_ID = "request_id"` (128), `KEY_REPLY = "reply_text"` (130, the RemoteInput key).
- `RestartActivity.EXTRA_MAIN_PID = "main_pid"` (RestartActivity.kt:36), written at data/BackupManager.kt:159.
- FileProvider cache names (see C): `diagnostics`, `camera`, `attachments`.
- Diagnostics mail subject `Agirî diagnostics` (MainViewModel.kt:226) and the `Intent.EXTRA_STREAM` share path.

---

## E. Navigator and deep links

### E1. Every Route in `app/src/main/java/dev/pocketagent/app/ui/Nav.kt`

| Route (Nav.kt line) | Parameters | Encoded key | Opened from (file:line) |
|---|---|---|---|
| Home (11) | none | `home` | tab: AppRoot.kt:168; Navigator reset (`go(Home)` = `tab`); AppRoot.kt:251, 335; HermesScreen.kt:167 (fallback); AppLink.Routines pushes Home then Routines (AppRoot.kt:335-336) |
| Chats (12) | none | `chats` | tab: AppRoot.kt:169; HomeScreen.kt:319, 325; ChatScreen.kt:129, 135, 734 (fallback); StarredScreen.kt:73 (fallback); AppRoot.kt:315-317, 327 (OpenChat/NewChat) |
| Chat (13) | `convId: String` | `chat:<id>` | BotsScreen.kt:100, 125; ChatListScreen.kt:118, 216; ChatScreen.kt:383, 845; HomeScreen.kt:146, 272, 336; StarredScreen.kt:88; AppRoot.kt:301 (share into chat), 313 (OpenChat link), 328 (NewChat); AppRoot.kt:409 (screen) |
| Bots (14) | none | `bots` | tab: AppRoot.kt:170 |
| BotEditor (15) | `botId: String?` (null = new) | `botnew` / `bot:<id>` | BotsScreen.kt:80, 112, 121; NewChatSheet.kt:93; ChatScreen.kt:850; HomeScreen.kt:299 |
| Hermes (16) | none | `hermes` | tab: AppRoot.kt:171; AppLink.Terminal (AppRoot.kt:331, Hermes then Terminal); ChatScreen.kt:842, 911, 1051; HomeScreen.kt:347 |
| Terminal (17) | none | `terminal` | AppLink.Terminal (AppRoot.kt:332); HermesScreen.kt:121; ChatScreen.kt:1066; HomeScreen.kt:348 |
| Settings (18) | `section: String?` (default null) | `settings` / `settings:<section>` | tab: AppRoot.kt:172, 339; AppLink.Usage (AppRoot.kt:340, section `usage`); ChatScreen.kt:878 (`provider`); HermesScreen.kt:158 (`provider`); TerminalScreen.kt:134 (`runtime`); HomeScreen.kt:220 (`runtime`), 236 (`provider`) |
| Routines (19) | none | `routines` | AppLink.Routines (AppRoot.kt:336); HomeScreen.kt:352 |
| RoutineEditor (21) | `routineId: String?` (null = new) | `routinenew` / `routine:<id>` | RoutinesScreen.kt:98 (new), 142, 168 (edit) |
| Starred (23) | none | `starred` | ChatListScreen.kt:127; HomeScreen.kt:356 |

Other facts: `Navigator.Saver` keeps the stack as strings (`encode()`/`decodeRoute()`, Nav.kt:71-101), so unknown keys are dropped on restore. Tabs (AppRoot.kt:168-172): Home, Chats, Bots, Hermes, Settings.

Settings section strings in use: `provider`, `runtime`, `usage` (listed above; the Settings screen decides what each means).

### E2. Deep links and intents AppLinks.kt understands

Source: AppLinks.kt (parse at 55-59, parseParts at 62-72, processText at 78-84).

- Action `dev.pocketagent.app.OPEN_CHAT`: opens a chat. Convo id from the `conv_id` extra, else from the path of `agiri://chat/<id>`. Empty id gives nothing.
- Action `dev.pocketagent.app.NEW_CHAT`: new chat (reuses an empty plain Hermes chat, AppRoot.kt:318-330).
- Action `dev.pocketagent.app.TERMINAL`: Hermes > Terminal.
- Action `dev.pocketagent.app.ROUTINES`: Routines (sent by the routines notification, ReplyNotifier.kt:285).
- Action `dev.pocketagent.app.USAGE`: Settings > Usage (parsed; no sender in this code).
- `Intent.ACTION_PROCESS_TEXT` with text (max 20,000 chars, `MAX_PROCESS_TEXT`): "Ask Agiri" (handled in MainActivity.kt:51, not by parse).
- Ignored when the intent has `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY` (AppLinks.kt:63 and 80).
- MainActivity also takes `Intent.ACTION_SEND` (text, image or any stream, MainActivity.kt:58-70), which is not in AppLinks.
- Not in AppLinks: notification action broadcasts with URI `agiri://notification/...` (NotificationActionReceiver, D2) and the alarm action (ROUTINE_ALARM, D1).
- The shortcut and tile entry points are the same actions (shortcuts.xml; FireTileService uses an explicit MainActivity intent with no action, FireTileService.kt:87-89).

---

## F. BuildConfig, versions, CI and the local build script

### F1. BuildConfig fields in use

- `GIT_COMMIT`: declared with `buildConfigField` in app/build.gradle.kts:91. Value: gradle property `gitCommit`, else `git rev-parse --short=12 HEAD`, else `unknown` (build.gradle.kts:15-16).
  - Used: Diagnostics.kt:73, PocketApp.kt:144, RuntimeManager.kt:395, RuntimeSection.kt:159, SettingsScreen.kt:178.
- `VERSION_NAME`: generated by AGP from build.gradle.kts:89. Used: BackupManager.kt:79, PocketApp.kt:144, RuntimeSection.kt:159, SettingsScreen.kt:178.
- `BUILD_TYPE`: generated by AGP. Used: Diagnostics.kt:74 (`build type: ...`).
- `VERSION_CODE`, `APPLICATION_ID`, `DEBUG`: generated but not used in app code. `PackageInfo` read at Diagnostics.kt:68-72 (`versionName`, `longVersionCode`).

### F2. How versionCode and versionName are computed

- build.gradle.kts:17: `buildNumber = providers.gradleProperty("buildNumber").toIntOrNull() ?: 0`.
- build.gradle.kts:88: `versionCode = maxOf(buildNumber, 1)`.
- build.gradle.kts:89: `versionName = "0.1.$buildNumber"`. With no `-PbuildNumber`, versionName is `0.1.0` and versionCode is 1.
- CI passes `-PbuildNumber=${{ github.run_number }}` (build.yml, apk job). Result: versionCode = run number, versionName `0.1.<run>`.
- Local build: `Resolve-BuildNumber` in tools/local-build.ps1 (lines 360-370) defaults to the release number recorded for the parts (`source.txt`) and refuses a lower number.
- **Mismatch:** tools/local-build.ps1 line 18 (help text) says the version is `"0.0.<n>-phase0"`. build.gradle.kts says `0.1.<n>`. AGENTS.md (CI section, "app version `0.0.N-phase0`") says `0.0.N-phase0`, which matches neither the code nor the local help. The code is the authority: `0.1.<n>`.

### F3. .github/workflows/build.yml (single workflow named `build`)

Trigger: push to branch `ccr-bb6d7bcc-9eojyu` (line 7) or workflow_dispatch. Concurrency cancels in-progress runs for the same ref (13-15). Env: HERMES_TAG v0.23.3, HERMES_COMMIT caafe5389ae585ef77387a962aee05b1bcf17029, NDK_VERSION 27.3.13750724 (17-21).

| Job (line) | Runner | Needs | What it builds / runs | Uploaded artifact (name, files) |
|---|---|---|---|---|
| unit (24) | ubuntu-24.04 | none | Temurin JDK 21; `sdkmanager` platforms;android-37.2, build-tools;36.0.0; `./gradlew :app:testDebugUnitTest`; `bash -n`/`sh -n` on runtime, rootfs and proot scripts; launcher test `sh runtime/test-launcher.sh sh dash bash mksh` | none |
| proot (49) | ubuntu-24.04 | none | NDK install; `proot/build-proot.sh --abi arm64-v8a --out $RUNNER_TEMP/proot-out`; qemu start-up check; summary | `proot-arm64`: `libproot.so`, `libproot-loader.so`, `proot-build.info` (directory contents) |
| rootfs (77) | ubuntu-24.04-arm | none | `rootfs/build-rootfs.sh --arch arm64` (XZ_LEVEL=7); step summary | `rootfs-arm64`: `rootfs-arm64.tar.xz`, `rootfs-arm64.info` (compression level 0) |
| smoke (110) | ubuntu-24.04-arm | rootfs, proot | extracts rootfs as unprivileged user; `rootfs/smoke/smoke-test.sh` | `smoke-logs` (`*.log`, if any) |
| apk (141) | ubuntu-24.04 | unit, smoke, rootfs, proot | Temurin 21; SDK; real-rootfs extraction test (`RealRootfsExtractionTest`); `./gradlew :app:assembleDebug -ProotfsArchive=... -PprootDir=... -PbuildNumber=<run_number> -PgitCommit=<sha12>` | see below |
| release (204) | ubuntu-24.04 | apk | `gh release create phase0-<run> --prerelease` | attached assets: the APK, `SHA256SUMS.txt`, `rootfs-arm64.info`, `proot-build.info` |

APK job details:

- Gradle output is `app/build/outputs/apk/debug/app-debug.apk` (copied to the name below).
- Renamed to `PocketAgent-phase0-<run>-<sha7>.apk` (line 177). Verified with `unzip -l` (libproot and assets/rootfs) and `apksigner verify --print-certs`.
- **Checksum step:** line 185, `sha256sum "$NAME" > SHA256SUMS.txt` (run inside the "Verify and name the APK" step).
- Writes outputs `name`, `size` (lines 183-184). `apk-size.txt` written at line 196.
- Artifact `apk` (uploaded from `release-files/`, line 197-201): `PocketAgent-phase0-<run>-<sha7>.apk`, `SHA256SUMS.txt`, `rootfs-arm64.info`, `proot-build.info`, `apk-size.txt`.

Release job: downloads artifact `apk`, takes `PocketAgent-phase0-*.apk` by glob (line 219), tag `phase0-<run>` (line 216), target = default branch (not the build commit, per the comment at 231-232). The release does **not** attach `rootfs-arm64.tar.xz`.

Signing: all builds use the committed `app/sideload-debug.keystore` (`sideload` config, build.gradle.kts:99-108). The keystore file itself is in the repo's app/ folder.

### F4. What tools/local-build.ps1 assumes

- Package: `$AppId = 'dev.pocketagent.app'` (line 59). Launch: `adb shell am start -n <AppId>/<AppId>.MainActivity` (line 499), so it assumes the class is `dev.pocketagent.app.MainActivity` (the same as the manifest).
- Install: `adb install -r` (or `-d` on downgrade) of `app\build\outputs\apk\debug\app-debug.apk` (lines 394 and 480). Assumes the debug variant.
- Versions: passes `-PbuildNumber=$bn` and `-PgitCommit=$commit` (line 385). Help text version `0.0.<n>-phase0` (line 18), which does not match build.gradle.kts (see F2).
- Parts: the rootfs and proot come from `dist\runtime-parts\` (`rootfs\rootfs-arm64.tar.xz` and `proot\`, lines 382-383), verified with `SHA256SUMS.txt` and `*.info` files from `phase0-N` releases (FetchParts, lines 202-330). It assumes CI names: `PocketAgent-phase0-<n>-*.apk`, `SHA256SUMS.txt`, `rootfs-arm64.info`, `proot-build.info` (lines 293-299, 325).
- Unit test filter: test classes `dev.pocketagent.app.TarExtractorTest` and `dev.pocketagent.app.FileAndUserStateTest` (line 435), used with `-Test`.
- Not in the script: the package name is passed only through `$AppId`, there is no reference to `applicationId` in Gradle.

---

## G. Names looked up at run time (what a code shrinker could break)

Current state: `release` has `isMinifyEnabled = false` (build.gradle.kts:113) and the debug build is what the owner runs. The app has **no ProGuard/R8 rules file** (`app/*.pro` does not exist), and the sherpa-onnx AAR's `proguard.txt` is **0 bytes** (no keep rules shipped). Anything below matters only once minification is turned on, or for a library that is itself obfuscated.

### G1. Reflection and Class.forName

- `Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null)` at `app/src/main/java/dev/pocketagent/app/ui/pixel/SpriteBank.kt:255`. Platform class, so R8 never renames it; the risk is only the method name, which is platform API.
- `javaClass.getDeclaredField("pid")` at `runtime/RuntimeManager.kt:603`: reflection on a private field of the platform process class (`java.lang.ProcessImpl`). Not renamable by R8. Hidden-API restrictions can apply on newer Android versions (not tested here).
- No other `Class.forName`, `getMethod`, `ServiceLoader` or `loadLibrary` in app code (grep, all of app/src/main).

### G2. Enums stored by name and read back by name

Enum values are stored by `.name` and read with `valueOf` (throws on unknown names, caught and given a default) or `enumValues<E>().firstOrNull { it.name == … }` (never throws). Renaming any constant under minification would change the stored name; the names below are the storage keys in use.

| Enum (declared) | Stored where (file:line) | Read with |
|---|---|---|
| ThemeId, AccentId, DarkMode, AnimLevel, BubbleStyle (ui/theme/Appearance.kt:10-20) | SharedPreferences `appearance`, keys theme/accent/darkMode/animations/bubbleStyle (Appearance.kt:51-57) | `parse<E>()` at Appearance.kt:85-86 (firstOrNull, default); ThemeId also by name in ChatScreen.kt:628 (`/theme` command) |
| TtsEngineMode (voice/VoicePrefs.kt:10), SoundPack (VoicePrefs.kt:12) | SharedPreferences `voice_sound`, keys ttsEngine/soundPack (VoicePrefs.kt:53, 58) | `parse<E>()` at VoicePrefs.kt:86 |
| AuthMode (runtime/ProviderConfig.kt:9), ApiEndpoint (ProviderConfig.kt:33) | SharedPreferences `settings`, keys `auth_mode`, `endpoint` (Settings.kt:11-16) | `valueOf` in try/catch with default (Settings.kt:11, 15) |
| BotVoiceStyle (data/Models.kt:10) | Bot JSON `voiceStyle` (ChatJson.kt:20 write; BotShare.kt:32 write) | `valueOf` in try/catch (ChatJson.kt:27; BotShare.kt:70) and in the UI (BotEditorScreen.kt:142, 144) |
| GroupMode (Models.kt:78) | Conversation JSON `groupMode` (ChatJson.kt:49) | `valueOf` try/catch (ChatJson.kt:70) |
| ChatMode (Models.kt:51) | Conversation JSON `mode` (ChatJson.kt:51) | `valueOf` try/catch (ChatJson.kt:72) |
| Role (Models.kt:36) | Message JSON `role` (ChatJson.kt:109) | `valueOf` try/catch, returns null (message dropped) (ChatJson.kt:128) |
| AttachmentKind (Models.kt:38) | Attachment JSON `kind` (ChatJson.kt:84) | `valueOf` try/catch (ChatJson.kt:95) |
| BudgetMode (data/UsageStore.kt:24) | Usage JSON `budget.mode` (UsageStore.kt:209) | `valueOf("OFF"…)` try/catch (UsageStore.kt:174) |
| RoutineKind (routines/Routine.kt:6) | Routines JSON `kind` (routines/RoutineStore.kt:36) | `valueOf` try/catch (RoutineStore.kt:56); UI state via `rememberSaveable` (RoutineEditorScreen.kt:123, 135) |
| AvatarId (ui/pixel/Avatars.kt:20) | Bot avatar stored as a string; `avatarIdOf(name)` matches names case-insensitively (Avatars.kt:22-25) | `AvatarId.entries.firstOrNull { it.name.equals(...) }` (never throws) |
| LogKind (hermes/HermesHome.kt:17) | UI state `rememberSaveable` as a name (HermesTabs.kt:247) | `LogKind.valueOf(kind)` (HermesTabs.kt:256), inside a try |
| AppIconStyle (ui/theme/AppIconManager.kt:12-19) | Not stored in prefs: the enabled state is the PackageManager component state of each alias class | `AppIconStyle.entries` and `aliasClass` strings (AppIconManager.kt:17-22, 27-28) |

Enums not checked for storage (not in the list above): HermesDoc, TermKind, SoundCue, MascotMood, HearthState, PixelIcon, ButtonKind, BackupReason. Enums under `Route` are not enums (they are sealed classes, encoded by string key in Nav.kt:71-101).

### G3. JNI and native libraries (sherpa-onnx 2.0.3)

- Gradle dependency: `k2-fsa:sherpa-onnx:2.0.3` with artifact `sherpa-onnx-static-link-onnxruntime` (app/build.gradle.kts:34-39). Its AAR contains `classes.jar`, `proguard.txt` (0 bytes), and `jni/{arm64-v8a,armeabi-v7a,x86,x86_64}/libsherpa-onnx-jni.so` (plus `jni/x86/libonnxruntime.so`).
- The app code has no `System.loadLibrary` call; the library's own classes load `sherpa-onnx-jni` (the string `sherpa-onnx-jni` is present in the class files; checked in `dist/sherpa-classes.jar`, which is `classes.jar` of the AAR).
- Classes the app uses (VoiceEngine.kt:10-13, 274-293): `OfflineTts`, `OfflineTtsConfig`, `OfflineTtsModelConfig`, `OfflineTtsSupertonicModelConfig`. Also 132 `com/k2fsa/sherpa/onnx/*` classes in the jar.
- JNI symbols in the arm64 `.so`: 184 exported `Java_com_k2fsa_sherpa_onnx_*` names, including `Java_com_k2fsa_sherpa_onnx_OfflineTts_generateWithConfigImpl`, `..._generateWithCallbackImpl`, `..._newFromAsset`. These are looked up by the JVM at call time by name (fixed by the package and class name, so a rename of `com.k2fsa.sherpa.onnx` would break them; R8 would break them unless kept).
- Config members: the arm64 `.so` contains the strings `modelConfig` (1 occurrence), `supertonic` (21), `lexicon` (43). This suggests the native side reads Kotlin config members by name. Not verified in source; the `.so` has no visible `GetFieldID` symbol (stripped), so the exact lookup is unknown.
- Callback: `OfflineTts.generateWithConfigAndCallback` is avoided by the app. `VoiceEngine.kt:365` calls `tts.generateWithConfig(chunk, genCfg)` (no callback), per the AGENTS.md note about the Kotlin lambda `invoke([F)Ljava/lang/Integer;` lookup. The `.so` contains the string `invoke` (4-5 occurrences in arm64).
- Other native code: none in the app. The proot binaries are shipped as `libproot.so` and `libproot-loader.so` (jniLibs, `useLegacyPackaging = true`, build.gradle.kts:119-122) and run as files, not loaded through JNI.

### G4. Third-party ServiceLoader users (checked in the jars in the Gradle cache)

- `org.apache.commons:commons-compress:1.28.0` (`gradle/libs.versions.toml:9`, cache path `org.apache.commons/commons-compress/1.28.0`): `java.util.ServiceLoader` is referenced by `archivers/ArchiveStreamFactory.class`, `compressors/CompressorStreamFactory.class` and `utils/ServiceLoaderIterator.class`. The jar has **no** `META-INF/services` entries. The app uses only `TarArchiveInputStream`, `TarArchiveEntry` and `XZCompressorInputStream` directly (runtime/TarExtractor.kt:3-5), never the factories, so the ServiceLoader path is not reached by app code. A shrinker could only break it if the factories were used.
- `org.tukaani:xz:1.12` (`libs.versions.toml:10`): no `java/util/ServiceLoader` references, no `META-INF/services`. Nothing to keep.
- `org.json` is used as a JVM library (fine, per the task).
- sherpa-onnx classes: no `ServiceLoader` references (0 in `classes.jar`).

### G5. Other things looked up by name

- Component names by string: `AppIconStyle.aliasClass` (six `dev.pocketagent.app.Launcher*` names, AppIconManager.kt:17-22) used in `ComponentName` and `setComponentEnabledSetting`. These are manifest components, so they are kept by the manifest.
- Manifest class names (`.PocketApp`, `.MainActivity`, `.RestartActivity`, `.service.AgentService`, `.service.FireTileService`, `.routines.RoutineAlarmReceiver`, `.routines.BootReceiver`, `.service.NotificationActionReceiver`, the six aliases) are resolved by the framework from the manifest. AGP keeps them; they break only if a class is renamed without updating the manifest.
- Shortcut `targetClass` strings (shortcuts.xml) name `dev.pocketagent.app.MainActivity`.
- `BackupCore.PREF_SCHEMA` (BackupManager.kt:244-256) is keyed by SharedPreferences file names and keys (`appearance`, `voice_sound`, `presence`, and their keys). A rename of a key would break restore (no migration code is visible in that file for the old names).
- `BackupManager.kt` (zip) entries are checked by string: `prefs/<name>.json`, `hermes/...`, `chat/...` (BackupManager.kt:325, 366, 470).
- FileProvider path names (`diagnostics`, `camera`, `attachments`, file_paths.xml) are strings in XML.
- Route keys in Nav.kt are strings (not enums) and are stored in the saver.

---

## Summary

- Occurrences of `dev.pocketagent.app` outside docs and dist: 1181 (1167 code/scripts + 14 in root AGENTS.md and README.md). Of the code lines, 167 package declarations and 885 imports; 115 other code/script lines are listed in section A.
- Manifest: 8 permissions, 1 `<queries>` block with 2 intents, 1 main activity plus 1 restart activity, 6 launcher aliases (only Newroz enabled), 1 service plus 1 tile service, 3 receivers, 1 FileProvider. Only the tile service and MainActivity are exported to other apps by design (plus the six launcher aliases, which are exported as launchers).
- Derived vs literal: intent actions, channel ids, group tags, SharedPreferences names and request codes are literal; the FileProvider authority is derived from the package name (`${applicationId}` in the manifest, `packageName` in code).
- Discrepancy found: the version text in tools/local-build.ps1 (line 18) and AGENTS.md (`0.0.N-phase0`) do not match build.gradle.kts (`0.1.N`).
- Shrinker risk: none active today (minify off, no ProGuard file, sherpa AAR ships an empty proguard.txt). Name-based risks to keep if minify turns on: sherpa JNI package/class names and the config member names read from native code (unverified), enum names in prefs and JSON (section G2), BackupCore prefs keys, and the six alias class names.

No tracked file was changed. This file is the only output: dist/w0/survey-package.md (git-ignored).
