# Wave 0 contract, area ROBOT: smoothness numbers, phone robot, practice model, UI ratchet lint, memory numbers (draft)

Written 2026-11-25 against the tree at commit `344829f`. Line numbers were taken from commands run for this draft. Plan features: 0.2, 0.3, 0.4, 0.5 (second bullet), 0.12. Written for the PASS case of S5 and S7; section 10 has the fallbacks. Read together with the drafts of BUILD (`tools/build-ids.ps1`, `IS_TWIN`, twin `dev.pocketagent.app.debug`) and NAV (`NavTags`, the one debug link `dev.pocketagent.app.DEBUG`, `robotRoot()`).

## 1. Scope

| # | Work item | Feature |
|---|---|---|
| R1 | `tools/phone-lib.ps1`: adb runner, UI dump parser, selector matching, the step engine, automatic checks, report writer. Used by R2, R3, R6 | 0.3 |
| R2 | `tools/phone-test.ps1` runner + scenario format (JSON) + the vocabulary of section 3.4 + the two automatic checks (spoken label, cut at double text size) | 0.3 |
| R3 | `app/.../ui/RobotTags.kt`: the one registry of test tags for every control outside the shell (NAV owns `NavTags`) | 0.3 |
| R4 | 39 scenario files (section 3.5), `routes.json`, seed generators, `a11y-allow.json`, README | 0.3, 0.4 |
| R5 | Practice model: ONE python file (`rootfs/smoke/mock_llm.py`, stays the CI mock) with the nine keywords + 3 helper keywords + fixed usage; `runtime/practice-start.sh`; `runtime/PracticeModel.kt`; the switch in `RuntimeSection.kt`; phase 3 of `smoke-test.sh` runs through the same start script | 0.4 |
| R6 | `tools/phone-perf.ps1` modes Smooth, ColdStart, Memory; seven perf scenarios; JSON under `dist/perf/<label>/`; `docs/perf/w0.md` layout | 0.2, 0.12 |
| R7 | `tools/lint-ui.ps1` ratchet + `tools/lint-ui-baseline.json` | 0.5 |
| R8 | Old debt: 14 scenarios, one per item of plan 0.4 (marked D in 3.5) | 0.4 |

All core. **Cut first, in this order:** (1) R6 `-Mode Memory` and keyword `BALLAST` (removed from the python table and from `PracticeModel.KEYWORDS` together): 0.12's numbers are then taken by hand with the raw commands of 3.2 (the app loses the repeatable memory table). (2) SAF picker automation (`pick` op) and the 3 scenarios that need it (`bot-export-import`, `restore-into-wiped-twin`): they become "robot does everything but the picker, the lead taps it once". (3) `expectColor` and the pixel check of `terminal-colours`: the scenario ends in a screenshot judged by eye. Also dependent on an unowned symbol: the Export logs button (section 11, item 9); dropped if nobody provides `MainViewModel.shareLogsIntent()`.

## 2. Files (ROBOT owns them exclusively)

| File | Change |
|---|---|
| `tools/phone-lib.ps1` | New. Dot-sourced after `tools/build-ids.ps1`. PowerShell 5.1 and 7. Defines nothing at load except functions and the `PaProc` type (same guard and source as `local-build.ps1:83`) |
| `tools/phone-test.ps1` | New. Runner, section 3.2 |
| `tools/phone-perf.ps1` | New. Section 3.2 |
| `tools/lint-ui.ps1`, `tools/lint-ui-baseline.json` | New. Section 3.2 and 3.6 |
| `tools/scenarios/*.json` (39), `tools/scenarios/perf/*.json` (P1..P7, M0..M4), `tools/scenarios/routes.json`, `tools/scenarios/a11y-allow.json`, `tools/scenarios/cut-allow.json`, `tools/scenarios/README.md`, `tools/scenarios/fixtures/dump-sample.xml`, `tools/scenarios/fixtures/gfxinfo-sample.txt` | New |
| `rootfs/smoke/mock_llm.py` | Modify (name kept so `smoke-test.sh:238` and CI keep working). It IS the practice model; BUILD copies it into the APK as asset `practice_model.py` (request 7.1). **Unowned in the plan's table; ROBOT takes it** |
| `runtime/practice-start.sh` | New, POSIX sh, LF (`.gitattributes` has `*.sh text eol=lf`). Shipped as asset `practice-start.sh` |
| `rootfs/smoke/smoke-test.sh` | Modify phase 3 only (lines 227 to 242): config from the fixture `practice.*` and gateway start through `practice-start.sh`. **Unowned; ROBOT takes it** |
| `.gitattributes` | Add `*.py text eol=lf`. **Unowned; ROBOT takes it** |
| `app/src/main/java/dev/pocketagent/app/runtime/PracticeModel.kt` | New: `PracticeModel`, `PracticePrefs` |
| `app/src/main/java/dev/pocketagent/app/ui/RobotTags.kt` | New |
| `app/src/main/java/dev/pocketagent/app/ui/settings/RuntimeSection.kt` | Modify: practice switch, tags (state label, Start, Stop, Restart), Export logs button in `DiagnosticsSection` (line 129), keywords hint. Line 78 `startsWith("still starting")` uses the constant of 4.2 |
| `app/src/main/res/values/strings_robot.xml` | New, prefix `rbt_` |
| `docs/perf/w0.md` | New: layout of 3.3 only; the lead fills in numbers |
| Tests | `app/src/test/java/dev/pocketagent/app/PracticeModelTest.kt`, `PracticePrefsTest.kt`, `RobotTagsTest.kt`, `ScenarioFilesTest.kt` |

## 3. Shared symbols ROBOT provides

### 3.1 Kotlin

```kotlin
package dev.pocketagent.app.runtime
object PracticeModel {
    const val MODEL_ID = "practice-model"                 // model.default; the mock also lists "mock-model" (the CI fixtures use it)
    const val PROVIDER = "custom"                         // model.provider AND the per-request `provider` while the switch is on
    const val ASSET_SCRIPT = "practice_model.py"; const val ASSET_START = "practice-start.sh"
    const val HOST_DIR_NAME = "practice"                  // File(paths.hermesHome, HOST_DIR_NAME); regenerated at every start, not in UserState.NAMES
    const val HOST_SCRIPT_NAME = "practice_model.py"; const val HOST_START_NAME = "start.sh"   // file names inside hostDir
    const val GUEST_DIR = "/root/.hermes/practice"
    const val GUEST_START = "/root/.hermes/practice/start.sh"
    const val GUEST_REQUEST_LOG = "/root/.hermes/practice/requests.jsonl"
    val KEYWORDS: List<String>                            // RUN_TOOL ASK_APPROVAL LONG THINK SLOW FAIL MEMORY FORGE MEDIA PROMPT_HAS PROMPT_SIZE BALLAST
    fun baseUrl(port: Int): String                        // "http://127.0.0.1:<port>/v1"; IllegalArgumentException unless port in 1..65535
    fun inputs(base: ProviderInputs, port: Int): ProviderInputs   // base.copy(mode = CUSTOM, model = MODEL_ID, baseUrl = baseUrl(port), geminiApiKey = "", providerKey = "")
    fun effective(base: ProviderInputs, enabled: Boolean, port: Int): ProviderInputs  // enabled ? inputs(base, port) : base (same instance)
    fun guestCommand(port: Int, hermesArgv: List<String>): List<String>  // listOf("sh", GUEST_START, port.toString()) + hermesArgv
    fun hostDir(paths: Paths): java.io.File
    fun install(context: android.content.Context, paths: Paths): java.io.File  // writes both assets into hostDir (atomic, only when bytes differ, start.sh mode 0700); returns hostDir; IOException on failure
    internal fun installInto(dir: java.io.File, script: ByteArray, start: ByteArray): Boolean // true when anything was (re)written; pure file logic for tests
}
internal interface BooleanStore { fun get(): Boolean; fun put(v: Boolean) }
class PracticePrefs internal constructor(store: BooleanStore) {
    val enabled: StateFlow<Boolean>                       // default false
    fun set(on: Boolean)                                  // no-op when unchanged
    companion object { fun get(context: android.content.Context): PracticePrefs }  // process singleton; SharedPreferences file "practice", key "enabled"; not in BackupCore.PREF_SCHEMA on purpose
}
```
Behaviour: while `enabled` is true Hermes is configured ONLY from `effective(...)`: the owner's credentials are not copied into the guest (`HermesConfig.apply` already deletes them for a CUSTOM mode, `HermesConfig.kt:11`), Settings is never written, so turning the switch off and starting again regenerates the owner's files exactly. The guest wrapper is the only thing that starts the mock.

