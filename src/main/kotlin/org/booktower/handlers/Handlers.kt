@file:Suppress("MatchingDeclarationName")

package org.booktower.handlers

import org.booktower.config.StorageConfig
import org.booktower.routers.AuthRouter
import org.booktower.routers.AdminApiRouter
import org.booktower.routers.AudiobookApiRouter
import org.booktower.routers.BookApiRouter
import org.booktower.routers.DeviceSyncRouter
import org.booktower.routers.LibraryApiRouter
import org.booktower.routers.MetadataApiRouter
import org.booktower.routers.OidcRouter
import org.booktower.routers.PageRouter
import org.booktower.routers.UserApiRouter
import org.booktower.services.VersionService
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.ResourceLoader
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static

/** All book/audio formats supported by BookTower, with MIME types. */
val SUPPORTED_FORMATS: Map<String, String> =
    mapOf(
        "pdf" to "application/pdf",
        "epub" to "application/epub+zip",
        "mobi" to "application/x-mobipocket-ebook",
        "azw3" to "application/x-mobi8-ebook",
        "fb2" to "application/xml",
        "cbz" to "application/zip",
        "cbr" to "application/x-rar-compressed",
        "djvu" to "image/vnd.djvu",
        "mp3" to "audio/mpeg",
        "m4b" to "audio/mp4",
        "m4a" to "audio/mp4",
        "ogg" to "audio/ogg",
        "flac" to "audio/flac",
        "aac" to "audio/aac",
    )

/**
 * Top-level HTTP handler that composes domain routers into the full route table.
 *
 * Before the router extraction this class had a 68-parameter constructor and 3,000+ lines.
 * Each domain router now owns its own routes and inline handler methods, keeping this class
 * small and focused on composition.
 */
class AppHandler(
    private val fileHandler: FileHandler,
    private val storageConfig: StorageConfig,
    private val demoMode: Boolean,
    // Domain routers
    private val authRouter: AuthRouter,
    private val oidcRouter: OidcRouter,
    private val pageRouter: PageRouter,
    private val bookApiRouter: BookApiRouter,
    private val libraryApiRouter: LibraryApiRouter,
    private val userApiRouter: UserApiRouter,
    private val adminApiRouter: AdminApiRouter,
    private val metadataApiRouter: MetadataApiRouter,
    private val audiobookApiRouter: AudiobookApiRouter,
    private val deviceSyncRouter: DeviceSyncRouter,
) {
    fun routes(): RoutingHttpHandler {
        val allRoutes =
            listOf(
                "/static" bind static(ResourceLoader.Classpath("/static")),
                "/covers/{filename}" bind Method.GET to fileHandler::cover,
                "/manifest.json" bind Method.GET to ::pwaManifest,
                "/health" bind Method.GET to {
                    Response(Status.OK)
                        .header("Content-Type", "application/json")
                        .body("""{"status":"ok"}""")
                },
                "/api/version" bind Method.GET to {
                    Response(Status.OK)
                        .header("Content-Type", "application/json")
                        .body(
                            org.booktower.config.Json.mapper
                                .writeValueAsString(VersionService.info),
                        )
                },
                "/api/demo/status" bind Method.GET to ::demoStatus,
            ) +
                pageRouter.routes() +
                authRouter.routes() +
                oidcRouter.routes() +
                libraryApiRouter.routes() +
                bookApiRouter.routes() +
                userApiRouter.routes() +
                adminApiRouter.routes() +
                metadataApiRouter.routes() +
                audiobookApiRouter.routes() +
                deviceSyncRouter.routes()

        return routes(*allRoutes.toTypedArray())
    }

    private fun pwaManifest(
        @Suppress("UNUSED_PARAMETER") req: org.http4k.core.Request,
    ): Response {
        val manifest = """{
  "name": "BookTower",
  "short_name": "BookTower",
  "description": "Your personal digital library",
  "start_url": "/",
  "display": "standalone",
  "background_color": "#0f1117",
  "theme_color": "#6366f1",
  "icons": [
    {"src": "/static/icons/icon-192.png", "sizes": "192x192", "type": "image/png"},
    {"src": "/static/icons/icon-512.png", "sizes": "512x512", "type": "image/png"}
  ],
  "categories": ["books", "education", "utilities"]
}"""
        return Response(Status.OK)
            .header("Content-Type", "application/manifest+json")
            .body(manifest)
    }

    private fun demoStatus(
        @Suppress("UNUSED_PARAMETER") req: org.http4k.core.Request,
    ): Response =
        Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(mapOf("demoMode" to demoMode)),
            )
}
