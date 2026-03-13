package org.booktower.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import org.booktower.config.TemplateEngine
import org.booktower.services.AuthService
import org.booktower.services.BookService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.booktower.web.WebContext
import org.http4k.core.*
import org.http4k.core.cookie.cookie
import org.http4k.routing.ResourceLoader
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("booktower.handlers")
private val objectMapper = ObjectMapper()

class AppHandler(
    private val authService: AuthService,
    private val libraryService: LibraryService,
    private val bookService: BookService,
    private val jwtService: JwtService,
) {
    private val authHandler = AuthHandler2(authService)
    private val libraryHandler = LibraryHandler2(libraryService, jwtService)
    private val bookHandler = BookHandler2(bookService, jwtService)

    fun routes(): RoutingHttpHandler {
        return routes(
            "/static" bind static(ResourceLoader.Classpath("/static")),
            "/" bind Method.GET to ::index,
            "/login" bind Method.GET to ::loginPage,
            "/register" bind Method.GET to ::registerPage,
            "/auth/register" bind Method.POST to authHandler::register,
            "/auth/login" bind Method.POST to authHandler::login,
            "/auth/logout" bind Method.POST to authHandler::logout,
            "/api/libraries" bind Method.GET to libraryHandler::list,
            "/api/libraries" bind Method.POST to libraryHandler::create,
            "/api/books" bind Method.GET to bookHandler::list,
            "/api/books" bind Method.POST to bookHandler::create,
            "/api/recent" bind Method.GET to bookHandler::recent,
        )
    }

    private fun index(req: Request): Response {
        val ctx = WebContext(req)
        val token = req.cookie("token")?.value
        val isAuth = token != null && jwtService.extractUserId(token) != null

        val content =
            if (isAuth) {
                val userId = jwtService.extractUserId(token)!!
                val libraries = libraryService.getLibraries(userId)
                TemplateEngine.render(
                    "home.kte",
                    mapOf(
                        "isAuthenticated" to true,
                        "libraries" to libraries.map { mapOf("id" to it.id, "name" to it.name) },
                    ),
                )
            } else {
                TemplateEngine.render(
                    "home.kte",
                    mapOf(
                        "isAuthenticated" to false,
                        "libraries" to emptyList<Map<String, String>>(),
                    ),
                )
            }

        return Response(Status.OK)
            .header("Content-Type", "text/html")
            .body(content)
    }

    private fun loginPage(req: Request): Response {
        val ctx = WebContext(req)
        val content =
            TemplateEngine.render(
                "home.kte",
                mapOf(
                    "isAuthenticated" to false,
                    "showLogin" to true,
                    "themeCss" to ctx.themeCss,
                    "lang" to ctx.lang,
                    "i18n" to ctx.i18n,
                ),
            )
        return Response(Status.OK)
            .header("Content-Type", "text/html")
            .body(content)
    }

    private fun registerPage(req: Request): Response {
        val ctx = WebContext(req)
        val content =
            TemplateEngine.render(
                "home.kte",
                mapOf(
                    "isAuthenticated" to false,
                    "showRegister" to true,
                    "themeCss" to ctx.themeCss,
                    "lang" to ctx.lang,
                    "i18n" to ctx.i18n,
                ),
            )
        return Response(Status.OK)
            .header("Content-Type", "text/html")
            .body(content)
    }
}
