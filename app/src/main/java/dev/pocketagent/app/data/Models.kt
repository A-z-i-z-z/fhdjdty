package dev.pocketagent.app.data

/**
 * A chat persona. [avatar] is an `AvatarId` name (see `avatarIdOf`), [color] an ARGB value.
 * [systemPrompt] is layered on top of Hermes's own prompt (and SOUL.md) as an ephemeral system message;
 * the built-in "Hermes" bot has none. [model] "" means "whatever Hermes is configured with"; [effort] "" means
 * "Hermes default", otherwise one of none/minimal/low/medium/high/xhigh.
 */
/** Per-bot spoken delivery style applied to Supertonic 3 cadence/pauses and system TTS pitch/rate. */
enum class BotVoiceStyle { DEFAULT, CALM, SERIOUS, HAPPY, EXCITED, STORYTELLER }

data class Bot(
    val id: String,
    val name: String,
    val avatar: String,
    val color: Long,
    val tagline: String,
    val systemPrompt: String,
    val model: String = "",
    val effort: String = "",
    val builtIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** Supertonic 3 speaker id: -1 = follow global default, 0..4 = female (F1..F5), 5..9 = male (M1..M5). */
    val voiceSid: Int = -1,
    /** Spoken delivery mood/style (happy, serious, excited, calm, storyteller). */
    val voiceStyle: BotVoiceStyle = BotVoiceStyle.DEFAULT,
    /** Optional per-bot speech speed override (0f = follow global speed + mood, else 0.85..1.35). */
    val voiceSpeed: Float = 0f,
)

/**
 * USER: typed by the person. BOT: an answer ([Message.botId] says whose). TOOL: one tool run of a bot
 * ([Message.toolName], [Message.text] = the preview/label, [Message.toolStatus] = running / completed / interrupted).
 * NOTE: a neutral system note (English text, not translated). ERROR: a failure, verbatim and redacted.
 */
enum class Role { USER, BOT, TOOL, NOTE, ERROR }

enum class AttachmentKind { IMAGE, DOCUMENT }

data class Attachment(
    val id: String,
    val name: String,
    val mime: String,
    val kind: AttachmentKind,
    val localPath: String,
    val guestPath: String = "",
    val sizeBytes: Long = 0L,
    val textExcerpt: String = "",
)

enum class ChatMode { NORMAL, PLAN, GOAL, ULTRACODE, LOOP }

data class Message(
    val id: String,
    val convId: String,
    val role: Role,
    val botId: String?,
    val text: String,
    val createdAt: Long,
    val toolName: String? = null,
    val toolStatus: String? = null,
    val streaming: Boolean = false,
    val durationMs: Long? = null,
    /** Reasoning ("thinking") text the model streamed for this BOT message; "" when there is none. */
    val reasoning: String = "",
    val attachments: List<Attachment> = emptyList(),
    val forgedBotIds: List<String> = emptyList(),
    /** Tokens the whole turn read / wrote; set on the last BOT message of a turn that has text, 0 elsewhere. */
    val tokensIn: Long = 0L,
    val tokensOut: Long = 0L,
    /** Model id this BOT message was generated with, as sent to Hermes; "" = Hermes's own default. */
    val model: String = "",
    /** Saved for later by the user (see the Saved messages screen). */
    val starred: Boolean = false,
)

/** Group chats: every bot answers in order, or only the @mentioned ones (else the first bot). */
enum class GroupMode { ALL_IN_TURN, MENTION_ONLY }

/**
 * [title] is "" until the first message is sent (the engine then derives it from that message); an empty title
 * means "untitled" and the UI shows a localized placeholder.
 */
data class Conversation(
    val id: String,
    val title: String,
    val botIds: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
    val groupMode: GroupMode = GroupMode.ALL_IN_TURN,
    val lastPreview: String = "",
    val mode: ChatMode = ChatMode.NORMAL,
    /** Per-chat model override ("" = use bot's model or Hermes global default). */
    val modelOverride: String = "",
    /** Per-chat thinking effort override ("" = use bot's effort or Hermes global default). */
    val effortOverride: String = "",
    /** Replies the user has not seen yet (bumped per finished BOT turn while the chat is not on screen). */
    val unread: Int = 0,
    /** Id of the routine this chat belongs to; "" for an ordinary chat. */
    val routineId: String = "",
) {
    val isGroup: Boolean get() = botIds.size > 1
}
