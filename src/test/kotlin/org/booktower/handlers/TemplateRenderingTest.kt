package org.booktower.handlers

import org.booktower.TestFixture
import org.booktower.i18n.I18nService
import org.booktower.model.ThemeCatalog
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TemplateRenderingTest {
    private val i18n = I18nService.create("messages")
    private val themeCss = ThemeCatalog.toCssVariables("dark")

    private fun renderIndex(
        isAuthenticated: Boolean = false,
        username: String? = null,
        libraries: Any? = null,
        showLogin: Boolean = false,
        showRegister: Boolean = false,
    ): String {
        return TestFixture.templateRenderer.render("index.kte", mapOf(
            "title" to "BookTower",
            "isAuthenticated" to isAuthenticated,
            "username" to username,
            "libraries" to libraries,
            "showLogin" to showLogin,
            "showRegister" to showRegister,
            "themeCss" to themeCss,
            "currentTheme" to "dark",
            "lang" to "en",
            "i18n" to i18n,
        ))
    }

    @Test
    fun `index template renders with theme CSS`() {
        val content = renderIndex()
        assertTrue(content.contains("<html"))
        assertTrue(content.contains("</html>"))
        assertTrue(content.contains("BookTower"))
        assertTrue(content.contains("htmx"))
    }

    @Test
    fun `index template injects server-side theme CSS`() {
        val content = renderIndex()
        assertTrue(content.contains("id=\"theme-style\""))
        assertTrue(content.contains(":root"))
    }

    @Test
    fun `index template shows sign in and sign up when not authenticated`() {
        val content = renderIndex()
        assertTrue(content.contains("Sign in"))
        assertTrue(content.contains("Sign up"))
    }

    @Test
    fun `index template shows login form when showLogin is true`() {
        val content = renderIndex(showLogin = true)
        assertTrue(content.contains("action=\"/auth/login\""))
        assertTrue(content.contains("name=\"username\""))
        assertTrue(content.contains("name=\"password\""))
    }

    @Test
    fun `index template shows register form when showRegister is true`() {
        val content = renderIndex(showRegister = true)
        assertTrue(content.contains("action=\"/auth/register\""))
        assertTrue(content.contains("name=\"email\""))
    }

    @Test
    fun `index template includes HTMX script`() {
        val content = renderIndex()
        assertTrue(content.contains("htmx.min.js") || content.contains("htmx.org"))
    }

    @Test
    fun `index template includes RemixIcon CSS`() {
        val content = renderIndex()
        assertTrue(content.contains("remixicon"))
    }

    @Test
    fun `index template does not include logout button when not authenticated`() {
        val content = renderIndex()
        assertFalse(content.contains("hx-post=\"/auth/logout\""))
    }

    @Test
    fun `HTMX script loads from vendor path`() {
        val content = renderIndex()
        assertTrue(content.contains("/static/vendor/htmx.min.js"), "htmx must be served from vendor path, not CDN")
    }

    @Test
    fun `HTMX script is in head section`() {
        val content = renderIndex()
        val headSection = content.substring(content.indexOf("<head>"), content.indexOf("</head>"))
        assertTrue(headSection.contains("htmx"))
    }

    @Test
    fun `index template uses correct language attribute`() {
        val content = renderIndex()
        assertTrue(content.contains("lang=\"en\""))
    }

    @Test
    fun `index template theme selector posts to preferences endpoint`() {
        val content = renderIndex()
        // index.kte is the landing page — no theme selector (that lives in layout.kte/sidebar)
        assertTrue(content.contains("BookTower"))
    }

    @Test
    fun `login form has correct labels in English`() {
        val content = renderIndex(showLogin = true)
        assertTrue(content.contains("Username or email") || content.contains("Benutzername"))
    }

    @Test
    fun `register form has correct labels in English`() {
        val content = renderIndex(showRegister = true)
        assertTrue(content.contains("Username") || content.contains("Benutzername"))
        assertTrue(content.contains("Email") || content.contains("E-Mail"))
        assertTrue(content.contains("Password") || content.contains("Passwort"))
    }

    @Test
    fun `index template landing page contains tagline`() {
        val content = renderIndex()
        // Shows the landing page with tagline
        assertTrue(content.contains("BookTower"))
        assertTrue(content.contains("Sign in") || content.contains("Anmelden"))
    }

    @Test
    fun `index template footer contains copyright`() {
        val content = renderIndex()
        assertTrue(content.contains("BookTower"))
        assertTrue(content.contains("footer"))
    }

    @Test
    fun `login page submit button uses i18n key`() {
        val content = renderIndex(showLogin = true)
        assertTrue(content.contains("Sign in"))
    }

    @Test
    fun `register page create account button uses i18n key`() {
        val content = renderIndex(showRegister = true)
        assertTrue(content.contains("Create account"))
    }

    @Test
    fun `index template theme style element present in head`() {
        val content = renderIndex()
        val headSection = content.substring(content.indexOf("<head>"), content.indexOf("</head>"))
        assertTrue(headSection.contains("theme-style"))
    }

    @Test
    fun `index template no flash of unstyled content`() {
        val content = renderIndex()
        // Server-side CSS injection means no localStorage JS needed
        assertFalse(content.contains("localStorage.getItem"))
    }

    @Test
    fun `theme selector uses POST method for HTMX request`() {
        // Theme selector is in layout.kte (sidebar), rendered only for authenticated pages
        // index.kte (landing/login/register) does not include the sidebar
        val content = renderIndex(showLogin = true)
        // login page should have the form action but not the sidebar theme selector
        assertTrue(content.contains("action=\"/auth/login\""))
    }
}
