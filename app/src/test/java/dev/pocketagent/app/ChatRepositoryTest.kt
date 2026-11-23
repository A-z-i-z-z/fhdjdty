package dev.pocketagent.app

import dev.pocketagent.app.data.BotPresets
import dev.pocketagent.app.data.ChatRepository
import dev.pocketagent.app.data.GroupMode
import dev.pocketagent.app.data.Message
import dev.pocketagent.app.data.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ChatRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun open(dir: File): ChatRepository =
        ChatRepository(dir, CoroutineScope(Dispatchers.IO), 20L).also { r -> runBlocking { r.loaded.first { it } } }

    private fun msg(conv: String, id: String, role: Role, text: String, bot: String? = null, t: Long = 1L) =
        Message(id, conv, role, bot, text, t)

    private fun waitFor(timeoutMs: Long = 5000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("condition not met within ${timeoutMs}ms")
            Thread.sleep(10)
        }
    }

    @Test fun firstRunSeedsHermesFirstAndStarterBots() {
        val dir = File(tmp.root, "chat")
        val r = open(dir)
        val ids = r.bots.value.map { it.id }
        assertEquals(BotPresets.HERMES_ID, ids.first())
        assertTrue(r.bots.value.first().builtIn)
        assertEquals(listOf("hermes") + BotPresets.firstRunBotIds, ids)
        r.flush()
        assertTrue(File(dir, "bots.json").isFile)
    }

    @Test fun roundTripKeepsEverything() {
        val dir = File(tmp.root, "chat")
        val a = open(dir)
        val c = a.createConversation(listOf("hermes", "rebel"), "Plans")
        a.setGroupMode(c.id, GroupMode.MENTION_ONLY)
        a.setPinned(c.id, true)
        a.addMessage(msg(c.id, "m1", Role.USER, "hello there"))
        a.addMessage(msg(c.id, "m2", Role.BOT, "general kenobi", "rebel").copy(reasoning = "thinking...", durationMs = 1234))
        a.addMessage(msg(c.id, "m3", Role.TOOL, "ls -la", "rebel").copy(toolName = "terminal", toolStatus = "completed"))
        a.addMessage(msg(c.id, "m4", Role.BOT, "streaming right now", "rebel").copy(streaming = true))
        a.saveBot(BotPresets.teacher.copy(id = "mine", name = "Mine"))
        a.flush()

        val b = open(dir)
        val conv = b.conversation(c.id)!!
        assertEquals("Plans", conv.title)
        assertEquals(listOf("hermes", "rebel"), conv.botIds)
        assertEquals(GroupMode.MENTION_ONLY, conv.groupMode)
        assertTrue(conv.pinned)
        assertTrue(conv.isGroup)
        assertEquals("streaming right now", conv.lastPreview)
        val msgs = b.messagesSnapshot(c.id)
        assertEquals(listOf("m1", "m2", "m3", "m4"), msgs.map { it.id })
        assertEquals("thinking...", msgs[1].reasoning)
        assertEquals(1234L, msgs[1].durationMs)
        assertEquals("terminal", msgs[2].toolName)
        assertEquals("completed", msgs[2].toolStatus)
        assertFalse("nothing is streaming after a restart", msgs[3].streaming)
        assertEquals("Mine", b.bot("mine")!!.name)
        assertEquals(a.bot("rebel"), b.bot("rebel"))
    }

    @Test fun messagesWriteSoonWithoutExplicitFlush() {
        val dir = File(tmp.root, "chat")
        val r = open(dir)
        val c = r.createConversation(listOf("hermes"))
        r.addMessage(msg(c.id, "m1", Role.USER, "hi"))
        waitFor { File(dir, "messages/${c.id}.json").isFile && File(dir, "conversations.json").isFile }
    }

    @Test fun pinnedFirstThenNewest() {
        val r = open(File(tmp.root, "chat"))
        val a = r.createConversation(listOf("hermes"), "a")
        Thread.sleep(5)
        val b = r.createConversation(listOf("hermes"), "b")
        assertEquals(listOf(b.id, a.id), r.conversations.value.map { it.id })
        r.setPinned(a.id, true)
        assertEquals(listOf(a.id, b.id), r.conversations.value.map { it.id })
        r.addMessage(msg(b.id, "x", Role.USER, "bump"))
        assertEquals("pinned stays first", a.id, r.conversations.value.first().id)
    }

    @Test fun corruptFilesAreKeptAsBadAndStartEmpty() {
        val dir = File(tmp.root, "chat")
        File(dir, "messages").mkdirs()
        File(dir, "bots.json").writeText("{ this is not json")
        File(dir, "conversations.json").writeText("[1,2,")
        File(dir, "messages/c1.json").writeText("garbage")
        val r = open(dir)
        assertEquals(listOf("hermes"), r.bots.value.map { it.id })   // Hermes always exists
        assertTrue(r.conversations.value.isEmpty())
        assertEquals("{ this is not json", File(dir, "bots.json.bad").readText())
        assertEquals("[1,2,", File(dir, "conversations.json.bad").readText())
        assertTrue(r.messagesSnapshot("c1").isEmpty())
        assertEquals("garbage", File(dir, "messages/c1.json.bad").readText())
        // A valid file replaces the corrupt one.
        waitFor { File(dir, "bots.json").readText().trimStart().startsWith("{\"v\"") || File(dir, "bots.json").readText().contains("\"bots\"") }
    }

    @Test fun malformedEntriesAreSkippedNotFatal() {
        val dir = File(tmp.root, "chat")
        dir.mkdirs()
        File(dir, "bots.json").writeText("""{"v":1,"bots":[{"name":"no id"},{"id":"ok","name":"Fine"},"junk"]}""")
        val r = open(dir)
        assertEquals(listOf("hermes", "ok"), r.bots.value.map { it.id })
        assertFalse(File(dir, "bots.json.bad").exists())
    }

    @Test fun deleteConversationRemovesItsFileAndHermesCannotBeDeleted() {
        val dir = File(tmp.root, "chat")
        val r = open(dir)
        val c = r.createConversation(listOf("hermes"))
        r.addMessage(msg(c.id, "m1", Role.USER, "x"))
        r.flush()
        assertTrue(File(dir, "messages/${c.id}.json").isFile)
        r.deleteConversation(c.id)
        r.flush()
        assertFalse(File(dir, "messages/${c.id}.json").exists())
        assertNull(r.conversation(c.id))
        r.deleteBot(BotPresets.HERMES_ID)
        assertNotNull(r.bot(BotPresets.HERMES_ID))
        r.deleteBot("rebel")
        assertNull(r.bot("rebel"))
    }

    @Test fun truncateUpdateDeleteAndPreview() {
        val r = open(File(tmp.root, "chat"))
        val c = r.createConversation(listOf("hermes"))
        r.addMessage(msg(c.id, "1", Role.USER, "one"))
        r.addMessage(msg(c.id, "2", Role.BOT, "two", "hermes"))
        r.addMessage(msg(c.id, "3", Role.USER, "three"))
        assertEquals("three", r.conversation(c.id)!!.lastPreview)
        r.updateMessage(c.id, "2") { it.copy(text = "TWO") }
        assertEquals("TWO", r.messagesSnapshot(c.id)[1].text)
        r.truncateAfter(c.id, "2")
        assertEquals(listOf("1", "2"), r.messagesSnapshot(c.id).map { it.id })
        assertEquals("TWO", r.conversation(c.id)!!.lastPreview)
        r.deleteMessage(c.id, "2")
        assertEquals("one", r.conversation(c.id)!!.lastPreview)
        r.clearConversation(c.id)
        assertTrue(r.messagesSnapshot(c.id).isEmpty())
        assertEquals("", r.conversation(c.id)!!.lastPreview)
    }

    @Test fun searchFindsMessagesAndTitles() {
        val dir = File(tmp.root, "chat")
        val a = open(dir)
        val c1 = a.createConversation(listOf("hermes"), "Kurdish songs")
        a.addMessage(msg(c1.id, "1", Role.USER, "Tell me about the Dengbêj tradition"))
        val c2 = a.createConversation(listOf("hermes"), "Other")
        a.addMessage(msg(c2.id, "2", Role.USER, "weather today"))
        a.flush()
        val b = open(dir)   // nothing loaded in memory: search reads the files
        val hits = b.search("dengbêj")
        assertEquals(1, hits.size)
        assertEquals(c1.id, hits[0].first.id)
        assertEquals("1", hits[0].second.id)
        assertEquals(1, b.search("kurdish songs").size)   // title match brings the last message
        assertTrue(b.search("   ").isEmpty())
        assertTrue(b.search("zzz").isEmpty())
    }

    @Test fun anUnreadableFileIsNeverWrittenOver() {
        val dir = File(tmp.root, "chat")
        // A directory where bots.json should be: exists, cannot be read as text (stands in for a transient I/O error).
        File(dir, "bots.json").mkdirs()
        File(dir, "messages/c1.json").mkdirs()
        val r = open(dir)
        assertEquals(listOf("hermes"), r.bots.value.map { it.id })
        r.saveBot(BotPresets.teacher)
        r.addMessage(msg("c1", "m", Role.USER, "hello"))
        r.flush()
        assertTrue("bots.json untouched", File(dir, "bots.json").isDirectory)
        assertTrue("message file untouched", File(dir, "messages/c1.json").isDirectory)
        assertEquals("still usable in memory", 2, r.bots.value.size)
    }

    @Test fun runningToolsAreInterruptedAfterARestart() {
        val dir = File(tmp.root, "chat")
        val a = open(dir)
        val c = a.createConversation(listOf("hermes"))
        a.addMessage(msg(c.id, "t", Role.TOOL, "ls", "hermes").copy(toolName = "terminal", toolStatus = "running"))
        a.flush()
        assertEquals("interrupted", open(dir).messagesSnapshot(c.id).single().toolStatus)
    }

    @Test fun streamingWritesAreDelayedAndTheFinalFlushStillWrites() {
        val dir = File(tmp.root, "chat")
        val r = ChatRepository(dir, CoroutineScope(Dispatchers.IO), 20L, 1500L).also { x -> runBlocking { x.loaded.first { it } } }
        val c = r.createConversation(listOf("hermes"))
        r.flush()
        Thread.sleep(150)   // let the short write scheduled by createConversation finish
        val f = File(dir, "messages/${c.id}.json")
        r.addMessage(msg(c.id, "s", Role.BOT, "partial", "hermes").copy(streaming = true))
        Thread.sleep(400)
        assertFalse("no write while streaming within the long debounce", f.exists())
        r.updateMessage(c.id, "s") { it.copy(streaming = false) }
        r.flush()
        assertTrue(f.isFile)
    }

    @Test fun untouchedOldStarterPromptsAreUpgradedOnLoadButEditedOnesAreNot() {
        val dir = File(tmp.root, "chat")
        dir.mkdirs()
        val old = dev.pocketagent.app.data.LegacyBotPrompts.byId.getValue("dengbej").first()
        val edited = BotPresets.rebel.copy(systemPrompt = dev.pocketagent.app.data.LegacyBotPrompts.byId.getValue("rebel").first() + "\nAlso: love cats.")
        val custom = BotPresets.coder.copy(id = "my-coder", systemPrompt = dev.pocketagent.app.data.LegacyBotPrompts.byId.getValue("coder").first())
        File(dir, "bots.json").writeText(dev.pocketagent.app.data.ChatJson.encodeBots(listOf(BotPresets.hermes, BotPresets.dengbej.copy(systemPrompt = old), edited, custom)))
        val r = open(dir)
        assertEquals(BotPresets.dengbej.systemPrompt, r.bot("dengbej")!!.systemPrompt)
        assertFalse(r.bot("dengbej")!!.systemPrompt == old)
        assertEquals(edited.systemPrompt, r.bot("rebel")!!.systemPrompt)
        assertEquals("other ids are never touched", custom.systemPrompt, r.bot("my-coder")!!.systemPrompt)
        r.flush()
        assertEquals(BotPresets.dengbej.systemPrompt, open(dir).bot("dengbej")!!.systemPrompt)
    }

    @Test fun currentPresetsAreNotTreatedAsLegacy() {
        for (t in BotPresets.templates) assertEquals(t, BotPresets.upgradeLegacy(t))
        // The personas that mention a language (all but the translator) really changed, so existing installs can be upgraded.
        for (id in listOf("rebel", "coder", "dengbej", "teacher", "critic", "planner"))
            assertTrue(id, dev.pocketagent.app.data.LegacyBotPrompts.byId.getValue(id).none { it.trim() == BotPresets.templates.first { t -> t.id == id }.systemPrompt.trim() })
    }
}
