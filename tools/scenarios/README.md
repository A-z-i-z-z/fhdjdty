# Scenarios of the phone robot

`tools/phone-test.ps1` runs the JSON files in this folder against the app on a phone; `tools/phone-perf.ps1` runs the files in `perf/`.
The engine is `tools/phone-lib.ps1`. Everything runs from the repo root through the execution-policy switch of this PC:

```
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phone-test.ps1 -List
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phone-test.ps1 -Suite smoke
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phone-test.ps1 -Owner -Suite smoke
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phone-perf.ps1 -Owner -Scenario P1,P5,P6,P7
```

## Who is the target

| Switch | Package | Which scenarios |
|---|---|---|
| none | the test twin (`$PaTwinPackage` of `tools/build-ids.ps1`) | the suite asked for; the twin-only ones check the variant line of `app.log` first |
| `-Owner` | the owner's app (`$PaOwnerPackage`) | only files with `"safe": true`; default suite `smoke` |

A scenario marked `"safe": true` reads, navigates and takes pictures. It never wipes, seeds, writes a preference, kills the guest, restores,
installs, runs a free adb call, picks a file or changes a system scale (`ScenarioFilesTest` pins that), it types only `hermes --version`
into `terminal.input`, and it is not flagged `practice`. On the owner's package the robot also types only plain words, `LONG` and `PAUSE`
(`Test-PaOwnerTypeAllowed`): a whole-word `MEMORY` or `FORGE` would write into the owner's memory or bots.

Every op that changes data starts with `Assert-PaTwinTarget` (package is the twin AND the newest start line of `app.log` says
`variant=twin`). System settings are changed in one function only, `Set-PaSystemScales`, which first records the four current values in
`dist\phone-test\system-settings-backup.json`, restores them in a `finally`, and on the owner's package works only with
`-AllowSystemSettings` (the lead passes it).

## File format

```json
{ "name": "smoke-launch", "title": "...", "flags": ["core", "smoke"], "safe": true, "practice": false,
  "cutCheck": false, "vars": {"lang": "en-XZ"}, "timeoutSec": 120,
  "steps": [ { "op": "tap", "any": [ {"tag": "tab.home"}, {"desc": "Home"} ] } ],
  "cleanup": [ ] }
```

