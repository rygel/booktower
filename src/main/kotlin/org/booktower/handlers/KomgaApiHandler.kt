package org.booktower.handlers

import org.booktower.config.Json
import org.booktower.filters.AuthenticatedUser
import org.booktower.services.KomgaApiService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.path
import java.util.UUID

/**
 * Exposes a Komga-compatible REST API so that Komga-aware readers
 * (Tachiyomi, Paperback, etc.) can connect to BookTower as a Komga server.
 *
 * Auth: uses the same JWT cookie / token header as the main API.
 */
class KomgaApiHandler(
    private val komgaApiService: KomgaApiService,
) {
    /** GET /api/v1/libraries */
    fun libraries(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val libs = komgaApiService.listLibraries(userId)
        return ok(libs)
    }

    /** GET /api/v1/series?libraryId=... */
    fun series(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val libraryId = req.query("libraryId")
        val result = komgaApiService.listSeries(userId, libraryId)
        return ok(result)
    }

    /** GET /api/v1/series/{id} */
    fun seriesById(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val id = req.path("id") ?: return Response(Status.BAD_REQUEST)
        val series = komgaApiService.getSeriesById(userId, id) ?: return Response(Status.NOT_FOUND)
        return ok(series)
    }

    /** GET /api/v1/books?libraryId=...&seriesId=... */
    fun books(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val libraryId = req.query("libraryId")
        val seriesId = req.query("seriesId")
        val result = komgaApiService.listBooks(userId, libraryId, seriesId)
        return ok(result)
    }

    /** GET /api/v1/books/{id} */
    fun bookById(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.path("id")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST)
        val book = komgaApiService.getBook(userId, bookId) ?: return Response(Status.NOT_FOUND)
        return ok(book)
    }

    private fun ok(body: Any) = Response(Status.OK).header("Content-Type", "application/json").body(Json.mapper.writeValueAsString(body))
}
