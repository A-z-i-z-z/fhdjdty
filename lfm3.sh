#!/usr/bin/env bash
# Runs Liquid AI LFM3 models with llama.cpp inside Termux on a Galaxy S24 Ultra
# (Snapdragon 8 Gen 3: CPU, Adreno 750 GPU via OpenCL, Hexagon v75 NPU).
#
#   bash lfm3.sh help
set -euo pipefail

LFM3_HOME="${LFM3_HOME:-$HOME/lfm3}"
LLAMA_DIR="$LFM3_HOME/llama.cpp"

# First llama.cpp builds with LFM3 support:
# b11938 = ggml-org/llama.cpp#31252 (LFM3-1.7B, LFM3-VL-2B)
# b11941 = ggml-org/llama.cpp#31257 (LFM3-Audio-2B, LFM3-Omni-2B)
MIN_BUILD_TEXT=11938
MIN_BUILD_AUDIO=11941

QUANT="${QUANT:-Q4_0}"
CTX="${CTX:-8192}"
THREADS="${THREADS:-6}"   # 1x X4 + 5x A720; the two A520 little cores only slow decoding down
THINK="${THINK:-auto}"    # auto | on | off
PORT="${PORT:-8080}"

die()  { echo "HATA: $*" >&2; exit 1; }
info() { echo ">> $*"; }

usage() {
  cat <<EOF
Kullanım: bash lfm3.sh <komut> [argümanlar]

Komutlar:
  install [ETIKET]          llama.cpp Snapdragon paketini kurar (varsayılan: en son release)
  devices                   llama.cpp'nin gördüğü cihazları listeler (CPU / GPUOpenCL / HTP0)
  chat   [MODEL] [BACKEND]  terminalde sohbet
  server [MODEL] [BACKEND]  web arayüzü + OpenAI uyumlu API: http://127.0.0.1:$PORT
  bench  [MODEL]            CPU, GPU ve NPU hızlarını sırayla ölçer
  tts    "METIN" [DOSYA]    LFM3-Omni ile Türkçe konuşma üretir (varsayılan: out.wav)

MODEL:   text (LFM3-1.7B, varsayılan) | vl (LFM3-VL-2B) | omni (LFM3-Omni-2B)
BACKEND: cpu (varsayılan) | gpu (Adreno 750) | npu (Hexagon)

Ortam değişkenleri:
  QUANT=$QUANT  CTX=$CTX  THREADS=$THREADS  THINK=$THINK  PORT=$PORT  LFM3_HOME=$LFM3_HOME
  tts için: VOICE=female|male  BACKEND=cpu|gpu|npu

Örnekler:
  bash lfm3.sh install
  bash lfm3.sh chat
  THINK=off bash lfm3.sh chat text gpu
  QUANT=Q8_0 bash lfm3.sh server vl
  bash lfm3.sh bench
EOF
}

# Sets MODEL_REPO; called directly (not in $(...)) so that die stops the script.
set_model() {
  case "$1" in
    text) MODEL_REPO="LiquidAI/LFM3-1.7B-GGUF" ;;
    vl)   MODEL_REPO="LiquidAI/LFM3-VL-2B-GGUF" ;;
    omni) MODEL_REPO="LiquidAI/LFM3-Omni-2B-GGUF" ;;
    *)    die "bilinmeyen model '$1' (text | vl | omni)" ;;
  esac
}

installed_build() {
  local target
  target="$(readlink "$LLAMA_DIR" 2>/dev/null)" || die "llama.cpp kurulu değil, önce: bash lfm3.sh install"
  target="${target##*/llama-b}"
  [[ "$target" =~ ^[0-9]+$ ]] || die "kurulu sürüm okunamadı: $LLAMA_DIR"
  echo "$target"
}

require_build_for() {
  local build
  build="$(installed_build)"
  if [ "$1" = "omni" ] && (( build < MIN_BUILD_AUDIO )); then
    die "kurulu b$build LFM3-Omni desteği içermiyor (b$MIN_BUILD_AUDIO+ gerekli): bash lfm3.sh install"
  fi
}

# Sets DEV_ARGS for llama-cli/llama-server/llama-tts and BENCH_DEV_ARGS for llama-bench.
set_device_args() {
  case "$1" in
    # --device none only covers the language model; the vision/audio projector needs its own flag
    cpu) DEV_ARGS=(--device none -ngl 0 --no-mmproj-offload); BENCH_DEV_ARGS=(-dev none -ngl 0) ;;
    gpu) DEV_ARGS=(--device GPUOpenCL -ngl 99); BENCH_DEV_ARGS=(-dev GPUOpenCL -ngl 99) ;;
    npu) DEV_ARGS=(--device HTP0 -ngl 99);      BENCH_DEV_ARGS=(-dev HTP0 -ngl 99) ;;
    *)   die "bilinmeyen backend '$1' (cpu | gpu | npu)" ;;
  esac
}

# Generation settings from the LFM3 model cards.
set_sampling_args() {
  case "$1" in
    text|vl)
      SAMPLING_ARGS=(--temp 0.1 --top-k 50 --top-p 1.0 --min-p 0.0 --repeat-penalty 1.1 --no-reasoning-preserve)
      if [ "$THINK" != "auto" ]; then
        SAMPLING_ARGS+=(-rea "$THINK")
      fi
      ;;
    omni)
      # non-reasoning model, greedy decoding for text output
      SAMPLING_ARGS=(--temp 0)
      ;;
  esac
}

