package dev.pocketagent.app.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Bots as a portable document (export and import of `*.agiri-bots.json` files and clipboard text).
 * The document carries no ids: whoever imports assigns fresh ones, so importing never overwrites an existing bot.
 */
object BotShare {
    const val FILE_SUFFIX = ".agiri-bots.json"
    const val FORMAT = 1
    const val MAX_BOTS = 50
    const val MAX_NAME = 60
    const val MAX_TAGLINE = 200
    const val MAX_PROMPT = 20_000
    private const val MAX_MODEL = 200
    private const val MAX_AVATAR = 32
    private const val MAX_DOCUMENT_CHARS = 3_000_000
    private const val DEFAULT_COLOR = 0xFFE8661CL

    /** `{"agiri_bots":1,"bots":[{name,avatar,color,tagline,systemPrompt,model,effort,voiceSid,voiceStyle,voiceSpeed}]}`. No ids. */
    fun encode(bots: List<Bot>): String {
        val arr = JSONArray()
        for (b in bots) {
            arr.put(
                JSONObject()
                    .put("name", b.name).put("avatar", b.avatar).put("color", b.color)
                    .put("tagline", b.tagline).put("systemPrompt", b.systemPrompt)
                    .put("model", b.model).put("effort", b.effort)
                    .put("voiceSid", b.voiceSid).put("voiceStyle", b.voiceStyle.name).put("voiceSpeed", b.voiceSpeed.toDouble()),
            )
        }
        return JSONObject().put("agiri_bots", FORMAT).put("bots", arr).toString(2)
    }

    /**
     * Bots with `id = ""` (the caller assigns `repo.newId()`) and `builtIn = false`.
     * Limits: at most [MAX_BOTS] bots, name <= [MAX_NAME] chars, tagline <= [MAX_TAGLINE], systemPrompt <= [MAX_PROMPT].
     * @throws IllegalArgumentException with a short reason when [text] is not a usable document.
     */
    fun decode(text: String): List<Bot> {
        val clean = text.trim().removePrefix("﻿").trim()
        require(clean.isNotEmpty()) { "The file is empty." }
        require(clean.length <= MAX_DOCUMENT_CHARS) { "The file is too large." }
        val root = try { JSONObject(clean) } catch (e: JSONException) { throw IllegalArgumentException("This is not a bots file (it is not valid JSON).") }
        if (!root.has("agiri_bots")) throw IllegalArgumentException("This is not an Agiri bots file.")
        val version = root.optInt("agiri_bots", -1)
        require(version >= 1) { "This is not an Agiri bots file." }
        require(version <= FORMAT) { "This file was made by a newer version of the app." }
        val arr = root.optJSONArray("bots") ?: throw IllegalArgumentException("The file has no list of bots.")
        require(arr.length() > 0) { "The file contains no bots." }
        require(arr.length() <= MAX_BOTS) { "The file contains more than $MAX_BOTS bots." }
        val now = System.currentTimeMillis()
        val out = ArrayList<Bot>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: throw IllegalArgumentException("Bot ${i + 1} is not an object.")
            val name = str(o, "name").trim()
            require(name.isNotEmpty()) { "Bot ${i + 1} has no name." }
            require(name.length <= MAX_NAME) { "The name of bot ${i + 1} is longer than $MAX_NAME characters." }
            val tagline = str(o, "tagline").trim()
            require(tagline.length <= MAX_TAGLINE) { "The tagline of \"$name\" is longer than $MAX_TAGLINE characters." }
            val prompt = str(o, "systemPrompt")
            require(prompt.length <= MAX_PROMPT) { "The instructions of \"$name\" are longer than $MAX_PROMPT characters." }
            val avatar = str(o, "avatar").trim().take(MAX_AVATAR).filter { it.isLetterOrDigit() || it == '_' }.ifEmpty { "NEWT" }
            val color = if (o.has("color") && !o.isNull("color")) o.optLong("color", DEFAULT_COLOR) else DEFAULT_COLOR
            val effort = str(o, "effort").trim().let { if (it in ChatEngine.EFFORTS) it else "" }
            val sid = if (o.has("voiceSid") && !o.isNull("voiceSid")) o.optInt("voiceSid", -1) else -1
            val style = try { BotVoiceStyle.valueOf(str(o, "voiceStyle")) } catch (_: Exception) { BotVoiceStyle.DEFAULT }
            val speed = if (o.has("voiceSpeed") && !o.isNull("voiceSpeed")) o.optDouble("voiceSpeed", 0.0).toFloat() else 0f
            out += Bot(
                id = "", name = name, avatar = avatar, color = color, tagline = tagline, systemPrompt = prompt,
                model = str(o, "model").trim().take(MAX_MODEL), effort = effort, builtIn = false, createdAt = now + i,
                voiceSid = if (sid in 0..9) sid else -1,
                voiceStyle = style,
                voiceSpeed = if (speed.isNaN()) 0f else speed.coerceIn(0f, 1.5f),
            )
        }
        return out
    }

    /** A string field, "" when absent, null or not a string-like value. */
    private fun str(o: JSONObject, key: String): String = if (!o.has(key) || o.isNull(key)) "" else o.optString(key, "")
}
