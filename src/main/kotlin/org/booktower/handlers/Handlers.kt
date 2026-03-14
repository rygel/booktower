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
            "/api/libraries/{id}" bind Method.DELETE to libraryHandler::delete,
            "/api/books" bind Method.GET to bookHandler::list,
            "/api/books" bind Method.POST to bookHandler::create,
            "/api/books/{id}" bind Method.GET to bookHandler::get,
            "/api/recent" bind Method.GET to bookHandler::recent,
            "/preferences/theme" bind Method.POST to ::setTheme,
            "/preferences/lang" bind Method.POST to ::setLanguage,
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
                    "index.kte",
                    mapOf<String, Any?>(
                        "title" to "BookTower",
                        "isAuthenticated" to true,
                        "username" to null,
                        "libraries" to libraries.map { mapOf("id" to it.id, "name" to it.name) },
                    ),
                )
            } else {
                TemplateEngine.render(
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
            TemplateEngine.render(
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
            TemplateEngine.render(
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
        
        if (isHtmx) {
            return Response(Status.OK)
                .header("HX-Trigger", "theme-updated")
                .header("HX-Reswap", "none")
                .body("Theme updated to $theme")
        } else {
            return Response(Status.SEE_OTHER)
                .header("Location", "/")
                .body("Theme updated. Redirecting...")
        }
    }

    private fun setLanguage(req: Request): Response {
        val isHtmx = req.header("HX-Request") != null
        
        val lang = req.bodyString().trim().let {
            if (it.isNotBlank()) it else "en"
        }
        
        if (isHtmx) {
            return Response(Status.OK)
                .header("HX-Trigger", "lang-updated")
                .header("HX-Reswap", "none")
                .body("Language updated to $lang")
        } else {
            return Response(Status.SEE_OTHER)
                .header("Location", "/")
                .body("Language updated. Redirecting...")
        }
    }
}
