package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * End-to-end test for the Library Statistics page.
 * Verifies the full stack: PageHandler → JTE template → rendered HTML.
 */
class LibraryStatsPageTest : IntegrationTestBase() {
    @Test
    fun `stats page requires authentication`() {
        val resp = app(Request(Method.GET, "/stats"))
        // Should redirect to login
        assertTrue(resp.status == Status.FOUND || resp.status == Status.SEE_OTHER || resp.status == Status.UNAUTHORIZED)
    }

    @Test
    fun `stats page returns HTML with statistics for authenticated user`() {
        val token = registerAndGetToken()
        // Create a library and book so stats have data
        val libId = createLibrary(token, "stats-test-lib")
        app(
            Request(Method.POST, "/api/books")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"Stats Test Book","author":"Test Author","libraryId":"$libId"}"""),
        )

        val resp = app(Request(Method.GET, "/stats").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()

        // Page renders with the stats title
        assertTrue(html.contains("Library Statistics") || html.contains("page-title"), "Page should contain stats title")
        // Should contain stat cards with numbers
        assertTrue(html.contains("stat-card"), "Page should contain stat cards")
        assertTrue(html.contains("stat-value"), "Page should contain stat values")
        // Should contain the panels for breakdowns
        assertTrue(html.contains("panel"), "Page should contain breakdown panels")
    }

    @Test
    fun `stats page shows correct book count`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token, "count-lib")
        // Create 3 books
        for (i in 1..3) {
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Book $i","author":"Author $i","libraryId":"$libId"}"""),
            )
        }

        val resp = app(Request(Method.GET, "/stats").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()

        // Should show 3 as total books somewhere in the stat cards
        assertTrue(html.contains(">3<"), "Page should show 3 total books")
    }

    @Test
    fun `stats page shows author in top authors panel`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token, "author-lib")
        // Create multiple books by the same author
        for (i in 1..3) {
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Book $i","author":"Repeated Author","libraryId":"$libId"}"""),
            )
        }

        val resp = app(Request(Method.GET, "/stats").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()

        assertTrue(html.contains("Repeated Author"), "Page should show repeated author in top authors")
    }

    @Test
    fun `stats page is accessible from sidebar navigation`() {
        val token = registerAndGetToken()
        // Check the main page has a link to /stats
        val resp = app(Request(Method.GET, "/").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()

        assertTrue(html.contains("href=\"/stats\""), "Sidebar should contain link to /stats")
    }

    @Test
    fun `stats page handles empty library gracefully`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/stats").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()

        // Should not crash — should show 0 or empty state
        assertTrue(html.contains("stat-card") || html.contains("unavailable"), "Page should render even with no data")
    }
}
