package org.booktower.config

import org.booktower.handlers.AppHandler
import org.booktower.services.AdminService
import org.booktower.services.AnnotationService
import org.booktower.services.AuthService
import org.booktower.services.BookmarkService
import org.booktower.services.BookService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
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

    single { BookService(get<Database>().getJdbi(), get()) }

    single { BookmarkService(get<Database>().getJdbi()) }

    single { UserSettingsService(get<Database>().getJdbi()) }

    single { AnalyticsService(get<Database>().getJdbi(), get()) }

    single { PdfMetadataService(get<Database>().getJdbi(), get<AppConfig>().storage.coversPath) }

    single { AdminService(get<Database>().getJdbi()) }

    single { AnnotationService(get<Database>().getJdbi()) }

    single { WeblateHandler(get<AppConfig>().weblate) }

    single { AppHandler(get(), get(), get(), get(), get(), get(), get(), get(), get<AppConfig>().storage, get(), get(), get(), get()) }
}
