# Agirî, road to 1.0: progress log

This file is the running record of the execution of `docs/phase4-plan.md` (second edition, fourteen
waves). **Read it top to bottom before continuing the work.** It is updated and committed the moment a
step is finished. The plan and `docs/design-language.md` are never edited or shortened: what happened
goes here, not there.

If you are a new agent picking this up: read `AGENTS.md`, then this file, then the part of the plan
for the wave named under "Where we are". Continue from the first step that is not marked done.

## Where we are

*(Rewritten whenever the state changes. Last rewrite: 2026-11-25, 15:10 UTC.)*

- **Wave 0, Foundation: built, reviewed, green in CI (run 16, `phase0-16`), and installed over the
  owner's own app as an owner-debug build (version code 1000801). It starts, Hermes runs, the
  owner's data is byte for byte unchanged.** Its proof on the phone is in progress (step 0.j,
  part 3): look at the last log entries. What can only be proven on the test twin waits for the
  owner (see "Waiting for the owner", first row).
- **Wave 1a (the scene and the real sky, the fire, Pêt, Home 2.0): the eight area drafts are written
  (`docs/waves/w1/`), the contract `docs/waves/w1a-contract.md` is being merged and reviewed.** No
  code of Wave 1 exists yet.
- The subscription's usage limit has stopped the work twice for about two hours each time; the
  workflows resume by themselves.

## Handoff: if you are taking over

Read `AGENTS.md` first (its "Current status" has what Wave 0 added and how to build), then this
section, then the log below from the bottom up.

**The state of things**

| Thing | State |
|---|---|
| Branch `ccr-bb6d7bcc-9eojyu` | Everything is committed. The last pushed commit that CI built green is `d52bf49` (run 16). Later commits are records, tool fixes found on the phone, and the Wave 1 drafts |
| The owner's phone | `dev.pocketagent.app` is Wave 0 as an **owner-debug** build (debuggable, committed key, version code 1000801, the same Linux image as before). **The twin `dev.pocketagent.app.debug` is installed since 15:43 UTC (the owner tapped "Install anyway"); it has not been started yet.** `/data/local/tmp/exp-s5` (1.2 GB) is a leftover harness that may be deleted |
| The way back | `dist/rollback/agiri-rollback-1000899.apk` (the pre-wave code; install with `adb install -r`; no data is lost). Safety copies of the owner's data: `dist/owner-backup/*.tar` (no credentials). Both are on the owner's PC only (`dist/` is git-ignored) |
| Wave 0 on the phone | Proven: install over real data, start, Hermes Running, data unchanged, Home looks the same. In progress or still to do: see "What is left of Wave 0" |
| Wave 1a | Drafts and the lead's decisions in `docs/waves/w1/`; the contract `docs/waves/w1a-contract.md` when it exists. Not built |

**What is left of Wave 0** (in this order)

1. Finish step 0.j part 3 (the log says how far it got): the robot's safe scenarios with `-Owner`,
   the "after" numbers, the practice model through the app's interface, navigation, one real turn.
   Whatever it finds in the *app* is fixed by a Sonnet agent, looked at by Opus, rebuilt
   (`tools\local-build.ps1 -OwnerDebug`, then `-Test`), and installed again with
   `tools\local-build.ps1 -OwnerDebug -Install` (take a fresh copy into `dist/owner-backup/` first:
   the script insists on it).
2. Write `docs/perf/w0.md` from `dist/perf/build32/` (before) and `dist/perf/w0-owner-debug/` (after).
3. Update `README.md` and `docs/APP_OVERVIEW.md` for Wave 0 (not done yet), then commit and push.
4. **The owner has tapped "Install anyway" for the twin (15:43 UTC), so this is now open:** `tools\local-build.ps1 -Start`
   (the twin), then experiments S2 and S4 (build `-Release`, install it over the *twin's* package is
   not possible, so S2 is tried as the plan says: an optimised build under a throwaway package; ask
   the owner for the tap again), S6 with a throwaway package, S7 (memory), then the twin-only robot
   scenarios (they are not written yet: contract 11.10) and the fourteen old-debt items of plan 0.4.
   Only after S2 has passed may the owner's app become the optimised build
   (`tools\local-build.ps1 -Release -Install -AcknowledgeS2`).
