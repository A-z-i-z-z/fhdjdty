package dev.pocketagent.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import dev.pocketagent.app.runtime.ApiEndpoint
import dev.pocketagent.app.runtime.AuthMode
import dev.pocketagent.app.runtime.CredentialAction
import dev.pocketagent.app.runtime.GuestOptions
import dev.pocketagent.app.runtime.HermesConfig
import dev.pocketagent.app.runtime.Paths
import dev.pocketagent.app.runtime.ProviderConfig
import dev.pocketagent.app.runtime.ProviderInputs
import dev.pocketagent.app.runtime.SecretStore
import dev.pocketagent.app.runtime.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.reflect.Proxy

/**
 * The pure planning of the guest files ([HermesConfig.plan]) and the file work of [HermesConfig.apply]: practice mode never reads the
 * owner's provider setup and never writes or deletes a credential file; every other mode installs exactly what it needs.
 */
class HermesConfigPlanTest {
    @get:Rule val tmp = TemporaryFolder()

    private val apiKey = "smoke-api-key-0123456789abcdef0123456789"
    private val geminiKey = "gemini-secret-key-for-the-test-0123456789"

    private fun inputs(mode: AuthMode) =
        ProviderInputs(mode, ApiEndpoint.STUDIO, "some-model", "global", "smoke-project", geminiKey, "", "provider-key-0123456789", "https://x.example.com/v1")

    @Test fun practiceInstallsNoCredentialsAndNoProviderKey() {
        // Every credential and every key is present; practice must still write none of them and touch no file.
        for (mode in AuthMode.values()) {
            val f = HermesConfig.plan(inputs(mode), apiKey, 8642, GuestOptions(practicePort = 1234), hasServiceAccount = true, hasUserCredentials = true)
            assertEquals(mode.name, CredentialAction.UNTOUCHED, f.credentials)
            assertFalse(mode.name, f.env.contains(geminiKey)); assertFalse(mode.name, f.env.contains("provider-key-0123456789"))
            assertFalse(mode.name, f.env.contains("GEMINI")); assertFalse(mode.name, f.env.contains("VERTEX"))
            assertFalse(mode.name, f.yaml.contains("vertex:"))
            assertEquals(mode.name, ProviderConfig.renderPracticeEnv(apiKey, 8642), f.env)
            assertEquals(mode.name, ProviderConfig.renderPracticeYaml(1234, GuestOptions(practicePort = 1234)), f.yaml)
            assertTrue(mode.name, f.yaml.contains("  base_url: \"http://127.0.0.1:1234/v1\"\n"))
        }
    }

    @Test fun practiceKeepsTheWebSwitch() {
        val f = HermesConfig.plan(inputs(AuthMode.API_KEY), apiKey, 8642, GuestOptions(webKeylessFallback = false, practicePort = 9), true, true)
        assertTrue(f.yaml.endsWith("web:\n  keyless_fallback: false\n"))
    }

    @Test fun normalModeInstallsOnlyWhatTheModeNeeds() {
        val o = GuestOptions()
        assertEquals(CredentialAction.INSTALL_SA, HermesConfig.plan(inputs(AuthMode.SERVICE_ACCOUNT), apiKey, 8642, o, hasServiceAccount = true, hasUserCredentials = true).credentials)
        assertEquals(CredentialAction.INSTALL_ADC, HermesConfig.plan(inputs(AuthMode.USER_CREDENTIALS), apiKey, 8642, o, hasServiceAccount = true, hasUserCredentials = true).credentials)
        // a Google mode whose file is missing, and every other mode, clear both files (today's behaviour)
        assertEquals(CredentialAction.REMOVE_BOTH, HermesConfig.plan(inputs(AuthMode.SERVICE_ACCOUNT), apiKey, 8642, o, hasServiceAccount = false, hasUserCredentials = true).credentials)
        assertEquals(CredentialAction.REMOVE_BOTH, HermesConfig.plan(inputs(AuthMode.USER_CREDENTIALS), apiKey, 8642, o, hasServiceAccount = true, hasUserCredentials = false).credentials)
        for (m in AuthMode.values().filter { !it.google || it == AuthMode.API_KEY }) {
            assertEquals(m.name, CredentialAction.REMOVE_BOTH, HermesConfig.plan(inputs(m), apiKey, 8642, o, hasServiceAccount = true, hasUserCredentials = true).credentials)
        }
    }

