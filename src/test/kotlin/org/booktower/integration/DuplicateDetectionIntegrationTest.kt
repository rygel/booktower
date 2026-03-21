package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.services.DuplicateDetectionService
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.UUID
import kotlin.test.assertTrue

class DuplicateDetectionIntegrationTest : IntegrationTestBase() {
    private val jdbi = TestFixture.database.getJdbi()
    private val duplicateService = DuplicateDetectionService(jdbi)

    // Raw SQL is acceptable here: the duplicate detection tests need books with specific isbn
    // and file_hash values. The BookService.createBook API does not support setting these fields
    // directly — they are normally populated by metadata extraction and file scanning pipelines.
    private fun insertBook(
        libraryId: String,
        title: String,
        author: String? = null,
        isbn: String? = null,
        fileHash: String? = null,
    ): String {
        val id = UUID.randomUUID().toString()
        val now =
            java.time.Instant
                .now()
                .toString()
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate(
                    """INSERT INTO books (id, title, author, isbn, file_hash, library_id, file_path, file_size, added_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?)""",
                ).bind(0, id)
                .bind(1, title)
                .bind(2, author)
                .bind(3, isbn)
                .bind(4, fileHash)
                .bind(5, libraryId)
                .bind(6, "/tmp/$id.epub")
                .bind(7, now)
                .execute()
        }
        return id
    }

    private fun createLibraryForUser(
        userId: UUID,
        name: String = "Lib",
    ): String {
        val libDir = Files.createTempDirectory("bt-dup-test-").toFile()
        val pdfMetadataService = org.booktower.services.PdfMetadataService(jdbi, TestFixture.config.storage.coversPath)
        val libraryAccessService = org.booktower.services.LibraryAccessService(jdbi)
        val libraryService = org.booktower.services.LibraryService(jdbi, pdfMetadataService, libraryAccessService)
        val lib = libraryService.createLibrary(userId, org.booktower.models.CreateLibraryRequest(name, libDir.absolutePath))
        return lib.id
    }

    private fun createUser(): UUID {
        val userId = createTestUser("duptest_${System.nanoTime()}")
        return UUID.fromString(userId)
    }

    @Test
    fun `findDuplicates groups books with same ISBN`() {
        val userId = createUser()
        val lib = createLibraryForUser(userId)
        insertBook(lib, "Book A", isbn = "978-0-123456-78-9")
        insertBook(lib, "Book A copy", isbn = "978-0-123456-78-9")

        val groups = duplicateService.findDuplicates(userId)
        val isbnGroup = groups.find { it.reason == "isbn" }
        assertTrue(isbnGroup != null, "Expected ISBN duplicate group")
        assertEquals(2, isbnGroup!!.books.size)
        assertEquals("978-0-123456-78-9", isbnGroup.matchValue)
    }

    @Test
    fun `findDuplicates groups books with same file hash`() {
        val userId = createUser()
        val lib = createLibraryForUser(userId)
        val hash = "abc123def456"
        insertBook(lib, "Hash Book 1", fileHash = hash)
        insertBook(lib, "Hash Book 2", fileHash = hash)

        val groups = duplicateService.findDuplicates(userId)
        val hashGroup = groups.find { it.reason == "file_hash" }
        assertTrue(hashGroup != null, "Expected file_hash duplicate group")
        assertEquals(2, hashGroup!!.books.size)
        assertEquals(hash, hashGroup.matchValue)
    }

    @Test
    fun `findDuplicates groups books with same normalised title and author`() {
        val userId = createUser()
        val lib = createLibraryForUser(userId)
        insertBook(lib, "The Great Gatsby", author = "F. Scott Fitzgerald")
        insertBook(lib, "The Great Gatsby", author = "F. Scott Fitzgerald")

        val groups = duplicateService.findDuplicates(userId)
        val titleGroup = groups.find { it.reason == "title_author" }
        assertTrue(titleGroup != null, "Expected title_author duplicate group")
        assertEquals(2, titleGroup!!.books.size)
    }

    @Test
    fun `findDuplicates normalises title case and punctuation`() {
        val userId = createUser()
        val lib = createLibraryForUser(userId)
        insertBook(lib, "  THE great gatsby  ", author = "f scott fitzgerald")
        insertBook(lib, "The Great Gatsby", author = "F. Scott Fitzgerald")

        val groups = duplicateService.findDuplicates(userId)
        val titleGroup = groups.find { it.reason == "title_author" }
        assertTrue(titleGroup != null, "Expected normalised title+author to match")
    }

    @Test
    fun `findDuplicates does not cross user boundaries`() {
        val userId1 = createUser()
        val userId2 = createUser()
        val lib1 = createLibraryForUser(userId1)
        val lib2 = createLibraryForUser(userId2)
        val isbn = "978-0-999999-99-9"
        insertBook(lib1, "Shared ISBN Book", isbn = isbn)
        insertBook(lib2, "Shared ISBN Book", isbn = isbn)

        val groups1 = duplicateService.findDuplicates(userId1)
        val groups2 = duplicateService.findDuplicates(userId2)
        // Each user has only one book, so no duplicates within their scope
        assertTrue(groups1.none { it.reason == "isbn" && it.matchValue == isbn })
        assertTrue(groups2.none { it.reason == "isbn" && it.matchValue == isbn })
    }

    @Test
    fun `findDuplicates returns empty when no duplicates exist`() {
        val userId = createUser()
        val lib = createLibraryForUser(userId)
        insertBook(lib, "Unique Book 1", author = "Author A", isbn = "111")
        insertBook(lib, "Unique Book 2", author = "Author B", isbn = "222")

        val groups = duplicateService.findDuplicates(userId)
        assertTrue(groups.isEmpty(), "Expected no duplicate groups")
    }

    @Test
    fun `GET api admin duplicates requires admin token`() {
        val token = registerAndGetToken("duptest")
        val resp =
            app(
                Request(Method.GET, "/api/admin/duplicates").header("Cookie", "token=$token"),
            )
        assertEquals(Status.FORBIDDEN, resp.status)
    }
}
