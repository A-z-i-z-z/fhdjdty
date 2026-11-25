# Wave 0, area BUILD: the owner's build, the test twin, signing, versions, R8, CI (draft contract)

Plan: 7.0 feature 0.1 (findings A1, A15; experiments S2, S4, S6). Written against the tree at commit `344829f`.
Ground rules are those of `docs/phase3-plan.md` (do not run Gradle for the sake of it, own files only, `REQUESTS` for
anything else). The agent cannot compile: every Gradle spelling below binds in meaning, and the integration agent may
fix spelling for AGP 9.5.2 (new DSL) without asking.

Facts checked for this draft (commands run, not remembered): `app/proguard-rules.pro` does not exist; `app/src/debug/`
does not exist; `androidx.profileinstaller:1.4.0` is already on the classpath (`ProfileInstallReceiver` is in
`app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml`), so no new library;
local debug APK is 255,302,892 bytes of which `assets/rootfs.tar.xz` is 227,984,352 stored, so "the app without the
Linux image" is 27,318,540 bytes today (debug, no R8); `AndroidManifest.xml` contains no literal package name
(`${applicationId}` only); `dist/exp-results/E0-twin.md`: a twin built with a `.debug` suffix compiled, and its first
install on the phone was BLOCKED by Play Protect (`INSTALL_FAILED_VERIFICATION_FAILURE`, "Install anyway" not tapped).

## 1. Scope

