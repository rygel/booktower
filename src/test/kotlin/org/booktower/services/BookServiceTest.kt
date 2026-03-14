package org.booktower.services

import org.booktower.config.AppConfig
import org.booktower.config.Database
import org.booktower.models.CreateBookRequest
import org.booktower.models.CreateLibraryRequest
import org.booktower.models.CreateUserRequest
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
        val config = AppConfig.load()
        val database = Database.connect(config.database)
        jwtService = JwtService(config.security)
        authService = AuthService(database.getJdbi(), jwtService)
        libraryService = LibraryService(database.getJdbi(), config.storage)
        bookService = BookService(database.getJdbi(), config.storage)

        val result = authService.register(
            CreateUserRequest("bookuser_${System.nanoTime()}", "book_${System.nanoTime()}@test.com", "password123"),
        )
        userId = jwtService.extractUserId(result.getOrThrow().token)!!

        val lib = libraryService.createLibrary(userId, CreateLibraryRequest("BookLib", "./data/test-bl-${System.nanoTime()}"))
        libraryId = lib.id
    }

    @Test
    fun `createBook creates book in library`() {
        val request = CreateBookRequest("Test Book", "Author", "Description", libraryId)
        val result = bookService.createBook(userId, request)

        assertTrue(result.isSuccess)
        val book = result.getOrThrow()
        assertEquals("Test Book", book.title)
        assertEquals("Author", book.author)
        assertEquals("Description", book.description)
    }

    @Test
    fun `createBook fails for non-existent library`() {
        val request = CreateBookRequest("Book", "Author", null, UUID.randomUUID().toString())
        val result = bookService.createBook(userId, request)

        assertTrue(result.isFailure)
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
        for (i in 1..5) {
            bookService.createBook(userId, CreateBookRequest("Book $i", null, null, libraryId))
        }

        val page1 = bookService.getBooks(userId, libraryId, page = 1, pageSize = 2)
        assertEquals(5, page1.total)
        assertEquals(2, page1.getBooks().size)

        val page3 = bookService.getBooks(userId, libraryId, page = 3, pageSize = 2)
        assertEquals(1, page3.getBooks().size)
    }

    @Test
    fun `getBook returns specific book`() {
        val created = bookService.createBook(userId, CreateBookRequest("Find Me", "Author", null, libraryId)).getOrThrow()
        val bookId = UUID.fromString(created.id)

        val found = bookService.getBook(userId, bookId)
        assertNotNull(found)
        assertEquals("Find Me", found.title)
    }

    @Test
    fun `getBook returns null for non-existent id`() {
        val found = bookService.getBook(userId, UUID.randomUUID())
        assertNull(found)
    }

    @Test
    fun `deleteBook removes book`() {
        val created = bookService.createBook(userId, CreateBookRequest("Delete Me", null, null, libraryId)).getOrThrow()
        val bookId = UUID.fromString(created.id)

        val deleted = bookService.deleteBook(userId, bookId)
        assertTrue(deleted)

        val found = bookService.getBook(userId, bookId)
        assertNull(found)
    }

    @Test
    fun `getBooks without libraryId returns all user books`() {
        bookService.createBook(userId, CreateBookRequest("All Books", null, null, libraryId))

        val bookList = bookService.getBooks(userId, null)
        assertTrue(bookList.total >= 1)
    }
}
