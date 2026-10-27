package app.yerel.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.yerel.BuildConfig
import app.yerel.container
import app.yerel.core.AgentConfig
import app.yerel.core.AgentEvent
import app.yerel.core.AgentRunner
import app.yerel.core.AppSettings
import app.yerel.core.ChatMessage
import app.yerel.core.Conversation
import app.yerel.core.HfFile
import app.yerel.core.HfRef
import app.yerel.core.HuggingFace
import app.yerel.core.ModelEntry
import app.yerel.core.Prompt
import app.yerel.core.Role
import app.yerel.core.SamplingPreset
import app.yerel.core.ToolCall
import app.yerel.core.ToolCatalog
import app.yerel.core.ToolConfig
import app.yerel.core.ToolRunner
import app.yerel.core.estimateKvBytes
import app.yerel.core.genParamsJson
import app.yerel.data.ModelManager
import app.yerel.data.RamInfo
import app.yerel.data.UserFacingException
import app.yerel.engine.EngineException
import app.yerel.engine.EngineStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

/** The reply being generated right now. */
data class LiveReply(
    val content: String = "",
    val reasoning: String = "",
    val toolCalls: List<ToolCall> = emptyList(),
    val activeTool: ToolCall? = null,
    val nGen: Int = 0,
    val tps: Double = 0.0,
    val loadingModel: Boolean = false,
)

/** Result of resolving a pasted Hugging Face link. */
sealed class HfLookup {
    data object Idle : HfLookup()
    data class Loading(val input: String) : HfLookup()
    data class Found(val repo: String, val revision: String, val files: List<HfFile>, val suggested: HfFile?) : HfLookup()
    data class Error(val message: String) : HfLookup()
}

enum class Fit { COMFORTABLE, OK, TIGHT, UNKNOWN }

data class MemoryEstimate(val modelBytes: Long, val kvBytes: Long, val overheadBytes: Long, val availBytes: Long, val fit: Fit) {
    val totalBytes get() = modelBytes + kvBytes + overheadBytes
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    val settings: StateFlow<AppSettings> = c.settings.flow
    val models = c.models.flow
    val memories = c.memory.flow
    val chats = c.chats.index
    val engineStatus = c.engine.status
    val downloads = c.manager.downloads
    val importProgress = c.manager.import

    private val _conversation = MutableStateFlow(Conversation(title = ""))
    val conversation: StateFlow<Conversation> = _conversation.asStateFlow()

    private val _live = MutableStateFlow<LiveReply?>(null)
    val live: StateFlow<LiveReply?> = _live.asStateFlow()

    private val _hf = MutableStateFlow<HfLookup>(HfLookup.Idle)
    val hf: StateFlow<HfLookup> = _hf.asStateFlow()

    private val _pendingImport = MutableStateFlow<ModelManager.LocalFile?>(null)
    val pendingImport: StateFlow<ModelManager.LocalFile?> = _pendingImport.asStateFlow()

    private val _messages = MutableStateFlow<String?>(null)
    /** One-shot snackbar text. */
    val toast: StateFlow<String?> = _messages.asStateFlow()

    private val _ram = MutableStateFlow(c.manager.ramInfo())
    val ram: StateFlow<RamInfo> = _ram.asStateFlow()

    val systemInfo: String get() = c.engine.systemInfo

    @Volatile private var cancelled = false
    private var genJob: Job? = null
    private var pollJob: Job? = null
    private var reloadJob: Job? = null
    private var backgroundJob: Job? = null

    private val tools = ToolRunner(c.http, c.memory, {
        val s = settings.value
        ToolConfig(searchProvider = s.searchProvider, searxngUrl = s.searxngUrl, fetchMaxChars = s.fetchMaxChars)
    })

    init {
        viewModelScope.launch {
            runCatching { c.engine.warmUp() }.onFailure { Log.e("Yerel", "native init failed", it) }
        }
        viewModelScope.launch {
            c.manager.scanFolder()
            if (selectedModel() == null) models.value.firstOrNull()?.let { m -> c.settings.update { it.copy(lastModelId = m.id) } }
        }
        startPolling()
    }

    fun consumeToast() { _messages.value = null }
    private fun toast(s: String) { _messages.value = s }

    // ---- models ---------------------------------------------------------------------------

    fun selectedModel(): ModelEntry? = c.models.get(settings.value.lastModelId)

    fun selectModel(id: String, preload: Boolean = true) {
        c.settings.update { it.copy(lastModelId = id) }
        if (preload && genJob?.isActive != true) {
            val entry = c.models.get(id) ?: return
            viewModelScope.launch {
                try {
                    c.engine.ensureLoaded(entry, settings.value)
                } catch (e: EngineException) {
                    toast("Model yüklenemedi: ${e.message}")
                }
            }
        }
    }

