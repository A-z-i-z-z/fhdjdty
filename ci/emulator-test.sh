#!/usr/bin/env bash
# Drives the debug APK on an emulator: loads a real model, runs agent chats, takes screenshots.
# usage: emulator-test.sh <app-debug.apk> <model.gguf> <out-dir>
set -uo pipefail
APK=$1
MODEL=$2
OUT=$3
PKG=app.yerel.debug
ACT=$PKG/app.yerel.MainActivity
mkdir -p "$OUT"
FAILS=0

log() { echo "[$(date +%H:%M:%S)] $*"; }
shot() { adb exec-out screencap -p > "$OUT/$1.png"; log "screenshot $1"; }
start() { adb shell am start -W -n "$ACT" "$@" > /dev/null; }

replies() { adb logcat -d -s YerelTest:I | grep -c REPLY_DONE; }

wait_reply() { # $1 = label, $2 = reply count before the prompt; waits for a new REPLY_DONE
  local deadline=$((SECONDS + 900))
  while (( SECONDS < deadline )); do
    local line
    if (( $(replies) > $2 )); then
      line=$(adb logcat -d -s YerelTest:I | grep REPLY_DONE | tail -1)
      log "$1: ${line#*REPLY_DONE }"
      echo "$1: ${line#*REPLY_DONE }" >> "$OUT/replies.txt"
      [[ "$line" != *"REPLY_DONE ERROR:"* ]]
      return
    fi
    if ! adb shell pidof "$PKG" > /dev/null; then
      log "$1: app process died"
      return 1
    fi
    sleep 5
  done
  log "$1: timed out"
  return 1
}

prompt() { # $1 label, $2 text, extra am args after
  local label=$1 text=$2
  shift 2
  local before
  before=$(replies)
  adb shell "am start -n $ACT $* --es test_prompt \"$text\"" > /dev/null
  sleep 3
  shot "$label-start"
  if wait_reply "$label" "$before"; then sleep 2; shot "$label"; else FAILS=$((FAILS + 1)); shot "$label-failed"; fi
}

adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
unzip -l "$APK" | grep "\.so$" > "$OUT/apk-native-libs.txt" || true
adb logcat -G 16M || true
adb install -r -g "$APK" || exit 1
adb shell cmd uimode night no

start
sleep 5
shot 01-welcome
adb shell dumpsys meminfo "$PKG" > "$OUT/meminfo-idle.txt" || true

log "copying model into the app"
adb push "$MODEL" /data/local/tmp/model.gguf > /dev/null
adb shell run-as "$PKG" sh -c "'mkdir -p files/models && cp /data/local/tmp/model.gguf files/models/test-model.gguf'" ||
  adb shell "cat /data/local/tmp/model.gguf | run-as $PKG sh -c 'cat > files/models/test-model.gguf'"
adb shell run-as "$PKG" ls -la files/models
adb shell am force-stop "$PKG"
start
sleep 6
shot 02-model-found

prompt 03-tool-calc "17 ile 23'ün çarpımı kaç? Hesap makinesi aracını kullan." --ez test_thinking true --ez test_tools true --ei test_max_tokens 700
prompt 04-markdown "Türkiye'nin en kalabalık 3 şehrini nüfuslarıyla birlikte madde madde yaz." --ez test_thinking false --ez test_tools false --ei test_max_tokens 300
prompt 05-memory "Benim adım Deniz, bunu hafızana kaydet." --ez test_thinking false --ez test_tools true --ei test_max_tokens 300

start --es test_screen MODELS
sleep 3
shot 06-models
# a Hugging Face link opened from the browser lists the repo's GGUF files
adb shell am start -n "$ACT" -a android.intent.action.VIEW -d "https://huggingface.co/LiquidAI/LFM3-1.7B-GGUF" > /dev/null
sleep 12
shot 06b-hf-link
start --es test_screen SETTINGS
sleep 3
shot 07-settings
start --es test_screen MEMORY
sleep 3
shot 08-memory

adb shell cmd uimode night yes
sleep 2
start --es test_screen CHAT
sleep 3
shot 09-dark-chat
adb shell cmd uimode night no

adb logcat -d -v time -s YerelTest:I LlamaEngine:* yerel-llama:* yerel-engine:* Yerel:* AndroidRuntime:E DEBUG:* > "$OUT/logcat.txt" || true
adb shell dumpsys package "$PKG" | grep -E "legacyNativeLibraryDir|nativeLibraryDir|primaryCpuAbi" > "$OUT/package.txt" || true
adb shell dumpsys meminfo "$PKG" > "$OUT/meminfo.txt" || true
log "failures: $FAILS"
echo "$FAILS" > "$OUT/failures"
exit 0
