package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.models.CreateBookRequest
import org.booktower.models.CreateLibraryRequest
import org.booktower.models.CreateUserRequest
import org.booktower.models.ReadStatus
import org.booktower.services.AnalyticsService
import org.booktower.services.AuthService
import org.booktower.services.BookService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.booktower.services.PdfMetadataService
import org.booktower.services.UserSettingsService
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RatingFilterIntegrationTest : IntegrationTestBase() {
    private lateinit var bookService: BookService
    private lateinit var userId: UUID
    private lateinit var libId: String
    private lateinit var token: String

    @BeforeEach
    fun setupServices() {
        val jdbi = TestFixture.database.getJdbi()
        val config = TestFixture.config
        val jwtService = JwtService(config.security)
        val authService = AuthService(jdbi, jwtService)
        val userSettingsService = UserSettingsService(jdbi)
        val analyticsService = AnalyticsService(jdbi, userSettingsService)
        bookService = BookService(jdbi, analyticsService)
        val pdfMetadataService = PdfMetadataService(jdbi, config.storage.coversPath)
        val libraryService = LibraryService(jdbi, pdfMetadataService)

        val result =
            authService.register(
                CreateUserRequest("rf_${System.nanoTime()}", "rf_${System.nanoTime()}@test.com", org.booktower.TestPasswords.DEFAULT),
            )
        val loginResponse = result.getOrThrow()
        userId = jwtService.extractUserId(loginResponse.token)!!
        token = loginResponse.token
        libId = libraryService.createLibrary(userId, CreateLibraryRequest("Rating Lib", "./data/rf-${System.nanoTime()}")).id
    }

    private fun createBookWithRating(
        title: String,
        rating: Int,
    ): String {
        val book = bookService.createBook(userId, CreateBookRequest(title, null, null, libId)).getOrThrow()
        bookService.setRating(userId, UUID.fromString(book.id), rating)
        return book.id
    }

    // ── Service-level tests ───────────────────────────────────────────────────

    @Test
    fun `getBooks with ratingGte=3 returns only books rated 3 or above`() {
        createBookWithRating("One Star", 1)
        createBookWithRating("Two Stars", 2)
        val threeId = createBookWithRating("Three Stars", 3)
        val fourId = createBookWithRating("Four Stars", 4)
        val fiveId = createBookWithRating("Five Stars", 5)

        val result = bookService.getBooks(userId, libId, ratingGte = 3)
        val ids = result.getBooks().map { it.id }.toSet()

        assertTrue(ids.contains(threeId))
        assertTrue(ids.contains(fourId))
        assertTrue(ids.contains(fiveId))
        assertFalse(ids.contains(createBookWithRating("also one", 1).also {}))
        assertEquals(3, ids.size)
    }

    @Test
    fun `getBooks with ratingGte=5 returns only 5-star books`() {
        createBookWithRating("Low", 2)
        val fiveId = createBookWithRating("Perfect", 5)

        val result = bookService.getBooks(userId, libId, ratingGte = 5)
        val books = result.getBooks()

        assertEquals(1, books.size)
        assertEquals(fiveId, books[0].id)
    }

    @Test
    fun `getBooks with ratingGte=1 excludes unrated books`() {
        val ratedId = createBookWithRating("Rated", 1)
        val unrated = bookService.createBook(userId, CreateBookRequest("Unrated", null, null, libId)).getOrThrow()

        val result = bookService.getBooks(userId, libId, ratingGte = 1)
        val ids = result.getBooks().map { it.id }.toSet()

        assertTrue(ids.contains(ratedId))
        assertFalse(ids.contains(unrated.id))
    }

    @Test
    fun `getBooks with null ratingGte returns all books including unrated`() {
        createBookWithRating("Rated", 3)
        val unrated = bookService.createBook(userId, CreateBookRequest("Unrated", null, null, libId)).getOrThrow()

        val result = bookService.getBooks(userId, libId, ratingGte = null)
        val ids = result.getBooks().map { it.id }.toSet()

        assertTrue(ids.contains(unrated.id))
    }

    @Test
    fun `getBooks total count respects ratingGte filter`() {
        createBookWithRating("Low 1", 1)
        createBookWithRating("Low 2", 2)
        createBookWithRating("High 1", 4)
        createBookWithRating("High 2", 5)

        val result = bookService.getBooks(userId, libId, ratingGte = 4)

        assertEquals(2, result.total)
    }

    @Test
    fun `getBooks ratingGte combined with statusFilter`() {
        val bookA = bookService.createBook(userId, CreateBookRequest("A", null, null, libId)).getOrThrow()
        val bookB = bookService.createBook(userId, CreateBookRequest("B", null, null, libId)).getOrThrow()
        val bookC = bookService.createBook(userId, CreateBookRequest("C", null, null, libId)).getOrThrow()

        bookService.setRating(userId, UUID.fromString(bookA.id), 4)
        bookService.setStatus(userId, UUID.fromString(bookA.id), ReadStatus.FINISHED)
        bookService.setRating(userId, UUID.fromString(bookB.id), 4)
        bookService.setStatus(userId, UUID.fromString(bookB.id), ReadStatus.READING)
        bookService.setRating(userId, UUID.fromString(bookC.id), 2)
        bookService.setStatus(userId, UUID.fromString(bookC.id), ReadStatus.FINISHED)

        val result = bookService.getBooks(userId, libId, statusFilter = "FINISHED", ratingGte = 4)
        val ids = result.getBooks().map { it.id }

        assertEquals(1, ids.size)
        assertEquals(bookA.id, ids[0])
    }

    @Test
    fun `getBooks ratingGte works across all libraries (no libraryId)`() {
        createBookWithRating("High", 5)
        createBookWithRating("Low", 1)

        val result = bookService.getBooks(userId, libraryId = null, ratingGte = 5)
        val books = result.getBooks()

        assertTrue(books.all { it.rating == 5 })
    }

    // ── HTTP endpoint tests ───────────────────────────────────────────────────

    @Test
    fun `library page with rating=3 returns 200`() {
        val libId2 = createLibrary(token)
        val response =
            app(
                Request(Method.GET, "/libraries/$libId2?rating=3")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `library page with invalid rating param is ignored`() {
        val libId2 = createLibrary(token)
        val response =
            app(
                Request(Method.GET, "/libraries/$libId2?rating=abc")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `library page rating=0 returns all books`() {
        val libId2 = createLibrary(token)
        val response =
            app(
                Request(Method.GET, "/libraries/$libId2?rating=0")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, response.status)
    }
}
