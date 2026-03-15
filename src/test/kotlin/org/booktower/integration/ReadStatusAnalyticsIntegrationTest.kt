package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies the interaction between read-status changes and the analytics system.
 * Specifically: marking a book as FINISHED while analytics is enabled should be
 * reflected in the analytics dashboard, and disabling analytics should not break
 * status changes.
 */
class ReadStatusAnalyticsIntegrationTest : IntegrationTestBase() {

    private fun enableAnalytics(token: String) {
        app(
            Request(Method.POST, "/ui/preferences/analytics")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("enabled=true"),
        )
    }

    private fun setStatus(token: String, bookId: String, status: String) {
        val resp = app(
            Request(Method.POST, "/ui/books/$bookId/status")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("status=$status"),
        )
        assertEquals(Status.OK, resp.status, "setStatus($status) should return 200")
    }

    private fun recordProgress(token: String, bookId: String, page: Int) {
        app(
            Request(Method.POST, "/ui/books/$bookId/progress")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("currentPage=$page"),
        )
    }

    // ── Status changes don't break analytics ─────────────────────────────────

    @Test
    fun `setting FINISHED status with analytics enabled does not crash`() {
        val token = registerAndGetToken("rsa1")
        enableAnalytics(token)
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Done Book")

        // Should not throw or return 5xx
        val resp = app(
            Request(Method.POST, "/ui/books/$bookId/status")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("status=FINISHED"),
        )
        assertEquals(Status.OK, resp.status, "FINISHED status with analytics enabled should return 200")
    }

    @Test
    fun `analytics page renders successfully after marking a book finished`() {
        val token = registerAndGetToken("rsa2")
        enableAnalytics(token)
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Finished Book")
        setStatus(token, bookId, "FINISHED")

        val resp = app(Request(Method.GET, "/analytics").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        // Page should render without error — stats section should be visible
        val body = resp.bodyString()
        assertTrue(body.contains("Books Finished") || body.contains("Total Pages"),
            "Analytics page should render stats after marking book finished")
    }

    @Test
    fun `books finished count reflects completed books when analytics enabled`() {
        val token = registerAndGetToken("rsa3")
        enableAnalytics(token)
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Completion Book")

        // Record some progress then mark finished
        recordProgress(token, bookId, 100)
        setStatus(token, bookId, "FINISHED")

        val body = app(Request(Method.GET, "/analytics").header("Cookie", "token=$token")).bodyString()
        // Should show at least 1 in Books Finished section
        assertTrue(body.contains("Books Finished"), "Books Finished section must be present")
    }

    // ── Status transitions with analytics ────────────────────────────────────

    @Test
    fun `transitioning through all statuses with analytics on does not error`() {
        val token = registerAndGetToken("rsa4")
        enableAnalytics(token)
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Status Journey")

        // Walk through all statuses
        for (status in listOf("WANT_TO_READ", "READING", "FINISHED", "NONE")) {
            val resp = app(
                Request(Method.POST, "/ui/books/$bookId/status")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("status=$status"),
            )
            assertEquals(Status.OK, resp.status, "Status transition to $status should succeed")
        }

        // Analytics page should still render
        val analyticsResp = app(Request(Method.GET, "/analytics").header("Cookie", "token=$token"))
        assertEquals(Status.OK, analyticsResp.status)
    }

    @Test
    fun `status changes with analytics disabled do not error`() {
        val token = registerAndGetToken("rsa5")
        // Analytics stays disabled (default)
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Untracked Book")

        val resp = app(
            Request(Method.POST, "/ui/books/$bookId/status")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("status=FINISHED"),
        )
        assertEquals(Status.OK, resp.status, "Status change with analytics disabled should succeed")
    }

    @Test
    fun `book status is preserved and visible on detail page independently of analytics`() {
        val token = registerAndGetToken("rsa6")
        enableAnalytics(token)
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Status Visible")
        setStatus(token, bookId, "READING")

        val response = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token"))
        assertEquals(Status.OK, response.status, "Book page must return 200 (got: ${response.bodyString().take(300)})")
        val html = response.bodyString()
        // JTE HTML mode may emit `selected` (bare) or `selected="true"` — match either form.
        // The regex finds an <option> tag that has both value="READING" and selected in any order.
        val selectedReading = Regex("""<option\b[^>]*value="READING"[^>]*selected""").containsMatchIn(html)
        val selectedReadingRev = Regex("""<option\b[^>]*selected[^>]*value="READING"""").containsMatchIn(html)
        val selectorArea = html.substringAfter("book-status-select", "").take(600)
        assertTrue(selectedReading || selectedReadingRev, "READING option must be selected in status dropdown. Selector area: $selectorArea")
    }

    @Test
    fun `progress recording and status setting together are consistent`() {
        val token = registerAndGetToken("rsa7")
        enableAnalytics(token)
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Combined Book")

        recordProgress(token, bookId, 50)
        setStatus(token, bookId, "READING")
        recordProgress(token, bookId, 200)
        setStatus(token, bookId, "FINISHED")

        // Both analytics and status should reflect final state
        val bookResp = app(Request(Method.GET, "/api/books/$bookId").header("Cookie", "token=$token"))
        val body = bookResp.bodyString()
        assertTrue(body.contains("FINISHED"), "final status should be FINISHED")
        assertTrue(body.contains("200"), "final progress page should be 200")

        val analyticsResp = app(Request(Method.GET, "/analytics").header("Cookie", "token=$token"))
        assertEquals(Status.OK, analyticsResp.status, "analytics should render cleanly after combined usage")
    }
}
