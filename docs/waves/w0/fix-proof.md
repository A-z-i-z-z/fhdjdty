# Wave 0 defect fixes: the proof on the twin (2026-11-25)

**Status: blocked at the first start. No item of the proof was run.**

## What was installed

- `tools\local-build.ps1 -Install` built the current tree (HEAD plus the untracked `docs/waves/w1b/`, no code change) and installed it over the
  twin in place: `dev.pocketagent.app.debug`, version `0.2.8-local2-test`, version code 802 (the installed twin was 801). Install result: Success,
  no Play Protect dialog. The variant check of the APK said `twin`.
- The owner's package was only read (`dumpsys package`); nothing else was done to it.

## Why the proof stopped

The first start of the twin (a robot `link` with the MAIN intent) put a system dialog on top of the app: **"Android App Compatibility"**, with the
buttons "OK" and "Don't Show Again". It says the app is debuggable and "isn't 16 KB compatible" (ELF alignment check failed for
`libsherpa-onnx-jni.so`, `libproot.so`, `libproot-loader.so` and `libandroidx.graphics.path.so`). The window in focus is `android`, not the twin
(the twin's MainActivity is only the focused app behind it).

The rules for this run say never to tap a system dialog that was not expected, and to report it. The robot's focus guard also refuses every tap
while the focus is not the twin. So I sent no input, and I did not use "Don't Show Again" (that would write a system setting). A screenshot is in
`dist\proof\w0-fix\dialog-first-start.png` (git-ignored).

Notes for whoever continues:
- AGENTS.md already records that a build for a newer target API shows a compatibility dialog at every cold start. This one is the 16 KB check of
  the page-size compatibility layer; the owner-debug build of the earlier proof did not show it in the reports, probably because it had been
  dismissed on the owner's package before. The twin is a new package, so it is shown again.
- It will most likely come back at every cold start of the twin until someone taps OK (or "Don't Show Again"). A person has to tap it once, or
  the owner has to decide that the robot may tap "OK" on this one dialog (it is informational, and the robot's focus guard would need `android`
  added to the allowed surfaces of one op).
- The real fix is 16 KB alignment of the four native libraries (`libproot-loader.so` is ours: its LOAD segment is not aligned; the proot build
  would need the linker flag `-Wl,-z,max-page-size=16384`; the other three are prebuilt libraries). That is a Wave 1 or later item, not part of this proof.

## Items

| Item | Result |
|---|---|
| S1a-S1g, S2, S3, E1-E6, M1, R1 | not tried (blocked by the dialog above) |

## Numbers, causes, new defects

- No code file was changed. No robot script was changed.
- New finding: the first start of the twin shows the system's 16 KB compatibility dialog (see above).

## State of the phone at the end

- The twin is in front with the system dialog still open (nothing was tapped). Its Hermes was never started (the Home screen shows the sleeping
  fire). The practice model was not switched on.
- Animation scales: window, transition and animator scale are 1 and were never changed; font scale 1.0; no `system-settings-backup.json`.
- The owner's app `dev.pocketagent.app` was not touched.

## Not tried

Everything in the task list, to be done once the dialog has been dismissed by a person (or its handling has been allowed).
