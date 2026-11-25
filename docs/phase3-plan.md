# Phase 3 plan and build contract: "the always-on hearth" (written 2026-11-24)

Written by the lead agent after surveying the code, the phone and Hermes v0.23.3's source inside the
rootfs. Eight agents build this at the same time, each owning its own files. This file is the contract
between them: **implement exactly the names and signatures below** so the parts compile together. If
you must deviate, keep the listed API working (add, do not rename) and say so in your final report.
`docs/phase1-contract.md` is still valid for everything that already exists.

## Why these things (what the survey found)

The app is rich inside a chat, but it behaves like a tool you have to switch on, not like an agent that
is there for you:

| Gap found | What Phase 3 does about it |
|---|---|
| Hermes must be started by hand at every app launch; the chat input is replaced by a "Hermes is asleep" card | Auto-start on app open, and **wake-on-send**: you just type, the app lights the fire and delivers the message |
| A reply that finishes while you are elsewhere only plays a sound | Reply / approval / failure **notifications** with tap-to-open and inline reply, unread badges |
| An agent that can only act when you talk to it | **Routines**: scheduled prompts (daily, interval, once) that wake Hermes, run in their own chat and notify you |
| No idea what a chat costs (the owner wants $0 spend) | **Usage meter**: tokens per reply, per chat, per day and model, plus a daily budget that warns or blocks |
| Everything lives only on this phone, `allowBackup=false` | **Backup and restore** to one zip (never credentials), bot export/import |
| Google is the only provider | **More providers**: OpenRouter, Anthropic, OpenAI, any OpenAI-compatible endpoint |
| No Android integration beyond the share sheet | Launcher **shortcuts**, a **Quick Settings tile** for the fire, "Ask Agirî" in the text-selection menu |
| Chat gaps: no find-in-chat, drafts die with the process, nothing can be saved for later | **Find in chat**, persistent drafts, **starred messages**, unread filter, export a chat to a file |
| Hermes screen gaps: skills cannot be created or deleted, terminal drops colours | Create / delete skills, ANSI colours in the terminal |
| Hermes exits unexpectedly -> stays dead | One automatic restart; optional idle stop to save battery |

Facts this plan relies on (verified in the Hermes source on the phone, 2026-11-24):
- The streaming chat API **always** puts a `usage` object (`prompt_tokens`, `completion_tokens`,
  `total_tokens`) on the final chunk (the one with `finish_reason`); zeros when the agent failed
  (`gateway/platforms/api_server_openai_routes.py:1212`, `api_server.py:613-616`, `:3602`). No request
  flag is needed.
- Hermes has its own cron (`/api/jobs`), but its output goes to Hermes's own delivery targets, not to
  an app chat, so Routines are implemented **app-side** with `AlarmManager` (targetSdk 28: exact alarms
  and starting a foreground service from the background are allowed).
- Provider names in Hermes: `gemini`, `vertex`, `anthropic` (`ANTHROPIC_API_KEY`), `openai-api`
  (`OPENAI_API_KEY`, `OPENAI_BASE_URL`), plugin providers `openrouter` (`OPENROUTER_API_KEY`) and
  `custom` (user base URL). Config keys: `model.default`, `model.provider`, `model.base_url`,
  `model.key_env` / `model.api_key_env` (`hermes_cli/auth.py:371, 1544-1561`). The PROVIDERS agent must
  confirm the details in the source before writing config for a provider.

## Ground rules for every agent

- Kotlin + Jetpack Compose, packages under `dev.pocketagent.app`. **No new dependencies.** Available:
  compose ui/foundation/material3, activity-compose, lifecycle-runtime-compose,
  lifecycle-viewmodel-compose, androidx.core, coroutines, org.json, java.time (minSdk 26).
- **Do not run Gradle, do not install on the phone, do not commit.** Other agents are editing the tree at
  the same time; an integration agent compiles afterwards. Write code carefully (imports, exact types,
  exhaustive `when`).
- **Touch only the files you own** (table below). New files go in your own packages. If you need a
  change in a file you do not own, do NOT make it: use the API in this contract, and list anything
  missing under `REQUESTS` in your final report.
- The phone is connected over USB. **Read-only** `adb shell run-as dev.pocketagent.app ...` (ls, cat,
  grep, sed -n) is allowed for looking at Hermes's source under `files/rootfs/root/.hermes/hermes-agent/`.
  Never read `.env`, `files/secrets`, credential files; never write, start, stop or install anything.
  In Git Bash prefix adb commands with `MSYS_NO_PATHCONV=1`; adb is at
  `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`.
- No secrets in logs, UI or the repo. Anything that can contain a key goes through `Diag.redactor`.
- User-visible strings go in string resources, English in `values/`, Turkish in `values-tr/`, French
  in `values-fr/`, **the same keys in all three**, each agent in its own file (table below). Escape
  apostrophes (`\'`). `aapt2` trims leading/trailing spaces in `<string>`. Engine texts written into the
  chat as NOTE/ERROR messages stay English (existing convention).
