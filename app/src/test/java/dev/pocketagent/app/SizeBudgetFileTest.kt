package dev.pocketagent.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File

/** tools/size-budget.txt is read by tools/ci/apk-size.sh in CI; a typo there would fail the build or silently disable the gate. */
class SizeBudgetFileTest {
    private fun budget(): Map<String, Long> {
        val f = listOf(File("../tools/size-budget.txt"), File("tools/size-budget.txt")).firstOrNull { it.isFile }
        Assume.assumeTrue("tools/size-budget.txt not reachable from the test's working directory", f != null)
        val values = LinkedHashMap<String, Long>()
        for (raw in f!!.readLines()) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val parts = line.split('=', limit = 2)
            assertEquals("not key=value: $raw", 2, parts.size)
            val key = parts[0].trim()
            val value = parts[1].trim()
            assertTrue("not a whole number: $raw", value.isNotEmpty() && value.all { it in '0'..'9' })
            assertTrue("duplicate key $key", values.put(key, value.toLong()) == null)
        }
        return values
    }

    @Test fun theBudgetFileParses() {
        val b = budget()
        for (key in listOf("owner_baseline_bytes", "owner_ceiling_bytes", "twin_recorded_bytes")) {
            assertNotNull("missing key $key", b[key])
        }
        assertEquals("unexpected keys: ${b.keys}", 3, b.size)
    }

    @Test fun theCeilingIsUnsetOrNotBelowTheBaseline() {
        val b = budget()
        val baseline = b.getValue("owner_baseline_bytes")
        val ceiling = b.getValue("owner_ceiling_bytes")
        // 0 means "not set yet" (apk-size.sh reports verdict=unset and never fails)
        assertTrue("ceiling $ceiling is below the baseline $baseline", ceiling == 0L || ceiling >= baseline)
    }

    @Test fun theRecordedTwinSizeIsPlausible() {
        // the debug build before Wave 0 was about 27 MB without the Linux image; the twin is recorded, never gated
        val twin = budget().getValue("twin_recorded_bytes")
        assertTrue(twin in 1_000_000L..200_000_000L)
    }
}
