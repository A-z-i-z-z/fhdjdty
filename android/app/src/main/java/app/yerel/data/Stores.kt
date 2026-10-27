package app.yerel.data

import android.content.Context
import app.yerel.core.AppSettings
import app.yerel.core.Conversation
import app.yerel.core.MemoryAccess
import app.yerel.core.MemoryItem
import app.yerel.core.ModelEntry
import app.yerel.core.mapObjects
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Writes through a temp file so a crash never leaves half a JSON file behind. */
internal fun File.writeAtomic(text: String) {
    parentFile?.mkdirs()
    val tmp = File(parentFile, "$name.tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(this)) {
        delete()
        tmp.renameTo(this)
    }
}

internal fun File.readOrNull(): String? = try {
    if (exists()) readText() else null
} catch (e: Exception) {
    null
}

class SettingsStore(context: Context) {
    private val file = File(context.filesDir, "settings.json")
    private val _flow = MutableStateFlow(
        file.readOrNull()?.let { runCatching { AppSettings.fromJson(JSONObject(it)) }.getOrNull() } ?: AppSettings(),
    )
    val flow: StateFlow<AppSettings> = _flow.asStateFlow()
    val value get() = _flow.value

    @Synchronized
    fun update(fn: (AppSettings) -> AppSettings) {
        val next = fn(_flow.value)
        if (next == _flow.value) return
        _flow.value = next
        file.writeAtomic(next.toJson().toString())
    }
}

class ModelStore(context: Context) {
    private val file = File(context.filesDir, "models.json")
    private val _flow = MutableStateFlow(load())
    val flow: StateFlow<List<ModelEntry>> = _flow.asStateFlow()
    val value get() = _flow.value

    private fun load(): List<ModelEntry> = file.readOrNull()?.let {
        runCatching { JSONArray(it).mapObjects(ModelEntry::fromJson) }.getOrNull()
    } ?: emptyList()

    fun get(id: String?) = _flow.value.firstOrNull { it.id == id }

    @Synchronized
    fun upsert(entry: ModelEntry) {
        _flow.update { list -> listOf(entry) + list.filter { it.id != entry.id } }
        save()
    }

    @Synchronized
    fun remove(id: String) {
        _flow.update { list -> list.filter { it.id != id } }
        save()
    }

    private fun save() = file.writeAtomic(JSONArray(_flow.value.map { it.toJson() }).toString())
}

class MemoryStore(context: Context) : MemoryAccess {
    private val file = File(context.filesDir, "memory.json")
    private val _flow = MutableStateFlow(
        file.readOrNull()?.let { runCatching { JSONArray(it).mapObjects(MemoryItem::fromJson) }.getOrNull() } ?: emptyList(),
    )
    val flow: StateFlow<List<MemoryItem>> = _flow.asStateFlow()

    override fun list() = _flow.value

    @Synchronized
    override fun add(text: String): MemoryItem {
        val item = MemoryItem(text = text.trim())
        _flow.update { it + item }
        save()
        return item
    }

    @Synchronized
    override fun remove(id: String): Boolean {
        val before = _flow.value.size
        _flow.update { list -> list.filter { it.id != id } }
        save()
        return _flow.value.size != before
    }

    @Synchronized
    fun edit(id: String, text: String) {
        _flow.update { list -> list.map { if (it.id == id) it.copy(text = text.trim()) else it } }
        save()
    }

    @Synchronized
    fun clear() {
        _flow.value = emptyList()
        save()
    }

    private fun save() = file.writeAtomic(JSONArray(_flow.value.map { it.toJson() }).toString())
}

data class ConversationSummary(val id: String, val title: String, val updatedAt: Long, val modelId: String?)

/** One JSON file per conversation plus a small index, so only the open chat is held in memory. */
class ConversationStore(context: Context) {
    private val dir = File(context.filesDir, "chats")
    private val indexFile = File(dir, "index.json")
    private val _index = MutableStateFlow(loadIndex())
    val index: StateFlow<List<ConversationSummary>> = _index.asStateFlow()

    private fun loadIndex(): List<ConversationSummary> {
        val list = indexFile.readOrNull()?.let {
            runCatching {
                JSONArray(it).mapObjects { o ->
                    ConversationSummary(o.getString("id"), o.optString("title"), o.optLong("updated"), o.optString("model").ifEmpty { null })
                }
            }.getOrNull()
        }
        return (list ?: rebuildIndex()).sortedByDescending { it.updatedAt }
    }

    private fun rebuildIndex(): List<ConversationSummary> =
        dir.listFiles { f -> f.name.endsWith(".json") && f.name != "index.json" }?.mapNotNull { f ->
            f.readOrNull()?.let { runCatching { Conversation.fromJson(JSONObject(it)) }.getOrNull() }
                ?.let { ConversationSummary(it.id, it.title, it.updatedAt, it.modelId) }
        } ?: emptyList()

    private fun fileOf(id: String) = File(dir, "${id.filter { it.isLetterOrDigit() || it == '-' }}.json")

    fun load(id: String): Conversation? =
        fileOf(id).readOrNull()?.let { runCatching { Conversation.fromJson(JSONObject(it)) }.getOrNull() }

    @Synchronized
    fun save(c: Conversation) {
        fileOf(c.id).writeAtomic(c.toJson().toString())
        val summary = ConversationSummary(c.id, c.title, c.updatedAt, c.modelId)
        _index.update { list -> (listOf(summary) + list.filter { it.id != c.id }).sortedByDescending { it.updatedAt } }
        saveIndex()
    }

    @Synchronized
    fun rename(id: String, title: String) {
        load(id)?.let { save(it.copy(title = title)) }
    }

    @Synchronized
    fun delete(id: String) {
        fileOf(id).delete()
        _index.update { list -> list.filter { it.id != id } }
        saveIndex()
    }

    @Synchronized
    fun deleteAll() {
        dir.listFiles()?.forEach { it.delete() }
        _index.value = emptyList()
    }

    private fun saveIndex() = indexFile.writeAtomic(
        JSONArray(_index.value.map { s ->
            JSONObject().put("id", s.id).put("title", s.title).put("updated", s.updatedAt).put("model", s.modelId ?: "")
        }).toString(),
    )
}
