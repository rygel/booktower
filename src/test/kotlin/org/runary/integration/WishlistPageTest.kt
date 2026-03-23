package org.runary.integration

import org.runary.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * End-to-end test for the Wishlist page.
 */
class WishlistPageTest : IntegrationTestBase() {
    @Test
    fun `wishlist page requires authentication`() {
        val resp = app(Request(Method.GET, "/wishlist"))
        assertTrue(resp.status == Status.FOUND || resp.status == Status.SEE_OTHER || resp.status == Status.UNAUTHORIZED)
    }

    @Test
    fun `wishlist page renders empty state`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/wishlist").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("add-wish-btn"), "Page should have add button")
    }

    @Test
    fun `wishlist page shows added item`() {
        val token = registerAndGetToken()
        app(
            Request(Method.POST, "/api/wishlist")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"The Name of the Wind","author":"Patrick Rothfuss","notes":"Recommended by Alex"}"""),
        )

        val resp = app(Request(Method.GET, "/wishlist").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val html = resp.bodyString()
        assertTrue(html.contains("The Name of the Wind"), "Should show book title")
        assertTrue(html.contains("Patrick Rothfuss"), "Should show author")
        assertTrue(html.contains("Recommended by Alex"), "Should show notes")
    }

    @Test
    fun `wishlist page has delete button`() {
        val token = registerAndGetToken()
        val createResp =
            app(
                Request(Method.POST, "/api/wishlist")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Deletable Book"}"""),
            )
        val itemId =
            Json.mapper
                .readTree(createResp.bodyString())
                .get("id")
                .asText()

        val resp = app(Request(Method.GET, "/wishlist").header("Cookie", "token=$token"))
        val html = resp.bodyString()
        assertTrue(html.contains("wish-delete"), "Should have delete button")
        assertTrue(html.contains(itemId), "Should have item ID")
    }

    @Test
    fun `sidebar contains wishlist link`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertTrue(resp.bodyString().contains("href=\"/wishlist\""), "Sidebar should have /wishlist link")
    }
}
