# Wave 0: the lead's decisions on the drafts

Written 2026-11-25 by the lead after reading the nine drafts (`docs/waves/w0/<AREA>.md`) and the first
experiment results. Where an area file and this file disagree, this file wins; where the contract
(`docs/waves/w0-contract.md`) and this file disagree, the contract wins (it is written later).

## G. What the experiments changed

- **G1. The twin cannot be installed tonight.** Google Play Protect blocks the first install of any new
  package that targets API 28 ("built for an older version of Android"); the dialog has an "Install
  anyway" link that only the owner may tap. Updates of the already installed `dev.pocketagent.app`
  with the same key are not blocked. So: the twin is built exactly as planned and waits for one tap by
  the owner. No agent taps "Install anyway", changes Play Protect or the adb-install verifier
  settings, or uses a flag that skips verification.
- **G2. An interim third build: `owner-debug`.** Until the twin exists on the phone, the wave is proven
  on the owner's own package with a debug build (debuggable, committed key, the same as every build
  before Wave 0). Gradle property `-PownerDebug=true` gives the debug build type without the
  application-id suffix and without the twin's label and icon overlays. `BuildConfig.APP_VARIANT` is
  one of `"owner"` (release), `"twin"` (debug) and `"owner-debug"` (debug with `-PownerDebug`).
- **G3. Test hooks are gated by `BuildConfig.TEST_HOOKS`** (true for the debug build type, whatever
  the package; false for release). Never gate on `BuildConfig.DEBUG` or on the package name, with one
  exception: G4.
- **G4. Anything that writes test data in bulk exists only in the twin** (`APP_VARIANT == "twin"`):
  the seeding link, `wipe`, restoring a backup, replacing the Linux image. On `owner-debug` the robot
  may only run scenarios marked `"safe": true`: they read, navigate, take pictures, and may create a
  chat through the interface only if they delete it again and only with the practice model switched
  on through the interface. The robot refuses every other scenario on the owner's package.
- **G5. The owner's phone gets the release (R8) build only after S2 has passed in the twin.** Until
  then the owner's app stays a debug build (`owner-debug`). CI still builds and publishes the release
  file, so S2 can be tried the moment the twin can be installed.
- **G6. Before the first install of a Wave 0 build over the owner's app**, a read-only copy of the
  app's data (chats, bots, memory, settings; no credentials, no Linux image) is taken to the PC under
  `dist/owner-backup/` (git-ignored). `tools/local-build.ps1 -OwnerDebug` refuses to install without
  the same-certificate check and never uninstalls.
- **G7. S5 passed in a shell harness** (`dist/exp-results/S5-practice-model.md`): the practice model
  runs inside the guest as a child of the same launch; the launcher file does not change; the guest
  command is `sh /opt/agiri/practice/start.sh <port>`; the guest has no `python3` on its path (use
  `/root/.hermes/tools/python-*/bin/python3`); the mock must report usage and a context length; the
  first turn after a cold start may take 30 s. Not yet seen through the app's own interface.
- **G8. S1 and S3 are built with a runtime switch each** (`NavFlags`), default on, because they can
  only be tried once a build is on the phone; the fallbacks of the register stay in the code.
- **G9. S2, S4, S6 and S7 wait for the twin.** `proguard-rules.pro` and the release variant are
  written anyway.

## Decisions per area

**BUILD**
- Version codes: accepted as proposed. Owner: `(10000 + run) * 100 + localSeq`; twin: `run * 100 +
  localSeq`; `owner-debug` uses the owner's scheme (it replaces build 32 on the phone).
- The twin's shortcuts: a twin-only overlay file under `app/src/debug/`, not a string reference.
  With `-PownerDebug` the overlays of `app/src/debug/` must not apply: keep them in a source set that
  only the twin uses (for example `app/src/twin/` added to the debug source set unless
  `-PownerDebug`), and say so exactly.
- The stretched test language (`values-en-rXZ`) is **committed**, lives in the same twin/debug-only
  source set, and CI runs `tools/make-test-language.ps1 -Check` to catch drift. It is also present in
  `owner-debug` builds (it is a debug aid, not test data).
