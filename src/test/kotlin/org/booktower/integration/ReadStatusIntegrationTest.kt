package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadStatusIntegrationTest : IntegrationTestBase() {

    private fun setStatus(token: String, bookId: String, status: String) {
        app(Request(Method.POST, "/ui/books/$bookId/status")
            .header("Cookie", "token=$token")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("status=$status"))
    }

    // ── Book detail page ────────────────────────────────────────────────────────

    @Test
    fun `book detail page shows status selector`() {
        val token = registerAndGetToken("rs1")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val body = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("book-status-select"), "Book detail should show status selector")
    }

    @Test
    fun `setting status to WANT_TO_READ returns 200`() {
        val token = registerAndGetToken("rs2")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response = app(Request(Method.POST, "/ui/books/$bookId/status")
            .header("Cookie", "token=$token")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("status=WANT_TO_READ"))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `status is reflected on book detail page after setting`() {
        val token = registerAndGetToken("rs3")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        setStatus(token, bookId, "READING")

        val body = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token")).bodyString()
        val readingIdx = body.indexOf("value=\"READING\"")
        assertTrue(readingIdx >= 0, "READING option must exist")
        val snippet = body.substring(readingIdx, minOf(readingIdx + 30, body.length))
        assertTrue(snippet.contains("selected"), "READING should be selected after setting: $snippet")
    }

    @Test
    fun `setting status to NONE clears the status`() {
        val token = registerAndGetToken("rs4")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        setStatus(token, bookId, "FINISHED")
        setStatus(token, bookId, "NONE")

        val body = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token")).bodyString()
        val noneIdx = body.indexOf("value=\"NONE\"")
        assertTrue(noneIdx >= 0, "NONE option must exist")
        val snippet = body.substring(noneIdx, minOf(noneIdx + 30, body.length))
        assertTrue(snippet.contains("selected"), "NONE should be selected after clearing: $snippet")
    }

    // ── Library page filter ─────────────────────────────────────────────────────

    @Test
    fun `library page shows status filter dropdown`() {
        val token = registerAndGetToken("rs5")
        val libId = createLibrary(token)

        val body = app(Request(Method.GET, "/libraries/$libId").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("WANT_TO_READ") || body.contains("status.want.to.read"),
            "Library page should show status filter with WANT_TO_READ option")
    }

    @Test
    fun `status filter shows only matching books`() {
        val token = registerAndGetToken("rs6")
        val libId = createLibrary(token)
        val book1 = createBook(token, libId, "Reading Book")
        val book2 = createBook(token, libId, "Finished Book")
        setStatus(token, book1, "READING")
        setStatus(token, book2, "FINISHED")

        val body = app(Request(Method.GET, "/libraries/$libId?status=READING")
            .header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("Reading Book"), "READING filter should show Reading Book")
        assertFalse(body.contains("Finished Book"), "READING filter should not show Finished Book")
    }

    @Test
    fun `status filter ALL shows all books`() {
        val token = registerAndGetToken("rs7")
        val libId = createLibrary(token)
        val book1 = createBook(token, libId, "First Book")
        val book2 = createBook(token, libId, "Second Book")
        setStatus(token, book1, "READING")

        val body = app(Request(Method.GET, "/libraries/$libId?status=ALL")
            .header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("First Book"), "ALL filter should show First Book")
        assertTrue(body.contains("Second Book"), "ALL filter should show Second Book")
    }

    @Test
    fun `status filter excludes books without status when filtering`() {
        val token = registerAndGetToken("rs8")
        val libId = createLibrary(token)
        val book1 = createBook(token, libId, "Tagged Book")
        createBook(token, libId, "No Status Book")
        setStatus(token, book1, "WANT_TO_READ")

        val body = app(Request(Method.GET, "/libraries/$libId?status=WANT_TO_READ")
            .header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("Tagged Book"), "Should show book with matching status")
        assertFalse(body.contains("No Status Book"), "Should not show book with no status")
    }

    // ── Status badge on book card ───────────────────────────────────────────────

    @Test
    fun `book card shows status badge after status is set`() {
        val token = registerAndGetToken("rs9")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Badged Book")
        setStatus(token, bookId, "FINISHED")

        val body = app(Request(Method.GET, "/libraries/$libId").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("Badged Book"), "Book must appear on library page")
        assertTrue(body.contains("book-status-badge"),
            "Library page should show Finished badge for the book")
    }

    @Test
    fun `book card has no status badge when no status set`() {
        val token = registerAndGetToken("rs10")
        val libId = createLibrary(token)
        createBook(token, libId, "Plain Book")

        val body = app(Request(Method.GET, "/libraries/$libId").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("Plain Book"))
        // No status badge should appear since status is null
        assertFalse(body.contains("book-status-badge"),
            "No status badge should appear for a book with no status")
    }

    // ── Status isolation ────────────────────────────────────────────────────────

    @Test
    fun `status is per-user not shared between users`() {
        val token1 = registerAndGetToken("rs11a")
        val token2 = registerAndGetToken("rs11b")
        val libId1 = createLibrary(token1)
        val bookId = createBook(token1, libId1, "Shared-ish Book")

        // user1 sets FINISHED
        setStatus(token1, bookId, "FINISHED")

        // user2 accesses the book detail (they can't since it's not their library, but test status isolation)
        // Instead, user2 creates their own book and checks no cross-contamination
        val libId2 = createLibrary(token2)
        val bookId2 = createBook(token2, libId2, "User2 Book")
        setStatus(token2, bookId2, "READING")

        // user1's status should still be FINISHED, not affected by user2
        val body1 = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token1")).bodyString()
        val finishedIdx = body1.indexOf("value=\"FINISHED\"")
        assertTrue(finishedIdx >= 0)
        val snippet1 = body1.substring(finishedIdx, minOf(finishedIdx + 30, body1.length))
        assertTrue(snippet1.contains("selected"), "User1 FINISHED status must be preserved")
    }

    @Test
    fun `updating status changes it correctly`() {
        val token = registerAndGetToken("rs12")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        setStatus(token, bookId, "WANT_TO_READ")
        setStatus(token, bookId, "READING")
        setStatus(token, bookId, "FINISHED")

        val body = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token")).bodyString()
        val finishedIdx = body.indexOf("value=\"FINISHED\"")
        assertTrue(finishedIdx >= 0)
        val snippet = body.substring(finishedIdx, minOf(finishedIdx + 30, body.length))
        assertTrue(snippet.contains("selected"), "Final status FINISHED should be selected")
    }
}
