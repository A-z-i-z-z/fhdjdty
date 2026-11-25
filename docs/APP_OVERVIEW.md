# Agirî: what the app is and what it can do

This note describes the app for someone (a person or an AI agent) who has to plan its next steps and
has never seen it. It says what exists, how it is built, what has been proven on a real phone and what
has not, and what is deliberately missing. It does not say how to work on the code: that is in
`AGENTS.md` (handoff notes, build loop, hard-won facts). The API contracts are in
`docs/phase1-contract.md` and `docs/phase3-plan.md`.

Last updated: 2026-11-25. The screen-by-screen parts describe version 0.1.31 (Phase 3); Wave 0 (version 0.2)
changed the foundations underneath and is described in "What Wave 0 added" below, which wins where the two differ.

## In one paragraph

Agirî (formerly PocketAgent) is a sideloaded Android app that runs a complete AI agent platform on the
phone itself. The agent is Nous Research's Hermes Agent, a Python program, running unmodified inside a
small Debian Linux that the app unpacks into its private storage. Only the language model is remote:
Hermes calls the model provider the user configured. Around that engine the app is a full pixel-art
chat application with its own identity: personas ("bots"), group chats, scheduled routines, voice,
attachments, full control over Hermes's soul, memory and skills, a terminal into the Linux guest, usage
tracking, backup, and Android integration. Everything ships inside one APK; nothing else has to be
installed and no server of ours is involved.

## Who it is for and the constraints that shape it

- **The owner** is one person, Kurdish, writes Turkish, is not an Android developer, and uses the app on
  a Galaxy S24 Ultra (One UI 9 / Android 17, arm64). The app is also public on GitHub.
- **$0 spend.** No paid services. Test chats use the cheapest model at low thinking effort.
- **Sideload only, on purpose.** The app targets SDK 28 so that it may execute binaries from its own data
  directory, which the Linux guest needs. It can therefore never be on Google Play. `applicationId` is
  `dev.pocketagent.app` and stays that way so updates keep the user's data.
- **No new libraries by default.** Compose, coroutines, androidx.core, org.json, commons-compress + xz
  (for unpacking) and sherpa-onnx (speech) are all there is. No Room, no navigation library, no image
  loader, no icon pack. Pixel art is drawn at runtime from text sprite files.
- **Size and cost of the engine:** the APK is about 244 MB; the unpacked Linux about 1.1 GB; Hermes
  needs 250 to 330 MB of RAM and about 10 seconds to start; first launch unpacks for about a minute.
  Every chat turn carries roughly 11,000 prompt tokens of Hermes's own system prompt and tool
  definitions before the conversation itself.
- **Privacy:** credentials are typed or imported in the app, stored in app-private files, never shown
  again, never logged (a redactor filters logs and the diagnostics report). Chats live only on the phone.

## Identity

- **Name:** Agirî. **Theme:** the hearth is the Newroz fire. The home screen is a living pixel-art scene:
  night sky over Mount Agirî and the Zagros, a stone ring with a hand-animated fire, embers, fireflies,
  shooting stars. The fire mirrors the engine: cold when Hermes is stopped, kindling while it starts,
  blazing when it runs, smoke on an error.
- **Mascot:** Pêt ("spark"), a Kurdistan newt, with moods (idle, thinking, talking, happy, sleeping,
  error, waving). Tapping the scene stokes the fire; there are a few hidden jokes.
- **Look:** six themes (Newroz, Zagros, Semender, Nergiz, Kawa, Roj; dark and light variants), accent
  colours, text size, animation level (full / reduced / off), two bubble styles, a bitmap pixel font for
  titles, six switchable launcher icons, 16 animal/object avatars, about 60 hand-drawn monochrome icons.
- **Languages of the interface:** English, Turkish, French (about 920 strings each), switchable inside
  the app independently of the phone's language. Notifications and the quick-settings tile follow it.
- **Sound and voice:** a small library of CC0 interface sounds in two packs; on-device neural speech
  (Supertonic 3 through sherpa-onnx, 10 voices, about three times faster than real time on this phone,
  a 139 MB model downloaded on request) with the system speech engine as fallback; every bot can have
  its own voice, mood and speed; replies can be read aloud automatically. Voice input uses Android's
  speech recogniser.

## How it is built (short)