- Read "Hard-won facts" in `AGENTS.md` (Kotlin init order, nested block comments in KDoc, `PixelPanel`
  fill, transparent system bars, `aapt2` whitespace). They cost real time before.
- Everything new respects `Pocket.colors`, `Pocket.anim` (FULL / REDUCED / OFF) and the pixel
  components in `ui/components`. Clickable things get a `Role` and a content description.
- Unit tests for pure logic go in `app/src/test/java/dev/pocketagent/app/` (JUnit 4, `org.json` is on the
  test classpath, `unitTests.isReturnDefaultValues = true`). Existing tests must keep passing; when you
  change a signature a test uses, update the test.
- PendingIntents: always `FLAG_IMMUTABLE` (or `FLAG_MUTABLE` only where a `RemoteInput` needs it), plus
  `FLAG_UPDATE_CURRENT`.
- Final report of every agent: files created/changed, deviations from this contract, `REQUESTS`, and
  what you could not verify.

## Ownership

| Agent | Owns (only these may be edited) | String file (prefix) |
|---|---|---|
| ENGINE | `api/*`, `data/ChatEngine.kt`, `data/Models.kt`, `data/ChatJson.kt`, `data/ChatRepository.kt`, `data/Transcript.kt`, `data/AttachmentStore.kt`, new `data/UsageStore.kt`, `data/DraftStore.kt`, `data/BotShare.kt`, new `runtime/RuntimeAwait.kt`, `PocketApp.kt`; tests `ChatEngineTest`, `ChatRepositoryTest`, `Sse*Test`, `MultimodalApiTest`, `TranscriptTest`, new `UsageStoreTest`, `BotShareTest`, `DraftStoreTest` | none |
| PRESENCE | `service/AgentService.kt`, new `service/Presence.kt`, `service/AppForeground.kt`, `service/ReplyNotifier.kt`, `service/NotificationActionReceiver.kt`, new `runtime/PresencePrefs.kt`, `runtime/RuntimeManager.kt` (additive only), `runtime/RuntimeState.kt`; new test `PresenceLogicTest` | new `strings_presence.xml` (`prs_`) |
| ROUTINES | new package `routines/*`, new `ui/routines/*`; new tests `RoutineScheduleTest`, `RoutineStoreTest` | new `strings_routines.xml` (`rtn_`) |
| SURFACE | `AndroidManifest.xml`, `MainActivity.kt`, `ui/Nav.kt`, `ui/AppRoot.kt`, new `AppLinks.kt`, new `service/FireTileService.kt`, `res/xml/*`, new `res/drawable/ic_shortcut_*.xml`, `res/drawable/ic_tile_fire.xml`; new test `AppLinksTest` | new `strings_surface.xml` (`srf_`) |
| CHAT | `ui/chat/*`, `ui/home/*` (new files there too, e.g. `ui/chat/StarredScreen.kt`, `ui/chat/ChatSearch.kt`); `data/SlashCommands.kt`; tests `SlashCommandsTest`, `VoiceAndHighlightTest` | `strings_chat.xml` (`ui_`) |
| CONTROL | `ui/settings/*` except `ProviderSection.kt`; `ui/bots/*`; `ui/hermes/*`; `hermes/*`; new `data/BackupManager.kt`; tests `HermesHomeTest`, `TerminalSessionTest`, new `BackupManagerTest` | `strings_control.xml` (`ctl_`) |
| PROVIDERS | `runtime/ProviderConfig.kt`, `runtime/Settings.kt`, `runtime/SecretStore.kt`, `runtime/HermesConfig.kt`, `runtime/Paths.kt`, `runtime/ServiceAccount.kt`, `MainViewModel.kt`, `ui/settings/ProviderSection.kt`, `diag/*`; tests `ProviderConfigTest`, `RedactorTest`, `ServiceAccountTest`, `DumpAndPortTest` | `strings.xml` (existing names) |
| ART | `assets/pixel/icons.pxl`, `ui/components/PixelIcon.kt`; test `PxlParserTest` | none |

