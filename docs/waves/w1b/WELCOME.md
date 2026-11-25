# Wave 1b, area WELCOME: contract draft (the first-run wizard)

`J/` = `app/src/main/java/dev/pocketagent/app/`, `T/` = `app/src/test/java/dev/pocketagent/app/`, `R/` = `app/src/main/res/`, `A/` = `app/src/main/assets/`; package `dev.pocketagent.app` is written `app`. Written 2026-11-25 from `docs/waves/w1b/SPLIT.md` (read first), `docs/phase4-plan.md` 7.1 (items 1.7, 1.3, 1.8; lines 843 to 879), 4.1, 8 (S8) and 13, `docs/design-language.md` 2.4, 3 (patterns 2, 7, 15, 16, 18), 4, 5, 6.3, 6.4, 7, 8.4, 8.16, 9, 10, 13, `docs/waves/w0-contract.md` (what exists), **`docs/waves/w1a-contract.md` (its registry, section 3, and rulings R1 to R15 are the only source for everything 1a creates; 1a is NOT in the work branch's code and is built in the worktrees under `dist/`)** and the files named below, read in the working tree on 2026-11-25 (other agents have uncommitted edits in it; the line numbers are of the files as read and are hints, the symbols are the contract). Taste and policy choices below are the plan's own recommendation and are marked "assumed, owner may change".

**Two agents build this draft** and read it together: **WELCOME-CORE** (pure rules, the state machine, the key guide, the guided-chat script, one settings flag, `RuntimeManager.prepareRootfs()`, their tests) and **WELCOME-UI** (the screens, the scene per step, the key guide pictures, the guided chat view, `HearthScene`'s one new parameter, strings). Their files are disjoint (section 1). CORE has no 1a dependency and is built in slice A (SPLIT section 9); UI and the one function `FirstRun.initialRoutes` are built in slice D.

BINDING from the design language: 8.4 (five steps on one scene that grows lighter; the phone's own language first; a plain guide to a free key and one sentence on what it means; the kindling is the progress of unpacking while Pêt shows short lines; the first hello or a guided chat that needs no key; two skippable questions; can be left at any step; never appears again), 8.16 (kinds **Stage** and **Flow** at once: at most four controls along the bottom, a progress line of whole steps, Back always there, nothing lost on leaving; the five states), rule 1 (the fire tells the state), 6.4 (the wizard teaches the fire gesture once), patterns 7, 15, 16, 18, section 4 (haptics), 10 (labels, double text, mirrored), 13.2 and 13.5 (rooms and voice).

## 1. Scope and files

| # | Work item (SPLIT id) | Agent | Kind |
|---|---|---|---|
| W1 | Five steps on one continuous scene: language, meet Pêt, model, kindling, first hello (B8) | UI (screens), CORE (`WelcomeFlow`) | core |
| W2 | Unpacking starts at the first step with no provider set: `RuntimeManager.prepareRootfs()` (B9) | CORE | core, never cut |
| W3 | The model step: three-picture key guide, a button that opens the key page, "I use something else" opens the full provider form (B10) | UI (`KeyGuideUi`), CORE (`KeyGuide`) | core |
| W4 | Never again after finished or skipped; never to an existing installation; derived, no write on an existing installation (B11) | CORE | core, never cut |
| W5 | The guided chat on the practice model: a question, a tool step, an answer; zero tokens (B12) | CORE (`GuidedChat`), UI (`GuidedChatUi`) | core |
| W6 | The one-sentence cost of a free key, a link to the provider's terms, "not offered in every country" (B13; needs lookup L1) | UI, CORE (facts and addresses) | core |
| W7 | The two optional questions: weather (only while `WeatherStore.available`) and days (B14) | UI | core (cut order 9 of SPLIT 11) |
| W8 | The fire gesture taught once, with no effect on the runtime (B15) | UI, CORE (`FireLesson`) | core |
| W9 | `HearthScene.skyOverride` (one additive parameter) and the sky per step (SPLIT open point 7) | UI | core, the lightening between steps is an extra (two fixed frames stay) |
| W10 | Dry preview (no writes, no unpack) for pictures on the owner's phone, and the debug pin `firstrun` | CORE (pin), UI | core |
| X1 | "Paste" button for the key field | UI | extra |
| X2 | Dry replay of the guided chat (a canned transcript, for pictures) | UI, CORE | extra |
| X3 | Celebration on the first answer ever (pattern 15) | UI | extra |
| X4 | Pêt's four lines rotate by themselves (static first line, tap for the next, stays) | UI | extra |
| X5 | `FireAnchor.publish` from the Welcome's fire so the splash flame can travel to it | UI | extra (depends on START and experiment S8) |

Cut order and never-cut list: section 6.

**Files WELCOME-CORE owns (all new unless said):**
- `J/ui/onboarding/FirstRun.kt`, `WelcomeFlow.kt`, `KeyGuide.kt`, `GuidedChat.kt`.
- `J/runtime/Settings.kt` (modify: one property, `firstRun`), `J/runtime/RuntimeManager.kt` (modify: one new function, `prepareRootfs()`, placed after `restart()`; nothing else in the file changes).
- `T/ui/onboarding/FirstRunRulesTest.kt`, `WelcomeFlowTest.kt`, `KeyGuideTest.kt`, `GuidedChatScriptTest.kt`; `T/RuntimePrepareTest.kt`.

**Files WELCOME-UI owns (all new unless said):**
- `J/ui/onboarding/WelcomeScreen.kt`, `WelcomeSteps.kt`, `WelcomeScene.kt`, `KeyGuideUi.kt`, `GuidedChatUi.kt`, `WelcomeTags.kt`, `WelcomeChrome.kt`.
- `J/ui/pixel/HearthScene.kt` (1a's file, transferred from 1a's SCENE for 1b; one additive parameter, section 3.7).
- `R/values/strings_welcome.xml` (prefix `wel_`).
- `T/ui/onboarding/WelcomeTagsTest.kt`, `WelcomeSkyTest.kt` (slice D; it needs 1a's `SkyFrames`, so it cannot exist in slice A). SPLIT lists no UI tests; these two are added here and INTEGRATE-B keeps them.

Nobody else edits these files. The Welcome edits no existing file besides the four above (`Settings.kt`, `RuntimeManager.kt`, `HearthScene.kt`) and adds nothing to the manifest (no permission, no component; SPLIT section 5).

## 2. What exists today, and what 1a will have changed

**Today (read in the tree):**
- No first-run flow exists. The first start lands on `Route.Home`; Home's `FIRST_RUN` state (`J/ui/home/HomeScreen.kt:141-151, 241-245`) shows a card "setup incomplete" that opens `Route.Settings("provider")`, plus the hint `ui_home_hint_first` ("The first start takes about a minute. It only happens once.", `strings_chat.xml:23`).
- **A provider is needed to unpack.** `RuntimeManager.start()` (`RuntimeManager.kt:189-214`) runs `ensureRootfs()` (`:344-409`) and then `launchHermes()` (`:443`), which refuses a setup with a blocking problem. The user cannot unpack the image without starting Hermes. `ensureRootfs()` already does everything needed (disk check, park and restore of user state, extraction with `RuntimeState.Extracting(fraction, detail)`, the stamp `files/rootfs.stamp`), is guarded by `mutex`, and returns true without a state change when the image is valid. `ensureServiceStarted()` (`:309`) and `holdWake` (`:316`) keep the process alive during the work.
- **Presence does not interfere.** `Presence.shouldAutoStart` (`service/Presence.kt:41-44`) needs a valid image, state `Stopped` and a complete provider setup, and runs once per process (`:184-185`); a fresh install never satisfies it.
- `MainViewModel` (`MainViewModel.kt`): `authMode`, `endpoint`, `model`, `apiKeyInput`, `selectMode`, `selectEndpoint`, `saveSettings(): List<ProviderConfig.Problem>` (writes the typed key to `SecretStore` and the provider settings; restarts a running gateway), `savedConfigIncomplete()` (snapshot-state backed; true on a fresh install), `start()`, `stop()` (`pa.presence.noteUserStop()` then `runtime.stop()`), `hasStoredApiKey`.
- `ProviderSection(vm)` (`J/ui/settings/ProviderSection.kt:62`, `internal`, so callable from `ui/onboarding`): the full provider form with its own "Save and apply" button. The default for Google is `AuthMode.API_KEY` with `ApiEndpoint.STUDIO` (`Settings.authMode`, `Settings.endpoint` defaults), the AI Studio key path; `ProviderConfig.problems` does not check the shape of a Google key.
- **The practice model** (Wave 0): `GuestPrefs.get(ctx).setPracticeModel(on)`, `.practiceModel: StateFlow<Boolean>`; `RuntimeManager.onGuestOptionsChanged(restartIfRunning)` (`:294`); while practice is on `ProviderConfig.savedProblems` is empty (`ProviderConfig.kt:197`) and `launchHermes` skips the provider check (`:454`); a `PracticeBanner` row shows at the top of every screen (`ui/settings/PracticeBanner.kt`); on a build that is not the twin the switch ends after two hours and is reset at every cold start (`GuestPrefs.kt:46-57`). `PracticeModel.KEYWORDS` has 15 words (`runtime/PracticeModel.kt:46`); the python model answers `RUN_TOOL` with one terminal call `echo hello-from-tool` when the request carries a terminal tool, and after the tool result with `tool-result-seen: hello-from-tool` (`rootfs/smoke/mock_llm.py:260-265, 594`). It matches a keyword as a whole word (line 707). The first turn after a cold start takes 15 to 30 s (AGENTS.md).
- Chat engine: `app.engine.send(convId, text, onlyBotIds, attachments): Boolean` (accepts while Hermes is ready, or wakes it when wake-on-send allows); `app.chat.createConversation(botIds)`, `deleteConversation(id)`, `messages(convId): StateFlow<List<Message>>`; messages of `Role.USER`, `Role.TOOL` (`toolName`, `toolStatus` running, completed, interrupted, `text` = the label), `Role.BOT`, `Role.ERROR` (`data/Models.kt:36-81`); `app.engine.busy`. `PocketApp` already plays `SoundCue.COMPLETE`, `TOOL` and `ERROR` on engine events (`PocketApp.kt:201-215`).
- Navigation: `Navigator(initial: List<Route>)`, `reset(routes)` (`Nav.kt:42-88`); the shell holds it in `rememberSaveable(saver = Navigator.Saver) { Navigator() }` (`AppRoot.kt:215`), so a restored process returns to the saved stack. `BackHandler(enabled = nav.canPop || route != Route.Home) { if (!nav.back()) nav.tab(Route.Home) }` (`AppRoot.kt:249`) would send a back press on any other root to Home.
- `AppRoot` wraps everything in `LocaleScope(overrideLanguage ?: appearance.language, ...)`, so writing `Appearance.language` changes every screen at once (`AppRoot.kt:132-145`); `DebugOverrides.setLanguage(tag)` does the same in memory (test builds).
- `HearthScene(state, modifier, progress, mood, stokeBoost, celebration)` (`HearthScene.kt:66-73`) forces `.height(280.dp)` today; it reads the theme palette and the real classic night.
- `Settings` (prefs `settings`, `Settings.kt`) has no first-run flag. `BackupCore.PREF_SCHEMA` lists only `appearance`, `voice_sound`, `presence` (`BackupManager.kt:244`): the `settings` file is not in a backup.
- Connectivity: the manifest declares `ACCESS_NETWORK_STATE` (`RuntimeManager.dnsServers` already reads it).
- Debug links: `DebugLinks.EXTRA_*` and `DebugRequest` (`DebugLinks.kt:26-64`), `DebugOverrides` (language, rtl).

**What 1a will have changed (from `docs/waves/w1a-contract.md`; not in the tree yet, so every item below is "as the registry spells it"):**
- `HearthScene` (3.8) gains `collapse`, `reveal`, `bottomBand`, `woodpile`, `fireHold: () -> Float`, `onFireTransition: (FireTransition.Kind) -> Unit`, `pet: PetController?`; the height comes from `modifier`; it reads `rememberSkyFrame()`, `rememberStarTable()`, `rememberWeather()`, `SceneDaysStore.lists`, `Appearance.classicFire`; the stage is allocated once. `SkyFrame`, `SkyInputs`, `SkyFrames.build(SkyInputs): SkyFrame?`, `SkyMode`, `SceneCalendar.LISTS`, `DayList`, `DayGroup`, `DayListTexts.title(id)`, `SceneDaysStore.get(ctx)` with `setList(id, on)`, `WeatherStore.available`/`setEnabled`, `FireSwitch.classify`/`movedBeyond`, `FireRing.fraction`, `FireTuning.HOLD_MS = 700L`, `FireGeometry(geo).hitRect(grid, minPx): FireHit`, `StageGrid.forScene`, `SceneGeometry.of`, `PetController` with `post(PetEvent)`, `rememberPetController()`, `PetEvent.Tap(n)` (even n gives WAVE when Pêt is not asleep), `SceneSounds(hearth, rainLevel)`, `SoundCue.SPARK`, `HomeSky`, `AppearanceStore.pin`.
- `SceneDaysStore`'s migration decides "existing installation" with `SceneCalendar.existingInstall(filesDir)` (`rootfs.stamp` or `chat/conversations.json` exists), and writes `lists` and `migrated=1` in one `apply()` at the first Home composition (1a ruling R4). **This collides with Wave 1b's early unpacking** (risk 1, request SC-2): the Welcome unpacks the image at its first step, so by the time Home is first composed a brand-new user's `rootfs.stamp` exists and the rule would call them an existing installation and switch the culture list `newroz` on for them, which 1a ruling R14 forbids ("cultural days all off, an existing install keeps Newroz").

## 3. The design

### 3.0 Rulings of this draft (assumed, owner may change, unless marked fixed by the plan)

| # | Ruling |
|---|---|
| D1 | The Welcome is one `HearthScene` with a card over it (a Stage and a Flow at once). The fire in the scene always shows the **real** state of the runtime (unpacking is the fire catching, from the first step); only the **sky** is set by the step (night to day). Pêt is the 1a Pêt (controller), waking up when the fire catches. |
| D2 | **Unpacking starts at the first composition of a live Welcome** (`prepareRootfs()`), not at "Next". It is never cancelled by Skip or Back: it goes on in the background and Home shows the same kindling. |
| D3 | **Two guarantees (plan 1.7).** "Never to an existing installation" is derived on every start from the files and from the stored value and writes nothing. "Never again after finished or skipped" is a stored value written only when the Welcome ends. A third value, `started`, is written at the first composition of a **live** Welcome (never on an existing installation), because the unpacking creates the very files that would otherwise make the next start look like an existing installation; `started` makes the Welcome resume at the saved step. |
| D4 | **Live and dry.** A *live* Welcome is the real first run (writes, unpacks, can start the agent). A *dry* Welcome is a preview that writes nothing and touches no runtime: it is what the debug pin `firstrun=reset` gives on any build, and what the screen becomes when it is reached by a debug link on an existing installation. On the owner's package only dry is possible (the Welcome never arrives by itself there). |
| D5 | The agent is **started automatically** when the image is unpacked and a provider is saved and the step is the model step or later ("the agent is ready when the user is", plan 1.7). With no provider it is not started: the practice chat starts it on request. |
| D6 | **The guided chat is a real chat on the practice model**, driven by the chat engine, shown in the Welcome's own small transcript view (not the chat screen). The text that goes out is the shown question plus the keyword `RUN_TOOL`; the view hides the keyword. The conversation is deleted and the practice switch is turned off when the Welcome ends (SPLIT open point 15). Experiment E-B5 is therefore not needed for the Welcome (the keyword never reaches a Markdown view). |
| D7 | **The fire lesson has no effect on the runtime.** A tap stokes (flare, haptic `STOKE`, sound `SPARK`); a hold fills the ring and ends in a puff and a Pêt note. Neither calls `start()` or `stop()` (a stop would also cancel the unpacking, `RuntimeManager.stop()` sets `cancelExtract`). |
| D8 | Both optional questions are asked on step 4 (the waiting step), below Pêt's lines, as quiet cards with "Not now" implicit. The weather card is composed only while `WeatherStore.available` (dark in 1b: the contact is empty, 1a 1.2); the days card shows the `CULTURE` lists whose `DayListTexts.title(id) != 0`, none on by default (1a R14). |
| D9 | Skip asks for confirmation **only** when a typed key has not been saved; otherwise it leaves at once ("nothing lost on leaving"). |
| D10 | A key is checked softly: empty and inner spaces block Save; "looks short" (under 20 characters) is a hint, not a block (the shape of a Google key is not checked anywhere in the app and is not invented here). |
| D11 | The first hello continues into the real chat: Finish after an answer goes to Home with the chat pushed on top (`[Home, Chat(id)]`). With the tour or with no chat it goes to Home. |
| D12 | The language step writes `Appearance.language` through `AppearanceStore.update` in a live Welcome and `DebugOverrides.setLanguage` (memory) in a dry one. Answer and dictation languages (LANG's `LanguagePrefs`) are not touched: defaults. |
| D13 | **What's new.** A live Welcome sets START's "seen" marker at its first composition (not at the end), so a user who leaves the wizard in the middle is not shown "What's new" on top of it (request ST-1). |
| D14 | The Welcome asks for **no permission**. Notifications are left to Android's own first-notification dialog; the microphone to the chat. |
| D15 | Offline is read with `ConnectivityManager.activeNetwork` and `NetworkCapabilities.NET_CAPABILITY_INTERNET` (permission exists). It changes one line of text on step 3 and nothing else; the unpacking and the tour need no network. |

### 3.1 The five steps (what the user sees)

```
+--------------------------------------+   the scene fills the top 40 % (240 to 360 dp), under the status bar:
|  sky of the step, hills, fire, Pêt   |   its sky is lighter with each step; the fire is the real state
|          (the fire is a target)      |
| +----------------------------------+ |   the card overlaps the scene by 28 dp; it scrolls
| | Step title (pixel font)          | |
| | the step's content               | |
| +----------------------------------+ |
|  [=][=][ ][ ][ ]   progress (5 whole steps, the ember on the current one)
|  [Back]   [Skip]              [Next] |   at most four controls; Back always there (disabled on step 1)
+--------------------------------------+
```

| Step | Title (key) | Content | Primary button | Scene |
|---|---|---|---|---|
| 1 `LANGUAGE` | `wel_lang_title` | LANG's `LanguageChoices` (the phone's own language first, every language under its own name, a "machine translated" mark where LANG says so), one muted line `wel_lang_body` | `wel_next` | sky NIGHT, fire at the real state (unpacking has just started) |
| 2 `MEET` | `wel_meet_title` | Pêt's two short paragraphs `wel_meet_body_1` (what the app is: an agent that lives on this phone; only the language model is far away) and `wel_meet_body_2` (the fire shows what the agent is doing), then the muted name line `wel_meet_name` | `wel_next` | sky DAWN; Pêt waves once (`PetEvent.Tap(2)`) |
| 3 `MODEL` | `wel_model_title` | the key guide (3 pictures with a sentence each), `wel_key_open`, the key field, the cost sentence with `wel_terms_link`, `wel_model_other`, offline note | `wel_key_save` when a valid key is typed, else `wel_key_later` (when a provider is already saved: `wel_continue`) | sky SUNRISE |
| 4 `KINDLING` | `wel_kindle_title` | status line with the unpacking percent, Pêt's four lines (`wel_pet_line_1..4`), the fire hint, the optional cards | `wel_continue`, enabled when the image is unpacked | sky DAY (early); fire catching |
| 5 `HELLO` | `wel_hello_title` (provider saved) or `wel_tour_title` (none) | a real first message field with one chip, or the guided chat | `wel_finish` (after an answer or when the tour is skipped) | sky DAY; fire lit once the agent runs |

**Language step.** `welcome.lang.<code>` rows; picking one calls `onPick(tag)`; the whole screen re-composes in the language at once (AppRoot's `LocaleScope`). The selected row is the stored `Appearance.language` (`""` = System, shown first with the phone's own name by LANG).

**Model step, the free path (plan 1.7, B10).** Text order: `wel_model_intro` (one sentence); the guide: three rows, each a 24 by 24 sprite drawn at 2u per pixel (DL 9: about 48 dp) and one sentence (`wel_key_step_1..3`): open the key page and sign in; choose Create API key; copy the key and come back; the button `wel_key_open` (opens `KeyGuide.KEY_PAGE_URL` through `LocalUriHandler.openUri`, in the browser; the app goes to the background, which is why the foreground service already runs for the unpacking); the field `wel_key_field` (`PocketTextField(password = true, singleLine = true)`, bound to `vm.apiKeyInput` after `vm.selectMode(AuthMode.API_KEY)` and `vm.selectEndpoint(ApiEndpoint.STUDIO)`); under it, in the muted `Type.label` style, **the cost sentence** `wel_model_cost` and the link button `wel_terms_link` (opens `KeyGuide.TERMS_URL`); then the ghost button `wel_model_other` ("I use something else") which opens a `PocketSheet` titled `wel_model_other_title` that contains `ProviderSection(vm)` unchanged (its own Save button saves; the Welcome reads `vm.savedConfigIncomplete()` afterwards). **The wording of `wel_model_cost` and both addresses are written from the provider's own pages as they read on the day the wave is built (lookup L1); the English below is a placeholder of the same three facts and is replaced by the lead after L1** (SPLIT section 8, the plan's own rule). If L1 cannot be done, the sentence says only that the terms are one tap away and the link stays.

**Kindling step.** Status line by `ImageKind` (`wel_kindle_unpacking` with the percent, `wel_kindle_ready`, `wel_kindle_starting`, `wel_kindle_running`), `ui_home_hint_first` (existing key, reused: no new key for "about a minute, once"), Pêt's line (a cross-fade every `PET_LINE_MS`), the fire hint `wel_fire_hint`, then the cards. **Fire lesson (B15):** the fire in the scene is a target (tag `welcome.fire`, hit box from `FireGeometry`), tap = stoke, hold 700 ms = ring then puff; after both have been done once, the hint turns into `wel_fire_learned` and the lesson never shows again in this run; there is nothing to store: the Welcome itself never shows twice.

**Hello step.** *Provider saved:* a one-line field with `wel_hello_hint`, one chip `wel_hello_sugg`, Send (`ui_home_send`, existing key); sending creates a chat with `BotPresets.HERMES_ID` and calls `app.engine.send`; while it answers the card shows the typing indicator and Pêt reads; the answer appears as one bubble (the card shows the last bot message with the plain Markdown view `MarkdownView` the chat uses, read only); then `wel_hello_open` and `wel_finish`. *No provider:* `wel_tour_body`, `wel_tour_start`; after it the small transcript of 3.3 and the two captions; `wel_tour_skip` ends the tour (and the Welcome, as Finish).

### 3.2 WELCOME-CORE: files, symbols, exact signatures

All CORE types are JVM-loadable: no Android type in a constant or an initialiser, no `android.` call outside a function body that takes a `Context` (the rule of `PracticeModel`). Packages: `app.ui.onboarding` for `FirstRun.kt`, `WelcomeFlow.kt`, `KeyGuide.kt`, `GuidedChat.kt`; `app.runtime` for the two edits.

#### `J/ui/onboarding/FirstRun.kt`

```kotlin
enum class FirstRunStatus { NONE, STARTED, DONE, SKIPPED }
@Immutable data class FirstRunStored(val status: FirstRunStatus = FirstRunStatus.NONE, val step: Int = 1, val tourConvId: String = "")

/** The one stored string ("first_run" in the prefs "settings"): "" | "started:<1..5>" | "started:<1..5>;tour=<convId>" | "done" | "skipped". */
object FirstRunCodec {
    const val KEY = "first_run"
    fun decode(raw: String?): FirstRunStored      // anything unreadable gives NONE; step clamped to 1..5; tour kept only when it matches [A-Za-z0-9_-]{1,32}; an unknown ";x=..." part is ignored
    fun encode(s: FirstRunStored): String         // NONE gives ""; DONE "done"; SKIPPED "skipped"
}
enum class FirstRunDecision { SHOW, RESUME, NEVER }
enum class WelcomeMode { LIVE, DRY }

object FirstRunRules {
    /** The 1a rule, copied (pure): rootfs.stamp or chat/conversations.json exists. bots.json does NOT count (ChatRepository writes it on every fresh install). */
    fun filesSayExisting(filesDir: java.io.File): Boolean
    /** NONE and a file says existing: NEVER. NONE and no file: SHOW. STARTED: RESUME (even though the unpacking made the files). DONE or SKIPPED: NEVER. */
    fun decide(filesSayExisting: Boolean, stored: FirstRunStored): FirstRunDecision
    /** For the stores (the days store's migration, request SC-2): an existing installation is one that the Welcome did not start: stored NONE and the files say so. */
    fun isExistingInstall(filesSayExisting: Boolean, stored: FirstRunStored): Boolean = stored.status == FirstRunStatus.NONE && filesSayExisting
    /** Resolved once when the screen opens. LIVE only when the Welcome is the real first run (SHOW or RESUME) or the pin is LIVE on the twin; everything else is DRY. */
    fun mode(decision: FirstRunDecision, pin: FirstRunPin?, isTwin: Boolean): WelcomeMode
}

interface FirstRunStore { fun read(): String; fun write(raw: String) }     // production: Settings.firstRun; tests: a fake that counts writes

/** A debug pin (TEST_HOOKS builds only; memory only; cleared by reset=1). */
data class FirstRunPin(val kind: Kind, val step: Int = 1, val percent: Int? = null) {
    enum class Kind { RESET, LIVE, DONE }
}
object FirstRunDebug {
    /** "reset" | "reset:<1..5>" | "reset:<1..5>@<0..100>" | "live" | "live:<1..5>" | "done" | "off"; "off" parses to null AND clears; any other text returns null from parse and is rejected by the link. */
    fun parse(text: String): FirstRunPin?
    val pin: StateFlow<FirstRunPin?>
    fun pin(p: FirstRunPin?, testHooks: Boolean = BuildConfig.TEST_HOOKS)     // inert unless testHooks
    fun current(): FirstRunPin?
}

object FirstRun {
    /** Slice D (needs SHELL's Route.Welcome). Synchronous (one preferences read, two file checks), no write. DONE pin gives [Route.Home]. */
    fun initialRoutes(context: Context): List<Route>
    /** The testable core of the line above. */
    fun wantsWelcome(store: FirstRunStore, filesDir: java.io.File, pin: FirstRunPin?): Boolean
    fun stored(context: Context): FirstRunStored
    fun decision(context: Context): FirstRunDecision
    fun mode(context: Context): WelcomeMode                           // for WelcomeScreen
    fun isExistingInstall(context: Context): Boolean                  // the symbol INTEGRATE-B puts into SceneDaysStore's migration (SC-2)
    fun markStarted(context: Context, step: Int = 1)                  // LIVE only; writes "started:<step>" only when the stored status is NONE, otherwise leaves the stored value as it is
    fun saveStep(context: Context, step: Int)                         // keeps the tour id; no-op unless STARTED
    fun saveTour(context: Context, convId: String)                    // keeps the step; no-op unless STARTED
    fun markDone(context: Context); fun markSkipped(context: Context)
}
```
`FirstRun.initialRoutes`: pin `DONE` gives `[Route.Home]`; pin `RESET` or `LIVE` gives `[Route.Welcome]` (RESET is dry); otherwise `decide(...)`: SHOW or RESUME give `[Route.Welcome]`, NEVER gives `[Route.Home]`. The pin is read only when `BuildConfig.TEST_HOOKS`. The function writes nothing, never throws (a preferences or file error gives `[Route.Home]`), and costs one `SharedPreferences` read and two `File.exists` calls.

#### `J/ui/onboarding/WelcomeFlow.kt` (the state machine, pure)

```kotlin
enum class WelcomeStep(val number: Int) {
    LANGUAGE(1), MEET(2), MODEL(3), KINDLING(4), HELLO(5);
    companion object { const val COUNT = 5; fun of(number: Int): WelcomeStep }   // clamps to 1..5
}
enum class ImageKind { NOT_READY, UNPACKING, READY, STARTING, RUNNING, FAILED }
enum class WelcomeEffect { PREPARE_IMAGE, START_AGENT }
enum class NextLabel { NEXT, CONTINUE, SAVE_AND_CONTINUE, ADD_KEY_LATER, FINISH }
enum class FireKind { COLD, KINDLING, BLAZING, SMOKE }                 // the UI maps 1:1 to HearthState
@Immutable data class WelcomeFire(val kind: FireKind, val progress: Float?)
@Immutable data class WelcomeWorld(
    val image: ImageKind = ImageKind.NOT_READY, val unpack: Float = 0f, val failure: String = "",
    val haveProvider: Boolean = false, val practiceOn: Boolean = false, val online: Boolean = true,
    val tour: TourStage = TourStage.IDLE, val helloAnswered: Boolean = false, val dry: Boolean = false,
)
enum class WelcomeBanner { IMAGE_FAILED }
enum class Question { WEATHER, DAYS }
enum class FinishEffect { STOP_AGENT, PRACTICE_OFF, DELETE_TOUR_CHAT, MARK_DONE, MARK_SKIPPED }
@Immutable data class FinishPlan(val effects: List<FinishEffect>, val openChatId: String?)

object WelcomeFlow {
    const val PET_LINE_MS = 5200L; const val PET_LINES = 4; const val SKY_QUANT = 16
    fun next(s: WelcomeStep): WelcomeStep?; fun back(s: WelcomeStep): WelcomeStep?        // null at the ends
    fun segments(s: WelcomeStep): List<Boolean>                                           // five booleans: true up to and including s
    fun resume(stored: FirstRunStored): WelcomeStep                                       // STARTED gives of(step); anything else LANGUAGE
    fun nextLabel(s: WelcomeStep, w: WelcomeWorld, key: KeyCheck): NextLabel
    fun nextEnabled(s: WelcomeStep, w: WelcomeWorld, key: KeyCheck): Boolean
    fun fireFor(w: WelcomeWorld): WelcomeFire
    fun effects(step: WelcomeStep, w: WelcomeWorld, fired: Set<WelcomeEffect>): List<WelcomeEffect>
    fun banner(w: WelcomeWorld): WelcomeBanner?
    fun skyPosition(step: WelcomeStep): Float                                             // 0f, 1f, 2f, 3f, 4f
    fun quantizeSky(p: Float): Float                                                      // round(p * 16) / 16, coerced 0..4, NaN gives 0
    fun skyEpochMs(position: Float): Long                                                 // linear between SKY_EPOCHS_MS; see 3.7
    val SKY_EPOCHS_MS: LongArray                                                          // five instants of one neutral place; tuned in slice D by LOOK-B
    const val SKY_LAT_DEG = 0.0; const val SKY_LON_DEG = 0.0
    fun petLine(elapsedMs: Long, animate: Boolean, tapped: Int): Int                      // 0 until PET_LINES: (elapsed / PET_LINE_MS + tapped) mod 4; with animate false only tapped counts
    fun questions(step: WelcomeStep, weatherAvailable: Boolean, dayListIds: List<String>): List<Question>
    fun dryWorld(step: WelcomeStep, percent: Int?): WelcomeWorld
    fun finishPlan(skipped: Boolean, w: WelcomeWorld, tourConvId: String?, helloConvId: String?): FinishPlan
}
object FireLesson {                                                                       // B15: a pure little reducer
    @Immutable data class State(val stoked: Boolean = false, val held: Boolean = false) { val learned: Boolean get() = stoked && held }
    fun reduce(s: State, g: FireGestureKind): State
    enum class FireGestureKind { TAP, HOLD }                                              // mapped by the UI from 1a's FireGesture (CORE cannot import 1a in slice A)
}
```
Rules (each has a test, section 4):
- `fireFor`: `NOT_READY` COLD, progress 0; `UNPACKING` KINDLING with `progress = unpack * 0.9` (the rule of `HomeUi`, `HOME.md` 3.2); `READY` KINDLING 0.9 (embers: the image is ready, nothing is lit); `STARTING` KINDLING 0.95; `RUNNING` BLAZING, null; `FAILED` SMOKE, null.
- `effects`: `PREPARE_IMAGE` when `!w.dry && w.image == NOT_READY` and not in `fired`; `START_AGENT` when `!w.dry && w.image == READY && w.haveProvider && !w.practiceOn && step >= MODEL` and not in `fired`. Idempotent: an effect already in `fired` is never returned; nothing is returned in a dry world.
- `nextLabel`: LANGUAGE and MEET `NEXT`; MODEL: `SAVE_AND_CONTINUE` when `key == OK` or `SHORT`, `CONTINUE` when `w.haveProvider`, else `ADD_KEY_LATER`; KINDLING `CONTINUE`; HELLO `FINISH`.
- `nextEnabled`: LANGUAGE, MEET true; MODEL false when `key` is `SPACES`, else true; KINDLING true when `w.dry` or `w.image` is `READY`, `STARTING` or `RUNNING` (not while `NOT_READY`, `UNPACKING`, `FAILED`); HELLO true when `w.helloAnswered` or `w.tour == ANSWERED` or `w.tour == FAILED`, and in the tour also always true on the skip control (the UI shows `wel_tour_skip` as its own control).
- `banner`: `IMAGE_FAILED` when `w.image == FAILED` (shown on steps 2 to 5 as one quiet card, with Retry).
- `questions`: `[WEATHER]` only when `weatherAvailable`, `[DAYS]` only when `dayListIds.isNotEmpty()`, both only on `KINDLING`, weather first; otherwise empty.
- `dryWorld(step, percent)`: step 1 `NOT_READY` 0; 2 `UNPACKING` 0.15; 3 `UNPACKING` 0.35; 4 `UNPACKING` 0.6; 5 `RUNNING`; a `percent` (0 to 100) replaces the fraction of steps 1 to 4 and at 100 the image is `READY`; `dry = true`, `online = true`, `haveProvider = false`.
- `finishPlan`: effects in this order and only those that apply. Skipped: `MARK_SKIPPED`; if `w.practiceOn`: `STOP_AGENT, PRACTICE_OFF` first, `DELETE_TOUR_CHAT` when `tourConvId != null`. Finished: the same cleanup, then `MARK_DONE`. `STOP_AGENT` only when `w.practiceOn` (never stops a real agent, never while only unpacking). `openChatId = helloConvId` only when not skipped, `w.helloAnswered`, and the chat is not the tour. In a dry world `effects` is empty and `openChatId` null.

#### `J/ui/onboarding/KeyGuide.kt` (pure data)

```kotlin
enum class GuideStep(val number: Int, val sprite: String) {          // the picture names are ART-B's (request AB-1)
    OPEN_PAGE(1, "wel_key_1"), CREATE_KEY(2, "wel_key_2"), COPY_KEY(3, "wel_key_3")
}
enum class KeyCheck { EMPTY, SPACES, SHORT, OK }
object KeyGuide {
    const val KEY_PAGE_URL = "https://aistudio.google.com/apikey"      // PLACEHOLDER from memory: lookup L1 confirms or replaces it
    const val TERMS_URL = "https://ai.google.dev/gemini-api/terms"     // PLACEHOLDER from memory: lookup L1 confirms or replaces it
    val ALLOWED_HOSTS: Set<String> = setOf("aistudio.google.com", "ai.google.dev")
    const val MIN_PLAUSIBLE = 20
    /** The three facts of the one sentence (B13): what a free key may mean, said once. The UI picks wel_model_cost; the test keeps the three in step with the sentence's note. */
    enum class CostFact { MAY_IMPROVE_PRODUCTS, MAY_BE_READ, NOT_IN_EVERY_COUNTRY }
    val COST_FACTS: List<CostFact>
    fun clean(raw: String): String                                      // trims whitespace, a trailing newline, and one pair of surrounding quotes
    fun check(raw: String): KeyCheck                                    // EMPTY (blank), SPACES (white space inside the cleaned text), SHORT (< MIN_PLAUSIBLE), OK
    fun isSafeUrl(url: String): Boolean                                 // https, host in ALLOWED_HOSTS, no user info, no port, no query, no fragment
}
```
The two addresses are constants a person edits in one place; nothing of the typed key is ever built into a URL (privacy: no personal data in URL parameters).

#### `J/ui/onboarding/GuidedChat.kt` (pure script and stage)

```kotlin
enum class TourStage { IDLE, WAKING, ASKED, TOOL, ANSWERED, FAILED }
enum class TourAction { NONE, ENABLE_PRACTICE, START_AGENT, SEND_QUESTION }
enum class TourLineKind { USER, TOOL, ANSWER }
@Immutable data class TourLine(val kind: TourLineKind, val text: String, val done: Boolean)

object GuidedChat {
    const val KEYWORD = "RUN_TOOL"                       // in PracticeModel.KEYWORDS; the one turn of the script
    const val WAKE_TIMEOUT_MS = 120_000L; const val ANSWER_TIMEOUT_MS = 90_000L
    /** "<shown question> RUN_TOOL": the keyword is the last whole word, never followed by anything (the model reads the word after it). */
    fun sendText(shownQuestion: String): String
    /** The inverse for display: drops a trailing KEYWORD token (any word of PracticeModel.KEYWORDS), trims. Text without one is returned unchanged. */
    fun displayText(raw: String): String
    fun stageOf(requested: Boolean, image: ImageKind, practiceOn: Boolean, messages: List<Message>, busy: Boolean, timedOut: Boolean): TourStage
    fun nextAction(requested: Boolean, practiceOn: Boolean, image: ImageKind, convExists: Boolean): TourAction
    fun lines(messages: List<Message>): List<TourLine>        // user question, tool step(s), the answer; a streaming answer has done = false
}
```
- `stageOf`: `!requested` IDLE; `timedOut` or an `Role.ERROR` message or `image == FAILED` FAILED; no user message yet and the agent not `RUNNING` WAKING; a user message and no tool message and no finished bot text: ASKED; a `Role.TOOL` message whose `toolStatus` is `running` or the turn still `busy` after one: TOOL; a `Role.BOT` message with text and `!streaming` and `!busy`: ANSWERED.
- `nextAction`: `!requested` NONE; `!practiceOn` ENABLE_PRACTICE; `image == READY` (stopped) START_AGENT; `image == RUNNING && !convExists` SEND_QUESTION; else NONE. (`convExists` covers "already sent": one send per tour.)
- `lines`: uses `displayText` for the user text; the tool line text is `Message.text` (the engine's label, for example the command), `done = (toolStatus == "completed")`.

#### `J/runtime/Settings.kt`

One property, after `reasoningEffort` (`Settings.kt:64-66`), nothing else:

```kotlin
/** The Welcome's one stored flag, see FirstRunCodec ("" = never started). Written only by a live Welcome. */
var firstRun: String
    get() = sp.getString("first_run", "") ?: ""
    set(v) = sp.edit().putString("first_run", v).apply()
```

#### `J/runtime/RuntimeManager.kt`: `prepareRootfs()`

```kotlin
/**
 * Unpacks the Linux image when it is missing or from another build, and stops there: no provider is needed, Hermes is not
 * started. Same state flow as start() (Extracting(fraction), then Stopped("ready")). Returns at once; the work runs on the
 * runtime's own thread under the same mutex as start(), so a start() that comes meanwhile waits and then finds the image
 * ready. A no-op while Hermes runs or starts. Safe to call twice. Never cancelled by anything but stop(), which the Welcome never calls.
 */
fun prepareRootfs()
```
Body (the contract; one function, placed after `restart()` at about `RuntimeManager.kt:230`):

```kotlin
fun prepareRootfs() {
    beginOp()
    scope.launch {
        try {
            mutex.withLock {
                if (_state.value.isBusyOrRunning && processAlive()) return@withLock
                ensureServiceStarted()
                val token = holdWake("prepare")
                try {
                    cancelExtract = false
                    if (ensureRootfs()) {
                        val s = _state.value
                        if (s is RuntimeState.Extracting || s == RuntimeState.NotInstalled) setState(RuntimeState.Stopped("ready"))
                    }
                } catch (e: Exception) {
                    AppLog.e("Runtime", "prepare failed", e)
                    setState(RuntimeState.Error("${e.javaClass.simpleName}: ${e.message}"))
                } finally { releaseWake(token, "prepare done") }
            }
        } finally { endOp() }
    }
}
```
It touches no other member, and `ensureRootfs()` is not changed. `setState(Stopped("ready"))` is guarded so it never overwrites `Starting` or `Running` set by a `start()` that ran after the lock was released. The wake token lasts as long as `start()`'s (25 minutes cap, released in `finally`).

### 3.3 WELCOME-UI: files, symbols, behaviour

All composables are `internal` unless named otherwise; text only through `t()` and `tn()`; tokens `Space`, `Corner`, `Dur`, `Loop`, `Flourish`, `Pocket.motion`, `HapticCue`, `PetNotes`, `PocketSheet`, `ConfirmDialog` (w0 1.1); no `.uppercase()`, `.lowercase()`, `delay(<digits>)`, `durationMillis = <digits>` or `N.dp` literal in `ui/` (the lint ratchet counts them: use named Float constants with `.dp` applied).

#### `WelcomeScreen.kt`

```kotlin
@Composable fun WelcomeScreen()                                   // the symbol SHELL calls (registry)
@Stable internal class WelcomeModel(/* context, app, vm, mode */) // state holder; created by remember { } in WelcomeScreen
```
`WelcomeScreen()` does, in this order, **before** composing the scene (the scene's host may read the days store; the stored `started` must exist first):
1. `val ctx = LocalContext.current; val app = remember { PocketApp.from(ctx) }; val vm: MainViewModel = viewModel()`; `val mode = remember { FirstRun.mode(ctx) }` (`LIVE` or `DRY`).
2. (`initialStep` is worked out first, see 3.) `if (mode == LIVE) remember { FirstRun.markStarted(ctx, initialStep); WhatsNewStore.markSeenForNewUser(ctx) }` (D13, request ST-1). In a dry Welcome nothing is written.
3. `var step by rememberSaveable { mutableIntStateOf(initialStep) }` where `initialStep` = the pin's step in a dry preview, else `WelcomeFlow.resume(FirstRun.stored(ctx)).number`; every change of `step` in a live Welcome calls `FirstRun.saveStep(ctx, step)` from a `LaunchedEffect(step)`.
4. The world: `WelcomeWorld` built each time `runtime.state`, `vm.savedConfigIncomplete()`, `GuestPrefs.practiceModel`, the connectivity and the tour stage change (`derivedStateOf`; a dry Welcome uses `WelcomeFlow.dryWorld(step, pin?.percent)` instead and ignores the real runtime).
5. `LaunchedEffect(world.image, step, world.haveProvider)`: for each effect returned by `WelcomeFlow.effects(...)`: `PREPARE_IMAGE` calls `app.runtime.prepareRootfs()`; `START_AGENT` calls `vm.start()`; each is added to a `rememberSaveable` set `fired` before the call. (A restored process finds the set again and does not repeat `START_AGENT` a second time; `PREPARE_IMAGE` repeating is harmless because `prepareRootfs` is idempotent, so the set is not needed for it but is kept for symmetry.)
6. `BackHandler(enabled = step > 1) { step = Welcome back }` (deeper than the shell's own handler, so it wins; on step 1 nothing handles it and the shell's handler is disabled for this route: request SH-3, so back leaves the app).
7. Layout: a `Box(Modifier.fillMaxSize().background(c.bgDeep))` holding `WelcomeScene(...)` (aligned top, fixed height) and a `Column` with the scrolling card and the pinned bottom controls (`WelcomeChrome`, 3.3). The root of the card has the tag `welcome.step.<n>`; `semantics { paneTitle = t(wel_cd_screen) }`.
8. Leaving: `finish(skipped: Boolean)` builds `WelcomeFlow.finishPlan(...)` and runs its effects in order on the IO dispatcher then calls the navigator (below). `Finish` and `Skip` are the only two ways out; the system back on step 1 leaves the app and keeps `started`.

**Effects of `FinishEffect`** (live only; each in a `try` so one failure never blocks the rest): `STOP_AGENT` = `vm.stop()` (marks the stop for Presence); `PRACTICE_OFF` = `GuestPrefs.get(ctx).setPracticeModel(false)` then `app.runtime.onGuestOptionsChanged(false)` (rewrites the guest files for the real setup, no restart); `DELETE_TOUR_CHAT` = `app.chat.deleteConversation(tourId)` (id from the model, or the stored `tourConvId` after a restored process); `MARK_DONE` / `MARK_SKIPPED` = `FirstRun.markDone/markSkipped(ctx)`. Navigation after the effects: `nav.reset(listOf(Route.Home))` or `nav.reset(listOf(Route.Home, Route.Chat(openChatId)))`. In a dry Welcome the same function skips every effect and only navigates.

**Skip.** `welcome.skip` calls `finish(true)` at once, except when `vm.apiKeyInput.isNotBlank() && !savedThisRun` (a typed key that is not saved): then `ConfirmDialog(title = wel_skip_title, body = wel_skip_body, confirmLabel = wel_skip_leave, dismissLabel = wel_skip_stay, danger = false)`. Skip during unpacking never stops it.

**Key save** (step 3, "Save and continue"): `vm.selectMode(API_KEY); vm.selectEndpoint(STUDIO)` once at the start of the step if no provider is saved and `vm.authMode` is a Google mode; on the primary button with `KeyCheck.OK` or `SHORT`: `vm.apiKeyInput = KeyGuide.clean(raw)`, `val problems = vm.saveSettings()`; no problems: `PetNotes.show(t(wel_key_saved), PetNoteKind.SUCCESS)`, `savedThisRun = true`, go to step 4; problems: show the first one with the existing `problem_*` texts (the same `when` as `ProviderSection`), the field shakes (pattern 16, `HapticCue.DOUBLE`) and nothing is lost. `saveSettings()` restarts a running gateway: at this point it is not running (an effect only starts it afterwards), and a restart is the right thing when it is. A dry Welcome never calls it: the primary button only advances. In a dry Welcome the tour's start button is inert unless X2 (the canned replay) is built; the card then simply shows the pre-tour text, which is what the picture of step 5 needs.

**The guided chat (tour) controller** (in `WelcomeScreen.kt`'s model): `requested` becomes true on `welcome.tour.start`. A `LaunchedEffect(requested, world.image, practiceOn, convId)` runs `GuidedChat.nextAction(...)`: `ENABLE_PRACTICE` = `GuestPrefs.get(ctx).setPracticeModel(true)` then `app.runtime.onGuestOptionsChanged(false)` (the agent is stopped: this only rewrites the files) ; `START_AGENT` = `vm.start()`; `SEND_QUESTION` = create the conversation (`app.chat.createConversation(listOf(BotPresets.HERMES_ID))`), `FirstRun.saveTour(ctx, conv.id)`, then `app.engine.send(conv.id, GuidedChat.sendText(t(wel_tour_question)))`; if `send` returns false the stage is FAILED. A timeout (`WAKE_TIMEOUT_MS` from the request until `RUNNING`, `ANSWER_TIMEOUT_MS` from the send until an answer) sets `timedOut`. `Retry` (`welcome.retry`) clears the failure: deletes the half-made conversation, resets `requested`, and the flow runs again.

#### `WelcomeSteps.kt`

`StepLanguage`, `StepMeet`, `StepModel`, `StepKindling`, `StepHello` and small private parts (`FireLessonLine`, `OptionalCard`, `ImageErrorCard`). Layout rules: a title in `PixelText(pixel = 2.dp)` (drops to `Type.title` when it does not fit: DL 2.2), body in `Type.body`, muted lines in `Type.label` with `Pocket.colors.textMuted`; vertical rhythm `Space.m`; the card is `PixelPanel(variant = PanelVariant.RAISED, corner = Corner.sheet)` with `Space.gutter`; every control at least `Space.touch` high. `StepLanguage` calls `LanguageChoices(selected = Pocket.appearance.language, onPick = ::pickLanguage, systemFirst = true, tagOf = WelcomeTags::lang)` (request LA-1). `StepModel` content is in 3.1. `StepKindling` shows the questions that `WelcomeFlow.questions(...)` returns:
- *Weather card:* title `wel_q_weather_title`, body `wel_q_weather_body`, two buttons `wel_q_weather_yes` (calls `WeatherStore.get(ctx).setEnabled(true)`; live only) and `wel_q_weather_no`; once answered the card collapses to one muted line. Never composed while `!WeatherStore.available`.
- *Days card:* title `wel_q_days_title`, body `wel_q_days_body`, one `PocketSwitchRow` per `SceneCalendar.LISTS` entry with `group == DayGroup.CULTURE` and `DayListTexts.title(id) != 0` (title `DayListTexts.title`, subtitle `DayListTexts.sub`), tag `welcome.day.<id>`, `SceneDaysStore.get(ctx).setList(id, on)` (live only; in a dry Welcome the switches work on a local `mutableStateMapOf` and store nothing); a chip `wel_q_days_none` is the default state and does nothing.
`StepHello` has two forms (provider saved, or the tour; 3.1) and the error form of 3.5.

#### `WelcomeScene.kt`

```kotlin
@Composable internal fun WelcomeScene(step: WelcomeStep, world: WelcomeWorld, lesson: FireLesson.State, celebrate: Boolean,
                                       onFireGesture: (FireLesson.FireGestureKind) -> Unit, modifier: Modifier = Modifier)
@Composable internal fun rememberWelcomeSky(step: WelcomeStep, quality: ...): State<SkyFrame?>
internal object WelcomeSceneMetrics { const val HERO_FRACTION = 0.40f; const val HERO_MIN_DP = 240f; const val HERO_MAX_DP = 360f; const val BAND_DP = 56f; const val OVERLAP_DP = 28f }
```
- **Height** = `(LocalConfiguration.current.screenHeightDp * HERO_FRACTION).coerceIn(HERO_MIN_DP, HERO_MAX_DP)` dp, fixed per orientation (never the container, so the keyboard cannot resize and reallocate the stage; the card scrolls instead).
- **Call:** `HearthScene(state = fire.toHearthState(), modifier = Modifier.fillMaxWidth().height(hero), progress = fire.progress, mood = null, stokeBoost = stoke(), celebration = celebrate, bottomBand = BAND_DP.dp, fireHold = { holdFraction }, onFireTransition = { /* haptics */ }, pet = pet, skyOverride = sky.value)`, with `pet = rememberPetController()`; `SceneSounds(hearth, rainLevel = 0)` once (WAKE when the fire catches).
- **Sky.** `rememberWelcomeSky(step)` returns the frame for the animated position `p` (an `Animatable<Float>` moved to `WelcomeFlow.skyPosition(step)` with `Pocket.motion.scene()`, level OFF snaps); `q = WelcomeFlow.quantizeSky(p)` is read in composition and the frame is `remember(q, palette, accent, variant, blackNight) { SkyFrames.build(SkyInputs(epochMs = WelcomeFlow.skyEpochMs(q), latDeg = SKY_LAT_DEG, lonDeg = SKY_LON_DEG, zone = ZoneId.of("UTC"), mode = SkyMode.FOLLOW, night = hearthPalette(colors), accent = ..., variant = Pocket.appearance.theme.name, blackNight = Pocket.appearance.blackNight, lists = emptySet(), userDays = emptyList(), firstLaunchEpochDay = null, seasons = SeasonSetting.OFF)) }`: at most 17 builds over a transition, none at rest; no real clock, place, season or day enters (the Welcome looks the same for everybody). `SkyInputs` is read from 1a's contract 3.1 (`SCENE.md` 3.1) and the build must follow its final form.
- **Fire target.** A `Box` over the scene with `Modifier.testTag(WelcomeTags.FIRE)` at the hit rectangle of `FireGeometry(SceneGeometry.of(...)).hitRect(StageGrid.forScene(widthPx, heightPx), minPx = 48.dp.toPx())` converted to dp (the way `HomeHero.kt` does it; UI reads that file after 1a is merged and does not invent a second geometry). The gesture: `FireSwitch.classify(heldMs, moved)` with `FireSwitch.movedBeyond` and the same loop `HomeHero` has (touch slop, consumed change cancels); a TAP posts `HapticCue.STOKE`, `SoundCue.SPARK`, `stokeBoost` 1f decaying over `Flourish.STOKE`, `onFireGesture(TAP)`, a Pêt note `wel_fire_stoked` the first time; a HOLD shows the ring through `fireHold = FireRing.fraction(heldMs)` and at the end `HapticCue.HOLD`, a puff (`celebration` is not used), `onFireGesture(HOLD)` and the note `wel_fire_held`. Nothing calls the runtime (D7). The node has the spoken label `wel_fire_cd` and a custom action `wel_fire_act_hold` ("Practice putting the fire out") so a screen reader can do both gestures.
- **Animation level.** `Pocket.motion.sceneFps` 30 at FULL, 10 at REDUCED, still at OFF (the scene is `HearthScene`'s own). **Fallback if the unpacking makes the scene janky (measurement M1, section 5):** wrap only the `HearthScene` call in `CompositionLocalProvider(LocalMotion provides MotionScheme(AnimLevel.REDUCED))` while `world.image == UNPACKING` (10 frames per second, no particles, no shaders); no new parameter.
- **Splash (X5).** Once the scene knows the fire's window position the scene calls `FireAnchor.publish(xPx, yPx, sizePx)` (START's object) from `onGloballyPositioned` of the fire target; cut with the handoff.

#### `WelcomeChrome.kt`

```kotlin
object WelcomeChrome { val iconsDark: StateFlow<Boolean>; internal fun setIconsDark(on: Boolean) }   // the symbol SHELL reads (registry)
@Composable internal fun WelcomeProgress(step: WelcomeStep, modifier: Modifier = Modifier)         // five segments, ember on the current one; tag welcome.progress
@Composable internal fun WelcomeControls(step: WelcomeStep, nextLabel: NextLabel, nextEnabled: Boolean, onBack: () -> Unit, onSkip: () -> Unit, onNext: () -> Unit)
```
`iconsDark` is the `SkyFrame.iconsDark` of the sky now drawn (luma of `skyTop` at least 0.40, 1a registry 3.2); `WelcomeScene` writes it from a `LaunchedEffect` when the quantised sky changes and sets it to false in `onDispose`. `WelcomeControls` is one `Row` of at most three controls: `welcome.back` (`ButtonKind.GHOST`, disabled on step 1, spoken label `wel_back`), `welcome.skip` (ghost, `wel_skip`, spoken label `wel_skip_cd`), `welcome.next` (primary; on step 5 `welcome.finish`). All three at least 48 dp; the Row mirrors in right-to-left; the progress line mirrors too (DL 13.3), the scene does not. The progress segments are drawn in `u` (`Pocket` pixel unit) in `Pocket.colors.outline` and the current one in `fireMid` with a `fireHigh` top pixel; the spoken label is `wel_progress_cd` ("Step 2 of 5: Meet Pêt").

#### `KeyGuideUi.kt`, `GuidedChatUi.kt`, `WelcomeTags.kt`

```kotlin
@Composable internal fun KeyGuideRows(modifier: Modifier = Modifier)        // the three rows: PixelImage(step.sprite, frame = 0, playing = false) at 2u per pixel (48 dp), the sentence; a missing sprite draws the digit instead
@Composable internal fun GuidedChatView(lines: List<TourLine>, stage: TourStage, modifier: Modifier = Modifier)   // the small transcript
object WelcomeTags { /* 3.8 */ }
```
`GuidedChatView` draws the lines of `GuidedChat.lines(...)`: the user bubble (the shown question), a tool row (`PixelIconView(PixelIcon.TERMINAL)`, an existing icon; the command text in `Type.mono`, an ember spinner while `done == false` and a check when done: the same three-frame check the chat uses; the lines slide in from 8 dp below in `Dur.FAST`, pattern 13), the answer bubble (read only, `MarkdownView`), then the two captions `wel_tour_caption_tool` and `wel_tour_caption_answer` in `Type.label` once their line exists. While waking: `wel_tour_waking` and the fire catching in the scene above (pattern 18: no spinner on an empty screen; the three lines appear as `Skeleton` rows of the right shape until each arrives). Tags `welcome.tour.msg.<i>` (0 user, 1 tool, 2 answer).

### 3.4 State and stored data

| Item | Where | Written when | Read by the previous build? |
|---|---|---|---|
| `first_run` (string) | prefs `settings` (existing file), new key | live Welcome: `started:<n>` at its first composition and on every step change, `started:5;tour=<id>` when the tour chat is made, `done` or `skipped` at the end | no: `Settings` never reads it; an unknown key in a preferences file is ignored |
| `language` | prefs `appearance` (existing key) | live Welcome, step 1 | yes (the same field the Settings picker writes) |
| the provider setup | existing stores (`Settings`, `SecretStore`) through `MainViewModel.saveSettings()` | live Welcome, step 3 | yes |
| practice flag | prefs `guest` (existing) | live tour; off at the end | yes |
| one conversation | `files/chat/` (existing format) | live tour, deleted at the end | yes |
| `lists` of the days store | 1a's prefs `scene_days` | live Welcome, the days card | n/a (1a's) |
| START's "seen" marker | START's prefs `whatsnew` | live Welcome, first composition | n/a |
| the stored step and all UI state | `rememberSaveable` in the Welcome only | memory (survives rotation and a restored process through the Navigator's saver and `first_run`) | |

**Migration for an installation that already exists: none, and no write.** On the owner's phone (and on any phone with `files/rootfs.stamp` or `files/chat/conversations.json` and no stored `first_run`) `FirstRun.initialRoutes` returns `[Route.Home]` and no line of this draft runs. `shared_prefs/settings.xml` gets no `first_run` entry (proof 3 of section 5). A rollback to the Wave 0 or the 1a build changes nothing on disk and shows no Welcome. **After a restore from a backup** the phone counts as existing (the `settings` file is not in the backup schema, the chat files are), so no Welcome follows a restore. **After "Reset Linux image"** the stored `done` keeps the Welcome away.

**Why `started` exists (D3).** Without it, a user who leaves the app in step 3 (very likely: they go to the browser to fetch a key and the process may be killed) would find `rootfs.stamp` on the next start, be taken for an existing installation, and never see the rest of the Welcome. A `started` value is only ever written by a live Welcome, which exists only for a phone that had neither file.

### 3.5 The five states of the screen (DL 8.16, kinds Stage and Flow)

| State | Step 1 Language | Step 2 Meet | Step 3 Model | Step 4 Kindling | Step 5 Hello |
|---|---|---|---|---|---|
| **Loading** | the list is static: not applicable. The scene draws nothing but its sky colour until the sprites are ready (`rememberSpritesReady`, a moment), the card is already usable | same | saving a key: the primary button shows its pressed state for the one frame `saveSettings` takes; no spinner | the unpacking itself: the fire catching and the percent (`wel_kindle_unpacking`); pattern 18 is the scene, not a block | waking the practice model or waiting for the answer: the fire catching, `wel_tour_waking`, skeleton rows of the three lines, the typing indicator |
| **Empty** | not applicable (the list always has rows) | not applicable | an empty key field is the normal state: the primary button reads `wel_key_later` and works | not applicable | the empty field shows its hint `wel_hello_hint` and the chip; the tour before `Start` shows `wel_tour_body` |
| **Content** | rows, one selected | the two paragraphs | guide, field, sentence, links | percent, Pêt's line, fire hint, cards | a bubble and the answer; Finish |
| **Error** | not applicable (a language switch cannot fail) | `ImageErrorCard` if unpacking failed (it can fail from step 1) | a blocked key (`SPACES`): `wel_key_err_spaces` under the field, the field shakes; a refused save: the first `problem_*` text; the `SHORT` hint `wel_key_hint_short` | `ImageErrorCard`: `wel_err_unpack` + the first line of `RuntimeState.Error.reason` (which says what happened, for example `eng_rt_no_space`), `wel_err_unpack_kept` ("Nothing else was changed."), and `welcome.retry` = `prepareRootfs()` again | the tour failed or timed out: `wel_tour_fail` + `welcome.retry` + `wel_tour_skip`; a real hello that the engine refused or that came back as a `Role.ERROR` message: that message's text, Retry (sends again), and "open the chat" |
| **Offline** | not applicable (no network used) | not applicable | `wel_offline_note`: one quiet line under the key button (the key page needs a connection; the practice chat does not); nothing is greyed out | not applicable (the image is in the APK) | the tour works offline (local); a real hello offline gets the engine's error text and the offline note |
| **Needs permission** | not applicable: the Welcome asks for none (D14); stated once here for all five steps | | | | |

The `ImageErrorCard` also appears on steps 2 and 3 when the unpacking fails early; Back and Next keep working, Skip leaves.

### 3.6 Motion, haptics, sound (by token name)

| What | Token or pattern |
|---|---|
| Step change | content cross-fades with an 8 dp slide: incoming `fadeIn(motion.base())` plus `slideInVertically(motion.base()) { Space.xl }`, outgoing `fadeOut(motion.fast())` (pattern 7; `Ease.enter` through `base()`); OFF: no animation |
| The sky between steps | `motion.scene()` (`Dur.SCENE`, 480 ms) on one `Animatable`, rebuilt at 16 quantised positions (3.3); it is the one hero animation (a long flourish allowed for "the first launch", DL 2.4) |
| Progress segments | the ember moves with `motion.pop()` (three steps); OFF snaps |
| Pêt | one wave at step 2 (`PetEvent.Tap(2)`); a `PetEvent.Forged` (HAPPY) on the first answer; otherwise `PetBrain` alone |
| Pêt's line | cross-fade `motion.base()` every `WelcomeFlow.PET_LINE_MS` (X4; otherwise static, tap for the next) |
| Key error | pattern 16: two shakes of 2u in 180 ms, `HapticCue.DOUBLE` |
| Fire tap | `HapticCue.STOKE`, `SoundCue.SPARK`, `stokeBoost` over `Flourish.STOKE` |
| Fire hold | the 20-dot ring from `fireHold` (FIRE draws it); at the end `HapticCue.HOLD` |
| Language chip, key chip | `HapticCue.TICK` (inside `PocketChip`) |
| Buttons | `HapticCue.CLICK` and the press of pattern 1 (inside `PixelButton`) |
| Send (hello) | `HapticCue.SEND` |
| The first answer | `HapticCue.RISE`; X3: `celebration = true` for `Flourish.SHOWER` (pattern 15) |
| The fire catches | `SoundCue.WAKE` through `SceneSounds` (1a, on the hearth change to BLAZING) |
| Tour lines | slide in from 8 dp below in `Dur.FAST` (pattern 13); the check in three frames |
| Reduced / off | `Pocket.motion`: fades up to 150 ms and a 10 fps scene at REDUCED; everything snaps at OFF |

No animation blocks input; every transition can be interrupted by the next tap.

### 3.7 `HearthScene.kt`: the one additive parameter (UI's edit; the file is 1a's)

After 1a is merged UI reads the final file and applies exactly this and nothing else:
1. Append as the **last** parameter `skyOverride: SkyFrame? = null` (after `pet: PetController? = null`; today the signature is `HearthScene.kt:66-73`, 1a's final form is registry 3.8). Home and every existing caller pass nothing and are unchanged.
2. Where the host takes the sky frame (1a: `rememberSkyFrame()`), write `val frameState: State<SkyFrame?> = if (skyOverride != null) rememberUpdatedState(skyOverride) else rememberSkyFrame()` and use `frameState` in place of that value everywhere the host used it (the renderer's `sky`, `pageColor`, `PetWorld.sunAltitudeDeg`).
3. When `skyOverride != null` the host must not publish anything into `HomeSky` or `HearthInfo` (Home owns them; the Welcome has `WelcomeChrome`), and must not start the minute loop of `rememberSkyFrame` (the `if` above already avoids it). `pageColor` follows the frame as for FOLLOW (the dithered horizon into `Pocket.colors.bg`).
4. Nothing else: not the geometry, not the layers, not the classic path (`skyOverride == null` and `sky == null` stay today's night, parity test unchanged).

Documented in the file's KDoc: "skyOverride: a fixed sky for a screen that is not Home (the Welcome); null = the real sky."

### 3.8 Test tags, debug link

**Tags** (`WelcomeTags`, lower case, dot separated, one per node; `ScenarioFilesTest` known set by INTEGRATE-B):
`welcome.step.1` to `welcome.step.5` (the card root of each step), `welcome.progress`, `welcome.status` (the status line of step 4), `welcome.next`, `welcome.back`, `welcome.skip`, `welcome.finish`, `welcome.lang.<code>` (`system` for the stored `""`; the LANG codes otherwise: `en es fr de pt it tr ru id ku`), `welcome.key.open`, `welcome.key.field`, `welcome.key.paste` (X1), `welcome.key.terms`, `welcome.key.other`, `welcome.fire`, `welcome.pet.line`, `welcome.weather.yes`, `welcome.weather.no`, `welcome.day.<id>`, `welcome.hello.input`, `welcome.hello.send`, `welcome.hello.open`, `welcome.tour.start`, `welcome.tour.skip`, `welcome.tour.msg.<i>`, `welcome.error`, `welcome.retry`. The primary button of every step carries `welcome.next` whatever it reads (on step 3 "Save and continue"), so there is no `welcome.key.save`; on step 5 it carries `welcome.finish` instead (one tag per node).

**Debug link** (extra owned by SHELL's `DebugLinks.kt`, grammar owned here): extra name `firstrun`, value forms of `FirstRunDebug.parse` (3.2): `reset`, `reset:<1..5>`, `reset:<1..5>@<0..100>` (a dry Welcome at that step with that unpack percent), `live`, `live:<1..5>` (twin only; on any other build it behaves as `reset`), `done` (the initial stack is Home), `off` (clears). `reset=1` clears it (`FirstRunDebug.pin(null)`). The pin is applied after `wood` (1a 3.9 order) and before the route, so `--es route welcome --es firstrun reset:4@45` shows the fire 45 percent unpacked. A bad value goes to `rejected` and nothing is applied. `route welcome` without a pin on an existing installation is dry by rule (`FirstRunRules.mode`), so the debug link can never turn a real agent off or write to the owner's phone.

### 3.9 Texts: `R/values/strings_welcome.xml` (prefix `wel_`; English; every key carries `<!-- t: ... | kind: ... -->`)

Kinds and rooms (English characters): tab 10, button 22, chip 18, title 28, row 40, pet 80; a placeholder counts 2. Sentence case, no exclamation marks except Pêt's (none here), no idioms. "Pêt", "Agirî", "Hermes", "Google AI Studio" are names and stay as written. Keys reused from other files: `ui_home_hint_first`, `ui_home_send` (both `strings_chat.xml`). Nothing of this file is read by another area except as the registry says (SHELL needs none).

| Key | English | Note (`t:` ... `kind:`) |
|---|---|---|
| `wel_cd_screen` | Welcome | t: spoken name of the screen (pane title), not drawn \| kind: body |
| `wel_progress_cd` | Step %1$d of %2$d: %3$s | t: spoken label of the progress line; %1$d current step, %2$d number of steps, %3$s the step's name (wel_step_name_*) \| kind: body |
| `wel_step_name_1`..`_5` | Language / Meet Pêt / Model / The fire / First hello | t: names of the five steps, only spoken \| kind: chip |
| `wel_next` | Next | t: primary button of steps 1 and 2 \| kind: button |
| `wel_continue` | Continue | t: primary button of step 4, and of step 3 when a key is already saved \| kind: button |
| `wel_back` | Back | t: spoken label and text of the back control \| kind: button |
| `wel_skip` | Skip | t: leaves the whole welcome; always there \| kind: button |
| `wel_skip_cd` | Skip the welcome | t: spoken label of the skip control \| kind: body |
| `wel_finish` | Finish | t: last step, after an answer \| kind: button |
| `wel_retry` | Try again | t: retry of an error card or of the practice chat \| kind: button |
| `wel_skip_title` | Leave without saving? | t: title of the one confirmation (only when a typed key is unsaved); up to 24 characters \| kind: title |
| `wel_skip_body` | The key you typed has not been saved. You can add it later in Settings. | t: body of that dialog; two lines \| kind: body |
| `wel_skip_leave` | Leave | t: confirm button \| kind: button |
| `wel_skip_stay` | Stay | t: dismiss button \| kind: button |
| `wel_lang_title` | Choose your language | t: title of step 1 \| kind: title |
| `wel_lang_body` | You can change it later in Settings. | t: one muted line under the title \| kind: body |
| `wel_meet_title` | Meet Pêt | t: title of step 2; Pêt is the app's newt, a name \| kind: title |
| `wel_meet_body_1` | Agirî is an agent that lives on your phone. Only the language model it thinks with is far away. | t: first paragraph of step 2; says what the app is in two facts; two or three lines \| kind: body |
| `wel_meet_body_2` | Pêt keeps the fire. The fire shows what the agent is doing: asleep, waking, working. | t: second paragraph; teaches that the fire is the status; two lines \| kind: body |
| `wel_meet_name` | Agirî is a Kurdish word for fire. | t: one muted line about the app's name; keep it short and factual \| kind: body |
| `wel_model_title` | Connect a model | t: title of step 3; up to 26 characters \| kind: title |
| `wel_model_intro` | Hermes thinks with a language model that you choose. A free key from Google AI Studio is the quickest start. | t: one or two sentences above the guide \| kind: body |
| `wel_key_step_1` | Open the key page and sign in with a Google account. | t: guide sentence 1, next to the first picture; up to two lines \| kind: body |
| `wel_key_step_2` | Choose Create API key. | t: guide sentence 2; the button's name as the provider's page writes it (re-read on lookup L1) \| kind: body |
| `wel_key_step_3` | Copy the key, come back here and paste it below. | t: guide sentence 3 \| kind: body |
| `wel_key_open` | Open the key page | t: opens the provider's key page in the browser \| kind: button |
| `wel_key_field` | API key | t: label of the key field \| kind: chip |
| `wel_key_paste` | Paste | t: extra: pastes the clipboard into the key field \| kind: button |
| `wel_key_save` | Save and continue | t: primary button of step 3 when a key is typed \| kind: button |
| `wel_key_later` | Add a key later | t: primary button of step 3 when the field is empty and no key is saved \| kind: button |
| `wel_key_saved` | Key saved. | t: Pêt note after a successful save; up to 40 characters \| kind: pet |
| `wel_key_hint_short` | That looks short for a key. Check that you copied all of it. | t: hint under the field when the text is under 20 characters; does not block \| kind: body |
| `wel_key_err_spaces` | A key has no spaces. Remove any that came with the copy. | t: error under the field; says what is wrong and what to do \| kind: body |
| `wel_model_cost` | A free key costs nothing, but Google may use what you send to improve its products, and a person may read it. Free keys are not offered in every country. | t: THE one sentence of plan item 1.7: what the provider may do with what is sent on a free key, and that it is not offered everywhere; the English here is a placeholder of those three facts, replaced after lookup L1 from the provider's own terms; keep it one sentence, plain, no blame \| kind: body |
| `wel_terms_link` | Read the terms | t: opens the provider's own terms page \| kind: button |
| `wel_model_other` | I use something else | t: opens the full provider form (OpenRouter, Anthropic, OpenAI, a custom address, Google by file) \| kind: button |
| `wel_model_other_title` | Another provider | t: title of the sheet with the provider form \| kind: title |
| `wel_offline_note` | No connection right now. The key page needs one. You can add a key later, and the practice chat works without it. | t: quiet line under the key button when the phone is offline; says what still works \| kind: body |
| `wel_kindle_title` | Lighting the fire | t: title of step 4 \| kind: title |
| `wel_kindle_unpacking` | Unpacking the agent, %1$d%% | t: status while the Linux image is unpacked; %1$d percent 0 to 100 \| kind: body |
| `wel_kindle_ready` | The agent is unpacked and ready. | t: status when the image is ready and nothing is started \| kind: body |
| `wel_kindle_starting` | Waking Hermes | t: status while Hermes starts \| kind: body |
| `wel_kindle_running` | Hermes is awake. | t: status when Hermes runs \| kind: body |
| `wel_pet_line_1` | Hermes can run commands on this phone, and you see every step. | t: Pêt's line 1; Pêt speaks about the agent in a short, calm sentence; written afresh in each language from docs/i18n/pet-voice.md, not translated \| kind: pet |
| `wel_pet_line_2` | It remembers what matters. You can read it and change it. | t: Pêt's line 2 (memory) \| kind: pet |
| `wel_pet_line_3` | Give it a routine and it works while you sleep. | t: Pêt's line 3 (routines) \| kind: pet |
| `wel_pet_line_4` | Show it a photo or a file and it will read it. | t: Pêt's line 4 (attachments) \| kind: pet |
| `wel_fire_hint` | Try the fire: tap it to stoke it, hold it to put it out. Here nothing really stops. | t: the one-time lesson of the fire gesture; two lines \| kind: body |
| `wel_fire_learned` | That is all there is to the fire. | t: replaces the hint once both gestures were tried \| kind: body |
| `wel_fire_stoked` | Stoked. | t: Pêt note on the first tap; one word \| kind: pet |
| `wel_fire_held` | Held. On Home, that puts the fire out. | t: Pêt note on the first hold \| kind: pet |
| `wel_fire_cd` | The fire. Double tap to stoke it. | t: spoken label of the fire target \| kind: body |
| `wel_fire_act_hold` | Practice putting the fire out | t: spoken custom action of the fire target \| kind: body |
| `wel_q_weather_title` | Show the weather in the sky? | t: title of the optional weather card; up to 28 characters; composed only when the weather can be switched on \| kind: title |
| `wel_q_weather_body` | Rain, snow and fog can appear in the scene. About once an hour the app asks a weather service about your rough place, rounded to about ten kilometres. | t: what the weather switch does and sends; two or three lines \| kind: body |
| `wel_q_weather_yes` | Show it | t: switches the weather on \| kind: button |
| `wel_q_weather_no` | Not now | t: leaves it off \| kind: button |
| `wel_q_days_title` | Days for the sky | t: title of the optional days card \| kind: title |
| `wel_q_days_body` | The sky always knows solstices and full moons. You can add days of your own. None is on unless you switch it on. | t: body of the days card; says that nothing is on by default \| kind: body |
| `wel_q_days_none` | None for now | t: the default state of the days card \| kind: chip |
| `wel_err_unpack` | Could not unpack the agent. | t: first line of the error card; the reason follows in monospace \| kind: title |
| `wel_err_unpack_kept` | Nothing else was changed. You can try again. | t: second line: what was kept and what can be done \| kind: body |
| `wel_hello_title` | Say hello | t: title of step 5 when a key is saved \| kind: title |
| `wel_hello_body` | Hermes is awake. Write a first message. | t: body of step 5 with a key \| kind: body |
| `wel_hello_hint` | Write a message | t: placeholder of the first message field \| kind: chip |
| `wel_hello_sugg` | What can you do? | t: the one suggestion chip; it fills the field and does not send \| kind: chip |
| `wel_hello_open` | Open the chat | t: after the answer, opens the chat that was made \| kind: button |
| `wel_hello_waiting_cd` | Hermes is thinking | t: spoken label while the answer comes \| kind: body |
| `wel_tour_title` | Try it first | t: title of step 5 when no key is saved \| kind: title |
| `wel_tour_body` | No key yet? Watch a practice run. It costs nothing, needs no account, and the answers are scripted. | t: body of the tour card \| kind: body |
| `wel_tour_start` | Start a practice chat | t: starts the practice model and the chat \| kind: button |
| `wel_tour_skip` | Skip the tour | t: ends the tour and the welcome \| kind: button |
| `wel_tour_waking` | Waking the practice model. This takes a few seconds. | t: shown while the agent starts for the tour \| kind: body |
| `wel_tour_question` | What can you do on this phone? Run a small command so I can see it. | t: the question that is shown in the tour's first bubble; the practice keyword is added by the app and never shown \| kind: body |
| `wel_tour_caption_tool` | Hermes ran a command in its own small Linux. | t: caption under the tool line \| kind: body |
| `wel_tour_caption_answer` | Then it answered from the result. Real answers come from your model. | t: caption under the answer; says the answer is scripted \| kind: body |
| `wel_tour_done` | That was a practice run. Add a key in Settings to talk to a real model. | t: line under the finished tour \| kind: body |
| `wel_tour_fail` | The practice chat did not start. Nothing was changed. Try again, or skip the tour. | t: error of the tour: what happened, what was kept, what to do \| kind: body |
| `wel_scene_out` | The fire is out | t: spoken label of the scene while the fire is out \| kind: body |
| `wel_scene_catching` | The fire is catching | t: same, kindling \| kind: body |
| `wel_scene_lit` | The fire is lit | t: same, burning \| kind: body |
| `wel_scene_smoke` | The fire is smoking | t: same, after a failure \| kind: body |
| `wel_scene_cd` | The hearth. %1$s | t: spoken label of the whole scene; %1$s is one of the four lines above \| kind: body |

TRANSLATE (LANG stage 4, in all ten languages): every key; the four `wel_pet_line_*`, `wel_fire_stoked`, `wel_fire_held` and `wel_key_saved` go through the Pêt-lines pass (DL 13.5); `wel_model_cost` is translated only after lookup L1 has fixed the English, by a reader who checks it still says the three facts; `wel_meet_name` keeps the word "Agirî". `strings-check` must exit 0.

## 4. Unit tests (JUnit 4, JVM; nothing here reads the clock, the phone or the network; repo files are read with `Assume.assumeTrue(file.exists())`)

| Class | Tests: what each proves |
|---|---|
| `FirstRunRulesTest` | `freshInstallShowsWelcome` (NONE, no file: SHOW); `existingInstallNeverShows` (NONE with only `rootfs.stamp`, only `chat/conversations.json`, both: NEVER); `botsJsonAloneIsNotExisting` (a temp `files/chat/bots.json` only: SHOW); `doneAndSkippedNeverAgain` (DONE and SKIPPED, with and without files: NEVER); `startedResumesAfterTheUnpackMadeTheFiles` (STARTED with both files: RESUME); `decideWritesNothing` (a fake `FirstRunStore` counts writes: `wantsWelcome` on an existing installation, on a fresh one and with every pin makes zero writes); `isExistingInstallForTheStores` (NONE+files true; STARTED, DONE, SKIPPED with files false; NONE without files false); `equalsTheSceneCalendarRule` (`Assume` that `dev.pocketagent.app.ui.pixel.stage.SceneCalendar` loads; calls its `existingInstall(File)` by Java reflection on the `INSTANCE` and compares with `filesSayExisting` over the same four temp trees; skipped before 1a is merged); `modeTable` (`mode(decision, pin, isTwin)` over SHOW, RESUME and NEVER: SHOW and RESUME with no pin LIVE; NEVER with no pin DRY; pin RESET always DRY; pin LIVE with `isTwin` LIVE and without DRY); `codecRoundTrip` (all four statuses, step 1 to 5, a tour id); `codecGarbage` (empty, "x", "started:9" gives step 5, "started:0" gives 1, "started:a", a tour id with a slash is dropped, an unknown `;x=1` part ignored); `codecEncodeNone`; `pinParse` (`reset`, `reset:3`, `reset:4@45`, `live`, `live:2`, `done`, `off` gives null and clears, `reset:6`, `reset:3@101`, `RESET`, `reset:`, an empty text: each bad one null); `pinInertWithoutTestHooks` (`pin(p, testHooks = false)` leaves `current()` null) |
| `WelcomeFlowTest` | `fiveStepsInOrder` (`next` chain LANGUAGE to HELLO then null; `back` chain; `of` clamps 0, 6, -1); `segmentsAreWholeSteps` (five booleans, the first n true); `resumeFromStored` (STARTED:3 gives MODEL; NONE, DONE, SKIPPED give LANGUAGE); `fireForTable` (every `ImageKind`; `UNPACKING` 0.5 gives 0.45, 1.0 gives 0.9, the same as `HomeUi` for `Extracting`; `READY` 0.9; `STARTING` 0.95; `RUNNING` BLAZING; `FAILED` SMOKE); `nextLabelTable` and `nextEnabledTable` (every step x key check x image kind x tour stage cell of 3.2); `effectsPrepareOnce` (NOT_READY live returns `[PREPARE_IMAGE]`, a second call with it in `fired` returns empty); `effectsStartAgentNeedsAProvider` (READY + provider + step MODEL returns START_AGENT; no provider, practice on, step LANGUAGE or MEET, a dry world: none); `effectsNeverInDry`; `bannerOnFailure`; `skyPositionAndQuantize` (positions 0 to 4; `quantizeSky(2.03)` is 2.0, 2.04 is 2.0625, NaN is 0, 9 is 4); `petLineRotation` (index by elapsed time, wraps after 4, `animate = false` ignores elapsed, taps add); `questionsGate` (weather only while available and only on KINDLING; days only with ids; weather before days; none on other steps); `dryWorldTable` (steps 1 to 5 and the percent override, 100 gives READY); `finishPlanCleanup` (practice on: STOP_AGENT, PRACTICE_OFF, DELETE_TOUR_CHAT, MARK_DONE in that order; practice off and no tour: `[MARK_DONE]`; skipped: the same with MARK_SKIPPED; unpacking and skipped: `[MARK_SKIPPED]` only, never STOP_AGENT); `finishOpensTheHelloChat` (answered, real hello: `openChatId` set; tour id equal to the hello id: null; skipped: null; dry: effects empty and null); `fireLessonLearnsBoth` (TAP, HOLD, either order: `learned`; one only: not) |
| `KeyGuideTest` | `urlsAreSafe` (`isSafeUrl` true for the two constants; false for http, another host, a user name, a port, a query, a fragment, an empty text); `hostsAreTheAllowList` (the hosts of both constants are in `ALLOWED_HOSTS`, and the set has exactly two entries); `checkTable` (empty and blanks EMPTY; "ab cd" SPACES; 19 characters SHORT; 20 OK; surrounding quotes and a trailing newline are cleaned first); `cleanKeepsInnerText`; `threeGuideStepsInOrder` (numbers 1, 2, 3, sprite names `wel_key_1..3`); `costFactsAreThree`; `spritesExistInArt` (`Assume` that `A/pixel/illustrations_b.pxl` exists: it defines the three names) |
| `GuidedChatScriptTest` | `keywordIsInThePracticeList` (`GuidedChat.KEYWORD in PracticeModel.KEYWORDS`; the script has exactly one turn); `sendTextAppendsTheKeywordLast` (ends with ` RUN_TOOL`, the shown question is the prefix, the text after the keyword is empty, the word `big` never follows); `displayTextRoundTrips` (`displayText(sendText(q)) == q`; a text without a keyword is unchanged; a keyword in the middle is untouched; each of the 15 words is stripped when last); `stageTable` (the six stages from crafted message lists: nothing sent and the agent not running WAKING; a user message only ASKED; a running tool TOOL; a streaming answer still TOOL or ASKED; a finished answer ANSWERED; an `ERROR` message, `timedOut`, `image == FAILED` FAILED; `!requested` IDLE); `nextActionOrder` (ENABLE_PRACTICE, then START_AGENT, then SEND_QUESTION, then NONE, and never SEND twice: `convExists` true gives NONE); `linesUseDisplayText` (the user line has no keyword; the tool line `done` follows `toolStatus`; the order user, tool, answer) |
| `RuntimePrepareTest` (T/, package `app`) | built like `RuntimeManagerInitTest` (the same fake context): `validImageEndsStoppedReady` (a fake `rootfs/usr/bin` and a stamp equal to `"unknown"`: after `prepareRootfs()` the state becomes `Stopped("ready")`, no process, `foregroundNeeded` false within 3 s); `missingImageWithoutAssetEndsInErrorAndReleases` (no rootfs, no asset: ends in `Error`, no exception, `foregroundNeeded` false); `neverOverwritesRunning` is covered by code review (a `Running` state cannot be set from a JVM test) |
| `WelcomeTagsTest` | every constant and every generated tag matches `^welcome\.[a-z0-9]+(\.[a-z0-9]+)*$`; all distinct; `lang("")` is `welcome.lang.system`; there is no `welcome.key.save` |
| `WelcomeSkyTest` (slice D; needs 1a) | `Assume` that `SkyFrames` loads: for the five `skyEpochMs(position)` instants the frames built with a fixed test palette have `daylight` strictly increasing; the first frame's `moment` is NIGHT; the second's side of DAWN or SUNRISE; the fifth's DAY; `iconsDark` false for the first three; `quantizeSky` positions give at most 17 distinct epochs over 0 to 4; the same inputs give an equal `skyTop` (no clock, no randomness) |

Existing tests that must stay green: `RuntimeManagerInitTest`, `GuestPrefsTest`, `PracticeModelTest`, `ScenarioFilesTest` (INTEGRATE-B adds the tags of 3.8), `RobotTagsTest` (the Welcome's tags live in `WelcomeTags`, not in `RobotTags`).

## 5. Proof on the phone (P = owner-debug, pins and pictures only, `"safe": true`, dry Welcome; T = needs the twin; U = unit test; E = the owner's eyes)

**Link form** (the robot adds the token): `adb shell am start -a dev.pocketagent.app.DEBUG -n dev.pocketagent.app/dev.pocketagent.app.MainActivity --es route welcome --es firstrun reset:4@45 --es theme newroz --es dark dark --es token <T>`.

| # | Proof | Cat | How |
|---|---|---|---|
| 1 | The robot walks the Welcome from a wiped installation to the first answer on the practice model (plan "Proven when") | T | scenario `w1b-welcome` (the lead writes it; not `safe`): `wipe` the **twin only**, start; `expect` `screen.welcome` and `welcome.step.1`; `tap` `welcome.lang.system`, `tap` `welcome.next` twice; on step 3 `expect` `welcome.key.open` and `welcome.key.other`, `tap` `welcome.key.other`, `expect` the sheet, `key BACK`; `tap` `welcome.next` (it reads "Add a key later"); on step 4 `expect` `welcome.status`, `tap` `welcome.fire` (a tap) and `tap` `welcome.fire` with `holdMs 900`; `wait` until `welcome.next` is enabled (`timeout 180`: the unpacking takes about a minute); `tap` it; `tap` `welcome.tour.start`; `wait` for `welcome.tour.msg.2` (`timeout 120`); `shot`; `tap` `welcome.finish`; `expect` `screen.home`; `expectNot` `practice.banner`; the practice switch is off (`pref guest practice` false), `files/chat/` has no conversation, `settings.xml` holds `done`; `forceStop`, start again: `screen.home` and no `screen.welcome` |
| 2 | Skipping works and does not stop the unpacking | T | `w1b-welcome-skip`: `wipe`, start, `tap` `welcome.skip` on step 1; `expect` `screen.home` with the kindling (`home.state` text) and wait for `Stopped`; `settings.xml` holds `skipped`; restart: no Welcome |
| 3 | The Welcome never shows to an existing installation, and writes nothing there | U, P | `FirstRunRulesTest`; on the owner-debug build the app starts on Home (`expect` `screen.home`), and `shared_prefs/settings.xml` has no `first_run` entry (`run-as` read only, the lead's step) |
| 4 | Pictures of every step, dry, no write | P | one picture per step with the link above (`reset`, `reset:2`, `reset:3`, `reset:4@0`, `reset:4@45`, `reset:4@100`, `reset:5`), each in NEWROZ dark and NERGIZ light; plus `lang tr`, `lang fr`, `lang en-XZ` (the stretched test language), `rtl 1` (mirrored) and text at 2.0 for steps 1, 3 and 4 (the cut probe `cutCheck`; font scale needs the lead's `-AllowSystemSettings`); `scanDump` on each. The dry Welcome touches no runtime, so these are safe on the owner's package |
| 5 | The scene gets lighter with each step | P, E | the five step pictures side by side (the lead's contact sheet): the sky is darker on step 1 than 2, and so on (`WelcomeSkyTest` proves the numbers; the pictures prove the look) |
| 6 | The model step is a plain guide | P, E | picture of step 3 (needs ART-B's `wel_key_1..3`; a missing sprite shows the digit), once with the keyboard open (the field is visible, the primary button reachable) |
| 7 | The fire lesson works and does nothing real | T | on the twin: tap and hold on `welcome.fire`; the runtime state in `app.log` does not change; `expect` the notes |
| 8 | The key path saves and starts the agent | T | optional scenario `w1b-welcome-key` (twin): `type` a placeholder text (not a real key) into `welcome.key.field`, `expect` `welcome.next` reads Save and continue, `tap` it, `expect` the note; the scene's fire then follows `Starting` to `Running`; the guest is then not asked anything (no chat) |
| 9 | What's new is not shown to a new user | T | after proof 1 the `whatsnew` prefs hold the current page key (START's proof) |
| 10 | No new permission, no new component | U | the manifest diff of the slice is empty (SPLIT 10 item 11) |
| 11 | Smoothness while unpacking (measurement M1) | T | `phone-perf` over steps 2 to 4 on the twin's optimised build (`-TwinRelease`): frames within 8.3 ms and 16.7 ms while `Extracting`; if the 95th percentile is over budget the fallback of 3.3 (REDUCED scene while unpacking) is applied and measured again |
| 12 | The old data is unchanged | P | `dist\owner-backup\` compared read only; the Welcome adds nothing on the owner's phone |

**What only the owner's eyes can judge (E):** whether the five skies read as "the day coming up" and not as a colour fade; Pêt's four lines and the English of every card (tone, length, "no blame"); the key guide pictures (can a stranger follow them?); whether the cost sentence is acceptable to say; whether the scripted English answer of the tour (`tool-result-seen: hello-from-tool`) is acceptable in a localized app; the lesson of the fire; the line "Agirî is a Kurdish word for fire".

## 6. Core and extras, and the order of cutting (first cut first)

Extras: X1 (Paste), X2 (dry replay of the tour), X3 (celebration), X4 (rotating lines), X5 (splash anchor). Then, in the order of SPLIT section 11: item 7 (the lightening sky: **the two-frame fallback is `WelcomeFlow.SKY_EPOCHS_MS` with only the first and last used and no animation**, the sky is set once per step by a cut); item 9 (the weather and days cards: `questions` returns empty); item 10 (the tour: step 5 then offers only the real hello, and with no key shows `wel_tour_body` replaced by the line "Add a key in Settings", `GuidedChat` stays unused but keeps its symbols). **Never cut:** `prepareRootfs`, the two guarantees (the gate), the language step, the model step with the key guide, the one-sentence cost and its link, `FirstRunRules`, `WelcomeFlow`, the dry mode (the proof on the owner's phone needs it), the tags, `WelcomeChrome.iconsDark`, `FirstRun.initialRoutes`. A cut symbol keeps an empty body; every symbol of the SPLIT section 6 table that WELCOME provides (`FirstRun.initialRoutes`, `FirstRunDebug.pin`, `WelcomeScreen`, `WelcomeChrome.iconsDark`, `RuntimeManager.prepareRootfs`, `Settings.firstRun`) exists whatever is cut.

## 7. REQUESTS to other areas (exact), and what WELCOME provides

**Provided (the registry rows):** `FirstRun.initialRoutes(context): List<Route>`, `FirstRun.isExistingInstall(context): Boolean`, `FirstRunDebug.parse(text): FirstRunPin?` and `FirstRunDebug.pin(p)`, `@Composable fun WelcomeScreen()`, `WelcomeChrome.iconsDark: StateFlow<Boolean>`, `RuntimeManager.prepareRootfs()`, `Settings.firstRun`, `WelcomeTags`, `HearthScene(..., skyOverride: SkyFrame? = null)`, the string keys of 3.9. Nobody else calls `GuidedChat`, `WelcomeFlow` or `KeyGuide`.

| To | Request (exact) |
|---|---|
| **SHELL SH-1** | `Route.Welcome` (`encode` "welcome", `decodeRoute("welcome")`, `showsBar() == false`, `tabIndex()` 0, `rank()` 1 as every route without a bar) and `NavTags.screen(Route.Welcome) = "screen.welcome"`; `RouteContent`: `Route.Welcome -> WelcomeScreen()`. |
| **SHELL SH-2** | `AppRoot.kt:215`: `val nav = rememberSaveable(saver = Navigator.Saver) { Navigator(FirstRun.initialRoutes(ctx)) }` (move `val ctx = LocalContext.current` above it). The function is synchronous and writes nothing. |
| **SHELL SH-3** | `AppRoot.kt:249`: `BackHandler(enabled = (nav.canPop || route != Route.Home) && route != Route.Welcome) { ... }`. Without it a back press on step 1 would send the user to Home and skip the Welcome without writing `skipped`. |
| **SHELL SH-4** | Status-bar icons: for `route == Route.Welcome` the polarity is `WelcomeChrome.iconsDark.collectAsState().value` (not `!c.isDark`): the scene is full-bleed like Home's. Keep 1a's Home line intact: `darkIcons = ((route != Route.Home && route != Route.Welcome || practiceOn) && !c.isDark) || (route == Route.Home && !practiceOn && HomeSky.iconsDark...) || (route == Route.Welcome && !practiceOn && WelcomeChrome.iconsDark...)`. While the practice banner is on (during the tour) the area behind the status bar is the banner's own, as for Home. |
| **SHELL SH-5** | `DebugLinks.stackFor("welcome") = listOf(Route.Welcome)`; the extra `firstrun` is read with `FirstRunDebug.parse(value)` (null = rejected; the text `off` clears), applied through `FirstRunDebug.pin(pin)` after `wood` and before the route; `reset=1` calls `FirstRunDebug.pin(null)`. `DebugRequest` gets `firstrun: Setting<FirstRunPin>` after `wood` (SPLIT 4.1). |
| **SHELL SH-6** | The motion from the Welcome to Home (`nav.reset`) is a cross-fade of `Dur.SCENE` and no slide; the shell shows no bottom bar on the Welcome; `PocketBottomBar` appears with the existing slide when Home is reached. |
| **START ST-1** | `WhatsNewStore.markSeenForNewUser(context: Context)`: sets "seen" to the current page key (no-op when it is already that or newer); the live Welcome calls it at its first composition (D13). `WhatsNewHost` shows nothing while `route == Route.Welcome`. |
| **START ST-2** | The splash's "hold until the first frame" rule must not wait for a Home-specific signal: on a first run the first frame is the Welcome's. `FireAnchor.publish(xPx, yPx, sizePx)` is also called by `WelcomeScene` (X5); with no publish the splash fades after the time S8 finds. |
| **LANG LA-1** | `@Composable fun LanguageChoices(selected: String, onPick: (String) -> Unit, modifier: Modifier = Modifier, systemFirst: Boolean = true, tagOf: (String) -> String = { it })` in `J/ui/settings/LanguageSection.kt`: `selected` is the stored tag (`""` = System); `onPick(tag)` gets the tag to store; the first row is System with the phone's own language name; every other row shows the language under its own name and the "machine translated" mark where `LangTable` says; `tagOf(code)` gives the test tag (`""` is passed as `"system"`); the composable has no scroll of its own when the caller scrolls (a `Column`, not a `LazyColumn`), ten rows at most. If LANG's draft spells it differently the lead reconciles; the Welcome changes one call site. |
| **LANG LA-2** | When the phone's language is not one of the ten, the System row says so ("shown in English"): the first step is the only place a stranger learns it. |
| **LANG LA-3** | The four `wel_pet_line_*`, `wel_fire_stoked`, `wel_fire_held`, `wel_key_saved` are in the Pêt-lines pass; `docs/i18n/pet-voice.md` is their source; `docs/i18n/<code>.md` fixes how "key", "free key", "practice model" and "fire" are said. |
| **ART-B AB-1** | Three sprites, new file `A/pixel/illustrations_b.pxl`: `wel_key_1`, `wel_key_2`, `wel_key_3`, 24 by 24, one frame, at most 10 colours, plum outline visible on dark and light, drawn for 2u per pixel (48 dp). (1) a browser window with a sign-in figure; (2) a button with a small key; (3) a key and two sheets (copy and paste). No text, no brand marks, nothing that looks like a real logo. Fallback when missing: the Welcome draws the digit 1, 2, 3 (`PixelText`). A per-step picture is **not** wanted: the scene is the picture. |
| **SCENE / INTEGRATE-B SC-1** | `HearthScene` edit as 3.7 (UI applies it). The final 1a signature must keep `skyOverride` free as the last parameter. |
| **SCENE / INTEGRATE-B SC-2 (the one real collision)** | `SceneDaysStore`'s migration must decide "existing installation" with `FirstRun.isExistingInstall(context)` (CORE provides it) instead of `SceneCalendar.existingInstall(filesDir)`. Reason: the Welcome unpacks the image at its first step, so `rootfs.stamp` exists when a new user first reaches Home and the 1a rule would switch `newroz` on for them (against 1a R14). `FirstRunRules.isExistingInstall` is "stored NONE and the files say so": a phone the Welcome started or finished is never "existing", the owner's phone (no stored value, files present) still is. One call, one line in `SkyHost.kt` or wherever the store decides; `SceneCalendar.existingInstall` stays for its test. INTEGRATE-B writes it once 1a is merged (`rg "existingInstall" app/src` finds the place). |
| **FIRE / HOME-HERO** | None to edit. The Welcome calls `FireSwitch.classify`, `FireSwitch.movedBeyond`, `FireRing.fraction`, `FireGeometry`, `StageGrid.forScene`, `SceneGeometry.of` as the registry spells them. If `HomeHero.kt` keeps its fire gesture loop private, UI writes a second copy of the same loop (about 30 lines) rather than asking for a shared one; a shared `Modifier.fireTarget` is a 1c clean-up. |
| **PET** | None. The Welcome posts `PetEvent.Tap(2)` once on step 2 (relies on "even n gives WAVE", PET.md 3.2, and on Pêt not being asleep; the fire is already catching at that point because unpacking began at step 1) and `PetEvent.Forged` on the first answer. If PET renames these, one call site changes. |
| **WEATHER** | None. `WeatherStore.available` and `WeatherStore.get(ctx).setEnabled(true)` as the registry spells them; the credit line stays in Appearance. |
| **APPEAR-B** | None. `AppearanceStore.get(ctx).update { it.copy(language = tag) }` already exists. |
| **BARS** | None. |
| **INTEGRATE-B** | (1) the tags of 3.8 into `ScenarioFilesTest`'s known set; (2) SC-2; (3) `tools/strings-check.baseline.json` and `tools/lint-ui-baseline.json` get no rise (new files start at 0; WELCOME uses tokens and `t()`); (4) the regenerated `app/src/debug/res/values-en-rXZ/` for `strings_welcome.xml`. **The lead** (not an area): lookup L1 (the two addresses and the sentence), the scenarios `w1b-welcome`, `w1b-welcome-skip`, optional `w1b-welcome-key`, the `safeNames` decision (none of them is safe). |

## 8. Risks, and where the plan or the design language is wrong or impossible given the real code

1. **The days-store collision (SC-2).** Described above; found by reading 1a R4 against plan 1.7's "unpacking starts at the first step". It is real, silent (nothing crashes: a new user simply gets Newroz) and exactly the kind of default the owner asked not to have. The fix is one call; if INTEGRATE-B forgets it, `FirstRunRulesTest.isExistingInstallForTheStores` documents the rule and a grep `rg "SceneCalendar.existingInstall" app/src/main` finds the line. `AppearanceStore` has no such marker (1a R4), so nothing else breaks.
2. **A stored "started" value is a third write the plan did not name.** The plan says "never shows again after finished or skipped" and "never to an existing installation". Without `started` the second rule destroys the first mid-wizard (D3). The owner's phone is still never written.
3. **The key page and the terms are not read here.** `KEY_PAGE_URL`, `TERMS_URL`, the name of the button in `wel_key_step_2` and the cost sentence are from memory until L1. A wrong address would send a person to a wrong page. `isSafeUrl` limits it to two hosts of one company; the lead reads both pages on the day and edits the constants (one place) and the English sentence.
4. **The tour's answer is not localizable and not friendly.** The practice model answers `tool-result-seen: hello-from-tool`, in English, whatever the interface language. The captions explain it. A nicer scripted reply needs a new keyword in `rootfs/smoke/mock_llm.py` and its asset copy, which 1b does not touch (SPLIT section 5). Open point 3.
5. **The first turn after a cold start takes 15 to 30 s (AGENTS.md).** The tour starts Hermes on request (about ten seconds plus the first turn). `WAKE_TIMEOUT_MS` 120 s and `ANSWER_TIMEOUT_MS` 90 s give a failure card with Retry; the scene's fire catching and the skeleton lines make the wait honest (pattern 18). Starting the practice agent earlier (at step 4) was rejected: it would show the practice banner before the user chose the tour.
6. **`HearthScene` after 1a is not read yet.** The edit of 3.7 is written against the registry; if the final host reads the frame in more places than the registry names (for example a second `rememberSkyFrame()` for `HearthInfo`), UI must route all of them through `frameState`. SC-1 asks that `skyOverride` be the last parameter.
7. **The scene during unpacking.** One minute of disk work and a 30 fps scene may drop frames on the twin's release build (not measured). Fallback written (REDUCED scene while `UNPACKING`, no new parameter). The plan lists this as a measurement, not a proof.
8. **Dialog language and sheet visibility (the Wave 0 phone proof).** `PocketSheet` (the provider form) and `ConfirmDialog` (Skip with an unsaved key) were found to ignore the in-app language and to be invisible at normal animation speed; both are being fixed (`docs/waves/w0/phone-proof.md`). The Welcome is the first screen a stranger sees, so it relies on both fixes; slice D does not start before they are merged. If a sheet is still wrong the fallback for "I use something else" is an inline expansion of `ProviderSection(vm)` inside the card (no window).
9. **A key typed in the Welcome lives in `MainViewModel.apiKeyInput`** (memory only, as in Settings). A killed process loses it (the user goes to the browser and the process may die): the user pastes again; X1 makes that a one-tap action. Nothing is ever written to a log (`AppLog` redacts keys; the Welcome logs only step numbers and states).
10. **The Welcome never touches `Settings.authMode` unless the user saves.** A user who types a key and skips with the confirmation loses it; one who saves and then goes Back keeps it (the field then shows "stored", via `vm.hasStoredApiKey`).
11. **Android installs and Play Protect.** A new user's first install is not affected by anything here. For the twin, the first install still needs the owner's one tap (AGENTS.md); the Welcome's proof 1 therefore cannot run before that.
12. **`wipe` is twin-only and removes the Linux image,** so proof 1 takes about a minute longer than a normal scenario; the scenario's `timeoutSec` must be at least 420.
13. **Right-to-left.** The scene never mirrors (DL 13.3) and the card does; the fire target's hit rectangle is computed in scene coordinates, so it is right in both. Tested by pictures with `rtl 1`.
14. **The pixel font may lack glyphs** for the Welcome's titles in Cyrillic and some accents until ART-B's glyphs are in (slice B); a title the font cannot draw drops to `Type.title` as DL 13.1 says (`PixelTitle` already does), so nothing shows as a box.
15. **`wel_meet_name` says Agirî is a Kurdish word.** It is true and short; the owner asked that the Kurdish identity not be overdone. Open point 4.
16. **Kotlin traps for an agent that cannot compile:** no slash-star inside a comment; `0xFF...` literals need `.toInt()`; declare every property before the `init` that reads it; `Pocket.colors`, `Pocket.anim`, `Pocket.appearance`, `Pocket.motion` are `@Composable` getters (read them into locals in composition, never in a draw lambda); `rememberSaveable` of a `Set` needs a saver (use a `List<String>` and `toSet()`); `enum class` entries used in `when` must be exhaustive (the `NextLabel` `when` in the UI has no `else`); `data object` needs Kotlin 2.x (present); the string `%%` in `wel_kindle_unpacking` must stay doubled; `aapt2` trims leading and trailing spaces of a string (none of these has one).

## 9. Open points for the lead (each has a default; the drafts and the code can start)

1. **Lookup L1 (the lead, on the day the wave is built).** The address of the AI Studio key page, the provider's terms page for the free tier, what it says about use of submitted content on a free key and about countries where a free key is offered, and the name of the "create key" button. Default until then: the placeholders of 3.2 and 3.9, flagged as placeholders in the code (`// L1`) and in the note of `wel_model_cost`.
2. **SC-2 (the days store's "existing installation").** Default: yes, INTEGRATE-B swaps it for `FirstRun.isExistingInstall(context)`. The alternative (leaving it) gives every new user Newroz.
3. **The tour's scripted answer.** Default: the existing keyword `RUN_TOOL` and captions (D6). The alternative is a new keyword (`TOUR`) with a friendly, localizable-in-spirit reply, which touches `rootfs/smoke/mock_llm.py` and rebuilds the rootfs.
4. **The line "Agirî is a Kurdish word for fire."** Default: kept, muted, one line on step 2. The owner may remove it (delete `wel_meet_name`).
5. **Start the agent automatically at step 3 or 4 when a key is saved (D5).** Default: yes. The alternative waits for the end of the Welcome (the agent then starts when the user finishes).
6. **The tour's conversation (SPLIT open point 15).** Default: deleted at the end with the practice switch turned off; a real first hello is kept and opened.
7. **Skip confirms only with an unsaved key (D9).** Default as stated; the alternative (always confirm) adds a dialog to the most common exit.
8. **The two optional questions are on step 4.** Default as stated; the design language says only "on the way".
9. **`HearthScene.skyOverride` and the transfer of the file** (SPLIT open point 7): confirm. The alternative is impossible (a pinned clock is inert outside test builds).
10. **Order of work between 1a and WELCOME-UI.** UI starts in slice D, after 1a is merged and after the two Wave 0 phone-proof fixes for sheets and dialogs. CORE starts in slice A now. If 1a's final registry names something differently from what this draft quotes, the lead re-reads section 2 and section 7 before UI starts.
11. **A person who restores a backup into a fresh install** goes to Settings from the Welcome (Skip), or after it; the Welcome offers no restore button in 1b. Say if "Restore a backup" must be on step 1.
