package org.booktower.integration

import org.booktower.config.Json
import org.booktower.services.NotificationService
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationIntegrationTest : IntegrationTestBase() {
    /** Publish a notification directly via the service (bypasses HTTP for setup). */
    private fun publishDirect(
        token: String,
        type: String = "info",
        title: String = "Test",
    ): String {
        // We need the userId from the token — easier to call the service via the test JDBI
        val jdbi =
            org.booktower.TestFixture.database
                .getJdbi()
        val svc = NotificationService(jdbi)
        // Decode userId from JWT
        val jwt =
            com.auth0.jwt.JWT
                .decode(token)
        val userId = java.util.UUID.fromString(jwt.subject)
        return svc.publish(userId, type, title).id
    }

    @Test
    fun `GET notifications returns empty list initially`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/api/notifications").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertEquals(0, Json.mapper.readTree(resp.bodyString()).size())
    }

    @Test
    fun `GET notifications returns published notification`() {
        val token = registerAndGetToken()
        publishDirect(token, "scan.complete", "Library scan finished")

        val resp = app(Request(Method.GET, "/api/notifications").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val arr = Json.mapper.readTree(resp.bodyString())
        assertEquals(1, arr.size())
        assertEquals("scan.complete", arr.get(0).get("type").asText())
        assertEquals("Library scan finished", arr.get(0).get("title").asText())
    }

    @Test
    fun `GET notifications with unread=true filters read ones`() {
        val token = registerAndGetToken()
        val id = publishDirect(token, "info", "Unread")
        publishDirect(token, "info", "Also Unread")

        // Mark first one read
        app(Request(Method.POST, "/api/notifications/$id/read").header("Cookie", "token=$token"))

        val resp = app(Request(Method.GET, "/api/notifications?unread=true").header("Cookie", "token=$token"))
        val arr = Json.mapper.readTree(resp.bodyString())
        assertEquals(1, arr.size())
        assertEquals("Also Unread", arr.get(0).get("title").asText())
    }

    @Test
    fun `POST mark read marks notification as read`() {
        val token = registerAndGetToken()
        val id = publishDirect(token)

        val resp = app(Request(Method.POST, "/api/notifications/$id/read").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)

        val listResp = app(Request(Method.GET, "/api/notifications?unread=true").header("Cookie", "token=$token"))
        assertEquals(0, Json.mapper.readTree(listResp.bodyString()).size())
    }

    @Test
    fun `POST read-all marks all as read`() {
        val token = registerAndGetToken()
        publishDirect(token, "info", "N1")
        publishDirect(token, "info", "N2")

        val resp = app(Request(Method.POST, "/api/notifications/read-all").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val marked =
            Json.mapper
                .readTree(resp.bodyString())
                .get("marked")
                .asInt()
        assertEquals(2, marked)

        val unread = app(Request(Method.GET, "/api/notifications?unread=true").header("Cookie", "token=$token"))
        assertEquals(0, Json.mapper.readTree(unread.bodyString()).size())
    }

    @Test
    fun `DELETE removes notification`() {
        val token = registerAndGetToken()
        val id = publishDirect(token)

        val delResp = app(Request(Method.DELETE, "/api/notifications/$id").header("Cookie", "token=$token"))
        assertEquals(Status.NO_CONTENT, delResp.status)

        val listResp = app(Request(Method.GET, "/api/notifications").header("Cookie", "token=$token"))
        assertEquals(0, Json.mapper.readTree(listResp.bodyString()).size())
    }

    @Test
    fun `GET stream returns text event-stream with heartbeat`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/api/notifications/stream").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertTrue(resp.header("Content-Type")?.contains("text/event-stream") == true)
        val body = resp.bodyString()
        assertTrue(body.contains("event: heartbeat"))
    }

    @Test
    fun `GET stream includes unread notifications as events`() {
        val token = registerAndGetToken()
        publishDirect(token, "task.done", "Export complete")

        val resp = app(Request(Method.GET, "/api/notifications/stream").header("Cookie", "token=$token"))
        val body = resp.bodyString()
        assertTrue(body.contains("event: notification"))
        assertTrue(body.contains("task.done"))
    }

    @Test
    fun `GET notifications count returns zero initially`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/api/notifications/count").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertEquals(
            0,
            Json.mapper
                .readTree(resp.bodyString())
                .get("count")
                .asInt(),
        )
    }

    @Test
    fun `GET notifications count reflects unread count`() {
        val token = registerAndGetToken()
        publishDirect(token, "info", "N1")
        publishDirect(token, "info", "N2")

        val resp = app(Request(Method.GET, "/api/notifications/count").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertEquals(
            2,
            Json.mapper
                .readTree(resp.bodyString())
                .get("count")
                .asInt(),
        )
    }

    @Test
    fun `GET notifications count decreases after mark-all-read`() {
        val token = registerAndGetToken()
        publishDirect(token, "info", "N1")
        publishDirect(token, "info", "N2")
        app(Request(Method.POST, "/api/notifications/read-all").header("Cookie", "token=$token"))

        val resp = app(Request(Method.GET, "/api/notifications/count").header("Cookie", "token=$token"))
        assertEquals(
            0,
            Json.mapper
                .readTree(resp.bodyString())
                .get("count")
                .asInt(),
        )
    }

    @Test
    fun `notifications endpoints require authentication`() {
        val resp = app(Request(Method.GET, "/api/notifications"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `users only see their own notifications`() {
        val token1 = registerAndGetToken("user1")
        val token2 = registerAndGetToken("user2")
        publishDirect(token1, "info", "User1's notification")

        val resp = app(Request(Method.GET, "/api/notifications").header("Cookie", "token=$token2"))
        assertEquals(0, Json.mapper.readTree(resp.bodyString()).size())
    }
}
