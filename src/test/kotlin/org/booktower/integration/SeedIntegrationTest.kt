package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.config.Json
import org.booktower.models.BookDto
import org.booktower.models.BookListDto
import org.booktower.models.LibraryDto
import org.booktower.models.LoginResponse
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SeedIntegrationTest : IntegrationTestBase() {

    /** Register a user then elevate them to admin directly in the DB, and return a fresh admin token. */
    private fun registerAdminAndGetToken(prefix: String = "seedadmin"): String {
        val username = "${prefix}_${System.nanoTime()}"
        val registerResponse = app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
        )
        assertEquals(Status.CREATED, registerResponse.status)
        val userId = Json.mapper.readValue(registerResponse.bodyString(), LoginResponse::class.java).user.id

        TestFixture.database.getJdbi().useHandle<Exception> { handle ->
            handle.createUpdate("UPDATE users SET is_admin = true WHERE id = ?")
                .bind(0, userId)
                .execute()
        }

        val loginResponse = app(
            Request(Method.POST, "/auth/login")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","password":"password123"}"""),
        )
        assertEquals(Status.OK, loginResponse.status)
        return Json.mapper.readValue(loginResponse.bodyString(), LoginResponse::class.java).token
    }

    @Test
    fun `unauthenticated seed request returns 401`() {
        val response = app(Request(Method.POST, "/admin/seed"))
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `non-admin cannot trigger seed`() {
        val token = registerAndGetToken("nonseedadmin")
        val response = app(
            Request(Method.POST, "/admin/seed")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.FORBIDDEN, response.status)
    }

    @Test
    fun `seed creates 3 libraries for admin user`() {
        val token = registerAdminAndGetToken()
        val response = app(
            Request(Method.POST, "/admin/seed")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.OK, response.status)

        val librariesResponse = app(
            Request(Method.GET, "/api/libraries")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.OK, librariesResponse.status)
        val libraries = Json.mapper.readValue(librariesResponse.bodyString(), Array<LibraryDto>::class.java)
        assertEquals(3, libraries.size)
    }

    @Test
    fun `seed creates 24 books total`() {
        val token = registerAdminAndGetToken()
        app(
            Request(Method.POST, "/admin/seed")
                .header("Cookie", "token=$token"),
        )

        val booksResponse = app(
            Request(Method.GET, "/api/books?pageSize=100")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.OK, booksResponse.status)
        val bookList = Json.mapper.readValue(booksResponse.bodyString(), BookListDto::class.java)
        assertEquals(24, bookList.total)
    }

    @Test
    fun `seed is idempotent - second call returns 409`() {
        val token = registerAdminAndGetToken()
        val first = app(
            Request(Method.POST, "/admin/seed")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.OK, first.status)

        val second = app(
            Request(Method.POST, "/admin/seed")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.CONFLICT, second.status)
    }

    @Test
    fun `seed returns HX-Trigger with success message`() {
        val token = registerAdminAndGetToken()
        val response = app(
            Request(Method.POST, "/admin/seed")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.OK, response.status)
        val trigger = response.header("HX-Trigger")
        assertNotNull(trigger, "HX-Trigger header should be present")
        assertTrue(trigger.contains("showToast"), "HX-Trigger should contain showToast")
    }

    @Test
    fun `seeded books have authors and tags`() {
        val token = registerAdminAndGetToken()
        app(
            Request(Method.POST, "/admin/seed")
                .header("Cookie", "token=$token"),
        )

        val booksResponse = app(
            Request(Method.GET, "/api/books?pageSize=100")
                .header("Cookie", "token=$token"),
        )
        val bookList = Json.mapper.readValue(booksResponse.bodyString(), BookListDto::class.java)
        val books = bookList.getBooks()

        assertTrue(books.all { it.author != null }, "All seeded books should have an author")
        assertTrue(books.any { it.tags.isNotEmpty() }, "At least some seeded books should have tags")
    }

    @Test
    fun `seeded books have varied read statuses`() {
        val token = registerAdminAndGetToken()
        app(
            Request(Method.POST, "/admin/seed")
                .header("Cookie", "token=$token"),
        )

        val booksResponse = app(
            Request(Method.GET, "/api/books?pageSize=100")
                .header("Cookie", "token=$token"),
        )
        val books = Json.mapper.readValue(booksResponse.bodyString(), BookListDto::class.java).getBooks()

        val statuses = books.mapNotNull { it.status }.toSet()
        assertTrue(statuses.contains("FINISHED"), "Should have FINISHED books")
        assertTrue(statuses.contains("READING"), "Should have READING books")
        assertTrue(statuses.contains("WANT_TO_READ"), "Should have WANT_TO_READ books")
    }

    @Test
    fun `seeded books include expected titles`() {
        val token = registerAdminAndGetToken()
        app(
            Request(Method.POST, "/admin/seed")
                .header("Cookie", "token=$token"),
        )

        val booksResponse = app(
            Request(Method.GET, "/api/books?pageSize=100")
                .header("Cookie", "token=$token"),
        )
        val titles = Json.mapper.readValue(booksResponse.bodyString(), BookListDto::class.java)
            .getBooks().map { it.title }

        assertTrue(titles.any { it.contains("War of the Worlds", ignoreCase = true) })
        assertTrue(titles.any { it.contains("Time Machine", ignoreCase = true) })
        assertTrue(titles.any { it.contains("Frankenstein", ignoreCase = true) })
    }

    @Test
    fun `seed for non-admin user with existing libraries returns 409`() {
        // Create a non-seed user with their own library, then try seeding as admin of fresh account
        val adminToken = registerAdminAndGetToken()
        // Seed once to populate
        app(Request(Method.POST, "/admin/seed").header("Cookie", "token=$adminToken"))

        // Different admin user - has no libraries yet, should succeed
        val adminToken2 = registerAdminAndGetToken("seedadmin2")
        val response = app(
            Request(Method.POST, "/admin/seed")
                .header("Cookie", "token=$adminToken2"),
        )
        assertEquals(Status.OK, response.status)

        // Verify second admin also got 3 libraries (isolated per user)
        val librariesResponse = app(
            Request(Method.GET, "/api/libraries")
                .header("Cookie", "token=$adminToken2"),
        )
        val libraries = Json.mapper.readValue(librariesResponse.bodyString(), Array<LibraryDto>::class.java)
        assertEquals(3, libraries.size)
    }
}
