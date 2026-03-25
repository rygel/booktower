package org.runary

import org.runary.config.AppConfig
import org.runary.config.Database
import org.runary.config.appModule
import org.runary.filters.bodyLimitFilter
import org.runary.filters.compressionFilter
import org.runary.filters.csrfFilter
import org.runary.filters.globalErrorFilter
import org.runary.filters.requestLoggingFilter
import org.runary.filters.staticCacheFilter
import org.runary.handlers.AppHandler
import org.runary.models.CreateLibraryRequest
import org.runary.models.CreateUserRequest
import org.runary.services.AuthService
import org.runary.services.ComicPageHashWorker
import org.runary.services.EpubMetadataService
import org.runary.services.FtsIndexWorker
import org.runary.services.FtsService
import org.runary.services.JwtService
import org.runary.services.LibraryService
import org.runary.services.LibraryWatchService
import org.runary.services.PdfMetadataService
import org.runary.services.ScanScheduleService
import org.runary.services.UserSettingsService
import org.runary.web.WebContext
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
    val logger = LoggerFactory.getLogger("runary.Main")

    logger.info("Starting Runary application...")
    val startTime = System.currentTimeMillis()

    startKoin { modules(appModule) }
    val koin = GlobalContext.get()

    val config = koin.get<AppConfig>()
    val database = koin.get<Database>()
    val appHandler = koin.get<AppHandler>()
    val pdfMetadataService = koin.get<PdfMetadataService>()
    val epubMetadataService = koin.get<EpubMetadataService>()
    val scanScheduleService = koin.get<ScanScheduleService>()
    val libraryWatchService = koin.get<LibraryWatchService>()

    // Wire user settings into WebContext for theme/language preference persistence
    val userSettingsService = koin.get<UserSettingsService>()
    WebContext.settingsProvider = { userId, key -> userSettingsService.get(userId, key) }

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
            logger.info("Shutting down Runary...")
            pdfMetadataService.shutdown()
            epubMetadataService.shutdown()
            database.close()
            ftsIndexWorker.stop()
            comicPageHashWorker.stop()
        },
    )

    val isProduction = System.getenv("RUNARY_ENV")?.lowercase() == "production"
    val isQuickstart = System.getenv("RUNARY_QUICKSTART")?.lowercase() == "true"

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
                CreateUserRequest("demo", "demo@runary.local", "demo1234"),
            )
        if (result.isSuccess) {
            val userId =
                jwtService.extractUserId(result.getOrThrow().token)
                    ?: error("Failed to extract user ID from quickstart token")
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
            "/health" bind Method.GET to {
                val dbOk =
                    try {
                        database.getJdbi().withHandle<Boolean, Exception> { h ->
                            h.createQuery("SELECT 1").mapTo(Int::class.java).firstOrNull() == 1
                        }
                    } catch (_: Exception) {
                        false
                    }
                val version = org.runary.services.VersionService.info.version
                val uptimeMs = System.currentTimeMillis() - startTime
                val uptimeSec = uptimeMs / 1000
                Response(OK)
                    .header("Content-Type", "application/json")
                    .body("""{"status":"${if (dbOk) "up" else "degraded"}","database":"${if (dbOk) "ok" else "unreachable"}","version":"$version","uptimeSeconds":$uptimeSec}""")
            },
            appHandler.routes(),
        )

    val filteredApp =
        globalErrorFilter()
            .then(bodyLimitFilter())
            .then(compressionFilter())
            .then(requestLoggingFilter())
            .then(csrfFilter(config.csrf.allowedHosts))
            .then(staticCacheFilter())
            .then(app)

    logger.info("Starting server on http://${config.host}:${config.port}")

    filteredApp.asServer(Jetty(config.port)).start()

    logger.info("Runary started successfully!")
}
