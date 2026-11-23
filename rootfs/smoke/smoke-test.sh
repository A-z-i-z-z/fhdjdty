#!/bin/bash
# Boots the rootfs under proot with the SAME launcher script the app uses
# (runtime/pocketagent-proot.sh) and checks that unmodified Hermes answers on its API.
#
#   rootfs/smoke/smoke-test.sh --rootfs DIR --proot PATH [--loader PATH] [--work DIR]
#
# Phases
#   1. proot basics (uname, id, hermes --version) - catches a broken proot/rootfs early.
#   2. For every provider fixture (the very files the app's config generator is unit-tested
#      against): boot `hermes gateway run`, assert /health, auth enforcement on /v1/models,
#      /v1/capabilities, loopback-only bind. One boot runs with PA_NO_SECCOMP=1.
#   3. End-to-end chat against a local mock LLM: non-streaming, SSE streaming, a tool call
#      (real terminal tool executed inside proot), and session continuity.
# Not covered (needs real Google credentials): an actual Gemini/Vertex call.
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
ROOTFS=""; PROOT=""; LOADER=""; WORK=""
while [ $# -gt 0 ]; do
  case "$1" in
    --rootfs) ROOTFS="$2"; shift 2 ;;
    --proot) PROOT="$2"; shift 2 ;;
    --loader) LOADER="$2"; shift 2 ;;
    --work) WORK="$2"; shift 2 ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
done
[ -d "$ROOTFS/usr" ] || { echo "--rootfs must be an extracted rootfs" >&2; exit 2; }
[ -x "$PROOT" ] || { echo "--proot must be an executable" >&2; exit 2; }
WORK="${WORK:-$(mktemp -d)}"; mkdir -p "$WORK"
ROOTFS="$(cd "$ROOTFS" && pwd)"; WORK="$(cd "$WORK" && pwd)"
LAUNCH="$REPO/runtime/pocketagent-proot.sh"
H="$ROOTFS/root/.hermes"
PORT=8642
KEY="smoke-api-key-0123456789abcdef0123456789"   # same as in the fixtures
MOCK_PORT=18080
GW_PID=""; MOCK_PID=""
FAILS=0
FAILURES=()

export PA_PROOT="$PROOT" PA_ROOTFS="$ROOTFS" PA_STATE="$WORK/state" PA_DNS="127.0.0.1"
[ -n "$LOADER" ] && export PA_LOADER="$LOADER"
mkdir -p "$PA_STATE"

pass() { echo "  PASS: $*"; }
fail() { echo "  FAIL: $*"; FAILS=$((FAILS+1)); FAILURES+=("$*"); }
check() { # description command...
  local d="$1"; shift
  if "$@" >/dev/null 2>&1; then pass "$d"; else fail "$d"; fi
}

# proot ignores SIGTERM and --kill-on-exit does not take the guest down when proot is killed, so
# (exactly like the app does) find guest processes through the PA_IN_PROOT=1 marker the launcher
# puts in their environment and signal them directly.
guest_pids() {
  local e p
  for e in /proc/[0-9]*/environ; do
    if tr '\0' '\n' 2>/dev/null < "$e" | grep -qx 'PA_IN_PROOT=1'; then p="${e#/proc/}"; echo "${p%%/*}"; fi
  done
}

stop_gateway() {
  local p
  for p in $(guest_pids); do kill -TERM "$p" 2>/dev/null; done
  if [ -n "$GW_PID" ]; then
    for _ in $(seq 1 30); do kill -0 "$GW_PID" 2>/dev/null || break; sleep 0.5; done
  fi
  for p in $(guest_pids); do kill -KILL "$p" 2>/dev/null; done
  [ -n "$GW_PID" ] && kill -KILL "$GW_PID" 2>/dev/null
  GW_PID=""
  for _ in $(seq 1 40); do
    curl -s -m 1 "http://127.0.0.1:$PORT/health" >/dev/null 2>&1 || { sleep 1; return 0; }
    sleep 0.5
  done
  echo "  WARNING: something still answers on port $PORT after stop"
  return 0
}

cleanup() {
  stop_gateway
  [ -n "$MOCK_PID" ] && kill "$MOCK_PID" 2>/dev/null
}
trap cleanup EXIT

start_gateway() { # logfile
  rm -f "$H/gateway.pid" "$H/gateway.sock" "$H"/gateway*.lock 2>/dev/null
  sh "$LAUNCH" -- hermes gateway run --replace -v > "$1" 2>&1 &
  GW_PID=$!
}

