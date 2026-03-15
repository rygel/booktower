package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HealthIntegrationTest : IntegrationTestBase() {

    @Test
    fun `health endpoint returns 200`() {
        val r = app(Request(Method.GET, "/health"))
        assertEquals(Status.OK, r.status)
    }

    @Test
    fun `health endpoint returns json content-type`() {
        val r = app(Request(Method.GET, "/health"))
        assertTrue(
            r.header("Content-Type")?.contains("application/json") == true,
            "expected JSON content-type, got: ${r.header("Content-Type")}",
        )
    }

    @Test
    fun `health endpoint body contains ok status`() {
        val r = app(Request(Method.GET, "/health"))
        assertTrue(r.bodyString().contains("ok"), "body: ${r.bodyString()}")
    }

    @Test
    fun `health endpoint does not require authentication`() {
        // No cookie, no auth header — should still return 200
        val r = app(Request(Method.GET, "/health"))
        assertEquals(Status.OK, r.status)
    }
}
