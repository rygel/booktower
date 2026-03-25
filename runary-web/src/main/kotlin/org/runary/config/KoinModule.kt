package org.runary.config

import org.http4k.core.then
import org.koin.dsl.module
import org.runary.filters.RateLimitFilter
import org.runary.filters.adminFilter
import org.runary.filters.jwtAuthFilter
import org.runary.filters.optionalAuthFilter
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
import org.runary.handlers.FontHandler
import org.runary.handlers.GoodreadsImportHandler
import org.runary.handlers.JournalHandler
import org.runary.handlers.KOReaderSyncHandler
import org.runary.handlers.KoboSyncHandler
import org.runary.handlers.LibraryHandler2
import org.runary.handlers.OidcHandler
import org.runary.handlers.OpdsHandler
import org.runary.handlers.PageHandler
import org.runary.handlers.ReaderPreferencesHandler
import org.runary.handlers.SettingsPageHandler
import org.runary.handlers.StatsPageHandler
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
import org.runary.services.AlternativeCoverService
import org.runary.services.AnalyticsService
import org.runary.services.AnnotationService
import org.runary.services.ApiTokenService
import org.runary.services.AudiobookMetaService
import org.runary.services.AuditService
import org.runary.services.AuthService
import org.runary.services.AuthorMetadataService
import org.runary.services.BackgroundTaskService
import org.runary.services.BookDeliveryService
import org.runary.services.BookDropService
import org.runary.services.BookFilesService
import org.runary.services.BookLinkService
import org.runary.services.BookNotebookService
import org.runary.services.BookReviewService
import org.runary.services.BookService
import org.runary.services.BookSharingService
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
import org.runary.services.GoodreadsImportService
import org.runary.services.HardcoverSyncService
import org.runary.services.JournalService
import org.runary.services.JwtService
import org.runary.services.KOReaderSyncService
import org.runary.services.KoboSyncService
import org.runary.services.LibraryAccessService
import org.runary.services.LibraryHealthService
import org.runary.services.LibraryService
import org.runary.services.LibraryWatchService
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
import org.runary.services.ScanScheduleService
import org.runary.services.ScheduledTaskService
import org.runary.services.SeedService
import org.runary.services.TelemetryService
import org.runary.services.UserPermissionsService
import org.runary.services.UserSettingsService
import org.runary.weblate.WeblateHandler

// ── Core: config, template engine, services with no DB dependency ───────────

val coreModule =
    module {
        single { AppConfig.load() }
        single { TemplateRenderer() }
        single { ComicService() }
        single { GeoIpService() }
        single { BackgroundTaskService() }
        single { EmailService(get<AppConfig>().smtp) }
        single { MetadataFetchService(get<AppConfig>().metadata) }
    }

// ── Security: auth, JWT, permissions, rate limiting ─────────────────────────

val securityModule =
    module {
        single { JwtService(get<AppConfig>().security) }
        single { AuthService(get<Database>().getJdbi(), get()) }
        single { UserPermissionsService(get<Database>().getJdbi()) }
        single { LibraryAccessService(get<Database>().getJdbi()) }
        single { PasswordResetService(get<Database>().getJdbi()) }
        single { ApiTokenService(get<Database>().getJdbi()) }
        single { OpdsCredentialsService(get<Database>().getJdbi()) }
        single { OidcService(get<AppConfig>().oidc) }
    }

// ── Persistence: database connection, JDBI-backed services ──────────────────

