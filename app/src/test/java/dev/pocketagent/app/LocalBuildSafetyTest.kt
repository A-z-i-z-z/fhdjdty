package dev.pocketagent.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File

/**
 * The local build script and the CI signing script can touch the owner's phone or secrets, so the things they must
 * never do are pinned here as text checks (Wave 0 contract, 1.3 and 5.1). The scripts themselves cannot run in a JVM test.
 */
class LocalBuildSafetyTest {
    private fun repoFile(relative: String): File? =
        listOf(File("../$relative"), File(relative)).firstOrNull { it.isFile }

    private fun script(relative: String): String {
        val f = repoFile(relative)
        Assume.assumeTrue("$relative not reachable from the test's working directory", f != null)
        return f!!.readText()
    }

    @Test fun theLocalBuildScriptCannotDestroyTheOwnersApp() {
        val text = script("tools/local-build.ps1")
        val lower = text.lowercase()
        for (token in listOf("uninstall", "pm clear", "settings put", "--bypass-low-target-sdk-block", "bypass-low-target-sdk", "verifier", "package_verifier")) {
            assertFalse("tools/local-build.ps1 must not contain '$token'", lower.contains(token))
        }
        // -ExecutionPolicy Bypass is how the script is run on this PC (script execution is off): it is the one allowed use of the word.
        for (line in text.lines()) {
            if (line.contains("Bypass", ignoreCase = true)) {
                assertTrue("only -ExecutionPolicy Bypass may use the word: $line", line.contains("-ExecutionPolicy Bypass") || line.contains("'-ExecutionPolicy', 'Bypass'"))
            }
        }
        // the build runs on Windows PowerShell 5.1 and calls the other tools through it, never through pwsh (not installed on this PC)
        assertFalse("pwsh is CI only", lower.contains("pwsh"))
    }

    @Test fun theNamedSafetyFunctionsExist() {
        val text = script("tools/local-build.ps1")
        for (name in listOf(
            "Get-ApkPackage", "Get-ApkCertSha256", "Get-InstalledCertSha256", "Get-InstalledVersionCode", "Resolve-LocalSeq",
            "Assert-OwnerInstallAllowed", "Assert-TwinReleaseInstallAllowed", "Invoke-CompileSpeed", "Get-CompileState",
        )) {
            assertTrue("function $name is missing", Regex("(?m)^function\\s+" + Regex.escape(name) + "\\b").containsMatchIn(text))
        }
        // package names come from build-ids.ps1 only
        assertTrue(text.contains("build-ids.ps1"))
        assertFalse("a package name is spelled out in the script", Regex("'dev\\.pocketagent\\.app(\\.debug)?'").containsMatchIn(text.lines().filter { !it.trimStart().startsWith("#") }.joinToString("\n")))
    }

    @Test fun theDowngradeFlagIsOnlyForTheTwin() {
        val text = script("tools/local-build.ps1")
        val flag = Regex("""(^|[\s'",(@])-d($|[\s'",)])""")
        var seen = 0
        for (line in text.lines()) {
            if (line.trimStart().startsWith("#")) continue
            if (flag.containsMatchIn(line)) {
                seen++
                assertTrue("-d on a line that does not name the twin package: $line", line.contains("\$PaTwinPackage"))
            }
        }
        assertTrue("the twin downgrade retry is expected to exist", seen >= 1)
    }

    @Test fun everyOwnerInstallGoesThroughTheAssertion() {
        // A checkout on Windows may carry CRLF endings: match on LF text.
        val text = script("tools/local-build.ps1").replace("\r\n", "\n")
        val install = Regex("(?s)function Invoke-Install \\{.*?\\n\\}\\n").find(text)
        assertTrue("Invoke-Install not found", install != null)
        val body = install!!.value
        val assertAt = body.indexOf("Assert-OwnerInstallAllowed")
        val installAt = body.indexOf("'install', '-r'")
        assertTrue("the assertion must be called in Invoke-Install", assertAt >= 0)
        assertTrue("adb install not found in Invoke-Install", installAt >= 0)
        assertTrue("the assertion must come before the first adb install", assertAt < installAt)
        // the optimised owner build also needs the experiment acknowledgement
        assertTrue(body.contains("AcknowledgeS2"))
        // a read-only copy of the owner's data, and the same Linux image, are conditions of the assertion
        assertTrue(text.contains("owner-backup"))
        assertTrue(text.contains("rootfs.stamp"))
        assertTrue(text.contains("AllowReextract"))
    }

