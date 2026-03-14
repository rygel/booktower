package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.config.Json
import org.booktower.config.TemplateRenderer
import org.booktower.filters.GlobalErrorFilter
import org.booktower.handlers.AppHandler
import org.booktower.models.LibraryDto
import org.booktower.models.LoginResponse
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
import kotlin.test.assertTrue

class LibraryIntegrationTest : IntegrationTestBase() {

    private fun registerAndGetToken(): String {
        val username = "user_${System.nanoTime()}"
        val response = app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
        )
        return Json.mapper.readValue(response.bodyString(), LoginResponse::class.java).token
    }

    private fun authedRequest(method: Method, uri: String, token: String): Request =
        Request(method, uri).header("Cookie", "token=$token")

    @Test
    fun `list libraries without auth returns 401`() {
        val response = app(Request(Method.GET, "/api/libraries"))
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `list libraries with invalid token returns 401`() {
        val response = app(Request(Method.GET, "/api/libraries").header("Cookie", "token=invalid"))
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `list libraries for new user returns empty array`() {
        val token = registerAndGetToken()
        val response = app(authedRequest(Method.GET, "/api/libraries", token))

        assertEquals(Status.OK, response.status)
        val libraries = Json.mapper.readValue(response.bodyString(), Array<LibraryDto>::class.java)
        assertEquals(0, libraries.size)
    }

    @Test
    fun `create library returns 201 with library data`() {
        val token = registerAndGetToken()
        val response = app(
            authedRequest(Method.POST, "/api/libraries", token)
                .header("Content-Type", "application/json")
                .body("""{"name":"My Library","path":"./data/test-lib-${System.nanoTime()}"}"""),
        )

        assertEquals(Status.CREATED, response.status)
        val library = Json.mapper.readValue(response.bodyString(), LibraryDto::class.java)
        assertEquals("My Library", library.name)
        assertEquals(0, library.bookCount)
        assertTrue(library.id.isNotBlank())
    }

    @Test
    fun `create library without auth returns 401`() {
        val response = app(
            Request(Method.POST, "/api/libraries")
                .header("Content-Type", "application/json")
                .body("""{"name":"Test","path":"./data/x"}"""),
        )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `create library with empty body returns 400`() {
        val token = registerAndGetToken()
        val response = app(
            authedRequest(Method.POST, "/api/libraries", token)
                .header("Content-Type", "application/json")
                .body(""),
        )
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `create library with blank name returns 400`() {
        val token = registerAndGetToken()
        val response = app(
            authedRequest(Method.POST, "/api/libraries", token)
                .header("Content-Type", "application/json")
                .body("""{"name":"","path":"./data/x"}"""),
        )
        assertEquals(Status.BAD_REQUEST, response.status)
        assertTrue(response.bodyString().contains("VALIDATION_ERROR"))
    }

    @Test
    fun `create library with name over 100 chars returns 400`() {
        val token = registerAndGetToken()
        val longName = "a".repeat(101)
        val response = app(
            authedRequest(Method.POST, "/api/libraries", token)
                .header("Content-Type", "application/json")
                .body("""{"name":"$longName","path":"./data/x"}"""),
        )
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `created library appears in list`() {
        val token = registerAndGetToken()
        app(
            authedRequest(Method.POST, "/api/libraries", token)
                .header("Content-Type", "application/json")
                .body("""{"name":"Listed Lib","path":"./data/test-listed-${System.nanoTime()}"}"""),
        )

        val response = app(authedRequest(Method.GET, "/api/libraries", token))
        assertEquals(Status.OK, response.status)

        val libraries = Json.mapper.readValue(response.bodyString(), Array<LibraryDto>::class.java)
        assertTrue(libraries.any { it.name == "Listed Lib" })
    }

    @Test
    fun `delete library returns 200`() {
        val token = registerAndGetToken()
        val createResponse = app(
            authedRequest(Method.POST, "/api/libraries", token)
                .header("Content-Type", "application/json")
                .body("""{"name":"Delete Me","path":"./data/test-del-${System.nanoTime()}"}"""),
        )
        val libraryId = Json.mapper.readValue(createResponse.bodyString(), LibraryDto::class.java).id

        val deleteResponse = app(authedRequest(Method.DELETE, "/api/libraries/$libraryId", token))
        assertEquals(Status.OK, deleteResponse.status)
    }

    @Test
    fun `delete non-existent library returns 404`() {
        val token = registerAndGetToken()
        val response = app(authedRequest(Method.DELETE, "/api/libraries/00000000-0000-0000-0000-000000000000", token))
        assertEquals(Status.NOT_FOUND, response.status)
    }

    @Test
    fun `deleted library no longer appears in list`() {
        val token = registerAndGetToken()
        val createResponse = app(
            authedRequest(Method.POST, "/api/libraries", token)
                .header("Content-Type", "application/json")
                .body("""{"name":"Gone Lib","path":"./data/test-gone-${System.nanoTime()}"}"""),
        )
        val libraryId = Json.mapper.readValue(createResponse.bodyString(), LibraryDto::class.java).id

        app(authedRequest(Method.DELETE, "/api/libraries/$libraryId", token))

        val listResponse = app(authedRequest(Method.GET, "/api/libraries", token))
        val libraries = Json.mapper.readValue(listResponse.bodyString(), Array<LibraryDto>::class.java)
        assertTrue(libraries.none { it.id == libraryId })
    }

    @Test
    fun `user cannot see another user's libraries`() {
        val token1 = registerAndGetToken()
        val token2 = registerAndGetToken()

        app(
            authedRequest(Method.POST, "/api/libraries", token1)
                .header("Content-Type", "application/json")
                .body("""{"name":"Private Lib","path":"./data/test-priv-${System.nanoTime()}"}"""),
        )

        val response = app(authedRequest(Method.GET, "/api/libraries", token2))
        val libraries = Json.mapper.readValue(response.bodyString(), Array<LibraryDto>::class.java)
        assertTrue(libraries.none { it.name == "Private Lib" })
    }

    @Test
    fun `user cannot delete another user's library`() {
        val token1 = registerAndGetToken()
        val token2 = registerAndGetToken()

        val createResponse = app(
            authedRequest(Method.POST, "/api/libraries", token1)
                .header("Content-Type", "application/json")
                .body("""{"name":"Protected","path":"./data/test-prot-${System.nanoTime()}"}"""),
        )
        val libraryId = Json.mapper.readValue(createResponse.bodyString(), LibraryDto::class.java).id

        val deleteResponse = app(authedRequest(Method.DELETE, "/api/libraries/$libraryId", token2))
        assertEquals(Status.NOT_FOUND, deleteResponse.status)
    }
}