| # | Work item | Core |
|---|---|---|
| B1 | `app/build.gradle.kts`: two identities (release = owner `dev.pocketagent.app`, debug = twin `dev.pocketagent.app.debug`), version scheme, BuildConfig fields, manifest placeholders, `namespace` unchanged | core |
| B2 | Signing: `owner` config from env/properties file, fallback to the committed key, one-step switch later; `lint { checkReleaseBuilds = false }` | core |
| B3 | R8 + resource shrinking on `release` (switch `-Pshrink`), not debuggable, `-dontobfuscate`; `app/proguard-rules.pro`; `app/src/main/res/raw/keep.xml` | core |
| B4 | `app/src/main/baseline-prof.txt` (hand-written; Compose's library profiles merge by themselves) | **cut first (1)** |
| B5 | Twin look under `app/src/debug/`: label "Agirî Test" (3 locales), marked launcher icons (12 overlays), shortcuts that target the twin | core |
| B6 | `-Pperf=true`: the twin as an optimised, non-debuggable build, for the robot's numbers without touching the owner's app | **cut first (2)** |
| B7 | `tools/build-ids.ps1`: the one place the tools take package names from | core |
| B8 | `tools/local-build.ps1` rewritten around the two identities, with the install safety rules | core |
| B9 | `.github/workflows/build.yml`: two APKs, checksums, checks, size, lint and strings steps | core |
| B10 | `tools/ci/apk-size.sh`, `tools/ci/verify-apk.sh`, `tools/ci/sign-owner.sh`, `tools/size-budget.txt`, their shell test | core (the lineage option inside `sign-owner.sh` and `local-build.ps1` is **cut first (3)**) |
| B11 | Tests (section 8) and `.gitignore` additions | core |

Cut first, in this order, if the session runs out: (1) B4: nothing breaks; a fresh CI install is slower to warm up until
`compile -m speed -f` has run, which `-Release` does anyway. (2) B6: optimised numbers can then only be taken on the
owner's app (after `-Release -Install`), which is allowed but touches it. (3) the lineage option: only needed when the
private key arrives (S6 is not done in this run).

## 2. Files (BUILD owns all; one line each)

| File | Change |
|---|---|
| `app/build.gradle.kts` | B1, B2, B3, B6 as in section 3 (build types, version math, signing resolution, BuildConfig, placeholders, `lint`, R8). Existing runtime-parts task, packaging, `noCompress`, `testOptions` untouched |
| `app/proguard-rules.pro` | New: the rules of section 3.4 |
| `app/src/main/res/raw/keep.xml` | New: `tools:keep` for launcher, shortcut, tile, notification icons and `@raw/sfx_*`; `tools:shrinkMode="strict"` NOT set (lenient) |
| `app/src/main/baseline-prof.txt` | New (B4): startup rules for `PocketApp`, `MainActivity`, `ui/AppRoot`, `ui/Nav`, `ui/home/**`, `ui/theme/**`, `ui/components/**`, `ui/pixel/**`; `Ldev/pocketagent/app/ui/**;` class lines. Wildcards (`**`) are supported by AGP's profile compiler to the best of knowledge; if the first release build rejects the file, delete it (item is cut-first) |
| `app/src/debug/res/values/strings_build.xml`, `values-tr/…`, `values-fr/…` | New: override `app_name`, `srf_ask_agiri`, `srf_tile_label` with the same neutral text in all three (section 5) |
| `app/src/debug/res/drawable/twin_badge.xml` | New: 108x108 vector, band `#FFF5A524` x=36..72 y=67..81, "TEST" in 3x5-cell pixel letters, cell 2 units, from x=39 y=69, colour `#FF140A20` (T `111/010/010/010/010`, E `111/100/111/100/111`, S `111/100/111/001/111`, T again, 1 cell gap) |
| `app/src/debug/res/drawable/twin_fg_{newroz,pet,flame,zagros,kawa,roj}.xml` | New: `layer-list` of the owner's foreground (`ic_launcher_foreground`, `ic_launcher_pet_foreground`, `…_flame_…`, `…_zagros_…`, `…_kawa_…`, `…_roj_…`) and `@drawable/twin_badge` |
| `app/src/debug/res/mipmap-anydpi-v26/ic_launcher{,_round,_pet,_pet_round,_flame,_flame_round,_zagros,_zagros_round,_kawa,_kawa_round,_roj,_roj_round}.xml` | New (12): copy of the main file with only `<foreground android:drawable="@drawable/twin_fg_X"/>` changed; background and monochrome references unchanged |
| `app/src/debug/res/xml/shortcuts.xml` | New: copy of main's three shortcuts (same ids, actions, labels, icons, `targetClass`), but `android:targetPackage="dev.pocketagent.app.debug"`. Without it the twin's shortcuts would open the OWNER's app |
| `tools/build-ids.ps1` | New (B7), section 3.3 |
| `tools/local-build.ps1` | Rewritten (B8), section 3.3 |
| `tools/ci/apk-size.sh`, `tools/ci/verify-apk.sh`, `tools/ci/sign-owner.sh`, `tools/ci/test-apk-size.sh` | New (B10), section 3.3 |
| `tools/size-budget.txt` | New, section 3.3 |
| `.github/workflows/build.yml` | Section 3.5 |
| `.gitignore` | Append: `owner-signing.properties`, `*.jks`, `*.p12`, `lineage*.bin`, `/dist/perf-build/` (never remove the existing lines; `app/sideload-debug.keystore` stays tracked) |
| Tests | `app/src/test/java/dev/pocketagent/app/{BuildContractTest,ProguardRulesTest,TwinOverlayTest,LocalBuildSafetyTest,SizeBudgetFileTest}.kt` |

NOT touched although the plan mentions them: `AppLinks.kt`, `shortcuts.xml` in `main`, `AndroidManifest.xml` (NAV); `Diagnostics`/Export logs
(CONFIG, see section 11); `AGENTS.md` and docs (lead).

## 3. Shared symbols BUILD PROVIDES

### 3.1 Gradle inputs (all optional) and what they do

| Property / env | Meaning |
|---|---|
| `-PciRun=<int>` (alias `-PbuildNumber`, kept one wave) | CI run number the build belongs to; default 0 |
| `-PlocalSeq=<0..99>` | 0 = a CI build; 1..99 = a local build on top of that run (coerced into range) |
| `-PgitCommit=<text>` | unchanged from today (default `git rev-parse --short=12 HEAD`, else `unknown`) |
| `-PsigningKind=private\|committed` | CI only: says that CI re-signs the owner APK with the private key afterwards (BuildConfig must tell the truth) |
| `-Pshrink=true\|false` | default `true`. `false` = R8 and resource shrinking off for the optimised builds (the S2 fallback: flip the default here, one line) |
| `-Pperf=true` | B6: the debug type is built optimised (`isMinifyEnabled`, `isShrinkResources` = `shrink`, `isDebuggable = false`), `APP_VARIANT = "twin-perf"`. Same package as the twin |
| `-PownerSigning=<path>`, or file `<user.home>/.agiri/owner-signing.properties` | keys `storeFile`, `storePassword`, `keyAlias`, `keyPassword`, optional `lineageFile` |
| env `AGIRI_KEYSTORE_FILE`, `AGIRI_KEYSTORE_PASSWORD`, `AGIRI_KEY_ALIAS`, `AGIRI_KEY_PASSWORD`, `AGIRI_SIGNING_LINEAGE` | override the file, key by key |

Signing resolution for the `release` type, in this order: env (all four) -> properties file (all four) -> committed
`sideload` config. Some but not all of the four -> the build FAILS with a message naming the missing ones (never a silent
fallback). Result is logged once (`logger.lifecycle("AGIRI: owner build signed with the private key")` or `"... with the committed test key"`) and
sets `SIGNING_KIND`. `debug` always uses `sideload`.

Version scheme (one rule for both; local builds can never outrun CI):

| Build | `versionCode` | `versionName` |
|---|---|---|
| CI owner, run n | `(10000+n)*100` | `0.2.n` |
| CI twin, run n | `n*100` | `0.2.n-test` |
| local on run n, seq s | the same plus `s` | `0.2.n-local<s>` (`-test` appended for the twin) |
| no properties | owner 1000000, twin 1 (`max(…,1)`) | `0.2.0`, `0.2.0-test` |

```kotlin
// semantics that bind (spelling may need adjusting)
val versionBase = "0.2"
val ciRun = prop("ciRun")?.toIntOrNull() ?: prop("buildNumber")?.toIntOrNull() ?: 0
val localSeq = (prop("localSeq")?.toIntOrNull() ?: 0).coerceIn(0, 99)
fun codeFor(offset: Int) = maxOf((offset + ciRun) * 100 + localSeq, 1)
defaultConfig { versionName = "$versionBase.$ciRun" + (if (localSeq > 0) "-local$localSeq" else "") }
buildTypes {
  debug   { applicationIdSuffix = ".debug"; versionNameSuffix = "-test"; signingConfig = sideload
            buildConfigField("boolean","IS_TWIN","true"); buildConfigField("String","APP_VARIANT","\"twin\"") /* "twin-perf" with -Pperf */
            manifestPlaceholders += mapOf("deepLinkScheme" to "agiri-test", "appVariant" to "twin") }
  release { isMinifyEnabled = shrink; isShrinkResources = shrink; isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("boolean","IS_TWIN","false"); buildConfigField("String","APP_VARIANT","\"owner\"") }
}
// versionCode per type via androidComponents.onVariants { it.outputs.forEach { o -> o.versionCode.set(codeFor(if (release) 10000 else 0)) } }
```
`namespace` stays `dev.pocketagent.app`: classes, `R`, `BuildConfig` and manifest-relative names (`.MainActivity`, `.LauncherPet`) keep their names; only the
installed package id changes. `lint { abortOnError = false; checkReleaseBuilds = false }`.

### 3.2 Kotlin: `dev.pocketagent.app.BuildConfig` (the full list; others are AGP's own)

| Field | Type | Values |
|---|---|---|
| `IS_TWIN` | `Boolean` | `true` for `debug` (also with `-Pperf`), `false` for `release`. **Every debug-only path (debug links, clock pin, forced layout direction, the stretched test language) is gated by this, never by `BuildConfig.DEBUG`** (`DEBUG` is false in the perf twin) |
| `APP_VARIANT` | `String` | `"owner"`, `"twin"`, `"twin-perf"` |
| `OWNER_PACKAGE` | `String` | `"dev.pocketagent.app"` always (the twin needs it to name the owner's app in a warning) |
| `CI_RUN` | `Int` | `-PciRun`, 0 when absent |
| `SIGNING_KIND` | `String` | `"committed"` or `"private"` |
| `DEEP_LINK_SCHEME` | `String` | `"agiri"` (owner), `"agiri-test"` (twin) |
| existing | | `GIT_COMMIT`, `VERSION_NAME`, `VERSION_CODE`, `APPLICATION_ID` (= `dev.pocketagent.app.debug` in the twin), `BUILD_TYPE`, `DEBUG` |

Manifest placeholders (besides AGP's `${applicationId}`): `${deepLinkScheme}` (same values), `${appVariant}` (same as `APP_VARIANT` without perf). No other.
Intent actions (`dev.pocketagent.app.OPEN_CHAT` and the rest), the `agiri://` URIs, channel ids and pref names stay literal and unchanged: they are
delivered with explicit components, no filter in the manifest claims them, so two apps may share the strings (E0 read every site). A future
`VIEW agiri://` filter must use `${deepLinkScheme}` (section 7).

### 3.3 Scripts (exact command lines and output)

`tools/build-ids.ps1` (dot-sourced by `local-build.ps1`, `phone-test.ps1`, `phone-perf.ps1`; defines, no side effects):
```powershell
$PaNamespace = 'dev.pocketagent.app'; $PaOwnerPackage = 'dev.pocketagent.app'; $PaTwinPackage = 'dev.pocketagent.app.debug'
$PaMainActivity = 'dev.pocketagent.app.MainActivity'
function Get-PaComponent([string]$Package, [string]$ClassName) # -> "<Package>/<fully qualified class>"; a ClassName starting with '.' gets $PaNamespace (NOT $Package) prepended
function Test-PaOwnerPackage([string]$Package)                  # [bool]: $true only for 'dev.pocketagent.app'
```
Never write `adb … -n <pkg>/.Class` for the twin: the shorthand resolves against the package id and points at a class that does not exist.

`tools/local-build.ps1` (run through `powershell -NoProfile -ExecutionPolicy Bypass -File tools\local-build.ps1 [switches]`):

| Switch | Behaviour |
|---|---|
| (none) | Gradle `:app:assembleDebug` -> `app\build\outputs\apk\debug\app-debug.apk` (twin) |
| `-Release` | `:app:assembleRelease` -> `app\build\outputs\apk\release\app-release.apk` (owner). Without `-Install` nothing touches the phone |
| `-Perf` | B6: `:app:assembleDebug -Pperf=true`, copies the result to `dist\perf-build\app-twin-perf.apk`; install target = twin; compile step after install |
| `-Install` / `-Start` | install the APK built or `-NoBuild`-found for the chosen identity; `-Start` then `am start -n <pkg>/dev.pocketagent.app.MainActivity` (via `Get-PaComponent`) |
| `-NoCompile` | skip the post-install `adb shell cmd package compile -m speed -f <pkg>` (done after `-Release -Install` and `-Perf -Install`, never for the plain twin) |
| `-CheckSigning` | print the two certificate digests and exit: 0 same, 3 different or app not installed |
| `-BuildNumber <n>` | = `-PciRun`; default = run number of `dist\runtime-parts\source.txt`; refused when lower |
| `-FetchParts [phase0-N]`, `-FromApk`, `-Sums`, `-Test`, `-NoBuild`, `-ListDevices`, `-Serial` | as today. `-FetchParts` now downloads `Agiri-*-test.apk` (falls back to the legacy `PocketAgent-phase0-*.apk` for old tags) and expects exactly one match; `-FromApk` reads the run from `Agiri-(\d+)` or `phase0-(\d+)` |

Install rules (functions with these exact names, tested by `LocalBuildSafetyTest`):
`Get-ApkPackage($apk)` (`aapt2 dump packagename`), `Get-ApkCertSha256($apk)` (`apksigner verify --print-certs`, line `Signer #1 certificate SHA-256 digest:`),
`Get-InstalledCertSha256($pkg)` (`pm path` -> `adb pull` the base APK to a temp file -> `Get-ApkCertSha256` -> delete; `dumpsys` hashes are NOT used: they are not SHA-256),
`Get-InstalledVersionCode($pkg)` (`dumpsys package`, `versionCode=`), `Resolve-LocalSeq($ciRun,$installedCode,$offset)` (installed < base: 1; inside the run's 99 slots: next; newer CI run installed: stop and tell to `-FetchParts`),
`Assert-OwnerInstallAllowed($apk)`, `Invoke-CompileSpeed($pkg)`, `Get-CompileState($pkg)`.
1. Every install first runs `Get-ApkPackage` and requires it to match the chosen identity (stale APK guard).
2. Package `dev.pocketagent.app` is installed ONLY with `-Release`, and only when `Assert-OwnerInstallAllowed` passes: not installed (fresh install, notice) OR installed certificate digest equals the built APK's AND new `versionCode` > installed. Otherwise exit 3, phone untouched.
3. The script contains no `uninstall`, no `pm clear`, no `settings put`, no verifier or low-target-SDK bypass, and uses `-d` only for the twin package. The owner's package never gets `-d` or a retry.
4. `INSTALL_FAILED_VERIFICATION_FAILURE` (Play Protect, E0): print "Play Protect blocked the first install of the test twin. On the phone tap 'Install anyway' once (the owner's decision); updates of an installed twin are not asked again." Exit 1. No workaround is attempted.
Output lines the robot and humans can grep: `APK: <path>`, `PACKAGE: <id>`, `VERSION: <name> (<code>)`, `CERT-SHA256: <hex>`, `INSTALLED-CERT-SHA256: <hex|none>`, `COMPILE: <status or unknown>` (from `dumpsys package <pkg>` "Dexopt state", the `[status=…]` of arm64). Exit codes: 0 ok, 1 error, 2 expected Windows symlink test failures (as today), 3 refused for safety. Help text version string becomes `0.2.<n>` (today's `0.0.<n>-phase0`, line 18, was already wrong).

`tools/ci/apk-size.sh <apk> <owner|twin> <budget-file> [out-file]` (POSIX sh, needs `unzip`, `stat`): prints and writes
`variant=`, `apk_bytes=`, `rootfs_bytes=` (stored size of `assets/rootfs.tar.xz`), `app_bytes=` (apk - rootfs), `dex_bytes=` (sum of `classes*.dex`, compressed), `native_bytes=` (`lib/**`, compressed), `ceiling_bytes=<n|none>`, `verdict=ok|over|unset`. Exit 0 for ok/unset, 1 for over, 2 usage.
`tools/ci/verify-apk.sh <apk> <package> <debuggable:0|1> <expect-versionCode|->`: asserts package (`aapt2 dump packagename`), `application-debuggable` presence, versionCode, entries `lib/arm64-v8a/libproot.so` and `assets/rootfs.tar.xz` (method Stored), prints `apksigner verify --print-certs` digest; exit 1 on any mismatch.
`tools/ci/sign-owner.sh <in.apk> <out.apk>`: env `KS_B64`, `KS_PASS`, `KEY_ALIAS`, `KEY_PASS`, optional `LINEAGE_B64`; decodes to a temp dir with `trap` cleanup, `apksigner sign --ks … --ks-pass env:KS_PASS --key-pass env:KEY_PASS [--lineage …]`, then `apksigner verify`. If `KS_B64` is empty it copies in to out and prints `signing=committed`; else `signing=private`. Never prints a secret.
`tools/size-budget.txt` (key=value, `#` comments):
```
owner_baseline_bytes=0        # app_bytes of the first green release build; the lead fills it after run 1 of Wave 0
owner_ceiling_bytes=0         # 0 = not set yet (verdict=unset, never fails). Wave 0 sets baseline + 1048576; every wave adds its share of the 15 MiB
twin_recorded_bytes=27318540  # debug build today, recorded only, the twin is never gated
```

### 3.4 `app/proguard-rules.pro` (exact content to write)
```
# Agirî optimised build (release, and the twin with -Pperf=true). Names stay readable on purpose:
# crash traces in app.log must be readable without a mapping file.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*
# sherpa-onnx: the native code looks up classes, fields and methods by name (see the JNI trap in AGENTS.md)
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }
# enums stored in preferences or JSON by name, read back with valueOf / entries
-keepclassmembers enum dev.pocketagent.app.** { <fields>; public static **[] values(); public static ** valueOf(java.lang.String); }
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }
# the rootfs extractor reads tar and xz through commons-compress and xz
-keep class org.apache.commons.compress.archivers.tar.** { *; }
-keep class org.apache.commons.compress.compressors.xz.** { *; }
-keep class org.tukaani.xz.** { *; }
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**
-dontwarn org.osgi.**
# the first R8 run lists anything else it misses in app/build/outputs/mapping/release/missing_rules.txt: copy those -dontwarn lines here
```
Verified need: `SpriteBank.kt:255` and `RuntimeManager.kt:603` reflect on platform classes (no rule); `ComponentName` alias class names are manifest-kept; the app has no `getIdentifier`, no `ServiceLoader`; resource names looked up by string: none.

### 3.5 `.github/workflows/build.yml` (changes; job names and pinned action SHAs stay)
- `unit`: before "Unit tests" add steps, each failing the job on a non-zero exit: `pwsh --version`; `pwsh -NoProfile -File tools/lint-ui.ps1 -Check`; `pwsh -NoProfile -File tools/strings-check.ps1 -Check`; `pwsh -NoProfile -File tools/make-test-language.ps1 -Check`; `sh tools/ci/test-apk-size.sh`. The existing syntax loop gets `tools/ci/*.sh`. `testDebugUnitTest` unchanged.
- `apk`: new step "Detect private key" (env `KS_B64: ${{ secrets.AGIRI_KEYSTORE_B64 }}`, output `kind=private|committed`, no secret echoed). Gradle: `./gradlew --no-daemon :app:assembleRelease :app:assembleDebug -ProotfsArchive=… -PprootDir=… -PciRun=${{ github.run_number }} -PgitCommit=${GITHUB_SHA::12} -PsigningKind=${{ steps.key.outputs.kind }}`. Then: copy `app/build/outputs/apk/release/app-release.apk` to `Agiri-<run>-unsigned.apk` -> `sh tools/ci/sign-owner.sh` (secrets only in THIS step's `env:`) -> `Agiri-<run>.apk`; copy `debug/app-debug.apk` -> `Agiri-<run>-test.apk`; `sh tools/ci/verify-apk.sh Agiri-<run>.apk dev.pocketagent.app 0 $(( (10000+run)*100 ))` and `… Agiri-<run>-test.apk dev.pocketagent.app.debug 1 $(( run*100 ))`; `sh tools/ci/apk-size.sh` for both into `size-report.txt` (a verdict `over` fails the job); `sha256sum Agiri-<run>.apk Agiri-<run>-test.apk > SHA256SUMS.txt`; step summary lists names, sizes, signer digests.
- Artifact `apk` carries: both APKs, `SHA256SUMS.txt`, `rootfs-arm64.info`, `proot-build.info`, `size-report.txt` (replaces `apk-size.txt`).
- `release`: tag stays `phase0-<run>` (the local script and `gh release list` rely on it), title "Build <run>", assets: both APKs, sums, the two infos, `size-report.txt`; replace `ls PocketAgent-phase0-*.apk` (line 219) by the two exact names; notes say which key signed the owner APK (from the artifact's `signing=` line) and that the twin always uses the shared public test key.

## 4. Shared symbols BUILD CONSUMES

| From | What I expect |
|---|---|
| ROBOT | `tools/lint-ui.ps1 -Check`: exit 0 when the counts do not exceed the committed baseline, 1 when they do; runs under PowerShell 7 (`pwsh`) on Linux (forward slashes, no Windows-only APIs); baseline file committed in the repo (not under `dist/`) |
| TEXT | `tools/strings-check.ps1 -Check` and `tools/make-test-language.ps1 -Check`, same exit and `pwsh` rules; the stretched test language's generated files are COMMITTED under `app/src/debug/res/values-<qualifier>/` (TEXT owns that subtree, BUILD never writes there) and `-Check` fails when they are stale; the strings check scans only `app/src/main/res` (my `app/src/debug/res/values*/strings_build.xml` override existing keys and must not count as orphans) |
| NAV | Class names stay `dev.pocketagent.app.MainActivity`, `.RestartActivity`, `.service.FireTileService`, aliases `.Launcher{Newroz,Pet,Flame,Zagros,Kawa,Roj}`; manifest keeps `${applicationId}` and `android:label="@string/app_name"` on `<application>` and all six aliases; no literal package string |
| CONFIG | `rootfs/`, `proot/` build outputs and `rootfs-arm64.info` / `proot-build.info` names unchanged (CI and `-FetchParts` read them) |

## 5. Strings

No new keys in `app/src/main/res`. Twin overrides (not translatable product text; the same value in all three locale files because `values-tr` and `values-fr` of `main` would otherwise win, as E0 found):

| Key | Value (en = tr = fr) | Note for translators |
|---|---|---|
| `app_name` | `Agirî Test` | Name of the test twin on the launcher and in share sheets. Never translate |
| `srf_ask_agiri` | `Ask Agirî Test` | Text-selection menu entry of the twin; must differ from the owner's entry |
| `srf_tile_label` | `Agirî Test fire` | Quick Settings tile of the twin |

## 6. Manifest entries

None to add. NAV keeps the invariants of the consume table. The merged twin manifest must show `package="dev.pocketagent.app.debug"`, provider authority `dev.pocketagent.app.debug.fileprovider`, process `dev.pocketagent.app.debug:restart`. If NAV ever adds a `VIEW` filter for `agiri://…`, its `android:scheme` must be `${deepLinkScheme}`.

## 7. REQUESTS

- NAV: `AppLinks.kt` / `MainActivity.kt`: gate every debug-only link (open any route, pin the clock, force language, force RTL) with `BuildConfig.IS_TWIN`; keep the `ACTION_*` strings and `SCHEME = "agiri"` as they are; when a `VIEW` filter exists use `${deepLinkScheme}` and `BuildConfig.DEEP_LINK_SCHEME`. Their tests keep the literal action strings (AppLinksTest 54-57 stays valid).
- CONFIG: `PocketApp.kt:144`: extend the log line to `started Agiri build ${GIT_COMMIT} (${VERSION_NAME}) variant=${APP_VARIANT} signing=${SIGNING_KIND} package=${APPLICATION_ID}`. `diag/Diagnostics.kt:73-74`: add one line `variant: ${APP_VARIANT}  signing key: ${SIGNING_KIND}  ci run: ${CI_RUN}` under `build type`. Only if S4 fails: a Diagnostics line when the app finds itself uncompiled.
- TEXT: see consume table (`-Check` flags, `pwsh`, committed generated files, scan scope).
- ROBOT: dot-source `tools/build-ids.ps1`; default package = `$PaTwinPackage`; refuse `$PaOwnerPackage` without an explicit `-Owner` switch; build component names only with `Get-PaComponent` (twin shorthand `/.X` is wrong); read logs with `run-as $PaTwinPackage`; the perf script targets the twin (optionally the `-Perf` build).
- Lead: update `AGENTS.md` "Testing on the phone" and the repo map with the twin package, `local-build.ps1` switches and the version scheme; the CI secrets to create later are `AGIRI_KEYSTORE_B64`, `AGIRI_KEYSTORE_PASSWORD`, `AGIRI_KEY_ALIAS`, `AGIRI_KEY_PASSWORD`, optionally `AGIRI_LINEAGE_B64`.
- SWEEP, WORDS, DESIGN, STAGE, STREAM: none.

## 8. Tests (JVM, no instrumentation)

The Gradle script and the PowerShell cannot run in a JVM test, so the logic is kept checkable in three ways: the facts that cross the boundary live in `BuildConfig` (read by the test), the invariants live in plain text files (parsed by the test, working directory is `app/`, `File("../tools/…")` with a fallback to `File("tools/…")`), and the shell logic has its own `sh` test run by CI.

| Class > test | Proves |
|---|---|
| `BuildContractTest > the twin id is the owner package plus .debug` | `BuildConfig.APPLICATION_ID == BuildConfig.OWNER_PACKAGE + ".debug"`, `IS_TWIN`, `APP_VARIANT in {"twin","twin-perf"}` |
| `BuildContractTest > BuildConfig exposes the documented fields` | reflection finds `IS_TWIN:boolean`, `APP_VARIANT`, `OWNER_PACKAGE`, `CI_RUN:int`, `SIGNING_KIND`, `DEEP_LINK_SCHEME`, with the documented value sets |
| `BuildContractTest > classes keep their names under the suffix` | `BuildConfig::class.java.name == "dev.pocketagent.app.BuildConfig"` and `MainActivity` likewise (namespace unchanged) |
| `BuildContractTest > version code follows the scheme` | `VERSION_CODE == max(CI_RUN*100 + seq, 1)` with `seq` parsed from `-local(\d+)` in `VERSION_NAME`; name matches `^0\.2\.\d+(-local\d+)?-test$` for the twin |
| `BuildContractTest > build-ids script agrees with Gradle` | `tools/build-ids.ps1` literals equal `OWNER_PACKAGE`, `OWNER_PACKAGE + ".debug"` and `MainActivity` FQN |
| `ProguardRulesTest > required rules are present` | the file contains `-dontobfuscate`, the sherpa keep, the native-method keep, both enum keeps, the tar, xz and tukaani keeps |
| `ProguardRulesTest > nothing weakens the build` | no `-dontshrink`, `-dontoptimize`, `-ignorewarnings`, no blanket `-dontwarn **` / `-dontwarn *` |
| `TwinOverlayTest > shortcuts overlay matches main` | same shortcut ids, actions, `targetClass`; main targets `dev.pocketagent.app`, overlay `dev.pocketagent.app.debug` |
| `TwinOverlayTest > every launcher icon has a twin overlay` | the 12 file names in `src/debug/res/mipmap-anydpi-v26` equal those in `main`, each refers to a `twin_fg_*` that exists |
| `TwinOverlayTest > labels are overridden in all three locales` | the three `strings_build.xml` define exactly the three keys with the same text |
| `TwinOverlayTest > the manifest names no package` | `AndroidManifest.xml` has no `dev.pocketagent.app` substring and every `<application>`/alias label is `@string/app_name` |
| `LocalBuildSafetyTest > the script cannot destroy the owner's app` | `tools/local-build.ps1` has none of: `uninstall`, `pm clear`, `settings put`, `bypass`, `verifier`; has the named functions of 3.3; `-d` occurs only in lines that also mention the twin variable |
| `SizeBudgetFileTest > the budget file parses` | keys present, numbers, and `owner_ceiling_bytes` is 0 or >= `owner_baseline_bytes` |
| shell: `tools/ci/test-apk-size.sh` | builds fake APKs with `zip` (stored rootfs entry, a dex, a lib): `app_bytes = apk - rootfs`, `over` exits 1, ceiling 0 gives `unset` exit 0, bad usage exits 2 |

## 9. Robot hooks

No screens, so no testTags. For the robot (exact):
- Twin id `dev.pocketagent.app.debug`; start `adb shell am start -n dev.pocketagent.app.debug/dev.pocketagent.app.MainActivity`; links `adb shell am start -a dev.pocketagent.app.TERMINAL -n dev.pocketagent.app.debug/dev.pocketagent.app.MainActivity`; kill `adb shell am kill dev.pocketagent.app.debug`; logs `adb shell run-as dev.pocketagent.app.debug cat files/logs/app.log` (twin only: the optimised twin and the owner's build are not debuggable, so no `run-as`); tile `cmd statusbar add-tile dev.pocketagent.app.debug/dev.pocketagent.app.service.FireTileService`.
- The `app.log` start line (request to CONFIG) proves which build ran: `variant=twin signing=committed package=dev.pocketagent.app.debug`.
- Practice-model keywords: none needed. Debug links: none of mine; NAV's are gated by `IS_TWIN`.
- Smoke scenario for BUILD, on the twin: install, start, force-stop, start, `noCrash`; then one against the owner build after a release (`-Release -Install`, `COMPILE: speed`).

## 10. Experiment branches

- **S2 passes (written for this case):** release and `-Pperf` run with R8 and resource shrinking. **If S2 fails** (a `NoSuchMethodError`, `ClassNotFoundException` or native abort in logcat for Hermes start, neural voice, backup/restore or icon switch): first add the missing keep to section 3.4 and retry once; if it still fails, change the default of `-Pshrink` to `false` (one line). The build stays non-debuggable and `compile -m speed -f` still applies; ProguardRulesTest stays green.
- **S4 passes:** `local-build.ps1` compiles once after `-Release -Install`. **If S4 fails** (filter back to `verify` after a day): the script already repeats the compile at every install; add the Diagnostics line requested from CONFIG. Second half of S4 (after a reboot) waits for the owner (PROGRESS.md).
- **S6 is not run on the owner's app in this run:** the owner's build keeps the committed key; the whole private-key path (properties file, CI secrets, `sign-owner.sh`, lineage) is written and unit-checked but never exercised against the owner's phone. Switching later = put the four secrets, plus the lineage file, in place; no code change. Without a lineage the install over today's key is refused by Android, so never set the secrets without the lineage.

## 11. Risks and things in the plan that are wrong or impossible

1. **Play Protect blocks the first install of the twin** (`dist/exp-results/E0-twin.md`). The plan's whole "agents test on the twin" rests on one owner tap ("Install anyway"), which is not in plan section 6's list of what the owner does, and the script must not work around it (3.3 rule 4). Needs the lead and the owner before the robot can run at all.
2. **Plan 0.1 "version code = 10,000 + run number" and "local -Release a number above the installed one" cannot both hold:** a local build above CI's next number makes CI builds downgrades, exactly finding A15 (`build.gradle.kts:17, 88`). Replaced by the `*100 + seq` scheme of 3.1 (strictly increasing, local can never pass the next CI run; 99 local builds per run). Needs the lead's yes.
3. **"Everything that spells the package takes it from the build" is partly unnecessary and partly impossible in XML.** Intent actions are names, not packages (explicit delivery, no filters), so they stay literal; `res/xml` has no placeholders. E0 tried `resValue` plus `targetPackage="@string/…"` in `main/res/xml/shortcuts.xml:12,23,34`, unverified (install blocked). I use a twin-only overlay file instead (certain; guarded by `TwinOverlayTest`); the lead may prefer E0's way once the twin installs.
4. **"Export logs" (plan 0.1 last bullet) has no owner:** it is not in BUILD's list and the Diagnostics dialog is `ui/settings/SettingsScreen.kt:131` (SWEEP-only, mechanical). The `FileProvider` already has `cache-path diagnostics/` (`res/xml/file_paths.xml:3`), so no BUILD change is needed once someone owns it.
5. **Baseline profile:** the plan says "packaged"; library profiles (Compose) merge by themselves, `profileinstaller` is on the classpath (checked). An app profile cannot be generated without Macrobenchmark, a new library, so only the hand-written file of B4 is possible.
6. **`dumpsys` cannot prove "same certificate"** (it prints a hash of the certificate bytes, not SHA-256); the script pulls the installed base APK (about 250 MB, seconds over USB) and uses `apksigner`. Slow but certain.
7. **Optimised builds with a 228 MB stored asset:** R8 and resource shrinking only touch dex and resources; `noCompress xz` and `useLegacyPackaging` stay. R8 memory: `gradle.properties` has `-Xmx3g`; if the first release build runs out, raise to `-Xmx4g` (one line, BUILD).
8. **commons-compress optional dependencies** make R8 stop on missing classes; 3.4 pre-lists the usual four, the first run's `missing_rules.txt` completes it. Hard to know without compiling.
9. **Two Hermes at once:** the twin and the owner's app can both run their gateway (about 300 MB each); S7 measures, the robot should stop the owner's Hermes before heavy scenarios. Android's per-app memory limit is per UID, so they do not kill each other.
10. **PowerShell:** CI runs `pwsh` 7, the PC runs 5.1; ROBOT and TEXT scripts must work in both. `local-build.ps1` stays 5.1 only (PC-only).
11. **Release notes and `AGENTS.md`** still say all builds share one public test key and the version `0.0.N-phase0`; both become wrong with this wave (`build.yml:224,229`, `local-build.ps1:18`). `AGENTS.md` is the lead's.
12. **Resource shrinker:** no `getIdentifier` exists, so nothing is looked up by string; `keep.xml` is a belt-and-braces. A resource removed by mistake shows only on the phone (S2 covers icon switch and sounds).

## 12. Open questions for the lead

1. Is the owner willing to tap "Install anyway" once for the twin (risk 1)? Without it BUILD's output cannot be used.
2. OK to replace the plan's version-code rule by the `(10000+run)*100 + seq` scheme (risk 2)?
3. Who owns "Export logs" (risk 4)?
4. Are TEXT's generated test-language files committed (assumed) or generated in CI?
5. Shortcuts: twin overlay (written) or E0's `@string` reference (risk 3)?
