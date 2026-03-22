package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.config.Json
import org.booktower.config.OidcConfig
import org.booktower.config.SmtpConfig
import org.booktower.config.WeblateConfig
import org.booktower.filters.DemoModeFilter
import org.booktower.filters.RateLimitFilter
import org.booktower.filters.adminFilter
import org.booktower.filters.globalErrorFilter
import org.booktower.filters.jwtAuthFilter
import org.booktower.handlers.AdminHandler
import org.booktower.handlers.ApiTokenHandler
import org.booktower.handlers.AppHandler
import org.booktower.handlers.AuthHandler2
import org.booktower.handlers.BackgroundTaskHandler
import org.booktower.handlers.BookHandler2
import org.booktower.handlers.BookmarkHandler
import org.booktower.handlers.BulkBookHandler
import org.booktower.handlers.ExportHandler
import org.booktower.handlers.FileHandler
import org.booktower.handlers.GoodreadsImportHandler
import org.booktower.handlers.JournalHandler
import org.booktower.handlers.LibraryHandler2
import org.booktower.handlers.OpdsHandler
import org.booktower.handlers.PageHandler
import org.booktower.handlers.UserSettingsHandler
import org.booktower.models.BookDto
import org.booktower.models.LibraryDto
import org.booktower.models.LoginResponse
import org.booktower.routers.AdminApiRouter
import org.booktower.routers.AudiobookApiRouter
import org.booktower.routers.AuthRouter
import org.booktower.routers.BookApiRouter
import org.booktower.routers.DeviceSyncRouter
import org.booktower.routers.FilterSet
import org.booktower.routers.LibraryApiRouter
import org.booktower.routers.MetadataApiRouter
import org.booktower.routers.OidcRouter
import org.booktower.routers.PageRouter
import org.booktower.routers.UserApiRouter
import org.booktower.services.AdminService
import org.booktower.services.AnalyticsService
import org.booktower.services.AnnotationService
import org.booktower.services.ApiTokenService
import org.booktower.services.AudiobookMetaService
import org.booktower.services.AuthService
import org.booktower.services.BookFilesService
import org.booktower.services.BookNotebookService
import org.booktower.services.BookReviewService
import org.booktower.services.BookService
import org.booktower.services.BookmarkService
import org.booktower.services.BulkCoverService
import org.booktower.services.CalibreConversionService
import org.booktower.services.ComicMetadataService
import org.booktower.services.ComicService
import org.booktower.services.ContentRestrictionsService
import org.booktower.services.DuplicateDetectionService
import org.booktower.services.EmailProviderService
import org.booktower.services.EmailService
import org.booktower.services.EpubMetadataService
import org.booktower.services.ExportService
import org.booktower.services.FilterPresetService
import org.booktower.services.GeoIpService
import org.booktower.services.GeoLocation
import org.booktower.services.GoodreadsImportService
import org.booktower.services.HardcoverSyncService
import org.booktower.services.JournalService
import org.booktower.services.JwtService
import org.booktower.services.KoboSyncService
import org.booktower.services.LibraryAccessService
import org.booktower.services.LibraryService
import org.booktower.services.ListeningSessionService
import org.booktower.services.ListeningStatsService
import org.booktower.services.MagicShelfService
import org.booktower.services.MetadataFetchService
import org.booktower.services.MetadataLockService
import org.booktower.services.MetadataProposalService
import org.booktower.services.NotificationService
import org.booktower.services.OidcService
import org.booktower.services.OpdsCredentialsService
import org.booktower.services.PasswordResetService
import org.booktower.services.PdfMetadataService
import org.booktower.services.ReadingSessionService
import org.booktower.services.ReadingStatsService
import org.booktower.services.ScheduledTaskService
import org.booktower.services.SeedService
import org.booktower.services.TelemetryService
import org.booktower.services.UserPermissionsService
import org.booktower.services.UserSettingsService
import org.booktower.weblate.WeblateHandler
import org.http4k.client.JavaHttpClient
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Uri
import org.http4k.core.then
import org.junit.jupiter.api.BeforeEach

abstract class IntegrationTestBase {
    protected lateinit var app: HttpHandler

    companion object {
        /** Set via -De2e.baseUrl=http://localhost:9999 to run tests against an external instance. */
        fun externalBaseUrl(): String? = System.getProperty("e2e.baseUrl")?.takeIf { it.isNotBlank() }

        fun isExternal(): Boolean = externalBaseUrl() != null
    }

