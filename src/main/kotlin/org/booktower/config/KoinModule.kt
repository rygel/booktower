package org.booktower.config

import org.booktower.handlers.AppHandler
import org.booktower.handlers.BulkBookHandler
import org.booktower.services.AdminService
import org.booktower.services.ScanScheduleService
import org.booktower.services.AnnotationService
import org.booktower.services.ApiTokenService
import org.booktower.services.ComicService
import org.booktower.services.EpubMetadataService
import org.booktower.services.ExportService
import org.booktower.services.GoodreadsImportService
import org.booktower.services.ReadingSessionService
import org.booktower.services.MagicShelfService
import org.booktower.services.MetadataFetchService
import org.booktower.services.AuthService
import org.booktower.services.BookmarkService
import org.booktower.services.BookService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.booktower.services.PasswordResetService
import org.booktower.services.PdfMetadataService
import org.booktower.services.AnalyticsService
import org.booktower.services.UserSettingsService
import org.booktower.weblate.WeblateHandler
import org.koin.dsl.module

val appModule = module {
    single { AppConfig.load() }

    single {
        val config = get<AppConfig>()
        config.storage.ensureDirectories()
        Database.connect(config.database)
    }

    single { TemplateRenderer() }

    single { JwtService(get<AppConfig>().security) }

    single { AuthService(get<Database>().getJdbi(), get()) }

    single { LibraryService(get<Database>().getJdbi(), get()) }

    single { BookService(get<Database>().getJdbi(), get(), get()) }

    single { BookmarkService(get<Database>().getJdbi()) }

    single { UserSettingsService(get<Database>().getJdbi()) }

    single { AnalyticsService(get<Database>().getJdbi(), get()) }

    single { ReadingSessionService(get<Database>().getJdbi()) }

    single { PdfMetadataService(get<Database>().getJdbi(), get<AppConfig>().storage.coversPath) }

    single { EpubMetadataService(get<Database>().getJdbi(), get<AppConfig>().storage.coversPath) }

    single { AdminService(get<Database>().getJdbi()) }

    single { AnnotationService(get<Database>().getJdbi()) }

    single { MetadataFetchService() }

    single { MagicShelfService(get<Database>().getJdbi(), get()) }

    single { PasswordResetService(get<Database>().getJdbi()) }

    single { ApiTokenService(get<Database>().getJdbi()) }

    single { ExportService(get<Database>().getJdbi()) }

    single { GoodreadsImportService(get()) }

    single { ComicService() }

    single { BulkBookHandler(get()) }

    single { ScanScheduleService(get<Database>().getJdbi(), get(), get<AppConfig>().autoScanMinutes) }

    single { WeblateHandler(get<AppConfig>().weblate) }

    single { AppHandler(get(), get(), get(), get(), get(), get(), get(), get(), get(), get<AppConfig>().storage, get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}
