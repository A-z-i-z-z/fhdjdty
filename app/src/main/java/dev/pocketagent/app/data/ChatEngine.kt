package dev.pocketagent.app.data

import dev.pocketagent.app.api.ChatEvent
import dev.pocketagent.app.api.ChatMessage
import dev.pocketagent.app.api.ChatOutcome
import dev.pocketagent.app.api.ChatRequest
import dev.pocketagent.app.api.HermesApi
import dev.pocketagent.app.api.HttpResult
import dev.pocketagent.app.api.StreamHandle
import dev.pocketagent.app.diag.Diag
import dev.pocketagent.app.runtime.WakeLocks
import dev.pocketagent.app.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Events emitted by [ChatEngine] for sound cues, auto-speak, and live UI feedback. */
sealed interface EngineEvent {
    /** A bot begins to answer (the request is about to go out). */
    data class TurnStarted(val convId: String, val botId: String) : EngineEvent
    data class TurnCompleted(
        val convId: String, val messageId: String, val botId: String, val text: String,
        val tokensIn: Long = 0L, val tokensOut: Long = 0L,
    ) : EngineEvent
    data class ToolFinished(val convId: String, val toolName: String) : EngineEvent
    data class MemorySaved(val convId: String) : EngineEvent
    data class BotForged(val convId: String, val botIds: List<String>) : EngineEvent
    data class ApprovalRequested(val convId: String) : EngineEvent
    data class TurnFailed(val convId: String) : EngineEvent
}

/** The transport the engine talks through; [HermesApi.streamCompletion] in the app, a fake in tests. */
internal fun interface ChatBackend {
    fun stream(request: ChatRequest, handle: StreamHandle, onEvent: (ChatEvent) -> Unit): ChatOutcome
}

/** Hermes is waiting for permission to run a flagged command in conversation [convId]; answer with [ChatEngine.resolveApproval]. */
data class PendingApproval(
    val convId: String, val botId: String, val runId: String, val requestId: String,
    val command: String, val description: String, val choices: List<String>,
)

/** Provider-specific spelling of a model id: the `vertex` provider wants "google/<model>", the `gemini` provider the bare id. */
internal object ModelIds {
    private enum class Family { GOOGLE, CLAUDE, OPENAI, OTHER }

    private fun family(model: String): Family {
        val m = model.trim().lowercase()
        val bare = m.substringAfterLast('/')
        val vendor = if (m.contains('/')) m.substringBefore('/') else ""
        return when {
            vendor == "google" || bare.startsWith("gemini") || bare.startsWith("gemma") -> Family.GOOGLE
            vendor == "anthropic" || bare.startsWith("claude") -> Family.CLAUDE
            vendor == "openai" || bare.startsWith("gpt") || bare.startsWith("chatgpt") ||
                Regex("o[0-9].*").matches(bare) -> Family.OPENAI
            else -> Family.OTHER
        }
    }

    fun normalize(provider: String, model: String): String {
        val m = model.trim()
        if (m.isEmpty()) return ""
        return when (provider) {
            "vertex" -> if (m.contains('/')) m else "google/$m"
            "gemini" -> m.removePrefix("google/")
            // OpenRouter wants vendor/model: a bare id saved while another provider was active gets its vendor.
            "openrouter" -> if (m.contains('/')) m else when (family(m)) {
                Family.GOOGLE -> "google/$m"
                Family.CLAUDE -> "anthropic/$m"
                Family.OPENAI -> "openai/$m"
                Family.OTHER -> m
            }
            else -> m
        }
    }

    /**
     * False when [model] clearly belongs to another provider's family (a Gemini id kept on a bot after the owner switched to
     * Anthropic, a Claude id on the Gemini provider ...). Such an override is not sent: the provider would answer
     * "model not found". Unknown ids and the `custom` endpoint (it can host anything) always fit.
     */
    fun fits(provider: String, model: String): Boolean {
        if (model.isBlank()) return true
        val f = family(model)
        return when (provider) {
            "gemini", "vertex" -> f != Family.CLAUDE && f != Family.OPENAI
            "anthropic" -> f != Family.GOOGLE && f != Family.OPENAI
            "openai-api" -> f != Family.GOOGLE && f != Family.CLAUDE
            else -> true
        }
    }
}

