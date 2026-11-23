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
export UV_COMPILE_BYTECODE=1
export PYTHONUNBUFFERED=1
INSTALL_DIR="$HERMES_HOME/hermes-agent"
INSTALLER=/tmp/hermes-install.sh

echo "::group::fetch installer (pinned commit $HERMES_COMMIT = $HERMES_TAG)"
mkdir -p "$HERMES_HOME"
curl -fsSL --retry 5 --retry-delay 3 \
  "https://raw.githubusercontent.com/NousResearch/hermes-agent/${HERMES_COMMIT}/scripts/install.sh" -o "$INSTALLER"
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

# Precompile bytecode for everything that runs. Under ptrace on a phone, importing ~8000 modules from source
# on first start is far slower than on a CI runner; the editable "workspace" copy that actually runs
# (and the checkout) ship without .pyc files. The archive extractor preserves mtimes, so the
# timestamp-based .pyc files stay valid after extraction.
echo "::group::precompile bytecode"
count_pyc() { echo "$1: py=$(find "$1" -name '*.py' | wc -l) pyc=$(find "$1" -name '*.pyc' | wc -l)"; }
for d in "$INSTALL_DIR" "$HERMES_HOME"/installs/*/environments/*/workspace "$HERMES_HOME"/installs/*/environments/*/venv/lib; do
  count_pyc "$d"
  "$PROJECT_PY" -m compileall -q -j0 "$d" || echo "warning: compileall reported errors in $d (non-fatal, usually py2-only test data)"
  count_pyc "$d"
done
echo "::endgroup::"

# Seal the install. The installer's checkout is a blobless clone with every branch (~300 MB of
# history we never use), and with a .git directory present Hermes's launcher treats the tree as a
# self-updating checkout: any ordinary CLI command first runs a "source-update completion", which
# on a phone would re-download Node, ffmpeg, pnpm (1 GB) and rebuild the PM runtime. Without
# .git, prepare_launch returns early and none of that happens. The tag and commit are recorded in
# /etc/pocketagent-rootfs and .hermes-bootstrap-complete.
echo "::group::seal install (drop .git and the bootstrap interpreter)"
rm -rf "$INSTALL_DIR/.git"
# The PM runtime venv was built against the bootstrap interpreter; both go (about 110 MB). If a lazy
# `pm.ensure_import` ever needs the runtime it is rebuilt on demand.
rm -rf /root/.local/share/uv/python "$HERMES_HOME"/installs/*/pm-runtime "$HERMES_HOME/cache"

hermes --version
hermes config check > /tmp/seal.txt 2>&1 || true
if grep -Eq "completing source-update|Preparing the isolated|dependencies failed|No interpreter found|Traceback" /tmp/seal.txt; then
  echo "FATAL: a cold hermes launch still tries to sync dependencies:" >&2; head -n 30 /tmp/seal.txt >&2; exit 1
fi
echo "cold launch is quiet"
rm -f /tmp/seal.txt
echo "::endgroup::"