    @BeforeEach
    open fun setupApp() {
        val baseUrl = externalBaseUrl()
        app =
            if (baseUrl != null) {
                createExternalClient(baseUrl)
            } else {
                buildApp()
            }
    }

    private fun createExternalClient(baseUrl: String): HttpHandler {
        val client = JavaHttpClient()
        return { req: Request ->
            val targetUri = Uri.of(baseUrl + req.uri.path + (if (req.uri.query.isNotBlank()) "?${req.uri.query}" else ""))
            client(req.uri(targetUri))
        }
    }

    @Suppress("LongMethod")
    fun buildApp(
        registrationOpen: Boolean = true,
        demoMode: Boolean = false,
        oidcForceOnly: Boolean = false,
    ): HttpHandler {
        val config = TestFixture.config
        val jdbi = TestFixture.database.getJdbi()

        // ── Services ─────────────────────────────────────────────────────
        val jwtService = JwtService(config.security)
        val authService = AuthService(jdbi, jwtService)
        val pdfMetadataService = PdfMetadataService(jdbi, config.storage.coversPath)
        val libraryAccessService = LibraryAccessService(jdbi)
        val metadataLockService = MetadataLockService(jdbi)
        val readingStatsService = ReadingStatsService(jdbi)
        val listeningSessionService = ListeningSessionService(jdbi)
        val listeningStatsService = ListeningStatsService(jdbi)
        val metadataProposalService = MetadataProposalService(jdbi)
        val libraryService = LibraryService(jdbi, pdfMetadataService, libraryAccessService)
        val bookmarkService = BookmarkService(jdbi)
        val userSettingsService = UserSettingsService(jdbi)
        val hardcoverSyncService = HardcoverSyncService(jdbi, userSettingsService)
        val analyticsService = AnalyticsService(jdbi, userSettingsService)
        val readingSessionService = ReadingSessionService(jdbi)
        val bookService = BookService(jdbi, analyticsService, readingSessionService, metadataLockService)
        val adminService = AdminService(jdbi)
        val annotationService = AnnotationService(jdbi)
        val metadataFetchService = createMetadataFetchService()
        val magicShelfService = MagicShelfService(jdbi, bookService)
        val passwordResetService = PasswordResetService(jdbi)
        val apiTokenService = ApiTokenService(jdbi)
        val exportService = ExportService(jdbi)
        val epubMetadataService = EpubMetadataService(jdbi, config.storage.coversPath)
        val comicService = ComicService()
        val goodreadsImportService = GoodreadsImportService(bookService)
        val backgroundTaskService = org.booktower.services.BackgroundTaskService()
        val seedService = SeedService(bookService, libraryService, config.storage.coversPath, config.storage.booksPath, backgroundTaskService)
        val userPermissionsService = UserPermissionsService(jdbi)
        val koboSyncService = KoboSyncService(jdbi, bookService, "http://localhost:9999", userSettingsService)
        val koreaderSyncService = org.booktower.services.KOReaderSyncService(jdbi, bookService)
        val opdsCredentialsService = OpdsCredentialsService(jdbi)
        val bookFilesService = BookFilesService(jdbi)
        val emailProviderService = EmailProviderService(jdbi)
        val scheduledTaskService = ScheduledTaskService(jdbi)
        val bulkCoverService = BulkCoverService(jdbi, pdfMetadataService, epubMetadataService)
        val comicMetadataService = ComicMetadataService(jdbi)
        val contentRestrictionsService = ContentRestrictionsService(jdbi, userSettingsService)
        val bookReviewService = BookReviewService(jdbi)
        val bookNotebookService = BookNotebookService(jdbi)
        val notificationService = NotificationService(jdbi)
        val audiobookMetaService = AudiobookMetaService(jdbi)
        val filterPresetService = FilterPresetService(jdbi)
        val telemetryService = TelemetryService(jdbi, userSettingsService)
        val communityRatingService = createCommunityRatingService(jdbi)
        val comicPageHashService = org.booktower.services.ComicPageHashService(jdbi, comicService)
        val duplicateDetectionService = DuplicateDetectionService(jdbi)
        val geoIpService =
            object : GeoIpService() {
                override fun lookup(ip: String): GeoLocation = GeoLocation(countryCode = "US", countryName = "United States", city = "Test City")
            }
        val auditService = org.booktower.services.AuditService(jdbi, geoIpService)
        val oidcService = if (oidcForceOnly) OidcService(OidcConfig(enabled = true, forceOnlyMode = true)) else null
        val journalService = JournalService(jdbi)

        // ── Handler objects ──────────────────────────────────────────────
        val authHandler =
            AuthHandler2(
                authService,
                userSettingsService,
                passwordResetService,
                EmailService(SmtpConfig("", 587, "", "", "", true)),
                "http://localhost:9999",
                registrationOpen,
                auditService,
                oidcService?.config?.forceOnlyMode ?: false,
            )
        val libraryHandler = LibraryHandler2(libraryService, backgroundTaskService, config.storage)
        val bookHandler = BookHandler2(bookService, readingSessionService)
        val bookmarkHandler = BookmarkHandler(bookmarkService)
        val calibreService = CalibreConversionService(java.io.File(config.storage.tempPath, "calibre-cache"))
        val fileHandler = FileHandler(bookService, pdfMetadataService, epubMetadataService, config.storage, calibreService = calibreService)
        val settingsHandler = UserSettingsHandler(userSettingsService)
        val adminHandler =
            AdminHandler(
                adminService,
                jwtService,
                authService,
                TestFixture.templateRenderer,
                passwordResetService,
                seedService,
                EmailService(SmtpConfig("", 587, "", "", "", true)),
                "http://localhost:9999",
                duplicateDetectionService,
                auditService,
                userPermissionsService,
                libraryAccessService,
                comicPageHashService,
            )
        val pageHandler =
            PageHandler(
                jwtService,
                authService,
                libraryService,
                bookService,
                bookmarkService,
                userSettingsService,
                analyticsService,
                annotationService,
                metadataFetchService,
                magicShelfService,
                TestFixture.templateRenderer,
                readingSessionService,
                null, // libraryWatchService
                null, // bookLinkService
                org.booktower.services.BookSharingService(jdbi, bookService),
                backgroundTaskService,
                org.booktower.services.LibraryStatsService(jdbi),
            )
        val backgroundTaskHandler = BackgroundTaskHandler(backgroundTaskService)
        val journalHandler = JournalHandler(journalService)
        val oidcHandler = oidcService?.let { org.booktower.handlers.OidcHandler(it, authService) }
        val koboSyncHandler = org.booktower.handlers.KoboSyncHandler(koboSyncService)
        val koreaderSyncHandler = org.booktower.handlers.KOReaderSyncHandler(koreaderSyncService)
        val opdsHandler = OpdsHandler(authService, libraryService, bookService, config.storage, apiTokenService, opdsCredentialsService)
        val apiTokenHandler = ApiTokenHandler(apiTokenService, jwtService)
        val exportHandler = ExportHandler(exportService, jwtService)
        val goodreadsImportHandler = GoodreadsImportHandler(goodreadsImportService, jwtService)
        val bulkBookHandler = BulkBookHandler(bookService)

        // ── Shared filters ───────────────────────────────────────────────
        val userExistsCheck = { userId: java.util.UUID -> authService.getUserById(userId) != null }
        val authFilter = jwtAuthFilter(jwtService, userExistsCheck)
        val filters =
            FilterSet(
                auth = authFilter,
                admin = authFilter.then(adminFilter()),
                authRateLimit = RateLimitFilter(maxRequests = 10, windowSeconds = 60),
                optionalAuth = org.booktower.filters.optionalAuthFilter(jwtService, userExistsCheck),
            )

        // ── Domain routers ───────────────────────────────────────────────
        val authRouter = AuthRouter(authHandler, filters)
        val oidcRouter = OidcRouter(oidcHandler)
        val pageRouter = PageRouter(filters, pageHandler, adminHandler, jwtService, TestFixture.templateRenderer, registrationOpen)
        val bookApiRouter =
            BookApiRouter(
                filters,
                bookHandler,
                bulkBookHandler,
                bookmarkHandler,
                fileHandler,
                bookService,
                comicService,
                config.storage,
                magicShelfService,
                null,
                null,
                journalHandler,
                null,
                null,
                null,
                bookFilesService,
                comicMetadataService,
                communityRatingService,
                bookReviewService,
                bookNotebookService,
                duplicateDetectionService,
                null, // bookLinkService
                org.booktower.services.BookSharingService(jdbi, bookService),
            )
        val libraryApiRouter = LibraryApiRouter(filters, libraryHandler, libraryService, null, null)
        val userApiRouter =
            UserApiRouter(
                filters,
                settingsHandler,
                bookService,
                userSettingsService,
                readingStatsService,
                hardcoverSyncService,
                opdsCredentialsService,
                contentRestrictionsService,
                filterPresetService,
                telemetryService,
                null,
                notificationService,
                backgroundTaskHandler,
                apiTokenHandler,
                exportHandler,
                goodreadsImportHandler,
            )
        val adminApiRouter =
            AdminApiRouter(
                filters,
                adminHandler,
                backgroundTaskHandler,
                WeblateHandler(WeblateConfig("", "", "", false)),
                emailProviderService,
                scheduledTaskService,
                bulkCoverService,
                telemetryService,
                null,
                null,
                null,
            )
        val metadataApiRouter =
            MetadataApiRouter(
                filters,
                metadataFetchService,
                bookService,
                metadataProposalService,
                metadataLockService,
                null,
            )
        val audiobookApiRouter =
            AudiobookApiRouter(
                filters,
                listeningSessionService,
                listeningStatsService,
                audiobookMetaService,
                config.storage,
            )
        val deviceSyncRouter =
            DeviceSyncRouter(
                filters,
                koboSyncHandler,
                koreaderSyncHandler,
                opdsHandler,
            )

        // ── Compose ──────────────────────────────────────────────────────
        val appHandler =
            AppHandler(
                fileHandler,
                config.storage,
                demoMode,
                authRouter,
                oidcRouter,
                pageRouter,
                bookApiRouter,
                libraryApiRouter,
                userApiRouter,
                adminApiRouter,
                metadataApiRouter,
                audiobookApiRouter,
                deviceSyncRouter,
            )
        return globalErrorFilter().then(DemoModeFilter(demoMode)).then(appHandler.routes())
    }

