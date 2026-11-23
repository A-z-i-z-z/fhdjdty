#!/bin/sh
# PocketAgent proot launcher.
#
# SINGLE SOURCE OF TRUTH for how the Debian rootfs is entered. The Android app
# ships this file as an asset and runs it with /system/bin/sh; the CI smoke test
# runs the very same file with a host proot. Keep it POSIX sh (Android's sh is
# mksh) and keep every Android-specific tweak conditional so that it stays inert
# on a plain Linux runner.
#
# Usage:
#   pocketagent-proot.sh [-E NAME=VALUE]... [-- command [args...]]
# With no command an interactive login shell (bash -l) is started.
# -E adds/overrides a variable in the guest environment.
#
# Configuration (environment variables read from the caller's environment):
#   PA_PROOT        path of the proot binary                           (required)
#   PA_ROOTFS       path of the extracted Debian rootfs                (required)
#   PA_STATE        writable host dir for scratch/fake files           (required)
#   PA_LOADER       value for PROOT_LOADER (proot's loader binary)     (optional)
#   PA_NO_SECCOMP   "1" -> PROOT_NO_SECCOMP=1 (ptrace only, slower)    (optional)
#   PA_DNS          space separated nameservers for resolv.conf        (optional)
#   PA_TZ           value for TZ inside the guest                      (optional)
#   PA_KERNEL       fake `uname -r` (default 6.8.0-pocketagent)        (optional)
#   PA_FAKE_PROC    "1" force fake /proc files, "0" never, else auto   (optional)
#   PA_EXTRA_BIND   extra proot -b arguments, space separated          (optional)
#   PA_DRY_RUN      "1" print the final command line and exit          (optional)
#
# Every launch writes the resolved command line to $PA_STATE/last-launch.txt so
# diagnostics can show exactly what was executed.
set -u

fail() { echo "pocketagent-proot: $*" >&2; exit 64; }

[ -n "${PA_PROOT:-}" ]  || fail "PA_PROOT is not set"
[ -n "${PA_ROOTFS:-}" ] || fail "PA_ROOTFS is not set"
[ -n "${PA_STATE:-}" ]  || fail "PA_STATE is not set"
if [ "${PA_DRY_RUN:-}" != 1 ]; then
  [ -x "$PA_PROOT" ]     || fail "proot binary is not executable: $PA_PROOT"
  [ -d "$PA_ROOTFS/usr" ] || fail "rootfs looks empty or incomplete: $PA_ROOTFS"
fi

NL='
'

# ---- options ------------------------------------------------------------------
USER_ENV=""
while [ $# -gt 0 ]; do
  case "$1" in
    -E) [ $# -ge 2 ] || fail "-E needs NAME=VALUE"; USER_ENV="$USER_ENV$NL$2"; shift 2 ;;
    --) shift; break ;;
    *) break ;;
  esac
done
if [ $# -eq 0 ]; then set -- /bin/bash -l; fi

# ---- host side preparation ------------------------------------------------------
if [ "${PA_DRY_RUN:-}" != 1 ]; then
  mkdir -p "$PA_STATE/tmp" "$PA_STATE/shm" "$PA_STATE/fakeproc" 2>/dev/null
  chmod 1777 "$PA_STATE/shm" 2>/dev/null
  mkdir -p "$PA_ROOTFS/tmp" "$PA_ROOTFS/root" "$PA_ROOTFS/dev" "$PA_ROOTFS/proc" "$PA_ROOTFS/sys" "$PA_ROOTFS/etc" 2>/dev/null
  chmod 1777 "$PA_ROOTFS/tmp" 2>/dev/null

  # resolv.conf: Android has none that a glibc process can use.
  if [ -n "${PA_DNS:-}" ]; then
    : > "$PA_ROOTFS/etc/resolv.conf" 2>/dev/null
    for ns in $PA_DNS; do echo "nameserver $ns" >> "$PA_ROOTFS/etc/resolv.conf" 2>/dev/null; done
  elif [ ! -s "$PA_ROOTFS/etc/resolv.conf" ]; then
    rm -f "$PA_ROOTFS/etc/resolv.conf" 2>/dev/null
    printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\n' > "$PA_ROOTFS/etc/resolv.conf" 2>/dev/null
  fi
fi

# Fake /proc files: since Android 8 untrusted apps may not read several of them
# (psutil, uptime, free ... then fail with EACCES). Generated only when the real
# file is unreadable, unless PA_FAKE_PROC forces it.
need_fake() {
  case "${PA_FAKE_PROC:-auto}" in 1) return 0 ;; 0) return 1 ;; esac
  ! cat "$1" >/dev/null 2>&1
}
FAKE_BINDS=""
FAKE_DIR="$PA_STATE/fakeproc"
add_fake() { # name content
  if need_fake "/proc/$1"; then
    if [ "${PA_DRY_RUN:-}" != 1 ]; then printf '%s\n' "$2" > "$FAKE_DIR/$1" 2>/dev/null || return 0; fi
    FAKE_BINDS="$FAKE_BINDS$NL-b$NL$FAKE_DIR/$1:/proc/$1"
  fi
}
add_fake stat "cpu  1050008 127632 898432 43828767 37203 63 99244 0 0 0
cpu0 212383 20476 204704 8389202 7253 42 12597 0 0 0
intr 0
ctxt 0
btime 1700000000
processes 100000
procs_running 1
procs_blocked 0"
add_fake loadavg "0.12 0.07 0.02 2/165 765"
add_fake uptime "12345.67 12345.67"
add_fake version "Linux version ${PA_KERNEL:-6.8.0-pocketagent} (pocketagent@proot) (gcc) #1 SMP"
add_fake vmstat "pgpgin 0
pgpgout 0
pswpin 0
pswpout 0
pgfault 0
pgmajfault 0"

