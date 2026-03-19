@file:Suppress("MatchingDeclarationName") // file contains both AuthenticatedUser and JwtAuthFilter

package org.booktower.filters

import org.booktower.config.Json
import org.booktower.models.ErrorResponse
import org.booktower.services.JwtService
import org.http4k.core.Filter
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.cookie.cookie
import org.slf4j.LoggerFactory
import java.util.UUID

private val authLogger = LoggerFactory.getLogger("booktower.AuthFilter")

object AuthenticatedUser {
    const val USER_ID_HEADER = "X-Auth-User-Id"
    const val IS_ADMIN_HEADER = "X-Auth-Is-Admin"

    fun from(request: Request): UUID {
        val header =
            request.header(USER_ID_HEADER)
                ?: error("Missing $USER_ID_HEADER header — request was not authenticated by jwtAuthFilter")
        return UUID.fromString(header)
    }

    fun isAdmin(request: Request): Boolean = request.header(IS_ADMIN_HEADER)?.toBoolean() ?: false
}

fun jwtAuthFilter(
    jwtService: JwtService,
    userExists: ((java.util.UUID) -> Boolean)? = null,
): Filter =
    Filter { next ->
        { req ->
            val token = req.cookie("token")?.value
            val claims = token?.let { jwtService.extractClaims(it) }
            val userId = claims?.first
            val isAdmin = claims?.second ?: false

            val authenticated = userId != null && (userExists == null || userExists(userId))

            if (authenticated) {
                next(
                    req
                        .header(AuthenticatedUser.USER_ID_HEADER, userId.toString())
                        .header(AuthenticatedUser.IS_ADMIN_HEADER, isAdmin.toString()),
                )
            } else {
                val reason =
                    when {
                        token == null -> "no token cookie"
                        claims == null -> "invalid/expired token"
                        userId == null -> "no userId in claims"
                        else -> "user not found"
                    }
                authLogger.warn("Auth failed for {} {}: {}", req.method, req.uri.path, reason)
                Response(Status.UNAUTHORIZED)
                    .header("Content-Type", "application/json")
                    .header("WWW-Authenticate", "Bearer")
                    .body(
                        Json.mapper.writeValueAsString(
                            ErrorResponse("UNAUTHORIZED", "Authentication required"),
                        ),
                    )
            }
        }
    }