wait_health() { # seconds
  local t0=$SECONDS
  while [ $((SECONDS - t0)) -lt "$1" ]; do
    if ! kill -0 "$GW_PID" 2>/dev/null; then echo "  gateway process exited early"; return 1; fi
    if curl -s -m 2 "http://127.0.0.1:$PORT/health" 2>/dev/null | grep -q '"status": *"ok"'; then
      echo "  healthy after $((SECONDS - t0))s"; return 0
    fi
    sleep 1
  done
  return 1
}

http_code() { curl -s -o /dev/null -m 20 -w '%{http_code}' "$@"; }

dump_log() { echo "----- tail of $1 -----"; tail -n 60 "$1" 2>/dev/null | cut -c1-300; echo "-----"; }

install_fixture() { # name
  local f="$HERE/fixtures/$1"
  cp "$f.env" "$H/.env"; chmod 600 "$H/.env"
  cp "$f.yaml" "$H/config.yaml"
  mkdir -p "$H/secrets"
  if [ "$1" = vertex-sa ]; then echo '{"type":"service_account","project_id":"smoke-project"}' > "$H/secrets/vertex-sa.json"; fi
}

# ---------------------------------------------------------------------------------------------
echo "== phase 1: proot basics"
out="$(sh "$LAUNCH" -- sh -c 'uname -r; id -u; cat /etc/pocketagent-rootfs; hermes --version' 2>&1)"; rc=$?
echo "$out" | sed 's/^/  | /'
[ $rc -eq 0 ] && pass "launcher ran a command (rc=0)" || fail "launcher command failed rc=$rc"
echo "$out" | grep -q "Hermes Agent v0.23.3" && pass "hermes --version works" || fail "hermes --version"
echo "$out" | grep -q "pocketagent" && pass "fake kernel release applied (-k)" || fail "kernel release"
head -n 2 <<<"$out" | tail -n 1 | grep -qx 0 && pass "fake root (id -u = 0)" || fail "id -u"
imp="$(sh "$LAUNCH" -- sh -c 'for py in /root/.hermes/installs/*/environments/*/venv/bin/python; do "$py" -c "import aiohttp, google.auth, google.oauth2.service_account, google.oauth2.credentials; print(\"IMPORTS-OK\")"; done' 2>&1)"
echo "$imp" | grep -q IMPORTS-OK && pass "aiohttp + google-auth importable in the Hermes venv (API server and Vertex/ADC need them)" || { fail "venv imports: $imp"; }
[ -s "$PA_STATE/last-launch.txt" ] && pass "launcher wrote last-launch.txt" || fail "last-launch.txt missing"

# ---------------------------------------------------------------------------------------------
for fx in studio express vertex-sa vertex-user; do
  echo "== phase 2: boot gateway with fixture '$fx'"
  stop_gateway
  install_fixture "$fx"
  if [ "$fx" = studio ]; then export PA_NO_SECCOMP=1; echo "  (PA_NO_SECCOMP=1 for this boot)"; else unset PA_NO_SECCOMP; fi
  LOG="$WORK/gateway-$fx.log"
  start_gateway "$LOG"
  if wait_health 240; then
    pass "[$fx] /health"
    body="$(curl -s -m 10 "http://127.0.0.1:$PORT/health")"
    echo "$body" | grep -q "hermes-agent" && pass "[$fx] /health names platform hermes-agent" || fail "[$fx] health body: $body"
    [ "$(http_code "http://127.0.0.1:$PORT/v1/models")" = 401 ] && pass "[$fx] /v1/models without key -> 401" || fail "[$fx] no-key models not 401"
    [ "$(http_code -H 'Authorization: Bearer wrong-key' "http://127.0.0.1:$PORT/v1/models")" = 401 ] && pass "[$fx] wrong key -> 401" || fail "[$fx] wrong-key models not 401"
    m="$(curl -s -m 10 -H "Authorization: Bearer $KEY" "http://127.0.0.1:$PORT/v1/models")"
    echo "$m" | grep -q '"id": *"hermes-agent"' && pass "[$fx] /v1/models with key lists hermes-agent" || fail "[$fx] models body: $m"
    c="$(curl -s -m 10 -H "Authorization: Bearer $KEY" "http://127.0.0.1:$PORT/v1/capabilities")"
    echo "$c" | grep -q '"required": *true' && pass "[$fx] capabilities: auth required" || fail "[$fx] capabilities: $c"
    if command -v ss >/dev/null 2>&1; then
      if ss -ltnH 2>/dev/null | awk '{print $4}' | grep -E ":$PORT\$" | grep -vq '^127\.0\.0\.1:'; then fail "[$fx] API port is bound beyond loopback: $(ss -ltnH | grep ":$PORT")"; else pass "[$fx] API port bound to loopback only"; fi
    fi
    if grep -Eqi "Traceback|ModuleNotFoundError|ImportError" "$LOG"; then fail "[$fx] Python errors in gateway log"; dump_log "$LOG"; else pass "[$fx] no Python tracebacks in gateway log"; fi
  else
    fail "[$fx] gateway did not become healthy"
    dump_log "$LOG"
  fi
done
unset PA_NO_SECCOMP

# ---------------------------------------------------------------------------------------------
echo "== phase 3: end-to-end chat against a local mock LLM"
stop_gateway
cat > "$H/config.yaml" <<EOF
model:
  default: "mock-model"
  provider: custom
  base_url: http://127.0.0.1:$MOCK_PORT/v1
  api_key: mock-key
EOF
cp "$HERE/fixtures/studio.env" "$H/.env"; sed -i '/^GEMINI_/d' "$H/.env"; chmod 600 "$H/.env"
MOCKLOG="$WORK/mock.log"; : > "$MOCKLOG"
python3 "$HERE/mock_llm.py" --port "$MOCK_PORT" --log "$MOCKLOG" > "$WORK/mock.out" 2>&1 &
MOCK_PID=$!
sleep 1
LOG="$WORK/gateway-chat.log"
start_gateway "$LOG"
if wait_health 240; then
  AUTH=(-H "Authorization: Bearer $KEY" -H "Content-Type: application/json")
  U="http://127.0.0.1:$PORT/v1/chat/completions"
  r="$(curl -s -m 120 "${AUTH[@]}" -H "X-Hermes-Session-Id: smoke-ns" -d '{"model":"hermes-agent","messages":[{"role":"user","content":"say hello"}]}' "$U")"
  echo "$r" | grep -q "mock-reply: say hello" && pass "non-streaming chat completion" || { fail "non-streaming chat: $(echo "$r" | cut -c1-300)"; dump_log "$LOG"; }

  s="$(curl -s -N -m 120 "${AUTH[@]}" -H "X-Hermes-Session-Id: smoke-stream" -d '{"model":"hermes-agent","stream":true,"messages":[{"role":"user","content":"stream please"}]}' "$U")"
  echo "$s" | grep -q "mock-reply" && echo "$s" | grep -q '^data: \[DONE\]' && pass "SSE streaming chat (content + [DONE])" || fail "streaming chat: $(echo "$s" | cut -c1-300)"

  t="$(curl -s -N -m 180 "${AUTH[@]}" -H "X-Hermes-Session-Id: smoke-tool" -d '{"model":"hermes-agent","stream":true,"messages":[{"role":"user","content":"please RUN_TOOL now"}]}' "$U")"
  echo "$t" | grep -q "event: hermes.tool.progress" && pass "stream carries hermes.tool.progress events" || fail "no tool progress event"
  echo "$t" | grep -q "hello-from-tool" && pass "terminal tool really ran inside proot (output came back)" || { fail "tool output missing: $(echo "$t" | cut -c1-400)"; dump_log "$LOG"; }

  # session continuity: second turn with the same session id must reach the LLM with prior turns.
  curl -s -m 120 "${AUTH[@]}" -H "X-Hermes-Session-Id: smoke-cont" -d '{"model":"hermes-agent","messages":[{"role":"user","content":"first turn"}]}' "$U" >/dev/null
  curl -s -m 120 "${AUTH[@]}" -H "X-Hermes-Session-Id: smoke-cont" -d '{"model":"hermes-agent","messages":[{"role":"user","content":"second turn"}]}' "$U" >/dev/null
  last_roles="$(grep '"POST"' "$MOCKLOG" | tail -n 1)"
  n="$(echo "$last_roles" | sed -n 's/.*"n_msgs": *\([0-9]*\).*/\1/p')"
  if [ -n "$n" ] && [ "$n" -ge 3 ]; then pass "session continuity: 2nd turn carried history ($n messages sent to the LLM)"; else fail "session continuity: LLM saw only ${n:-?} messages for turn 2 ($last_roles)"; fi
else
  fail "chat phase: gateway did not become healthy"
  dump_log "$LOG"
fi

echo
if [ $FAILS -eq 0 ]; then echo "SMOKE TEST PASSED"; exit 0; fi
echo "SMOKE TEST FAILED ($FAILS failures):"; printf '  - %s\n' "${FAILURES[@]}"
exit 1
