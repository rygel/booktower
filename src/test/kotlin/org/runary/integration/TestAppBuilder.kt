package org.runary.integration

import org.runary.TestFixture
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
import org.runary.handlers.BulkBookHandler
import org.runary.handlers.ExportHandler
import org.runary.handlers.FileHandler
import org.runary.handlers.FontHandler
import org.runary.handlers.GoodreadsImportHandler
import org.runary.handlers.JournalHandler
import org.runary.handlers.LibraryHandler2
import org.runary.handlers.OpdsHandler
import org.runary.handlers.BrowsePageHandler
import org.runary.handlers.DiscoveryPageHandler
import org.runary.handlers.PageHandler
import org.runary.handlers.SettingsPageHandler
import org.runary.handlers.StatsPageHandler
import org.runary.handlers.ReaderPreferencesHandler
import org.runary.handlers.UserSettingsHandler
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
import org.runary.services.AuditService
import org.runary.services.AuthService
import org.runary.services.BackgroundTaskService
import org.runary.services.BookDeliveryService
import org.runary.services.BookDropService
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
import org.runary.services.FontService
import org.runary.services.GeoIpService
import org.runary.services.GeoLocation
import org.runary.services.GoodreadsImportService
import org.runary.services.HardcoverSyncService
import org.runary.services.JournalService
import org.runary.services.JwtService
import org.runary.services.KOReaderSyncService
import org.runary.services.KoboSyncService
import org.runary.services.LibraryAccessService
import org.runary.services.LibraryHealthService
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
import org.runary.services.ReaderPreferencesService
import org.runary.services.ReadingSessionService
import org.runary.services.ReadingStatsService
import org.runary.services.RecommendationService
import org.runary.services.ScheduledTaskService
import org.runary.services.SeedService
import org.runary.services.TelemetryService
import org.runary.services.UserPermissionsService
import org.runary.services.UserSettingsService
import org.runary.weblate.WeblateHandler
import org.http4k.core.HttpHandler
import org.http4k.core.then

/**
 * Standalone builder for the test application.
 *
 * Accepts optional service overrides so individual integration tests can
 * inject custom services while getting sensible defaults for everything else.
 */
