package org.runary.handlers

import org.runary.config.Json
import org.runary.models.CreateApiTokenRequest
import org.runary.models.ErrorResponse
import org.runary.services.ApiTokenService
import org.runary.services.JwtService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.cookie.cookie
import java.util.UUID

class ApiTokenHandler(
    private val apiTokenService: ApiTokenService,
    private val jwtService: JwtService,
) {
    /** GET /api/tokens */
    fun list(req: Request): Response {
        val userId = userId(req) ?: return unauthorized()
        val tokens = apiTokenService.listTokens(userId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(tokens))
    }

    /** POST /api/tokens  body: {"name":"My App"} */
    fun create(req: Request): Response {
        val userId = userId(req) ?: return unauthorized()
        val body = req.bodyString()
        if (body.isBlank()) return badRequest("Request body is required")
        val request =
            try {
                Json.mapper.readValue(body, CreateApiTokenRequest::class.java)
            } catch (_: Exception) {
                return badRequest("Invalid JSON body")
            }
        if (request.name.isBlank()) return badRequest("name is required")
        if (request.name.length > 100) return badRequest("name must be 100 characters or fewer")
        val result = apiTokenService.createToken(userId, request.name.trim())
        return Response(Status.CREATED)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(result))
    }

    /** DELETE /api/tokens/{id} */
    fun revoke(req: Request): Response {
        val userId = userId(req) ?: return unauthorized()
        val tokenId =
            req.uri.path
                .split("/")
                .lastOrNull()
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return badRequest("Invalid token ID")
        val deleted = apiTokenService.revokeToken(userId, tokenId)
        return if (deleted) {
            Response(Status.OK).header("Content-Type", "application/json").body("{}")
        } else {
            Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "Token not found")))
        }
    }

    private fun userId(req: Request): UUID? = jwtService.extractUserId(req.cookie("token")?.value ?: "")

    private fun unauthorized() =
        Response(Status.UNAUTHORIZED)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(ErrorResponse("UNAUTHORIZED", "Authentication required")))

    private fun badRequest(msg: String) =
        Response(Status.BAD_REQUEST)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", msg)))
}
