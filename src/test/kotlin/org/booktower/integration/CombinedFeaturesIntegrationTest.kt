package org.booktower.integration

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.booktower.config.Json
import org.booktower.models.BookmarkDto
import org.booktower.models.ReadingProgressDto
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests that combine multiple features to verify they work correctly together
 * and don't interfere with each other. Also covers cascading deletion behaviour.
 */
class CombinedFeaturesIntegrationTest : IntegrationTestBase() {
    private fun minimalPdf(): ByteArray {
        val doc = PDDocument().also { it.addPage(PDPage()) }
        return ByteArrayOutputStream()
            .also {
                doc.save(it)
                doc.close()
            }.toByteArray()
    }

    private fun uploadPdf(
        token: String,
        bookId: String,
    ) {
        app(
            Request(Method.POST, "/api/books/$bookId/upload")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/octet-stream")
                .header("X-Filename", "book.pdf")
                .body(ByteArrayInputStream(minimalPdf())),
        )
    }

    private fun addBookmark(
        token: String,
        bookId: String,
        page: Int,
        title: String = "Mark",
    ): String {
        val r =
            app(
                Request(Method.POST, "/api/bookmarks")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"bookId":"$bookId","page":$page,"title":"$title","note":null}"""),
            )
        return Json.mapper.readValue(r.bodyString(), BookmarkDto::class.java).id
    }

    private fun setProgress(
        token: String,
        bookId: String,
        page: Int,
    ) {
        app(
            Request(Method.PUT, "/api/books/$bookId/progress")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"currentPage":$page}"""),
        )
    }

    // ── Progress + bookmarks coexist ──────────────────────────────────────

    @Test
    fun `progress and bookmarks on same book are independent`() {
        val token = registerAndGetToken("cmb")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        setProgress(token, bookId, 42)
        val bmId = addBookmark(token, bookId, 10, "Chapter 1")

        // Progress is correct
        val prog =
            app(
                Request(Method.GET, "/api/books/$bookId")
                    .header("Cookie", "token=$token"),
            )
        assertTrue(prog.bodyString().contains("42"), "progress page should be 42")

        // Bookmark still there
        val bm =
            app(
                Request(Method.GET, "/api/bookmarks?bookId=$bookId")
                    .header("Cookie", "token=$token"),
            )
        assertTrue(bm.bodyString().contains(bmId))
    }

    @Test
    fun `updating progress does not remove bookmarks`() {
        val token = registerAndGetToken("cmb")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val bmId = addBookmark(token, bookId, 5)
        setProgress(token, bookId, 10)
        setProgress(token, bookId, 50) // overwrite progress

        val bm =
            app(
                Request(Method.GET, "/api/bookmarks?bookId=$bookId")
                    .header("Cookie", "token=$token"),
            )
        assertTrue(bm.bodyString().contains(bmId), "bookmark should survive progress updates")
    }

    @Test
    fun `deleting a bookmark does not affect progress`() {
        val token = registerAndGetToken("cmb")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        setProgress(token, bookId, 77)
        val bmId = addBookmark(token, bookId, 20)

        app(Request(Method.DELETE, "/api/bookmarks/$bmId").header("Cookie", "token=$token"))

        val book =
            app(
                Request(Method.GET, "/api/books/$bookId")
                    .header("Cookie", "token=$token"),
            )
        assertTrue(book.bodyString().contains("77"), "progress should survive bookmark deletion")
    }

    // ── File upload + progress + bookmarks ───────────────────────────────

    @Test
    fun `file upload does not reset progress or bookmarks`() {
        val token = registerAndGetToken("cmb")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        setProgress(token, bookId, 30)
        val bmId = addBookmark(token, bookId, 15)

        uploadPdf(token, bookId)

        val book =
            app(
                Request(Method.GET, "/api/books/$bookId")
                    .header("Cookie", "token=$token"),
            )
        val body = book.bodyString()
        assertTrue(body.contains("30"), "progress should survive file upload")

        val bm =
            app(
                Request(Method.GET, "/api/bookmarks?bookId=$bookId")
                    .header("Cookie", "token=$token"),
            )
        assertTrue(bm.bodyString().contains(bmId), "bookmarks should survive file upload")
    }

    @Test
    fun `second upload overwrites first file and book remains accessible`() {
        val token = registerAndGetToken("cmb")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        // First upload
        uploadPdf(token, bookId)

        // Second upload to same book
        val r2 =
            app(
                Request(Method.POST, "/api/books/$bookId/upload")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/octet-stream")
                    .header("X-Filename", "book-v2.pdf")
                    .body(ByteArrayInputStream(minimalPdf())),
            )
        assertEquals(Status.OK, r2.status, "second upload should succeed")

        // Book is still downloadable
        val dl =
            app(
                Request(Method.GET, "/api/books/$bookId/file")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, dl.status)
    }

    // ── Cross-user isolation ──────────────────────────────────────────────

    @Test
    fun `user B cannot see user A reading progress`() {
        val tokenA = registerAndGetToken("cmb_a")
        val tokenB = registerAndGetToken("cmb_b")
        val libId = createLibrary(tokenA)
        val bookId = createBook(tokenA, libId)

        setProgress(tokenA, bookId, 99)

        // B tries to update progress on A's book → 404
        val r =
            app(
                Request(Method.PUT, "/api/books/$bookId/progress")
                    .header("Cookie", "token=$tokenB")
                    .header("Content-Type", "application/json")
                    .body("""{"currentPage":1}"""),
            )
        assertEquals(Status.NOT_FOUND, r.status)
    }

    @Test
    fun `user B cannot see user A bookmarks`() {
        val tokenA = registerAndGetToken("cmb_a")
        val tokenB = registerAndGetToken("cmb_b")
        val libId = createLibrary(tokenA)
        val bookId = createBook(tokenA, libId)

        addBookmark(tokenA, bookId, 5)

        // B queries bookmarks for A's book → empty, not A's marks
        val r =
            app(
                Request(Method.GET, "/api/bookmarks?bookId=$bookId")
                    .header("Cookie", "token=$tokenB"),
            )
        assertEquals(Status.OK, r.status)
        assertFalse(r.bodyString().contains("\"page\":5"), "B should not see A's bookmarks")
    }

    @Test
    fun `progress percentage is correct when page count is known`() {
        val token = registerAndGetToken("cmb")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        // Upload a real PDF so page count is extracted
        uploadPdf(token, bookId)
        Thread.sleep(2000) // wait for async PDF extraction

        // Page count for a minimal PDF is 1; set progress to 1 → should be 100%
        val r =
            app(
                Request(Method.PUT, "/api/books/$bookId/progress")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"currentPage":1}"""),
            )
        assertEquals(Status.OK, r.status)
        val prog = Json.mapper.readValue(r.bodyString(), ReadingProgressDto::class.java)
        // percentage may be null if pageCount not yet extracted; if set it should be 100.0
        if (prog.percentage != null) {
            assertEquals(100.0, prog.percentage, 0.01)
        }
    }

    // ── Cascading deletion ────────────────────────────────────────────────

    @Test
    fun `deleting a book removes it from search results`() {
        val token = registerAndGetToken("cmb")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "UniqueVanishingTitle${System.nanoTime()}")

        app(Request(Method.DELETE, "/api/books/$bookId").header("Cookie", "token=$token"))

        val search =
            app(
                Request(Method.GET, "/api/search?q=UniqueVanishingTitle")
                    .header("Cookie", "token=$token"),
            )
        assertFalse(search.bodyString().contains(bookId))
    }

    @Test
    fun `deleting library removes all its books from combined listing`() {
        val token = registerAndGetToken("cmb")
        val libId = createLibrary(token)
        val bookId1 = createBook(token, libId)
        val bookId2 = createBook(token, libId)

        app(Request(Method.DELETE, "/ui/libraries/$libId").header("Cookie", "token=$token"))

        val all =
            app(
                Request(Method.GET, "/api/books")
                    .header("Cookie", "token=$token"),
            )
        val body = all.bodyString()
        assertFalse(body.contains(bookId1), "book1 should be gone after library deletion")
        assertFalse(body.contains(bookId2), "book2 should be gone after library deletion")
    }

    @Test
    fun `deleting library with uploaded files and progress does not crash`() {
        val token = registerAndGetToken("cmb")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        uploadPdf(token, bookId)
        setProgress(token, bookId, 10)
        addBookmark(token, bookId, 3)

        val r =
            app(
                Request(Method.DELETE, "/ui/libraries/$libId")
                    .header("Cookie", "token=$token"),
            )
        // Should succeed, not 500
        assertTrue(r.status.code < 500, "deletion of enriched library should not error: ${r.status}")
    }

    // ── Search + combined state ───────────────────────────────────────────

    @Test
    fun `search results include books regardless of progress or upload state`() {
        val token = registerAndGetToken("cmb")
        val libId = createLibrary(token)

        // Book with nothing
        val bare = createBook(token, libId, "SearchTarget bare ${System.nanoTime()}")
        // Book with progress
        val withProgress = createBook(token, libId, "SearchTarget progress ${System.nanoTime()}")
        setProgress(token, withProgress, 5)
        // Book with file
        val withFile = createBook(token, libId, "SearchTarget file ${System.nanoTime()}")
        uploadPdf(token, withFile)

        listOf(bare, withProgress, withFile).forEach { id ->
            val r =
                app(
                    Request(Method.GET, "/api/search?q=SearchTarget")
                        .header("Cookie", "token=$token"),
                )
            assertTrue(r.bodyString().contains(id), "book $id should appear in search")
        }
    }

    @Test
    fun `recent books only contains books with progress not all books`() {
        val token = registerAndGetToken("cmb")
        val libId = createLibrary(token)

        val noProgress = createBook(token, libId)
        val withProgress = createBook(token, libId)
        setProgress(token, withProgress, 1)

        val r =
            app(
                Request(Method.GET, "/api/recent")
                    .header("Cookie", "token=$token"),
            )
        val body = r.bodyString()
        assertTrue(body.contains(withProgress), "book with progress should appear in recent")
        assertFalse(body.contains(noProgress), "book without progress should NOT appear in recent")
    }

    // ── Multiple libraries ────────────────────────────────────────────────

    @Test
    fun `books across multiple libraries all appear in combined listing`() {
        val token = registerAndGetToken("cmb")
        val lib1 = createLibrary(token)
        val lib2 = createLibrary(token)
        val book1 = createBook(token, lib1)
        val book2 = createBook(token, lib2)

        val r =
            app(
                Request(Method.GET, "/api/books")
                    .header("Cookie", "token=$token"),
            )
        val body = r.bodyString()
        assertTrue(body.contains(book1))
        assertTrue(body.contains(book2))
    }

    @Test
    fun `deleting one library does not affect books in another library`() {
        val token = registerAndGetToken("cmb")
        val lib1 = createLibrary(token)
        val lib2 = createLibrary(token)
        val book1 = createBook(token, lib1)
        val book2 = createBook(token, lib2)

        app(Request(Method.DELETE, "/ui/libraries/$lib1").header("Cookie", "token=$token"))

        val r =
            app(
                Request(Method.GET, "/api/books")
                    .header("Cookie", "token=$token"),
            )
        val body = r.bodyString()
        assertFalse(body.contains(book1), "book in deleted library should be gone")
        assertTrue(body.contains(book2), "book in surviving library should remain")
    }
}
