package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.config.Json
import org.booktower.config.TemplateRenderer
import org.booktower.filters.GlobalErrorFilter
import org.booktower.handlers.AppHandler
import org.booktower.models.BookDto
import org.booktower.models.LibraryDto
import org.booktower.models.LoginResponse
import org.booktower.models.ReadingProgressDto
import org.booktower.services.AuthService
import org.booktower.services.BookmarkService
import org.booktower.services.PdfMetadataService
import org.booktower.services.UserSettingsService
import org.booktower.services.BookService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.then
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReadingProgressIntegrationTest : IntegrationTestBase() {

    private fun uniqueUser() = "prog_${System.nanoTime()}"

    private fun registerAndGetToken(): String {
        val username = uniqueUser()
        val response = app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
        )
        return Json.mapper.readValue(response.bodyString(), LoginResponse::class.java).token
    }

    private fun createLibrary(token: String): String {
        val response = app(
            Request(Method.POST, "/api/libraries")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"name":"Progress Lib","path":"./data/test-prog-${System.nanoTime()}"}"""),
        )
        return Json.mapper.readValue(response.bodyString(), LibraryDto::class.java).id
    }

    private fun createBook(token: String, libId: String): String {
        val response = app(
            Request(Method.POST, "/api/books")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"Test Book ${System.nanoTime()}","author":null,"description":null,"libraryId":"$libId"}"""),
        )
        return Json.mapper.readValue(response.bodyString(), BookDto::class.java).id
    }

    @Test
    fun `update progress returns 200 with progress data`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response = app(
            Request(Method.PUT, "/api/books/$bookId/progress")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"currentPage":42}"""),
        )

        assertEquals(Status.OK, response.status)
        val progress = Json.mapper.readValue(response.bodyString(), ReadingProgressDto::class.java)
        assertEquals(42, progress.currentPage)
        assertNotNull(progress.lastReadAt)
    }

    @Test
    fun `update progress twice updates the record`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        app(
            Request(Method.PUT, "/api/books/$bookId/progress")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"currentPage":10}"""),
        )

        val response = app(
            Request(Method.PUT, "/api/books/$bookId/progress")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"currentPage":25}"""),
        )

        assertEquals(Status.OK, response.status)
        val progress = Json.mapper.readValue(response.bodyString(), ReadingProgressDto::class.java)
        assertEquals(25, progress.currentPage)
    }

    @Test
    fun `update progress for non-existent book returns 404`() {
        val token = registerAndGetToken()
        val fakeId = "00000000-0000-0000-0000-000000000000"

        val response = app(
            Request(Method.PUT, "/api/books/$fakeId/progress")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"currentPage":5}"""),
        )

        assertEquals(Status.NOT_FOUND, response.status)
    }

    @Test
    fun `update progress with page 0 is valid`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response = app(
            Request(Method.PUT, "/api/books/$bookId/progress")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"currentPage":0}"""),
        )

        assertEquals(Status.OK, response.status)
        val progress = Json.mapper.readValue(response.bodyString(), ReadingProgressDto::class.java)
        assertEquals(0, progress.currentPage)
    }

    @Test
    fun `update progress with negative page returns 400`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response = app(
            Request(Method.PUT, "/api/books/$bookId/progress")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"currentPage":-1}"""),
        )

        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `update progress without body returns 400`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response = app(
            Request(Method.PUT, "/api/books/$bookId/progress")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json"),
        )

        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `update progress without auth returns 401`() {
        val response = app(
            Request(Method.PUT, "/api/books/00000000-0000-0000-0000-000000000000/progress")
                .header("Content-Type", "application/json")
                .body("""{"currentPage":5}"""),
        )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `delete book returns 200`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response = app(
            Request(Method.DELETE, "/api/books/$bookId")
                .header("Cookie", "token=$token"),
        )

        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `delete book removes it from listing`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        app(
            Request(Method.DELETE, "/api/books/$bookId")
                .header("Cookie", "token=$token"),
        )

        val listResponse = app(
            Request(Method.GET, "/api/books?libraryId=$libId")
                .header("Cookie", "token=$token"),
        )
        assertTrue(!listResponse.bodyString().contains(bookId))
    }

    @Test
    fun `delete non-existent book returns 404`() {
        val token = registerAndGetToken()
        val fakeId = "00000000-0000-0000-0000-000000000000"

        val response = app(
            Request(Method.DELETE, "/api/books/$fakeId")
                .header("Cookie", "token=$token"),
        )

        assertEquals(Status.NOT_FOUND, response.status)
    }

    @Test
    fun `delete book without auth returns 401`() {
        val response = app(
            Request(Method.DELETE, "/api/books/00000000-0000-0000-0000-000000000000"),
        )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `recent books reflects progress update`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        app(
            Request(Method.PUT, "/api/books/$bookId/progress")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"currentPage":15}"""),
        )

        val recentResponse = app(
            Request(Method.GET, "/api/recent")
                .header("Cookie", "token=$token"),
        )

        assertEquals(Status.OK, recentResponse.status)
        assertTrue(recentResponse.bodyString().contains(bookId))
    }
}
