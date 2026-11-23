#!/bin/sh
# Fast, runner-independent test of runtime/pocketagent-proot.sh: no real proot, no rootfs.
#
#   sh runtime/test-launcher.sh [SHELL...]      (default: sh; the app runs the launcher with Android's mksh)
#
# A fake "proot" script sits in a directory shaped like Android's nativeLibraryDir
# (.../~~xx==/dev.pocketagent.app-yy==/lib/arm64/libproot.so: the path contains "="). The launcher is run
# for real (not dry-run) and must execute exactly that program. Regression test for
#   env: exec -0: No such file or directory
# which `env -i VARS... $PA_PROOT -0 ...` produced because env took the "=" path for an assignment.
set -u

HERE="$(cd "$(dirname "$0")" && pwd)"
LAUNCH="$HERE/pocketagent-proot.sh"
FAILS=0
DRY=0
pass() { echo "  PASS: $*"; }
fail() { echo "  FAIL: $*"; FAILS=$((FAILS+1)); }

TMP="$(mktemp -d)" || { echo "mktemp failed" >&2; exit 2; }
trap 'rm -rf "$TMP"' EXIT
REC="$TMP/rec"; mkdir -p "$REC"
LIBDIR="$TMP/~~h-vFzyO2kFEAxYJt-QO6ug==/dev.pocketagent.app-UKIpKf_m23aztVt8VabgPA==/lib/arm64"
PROOT="$LIBDIR/libproot.so"
LOADER="$LIBDIR/libproot-loader.so"
ROOTFS="$TMP/rootfs"
STATE="$TMP/state dir"          # a space on purpose: paths are never word-split
mkdir -p "$LIBDIR" "$ROOTFS/usr" "$STATE"

# The fake proot records what it was started with, then exits 7. It must not rely on the environment it
# is given (the launcher uses env -i), hence the hard-coded record directory.
{
  echo '#!/bin/sh'
  echo "REC='$REC'"
  cat <<'EOF'
printf '%s\n' "$0" > "$REC/argv0"
printf '%s\n' "$@" > "$REC/args"
env > "$REC/env"
exit 7
EOF
} > "$PROOT"
: > "$LOADER"
chmod 755 "$PROOT" "$LOADER"

# Caller environment: what an Android app process has, plus the launcher's own configuration.
launch() { # shell launcher-args...
  _sh="$1"; shift
  env ANDROID_ROOT=/leak ANDROID_DATA=/leak ANDROID_ART_ROOT=/leak BOOTCLASSPATH=/leak \
      LD_LIBRARY_PATH=/leak TERMUX_VERSION=leak EXTERNAL_STORAGE=/leak \
      PA_PROOT="$PROOT" PA_LOADER="$LOADER" PA_ROOTFS="$ROOTFS" PA_STATE="$STATE" PA_DNS=127.0.0.1 \
      PA_FAKE_PROC=0 PA_DRY_RUN="$DRY" "$_sh" "$LAUNCH" "$@"
}