5. The private signing key (S6) is the owner's to make; until then everything is signed with the
   committed key.

**What is next after that**

- Wave 1a: build from `docs/waves/w1a-contract.md` in the order at the end of
  `docs/waves/w1/LEAD-DECISIONS.md`, the same way Wave 0 was built (the scripts of every workflow
  that was run are described in the log entries; the pattern is: stage 1 in parallel, each area
  followed by its own Opus review and one round of fixes; then the mechanical stages; INTEGRATE;
  a final review by lens; fixes; the phone).
- Then Wave 1b (tab bar and Library, splash with experiment S8, "What's new", the Welcome, the ten
  languages), then Wave 2, in the plan's order.

**Rules that must not be broken** (they come from the owner)

- Never uninstall `dev.pocketagent.app`, never `pm clear` it, never install over it anything signed
  with another key or carrying another Linux image. Use `tools\local-build.ps1`, which checks.
- Never tap "Install anyway" on the Play Protect dialog and never change Play Protect: the owner does.
- Every tap or key sent to the phone goes through `tools/phone-lib.ps1` (it checks the focused
  window first). An agent's blind taps opened another app once (see "Things the owner should know").
- $0. Real-model turns only on `google/gemini-4-hydrogen` at low effort, and few. Everything else on
  the practice model.
- No secrets, account names, project ids, e-mail addresses or device serials in the repository.
- `docs/phase4-plan.md` and `docs/design-language.md` are never edited or shortened.
- Code is written by the mid-size model, reviewed by the strong model before it lands, the simplest
  work goes to the small model; the lead conducts and keeps this file current.

## The owner's orders for this run (given 2026-11-25, through the watcher session)

1. Execute the plan wave by wave, in its order.
2. The lead agent only conducts. Code is written by the mid-size model (Sonnet, high effort), every
   change is reviewed by the strong model (Opus, medium effort) before it lands, and the simplest work
   (lookups, mechanical checks, renames, log reading) goes to the small model (Haiku).
3. The owner is asleep. For each decision of plan section 13 the plan's own recommendation is taken and
   written below as "assumed, owner may change". Whatever needs the owner's hands (money, accounts,
   secrets, a spending limit at a provider) is **not** done: it is listed under "Waiting for the owner"
   and worked around.
4. $0: no paid services, no trials. Test chats on `google/gemini-4-hydrogen` at low effort only.
5. **Never lose the owner's data on the phone.** `dev.pocketagent.app` is never uninstalled, and nothing
   is installed that would force an uninstall (for example a build signed with another key under the
   same package name). A new key or a test twin is only ever used under its own package name.
6. The phone is on USB with the screen on. adb may be used freely until the owner says they need it.
7. Commit on `ccr-bb6d7bcc-9eojyu` after each completed step; pushing it for CI is fine. `main` and all
   other branches are left alone.
8. The owner's subscription may end on 2026-11-25 around 16:00 Quebec time. A full handoff entry is
   written here around 15:00 Quebec time (20:00 UTC).

## Decisions taken for the owner (assumed, owner may change)