    @Test fun theOptimisedTwinInstallsOnlyOverTheTwin() {
        val text = script("tools/local-build.ps1").replace("\r\n", "\n")
        assertTrue("the -TwinRelease switch", text.contains("[switch]\$TwinRelease"))
        assertTrue("the three builds exclude each other", text.contains("-Release, -OwnerDebug and -TwinRelease exclude each other"))
        // the identity is chosen from the switch, and twin-release lives in the twin's package, never the owner's
        assertTrue(text.contains("elseif (\$TwinRelease.IsPresent) { \$Identity = 'twin-release' }"))
        assertTrue(text.contains("if (\$Identity -eq 'owner' -or \$Identity -eq 'owner-debug') { \$TargetPackage = \$PaOwnerPackage }"))
        assertTrue(text.contains("\$WantDebuggable = (\$Identity -eq 'twin' -or \$Identity -eq 'owner-debug')"))
        assertTrue("-PtwinRelease reaches Gradle", text.contains("-PtwinRelease=true"))
        // Invoke-Install: refuse any other package first, then the update-only assertion, both before the adb install
        val body = Regex("(?s)function Invoke-Install \\{.*?\\n\\}\\n").find(text)!!.value
        val refuse = body.indexOf("\$Identity -eq 'twin-release' -and \$pkg -ne \$PaTwinPackage")
        val assertAt = body.indexOf("Assert-TwinReleaseInstallAllowed")
        val installAt = body.indexOf("'install', '-r'")
        assertTrue("package refusal missing", refuse >= 0)
        assertTrue("assertion call missing", assertAt > refuse)
        assertTrue("the assertion must come before the adb install", assertAt < installAt)
        // the twin's downgrade retry is not used for it, and it is compiled with the speed filter like the owner build
        assertTrue(body.contains("-and \$Identity -ne 'twin-release'"))
        assertTrue(body.contains("(\$Identity -eq 'owner' -or \$Identity -eq 'twin-release') -and -not \$NoCompile.IsPresent) { Invoke-CompileSpeed"))
        // the assertion: installed twin only, version code above the installed one
        val assertion = Regex("(?s)function Assert-TwinReleaseInstallAllowed.*?\\n\\}\\n").find(text)!!.value
        assertTrue(assertion.contains("\$badge.Package -ne \$PaTwinPackage"))
        assertTrue(assertion.contains("Get-InstalledPackageInfo \$PaTwinPackage"))
        assertTrue(assertion.contains("\$badge.VersionCode -le \$info.VersionCode"))
        assertFalse("it must never be the owner assertion", assertion.contains("PaOwnerPackage"))
        // the owner build and twin-release share the release folder: a change of variant triggers the clean Kotlin recompile
        assertTrue(text.contains("((\$memo -ne \$Identity)"))
        assertTrue(text.contains("if (\$Identity -eq 'owner' -or \$Identity -eq 'twin-release') {"))
    }

    @Test fun theSigningScriptNeverPrintsASecret() {
        val text = script("tools/ci/sign-owner.sh")
        assertFalse("no shell tracing", Regex("(?m)^\\s*set\\s+-[a-z]*x").containsMatchIn(text))
        val printing = Regex("\\b(echo|printf)\\b")
        val secretValue = Regex("\\$\\{?(KS_PASS|KEY_PASS|KS_B64|LINEAGE_B64)")
        for (line in text.lines()) {
            val code = line.trim()
            if (code.startsWith("#") || !printing.containsMatchIn(code) || !secretValue.containsMatchIn(code)) continue
            // the only allowed use: feeding the base64 text to the decoder
            assertTrue("a secret may only be piped to base64 -d: $line", code.contains("base64 -d"))
        }
        // passwords reach apksigner through the environment, never on the command line
        assertTrue(text.contains("--ks-pass env:KS_PASS"))
        assertTrue(text.contains("--key-pass env:KEY_PASS"))
        assertTrue(text.contains("trap "))
    }

    @Test fun theShellScriptsOfTheBuildHaveUnixLineEndings() {
        for (name in listOf("tools/ci/apk-size.sh", "tools/ci/verify-apk.sh", "tools/ci/sign-owner.sh", "tools/ci/test-apk-size.sh")) {
            val f = repoFile(name)
            Assume.assumeTrue("$name not reachable", f != null)
            val bytes = f!!.readBytes()
            assertEquals("$name has CR bytes", -1, bytes.indexOf(13.toByte()))
            assertTrue("$name starts with a shebang", String(bytes, 0, 2) == "#!")
        }
    }
}
