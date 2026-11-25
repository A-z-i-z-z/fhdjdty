# Wave 1b, "Home: the shell, the first run and ten languages": how it is cut (written 2026-11-25)

This file is the planner's cut of the second half of Wave 1. It is the input of the area drafts
(`docs/waves/w1b/<AREA>.md`, one per area, written like `docs/waves/w1/<AREA>.md`) and of the contract
(`docs/waves/w1b-contract.md`). It lists what belongs to 1b, who owns which file, what is not in 1b, what must
be tried on the phone first, and the closed slices in which the wave is built. Nothing here is built.

Sources read for this cut: `docs/phase4-plan.md` 7.1 (lines 768 to 917), 4.1 (415 to 436), section 6 (511 to
559), 8 (938 to 999), 9.1 and 9.2, 13 (1 to 22); `docs/design-language.md` 2.2, 2.3, pattern 8, 6.4, 7, 8.1, 8.4,
8.15, 8.16, 9, 10, 13; `docs/waves/w0-contract.md` 3.3 to 3.6, 3.12; `docs/waves/w1a-contract.md` (all of
sections 0 to 3, 7.5, 8, 9, 10, 11) and `docs/waves/w1/LEAD-DECISIONS.md`; and the tree at the work branch
(`ccr-bb6d7bcc-9eojyu`). Facts about files below were read in that tree. **Everything 1b takes from Wave 1a is
taken from `docs/waves/w1a-contract.md` (its registry, section 3, and its section 7.5 edits), not from today's
code: 1a is being built in the worktrees `dist/wt-w1` (branch `w1a`, slices A and C) and `dist/wt-w1b` (branch
`w1a-art`; careful, "w1b" there means slice B of 1a, not this wave) and is not in the work branch.** Where 1a's
build changes a name, the 1b contract is re-read against 1a's final contract before the first 1b agent starts.

## 1. What 1b is

The plan says (7.1, lines 888 to 889): "the scene, the fire, Pêt and Home first (0.3a); the tab bar with the
first Library, the Welcome and the languages second." So 1b is the shell around Home and everything about the
first minute and the world's languages:

1. the new tab set Home, Chats, Library, Bots, Agent, with Settings behind a gear;
2. the first Library (two shelves: what exists today);
3. the start: the splash that hands the flame to the scene (experiment S8), "What's new" after an update;
4. the Welcome, the first-run wizard, with unpacking from its first step and a guided chat on the practice model;
5. ten languages: the picker, three language settings, the glossaries, the translations, the pixel font's new
   glyphs, plural and date checks;
6. what 1a left over for the shell (the fire line in top bars, the small Pêt, Appearance's wallpaper accent and
   Pêt-in-chats row, tab icon frames, the remaining empty-state pictures, the heat-haze default).

It completes version 0.3 (plan table line 346; 1a is "0.3a"). **Core and extras** (plan decision 19, assumed
yes): the core is every item of section 2 not marked "extra"; extras are cut first, in the order of section 11.

## 2. The items of the plan that belong to 1b

"Line" is the line in `docs/phase4-plan.md`. "DL" is `docs/design-language.md`. Area names are those of section 4.

