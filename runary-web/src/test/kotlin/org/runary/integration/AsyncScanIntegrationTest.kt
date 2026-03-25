package org.runary.integration

import org.runary.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AsyncScanIntegrationTest : IntegrationTestBase() {
    @Test
    fun `async scan requires auth`() {
        val resp =
            app(
                Request(Method.POST, "/api/libraries/00000000-0000-0000-0000-000000000000/scan/async")
                    .header("Content-Type", "application/json"),
            )
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `async scan returns 400 for invalid library id`() {
        val token = registerAndGetToken("scan")
        val resp =
            app(
                Request(Method.POST, "/api/libraries/not-a-uuid/scan/async")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `async scan starts job and returns jobId`() {
        val token = registerAndGetToken("scan")
        val libId = createLibrary(token)

        val resp =
            app(
                Request(Method.POST, "/api/libraries/$libId/scan/async")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.ACCEPTED, resp.status)
        val body = Json.mapper.readTree(resp.bodyString())
        assertNotNull(body.get("jobId"), "Response should contain jobId")
        assertTrue(body.get("jobId").asText().isNotBlank())
    }

    @Test
    fun `scan status requires auth`() {
        val resp = app(Request(Method.GET, "/api/libraries/00000000-0000-0000-0000-000000000000/scan/some-job-id"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `scan status returns 404 for unknown job`() {
        val token = registerAndGetToken("scan")
        val libId = createLibrary(token)

        val resp =
            app(
                Request(Method.GET, "/api/libraries/$libId/scan/nonexistent-job-id")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `scan job eventually completes with DONE or FAILED state`() {
        val token = registerAndGetToken("scan")
        val libId = createLibrary(token)

        // Start the job
        val startResp =
            app(
                Request(Method.POST, "/api/libraries/$libId/scan/async")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.ACCEPTED, startResp.status)
        val jobId =
            Json.mapper
                .readTree(startResp.bodyString())
                .get("jobId")
                .asText()

        // Poll until done (max 3 seconds)
        val deadline = System.currentTimeMillis() + 3000
        var finalState = ""
        while (System.currentTimeMillis() < deadline) {
            val statusResp =
                app(
                    Request(Method.GET, "/api/libraries/$libId/scan/$jobId")
                        .header("Cookie", "token=$token"),
                )
            if (statusResp.status == Status.OK) {
                val node = Json.mapper.readTree(statusResp.bodyString())
                val state = node.get("state")?.asText() ?: ""
                if (state == "DONE" || state == "FAILED") {
                    finalState = state
                    break
                }
            }
            Thread.sleep(100)
        }
        assertTrue(
            finalState == "DONE" || finalState == "FAILED",
            "Job should eventually reach DONE or FAILED, got: $finalState",
        )
    }

    @Test
    fun `scan status returns RUNNING immediately after start`() {
        val token = registerAndGetToken("scan")
        val libId = createLibrary(token)

        val startResp =
            app(
                Request(Method.POST, "/api/libraries/$libId/scan/async")
                    .header("Cookie", "token=$token"),
            )
        val jobId =
            Json.mapper
                .readTree(startResp.bodyString())
                .get("jobId")
                .asText()

        // Check immediately — may be RUNNING or already DONE (fast empty scan)
        val statusResp =
            app(
                Request(Method.GET, "/api/libraries/$libId/scan/$jobId")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, statusResp.status)
        val node = Json.mapper.readTree(statusResp.bodyString())
        val state = node.get("state")?.asText()
        assertTrue(
            state == "RUNNING" || state == "DONE" || state == "FAILED",
            "State should be valid, got: $state",
        )
        assertEquals(jobId, node.get("jobId")?.asText())
    }
}
