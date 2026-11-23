package dev.pocketagent.app

import dev.pocketagent.app.diag.Redactor
import dev.pocketagent.app.diag.SectionFormat
import dev.pocketagent.app.runtime.PortAnnouncement
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DumpAndPortTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun secretStraddlingTheCapBoundaryIsRedactedBeforeCapping() {
        val secret = "S3CR3T-key-value-0123456789abcdef0123456789"
        val r = Redactor().also { it.addSecret("api-server-key", secret) }
        // place the secret so that every plausible cut position lands inside it
        for (cutInside in 1 until secret.length step 7) {
            val text = "x".repeat(100 - cutInside) + secret + "y".repeat(200)
            for (keepHead in listOf(true, false)) {
                val out = SectionFormat.redactThenCap(r, text, 100, keepHead)
                assertFalse("fragment leaked (cut=$cutInside head=$keepHead)", out.contains("S3CR3T") || out.contains("0123456789abcdef") || out.contains("key-value"))
            }
        }
    }

    @Test fun headSectionsKeepTheirFirstLinesTailSectionsKeepTheirLast() {
        val text = (1..200).joinToString("\n") { "line$it" }
        val head = SectionFormat.redactThenCap(Redactor(), text, 50, keepHead = true)
        val tail = SectionFormat.redactThenCap(Redactor(), text, 50, keepHead = false)
        assertTrue(head.startsWith("line1\n")); assertFalse(head.contains("line200"))
        assertTrue(tail.endsWith("line200")); assertFalse(tail.contains("line1\n"))
    }

    @Test fun portAnnouncementNeedsTheExactPortAndOnlyCountsNewBytes() {
        assertTrue(PortAnnouncement.found("[Api_Server] API server listening on http://127.0.0.1:8642 (model: hermes-agent)", "127.0.0.1", 8642))
        assertFalse(PortAnnouncement.found("API server listening on http://127.0.0.1:86421 (x)", "127.0.0.1", 8642))
        assertTrue(PortAnnouncement.found("API server listening on http://127.0.0.1:8642", "127.0.0.1", 8642)) // end of text
        val log = tmp.newFile("runtime.log")
        log.writeText("=== old run\nAPI server listening on http://127.0.0.1:8642 (model: hermes-agent)\n")
        val offset = log.length()
        assertFalse("an older run's line must not satisfy a new launch", PortAnnouncement.foundInFileSince(log, offset, "127.0.0.1", 8642))
        log.appendText("=== new run\nnoise\n")
        assertFalse(PortAnnouncement.foundInFileSince(log, offset, "127.0.0.1", 8642))
        log.appendText("API server listening on http://127.0.0.1:8642 (model: hermes-agent)\n")
        assertTrue(PortAnnouncement.foundInFileSince(log, offset, "127.0.0.1", 8642))
    }
}
