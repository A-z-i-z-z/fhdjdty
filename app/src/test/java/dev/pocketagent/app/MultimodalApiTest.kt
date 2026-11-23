package dev.pocketagent.app

import dev.pocketagent.app.api.ChatMessage
import dev.pocketagent.app.api.ChatRequest
import dev.pocketagent.app.data.Attachment
import dev.pocketagent.app.data.AttachmentKind
import dev.pocketagent.app.data.BotPresets
import dev.pocketagent.app.data.ChatJson
import dev.pocketagent.app.data.ChatMode
import dev.pocketagent.app.data.Conversation
import dev.pocketagent.app.data.Message
import dev.pocketagent.app.data.Role
import dev.pocketagent.app.data.Transcript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultimodalApiTest {
    @Test fun chatRequestToJsonEncodesPlainStringAndMultimodalImages() {
        val req = ChatRequest(
            messages = listOf(
                ChatMessage("system", "sys"),
                ChatMessage("user", "plain text"),
                ChatMessage("user", "look at this", listOf("data:image/jpeg;base64,AAA=")),
            ),
        )
        val arr = req.toJson().getJSONArray("messages")
        assertEquals("plain text", arr.getJSONObject(1).getString("content"))

        val parts = arr.getJSONObject(2).getJSONArray("content")
        assertEquals("text", parts.getJSONObject(0).getString("type"))
        assertEquals("look at this", parts.getJSONObject(0).getString("text"))
        assertEquals("image_url", parts.getJSONObject(1).getString("type"))
        assertEquals("data:image/jpeg;base64,AAA=", parts.getJSONObject(1).getJSONObject("image_url").getString("url"))
    }

    @Test fun chatJsonRoundTripsAttachmentsForgedBotsAndMode() {
        val conv = Conversation("c1", "Title", listOf("hermes"), 10L, 20L, mode = ChatMode.ULTRACODE)
        val att = Attachment("a1", "notes.txt", "text/plain", AttachmentKind.DOCUMENT, "/tmp/notes.txt", "/root/.hermes/attachments/notes.txt", 42L, "hello world")
        val msg = Message("m1", "c1", Role.USER, null, "", 10L, attachments = listOf(att), forgedBotIds = listOf("bot99"))

        val decConv = ChatJson.decodeConversations(ChatJson.encodeConversations(listOf(conv))).single()
        assertEquals(ChatMode.ULTRACODE, decConv.mode)

        val decMsg = ChatJson.decodeMessages(ChatJson.encodeMessages(listOf(msg)), "c1").single()
        assertEquals(1, decMsg.attachments.size)
        assertEquals("notes.txt", decMsg.attachments[0].name)
        assertEquals(AttachmentKind.DOCUMENT, decMsg.attachments[0].kind)
        assertEquals("hello world", decMsg.attachments[0].textExcerpt)
        assertEquals(listOf("bot99"), decMsg.forgedBotIds)
    }

    @Test fun chatJsonReadsLegacyMessagesWithoutNewFields() {
        val legacyConv = """{"v":1,"conversations":[{"id":"c0","title":"Old","botIds":["hermes"],"createdAt":1,"updatedAt":2,"pinned":false,"groupMode":"ALL_IN_TURN","lastPreview":"hi"}]}"""
        val legacyMsg = """{"v":1,"messages":[{"id":"m0","convId":"c0","role":"USER","text":"hi","createdAt":1}]}"""
        val c = ChatJson.decodeConversations(legacyConv).single()
        val m = ChatJson.decodeMessages(legacyMsg, "c0").single()
        assertEquals(ChatMode.NORMAL, c.mode)
        assertTrue(m.attachments.isEmpty())
        assertTrue(m.forgedBotIds.isEmpty())
    }

    @Test fun transcriptIncludesDocumentExcerptsAndLastThreeImageMessages() {
        val c = Conversation("c1", "", listOf("hermes"), 0L, 0L, mode = ChatMode.PLAN)
        val imgs = (1..4).map { i ->
            val a = Attachment("img$i", "p$i.jpg", "image/jpeg", AttachmentKind.IMAGE, "/tmp/p$i.jpg", "/root/.hermes/attachments/p$i.jpg", 100L)
            Message("m$i", "c1", Role.USER, null, "pic $i", i.toLong(), attachments = listOf(a))
        }
        val doc = Attachment("d1", "spec.md", "text/markdown", AttachmentKind.DOCUMENT, "/tmp/spec.md", "/root/.hermes/attachments/spec.md", 50L, "# Spec body")
        val docMsg = Message("m5", "c1", Role.USER, null, "", 5L, attachments = listOf(doc))

        val out = Transcript.build(c, BotPresets.hermes, listOf(BotPresets.hermes), imgs + docMsg) { a -> "data:image/jpeg;base64,${a.id}" }
        assertTrue(out[0].content.contains("ACTIVE MODE: PLAN"))
        val user = out[1]
        assertEquals(listOf("data:image/jpeg;base64,img2", "data:image/jpeg;base64,img3", "data:image/jpeg;base64,img4"), user.imageDataUrls)
        assertTrue(user.content.contains("Begin spec.md"))
        assertTrue(user.content.contains("# Spec body"))
    }
}