- **Engine:** proot (built from source, shipped as native libraries) enters a Debian trixie arm64 rootfs
  with Hermes v0.23.3 and Python preinstalled. A foreground service keeps `hermes gateway` alive. The app
  talks to Hermes's OpenAI-compatible HTTP API on loopback, on a random port, with a per-install key.
  Hermes's `.env` and `config.yaml` are regenerated from the app's settings at every start. User state
  inside the guest (soul, memory, skills, sessions) survives a rootfs replacement.
- **App:** Kotlin + Jetpack Compose, one activity, a small hand-written navigator (since Wave 0 with ids and kept state per screen),
  about 26,000 lines in `app/src/main` before Wave 0, 804 unit tests after it. Data is plain JSON files written atomically under
  the app's private `files/chat/` (bots, conversations, one file of messages per conversation, routines,
  usage, drafts); preferences are SharedPreferences.
- **Chat engine:** the app keeps the transcript itself and sends the whole history each turn; every bot is
  a system prompt layered on top of Hermes's own. Streams are parsed for text, reasoning, tool progress,
  approval requests, status lines and token usage. Different chats can generate at the same time.
- **Build:** GitHub Actions builds rootfs, proot and APK and runs a smoke test that boots Hermes against a
  mock model (about 10 minutes); app-only changes are built locally in under a minute against the parts
  of a published build.

## What the user can do, screen by screen

### Home
The hearth scene and greeting; one button to light or put out the fire (start / stop Hermes) with plain
status text and progress; a setup prompt when credentials are missing; a "talk to Hermes" field; the
bots as cards; a button for a group chat; the four most recent chats with unread counts; tiles for the
Hermes inspector, the terminal, Routines (with the next run time) and Saved messages; today's token use.

### Chats (list)
Search over titles and message text; filter chips (all, unread, pinned, groups, 1:1, chats in a mode);
pinned chats first; unread badges; "Draft: ..." previews; a clock on routine chats; long-press for pin,
rename, mark read/unread, delete; a shortcut to Saved messages; a new-chat sheet (one bot or a group).

### A chat
- Streaming answers with Markdown: headings, lists, quotes, tables, links, code blocks with syntax
  colouring, a copy button and "Run in terminal" (with confirmation).
- The model's thinking is folded away as "Thought process"; tool calls appear as their own lines with
  status; a command Hermes wants permission for appears as an approval card (once / session / always /
  deny).
- Each reply shows time, duration and tokens in / out. Actions: speak, copy, select text, share, save
  (star), fork the chat from this point, regenerate, edit an earlier message and resend, delete.
