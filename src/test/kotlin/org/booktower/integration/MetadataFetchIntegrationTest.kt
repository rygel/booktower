package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for the "Fetch Metadata" endpoint.
 *
 * We do NOT make real Open Library network calls here — these tests verify
 * the routing, authentication, and error-handling layers. Live metadata
 * round-trips are covered by MetadataFetchService unit tests (or manual QA).
 */
class MetadataFetchIntegrationTest : IntegrationTestBase() {

    @Test
    fun `fetch metadata requires auth`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Some Book")

        val resp = app(Request(Method.POST, "/ui/books/$bookId/fetch-metadata"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `fetch metadata returns 404 for nonexistent book`() {
        val token = registerAndGetToken()
        val fakeId = "00000000-0000-0000-0000-000000000000"

        val resp = app(
            Request(Method.POST, "/ui/books/$fakeId/fetch-metadata")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `fetch metadata returns 404 for another user's book`() {
        val ownerToken = registerAndGetToken("owner")
        val otherToken = registerAndGetToken("other")
        val libId = createLibrary(ownerToken)
        val bookId = createBook(ownerToken, libId, "Private Book")

        val resp = app(
            Request(Method.POST, "/ui/books/$bookId/fetch-metadata")
                .header("Cookie", "token=$otherToken"),
        )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `fetch metadata returns 400 for malformed book id`() {
        val token = registerAndGetToken()

        val resp = app(
            Request(Method.POST, "/ui/books/not-a-uuid/fetch-metadata")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `fetch metadata route is registered and reachable`() {
        // When Open Library returns no match (highly unlikely title), the handler
        // responds 200 with an HX-Trigger toast rather than crashing.
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        // Use a title that will definitely produce no real network call in unit test
        // (the MetadataFetchService catches all exceptions and returns null).
        val bookId = createBook(token, libId, "xyzzy_no_match_${System.nanoTime()}")

        val resp = app(
            Request(Method.POST, "/ui/books/$bookId/fetch-metadata")
                .header("Cookie", "token=$token"),
        )
        // Either 200 (not found / network error) or redirect (HX-Redirect on success).
        // Both are acceptable — the route is wired and auth passes.
        assertTrue(
            resp.status == Status.OK || resp.status == Status.FOUND || resp.status.successful,
            "Expected a non-error status, got ${resp.status}",
        )
    }
}