@Suppress("LongMethod", "LongParameterList")
fun buildTestApp(
    registrationOpen: Boolean = true,
    demoMode: Boolean = false,
    oidcForceOnly: Boolean = false,
    // Core services that tests commonly override
    authService: AuthService? = null,
    libraryService: LibraryService? = null,
    bookService: BookService? = null,
    jwtService: JwtService? = null,
    metadataFetchService: MetadataFetchService? = null,
    // Optional service overrides
    auditService: AuditService? = null,
    authorMetadataService: org.runary.services.AuthorMetadataService? = null,
    backgroundTaskService: BackgroundTaskService? = null,
    bookDeliveryService: BookDeliveryService? = null,
    bookDropService: BookDropService? = null,
    fontService: FontService? = null,
    readerPreferencesService: ReaderPreferencesService? = null,
    journalService: JournalService? = null,
    koboSyncService: KoboSyncService? = null,
    koReaderSyncService: KOReaderSyncService? = null,
    libraryHealthService: LibraryHealthService? = null,
    recommendationService: RecommendationService? = null,
): HttpHandler {
    val config = TestFixture.config
    val jdbi = TestFixture.database.getJdbi()

    // ── Services (use overrides or create defaults) ──────────────────────
    val jwt = jwtService ?: JwtService(config.security)
    val auth = authService ?: AuthService(jdbi, jwt)
    val pdfMetadataService = PdfMetadataService(jdbi, config.storage.coversPath)
    val libraryAccessService = LibraryAccessService(jdbi)
    val metadataLockService = MetadataLockService(jdbi)
    val readingStatsService = ReadingStatsService(jdbi)
    val listeningSessionService = ListeningSessionService(jdbi)
    val listeningStatsService = ListeningStatsService(jdbi)
    val metadataProposalService = MetadataProposalService(jdbi)
    val lib = libraryService ?: LibraryService(jdbi, pdfMetadataService, libraryAccessService)
    val bookmarkService = BookmarkService(jdbi)
    val userSettingsService = UserSettingsService(jdbi)
    val hardcoverSyncService = HardcoverSyncService(jdbi, userSettingsService)
    val analyticsService = AnalyticsService(jdbi, userSettingsService)
    val readingSessionService = ReadingSessionService(jdbi)
    val book = bookService ?: BookService(jdbi, analyticsService, readingSessionService, metadataLockService)
    val adminService = AdminService(jdbi)
    val annotationService = AnnotationService(jdbi)
    val metaFetch = metadataFetchService ?: MetadataFetchService()
    val magicShelfService = MagicShelfService(jdbi, book)
    val passwordResetService = PasswordResetService(jdbi)
    val apiTokenService = ApiTokenService(jdbi)
    val exportService = ExportService(jdbi)
    val epubMetadataService = EpubMetadataService(jdbi, config.storage.coversPath)
    val comicService = ComicService()
    val goodreadsImportService = GoodreadsImportService(book)
    val bgTaskService = backgroundTaskService ?: org.runary.services.BackgroundTaskService()
    val seedService = SeedService(book, lib, config.storage.coversPath, config.storage.booksPath, bgTaskService)
    val userPermissionsService = UserPermissionsService(jdbi)
    val koboSync = koboSyncService ?: KoboSyncService(jdbi, book, "http://localhost:9999", userSettingsService)
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
    val communityRatingService = org.runary.services.CommunityRatingService(jdbi)
    val comicPageHashService = org.runary.services.ComicPageHashService(jdbi, comicService)
    val duplicateDetectionService = DuplicateDetectionService(jdbi)
    val geoIpService =
        object : GeoIpService() {
            override fun lookup(ip: String): GeoLocation = GeoLocation(countryCode = "US", countryName = "United States", city = "Test City")
        }
    val audit = auditService ?: org.runary.services.AuditService(jdbi, geoIpService)
    val oidcService = if (oidcForceOnly) OidcService(OidcConfig(enabled = true, forceOnlyMode = true)) else null
    val journal = journalService ?: JournalService(jdbi)

    // ── Handler objects ──────────────────────────────────────────────────
    val authHandler =
        AuthHandler2(
            auth,
            userSettingsService,
            passwordResetService,
            EmailService(SmtpConfig("", 587, "", "", "", true)),
            "http://localhost:9999",
            registrationOpen,
            audit,
            oidcService?.config?.forceOnlyMode ?: false,
        )
    val libraryHandler = LibraryHandler2(lib, bgTaskService, config.storage)
    val bookHandler = BookHandler2(book, readingSessionService)
    val bookmarkHandler = BookmarkHandler(bookmarkService)
    val calibreService = CalibreConversionService(java.io.File(config.storage.tempPath, "calibre-cache"))
    val fileHandler = FileHandler(book, pdfMetadataService, epubMetadataService, config.storage, calibreService = calibreService)
    val settingsHandler = UserSettingsHandler(userSettingsService)
    val adminHandler =
        AdminHandler(
            adminService,
            jwt,
            auth,
            TestFixture.templateRenderer,
            passwordResetService,
            seedService,
            EmailService(SmtpConfig("", 587, "", "", "", true)),
            "http://localhost:9999",
            duplicateDetectionService,
            audit,
            userPermissionsService,
            libraryAccessService,
            comicPageHashService,
        )
    val handlers =
        TestPageHandlers.create(
            jwt, auth, lib, book, bookmarkService, userSettingsService, analyticsService,
            annotationService, metaFetch, magicShelfService, TestFixture.templateRenderer,
            readingSessionService = readingSessionService,
            backgroundTaskService = bgTaskService,
            libraryStatsService = org.runary.services.LibraryStatsService(jdbi),
        )
    val pageHandler = handlers.pageHandler
    val browsePageHandler = handlers.browsePageHandler
    val statsPageHandler = handlers.statsPageHandler
    val settingsPageHandler = handlers.settingsPageHandler
    val discoveryPageHandler = handlers.discoveryPageHandler
    val bgTaskHandler = BackgroundTaskHandler(bgTaskService)
    val journalHandler = JournalHandler(journal)
    val oidcHandler = oidcService?.let { org.runary.handlers.OidcHandler(it, auth) }
    val koboSyncHandler = org.runary.handlers.KoboSyncHandler(koboSync)
    val koReaderSyncHandler = koReaderSyncService?.let { org.runary.handlers.KOReaderSyncHandler(it) }
    val fontHandler = fontService?.let { FontHandler(it) }
    val readerPrefsHandler = readerPreferencesService?.let { ReaderPreferencesHandler(it) }
    val opdsHandler = OpdsHandler(auth, lib, book, config.storage, apiTokenService, opdsCredentialsService)
    val apiTokenHandler = ApiTokenHandler(apiTokenService, jwt)
    val exportHandler = ExportHandler(exportService, jwt)
    val goodreadsImportHandler = GoodreadsImportHandler(goodreadsImportService, jwt)
    val bulkBookHandler = BulkBookHandler(book)

    // ── Shared filters ───────────────────────────────────────────────────
    val userExistsCheck = { userId: java.util.UUID -> auth.getUserById(userId) != null }
    val authFilter = jwtAuthFilter(jwt, userExistsCheck)
    val filters =
        FilterSet(
            auth = authFilter,
            admin = authFilter.then(adminFilter()),
            authRateLimit = RateLimitFilter(maxRequests = 10, windowSeconds = 60),
            optionalAuth = org.runary.filters.optionalAuthFilter(jwt, userExistsCheck),
        )

    // ── Domain routers ───────────────────────────────────────────────────
    val authRouter = AuthRouter(authHandler, filters)
    val oidcRouter = OidcRouter(oidcHandler)
    val pageRouter = PageRouter(filters, pageHandler, adminHandler, jwt, TestFixture.templateRenderer, registrationOpen, null, browsePageHandler, statsPageHandler, settingsPageHandler, discoveryPageHandler)
    val bookApiRouter =
        BookApiRouter(
            filters,
            bookHandler,
            bulkBookHandler,
            bookmarkHandler,
            fileHandler,
            book,
            comicService,
            config.storage,
            magicShelfService,
            fontHandler,
            readerPrefsHandler,
            journalHandler,
            null,
            bookDeliveryService,
            recommendationService,
            bookFilesService,
            comicMetadataService,
            communityRatingService,
            bookReviewService,
            bookNotebookService,
            duplicateDetectionService,
            null, // bookLinkService
        )
    val libraryApiRouter = LibraryApiRouter(filters, libraryHandler, lib, libraryHealthService, bookDropService)
    val userApiRouter =
        UserApiRouter(
            filters,
            settingsHandler,
            book,
            userSettingsService,
            readingStatsService,
            hardcoverSyncService,
            opdsCredentialsService,
            contentRestrictionsService,
            filterPresetService,
            telemetryService,
            bookDeliveryService,
            bgTaskHandler,
            apiTokenHandler,
            exportHandler,
            goodreadsImportHandler,
        )
    val adminApiRouter =
        AdminApiRouter(
            filters,
            adminHandler,
            bgTaskHandler,
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
            metaFetch,
            book,
            metadataProposalService,
            metadataLockService,
            authorMetadataService,
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
            koReaderSyncHandler,
            opdsHandler,
        )

    // ── Compose ──────────────────────────────────────────────────────────
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
