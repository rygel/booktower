package org.booktower

import org.booktower.config.AppConfig
import org.booktower.config.Database
import org.booktower.config.appModule
import org.booktower.filters.CsrfFilter
import org.booktower.filters.GlobalErrorFilter
import org.booktower.filters.RequestLoggingFilter
import org.booktower.filters.StaticCacheFilter
import org.booktower.handlers.AppHandler
import org.booktower.services.AuthService
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger("booktower.Main")

    logger.info("Starting BookTower application...")

    startKoin { modules(appModule) }
    val koin = GlobalContext.get()

    val config = koin.get<AppConfig>()
    val database = koin.get<Database>()
    val appHandler = koin.get<AppHandler>()

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutting down BookTower...")
        database.close()
    })

    if (System.getenv("BOOKTOWER_ENV")?.lowercase() != "production") {
        val authService = koin.get<AuthService>()
        val devLogin = authService.seedDevUser()
        if (devLogin != null) {
            logger.info("=================================================")
            logger.info("  DEV USER READY")
            logger.info("  Username: dev")
            logger.info("  Password: dev12345")
            logger.info("  Email:    dev@booktower.local")
            logger.info("=================================================")
        }
    }

    val app = routes(
        "/health" bind Method.GET to { Response(OK).body("OK") },
        appHandler.routes(),
    )

    val filteredApp = GlobalErrorFilter()
        .then(RequestLoggingFilter())
        .then(CsrfFilter(config.csrf.allowedHosts))
        .then(StaticCacheFilter())
        .then(app)

    logger.info("Starting server on http://${config.host}:${config.port}")

    filteredApp.asServer(Jetty(config.port)).start()

    logger.info("BookTower started successfully!")
}
