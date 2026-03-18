package org.booktower.routers

import org.booktower.handlers.KOReaderSyncHandler
import org.booktower.handlers.KoboSyncHandler
import org.booktower.handlers.KomgaApiHandler
import org.booktower.handlers.OpdsHandler
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind

class DeviceSyncRouter(
    private val filters: FilterSet,
    private val koboSyncHandler: KoboSyncHandler?,
    private val koReaderSyncHandler: KOReaderSyncHandler?,
    private val komgaApiHandler: KomgaApiHandler?,
    private val opdsHandler: OpdsHandler,
) {
    fun routes(): List<RoutingHttpHandler> =
        listOf(
            // OPDS Catalog 1.2 (HTTP Basic Auth -- no JWT required)
            "/opds/catalog" bind Method.GET to opdsHandler::catalog,
            "/opds/catalog/{libraryId}" bind Method.GET to opdsHandler::library,
            "/opds/books/{id}/file" bind Method.GET to opdsHandler::download,
            "/opds/books/{id}/chapters/{trackIndex}" bind Method.GET to opdsHandler::streamChapter,
            // Kobo device sync
            "/api/kobo/devices" bind Method.POST to
                filters.auth.then(
                    optionalHandler(koboSyncHandler?.let { it::register }),
                ),
            "/api/kobo/devices" bind Method.GET to
                filters.auth.then(
                    optionalHandler(koboSyncHandler?.let { it::listDevices }),
                ),
            "/api/kobo/devices/{token}" bind Method.DELETE to
                filters.auth.then(
                    optionalHandler(koboSyncHandler?.let { it::deleteDevice }),
                ),
            "/kobo/{token}/v1/initialization" bind Method.GET to (
                koboSyncHandler?.let { it::initialization } ?: { _ ->
                    Response(Status.SERVICE_UNAVAILABLE)
                }
            ),
            "/kobo/{token}/v1/library/sync" bind Method.POST to (
                koboSyncHandler?.let { it::sync } ?: { _ ->
                    Response(Status.SERVICE_UNAVAILABLE)
                }
            ),
            "/kobo/{token}/v1/library/snapshot" bind Method.GET to (
                koboSyncHandler?.let { it::snapshot } ?: { _ ->
                    Response(Status.SERVICE_UNAVAILABLE)
                }
            ),
            "/kobo/{token}/v1/library/{bookId}/reading-state" bind Method.PUT to (
                koboSyncHandler?.let { it::readingState } ?: { _ ->
                    Response(Status.SERVICE_UNAVAILABLE)
                }
            ),
            // KOReader sync (kosync protocol)
            "/api/koreader/devices" bind Method.POST to
                filters.auth.then(
                    optionalHandler(koReaderSyncHandler?.let { it::register }),
                ),
            "/api/koreader/devices" bind Method.GET to
                filters.auth.then(
                    optionalHandler(koReaderSyncHandler?.let { it::listDevices }),
                ),
            "/api/koreader/devices/{token}" bind Method.DELETE to
                filters.auth.then(
                    optionalHandler(koReaderSyncHandler?.let { it::deleteDevice }),
                ),
            "/koreader/{token}/syncs/progress" bind Method.PUT to (
                koReaderSyncHandler?.let { it::pushProgress } ?: { _ ->
                    Response(Status.SERVICE_UNAVAILABLE)
                }
            ),
            "/koreader/{token}/syncs/progress/{document}" bind Method.GET to (
                koReaderSyncHandler?.let { it::getProgress } ?: { _ ->
                    Response(Status.SERVICE_UNAVAILABLE)
                }
            ),
            // Komga-compatible API (for Tachiyomi / Paperback)
            "/api/v1/libraries" bind Method.GET to
                filters.auth.then(
                    optionalHandler(komgaApiHandler?.let { it::libraries }),
                ),
            "/api/v1/series" bind Method.GET to
                filters.auth.then(
                    optionalHandler(komgaApiHandler?.let { it::series }),
                ),
            "/api/v1/series/{id}" bind Method.GET to
                filters.auth.then(
                    optionalHandler(komgaApiHandler?.let { it::seriesById }),
                ),
            "/api/v1/books" bind Method.GET to
                filters.auth.then(
                    optionalHandler(komgaApiHandler?.let { it::books }),
                ),
            "/api/v1/books/{id}" bind Method.GET to
                filters.auth.then(
                    optionalHandler(komgaApiHandler?.let { it::bookById }),
                ),
        )
}
