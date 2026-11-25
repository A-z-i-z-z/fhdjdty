# Agirî (formerly PocketAgent): notes for whoever picks this up next

This file is the handoff note for any AI agent (or person) continuing the project. Read it before
touching anything. **Keep it current:** at the end of a working session update "Current status",
and add to "Hard-won facts" whenever something cost real time to find out. `README.md` is the
user-facing description; this file is the working memory.

Last updated: 2026-11-25

## What this is

A sideloaded Android app that runs agent platforms fully on the phone, with only the LLM remote.
First target: Nous Research's Hermes Agent. More agents may follow. Confirmed official app name:
**"Agirî"**, keeping `applicationId = "dev.pocketagent.app"` intact so APK updates preserve
credentials, chats, and extracted rootfs on existing installs.

Target device: Galaxy S24 Ultra, One UI 9 / Android 17 (SDK 37), arm64, 4 KB pages.

## The owner and how they want to work

- Writes in Turkish and is not an Android developer. Answer in plain Turkish, no jargon, lead with
  what works and what does not.
- Wants the lead agent to orchestrate rather than write code itself: a mid-size model writes code,
  a small model does research and lookups, a strong model reviews every change before it is pushed.
  Diagnosis on the phone (adb, logs, throwaway experiments) is done directly.
- Wants $0 spend on their own money. Do not enable paid services, upgrade trials or raise spend caps.
- Wants a global app: English first, many languages, and the Kurdish identity not overdone (said
  2026-11-24). New features, waves and screens get plain English names. What already shipped (the name
  Agirî, Pêt, the hearth, the six theme names) stays; days and scenes of a culture are options a user
  switches on. Expects the app to match the leading assistants feature for feature and add its own.
- Never ask for secrets in chat and never print them. Credentials are entered or imported in the
  app. Do not put account names, project ids, e-mail addresses or device serials in this repo: it
  is public.

## Current status

- Branch `ccr-bb6d7bcc-9eojyu` is the work branch. `main` holds only the initial commit. Other
  branches and the `yerel-latest` release belong to unrelated projects: leave them alone.
- **Phase 0 is proven on the real phone (2026-11-23).** With a local build of commit `4e9611c`:
  the app extracts the rootfs (about 60 s), starts Hermes under proot, reaches "Running" about 10 s
  after launch, survives force-stop and reopen, and a real chat through Vertex
  (`google/gemini-4-neon`, region `global`, gcloud user credentials) answered in about 12 s.
  Hermes sits at roughly 250 to 300 MB of RAM.
- Two device-only bugs were found and fixed on the way (both invisible to CI at the time):
  the `=` in Android's install path (build 7, see "Hard-won facts") and a crash at every cold
  start once the rootfs was installed (build 8, init-order NPE in `RuntimeManager`).
- The owner's Vertex credentials are imported in the app on the phone; nothing about them is in
  this repo.
- Also verified on the phone with later local builds: the Gemini 4 model chips, the "Thinking
  effort" setting (written to Hermes's `config.yaml` as `agent.reasoning_effort`), and a tool call
  through the chat (Hermes ran `uname -a` with its terminal tool, 2 API calls, 10 s total with
  `google/gemini-4-hydrogen` at low effort). Not confirmed: that the effort level reaches Google
  on the wire (only that the config file is written correctly).
- **The owner wants test chats run with Gemini 4 Hydrogen at low effort** (cheapest). Keep the
  phone's saved setup on that unless they say otherwise.

- CI build `phase0-9` (commit `b048016`) is green, including the launcher test under mksh and
  the `studio-effort` smoke fixture.
- **Phase 1 + 2 first cut written (2026-11-23, version 0.1.N).** The owner asked, before
  leaving for an hour, for "the full Hermes experience": a real app with pixel-art identity
  (Newroz fire, Kurdistan newt mascot "Pêt"; the owner is Kurdish), custom bots, group chats,
  full control over Hermes (SOUL.md, memory, skills, config, logs, terminal), themes, colours,
  languages (English, Turkish, French) and a new app icon. Five agents built it in parallel from
  `docs/phase1-contract.md` (read it: it is the map of the new code). It compiles, installs and
  starts (CI build `phase0-10`, commit `f47633e`, is green). Checked on the phone the same day:
  Home (all hearth states), Chats, Chat, Bots, Hermes (Soul tab), Terminal (`hermes status`),
  Settings, Appearance; NEWROZ dark and NERGIZ light. A real 1:1 chat and a group chat (Rebel +
  Dengbêj) work with Hydrogen at low effort and answer in Turkish. Fixed after that look:
  invisible pixel-font text, input bar under the gesture bar, Gemini thought summaries in
  answers, double `$` in the terminal, raw Markdown in chat previews. See "Phase 1 notes" below.

