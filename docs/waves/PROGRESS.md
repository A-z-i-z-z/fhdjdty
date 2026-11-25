# Agirî, road to 1.0: progress log

This file is the running record of the execution of `docs/phase4-plan.md` (second edition, fourteen
waves). **Read it top to bottom before continuing the work.** It is updated and committed the moment a
step is finished. The plan and `docs/design-language.md` are never edited or shortened: what happened
goes here, not there.

If you are a new agent picking this up: read `AGENTS.md`, then this file, then the part of the plan
for the wave named under "Where we are". Continue from the first step that is not marked done.

## Where we are

*(Rewritten whenever the state changes. Last rewrite: 2026-11-25, 16:10 UTC.)*

- **The owner's app is back on the pre-wave code** (`dev.pocketagent.app`, version code 1000899,
  the rollback build; rolled back at 16:02 UTC). Reason: the proof on the phone found two severe
  defects in Wave 0 (every bottom sheet stays invisible at the phone's normal animation speed; an
  approval card cannot be answered). The owner's data is byte for byte unchanged through the install
  and the rollback. **Wave 0 goes back onto the owner's app only after the defects are fixed and the
  fixes are proven on the twin.**
- **The test twin is installed** (`dev.pocketagent.app.debug`, since 15:43 UTC; the owner tapped
  "Install anyway"). From now on everything is tried there first.
- **Wave 0, Foundation:** built, reviewed, green in CI (run 16). Its phone proof is done
  (`docs/waves/w0/phone-proof.md`): most of it passed; the defects it found are being fixed (see the
  last log entries for how far that is).
- **Wave 1a:** the contract is reviewed (`docs/waves/w1a-contract.md`); slice A (pure logic and
  tests) is being built in the worktree `dist/wt-w1` (local branch `w1a`). Nothing of Wave 1 is in
  the work branch's code yet.

## Handoff: if you are taking over

Read `AGENTS.md` first (its "Current status" has what Wave 0 added and how to build), then this
section, then the log below from the bottom up.

**The state of things**

| Thing | State |
|---|---|
| Branch `ccr-bb6d7bcc-9eojyu` | Everything is committed. The last pushed commit that CI built green is `d52bf49` (run 16). Later commits are records, tool fixes found on the phone, and the Wave 1 drafts |
| The owner's phone | `dev.pocketagent.app` is the **rollback build** (pre-wave code `41e8d4d`, debuggable, committed key, version code 1000899, the same Linux image as always). `dev.pocketagent.app.debug` is the **test twin** (Wave 0 debug build, version code 801, never started yet unless the log says so). `/data/local/tmp/exp-s5` (1.2 GB) is a leftover harness that may be deleted |
| The way back | `dist/rollback/agiri-rollback-1000899.apk` (the pre-wave code; install with `adb install -r`; no data is lost). Safety copies of the owner's data: `dist/owner-backup/*.tar` (no credentials). Both are on the owner's PC only (`dist/` is git-ignored) |
| Wave 0 on the phone | Proven on the owner's data and then rolled back: install, start, Hermes Running, data unchanged, looks the same, the practice model through the interface, one real turn, navigation. Defects found: `docs/waves/w0/phone-proof.md`. To put Wave 0 back on the owner's app after the fixes: the version code must be above 1000899, so build with `tools\local-build.ps1 -OwnerDebug -BuildNumber 16 -Install` (gives 10016xx); take a fresh copy into `dist/owner-backup/` first |
| Wave 1a | Drafts and the lead's decisions in `docs/waves/w1/`; the contract `docs/waves/w1a-contract.md` when it exists. Not built |

**What is left of Wave 0** (in this order)

1. **Fix the defects of the phone proof** (`docs/waves/w0/phone-proof.md`, both "Defects of the app"
   lists): the two severe ones first (sheets invisible at normal animation speed; the approval
   answer refused with HTTP 400 because `execution_generation` is not sent), then dialogs that show
   Turkish in an English interface, the stretched test language leaking into the normal interface
   (`app_name`), a failed turn shown as raw JSON, the unlabelled controls, practice tokens counted in
   the owner's usage. Sonnet fixes, Opus looks, rebuild, unit tests.
