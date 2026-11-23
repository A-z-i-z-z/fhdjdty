package dev.pocketagent.app.data

/**
 * A chat persona. [avatar] is an `AvatarId` name (see `avatarIdOf`), [color] an ARGB value.
 * [systemPrompt] is layered on top of Hermes's own prompt (and SOUL.md) as an ephemeral system message;
 * the built-in "Hermes" bot has none. [model] "" means "whatever Hermes is configured with"; [effort] "" means
 * "Hermes default", otherwise one of none/minimal/low/medium/high/xhigh.
 */
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
)

/**
 * USER: typed by the person. BOT: an answer ([Message.botId] says whose). TOOL: one tool run of a bot
 * ([Message.toolName], [Message.text] = the preview/label, [Message.toolStatus] = running / completed / interrupted).
 * NOTE: a neutral system note (English text, not translated). ERROR: a failure, verbatim and redacted.
 */
enum class Role { USER, BOT, TOOL, NOTE, ERROR }

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
) {
    val isGroup: Boolean get() = botIds.size > 1
}