```kotlin
package dev.pocketagent.app.ui
object RobotTags {   // every name below is its own `const val NAME = "value"` (written compactly here); plain strings; owners write Modifier.testTag(RobotTags.X); NavTags (tab.*, screen.*, share.*) and DESIGN's literals (sheet, dialog, petnote, gallery) stay theirs
    const val RUNTIME_STATE = "settings.runtime.state"; RUNTIME_START = "settings.runtime.start"; RUNTIME_STOP = "settings.runtime.stop"
    const val RUNTIME_RESTART = "settings.runtime.restart"; RUNTIME_PRACTICE = "settings.runtime.practice"; DIAG_EXPORT_LOGS = "settings.diag.export"
    const val HOME_STATE = "home.state"; HOME_START = "home.start"; CHATS_NEW = "chats.new"
    const val CHAT_INPUT = "chat.input"; CHAT_SEND = "chat.send"; CHAT_STOP = "chat.stop"; CHAT_LIST = "chat.list"; CHAT_THOUGHT = "chat.thought"
    const val CHAT_APPROVAL_ONCE = "chat.approval.once"; CHAT_APPROVAL_SESSION = "chat.approval.session"; CHAT_APPROVAL_ALWAYS = "chat.approval.always"; CHAT_APPROVAL_DENY = "chat.approval.deny"
    const val CHAT_MEMORY_OPEN = "chat.memory.open"; CHAT_FORGED_CHAT = "chat.forged.chat"; CHAT_MENU = "chat.menu"; CHAT_FIND = "chat.find"
    const val HERMES_TAB_SOUL = "hermes.tab.soul"; HERMES_TAB_MEMORY = "hermes.tab.memory"; HERMES_TAB_SKILLS = "hermes.tab.skills"
    const val HERMES_DOC_EDITOR = "hermes.doc.editor"; HERMES_DOC_SAVE = "hermes.doc.save"; HERMES_SKILL_NEW = "hermes.skill.new"; HERMES_SKILL_NAME = "hermes.skill.name"
    const val HERMES_SKILL_CREATE = "hermes.skill.create"; HERMES_SKILL_DELETE = "hermes.skill.delete"; HERMES_SKILL_DELETE_CONFIRM = "hermes.skill.delete.confirm"
    const val TERMINAL_INPUT = "terminal.input"; TERMINAL_RUN = "terminal.run"; TERMINAL_OUTPUT = "terminal.output"
    const val USAGE_TODAY = "usage.today"; USAGE_BUDGET_LIMIT = "usage.budget.limit"; USAGE_BUDGET_WARN = "usage.budget.warn"; USAGE_BUDGET_BLOCK = "usage.budget.block"
    const val BEHAVIOUR_IDLE_MINUTES = "behaviour.idle.minutes"
    const val BACKUP_SAVE = "backup.save"; BACKUP_RESTORE = "backup.restore"; BACKUP_CONFIRM = "backup.confirm"; BACKUP_RESULT = "backup.result"
    const val BOTS_EXPORT = "bots.export"; BOTS_IMPORT = "bots.import"
    fun chatsRow(i: Int) = "chats.row.$i"      // i = 0 is the top row
    fun chatMsg(i: Int) = "chat.msg.$i"        // i = 0 is the NEWEST message (stable while the list is virtualised); a tool card is chat.tool.<i> counted among tool cards only (chatTool)
    fun chatTool(i: Int) = "chat.tool.$i"
    fun settingsSection(key: String) = "settings.section.$key"   // provider usage behaviour appearance voice language backup runtime diagnostics about
    fun hermesSkillRow(name: String) = "hermes.skill.row.$name"; fun botsRow(i: Int) = "bots.row.$i"
}
```
A tag on a container (Box) is fine: the robot taps the centre of its bounds and reads `checked` from the first checkable descendant.

### 3.2 Scripts: exact command lines (PowerShell 5.1 and 7; run `powershell -NoProfile -ExecutionPolicy Bypass -File tools\<x>.ps1 ...`)

