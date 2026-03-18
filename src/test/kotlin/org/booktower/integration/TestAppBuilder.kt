package org.booktower.integration

import org.booktower.TestFixture
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
import org.booktower.handlers.FontHandler
import org.booktower.handlers.GoodreadsImportHandler
import org.booktower.handlers.JournalHandler
import org.booktower.handlers.LibraryHandler2
import org.booktower.handlers.OpdsHandler
import org.booktower.handlers.PageHandler
import org.booktower.handlers.ReaderPreferencesHandler
import org.booktower.handlers.UserSettingsHandler
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
import org.booktower.services.AuditService
import org.booktower.services.AuthService
import org.booktower.services.BackgroundTaskService
import org.booktower.services.BookDeliveryService
import org.booktower.services.BookDropService
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
import org.booktower.services.FontService
import org.booktower.services.GeoIpService
import org.booktower.services.GeoLocation
import org.booktower.services.GoodreadsImportService
import org.booktower.services.HardcoverSyncService
import org.booktower.services.JournalService
import org.booktower.services.JwtService
import org.booktower.services.KOReaderSyncService
import org.booktower.services.KoboSyncService
import org.booktower.services.LibraryAccessService
import org.booktower.services.LibraryHealthService
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
import org.booktower.services.ReaderPreferencesService
import org.booktower.services.ReadingSessionService
import org.booktower.services.ReadingStatsService
import org.booktower.services.RecommendationService
import org.booktower.services.ScheduledTaskService
import org.booktower.services.SeedService
import org.booktower.services.TelemetryService
import org.booktower.services.UserPermissionsService
import org.booktower.services.UserSettingsService
import org.booktower.weblate.WeblateHandler
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
    authorMetadataService: org.booktower.services.AuthorMetadataService? = null,
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
    val seedService = SeedService(book, lib, config.storage.coversPath, config.storage.booksPath)
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
    val communityRatingService = org.booktower.services.CommunityRatingService(jdbi)
    val comicPageHashService = org.booktower.services.ComicPageHashService(jdbi, comicService)
    val duplicateDetectionService = DuplicateDetectionService(jdbi)
    val geoIpService =
        object : GeoIpService() {
            override fun lookup(ip: String): GeoLocation = GeoLocation(countryCode = "US", countryName = "United States", city = "Test City")
        }
    val audit = auditService ?: org.booktower.services.AuditService(jdbi, geoIpService)
    val oidcService = if (oidcForceOnly) OidcService(OidcConfig(enabled = true, forceOnlyMode = true)) else null
    val journal = journalService ?: JournalService(jdbi)
    val bgTaskService = backgroundTaskService ?: org.booktower.services.BackgroundTaskService()

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
    val pageHandler =
        PageHandler(
            jwt,
            auth,
            lib,
            book,
            bookmarkService,
            userSettingsService,
            analyticsService,
            annotationService,
            metaFetch,
            magicShelfService,
            TestFixture.templateRenderer,
            readingSessionService,
            null,
        )
    val bgTaskHandler = BackgroundTaskHandler(bgTaskService)
    val journalHandler = JournalHandler(journal)
    val oidcHandler = oidcService?.let { org.booktower.handlers.OidcHandler(it, auth) }
    val koboSyncHandler = org.booktower.handlers.KoboSyncHandler(koboSync)
    val koReaderSyncHandler = koReaderSyncService?.let { org.booktower.handlers.KOReaderSyncHandler(it) }
    val fontHandler = fontService?.let { FontHandler(it) }
    val readerPrefsHandler = readerPreferencesService?.let { ReaderPreferencesHandler(it) }
    val opdsHandler = OpdsHandler(auth, lib, book, config.storage, apiTokenService, opdsCredentialsService)
    val apiTokenHandler = ApiTokenHandler(apiTokenService, jwt)
    val exportHandler = ExportHandler(exportService, jwt)
    val goodreadsImportHandler = GoodreadsImportHandler(goodreadsImportService, jwt)
    val bulkBookHandler = BulkBookHandler(book)

    // ── Shared filters ───────────────────────────────────────────────────
    val authFilter = jwtAuthFilter(jwt) { userId: java.util.UUID -> auth.getUserById(userId) != null }
    val filters =
        FilterSet(
            auth = authFilter,
            admin = authFilter.then(adminFilter()),
            authRateLimit = RateLimitFilter(maxRequests = 10, windowSeconds = 60),
        )

    // ── Domain routers ───────────────────────────────────────────────────
    val authRouter = AuthRouter(authHandler, filters)
    val oidcRouter = OidcRouter(oidcHandler)
    val pageRouter = PageRouter(filters, pageHandler, adminHandler, jwt, TestFixture.templateRenderer, registrationOpen)
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
            notificationService,
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
