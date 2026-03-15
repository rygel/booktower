package org.booktower.integration

import org.booktower.config.Json
import org.booktower.models.UpdateBookRequest
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthorBrowserIntegrationTest : IntegrationTestBase() {

    private fun setAuthor(token: String, bookId: String, author: String) {
        app(
            Request(Method.PUT, "/api/books/$bookId")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(
                    UpdateBookRequest("Book $bookId", author, null),
                )),
        )
    }

    @Test
    fun `author list page returns 200`() {
        val token = registerAndGetToken("author")
        val response = app(Request(Method.GET, "/authors").header("Cookie", "token=$token"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("BookTower"))
    }

    @Test
    fun `author list shows author after book is assigned`() {
        val token = registerAndGetToken("author")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Dune")
        setAuthor(token, bookId, "Frank Herbert")

        val body = app(Request(Method.GET, "/authors").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("Frank Herbert"))
    }

    @Test
    fun `author list is empty when no books have an author`() {
        val token = registerAndGetToken("author")
        createBook(token, createLibrary(token), "No Author Book")

        val body = app(Request(Method.GET, "/authors").header("Cookie", "token=$token")).bodyString()
        assertFalse(body.contains("No Author Book"))
    }

    @Test
    fun `author detail page returns 200`() {
        val token = registerAndGetToken("author")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Foundation")
        setAuthor(token, bookId, "Isaac Asimov")

        val response = app(
            Request(Method.GET, "/authors/Isaac%20Asimov").header("Cookie", "token=$token"),
        )
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("Isaac Asimov"))
    }

    @Test
    fun `author detail page lists all books by that author`() {
        val token = registerAndGetToken("author")
        val libId = createLibrary(token)
        val b1 = createBook(token, libId, "Book One")
        val b2 = createBook(token, libId, "Book Two")
        val b3 = createBook(token, libId, "Other Author Book")
        setAuthor(token, b1, "Author Alpha")
        setAuthor(token, b2, "Author Alpha")
        setAuthor(token, b3, "Author Beta")

        val body = app(
            Request(Method.GET, "/authors/Author%20Alpha").header("Cookie", "token=$token"),
        ).bodyString()
        assertTrue(body.contains(b1))
        assertTrue(body.contains(b2))
        assertFalse(body.contains(b3))
    }

    @Test
    fun `authors are isolated between users`() {
        val tokenA = registerAndGetToken("author")
        val tokenB = registerAndGetToken("author")
        val libA = createLibrary(tokenA)
        val bookA = createBook(tokenA, libA, "Private Book")
        setAuthor(tokenA, bookA, "PrivateAuthor")

        val bodyB = app(Request(Method.GET, "/authors").header("Cookie", "token=$tokenB")).bodyString()
        assertFalse(bodyB.contains("PrivateAuthor"))
    }

    @Test
    fun `author list page requires authentication`() {
        val response = app(Request(Method.GET, "/authors"))
        assertTrue(response.status.code in listOf(302, 303, 401))
    }

    @Test
    fun `author detail page requires authentication`() {
        val response = app(Request(Method.GET, "/authors/Someone"))
        assertTrue(response.status.code in listOf(302, 303, 401))
    }

    @Test
    fun `multiple authors appear in list`() {
        val token = registerAndGetToken("author")
        val libId = createLibrary(token)
        val b1 = createBook(token, libId, "B1")
        val b2 = createBook(token, libId, "B2")
        setAuthor(token, b1, "AuthorX")
        setAuthor(token, b2, "AuthorY")

        val body = app(Request(Method.GET, "/authors").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("AuthorX"))
        assertTrue(body.contains("AuthorY"))
    }

    @Test
    fun `author detail with URL-encoded name works`() {
        val token = registerAndGetToken("author")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "The Hobbit")
        setAuthor(token, bookId, "J.R.R. Tolkien")

        val response = app(
            Request(Method.GET, "/authors/J.R.R.%20Tolkien").header("Cookie", "token=$token"),
        )
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("J.R.R. Tolkien"))
    }
}