    fun unloadModel() {
        if (genJob?.isActive == true) stop()
        viewModelScope.launch { c.engine.unload() }
    }

    fun deleteModel(entry: ModelEntry) {
        viewModelScope.launch {
            if (c.engine.loadedModelId == entry.id || (engineStatus.value as? EngineStatus.Loading)?.modelId == entry.id) c.engine.unload()
            c.manager.delete(entry)
            if (settings.value.lastModelId == entry.id) c.settings.update { it.copy(lastModelId = models.value.firstOrNull()?.id) }
        }
    }

    fun renameModel(entry: ModelEntry, name: String) {
        if (name.isNotBlank()) c.models.upsert(entry.copy(name = name.trim()))
    }

    fun setModelPreset(entry: ModelEntry, preset: SamplingPreset?) = c.models.upsert(entry.copy(preset = preset))

    fun refreshRam() { _ram.value = c.manager.ramInfo() }

    fun freeSpace() = c.manager.freeSpace()

    /** How much RAM a model needs with the current settings, against what the phone has free. */
    fun estimate(sizeBytes: Long, info: app.yerel.core.GgufInfo?, s: AppSettings = settings.value, loadedId: String? = c.engine.loadedModelId): MemoryEstimate {
        val kv = info?.let { estimateKvBytes(it, s.nCtx, s.kvTypeK, s.kvTypeV, s.nBatch) } ?: 0L
        val overhead = (s.nBatch.toLong() * 512 * 1024).coerceAtLeast(96L shl 20) + (60L shl 20)
        // memory held by the currently loaded model would be freed first
        val freed = loadedId?.let { c.models.get(it)?.sizeBytes } ?: 0L
        val avail = _ram.value.availBytes + freed
        val need = sizeBytes + kv + overhead
        val fit = when {
            info == null && sizeBytes <= 0 -> Fit.UNKNOWN
            need < avail * 0.6 -> Fit.COMFORTABLE
            need < avail * 0.9 -> Fit.OK
            else -> Fit.TIGHT
        }
        return MemoryEstimate(sizeBytes, kv, overhead, avail, fit)
    }

    fun lookupHf(input: String) {
        val ref = HuggingFace.parse(input)
        if (ref == null) {
            _hf.value = HfLookup.Error("Bu bir Hugging Face model bağlantısı değil. Örnek: https://huggingface.co/Qwen/Qwen3-0.6B-GGUF")
            return
        }
        _hf.value = HfLookup.Loading(input)
        viewModelScope.launch {
            _hf.value = try {
                val files = c.manager.resolve(ref)
                val revision = when (ref) { is HfRef.Repo -> ref.revision; is HfRef.File -> ref.revision }
                val hint = (ref as? HfRef.Repo)?.quantHint
                HfLookup.Found(ref.repo, revision, files, if (ref is HfRef.File) files.firstOrNull() else HuggingFace.pick(files, hint))
            } catch (e: UserFacingException) {
                HfLookup.Error(e.message ?: "Hata")
            } catch (e: Exception) {
                HfLookup.Error("Beklenmeyen hata: ${e.message}")
            }
        }
    }

    fun clearHf() { _hf.value = HfLookup.Idle }

    fun download(repo: String, revision: String, file: HfFile, preset: SamplingPreset? = null) {
        viewModelScope.launch {
            val err = c.manager.download(repo, revision, file, preset)
            if (err != null) toast(err) else {
                toast("İndirme başladı: ${file.fileName}")
                clearHf()
                startPolling()
            }
        }
    }

    /** Catalog entries resolve their file from the repo at download time. */
    fun downloadCatalog(m: app.yerel.core.CatalogModel) {
        viewModelScope.launch {
            try {
                val files = c.manager.resolve(HfRef.Repo(m.repo))
                val f = HuggingFace.pick(files, m.quant) ?: throw UserFacingException("Uygun dosya bulunamadı")
                download(m.repo, "main", f, m.preset)
            } catch (e: UserFacingException) {
                toast(e.message ?: "Hata")
            }
        }
    }

