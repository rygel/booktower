package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookSharingIntegrationTest : IntegrationTestBase() {
    @Test
    fun `share book generates token and public endpoint returns book data`() {
        val token = registerAndGetToken("share1")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Shared Book")

        // Share the book
        val shareResp =
            app(
                Request(Method.POST, "/api/books/$bookId/share")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, shareResp.status, "Share should succeed")
        val shareBody = shareResp.bodyString()
        assertTrue(shareBody.contains("shareToken"), "Response should contain shareToken")
        assertTrue(shareBody.contains("shareUrl"), "Response should contain shareUrl")

        // Extract the token
        val shareToken =
            org.booktower.config.Json.mapper
                .readTree(shareBody)
                .get("shareToken")
                .asText()
        assertNotNull(shareToken, "Share token should not be null")

        // Access the public endpoint (no auth)
        val publicResp = app(Request(Method.GET, "/public/book/$shareToken"))
        assertEquals(Status.OK, publicResp.status, "Public book endpoint should return 200")
        val publicBody = publicResp.bodyString()
        assertTrue(publicBody.contains("Shared Book"), "Public response should contain book title")
    }

    @Test
    fun `unshare book removes public access`() {
        val token = registerAndGetToken("share2")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Unshare Test")

        // Share
        val shareResp =
            app(
                Request(Method.POST, "/api/books/$bookId/share")
                    .header("Cookie", "token=$token"),
            )
        val shareToken =
            org.booktower.config.Json.mapper
                .readTree(shareResp.bodyString())
                .get("shareToken")
                .asText()

        // Unshare
        val unshareResp =
            app(
                Request(Method.DELETE, "/api/books/$bookId/share")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.NO_CONTENT, unshareResp.status, "Unshare should succeed")

        // Public endpoint should now return 404
        val publicResp = app(Request(Method.GET, "/public/book/$shareToken"))
        assertEquals(Status.NOT_FOUND, publicResp.status, "Public endpoint should return 404 after unshare")
    }

    @Test
    fun `get share token returns null when book is not shared`() {
        val token = registerAndGetToken("share3")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Not Shared")

        val resp =
            app(
                Request(Method.GET, "/api/books/$bookId/share")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, resp.status)
        val body =
            org.booktower.config.Json.mapper
                .readTree(resp.bodyString())
        assertTrue(body.get("shareToken").isNull, "Share token should be null for unshared book")
    }

    @Test
    fun `share book returns 404 for nonexistent book`() {
        val token = registerAndGetToken("share4")
        val resp =
            app(
                Request(Method.POST, "/api/books/00000000-0000-0000-0000-000000000000/share")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `cannot access other users shared book management`() {
        val token1 = registerAndGetToken("share5a")
        val token2 = registerAndGetToken("share5b")
        val libId = createLibrary(token1)
        val bookId = createBook(token1, libId, "User1 Book")

        // User2 tries to share user1's book
        val resp =
            app(
                Request(Method.POST, "/api/books/$bookId/share")
                    .header("Cookie", "token=$token2"),
            )
        assertEquals(Status.NOT_FOUND, resp.status, "Should not be able to share another user's book")
    }
}
