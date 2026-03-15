package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.models.CreateBookRequest
import org.booktower.models.CreateLibraryRequest
import org.booktower.models.CreateUserRequest
import org.booktower.services.AnalyticsService
import org.booktower.services.AuthService
import org.booktower.services.BookService
import org.booktower.services.GoodreadsImportService
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoodreadsImportIntegrationTest : IntegrationTestBase() {

    private lateinit var importService: GoodreadsImportService
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
        importService = GoodreadsImportService(bookService)

        val result = authService.register(
            CreateUserRequest("gr_${System.nanoTime()}", "gr_${System.nanoTime()}@test.com", "password123"),
        )
        val loginResponse = result.getOrThrow()
        userId = jwtService.extractUserId(loginResponse.token)!!
        token = loginResponse.token
        libId = libraryService.createLibrary(userId, CreateLibraryRequest("GR Lib", "./data/gr-${System.nanoTime()}")).id
    }

    private fun csv(vararg rows: String): String {
        val header = "Book Id,Title,Author,Author l-f,Additional Authors,ISBN,ISBN13,My Rating,Average Rating,Publisher,Binding,Number of Pages,Year Published,Original Publication Year,Date Read,Date Added,Bookshelves,Bookshelves with positions,Exclusive Shelf,My Review,Spoiler,Private Notes,Read Count,Owned Copies"
        return (listOf(header) + rows.toList()).joinToString("\n")
    }

    private fun row(
        title: String,
        author: String = "",
        isbn: String = "",
        rating: Int = 0,
        publisher: String = "",
        pages: Int = 0,
        year: String = "",
        shelf: String = "",
        bookshelves: String = "",
    ) = "1,\"$title\",\"$author\",,,$isbn,,$rating,4.0,\"$publisher\",,${if (pages > 0) pages else ""},$year,,,,\"$bookshelves\",,\"$shelf\",,,,"

    // ── Service-level unit tests ──────────────────────────────────────────────

    @Test
    fun `import creates books from CSV rows`() {
        val csv = csv(
            row("The Hobbit", "J.R.R. Tolkien"),
            row("Dune", "Frank Herbert"),
        )
        val result = importService.import(userId, libId, csv.byteInputStream())

        assertEquals(2, result.imported)
        assertEquals(0, result.skipped)
        assertEquals(0, result.errors)

        val books = bookService.getBooks(userId, libId).getBooks()
        assertEquals(2, books.size)
        val titles = books.map { it.title }.toSet()
        assertTrue(titles.contains("The Hobbit"))
        assertTrue(titles.contains("Dune"))
    }

    @Test
    fun `import sets author on book`() {
        val csv = csv(row("Foundation", "Isaac Asimov"))
        importService.import(userId, libId, csv.byteInputStream())

        val book = bookService.getBooks(userId, libId).getBooks().first()
        assertEquals("Isaac Asimov", book.author)
    }

    @Test
    fun `import maps exclusive shelf=read to FINISHED status`() {
        val csv = csv(row("Book A", shelf = "read"))
        importService.import(userId, libId, csv.byteInputStream())

        val book = bookService.getBooks(userId, libId).getBooks().first()
        assertEquals("FINISHED", book.status)
    }

    @Test
    fun `import maps exclusive shelf=currently-reading to READING status`() {
        val csv = csv(row("Book B", shelf = "currently-reading"))
        importService.import(userId, libId, csv.byteInputStream())

        val book = bookService.getBooks(userId, libId).getBooks().first()
        assertEquals("READING", book.status)
    }

    @Test
    fun `import maps exclusive shelf=to-read to WANT_TO_READ status`() {
        val csv = csv(row("Book C", shelf = "to-read"))
        importService.import(userId, libId, csv.byteInputStream())

        val book = bookService.getBooks(userId, libId).getBooks().first()
        assertEquals("WANT_TO_READ", book.status)
    }

    @Test
    fun `import sets rating 1-5 on book`() {
        val csv = csv(row("Rated Book", rating = 4))
        importService.import(userId, libId, csv.byteInputStream())

        val book = bookService.getBook(userId, bookService.getBooks(userId, libId).getBooks().first().id.let { UUID.fromString(it) })
        assertNotNull(book)
        assertEquals(4, book!!.rating)
    }

    @Test
    fun `import ignores rating=0 (unrated)`() {
        val csv = csv(row("Unrated Book", rating = 0))
        importService.import(userId, libId, csv.byteInputStream())

        val book = bookService.getBook(userId, bookService.getBooks(userId, libId).getBooks().first().id.let { UUID.fromString(it) })
        assertNull(book!!.rating)
    }

    @Test
    fun `import maps bookshelves to tags excluding shelf names`() {
        val csv = csv(row("Tagged Book", shelf = "read", bookshelves = "read, sci-fi, favorites"))
        importService.import(userId, libId, csv.byteInputStream())

        val book = bookService.getBooks(userId, libId).getBooks().first()
        val tags = bookService.getBook(userId, UUID.fromString(book.id))!!.tags
        assertTrue(tags.contains("sci-fi"))
        assertTrue(tags.contains("favorites"))
        assertTrue(!tags.contains("read"))
    }

    @Test
    fun `import skips rows with blank title`() {
        val csv = csv(
            row(""),
            row("Valid Title"),
        )
        val result = importService.import(userId, libId, csv.byteInputStream())

        assertEquals(1, result.imported)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `import returns error count on invalid libraryId`() {
        val csv = csv(row("Book"))
        val result = importService.import(userId, "non-existent-lib", csv.byteInputStream())

        assertEquals(0, result.imported)
        assertEquals(1, result.errors)
    }

    @Test
    fun `import handles empty CSV gracefully`() {
        val csv = csv() // header only
        val result = importService.import(userId, libId, csv.byteInputStream())

        assertEquals(0, result.imported)
        assertEquals(0, result.errors)
    }

    @Test
    fun `import handles malformed CSV gracefully`() {
        val result = importService.import(userId, libId, "not,a,valid,goodreads,csv".byteInputStream())

        // Missing "title" column → all rows skipped or error, no crash
        assertEquals(0, result.imported)
    }

    @Test
    fun `import strips Goodreads ISBN wrapping`() {
        val csv = csv(row("ISBN Book", isbn = "=\"9780441013593\""))
        importService.import(userId, libId, csv.byteInputStream())

        val book = bookService.getBook(userId, bookService.getBooks(userId, libId).getBooks().first().id.let { UUID.fromString(it) })
        assertEquals("9780441013593", book!!.isbn)
    }

    // ── HTTP endpoint tests ───────────────────────────────────────────────────

    @Test
    fun `POST api import goodreads returns 200 with import result`() {
        val libId2 = createLibrary(token)
        val csv = csv(row("HTTP Import Book", "Author"))

        val response = app(
            Request(Method.POST, "/api/import/goodreads?libraryId=$libId2")
                .header("Cookie", "token=$token")
                .header("Content-Type", "text/csv")
                .body(csv),
        )

        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("imported"))
    }

    @Test
    fun `POST api import goodreads returns 401 without auth`() {
        val response = app(
            Request(Method.POST, "/api/import/goodreads?libraryId=$libId")
                .header("Content-Type", "text/csv")
                .body(csv()),
        )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `POST api import goodreads returns 400 without libraryId`() {
        val response = app(
            Request(Method.POST, "/api/import/goodreads")
                .header("Cookie", "token=$token")
                .header("Content-Type", "text/csv")
                .body(csv()),
        )
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `profile page returns 200 with import section`() {
        val response = app(
            Request(Method.GET, "/profile")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("import-library") || response.bodyString().contains("Goodreads"))
    }
}
