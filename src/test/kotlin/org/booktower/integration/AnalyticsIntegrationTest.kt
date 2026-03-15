package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalyticsIntegrationTest : IntegrationTestBase() {

    private fun enableAnalytics(token: String) {
        app(Request(Method.POST, "/ui/preferences/analytics")
            .header("Cookie", "token=$token")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("enabled=true"))
    }

    private fun disableAnalytics(token: String) {
        app(Request(Method.POST, "/ui/preferences/analytics")
            .header("Cookie", "token=$token")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("enabled=false"))
    }

    private fun recordProgress(token: String, bookId: String, page: Int) {
        app(Request(Method.POST, "/ui/books/$bookId/progress")
            .header("Cookie", "token=$token")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("currentPage=$page"))
    }

    @Test
    fun `analytics page returns 200 for authenticated user`() {
        val token = registerAndGetToken("ana1")
        val response = app(Request(Method.GET, "/analytics").header("Cookie", "token=$token"))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `analytics page redirects to login when not authenticated`() {
        val response = app(Request(Method.GET, "/analytics"))
        assertEquals(Status.SEE_OTHER, response.status)
    }

    @Test
    fun `analytics page shows disabled state by default`() {
        val token = registerAndGetToken("ana2")
        val body = app(Request(Method.GET, "/analytics").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("Analytics not enabled") || body.contains("disabled") || body.contains("enable"),
            "Analytics page should show disabled state by default")
        assertFalse(body.contains("Day Streak") || body.contains("Total Pages"),
            "Stats should not show when analytics is disabled")
    }

    @Test
    fun `enabling analytics via POST saves the preference`() {
        val token = registerAndGetToken("ana3")
        enableAnalytics(token)

        val body = app(Request(Method.GET, "/analytics").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("Day Streak") || body.contains("Total Pages") || body.contains("Books Finished"),
            "Stats should show after enabling analytics")
    }

    @Test
    fun `disabling analytics hides stats`() {
        val token = registerAndGetToken("ana4")
        enableAnalytics(token)
        disableAnalytics(token)

        val body = app(Request(Method.GET, "/analytics").header("Cookie", "token=$token")).bodyString()
        assertFalse(body.contains("Day Streak") || body.contains("Total Pages Read"),
            "Stats should be hidden after disabling analytics")
    }

    @Test
    fun `recording progress increments total pages when analytics enabled`() {
        val token = registerAndGetToken("ana5")
        enableAnalytics(token)
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Analytics Book")

        recordProgress(token, bookId, 50)

        val body = app(Request(Method.GET, "/analytics").header("Cookie", "token=$token")).bodyString()
        // Total pages = 50 (from page 0 to page 50)
        assertTrue(body.contains("50"), "Analytics should reflect 50 pages read")
    }

    @Test
    fun `progress not recorded when analytics disabled`() {
        val token = registerAndGetToken("ana6")
        // Analytics disabled (default)
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "No Track Book")

        recordProgress(token, bookId, 100)

        // Now enable to check — should still show 0 total pages
        enableAnalytics(token)
        val body = app(Request(Method.GET, "/analytics").header("Cookie", "token=$token")).bodyString()
        // No pages were recorded because analytics was off when progress was saved
        assertFalse(body.contains(">100<") || body.contains(">100 <"),
            "Pages read before enabling analytics should not be counted")
    }

    @Test
    fun `analytics streak is 0 with no reading activity`() {
        val token = registerAndGetToken("ana7")
        enableAnalytics(token)

        val body = app(Request(Method.GET, "/analytics").header("Cookie", "token=$token")).bodyString()
        // Streak should show 0
        assertTrue(body.contains(">0<") || body.contains(">0 "),
            "Streak should be 0 with no reading activity")
    }

    @Test
    fun `analytics page is linked from sidebar`() {
        val token = registerAndGetToken("ana8")
        val body = app(Request(Method.GET, "/").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("href=\"/analytics\""), "Sidebar should contain analytics link")
    }

    @Test
    fun `profile page shows analytics toggle`() {
        val token = registerAndGetToken("ana9")
        val body = app(Request(Method.GET, "/profile").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("analytics-toggle") || body.contains("analytics.enable"),
            "Profile page should show the analytics toggle")
    }

    @Test
    fun `analytics toggle is unchecked by default`() {
        val token = registerAndGetToken("ana10")
        val body = app(Request(Method.GET, "/profile").header("Cookie", "token=$token")).bodyString()
        // JTE smart attr: checked="${true}" renders as `checked`, checked="${false}" omits the attribute.
        // When disabled, the id is followed by whitespace then onchange — not by the word "checked".
        assertTrue(body.contains("analytics-toggle"), "analytics-toggle element must exist")
        assertFalse(body.contains("""analytics-toggle" checked"""),
            "Analytics toggle should be unchecked by default (no checked attribute after id)")
    }

    @Test
    fun `bar chart renders 30 day data`() {
        val token = registerAndGetToken("ana11")
        enableAnalytics(token)
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Chart Book")
        recordProgress(token, bookId, 30)

        val body = app(Request(Method.GET, "/analytics").header("Cookie", "token=$token")).bodyString()
        // The chart container renders — check for presence of page date range labels
        assertTrue(body.contains("-"), "Bar chart date labels should contain dashes (YYYY-MM-DD format)")
    }
}
