package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.config.Json
import org.booktower.config.TemplateRenderer
import org.booktower.filters.GlobalErrorFilter
import org.booktower.handlers.AppHandler
import org.booktower.models.LoginResponse
import org.booktower.services.AuthService
import org.booktower.services.BookmarkService
import org.booktower.services.BookService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.booktower.services.PdfMetadataService
import org.booktower.services.UserSettingsService
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.then
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserSettingsIntegrationTest {
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

    private fun uniqueUser() = "us_${System.nanoTime()}"

    private fun registerAndGetToken(): String {
        val username = uniqueUser()
        val response = app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
        )
        return Json.mapper.readValue(response.bodyString(), LoginResponse::class.java).token
    }

    @Test
    fun `get settings returns empty map for new user`() {
        val token = registerAndGetToken()
        val response = app(
            Request(Method.GET, "/api/settings")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.OK, response.status)
        val settings = Json.mapper.readTree(response.bodyString())
        assertTrue(settings.isObject)
        assertEquals(0, settings.size())
    }

    @Test
    fun `set and get setting round-trips correctly`() {
        val token = registerAndGetToken()

        app(
            Request(Method.PUT, "/api/settings/theme")
                .header("Cookie", "token=$token")
                .body("dark"),
        )

        val response = app(
            Request(Method.GET, "/api/settings")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.OK, response.status)
        val settings = Json.mapper.readTree(response.bodyString())
        assertEquals("dark", settings.get("theme").asText())
    }

    @Test
    fun `updating setting overwrites previous value`() {
        val token = registerAndGetToken()

        app(
            Request(Method.PUT, "/api/settings/theme")
                .header("Cookie", "token=$token")
                .body("dark"),
        )
        app(
            Request(Method.PUT, "/api/settings/theme")
                .header("Cookie", "token=$token")
                .body("light"),
        )

        val response = app(
            Request(Method.GET, "/api/settings")
                .header("Cookie", "token=$token"),
        )
        val settings = Json.mapper.readTree(response.bodyString())
        assertEquals("light", settings.get("theme").asText())
    }

    @Test
    fun `multiple settings are stored independently`() {
        val token = registerAndGetToken()

        app(Request(Method.PUT, "/api/settings/theme").header("Cookie", "token=$token").body("dark"))
        app(Request(Method.PUT, "/api/settings/language").header("Cookie", "token=$token").body("en"))
        app(Request(Method.PUT, "/api/settings/page-size").header("Cookie", "token=$token").body("20"))

        val response = app(Request(Method.GET, "/api/settings").header("Cookie", "token=$token"))
        val settings = Json.mapper.readTree(response.bodyString())
        assertEquals("dark", settings.get("theme").asText())
        assertEquals("en", settings.get("language").asText())
        assertEquals("20", settings.get("page-size").asText())
    }

    @Test
    fun `settings are isolated between users`() {
        val tokenA = registerAndGetToken()
        val tokenB = registerAndGetToken()

        app(Request(Method.PUT, "/api/settings/theme").header("Cookie", "token=$tokenA").body("dark"))

        val response = app(Request(Method.GET, "/api/settings").header("Cookie", "token=$tokenB"))
        val settings = Json.mapper.readTree(response.bodyString())
        assertEquals(0, settings.size())
    }

    @Test
    fun `set setting with JSON string value works`() {
        val token = registerAndGetToken()

        val response = app(
            Request(Method.PUT, "/api/settings/font")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("\"serif\""),
        )

        assertEquals(Status.OK, response.status)
        val body = Json.mapper.readTree(response.bodyString())
        assertEquals("font", body.get("key").asText())
        assertEquals("serif", body.get("value").asText())
    }

    @Test
    fun `set setting without auth returns 401`() {
        val response = app(
            Request(Method.PUT, "/api/settings/theme")
                .body("dark"),
        )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `get settings without auth returns 401`() {
        val response = app(Request(Method.GET, "/api/settings"))
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `set setting with blank body stores null value`() {
        val token = registerAndGetToken()
        val response = app(
            Request(Method.PUT, "/api/settings/reset-key")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.OK, response.status)
    }
}