- **Masterpiece upgrade + full phone verification (2026-11-23, local build 18 / 0.1.18).**
  Added and verified on the Galaxy S24 Ultra (`google/gemini-4-hydrogen` at `low` effort):
  - **Multimodal attachments (`AttachmentStore`, `AttachmentUi`, `HermesApi`):** Camera capture
    (without adding `CAMERA` permission — `TakePicture` + `FileProvider`), gallery image picker
    (downscaled <= 1600px JPEG + EXIF rotation, sent as `image_url` base64 data URLs for the
    last 3 image turns), and document picker (text excerpts up to 12k chars + PDF page counts,
    plus mirroring into `/root/.hermes/attachments/` so Hermes's tools can access them too).
  - **Autonomous Bot Forge (`BotForge`) & Live Memory:** Bots can emit ```` ```pocket-bot ``` ````
    specs when asked (or via `/forge`) to create new bots into `bots.json` with an interactive
    `ForgedBotCard` (`Chat now`, `Add to group`, `Edit`). Native Hermes `memory` tool updates
    surface a highlighted **Memory updated** card with an `Open Memory` shortcut and trigger
    `HermesScreen` auto-reload via `ChatEngine.memoryUpdates`.
  - **Slash Commands & Modes (`SlashCommands`, `SlashPalette`):** Autocomplete palette for
    `/plan`, `/goal`, `/ultracode`, `/loop [2-5]`, `/normal`, `/forge`, `/skills`, `/remember`,
    `/memory`, `/theme`, `/export`, `/clear`, `/help`, plus secret easter eggs (`/pet`, `/newroz`
    ember shower, `/roll`) and an `ActiveModeBanner`.
  - **Interactive Hearth & Secrets:** Tapping the home hearth stokes the fire with Pêt quips;
    7 quick taps trigger **Newroz Blaze Overdrive**; 5 taps on Pêt in About reveal **Hearthkeeper
    Stats**.
  - **Phone checks passed on v0.1.17/18:** Home hearth & overdrive, Bot Editor, Hermes tabs
    (Soul, Memory, User, Skills, Config, Logs), About secret stats, `/` palette & local commands,
    live `/forge` (forged bot "Uluğ"), live `memory` tool (`USER.md` with `\n§\n`), multimodal
    image vision & document reading, `/plan` banner, `terminal` (`uname -a`) tool call, message
    actions, `/theme zagros` -> `newroz`, and `force-stop` -> reopen survival (Running in 9s,
    zero crashes in `app.log`). Also completed all three small review follow-ups from the
    previous handoff.

- **Agirî Masterpiece Release + full phone verification (2026-11-23, local builds 22–23 / 0.1.22–0.1.23, commits `c421cad` + `7523b1b`).**
  Confirmed official name **"Agirî"** (keeping `applicationId = "dev.pocketagent.app"`). Added,
  unit-tested (`172` tests, 0 unexpected failures), and verified live on the Galaxy S24 Ultra
  (Snapdragon 8 Gen 3, saved config kept on `google/gemini-4-hydrogen` at `low` effort):
  - **On-device Neural TTS (`voice/VoiceEngine`, `SupertonicModelStore`, `TtsTextCleaner`, `VoicePrefs`):**
    Integrated **Supertonic 3 INT8** (99M, 31 languages including TR/EN/FR, 10 female/male voices
    F1–F5 & M1–M5) via `sherpa-onnx` v2.0.3 (static-link ONNX Runtime AAR fetched via GitHub Releases
    Ivy repo) with automatic Android `TextToSpeech` fallback, in-app resumable/atomic model
    downloader (`~139 MB` stored in `files/tts/supertonic-3-int8/`), EN/FR/TR language detection
    that respects Kurdish names (`Agirî`, `Pêt`, `Dengbêj`), and **per-bot customizable TTS voice
    & delivery mood** (`Natural`, `Happy`, `Excited`, `Serious`, `Calm`, `Storyteller` + speed
    override + live preview in Bot Editor + signature starter bot defaults + Bot Forge voice tags).
    Measured live on Snapdragon 8 Gen 3 CPU: loads in ~1.0 s, synthesizes 44.1 kHz Turkish speech
    at **RTF 0.30–0.32** (~3.2× faster than real time).
  - **Curated CC0 UI Sound Library (`voice/SoundEngine`, `res/raw/sfx_*.ogg`):** 20 CC0 `.ogg` cues
    (~73 KB total from `romainsimon/uisfx`) across `Hearth`, `Studio`, and `Silent` packs for send,
    reply complete (OpenCode-style completion chime), tool finished, memory saved, bot forged,
    approval requested, error, hearth stoke, and subtle UI taps.
  - **Artistic Pixel Art Refresh & 6 Switchable Launcher Icons (`AppIconManager`, `assets/pixel/*.pxl`):**
    Redrew the fire hearth, overlapping Mount Agirî & Zagros panorama with *Nêrgiz* alpine meadow
    (no polka-dot grass or vertical seams), all 16 bot avatars, and 6 distinct switchable adaptive
    launcher icons (`Newroz`, `Pêt`, `Agirî`, `Zagros`, `Kawa`, `Roj`) via `<activity-alias>` in
    Appearance settings (keeping `MainActivity` always enabled). Added moon halo, distant twinkling
    Newroz ridge bonfires, meadow fireflies, and occasional pixel shooting stars in `HearthScene`.
  - **Deep UI/UX & Power Features:** Fixed double status-bar padding (`AppRoot`/`PocketTopBar`,
    full-bleed `HearthScene`), in-chat **Chat Controls** sheet (Mode + per-chat Model & Thinking
    Effort overrides), voice input (`SpeechRecognizer` + `RecognizerIntent` fallback), inline bot
    quick-action strip (Speak/Stop, Copy, Fork, Regenerate) + **Conversation Forking**, syntax-
    highlighted code blocks with **Run in Terminal** confirmation dialog & execution, visual
    searchable **Skill Picker** sheet, **Group Chat participant bar** (tap to `@mention`),
    ChatList filter chips (`All`, `Pinned`, `Groups`, `1:1`, `Modes`), Android `ACTION_SEND` share
    target (`Send to Agirî` dialog), plus subtle surprises (double-tap bot avatar spring bounce,
    `/agir` secret command).
  - **Phone checks passed on v0.1.20–0.1.23:** Home full-bleed night sky & blazing hearth in BOTH
    Newroz Dark and Nergiz Light (transparent status bar with adaptive white icons over Home's night
    sky), live Supertonic 3 Turkish synthesis (default F1 `RTF=0.32`, Coder M5/Serious `RTF=0.30`,
    Dengbêj M4/Storyteller `RTF=0.34`), live in-app HuggingFace download (~139 MB in ~5s on Wi-Fi 6E)
    & instant mid-download cancellation, live System TTS test button & playback, live `SpeechRecognizer`
    permission + listening state, live 6-icon switcher (`NEWROZ` -> `PET` -> `NEWROZ` verified via
    `dumpsys package`), Bot Voice & Delivery editor, Chats filter chips + code-block message previews,
    Group Participant Bar, localized Chat Controls sheet, Visual Skill Picker (44 Hermes skills),
    live Coder reply with syntax-highlighted `bash` block -> **Run in Terminal** (`uname -a` executed
    cleanly), **Fork** branching, `ACTION_SEND` share target (including into the currently open chat),
    `force-stop` -> reopen -> Start recovery (11s to `Running`), and 22-minute steady background run
    (~260 MiB RSS, zero crashes).
  - **Adversarial review fixes (v0.1.23, `177` unit tests, 0 unexpected failures; CI run #12 on
    `c421cad` green end-to-end including `sherpa-onnx:2.0.3` Ivy fetch):**
    - Serialized native `OfflineTts` inference/release under `ttsLock` with per-utterance `speakSeq`
      + `AtomicBoolean` cancellation (fixes concurrent native ONNX overlap & delete-while-speaking
      use-after-free), clean `AudioTrack` tail drain, 5-min idle neural unload, and hardened System TTS.
    - Fixed `SupertonicModelStore.cancelDownload()` to close active `HttpURLConnection` immediately
      and guard progress updates against overwriting cancelled state.
    - Fixed `ACTION_SEND` into the currently open chat via reactive `ChatShareInbox` and blocked
      Recents intent replays (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`).
    - Fixed opaque `window.statusBarColor` clipping Home's night sky in dark & light themes.
    - Fixed `markdownToPlain` returning blank (`"No messages yet"`) for single-line flattened code-block
      previews, fixed `forkConversation` spacing after `aapt2` whitespace trimming, localized effort
      chips in `ChatControlsSheet`/`ChatOverridesBanner`, fixed `BotPresets.upgradeLegacy` overwriting
      explicit `Follow default` voice choices across restarts, fixed comment-only shell `isRunnable`,
      refined `TtsTextCleaner` (code-only replies, `snake_case`, French contractions, Turkish stopwords),
      added System TTS preview button when Supertonic isn't installed, fixed Turkish typo in `ctl_sfx_sub`,
      and updated leftover `"PocketAgent diagnostics"` strings to `"Agirî diagnostics"`.

- **Phase 3, "the always-on hearth" (2026-11-24, local builds 30–32 / 0.1.30–0.1.32).** The owner
  asked for detailed plans to make the app far better, coded by Sonnet, verified by Opus, with Haiku as
  the low-level worker. The plan and contract is `docs/phase3-plan.md` (read it: it is the map of the new
  code and says why each thing exists); `docs/APP_OVERVIEW.md` describes the whole app for whoever plans
  the next round. Eight Sonnet agents built it in parallel from the contract, an integration agent made
  it compile, nine Opus reviewers plus a Haiku mechanical check produced 35 findings, each confirmed by a
  second Opus pass and fixed, then a phone test found four more defects (fixed, reviewed once more).
  About 410 unit tests, 0 unexpected failures. What it adds:
  - **Presence:** auto-start when the app opens, wake-on-send (type while Hermes is asleep; the engine
    lights the fire and delivers), reply / approval / failure notifications with inline reply and
    mark-as-read, unread counts, one automatic restart after an unexpected exit, optional idle stop
    (`service/Presence.kt`, `ReplyNotifier.kt`, `AppForeground.kt`, `runtime/PresencePrefs.kt`).
  - **Routines:** scheduled prompts (daily / interval / once) through `AlarmManager`, running from a dead
    process into their own chat (`routines/*`, `ui/routines/*`).
  - **Usage:** tokens per reply, per chat, per day and model, a daily budget that warns or blocks
    (`data/UsageStore.kt`, Settings > Usage).
  - **Backup and restore** to one zip without credentials (`data/BackupManager.kt`), bot export / import
    (`data/BotShare.kt`).
  - **Providers:** OpenRouter, Anthropic, OpenAI and a custom OpenAI-compatible endpoint next to the
    three Google modes (`runtime/ProviderConfig.kt`; the Google modes' generated files are unchanged).
  - **Android surface:** launcher shortcuts, a Quick Settings tile, "Ask Agirî" in the text-selection
    menu, deep links (`AppLinks.kt`, `service/FireTileService.kt`).
  - **Chat:** find in chat, starred messages + Saved messages screen, drafts kept on disk, export to a
    file, usage in the meta line; **Hermes screen:** create / delete skills, ANSI colours in the terminal.
  - **Checked on the phone (Hydrogen, low effort):** auto-start (Running 11 s after opening), wake-on-send
    from a stopped engine, reply notification as its own row and opening the chat from it, per-turn token
    numbers and the usage screen, find in chat, star and draft surviving a force-stop, a routine firing
    on the second with the process dead (start, answer, notification, Hermes stopped again), deleting a
    routine removes its alarm, backup (no credential in the zip) and restore with the automatic restart,
    shortcut links, PROCESS_TEXT, the tile in both directions, the Home status-bar scrim.
  - **Not tried on the phone:** the new providers with a real key (none available; only config generation
    is tested), approval card and approval notification, inline reply from a notification, budget
    warn / block, idle stop, auto-restart after a crash, bot export / import, skill create / delete,
    terminal colours, restoring on another phone.
  - Committed and pushed as `844e48e`; CI run #14 is green end-to-end (unit tests, proot, rootfs, smoke
    test, APK, prerelease `phase0-14`). The phone runs local build 32 of the same code. CI run #13 (commit
    `7523b1b`) had failed for a reason outside the code: Gradle could not download the Kotlin Compose
    plugin that time; the build files were unchanged and the next run passed.

- **The road to 1.0 is planned (2026-11-24, second edition the same day, its review worked in on
  2026-11-25); nothing of it is built.** The owner asked for a "masterpiece plan" and said not to
  execute it, then asked for a much richer second edition with notes of their own: a creative
  workspace (an output panel, documents and slides, pictures, deep research with sources), everything
  the leading assistants offer plus features of its own, and a global app with English first. Two
  documents: `docs/phase4-plan.md` (fourteen waves, 0 "Foundation" to 13 "One"; the five largest ship
  in two halves and every wave names its core and its extras; with the comparison against the leading
  assistants, the findings behind the plan, a map of where things live in the app (section 4.1),
  ownership tables, 45 experiments with fallbacks, risks and the owner's decisions) and
  `docs/design-language.md` ("Hearthlight 2", second edition: four rules, tokens, motion, touch,
  sound, the living scene, Pêt, every screen including the Canvas, slides and research, the map, six
  kinds of screen with five states each, and type for every script; binding for every interface
  agent). They rest on a survey of the whole app and its screenshots, the source of the Hermes that
  ships in the image, Android's rules for a target-28 app, the speech models sherpa-onnx can run, and
  what the leading assistants offer on phones in November 2026. The first edition's independent review
  found thirty problems (four serious, all in how the agent's phone powers were guarded). The second
  edition was reviewed by two strong-model reviewers (facts and safety; product and consistency):
  sixty findings, eight of them serious, the most consequential checked in the Hermes source and all
  worked in, without a second review round. Not committed (the owner did not ask for a commit).

Next steps, in order:
1. Wait for the owner's go-ahead and their answers to section 13 of `docs/phase4-plan.md` (the order of
   the waves, the optimised build and the test twin, whether a wave may ship without its extras, a
   spending cap at the provider for the twin's credential, the first ten languages, the contact address
   the weather service needs, the 1.0 date). Do not start building before that.
2. Then Wave 0: run experiments S1 to S7 on the phone (section 8 of the plan), write
   `docs/waves/w0-contract.md` in the style of `docs/phase3-plan.md`, build, review once, prove, ship.
   Wave 0 also pays the old debt: everything listed above as "not tried on the phone" becomes a scripted
   scenario on the practice model (editing `SOUL.md`, `/loop`, the approval card and the rest).
3. Leftovers that need a person or a real device state and stay manual: the live camera shutter, spoken
   dictation, a rootfs update over an existing install with user data.

## Repo map

| Path | What it is |
|---|---|
| `app/` | The Android app, Kotlin + Compose. `runtime/` = install, launch, config, secrets; `service/` = foreground service; `api/` = client for Hermes's OpenAI-compatible API; `diag/` = diagnostics and redaction; `data/` = bots, conversations, messages, chat engine (group chats); `hermes/` = SOUL/memory/skills/log files and the terminal; `ui/` = screens (see `docs/phase1-contract.md`). Unit tests in `app/src/test`. |
| `app/src/main/assets/pixel/*.pxl` | All pixel art (mascot, fire, scene, avatars, icons, app icon) as text sprites. Format in `docs/phase1-contract.md`. Preview: `tools/pixel-preview.ps1 -File <pxl> -Out dist\pixel-preview`; launcher icon XML: `tools/pxl-to-vector.ps1`. |
| `docs/phase1-contract.md` | The API contract between UI, art, design system and engine. |
| `docs/phase3-plan.md` | Plan and contract of Phase 3 (presence, routines, usage, backup, providers, Android surface). |
| `docs/APP_OVERVIEW.md` | What the app is and does, what is proven and what does not exist: the briefing for planning. |
| `docs/phase4-plan.md` | The road to 1.0, second edition with its review worked in: fourteen waves (core and extras, five of them in two halves), where Agirî stands against the leading assistants, the findings behind the plan (file and line), where things live in the app (section 4.1), 45 experiments, risks, the owner's decisions. **Planned, not built.** Per-wave contracts will go to `docs/waves/`. |
| `docs/design-language.md` | "Hearthlight 2", second edition: four rules, tokens (the design pixel in whole device pixels), motion (28 patterns), touch, sound, the living scene under the real sky, Pêt, every screen (Home, chat, Canvas, slides, pictures, research, voice), the map of the app, six kinds of screen and their five states, type and layout for every language. Binding for interface work from Wave 0 on. |
| `app/.../routines/`, `service/`, `AppLinks.kt`, `RestartActivity.kt` | Scheduled prompts; foreground service, notifications, presence, tile; deep links; the process restart after a restore. |
| `runtime/pocketagent-proot.sh` | The ONE launcher that enters the rootfs. Shipped as an app asset and used unchanged by the CI smoke test. POSIX sh only (Android runs it with mksh). |
| `runtime/test-launcher.sh` | Fast launcher test with a fake proot; runs anywhere (`sh runtime/test-launcher.sh [shells...]`). |
| `rootfs/` | Builds the Debian trixie arm64 rootfs with Hermes preinstalled; `rootfs/smoke/` boots Hermes under proot against a mock LLM. |
| `proot/build-proot.sh` | Builds static proot + loader from a pinned commit with the NDK. |
| `tools/local-build.ps1` | Fast local build, install and unit-test runner for Windows (see "Faster iteration"). |
| `.github/workflows/build.yml` | CI: unit tests, proot, rootfs, smoke test, APK, prerelease. |
| `dist/` | Git-ignored. Downloaded builds and screenshots. |

## Architecture (decided; do not reopen without a reason)

- **targetSdk 28 on purpose.** It lets the app exec binaries from its private data directory, which
  the extracted rootfs needs. That is why the app can only be sideloaded.
- **proot + Debian rootfs.** proot is built from source (static, pinned commit) and shipped as
  `libproot.so` / `libproot-loader.so` in `jniLibs`, so Android extracts them as real executable
  files. The rootfs (Hermes v0.23.3, Python 3.14) ships as a `tar.xz` asset and is extracted on
  first launch (about 1.1 GB on disk).
- **A foreground service runs `hermes gateway`.** The app talks to its OpenAI-compatible API on
  loopback, on a random port, with a per-install key. The key is sent only after the child has
  announced that port in its own log.
- **Credential modes** (all Google for now): API key through Hermes's `gemini` provider (AI Studio,
  or Vertex express mode via `GEMINI_BASE_URL`); service-account JSON through the `vertex`
  provider; gcloud user credentials (`authorized_user`) placed at the ADC path with the project id
  in `config.yaml`. `.env` and `config.yaml` are regenerated on every start.
- **User state survives a rootfs replacement** through an allowlist park/restore.
- **Fallback if proot ever fails on a new Android:** embed Python with Chaquopy, as the community
  app `com.mobilefork.hermesagent` does. Not needed so far.

## Build and release loop

- A push to `ccr-bb6d7bcc-9eojyu` builds everything and publishes a prerelease `phase0-N`
  (N = workflow run number, also the app version `0.0.N-phase0`). About 10 minutes: rootfs ~4,
  proot 1 to 7 (NDK download), smoke ~3, APK ~4. If you change branches, change the trigger.
- `concurrency: cancel-in-progress` is on: a second push cancels the running build.
- Release tags sit on `main`'s commit because Actions gets a 403 when tagging commits that touch
  workflow files.
- Pushing a change to `.github/workflows/` needs a token with the `workflow` scope.
- The committed debug keystore is for test builds only. Install only APKs from this repo's
  Releases page (or your own local build).

## Testing on the phone

With the phone on USB and debugging allowed:

```sh
adb install -r PocketAgent-phase0-N-<sha>.apk
adb shell run-as dev.pocketagent.app cat files/logs/app.log        # app log (secrets redacted)
adb shell run-as dev.pocketagent.app cat files/logs/runtime.log    # what Hermes/proot printed
adb shell run-as dev.pocketagent.app cat files/runtime/state/last-launch.txt   # exact command line
adb logcat | grep PocketAgent/
```

- The build is debuggable, so `run-as` reads the app's private files.
- The app rewrites `files/runtime/pocketagent-proot.sh` from its asset on every start. To try a
  launcher change without a build, put a patched copy under `files/runtime/state-selftest/` and run
  it by hand with the `PA_*` variables (`PA_PROOT`/`PA_LOADER` = the two files in the app's
  `lib/arm64` directory, `PA_ROOTFS=files/rootfs`, `PA_STATE=files/runtime/state-selftest`,
  `PA_SELFTEST=1`, `PA_GUEST_MARKER=selftest`). Files pushed to `/data/local/tmp` reach the app with
  `cat file | run-as dev.pocketagent.app sh -c 'cat > files/...'`.
- The app's Debug section has "Copy diagnostics" (secrets redacted, self-test included) for when
  no computer is at hand.

## Faster iteration

CI rebuilds the Linux system and proot on every push, which is wasted time for app-only changes.

- App code, UI and launcher changes are built locally on the owner's Windows PC with
  `tools/local-build.ps1` (about 30 s to build, 15 s to install, against 10 minutes on CI):

  ```powershell
  powershell -NoProfile -ExecutionPolicy Bypass -File tools\local-build.ps1 -Start
  ```

  | Switch | What it does |
  |---|---|
  | (none) | Build the debug APK with the rootfs and proot from `dist\runtime-parts\` |
  | `-FetchParts [phase0-N]` | Refresh those parts from a release APK (hash-checked); default latest |
  | `-Install` / `-Start` | Install on the connected phone / also launch the app |
  | `-BuildNumber N` | Version number; must not go below what is installed, so raise it each time |
  | `-Test` | Run the unit tests from a copy outside the apostrophe path (see below) |
  | `-ListDevices` | Show connected phones |

- It needs JDK 21 (Android Studio's `jbr` is used) and the Android SDK with platform
  `android-37.2` and build-tools 36.0.0; both are installed on the owner's PC.
- The checkout path `C:\Users\PC\Agent's Workspace\Claude\PocketAgent` contains an apostrophe (and a space): assembling works, but Gradle's
  test workers fail with `ClassNotFoundException` there, hence `-Test`. Four tests that create
  symlinks fail on Windows without Developer Mode; `-Test` reports them as expected (exit 2).
- Reinstalling with an unchanged rootfs does not extract it again (`files/rootfs.stamp` holds the
  rootfs build id). Every CI run produces a new build id, so switching between a CI APK and a local
  build made from other parts re-extracts (about a minute; user state is kept).
- Use CI when `rootfs/` or `proot/` change, and for the builds the owner keeps. Then run
  `-FetchParts` once to build locally against the new parts.

## Hard-won facts

- **Android install paths contain `=`.** `nativeLibraryDir` looks like
  `/data/app/~~<random>==/dev.pocketagent.app-<random>==/lib/arm64`. `env` (toybox and GNU alike)
  treats any argument containing `=` as an assignment, so never put such a path directly after
  `env VAR=...`. CI did not catch this until the smoke test was routed through such a path.
- **The first launch is not the only launch.** Paths that only run once state exists on disk
  (installed rootfs, saved settings, imported credentials) are not covered by a fresh-install
  test. Always test: install, start, force-stop, reopen, start again.
- Kotlin initialises properties and `init` blocks in textual order: an `init` that calls a method
  must come after every property that method can reach, including `by lazy` delegates.
- `filesDir` is behind a symlink (`/data/user/0` -> `/data/data`): always compare canonical paths.
- proot ignores SIGTERM and `--kill-on-exit` does not kill guests. Guests are found by the
  `PA_IN_PROOT` environment marker; PIDs are not virtualised.
- Static bionic arm64 binaries need TLS segment alignment >= 64, and the main executable is linked
  for 16 KB pages.
- Hermes's default install pulls about 1 GB of Node/ffmpeg, and a `.git` directory triggers network
  update checks: the rootfs is lean and sealed on purpose. The "install out of sync (ffmpeg, node
  ...)" warning at gateway start is expected.
- `UV_LINK_MODE=copy` is required under proot's `--link2symlink`.
- Shell scripts must stay LF (`.gitattributes`): a CRLF launcher does not run under mksh.
- Android's file picker shows a file pushed with `adb push` only after a media scan; searching by
  name in the picker finds it.
- **`X-Hermes-Session-Id` makes Hermes discard the history in the request body** (it reloads the
  session from its own store and uses only the last message). The chat engine therefore never
  sends it and always sends the full transcript; Hermes derives a session id from
  sha256(system + first user message), so every bot's system message ends with a
  `[chat reference <convId>/<botId>]` line to keep chats apart.
- Client `system` messages are layered on top of Hermes's own prompt (SOUL.md stays in force):
  that is how bot personas work.
- Per-request model needs `model` and `provider` together (a bare model is ignored); the `vertex`
  provider wants the `google/` prefix. Per-request effort: `"model_options":{"reasoning_effort":..}`.
- Memory files are `HERMES_HOME/memories/MEMORY.md` and `USER.md`, entries separated by
  `"\n§\n"`, limits 2200 / 1375 chars. SOUL.md is at the HERMES_HOME root.
- **Gemini 4 thought summaries arrive as normal answer text.** Hermes's Vertex profile always
  sends `include_thoughts: true` for gemini-4, and Vertex's OpenAI-compatible stream puts each
  summary in `delta.content` as a whole chunk `**Title**\n\n<English paragraph>\n\n` before the
  answer. Only `reasoning_effort: "none"` turns it off (and it also forces the lowest thinking).
  `data/ThoughtFilter.kt` moves such leading chunks into `Message.reasoning` (shown folded as
  "Thought process").
- In the guest, `bash -lc` resets PATH (Debian /etc/profile) and `hermes` is not found: the
  terminal uses `bash -c`.
- Starting or stopping the gateway kills running terminal commands (the launcher sweeps all
  `PA_IN_PROOT=1` processes).
- Kotlin nests block comments: a KDoc containing `assets/pixel/*.pxl` opens a nested comment and
  the file no longer compiles ("Unclosed comment").
- `adb exec-out screencap -p > file` in PowerShell corrupts the PNG; use `screencap` to
  `/data/local/tmp` and `adb pull`.
- On localized (e.g. Turkish) Windows, `FileSystemException` symlink-privilege errors print
  `"Gereken ayrıcalık istemcide yok"`, and PowerShell 5.1 `Get-Content` defaults to ANSI unless
  `-Encoding UTF8` is passed (important when parsing `uiautomator dump` XML).
- `ActivityResultContracts.TakePicture()` works without runtime `CAMERA` permission only when
  `android.permission.CAMERA` is NOT declared in `AndroidManifest.xml`.
- Hermes gateway (`api_server.py`) natively accepts OpenAI multimodal `content` arrays with
  `type: "text"` and `type: "image_url"` (`data:image/jpeg;base64,...`) and routes them through
  Vertex (`google/gemini-4-hydrogen` verified).
- **sherpa-onnx JNI + Kotlin 2.x invokedynamic trap:** `OfflineTts.generateWithConfigAndCallback`
  looks up `invoke([F)Ljava/lang/Integer;` via JNI `GetMethodID`, which throws `NoSuchMethodError`
  and aborts (`SIGABRT`) when given a Kotlin 2.x invokedynamic lambda (`$$ExternalSyntheticLambda`).
  Always call `OfflineTts.generateWithConfig(text, config)` (passes `null` callback to native code)
  and split long text into sentences in Kotlin instead.
- **Supertonic 3 language detection vs. Kurdish names:** Circumflex vowels (`î`, `ê`, `û`) appear
  in Kurdish names (`Agirî`, `Pêt`, `Dengbêj`). Never weight circumflexes as uniquely French;
  score EN/FR/TR explicitly and resolve empty `Appearance.language` (`""` = System language) via
  `Locale.getDefault().language`.
- **Android VectorDrawable 32,767-char `pathData` limit:** High-res dithered `.pxl` backgrounds
  (e.g. 90×90) easily exceed Android's string pool limit per `<path>`. Keep adaptive icon
  backgrounds at 45×45 (`-Pixel 2.4`), keeping every `<path>` well under 9 KB.
- **Switchable launcher icons (`<activity-alias>`):** Keep `MainActivity` always `enabled="true"`
  (handling `ACTION_SEND` etc.) and place `MAIN`/`LAUNCHER` exclusively on the `<activity-alias>`
  entries so `adb shell am start -n dev.pocketagent.app/.MainActivity` and in-app intents never
  break regardless of which icon is active.
- **`PixelPanel` fill opacity:** `PixelPanel` draws the `border` color behind the inner fill; never
  pass a translucent `color` with a bright `border` (it bleeds solid `border` color through).
- **Android 11+ package visibility (`<queries>`):** Declare `<queries>` intents for
  `android.speech.RecognitionService` and `android.intent.action.TTS_SERVICE` in
  `AndroidManifest.xml` so voice dictation and fallback System TTS resolve cleanly on Android 11–17.
- **`aapt2` trims unquoted leading/trailing whitespace in `<string>` XML:** Never rely on a leading
  space inside `<string name="ui_fork_suffix"> (fork)</string>`; insert separator spaces in Kotlin.
- **Transparent edge-to-edge bars vs. `PocketTheme.SystemBars`:** Do not set opaque
  `window.statusBarColor = colors.bgDeep.toArgb()` after `enableEdgeToEdge()`, or full-bleed hero
  scenes like `HearthScene` get clipped by an opaque colored slab in light themes. Keep system bars
  transparent and toggle `isAppearanceLightStatusBars` per route (`false` on `Route.Home`).

- **Hermes reports token usage on every stream, per turn.** The final chunk (the one with
  `finish_reason`) always carries `usage`; the counters belong to an agent object created per request,
  so they are per turn, and prompt tokens add up over every model call of the turn. A plain "hello" turn
  costs about 11,000 prompt tokens (Hermes's own prompt and tool definitions).
- **`adb shell am force-stop` cancels the app's alarms** (Android does that on a force-stop). To test an
  alarm against a dead process, press Home and use `adb shell am kill dev.pocketagent.app`.
- **One UI bundles all of an app's notifications into one collapsed row** unless they have explicit
  groups: a reply notification was hidden behind the ongoing "running" notification. Every notification
  now has a group and an app-posted silent summary (`service/NotificationGroups.kt`). Cancelling a
  summary cancels its children, including ones still queued.
- **An app cannot restart itself by starting its own activity and then killing its process:** the new
  activity lives in the dying process. `RestartActivity` runs in the `:restart` process (process-phoenix
  pattern), and `PocketApp.onCreate` returns at once in that process. Activity starts from the
  background are blocked silently on Android 10+ whatever the targetSdk.
- **Git Bash rewrites `/data/...` and `/sdcard/...` arguments** into Windows paths: prefix adb commands
  with `MSYS_NO_PATHCONV=1`.
- **Phone testing without touching the screen:** `uiautomator dump` gives texts and bounds (a list item
  scrolled under a top bar still reports its bounds, but a tap there hits the bar); shortcuts with
  `adb shell am start -a dev.pocketagent.app.TERMINAL -n dev.pocketagent.app/.MainActivity`; the tile with
  `cmd statusbar add-tile | click-tile | remove-tile dev.pocketagent.app/.service.FireTileService`
  (a click is delivered when the panel next listens); notifications with
  `dumpsys notification --noredact`; pending alarms with `dumpsys alarm`.
- **Parallel agents on one tree work when the contract names every shared symbol** and each file has one
  owner; `PocketApp.kt`, the manifest, `Nav.kt` and each strings file had exactly one. The first compile
  of eight agents' work needed three small fixes.
- **The agent's own files do not survive an update.** Only the names listed in `runtime/UserState.kt`
  inside `HERMES_HOME` are parked when the rootfs is replaced, and every CI build brings a new rootfs.
  What the agent writes in `/root` or anywhere else in the guest is deleted. The launcher already takes
  extra mounts (`PA_EXTRA_BIND`, unused so far); the plan mounts a folder from outside the image (Wave 2).
- **The app the owner runs is a debug build** (`isMinifyEnabled = false`, CI runs `assembleDebug`):
  every impression of speed and smoothness so far is of unoptimised code.
- **Hermes's source can be read without the phone:** extract `./root/.hermes/hermes-agent` from
  `dist/runtime-parts/rootfs/rootfs-arm64.tar.xz`. What was read there for planning is in
  `docs/phase4-plan.md`, sections 3.2, 3.3 and 14, with file and line: the Runs API (`/v1/runs`: stop, steer,
  events that can be joined again, sub-agents, cost), toolsets only through
  `platform_toolsets.api_server`, profiles under `/p/<name>/`, `hermes prompt-size`,
  `memory.write_approval` / `skills.write_approval` (for an API client the waiting writes are files
  under `pending/`), the MCP client, the browser attaching through `browser.cdp_url`. **None of it has
  been tried on the phone:** section 8 of the plan lists the experiments that must come first.
- **Hermes has an official gate before every tool call** (read in the source, not tried): a plugin in
  `HERMES_HOME/plugins/<name>/` (`plugin.yaml`, `register(ctx)`, listed under `plugins.enabled`) gets
  `pre_tool_call` with the tool, its arguments and the session, and may answer `block`, `approve` (send
  the call to the human-approval gate) or `modify`. Shell hooks (`hooks:` in the configuration) do the
  same with one process per call. The plan's whole safety design stands on this (the gate is built in
  Wave 3, the phone's powers behind it in Wave 9).
- **More read in the Hermes source that changes how to build:** the gateway reads `config.yaml` again
  for every request (tool lists change without a restart); a second profile switches profile serving on
  by itself, and each profile then reads only its own `.env`; `no_mcp` in a tool list shuts out every
  MCP server, so the app's own must be named instead; `cronjob_manage` is a default tool and
  `HERMES_HOME/hooks/<name>/handler.py` is code the gateway runs, both ways for a misled agent to leave
  something behind (and `hooks` and `plugins` survive an update); `hermes prompt-size` counts characters.
- **The version code is the build number, and local builds ran ahead of CI** (32 on the phone, 14 in
  CI): the next CI builds would be refused as downgrades. The plan's Wave 0 moves the owner's build to
  a CI-only numbering and a private key, and gives agents a twin app with its own package name.
- **Kurdish speech exists for the library that is already embedded** (found 2026-11-24, not tried on the
  phone). A Kurmanji Piper voice with two speakers, ready-converted, is in the `tts-models` release of
  k2-fsa/sherpa-onnx: `vits-piper-ku_TR-berfin_renas-medium-int8.tar.bz2` (24 MB; also 42 and 80 MB),
  CC-BY-NC-4.0 (non-commercial, credit required: a download on request, never bundled). It loads through
  `OfflineTtsVitsModelConfig`, which the embedded 2.0.3 AAR contains. The AAR also contains
  `OfflineOmnilingualAsrCtcModelConfig`: the 1,600-language recogniser (365 MB int8, Apache-2.0) lists
  Kurmanji and Sorani, accuracy unknown. Whisper has no Kurdish. The class list was read from
  `dist/sherpa-classes.jar` (git-ignored: it is `classes.jar` taken out of the sherpa-onnx AAR in
  Gradle's cache). The same jar shows the other voice families (Kokoro, Kitten, Matcha), language
  identification, punctuation, speaker separation and noise removal; the plan's Wave 8 treats the
  Kurmanji voice as one entry of a catalogue of voices for many languages.
- **Hermes already carries most of a creative workspace as skills** (read in the source 2026-11-24, not
  tried on the phone): `skills/productivity/microsoft-office` (nineteen scripts that create, read and
  edit Word, PowerPoint and Excel files from plain JSON descriptions), `skills/productivity/pdf`, and
  `skills/research/grounded-citations` (a list of sources with numbers, quotes and "unverified" flags;
  its file is given per call with `--ledger`, or through `HERMES_CITATION_LEDGER`, which is read only
  at start). The libraries they need are the optional extras `documents` and `documents-pdf`
  (`pyproject.toml`), which the app's image does **not** install. The office skill does not use
  Hermes's own Python: it looks for an interpreter that has the libraries and otherwise downloads and
  builds a side environment (`hermes pm environment documents`) in a folder an update deletes, so the
  plan's Wave 6 builds exactly that environment into the image. Hermes's own preview script for slides
  needs an office suite the image does not have; the plan draws the same JSON descriptions natively.
  `pptx_create.py` knows four chart kinds (bar, bar_h, line, pie); `docx_create.py` has no links, code
  blocks, footnotes or mathematics.
- **Hermes has no Google provider for pictures** (`plugins/image_gen/`: deepinfra, fal, krea, meta-ai,
  openai, openai-codex, openrouter, xai; OpenRouter resells Google's picture models), and Google's
  picture models have no free allowance (roughly 3 to 15 US cents a picture, read 2026-11-24). Under
  the $0 rule no picture may be generated in a test: the plan builds pictures against a stand-in that
  costs nothing, behind a price card. Two default tools can spend or send without being asked:
  `image_generate` (active as soon as `image_gen.provider` is set) and `text_to_speech` (its default
  provider is a public cloud voice); and session titles are made by a model call unless
  `auxiliary.title_generation.model_upgrade_enabled` is false. The plan's Wave 0 removes all three.
  The provider's key lies in the guest's `.env`, so a terminal command can always reach it: the key is
  never the defence, and the hard stop is a spending cap at the provider.
- **`post_tool_call` hands a plugin every tool's result**, and plugins also get hooks around every
  request to the model and around Hermes's own side calls (`pre_api_request`, `post_api_request`,
  `pre_auxiliary_call`, `post_auxiliary_call`) and at a sub-agent's start and stop. A run reports its
  usage only at its end, so a running count has to come from those hooks. The plan's ledger, its
  source marks and its meter stand on them (Wave 3; whether a request can be refused or changed from a
  hook is experiment S10). File checkpoints (`checkpoints.enabled`) are taken once per turn, only
  before the file tools and before commands that look destructive, restored only by the typed
  `/rollback` (which the API does not carry), and their store does not survive an update: the plan's
  Undo stands on the app's own git snapshots of the workspace instead.
- **Facts for the scene and for other scripts** (research of 2026-11-24, to be read again before use):
  weather without a key from `api.met.no` (it wants an identifying request header, so the owner must
  choose a contact address that reveals nothing personal); a table of the visible stars from the HYG
  database (CC BY-SA 4.0); open pixel fonts for Chinese, Japanese and Korean (Ark Pixel at 12 pixels,
  SIL OFL) and perhaps for Arabic and Hebrew (Handjet; whether its letters join is unverified); none
  for Indic scripts or Thai.
- **Android facts for a target-28 app, read on the reference pages on 2026-11-24 and not tried:** a PDF
  page's text and form fields are platform calls only from API 35; an entry of the text-selection menu
  can return replacement text; `Presentation` drives a second display; the framework's
  `BiometricPrompt` exists from API 28; a microphone service must be started while the app is visible;
  Android 17 limits memory per app (`adb shell am memory-limiter status`), and whether Hermes's
  processes count is unknown (experiment S7 of the plan).
- **Read in the Hermes source during the review of 2026-11-25 (not tried):** a `pre_tool_call` plugin
  that raises an error lets the call through (`agent/agent_runtime_helpers.py:2034-2047`), so the
  gate's plugin must catch everything and refuse; sub-agents of a turn that comes through the API are
  cut loose from the turn unless `delegation.gateway_mode` is `inline`, a child's approval requests are
  refused, and a child may take 250 steps (`tools/delegate_tool_config.py`); pages are read "without a
  key" by outside services (Exa, Parallel, Firecrawl, Keenable) with a twenty-minute cache, and a long
  page comes back as head and tail with the whole text in a cache file; MCP sign-ins live in
  `HERMES_HOME/mcp-tokens/` (not parked by `runtime/UserState.kt`), the browser sign-in opens a browser
  inside the guest, and the sign-in by code is `hermes mcp login`; `DELETE /api/sessions/{id}` exists.
- **Facts about today's app that the plan's review turned up:** a `pocket-bot` block in an answer saves
  a bot without asking (`data/ChatEngine.kt:493-509`); the themes are an enum (`ThemeId` in
  `ui/theme/Appearance.kt`), so a theme made by the user needs them to become data; the text-selection
  entry opens `MainActivity`, a single instance that cannot hand text back; a web view that lives in
  another process cannot be placed inside the app's own Compose screen (a sandboxed page has to run in
  its own activity and be shown elsewhere as a picture); a camera viewfinder inside the app would need
  the `CAMERA` permission, which would break today's permission-free `TakePicture`.

## Phase 1 notes (the new app, 2026-11-23)

- Navigation is a small hand-written back stack (`ui/Nav.kt`), tabs Home, Chats, Bots, Hermes,
  Settings. Language switching is done in `ui/AppRoot.kt` by overriding the configuration
  (`Appearance.language`); appearance lives in `ui/theme/AppearanceStore` (SharedPreferences).
- Chats are JSON files under `files/chat/` (`ChatRepository`, atomic writes, corrupt files kept
  as `.bad`). Starter bots: Hermes (plain, id `hermes`), plus rebel, coder, dengbej; more
  templates in `data/BotPresets.kt`.
- Group chats: every bot answers in turn (or only @mentioned ones), each sees the others' lines
  prefixed with their names.
- Engine texts (NOTE/ERROR messages, terminal INFO lines) are English only.
- String resources are split per owner: `strings.xml`, `strings_chat.xml` (`ui_*`),
  `strings_control.xml` (`ctl_*`), `strings_design.xml`, each with `values-tr` and `values-fr`.

## Roadmap

- **Phase 0 (now):** prove the runtime on the real phone and make one real LLM call.
- **Phase 1:** real chat UI; Inspector (view and edit system prompt, config, memory, skills, raw
  logs); in-app language picker (English first, then Turkish and French); terminal screen with the
  full `hermes` CLI.
- **Phase 2:** the chosen pixel-art identity, animations, themes, accent colours, personalisation.
- **Phase 3 (built 2026-11-24):** the always-on hearth: presence, routines, usage, backup, more
  providers, Android surface. See `docs/phase3-plan.md`.
- **Phase 4 and beyond (planned 2026-11-24, not built):** fourteen waves to version 1.0: Foundation,
  Home, Conversation, Engine, Thrift, Canvas, Create, Know, Voice, Hands, Everywhere, Memory, World,
  One. See `docs/phase4-plan.md` and `docs/design-language.md`.
- Always: everything inside the APK, and keep the one-tap diagnostics copy.

## Open decisions with the owner

1. ~~The app name~~ — **Confirmed: "Agirî"** (keeping `applicationId = "dev.pocketagent.app"`).
2. ~~Design direction~~ — Hearthlight / Newroz pixel-art identity with Pêt & 6 selectable themes + 6 selectable app icons shipped.
3. Which Gemini model to use by default long-term (currently kept on `google/gemini-4-hydrogen` at `low` effort for $0 testing).
4. The decisions in section 13 of `docs/phase4-plan.md` (22 rows): the order of the waves, the optimised
   build for daily use and the test twin, whether a wave may ship without its extras, a spending cap at
   the provider for the twin's credential, the first ten languages, five taste gates (the fire, the
   sky, the answer layout, the Canvas panel, the deck themes), the weather and its contact address,
   whether one real picture may ever be paid for, the voices and a priced live voice, the defaults for
   phone powers, where the updater looks for releases, the star of the last wave, the 1.0 date
   (proposed: the June solstice, 21 June 2027; late summer if every extra has to be inside 1.0).
