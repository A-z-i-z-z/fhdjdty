package dev.pocketagent.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File

/**
 * The facts that cross the Gradle boundary (Wave 0 contract, sections 1.2 and 3.1) are read from BuildConfig, so the
 * three variants cannot drift apart unnoticed. Unit tests run against the debug type: the twin, or the owner-debug
 * variant when the build was made with -PownerDebug=true. The owner (release) variant has no unit test run; its rules
 * are still written down here so a future release test run is checked too.
 */
class BuildContractTest {
    private val ownerPackage = "dev.pocketagent.app"

    /** The local build counter: `-local<seq>` in the version name, 0 when absent. */
    private fun localSeq(): Int =
        Regex("-local(\\d+)").find(BuildConfig.VERSION_NAME)?.groupValues?.get(1)?.toInt() ?: 0

    private fun repoFile(relative: String): File? {
        val candidates = listOf(File("../$relative"), File(relative))
        return candidates.firstOrNull { it.isFile }
    }

    /** Both variants that live in the twin's package: the debug twin and the optimised twin (-PtwinRelease=true, experiment S2). */
    private val twinVariants = setOf("twin", "twin-release")

    @Test fun theVariantNameIsOneOfTheFour() {
        assertTrue(BuildConfig.APP_VARIANT, BuildConfig.APP_VARIANT in setOf("owner", "twin", "owner-debug", "twin-release"))
    }

    @Test fun thePackageIdFollowsTheVariant() {
        assertEquals(ownerPackage, BuildConfig.OWNER_PACKAGE)
        when (BuildConfig.APP_VARIANT) {
            "twin", "twin-release" -> assertEquals(BuildConfig.OWNER_PACKAGE + ".debug", BuildConfig.APPLICATION_ID)
            "owner", "owner-debug" -> assertEquals(BuildConfig.OWNER_PACKAGE, BuildConfig.APPLICATION_ID)
            else -> throw AssertionError("unknown variant ${BuildConfig.APP_VARIANT}")
        }
    }

    @Test fun theGatesFollowTheVariant() {
        // IS_TWIN is true for the two variants in the twin's package (they hold only test data); TEST_HOOKS for the two debug
        // variants only (twin-release is optimised and not debuggable: no debug links, no stretched language).
        val debugVariant = BuildConfig.APP_VARIANT == "twin" || BuildConfig.APP_VARIANT == "owner-debug"
        assertEquals(BuildConfig.APP_VARIANT in twinVariants, BuildConfig.IS_TWIN)
        assertEquals(debugVariant, BuildConfig.TEST_HOOKS)
        // Never gate on DEBUG: the owner build and twin-release are not debuggable, the debug variants are.
        assertEquals(debugVariant, BuildConfig.DEBUG)
    }

    @Test fun buildConfigExposesTheDocumentedFields() {
        val c = BuildConfig::class.java
        fun field(name: String, type: Class<*>) {
            val f = c.getField(name)
            assertEquals("type of $name", type, f.type)
        }
        field("TEST_HOOKS", Boolean::class.javaPrimitiveType!!)
        field("IS_TWIN", Boolean::class.javaPrimitiveType!!)
        field("APP_VARIANT", String::class.java)
        field("OWNER_PACKAGE", String::class.java)
        field("CI_RUN", Int::class.javaPrimitiveType!!)
        field("SIGNING_KIND", String::class.java)
        field("DEEP_LINK_SCHEME", String::class.java)
        field("GIT_COMMIT", String::class.java)
        assertTrue(BuildConfig.SIGNING_KIND, BuildConfig.SIGNING_KIND in setOf("committed", "private"))
        assertTrue(BuildConfig.CI_RUN >= 0)
        val scheme = if (BuildConfig.APP_VARIANT in twinVariants) "agiri-test" else "agiri"
        assertEquals(scheme, BuildConfig.DEEP_LINK_SCHEME)
        // Debug builds and twin-release are always signed with the committed test key.
        if (BuildConfig.TEST_HOOKS || BuildConfig.APP_VARIANT == "twin-release") assertEquals("committed", BuildConfig.SIGNING_KIND)
    }

    @Test fun classesKeepTheirNamesUnderTheSuffix() {
        // The namespace stays dev.pocketagent.app for every variant: only the installed package id changes.
        assertEquals("dev.pocketagent.app.BuildConfig", BuildConfig::class.java.name)
        val mainActivity = try {
            Class.forName("dev.pocketagent.app.MainActivity", false, BuildConfig::class.java.classLoader)
        } catch (e: LinkageError) {
            null   // the class needs Android's activity classes at link time; the source file check below stands in
        }
        if (mainActivity != null) {
            assertEquals("dev.pocketagent.app.MainActivity", mainActivity.name)
        } else {
            val src = repoFile("app/src/main/java/dev/pocketagent/app/MainActivity.kt")
            Assume.assumeTrue(src != null)
            assertTrue(src!!.readText().contains("class MainActivity"))
        }
    }

    @Test fun versionCodeAndNameFollowTheScheme() {
        val seq = localSeq()
        assertTrue("local seq in 0..99", seq in 0..99)
        val name = BuildConfig.VERSION_NAME
        when (BuildConfig.APP_VARIANT) {
            "twin", "twin-release" -> {
                assertEquals(maxOf(BuildConfig.CI_RUN * 100 + seq, 1), BuildConfig.VERSION_CODE)
                assertTrue(name, Regex("^0\\.2\\.\\d+(-local\\d+)?-test$").matches(name))
            }
            "owner-debug", "owner" -> {
                assertEquals((10000 + BuildConfig.CI_RUN) * 100 + seq, BuildConfig.VERSION_CODE)
                assertTrue(name, Regex("^0\\.2\\.\\d+(-local\\d+)?$").matches(name))
            }
            else -> throw AssertionError("unknown variant ${BuildConfig.APP_VARIANT}")
        }
        // The run number in the name is the CI_RUN field.
        assertEquals(BuildConfig.CI_RUN, Regex("^0\\.2\\.(\\d+)").find(name)!!.groupValues[1].toInt())
    }

