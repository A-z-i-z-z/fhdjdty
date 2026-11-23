package dev.pocketagent.app.data

import org.json.JSONArray
import org.json.JSONObject

/** JSON (de)serialisation of the chat models. Tolerant on read (missing fields get defaults), strict on write. */
internal object ChatJson {
    const val VERSION = 1

    private fun JSONObject.str(name: String): String? = if (!has(name) || isNull(name)) null else optString(name)
    private fun JSONObject.bool(name: String, def: Boolean) = if (!has(name) || isNull(name)) def else optBoolean(name, def)
    private fun JSONObject.lng(name: String, def: Long) = if (!has(name) || isNull(name)) def else optLong(name, def)

    fun botToJson(b: Bot): JSONObject = JSONObject()
        .put("id", b.id).put("name", b.name).put("avatar", b.avatar).put("color", b.color)
        .put("tagline", b.tagline).put("systemPrompt", b.systemPrompt).put("model", b.model).put("effort", b.effort)
        .put("builtIn", b.builtIn).put("createdAt", b.createdAt)

    fun botFromJson(o: JSONObject): Bot? {
        val id = o.str("id")?.takeIf { it.isNotBlank() } ?: return null
        return Bot(
            id = id,
            name = o.str("name") ?: id,
            avatar = o.str("avatar") ?: "NEWT",
            color = o.lng("color", 0xFFE8661CL),
            tagline = o.str("tagline") ?: "",
            systemPrompt = o.str("systemPrompt") ?: "",
            model = o.str("model") ?: "",
            effort = o.str("effort") ?: "",
            builtIn = o.bool("builtIn", false),
            createdAt = o.lng("createdAt", 0L),
        )
    }

    fun convToJson(c: Conversation): JSONObject = JSONObject()
        .put("id", c.id).put("title", c.title).put("botIds", JSONArray(c.botIds))
        .put("createdAt", c.createdAt).put("updatedAt", c.updatedAt).put("pinned", c.pinned)
        .put("groupMode", c.groupMode.name).put("lastPreview", c.lastPreview)

    fun convFromJson(o: JSONObject): Conversation? {
        val id = o.str("id")?.takeIf { it.isNotBlank() } ?: return null
        val ids = ArrayList<String>()
        o.optJSONArray("botIds")?.let { a -> for (i in 0 until a.length()) if (!a.isNull(i)) ids += a.optString(i) }
        val created = o.lng("createdAt", 0L)
        return Conversation(
            id = id,
            title = o.str("title") ?: "",
            botIds = ids,
            createdAt = created,
            updatedAt = o.lng("updatedAt", created),
            pinned = o.bool("pinned", false),
            groupMode = try { GroupMode.valueOf(o.str("groupMode") ?: "") } catch (_: Exception) { GroupMode.ALL_IN_TURN },
            lastPreview = o.str("lastPreview") ?: "",
        )
    }

    fun msgToJson(m: Message): JSONObject {
        val o = JSONObject().put("id", m.id).put("convId", m.convId).put("role", m.role.name)
            .put("text", m.text).put("createdAt", m.createdAt)
        m.botId?.let { o.put("botId", it) }
        m.toolName?.let { o.put("toolName", it) }
        m.toolStatus?.let { o.put("toolStatus", it) }
        if (m.streaming) o.put("streaming", true)
        m.durationMs?.let { o.put("durationMs", it) }
        if (m.reasoning.isNotEmpty()) o.put("reasoning", m.reasoning)
        return o
    }

    fun msgFromJson(o: JSONObject, fallbackConvId: String): Message? {
        val id = o.str("id")?.takeIf { it.isNotBlank() } ?: return null
        val role = try { Role.valueOf(o.str("role") ?: "") } catch (_: Exception) { return null }
        return Message(
            id = id,
            convId = o.str("convId") ?: fallbackConvId,
            role = role,
            botId = o.str("botId"),
            text = o.str("text") ?: "",
            createdAt = o.lng("createdAt", 0L),
            toolName = o.str("toolName"),
            // A tool that was running when the app died is not running any more.
            toolStatus = o.str("toolStatus")?.let { if (it == "running") "interrupted" else it },
            // Nothing can be streaming right after a load: whatever was generating when the app died is over.
            streaming = false,
            durationMs = if (o.has("durationMs") && !o.isNull("durationMs")) o.optLong("durationMs") else null,
            reasoning = o.str("reasoning") ?: "",
        )
    }

    fun encodeBots(bots: List<Bot>): String =
        JSONObject().put("v", VERSION).put("bots", JSONArray().also { a -> bots.forEach { a.put(botToJson(it)) } }).toString(1)

    fun encodeConversations(list: List<Conversation>): String =
        JSONObject().put("v", VERSION).put("conversations", JSONArray().also { a -> list.forEach { a.put(convToJson(it)) } }).toString(1)

    fun encodeMessages(list: List<Message>): String =
        JSONObject().put("v", VERSION).put("messages", JSONArray().also { a -> list.forEach { a.put(msgToJson(it)) } }).toString()

    /** Throws (JSONException) when [text] is not the expected document; individual malformed entries are skipped. */
    fun decodeBots(text: String): List<Bot> {
        val arr = JSONObject(text).getJSONArray("bots")
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::botFromJson) }
    }

    fun decodeConversations(text: String): List<Conversation> {
        val arr = JSONObject(text).getJSONArray("conversations")
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::convFromJson) }
    }

    fun decodeMessages(text: String, convId: String): List<Message> {
        val arr = JSONObject(text).getJSONArray("messages")
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let { o -> msgFromJson(o, convId) } }
    }
}