- Input: text, voice dictation, attachments (camera, gallery image, document; images go to the model as
  images, documents as text excerpts and are also placed where Hermes's tools can read them), `@mention`
  in groups, and a slash-command palette.
- **Slash commands:** `/plan`, `/goal`, `/ultracode`, `/loop [2-5]` (multi-pass refinement), `/normal`
  (modes, shown as a banner); `/forge` (a bot designs new bots, which appear as cards), `/skills`
  (visual picker over Hermes's skills), `/remember`, `/memory`, `/find`, `/theme`, `/export`, `/clear`,
  `/help`; hidden: `/pet`, `/newroz`, `/agir`, `/roll`.
- Chat controls: operating mode, a model and thinking effort for this chat only, token totals of the
  chat. Find in chat with match count and previous / next. Export as Markdown to the share sheet or a
  file. Drafts are kept per chat and survive closing the app.
- **Wake-on-send:** when Hermes is asleep you can still type and send; the app lights the fire, shows
  "Lighting the fire…" and delivers the message when Hermes is up.
- **Group chats:** several bots in one chat; all answer in turn, or only the mentioned ones; each sees the
  others' lines with their names.
- Other apps can share text, images and files into a chat (Android share sheet), and selected text in any
  app offers "Ask Agirî".

### Saved messages
Every starred message across all chats; tapping one opens its chat at that message.

### Bots
Personas with name, avatar, colour, tagline, system prompt, optional own model and thinking effort,
voice, speaking mood and speed. Four starter bots (Hermes, Rebel, Coder, Dengbêj) and more templates
(Teacher, Translator, Critic, Planner). Create, edit, duplicate, delete, export to a file or as text,
import from a file or the clipboard. Bots can also be created by a bot (`/forge`).

### Routines
Scheduled prompts: daily at a time on chosen weekdays, repeating every N minutes/hours, or once. Each
routine has a bot, an optional model and effort, and its own chat. At the scheduled time the app wakes
up (even if it was closed), starts Hermes if needed, sends the prompt, notifies the user with the
answer and stops Hermes again if the routine was what woke it. Run now, pause, last status, next run,
ready-made ideas (morning briefing, evening journal, weekly review, a daily word in Kurdish). Runs
missed by less than six hours are caught up once.

### Hermes (inspector)
Hermes's version and state, restart, and tabs: **Soul** (`SOUL.md`), **Memory** and **You** (Hermes's two
memory files, as editable entries with their size limits), **Skills** (search, read, edit, create,
delete; about 44 come with Hermes), **Config** (the generated config, read-only), **Logs** (app, runtime,
API). The inspector reloads when Hermes changes its memory during a chat, and the chat shows a "Memory
updated" card.

### Terminal
One command at a time inside the Linux guest with the full `hermes` command line, history, quick-command
chips, copy output, cancel, ANSI colours.

### Settings
- **Provider and model:** Google AI Studio key, Vertex express mode, Vertex with a service-account file,
  Vertex with gcloud user credentials; and, new in Phase 3, OpenRouter, Anthropic, OpenAI and any
  OpenAI-compatible endpoint (base URL + key). Model field with suggestions, default thinking effort,
  region and project for Vertex. Each provider keeps its own key.
- **Usage and budget:** tokens today / last 7 days / all time, a 14-day chart, a per-model table, a daily
  token budget that warns or blocks.
- **Behaviour:** light the fire when the app opens; wake on send; reply notifications; approval
  notifications; restart once after a crash; stop Hermes after a time without activity in the
  background; notification permission status; battery-optimisation exemption.
- **Appearance**, **Voice & Sound**, **Language** (see Identity).
- **Backup and restore:** everything (chats, bots, routines, usage, drafts, appearance / voice / behaviour
  settings, optionally attachments and Hermes's soul, memory and skills) into one zip chosen with the
  system file dialog; restore replaces the data after showing what is in the file. Credentials and
  provider settings are never in a backup.
- **Runtime:** start, stop, reinstall the Linux, a self-test, a compatibility switch.
- **Diagnostics:** copy or share a full report with secrets removed. **About.**

### Outside the app
- **Notifications:** a finished reply while the chat is not on screen (tap opens the chat; reply and
  mark-as-read from the notification), a pending approval (allow once / deny, only with the phone
  unlocked), a failed turn, routine results on their own channel; the ongoing "Hermes is running"
  notification with a stop button.
- **Launcher shortcuts** (long-press the icon): new chat, routines, terminal.
- **Quick Settings tile:** shows whether the fire burns; a tap lights it or puts it out.

## What has been proven on the real phone

Proven with real chats through Vertex (cheapest model, low effort) unless noted:

- The engine: unpack, start, stop, force-stop and reopen, hours in the background, tool calls
  (Hermes ran shell commands), Hermes's own memory tool, attachments (image understood, document read),
  group chat, bot forge, slash commands and modes, fork, share target, voice synthesis in Turkish,
  voice model download, icon switching, themes, languages.
- Phase 3 (2026-11-24, build 30/31): auto-start on opening the app; wake-on-send from a stopped engine;
  reply notification and opening the chat from it; token numbers per reply (per turn, not cumulative)
  and the usage screen; find in chat; star + Saved messages and drafts surviving a force-stop; a routine
  firing exactly on time with the app process dead, answering, notifying and stopping Hermes again;
  deleting a routine removes its alarm; unread badges; backup (no credentials inside, checked) and
  restore of the same file; launcher shortcut links; "Ask Agirî" from selected text; the Quick Settings
  tile in both directions.

Built and unit-tested but **not yet tried on the phone:** the new providers with a real key (no key
available; only "the configuration Hermes needs is written correctly" is covered by tests against
Hermes's source); approval card and approval notification; inline reply from a notification; budget
warn / block; idle stop; automatic restart after a crash; bot export / import; creating and deleting a
skill; terminal colours; editing `SOUL.md` end to end; the camera shutter; spoken dictation; `/loop`;
a Linux (rootfs) update over an existing installation with user data; restoring a backup on a
different phone.

## What Wave 0 added (2026-11-25)

Wave 0, "Foundation", is the first of the fourteen waves of `docs/phase4-plan.md`. It adds almost nothing a
user sees at first glance (the screens look like build 32 apart from motion); it adds the ground the later
waves stand on. It is built and committed (`d52bf49` and later, version 0.2, CI run 16 green) and its phone
proof is done, but **the owner's app is back on the pre-wave code** because the proof found two severe defects.
The details and every agent report are in `docs/waves/w0-contract.md` (ownership, rulings) and `docs/waves/w0/`.

### What exists now

- **Three builds instead of one.** The owner's optimised build (R8, not debuggable, `dev.pocketagent.app`); a
  test twin "Agirî Test" with its own package (`dev.pocketagent.app.debug`), icons, shortcuts and data, where
  every experiment and destructive test runs; and owner-debug (the owner's package, debuggable), the interim
  build while the twin could not be installed. Version codes come from the CI run number, not from local builds.
  All are signed with the committed key until the owner makes a private one.
- **The practice model.** A scripted stand-in for the language model that runs inside the Linux (one file,
  `rootfs/smoke/mock_llm.py`, shared with CI). Switch in Settings > Runtime; a banner says it is on; zero
  tokens; it never touches the owner's provider setup or credentials; on any build but the twin it ends after
  two hours. Keywords in a message script its behaviour (run a tool, ask for approval, a long answer, a
  thought, a failure, memory, forge).
- **A phone robot and smoothness numbers.** `tools/phone-test.ps1` runs scenario files (JSON) and
  `tools/phone-perf.ps1` measures frames and cold start. Every tap and key first checks which window has the
  focus. Destructive steps run on the twin only. Debug builds accept links (action
  `dev.pocketagent.app.DEBUG`) that jump to a screen and set the clock, language, mirrored layout or test data,
  guarded by a token only `run-as` can read. Two ratchet checks (`tools/lint-ui.ps1`, `tools/strings-check.ps1`)
  only ever allow the counts to go down.
- **Interface foundations.** Design tokens, motion that follows the system's animation setting and battery
  saver, haptics, and components v2: one bottom sheet, one confirmation dialog, Pêt notes instead of Android
  toasts (on Android 13 and later the system's own "Copied" message still shows), swipe rows, loading
  skeletons. A new navigator keeps each screen's state while it is on the stack and animates shared elements
  (the avatar from list to chat). The back swipe cannot drive the animation on this phone, so the transition
  plays after the gesture.
- **The stage.** The home scene is now drawn by code free of Android classes (`ui/pixel/stage/`), pixel for
  pixel the same picture as before (a test proves it). It paints into an array on the CPU, then into a bitmap.
- **Streaming and the chat screen.** Streamed text is smoothed and its Markdown parsed incrementally; the chat
  screen was split into a state holder and four parts; tool output opens in a sheet.
- **Safety stop-gaps in the generated Hermes configuration.** The toolset for the app's API is an explicit
  list without the browser tools, Hermes's own scheduler, picture generation, text-to-speech and the
  connections set; no model call to title chats; no lazy installs; a switch for keyless web lookups; a listing
  of foreign code in `hooks/` and `plugins/`.
- **Diagnostics.** Exit reasons, an offer to send a crash report, and Export logs.
- **One function for every text** (`t()`, `tn()`, `Texts.get()`); the engine's own texts are resources; Turkish
  and French for every key; a stretched test language and right-to-left by debug link, in debug builds.

### What is proven on the phone

On the owner's phone as owner-debug, 2026-11-25 (`docs/waves/w0/phone-proof.md`): install over the old build
with the owner's data unchanged; Hermes Running again 10 s after a cold start; the focus guard and the debug
links; all 8 safe robot scenarios pass except the stretched and mirrored sweep, which fails only on text that
cannot be translated; the practice model through the interface (banner, scripted answers, tool step, thought,
failure, find, star, fork, regenerate, stop); one real turn on the cheapest model (6 s, no thought leak);
navigation, shared element and back transition. Smoothness: not better than before, debug against debug
(Home idle about 11.0 ms per frame against 9.7 ms, Home scroll worse in the tail), on a phone about 9 degrees
warmer, so a regression is neither proven nor excluded; cold start 931 ms against 1,108 ms; streaming a long
answer 21 % of frames within 8.3 ms, worst 44 ms (`docs/perf/w0.md`).

### Not proven

The optimised build on a phone (experiment S2, then S4 and S7); the test twin has been installed but not yet
used for a proof; the approval notification and the choices Allow once, this session and Always; a
back-to-back smoothness repeat on a cool phone; streaming before the wave; neural voice, backup and restore and
the icon switch on the optimised build.

### Known defects (fixes in progress, none proven yet)

1. Every bottom sheet (new chat, chat controls, tool output, message menus) stays invisible at the phone's
   normal animation speed and its invisible scrim eats input until Back. (Severe.)
2. An approval card cannot be answered: Hermes's session authority wants `execution_generation` in the answer
   and the app does not send it (HTTP 400); the chat then stays blocked for minutes. (Severe.)
3. Dialogs and sheets show Turkish in an English interface (the phone's system language leaks in).
4. The stretched test language's app name leaks into the normal About row of debug builds.
5. A failed turn is shown as the raw stream chunk.
6. The About mascot and the sheet scrim have no spoken label; the tool-output sheet shows the command but not
   its output; practice tokens appear to count in the owner's usage totals.
7. In a mirrored layout numbers, paths and the terminal are reordered (a list for the language wave).

### Cut on purpose, and deviations

Cut because nothing uses it yet: the baseline profile, the ember arc between tabs, language packs from a
file, the twin-only robot scenarios and perf scenarios P2 and P3, the memory phases. Deviations from the plan:
no private signing key yet; the Linux image is unchanged; the platform's formatters for dates, numbers and
money wait for Wave 1; capitals made in code stay until Wave 1.

## What does not exist (on purpose or not yet)

- No Google Play build, no iOS, no tablet layout, no landscape design.
- Only Hermes; the name of the project anticipated more agent platforms, none is integrated.
- No cloud of any kind: no account, no sync between devices, no web UI, no remote access to the phone's
  agent. Backup is a manual file.
- No home-screen widget. No in-app update check (updates are APKs from the GitHub releases page).
- No first-run wizard: a new user sees the home screen with a "one more step" card that leads to the
  provider settings.
- No Kurdish (Kurmanji / Sorani) interface, although the whole identity is Kurdish; right-to-left layout has been
  looked at only through the debug link (Wave 0), and showed bugs (see below).
- Chat rendering: no images or files produced by a bot shown inline, no LaTeX, no diagrams. No
  multi-select or archive or folders in the chat list.
- Hermes features with no interface in the app: Hermes's own scheduler (cron), toolset on/off switches,
  MCP servers, messaging gateways (Telegram and the like), sub-agents, sessions stored by Hermes.
  They are reachable only through the terminal.
- Usage is counted in tokens, not money; there is no price table.
- Speech: synthesis is on-device, but dictation depends on the phone's recogniser; no hands-free
  conversation mode, no wake word.
- No on-device language model: the model is always remote.
- Accessibility has had care (roles, descriptions) but no audit with a screen reader.
- Automated tests cover logic only; there are no UI tests. Since Wave 0 a phone robot drives the app
  through adb (see below), but most checks are still read by a person or an agent.
- No lasting place for the agent's own files: what it writes outside Hermes's state folders (for
  example in `/root`) is deleted when an update replaces the Linux image.
- The build the owner runs is still a debug build; an optimised build exists since Wave 0 but has not been
  tried on a phone, and smoothness has only been measured on debug builds (see below, and
  `docs/perf/w0.md`).

A plan that answers this list, and goes far beyond it, was written on 2026-11-24 and is **not built**:
`docs/phase4-plan.md` (second edition, with the findings of two independent reviews worked in on
2026-11-25: fourteen waves to version 1.0, among them a creative workspace with documents, slides
and pictures, research with sources, voice, phone powers and eighteen languages; every wave names
its core and its extras) with `docs/design-language.md` (how it should look, move and sound).

## Where things are

| Area | Location |
|---|---|
| Handoff notes, build and test loop, hard-won facts | `AGENTS.md` |
| User-facing readme | `README.md` |
| Contracts between the parts of the app | `docs/phase1-contract.md`, `docs/phase3-plan.md`, `docs/waves/w0-contract.md` |
| The plan for the road to 1.0 (not built) and the design language | `docs/phase4-plan.md`, `docs/design-language.md` |
| App source | `app/src/main/java/dev/pocketagent/app/` (`runtime`, `service`, `api`, `data`, `routines`, `hermes`, `voice`, `diag`, `ui/*`) |
| Pixel art sources | `app/src/main/assets/pixel/*.pxl` |
| Strings (en / tr / fr) | `app/src/main/res/values*/strings*.xml` |
| The Linux guest and the launcher | `rootfs/`, `proot/`, `runtime/pocketagent-proot.sh` |
| CI | `.github/workflows/build.yml` |
| Progress of the road to 1.0, the Wave 0 proof, smoothness numbers | `docs/waves/PROGRESS.md`, `docs/waves/w0/phone-proof.md`, `docs/perf/w0.md` |
| Phone robot and checks | `tools/phone-test.ps1`, `tools/phone-perf.ps1`, `tools/scenarios/` |
