package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.config.Json
import org.booktower.config.TemplateRenderer
import org.booktower.filters.GlobalErrorFilter
import org.booktower.handlers.AppHandler
import org.booktower.models.BookDto
import org.booktower.models.BookListDto
import org.booktower.models.LibraryDto
import org.booktower.models.LoginResponse
import org.booktower.services.AuthService
import org.booktower.services.BookmarkService
import org.booktower.services.BookService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.then
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BookIntegrationTest {
    private lateinit var app: HttpHandler

    @BeforeEach
    fun setup() {
        val config = TestFixture.config
        val jdbi = TestFixture.database.getJdbi()
        val jwtService = JwtService(config.security)
        val authService = AuthService(jdbi, jwtService)
        val libraryService = LibraryService(jdbi, config.storage)
        val bookService = BookService(jdbi, config.storage)
        val bookmarkService = BookmarkService(jdbi)
        val appHandler = AppHandler(authService, libraryService, bookService, bookmarkService, jwtService, TemplateRenderer())
        app = GlobalErrorFilter().then(appHandler.routes())
    }

    private fun registerAndGetToken(): String {
        val username = "user_${System.nanoTime()}"
        val response = app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
        )
        return Json.mapper.readValue(response.bodyString(), LoginResponse::class.java).token
    }

    private fun createLibrary(token: String): String {
        val response = app(
            Request(Method.POST, "/api/libraries")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"name":"Test Lib","path":"./data/test-book-${System.nanoTime()}"}"""),
        )
        return Json.mapper.readValue(response.bodyString(), LibraryDto::class.java).id
    }

    private fun authedRequest(method: Method, uri: String, token: String): Request =
        Request(method, uri).header("Cookie", "token=$token")

    @Test
    fun `list books without auth returns 401`() {
        assertEquals(Status.UNAUTHORIZED, app(Request(Method.GET, "/api/books")).status)
    }

    @Test
    fun `create book returns 201 with book data`() {
        val token = registerAndGetToken()
        val libraryId = createLibrary(token)

        val response = app(
            authedRequest(Method.POST, "/api/books", token)
                .header("Content-Type", "application/json")
                .body("""{"title":"Test Book","author":"Author","description":"A book","libraryId":"$libraryId"}"""),
        )

        assertEquals(Status.CREATED, response.status)
        val book = Json.mapper.readValue(response.bodyString(), BookDto::class.java)
        assertEquals("Test Book", book.title)
        assertEquals("Author", book.author)
        assertEquals("A book", book.description)
        assertNotNull(book.id)
    }

    @Test
    fun `create book without auth returns 401`() {
        val response = app(
            Request(Method.POST, "/api/books")
                .header("Content-Type", "application/json")
                .body("""{"title":"x","author":null,"description":null,"libraryId":"some-id"}"""),
        )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `create book with empty title returns 400`() {
        val token = registerAndGetToken()
        val libraryId = createLibrary(token)

        val response = app(
            authedRequest(Method.POST, "/api/books", token)
                .header("Content-Type", "application/json")
                .body("""{"title":"","author":null,"description":null,"libraryId":"$libraryId"}"""),
        )
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `create book with invalid libraryId returns 400`() {
        val token = registerAndGetToken()

        val response = app(
            authedRequest(Method.POST, "/api/books", token)
                .header("Content-Type", "application/json")
                .body("""{"title":"Book","author":null,"description":null,"libraryId":"not-a-uuid"}"""),
        )
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `create book with non-existent library returns 400`() {
        val token = registerAndGetToken()

        val response = app(
            authedRequest(Method.POST, "/api/books", token)
                .header("Content-Type", "application/json")
                .body("""{"title":"Book","author":null,"description":null,"libraryId":"00000000-0000-0000-0000-000000000000"}"""),
        )
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `created book appears in list`() {
        val token = registerAndGetToken()
        val libraryId = createLibrary(token)

        app(
            authedRequest(Method.POST, "/api/books", token)
                .header("Content-Type", "application/json")
                .body("""{"title":"Listed Book","author":"Author","description":null,"libraryId":"$libraryId"}"""),
        )

        val response = app(authedRequest(Method.GET, "/api/books?libraryId=$libraryId", token))
        assertEquals(Status.OK, response.status)

        val bookList = Json.mapper.readValue(response.bodyString(), BookListDto::class.java)
        assertEquals(1, bookList.total)
        assertEquals("Listed Book", bookList.getBooks().first().title)
    }

    @Test
    fun `get book by id returns book`() {
        val token = registerAndGetToken()
        val libraryId = createLibrary(token)

        val createResponse = app(
            authedRequest(Method.POST, "/api/books", token)
                .header("Content-Type", "application/json")
                .body("""{"title":"Fetchable","author":"Auth","description":null,"libraryId":"$libraryId"}"""),
        )
        val bookId = Json.mapper.readValue(createResponse.bodyString(), BookDto::class.java).id

        val response = app(authedRequest(Method.GET, "/api/books/$bookId", token))
        assertEquals(Status.OK, response.status)

        val book = Json.mapper.readValue(response.bodyString(), BookDto::class.java)
        assertEquals("Fetchable", book.title)
    }

    @Test
    fun `get non-existent book returns 404`() {
        val token = registerAndGetToken()
        val response = app(authedRequest(Method.GET, "/api/books/00000000-0000-0000-0000-000000000000", token))
        assertEquals(Status.NOT_FOUND, response.status)
    }

    @Test
    fun `list books with pagination`() {
        val token = registerAndGetToken()
        val libraryId = createLibrary(token)

        for (i in 1..5) {
            app(
                authedRequest(Method.POST, "/api/books", token)
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Book $i","author":null,"description":null,"libraryId":"$libraryId"}"""),
            )
        }

        val page1 = app(authedRequest(Method.GET, "/api/books?libraryId=$libraryId&page=1&pageSize=2", token))
        val bookList1 = Json.mapper.readValue(page1.bodyString(), BookListDto::class.java)
        assertEquals(5, bookList1.total)
        assertEquals(2, bookList1.getBooks().size)
        assertEquals(1, bookList1.page)
        assertEquals(2, bookList1.pageSize)

        val page3 = app(authedRequest(Method.GET, "/api/books?libraryId=$libraryId&page=3&pageSize=2", token))
        val bookList3 = Json.mapper.readValue(page3.bodyString(), BookListDto::class.java)
        assertEquals(1, bookList3.getBooks().size)
    }

    @Test
    fun `list books without libraryId returns all user books`() {
        val token = registerAndGetToken()
        val lib1 = createLibrary(token)

        app(
            authedRequest(Method.POST, "/api/books", token)
                .header("Content-Type", "application/json")
                .body("""{"title":"All Books Test","author":null,"description":null,"libraryId":"$lib1"}"""),
        )

        val response = app(authedRequest(Method.GET, "/api/books", token))
        assertEquals(Status.OK, response.status)

        val bookList = Json.mapper.readValue(response.bodyString(), BookListDto::class.java)
        assertTrue(bookList.total >= 1)
    }

    @Test
    fun `recent books without auth returns 401`() {
        assertEquals(Status.UNAUTHORIZED, app(Request(Method.GET, "/api/recent")).status)
    }

    @Test
    fun `recent books returns 200 for authenticated user`() {
        val token = registerAndGetToken()
        val response = app(authedRequest(Method.GET, "/api/recent", token))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `book with null optional fields is created successfully`() {
        val token = registerAndGetToken()
        val libraryId = createLibrary(token)

        val response = app(
            authedRequest(Method.POST, "/api/books", token)
                .header("Content-Type", "application/json")
                .body("""{"title":"Minimal Book","author":null,"description":null,"libraryId":"$libraryId"}"""),
        )

        assertEquals(Status.CREATED, response.status)
        val book = Json.mapper.readValue(response.bodyString(), BookDto::class.java)
        assertEquals("Minimal Book", book.title)
        assertEquals(null, book.author)
    }

    @Test
    fun `library book count reflects created books`() {
        val token = registerAndGetToken()
        val libraryId = createLibrary(token)

        for (i in 1..3) {
            app(
                authedRequest(Method.POST, "/api/books", token)
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Count Book $i","author":null,"description":null,"libraryId":"$libraryId"}"""),
            )
        }

        val response = app(authedRequest(Method.GET, "/api/libraries", token))
        val libraries = Json.mapper.readValue(response.bodyString(), Array<LibraryDto>::class.java)
        val lib = libraries.find { it.id == libraryId }
        assertNotNull(lib)
        assertEquals(3, lib.bookCount)
    }
}
