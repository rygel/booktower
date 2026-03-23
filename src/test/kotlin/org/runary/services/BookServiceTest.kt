package org.runary.services

import org.runary.TestFixture
import org.runary.models.CreateBookRequest
import org.runary.models.CreateLibraryRequest
import org.runary.models.CreateUserRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookServiceTest {
    private lateinit var bookService: BookService
    private lateinit var libraryService: LibraryService
    private lateinit var authService: AuthService
    private lateinit var jwtService: JwtService
    private lateinit var userId: UUID
    private lateinit var libraryId: String

    @BeforeEach
    fun setup() {
        val config = TestFixture.config
        val jdbi = TestFixture.database.getJdbi()
        jwtService = JwtService(config.security)
        authService = AuthService(jdbi, jwtService)
        val pdfMetadataService = PdfMetadataService(jdbi, config.storage.coversPath)
        libraryService = LibraryService(jdbi, pdfMetadataService)
        bookService = BookService(jdbi)

        val result =
            authService.register(
                CreateUserRequest("bookuser_${System.nanoTime()}", "book_${System.nanoTime()}@test.com", org.runary.TestPasswords.DEFAULT),
            )
        userId = jwtService.extractUserId(result.getOrThrow().token)!!
        libraryId = libraryService.createLibrary(userId, CreateLibraryRequest("BookLib", "./data/test-bl-${System.nanoTime()}")).id
    }

    @Test
    fun `createBook creates book in library`() {
        val result = bookService.createBook(userId, CreateBookRequest("Test Book", "Author", "Description", libraryId))
        assertTrue(result.isSuccess)
        val book = result.getOrThrow()
        assertEquals("Test Book", book.title)
        assertEquals("Author", book.author)
    }

    @Test
    fun `createBook fails for non-existent library`() {
        assertTrue(bookService.createBook(userId, CreateBookRequest("Book", "Author", null, UUID.randomUUID().toString())).isFailure)
    }

    @Test
    fun `getBooks returns books in library`() {
        bookService.createBook(userId, CreateBookRequest("Book A", "Author A", null, libraryId))
        bookService.createBook(userId, CreateBookRequest("Book B", "Author B", null, libraryId))
        val bookList = bookService.getBooks(userId, libraryId)
        assertEquals(2, bookList.total)
        assertEquals(2, bookList.getBooks().size)
    }

    @Test
    fun `getBooks returns empty for library with no books`() {
        val bookList = bookService.getBooks(userId, libraryId)
        assertEquals(0, bookList.total)
        assertTrue(bookList.getBooks().isEmpty())
    }

    @Test
    fun `getBooks supports pagination`() {
        for (i in 1..5) bookService.createBook(userId, CreateBookRequest("Book $i", null, null, libraryId))
        val page1 = bookService.getBooks(userId, libraryId, page = 1, pageSize = 2)
        assertEquals(5, page1.total)
        assertEquals(2, page1.getBooks().size)
        assertEquals(1, bookService.getBooks(userId, libraryId, page = 3, pageSize = 2).getBooks().size)
    }

    @Test
    fun `getBook returns specific book`() {
        val created = bookService.createBook(userId, CreateBookRequest("Find Me", "Author", null, libraryId)).getOrThrow()
        val found = bookService.getBook(userId, UUID.fromString(created.id))
        assertNotNull(found)
        assertEquals("Find Me", found.title)
    }

    @Test
    fun `getBook returns null for non-existent id`() {
        assertNull(bookService.getBook(userId, UUID.randomUUID()))
    }

    @Test
    fun `deleteBook removes book`() {
        val created = bookService.createBook(userId, CreateBookRequest("Delete Me", null, null, libraryId)).getOrThrow()
        val bookId = UUID.fromString(created.id)
        assertTrue(bookService.deleteBook(userId, bookId))
        assertNull(bookService.getBook(userId, bookId))
    }

    @Test
    fun `getBooks without libraryId returns all user books`() {
        bookService.createBook(userId, CreateBookRequest("All Books", null, null, libraryId))
        assertTrue(bookService.getBooks(userId, null).total >= 1)
    }
}
