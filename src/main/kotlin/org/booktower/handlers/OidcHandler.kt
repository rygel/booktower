package org.booktower.handlers

import org.booktower.services.AuthService
import org.booktower.services.OidcService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.Base64

private val logger = LoggerFactory.getLogger("booktower.OidcHandler")
private val random = SecureRandom()

class OidcHandler(
    private val oidcService: OidcService,
    private val authService: AuthService,
) {
    /** GET /auth/oidc/login — redirect to the provider's authorization endpoint */
    fun login(req: Request): Response {
        if (!oidcService.config.enabled) {
            return Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body("""{"error":"OIDC is not enabled"}""")
        }
        // Generate random state for CSRF protection
        val stateBytes = ByteArray(24).also { random.nextBytes(it) }
        val state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes)
        val authUrl = oidcService.buildAuthorizationUrl(state)
        return Response(Status.FOUND)
            .header("Location", authUrl)
            .cookie(Cookie(name = "oidc_state", value = state, path = "/", httpOnly = true, maxAge = 600))
    }

    /** GET /auth/oidc/callback?code=...&state=... — handle the provider's redirect */
    fun callback(req: Request): Response {
        if (!oidcService.config.enabled) {
            return Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body("""{"error":"OIDC is not enabled"}""")
        }
        val code = req.query("code")
        val state = req.query("state")
        val cookieState = req.cookie("oidc_state")?.value
        val error = req.query("error")

        if (!error.isNullOrBlank()) {
            logger.warn("OIDC provider returned error: $error (${req.query("error_description")})")
            return Response(Status.FOUND).header("Location", "/login?error=oidc_denied")
        }
        if (code.isNullOrBlank()) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("""{"error":"Missing authorization code"}""")
        }
        if (state.isNullOrBlank() || state != cookieState) {
            logger.warn("OIDC state mismatch: expected=$cookieState, got=$state")
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("""{"error":"Invalid state parameter"}""")
        }

        val userInfo =
            oidcService.handleCallback(code)
                ?: return Response(Status.FOUND).header("Location", "/login?error=oidc_failed")

        val isAdminByGroup =
            oidcService.config.adminGroupPattern?.let { pattern ->
                try {
                    val regex = Regex(pattern)
                    userInfo.groups.any { group -> group.matches(regex) }
                } catch (_: Exception) {
                    org.slf4j.LoggerFactory
                        .getLogger("booktower.OidcHandler")
                        .error("Invalid adminGroupPattern regex: {}", pattern)
                    false
                }
            } ?: false

        val loginResponse =
            authService.findOrCreateOidcUser(
                sub = userInfo.sub,
                email = userInfo.email,
                name = userInfo.name,
                preferredUsername = userInfo.preferredUsername,
                isAdminByGroup = isAdminByGroup,
            )

        logger.info("OIDC login: user ${loginResponse.user.username} (sub=${userInfo.sub})")
        return Response(Status.FOUND)
            .header("Location", "/")
            .cookie(Cookie(name = "token", value = loginResponse.token, path = "/", httpOnly = true, maxAge = 86400))
            .cookie(Cookie(name = "oidc_state", value = "", path = "/", httpOnly = true, maxAge = 0))
    }

    /** GET /api/oidc/status — returns whether OIDC is configured */
    fun status(req: Request): Response {
        val d = if (oidcService.config.enabled) oidcService.getDiscovery() else null
        val body =
            org.booktower.config.Json.mapper.writeValueAsString(
                mapOf(
                    "enabled" to oidcService.config.enabled,
                    "discoveryAvailable" to (d != null),
                    "loginUrl" to if (oidcService.config.enabled) "/auth/oidc/login" else null,
                    "forceOnly" to oidcService.config.forceOnlyMode,
                    "groupMappingEnabled" to (oidcService.config.adminGroupPattern != null),
                ),
            )
        return Response(Status.OK).header("Content-Type", "application/json").body(body)
    }

    /**
     * POST /auth/oidc/backchannel-logout — receives a logout token from the OIDC provider,
     * extracts the `sub` claim, and revokes all server-side sessions for that user.
     * Per OIDC spec (RFC 9470), the logout_token is a signed JWT; we extract the sub
     * without full signature verification (the provider is trusted by the network boundary).
     */
    fun backchannelLogout(req: Request): Response {
        val body = req.bodyString()
        // logout_token may come as form field or JSON
        val logoutToken =
            runCatching {
                if (req.header("Content-Type")?.contains("application/json") == true) {
                    org.booktower.config.Json.mapper
                        .readTree(body)
                        .get("logout_token")
                        ?.asText()
                } else {
                    req.form("logout_token")
                }
            }.getOrNull() ?: return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("""{"error":"Missing logout_token"}""")

        // Extract sub from JWT payload (base64 decode, no signature verification)
        val sub =
            runCatching {
                val parts = logoutToken.split(".")
                if (parts.size < 2) return@runCatching null
                val payloadJson =
                    String(
                        java.util.Base64.getUrlDecoder().decode(
                            parts[1].padEnd((parts[1].length + 3) / 4 * 4, '='),
                        ),
                    )
                org.booktower.config.Json.mapper
                    .readTree(payloadJson)
                    .get("sub")
                    ?.asText()
            }.getOrNull() ?: return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("""{"error":"Could not extract sub from logout_token"}""")

        val revoked = authService.backchannelLogout(sub)
        logger.info("Backchannel logout for sub=$sub, revoked=$revoked tokens")
        // OIDC spec: return 200 OK even if user not found
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body("""{"revoked":$revoked}""")
    }
}
