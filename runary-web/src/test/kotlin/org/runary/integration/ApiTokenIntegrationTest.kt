package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import org.runary.config.Json
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for API token management and OPDS Bearer token auth.
 */
class ApiTokenIntegrationTest : IntegrationTestBase() {
    // ── GET /api/tokens ───────────────────────────────────────────────────────

    @Test
    fun `list tokens returns empty list for new user`() {
        val token = registerAndGetToken("apitok_list")
        val resp =
            app(
                Request(Method.GET, "/api/tokens")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, resp.status)
        val body = Json.mapper.readTree(resp.bodyString())
        assertTrue(body.isArray, "Response should be an array")
        assertEquals(0, body.size())
    }

    @Test
    fun `unauthenticated request to list tokens returns 401`() {
        val resp = app(Request(Method.GET, "/api/tokens"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    // ── POST /api/tokens ──────────────────────────────────────────────────────

    @Test
    fun `create token returns 201 with token value`() {
        val token = registerAndGetToken("apitok_create")
        val resp =
            app(
                Request(Method.POST, "/api/tokens")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"KOReader"}"""),
            )
        assertEquals(Status.CREATED, resp.status)
        val body = Json.mapper.readTree(resp.bodyString())
        assertNotNull(body.get("token"), "Response should include raw token")
        assertTrue(body.get("token").asText().startsWith("bt_"), "Token should start with bt_")
        assertEquals("KOReader", body.get("name").asText())
    }

    @Test
    fun `create token with blank name returns 400`() {
        val token = registerAndGetToken("apitok_blank")
        val resp =
            app(
                Request(Method.POST, "/api/tokens")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"  "}"""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `created token appears in list`() {
        val jwtToken = registerAndGetToken("apitok_visible")

        app(
            Request(Method.POST, "/api/tokens")
                .header("Cookie", "token=$jwtToken")
                .header("Content-Type", "application/json")
                .body("""{"name":"MyApp"}"""),
        )

        val listResp =
            app(
                Request(Method.GET, "/api/tokens")
                    .header("Cookie", "token=$jwtToken"),
            )
        val body = Json.mapper.readTree(listResp.bodyString())
        assertEquals(1, body.size())
        assertEquals("MyApp", body[0].get("name").asText())
        assertTrue(body[0].get("id").asText().isNotBlank())
    }

    // ── DELETE /api/tokens/{id} ───────────────────────────────────────────────

    @Test
    fun `revoke token removes it from list`() {
        val jwtToken = registerAndGetToken("apitok_revoke")

        val createResp =
            app(
                Request(Method.POST, "/api/tokens")
                    .header("Cookie", "token=$jwtToken")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"ToRevoke"}"""),
            )
        val tokenId =
            Json.mapper
                .readTree(createResp.bodyString())
                .get("id")
                .asText()

        val deleteResp =
            app(
                Request(Method.DELETE, "/api/tokens/$tokenId")
                    .header("Cookie", "token=$jwtToken"),
            )
        assertEquals(Status.OK, deleteResp.status)

        val listResp =
            app(
                Request(Method.GET, "/api/tokens")
                    .header("Cookie", "token=$jwtToken"),
            )
        val body = Json.mapper.readTree(listResp.bodyString())
        assertEquals(0, body.size(), "Token should be gone after revocation")
    }

    @Test
    fun `revoke nonexistent token returns 404`() {
        val jwtToken = registerAndGetToken("apitok_nx")
        val resp =
            app(
                Request(Method.DELETE, "/api/tokens/00000000-0000-0000-0000-000000000000")
                    .header("Cookie", "token=$jwtToken"),
            )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `user B cannot revoke user A token`() {
        val tokenA = registerAndGetToken("apitok_a")
        val tokenB = registerAndGetToken("apitok_b")

        val createResp =
            app(
                Request(Method.POST, "/api/tokens")
                    .header("Cookie", "token=$tokenA")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"UserAToken"}"""),
            )
        val tokenId =
            Json.mapper
                .readTree(createResp.bodyString())
                .get("id")
                .asText()

        val deleteResp =
            app(
                Request(Method.DELETE, "/api/tokens/$tokenId")
                    .header("Cookie", "token=$tokenB"),
            )
        assertEquals(Status.NOT_FOUND, deleteResp.status, "User B must not be able to revoke User A's token")
    }

    // ── OPDS Bearer token auth ────────────────────────────────────────────────

    @Test
    fun `OPDS catalog accessible with Bearer API token`() {
        val jwtToken = registerAndGetToken("opds_bearer")

        val createResp =
            app(
                Request(Method.POST, "/api/tokens")
                    .header("Cookie", "token=$jwtToken")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"KOReader"}"""),
            )
        val rawApiToken =
            Json.mapper
                .readTree(createResp.bodyString())
                .get("token")
                .asText()

        val opdsResp =
            app(
                Request(Method.GET, "/opds/catalog")
                    .header("Authorization", "Bearer $rawApiToken"),
            )
        assertEquals(Status.OK, opdsResp.status)
        assertTrue(opdsResp.header("Content-Type")?.contains("opds-catalog") == true)
    }

    @Test
    fun `OPDS catalog returns 401 with invalid Bearer token`() {
        val opdsResp =
            app(
                Request(Method.GET, "/opds/catalog")
                    .header("Authorization", "Bearer bt_invalid_token"),
            )
        assertEquals(Status.UNAUTHORIZED, opdsResp.status)
    }
}
