package org.booktower.browser

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Browser tests for the authentication flows.
 * Verifies that login/register pages render correctly and that
 * form interactions work end-to-end in a real browser.
 */
@Tag("browser")
class AuthBrowserTest : BrowserTestBase() {

    @Test
    fun `login page renders without errors`() {
        val page = newPage()
        val errors = mutableListOf<String>()
        page.onConsoleMessage { if (it.type() == "error") errors.add(it.text()) }

        page.navigate("$baseUrl/auth/login")

        assertNotNull(page.querySelector("form"), "Login form must be present")
        assertNotNull(page.querySelector("input[name=usernameOrEmail], input[type=text]"), "Username field must exist")
        assertNotNull(page.querySelector("input[type=password]"), "Password field must exist")
        assertNotNull(page.querySelector("button[type=submit], input[type=submit]"), "Submit button must exist")

        val critical = errors.filter { !it.contains("favicon") && !it.contains("icon-") }
        assertTrue(critical.isEmpty(), "Login page must have no JS errors: $critical")
        page.close()
    }

    @Test
    fun `register page renders without errors`() {
        val page = newPage()
        val errors = mutableListOf<String>()
        page.onConsoleMessage { if (it.type() == "error") errors.add(it.text()) }

        page.navigate("$baseUrl/auth/register")

        assertNotNull(page.querySelector("form"), "Register form must be present")
        assertNotNull(page.querySelector("input[name=username]"), "Username field must exist")
        assertNotNull(page.querySelector("input[name=email], input[type=email]"), "Email field must exist")
        assertNotNull(page.querySelector("input[type=password]"), "Password field must exist")

        val critical = errors.filter { !it.contains("favicon") && !it.contains("icon-") }
        assertTrue(critical.isEmpty(), "Register page must have no JS errors: $critical")
        page.close()
    }

    @Test
    fun `login with valid credentials reaches home page`() {
        // Register a user via the API, then log in through the real browser form
        val username = "bauth_${System.nanoTime()}"
        val password = "password123"
        app(
            org.http4k.core.Request(org.http4k.core.Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$username@test.com","password":"$password"}"""),
        )

        val page = newPage()
        page.navigate("$baseUrl/auth/login")
        page.fill("input[name=usernameOrEmail], input[type=text]", username)
        page.fill("input[type=password]", password)
        page.click("button[type=submit], input[type=submit]")

        // After successful login the app redirects to the home/dashboard page
        page.waitForTimeout(1500.0)
        val url = page.url()
        assertTrue(
            !url.contains("/login") && !url.contains("/auth"),
            "Should be redirected away from login page after success, but was at: $url",
        )
        page.close()
    }

    @Test
    fun `login with wrong password stays on login page`() {
        val username = "bauth2_${System.nanoTime()}"
        app(
            org.http4k.core.Request(org.http4k.core.Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$username@test.com","password":"correct123"}"""),
        )

        val page = newPage()
        page.navigate("$baseUrl/auth/login")
        page.fill("input[name=usernameOrEmail], input[type=text]", username)
        page.fill("input[type=password]", "wrongpassword")
        page.click("button[type=submit], input[type=submit]")

        page.waitForTimeout(1000.0)
        // Should stay on login or show an error — not redirect to dashboard
        val url = page.url()
        val body = page.content()
        assertTrue(
            url.contains("/login") || url.contains("/auth") ||
                body.contains("Invalid") || body.contains("incorrect") || body.contains("error"),
            "Should show error or stay on login page after wrong password",
        )
        page.close()
    }

    @Test
    fun `unauthenticated access to home redirects to login`() {
        val page = newPage()
        page.navigate("$baseUrl/")
        page.waitForTimeout(500.0)

        val url = page.url()
        assertTrue(
            url.contains("/login") || url.contains("/auth"),
            "Unauthenticated home visit should redirect to login, but was at: $url",
        )
        page.close()
    }

    @Test
    fun `authenticated user sees navigation with their username`() {
        val (page, _) = newAuthenticatedPage("authui")
        page.navigate("$baseUrl/")
        page.waitForTimeout(500.0)

        val body = page.content()
        // The layout renders the username in the nav
        assertTrue(
            body.contains("authui"),
            "Logged-in user's username prefix should appear in the nav",
        )
        page.close()
    }
}
