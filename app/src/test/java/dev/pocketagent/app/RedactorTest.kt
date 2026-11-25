package dev.pocketagent.app

import dev.pocketagent.app.diag.Redactor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactorTest {
    private val pem = "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC7\nabcdEFGH1234\n-----END PRIVATE KEY-----\n"

    @Test fun redactsRegisteredLiteralSecrets() {
        val r = Redactor()
        r.addSecret("api-server-key", "0123456789abcdef0123456789abcdef")
        val out = r.redact("curl -H 'X: 0123456789abcdef0123456789abcdef' ok")
        assertFalse(out.contains("0123456789abcdef"))
        assertTrue(out.contains("[REDACTED api-server-key len=32]"))
    }

    @Test fun redactsServiceAccountJsonKeepingShape() {
        val json = """{"type":"service_account","project_id":"my-proj","private_key_id":"deadbeef12345678","private_key":"${pem.replace("\n", "\\n")}","client_email":"agent@my-proj.iam.gserviceaccount.com","client_id":"1234567890"}"""
        val out = Redactor().redact(json)
        assertFalse(out.contains("MIIEvQ"))
        assertFalse(out.contains("deadbeef12345678"))
        assertFalse(out.contains("agent@my-proj"))
        assertFalse(out.contains("1234567890"))
        assertTrue(out.contains("\"project_id\":\"my-proj\""))
        assertTrue(out.contains("\"type\":\"service_account\""))
        assertTrue(out.contains("[REDACTED"))
    }

    @Test fun redactsRawPemBlock() {
        val out = Redactor().redact("before\n$pem after")
        assertFalse(out.contains("MIIEvQ"))
        assertTrue(out.contains("before"))
        assertTrue(out.contains("after"))
    }

    @Test fun redactsBearerAndGoogleTokens() {
        val out = Redactor().redact("Authorization: Bearer abc.DEF-123_xyz~tok and ya29.a0AfH6SMBxxxxxxxxxxxxxxxx")
        assertFalse(out.contains("abc.DEF-123_xyz"))
        assertFalse(out.contains("a0AfH6SMB"))
        assertTrue(out.contains("Authorization"))
    }

    @Test fun redactsEnvStyleKey() {
        val out = Redactor().redact("API_SERVER_ENABLED=true\nAPI_SERVER_KEY=supersecretvalue99\n")
        assertFalse(out.contains("supersecretvalue99"))
        assertTrue(out.contains("API_SERVER_ENABLED=true"))
    }

    @Test fun leavesOrdinaryTextAlone() {
        val t = "proot exited with code 137 after 12.5s; project my-proj region global"
        assertTrue(Redactor().redact(t) == t)
    }

    @Test fun redactsJsonEscapedLiteral() {
        val r = Redactor()
        r.addSecret("sa-private-key", pem)
        val out = r.redact("{\"k\":\"" + pem.replace("\n", "\\n") + "\"}")
        assertFalse(out.contains("MIIEvQ"))
    }

    @Test fun redactsApiKeysAndOauthSecrets() {
        val out = Redactor().redact("x-goog-api-key: AQ.Ab8RN6IexampleExampleExample12345 GEMINI_API_KEY=AIzaSyExampleExampleExampleExample123456 " +
            "{\"client_secret\":\"GOCSPX-abcdefghij12345\",\"refresh_token\":\"1//0gabcdefghijklmnopqrstuvwxyz\"}")
        assertFalse(out.contains("Ab8RN6I")); assertFalse(out.contains("AIzaSy")); assertFalse(out.contains("GOCSPX")); assertFalse(out.contains("0gabcdef"))
    }

    @Test fun redactsTheNewProviderKeysByShapeEvenWhenNotRegistered() {
        val ant = "sk-ant-api03-AbCdEfGhIjKlMnOpQrStUvWxYz0123456789"
        val or = "sk-or-v1-0123456789abcdef0123456789abcdef"
        val oa = "sk-proj-AbCdEfGhIjKlMnOpQrStUvWxYz0123456789abcd"
        val oa2 = "sk-AbCdEfGhIjKlMnOpQrStUvWxYz0123456789abcd"
        val out = Redactor().redact("a $ant b $or c $oa d $oa2 e")
        for (k in listOf(ant, or, oa, oa2)) assertFalse(k, out.contains(k.substring(8)))
        assertTrue(out.contains("[REDACTED anthropic-api-key") && out.contains("[REDACTED openrouter-api-key") && out.contains("[REDACTED openai-api-key"))
    }

    @Test fun redactsTheProviderKeysInEnvAndHeaders() {
        val r = Redactor()
        r.addSecret("custom-api-key", "my-custom-server-key-9876")
        val env = listOf("OPENROUTER_API_KEY=notaprefixedkey12345", "ANTHROPIC_API_KEY=whatever-value-123", "AGIRI_CUSTOM_API_KEY=my-custom-server-key-9876", "").joinToString("\n")
        val out = r.redact(env + "x-api-key: some-header-value-1234")
        for (v in listOf("notaprefixedkey12345", "whatever-value-123", "my-custom-server-key-9876", "some-header-value-1234")) assertFalse(v, out.contains(v))
        assertTrue(out.contains("OPENROUTER_API_KEY="))
    }

    @Test fun masksShortEnvSecretsWhateverTheirLength() {
        val env = "API_SERVER_ENABLED=true\n# AGIRI_CUSTOM_API_KEY is not set\nAGIRI_CUSTOM_API_KEY=ab12x\nOPENAI_BASE_URL=http://192.168.1.5:8080/v1\nSOME_TOKEN=\nDB_PASSWORD=pw\r\n"
        val out = Redactor().redact(Redactor.maskEnvSecrets(env))
        assertFalse(out.contains("ab12x"))
        assertFalse(out.contains("=pw"))
        assertTrue(out.contains("AGIRI_CUSTOM_API_KEY=(5 chars, hidden)"))
        assertTrue(out.contains("API_SERVER_ENABLED=true"))
        assertTrue(out.contains("# AGIRI_CUSTOM_API_KEY is not set"))
        assertTrue(out.contains("OPENAI_BASE_URL=http://192.168.1.5:8080/v1"))
        assertTrue(out.contains("SOME_TOKEN=\n"))
    }

    @Test fun redactsUrlUserInfo() {
        val out = Redactor().redact("base https://admin:s3cretPw@llm.example.com/v1 and http://me@host")
        assertFalse(out.contains("s3cretPw")); assertFalse(out.contains("admin")); assertFalse(out.contains("me@"))
        assertTrue(out.contains("llm.example.com/v1"))
        assertTrue(Redactor().redact("see https://example.com/a@b") == "see https://example.com/a@b")
    }

    @Test fun ordinaryWordsWithSkAreLeftAlone() {
        val t = "risk-assessment-for-the-quarterly-report-0123456789abcdef and task-list-0123456789abcdef0123456789abcdef"
        assertTrue(Redactor().redact(t) == t)
    }
}
