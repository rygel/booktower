package org.runary.handlers

import org.runary.config.Json
import org.runary.models.ErrorResponse
import org.runary.services.ExportService
import org.runary.services.JwtService
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
        val userId =
            jwtService.extractUserId(req.cookie("token")?.value ?: "")
                ?: return Response(Status.UNAUTHORIZED)
                    .header("Content-Type", "application/json")
                    .body(Json.mapper.writeValueAsString(ErrorResponse("UNAUTHORIZED", "Authentication required")))
        return try {
            val data = exportService.exportUser(userId)
            val json = Json.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data)
            Response(Status.OK)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Content-Disposition", "attachment; filename=\"runary-export.json\"")
                .body(json)
        } catch (e: Exception) {
            Response(Status.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("INTERNAL_ERROR", "Export failed: ${e.message}")))
        }
    }
}