val persistenceModule =
    module {
        single {
            val config = get<AppConfig>()
            config.storage.ensureDirectories()
            Database.connect(config.database)
        }

        single {
            LibraryService(
                get<Database>().getJdbi(),
                get<PdfMetadataService>(),
                get<LibraryAccessService>(),
                get(),
                get(),
            )
        }
        single { BookService(get<Database>().getJdbi(), get(), get(), webhookService = getOrNull()) }
        single { BookmarkService(get<Database>().getJdbi()) }
        single { UserSettingsService(get<Database>().getJdbi()) }
        single { AnalyticsService(get<Database>().getJdbi(), get()) }
        single { ReadingSessionService(get<Database>().getJdbi()) }
        single { PdfMetadataService(get<Database>().getJdbi(), get<AppConfig>().storage.coversPath) }
        single { EpubMetadataService(get<Database>().getJdbi(), get<AppConfig>().storage.coversPath) }
        single { AdminService(get<Database>().getJdbi()) }
        single { AnnotationService(get<Database>().getJdbi()) }
        single { MagicShelfService(get<Database>().getJdbi(), get()) }
        single { ExportService(get<Database>().getJdbi()) }
        single { GoodreadsImportService(get()) }
        single { ScanScheduleService(get<Database>().getJdbi(), get(), get<AppConfig>().autoScanMinutes) }
        single { LibraryWatchService(get<Database>().getJdbi(), get()) }
        single { DuplicateDetectionService(get<Database>().getJdbi()) }
        single { AuditService(get<Database>().getJdbi(), get<GeoIpService>()) }
        single { JournalService(get<Database>().getJdbi()) }
        single { LibraryHealthService(get<Database>().getJdbi()) }
        single { RecommendationService(get<Database>().getJdbi()) }
        single { BookDeliveryService(get<Database>().getJdbi(), get(), get(), get<AppConfig>().storage.booksPath) }
        single { BookDropService(get(), "${get<AppConfig>().storage.booksPath}/bookdrop") }
        single { KoboSyncService(get<Database>().getJdbi(), get(), get<AppConfig>().baseUrl, get()) }
        single { KOReaderSyncService(get<Database>().getJdbi(), get()) }
        single { org.runary.services.FontService(get<Database>().getJdbi(), "${get<AppConfig>().storage.booksPath}/fonts") }
        single { ReaderPreferencesService(get()) }
        single { org.runary.services.FtsService(get<Database>().getJdbi(), get<AppConfig>().fts.enabled) }
        single { org.runary.services.FtsIndexWorker(get(), get(), get<AppConfig>().fts.throttleMs) }
        single { org.runary.services.ComicPageHashService(get<Database>().getJdbi(), get()) }
        single { org.runary.services.ComicPageHashWorker(get(), get()) }
        single { MetadataLockService(get<Database>().getJdbi()) }
        single { ReadingStatsService(get<Database>().getJdbi()) }
        single { ListeningSessionService(get<Database>().getJdbi()) }
        single { ListeningStatsService(get<Database>().getJdbi()) }
        single { MetadataProposalService(get<Database>().getJdbi()) }
        single { HardcoverSyncService(get<Database>().getJdbi(), get()) }
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
        single { org.runary.services.CommunityRatingService(get<Database>().getJdbi()) }
        single { BookLinkService(get<Database>().getJdbi(), get<BookService>()) }
        single { BookSharingService(get<Database>().getJdbi(), get<BookService>()) }
        single { org.runary.services.CollectionService(get<Database>().getJdbi()) }
        single { org.runary.services.BulkMetadataRefreshService(get<Database>().getJdbi(), get(), get(), get()) }
        single { org.runary.services.BatchImportService(get<Database>().getJdbi(), get(), get(), get(), get(), get()) }
        single { org.runary.services.CustomFieldService(get<Database>().getJdbi()) }
        single { org.runary.services.PublicProfileService(get<Database>().getJdbi(), get()) }
        single { org.runary.services.ReadingSpeedService(get<Database>().getJdbi()) }
        single { org.runary.services.BookConditionService(get()) }
        single { org.runary.services.HealthService(get<Database>().getJdbi()) }
        single { org.runary.services.ReadingListService(get<Database>().getJdbi()) }
        single { org.runary.services.LibraryStatsService(get<Database>().getJdbi()) }
        single { org.runary.services.WebhookService(get<Database>().getJdbi()) }
        single { org.runary.services.ReadingTimelineService(get<Database>().getJdbi()) }
        single { org.runary.services.ReadingGoalService(get<Database>().getJdbi(), get()) }
        single { org.runary.services.AnnotationExportService(get<Database>().getJdbi()) }
        single { org.runary.services.DiscoveryService(get<Database>().getJdbi()) }
        single { org.runary.services.BackupService(get<Database>().getJdbi()) }
        single { org.runary.services.PositionSyncService(get<Database>().getJdbi()) }
        single { org.runary.services.WishlistService(get<Database>().getJdbi()) }
        single { org.runary.services.AdvancedSearchService(get<Database>().getJdbi(), getOrNull()) }
        single { AuthorMetadataService() }
        single { AlternativeCoverService() }
        single { CalibreConversionService(java.io.File(get<AppConfig>().storage.tempPath, "calibre-cache")) }
    }

// ── Seed: demo data generation ──────────────────────────────────────────────

val seedModule =
    module {
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
    }

// ── Web: handlers, routers, filters, app assembly ───────────────────────────

