# Agirî

A sideloaded native Android app (`dev.pocketagent.app`) that runs [Hermes Agent](https://github.com/NousResearch/hermes-agent)
(Python) entirely on the phone, inside **proot** with a **Debian trixie arm64 rootfs**. Only the LLM is remote
(Google Gemini via AI Studio or Vertex AI). Named after Mount Agirî and guided by **Pêt**, a pixel-art Kurdistan
newt keeping the Newroz hearth burning.

## Highlights

- **Full Hermes Agent on-device:** runs unmodified `hermes gateway` v0.23.3 on loopback (`127.0.0.1`, random port,
  per-install Bearer key), with built-in terminal (`bash -c`), `SOUL.md`, `MEMORY.md` / `USER.md`, skills, config,
  and raw log inspector.
- **Custom Bots, Group Chats & Bot Forge:** starter personas (Hermes, Rebel, Coder, Dengbêj) plus templates,
  multi-bot group chats with participant bar & `@mention` routing, conversation forking, and autonomous Bot Forge
  (`/forge` or ```` ```pocket-bot ``` ````).
- **Multimodal & Power Chat:** gallery/camera image vision (`TakePicture` without `CAMERA` permission), document
  excerpts mirrored into `/root/.hermes/attachments/`, syntax-highlighted code blocks with one-tap **Run in Terminal**,
  visual searchable **Skill Picker**, in-chat **Chat Controls** (Mode + per-chat Model & Thinking Effort overrides),
  slash command palette (`/plan`, `/goal`, `/ultracode`, `/loop`, `/forge`, `/skills`, `/remember`, `/theme`, …),
  ChatList filter chips, and Android `ACTION_SEND` share target.
- **On-device Neural Voice (Supertonic 3 INT8) & Curated CC0 Sounds:** optional offline 99M neural TTS via
  `sherpa-onnx` (31 languages including TR/EN/FR, 10 female/male voices F1–F5 & M1–M5, automatic Android TTS
  fallback), **per-bot voice & delivery mood** (Natural, Happy, Excited, Serious, Calm, Storyteller + speed),
  voice dictation (STT), and subtle CC0 sound effects (`Hearth`, `Studio`, `Silent`).
- **Pixel-art Identity & Personalisation:** 6 themes (`Newroz`, `Zagros`, `Semender`, `Nergiz`, `Kawa`, `Roj`),
  8 accent colours, English / Turkish / French UI, and **6 switchable adaptive launcher icons**.

## Repository layout

| Path | What |
|---|---|
| `app/` | Kotlin + Jetpack Compose app (`runtime/`, `service/`, `api/`, `data/`, `hermes/`, `voice/`, `diag/`, `ui/`) |
| `app/src/main/assets/pixel/*.pxl` | Pixel-art sprites (mascot, hearth fire, mountain panorama, 16 bot avatars, icons, 6 app icons) |
| `app/src/main/res/raw/sfx_*.ogg` | Curated CC0 UI sound cues (`romainsimon/uisfx`) |
| `runtime/pocketagent-proot.sh` | **The** proot launcher. Shipped as an app asset *and* used by the CI smoke test (POSIX sh / mksh) |
| `rootfs/build-rootfs.sh`, `rootfs/install-hermes.sh` | Builds the Debian trixie rootfs with Hermes preinstalled (arm64, amd64) |
| `rootfs/smoke/` | Smoke test (boots Hermes under proot, checks the API), mock LLM, golden provider config fixtures |
| `proot/build-proot.sh` | Builds static proot + loader from source with the NDK |
| `tools/` | Windows local build/test runner (`local-build.ps1`), pixel previewer, vector icon generator |
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

Without those two properties a stub APK is built (starts UI without rootfs); handy for pure UI work.
On Windows, `tools/local-build.ps1 -Test` runs unit tests and `tools/local-build.ps1 -Start` builds and installs fast
against cached runtime parts in `dist/runtime-parts/`.

## Signing key (sideload test builds only)

`app/sideload-debug.keystore` is **committed on purpose**, with the conventional `android` / `android` credentials
(alias `androiddebugkey`). It exists so that CI-built test APKs are signed consistently and can update each other
on a phone. It must never be used for anything else, and no other credential of any kind is stored in this repository.

## On the phone

1. Download the APK from the latest prerelease on the GitHub Releases page and open it (allow installs from
   the browser or file manager when asked). About 1.5 GB of free storage are needed (the APK plus the extracted runtime of about 1 GB).
2. Open **Agirî** and press **Light the fire**. First start extracts the rootfs (progress shown), then boots Hermes.
3. In **Settings > Provider & model**, choose a credential mode, enter/import credentials, and **Save and apply**.
4. Optionally open **Settings > Voice & Sound** to download the offline Supertonic 3 INT8 voice model (~139 MB),
   and **Settings > Appearance** to pick your theme, accent, and launcher icon style.
5. If a start fails, open **Settings > Diagnostics > Copy diagnostics** (secrets are redacted automatically).

## Security notes

* **Only install builds from this repository's Releases page.** The committed sideload keystore is public, so
  anybody can produce an APK that Android accepts as an update of this app. Treat any APK from elsewhere as hostile.
* **proot is not a security boundary.** The agent's terminal tool runs as the app's own user, so it can read the
  app's private files, including stored credentials and the API-server key. Do not point the agent at untrusted
  material, and use credentials with limited scope and budget.
* The API server listens on `127.0.0.1` only, on a random port per launch. Loopback calls bypass any system proxy.

## Updates and user data

Your chat history, bots, memories and skills live in app-private storage and Hermes's home inside the rootfs. When an
APK update replaces the rootfs, the app parks Hermes user data aside, replaces the rootfs, and restores it
automatically (credentials, settings, chats, and downloaded TTS models live outside the rootfs and are never touched).

## Known limitations

* The Hermes `/v1/chat/completions` stream is not resumable: if the app is killed mid-answer, resend.
* Interactive full-screen CLI programs (e.g. `vim`, `hermes chat`) are not supported in the built-in terminal.
