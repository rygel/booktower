package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BookSharingIntegrationTest : IntegrationTestBase() {
    @Test
    fun `share book generates token and another user can view shared book`() {
        val ownerToken = registerAndGetToken("share1a")
        val viewerToken = registerAndGetToken("share1b")
        val libId = createLibrary(ownerToken)
        val bookId = createBook(ownerToken, libId, "Shared Book")

        // Share the book
        val shareResp =
            app(
                Request(Method.POST, "/api/books/$bookId/share")
                    .header("Cookie", "token=$ownerToken"),
            )
        assertEquals(Status.OK, shareResp.status, "Share should succeed")
        val shareBody = shareResp.bodyString()
        assertTrue(shareBody.contains("shareToken"), "Response should contain shareToken")

        val shareToken =
            org.booktower.config.Json.mapper
                .readTree(shareBody)
                .get("shareToken")
                .asText()
        assertNotNull(shareToken, "Share token should not be null")

        // Another authenticated user can access the shared book
        val viewResp =
            app(
                Request(Method.GET, "/shared/book/$shareToken")
                    .header("Cookie", "token=$viewerToken"),
            )
        assertEquals(Status.OK, viewResp.status, "Authenticated user should access shared book")
        assertTrue(viewResp.bodyString().contains("Shared Book"), "Response should contain book title")
    }

    @Test
    fun `shared book requires authentication`() {
        val token = registerAndGetToken("share2")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Auth Test")

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

        // Unauthenticated access should return 401
        val anonResp = app(Request(Method.GET, "/shared/book/$shareToken"))
        assertEquals(Status.UNAUTHORIZED, anonResp.status, "Anonymous access should return 401")
    }

    @Test
    fun `unshare book removes access`() {
        val ownerToken = registerAndGetToken("share3a")
        val viewerToken = registerAndGetToken("share3b")
        val libId = createLibrary(ownerToken)
        val bookId = createBook(ownerToken, libId, "Unshare Test")

        // Share
        val shareResp =
            app(
                Request(Method.POST, "/api/books/$bookId/share")
                    .header("Cookie", "token=$ownerToken"),
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
                    .header("Cookie", "token=$ownerToken"),
            )
        assertEquals(Status.NO_CONTENT, unshareResp.status, "Unshare should succeed")

        // Shared endpoint should now return 404 even for authenticated users
        val viewResp =
            app(
                Request(Method.GET, "/shared/book/$shareToken")
                    .header("Cookie", "token=$viewerToken"),
            )
        assertEquals(Status.NOT_FOUND, viewResp.status, "Should return 404 after unshare")
    }

    @Test
    fun `get share token returns null when book is not shared`() {
        val token = registerAndGetToken("share4")
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
    fun `cannot share another users book`() {
        val token1 = registerAndGetToken("share5a")
        val token2 = registerAndGetToken("share5b")
        val libId = createLibrary(token1)
        val bookId = createBook(token1, libId, "User1 Book")

        val resp =
            app(
                Request(Method.POST, "/api/books/$bookId/share")
                    .header("Cookie", "token=$token2"),
            )
        assertEquals(Status.NOT_FOUND, resp.status, "Should not be able to share another user's book")
    }
}
