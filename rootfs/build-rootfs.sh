#!/bin/bash
# Build the PocketAgent Debian (trixie) rootfs with Hermes Agent preinstalled.
#
#   rootfs/build-rootfs.sh --arch arm64|amd64 --out DIR [options]
#
# Needs root (uses chroot + bind mounts), debootstrap, xz, curl. Works on a native
# host of the target architecture (GitHub `ubuntu-24.04-arm` for arm64,
# `ubuntu-24.04` for amd64). Cross-building is deliberately not supported: the
# Hermes installer downloads arch-specific Python/uv builds and runs them.
#
# Outputs in DIR:
#   rootfs-<arch>.tar.xz     the archive that is embedded in the APK
#   rootfs-<arch>.info       key=value build facts (also stored inside the image
#                            as /etc/pocketagent-rootfs)
# and the unpacked tree stays in WORK/rootfs (used by the smoke test).
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ARCH=""; OUT=""; WORK=""
DEBIAN_SUITE="${DEBIAN_SUITE:-trixie}"
MIRROR="${DEBIAN_MIRROR:-http://deb.debian.org/debian}"
HERMES_TAG="${HERMES_TAG:-v0.23.3}"
# Full commit the tag must resolve to (guards against a moved tag).
HERMES_COMMIT="${HERMES_COMMIT:-caafe5389ae585ef77387a962aee05b1bcf17029}"
XZ_LEVEL="${XZ_LEVEL:-9}"
PRUNE=1
# Local-development hooks (corporate/sandbox proxies). Never used in CI.
#   ROOTFS_BUILD_ENV="HTTPS_PROXY=... SSL_CERT_FILE=..."   extra env inside the chroot
#   ROOTFS_BUILD_CA=/path/ca.pem                           extra CA trusted during build only
ROOTFS_BUILD_ENV="${ROOTFS_BUILD_ENV:-}"
ROOTFS_BUILD_CA="${ROOTFS_BUILD_CA:-}"

while [ $# -gt 0 ]; do
  case "$1" in
    --arch) ARCH="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    --work) WORK="$2"; shift 2 ;;
    --hermes-tag) HERMES_TAG="$2"; shift 2 ;;
    --hermes-commit) HERMES_COMMIT="$2"; shift 2 ;;
    --no-prune) PRUNE=0; shift ;;
    -h|--help) sed -n '2,16p' "$0"; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done
case "$ARCH" in arm64|amd64) ;; *) echo "--arch must be arm64 or amd64" >&2; exit 2 ;; esac
[ -n "$OUT" ] || { echo "--out is required" >&2; exit 2; }
[ "$(id -u)" -eq 0 ] || { echo "run as root (sudo)" >&2; exit 2; }
HOST_ARCH="$(dpkg --print-architecture)"
[ "$HOST_ARCH" = "$ARCH" ] || { echo "host arch $HOST_ARCH != target $ARCH (native builds only)" >&2; exit 2; }
WORK="${WORK:-$OUT/work}"
ROOT="$WORK/rootfs"
mkdir -p "$OUT" "$WORK"
OUT="$(cd "$OUT" && pwd)"; WORK="$(cd "$WORK" && pwd)"; ROOT="$WORK/rootfs"

PKGS=(ca-certificates curl git ripgrep bash procps tzdata less xz-utils unzip jq nano openssh-client iproute2 iputils-ping netbase)

cleanup_mounts() {
  for m in dev/pts dev sys proc; do
    mountpoint -q "$ROOT/$m" 2>/dev/null && umount -l "$ROOT/$m" 2>/dev/null || true
  done
}
trap cleanup_mounts EXIT

in_chroot() { # run a command in the rootfs with a clean environment
  # shellcheck disable=SC2086
  chroot "$ROOT" /usr/bin/env -i HOME=/root TERM=dumb LANG=C.UTF-8 DEBIAN_FRONTEND=noninteractive \
    PATH=/root/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
    $ROOTFS_BUILD_ENV "$@"
}

echo "::group::debootstrap $DEBIAN_SUITE ($ARCH)"
rm -rf "$ROOT"
debootstrap --variant=minbase --arch="$ARCH" --components=main \
  --include="$(IFS=,; echo "${PKGS[*]}")" "$DEBIAN_SUITE" "$ROOT" "$MIRROR"
echo "::endgroup::"

mount -t proc proc "$ROOT/proc"
mount --rbind /sys "$ROOT/sys" || true
mount --bind /dev "$ROOT/dev"
mount --bind /dev/pts "$ROOT/dev/pts" 2>/dev/null || true

# Network for the build only; the launcher writes its own resolv.conf at runtime.
cp -L /etc/resolv.conf "$ROOT/etc/resolv.conf"
if [ -n "$ROOTFS_BUILD_CA" ]; then
  cp "$ROOTFS_BUILD_CA" "$ROOT/usr/local/share/ca-certificates/build-extra-ca.crt"
  in_chroot update-ca-certificates
