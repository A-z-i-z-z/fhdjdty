package dev.pocketagent.app

import dev.pocketagent.app.runtime.SaParse
import dev.pocketagent.app.runtime.ServiceAccountParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceAccountTest {
    private val body = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC7" + "A".repeat(200)
    private val pemEscaped = "-----BEGIN PRIVATE KEY-----\\n" + body.chunked(64).joinToString("\\n") + "\\n-----END PRIVATE KEY-----\\n"

    private fun json(key: String = pemEscaped, type: String = "service_account", extra: String = "") =
        """{"type":"$type","project_id":"my-proj","private_key_id":"abc123","private_key":"$key","client_email":"agent@my-proj.iam.gserviceaccount.com","client_id":"42"$extra}"""

    @Test fun acceptsValidFileVerbatim() {
        val raw = json()
        val r = ServiceAccountParser.parse(raw) as SaParse.Ok
        assertFalse(r.repaired)
        assertEquals(raw, r.jsonText)
        assertEquals("my-proj", r.projectId)
        assertEquals("a***t@my-proj.iam.gserviceaccount.com", r.maskedEmail)
    }

    @Test fun toleratesWhitespaceAroundPaste() {
        val r = ServiceAccountParser.parse("\n\n   " + json() + "  \n \n") as SaParse.Ok
        assertEquals("my-proj", r.projectId)
    }

    @Test fun repairsRealNewlinesInsidePrivateKey() {
        // Newlines pasted as real line breaks (and a terminal wrap in the email) instead of \n escapes.
        val broken = json(key = pemEscaped.replace("\\n", "\n"))
            .replace("agent@my-proj", "agent@my-\nproj")
        val r = ServiceAccountParser.parse(broken) as SaParse.Ok
        assertTrue(r.repaired)
        assertEquals("agent@my-proj.iam.gserviceaccount.com", r.clientEmail)
        assertTrue(r.privateKey.startsWith("-----BEGIN PRIVATE KEY-----\n"))
        assertTrue(r.privateKey.endsWith("-----END PRIVATE KEY-----\n"))
        assertTrue(r.privateKey.lines().all { it.length <= 64 })
        // repaired text is itself valid and stable
        val again = ServiceAccountParser.parse(r.jsonText) as SaParse.Ok
        assertFalse(again.repaired)
    }

    @Test fun namesMissingFields() {
        val r = ServiceAccountParser.parse("""{"type":"service_account","project_id":"p"}""") as SaParse.Fail
        assertEquals(SaParse.Code.MISSING_FIELDS, r.code)
        assertTrue(r.detail.contains("private_key"))
        assertTrue(r.detail.contains("client_email"))
        assertFalse(r.detail.contains("project_id"))
    }

    @Test fun rejectsWrongType() {
        val r = ServiceAccountParser.parse(json(type = "authorized_user")) as SaParse.Fail
        assertEquals(SaParse.Code.WRONG_TYPE, r.code)
    }

    @Test fun rejectsGarbageWithoutEchoingIt() {
        val r = ServiceAccountParser.parse("this is not json at all") as SaParse.Fail
        assertEquals(SaParse.Code.NOT_OBJECT, r.code)
        val r2 = ServiceAccountParser.parse("{\"type\": \"service_account\", oops secret-text}") as SaParse.Fail
        assertEquals(SaParse.Code.NOT_JSON, r2.code)
        assertFalse(r2.detail.contains("secret-text"))
    }

    @Test fun rejectsEmptyAndBadKey() {
        assertEquals(SaParse.Code.EMPTY, (ServiceAccountParser.parse("  \n") as SaParse.Fail).code)
        val r = ServiceAccountParser.parse(json(key = "not a pem")) as SaParse.Fail
        assertEquals(SaParse.Code.BAD_PRIVATE_KEY, r.code)
    }

    @Test fun masksEmail() {
        assertEquals("a***z@x.com", ServiceAccountParser.maskEmail("abcdz@x.com"))
        assertEquals("**@x.com", ServiceAccountParser.maskEmail("ab@x.com"))
    }
}
