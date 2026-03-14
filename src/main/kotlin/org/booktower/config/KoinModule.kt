package org.booktower.config

import org.booktower.handlers.AppHandler
import org.booktower.services.AuthService
import org.booktower.services.BookmarkService
import org.booktower.services.BookService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.booktower.services.UserSettingsService
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

    single { LibraryService(get<Database>().getJdbi(), get<AppConfig>().storage) }

    single { BookService(get<Database>().getJdbi(), get<AppConfig>().storage) }

    single { BookmarkService(get<Database>().getJdbi()) }

    single { UserSettingsService(get<Database>().getJdbi()) }

    single { AppHandler(get(), get(), get(), get(), get(), get(), get<AppConfig>().storage, get()) }
}
