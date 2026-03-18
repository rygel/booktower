package org.booktower

import org.booktower.config.AppConfig
import org.booktower.config.Database
import org.booktower.config.appModule
import org.booktower.filters.csrfFilter
import org.booktower.filters.globalErrorFilter
import org.booktower.filters.requestLoggingFilter
import org.booktower.filters.staticCacheFilter
import org.booktower.handlers.AppHandler
import org.booktower.models.CreateLibraryRequest
import org.booktower.models.CreateUserRequest
import org.booktower.services.AuthService
import org.booktower.services.ComicPageHashWorker
import org.booktower.services.EpubMetadataService
import org.booktower.services.FtsIndexWorker
import org.booktower.services.FtsService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.booktower.services.LibraryWatchService
import org.booktower.services.PdfMetadataService
import org.booktower.services.ScanScheduleService
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
    val pdfMetadataService = koin.get<PdfMetadataService>()
    val epubMetadataService = koin.get<EpubMetadataService>()
    val scanScheduleService = koin.get<ScanScheduleService>()
    val libraryWatchService = koin.get<LibraryWatchService>()

    scanScheduleService.start()
    libraryWatchService.start()

    val ftsService = koin.get<FtsService>()
    val ftsIndexWorker = koin.get<FtsIndexWorker>()
    ftsService.initialize()
    ftsIndexWorker.start()

    val comicPageHashWorker = koin.get<ComicPageHashWorker>()
    comicPageHashWorker.start()

    Runtime.getRuntime().addShutdownHook(
        Thread {
            logger.info("Shutting down BookTower...")
            pdfMetadataService.shutdown()
            epubMetadataService.shutdown()
            database.close()
            ftsIndexWorker.stop()
            comicPageHashWorker.stop()
        },
    )

    val isProduction = System.getenv("BOOKTOWER_ENV")?.lowercase() == "production"
    val isQuickstart = System.getenv("BOOKTOWER_QUICKSTART")?.lowercase() == "true"

    if (!isProduction) {
        val authService = koin.get<AuthService>()
        val devLogin = authService.seedDevUser()
        if (devLogin != null) {
            logger.info("=================================================")
            logger.info("  DEV USER READY")
            logger.info("  Username: dev  |  Password: dev12345")
            logger.info("=================================================")
        }
    }

    if (isQuickstart) {
        val authService = koin.get<AuthService>()
        val jwtService = koin.get<JwtService>()
        val libraryService = koin.get<LibraryService>()
        val result =
            authService.register(
                CreateUserRequest("demo", "demo@booktower.local", "demo1234"),
            )
        if (result.isSuccess) {
            val userId = jwtService.extractUserId(result.getOrThrow().token)!!
            libraryService.createLibrary(userId, CreateLibraryRequest("My Library", "${config.storage.booksPath}/demo"))
            logger.info("=================================================")
            logger.info("  QUICKSTART MODE")
            logger.info("  Open:     http://localhost:${config.port}")
            logger.info("  Username: demo")
            logger.info("  Password: demo1234")
            logger.info("=================================================")
        } else {
            // demo user already exists from a previous run — that is fine
            logger.info("Quickstart: demo user already exists, skipping seed")
        }
    }

    val app =
        routes(
            "/health" bind Method.GET to { Response(OK).body("OK") },
            appHandler.routes(),
        )

    val filteredApp =
        globalErrorFilter()
            .then(requestLoggingFilter())
            .then(csrfFilter(config.csrf.allowedHosts))
            .then(staticCacheFilter())
            .then(app)

    logger.info("Starting server on http://${config.host}:${config.port}")

    filteredApp.asServer(Jetty(config.port)).start()

    logger.info("BookTower started successfully!")
}
