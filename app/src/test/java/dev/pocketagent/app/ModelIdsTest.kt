package dev.pocketagent.app

import dev.pocketagent.app.data.AttachmentStore
import dev.pocketagent.app.data.ModelIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ModelIdsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun anOverrideFromAnotherProviderDoesNotFit() {
        assertFalse(ModelIds.fits("anthropic", "gemini-4-hydrogen"))
        assertFalse(ModelIds.fits("anthropic", "google/gemini-4-argon"))
        assertFalse(ModelIds.fits("openai-api", "gemini-4-hydrogen"))
        assertFalse(ModelIds.fits("openai-api", "claude-sonnet-4"))
        assertFalse(ModelIds.fits("gemini", "claude-sonnet-4"))
        assertFalse(ModelIds.fits("vertex", "gpt-5"))
        assertFalse(ModelIds.fits("anthropic", "gpt-5"))
    }

    @Test fun matchingUnknownBlankAndCustomIdsFit() {
        assertTrue(ModelIds.fits("vertex", "gemini-4-neon"))
        assertTrue(ModelIds.fits("vertex", "google/gemini-4-neon"))
        assertTrue(ModelIds.fits("gemini", "gemini-4-neon"))
        assertTrue(ModelIds.fits("anthropic", "claude-sonnet-4"))
        assertTrue(ModelIds.fits("openai-api", "gpt-5"))
        assertTrue(ModelIds.fits("openai-api", "o3-mini"))
        assertTrue(ModelIds.fits("openrouter", "gemini-4-hydrogen"))
        assertTrue(ModelIds.fits("custom", "gemini-4-hydrogen"))
        assertTrue(ModelIds.fits("anthropic", "some-new-model"))
        assertTrue(ModelIds.fits("anthropic", "  "))
    }

    @Test fun openRouterGetsTheVendorPrefixForBareIds() {
        assertEquals("google/gemini-4-hydrogen", ModelIds.normalize("openrouter", "gemini-4-hydrogen"))
        assertEquals("anthropic/claude-sonnet-4", ModelIds.normalize("openrouter", "claude-sonnet-4"))
        assertEquals("openai/gpt-5", ModelIds.normalize("openrouter", "gpt-5"))
        assertEquals("meta-llama/llama-4", ModelIds.normalize("openrouter", "meta-llama/llama-4"))
        assertEquals("mystery", ModelIds.normalize("openrouter", "mystery"))
    }

    @Test fun attachmentPathsResolveOnlyInsideTheAttachmentsFolder() {
        val dir = File(tmp.root, "attachments").also { it.mkdirs() }
        File(dir, "abc.jpg").writeText("x")
        File(tmp.root, "secret.txt").writeText("s")
        // Same file name under another absolute prefix (restore onto another Android user) still resolves.
        val moved = AttachmentStore.resolveIn(dir, "/data/user/10/dev.pocketagent.app/files/chat/attachments/abc.jpg")
        assertNotNull(moved); assertEquals(File(dir, "abc.jpg").canonicalFile, moved!!.canonicalFile)
        assertNotNull(AttachmentStore.resolveIn(dir, File(dir, "abc.jpg").absolutePath))
        // Anything that tries to leave the folder, or is not a plain file name, does not.
        assertNull(AttachmentStore.resolveIn(dir, "../secret.txt/.."))
        assertNull(AttachmentStore.resolveIn(dir, ".."))
        assertNull(AttachmentStore.resolveIn(dir, "/data/data/dev.pocketagent.app/files/secrets/"))
        assertNull(AttachmentStore.resolveIn(dir, ".hidden"))
        assertNull(AttachmentStore.resolveIn(dir, "a b.jpg"))
        assertNull(AttachmentStore.resolveIn(dir, ""))
        assertNull(AttachmentStore.resolveIn(dir, "x".repeat(201)))
    }
}
