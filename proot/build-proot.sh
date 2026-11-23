#!/bin/bash
# Build proot (Termux fork) for Android with the NDK, statically linked against
# talloc, loader kept as a separate file. Produces:
#   OUT/libproot.so          the proot executable   (named lib*.so so that the APK
#   OUT/libproot-loader.so   proot's tiny loader     packager extracts and keeps it
#                                                    executable on every device)
#   OUT/proot-build.info     provenance (versions, commits, hashes)
#
#   proot/build-proot.sh --abi arm64-v8a|x86_64 --out DIR
#
# Needs: Android NDK (ANDROID_NDK_HOME / ANDROID_NDK_ROOT or ANDROID_HOME/ndk/*),
# git, curl, make, tar, sha256sum. Nothing from the network is trusted blindly:
# proot is pinned by git commit (content addressed), talloc by SHA-256.
#
# Why build from source instead of using Termux's prebuilt .deb: the Termux
# binary is dynamically linked against libtalloc.so.2 and libandroid-shmem and
# has /data/data/com.termux paths compiled in; making that relocatable means
# patching ELF files. A static build + PROOT_LOADER is relocatable by design.
set -euo pipefail

PROOT_REPO="https://github.com/termux/proot"
PROOT_TAG="v5.1.107.102"
PROOT_COMMIT="52d65a4dc140ca554d241a1438ddd4f6b00c9813"
TALLOC_VERSION="2.4.3"
TALLOC_URL="https://download.samba.org/pub/talloc/talloc-${TALLOC_VERSION}.tar.gz"
TALLOC_SHA256="dc46c40b9f46bb34dd97fe41f548b0e8b247b77a918576733c528e83abd854dd"
API_LEVEL="${ANDROID_API_LEVEL:-26}"

ABI=""; OUT=""
while [ $# -gt 0 ]; do
  case "$1" in
    --abi) ABI="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
done
case "$ABI" in
  arm64-v8a) TRIPLE="aarch64-linux-android" ;;
  x86_64)    TRIPLE="x86_64-linux-android" ;;
  *) echo "--abi must be arm64-v8a or x86_64" >&2; exit 2 ;;
esac
[ -n "$OUT" ] || { echo "--out is required" >&2; exit 2; }
mkdir -p "$OUT"; OUT="$(cd "$OUT" && pwd)"

NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [ -z "$NDK" ] && [ -n "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]; then
  NDK="$(ls -d "${ANDROID_HOME:-$ANDROID_SDK_ROOT}"/ndk/* 2>/dev/null | sort -V | tail -n1 || true)"
fi
[ -d "$NDK" ] || { echo "Android NDK not found (set ANDROID_NDK_HOME)" >&2; exit 2; }
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
[ -d "$TOOLCHAIN" ] || { echo "NDK toolchain dir missing: $TOOLCHAIN" >&2; exit 2; }

export CC="$TOOLCHAIN/bin/${TRIPLE}${API_LEVEL}-clang"
export AR="$TOOLCHAIN/bin/llvm-ar"
export RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
export STRIP="$TOOLCHAIN/bin/llvm-strip"
export OBJCOPY="$TOOLCHAIN/bin/llvm-objcopy"
export OBJDUMP="$TOOLCHAIN/bin/llvm-objdump"
export READELF="$TOOLCHAIN/bin/llvm-readelf"
export LD="$TOOLCHAIN/bin/ld.lld"
export AS="$CC"
export CXX="$TOOLCHAIN/bin/${TRIPLE}${API_LEVEL}-clang++"
# proot's makefile calls `readelf`/`objcopy`/`strip` by those plain names.
SHIMS="$(mktemp -d)"; trap 'rm -rf "$SHIMS"' EXIT
for t in readelf objcopy objdump strip ar ranlib; do
  src="$TOOLCHAIN/bin/llvm-$t"; [ "$t" = ar ] || true
  ln -s "$src" "$SHIMS/$t"
done
ln -s "$TOOLCHAIN/bin/llvm-ar" "$SHIMS/${TRIPLE}-ar" || true
# talloc's configure probes pkg-config; a failing stub keeps it from finding host libs.
printf '#!/bin/sh\nexit 1\n' > "$SHIMS/pkg-config"; chmod +x "$SHIMS/pkg-config"
export PATH="$SHIMS:$PATH"

WORK="${PROOT_WORK:-$(mktemp -d)}"
mkdir -p "$WORK"; WORK="$(cd "$WORK" && pwd)"
echo "work dir: $WORK  ndk: $NDK  target: $TRIPLE$API_LEVEL"

# ---- sources -------------------------------------------------------------------
echo "::group::fetch sources"
if [ ! -d "$WORK/proot/.git" ]; then
  git clone --quiet "$PROOT_REPO" "$WORK/proot"
fi
git -C "$WORK/proot" checkout --quiet --detach "$PROOT_COMMIT"
ACTUAL="$(git -C "$WORK/proot" rev-parse HEAD)"
[ "$ACTUAL" = "$PROOT_COMMIT" ] || { echo "proot commit mismatch: $ACTUAL" >&2; exit 1; }
if [ "$(git -C "$WORK/proot" rev-parse "refs/tags/$PROOT_TAG^{commit}" 2>/dev/null || true)" != "$PROOT_COMMIT" ]; then
  echo "note: tag $PROOT_TAG no longer points at the pinned commit (tag moved); building the pinned commit"
fi
if [ ! -f "$WORK/talloc.tar.gz" ]; then curl -fsSL --retry 5 -o "$WORK/talloc.tar.gz" "$TALLOC_URL"; fi
echo "$TALLOC_SHA256  $WORK/talloc.tar.gz" | sha256sum -c -
rm -rf "$WORK/talloc-$TALLOC_VERSION"; tar -xzf "$WORK/talloc.tar.gz" -C "$WORK"
echo "::endgroup::"

# ---- talloc (static) -----------------------------------------------------------
echo "::group::talloc"
STATIC="$WORK/static"; rm -rf "$STATIC"; mkdir -p "$STATIC/include" "$STATIC/lib"
(
  cd "$WORK/talloc-$TALLOC_VERSION"
  cat > cross-answers.txt <<EOF
Checking uname sysname type: "Linux"
Checking uname machine type: "dontcare"
Checking uname release type: "dontcare"
Checking uname version type: "dontcare"
Checking simple C program: OK
rpath library support: OK
-Wl,--version-script support: FAIL
Checking getconf LFS_CFLAGS: OK
Checking for large file support without additional flags: OK
Checking for -D_FILE_OFFSET_BITS=64: OK
Checking for -D_LARGE_FILES: OK
Checking correct behavior of strtoll: OK
Checking for working strptime: OK
Checking for C99 vsnprintf: OK
Checking for HAVE_SHARED_MMAP: OK
Checking for HAVE_MREMAP: OK
Checking for HAVE_INCOHERENT_MMAP: OK
Checking for HAVE_SECURE_MKSTEMP: OK
Checking getconf large file support flags work: OK
Checking for HAVE_IFACE_IFCONF: FAIL
EOF
  ./configure build "--prefix=$WORK/talloc-prefix" --disable-rpath --disable-python \
    --cross-compile --cross-answers=cross-answers.txt > "$WORK/talloc-configure.log" 2>&1 \
    || { tail -n 40 "$WORK/talloc-configure.log"; exit 1; }
  "$AR" rcs "$STATIC/lib/libtalloc.a" bin/default/talloc*.o
  cp -f talloc.h "$STATIC/include/"
)
echo "::endgroup::"

# ---- proot ----------------------------------------------------------------------
echo "::group::proot"
(
  cd "$WORK/proot/src"
  make distclean >/dev/null 2>&1 || true
  # Loader stays a separate file (PROOT_UNBUNDLE_LOADER); the app points
  # PROOT_LOADER at the extracted libproot-loader.so, so the compiled-in path is
  # never used.
  export PROOT_UNBUNDLE_LOADER=/nonexistent/proot-loader
  export CPPFLAGS="-DARG_MAX=131072 -DVERSION=\"${PROOT_TAG#v}\" -I$STATIC/include"
  # extension/ashmem_memfd/ashmem_memfd.c lacks #include <string.h>; clang >= 16 turns the
  # implicit declarations into errors. (A global -include would break the freestanding loader.)
  grep -q '<string.h>' extension/ashmem_memfd/ashmem_memfd.c || \
    sed -i '0,/^#include/s//#include <string.h>\n#include/' extension/ashmem_memfd/ashmem_memfd.c
  export CFLAGS="-O2"
  # Bionic's static startup aborts on ARM64 when the executable's TLS segment is aligned below 64
  # ("executable's TLS segment is underaligned: alignment is 8 ... needs to be at least 64"; found by
  # the CI smoke test on an arm64 host). An over-aligned thread-local object in its own translation
  # unit raises the segment alignment; it is linked explicitly so it can never be dropped.
  cat > "$WORK/tlsalign.c" <<'TLS'
__thread char pocketagent_tls_alignment_pad[1] __attribute__((aligned(64), used));
TLS
  "$CC" -O2 -fno-emulated-tls -c "$WORK/tlsalign.c" -o "$WORK/tlsalign.o"   # API<29 defaults to emulated TLS, which would emit no TLS segment
  export LDFLAGS="-L$STATIC/lib -static -Wl,-z,max-page-size=16384 $WORK/tlsalign.o"
  make V=1 -j"$(nproc)" proot > "$WORK/proot-make.log" 2>&1 || { tail -n 60 "$WORK/proot-make.log"; exit 1; }
  cp -f proot "$OUT/libproot.so"
  cp -f loader/loader "$OUT/libproot-loader.so"
)
"$STRIP" "$OUT/libproot.so" "$OUT/libproot-loader.so"
# Guard against the Bionic startup abort described above.
if [ "$ABI" = arm64-v8a ]; then
  TLS_ALIGN="$("$READELF" -lW "$OUT/libproot.so" | awk '$1=="TLS"{print $NF}')"
  if [ -z "$TLS_ALIGN" ] || [ $((TLS_ALIGN)) -lt 64 ]; then
    echo "FATAL: libproot.so TLS segment alignment is '${TLS_ALIGN:-none}', Bionic needs >= 0x40" >&2; exit 1
  fi
  echo "TLS segment alignment: $TLS_ALIGN"
fi
chmod 755 "$OUT/libproot.so" "$OUT/libproot-loader.so"
echo "::endgroup::"

{
  echo "proot_repo=$PROOT_REPO"
  echo "proot_tag=$PROOT_TAG"
  echo "proot_commit=$PROOT_COMMIT"
  echo "talloc_version=$TALLOC_VERSION"
  echo "talloc_sha256=$TALLOC_SHA256"
  echo "abi=$ABI"
  echo "android_api=$API_LEVEL"
  echo "ndk=$(basename "$NDK")"
  echo "libproot_sha256=$(sha256sum "$OUT/libproot.so" | cut -d' ' -f1)"
  echo "libproot_loader_sha256=$(sha256sum "$OUT/libproot-loader.so" | cut -d' ' -f1)"
} > "$OUT/proot-build.info"
file "$OUT/libproot.so" "$OUT/libproot-loader.so" || true
ls -l "$OUT"
