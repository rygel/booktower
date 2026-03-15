@file:Suppress("MatchingDeclarationName") // file is the router/wiring hub and contains AppHandler

package org.booktower.handlers

import org.booktower.config.StorageConfig
import org.booktower.config.TemplateRenderer
import org.booktower.filters.AdminFilter
import org.booktower.filters.JwtAuthFilter
import org.booktower.filters.RateLimitFilter
import org.booktower.model.ThemeCatalog
import org.booktower.services.AdminService
import org.booktower.services.AnnotationService
import org.booktower.services.ApiTokenService
import org.booktower.services.ComicService
import org.booktower.services.EpubMetadataService
import org.booktower.services.ExportService
import org.booktower.services.GoodreadsImportService
import org.booktower.services.MagicShelfService
import org.booktower.services.ReadingSessionService
import org.booktower.services.MetadataFetchService
import org.booktower.services.AuthService
import org.booktower.services.BookmarkService
import org.booktower.services.BookService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.booktower.services.PasswordResetService
import org.booktower.services.PdfMetadataService
import org.booktower.services.AnalyticsService
import org.booktower.services.UserSettingsService
import org.booktower.web.WebContext
import org.booktower.weblate.WeblateHandler
import org.http4k.core.*
import org.http4k.core.body.form
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.routing.ResourceLoader
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
class AppHandler(
    private val authService: AuthService,
    private val libraryService: LibraryService,
    private val bookService: BookService,
    private val bookmarkService: BookmarkService,
    private val userSettingsService: UserSettingsService,
    private val pdfMetadataService: PdfMetadataService,
    private val epubMetadataService: EpubMetadataService,
    private val adminService: AdminService,
    private val jwtService: JwtService,
    private val storageConfig: StorageConfig,
    private val templateRenderer: TemplateRenderer,
    private val weblateHandler: WeblateHandler,
    private val analyticsService: AnalyticsService,
    private val annotationService: AnnotationService,
    private val metadataFetchService: MetadataFetchService,
    private val magicShelfService: MagicShelfService,
    private val passwordResetService: PasswordResetService,
    private val apiTokenService: ApiTokenService,
    private val exportService: ExportService,
    private val comicService: ComicService,
    private val goodreadsImportService: GoodreadsImportService,
    private val readingSessionService: ReadingSessionService,
) {
    private val authHandler = AuthHandler2(authService, userSettingsService, passwordResetService)
    private val libraryHandler = LibraryHandler2(libraryService)
    private val bookHandler = BookHandler2(bookService, readingSessionService)
    private val bookmarkHandler = BookmarkHandler(bookmarkService)
    private val fileHandler = FileHandler(bookService, pdfMetadataService, epubMetadataService, storageConfig)
    private val settingsHandler = UserSettingsHandler(userSettingsService)
    private val adminHandler = AdminHandler(adminService, templateRenderer, passwordResetService)
    private val pageHandler = PageHandler(jwtService, authService, libraryService, bookService, bookmarkService, userSettingsService, analyticsService, annotationService, metadataFetchService, magicShelfService, templateRenderer, readingSessionService)
    private val opdsHandler = OpdsHandler(authService, libraryService, bookService, storageConfig, apiTokenService)
    private val apiTokenHandler = ApiTokenHandler(apiTokenService, jwtService)
    private val exportHandler = ExportHandler(exportService, jwtService)
    private val goodreadsImportHandler = GoodreadsImportHandler(goodreadsImportService, jwtService)
    private val bulkBookHandler = BulkBookHandler(bookService)
    private val authFilter = JwtAuthFilter(jwtService)
    private val adminFilter = authFilter.then(AdminFilter())
    private val authRateLimit = RateLimitFilter(maxRequests = 10, windowSeconds = 60)

    fun routes(): RoutingHttpHandler {
        return routes(
            "/static" bind static(ResourceLoader.Classpath("/static")),
            "/covers/{filename}" bind Method.GET to fileHandler::cover,
            // HTML pages
            "/" bind Method.GET to ::index,
            "/login" bind Method.GET to ::loginPage,
            "/register" bind Method.GET to ::registerPage,
            "/forgot-password" bind Method.GET to ::forgotPasswordPage,
            "/reset-password" bind Method.GET to ::resetPasswordPage,
            "/libraries" bind Method.GET to pageHandler::libraries,
            "/libraries/{id}" bind Method.GET to pageHandler::library,
            "/books/{id}" bind Method.GET to pageHandler::book,
            "/books/{id}/read" bind Method.GET to pageHandler::reader,
            "/search" bind Method.GET to pageHandler::search,
            "/series" bind Method.GET to authFilter.then(pageHandler::seriesList),
            "/series/{name}" bind Method.GET to authFilter.then(pageHandler::series),
            "/profile" bind Method.GET to pageHandler::profile,
            "/analytics" bind Method.GET to pageHandler::analytics,
            "/ui/preferences/analytics" bind Method.POST to pageHandler::setAnalytics,
            "/admin" bind Method.GET to adminFilter.then(adminHandler::adminPage),
            // Auth (rate-limited: 10 requests per 60 s per IP)
            "/auth/register" bind Method.POST to authRateLimit.then(authHandler::register),
            "/auth/login" bind Method.POST to authRateLimit.then(authHandler::login),
            "/auth/logout" bind Method.POST to authHandler::logout,
            "/auth/forgot-password" bind Method.POST to authRateLimit.then(authHandler::forgotPassword),
            "/auth/reset-password" bind Method.POST to authRateLimit.then(authHandler::resetPassword),
            // HTMX UI mutations
            "/ui/libraries" bind Method.POST to pageHandler::createLibrary,
            "/ui/libraries/{id}" bind Method.DELETE to pageHandler::deleteLibrary,
            "/ui/libraries/{id}/rename" bind Method.POST to pageHandler::renameLibrary,
            "/ui/libraries/{libId}/books" bind Method.POST to pageHandler::createBook,
            "/ui/books/{id}" bind Method.DELETE to pageHandler::deleteBook,
            "/ui/books/{id}/move" bind Method.POST to pageHandler::moveBook,
            "/ui/books/{id}/meta" bind Method.POST to pageHandler::editBook,
            "/ui/books/{id}/progress" bind Method.POST to pageHandler::updateProgress,
            "/ui/books/{id}/status" bind Method.POST to pageHandler::setStatus,
            "/ui/books/{id}/rating" bind Method.POST to pageHandler::setRating,
            "/ui/books/{id}/tags" bind Method.POST to pageHandler::setTags,
            "/ui/books/{id}/bookmarks" bind Method.POST to pageHandler::createBookmark,
            "/ui/bookmarks/{id}" bind Method.DELETE to pageHandler::deleteBookmark,
            "/ui/goal" bind Method.POST to pageHandler::setGoal,
            "/ui/books/{id}/fetch-metadata" bind Method.POST to pageHandler::fetchMetadata,
            "/ui/books/{id}/annotations" bind Method.GET to pageHandler::getAnnotations,
            "/ui/books/{id}/annotations" bind Method.POST to pageHandler::createAnnotation,
            "/ui/annotations/{id}" bind Method.DELETE to pageHandler::deleteAnnotation,
            // Health
            "/health" bind Method.GET to { Response(Status.OK).header("Content-Type", "application/json").body("""{"status":"ok"}""") },
            // Preferences
            "/preferences/theme" bind Method.POST to ::setTheme,
            "/preferences/lang" bind Method.POST to ::setLanguage,
            // JSON API
            "/api/libraries" bind Method.GET to authFilter.then(libraryHandler::list),
            "/api/libraries" bind Method.POST to authFilter.then(libraryHandler::create),
            "/api/libraries/{id}" bind Method.DELETE to authFilter.then(libraryHandler::delete),
            "/api/libraries/{id}/scan/async" bind Method.POST to authFilter.then(libraryHandler::scanAsync),
            "/api/libraries/{id}/scan/{jobId}" bind Method.GET to authFilter.then(libraryHandler::scanStatus),
            "/api/libraries/{id}/scan" bind Method.POST to authFilter.then(libraryHandler::scan),
            "/api/books" bind Method.GET to authFilter.then(bookHandler::list),
            "/api/books" bind Method.POST to authFilter.then(bookHandler::create),
            "/api/books/{id}" bind Method.GET to authFilter.then(bookHandler::get),
            "/api/books/{id}" bind Method.PUT to authFilter.then(bookHandler::update),
            "/api/books/{id}" bind Method.DELETE to authFilter.then(bookHandler::delete),
            "/api/books/{id}/progress" bind Method.PUT to authFilter.then(bookHandler::updateProgress),
            "/api/books/{id}/sessions" bind Method.GET to authFilter.then(bookHandler::sessions),
            "/api/recent" bind Method.GET to authFilter.then(bookHandler::recent),
            "/api/search" bind Method.GET to authFilter.then(bookHandler::search),
            "/api/bookmarks" bind Method.GET to authFilter.then(bookmarkHandler::list),
            "/api/bookmarks" bind Method.POST to authFilter.then(bookmarkHandler::create),
            "/api/bookmarks/{id}" bind Method.DELETE to authFilter.then(bookmarkHandler::delete),
            "/api/books/{id}/upload" bind Method.POST to authFilter.then(fileHandler::upload),
            "/api/books/{id}/cover" bind Method.POST to authFilter.then(fileHandler::uploadCover),
            "/api/books/{id}/file" bind Method.GET to authFilter.then(fileHandler::download),
            "/api/books/{id}/comic/pages" bind Method.GET to authFilter.then(::comicPages),
            "/api/books/{id}/comic/{page}" bind Method.GET to authFilter.then(::comicPage),
            "/api/books/bulk/move" bind Method.POST to authFilter.then(bulkBookHandler::move),
            "/api/books/bulk/delete" bind Method.POST to authFilter.then(bulkBookHandler::delete),
            "/api/books/bulk/tag" bind Method.POST to authFilter.then(bulkBookHandler::tag),
            "/api/books/bulk/status" bind Method.POST to authFilter.then(bulkBookHandler::status),
            "/api/auth/change-password" bind Method.POST to authRateLimit.then(authFilter.then(authHandler::changePassword)),
            "/api/auth/change-email" bind Method.POST to authRateLimit.then(authFilter.then(authHandler::changeEmail)),
            "/api/settings" bind Method.GET to authFilter.then(settingsHandler::getAll),
            "/api/settings/{key}" bind Method.PUT to authFilter.then(settingsHandler::set),
            "/api/settings/{key}" bind Method.DELETE to authFilter.then(settingsHandler::delete),
            // Admin API
            "/api/admin/password-reset-tokens" bind Method.GET to adminFilter.then(adminHandler::listResetTokens),
            "/api/admin/users" bind Method.GET to adminFilter.then(adminHandler::listUsers),
            "/api/admin/users/{userId}/promote" bind Method.POST to adminFilter.then(adminHandler::promote),
            "/api/admin/users/{userId}/demote" bind Method.POST to adminFilter.then(adminHandler::demote),
            "/api/admin/users/{userId}" bind Method.DELETE to adminFilter.then(adminHandler::deleteUser),
            // Weblate translation sync (admin-only endpoints, require Weblate to be enabled)
            "/api/weblate/pull" bind Method.POST to weblateHandler::pull,
            "/api/weblate/push" bind Method.POST to weblateHandler::push,
            "/api/weblate/status" bind Method.GET to weblateHandler::status,
            // API tokens
            "/api/tokens" bind Method.GET to authFilter.then(apiTokenHandler::list),
            "/api/tokens" bind Method.POST to authFilter.then(apiTokenHandler::create),
            "/api/tokens/{id}" bind Method.DELETE to authFilter.then(apiTokenHandler::revoke),
            // Export & Import
            "/api/export" bind Method.GET to authFilter.then(exportHandler::export),
            "/api/import/goodreads" bind Method.POST to authFilter.then(goodreadsImportHandler::import),
            // Smart shelves
            "/shelves/{id}" bind Method.GET to pageHandler::magicShelf,
            "/ui/shelves" bind Method.POST to pageHandler::createMagicShelf,
            "/ui/shelves/{id}" bind Method.DELETE to pageHandler::deleteMagicShelf,
            // OPDS Catalog 1.2 (HTTP Basic Auth — no JWT required)
            "/opds/catalog" bind Method.GET to opdsHandler::catalog,
            "/opds/catalog/{libraryId}" bind Method.GET to opdsHandler::library,
            "/opds/books/{id}/file" bind Method.GET to opdsHandler::download,
        )
    }

    private fun index(req: Request): Response {
        val token = req.cookie("token")?.value
        val isAuth = token != null && jwtService.extractUserId(token) != null
        if (isAuth) {
            return pageHandler.dashboard(req)
        }
        val ctx = WebContext(req)
        val content = templateRenderer.render(
            "index.kte",
            mapOf<String, Any?>(
                "title" to "BookTower",
                "isAuthenticated" to false,
                "username" to null,
                "libraries" to null,
                "showLogin" to false,
                "showRegister" to false,
                "themeCss" to ctx.themeCss,
                "currentTheme" to ctx.theme,
                "lang" to ctx.lang,
                "i18n" to ctx.i18n,
            ),
        )
        return Response(Status.OK).header("Content-Type", "text/html; charset=utf-8").body(content)
    }

    private fun loginPage(req: Request): Response {
        val ctx = WebContext(req)
        val content = templateRenderer.render(
            "index.kte",
            mapOf<String, Any?>(
                "title" to "Login - BookTower",
                "isAuthenticated" to false,
                "username" to null,
                "libraries" to null,
                "showLogin" to true,
                "showRegister" to false,
                "themeCss" to ctx.themeCss,
                "currentTheme" to ctx.theme,
                "lang" to ctx.lang,
                "i18n" to ctx.i18n,
            ),
        )
        return Response(Status.OK).header("Content-Type", "text/html; charset=utf-8").body(content)
    }

    private fun registerPage(req: Request): Response {
        val ctx = WebContext(req)
        val content = templateRenderer.render(
            "index.kte",
            mapOf<String, Any?>(
                "title" to "Register - BookTower",
                "isAuthenticated" to false,
                "username" to null,
                "libraries" to null,
                "showLogin" to false,
                "showRegister" to true,
                "themeCss" to ctx.themeCss,
                "currentTheme" to ctx.theme,
                "lang" to ctx.lang,
                "i18n" to ctx.i18n,
            ),
        )
        return Response(Status.OK).header("Content-Type", "text/html; charset=utf-8").body(content)
    }

    private fun forgotPasswordPage(req: Request): Response {
        val ctx = WebContext(req)
        val content = templateRenderer.render(
            "forgot-password.kte",
            mapOf<String, Any?>(
                "themeCss" to ctx.themeCss,
                "currentTheme" to ctx.theme,
                "lang" to ctx.lang,
                "i18n" to ctx.i18n,
            ),
        )
        return Response(Status.OK).header("Content-Type", "text/html; charset=utf-8").body(content)
    }

    private fun resetPasswordPage(req: Request): Response {
        val token = req.query("token") ?: ""
        val ctx = WebContext(req)
        val content = templateRenderer.render(
            "reset-password.kte",
            mapOf<String, Any?>(
                "token" to token,
                "themeCss" to ctx.themeCss,
                "currentTheme" to ctx.theme,
                "lang" to ctx.lang,
                "i18n" to ctx.i18n,
            ),
        )
        return Response(Status.OK).header("Content-Type", "text/html; charset=utf-8").body(content)
    }

    private fun setTheme(req: Request): Response {
        val themeId = req.form("theme")?.trim()?.takeIf { ThemeCatalog.isValid(it) } ?: "catppuccin-mocha"
        val css = ThemeCatalog.toCssVariables(themeId)
        val themeCookie = Cookie(name = "app_theme", value = themeId, path = "/", maxAge = 365L * 24 * 3600)
        return Response(Status.OK)
            .header("Content-Type", "text/html; charset=utf-8")
            .cookie(themeCookie)
            .body("""<style id="theme-style" data-theme="$themeId">$css</style>""")
    }

    private fun setLanguage(req: Request): Response {
        val lang = req.form("lang")?.trim()?.takeIf { it in WebContext.SUPPORTED_LANGS } ?: "en"
        val langCookie = Cookie(name = "app_lang", value = lang, path = "/", maxAge = 365L * 24 * 3600)
        return Response(Status.OK)
            .cookie(langCookie)
            .header("HX-Refresh", "true")
            .body("")
    }

    /** GET /api/books/{id}/comic/pages — returns page count for a CBZ/CBR book */
    private fun comicPages(req: Request): Response {
        val userId = jwtService.extractUserId(req.cookie("token")?.value ?: "") ?: return Response(Status.UNAUTHORIZED)
        val bookId = req.uri.path.split("/").dropLast(2).lastOrNull()
            ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
            ?: return Response(Status.BAD_REQUEST)
        val filePath = bookService.getBookFilePath(userId, bookId) ?: return Response(Status.NOT_FOUND)
        val count = comicService.getPageCount(filePath)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body("""{"pageCount":$count}""")
    }

    /** GET /api/books/{id}/comic/{page} — returns a single comic page image */
    private fun comicPage(req: Request): Response {
        val userId = jwtService.extractUserId(req.cookie("token")?.value ?: "") ?: return Response(Status.UNAUTHORIZED)
        val parts = req.uri.path.split("/").filter { it.isNotBlank() }
        val pageIndex = parts.lastOrNull()?.toIntOrNull() ?: return Response(Status.BAD_REQUEST)
        val bookId = parts.dropLast(2).lastOrNull()
            ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
            ?: return Response(Status.BAD_REQUEST)
        val filePath = bookService.getBookFilePath(userId, bookId) ?: return Response(Status.NOT_FOUND)
        val bytes = comicService.getPage(filePath, pageIndex) ?: return Response(Status.NOT_FOUND)
        val pages = comicService.listPages(filePath)
        val mime = pages.getOrNull(pageIndex)?.contentType ?: "image/jpeg"
        return Response(Status.OK)
            .header("Content-Type", mime)
            .header("Cache-Control", "private, max-age=3600")
            .body(bytes.inputStream())
    }
}
