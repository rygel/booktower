package org.booktower.integration

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.booktower.config.Json
import org.booktower.models.ScanResult
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end lifecycle tests that combine library scanning with subsequent book
 * operations: uploading files, reading progress, bookmarks, and deletion. These
 * tests verify that scan-created books behave identically to manually-created ones.
 */
class ScanBookLifecycleIntegrationTest : IntegrationTestBase() {

    private val tempDirs = mutableListOf<File>()

    @AfterEach
    fun cleanup() {
        tempDirs.forEach { it.deleteRecursively() }
        tempDirs.clear()
    }

    private fun tempDir(): File = Files.createTempDirectory("bt-lifecycle-test").toFile().also { tempDirs += it }

    private fun realPdfBytes(pages: Int = 2): ByteArray {
        val doc = PDDocument()
        repeat(pages) { doc.addPage(PDPage()) }
        return ByteArrayOutputStream().also { doc.save(it); doc.close() }.toByteArray()
    }

    private fun createLibraryWithPath(token: String, path: String): String {
        val resp = app(
            Request(Method.POST, "/api/libraries")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"name":"ScanLib ${System.nanoTime()}","path":"${path.replace("\\", "\\\\")}"}"""),
        )
        assertEquals(Status.CREATED, resp.status)
        return Json.mapper.readTree(resp.bodyString()).get("id").asText()
    }

    private fun scan(token: String, libId: String): ScanResult {
        val resp = app(
            Request(Method.POST, "/api/libraries/$libId/scan")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.OK, resp.status)
        return Json.mapper.readValue(resp.bodyString(), ScanResult::class.java)
    }

    // ── Scan → read (download) the scanned file ──────────────────────────────

    @Test
    fun `scanned book file can be downloaded immediately after scan`() {
        val token = registerAndGetToken("slc1")
        val dir = tempDir()
        val pdfBytes = realPdfBytes()
        File(dir, "download_me.pdf").writeBytes(pdfBytes)
        val libId = createLibraryWithPath(token, dir.absolutePath)
        val result = scan(token, libId)

        assertEquals(1, result.added)
        val bookId = result.books[0].id

        val dlResp = app(
            Request(Method.GET, "/api/books/$bookId/file")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.OK, dlResp.status, "scanned book should be downloadable")
        assertTrue(dlResp.bodyString().isNotEmpty(), "downloaded body should not be empty")
    }

    // ── Scan → upload new version → download updated version ─────────────────

    @Test
    fun `after scan user can upload a new version of the file`() {
        val token = registerAndGetToken("slc2")
        val dir = tempDir()
        File(dir, "replaceable.pdf").writeBytes(realPdfBytes(1))
        val libId = createLibraryWithPath(token, dir.absolutePath)
        val result = scan(token, libId)
        val bookId = result.books[0].id

        // Upload a new PDF to replace the scanned one
        val newPdfBytes = realPdfBytes(3)
        val uploadResp = app(
            Request(Method.POST, "/api/books/$bookId/upload")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/octet-stream")
                .header("X-Filename", "updated.pdf")
                .body(ByteArrayInputStream(newPdfBytes)),
        )
        assertEquals(Status.OK, uploadResp.status, "upload to scanned book should succeed")

        val dlResp = app(
            Request(Method.GET, "/api/books/$bookId/file")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.OK, dlResp.status)
    }

    // ── Scan → set progress → verify ─────────────────────────────────────────

    @Test
    fun `scanned book supports reading progress tracking`() {
        val token = registerAndGetToken("slc3")
        val dir = tempDir()
        File(dir, "progress_book.pdf").writeBytes(realPdfBytes())
        val libId = createLibraryWithPath(token, dir.absolutePath)
        val bookId = scan(token, libId).books[0].id

        val progressResp = app(
            Request(Method.POST, "/ui/books/$bookId/progress")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("currentPage=42"),
        )
        assertEquals(Status.OK, progressResp.status)

        val bookResp = app(
            Request(Method.GET, "/api/books/$bookId")
                .header("Cookie", "token=$token"),
        )
        assertTrue(bookResp.bodyString().contains("42"), "progress should be recorded for scanned book")
    }

    // ── Scan → add bookmarks → verify ────────────────────────────────────────

    @Test
    fun `scanned book supports bookmarks`() {
        val token = registerAndGetToken("slc4")
        val dir = tempDir()
        File(dir, "bookmarked.pdf").writeBytes(realPdfBytes())
        val libId = createLibraryWithPath(token, dir.absolutePath)
        val bookId = scan(token, libId).books[0].id

        val bmResp = app(
            Request(Method.POST, "/api/bookmarks")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"bookId":"$bookId","page":7,"title":"Scan Bookmark","note":null}"""),
        )
        assertEquals(Status.CREATED, bmResp.status)

        val listResp = app(
            Request(Method.GET, "/api/bookmarks?bookId=$bookId")
                .header("Cookie", "token=$token"),
        )
        assertTrue(listResp.bodyString().contains("Scan Bookmark"))
    }

    // ── Scan → book detail page renders correctly ─────────────────────────────

    @Test
    fun `scanned book renders correctly on book detail page`() {
        val token = registerAndGetToken("slc5")
        val dir = tempDir()
        File(dir, "my_rendered_book.pdf").writeBytes(realPdfBytes())
        val libId = createLibraryWithPath(token, dir.absolutePath)
        val bookId = scan(token, libId).books[0].id

        val pageResp = app(
            Request(Method.GET, "/books/$bookId")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.OK, pageResp.status)
        val body = pageResp.bodyString()
        // Title should be derived from filename
        assertTrue(body.contains("my rendered book") || body.contains("my_rendered_book"),
            "Book title should appear on detail page. Body snippet: ${body.take(500)}")
    }

    // ── Scan → delete book → confirm gone ────────────────────────────────────

    @Test
    fun `scanned book can be deleted and is removed from listing`() {
        val token = registerAndGetToken("slc6")
        val dir = tempDir()
        File(dir, "to_delete.pdf").writeBytes(realPdfBytes())
        val libId = createLibraryWithPath(token, dir.absolutePath)
        val bookId = scan(token, libId).books[0].id

        app(Request(Method.DELETE, "/api/books/$bookId").header("Cookie", "token=$token"))

        val listResp = app(Request(Method.GET, "/api/books").header("Cookie", "token=$token"))
        assertFalse(listResp.bodyString().contains(bookId), "deleted scanned book should not appear in listing")
    }

    // ── Scan → second scan skips, re-scan after delete re-adds ───────────────

    @Test
    fun `re-scan after deleting a scanned book adds it again`() {
        val token = registerAndGetToken("slc7")
        val dir = tempDir()
        File(dir, "readd_me.pdf").writeBytes(realPdfBytes())
        val libId = createLibraryWithPath(token, dir.absolutePath)

        val first = scan(token, libId)
        assertEquals(1, first.added, "first scan should add 1 book")
        val bookId = first.books[0].id

        // Delete the book record (file remains on disk)
        app(Request(Method.DELETE, "/api/books/$bookId").header("Cookie", "token=$token"))

        // Re-scan: file still exists on disk → should be added again
        val second = scan(token, libId)
        assertEquals(1, second.added, "re-scan after delete should add the book again")
    }

    // ── Multi-file scan: mixed PDF/EPUB lifecycle ─────────────────────────────

    @Test
    fun `scan with multiple file types all books are independently usable`() {
        val token = registerAndGetToken("slc8")
        val dir = tempDir()
        File(dir, "book1.pdf").writeBytes(realPdfBytes())
        // EPUB is just a ZIP — write minimal stub (scan just checks extension)
        File(dir, "book2.epub").writeText("fake epub content")
        val libId = createLibraryWithPath(token, dir.absolutePath)

        val result = scan(token, libId)
        assertEquals(2, result.added, "both files should be scanned")

        // Both books should be individually accessible
        result.books.forEach { book ->
            val pageResp = app(
                Request(Method.GET, "/books/${book.id}")
                    .header("Cookie", "token=$token"),
            )
            assertEquals(Status.OK, pageResp.status, "book ${book.title} page should render")
        }
    }
}
