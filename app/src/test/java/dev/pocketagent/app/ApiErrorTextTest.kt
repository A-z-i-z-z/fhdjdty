package dev.pocketagent.app

import dev.pocketagent.app.api.ApiErrorText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shapes of an error answer and what the chat shows for each (E3 of the Wave 0 phone proof). */
class ApiErrorTextTest {
    @Test fun openAiShapeGivesMessageTypeAndCode() {
        val p = ApiErrorText.parse("""{"error":{"message":"practice failure: the FAIL word was used","type":"invalid_request_error","code":"practice_fail"}}""")
        assertEquals("practice failure: the FAIL word was used", p.message)
        assertEquals("invalid_request_error", p.type)
        assertEquals("practice_fail", p.code)
        assertEquals("practice failure: the FAIL word was used", p.best)
    }

    @Test fun aNumericCodeIsKeptAsText() {
        val p = ApiErrorText.parse("""{"error":{"message":"slow down","code":429}}""")
        assertEquals("429", p.code)
    }

    @Test fun errorAsPlainString() {
        assertEquals("bad key", ApiErrorText.parse("""{"error":"bad key"}""").best)
    }

    @Test fun detailAsStringListAndObject() {
        assertEquals("Not found", ApiErrorText.parse("""{"detail":"Not found"}""").best)
        assertEquals("field required", ApiErrorText.parse("""{"detail":[{"loc":["body","x"],"msg":"field required","type":"missing"}]}""").best)
        assertEquals("nope", ApiErrorText.parse("""{"detail":{"message":"nope"}}""").best)
    }

    @Test fun topLevelMessage() {
        assertEquals("overloaded", ApiErrorText.parse("""{"message":"overloaded"}""").best)
    }

    @Test fun codeIsTheFallbackWhenThereIsNoMessage() {
        val p = ApiErrorText.parse("""{"error":{"type":"agent_error","code":"stale_generation"}}""")
        assertNull(p.message)
        assertEquals("stale_generation", p.best)
    }

    @Test fun plainTextKeepsItsFirstLine() {
        assertEquals("upstream connect error", ApiErrorText.parse("upstream connect error\nsecond line\n").best)
    }

    @Test fun htmlGivesItsTitleOrNothing() {
        assertEquals("502 Bad Gateway", ApiErrorText.parse("<html><head><title>502 Bad Gateway</title></head><body><h1>502</h1></body></html>").best)
        assertNull(ApiErrorText.parse("<!DOCTYPE html><html><body><p>oops</p></body></html>").best)
    }

    @Test fun emptyAndBlankAndNullGiveNothing() {
        assertNull(ApiErrorText.parse("").best)
        assertNull(ApiErrorText.parse("   \n ").best)
        assertNull(ApiErrorText.parse(null).best)
    }

    @Test fun jsonWithoutAnyKnownFieldGivesNothingNotTheRawText() {
        val p = ApiErrorText.parse("""{"id":"chatcmpl-1","object":"chat.completion.chunk","choices":[{"finish_reason":"error"}]}""")
        assertNull("the raw chunk must never become the line", p.best)
    }

    @Test fun brokenJsonFallsBackToTextButCutsIt() {
        val p = ApiErrorText.parse("{\"error\": {\"message\": \"oh")
        assertTrue(p.best!!.startsWith("{"))   // plain text path: the first line, never an exception
    }

    @Test fun aNestedPythonStyleErrorIsReducedToTheInnerMessage() {
        val p = ApiErrorText.parse("""{"error":{"message":"Error code: 400 - {'error': {'message': 'API key not valid', 'type': 'x'}}"}}""")
        assertEquals("API key not valid", p.message)
        val q = ApiErrorText.parse("""{"error":{"message":"Error code: 400 - {'error': {'message': \"it's bad\", 'code': 400}}"}}""")
        assertEquals("it's bad", q.message)
    }

    @Test fun aNestedJsonMessageInPlainText() {
        assertEquals("quota exceeded", ApiErrorText.parse("""HTTP 429: {"error": {"message": "quota exceeded"}}""").best)
    }

    @Test fun longMessagesAreCutAndHaveNoLineBreaks() {
        val long = "x".repeat(1000)
        val m = ApiErrorText.parse("""{"error":{"message":"$long"}}""").message!!
        assertEquals(ApiErrorText.MAX_CHARS, m.length)
        assertTrue(m.endsWith("…"))
        val multi = ApiErrorText.parse("""{"error":{"message":"a\nb\t\tc"}}""").message!!
        assertEquals("a b c", multi)
        assertFalse(multi.contains('\n'))
    }

    @Test fun anArrayBodyIsRead() {
        assertEquals("first", ApiErrorText.parse("""[{"msg":"first"},{"msg":"second"}]""").best)
    }
}
