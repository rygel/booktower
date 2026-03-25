package org.runary.integration

import org.runary.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PublicShelfIntegrationTest : IntegrationTestBase() {
    private fun createShelf(
        token: String,
        name: String = "My Shelf",
    ): String {
        val resp =
            app(
                Request(Method.POST, "/api/shelves")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"$name","ruleType":"STATUS","ruleValue":"FINISHED"}"""),
            )
        return Json.mapper
            .readTree(resp.bodyString())
            .get("id")
            .asText()
    }

    @Test
    fun `GET api shelves returns list`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/api/shelves").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertTrue(Json.mapper.readTree(resp.bodyString()).isArray)
    }

    @Test
    fun `shelf is private by default`() {
        val token = registerAndGetToken()
        val shelfId = createShelf(token)
        val resp = app(Request(Method.GET, "/api/shelves").header("Cookie", "token=$token"))
        val shelf = Json.mapper.readTree(resp.bodyString()).first { it.get("id").asText() == shelfId }
        assertFalse(shelf.get("isPublic").asBoolean())
        assertTrue(shelf.get("shareToken").isNull)
    }

    @Test
    fun `POST share generates share token`() {
        val token = registerAndGetToken()
        val shelfId = createShelf(token)
        val resp =
            app(
                Request(Method.POST, "/api/shelves/$shelfId/share")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, resp.status)
        val body = Json.mapper.readTree(resp.bodyString())
        assertTrue(body.get("isPublic").asBoolean())
        assertNotNull(body.get("shareToken").asText())
        assertTrue(body.get("shareToken").asText().isNotBlank())
    }

    @Test
    fun `GET public shelf without auth returns 200`() {
        val token = registerAndGetToken()
        val shelfId = createShelf(token)
        val shareResp =
            app(
                Request(Method.POST, "/api/shelves/$shelfId/share")
                    .header("Cookie", "token=$token"),
            )
        val shareToken =
            Json.mapper
                .readTree(shareResp.bodyString())
                .get("shareToken")
                .asText()

        val publicResp = app(Request(Method.GET, "/shared/shelf/$shareToken").header("Cookie", "token=$token"))
        assertEquals(Status.OK, publicResp.status)
        val body = Json.mapper.readTree(publicResp.bodyString())
        assertNotNull(body.get("name"))
        assertNotNull(body.get("books"))
    }

    @Test
    fun `GET public shelf includes books matching shelf rule`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Finished Book")
        // Mark book as FINISHED
        app(
            Request(Method.POST, "/api/books/$bookId/status")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"status":"FINISHED"}"""),
        )

        val shelfId = createShelf(token, "Finished Books")
        val shareToken =
            Json.mapper
                .readTree(
                    app(Request(Method.POST, "/api/shelves/$shelfId/share").header("Cookie", "token=$token")).bodyString(),
                ).get("shareToken")
                .asText()

        val resp = app(Request(Method.GET, "/shared/shelf/$shareToken").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val books = Json.mapper.readTree(resp.bodyString()).get("books")
        assertTrue(books.any { it.get("id").asText() == bookId })
    }

    @Test
    fun `GET unknown share token returns 404`() {
        val token = registerAndGetToken()
        val resp =
            app(
                Request(Method.GET, "/shared/shelf/00000000-0000-0000-0000-000000000000")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `DELETE share unshares the shelf`() {
        val token = registerAndGetToken()
        val shelfId = createShelf(token)
        val shareToken =
            Json.mapper
                .readTree(
                    app(Request(Method.POST, "/api/shelves/$shelfId/share").header("Cookie", "token=$token")).bodyString(),
                ).get("shareToken")
                .asText()

        val deleteResp =
            app(
                Request(Method.DELETE, "/api/shelves/$shelfId/share")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.NO_CONTENT, deleteResp.status)

        val publicResp = app(Request(Method.GET, "/shared/shelf/$shareToken").header("Cookie", "token=$token"))
        assertEquals(Status.NOT_FOUND, publicResp.status)
    }

    @Test
    fun `cannot access another users private shelf`() {
        val token1 = registerAndGetToken("user1")
        val token2 = registerAndGetToken("user2")
        val shelfId = createShelf(token1)

        // user2 tries to share user1's shelf — should fail (404)
        val resp =
            app(
                Request(Method.POST, "/api/shelves/$shelfId/share")
                    .header("Cookie", "token=$token2"),
            )
        assertEquals(Status.NOT_FOUND, resp.status)
    }
}
