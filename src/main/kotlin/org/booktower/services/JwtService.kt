package org.booktower.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import com.github.benmanes.caffeine.cache.Caffeine
import org.booktower.config.SecurityConfig
import org.booktower.models.*
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.TimeUnit

private val logger = LoggerFactory.getLogger("booktower.JwtService")

class JwtService(
    private val config: SecurityConfig,
) {
    private val algorithm = Algorithm.HMAC256(config.jwtSecret)

    /** Cache verified JWT claims for 60 seconds to avoid re-verifying the same token on rapid requests. */
    private val claimsCache =
        Caffeine
            .newBuilder()
            .maximumSize(2_000)
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .build<String, Pair<UUID, Boolean>>()

    fun generateToken(user: User): String {
        val now = Instant.now()
        val expiresAt = now.plus(config.sessionTimeout.toLong(), ChronoUnit.SECONDS)

        return JWT
            .create()
            .withIssuer(config.jwtIssuer)
            .withSubject(user.id.toString())
            .withClaim("username", user.username)
            .withClaim("email", user.email)
            .withClaim("admin", user.isAdmin)
            .withIssuedAt(now)
            .withExpiresAt(expiresAt)
            .sign(algorithm)
    }

    fun verifyToken(token: String): DecodedJWT? =
        try {
            JWT
                .require(algorithm)
                .withIssuer(config.jwtIssuer)
                .build()
                .verify(token)
        } catch (e: JWTVerificationException) {
            logger.warn("JWT verification failed: ${e.message}")
            null
        }

    /** Verifies once and extracts both userId and isAdmin in a single pass. Cached for 60s. */
    fun extractClaims(token: String): Pair<UUID, Boolean>? {
        claimsCache.getIfPresent(token)?.let { return it }
        val result =
            try {
                val jwt = verifyToken(token) ?: return null
                val userId = UUID.fromString(jwt.subject)
                val isAdmin = jwt.getClaim("admin")?.asBoolean() ?: false
                userId to isAdmin
            } catch (e: Exception) {
                return null
            }
        claimsCache.put(token, result)
        return result
    }

    fun extractUserId(token: String): UUID? = extractClaims(token)?.first

    fun extractIsAdmin(token: String): Boolean = extractClaims(token)?.second ?: false
}