    @Test fun aLocalBuildCanNeverPassTheNextCiRun() {
        // 99 local builds fit under one run's number: the next run starts 100 codes higher.
        val seq = localSeq()
        val next = when (BuildConfig.APP_VARIANT) {
            "twin", "twin-release" -> (BuildConfig.CI_RUN + 1) * 100
            else -> (10000 + BuildConfig.CI_RUN + 1) * 100
        }
        assertTrue(BuildConfig.VERSION_CODE < next)
        assertTrue(seq <= 99)
    }

    private fun gradleText(): String {
        val f = repoFile("app/build.gradle.kts")
        Assume.assumeTrue("app/build.gradle.kts not reachable from the test's working directory", f != null)
        return f!!.readText().replace("\r\n", "\n")
    }

    /** The text of one buildTypes block ("debug" or "release"), from its opening line to the matching closing brace. */
    private fun buildTypeBlock(gradle: String, name: String): String {
        val start = Regex("(?m)^        $name \\{\\n").find(gradle)
        assertNotNull("buildTypes.$name not found", start)
        val end = gradle.indexOf("\n        }\n", start!!.range.last)
        assertTrue("end of buildTypes.$name not found", end > 0)
        return gradle.substring(start.range.first, end)
    }

    @Test fun twinReleaseNeverGetsTheOwnersPackageOrKey() {
        val gradle = gradleText()
        // the property excludes -PownerDebug, and fails the build when both are given
        assertTrue(gradle.contains("val twinRelease = prop(\"twinRelease\")"))
        assertTrue(Regex("if\\s*\\(\\s*ownerDebug\\s*&&\\s*twinRelease\\s*\\)\\s*\\{\\s*throw GradleException").containsMatchIn(gradle))
        // the default identity stays the owner's package; the suffix is added only inside the twinRelease condition of the release type
        assertTrue(gradle.contains("applicationId = ownerPackage"))
        val release = buildTypeBlock(gradle, "release")
        assertTrue(Regex("if\\s*\\(\\s*twinRelease\\s*\\)\\s*\\{\\s*applicationIdSuffix = \"\\.debug\"").containsMatchIn(release))
        val withoutTwinBranch = release.replace(Regex("if\\s*\\(\\s*twinRelease\\s*\\)\\s*\\{[^}]*\\}"), "")
        assertFalse("the suffix must not be unconditional in the release type", withoutTwinBranch.contains("applicationIdSuffix"))
        // identity fields: twin scheme, optimised and not debuggable, no test hooks, never the private key
        assertTrue(release.contains("twin-release"))
        assertTrue(release.contains("isDebuggable = false"))
        assertTrue(release.contains("buildConfigField(\"boolean\", \"TEST_HOOKS\", \"false\")"))
        assertTrue(release.contains("buildConfigField(\"boolean\", \"IS_TWIN\", if (twinRelease) \"true\" else \"false\")"))
        assertTrue(release.contains("signingConfig = if (twinRelease || ownerKey == null) signingConfigs.getByName(\"sideload\")"))
        assertTrue(gradle.contains("val ownerKey: OwnerKey? = if (twinRelease) null else resolveOwnerKey()"))
        assertTrue(gradle.contains("val ownerSigningKind = if (twinRelease) \"committed\""))
        // the twin's version scheme applies to it
        assertTrue(gradle.contains("(variant.buildType == \"release\" && twinRelease)"))
        // the debug type still adds the suffix unless -PownerDebug
        assertTrue(Regex("if\\s*\\(\\s*!ownerDebug\\s*\\)\\s*\\{\\s*applicationIdSuffix = \"\\.debug\"").containsMatchIn(buildTypeBlock(gradle, "debug")))
    }

    @Test fun theBuildIdsScriptAgreesWithGradle() {
        val script = repoFile("tools/build-ids.ps1")
        Assume.assumeTrue("tools/build-ids.ps1 not reachable from the test's working directory", script != null)
        val text = script!!.readText()
        fun literal(name: String): String {
            val m = Regex("\\$" + name + "\\s*=\\s*'([^']+)'").find(text)
            assertNotNull("$name is not defined in build-ids.ps1", m)
            return m!!.groupValues[1]
        }
        assertEquals(BuildConfig.OWNER_PACKAGE, literal("PaOwnerPackage"))
        assertEquals(BuildConfig.OWNER_PACKAGE + ".debug", literal("PaTwinPackage"))
        assertEquals(BuildConfig.OWNER_PACKAGE, literal("PaNamespace"))
        assertEquals("dev.pocketagent.app.MainActivity", literal("PaMainActivity"))
        assertTrue(text.contains("function Get-PaComponent"))
        assertTrue(text.contains("function Test-PaOwnerPackage"))
        // The file only defines values and functions: it must not run anything when dot-sourced.
        val code = text.lines().filter { !it.trim().startsWith("#") && it.isNotBlank() }
        assertFalse(code.any { it.trim().startsWith("&") || it.trim().startsWith("adb") })
    }
}
