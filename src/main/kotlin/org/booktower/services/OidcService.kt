package org.booktower.services

import com.fasterxml.jackson.databind.ObjectMapper
import org.booktower.config.OidcConfig
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

private val oidcLogger = LoggerFactory.getLogger("booktower.OidcService")
private val oidcMapper = ObjectMapper()

data class OidcUserInfo(
    val sub: String,
    val email: String?,
    val name: String?,
    val preferredUsername: String?,
    val groups: List<String> = emptyList(),
)

data class OidcDiscovery(
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val userinfoEndpoint: String,
)

open class OidcService(val config: OidcConfig) {

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    @Volatile
    private var discovery: OidcDiscovery? = null

    // ── Public API ────────────────────────────────────────────────────────────

    /** Builds the URL the user should be redirected to for authorization. */
    fun buildAuthorizationUrl(state: String): String {
        val d = getDiscovery() ?: error("OIDC discovery not available")
        val params = mapOf(
            "response_type" to "code",
            "client_id" to config.clientId,
            "redirect_uri" to config.redirectUri,
            "scope" to config.scope,
            "state" to state,
        )
        val query = params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        return "${d.authorizationEndpoint}?$query"
    }

    /** Exchanges an authorization code for user info. Returns null on failure. */
    open fun handleCallback(code: String): OidcUserInfo? {
        return try {
            val d = getDiscovery() ?: return null
            val tokenResp = exchangeCode(code, d.tokenEndpoint) ?: return null
            val accessToken = tokenResp.get("access_token")?.asText() ?: return null
            fetchUserInfo(accessToken, d.userinfoEndpoint)
        } catch (e: Exception) {
            oidcLogger.warn("OIDC callback handling failed: ${e.message}")
            null
        }
    }

    // ── Discovery ─────────────────────────────────────────────────────────────

    open fun getDiscovery(): OidcDiscovery? {
        discovery?.let { return it }
        return try {
            val url = "${config.issuer.trimEnd('/')}/.well-known/openid-configuration"
            val resp = get(url) ?: return null
            val root = oidcMapper.readTree(resp)
            OidcDiscovery(
                authorizationEndpoint = root.get("authorization_endpoint")?.asText() ?: return null,
                tokenEndpoint = root.get("token_endpoint")?.asText() ?: return null,
                userinfoEndpoint = root.get("userinfo_endpoint")?.asText() ?: return null,
            ).also { discovery = it }
        } catch (e: Exception) {
            oidcLogger.warn("OIDC discovery failed for issuer ${config.issuer}: ${e.message}")
            null
        }
    }

    // ── Token exchange ────────────────────────────────────────────────────────

    private fun exchangeCode(code: String, tokenEndpoint: String): com.fasterxml.jackson.databind.JsonNode? {
        val body = mapOf(
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to config.redirectUri,
            "client_id" to config.clientId,
            "client_secret" to config.clientSecret,
        ).entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        return try {
            val req = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "BookTower/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() != 200) {
                oidcLogger.warn("Token exchange returned HTTP ${resp.statusCode()}: ${resp.body()}")
                return null
            }
            oidcMapper.readTree(resp.body())
        } catch (e: Exception) {
            oidcLogger.warn("Token exchange failed: ${e.message}")
            null
        }
    }

    // ── UserInfo ──────────────────────────────────────────────────────────────

    private fun fetchUserInfo(accessToken: String, userinfoEndpoint: String): OidcUserInfo? {
        return try {
            val req = HttpRequest.newBuilder()
                .uri(URI.create(userinfoEndpoint))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer $accessToken")
                .header("User-Agent", "BookTower/1.0")
                .GET()
                .build()
            val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() != 200) {
                oidcLogger.warn("UserInfo returned HTTP ${resp.statusCode()}")
                return null
            }
            val root = oidcMapper.readTree(resp.body())
            val groupsClaim = config.groupsClaim
            val groups = root.get(groupsClaim)?.let { node ->
                when {
                    node.isArray -> node.map { it.asText() }.filter { it.isNotBlank() }
                    node.isTextual -> node.asText().split(",").map { it.trim() }.filter { it.isNotBlank() }
                    else -> emptyList()
                }
            } ?: emptyList()
            OidcUserInfo(
                sub = root.get("sub")?.asText() ?: return null,
                email = root.get("email")?.asText()?.takeIf { it.isNotBlank() },
                name = root.get("name")?.asText()?.takeIf { it.isNotBlank() },
                preferredUsername = root.get("preferred_username")?.asText()?.takeIf { it.isNotBlank() }
                    ?: root.get("nickname")?.asText()?.takeIf { it.isNotBlank() },
                groups = groups,
            )
        } catch (e: Exception) {
            oidcLogger.warn("UserInfo fetch failed: ${e.message}")
            null
        }
    }

    // ── HTTP helper ───────────────────────────────────────────────────────────

    protected open fun get(url: String): String? {
        return try {
            val req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "BookTower/1.0")
                .GET()
                .build()
            val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() != 200) {
                oidcLogger.warn("HTTP ${resp.statusCode()} from $url")
                return null
            }
            resp.body()
        } catch (e: Exception) {
            oidcLogger.warn("HTTP request failed for $url: ${e.message}")
            null
        }
    }
}
