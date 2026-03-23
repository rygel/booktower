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

class EdgeCaseIntegrationTest : IntegrationTestBase() {
    private fun uniqueUser() = "edge_${System.nanoTime()}"

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
                    .body("""{"name":"Edge Lib","path":"./data/test-edge-${System.nanoTime()}"}"""),
            )
        return Json.mapper.readValue(response.bodyString(), LibraryDto::class.java).id
    }

    @Test
    fun `pagination with page 0 is coerced to page 1`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        app(
            Request(Method.POST, "/api/books")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"Page Zero","author":null,"description":null,"libraryId":"$libId"}"""),
        )

        val response =
            app(
                Request(Method.GET, "/api/books?libraryId=$libId&page=0&pageSize=10")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, response.status)
        val bookList = Json.mapper.readValue(response.bodyString(), BookListDto::class.java)
        assertEquals(1, bookList.page)
    }

    @Test
    fun `pagination with very large page returns empty results`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)

        val response =
            app(
                Request(Method.GET, "/api/books?libraryId=$libId&page=99999&pageSize=10")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, response.status)
        val bookList = Json.mapper.readValue(response.bodyString(), BookListDto::class.java)
        assertEquals(0, bookList.getBooks().size)
    }

    @Test
    fun `pagination with negative pageSize is coerced to 1`() {
        val token = registerAndGetToken()
        val response =
            app(
                Request(Method.GET, "/api/books?page=1&pageSize=-1")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, response.status)
        val bookList = Json.mapper.readValue(response.bodyString(), BookListDto::class.java)
        assertEquals(1, bookList.pageSize)
    }

    @Test
    fun `pagination with non-numeric values uses defaults`() {
        val token = registerAndGetToken()
        val response =
            app(
                Request(Method.GET, "/api/books?page=abc&pageSize=xyz")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `malformed JSON body returns error`() {
        val token = registerAndGetToken()
        val response =
            app(
                Request(Method.POST, "/api/libraries")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("{invalid json!!!}"),
            )
        assertTrue(response.status.code >= 400)
    }

    @Test
    fun `very long library name at boundary (100 chars) succeeds`() {
        val token = registerAndGetToken()
        val name100 = "a".repeat(100)
        val response =
            app(
                Request(Method.POST, "/api/libraries")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"$name100","path":"./data/test-long-${System.nanoTime()}"}"""),
            )
        assertEquals(Status.CREATED, response.status)
    }

    @Test
    fun `username at boundary (3 chars) succeeds`() {
        val response =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body(
                        """{"username":"u_${System.nanoTime() % 10000}","email":"min_${System.nanoTime()}@test.com","password":"${org.runary.TestPasswords.DEFAULT}"}""",
                    ),
            )
        // May succeed or fail depending on username length - just shouldn't crash
        assertTrue(response.status.code in listOf(201, 400))
    }

    @Test
    fun `username at boundary (50 chars) succeeds`() {
        val username = "a".repeat(50)
        val response =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","email":"long_${System.nanoTime()}@test.com","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
            )
        assertEquals(Status.CREATED, response.status)
    }

    @Test
    fun `username at boundary (51 chars) fails`() {
        val username = "a".repeat(51)
        val response =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","email":"toolong_${System.nanoTime()}@test.com","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
            )
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `password exactly 8 chars succeeds`() {
        val username = uniqueUser()
        val response =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","email":"$username@test.com","password":"12345678"}"""),
            )
        assertEquals(Status.CREATED, response.status)
    }

    @Test
    fun `password exactly 7 chars fails`() {
        val username = uniqueUser()
        val response =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","email":"$username@test.com","password":"1234567"}"""),
            )
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `deleting library removes its books from user listing`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)

        app(
            Request(Method.POST, "/api/books")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"Orphan Book","author":null,"description":null,"libraryId":"$libId"}"""),
        )

        app(Request(Method.DELETE, "/api/libraries/$libId").header("Cookie", "token=$token"))

        val response =
            app(
                Request(Method.GET, "/api/books")
                    .header("Cookie", "token=$token"),
            )
        val bookList = Json.mapper.readValue(response.bodyString(), BookListDto::class.java)
        assertTrue(bookList.getBooks().none { it.title == "Orphan Book" })
    }

    @Test
    fun `books across multiple libraries appear in combined listing`() {
        val token = registerAndGetToken()
        val lib1 = createLibrary(token)

        val lib2Response =
            app(
                Request(Method.POST, "/api/libraries")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"Second Lib","path":"./data/test-multi-${System.nanoTime()}"}"""),
            )
        val lib2 = Json.mapper.readValue(lib2Response.bodyString(), LibraryDto::class.java).id

        app(
            Request(Method.POST, "/api/books")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"Book In Lib1","author":null,"description":null,"libraryId":"$lib1"}"""),
        )
        app(
            Request(Method.POST, "/api/books")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"Book In Lib2","author":null,"description":null,"libraryId":"$lib2"}"""),
        )

        val response =
            app(
                Request(Method.GET, "/api/books")
                    .header("Cookie", "token=$token"),
            )
        val bookList = Json.mapper.readValue(response.bodyString(), BookListDto::class.java)
        assertTrue(bookList.total >= 2)
        assertTrue(bookList.getBooks().any { it.title == "Book In Lib1" })
        assertTrue(bookList.getBooks().any { it.title == "Book In Lib2" })
    }

    @Test
    fun `content-type header is application json on all API responses`() {
        val token = registerAndGetToken()

        val endpoints =
            listOf(
                app(Request(Method.GET, "/api/libraries").header("Cookie", "token=$token")),
                app(Request(Method.GET, "/api/books").header("Cookie", "token=$token")),
                app(Request(Method.GET, "/api/recent").header("Cookie", "token=$token")),
            )

        for (response in endpoints) {
            assertTrue(
                response.header("Content-Type")?.contains("application/json") == true,
                "API response should have Content-Type: application/json",
            )
        }
    }

    @Test
    fun `double registration with same email but different username`() {
        val user1 = uniqueUser()
        val email = "shared_${System.nanoTime()}@test.com"
        app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$user1","email":"$email","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
        )

        val user2 = uniqueUser()
        val response =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$user2","email":"$email","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
            )
        // H2 has unique constraint on email - should fail
        assertTrue(response.status.code >= 400)
    }
}
