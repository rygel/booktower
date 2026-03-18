package org.booktower.routers

import org.booktower.filters.AuthenticatedUser
import org.booktower.handlers.LibraryHandler2
import org.booktower.services.BookDropService
import org.booktower.services.LibraryHealthService
import org.booktower.services.LibraryService
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind

class LibraryApiRouter(
    private val filters: FilterSet,
    private val libraryHandler: LibraryHandler2,
    private val libraryService: LibraryService,
    private val libraryHealthService: LibraryHealthService?,
    private val bookDropService: BookDropService?,
) {
    fun routes(): List<RoutingHttpHandler> =
        listOf(
            "/api/libraries" bind Method.GET to filters.auth.then(libraryHandler::list),
            "/api/libraries" bind Method.POST to filters.auth.then(libraryHandler::create),
            "/api/libraries/{id}" bind Method.DELETE to filters.auth.then(libraryHandler::delete),
            "/api/libraries/{id}/settings" bind Method.GET to filters.auth.then(libraryHandler::getSettings),
            "/api/libraries/{id}/settings" bind Method.PUT to filters.auth.then(libraryHandler::updateSettings),
            "/api/libraries/{id}/organize" bind Method.POST to filters.auth.then(libraryHandler::organize),
            "/api/libraries/{id}/scan/async" bind Method.POST to filters.auth.then(libraryHandler::scanAsync),
            "/api/libraries/{id}/scan/{jobId}" bind Method.GET to filters.auth.then(libraryHandler::scanStatus),
            "/api/libraries/{id}/scan" bind Method.POST to filters.auth.then(libraryHandler::scan),
            "/api/libraries/{id}/icon" bind Method.POST to filters.auth.then(libraryHandler::uploadIcon),
            "/api/libraries/{id}/icon" bind Method.GET to filters.auth.then(libraryHandler::getIcon),
            "/api/libraries/{id}/icon" bind Method.DELETE to filters.auth.then(libraryHandler::deleteIcon),
            "/api/libraries/health" bind Method.GET to filters.auth.then(::libraryHealth),
            "/api/bookdrop" bind Method.GET to filters.auth.then(::bookDropList),
            "/api/bookdrop/{filename}/import" bind Method.POST to filters.auth.then(::bookDropImport),
            "/api/bookdrop/{filename}" bind Method.DELETE to filters.auth.then(::bookDropDiscard),
        )

    private fun libraryHealth(req: Request): Response {
        val svc =
            libraryHealthService
                ?: return Response(Status.SERVICE_UNAVAILABLE)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Library health service not available"}""")
        val userId = AuthenticatedUser.from(req)
        val report = svc.check(userId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(report),
            )
    }

    private fun bookDropList(req: Request): Response {
        val svc =
            bookDropService
                ?: return Response(Status.SERVICE_UNAVAILABLE)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"BookDrop service not available"}""")
        val pending = svc.listPending()
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(mapOf("files" to pending)),
            )
    }

    private fun bookDropImport(req: Request): Response {
        val svc =
            bookDropService
                ?: return Response(Status.SERVICE_UNAVAILABLE)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"BookDrop service not available"}""")
        val userId = AuthenticatedUser.from(req)
        val filename =
            req.uri.path
                .split("/")
                .let { parts ->
                    val idx = parts.indexOf("bookdrop")
                    if (idx >= 0 && idx + 1 < parts.size) parts[idx + 1] else null
                }?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"filename is required"}""")
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
        val libraryId =
            body?.get("libraryId")?.asText()?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"libraryId is required"}""")
        val libUuid =
            runCatching { java.util.UUID.fromString(libraryId) }.getOrNull()
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Invalid library ID"}""")
        val library =
            libraryService.getLibrary(userId, libUuid)
                ?: return Response(Status.NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Library not found"}""")
        val bookId =
            svc.import(userId, filename, libraryId, library.path)
                ?: return Response(Status.NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"File not found in drop folder"}""")
        return Response(Status.CREATED)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(mapOf("bookId" to bookId)),
            )
    }

    private fun bookDropDiscard(req: Request): Response {
        val svc =
            bookDropService
                ?: return Response(Status.SERVICE_UNAVAILABLE)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"BookDrop service not available"}""")
        val filename =
            req.uri.path
                .split("/")
                .lastOrNull()
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"filename is required"}""")
        return if (svc.discard(filename)) {
            Response(Status.NO_CONTENT)
        } else {
            Response(Status.NOT_FOUND).header("Content-Type", "application/json").body("""{"error":"File not found"}""")
        }
    }
}
