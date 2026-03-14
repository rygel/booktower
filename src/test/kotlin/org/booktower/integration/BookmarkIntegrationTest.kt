package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.config.Json
import org.booktower.config.TemplateRenderer
import org.booktower.filters.GlobalErrorFilter
import org.booktower.handlers.AppHandler
import org.booktower.models.BookDto
import org.booktower.models.BookmarkDto
import org.booktower.models.LibraryDto
import org.booktower.models.LoginResponse
import org.booktower.services.AuthService
import org.booktower.services.BookmarkService
import org.booktower.services.PdfMetadataService
import org.booktower.services.UserSettingsService
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
import kotlin.test.assertTrue

class BookmarkIntegrationTest : IntegrationTestBase() {

    private fun uniqueUser() = "bm_${System.nanoTime()}"

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
                .body("""{"name":"BM Lib","path":"./data/test-bm-${System.nanoTime()}"}"""),
        )
        return Json.mapper.readValue(response.bodyString(), LibraryDto::class.java).id
    }

    private fun createBook(token: String, libId: String): String {
        val response = app(
            Request(Method.POST, "/api/books")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"Bookmark Book ${System.nanoTime()}","author":null,"description":null,"libraryId":"$libId"}"""),
        )
        return Json.mapper.readValue(response.bodyString(), BookDto::class.java).id
    }

    @Test
    fun `create bookmark returns 201 with bookmark data`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response = app(
            Request(Method.POST, "/api/bookmarks")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"bookId":"$bookId","page":42,"title":"Chapter 5","note":"Great quote here"}"""),
        )

        assertEquals(Status.CREATED, response.status)
        val bookmark = Json.mapper.readValue(response.bodyString(), BookmarkDto::class.java)
        assertEquals(42, bookmark.page)
        assertEquals("Chapter 5", bookmark.title)
        assertEquals("Great quote here", bookmark.note)
    }

    @Test
    fun `list bookmarks returns empty array when none exist`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response = app(
            Request(Method.GET, "/api/bookmarks?bookId=$bookId")
                .header("Cookie", "token=$token"),
        )

        assertEquals(Status.OK, response.status)
        val bookmarks = Json.mapper.readValue(response.bodyString(), Array<BookmarkDto>::class.java)
        assertEquals(0, bookmarks.size)
    }

    @Test
    fun `list bookmarks returns created bookmarks ordered by page`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        app(
            Request(Method.POST, "/api/bookmarks")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"bookId":"$bookId","page":100,"title":"Later","note":null}"""),
        )
        app(
            Request(Method.POST, "/api/bookmarks")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"bookId":"$bookId","page":10,"title":"Earlier","note":null}"""),
        )

        val response = app(
            Request(Method.GET, "/api/bookmarks?bookId=$bookId")
                .header("Cookie", "token=$token"),
        )

        assertEquals(Status.OK, response.status)
        val bookmarks = Json.mapper.readValue(response.bodyString(), Array<BookmarkDto>::class.java)
        assertEquals(2, bookmarks.size)
        assertEquals(10, bookmarks[0].page)
        assertEquals(100, bookmarks[1].page)
    }

    @Test
    fun `delete bookmark returns 200`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val createResponse = app(
            Request(Method.POST, "/api/bookmarks")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"bookId":"$bookId","page":5,"title":null,"note":null}"""),
        )
        val bookmarkId = Json.mapper.readValue(createResponse.bodyString(), BookmarkDto::class.java).id

        val deleteResponse = app(
            Request(Method.DELETE, "/api/bookmarks/$bookmarkId")
                .header("Cookie", "token=$token"),
        )

        assertEquals(Status.OK, deleteResponse.status)
    }

    @Test
    fun `delete bookmark removes it from listing`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val createResponse = app(
            Request(Method.POST, "/api/bookmarks")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"bookId":"$bookId","page":7,"title":"To delete","note":null}"""),
        )
        val bookmarkId = Json.mapper.readValue(createResponse.bodyString(), BookmarkDto::class.java).id

        app(Request(Method.DELETE, "/api/bookmarks/$bookmarkId").header("Cookie", "token=$token"))

        val listResponse = app(
            Request(Method.GET, "/api/bookmarks?bookId=$bookId")
                .header("Cookie", "token=$token"),
        )
        val bookmarks = Json.mapper.readValue(listResponse.bodyString(), Array<BookmarkDto>::class.java)
        assertEquals(0, bookmarks.size)
    }

    @Test
    fun `delete non-existent bookmark returns 404`() {
        val token = registerAndGetToken()
        val response = app(
            Request(Method.DELETE, "/api/bookmarks/00000000-0000-0000-0000-000000000000")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.NOT_FOUND, response.status)
    }

    @Test
    fun `create bookmark for book not owned by user returns 400`() {
        val tokenA = registerAndGetToken()
        val tokenB = registerAndGetToken()
        val libId = createLibrary(tokenA)
        val bookId = createBook(tokenA, libId)

        val response = app(
            Request(Method.POST, "/api/bookmarks")
                .header("Cookie", "token=$tokenB")
                .header("Content-Type", "application/json")
                .body("""{"bookId":"$bookId","page":1,"title":null,"note":null}"""),
        )

        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `list bookmarks without bookId returns 400`() {
        val token = registerAndGetToken()
        val response = app(
            Request(Method.GET, "/api/bookmarks")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `create bookmark without auth returns 401`() {
        val response = app(
            Request(Method.POST, "/api/bookmarks")
                .header("Content-Type", "application/json")
                .body("""{"bookId":"00000000-0000-0000-0000-000000000000","page":1,"title":null,"note":null}"""),
        )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `list bookmarks without auth returns 401`() {
        val response = app(
            Request(Method.GET, "/api/bookmarks?bookId=00000000-0000-0000-0000-000000000000"),
        )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `delete bookmark without auth returns 401`() {
        val response = app(
            Request(Method.DELETE, "/api/bookmarks/00000000-0000-0000-0000-000000000000"),
        )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `create bookmark with page 0 is valid`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response = app(
            Request(Method.POST, "/api/bookmarks")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"bookId":"$bookId","page":0,"title":"Cover","note":null}"""),
        )

        assertEquals(Status.CREATED, response.status)
    }

    @Test
    fun `create bookmark with negative page returns 400`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response = app(
            Request(Method.POST, "/api/bookmarks")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"bookId":"$bookId","page":-1,"title":null,"note":null}"""),
        )

        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `user B cannot see user A bookmarks`() {
        val tokenA = registerAndGetToken()
        val tokenB = registerAndGetToken()
        val libId = createLibrary(tokenA)
        val bookId = createBook(tokenA, libId)

        app(
            Request(Method.POST, "/api/bookmarks")
                .header("Cookie", "token=$tokenA")
                .header("Content-Type", "application/json")
                .body("""{"bookId":"$bookId","page":99,"title":"Private","note":null}"""),
        )

        // User B queries with A's bookId — should get empty (or bad request if no access)
        val response = app(
            Request(Method.GET, "/api/bookmarks?bookId=$bookId")
                .header("Cookie", "token=$tokenB"),
        )

        // B gets back empty list (bookmarks filtered by user_id in SQL)
        assertEquals(Status.OK, response.status)
        val bookmarks = Json.mapper.readValue(response.bodyString(), Array<BookmarkDto>::class.java)
        assertTrue(bookmarks.none { it.title == "Private" })
    }
}
