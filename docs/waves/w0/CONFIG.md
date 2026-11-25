# Wave 0, area CONFIG: guarded configuration, practice-model launch, diagnostics (features 0.10, 0.1 Export logs, 0.4 launch) — contract draft

Reads: `docs/phase4-plan.md` 438-560, 568-766, findings B2 B6 B13 B15 B21 C4 D6 D15, section 9.3, 14, experiment S5; `docs/phase3-plan.md` (style); `docs/design-language.md` 8.16; `dist/w0/survey-*.md`, `dist/w0/draft-STAGE.md` (its R1 and R2 are accepted below).
Real code read at commit 344829f: `runtime/{HermesConfig,ProviderConfig,RuntimeManager,Settings,UserState,Paths,PresencePrefs}.kt`, `diag/*`, `data/Transcript.kt`, `PocketApp.kt`, `api/HermesApi.kt`, `ui/settings/{BehaviourSection,RuntimeSection,CtlUi}.kt`, `rootfs/smoke/{smoke-test.sh,mock_llm.py,fixtures/*}`, `ProviderConfigTest`, `RuntimeManagerInitTest`.
Hermes source: extracted from `dist/runtime-parts/rootfs/rootfs-arm64.tar.xz` (`./root/.hermes/hermes-agent`) into `dist/w0/hermes-src/`. Every "H:" below is a file:line read there (paths relative to `hermes-agent/`). Written for the PASS case of S5.
Ground rules of Phase 3 apply (one owner per file, no Gradle, English texts only, Kotlin gotchas of AGENTS.md). Extra rules: logic is kept pure (plain data in, plain data out, `File` or `OutputStream` injected) so JVM tests cover it; nothing here changes a picture.

## 1. Scope

| # | Work item | Core |
|---|---|---|
| 1 | **Guard block** in `config.yaml` for every mode (0.10): fixed `platform_toolsets.api_server`, `auxiliary.title_generation.model_upgrade_enabled: false`, `security.allow_lazy_installs: false`, `web.keyless_fallback` (3.2, 3.3). Six golden fixtures change, two are added, `ProviderConfigTest` follows. | yes |
| 2 | **Privacy row** "Web lookups" with the switch (`GuestPrefs.webKeyless`) in Settings > Behaviour, applied live by rewriting `config.yaml` (3.1, 3.9). | yes |
| 3 | **Practice model launch** (0.4): `GuestPrefs.practiceModel`, its own generated `.env` and `config.yaml` (no provider key, no credential file), one launch that starts the scripted model and Hermes together, `RuntimeManager.onGuestOptionsChanged` (3.2 to 3.5). The owner's saved setup is never read or written in this mode. | yes |
| 4 | **Code Hermes runs** (0.10): at every start list `hooks/` and `plugins/` in HERMES_HOME; what the app did not write is logged, kept in `RuntimeManager.guestCodeReport` and shown in Diagnostics (3.6). | yes |
| 5 | **Exit reasons and crash offer** (0.10): structured reader of `ApplicationExitInfo` (API 30+, guarded), the dump section rebuilt on it, `CrashReports` and the dialog "Copy report" at the next launch (3.7). | yes |
| 6 | **Export logs** (0.1): one redacted zip of `app.log(.1)`, `runtime.log(.1)`, `last-launch.txt` and `report.txt` through the share sheet and the existing FileProvider (3.8). | yes |
| 7 | **60,000-character split** (0.10, B15): `Transcript.splitLong` and system messages split inside `Transcript.build` (3.10). The user/assistant half needs a 6-line change in `api/HermesApi.kt`, which nobody owns: request R1. | yes |
| 8 | **Smoke proof** (0.10): `/v1/toolsets` and "no title call" assertions in `smoke-test.sh`; phase 3 boots the `practice` fixture (requests R3, R4). | yes |
| 9 | **PocketApp.kt**: new members, the provider lambda, STAGE's R1 and R2, one named slot function `wave0Slots()` (3.11). | yes |

Cut FIRST if the session runs out (all three keep the dump complete):
1. The two UI cards of `DiagnosticsExtras` (exit reasons, guest code); the dump sections stay. Loses the glanceable view in Settings.
2. `report.txt` inside the zip, and STAGE's R2 line (sprite load errors in the dump). Loses one convenience file and one diagnostic line.
3. The `/v1/toolsets` and title assertions in `smoke-test.sh` (item 8). Loses the automatic CI proof that the tool list is really short; the robot check of section 9 still proves it on the phone.

## 2. Files (CONFIG owns these exclusively; paths under `app/src/main/java/dev/pocketagent/app/` unless marked)

| File | Change |
|---|---|
| `runtime/ProviderConfig.kt` | `renderYaml(i, o)` = `renderModelYaml(i)` (old body, unchanged) + `renderGuardYaml(o)`; add `renderPracticeYaml`, `renderPracticeEnv`, `AGENT_TOOLSETS`, `WITHHELD_TOOLSETS`. Nothing else changes (validation, `probeHosts`, `describe` stay). |
| `runtime/HermesConfig.kt` | Pure `plan(...)` returning `GuestFiles`; `apply(...)` does the I/O from it and gains `options: GuestOptions = GuestOptions()`; `launchCommand(...)`. |
| `runtime/GuestPrefs.kt` (new) | `GuestOptions`, `GuestPrefs` (SharedPreferences `"guest"`: `web_keyless`, `practice`). Not in `BackupManager`'s schema, so the practice switch never travels in a backup. |
| `runtime/GuestCodeScan.kt` (new) | Pure scan of `hooks/` and `plugins/`. |
| `runtime/RuntimeManager.kt` | Practice launch, guest options, code scan, `onGuestOptionsChanged`, `rewriteGuestConfigIfInstalled` follows the options (3.5). Constructor unchanged. |
| `diag/ExitReasons.kt`, `diag/CrashReports.kt`, `diag/LogExport.kt` (new) | 3.7, 3.8. |
| `diag/Diagnostics.kt` | Exit-reason section rebuilt on `ExitReasons`; new sections "Guest options and practice model", "Code Hermes runs from its folders", "Pixel sprite load errors" (STAGE R2); `exportLogs()`, `exportLogsIntent()`. `reasonName` moves to `ExitReasons.name`. |
| `data/Transcript.kt` | `MAX_PART_CHARS`, `splitLong`; `build` emits one system message per part. |
| `PocketApp.kt` | 3.11. |
| `ui/diag/DiagnosticsExtras.kt` (new) | `DiagnosticsExtras()`, `CrashReportOffer()`. |
| `ui/diag/PrivacyRows.kt` (new) | `WebLookupsRow()`. |
| `ui/settings/BehaviourSection.kt` | One import and one call `WebLookupsRow()` as the last child of the section's `Column` (after the battery block). Nothing else. |
| `res/values/strings_config.xml` (new) | Section 5. |
| `rootfs/smoke/fixtures/{studio,studio-effort,studio-nokey,express,vertex-sa,vertex-user}.yaml` | Guard block appended (3.2). The six `.env` files do not change. |
| `rootfs/smoke/fixtures/{practice,studio-nokeyless}.{yaml,env}` (new) | 3.3. |
| `app/src/test/java/dev/pocketagent/app/` | `ProviderConfigTest.kt` (changed), `TranscriptTest.kt` (added tests), new `HermesConfigPlanTest`, `GuestCodeScanTest`, `GuestPrefsTest`, `ExitReasonsTest`, `LogExportTest`, `TranscriptSplitTest`. |
| `rootfs/smoke/smoke-test.sh` | Only if the lead grants it (request R4). Exact edit in 3.12. |

