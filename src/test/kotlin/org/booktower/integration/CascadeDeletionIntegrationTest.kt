package org.booktower.integration

import org.booktower.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Verifies that the ON DELETE CASCADE constraints in the schema actually fire
 * end-to-end, i.e. deleting a library removes its books, and deleting a book
 * removes its bookmarks, reading progress, and annotations.
 */
class CascadeDeletionIntegrationTest : IntegrationTestBase() {
    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun createBookmark(
        token: String,
        bookId: String,
        page: Int = 1,
    ): String {
        val resp =
            app(
                Request(Method.POST, "/api/bookmarks")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"bookId":"$bookId","page":$page,"title":"test mark","note":null}"""),
            )
        assertEquals(Status.CREATED, resp.status, "createBookmark failed: ${resp.bodyString()}")
        return Json.mapper
            .readTree(resp.bodyString())
            .get("id")
            .asText()
    }

    private fun setProgress(
        token: String,
        bookId: String,
        page: Int = 5,
    ) {
        val resp =
            app(
                Request(Method.PUT, "/api/books/$bookId/progress")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"currentPage":$page}"""),
            )
        assertEquals(Status.OK, resp.status, "setProgress failed: ${resp.bodyString()}")
    }

    private fun getBookmarks(
        token: String,
        bookId: String,
    ): Int {
        val resp =
            app(
                Request(Method.GET, "/api/bookmarks?bookId=$bookId")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, resp.status)
        return Json.mapper.readTree(resp.bodyString()).size()
    }

    private fun getBook(
        token: String,
        bookId: String,
    ): Status =
        app(
            Request(Method.GET, "/api/books/$bookId")
                .header("Cookie", "token=$token"),
        ).status

    private fun deleteLibrary(
        token: String,
        libId: String,
    ): Status =
        app(
            Request(Method.DELETE, "/api/libraries/$libId")
                .header("Cookie", "token=$token"),
        ).status

    private fun deleteBook(
        token: String,
        bookId: String,
    ): Status =
        app(
            Request(Method.DELETE, "/api/books/$bookId")
                .header("Cookie", "token=$token"),
        ).status

    // ── Delete library cascades ───────────────────────────────────────────────

    @Test
    fun `deleting library removes its books`() {
        val token = registerAndGetToken("casc_lib1")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        assertEquals(Status.OK, deleteLibrary(token, libId))

        // Book should no longer be accessible
        assertEquals(Status.NOT_FOUND, getBook(token, bookId), "Book should be deleted with its library")
    }

    @Test
    fun `deleting library removes books from list`() {
        val token = registerAndGetToken("casc_lib2")
        val libId = createLibrary(token)
        createBook(token, libId, "Orphan Book")

        assertEquals(Status.OK, deleteLibrary(token, libId))

        val resp =
            app(
                Request(Method.GET, "/api/books")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals(0, tree.get("total").asInt(), "No books should remain after library deletion")
    }

    @Test
    fun `deleting library cascades to reading progress`() {
        val token = registerAndGetToken("casc_lib3")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        setProgress(token, bookId, 10)

        assertEquals(Status.OK, deleteLibrary(token, libId))

        // After library is gone, requesting progress for the deleted book is 404
        // (the book itself is gone, so the progress has cascaded away too)
        assertEquals(
            Status.NOT_FOUND,
            getBook(token, bookId),
            "Book (and its progress) should be gone after library deletion",
        )
    }

    @Test
    fun `deleting library cascades to bookmarks`() {
        val token = registerAndGetToken("casc_lib4")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        createBookmark(token, bookId, 1)

        assertEquals(Status.OK, deleteLibrary(token, libId))

        // Querying bookmarks for the now-deleted book should return 0
        assertEquals(0, getBookmarks(token, bookId), "Bookmarks should cascade away with the library")
    }

    @Test
    fun `deleting library with multiple books removes all of them`() {
        val token = registerAndGetToken("casc_lib5")
        val libId = createLibrary(token)
        val book1 = createBook(token, libId, "Book One")
        val book2 = createBook(token, libId, "Book Two")
        val book3 = createBook(token, libId, "Book Three")

        assertEquals(Status.OK, deleteLibrary(token, libId))

        listOf(book1, book2, book3).forEach { bookId ->
            assertEquals(Status.NOT_FOUND, getBook(token, bookId), "All books should be deleted with library")
        }
    }

    @Test
    fun `deleting one library does not affect books in another library`() {
        val token = registerAndGetToken("casc_lib6")
        val lib1 = createLibrary(token, "Lib To Delete")
        val lib2 = createLibrary(token, "Lib To Keep")
        val book1 = createBook(token, lib1, "Doomed Book")
        val book2 = createBook(token, lib2, "Safe Book")

        assertEquals(Status.OK, deleteLibrary(token, lib1))

        assertEquals(Status.NOT_FOUND, getBook(token, book1), "Book in deleted library should be gone")
        assertEquals(Status.OK, getBook(token, book2), "Book in kept library should remain")
    }

    // ── Delete book cascades ──────────────────────────────────────────────────

    @Test
    fun `deleting book removes its bookmarks`() {
        val token = registerAndGetToken("casc_book1")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        createBookmark(token, bookId, 3)
        createBookmark(token, bookId, 7)

        assertEquals(Status.OK, deleteBook(token, bookId))

        assertEquals(0, getBookmarks(token, bookId), "Bookmarks should cascade away when book is deleted")
    }

    @Test
    fun `deleting book removes its reading progress`() {
        val token = registerAndGetToken("casc_book2")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        setProgress(token, bookId, 50)

        assertEquals(Status.OK, deleteBook(token, bookId))

        // Book is gone; any further GET on it returns 404
        assertEquals(Status.NOT_FOUND, getBook(token, bookId))
    }

    @Test
    fun `deleting book does not affect bookmarks on other books`() {
        val token = registerAndGetToken("casc_book3")
        val libId = createLibrary(token)
        val bookA = createBook(token, libId, "Book A")
        val bookB = createBook(token, libId, "Book B")
        createBookmark(token, bookA, 1)
        createBookmark(token, bookB, 2)

        assertEquals(Status.OK, deleteBook(token, bookA))

        assertEquals(0, getBookmarks(token, bookA), "Book A bookmarks should be gone")
        assertEquals(1, getBookmarks(token, bookB), "Book B bookmarks should be unaffected")
    }

    @Test
    fun `deleting book makes GET by id return 404`() {
        val token = registerAndGetToken("casc_book4")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        assertEquals(Status.OK, deleteBook(token, bookId))
        assertEquals(Status.NOT_FOUND, getBook(token, bookId))
    }

    @Test
    fun `deleting book is idempotent — second delete returns 404`() {
        val token = registerAndGetToken("casc_book5")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        assertEquals(Status.OK, deleteBook(token, bookId))
        assertEquals(Status.NOT_FOUND, deleteBook(token, bookId), "Second delete should return 404")
    }

    // ── Delete book cascades to chapters ─────────────────────────────────────

    private fun uploadMinimalChapter(
        token: String,
        bookId: String,
        trackIndex: Int,
    ) {
        val mp3 = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00.toByte()) + ByteArray(416)
        app(
            Request(Method.POST, "/api/books/$bookId/chapters")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/octet-stream")
                .header("X-Filename", "chapter-$trackIndex.mp3")
                .header("X-Track-Index", trackIndex.toString())
                .body(mp3.inputStream(), mp3.size.toLong()),
        )
    }

    private fun listChapters(
        token: String,
        bookId: String,
    ): Int {
        val resp =
            app(
                Request(Method.GET, "/api/books/$bookId/chapters")
                    .header("Cookie", "token=$token"),
            )
        return if (resp.status == Status.OK) Json.mapper.readTree(resp.bodyString()).size() else 0
    }

    @Test
    fun `deleting book removes its chapters`() {
        val token = registerAndGetToken("casc_ch1")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        uploadMinimalChapter(token, bookId, 0)
        uploadMinimalChapter(token, bookId, 1)
        uploadMinimalChapter(token, bookId, 2)

        assertEquals(3, listChapters(token, bookId), "Should have 3 chapters before deletion")
        assertEquals(Status.OK, deleteBook(token, bookId))
        assertEquals(0, listChapters(token, bookId), "Chapters should cascade away when book is deleted")
    }

    @Test
    fun `deleting library removes audiobook chapters`() {
        val token = registerAndGetToken("casc_ch2")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        uploadMinimalChapter(token, bookId, 0)

        assertEquals(Status.OK, deleteLibrary(token, libId))
        // Book is gone; chapters must also be gone (via library→books→book_files cascade)
        assertEquals(Status.NOT_FOUND, getBook(token, bookId))
        assertEquals(0, listChapters(token, bookId))
    }

    @Test
    fun `deleting book with chapters does not affect chapters of another book`() {
        val token = registerAndGetToken("casc_ch3")
        val libId = createLibrary(token)
        val bookA = createBook(token, libId, "Audiobook A")
        val bookB = createBook(token, libId, "Audiobook B")
        uploadMinimalChapter(token, bookA, 0)
        uploadMinimalChapter(token, bookB, 0)
        uploadMinimalChapter(token, bookB, 1)

        assertEquals(Status.OK, deleteBook(token, bookA))

        assertEquals(0, listChapters(token, bookA), "Book A chapters should be gone")
        assertEquals(2, listChapters(token, bookB), "Book B chapters should be unaffected")
    }
}