2. **Prove the fixes on the twin** (first start of the twin, practice model on; sheets at animation
   scale 1; the approval card with Deny and Allow once; dialogs' language). The twin has no
   credentials and needs none.
3. **S2 on the twin:** `tools\local-build.ps1 -TwinRelease -Install` (the optimised build over the
   twin), then Hermes start, the neural voice, backup and restore, the icon switch; then S4 (compile
   filter) and the smoothness numbers of the optimised build; S7 (memory). Put the debug twin back
   afterwards (`tools\local-build.ps1 -Install`).
4. **Smoothness:** the debug numbers after the wave were not better than before (Home idle about
   1.4 ms per frame slower, though on a warmer phone). Measure the twin's optimised build, and if the
   scene is still over budget, find out why before Wave 1 adds to it (the stage draws into a
   framebuffer on the CPU).
5. Put Wave 0 back on the owner's app (see the table above), write `docs/perf/w0.md`, update
   `README.md` and `docs/APP_OVERVIEW.md`, commit, push.
6. The twin-only robot scenarios (not written yet: contract 11.10) and the old-debt items of plan
   0.4; S6 needs a throwaway package (one more tap by the owner) and the owner's own key.

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
- **16:05, a fourth build exists: `twin-release`** (for experiment S2). The optimised build (R8, not
  debuggable, no test hooks) with the twin's package name, label and version scheme, always signed
  with the committed key: `tools\local-build.ps1 -TwinRelease` (Gradle `-PtwinRelease=true`). It
  installs only as an update over the installed twin (the script refuses any other package, a
  missing twin, or a version code that is not higher) and then compiles the app on the phone.
  One Opus check: nothing to fix; the owner's package is no more reachable than before and the
  three other variants are unchanged. All four build; 808 unit tests, 0 unexpected failures.
  Not installed yet. To run S2: with the phone connected, `tools\local-build.ps1 -TwinRelease
  -Install`, then try in the twin: Hermes start (practice model), the neural voice, backup and
  restore, the icon switch, and read logcat for `NoSuchMethodError`, `ClassNotFoundException` or a
  native abort. The robot's guarded twin steps refuse this build (it has no debug links), so S2 is
  driven by plain taps through the focus-checked input function. Afterwards a debug twin can be put
  back with `tools\local-build.ps1 -Install` (the script picks a higher version code).
- **16:02, step 0.j is finished, and the owner's app was rolled back.** Report:
  `docs/waves/w0/phone-proof.md`.
  - **Passed on the owner's app:** the practice model through the interface (switch on, banner above
    the top bar, Hermes restarts in practice, mock answers, a tool step, a long stream that stays at
    the bottom, a folded thought, an error turn, switch off: the generated files and the owner's
    provider settings are again exactly what they were, secrets untouched); copy, star, find,
    regenerate, stop, fork, a local slash command, delete with the new confirm dialog; **one real
    turn on Hydrogen at low effort: answered in 6 s, 9.8k tokens in, 12 out, no thought leak** (a
    plain turn cost about 11k before the wave's shorter tool list); navigation state kept; the back
    swipe pops with the transition after the gesture; the shared avatar travels; Diagnostics shows
    exit reasons, the guest-code row and Export logs up to the share sheet; the web-lookups row.
    Every chat the test made was deleted; the owner's one chat was never opened.
  - **Severe defects found:** (1) every `PocketSheet` (New chat, Chat Controls, tool output, message
    menu) stays invisible at animation scale 1 and its invisible scrim swallows touches until Back;
    it only works with animations off, which is how the robot ran until then. (2) An approval card
    appears but none of its four answers works: Hermes answers HTTP 400 because the app does not
    send `execution_generation`; the chat's session then stays blocked for minutes. (Approvals had
    never been tried on a phone before; this one is older than the wave.)
  - **Other defects:** dialogs and sheets show Turkish in an English interface (the phone's system
    language is Turkish; the dialog windows do not get the app's language); the app's name on the
    About row comes from the stretched test language; a failed turn shows the raw stream chunk; the
    About mascot and the sheet scrim have no spoken label; the tool-output sheet shows the command
    but not the output; practice tokens seem to count in the owner's usage; in a mirrored layout
    numbers, paths and the terminal are reordered (for Wave 12's list).
  - **Smoothness after, debug against debug:** not better. Home scroll 17 to 20 % of frames within
    8.3 ms (before 23 %), worst 41 to 60 ms (before 25); Home idle 6 to 9 % (before 12 %), median
    11.0 ms (before 9.7). The phone was about 9 degrees warmer in the after run, so this is not
    yet proof of a regression, but it must be measured again (handoff, point 4). Streaming a long
    answer: 21 % within 8.3 ms, worst 44 ms. Cold start 931 ms (before 1,108).
  - **Rollback:** because of the two severe defects the owner's app was put back on the pre-wave
    code with `adb install -r dist/rollback/agiri-rollback-1000899.apk` (Success). Checksums of the
    owner's files before and after: identical. The rolled-back app starts, Hermes Running after
    11 s, no crash. Hermes was left running (the app starts it when opened). A third safety copy is
    `dist/owner-backup/2026-11-25-before-rollback.tar`. Phone: yes.
- **16:15, the defect fixes are being made** (workflow `w0-defect-fixes`; uncommitted changes in the
  main tree while it runs). Three areas side by side, each written by Sonnet, read by Opus, repaired:
  SHEET (the invisible sheets, the language inside dialog windows, the scrim's label; files
  `ui/components/`, `ui/text/`), ENGINE (the approval answer with `execution_generation`, a failed
  answer shown on the card, a readable error for a failed turn, the tool-output sheet, practice
  tokens kept out of the usage totals; files `api/`, `data/`, `ui/chat/`), MISC (the stretched test
  language leaking into English, the About mascot's label; `ui/settings/RuntimeSection.kt`,
  `tools/make-test-language.ps1`). Then one agent integrates (compile twin and owner-debug, unit
  tests, the checks), one installs the **twin** and proves every item on the phone with the animation
  scales at 1 (report: `docs/waves/w0/fix-proof.md`), and Opus reads the whole diff. **If you take
  over and find uncommitted changes in the main tree without that report:** the workflow was cut
  off; read `git diff`, run `tools\local-build.ps1 -Test`, and either finish the proof on the twin
  or `git stash` the changes and start the fixes again from the list in the handoff section.
  Phone: not yet.
- **16:25, Wave 1a slice B (the art and the data) was started** in its own worktree `dist/wt-w1b`
  (local branch `w1a-art`, cut from `a1f54df`), next to slice A in `dist/wt-w1` (branch `w1a`).
  Workflow `w1a-slice-b-art`: five generators (`tools/art/stars.py`, `tools/places/make_places.py`,
  `tools/art/make_sounds.py`, `tools/art/pet_clips.py`, new builders in `tools/art/craft_art.py`),
  each written by Sonnet, read by Opus, repaired; then the generators that need no download are run
  and the contact sheets rendered into `docs/waves/w1/look/`; an Opus reviewer looks at every sprite
  (keep, fix or cut), one repair round, a second look; its report will be
  `docs/waves/w1/slice-b-report.md`. **The two downloads (HYG star catalogue, Natural Earth city
  list) are not made yet:** the owner was asked through the watcher session; until the answer the
  star table and the city list do not exist and the code falls back (today's 46 stars; zones only).
  **If you take over:** neither worktree is merged; finished work in them is only what
  `git -C dist/wt-w1 log` and `git -C dist/wt-w1b log` show as commits above `a1f54df`; uncommitted
  files there belong to a workflow that was cut off (judge them with the slice's checks: for A a
  compile and `-Test` in that worktree, for B the scripts' `--check` and `--selftest`).
  The owner's clarification of 16:20: the session may end earlier or later than 16:00 Quebec time;
  the work goes on at full pace until it really stops. Phone: no.
- **16:15 UTC, documents of Wave 0 written** (one Sonnet agent; no build, no phone):
  `docs/perf/w0.md` (the smoothness numbers before and after from `dist/perf/`, what they do and do
  not show, three hypotheses for the scene's cost with file and line: the whole stage repainted on
  the main thread each tick, a new bitmap uploaded each tick, the clock waking at every vsync),
  `README.md` (the three builds, the practice model, Export logs, the warning not to install the CI
  release over an existing install yet) and `docs/APP_OVERVIEW.md` (a section "What Wave 0 added").
  Phone: no. (The clock times of the three entries above are a few minutes fast.)
- **The owner's answers, through the watcher session (16:12 UTC):** (1) yes to the two downloads
  (HYG star catalogue, Natural Earth populated places; into `dist/`, data only, never committed or
  run, credit in `NOTICE`); (2) **from 18:00 UTC (13:00 Quebec) the phone is gone for about one to
  two hours** and the owner is away from the PC: all phone work must be at a safe point before
  then (nothing half-installed, the owner's app working), and nothing may assume the phone is back
  until the owner says so.
- **16:20 UTC, the two data files are downloaded** (a Haiku agent; nothing committed, nothing run):
  `dist/wt-w1b/dist/data-w1a/hyg/hygdata_v41.csv` (33,932,548 bytes, SHA-256 `d9f69fd8…e4ebd`,
  119,626 rows, columns as expected: `id ra dec mag ci proper` and more; licence read from the
  repository's own file: CC BY-SA 4.0) and
  `dist/wt-w1b/dist/data-w1a/ne/ne_10m_populated_places_simple.geojson` (4,932,147 bytes, SHA-256
  `fd3fa867…ac42f`, 7,342 places, properties `nameascii`, `name`, `adm0_a3`, `iso_a2`, `pop_max`,
  `featurecla` as the contract expects; public domain, terms saved next to it). Both licences equal
  the lead's table. The HYG repository says newer versions now live on another site; v41 is the last
  one on GitHub and is what the plan needs. **Still to do with them** (contract 7.1 steps 1 and 2, in
  the worktree `dist/wt-w1b`, once slice B's scripts are reviewed): `stars.py --hyg ...` and
  `make_places.py --cities ...`, then their `--verify` / `--check`. Phone: no.
- **19:45 UTC, after a stop by the usage limit (about 17:00 to 19:40 UTC).** The three workflows
  picked themselves up at 19:40. **The phone is away** (the owner took it at 18:00 UTC and has not
  said they are back): nothing touches the phone until the watcher session says so. Because of
  that the defect-fix workflow was stopped and started again from its record with a changed order:
  fixes, integration (compile, unit tests, checks), **then the Opus review of the whole diff and
  its repairs, and the proof on the twin only later**, as a separate step when the phone is back
  (the script keeps the prover's full instructions:
  `...\workflows\scripts\w0-defect-fixes-wf_b8a19eb4-35a.js` in this session's folder; a successor
  without that file writes the proof from the item list in `docs/waves/w0/phone-proof.md` and the
  handoff section above). State of the fixes at this moment: area MISC is written, reviewed and
  repaired; SHEET and ENGINE are partly written (uncommitted files in the main tree, among them
  `ui/components/SheetSlide.kt`, `PocketWindow.kt`, `ui/text/AppLocale.kt`,
  `api/ApprovalContract.kt`, `api/ErrorText.kt`) and are being finished. Slice A of Wave 1a is at
  its integration (compile and tests in `dist/wt-w1`); slice B is in its script reviews
  (`dist/wt-w1b`). Phone: no.
- **19:50 UTC, the contract of Wave 1b is being written** (workflow `w1b-contract`; documents only,
  under `docs/waves/w1b/` and `docs/waves/w1b-contract.md`; no code, no phone): a split of the
  plan's 1b items into areas (`docs/waves/w1b/SPLIT.md`), one Sonnet draft per area, a merge into
  one contract in the shape of the 1a contract, three Opus reviews (facts; seams and safety; product
  and design) and their findings worked in (`docs/waves/w1b/contract-review.md`). Four workflows now
  run side by side: the Wave 0 defect fixes (main tree), Wave 1a slice A (`dist/wt-w1`), slice B
  (`dist/wt-w1b`), and this one. Phone: no.
- **20:05 UTC, Wave 1a slice A is finished** (local branch `w1a` in the worktree `dist/wt-w1`,
  commit `e9dc0bf`: 102 new files, 14,819 lines; not merged into the work branch and not pushed
  yet). The pure logic of every area and its unit tests: the sky model (sun, moon, stars, planets,
  moments of the day, seasons, the calendar of days), places and the weather model with its guard,
  the fire's feed and mathematics, Pêt's brain and placement, the logic of Home 2.0, appearance
  helpers. Eight areas written by Sonnet, each read by Opus (15 findings, all applied), then
  integrated: **the twin compiles, 1334 unit tests, 0 unexpected failures** (804 before), `lint-ui`
  and `strings-check` pass. Reference values of the astronomy were recomputed independently by
  writer and reviewer. To know: `ui/pixel/PetArt.kt` is a temporary stand-in until slice B's real
  file lands (same path); three tests skip until slice B's data exists (`PlacesTest` twice,
  `StarTableTest.realAsset`); open looks for LOOK: a small step of the sky's gold tint at local
  midnight under the midnight sun, and the far ridge being pure black at night with "black night".
  Phone: no.
- **20:05 UTC, slice C was started** in the same worktree (workflow `w1a-slice-c`): everything
  that draws, hosts or stores for the scene (the sky's draw functions, the renderer pipeline,
  `SkyHost.kt`, `HearthScene.kt`, the weather layers and stores, the fire's driver and layers,
  `PetHost.kt`, the model parts of Appearance and the sound engine), with Turkish and French for
  every new key at once, six areas each read by Opus, an integration (twin and owner-debug compile,
  unit tests, the checks, stills rendered into `dist/wt-w1/dist/stills-c`), then two Opus reviews of
  the whole slice (speed and lifecycle; today unchanged and data, with a look at the stills) and a
  repair. At its end the app must compile and work with today's Home screen (Home 2.0 is slice D).
  **Merging:** the slices are merged into the work branch only after the Wave 0 defect fixes are
  committed there, so that the fixes can be proven on the twin without Wave 1 code if wanted.
  Phone: no.
- **20:20 UTC, the defect fixes of Wave 0 are written, reviewed, built and unit-tested; NOT yet
  proven on the phone** (the phone is away). Report: `docs/waves/w0/fix-report.md`.
  - **Sheets invisible at normal animation speed:** the exact trigger could not be reproduced
    without the phone. The most probable cause: the sheet started hidden and was only moved into
    view by an animation that was started outside the dialog window and keyed on a measured height.
    `PocketSheet.kt` was rewritten on a small pure state holder (`SheetSlide.kt`, about 20 unit
    tests): visibility no longer depends on a measurement, the motion runs inside the window, and a
    watchdog finishes it by force after 1.5 s (log line `Sheet: no frame moved the sheet for
    1500 ms`: **if the proof sees that line, the real cause is still open**).
  - **Language inside dialog windows:** every dialog and sheet now goes through one door
    (`ui/components/PocketWindow.kt`) that hands the app's own language, layout direction and
    density into the window; `t()` takes the chosen language first.
  - **Approvals:** Hermes's contract was read in its source (three keys: `request_id`,
    `execution_generation`, `choice`; the generation comes with the approval event). The app now
    sends exactly that from the card and the notification, retries once on a stale generation, shows
    a failed answer on the card, and **Stop really stops the run at Hermes** (`POST
    /v1/runs/<id>/stop`), also while an answer is on its way; a stopped turn can never count as a
    finished reply.
  - **A failed turn** shows one short readable line (`api/ErrorText.kt`); the raw body goes to the
    log only.
  - **Tool output:** Hermes does not send a tool's output on this API (only the command and its
    state), so the sheet now says so and its button reads "Show the command".
  - **Practice turns** are never counted in the day totals, the model totals or the budget; a turn
    that wakes Hermes decides practice or real after the launch.
  - **The stretched test language** can no longer leak into plain English (the generator keeps the
    two generated folders on the same keys; a test and `strings-check` enforce it); the About mascot
    and the scrims have spoken labels.
  - **Numbers:** twin and owner-debug compile; **879 unit tests, 0 unexpected failures** (808
    before); `lint-ui`, `strings-check` (baseline 939, not raised) and the test-language check pass.
    Two Opus rounds: 8 findings in the areas, 3 on the whole diff (one major: Stop while a card
    waits could end as a normal reply), all repaired.
  - **Not handled** (listed in the report): right-to-left details, the debug gallery, the
    smoothness question, `dialog.confirm` tags on three OK buttons, a robot scenario for sheets at
    animation scale 1.
  - **Next for this step, when the phone is back:** install the twin
    (`tools\local-build.ps1 -Install`), practice model on, animation scales at 1, and prove: the
    four sheets rise and close; dialogs in the app's language with the phone on Turkish; the four
    approval answers (HTTP 200) and Stop while a card waits and right after an answer; a FAIL turn's
    text; the About name; Usage not moving on practice turns. The full item list is in the saved
    workflow script and at the end of `fix-report.md`. Only after that does Wave 0 go back onto the
    owner's app (`tools\local-build.ps1 -OwnerDebug -BuildNumber 16 -Install`, version code above
    1000899, after a fresh copy into `dist/owner-backup/`). Phone: no.
- **20:30 UTC, slice A of Wave 1a is merged into the work branch** (merge commit `9fb25f1` on top
  of the defect fixes `2a1ee46`). The merged tree: the twin compiles, **1409 unit tests, 0
  unexpected failures** (879 of the fixed Wave 0 plus 530 of slice A). The slice's code is pure
  logic that nothing calls yet, so the app on a phone behaves exactly as the fixed Wave 0. The
  worktree `dist/wt-w1` (branch `w1a`) goes on with slice C on top of the same slice A; when slice
  C is committed there, merge the work branch into it first (it does not have the defect fixes).
  To know: `tools\local-build.ps1 -Test` copies the tree to ONE folder (`%TEMP%\agiri-test-copy`)
  whatever checkout it runs from: never run it from two checkouts at the same time. Phone: no.
- **20:35 UTC, the twin-only robot scenarios for the proof of the fixes are being written**
  (workflow `w0-twin-scenarios`, main tree, no phone): five scenario files `tools/scenarios/w0fix-*`
  (sheets at animation scale 1; language inside dialogs; the four approval answers and Stop; errors
  and usage; a regression walk), the missing `dialog.confirm` tags, and a per-checkout folder for
  `local-build.ps1 -Test`. Sonnet writes, Opus checks safety and truth, then self-tests, compile
  and unit tests. Uncommitted changes in the main tree under `tools/` and a few `ui/` files belong
  to it until its entry below says it is finished. Phone: no.
- **20:45 UTC, Wave 1a slice B, first half, is finished** (local branch `w1a-art` in the worktree
  `dist/wt-w1b`, commit `874eb32`; not merged, not pushed). Scripts: `tools/art/stars.py`,
  `tools/places/make_places.py`, `tools/art/make_sounds.py`, `tools/art/pet_clips.py`, new builders
  in `tools/art/craft_art.py` (each written by Sonnet, read by Opus: 28 findings, repaired).
  Generated: `mascot_anim.pxl` (15 sprites, 87 frames), `scene_seasons.pxl` (6), `scene_props.pxl`
  (5), `illustrations.pxl` (11), six WAV cues from noise and tones (250 KB together). The four old
  art files are byte for byte unchanged. **Every sprite was judged on contact sheets by an Opus
  reviewer who did not draw it** (`docs/waves/w1/look/review-ART-PET.txt`, `review-ART-WORLD.txt`),
  12 were repaired and looked at again; **cut:** `pet_stretch` (read as a second "happy"),
  `prop_roj` (a fixed sun contradicts the real sky), `tile_routines` (a candle does not say
  "scheduled prompts"); the authors themselves left out `pet_dance`, `pet_warm`, `pet_poke`. The
  code falls back for each. Report: `docs/waves/w1/slice-b-report.md` (on that branch). To do when
  merging: slice A's stand-in `ui/pixel/PetArt.kt` and `PetAnim.kt` still name `pet_stretch`.
- **20:45 UTC, slice B, second half, was started** (workflow `w1a-slice-b2-data`, same worktree):
  the star table (`assets/sky/stars.bin`) and the city list (`assets/places/zones.tsv`,
  `cities.tsv`) are derived from the two downloaded files; the last two sprites with a FIX line
  (`pet_carry`, `empty_stool`) are repaired; an Opus reviewer ports the Kotlin readers to a
  throwaway script and parses the tables with it, compares stars and cities with the sources, and
  checks the licence wording for `NOTICE`. Phone: no.
- **20:20 UTC (by the PC's clock; the clock times of the entries between 19:45 and here were
  estimates and run up to half an hour fast), the phone is back** (the owner said so through the
  watcher session: use the twin first, the owner's app only for checks that change nothing).
  **The proof of the fixes on the twin is running** (workflow `w0-fix-proof-on-twin`, main tree;
  the saved script holds the full item list): install the twin over the installed twin, practice
  model on, animation scales at 1, every item judged from the screenshot and the screen dump
  (report: `docs/waves/w0/fix-proof.md`); code the prover changes on the way is read by Opus,
  repaired and proven again; then, only if no severe item fails, the twin-only experiments: S2
  (the optimised build with the twin's package: start, Hermes on practice, approval, icon switch,
  backup, the neural voice under R8), S4 (compile filter and cold start), the smoothness numbers
  of the optimised twin against the debug twin back to back, S7 (memory), and the debug twin put
  back (report: `docs/waves/w0/twin-experiments.md`). The scenario-writing workflow was stopped
  before it changed anything (the robot's scripts must not change under the prover); it is to be
  started again after the proof. **If you take over while this runs:** the twin may be the
  optimised build (not debuggable); `tools\local-build.ps1 -Install` puts the debug twin back; the
  system animation scales must be 1; uncommitted changes under `app/` and `tools/` in the main tree
  belong to the prover. Phone: in use (twin only).
