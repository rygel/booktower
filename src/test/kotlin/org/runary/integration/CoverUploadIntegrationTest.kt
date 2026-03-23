package org.runary.integration

import org.runary.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoverUploadIntegrationTest : IntegrationTestBase() {
    private fun setup(): Pair<String, String> {
        val token = registerAndGetToken("cover")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        return token to bookId
    }

    // Minimal valid 1×1 PNG bytes
    private val pngBytes: ByteArray =
        byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4e,
            0x47,
            0x0d,
            0x0a,
            0x1a,
            0x0a,
            0x00,
            0x00,
            0x00,
            0x0d,
            0x49,
            0x48,
            0x44,
            0x52,
            0x00,
            0x00,
            0x00,
            0x01,
            0x00,
            0x00,
            0x00,
            0x01,
            0x08,
            0x02,
            0x00,
            0x00,
            0x00,
            0x90.toByte(),
            0x77,
            0x53,
            0xde.toByte(),
            0x00,
            0x00,
            0x00,
            0x0c,
            0x49,
            0x44,
            0x41,
            0x54,
            0x08,
            0xd7.toByte(),
            0x63,
            0xf8.toByte(),
            0xcf.toByte(),
            0xc0.toByte(),
            0x00,
            0x00,
            0x00,
            0x02,
            0x00,
            0x01,
            0xe2.toByte(),
            0x21,
            0xbc.toByte(),
            0x33,
            0x00,
            0x00,
            0x00,
            0x00,
            0x49,
            0x45,
            0x4e,
            0x44,
            0xae.toByte(),
            0x42,
            0x60,
            0x82.toByte(),
        )

    @Test
    fun `upload png cover returns 200 with coverUrl`() {
        val (token, bookId) = setup()
        val resp =
            app(
                Request(Method.POST, "/api/books/$bookId/cover")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/octet-stream")
                    .header("X-Filename", "cover.png")
                    .body(pngBytes.inputStream()),
            )
        assertEquals(Status.OK, resp.status)
        val body = Json.mapper.readTree(resp.bodyString())
        assertTrue(body.has("coverUrl"))
        assertTrue(body["coverUrl"].asText().startsWith("/covers/"))
        assertTrue(body["coverUrl"].asText().endsWith(".png"))
    }

    @Test
    fun `upload jpg cover is accepted`() {
        val (token, bookId) = setup()
        val resp =
            app(
                Request(Method.POST, "/api/books/$bookId/cover")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/octet-stream")
                    .header("X-Filename", "photo.jpg")
                    .body(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte()).inputStream()),
            )
        assertEquals(Status.OK, resp.status)
    }

    @Test
    fun `unsupported extension is rejected with 400`() {
        val (token, bookId) = setup()
        val resp =
            app(
                Request(Method.POST, "/api/books/$bookId/cover")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/octet-stream")
                    .header("X-Filename", "cover.gif")
                    .body(byteArrayOf(0x01).inputStream()),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
        val body = resp.bodyString()
        assertTrue(body.contains("gif"), "Expected 'gif' in error: $body")
    }

    @Test
    fun `missing X-Filename header returns 400`() {
        val (token, bookId) = setup()
        val resp =
            app(
                Request(Method.POST, "/api/books/$bookId/cover")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/octet-stream")
                    .body(pngBytes.inputStream()),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `empty body returns 400`() {
        val (token, bookId) = setup()
        val resp =
            app(
                Request(Method.POST, "/api/books/$bookId/cover")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/octet-stream")
                    .header("X-Filename", "cover.png")
                    .body(byteArrayOf().inputStream()),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `unauthenticated request returns 401`() {
        val (_, bookId) = setup()
        val resp =
            app(
                Request(Method.POST, "/api/books/$bookId/cover")
                    .header("Content-Type", "application/octet-stream")
                    .header("X-Filename", "cover.png")
                    .body(pngBytes.inputStream()),
            )
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `other user cannot upload cover to foreign book`() {
        val (_, bookId) = setup()
        val otherToken = registerAndGetToken("coverOther")
        val resp =
            app(
                Request(Method.POST, "/api/books/$bookId/cover")
                    .header("Cookie", "token=$otherToken")
                    .header("Content-Type", "application/octet-stream")
                    .header("X-Filename", "cover.png")
                    .body(pngBytes.inputStream()),
            )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `cover url is reflected in book details after upload`() {
        val (token, bookId) = setup()
        app(
            Request(Method.POST, "/api/books/$bookId/cover")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/octet-stream")
                .header("X-Filename", "cover.png")
                .body(pngBytes.inputStream()),
        )
        val bookResp =
            app(
                Request(Method.GET, "/api/books/$bookId")
                    .header("Cookie", "token=$token"),
            )
        val body = Json.mapper.readTree(bookResp.bodyString())
        assertTrue(
            body.has("coverUrl") && !body["coverUrl"].isNull,
            "Book should have coverUrl after upload",
        )
    }
}
