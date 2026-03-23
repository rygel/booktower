package org.runary.routers

import org.runary.config.StorageConfig
import org.runary.filters.AuthenticatedUser
import org.runary.services.AudiobookMetaService
import org.runary.services.ListeningSessionService
import org.runary.services.ListeningStatsService
import org.runary.services.UpdateAudiobookMetaRequest
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("runary.AudiobookApiRouter")

class AudiobookApiRouter(
    private val filters: FilterSet,
    private val listeningSessionService: ListeningSessionService?,
    private val listeningStatsService: ListeningStatsService?,
    private val audiobookMetaService: AudiobookMetaService?,
    private val storageConfig: StorageConfig,
) {
    fun routes(): List<RoutingHttpHandler> =
        listOf(
            // Audiobook listening sessions and statistics
            "/api/books/{id}/listen" bind Method.POST to filters.auth.then(::recordListenSession),
            "/api/books/{id}/listen-progress" bind Method.GET to filters.auth.then(::getListenProgress),
            "/api/books/{id}/listen-progress" bind Method.PUT to filters.auth.then(::updateListenProgress),
            "/api/stats/listening" bind Method.GET to filters.auth.then(::getListeningStats),
            "/api/listen-sessions" bind Method.GET to filters.auth.then(::getRecentListenSessions),
            // Audiobook metadata
            "/api/books/{id}/audiobook-meta" bind Method.GET to filters.auth.then(::getAudiobookMeta),
            "/api/books/{id}/audiobook-meta" bind Method.PUT to filters.auth.then(::updateAudiobookMeta),
            "/api/books/{id}/audiobook-meta" bind Method.DELETE to filters.auth.then(::deleteAudiobookMeta),
            "/api/books/{id}/audiobook-cover" bind Method.POST to filters.auth.then(::uploadAudiobookCover),
            "/api/books/{id}/audiobook-cover" bind Method.GET to filters.auth.then(::getAudiobookCover),
        )

    // ─── Listening sessions ──────────────────────────────────────────────────

    private fun recordListenSession(req: Request): Response {
        val svc = listeningSessionService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val bookId =
            extractBookIdFromPath(req)
                ?: return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"Invalid book ID"}""")
        val body =
            runCatching {
                org.runary.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
                ?: return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"Invalid JSON"}""")
        val startPos = body.get("startPosSec")?.asInt() ?: 0
        val endPos =
            body.get("endPosSec")?.asInt()
                ?: return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"endPosSec required"}""")
        val totalSec = body.get("totalSec")?.takeIf { !it.isNull }?.asInt()
        svc.recordSession(userId, bookId, startPos, endPos, totalSec)
        return Response(Status.NO_CONTENT)
    }

    private fun getListenProgress(req: Request): Response {
        val svc = listeningSessionService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val bookId =
            extractBookIdFromPath(req)
                ?: return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"Invalid book ID"}""")
        val progress =
            svc.getProgress(userId, bookId)
                ?: return Response(Status.NOT_FOUND).header("Content-Type", "application/json").body("""{"error":"No progress found"}""")
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.runary.config.Json.mapper
                    .writeValueAsString(progress),
            )
    }

    private fun updateListenProgress(req: Request): Response {
        val svc = listeningSessionService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val bookId =
            extractBookIdFromPath(req)
                ?: return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"Invalid book ID"}""")
        val body =
            runCatching {
                org.runary.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
                ?: return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"Invalid JSON"}""")
        val positionSec =
            body.get("positionSec")?.asInt()
                ?: return Response(
                    Status.BAD_REQUEST,
                ).header("Content-Type", "application/json").body("""{"error":"positionSec required"}""")
        val totalSec = body.get("totalSec")?.takeIf { !it.isNull }?.asInt()
        svc.updateProgress(userId, bookId, positionSec, totalSec)
        return Response(Status.NO_CONTENT)
    }

    private fun getListeningStats(req: Request): Response {
        val svc = listeningStatsService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val days = req.query("days")?.toIntOrNull()?.coerceIn(7, 365) ?: 365
        val stats = svc.getStats(userId, days)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.runary.config.Json.mapper
                    .writeValueAsString(stats),
            )
    }

    private fun getRecentListenSessions(req: Request): Response {
        val svc = listeningSessionService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val limit = req.query("limit")?.toIntOrNull()?.coerceIn(1, 100) ?: 20
        val sessions = svc.getRecentSessions(userId, limit)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.runary.config.Json.mapper
                    .writeValueAsString(sessions),
            )
    }

    // ─── Audiobook metadata ──────────────────────────────────────────────────

    private fun getAudiobookMeta(req: Request): Response {
        val svc = audiobookMetaService ?: return Response(Status.SERVICE_UNAVAILABLE)
        AuthenticatedUser.from(req) // ensure authenticated
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val meta = svc.get(bookId) ?: return Response(Status.NOT_FOUND)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.runary.config.Json.mapper
                    .writeValueAsString(meta),
            )
    }

    private fun updateAudiobookMeta(req: Request): Response {
        val svc = audiobookMetaService ?: return Response(Status.SERVICE_UNAVAILABLE)
        AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val request =
            runCatching {
                org.runary.config.Json.mapper
                    .readValue(req.bodyString(), UpdateAudiobookMetaRequest::class.java)
            }.getOrNull() ?: return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("""{"error":"Invalid request body"}""")
        val result = svc.upsert(bookId, request)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.runary.config.Json.mapper
                    .writeValueAsString(result),
            )
    }

    private fun deleteAudiobookMeta(req: Request): Response {
        val svc = audiobookMetaService ?: return Response(Status.SERVICE_UNAVAILABLE)
        AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        return if (svc.delete(bookId)) Response(Status.NO_CONTENT) else Response(Status.NOT_FOUND)
    }

    private fun uploadAudiobookCover(req: Request): Response {
        val svc = audiobookMetaService ?: return Response(Status.SERVICE_UNAVAILABLE)
        AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val filename =
            req.header("X-Filename")?.trim()
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"X-Filename header required"}""")
        val ext = filename.substringAfterLast('.', "").lowercase()
        if (ext !in setOf("jpg", "jpeg", "png", "webp")) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("""{"error":"Unsupported image type"}""")
        }
        val bytes = req.body.stream.readBytes()
        if (bytes.isEmpty()) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("""{"error":"Empty body"}""")
        }
        val coversDir = java.io.File(storageConfig.coversPath, "audiobook-covers")
        if (!coversDir.exists() && !coversDir.mkdirs()) logger.warn("Could not create covers directory: ${coversDir.absolutePath}")
        val coverFilename = "$bookId-audio.$ext"
        java.io.File(coversDir, coverFilename).writeBytes(bytes)
        svc.upsert(bookId, UpdateAudiobookMetaRequest(audioCover = "audiobook-covers/$coverFilename"))
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body("""{"coverUrl":"/api/books/$bookId/audiobook-cover"}""")
    }

    private fun getAudiobookCover(req: Request): Response {
        val svc = audiobookMetaService ?: return Response(Status.SERVICE_UNAVAILABLE)
        AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val meta = svc.get(bookId) ?: return Response(Status.NOT_FOUND)
        val coverPath = meta.audioCover ?: return Response(Status.NOT_FOUND)
        val file = java.io.File(storageConfig.coversPath, coverPath)
        if (!file.exists()) return Response(Status.NOT_FOUND)
        val contentType =
            when (file.extension.lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> "application/octet-stream"
            }
        return Response(Status.OK)
            .header("Content-Type", contentType)
            .header("Content-Length", file.length().toString())
            .body(file.inputStream())
    }
}