- The first R8 run's missing rules and the first numbers of `tools/size-budget.txt` are filled in by
  the integration agent.

**CONFIG**
- Owns in addition: `MainViewModel.kt`, `api/HermesApi.kt`, `rootfs/smoke/smoke-test.sh`, and the
  log export (`diag/`), including the share intent. ROBOT owns only the button that calls it.
- D1 accepted: the `connections` toolset is withheld too. It is logged as a deviation from the plan.
- The crash offer is a `ConfirmDialog` over any screen. The web switch does not travel in backups in
  Wave 0. B21 stays half closed until Wave 3 (written down in `AGENTS.md`).
- **Practice mode must never read, write or delete the owner's provider setup, secrets or guest
  credential files**, and must be impossible to leave on by accident: a banner while it is on, and it
  is not restored from a backup.

**ROBOT**
- Owns `rootfs/smoke/mock_llm.py` (one file for CI and the app, as proposed).
- Seeding: a debug link, twin only (G4). On `owner-debug` the smoothness run uses one practice chat
  with `LONG` turns and deletes it.
- Scenarios that need the notification shade or the file picker may be marked manual if they cannot
  be driven; approval through the API may end as SKIP with the reason written down.
- The gallery route key is `gallery`; the stretched language tag is `en-XZ`.

**DESIGN**
- "Apart from motion nothing looks different" means: no screen changes its layout, content or colour.
  Sheets, dialogs and notes change as feature 0.6 says. Kerning stays off; no haptics row in Settings;
  only the `glow` and `scrim` colour roles are added now.
- DESIGN names the note API (`PetNotes`); NAV and STREAM call it in their own files.
- `PaletteContrastTest`'s known failures are filled in by the integration agent from the first run.
- Capital letters made in code stay as they are in Wave 0, except that every such site uses the
  chosen language's locale (a helper from TEXT), which fixes the dotted İ in Turkish titles. Writing
  capitals into the texts themselves is Wave 1.

**NAV**
- Owns `res/xml/shortcuts.xml` (main). Tab-root state is kept. The pinned clock is frozen.

**STAGE**
- Parity on the phone: at most one channel level and under 0.5 % of pixels. The unused ember code is
  left alone. The three sprite sheets without a generator are accepted and listed as a follow-up.
  The lifecycle pause stays in the scene clock. A contact sheet that needs Pillow is fine (the README
  says so).

**STREAM**
- The lock in `ChatEngine.Turn` is accepted. The tool card's "more" icon, the 60,000-character cap of
  the output sheet, and "no row animates its placement while a message streams" are accepted. The
  caret cadence is Wave 2.

**TEXT**
- Length rule accepted (English within the room, translations within 1.4 times). `en-XZ` accepted.
  The plural fix ("1 bot") is accepted. Bot presets stay untranslated. `files/lang/` joins backups in
  Wave 12. TEXT may edit the other string files for plurals only.
- **Every key added in Wave 0 gets Turkish and French in the same wave.** Areas write English only;
  a TRANSLATE agent adds `values-tr` and `values-fr` for all new keys after WORDS.

**SWEEP and WORDS**
- `RoundedCornerShape(` is **not** replaced in Wave 0 (skip recipe block R6.b): shapes change with the
  waves that redo those screens. The lint only records today's count.
- Batch W10b (`runtime/RuntimeManager.kt`, `voice/VoiceEngine.kt`) is part of WORDS and runs after
  CONFIG. `ChatInput.kt`'s test tags belong to SWEEP. `ChatScreen.kt:630` and the toast kinds stay.
- SWEEP runs only after every first-stage agent has finished, so the import-order question does not
  arise.

## The order of the build

1. **Stage 1, in parallel:** BUILD, ROBOT, DESIGN, NAV, STAGE, STREAM, CONFIG, TEXT.
2. **Stage 2:** SWEEP (six batches in parallel).
3. **Stage 3:** WORDS (ten batches in parallel).
4. **Stage 4:** TRANSLATE (Turkish and French for every new key; regenerate the stretched language).
5. **Stage 5:** INTEGRATE (compile both variants, unit tests, the three checks, fix the seams).
6. **Review** (Opus, once), fixes, then the phone.
