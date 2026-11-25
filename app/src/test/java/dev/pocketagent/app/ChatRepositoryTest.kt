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

    // ---- Phase 3: unread, stars, routine chats, restore support ---------------------------------------------------

    @Test fun unreadCountsUpUnlessTheChatIsOnScreenAndNeverTouchesTheOrder() {
        val r = open(File(tmp.root, "chat"))
        val a = r.createConversation(listOf("hermes"), "a")
        Thread.sleep(5)
        val b = r.createConversation(listOf("hermes"), "b")
        val updatedBefore = r.conversation(a.id)!!.updatedAt
        r.bumpUnread(a.id); r.bumpUnread(a.id)
        assertEquals(2, r.conversation(a.id)!!.unread)
        assertEquals("unread never bumps updatedAt", updatedBefore, r.conversation(a.id)!!.updatedAt)
        assertEquals("order unchanged", listOf(b.id, a.id), r.conversations.value.map { it.id })
        r.setActiveConversation(a.id)
        assertEquals(a.id, r.activeConversation)
        assertEquals("opening clears", 0, r.conversation(a.id)!!.unread)
        r.bumpUnread(a.id)
        assertEquals("on screen: no bump", 0, r.conversation(a.id)!!.unread)
        r.setActiveConversation(null)
        assertNull(r.activeConversation)
        r.markUnread(a.id)
        assertEquals(1, r.conversation(a.id)!!.unread)
        r.bumpUnread(a.id)
        r.markUnread(a.id)
        assertEquals("markUnread keeps a higher count", 2, r.conversation(a.id)!!.unread)
        r.markRead(a.id)
        assertEquals(0, r.conversation(a.id)!!.unread)
        r.bumpUnread("nope")   // unknown chat: ignored
    }

    @Test fun unreadStarsAndRoutineSurviveARestartAndOldFilesLoadUnchanged() {
        val dir = File(tmp.root, "chat")
        val a = open(dir)
        val c = a.createConversation(listOf("hermes"), "Plans")
        a.addMessage(msg(c.id, "m1", Role.USER, "q"))
        a.addMessage(msg(c.id, "m2", Role.BOT, "answer", "hermes").copy(tokensIn = 1200, tokensOut = 340, model = "google/x"))
        a.setStarred(c.id, "m2", true)
        a.bumpUnread(c.id)
        a.setRoutine(c.id, "rt1")
        a.flush()
        val b = open(dir)
        assertEquals(1, b.conversation(c.id)!!.unread)
        assertEquals("rt1", b.conversation(c.id)!!.routineId)
        val m2 = b.messagesSnapshot(c.id)[1]
        assertTrue(m2.starred)
        assertEquals(1200L, m2.tokensIn); assertEquals(340L, m2.tokensOut); assertEquals("google/x", m2.model)

        // New fields are only written when they differ from the default, and a file without them loads with the defaults.
        val plain = dev.pocketagent.app.data.ChatJson.msgToJson(msg(c.id, "m3", Role.USER, "hi")).toString()
        assertFalse(plain.contains("tokensIn") || plain.contains("starred") || plain.contains("\"model\""))
        val plainConv = dev.pocketagent.app.data.ChatJson.convToJson(dev.pocketagent.app.data.Conversation("x", "t", listOf("hermes"), 1, 1)).toString()
        assertFalse(plainConv.contains("unread") || plainConv.contains("routineId"))
        val old = dev.pocketagent.app.data.ChatJson.convFromJson(org.json.JSONObject("""{"id":"o","title":"Old","botIds":["hermes"],"createdAt":5}"""))!!
        assertEquals(0, old.unread); assertEquals("", old.routineId)
        val oldMsg = dev.pocketagent.app.data.ChatJson.msgFromJson(org.json.JSONObject("""{"id":"x","role":"BOT","text":"t","createdAt":1}"""), "c")!!
        assertEquals(0L, oldMsg.tokensIn); assertEquals("", oldMsg.model); assertFalse(oldMsg.starred)
    }

    @Test fun starredMessagesListsAllChatsNewestFirstEvenWhenNotLoaded() {
        val dir = File(tmp.root, "chat")
        val a = open(dir)
        val c1 = a.createConversation(listOf("hermes"), "one")
        val c2 = a.createConversation(listOf("hermes"), "two")
        a.addMessage(msg(c1.id, "a1", Role.BOT, "old star", "hermes", t = 100))
        a.addMessage(msg(c2.id, "b1", Role.BOT, "new star", "hermes", t = 300))
        a.addMessage(msg(c2.id, "b2", Role.BOT, "plain", "hermes", t = 400))
        a.addMessage(msg(c1.id, "a2", Role.BOT, "mid star", "hermes", t = 200))
        a.setStarred(c1.id, "a1", true); a.setStarred(c2.id, "b1", true); a.setStarred(c1.id, "a2", true)
        a.flush()
        for (r in listOf(a, open(dir))) {   // in memory and read from the files
            val s = r.starredMessages()
            assertEquals(listOf("b1", "a2", "a1"), s.map { it.second.id })
            assertEquals(listOf(c2.id, c1.id, c1.id), s.map { it.first.id })
        }
        a.setStarred(c1.id, "a1", false)
        assertEquals(listOf("b1", "a2"), a.starredMessages().map { it.second.id })
    }

    @Test fun starredMessagesAreCappedAt300() {
        val r = open(File(tmp.root, "chat"))
        val c = r.createConversation(listOf("hermes"))
        for (i in 1..320) r.addMessage(msg(c.id, "m$i", Role.BOT, "t$i", "hermes", t = i.toLong()).copy(starred = true))
        val s = r.starredMessages()
        assertEquals(300, s.size)
        assertEquals("m320", s.first().second.id)
    }

    @Test fun forkResetsUnreadRoutineAndStars() {
        val r = open(File(tmp.root, "chat"))
        val c = r.createConversation(listOf("hermes"), "Chat")
        r.addMessage(msg(c.id, "m1", Role.BOT, "x", "hermes").copy(tokensIn = 5))
        r.setStarred(c.id, "m1", true); r.bumpUnread(c.id); r.setRoutine(c.id, "rt")
        val f = r.forkConversation(c.id)!!
        assertEquals(0, f.unread); assertEquals("", f.routineId)
        val m = r.messagesSnapshot(f.id).single()
        assertFalse(m.starred)
        assertEquals("history keeps its numbers", 5L, m.tokensIn)
        assertTrue("the original keeps its flags", r.messagesSnapshot(c.id).single().starred && r.conversation(c.id)!!.unread == 1)
    }

    @Test fun nothingIsWrittenAfterSuspendWrites() {
        val dir = File(tmp.root, "chat")
        val r = open(dir)
        val c = r.createConversation(listOf("hermes"), "Before")
        r.flush()
        val before = File(dir, "conversations.json").readText()
        r.suspendWrites()
        r.renameConversation(c.id, "After")
        r.addMessage(msg(c.id, "m1", Role.USER, "hello"))
        r.flush()
        Thread.sleep(200)   // longer than the debounce
        assertEquals(before, File(dir, "conversations.json").readText())
        assertFalse(File(dir, "messages/${c.id}.json").exists())
        assertEquals("still usable in memory", "After", r.conversation(c.id)!!.title)
    }

    @Test fun currentPresetsAreNotTreatedAsLegacy() {
        for (t in BotPresets.templates) assertEquals(t, BotPresets.upgradeLegacy(t))
        // The personas that mention a language (all but the translator) really changed, so existing installs can be upgraded.
        for (id in listOf("rebel", "coder", "dengbej", "teacher", "critic", "planner"))
            assertTrue(id, dev.pocketagent.app.data.LegacyBotPrompts.byId.getValue(id).none { it.trim() == BotPresets.templates.first { t -> t.id == id }.systemPrompt.trim() })
    }

    @Test fun indexIntactIsTrueForAFreshOrValidIndexAndFalseForACorruptOne() {
        val fresh = File(tmp.root, "fresh")
        assertTrue(open(fresh).indexIntact)
        val dir = File(tmp.root, "chat")
        val a = open(dir); a.createConversation(listOf("hermes"), "x"); a.flush()
        assertTrue(open(dir).indexIntact)
        File(dir, "conversations.json").writeText("[1,2,")
        val c = open(dir)
        assertFalse("a corrupt chat list must not be taken as 'no chats exist'", c.indexIntact)
        assertTrue(c.conversations.value.isEmpty())
    }

    @Test fun setStarredOnAChatThatIsNotInMemoryAppliesInTheBackground() {
        val dir = File(tmp.root, "chat")
        val a = open(dir)
        val c = a.createConversation(listOf("hermes"), "one")
        a.addMessage(msg(c.id, "m1", Role.BOT, "x", "hermes"))
        a.setStarred(c.id, "m1", true)
        a.flush()
        val b = open(dir)            // cold start: the messages are not loaded
        b.setStarred(c.id, "m1", false)   // must return without reading the file on this thread
        waitFor { b.starredMessages().isEmpty() }
        b.flush()
        assertFalse(open(dir).messagesSnapshot(c.id).single().starred)
    }

    @Test fun suspendWritesReturnsAndStopsAllWrites() {
        val dir = File(tmp.root, "chat")
        val a = open(dir)
        a.flush()
        val before = File(dir, "conversations.json").takeIf { it.exists() }?.readText()
        a.suspendWrites()
        a.createConversation(listOf("hermes"), "late")
        a.flush()
        Thread.sleep(80)
        assertEquals(before, File(dir, "conversations.json").takeIf { it.exists() }?.readText())
    }
}
