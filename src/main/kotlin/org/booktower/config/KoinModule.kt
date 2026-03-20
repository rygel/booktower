package org.booktower.config

import org.booktower.filters.RateLimitFilter
import org.booktower.filters.adminFilter
import org.booktower.filters.jwtAuthFilter
import org.booktower.filters.optionalAuthFilter
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
import org.booktower.handlers.KOReaderSyncHandler
import org.booktower.handlers.KoboSyncHandler
import org.booktower.handlers.LibraryHandler2
import org.booktower.handlers.OidcHandler
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
import org.booktower.services.AlternativeCoverService
import org.booktower.services.AnalyticsService
import org.booktower.services.AnnotationService
import org.booktower.services.ApiTokenService
import org.booktower.services.AudiobookMetaService
import org.booktower.services.AuditService
import org.booktower.services.AuthService
import org.booktower.services.AuthorMetadataService
import org.booktower.services.BackgroundTaskService
import org.booktower.services.BookDeliveryService
import org.booktower.services.BookDropService
import org.booktower.services.BookFilesService
import org.booktower.services.BookLinkService
import org.booktower.services.BookNotebookService
import org.booktower.services.BookReviewService
import org.booktower.services.BookService
import org.booktower.services.BookSharingService
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
import org.booktower.services.GoodreadsImportService
import org.booktower.services.HardcoverSyncService
import org.booktower.services.JournalService
import org.booktower.services.JwtService
import org.booktower.services.KOReaderSyncService
import org.booktower.services.KoboSyncService
import org.booktower.services.LibraryAccessService
import org.booktower.services.LibraryHealthService
import org.booktower.services.LibraryService
import org.booktower.services.LibraryWatchService
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
import org.booktower.services.ScanScheduleService
import org.booktower.services.ScheduledTaskService
import org.booktower.services.SeedService
import org.booktower.services.TelemetryService
import org.booktower.services.UserPermissionsService
import org.booktower.services.UserSettingsService
import org.booktower.weblate.WeblateHandler
import org.http4k.core.then
import org.koin.dsl.module