| ID | Item (plan number) | Where in the plan | Area | Kind |
|---|---|---|---|---|
| B1 | Bottom bar v2: the hopping ember, tab icons that play once when chosen (1.6) | l.835; DL pattern 8 (l.176) | SHELL (code), ART-B (frames) | core |
| B2 | The fire line in top bars, fed by `FireFeed` (1.6, 1.4) | l.824 to 825, l.835; DL 8.2 | BARS | core |
| B3 | The start screen: the system's splash shows the flame, which travels to its place in the scene; plain fade without it (1.6, experiment S8) | l.835 to 837; l.2953 | START | core, cut-first part: the handoff (the fade is core) |
| B4 | "What's new": after an update a Pêt note opens one short page per version, `assets/whatsnew/<version>.md`, in every interface language, drawn by the app's own Markdown view (1.6) | l.838 to 839; section 6 step 9 (l.540 to 541) | START | core |
| B5 | The tab bar of 4.1: Home, Chats, Library, Bots, Agent (1.6) | l.840; 4.1 l.421 to 428 | SHELL | core |
| B6 | The Library opens with what exists today: saved messages and the files given to the agent (1.6) | l.840 to 841; 4.1 l.425, 435 to 436; DL 8.15 | LIBRARY | core |
| B7 | Settings moves behind a gear (Home and the Agent tab) (1.6) | l.841; 4.1 l.429 to 430; DL 8.1 l.455 to 456 | BARS (the gear), SHELL (the route), APPEAR-B (the screen) | core |
| B8 | Welcome: five steps on one continuous scene that grows lighter (1.7) | l.843 to 845; DL 8.4 (l.518 to 525) | WELCOME | core |
| B9 | Unpacking the Linux starts in the background at the first step (1.7) | l.845 to 846 | WELCOME (core half) | core |
| B10 | The model step leads with the free path: a three-picture guide to a Google AI Studio key and a button that opens the key page; "I use something else" opens the full provider form (1.7) | l.847 to 848 | WELCOME (UI half), ART-B (pictures) | core |
| B11 | Never shows again after it was finished or skipped, and never to an existing installation (1.7) | l.849 | WELCOME (core half) | core |
| B12 | Try before a key: a short guided chat on the practice model shows a question, a tool step and an answer (1.7) | l.850 to 852 | WELCOME | core |
| B13 | What a free key costs: one sentence, a link to the provider's own terms as they read when the wave is built, and that a free key is not offered in every country (1.7) | l.853 to 856 | WELCOME (UI half); needs lookup L1 | core |
| B14 | The two optional questions on the way, both skippable: may the sky show the weather, and which days should the scene know (DL 8.4; plan 1.3 "in the Welcome or under Appearance") | DL l.523 to 525; l.811 to 812 | WELCOME (UI half) | core (the weather question is composed only while `WeatherStore.available`) |
| B15 | The wizard teaches the fire gesture once (tap lights, hold puts out) (DL 6.4) | DL l.333 to 334 | WELCOME (UI half) | core |
| B16 | Ten languages: English, Spanish, French, German, Portuguese, Italian, Turkish, Russian, Indonesian, Kurmanji (1.8) | l.858 to 862 | LANG | core |
| B17 | The picker shows each language under its own name, "System" first; one setting becomes three: language of the interface, language of answers ("as I write" by default or fixed), language of dictation (1.8) | l.863 to 865 | LANG | core |
| B18 | A glossary per language (`docs/i18n/<code>.md`); a mid-size model translates with it, each text's note and everything translated before; a second agent reads every language for length, placeholders and tone; `strings-check` proves nothing is missing (1.8) | l.866 to 870 | LANG (stage 4) | core |
| B19 | A language nobody who speaks it has read is marked "machine translated" in the picker; the owner reads one page for Turkish (sixty most visible texts, `docs/i18n/tr-review.md`); a page for Kurmanji if the owner wishes (1.8) | l.871 to 874 | LANG; the owner's reading waits | core (the page for Kurmanji: extra) |
| B20 | Plural rules and dates for a language Android cannot format come from the app's text function; each of the ten is checked for this on the phone (1.8) | l.877 to 879 | LANG | core |
| B21 | The pixel font learns about 130 glyphs: Cyrillic in both cases and the accented Latin letters still missing (1.8) | l.860 to 862; DL 2.2 (l.96 to 99), 13.1 | ART-B | core |
| B22 | Appearance: wallpaper accent (one more `AccentId` that takes the system accent colour, Android 12 and later) (1.9) | l.882 to 883; DL 2.3 l.122 | APPEAR-B | core |
| B23 | Appearance: Pêt in chats (the row that 1a wrote and did not compose) and the small Pêt (`PetPeek`) looking over a chat's top bar while the agent works (1.5, 1.9) | l.831, l.883; 1a contract A6 and open point 7; DL 7 l.405 to 407 | APPEAR-B (the row), BARS (the placement) | core |
| B24 | Eight empty-state pictures: the pictures exist after 1a only as far as 1a did not cut them (`empty_bubble` is core in 1a, the seven others are extras there); 1b draws the missing ones and places them in `EmptyState` (1.5) | l.831; 1a contract 3.10 and cut order 9; DL 7 l.406 to 408 | ART-B (pictures), BARS (placement) | core for the placement, the missing pictures: extra |
| B25 | Tab icon frames (5 tab icons, 4 frames each) and the Library icon (moved to 1b by the lead's ruling in `docs/waves/w1/ART-WORLD.md` line 227) | plan table l.902 | ART-B | core |
| B26 | Heat haze: the lead flips `FireDriver.hazeActive` to true by default in 1b after the R8 measurement (1a contract l.639) | 1a contract section 5.3 item 6 | INTEGRATE-B (the lead) | extra (plan extras l.891) |
| B27 | Crackle volume follows `FireFeed.level()` (1a ruling F6: "stays a flat 0.25, extra later"; registry 3.4 "via SceneSounds in 1b") | 1a contract 4 (F6), 3.4 | APPEAR-B | extra |
| B28 | Version label 0.3 (1a is "0.3a") | plan l.346, l.888 | INTEGRATE-B | core |
| B29 | The "Proven when" items that are 1b's: the robot walks the Welcome from a wiped installation to the first answer on the practice model; the five main screens photographed in each of the ten languages with nothing missing and nothing cut; the owner read the Turkish page; a reviewer looked at every new sprite and glyph on a contact sheet | l.908 to 909, 915 to 917 | section 10 | core |

Rules from the plan that apply to every screen of 1b: principle 12 (English first, new names are English words),
principle 13 (a label a screen reader can speak, text at double size, photographed mirrored, in every wave), DL
8.16 (every screen is one of six kinds with five states), DL 13.2 (a tab label has room for 10 characters in
English, a translation for 14), DL 13.5 (no exclamation marks except Pêt's, capitals written by the author, never
made by code).

## 3. What 1a explicitly handed to 1b (searched in the 1a contract and `docs/waves/w1/`)

Every row is in exactly one area (or in INTEGRATE-B, stage 5, which is not one of the eight areas).

| Deferred item | Source | Goes to |
|---|---|---|
| Wallpaper accent row of Appearance ("Wave 1b with the shell; it needs no permission") | 1a contract section 10 point 10 | APPEAR-B (B22) |
| `PetPeek` is placed in 1b; the `apr_pet_*` row is composed in 1b ("1b places it") | 1a contract A6, section 10 point 7; `docs/waves/w1/PET.md` lines 18, 233, 240 | BARS places `PetPeek`; APPEAR-B composes the row (B23) |
| The chat top bar's fire line and every other reader of `FireFeed.level()` "in Wave 1b and later"; the widget is Wave 10 | `docs/waves/w1/FIRE.md` line 77; plan l.824 to 825 | BARS (B2) |
| Tab icon frames in `icons.pxl` and the pixel font's new glyphs (the lead moved them from ART-WORLD to 1b) | `docs/waves/w1/ART-WORLD.md` line 227 | ART-B (B21, B25) |
| Heat haze on by default "the lead flips the default in Wave 1b" once the R8 measurement of E2 passes | 1a contract section 5.3 item 6 | INTEGRATE-B (B26) |
| Crackle volume from `FireFeed.level()` "via SceneSounds in 1b" | 1a contract 3.4 | APPEAR-B (B27) |
| The status-bar icon polarity line of `AppRoot.kt` (`HomeSky.iconsDark`) belongs to "SHELL, Wave 1b" in the drafts, and 1a's INTEGRATE writes the two lines (7.5 step 5) | `docs/waves/w1/HOME.md` line 254, `SCENE.md` line 303; 1a 7.5 | SHELL keeps it intact when it rewrites `AppRoot.kt` |
| "Home keeps the five tabs as they are (Wave 1b changes them)" | `docs/waves/w1/HOME.md` line 3 | SHELL |
| `tile_hermes` for the Agent tab (1b); `tile_library` (1a extra) | `docs/waves/w1/ART-WORLD.md` line 150; 1a 3.10 | ART-B draws what 1a cut; BARS points the Home tiles at the new tabs |
| Settings sections: `LanguageSection` "at line 273 stays as it is: LANG takes it out in Wave 1b" | `docs/waves/w1/APPEAR.md` line 35 | LANG writes the new file; APPEAR-B removes the old one |

## 4. The areas

Eight areas. As in 1a, a big area is built by two agents that read the same draft and write disjoint files
(WELCOME, LANG). Every file below has exactly one owner. `J/` is `app/src/main/java/dev/pocketagent/app/`, `T/` is
`app/src/test/java/dev/pocketagent/app/`, `R/` is `app/src/main/res/`, `A/` is `app/src/main/assets/`; "new" means
the file does not exist; "1a file" means the file is created by 1a and is, in 1b, edited only by the named owner.
New string files carry the notes (`<!-- t: ... | kind: ... -->`) and a prefix no other area uses. The prefixes
`shell_ bar_ lib_ wel_ wn_ lang_` are free today (checked in `R/values/strings*.xml`); `apr_` continues 1a's.

### 4.1 SHELL: the tab bar, the routes, the gates

Owner of the map of the app (plan 4.1, last sentence: "one agent owns it in every wave, the owner of `ui/Nav.kt`").
Two passes: slice C (Library only) and slice D (Welcome and What's new routes), see section 9.

| Files | Notes |
|---|---|
| J/ui/`Nav.kt` | `Route.Library(shelf: String?)` (keys `library`, `library:<shelf>`), `Route.Welcome` (key `welcome`), `Route.WhatsNew` (key `whatsnew`); `tabIndex()` becomes Home 0, Chats 1, Library 2, Bots 3, Hermes 4 (the route and its key `hermes` keep their names; only the label becomes "Agent"); `Route.Settings` leaves the bar (pushed from the gear; `tabIndex` that of the tab below); `Route.Starred` stays (old links, scenarios, tests) and counts as Library; `showsBar()` false for Welcome and WhatsNew |
| J/ui/nav/`NavStack.kt`, `NavTags.kt`, `NavMotion.kt` | stable root ids (proposal: 1 to 4 kept, Library 6, 5 stays the id of a Settings root that nothing puts on the bar; `isRootId` and `FIRST_FREE_ID = 100` adjusted), `TAB_LIBRARY = "tab.library"`, `screen.library`, `screen.welcome`, `screen.whatsnew`; `TAB_SETTINGS` fate: open point 4 |
| J/ui/`AppRoot.kt` | the tab list, `RouteContent` for the new routes, `navigateToLink` (`AppLink.Usage` goes Home then `Settings("usage")`), the initial stack from WELCOME's `FirstRun.initialRoutes(context)`, the one placement of START's `WhatsNewHost()` beside `PetNoteHost`, the status-bar polarity (keeps 1a's `HomeSky.iconsDark` line and adds Welcome's `WelcomeChrome.iconsDark`), the first-frame rule (no flash of Home before Welcome) |
| J/ui/`PocketBottomBar.kt` | new: the bar moves out of `AppRoot.kt` (it is 60 dp today, with a 3 dp ember under the active tab); the ember hops (DL pattern 8: five steps, two sparks), the icon of the chosen tab plays once (`PixelImage(name, frame = n, playing = false)` stepped by time, never looped); labels follow DL 13.2 (icons only, label under the chosen tab, when a label exceeds its room); `Pocket.motion` and `Dur` only |
| J/ui/components/`PixelIcon.kt` | the enum gains `LIBRARY` (sprite `icon_library`, from ART-B's new file); nothing else changes |
| J/`DebugLinks.kt`, T/`DebugLinksTest.kt` | routes `library`, `library:<shelf>`, `welcome`, `whatsnew` in `stackFor`; extras `firstrun` (`reset\|done\|off`, memory only) and `whatsnew` (`pending\|off`), appended after `wood` in `DebugRequest` (1a contract 3.9 order) and applied after it; both call pin objects the owners provide (section 6); `reset=1` clears them |
| R/values/`strings_shell.xml` (`shell_`) | `shell_tab_home`, `shell_tab_chats`, `shell_tab_library`, `shell_tab_bots`, `shell_tab_agent` (each `kind: tab`), `shell_gear_cd` (the gear's spoken label, used by BARS); SHELL also deletes the five `ui_nav_*` keys from `R/values/strings_chat.xml` and its `values-tr`, `values-fr` copies in the same change (the lint ratchet counts an unused key; baselines never rise, 1a ruling 2) and is the only agent that touches `strings_chat.xml` in 1b |
| T/ui/nav/`NavStackTest`, `NavMotionTest`, `NavTagsTest`; T/`AppLinksTest.kt`; T/ui/`TabModelTest` (new, pure `TabModel`: the tab list, route to tab, label room) | the existing tests assert five tabs by index and by tag: SHELL updates them in the same slice |

Also SHELL's list for the lead's robot pass (not SHELL's files): `tools/scenarios/smoke-launch.json`,
`smoke-tabs-labels.json`, `smoke-hermes-running.json` and `tools/phone-lib.ps1` line 1753 tap `tab.settings`;
they are rewritten after the slice (open point 4).

### 4.2 BARS: top bars, the gear, the fire line, Pêt's peek, empty states, and the Home edits that go with them

| Files | Notes |
|---|---|
| J/ui/components/`PocketTopBar.kt` | a gear action: `onSettings: (() -> Unit)? = null` as the last parameter, tag `top.gear`, spoken label `shell_gear_cd`; the existing `ember: Boolean` stays |
| J/ui/components/`EmberLine.kt` | an optional `level: () -> Float = { 1f }` read in the draw phase only; callers that pass it read `FireFeed.level()` (1a registry 3.4); with the animation level OFF it is a still line |
| J/ui/chat/`ChatTopArea.kt` | the fire line burns at the level of `FireFeed`, and `PetPeek(enabled = Pocket.appearance.petInChatsVisible, working = generating)` sits over the bar's lower edge (PET.md 3.7: draws nothing and never throws when the sprite is missing) |
| J/ui/hermes/`HermesScreen.kt` | the Agent tab: title "Agent" (new key, below), a gear in the top bar, nothing else moves (the six rooms are Waves 3, 4 and 11) |
| J/ui/components/`EmptyState.kt`, plus one `art =` argument in J/ui/chat/`ChatListScreen.kt`, J/ui/bots/`BotsScreen.kt`, J/ui/chat/`ChatMessageList.kt`, J/ui/routines/`RoutinesScreen.kt` | `EmptyState` gains `art: String? = null` (a sprite name from `illustrations*.pxl`; DL 9 says empty states use illustrated sprites "at 2u per pixel", 1a drew them 32 by 32 and shows `empty_bubble` at 96 dp on Home, so the size is read from 1a's `Illustrations.kt`, not decided here); falls back to `Mascot` as today; the `StarredScreen` call belongs to LIBRARY |
| J/ui/home/`HomeHero.kt`, `HomeScreen.kt`, `HomeParts.kt`, `HomeStatus.kt`, `HomeLists.kt` (1a files) | the gear on Home (over the hero's top right and inside the pinned strip, tag `home.gear`, label `shell_gear_cd`), the tile targets (the Saved tile opens `Route.Library("saved")`, the Hermes tile opens the Agent tab), and one call `FireAnchor.publish(...)` where the hero knows the fire's window position (for the splash, START). Nothing of Home's behaviour changes; Home's rows slot gets no row in 1b |
| R/values/`strings_bars.xml` (`bar_`) | `bar_agent_title`, `bar_agent_subtitle`, `bar_fire_line_cd`, `bar_peek_cd` and the like |
| T/ui/components/`EmberLevelTest` (pure mapping from level to steps), T/ui/home/`HomeGearTagsTest` | |

### 4.3 START: the splash and "What's new"

| Files | Notes |
|---|---|
| J/`MainActivity.kt` | the splash exit listener (`SplashScreen.setOnExitAnimationListener`, platform API 31; no new library) and the rule that holds the splash until Home's first frame (a pre-draw listener on the content view; the outcome of S8) |
| R/values-v31/`themes.xml` (new), R/values/`themes.xml` | `Theme.PocketAgent` for API 31 and later with `windowSplashScreenAnimatedIcon` (the flame, `@drawable/ic_splash_flame` from ART-B), `windowSplashScreenBackground`, `windowSplashScreenAnimationDuration`; the base file keeps the plum window colour so there is no white flash before API 31 |
| J/ui/pixel/`FireAnchor.kt` | new: where the fire is on screen (`publish(xPx, yPx, sizePx)`, `target: StateFlow<FireTarget?>`); BARS calls `publish`, START's listener reads `target` (waits at most the time S8 finds, else fades) |
| J/ui/whatsnew/`WhatsNewLogic.kt` (pure: page key of a version name, "should a note show", fallback language), `WhatsNewStore.kt` (prefs `whatsnew`, key `seen`), `WhatsNewHost.kt` (posts the Pêt note through `PetNotes.show`, with an action that opens the page), `WhatsNewScreen.kt` (reader: loads the page for the language in force, else English, draws it with the app's Markdown view), `WhatsNewDebug.kt` (the pin) | `Route.WhatsNew` is SHELL's. A note shows once per page: after an update the first start sees `seen` older than the page key; a fresh install and the end of the Welcome set `seen` so a new user is never shown it; an existing installation (the owner's) sees it once |
| A/whatsnew/`0.3.md` (English), later `0.3.<code>.md` for the other nine | START writes the English page (one short page; plain English; what a person gets: the plan's "The owner gets" for Wave 1, l.346); LANG's stage 4 writes the nine others; open point 9 (naming) |
| R/values/`strings_start.xml` (`wn_`) | `wn_note`, `wn_note_action`, `wn_title`, `wn_close` |
| T/ui/whatsnew/`WhatsNewLogicTest`, `WhatsNewAssetsTest` (every page parses; every language has its file or falls back; at most 1,200 characters; no heading made by code) | |

### 4.4 LIBRARY: the first Library

| Files | Notes |
|---|---|
| J/ui/library/`LibraryScreen.kt` (`fun LibraryScreen(shelf: String?)`), `LibraryShelves.kt` (the row of shelf tabs under the title that scrolls sideways, each with its count; DL 8.15), `FilesShelf.kt`, `SavedShelf.kt`, `LibraryTags.kt` | kind of screen: List (DL 8.16). Shelves: **Files** (the photos and documents given to the agent, from `files/chat/attachments/`: name, kind, size, date, the chat it belongs to; tap opens the chat at the message, a second action opens or shares the file through the FileProvider whose `file_paths.xml` already holds `chat/attachments/`) and **Saved** (the starred messages). A shelf that is empty still shows (the first two always show); the later shelves are other waves'. Read only in 1b: nothing in the Library deletes an attachment (it would break the chats that point at it) |
| J/data/`LibraryIndex.kt` (pure: shelf model, sort, filter by kind, counts), `LibraryFiles.kt` (reads the attachments of the loaded conversations; never writes) | |
| J/ui/chat/`StarredScreen.kt` | the list body is extracted as `StarredList(...)` so the Saved shelf and the old full screen show the same thing; `Route.Starred` keeps working |
| R/values/`strings_library.xml` (`lib_`) | shelf names, counts as `<plurals>` (`lib_files_count`, `lib_saved_count`), the five states |
| T/data/`LibraryIndexTest`, T/ui/library/`LibraryShelvesTest` | |

The five states (DL 8.16): Empty (a picture from ART-B and the one action that fills it: "Attach a file in a
chat" and "Long-press a message"), Loading (`Skeleton` rows), Error (could not read: what was kept, Retry),
Offline and Needs permission: **not applicable** (no network, no permission) and the contract says so in one line.
Tags: `screen.library` (SHELL), `library.shelf.files`, `library.shelf.saved`, `library.list`, `library.row.<i>`,
`library.empty`, `library.skeleton`, `library.error`, `library.retry`.

### 4.5 WELCOME: the first run (two agents: WELCOME-CORE, WELCOME-UI)

| Files | Agent |
|---|---|
| J/ui/onboarding/`FirstRun.kt` (pure rules and the store: `FirstRunRules.decide(existingInstall, stored)`, `FirstRun.initialRoutes(context)`, `FirstRun.markDone/markSkipped/saveStep`), `WelcomeFlow.kt` (pure state machine: steps LANGUAGE, MEET, MODEL, KINDLING, HELLO; Next, Back, Skip, resume), `KeyGuide.kt` (pure data: three guide pictures, the key page address constant, the terms address constant, the sentence's facts), `GuidedChat.kt` (pure script of the guided chat: the messages and the practice keywords they carry) | CORE |
| J/runtime/`Settings.kt` (one flag, as the plan's table says: `firstRun`), J/runtime/`RuntimeManager.kt` (a new `prepareRootfs()`: extract and stamp the Linux image without launching Hermes; today `start()` runs `ensureRootfs()` and then `launchHermes()` refuses a missing provider, so a user with no key cannot unpack; the new entry reuses `ensureRootfs()` under the same mutex and ends in `Stopped("ready")`, with the same progress flow) | CORE |
| T/ui/onboarding/`FirstRunRulesTest`, `WelcomeFlowTest`, `KeyGuideTest`, `GuidedChatScriptTest` (every keyword is in `PracticeModel.KEYWORDS`), T/`RuntimePrepareTest` | CORE |
| J/ui/onboarding/`WelcomeScreen.kt`, `WelcomeSteps.kt` (the five steps), `WelcomeScene.kt` (drives `HearthScene` per step), `KeyGuideUi.kt`, `GuidedChatUi.kt`, `WelcomeTags.kt`, `WelcomeChrome.kt` (`iconsDark: StateFlow<Boolean>` for SHELL's status-bar line) | UI |
| J/ui/pixel/`HearthScene.kt` (1a file) | UI: the one additive edit: a last parameter `skyOverride: SkyFrame? = null` used instead of `rememberSkyFrame()` when set, so a step can show a lighter sky than the clock; Home passes nothing. This transfers the file from 1a's SCENE to WELCOME-UI for 1b (open point 7) |
| R/values/`strings_welcome.xml` (`wel_`) | UI |

Behaviour fixed by the plan: five steps on one scene (language, meet Pêt, model, kindling, first hello), a Flow and
a Stage at once (DL 8.16: at most four controls, a progress line of whole steps, Back always there, nothing lost on
leaving); unpacking starts at step one; the model step offers the free key path, one sentence on what it costs and a
link; "I use something else" shows the existing `ProviderSection(vm)` (internal in `ui/settings`, callable from the
same module, not edited); step four is the fire catching as the unpack progresses, with the gesture taught once
(1a's `FireSwitch`); step five is the first hello, or the guided chat on the practice model (zero tokens: the
switch is `GuestPrefs.setPracticeModel`, the banner shows, on a build that is not the twin it ends after two hours,
and the Welcome switches it off when the tour ends); the two optional questions (weather: only while
`WeatherStore.available`; days: the `CULTURE` lists of `SceneCalendar.LISTS` with a non-zero `DayListTexts.title`,
none on by default, 1a ruling R14). The plan's two guarantees are rules of CORE: "never again after finished or
skipped" (a stored value `done` or `skipped` written only when the Welcome ends) and "never to an existing
installation" (derived, not written: `files/rootfs.stamp` or `files/chat/conversations.json` exists, the rule of 1a's
`SceneCalendar.existingInstall`; CORE writes its own copy of that pure rule with a test that equals 1a's, and
INTEGRATE-B swaps it for a call once 1a is in the tree; `bots.json` is not used, 1a's finding). **On the owner's phone
the Welcome therefore writes nothing at all** (open point 5 asks whether to freeze the answer instead).

Tags: `screen.welcome` (SHELL), `welcome.step.<n>`, `welcome.next`, `welcome.back`, `welcome.skip`,
`welcome.lang.<code>`, `welcome.key.open`, `welcome.key.other`, `welcome.tour.start`, `welcome.tour.skip`,
`welcome.fire`, `welcome.finish`.

### 4.6 LANG: ten languages (two phases: stage 1 code, tools, glossaries; stage 4 translation)

**The ten, by the plan's recommended option (plan decision 3: "the list as written"; assumed, owner may change).**
"Folder" is where the text files go; "plural" is what a `<plurals>` of that language must contain
(`strings-check` is extended to enforce it); "address" is the glossary's proposal.

| # | Language (its own name) | Stored tag | Folder | Script and type tier (DL 13.1) | Plural quantities required | Address (proposal) | Glyphs the pixel font still lacks (the audit of ART-B decides) |
|---|---|---|---|---|---|---|---|
| 1 | English | `en` | `values/` | Latin, tier 1 | one, other | n/a | none |
| 2 | Español | `es` | `values-es` | Latin, tier 1 | one, other (many is optional) | tú | Á Í Ó Ú Ñ á í ó ú ñ ¿ ¡ |
| 3 | Français | `fr` | `values-fr` (exists) | Latin, tier 1 | one, other (many is optional) | vous as shipped (open point 10) | Æ æ Œ œ Ÿ ÿ « » |
| 4 | Deutsch | `de` | `values-de` | Latin, tier 1 | one, other | du | Ä ä ß |
| 5 | Português | `pt` | `values-pt` (written for Brazil; open point 11) | Latin, tier 1 | one, other (the app's `PluralRules` already gives one for 0 and 1) | você | Á À Â Ã Õ Í Ó Ô Ú and their lower case |
| 6 | Italiano | `it` | `values-it` | Latin, tier 1 | one, other (many is optional) | tu | À È É Ì Ò Ó Ù and lower case |
| 7 | Türkçe | `tr` | `values-tr` (exists) | Latin, tier 1 | one, other | sen as shipped | none (Ç Ğ İ Ö Ş Ü and ı exist) |
| 8 | Русский | `ru` | `values-ru` | **Cyrillic, tier 1 (new)** | one, few, many, other | ты | all 66 letters (33 capitals, 33 small, including Ё ё); 11 capitals and 7 small ones can reuse a Latin shape |
| 9 | Bahasa Indonesia | `id` | `values-in` proposed (E-B2 decides between `in`, `id`, `b+id`) | Latin, tier 1 | other only | kamu | none |
| 10 | Kurdî (Kurmancî) | `ku` | `values-ku` proposed (Latin script; Sorani, Arabic script, is Wave 12) | Latin, tier 1 | one, other | tu | none (Ê Î Û Ç Ş exist) |

Scripts: all ten are written left to right with Latin or Cyrillic letters, so the app's own pixel font covers
every title (tier 1) once ART-B has drawn the glyphs; body text stays in the system's font as it always has. No
font is downloaded, so `NOTICE` gets nothing for fonts. Right-to-left languages, East Asian and Indic scripts,
Greek, packs from a file and the workshop are Wave 12 (plan l.861 to 862).

| Files (LANG, stage 1) | Notes |
|---|---|
| J/ui/text/`Languages.kt` (new, pure: `LangTable`, the ten entries with tag, own name, folder, plural set, `reviewed: Boolean`, an `alphabet` string that ART-B's audit reads, `LangSelect.systemFirst(locale)`) | the "machine translated" mark is `reviewed == false`; all nine non-English languages start unreviewed, Turkish flips after the owner's reading (a one-line edit by the lead) |
| J/ui/text/`LanguagePrefs.kt` (new: prefs `language_settings`, keys `answer`, `dictation`; the interface language stays `Appearance.language`, so APPEAR-B edits no field for it), `AnswerLanguage.kt` (pure: the prompt line for "as I write" or a fixed language), `DictationLanguage.kt` (pure: tag to the recogniser's tag; `ku` has no assured recogniser, falls back to the system's) | |
| J/ui/settings/`LanguageSection.kt` (new: `LanguageSection()`, `languageName(code)`, `LanguageChoices(selected, onPick, systemFirst)`, which the Welcome's first step reuses) | the old `LanguageSection` and `languageName` in `AppearanceSection.kt` and `SettingsScreen.kt` are deleted by APPEAR-B in the same slice (a duplicate would not compile) |
| J/data/`BotPresets.kt` (the `LANGUAGE_RULE` line), J/data/`Transcript.kt` (`STYLE_RULE` and a settable `answerLanguage: () -> String` provider, default empty, set by one init line that INTEGRATE-B writes in `PocketApp.kt`; no `ChatEngine.kt` edit) | changing the system text mid-chat changes Hermes's derived session id (AGENTS.md, "Hard-won facts"): experiment E-B4 and open point 6 |
| J/ui/chat/`ChatInputArea.kt` (the one block `sttLocaleTag`, today `tr`, `fr`, `en`, else the phone's) | |
| R/values/`strings_lang.xml` (`lang_`) | picker rows, the machine-translated mark, the three settings, the answer chips |
| `docs/i18n/README.md`, `docs/i18n/<code>.md` for the ten (the glossary: how Canvas, Space, Fireside, ledger, woodpile, hearth, Pêt, Agirî, Hermes are said; the address form; tone; the room rules), `docs/i18n/pet-voice.md` (a description of Pêt's character, from which each language's Pêt lines are written afresh, DL 13.5), `docs/i18n/tr-review.md` (the sixty most visible texts, Turkish) and optionally `docs/i18n/ku-review.md` | stage 1 writes the glossaries before any translation; the review pages are generated from the final texts in stage 4 |
| tools/`strings-check.ps1` (the plural check, the ten, the work list), tools/`strings-kinds.txt`, tools/`make-test-language.ps1` (the stretched language; unchanged unless the check needs it), tools/`i18n-worklist.ps1` (new: per language and file, the keys still missing, with their notes) | these three tool files are modified in the working tree now by the Wave 0 defect fixes: LANG starts after that work is committed |
| T/text/`LangTableTest`, `AnswerLanguageTest`, `DictationLanguageTest`, `LanguagePrefsTest`, `PluralRulesTest` (extended to the ten), `PluralsCoverageTest` (new: every `<plurals>` of every shipped language has the quantities of its row), `ResourceParityTest` (its loop is `listOf("tr", "fr")` today; it covers every shipped folder after 1b) | |

**Stage 4, the translation (still LANG).** Inputs: the English files frozen across 1a and 1b, the notes, the
glossaries, `strings-check` as the work list. Size today: 1,115 keys in 12 files (`strings.xml` 112, `strings_chat`
286, `strings_control` 387, `strings_engine` 138, `strings_routines` 111 and seven small files); 1a adds an estimated
300 to 450 and 1b about 250; the count is read from `strings-check` when English freezes (open point 12). Every key
goes into seven new languages and the 1b keys into Turkish and French. Work: **seven translators** (Sonnet, one per
new language, each walking all files in three passes: the screens of 1a and 1b first, which sets the tone, then chat
and routines, then settings and engine; with the glossary, each key's note and everything already translated),
**two supplements** (Sonnet: the 1b keys into `tr` and `fr`), a separate pass for **Pêt's lines** in each language
(the eight `pet_quip_*` and the home quips, written from `docs/i18n/pet-voice.md`, not translated), **nine readers**
(Opus, one per non-English language: length against the rooms of DL 13.2, placeholders, tone and glossary; report
only), and Haiku for the mechanical checks. Outputs: `R/values-es|de|pt|it|ru|<in or id>|ku/*.xml` (new), the 1b
additions in `values-tr`, `values-fr`, the nine `A/whatsnew/0.3.<code>.md`, the review pages, the regenerated
stretched language `app/src/debug/res/values-en-rXZ/`. Checks: `strings-check.ps1` exit 0 (2 means a ratchet got
worse), `make-test-language.ps1 -Check` and `-SelfTest`, `PluralsCoverageTest`.

**Who translates, who checks.** Sonnet translates; Opus reads; Haiku runs the scripts; the owner reads the Turkish
page (about ten minutes) and, if wished, the Kurmanji one. A language nobody who speaks it has read stays marked
"machine translated" in the picker. Nothing here claims a human author.

### 4.7 ART-B: glyphs, tab icons, welcome pictures, the missing empty states

| Files | Notes |
|---|---|
| J/ui/theme/`PixelFont.kt` (the only 1b editor) | about 130 glyphs: the Latin accents are cheap (`accent(c, base, mark, startRow)` already composes a base with an accent mark); the Cyrillic letters need real design (about 48 new shapes plus aliases for the shapes that equal a Latin letter); `« »` and `œ` are mapped in `MAP` if no glyph is drawn; every existing glyph stays byte for byte equal (`pixelfont-v1.golden`) |
| T/ui/theme/`PixelFontGoldenTest.kt` + a new golden `T/ui/theme/pixelfont-b.golden` for the new glyphs only; T/ui/theme/`PixelFontCoverageTest.kt` (new: every character of every `LangTable.alphabet` has a glyph or a `MAP` entry; a second part reads `values-*/strings*.xml` with `Assume` and checks the texts of kinds that draw in the pixel font: title, tab, chip) | the second part runs once the translations exist (stage 4); until then the alphabets carry it |
| T/ui/theme/`PixelFontSheetDump.kt` (new, env `FONT_SHEET_DIR`, writes the font as a contact sheet PNG; run once after INTEGRATE-B compiles, reviewed by LOOK-B) | the same pattern as 1a's `SceneStillsDump` |
| A/pixel/`tabicons.pxl` (new: `tabicon_home`, `tabicon_chats`, `tabicon_library`, `tabicon_bots`, `tabicon_agent`; 12 by 12, 4 frames each: the rest picture (equal to the static icon) and three more of 90 ms; drawn by index with `playing = false`), A/pixel/`icons_b.pxl` (new: `icon_library`), A/pixel/`illustrations_b.pxl` (new: the three guide pictures `wel_key_1` to `wel_key_3`, a Welcome picture per step if wanted, `tile_library`, and every `empty_*` / `tile_*` that 1a's LOOK cut; 24 by 24 or 32 by 32 as 1a's art rules say, at most 7 to 10 colours, plum outline visible on dark and light) | new sprites live in new files (1a gotcha: a duplicate name keeps the first, a bad file is skipped whole); `icons.pxl`, `illustrations.pxl`, `mascot.pxl`, `scene.pxl`, `fire.pxl` stay byte-identical |
| tools/art/`craft_b.py` (new generator for the three new files, with `--check` and `--selftest`; it imports the helpers of `pxl.py` and does not edit `craft_art.py`, which 1a is editing in `dist/wt-w1b`), tools/art/`README.md` (its rows) | avoids a merge fight with 1a's builders |
| R/drawable/`ic_splash_flame.xml` | the splash's icon: a vector made from the fire's pixels with `tools/pxl-to-vector.ps1`; inside the circle that Android masks (about two thirds of 288 dp); START references it by name |

Review: contact sheets of the glyphs, the tab icon frames and the Welcome pictures, rendered by DATA-B and judged by
LOOK-B (a sprite that is not clearly better than what it replaces is cut; the same cut rule as 1a 7.2).

### 4.8 APPEAR-B: the Appearance parts that were left, and the Settings screen as a pushed screen

| Files | Notes |
|---|---|
| J/ui/theme/`Appearance.kt` | `AccentId.WALLPAPER` appended last (stored by name under the existing key `accent`; the previous build reads an unknown name as the default, so the rollback APK is unaffected); no other field (the language fields live in LANG's `LanguagePrefs`) |
| J/ui/theme/`Palettes.kt`, `PocketTheme.kt` (the line that builds `colors`), J/ui/theme/`SystemAccent.kt` (new) | the accent from `android.R.color.system_accent1_*` on Android 12 and later (`Build.VERSION.SDK_INT >= 31`); below that the chip is not shown and the stored value falls back to the theme's own; a contrast guard keeps text on the accent readable in every theme (`PaletteContrastTest` must not get worse: its 215 known failures are a ratchet) |
| J/ui/settings/`AppearanceSection.kt` | the wallpaper chip in the accent row; the Pêt-in-chats row composed (1a wrote it as a private composable, tag `appearance.pet`, reads `Appearance.petInChats`); deletes the old `LanguageSection` and `languageName` |
| J/ui/settings/`SettingsScreen.kt` | Settings is a pushed screen: the back arrow is always present (today `onBack` exists only when a `section` is given); the summary line for Language uses LANG's `languageName`; the section list otherwise unchanged |
| J/voice/`SceneSounds.kt` | extra B27: the crackle loop's level follows `FireFeed.level()` |
| R/values/`strings_appearance_b.xml` (`apr_`) | `apr_wallpaper_title`, `apr_wallpaper_sub` and the like; 1a's `strings_appearance.xml` is not touched |
| T/ui/theme/`AccentTest`, `AppearanceModelTest` (additions), T/voice/`SoundCuesTest` (extra) | |

## 5. Shared and single-owner files (one writer each)

| File | Owner in 1b | Others' needs |
|---|---|---|
| `J/ui/Nav.kt`, `ui/nav/*`, `J/ui/AppRoot.kt`, `J/DebugLinks.kt` | SHELL | everyone asks through section 6 |
| `AndroidManifest.xml` | nobody: **no change in 1b** (no permission, no component; the splash needs only a theme, the Library uses the FileProvider that exists) | if an area finds it needs one, it is a finding for the lead, not an edit |
| `app/build.gradle.kts` | INTEGRATE-B: `baseVersionName` "0.2" becomes "0.3" (line 50 today), nothing else; no library is added | |
| `J/PocketApp.kt` | INTEGRATE-B: one init line for `LanguagePrefs` (w0 3.13: an area that needs an init line writes `slot: <AREA>: <line>` under REQUESTS) | LANG |
| `J/ui/pixel/HearthScene.kt` | WELCOME-UI (one additive parameter, section 4.5) | BARS and HOME need nothing from it |
| `J/ui/home/*` | BARS (the gear, tile targets, one `FireAnchor.publish` call) | START reads `FireAnchor` only |
| `R/values/strings_chat.xml` and its `-tr`, `-fr` copies | SHELL (five key deletions) | |
| every other `R/values/strings_*.xml` | its area (4.1 to 4.8); a file is never shared; the nine new `values-<code>/` folders and the 1b keys in `values-tr`, `values-fr`: LANG stage 4 | |
| `tools/art/craft_b.py`, `tools/art/README.md` | ART-B | |
| `tools/strings-check.ps1`, `strings-kinds.txt`, `make-test-language.ps1`, `i18n-worklist.ps1` | LANG | |
| `T/ScenarioFilesTest.kt` (the known tags), `tools/size-budget.txt`, `.github/workflows/build.yml`, `NOTICE`, `tools/lint-ui-baseline.json`, `tools/strings-check.baseline.json`, `app/src/debug/res/values-en-rXZ/` | INTEGRATE-B (baselines never rise: 1a ruling 2) | |
| `tools/scenarios/w1b-*.json`, the `tab.settings` rewrites in existing scenarios and `tools/phone-lib.ps1`, `safeNames` of `ScenarioFilesTest` | the lead's robot pass after INTEGRATE-B (as 1a 2.9) | |
| `AGENTS.md`, `docs/waves/PROGRESS.md`, `docs/APP_OVERVIEW.md`, `README.md` | the lead; `docs/phase4-plan.md` and `docs/design-language.md` are never edited | |

**What 1b does not touch** (as in 1a 2.10): `J/ui/home/HomeScrim.kt`, `J/data/BackupManager.kt`, `ui/pixel/Mascot.kt`,
`Avatars.kt`, `Fire.kt`, every 1a-generated `.pxl`, `A/pixel/icons.pxl`, `rootfs/*`, `runtime/pocketagent-proot.sh`
(the launcher stays the one launcher), the Hermes configuration generators (`HermesConfig.kt`, `ProviderConfig.kt`),
`LegacyHearthReference.kt` and `SceneRendererParityTest.kt`.

## 6. The seams: the names that cross areas (to be spelled exactly in the contract)

| Provider | Symbol | Consumer |
|---|---|---|
| SHELL | `Route.Library(shelf)`, `Route.Welcome`, `Route.WhatsNew`; `NavTags.TAB_LIBRARY`; `shell_gear_cd` | LIBRARY, WELCOME, START, BARS |
| SHELL | `PocketBottomBar(route, nav, onHeight)` (internal to the shell) | AppRoot only |
| LIBRARY | `@Composable fun LibraryScreen(shelf: String?)`, `LibraryShelf.FILES = "files"`, `LibraryShelf.SAVED = "saved"` | SHELL, BARS (Home tile) |
| WELCOME | `FirstRun.initialRoutes(context): List<Route>` (synchronous, a preferences read and two file checks), `FirstRunDebug.pin(...)`, `@Composable fun WelcomeScreen()`, `WelcomeChrome.iconsDark: StateFlow<Boolean>` | SHELL, SHELL's `DebugLinks` |
| WELCOME | `RuntimeManager.prepareRootfs()`, `Settings.firstRun` | WELCOME-UI |
| START | `@Composable fun WhatsNewHost()`, `@Composable fun WhatsNewScreen()`, `WhatsNewDebug.pin(...)`, `WhatsNewStore.markSeen(...)` | SHELL, WELCOME (end of the Welcome sets `seen`), SHELL's `DebugLinks` |
| START | `FireAnchor.publish(xPx, yPx, sizePx)`, `FireAnchor.target` | BARS (writes), START's `MainActivity` (reads) |
| BARS | `PocketTopBar(..., onSettings = ...)`, `EmptyState(..., art = ...)`, tags `top.gear`, `home.gear` | every screen with a top bar, ART-B's sprite names |
| LANG | `LangTable`, `LanguageChoices(...)`, `languageName(code)`, `LanguagePrefs`, `Transcript.answerLanguage` | WELCOME-UI (step one), APPEAR-B (summary), INTEGRATE-B (init line) |
| ART-B | sprite names `tabicon_*`, `icon_library`, `wel_key_1..3`, `tile_library`, the `empty_*` and `tile_*` that 1a cut, drawable `ic_splash_flame` | SHELL, WELCOME-UI, BARS, LIBRARY, START |
| ART-B | the glyph set (`PixelFont`) | every title in every language |
| APPEAR-B | `AccentId.WALLPAPER` | LANG's and WELCOME's pictures need nothing |
| 1a | `FireFeed.level()`, `FireTags.HIT`, `FireSwitch`, `PetPeek(enabled, working)`, `PetController` and `rememberPetController()`, `Appearance.petInChatsVisible`, `HomeTags`, `HomeHero`, `HearthScene` (3.8), `SceneCalendar.LISTS`, `DayListTexts`, `SceneDaysStore.setList`, `WeatherStore.available`, `SkyFrames.build(SkyInputs)`, `HomeSky.iconsDark`, `AppearanceStore.pin`, the `DebugRequest` order of 3.9 | everyone, as the 1a registry spells them |

## 7. What is explicitly NOT in 1b

Everything of 1a (scene, sky, weather, fire, Pêt, Home 2.0, appearance and sound parts that 1a took) and, of the
plan's other waves: the Library's later shelves (Made, Pictures, Reading list, Inbox, Recordings, Cards: Waves 5 to
8); spaces as a filter (Wave 2); chat archive, labels, search and swipe archive (Wave 2); the Agent tab as a list of
six rooms with Abilities, Connections and Safety and privacy (Waves 3, 4, 11: in 1b the Agent tab is today's Hermes
screen renamed); rows on Home (zero registered); the widget (Wave 10); updates from inside the app; a new signing key.
Carried over from 1a and still open, each with its decider:

| Item | Why it is not in 1b | Decider |
|---|---|---|
| "Allow the phone's approximate location" as a way to pick the place (plan l.790 to 791) | needs a permission (`ACCESS_COARSE_LOCATION`), a new `PlaceChoice` (1a's registry has only Auto and Picked) and a runtime flow; 1a ruled it "a later wave"; the weather it feeds is dark | the lead; assumed: not in 1b, owner may change (open point 8) |
| The paper hat, the `app` day list and the user-day rows (1a ruling R15, open point 11) | belong to Pêt's layer and the scene (1a code) | follow-up of 1a, not assigned to a 1b area |
| `BackupCore.PREF_SCHEMA` for the new preferences of 1a and 1b (`language_settings`, `whatsnew`, `scene_days`, `place`, `weather`, `haptics`) | `BackupManager.kt` stays untouched (1a follow-up 9); a restore returns them to defaults, which is harmless; `settings` (the Welcome's flag) is not in the schema either, and the existing-install rule makes a restored phone count as existing | the wave that next edits `BackupManager` |
| The weather switch and its contact address (plan decision 6) | `BuildConfig.WEATHER_CONTACT` stays empty; the Welcome's weather question is composed only while `WeatherStore.available` | the owner |
| Speech out in the new languages (Supertonic detection knows English, French and Turkish only) and listening beyond the recogniser's tag | Wave 8 ("voices for many languages"); 1b only sets the dictation tag | Wave 8 |
| Android's own per-app language list (`android:localeConfig`) | not in the plan, and the app has its own picker | none |
| Right-to-left languages, East Asian and Indic scripts, Greek, eight more languages, language packs and the workshop | Wave 12 | |
| Edits to `docs/phase4-plan.md` and `docs/design-language.md` | never | |

## 8. What must be tried first, and what needs the phone

Plan rule 2: anything that depends on undocumented behaviour is tried for at most an hour before a line of
contract is written, and every experiment has a fallback. The phone is away until the owner says so
(`docs/waves/PROGRESS.md`, 19:45 entry), and **nothing experimental goes on the owner's package**: every phone
experiment runs on the test twin (`dev.pocketagent.app.debug`), where `pm clear` is allowed.

| # | Question | Needs | How, in one line | Passes when | Otherwise (fallback) |
|---|---|---|---|---|---|
| S8 | Can the system's splash hand its icon over at target 28? (plan l.2953) | the twin, the phone | `windowSplashScreenAnimatedIcon` in `values-v31`, an exit listener that moves the icon's view to a rectangle; also: does it show for a start by `am start` and from the launcher; for each of the six launcher aliases (the alias's own icon or the theme's); can the splash be held until Home's first frame without a library (a pre-draw listener); frame times with `phone-perf` cold start | the flame is shown, the listener receives the view, the handoff costs no frame over 16.7 ms and cold start to first frame of Home stays under 700 ms (DL 11) | a plain fade into the scene (the core of B3 ships either way) |
| E-B2 | Which resource folders do Indonesian and Kurmanji need on Android 17, and what do `Locale.forLanguageTag("id")`, `Locale.getLanguage()` and `resolveAppLocale` return? | JVM test, then the twin | a build with one throwaway string in `values-in`, `values-id` and `values-ku`, the debug link `lang=id` and `lang=ku`, a `uiautomator dump`; reading Android's resource-qualifier documentation (lookup L2) | the string is found for both languages with the folder chosen | the language is offered under another folder name or tag that does work; `values-b+...` |
| E-B3 | Are `android.R.color.system_accent1_*` readable at target 28, and do they change with the wallpaper? | the twin | read the colours in a debug build with the wallpaper changed | the colour reads and differs between two wallpapers | the chip is not offered (B22 becomes "not shipped", reported) |
| E-B4 | Does a changed system message change the session id Hermes derives (AGENTS.md: sha256 of system plus first user message), and does a second system message count? | no phone for the first half (read the Hermes source in `dist/hermes-src`, lookup L3); the practice model on the twin for the second (zero tokens) | send the same chat with two different system lines and read `/api/sessions` | the answer-language line can be added without splitting the chat's session, or the cost is known | the line is appended where it does not change the id; or a change of the answer language says that the next message starts a new session |
| E-B5 | Can the guided chat carry the practice keywords (`RUN_TOOL` and the like) without showing them? | JVM (does `parseMarkdown` drop an HTML comment?) | a unit test, then one look on the twin | the keyword is invisible in the bubble | the keywords are shown in the user's bubble, small and muted, and the plan's "a question, a tool step and an answer" is still met |
| L1 | The key page and terms | no phone (the lead reads the provider's own pages on the day) | the address of the Google AI Studio key page, the provider's terms page for the free tier, the countries where a free key is offered | the wording of B13 is written from what is read, never from memory | the sentence says only that the terms are one tap away, and links them |
| L2, L3 | Android's resource-qualifier table for `id`/`in`/`ku`; Hermes's session-id derivation | no phone | Haiku reads the two sources | | |

Two things the plan lists as proofs that are really measurements, and have a fallback written here: the Welcome's
smoothness while the Linux image unpacks (the scene at 30 fps and a minute of disk work; fallback: the scene drops to
10 fps while `Extracting`, as `Pocket.motion.sceneFps` already allows) and the cost of five more languages in the
APK (the plan gives languages 2 MB; fallback: the table of untranslated engine texts of the rarely seen screens falls
back to English and is reported).

## 9. The slices

As in 1a (`docs/waves/w1/LEAD-DECISIONS.md` and 1a contract 11 item 5): the wave is built in small closed slices
because the work may stop at any moment. Each slice ends with a green build of the three variants (twin,
owner-debug, optimised), green unit tests and a commit; the registry holds across slices; a symbol whose provider is
in a later slice is simply not called yet. **Worktree:** a new one, `dist/wt-wave1b` on branch `wave1b` (not
`dist/wt-w1b`, which is 1a's slice B). Slices A and B touch nothing 1a touches and can start from the work branch's
tip now; slices C onward start after 1a is merged into the work branch.

| Slice | Contents | Areas | Needs | Ends with |
|---|---|---|---|---|
| **0 (lead)** | experiments E-B2 to E-B5 that need no phone, lookups L1 to L3, the area drafts, the contract and its review | | nothing | the contract |
| **A: pure, new files only** | `FirstRun`, `WelcomeFlow`, `KeyGuide`, `GuidedChat`; `WhatsNewLogic`; `LibraryIndex`; `TabModel`; `Languages`, `AnswerLanguage`, `DictationLanguage`, `LanguageSettings` codec; `EmberLevel`; their tests | WELCOME-CORE, START, LIBRARY, SHELL, LANG, BARS | none of 1a | compile and `-Test` green |
| **B: art, glossaries, tools** | the glyphs and their goldens; `tabicons.pxl`, `icons_b.pxl`, `illustrations_b.pxl`, `craft_b.py`, `ic_splash_flame.xml`; DATA-B runs the generators and renders the contact sheets; LOOK-B judges and cuts; the glossaries, `strings-check` plural check, `i18n-worklist.ps1`, `PluralRulesTest`, `PluralsCoverageTest`; E-B2 and E-B3 on the twin if the phone is back | ART-B, LANG, then DATA-B and LOOK-B | none of 1a (the three new `.pxl` names are unique) | `craft_b.py --check`, `PixelFontGoldenTest`, `strings-check` green; verdicts in `docs/waves/w1b/look/` |
| **C: shell, Library, bars, appearance** | the new tab set and `Route.Library`, `PocketBottomBar`, `LibraryScreen` with its two shelves, the gear, the fire line, `PetPeek`, `EmptyState` art, the Home edits, `AccentId.WALLPAPER`, the Pêt row, Settings as a pushed screen, the `DebugLinks` routes, the language picker and the three settings (LANG's UI together with APPEAR-B's deletion of the old section in one commit), SHELL's updates of the existing nav tests | SHELL (pass 1), LIBRARY, BARS, APPEAR-B, LANG (UI) | 1a merged (`HomeHero`, `FireFeed`, `PetPeek`, `Appearance`, the `AppRoot` lines); slice B's sprite names (a missing sprite draws nothing: every consumer has a fallback) | three builds, unit tests; pictures of the five tabs on the owner-debug build with pins only; the robot scenarios rewritten for the missing Settings tab |
| **D: the first run and the start** | `Route.Welcome`, `Route.WhatsNew` and the initial stack (SHELL pass 2), `WelcomeScreen` and its steps, `prepareRootfs`, the guided chat, the key guide, `WhatsNewHost` and the page, the splash theme, listener and `FireAnchor`; S8's result decides whether the handoff or only the fade ships | WELCOME, START, SHELL (pass 2), BARS (the `FireAnchor.publish` call, if not done in C) | slice C; S8 (twin); lookup L1 | three builds; the robot walks the Welcome on a wiped twin on the practice model (T) |
| **E: the ten languages** | English freeze across 1a and 1b, then LANG stage 4: seven translators, two supplements, the Pêt-lines pass, nine readers, Haiku's checks, the review pages for the owner | LANG | C and D (the texts of 1b), 1a's English frozen, the glossaries of B | `strings-check` exit 0, stretched language regenerated, `PluralsCoverageTest`, `PixelFontCoverageTest` second part green |
| **F: integrate, review, prove** | INTEGRATE-B's edits (version 0.3, the init line, CI step for `craft_b.py --check` and `strings-check`, size budget, `ScenarioFilesTest` tags, the existing-install call, the heat-haze default after its measurement), the final review by lens (Opus), the scenarios, the proof of section 10, the owner's Turkish page | INTEGRATE-B, the lead | all | commit and push (CI prerelease); `AGENTS.md`, `PROGRESS.md`, `APP_OVERVIEW.md`, `README.md` updated |

Stages inside the slices are the same as 1a's: stage 1 the eight areas in parallel, each followed by its own Opus
review and one round of fixes; stage 2 DATA-B (mechanical: runs the generators and the contact sheets; **no
downloads in 1b**, no data file, no font, no sound); stage 3 LOOK-B (sheets and verdicts, cut rule of 1a 7.2); stage 4
TRANSLATE (LANG); stage 5 INTEGRATE-B; then the final review. Cut rule: an area that does not finish cuts only its
own extras in the order of section 11 and keeps every symbol that section 6 names (a cut symbol keeps an empty body).

## 10. How "Proven when" is met (categories as in 1a: P owner-debug with pins and pictures only, `"safe": true`; T needs the twin; U unit test; E the owner's eyes)

| # | Item | Cat | How |
|---|---|---|---|
| 1 | The robot walks the Welcome from a wiped installation to the first answer on the practice model (plan l.908) | T | `pm clear` of the **twin only** (the Linux image unpacks again, about a minute), scenario `w1b-welcome` (not `safe`); ends with the guided chat's answer; the practice chat deleted afterwards; the Welcome never shows again on the second start |
| 2 | The five main screens (Home, Chats, Library, Bots, Agent) photographed in each of the ten languages, nothing missing and nothing cut (plan l.915 to 916) | P | scenario `w1b-languages` loops the debug link `lang` over the ten tags (in memory, so `safe`), takes the five screens, reads the cut probe (`AgiriCut` in the log) and runs `strings-check.ps1 -ScanDump` on each dump; plus the stretched language (`en-XZ`), the mirrored layout (`rtl 1`) and text at 2.0 for the new screens (font scale needs the lead's `-AllowSystemSettings`, as in 1a P18) |
| 3 | Plurals and dates in each of the ten (plan l.877 to 879) | U, P | `PluralsCoverageTest`, `PluralRulesTest`; the pictures of item 2 include the chat list's times and counts |
| 4 | The Welcome never shows to an existing installation, and never again after it is finished or skipped | U, P | `FirstRunRulesTest`; on the owner-debug build the app starts on Home and `shared_prefs/settings.xml` has no `firstRun` entry (read only) |
| 5 | Tab bar, Library, gear, fire line, Pêt's peek | U, P, T | `NavStackTest`, `NavMotionTest`, `TabModelTest`, `LibraryIndexTest`; pictures of each tab with pins; the fire line and peek while a practice answer streams (T) |
| 6 | What's new shows once after an update and never to a new user | U, T | `WhatsNewLogicTest`; the debug link `whatsnew pending` on the twin |
| 7 | The splash hands the flame to the scene, or fades | T | S8's scenario; cold start under 700 ms |
| 8 | A reviewer looked at every new sprite and glyph on a contact sheet | U | `docs/waves/w1b/look/review-ART-B.txt`, `LookReviewFilesTest` extended to the three new `.pxl` files and the font sheet |
| 9 | The owner read the Turkish page | E | `docs/i18n/tr-review.md` (waits for the owner); the correction goes into the glossary; then `reviewed` flips |
| 10 | Old data unchanged | P | `dist\owner-backup\` compared read only; the only new files on the owner's phone are preferences the previous build never opens (`language_settings`, `whatsnew`, and `settings.firstRun` only after a Welcome, which the owner never sees) |
| 11 | No new permission, no new component | U | the manifest diff of the slice is empty |
| 12 | Size | U | `tools/ci/apk-size.sh`: the growth of 1b (languages, glyph code, sprites) at most 2 MiB, recorded in `tools/size-budget.txt` by INTEGRATE-B |

New scenario files (the lead writes them after INTEGRATE-B, with the `safeNames` edit): `w1b-tabs` (safe),
`w1b-library` (safe), `w1b-languages` (safe), `w1b-welcome` (twin), `w1b-whatsnew` (twin). The existing three that tap
`tab.settings` are rewritten first.

## 11. Core and extras, and the order of cutting (first cut first)

Extras: B26 heat-haze default, B27 crackle follows the fire, the missing empty-state pictures of B24 beyond
`empty_bubble`, the Kurmanji review page, the splash handoff animation (the fade stays), `WelcomeScene`'s per-step
sky (a fixed night-to-dawn pair of frames stays), the hop arc of the ember (a slide stays), `WhatsNewDebug`.
Order: 1. B27; 2. B26; 3. the Kurmanji page; 4. the pictures of B24 other than `empty_bubble`; 5. the splash
handoff (fade); 6. the ember's arc; 7. the Welcome's lightening sky (two frames); 8. the Library's second action on a
file (open or share); 9. the Welcome's weather and days questions (they are skippable already); 10. the guided chat
(the Welcome then ends at the first hello). **Never cut:** the tab set, `Route.Library` and its two shelves, the gate
that keeps the Welcome from an existing installation, `prepareRootfs`, the three language settings, the glyphs of
the ten alphabets, the glossaries, the plural check, the stretched-language regeneration.

## 12. Decisions of plan section 13 that touch 1b (all taken as the plan's own recommendation; assumed, owner may change)

| # | Decision | Assumed | Touches |
|---|---|---|---|
| 1 | Order of the waves | As numbered (1b follows 1a). In the shorter order the tab bar and the Library move into Wave 2 and nothing else of 1b depends on it | the slices |
| 2 | Optimised build for the owner, test twin for agents | Yes (the owner's key waits); every experiment and every destructive test of 1b runs on the twin | section 8 |
| 3 | The first ten languages; the owner reads one Turkish page (and Kurmanji if wished) | The list as written; ten minutes | B16, B19 |
| 6 | The weather: offer it, and the contact address | Offered, switched off at first; the contact address stays the owner's; the Welcome asks only when the switch can exist | B14 |
| 16 | Which regional and cultural day lists exist | The first handful of 1a (none on by default); the Welcome's days question shows what 1a ships | B14 |
| 18 | 1.0 on 21 June 2027 | Yes | the pace |
| 19 | A wave may ship without its extras | Yes (section 11) | all |

Decisions 4 and 5 (taste gates: the fire, the default sky) are 1a's and not asked again here.

## 13. Open points (for the lead; each has a default so the area drafts can start)

1. **Same as the plan's wording, cut differently.** The plan's table gives ONBOARD one area and LANG one; this cut makes
   WELCOME and LANG two agents each (disjoint files) and moves the splash and "What's new" into START, the top bars
   into BARS and the Settings screen into APPEAR-B. Default: as cut here.
2. **Agent tab and route naming.** The route stays `Route.Hermes` (key `hermes`, tag `tab.hermes`); only the label
   changes to "Agent". Default: yes, so debug links, scenarios and `AppLinks` keep working.
3. **Where the Agent tab's content comes from.** Today's Hermes screen unchanged (Soul, Memory, User, Skills, Config,
   Logs, Terminal button). The six rooms of plan 4.1 arrive with Waves 3, 4 and 11. Default: as stated.
4. **`tab.settings`.** Three scenarios and `phone-lib.ps1` line 1753 use it. Default: the tag goes away and the lead
   rewrites them (Settings is opened by the debug link `route settings` or the gear's tag `top.gear`); the safe
   alternative is to keep the string `tab.settings` on the Home gear for one wave.
5. **Existing-install rule: derive or freeze.** Default: derive on every start and write only when the Welcome ends, so
   the owner's phone gets no write. The alternative (a frozen `existing` answer written once, as 1a does for the
   days) keeps the answer after a Reset of the Linux image and deleted chats; it costs one stored write on the owner's
   phone.
6. **Answer language and Hermes's session id.** Default: do not ship the fixed-answer-language chip until E-B4 says
   the system text can change without splitting the chat; "as I write" (today's behaviour) is always there.
7. **`HearthScene.skyOverride`.** One additive parameter for the Welcome's lightening sky; the file moves from 1a's
   SCENE to WELCOME-UI for 1b. Alternative: the Welcome pins a clock through `AppClock`, which is inert outside test
   builds and so impossible.
8. **Approximate location** is not built (section 7). Say if the plan's "or allow the phone's approximate location" must
   be in 1b: it adds a permission to the manifest, a `PlaceChoice.Located` to 1a's `PlaceStore` and an extra for APPEAR-B.
9. **What's new file names.** Default: `assets/whatsnew/0.3.md` (English, the plan's path) and `0.3.<code>.md` for the
   others, the page key being the first two numbers of the version name (`0.3.17-local2-test` gives `0.3`).
10. **French address form.** Shipped French says "vous" (`ctl_lang_intro`: "Choisissez..."), Turkish says "sen", and
    DL 13.5 asks for the familiar form unless a glossary says otherwise. Default: the French glossary keeps "vous"
    (changing it would touch every shipped French text); the new languages use the familiar form of the table.
11. **Portuguese and Spanish variety.** Default: Portuguese as written in Brazil, Spanish neutral (no voseo), folders
    `values-pt` and `values-es`; regional folders are Wave 12.
12. **Counts.** The key counts of section 4.6 are estimates until English freezes; if the 1a and 1b keys together pass
    about 1,900, the translators work in four passes instead of three.
13. **Picker order.** Default: "System" first, then English and the other nine in the order of plan l.859 to 860 (not
    alphabetical); the owner may prefer alphabetical by own name.
14. **Practice keywords visible?** E-B5 decides; the default fallback shows them small and muted.
15. **Welcome's practice chat afterwards.** Default: the tour's conversation is deleted when the Welcome ends and the
    practice switch is turned off; the alternative keeps it as the first chat.
16. **S8 may wait for the phone.** Slices A to C do not depend on it; D builds the fade first and the handoff only
    when S8 passes.
17. **Version label.** 1a's INTEGRATE sets whatever 1a's version label is; the 1b value of `baseVersionName` is "0.3"
    only if that does not collide with it (read `app/build.gradle.kts` at the start of slice F).
