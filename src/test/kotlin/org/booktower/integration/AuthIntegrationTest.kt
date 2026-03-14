package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.config.Json
import org.booktower.config.TemplateRenderer
import org.booktower.filters.GlobalErrorFilter
import org.booktower.handlers.AppHandler
import org.booktower.models.ErrorResponse
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
import org.http4k.core.cookie.cookies
import org.http4k.core.then
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthIntegrationTest {
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

    private fun uniqueUser() = "user_${System.nanoTime()}"

    private fun registerJson(username: String, email: String = "$username@test.com", password: String = "password123"): String =
        """{"username":"$username","email":"$email","password":"$password"}"""

    private fun loginJson(username: String, password: String = "password123"): String =
        """{"username":"$username","password":"$password"}"""

    @Test
    fun `register returns 201 with token and user data`() {
        val username = uniqueUser()
        val response = app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body(registerJson(username)),
        )

        assertEquals(Status.CREATED, response.status)
        val body = Json.mapper.readValue(response.bodyString(), LoginResponse::class.java)
        assertEquals(username, body.user.username)
        assertTrue(body.token.isNotBlank())
        assertNotNull(response.cookies().find { it.name == "token" })
        assertEquals(body.token, response.cookies().find { it.name == "token" }?.value)
    }

    @Test
    fun `register sets httpOnly cookie`() {
        val response = app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body(registerJson(uniqueUser())),
        )

        val cookie = response.cookies().find { it.name == "token" }
        assertNotNull(cookie)
        assertTrue(cookie.httpOnly)
    }

    @Test
    fun `register with duplicate username returns error`() {
        val username = uniqueUser()
        app(Request(Method.POST, "/auth/register").header("Content-Type", "application/json").body(registerJson(username)))

        val response = app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body(registerJson(username, "${username}_2@test.com")),
        )

        assertTrue(response.status.code >= 400, "Expected error status but got ${response.status}")
        assertTrue(response.bodyString().isNotBlank())
    }

    @Test
    fun `register with empty body returns 400`() {
        val response = app(Request(Method.POST, "/auth/register").header("Content-Type", "application/json").body(""))
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `register with short username returns 400`() {
        val response = app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"ab","email":"ab@test.com","password":"password123"}"""),
        )

        assertEquals(Status.BAD_REQUEST, response.status)
        assertTrue(response.bodyString().contains("VALIDATION_ERROR"))
    }

    @Test
    fun `register with short password returns 400`() {
        val response = app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"${uniqueUser()}","email":"x@test.com","password":"short"}"""),
        )

        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `register with invalid email returns 400`() {
        val response = app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"${uniqueUser()}","email":"not-an-email","password":"password123"}"""),
        )

        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `register with special chars in username returns 400`() {
        val response = app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"user@name!","email":"x@test.com","password":"password123"}"""),
        )

        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `login with correct credentials returns 200 with token`() {
        val username = uniqueUser()
        app(Request(Method.POST, "/auth/register").header("Content-Type", "application/json").body(registerJson(username)))

        val response = app(
            Request(Method.POST, "/auth/login")
                .header("Content-Type", "application/json")
                .body(loginJson(username)),
        )

        assertEquals(Status.OK, response.status)
        val body = Json.mapper.readValue(response.bodyString(), LoginResponse::class.java)
        assertEquals(username, body.user.username)
        assertTrue(body.token.isNotBlank())
        assertNotNull(response.cookies().find { it.name == "token" })
    }

    @Test
    fun `login with wrong password returns 401`() {
        val username = uniqueUser()
        app(Request(Method.POST, "/auth/register").header("Content-Type", "application/json").body(registerJson(username)))

        val response = app(
            Request(Method.POST, "/auth/login")
                .header("Content-Type", "application/json")
                .body(loginJson(username, "wrongpassword")),
        )

        assertEquals(Status.UNAUTHORIZED, response.status)
        assertTrue(response.bodyString().contains("INVALID_CREDENTIALS"))
    }

    @Test
    fun `login with non-existent user returns 401`() {
        val response = app(
            Request(Method.POST, "/auth/login")
                .header("Content-Type", "application/json")
                .body(loginJson("nonexistent_${System.nanoTime()}")),
        )

        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `login with empty body returns 400`() {
        val response = app(Request(Method.POST, "/auth/login").header("Content-Type", "application/json").body(""))
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `logout clears token cookie`() {
        val response = app(Request(Method.POST, "/auth/logout"))

        assertEquals(Status.OK, response.status)
        val cookie = response.cookies().find { it.name == "token" }
        assertNotNull(cookie)
        assertEquals("", cookie.value)
        assertEquals(0L, cookie.maxAge)
    }

    @Test
    fun `register then login produces valid tokens for API access`() {
        val username = uniqueUser()
        app(Request(Method.POST, "/auth/register").header("Content-Type", "application/json").body(registerJson(username)))

        val loginResponse = app(
            Request(Method.POST, "/auth/login")
                .header("Content-Type", "application/json")
                .body(loginJson(username)),
        )
        val token = loginResponse.cookies().find { it.name == "token" }!!.value

        val librariesResponse = app(
            Request(Method.GET, "/api/libraries").header("Cookie", "token=$token"),
        )

        assertEquals(Status.OK, librariesResponse.status)
    }
}
