package dev.pocketagent.app

import dev.pocketagent.app.runtime.ApiEndpoint
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import dev.pocketagent.app.runtime.AuthMode
import dev.pocketagent.app.runtime.GuestOptions
import dev.pocketagent.app.runtime.GuestPrefs
import dev.pocketagent.app.runtime.Paths
import dev.pocketagent.app.runtime.ProviderConfig
import dev.pocketagent.app.runtime.ProviderInputs
import dev.pocketagent.app.runtime.SecretStore
import dev.pocketagent.app.runtime.Settings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.reflect.Proxy

/**
 * The fixtures under rootfs/smoke/fixtures are the golden files: CI boots the real Hermes gateway
 * with each of them, and this test proves the app generates byte-identical files for the same inputs.
 */
class ProviderConfigTest {
    @get:Rule val tmp = TemporaryFolder()

    // The practice flag is process-global: no test order may leak it into another test (or into another class).
    @Before fun practiceOff() { GuestPrefs.practiceActive = false }
    @After fun practiceOffAgain() { GuestPrefs.practiceActive = false }

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
        // the agent block sits between the model part and the guard block
        assertEquals(golden("studio-effort.yaml"),
            ProviderConfig.renderYaml(ProviderInputs(AuthMode.API_KEY, ApiEndpoint.STUDIO, "gemini-4-hydrogen", "global", "", "smoke-gemini-key", "low")))
        // after the vertex block too, and for every allowed level
        val v = ProviderConfig.renderYaml(inputs("xhigh", AuthMode.SERVICE_ACCOUNT))
        assertTrue(v.contains("vertex:\n  project_id: \"smoke-project\"\n  region: \"global\"\nagent:\n  reasoning_effort: \"xhigh\"\n" + GUARD))
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

    // ---- Wave 0: the guard block (fail-closed tool list and privacy switches) ----------------------------------------
    // The literal text lives here once, in the test source, not produced by the function under test.

    private val GUARD = "# Fail-closed tool list and privacy switches (Wave 0 stop-gaps).\n" +
        "platform_toolsets:\n  api_server:\n" +
        "    - web\n    - terminal\n    - file\n    - code_execution\n    - vision\n    - skills\n    - todo\n    - memory\n" +
        "    - session_search\n    - delegation\n" +
        "auxiliary:\n  title_generation:\n    model_upgrade_enabled: false\n" +
        "security:\n  allow_lazy_installs: false\n" +
        "web:\n  keyless_fallback: true\n"

    /** Lead ruling 11.8: in the Anthropic mode Hermes installs its SDK on first use, so the security block is left out. */
    private val GUARD_ANTHROPIC = GUARD.replace("security:\n  allow_lazy_installs: false\n", "")

    private fun everyMode(): List<ProviderInputs> = AuthMode.values().map { m ->
        ProviderInputs(m, ApiEndpoint.STUDIO, "some-model", "global", "smoke-project", "smoke-gemini-key", "", "provider-key-0123456789", "https://x.example.com/v1")
    }

    @Test fun guardBlockTextIsExact() {
        assertEquals(GUARD, ProviderConfig.renderGuardYaml())
        assertEquals(GUARD.replace("keyless_fallback: true", "keyless_fallback: false"), ProviderConfig.renderGuardYaml(GuestOptions(webKeylessFallback = false)))
        assertTrue(GUARD.endsWith("\n")); assertFalse(GUARD.contains("\r"))
    }

    @Test fun everyModeEndsWithTheGuardBlock() {
        assertEquals(7, AuthMode.values().size)
        for (i in everyMode()) {
            val y = ProviderConfig.renderYaml(i)
            val want = if (i.mode == AuthMode.ANTHROPIC) GUARD_ANTHROPIC else GUARD
            assertTrue(i.mode.name, y.endsWith(want))
            assertEquals(i.mode.name, 1, Regex("(?m)^platform_toolsets:").findAll(y).count())
            assertEquals(i.mode.name, ProviderConfig.renderModelYaml(i) + want, y)
        }
    }

