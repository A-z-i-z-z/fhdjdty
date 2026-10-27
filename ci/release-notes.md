## Yerel — telefonunda çalışan yapay zekâ asistanı

**Kurulum:** Aşağıdaki `Yerel-…-arm64.apk` dosyasını telefona indirip açın (gerekirse "Bilinmeyen kaynaklardan yükleme" iznini verin).
Galaxy S24 Ultra ve tüm 64-bit ARM Android 9+ telefonlarda çalışır.

- İlk açılışta **Hızlı başlangıç: Qwen3 0.6B** düğmesine basın veya *Modeller* ekranından bir model indirin.
- Hugging Face bağlantısı yapıştırabilir, tarayıcıdan "Yerel ile aç" diyebilir veya telefondaki bir `.gguf` dosyasını seçebilirsiniz.
- `screen-*.png` dosyaları bu derlemenin emülatörde gerçek bir modelle çekilmiş ekran görüntüleridir.

### Yeni
- **GPU / NPU:** Ayarlar → *Donanım ve hız* → Hesaplama birimi: CPU, GPU (Adreno, OpenCL) veya NPU (Hexagon, deneysel). En iyi sonuç Q4_0 / Q8_0 dosyalarla.
- **Hızlandırma (spekülatif çözümleme):** N-gram (tekrar eden metin ve kod), MTP (modelin kendi tahmin katmanı, ör. Qwen3.5) ve taslak model (ör. Qwen2.5 Coder 3B + 0.5B). Varsayılan: Otomatik.
- **Kod modu** (sohbetteki *Kod* düğmesi): kodlama kuralları, düşük sıcaklık, uzun cevap; `run_javascript` ve `check_code` araçları; cevaptaki HTML/SVG/JSON/CSS/JS otomatik kontrol edilir, hata varsa model düzeltir.
- **Kod stüdyosu:** kod bloklarında renklendirme; HTML ve SVG için uygulama içi önizleme (internetsiz), JavaScript çalıştırma ve konsol, düzenleme, kaydetme/paylaşma, "Modele düzelt".
- Uzunluk sınırında kesilen cevaplar için **Devam et** düğmesi.
- Düşünme balonu artık kapalı gelir; modele giden sistem metni ve araç açıklamaları İngilizce.
