# Agirî, road to 1.0: progress log

This file is the running record of the execution of `docs/phase4-plan.md` (second edition, fourteen
waves). **Read it top to bottom before continuing the work.** It is updated and committed the moment a
step is finished. The plan and `docs/design-language.md` are never edited or shortened: what happened
goes here, not there.

If you are a new agent picking this up: read `AGENTS.md`, then this file, then the part of the plan
for the wave named under "Where we are". Continue from the first step that is not marked done.

## Where we are

- **Wave:** 0, Foundation (plan section 7.0).
- **Step in progress:** experiments S1 to S7 on the phone, and the survey of the code for the contract.
- **Next:** write `docs/waves/w0-contract.md`, then the ten build agents.

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
| A private signing key for the owner's build (plan 0.1, experiment S6) | The key must exist only on the owner's PC and as a CI secret, and installing a differently signed build over the owner's app is forbidden in this run | The owner's build keeps the committed key. S6 is tried only under a throwaway package name. The build scripts are written so that the key becomes a one-step change later |
| A test credential imported into the test twin (plan section 6, "What the owner does") | The twin never sees the owner's credentials, and secrets are never typed by an agent | Everything is proven on the practice model (zero tokens). The few real-model checks are done in the owner's app after a wave has shipped to it, on Hydrogen at low effort |
| A spending cap at the provider (decision 20) | An account setting at the provider | The app's own daily budget; test turns stay few |
| S4, second half: does ahead-of-time compilation survive a reboot and a day | A reboot would lock the phone behind its PIN for the rest of the night | The compile command is repeated at every install (the plan's fallback), and the check after a reboot is left for a day when the owner is at hand |
| The three manual checks of plan 0.4 | They need a person or a key: the camera shutter, spoken dictation, a new provider with a real key | Listed in `docs/APP_OVERVIEW.md` as not tried |

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
