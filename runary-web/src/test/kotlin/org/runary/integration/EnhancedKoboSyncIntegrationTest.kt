package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.runary.config.Json

class EnhancedKoboSyncIntegrationTest : IntegrationTestBase() {
    private fun registerDevice(
        token: String,
        deviceName: String = "TestKobo",
    ): String {
        val resp =
            app(
                Request(Method.POST, "/api/kobo/devices")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"deviceName":"$deviceName"}"""),
            )
        return Json.mapper
            .readTree(resp.bodyString())
            .get("token")
            .asText()
    }

    @Test
    fun `sync without token returns all books`() {
        val token = registerAndGetToken()
        val devToken = registerDevice(token)
        val libId = createLibrary(token)
        createBook(token, libId, "Book A")
        createBook(token, libId, "Book B")

        val resp = app(Request(Method.POST, "/kobo/$devToken/v1/library/sync"))
        assertEquals(Status.OK, resp.status)
        val body = Json.mapper.readTree(resp.bodyString())
        assertTrue(body.get("BookEntitlements").isArray)
        assertEquals(2, body.get("BookEntitlements").size())
        // Should return a sync token header
        assertNotNull(resp.header("X-Kobo-Sync-Token"))
    }

    @Test
    fun `sync with recent token returns empty delta when nothing changed`() {
        val token = registerAndGetToken()
        val devToken = registerDevice(token)
        val libId = createLibrary(token)
        createBook(token, libId, "Book A")

        // First sync to get a token
        val firstResp = app(Request(Method.POST, "/kobo/$devToken/v1/library/sync"))
        val syncToken = firstResp.header("X-Kobo-Sync-Token")!!

        // Wait a ms to ensure timestamp diff, then sync again with that token
        // Use a timestamp 48 hours in the future to safely exceed any timezone offset in H2
        val futureToken = (System.currentTimeMillis() + 48L * 3600 * 1000).toString()
        val deltaResp =
            app(
                Request(Method.POST, "/kobo/$devToken/v1/library/sync")
                    .header("X-Kobo-Sync-Token", futureToken),
            )
        assertEquals(Status.OK, deltaResp.status)
        val body = Json.mapper.readTree(deltaResp.bodyString())
        assertTrue(body.get("BookEntitlements").isArray)
        // No books updated after a future timestamp
        assertEquals(0, body.get("BookEntitlements").size())
    }

    @Test
    fun `sync with old token returns all books as delta`() {
        val token = registerAndGetToken()
        val devToken = registerDevice(token)
        val libId = createLibrary(token)
        createBook(token, libId, "Book A")

        // Use epoch as old token — all books are newer
        val oldToken = "0"
        val resp =
            app(
                Request(Method.POST, "/kobo/$devToken/v1/library/sync")
                    .header("X-Kobo-Sync-Token", oldToken),
            )
        assertEquals(Status.OK, resp.status)
        val body = Json.mapper.readTree(resp.bodyString())
        assertEquals(1, body.get("BookEntitlements").size())
    }

    @Test
    fun `snapshot returns all books with structure`() {
        val token = registerAndGetToken()
        val devToken = registerDevice(token)
        val libId = createLibrary(token)
        createBook(token, libId, "Snap A")
        createBook(token, libId, "Snap B")

        val resp = app(Request(Method.GET, "/kobo/$devToken/v1/library/snapshot"))
        assertEquals(Status.OK, resp.status)
        val body = Json.mapper.readTree(resp.bodyString())
        assertTrue(body.get("Snapshot").isArray)
        assertEquals(2, body.get("Snapshot").size())
        assertNotNull(body.get("SnapshotTimestamp"))
        assertEquals(2, body.get("TotalCount").asInt())
    }

    @Test
    fun `reading-state with CFI location stores location type`() {
        val token = registerAndGetToken()
        val devToken = registerDevice(token)
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "CFI Book")

        val stateBody = """{
            "CurrentBookmark": {
                "ContentSourceProgressPercent": 0.5,
                "Location": "epubcfi(/6/4[chap01]!/4/2/10/2/1:0)",
                "LocationType": "CFI"
            }
        }"""
        val resp =
            app(
                Request(Method.PUT, "/kobo/$devToken/v1/library/$bookId/reading-state")
                    .header("Content-Type", "application/json")
                    .body(stateBody),
            )
        assertEquals(Status.OK, resp.status)
        val body = Json.mapper.readTree(resp.bodyString())
        assertEquals("RequestAccepted", body.get("RequestResult").asText())

        // Now verify the location appears in the sync response
        val syncResp = app(Request(Method.POST, "/kobo/$devToken/v1/library/sync"))
        val books = Json.mapper.readTree(syncResp.bodyString()).get("BookEntitlements")
        val theBook =
            (0 until books.size())
                .map { books[it] }
                .firstOrNull { it.get("EntitlementId").asText() == bookId }
        assertNotNull(theBook)
        val bookmark = theBook!!.path("NewEntitlement").path("ReadingState").path("CurrentBookmark")
        assertEquals("epubcfi(/6/4[chap01]!/4/2/10/2/1:0)", bookmark.get("Location").asText())
        assertEquals("CFI", bookmark.get("LocationType").asText())
    }

    @Test
    fun `reading-state with numeric location stores progress`() {
        val token = registerAndGetToken()
        val devToken = registerDevice(token)
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Pct Book")

        val resp =
            app(
                Request(Method.PUT, "/kobo/$devToken/v1/library/$bookId/reading-state")
                    .header("Content-Type", "application/json")
                    .body("""{"CurrentBookmark":{"ContentSourceProgressPercent":0.25,"Location":"50","LocationType":"PageNumber"}}"""),
            )
        assertEquals(Status.OK, resp.status)
    }

    @Test
    fun `snapshot requires valid device token`() {
        val resp = app(Request(Method.GET, "/kobo/bad-token/v1/library/snapshot"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
