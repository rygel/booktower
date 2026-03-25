package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.runary.config.Json
import org.runary.models.LoginResponse

class RefreshTokenIntegrationTest : IntegrationTestBase() {
    private fun registerAndLoginResponse(prefix: String = "rt"): LoginResponse {
        val u = "${prefix}_${System.nanoTime()}"
        val r =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$u","email":"$u@test.com","password":"pass1234"}"""),
            )
        return Json.mapper.readValue(r.bodyString(), LoginResponse::class.java)
    }

    @Test
    fun `login response includes refreshToken`() {
        val resp = registerAndLoginResponse()
        assertNotNull(resp.refreshToken)
        assert(resp.refreshToken!!.isNotBlank())
    }

    @Test
    fun `POST auth refresh returns new access and refresh tokens`() {
        val login = registerAndLoginResponse()
        val rt = login.refreshToken!!

        val resp =
            app(
                Request(Method.POST, "/auth/refresh")
                    .header("Content-Type", "application/json")
                    .body("""{"refreshToken":"$rt"}"""),
            )
        assertEquals(Status.OK, resp.status)
        val refreshed = Json.mapper.readValue(resp.bodyString(), LoginResponse::class.java)
        assertNotNull(refreshed.token)
        assertNotNull(refreshed.refreshToken)
        // Rotated — new refresh token differs from the old one
        assertNotEquals(rt, refreshed.refreshToken)
    }

    @Test
    fun `refresh token is rotated — old token no longer works`() {
        val login = registerAndLoginResponse()
        val rt = login.refreshToken!!

        // Use it once
        app(
            Request(Method.POST, "/auth/refresh")
                .header("Content-Type", "application/json")
                .body("""{"refreshToken":"$rt"}"""),
        )

        // Try to use the old token again
        val resp2 =
            app(
                Request(Method.POST, "/auth/refresh")
                    .header("Content-Type", "application/json")
                    .body("""{"refreshToken":"$rt"}"""),
            )
        assertEquals(Status.UNAUTHORIZED, resp2.status)
    }

    @Test
    fun `POST auth refresh with invalid token returns 401`() {
        val resp =
            app(
                Request(Method.POST, "/auth/refresh")
                    .header("Content-Type", "application/json")
                    .body("""{"refreshToken":"00000000-0000-0000-0000-000000000000"}"""),
            )
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `POST auth refresh without body returns 400`() {
        val resp =
            app(
                Request(Method.POST, "/auth/refresh")
                    .header("Content-Type", "application/json")
                    .body("{}"),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `POST auth revoke invalidates the refresh token`() {
        val login = registerAndLoginResponse()
        val rt = login.refreshToken!!

        val revoke =
            app(
                Request(Method.POST, "/auth/revoke")
                    .header("Content-Type", "application/json")
                    .body("""{"refreshToken":"$rt"}"""),
            )
        assertEquals(Status.NO_CONTENT, revoke.status)

        // Token should no longer work
        val resp =
            app(
                Request(Method.POST, "/auth/refresh")
                    .header("Content-Type", "application/json")
                    .body("""{"refreshToken":"$rt"}"""),
            )
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `new access token from refresh works for authenticated endpoint`() {
        val login = registerAndLoginResponse()
        val rt = login.refreshToken!!

        val refreshResp =
            app(
                Request(Method.POST, "/auth/refresh")
                    .header("Content-Type", "application/json")
                    .body("""{"refreshToken":"$rt"}"""),
            )
        val newToken = Json.mapper.readValue(refreshResp.bodyString(), LoginResponse::class.java).token

        // Use new access token for an authenticated endpoint
        val apiResp =
            app(
                Request(Method.GET, "/api/libraries")
                    .header("Cookie", "token=$newToken"),
            )
        assertEquals(Status.OK, apiResp.status)
    }
}
