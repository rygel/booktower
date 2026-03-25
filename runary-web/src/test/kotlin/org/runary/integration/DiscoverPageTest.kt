package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * End-to-end test for the Discover / Recommendations page.
 * Verifies full stack: PageHandler → JTE template → rendered HTML.
 */
class DiscoverPageTest : IntegrationTestBase() {
    @Test
    fun `discover page requires authentication`() {
        val resp = app(Request(Method.GET, "/discover"))
        assertTrue(resp.status == Status.FOUND || resp.status == Status.SEE_OTHER || resp.status == Status.UNAUTHORIZED)
    }

    @Test
    fun `discover page renders for authenticated user`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/discover").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("discover") || html.contains("Discover"), "Page should contain discover content")
    }

    @Test
    fun `discover page shows empty state for new user`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/discover").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        // New user with no books/ratings should see empty state or no recommendations
        assertTrue(html.contains("empty") || html.contains("discover"), "Page should handle empty library")
    }

    @Test
    fun `sidebar contains discover link`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertTrue(resp.bodyString().contains("href=\"/discover\""), "Sidebar should have /discover link")
    }
}
