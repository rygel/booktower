package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import org.runary.config.Json
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for GET /api/export — user data JSON export.
 */
class ExportIntegrationTest : IntegrationTestBase() {
    @Test
    fun `export returns 401 when unauthenticated`() {
        val resp = app(Request(Method.GET, "/api/export"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `export returns valid JSON with user structure`() {
        val token = registerAndGetToken("export_basic")

        val resp =
            app(
                Request(Method.GET, "/api/export")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, resp.status)
        val body = Json.mapper.readTree(resp.bodyString())
        assertNotNull(body.get("username"), "Should have username")
        assertNotNull(body.get("email"), "Should have email")
        assertNotNull(body.get("memberSince"), "Should have memberSince")
        assertNotNull(body.get("libraries"), "Should have libraries array")
        assertTrue(body.get("libraries").isArray)
    }

    @Test
    fun `export includes libraries and books`() {
        val token = registerAndGetToken("export_data")
        val libId = createLibrary(token)
        createBook(token, libId, "My Exported Book")

        val resp =
            app(
                Request(Method.GET, "/api/export")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, resp.status)
        val body = Json.mapper.readTree(resp.bodyString())
        val libraries = body.get("libraries")
        assertTrue(libraries.size() >= 1, "Should have at least 1 library")
        val books = libraries[0].get("books")
        assertTrue(books.size() >= 1, "Library should have at least 1 book")
        assertEquals("My Exported Book", books[0].get("title").asText())
    }

    @Test
    fun `export includes bookmarks in books`() {
        val token = registerAndGetToken("export_bm")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        // Create a bookmark
        app(
            Request(Method.POST, "/api/bookmarks")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"bookId":"$bookId","page":42,"title":"My Mark","note":null}"""),
        )

        val resp =
            app(
                Request(Method.GET, "/api/export")
                    .header("Cookie", "token=$token"),
            )
        val body = Json.mapper.readTree(resp.bodyString())
        val books = body.get("libraries")[0].get("books")
        val bookmarks = books[0].get("bookmarks")
        assertTrue(bookmarks.isArray)
        assertEquals(1, bookmarks.size(), "Should have 1 exported bookmark")
        assertEquals(42, bookmarks[0].get("page").asInt())
        assertEquals("My Mark", bookmarks[0].get("title").asText())
    }

    @Test
    fun `export content-disposition header set for download`() {
        val token = registerAndGetToken("export_dl")

        val resp =
            app(
                Request(Method.GET, "/api/export")
                    .header("Cookie", "token=$token"),
            )
        val disposition = resp.header("Content-Disposition") ?: ""
        assertTrue(disposition.contains("attachment"), "Should be a download attachment")
        assertTrue(disposition.contains(".json"), "Filename should end with .json")
    }

    @Test
    fun `export user A data does not include user B data`() {
        val tokenA = registerAndGetToken("export_isoa")
        val tokenB = registerAndGetToken("export_isob")

        val libA = createLibrary(tokenA)
        createBook(tokenA, libA, "User A Secret Book")

        val libB = createLibrary(tokenB)
        createBook(tokenB, libB, "User B Private Book")

        val exportA =
            app(
                Request(Method.GET, "/api/export")
                    .header("Cookie", "token=$tokenA"),
            )
        val bodyA = exportA.bodyString()
        assertTrue(bodyA.contains("User A Secret Book"), "User A's export should have their book")
        assertTrue(!bodyA.contains("User B Private Book"), "User A's export must not contain User B's book")
    }
}
