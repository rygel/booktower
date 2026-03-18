package org.booktower.integration

import org.booktower.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MergeBookIntegrationTest : IntegrationTestBase() {
    @Test
    fun `merge source into target returns 200 and source is deleted`() {
        val token = registerAndGetToken("merge")
        val libId = createLibrary(token)
        val targetId = createBook(token, libId, "Target Book")
        val sourceId = createBook(token, libId, "Source Book")

        val response =
            app(
                Request(Method.POST, "/api/books/$targetId/merge")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"sourceId":"$sourceId"}"""),
            )
        assertEquals(200, response.status.code)
        val body = Json.mapper.readTree(response.bodyString())
        assertTrue(body.get("merged").asBoolean())

        // Source should be gone
        val sourceGet =
            app(
                Request(Method.GET, "/api/books/$sourceId")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(404, sourceGet.status.code)

        // Target should still exist
        val targetGet =
            app(
                Request(Method.GET, "/api/books/$targetId")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(200, targetGet.status.code)
    }

    @Test
    fun `merge with invalid source id returns 400`() {
        val token = registerAndGetToken("merge2")
        val libId = createLibrary(token)
        val targetId = createBook(token, libId)

        val response =
            app(
                Request(Method.POST, "/api/books/$targetId/merge")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"sourceId":"not-a-uuid"}"""),
            )
        assertEquals(400, response.status.code)
    }

    @Test
    fun `merge with nonexistent source returns 404`() {
        val token = registerAndGetToken("merge3")
        val libId = createLibrary(token)
        val targetId = createBook(token, libId)
        val fakeId =
            java.util.UUID
                .randomUUID()
                .toString()

        val response =
            app(
                Request(Method.POST, "/api/books/$targetId/merge")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"sourceId":"$fakeId"}"""),
            )
        assertEquals(404, response.status.code)
    }

    @Test
    fun `merge same book into itself returns 404`() {
        val token = registerAndGetToken("merge4")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response =
            app(
                Request(Method.POST, "/api/books/$bookId/merge")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"sourceId":"$bookId"}"""),
            )
        assertEquals(404, response.status.code)
    }

    @Test
    fun `merge requires authentication`() {
        val token = registerAndGetToken("merge5")
        val libId = createLibrary(token)
        val targetId = createBook(token, libId)
        val sourceId = createBook(token, libId)

        val response =
            app(
                Request(Method.POST, "/api/books/$targetId/merge")
                    .header("Content-Type", "application/json")
                    .body("""{"sourceId":"$sourceId"}"""),
            )
        assertEquals(401, response.status.code)
    }

    @Test
    fun `merge cannot steal books from another user`() {
        val ownerToken = registerAndGetToken("mergeowner")
        val otherToken = registerAndGetToken("mergeother")
        val ownerLib = createLibrary(ownerToken)
        val otherLib = createLibrary(otherToken)
        val ownerBook = createBook(ownerToken, ownerLib)
        val otherBook = createBook(otherToken, otherLib)

        // Other user tries to merge owner's book into their own book
        val response =
            app(
                Request(Method.POST, "/api/books/$otherBook/merge")
                    .header("Cookie", "token=$otherToken")
                    .header("Content-Type", "application/json")
                    .body("""{"sourceId":"$ownerBook"}"""),
            )
        // ownerBook doesn't belong to otherToken, so returns 404
        assertEquals(404, response.status.code)
    }

    @Test
    fun `merge moves tags from source to target`() {
        val token = registerAndGetToken("mergetag")
        val libId = createLibrary(token)
        val targetId = createBook(token, libId, "Target")
        val sourceId = createBook(token, libId, "Source")

        // Add a tag to source via the UI form endpoint
        app(
            Request(Method.POST, "/ui/books/$sourceId/tags")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("tags=unique-source-tag"),
        )

        // Merge
        val mergeResponse =
            app(
                Request(Method.POST, "/api/books/$targetId/merge")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"sourceId":"$sourceId"}"""),
            )
        assertEquals(200, mergeResponse.status.code)

        // Target should have the tag
        val targetGet = app(Request(Method.GET, "/api/books/$targetId").header("Cookie", "token=$token"))
        assertEquals(200, targetGet.status.code)
        val body = targetGet.bodyString()
        assertTrue(body.contains("unique-source-tag"), "Expected tag to be present in target: $body")
    }
}
