package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import org.runary.config.Json
import org.runary.models.BookDto
import org.runary.models.BookmarkDto
import org.runary.models.LibraryDto
import org.runary.models.LoginResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookmarkIntegrationTest : IntegrationTestBase() {
    private fun uniqueUser() = "bm_${System.nanoTime()}"

    private fun registerAndGetToken(): String {
        val username = uniqueUser()
        val response =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","email":"$username@test.com","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
            )
        return Json.mapper.readValue(response.bodyString(), LoginResponse::class.java).token
    }

    private fun createLibrary(token: String): String {
        val response =
            app(
                Request(Method.POST, "/api/libraries")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"BM Lib","path":"./data/test-bm-${System.nanoTime()}"}"""),
            )
        return Json.mapper.readValue(response.bodyString(), LibraryDto::class.java).id
    }

    private fun createBook(
        token: String,
        libId: String,
    ): String {
        val response =
            app(
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

        val response =
            app(
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

        val response =
            app(
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

        val response =
            app(
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

        val createResponse =
            app(
                Request(Method.POST, "/api/bookmarks")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"bookId":"$bookId","page":5,"title":null,"note":null}"""),
            )
        val bookmarkId = Json.mapper.readValue(createResponse.bodyString(), BookmarkDto::class.java).id

        val deleteResponse =
            app(
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

        val createResponse =
            app(
                Request(Method.POST, "/api/bookmarks")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"bookId":"$bookId","page":7,"title":"To delete","note":null}"""),
            )
        val bookmarkId = Json.mapper.readValue(createResponse.bodyString(), BookmarkDto::class.java).id

        app(Request(Method.DELETE, "/api/bookmarks/$bookmarkId").header("Cookie", "token=$token"))

        val listResponse =
            app(
                Request(Method.GET, "/api/bookmarks?bookId=$bookId")
                    .header("Cookie", "token=$token"),
            )
        val bookmarks = Json.mapper.readValue(listResponse.bodyString(), Array<BookmarkDto>::class.java)
        assertEquals(0, bookmarks.size)
    }

    @Test
    fun `delete non-existent bookmark returns 404`() {
        val token = registerAndGetToken()
        val response =
            app(
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

        val response =
            app(
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
        val response =
            app(
                Request(Method.GET, "/api/bookmarks")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `create bookmark without auth returns 401`() {
        val response =
            app(
                Request(Method.POST, "/api/bookmarks")
                    .header("Content-Type", "application/json")
                    .body("""{"bookId":"00000000-0000-0000-0000-000000000000","page":1,"title":null,"note":null}"""),
            )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `list bookmarks without auth returns 401`() {
        val response =
            app(
                Request(Method.GET, "/api/bookmarks?bookId=00000000-0000-0000-0000-000000000000"),
            )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `delete bookmark without auth returns 401`() {
        val response =
            app(
                Request(Method.DELETE, "/api/bookmarks/00000000-0000-0000-0000-000000000000"),
            )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `create bookmark with page 0 is valid`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response =
            app(
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

        val response =
            app(
                Request(Method.POST, "/api/bookmarks")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"bookId":"$bookId","page":-1,"title":null,"note":null}"""),
            )

        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `save audio bookmark with timestamp page stores and retrieves correctly`() {
        // For single-file audio books the page value is seconds into the track
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response =
            app(
                Request(Method.POST, "/api/bookmarks")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"bookId":"$bookId","page":90,"title":"Intro done","note":null}"""),
            )

        assertEquals(Status.CREATED, response.status)
        val created = Json.mapper.readValue(response.bodyString(), BookmarkDto::class.java)
        assertEquals(90, created.page)

        val listResponse =
            app(
                Request(Method.GET, "/api/bookmarks?bookId=$bookId")
                    .header("Cookie", "token=$token"),
            )
        val bookmarks = Json.mapper.readValue(listResponse.bodyString(), Array<BookmarkDto>::class.java)
        assertEquals(1, bookmarks.size)
        assertEquals(90, bookmarks[0].page)
    }

    @Test
    fun `save multi-chapter audiobook packed page bookmark stores and retrieves correctly`() {
        // For multi-chapter audiobooks the page value is packed: trackIndex * 1_000_000 + offsetSeconds
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val packed = 1 * 1_000_000 + 300 // track 1, 300 seconds in

        val response =
            app(
                Request(Method.POST, "/api/bookmarks")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"bookId":"$bookId","page":$packed,"title":"Chapter 2 start","note":null}"""),
            )

        assertEquals(Status.CREATED, response.status)
        val created = Json.mapper.readValue(response.bodyString(), BookmarkDto::class.java)
        assertEquals(packed, created.page)
        // Verify packed encoding decodes correctly
        assertEquals(1, created.page / 1_000_000)
        assertEquals(300, created.page % 1_000_000)

        val listResponse =
            app(
                Request(Method.GET, "/api/bookmarks?bookId=$bookId")
                    .header("Cookie", "token=$token"),
            )
        val bookmarks = Json.mapper.readValue(listResponse.bodyString(), Array<BookmarkDto>::class.java)
        assertEquals(1, bookmarks.size)
        assertEquals(packed, bookmarks[0].page)
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
        val response =
            app(
                Request(Method.GET, "/api/bookmarks?bookId=$bookId")
                    .header("Cookie", "token=$tokenB"),
            )

        // B gets back empty list (bookmarks filtered by user_id in SQL)
        assertEquals(Status.OK, response.status)
        val bookmarks = Json.mapper.readValue(response.bodyString(), Array<BookmarkDto>::class.java)
        assertTrue(bookmarks.none { it.title == "Private" })
    }
}
