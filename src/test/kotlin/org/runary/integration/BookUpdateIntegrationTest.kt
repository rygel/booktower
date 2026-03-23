package org.runary.integration

import org.runary.config.Json
import org.runary.models.BookDto
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookUpdateIntegrationTest : IntegrationTestBase() {
    private fun updateBook(
        token: String,
        bookId: String,
        title: String,
        author: String? = null,
        description: String? = null,
    ): org.http4k.core.Response {
        val authorJson = if (author == null) "null" else "\"$author\""
        val descJson = if (description == null) "null" else "\"$description\""
        return app(
            Request(Method.PUT, "/api/books/$bookId")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"$title","author":$authorJson,"description":$descJson}"""),
        )
    }

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    fun `update book title returns 200 with updated data`() {
        val token = registerAndGetToken("upd")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Original Title")

        val r = updateBook(token, bookId, "Updated Title")

        assertEquals(Status.OK, r.status)
        val book = Json.mapper.readValue(r.bodyString(), BookDto::class.java)
        assertEquals("Updated Title", book.title)
    }

    @Test
    fun `update book sets author`() {
        val token = registerAndGetToken("upd")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val r = updateBook(token, bookId, "Title", author = "New Author")

        assertEquals(Status.OK, r.status)
        val book = Json.mapper.readValue(r.bodyString(), BookDto::class.java)
        assertEquals("New Author", book.author)
    }

    @Test
    fun `update book clears author when set to null`() {
        val token = registerAndGetToken("upd")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        // First set author
        updateBook(token, bookId, "Title", author = "Some Author")

        // Now clear it
        val r = updateBook(token, bookId, "Title", author = null)

        assertEquals(Status.OK, r.status)
        val book = Json.mapper.readValue(r.bodyString(), BookDto::class.java)
        assertNull(book.author)
    }

    @Test
    fun `update book sets description`() {
        val token = registerAndGetToken("upd")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val r = updateBook(token, bookId, "Title", description = "A fine description")

        assertEquals(Status.OK, r.status)
        val book = Json.mapper.readValue(r.bodyString(), BookDto::class.java)
        assertEquals("A fine description", book.description)
    }

    @Test
    fun `get book after update reflects new values`() {
        val token = registerAndGetToken("upd")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Before")

        updateBook(token, bookId, "After", author = "Author X", description = "Desc Y")

        val r =
            app(
                Request(Method.GET, "/api/books/$bookId")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, r.status)
        val book = Json.mapper.readValue(r.bodyString(), BookDto::class.java)
        assertEquals("After", book.title)
        assertEquals("Author X", book.author)
        assertEquals("Desc Y", book.description)
    }

    @Test
    fun `update book with unicode title succeeds`() {
        val token = registerAndGetToken("upd")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val r = updateBook(token, bookId, "日本語タイトル")

        assertEquals(Status.OK, r.status)
        val book = Json.mapper.readValue(r.bodyString(), BookDto::class.java)
        assertEquals("日本語タイトル", book.title)
    }

    @Test
    fun `update does not affect file, progress, or bookmarks`() {
        val token = registerAndGetToken("upd")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        // Set progress and a bookmark
        app(
            Request(Method.PUT, "/api/books/$bookId/progress")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"currentPage":55}"""),
        )
        app(
            Request(Method.POST, "/api/bookmarks")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"bookId":"$bookId","page":10,"title":"Saved mark","note":null}"""),
        )

        updateBook(token, bookId, "New Title")

        // Progress still there
        val bookR = app(Request(Method.GET, "/api/books/$bookId").header("Cookie", "token=$token"))
        assertTrue(bookR.bodyString().contains("55"), "progress should survive update")

        // Bookmark still there
        val bmR =
            app(
                Request(Method.GET, "/api/bookmarks?bookId=$bookId")
                    .header("Cookie", "token=$token"),
            )
        assertTrue(bmR.bodyString().contains("Saved mark"), "bookmark should survive update")
    }

    // ── Validation errors ─────────────────────────────────────────────────

    @Test
    fun `update with blank title returns 400`() {
        val token = registerAndGetToken("upd")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val r = updateBook(token, bookId, "   ")

        assertEquals(Status.BAD_REQUEST, r.status)
    }

    @Test
    fun `update with title over 255 chars returns 400`() {
        val token = registerAndGetToken("upd")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val r =
            app(
                Request(Method.PUT, "/api/books/$bookId")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"${"A".repeat(256)}","author":null,"description":null}"""),
            )

        assertEquals(Status.BAD_REQUEST, r.status)
    }

    @Test
    fun `update without body returns 400`() {
        val token = registerAndGetToken("upd")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val r =
            app(
                Request(Method.PUT, "/api/books/$bookId")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json"),
            )

        assertEquals(Status.BAD_REQUEST, r.status)
    }

    // ── Authorization ─────────────────────────────────────────────────────

    @Test
    fun `update without auth returns 401`() {
        val r =
            app(
                Request(Method.PUT, "/api/books/00000000-0000-0000-0000-000000000000")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"x","author":null,"description":null}"""),
            )
        assertEquals(Status.UNAUTHORIZED, r.status)
    }

    @Test
    fun `update non-existent book returns 404`() {
        val token = registerAndGetToken("upd")

        val r = updateBook(token, "00000000-0000-0000-0000-000000000000", "Title")

        assertEquals(Status.NOT_FOUND, r.status)
    }

    @Test
    fun `user B cannot update user A book`() {
        val tokenA = registerAndGetToken("upd_a")
        val tokenB = registerAndGetToken("upd_b")
        val libId = createLibrary(tokenA)
        val bookId = createBook(tokenA, libId, "A's Book")

        val r =
            app(
                Request(Method.PUT, "/api/books/$bookId")
                    .header("Cookie", "token=$tokenB")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Hijacked","author":null,"description":null}"""),
            )

        assertEquals(Status.NOT_FOUND, r.status)

        // A's book still has original title
        val get = app(Request(Method.GET, "/api/books/$bookId").header("Cookie", "token=$tokenA"))
        val book = Json.mapper.readValue(get.bodyString(), BookDto::class.java)
        assertEquals("A's Book", book.title)
    }
}
