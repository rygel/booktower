package org.booktower.integration

import org.booktower.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HardcoverSyncIntegrationTest : IntegrationTestBase() {

    @Test
    fun `GET hardcover status returns not configured when no key set`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/api/user/hardcover/status").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertFalse(tree.get("configured").asBoolean())
        assertFalse(tree.get("connected").asBoolean())
    }

    @Test
    fun `PUT hardcover key stores the key`() {
        val token = registerAndGetToken()
        val putResp = app(
            Request(Method.PUT, "/api/user/hardcover/key")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"apiKey":"test-key-123"}"""),
        )
        assertEquals(Status.NO_CONTENT, putResp.status)

        // Status should now show configured (but connection will fail since test-key is fake)
        val statusResp = app(Request(Method.GET, "/api/user/hardcover/status").header("Cookie", "token=$token"))
        val tree = Json.mapper.readTree(statusResp.bodyString())
        assertTrue(tree.get("configured").asBoolean())
        // connected may be false because the key is fake — that's OK
    }

    @Test
    fun `PUT hardcover key with empty string clears the key`() {
        val token = registerAndGetToken()
        app(Request(Method.PUT, "/api/user/hardcover/key").header("Cookie", "token=$token")
            .header("Content-Type", "application/json").body("""{"apiKey":"some-key"}"""))

        app(Request(Method.PUT, "/api/user/hardcover/key").header("Cookie", "token=$token")
            .header("Content-Type", "application/json").body("""{"apiKey":""}"""))

        val statusResp = app(Request(Method.GET, "/api/user/hardcover/status").header("Cookie", "token=$token"))
        val tree = Json.mapper.readTree(statusResp.bodyString())
        assertFalse(tree.get("configured").asBoolean())
    }

    @Test
    fun `GET hardcover mapping returns 404 when no mapping exists`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp = app(Request(Method.GET, "/api/books/$bookId/hardcover/mapping").header("Cookie", "token=$token"))
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `POST sync book without API key returns sync failed result`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp = app(
            Request(Method.POST, "/api/books/$bookId/hardcover/sync")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"status":"FINISHED"}"""),
        )
        // Returns 502 because no API key means sync fails
        assertEquals(Status.BAD_GATEWAY, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertFalse(tree.get("synced").asBoolean())
        assertTrue(tree.get("message").asText().contains("API key", ignoreCase = true))
    }

    @Test
    fun `POST sync book without status or currentPage returns 400`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp = app(
            Request(Method.POST, "/api/books/$bookId/hardcover/sync")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("{}"),
        )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `hardcover endpoints require authentication`() {
        val statusResp = app(Request(Method.GET, "/api/user/hardcover/status"))
        assertEquals(Status.UNAUTHORIZED, statusResp.status)
        val keyResp = app(Request(Method.PUT, "/api/user/hardcover/key"))
        assertEquals(Status.UNAUTHORIZED, keyResp.status)
    }
}