| Script | Command line | Output and exit codes |
|---|---|---|
| phone-test | `phone-test.ps1 [-Suite core\|smoke\|slow\|all] [-Scenario a,b] [-Owner] [-Package id] [-Serial id] [-Out dir] [-Var name=value[,..]] [-NoA11y] [-NoCut] [-StopOnFail] [-List] [-SelfTest] [-Adb path]` | Default suite `core` (flag `core`, no `slow`), default target the twin (`$PaTwinPackage`); `-Owner` needs the explicit switch, makes `smoke` the default suite and runs only scenarios with `"owner": true`. Folder `dist\phone-test\<versionName>\<yyyyMMdd-HHmmss>\` with `report.json`, `report.md`, `<scenario>\NN-<op>.png`, `dump-NN.xml` (failures), `logcat.txt`, `app.log` (twin). Lines: `PASS name 41.2s`, `FAIL name step 7 expect tag=chat.msg.1 not found after 8s (shot NN-expect.png)`, `SKIP name (reason)`, `A11Y screen=<sig> id=<tag or class@bounds> no spoken label`, `CUT screen=<sig> "<text>"`, last line `SUMMARY pass=n fail=n skip=n a11y=n cut=n`. Exit 0 all pass; 1 any FAIL or unlisted finding; 2 usage or environment (no device, package missing, screen locked, JSON invalid); 3 nothing ran |
| phone-perf | `phone-perf.ps1 [-Mode Smooth\|ColdStart\|Memory\|All] [-Scenario P1,P3] [-Owner] [-Package id] [-Serial id] [-Label name] [-Repeat n] [-Out dir] [-Report dirA,dirB] [-Markdown file] [-Enforce] [-LimitMb n] [-SelfTest] [-Adb path]` | Default Smooth, repeat 3 (ColdStart 7). `dist\perf\<label>\<scenario>.json`, `summary.json`, `memory.json`; label defaults to `<versionName>-<debuggable ? debug : optimised>` from `dumpsys package`. Per scenario one line: `P3 chat200-scroll frames=1450 within8.3=97.1% p90=6.2 p95=7.4 p99=11.9 worst=24.8 over16.7=3 over33=0 budget=PASS`. `-Report a,b` prints markdown tables (side by side) to stdout or `-Markdown file`. Exit 0 after a run (budgets reported); 1 only with `-Enforce` and a budget missed; 2 environment |
| lint-ui | `lint-ui.ps1 [-Check] [-Update] [-Init] [-Json] [-SelfTest]` | `-Check` (default): exit 0 when no count is above the baseline (prints `RATCHET rule 17 (baseline 21): run -Update` when lower), 1 when any rule or any file is above (prints `OVER rule file n > m`), 2 baseline missing/unreadable (use `-Init` once). `-Update` lowers the baseline, refuses to raise. `-Init` writes it only when absent. Runs on pwsh 7 on Linux: forward slashes in keys, `-LiteralPath` everywhere |

`-SelfTest` of each script: no phone; parses the fixtures in `tools/scenarios/fixtures/` (a UI dump with a labelled and an unlabelled clickable, a gfxinfo capture) and embedded snippets, prints `SELFTEST OK`, exit 0/1. Hand commands for 0.12 (cut item 1): `adb shell dumpsys meminfo <pkg>`, `adb shell ps -A -o USER,PID,RSS,NAME`, `adb shell am memory-limiter status` (this command could not be checked without the phone; the script stores its raw text or `unavailable`).
House rules for all three: `Set-StrictMode -Version 2`, `$ErrorActionPreference='Stop'`, host paths only as separate array elements or through `Quote-Arg`, `-LiteralPath`, never `Invoke-Expression`; device commands are ONE argument with single quotes inside, and no host path ever reaches the device command text (files travel by `adb push/pull` through `/data/local/tmp`, as AGENTS.md says for `run-as`); JSON read with `Get-Content -Raw -Encoding UTF8`, written as UTF-8 without BOM; PNGs by `screencap` to `/data/local/tmp` + `adb pull` (never `exec-out`); `MSYS_NO_PATHCONV` irrelevant (no Git Bash). Component names only through `Get-PaComponent $pkg 'dev.pocketagent.app.MainActivity'` (BUILD). The screen must be on and unlocked: the robot checks `dumpsys window` for keyguard and exits 2 with a message.

### 3.3 Perf: scenarios, parsing, files

Scenarios are scenario files (same vocabulary) with a `"measure"` block; the engine runs `dumpsys gfxinfo <pkg> reset`, the steps, polling `dumpsys gfxinfo <pkg> framestats` every 400 ms (the buffer holds only 120 frames), merging by `IntendedVsync`.

| Id | File | What | Budget (design language 11) |
|---|---|---|---|
| P1 | `perf/P1-home-scroll.json` | Home, 10 swipes (`swipe` 300 ms) up and down | within 8.3 ms >= 95 %, none over 33 ms |
| P2 | `perf/P2-chatlist-scroll.json` | `seed chats:120`, Chats tab, 10 swipes | same |
| P3 | `perf/P3-chat200-scroll.json` | `seed chat:200:400`, open it, 12 swipes | same |
| P4 | `perf/P4-stream2000.json` | into the chat of P3 send `LONG 2000`; window = send tap until `[LONG-END]` shows | within 8.3 ms >= 95 % |
| P5 | `perf/P5-transitions.json` | open chat, back, tab switch x5 each; `"transition": true` | no frame over 16.7 ms |
| P6 | `perf/P6-coldstart.json` | `forceStop`, `am start -W` x7 (`WaitTime`/`TotalTime` median) | under 700 ms |
| P7 | `perf/P7-home-idle.json` | Home idle 10 s: frames drawn (no budget yet; the scene's cost appears here) | none |

Parsing: block between two `---PROFILEDATA---` lines, header row starting `Flags,`; a row counts when `Flags & 1 == 0`; duration = `FrameCompleted - IntendedVsync` (ns, to ms); frames de-duplicated by `IntendedVsync`; percentiles nearest-rank `sorted[ceil(p/100*n)-1]`; "within 8.3" is `duration <= 8.3`; the display refresh rate is read from `dumpsys display` and stored. Each JSON: `{"schema":1,"label","package","versionName","debuggable":bool,"compileFilter":"speed|verify|...","refreshHz","thermal","scenario","frames","within8_3Pct","p50","p90","p95","p99","worstMs","over16_7","over33","budget":{...},"pass":bool,"polls":n}`. `seed` without `run-as` (optimised/owner build) falls back to filling the chat through the UI with `FILL` turns (`mock-reply` is instant; about 3 s per turn).
Memory mode (cut item 1): phases `M0` app only (Hermes stopped), `M1` Hermes idle, `M2` after a `LONG` answer, `M3` speech model loaded (precondition `-Var speech=1`: Supertonic already downloaded; the step taps the voice preview), `M4` after `BALLAST 240` (stands for three sub-agents: S7 sets the real number). After each phase wait 20 s, then record `dumpsys meminfo <pkg>` TOTAL PSS / Java / Native / Graphics, the summed RSS of the other processes of the app's uid (`ps -A -o USER,PID,RSS,NAME`, user `u0_a<uid-10000>`), `MemAvailable`, the limiter text; `memory.json` = phases + `peakKb` (max of main PSS + guest RSS) + `headroom` when `-LimitMb` is given (`1 - peak/limit`, the plan wants >= 0.25).
`docs/perf/w0.md` layout: 1 Setup (phone, Android build, refresh rate, battery saver off, animation scales, thermal); 2 Builds compared (table: label, versionName, debuggable, compile filter); 3 Smoothness (one row per P1..P5: budget, then for each build frames / within 8.3 % / p90 / p95 / p99 / worst / verdict); 4 Cold start (median, runs); 5 Memory (phase table, peak, limit, headroom, the two caps decided: sub-agents at once, speech unloaded before a web view yes/no); 6 Gaps and causes; 7 How to reproduce (the exact commands).

### 3.4 Scenario file: format and vocabulary

```json
{ "name": "chat-tool-run", "title": "A tool call runs and its output shows", "flags": ["core","practice"], "owner": false,
  "practice": true, "timeoutSec": 240, "setup": { "wipe": false, "prefs": [], "seed": [] },
  "steps": [
    { "op": "link", "debug": { "reset": "1", "route": "chats" } },
    { "op": "tap", "any": [ { "tag": "chats.new" }, { "desc": "New chat" } ] },
    { "op": "type", "tag": "chat.input", "text": "please RUN_TOOL now" },
    { "op": "tap", "tag": "chat.send" },
    { "op": "wait", "tag": "chat.tool.0", "timeout": 90 },
    { "op": "expect", "textContains": "tool-result-seen", "timeout": 60 },
    { "op": "shot", "name": "tool" }, { "op": "noCrash" } ] }
