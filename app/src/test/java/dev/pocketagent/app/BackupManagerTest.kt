package dev.pocketagent.app

import dev.pocketagent.app.data.BackupCore
import dev.pocketagent.app.data.BackupException
import dev.pocketagent.app.data.BackupManager
import dev.pocketagent.app.data.BackupReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupManagerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val manifestJson =
        """{"format":1,"app":"t","created":5,"conversations":2,"bots":1,"routines":0,"attachments":true,"hermes":true,"files":0,"bytes":0}"""

    private fun put(f: File, text: String) { f.parentFile.mkdirs(); f.writeText(text) }

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z -> for ((n, t) in entries) { z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() } }
        return bos.toByteArray()
    }

    private fun names(bytes: ByteArray): List<String> {
        val out = ArrayList<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z -> while (true) { out += (z.nextEntry ?: break).name } }
        return out
    }

    /** A chat directory and a Hermes home with real-looking content plus decoys that must never travel. */
    private class Fixture(val chat: File, val hermes: File, val prefs: Map<String, Map<String, *>>)

    private fun fixture(root: File = File(tmp.root, "src")): Fixture {
        val chat = File(root, "chat"); val hermes = File(root, "hermes-home")
        put(File(chat, "bots.json"), """{"v":1,"bots":[{"id":"hermes"}]}""")
        put(File(chat, "conversations.json"), """{"v":1,"conversations":[{"id":"a"},{"id":"b"}]}""")
        put(File(chat, "routines.json"), """{"routines":[]}""")
        put(File(chat, "usage.json"), """{"days":[]}""")
        put(File(chat, "messages/abc123.json"), """{"v":1,"messages":[{"id":"m1"}]}""")
        put(File(chat, "messages/def456.json"), """{"v":1,"messages":[]}""")
        File(chat, "attachments").mkdirs(); File(chat, "attachments/pic_1.jpg").writeBytes(byteArrayOf(1, 2, 3, 4))
        put(File(chat, "attachments/note.txt"), "hello")
        put(File(hermes, "SOUL.md"), "soul text")
        put(File(hermes, "memories/MEMORY.md"), "m1\n§\nm2")
        put(File(hermes, "memories/USER.md"), "u1")
        put(File(hermes, "skills/custom/mine/SKILL.md"), "---\nname: mine\n---\n")
        put(File(hermes, "skills/custom/mine/references/notes.md"), "notes")
        // decoys
        put(File(chat, "secrets/api_key"), "TOPSECRET1")
        put(File(chat, ".env"), "KEY=TOPSECRET2")
        put(File(chat, "messages/.env"), "KEY=TOPSECRET3")
        put(File(hermes, ".env"), "GEMINI_API_KEY=TOPSECRET4")
        put(File(hermes, "config.yaml"), "api_key: TOPSECRET5")
        put(File(hermes, "auth.json"), """{"token":"TOPSECRET6"}""")
        put(File(hermes, "skills/custom/mine/.env"), "TOPSECRET7")
        put(File(hermes, "skills/custom/mine/token.json"), "TOPSECRET8")
        put(File(hermes, "skills/custom/mine/id_rsa"), "TOPSECRET9")
        put(File(hermes, "skills/.bundled_manifest"), "arxiv:123")
        put(File(hermes, "memories/credentials.txt"), "TOPSECRET10")
        val prefs = mapOf(
            "appearance" to mapOf("theme" to "KAWA", "textScale" to 1.1f, "showMascot" to true, "stray" to "x"),
            "voice_sound" to mapOf("voiceSid" to 3, "autoSpeak" to false),
            "presence" to mapOf("idleStopMinutes" to 30, "autoStart" to "not a boolean"),
            "settings" to mapOf("api_key" to "TOPSECRET11"),
        )
        return Fixture(chat, hermes, prefs)
    }

    private fun export(fx: Fixture, options: BackupManager.Options = BackupManager.Options()): Pair<ByteArray, BackupManager.Summary> {
        val bos = ByteArrayOutputStream()
        val s = BackupCore.export(bos, BackupCore.Sources(fx.chat, fx.hermes, fx.prefs), options, "9.9.9", now = 1234L)
        return bos.toByteArray() to s
    }

    // ---- export ---------------------------------------------------------------------------------------------------

    @Test fun exportHoldsTheUserDataAndTheManifestCounts() {
        val (bytes, summary) = export(fixture())
        val n = names(bytes)
        for (expected in listOf(
            "chat/bots.json", "chat/conversations.json", "chat/routines.json", "chat/usage.json", "chat/messages/abc123.json",
            "attachments/pic_1.jpg", "attachments/note.txt", "hermes/SOUL.md", "hermes/memories/MEMORY.md", "hermes/memories/USER.md",
            "hermes/skills/custom/mine/SKILL.md", "hermes/skills/custom/mine/references/notes.md",
            "prefs/appearance.json", "prefs/voice_sound.json", "prefs/presence.json", "manifest.json",
        )) assertTrue("missing $expected in $n", expected in n)
        assertEquals(2, summary.conversations)
        assertEquals(1, summary.bots)
        assertEquals(0, summary.routines)
        assertEquals("9.9.9", summary.appVersion)
        assertEquals(1234L, summary.createdAt)
        assertEquals(n.size - 1, summary.files)
    }

    @Test fun secretsAreNeverExported() {
        val (bytes, _) = export(fixture())
        val n = names(bytes)
        for (bad in listOf("secret", ".env", "config.yaml", "auth.json", "api_key", "token", "id_rsa", "credentials", ".bundled_manifest", "settings")) {
            assertTrue("a name contains $bad: $n", n.none { it.lowercase().contains(bad) })
        }
        // Not even as file content, wherever it came from.
        val raw = String(bytes, Charsets.ISO_8859_1)
        assertFalse(raw.contains("TOPSECRET"))
        val allContent = StringBuilder()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z -> while (true) { z.nextEntry ?: break; allContent.append(String(z.readBytes())) } }
        assertFalse(allContent.contains("TOPSECRET"))
    }

    @Test fun optionsLeaveOutAttachmentsAndHermes() {
        val (bytes, _) = export(fixture(), BackupManager.Options(includeAttachments = false, includeHermes = false))
        val n = names(bytes)
        assertTrue(n.none { it.startsWith("attachments/") || it.startsWith("hermes/") })
        assertTrue("chat/bots.json" in n)
        val scan = BackupCore.scan(ByteArrayInputStream(bytes), null)
        assertFalse(scan.manifest.attachments)
        assertFalse(scan.manifest.hermes)
    }

    @Test fun anAbsentHermesHomeIsFine() {
        val fx = fixture()
        val bos = ByteArrayOutputStream()
        BackupCore.export(bos, BackupCore.Sources(fx.chat, null, fx.prefs), BackupManager.Options(), "1")
        assertTrue(names(bos.toByteArray()).none { it.startsWith("hermes/") })
    }

    // ---- round trip -------------------------------------------------------------------------------------------------

    @Test fun roundTripRestoresEverythingAndReplacesOldChatData() {
        val (bytes, _) = export(fixture())
        // The target already has other data: it must be replaced, not merged.
        val dstChat = File(tmp.root, "dst/chat"); val dstHermes = File(tmp.root, "dst/hermes-home")
        put(File(dstChat, "bots.json"), "OLD bots")
        put(File(dstChat, "messages/old.json"), "OLD messages")
        put(File(dstChat, "attachments/old.png"), "OLD attachment")
        put(File(dstHermes, "SOUL.md"), "OLD soul")
        put(File(dstHermes, "skills/bundled/keep/SKILL.md"), "bundled skill stays")
        put(File(dstHermes, ".env"), "GEMINI_API_KEY=KEEPME")

        val staging = File(tmp.root, "dst/staging")
        val scan = BackupCore.scan(ByteArrayInputStream(bytes), staging)
        assertEquals(2, scan.manifest.conversations)
        assertEquals(0, scan.skipped)
        BackupCore.swapIn(staging, dstChat, dstHermes, scan.manifest)

        assertEquals("""{"v":1,"bots":[{"id":"hermes"}]}""", File(dstChat, "bots.json").readText())
        assertEquals("""{"v":1,"messages":[{"id":"m1"}]}""", File(dstChat, "messages/abc123.json").readText())
        assertFalse(File(dstChat, "messages/old.json").exists())
        assertFalse(File(dstChat, "attachments/old.png").exists())
        assertEquals(listOf<Byte>(1, 2, 3, 4), File(dstChat, "attachments/pic_1.jpg").readBytes().toList())
        assertEquals("soul text", File(dstHermes, "SOUL.md").readText())
        assertEquals("m1\n§\nm2", File(dstHermes, "memories/MEMORY.md").readText())
        assertEquals("notes", File(dstHermes, "skills/custom/mine/references/notes.md").readText())
        // Hermes files are only overlaid: nothing of Hermes is deleted, and credentials are untouched.
        assertEquals("bundled skill stays", File(dstHermes, "skills/bundled/keep/SKILL.md").readText())
        assertEquals("GEMINI_API_KEY=KEEPME", File(dstHermes, ".env").readText())
    }

    @Test fun aBackupWithoutAttachmentsKeepsTheOnesOnThePhone() {
        val (bytes, _) = export(fixture(), BackupManager.Options(includeAttachments = false, includeHermes = false))
        val dstChat = File(tmp.root, "dst2/chat")
        put(File(dstChat, "attachments/mine.png"), "keep")
        val staging = File(tmp.root, "dst2/staging")
        val scan = BackupCore.scan(ByteArrayInputStream(bytes), staging)
        BackupCore.swapIn(staging, dstChat, null, scan.manifest)
        assertEquals("keep", File(dstChat, "attachments/mine.png").readText())
        assertTrue(File(dstChat, "bots.json").isFile)
    }

    @Test fun inspectWritesNothing() {
        val (bytes, summary) = export(fixture())
        val before = tmp.root.walkTopDown().count()
        val scan = BackupCore.scan(ByteArrayInputStream(bytes), null)
        assertEquals(before, tmp.root.walkTopDown().count())
        assertEquals(summary.files, scan.files)
        assertEquals(summary.bytes, scan.bytes)
    }

    // ---- preferences --------------------------------------------------------------------------------------------------

    @Test fun preferencesKeepTheirTypesAndDropForeignKeysAndWrongTypes() {
        val (bytes, _) = export(fixture())
        val staging = File(tmp.root, "p/staging")
        BackupCore.scan(ByteArrayInputStream(bytes), staging)
        val appearance = BackupCore.prefsFromJson("appearance", File(staging, "prefs/appearance.json").readText()).associateBy { it.key }
        assertEquals("KAWA", appearance.getValue("theme").value)
        assertEquals(1.1f, appearance.getValue("textScale").value as Float, 0.0001f)
        assertEquals(true, appearance.getValue("showMascot").value)
        assertNull(appearance["stray"])
        val voice = BackupCore.prefsFromJson("voice_sound", File(staging, "prefs/voice_sound.json").readText()).associateBy { it.key }
        assertEquals(3, voice.getValue("voiceSid").value)
        assertEquals(false, voice.getValue("autoSpeak").value)
        val presence = BackupCore.prefsFromJson("presence", File(staging, "prefs/presence.json").readText()).associateBy { it.key }
        assertEquals(30, presence.getValue("idleStopMinutes").value)
        assertNull(presence["autoStart"])                              // a string where a boolean belongs was never exported
        assertFalse(File(staging, "prefs/settings.json").exists())     // provider settings are not part of a backup
    }

    @Test fun aTamperedPreferencesFileCannotPutTheWrongTypeUnderAKey() {
        val text = """{"v":1,"values":{"theme":{"t":"i","v":5},"textScale":{"t":"f","v":"x"},"showMascot":{"t":"b","v":false},"other":{"t":"s","v":"q"}}}"""
        val v = BackupCore.prefsFromJson("appearance", text).associateBy { it.key }
        assertEquals(setOf("showMascot"), v.keys)
        assertTrue(BackupCore.prefsFromJson("settings", text).isEmpty())
        assertThrows(BackupException::class.java) { BackupCore.prefsFromJson("appearance", "not json") }
    }

    // ---- zip-slip and other hostile archives ----------------------------------------------------------------------------

    @Test fun traversalAndOddEntryNamesRefuseTheWholeArchive() {
        val hostile = listOf(
            "../evil.txt", "chat/../../evil.txt", "/etc/passwd", "chat\\..\\x.json", "hermes/skills/../../../x", "chat/messages/../../x.json",
            "hermes/skills/./a", "chat//bots.json", "C:/x", "chat/bots.json/../../../y", "hermes/skills/a\u0000b",
        )
        for (name in hostile) {
            val staging = File(tmp.root, "z/${hostile.indexOf(name)}/staging")
            val bytes = zip("manifest.json" to manifestJson, name to "evil")
            assertThrows("should refuse: $name", BackupException::class.java) { BackupCore.scan(ByteArrayInputStream(bytes), staging) }
            assertFalse(File(staging.parentFile, "evil.txt").exists())
            assertFalse(File(tmp.root, "evil.txt").exists())
        }
    }

    @Test fun anUnknownTopLevelFolderRefusesTheArchive() {
        val bytes = zip("manifest.json" to manifestJson, "other/file.txt" to "x")
        assertThrows(BackupException::class.java) { BackupCore.scan(ByteArrayInputStream(bytes), null) }
        val bytes2 = zip("manifest.json" to manifestJson, "shell.sh" to "x")
        assertThrows(BackupException::class.java) { BackupCore.scan(ByteArrayInputStream(bytes2), null) }
    }

    @Test fun secretsAndUnknownFilesInsideKnownFoldersAreSkippedNotWritten() {
        val staging = File(tmp.root, "s/staging")
        val bytes = zip(
            "manifest.json" to manifestJson,
            "chat/bots.json" to """{"bots":[]}""",
            "chat/secrets/api_key" to "TOPSECRET", "chat/.env" to "TOPSECRET", "chat/other.json" to "x",
            "hermes/.env" to "TOPSECRET", "hermes/config.yaml" to "TOPSECRET", "hermes/auth.json" to "TOPSECRET",
            "hermes/skills/x/.env" to "TOPSECRET", "hermes/skills/x/credentials.json" to "TOPSECRET", "hermes/skills/x/SKILL.md" to "ok",
            "attachments/.hidden" to "x", "prefs/settings.json" to "{}", "prefs/appearance.json" to """{"values":{}}""",
        )
        val r = BackupCore.scan(ByteArrayInputStream(bytes), staging)
        assertEquals(3, r.files)      // bots.json, SKILL.md, appearance.json
        assertEquals(10, r.skipped)
        assertTrue(File(staging, "chat/bots.json").isFile)
        assertTrue(File(staging, "hermes/skills/x/SKILL.md").isFile)
        assertFalse(File(staging, "chat/secrets").exists())
        assertFalse(File(staging, "chat/.env").exists())
        assertFalse(File(staging, "hermes/.env").exists())
        assertFalse(File(staging, "hermes/config.yaml").exists())
        assertFalse(File(staging, "hermes/skills/x/.env").exists())
        assertFalse(File(staging, "hermes/skills/x/credentials.json").exists())
        assertFalse(File(staging, "prefs/settings.json").exists())
        assertTrue(staging.walkTopDown().none { it.isFile && it.readText().contains("TOPSECRET") })
    }

    @Test fun sizeAndEntryCapsAreEnforcedWhileReading() {
        val big = "x".repeat(5000)
        val bytes = zip("manifest.json" to manifestJson, "chat/bots.json" to big, "chat/conversations.json" to big)
        assertThrows(BackupException::class.java) {
            BackupCore.scan(ByteArrayInputStream(bytes), null, BackupCore.Limits(maxTotalBytes = 6000))
        }
        // Skipped entries count too: a zip bomb hidden in an ignored name does not get through.
        val bomb = zip("manifest.json" to manifestJson, "chat/ignored.bin" to "y".repeat(100_000))
        assertThrows(BackupException::class.java) {
            BackupCore.scan(ByteArrayInputStream(bomb), null, BackupCore.Limits(maxTotalBytes = 10_000))
        }
        val many = zip("manifest.json" to manifestJson, "chat/bots.json" to "{}", "chat/usage.json" to "{}", "chat/drafts.json" to "{}")
        assertThrows(BackupException::class.java) {
            BackupCore.scan(ByteArrayInputStream(many), null, BackupCore.Limits(maxEntries = 3))
        }
        // The same archives pass with the real limits.
        assertNotNull(BackupCore.scan(ByteArrayInputStream(bytes), null))
    }

    @Test fun notABackupIsRefusedWithAClearError() {
        assertThrows(BackupException::class.java) { BackupCore.scan(ByteArrayInputStream("this is not a zip".toByteArray()), null) }
        assertThrows(BackupException::class.java) { BackupCore.scan(ByteArrayInputStream(ByteArray(0)), null) }
        assertThrows(BackupException::class.java) { BackupCore.scan(ByteArrayInputStream(zip("chat/bots.json" to "{}")), null) }
        assertThrows(BackupException::class.java) {
            BackupCore.scan(ByteArrayInputStream(zip("manifest.json" to """{"format":99,"app":"x"}""")), null)
        }
        assertThrows(BackupException::class.java) {
            BackupCore.scan(ByteArrayInputStream(zip("manifest.json" to "{not json")), null)
        }
    }

    @Test fun failuresCarryAReasonCodeSoTheUiCanTranslateThem() {
        fun reason(block: () -> Unit): BackupReason =
            try { block(); BackupReason.OTHER } catch (e: BackupException) { e.reason }
        assertEquals(BackupReason.NOT_BACKUP, reason { BackupCore.scan(ByteArrayInputStream("this is not a zip".toByteArray()), null) })
        assertEquals(BackupReason.NOT_BACKUP, reason { BackupCore.scan(ByteArrayInputStream(zip("chat/bots.json" to "{}")), null) })
        assertEquals(BackupReason.NOT_BACKUP, reason { BackupCore.scan(ByteArrayInputStream(zip("manifest.json" to "{not json")), null) })
        assertEquals(
            BackupReason.NEWER_VERSION,
            reason { BackupCore.scan(ByteArrayInputStream(zip("manifest.json" to """{"format":99,"app":"x"}""")), null) },
        )
        assertEquals(
            BackupReason.UNSAFE,
            reason { BackupCore.scan(ByteArrayInputStream(zip("manifest.json" to manifestJson, "../evil.txt" to "x")), null) },
        )
        assertEquals(
            BackupReason.UNSAFE,
            reason { BackupCore.scan(ByteArrayInputStream(zip("manifest.json" to manifestJson, "other/file.txt" to "x")), null) },
        )
        val big = "x".repeat(5000)
        assertEquals(
            BackupReason.TOO_LARGE,
            reason {
                BackupCore.scan(
                    ByteArrayInputStream(zip("manifest.json" to manifestJson, "chat/bots.json" to big)), null, BackupCore.Limits(maxTotalBytes = 3000),
                )
            },
        )
        assertEquals(
            BackupReason.TOO_MANY_FILES,
            reason {
                BackupCore.scan(
                    ByteArrayInputStream(zip("manifest.json" to manifestJson, "chat/bots.json" to "{}", "chat/usage.json" to "{}")), null,
                    BackupCore.Limits(maxEntries = 2),
                )
            },
        )
        assertEquals(BackupReason.DAMAGED, reason { BackupCore.prefsFromJson("appearance", "not json") })
        // Too large while writing: the export refuses a total above the limit.
        val fx = fixture(File(tmp.root, "reasons"))
        assertEquals(
            BackupReason.TOO_LARGE,
            reason {
                BackupCore.export(ByteArrayOutputStream(), BackupCore.Sources(fx.chat, fx.hermes, fx.prefs), BackupManager.Options(), "t", limits = BackupCore.Limits(maxTotalBytes = 5))
            },
        )
    }

    @Test fun checkEntryClassifiesNames() {
        fun kind(n: String) = (BackupCore.checkEntry(n) as? BackupCore.Check.Accept)?.kind
        assertEquals(BackupCore.Kind.MANIFEST, kind("manifest.json"))
        assertEquals(BackupCore.Kind.CHAT_INDEX, kind("chat/conversations.json"))
        assertEquals(BackupCore.Kind.CHAT_MESSAGE, kind("chat/messages/abc-1_2.json"))
        assertEquals(BackupCore.Kind.ATTACHMENT, kind("attachments/a1b2_Photo.jpg"))
        assertEquals(BackupCore.Kind.PREFS, kind("prefs/presence.json"))
        assertEquals(BackupCore.Kind.HERMES, kind("hermes/SOUL.md"))
        assertEquals(BackupCore.Kind.HERMES, kind("hermes/skills/a/b/SKILL.md"))
        assertEquals(BackupCore.Kind.HERMES, kind("hermes/skills/a/b/references/auth.md"))   // Markdown about "auth" is content, not a credential
        assertNull(kind("hermes/skills/a/b/auth.json"))
        assertNull(kind("hermes/memories/notes.sh"))
        assertNull(kind("hermes/memories/sub/MEMORY.md"))
        assertNull(kind("hermes/hermes-agent/run.py"))
        assertNull(kind("attachments/sub/x.jpg"))
        assertNull(kind("attachments/.env"))
        assertTrue(BackupCore.checkEntry("hermes/skills/a/") is BackupCore.Check.Accept)    // directories get the same name check
        assertTrue(BackupCore.checkEntry("../x") is BackupCore.Check.Reject)
        assertTrue(BackupCore.checkEntry("zzz/x") is BackupCore.Check.Reject)
        assertTrue(BackupCore.isHardSecret("service-account.json"))
        assertTrue(BackupCore.isHardSecret(".env.local"))
        assertTrue(BackupCore.isHardSecret("server.PEM"))
        assertFalse(BackupCore.isHardSecret("SKILL.md"))
    }
}