Nobody edits `data/BotPresets.kt`, `data/BotForge.kt`, `voice/*`, `ui/theme/*`, `ui/pixel/*`,
`ui/components/*` (except ART's `PixelIcon.kt`), `build.gradle.kts`, `rootfs/`, `proot/`, `runtime/*.sh`,
`.github/`.

---

## ENGINE: usage, wake-on-send, unread, stars, drafts, bot sharing

### Stream usage (`api/Sse.kt`)

```kotlin
sealed class ChatEvent {
    // ... existing ...
    /** Token counts of the whole turn, from the `usage` object on the final chunk. Emitted only when some count is > 0. */
    data class Usage(val promptTokens: Long, val completionTokens: Long, val totalTokens: Long) : ChatEvent()
}
```
`ChatStreamDecoder.decode` emits `Usage` (before `Finish`) when the chunk has a non-null top-level
`usage` object with a positive `prompt_tokens` or `completion_tokens`. Check in the Hermes source
whether the counters are per turn or accumulate over a Hermes session (the session id is derived from
the system prompt + first user message, so one chat = one session); if they accumulate, convert to
per-turn values in the engine and say how in your report.
Remove the dead `HermesApi.streamChat` / `cancelChat` / `legacyHandle`.

### Models (`data/Models.kt`, `data/ChatJson.kt`; all new fields default-valued and optional in JSON)

```kotlin
data class Message(/* existing */, val tokensIn: Long = 0L, val tokensOut: Long = 0L,
    val model: String = "" /* model id this BOT message was generated with; "" = Hermes default */,
    val starred: Boolean = false)
data class Conversation(/* existing */, val unread: Int = 0,
    val routineId: String = "" /* set when this chat belongs to a routine */)
```
The turn's usage is written on the last BOT message of the turn that has text (if the turn produced
none, it is only recorded in `UsageStore`).

### `data/UsageStore.kt`

```kotlin
data class UsageDay(val day: String /* yyyy-MM-dd, device time zone */, val model: String /* "" = Hermes default */,
    val turns: Int, val tokensIn: Long, val tokensOut: Long)
enum class BudgetMode { OFF, WARN, BLOCK }
data class UsageBudget(val dailyTokens: Long = 0L /* 0 = no limit */, val mode: BudgetMode = BudgetMode.OFF)
class UsageStore internal constructor(file: java.io.File, scope: CoroutineScope, clock: () -> Long = System::currentTimeMillis) {
    constructor(context: android.content.Context)          // filesDir/chat/usage.json, atomic, debounced
    val days: StateFlow<List<UsageDay>>                     // newest day first; at most 400 days are kept
    val budget: StateFlow<UsageBudget>
    fun record(model: String, tokensIn: Long, tokensOut: Long)   // adds one turn to today's row for that model
    fun setBudget(b: UsageBudget)
    fun todayTotal(): Long                                  // tokensIn + tokensOut of today, all models
    fun overBudget(): Boolean                               // mode != OFF && dailyTokens > 0 && todayTotal() >= dailyTokens
    fun clear()
    fun flush()                                             // blocking
    fun suspendWrites()                                     // after this nothing is written any more (used right before a restore)
}
```
Engine behaviour: `BLOCK` and over budget -> the turn is not sent; an ERROR message says the daily
token budget is reached and where to change it (Settings > Usage). `WARN` -> one NOTE per day, the
first time a turn crosses the budget.

### Wake-on-send (`data/ChatEngine.kt`, `runtime/RuntimeAwait.kt`)

Today `send` returns false unless Hermes is Running. New behaviour: when Hermes is not running and
wake-on-send is allowed, the engine accepts the message, shows it, starts Hermes, waits and then
answers.