run_one() { # shell
  SH="$1"
  echo "== launcher under: $SH"
  if ! command -v "$SH" >/dev/null 2>&1; then fail "$SH is not installed"; return; fi

  # 1. the real launch
  rm -f "$REC"/* "$STATE/last-launch.txt"
  launch "$SH" -E MYVAR=a=b -- mycmd 'two  words' "it's \"q\"" '$HOME *'; rc=$?
  [ -f "$REC/args" ] && pass "the fake proot was executed (despite '=' in $PROOT)" || fail "the fake proot never ran (rc=$rc)"
  [ "$rc" = 7 ] && pass "the launcher exec'd it: exit status 7 came straight through" || fail "exit status $rc, expected 7"
  [ -f "$REC/argv0" ] && [ "$(cat "$REC/argv0")" = "$PROOT" ] && pass "program executed is exactly PA_PROOT" || fail "argv0 is '$(cat "$REC/argv0" 2>/dev/null)'"
  [ "$(head -n 1 "$REC/args" 2>/dev/null)" = "-0" ] && pass "first argument of proot is -0" || fail "first argument is '$(head -n 1 "$REC/args" 2>/dev/null)'"
  printf '%s\n' mycmd 'two  words' "it's \"q\"" '$HOME *' > "$REC/expect"
  tail -n 4 "$REC/args" > "$REC/tail" 2>/dev/null
  cmp -s "$REC/expect" "$REC/tail" && pass "user command argument boundaries survive (spaces, quote, \$, *)" || { fail "user command arguments changed"; sed 's/^/    | /' "$REC/args"; }
  grep -qxF "PA_IN_PROOT=1" "$REC/args" && grep -qxF "MYVAR=a=b" "$REC/args" && grep -qxF "/usr/bin/env" "$REC/args" && pass "guest side: /usr/bin/env -i with the guest variables and -E" || fail "guest env arguments missing"
  grep -qxF -- "$ROOTFS" "$REC/args" && grep -qxF -- "$STATE/shm:/dev/shm" "$REC/args" && pass "-r / -b arguments carry the (spaced) paths intact" || fail "rootfs / state path arguments"

  # 2. environment isolation of the host side
  if [ -f "$REC/env" ]; then
    if grep -Eq '^(ANDROID_|BOOTCLASSPATH=|LD_PRELOAD=|LD_LIBRARY_PATH=|TERMUX_|EXTERNAL_STORAGE=|PA_)' "$REC/env"; then
      fail "caller variables leaked into proot:"; grep -E '^(ANDROID_|BOOTCLASSPATH=|LD_PRELOAD=|LD_LIBRARY_PATH=|TERMUX_|EXTERNAL_STORAGE=|PA_)' "$REC/env" | sed 's/^/    | /'
    else pass "no Android / PA_* variable of the caller leaked into proot"; fi
    grep -q '^PATH=/system/bin:' "$REC/env" && grep -qxF "HOME=$STATE" "$REC/env" && grep -qxF "PROOT_TMP_DIR=$STATE/tmp" "$REC/env" && grep -qxF "PROOT_LOADER=$LOADER" "$REC/env" \
      && pass "host env has PATH, HOME, PROOT_TMP_DIR and PROOT_LOADER (also a '=' path)" || fail "host env incomplete"
    grep -q '^PROOT_NO_SECCOMP=' "$REC/env" && fail "PROOT_NO_SECCOMP set without PA_NO_SECCOMP" || pass "PROOT_NO_SECCOMP unset by default"
  else fail "no environment recorded"; fi

  # 3. the recorded / dry-run command line is the one that really ran
  if [ -s "$STATE/last-launch.txt" ]; then
    case "$(cat "$STATE/last-launch.txt")" in
      "env -i "*"sh -c 'exec \"\$@\"' sh "*) pass "last-launch.txt shows the sh trampoline between the env assignments and proot" ;;
      *) fail "last-launch.txt has no trampoline: $(cat "$STATE/last-launch.txt")" ;;
    esac
    grep -qF -- "$PROOT" "$STATE/last-launch.txt" && pass "last-launch.txt names the proot path" || fail "last-launch.txt lacks the proot path"
    DRY=1; dry="$(launch "$SH" -E MYVAR=a=b -- mycmd 'two  words' "it's \"q\"" '$HOME *')"; DRY=0
    [ "$dry" = "$(cat "$STATE/last-launch.txt")" ] && pass "PA_DRY_RUN output equals last-launch.txt" || fail "dry-run differs from last-launch.txt"
  else fail "last-launch.txt missing"; fi

  # 4. a command starting with NAME=VALUE is refused (the guest env would swallow it)
  rm -f "$REC"/*
  launch "$SH" -- FOO=bar mycmd >/dev/null 2>"$REC/err"; rc=$?
  [ "$rc" = 64 ] && [ ! -f "$REC/args" ] && pass "command starting with NAME=VALUE is refused (rc 64), proot not run" || fail "NAME=VALUE command: rc=$rc"

  # 5. -E must be NAME=VALUE (a bare word would be run as the command by the guest's env)
  for bad in FOO =x; do
    rm -f "$REC"/*
    launch "$SH" -E "$bad" -- mycmd >/dev/null 2>"$REC/err"; rc=$?
    [ "$rc" = 64 ] && [ ! -f "$REC/args" ] && pass "-E '$bad' is refused (rc 64), proot not run" || fail "-E '$bad': rc=$rc"
  done
}

SHELLS="${*:-sh}"
for s in $SHELLS; do run_one "$s"; done

echo "== negative control (informational): the old construction on this system"
rm -f "$REC"/*
env -i PATH="$PATH" "$PROOT" -0 >/dev/null 2>&1
if [ -f "$REC/args" ]; then echo "  note: this env does NOT mistake a '=' path for an assignment, so this system cannot reproduce the old bug"
else echo "  ok: plain 'env -i VARS <path with => -0' fails here, as it did on the device"; fi

echo
if [ $FAILS -eq 0 ]; then echo "LAUNCHER TEST PASSED"; exit 0; fi
echo "LAUNCHER TEST FAILED ($FAILS failures)"; exit 1