fi

printf '127.0.0.1 localhost\n::1 localhost ip6-localhost ip6-loopback\n' > "$ROOT/etc/hosts"
echo pocketagent > "$ROOT/etc/hostname"
# No service starts inside proot; make sure package scripts never try.
printf '#!/bin/sh\nexit 101\n' > "$ROOT/usr/sbin/policy-rc.d"; chmod 755 "$ROOT/usr/sbin/policy-rc.d"

install -m 755 "$HERE/install-hermes.sh" "$ROOT/tmp/install-hermes.sh"
echo "::group::install Hermes $HERMES_TAG"
in_chroot env HERMES_TAG="$HERMES_TAG" HERMES_COMMIT="$HERMES_COMMIT" bash /tmp/install-hermes.sh
echo "::endgroup::"

echo "::group::cleanup"
rm -f "$ROOT/usr/sbin/policy-rc.d"
in_chroot apt-get clean
rm -rf "$ROOT"/var/lib/apt/lists/* "$ROOT"/var/cache/apt/* "$ROOT"/var/cache/debconf/*-old \
       "$ROOT"/var/lib/dpkg/*-old "$ROOT"/var/log/apt "$ROOT"/var/log/dpkg.log "$ROOT"/var/log/alternatives.log \
       "$ROOT"/root/.cache "$ROOT"/root/.hermes/cache "$ROOT"/root/.bash_history "$ROOT"/tmp/* "$ROOT"/var/tmp/*
# Documentation, man pages and translations are dead weight on a phone; the
# Debian copyright files stay (license obligation).
find "$ROOT/usr/share/doc" -mindepth 2 ! -name copyright -delete 2>/dev/null || true
find "$ROOT/usr/share/doc" -mindepth 1 -type d -empty -delete 2>/dev/null || true
rm -rf "$ROOT"/usr/share/man/* "$ROOT"/usr/share/info/* "$ROOT"/usr/share/lintian "$ROOT"/usr/share/locale/*
if [ "$PRUNE" = 1 ]; then
  # Parts of the Hermes checkout the gateway never touches.
  for d in tests tests-js website apps contributors evals ui-tui nix docker; do
    rm -rf "$ROOT/root/.hermes/hermes-agent/$d"
  done
fi
if [ -n "$ROOTFS_BUILD_CA" ]; then
  rm -f "$ROOT/usr/local/share/ca-certificates/build-extra-ca.crt"
  in_chroot update-ca-certificates --fresh
fi
: > "$ROOT/etc/resolv.conf"
find "$ROOT/root" -name '*.log' -path '*logs*' -size +1M -delete 2>/dev/null || true
echo "::endgroup::"

cleanup_mounts
# Empty mountpoints must exist, nothing else may linger from the bind mounts.
mkdir -p "$ROOT/proc" "$ROOT/sys" "$ROOT/dev" "$ROOT/tmp"; chmod 1777 "$ROOT/tmp"

DEB_VERSION="$(cat "$ROOT/etc/debian_version")"
UNPACKED_BYTES="$(du -sb "$ROOT" | cut -f1)"
BUILD_ID="${GITHUB_RUN_ID:-local}-$(date -u +%Y%m%dT%H%M%SZ)"
INFO="$ROOT/etc/pocketagent-rootfs"
{
  echo "format=1"
  echo "arch=$ARCH"
  echo "debian=$DEB_VERSION"
  echo "hermes_tag=$HERMES_TAG"
  echo "hermes_commit=$HERMES_COMMIT"
  echo "build_id=$BUILD_ID"
} > "$INFO"

ARCHIVE="$OUT/rootfs-$ARCH.tar.xz"
echo "::group::pack $ARCHIVE"
# --hard-dereference: no hardlinks in the archive, so extraction on Android never
# depends on link(2) being permitted. --numeric-owner/--sort: deterministic.
tar --numeric-owner --sort=name --hard-dereference --one-file-system \
    --exclude=./proc/'*' --exclude=./sys/'*' --exclude=./dev/'*' \
    -C "$ROOT" -cf - . | xz -T0 -"$XZ_LEVEL" > "$ARCHIVE"
echo "::endgroup::"

ARCHIVE_BYTES="$(stat -c %s "$ARCHIVE")"
cp "$INFO" "$OUT/rootfs-$ARCH.info"
{
  echo "unpacked_bytes=$UNPACKED_BYTES"
  echo "archive_bytes=$ARCHIVE_BYTES"
  echo "archive_sha256=$(sha256sum "$ARCHIVE" | cut -d' ' -f1)"
} >> "$OUT/rootfs-$ARCH.info"
echo "rootfs unpacked: $(numfmt --to=iec "$UNPACKED_BYTES")  compressed: $(numfmt --to=iec "$ARCHIVE_BYTES")"
echo "files: $(find "$ROOT" -xdev | wc -l)"
echo "done: $ARCHIVE"
