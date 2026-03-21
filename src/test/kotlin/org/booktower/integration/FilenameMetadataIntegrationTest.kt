package org.booktower.integration

import org.booktower.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * End-to-end tests for POST /api/books/{id}/apply-filename-metadata.
 * Uses IntegrationTestBase (stub MetadataFetchService already in place).
 */
class FilenameMetadataIntegrationTest : IntegrationTestBase() {
    @Test
    fun `apply-filename-metadata extracts author and title from book file path`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)

        // Create a book; its filePath will be blank/default, so metadata comes from title parse.
        // Raw SQL is acceptable here: file_path is internal state set during library scanning,
        // and there is no BookService method to update just file_path on an existing book.
        val bookId = createBook(token, libId, "Placeholder")
        val jdbi =
            org.booktower.TestFixture.database
                .getJdbi()
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate("UPDATE books SET file_path = ? WHERE id = ?")
                .bind(0, "/books/Frank Herbert - Dune (Dune Chronicles 1).epub")
                .bind(1, bookId)
                .execute()
        }

        val resp =
            app(
                Request(Method.POST, "/api/books/$bookId/apply-filename-metadata")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        val extracted = tree.get("extracted")
        assertNotNull(extracted)
        assertEquals("Dune", extracted.get("title").asText())
        assertEquals("Frank Herbert", extracted.get("author").asText())
        assertEquals("Dune Chronicles", extracted.get("series").asText())
        assertEquals(1.0, extracted.get("seriesIndex").asDouble())

        // Verify DB was updated
        val book =
            Json.mapper.readTree(
                app(Request(Method.GET, "/api/books/$bookId").header("Cookie", "token=$token")).bodyString(),
            )
        assertEquals("Dune", book.get("title").asText())
        assertEquals("Frank Herbert", book.get("author").asText())
    }

    @Test
    fun `apply-filename-metadata with title-only filename`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Placeholder2")
        // Raw SQL is acceptable here: file_path is internal state with no service method to update it.
        val jdbi =
            org.booktower.TestFixture.database
                .getJdbi()
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate("UPDATE books SET file_path = ? WHERE id = ?")
                .bind(0, "/books/The Hobbit.pdf")
                .bind(1, bookId)
                .execute()
        }

        val resp =
            app(
                Request(Method.POST, "/api/books/$bookId/apply-filename-metadata")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, resp.status)
        val extracted = Json.mapper.readTree(resp.bodyString()).get("extracted")
        assertEquals("The Hobbit", extracted.get("title").asText())
        assertNull(extracted.get("author")?.takeIf { !it.isNull })
    }

    @Test
    fun `apply-filename-metadata returns 404 for unknown book`() {
        val token = registerAndGetToken()
        val resp =
            app(
                Request(Method.POST, "/api/books/00000000-0000-0000-0000-000000000000/apply-filename-metadata")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `apply-filename-metadata requires authentication`() {
        val resp = app(Request(Method.POST, "/api/books/some-id/apply-filename-metadata"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