/**
 * Runs the conversations: builds each bot's request from the transcript (see [Transcript]), streams Hermes's answer into
 * BOT messages (one BOT message per stretch of text; a tool call closes the current one and appears as a TOOL message that
 * is updated in place), records errors as ERROR messages and keeps the CPU awake while it works.
 *
 * One generation at a time per conversation (a second `send` while busy is ignored: the UI should disable input);
 * different conversations run concurrently (Hermes allows 10 concurrent runs by default). In a group chat the bots answer one
 * after the other and a failed or stopped bot ends the round.
 */
class ChatEngine internal constructor(
    private val repo: ChatRepository,
    private val backend: ChatBackend,
    private val approve: (runId: String, choice: String, requestId: String?) -> HttpResult,
    private val acquireWake: (String) -> Unit,
    private val releaseWake: (String) -> Unit,
    /** Hermes provider name of the app's current setup ("gemini" or "vertex"): bots with their own model use it. */
    private val provider: () -> String,
    /** False while Hermes is not up; a send then fails fast with an ERROR message instead of a connection error. */
    private val isReady: () -> Boolean,
    private val scope: CoroutineScope,
    private val imageLoader: (Attachment) -> String? = { null },
    /** True when a message sent while Hermes is asleep may light the fire (wake-on-send is on and the runtime is installed). */
    private val canWake: () -> Boolean = { false },
    /** Starts Hermes if needed and blocks until it is Running; false when it could not (or the run was stopped meanwhile). */
    private val awaitReady: (cancelled: () -> Boolean) -> Boolean = { isReady() },
    /** Token statistics and the daily budget; null in tests that do not care. */
    private val usage: UsageStore? = null,
) {
    constructor(
        repo: ChatRepository, api: HermesApi, wakeLocks: WakeLocks,
        provider: () -> String, isReady: () -> Boolean,
        imageLoader: (Attachment) -> String? = { null },
        canWake: () -> Boolean = { false },
        awaitReady: (cancelled: () -> Boolean) -> Boolean = { isReady() },
        usage: UsageStore? = null,
    ) : this(
        repo, ChatBackend { req, h, ev -> api.streamCompletion(req, h, ev) },
        { runId, choice, requestId -> api.resolveApproval(runId, choice, requestId) },
        { why -> wakeLocks.acquire(why, 30 * 60_000L) }, { why -> wakeLocks.release(why) },
        provider, isReady, CoroutineScope(SupervisorJob() + Dispatchers.IO), imageLoader, canWake, awaitReady, usage,
    )

    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    private val _speaking = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _status = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _approvals = MutableStateFlow<Map<String, PendingApproval>>(emptyMap())
    private val _memoryUpdates = MutableStateFlow(0)
    private val memoryCounter = AtomicInteger(0)
    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 64)
    @Volatile private var _lastActivityAt = System.currentTimeMillis()
    /** The day (see [UsageStore.dayOf]) for which the "budget reached" note was already written. */
    private val warnedDay = java.util.concurrent.atomic.AtomicReference<String?>(null)

    /** convIds generating now. */
    val busy: StateFlow<Set<String>> = _busy
    /** convId -> botId that is answering right now (drives the typing indicator). */
    val speaking: StateFlow<Map<String, String>> = _speaking
    /** convId -> a short Hermes status text (provider wait, retry countdown, fallback switch) while the stream is quiet. */
    val status: StateFlow<Map<String, String>> = _status
    /** convId -> the command Hermes wants permission for. The turn waits until [resolveApproval] (or Hermes's own timeout). */
    val approvals: StateFlow<Map<String, PendingApproval>> = _approvals
    /** Increments whenever Hermes finishes a `memory` tool call in any chat (lets the inspector auto-reload). */
    val memoryUpdates: StateFlow<Int> = _memoryUpdates
    /** High-level events (completion chime, tool finished, bot forged, memory updated, errors). */
    val events: SharedFlow<EngineEvent> = _events.asSharedFlow()

    private class Run {
        @Volatile var stop = false
        @Volatile var handle: StreamHandle? = null
    }
    private val runs = ConcurrentHashMap<String, Run>()
    /** "provider|model" pairs already reported as "model of another provider" in this process (one NOTE each). */
    private val warnedForeignModels: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** True while [convId] is generating (the same fact as `convId in busy.value`, without reading a flow). */
    fun isBusy(convId: String): Boolean = runs.containsKey(convId)

    /** Wall clock of the last run start or end (the process start until the first run); the idle stop measures from here. */
    val lastActivityAt: Long get() = _lastActivityAt

    /** True when a request can be accepted now: Hermes is running, or sending may light the fire. */
    private fun accepting(): Boolean = isReady() || canWake()

    // ---- public API ------------------------------------------------------------------------------------------

    /**
     * Adds the user's message and lets the right bot(s) answer. [onlyBotIds] (group chats) restricts who answers.
     * Returns true when accepted; false when ignored (empty text, the conversation is already generating, or Hermes is not
     * running and cannot be woken): nothing was added to the chat then, so the caller should keep the user's text.
     * While Hermes is asleep and wake-on-send is allowed the message is accepted and shown, Hermes is started
     * ([STATUS_WAKING] in [status]) and the answer follows once it is up.
     */
    fun send(
        convId: String,
        text: String,
        onlyBotIds: List<String> = emptyList(),
        attachments: List<Attachment> = emptyList(),
    ): Boolean {
        val clean = text.trim()
        if ((clean.isEmpty() && attachments.isEmpty()) || !accepting()) return false
        return startRun(convId) {
            val conv = repo.conversation(convId) ?: return@startRun
            repo.addMessage(newMessage(convId, Role.USER, null, clean, attachments = attachments))
            val summary = Transcript.messageSummary(clean, attachments)
            if (conv.title.isBlank() && summary.isNotBlank()) repo.renameConversation(convId, Transcript.autoTitle(summary))
            val targets = Transcript.selectBots(conv, convBots(conv), clean, onlyBotIds)
            if (targets.isEmpty()) { addIssue(convId, Role.ERROR, null, "This chat has no bots (they were deleted). Add a bot to the chat first."); return@startRun }
            runTurn(it, convId, targets.map { b -> b.id })
        }
    }

    /** Runs a multi-pass refinement loop ([passes] clamped to 2..5) over [prompt], stopping cleanly on cancellation. */
    fun runLoop(
        convId: String,
        prompt: String,
        passes: Int = 3,
        attachments: List<Attachment> = emptyList(),
        onlyBotIds: List<String> = emptyList(),
    ): Boolean {
        val clean = prompt.trim()
        if ((clean.isEmpty() && attachments.isEmpty()) || !accepting()) return false
        val totalPasses = passes.coerceIn(2, 5)
        return startRun(convId) { run ->
            val conv = repo.conversation(convId) ?: return@startRun
            repo.addMessage(newMessage(convId, Role.USER, null, clean, attachments = attachments))
            val summary = Transcript.messageSummary(clean, attachments)
            if (conv.title.isBlank() && summary.isNotBlank()) repo.renameConversation(convId, Transcript.autoTitle(summary))
            val targets = Transcript.selectBots(conv, convBots(conv), clean, onlyBotIds)
            if (targets.isEmpty()) { addIssue(convId, Role.ERROR, null, "This chat has no bots (they were deleted). Add a bot to the chat first."); return@startRun }
            val ids = targets.map { it.id }
            addIssue(convId, Role.NOTE, null, "Loop mode: pass 1/$totalPasses")
            if (!runTurn(run, convId, ids)) return@startRun
            for (pass in 2..totalPasses) {
                if (run.stop) break
                addIssue(convId, Role.NOTE, null, "Loop mode: pass $pass/$totalPasses — refining…")
                val refinePrompt =
                    "Critically review your previous response in this conversation (pass $pass of $totalPasses). " +
                        "Fix any gaps, unclear reasoning, or missing edge cases and present an improved, tighter final version."
                if (!runTurn(run, convId, ids, ephemeralUserTurn = refinePrompt)) break
            }
        }
    }

    /** Drops everything after the last user message and answers it again (the same bots that answered, or the default ones). False when ignored (busy or Hermes not running). */
    fun regenerateLast(convId: String): Boolean {
        if (!accepting()) return false
        return startRun(convId) { run ->
            val msgs = repo.messagesSnapshot(convId)
            val idx = msgs.indexOfLast { it.role == Role.USER }
            if (idx < 0) return@startRun
            val user = msgs[idx]
            val involved = msgs.drop(idx + 1).filter { it.role == Role.BOT || it.role == Role.ERROR || it.role == Role.TOOL }
                .mapNotNull { it.botId }.toSet()
            repo.truncateAfter(convId, user.id)
            val conv = repo.conversation(convId) ?: return@startRun
            val bots = convBots(conv)
            val targets = if (conv.isGroup && involved.isNotEmpty() && bots.any { it.id in involved }) bots.filter { it.id in involved }
            else Transcript.selectBots(conv, bots, user.text)
            if (targets.isEmpty()) { addIssue(convId, Role.ERROR, null, "This chat has no bots (they were deleted). Add a bot to the chat first."); return@startRun }
            runTurn(run, convId, targets.map { it.id })
        }
    }

    /** Replaces the text of a user message, deletes everything after it and answers again. False when ignored (empty text, busy or Hermes not running). */
    fun editAndResend(convId: String, messageId: String, newText: String): Boolean {
        val clean = newText.trim()
        if (clean.isEmpty() || !accepting()) return false
        return startRun(convId) { run ->
            val msgs = repo.messagesSnapshot(convId)
            val target = msgs.firstOrNull { it.id == messageId && it.role == Role.USER } ?: return@startRun
            val isFirst = msgs.firstOrNull { it.role == Role.USER }?.id == messageId
            val oldAuto = Transcript.autoTitle(target.text)
            repo.truncateAfter(convId, messageId)
            repo.updateMessage(convId, messageId) { it.copy(text = clean) }
            val conv = repo.conversation(convId) ?: return@startRun
            // The title follows the first message unless the user renamed the conversation.
            if (isFirst && (conv.title.isBlank() || conv.title == oldAuto)) repo.renameConversation(convId, Transcript.autoTitle(clean))
            val targets = Transcript.selectBots(conv, convBots(conv), clean)
            if (targets.isEmpty()) { addIssue(convId, Role.ERROR, null, "This chat has no bots (they were deleted). Add a bot to the chat first."); return@startRun }
            runTurn(run, convId, targets.map { it.id })
        }
    }

    /** Stops the running generation of [convId]; text received so far stays. */
    fun stop(convId: String) {
        val r = runs[convId] ?: return
        r.stop = true
        r.handle?.cancel()
    }

    /** Answers the pending approval of [convId]: once, session, always or deny (see [PendingApproval.choices]). */
    fun resolveApproval(convId: String, choice: String) {
        val p = _approvals.value[convId] ?: return
        _approvals.update { it - convId }
        scope.launch {
            val r = try { approve(p.runId, choice, p.requestId.ifEmpty { null }) }
            catch (e: Throwable) { HttpResult(-1, "", 0, "${e.javaClass.simpleName}: ${e.message}") }
            if (!r.ok) {
                AppLog.w("Chat", "approval answer failed: ${r.describe().take(200)}")
                // The card must not vanish silently: put it back (unless the turn has moved on) and say what happened.
                if (runs.containsKey(convId)) _approvals.update { if (it.containsKey(convId)) it else it + (convId to p) }
                try { addIssue(convId, Role.ERROR, p.botId, Diag.redactor.redact("Could not send your answer to Hermes: ${r.describe()}")) } catch (_: Throwable) { }
            }
        }
    }

    // ---- running ---------------------------------------------------------------------------------------------------

    private fun startRun(convId: String, body: (Run) -> Unit): Boolean {
        val run = Run()
        if (runs.putIfAbsent(convId, run) != null) { AppLog.w("Chat", "conversation $convId is already generating; request ignored"); return false }
        _busy.update { it + convId }
        _lastActivityAt = System.currentTimeMillis()
        scope.launch {
            acquireWake("chat")
            try {
                body(run)
            } catch (e: Throwable) {
                AppLog.e("Chat", "generation failed", e)
                try { addIssue(convId, Role.ERROR, null, Diag.redactor.redact("Internal error: ${e.javaClass.simpleName}: ${e.message}")) } catch (_: Throwable) { }
            } finally {
                try { repo.refreshPreview(convId); repo.flush() } catch (e: Throwable) { AppLog.e("Chat", "final flush failed", e) }
                _speaking.update { it - convId }; _status.update { it - convId }; _approvals.update { it - convId }
                runs.remove(convId)
                _busy.update { it - convId }
                _lastActivityAt = System.currentTimeMillis()
                releaseWake("chat")
            }
        }
        return true
    }

    private fun convBots(conv: Conversation): List<Bot> = conv.botIds.mapNotNull { repo.bot(it) }

    private fun runTurn(run: Run, convId: String, botIds: List<String>, ephemeralUserTurn: String? = null): Boolean {
        for (id in botIds) {
            if (run.stop) return false
            if (!generate(run, convId, id, ephemeralUserTurn)) return false
        }
        return true
    }

    private fun newMessage(
        convId: String,
        role: Role,
        botId: String?,
        text: String,
        streaming: Boolean = false,
        attachments: List<Attachment> = emptyList(),
    ) = Message(repo.newId(), convId, role, botId, text, System.currentTimeMillis(), streaming = streaming, attachments = attachments)

    /** Appends a local system note (used by slash commands and local helpers). */
    fun addLocalNote(convId: String, text: String) {
        addIssue(convId, Role.NOTE, null, text)
    }

    /** Notifies listeners (e.g. HermesScreen) that on-disk memory files were updated locally. */
    fun notifyMemoryUpdated() {
        _memoryUpdates.value = memoryCounter.incrementAndGet()
    }

    private fun addIssue(convId: String, role: Role, botId: String?, text: String) {
        repo.addMessage(newMessage(convId, role, botId, text.take(4000)))
    }

    /**
     * True when Hermes is up. When it is not and waking is allowed, starts it and waits ([STATUS_WAKING] shows meanwhile).
     * False when the run was stopped (silently) or Hermes is not running and could not be started (an ERROR message says so).
     */
    private fun ensureReady(run: Run, convId: String, botId: String): Boolean {
        if (isReady()) return true
        if (!canWake()) {
            addIssue(convId, Role.ERROR, botId, "Hermes is not running. Start it from the home screen, wait until it says Running, then try again.")
            return false
        }
        _status.update { it + (convId to STATUS_WAKING) }
        val up = try {
            awaitReady { run.stop }
        } catch (e: Throwable) {
            AppLog.e("Chat", "waking Hermes failed", e)
            false
        } finally {
            _status.update { if (it[convId] == STATUS_WAKING) it - convId else it }
        }
        if (run.stop) return false
        if (!up || !isReady()) {
            addIssue(convId, Role.ERROR, botId, "Hermes could not start. Open the home screen to see why.")
            _events.tryEmit(EngineEvent.TurnFailed(convId))
            return false
        }
        return true
    }

    /** One bot's answer. Returns true when the stream completed cleanly (the next bot of a group may go on). */
    private fun generate(run: Run, convId: String, botId: String, ephemeralUserTurn: String? = null): Boolean {
        val conv = repo.conversation(convId) ?: return false
        val bot = repo.bot(botId)
        if (bot == null) { addIssue(convId, Role.ERROR, null, "Bot not found (it was deleted)."); return false }
        if (run.stop) return false
        // The daily budget first: a blocked turn must not even light the fire.
        usage?.let { u ->
            if (u.budget.value.mode == BudgetMode.BLOCK && u.overBudget()) {
                val limit = u.budget.value.dailyTokens
                addIssue(convId, Role.ERROR, bot.id, "The daily token budget ($limit tokens) is reached, so this message was not sent to the model. " +
                    "Raise or turn off the limit under Settings > Usage, or try again tomorrow.")
                _events.tryEmit(EngineEvent.TurnFailed(convId))
                return false
            }
        }
        if (!ensureReady(run, convId, bot.id)) return false
        val history = repo.messagesSnapshot(convId)
        val providerName = provider()
        // Precedence: per-conversation override -> bot override -> Hermes global default
        val chosenModel = conv.modelOverride.ifBlank { bot.model }
        val effectiveEffort = conv.effortOverride.ifBlank { bot.effort }
        // An override saved under another provider (e.g. a Gemini id after switching to Anthropic) would only make the
        // provider answer "model not found": use Hermes's default instead and say so once.
        val modelFits = ModelIds.fits(providerName, chosenModel)
        val effectiveModel = if (modelFits) chosenModel else ""
        if (!modelFits && warnedForeignModels.add("$providerName|$chosenModel")) {
            addIssue(convId, Role.NOTE, bot.id, "The model \"${chosenModel.trim()}\" set for this chat or bot belongs to another provider, " +
                "so the default model of the current provider is used instead. Change it in the chat controls or the bot editor.")
        }
        val hasModel = effectiveModel.isNotBlank()
        var built = Transcript.build(conv, bot, repo.bots.value, history, imageLoader)
        if (!ephemeralUserTurn.isNullOrBlank()) {
            built = built + ChatMessage("user", ephemeralUserTurn)
        }
        val modelId = if (hasModel) ModelIds.normalize(providerName, effectiveModel) else ""
        val req = ChatRequest(
            messages = built,
            model = modelId,
            provider = if (hasModel) providerName else null,
            reasoningEffort = effectiveEffort.takeIf { it in EFFORTS },
        )
        val handle = StreamHandle()
        run.handle = handle
        if (run.stop) handle.cancel()
        _speaking.update { it + (convId to bot.id) }
        _events.tryEmit(EngineEvent.TurnStarted(convId, bot.id))
        val turn = Turn(convId, bot.id, modelId)
        val outcome = try {
            backend.stream(req, handle) { ev -> turn.onEvent(ev) }
        } finally {
            run.handle = null
        }
        return turn.finish(outcome)
    }

    /** The state of one bot's streamed answer. Called from one thread only (the generation thread). */
    private inner class Turn(val convId: String, val botId: String, val modelId: String = "") {
        private val t0 = System.currentTimeMillis()
        private var usageIn = 0L
        private var usageOut = 0L
        private var bubbleId: String? = null
        private val text = StringBuilder()
        private val reasoning = StringBuilder()
        private var lastPush = 0L
        private val tools = HashMap<String, String>()   // tool call id -> TOOL message id
        private var produced = false
        private var hadError = false
        private val guessed = StringBuilder()   // thought summaries recognised by [ThoughtFilter] in this bubble
        private var realReasoning = false       // the model sent proper reasoning_content in this bubble
        private var turnRealText = false        // some real answer text was shown in this turn
        private var lastClosedId: String? = null
        private var lastClosedText: String = ""

        private fun ensureBubble() {
            if (bubbleId != null) return
            val m = newMessage(convId, Role.BOT, botId, "", streaming = true).copy(model = modelId)
            repo.addMessage(m)
            bubbleId = m.id
            text.setLength(0); reasoning.setLength(0)
        }

        private fun push(force: Boolean) {
            val id = bubbleId ?: return
            val now = System.nanoTime() / 1_000_000
            if (!force && now - lastPush < PUSH_INTERVAL_MS) return
            lastPush = now
            val t = text.toString(); val r = reasoning.toString()
            repo.updateMessage(convId, id) { it.copy(text = t, reasoning = r) }
        }

        private fun closeBubble() {
            val id = bubbleId ?: return
            bubbleId = null
            val rawText = text.toString().trimEnd(); val r = reasoning.toString().trim()
            text.setLength(0); reasoning.setLength(0); guessed.setLength(0); realReasoning = false   // the next bubble starts empty (its first delta is left-trimmed)
            if (rawText.isBlank() && r.isBlank()) { repo.deleteMessage(convId, id); return }
            val extracted = BotForge.extract(rawText)
            val forgedIds = if (extracted.bots.isNotEmpty()) {
                val now = System.currentTimeMillis()
                extracted.bots.mapIndexed { idx, spec ->
                    val newBot = Bot(
                        id = repo.newId(),
                        name = spec.name,
                        avatar = spec.avatar,
                        color = spec.color,
                        tagline = spec.tagline,
                        systemPrompt = spec.systemPrompt,
                        builtIn = false,
                        createdAt = now + idx,
                        voiceSid = spec.voiceSid,
                        voiceStyle = spec.voiceStyle,
                    )
                    repo.saveBot(newBot)
                    newBot.id
                }
            } else emptyList()
            val finalText = if (forgedIds.isNotEmpty() && extracted.cleanedText.isBlank()) {
                extracted.bots.joinToString(", ") { it.name }
            } else extracted.cleanedText
            val elapsed = System.currentTimeMillis() - t0
            repo.updateMessage(convId, id) {
                it.copy(text = finalText, reasoning = r, streaming = false, durationMs = elapsed, forgedBotIds = forgedIds)
            }
            if (finalText.isNotBlank()) {
                lastClosedId = id
                lastClosedText = finalText
            }
            if (forgedIds.isNotEmpty()) _events.tryEmit(EngineEvent.BotForged(convId, forgedIds))
        }

        private fun clearTransient() {
            if (_status.value.containsKey(convId)) _status.update { it - convId }
        }

        fun onEvent(ev: ChatEvent) {
            when (ev) {
                is ChatEvent.Delta -> {
                    clearTransient()
                    // After a tool call Hermes continues with "\n\n..."; a bubble never starts with blank lines.
                    val d = if (text.isEmpty()) ev.text.trimStart() else ev.text
                    if (d.isEmpty()) return
                    if (text.isEmpty() && ThoughtFilter.isThoughtBlock(d)) {
                        // A Gemini thought summary that arrived as content: show it as reasoning, not as the answer.
                        ensureBubble()
                        if (reasoning.length < MAX_REASONING_CHARS) { reasoning.append(d); guessed.append(d) }
                        produced = true
                        push(false)
                        return
                    }
                    ensureBubble()
                    text.append(d)
                    turnRealText = true
                    produced = true
                    push(false)
                }
                is ChatEvent.Reasoning -> {
                    clearTransient()
                    if (reasoning.length >= MAX_REASONING_CHARS) return
                    ensureBubble()
                    realReasoning = true
                    reasoning.append(ev.text)
                    produced = true
                    push(false)
                }
                is ChatEvent.Tool -> {
                    clearTransient()
                    closeBubble()
                    produced = true
                    val key = ev.callId.ifEmpty { ev.tool }
                    val label = Diag.redactor.redact(ev.label)
                    val existing = tools[key]
                    if (existing != null) {
                        repo.updateMessage(convId, existing) { it.copy(toolStatus = ev.status, text = if (label.isNotBlank()) label else it.text) }
                    } else {
                        val m = newMessage(convId, Role.TOOL, botId, label).copy(toolName = ev.tool, toolStatus = ev.status)
                        repo.addMessage(m)
                        tools[key] = m.id
                    }
                    if (ev.status.lowercase() != "running") {
                        if (ev.tool.contains("memory", ignoreCase = true)) {
                            _memoryUpdates.value = memoryCounter.incrementAndGet()
                            _events.tryEmit(EngineEvent.MemorySaved(convId))
                        } else {
                            _events.tryEmit(EngineEvent.ToolFinished(convId, ev.tool))
                        }
                    }
                }
                is ChatEvent.Usage -> { usageIn = ev.promptTokens; usageOut = ev.completionTokens }
                is ChatEvent.Status -> _status.update { it + (convId to Diag.redactor.redact(ev.text).take(300)) }
                is ChatEvent.Approval -> {
                    val cmd = Diag.redactor.redact(ev.command)
                    val desc = Diag.redactor.redact(ev.description)
                    _approvals.update { it + (convId to PendingApproval(convId, botId, ev.runId, ev.requestId, cmd, desc, ev.choices)) }
                    _events.tryEmit(EngineEvent.ApprovalRequested(convId))
                    closeBubble()
                    addIssue(convId, Role.NOTE, botId, "Hermes asks permission before it runs this and is waiting for an answer:\n" +
                        (if (cmd.isNotBlank()) cmd else desc) + (if (cmd.isNotBlank() && desc.isNotBlank()) "\n($desc)" else ""))
                }
                is ChatEvent.StreamError -> {
                    clearTransient(); closeBubble(); hadError = true
                    addIssue(convId, Role.ERROR, botId, Diag.redactor.redact(ev.raw))
                }
                is ChatEvent.Finish -> {
                    when {
                        ev.reason == "stop" -> {}
                        ev.reason == "length" -> { closeBubble(); addIssue(convId, Role.NOTE, botId, "The answer was cut off (length limit reached).") }
                        !hadError -> {
                            closeBubble(); hadError = true
                            addIssue(convId, Role.ERROR, botId, "The run ended abnormally: finish_reason=${ev.reason}" + (ev.errorCode?.let { " error_code=$it" } ?: ""))
                        }
                    }
                }
                ChatEvent.Done -> {}
            }
        }

        /**
         * Writes the turn's token counts on the last BOT message that has text and adds them to the statistics; with the budget
         * on WARN, the first turn of a day that crosses the limit also leaves one note in the chat.
         */
        private fun recordUsage() {
            val tin = usageIn; val tout = usageOut
            if (tin <= 0L && tout <= 0L) return
            lastClosedId?.let { cid -> repo.updateMessage(convId, cid) { it.copy(tokensIn = tin, tokensOut = tout) } }
            val u = usage ?: return
            val before = u.todayTotal()
            u.record(modelId, tin, tout)
            val b = u.budget.value
            if (b.mode == BudgetMode.WARN && b.dailyTokens > 0L && before < b.dailyTokens && u.todayTotal() >= b.dailyTokens) {
                val day = UsageStore.dayOf(System.currentTimeMillis())
                if (warnedDay.getAndSet(day) != day) {
                    addIssue(convId, Role.NOTE, null, "Daily token budget reached: ${b.dailyTokens} tokens used today. " +
                        "Hermes keeps answering; change the limit under Settings > Usage.")
                }
            }
        }

        /** Closes the answer and records how the stream ended. Returns true when the round may go on. */
        fun finish(outcome: ChatOutcome): Boolean {
            _approvals.update { it - convId }
            clearTransient()
            // Safety net: a turn whose ONLY output looked like thought summaries (no tool, no real text) was the answer after all.
            if (bubbleId != null && text.isBlank() && !turnRealText && tools.isEmpty() && !realReasoning && guessed.isNotBlank()) {
                text.append(guessed.toString().trim()); reasoning.setLength(0)
            }
            closeBubble()
            val clean = outcome is ChatOutcome.Completed
            for ((_, mid) in tools) repo.updateMessage(convId, mid) { if (it.toolStatus == "running") it.copy(toolStatus = if (clean) "completed" else "interrupted") else it }
            recordUsage()
            when (outcome) {
                ChatOutcome.Completed -> {
                    if (!produced && !hadError) addIssue(convId, Role.NOTE, botId, "The bot returned an empty reply.")
                    if (!hadError) {
                        val cid = lastClosedId
                        if (cid != null) {
                            repo.bumpUnread(convId)
                            _events.tryEmit(EngineEvent.TurnCompleted(convId, cid, botId, lastClosedText, usageIn, usageOut))
                        }
                    } else {
                        _events.tryEmit(EngineEvent.TurnFailed(convId))
                    }
                }
                ChatOutcome.Cancelled -> {}
                is ChatOutcome.HttpError -> {
                    addIssue(convId, Role.ERROR, botId, "HTTP ${outcome.code}: ${Diag.redactor.redact(outcome.body)}")
                    _events.tryEmit(EngineEvent.TurnFailed(convId))
                }
                is ChatOutcome.TransportError -> {
                    addIssue(convId, Role.ERROR, botId, Diag.redactor.redact(outcome.message))
                    _events.tryEmit(EngineEvent.TurnFailed(convId))
                }
            }
            return clean && !hadError
        }
    }

    companion object {
        /** Value of [status] for a conversation while the engine waits for Hermes to start; the UI shows a localized text for it. */
        const val STATUS_WAKING = "@waking"
        private const val PUSH_INTERVAL_MS = 60L
        private const val MAX_REASONING_CHARS = 30_000
        /** Values Hermes accepts as `model_options.reasoning_effort` that the app offers. */
        val EFFORTS = setOf("none", "minimal", "low", "medium", "high", "xhigh")
    }
}
