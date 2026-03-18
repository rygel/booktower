package org.booktower.integration

import org.booktower.config.Json
import org.booktower.models.FetchedMetadata
import org.booktower.services.MetadataFetchService
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MetadataProposalIntegrationTest : IntegrationTestBase() {
    private val stubMeta =
        FetchedMetadata(
            title = "Proposed Title",
            author = "Proposed Author",
            description = "Proposed desc",
            isbn = "9780000000099",
            publisher = "Pub",
            publishedDate = "2020",
            source = "openlibrary",
        )

    override fun createMetadataFetchService() =
        object : MetadataFetchService() {
            override fun fetchMetadata(
                title: String,
                author: String?,
                source: String?,
            ) = stubMeta
        }

    private fun proposeUrl(bookId: String) = "/api/books/$bookId/metadata/propose"

    private fun proposalsUrl(bookId: String) = "/api/books/$bookId/metadata/proposals"

    @Test
    fun `POST propose creates a pending proposal`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp =
            app(
                Request(Method.POST, proposeUrl(bookId))
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"source":"openlibrary"}"""),
            )
        assertEquals(Status.CREATED, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals("PENDING", tree.get("status").asText())
        assertEquals("openlibrary", tree.get("source").asText())
        assertEquals("Proposed Title", tree.path("metadata").get("title").asText())
    }

    @Test
    fun `GET proposals lists pending proposals for a book`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        app(
            Request(Method.POST, proposeUrl(bookId))
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("{}"),
        )
        app(
            Request(Method.POST, proposeUrl(bookId))
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("{}"),
        )

        val resp = app(Request(Method.GET, proposalsUrl(bookId)).header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val arr = Json.mapper.readTree(resp.bodyString())
        assertTrue(arr.isArray)
        assertEquals(2, arr.size())
    }

    @Test
    fun `POST apply applies the proposal and updates book`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Original Title")

        val propResp =
            app(
                Request(Method.POST, proposeUrl(bookId))
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("{}"),
            )
        val proposalId =
            Json.mapper
                .readTree(propResp.bodyString())
                .get("id")
                .asText()

        val applyResp =
            app(
                Request(Method.POST, "/api/books/$bookId/metadata/proposals/$proposalId/apply")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, applyResp.status)
        val book = Json.mapper.readTree(applyResp.bodyString())
        assertEquals("Proposed Title", book.get("title").asText())
        assertEquals("Proposed Author", book.get("author").asText())
    }

    @Test
    fun `applied proposal no longer appears in pending list`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val propResp =
            app(
                Request(Method.POST, proposeUrl(bookId))
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("{}"),
            )
        val proposalId =
            Json.mapper
                .readTree(propResp.bodyString())
                .get("id")
                .asText()

        app(
            Request(Method.POST, "/api/books/$bookId/metadata/proposals/$proposalId/apply")
                .header("Cookie", "token=$token"),
        )

        val listResp = app(Request(Method.GET, proposalsUrl(bookId)).header("Cookie", "token=$token"))
        val arr = Json.mapper.readTree(listResp.bodyString())
        assertEquals(0, arr.size())
    }

    @Test
    fun `DELETE proposal dismisses it`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val propResp =
            app(
                Request(Method.POST, proposeUrl(bookId))
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("{}"),
            )
        val proposalId =
            Json.mapper
                .readTree(propResp.bodyString())
                .get("id")
                .asText()

        val delResp =
            app(
                Request(Method.DELETE, "/api/books/$bookId/metadata/proposals/$proposalId")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.NO_CONTENT, delResp.status)

        val listResp = app(Request(Method.GET, proposalsUrl(bookId)).header("Cookie", "token=$token"))
        assertEquals(0, Json.mapper.readTree(listResp.bodyString()).size())
    }

    @Test
    fun `cannot apply proposal that does not belong to user`() {
        val token1 = registerAndGetToken("mp1")
        val token2 = registerAndGetToken("mp2")
        val libId = createLibrary(token1)
        val bookId = createBook(token1, libId)

        val propResp =
            app(
                Request(Method.POST, proposeUrl(bookId))
                    .header("Cookie", "token=$token1")
                    .header("Content-Type", "application/json")
                    .body("{}"),
            )
        val proposalId =
            Json.mapper
                .readTree(propResp.bodyString())
                .get("id")
                .asText()

        // user2 tries to apply user1's proposal
        val applyResp =
            app(
                Request(Method.POST, "/api/books/$bookId/metadata/proposals/$proposalId/apply")
                    .header("Cookie", "token=$token2"),
            )
        // book doesn't belong to user2 so not found
        assertTrue(applyResp.status.code >= 400)
    }

    @Test
    fun `propose requires authentication`() {
        val resp = app(Request(Method.POST, "/api/books/00000000-0000-0000-0000-000000000001/metadata/propose"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
