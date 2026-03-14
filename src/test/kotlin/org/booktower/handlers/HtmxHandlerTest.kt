package org.booktower.handlers

import org.booktower.TestFixture
import org.booktower.config.TemplateRenderer
import org.booktower.services.AuthService
import org.booktower.services.BookmarkService
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
        val config = TestFixture.config
        val jdbi = TestFixture.database.getJdbi()
        val jwtService = JwtService(config.security)
        val authService = AuthService(jdbi, jwtService)
        val libraryService = LibraryService(jdbi, config.storage)
        val bookService = BookService(jdbi, config.storage)
        val bookmarkService = BookmarkService(jdbi)
        appHandler = AppHandler(authService, libraryService, bookService, bookmarkService, jwtService, TemplateRenderer())
    }

    @Test
    fun `set theme without HTMX header returns redirect`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/theme").body("dark"))
        assertEquals(Status.SEE_OTHER, response.status)
        assertTrue(response.header("Location").isNullOrEmpty() || response.header("Location") == "/")
    }

    @Test
    fun `set theme with HTMX header returns OK with HX-Trigger`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/theme").header("HX-Request", "true").body("dark"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.header("HX-Trigger")?.contains("theme-updated") == true)
        assertTrue(response.header("HX-Reswap") == "none")
        assertTrue(response.bodyString().contains("dark"))
    }

    @Test
    fun `set theme to light with HTMX header`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/theme").header("HX-Request", "true").body("light"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("light"))
        assertTrue(response.header("HX-Trigger") == "theme-updated")
    }

    @Test
    fun `set theme to nord with HTMX header`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/theme").header("HX-Request", "true").body("nord"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("nord"))
    }

    @Test
    fun `set language without HTMX header returns redirect`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/lang").body("en"))
        assertEquals(Status.SEE_OTHER, response.status)
    }

    @Test
    fun `set language with HTMX header returns OK with HX-Trigger`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/lang").header("HX-Request", "true").body("en"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.header("HX-Trigger")?.contains("lang-updated") == true)
        assertTrue(response.header("HX-Reswap") == "none")
        assertTrue(response.bodyString().contains("en"))
    }

    @Test
    fun `set language to french with HTMX header`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/lang").header("HX-Request", "true").body("fr"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("fr"))
        assertTrue(response.header("HX-Trigger") == "lang-updated")
    }

    @Test
    fun `set theme with empty body defaults to dark`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/theme").header("HX-Request", "true").body("   "))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("dark"))
    }

    @Test
    fun `set language with empty body defaults to en`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/lang").header("HX-Request", "true").body("   "))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("en"))
    }

    @Test
    fun `HTMX request detection via HX-Request header`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/theme").header("HX-Request", "true").body("test-theme"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.header("HX-Trigger") != null)
    }

    @Test
    fun `non-HTMX request does not include HX headers in response`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/theme").body("test-theme"))
        assertEquals(Status.SEE_OTHER, response.status)
        assertTrue(response.header("HX-Trigger") == null)
        assertTrue(response.header("HX-Reswap") == null)
    }

    @Test
    fun `set theme response body contains theme name`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/theme").header("HX-Request", "true").body("monokai-pro"))
        assertTrue(response.bodyString().contains("monokai-pro"))
        assertTrue(response.bodyString().contains("Theme updated"))
    }

    @Test
    fun `set language response body contains language code`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/lang").header("HX-Request", "true").body("fr"))
        assertTrue(response.bodyString().contains("fr"))
        assertTrue(response.bodyString().contains("Language updated"))
    }

    @Test
    fun `HTMX theme update triggers client-side event`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/theme").header("HX-Request", "true").body("dracula"))
        assertEquals("theme-updated", response.header("HX-Trigger"))
    }

    @Test
    fun `HTMX language update triggers client-side event`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/lang").header("HX-Request", "true").body("fr"))
        assertEquals("lang-updated", response.header("HX-Trigger"))
    }

    @Test
    fun `HTMX response uses HX-Reswap none for theme updates`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/theme").header("HX-Request", "true").body("one-dark"))
        assertEquals("none", response.header("HX-Reswap"))
    }

    @Test
    fun `HTMX response uses HX-Reswap none for language updates`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/lang").header("HX-Request", "true").body("en"))
        assertEquals("none", response.header("HX-Reswap"))
    }

    @Test
    fun `non-HTMX theme update includes redirect message in body`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/theme").body("dark"))
        assertTrue(response.bodyString().contains("Redirecting"))
    }

    @Test
    fun `non-HTMX language update includes redirect message in body`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/lang").body("en"))
        assertTrue(response.bodyString().contains("Redirecting"))
    }

    @Test
    fun `set theme with whitespace body trims correctly`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/theme").header("HX-Request", "true").body("  catppuccin-mocha  "))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("catppuccin-mocha"))
    }

    @Test
    fun `set language with whitespace body trims correctly`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/lang").header("HX-Request", "true").body("  fr  "))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("fr"))
    }
}