val webModule =
    module {
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
            FileHandler(get(), get(), get(), get<AppConfig>().storage, calibreService = get(), ftsService = get())
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
                get<org.runary.services.ComicPageHashService>(),
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
                getOrNull(),
                getOrNull(),
                getOrNull(),
                get<BackgroundTaskService>(),
            )
        }
        single {
            BrowsePageHandler(
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        single {
            StatsPageHandler(
                get(),
                get(),
                get(),
                get(),
                getOrNull(),
                getOrNull<org.runary.services.LibraryStatsService>(),
                getOrNull<org.runary.services.ReadingTimelineService>(),
                getOrNull<org.runary.services.ReadingSpeedService>(),
                getOrNull<org.runary.services.LibraryHealthService>(),
            )
        }
        single {
            SettingsPageHandler(
                get(),
                get(),
                get(),
                getOrNull<org.runary.services.KoboSyncService>(),
                getOrNull<org.runary.services.KOReaderSyncService>(),
                getOrNull<org.runary.services.FilterPresetService>(),
                getOrNull<org.runary.services.ScheduledTaskService>(),
                getOrNull<org.runary.services.OpdsCredentialsService>(),
                getOrNull<org.runary.services.ContentRestrictionsService>(),
                getOrNull<org.runary.services.HardcoverSyncService>(),
                getOrNull<org.runary.services.BookDeliveryService>(),
            )
        }
        single {
            DiscoveryPageHandler(
                get(),
                get(),
                get(),
                get(),
                get(),
                getOrNull<org.runary.services.DiscoveryService>(),
                getOrNull<org.runary.services.ReadingListService>(),
                getOrNull<org.runary.services.WishlistService>(),
                getOrNull<org.runary.services.CollectionService>(),
                getOrNull<org.runary.services.WebhookService>(),
                getOrNull<org.runary.services.BookDropService>(),
                getOrNull<org.runary.services.MetadataProposalService>(),
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
        single { WeblateHandler(get<AppConfig>().weblate) }

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
                get<AuthService>(),
                get<UserSettingsService>(),
                get<BrowsePageHandler>(),
                get<StatsPageHandler>(),
                get<SettingsPageHandler>(),
                get<DiscoveryPageHandler>(),
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
                communityRatingService = get<org.runary.services.CommunityRatingService>(),
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
        single { org.runary.handlers.CollectionApiHandler(get<org.runary.services.CollectionService>()) }
        single { org.runary.handlers.ReadingListApiHandler(get<org.runary.services.ReadingListService>()) }
        single { org.runary.handlers.WishlistApiHandler(get<org.runary.services.WishlistService>()) }
        single { org.runary.handlers.WebhookApiHandler(get<org.runary.services.WebhookService>()) }
        single { org.runary.handlers.NotificationApiHandler(get<NotificationService>()) }
        single { org.runary.handlers.CustomFieldApiHandler(get<org.runary.services.CustomFieldService>()) }
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
                backgroundTaskHandler = get<BackgroundTaskHandler>(),
                apiTokenHandler = get<ApiTokenHandler>(),
                exportHandler = get<ExportHandler>(),
                goodreadsImportHandler = get<GoodreadsImportHandler>(),
                collectionApiHandler = get<org.runary.handlers.CollectionApiHandler>(),
                auditService = get<org.runary.services.AuditService>(),
                libraryStatsService = get<org.runary.services.LibraryStatsService>(),
                webhookApiHandler = get<org.runary.handlers.WebhookApiHandler>(),
                readingTimelineService = get<org.runary.services.ReadingTimelineService>(),
                readingGoalService = get<org.runary.services.ReadingGoalService>(),
                annotationExportService = get<org.runary.services.AnnotationExportService>(),
                discoveryService = get<org.runary.services.DiscoveryService>(),
                positionSyncService = get<org.runary.services.PositionSyncService>(),
                customFieldApiHandler = get<org.runary.handlers.CustomFieldApiHandler>(),
                publicProfileService = get<org.runary.services.PublicProfileService>(),
                readingSpeedService = get<org.runary.services.ReadingSpeedService>(),
                bookConditionService = get<org.runary.services.BookConditionService>(),
                readingListApiHandler = get<org.runary.handlers.ReadingListApiHandler>(),
                annotationService = get<org.runary.services.AnnotationService>(),
                wishlistApiHandler = get<org.runary.handlers.WishlistApiHandler>(),
                advancedSearchService = get<org.runary.services.AdvancedSearchService>(),
                notificationApiHandler = get<org.runary.handlers.NotificationApiHandler>(),
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
                get<org.runary.services.BulkMetadataRefreshService>(),
                get<org.runary.services.BackupService>(),
                get<org.runary.services.BatchImportService>(),
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
                get<org.runary.services.HealthService>(),
            )
        }
    }

// ── Composed application module ─────────────────────────────────────────────

val appModule =
    module {
        includes(coreModule, securityModule, persistenceModule, seedModule, webModule)
    }