EXTRA_BINDS=""
for b in ${PA_EXTRA_BIND:-}; do EXTRA_BINDS="$EXTRA_BINDS$NL-b$NL$b"; done
SYS_BIND=""
[ -d /sys ] && SYS_BIND="$NL-b${NL}/sys"

# ---- guest environment ----------------------------------------------------------
# `env -i` inside the guest guarantees that nothing from the Android environment
# (LD_PRELOAD, ANDROID_ROOT, TERMUX_*, ...) reaches the guest.
GUEST_ENV="HOME=/root
USER=root
LOGNAME=root
SHELL=/bin/bash
PATH=/root/.local/bin:/root/.hermes/hermes-agent/.hermes/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
LANG=C.UTF-8
LC_ALL=C.UTF-8
TERM=xterm-256color
TMPDIR=/tmp
HERMES_HOME=/root/.hermes
PYTHONUNBUFFERED=1
PYTHONIOENCODING=utf-8
UV_LINK_MODE=copy
UV_NO_CONFIG=1
SSL_CERT_FILE=/etc/ssl/certs/ca-certificates.crt
PA_IN_PROOT=1"
[ -n "${PA_TZ:-}" ] && GUEST_ENV="$GUEST_ENV${NL}TZ=$PA_TZ"
GUEST_ENV="$GUEST_ENV$USER_ENV"

HOST_ENV="PATH=/system/bin:/system/xbin:/usr/local/bin:/usr/bin:/bin
HOME=$PA_STATE
PROOT_TMP_DIR=$PA_STATE/tmp"
[ -n "${PA_LOADER:-}" ] && HOST_ENV="$HOST_ENV${NL}PROOT_LOADER=$PA_LOADER"
[ "${PA_NO_SECCOMP:-}" = 1 ] && HOST_ENV="$HOST_ENV${NL}PROOT_NO_SECCOMP=1"

# ---- final command line (built in the positional parameters) ----------------------
# Prefix items are newline separated; IFS=newline + noglob splits them without
# touching spaces inside paths. The user command (already in "$@") keeps its
# exact argument boundaries.
PREFIX="env${NL}-i${NL}${HOST_ENV}${NL}${PA_PROOT}${NL}-0${NL}--link2symlink${NL}--kill-on-exit${NL}-k${NL}${PA_KERNEL:-6.8.0-pocketagent}${NL}-r${NL}${PA_ROOTFS}${NL}-w${NL}/root${NL}-b${NL}/dev${NL}-b${NL}/proc${SYS_BIND}${NL}-b${NL}${PA_STATE}/shm:/dev/shm${FAKE_BINDS}${EXTRA_BINDS}${NL}/usr/bin/env${NL}-i${NL}${GUEST_ENV}"
OLD_IFS=$IFS
IFS=$NL
set -f
# shellcheck disable=SC2086
set -- $PREFIX "$@"
set +f
IFS=$OLD_IFS

# Record / print the exact command line (values shell-quoted).
quote() {
  case "$1" in
    *[!A-Za-z0-9_./:=@%+,-]*|'') printf "'%s'" "$(printf '%s' "$1" | sed "s/'/'\\\\''/g")" ;;
    *) printf '%s' "$1" ;;
  esac
}
LINE=""
for a in "$@"; do LINE="$LINE $(quote "$a")"; done
if [ "${PA_DRY_RUN:-}" = 1 ]; then printf '%s\n' "${LINE# }"; exit 0; fi
printf '%s\n' "${LINE# }" > "$PA_STATE/last-launch.txt" 2>/dev/null

exec "$@"
