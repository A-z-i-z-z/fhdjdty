package dev.pocketagent.app

import dev.pocketagent.app.runtime.HermesConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HermesConfigTest {
    private val tpl = File("../runtime/hermes-config.yaml.tpl").readText()
    private val envTpl = File("../runtime/hermes.env.tpl").readText()

    @Test fun rendersConfigWithAndWithoutProject() {
        val a = HermesConfig.renderConfig(tpl, "google/gemini-4-argon", "global", "")
        assertTrue(a.contains("default: \"google/gemini-4-argon\""))
        assertTrue(a.contains("provider: vertex"))
        assertTrue(a.contains("  region: \"global\""))
        assertFalse(a.contains("project_id"))
        val b = HermesConfig.renderConfig(tpl, "m", "us-central1", " my-proj ")
        assertTrue(b.contains("  project_id: \"my-proj\"\n  region: \"us-central1\""))
        assertFalse(b.contains("{{"))
    }

    @Test fun escapesHostileValues() {
        val a = HermesConfig.renderConfig(tpl, "x\"\nfoo: bar", "g\\", "")
        assertFalse(a.contains("\nfoo: bar"))
        assertEquals(1, Regex("(?m)^model:").findAll(a).count())
    }

    @Test fun rendersEnv() {
        val e = HermesConfig.renderEnv(envTpl, "k3y", "/root/.hermes/secrets/vertex-sa.json")
        assertTrue(e.contains("API_SERVER_KEY=k3y"))
        assertTrue(e.contains("API_SERVER_HOST=127.0.0.1"))
        assertTrue(e.contains("VERTEX_CREDENTIALS_PATH=/root/.hermes/secrets/vertex-sa.json"))
        assertFalse(e.contains("{{"))
    }
}
