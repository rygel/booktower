package org.booktower.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import org.booktower.config.SecurityConfig
import org.booktower.models.*
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

private val logger = LoggerFactory.getLogger("booktower.JwtService")

class JwtService(
    private val config: SecurityConfig,
) {
    private val algorithm = Algorithm.HMAC256(config.jwtSecret)

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

    fun extractUserId(token: String): UUID? =
        try {
            verifyToken(token)?.subject?.let { UUID.fromString(it) }
        } catch (e: Exception) {
            null
        }

    fun extractIsAdmin(token: String): Boolean =
        try {
            verifyToken(token)?.getClaim("admin")?.asBoolean() ?: false
        } catch (e: Exception) {
            false
        }
}
