package org.booktower.filters

import org.booktower.models.ErrorResponse
import org.booktower.services.JwtService
import com.fasterxml.jackson.databind.ObjectMapper
import org.http4k.core.Filter
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.cookie.cookie
import java.util.UUID

private val objectMapper = ObjectMapper()

object AuthenticatedUser {
    const val USER_ID_HEADER = "X-Auth-User-Id"

    fun from(request: Request): UUID =
        UUID.fromString(request.header(USER_ID_HEADER)!!)
}

fun JwtAuthFilter(jwtService: JwtService): Filter = Filter { next ->
    { req ->
        val token = req.cookie("token")?.value
        val userId = token?.let { jwtService.extractUserId(it) }

        if (userId != null) {
            next(req.header(AuthenticatedUser.USER_ID_HEADER, userId.toString()))
        } else {
            Response(Status.UNAUTHORIZED)
                .header("Content-Type", "application/json")
                .body(
                    objectMapper.writeValueAsString(
                        ErrorResponse("UNAUTHORIZED", "Authentication required"),
                    ),
                )
        }
    }
}