    /** The model part is what renderYaml wrote before the guard existed: the existing golden minus the guard proves it byte for byte. */
    @Test fun modelPartIsTheOldOutput() {
        assertEquals(golden("studio.yaml").removeSuffix(GUARD), ProviderConfig.renderModelYaml(
            ProviderInputs(AuthMode.API_KEY, ApiEndpoint.STUDIO, "gemini-4-hydrogen", "global", "", "smoke-gemini-key")))
    }

    @Test fun anthropicKeepsLazyInstalls() {
        for (i in everyMode().filter { it.mode != AuthMode.ANTHROPIC }) {
            assertTrue(i.mode.name, ProviderConfig.renderYaml(i).contains("security:\n  allow_lazy_installs: false\n"))
        }
        val a = ProviderConfig.renderYaml(everyMode().single { it.mode == AuthMode.ANTHROPIC })
        assertFalse(a.contains("security:")); assertFalse(a.contains("allow_lazy_installs"))
        // the other guard lines stay in the Anthropic mode
        assertTrue(a.contains("model_upgrade_enabled: false")); assertTrue(a.contains("keyless_fallback: true")); assertTrue(a.contains("platform_toolsets:"))
        // the practice model is a custom endpoint: lazy installs stay off there
        assertTrue(ProviderConfig.renderPracticeYaml(18080).contains("allow_lazy_installs: false"))
    }

    @Test fun toolListIsFailClosed() {
        val listed = ProviderConfig.renderGuardYaml().lines().filter { it.startsWith("    - ") }.map { it.removePrefix("    - ") }
        assertEquals(ProviderConfig.AGENT_TOOLSETS, listed)
        assertEquals(10, ProviderConfig.AGENT_TOOLSETS.size)
        assertEquals(ProviderConfig.AGENT_TOOLSETS.size, ProviderConfig.AGENT_TOOLSETS.toSet().size)
        val never = ProviderConfig.WITHHELD_TOOLSETS + listOf("clarify", "kanban", "computer_use")
        assertTrue(ProviderConfig.AGENT_TOOLSETS.none { it in never })
        assertEquals(listOf("browser", "cronjob", "image_gen", "tts", "connections"), ProviderConfig.WITHHELD_TOOLSETS)
    }

    /** B13: with model.provider always written Hermes keeps side tasks on the main provider; nothing may be written that would break that. */
    @Test fun sideTasksArePinnedByConstruction() {
        val allowedTop = setOf("model", "vertex", "agent", "platform_toolsets", "auxiliary", "security", "web")
        for (i in everyMode() + listOf(inputs("low"), inputs("high", AuthMode.SERVICE_ACCOUNT))) {
            val y = ProviderConfig.renderYaml(i)
            val top = y.lines().filter { it.isNotEmpty() && !it.startsWith(" ") && !it.startsWith("#") }.map { it.substringBefore(':') }.toSet()
            assertTrue("${i.mode}: $top", allowedTop.containsAll(top))
            assertTrue(i.mode.name, Regex("(?m)^  provider: \\S+").containsMatchIn(y))
            for (word in listOf("free_only", "fallback_providers", "openrouter_model")) assertFalse("${i.mode} $word", y.contains(word))
            val aux = y.substring(y.indexOf("auxiliary:")).substringBefore("security:").substringBefore("web:")
            assertEquals(i.mode.name, "auxiliary:\n  title_generation:\n    model_upgrade_enabled: false\n", aux)
        }
    }

