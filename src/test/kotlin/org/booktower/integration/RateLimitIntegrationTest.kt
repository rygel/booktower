package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifies the sliding-window rate limiter on auth endpoints.
 *
 * The RateLimitFilter bypasses rate-limiting for requests with no
 * resolvable IP (normal in-process tests). We work around this by
 * sending an X-Forwarded-For header with a synthetic IP so the filter
 * treats it as a real client.
 *
 * The AppHandler configures the auth rate limit at 10 req / 60 s, so
 * the 11th request from the same IP within a window should receive 429.
 */
class RateLimitIntegrationTest : IntegrationTestBase() {
    private fun registerRequest(
        username: String,
        ip: String,
    ): org.http4k.core.Response =
        app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", ip)
                .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
        )

    private fun loginRequest(ip: String): org.http4k.core.Response =
        app(
            Request(Method.POST, "/auth/login")
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", ip)
                .body("""{"username":"nonexistent","password":"wrong"}"""),
        )

    @Test
    fun `requests within limit are allowed`() {
        val ip = "10.0.0.1"
        // First 5 requests should not be rate limited (well within 10 req/min limit)
        repeat(5) { i ->
            val username = "rl_ok_${System.nanoTime()}_$i"
            val r = registerRequest(username, ip)
            assertTrue(r.status != Status.TOO_MANY_REQUESTS, "request $i should not be rate limited")
        }
    }

    @Test
    fun `11th request from same IP within window returns 429`() {
        // Use a unique IP per test run to avoid cross-test interference
        val ip = "10.1.${(1..254).random()}.${(1..254).random()}"

        // Send 10 requests to exhaust the limit
        repeat(10) { i ->
            val username = "rl_exhaust_${System.nanoTime()}_$i"
            registerRequest(username, ip)
        }

        // The 11th should be blocked
        val r = loginRequest(ip)
        assertEquals(Status.TOO_MANY_REQUESTS, r.status)
    }

    @Test
    fun `429 response includes Retry-After header`() {
        val ip = "10.2.${(1..254).random()}.${(1..254).random()}"

        repeat(10) { i ->
            registerRequest("rl_hdr_${System.nanoTime()}_$i", ip)
        }

        val r = loginRequest(ip)
        assertEquals(Status.TOO_MANY_REQUESTS, r.status)
        assertNotNull(r.header("Retry-After"), "429 must include Retry-After header")
    }

    @Test
    fun `429 response body is json with error field`() {
        val ip = "10.3.${(1..254).random()}.${(1..254).random()}"

        repeat(10) { i ->
            registerRequest("rl_body_${System.nanoTime()}_$i", ip)
        }

        val r = loginRequest(ip)
        assertEquals(Status.TOO_MANY_REQUESTS, r.status)
        assertTrue(r.bodyString().contains("TOO_MANY_REQUESTS"), "body: ${r.bodyString()}")
    }

    @Test
    fun `different IPs have independent rate limit buckets`() {
        val ip1 = "10.4.${(1..254).random()}.1"
        val ip2 = "10.4.${(1..254).random()}.2"

        // Exhaust ip1
        repeat(10) { i ->
            registerRequest("rl_ip1_${System.nanoTime()}_$i", ip1)
        }

        // ip2 should still be allowed
        val r = loginRequest(ip2)
        assertTrue(r.status != Status.TOO_MANY_REQUESTS, "ip2 should not be rate limited by ip1's requests")
    }

    @Test
    fun `non-auth endpoints are not rate limited`() {
        val ip = "10.5.${(1..254).random()}.${(1..254).random()}"

        // Send many requests to a non-rate-limited endpoint
        repeat(20) {
            val r =
                app(
                    Request(Method.GET, "/health")
                        .header("X-Forwarded-For", ip),
                )
            assertEquals(Status.OK, r.status, "health endpoint should never be rate limited")
        }
    }
}
