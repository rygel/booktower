package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end tests for features added in March 2026:
 *  1. Dashboard "In Progress" stat backed by countByStatus(READING)
 *  2. Book metadata edit: isbn, publisher, publishedDate, pageCount, series/seriesIndex
 *
 * Each test covers a complete user-facing workflow and verifies the final
 * rendered HTML (not just the JSON API response).
 */
class NewFeaturesE2ETest : IntegrationTestBase() {
    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun setStatus(
        token: String,
        bookId: String,
        status: String,
    ) {
        val resp =
            app(
                Request(Method.POST, "/ui/books/$bookId/status")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("status=$status"),
            )
        assertEquals(Status.OK, resp.status, "setStatus($status) should return 200")
    }

    private fun editBook(
        token: String,
        bookId: String,
        fields: Map<String, String>,
    ): Status {
        val body =
            fields.entries.joinToString("&") { (k, v) ->
                "$k=${java.net.URLEncoder.encode(v, "UTF-8")}"
            }
        return app(
            Request(Method.POST, "/ui/books/$bookId/meta")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(body),
        ).status
    }

    private fun dashboardHtml(token: String) = app(Request(Method.GET, "/").header("Cookie", "token=$token")).bodyString()

    private fun bookHtml(
        token: String,
        bookId: String,
    ): String {
        val resp = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status, "Book detail page should return 200")
        return resp.bodyString()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Feature 1: Dashboard "In Progress" stat — full status lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `dashboard in-progress stat increments to 1 when book set to READING`() {
        val token = registerAndGetToken("e2e1")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Lifecycle Book")

        setStatus(token, bookId, "READING")

        // Dashboard renders correctly and the library page shows a READING badge.
        // (The dashboard's "My Libraries" section shows library cards, not book cards,
        //  so book-status-badge appears on the library page rather than the dashboard.)
        assertEquals(
            Status.OK,
            app(Request(Method.GET, "/").header("Cookie", "token=$token")).status,
            "Dashboard must render without error after setting READING status",
        )

        val libHtml = app(Request(Method.GET, "/libraries/$libId").header("Cookie", "token=$token")).bodyString()
        assertTrue(libHtml.contains("book-status-badge"), "Library page must show READING badge on book card")
        assertTrue(libHtml.contains("ri-book-open-line"), "READING icon must appear in the badge")
    }

    @Test
    fun `dashboard in-progress stat drops to 0 after READING book is marked FINISHED`() {
        val token = registerAndGetToken("e2e2")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Finishing Book")

        setStatus(token, bookId, "READING")
        val libHtmlReading = app(Request(Method.GET, "/libraries/$libId").header("Cookie", "token=$token")).bodyString()
        assertTrue(libHtmlReading.contains("ri-book-open-line"), "READING icon must appear before finishing")

        setStatus(token, bookId, "FINISHED")

        // Library page now shows FINISHED badge (ri-checkbox-circle-line), not READING (ri-book-open-line)
        val libHtmlFinished = app(Request(Method.GET, "/libraries/$libId").header("Cookie", "token=$token")).bodyString()
        assertTrue(libHtmlFinished.contains("ri-checkbox-circle-line"), "FINISHED icon must appear after marking finished")
        assertFalse(libHtmlFinished.contains("ri-book-open-line"), "READING icon must not appear after marking finished")

        // Dashboard still renders cleanly
        assertEquals(
            Status.OK,
            app(Request(Method.GET, "/").header("Cookie", "token=$token")).status,
            "Dashboard must render without error after status transition",
        )
    }

    @Test
    fun `dashboard in-progress stat counts only READING books not WANT_TO_READ`() {
        val token = registerAndGetToken("e2e3")
        val libId = createLibrary(token)
        val book1 = createBook(token, libId, "Wishlist Book")
        val book2 = createBook(token, libId, "Current Book")

        setStatus(token, book1, "WANT_TO_READ")
        setStatus(token, book2, "READING")

        // Library page shows both badges
        val libHtml = app(Request(Method.GET, "/libraries/$libId").header("Cookie", "token=$token")).bodyString()
        assertTrue(libHtml.contains("ri-book-open-line"), "READING badge should appear for book2")
        assertTrue(libHtml.contains("ri-bookmark-line"), "WANT_TO_READ badge should appear for book1")

        // Dashboard renders cleanly with both statuses set
        assertEquals(
            Status.OK,
            app(Request(Method.GET, "/").header("Cookie", "token=$token")).status,
            "Dashboard must render without error when books have mixed statuses",
        )
    }