    @Test fun normalModeWritesTheSameFilesAsBefore() {
        val i = inputs(AuthMode.API_KEY)
        val f = HermesConfig.plan(i, apiKey, 40123, GuestOptions(webKeylessFallback = false), false, false)
        assertEquals(ProviderConfig.renderEnv(apiKey, i, 40123), f.env)
        assertEquals(ProviderConfig.renderYaml(i, GuestOptions(webKeylessFallback = false)), f.yaml)
        assertTrue(f.env.contains("GEMINI_API_KEY=$geminiKey"))
    }

    @Test fun launchCommandFormsAreExact() {
        val l = "/data/user/0/x/files/runtime/pocketagent-proot.sh"
        assertEquals(listOf("/system/bin/sh", l, "--", "hermes", "gateway", "run", "--replace", "-v"), HermesConfig.launchCommand(l, null))
        assertEquals(listOf("/system/bin/sh", l, "--", "sh", "/opt/agiri/practice/start.sh", "18080"), HermesConfig.launchCommand(l, 18080))
    }

    // ---- apply: the file work -------------------------------------------------------------------------------------------

    private class Fixture(val paths: Paths, val settings: Settings, val secrets: SecretStore)

    private fun fixture(): Fixture {
        val files = tmp.newFolder("app", "files")
        val cache = File(files.parentFile, "cache").also { it.mkdirs() }
        val libs = File(files.parentFile, "lib").also { it.mkdirs() }
        val prefs = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, m, args ->
            when (m.name) { "getString", "getInt", "getLong", "getFloat", "getBoolean" -> args[1]; "contains" -> false; else -> null }
        } as SharedPreferences
        val ctx: Context = object : ContextWrapper(null) {
            override fun getFilesDir(): File = files
            override fun getCacheDir(): File = cache
            override fun getApplicationInfo(): ApplicationInfo = ApplicationInfo().also { it.nativeLibraryDir = libs.path }
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
        }
        val paths = Paths(ctx); val settings = Settings(ctx)
        val secrets = SecretStore(paths, settings)
        paths.geminiKeyFile.writeText(geminiKey)
        paths.guestServiceAccount.parentFile!!.mkdirs()
        paths.guestServiceAccount.writeText("{\"guest\":\"service account\"}")
        paths.guestAdcFile.parentFile!!.mkdirs()
        paths.guestAdcFile.writeText("{\"guest\":\"adc\"}")
        return Fixture(paths, settings, secrets)
    }

    @Test fun practiceLeavesCredentialsUntouched() {
        // The contract's name (3.10). Plan level here; the file level (nothing read, nothing written) follows below.
        for (mode in AuthMode.values()) {
            assertEquals(mode.name, CredentialAction.UNTOUCHED,
                HermesConfig.plan(inputs(mode), apiKey, 8642, GuestOptions(practicePort = 1), true, true).credentials)
        }
    }

    @Test fun applyInPracticeNeitherReadsTheOwnersKeyNorTouchesTheCredentialFiles() {
        val f = fixture()
        HermesConfig.apply(f.paths, f.settings, f.secrets, apiKey, 8642, GuestOptions(practicePort = 18080))
        val env = f.paths.hermesEnv.readText(); val yaml = f.paths.hermesConfig.readText()
        assertFalse(env.contains(geminiKey)); assertFalse(env.contains("GEMINI"))
        assertEquals(ProviderConfig.renderPracticeEnv(apiKey, 8642), env)
        assertEquals(ProviderConfig.renderPracticeYaml(18080), yaml)
        assertTrue(f.paths.guestServiceAccount.isFile); assertEquals("{\"guest\":\"service account\"}", f.paths.guestServiceAccount.readText())
        assertTrue(f.paths.guestAdcFile.isFile); assertEquals("{\"guest\":\"adc\"}", f.paths.guestAdcFile.readText())
        // the owner's own files on the phone are not touched either
        assertEquals(geminiKey, f.paths.geminiKeyFile.readText())
    }

    @Test fun applyInANormalModeStillClearsTheCredentialsTheModeDoesNotUse() {
        val f = fixture()   // default mode: API key
        HermesConfig.apply(f.paths, f.settings, f.secrets, apiKey, 8642)
        assertTrue(f.paths.hermesEnv.readText().contains("GEMINI_API_KEY=$geminiKey"))
        assertFalse(f.paths.guestServiceAccount.exists()); assertFalse(f.paths.guestAdcFile.exists())
        assertTrue(f.paths.hermesConfig.readText().endsWith(ProviderConfig.renderGuardYaml()))
    }

    @Test fun applyWritesTheWebSwitch() {
        val f = fixture()
        HermesConfig.apply(f.paths, f.settings, f.secrets, apiKey, 8642, GuestOptions(webKeylessFallback = false))
        assertTrue(f.paths.hermesConfig.readText().endsWith("web:\n  keyless_fallback: false\n"))
    }
}
