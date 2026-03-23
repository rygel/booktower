package org.runary.integration

import org.runary.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Integration tests for the KOReader kosync protocol.
 * Tests device registration, progress push/pull, and error handling.
 */
class KOReaderSyncIntegrationTest : IntegrationTestBase() {
    @Test
    fun `register KOReader device returns token`() {
        val token = registerAndGetToken()
        val resp =
            app(
                Request(Method.POST, "/api/koreader/devices")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"deviceName":"My KOReader"}"""),
            )
        assertEquals(Status.CREATED, resp.status)
        val body = Json.mapper.readTree(resp.bodyString())
        assertNotNull(body.get("token").asText())
        assertEquals("My KOReader", body.get("deviceName").asText())
    }

    @Test
    fun `list KOReader devices returns registered devices`() {
        val token = registerAndGetToken()
        app(
            Request(Method.POST, "/api/koreader/devices")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"deviceName":"Device 1"}"""),
        )
        app(
            Request(Method.POST, "/api/koreader/devices")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"deviceName":"Device 2"}"""),
        )
        val resp =
            app(
                Request(Method.GET, "/api/koreader/devices")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, resp.status)
        val devices = Json.mapper.readTree(resp.bodyString())
        assertTrue(devices.isArray)
        assertTrue(devices.size() >= 2, "Should have at least 2 devices")
    }

    @Test
    fun `delete KOReader device removes it`() {
        val token = registerAndGetToken()
        val regResp =
            app(
                Request(Method.POST, "/api/koreader/devices")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"deviceName":"Temporary"}"""),
            )
        val deviceToken =
            Json.mapper
                .readTree(regResp.bodyString())
                .get("token")
                .asText()

        val delResp =
            app(
                Request(Method.DELETE, "/api/koreader/devices/$deviceToken")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.NO_CONTENT, delResp.status)

        val listResp =
            app(
                Request(Method.GET, "/api/koreader/devices")
                    .header("Cookie", "token=$token"),
            )
        val devices = Json.mapper.readTree(listResp.bodyString())
        val found = (0 until devices.size()).any { devices[it].get("token").asText() == deviceToken }
        assertTrue(!found, "Deleted device should not appear in list")
    }

    @Test
    fun `push and pull reading progress round-trip`() {
        val token = registerAndGetToken()
        // Create a real book so KOReader can resolve it by ID
        val libId = createLibrary(token, "kr-lib")
        val bookResp =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"KOReader Book","libraryId":"$libId"}"""),
            )
        val bookId =
            Json.mapper
                .readTree(bookResp.bodyString())
                .get("id")
                .asText()

        val regResp =
            app(
                Request(Method.POST, "/api/koreader/devices")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"deviceName":"Test Reader"}"""),
            )
        val deviceToken =
            Json.mapper
                .readTree(regResp.bodyString())
                .get("token")
                .asText()

        val pushResp =
            app(
                Request(Method.PUT, "/koreader/$deviceToken/syncs/progress")
                    .header("Content-Type", "application/json")
                    .body("""{"document":"$bookId","progress":"42/100","percentage":0.42,"device":"KOReader","device_id":"abc123"}"""),
            )
        assertEquals(Status.OK, pushResp.status)
        val pushBody = Json.mapper.readTree(pushResp.bodyString())
        assertEquals(bookId, pushBody.get("document").asText())
        assertNotNull(pushBody.get("timestamp"))

        val pullResp =
            app(
                Request(Method.GET, "/koreader/$deviceToken/syncs/progress/$bookId"),
            )
        assertEquals(Status.OK, pullResp.status)
        val pullBody = Json.mapper.readTree(pullResp.bodyString())
        assertEquals(bookId, pullBody.get("document").asText())
    }

    @Test
    fun `pull progress for unknown document returns 404`() {
        val token = registerAndGetToken()
        val regResp =
            app(
                Request(Method.POST, "/api/koreader/devices")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"deviceName":"Test"}"""),
            )
        val deviceToken =
            Json.mapper
                .readTree(regResp.bodyString())
                .get("token")
                .asText()

        val resp =
            app(
                Request(Method.GET, "/koreader/$deviceToken/syncs/progress/nonexistent.epub"),
            )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `invalid device token returns 401`() {
        val resp =
            app(
                Request(Method.PUT, "/koreader/invalid-token/syncs/progress")
                    .header("Content-Type", "application/json")
                    .body("""{"document":"test.epub","progress":"1/10","percentage":0.1}"""),
            )
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `push progress updates existing entry`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token, "kr-update-lib")
        val bookResp =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Updatable Book","libraryId":"$libId"}"""),
            )
        val bookId =
            Json.mapper
                .readTree(bookResp.bodyString())
                .get("id")
                .asText()

        val regResp =
            app(
                Request(Method.POST, "/api/koreader/devices")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"deviceName":"Updater"}"""),
            )
        val deviceToken =
            Json.mapper
                .readTree(regResp.bodyString())
                .get("token")
                .asText()

        app(
            Request(Method.PUT, "/koreader/$deviceToken/syncs/progress")
                .header("Content-Type", "application/json")
                .body("""{"document":"$bookId","progress":"10/200","percentage":0.05}"""),
        )
        app(
            Request(Method.PUT, "/koreader/$deviceToken/syncs/progress")
                .header("Content-Type", "application/json")
                .body("""{"document":"$bookId","progress":"150/200","percentage":0.75}"""),
        )

        val pullResp =
            app(
                Request(Method.GET, "/koreader/$deviceToken/syncs/progress/$bookId"),
            )
        assertEquals(Status.OK, pullResp.status)
        val body = Json.mapper.readTree(pullResp.bodyString())
        assertEquals(bookId, body.get("document").asText())
    }

    @Test
    fun `device registration requires authentication`() {
        val resp =
            app(
                Request(Method.POST, "/api/koreader/devices")
                    .header("Content-Type", "application/json")
                    .body("""{"deviceName":"Unauthorized"}"""),
            )
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