val appModule =
    module {
        single { AppConfig.load() }

        single {
            val config = get<AppConfig>()
            config.storage.ensureDirectories()
            Database.connect(config.database)
        }

        single { TemplateRenderer() }

        // ── Services ─────────────────────────────────────────────────────────
        single { JwtService(get<AppConfig>().security) }
        single { AuthService(get<Database>().getJdbi(), get()) }
        single {
            LibraryService(
                get<Database>().getJdbi(),
                get<PdfMetadataService>(),
                get<LibraryAccessService>(),
                get(),
                get(),
            )
        }
        single { BookService(get<Database>().getJdbi(), get(), get()) }
        single { BookmarkService(get<Database>().getJdbi()) }
        single { UserSettingsService(get<Database>().getJdbi()) }
        single { AnalyticsService(get<Database>().getJdbi(), get()) }
        single { ReadingSessionService(get<Database>().getJdbi()) }
        single { PdfMetadataService(get<Database>().getJdbi(), get<AppConfig>().storage.coversPath) }
        single { EpubMetadataService(get<Database>().getJdbi(), get<AppConfig>().storage.coversPath) }
        single { AdminService(get<Database>().getJdbi()) }
        single { AnnotationService(get<Database>().getJdbi()) }
        single { MetadataFetchService(get<AppConfig>().metadata) }
        single { MagicShelfService(get<Database>().getJdbi(), get()) }
        single { PasswordResetService(get<Database>().getJdbi()) }
        single { EmailService(get<AppConfig>().smtp) }
        single { ApiTokenService(get<Database>().getJdbi()) }
        single { ExportService(get<Database>().getJdbi()) }
        single { GoodreadsImportService(get()) }
        single { ComicService() }
        single {
            SeedService(
                get(),
                get(),
                get<AppConfig>().storage.coversPath,
                get<AppConfig>().storage.booksPath,
                get(),
                get<BookmarkService>(),
                get<ReadingSessionService>(),
                get<MagicShelfService>(),
                get<UserSettingsService>(),
                get<Database>().getJdbi(),
            )
        }
        single { ScanScheduleService(get<Database>().getJdbi(), get(), get<AppConfig>().autoScanMinutes) }
        single { LibraryWatchService(get<Database>().getJdbi(), get()) }
        single { DuplicateDetectionService(get<Database>().getJdbi()) }
        single { GeoIpService() }
        single { AuditService(get<Database>().getJdbi(), get<GeoIpService>()) }
        single { BackgroundTaskService() }
        single { JournalService(get<Database>().getJdbi()) }
        single { LibraryHealthService(get<Database>().getJdbi()) }
        single { AuthorMetadataService() }
        single { RecommendationService(get<Database>().getJdbi()) }
        single { AlternativeCoverService() }
        single { BookDeliveryService(get<Database>().getJdbi(), get(), get(), get<AppConfig>().storage.booksPath) }
        single { BookDropService(get(), "${get<AppConfig>().storage.booksPath}/bookdrop") }
        single { OidcService(get<AppConfig>().oidc) }
        single { KoboSyncService(get<Database>().getJdbi(), get(), get<AppConfig>().baseUrl, get()) }
        single { KOReaderSyncService(get<Database>().getJdbi(), get()) }
        single { org.booktower.services.FontService(get<Database>().getJdbi(), "${get<AppConfig>().storage.booksPath}/fonts") }
        single { ReaderPreferencesService(get()) }
        single { org.booktower.services.FtsService(get<Database>().getJdbi(), get<AppConfig>().fts.enabled) }
        single { org.booktower.services.FtsIndexWorker(get(), get(), get<AppConfig>().fts.throttleMs) }
        single { org.booktower.services.ComicPageHashService(get<Database>().getJdbi(), get()) }
        single { org.booktower.services.ComicPageHashWorker(get(), get()) }
        single { UserPermissionsService(get<Database>().getJdbi()) }
        single { LibraryAccessService(get<Database>().getJdbi()) }
        single { MetadataLockService(get<Database>().getJdbi()) }
        single { ReadingStatsService(get<Database>().getJdbi()) }
        single { ListeningSessionService(get<Database>().getJdbi()) }
        single { ListeningStatsService(get<Database>().getJdbi()) }
        single { MetadataProposalService(get<Database>().getJdbi()) }
        single { HardcoverSyncService(get<Database>().getJdbi(), get()) }
        single { OpdsCredentialsService(get<Database>().getJdbi()) }
        single { BookFilesService(get<Database>().getJdbi()) }
        single { EmailProviderService(get<Database>().getJdbi()) }
        single { ScheduledTaskService(get<Database>().getJdbi()) }
        single { BulkCoverService(get<Database>().getJdbi(), get(), get()) }
        single { ComicMetadataService(get<Database>().getJdbi()) }
        single { ContentRestrictionsService(get<Database>().getJdbi(), get()) }
        single { BookReviewService(get<Database>().getJdbi()) }
        single { BookNotebookService(get<Database>().getJdbi()) }
        single { NotificationService(get<Database>().getJdbi()) }
        single { AudiobookMetaService(get<Database>().getJdbi()) }
        single { FilterPresetService(get<Database>().getJdbi()) }
        single { TelemetryService(get<Database>().getJdbi(), get()) }
        single { org.booktower.services.CommunityRatingService(get<Database>().getJdbi()) }
        single { BookLinkService(get<Database>().getJdbi(), get<BookService>()) }
        single { BookSharingService(get<Database>().getJdbi(), get<BookService>()) }
        single { WeblateHandler(get<AppConfig>().weblate) }
        single { org.booktower.services.CollectionService(get<Database>().getJdbi()) }

        // ── Handler objects ──────────────────────────────────────────────────
        single {
            AuthHandler2(
                get(),
                get(),
                get(),
                get(),
                get<AppConfig>().baseUrl,
                get<AppConfig>().registrationOpen,
                get(),
                get<OidcService>().config.forceOnlyMode,
            )
        }
        single { LibraryHandler2(get(), get(), get<AppConfig>().storage) }
        single { BookHandler2(get(), get()) }
        single { BookmarkHandler(get()) }
        single {
            val calibreService = CalibreConversionService(java.io.File(get<AppConfig>().storage.tempPath, "calibre-cache"))
            FileHandler(get(), get(), get(), get<AppConfig>().storage, calibreService = calibreService)
        }
        single { UserSettingsHandler(get()) }
        single {
            AdminHandler(
                get(), // adminService
                get<JwtService>(),
                get<AuthService>(),
                get(), // templateRenderer
                get(), // passwordResetService
                get(), // seedService
                get(), // emailService
                get<AppConfig>().baseUrl,
                get(), // duplicateDetectionService
                get(), // auditService
                get(), // userPermissionsService
                get(), // libraryAccessService
                get<org.booktower.services.ComicPageHashService>(),
            )
        }
        single {
            PageHandler(
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                getOrNull(),
                getOrNull(),
                get<BackgroundTaskService>(),
            )
        }
        single { BackgroundTaskHandler(get(), get()) }
        single { JournalHandler(get()) }
        single { OidcHandler(get(), get()) }
        single { KoboSyncHandler(get()) }
        single { KOReaderSyncHandler(get()) }
        single { FontHandler(get()) }
        single { ReaderPreferencesHandler(get()) }
        single {
            OpdsHandler(get(), get(), get(), get<AppConfig>().storage, get(), get())
        }
        single { ApiTokenHandler(get(), get()) }
        single { ExportHandler(get(), get()) }
        single { GoodreadsImportHandler(get(), get()) }
        single { BulkBookHandler(get()) }

        // ── Filters ──────────────────────────────────────────────────────────
        single {
            val jwtService = get<JwtService>()
            val authService = get<AuthService>()
            val userExistsCache =
                com.github.benmanes.caffeine.cache.Caffeine
                    .newBuilder()
                    .maximumSize(1_000)
                    .expireAfterWrite(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build<java.util.UUID, Boolean>()
            val userExistsCheck = { userId: java.util.UUID ->
                userExistsCache.get(userId) { authService.getUserById(it) != null }!!
            }
            val authFilter = jwtAuthFilter(jwtService, userExistsCheck)
            FilterSet(
                auth = authFilter,
                admin = authFilter.then(adminFilter()),
                authRateLimit = RateLimitFilter(maxRequests = 10, windowSeconds = 60),
                optionalAuth = optionalAuthFilter(jwtService, userExistsCheck),
            )
        }

        // ── Domain routers ───────────────────────────────────────────────────
        single { AuthRouter(get<AuthHandler2>(), get<FilterSet>()) }
        single { OidcRouter(get<OidcHandler>()) }
        single {
            PageRouter(
                get<FilterSet>(),
                get<PageHandler>(),
                get<AdminHandler>(),
                get<JwtService>(),
                get<TemplateRenderer>(),
                get<AppConfig>().registrationOpen,
                get<UserSettingsService>(),
            )
        }
        single {
            BookApiRouter(
                filters = get<FilterSet>(),
                bookHandler = get<BookHandler2>(),
                bulkBookHandler = get<BulkBookHandler>(),
                bookmarkHandler = get<BookmarkHandler>(),
                fileHandler = get<FileHandler>(),
                bookService = get<BookService>(),
                comicService = get<ComicService>(),
                storageConfig = get<AppConfig>().storage,
                magicShelfService = get<MagicShelfService>(),
                fontHandler = get<FontHandler>(),
                readerPreferencesHandler = get<ReaderPreferencesHandler>(),
                journalHandler = get<JournalHandler>(),
                alternativeCoverService = get<AlternativeCoverService>(),
                bookDeliveryService = get<BookDeliveryService>(),
                recommendationService = get<RecommendationService>(),
                bookFilesService = get<BookFilesService>(),
                comicMetadataService = get<ComicMetadataService>(),
                communityRatingService = get<org.booktower.services.CommunityRatingService>(),
                bookReviewService = get<BookReviewService>(),
                bookNotebookService = get<BookNotebookService>(),
                duplicateDetectionService = get<DuplicateDetectionService>(),
                bookLinkService = get<BookLinkService>(),
                bookSharingService = get<BookSharingService>(),
            )
        }
        single {
            LibraryApiRouter(
                get<FilterSet>(),
                get<LibraryHandler2>(),
                get<LibraryService>(),
                get<LibraryHealthService>(),
                get<BookDropService>(),
            )
        }
        single {
            UserApiRouter(
                filters = get<FilterSet>(),
                settingsHandler = get<UserSettingsHandler>(),
                bookService = get<BookService>(),
                userSettingsService = get<UserSettingsService>(),
                readingStatsService = get<ReadingStatsService>(),
                hardcoverSyncService = get<HardcoverSyncService>(),
                opdsCredentialsService = get<OpdsCredentialsService>(),
                contentRestrictionsService = get<ContentRestrictionsService>(),
                filterPresetService = get<FilterPresetService>(),
                telemetryService = get<TelemetryService>(),
                bookDeliveryService = get<BookDeliveryService>(),
                notificationService = get<NotificationService>(),
                backgroundTaskHandler = get<BackgroundTaskHandler>(),
                apiTokenHandler = get<ApiTokenHandler>(),
                exportHandler = get<ExportHandler>(),
                goodreadsImportHandler = get<GoodreadsImportHandler>(),
                collectionService = get<org.booktower.services.CollectionService>(),
            )
        }
        single {
            AdminApiRouter(
                get<FilterSet>(),
                get<AdminHandler>(),
                get<BackgroundTaskHandler>(),
                get<WeblateHandler>(),
                get<EmailProviderService>(),
                get<ScheduledTaskService>(),
                get<BulkCoverService>(),
                get<TelemetryService>(),
            )
        }
        single {
            MetadataApiRouter(
                get<FilterSet>(),
                get<MetadataFetchService>(),
                get<BookService>(),
                get<MetadataProposalService>(),
                get<MetadataLockService>(),
                get<AuthorMetadataService>(),
            )
        }
        single {
            AudiobookApiRouter(
                get<FilterSet>(),
                get<ListeningSessionService>(),
                get<ListeningStatsService>(),
                get<AudiobookMetaService>(),
                get<AppConfig>().storage,
            )
        }
        single {
            DeviceSyncRouter(
                get<FilterSet>(),
                get<KoboSyncHandler>(),
                get<KOReaderSyncHandler>(),
                get<OpdsHandler>(),
            )
        }

        // ── AppHandler (composes routers) ────────────────────────────────────
        single {
            AppHandler(
                get<FileHandler>(),
                get<AppConfig>().storage,
                get<AppConfig>().demoMode,
                get<AuthRouter>(),
                get<OidcRouter>(),
                get<PageRouter>(),
                get<BookApiRouter>(),
                get<LibraryApiRouter>(),
                get<UserApiRouter>(),
                get<AdminApiRouter>(),
                get<MetadataApiRouter>(),
                get<AudiobookApiRouter>(),
                get<DeviceSyncRouter>(),
            )
        }
    }
