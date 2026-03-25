package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import org.runary.config.Json
import org.runary.models.BookDto
import org.runary.models.ErrorResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for input validation: Unicode, long strings, edge cases,
 * body size limits, and numeric range enforcement.
 */
class InputValidationIntegrationTest : IntegrationTestBase() {
    // ── Unicode ──────────────────────────────────────────────────────────

    @Test
    fun `book title with Unicode characters works`() {
        val token = registerAndGetToken("unicode1")
        val libId = createLibrary(token)
        val resp =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"日本語のタイトル 📚","author":"著者名","description":"説明文","libraryId":"$libId"}"""),
            )
        assertEquals(Status.CREATED, resp.status)
        val book = Json.mapper.readValue(resp.bodyString(), BookDto::class.java)
        assertEquals("日本語のタイトル 📚", book.title)
        assertEquals("著者名", book.author)
    }

    @Test
    fun `book with Chinese, Arabic, and Cyrillic characters`() {
        val token = registerAndGetToken("unicode2")
        val libId = createLibrary(token)
        val resp =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"中文书名 • كتاب • Книга","author":"作者","description":null,"libraryId":"$libId"}"""),
            )
        assertEquals(Status.CREATED, resp.status)
        val book = Json.mapper.readValue(resp.bodyString(), BookDto::class.java)
        assertTrue(book.title.contains("中文书名"))
        assertTrue(book.title.contains("Книга"))
    }

    @Test
    fun `search with Unicode query returns results`() {
        val token = registerAndGetToken("unicode3")
        val libId = createLibrary(token)
        app(
            Request(Method.POST, "/api/books")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"Ünïcödé Tëst Bøøk","author":null,"description":null,"libraryId":"$libId"}"""),
        )
        val resp =
            app(
                Request(Method.GET, "/api/search?q=${java.net.URLEncoder.encode("Ünïcödé", "UTF-8")}")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, resp.status)
    }

    // ── Long strings ─────────────────────────────────────────────────────

    @Test
    fun `description at max length is accepted`() {
        val token = registerAndGetToken("longdesc1")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        val longDesc = "A".repeat(10_000)
        val resp =
            app(
                Request(Method.PUT, "/api/books/$bookId")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Test","author":null,"description":"$longDesc"}"""),
            )
        assertEquals(Status.OK, resp.status)
    }

    @Test
    fun `description over max length is rejected`() {
        val token = registerAndGetToken("longdesc2")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        val tooLong = "A".repeat(10_001)
        val resp =
            app(
                Request(Method.PUT, "/api/books/$bookId")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Test","author":null,"description":"$tooLong"}"""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
        assertTrue(resp.bodyString().contains("10,000"))
    }

    @Test
    fun `title over 255 chars is rejected`() {
        val token = registerAndGetToken("longtitle")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        val longTitle = "T".repeat(256)
        val resp =
            app(
                Request(Method.PUT, "/api/books/$bookId")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"$longTitle","author":null,"description":null}"""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    // ── Whitespace / empty strings ────────────────────────────────────────

    @Test
    fun `blank title is rejected`() {
        val token = registerAndGetToken("blank1")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        val resp =
            app(
                Request(Method.PUT, "/api/books/$bookId")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"   ","author":null,"description":null}"""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `whitespace-only library name is rejected`() {
        val token = registerAndGetToken("blank2")
        val resp =
            app(
                Request(Method.POST, "/api/libraries")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"   ","path":"./data/blank-test"}"""),
            )
        assertTrue(
            resp.status == Status.BAD_REQUEST || resp.status == Status.UNPROCESSABLE_ENTITY,
            "Whitespace-only name should be rejected, got ${resp.status}",
        )
    }

    // ── Numeric range ────────────────────────────────────────────────────

    @Test
    fun `negative page count is rejected`() {
        val token = registerAndGetToken("negpage")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        val resp =
            app(
                Request(Method.PUT, "/api/books/$bookId")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Test","author":null,"description":null,"pageCount":-5}"""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
        assertTrue(resp.bodyString().contains("negative"))
    }

    @Test
    fun `page count over 100000 is rejected`() {
        val token = registerAndGetToken("bigpage")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        val resp =
            app(
                Request(Method.PUT, "/api/books/$bookId")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Test","author":null,"description":null,"pageCount":200000}"""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
        assertTrue(resp.bodyString().contains("100,000"))
    }

    @Test
    fun `zero page count is accepted`() {
        val token = registerAndGetToken("zeropage")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        val resp =
            app(
                Request(Method.PUT, "/api/books/$bookId")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Test","author":null,"description":null,"pageCount":0}"""),
            )
        assertEquals(Status.OK, resp.status)
    }

    // ── Special characters ───────────────────────────────────────────────

    @Test
    fun `title with special characters and HTML is stored safely`() {
        val token = registerAndGetToken("special1")
        val libId = createLibrary(token)
        val resp =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"O'Reilly's <Book> & \"Quotes\"","author":null,"description":null,"libraryId":"$libId"}"""),
            )
        assertEquals(Status.CREATED, resp.status)
        val book = Json.mapper.readValue(resp.bodyString(), BookDto::class.java)
        assertTrue(book.title.contains("O'Reilly"))
        assertTrue(book.title.contains("&"))
    }

    // ── Optimistic locking ───────────────────────────────────────────────

    @Test
    fun `concurrent update with stale expectedUpdatedAt returns 409`() {
        val token = registerAndGetToken("lock1")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Original")

        // First update succeeds
        app(
            Request(Method.PUT, "/api/books/$bookId")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"Updated","author":null,"description":null}"""),
        )

        // Second update with stale timestamp should fail
        val resp =
            app(
                Request(Method.PUT, "/api/books/$bookId")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Conflicting","author":null,"description":null,"expectedUpdatedAt":"2020-01-01T00:00:00Z"}"""),
            )
        assertEquals(Status.CONFLICT, resp.status)
        assertTrue(resp.bodyString().contains("CONFLICT"))
    }

    @Test
    fun `update without expectedUpdatedAt always succeeds`() {
        val token = registerAndGetToken("lock2")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Original")

        // Two updates without optimistic lock — both succeed
        val resp1 =
            app(
                Request(Method.PUT, "/api/books/$bookId")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"First","author":null,"description":null}"""),
            )
        assertEquals(Status.OK, resp1.status)

        val resp2 =
            app(
                Request(Method.PUT, "/api/books/$bookId")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Second","author":null,"description":null}"""),
            )
        assertEquals(Status.OK, resp2.status)
    }
}