    @Test fun webSwitchIsWritten() {
        val i = ProviderInputs(AuthMode.API_KEY, ApiEndpoint.STUDIO, "gemini-4-hydrogen", "global", "", "smoke-gemini-key")
        val off = ProviderConfig.renderYaml(i, GuestOptions(webKeylessFallback = false))
        assertEquals(golden("studio-nokeyless.yaml"), off)
        assertEquals(golden("studio-nokeyless.env"), ProviderConfig.renderEnv(apiKey, i))
        assertTrue(off.endsWith("web:\n  keyless_fallback: false\n"))
        assertTrue(ProviderConfig.renderYaml(i, GuestOptions(webKeylessFallback = true)).endsWith("web:\n  keyless_fallback: true\n"))
    }

    @Test fun practiceFixturesMatch() {
        assertEquals(golden("practice.yaml"), ProviderConfig.renderPracticeYaml(18080))
        assertEquals(golden("practice.env"), ProviderConfig.renderPracticeEnv(apiKey))
        val env = ProviderConfig.renderPracticeEnv(apiKey, 40123)
        assertTrue(env.contains("API_SERVER_PORT=40123"))
        // no provider key of any kind: the only line with KEY= is the API server's own
        assertEquals(listOf("API_SERVER_KEY=$apiKey"), env.lines().filter { Regex("^[A-Z_]*KEY[A-Z_]*=").containsMatchIn(it) })
        val y = ProviderConfig.renderPracticeYaml(18080)
        assertTrue(y.contains("  api_key: \"practice\"\n"))
        assertTrue(y.contains("  provider: custom\n") && y.contains("  default: \"practice-model\"\n") && y.contains("  base_url: \"http://127.0.0.1:18080/v1\"\n"))
        assertFalse(y.contains("key_env"))
        assertTrue(y.endsWith(GUARD))
        assertTrue(ProviderConfig.renderPracticeYaml(18080, GuestOptions(webKeylessFallback = false)).endsWith("keyless_fallback: false\n"))
    }

    @Test fun savedProblemsAreEmptyWhilePracticeIsActive() {
        val ctx = fakeContext(tmp.newFolder("app", "files"))
        val paths = Paths(ctx); val settings = Settings(ctx)
        val secrets = SecretStore(paths, settings)
        // Negative control: without the practice flag the default setup (API key mode, no key file) reports a missing key, so the
        // empty list below can only come from the practice guard.
        GuestPrefs.practiceActive = false
        assertFalse(ProviderConfig.savedProblems(settings, secrets).isEmpty())
        GuestPrefs.practiceActive = true
        assertTrue(ProviderConfig.savedProblems(settings, secrets).isEmpty())
    }

