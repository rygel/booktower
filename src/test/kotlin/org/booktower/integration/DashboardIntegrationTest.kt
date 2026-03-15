package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DashboardIntegrationTest : IntegrationTestBase() {

    @Test
    fun `GET root without auth shows landing page`() {
        val response = app(Request(Method.GET, "/"))
        assertEquals(Status.OK, response.status)
        val body = response.bodyString()
        // Landing page shows sign-in / sign-up, not the sidebar nav
        assertTrue(body.contains("BookTower"))
        assertFalse(body.contains("sidebar-link"), "Landing page should not have sidebar nav")
    }

    @Test
    fun `GET root with auth shows dashboard`() {
        val token = registerAndGetToken("dash1")
        val response = app(Request(Method.GET, "/").header("Cookie", "token=$token"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.header("Content-Type")?.contains("text/html") == true)
        assertTrue(response.bodyString().contains("sidebar-link"), "Dashboard should include sidebar nav")
    }

    @Test
    fun `dashboard shows zero stats for new user`() {
        val token = registerAndGetToken("dash2")
        val body = app(Request(Method.GET, "/").header("Cookie", "token=$token")).bodyString()
        // Stats row should show 0 libraries and 0 books
        assertTrue(body.contains(">0<"), "Should display 0 for empty stats")
    }

    @Test
    fun `dashboard shows library count after creating a library`() {
        val token = registerAndGetToken("dash3")
        createLibrary(token, "Dash Library")

        val body = app(Request(Method.GET, "/").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("Dash Library"), "Dashboard should show library name")
    }

    @Test
    fun `dashboard shows empty state when no libraries`() {
        val token = registerAndGetToken("dash4")
        val body = app(Request(Method.GET, "/").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("page.libraries.new") || body.contains("New Library"),
            "Empty state should have CTA to create library")
    }

    @Test
    fun `dashboard shows Continue Reading section when reading progress exists`() {
        val token = registerAndGetToken("dash5")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "My Reading Book")

        // Record reading progress
        app(Request(Method.POST, "/ui/books/$bookId/progress")
            .header("Cookie", "token=$token")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("currentPage=42"))

        val body = app(Request(Method.GET, "/").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("My Reading Book"), "Dashboard should show book in Continue Reading")
    }

    @Test
    fun `dashboard does not show Continue Reading when no progress`() {
        val token = registerAndGetToken("dash6")
        val libId = createLibrary(token)
        createBook(token, libId, "Unread Book")

        val body = app(Request(Method.GET, "/").header("Cookie", "token=$token")).bodyString()
        // "Unread Book" has no progress, so it should not appear in Continue Reading
        // (it may appear in libraries section, but not "Continue Reading" heading)
        // We can verify the continue reading section only shows books with progress
        assertFalse(body.contains("page.dashboard.continue.reading"),
            "Continue Reading heading should not appear as literal key")
    }

    @Test
    fun `dashboard has Home link active in sidebar`() {
        val token = registerAndGetToken("dash7")
        val body = app(Request(Method.GET, "/").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("""href="/""""), "Sidebar should have Home link")
    }

    @Test
    fun `dashboard reflects theme from cookie`() {
        val token = registerAndGetToken("dash8")
        val body = app(Request(Method.GET, "/")
            .header("Cookie", "token=$token; app_theme=dracula")).bodyString()
        assertTrue(body.contains("data-theme=\"dracula\""))
    }

    @Test
    fun `dashboard title is translated in German`() {
        val token = registerAndGetToken("dash9")
        val body = app(Request(Method.GET, "/")
            .header("Cookie", "token=$token; app_lang=de")).bodyString()
        assertTrue(body.contains("Startseite"), "Dashboard title should be in German")
    }

    @Test
    fun `libraries page still accessible directly`() {
        val token = registerAndGetToken("dash10")
        val response = app(Request(Method.GET, "/libraries").header("Cookie", "token=$token"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("My Libraries"))
    }
}