## 3. Shared symbols you PROVIDE

### 3.1 `runtime/GuestPrefs.kt` (package `dev.pocketagent.app.runtime`)
```kotlin
data class GuestOptions(
    val webKeylessFallback: Boolean = true,   // written as web.keyless_fallback
    val practicePort: Int? = null,            // non-null = practice mode, the scripted model listens on this loopback port
) { val practice: Boolean get() = practicePort != null }

class GuestPrefs internal constructor(sp: android.content.SharedPreferences) {
    val webKeyless: StateFlow<Boolean>        // default true ("the default stays on, as today")
    val practiceModel: StateFlow<Boolean>     // default false
    fun setWebKeyless(on: Boolean)            // persists at once, updates the flow, does NOT touch Hermes
    fun setPracticeModel(on: Boolean)         // same; the caller then calls RuntimeManager.onGuestOptionsChanged(true)
    companion object { fun get(context: Context): GuestPrefs }   // process singleton like PresencePrefs.get
}
```

### 3.2 `runtime/ProviderConfig.kt` additions — the exact YAML
```kotlin
val AGENT_TOOLSETS: List<String> = listOf("web","terminal","file","code_execution","vision","skills","todo","memory","session_search","delegation")
val WITHHELD_TOOLSETS: List<String> = listOf("browser","cronjob","image_gen","tts","connections")   // tests and Diagnostics only
fun renderModelYaml(i: ProviderInputs): String            // the body of today's renderYaml, byte for byte
fun renderGuardYaml(o: GuestOptions = GuestOptions()): String
fun renderYaml(i: ProviderInputs, o: GuestOptions = GuestOptions()): String = renderModelYaml(i) + renderGuardYaml(o)
fun renderPracticeYaml(port: Int, o: GuestOptions = GuestOptions()): String
fun renderPracticeEnv(apiServerKey: String, apiPort: Int = Paths.DEFAULT_API_PORT): String
```
`renderGuardYaml` returns exactly (LF, ends with a newline, `<kw>` = `true` or `false` from `o.webKeylessFallback`), appended after the model part of EVERY mode (API_KEY studio and express, SERVICE_ACCOUNT, USER_CREDENTIALS, OPENROUTER, ANTHROPIC, OPENAI, CUSTOM). It never writes an `agent:` key (`ProviderConfigTest` asserts `contains("agent:")` is false for a bad effort):
```yaml
# Fail-closed tool list and privacy switches (Wave 0 stop-gaps).
platform_toolsets:
  api_server:
    - web
    - terminal
    - file
    - code_execution
    - vision
    - skills
    - todo
    - memory
    - session_search
    - delegation
auxiliary:
  title_generation:
    model_upgrade_enabled: false
security:
  allow_lazy_installs: false
web:
  keyless_fallback: <kw>
```
Why each line is read (H = `hermes-agent/`):
- `platform_toolsets.api_server`: the API server resolves its tools with `_get_platform_tools(user_config, "api_server")` per request (H gateway/platforms/api_server.py:2217). A saved list of configurable keys is taken as given, nothing else is added except non-configurable toolsets the platform composite lists (H hermes_cli/tools_config.py:572-600 `_get_platform_tools`, :489-504 `_explicit_toolsets`, :713-735 `_recover_platform_native_toolsets`). The keys are the first column of `CONFIGURABLE_TOOLSETS` (H tools_config.py:69-95). Without a list the default is `hermes-api-server` (H hermes_cli/platforms.py:33), 24 tools (H toolsets.py:191-195). The ten keys above give 17 tools: web_search, web_extract, terminal, process_manage, read_file, write_file, patch, search_files, execute_code, vision_analyze, skills_list, skill_view, skill_manage, todo_list, memory, session_search, delegate_task. Left out: `browser` (4 tools, toolsets.py:108), `cronjob` (:113), `image_gen` (:84), `tts` (:123), and `connections` (3.13 D1). Children of `delegate_task` inherit these (H tools/delegate_tool.py:267 `_resolve_child_toolsets`), so no second door. `agent.disabled_toolsets` is deliberately NOT written (Wave 4 owns it; it runs last and would also work, H tools_config.py:640).
- `auxiliary.title_generation.model_upgrade_enabled: false`: read by `_model_title_upgrade_enabled` (H agent/title_generator.py:194-201); false keeps the instant derived title and returns before any model call (:947). `enabled` stays true, so sessions still get a title.
- `security.allow_lazy_installs: false`: read in H pm/install.py:210 and hermes_cli/default_plugins.py:134 (`is True` is required to fetch). It stops PyPI installs at first use of a backend.
- `web.keyless_fallback`: read per lookup by `_keyless_tier_enabled` (H agent/web_search_registry.py:145-153, default True; the config is re-read when the file changes, H hermes_cli/config.py:2121-2127), so the switch works without a restart. `web.keyless_rescue` follows it (H config_defaults.py:389-393). The ring is Exa, Parallel, Firecrawl, Keenable (H plugins/web/keyless_mcp.py:1-27); `ddgs` is not installed (not in extra `all`, H pyproject.toml:242), and lazy installs are now off.
- Side-task models "pinned to the main model": NOTHING is written, on purpose (3.13 D2). `model.provider` is always written, which already routes `auto` side tasks to the main provider and model (H agent/auxiliary_client.py:4459-4466) and forbids the guessing chain (:4420-4432). `auxiliary.free_only` must not be written: it would also reject the main model in OpenRouter mode (H auxiliary_client.py:2274-2281 via :4810). A test pins this (section 8).

