package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.config.Json
import org.booktower.models.LoginResponse
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Weblate endpoints require admin auth and are gated by a config flag.
 * In the test environment WeblateHandler is constructed with enabled=false,
 * so all three endpoints should return 503 Service Unavailable.
 */
class WeblateIntegrationTest : IntegrationTestBase() {
    private fun registerAdminAndGetToken(): String {
        val username = "wbadmin_${System.nanoTime()}"
        val resp =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
            )
        val userId =
            Json.mapper
                .readValue(resp.bodyString(), LoginResponse::class.java)
                .user.id
        TestFixture.database.getJdbi().useHandle<Exception> { h ->
            h.createUpdate("UPDATE users SET is_admin = true WHERE id = ?").bind(0, userId).execute()
        }
        val loginResp =
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","password":"password123"}"""),
            )
        return Json.mapper.readValue(loginResp.bodyString(), LoginResponse::class.java).token
    }

    @Test
    fun `pull returns 503 when weblate is disabled`() {
        val token = registerAdminAndGetToken()
        val r = app(Request(Method.POST, "/api/weblate/pull").header("Cookie", "token=$token"))
        assertEquals(Status.SERVICE_UNAVAILABLE, r.status)
        assertTrue(r.bodyString().contains("not enabled"), "body should explain why: ${r.bodyString()}")
    }

    @Test
    fun `push returns 503 when weblate is disabled`() {
        val token = registerAdminAndGetToken()
        val r = app(Request(Method.POST, "/api/weblate/push").header("Cookie", "token=$token"))
        assertEquals(Status.SERVICE_UNAVAILABLE, r.status)
        assertTrue(r.bodyString().contains("not enabled"), "body should explain why: ${r.bodyString()}")
    }

    @Test
    fun `status returns 503 when weblate is disabled`() {
        val token = registerAdminAndGetToken()
        val r = app(Request(Method.GET, "/api/weblate/status").header("Cookie", "token=$token"))
        assertEquals(Status.SERVICE_UNAVAILABLE, r.status)
        assertTrue(r.bodyString().contains("not enabled"), "body should explain why: ${r.bodyString()}")
    }

    @Test
    fun `pull response has json content-type`() {
        val token = registerAdminAndGetToken()
        val r = app(Request(Method.POST, "/api/weblate/pull").header("Cookie", "token=$token"))
        assertTrue(
            r.header("Content-Type")?.contains("application/json") == true,
            "expected JSON content-type, got: ${r.header("Content-Type")}",
        )
    }
}
