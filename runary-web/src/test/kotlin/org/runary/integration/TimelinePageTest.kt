package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * End-to-end test for the Reading Timeline page.
 * Verifies full stack: PageHandler → JTE template → rendered HTML.
 */
class TimelinePageTest : IntegrationTestBase() {
    @Test
    fun `timeline page requires authentication`() {
        val resp = app(Request(Method.GET, "/timeline"))
        assertTrue(resp.status == Status.FOUND || resp.status == Status.SEE_OTHER || resp.status == Status.UNAUTHORIZED)
    }

    @Test
    fun `timeline page renders empty state for new user`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/timeline").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("timeline") || html.contains("Timeline"), "Page should contain timeline content")
    }

    @Test
    fun `timeline page shows added book`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token, "timeline-lib")
        app(
            Request(Method.POST, "/api/books")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"Timeline Test Book","author":"Timeline Author","libraryId":"$libId"}"""),
        )

        val resp = app(Request(Method.GET, "/timeline").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("Timeline Test Book"), "Timeline should show added book title")
        assertTrue(html.contains("Timeline Author"), "Timeline should show book author")
    }

    @Test
    fun `timeline page has correct CSS classes`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token, "css-lib")
        app(
            Request(Method.POST, "/api/books")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"CSS Book","libraryId":"$libId"}"""),
        )

        val resp = app(Request(Method.GET, "/timeline").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("timeline-item"), "Page should have timeline-item class")
        assertTrue(html.contains("timeline-content"), "Page should have timeline-content class")
        assertTrue(html.contains("timeline-book"), "Page should have timeline-book class")
    }

    @Test
    fun `sidebar contains timeline link`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertTrue(resp.bodyString().contains("href=\"/timeline\""), "Sidebar should have /timeline link")
    }
}
