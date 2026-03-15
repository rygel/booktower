package org.booktower.handlers

import org.booktower.TestFixture
import org.booktower.config.WeblateConfig
import org.booktower.services.AdminService
import org.booktower.services.AuthService
import org.booktower.services.BookmarkService
import org.booktower.services.PdfMetadataService
import org.booktower.services.UserSettingsService
import org.booktower.services.BookService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.booktower.weblate.WeblateHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HtmxHandlerTest {
    private lateinit var appHandler: AppHandler

    @BeforeEach
    fun setup() {
        val config = TestFixture.config
        val jdbi = TestFixture.database.getJdbi()
        val jwtService = JwtService(config.security)
        val authService = AuthService(jdbi, jwtService)
        val pdfMetadataService = PdfMetadataService(jdbi, config.storage.coversPath)
        val libraryService = LibraryService(jdbi, pdfMetadataService)
        val bookService = BookService(jdbi)
        val bookmarkService = BookmarkService(jdbi)
        val userSettingsService = UserSettingsService(jdbi)
        val adminService = AdminService(jdbi)
        val weblateHandler = WeblateHandler(WeblateConfig("", "", "", false))
        appHandler = AppHandler(
            authService, libraryService, bookService, bookmarkService,
            userSettingsService, pdfMetadataService, adminService, jwtService,
            config.storage, TestFixture.templateRenderer, weblateHandler,
        )
    }

    @Test
    fun `set theme returns 200 OK with style element`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/theme").body("theme=dark")
            .header("Content-Type", "application/x-www-form-urlencoded"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("<style"))
        assertTrue(response.bodyString().contains("theme-style"))
    }

    @Test
    fun `set theme sets app_theme cookie`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/theme").body("theme=dracula")
            .header("Content-Type", "application/x-www-form-urlencoded"))
        assertEquals(Status.OK, response.status)
        val setCookie = response.header("Set-Cookie")
        assertNotNull(setCookie)
        assertTrue(setCookie!!.contains("app_theme"))
        assertTrue(setCookie.contains("dracula"))
    }

    @Test
    fun `set theme response body contains data-theme attribute`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/theme").body("theme=nord")
            .header("Content-Type", "application/x-www-form-urlencoded"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("nord"))
    }

    @Test
    fun `set theme with invalid theme defaults to catppuccin-mocha`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/theme").body("theme=nonexistent-theme")
            .header("Content-Type", "application/x-www-form-urlencoded"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("catppuccin-mocha"))
    }

    @Test
    fun `set theme with no body defaults to catppuccin-mocha`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/theme")
            .header("Content-Type", "application/x-www-form-urlencoded"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("catppuccin-mocha"))
    }

    @Test
    fun `set theme to light`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/theme").body("theme=light")
            .header("Content-Type", "application/x-www-form-urlencoded"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("light"))
    }

    @Test
    fun `set language returns 200 OK`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/lang").body("lang=en")
            .header("Content-Type", "application/x-www-form-urlencoded"))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `set language sets app_lang cookie`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/lang").body("lang=fr")
            .header("Content-Type", "application/x-www-form-urlencoded"))
        assertEquals(Status.OK, response.status)
        val setCookie = response.header("Set-Cookie")
        assertNotNull(setCookie)
        assertTrue(setCookie!!.contains("app_lang"))
        assertTrue(setCookie.contains("fr"))
    }

    @Test
    fun `set language sends HX-Refresh to reload page`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/lang").body("lang=de")
            .header("Content-Type", "application/x-www-form-urlencoded"))
        assertEquals(Status.OK, response.status)
        assertEquals("true", response.header("HX-Refresh"))
    }

    @Test
    fun `set language with invalid language defaults to en`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/lang").body("lang=xx")
            .header("Content-Type", "application/x-www-form-urlencoded"))
        assertEquals(Status.OK, response.status)
        val setCookie = response.header("Set-Cookie")
        assertNotNull(setCookie)
        assertTrue(setCookie!!.contains("en"))
    }

    @Test
    fun `set theme returns no HX-Trigger`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/theme").body("theme=dark")
            .header("Content-Type", "application/x-www-form-urlencoded"))
        assertNull(response.header("HX-Trigger"))
    }

    @Test
    fun `set theme returns HTML content type`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/theme").body("theme=dracula")
            .header("Content-Type", "application/x-www-form-urlencoded"))
        assertTrue(response.header("Content-Type")?.contains("text/html") == true)
    }

    @Test
    fun `set catppuccin-mocha theme`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/theme").body("theme=catppuccin-mocha")
            .header("Content-Type", "application/x-www-form-urlencoded"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("catppuccin-mocha"))
    }

    @Test
    fun `set language to german`() {
        val response = appHandler.routes()(Request(Method.POST, "/preferences/lang").body("lang=de")
            .header("Content-Type", "application/x-www-form-urlencoded"))
        assertEquals(Status.OK, response.status)
        val setCookie = response.header("Set-Cookie")
        assertNotNull(setCookie)
        assertTrue(setCookie!!.contains("de"))
    }
}
