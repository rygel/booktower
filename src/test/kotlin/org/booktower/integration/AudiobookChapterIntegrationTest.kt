package org.booktower.integration

import org.booktower.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AudiobookChapterIntegrationTest : IntegrationTestBase() {
    private fun minimalMp3(): ByteArray {
        // Minimal valid MP3 frame header (sync word + MPEG1 Layer3 128kbps 44100Hz stereo)
        // Followed by enough zeros to satisfy size checks
        val header = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00.toByte())
        return header + ByteArray(416) // one minimal MP3 frame
    }

    private fun uploadChapter(
        token: String,
        bookId: String,
        trackIndex: Int,
        title: String? = null,
    ): org.http4k.core.Response {
        val req =
            Request(Method.POST, "/api/books/$bookId/chapters")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/octet-stream")
                .header("X-Filename", "chapter-$trackIndex.mp3")
                .header("X-Track-Index", trackIndex.toString())
                .body(minimalMp3().inputStream(), minimalMp3().size.toLong())
        return if (title != null) app(req.header("X-Chapter-Title", title)) else app(req)
    }

    @Test
    fun `list chapters returns empty array for new book`() {
        val token = registerAndGetToken("ch")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response =
            app(
                Request(Method.GET, "/api/books/$bookId/chapters")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, response.status)
        val tree = Json.mapper.readTree(response.bodyString())
        assertTrue(tree.isArray)
        assertEquals(0, tree.size())
    }

    @Test
    fun `upload chapter returns 201 with track info`() {
        val token = registerAndGetToken("ch")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response = uploadChapter(token, bookId, 0, "Introduction")
        assertEquals(Status.CREATED, response.status)
        val tree = Json.mapper.readTree(response.bodyString())
        assertEquals(0, tree.get("trackIndex").asInt())
        assertTrue(tree.get("fileSize").asLong() > 0)
    }

    @Test
    fun `duplicate track index returns 409 conflict`() {
        val token = registerAndGetToken("ch")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        uploadChapter(token, bookId, 0, "Original")

        val response = uploadChapter(token, bookId, 0, "Duplicate")
        assertEquals(Status.CONFLICT, response.status)
    }

    @Test
    fun `uploaded chapter appears in chapter list`() {
        val token = registerAndGetToken("ch")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        uploadChapter(token, bookId, 0, "Chapter One")

        val response =
            app(
                Request(Method.GET, "/api/books/$bookId/chapters")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, response.status)
        val arr = Json.mapper.readTree(response.bodyString())
        assertTrue(arr.isArray)
        assertEquals(1, arr.size())
        assertEquals(0, arr.get(0).get("trackIndex").asInt())
        assertEquals("Chapter One", arr.get(0).get("title").asText())
    }

    @Test
    fun `upload multiple chapters and list them all`() {
        val token = registerAndGetToken("ch")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        uploadChapter(token, bookId, 0, "Chapter One")
        uploadChapter(token, bookId, 1, "Chapter Two")
        uploadChapter(token, bookId, 2, "Chapter Three")

        val response =
            app(
                Request(Method.GET, "/api/books/$bookId/chapters")
                    .header("Cookie", "token=$token"),
            )
        val arr = Json.mapper.readTree(response.bodyString())
        assertEquals(3, arr.size())
    }

    @Test
    fun `stream chapter returns 200 with audio content type`() {
        val token = registerAndGetToken("ch")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        uploadChapter(token, bookId, 0)

        val response =
            app(
                Request(Method.GET, "/api/books/$bookId/chapters/0")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, response.status)
        val ct = response.header("Content-Type") ?: ""
        assertTrue(ct.contains("audio/") || ct.contains("octet-stream"), "Expected audio content type, got: $ct")
        assertTrue(response.header("Accept-Ranges") == "bytes")
    }

    @Test
    fun `stream chapter with Range header returns 206 partial content`() {
        val token = registerAndGetToken("ch")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        uploadChapter(token, bookId, 0)

        val response =
            app(
                Request(Method.GET, "/api/books/$bookId/chapters/0")
                    .header("Cookie", "token=$token")
                    .header("Range", "bytes=0-99"),
            )
        assertEquals(Status.PARTIAL_CONTENT, response.status)
        assertNotNull(response.header("Content-Range"))
    }

    @Test
    fun `stream nonexistent chapter returns 404`() {
        val token = registerAndGetToken("ch")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response =
            app(
                Request(Method.GET, "/api/books/$bookId/chapters/99")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.NOT_FOUND, response.status)
    }

    @Test
    fun `delete chapter removes it from list`() {
        val token = registerAndGetToken("ch")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        uploadChapter(token, bookId, 0, "To Delete")

        val deleteResp =
            app(
                Request(Method.DELETE, "/api/books/$bookId/chapters/0")
                    .header("Cookie", "token=$token"),
            )
        assertTrue(deleteResp.status.code in listOf(200, 204))

        val listResp =
            app(
                Request(Method.GET, "/api/books/$bookId/chapters")
                    .header("Cookie", "token=$token"),
            )
        val arr = Json.mapper.readTree(listResp.bodyString())
        assertEquals(0, arr.size())
    }

    @Test
    fun `delete nonexistent chapter returns 404`() {
        val token = registerAndGetToken("ch")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response =
            app(
                Request(Method.DELETE, "/api/books/$bookId/chapters/99")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.NOT_FOUND, response.status)
    }

    @Test
    fun `update chapter title via PUT`() {
        val token = registerAndGetToken("ch")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        uploadChapter(token, bookId, 0, "Old Title")

        val response =
            app(
                Request(Method.PUT, "/api/books/$bookId/chapters/0")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"New Title"}"""),
            )
        assertEquals(Status.OK, response.status)

        val listResp =
            app(
                Request(Method.GET, "/api/books/$bookId/chapters")
                    .header("Cookie", "token=$token"),
            )
        val arr = Json.mapper.readTree(listResp.bodyString())
        assertEquals("New Title", arr.get(0).get("title").asText())
    }

    @Test
    fun `upload chapter without X-Filename returns 400`() {
        val token = registerAndGetToken("ch")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response =
            app(
                Request(Method.POST, "/api/books/$bookId/chapters")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/octet-stream")
                    .header("X-Track-Index", "0")
                    .body(minimalMp3().inputStream(), minimalMp3().size.toLong()),
            )
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `upload chapter without X-Track-Index returns 400`() {
        val token = registerAndGetToken("ch")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response =
            app(
                Request(Method.POST, "/api/books/$bookId/chapters")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/octet-stream")
                    .header("X-Filename", "chapter.mp3")
                    .body(minimalMp3().inputStream(), minimalMp3().size.toLong()),
            )
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `upload chapter with invalid format returns 400`() {
        val token = registerAndGetToken("ch")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response =
            app(
                Request(Method.POST, "/api/books/$bookId/chapters")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/octet-stream")
                    .header("X-Filename", "chapter.docx")
                    .header("X-Track-Index", "0")
                    .body(ByteArray(100).inputStream(), 100),
            )
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `unauthenticated chapter list returns 401`() {
        val token = registerAndGetToken("ch")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response = app(Request(Method.GET, "/api/books/$bookId/chapters"))
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `user B cannot list user A chapters`() {
        val tokenA = registerAndGetToken("chA")
        val tokenB = registerAndGetToken("chB")
        val libId = createLibrary(tokenA)
        val bookId = createBook(tokenA, libId)
        uploadChapter(tokenA, bookId, 0, "Secret Chapter")

        // User B queries A's book — must see 0 chapters (book not found for B)
        val response =
            app(
                Request(Method.GET, "/api/books/$bookId/chapters")
                    .header("Cookie", "token=$tokenB"),
            )
        // Either 404 (book not found) or 200 with empty array are acceptable
        assertTrue(
            response.status == Status.NOT_FOUND || response.status == Status.OK,
            "Expected 404 or 200, got ${response.status}",
        )
        if (response.status == Status.OK) {
            val arr = Json.mapper.readTree(response.bodyString())
            assertEquals(0, arr.size(), "User B must not see User A's chapters")
        }
    }

    @Test
    fun `user B cannot upload chapter to user A book`() {
        val tokenA = registerAndGetToken("chA2")
        val tokenB = registerAndGetToken("chB2")
        val libId = createLibrary(tokenA)
        val bookId = createBook(tokenA, libId)

        val mp3 = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00.toByte()) + ByteArray(416)
        val response =
            app(
                Request(Method.POST, "/api/books/$bookId/chapters")
                    .header("Cookie", "token=$tokenB")
                    .header("Content-Type", "application/octet-stream")
                    .header("X-Filename", "attack.mp3")
                    .header("X-Track-Index", "0")
                    .body(mp3.inputStream(), mp3.size.toLong()),
            )
        // Book belongs to A; B's request should get 404 (book not found for B)
        assertEquals(Status.NOT_FOUND, response.status, "User B must not upload chapters to User A's book")

        // Verify A's book has no chapters
        val listResp =
            app(
                Request(Method.GET, "/api/books/$bookId/chapters")
                    .header("Cookie", "token=$tokenA"),
            )
        val arr = Json.mapper.readTree(listResp.bodyString())
        assertEquals(0, arr.size(), "No chapters should exist after cross-user upload attempt")
    }

    @Test
    fun `book file size aggregated after chapter upload`() {
        val token = registerAndGetToken("ch")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        uploadChapter(token, bookId, 0, "Ch 1")
        uploadChapter(token, bookId, 1, "Ch 2")

        val bookResp =
            app(
                Request(Method.GET, "/api/books/$bookId")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, bookResp.status)
        val bookTree = Json.mapper.readTree(bookResp.bodyString())
        assertTrue(bookTree.get("fileSize").asLong() > 0, "fileSize should be > 0 after chapter uploads")
    }

    @Test
    fun `book file size decreases after chapter deletion`() {
        val token = registerAndGetToken("ch")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        uploadChapter(token, bookId, 0, "Ch 1")
        uploadChapter(token, bookId, 1, "Ch 2")

        val sizeBefore =
            Json.mapper
                .readTree(
                    app(Request(Method.GET, "/api/books/$bookId").header("Cookie", "token=$token")).bodyString(),
                ).get("fileSize")
                .asLong()
        assertTrue(sizeBefore > 0, "fileSize should be > 0 before deletion")

        app(Request(Method.DELETE, "/api/books/$bookId/chapters/0").header("Cookie", "token=$token"))

        val sizeAfter =
            Json.mapper
                .readTree(
                    app(Request(Method.GET, "/api/books/$bookId").header("Cookie", "token=$token")).bodyString(),
                ).get("fileSize")
                .asLong()
        assertTrue(sizeAfter < sizeBefore, "fileSize should decrease after deleting a chapter")
        assertTrue(sizeAfter > 0, "fileSize should still be > 0 with one chapter remaining")
    }

    @Test
    fun `upload after deleting middle chapter uses next available index`() {
        val token = registerAndGetToken("ch")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        uploadChapter(token, bookId, 0, "Ch 0")
        uploadChapter(token, bookId, 1, "Ch 1")
        uploadChapter(token, bookId, 2, "Ch 2")

        // Delete the middle chapter
        app(Request(Method.DELETE, "/api/books/$bookId/chapters/1").header("Cookie", "token=$token"))

        // Chapters remaining: 0, 2 — size = 2, but max index = 2
        // Uploading with index 2 would conflict; must use index 3
        val response = uploadChapter(token, bookId, 3, "Ch 3 (new)")
        assertEquals(Status.CREATED, response.status, "Should succeed using index 3 not the size-based index 2")
    }

    @Test
    fun `chapter list API does not expose internal file path`() {
        val token = registerAndGetToken("ch")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        uploadChapter(token, bookId, 0, "Ch 1")

        val listResp =
            app(
                Request(Method.GET, "/api/books/$bookId/chapters")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, listResp.status)
        val arr = Json.mapper.readTree(listResp.bodyString())
        assertTrue(arr.isArray && arr.size() == 1, "Should return one chapter")
        // filePath must not be present in the JSON
        assertFalse(
            arr.get(0).has("filePath"),
            "Internal filePath must not be exposed in the chapter list API response",
        )
    }
}
