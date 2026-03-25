package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import org.runary.config.Json
import org.runary.models.BookDto
import org.runary.models.LibraryDto
import org.runary.models.LoginResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileUploadIntegrationTest : IntegrationTestBase() {
    private fun uniqueUser() = "fu_${System.nanoTime()}"

    private fun registerAndGetToken(): String {
        val username = uniqueUser()
        val response =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","email":"$username@test.com","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
            )
        return Json.mapper.readValue(response.bodyString(), LoginResponse::class.java).token
    }

    private fun createBook(token: String): Pair<String, String> {
        val libResponse =
            app(
                Request(Method.POST, "/api/libraries")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"File Lib","path":"./data/test-fu-${System.nanoTime()}"}"""),
            )
        val libId = Json.mapper.readValue(libResponse.bodyString(), LibraryDto::class.java).id

        val bookResponse =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Upload Test ${System.nanoTime()}","author":null,"description":null,"libraryId":"$libId"}"""),
            )
        val bookId = Json.mapper.readValue(bookResponse.bodyString(), BookDto::class.java).id
        return bookId to libId
    }

    // Minimal valid PDF bytes (header only — enough for upload test, not for real parsing)
    private val fakePdfBytes = "%PDF-1.4 fake content for testing".toByteArray()
    private val fakeEpubBytes = "PK fake epub content".toByteArray()

    @Test
    fun `upload PDF returns 200 with filename and size`() {
        val token = registerAndGetToken()
        val (bookId, _) = createBook(token)

        val response =
            app(
                Request(Method.POST, "/api/books/$bookId/upload")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/octet-stream")
                    .header("X-Filename", "mybook.pdf")
                    .body(java.io.ByteArrayInputStream(fakePdfBytes)),
            )

        assertEquals(Status.OK, response.status)
        val body = response.bodyString()
        assertTrue(body.contains("filename"))
        assertTrue(body.contains(".pdf"))
    }

    @Test
    fun `upload EPUB returns 200`() {
        val token = registerAndGetToken()
        val (bookId, _) = createBook(token)

        val response =
            app(
                Request(Method.POST, "/api/books/$bookId/upload")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/octet-stream")
                    .header("X-Filename", "mybook.epub")
                    .body(java.io.ByteArrayInputStream(fakeEpubBytes)),
            )

        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `upload without X-Filename returns 400`() {
        val token = registerAndGetToken()
        val (bookId, _) = createBook(token)

        val response =
            app(
                Request(Method.POST, "/api/books/$bookId/upload")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/octet-stream")
                    .body(java.io.ByteArrayInputStream(fakePdfBytes)),
            )

        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `upload with unsupported extension returns 400`() {
        val token = registerAndGetToken()
        val (bookId, _) = createBook(token)

        val response =
            app(
                Request(Method.POST, "/api/books/$bookId/upload")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/octet-stream")
                    .header("X-Filename", "mybook.exe")
                    .body("fake exe"),
            )

        assertEquals(Status.BAD_REQUEST, response.status)
        assertTrue(response.bodyString().contains("Unsupported file type"))
    }

    @Test
    fun `upload with empty body returns 400`() {
        val token = registerAndGetToken()
        val (bookId, _) = createBook(token)

        val response =
            app(
                Request(Method.POST, "/api/books/$bookId/upload")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/octet-stream")
                    .header("X-Filename", "mybook.pdf"),
            )

        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `upload to non-existent book returns 404`() {
        val token = registerAndGetToken()
        val fakeId = "00000000-0000-0000-0000-000000000000"

        val response =
            app(
                Request(Method.POST, "/api/books/$fakeId/upload")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/octet-stream")
                    .header("X-Filename", "mybook.pdf")
                    .body(java.io.ByteArrayInputStream(fakePdfBytes)),
            )

        assertEquals(Status.NOT_FOUND, response.status)
    }

    @Test
    fun `upload without auth returns 401`() {
        val response =
            app(
                Request(Method.POST, "/api/books/00000000-0000-0000-0000-000000000000/upload")
                    .header("Content-Type", "application/octet-stream")
                    .header("X-Filename", "mybook.pdf")
                    .body(java.io.ByteArrayInputStream(fakePdfBytes)),
            )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `download file after upload succeeds`() {
        val token = registerAndGetToken()
        val (bookId, _) = createBook(token)

        app(
            Request(Method.POST, "/api/books/$bookId/upload")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/octet-stream")
                .header("X-Filename", "readable.pdf")
                .body(java.io.ByteArrayInputStream(fakePdfBytes)),
        )

        val response =
            app(
                Request(Method.GET, "/api/books/$bookId/file")
                    .header("Cookie", "token=$token"),
            )

        assertEquals(Status.OK, response.status)
        assertTrue(response.header("Content-Type")?.contains("application/pdf") == true)
        assertTrue(response.header("Content-Disposition")?.contains(".pdf") == true)
    }

    @Test
    fun `download file before upload returns 404`() {
        val token = registerAndGetToken()
        val (bookId, _) = createBook(token)

        val response =
            app(
                Request(Method.GET, "/api/books/$bookId/file")
                    .header("Cookie", "token=$token"),
            )

        assertEquals(Status.NOT_FOUND, response.status)
    }

    @Test
    fun `download non-existent book returns 404`() {
        val token = registerAndGetToken()
        val response =
            app(
                Request(Method.GET, "/api/books/00000000-0000-0000-0000-000000000000/file")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.NOT_FOUND, response.status)
    }

    @Test
    fun `download without auth returns 401`() {
        val response =
            app(
                Request(Method.GET, "/api/books/00000000-0000-0000-0000-000000000000/file"),
            )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `user B cannot download user A book`() {
        val tokenA = registerAndGetToken()
        val tokenB = registerAndGetToken()
        val (bookId, _) = createBook(tokenA)

        app(
            Request(Method.POST, "/api/books/$bookId/upload")
                .header("Cookie", "token=$tokenA")
                .header("Content-Type", "application/octet-stream")
                .header("X-Filename", "private.pdf")
                .body(java.io.ByteArrayInputStream(fakePdfBytes)),
        )

        val response =
            app(
                Request(Method.GET, "/api/books/$bookId/file")
                    .header("Cookie", "token=$tokenB"),
            )

        assertEquals(Status.NOT_FOUND, response.status)
    }
}
