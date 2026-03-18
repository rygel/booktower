package org.booktower.integration

import org.booktower.config.Json
import org.booktower.models.CommunityRatingDto
import org.http4k.core.Method
import org.http4k.core.Request
import org.jdbi.v3.core.Jdbi
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CommunityRatingIntegrationTest : IntegrationTestBase() {
    /** Override to inject a fake service that returns a known result without HTTP calls */
    override fun createCommunityRatingService(jdbi: Jdbi) =
        object : org.booktower.services.CommunityRatingService(jdbi) {
            override fun fetchFromGoogleBooks(
                googleBooksId: String?,
                isbn: String?,
                title: String?,
            ): CommunityRatingDto? =
                if (!isbn.isNullOrBlank() || !googleBooksId.isNullOrBlank() || !title.isNullOrBlank()) {
                    CommunityRatingDto(rating = 4.2, count = 1500, source = "googlebooks", fetchedAt = null)
                } else {
                    null
                }

            override fun fetchFromOpenLibrary(
                openLibraryId: String?,
                isbn: String?,
            ): CommunityRatingDto? = null
        }

    @Test
    fun `fetch community rating stores and returns result`() {
        val token = registerAndGetToken("cr1")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "The Great Gatsby")

        val response =
            app(
                Request(Method.POST, "/api/books/$bookId/community-rating/fetch")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(200, response.status.code)
        val body = Json.mapper.readTree(response.bodyString())
        assertEquals(4.2, body.get("rating").asDouble(), 0.01)
        assertEquals(1500, body.get("count").asInt())
        assertEquals("googlebooks", body.get("source").asText())
    }

    @Test
    fun `get stored community rating returns previously fetched data`() {
        val token = registerAndGetToken("cr2")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Some Book")

        // Fetch first
        app(Request(Method.POST, "/api/books/$bookId/community-rating/fetch").header("Cookie", "token=$token"))

        // Then get stored
        val response =
            app(
                Request(Method.GET, "/api/books/$bookId/community-rating")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(200, response.status.code)
        val body = Json.mapper.readTree(response.bodyString())
        assertEquals(4.2, body.get("rating").asDouble(), 0.01)
        assertNotNull(body.get("fetchedAt"))
    }

    @Test
    fun `get community rating before fetch returns null fields`() {
        val token = registerAndGetToken("cr3")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Unfetched Book")

        val response =
            app(
                Request(Method.GET, "/api/books/$bookId/community-rating")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(200, response.status.code)
        val body = Json.mapper.readTree(response.bodyString())
        // rating should be null (never fetched)
        assert(body.get("rating").isNull || !body.has("rating") || body.get("rating").asDouble() == 0.0)
    }

    @Test
    fun `community rating included in book dto after fetch`() {
        val token = registerAndGetToken("cr4")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Rated Book")

        // Fetch rating
        app(Request(Method.POST, "/api/books/$bookId/community-rating/fetch").header("Cookie", "token=$token"))

        // Get book and check communityRating field
        val response = app(Request(Method.GET, "/api/books/$bookId").header("Cookie", "token=$token"))
        assertEquals(200, response.status.code)
        val body = Json.mapper.readTree(response.bodyString())
        assertEquals(4.2, body.get("communityRating").asDouble(), 0.01)
        assertEquals(1500, body.get("communityRatingCount").asInt())
        assertEquals("googlebooks", body.get("communityRatingSource").asText())
    }

    @Test
    fun `fetch requires authentication`() {
        val token = registerAndGetToken("cr5")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response = app(Request(Method.POST, "/api/books/$bookId/community-rating/fetch"))
        assertEquals(401, response.status.code)
    }

    @Test
    fun `fetch for nonexistent book returns 404`() {
        val token = registerAndGetToken("cr6")
        val fakeId =
            java.util.UUID
                .randomUUID()
                .toString()

        val response =
            app(
                Request(Method.POST, "/api/books/$fakeId/community-rating/fetch")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(404, response.status.code)
    }
}