    @Test
    fun `dashboard in-progress stat is not affected by reading progress without a status`() {
        val token = registerAndGetToken("e2e4")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Progress Only")

        // Record progress but never set status
        app(
            Request(Method.POST, "/ui/books/$bookId/progress")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("currentPage=50"),
        )

        // The book appears in "Continue Reading" (progress-based section) on the dashboard
        val dash = dashboardHtml(token)
        assertTrue(dash.contains("Progress Only"), "Book should appear in Continue Reading section")

        // Library page must show no status badge — progress was recorded but no status set
        val libHtml = app(Request(Method.GET, "/libraries/$libId").header("Cookie", "token=$token")).bodyString()
        assertTrue(libHtml.contains("Progress Only"), "Book must appear on library page")
        assertFalse(
            libHtml.contains("book-status-badge"),
            "No status badge should appear on library page when only progress is recorded",
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Feature 2: Book metadata edit — rendered HTML roundtrip
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `edited pageCount appears in book detail page header`() {
        val token = registerAndGetToken("e2e5")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Page Count Book")

        val status = editBook(token, bookId, mapOf("title" to "Page Count Book", "pageCount" to "512"))
        assertEquals(200, status.code, "Edit should succeed")

        val html = bookHtml(token, bookId)
        // Template renders: <span>... 512 pages</span>
        assertTrue(html.contains("512"), "Page count must appear in book detail header")
    }

    @Test
    fun `edited series appears as clickable link in book detail page`() {
        val token = registerAndGetToken("e2e6")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Series Member")

        editBook(
            token,
            bookId,
            mapOf(
                "title" to "Series Member",
                "series" to "Dune",
                "seriesIndex" to "1",
            ),
        )

        val html = bookHtml(token, bookId)
        // Template renders: <a href="/series/Dune">Dune</a> #1
        assertTrue(html.contains("/series/Dune"), "Series link must point to /series/Dune")
        assertTrue(html.contains("Dune"), "Series name must appear in rendered HTML")
        assertTrue(html.contains("#1"), "Series index 1 must appear as #1")
    }

    @Test
    fun `series set via edit form appears in series browser page`() {
        val token = registerAndGetToken("e2e7")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "The Way of Kings")

        editBook(
            token,
            bookId,
            mapOf(
                "title" to "The Way of Kings",
                "series" to "The Stormlight Archive",
                "seriesIndex" to "1",
            ),
        )

        // Series browser: GET /series lists all series
        val seriesListHtml =
            app(
                Request(Method.GET, "/series").header("Cookie", "token=$token"),
            ).bodyString()
        assertTrue(
            seriesListHtml.contains("The Stormlight Archive"),
            "Series browser must list the newly assigned series",
        )

        // Series page: GET /series/{name} lists books in the series
        val seriesName =
            java.net.URLEncoder
                .encode("The Stormlight Archive", "UTF-8")
                .replace("+", "%20")
        val seriesPageHtml =
            app(
                Request(Method.GET, "/series/$seriesName").header("Cookie", "token=$token"),
            ).bodyString()
        assertTrue(
            seriesPageHtml.contains("The Way of Kings"),
            "Series page must show the book assigned to that series",
        )
    }

    @Test
    fun `series is removed from series browser after being cleared in edit form`() {
        val token = registerAndGetToken("e2e8")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Orphaned Book")

        editBook(token, bookId, mapOf("title" to "Orphaned Book", "series" to "Temp Series"))

        val beforeHtml = app(Request(Method.GET, "/series").header("Cookie", "token=$token")).bodyString()
        assertTrue(beforeHtml.contains("Temp Series"), "Series should appear before clearing")

        // Clear series by omitting it from the edit form
        editBook(token, bookId, mapOf("title" to "Orphaned Book"))

        val afterHtml = app(Request(Method.GET, "/series").header("Cookie", "token=$token")).bodyString()
        assertFalse(
            afterHtml.contains("Temp Series"),
            "Series browser must not show series after all books have it cleared",
        )
    }

    @Test
    fun `edited isbn appears in book detail page header and pre-fills the edit form`() {
        val token = registerAndGetToken("e2e9")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "ISBN Book")

        editBook(token, bookId, mapOf("title" to "ISBN Book", "isbn" to "9780743273565"))

        val html = bookHtml(token, bookId)
        // ISBN appears in the read-only metadata row (ri-barcode-line icon) and in the edit form input
        assertTrue(
            html.contains("9780743273565"),
            "Saved ISBN must appear in the book detail page (metadata row + edit form)",
        )
        assertTrue(
            html.contains("ri-barcode-line"),
            "ISBN barcode icon must appear in the metadata row when ISBN is set",
        )
    }

