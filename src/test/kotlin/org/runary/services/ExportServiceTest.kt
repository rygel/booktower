package org.runary.services

import org.runary.TestFixture
import org.runary.models.CreateBookRequest
import org.runary.models.CreateBookmarkRequest
import org.runary.models.CreateLibraryRequest
import org.runary.models.CreateUserRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportServiceTest {
    private lateinit var exportService: ExportService
    private lateinit var bookService: BookService
    private lateinit var libraryService: LibraryService
    private lateinit var bookmarkService: BookmarkService
    private lateinit var authService: AuthService
    private lateinit var jwtService: JwtService
    private lateinit var userId: UUID
    private lateinit var username: String
    private lateinit var email: String

    @BeforeEach
    fun setup() {
        val jdbi = TestFixture.database.getJdbi()
        val config = TestFixture.config
        jwtService = JwtService(config.security)
        authService = AuthService(jdbi, jwtService)
        val pdfMetadataService = PdfMetadataService(jdbi, config.storage.coversPath)
        libraryService = LibraryService(jdbi, pdfMetadataService)
        bookService = BookService(jdbi)
        bookmarkService = BookmarkService(jdbi)
        exportService = ExportService(jdbi)

        val nano = System.nanoTime()
        username = "export_$nano"
        email = "export_$nano@test.com"
        val result = authService.register(CreateUserRequest(username, email, org.runary.TestPasswords.DEFAULT))
        userId = jwtService.extractUserId(result.getOrThrow().token)!!
    }

    @Test
    fun `exportUser returns correct username and email`() {
        val export = exportService.exportUser(userId)
        assertEquals(username, export.username)
        assertEquals(email, export.email)
        assertTrue(export.memberSince.isNotBlank())
    }

    @Test
    fun `exportUser with no libraries returns empty list`() {
        val export = exportService.exportUser(userId)
        assertTrue(export.libraries.isEmpty())
    }

    @Test
    fun `exportUser includes created libraries`() {
        libraryService.createLibrary(userId, CreateLibraryRequest("Shelf A", "./data/ea-${System.nanoTime()}"))
        libraryService.createLibrary(userId, CreateLibraryRequest("Shelf B", "./data/eb-${System.nanoTime()}"))
        val export = exportService.exportUser(userId)
        assertEquals(2, export.libraries.size)
        val names = export.libraries.map { it.name }
        assertTrue(names.contains("Shelf A"))
        assertTrue(names.contains("Shelf B"))
    }

    @Test
    fun `exportUser includes books in library`() {
        val libId = libraryService.createLibrary(userId, CreateLibraryRequest("Books Lib", "./data/ebl-${System.nanoTime()}")).id
        bookService.createBook(userId, CreateBookRequest("Exported Book", "Author X", "A description", libId))
        val export = exportService.exportUser(userId)
        val lib = export.libraries.first()
        assertEquals(1, lib.books.size)
        val book = lib.books.first()
        assertEquals("Exported Book", book.title)
        assertEquals("Author X", book.author)
        assertEquals("A description", book.description)
    }

    @Test
    fun `exportUser includes bookmarks for books`() {
        val libId = libraryService.createLibrary(userId, CreateLibraryRequest("BM Export", "./data/ebm-${System.nanoTime()}")).id
        val book = bookService.createBook(userId, CreateBookRequest("BM Book", "Auth", null, libId)).getOrThrow()
        bookmarkService.createBookmark(userId, CreateBookmarkRequest(book.id, 5, "Ch 1", "A note"))
        val export = exportService.exportUser(userId)
        val exportedBook =
            export.libraries
                .first()
                .books
                .first()
        assertEquals(1, exportedBook.bookmarks.size)
        assertEquals(5, exportedBook.bookmarks.first().page)
        assertEquals("Ch 1", exportedBook.bookmarks.first().title)
    }

    @Test
    fun `exportUser throws for non-existent user`() {
        assertThrows<IllegalArgumentException> {
            exportService.exportUser(UUID.randomUUID())
        }
    }

    @Test
    fun `exportUser does not include another user's libraries`() {
        val otherResult =
            authService.register(
                CreateUserRequest("exportother_${System.nanoTime()}", "exportother_${System.nanoTime()}@test.com", org.runary.TestPasswords.DEFAULT),
            )
        val otherId = jwtService.extractUserId(otherResult.getOrThrow().token)!!
        libraryService.createLibrary(otherId, CreateLibraryRequest("Other Lib", "./data/eo-${System.nanoTime()}"))
        val export = exportService.exportUser(userId)
        assertTrue(export.libraries.isEmpty())
    }

    @Test
    fun `exportUser book tags are included`() {
        val libId = libraryService.createLibrary(userId, CreateLibraryRequest("Tag Export", "./data/etag-${System.nanoTime()}")).id
        val book = bookService.createBook(userId, CreateBookRequest("Tagged", "Auth", null, libId)).getOrThrow()
        bookService.bulkTag(userId, listOf(UUID.fromString(book.id)), listOf("sci-fi", "classic"))
        val export = exportService.exportUser(userId)
        val exportedBook =
            export.libraries
                .first()
                .books
                .first()
        assertEquals(listOf("classic", "sci-fi"), exportedBook.tags.sorted())
    }
}
