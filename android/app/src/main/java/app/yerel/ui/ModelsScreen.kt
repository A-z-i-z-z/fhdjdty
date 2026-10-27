package app.yerel.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yerel.core.Catalog
import app.yerel.core.CatalogModel
import app.yerel.core.HfFile
import app.yerel.core.ModelEntry
import app.yerel.core.formatBytes
import app.yerel.data.PendingDownload
import app.yerel.engine.EngineStatus
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(vm: AppViewModel, snackbar: SnackbarHostState, initialLink: String?, onBack: () -> Unit) {
    val models by vm.models.collectAsState()
    val downloads by vm.downloads.collectAsState()
    val settings by vm.settings.collectAsState()
    val status by vm.engineStatus.collectAsState()
    val hf by vm.hf.collectAsState()
    val ram by vm.ram.collectAsState()
    val importing by vm.importProgress.collectAsState()
    var link by rememberSaveable { mutableStateOf(initialLink ?: "") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::inspectLocal) }

    LaunchedEffect(initialLink) {
        if (!initialLink.isNullOrBlank()) { link = initialLink; vm.lookupHf(initialLink) }
    }
    LaunchedEffect(Unit) {
        while (true) { vm.refreshRam(); delay(3000) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Modeller") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Geri") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { RamCard(ram.availBytes, ram.totalBytes, vm) }
            item {
                AddCard(
                    link = link,
                    onLink = { link = it },
                    onLookup = { vm.lookupHf(link) },
                    onPick = { picker.launch(arrayOf("*/*")) },
                    hf = hf,
                    vm = vm,
                )
            }
            importing?.let { p ->
                item {
                    ProgressCard("Kopyalanıyor: ${p.name}", if (p.total > 0) p.copied.toFloat() / p.total else null, "${formatBytes(p.copied)} / ${formatBytes(p.total)}", null)
                }
            }
            if (downloads.isNotEmpty()) {
                item { SectionTitle("İndirilenler") }
                items(downloads, key = { "dl${it.downloadId}" }) { d -> DownloadCard(d, vm) }
            }
            item { SectionTitle("Modellerim") }
            if (models.isEmpty()) {
                item {
                    Text(
                        "Henüz model yok. Aşağıdan önerilen bir modeli indirin, Hugging Face bağlantısı yapıştırın veya telefondaki bir GGUF dosyasını seçin.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(models, key = { it.id }) { m ->
                InstalledCard(
                    m, selected = m.id == settings.lastModelId,
                    loaded = status is EngineStatus.Ready && (status as EngineStatus.Ready).modelId == m.id,
                    vm = vm,
                )
            }
            item { SectionTitle("Önerilen modeller") }
            items(Catalog.models, key = { it.repo }) { c ->
                val installed = models.any { it.source.startsWith("hf:${c.repo}/") }
                val busy = downloads.any { it.repo == c.repo && !it.failed }
                CatalogCard(c, installed, busy, vm)
            }
            item {
                Text(
                    "İpucu: Bilgisayardan USB ile kopyaladığınız .gguf dosyaları için klasör: Android/data/app.yerel/files/models — uygulama bunları otomatik bulur.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    vm.pendingImport.collectAsState().value?.let { f ->
        val est = vm.estimate(f.size, f.info)
        AlertDialog(
            onDismissRequest = vm::dismissImport,
            title = { Text("Modeli ekle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(f.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOfNotNull(f.info.arch, f.info.quantName.ifBlank { null }, if (f.size > 0) formatBytes(f.size) else null, f.info.sizeLabel.ifBlank { null })
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FitLine(est)
                    Spacer(Modifier.height(4.dp))
                    Text("Kopyala: dosya uygulamaya kopyalanır, en hızlı yükleme. Ek ${formatBytes(f.size.coerceAtLeast(0))} yer kaplar.", style = MaterialTheme.typography.bodySmall)
                    Text("Bağlantı: dosya yerinde kullanılır, ek yer kaplamaz. Dosyayı silerseniz model de gider.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { Button(onClick = { vm.confirmImport(copy = true) }) { Text("Kopyala") } },
            dismissButton = { OutlinedButton(onClick = { vm.confirmImport(copy = false) }) { Text("Bağlantı") } },
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, start = 4.dp),
    )
}

@Composable
private fun RamCard(avail: Long, total: Long, vm: AppViewModel) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(18.dp), color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Memory, null, tint = scheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("Boş RAM", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("${formatBytes(avail)} / ${formatBytes(total)}", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { if (total > 0) 1f - avail.toFloat() / total else 0f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Model + KV önbelleği boş RAM'e sığmalı. Diğer uygulamaları kapatmak yer açar. Depolamada ${formatBytes(vm.freeSpace())} boş.",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddCard(link: String, onLink: (String) -> Unit, onLookup: () -> Unit, onPick: () -> Unit, hf: HfLookup, vm: AppViewModel) {
    val scheme = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    Surface(shape = RoundedCornerShape(18.dp), color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Model ekle", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = link,
                onValueChange = onLink,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Link, null) },
                placeholder = { Text("huggingface.co bağlantısı veya kullanıcı/depo") },
                trailingIcon = {
                    if (link.isEmpty()) {
                        IconButton(onClick = { clipboard.getText()?.text?.let { onLink(it.trim()); if (it.isNotBlank()) onLookup() } }) {
                            Icon(Icons.Rounded.ContentPaste, "Yapıştır")
                        }
                    } else {
                        IconButton(onClick = onLookup) { Icon(Icons.Rounded.Search, "Bul") }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onLookup() }),
                shape = RoundedCornerShape(14.dp),
            )
            when (hf) {
                is HfLookup.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Dosyalar getiriliyor…", style = MaterialTheme.typography.bodyMedium)
                }
                is HfLookup.Error -> Text(hf.message, color = scheme.error, style = MaterialTheme.typography.bodyMedium)
                is HfLookup.Found -> HfFiles(hf, vm)
                HfLookup.Idle -> {}
            }
            OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Telefondan GGUF dosyası seç")
            }
        }
    }
}

@Composable
private fun HfFiles(found: HfLookup.Found, vm: AppViewModel) {
    var chosen by remember(found) { mutableStateOf(found.suggested ?: found.files.firstOrNull { !it.isMmproj }) }
    val scheme = MaterialTheme.colorScheme
    Column {
        Text(found.repo, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        found.files.filter { !it.isMmproj }.forEach { f ->
            val est = vm.estimate(f.size, null)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = chosen == f, onClick = { chosen = f }, enabled = !f.isSplitPart)
                Column(Modifier.weight(1f)) {
                    Text(f.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(f.quant.ifBlank { null }, if (f.size > 0) formatBytes(f.size) else null, if (f.isSplitPart) "parçalı, desteklenmiyor" else null)
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                    )
                }
                if (f.size > 0) FitDot(est.fit)
            }
        }
        val mmproj = found.files.count { it.isMmproj }
        if (mmproj > 0) {
            Text("$mmproj görüntü projektörü (mmproj) dosyası gizlendi; bu sürüm sadece metin destekler.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Row {
            TextButton(onClick = vm::clearHf) { Text("Kapat") }
            Spacer(Modifier.weight(1f))
            Button(onClick = { chosen?.let { vm.download(found.repo, found.revision, it) } }, enabled = chosen != null && chosen?.isSplitPart == false) {
                Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("İndir")
            }
        }
    }
}

@Composable
private fun FitDot(fit: Fit) {
    val (color, label) = fitStyle(fit)
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.15f)) {
        Text(label, color = color, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
private fun fitStyle(fit: Fit): Pair<Color, String> = when (fit) {
    Fit.COMFORTABLE -> Color(0xFF2E9E5B) to "Rahat sığar"
    Fit.OK -> Color(0xFFC98A0B) to "Sığar"
    Fit.TIGHT -> MaterialTheme.colorScheme.error to "RAM yetmeyebilir"
    Fit.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant to "?"
}

@Composable
fun FitLine(est: MemoryEstimate) {
    val (color, label) = fitStyle(est.fit)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(
            "$label · gereken ~${formatBytes(est.totalBytes)} (model ${formatBytes(est.modelBytes)} + KV ${formatBytes(est.kvBytes)}), boş ${formatBytes(est.availBytes)}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ProgressCard(title: String, progress: Float?, detail: String, onCancel: (() -> Unit)?) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                onCancel?.let { IconButton(onClick = it, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Close, "İptal", Modifier.size(18.dp)) } }
            }
            Spacer(Modifier.height(8.dp))
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
            }
            Spacer(Modifier.height(6.dp))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DownloadCard(d: PendingDownload, vm: AppViewModel) {
    if (d.failed) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(d.name, style = MaterialTheme.typography.titleSmall)
                    Text("İndirilemedi: ${vm.failureText(d)}", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { vm.cancelDownload(d.downloadId) }) { Text("Kaldır") }
            }
        }
        return
    }
    val detail = when (d.status) {
        android.app.DownloadManager.STATUS_PAUSED -> "Bekliyor (bağlantı) · ${formatBytes(d.downloaded)} / ${formatBytes(d.totalBytes)}"
        android.app.DownloadManager.STATUS_PENDING -> "Sırada…"
        else -> "${formatBytes(d.downloaded)} / ${formatBytes(d.totalBytes)} · %${(d.progress * 100).toInt()}"
    }
    ProgressCard(d.name, if (d.totalBytes > 0) d.progress else null, detail) { vm.cancelDownload(d.downloadId) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InstalledCard(m: ModelEntry, selected: Boolean, loaded: Boolean, vm: AppViewModel) {
    val scheme = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
        onClick = { vm.selectModel(m.id) },
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 14.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(m.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Seçenekler") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Yeniden adlandır") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { renaming = true; menu = false })
                        if (loaded) DropdownMenuItem(text = { Text("Bellekten kaldır") }, leadingIcon = { Icon(Icons.Rounded.Memory, null) }, onClick = { vm.unloadModel(); menu = false })
                        DropdownMenuItem(text = { Text("Sil") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { confirmDelete = true; menu = false })
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(end = 12.dp)) {
                val info = m.info
                listOfNotNull(
                    info?.quantName?.ifBlank { null },
                    formatBytes(m.sizeBytes),
                    info?.arch?.ifBlank { null },
                    info?.sizeLabel?.ifBlank { null },
                    info?.ctxTrain?.takeIf { it > 0 }?.let { "${it / 1024}K bağlam" },
                    when (m.source) { "link" -> "bağlantı"; "local" -> "yerel dosya"; "folder" -> "klasör"; else -> null },
                    if (info?.templateTools == true) "araçlar" else null,
                    if (info?.templateThinking == true) "düşünme" else null,
                    if (loaded) "● bellekte" else null,
                ).forEach { Tag(it) }
            }
            Spacer(Modifier.height(6.dp))
            FitLine(vm.estimate(m.sizeBytes, m.info))
        }
    }
    if (renaming) {
        var text by remember { mutableStateOf(m.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Yeniden adlandır") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.renameModel(m, text); renaming = false }) { Text("Kaydet") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Vazgeç") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Model silinsin mi?") },
            text = { Text(if (m.isLinked) "Bağlantı kaldırılır; orijinal dosya silinmez." else "${m.name} (${formatBytes(m.sizeBytes)}) telefondan silinir.") },
            confirmButton = { TextButton(onClick = { vm.deleteModel(m); confirmDelete = false }) { Text("Sil", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } },
        )
    }
}

@Composable
fun Tag(text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogCard(c: CatalogModel, installed: Boolean, busy: Boolean, vm: AppViewModel) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(18.dp), color = scheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("${c.repo} · ${c.quant} · ~${formatBytes(c.approxBytes)}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                when {
                    installed -> Icon(Icons.Rounded.CheckCircle, "Yüklü", tint = scheme.primary)
                    busy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else -> FilledTonalButton(onClick = { vm.downloadCatalog(c) }, contentPadding = PaddingValues(horizontal = 14.dp)) {
                        Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("İndir")
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(c.description, style = MaterialTheme.typography.bodyMedium)
            if (c.tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { c.tags.forEach { Tag(it) } }
            }
        }
    }
}

