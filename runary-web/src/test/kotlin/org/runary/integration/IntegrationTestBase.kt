package org.runary.integration

import org.http4k.client.JavaHttpClient
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Uri
import org.http4k.core.then
import org.junit.jupiter.api.BeforeEach
import org.runary.TestFixture
import org.runary.config.Json
import org.runary.config.OidcConfig
import org.runary.config.SmtpConfig
import org.runary.config.WeblateConfig
import org.runary.filters.DemoModeFilter
import org.runary.filters.RateLimitFilter
import org.runary.filters.adminFilter
import org.runary.filters.globalErrorFilter
import org.runary.filters.jwtAuthFilter
import org.runary.handlers.AdminHandler
import org.runary.handlers.ApiTokenHandler
import org.runary.handlers.AppHandler
import org.runary.handlers.AuthHandler2
import org.runary.handlers.BackgroundTaskHandler
import org.runary.handlers.BookHandler2
import org.runary.handlers.BookmarkHandler
import org.runary.handlers.BrowsePageHandler
import org.runary.handlers.BulkBookHandler
import org.runary.handlers.DiscoveryPageHandler
import org.runary.handlers.ExportHandler
import org.runary.handlers.FileHandler
import org.runary.handlers.GoodreadsImportHandler
import org.runary.handlers.JournalHandler
import org.runary.handlers.LibraryHandler2
import org.runary.handlers.OpdsHandler
import org.runary.handlers.PageHandler
import org.runary.handlers.SettingsPageHandler
import org.runary.handlers.StatsPageHandler
import org.runary.handlers.UserSettingsHandler
import org.runary.models.BookDto
import org.runary.models.LibraryDto
import org.runary.models.LoginResponse
import org.runary.routers.AdminApiRouter
import org.runary.routers.AudiobookApiRouter
import org.runary.routers.AuthRouter
import org.runary.routers.BookApiRouter
import org.runary.routers.DeviceSyncRouter
import org.runary.routers.FilterSet
import org.runary.routers.LibraryApiRouter
import org.runary.routers.MetadataApiRouter
import org.runary.routers.OidcRouter
import org.runary.routers.PageRouter
import org.runary.routers.UserApiRouter
import org.runary.services.AdminService
import org.runary.services.AnalyticsService
import org.runary.services.AnnotationService
import org.runary.services.ApiTokenService
import org.runary.services.AudiobookMetaService
import org.runary.services.AuthService
import org.runary.services.BookFilesService
import org.runary.services.BookNotebookService
import org.runary.services.BookReviewService
import org.runary.services.BookService
import org.runary.services.BookmarkService
import org.runary.services.BulkCoverService
import org.runary.services.CalibreConversionService
import org.runary.services.ComicMetadataService
import org.runary.services.ComicService
import org.runary.services.ContentRestrictionsService
import org.runary.services.DuplicateDetectionService
import org.runary.services.EmailProviderService
import org.runary.services.EmailService
import org.runary.services.EpubMetadataService
import org.runary.services.ExportService
import org.runary.services.FilterPresetService
import org.runary.services.GeoIpService
import org.runary.services.GeoLocation
import org.runary.services.GoodreadsImportService
import org.runary.services.HardcoverSyncService
import org.runary.services.JournalService
import org.runary.services.JwtService
import org.runary.services.KoboSyncService
import org.runary.services.LibraryAccessService
import org.runary.services.LibraryService
import org.runary.services.ListeningSessionService
import org.runary.services.ListeningStatsService
import org.runary.services.MagicShelfService
import org.runary.services.MetadataFetchService
import org.runary.services.MetadataLockService
import org.runary.services.MetadataProposalService
import org.runary.services.NotificationService
import org.runary.services.OidcService
import org.runary.services.OpdsCredentialsService
import org.runary.services.PasswordResetService
import org.runary.services.PdfMetadataService
import org.runary.services.ReadingSessionService
import org.runary.services.ReadingStatsService
import org.runary.services.ScheduledTaskService
import org.runary.services.SeedService
import org.runary.services.TelemetryService
import org.runary.services.UserPermissionsService
import org.runary.services.UserSettingsService
import org.runary.weblate.WeblateHandler

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
        val backgroundTaskService = org.runary.services.BackgroundTaskService()
        val seedService = SeedService(bookService, libraryService, config.storage.coversPath, config.storage.booksPath, backgroundTaskService)
        val userPermissionsService = UserPermissionsService(jdbi)
        val koboSyncService = KoboSyncService(jdbi, bookService, "http://localhost:9999", userSettingsService)
        val koreaderSyncService = org.runary.services.KOReaderSyncService(jdbi, bookService)
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
        val comicPageHashService = org.runary.services.ComicPageHashService(jdbi, comicService)
        val duplicateDetectionService = DuplicateDetectionService(jdbi)
        val geoIpService =
            object : GeoIpService() {
                override fun lookup(ip: String): GeoLocation = GeoLocation(countryCode = "US", countryName = "United States", city = "Test City")
            }
        val auditService = org.runary.services.AuditService(jdbi, geoIpService)
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
        val handlers =
            TestPageHandlers.create(
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
                readingSessionService = readingSessionService,
                bookSharingService = org.runary.services.BookSharingService(jdbi, bookService),
                backgroundTaskService = backgroundTaskService,
                webhookService = org.runary.services.WebhookService(jdbi),
                libraryStatsService = org.runary.services.LibraryStatsService(jdbi),
                readingTimelineService = org.runary.services.ReadingTimelineService(jdbi),
                readingSpeedService = org.runary.services.ReadingSpeedService(jdbi),
                libraryHealthService = org.runary.services.LibraryHealthService(jdbi),
                readingListService = org.runary.services.ReadingListService(jdbi),
                wishlistService = org.runary.services.WishlistService(jdbi),
                collectionService = org.runary.services.CollectionService(jdbi),
                koboSyncService = koboSyncService,
                koreaderSyncService = koreaderSyncService,
                filterPresetService = filterPresetService,
                scheduledTaskService = scheduledTaskService,
                opdsCredentialsService = opdsCredentialsService,
                contentRestrictionsService = contentRestrictionsService,
                hardcoverSyncService = hardcoverSyncService,
                metadataProposalService = metadataProposalService,
            )
        val pageHandler = handlers.pageHandler
        val browsePageHandler = handlers.browsePageHandler
        val statsPageHandler = handlers.statsPageHandler
        val settingsPageHandler = handlers.settingsPageHandler
        val discoveryPageHandler = handlers.discoveryPageHandler
        val backgroundTaskHandler = BackgroundTaskHandler(backgroundTaskService)
        val journalHandler = JournalHandler(journalService)
        val oidcHandler = oidcService?.let { org.runary.handlers.OidcHandler(it, authService) }
        val koboSyncHandler = org.runary.handlers.KoboSyncHandler(koboSyncService)
        val koreaderSyncHandler = org.runary.handlers.KOReaderSyncHandler(koreaderSyncService)
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
                optionalAuth = org.runary.filters.optionalAuthFilter(jwtService, userExistsCheck),
            )

        // ── Domain routers ───────────────────────────────────────────────
        val authRouter = AuthRouter(authHandler, filters)
        val oidcRouter = OidcRouter(oidcHandler)
        val pageRouter = PageRouter(filters, pageHandler, adminHandler, jwtService, TestFixture.templateRenderer, registrationOpen, null, null, browsePageHandler, statsPageHandler, settingsPageHandler, discoveryPageHandler)
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
                org.runary.services.BookSharingService(jdbi, bookService),
            )
        val libraryApiRouter = LibraryApiRouter(filters, libraryHandler, libraryService, null, null)
        val collectionApiHandler = org.runary.handlers.CollectionApiHandler(org.runary.services.CollectionService(jdbi))
        val customFieldService = org.runary.services.CustomFieldService(jdbi)
        val customFieldApiHandler = org.runary.handlers.CustomFieldApiHandler(customFieldService)
        val webhookApiHandler = org.runary.handlers.WebhookApiHandler(org.runary.services.WebhookService(jdbi))
        val notificationApiHandler = org.runary.handlers.NotificationApiHandler(notificationService)
        val readingListApiHandler = org.runary.handlers.ReadingListApiHandler(org.runary.services.ReadingListService(jdbi))
        val wishlistApiHandler = org.runary.handlers.WishlistApiHandler(org.runary.services.WishlistService(jdbi))
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
                backgroundTaskHandler,
                apiTokenHandler,
                exportHandler,
                goodreadsImportHandler,
                collectionApiHandler = collectionApiHandler,
                webhookApiHandler = webhookApiHandler,
                annotationExportService = org.runary.services.AnnotationExportService(jdbi),
                customFieldApiHandler = customFieldApiHandler,
                publicProfileService = org.runary.services.PublicProfileService(jdbi, userSettingsService),
                bookConditionService = org.runary.services.BookConditionService(customFieldService),
                readingListApiHandler = readingListApiHandler,
                wishlistApiHandler = wishlistApiHandler,
                annotationService = annotationService,
                notificationApiHandler = notificationApiHandler,
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
                null, // bulkMetadataRefreshService
                org.runary.services.BackupService(jdbi), // backupService
                null, // batchImportService
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

    protected open fun createCommunityRatingService(jdbi: org.jdbi.v3.core.Jdbi): org.runary.services.CommunityRatingService = org.runary.services.CommunityRatingService(jdbi)

    protected fun registerAndGetToken(prefix: String = "test"): String {
        val username = "${prefix}_${System.nanoTime()}"
        val pw = org.runary.TestPasswords.DEFAULT
        val response =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","email":"$username@test.com","password":"$pw"}"""),
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
        password: String = org.runary.TestPasswords.DEFAULT,
    ): String {
        val jdbi = TestFixture.database.getJdbi()
        val jwtService = org.runary.services.JwtService(TestFixture.config.security)
        val authService = AuthService(jdbi, jwtService)
        val result = authService.register(org.runary.models.CreateUserRequest(username, email, password))
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
