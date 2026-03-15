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
import java.util.UUID

object AuthenticatedUser {
    const val USER_ID_HEADER = "X-Auth-User-Id"
    const val IS_ADMIN_HEADER = "X-Auth-Is-Admin"

    fun from(request: Request): UUID =
        UUID.fromString(request.header(USER_ID_HEADER)!!)

    fun isAdmin(request: Request): Boolean =
        request.header(IS_ADMIN_HEADER)?.toBoolean() ?: false
}

fun JwtAuthFilter(jwtService: JwtService): Filter = Filter { next ->
    { req ->
        val token = req.cookie("token")?.value
        val userId = token?.let { jwtService.extractUserId(it) }
        val isAdmin = token?.let { jwtService.extractIsAdmin(it) } ?: false

        if (userId != null) {
            next(
                req.header(AuthenticatedUser.USER_ID_HEADER, userId.toString())
                    .header(AuthenticatedUser.IS_ADMIN_HEADER, isAdmin.toString()),
            )
        } else {
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
