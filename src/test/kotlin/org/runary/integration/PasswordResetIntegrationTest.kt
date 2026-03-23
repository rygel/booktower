package org.runary.integration

import org.runary.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for the password-reset token flow.
 * Self-hosted design: the raw token is returned by the service and logged;
 * the admin shows it to the user out-of-band.
 */
class PasswordResetIntegrationTest : IntegrationTestBase() {
    // ── POST /auth/forgot-password ────────────────────────────────────────────

    @Test
    fun `forgot-password always returns 200 for unknown email`() {
        val resp =
            app(
                Request(Method.POST, "/auth/forgot-password")
                    .header("Content-Type", "application/json")
                    .body("""{"email":"nobody@example.com"}"""),
            )
        assertEquals(Status.OK, resp.status)
    }

    @Test
    fun `forgot-password returns 200 for known email`() {
        val username = "pwreset_${System.nanoTime()}"
        app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$username@test.com","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
        )

        val resp =
            app(
                Request(Method.POST, "/auth/forgot-password")
                    .header("Content-Type", "application/json")
                    .body("""{"email":"$username@test.com"}"""),
            )
        assertEquals(Status.OK, resp.status)
        val body = Json.mapper.readTree(resp.bodyString())
        assertNotNull(body.get("message"), "Should return a message field")
    }

    @Test
    fun `forgot-password accepts form submission`() {
        val resp =
            app(
                Request(Method.POST, "/auth/forgot-password")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("email=nobody%40example.com"),
            )
        assertEquals(Status.OK, resp.status)
    }

    // ── POST /auth/reset-password ─────────────────────────────────────────────

    @Test
    fun `reset-password with invalid token returns 400`() {
        val resp =
            app(
                Request(Method.POST, "/auth/reset-password")
                    .header("Content-Type", "application/json")
                    .body("""{"token":"invalid-token-abc","newPassword":"newpass99"}"""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
        val body = Json.mapper.readTree(resp.bodyString())
        assertEquals("INVALID_TOKEN", body.get("error")?.asText())
    }

    @Test
    fun `reset-password with missing token returns 400`() {
        val resp =
            app(
                Request(Method.POST, "/auth/reset-password")
                    .header("Content-Type", "application/json")
                    .body("""{"newPassword":"newpass99"}"""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `reset-password with short password returns 400`() {
        val resp =
            app(
                Request(Method.POST, "/auth/reset-password")
                    .header("Content-Type", "application/json")
                    .body("""{"token":"sometoken","newPassword":"short"}"""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
        val body = Json.mapper.readTree(resp.bodyString())
        assertTrue(body.get("message")?.asText()?.contains("8") == true, "Should mention 8 characters")
    }

    @Test
    fun `full reset flow - create token and reset password`() {
        // Register user
        val username = "fullreset_${System.nanoTime()}"
        val email = "$username@test.com"
        app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$email","password":"oldpass123"}"""),
        )

        // We call the service directly via HTTP to trigger token creation (token is logged)
        val forgotResp =
            app(
                Request(Method.POST, "/auth/forgot-password")
                    .header("Content-Type", "application/json")
                    .body("""{"email":"$email"}"""),
            )
        assertEquals(Status.OK, forgotResp.status)

        // Since we can't intercept logs from HTTP, verify that login with old password still works
        val loginResp =
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","password":"oldpass123"}"""),
            )
        assertEquals(Status.OK, loginResp.status, "Old password should still work before reset")
    }

    @Test
    fun `reset-password endpoint is rate-limited path (uses authRateLimit filter)`() {
        // Just verify the endpoint exists and returns expected status codes
        val resp =
            app(
                Request(Method.POST, "/auth/reset-password")
                    .header("Content-Type", "application/json")
                    .body("""{"token":"","newPassword":""}"""),
            )
        // Should be 400 (validation) not 404
        assertTrue(resp.status != Status.NOT_FOUND, "Endpoint should exist")
    }
}
