#!/bin/sh
# Test of tools/ci/apk-size.sh with small fake APKs (a zip with a stored rootfs entry, a dex and a native library).
#
#   sh tools/ci/test-apk-size.sh
#
# Needs zip and unzip. When zip is missing the test says so and exits 0 (the CI runner has it).
# Exit: 0 all passed (or skipped), 1 a check failed.
# POSIX sh.

HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
SIZE="$HERE/apk-size.sh"
[ -f "$SIZE" ] || { echo "test-apk-size: $SIZE not found" >&2; exit 1; }

if ! command -v zip >/dev/null 2>&1 || ! command -v unzip >/dev/null 2>&1; then
    echo "test-apk-size: SKIPPED (zip or unzip is not installed)"
    exit 0
fi

WORK=$(mktemp -d) || exit 1
trap 'rm -rf "$WORK"' EXIT HUP INT TERM

FAILED=0
pass() { echo "ok   $1"; }
bad() { echo "FAIL $1" >&2; FAILED=1; }

# $1 name, $2 expected exit, $3 expected substring of the output (or empty); the rest is the command
check() {
    name=$1; want_exit=$2; want_text=$3; shift 3
    out=$("$@" 2>&1); code=$?
    if [ "$code" -ne "$want_exit" ]; then bad "$name (exit $code, expected $want_exit): $out"; return; fi
    if [ -n "$want_text" ] && ! printf '%s\n' "$out" | grep -q -- "$want_text"; then bad "$name (output lacks '$want_text'): $out"; return; fi
    pass "$name"
}

# fake APK: 1,000,000 stored bytes as the "Linux image", 20,000 bytes dex, 30,000 bytes native library
mkdir -p "$WORK/src/assets" "$WORK/src/lib/arm64-v8a"
head -c 1000000 /dev/zero | tr '\0' 'x' > "$WORK/src/assets/rootfs.tar.xz"
head -c 20000 /dev/zero | tr '\0' 'd' > "$WORK/src/classes.dex"
head -c 30000 /dev/zero | tr '\0' 'n' > "$WORK/src/lib/arm64-v8a/libfake.so"
APK="$WORK/fake.apk"
(cd "$WORK/src" && zip -q -0 "$APK" assets/rootfs.tar.xz && zip -q "$APK" classes.dex lib/arm64-v8a/libfake.so) \
    || { echo "test-apk-size: could not build the fake APK" >&2; exit 1; }
APK_BYTES=$(wc -c < "$APK" | tr -d '[:space:]')

field() { printf '%s\n' "$1" | sed -n "s/^$2=//p"; }

# a budget with the ceiling not set
printf 'owner_baseline_bytes=0   # comment\nowner_ceiling_bytes=0    # 0 = not set yet\ntwin_recorded_bytes=5\n' > "$WORK/unset.txt"
OUTPUT=$(sh "$SIZE" "$APK" owner "$WORK/unset.txt" "$WORK/report.txt"); CODE=$?
[ "$CODE" -eq 0 ] && pass "ceiling 0 exits 0" || bad "ceiling 0 exited $CODE"
[ "$(field "$OUTPUT" verdict)" = unset ] && pass "ceiling 0 gives verdict=unset" || bad "ceiling 0 verdict: $OUTPUT"
[ "$(field "$OUTPUT" ceiling_bytes)" = none ] && pass "ceiling 0 prints ceiling_bytes=none" || bad "ceiling 0 ceiling: $OUTPUT"
[ "$(field "$OUTPUT" variant)" = owner ] && pass "variant is printed" || bad "variant: $OUTPUT"
[ "$(field "$OUTPUT" apk_bytes)" = "$APK_BYTES" ] && pass "apk_bytes is the file size" || bad "apk_bytes: $OUTPUT (file $APK_BYTES)"
[ "$(field "$OUTPUT" rootfs_bytes)" = 1000000 ] && pass "rootfs_bytes is the stored size of the image" || bad "rootfs_bytes: $OUTPUT"
APP=$(field "$OUTPUT" app_bytes)
[ "$APP" = "$((APK_BYTES - 1000000))" ] && pass "app_bytes is apk minus rootfs" || bad "app_bytes: $OUTPUT"
DEX=$(field "$OUTPUT" dex_bytes)
[ "$DEX" -gt 0 ] && [ "$DEX" -le 20000 ] && pass "dex_bytes counts the compressed dex" || bad "dex_bytes: $OUTPUT"
NAT=$(field "$OUTPUT" native_bytes)
[ "$NAT" -gt 0 ] && [ "$NAT" -le 30000 ] && pass "native_bytes counts the compressed library" || bad "native_bytes: $OUTPUT"
[ -f "$WORK/report.txt" ] && [ "$(field "$(cat "$WORK/report.txt")" app_bytes)" = "$APP" ] && pass "the report file is written" || bad "report file missing or different"

# a ceiling the app stays under, and one it does not
printf 'owner_baseline_bytes=1\nowner_ceiling_bytes=%s\n' "$((APP + 10))" > "$WORK/roomy.txt"
check "under the ceiling is ok" 0 "verdict=ok" sh "$SIZE" "$APK" owner "$WORK/roomy.txt"
printf 'owner_baseline_bytes=1\nowner_ceiling_bytes=%s\n' "$((APP - 1))" > "$WORK/tight.txt"
check "over the ceiling exits 1" 1 "verdict=over" sh "$SIZE" "$APK" owner "$WORK/tight.txt"
printf 'owner_baseline_bytes=1\nowner_ceiling_bytes=%s\n' "$APP" > "$WORK/exact.txt"
check "exactly at the ceiling is ok" 0 "verdict=ok" sh "$SIZE" "$APK" owner "$WORK/exact.txt"

# the twin is never gated, even against a tight ceiling
check "the twin is never gated" 0 "verdict=unset" sh "$SIZE" "$APK" twin "$WORK/tight.txt"

# bad usage
check "no arguments exit 2" 2 "usage" sh "$SIZE"
check "an unknown variant exits 2" 2 "variant" sh "$SIZE" "$APK" phone "$WORK/unset.txt"
check "a missing APK exits 2" 2 "no such file" sh "$SIZE" "$WORK/none.apk" owner "$WORK/unset.txt"
check "a missing budget exits 2" 2 "budget" sh "$SIZE" "$APK" owner "$WORK/none.txt"
printf 'owner_baseline_bytes=1\n' > "$WORK/nokey.txt"
check "a budget without the ceiling key exits 2" 2 "owner_ceiling_bytes" sh "$SIZE" "$APK" owner "$WORK/nokey.txt"
printf 'not a zip' > "$WORK/notzip.apk"
check "a file that is not a zip exits 2" 2 "" sh "$SIZE" "$WORK/notzip.apk" owner "$WORK/unset.txt"

if [ "$FAILED" -ne 0 ]; then
    echo "test-apk-size: FAILED" >&2
    exit 1
fi
echo "test-apk-size: all passed"
exit 0
