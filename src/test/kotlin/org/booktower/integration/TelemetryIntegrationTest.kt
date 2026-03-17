package org.booktower.integration

import org.booktower.config.Json
import org.booktower.services.TelemetryService
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TelemetryIntegrationTest : IntegrationTestBase() {

    private fun registerAdminAndGetToken(): String {
        val token = registerAndGetToken("admin")
        val jdbi = org.booktower.TestFixture.database.getJdbi()
        val userId = com.auth0.jwt.JWT.decode(token).subject
        jdbi.useHandle<Exception> { h ->
            h.createUpdate("UPDATE users SET is_admin = TRUE WHERE id = ?").bind(0, userId).execute()
        }
        val loginResp = app(
            Request(Method.POST, "/auth/login")
                .header("Content-Type", "application/json")
                .body("""{"username":"${com.auth0.jwt.JWT.decode(token).getClaim("username").asString()}","password":"password123"}"""),
        )
        return Json.mapper.readValue(loginResp.bodyString(), org.booktower.models.LoginResponse::class.java).token
    }

    @Test
    fun `GET telemetry status shows not opted in by default`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/api/telemetry/status").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertFalse(Json.mapper.readTree(resp.bodyString()).get("optedIn").asBoolean())
    }

    @Test
    fun `POST opt-in enables telemetry for user`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.POST, "/api/telemetry/opt-in").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertTrue(Json.mapper.readTree(resp.bodyString()).get("optedIn").asBoolean())

        val statusResp = app(Request(Method.GET, "/api/telemetry/status").header("Cookie", "token=$token"))
        assertTrue(Json.mapper.readTree(statusResp.bodyString()).get("optedIn").asBoolean())
    }

    @Test
    fun `POST opt-out disables telemetry`() {
        val token = registerAndGetToken()
        app(Request(Method.POST, "/api/telemetry/opt-in").header("Cookie", "token=$token"))
        val resp = app(Request(Method.POST, "/api/telemetry/opt-out").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertFalse(Json.mapper.readTree(resp.bodyString()).get("optedIn").asBoolean())
    }

    @Test
    fun `record only stores events for opted-in users`() {
        val token = registerAndGetToken()
        val jdbi = org.booktower.TestFixture.database.getJdbi()
        val svc = TelemetryService(jdbi, org.booktower.services.UserSettingsService(jdbi))
        val userId = java.util.UUID.fromString(com.auth0.jwt.JWT.decode(token).subject)

        svc.record(userId, "test.event", "payload")
        val countBefore = jdbi.withHandle<Int, Exception> { h ->
            h.createQuery("SELECT COUNT(*) FROM telemetry_events WHERE event_type = 'test.event'")
                .mapTo(java.lang.Integer::class.java).first()?.toInt() ?: 0
        }
        assertEquals(0, countBefore) // not opted in

        svc.optIn(userId)
        svc.record(userId, "test.event", "payload")
        val countAfter = jdbi.withHandle<Int, Exception> { h ->
            h.createQuery("SELECT COUNT(*) FROM telemetry_events WHERE event_type = 'test.event'")
                .mapTo(java.lang.Integer::class.java).first()?.toInt() ?: 0
        }
        assertEquals(1, countAfter)
    }

    @Test
    fun `telemetry endpoints require authentication`() {
        val resp = app(Request(Method.GET, "/api/telemetry/status"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `admin telemetry stats endpoint is admin-only`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/api/admin/telemetry/stats").header("Cookie", "token=$token"))
        assertEquals(Status.FORBIDDEN, resp.status)
    }
}
