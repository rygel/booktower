package org.booktower.routers

import org.booktower.config.TemplateRenderer
import org.booktower.handlers.AdminHandler
import org.booktower.handlers.PageHandler
import org.booktower.model.ThemeCatalog
import org.booktower.services.JwtService
import org.booktower.services.UserSettingsService
import org.booktower.web.WebContext
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.core.then
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind

class PageRouter(
    private val filters: FilterSet,
    private val pageHandler: PageHandler,
    private val adminHandler: AdminHandler,
    private val jwtService: JwtService,
    private val templateRenderer: TemplateRenderer,
    private val registrationOpen: Boolean,
    private val userSettingsService: UserSettingsService? = null,
) {
    fun routes(): List<RoutingHttpHandler> =
        listOf(
            // HTML pages
            "/" bind Method.GET to filters.optionalAuth.then(::index),
            "/login" bind Method.GET to ::loginPage,
            "/register" bind Method.GET to ::registerPage,
            "/forgot-password" bind Method.GET to ::forgotPasswordPage,
            "/reset-password" bind Method.GET to ::resetPasswordPage,
            "/libraries" bind Method.GET to filters.optionalAuth.then(pageHandler::libraries),
            "/libraries/{id}" bind Method.GET to filters.optionalAuth.then(pageHandler::library),
            "/books/{id}" bind Method.GET to filters.optionalAuth.then(pageHandler::book),
            "/books/{id}/read" bind Method.GET to filters.optionalAuth.then(pageHandler::reader),
            "/search" bind Method.GET to filters.optionalAuth.then(pageHandler::search),
            "/queue" bind Method.GET to filters.auth.then(pageHandler::queue),
            "/series" bind Method.GET to filters.auth.then(pageHandler::seriesList),
            "/series/{name}" bind Method.GET to filters.auth.then(pageHandler::series),
            "/authors" bind Method.GET to filters.auth.then(pageHandler::authorList),
            "/authors/{name}" bind Method.GET to filters.auth.then(pageHandler::author),
            "/tags" bind Method.GET to filters.auth.then(pageHandler::tagList),
            "/tags/{name}" bind Method.GET to filters.auth.then(pageHandler::tag),
            "/profile" bind Method.GET to filters.optionalAuth.then(pageHandler::profile),
            "/activity" bind Method.GET to filters.auth.then(pageHandler::activity),
            "/downloads" bind Method.GET to filters.optionalAuth.then(pageHandler::downloads),
            "/shared/book/{token}" bind Method.GET to filters.optionalAuth.then(pageHandler::sharedBook),
            "/analytics" bind Method.GET to filters.optionalAuth.then(pageHandler::analytics),
            "/stats" bind Method.GET to filters.optionalAuth.then(pageHandler::libraryStats),
            "/webhooks" bind Method.GET to filters.auth.then(pageHandler::webhooks),
            "/ui/preferences/analytics" bind Method.POST to pageHandler::setAnalytics,
            "/admin" bind Method.GET to filters.admin.then(adminHandler::adminPage),
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
            // Preferences
            "/preferences/theme" bind Method.POST to ::setTheme,
            "/preferences/theme-pair" bind Method.POST to ::setThemePair,
            "/preferences/lang" bind Method.POST to ::setLanguage,
            // Smart shelves (UI)
            "/shelves/{id}" bind Method.GET to pageHandler::magicShelf,
            "/ui/shelves" bind Method.POST to pageHandler::createMagicShelf,
            "/ui/shelves/{id}" bind Method.DELETE to pageHandler::deleteMagicShelf,
        )

    private fun index(req: Request): Response {
        val isAuth = req.header("X-Auth-User-Id") != null
        if (isAuth) {
            return pageHandler.dashboard(req)
        }
        val ctx = WebContext(req)
        val content =
            templateRenderer.render(
                "index.kte",
                mapOf<String, Any?>(
                    "title" to "BookTower",
                    "isAuthenticated" to false,
                    "username" to null,
                    "libraries" to null,
                    "showLogin" to false,
                    "showRegister" to false,
                    "registrationOpen" to registrationOpen,
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
                    "registrationOpen" to registrationOpen,
                    "themeCss" to ctx.themeCss,
                    "currentTheme" to ctx.theme,
                    "lang" to ctx.lang,
                    "i18n" to ctx.i18n,
                ),
            )
        return Response(Status.OK).header("Content-Type", "text/html; charset=utf-8").body(content)
    }

    private fun registerPage(req: Request): Response {
        if (!registrationOpen) {
            return Response(Status.SEE_OTHER).header("Location", "/login")
        }
        val ctx = WebContext(req)
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
                    "registrationOpen" to true,
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
        val content =
            templateRenderer.render(
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
        val content =
            templateRenderer.render(
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
        // Persist to user settings so preference survives across devices
        authenticatedUserId(req)?.let { userId ->
            userSettingsService?.set(userId, "pref.theme", themeId)
        }
        return Response(Status.OK)
            .header("Content-Type", "text/html; charset=utf-8")
            .cookie(themeCookie)
            .body("""<style id="theme-style" data-theme="$themeId">$css</style>""")
    }

    private fun setLanguage(req: Request): Response {
        val lang = req.form("lang")?.trim()?.takeIf { it in WebContext.SUPPORTED_LANGS } ?: "en"
        val langCookie = Cookie(name = "app_lang", value = lang, path = "/", maxAge = 365L * 24 * 3600)
        // Persist to user settings so preference survives across devices
        authenticatedUserId(req)?.let { userId ->
            userSettingsService?.set(userId, "pref.lang", lang)
        }
        return Response(Status.OK)
            .cookie(langCookie)
            .header("HX-Refresh", "true")
            .body("")
    }

    private fun setThemePair(req: Request): Response {
        val userId = authenticatedUserId(req) ?: return Response(Status.UNAUTHORIZED)
        val darkTheme = req.form("darkTheme")?.trim()?.takeIf { ThemeCatalog.isValid(it) }
        val lightTheme = req.form("lightTheme")?.trim()?.takeIf { ThemeCatalog.isValid(it) }
        if (darkTheme != null) userSettingsService?.set(userId, "pref.theme.dark", darkTheme)
        if (lightTheme != null) userSettingsService?.set(userId, "pref.theme.light", lightTheme)
        var resp =
            Response(Status.OK)
                .header("HX-Trigger", """{"showToast":{"message":"Theme pair saved","type":"success"}}""")
                .body("")
        if (darkTheme != null) resp = resp.cookie(Cookie(name = "pref_dark_theme", value = darkTheme, path = "/", maxAge = 365L * 24 * 3600))
        if (lightTheme != null) resp = resp.cookie(Cookie(name = "pref_light_theme", value = lightTheme, path = "/", maxAge = 365L * 24 * 3600))
        return resp
    }

    private fun authenticatedUserId(req: Request): java.util.UUID? {
        val token = req.cookie("token")?.value ?: return null
        return jwtService.extractUserId(token)
    }
}
