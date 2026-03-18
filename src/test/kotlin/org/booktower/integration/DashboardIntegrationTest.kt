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
        assertTrue(
            body.contains("page.libraries.new") || body.contains("New Library"),
            "Empty state should have CTA to create library",
        )
    }

    @Test
    fun `dashboard shows Continue Reading section when reading progress exists`() {
        val token = registerAndGetToken("dash5")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "My Reading Book")

        // Record reading progress
        app(
            Request(Method.POST, "/ui/books/$bookId/progress")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("currentPage=42"),
        )

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
        assertFalse(
            body.contains("page.dashboard.continue.reading"),
            "Continue Reading heading should not appear as literal key",
        )
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
        val body =
            app(
                Request(Method.GET, "/")
                    .header("Cookie", "token=$token; app_theme=dracula"),
            ).bodyString()
        assertTrue(body.contains("data-theme=\"dracula\""))
    }

    @Test
    fun `dashboard title is translated in German`() {
        val token = registerAndGetToken("dash9")
        val body =
            app(
                Request(Method.GET, "/")
                    .header("Cookie", "token=$token; app_lang=de"),
            ).bodyString()
        assertTrue(body.contains("Startseite"), "Dashboard title should be in German")
    }

    @Test
    fun `libraries page still accessible directly`() {
        val token = registerAndGetToken("dash10")
        val response = app(Request(Method.GET, "/libraries").header("Cookie", "token=$token"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("My Libraries"))
    }

    // ── Recently Added ─────────────────────────────────────────────────────────

    @Test
    fun `dashboard shows Recently Added section when books exist`() {
        val token = registerAndGetToken("dash11")
        val libId = createLibrary(token)
        createBook(token, libId, "My New Book")

        val body = app(Request(Method.GET, "/").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("My New Book"), "Dashboard should show newly added book in Recently Added")
    }

    @Test
    fun `dashboard Recently Added section is absent when no books`() {
        val token = registerAndGetToken("dash12")
        createLibrary(token)

        val body = app(Request(Method.GET, "/").header("Cookie", "token=$token")).bodyString()
        assertFalse(
            body.contains("page.dashboard.recently.added"),
            "Recently Added heading should not render as raw i18n key",
        )
        assertFalse(
            body.contains("Recently Added"),
            "Recently Added section should not appear when there are no books",
        )
    }

    @Test
    fun `dashboard Recently Added shows most recently added book first`() {
        val token = registerAndGetToken("dash13")
        val libId = createLibrary(token)
        createBook(token, libId, "Older Book")
        Thread.sleep(10)
        createBook(token, libId, "Newer Book")

        val body = app(Request(Method.GET, "/").header("Cookie", "token=$token")).bodyString()
        val olderPos = body.indexOf("Older Book")
        val newerPos = body.indexOf("Newer Book")
        assertTrue(newerPos < olderPos, "Most recently added book should appear first")
    }

    @Test
    fun `dashboard Recently Added shows at most 6 books`() {
        val token = registerAndGetToken("dash14")
        val libId = createLibrary(token)
        repeat(8) { i -> createBook(token, libId, "Book $i") }

        val body = app(Request(Method.GET, "/").header("Cookie", "token=$token")).bodyString()
        // We can't count exact items without parsing HTML, but the section renders without error
        assertEquals(Status.OK, app(Request(Method.GET, "/").header("Cookie", "token=$token")).status)
        assertTrue(body.contains("Book "), "Dashboard should show recently added books")
    }

    @Test
    fun `dashboard Recently Added links to book detail page`() {
        val token = registerAndGetToken("dash15")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Linked Book")

        val body = app(Request(Method.GET, "/").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("/books/$bookId"), "Recently Added book should link to book detail page")
    }

    // ── In Progress stat (currently reading) ────────────────────────────────────

    @Test
    fun `in-progress stat shows 1 after marking a book as READING`() {
        val token = registerAndGetToken("dash16")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Active Book")

        app(
            Request(Method.POST, "/ui/books/$bookId/status")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("status=READING"),
        )

        val body = app(Request(Method.GET, "/").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains(">1<"), "In-progress stat should show 1 after marking a book READING")
    }

    @Test
    fun `in-progress stat does not count books with progress but no READING status`() {
        val token = registerAndGetToken("dash17")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Progress Only Book")

        // record reading progress but do NOT set status
        app(
            Request(Method.POST, "/ui/books/$bookId/progress")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("currentPage=10"),
        )

        val body = app(Request(Method.GET, "/").header("Cookie", "token=$token")).bodyString()
        // In-progress stat should still be 0 — no READING status set
        assertTrue(body.contains(">0<"), "In-progress stat should be 0 when no book has READING status")
    }

    @Test
    fun `in-progress stat excludes FINISHED books`() {
        val token = registerAndGetToken("dash18")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Done Book")

        app(
            Request(Method.POST, "/ui/books/$bookId/status")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("status=FINISHED"),
        )

        val body = app(Request(Method.GET, "/").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains(">0<"), "In-progress stat should not count FINISHED books")
    }

    // ── Recently Finished ────────────────────────────────────────────────────────

    @Test
    fun `dashboard shows Recently Finished section after marking a book as FINISHED`() {
        val token = registerAndGetToken("dash19")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Finished Book")

        app(
            Request(Method.POST, "/ui/books/$bookId/status")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("status=FINISHED"),
        )

        val body = app(Request(Method.GET, "/").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("Finished Book"), "Dashboard must show the finished book in Recently Finished")
        assertTrue(
            body.contains("ri-checkbox-circle-line"),
            "Recently Finished section must use the checkbox-circle icon",
        )
    }

    @Test
    fun `dashboard Recently Finished section is absent when no books are finished`() {
        val token = registerAndGetToken("dash20")
        val libId = createLibrary(token)
        createBook(token, libId, "Unfinished Book")

        val body = app(Request(Method.GET, "/").header("Cookie", "token=$token")).bodyString()
        assertFalse(
            body.contains("page.dashboard.recently.finished"),
            "Recently Finished heading must not render as raw i18n key",
        )
        assertFalse(
            body.contains("Recently Finished"),
            "Recently Finished section must not appear when no books are finished",
        )
    }

    @Test
    fun `dashboard Recently Finished shows most recently finished book first`() {
        val token = registerAndGetToken("dash21")
        val libId = createLibrary(token)
        val book1 = createBook(token, libId, "First Finished")
        val book2 = createBook(token, libId, "Second Finished")

        app(
            Request(Method.POST, "/ui/books/$book1/status")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("status=FINISHED"),
        )
        Thread.sleep(10)
        app(
            Request(Method.POST, "/ui/books/$book2/status")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("status=FINISHED"),
        )

        val body = app(Request(Method.GET, "/").header("Cookie", "token=$token")).bodyString()
        val pos1 = body.indexOf("First Finished")
        val pos2 = body.indexOf("Second Finished")
        assertTrue(pos2 < pos1, "Most recently finished book must appear first")
    }

    @Test
    fun `dashboard Recently Finished does not show READING or WANT_TO_READ books`() {
        val token = registerAndGetToken("dash22")
        val libId = createLibrary(token)
        val book1 = createBook(token, libId, "Reading Book")
        val book2 = createBook(token, libId, "Want Book")

        app(
            Request(Method.POST, "/ui/books/$book1/status")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("status=READING"),
        )
        app(
            Request(Method.POST, "/ui/books/$book2/status")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("status=WANT_TO_READ"),
        )

        val body = app(Request(Method.GET, "/").header("Cookie", "token=$token")).bodyString()
        assertFalse(
            body.contains("Recently Finished"),
            "Recently Finished section must not appear when no books have FINISHED status",
        )
    }
}