| # (plan 13) | Decision | Assumed | Note |
|---|---|---|---|
| 1 | Order of the waves | As numbered: 0, 1, 2, ... | |
| 2 | Optimised build for the owner, test twin for agents | Yes, **except the private key** | See "Waiting for the owner": the owner's build stays on today's committed key (the plan's fallback for S6) until the owner makes the key |
| 18 | 1.0 on 21 June 2027 | Yes | |
| 19 | A wave may ship without its extras | Yes | Wave 0 has no extras |
| 20 | Spending cap at the provider for the twin's credential | Not done | Owner's hands; see below |

Decisions that belong to later waves are assumed when their wave starts and are added to this table.

## Waiting for the owner

| What | Why it waits | How the work goes on without it |
|---|---|---|
| ~~One tap on "Install anyway" for the test twin~~ | **Done 2026-11-25, 15:43 UTC:** the owner tapped it; `dev.pocketagent.app.debug` ("Agirî Test") is installed next to the owner's app | Updates of the twin with the same key need no further tap. A *new* package name (a throwaway package for S6) would need one again |
| A private signing key for the owner's build (plan 0.1, experiment S6) | The key must exist only on the owner's PC and as a CI secret, and installing a differently signed build over the owner's app is forbidden in this run | The owner's build keeps the committed key. S6 is tried only under a throwaway package name. The build scripts are written so that the key becomes a one-step change later |
| A test credential imported into the test twin (plan section 6, "What the owner does") | The twin never sees the owner's credentials, and secrets are never typed by an agent | Everything is proven on the practice model (zero tokens). The few real-model checks are done in the owner's app after a wave has shipped to it, on Hydrogen at low effort |
| A spending cap at the provider (decision 20) | An account setting at the provider | The app's own daily budget; test turns stay few |
| S4, second half: does ahead-of-time compilation survive a reboot and a day | A reboot would lock the phone behind its PIN for the rest of the night | The compile command is repeated at every install (the plan's fallback), and the check after a reboot is left for a day when the owner is at hand |
| The three manual checks of plan 0.4 | They need a person or a key: the camera shutter, spoken dictation, a new provider with a real key | Listed in `docs/APP_OVERVIEW.md` as not tried |

## Things the owner should know

- **An experiment agent's taps went astray once (2026-11-25, about 06:32 UTC).** While the
  navigation experiment ran on a throwaway copy of the app, that copy crashed and the agent's next
  scripted taps landed on the phone's launcher: they opened the **AutoTrader** app from the dock and
  about two taps hit its start screen (a consent screen may have been showing; the agent cannot tell
  whether anything was chosen), and one back swipe went into it. Nothing else was touched; from then
  on every input is guarded by a check of which app has the focus. The owner may want to look at
  AutoTrader's consent or privacy settings once.
- The throwaway copies used by that experiment were built with a higher target API (35) so that Play
  Protect accepted them; they could not run Hermes and were uninstalled at the end. Three developer
  settings were changed and restored (`enable_back_animation`, `predictive_back_system_animation`,
  `animator_duration_scale`).
- `/data/local/tmp/exp-s5` (1.2 GB) on the phone is the shell harness of experiment S5. It is left
  in place because it is useful for more experiments; remove it with
  `adb shell rm -rf /data/local/tmp/exp-s5`.

## Log

Newest entry last. Times are UTC. "Phone" says whether the step was checked on the real phone.

### 2026-11-25

- **05:45, step 0.a: the plan is committed.** `docs/phase4-plan.md` (3,484 lines) and
  `docs/design-language.md` (937 lines) committed exactly as the planning session left them, together
  with that session's updates to `AGENTS.md` and `docs/APP_OVERVIEW.md`. Commit `0e6d1b8`. Phone: not
  applicable.
- **05:50, step 0.b: this log is opened.** State of the phone at the start: the owner's app is local
  build 32 (version code 32, 0.1.32, debug, committed key); the phone is awake, "stay awake while
  charging" is on, 48 GB free. No twin installed yet. Phone: read only.
- **06:10, step 0.c started: experiments and contract drafts run side by side.**
  - Phone experiments, first chain (one Sonnet agent after another, in the throwaway worktree
    `dist/wt/exp`, branch `exp/w0-experiments`, never pushed): the test twin (`dev.pocketagent.app.debug`
    next to the owner's app), S5 (practice model inside the guest), S1 and S3 (back-swipe progress,
    shared elements). Each writes its findings to `dist/exp-results/<id>.md` (git-ignored); the facts
    are copied into `AGENTS.md` "Hard-won facts" when the chain ends. Second chain afterwards: S2 and
    S4 (optimised build), S6 (key rotation, throwaway package only), S7 (memory).
  - Contract drafts (no code changes): two Haiku surveys of the code (`dist/w0/survey-*.md`), then one
    Sonnet draft per build area of plan 7.0 (`dist/w0/draft-<AREA>.md`: BUILD, ROBOT, DESIGN, NAV,
    STAGE, STREAM, CONFIG, TEXT) and the recipes for the two mechanical sweeps. They are merged into
    `docs/waves/w0-contract.md` and reviewed by Opus once the first experiment chain has reported.
  - If this run is interrupted here: nothing in the main checkout has changed yet. Reuse whatever is
    in `dist/exp-results/` and `dist/w0/`, and redo what is missing.
- **06:15, experiment E0 (the twin): the build works, the install is blocked.** In the throwaway
  worktree the debug build got `applicationIdSuffix ".debug"` and the label "Agirî Test"; only three
  things spell the package and needed a change (the shortcuts' `targetPackage`, the label in all
  three languages, `buildFeatures.resValues`); everything else already follows the build (the
  provider authority, the restart process, aliases, channels). `adb install` of the new package was
  refused by Play Protect (`INSTALL_FAILED_VERIFICATION_FAILURE`, "built for an older version of
  Android"). The agent did not tap "Install anyway". The owner's app was not touched (version code 32
  and its update time read before and after). Write-up: `dist/exp-results/E0-twin.md`. Phone: yes.
- **06:15, experiment S5 (the practice model): passes in a shell harness.** The same launcher, proot
  and Linux image, run as the adb shell user under `/data/local/tmp/exp-s5` (1.2 GB, still there; a
  ready harness; remove with `adb shell rm -rf /data/local/tmp/exp-s5`). One launch starts the mock
  and then Hermes; "hello" is answered by the mock, also after stop and start; a scripted tool call
  (`uname -a` through Hermes's terminal tool) works end to end; stop takes the mock down in under a
  second. Not seen through the app's own interface (no twin). The design that follows is in
  `dist/exp-results/S5-practice-model.md` and in decision G7. Phone: yes (shell harness only).
- **06:20, the contract drafts are done** (eleven agents): two surveys and nine area drafts, now
  committed under `docs/waves/w0/`.
- **06:40, the lead's decisions on the drafts** are in `docs/waves/w0/LEAD-DECISIONS.md`. The ones a
  reader must know: the twin waits for the owner's tap (G1); an interim `owner-debug` build proves
  the wave on the owner's package without destructive tests (G2 to G6); the owner's phone gets the
  optimised build only after S2 has passed in the twin (G5); the `connections` toolset is withheld in
  addition to the plan's four (assumed, owner may change); `RoundedCornerShape` is not replaced in
  Wave 0; every new text gets Turkish and French in the same wave.
- **06:45, step 0.d started: merging the contract** (one Sonnet merger, two Opus reviewers for seams
  and for safety, one round of fixes).
- **06:50, experiments S1 and S3 (navigation) reported.** Clock note: the times of the entries
  above were estimates and run about 25 minutes ahead; from here on they are read from the clock.
  - **S1 fails on this phone:** a back swipe never reports progress. Samsung's side-gesture service
    turns the swipe into a back *key* when the finger lifts (logcat: `performSwipeAction ...
    action=key_back`, then "Trigger back without dispatching to animator"); the app's callback sees
    start, one progress of 0.0 and invoke within 3 ms. The two developer toggles for back animations
    change nothing. Consequence: the fallback of the register. The back transition plays after the
    gesture ends, and no seekable back machinery is written in Wave 0.
  - **S3 passes:** one `SharedTransitionLayout` around the navigator's existing `AnimatedContent`, two
    CompositionLocals and one modifier carry an avatar from the chat list to the chat's top bar and
    back, with no flicker in sampled frames; its cost is within the noise of a debug build.
  - **Play Protect lets a new package through at target API 35** (29 and 33 are blocked). Such a copy
    cannot run Hermes (the service crashes, and running programs from the data folder needs target
    28), so it only serves experiments on the interface.
  - Frame numbers of the debug build during a screen change: about two thirds of the frames miss
    8.3 ms and the worst frame is 70 to 140 ms (the first composition of the destination). This is the
    "before" that the optimised build has to beat.
  - Write-up: `dist/exp-results/S1-S3-navigation.md`. Phone: yes (interface only, target-35 copy).
- **06:50, the safety copy of the owner's data exists:** `dist/owner-backup/2026-11-25-before-w0.tar`
  (5.5 MB, 446 entries: preferences, chats, the app's own backups, terminal history, and from the
  agent's home: sessions, memories, skills, cron, hooks, plugins, `SOUL.md`, the databases; no
  credentials, no `.env`). Taken read-only with `run-as` and `tar`. To put a file back by hand:
  `adb shell run-as dev.pocketagent.app` and copy it into place with the app stopped.
- **07:10, step 0.d done: the contract is written and reviewed.** `docs/waves/w0-contract.md` (642
  lines; ground rules, ownership of every file, the registry of every shared symbol, 71 rulings on the
  drafts' requests, amendments per area, strings, manifest, the later stages, the proof list, and the
  lead's rulings in section 11). Two Opus reviewers (seams; safety and feasibility) found 36 problems
  (3 would have turned CI red, 17 major); all were applied in one round, none refuted. What changed
  because of the review: ROBOT and CONFIG are each split into two agents; the robot may change system
  settings only through one guarded function; destructive robot steps refuse the owner's package in
  code; practice mode expires after two hours on a build that is not the twin; an install over the
  owner's app is refused when it would replace the Linux image; a rollback path is described. The
  Linux image is not changed in Wave 0 (ruling 11.8). Phone: not applicable.
- **07:10, step 0.e started: stage 1 of the build.** Ten Sonnet agents in parallel (BUILD, ROBOT-P,
  ROBOT-T, DESIGN, NAV, STAGE, STREAM, CONFIG-R, CONFIG-D, TEXT), each followed at once by its own Opus
  review and one round of fixes. Nobody compiles in this stage. If this run is interrupted here: the
  tree holds unfinished, uncompiled work of several agents; `git status` shows it; either continue
  from the contract area by area or `git stash` it and start stage 1 again.
- **10:30, step 0.e done: stage 1 is written and reviewed, not yet compiled.** Thirty agent runs (ten
  builds, ten Opus reviews, ten rounds of fixes; 36 findings, 3 of them blockers, all applied). The
  run was paused for about two hours by the subscription's usage limit and resumed by itself. What
  each area built, where it departed from the contract, what it asks of other areas and what it left
  undone is in `docs/waves/w0/stage1-report.md`. Cut on purpose, all without a consumer: the baseline
  profile file, the ember arc between tabs, the twin-only scenario files and perf P2/P3, the memory
  phases of the perf tool, language packs from a file (the text function works without them).
  Committed as a checkpoint, **not pushed** (it has never been compiled). Phone: no.
- **10:35, step 0.f started: stages 2 to 4.** One Sonnet agent first settles the requests the areas
  made of each other (`docs/waves/w0/stage1-resolution.md`), then SWEEP (five batches, Sonnet), WORDS
  (eleven batches, Haiku), TRANSLATE (Turkish and French for every new text). Then stage 5:
  compile, unit tests, the checks. If this run is interrupted here: the last commit is the stage-1
  checkpoint; `git status` shows how far the sweeps got; they can simply be run again.
- **10:55, step 0.f done: stages 2 to 4 are finished, still not compiled.** Eighteen agent runs.
  RESOLVE checked every request the areas made of each other and every row of the registry against
  the code (one small fix was needed; the table is `docs/waves/w0/stage1-resolution.md`). SWEEP
  replaced the toasts, the three private confirm dialogs and the sheets with the new components and
  added the test tags. WORDS turned every direct text call into `t(` / `Texts.get` (no
  `stringResource(` call is left under `app/src/main/java`) and moved the engine's English texts into
  resources. TRANSLATE wrote Turkish and French for the 175 new keys and regenerated the stretched
  test language. Reports: `docs/waves/w0/stages2-4-report.md`. Committed as a second checkpoint, not
  pushed. Phone: no.
- **10:55, step 0.g started: stage 5, INTEGRATE.** Three Sonnet agents one after another: compile the
  twin and the owner-debug variant; unit tests and the first-run data; the optimised build, the lint,
  the string checks and every tool's self-test. Nothing is installed on the phone in this step.
- **11:00, step 0.h started alongside 0.g: the final review.** Five Opus reviewers read the whole
  wave (`git diff 4b7ed57 HEAD`), each with one lens: the start path and the owner's data;
  navigation and every way into the app; the chat; the new components, the sweeps and the texts; the
  tools that touch the phone, the build and CI. They only read. Their findings are applied by one
  round of Sonnet fixes once INTEGRATE has left the tree green; then the build goes to the phone.
- **11:10, step 0.g done: the wave compiles and its unit tests pass.** The first compile of thirty
  agents' work had four errors (an import in the Gradle script, one missing `@Composable`, one wrong
  import, nothing else). Twin `dev.pocketagent.app.debug` (version code 801) and owner-debug
  `dev.pocketagent.app` (1000801) both build; the optimised R8 build compiles too and needed no extra
  keep rule (its app part without the Linux image is 13.0 MB against 27.8 MB for the debug build).
  793 unit tests in both variants, 0 unexpected failures (the four known Windows symlink tests
  fail as always). The pixel-font golden file was made from the pre-wave code and the new font
  matches it; the new scene renderer equals the old one pixel for pixel in the parity test. Lint
  baseline, strings baseline and size budget are written. `mock_llm.py --selftest`, the launcher
  test and every tool's self-test pass. Not checked here: anything that needs Linux `pwsh`, `zip` or
  the real smoke job (CI will say). Report: `docs/waves/w0/integrate-report.md`. Phone: no.
- **11:10, step 0.h done: the final review.** No blocker and nothing against installing over the
  owner's app tonight. Nine findings (four major): Pêt's notes can stop showing after the app was
  used as a share target (two activities, one flag); the robot never checks which window has the
  focus before it injects input; the robot's free-form adb step and its install step can be pointed
  at the owner's package. Minor: a very long text is split at a space that comes back as a newline;
  the debug link can be sent by any app on an owner-debug build; the practice banner covers the top
  bar; a skill heading gets a Turkish capital İ; twin and owner-debug share one build folder.
  Full text with everything the reviewers checked and found right: `docs/waves/w0/final-review.md`.
- **11:15, step 0.i started: the findings are fixed** (two Sonnet agents: app, tools), the fixes are
  looked at once by Opus, then everything is built and tested again.
- **11:55, step 0.i done: the review's findings are fixed and the code of Wave 0 is closed.** All nine
  findings applied; one Opus look at the fixes found the note fix incomplete (the newest composed
  host, not the visible one) and two small things, all applied. What changed: Pêt's note host
  follows the activity's lifecycle; every input the robot injects goes through one function that
  first reads the focused window and fails closed; the robot's free adb step is an allow list and
  its install step reads the APK's package; the debug link needs a token that only `run-as` can
  read (`files/debug-link.token`), so no other app can send it; seeding also checks the package
  name; the practice banner sits above the screens instead of over them; `local-build.ps1`
  recompiles from clean when the variant changes. After the fixes: twin, owner-debug and R8 builds
  green, **804 unit tests, 0 unexpected failures** in both variants, every self-test and check green.
  The three final APKs are in `dist/final/` (git-ignored). Details: `docs/waves/w0/final-fixes.md`.
  Committed and pushed for CI. Phone: no.
- **11:55, step 0.j started: proving on the phone** (owner-debug over the owner's app; see decisions
  G2 to G6): "before" pictures and numbers from build 32, the rollback APK, the install, the start,
  the robot's safe scenarios, the practice model through the app's own interface, one real turn.
- **12:00, step 0.j, part 1 done: the "before" state and the way back.** Nothing was installed.
  - Pictures of build 32 (Home, Chats, Bots, Hermes, Settings): `dist/proof/w0/before/`.
  - Smoothness of build 32 (debug build, 120 Hz, a frame has 8.3 ms): cold start 1,108 ms (one
    sample); Home scroll: 23 % of 3,042 frames within 8.3 ms, 95th percentile 11.7 ms, worst 25 ms;
    Home idle for 10 s: 12 % of 2,459 frames within 8.3 ms, every frame about 10 ms (the idle scene
    alone costs more than a frame). Files: `dist/perf/build32/`.
  - The rollback APK exists and is verified: `dist/rollback/agiri-rollback-1000899.apk` (the
    pre-wave code, package `dev.pocketagent.app`, version code 1000899, same key, same Linux image).
    To go back: `adb install -r dist\rollback\agiri-rollback-1000899.apk` (no data is lost; the
    pre-wave code ignores the wave's new files).
  - **The owner should know:** Hermes had been stopped by hand the evening before. Opening the app
    for the pictures auto-started it. It is stopped again through the app when the proof is over.
  - Tool findings: two perf scenarios could not find the Home tab on a build without test tags
    (fixed: a text alternative). The perf tool's cold-start scenario force-stops the app; under
    `-Owner` that is not wanted (a force-stop cancels the owner's alarms), so it was not run; the
    after-number is taken the same way as the before-number (a start from a dead process).
  - CI for `d52bf49`: the unit-test job (with the Linux `pwsh` checks) is green; the rest is running.
    The install on the phone waits for the smoke test, which boots Hermes with the new configuration.
- **12:13, CI for `d52bf49` is green end to end** (run 16): unit tests with the Linux `pwsh` checks,
  proot, rootfs, the smoke test (Hermes boots with the guard block, the tool list is the expected
  ten sets, no title call, and phase 3 runs through the practice model inside the guest), both APKs,
  the prerelease `phase0-16` with `Agiri-16.apk` and `Agiri-16-test.apk`. **Do not install
  `Agiri-16.apk` over an existing install yet:** it is the optimised build (S2 untried) and carries
  CI's own Linux image, which would be extracted again over the agent's data.
- **12:14, step 0.j, part 2: Wave 0 is installed over the owner's app (owner-debug) and starts.**
  - Before the install: a second safety copy (`dist/owner-backup/2026-11-25-before-install.tar`) and
    checksums of the owner's chat files, `SOUL.md`, memory and preferences.
  - Installed with `tools\local-build.ps1 -OwnerDebug -Install -NoBuild`; the script's checks passed
    ("same certificate, higher version code, data copy present, same Linux image"). The app is now
    version code 1000801, `0.2.8-local1`, debuggable, same key.
  - First start over the existing data: `variant=owner-debug`, "no foreign hooks or plugins",
    `practice=off`, auto-start, **Hermes Running after 10 s** with the owner's saved credentials
    (health OK, `/v1/models` 200). No crash in logcat or `app.log`. The generated `config.yaml`
    carries the four new guard keys.
  - **The owner's data is byte for byte what it was:** the checksums of `bots.json`,
    `conversations.json`, `drafts.json`, `routines.json`, `usage.json`, the message file, `SOUL.md`,
    `USER.md`, the three preference files and the rootfs stamp are identical before and after; the
    secrets folder still holds its two files. New files written by the wave: `crash_reports.xml`,
    `files/debug-link.token`.
  - Home looks the same as before (pictures `dist/proof/w0/before/01-home.png` and
    `dist/proof/w0/after/01-home.png`). Phone: **yes**.
- **12:20, step 0.j, part 3 started:** the robot's safe scenarios and the smoothness numbers after;
  then the practice model through the app's own interface, navigation, and one real turn on
  Hydrogen at low effort; at the end Hermes is stopped again, as the owner had left it.
- **12:35, Wave 1 is being prepared while the phone proof of Wave 0 runs.** Read-only: one Haiku survey
  of the code as it is after Wave 0 and eight Sonnet contract drafts for **Wave 1a** (the scene and
  the real sky, the weather, the fire, Pêt, the two art areas, Home 2.0, Appearance), written to
  `dist/w1a/` and copied to `docs/waves/w1/` when they are done. Decisions assumed for this half
  (owner may change): the sky follows the real day by default, with "always night" (today's look)
  as a choice; the hand-keyed flame stays and the living edge is added around it, with "Classic
  fire" as a choice; the weather is built and tested with a pinned weather but ships dark, because
  the service needs a contact address that only the owner can choose (no request can leave the
  phone while it is empty); no new permission; an existing installation keeps the day it knows
  today. Wave 1b (tab bar, Library, splash, Welcome, ten languages) follows after 1a.
- **15:00, the Wave 1a drafts are done** (one survey, eight drafts; the run was paused about two
  hours by the usage limit). They are in `docs/waves/w1/` with the lead's decisions
  (`LEAD-DECISIONS.md`). Found by the survey and worth knowing: Home is one list with a 280 dp scene;
  `AppClock` is not read by any production code yet; the engine has no "tool started" event;
  `NOTICE` has no entry for the sounds or the speech model (to be added in Wave 1a); the Home label
  calls Pêt a salamander while the rest of the app says newt.
- **15:05, step 1a.b started: merging the Wave 1a contract** (one Sonnet merger, two Opus reviewers,
  one round of fixes), as for Wave 0.
- **15:10, the handoff section above was written early**, because the usage limit may stop the work
  before the planned time. It is kept current from here on.
- **15:30, step 0.j part 3, the robot half is done** (the practice half is running). On the owner's
  package with `-Owner`:
  - The focus guard and the debug-link token work on the real phone.
  - Safe scenarios: first run 5 of 8 passed; the three failures were the scenarios' own (the gallery
    scenario left the app on the gallery route; the stretched-language sweep counted the owner's own
    data, language names and dates as "plain English left"). Fixed in the scenario files and the
    allow list; after that `sweep-labels` passes and the smoke scenarios start from Home. The
    stretched and mirrored sweep still reports the owner's own texts as leaks, which only a twin
    with practice data can avoid (reports: `dist/phone-test/0.2.8-local1/`, montages:
    `dist/proof/w0/montage/`).
  - **App defects found:** (1) the Pêt picture on the About screen is clickable (the five-tap secret)
    and has no spoken label; (2) the About row shows the app's name from the stretched test language
    ("[ÅgîrîÅî]") in an otherwise English interface, also after a cold start: a debug-build resource
    (`values-en`/`values-en-rXZ`) wins where it must not. Neither touches data; both are debug-build
    or accessibility matters; they are fixed with the practice half's findings.
  - Numbers after (debug build against debug build): cold start 931 ms from a dead process (before:
    1,108 ms); Home scroll and Home idle taken twice (`dist/perf/w0-owner-debug/`, `-run2/`); the
    table goes into `docs/perf/w0.md` when the practice half has added the streaming numbers.
  - A second cold start of the new build over its own new state: Hermes Running 10 s later, no
    warning or error lines, no crash.
  - "After" pictures of the five tabs: `dist/proof/w0/after/`.
  - The tool fixes are committed (`tools/phone-lib.ps1`: half-hidden rows are no longer reported as
    unlabelled; three scenario files; the leak allow list).
- **15:30, the first text of `docs/waves/w1a-contract.md` exists and is committed as a draft**; two
  Opus reviewers are reading it. It is not final until the log says "the Wave 1a contract is reviewed".
- **15:43, the test twin is installed.** The owner was at the phone and said so through the watcher
  session. `tools\local-build.ps1 -Install` built the twin (version code 801, `0.2.8-local1-test`)
  and started the install; Play Protect's dialog came up; the owner tapped "Install anyway" 14
  seconds later; `adb` reported Success. Both packages are on the phone; the owner's app is
  unchanged (version code 1000801, same update time). The twin was not started, because the practice
  half of the proof was still using the screen. Phone: yes.
  - What this opens: the twin's first start (unpacking, practice model, no credentials), the
    twin-only robot scenarios, S7, and S2/S4. **S2 needs no second tap:** an optimised build with the
    twin's package name installs over the twin as an update (plan for it: a Gradle switch that gives
    the release build type the twin's suffix; version code above the twin's).
- **15:50, step 1a.b done: the Wave 1a contract is reviewed.** `docs/waves/w1a-contract.md` (about
  860 lines). Two Opus reviewers: 41 findings, 2 blockers (a clash with `SpriteBank.find`; a change
  that would have broken the parity test of Wave 0), 19 major; the safety reviewer recomputed the
  astronomy and most reference values matched. All applied (`docs/waves/w1/contract-review.md`).
  HOME and SCENE were each split in two and the debug-link extras became an agent of their own:
  eleven areas (SKY, SCENE, WEATHER, FIRE, PET, ART-PET, ART-WORLD, HOME-HERO, HOME-LIST, APPEAR,
  LINKS). **The lead's rulings are section 11 of the contract; the one that matters for whoever
  continues: the build is cut into five closed slices (A to E), and Wave 1 code is built in the
  worktree `dist/wt-w1` on the local branch `w1a` until Wave 0's phone proof is over.**
- **15:50, slice A of Wave 1a started** (pure logic and its tests, new files only: the sky's
  mathematics, the calendar, places and the weather model with its guard, the fire feed, Pêt's
  brain, Home's suggestions). Sonnet per area, Opus review each, one compile and test run in the
  worktree, then a merge into the work branch.
