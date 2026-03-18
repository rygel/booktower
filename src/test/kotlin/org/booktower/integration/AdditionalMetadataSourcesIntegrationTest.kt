package org.booktower.integration

import org.booktower.config.Json
import org.booktower.models.FetchedMetadata
import org.booktower.services.METADATA_SOURCES
import org.booktower.services.MetadataFetchService
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdditionalMetadataSourcesIntegrationTest : IntegrationTestBase() {
    private var capturedSource: String? = null
    private var stubResult: FetchedMetadata? = null

    override fun createMetadataFetchService() =
        object : MetadataFetchService() {
            override fun fetchMetadata(
                title: String,
                author: String?,
                source: String?,
            ): FetchedMetadata? {
                capturedSource = source
                return stubResult
            }
        }

    @Test
    fun `METADATA_SOURCES includes hardcover, comicvine and audible`() {
        assertTrue("hardcover" in METADATA_SOURCES)
        assertTrue("comicvine" in METADATA_SOURCES)
        assertTrue("audible" in METADATA_SOURCES)
    }

    @Test
    fun `GET api metadata sources lists new providers`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/api/metadata/sources").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val sources =
            Json.mapper
                .readTree(resp.bodyString())
                .get("sources")
                .map { it.asText() }
        assertTrue("hardcover" in sources)
        assertTrue("comicvine" in sources)
        assertTrue("audible" in sources)
    }

    @Test
    fun `can request metadata from hardcover source`() {
        val token = registerAndGetToken()
        capturedSource = "not-called"
        stubResult =
            FetchedMetadata(
                title = "Test Book",
                author = "Author",
                description = "Desc",
                isbn = null,
                publisher = null,
                publishedDate = null,
                source = "hardcover",
                series = "Series A",
                seriesIndex = 1.0,
            )
        val resp =
            app(
                Request(Method.GET, "/api/metadata/search?title=Test&source=hardcover")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, resp.status)
        assertEquals("hardcover", capturedSource)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals("hardcover", tree.get("source").asText())
        assertEquals("Series A", tree.get("series").asText())
    }

    @Test
    fun `can request metadata from comicvine source`() {
        val token = registerAndGetToken()
        capturedSource = "not-called"
        stubResult =
            FetchedMetadata(
                title = "Batman Vol 1",
                author = null,
                description = "Comic desc",
                isbn = null,
                publisher = "DC Comics",
                publishedDate = "1986",
                source = "comicvine",
            )
        val resp =
            app(
                Request(Method.GET, "/api/metadata/search?title=Batman&source=comicvine")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, resp.status)
        assertEquals("comicvine", capturedSource)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals("DC Comics", tree.get("publisher").asText())
    }

    @Test
    fun `can request metadata from audible source`() {
        val token = registerAndGetToken()
        capturedSource = "not-called"
        stubResult =
            FetchedMetadata(
                title = "Dune",
                author = "Frank Herbert",
                description = null,
                isbn = null,
                publisher = "Macmillan Audio",
                publishedDate = "1987",
                source = "audible",
                narrator = "Scott Brick",
                durationSeconds = 21 * 3600,
            )
        val resp =
            app(
                Request(Method.GET, "/api/metadata/search?title=Dune&source=audible")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, resp.status)
        assertEquals("audible", capturedSource)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals("Scott Brick", tree.get("narrator").asText())
        assertEquals(21 * 3600, tree.get("durationSeconds").asInt())
    }

    @Test
    fun `FetchedMetadata genres and series fields serialised`() {
        val token = registerAndGetToken()
        stubResult =
            FetchedMetadata(
                title = "Foundation",
                author = "Asimov",
                description = null,
                isbn = null,
                publisher = null,
                publishedDate = null,
                source = "hardcover",
                genres = listOf("Science Fiction", "Classic"),
                series = "Foundation Series",
                seriesIndex = 1.0,
                subtitle = "The Epic Saga",
            )
        val resp =
            app(
                Request(Method.GET, "/api/metadata/search?title=Foundation")
                    .header("Cookie", "token=$token"),
            )
        val tree = Json.mapper.readTree(resp.bodyString())
        assertTrue(tree.get("genres").isArray)
        assertEquals(2, tree.get("genres").size())
        assertEquals("Science Fiction", tree.get("genres")[0].asText())
        assertEquals("Foundation Series", tree.get("series").asText())
        assertEquals(1.0, tree.get("seriesIndex").asDouble())
        assertEquals("The Epic Saga", tree.get("subtitle").asText())
    }

    @Test
    fun `missing API key source returns 404 gracefully`() {
        val token = registerAndGetToken()
        stubResult = null // simulate no result (e.g. key not configured)
        val resp =
            app(
                Request(Method.GET, "/api/metadata/search?title=Batman&source=comicvine")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.NOT_FOUND, resp.status)
    }
}
