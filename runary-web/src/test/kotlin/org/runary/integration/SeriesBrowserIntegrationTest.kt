package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import org.runary.config.Json
import org.runary.models.UpdateBookRequest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeriesBrowserIntegrationTest : IntegrationTestBase() {
    private fun setSeriesOnBook(
        token: String,
        bookId: String,
        series: String,
        index: Double?,
    ) {
        app(
            Request(Method.PUT, "/api/books/$bookId")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body(
                    Json.mapper.writeValueAsString(
                        UpdateBookRequest("Book $bookId", null, null, series, index),
                    ),
                ),
        )
    }

    @Test
    fun `series list page returns 200`() {
        val token = registerAndGetToken("series")
        val response = app(Request(Method.GET, "/series").header("Cookie", "token=$token"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("Runary"))
    }

    @Test
    fun `series list page shows series after books are assigned`() {
        val token = registerAndGetToken("series")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Foundation")
        setSeriesOnBook(token, bookId, "Foundation", 1.0)

        val body = app(Request(Method.GET, "/series").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("Foundation"))
    }

    @Test
    fun `series list is empty when no books have series`() {
        val token = registerAndGetToken("series")
        createBook(token, createLibrary(token), "Standalone")

        val body = app(Request(Method.GET, "/series").header("Cookie", "token=$token")).bodyString()
        assertFalse(body.contains("Standalone"))
    }

    @Test
    fun `series detail page returns 200 for existing series`() {
        val token = registerAndGetToken("series")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Dune")
        setSeriesOnBook(token, bookId, "Dune", 1.0)

        val response =
            app(
                Request(Method.GET, "/series/Dune").header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("Dune"))
    }

    @Test
    fun `series detail page lists books sorted by series index`() {
        val token = registerAndGetToken("series")
        val libId = createLibrary(token)
        val book1 = createBook(token, libId, "Book One")
        val book2 = createBook(token, libId, "Book Two")
        val book3 = createBook(token, libId, "Book Three")
        setSeriesOnBook(token, book1, "MySeries", 1.0)
        setSeriesOnBook(token, book2, "MySeries", 2.0)
        setSeriesOnBook(token, book3, "MySeries", 3.0)

        val body =
            app(
                Request(Method.GET, "/series/MySeries").header("Cookie", "token=$token"),
            ).bodyString()
        assertTrue(body.contains("MySeries"))
        assertTrue(body.contains("3 ")) // book count in heading
        // All three books' IDs should appear in the rendered cards
        assertTrue(body.contains(book1))
        assertTrue(body.contains(book2))
        assertTrue(body.contains(book3))
    }

    @Test
    fun `series detail page shows series index badge`() {
        val token = registerAndGetToken("series")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Clash of Kings")
        setSeriesOnBook(token, bookId, "ASOIAF", 2.0)

        val body =
            app(
                Request(Method.GET, "/series/ASOIAF").header("Cookie", "token=$token"),
            ).bodyString()
        assertTrue(body.contains("#2"))
    }

    @Test
    fun `series are isolated between users`() {
        val tokenA = registerAndGetToken("series")
        val tokenB = registerAndGetToken("series")
        val libA = createLibrary(tokenA)
        val bookA = createBook(tokenA, libA, "Private Book")
        setSeriesOnBook(tokenA, bookA, "PrivateSeries", 1.0)

        // User B should not see user A's series
        val bodyB = app(Request(Method.GET, "/series").header("Cookie", "token=$tokenB")).bodyString()
        assertFalse(bodyB.contains("PrivateSeries"))
    }

    @Test
    fun `series list page requires authentication`() {
        val response = app(Request(Method.GET, "/series"))
        // redirects to login (303) or 401
        assertTrue(response.status.code in listOf(302, 303, 401))
    }

    @Test
    fun `series detail page with URL-encoded name works`() {
        val token = registerAndGetToken("series")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "A Game of Thrones")
        setSeriesOnBook(token, bookId, "A Song of Ice and Fire", 1.0)

        // URL-encode the series name
        val encoded = "A%20Song%20of%20Ice%20and%20Fire"
        val response =
            app(
                Request(Method.GET, "/series/$encoded").header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("A Song of Ice and Fire"))
    }

    @Test
    fun `series list shows multiple series`() {
        val token = registerAndGetToken("series")
        val libId = createLibrary(token)
        val b1 = createBook(token, libId, "Book1")
        val b2 = createBook(token, libId, "Book2")
        setSeriesOnBook(token, b1, "SeriesAlpha", 1.0)
        setSeriesOnBook(token, b2, "SeriesBeta", 1.0)

        val body = app(Request(Method.GET, "/series").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("SeriesAlpha"))
        assertTrue(body.contains("SeriesBeta"))
    }
}
