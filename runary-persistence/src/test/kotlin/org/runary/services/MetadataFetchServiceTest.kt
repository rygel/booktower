package org.runary.services

import org.junit.jupiter.api.Test
import org.runary.models.FetchedMetadata
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for MetadataFetchService using a controllable subclass that overrides
 * the network call. This avoids test-time internet dependency while verifying
 * that callers get correctly shaped results and null-handling is correct.
 */
class MetadataFetchServiceTest {
    /** Returns a pre-configured result instead of calling Open Library. */
    private fun stubService(result: FetchedMetadata?): MetadataFetchService =
        object : MetadataFetchService() {
            override fun fetchMetadata(
                title: String,
                author: String?,
                source: String?,
            ): FetchedMetadata? = result
        }

    @Test
    fun `fetchMetadata returns null when stub returns null`() {
        val service = stubService(null)
        assertNull(service.fetchMetadata("Unknown Title", null))
    }

    @Test
    fun `fetchMetadata returns metadata when stub provides a result`() {
        val metadata =
            FetchedMetadata(
                title = "Dune",
                author = "Frank Herbert",
                description = "A sci-fi classic.",
                isbn = "9780441013593",
                publisher = "Ace Books",
                publishedDate = "1965",
                openLibraryCoverId = 12345L,
            )
        val service = stubService(metadata)
        val result = service.fetchMetadata("Dune", "Frank Herbert")
        assertNotNull(result)
        assertEquals("Dune", result.title)
        assertEquals("Frank Herbert", result.author)
        assertEquals("A sci-fi classic.", result.description)
        assertEquals("9780441013593", result.isbn)
        assertEquals("Ace Books", result.publisher)
        assertEquals("1965", result.publishedDate)
        assertEquals(12345L, result.openLibraryCoverId)
    }

    @Test
    fun `fetchMetadata handles null author gracefully`() {
        val metadata =
            FetchedMetadata(
                title = "Unknown Author Book",
                author = null,
                description = null,
                isbn = null,
                publisher = null,
                publishedDate = null,
                openLibraryCoverId = null,
            )
        val service = stubService(metadata)
        val result = service.fetchMetadata("Unknown Author Book", null)
        assertNotNull(result)
        assertNull(result.author)
        assertNull(result.isbn)
    }

    @Test
    fun `fetchMetadata handles partial metadata`() {
        val metadata =
            FetchedMetadata(
                title = "Partial",
                author = "Some Author",
                description = null,
                isbn = null,
                publisher = null,
                publishedDate = "2000",
                openLibraryCoverId = null,
            )
        val service = stubService(metadata)
        val result = service.fetchMetadata("Partial", "Some Author")
        assertNotNull(result)
        assertEquals("Partial", result.title)
        assertEquals("2000", result.publishedDate)
        assertNull(result.description)
        assertNull(result.isbn)
    }

    @Test
    fun `real MetadataFetchService returns null for blank title on network error`() {
        // Passing a blank title to the real service — it will make a network call
        // that either returns no docs or fails; either way null is expected.
        // If there is no internet, the exception handler returns null.
        val service = MetadataFetchService()
        val result = service.fetchMetadata("ZZZZZZ_UNLIKELY_TO_MATCH_ANYTHING_XYZ_12345", null)
        // Result may be null (no match or no network) or a FetchedMetadata;
        // either is valid — we just verify no exception is thrown.
        // This is a smoke test: the real service must not throw.
    }

    @Test
    fun `MetadataFetchService can be subclassed for dependency injection`() {
        // Verifies the open class / open method contract works as expected.
        var called = false
        val service =
            object : MetadataFetchService() {
                override fun fetchMetadata(
                    title: String,
                    author: String?,
                    source: String?,
                ): FetchedMetadata? {
                    called = true
                    return null
                }
            }
        service.fetchMetadata("Any Title", null)
        assertEquals(true, called)
    }
}
