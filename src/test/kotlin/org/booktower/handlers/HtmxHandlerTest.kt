package org.booktower.handlers

import org.booktower.config.AppConfig
import org.booktower.config.Database
import org.booktower.services.AuthService
import org.booktower.services.BookService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HtmxHandlerTest {
    private lateinit var appHandler: AppHandler

    @BeforeEach
    fun setup() {
        val config = AppConfig.load()
        val database = Database.connect(config.database)
        val jwtService = JwtService(config.security)
        val authService = AuthService(database.getJdbi(), jwtService)
        val libraryService = LibraryService(database.getJdbi(), config.storage)
        val bookService = BookService(database.getJdbi(), config.storage)
        appHandler = AppHandler(authService, libraryService, bookService, jwtService)
    }

    @Test
    fun `set theme without HTMX header returns redirect`() {
        val request = Request(Method.POST, "/preferences/theme")
            .body("dark")

        val response = appHandler.routes()(request)

        assertEquals(Status.SEE_OTHER, response.status)
        assertTrue(response.header("Location").isNullOrEmpty() || response.header("Location") == "/")
    }

    @Test
    fun `set theme with HTMX header returns OK with HX-Trigger`() {
        val request = Request(Method.POST, "/preferences/theme")
            .header("HX-Request", "true")
            .body("dark")

        val response = appHandler.routes()(request)

        assertEquals(Status.OK, response.status)
        assertTrue(response.header("HX-Trigger")?.contains("theme-updated") == true)
        assertTrue(response.header("HX-Reswap") == "none")
        assertTrue(response.bodyString().contains("dark"))
    }

    @Test
    fun `set theme to light with HTMX header`() {
        val request = Request(Method.POST, "/preferences/theme")
            .header("HX-Request", "true")
            .body("light")

        val response = appHandler.routes()(request)

        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("light"))
        assertTrue(response.header("HX-Trigger") == "theme-updated")
    }

    @Test
    fun `set theme to nord with HTMX header`() {
        val request = Request(Method.POST, "/preferences/theme")
            .header("HX-Request", "true")
            .body("nord")

        val response = appHandler.routes()(request)

        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("nord"))
    }

    @Test
    fun `set language without HTMX header returns redirect`() {
        val request = Request(Method.POST, "/preferences/lang")
            .body("en")

        val response = appHandler.routes()(request)

        assertEquals(Status.SEE_OTHER, response.status)
    }

    @Test
    fun `set language with HTMX header returns OK with HX-Trigger`() {
        val request = Request(Method.POST, "/preferences/lang")
            .header("HX-Request", "true")
            .body("en")

        val response = appHandler.routes()(request)

        assertEquals(Status.OK, response.status)
        assertTrue(response.header("HX-Trigger")?.contains("lang-updated") == true)
        assertTrue(response.header("HX-Reswap") == "none")
        assertTrue(response.bodyString().contains("en"))
    }

    @Test
    fun `set language to french with HTMX header`() {
        val request = Request(Method.POST, "/preferences/lang")
            .header("HX-Request", "true")
            .body("fr")

        val response = appHandler.routes()(request)

        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("fr"))
        assertTrue(response.header("HX-Trigger") == "lang-updated")
    }

    @Test
    fun `set theme with empty body defaults to dark`() {
        val request = Request(Method.POST, "/preferences/theme")
            .header("HX-Request", "true")
            .body("   ")

        val response = appHandler.routes()(request)

        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("dark"))
    }

    @Test
    fun `set language with empty body defaults to en`() {
        val request = Request(Method.POST, "/preferences/lang")
            .header("HX-Request", "true")
            .body("   ")

        val response = appHandler.routes()(request)

        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("en"))
    }

    @Test
    fun `HTMX request detection via HX-Request header`() {
        val request = Request(Method.POST, "/preferences/theme")
            .header("HX-Request", "true")
            .body("test-theme")

        val response = appHandler.routes()(request)

        assertEquals(Status.OK, response.status)
        assertTrue(response.header("HX-Trigger") != null)
    }

    @Test
    fun `non-HTMX request does not include HX headers in response`() {
        val request = Request(Method.POST, "/preferences/theme")
            .body("test-theme")

        val response = appHandler.routes()(request)

        assertEquals(Status.SEE_OTHER, response.status)
        assertTrue(response.header("HX-Trigger") == null)
        assertTrue(response.header("HX-Reswap") == null)
    }

    @Test
    fun `set theme response body contains theme name`() {
        val request = Request(Method.POST, "/preferences/theme")
            .header("HX-Request", "true")
            .body("monokai-pro")

        val response = appHandler.routes()(request)

        assertTrue(response.bodyString().contains("monokai-pro"))
        assertTrue(response.bodyString().contains("Theme updated"))
    }

    @Test
    fun `set language response body contains language code`() {
        val request = Request(Method.POST, "/preferences/lang")
            .header("HX-Request", "true")
            .body("fr")

        val response = appHandler.routes()(request)

        assertTrue(response.bodyString().contains("fr"))
        assertTrue(response.bodyString().contains("Language updated"))
    }

    @Test
    fun `HTMX theme update triggers client-side event`() {
        val request = Request(Method.POST, "/preferences/theme")
            .header("HX-Request", "true")
            .body("dracula")

        val response = appHandler.routes()(request)

        assertEquals("theme-updated", response.header("HX-Trigger"))
    }

    @Test
    fun `HTMX language update triggers client-side event`() {
        val request = Request(Method.POST, "/preferences/lang")
            .header("HX-Request", "true")
            .body("fr")

        val response = appHandler.routes()(request)

        assertEquals("lang-updated", response.header("HX-Trigger"))
    }

    @Test
    fun `HTMX response uses HX-Reswap none for theme updates`() {
        val request = Request(Method.POST, "/preferences/theme")
            .header("HX-Request", "true")
            .body("one-dark")

        val response = appHandler.routes()(request)

        assertEquals("none", response.header("HX-Reswap"))
    }

    @Test
    fun `HTMX response uses HX-Reswap none for language updates`() {
        val request = Request(Method.POST, "/preferences/lang")
            .header("HX-Request", "true")
            .body("en")

        val response = appHandler.routes()(request)

        assertEquals("none", response.header("HX-Reswap"))
    }

    @Test
    fun `non-HTMX theme update includes redirect message in body`() {
        val request = Request(Method.POST, "/preferences/theme")
            .body("dark")

        val response = appHandler.routes()(request)

        assertTrue(response.bodyString().contains("Redirecting"))
    }

    @Test
    fun `non-HTMX language update includes redirect message in body`() {
        val request = Request(Method.POST, "/preferences/lang")
            .body("en")

        val response = appHandler.routes()(request)

        assertTrue(response.bodyString().contains("Redirecting"))
    }

    @Test
    fun `set theme with whitespace body trims correctly`() {
        val request = Request(Method.POST, "/preferences/theme")
            .header("HX-Request", "true")
            .body("  catppuccin-mocha  ")

        val response = appHandler.routes()(request)

        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("catppuccin-mocha"))
    }

    @Test
    fun `set language with whitespace body trims correctly`() {
        val request = Request(Method.POST, "/preferences/lang")
            .header("HX-Request", "true")
            .body("  fr  ")

        val response = appHandler.routes()(request)

        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("fr"))
    }
}
