# PocketAgent: notes for whoever picks this up next

This file is the handoff note for any AI agent (or person) continuing the project. Read it before
touching anything. **Keep it current:** at the end of a working session update "Current status",
and add to "Hard-won facts" whenever something cost real time to find out. `README.md` is the
user-facing description; this file is the working memory.

Last updated: 2026-11-23

## What this is

A sideloaded Android app that runs agent platforms fully on the phone, with only the LLM remote.
First target: Nous Research's Hermes Agent. More agents may follow. Working name "PocketAgent",
applicationId `dev.pocketagent.app` (the owner has not confirmed the name).

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
  starts; it was NOT yet looked at on the phone (the phone locked with a PIN), so expect visual
  and layout fixes once someone can see it. See "Phase 1 notes" below.

Next steps, in order:
1. Look at every screen on the phone (Home, Chats, Chat, Bots, Bot editor, Hermes tabs,
   Terminal, Settings sections) in at least NEWROZ dark and NERGIZ light; fix what looks off.
2. Run a real 1:1 chat and a group chat (Hydrogen, low effort), a tool call, edit/regenerate,
   stop, an approval if one comes, a terminal command (`hermes status`), and a SOUL.md edit.
3. Not yet exercised on the phone: long sessions, background survival with the screen off, a
   rootfs update over an existing install with user data.

Small follow-ups from the last review (none blocks use; do them with the next change):
- `tools/local-build.ps1 -Test`: count a `FileSystemException` as "expected on Windows" only when
  its message is the missing-symlink-privilege one; treat a robocopy timeout (exit -1) as failure.
- `RuntimeManager`: "Delete runtime files" should delete the stamp with `FileUtil.deleteOrThrow`
  (an undeletable stamp currently wedges Start); the unreadable-stamp error text says "Reset" but
  the button is "Delete runtime files".
- `ProviderConfigTest`: the `studio-effort` comment overstates it. The smoke test only proves that
  Hermes boots with an `agent:` block, not that it applies the level.

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
- In the guest, `bash -lc` resets PATH (Debian /etc/profile) and `hermes` is not found: the
  terminal uses `bash -c`.
- Starting or stopping the gateway kills running terminal commands (the launcher sweeps all
  `PA_IN_PROOT=1` processes).
- Kotlin nests block comments: a KDoc containing `assets/pixel/*.pxl` opens a nested comment and
  the file no longer compiles ("Unclosed comment").
- `adb exec-out screencap -p > file` in PowerShell corrupts the PNG; use `screencap` to
  `/data/local/tmp` and `adb pull`.

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

1. The app name ("PocketAgent" is a working name).
2. Design direction: three pixel-art options (A Hearthlight, B Plotter, C Sprig; the designer
   recommends A). The owner has the link.
3. Which Gemini model to use by default once Vertex works (they intend to use Gemini 4 models).
