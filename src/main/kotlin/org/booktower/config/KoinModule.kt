package org.booktower.config

import org.booktower.handlers.AppHandler
import org.booktower.handlers.BulkBookHandler
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
import org.booktower.services.BookNotebookService
import org.booktower.services.BookReviewService
import org.booktower.services.BookService
import org.booktower.services.BookmarkService
import org.booktower.services.BulkCoverService
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
import org.booktower.services.GoodreadsImportService
import org.booktower.services.HardcoverSyncService
import org.booktower.services.JournalService
import org.booktower.services.JwtService
import org.booktower.services.KOReaderSyncService
import org.booktower.services.KoboSyncService
import org.booktower.services.KomgaApiService
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

        single { JwtService(get<AppConfig>().security) }

        single { AuthService(get<Database>().getJdbi(), get()) }

        single {
            LibraryService(
                get<Database>().getJdbi(),
                get<org.booktower.services.PdfMetadataService>(),
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

        single { SeedService(get(), get(), get<AppConfig>().storage.coversPath, get<AppConfig>().storage.booksPath) }

        single { BulkBookHandler(get()) }

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

        single { KomgaApiService(get<Database>().getJdbi(), get(), get(), get<AppConfig>().baseUrl) }

        single { FontService(get<Database>().getJdbi(), "${get<AppConfig>().storage.booksPath}/fonts") }

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

        single { WeblateHandler(get<AppConfig>().weblate) }

        single {
            AppHandler(get(), get(), get(), get(), get(), get(), get(), get(), get(), get<AppConfig>().storage, get(), get(), get(), get(), get(), get(), get(), get(), get<AppConfig>().baseUrl, get<AppConfig>().registrationOpen, get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), communityRatingService = get(), comicPageHashService = get(), demoMode = get<AppConfig>().demoMode)
        }
    }
