package app.yerel.data

import app.yerel.core.Http
import app.yerel.core.HttpResult
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset

/** Small HttpURLConnection client for the agent tools: follows redirects, caps the body size. */
class AndroidHttp : Http {
    override fun get(url: String, headers: Map<String, String>, maxBytes: Int) = request("GET", url, headers, null, maxBytes)

    override fun postForm(url: String, form: Map<String, String>, headers: Map<String, String>): HttpResult {
        val body = form.entries.joinToString("&") { (k, v) -> "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}" }
        return request("POST", url, headers + ("Content-Type" to "application/x-www-form-urlencoded"), body.toByteArray(), 2_000_000)
    }

    private fun request(method: String, start: String, headers: Map<String, String>, body: ByteArray?, maxBytes: Int): HttpResult {
        var url = URL(start)
        var m = method
        var payload = body
        repeat(6) {
            val c = url.openConnection() as HttpURLConnection
            try {
                c.requestMethod = m
                c.instanceFollowRedirects = false
                c.connectTimeout = 15_000
                c.readTimeout = 20_000
                headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
                if (payload != null) {
                    c.doOutput = true
                    c.outputStream.use { it.write(payload) }
                }
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: return HttpResult(code, "", null, url.toString())
                    url = URL(url, loc)
                    if (code == 303 || ((code == 301 || code == 302) && m == "POST")) {
                        m = "GET"
                        payload = null
                    }
                    return@repeat
                }
                val type = c.contentType
                val stream = if (code >= 400) c.errorStream else c.inputStream
                val bytes = stream?.use { input ->
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(16 * 1024)
                    while (out.size() < maxBytes) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, minOf(n, maxBytes - out.size()))
                    }
                    out.toByteArray()
                } ?: ByteArray(0)
                return HttpResult(code, String(bytes, charsetOf(type)), type, url.toString())
            } finally {
                c.disconnect()
            }
        }
        return HttpResult(310, "", null, url.toString())
    }

    private fun charsetOf(type: String?): Charset {
        val name = type?.split(';')?.map { it.trim() }?.firstOrNull { it.startsWith("charset=", true) }
            ?.substringAfter('=')?.trim('"', ' ')
        return try { if (name != null) Charset.forName(name) else Charsets.UTF_8 } catch (e: Exception) { Charsets.UTF_8 }
    }
}
