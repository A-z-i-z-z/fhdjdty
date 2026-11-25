#!/bin/sh
# Checks one built APK against what the build was supposed to make (Wave 0).
#
#   sh tools/ci/verify-apk.sh <apk> <package> <debuggable: 0|1> <expect-versionCode | ->
#
# Asserts: the package id; application-debuggable present exactly when 1 was given; the version code (unless "-");
# target SDK 28 (the extracted Linux image needs it); the entries lib/arm64-v8a/libproot.so and assets/rootfs.tar.xz
# (the latter Stored, not compressed); and a valid signature, whose SHA-256 certificate digest it prints.
# Every mismatch is reported; the exit code is 1 when there was any, 2 for bad usage, else 0.
# Needs aapt2, apksigner (the workflow puts build-tools 36.0.0 on PATH) and unzip. The APK is only read. POSIX sh.

usage() {
    echo "usage: sh tools/ci/verify-apk.sh <apk> <package> <debuggable:0|1> <expect-versionCode|->" >&2
    exit 2
}

[ $# -eq 4 ] || usage
APK=$1
PKG=$2
DEBUGGABLE=$3
WANT_CODE=$4

case "$DEBUGGABLE" in 0 | 1) ;; *) usage ;; esac
case "$WANT_CODE" in - | [0-9]*) ;; *) usage ;; esac
[ -f "$APK" ] || { echo "verify-apk: no such file: $APK" >&2; exit 2; }
for tool in aapt2 apksigner unzip; do
    command -v "$tool" >/dev/null 2>&1 || { echo "verify-apk: $tool is not on PATH" >&2; exit 2; }
done

BAD=0
fail() { echo "MISMATCH: $1" >&2; BAD=1; }

GOT_PKG=$(aapt2 dump packagename "$APK" 2>/dev/null | tr -d '[:space:]')
[ "$GOT_PKG" = "$PKG" ] || fail "package is '$GOT_PKG', expected '$PKG'"
echo "package: $GOT_PKG"

BADGING=$(aapt2 dump badging "$APK" 2>/dev/null)
GOT_CODE=$(printf '%s\n' "$BADGING" | sed -n "s/^package: .* versionCode='\\([0-9]*\\)'.*/\\1/p" | head -n 1)
GOT_NAME=$(printf '%s\n' "$BADGING" | sed -n "s/^package: .* versionName='\\([^']*\\)'.*/\\1/p" | head -n 1)
echo "version: $GOT_NAME ($GOT_CODE)"
if [ "$WANT_CODE" != "-" ] && [ "$GOT_CODE" != "$WANT_CODE" ]; then
    fail "versionCode is '$GOT_CODE', expected '$WANT_CODE'"
fi

if printf '%s\n' "$BADGING" | grep -q '^application-debuggable'; then IS_DEBUGGABLE=1; else IS_DEBUGGABLE=0; fi
echo "debuggable: $IS_DEBUGGABLE"
[ "$IS_DEBUGGABLE" = "$DEBUGGABLE" ] || fail "debuggable is $IS_DEBUGGABLE, expected $DEBUGGABLE"

TARGET=$(printf '%s\n' "$BADGING" | sed -n "s/^targetSdkVersion:'\\([0-9]*\\)'.*/\\1/p" | head -n 1)
echo "targetSdk: $TARGET"
[ "$TARGET" = 28 ] || fail "targetSdkVersion is '$TARGET', expected 28"

LISTING=$(unzip -lv "$APK") || { echo "verify-apk: cannot read $APK as a zip" >&2; exit 1; }
printf '%s\n' "$LISTING" | awk 'NF >= 8 && $NF == "lib/arm64-v8a/libproot.so" { f = 1 } END { exit f ? 0 : 1 }' \
    || fail "entry lib/arm64-v8a/libproot.so is missing"
METHOD=$(printf '%s\n' "$LISTING" | awk 'NF >= 8 && $NF == "assets/rootfs.tar.xz" { print $2 }' | head -n 1)
if [ -z "$METHOD" ]; then
    fail "entry assets/rootfs.tar.xz is missing"
elif [ "$METHOD" != "Stored" ]; then
    fail "assets/rootfs.tar.xz is compressed ($METHOD); it must be Stored (the app opens it by file descriptor)"
fi

# apksigner exits non-zero on a broken signature; the digest goes to the log so the signer can be compared by eye.
if CERTS=$(apksigner verify --print-certs "$APK" 2>&1); then
    printf '%s\n' "$CERTS" | grep -E 'Signer #1 certificate (DN|SHA-256 digest)' | sed 's/^/signature: /'
else
    printf '%s\n' "$CERTS" | sed 's/^/apksigner: /' >&2
    fail "the signature does not verify"
fi

if [ "$BAD" -ne 0 ]; then
    echo "verify-apk: $APK FAILED" >&2
    exit 1
fi
echo "verify-apk: $APK ok"
exit 0
