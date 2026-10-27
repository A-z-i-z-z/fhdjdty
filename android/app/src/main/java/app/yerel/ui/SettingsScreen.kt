package app.yerel.ui

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yerel.BuildConfig
import app.yerel.core.AppSettings
import app.yerel.core.DEFAULT_SYSTEM_PROMPT
import app.yerel.core.ToolCatalog
import app.yerel.engine.EngineStatus
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit, onMemory: () -> Unit) {
    val s by vm.settings.collectAsState()
    val models by vm.models.collectAsState()
    val status by vm.engineStatus.collectAsState()
    val model = models.firstOrNull { it.id == s.lastModelId }
    val set: ((AppSettings) -> AppSettings) -> Unit = vm::updateSettings
    var editPrompt by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Geri") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Group("Bağlam ve bellek") {
                    val ctxMax = model?.info?.ctxTrain?.takeIf { it > 0 }
                    Choice(
                        "Bağlam penceresi", "Modelin aynı anda görebildiği token sayısı. Büyüdükçe KV önbelleği RAM'i artar." +
                            (ctxMax?.let { " Bu model en fazla ${it / 1024}K destekler." } ?: ""),
                        listOf(1024, 2048, 4096, 8192, 16384, 32768, 65536, 131072).filter { ctxMax == null || it <= ctxMax },
                        s.nCtx, { "${it / 1024}K" },
                    ) { v -> set { it.copy(nCtx = v) } }
                    val kvTypes = listOf("f16", "bf16", "q8_0", "q5_1", "q5_0", "q4_1", "q4_0", "iq4_nl")
                    Choice("KV önbelleği: K", "q8_0 neredeyse kayıpsızdır ve belleği yarıya indirir. q4_0 dörtte bire indirir, kaliteyi biraz düşürür.", kvTypes, s.kvTypeK, { it }) { v -> set { it.copy(kvTypeK = v) } }
                    Choice("KV önbelleği: V", "Kuantize V için Flash Attention otomatik açılır.", kvTypes, s.kvTypeV, { it }) { v -> set { it.copy(kvTypeV = v) } }
                    Choice("Flash Attention", null, listOf(-1, 1, 0), s.flashAttn, { when (it) { 1 -> "Açık"; 0 -> "Kapalı"; else -> "Otomatik" } }) { v -> set { it.copy(flashAttn = v) } }
                    if (model != null) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(model.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                FitLine(vm.estimate(model.sizeBytes, model.info, s))
                            }
                        }
                    }
                    Choice("Toplu işlem (batch)", "Büyük değer istemi daha hızlı işler ama daha çok RAM kullanır.", listOf(64, 128, 256, 512, 1024), s.nBatch, { "$it" }) { v -> set { it.copy(nBatch = v) } }
                    val cpus = Runtime.getRuntime().availableProcessors()
                    Choice(
                        "İş parçacığı", "Otomatik: ${s.resolvedThreads(cpus)} (çekirdek: $cpus). Büyük çekirdek sayısını aşmak yavaşlatır.",
                        listOf(0, 2, 3, 4, 5, 6, 8).filter { it <= cpus }, s.nThreads, { if (it == 0) "Otomatik" else "$it" },
                    ) { v -> set { it.copy(nThreads = v) } }
                    Toggle("Bellek eşleme (mmap)", "Önerilir: model dosyadan okunur, RAM'i sistemle paylaşır ve hızlı açılır.", s.useMmap) { v -> set { it.copy(useMmap = v) } }
                    Toggle("Belleğe kilitle (mlock)", "Modelin RAM'den atılmasını engeller; RAM azsa diğer uygulamaları kapatabilir.", s.useMlock) { v -> set { it.copy(useMlock = v) } }
                    Toggle("Arka planda bellekten kaldır", "Uygulamadan çıkınca 1 dk sonra modeli RAM'den boşaltır.", s.unloadInBackground) { v -> set { it.copy(unloadInBackground = v) } }
                    if (status is EngineStatus.Ready) {
                        TextButton(onClick = vm::unloadModel) { Text("Modeli şimdi bellekten kaldır") }
                    }
                }
            }
            item {
                val devices by vm.devices.collectAsState()
                val probing by vm.probing.collectAsState()
                val info = (status as? EngineStatus.Ready)?.info
                Group("Donanım ve hız") {
                    Choice(
                        "Hesaplama birimi",
                        "GPU: Adreno ekran kartı (OpenCL). NPU: Snapdragon Hexagon yapay zekâ birimi, deneysel. " +
                            "En iyi sonuç Q4_0 ve Q8_0 dosyalarla alınır; desteklenmeyen kısımlar CPU'da çalışır.",
                        listOf("cpu", "gpu", "npu"), s.device, { when (it) { "gpu" -> "GPU"; "npu" -> "NPU (deneysel)"; else -> "CPU" } },
                    ) { v -> set { it.copy(device = v) } }
                    if (s.device != "cpu") {
                        val layers = model?.info?.nLayer?.takeIf { it > 0 } ?: 48
                        SliderRow(
                            "GPU/NPU'ya taşınan katman", s.gpuLayers.coerceAtMost(layers + 1).toFloat(), 0f..(layers + 1).toFloat(), 0,
                            { if (it.roundToInt() > layers) "Tümü" else "${it.roundToInt()}" },
                        ) { v -> set { it.copy(gpuLayers = if (v.roundToInt() > layers) 999 else v.roundToInt()) } }
                    }
                    if (info != null) {
                        InfoLine("Şu an: ${info.device}" + info.deviceNote.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty())
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = vm::probeDevices, enabled = !probing) { Text(if (probing) "Taranıyor…" else "Cihazları tara") }
                        devices?.let { list ->
                            val acc = list.filter { it.kind != "cpu" }
                            Text(
                                if (acc.isEmpty()) "Hızlandırıcı bulunamadı" else acc.joinToString { "${it.kind.uppercase()}: ${it.description}" },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Choice(
                        "Hızlandırma (spekülatif çözümleme)",
                        "Önce ucuz bir tahmin üretilir, model tek adımda doğrular; cevap aynı kalır, hız artar. " +
                            "N-gram: tekrar eden metin ve kodda (ek bellek yok). MTP: modelin kendi tahmin katmanı (Qwen3.5 vb.). " +
                            "Taslak model: aynı aileden küçük bir model. Otomatik: N-gram + MTP ya da taslak model.",
                        listOf("off", "auto", "ngram", "mtp", "draft"), s.specMode,
                        { when (it) { "off" -> "Kapalı"; "auto" -> "Otomatik"; "ngram" -> "N-gram"; "mtp" -> "MTP"; else -> "Taslak model" } },
                    ) { v -> set { it.copy(specMode = v) } }
                    if (s.specMode in setOf("auto", "draft")) {
                        val candidates = models.filter { it.id != s.lastModelId && it.path != null }
                        Choice(
                            "Taslak model", "Ana modelle aynı sözlüğü kullanan küçük bir model (ör. Qwen3 4B için Qwen3 0.6B). RAM ister.",
                            listOf<String?>(null) + candidates.map { it.id }, s.draftModelId,
                            { id -> if (id == null) "Yok" else candidates.firstOrNull { it.id == id }?.name ?: "?" },
                        ) { v -> set { it.copy(draftModelId = v) } }
                    }
                    if (s.specMode != "off") {
                        Choice("Taslak uzunluğu", "MTP ve taslak modelin bir adımda önerdiği token sayısı.", listOf(1, 2, 3, 4, 6, 8), s.nDraft, { "$it" }) { v -> set { it.copy(nDraft = v) } }
                    }
                    if (info != null && s.specMode != "off") {
                        val active = info.spec.split(',').filter { it.isNotBlank() }.joinToString { specLabel(it) }
                        InfoLine("Etkin: " + active.ifEmpty { "yok" } + info.specNote.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty())
                    }
                }
            }
            item {
                Group("Örnekleme") {
                    val preset = model?.preset
                    if (preset != null) {
                        Toggle("Modelin önerdiği değerleri kullan", "Model kartındaki sıcaklık/top-k/top-p değerleri aşağıdakilerin yerine geçer.", s.useModelPreset) { v -> set { it.copy(useModelPreset = v) } }
                    }
                    val overridden = preset != null && s.useModelPreset
                    SliderRow("Sıcaklık", s.temperature, 0f..2f, 40, { fmt(it, 2) }, enabled = !(overridden && preset?.temperature != null)) { v -> set { it.copy(temperature = v) } }
                    SliderRow("Top-K", s.topK.toFloat(), 0f..200f, 0, { if (it < 1f) "kapalı" else "${it.roundToInt()}" }, enabled = !(overridden && preset?.topK != null)) { v -> set { it.copy(topK = v.roundToInt()) } }
                    SliderRow("Top-P", s.topP, 0f..1f, 19, { fmt(it, 2) }, enabled = !(overridden && preset?.topP != null)) { v -> set { it.copy(topP = v) } }
                    SliderRow("Min-P", s.minP, 0f..0.5f, 49, { fmt(it, 2) }, enabled = !(overridden && preset?.minP != null)) { v -> set { it.copy(minP = v) } }
                    SliderRow("Tekrar cezası", s.repeatPenalty, 1f..1.5f, 49, { fmt(it, 2) }, enabled = !(overridden && preset?.repeatPenalty != null)) { v -> set { it.copy(repeatPenalty = v) } }
                    Choice("En uzun cevap", "Token cinsinden.", listOf(256, 512, 1024, 2048, 4096, 8192), s.maxTokens, { "$it" }) { v -> set { it.copy(maxTokens = v) } }
                    NumberField("Tohum (seed)", "-1 = her seferinde rastgele", s.seed.toString()) { t -> t.toLongOrNull()?.let { v -> set { it.copy(seed = v) } } }
                    TextButton(onClick = {
                        val d = AppSettings()
                        set { it.copy(temperature = d.temperature, topK = d.topK, topP = d.topP, minP = d.minP, repeatPenalty = d.repeatPenalty, maxTokens = d.maxTokens, seed = d.seed) }
                    }) { Text("Varsayılanlara dön") }
                }
            }
            item {
                Group("Asistan") {
                    SettingRow("Sistem istemi", s.systemPrompt.take(90) + if (s.systemPrompt.length > 90) "…" else "", onClick = { editPrompt = true })
                    Toggle("Düşünme modu", "Destekleyen modeller (Qwen3, LFM3…) cevaptan önce düşünür. Daha akıllı ama daha yavaş.", s.thinking) { v -> set { it.copy(thinking = v) } }
                    if (s.thinking) {
                        Choice("Düşünme bütçesi", "Düşünce bu kadar tokeni aşınca cevaba geçilir.", listOf(-1, 256, 512, 1024, 2048, 4096), s.reasoningBudget, { if (it < 0) "Sınırsız" else "$it" }) { v -> set { it.copy(reasoningBudget = v) } }
                    }
                    Toggle("Ajan araçları", "Model gerektiğinde web araması, hesap makinesi vb. araçları kendisi çağırır.", s.agentEnabled) { v -> set { it.copy(agentEnabled = v) } }
                    if (s.agentEnabled) {
                        ToolCatalog.all.forEach { t ->
                            if (!s.memoryEnabled && (t.name == "remember" || t.name == "forget")) return@forEach
                            Toggle("   ${t.title}", if (t.coding) "Kod modunda" else null, t.name !in s.disabledTools) { on ->
                                set { it.copy(disabledTools = if (on) it.disabledTools - t.name else it.disabledTools + t.name) }
                            }
                        }
                        Choice("En fazla araç adımı", "Bir cevap için arka arkaya kaç araç turu yapılabilir.", listOf(1, 2, 4, 6, 8, 12), s.maxAgentSteps, { "$it" }) { v -> set { it.copy(maxAgentSteps = v) } }
                    }
                    Toggle("Uzun süreli hafıza", "Model kalıcı bilgileri kaydeder ve her sohbette hatırlar.", s.memoryEnabled) { v -> set { it.copy(memoryEnabled = v) } }
                    SettingRow("Hafızayı yönet", "Kayıtlı bilgileri gör, düzenle, sil", onClick = onMemory)
                }
            }
            item {
                Group("Kodlama") {
                    Toggle(
                        "Kod modu", "Kod yazarken düşük sıcaklık, uzun cevap, kodlama kuralları ve JavaScript çalıştırma / kod kontrol araçları. " +
                            "Sohbet ekranındaki \"Kod\" düğmesiyle de açılır.", s.codeMode,
                    ) { v -> set { it.copy(codeMode = v) } }
                    Toggle(
                        "Otomatik kontrol ve düzeltme", "Kod modunda cevaptaki HTML, SVG, JSON, CSS ve JavaScript kontrol edilir; hata varsa model en fazla 2 kez düzeltmesi için uyarılır.",
                        s.codeAutoCheck,
                    ) { v -> set { it.copy(codeAutoCheck = v) } }
                    Toggle(
                        "Önizlemede internete izin ver", "Kapalıyken önizlenen sayfalar hiçbir yere bağlanamaz (önerilir).", s.previewNetwork,
                    ) { v -> set { it.copy(previewNetwork = v) } }
                }
            }
            item {
                Group("Web") {
                    Choice("Arama motoru", "SearXNG kendi sunucunuz veya herkese açık bir örnek olabilir.", listOf("duckduckgo", "searxng"), s.searchProvider, { if (it == "searxng") "SearXNG" else "DuckDuckGo" }) { v -> set { it.copy(searchProvider = v) } }
                    if (s.searchProvider == "searxng") {
                        TextInput("SearXNG adresi", s.searxngUrl, "https://searx.example.org") { v -> set { it.copy(searxngUrl = v.trim()) } }
                    }
                    Choice("Sayfa okuma uzunluğu", "Okunan web sayfasından modele verilen en fazla karakter. Çok uzunu bağlamı doldurur.", listOf(2000, 4000, 6000, 12000, 24000), s.fetchMaxChars, { "${it / 1000}K" }) { v -> set { it.copy(fetchMaxChars = v) } }
                }
            }
            item {
                Group("Hugging Face") {
                    TextInput("Erişim tokeni (isteğe bağlı)", s.hfToken, "hf_…", secret = true) { v -> set { it.copy(hfToken = v.trim()) } }
                    Text(
                        "Sadece özel veya erişim onayı isteyen (gated) modeller için gerekir. huggingface.co/settings/tokens adresinden \"read\" tokeni oluşturun.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Group("Görünüm") {
                    Choice("Tema", null, listOf(0, 1, 2), s.themeMode, { when (it) { 1 -> "Açık"; 2 -> "Koyu"; else -> "Sistem" } }) { v -> set { it.copy(themeMode = v) } }
                    AccentPicker(s.accent, enabled = !s.dynamicColor) { v -> set { it.copy(accent = v) } }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Toggle("Duvar kağıdı renkleri", "Android'in Material You renklerini kullan.", s.dynamicColor) { v -> set { it.copy(dynamicColor = v) } }
                    }
                    SliderRow("Yazı boyutu", s.fontScale, 0.8f..1.4f, 11, { "%${(it * 100).roundToInt()}" }) { v -> set { it.copy(fontScale = v) } }
                    Toggle("Hız istatistiklerini göster", "Cevapların altında token/sn ve token sayısı.", s.showStats) { v -> set { it.copy(showStats = v) } }
                }
            }
            item {
                Group("Veri") {
                    SettingRow("Tüm sohbetleri sil", "Geri alınamaz", onClick = { confirmWipe = true })
                }
            }
            item {
                Group("Hakkında") {
                    Text("Yerel ${BuildConfig.VERSION_NAME} · llama.cpp", style = MaterialTheme.typography.bodyMedium)
                    Text(vm.systemInfo, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (editPrompt) {
        var text by remember { mutableStateOf(s.systemPrompt) }
        AlertDialog(
            onDismissRequest = { editPrompt = false },
            title = { Text("Sistem istemi") },
            text = { OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 5, maxLines = 12) },
            confirmButton = { TextButton(onClick = { set { it.copy(systemPrompt = text) }; editPrompt = false }) { Text("Kaydet") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { text = DEFAULT_SYSTEM_PROMPT }) { Text("Varsayılan") }
                    TextButton(onClick = { editPrompt = false }) { Text("Vazgeç") }
                }
            },
        )
    }
    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Tüm sohbetler silinsin mi?") },
            text = { Text("Bu işlem geri alınamaz. Modeller ve hafıza silinmez.") },
            confirmButton = { TextButton(onClick = { vm.deleteAllChats(); confirmWipe = false }) { Text("Sil", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Vazgeç") } },
        )
    }
}

private fun fmt(v: Float, digits: Int) = String.format(Locale.ROOT, "%.${digits}f", v)

private fun specLabel(type: String) = when (type.trim()) {
    "ngram-simple" -> "N-gram"
    "draft-mtp" -> "MTP"
    "draft-simple" -> "Taslak model"
    else -> type.trim()
}

@Composable
private fun InfoLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun Group(title: String, content: @Composable () -> Unit) {
    Column {
        SectionTitle(title)
        Spacer(Modifier.height(8.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun <T> Choice(title: String, help: String?, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        help?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            options.forEach { o ->
                FilterChip(
                    selected = o == selected,
                    onClick = { onSelect(o) },
                    label = { Text(label(o)) },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }
    }
}

@Composable
fun Toggle(title: String, help: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            help?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, label: (Float) -> String, enabled: Boolean = true, onChange: (Float) -> Unit) {
    var local by remember(value) { mutableStateOf(value) }
    Column {
        Row {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(if (enabled) label(local) else "model ayarı", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = local, onValueChange = { local = it }, valueRange = range, steps = steps, enabled = enabled,
            onValueChangeFinished = { onChange(local) },
        )
    }
}

@Composable
fun SettingRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
fun TextInput(title: String, value: String, placeholder: String, secret: Boolean = false, onDone: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onDone(it) },
        label = { Text(title) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
fun NumberField(title: String, help: String, value: String, onDone: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { t -> text = t.filter { it.isDigit() || it == '-' }; onDone(text) },
        label = { Text(title) },
        supportingText = { Text(help) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentPicker(selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    val dark = MaterialTheme.colorScheme.background.let { (it.red + it.green + it.blue) / 3 < 0.5f }
    Column {
        Text("Vurgu rengi", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Accents.forEachIndexed { i, a ->
                val color = if (dark) a.dark else a.light
                Box(
                    Modifier.size(38.dp).background(color, CircleShape)
                        .then(if (i == selected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape) else Modifier)
                        .clickable(enabled = enabled) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (i == selected) Icon(Icons.Rounded.Check, a.name, tint = if (dark) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