```
`flags`: `core` (default suite), `smoke`, `slow`, `fragile` (shade or picker), `debt`, `manual-fallback`. `practice: true` expands before step 1 into UI steps: Settings > Runtime, set the switch on if off, `wait state Running`, then back to Home (switch and wait are skipped when already on and Running). Placeholders `{{pkg}}`, `{{latestChat}}` (newest id in `files/chat/conversations.json`), `{{now}}`, `{{var:name}}`. Selectors (on any step that takes one): `tag` (resource-id after the last `/`), `text`, `desc`, `textContains`, `descContains`, `re` (regex over text then desc), `idx` (n-th match, default 0), `any:[sel,...]` (first selector that matches wins, used for tag-then-English fallback), `scope` (`app` default, `systemui`). Attribute checks `checked`, `enabled`, `selected` read the matched node, or its first checkable descendant.

| op | Parameters | Behaviour / failure |
|---|---|---|
| `link` | `action` (`OPEN_CHAT` `NEW_CHAT` `TERMINAL` `ROUTINES` `USAGE`, extra `conv_id`) or `debug` {`route`,`clock`,`lang`,`rtl`,`reset`} or `intent` {`action`,`data`} | `am start -a dev.pocketagent.app.<ACTION> -n <Get-PaComponent> [-d agiri://chat/<id>] --es k v`; `debug` sends `-a dev.pocketagent.app.DEBUG` (NAV; twin only) and the robot adds `--es token <T>` itself (T read with `run-as <pkg> cat files/debug-link.token`). Fails when `am` prints `Error` |
| `tap` | selector, `timeout` 10 | Waits for the node, `input tap` at the centre of bounds. Fails when absent |
| `type` | selector or none (focused), `text` (ASCII), `clear` false, `submit` false | Taps the field, `input text` in chunks of 40, `%s` for spaces; `clear` sends 60 deletes first |
| `wait` | `ms` OR selector OR `state` (`Running`, `Stopped`, `Error`; twin: last `I/State:` line of `app.log`, owner: text of `settings.runtime.state`) OR `log` (regex over `app.log` since scenario start), `gone` false, `timeout` 30 | Polls every 500 ms. Fails on timeout |
| `expect` | selector or `log` regex, `count`, `checked`, `enabled`, `timeout` 8 | Same polling as `wait`, recorded as an assertion |
| `expectNot` | selector or `log`, `timeout` 3 | Must stay absent for the whole time |
| `shot` | `name` | `screencap`, pulled to the scenario folder |
| `kill` | none | `input keyevent HOME`, 1 s, `am kill <pkg>` (kills only a background process; keeps alarms). Verifies the pid changed |
| `forceStop` | none | `am force-stop <pkg>` (cancels alarms) |
| `notification` | `expect` {`titleContains`,`textContains`} (via `dumpsys notification --noredact`, pkg only), `tap` label, `reply` text, `open` true, `clear` true, `timeout` 30 | Shade steps run `cmd statusbar expand-notifications`, dump with `scope: systemui`, then `collapse`. Flag `fragile` |
| `noCrash` | none | Since scenario start: logcat free of `FATAL EXCEPTION`, `Fatal signal`, `ANR in <pkg>`, `NoSuchMethodError`, `ClassNotFoundException`; twin also `app.log` free of ` E/CRASH:` |
| extras | `key` (`HOME`,`BACK`,`ENTER`), `swipe` (`dir` up/down, `ms`, `times`), `wipe` (`pm clear`, twin only, then waits for re-extraction), `pref` (`file`,`key`,`type`,`value`; twin only; app stopped first), `seed` (`chats:n`, `chat:<messages>:<chars>`, `routine:<minutesFromNow>`, `usage:<limit>:<mode>`; twin only; app stopped), `killGuest` (twin only: SIGKILL every process whose environ has `PA_IN_PROOT=1`), `expectFile` (`path` under `files/`, `contains`/`regex`/`absent`, `lines` {`match`,`min`,`max`}; twin only), `expectColor` (selector, `dominant` red/green/blue, `min` pixels; PNG sampled with System.Drawing), `pick` (`save`/`open`, `name`; drives the system file picker by name, fragile), `install` (`apk`; refuses the owner package), `adb` (`args[]`, `expectExit`), `scale` (`font` 1.0\|2.0, `animations` 0\|1: `settings put system font_scale`, `global animator_duration_scale` and the two others; restored at scenario end), `a11y` (on-demand run of both checks on the current screen) | Each fails with the op name and the reason |

Setup for functional runs: animator, transition and window scales 0 (the Home scene would keep `uiautomator dump` from going idle); a dump that fails is retried 3 times. Perf runs set them to 1.
Automatic checks (every `link`/`tap`/`wait`/`expect` that ends on a dump the run has not seen: screen signature = SHA1 of the sorted set of tags in the dump): **label**, every node of the app's package with `clickable`, `checkable` or `long-clickable` true, bounds larger than 4 px and inside the screen, has a spoken label: its `text`, else its `content-desc`, else the first non-empty text or description among its descendants (4 levels); **cut**, with font scale 2.0: any `AgiriCut` line in logcat during the dwell (DESIGN's debug probe, 4.3) or a node of a non-scrolling ancestor chain whose bounds leave the screen by more than 2 px. Findings in `a11y-allow.json` / `cut-allow.json` (`{"allow":[{"screen","id","reason"}]}`) are not counted. Both checks also run once per route of `routes.json` in the scenarios `sweep-*` (with `scale font 2.0` for the cut check).

### 3.5 Scenarios to write (39). Flags: S smoke and owner-safe (taps only, no chat is made), D old debt of plan 0.4, F fragile, L slow (excluded from `core`), T twin only

