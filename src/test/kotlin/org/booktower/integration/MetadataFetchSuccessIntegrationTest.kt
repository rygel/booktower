package org.booktower.integration

import com.fasterxml.jackson.databind.ObjectMapper
import org.booktower.models.FetchedMetadata
import org.booktower.services.MetadataFetchService
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests the metadata fetch happy path end-to-end using a stub MetadataFetchService
 * so no real network calls are made. Verifies that fetched data is actually applied
 * to the book and appears on the book detail page.
 */
class MetadataFetchSuccessIntegrationTest : IntegrationTestBase() {

    private val mapper = ObjectMapper()

    override fun createMetadataFetchService() = object : MetadataFetchService() {
        override fun fetchMetadata(title: String, author: String?, source: String?) = FetchedMetadata(
            title = "The Fetched Title",
            author = "Fetched Author Name",
            description = "A wonderful description fetched from Open Library.",
            isbn = "9780123456789",
            publisher = "Stub Publisher Co.",
            publishedDate = "1984-05-20",
            openLibraryCoverId = null,
        )
    }

    @Test
    fun `fetch metadata applies all fetched fields to the book`() {
        val token = registerAndGetToken("mfs1")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Original Title")

        val resp = app(
            Request(Method.POST, "/ui/books/$bookId/fetch-metadata")
                .header("Cookie", "token=$token"),
        )
        // Handler redirects via HX-Redirect on success
        assertEquals(Status.OK, resp.status)
        assertTrue(resp.header("HX-Redirect")?.contains("/books/$bookId") == true,
            "Should redirect back to book page on success")

        // Verify the data was actually persisted — read via API
        val bookResp = app(
            Request(Method.GET, "/api/books/$bookId")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.OK, bookResp.status)
        val body = bookResp.bodyString()
        assertTrue(body.contains("The Fetched Title"), "title should be updated")
        assertTrue(body.contains("Fetched Author Name"), "author should be updated")
        assertTrue(body.contains("A wonderful description"), "description should be updated")
    }

    @Test
    fun `fetch metadata updates isbn publisher and publishedDate`() {
        val token = registerAndGetToken("mfs2")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Some Book")

        app(
            Request(Method.POST, "/ui/books/$bookId/fetch-metadata")
                .header("Cookie", "token=$token"),
        )

        val bookResp = app(
            Request(Method.GET, "/api/books/$bookId")
                .header("Cookie", "token=$token"),
        )
        val body = bookResp.bodyString()
        assertTrue(body.contains("9780123456789"), "isbn should be stored")
        assertTrue(body.contains("Stub Publisher Co."), "publisher should be stored")
        assertTrue(body.contains("1984-05-20") || body.contains("1984"), "publishedDate should be stored")
    }

    @Test
    fun `fetched data is visible on the rendered book detail page`() {
        val token = registerAndGetToken("mfs3")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "My Book")

        app(
            Request(Method.POST, "/ui/books/$bookId/fetch-metadata")
                .header("Cookie", "token=$token"),
        )

        val pageResp = app(
            Request(Method.GET, "/books/$bookId")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.OK, pageResp.status)
        val html = pageResp.bodyString()
        assertTrue(html.contains("The Fetched Title"), "title should render in HTML")
        assertTrue(html.contains("Fetched Author Name"), "author should render in HTML")
        assertTrue(html.contains("A wonderful description"), "description should render in HTML")
    }

    @Test
    fun `fetch metadata preserves existing fields not returned by fetch`() {
        // Stub returns null for openLibraryCoverId — original title should be replaced,
        // but a field not in FetchedMetadata (e.g. tags, status) should be untouched.
        val token = registerAndGetToken("mfs4")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Original Book Title")

        // Set a status before fetching metadata
        app(
            Request(Method.POST, "/ui/books/$bookId/status")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("status=READING"),
        )

        app(
            Request(Method.POST, "/ui/books/$bookId/fetch-metadata")
                .header("Cookie", "token=$token"),
        )

        val bookResp = app(
            Request(Method.GET, "/api/books/$bookId")
                .header("Cookie", "token=$token"),
        )
        val body = bookResp.bodyString()
        // Status should survive the metadata fetch
        assertTrue(body.contains("READING"), "read status should survive metadata fetch")
        // New metadata applied
        assertTrue(body.contains("The Fetched Title"), "title should be updated")
    }

    @Test
    fun `fetch metadata for book with null author still works`() {
        val token = registerAndGetToken("mfs5")
        val libId = createLibrary(token)
        // Create a book with no author (null by default in createBook)
        val bookId = createBook(token, libId, "Authorless Book")

        val resp = app(
            Request(Method.POST, "/ui/books/$bookId/fetch-metadata")
                .header("Cookie", "token=$token"),
        )
        // Should not crash even when author is null in the request to fetchMetadata
        assertTrue(resp.status.successful || resp.status == Status.OK,
            "Fetch with null author should not error: ${resp.status}")
    }
}