    fun cancelDownload(id: Long) = c.manager.cancelDownload(id)
    fun failureText(p: app.yerel.data.PendingDownload) = c.manager.failureText(p)

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                val active = c.manager.poll()
                if (!active) break
                delay(700)
            }
            if (selectedModel() == null) models.value.firstOrNull()?.let { m -> c.settings.update { it.copy(lastModelId = m.id) } }
        }
    }

    fun onResume() {
        refreshRam()
        backgroundJob?.cancel()
        viewModelScope.launch { c.manager.scanFolder() }
        startPolling()
    }

    /** Frees model memory a while after the app leaves the screen, if the user asked for that. */
    fun onBackground() {
        if (!settings.value.unloadInBackground) return
        backgroundJob?.cancel()
        backgroundJob = viewModelScope.launch {
            delay(60_000)
            if (genJob?.isActive != true) c.engine.unload()
        }
    }

    fun onTrimMemory() {
        if (genJob?.isActive != true) viewModelScope.launch { c.engine.unload() }
    }

    fun inspectLocal(uri: Uri) {
        viewModelScope.launch {
            try {
                _pendingImport.value = c.manager.inspect(uri)
            } catch (e: UserFacingException) {
                toast(e.message ?: "Hata")
            }
        }
    }

    fun confirmImport(copy: Boolean) {
        val f = _pendingImport.value ?: return
        _pendingImport.value = null
        viewModelScope.launch {
            try {
                val e = c.manager.importLocal(f, copy)
                toast("Eklendi: ${e.name}")
                if (selectedModel() == null) selectModel(e.id, preload = false)
            } catch (e: UserFacingException) {
                toast(e.message ?: "Hata")
            }
        }
    }

    fun dismissImport() { _pendingImport.value = null }

    // ---- settings ---------------------------------------------------------------------------

    fun updateSettings(fn: (AppSettings) -> AppSettings) {
        val before = settings.value
        c.settings.update(fn)
        val after = settings.value
        if (before.contextKey() != after.contextKey() || before.loadKey() != after.loadKey()) scheduleReload()
    }

    /** Applies context changes to an already loaded model once the user stops dragging sliders. */
    private fun scheduleReload() {
        val loaded = c.engine.loadedModelId ?: return
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            delay(800)
            if (genJob?.isActive == true) return@launch
            val entry = c.models.get(loaded) ?: return@launch
            try {
                c.engine.ensureLoaded(entry, settings.value)
            } catch (e: EngineException) {
                toast("Ayarlar uygulanamadı: ${e.message}")
            }
        }
    }

    // ---- memory -----------------------------------------------------------------------------

    fun addMemory(text: String) { if (text.isNotBlank()) c.memory.add(text) }
    fun editMemory(id: String, text: String) = c.memory.edit(id, text)
    fun removeMemory(id: String) { c.memory.remove(id) }
    fun clearMemory() = c.memory.clear()

    // ---- chats ------------------------------------------------------------------------------

    fun newChat() {
        if (genJob?.isActive == true) stop()
        _conversation.value = Conversation(title = "")
    }

    fun openChat(id: String) {
        if (genJob?.isActive == true) stop()
        c.chats.load(id)?.let { _conversation.value = it }
    }

    fun deleteChat(id: String) {
        c.chats.delete(id)
        if (_conversation.value.id == id) newChat()
    }

    fun deleteAllChats() {
        newChat()
        c.chats.deleteAll()
    }

    fun renameChat(id: String, title: String) {
        if (title.isBlank()) return
        if (_conversation.value.id == id) {
            _conversation.update { it.copy(title = title.trim()) }
            persist()
        } else {
            c.chats.rename(id, title.trim())
        }
    }

    private fun persist() {
        val conv = _conversation.value
        if (conv.messages.isNotEmpty()) c.chats.save(conv)
    }

    val isGenerating get() = genJob?.isActive == true

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        if (isGenerating) {
            // a stopped reply may still be unwinding; send once it is done
            if (cancelled) viewModelScope.launch { genJob?.join(); send(t) }
            return
        }
        val msg = ChatMessage(role = Role.USER, content = t)
        _conversation.update { conv ->
            conv.copy(
                title = conv.title.ifBlank { titleFrom(t) },
                messages = conv.messages + msg,
                updatedAt = System.currentTimeMillis(),
            )
        }
        persist()
        runAgent()
    }

    /** Drops everything after the last user message and answers it again. */
    fun regenerate() {
        if (isGenerating) return
        val msgs = _conversation.value.messages
        val lastUser = msgs.indexOfLast { it.role == Role.USER }
        if (lastUser < 0) return
        _conversation.update { it.copy(messages = msgs.subList(0, lastUser + 1)) }
        persist()
        runAgent()
    }

    /** Replaces a user message and regenerates from there, like editing a prompt in ChatGPT. */
    fun editAndResend(messageId: String, text: String) {
        if (isGenerating || text.isBlank()) return
        val msgs = _conversation.value.messages
        val i = msgs.indexOfFirst { it.id == messageId }
        if (i < 0) return
        _conversation.update { it.copy(messages = msgs.subList(0, i) + msgs[i].copy(content = text.trim())) }
        persist()
        runAgent()
    }

    fun stop() {
        cancelled = true
        c.engine.cancel()
    }

    private fun titleFrom(t: String) = t.lineSequence().first().take(48).let { if (t.length > 48) "$it…" else it }

    private fun appendMessage(m: ChatMessage) {
        _conversation.update { it.copy(messages = it.messages + m, updatedAt = System.currentTimeMillis()) }
        persist()
    }

    private fun runAgent() {
        val entry = selectedModel()
        if (entry == null) {
            appendMessage(ChatMessage(role = Role.ASSISTANT, content = "", error = "Önce bir model seçin veya indirin (Modeller ekranı)."))
            return
        }
        cancelled = false
        val convId = _conversation.value.id
        _conversation.update { it.copy(modelId = entry.id) }
        genJob = viewModelScope.launch {
            _live.value = LiveReply(loadingModel = c.engine.loadedModelId != entry.id)
            val info = try {
                c.engine.ensureLoaded(entry, settings.value)
            } catch (e: EngineException) {
                _live.value = null
                appendMessage(ChatMessage(role = Role.ASSISTANT, content = "", error = "Model yüklenemedi: ${e.message}"))
                if (BuildConfig.DEBUG) Log.i("YerelTest", "REPLY_DONE ERROR: ${e.message}")
                return@launch
            }
            if (cancelled) {
                _live.value = null
                return@launch
            }
            _live.value = LiveReply()
            val s = settings.value
            val toolsOn = s.agentEnabled && (info?.supportsTools ?: entry.info?.templateTools ?: true)
            val enabled = ToolCatalog.all.map { it.name }
                .filter { it !in s.disabledTools && (s.memoryEnabled || it !in MEMORY_TOOLS) }
            val config = AgentConfig(
                systemPrompt = Prompt.systemPrompt(s.systemPrompt, if (s.memoryEnabled) memories.value else emptyList(), Date(), toolsOn && enabled.isNotEmpty()),
                toolsJson = if (toolsOn) ToolCatalog.toolsJson(enabled) else "[]",
                paramsJson = s.genParamsJson(entry.preset, s.thinking),
                maxSteps = if (toolsOn) s.maxAgentSteps else 1,
            )
            val history = _conversation.value.messages
            var lastReply = ""
            withContext(c.engine.dispatcher) {
                AgentRunner(c.engine, tools) { cancelled }.run(history, config) emit@{ ev ->
                    if (_conversation.value.id != convId) return@emit
                    when (ev) {
                        is AgentEvent.Partial -> _live.update {
                            (it ?: LiveReply()).copy(
                                content = ev.content, reasoning = ev.reasoning, toolCalls = ev.toolCalls,
                                nGen = ev.nGen, tps = ev.tps, activeTool = null,
                            )
                        }
                        is AgentEvent.Added -> {
                            if (ev.message.role == Role.ASSISTANT) lastReply = ev.message.content
                            appendMessage(ev.message)
                            _live.value = LiveReply()
                        }
                        is AgentEvent.ToolStarted -> _live.value = LiveReply(activeTool = ev.call)
                        is AgentEvent.Trimmed -> toast("Bağlam doldu: en eski ${ev.droppedMessages} mesaj modele gönderilmedi.")
                    }
                }
            }
            _live.value = null
            if (BuildConfig.DEBUG) {
                val last = _conversation.value.messages.lastOrNull()
                val stats = last?.stats?.let { " [${it.genTokens} tok, ${"%.1f".format(it.tokensPerSecond)} tok/s, prompt ${it.promptTokens}]" } ?: ""
                val text = last?.error?.let { "ERROR: $it" } ?: lastReply.replace('\n', ' ').take(400)
                val tools = _conversation.value.messages.filter { it.role == Role.TOOL }.joinToString(",") { it.toolName ?: "?" }
                Log.i("YerelTest", "REPLY_DONE $text$stats tools=[$tools]")
            }
        }
    }

    /** Debug builds only: lets CI drive a full chat turn through an intent. */
    fun runTestPrompt(prompt: String) {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            val m = models.first { it.isNotEmpty() }.first()
            selectModel(m.id, preload = false)
            newChat()
            Log.i("YerelTest", "PROMPT model=${m.name}")
            send(prompt)
        }
    }

    companion object {
        private val MEMORY_TOOLS = setOf("remember", "forget")
    }
}
