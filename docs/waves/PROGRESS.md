# Agirî, road to 1.0: progress log

This file is the running record of the execution of `docs/phase4-plan.md` (second edition, fourteen
waves). **Read it top to bottom before continuing the work.** It is updated and committed the moment a
step is finished. The plan and `docs/design-language.md` are never edited or shortened: what happened
goes here, not there.

If you are a new agent picking this up: read `AGENTS.md`, then this file, then the part of the plan
for the wave named under "Where we are". Continue from the first step that is not marked done.

## Where we are

- **Wave:** 0, Foundation (plan section 7.0).
- **Step in progress:** the contract `docs/waves/w0-contract.md` is being merged from the nine area
  drafts in `docs/waves/w0/` and reviewed once by Opus. Experiment S1+S3 is still running.
- **Next:** the build, in the five stages listed at the end of `docs/waves/w0/LEAD-DECISIONS.md`.
- **The one thing the owner must do for the plan's test twin:** see the first row of "Waiting for the
  owner".

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
| **One tap on "Install anyway" ("Yine de yükleyin") when the test twin is installed for the first time.** Command, from the repository root, once Wave 0's build tools exist: `powershell -NoProfile -ExecutionPolicy Bypass -File tools\local-build.ps1 -Start` (it installs `dev.pocketagent.app.debug`, "Agirî Test"; the phone then shows Play Protect's "unsafe app blocked" dialog; open its details and tap "Install anyway") | Google Play Protect blocks the first install of any new package that targets API 28. Only the owner may override a security warning on their phone; no agent taps it, changes Play Protect, or uses a flag that skips verification | The wave is proven on the owner's own package with a debug build signed with the same key (`owner-debug`, exactly the kind of build that was on the phone before), after a read-only copy of the app's data to `dist/owner-backup/`; only non-destructive checks run there. Everything that wipes, seeds, restores or replaces the Linux image, and experiments S2, S4, S6 and S7, wait for the twin |
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
