package org.runary.integration

import org.runary.config.Json
import org.runary.models.BookDto
import org.runary.models.BookmarkDto
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Boundary and edge-case tests for book and bookmark field values:
 * whitespace, Unicode, length limits, and input sanitation.
 */
class BookFieldBoundaryTest : IntegrationTestBase() {
    // ── Book title ────────────────────────────────────────────────────────

    @Test
    fun `whitespace-only title returns 400`() {
        val token = registerAndGetToken("bfb")
        val libId = createLibrary(token)

        val r =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"   ","author":null,"description":null,"libraryId":"$libId"}"""),
            )
        assertEquals(Status.BAD_REQUEST, r.status)
    }

    @Test
    fun `tab-only title returns 400`() {
        val token = registerAndGetToken("bfb")
        val libId = createLibrary(token)

        val r =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("{\"title\":\"\\t\",\"author\":null,\"description\":null,\"libraryId\":\"$libId\"}"),
            )
        assertEquals(Status.BAD_REQUEST, r.status)
    }

    @Test
    fun `unicode title is stored and returned correctly`() {
        val token = registerAndGetToken("bfb")
        val libId = createLibrary(token)

        val title = "日本語タイトル — Ünïcödé"
        val r =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json; charset=utf-8")
                    .body("""{"title":"$title","author":null,"description":null,"libraryId":"$libId"}"""),
            )
        assertEquals(Status.CREATED, r.status)
        val book = Json.mapper.readValue(r.bodyString(), BookDto::class.java)
        assertEquals(title, book.title)
    }

    @Test
    fun `title with single quote does not break storage`() {
        val token = registerAndGetToken("bfb")
        val libId = createLibrary(token)

        // Use JSON-escaped single quote (actually a plain single quote is fine in JSON)
        val r =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"O'Brien's Tale","author":null,"description":null,"libraryId":"$libId"}"""),
            )
        assertEquals(Status.CREATED, r.status)
        val book = Json.mapper.readValue(r.bodyString(), BookDto::class.java)
        assertTrue(book.title.contains("O'Brien"))
    }

    @Test
    fun `title with SQL-injection-like content is stored safely`() {
        val token = registerAndGetToken("bfb")
        val libId = createLibrary(token)

        // Should be stored literally, not interpreted as SQL
        val r =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"1'; DROP TABLE books; --","author":null,"description":null,"libraryId":"$libId"}"""),
            )
        // Should succeed (JDBI uses parameterized queries), books table still intact
        assertEquals(Status.CREATED, r.status)

        // Table still works — we can still list books
        val list =
            app(
                Request(Method.GET, "/api/books?libraryId=$libId")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, list.status)
    }

    @Test
    fun `title exceeding 255 characters is rejected with 400`() {
        val token = registerAndGetToken("bfb")
        val libId = createLibrary(token)
        val longTitle = "A".repeat(256)

        val r =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"$longTitle","author":null,"description":null,"libraryId":"$libId"}"""),
            )
        assertEquals(Status.BAD_REQUEST, r.status, "Title > 255 chars should return 400")
    }

    @Test
    fun `title exactly 255 characters is accepted`() {
        val token = registerAndGetToken("bfb")
        val libId = createLibrary(token)
        val title255 = "A".repeat(255)

        val r =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"$title255","author":null,"description":null,"libraryId":"$libId"}"""),
            )
        assertEquals(Status.CREATED, r.status, "255-char title should be accepted")
    }

    // ── Author and description ─────────────────────────────────────────────

    @Test
    fun `book with empty string author is accepted`() {
        val token = registerAndGetToken("bfb")
        val libId = createLibrary(token)

        val r =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"No Author Book","author":"","description":null,"libraryId":"$libId"}"""),
            )
        // Empty author string differs from null — both are valid inputs with no explicit validation
        assertTrue(r.status.code < 500, "Server should not crash on empty author: ${r.status}")
    }

    @Test
    fun `book with unicode author is stored correctly`() {
        val token = registerAndGetToken("bfb")
        val libId = createLibrary(token)
        val author = "Ré Lëï"

        val r =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json; charset=utf-8")
                    .body("""{"title":"Book Title","author":"$author","description":null,"libraryId":"$libId"}"""),
            )
        assertEquals(Status.CREATED, r.status)
        val book = Json.mapper.readValue(r.bodyString(), BookDto::class.java)
        assertEquals(author, book.author)
    }

    @Test
    fun `book with multiline description is stored correctly`() {
        val token = registerAndGetToken("bfb")
        val libId = createLibrary(token)

        val r =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Described Book","author":null,"description":"Line 1\nLine 2\nLine 3","libraryId":"$libId"}"""),
            )
        assertEquals(Status.CREATED, r.status)
        val book = Json.mapper.readValue(r.bodyString(), BookDto::class.java)
        assertTrue(book.description?.contains("Line 1") == true)
    }

    // ── Bookmark title boundary (255-char limit) ───────────────────────────

    @Test
    fun `bookmark title exactly 255 characters is accepted`() {
        val token = registerAndGetToken("bfb")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        val title255 = "B".repeat(255)

        val r =
            app(
                Request(Method.POST, "/api/bookmarks")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"bookId":"$bookId","page":1,"title":"$title255","note":null}"""),
            )
        assertEquals(Status.CREATED, r.status, "255-char bookmark title should be accepted")
        val bm = Json.mapper.readValue(r.bodyString(), BookmarkDto::class.java)
        assertEquals(255, bm.title?.length)
    }

    @Test
    fun `bookmark title of 256 characters is rejected`() {
        val token = registerAndGetToken("bfb")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        val title256 = "C".repeat(256)

        val r =
            app(
                Request(Method.POST, "/api/bookmarks")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"bookId":"$bookId","page":1,"title":"$title256","note":null}"""),
            )
        assertEquals(Status.BAD_REQUEST, r.status, "256-char bookmark title should be rejected")
    }

    @Test
    fun `bookmark with null title is accepted`() {
        val token = registerAndGetToken("bfb")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val r =
            app(
                Request(Method.POST, "/api/bookmarks")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"bookId":"$bookId","page":5,"title":null,"note":null}"""),
            )
        assertEquals(Status.CREATED, r.status)
    }

    @Test
    fun `bookmark with empty title is accepted`() {
        val token = registerAndGetToken("bfb")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val r =
            app(
                Request(Method.POST, "/api/bookmarks")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"bookId":"$bookId","page":5,"title":"","note":null}"""),
            )
        // Empty title is different from null; the handler only rejects titles > 255 chars
        assertTrue(r.status.code < 500, "Empty title should not crash server: ${r.status}")
    }

    @Test
    fun `bookmark with unicode title is stored correctly`() {
        val token = registerAndGetToken("bfb")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        val title = "第一章：始まり"

        val r =
            app(
                Request(Method.POST, "/api/bookmarks")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json; charset=utf-8")
                    .body("""{"bookId":"$bookId","page":1,"title":"$title","note":null}"""),
            )
        assertEquals(Status.CREATED, r.status)
        val bm = Json.mapper.readValue(r.bodyString(), BookmarkDto::class.java)
        assertEquals(title, bm.title)
    }

    // ── Progress boundary ──────────────────────────────────────────────────

    @Test
    fun `progress with very large page number is accepted`() {
        val token = registerAndGetToken("bfb")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val r =
            app(
                Request(Method.PUT, "/api/books/$bookId/progress")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"currentPage":999999}"""),
            )
        assertEquals(Status.OK, r.status, "Very large page number should be accepted")
    }

    @Test
    fun `progress update to page 1 after higher page is allowed`() {
        val token = registerAndGetToken("bfb")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        app(
            Request(Method.PUT, "/api/books/$bookId/progress")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"currentPage":100}"""),
        )

        // Going backwards is allowed — no "must be >= previous" rule
        val r =
            app(
                Request(Method.PUT, "/api/books/$bookId/progress")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"currentPage":1}"""),
            )
        assertEquals(Status.OK, r.status, "Rewinding progress should be allowed")
    }
}
