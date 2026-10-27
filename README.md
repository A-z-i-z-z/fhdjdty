# LFM3 → Galaxy S24 Ultra

Liquid AI'ın 27 Ekim 2026'da çıkardığı **LFM3** serisinin araştırma notları ve S24 Ultra'da
(Snapdragon 8 Gen 3) çalıştırma rehberi. Tek komutla kurulum ve çalıştırma için [`lfm3.sh`](lfm3.sh).

> **Kaynaklar hakkında not.** Teknik detaylar birincil kaynaklardan alındı: Liquid'in resmi
> dokümantasyon reposu ([Liquid4All/docs](https://github.com/Liquid4All/docs), 27 Ekim'de LFM3
> sayfalarıyla güncellendi) ve llama.cpp'nin LFM3 kaynak kodu
> ([#31252](https://github.com/ggml-org/llama.cpp/pull/31252),
> [#31257](https://github.com/ggml-org/llama.cpp/pull/31257)). Hugging Face'e bu oturumdan erişilemedi,
> bu yüzden benchmark sayıları ikincil haber kaynaklarından geliyor ve öyle işaretlendi.

---

## 1. LFM3 serisi

Hepsi aynı **LFM3-1.7B-Base** gövdesinden türetilmiş, ~2B sınıfı açık ağırlıklı modeller:

| Model | Parametre | Giriş → Çıkış | Bağlam | GGUF (telefon için) | Ne için |
|---|---|---|---|---|---|
| **LFM3-1.7B** | 1.73B | metin → metin | 256K | ✅ `LiquidAI/LFM3-1.7B-GGUF` | Lokal ajan, tool calling, yapılandırılmış çıktı |
| LFM3-1.7B-Base | 1.73B | metin → metin | – | – | Sadece ağır fine-tuning için, sohbet modeli değil |
| **LFM3-VL-2B** | 2B (1.73B LM + 164M görüntü kodlayıcı + 25M projektör) | metin+resim+**video** → metin | 128K | ✅ `LiquidAI/LFM3-VL-2B-GGUF` | Görsel soru-cevap, video, uzamsal akıl yürütme |
| LFM3-Audio-2B | 2B | metin/ses → metin/ses | 32K | ⏳ "yakında" | ASR, TTS, sesli sohbet |
| **LFM3-Omni-2B** | 2.17B | metin+resim+video+ses → metin/ses | 32K | ✅ `LiquidAI/LFM3-Omni-2B-GGUF` | Tek modelde ASR + VLM + LLM + TTS |
| d1-2B | 1.92B | metin+resim+video → tipli karar | 128K | ⏳ "yakında" | Sınıflandırma, çıkarım, yönlendirme (token üretmez) |
| d1-omni-2B | – | + ses → tipli karar | 128K | ⏳ "yakında" | d1-2B + ses girişi |

Ayrıca LFM3-1.7B için MLX (Apple), ONNX (WebGPU/CPU), FP8 ve NVFP4 (SGLang ile GPU sunucu) sürümleri var.
Bunların hiçbiri Android'de llama.cpp'den daha iyi bir yol sunmuyor.

### Mimari: "tri-hybrid"

llama.cpp'deki model grafiğinden (`src/models/lfm3.cpp`) doğrulandı:

- **24 katman:** 13 gated short convolution + 5 sliding-window attention (256 token pencere) + 6 global attention.
- Attention katmanlarında **QK-norm** ve head başına **sigmoid çıkış kapısı** (`wqkv_gate`).
- Global katmanlarda **kısmi RoPE**, SWA katmanlarında ayrı bir RoPE tabanı.
- SwiGLU FFN (ara boyut 7680), opsiyonel sandwich norm, 128K kelime haznesi.

**Telefon için neden önemli:** Bağlamla büyüyen KV cache sadece 6 global katmanda tutuluyor. Konvolüsyon
katmanlarının durumu sabit boyutlu, SWA katmanları da sadece son 256 token'ı tutuyor. Yani uzun bağlamda
bile bellek ve decode hızı, klasik bir transformer'a göre çok daha az kötüleşiyor.

### Sohbet şablonu ve özellikler

- ChatML benzeri `<|im_start|>` / `<|im_end|>`.
- **Akıl yürütme (thinking) varsayılan olarak açık** (`<think>…</think>`). Kapatmak için Transformers'ta
  `enable_thinking=False`, llama.cpp'de `-rea off` kullanın. Önceki turların düşünceleri, `preserve_thinking`
  verilmedikçe atılıyor.
- Tool calling XML formatında (`<tool_call><function=ad><parameter=x>…`). vLLM ve SGLang'de `qwen3_coder`
  parser'ıyla çalışıyor.
- **Ertelenmiş araçlar (defer loading / tool search):** Araç tanımları, bir arama aracı döndürene kadar
  prompt'a girmiyor. Çok araçlı ajanlarda bağlamdan tasarruf sağlıyor.
- Önerilen örnekleme: `temperature=0.1, top_k=50, repetition_penalty=1.1`. Matematik ve bilim için:
  `temperature=1.0, top_p=0.95, top_k=20, repetition_penalty=1.0`.
- Omni akıl yürütme yapmayan bir model; metin çıktısında greedy decoding öneriliyor.

### ⚠️ Türkçe

- **Metin dilleri** (1.7B, VL, Omni, d1): İngilizce, Arapça, Çince, Fransızca, Almanca, İtalyanca, Japonca,
  Korece, Portekizce, İspanyolca, Vietnamca, Tayca, Endonezce, Hintçe, Rusça, Lehçe. **Türkçe resmi listede yok.**
  Model Türkçe anlayıp yazabilir ama kalite garanti edilmiyor. Ciddi iş için İngilizce prompt daha güvenli.
- **Konuşma dilleri** (Audio-2B ve Omni-2B, 18 dil): **Türkçe var.** ASR için `Perform ASR in Turkish.`,
  TTS için `Perform TTS in Turkish using a female voice.` (veya `male`).

### Lisans

LFM Open License v1.0. Apache 2.0 tabanlı, tek fark bir ticari eşik: yıllık geliri **10 milyon $ altındaki**
şirketler için ticari kullanım ücretsiz, kişisel ve araştırma kullanımı serbest. Fine-tune edilmiş modeller
de bu lisansa tabi ama açık kaynak yapma zorunluluğu yok.

### Benchmark'lar (dikkatle okuyun)

Liquid'in iddiası: LFM3-1.7B, %35 daha az parametreyle LFM2.5-2.6B'yi geçiyor. VL-2B, 62 görsel benchmark
ortalamasında 71.4 alıyor (LFM2.5-VL-3B: 57.6), Omni-2B ise Daily-Omni, WorldSense ve AV-SpeakerBench'te
Qwen2.5-Omni-7B'yi geçiyor. İkincil kaynaklarda LFM3-1.7B için BFCL v4 71.4, IFEval 96.7, RULER-128K 65.6
gibi sayılar geçiyor. **Hepsi şirketin kendi ölçümü**, bağımsız değerlendirme henüz yok. Model kartı da
modelin küçük boyutu nedeniyle ajan tabanlı kodlama ve karmaşık çok adımlı akıl yürütme için önerilmediğini
açıkça söylüyor. Bilgi isteyen görevlerde retrieval ya da arama ile birlikte kullanılmalı.

---

## 2. S24 Ultra'da en iyi yol

**Donanım:** S24 Ultra her pazarda Snapdragon 8 Gen 3 for Galaxy kullanıyor:
CPU 1× Cortex-X4 + 5× A720 + 2× A520, GPU **Adreno 750**, NPU **Hexagon v75**, RAM 12 GB.

### Seçenekler

| Yol | Durum | Karar |
|---|---|---|
| **llama.cpp, Termux** (CPU / Adreno OpenCL / Hexagon NPU) | LFM3 desteği bugün upstream'e eklendi. llama.cpp release'lerinde hazır `android-arm64-snapdragon` paketi var (CPU + OpenCL + Hexagon). | ✅ **Önerilen** |
| Hazır Android uygulamaları (PocketPal, ChatterUI, SmolChat, Arm AI Chat…) | Hepsi gömülü llama.cpp kullanıyor. LFM3 için ≥ b11938 tabanlı bir güncelleme gerekiyor. | ⏳ Güncellenmelerini bekleyin |
| LEAP SDK (Liquid'in mobil SDK'sı) | **Deprecated.** Liquid artık doğrudan llama.cpp öneriyor. | ❌ |
| ONNX | WebGPU, CPU ve GPU hedefli. Android NPU yolu yok. | ❌ |
| MLX / vLLM / SGLang | Apple ya da GPU sunucu. | ❌ |

**Önerim:** Termux'ta llama.cpp'nin resmi Snapdragon paketi + `llama-server`'ın kendi web arayüzü.
Telefonun tarayıcısında `http://127.0.0.1:8080` açınca ChatGPT benzeri bir arayüz geliyor; resim yükleme
(VL) de orada çalışıyor.

**llama.cpp sürümü kritik.** Release etiketleri commit sırasını izliyor:

- **b11938+** → LFM3-1.7B ve LFM3-VL-2B
- **b11941+** → LFM3-Audio-2B ve LFM3-Omni-2B

Bunu yazdığım sırada yayınlanmış son etiket b11936'ydı; LFM3 commit'leri ondan sonra geldi. Her master
push'u otomatik release ürettiği için yeni etiketlerin kısa sürede çıkması beklenir.
Kontrol: <https://github.com/ggml-org/llama.cpp/releases>

### Backend seçimi: CPU, GPU, NPU

- **CPU, en güvenli seçenek.** `Q4_0` ile llama.cpp ağırlıkları yükleme sırasında Arm i8mm çekirdeklerine
  yeniden paketliyor. Liquid de Android için CPU'yu güvenli varsayılan olarak öneriyor. 6 thread kullanın;
  A520 küçük çekirdekleri decode'u yavaşlatıyor.
- **GPU (Adreno 750, OpenCL).** llama.cpp'nin OpenCL backend'i 8 Gen 3'ü resmi olarak destekliyor ve
  LFM'nin kullandığı `SSM_CONV` op'u bu backend'de var. Uzun prompt'larda (prefill) fark yaratabilir.
- **NPU (Hexagon v75).** Backend hâlâ "experimental" etiketli. LFM3'ün ihtiyaç duyduğu op'ların (SSM_CONV,
  sigmoid, frekans faktörlü RoPE) Hexagon çekirdeklerine sahip olduğunu kaynak kodda doğruladım. Matris
  çarpımında Q4_0, Q4_1, Q8_0, IQ4_NL, MXFP4 ve Q2_K–Q6_K tipleri destekleniyor; Q4_K_M de çalışır, ama
  llama.cpp'nin Snapdragon örneklerinin hepsi **Q4_0** kullanıyor. Prefill'de en büyük kazanç burada beklenir.

LFM3 için yayınlanmış bir telefon benchmark'ı bulamadım. Bu yüzden tahmin yerine **ölçün**:
`bash lfm3.sh bench` üç backend'i sırayla dener. Kabaca bir fikir vermek gerekirse: Liquid'in LFM2.5-1.2B
için S25 Ultra CPU'sunda bildirdiği ~70 tok/s decode değeri, parametre farkı ve 8 Gen 3'ün biraz daha yavaş
CPU'suna göre ölçeklenince LFM3-1.7B Q4_0 için **CPU'da ~30-45 tok/s** gibi bir rakam çıkıyor. Bu ölçülmüş
değil, sadece bir tahmin.

### Bellek tahmini

12 GB RAM'de bütün GGUF'lar rahatça sığıyor. Ağırlık boyutları bit/parametre üzerinden hesaplandı.
KV cache satırları ise parametre sayısından geriye doğru çıkardığım boyutlara dayanıyor (hidden 2048,
8 KV head × 64): 6 global katman × ~12 KB/token (f16).

| | Q4_0 | Q4_K_M | Q8_0 | F16 |
|---|---|---|---|---|
| LFM3-1.7B ağırlıkları | ~1.0 GB | ~1.05 GB | ~1.85 GB | ~3.5 GB |

| Bağlam (`-c`) | 8K | 32K | 128K | 256K |
|---|---|---|---|---|
| KV cache (f16) | ~0.1 GB | ~0.4 GB | ~1.5 GB | ~3 GB |

VL ve Omni bunlara ek olarak birkaç yüz MB'lık bir projektör (mmproj) dosyası yüklüyor. Hız için
**Q4_0**, kalite için **Q8_0** öneriyorum; özellikle Türkçe kullanımda Q8_0 denemeye değer, RAM fazlasıyla yetiyor.

---

## 3. Kurulum (adım adım)

1. **Termux'u** F-Droid'den veya GitHub release'inden kurun.
2. Termux'ta:

   ```bash
   pkg update && pkg install -y curl tar
   # repo herkese açıksa doğrudan indirin; değilse lfm3.sh'yi telefona kopyalayın
   curl -LO https://raw.githubusercontent.com/a-z-i-z-z/fhdjdty/ccr-32e370e8-tln28z/lfm3.sh
   bash lfm3.sh install          # llama.cpp Snapdragon paketini ~/lfm3 altına kurar
   bash lfm3.sh devices          # CPU, GPUOpenCL ve HTP0 görünüyor mu?
   ```

3. Çalıştırın (model ilk seferde Hugging Face'ten indirilir, ~1 GB):

   ```bash
   bash lfm3.sh chat                   # LFM3-1.7B, CPU, terminalde sohbet
   THINK=off bash lfm3.sh chat         # düşünme kapalı, daha hızlı ve kısa cevaplar
   bash lfm3.sh server                 # tarayıcıda http://127.0.0.1:8080
   bash lfm3.sh server vl gpu          # LFM3-VL-2B, Adreno GPU; web arayüzünden resim yükleyin
   bash lfm3.sh bench                  # CPU / GPU / NPU hız karşılaştırması
   QUANT=Q8_0 bash lfm3.sh chat text npu
   bash lfm3.sh tts "Merhaba, ben LFM3." merhaba.wav   # LFM3-Omni ile Türkçe TTS
   ```

4. Uzun oturumlarda Android'in Termux'u arka planda öldürmemesi için `termux-wake-lock` kullanın.

### Script'in yaptığı (elle yapmak isterseniz)

```bash
TAG=b11941   # veya daha yenisi
curl -LO https://github.com/ggml-org/llama.cpp/releases/download/$TAG/llama-$TAG-bin-android-arm64-snapdragon.tar.gz
tar -xzf llama-$TAG-bin-android-arm64-snapdragon.tar.gz && cd llama-$TAG

export LD_LIBRARY_PATH=$PWD/lib:/vendor/lib64      # libOpenCL.so ve libcdsprpc.so /vendor'da
export ADSP_LIBRARY_PATH=$PWD/lib                  # NPU, libggml-htp-v75.so'yu buradan yükler
export GGML_OPENCL_KERNEL_CACHE_DIR=$HOME/cl-cache # varsayılan /tmp Android'de yok

./bin/llama-cli -hf LiquidAI/LFM3-1.7B-GGUF:Q4_0 --device none -ngl 0 -t 6 -c 8192 \
  --temp 0.1 --top-k 50 --repeat-penalty 1.1 --no-reasoning-preserve
# GPU: --device GPUOpenCL -ngl 99     NPU: --device HTP0 -ngl 99
```

---

## 4. Model bazında kullanım

- **LFM3-1.7B (metin).** Ajan ve tool calling için `llama-server` OpenAI uyumlu API sunuyor
  (`/v1/chat/completions`, `tools`). Telefondaki başka uygulamalar `http://127.0.0.1:8080/v1` adresine
  bağlanabilir.
- **LFM3-VL-2B (görsel).** `llama-cli` içinde `/image <yol>` ile resim ekleyin, ya da web arayüzünden yükleyin.
  `-hf` vision projektörünü (mmproj) otomatik indiriyor.
- **LFM3-Omni-2B (her şey).** `bash lfm3.sh server omni` metin, resim ve ses girişini kabul ediyor; `/tts`
  uç noktası konuşma üretiyor (`{"input": "...", "lang": "tr", "voice": "female"}`). Ayrıntılı komutlar için
  [LFM3-Omni-2B-GGUF](https://huggingface.co/LiquidAI/LFM3-Omni-2B-GGUF) reposundaki talimatlara bakın.
- **LFM3-Audio-2B, d1-2B, d1-omni-2B.** GGUF henüz yayınlanmadı. Telefonda şimdilik Omni-2B, Audio'nun
  yerini tutuyor (ASR ve TTS dahil, Türkçe konuşma destekli).

## 5. Sorun giderme

| Belirti | Çözüm |
|---|---|
| `install` "LFM3 desteği içermiyor" diyor | Release henüz çıkmamış. Birkaç saat sonra tekrar deneyin ya da etiketi elle verin: `bash lfm3.sh install b119xx`. |
| `devices` GPU veya NPU göstermiyor | `/vendor/lib64/libOpenCL.so` ve `/vendor/lib64/libcdsprpc.so` var mı kontrol edin. Termux'ta NPU açılmazsa, aynı paketi **kablosuz ADB** üzerinden `adb shell` ile çalıştırın (llama.cpp'nin resmi yolu, `/data/local/tmp`, root gerekmez). PC'siz de yapılabilir: Termux'ta `pkg install android-tools`, sonra Geliştirici seçenekleri → Kablosuz hata ayıklama → `adb pair 127.0.0.1:<port>` → `adb connect` → `adb shell`. |
| Uygulama kapanıyor ya da donuyor | `CTX`'i düşürün (`CTX=4096`) ve arka plandaki uygulamaları kapatın. |
| Uzun kullanımda hız düşüyor | Isıl kısıtlama (thermal throttling). Kılıfı çıkarın, şarjdayken çalıştırmaktan kaçının. |
| Türkçe cevaplar zayıf | Türkçe resmi metin dili değil. Q8_0 deneyin, sistem prompt'unu İngilizce yazıp "Türkçe cevap ver" deyin. |

## Kaynaklar

- Liquid resmi dokümanları: [LFM3-1.7B](https://docs.liquid.ai/lfm/models/lfm3-1.7b),
  [LFM3-VL-2B](https://docs.liquid.ai/lfm/models/lfm3-vl-2b),
  [LFM3-Audio-2B](https://docs.liquid.ai/lfm/models/lfm3-audio-2b),
  [LFM3-Omni-2B](https://docs.liquid.ai/lfm/models/lfm3-omni-2b),
  [d1-2B](https://docs.liquid.ai/lfm/models/d1-2b),
  [LEAP SDK'dan geçiş](https://docs.liquid.ai/deployment/on-device/llama-cpp/migrating-from-leap-sdk),
  [Lisans](https://docs.liquid.ai/lfm/help/model-license). Kaynak: [Liquid4All/docs](https://github.com/Liquid4All/docs).
- llama.cpp: [LFM3 kodu](https://github.com/ggml-org/llama.cpp/blob/master/src/models/lfm3.cpp),
  [Snapdragon backend](https://github.com/ggml-org/llama.cpp/blob/master/docs/backend/snapdragon/README.md),
  [OpenCL/Adreno](https://github.com/ggml-org/llama.cpp/blob/master/docs/backend/OPENCL.md),
  [Android](https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md).
- Koleksiyon: <https://huggingface.co/collections/LiquidAI/lfm3>, duyuru:
  <https://huggingface.co/blog/LiquidAI/lfm3>.
- İkincil haberler (benchmark sayıları):
  [Unite.AI](https://www.unite.ai/liquid-ai-launches-lfm3-family-of-six-open-weight-2b-class-models/),
  [Superpower Daily](https://superpowerdaily.com/posts/liquidai-releases-six-lfm3-models-built-for-specialized-on-device-ai),
  [Crypto Briefing](https://cryptobriefing.com/liquid-ai-lfm3-local-ai-models/),
  [LM Studio](https://lmstudio.ai/models/lfm3).
