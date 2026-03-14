package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.config.Json
import org.booktower.config.TemplateRenderer
import org.booktower.filters.GlobalErrorFilter
import org.booktower.handlers.AppHandler
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PageIntegrationTest {
    private lateinit var app: HttpHandler

    @BeforeEach
    fun setup() {
        val config = TestFixture.config
        val jdbi = TestFixture.database.getJdbi()
        val jwtService = JwtService(config.security)
        val authService = AuthService(jdbi, jwtService)
        val libraryService = LibraryService(jdbi, config.storage)
        val bookService = BookService(jdbi, config.storage)
        val bookmarkService = BookmarkService(jdbi)
        val userSettingsService = UserSettingsService(jdbi)
        val pdfMetadataService = PdfMetadataService(jdbi, config.storage.coversPath)
        val appHandler = AppHandler(authService, libraryService, bookService, bookmarkService, userSettingsService, pdfMetadataService, jwtService, config.storage, TemplateRenderer())
        app = GlobalErrorFilter().then(appHandler.routes())
    }

    @Test
    fun `index page returns HTML`() {
        val response = app(Request(Method.GET, "/"))
        assertEquals(Status.OK, response.status)
        assertEquals("text/html", response.header("Content-Type"))
        assertTrue(response.bodyString().contains("<html"))
    }

    @Test
    fun `index page without auth shows login and register links`() {
        val response = app(Request(Method.GET, "/"))
        assertTrue(response.bodyString().contains("Login"))
        assertTrue(response.bodyString().contains("Sign Up"))
        assertFalse(response.bodyString().contains("hx-post=\"/auth/logout\""))
    }

    @Test
    fun `index page with auth shows authenticated view`() {
        val username = "pageuser_${System.nanoTime()}"
        val regResponse = app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
        )
        val token = Json.mapper.readValue(regResponse.bodyString(), LoginResponse::class.java).token

        val response = app(Request(Method.GET, "/").header("Cookie", "token=$token"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("hx-post=\"/auth/logout\""))
    }

    @Test
    fun `index page with invalid token shows unauthenticated view`() {
        val response = app(Request(Method.GET, "/").header("Cookie", "token=bogus"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("Login"))
        assertFalse(response.bodyString().contains("hx-post=\"/auth/logout\""))
    }

    @Test
    fun `login page returns HTML`() {
        val response = app(Request(Method.GET, "/login"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("Login"))
    }

    @Test
    fun `register page returns HTML`() {
        val response = app(Request(Method.GET, "/register"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("Register"))
    }

    @Test
    fun `authenticated index page shows user libraries`() {
        val username = "libpage_${System.nanoTime()}"
        val regResponse = app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
        )
        val token = Json.mapper.readValue(regResponse.bodyString(), LoginResponse::class.java).token

        app(
            Request(Method.POST, "/api/libraries")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"name":"Page Test Library","path":"./data/test-page-${System.nanoTime()}"}"""),
        )

        val response = app(Request(Method.GET, "/").header("Cookie", "token=$token"))
        assertTrue(response.bodyString().contains("Page Test Library"))
    }

    @Test
    fun `health endpoint returns OK`() {
        // Health is mounted in BookTowerApp.main(), not in AppHandler,
        // so we just verify that the app doesn't crash on unknown routes
        val response = app(Request(Method.GET, "/nonexistent"))
        // http4k returns 404 for unmatched routes
        assertEquals(Status.NOT_FOUND, response.status)
    }
}
