package app.yerel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.yerel.core.MemoryItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(vm: AppViewModel, onBack: () -> Unit) {
    val items by vm.memories.collectAsState()
    val s by vm.settings.collectAsState()
    var input by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<MemoryItem?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val df = remember { SimpleDateFormat("d MMM yyyy", Locale("tr")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hafıza") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Geri") } },
                actions = {
                    if (items.isNotEmpty()) IconButton(onClick = { confirmClear = true }) { Icon(Icons.Rounded.DeleteSweep, "Hepsini sil") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Toggle(
                    "Uzun süreli hafıza",
                    "Açıkken model, \"adım Deniz\" gibi kalıcı bilgileri kendisi kaydeder; kayıtlar her sohbetin başında modele verilir.",
                    s.memoryEnabled,
                ) { v -> vm.updateSettings { it.copy(memoryEnabled = v) } }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                        placeholder = { Text("Elle bilgi ekle…") }, shape = RoundedCornerShape(14.dp), maxLines = 3,
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(onClick = { vm.addMemory(input); input = "" }, enabled = input.isNotBlank()) {
                        Icon(Icons.Rounded.Add, "Ekle")
                    }
                }
            }
            if (items.isEmpty()) {
                item {
                    Text(
                        "Henüz kayıtlı bilgi yok. Sohbette \"Bunu hatırla: …\" diyebilirsin.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            items(items.asReversed(), key = { it.id }) { m ->
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.text, style = MaterialTheme.typography.bodyLarge)
                            Text(df.format(Date(m.createdAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { editing = m }) { Icon(Icons.Rounded.Edit, "Düzenle") }
                        IconButton(onClick = { vm.removeMemory(m.id) }) { Icon(Icons.Rounded.Delete, "Sil") }
                    }
                }
            }
        }
    }
    editing?.let { m ->
        var text by remember(m.id) { mutableStateOf(m.text) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Bilgiyi düzenle") },
            text = { OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth(), maxLines = 5) },
            confirmButton = { TextButton(onClick = { vm.editMemory(m.id, text); editing = null }, enabled = text.isNotBlank()) { Text("Kaydet") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Vazgeç") } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Tüm hafıza silinsin mi?") },
            confirmButton = { TextButton(onClick = { vm.clearMemory(); confirmClear = false }) { Text("Sil", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Vazgeç") } },
        )
    }
}
