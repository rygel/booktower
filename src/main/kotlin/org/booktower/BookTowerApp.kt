package org.booktower

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.booktower.config.AppConfig
import org.booktower.config.Database
import org.booktower.handlers.AppHandler
import org.booktower.services.AuthService
import org.booktower.services.BookService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.filter.ServerFilters
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Jetty
import org.http4k.server.asServer

fun main() {
    val logger: Logger = LoggerFactory.getLogger("booktower.Main")
    
    logger.info("Starting BookTower application...")

    val config = AppConfig.load()
    config.storage.ensureDirectories()
    val database = Database.connect(config.database)
    
    val jwtService = JwtService(config.security)
    val authService = AuthService(database.jdbi, jwtService)
    val libraryService = LibraryService(database.jdbi, config.storage)
    val bookService = BookService(database.jdbi, config.storage)
    
    val appHandler = AppHandler(authService, libraryService, bookService, jwtService)
    
    val healthHandler: HttpHandler = { Response(OK).body("OK") }
    
    val app: RoutingHttpHandler = routes(
        "/health" bind Method.GET to healthHandler,
        appHandler.routes()
    )
    
    val filteredApp = ServerFilters.CatchAll().then(app)
    
    logger.info("Starting server on http://${config.host}:${config.port}")
    
    filteredApp.asServer(Jetty(config.port)).start()
    
    logger.info("BookTower started successfully!")
}
