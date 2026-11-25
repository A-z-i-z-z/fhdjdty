#!/bin/sh
# practice-start.sh PORT [COMMAND [ARGS...]]
#
# Runs INSIDE the Linux guest (shipped as the app asset practice-start.sh and copied to /opt/agiri/practice/start.sh; the CI smoke
# test copies it the same way). It starts the practice model (practice_model.py, a scripted OpenAI-compatible server that lives
# next to this file) on 127.0.0.1:PORT, waits until it listens, and then `exec`s COMMAND, so the command keeps this process id.
# Default COMMAND: hermes gateway run --replace -v
#
# The practice model is started with --watch-parent: it ends by itself when the process that started it ends (the guest sweep of
# the launcher also kills it, it carries the same PA_IN_PROOT marker as everything else in the guest).
#
# Exit codes:  70 no python in the guest, or practice_model.py is missing
#              71 the model died or never listened within 10 seconds (the port is probably taken)
#              64 bad call (PORT is not a number from 1 to 65535)
#
# POSIX sh only: the Android launcher runs scripts with mksh, CI with dash.
set -u

PORT=${1:-}
case "$PORT" in
  ''|*[!0-9]*) echo "practice-start: PORT must be a number from 1 to 65535" >&2; exit 64 ;;
esac
if [ "$PORT" -lt 1 ] || [ "$PORT" -gt 65535 ]; then
  echo "practice-start: PORT must be a number from 1 to 65535" >&2
  exit 64
fi
shift
if [ "$#" -eq 0 ]; then
  set -- hermes gateway run --replace -v
fi

HERE=$(cd "$(dirname "$0")" && pwd) || exit 70
cd "$HERE" || exit 70

if [ ! -f "$HERE/practice_model.py" ]; then
  echo "practice-start: practice_model.py is missing in $HERE" >&2
  exit 70
fi

# The guest has no python on PATH: take the one Hermes brought. First match wins.
HH=${HERMES_HOME:-/root/.hermes}
PY=""
for cand in "$HH"/tools/python-*/bin/python3 "$HH"/installs/*/environments/*/venv/bin/python \
  /root/.hermes/tools/python-*/bin/python3 /root/.hermes/installs/*/environments/*/venv/bin/python; do
  if [ -x "$cand" ]; then PY=$cand; break; fi
done
if [ -z "$PY" ]; then
  PY=$(command -v python3 2>/dev/null) || PY=""
fi
if [ -z "$PY" ]; then
  echo "practice-start: no python found in the guest" >&2
  exit 70
fi

mkdir -p media
# One log per launch; the request log (requests.jsonl) is appended to by the model itself and cut by it past 1 MB.
: > practice.log
"$PY" -u ./practice_model.py --port "$PORT" --log requests.jsonl --media-dir media --watch-parent >> practice.log 2>&1 &
MOCK=$!

# The model prints "listening on PORT" once the socket is bound, and "cannot listen" and exits when the port is taken.
tries=0
while :; do
  if grep -q "listening on $PORT$" practice.log 2>/dev/null; then break; fi
  if ! kill -0 "$MOCK" 2>/dev/null; then
    echo "practice-start: the practice model stopped at once (is port $PORT taken?)" >&2
    tail -n 5 practice.log >&2
    exit 71
  fi
  tries=$((tries + 1))
  if [ "$tries" -gt 50 ]; then
    echo "practice-start: the practice model did not listen on port $PORT within 10 seconds" >&2
    kill "$MOCK" 2>/dev/null
    exit 71
  fi
  sleep 0.2
done

exec "$@"
