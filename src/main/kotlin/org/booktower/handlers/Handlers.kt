package org.booktower.handlers

import org.booktower.config.TemplateEngine
import org.booktower.services.AuthService
import org.booktower.services.BookService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.booktower.web.WebContext
import org.http4k.core.*
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.routing.ResourceLoader
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.net.URLDecoder

private val logger = LoggerFactory.getLogger("booktower.handlers")
private val objectMapper = ObjectMapper()

class AppHandler(
    private val authService: AuthService,
    private val libraryService: LibraryService,
    private val bookService: BookService,
    private val jwtService: JwtService
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
            "/api/recent" bind Method.GET to bookHandler::recent
        )
    }
    
    private fun index(req: Request): Response {
        val ctx = WebContext(req)
        val token = req.cookie("token")?.value
        val isAuth = token != null && jwtService.extractUserId(token) != null

        val content = if (isAuth && token != null) {
            val userId = jwtService.extractUserId(token)!!
            val libraries = libraryService.getLibraries(userId)
            TemplateEngine.render("home.kte", mapOf(
                "isAuthenticated" to true,
                "libraries" to libraries.map { mapOf("id" to it.id, "name" to it.name) }
            ))
        } else {
            TemplateEngine.render("home.kte", mapOf(
                "isAuthenticated" to false,
                "libraries" to emptyList<Map<String, String>>()
            ))
        }

        return Response(Status.OK)
            .header("Content-Type", "text/html")
            .body(content)
    }
    
    private fun loginPage(req: Request): Response {
        val ctx = WebContext(req)
        val content = TemplateEngine.render("home.kte", mapOf(
            "isAuthenticated" to false,
            "showLogin" to true,
            "themeCss" to ctx.themeCss,
            "lang" to ctx.lang,
            "i18n" to ctx.i18n
        ))
        return Response(Status.OK)
            .header("Content-Type", "text/html")
            .body(content)
    }
    
    private fun registerPage(req: Request): Response {
        val ctx = WebContext(req)
        val content = TemplateEngine.render("home.kte", mapOf(
            "isAuthenticated" to false,
            "showRegister" to true,
            "themeCss" to ctx.themeCss,
            "lang" to ctx.lang,
            "i18n" to ctx.i18n
        ))
        return Response(Status.OK)
            .header("Content-Type", "text/html")
            .body(content)
    }
}

class AuthHandler2(private val authService: AuthService) {
    fun register(req: Request): Response {
        val params = parseForm(req)
        val username = params["username"] ?: return error("Missing username")
        val email = params["email"] ?: return error("Missing email")
        val password = params["password"] ?: return error("Missing password")

        val result = authService.register(org.booktower.models.CreateUserRequest(username, email, password))
        return result.fold(
            { r: org.booktower.models.LoginResponse -> Response(Status.CREATED).header("Set-Cookie", "token=${r.token}; Path=/; HttpOnly").body("""{"user": "${r.user.username}"}""") },
            { e: Throwable -> Response(Status.BAD_REQUEST).body("""{"error": "${e.message}"}""") }
        )
    }

    fun login(req: Request): Response {
        val params = parseForm(req)
        val username = params["username"] ?: return error("Missing username")
        val password = params["password"] ?: return error("Missing password")

        val result = authService.login(org.booktower.models.LoginRequest(username, password))
        return result.fold(
            { r: org.booktower.models.LoginResponse -> Response(Status.OK).header("Set-Cookie", "token=${r.token}; Path=/; HttpOnly").body("""{"user": "${r.user.username}"}""") },
            { e: Throwable -> Response(Status.UNAUTHORIZED).body("""{"error": "${e.message}"}""") }
        )
    }

    fun logout(req: Request): Response {
        return Response(Status.OK).header("Set-Cookie", "token=; Path=/; Max-Age=0").body("""{"message": "logged out"}""")
    }
    
    private fun error(msg: String) = Response(Status.BAD_REQUEST).body(msg)
    
