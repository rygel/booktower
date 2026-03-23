package org.runary.integration

import org.runary.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Integration tests for book series fields (series + seriesIndex).
 * These fields were added in V9__book_series.sql and wired through
 * UpdateBookRequest / BookDto / BookService.updateBook.
 */
class BookSeriesIntegrationTest : IntegrationTestBase() {
    @Test
    fun `update book with series and seriesIndex persists correctly`() {
        val token = registerAndGetToken("series_update")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "The Fellowship of the Ring")

        val putResp =
            app(
                Request(Method.PUT, "/api/books/$bookId")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body(
                        """{"title":"The Fellowship of the Ring","author":"Tolkien","description":null,"series":"Lord of the Rings","seriesIndex":1.0}""",
                    ),
            )
        assertEquals(Status.OK, putResp.status)

        val getResp =
            app(
                Request(Method.GET, "/api/books/$bookId")
                    .header("Cookie", "token=$token"),
            )
        val body = Json.mapper.readTree(getResp.bodyString())
        assertEquals("Lord of the Rings", body.get("series")?.asText())
        assertEquals(1.0, body.get("seriesIndex")?.asDouble())
    }

    @Test
    fun `update book clears series when set to null`() {
        val token = registerAndGetToken("series_clear")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Dune")

        // Set series first
        app(
            Request(Method.PUT, "/api/books/$bookId")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"Dune","author":"Herbert","description":null,"series":"Dune Chronicles","seriesIndex":1.0}"""),
        )

        // Clear series
        app(
            Request(Method.PUT, "/api/books/$bookId")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"Dune","author":"Herbert","description":null,"series":null,"seriesIndex":null}"""),
        )

        val getResp =
            app(
                Request(Method.GET, "/api/books/$bookId")
                    .header("Cookie", "token=$token"),
            )
        val body = Json.mapper.readTree(getResp.bodyString())
        val series = body.get("series")
        val seriesIsNull = series == null || series.isNull
        assert(seriesIsNull) { "series should be null after clearing" }
    }

    @Test
    fun `newly created book has null series`() {
        val token = registerAndGetToken("series_new")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Standalone Novel")

        val getResp =
            app(
                Request(Method.GET, "/api/books/$bookId")
                    .header("Cookie", "token=$token"),
            )
        val body = Json.mapper.readTree(getResp.bodyString())
        val series = body.get("series")
        val seriesIsNull = series == null || series.isNull
        assert(seriesIsNull) { "New book should have null series" }
    }

    @Test
    fun `seriesIndex can be fractional (e_g 1_5 for interlude)`() {
        val token = registerAndGetToken("series_frac")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Interlude Story")

        app(
            Request(Method.PUT, "/api/books/$bookId")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"Interlude Story","author":null,"description":null,"series":"Epic Series","seriesIndex":1.5}"""),
        )

        val getResp =
            app(
                Request(Method.GET, "/api/books/$bookId")
                    .header("Cookie", "token=$token"),
            )
        val body = Json.mapper.readTree(getResp.bodyString())
        assertEquals(1.5, body.get("seriesIndex")?.asDouble())
    }
}
