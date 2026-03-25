package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.runary.config.Json

class MultiAuthorIntegrationTest : IntegrationTestBase() {
    @Test
    fun `PUT authors sets author list on a book`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "War and Peace")

        val resp =
            app(
                Request(Method.PUT, "/api/books/$bookId/authors")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"authors":["Leo Tolstoy"]}"""),
            )
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertTrue(tree.get("authors")?.isArray == true)
        assertEquals("Leo Tolstoy", tree.get("authors")?.get(0)?.asText())
    }

    @Test
    fun `PUT authors with multiple authors preserves order`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Good Omens")

        app(
            Request(Method.PUT, "/api/books/$bookId/authors")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"authors":["Terry Pratchett","Neil Gaiman"]}"""),
        )

        val bookResp = app(Request(Method.GET, "/api/books/$bookId").header("Cookie", "token=$token"))
        assertEquals(Status.OK, bookResp.status)
        val tree = Json.mapper.readTree(bookResp.bodyString())
        val authors = tree.get("authors")
        assertTrue(authors?.isArray == true)
        assertEquals(2, authors?.size())
        assertEquals("Terry Pratchett", authors?.get(0)?.asText())
        assertEquals("Neil Gaiman", authors?.get(1)?.asText())
    }

    @Test
    fun `PUT authors syncs legacy author field to primary author`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Dune")

        app(
            Request(Method.PUT, "/api/books/$bookId/authors")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"authors":["Frank Herbert","Brian Herbert"]}"""),
        )

        val bookResp = app(Request(Method.GET, "/api/books/$bookId").header("Cookie", "token=$token"))
        val tree = Json.mapper.readTree(bookResp.bodyString())
        // Legacy author field should be synced to the first author
        assertEquals("Frank Herbert", tree.get("author")?.asText())
    }

    @Test
    fun `PUT authors replaces previous authors`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Foundation")

        app(
            Request(Method.PUT, "/api/books/$bookId/authors")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"authors":["Isaac Asimov","Somebody Else"]}"""),
        )

        app(
            Request(Method.PUT, "/api/books/$bookId/authors")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"authors":["Isaac Asimov"]}"""),
        )

        val bookResp = app(Request(Method.GET, "/api/books/$bookId").header("Cookie", "token=$token"))
        val tree = Json.mapper.readTree(bookResp.bodyString())
        assertEquals(1, tree.get("authors")?.size())
    }

    @Test
    fun `PUT authors on nonexistent book returns 404`() {
        val token = registerAndGetToken()
        val resp =
            app(
                Request(Method.PUT, "/api/books/00000000-0000-0000-0000-000000000000/authors")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"authors":["Nobody"]}"""),
            )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `PUT authors requires authentication`() {
        val resp =
            app(
                Request(Method.PUT, "/api/books/00000000-0000-0000-0000-000000000000/authors")
                    .header("Content-Type", "application/json")
                    .body("""{"authors":["Nobody"]}"""),
            )
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `GET book list includes authors field`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Hyperion")

        app(
            Request(Method.PUT, "/api/books/$bookId/authors")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"authors":["Dan Simmons"]}"""),
        )

        val resp = app(Request(Method.GET, "/api/books?libraryId=$libId").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        val books = tree.get("books") ?: tree
        val book = if (books.isArray) books.first { it.get("id")?.asText() == bookId } else books
        assertTrue(book.get("authors")?.isArray == true)
        assertEquals("Dan Simmons", book.get("authors")?.get(0)?.asText())
    }
}