run_llama() {
  local bin="$1"; shift
  [ -x "$LLAMA_DIR/bin/$bin" ] || die "$bin bulunamadı, önce: bash lfm3.sh install"
  mkdir -p "$LFM3_HOME/cl-cache"
  # /vendor/lib64 provides libOpenCL.so (Adreno) and libcdsprpc.so (Hexagon FastRPC);
  # the DSP loads libggml-htp-v75.so from ADSP_LIBRARY_PATH.
  LD_LIBRARY_PATH="$LLAMA_DIR/lib:/vendor/lib64:/system/vendor/lib64" \
  ADSP_LIBRARY_PATH="$LLAMA_DIR/lib" \
  GGML_OPENCL_KERNEL_CACHE_DIR="$LFM3_HOME/cl-cache" \
    "$LLAMA_DIR/bin/$bin" "$@"
}

cmd_install() {
  command -v curl >/dev/null || die "curl yok: pkg install curl"
  command -v tar  >/dev/null || die "tar yok: pkg install tar"

  local tag="${1:-}"
  if [ -z "$tag" ]; then
    tag="$(curl -fsSL https://api.github.com/repos/ggml-org/llama.cpp/releases/latest \
      | sed -n 's/.*"tag_name": *"\(b[0-9]*\)".*/\1/p' | head -n 1)"
    [ -n "$tag" ] || die "son release etiketi alınamadı; elle verin: bash lfm3.sh install b$MIN_BUILD_AUDIO"
  fi
  local n="${tag#b}"
  [[ "$n" =~ ^[0-9]+$ ]] || die "geçersiz etiket: $tag (ör. b$MIN_BUILD_AUDIO)"
  (( n >= MIN_BUILD_TEXT )) || die "$tag LFM3 desteği içermiyor (en az b$MIN_BUILD_TEXT). Daha yeni bir release çıkınca tekrar deneyin."
  (( n >= MIN_BUILD_AUDIO )) || info "uyarı: $tag sadece text/vl destekler; omni için b$MIN_BUILD_AUDIO+ gerekli"

  local asset="llama-$tag-bin-android-arm64-snapdragon.tar.gz"
  local url="https://github.com/ggml-org/llama.cpp/releases/download/$tag/$asset"
  mkdir -p "$LFM3_HOME"
  info "indiriliyor: $url"
  curl -fL --retry 3 -o "$LFM3_HOME/$asset" "$url" \
    || die "indirilemedi; bu release'te Snapdragon paketi olmayabilir, bir önceki/sonraki etiketi deneyin: $url"
  rm -rf "$LFM3_HOME/llama-$tag"
  tar -xzf "$LFM3_HOME/$asset" -C "$LFM3_HOME"
  rm -f "$LFM3_HOME/$asset"
  [ -x "$LFM3_HOME/llama-$tag/bin/llama-cli" ] || die "paket beklenen düzende değil: $LFM3_HOME/llama-$tag"
  ln -sfn "$LFM3_HOME/llama-$tag" "$LLAMA_DIR"
  info "kuruldu: $LLAMA_DIR -> llama-$tag"
  cmd_devices
}

cmd_devices() {
  run_llama llama-cli --version
  run_llama llama-cli --list-devices
}

cmd_chat() {
  local model="${1:-text}" backend="${2:-cpu}"
  set_model "$model"
  require_build_for "$model"
  set_device_args "$backend"
  set_sampling_args "$model"
  run_llama llama-cli -hf "$MODEL_REPO:$QUANT" "${DEV_ARGS[@]}" "${SAMPLING_ARGS[@]}" \
    -c "$CTX" -t "$THREADS"
}

cmd_server() {
  local model="${1:-text}" backend="${2:-cpu}"
  set_model "$model"
  require_build_for "$model"
  set_device_args "$backend"
  set_sampling_args "$model"
  info "tarayıcıda açın: http://127.0.0.1:$PORT"
  run_llama llama-server -hf "$MODEL_REPO:$QUANT" "${DEV_ARGS[@]}" "${SAMPLING_ARGS[@]}" \
    -c "$CTX" -t "$THREADS" --host 127.0.0.1 --port "$PORT"
}

cmd_bench() {
  local model="${1:-text}" backend
  set_model "$model"
  require_build_for "$model"
  for backend in cpu gpu npu; do
    set_device_args "$backend"
    info "=== $backend ==="
    run_llama llama-bench -hf "$MODEL_REPO:$QUANT" "${BENCH_DEV_ARGS[@]}" \
      -t "$THREADS" -p 512 -n 128 \
      || info "$backend başarısız oldu, sıradakine geçiliyor"
  done
}

cmd_tts() {
  local text="${1:-}" out="${2:-out.wav}"
  [ -n "$text" ] || die "konuşturulacak metni verin: bash lfm3.sh tts \"Merhaba\""
  set_model omni
  require_build_for omni
  set_device_args "${BACKEND:-cpu}"
  run_llama llama-tts -hf "$MODEL_REPO:$QUANT" "${DEV_ARGS[@]}" -p "$text" \
    --tts-lang tr --tts-voice "${VOICE:-female}" --output "$out" -t "$THREADS"
  info "yazıldı: $out"
}

main() {
  local cmd="${1:-help}"
  shift || true
  case "$cmd" in
    install) cmd_install "$@" ;;
    devices) cmd_devices ;;
    chat)    cmd_chat "$@" ;;
    server)  cmd_server "$@" ;;
    bench)   cmd_bench "$@" ;;
    tts)     cmd_tts "$@" ;;
    help|-h|--help) usage ;;
    *) usage; exit 1 ;;
  esac
}

main "$@"
