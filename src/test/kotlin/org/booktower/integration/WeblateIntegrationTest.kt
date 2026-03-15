package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Weblate endpoints are gated by a config flag.
 * In the test environment the WeblateHandler is constructed with
 * enabled=false (see IntegrationTestBase), so all three endpoints
 * should return 503 Service Unavailable with an error body.
 */
class WeblateIntegrationTest : IntegrationTestBase() {

    @Test
    fun `pull returns 503 when weblate is disabled`() {
        val r = app(Request(Method.POST, "/api/weblate/pull"))
        assertEquals(Status.SERVICE_UNAVAILABLE, r.status)
        assertTrue(r.bodyString().contains("not enabled"), "body should explain why: ${r.bodyString()}")
    }

    @Test
    fun `push returns 503 when weblate is disabled`() {
        val r = app(Request(Method.POST, "/api/weblate/push"))
        assertEquals(Status.SERVICE_UNAVAILABLE, r.status)
        assertTrue(r.bodyString().contains("not enabled"), "body should explain why: ${r.bodyString()}")
    }

    @Test
    fun `status returns 503 when weblate is disabled`() {
        val r = app(Request(Method.GET, "/api/weblate/status"))
        assertEquals(Status.SERVICE_UNAVAILABLE, r.status)
        assertTrue(r.bodyString().contains("not enabled"), "body should explain why: ${r.bodyString()}")
    }

    @Test
    fun `pull response has json content-type`() {
        val r = app(Request(Method.POST, "/api/weblate/pull"))
        assertTrue(
            r.header("Content-Type")?.contains("application/json") == true,
            "expected JSON content-type, got: ${r.header("Content-Type")}",
        )
    }
}
