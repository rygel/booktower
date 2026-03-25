package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.junit.jupiter.api.Test
import org.runary.config.Json
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OidcIntegrationTest : IntegrationTestBase() {
    private val forceOnlyApp by lazy { buildApp(oidcForceOnly = true) }

    @Test
    fun `OIDC status returns enabled false by default`() {
        val response = app(Request(Method.GET, "/api/oidc/status"))
        assertEquals(200, response.status.code)
        val body = Json.mapper.readTree(response.bodyString())
        assertEquals(false, body.get("enabled").asBoolean())
        assertEquals(false, body.get("forceOnly").asBoolean())
    }

    @Test
    fun `OIDC status returns forceOnly true on force-only app`() {
        val response = forceOnlyApp(Request(Method.GET, "/api/oidc/status"))
        assertEquals(200, response.status.code)
        val body = Json.mapper.readTree(response.bodyString())
        assertEquals(true, body.get("enabled").asBoolean())
        assertEquals(true, body.get("forceOnly").asBoolean())
    }

    @Test
    fun `force-only mode blocks local login`() {
        val response =
            forceOnlyApp(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"someone","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
            )
        assertEquals(403, response.status.code)
        val body = response.bodyString()
        assertTrue(body.contains("OIDC_FORCE_ONLY"), "Expected OIDC_FORCE_ONLY error, got: $body")
    }

    @Test
    fun `normal app allows local login`() {
        val token = registerAndGetToken("oidctest")
        assertNotNull(token)
        assertTrue(token.isNotBlank())
    }

    @Test
    fun `backchannel logout with unknown sub returns 200`() {
        val logoutToken = buildFakeLogoutToken("unknown-sub-xyz")
        val response =
            forceOnlyApp(
                Request(Method.POST, "/auth/oidc/backchannel-logout")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("logout_token=$logoutToken"),
            )
        assertEquals(200, response.status.code)
    }

    @Test
    fun `backchannel logout with known user revokes tokens`() {
        val logoutToken = buildFakeLogoutToken("test-sub-123")
        val response =
            forceOnlyApp(
                Request(Method.POST, "/auth/oidc/backchannel-logout")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("logout_token=$logoutToken"),
            )
        assertEquals(200, response.status.code)
        val body = Json.mapper.readTree(response.bodyString())
        assertNotNull(body.get("revoked"))
    }

    @Test
    fun `backchannel logout with malformed token returns 400 or 200`() {
        val response =
            forceOnlyApp(
                Request(Method.POST, "/auth/oidc/backchannel-logout")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("logout_token=not.a.valid.jwt.at.all"),
            )
        assertTrue(response.status.code in listOf(200, 400))
    }

    @Test
    fun `OIDC status includes groupMappingEnabled field`() {
        val response = app(Request(Method.GET, "/api/oidc/status"))
        assertEquals(200, response.status.code)
        val body = Json.mapper.readTree(response.bodyString())
        assertNotNull(body.get("groupMappingEnabled"))
        assertEquals(false, body.get("groupMappingEnabled").asBoolean())
    }

    @Test
    fun `backchannel logout accepts JSON body`() {
        val logoutToken = buildFakeLogoutToken("json-sub-456")
        val response =
            forceOnlyApp(
                Request(Method.POST, "/auth/oidc/backchannel-logout")
                    .header("Content-Type", "application/json")
                    .body("""{"logout_token":"$logoutToken"}"""),
            )
        assertEquals(200, response.status.code)
    }

    private fun buildFakeLogoutToken(sub: String): String {
        val header =
            Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString("""{"alg":"RS256","typ":"JWT"}""".toByteArray())
        val payload =
            Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString("""{"sub":"$sub","iat":1700000000}""".toByteArray())
        return "$header.$payload.fakesignature"
    }
}