```kotlin
// runtime/RuntimeAwait.kt
object RuntimeAwait {
    /** Starts the runtime if it is not busy or running and blocks until it is Running. False when it ended in Error/Stopped,
     *  when [cancelled] became true, or after [timeoutMs]. Never call on the main thread. */
    fun awaitRunning(runtime: RuntimeManager, timeoutMs: Long, cancelled: () -> Boolean): Boolean
}
// ChatEngine: new constructor parameters (both constructors), defaults keep the old behaviour for tests
    private val canWake: () -> Boolean = { false },                 // PocketApp: presencePrefs.settings.value.wakeOnSend && rootfs installed
    private val awaitReady: (cancelled: () -> Boolean) -> Boolean = { isReady() },   // PocketApp: RuntimeAwait.awaitRunning(runtime, 6 * 60_000L, it)
    private val usage: UsageStore? = null,
// ChatEngine companion
    /** Value of [status] for a conversation while the engine waits for Hermes to start; the UI shows a localized text for it. */
    const val STATUS_WAKING = "@waking"
```
- `send`, `runLoop`, `regenerateLast`, `editAndResend` are accepted when `isReady() || canWake()`.
  Inside the run, before the first `generate`: if not ready, set `status[convId] = STATUS_WAKING`, call
  `awaitReady { run.stop }`; on false add an ERROR message ("Hermes could not start. Open the home screen
  to see why.") and end the run; `stop(convId)` cancels the wait.
- A fresh install (rootfs not extracted yet) is NOT woken by a send: `canWake` is false there.

### Unread, stars, active chat (`data/ChatRepository.kt`)

```kotlin
/** The chat that is on screen right now (null = none, also while the app is in the background). Setting it clears its unread count. */
fun setActiveConversation(convId: String?)
val activeConversation: String?
fun markRead(convId: String)
fun markUnread(convId: String)                       // unread = max(1, unread)
fun bumpUnread(convId: String)                       // +1 unless convId == activeConversation (called by the engine per finished BOT turn)
fun setStarred(convId: String, messageId: String, starred: Boolean)
/** Starred messages of all conversations, newest first, at most 300. Blocking disk reads: call off the main thread. */
fun starredMessages(): List<Pair<Conversation, Message>>
fun setRoutine(convId: String, routineId: String)
fun suspendWrites()                                  // after this nothing is written any more (used right before a restore)
```
`forkConversation` resets `unread`, `routineId` and `starred`. `unread` changes never bump `updatedAt`.

### Events (`data/ChatEngine.kt`)

```kotlin
sealed interface EngineEvent {
    data class TurnStarted(val convId: String, val botId: String) : EngineEvent                      // new
    data class TurnCompleted(val convId: String, val messageId: String, val botId: String, val text: String,
        val tokensIn: Long = 0L, val tokensOut: Long = 0L) : EngineEvent                             // two new fields
    // ToolFinished, MemorySaved, BotForged, ApprovalRequested, TurnFailed: unchanged
}
```
Also expose `fun isBusy(convId: String): Boolean` and `val lastActivityAt: Long` (wall clock of the last
run start or end; used by the idle stop).

### `data/DraftStore.kt`, `data/BotShare.kt`

```kotlin
class DraftStore internal constructor(file: java.io.File, scope: CoroutineScope) {
    constructor(context: android.content.Context)          // filesDir/chat/drafts.json, debounced, atomic
    val drafts: StateFlow<Map<String, String>>              // convId -> unsent text (blank drafts are removed)
    operator fun get(convId: String): String
    operator fun set(convId: String, text: String)
    fun prune(validConvIds: Set<String>)
    fun flush(); fun suspendWrites()
}
object BotShare {
    const val FILE_SUFFIX = ".agiri-bots.json"
    /** A portable document: {"agiri_bots":1,"bots":[{name,avatar,color,tagline,systemPrompt,model,effort,voiceSid,voiceStyle,voiceSpeed}]}. No ids. */
    fun encode(bots: List<Bot>): String
    /** Bots with id = "" (the caller assigns `repo.newId()`), builtIn = false. Throws IllegalArgumentException with a short reason.
     *  Limits: at most 50 bots, name <= 60 chars, tagline <= 200, systemPrompt <= 20_000. */
    fun decode(text: String): List<Bot>
}
```

### `PocketApp.kt` (ENGINE writes all of it; the other agents provide the classes with exactly these constructors)

```kotlin
lateinit var usage: UsageStore; private set                       // UsageStore(this)
lateinit var drafts: DraftStore; private set                      // DraftStore(this)
lateinit var presencePrefs: PresencePrefs; private set            // PresencePrefs.get(this)            (PRESENCE)
lateinit var routines: RoutineStore; private set                  // RoutineStore(this)                 (ROUTINES)
lateinit var routineScheduler: RoutineScheduler; private set      // RoutineScheduler(this)             (ROUTINES)
lateinit var backup: BackupManager; private set                   // BackupManager(this)                (CONTROL)
lateinit var presence: Presence; private set                      // Presence(this)                     (PRESENCE)
/** A navigation request from a notification, shortcut, tile or the text-selection menu, consumed by AppRoot. */
val pendingLink = MutableStateFlow<AppLink?>(null)                // AppLink                            (SURFACE)
```
Order at the end of `onCreate`: everything constructed, then `presence.install()`, then
`routineScheduler.rescheduleAll()`. `RoutineScheduler(app: PocketApp)`, `BackupManager(app: PocketApp)`
and `Presence(app: PocketApp)` take the application and read its members lazily (never in their
constructor / property initialisers: `PocketApp`'s other members may not exist yet).
`engine.events` audio handling stays; `TurnCompleted` additionally records nothing here (the engine
records usage itself).

---

## PRESENCE: auto-start, notifications, auto-restart, idle stop

```kotlin
// runtime/PresencePrefs.kt  (SharedPreferences "presence")
data class PresenceSettings(
    val autoStart: Boolean = true,          // light the fire when the app comes to the foreground (once per process, see below)
    val wakeOnSend: Boolean = true,         // sending while Hermes is asleep starts it and delivers the message
    val notifyReplies: Boolean = true,
    val notifyApprovals: Boolean = true,
    val autoRestart: Boolean = true,        // one automatic restart after an unexpected exit
    val idleStopMinutes: Int = 0,           // 0 = never; otherwise 5..240
)
class PresencePrefs private constructor(context: Context) {
    val settings: StateFlow<PresenceSettings>
    fun update(change: (PresenceSettings) -> PresenceSettings)
    companion object { fun get(context: Context): PresencePrefs }
}
// service/AppForeground.kt
object AppForeground {
    val isForeground: StateFlow<Boolean>            // true while at least one activity is started
    fun install(app: android.app.Application)       // ActivityLifecycleCallbacks; safe to call twice
}
// service/Presence.kt
class Presence(private val app: PocketApp) {
    val notifier: ReplyNotifier
    fun install()                                    // wires AppForeground, notifications, auto-start, auto-restart, idle stop
    /** Tells Presence that the user stopped Hermes on purpose (home button, tile, notification): no auto-start until the next process start. */
    fun noteUserStop()
}
// service/ReplyNotifier.kt
class ReplyNotifier(private val context: Context) {
    fun notificationsEnabled(): Boolean              // NotificationManager.areNotificationsEnabled()
    fun cancelFor(convId: String)                    // called when a chat is opened
    fun cancelAll()
    fun postRoutineFailure(routineName: String, reason: String)
    companion object { const val CHANNEL_REPLIES = "replies"; const val CHANNEL_APPROVALS = "approvals"; const val CHANNEL_ROUTINES = "routines" }
}
// runtime/RuntimeManager.kt (additive)
val unexpectedExits: StateFlow<Int>                  // +1 each time watchExit sees an exit that nobody asked for
```
Behaviour:
- **Auto-start:** on the first foreground of a process, when `autoStart`, the rootfs is installed
  (`runtime.isRootfsValid()`), the state is `Stopped`, the saved setup has no problems
  (`ProviderConfig.savedProblems(runtime.settings, runtime.secrets).isEmpty()`, PROVIDERS) and the user
  has not stopped Hermes in this process -> `runtime.start()`. Never auto-start an `Error` state or a
  fresh install.
- **Reply notification:** on `TurnCompleted` when `notifyReplies` and (the app is not in the foreground
  or `chat.activeConversation != convId`): one notification per conversation (id derived from the
  convId, later replies update it), title = bot name (group: "bot · chat title"), text = plain preview of
  the reply (Markdown stripped, <= 300 chars, `BigTextStyle`), tap -> `AppLinks.chatPendingIntent`,
  action "Reply" with `RemoteInput` -> `NotificationActionReceiver` -> `engine.send(convId, text)`,
  action "Mark read". Conversations with `routineId` use `CHANNEL_ROUTINES`. `TurnFailed` in the
  background -> a quiet "could not answer" notification. Opening the chat cancels its notification.
- **Approval notification** (`notifyApprovals`, `CHANNEL_APPROVALS`, high importance): command text,
  actions "Allow once" / "Deny" -> `NotificationActionReceiver` -> `engine.resolveApproval(convId, choice)`
  (only choices present in `PendingApproval.choices`), tap opens the chat.
- **Auto-restart:** on an `unexpectedExits` increment with `autoRestart`: wait 2 s, `runtime.start()`;
  at most 2 automatic restarts per 10 minutes, then leave the Error on screen.
- **Idle stop:** when `idleStopMinutes > 0`: Hermes Running, app in the background, no conversation busy,
  no terminal command running, `engine.lastActivityAt` older than the limit -> `runtime.stop()` (not a
  "user stop").
- **Service notification** (`AgentService`): text also says how many chats are answering; keep
  `START_NOT_STICKY` and the existing foreground/stop logic exactly as it is. The Stop action calls
  `presence.noteUserStop()` first.
- Put the pure decisions (should auto-start, should notify, restart budget, idle check, notification
  preview text) in small pure functions and unit-test them (`PresenceLogicTest`).

---

## ROUTINES: scheduled prompts

```kotlin
package dev.pocketagent.app.routines
enum class RoutineKind { DAILY, INTERVAL, ONCE }
data class Routine(
    val id: String, val name: String, val prompt: String,
    val botId: String = BotPresets.HERMES_ID,
    val kind: RoutineKind = RoutineKind.DAILY,
    val hour: Int = 8, val minute: Int = 0,
    val days: Int = 0x7F,                 // DAILY: bit 0 = Monday .. bit 6 = Sunday
    val intervalMinutes: Int = 60,        // INTERVAL: 15..10080
    val onceAt: Long = 0L,                // ONCE: wall-clock millis
    val enabled: Boolean = true,
    val convId: String = "",              // the routine's own chat; created on the first run
    val model: String = "", val effort: String = "",     // "" = bot / Hermes default
    val lastRunAt: Long = 0L, val lastStatus: String = "" /* "", "ok", "failed", "skipped" */,
    val createdAt: Long = System.currentTimeMillis(),
)
class RoutineStore internal constructor(file: java.io.File, scope: CoroutineScope) {
    constructor(context: Context)                          // filesDir/chat/routines.json, atomic, corrupt file kept as .bad
    val routines: StateFlow<List<Routine>>
    fun get(id: String): Routine?
    fun save(r: Routine); fun delete(id: String); fun newId(): String
    fun flush(); fun suspendWrites()
}
object RoutineSchedule {
    /** The next run strictly after [afterMillis], or null (disabled, ONCE in the past, no day selected). Pure; DST-safe (java.time). */
    fun nextRun(r: Routine, afterMillis: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Long?
}
class RoutineScheduler(private val app: PocketApp) {
    val nextRunAt: StateFlow<Long?>                         // earliest next run over all enabled routines
    fun rescheduleAll()                                     // one exact alarm for the earliest run (setExactAndAllowWhileIdle, RTC_WAKEUP)
    fun runNow(id: String)                                  // from the UI
    fun onAlarm()                                           // from RoutineAlarmReceiver: runs every routine that is due, then reschedules
}
class RoutineAlarmReceiver : BroadcastReceiver()            // not exported
class BootReceiver : BroadcastReceiver()                    // BOOT_COMPLETED, MY_PACKAGE_REPLACED -> rescheduleAll()
```
A run: wait for `chat.loaded` (cold process), make sure the routine's chat exists (create it with the
routine's name as title, `chat.setRoutine`, `chat.setOverrides(model, effort)`), then
`engine.send(convId, prompt)`; the engine wakes Hermes itself (if `send` returns false or the budget
blocks: status "skipped"/"failed" and `presence.notifier.postRoutineFailure`). The result reaches the
user through PRESENCE's reply notification and the unread badge. If the routine's run woke Hermes
while the app was in the background, stop Hermes again once nothing is busy (do not count as a user
stop). Missed runs (phone was off): run once at the next opportunity if less than 6 hours late, else
skip. Hold a wake lock (`app.wakeLocks`) from the alarm until the send has been handed to the engine.
An `INTERVAL` routine's next run counts from its last run.

UI (`ui/routines/`): `@Composable fun RoutinesScreen()` (list with next-run time, enable switch, run
now, last status, an empty state with Pêt and 4 one-tap templates such as a morning briefing, an
evening journal question, a weekly review, a daily word in Kurdish) and
`@Composable fun RoutineEditorScreen(routineId: String?)` (name, prompt, bot, kind, time, weekdays,
interval, once date/time, model/effort chips like the chat controls, delete). Navigation:
`Route.Routines`, `Route.RoutineEditor(routineId)` (SURFACE adds them). Use plain Compose pickers
built from existing components (no Material date picker dependency problems: material3 is available,
its `TimePicker`/`DatePicker` may be used if they theme acceptably; otherwise simple steppers).

---

## SURFACE: manifest, links, shortcuts, tile, text selection, navigation

```kotlin
package dev.pocketagent.app
sealed interface AppLink {
    data class OpenChat(val convId: String) : AppLink
    data object NewChat : AppLink
    data object Terminal : AppLink
    data object Routines : AppLink
    data object Usage : AppLink
}
object AppLinks {
    const val ACTION_OPEN_CHAT = "dev.pocketagent.app.OPEN_CHAT"
    const val ACTION_NEW_CHAT = "dev.pocketagent.app.NEW_CHAT"
    const val ACTION_TERMINAL = "dev.pocketagent.app.TERMINAL"
    const val ACTION_ROUTINES = "dev.pocketagent.app.ROUTINES"
    const val EXTRA_CONV_ID = "conv_id"
    fun chatIntent(context: Context, convId: String): Intent            // explicit MainActivity intent; a distinct data Uri per chat
    fun chatPendingIntent(context: Context, convId: String): PendingIntent
    fun parse(intent: Intent?): AppLink?                                 // pure enough to unit-test with a fake
}
// ui/Nav.kt: new routes (with encode/decode)
data object Routines : Route; data class RoutineEditor(val routineId: String?) : Route; data object Starred : Route
```
- `MainActivity.handleIncomingIntent`: `AppLinks.parse` -> `PocketApp.pendingLink`; `ACTION_PROCESS_TEXT`
  ("Ask Agirî" in the text-selection menu, read-only text) -> the existing `incomingShare` flow. Never
  replay a consumed intent from Recents (same guard as ACTION_SEND).
- `AppRoot`: consumes `pendingLink` (OpenChat -> Chats tab then the chat, if it still exists; NewChat ->
  a fresh 1:1 with Hermes; Terminal; Routines; Usage -> `Route.Settings("usage")`), adds the three
  routes to `RouteContent` (`RoutinesScreen()`, `RoutineEditorScreen(id)`, `StarredScreen()`; detail
  screens without the bottom bar; Routines belong to the Home tab, Starred to the Chats tab).
- Manifest: `RECEIVE_BOOT_COMPLETED`; receivers `.routines.RoutineAlarmReceiver` (not exported),
  `.routines.BootReceiver`, `.service.NotificationActionReceiver` (not exported); service
  `.service.FireTileService` (`BIND_QUICK_SETTINGS_TILE`, `android.service.quicksettings.action.QS_TILE`);
  `PROCESS_TEXT` filter on `MainActivity`; `android.app.shortcuts` meta-data on **every** launcher
  `<activity-alias>` (static shortcuts: New chat, Routines, Terminal; intents target `.MainActivity`
  explicitly). Keep `MainActivity` always enabled and every existing entry intact.
- `FireTileService`: tile state follows `runtime.state` (active = Running, unavailable while
  extracting/starting, inactive otherwise); a tap starts Hermes, or stops it (calling
  `presence.noteUserStop()`); works when the app process was dead (PocketApp is created for the service).

---

## CHAT: the chat and home screens

Uses ENGINE's new API. Tasks:
1. **Always-available input.** `ChatInputBar`: the input is usable whenever the saved setup is complete
   and (Hermes is Running, or `presencePrefs.wakeOnSend` and the runtime is installed). While the
   engine's status is `ChatEngine.STATUS_WAKING` show "Lighting the fire… your message is sent as soon as
   Hermes is up" (subtitle + a slim banner). The "asleep" card stays only when wake-on-send is off or
   nothing is installed. Same for the quick-talk field on Home.
2. **Find in chat**: a search action in the chat's menu/top bar -> search field, match count, previous /
   next, scrolls to and highlights the match (accent outline); closes with back.
3. **Usage**: tokens in the message meta line of BOT messages ("1.2k in · 340 out", compact numbers,
   localized), and totals of the chat (turns, tokens in/out) in the Chat Controls sheet. Hidden when 0.
4. **Stars**: star / unstar in the message actions and a small star in the meta line; a
   `StarredScreen()` ("Saved messages": list with chat title, bot, time, preview; tap opens that chat and
   scrolls to the message; unstar) reachable from the Chats screen's top bar.
5. **Unread**: `chat.setActiveConversation(convId)` while a chat is resumed (lifecycle-aware: null on
   pause/dispose), `presence.notifier.cancelFor(convId)` on open, an ember badge with the count in
   `ConversationRow`, bold title, a new "Unread" filter chip, "Mark as unread / read" in the row's
   long-press sheet.
6. **Drafts**: replace the in-memory `ChatDrafts` with `PocketApp.drafts` (saved while typing, debounced;
   cleared on send); the chat list shows "Draft: …" in the accent colour when a chat has one.
7. **Export**: `/export` and a menu item offer "Share" (as today) and "Save to file" (Markdown through
   `ActivityResultContracts.CreateDocument`).
8. **Home**: a third tile "Routines" with the next run time (`routineScheduler.nextRunAt`), a small
   "today" usage line (tokens today, tap -> Settings "usage"), and an unread count on the recent chats.
   Routine chats show a small clock icon in rows.
Deep link from StarredScreen/search results to a message: add an optional, process-local
`ChatScrollTarget` (convId -> messageId) object in `ui/chat` that `ChatScreen` consumes.

## CONTROL: settings, bots, Hermes screen, backup

1. **Settings > Usage** (key `"usage"`): today / last 7 days / all time (turns, tokens in, out), a pixel
   bar chart of the last 14 days, a per-model table, the daily budget (number field + OFF / WARN / BLOCK
   chips) and "Clear statistics" (confirm). Data: `PocketApp.usage`.
2. **Settings > Behaviour** (key `"behaviour"`): switches for every `PresenceSettings` field (idle stop
   as chips: never, 15, 30, 60, 120 min), notification status (`presence.notifier.notificationsEnabled()`
   with a button to the system notification settings of the app), and the existing battery-optimisation
   row moved/duplicated here.
3. **Settings > Backup** (key `"backup"`): "Create backup" (options: include attachments, include Hermes
   files) -> `CreateDocument("application/zip")`; "Restore from backup" -> `OpenDocument` -> summary of
   the file -> confirm -> restore -> the app restarts. Plain texts say what is inside and that
   credentials are never included.
4. **`data/BackupManager.kt`**
```kotlin
class BackupException(message: String) : Exception(message)
class BackupManager(private val app: PocketApp) {
    data class Options(val includeAttachments: Boolean = true, val includeHermes: Boolean = true)
    data class Summary(val createdAt: Long, val appVersion: String, val conversations: Int, val bots: Int,
        val routines: Int, val files: Int, val bytes: Long)
    fun export(out: java.io.OutputStream, options: Options = Options()): Summary     // blocking
    fun inspect(input: java.io.InputStream): Summary                                 // validates without writing; throws BackupException
    /** Replaces the app's data with the backup. Blocking. The caller restarts the process afterwards ([restartApp]). */
    fun restore(input: java.io.InputStream): Summary
    fun restartApp()
}
```
   Zip layout: `manifest.json` (format 1, app version, created, counts), `chat/**` (bots, conversations,
   messages, routines, usage, drafts), `attachments/**`, `prefs/*.json` (appearance, voice, presence as
   JSON maps of the SharedPreferences), `hermes/SOUL.md`, `hermes/memories/*`, `hermes/skills/**`.
   **Never** anything from `files/secrets`, `.env`, `config.yaml`, credential or auth files, the API
   key, provider settings. Restore: refuse while any chat is generating; extract to a staging directory
   first and validate everything (zip-slip: every entry must resolve inside its target; caps: 2 GB total,
   200k entries, known top-level folders only); write a safety copy of the current data to
   `files/backup/pre-restore.zip`; stop Hermes; call `suspendWrites()` on `chat`, `usage`, `drafts`,
   `routines`; swap the data in; write prefs with `commit()`; restart the process. Put the pure parts
   (entry validation, manifest) where a JVM unit test can reach them (`BackupManagerTest` with temp dirs:
   round trip, zip-slip entry rejected, secrets never exported).
5. **Bots**: "Export" (one bot from the editor, all custom bots from the list; share sheet or save to
   file) and "Import" (file or clipboard) using `BotShare`; imported bots get new ids.
6. **Hermes screen**: create a skill (name, description, body template in Hermes's SKILL.md format; look
   at an existing skill in the rootfs for the exact front matter) and delete a skill (confirm; never
   outside the skills directory, canonical-path check). **Terminal**: render ANSI SGR colours (16
   colours + bold, mapped to theme colours) instead of stripping them; other escape sequences are still
   stripped.

## PROVIDERS: more LLM providers

Add to the existing three Google modes: **OpenRouter**, **Anthropic**, **OpenAI**, and **OpenAI-compatible
(custom URL)**. Before writing config for a provider, read how Hermes v0.23.3 resolves it (provider
name, env var, base URL, model id format) in the source on the phone and cite file:line in your
report.
- `AuthMode` gains the new values; every `when (mode)` in the app stays exhaustive (grep the whole tree,
  including `diag/` and tests; report any file outside your ownership that needs a branch under
  `REQUESTS`; there should be none if you keep helper functions total).
- One stored API key per provider in `SecretStore` (same protection as the Gemini key; each key is
  registered with the redactor so it can never reach a log, the diagnostics report or the UI). The
  custom mode also has a base URL (https only, or http for 127.0.0.1/localhost/private LAN addresses,
  validated: it ends up in config.yaml).
- `ProviderConfig`: `providerName`, `problems`, `renderEnv`, `renderYaml`, `describe`, `suggestions`,
  `defaultModel` cover the new modes. **The generated `.env` and `config.yaml` for the three existing
  Google modes must stay byte-identical** (golden tests exist; the CI smoke test boots them). New golden
  tests for each new mode. Add:
  `fun savedProblems(settings: Settings, secrets: SecretStore): List<Problem>` (used by other agents) and
  keep `Settings.providerInputs(geminiApiKey: String)` and `ProviderConfig.providerName(inputs)` working
  as they are called from `PocketApp`.
- Model ids: free text field plus a few suggestions per provider taken from Hermes's own provider
  definitions in the source (do not invent model ids). `ModelIds.normalize` in the engine passes other
  providers' ids through unchanged; a per-bot or per-chat model is sent with the current provider name.
- `ProviderSection` UI: a provider picker first (Google AI Studio / Vertex stay as they are, visually
  first), then that provider's fields; keys are never shown after saving (same pattern as today:
  "stored" + clear). Switching provider never deletes another provider's stored credentials.
- `MainViewModel`: form state for the new fields; `savedConfigIncomplete()` keeps its meaning.

## ART: icons

Add monochrome 12x12 icons (same style and palette convention as the existing ones in `icons.pxl`:
only `x` and `.`) and the matching `PixelIcon` enum entries, **exactly these names**:
`CLOCK`, `STAR`, `STAR_OFF` (outline), `BELL`, `CHART`, `ARCHIVE` (box: backup), `DOWNLOAD`, `UPLOAD`,
`MOON` (idle / asleep), `LINK`, `FILTER`, `BOLT` (provider / speed).
Check how `PixelIcon` maps to sprite names and keep every existing entry. Render them with
`tools/pixel-preview.ps1 -File app\src\main\assets\pixel\icons.pxl -Out dist\pixel-preview-p3`
(PowerShell: `powershell -NoProfile -ExecutionPolicy Bypass -File ...`) and look at the PNGs to make
sure each one reads clearly at small size; iterate until they do.

---

## After the build (lead agent)

1. Integration: compile (`tools\local-build.ps1 -BuildNumber 30`), fix, run the unit tests (`-Test`).
2. Review by a stronger model per area, each finding verified by a second reviewer, fixes, rebuild.
3. Phone checks with `google/gemini-4-hydrogen` at low effort: wake-on-send from a cold app, auto-start,
   reply notification + inline reply, a routine firing while the app is closed, usage numbers of two
   turns in one chat, budget BLOCK, find in chat, star, drafts across a force-stop, backup -> restore
   round trip, bot export/import, tile, shortcuts, "Ask Agirî", new provider config written correctly
   with the existing Vertex setup still chatting, force-stop -> reopen.
