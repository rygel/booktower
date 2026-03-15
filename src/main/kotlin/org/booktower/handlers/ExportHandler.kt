package org.booktower.handlers

import org.booktower.config.Json
import org.booktower.models.ErrorResponse
import org.booktower.services.ExportService
import org.booktower.services.JwtService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.cookie.cookie

class ExportHandler(
    private val exportService: ExportService,
    private val jwtService: JwtService,
) {
    /** GET /api/export — returns all user data as JSON */
    fun export(req: Request): Response {
        val userId = jwtService.extractUserId(req.cookie("token")?.value ?: "")
            ?: return Response(Status.UNAUTHORIZED)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("UNAUTHORIZED", "Authentication required")))
        return try {
            val data = exportService.exportUser(userId)
            val json = Json.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data)
            Response(Status.OK)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Content-Disposition", "attachment; filename=\"booktower-export.json\"")
                .body(json)
        } catch (e: Exception) {
            Response(Status.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("INTERNAL_ERROR", "Export failed: ${e.message}")))
        }
    }
}
