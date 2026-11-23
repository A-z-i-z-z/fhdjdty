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

    // The generator only ever emits LF. .gitattributes pins the fixtures to LF; the normalisation stays as a harmless convenience for
    // Windows working trees checked out before that rule existed.
    private fun golden(name: String) = File(fx, name).readText().replace("\r\n", "\n")

    private fun check(name: String, i: ProviderInputs) {
        assertEquals("$name.env", golden("$name.env"), ProviderConfig.renderEnv(apiKey, i))
        assertEquals("$name.yaml", golden("$name.yaml"), ProviderConfig.renderYaml(i))
    }

    @Test fun studioMode() = check("studio",
        ProviderInputs(AuthMode.API_KEY, ApiEndpoint.STUDIO, "gemini-4-hydrogen", "global", "", "smoke-gemini-key"))

    /** The same fixture CI boots the real Hermes with: proves Hermes boots with an `agent:` block (not whether the level reaches Google on the wire). */
    @Test fun studioModeWithThinkingEffortLow() = check("studio-effort",
        ProviderInputs(AuthMode.API_KEY, ApiEndpoint.STUDIO, "gemini-4-hydrogen", "global", "", "smoke-gemini-key", "low"))

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

    private fun inputs(effort: String, mode: AuthMode = AuthMode.API_KEY) =
        ProviderInputs(mode, ApiEndpoint.STUDIO, "gemini-4-hydrogen", "global", "smoke-project", "k", effort)

    @Test fun effortNotSetWritesNoAgentBlock() {
        val y = ProviderConfig.renderYaml(inputs(""))
        assertFalse(y.contains("agent:")); assertFalse(y.contains("reasoning_effort"))
        // same bytes as the golden fixture: the new field must not change existing output
        assertEquals(golden("studio.yaml"), ProviderConfig.renderYaml(ProviderInputs(AuthMode.API_KEY, ApiEndpoint.STUDIO, "gemini-4-hydrogen", "global", "", "smoke-gemini-key", "")))
    }

    @Test fun effortSetIsWrittenAsAgentBlock() {
        assertEquals(golden("studio.yaml") + "agent:\n  reasoning_effort: \"low\"\n",
            ProviderConfig.renderYaml(ProviderInputs(AuthMode.API_KEY, ApiEndpoint.STUDIO, "gemini-4-hydrogen", "global", "", "smoke-gemini-key", "low")))
        // after the vertex block too, and for every allowed level
        val v = ProviderConfig.renderYaml(inputs("xhigh", AuthMode.SERVICE_ACCOUNT))
        assertTrue(v.endsWith("vertex:\n  project_id: \"smoke-project\"\n  region: \"global\"\nagent:\n  reasoning_effort: \"xhigh\"\n"))
        for (e in ProviderConfig.REASONING_EFFORTS) {
            assertTrue(e, ProviderConfig.problems(inputs(e)).isEmpty())
            assertTrue(e, ProviderConfig.renderYaml(inputs(e)).contains("  reasoning_effort: \"$e\"\n"))
        }
        assertEquals(listOf("none", "minimal", "low", "medium", "high", "xhigh"), ProviderConfig.REASONING_EFFORTS)
    }

    @Test fun invalidEffortIsRejectedAndNeverWritten() {
        for (bad in listOf("LOW", "ultra", "low\nprovider: evil", "\"", "medium ; x")) {
            val i = inputs(bad)
            assertTrue(bad, ProviderConfig.Problem.INVALID_REASONING_EFFORT in ProviderConfig.problems(i))
            assertTrue(bad, ProviderConfig.Problem.INVALID_REASONING_EFFORT in ProviderConfig.blocking(ProviderConfig.problems(i)))
            assertFalse(bad, ProviderConfig.renderYaml(i).contains("agent:"))
        }
        assertTrue(ProviderConfig.problems(inputs("  ")).isEmpty())   // blank = not set
    }

    @Test fun describeShowsTheEffort() {
        assertTrue(ProviderConfig.describe(inputs("")).contains("reasoning_effort=(default)"))
        assertTrue(ProviderConfig.describe(inputs("low")).contains("reasoning_effort=low"))
    }

    @Test fun suggestionsOfferTheGemini4FamilyFirst() {
        val plain = ProviderConfig.suggestions(AuthMode.API_KEY, ApiEndpoint.STUDIO)
        assertEquals(listOf("gemini-4-neon", "gemini-4-carbon", "gemini-4-argon", "gemini-4-hydrogen"), plain.take(4))
        assertTrue("gemini-3.8-flash" in plain && "gemini-3.7-flash" in plain)
        for (mode in listOf(AuthMode.SERVICE_ACCOUNT, AuthMode.USER_CREDENTIALS)) {
            val v = ProviderConfig.suggestions(mode, ApiEndpoint.STUDIO)
            assertEquals(plain.take(4).map { "google/$it" }, v.take(4))
            assertTrue(v.all { it.startsWith("google/") }); assertTrue("google/gemini-3.8-flash" in v)
        }
        // the defaults are unchanged
        assertEquals("gemini-4-hydrogen", ProviderConfig.defaultModel(AuthMode.API_KEY, ApiEndpoint.STUDIO))
        assertEquals("google/gemini-4-argon", ProviderConfig.defaultModel(AuthMode.SERVICE_ACCOUNT, ApiEndpoint.STUDIO))
    }

    @Test fun hostileValuesCannotInjectYaml() {
        val y = ProviderConfig.renderYaml(ProviderInputs(AuthMode.SERVICE_ACCOUNT, ApiEndpoint.STUDIO, "x\"\nprovider: evil", "g\\", "p\nq"))
        assertEquals(1, Regex("(?m)^  provider:").findAll(y).count())
    }
}
