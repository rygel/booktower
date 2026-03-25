package org.runary.integration

import org.runary.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SetupWizardIntegrationTest : IntegrationTestBase() {
    @Test
    fun `GET setup status shows all steps incomplete for new user`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/api/setup/status").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertFalse(tree.get("completed").asBoolean())
        val steps = tree.get("steps")
        assertFalse(steps.get("profile").asBoolean())
        assertFalse(steps.get("library").asBoolean())
        assertFalse(steps.get("import").asBoolean())
        assertFalse(steps.get("preferences").asBoolean())
    }

    @Test
    fun `POST complete step marks it done`() {
        val token = registerAndGetToken()
        val resp =
            app(
                Request(Method.POST, "/api/setup/steps/profile/complete")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals("profile", tree.get("step").asText())
        assertTrue(tree.get("completed").asBoolean())
    }

    @Test
    fun `GET status shows completed step`() {
        val token = registerAndGetToken()
        app(Request(Method.POST, "/api/setup/steps/library/complete").header("Cookie", "token=$token"))

        val resp = app(Request(Method.GET, "/api/setup/status").header("Cookie", "token=$token"))
        val steps = Json.mapper.readTree(resp.bodyString()).get("steps")
        assertTrue(steps.get("library").asBoolean())
        assertFalse(steps.get("profile").asBoolean())
    }

    @Test
    fun `POST complete marks wizard as done`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.POST, "/api/setup/complete").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertTrue(
            Json.mapper
                .readTree(resp.bodyString())
                .get("completed")
                .asBoolean(),
        )

        val statusResp = app(Request(Method.GET, "/api/setup/status").header("Cookie", "token=$token"))
        assertTrue(
            Json.mapper
                .readTree(statusResp.bodyString())
                .get("completed")
                .asBoolean(),
        )
    }

    @Test
    fun `POST invalid step returns 400`() {
        val token = registerAndGetToken()
        val resp =
            app(
                Request(Method.POST, "/api/setup/steps/unknown/complete")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `each user has independent wizard state`() {
        val token1 = registerAndGetToken("user1")
        val token2 = registerAndGetToken("user2")
        app(Request(Method.POST, "/api/setup/complete").header("Cookie", "token=$token1"))

        val resp2 = app(Request(Method.GET, "/api/setup/status").header("Cookie", "token=$token2"))
        assertFalse(
            Json.mapper
                .readTree(resp2.bodyString())
                .get("completed")
                .asBoolean(),
        )
    }

    @Test
    fun `setup endpoints require authentication`() {
        val resp = app(Request(Method.GET, "/api/setup/status"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
