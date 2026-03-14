package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.config.Json
import org.booktower.config.TemplateRenderer
import org.booktower.filters.GlobalErrorFilter
import org.booktower.handlers.AppHandler
import org.booktower.models.BookListDto
import org.booktower.models.LibraryDto
import org.booktower.models.LoginResponse
import org.booktower.services.AuthService
import org.booktower.services.BookmarkService
import org.booktower.services.BookService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.booktower.services.PdfMetadataService
import org.booktower.services.UserSettingsService
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.then
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchIntegrationTest {
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
        val userSettingsService = UserSettingsService(jdbi)
        val pdfMetadataService = PdfMetadataService(jdbi, config.storage.coversPath)
        val appHandler = AppHandler(authService, libraryService, bookService, bookmarkService, userSettingsService, pdfMetadataService, jwtService, config.storage, TemplateRenderer())
        app = GlobalErrorFilter().then(appHandler.routes())
    }

    private fun uniqueUser() = "srch_${System.nanoTime()}"

    private fun registerAndGetToken(): String {
        val username = uniqueUser()
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
                .body("""{"name":"Search Lib","path":"./data/test-srch-${System.nanoTime()}"}"""),
        )
        return Json.mapper.readValue(response.bodyString(), LibraryDto::class.java).id
    }

    private fun addBook(token: String, libId: String, title: String, author: String?) {
        app(
            Request(Method.POST, "/api/books")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"$title","author":${if (author != null) "\"$author\"" else "null"},"description":null,"libraryId":"$libId"}"""),
        )
    }

    @Test
    fun `search by title finds matching book`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        addBook(token, libId, "The Pragmatic Programmer", "Dave Thomas")
        addBook(token, libId, "Clean Code", "Robert Martin")

        val response = app(
            Request(Method.GET, "/api/search?q=Pragmatic")
                .header("Cookie", "token=$token"),
        )

        assertEquals(Status.OK, response.status)
        val results = Json.mapper.readValue(response.bodyString(), BookListDto::class.java)
        assertEquals(1, results.total)
        assertEquals("The Pragmatic Programmer", results.getBooks()[0].title)
    }

    @Test
    fun `search by author finds matching book`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        addBook(token, libId, "Clean Code", "Robert Martin")
        addBook(token, libId, "The Hobbit", "Tolkien")

        val response = app(
            Request(Method.GET, "/api/search?q=Martin")
                .header("Cookie", "token=$token"),
        )

        assertEquals(Status.OK, response.status)
        val results = Json.mapper.readValue(response.bodyString(), BookListDto::class.java)
        assertEquals(1, results.total)
        assertEquals("Clean Code", results.getBooks()[0].title)
    }

    @Test
    fun `search is case-insensitive`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        addBook(token, libId, "Design Patterns", "Gang of Four")

        val response = app(
            Request(Method.GET, "/api/search?q=design+patterns")
                .header("Cookie", "token=$token"),
        )

        assertEquals(Status.OK, response.status)
        val results = Json.mapper.readValue(response.bodyString(), BookListDto::class.java)
        assertTrue(results.total >= 1)
    }

    @Test
    fun `search with no matches returns empty results`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        addBook(token, libId, "Known Book", "Known Author")

        val response = app(
            Request(Method.GET, "/api/search?q=xyznotfoundxyz")
                .header("Cookie", "token=$token"),
        )

        assertEquals(Status.OK, response.status)
        val results = Json.mapper.readValue(response.bodyString(), BookListDto::class.java)
        assertEquals(0, results.total)
        assertEquals(0, results.getBooks().size)
    }

    @Test
    fun `search without q parameter returns 400`() {
        val token = registerAndGetToken()
        val response = app(
            Request(Method.GET, "/api/search")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `search without auth returns 401`() {
        val response = app(Request(Method.GET, "/api/search?q=something"))
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `search results are isolated per user`() {
        val tokenA = registerAndGetToken()
        val tokenB = registerAndGetToken()
        val libId = createLibrary(tokenA)
        addBook(tokenA, libId, "Secret Book Alpha", null)

        val response = app(
            Request(Method.GET, "/api/search?q=Secret+Book+Alpha")
                .header("Cookie", "token=$tokenB"),
        )

        assertEquals(Status.OK, response.status)
        val results = Json.mapper.readValue(response.bodyString(), BookListDto::class.java)
        assertEquals(0, results.total)
    }

    @Test
    fun `search supports pagination`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        repeat(5) { i -> addBook(token, libId, "Kotlin Book $i", "Author") }

        val response = app(
            Request(Method.GET, "/api/search?q=Kotlin+Book&page=1&pageSize=3")
                .header("Cookie", "token=$token"),
        )

        assertEquals(Status.OK, response.status)
        val results = Json.mapper.readValue(response.bodyString(), BookListDto::class.java)
        assertEquals(5, results.total)
        assertEquals(3, results.getBooks().size)
        assertEquals(1, results.page)
        assertEquals(3, results.pageSize)
    }

    @Test
    fun `search second page returns remaining results`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        repeat(5) { i -> addBook(token, libId, "PagedBook $i", "Author") }

        val response = app(
            Request(Method.GET, "/api/search?q=PagedBook&page=2&pageSize=3")
                .header("Cookie", "token=$token"),
        )

        assertEquals(Status.OK, response.status)
        val results = Json.mapper.readValue(response.bodyString(), BookListDto::class.java)
        assertEquals(5, results.total)
        assertEquals(2, results.getBooks().size)
    }
}
