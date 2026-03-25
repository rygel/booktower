package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.runary.config.Json

class CategoriesIntegrationTest : IntegrationTestBase() {
    @Test
    fun `PUT categories sets categories on a book`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp =
            app(
                Request(Method.PUT, "/api/books/$bookId/categories")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"categories":["Fiction","Mystery"]}"""),
            )
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        val cats = tree.get("categories")
        assertTrue(cats?.isArray == true && cats.size() == 2)
    }

    @Test
    fun `GET book returns categories field`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        app(
            Request(Method.PUT, "/api/books/$bookId/categories")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"categories":["Science Fiction"]}"""),
        )

        val resp = app(Request(Method.GET, "/api/books/$bookId").header("Cookie", "token=$token"))
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals("Science Fiction", tree.get("categories")?.get(0)?.asText())
    }

    @Test
    fun `PUT categories replaces previous categories`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        app(
            Request(Method.PUT, "/api/books/$bookId/categories")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"categories":["Fantasy","Adventure"]}"""),
        )
        app(
            Request(Method.PUT, "/api/books/$bookId/categories")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"categories":["Fantasy"]}"""),
        )

        val resp = app(Request(Method.GET, "/api/books/$bookId").header("Cookie", "token=$token"))
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals(1, tree.get("categories")?.size())
        assertEquals("Fantasy", tree.get("categories")?.get(0)?.asText())
    }

    @Test
    fun `categories are isolated per user`() {
        val token1 = registerAndGetToken("u1")
        val libId = createLibrary(token1)
        val bookId = createBook(token1, libId)

        app(
            Request(Method.PUT, "/api/books/$bookId/categories")
                .header("Cookie", "token=$token1")
                .header("Content-Type", "application/json")
                .body("""{"categories":["Horror"]}"""),
        )

        // Second user creates their own book — shouldn't see user1's categories
        val token2 = registerAndGetToken("u2")
        val lib2 = createLibrary(token2)
        val book2 = createBook(token2, lib2)
        val resp = app(Request(Method.GET, "/api/books/$book2").header("Cookie", "token=$token2"))
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals(0, tree.get("categories")?.size() ?: 0)
    }

    @Test
    fun `PUT categories on nonexistent book returns 404`() {
        val token = registerAndGetToken()
        val resp =
            app(
                Request(Method.PUT, "/api/books/00000000-0000-0000-0000-000000000000/categories")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"categories":["Anything"]}"""),
            )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `PUT categories requires authentication`() {
        val resp =
            app(
                Request(Method.PUT, "/api/books/00000000-0000-0000-0000-000000000000/categories")
                    .header("Content-Type", "application/json")
                    .body("""{"categories":["X"]}"""),
            )
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