    private fun fakeContext(files: File): Context {
        val cache = File(files.parentFile, "cache").also { it.mkdirs() }
        val libs = File(files.parentFile, "lib").also { it.mkdirs() }
        val prefs = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, m, args ->
            when (m.name) { "getString", "getInt", "getLong", "getFloat", "getBoolean" -> args[1]; "contains" -> false; else -> null }
        } as SharedPreferences
        return object : ContextWrapper(null) {
            override fun getFilesDir(): File = files
            override fun getCacheDir(): File = cache
            override fun getApplicationInfo(): ApplicationInfo = ApplicationInfo().also { it.nativeLibraryDir = libs.path }
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
        }
    }

    // ---- Phase 3 providers: OpenRouter, Anthropic, OpenAI, custom endpoint -----------------------------------------
    // The three Google tests above are the golden files of the existing modes and are unchanged. The new modes have no CI
    // fixture yet, so their expected text is spelled out here (it follows what Hermes v0.23.3 reads, see ProviderConfig).

    private val header = "# Generated by PocketAgent every time the runtime starts. Do not edit here.\n" +
        "# The API server gives full terminal access to whoever holds the key: loopback only.\n" +
        "API_SERVER_ENABLED=true\nAPI_SERVER_HOST=127.0.0.1\nAPI_SERVER_PORT=8642\nAPI_SERVER_KEY=$apiKey\n"
    private val yamlHeader = "# Generated by PocketAgent every time the runtime starts. Edit the fields in the app instead.\nmodel:\n"

    private fun other(mode: AuthMode, model: String, key: String = "", url: String = "", effort: String = "") =
        ProviderInputs(mode, ApiEndpoint.STUDIO, model, "global", "", "", effort, key, url)

    @Test fun openRouterMode() {
        val i = other(AuthMode.OPENROUTER, "google/gemini-3.8-flash", "sk-or-v1-testkey0123456789")
        assertEquals(header + "OPENROUTER_API_KEY=sk-or-v1-testkey0123456789\n", ProviderConfig.renderEnv(apiKey, i))
        assertEquals(yamlHeader + "  default: \"google/gemini-3.8-flash\"\n  provider: openrouter\n" + GUARD, ProviderConfig.renderYaml(i))
    }

    @Test fun anthropicMode() {
        val i = other(AuthMode.ANTHROPIC, "claude-sonnet-5", "sk-ant-api03-testkey0123456789")
        assertEquals(header + "ANTHROPIC_API_KEY=sk-ant-api03-testkey0123456789\n", ProviderConfig.renderEnv(apiKey, i))
        // Anthropic is the one mode whose guard block leaves allow_lazy_installs out (see anthropicKeepsLazyInstalls)
        assertEquals(yamlHeader + "  default: \"claude-sonnet-5\"\n  provider: anthropic\n" + GUARD_ANTHROPIC, ProviderConfig.renderYaml(i))
    }

    @Test fun openAiMode() {
        val i = other(AuthMode.OPENAI, "gpt-5.4-mini", "sk-proj-testkey0123456789")
        assertEquals(header + "OPENAI_API_KEY=sk-proj-testkey0123456789\n", ProviderConfig.renderEnv(apiKey, i))
        assertEquals(yamlHeader + "  default: \"gpt-5.4-mini\"\n  provider: openai-api\n" + GUARD, ProviderConfig.renderYaml(i))
    }

    @Test fun customModeWithKey() {
        val i = other(AuthMode.CUSTOM, "llama3.1:8b", "custom-key-0123456789", "http://192.168.1.20:11434/v1/")
        assertEquals(header + "AGIRI_CUSTOM_API_KEY=custom-key-0123456789\n", ProviderConfig.renderEnv(apiKey, i))
        assertEquals(
            yamlHeader + "  default: \"llama3.1:8b\"\n  provider: custom\n  base_url: \"http://192.168.1.20:11434/v1\"\n  key_env: \"AGIRI_CUSTOM_API_KEY\"\n" + GUARD,
            ProviderConfig.renderYaml(i))
        // The key itself never goes into config.yaml.
        assertFalse(ProviderConfig.renderYaml(i).contains("custom-key-0123456789"))
    }

    @Test fun customModeWithoutKeyIsFineForLocalServers() {
        val i = other(AuthMode.CUSTOM, "qwen3:4b", "", "https://llm.example.com/v1")
        assertEquals(header + "# AGIRI_CUSTOM_API_KEY is not set (fine for servers that need no key)\n", ProviderConfig.renderEnv(apiKey, i))
        assertEquals(yamlHeader + "  default: \"qwen3:4b\"\n  provider: custom\n  base_url: \"https://llm.example.com/v1\"\n" + GUARD, ProviderConfig.renderYaml(i))
        assertTrue(ProviderConfig.problems(i).isEmpty())
    }

    @Test fun newModesWithoutKeyStillBootAndAreNotBlocking() {
        for (m in listOf(AuthMode.OPENROUTER, AuthMode.ANTHROPIC, AuthMode.OPENAI)) {
            val i = other(m, "some-model")
            assertTrue(m.name, ProviderConfig.renderEnv(apiKey, i).contains("_API_KEY is not set yet (enter it in the app)\n"))
            assertEquals(m.name, listOf(ProviderConfig.Problem.NO_API_KEY), ProviderConfig.problems(i))
            assertTrue(m.name, ProviderConfig.blocking(ProviderConfig.problems(i)).isEmpty())
        }
    }

    @Test fun effortIsWrittenForTheNewModesToo() {
        val y = ProviderConfig.renderYaml(other(AuthMode.ANTHROPIC, "claude-sonnet-5", "k", effort = "low"))
        assertEquals(yamlHeader + "  default: \"claude-sonnet-5\"\n  provider: anthropic\nagent:\n  reasoning_effort: \"low\"\n" + GUARD_ANTHROPIC, y)
        val c = ProviderConfig.renderYaml(other(AuthMode.CUSTOM, "m", "k", "https://x.example.com", "high"))
        assertTrue(c.endsWith("  key_env: \"AGIRI_CUSTOM_API_KEY\"\nagent:\n  reasoning_effort: \"high\"\n" + GUARD))
    }

    @Test fun newModesNeverWriteAVertexBlockOrAnotherProvidersVariables() {
        val key = "k".repeat(30)
        for (m in listOf(AuthMode.OPENROUTER, AuthMode.ANTHROPIC, AuthMode.OPENAI, AuthMode.CUSTOM)) {
            val i = ProviderInputs(m, ApiEndpoint.VERTEX_EXPRESS, "m", "us-central1", "some-project", "gemini-key-should-not-appear", "", key, "https://x.example.com/v1")
            val env = ProviderConfig.renderEnv(apiKey, i); val y = ProviderConfig.renderYaml(i)
            assertFalse(m.name, y.contains("vertex:")); assertFalse(m.name, y.contains("project_id"))
            assertFalse(m.name, env.contains("GEMINI_")); assertFalse(m.name, env.contains("VERTEX_")); assertFalse(m.name, env.contains("gemini-key-should-not-appear"))
            // exactly one provider key variable
            assertEquals(m.name, 1, Regex("(?m)^[A-Z_]+_API_KEY=").findAll(env).count { !it.value.startsWith("API_SERVER") })
        }
        // and the other way round: a Google mode ignores a leftover provider key and URL
        val g = ProviderInputs(AuthMode.API_KEY, ApiEndpoint.STUDIO, "gemini-4-hydrogen", "global", "", "smoke-gemini-key", "", "sk-or-leftover-key-123456", "https://x.example.com")
        assertEquals(golden("studio.env"), ProviderConfig.renderEnv(apiKey, g))
        assertEquals(golden("studio.yaml"), ProviderConfig.renderYaml(g))
    }

    @Test fun providerNames() {
        val names = AuthMode.values().associateWith { ProviderConfig.providerName(other(it, "m")) }
        assertEquals(mapOf(
            AuthMode.API_KEY to "gemini", AuthMode.SERVICE_ACCOUNT to "vertex", AuthMode.USER_CREDENTIALS to "vertex",
            AuthMode.OPENROUTER to "openrouter", AuthMode.ANTHROPIC to "anthropic", AuthMode.OPENAI to "openai-api", AuthMode.CUSTOM to "custom"), names)
    }

    @Test fun authModeFlags() {
        assertEquals(setOf(AuthMode.API_KEY, AuthMode.SERVICE_ACCOUNT, AuthMode.USER_CREDENTIALS), AuthMode.values().filter { it.google }.toSet())
        assertEquals(setOf(AuthMode.API_KEY, AuthMode.OPENROUTER, AuthMode.ANTHROPIC, AuthMode.OPENAI, AuthMode.CUSTOM), AuthMode.values().filter { it.usesApiKey }.toSet())
    }

    @Test fun keyValidationForTheNewModes() {
        assertEquals(listOf(ProviderConfig.Problem.NO_API_KEY), ProviderConfig.problems(other(AuthMode.OPENROUTER, "m", "")))
        assertEquals(listOf(ProviderConfig.Problem.INVALID_API_KEY_FORMAT), ProviderConfig.problems(other(AuthMode.OPENROUTER, "m", "sk-proj-abc")))
        assertTrue(ProviderConfig.problems(other(AuthMode.OPENROUTER, "m", "sk-or-v1-abc")).isEmpty())
        // the form passes this stand-in while a key is stored
        assertTrue(ProviderConfig.problems(other(AuthMode.OPENROUTER, "m", ProviderConfig.STORED_KEY_PLACEHOLDER)).isEmpty())
        // Anthropic and OpenAI key shapes are not second-guessed
        assertTrue(ProviderConfig.problems(other(AuthMode.ANTHROPIC, "m", "anything-goes")).isEmpty())
        assertEquals(listOf(ProviderConfig.Problem.NO_API_KEY), ProviderConfig.problems(other(AuthMode.OPENAI, "m", "  ")))
        assertEquals(listOf(ProviderConfig.Problem.EMPTY_MODEL, ProviderConfig.Problem.NO_API_KEY), ProviderConfig.problems(other(AuthMode.ANTHROPIC, "", "")))
        // project / region belong to Google only: leftovers there never block another provider
        val i = ProviderInputs(AuthMode.ANTHROPIC, ApiEndpoint.VERTEX_EXPRESS, "m", "us central\n1", "Proj;x", "", "", "k")
        assertTrue(ProviderConfig.problems(i).isEmpty())
    }

    @Test fun customEndpointValidationAndBlocking() {
        assertEquals(listOf(ProviderConfig.Problem.NO_BASE_URL), ProviderConfig.problems(other(AuthMode.CUSTOM, "m", "", "")))
        val bad = other(AuthMode.CUSTOM, "m", "", "http://example.com/v1")
        assertEquals(listOf(ProviderConfig.Problem.INVALID_BASE_URL), ProviderConfig.problems(bad))
        assertEquals(listOf(ProviderConfig.Problem.INVALID_BASE_URL), ProviderConfig.blocking(ProviderConfig.problems(bad)))
        // a missing address is not blocking (like a missing key: Hermes boots), an invalid one is
        assertTrue(ProviderConfig.blocking(ProviderConfig.problems(other(AuthMode.CUSTOM, "m", "", ""))).isEmpty())
        // an invalid address is never written
        assertFalse(ProviderConfig.renderYaml(bad).contains("base_url"))
        assertFalse(ProviderConfig.renderYaml(other(AuthMode.CUSTOM, "m", "", "https://x.example.com/v1\nprovider: evil")).contains("evil"))
        assertEquals("https://x.example.com/v1", ProviderConfig.baseUrl(other(AuthMode.CUSTOM, "m", "", "https://x.example.com/v1/")))
        assertEquals(null, ProviderConfig.baseUrl(other(AuthMode.OPENAI, "m", "k", "https://x.example.com/v1")))
    }

    @Test fun acceptedBaseUrls() {
        val ok = mapOf(
            "https://openrouter.ai/api/v1" to "https://openrouter.ai/api/v1",
            "  HTTPS://Example.COM/v1/  " to "https://example.com/v1",
            "https://example.com" to "https://example.com",
            "https://example.com:8443/openai/v1" to "https://example.com:8443/openai/v1",
            "https://8.8.8.8/v1" to "https://8.8.8.8/v1",
            "http://localhost:11434/v1" to "http://localhost:11434/v1",
            "http://app.localhost/v1" to "http://app.localhost/v1",
            "http://127.0.0.1:1234/v1" to "http://127.0.0.1:1234/v1",
            "http://192.168.1.5:8000" to "http://192.168.1.5:8000",
            "http://10.0.0.2/v1" to "http://10.0.0.2/v1",
            "http://172.16.0.1/v1" to "http://172.16.0.1/v1",
            "http://172.31.255.1/v1" to "http://172.31.255.1/v1",
            "http://169.254.10.1/v1" to "http://169.254.10.1/v1",
            "http://100.101.102.103:8080/v1" to "http://100.101.102.103:8080/v1",
            "http://[::1]:8080/v1" to "http://[::1]:8080/v1",
            "http://my-pc.local:8080/v1" to "http://my-pc.local:8080/v1",
        )
        for ((raw, want) in ok) assertEquals(raw, want, ProviderConfig.normalizeBaseUrl(raw))
    }

    @Test fun rejectedBaseUrls() {
        val bad = listOf(
            "", "   ", "ftp://example.com/v1", "javascript:alert(1)", "example.com/v1", "https:///v1", "https://",
            "http://example.com/v1",            // plain http to a public host
            "http://8.8.8.8/v1", "http://172.32.0.1/v1", "http://172.15.0.1/v1", "http://192.169.1.1/v1", "http://100.128.0.1/v1",
            "http://127.0.0.1.evil.com/v1", "http://010.0.0.1/v1", "http://localhost.evil.com/v1", "http://[2001:db8::1]/v1",
            "https://user:pw@example.com/v1", "https://user@example.com/v1",
            "https://example.com/v1?key=1", "https://example.com/v1#x", "https://example.com/v1;x",
            "https://example.com/a b", "https://exa mple.com", "https://exa\"mple.com", "https://example.com/v1\nprovider: evil",
            "https://example.com/v1\\x", "https://example.com:0/v1", "https://example.com:70000/v1", "https://example.com:99999999999/v1",
            "https://example.com/../x", "https://example.com/./x", "https://-bad.com", "https://exa_mple.com", "https://example..com",
            "https://example.com/" + "a".repeat(300),
        )
        for (raw in bad) assertEquals(raw, null, ProviderConfig.normalizeBaseUrl(raw))
    }

    @Test fun reportNeverShowsARejectedBaseUrl() {
        assertEquals("(none)", ProviderConfig.baseUrlForReport("  "))
        assertEquals("https://example.com/v1", ProviderConfig.baseUrlForReport("https://example.com/v1/"))
        for (raw in listOf("https://admin:s3cretPw@llm.example.com/v1", "https://example.com/v1?sig=abcdef123")) {
            val out = ProviderConfig.baseUrlForReport(raw)
            assertFalse(out, out.contains("s3cretPw") || out.contains("admin") || out.contains("abcdef123") || out.contains("example"))
            assertTrue(out, out.startsWith("(invalid, not shown"))
        }
    }

    @Test fun hostileKeysCannotInjectEnvLines() {
        val env = ProviderConfig.renderEnv(apiKey, other(AuthMode.CUSTOM, "m", "abc\nEVIL=1\r\nOTHER=2", "https://x.example.com"))
        assertEquals(1, env.lines().count { it.startsWith("AGIRI_CUSTOM_API_KEY=") })
        assertFalse(env.lines().any { it.startsWith("EVIL") || it.startsWith("OTHER") })
    }

    @Test fun describeNeverShowsAKey() {
        val d = ProviderConfig.describe(other(AuthMode.OPENROUTER, "m", "sk-or-v1-supersecretvalue"))
        assertFalse(d.contains("supersecretvalue"))
        assertTrue(d.contains("provider=openrouter") && d.contains("api_key=present(len=25)"))
        val c = ProviderConfig.describe(other(AuthMode.CUSTOM, "m", "k-secret-value", "https://x.example.com/v1"))
        assertFalse(c.contains("k-secret-value")); assertTrue(c.contains("base_url=https://x.example.com/v1"))
        assertTrue(ProviderConfig.describe(other(AuthMode.ANTHROPIC, "m")).contains("api_key=absent"))
    }

    @Test fun suggestionsAndDefaultsOfTheNewModes() {
        assertEquals("google/gemini-3.8-flash", ProviderConfig.defaultModel(AuthMode.OPENROUTER, ApiEndpoint.STUDIO))
        assertEquals("claude-sonnet-5", ProviderConfig.defaultModel(AuthMode.ANTHROPIC, ApiEndpoint.STUDIO))
        assertEquals("gpt-5.4-mini", ProviderConfig.defaultModel(AuthMode.OPENAI, ApiEndpoint.STUDIO))
        assertEquals("", ProviderConfig.defaultModel(AuthMode.CUSTOM, ApiEndpoint.STUDIO))
        for (m in AuthMode.values()) {
            val s = ProviderConfig.suggestions(m, ApiEndpoint.STUDIO)
            val def = ProviderConfig.defaultModel(m, ApiEndpoint.STUDIO)
            if (m != AuthMode.CUSTOM) assertTrue("$m default is offered", def in s)
            assertEquals(m.name, s.size, s.toSet().size)
        }
        assertTrue(ProviderConfig.suggestions(AuthMode.OPENROUTER, ApiEndpoint.STUDIO).all { "/" in it })
        assertTrue(ProviderConfig.suggestions(AuthMode.ANTHROPIC, ApiEndpoint.STUDIO).none { "/" in it })
        assertTrue(ProviderConfig.suggestions(AuthMode.CUSTOM, ApiEndpoint.STUDIO).isEmpty())
    }

    @Test fun probeHostsKeepTheGoogleBehaviourAndStayShellSafe() {
        assertEquals(listOf("generativelanguage.googleapis.com"), ProviderConfig.probeHosts(AuthMode.API_KEY, ApiEndpoint.STUDIO, "global", ""))
        assertEquals(listOf("aiplatform.googleapis.com"), ProviderConfig.probeHosts(AuthMode.API_KEY, ApiEndpoint.VERTEX_EXPRESS, "global", ""))
        assertEquals(listOf("aiplatform.googleapis.com", "oauth2.googleapis.com"), ProviderConfig.probeHosts(AuthMode.SERVICE_ACCOUNT, ApiEndpoint.STUDIO, "global", ""))
        assertEquals(listOf("europe-west4-aiplatform.googleapis.com", "oauth2.googleapis.com"), ProviderConfig.probeHosts(AuthMode.USER_CREDENTIALS, ApiEndpoint.STUDIO, "europe-west4", ""))
        assertEquals(listOf("aiplatform.googleapis.com", "oauth2.googleapis.com"), ProviderConfig.probeHosts(AuthMode.USER_CREDENTIALS, ApiEndpoint.STUDIO, "bad region;x", ""))
        assertEquals(listOf("openrouter.ai"), ProviderConfig.probeHosts(AuthMode.OPENROUTER, ApiEndpoint.STUDIO, "global", ""))
        assertEquals(listOf("api.anthropic.com"), ProviderConfig.probeHosts(AuthMode.ANTHROPIC, ApiEndpoint.STUDIO, "global", ""))
        assertEquals(listOf("api.openai.com"), ProviderConfig.probeHosts(AuthMode.OPENAI, ApiEndpoint.STUDIO, "global", ""))
        assertEquals(listOf("llm.example.com"), ProviderConfig.probeHosts(AuthMode.CUSTOM, ApiEndpoint.STUDIO, "global", "https://llm.example.com:8443/v1"))
        // plain http, IP literals and invalid addresses are not probed (the host goes into a shell script)
        assertTrue(ProviderConfig.probeHosts(AuthMode.CUSTOM, ApiEndpoint.STUDIO, "global", "http://192.168.1.5:8000/v1").isEmpty())
        assertTrue(ProviderConfig.probeHosts(AuthMode.CUSTOM, ApiEndpoint.STUDIO, "global", "https://8.8.8.8/v1").isEmpty())
        assertTrue(ProviderConfig.probeHosts(AuthMode.CUSTOM, ApiEndpoint.STUDIO, "global", "https://x.com/v1; rm -rf /").isEmpty())
    }
}
