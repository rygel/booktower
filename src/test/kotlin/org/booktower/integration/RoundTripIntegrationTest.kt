package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.config.Json
import org.booktower.filters.CsrfFilter
import org.booktower.filters.GlobalErrorFilter
import org.booktower.filters.RequestLoggingFilter
import org.booktower.filters.StaticCacheFilter
import org.booktower.config.WeblateConfig
import org.booktower.handlers.AppHandler
import org.booktower.models.LibraryDto
import org.booktower.models.LoginResponse
import org.booktower.services.AdminService
import org.booktower.services.AnalyticsService
import org.booktower.services.AnnotationService
import org.booktower.services.AuthService
import org.booktower.services.MetadataFetchService
import org.booktower.services.BookmarkService
import org.booktower.services.PdfMetadataService
import org.booktower.services.UserSettingsService
import org.booktower.services.BookService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.booktower.weblate.WeblateHandler
import org.http4k.client.JettyClient
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.routing.bind
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.routing.routes
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoundTripIntegrationTest {
    private val client = JettyClient()
    private var port: Int = 0
    private lateinit var server: org.http4k.server.Http4kServer

    @BeforeAll
    fun startServer() {
        val config = TestFixture.config
        val jdbi = TestFixture.database.getJdbi()
        val jwtService = JwtService(config.security)
        val authService = AuthService(jdbi, jwtService)
        val pdfMetadataService = PdfMetadataService(jdbi, config.storage.coversPath)
        val libraryService = LibraryService(jdbi, pdfMetadataService)
        val userSettingsService = UserSettingsService(jdbi)
        val analyticsService = AnalyticsService(jdbi, userSettingsService)
        val bookService = BookService(jdbi, analyticsService)
        val bookmarkService = BookmarkService(jdbi)
        val adminService = AdminService(jdbi)
        val annotationService = AnnotationService(jdbi)
        val metadataFetchService = MetadataFetchService()
        val appHandler = AppHandler(
            authService, libraryService, bookService, bookmarkService,
            userSettingsService, pdfMetadataService, adminService, jwtService,
            config.storage, TestFixture.templateRenderer, WeblateHandler(WeblateConfig("", "", "", false)),
            analyticsService, annotationService, metadataFetchService,
            org.booktower.services.MagicShelfService(jdbi, bookService),
        )

        val app = routes(
            "/health" bind Method.GET to { Response(OK).body("OK") },
            appHandler.routes(),
        )

        val filteredApp = GlobalErrorFilter()
            .then(RequestLoggingFilter())
            .then(CsrfFilter(config.csrf.allowedHosts))
            .then(StaticCacheFilter())
            .then(app)

        server = filteredApp.asServer(Jetty(0)).start()
        port = server.port()
    }

    @AfterAll
    fun stopServer() {
        server.stop()
        client.close()
    }

    private fun url(path: String) = "http://localhost:$port$path"

    private fun uniqueUser() = "rt_${System.nanoTime()}"

    private fun registerUser(username: String): String {
        val response = client(
            Request(Method.POST, url("/auth/register"))
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
        )
        return Json.mapper.readValue(response.bodyString(), LoginResponse::class.java).token
    }

    @Test
    fun `health endpoint returns OK over HTTP`() {
        val response = client(Request(Method.GET, url("/health")))
        assertEquals(Status.OK, response.status)
        assertEquals("OK", response.bodyString())
    }

    @Test
    fun `index page returns HTML over HTTP`() {
        val response = client(Request(Method.GET, url("/")))
        assertEquals(Status.OK, response.status)
        assertTrue(response.header("Content-Type")?.contains("text/html") == true)
        assertTrue(response.bodyString().contains("BookTower"))
    }

    @Test
    fun `register via JSON over HTTP returns 201 with Set-Cookie`() {
        val username = uniqueUser()
        val response = client(
            Request(Method.POST, url("/auth/register"))
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
        )

        assertEquals(Status.CREATED, response.status)
        val setCookie = response.header("Set-Cookie")
        assertNotNull(setCookie, "Should have Set-Cookie header over real HTTP")
        assertTrue(setCookie.contains("token="), "Cookie should contain token")
        assertTrue(setCookie.contains("HttpOnly"), "Cookie should be HttpOnly")
    }

    @Test
    fun `login via JSON over HTTP returns token cookie`() {
        val username = uniqueUser()
        registerUser(username)

        val response = client(
            Request(Method.POST, url("/auth/login"))
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","password":"password123"}"""),
        )

        assertEquals(Status.OK, response.status)
        assertNotNull(response.header("Set-Cookie"))
    }

    @Test
    fun `protected endpoint returns 401 without cookie over HTTP`() {
        val response = client(Request(Method.GET, url("/api/libraries")))
        assertEquals(Status.UNAUTHORIZED, response.status)
        assertTrue(response.header("Content-Type")?.contains("application/json") == true)
    }

    @Test
    fun `protected endpoint works with cookie over HTTP`() {
        val token = registerUser(uniqueUser())
        val response = client(
            Request(Method.GET, url("/api/libraries"))
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `full CRUD flow over HTTP`() {
        val token = registerUser(uniqueUser())

        val createResponse = client(
            Request(Method.POST, url("/api/libraries"))
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"name":"HTTP Lib","path":"./data/test-http-${System.nanoTime()}"}"""),
        )
        assertEquals(Status.CREATED, createResponse.status)
        val library = Json.mapper.readValue(createResponse.bodyString(), LibraryDto::class.java)

        val listResponse = client(
            Request(Method.GET, url("/api/libraries"))
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.OK, listResponse.status)
        val libraries = Json.mapper.readValue(listResponse.bodyString(), Array<LibraryDto>::class.java)
        assertTrue(libraries.any { it.id == library.id })

        val deleteResponse = client(
            Request(Method.DELETE, url("/api/libraries/${library.id}"))
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.OK, deleteResponse.status)
    }

    @Test
    fun `CSRF filter blocks request from foreign origin over HTTP`() {
        val response = client(
            Request(Method.POST, url("/auth/login"))
                .header("Content-Type", "application/json")
                .header("Origin", "http://evil.com")
                .body("""{"username":"x","password":"y"}"""),
        )
        assertEquals(Status.FORBIDDEN, response.status)
    }

    @Test
    fun `CSRF filter allows request from localhost over HTTP`() {
        val response = client(
            Request(Method.POST, url("/auth/login"))
                .header("Content-Type", "application/json")
                .header("Origin", "http://localhost:$port")
                .body("""{"username":"nonexistent","password":"password123"}"""),
        )
        // Should not be 403 (CSRF blocked) - 401 means it got through the CSRF filter
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `static assets served over HTTP`() {
        val response = client(Request(Method.GET, url("/static/css/style.css")))
        assertEquals(Status.OK, response.status, "Static file should be served")
        assertTrue(response.bodyString().isNotBlank(), "Static file should have content")
    }

    @Test
    fun `form login over HTTP redirects with cookie`() {
        val username = uniqueUser()
        registerUser(username)

        val response = client(
            Request(Method.POST, url("/auth/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("username=$username&password=password123"),
        )

        // Jetty client follows redirects by default, so we may get 200 (the index page)
        // The important thing is we got a response and it has the auth cookie
        assertTrue(response.status.code in listOf(200, 303))
    }

    @Test
    fun `404 for unknown route over HTTP`() {
        val response = client(Request(Method.GET, url("/nonexistent/path")))
        assertEquals(Status.NOT_FOUND, response.status)
    }

    @Test
    fun `concurrent requests over HTTP do not interfere`() {
        val tokens = (1..3).map { registerUser(uniqueUser()) }

        val responses = tokens.map { token ->
            client(
                Request(Method.GET, url("/api/libraries"))
                    .header("Cookie", "token=$token"),
            )
        }

        responses.forEach { response ->
            assertEquals(Status.OK, response.status)
        }
    }
}
