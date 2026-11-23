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

echo "::group::sanity: hermes --version"
export PATH="/root/.local/bin:$INSTALL_DIR/.hermes/bin:$PATH"
hermes --version
echo "::endgroup::"
