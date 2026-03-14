package org.booktower.handlers

import org.booktower.config.TemplateEngine
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TemplateRenderingTest {
    private fun renderIndex(
        isAuthenticated: Boolean = false,
        username: String? = null,
        libraries: Any? = null,
        showLogin: Boolean? = null,
        showRegister: Boolean? = null,
    ): String {
        val model = mutableMapOf<String, Any?>(
            "title" to "BookTower",
            "isAuthenticated" to isAuthenticated,
            "username" to username,
            "libraries" to libraries,
        )
        if (showLogin != null) model["showLogin"] = showLogin
        if (showRegister != null) model["showRegister"] = showRegister
        return TemplateEngine.render("index.kte", model)
    }

    @Test
    fun `index template renders with theme CSS`() {
        val content = renderIndex()
        assertTrue(content.contains("<html"))
        assertTrue(content.contains("</html>"))
        assertTrue(content.contains("BookTower"))
        assertTrue(content.contains("htmx.org"))
    }

    @Test
    fun `index template shows login and register when not authenticated`() {
        val content = renderIndex()
        assertTrue(content.contains("Login"))
        assertTrue(content.contains("Sign Up"))
    }

    @Test
    fun `index template shows libraries when authenticated`() {
        val content = renderIndex(
            isAuthenticated = true,
            username = "testuser",
            libraries = listOf(mapOf("id" to "1", "name" to "Test Library")),
        )
        assertTrue(content.contains("Your Libraries"))
        assertTrue(content.contains("Test Library"))
    }

    @Test
    fun `index template includes theme selector`() {
        val content = renderIndex(isAuthenticated = true, username = "testuser")
        assertTrue(content.contains("Appearance"))
    }

    @Test
    fun `index template includes language selector`() {
        val content = renderIndex(isAuthenticated = true, username = "testuser")
        assertTrue(content.contains("Language"))
    }

    @Test
    fun `template includes HTMX script`() {
        val content = renderIndex()
        assertTrue(content.contains("htmx.org"))
    }

    @Test
    fun `template includes RemixIcon CSS`() {
        val content = renderIndex()
        assertTrue(content.contains("remixicon"))
    }

    @Test
    fun `template includes HTMX theme selector with correct attributes`() {
        val content = renderIndex(isAuthenticated = true, username = "testuser")
        assertTrue(content.contains("hx-post=\"/preferences/theme\""))
        assertTrue(content.contains("hx-target=\"#theme-notice\""))
        assertTrue(content.contains("hx-swap=\"innerHTML\""))
        assertTrue(content.contains("hx-trigger=\"change\""))
    }

    @Test
    fun `template includes HTMX language selector with correct attributes`() {
        val content = renderIndex(isAuthenticated = true, username = "testuser")
        assertTrue(content.contains("hx-post=\"/preferences/lang\""))
        assertTrue(content.contains("hx-target=\"#lang-notice\""))
        assertTrue(content.contains("hx-swap=\"innerHTML\""))
        assertTrue(content.contains("hx-trigger=\"change\""))
    }

    @Test
    fun `template includes theme notice div for HTMX updates`() {
        val content = renderIndex(isAuthenticated = true, username = "testuser")
        assertTrue(content.contains("id=\"theme-notice\""))
    }

    @Test
    fun `template includes language notice div for HTMX updates`() {
        val content = renderIndex(isAuthenticated = true, username = "testuser")
        assertTrue(content.contains("id=\"lang-notice\""))
    }

    @Test
    fun `template theme options include all available themes`() {
        val content = renderIndex(isAuthenticated = true, username = "testuser")
        assertTrue(content.contains("value=\"dark\""))
        assertTrue(content.contains("value=\"light\""))
        assertTrue(content.contains("value=\"nord\""))
        assertTrue(content.contains("value=\"dracula\""))
        assertTrue(content.contains("value=\"monokai-pro\""))
        assertTrue(content.contains("value=\"one-dark\""))
        assertTrue(content.contains("value=\"catppuccin-mocha\""))
    }

    @Test
    fun `template language options include English and French`() {
        val content = renderIndex(isAuthenticated = true, username = "testuser")
        assertTrue(content.contains("value=\"en\""))
        assertTrue(content.contains("value=\"fr\""))
    }

    @Test
    fun `HTMX notice divs have hidden class initially`() {
        val content = renderIndex(isAuthenticated = true, username = "testuser")
        assertTrue(content.contains("id=\"theme-notice\""))
        assertTrue(content.contains("id=\"lang-notice\""))
        assertTrue(content.contains("hidden"))
    }

    @Test
    fun `template includes HTMX logout button when authenticated`() {
        val content = renderIndex(isAuthenticated = true, username = "testuser")
        assertTrue(content.contains("hx-post=\"/auth/logout\""))
    }

    @Test
    fun `template does not include HTMX logout button when not authenticated`() {
        val content = renderIndex()
        assertFalse(content.contains("hx-post=\"/auth/logout\""))
    }

    @Test
    fun `HTMX script loads from unpkg CDN`() {
        val content = renderIndex()
        assertTrue(content.contains("https://unpkg.com/htmx.org@1.9.10"))
    }

    @Test
    fun `HTMX script is in head section`() {
        val content = renderIndex()
        val headSection = content.substring(content.indexOf("<head>"), content.indexOf("</head>"))
        assertTrue(headSection.contains("htmx.org"))
    }

    @Test
    fun `theme selector uses POST method for HTMX request`() {
        val content = renderIndex(isAuthenticated = true, username = "testuser")
        assertTrue(content.contains("hx-post"))
        assertFalse(content.contains("hx-get"))
    }

    @Test
    fun `language selector uses POST method for HTMX request`() {
        val content = renderIndex(isAuthenticated = true, username = "testuser")
        assertTrue(content.contains("hx-post=\"/preferences/lang\""))
    }

    @Test
    fun `HTMX attributes are properly formatted in template`() {
        val content = renderIndex(isAuthenticated = true, username = "testuser")
        assertTrue(content.contains("hx-post=\""))
        assertTrue(content.contains("hx-target=\""))
        assertTrue(content.contains("hx-swap=\""))
        assertTrue(content.contains("hx-trigger=\""))
    }

    @Test
    fun `notice divs have correct CSS classes for styling`() {
        val content = renderIndex(isAuthenticated = true, username = "testuser")
        assertTrue(content.contains("text-sm"))
        assertTrue(content.contains("text-green-600"))
    }

    @Test
    fun `appearance section is only shown when authenticated`() {
        val auth = renderIndex(isAuthenticated = true, username = "testuser")
        val noAuth = renderIndex()
        assertTrue(auth.contains("Appearance"))
        assertTrue(auth.contains("hx-post=\"/preferences/theme\""))
        assertFalse(noAuth.contains("Appearance"))
        assertFalse(noAuth.contains("hx-post=\"/preferences/theme\""))
    }

    @Test
    fun `HTMX theme selector has correct name attribute`() {
        val content = renderIndex(isAuthenticated = true, username = "testuser")
        assertTrue(content.contains("name=\"theme\""))
    }

    @Test
    fun `HTMX language selector has correct name attribute`() {
        val content = renderIndex(isAuthenticated = true, username = "testuser")
        assertTrue(content.contains("name=\"lang\""))
    }
}
