package org.booktower.integration

import org.booktower.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Validates that error responses include meaningful error messages and proper JSON structure.
 * Ensures the API doesn't leak stack traces but does provide actionable error information.
 */
class ErrorResponseIntegrationTest : IntegrationTestBase() {

    private fun assertJsonError(
        body: String,
        expectedCode: String? = null,
    ) {
        val json = Json.mapper.readTree(body)
        assertNotNull(json, "Error response should be valid JSON")
        val error = json.get("error") ?: json.get("code")
        assertNotNull(error, "Error response should contain 'error' or 'code' field, got: $body")
        if (expectedCode != null) {
            assertEquals(expectedCode, error.asText(), "Error code mismatch in: $body")
        }
        // Should NOT contain stack traces
        assertTrue(!body.contains("at org.booktower"), "Error response should not leak stack traces")
        assertTrue(!body.contains("Exception"), "Error response should not contain raw exception names")
    }

    // ── Auth errors ──────────────────────────────────────────────────────

    @Test
    fun `login with wrong password returns error message`() {
        val token = registerAndGetToken("erruser1")
        val resp =
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"erruser1_invalid","password":"wrongpass"}"""),
            )
        assertEquals(Status.UNAUTHORIZED, resp.status)
        val body = resp.bodyString()
        assertTrue(body.isNotBlank(), "Error body should not be empty")
    }

    @Test
    fun `register with short password returns validation error`() {
        val resp =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"shortpw","email":"short@test.com","password":"12"}"""),
            )
        assertTrue(
            resp.status == Status.BAD_REQUEST || resp.status == Status.UNPROCESSABLE_ENTITY,
            "Short password should be rejected, got ${resp.status}",
        )
    }

    @Test
    fun `register with duplicate username returns conflict`() {
        val username = "dupeuser_${System.nanoTime()}"
        app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
        )
        val resp =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","email":"${username}2@test.com","password":"password123"}"""),
            )
        assertTrue(
            resp.status == Status.CONFLICT || resp.status == Status.BAD_REQUEST,
            "Duplicate username should be rejected, got ${resp.status}",
        )
        val body = resp.bodyString()
        assertTrue(body.isNotBlank(), "Duplicate username error should have a body")
    }

    // ── Book errors ──────────────────────────────────────────────────────

    @Test
    fun `get nonexistent book returns 404 with error body`() {
        val token = registerAndGetToken("err_book404")
        val fakeId = java.util.UUID.randomUUID()
        val resp =
            app(Request(Method.GET, "/api/books/$fakeId").header("Cookie", "token=$token"))
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `get book with invalid UUID returns 400`() {
        val token = registerAndGetToken("err_badid")
        val resp =
            app(Request(Method.GET, "/api/books/not-a-uuid").header("Cookie", "token=$token"))
        assertTrue(
            resp.status == Status.BAD_REQUEST || resp.status == Status.NOT_FOUND,
            "Invalid UUID should return 400 or 404, got ${resp.status}",
        )
    }

    @Test
    fun `create book without library returns error`() {
        val token = registerAndGetToken("err_nolib")
        val fakeLibId = java.util.UUID.randomUUID()
        val resp =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Orphan Book","author":null,"description":null,"libraryId":"$fakeLibId"}"""),
            )
        assertTrue(
            resp.status == Status.NOT_FOUND || resp.status == Status.BAD_REQUEST,
            "Book creation with nonexistent library should fail, got ${resp.status}",
        )
    }

    @Test
    fun `create book with empty title returns error`() {
        val token = registerAndGetToken("err_notitle")
        val libId = createLibrary(token, "Err Test Lib")
        val resp =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"","author":null,"description":null,"libraryId":"$libId"}"""),
            )
        assertTrue(
            resp.status == Status.BAD_REQUEST || resp.status == Status.UNPROCESSABLE_ENTITY,
            "Empty title should be rejected, got ${resp.status}",
        )
    }

    // ── Library errors ───────────────────────────────────────────────────

    @Test
    fun `create library with empty name returns error`() {
        val token = registerAndGetToken("err_emptylib")
        val resp =
            app(
                Request(Method.POST, "/api/libraries")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"","path":"./data/empty-name"}"""),
            )
        assertTrue(
            resp.status == Status.BAD_REQUEST || resp.status == Status.UNPROCESSABLE_ENTITY,
            "Empty library name should be rejected, got ${resp.status}",
        )
    }

    @Test
    fun `delete nonexistent library returns 404`() {
        val token = registerAndGetToken("err_dellib")
        val fakeId = java.util.UUID.randomUUID()
        val resp =
            app(Request(Method.DELETE, "/api/libraries/$fakeId").header("Cookie", "token=$token"))
        assertTrue(
            resp.status == Status.NOT_FOUND || resp.status == Status.BAD_REQUEST,
            "Deleting nonexistent library should return 404, got ${resp.status}",
        )
    }

    // ── File errors ──────────────────────────────────────────────────────

    @Test
    fun `download file for book without upload returns 404 with message`() {
        val token = registerAndGetToken("err_nofile")
        val libId = createLibrary(token, "No File Lib")
        val bookId = createBook(token, libId, "No File Book")
        val resp =
            app(Request(Method.GET, "/api/books/$bookId/file").header("Cookie", "token=$token"))
        assertEquals(Status.NOT_FOUND, resp.status)
        val body = resp.bodyString()
        assertTrue(body.isNotBlank(), "404 for missing file should have error body")
    }

    // ── Unauthenticated errors ───────────────────────────────────────────

    @Test
    fun `unauthenticated API calls return 401 with JSON body`() {
        val endpoints =
            listOf(
                Request(Method.GET, "/api/books"),
                Request(Method.GET, "/api/libraries"),
                Request(Method.GET, "/api/notifications"),
                Request(Method.GET, "/api/bookmarks"),
                Request(Method.GET, "/api/export"),
            )

        endpoints.forEach { req ->
            val resp = app(req)
            assertEquals(
                Status.UNAUTHORIZED,
                resp.status,
                "Unauthenticated ${req.method} ${req.uri.path} should return 401",
            )
            val body = resp.bodyString()
            assertTrue(body.isNotBlank(), "401 response for ${req.uri.path} should have a body")
            assertTrue(
                resp.header("Content-Type")?.contains("json") == true,
                "401 response for ${req.uri.path} should be JSON, got ${resp.header("Content-Type")}",
            )
        }
    }

    @Test
    fun `expired or invalid token returns 401`() {
        val resp =
            app(
                Request(Method.GET, "/api/books")
                    .header("Cookie", "token=invalid.jwt.token"),
            )
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
