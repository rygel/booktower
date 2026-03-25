package org.runary.integration

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.runary.config.OidcConfig
import org.runary.services.OidcService
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.MountableFile
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * OIDC E2E test against a real Keycloak instance via Testcontainers.
 * Tests the full OAuth2/OIDC flow: discovery → token exchange → user info.
 *
 * Uses GenericContainer (not dasniko/testcontainers-keycloak) to avoid
 * RESTEasy/Jakarta EE classpath conflicts.
 *
 * Run with: mvn test -Dtest="OidcKeycloakE2ETest" -Doidc.integration=true
 * Requires Docker.
 */
@Testcontainers
@EnabledIfSystemProperty(named = "oidc.integration", matches = "true")
class OidcKeycloakE2ETest {
    companion object {
        private val logger = LoggerFactory.getLogger("runary.OidcKeycloakE2ETest")

        private const val REALM = "runary"
        private const val CLIENT_ID = "runary"
        private const val CLIENT_SECRET = "runary-test-secret"
        private const val TEST_USER = "testuser"
        private const val TEST_PASSWORD = "testpassword"

        @Container
        @JvmStatic
        val keycloak: GenericContainer<*> =
            GenericContainer("quay.io/keycloak/keycloak:26.2")
                .withExposedPorts(8080)
                .withCopyFileToContainer(
                    MountableFile.forClasspathResource("keycloak-test-realm.json"),
                    "/opt/keycloak/data/import/realm.json",
                ).withCommand("start-dev", "--import-realm")
                .waitingFor(Wait.forHttp("/realms/master").forPort(8080).withStartupTimeout(Duration.ofSeconds(120)))

        private lateinit var oidcService: OidcService
        private lateinit var issuerUrl: String
        private val http: HttpClient = HttpClient.newHttpClient()

        @BeforeAll
        @JvmStatic
        fun setup() {
            keycloak.start()
            val baseUrl = "http://${keycloak.host}:${keycloak.getMappedPort(8080)}"
            issuerUrl = "$baseUrl/realms/$REALM"
            logger.info("Keycloak started at $baseUrl, issuer: $issuerUrl")

            oidcService =
                OidcService(
                    OidcConfig(
                        enabled = true,
                        issuer = issuerUrl,
                        clientId = CLIENT_ID,
                        clientSecret = CLIENT_SECRET,
                        redirectUri = "http://localhost:9999/api/oidc/callback",
                    ),
                )
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            keycloak.stop()
        }

        /** Gets an access token from Keycloak using Resource Owner Password Grant. */
        private fun getAccessToken(
            username: String = TEST_USER,
            password: String = TEST_PASSWORD,
        ): String {
            val tokenUrl = "$issuerUrl/protocol/openid-connect/token"
            val body =
                mapOf(
                    "grant_type" to "password",
                    "client_id" to CLIENT_ID,
                    "client_secret" to CLIENT_SECRET,
                    "username" to username,
                    "password" to password,
                    "scope" to "openid email profile",
                ).entries.joinToString("&") { (k, v) ->
                    "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
                }

            val req =
                HttpRequest
                    .newBuilder()
                    .uri(URI.create(tokenUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build()
            val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
            assertEquals(200, resp.statusCode(), "Token request should succeed: ${resp.body()}")

            return org.runary.config.Json.mapper
                .readTree(resp.body())
                .get("access_token")
                .asText()
        }
    }

    // ── Discovery ────────────────────────────────────────────────────────

    @Test
    fun `OIDC discovery resolves from real Keycloak`() {
        val discovery = oidcService.getDiscovery()
        assertNotNull(discovery, "Should discover OIDC endpoints from Keycloak")
        assertTrue(discovery.authorizationEndpoint.contains("/realms/$REALM/"))
        assertTrue(discovery.tokenEndpoint.contains("/realms/$REALM/"))
        assertTrue(discovery.userinfoEndpoint.contains("/realms/$REALM/"))
    }

    @Test
    fun `authorization URL contains correct parameters`() {
        val authUrl = oidcService.buildAuthorizationUrl("test-state-abc")
        assertTrue(authUrl.contains("response_type=code"))
        assertTrue(authUrl.contains("client_id=$CLIENT_ID"))
        assertTrue(authUrl.contains("state=test-state-abc"))
    }

    // ── Token exchange ───────────────────────────────────────────────────

    @Test
    fun `direct grant returns valid JWT from Keycloak`() {
        val token = getAccessToken()
        assertTrue(token.isNotBlank())
        assertEquals(3, token.split(".").size, "Should be a JWT with 3 parts")
    }

    @Test
    fun `token contains correct issuer and client claims`() {
        val token = getAccessToken()
        val payloadPart = token.split(".")[1]
        // JWT Base64URL may need padding
        val padded = payloadPart + "=".repeat((4 - payloadPart.length % 4) % 4)
        val payload =
            String(
                java.util.Base64
                    .getUrlDecoder()
                    .decode(padded),
            )
        val claims =
            org.runary.config.Json.mapper
                .readTree(payload)

        assertTrue(payload.contains("iss"), "Payload should contain issuer: $payload")
        assertEquals(issuerUrl, claims.get("iss")?.asText(), "Issuer should match")
        assertEquals(CLIENT_ID, claims.get("azp")?.asText(), "Authorized party should match")
    }

    // ── UserInfo ─────────────────────────────────────────────────────────

    @Test
    fun `userinfo returns correct user details`() {
        val accessToken = getAccessToken()
        val discovery = oidcService.getDiscovery()!!

        val req =
            HttpRequest
                .newBuilder()
                .uri(URI.create(discovery.userinfoEndpoint))
                .header("Authorization", "Bearer $accessToken")
                .GET()
                .build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, resp.statusCode())

        val json =
            org.runary.config.Json.mapper
                .readTree(resp.body())
        assertEquals(TEST_USER, json.get("preferred_username")?.asText())
        assertEquals("testuser@runary.test", json.get("email")?.asText())
    }

    // ── Error cases ──────────────────────────────────────────────────────

    @Test
    fun `wrong password returns 401`() {
        val tokenUrl = "$issuerUrl/protocol/openid-connect/token"
        val body =
            mapOf(
                "grant_type" to "password",
                "client_id" to CLIENT_ID,
                "client_secret" to CLIENT_SECRET,
                "username" to TEST_USER,
                "password" to "wrong",
            ).entries.joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
            }
        val req =
            HttpRequest
                .newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        assertEquals(401, resp.statusCode())
    }

    @Test
    fun `handleCallback with invalid code returns null gracefully`() {
        val result = oidcService.handleCallback("invalid-code")
        assertEquals(null, result, "Invalid code should return null")
    }

    @Test
    fun `expired or revoked token is rejected by userinfo`() {
        val discovery = oidcService.getDiscovery()!!
        val req =
            HttpRequest
                .newBuilder()
                .uri(URI.create(discovery.userinfoEndpoint))
                .header("Authorization", "Bearer invalid.jwt.token")
                .GET()
                .build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        assertEquals(401, resp.statusCode())
    }
}
