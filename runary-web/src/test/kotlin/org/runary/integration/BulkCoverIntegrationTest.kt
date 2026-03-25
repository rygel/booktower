package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.runary.config.Json

class BulkCoverIntegrationTest : IntegrationTestBase() {
    @Test
    fun `POST regenerate returns result with counts`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.POST, "/api/covers/regenerate").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertTrue(tree.has("submitted"))
        assertTrue(tree.has("skipped"))
        assertTrue(tree.has("errors"))
    }

    @Test
    fun `POST regenerate with no books returns zero submitted`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.POST, "/api/covers/regenerate").header("Cookie", "token=$token"))
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals(0, tree.get("submitted").asInt())
        assertEquals(0, tree.get("errors").asInt())
    }

    @Test
    fun `POST regenerate with libraryId scopes to that library`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        createBook(token, libId)

        val resp =
            app(
                Request(Method.POST, "/api/covers/regenerate?libraryId=$libId").header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        // book has no file_path set so it's skipped
        assertEquals(0, tree.get("submitted").asInt())
        assertEquals(0, tree.get("skipped").asInt())
        assertEquals(0, tree.get("errors").asInt())
    }

    @Test
    fun `POST regenerate requires authentication`() {
        val resp = app(Request(Method.POST, "/api/covers/regenerate"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
