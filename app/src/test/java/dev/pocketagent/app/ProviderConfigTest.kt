package dev.pocketagent.app

import dev.pocketagent.app.runtime.ApiEndpoint
import dev.pocketagent.app.runtime.AuthMode
import dev.pocketagent.app.runtime.ProviderConfig
import dev.pocketagent.app.runtime.ProviderInputs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The fixtures under rootfs/smoke/fixtures are the golden files: CI boots the real Hermes gateway
 * with each of them, and this test proves the app generates byte-identical files for the same inputs.
 */
class ProviderConfigTest {
    private val fx = File("../rootfs/smoke/fixtures")
    private val apiKey = "smoke-api-key-0123456789abcdef0123456789"

    private fun check(name: String, i: ProviderInputs) {
        assertEquals("$name.env", File(fx, "$name.env").readText(), ProviderConfig.renderEnv(apiKey, i))
        assertEquals("$name.yaml", File(fx, "$name.yaml").readText(), ProviderConfig.renderYaml(i))
    }

    @Test fun studioMode() = check("studio",
        ProviderInputs(AuthMode.API_KEY, ApiEndpoint.STUDIO, "gemini-4-hydrogen", "global", "", "smoke-gemini-key"))

    @Test fun studioModeWithoutKeyBootsAndIsNotBlocking() {
        val i = ProviderInputs(AuthMode.API_KEY, ApiEndpoint.STUDIO, "gemini-4-hydrogen", "global", "", "")
        check("studio-nokey", i)
        assertEquals(listOf(ProviderConfig.Problem.NO_API_KEY), ProviderConfig.problems(i))
        assertTrue(ProviderConfig.blocking(ProviderConfig.problems(i)).isEmpty())
    }

    @Test fun portIsConfigurable() {
        val e = ProviderConfig.renderEnv(apiKey, ProviderInputs(AuthMode.API_KEY, ApiEndpoint.STUDIO, "m", "global", "", "k"), 40123)
        assertTrue(e.contains("API_SERVER_PORT=40123")); assertFalse(e.contains("8642"))
    }

    @Test fun rejectsBadProjectAndRegion() {
        val bad = ProviderInputs(AuthMode.SERVICE_ACCOUNT, ApiEndpoint.STUDIO, "m", "us central\n1", "Proj;x")
        val p = ProviderConfig.problems(bad)
        assertTrue(ProviderConfig.Problem.INVALID_PROJECT in p && ProviderConfig.Problem.INVALID_REGION in p)
        assertEquals(2, ProviderConfig.blocking(p).size)
        assertTrue(ProviderConfig.problems(ProviderInputs(AuthMode.SERVICE_ACCOUNT, ApiEndpoint.STUDIO, "m", "us-central1", "my_proj-1")).isEmpty())
    }

    @Test fun expressMode() = check("express",
        ProviderInputs(AuthMode.API_KEY, ApiEndpoint.VERTEX_EXPRESS, "gemini-4-argon", "global", "smoke-project", "smoke-gemini-key"))

    @Test fun serviceAccountMode() = check("vertex-sa",
        ProviderInputs(AuthMode.SERVICE_ACCOUNT, ApiEndpoint.STUDIO, "google/gemini-4-argon", "global", "smoke-project"))

    @Test fun userCredentialsMode() = check("vertex-user",
        ProviderInputs(AuthMode.USER_CREDENTIALS, ApiEndpoint.STUDIO, "google/gemini-4-argon", "global", "smoke-project"))

    @Test fun expressWithoutProjectUsesBareHost() {
        val i = ProviderInputs(AuthMode.API_KEY, ApiEndpoint.VERTEX_EXPRESS, "m", "global", "", "k")
        assertEquals("https://aiplatform.googleapis.com", ProviderConfig.baseUrl(i))
        assertFalse(ProviderConfig.renderEnv("x".repeat(40), i).contains("/endpoints/openapi"))
    }

    @Test fun switchingModesLeavesNothingStale() {
        val key = "a".repeat(40)
        val express = ProviderConfig.renderEnv(key, ProviderInputs(AuthMode.API_KEY, ApiEndpoint.VERTEX_EXPRESS, "m", "global", "p", "k"))
        val studio = ProviderConfig.renderEnv(key, ProviderInputs(AuthMode.API_KEY, ApiEndpoint.STUDIO, "m", "global", "p", "k"))
        val sa = ProviderConfig.renderEnv(key, ProviderInputs(AuthMode.SERVICE_ACCOUNT, ApiEndpoint.STUDIO, "m", "global", "p"))
        val user = ProviderConfig.renderEnv(key, ProviderInputs(AuthMode.USER_CREDENTIALS, ApiEndpoint.STUDIO, "m", "global", "p"))
        assertTrue(express.contains("GEMINI_BASE_URL"))
        assertFalse(studio.contains("GEMINI_BASE_URL"))
        assertFalse(sa.contains("GEMINI_"))
        assertTrue(sa.contains("VERTEX_CREDENTIALS_PATH="))
        assertFalse(user.contains("GEMINI_")); assertFalse(user.lines().any { it.startsWith("VERTEX_CREDENTIALS_PATH") || it.startsWith("GOOGLE_APPLICATION_CREDENTIALS") })
        assertFalse(studio.contains("VERTEX_")); assertFalse(express.contains("VERTEX_"))
    }

    @Test fun validation() {
        assertEquals(listOf(ProviderConfig.Problem.NO_API_KEY),
            ProviderConfig.problems(ProviderInputs(AuthMode.API_KEY, ApiEndpoint.STUDIO, "m", "global", "", "")))
        assertEquals(listOf(ProviderConfig.Problem.NO_PROJECT_FOR_USER_CREDENTIALS),
            ProviderConfig.problems(ProviderInputs(AuthMode.USER_CREDENTIALS, ApiEndpoint.STUDIO, "m", "global", "")))
        assertTrue(ProviderConfig.problems(ProviderInputs(AuthMode.SERVICE_ACCOUNT, ApiEndpoint.STUDIO, "m", "global", "")).isEmpty())
    }

    @Test fun hostileValuesCannotInjectYaml() {
        val y = ProviderConfig.renderYaml(ProviderInputs(AuthMode.SERVICE_ACCOUNT, ApiEndpoint.STUDIO, "x\"\nprovider: evil", "g\\", "p\nq"))
        assertEquals(1, Regex("(?m)^  provider:").findAll(y).count())
    }
}
