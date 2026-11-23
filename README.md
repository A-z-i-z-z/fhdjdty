# PocketAgent (Phase 0)

A sideloaded native Android app (`dev.pocketagent.app`) that runs [Hermes Agent](https://github.com/NousResearch/hermes-agent)
(Python) entirely on the phone, inside **proot** with a **Debian trixie arm64 rootfs**. Only the LLM is remote
(Google Gemini). The app starts Hermes's OpenAI-compatible API server on `127.0.0.1:8642` and chats with it.

Phase 0 has one job: answer on a real device whether **unmodified Hermes runs under proot inside the app**. The UI is
deliberately plain Material 3; the *Copy diagnostics* button is the main feature.

## Repository layout

| Path | What |
|---|---|
| `app/` | Kotlin + Jetpack Compose app (single activity, foreground service, runtime manager, diagnostics) |
| `runtime/pocketagent-proot.sh` | **The** proot launcher. Shipped as an app asset *and* used by the CI smoke test |
| `rootfs/build-rootfs.sh`, `rootfs/install-hermes.sh` | Builds the Debian rootfs with Hermes preinstalled (arch-parametric: arm64, amd64) |
| `rootfs/smoke/` | Smoke test (boots Hermes under proot, checks the API), mock LLM, golden provider config fixtures |
| `proot/build-proot.sh` | Builds proot from source with the NDK (static, relocatable) |
| `.github/workflows/build.yml` | CI: unit tests, proot, rootfs, smoke test, APK, prerelease |

## How it works

* **Runtime** = proot (bionic, arm64) + a Debian rootfs shipped inside the APK as `assets/rootfs.tar.xz`
  (stored uncompressed by aapt) and extracted to app-private storage on first start. A stamp file is written only after
  a complete extraction; a half-extracted rootfs is never used.
* `proot` and its loader are packaged as `lib/arm64-v8a/libproot.so` / `libproot-loader.so` with legacy native-lib
  packaging, so Android extracts them as real executable files on every device.
* **targetSdk 28 is deliberate**: apps targeting 28 or lower may `execve()` files in their private data directory, which
  the extracted rootfs needs. Android 15+ only refuses to install apps below targetSdk 24.
* Hermes is pinned to release tag `v0.23.3` (commit-verified). The rootfs build runs the official installer stages but
  leaves out Node/pnpm/ffmpeg/browser tooling, then seals the install (removes `.git`) so a cold `hermes` launch never
  tries to sync dependencies over the network.
* The API server is loopback-only with a random per-install key stored in app-private storage.
* Credentials: three modes (API key [default], Vertex service-account JSON, Vertex user credentials). Switching modes
  regenerates Hermes's `.env`/`config.yaml` from scratch, so nothing stale remains. The shared provider config
  generator is checked against golden files in `rootfs/smoke/fixtures/`, which CI boots with the real gateway.

## Build

CI does everything (see `.github/workflows/build.yml`). Locally:

```sh
export ANDROID_HOME=/path/to/android-sdk     # platforms;android-37.2, build-tools;36.0.0, ndk;27.3.13750724
proot/build-proot.sh --abi arm64-v8a --out out/proot
sudo rootfs/build-rootfs.sh --arch arm64 --out out/rootfs     # on an arm64 Linux host
./gradlew :app:assembleDebug -ProotfsArchive=$PWD/out/rootfs/rootfs-arm64.tar.xz -PprootDir=$PWD/out/proot
```

Without those two properties a stub APK is built (it starts, but shows "stub build"); handy for UI work.
`./gradlew :app:testDebugUnitTest` runs the unit tests (SSE parsing, redaction, tar extraction, credential parsing,
provider config golden files).

## Signing key (sideload test builds only)

`app/sideload-debug.keystore` is **committed on purpose**, with the conventional `android` / `android` credentials
(alias `androiddebugkey`). It exists so that CI-built test APKs are signed consistently and can update each other
on a phone. It must never be used for anything else, and no other credential of any kind is stored in this repository.

## On the phone

1. Download the APK from the latest `phase0-*` prerelease on the GitHub Releases page and open it (allow installs from
   the browser or file manager when asked). About 1.5 GB of free storage are needed (the APK plus the extracted runtime of about 1 GB).
2. Open PocketAgent. Press **Install and start**. First start extracts the rootfs (progress shown), then boots Hermes
   (the first boot can take a minute or two; a slow phone shows "still starting" instead of an error). Hermes boots
   even before credentials exist, so this can be done first.
3. In **Setup** choose a credential mode, enter/import credentials, **Save and apply** (a running runtime restarts itself).
   Chat stays disabled until the saved setup is complete.
4. Chat once the state shows *Running (health OK)*.
5. If a start fails (red error, or "still starting" for several minutes), the app runs a self-test by itself (about a
   minute; the screen says so). **Wait until that message disappears**, then **Debug > Copy diagnostics** (or *Share as
   file*) and paste it back. A dump taken while the self-test runs says so at the top and in the self-test section.
   If the state shows signal 31 / SIGSYS, switch on *Disable proot seccomp filter* and restart.

## Security notes

* **Only install builds from this repository's Releases page.** The committed sideload keystore is public, so
  anybody can produce an APK that Android accepts as an update of this app. Treat any APK from elsewhere as hostile.
* **proot is not a security boundary.** The agent's terminal tool runs as the app's own user, so it can read the
  app's private files, including the stored API key / service-account JSON / the API-server key. Untrusted content
  the agent reads (web pages, files, messages) can try to talk it into doing so (prompt injection). Do not point the
  agent at material you do not trust, and use credentials that are limited in scope and budget.
* The API server listens on `127.0.0.1` only, on a random port per launch. The app sends its Bearer key only after
  the child process announced that exact port in its own output, and (where Android lets apps read
  `/proc/net/tcp`) the listener's uid is ours. Loopback calls bypass any system proxy.
* Pinning: the Hermes installer is fetched by commit SHA and the tag is verified against that commit; Python
  dependencies are installed by Hermes's own `uv sync --locked` from its hash-carrying `uv.lock`; uv, Python and
  ripgrep come from Hermes's hash-pinned `pm/lock.json`; proot by commit and talloc by SHA-256; GitHub Actions by commit
  SHA; the Gradle distribution by SHA-256. Still unpinned: the Debian trixie packages (current mirror state at build
  time) and Gradle/AGP/Maven dependency resolution (versions pinned, no dependency lockfile).

## Updates and user data

Your chat history, memories and skills live in Hermes's home inside the rootfs. When an APK update replaces the rootfs,
the app first moves that user data aside, replaces the rootfs, and moves it back (credentials and settings live outside
the rootfs and are never touched). Install artifacts (Hermes code, Python) always come from the new build. If an update
is interrupted, the parked data is restored on the next start.

## Known limitations

* The Hermes `/v1/chat/completions` stream is not resumable: if the app is killed mid-answer, resend.
