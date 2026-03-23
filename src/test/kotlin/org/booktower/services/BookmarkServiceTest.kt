package org.booktower.services

import org.booktower.TestFixture
import org.booktower.models.CreateBookRequest
import org.booktower.models.CreateBookmarkRequest
import org.booktower.models.CreateLibraryRequest
import org.booktower.models.CreateUserRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookmarkServiceTest {
    private lateinit var bookmarkService: BookmarkService
    private lateinit var bookService: BookService
    private lateinit var libraryService: LibraryService
    private lateinit var authService: AuthService
    private lateinit var jwtService: JwtService
    private lateinit var userId: UUID
    private lateinit var bookId: UUID

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

        val result =
            authService.register(
                CreateUserRequest("bookmark_${System.nanoTime()}", "bookmark_${System.nanoTime()}@test.com", org.booktower.TestPasswords.DEFAULT),
            )
        userId = jwtService.extractUserId(result.getOrThrow().token)!!
        val libId = libraryService.createLibrary(userId, CreateLibraryRequest("BM Lib", "./data/bm-${System.nanoTime()}")).id
        val book = bookService.createBook(userId, CreateBookRequest("Bookmark Book", "Author", null, libId)).getOrThrow()
        bookId = UUID.fromString(book.id)
    }

    @Test
    fun `createBookmark creates and returns bookmark`() {
        val result =
            bookmarkService.createBookmark(
                userId,
                CreateBookmarkRequest(bookId = bookId.toString(), page = 10, title = "Ch 1", note = "Important"),
            )
        assertTrue(result.isSuccess)
        val bm = result.getOrThrow()
        assertEquals(10, bm.page)
        assertEquals("Ch 1", bm.title)
        assertEquals("Important", bm.note)
    }

    @Test
    fun `getBookmarks returns created bookmarks ordered by page`() {
        bookmarkService.createBookmark(userId, CreateBookmarkRequest(bookId.toString(), 20, "Ch 2", null))
        bookmarkService.createBookmark(userId, CreateBookmarkRequest(bookId.toString(), 5, "Intro", null))
        val bookmarks = bookmarkService.getBookmarks(userId, bookId)
        assertEquals(2, bookmarks.size)
        assertEquals(5, bookmarks[0].page)
        assertEquals(20, bookmarks[1].page)
    }

    @Test
    fun `getBookmarks returns empty list for book with no bookmarks`() {
        val bookmarks = bookmarkService.getBookmarks(userId, bookId)
        assertTrue(bookmarks.isEmpty())
    }

    @Test
    fun `createBookmark fails for non-existent book`() {
        val result =
            bookmarkService.createBookmark(
                userId,
                CreateBookmarkRequest(bookId = UUID.randomUUID().toString(), page = 1, title = "Ghost", note = null),
            )
        assertTrue(result.isFailure)
    }

    @Test
    fun `createBookmark fails for invalid bookId format`() {
        val result =
            bookmarkService.createBookmark(
                userId,
                CreateBookmarkRequest(bookId = "not-a-uuid", page = 1, title = "Bad", note = null),
            )
        assertTrue(result.isFailure)
    }

    @Test
    fun `deleteBookmark removes the bookmark`() {
        val bm =
            bookmarkService
                .createBookmark(
                    userId,
                    CreateBookmarkRequest(bookId.toString(), 7, "Delete Me", null),
                ).getOrThrow()
        val deleted = bookmarkService.deleteBookmark(userId, UUID.fromString(bm.id))
        assertTrue(deleted)
        assertTrue(bookmarkService.getBookmarks(userId, bookId).isEmpty())
    }

    @Test
    fun `deleteBookmark returns false for non-existent id`() {
        assertFalse(bookmarkService.deleteBookmark(userId, UUID.randomUUID()))
    }

    @Test
    fun `createBookmark fails for another user's book`() {
        val otherResult =
            authService.register(
                CreateUserRequest("bmother_${System.nanoTime()}", "bmother_${System.nanoTime()}@test.com", org.booktower.TestPasswords.DEFAULT),
            )
        val otherId = jwtService.extractUserId(otherResult.getOrThrow().token)!!
        val result =
            bookmarkService.createBookmark(
                otherId,
                CreateBookmarkRequest(bookId.toString(), 1, "Spy", null),
            )
        assertTrue(result.isFailure)
    }
}
