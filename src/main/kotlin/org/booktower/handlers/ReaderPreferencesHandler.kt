package org.booktower.handlers

import org.booktower.config.Json
import org.booktower.filters.AuthenticatedUser
import org.booktower.services.ReaderPreferencesService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.path

class ReaderPreferencesHandler(private val readerPreferencesService: ReaderPreferencesService) {

    /** GET /api/reader-preferences/{format} — retrieve saved preferences for a format. */
    fun get(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val format = req.path("format") ?: return Response(Status.BAD_REQUEST)
        val device = req.query("device")
        val prefs = readerPreferencesService.get(userId, format, device)
        return Response(Status.OK).header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(prefs))
    }

    /** PUT /api/reader-preferences/{format} — replace preferences for a format. */
    fun set(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val format = req.path("format") ?: return Response(Status.BAD_REQUEST)
        val device = req.query("device")
        val body = runCatching {
            @Suppress("UNCHECKED_CAST")
            Json.mapper.readValue(req.bodyString(), Map::class.java) as Map<String, Any?>
        }.getOrNull() ?: return Response(Status.BAD_REQUEST).header("Content-Type", "application/json")
            .body("""{"error":"Invalid JSON"}""")
        val saved = readerPreferencesService.set(userId, format, body, device)
        return Response(Status.OK).header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(saved))
    }

    /** PATCH /api/reader-preferences/{format} — merge partial updates. */
    fun merge(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val format = req.path("format") ?: return Response(Status.BAD_REQUEST)
        val device = req.query("device")
        val body = runCatching {
            @Suppress("UNCHECKED_CAST")
            Json.mapper.readValue(req.bodyString(), Map::class.java) as Map<String, Any?>
        }.getOrNull() ?: return Response(Status.BAD_REQUEST).header("Content-Type", "application/json")
            .body("""{"error":"Invalid JSON"}""")
        val merged = readerPreferencesService.merge(userId, format, body, device)
        return Response(Status.OK).header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(merged))
    }

    /** DELETE /api/reader-preferences/{format} — reset preferences for a format. */
    fun delete(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val format = req.path("format") ?: return Response(Status.BAD_REQUEST)
        val device = req.query("device")
        readerPreferencesService.delete(userId, format, device)
        return Response(Status.NO_CONTENT)
    }
}
