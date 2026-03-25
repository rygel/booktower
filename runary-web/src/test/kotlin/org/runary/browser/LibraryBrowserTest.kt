package org.runary.browser

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Browser tests for library management and navigation flows.
 * Covers creating libraries, adding books, and navigating between pages.
 */
@Tag("browser")
class LibraryBrowserTest : BrowserTestBase() {
    @Test
    fun `home page loads without JS errors`() {
        val (page, _) = newAuthenticatedPage("libhome")
        val errors = mutableListOf<String>()
        page.onConsoleMessage { if (it.type() == "error") errors.add(it.text()) }

        page.navigate("$baseUrl/")
        page.waitForTimeout(500.0)

        val critical = errors.filter { !it.contains("favicon") && !it.contains("icon-") }
        assertTrue(critical.isEmpty(), "Home page must have no JS errors: $critical")
        page.close()
    }

    @Test
    fun `home page shows empty state when no libraries exist`() {
        val (page, _) = newAuthenticatedPage("libempty")
        page.navigate("$baseUrl/")
        page.waitForTimeout(500.0)

        val body = page.content()
        assertTrue(
            body.contains("No libraries") || body.contains("New Library") || body.contains("library"),
            "Home page should show empty state or new library prompt",
        )
        page.close()
    }

    @Test
    fun `library page loads and shows book list`() {
        val (page, token) = newAuthenticatedPage("libpage")
        val libId = createLibrary(token, "Browser Test Library")
        createBook(token, libId, "Book Alpha")
        createBook(token, libId, "Book Beta")

        page.navigate("$baseUrl/libraries/$libId")
        page.waitForTimeout(500.0)

        val body = page.content()
        assertTrue(body.contains("Book Alpha"), "Library page should show added books")
        assertTrue(body.contains("Book Beta"), "Library page should show all added books")
        page.close()
    }

    @Test
    fun `library page has no JS errors`() {
        val (page, token) = newAuthenticatedPage("libjserr")
        val libId = createLibrary(token)
        val errors = mutableListOf<String>()
        page.onConsoleMessage { if (it.type() == "error") errors.add(it.text()) }

        page.navigate("$baseUrl/libraries/$libId")
        page.waitForTimeout(500.0)

        val critical = errors.filter { !it.contains("favicon") && !it.contains("icon-") }
        assertTrue(critical.isEmpty(), "Library page must have no JS errors: $critical")
        page.close()
    }

    @Test
    fun `book detail page loads without JS errors`() {
        val (page, token) = newAuthenticatedPage("bookdetail")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Detail Test Book")
        val errors = mutableListOf<String>()
        page.onConsoleMessage { if (it.type() == "error") errors.add(it.text()) }

        page.navigate("$baseUrl/books/$bookId")
        page.waitForTimeout(500.0)

        val critical = errors.filter { !it.contains("favicon") && !it.contains("icon-") }
        assertTrue(critical.isEmpty(), "Book detail page must have no JS errors: $critical")

        val body = page.content()
        assertTrue(body.contains("Detail Test Book"), "Book detail page should show book title")
        page.close()
    }

    @Test
    fun `search page loads without JS errors`() {
        val (page, _) = newAuthenticatedPage("searchp")
        val errors = mutableListOf<String>()
        page.onConsoleMessage { if (it.type() == "error") errors.add(it.text()) }

        page.navigate("$baseUrl/search")
        page.waitForTimeout(500.0)

        val critical = errors.filter { !it.contains("favicon") && !it.contains("icon-") }
        assertTrue(critical.isEmpty(), "Search page must have no JS errors: $critical")

        assertNotNull(page.querySelector("input[type=search], input[name=q]"), "Search input must exist")
        page.close()
    }

    @Test
    fun `htmx is loaded and operational`() {
        val (page, _) = newAuthenticatedPage("htmxchk")
        page.navigate("$baseUrl/")
        page.waitForTimeout(500.0)

        // htmx attaches itself to window.htmx
        val htmxDefined = page.evaluate("() => typeof window.htmx !== 'undefined'") as Boolean
        assertTrue(htmxDefined, "window.htmx must be defined — htmx.min.js failed to load from /static/vendor/")
        page.close()
    }

    @Test
    fun `remixicon font is served from vendor path`() {
        val (page, _) = newAuthenticatedPage("rimicon")
        page.navigate("$baseUrl/")
        page.waitForTimeout(500.0)

        // Check the CSS link points to vendored path
        val cssHref =
            page.evaluate(
                "() => { const l = document.querySelector('link[href*=\"remixicon\"]'); return l ? l.href : '' }",
            ) as String
        assertTrue(
            cssHref.contains("/static/vendor/remixicon"),
            "RemixIcon CSS should be loaded from /static/vendor/, not a CDN. Got: $cssHref",
        )
        page.close()
    }

    @Test
    fun `profile page loads without JS errors`() {
        val (page, _) = newAuthenticatedPage("profp")
        val errors = mutableListOf<String>()
        page.onConsoleMessage { if (it.type() == "error") errors.add(it.text()) }

        page.navigate("$baseUrl/profile")
        page.waitForTimeout(500.0)

        val critical = errors.filter { !it.contains("favicon") && !it.contains("icon-") }
        assertTrue(critical.isEmpty(), "Profile page must have no JS errors: $critical")
        page.close()
    }
}
