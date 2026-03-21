package org.booktower.integration

import org.booktower.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Integration tests for the Kobo sync protocol.
 * Tests device registration, library sync, reading state, and error handling.
 */
class KoboSyncIntegrationTest : IntegrationTestBase() {
    @Test
    fun `register Kobo device returns token`() {
        val token = registerAndGetToken()
        val resp =
            app(
                Request(Method.POST, "/api/kobo/devices")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"deviceName":"My Kobo Clara"}"""),
            )
        assertEquals(Status.CREATED, resp.status)
        val body = Json.mapper.readTree(resp.bodyString())
        assertNotNull(body.get("token").asText())
        assertEquals("My Kobo Clara", body.get("deviceName").asText())
    }

    @Test
    fun `list Kobo devices returns registered devices`() {
        val token = registerAndGetToken()
        app(
            Request(Method.POST, "/api/kobo/devices")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"deviceName":"Device A"}"""),
        )
        val resp = app(Request(Method.GET, "/api/kobo/devices").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val arr = Json.mapper.readTree(resp.bodyString())
        assertTrue(arr.isArray && arr.size() >= 1)
    }

    @Test
    fun `initialization endpoint returns device config`() {
        val token = registerAndGetToken()
        val regResp =
            app(
                Request(Method.POST, "/api/kobo/devices")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"deviceName":"Kobo"}"""),
            )
        val deviceToken =
            Json.mapper
                .readTree(regResp.bodyString())
                .get("token")
                .asText()

        val resp = app(Request(Method.GET, "/kobo/$deviceToken/v1/initialization"))
        assertEquals(Status.OK, resp.status)
        val body = Json.mapper.readTree(resp.bodyString())
        assertNotNull(body.get("Resources"))
        assertNotNull(body.get("DeviceId"))
    }

    @Test
    fun `library sync returns book list with sync token`() {
        val token = registerAndGetToken()
        val regResp =
            app(
                Request(Method.POST, "/api/kobo/devices")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"deviceName":"Kobo"}"""),
            )
        val deviceToken =
            Json.mapper
                .readTree(regResp.bodyString())
                .get("token")
                .asText()

        val resp = app(Request(Method.POST, "/kobo/$deviceToken/v1/library/sync"))
        assertEquals(Status.OK, resp.status)
        val body = Json.mapper.readTree(resp.bodyString())
        assertTrue(body.has("BookEntitlements"))
        assertTrue(body.has("SyncToken"))
        assertEquals(false, body.get("Continues").asBoolean())
        assertNotNull(resp.header("X-Kobo-Sync-Token"))
    }

    @Test
    fun `library snapshot returns full library`() {
        val token = registerAndGetToken()
        val regResp =
            app(
                Request(Method.POST, "/api/kobo/devices")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"deviceName":"Kobo"}"""),
            )
        val deviceToken =
            Json.mapper
                .readTree(regResp.bodyString())
                .get("token")
                .asText()

        val resp = app(Request(Method.GET, "/kobo/$deviceToken/v1/library/snapshot"))
        assertEquals(Status.OK, resp.status)
    }

    @Test
    fun `reading state update accepted for real book`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token, "KoboLib")
        val bookResp =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Kobo Book","libraryId":"$libId"}"""),
            )
        val bookId =
            Json.mapper
                .readTree(bookResp.bodyString())
                .get("id")
                .asText()

        val regResp =
            app(
                Request(Method.POST, "/api/kobo/devices")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"deviceName":"Kobo"}"""),
            )
        val deviceToken =
            Json.mapper
                .readTree(regResp.bodyString())
                .get("token")
                .asText()

        val resp =
            app(
                Request(Method.PUT, "/kobo/$deviceToken/v1/library/$bookId/reading-state")
                    .header("Content-Type", "application/json")
                    .body("""{"CurrentBookmark":{"ContentSourceProgressPercent":0.42,"Location":"epubcfi(/6/10)","LocationType":"CFI"}}"""),
            )
        assertEquals(Status.OK, resp.status)
        assertEquals(
            "RequestAccepted",
            Json.mapper
                .readTree(resp.bodyString())
                .get("RequestResult")
                .asText(),
        )
    }

    @Test
    fun `delete device revokes sync access`() {
        val token = registerAndGetToken()
        val regResp =
            app(
                Request(Method.POST, "/api/kobo/devices")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"deviceName":"Kobo"}"""),
            )
        val deviceToken =
            Json.mapper
                .readTree(regResp.bodyString())
                .get("token")
                .asText()

        app(Request(Method.DELETE, "/api/kobo/devices/$deviceToken").header("Cookie", "token=$token"))

        val initResp = app(Request(Method.GET, "/kobo/$deviceToken/v1/initialization"))
        assertEquals(Status.UNAUTHORIZED, initResp.status)
    }

    @Test
    fun `invalid device token returns 401`() {
        val resp = app(Request(Method.GET, "/kobo/bad-token/v1/initialization"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `device registration requires authentication`() {
        val resp =
            app(
                Request(Method.POST, "/api/kobo/devices")
                    .header("Content-Type", "application/json")
                    .body("""{"deviceName":"x"}"""),
            )
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
