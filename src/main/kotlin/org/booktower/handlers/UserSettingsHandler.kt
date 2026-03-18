package org.booktower.handlers

import org.booktower.config.Json
import org.booktower.filters.AuthenticatedUser
import org.booktower.models.ErrorResponse
import org.booktower.services.UserSettingsService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("booktower.UserSettingsHandler")

class UserSettingsHandler(
    private val settingsService: UserSettingsService,
) {
    fun getAll(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val settings = settingsService.getAll(userId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(settings))
    }

    fun delete(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val key =
            req.uri.path
                .split("/")
                .lastOrNull()
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Setting key is required")))

        val deleted = settingsService.delete(userId, key)
        return if (deleted) {
            Response(Status.NO_CONTENT)
        } else {
            Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "Setting '$key' not found")))
        }
    }

    fun set(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val key =
            req.uri.path
                .split("/")
                .lastOrNull()
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Setting key is required")))

        val body = req.bodyString()
        // Accept plain text value or JSON string
        val value =
            when {
                body.isBlank() -> {
                    null
                }

                body.startsWith("\"") -> {
                    try {
                        Json.mapper.readValue(body, String::class.java)
                    } catch (e: Exception) {
                        body
                    }
                }

                else -> {
                    body.trim()
                }
            }

        return try {
            settingsService.set(userId, key, value)
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(mapOf("key" to key, "value" to value)))
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid setting key '$key': ${e.message}")
            Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", e.message ?: "Invalid key")))
        }
    }
}
