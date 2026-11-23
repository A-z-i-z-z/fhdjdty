#!/bin/bash
# Runs INSIDE the rootfs (as root, in a chroot) during the rootfs build.
# Reproduces what the official installer does, pinned and non-interactive, but
# lean: no Node/pnpm/ffmpeg/browser/TUI/dashboard build. Only the pieces the
# gateway + API server need.
#
# What the official installer (scripts/install.sh at the pinned tag) does:
#   prerequisites -> repository (git clone of the tag) -> venv (acquire pinned uv
#   and Python 3.14) -> python-deps (`python -m pm.cli install`, which by default
#   also pulls node, npm, pnpm, ffmpeg ... = ~1 GB we do not want) -> products
#   (TUI/web builds, needs node) -> config -> complete.
# We run the same stages, except python-deps is called with an explicit tool list
# (python, ripgrep) and the extras `all` + `vertex`, and products is replaced by
# publishing the launchers directly.
set -euo pipefail

: "${HERMES_TAG:?HERMES_TAG is required}"
: "${HERMES_COMMIT:?HERMES_COMMIT is required (expected full SHA of the tag)}"

export HOME=/root
export HERMES_HOME=/root/.hermes
export UV_NO_CONFIG=1
export PYTHONUNBUFFERED=1
INSTALL_DIR="$HERMES_HOME/hermes-agent"
INSTALLER=/tmp/hermes-install.sh

echo "::group::fetch installer ($HERMES_TAG)"
mkdir -p "$HERMES_HOME"
curl -fsSL --retry 5 --retry-delay 3 \
  "https://raw.githubusercontent.com/NousResearch/hermes-agent/${HERMES_TAG}/scripts/install.sh" -o "$INSTALLER"
echo "::endgroup::"

run_stage() {
  echo "::group::installer stage: $1"
  bash "$INSTALLER" --branch "$HERMES_TAG" --non-interactive --skip-browser --skip-computer-use --verbose --stage "$1"
  echo "::endgroup::"
}

run_stage prerequisites
run_stage repository

ACTUAL_COMMIT="$(git -C "$INSTALL_DIR" rev-parse HEAD)"
if [ "$ACTUAL_COMMIT" != "$HERMES_COMMIT" ]; then
  echo "FATAL: $HERMES_TAG resolved to $ACTUAL_COMMIT but the pinned commit is $HERMES_COMMIT" >&2
  exit 1
fi
echo "Hermes $HERMES_TAG @ $ACTUAL_COMMIT"

run_stage venv   # acquires the pinned uv and a managed Python (bootstrap)

UV="$(ls -d "$HERMES_HOME"/tools/uv-*/uv | head -n1)"
BOOT_PY="$("$UV" python find --managed-python 3.14)"
echo "bootstrap python: $BOOT_PY"

echo "::group::pm install (lean: python + ripgrep, extras all+vertex)"
(cd "$INSTALL_DIR" && "$BOOT_PY" -m pm.cli install python ripgrep --extra all --extra vertex)
echo "::endgroup::"

echo "::group::publish launchers + install stamp"
PROJECT_PY="$(cd "$INSTALL_DIR" && "$BOOT_PY" -c 'from pathlib import Path; from pm.environments import project_python; print(project_python(Path(".").resolve()))')"
echo "project python: $PROJECT_PY"
(cd "$INSTALL_DIR" && PYTHONPATH="$INSTALL_DIR" "$PROJECT_PY" -B - <<'PY'
from pathlib import Path
from hermes_cli.venv_sync import publish_launchers
from hermes_cli.source_stamp import write_source_stamp
root = Path(".").resolve()
publish_launchers(root)
try:
    write_source_stamp(root)
except Exception as exc:  # stamp is informational for a sideloaded install
    print("warning: install stamp not written:", exc)
PY
)
echo "::endgroup::"

run_stage config
run_stage complete

export PATH="/root/.local/bin:$INSTALL_DIR/.hermes/bin:$PATH"
echo "::group::sanity: hermes --version"
hermes --version
echo "::endgroup::"

# Hermes keeps an isolated "PM runtime" venv. The installer prepares it against the
# bootstrap interpreter, but the first real CLI launch wants it built against the
# managed (store) Python and, when it differs, re-downloads it from PyPI. On a phone
# that would mean a surprise network sync on first use, so do that launch here, drop
# the bootstrap interpreter, and prove that a cold launch is then quiet.
echo "::group::warm-up: PM runtime for the store Python"
hermes config check > /tmp/warm1.txt 2>&1 || true
tail -n 3 /tmp/warm1.txt || true
rm -rf /root/.local/share/uv/python
for d in "$HERMES_HOME"/installs/*/pm-runtime; do
  [ -f "$d/selected.json" ] || continue
  keep="$("$PROJECT_PY" -c 'import json,sys; print(json.load(open(sys.argv[1]))["generation"])' "$d/selected.json")"
  for g in "$d"/generations/*; do
    [ "generations/$(basename "$g")" = "$keep" ] || rm -rf "$g"
  done
done
hermes config check > /tmp/warm2.txt 2>&1 || true
if grep -Eq "completing source-update|Preparing the isolated|dependencies failed|No interpreter found" /tmp/warm2.txt; then
  echo "FATAL: a cold hermes launch still tries to sync dependencies:" >&2
  head -n 30 /tmp/warm2.txt >&2
  exit 1
fi
echo "cold launch is quiet"

# The installer's checkout is a blobless clone with every branch: ~300 MB of history we never
# use. Without a .git directory Hermes's launcher skips its self-update/dependency-sync logic
# entirely (prepare_launch returns early), which is exactly what a sealed phone install wants.
# The tag + commit are recorded in /etc/pocketagent-rootfs and .hermes-bootstrap-complete.
rm -rf "$INSTALL_DIR/.git"
hermes config check > /tmp/warm3.txt 2>&1 || true
if grep -Eq "completing source-update|Preparing the isolated|dependencies failed|No interpreter found|Traceback" /tmp/warm3.txt; then
  echo "FATAL: hermes misbehaves without .git:" >&2; head -n 30 /tmp/warm3.txt >&2; exit 1
fi
hermes --version
rm -f /tmp/warm1.txt /tmp/warm2.txt /tmp/warm3.txt
echo "::endgroup::"
