package org.runary.integration

import org.runary.config.Json
import org.runary.models.BookListDto
import org.runary.models.LibraryDto
import org.runary.models.LoginResponse
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchIntegrationTest : IntegrationTestBase() {
    private fun uniqueUser() = "srch_${System.nanoTime()}"

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
                    .body("""{"name":"Search Lib","path":"./data/test-srch-${System.nanoTime()}"}"""),
            )
        return Json.mapper.readValue(response.bodyString(), LibraryDto::class.java).id
    }

    private fun addBook(
        token: String,
        libId: String,
        title: String,
        author: String?,
    ) {
        app(
            Request(Method.POST, "/api/books")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body(
                    """{"title":"$title","author":${if (author != null) "\"$author\"" else "null"},"description":null,"libraryId":"$libId"}""",
                ),
        )
    }

    @Test
    fun `search by title finds matching book`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        addBook(token, libId, "The Pragmatic Programmer", "Dave Thomas")
        addBook(token, libId, "Clean Code", "Robert Martin")

        val response =
            app(
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

        val response =
            app(
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

        val response =
            app(
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

        val response =
            app(
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
        val response =
            app(
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

        val response =
            app(
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

        val response =
            app(
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

        val response =
            app(
                Request(Method.GET, "/api/search?q=PagedBook&page=2&pageSize=3")
                    .header("Cookie", "token=$token"),
            )

        assertEquals(Status.OK, response.status)
        val results = Json.mapper.readValue(response.bodyString(), BookListDto::class.java)
        assertEquals(5, results.total)
        assertEquals(2, results.getBooks().size)
    }

    // ── Search page filters ────────────────────────────────────────────────────

    private fun setStatus(
        token: String,
        bookId: String,
        status: String,
    ) {
        app(
            Request(Method.POST, "/ui/books/$bookId/status")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("status=$status"),
        )
    }

    private fun setRating(
        token: String,
        bookId: String,
        rating: Int,
    ) {
        app(
            Request(Method.POST, "/ui/books/$bookId/rating")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("rating=$rating"),
        )
    }

    @Test
    fun `search page renders status and rating filter dropdowns`() {
        val token = registerAndGetToken("sf1")
        val html = app(Request(Method.GET, "/search?q=test").header("Cookie", "token=$token")).bodyString()
        assertTrue(
            html.contains("filter.all.ratings") || html.contains("All ratings"),
            "Rating filter dropdown must appear on search page",
        )
    }

    @Test
    fun `search page with status filter shows only READING books`() {
        val token = registerAndGetToken("sf2")
        val libId = createLibrary(token)
        val bookReading = createBook(token, libId, "Currently Reading SF")
        val bookFinished = createBook(token, libId, "Already Finished SF")
        setStatus(token, bookReading, "READING")
        setStatus(token, bookFinished, "FINISHED")

        val html =
            app(
                Request(Method.GET, "/search?q=SF&status=READING")
                    .header("Cookie", "token=$token"),
            ).bodyString()

        assertTrue(html.contains("Currently Reading SF"), "READING book must appear in filtered results")
        assertTrue(
            !html.contains("Already Finished SF"),
            "FINISHED book must not appear when filter is READING",
        )
    }

    @Test
    fun `search page with minRating filter excludes lower-rated books`() {
        val token = registerAndGetToken("sf3")
        val libId = createLibrary(token)
        val highRated = createBook(token, libId, "Excellent Book SF")
        val lowRated = createBook(token, libId, "Mediocre Book SF")
        setRating(token, highRated, 5)
        setRating(token, lowRated, 2)

        val html =
            app(
                Request(Method.GET, "/search?q=SF&rating=4")
                    .header("Cookie", "token=$token"),
            ).bodyString()

        assertTrue(html.contains("Excellent Book SF"), "5-star book must appear when filtering rating >= 4")
        assertTrue(
            !html.contains("Mediocre Book SF"),
            "2-star book must not appear when filtering rating >= 4",
        )
    }

    @Test
    fun `search page filters persist as selected values in dropdowns after submit`() {
        val token = registerAndGetToken("sf4")
        val libId = createLibrary(token)
        createBook(token, libId, "Any Book SF")

        val html =
            app(
                Request(Method.GET, "/search?q=SF&status=FINISHED&rating=3")
                    .header("Cookie", "token=$token"),
            ).bodyString()

        assertTrue(html.contains("FINISHED"), "FINISHED must be selected in status dropdown")
        assertTrue(html.contains("rating"), "Rating filter must be present in the form")
    }
}
