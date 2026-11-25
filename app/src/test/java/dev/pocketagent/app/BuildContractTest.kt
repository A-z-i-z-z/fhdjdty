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

    @Test fun theVariantNameIsOneOfTheThree() {
        assertTrue(BuildConfig.APP_VARIANT, BuildConfig.APP_VARIANT in setOf("owner", "twin", "owner-debug"))
    }

    @Test fun thePackageIdFollowsTheVariant() {
        assertEquals(ownerPackage, BuildConfig.OWNER_PACKAGE)
        when (BuildConfig.APP_VARIANT) {
            "twin" -> assertEquals(BuildConfig.OWNER_PACKAGE + ".debug", BuildConfig.APPLICATION_ID)
            "owner", "owner-debug" -> assertEquals(BuildConfig.OWNER_PACKAGE, BuildConfig.APPLICATION_ID)
            else -> throw AssertionError("unknown variant ${BuildConfig.APP_VARIANT}")
        }
    }

    @Test fun theGatesFollowTheVariant() {
        // IS_TWIN is true for the twin only; TEST_HOOKS for both debug variants and never for the owner build.
        assertEquals(BuildConfig.APP_VARIANT == "twin", BuildConfig.IS_TWIN)
        assertEquals(BuildConfig.APP_VARIANT != "owner", BuildConfig.TEST_HOOKS)
        // Never gate on DEBUG: the owner build is not debuggable, the debug variants are.
        assertEquals(BuildConfig.APP_VARIANT != "owner", BuildConfig.DEBUG)
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
        val scheme = if (BuildConfig.APP_VARIANT == "twin") "agiri-test" else "agiri"
        assertEquals(scheme, BuildConfig.DEEP_LINK_SCHEME)
        // Debug builds are always signed with the committed test key.
        if (BuildConfig.TEST_HOOKS) assertEquals("committed", BuildConfig.SIGNING_KIND)
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
            "twin" -> {
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
            "twin" -> (BuildConfig.CI_RUN + 1) * 100
            else -> (10000 + BuildConfig.CI_RUN + 1) * 100
        }
        assertTrue(BuildConfig.VERSION_CODE < next)
        assertTrue(seq <= 99)
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
