package com.moyue.reader

import com.moyue.reader.data.importer.BookImporter
import com.moyue.reader.data.transfer.TransferServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Socket
import java.net.URL
import java.nio.file.Files

/**
 * Wi-Fi 传书服务集成测试：JVM 内真实起 HTTP 服务，
 * 覆盖配对码拦截、扩展名白名单、验码后 multipart 上传全链路。
 * HTTP 客户端用原始 Socket：完全可控的请求构造与响应读取。
 */
class TransferServerTest {

    private class Ctx(val server: TransferServer, val received: MutableList<String>)

    private fun withServer(block: (Ctx) -> Unit) {
        val received = mutableListOf<String>()
        val server = TransferServer(Files.createTempDirectory("moyue-t").toFile()) { _, name, _ ->
            received += name
            // 模拟成功导入（真实管线由 BookImporter/解析器测试覆盖）
            BookImporter.ImportResult(name, true, "ok(GBK，12 章)")
        }
        val (base, _) = server.startLoopbackForTest()
        try {
            block(Ctx(server, received))
        } finally {
            server.stop()
        }
    }

    /** 原始 HTTP/1.1 请求，返回 (状态码, 响应体) */
    private fun raw(
        url: String,
        method: String,
        body: ByteArray? = null,
        contentType: String? = null,
        cookie: String? = null
    ): Pair<Int, String> {
        val u = URL(url)
        Socket(u.host, u.port).use { sock ->
            sock.soTimeout = 10_000
            val head = buildString {
                append(method).append(' ').append(u.file.ifEmpty { "/" }).append(" HTTP/1.1\r\n")
                append("Host: ").append(u.host).append(':').append(u.port).append("\r\n")
                append("Connection: close\r\n")
                contentType?.let { append("Content-Type: ").append(it).append("\r\n") }
                cookie?.let { append("Cookie: ").append(it).append("\r\n") }
                body?.let { append("Content-Length: ").append(it.size).append("\r\n") }
                append("\r\n")
            }
            sock.getOutputStream().apply {
                write(head.toByteArray(Charsets.ISO_8859_1))
                body?.let { write(it) }
                flush()
            }
            val respBytes = sock.getInputStream().readBytes()
            val resp = respBytes.toString(Charsets.ISO_8859_1) // 头部 ASCII 安全
            val status = resp.substringAfter(' ').substringBefore(' ').toInt()
            val body = respBytes.toString(Charsets.UTF_8).substringAfter("\r\n\r\n", "")
            return status to body
        }
    }

    /** multipart/form-data 请求体，返回 (body, contentType) */
    private fun multipart(name: String, bytes: ByteArray): Pair<ByteArray, String> {
        val b = "----moyuetest1234"
        val head = (
            "--$b\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"$name\"\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n"
            ).toByteArray()
        val tail = "\r\n--$b--\r\n".toByteArray()
        return head + bytes + tail to "multipart/form-data; boundary=$b"
    }

    private fun pair(base: String, code: String): Int =
        raw("$base/pair", "POST", "code=$code".toByteArray(), "application/x-www-form-urlencoded").first

    @Test
    fun `upload requires pairing`() {
        withServer { ctx ->
            val base = ctx.server.state.value.urls.first()
            val mp = multipart("a.txt", "hello".toByteArray())
            val (code, _) = raw("$base/upload?name=a.txt", "POST", mp.first, mp.second)
            assertEquals(403, code)
            assertTrue(ctx.received.isEmpty())
        }
    }

    @Test
    fun `wrong code rejected then correct code authorizes upload`() {
        withServer { ctx ->
            val base = ctx.server.state.value.urls.first()
            val code = ctx.server.state.value.pairCode
            // 错码 → 403
            assertEquals(403, pair(base, "9999"))
            // 对码 → 200
            assertEquals(200, pair(base, code))
            // 携带 token 上传 → 成功且进入导入回调
            val token = ctx.server.testToken()!!
            val mp = multipart("book.txt", "第一章 试传\n正文内容".toByteArray())
            val (c, body) = raw(
                "$base/upload?name=book.txt", "POST", mp.first, mp.second,
                cookie = "moyue_token=$token"
            )
            assertEquals(200, c)
            assertTrue(body.contains("\"ok\":true"))
            assertEquals(listOf("book.txt"), ctx.received)
        }
    }

    @Test
    fun `unsupported extension rejected`() {
        withServer { ctx ->
            val base = ctx.server.state.value.urls.first()
            pair(base, ctx.server.state.value.pairCode)
            val token = ctx.server.testToken()!!
            val mp = multipart("virus.exe", "MZ".toByteArray())
            val (c, _) = raw(
                "$base/upload?name=virus.exe", "POST", mp.first, mp.second,
                cookie = "moyue_token=$token"
            )
            assertEquals(406, c)
            assertTrue(ctx.received.isEmpty())
        }
    }

    @Test
    fun `home page served bilingual`() {
        withServer { ctx ->
            val base = ctx.server.state.value.urls.first()
            val (c, body) = raw(base, "GET")
            assertEquals(200, c)
            assertTrue(body.contains("MoYue") && body.contains("墨阅"))
        }
    }
}