    private fun parseForm(req: Request): Map<String, String> {
        return req.bodyString().split("&").associate { pair ->
            val parts = pair.split("=")
            val key = parts.getOrElse(0) { "" }
            val value = parts.getOrElse(1) { "" }
            key to URLDecoder.decode(value, Charsets.UTF_8)
        }
    }
}

class LibraryHandler2(private val libraryService: LibraryService, private val jwtService: JwtService) {
    private fun userId(req: Request): java.util.UUID? {
        return req.cookie("token")?.value?.let { jwtService.extractUserId(it) }
    }
    
    private fun requireUser(req: Request): Response? {
        val uid = userId(req) ?: return Response(Status.UNAUTHORIZED).body("No token")
        return null
    }

    fun list(req: Request): Response {
        requireUser(req)?.let { return it }
        val uid = userId(req)!!
        val libraries = libraryService.getLibraries(uid)
        return Response(Status.OK).header("Content-Type", "application/json").body(objectMapper.writeValueAsString(libraries))
    }
    
    fun create(req: Request): Response {
        requireUser(req)?.let { return it }
        val uid = userId(req)!!
        val params = parseForm(req)
        val name = params["name"] ?: return error("Missing name")
        val path = params["path"] ?: return error("Missing path")
        
        val library = libraryService.createLibrary(uid, org.booktower.models.CreateLibraryRequest(name, path))
        return Response(Status.CREATED).header("Content-Type", "application/json").body(objectMapper.writeValueAsString(library))
    }
    
    private fun error(msg: String) = Response(Status.BAD_REQUEST).body(msg)
    
    private fun parseForm(req: Request): Map<String, String> {
        return req.bodyString().split("&").associate { pair ->
            val parts = pair.split("=")
            val key = parts.getOrElse(0) { "" }
            val value = parts.getOrElse(1) { "" }
            key to URLDecoder.decode(value, Charsets.UTF_8)
        }
    }
}

class BookHandler2(private val bookService: BookService, private val jwtService: JwtService) {
    private fun userId(req: Request): java.util.UUID? {
        return req.cookie("token")?.value?.let { jwtService.extractUserId(it) }
    }
    
    private fun requireUser(req: Request): Response? {
        val uid = userId(req) ?: return Response(Status.UNAUTHORIZED).body("No token")
        return null
    }

    fun list(req: Request): Response {
        requireUser(req)?.let { return it }
        val uid = userId(req)!!
        val libraryId = req.query("libraryId")
        val page = req.query("page")?.toIntOrNull() ?: 1
        
        val books = bookService.getBooks(uid, libraryId, page)
        return Response(Status.OK).header("Content-Type", "application/json").body(objectMapper.writeValueAsString(books))
    }
    
    fun create(req: Request): Response {
        requireUser(req)?.let { return it }
        val uid = userId(req)!!
        val params = parseForm(req)
        val title = params["title"] ?: return error("Missing title")
        val libraryId = params["libraryId"] ?: return error("Missing libraryId")
        
        val result = bookService.createBook(uid, org.booktower.models.CreateBookRequest(title, null, null, libraryId))
        return result.fold(
            { b: org.booktower.models.BookDto -> Response(Status.CREATED).body(objectMapper.writeValueAsString(b)) },
            { e: Throwable -> Response(Status.BAD_REQUEST).body(e.message ?: "Error") }
        )
    }
    
    fun recent(req: Request): Response {
        requireUser(req)?.let { return it }
        val uid = userId(req)!!
        val books = bookService.getRecentBooks(uid)
        return Response(Status.OK).header("Content-Type", "application/json").body(objectMapper.writeValueAsString(books))
    }
    
    private fun error(msg: String) = Response(Status.BAD_REQUEST).body(msg)
    
    private fun parseForm(req: Request): Map<String, String> {
        return req.bodyString().split("&").associate { pair ->
            val parts = pair.split("=")
            val key = parts.getOrElse(0) { "" }
            val value = parts.getOrElse(1) { "" }
            key to URLDecoder.decode(value, Charsets.UTF_8)
        }
    }
}