| # | Name | Steps in one line | Keyword | Flags |
|---|---|---|---|---|
| 1 | smoke-launch | forceStop, start, wait `tab.home` 15 s, expect five tabs, noCrash | - | S |
| 2 | smoke-tabs-labels | tap each of the five tabs, a11y on each, noCrash | - | S |
| 3 | smoke-hermes-running | Settings > Runtime by taps, wait state Running 180 s (auto-start) | - | S |
| 4 | smoke-terminal-version | Hermes tab > Terminal, type `hermes --version`, expect `Hermes Agent v0.23.3` | - | S |
| 5 | smoke-settings-walk | open each of the 10 sections, expect no crash, a11y | - | S |
| 6 | practice-switch | Runtime: switch on, Running, hello answered; `expectFile requests.jsonl`; switch off, Running, `expectFile config.yaml` absent `practice-model`; `settings.xml` unchanged | hello | T |
| 7 | chat-hello | new chat, `hello`, expect `mock-reply: hello`, meta line shows 100 in and 10 out | - | T |
| 8 | chat-tool-run | `RUN_TOOL`, `chat.tool.0`, expect `hello-from-tool`, final `tool-result-seen` | RUN_TOOL | T |
| 9 | chat-think-fold | `THINK`, expect `chat.thought`, answer `think-answer: the calmer plan wins.`, the thought text not in the bubble | THINK | T |
| 10 | chat-long-stream | `LONG`, wait `[LONG-END]` 40 s, two shots mid-stream, noCrash | LONG | T |
| 11 | chat-slow-stop | `SLOW`, wait 3 s, tap `chat.stop`, expectNot the full twelve-word reply, `chat.send` back | SLOW | T |
| 12 | chat-fail-error | `FAIL`, expect error text `practice failure`, `chat.send` usable, hello answered after | FAIL | T |
| 13 | memory-card | `MEMORY`, expect `Memory updated`, tap `chat.memory.open`, `expectFile memories/MEMORY.md` contains `Practice note` | MEMORY | T |
| 14 | forge-bot | `FORGE`, expect `Practice Pal`, tap `chat.forged.chat`, Bots tab lists it | FORGE | T |
| 15 | media-inline | `MEDIA`, shot; records today's behaviour (no inline picture yet: a filed finding, not a failure) | MEDIA | T |
| 16 | usage-numbers | three hello + one `RUN_TOOL`, `usage.today` shows 590 | - | T |
| 17 | budget-warn | `seed usage:200:WARN`, two hello, expect `Daily token budget reached`, second reply still arrives | - | D T |
| 18 | budget-block | `seed usage:100:BLOCK`, hello, hello, expect `The daily token budget (100 tokens) is reached`, request log has exactly one POST | - | D T |
| 19 | approval-card | `ASK_APPROVAL`, `chat.approval.once`, expect `approval-flow-done`; again, `chat.approval.deny`, expect the denial in the result | ASK_APPROVAL | D T |
| 20 | approval-notification | `ASK_APPROVAL`, HOME, notification expect `asks for permission`, tap `Allow once`, open chat, expect `approval-flow-done` | ASK_APPROVAL | D T F |
| 21 | notification-reply | `SLOW`, HOME, notification expect `slow-reply`, `reply` "thanks", open chat, expect `mock-reply: thanks` | SLOW | D T F |
| 22 | idle-stop | `pref presence idleStopMinutes 5`, Running, wait 330 s, expect state Stopped and an idle line in `app.log` | - | D T L |
| 23 | auto-restart | Running, `killGuest`, wait state Running 150 s, `expectFile app.log` shows the unexpected exit and the restart, hello answered | - | D T |
| 24 | bot-export-import | `FORGE`, Bots, export to `bot.json` by `pick save`, delete the bot, import by `pick open`, expect it back | FORGE | D T F |
| 25 | skill-create-delete | Hermes > Skills, new `practice-skill`, expect row and `expectFile skills/**/practice-skill/SKILL.md`, delete + confirm, `absent` | - | D T |
| 26 | terminal-colours | Terminal: `printf '\033[31mred\033[0m \033[32mgreen\033[0m\n'`, expectNot `[31m`, expect `red`, `expectColor red`, shot | - | D T |
| 27 | soul-edit | Hermes > Soul, set `SOUL-MARK-7 answer in rhymes`, save, Restart, Running, send `PROMPT_HAS SOUL-MARK-7`, expect `prompt-has SOUL-MARK-7: yes` | PROMPT_HAS | D T |
| 28 | slash-loop | send `/loop 3 hello`, expect notes `pass 1/3`..`pass 3/3`, request log has 3 POSTs | - | D T |
| 29 | image-update-keeps-state | write a memory entry, a skill, an edited `SOUL.md`; overwrite `files/rootfs.stamp` with `fake-old`; forceStop, start; expect the extraction, then Running, and every file back byte for byte, `userstate.preserve` gone (one tree makes the same code path as a second APK; `-Var apk=` additionally `install`s a real older twin build first) | - | D T L |
| 30 | restore-into-wiped-twin | chat hello, backup to `twin.zip` (`pick save`), `wipe`, start, Running, restore (`pick open`, `backup.confirm`), app restarts, chat is there | - | D T F L |
| 31 | routine-fires-dead-process | `seed routine:2`, start, `kill`, notification within 150 s, open, expect `mock-reply:`, `app.log` has the routine line | - | T |
| 32 | restart-survival | hello, forceStop, start, Running in 60 s, chat in list, hello again | - | T |
| 33 | wake-on-send | Runtime Stop, new chat, hello, expect the answer within 180 s | - | T |
| 34 | deep-links | the five actions, each expect its `screen.*` tag (or the `Nav: top=` log line) | - | T |
| 35 | persist-star-draft | star a message, type a draft, forceStop, start, expect both | - | T |
| 36 | sweep-labels | `routes.json` loop: `link debug route`, a11y (label check) | - | T |
| 37 | sweep-double-text | `scale font 2.0`, same loop, cut check, restore | - | T |
| 38 | sweep-stretched-and-mirrored | loop with `lang` = the test language, shot; again with `rtl 1`, shot (judged by eye; `-Var marker=<regex>` fails a text node without the marker) | - | T |
| 39 | design-gallery | route `gallery` (DESIGN/NAV), open sheet, dialog, note, expect `sheet`, `dialog`, `petnote` | - | T |