    protected open fun createMetadataFetchService(): MetadataFetchService = MetadataFetchService()

    protected open fun createCommunityRatingService(jdbi: org.jdbi.v3.core.Jdbi): org.booktower.services.CommunityRatingService = org.booktower.services.CommunityRatingService(jdbi)

    protected fun registerAndGetToken(prefix: String = "test"): String {
        val username = "${prefix}_${System.nanoTime()}"
        val response =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
            )
        return Json.mapper.readValue(response.bodyString(), LoginResponse::class.java).token
    }

    /**
     * Create a test user via AuthService.register() and return the user ID.
     * Uses the real registration flow including password hashing.
     */
    protected fun createTestUser(
        username: String = "testuser_${System.nanoTime()}",
        email: String = "$username@test.com",
        password: String = "password123",
    ): String {
        val jdbi = TestFixture.database.getJdbi()
        val jwtService = org.booktower.services.JwtService(TestFixture.config.security)
        val authService = AuthService(jdbi, jwtService)
        val result = authService.register(org.booktower.models.CreateUserRequest(username, email, password))
        return result.getOrThrow().user.id
    }

    /**
     * Promote a user to admin via AdminService.setAdmin().
     * Uses the real service method instead of raw SQL.
     */
    protected fun promoteToAdmin(userId: String) {
        val jdbi = TestFixture.database.getJdbi()
        val adminService = AdminService(jdbi)
        adminService.setAdmin(java.util.UUID.fromString(userId), true)
    }

    protected fun createLibrary(
        token: String,
        nameSuffix: String = "",
    ): String {
        val name = if (nameSuffix.isNotBlank()) nameSuffix else "Lib ${System.nanoTime()}"
        val response =
            app(
                Request(Method.POST, "/api/libraries")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"$name","path":"./data/test-${System.nanoTime()}"}"""),
            )
        return Json.mapper.readValue(response.bodyString(), LibraryDto::class.java).id
    }

    protected fun createBook(
        token: String,
        libId: String,
        title: String = "Book ${System.nanoTime()}",
    ): String {
        val response =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"$title","author":null,"description":null,"libraryId":"$libId"}"""),
            )
        return Json.mapper.readValue(response.bodyString(), BookDto::class.java).id
    }
}
