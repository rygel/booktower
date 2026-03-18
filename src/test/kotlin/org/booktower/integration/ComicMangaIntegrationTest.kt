package org.booktower.integration

import org.booktower.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests for comic/manga reading direction (PUT /api/books/{id}/reading-direction).
 */
class ComicMangaIntegrationTest : IntegrationTestBase() {
    @Test
    fun `PUT reading-direction sets rtl on a book`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Naruto")

        val resp =
            app(
                Request(Method.PUT, "/api/books/$bookId/reading-direction")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"direction":"rtl"}"""),
            )
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals("rtl", tree.get("direction")?.asText())
    }

    @Test
    fun `PUT reading-direction sets ltr on a book`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Asterix")

        val resp =
            app(
                Request(Method.PUT, "/api/books/$bookId/reading-direction")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"direction":"ltr"}"""),
            )
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals("ltr", tree.get("direction")?.asText())
    }

    @Test
    fun `reading direction is returned in book GET response`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "One Piece")

        app(
            Request(Method.PUT, "/api/books/$bookId/reading-direction")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"direction":"rtl"}"""),
        )

        val bookResp = app(Request(Method.GET, "/api/books/$bookId").header("Cookie", "token=$token"))
        assertEquals(Status.OK, bookResp.status)
        val tree = Json.mapper.readTree(bookResp.bodyString())
        assertEquals("rtl", tree.get("readingDirection")?.asText())
    }

    @Test
    fun `PUT reading-direction on nonexistent book returns 404`() {
        val token = registerAndGetToken()
        val resp =
            app(
                Request(Method.PUT, "/api/books/00000000-0000-0000-0000-000000000000/reading-direction")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"direction":"rtl"}"""),
            )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `PUT reading-direction requires authentication`() {
        val resp =
            app(
                Request(Method.PUT, "/api/books/00000000-0000-0000-0000-000000000000/reading-direction")
                    .header("Content-Type", "application/json")
                    .body("""{"direction":"rtl"}"""),
            )
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `PUT reading-direction without direction field returns 400`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        val resp =
            app(
                Request(Method.PUT, "/api/books/$bookId/reading-direction")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{}"""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `user cannot change reading direction of another user s book`() {
        val token1 = registerAndGetToken("usr1")
        val libId = createLibrary(token1)
        val bookId = createBook(token1, libId)

        val token2 = registerAndGetToken("usr2")
        val resp =
            app(
                Request(Method.PUT, "/api/books/$bookId/reading-direction")
                    .header("Cookie", "token=$token2")
                    .header("Content-Type", "application/json")
                    .body("""{"direction":"rtl"}"""),
            )
        // Not found because the query scopes to the requesting user's libraries
        assertEquals(Status.NOT_FOUND, resp.status)
    }
}