    @Test
    fun `edited publisher appears in book detail page header and pre-fills the edit form`() {
        val token = registerAndGetToken("e2e10")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Publisher Book")

        editBook(token, bookId, mapOf("title" to "Publisher Book", "publisher" to "Tor Books"))

        val html = bookHtml(token, bookId)
        assertTrue(
            html.contains("Tor Books"),
            "Saved publisher must appear in the book detail page (metadata row + edit form)",
        )
        assertTrue(
            html.contains("ri-building-line"),
            "Publisher building icon must appear in the metadata row when publisher is set",
        )
    }

    @Test
    fun `edited publishedDate appears in book detail page header and pre-fills the edit form`() {
        val token = registerAndGetToken("e2e11")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Dated Book")

        editBook(token, bookId, mapOf("title" to "Dated Book", "publishedDate" to "1997-06-26"))

        val html = bookHtml(token, bookId)
        assertTrue(
            html.contains("1997-06-26"),
            "Saved publishedDate must appear in the book detail page (metadata row + edit form)",
        )
        assertTrue(
            html.contains("ri-calendar-event-line"),
            "Published date calendar icon must appear in the metadata row when date is set",
        )
    }

    @Test
    fun `all metadata fields survive a second edit that only changes the title`() {
        val token = registerAndGetToken("e2e12")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Multi Edit Book")

        // First edit — set all fields
        editBook(
            token,
            bookId,
            mapOf(
                "title" to "Multi Edit Book",
                "author" to "Author One",
                "isbn" to "9780743273565",
                "publisher" to "Scribner",
                "publishedDate" to "2000-09-11",
                "pageCount" to "541",
                "series" to "Summer Series",
                "seriesIndex" to "3",
            ),
        )

        // Second edit — only change title; other fields must be sent to avoid being cleared
        // This simulates the real browser behaviour: form sends all field values each time
        editBook(
            token,
            bookId,
            mapOf(
                "title" to "Multi Edit Book Revised",
                "author" to "Author One",
                "isbn" to "9780743273565",
                "publisher" to "Scribner",
                "publishedDate" to "2000-09-11",
                "pageCount" to "541",
                "series" to "Summer Series",
                "seriesIndex" to "3",
            ),
        )

        val html = bookHtml(token, bookId)
        assertTrue(html.contains("Multi Edit Book Revised"), "Updated title must appear")
        assertTrue(html.contains("541"), "Page count must persist across edits")
        assertTrue(html.contains("9780743273565"), "ISBN must persist across edits")
        assertTrue(html.contains("Summer Series"), "Series must persist across edits")
    }

    @Test
    fun `pageCount set via edit form matches what the book card shows on library page`() {
        val token = registerAndGetToken("e2e13")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Card Pages Book")

        editBook(token, bookId, mapOf("title" to "Card Pages Book", "pageCount" to "288"))

        // pageCount also feeds into the reading-progress percentage calculation on book cards.
        // Record progress so the card shows a progress bar.
        app(
            Request(Method.POST, "/ui/books/$bookId/progress")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("currentPage=144"),
        )

        // Library page should show the progress bar (144/288 = 50%)
        val libHtml =
            app(
                Request(Method.GET, "/libraries/$libId").header("Cookie", "token=$token"),
            ).bodyString()
        assertTrue(libHtml.contains("50"), "Book card should show ~50% progress after 144/288 pages")
    }

    @Test
    fun `series fix — series set via form now actually appears in rendered header (regression)`() {
        // Prior to the fix, the edit handler parsed title/author/description
        // but silently ignored series and seriesIndex form values.
        // This test acts as a regression guard.
        val token = registerAndGetToken("e2e14")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Regression Book")

        editBook(
            token,
            bookId,
            mapOf(
                "title" to "Regression Book",
                "series" to "Fix Series",
                "seriesIndex" to "2",
            ),
        )

        val html = bookHtml(token, bookId)
        assertTrue(
            html.contains("Fix Series"),
            "Series must appear in rendered book detail after the handler parse fix",
        )
        assertTrue(html.contains("#2"), "Series index must appear in rendered book detail")
    }
}
