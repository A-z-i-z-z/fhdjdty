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
- **Build 7 (`phase0-7`) on the real phone:** installs, opens, extracts the 1.1 GB rootfs in 53 s,
  then the runtime dies at once with `env: exec -0: No such file or directory` (exit 127). Cause:
  see "Hard-won facts", first item.
- **Fix (commit `0520db0`, build 8):** `sh -c 'exec "$@"'` trampoline in the launcher, a
  runner-independent launcher test, and the smoke test routed through a path containing `=`.
  Reviewed. A patched copy of the launcher was run by hand on the phone: Hermes v0.23.3 boots under
  proot on Android 17 and the API server listens after about 15 s.
- **Not yet verified:** build 8 started from the app itself on the phone; any real Gemini/Vertex
  call. The owner's Vertex credentials (gcloud user credentials, `authorized_user`) are already
  imported in the app on the phone and the setup is saved (provider `vertex`, model
  `google/gemini-4-argon`, region `global`).
- One local commit may still be unpushed: the CI step that runs `runtime/test-launcher.sh`.

Next steps, in order:
1. Install build 8, start the runtime from the app, confirm it reaches "Running".
2. Send a real chat message through Vertex; fix whatever breaks.
3. Set up a fast local build (see "Faster iteration").
4. Start Phase 1 (see "Roadmap").

## Repo map

| Path | What it is |
|---|---|
| `app/` | The Android app, Kotlin + Compose. `runtime/` = install, launch, config, secrets; `service/` = foreground service; `api/` = client for Hermes's OpenAI-compatible API; `diag/` = diagnostics and redaction; `ui/` = the single screen. Unit tests in `app/src/test`. |
| `runtime/pocketagent-proot.sh` | The ONE launcher that enters the rootfs. Shipped as an app asset and used unchanged by the CI smoke test. POSIX sh only (Android runs it with mksh). |
| `runtime/test-launcher.sh` | Fast launcher test with a fake proot; runs anywhere (`sh runtime/test-launcher.sh [shells...]`). |
| `rootfs/` | Builds the Debian trixie arm64 rootfs with Hermes preinstalled; `rootfs/smoke/` boots Hermes under proot against a mock LLM. |
| `proot/build-proot.sh` | Builds static proot + loader from a pinned commit with the NDK. |
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

- App code, UI and launcher changes can be built locally: `app/build.gradle.kts` accepts
  `-ProotfsArchive=<rootfs-arm64.tar.xz>` and `-PprootDir=<dir with libproot.so and
  libproot-loader.so>`. Both can be taken out of the latest CI APK (`assets/rootfs.tar.xz`,
  `lib/arm64-v8a/`). Without them the build produces stubs. Not set up yet on the owner's PC.
- Reinstalling with an unchanged rootfs does not extract it again (`files/rootfs.stamp`).
- Use CI when `rootfs/` or `proot/` change, and for the builds the owner keeps.

## Hard-won facts

- **Android install paths contain `=`.** `nativeLibraryDir` looks like
  `/data/app/~~<random>==/dev.pocketagent.app-<random>==/lib/arm64`. `env` (toybox and GNU alike)
  treats any argument containing `=` as an assignment, so never put such a path directly after
  `env VAR=...`. CI did not catch this until the smoke test was routed through such a path.
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
