#!/bin/sh
# Signs the owner APK with the private key held in CI secrets (Wave 0). Without the key it only copies the APK, so the
# owner build keeps the committed test key and the workflow still runs on a fork.
#
#   sh tools/ci/sign-owner.sh <in.apk> <out.apk>
#
# Environment (all optional together; set only in the one workflow step that signs):
#   KS_B64        the keystore file, base64. Empty or unset = no private key: copy in to out, print signing=committed.
#   KS_PASS       keystore password
#   KEY_ALIAS     key alias
#   KEY_PASS      key password
#   LINEAGE_B64   optional: the signing lineage file (key rotation), base64
# Prints a single line signing=private or signing=committed, then the signer digest. Never prints a secret: nothing here
# echoes the environment, and the decoded files live in a private temporary directory that a trap removes on every exit.
# Needs apksigner on PATH (build-tools 36.0.0). POSIX sh.

usage() {
    echo "usage: sh tools/ci/sign-owner.sh <in.apk> <out.apk>" >&2
    exit 2
}

[ $# -eq 2 ] || usage
IN=$1
OUT=$2
[ -f "$IN" ] || { echo "sign-owner: no such file: $IN" >&2; exit 2; }
[ "$IN" != "$OUT" ] || { echo "sign-owner: input and output must be different files" >&2; exit 2; }

if [ -z "${KS_B64:-}" ]; then
    cp "$IN" "$OUT" || exit 1
    echo "signing=committed"
    exit 0
fi

command -v apksigner >/dev/null 2>&1 || { echo "sign-owner: apksigner is not on PATH" >&2; exit 2; }
[ -n "${KS_PASS:-}" ] && [ -n "${KEY_ALIAS:-}" ] && [ -n "${KEY_PASS:-}" ] \
    || { echo "sign-owner: KS_B64 is set but KS_PASS, KEY_ALIAS or KEY_PASS is missing" >&2; exit 2; }

TMP=$(mktemp -d) || exit 1
chmod 700 "$TMP"
trap 'rm -rf "$TMP"' EXIT HUP INT TERM

printf '%s' "$KS_B64" | base64 -d > "$TMP/owner.keystore" || { echo "sign-owner: KS_B64 is not valid base64" >&2; exit 1; }
[ -s "$TMP/owner.keystore" ] || { echo "sign-owner: the decoded keystore is empty" >&2; exit 1; }

LINEAGE_ARGS=""
if [ -n "${LINEAGE_B64:-}" ]; then
    printf '%s' "$LINEAGE_B64" | base64 -d > "$TMP/lineage.bin" || { echo "sign-owner: LINEAGE_B64 is not valid base64" >&2; exit 1; }
    [ -s "$TMP/lineage.bin" ] || { echo "sign-owner: the decoded lineage is empty" >&2; exit 1; }
    LINEAGE_ARGS="--lineage $TMP/lineage.bin"
fi

# The passwords travel through the environment (env:NAME), never on the command line.
# shellcheck disable=SC2086
apksigner sign --ks "$TMP/owner.keystore" --ks-key-alias "$KEY_ALIAS" \
    --ks-pass env:KS_PASS --key-pass env:KEY_PASS $LINEAGE_ARGS --out "$OUT" "$IN" || { echo "sign-owner: apksigner sign failed" >&2; rm -f "$OUT"; exit 1; }

CERTS=$(apksigner verify --print-certs "$OUT") || { echo "sign-owner: the signed APK does not verify" >&2; rm -f "$OUT"; exit 1; }
echo "signing=private"
printf '%s\n' "$CERTS" | grep 'Signer #1 certificate SHA-256 digest' || true
exit 0
