package org.booktower.integration

import org.booktower.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class OpdsCredentialsIntegrationTest : IntegrationTestBase() {
    private fun basicAuthHeader(
        username: String,
        password: String,
    ): String {
        val encoded = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
        return "Basic $encoded"
    }

    @Test
    fun `GET opds credentials returns not configured initially`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/api/user/opds-credentials").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertFalse(tree.get("configured").asBoolean())
        assertTrue(tree.get("opdsUsername").isNull)
    }

    @Test
    fun `PUT opds credentials saves them`() {
        val token = registerAndGetToken()
        val putResp =
            app(
                Request(Method.PUT, "/api/user/opds-credentials")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"opdsUsername":"myopds","password":"securepass123"}"""),
            )
        assertEquals(Status.NO_CONTENT, putResp.status)

        val getResp = app(Request(Method.GET, "/api/user/opds-credentials").header("Cookie", "token=$token"))
        val tree = Json.mapper.readTree(getResp.bodyString())
        assertTrue(tree.get("configured").asBoolean())
        assertEquals("myopds", tree.get("opdsUsername").asText())
    }

    @Test
    fun `OPDS catalog authenticated with OPDS credentials succeeds`() {
        val token = registerAndGetToken("opds1")
        app(
            Request(Method.PUT, "/api/user/opds-credentials")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"opdsUsername":"opds_user","password":"password123"}"""),
        )

        val opdsResp =
            app(
                Request(Method.GET, "/opds/catalog")
                    .header("Authorization", basicAuthHeader("opds_user", "password123")),
            )
        assertEquals(Status.OK, opdsResp.status)
    }

    @Test
    fun `OPDS catalog with wrong OPDS password fails`() {
        val token = registerAndGetToken("opds2")
        app(
            Request(Method.PUT, "/api/user/opds-credentials")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"opdsUsername":"opds_user2","password":"correctpass123"}"""),
        )

        val opdsResp =
            app(
                Request(Method.GET, "/opds/catalog")
                    .header("Authorization", basicAuthHeader("opds_user2", "wrongpass")),
            )
        assertEquals(Status.UNAUTHORIZED, opdsResp.status)
    }

    @Test
    fun `DELETE opds credentials removes them`() {
        val token = registerAndGetToken()
        app(
            Request(Method.PUT, "/api/user/opds-credentials")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"opdsUsername":"todelete","password":"password123"}"""),
        )

        val delResp = app(Request(Method.DELETE, "/api/user/opds-credentials").header("Cookie", "token=$token"))
        assertEquals(Status.NO_CONTENT, delResp.status)

        val getResp = app(Request(Method.GET, "/api/user/opds-credentials").header("Cookie", "token=$token"))
        val tree = Json.mapper.readTree(getResp.bodyString())
        assertFalse(tree.get("configured").asBoolean())
    }

    @Test
    fun `PUT opds credentials with short password returns 400`() {
        val token = registerAndGetToken()
        val resp =
            app(
                Request(Method.PUT, "/api/user/opds-credentials")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"opdsUsername":"user","password":"short"}"""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `opds credentials endpoints require authentication`() {
        val getResp = app(Request(Method.GET, "/api/user/opds-credentials"))
        assertEquals(Status.UNAUTHORIZED, getResp.status)
        val putResp = app(Request(Method.PUT, "/api/user/opds-credentials"))
        assertEquals(Status.UNAUTHORIZED, putResp.status)
    }

    @Test
    fun `OPDS catalog still works with main account credentials when no OPDS creds set`() {
        // Register a user whose main credentials work for OPDS
        val username = "mainuser_${System.nanoTime()}"
        val password = "password123"
        app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$username@test.com","password":"$password"}"""),
        )
        val opdsResp =
            app(
                Request(Method.GET, "/opds/catalog")
                    .header("Authorization", basicAuthHeader(username, password)),
            )
        assertEquals(Status.OK, opdsResp.status)
    }
}
