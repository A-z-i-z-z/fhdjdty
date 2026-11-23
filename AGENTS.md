# Agirî (formerly PocketAgent): notes for whoever picks this up next

This file is the handoff note for any AI agent (or person) continuing the project. Read it before
touching anything. **Keep it current:** at the end of a working session update "Current status",
and add to "Hard-won facts" whenever something cost real time to find out. `README.md` is the
user-facing description; this file is the working memory.

Last updated: 2026-11-23

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

- **Agirî Masterpiece Release + full phone verification (2026-11-23, local build 22 / 0.1.22).**
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

Next steps, in order:
1. Not yet tried on the phone: editing `SOUL.md` end-to-end, live `TakePicture` camera shutter,
   spoken STT speech-to-text transcription (microphone capture tested in silence), `/loop` multi-pass
   mode, edit/regenerate/stop buttons in-flow, an approval card, and a rootfs update over an existing
   install with user data.
2. Monitor CI run on `ccr-bb6d7bcc-9eojyu` after this review-fixes push.

## Repo map

| Path | What it is |
|---|---|
| `app/` | The Android app, Kotlin + Compose. `runtime/` = install, launch, config, secrets; `service/` = foreground service; `api/` = client for Hermes's OpenAI-compatible API; `diag/` = diagnostics and redaction; `data/` = bots, conversations, messages, chat engine (group chats); `hermes/` = SOUL/memory/skills/log files and the terminal; `ui/` = screens (see `docs/phase1-contract.md`). Unit tests in `app/src/test`. |
| `app/src/main/assets/pixel/*.pxl` | All pixel art (mascot, fire, scene, avatars, icons, app icon) as text sprites. Format in `docs/phase1-contract.md`. Preview: `tools/pixel-preview.ps1 -File <pxl> -Out dist\pixel-preview`; launcher icon XML: `tools/pxl-to-vector.ps1`. |
| `docs/phase1-contract.md` | The API contract between UI, art, design system and engine. |
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
- The checkout path `C:\Claude's_WorkSpace` contains an apostrophe: assembling works, but Gradle's
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
- Always: everything inside the APK, and keep the one-tap diagnostics copy.

## Open decisions with the owner

1. ~~The app name~~ — **Confirmed: "Agirî"** (keeping `applicationId = "dev.pocketagent.app"`).
2. ~~Design direction~~ — Hearthlight / Newroz pixel-art identity with Pêt & 6 selectable themes + 6 selectable app icons shipped.
3. Which Gemini model to use by default long-term (currently kept on `google/gemini-4-hydrogen` at `low` effort for $0 testing).
