#!/bin/sh
# Size report of one APK against the size budget (Wave 0, tools/size-budget.txt).
#
#   sh tools/ci/apk-size.sh <apk> <owner|twin> <budget-file> [out-file]
#
# Prints (and writes to out-file when given, replacing it) these lines:
#   variant=<owner|twin>
#   apk_bytes=<size of the file>
#   rootfs_bytes=<stored size of assets/rootfs.tar.xz, 0 when absent>
#   app_bytes=<apk_bytes - rootfs_bytes: the app without the Linux image>
#   dex_bytes=<compressed size of every classes*.dex>
#   native_bytes=<compressed size of everything under lib/>
#   ceiling_bytes=<n|none>
#   verdict=<ok|over|unset>
#
# The ceiling applies to app_bytes of the owner build only (key owner_ceiling_bytes of the budget file). A ceiling of 0
# means "not set yet": verdict=unset, never a failure. The twin is never gated (ceiling_bytes=none, verdict=unset).
# Exit: 0 ok or unset, 1 over, 2 usage or an unreadable input.
# POSIX sh. Needs unzip and awk. The APK is only read.

usage() {
    echo "usage: sh tools/ci/apk-size.sh <apk> <owner|twin> <budget-file> [out-file]" >&2
    exit 2
}

[ $# -ge 3 ] && [ $# -le 4 ] || usage
APK=$1
VARIANT=$2
BUDGET=$3
OUT=${4:-}

case "$VARIANT" in owner | twin) ;; *) echo "apk-size: variant must be owner or twin, got: $VARIANT" >&2; exit 2 ;; esac
[ -f "$APK" ] || { echo "apk-size: no such file: $APK" >&2; exit 2; }
[ -f "$BUDGET" ] || { echo "apk-size: no such budget file: $BUDGET" >&2; exit 2; }
command -v unzip >/dev/null 2>&1 || { echo "apk-size: unzip is needed" >&2; exit 2; }

# value of key=value from the budget file (comments after # and blanks are ignored); empty when the key is absent
budget_value() {
    sed -n "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*\\([0-9][0-9]*\\)[[:space:]]*\\(#.*\\)\\{0,1\\}\$/\\1/p" "$BUDGET" | head -n 1
}

APK_BYTES=$(wc -c < "$APK" | tr -d '[:space:]')

# unzip -lv columns: Length Method Size Cmpr Date Time CRC-32 Name. Size (column 3) is the stored or compressed size.
LISTING=$(unzip -lv "$APK") || { echo "apk-size: cannot read $APK as a zip" >&2; exit 2; }
ROOTFS_BYTES=$(printf '%s\n' "$LISTING" | awk 'NF >= 8 && $NF == "assets/rootfs.tar.xz" { s += $3 } END { print s + 0 }')
DEX_BYTES=$(printf '%s\n' "$LISTING" | awk 'NF >= 8 && $NF ~ /^classes[0-9]*\.dex$/ { s += $3 } END { print s + 0 }')
NATIVE_BYTES=$(printf '%s\n' "$LISTING" | awk 'NF >= 8 && $NF ~ /^lib\// { s += $3 } END { print s + 0 }')
APP_BYTES=$((APK_BYTES - ROOTFS_BYTES))

CEILING=none
VERDICT=unset
if [ "$VARIANT" = owner ]; then
    C=$(budget_value owner_ceiling_bytes)
    [ -n "$C" ] || { echo "apk-size: owner_ceiling_bytes is missing or not a number in $BUDGET" >&2; exit 2; }
    if [ "$C" -gt 0 ]; then
        CEILING=$C
        if [ "$APP_BYTES" -gt "$C" ]; then VERDICT=over; else VERDICT=ok; fi
    fi
fi

REPORT="variant=$VARIANT
apk_bytes=$APK_BYTES
rootfs_bytes=$ROOTFS_BYTES
app_bytes=$APP_BYTES
dex_bytes=$DEX_BYTES
native_bytes=$NATIVE_BYTES
ceiling_bytes=$CEILING
verdict=$VERDICT"

printf '%s\n' "$REPORT"
if [ -n "$OUT" ]; then printf '%s\n' "$REPORT" > "$OUT"; fi

if [ "$VERDICT" = over ]; then
    echo "apk-size: the $VARIANT app (without the Linux image) is $APP_BYTES bytes, above the ceiling of $CEILING" >&2
    exit 1
fi
exit 0
