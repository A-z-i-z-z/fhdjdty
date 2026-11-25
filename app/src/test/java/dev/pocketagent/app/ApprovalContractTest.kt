package dev.pocketagent.app

import dev.pocketagent.app.api.ApprovalContract
import dev.pocketagent.app.api.ChatEvent
import dev.pocketagent.app.api.ChatStreamDecoder
import dev.pocketagent.app.api.HttpResult
import dev.pocketagent.app.api.SseParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The approval answer contract of the Hermes in the image (E1 of the Wave 0 phone proof); see [ApprovalContract] for the source lines. */
class ApprovalContractTest {
    private fun decode(text: String): List<ChatEvent> {
        val p = SseParser()
        val out = ArrayList<ChatEvent>()
        for (line in text.split("\n")) p.feed(line)?.let { out += ChatStreamDecoder.decode(it) }
        return out
    }

    @Test fun theBodyHasExactlyTheThreeKeysAndTheGenerationIsAnInteger() {
        for (choice in listOf("once", "session", "always", "deny")) {
            val raw = ApprovalContract.body("req-1", 7L, choice)
            val o = JSONObject(raw)
            assertEquals(setOf("request_id", "execution_generation", "choice"), o.keys().asSequence().toSet())
            assertEquals("req-1", o.getString("request_id"))
            assertEquals(choice, o.getString("choice"))
            assertTrue("an integer, not a string or a float: $raw", Regex("\"execution_generation\":7[,}]").containsMatchIn(raw))
        }
    }

    @Test fun theFourChoicesAreTheOnesHermesAccepts() {
        assertEquals(setOf("once", "session", "always", "deny"), ApprovalContract.CHOICES)
    }

    @Test fun theApprovalEventCarriesTheGeneration() {
        val raw = "{\"event\":\"approval.request\",\"run_id\":\"chatcmpl-abc\",\"timestamp\":1.5,\"execution_generation\":3,\"command\":\"rm -rf x\"," +
            "\"description\":\"d\",\"request_id\":\"r1\",\"choices\":[\"once\",\"session\",\"always\",\"deny\"],\"session_id\":\"s\"}"
        val a = decode("event: approval.request\ndata: $raw\n\n").single() as ChatEvent.Approval
        assertEquals(3L, a.generation)
        assertEquals("r1", a.requestId)
        assertEquals(listOf("once", "session", "always", "deny"), a.choices)
    }

    @Test fun anEventWithoutAGenerationDecodesToNull() {
        val a = decode("event: approval.request\ndata: {\"run_id\":\"c\",\"request_id\":\"r\",\"choices\":[\"once\"]}\n\n").single() as ChatEvent.Approval
        assertNull(a.generation)
        val b = decode("event: approval.request\ndata: {\"run_id\":\"c\",\"request_id\":\"r\",\"execution_generation\":null}\n\n").single() as ChatEvent.Approval
        assertNull(b.generation)
    }

    @Test fun theRunProjectionGivesTheGenerationAndTheOpenApprovalIds() {
        val st = ApprovalContract.parseRun(
            "{\"object\":\"hermes.run\",\"run_id\":\"c\",\"status\":\"waiting_for_approval\",\"execution_generation\":12," +
                "\"pending_controls\":[{\"kind\":\"clarify\",\"prompt_id\":\"q1\"},{\"kind\":\"approval\",\"prompt_id\":\"p9\",\"choices\":[\"once\",\"deny\"]}]}",
        )
        assertEquals(12L, st.generation)
        assertEquals(listOf("p9"), st.approvalIds)
    }

    @Test fun aRunProjectionWithoutThoseFieldsOrBrokenJsonGivesNothing() {
        assertNull(ApprovalContract.parseRun("{}").generation)
        assertTrue(ApprovalContract.parseRun("{}").approvalIds.isEmpty())
        assertNull(ApprovalContract.parseRun("not json").generation)
        assertNull(ApprovalContract.parseRun(null).generation)
    }

    @Test fun staleAndGoneAreToldApart() {
        val stale = HttpResult(409, "{\"error\":{\"message\":\"stale_generation\",\"type\":\"invalid_request_error\",\"code\":\"stale_generation\"}}", 1)
        assertTrue(ApprovalContract.isStale(stale))
        assertTrue(ApprovalContract.isGone(stale))
        val notPending = HttpResult(409, "{\"error\":{\"message\":\"approval_not_pending\",\"code\":\"approval_not_pending\"}}", 1)
        assertFalse(ApprovalContract.isStale(notPending))
        assertTrue(ApprovalContract.isGone(notPending))
        val badParams = HttpResult(400, "{\"error\":{\"message\":\"invalid_params\",\"code\":\"invalid_params\"}}", 1)
        assertFalse(ApprovalContract.isGone(badParams))
        assertFalse(ApprovalContract.isGone(HttpResult(-1, "", 0, "timeout")))
        assertTrue("a run that is not known any more is gone", ApprovalContract.isGone(HttpResult(404, "{}", 1)))
    }
}
