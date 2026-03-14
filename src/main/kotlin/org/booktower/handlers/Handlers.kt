package org.booktower.handlers

import org.booktower.config.StorageConfig
import org.booktower.config.TemplateRenderer
import org.booktower.filters.JwtAuthFilter
import org.booktower.services.AuthService
import org.booktower.services.BookmarkService
import org.booktower.services.BookService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.booktower.services.PdfMetadataService
import org.booktower.services.UserSettingsService
import org.http4k.core.*
import org.http4k.core.cookie.cookie
import org.http4k.routing.ResourceLoader
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("booktower.handlers")

class AppHandler(
    private val authService: AuthService,
    private val libraryService: LibraryService,
    private val bookService: BookService,
    private val bookmarkService: BookmarkService,
    private val userSettingsService: UserSettingsService,
    private val pdfMetadataService: PdfMetadataService,
    private val jwtService: JwtService,
    private val storageConfig: StorageConfig,
    private val templateRenderer: TemplateRenderer,
) {
    private val authHandler = AuthHandler2(authService)
    private val libraryHandler = LibraryHandler2(libraryService)
    private val bookHandler = BookHandler2(bookService)
    private val bookmarkHandler = BookmarkHandler(bookmarkService)
    private val fileHandler = FileHandler(bookService, pdfMetadataService, storageConfig)
    private val settingsHandler = UserSettingsHandler(userSettingsService)
    private val authFilter = JwtAuthFilter(jwtService)

    fun routes(): RoutingHttpHandler {
        return routes(
            "/static" bind static(ResourceLoader.Classpath("/static")),
            "/" bind Method.GET to ::index,
            "/login" bind Method.GET to ::loginPage,
            "/register" bind Method.GET to ::registerPage,
            "/auth/register" bind Method.POST to authHandler::register,
            "/auth/login" bind Method.POST to authHandler::login,
            "/auth/logout" bind Method.POST to authHandler::logout,
            "/api/libraries" bind Method.GET to authFilter.then(libraryHandler::list),
            "/api/libraries" bind Method.POST to authFilter.then(libraryHandler::create),
            "/api/libraries/{id}" bind Method.DELETE to authFilter.then(libraryHandler::delete),
            "/api/books" bind Method.GET to authFilter.then(bookHandler::list),
            "/api/books" bind Method.POST to authFilter.then(bookHandler::create),
            "/api/books/{id}" bind Method.GET to authFilter.then(bookHandler::get),
            "/api/books/{id}" bind Method.DELETE to authFilter.then(bookHandler::delete),
            "/api/books/{id}/progress" bind Method.PUT to authFilter.then(bookHandler::updateProgress),
            "/api/recent" bind Method.GET to authFilter.then(bookHandler::recent),
            "/api/search" bind Method.GET to authFilter.then(bookHandler::search),
            "/api/bookmarks" bind Method.GET to authFilter.then(bookmarkHandler::list),
            "/api/bookmarks" bind Method.POST to authFilter.then(bookmarkHandler::create),
            "/api/bookmarks/{id}" bind Method.DELETE to authFilter.then(bookmarkHandler::delete),
            "/api/books/{id}/upload" bind Method.POST to authFilter.then(fileHandler::upload),
            "/api/books/{id}/file" bind Method.GET to authFilter.then(fileHandler::download),
            "/api/settings" bind Method.GET to authFilter.then(settingsHandler::getAll),
            "/api/settings/{key}" bind Method.PUT to authFilter.then(settingsHandler::set),
            "/preferences/theme" bind Method.POST to ::setTheme,
            "/preferences/lang" bind Method.POST to ::setLanguage,
        )
    }

    private fun index(req: Request): Response {
        val token = req.cookie("token")?.value
        val isAuth = token != null && jwtService.extractUserId(token) != null

        val content =
            if (isAuth) {
                val userId = jwtService.extractUserId(token)!!
                val libraries = libraryService.getLibraries(userId)
                templateRenderer.render(
                    "index.kte",
                    mapOf<String, Any?>(
                        "title" to "BookTower",
                        "isAuthenticated" to true,
                        "username" to null,
                        "libraries" to libraries.map { mapOf("id" to it.id, "name" to it.name) },
                    ),
                )
            } else {
                templateRenderer.render(
                    "index.kte",
                    mapOf<String, Any?>(
                        "title" to "BookTower",
                        "isAuthenticated" to false,
                        "username" to null,
                        "libraries" to null,
                    ),
                )
            }

        return Response(Status.OK)
            .header("Content-Type", "text/html")
            .body(content)
    }

    private fun loginPage(req: Request): Response {
        val content =
            templateRenderer.render(
                "index.kte",
                mapOf<String, Any?>(
                    "title" to "Login - BookTower",
                    "isAuthenticated" to false,
                    "username" to null,
                    "libraries" to null,
                    "showLogin" to true,
                    "showRegister" to false,
                ),
            )
        return Response(Status.OK)
            .header("Content-Type", "text/html")
            .body(content)
    }

    private fun registerPage(req: Request): Response {
        val content =
            templateRenderer.render(
                "index.kte",
                mapOf<String, Any?>(
                    "title" to "Register - BookTower",
                    "isAuthenticated" to false,
                    "username" to null,
                    "libraries" to null,
                    "showLogin" to false,
                    "showRegister" to true,
                ),
            )
        return Response(Status.OK)
            .header("Content-Type", "text/html")
            .body(content)
    }

    private fun setTheme(req: Request): Response {
        val isHtmx = req.header("HX-Request") != null

        val theme = req.bodyString().trim().let {
            if (it.isNotBlank()) it else "dark"
        }

        return if (isHtmx) {
            Response(Status.OK)
                .header("HX-Trigger", "theme-updated")
                .header("HX-Reswap", "none")
                .body("Theme updated to $theme")
        } else {
            Response(Status.SEE_OTHER)
                .header("Location", "/")
                .body("Theme updated. Redirecting...")
        }
    }

    private fun setLanguage(req: Request): Response {
        val isHtmx = req.header("HX-Request") != null

        val lang = req.bodyString().trim().let {
            if (it.isNotBlank()) it else "en"
        }

        return if (isHtmx) {
            Response(Status.OK)
                .header("HX-Trigger", "lang-updated")
                .header("HX-Reswap", "none")
                .body("Language updated to $lang")
        } else {
            Response(Status.SEE_OTHER)
                .header("Location", "/")
                .body("Language updated. Redirecting...")
        }
    }
}
