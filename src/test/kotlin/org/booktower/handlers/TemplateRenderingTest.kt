package org.booktower.handlers

import org.booktower.config.AppConfig
import org.booktower.config.Database
import org.booktower.config.TemplateEngine
import org.booktower.services.AuthService
import org.booktower.services.BookService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.booktower.web.WebContext
import org.http4k.core.Method
import org.http4k.core.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TemplateRenderingTest {
    private lateinit var jwtService: JwtService
    private lateinit var authService: AuthService
    private lateinit var config: AppConfig
    private lateinit var database: Database

    @BeforeEach
    fun setup() {
        config = AppConfig.load()
        database = Database.connect(config.database)
        jwtService = JwtService(config.security)
        authService = AuthService(database.getJdbi(), jwtService)
    }

    @AfterEach
    fun cleanup() {
    }

    @Test
    fun `index template renders with theme CSS`() {
        val content =
            TemplateEngine.render(
                "index.kte",
                mapOf(
                    "title" to "BookTower",
                    "isAuthenticated" to false,
                    "username" to null,
                    "libraries" to null,
                ),
            )

        assertTrue(content.contains("<html"))
        assertTrue(content.contains("</html>"))
        assertTrue(content.contains("BookTower"))
        assertTrue(content.contains("htmx.org"))
    }

    @Test
    fun `index template shows login and register when not authenticated`() {
        val content =
            TemplateEngine.render(
                "index.kte",
                mapOf(
                    "title" to "BookTower",
                    "isAuthenticated" to false,
                    "username" to null,
                    "libraries" to null,
                ),
            )

        assertTrue(content.contains("Login"))
        assertTrue(content.contains("Sign Up"))
    }

    @Test
    fun `index template shows libraries when authenticated`() {
        val content =
            TemplateEngine.render(
                "index.kte",
                mapOf(
                    "title" to "BookTower",
                    "isAuthenticated" to true,
                    "username" to "testuser",
                    "libraries" to listOf(mapOf("id" to "1", "name" to "Test Library")),
                ),
            )

        assertTrue(content.contains("Your Libraries"))
        assertTrue(content.contains("Test Library"))
    }

    @Test
    fun `index template includes theme selector`() {
        val content =
            TemplateEngine.render(
                "index.kte",
                mapOf(
                    "title" to "BookTower",
                    "isAuthenticated" to true,
                    "username" to "testuser",
                    "libraries" to null,
                ),
            )

        assertTrue(content.contains("Appearance"))
    }

    @Test
    fun `index template includes language selector`() {
        val content =
            TemplateEngine.render(
                "index.kte",
                mapOf(
                    "title" to "BookTower",
                    "isAuthenticated" to true,
                    "username" to "testuser",
                    "libraries" to null,
                ),
            )

        assertTrue(content.contains("Language"))
    }

    @Test
    fun `template includes HTMX script`() {
        val content =
            TemplateEngine.render(
                "index.kte",
                mapOf(
                    "title" to "BookTower",
                    "isAuthenticated" to false,
                    "username" to null,
                    "libraries" to null,
                ),
            )

        assertTrue(content.contains("htmx.org"))
    }

    @Test
    fun `template includes RemixIcon CSS`() {
        val content =
            TemplateEngine.render(
                "index.kte",
                mapOf(
                    "title" to "BookTower",
                    "isAuthenticated" to false,
                    "username" to null,
                    "libraries" to null,
                ),
            )

        assertTrue(content.contains("remixicon"))
    }
}