- `name` equals the file name. `flags`: `core` (default suite), `smoke`, `slow` (only with `-Suite slow` or `all`), `perf`.
- `practice: true` turns the practice model on through Settings > Runtime before the steps (twin only in `phone-test`; `phone-perf` also does it
  for `P4` on the owner's package, with the alarm check, the proof from `app.log` and the switch off again in a `finally`).
- `cutCheck: true`: after the normal pass the runner sets font scale 2.0 and runs the steps again with the cut check on. On the owner's package
  that second pass is skipped (with the reason) unless `-AllowSystemSettings` is given.
- `vars` are defaults for `{{var:name}}`; `-Var name=value` overrides them.
- `cleanup` steps run in a `finally`, whatever happened; their failures are only noted. With `cleanupChain: true` the first failed cleanup step
  ends the cleanup (use it when the first steps prove where the run is before a later step deletes something; P4 does).

### Selectors

`tag` (the resource id after the last `/`, i.e. the test tag), `text`, `desc`, `textContains`, `descContains`, `re` (regex over text, then
description), `tagRe` (regex over the tag), `idx` (n-th match, default 0; negative counts from the end, -1 is the last), `scope` (`app` or `systemui`) and `any: [selector, ...]` (the first alternative that matches wins;
used as tag-then-English fallback so a build without tags still runs). A tap goes to the centre of the node's bounds. `checked`, `enabled`,
`selected` read the node or its first checkable descendant. Placeholders in any string: `{{pkg}}`, `{{latestChat}}`, `{{now}}`, `{{var:name}}`.

### Ops

| op | parameters |
|---|---|
| `link` | `action` (`OPEN_CHAT` `NEW_CHAT` `TERMINAL` `ROUTINES` `USAGE`, `conv_id`), or `debug` {`route`, `clock`, `lang`, `rtl`, `reset`, `seed`, `nav`}, or `intent` {`action`, `data`} |
| `tap` | selector, `timeout` (10), `holdMs` (long press), `cache` (reuse the position of an earlier tap: perf runs, no dump in between) |
| `type` | selector or none (the focused field), `text` (ASCII, no single quote), `clear`, `submit` |
| `wait` | `ms`, or a selector (`gone`), or `state` (`Running` `Stopped` `Error`), or `log` (regex over `app.log` since the scenario began); `timeout` (30); `navTop` = a route key: when the screen tag is missing, the navigator's own `I/Nav: top=` line may say it; `captureChat` = a variable name: after the wait succeeds, the id of the chat on screen (from the last `I/Nav: top=chat:<id>` line) is kept in `{{var:name}}` until the scenario ends |
| `expect`, `expectNot` | selector or `log`; `count`, `checked`, `enabled`, `selected`; or `navIs` (the route key of the last `I/Nav: top=` line, e.g. `chat:{{var:name}}`) and/or `chatHas` (plain text that is in the stored file of the chat on screen, read-only); `timeout` (8, and 3 for `expectNot`) |
| `shot` | `name` |
| `kill`, `forceStop`, `key` (`HOME` `BACK` `ENTER`), `swipe` (`dir` up or down, `ms`, `times`), `noCrash` | `kill` ends a background process and keeps alarms; `forceStop` cancels them |
| `a11y` | the spoken-label check (and the cut check in a cut pass) on the current screen |
| `scanDump` | keeps the screen dump and runs `tools/strings-check.ps1 -ScanDump` on it with `leak-allow.txt`; plain English left in the stretched language is collected and fails the scenario at its end |
| `eachRoute` | runs the nested `steps` once per route of `routes.json` with `{{var:route}}` and `{{var:screen}}` |
| `measure` | perf only: `action` `start` or `stop` limits the frame window |
| `notification`, `expectColor` | fragile (One UI's shade) / pixel check by `System.Drawing` |
| `wipe`, `pref`, `seed`, `killGuest`, `expectFile`, `restore`, `install`, `pick`, `adb` | twin only; each starts with `Assert-PaTwinTarget` |
| `scale` | `font` or `animations`; through `Set-PaSystemScales` only |

### The two automatic checks

Every dump the robot takes is checked once per screen: **label** (every clickable, checkable or long-clickable node of the app, larger than 4 px
and on screen, has a text, a description or a labelled descendant) and, in a cut pass at font 2.0, **cut** (a text node outside the screen by more than
2 px in a chain with no scrolling ancestor). Findings are printed as `A11Y screen=<name>#<signature> id=<tag or class@bounds> no spoken label` and
`CUT screen=... "<text>"`. `a11y-allow.json` and `cut-allow.json` hold accepted ones (`screen`, `id`, `reason`).

## Output and exit codes

`PASS name 41.2s`, `FAIL name step 7 ...`, `SKIP name (reason)`, `A11Y ...`, `CUT ...`, last line `SUMMARY pass=n fail=n skip=n a11y=n cut=n`. A report
folder `dist\phone-test\<versionName>\<yyyyMMdd-HHmmss>\` holds `report.json`, `report.md`, the pictures, failure dumps and `logcat.txt` (and
`app.log` on the twin; never on the owner's package). Exit 0 all passed, 1 a failure or an unlisted finding, 2 usage or environment (no phone, package
missing, screen off or locked), 3 nothing ran or refused for safety.

## Files here

| File | What it is |
|---|---|
| the nine `*.json` scenarios | the eight safe ones (`smoke-*`, `design-gallery`, `sweep-*`) and `nav-state-restore` (twin only) |
| `perf/P1, P4, P5, P6, P7` | smoothness, streaming, transitions, cold start, Home idle (`P2`, `P3` need the seeding link of the twin and are written later) |
| `routes.json` | the routes the sweeps visit |
| `a11y-allow.json`, `cut-allow.json`, `leak-allow.txt` | accepted findings |
| `fixtures/` | a hand-written UI dump and a hand-written `gfxinfo` capture for `-SelfTest`; never captured from the owner's app |

The twin-only scenarios of the plan (practice switch, chat turns, budget, approval, notification reply, idle stop, restore into a wiped twin and the rest)
are written once the first compile is green and the twin can be installed.

## Known limits

- `uiautomator dump` waits for an idle UI and the Home scene never stops moving: dumps are retried three times, and the functional runs set the
  animation scales to 0 (twin always; owner package only with `-AllowSystemSettings`).
- `input text` is ASCII only: the language scenarios use the debug link, not typing.
- `dumpsys gfxinfo framestats` keeps 120 frames: the perf run polls every 400 ms and merges by `IntendedVsync`; a dump in the middle of a measured
  window disturbs the frames, so perf scenarios tap with `cache` and wait with `ms`.
- The new-chat and delete-chat steps of `P4` and the editor name field of `nav-state-restore` are written against English labels and are unproven until the
  first run.