Counts: 39 files; `core` suite = all except L and F = 33, the 5 smoke ones included (the plan needs at least 20 green); D = 14; S = 5 (the owner's list).

### 3.6 Practice model (python) and the UI lint

CLI: `python practice_model.py [--host 127.0.0.1] --port N [--log FILE] [--media-dir DIR] [--watch-parent] [--selftest]`. Standard library only, Python 3.8+, threads. `--watch-parent`: exits when `os.getppid()` changes (the wrapper's `exec` keeps the pid, so it ends with Hermes). `--selftest`: serves on a free port, checks every row below with `urllib`, prints `PRACTICE SELFTEST OK`. `GET /health` -> `{"status":"ok","name":"practice-model"}`; `GET /v1/models` lists `mock-model` and `practice-model`; `POST /v1/chat/completions` stream and non-stream. Writes a deterministic 64x64 PNG to `<media-dir>/practice.png` (`zlib` + `struct`) at start. One JSON line per POST to `--log`, valid JSON (fields capped, no cut line), keeping the keys the CI reads (`"POST"`, `"n_msgs"` after `"stream"`) and adding `keyword`, `prompt_chars`, `last_user` (first 120 chars), `usage` [prompt, completion]; the file is cut to its last 200 lines past 1 MB. Keyword = first match in this priority, as a whole word, in the LAST user message: FAIL, ASK_APPROVAL, RUN_TOOL, MEMORY, FORGE, MEDIA, THINK, SLOW, LONG, PROMPT_HAS, PROMPT_SIZE, BALLAST; text after the keyword on the same line is its argument. Source line `KEYWORDS = (...)` is what `PracticeModelTest` compares with `PracticeModel.KEYWORDS`. Usage is on every non-stream answer and on the finish chunk of every stream (when the request has `stream_options.include_usage` it goes in a last chunk with `choices: []` instead). Numbers are per model call:

| Keyword | Behaviour (exact) | Usage prompt/completion |
|---|---|---|
| none | `mock-reply: <last user text[:200]>`, words streamed with no delay (unchanged, CI relies on it) | 100/10 |
| `RUN_TOOL [cmd]` | call 1: tool `terminal` (else first tool) with `{"command": cmd or "echo hello-from-tool"}`; call 2 (newest message is a tool result): `tool-result-seen: <result[:200]>` (unchanged) | 100/20 then 130/10 |
| `ASK_APPROVAL` | call 1: `terminal` with `rm -rf /tmp/agiri-practice-approval` (Hermes pattern "recursive delete", `tools/approval_detection.py:501`); call 2: `approval-flow-done: <result[:120]>` | 100/20 then 130/10 |
| `LONG [n]` | n chars (default 3000) of deterministic Markdown (headings, bold, inline code, bullets, a fenced code block, a table every fourth section), exactly n chars, ending `[LONG-END]`; chunks of 15 chars every 50 ms (300 chars/s) | 100/ceil(n/4) |
| `THINK` | first chunk `**Weighing the options**\n\nI compare two small plans and pick the calmer one before answering.\n\n` (the shape `ThoughtFilter` moves into the folded thought), then `think-answer: the calmer plan wins.` | 100/60 |
| `SLOW` | role chunk at once, 4 s of silence, then `slow-reply: one two three four five six seven eight nine ten eleven twelve` one word per second | 100/12 |
| `FAIL [code]` | HTTP `code` (default 400, 400..599) with `{"error":{"message":"practice failure: the FAIL word was used","type":"invalid_request_error","code":"practice_fail"}}`; no usage | none |
| `MEMORY` | call 1: tool `memory` `{"action":"add","target":"memory","content":"Practice note <epoch seconds>"}`; call 2: `memory-saved: <result[:120]>` | 100/25 then 130/10 |
| `FORGE` | fixed text with a fenced `pocket-bot` block (`Practice Pal`, avatar OWL, `#2E7D6B`, voice F2, calm) and a closing line | 100/260 |
| `MEDIA` | `Here is the practice picture: MEDIA:<media-dir>/practice.png` (Hermes turns the tag into a data URL, `api_server.py:714`) | 100/30 |
| `PROMPT_HAS w` | `prompt-has w: yes` or `no`, searching every message except the last user message | 100/10 |
| `PROMPT_SIZE` | `prompt-size: <characters of all messages + tool definitions>` (also S15's fallback) | 100/10 |
| `BALLAST mb` | allocates and touches `mb` MB (0 frees, cap 1024), `ballast: <mb> MB held` | 100/10 |

Totals for the scenarios: a plain turn 110; `RUN_TOOL`, `ASK_APPROVAL` 260; `MEMORY` 265.
`runtime/practice-start.sh` (`practice-start.sh PORT COMMAND [ARGS...]`): `set -u`; finds python at `/root/.hermes/installs/*/environments/*/venv/bin/python` (the CI smoke test uses the same glob), else `/usr/bin/python3`, else exits 70 with a message on stderr; `mkdir -p media`; starts `python -u practice_model.py --port PORT --log requests.jsonl --media-dir media --watch-parent >> practice.log 2>&1 &` in its own directory; waits up to 10 s for `curl -s -m 1 http://127.0.0.1:PORT/health`; then `exec "$@"`. No bashisms (mksh, dash).
Lint rules (`app/src/main/java/dev/pocketagent/app/`, comments stripped, counts per rule and per file): `rawTween` `\btween\(` and `\bkeyframes\(` in `ui/` except `ui/theme/Motion.kt`, `Tokens.kt`, `PocketTheme.kt`; `toast` `Toast\.makeText` in `ui/`; `roundedCorner` `RoundedCornerShape\(` in `ui/` (same exceptions); `literalDuration` `durationMillis\s*=\s*\d+`, `\bdelay\(\s*\d+`, `delayMillis\s*=\s*\d+` in `ui/` (`Dur.`, `Loop.`, `Flourish.` arguments never match); `literalDp` `\b\d+(\.\d+)?\.dp\b` in `ui/` outside `ui/theme/`; `directText` `\b(stringResource|pluralStringResource)\(`, `\.getString\(`, `getQuantityString\(` in `ui/`, `service/`, `routines/`, `data/`, `hermes/` except `ui/text/`; `leftRight` `AbsoluteAlignment\.`, `absolutePadding\(`, `Arrangement\.(Left|Right)`, `Alignment\.(CenterLeft|CenterRight|TopLeft|TopRight|BottomLeft|BottomRight)`, `TextAlign\.(Left|Right)` in `ui/`; `caseChange` `\.(uppercase|lowercase|capitalize)\(`, `replaceFirstChar` in `ui/`; `hapticDirect` `performHapticFeedback\(`, `HapticFeedbackType` in `ui/` except `ui/theme/Haptics.kt`; `sheetDirect` `ModalBottomSheet\(` in `ui/` except `ui/components/PocketSheet.kt` (the last two are DESIGN's request). Baseline `tools/lint-ui-baseline.json`: `{"schema":1,"generated":"yyyy-MM-dd","rules":{"<rule>":{"total":n,"files":{"ui/chat/ChatScreen.kt":3}}}}`; a file absent from the baseline counts as 0. The baseline is taken after the integration compile (DESIGN's request), by `-Init`.

## 4. Shared symbols ROBOT consumes

| # | From | Exact symbol / behaviour expected |
|---|---|---|
| 4.1 | BUILD | `tools/build-ids.ps1`: `$PaTwinPackage`, `$PaOwnerPackage`, `$PaMainActivity`, `Get-PaComponent([string]$Package,[string]$ClassName)`, `Test-PaOwnerPackage`. Twin installs with `app-debug.apk`, is debuggable (`run-as` works), `IS_TWIN` true. Owner build not debuggable. `-Pperf` twin is not debuggable either (then no `run-as`: the robot says so and uses logcat and the UI) |
| 4.2 | CONFIG | `RuntimeManager.STILL_STARTING: String` (`const val` in a companion, value `"still starting"`, the text built at `RuntimeManager.kt:465`) for `RuntimeSection.kt:78`; `MainViewModel.shareLogsIntent(): android.content.Intent?` (zip of `app.log`, `runtime.log`, `last-launch.txt`, redacted, in `cacheDir/diagnostics`, shared through the existing FileProvider path `diagnostics`). Practice calls (section 7) |
| 4.3 | DESIGN | `object CutProbe { fun report(text: String) }` in `ui/components/CutProbe.kt`: when `BuildConfig.IS_TWIN`, `Log.w("AgiriCut", text.take(60))`, else nothing. Called by `PixelText` when it ellipsizes (and by `CText` when `TextLayoutResult.hasVisualOverflow`). Plain tag strings `sheet`, `dialog`, `dialog.confirm`, `petnote`, `gallery` |
| 4.4 | NAV | `NavTags.TAB_*`, `NavTags.screen(route)`; `robotRoot()` at the root; the debug link: action `dev.pocketagent.app.DEBUG`, extras `route` (a `Route.encode()` key), `clock`, `lang`, `rtl`, `reset`, component `dev.pocketagent.app.debug/dev.pocketagent.app.MainActivity`; the five link actions stay literal `dev.pocketagent.app.OPEN_CHAT` etc. for both builds; the `app.log` line `I/Nav: top=<route key> id=<n> stack=[...]` |
| 4.5 | AppLog | `app.log` lines `I/State: <label>` (`RuntimeManager.setState`, labels `Running`, `Stopped(...)`, `Error(...)`) and `E/CRASH:` (`PocketApp.CrashLogger`) are read as they are today |
| 4.6 | TEXT | `tools/make-test-language.ps1` prints the language tag and the marker pattern of the stretched language; the lead passes them as `-Var lang=` and `-Var marker=` |

## 5. Strings (`res/values/strings_robot.xml`, prefix `rbt_`, English only)

| Key | English | Translator note |
|---|---|---|
| `rbt_practice_title` | Practice model (no tokens) | Switch title in Settings > Runtime. Keep short. |
| `rbt_practice_sub` | Hermes answers from a built-in script instead of a real model. Nothing is sent to any provider and nothing is spent. Your own setup stays as it is and comes back when this is off. | Subtitle, up to 4 lines. |
| `rbt_practice_restart` | Hermes restarts to apply this. | Shown under the switch after a change while Hermes runs. |
| `rbt_practice_on` | The practice model is on: replies are scripted. | Shown while on. |
| `rbt_practice_words` | Magic words to type in a chat: %1$s | %1$s is a comma-separated list of upper-case words; never translate the words. |
| `rbt_export_logs` | Export logs | Button label (Diagnostics). |
| `rbt_export_logs_hint` | One zip of the app log, the runtime log and the last launch line, with secrets removed. | Under the button. |

## 6. Manifest entries

None. The robot starts `MainActivity` by explicit component; `RuntimeSection` adds no permission. The FileProvider path `diagnostics` (`res/xml/file_paths.xml:3`) already covers the log zip.

## 7. REQUESTS to other areas

1. BUILD: `app/build.gradle.kts`, `PrepareRuntimeTask` (copies the launcher at line 46): add two input files, `rootfs/smoke/mock_llm.py` and `runtime/practice-start.sh`, copied next to the launcher as assets `practice_model.py` and `practice-start.sh` in both variants.
2. BUILD: `.github/workflows/build.yml`, job `unit`: add `sh -n runtime/practice-start.sh`, `python3 -m py_compile rootfs/smoke/mock_llm.py`, `python3 rootfs/smoke/mock_llm.py --selftest`, `pwsh -NoProfile -File tools/phone-test.ps1 -SelfTest`, `pwsh -NoProfile -File tools/phone-perf.ps1 -SelfTest` (your list already has `lint-ui.ps1 -Check`); add `runtime/practice-start.sh` to the `bash -n` loop at line 40. Phase 3 of the smoke job needs no change in the workflow.
3. CONFIG: `runtime/RuntimeManager.kt` `launchHermes` (lines 387 and 391) and `rewriteGuestConfigIfInstalled` (line 236): `val on = PracticePrefs.get(context).enabled.value`; when on, pick a second free port different from `port` and keep it in a field; compute `val inputs = PracticeModel.effective(settings.providerInputs(secrets.geminiApiKey()), on, practicePort)`; validate THAT with `ProviderConfig.blocking(...)` (a broken owner setup must not stop practice); `PracticeModel.install(context, paths)` before `HermesConfig.apply`; `cmd = listOf("/system/bin/sh", paths.launcher.path, "--") + (if (on) PracticeModel.guestCommand(practicePort, hermesArgv) else hermesArgv)`; add `STILL_STARTING` (4.2); write the app.log line `practice=on|off` at every start.
4. CONFIG: `runtime/HermesConfig.kt:11`: `apply(paths, settings, secrets, apiServerKey, apiPort, inputsOverride: ProviderInputs? = null)`; the override replaces `ProviderConfig.savedInputs(...)` (credential files follow the override's mode).
5. CONFIG: `PocketApp.kt:122`: `provider = { if (PracticePrefs.get(this).enabled.value) PracticeModel.PROVIDER else ProviderConfig.providerName(settings.providerInputs("")) }` so per-request model overrides are sent as `custom` (the mock ignores the model) and never as `gemini`.
6. CONFIG: `rootfs/smoke/fixtures/practice.env` and `practice.yaml`: the output of `renderEnv` and `renderYaml` of `PracticeModel.effective(<studio inputs>, true, 18080)` with the smoke API key, plus the golden test in `ProviderConfigTest`; `diag/Diagnostics.kt`: a line `practice model: on|off`; `MainViewModel.kt` (no owner in the plan; CONFIG please): `fun shareLogsIntent(): Intent?` (4.2).
7. NAV: `ui/AppRoot.kt`: nothing for the switch; tag `Modifier.testTag(NavTags...)` as in your draft. If the debug route for DESIGN's gallery is named, tell ROBOT the key (I assume `gallery`).
8. DESIGN: `ui/components/PocketSwitchRow.kt`: add `modifier: Modifier = Modifier` (first optional parameter after `subtitle`) so owners can tag a row directly; `CutProbe` (4.3); in `PixelText` call it when ellipsizing.
9. STREAM: `ui/chat/ChatScreen.kt`, `ChatMessages.kt` (and new parts): `Modifier.testTag(RobotTags.X)` on: input field `CHAT_INPUT`, send `CHAT_SEND`, stop `CHAT_STOP`, the message list `CHAT_LIST`, each message `chatMsg(i)` (0 = newest), each tool card `chatTool(i)`, the folded thought `CHAT_THOUGHT`, the four approval buttons, `CHAT_MEMORY_OPEN` (the "Open Memory" button), `CHAT_FORGED_CHAT` (the "Chat now" button of a forged bot card), top-bar menu `CHAT_MENU`, find `CHAT_FIND`.
10. SWEEP (small model, mechanical, only `Modifier.testTag(RobotTags.X)`): `ui/home/HomeScreen.kt` `HOME_STATE` on the status text, `HOME_START` on the Start control; `ui/chat/ChatListScreen.kt` `CHATS_NEW`, `chatsRow(i)`; `ui/hermes/HermesScreen.kt`, `HermesTabs.kt`, `HermesDocs.kt` the `HERMES_*` tags (tabs, editor, save, new skill, name, create, delete, confirm, `hermesSkillRow(name)`); `ui/hermes/TerminalScreen.kt` `TERMINAL_*`; `ui/settings/SettingsScreen.kt` `settingsSection(key)` on each section header; `UsageSection.kt` `USAGE_*`; `BehaviourSection.kt` `BEHAVIOUR_IDLE_MINUTES` (CONFIG edits only the privacy row of this file: one edit each, no overlap); `BackupSection.kt` `BACKUP_*`; `ui/bots/BotsScreen.kt` `BOTS_EXPORT`, `BOTS_IMPORT`, `botsRow(i)`.
11. Lead: `.gitattributes` and the two unowned files in section 2 are taken by ROBOT; confirm.

## 8. Tests (JVM, no instrumentation)

Pure logic stays pure: `PracticeModel` functions take values, `installInto` takes a `File` and bytes, `PracticePrefs` takes a `BooleanStore` fake, scenario files are read with `org.json` (on the test classpath, `app/build.gradle.kts:181`). Tests that read repo files use `Assume.assumeTrue(file.exists())` because `-Test` runs from a copy.

| Class.test | Proves |
|---|---|
| `PracticeModelTest.baseUrlPassesTheCustomEndpointValidation` | `ProviderConfig.normalizeBaseUrl(baseUrl(18081))` equals `http://127.0.0.1:18081/v1` (`ProviderConfig.kt:228`) |
| `.baseUrlRejectsBadPorts` | 0, -1, 65536 throw `IllegalArgumentException` |
| `.inputsDropEveryGoogleSecretAndKeepTheEffort` | from a SERVICE_ACCOUNT input with key text, the result is CUSTOM, `MODEL_ID`, empty keys, same `reasoningEffort`; the argument is unchanged |
| `.effectiveReturnsTheSameInstanceWhenOff` | `effective(base, false, 1)` is `base` |
| `.renderedConfigPointsAtTheMockAndHasNoKey` | `renderYaml` has `provider: custom`, the base URL, no `key_env`; `renderEnv` has no `GEMINI_API_KEY` and not the secret text |
| `.guestCommandShape` | `["sh", GUEST_START, "18081", "hermes", ...]`, the first word has no `=`, the argv tail is unchanged, two ports give the same first two elements |
| `.installIntoWritesOnlyWhenDifferent` | first call true, second false, changed bytes true, no `.tmp` left, files named `HOST_SCRIPT_NAME` and `HOST_START_NAME` |
| `.keywordsMatchThePythonFile` | the `KEYWORDS = (...)` line of `rootfs/smoke/mock_llm.py` equals `PracticeModel.KEYWORDS` as a set, and every keyword has a row in the python usage table |
| `PracticePrefsTest.defaultOffAndRoundTrip` | default false; `set(true)` makes the flow true; `set(true)` again writes nothing (fake counts puts) |
| `RobotTagsTest.valuesAreUniqueAndWellFormed` | reflection over the `const val`s: unique, match `^[a-z][a-z0-9.-]*$`, no `/` |
| `.functionsFollowThePattern` | `chatMsg(0)` is `chat.msg.0`, `settingsSection("runtime")` is `settings.section.runtime`, and so on |
| `ScenarioFilesTest.everyFileParsesAndUsesTheVocabulary` | 39+ files, `name` equals the file name, every `op` is in the vocabulary, selectors have at least one field, `timeout` is a number |
| `.everyTagIsKnown` | each `tag` in the scenarios is in `RobotTags`, matches a function pattern, or starts with `tab.`, `screen.`, `nav.`, `share.`, `sheet`, `dialog`, `petnote`, `gallery` (the seam check: it fails until the owners of request 9 and 10 add their tags) |
| `.coreSuiteHasAtLeastTwentyScenarios` | the plan's bar |
| `.smokeScenariosNeverTypeIntoAChat` | `smoke` scenarios contain no `type` into `chat.input`, no `practice`, no `seed`, no `pref`, no `wipe` |

Script self-tests (CI, request 7.2): `phone-test -SelfTest` (label and cut checks on `fixtures/dump-sample.xml`: exactly the one planted unlabelled node is reported; selector forms; the `{{...}}` substitution), `phone-perf -SelfTest` (`fixtures/gfxinfo-sample.txt`: frame count, flag-1 rows skipped, percentiles by nearest rank, the 120-frame overlap de-duplicated), `lint-ui -SelfTest` (each rule on a snippet; the comment stripper), `mock_llm.py --selftest` (every keyword, usage numbers, `LONG` exact length, `FAIL 429`, the ThoughtFilter shape, the `pocket-bot` block parses as JSON, the PNG signature). No JVM test can execute the python or the scripts.

## 9. Robot hooks

Tags: `RobotTags` (3.1), `NavTags`, DESIGN's literals. Required by the scenarios of 3.5 and nowhere else; an owner who cannot add one tells the lead and the scenario falls back to its `any:` English selector (the stretched-language scenarios then fail on purpose). Practice keywords: the twelve of 3.6 and their usage numbers; the request log (`files/rootfs/root/.hermes/practice/requests.jsonl`) lets a scenario count model calls (no title call: one POST for `hello`, two for `RUN_TOOL`). Debug link: NAV's single `DEBUG` action (4.4). Needed from nobody else: `adb shell run-as` on the twin, `uiautomator dump`, `dumpsys gfxinfo/notification/meminfo/package/display`, `cmd statusbar`, `settings put`.

## 10. Experiment branches

- **S5 fails** (the mock cannot live beside Hermes in one launch, or the launcher sweep kills it): `PracticeModel.guestCommand` is not used. New `runtime/PracticeServer.kt` (Kotlin, `ServerSocket` on 127.0.0.1, the same twelve keywords and the same usage table, started by `RuntimeManager` in the app process; `PracticeModel.baseUrl` unchanged because the guest shares the phone's loopback). The python file stays for CI, `PracticeModelTest.keywordsMatchThePythonFile` also reads `PracticeServer.KEYWORDS`. Cost: about 350 lines of Kotlin and a second behaviour table to keep equal; cut it only together with the debt scenarios 19 to 21, 23, 27, 28.
- **S5 passes but `ASK_APPROVAL` never reaches `approval.request`** (Hermes auto-approves for an API client): scenarios 19 and 20 end `SKIP (not testable)` with exit 3 for them and the finding goes to `AGENTS.md`; choose another flagged command (`chmod 777 /tmp/x`) by editing the python table only.
- **S7**: independent of the tooling (it records both the app's PSS and the other processes). If Android counts only the main process, the guest RSS column becomes informational and `headroom` is computed from the main process alone; if it counts the whole uid, the table already shows the sum.
- **S1/S3/S2/S4/S6**: none touches ROBOT. If S2 turns shrinking off, `-Owner` runs still work; only `compileFilter` in the perf JSON changes.

## 11. Risks, and what in the plan is wrong or impossible

1. **The practice model's source, the smoke script and `.gitattributes` have no owner in the plan's table** (`mock_llm.py`, `smoke-test.sh:227-242`). Section 2 takes them; the lead must confirm or a CONFIG agent edits `smoke-test.sh` at the same time as its fixtures.
2. **"Its own configuration" (plan 0.4) is not a second file.** Hermes reads one `config.yaml`; the app regenerates it at every start (`HermesConfig.apply`), so "comes back when the switch is off" means regeneration from `Settings`, which the switch never writes. Verified by `practice-switch` (scenario 6).
3. **Seeding needs `run-as`, which the owner build and the optimised twin do not have.** The robot falls back to the UI (`FILL` turns, slow) for P2 to P4 there; the first perf comparison "debug versus release" is therefore only fully automatic for the debug twin. BUILD's `-Pperf` twin (cut-first item 2 of its list) is not enough alone; a debug-only seeding link in NAV would fix it (not requested; Wave 2's seeder will).
4. **`dumpsys gfxinfo framestats` keeps 120 frames** (about a second at 120 Hz). The plan's "frames, share, percentiles per scenario" needs the 400 ms polling of 3.3; a frame lost between two polls is not counted (the `polls` field and a frames-per-second sanity check expose that).
5. **`uiautomator dump` waits for an idle UI** and the Home scene animates forever; animation scales 0 help only if DESIGN's effective level (plan 0.5) follows the system setting. Until then dumps on Home may fail (retried 3 times); `link` to a route other than Home first.
6. **Notification and file-picker scenarios depend on One UI's windows** (shade layout, Samsung's picker). Flag `fragile`; the unlock requirement is checked, not fixed. The plan's "three things stay manual" list therefore may grow by two (inline reply, picker) if they cannot be driven.
7. **Idle stop cannot be tested in under 5 minutes**: `PresencePrefs.MIN_IDLE_MINUTES = 5` (`PresencePrefs.kt:32`); scenario 22 waits 330 s and is `slow`.
8. **"An update that replaces the Linux image" needs two images** and local builds reuse the stamp (AGENTS.md "Faster iteration"). Scenario 29 forces the same code path by overwriting `files/rootfs.stamp`; a real second APK is an optional variable. The code path is real: `isRootfsValid` (`RuntimeManager.kt:151-160`) compares the stamp with the build id of the APK, a mismatch makes `start()` run `ensureRootfs`, which parks and restores the user state.
9. **Export logs (plan 0.1) has no owner**: its button belongs in `DiagnosticsSection` (my file, `RuntimeSection.kt:129`), the zip and `MainViewModel.shareLogsIntent()` in nobody's. Request 6 asks CONFIG; without it the button is not built.
10. `am memory-limiter status` (plan 0.12) was not verified on a phone for this draft; the script stores the raw text or `unavailable` and S7 decides the meaning.
11. In practice mode the owner's credentials are removed from the guest by `HermesConfig.apply`, so the worst a wrong request can do is fail; but `RuntimeSection.kt:78` and `ChatEngine` (`ModelIds.fits("custom", ...)` is always true, `ChatEngine.kt:91-98`) send per-request models unchanged; harmless for the mock, mentioned so nobody "fixes" it.
12. Text input by `input text` is ASCII only: Turkish and French texts cannot be typed by the robot; the language scenarios use the debug link `lang` instead.
