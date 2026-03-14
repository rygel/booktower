package org.booktower.services

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.booktower.TestFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PdfMetadataServiceTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var coversDir: File
    private lateinit var service: PdfMetadataService
    private val jdbi = TestFixture.database.getJdbi()

    @BeforeEach
    fun setup() {
        coversDir = tempDir.resolve("covers").toFile()
        service = PdfMetadataService(jdbi, coversDir.absolutePath)
    }

    /** Create a minimal valid PDF with optional title/author and a given page count. */
    private fun makePdf(title: String? = null, author: String? = null, pages: Int = 1): File {
        val doc = PDDocument()
        if (title != null) doc.documentInformation.title = title
        if (author != null) doc.documentInformation.author = author
        repeat(pages) { doc.addPage(PDPage()) }
        val file = tempDir.resolve("test-${UUID.randomUUID()}.pdf").toFile()
        doc.save(file)
        doc.close()
        return file
    }

    @Test
    fun `extracts page count from single-page PDF`() {
        val pdf = makePdf(pages = 1)
        val meta = service.extractAndStore(UUID.randomUUID().toString(), pdf)
        assertEquals(1, meta.pageCount)
    }

    @Test
    fun `extracts page count from multi-page PDF`() {
        val pdf = makePdf(pages = 5)
        val meta = service.extractAndStore(UUID.randomUUID().toString(), pdf)
        assertEquals(5, meta.pageCount)
    }

    @Test
    fun `extracts title from PDF metadata`() {
        val pdf = makePdf(title = "My Great Book")
        val meta = service.extractAndStore(UUID.randomUUID().toString(), pdf)
        assertEquals("My Great Book", meta.title)
    }

    @Test
    fun `extracts author from PDF metadata`() {
        val pdf = makePdf(author = "Jane Author")
        val meta = service.extractAndStore(UUID.randomUUID().toString(), pdf)
        assertEquals("Jane Author", meta.author)
    }

    @Test
    fun `returns null title when PDF has no title metadata`() {
        val pdf = makePdf(title = null)
        val meta = service.extractAndStore(UUID.randomUUID().toString(), pdf)
        assertNull(meta.title)
    }

    @Test
    fun `returns null author when PDF has no author metadata`() {
        val pdf = makePdf(author = null)
        val meta = service.extractAndStore(UUID.randomUUID().toString(), pdf)
        assertNull(meta.author)
    }

    @Test
    fun `generates cover JPEG for first page`() {
        val pdf = makePdf(pages = 2)
        val bookId = UUID.randomUUID().toString()
        val meta = service.extractAndStore(bookId, pdf)

        assertNotNull(meta.coverPath)
        val coverFile = File(coversDir, "$bookId.jpg")
        assertTrue(coverFile.exists(), "Cover JPEG should exist at ${coverFile.absolutePath}")
        assertTrue(coverFile.length() > 0, "Cover JPEG should not be empty")
    }

    @Test
    fun `cover filename matches bookId`() {
        val pdf = makePdf()
        val bookId = UUID.randomUUID().toString()
        val meta = service.extractAndStore(bookId, pdf)
        assertEquals("$bookId.jpg", meta.coverPath)
    }

    @Test
    fun `corrupt file returns zero page count and null cover`() {
        val corrupt = tempDir.resolve("corrupt.pdf").toFile()
        corrupt.writeText("this is not a pdf")
        val meta = service.extractAndStore(UUID.randomUUID().toString(), corrupt)
        assertEquals(0, meta.pageCount)
        assertNull(meta.coverPath)
        assertNull(meta.title)
    }

    @Test
    fun `non-existent file returns empty metadata without throwing`() {
        val missing = tempDir.resolve("missing.pdf").toFile()
        val meta = service.extractAndStore(UUID.randomUUID().toString(), missing)
        assertEquals(0, meta.pageCount)
        assertNull(meta.coverPath)
    }

    @Test
    fun `persists page count to database after extraction`() {
        // Create a book in DB to update
        val bookId = UUID.randomUUID()
        val libId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        jdbi.useHandle<Exception> { h ->
            h.execute("INSERT INTO users (id, username, email, password_hash, created_at, updated_at, is_admin) VALUES (?,?,?,?,?,?,?)",
                userId.toString(), "pdf_test_${System.nanoTime()}", "pdf_${System.nanoTime()}@test.com", "hash",
                "2024-01-01 00:00:00", "2024-01-01 00:00:00", false)
            h.execute("INSERT INTO libraries (id, user_id, name, path, created_at, updated_at) VALUES (?,?,?,?,?,?)",
                libId.toString(), userId.toString(), "PDF Lib", "./test", "2024-01-01 00:00:00", "2024-01-01 00:00:00")
            h.execute("INSERT INTO books (id, library_id, title, file_path, file_size, added_at, updated_at) VALUES (?,?,?,?,?,?,?)",
                bookId.toString(), libId.toString(), "Pending Book", "", 0, "2024-01-01 00:00:00", "2024-01-01 00:00:00")
        }

        val pdf = makePdf(title = "Extracted Title", author = "PDF Author", pages = 3)
        service.extractAndStore(bookId.toString(), pdf)

        val pageCount = jdbi.withHandle<Int?, Exception> { h ->
            h.createQuery("SELECT page_count FROM books WHERE id = ?")
                .bind(0, bookId.toString())
                .mapTo(java.lang.Integer::class.java).firstOrNull()?.toInt()
        }
        assertEquals(3, pageCount)
    }
}
