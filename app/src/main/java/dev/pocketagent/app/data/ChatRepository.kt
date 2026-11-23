package dev.pocketagent.app.data

import android.content.Context
import dev.pocketagent.app.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Bots, conversations and messages, kept as JSON files under `filesDir/chat/`:
 * `bots.json`, `conversations.json` and one `messages/<convId>.json` per conversation. Everything is written atomically
 * (temp file + rename). The index files load on a background thread at construction ([loaded] flips to true); a
 * conversation's messages load the first time they are asked for. Writes are debounced: while a bot streams, the file is
 * rewritten a few times per second at most, never per token; [flush] writes everything that is pending right now.
 *
 * A file that cannot be parsed is copied to `<name>.bad` (once per corruption), logged, and treated as empty.
 * Message ids and conversation ids are random hex; never rely on their order, use `createdAt`.
 */
class ChatRepository internal constructor(
    private val dir: File,
    private val scope: CoroutineScope,
    private val debounceMs: Long,
    /** Longer debounce while a message of the conversation is streaming (the whole file is rewritten each time). */
    private val streamingDebounceMs: Long = 2500L,
) {
    constructor(context: Context) : this(File(context.filesDir, "chat"), CoroutineScope(SupervisorJob() + Dispatchers.IO), 400L)

    private val lock = Any()      // guards the in-memory state below
    private val ioLock = Any()    // serialises file writes

    private val _bots = MutableStateFlow<List<Bot>>(emptyList())
    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    private val _loaded = MutableStateFlow(false)

    /** All bots; the built-in Hermes bot first. */
    val bots: StateFlow<List<Bot>> = _bots
    /** Pinned first, then newest activity first. */
    val conversations: StateFlow<List<Conversation>> = _conversations
    /** True once bots.json and conversations.json have been read (the lists above are empty until then). */
    val loaded: StateFlow<Boolean> = _loaded

    private class Slot { val flow = MutableStateFlow<List<Message>>(emptyList()); @Volatile var loaded = false }
    private val slots = HashMap<String, Slot>()          // guarded by lock

    private val dirtyBots = java.util.concurrent.atomic.AtomicBoolean(false)
    private val dirtyConvs = java.util.concurrent.atomic.AtomicBoolean(false)
    private val dirtyMessages = LinkedHashSet<String>()  // guarded by lock
    private val deletedMessageFiles = LinkedHashSet<String>() // guarded by lock
    private var pending: Job? = null                      // guarded by lock
    /** Files that exist but could not be read (I/O error): never written over, so a transient error cannot wipe real data. */
    private val protectedFiles: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private fun isProtected(f: File) = f.absolutePath in protectedFiles

    private val messagesDir get() = File(dir, "messages")
    private fun messageFile(convId: String) = File(messagesDir, safeName(convId) + ".json")
    private fun safeName(id: String) = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifEmpty { "_" }

    init {
        scope.launch { loadIndex() }
    }

    // ---- loading ----------------------------------------------------------------------------------------

    private fun loadIndex() {
        try {
            val botsFile = File(dir, "bots.json")
            val firstRun = !botsFile.exists()
            val botsRead = if (firstRun) null else readList(botsFile, "bots") { ChatJson.decodeBots(it) }
            var bots = if (firstRun) BotPresets.firstRun() else botsRead ?: emptyList()
            // Starter bots the owner never edited follow improved preset texts.
            val upgraded = bots.map { BotPresets.upgradeLegacy(it) }
            if (upgraded != bots) { bots = upgraded; dirtyBots.set(true) }
            if (bots.none { it.id == BotPresets.HERMES_ID }) bots = listOf(BotPresets.hermes.copy(createdAt = System.currentTimeMillis())) + bots
            bots = bots.distinctBy { it.id }.sortedByDescending { it.id == BotPresets.HERMES_ID } // stable: Hermes first
            val convsFile = File(dir, "conversations.json")
            val convsRead = readList(convsFile, "conversations") { ChatJson.decodeConversations(it) }
            val convs = convsRead ?: emptyList()
            // A corrupt file was copied to .bad; rewrite a valid (empty) one so the next start does not trip over it again.
            if (!firstRun && botsRead == null && !isProtected(botsFile)) dirtyBots.set(true)
            if (convsFile.exists() && convsRead == null && !isProtected(convsFile)) dirtyConvs.set(true)
            synchronized(lock) {
                // Whatever the UI created before the load finished (not possible through the UI, which waits for [loaded], but safe).
                val earlyBots = _bots.value.filter { e -> bots.none { it.id == e.id } }
                _bots.value = bots + earlyBots
                val earlyConvs = _conversations.value.filter { e -> convs.none { it.id == e.id } }
                _conversations.value = sortConvs(convs + earlyConvs)
            }
            if (firstRun) dirtyBots.set(true)
            if (dirtyBots.get() || dirtyConvs.get()) schedule()
        } catch (e: Throwable) {
            AppLog.e("Chat", "loading the chat index failed", e)
        } finally {
            _loaded.value = true
        }
    }

    /**
     * Parses [file] with [decode]; null when absent. A corrupt file is copied to `.bad` and also returns null. A file that
     * exists but cannot be read (I/O error) returns null too, is logged, and is protected: nothing is ever written over it.
     */
    private fun <T> readList(file: File, what: String, decode: (String) -> List<T>): List<T>? {
        val text = try { Fs.readTextStrict(file) ?: return null } catch (e: java.io.IOException) {
            protectedFiles += file.absolutePath
            AppLog.e("Chat", "$what file ${file.name} exists but cannot be read; leaving it untouched and not writing to it this run", e)
            return null
        }
        return try {
            decode(text)
        } catch (e: Exception) {
            AppLog.e("Chat", "$what file ${file.name} is corrupt (${e.javaClass.simpleName}); keeping a .bad copy and starting empty", e)
            try { Files.copy(file.toPath(), File(file.absoluteFile.parentFile, file.name + ".bad").toPath(), StandardCopyOption.REPLACE_EXISTING) } catch (_: Exception) { }
            null
        }
    }

    private fun slot(convId: String): Slot = synchronized(lock) { slots.getOrPut(convId) { Slot() } }

    /** Loads a conversation's messages from disk unless that already happened. Blocking: call off the main thread. */
    private fun ensureLoaded(convId: String) {
        val s = slot(convId)
        if (s.loaded) return
        val list = readList(messageFile(convId), "messages") { ChatJson.decodeMessages(it, convId) } ?: emptyList()
        synchronized(lock) {
            if (s.loaded) return
            val cur = s.flow.value
            s.flow.value = if (cur.isEmpty()) list else (list.filter { l -> cur.none { it.id == l.id } } + cur)
            s.loaded = true
        }
    }

    // ---- bots -----------------------------------------------------------------------------------------------

    fun bot(id: String): Bot? = _bots.value.firstOrNull { it.id == id }
    fun conversation(id: String): Conversation? = _conversations.value.firstOrNull { it.id == id }

    fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(16)

    /** Adds or replaces (by id) a bot. The Hermes bot keeps its built-in flag. */
    fun saveBot(bot: Bot) {
        synchronized(lock) {
            val b = if (bot.id == BotPresets.HERMES_ID) bot.copy(builtIn = true) else bot
            val cur = _bots.value
            _bots.value = if (cur.any { it.id == b.id }) cur.map { if (it.id == b.id) b else it } else cur + b
            dirtyBots.set(true)
        }
        schedule()
    }

    /** No-op for the built-in Hermes bot. Conversations that include the bot keep working: the bot is simply skipped. */
    fun deleteBot(id: String) {
        if (id == BotPresets.HERMES_ID) return
        synchronized(lock) {
            _bots.value = _bots.value.filter { it.id != id }
            dirtyBots.set(true)
        }
        schedule()
    }

    // ---- conversations ----------------------------------------------------------------------------------------

    fun createConversation(botIds: List<String>, title: String? = null): Conversation {
        val now = System.currentTimeMillis()
        val c = Conversation(newId(), title?.trim().orEmpty(), botIds.distinct(), now, now)
        synchronized(lock) {
            _conversations.value = sortConvs(_conversations.value + c)
            slots.getOrPut(c.id) { Slot() }.loaded = true   // brand new: nothing on disk to read
            dirtyConvs.set(true)
        }
        schedule()
        return c
    }

    fun renameConversation(id: String, title: String) = updateConversation(id, touch = false) { it.copy(title = title.trim()) }
    fun setPinned(id: String, pinned: Boolean) = updateConversation(id, touch = false) { it.copy(pinned = pinned) }
    fun setGroupMode(id: String, mode: GroupMode) = updateConversation(id, touch = false) { it.copy(groupMode = mode) }
    fun setConversationBots(id: String, botIds: List<String>) = updateConversation(id, touch = false) { it.copy(botIds = botIds.distinct()) }

    /** Applies [change] to a conversation; [touch] also bumps `updatedAt`. */
    fun updateConversation(id: String, touch: Boolean = false, change: (Conversation) -> Conversation) {
        var changed = false
        synchronized(lock) {
            val cur = _conversations.value
            if (cur.none { it.id == id }) return
            val now = System.currentTimeMillis()
            _conversations.value = sortConvs(cur.map { c ->
                if (c.id != id) c else change(c).let { n -> (if (touch) n.copy(updatedAt = now) else n).also { changed = changed || it != c } }
            })
            if (changed) dirtyConvs.set(true)
        }
        if (changed) schedule()
    }

    fun deleteConversation(id: String) {
        synchronized(lock) {
            _conversations.value = _conversations.value.filter { it.id != id }
            slots.remove(id)
            dirtyMessages.remove(id)
            deletedMessageFiles += id
            dirtyConvs.set(true)
        }
        schedule()
    }

    private fun sortConvs(l: List<Conversation>) =
        l.sortedWith(compareByDescending<Conversation> { it.pinned }.thenByDescending { it.updatedAt })

    // ---- messages ---------------------------------------------------------------------------------------------

    /** Live list of a conversation's messages, oldest first. Empty for a moment while the file loads. */
    fun messages(convId: String): StateFlow<List<Message>> {
        val s = slot(convId)
        if (!s.loaded) scope.launch { ensureLoaded(convId) }
        return s.flow
    }

    /** Current messages, loading them first if needed. Blocking disk read: call off the main thread. */
    fun messagesSnapshot(convId: String): List<Message> {
        ensureLoaded(convId)
        return slot(convId).flow.value
    }

    /** Appends a message and, for USER/BOT messages with text, bumps the conversation's `updatedAt` and `lastPreview`. */
    fun addMessage(m: Message) {
        ensureLoaded(m.convId)
        synchronized(lock) {
            val s = slots[m.convId] ?: return
            s.flow.value = s.flow.value + m
            dirtyMessages += m.convId
        }
        if ((m.role == Role.USER || m.role == Role.BOT) && m.text.isNotBlank()) {
            updateConversation(m.convId, touch = true) { it.copy(lastPreview = Transcript.preview(m.text)) }
        } else updateConversation(m.convId, touch = true) { it }
        schedule()
    }

    /** Replaces message [id] with `change(old)`. No-op when it does not exist. Cheap enough to call a few times a second. */
    fun updateMessage(convId: String, id: String, change: (Message) -> Message) {
        ensureLoaded(convId)
        synchronized(lock) {
            val s = slots[convId] ?: return
            val cur = s.flow.value
            val i = cur.indexOfFirst { it.id == id }
            if (i < 0) return
            val n = change(cur[i])
            if (n == cur[i]) return
            s.flow.value = cur.toMutableList().also { it[i] = n }
            dirtyMessages += convId
        }
        schedule()
    }

    fun deleteMessage(convId: String, messageId: String) {
        ensureLoaded(convId)
        synchronized(lock) {
            val s = slots[convId] ?: return
            val cur = s.flow.value
            if (cur.none { it.id == messageId }) return
            s.flow.value = cur.filter { it.id != messageId }
            dirtyMessages += convId
        }
        refreshPreview(convId)
        schedule()
    }

    /** Deletes every message after [messageId] (the message itself stays). */
    fun truncateAfter(convId: String, messageId: String) {
        ensureLoaded(convId)
        synchronized(lock) {
            val s = slots[convId] ?: return
            val cur = s.flow.value
            val i = cur.indexOfFirst { it.id == messageId }
            if (i < 0 || i == cur.size - 1) return
            s.flow.value = cur.subList(0, i + 1).toList()
            dirtyMessages += convId
        }
        refreshPreview(convId)
        schedule()
    }

    fun clearConversation(convId: String) {
        ensureLoaded(convId)
        synchronized(lock) {
            val s = slots[convId] ?: return
            s.flow.value = emptyList()
            dirtyMessages += convId
        }
        updateConversation(convId, touch = false) { it.copy(lastPreview = "") }
        schedule()
    }

    /** Recomputes `lastPreview` from the newest USER/BOT message with text (call after deleting or when a stream ends). */
    fun refreshPreview(convId: String) {
        val last = slot(convId).flow.value.lastOrNull { (it.role == Role.USER || it.role == Role.BOT) && it.text.isNotBlank() }
        val p = last?.let { Transcript.preview(it.text) } ?: ""
        updateConversation(convId, touch = false) { if (it.lastPreview == p) it else it.copy(lastPreview = p) }
    }

    /**
     * Messages that contain [query] (case-insensitive), newest conversations first, at most 200 hits. A conversation whose
     * title matches contributes its last message. Reads message files that are not in memory without keeping them.
     * Blocking disk reads: call off the main thread.
     */
    fun search(query: String): List<Pair<Conversation, Message>> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<Pair<Conversation, Message>>()
        for (c in _conversations.value) {
            val s = synchronized(lock) { slots[c.id] }
            val msgs = if (s != null && s.loaded) s.flow.value
            else readList(messageFile(c.id), "messages") { ChatJson.decodeMessages(it, c.id) } ?: emptyList()
            val hits = msgs.filter { (it.role == Role.USER || it.role == Role.BOT || it.role == Role.TOOL) && it.text.contains(q, ignoreCase = true) }
            if (hits.isNotEmpty()) hits.forEach { out += c to it }
            else if (c.title.contains(q, ignoreCase = true)) msgs.lastOrNull { it.role == Role.USER || it.role == Role.BOT }?.let { out += c to it }
            if (out.size >= 200) break
        }
        return out.take(200)
    }

    // ---- persistence ---------------------------------------------------------------------------------------------

    private fun schedule() {
        synchronized(lock) {
            if (pending != null) return
            val streaming = dirtyMessages.any { id -> slots[id]?.flow?.value?.any { it.streaming } == true }
            val wait = if (streaming) maxOf(debounceMs, streamingDebounceMs) else debounceMs
            pending = scope.launch {
                delay(wait)
                synchronized(lock) { pending = null }
                try { flush() } catch (e: Throwable) { AppLog.e("Chat", "background write failed", e) }
            }
        }
    }

    /** Writes everything that changed since the last write, now. Blocking: call off the main thread. */
    fun flush() {
        synchronized(ioLock) {
            val snap = synchronized(lock) {
                Snapshot(
                    bots = if (dirtyBots.getAndSet(false)) _bots.value else null,
                    convs = if (dirtyConvs.getAndSet(false)) _conversations.value else null,
                    messages = LinkedHashMap<String, List<Message>>().also { m ->
                        for (id in dirtyMessages) slots[id]?.takeIf { it.loaded }?.let { m[id] = it.flow.value }
                    },
                    deletes = deletedMessageFiles.toList(),
                ).also { dirtyMessages.clear(); deletedMessageFiles.clear() }
            }
            snap.bots?.let { b -> writeSafely(File(dir, "bots.json")) { ChatJson.encodeBots(b) } }
            snap.convs?.let { c -> writeSafely(File(dir, "conversations.json")) { ChatJson.encodeConversations(c) } }
            for ((id, list) in snap.messages) writeSafely(messageFile(id), sync = false) { ChatJson.encodeMessages(list) }
            for (id in snap.deletes) try { messageFile(id).delete() } catch (_: Exception) { }
        }
    }

    private class Snapshot(val bots: List<Bot>?, val convs: List<Conversation>?, val messages: Map<String, List<Message>>, val deletes: List<String>)

    private fun writeSafely(f: File, sync: Boolean = true, text: () -> String) {
        if (isProtected(f)) { AppLog.w("Chat", "not writing ${f.name}: it could not be read earlier and is left untouched"); return }
        try { Fs.writeAtomic(f, text(), sync) } catch (e: Exception) { AppLog.e("Chat", "could not write ${f.name}", e) }
    }
}