Fixtures (all LF, `.gitattributes` already pins them): the six existing `.yaml` files get `renderGuardYaml()` appended; the `.env` files are untouched. Examples of the model part per mode (unchanged lines, shown for completeness): studio `model: default "gemini-4-hydrogen", provider: gemini`; vertex modes add `vertex: project_id, region`; any mode with an effort adds `agent: reasoning_effort`; OPENROUTER `provider: openrouter`; ANTHROPIC `provider: anthropic`; OPENAI `provider: openai-api`; CUSTOM `provider: custom` + `base_url` (+ `key_env`). New fixtures:
- `studio-nokeyless.yaml` = `studio.yaml` with `keyless_fallback: false`; `studio-nokeyless.env` = `studio.env`. Boots in the smoke loop.
- `practice.yaml` (port 18080 = the smoke mock's port):
```yaml
# Generated by PocketAgent every time the runtime starts. Edit the fields in the app instead.
# PRACTICE MODEL: no provider and no key; every answer comes from the scripted model on this phone (zero tokens).
model:
  default: "practice-model"
  provider: custom
  base_url: "http://127.0.0.1:18080/v1"
  api_key: "practice"
```
followed by the guard block. `practice.env` = the two header comment lines, `API_SERVER_ENABLED=true`, `API_SERVER_HOST=127.0.0.1`, `API_SERVER_PORT=8642`, `API_SERVER_KEY=smoke-api-key-0123456789abcdef0123456789`, then `# PRACTICE MODEL: no provider key is written.` Inline `api_key` is what CI already proves (`smoke-test.sh` phase 3 writes it); the custom mode's `key_env` form is only unit-tested.

### 3.3 `runtime/HermesConfig.kt`
```kotlin
class GuestFiles(val env: String, val yaml: String, val installServiceAccount: Boolean, val installAdc: Boolean)
object HermesConfig {
    fun plan(inputs: ProviderInputs, apiServerKey: String, apiPort: Int, options: GuestOptions,
             hasServiceAccount: Boolean, hasUserCredentials: Boolean): GuestFiles
    fun apply(paths: Paths, settings: Settings, secrets: SecretStore, apiServerKey: String, apiPort: Int,
              options: GuestOptions = GuestOptions())
    fun launchCommand(launcherPath: String, practiceScript: String?): List<String>
    const val GUEST_PRACTICE_LOG = "/root/.hermes/logs/practice-model.log"
}
```
`plan`: practice (`options.practice`) gives `renderPracticeEnv` / `renderPracticeYaml(port, options)` and BOTH install flags false (the credential files in the guest are deleted, so a practice turn cannot spend even if the agent reads the disk); otherwise `renderEnv(...)` / `renderYaml(inputs, options)` and `installServiceAccount = mode == SERVICE_ACCOUNT && hasServiceAccount`, `installAdc = mode == USER_CREDENTIALS && hasUserCredentials`. `apply` reads inputs and files only when not practice (the owner's key never reaches `.env` in practice mode), writes atomically as today. `launchCommand`: `practiceScript == null` -> `listOf("/system/bin/sh", launcherPath, "--", "hermes", "gateway", "run", "--replace", "-v")` (today's list); else `listOf("/system/bin/sh", launcherPath, "--", "/bin/sh", "-c", practiceScript)`.

### 3.4 `runtime/RuntimeManager.kt` additions
```kotlin
@Volatile var lastGuestOptions: GuestOptions = GuestOptions(); private set       // declare next to lastLaunchEnv (init-order rule)
@Volatile var guestCodeReport: List<GuestCodeEntry> = emptyList(); private set
fun onGuestOptionsChanged(restartIfRunning: Boolean)
```
`onGuestOptionsChanged`: no-op when the rootfs is not valid; when `restartIfRunning` and the state is `Running` or `Starting` -> `restart()`; otherwise `rewriteGuestConfigIfInstalled()`. Callers: the privacy row (`false`: the live rewrite is enough), ROBOT's practice switch (`true`).

### 3.5 Launch sequence in `launchHermes` (RuntimeManager.kt:372-415)
1. `val prefs = GuestPrefs.get(context)`; `practice = prefs.practiceModel.value`. The provider validation at :379-381 runs only when not practice.
2. `port = pickFreePort()` (:385); `practicePort = if (practice) pickFreePort(exclude = port) else null` (private fun gets `exclude: Int = -1` and retries until different).
3. `opts = GuestOptions(prefs.webKeyless.value, practicePort)`; `lastGuestOptions = opts`; `runPracticePort = practicePort` (new private `@Volatile`, cleared in `stopInternal` and in `watchExit` of the current run); `paths.hermesLogs.mkdirs()`; `HermesConfig.apply(paths, settings, secrets, secrets.apiKey(), port, opts)` replaces :387.
4. If practice: `PracticeModel.installInto(context, paths.hermesHome)` (3.14); an `IOException` -> `setState(Error("Practice model could not be installed: ..."))`, return false.
5. `guestCodeReport = GuestCodeScan.scan(paths.hermesHome)` (a failure is logged and leaves an empty list); log one line `AppLog.w("Runtime", "guest code not written by the app: hooks/x (2 files), ...")` or `AppLog.i("Runtime", "no foreign hooks or plugins")`.
6. `cmd = HermesConfig.launchCommand(paths.launcher.path, practiceScript)` with `practiceScript = PracticeModel.guestScript(practicePort, HermesConfig.GUEST_PRACTICE_LOG, listOf("hermes","gateway","run","--replace","-v"))` or null; the run header written to `runtime.log` (:395) gains ` practice=<port>`.
7. `rewriteGuestConfigIfInstalled()` (:235-238): options = `GuestOptions(prefs.webKeyless.value, runPracticePort ?: if (prefs.practiceModel.value) 18080 else null)`, so a live web toggle never flips the mode of a running Hermes; while stopped with the switch on it writes the practice files with port 18080 (regenerated at every start anyway).
The sweep of guest processes (`killStaleGuests`, :570) already ends both the mock and Hermes: both carry `PA_IN_PROOT=1` because they start from the same launcher call.

### 3.6 `runtime/GuestCodeScan.kt`
```kotlin
enum class GuestCodeKind { HOOK, PLUGIN }
data class GuestCodeEntry(val kind: GuestCodeKind, val name: String, val path: String /* "hooks/x" */, val files: Int, val bytes: Long,
                          val newestModified: Long, val complete: Boolean, val symlink: Boolean)
object GuestCodeScan {
    const val MAX_ENTRIES = 100; const val MAX_FILES_PER_ENTRY = 2000
    fun scan(hermesHome: File, appWritten: Set<String> = emptySet()): List<GuestCodeEntry>   // sorted by path; never throws
    fun summary(entries: List<GuestCodeEntry>): String                                       // "hooks/x (2 files), plugins/y (1 file, incomplete)"
}
```
`hooks/<d>`: a HOOK; `complete` = `HOOK.yaml` and `handler.py` both exist (H gateway/hooks.py:1-9, :79-84; runs with NO enablement). `plugins/<d>`: a PLUGIN; `complete` = `plugin.yaml` and `__init__.py` (H hermes_cli/plugins.py:1-9; runs only when listed under `plugins.enabled`, which the app never writes and rewrites away at each start). A loose file directly in `hooks/` or `plugins/` becomes an entry with `complete = false`. Symlinks are listed, never followed (`symlink = true`, `files = 0`). `name` keeps only `[A-Za-z0-9._ -]` (other characters become `?`), at most 60 characters. `appWritten` = relative paths the app owns (empty in Wave 0; Wave 3's gate lists its own). Both folders survive updates (`UserState.kt:27`).

### 3.7 Exit reasons and the crash offer (package `dev.pocketagent.app.diag`)
```kotlin
data class ExitReason(val timestamp: Long, val pid: Int, val processName: String, val reason: Int, val status: Int,
                      val importance: Int, val pssKb: Long, val rssKb: Long, val description: String)
enum class ExitKind { CRASH, NATIVE_CRASH, ANR, START_FAILED, LOW_MEMORY, KILLED, USER, UPDATED, SELF, OTHER }
data class CrashDecision(val offer: List<ExitReason>, val newSeenUpTo: Long?)
object ExitReasons {
    fun name(reason: Int): String                 // "CRASH", "SIGNALED", ... "UNKNOWN" (the table of Diagnostics.reasonName)
    fun kind(reason: Int): ExitKind               // 4 CRASH, 5 NATIVE_CRASH, 6 ANR, 7 START_FAILED, 3 LOW_MEMORY, 2/9/14 KILLED, 10/11 USER, 16 UPDATED, 1 SELF, else OTHER
    fun isCrash(reason: Int): Boolean             // kinds CRASH, NATIVE_CRASH, ANR, START_FAILED
    fun decide(all: List<ExitReason>, mainProcess: String, seenUpTo: Long?): CrashDecision
    fun describe(e: ExitReason, formatTime: (Long) -> String): String      // one dump line
    fun read(context: Context, max: Int = 20): List<ExitReason>           // API < 30 or any failure -> emptyList(); newest first
}
class CrashReports(context: Context, diagnostics: Diagnostics,
                   prefs: SharedPreferences = context.getSharedPreferences("crash_reports", Context.MODE_PRIVATE),
                   reader: () -> List<ExitReason> = { ExitReasons.read(context) }) {
    val offer: StateFlow<CrashOffer?>             // CrashOffer(val crashes: List<ExitReason>) ; null = nothing to offer
    fun evaluate()                                // BLOCKING (file and system calls): call off the main thread; updates [offer]
    fun reportText(): String                      // header + one line per crash + diagnostics.buildForClipboard().text (already redacted)
    fun copyReport()                              // BLOCKING: puts reportText() on the clipboard, then dismiss()
    fun dismiss()                                 // marks the shown crashes as seen, clears [offer]
}
```
`decide`: keeps entries with `processName == mainProcess`, `isCrash(reason)` and `timestamp > seenUpTo`, newest first. `seenUpTo == null` (first run of this build) offers nothing and returns `newSeenUpTo = max(timestamp)` (0 if none): an install of this wave never shows an old crash. `mainProcess = context.packageName`, so the `:restart` process and the twin's other package never mix. `evaluate` stores `newSeenUpTo` only for the baseline; `dismiss` stores the newest shown timestamp. Shown only when the user is in the app, never as a notification.

### 3.8 Export logs
```kotlin
// diag/LogExport.kt
object LogExport {
    class Source(val entryName: String, val read: () -> String?)       // null = skip the entry
    fun zip(out: OutputStream, sources: List<Source>, redact: (String) -> String, maxCharsPerEntry: Int = 1_000_000)
}
// diag/Diagnostics.kt (members of the class)
fun exportLogs(): File             // BLOCKING; cache/diagnostics/agiri-logs-yyyyMMdd-HHmmss.zip, written as .part then renamed; keeps the newest 3
fun exportLogsIntent(): Intent?    // BLOCKING; chooser ACTION_SEND, type "application/zip", EXTRA_STREAM = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f), EXTRA_SUBJECT = cfg_diag_share_subject, FLAG_GRANT_READ_URI_PERMISSION, chooser gets FLAG_ACTIVITY_NEW_TASK; null on failure (logged)
```
Entries, flat names: `app.log`, `app.log.1` (from `filesDir/logs`), `runtime.log`, `runtime.log.1`, `last-launch.txt` (`paths.lastLaunch`), `report.txt` (= `buildFull()`). Every entry goes through `Diag.redactor.redact` before it is cut to its LAST `maxCharsPerEntry` characters (`FileUtil.capTail`). `.env`, `config.yaml` and credential files are never included. The folder `cache/diagnostics/` is already mapped in `res/xml/file_paths.xml` and the authority follows `packageName`, so the twin works without a change.

### 3.9 Composables (package `dev.pocketagent.app.ui.diag`)
```kotlin
@Composable fun DiagnosticsExtras()    // under the existing Copy/Share buttons of Diagnostics: Export logs button, "Why the app last stopped" card, "Code Hermes runs" card
@Composable fun CrashReportOffer()     // PocketDialog shown whenever PocketApp.crashReports.offer is non-null; no parameters
@Composable fun WebLookupsRow()        // SubHeading + Muted + PocketSwitchRow bound to GuestPrefs.webKeyless; calls runtime.onGuestOptionsChanged(false)
```
Kind of screen: they are parts of a Dashboard-kind section (Settings > Diagnostics, Behaviour). Their five states:

| State | Export logs | Exit-reason card | Guest-code card | Crash dialog | Web row |
|---|---|---|---|---|---|
| Empty | not possible (app.log always exists) | `cfg_diag_exits_none` | `cfg_diag_guest_none` | not shown | n/a |
| Loading | button disabled, label `cfg_diag_exporting` | `ctl_loading` while `ExitReasons.read` runs on IO | none (list is in memory) | n/a | n/a |
| Error | `ErrorText(cfg_diag_export_failed)`, the button is the retry | read failure shows `cfg_diag_exits_none` | n/a | copy failure keeps the dialog open | n/a |
| Offline | works offline, no text | works offline | works offline | works offline | switch still works; the subtitle says what leaves the phone |
| Needs permission | none | API < 30: `cfg_diag_exits_old_android` | none | none | none |

### 3.10 `data/Transcript.kt`
```kotlin
const val MAX_PART_CHARS = 60_000                       // Hermes cuts a text part at 65,536 (H api_server.py:192, :373-374)
fun splitLong(text: String, max: Int = MAX_PART_CHARS): List<String>
```
`splitLong`: text up to `max` -> `listOf(text)`. Otherwise cut each part at the last `'\n'` in the window `[pos+max/2, pos+max]` (the newline is dropped: Hermes joins the parts with `"\n"`, H api_server.py `_normalize_multimodal_content` ends `"\n".join(...)`, so the result is identical), else at the last `' '` (the space becomes a newline), else hard at `pos+max`, one character earlier when that would split a surrogate pair. Every part is `1..max` characters. Kotlin counts UTF-16 units, Hermes code points, so a Kotlin part under `max` is under it in Hermes too. `build`: the system prompt goes out as one `ChatMessage("system", part)` per part (Hermes joins system messages with `"\n"` without a second cap, H api_server_openai_routes.py:724-726; a part list inside ONE system message would be capped again, H api_server.py:381-410), so the `[chat reference ...]` line at the end is never lost. Prompts under the limit are unchanged byte for byte. The user/assistant half is request R1.

### 3.11 `PocketApp.kt`
New members (declared in this order in `onCreate`): `lateinit var guestPrefs: GuestPrefs; private set` created right after `settings`; `lateinit var crashReports: CrashReports; private set` created right after `diagnostics`. Changes: (a) the engine's `provider = { ... }` lambda (:122) becomes `{ if (guestPrefs.practiceModel.value) "custom" else ProviderConfig.providerName(settings.providerInputs("")) }`, so a per-chat model override in practice mode still goes to the practice endpoint (`ModelIds.fits` always accepts `custom`); (b) :113 becomes `dev.pocketagent.app.ui.pixel.SpriteBank.preload(this)` (STAGE R1; non-blocking, never throws); (c) the new slot, called last (after `routineScheduler.rescheduleAll()`, before the "started" log line):
```kotlin
/** WAVE 0 SLOTS: the one place other areas ask CONFIG to add an init line. One try/catch per slot; never construct a slow thing here. */
private fun wave0Slots() {
    // slot: CRASH  -> try { appScope.launch(Dispatchers.IO) { crashReports.evaluate() } } catch ...
    // slot: TEXT   -> (empty)    slot: DESIGN -> (empty)    slot: NAV -> (empty)    slot: ROBOT -> (empty)
}
```
Requests of other areas for an init line are written as `slot: <AREA>: <exact line>` and go in that area's REQUESTS.

### 3.12 Smoke test edit (needs R4)
(a) Phase 3 (`smoke-test.sh:229-236`, the here-document and the `.env` copy) becomes `cp "$HERE/fixtures/practice.yaml" "$H/config.yaml"; cp "$HERE/fixtures/practice.env" "$H/.env"; chmod 600 "$H/.env"` (the `sed -i '/^GEMINI_/d'` line goes). (b) The fixture loop (:157) becomes `studio express vertex-sa vertex-user studio-nokey studio-effort studio-nokeyless`. (c) After the `/v1/capabilities` check (:173) of the `studio` boot only: `curl -s -H "Authorization: Bearer $KEY" http://127.0.0.1:$PORT/v1/toolsets` (H api_server.py:2401-2428 lists every configurable toolset with `enabled` and its tool names, from the same `_get_platform_tools`); `python3 -c` asserts `enabled` is true for `AGENT_TOOLSETS` and false for `browser cronjob image_gen tts connections`. (d) After phase 3's `session continuity` block: `sleep 5; ! grep -q "You name chat sessions" "$MOCKLOG"` (the title prompt, H agent/title_generator.py:121); this needs R3 (the mock must log message heads).

### 3.13 Deviations from the plan (open for the lead)
- D1: `connections` (`manage_connections`) is also withheld. It is the 24th default tool, available to every guest identity (H tools/connectors/tool.py:161, tools/connectors/gateway/config.py:84-107) and its first use creates an identity at a hosted gateway (H tools/connectors/gateway/bridge.py:18). Principle 11 (what leaves the phone is listed). To restore it: add `"connections"` to `AGENT_TOOLSETS` and remove it from `WITHHELD_TOOLSETS`; the fixtures and the test follow from the lists.
- D2: no `auxiliary` pinning is written (see 3.2, last bullet).

### 3.14 What ROBOT provides and I consume (`runtime/PracticeModel.kt`, package `dev.pocketagent.app.runtime`; ROBOT owns it; the object must load in a JVM test, so no Android type in a constant or an initialiser)
```kotlin
object PracticeModel {
    const val MODEL_ID = "practice-model"
    const val API_KEY = "practice"                                       // not a secret
    const val GUEST_ABSOLUTE_PATH = "/root/.hermes/agiri/practice_model.py"
    fun baseUrl(port: Int): String                                       // "http://127.0.0.1:$port/v1"
    fun installInto(context: Context, hermesHome: File): File            // asset -> <hermesHome>/agiri/practice_model.py, atomic, only when different; IOException on failure; called at EVERY practice start
    fun guestScript(port: Int, logPath: String, hermesArgv: List<String>): String
}
```
`guestScript` is POSIX `sh` text for `/bin/sh -c` inside the guest: pick the Python of Hermes's environment (`/root/.hermes/installs/*/environments/*/venv/bin/python`, the same glob the self-test uses at RuntimeManager.kt:638, else `python3`); start `"$PY" GUEST_ABSOLUTE_PATH --port <port> --log <logPath>` in the background with its output appended to `<logPath>.out`; wait up to 15 s until `curl -s -m 1 http://127.0.0.1:<port>/v1/models` answers, else print `PRACTICE-MODEL-FAILED` to stderr and `exit 3`; then `exec` the single-quoted `hermesArgv`. `ProviderConfig` calls only `MODEL_ID`, `API_KEY`, `baseUrl`.

## 4. Shared symbols you CONSUME

| From | Exact symbol | Use |
|---|---|---|
| ROBOT | `PracticeModel` of 3.14 | launch, config |
| STAGE | `SpriteBank.preload(context: Context)`; `SpriteBank.loadErrors: List<String>` | PocketApp, dump |
| NAV | `Modifier.testTag` becomes visible to `uiautomator` through `testTagsAsResourceId` at the root; the one `CrashReportOffer()` call (R2) | robot, offer |
| DESIGN (today's, must keep working or be re-exported) | `PocketDialog(title: String, onDismiss: () -> Unit, confirm: (@Composable () -> Unit)? = null, dismiss: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit)`; `PixelButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: PixelIcon? = null, kind: ButtonKind = ButtonKind.PRIMARY, enabled: Boolean = true)`; `PocketSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, subtitle: String? = null)`; `Pocket.colors`; `PixelIcon.SHARE/COPY/FILE/GLOBE` | my composables |
| SWEEP/DESIGN | `internal` helpers of `ui/settings/CtlUi.kt`: `CText(text, modifier, color, size, weight, mono, maxLines, overflow, lineHeight)`, `Muted(text, modifier, size)`, `ErrorText(text, modifier)`, `SubHeading(text, modifier)`. If they move, keep the names. | my composables |
| Existing, unchanged | `AppLog.i/w/e`, `Diag.redactor.redact`, `Redactor.maskEnvSecrets`, `FileUtil.{capTail,capHead,writeAtomic,chmod,formatBytes}`, `Paths` fields (`hermesHome`, `hermesLogs`, `runtimeLog`, `lastLaunch`, `diagnosticsDir`, `launcher`), `UserState` | all |
| WORDS (last) | converts my `stringResource(R.string.cfg_*)` and `getString` into `t(...)`, `left`/`right` into `start`/`end` (I use neither) | later |

## 5. Strings (file `res/values/strings_config.xml`, prefix `cfg_`; English only; each key carries an XML comment `<!-- note -->` with its translator note)

| Key | English | Translator note |
|---|---|---|
| `cfg_diag_export_title` | Export logs | Button, one line, about 14 characters. |
| `cfg_diag_export_sub` | One zip file with the app log, the runtime log and the last launch line. Keys and passwords are removed first. | Line under the button, up to 3 lines. "zip" is a file type. |
| `cfg_diag_exporting` | Exporting… | Same button while it works. |
| `cfg_diag_export_failed` | The logs could not be exported. Try again. | Error line under the button. |
| `cfg_diag_share_subject` | Agirî logs | Mail subject in the share sheet. Agirî is the app name; keep it. |
| `cfg_diag_exits_title` | Why the app last stopped | Card title, one line. |
| `cfg_diag_exits_none` | No record yet. | Empty card. |
| `cfg_diag_exits_old_android` | Android 11 or newer is needed to read this. | Card on old phones. |
| `cfg_exit_crash` / `_native` / `_anr` / `_start` | The app crashed / The app crashed in native code / The app stopped responding / The app could not start | One sentence per kind of exit; used in the card and in the crash dialog (`%1$s`). No full stop. |
| `cfg_exit_memory` / `_killed` / `_user` / `_updated` / `_self` / `_other` | Android needed the memory back / Android stopped the app / You closed the app / The app was updated / The app closed itself / The app stopped for another reason | Same use. |
| `cfg_diag_guest_title` | Code Hermes runs from its folders | Card title. "Hermes" is a product name. |
| `cfg_diag_guest_none` | Nothing found in the hooks and plugins folders. | Empty card. |
| `cfg_diag_guest_found` (plurals) | one: 1 item in the hooks and plugins folders was not written by the app. other: %1$d items in the hooks and plugins folders were not written by the app. | `%1$d` is a count; each row below it is `kind name`. |
| `cfg_guest_kind_hook` / `_plugin` | Hook / Plugin | Row prefix, one word. |
| `cfg_crash_title` | Agirî stopped unexpectedly | Dialog title, up to 40 characters. |
| `cfg_crash_body` | %2$s: %1$s. A report can be copied for the person who fixes problems. It contains no keys or passwords. | `%1$s` = one of the `cfg_exit_*` sentences, `%2$s` = date and time from the platform formatter. |
| `cfg_crash_copy` / `cfg_crash_not_now` / `cfg_crash_copied` | Copy report / Not now / Report copied | Two dialog buttons (max 14 characters) and a confirmation line. |
| `cfg_priv_title` | Privacy | Small heading in Behaviour. |
| `cfg_priv_web_title` | Web lookups | Switch title. |
| `cfg_priv_web_sub` | When the agent searches the web or reads a page, the search words or the page address go to free public services (Exa, Parallel, Firecrawl and Keenable). No name or account is sent. Turn this off and the agent has no web search. | Subtitle, up to 5 lines. The four names are product names; keep them. |

## 6. Manifest entries
None. `res/xml/file_paths.xml` already maps `cache-path name="diagnostics" path="diagnostics/"` and the manifest provider authority is `${applicationId}.fileprovider`. `ApplicationExitInfo` and the share chooser need no permission and no `<queries>`. NAV must only keep that provider entry when it rewrites the manifest.

## 7. REQUESTS to other areas
- R1 **LEAD (no owner for `api/HermesApi.kt` in Wave 0; give CONFIG the one function or name an owner)**: in `ChatRequest.toJson` (HermesApi.kt:57-70) build `content` from `Transcript.splitLong(msg.content)`: with no images, `if (p.size <= 1) obj.put("content", msg.content) else obj.put("content", JSONArray().also { a -> p.forEach { a.put(JSONObject().put("type","text").put("text", it)) } })`; with images, the first text part is replaced by the same list (the placeholder `"Analyze the attached image(s)."` only when `msg.content` is blank). Role `system` is never touched (it is split in `Transcript.build`). Test: `ChatRequestSplitTest` (a 130,000-character user message gives 3 text parts; a 10-character one stays a plain string).
- R2 **NAV**: `ui/AppRoot.kt`: call `CrashReportOffer()` once inside the themed root, over every route (a dialog; no state to hoist). `testTagsAsResourceId` at the root (already your item).
- R3 **ROBOT**: `rootfs/smoke/mock_llm.py` (and the same logging in the practice asset): in the `POST` log line add `"heads": [first 60 characters of every message's text]`; two keywords for the proofs of section 9: `TOOLS` answers `tools: <sorted names of the request's tools joined by comma>`, `STATS` answers `requests=<n> titleCalls=<m>` where a title call is any request whose text contains `You name chat sessions`.
- R4 **LEAD**: assign `rootfs/smoke/smoke-test.sh` to CONFIG for the edit of 3.12 (it has no owner in the table).
- R5 **ROBOT**: `ui/settings/RuntimeSection.kt`: (a) in `DiagnosticsSection` add `DiagnosticsExtras()` (import `dev.pocketagent.app.ui.diag.DiagnosticsExtras`) after the share button (RuntimeSection.kt:135-139); (b) the switch "Practice model (no tokens)": `val g = remember { GuestPrefs.get(ctx) }`, checked = `g.practiceModel.collectAsState().value`, on change `g.setPracticeModel(v); PocketApp.from(ctx).runtime.onGuestOptionsChanged(true)`. The persisted value lives in `GuestPrefs`, not in `PracticeModel`.
- R6 **BUILD**: nothing for the twin (`Diagnostics`, `FileProvider`, `CrashReports` read `context.packageName`). For R8: nothing is looked up by name in my files.

## 8. Tests (JVM, `app/src/test/java/dev/pocketagent/app/`; logic is pure, files through `TemporaryFolder`, prefs through a map-backed `SharedPreferences` proxy as in `RuntimeManagerInitTest`)
| Class . test | Proves |
|---|---|
| `ProviderConfigTest` (all existing tests stay; the five inline expectations for OPENROUTER/ANTHROPIC/OPENAI/CUSTOM and `:208-210` append `ProviderConfig.renderGuardYaml()`; the golden `check()` reads the changed fixtures) . `guardBlockTextIsExact` | the literal text of 3.2, once, in the test source (not through the function) |
| . `everyModeEndsWithTheGuardBlock` | all 7 `AuthMode` values: yaml `endsWith` the guard, exactly one `platform_toolsets:` |
| . `toolListIsFailClosed` | the `- x` lines equal `AGENT_TOOLSETS`; disjoint from `WITHHELD_TOOLSETS` plus `clarify kanban computer_use` |
| . `sideTasksArePinnedByConstruction` | top-level keys are a subset of `model vertex agent platform_toolsets auxiliary security web`; `provider:` present; no `free_only`, `fallback_providers`, `openrouter_model` anywhere; `auxiliary` holds only `title_generation` |
| . `webSwitchIsWritten` | `GuestOptions(webKeylessFallback = false)` gives `keyless_fallback: false` and equals `studio-nokeyless.yaml` |
| . `practiceFixturesMatch` | `renderPracticeYaml(18080)` equals `practice.yaml`, `renderPracticeEnv(apiKey)` equals `practice.env`; the env has no `*_API_KEY=` line except `API_SERVER_KEY`; yaml has `api_key: "practice"` |
| `HermesConfigPlanTest . practiceInstallsNoCredentialsAndNoProviderKey` | inputs with a Gemini key and imported files + `GuestOptions(practicePort = 1)`: `env` lacks the key, both install flags false |
| . `normalModeInstallsOnlyWhatTheModeNeeds` | SERVICE_ACCOUNT with both files present: only `installServiceAccount` |
| . `launchCommandFormsAreExact` | the two lists of 3.3 |
| `GuestCodeScanTest . emptyHomeGivesNothing`, `hookDirectoryIsCompleteWithBothFiles`, `pluginWithoutInitIsIncomplete`, `appWrittenIsExcluded`, `symlinkIsListedNotFollowed` (skipped by `Assume` where symlinks need a privilege, as in `FileAndUserStateTest`), `namesAreSanitisedAndCapped`, `entryAndFileCapsHold`, `summaryReadsPlainly` | 3.6 |
| `GuestPrefsTest . defaultsAreKeylessOnPracticeOff`, `valuesPersistAndFlowsUpdate`, `wrongTypeFallsBackToDefault` | 3.1 |
| `ExitReasonsTest . namesAndKinds`, `decideBaselinesOnFirstRun`, `decideOffersOnlyMainProcessCrashes`, `decideRespectsSeenUpTo`, `signaledAndUserRequestedAreNeverOffered`, `describeLineHasReasonAndStatus` | 3.7 |
| `LogExportTest . zipHasFlatNamedEntries`, `everyEntryIsRedacted` (a registered secret and a Bearer token never appear in any entry), `missingSourceIsSkipped`, `bigEntryKeepsItsTail`, `envAndYamlAreNeverSources` | 3.8 |
| `TranscriptSplitTest . shortTextIsOnePart`, `partsAreWithinMax`, `rejoinWithNewlineIsLossless` (cut at newlines: `joinToString("\n") == text`), `spaceAndHardCutsOnlyChangeWhitespace` (equal after deleting `\n` and spaces), `surrogatePairIsNeverSplit`, `longPersonaBecomesSeveralSystemMessagesWithTheReferenceLast`, `shortPromptIsUnchangedByteForByte` | 3.10 |
| `RuntimeManagerInitTest` | unchanged and still green: the constructor signature and the last-member `init` are untouched |

## 9. Robot hooks
- testTags (exact): `diag.exportLogs` (button), `diag.exitReasons` (card), `diag.guestCode` (card; its text lists `hooks/x`), `crash.offer` (dialog content), `crash.copy`, `crash.dismiss` (its two buttons), `privacy.webLookups` (the whole switch row; set on a wrapping `Box`, `PocketSwitchRow` has no modifier). Dotted names, as STAGE's `stage.hearth`.
- Practice keywords needed (R3): `TOOLS` and `STATS`. Scenarios:
  - `tools-are-short`: send `TOOLS`; `expect` `terminal`, `read_file`; `expectNot` `browser_`, `cronjob_manage`, `image_generate`, `text_to_speech`, `manage_connections`.
  - `no-title-call`: send two plain messages, then `STATS`; `expect` `titleCalls=0`.
  - `export-logs`: link to Settings > Diagnostics, `tap diag.exportLogs`, `wait` for the chooser, `key back`; then `run-as <twin> ls cache/diagnostics` shows `agiri-logs-*.zip`; `unzip -l` lists the six entries; `noCrash`.
  - `crash-offer`: `adb shell am crash <twin package>`, launch, `expect crash.offer`, `tap crash.copy`, `expectNot crash.offer`; relaunch, `expectNot crash.offer`. (Assumption to verify: `am crash` is recorded as REASON_CRASH; fallback is a debug-only intentional throw in a later wave.)
  - `guest-code-report`: with `run-as`, create `files/rootfs/root/.hermes/hooks/demo/HOOK.yaml` and `handler.py`, restart the engine, open Diagnostics, `expect diag.guestCode` contains `hooks/demo`.
  - `web-switch`: link to Behaviour, `tap privacy.webLookups`; `run-as cat files/rootfs/root/.hermes/config.yaml` has `keyless_fallback: false`; tap again, `true`. `practice-switch` (ROBOT's tag): after the toggle `run-as cat .../.env` has no `API_KEY` line other than `API_SERVER_KEY` and `config.yaml` has `provider: custom`.
- Debug links: none beyond NAV's "open any route".

## 10. Experiment branches
- **S5 passes (written above)**: one launch runs the scripted model and Hermes inside the guest.
- **If S5 fails** (the launcher's sweep or proot's process handling kills the mock, or it cannot be reached): the model runs inside the app's own process on loopback. Deltas, all inside my files except the first: (1) ROBOT replaces `guestScript` by `fun startInProcess(context: Context, port: Int): AutoCloseable` (a `java.net.ServerSocket` server in Kotlin speaking the same scripted protocol; `installInto` and `GUEST_ABSOLUTE_PATH` disappear). (2) `launchHermes` step 4 calls `startInProcess` and keeps the handle in `@Volatile private var practiceHost: AutoCloseable?`; step 6 uses `launchCommand(launcher, null)`; `stopInternal` and `watchExit` of the current run close it, and a new start closes the old one first. (3) `plan`, `renderPracticeYaml/Env`, fixtures, `GuestPrefs`, the tests and every UI item are unchanged (the config still points at `http://127.0.0.1:<port>/v1`). The smoke phase 3 keeps using the Python mock on the host.
- S2 (R8): nothing in my files is found by name. S4, S6 are not mine.

## 11. Risks, and where the plan is wrong or impossible given the real code
1. **`text_to_speech` is already off the API server's list** (H toolsets.py:191-195 `_core_without("text_to_speech", "clarify", "computer_use", kanban=False)`), and `image_generate` is gated by a `check_fn` (H tools/image_generation_tool.py:1065). Plan D15/D6 call both default tools of an API client; for `api_server` they are not. The explicit list is still right: it makes the guarantee independent of Hermes's defaults and of a future image provider.
2. **B13 as written is already closed in v0.23.3**: once `model.provider` is set, side tasks use the main model or nothing (H auxiliary_client.py:4420-4432, 4459-4466). The plan's "pin" has nothing to write, and the one obvious knob, `auxiliary.free_only`, would break OpenRouter mode (3.2).
3. **B21 is only half closed**: `cronjob_manage` leaves the list, but the terminal tool can still run `hermes cron ...`, and the gateway runs a cron ticker every 60 s (H gateway/run.py:4456). No configuration switch for the ticker was found. Wave 3's gate has to cover it; say so in the proof of Wave 0.
4. **The 60,000-character split cannot be done in my files alone**: it needs `api/HermesApi.kt` (R1). One text part is not enough for a system message (3.10).
5. **"Diagnostics shows exit reasons" exists today** (Diagnostics.kt:131-139, API 30 guarded); the new work is the structured reader, the cards and the crash offer. `ApplicationExitInfo` knows only deaths of the app's own processes; a SIGKILL from the phantom-process killer on the guest is `SIGNALED`, not a crash, and is deliberately never offered.
6. **`allow_lazy_installs: false` may queue Hermes notices** ("Hermes did not fetch it", H hermes_cli/left_core_migration.py:875-880, memory_provider_migration.py:303) or log words the smoke grep treats as errors (`ImportError`). Unproven until the CI smoke runs; if a notice reaches a chat, the key moves to the environment variable `HERMES_DISABLE_LAZY_INSTALLS=1` (H default_plugins.py:126), same effect.
7. **Hooks run without any enablement** (H gateway/hooks.py:79-84) and survive updates (`UserState.kt:27`): the report only lists them in Wave 0, as the plan says; nothing deletes anything.
8. **Practice mode and the key**: the owner's provider key is not written anywhere in practice mode and the credential files are deleted from the guest, but the key is still in the app's private storage; nothing else in Wave 0 sends by itself, so the engine's `provider` lambda (PocketApp.kt:122) is the only place that follows the switch today. Wave 4's helpers must read `GuestPrefs.practiceModel`.
9. **Toggling the practice switch restarts Hermes** (a running turn is lost); the switch must say so (ROBOT's string).
10. The practice script's port is picked like the API port (`pickFreePort`), with a small race against other apps on the shared loopback; the 15 s wait in `guestScript` turns a lost race into a visible exit 3, not a hang.
11. `ProviderConfigTest` has inline expectations that will fail until all five are updated: do them in the same commit as the fixtures (CI boots the same fixtures).

## Open questions for the lead
1. D1: withhold `connections` as specified, or keep to the plan's four?
2. Should the web switch travel in a backup? It needs `"guest" to mapOf("web_keyless" to 'b')` in `BackupManager`'s `PREF_SCHEMA` (nobody owns that file in Wave 0). The practice flag must stay out of it.
3. Who owns `api/HermesApi.kt` and `rootfs/smoke/smoke-test.sh` (R1, R4)?
4. Is the crash offer allowed to appear as a dialog over any screen (R2), or should it be a card on Home (Wave 1)?
