package org.runary.handlers

import org.runary.config.Json
import org.runary.services.GoodreadsImportService
import org.runary.services.JwtService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.cookie.cookie

class GoodreadsImportHandler(
    private val importService: GoodreadsImportService,
    private val jwtService: JwtService,
) {
    /** POST /api/import/goodreads?libraryId=UUID — body is raw Goodreads CSV */
    fun import(req: Request): Response {
        val token = req.cookie("token")?.value ?: req.header("Authorization")?.removePrefix("Bearer ")
        val userId =
            token?.let { jwtService.extractUserId(it) }
                ?: return Response(Status.UNAUTHORIZED)

        val libraryId =
            req.query("libraryId")?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST).body("{\"error\":\"libraryId is required\"}")

        val result = importService.import(userId, libraryId, req.body.stream)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(result))
    }
}
