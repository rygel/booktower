package org.booktower.integration

import org.booktower.config.Json
import org.booktower.models.FetchedMetadata
import org.booktower.services.METADATA_SOURCES
import org.booktower.services.MetadataFetchService
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MetadataSourcesIntegrationTest : IntegrationTestBase() {

    // Stub that records which source was requested and returns a controlled response
    private var capturedSource: String? = "not-called"
    private var stubResult: FetchedMetadata? = FetchedMetadata(
        title = "Stub Title", author = "Stub Author",
        description = "Stub desc", isbn = "9780000000001",
        publisher = "Stub Pub", publishedDate = "2024",
        source = "openlibrary",
    )

    override fun createMetadataFetchService() = object : MetadataFetchService() {
        override fun fetchMetadata(title: String, author: String?, source: String?): FetchedMetadata? {
            capturedSource = source
            return stubResult
        }
    }

    @Test
    fun `GET api metadata sources lists all providers`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/api/metadata/sources").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        val sources = tree.get("sources")
        assertNotNull(sources)
        assertTrue(sources.isArray)
        val sourceList = sources.map { it.asText() }
        assertTrue("openlibrary" in sourceList)
        assertTrue("googlebooks" in sourceList)
        assertEquals(METADATA_SOURCES, sourceList)
    }

    @Test
    fun `GET api metadata search returns result for valid title`() {
        val token = registerAndGetToken()
        val resp = app(
            Request(Method.GET, "/api/metadata/search?title=Dune&author=Frank+Herbert")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals("Stub Title", tree.get("title").asText())
        assertEquals("Stub Author", tree.get("author").asText())
    }

    @Test
    fun `GET api metadata search passes source param to service`() {
        val token = registerAndGetToken()
        capturedSource = "not-called"
        app(
            Request(Method.GET, "/api/metadata/search?title=Dune&source=googlebooks")
                .header("Cookie", "token=$token"),
        )
        assertEquals("googlebooks", capturedSource)
    }

    @Test
    fun `GET api metadata search without source passes null to service`() {
        val token = registerAndGetToken()
        capturedSource = "not-called"
        app(
            Request(Method.GET, "/api/metadata/search?title=Dune")
                .header("Cookie", "token=$token"),
        )
        assertEquals(null, capturedSource)
    }

    @Test
    fun `GET api metadata search returns 404 when no result`() {
        val token = registerAndGetToken()
        stubResult = null
        val resp = app(
            Request(Method.GET, "/api/metadata/search?title=NoSuchBook")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `GET api metadata search requires title param`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/api/metadata/search").header("Cookie", "token=$token"))
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `metadata endpoints require authentication`() {
        val searchResp = app(Request(Method.GET, "/api/metadata/search?title=Dune"))
        assertEquals(Status.UNAUTHORIZED, searchResp.status)
        val sourcesResp = app(Request(Method.GET, "/api/metadata/sources"))
        assertEquals(Status.UNAUTHORIZED, sourcesResp.status)
    }

    @Test
    fun `FetchedMetadata source field is included in response`() {
        val token = registerAndGetToken()
        val resp = app(
            Request(Method.GET, "/api/metadata/search?title=Dune")
                .header("Cookie", "token=$token"),
        )
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals("openlibrary", tree.get("source").asText())
    }
}
