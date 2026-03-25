package org.runary.integration

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.runary.config.AppConfig
import org.runary.config.Database
import org.runary.config.TemplateRenderer
import org.runary.config.appModule
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
import org.runary.handlers.KOReaderSyncHandler
import org.runary.handlers.KoboSyncHandler
import org.runary.handlers.LibraryHandler2
import org.runary.handlers.OidcHandler
import org.runary.handlers.OpdsHandler
import org.runary.handlers.PageHandler
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
import org.runary.services.AuthService
import org.runary.services.BookService
import org.runary.services.FtsService
import org.runary.services.JwtService
import org.runary.services.LibraryService
import org.runary.services.SeedService
import kotlin.test.assertNotNull

/**
 * Verifies that all Koin DI bindings resolve without errors.
 *
 * This catches mismatches between KoinModule.kt and constructor signatures —
 * if a service adds a new dependency and KoinModule isn't updated, this test fails.
 *
 * The integration tests in IntegrationTestBase bypass Koin entirely (they construct
 * services manually), so without this test, DI resolution errors only surface at
 * runtime when the real application starts.
 */
class KoinResolutionTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            startKoin {
                modules(appModule)
            }
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            stopKoin()
        }
    }

    // ── Config & Infrastructure ─────────────────────────────────────────

    @Test
    fun `Koin resolves AppConfig`() {
        assertNotNull(GlobalContext.get().get<AppConfig>())
    }

    @Test
    fun `Koin resolves Database`() {
        assertNotNull(GlobalContext.get().get<Database>())
    }

    @Test
    fun `Koin resolves TemplateRenderer`() {
        assertNotNull(GlobalContext.get().get<TemplateRenderer>())
    }

    // ── Services ────────────────────────────────────────────────────────

    @Test
    fun `Koin resolves JwtService`() {
        assertNotNull(GlobalContext.get().get<JwtService>())
    }

    @Test
    fun `Koin resolves AuthService`() {
        assertNotNull(GlobalContext.get().get<AuthService>())
    }

    @Test
    fun `Koin resolves BookService`() {
        assertNotNull(GlobalContext.get().get<BookService>())
    }

    @Test
    fun `Koin resolves LibraryService`() {
        assertNotNull(GlobalContext.get().get<LibraryService>())
    }

    @Test
    fun `Koin resolves SeedService`() {
        assertNotNull(GlobalContext.get().get<SeedService>())
    }

    @Test
    fun `Koin resolves FtsService`() {
        assertNotNull(GlobalContext.get().get<FtsService>())
    }

    // ── Filters ─────────────────────────────────────────────────────────

    @Test
    fun `Koin resolves FilterSet`() {
        assertNotNull(GlobalContext.get().get<FilterSet>())
    }

    // ── Handlers ────────────────────────────────────────────────────────

    @Test
    fun `Koin resolves AuthHandler2`() {
        assertNotNull(GlobalContext.get().get<AuthHandler2>())
    }

    @Test
    fun `Koin resolves BookHandler2`() {
        assertNotNull(GlobalContext.get().get<BookHandler2>())
    }

    @Test
    fun `Koin resolves FileHandler`() {
        assertNotNull(GlobalContext.get().get<FileHandler>())
    }

    @Test
    fun `Koin resolves AdminHandler`() {
        assertNotNull(GlobalContext.get().get<AdminHandler>())
    }

    @Test
    fun `Koin resolves PageHandler`() {
        assertNotNull(GlobalContext.get().get<PageHandler>())
    }

    @Test
    fun `Koin resolves BackgroundTaskHandler`() {
        assertNotNull(GlobalContext.get().get<BackgroundTaskHandler>())
    }

    @Test
    fun `Koin resolves LibraryHandler2`() {
        assertNotNull(GlobalContext.get().get<LibraryHandler2>())
    }

    @Test
    fun `Koin resolves BookmarkHandler`() {
        assertNotNull(GlobalContext.get().get<BookmarkHandler>())
    }

    @Test
    fun `Koin resolves UserSettingsHandler`() {
        assertNotNull(GlobalContext.get().get<UserSettingsHandler>())
    }

    @Test
    fun `Koin resolves JournalHandler`() {
        assertNotNull(GlobalContext.get().get<JournalHandler>())
    }

    @Test
    fun `Koin resolves OidcHandler`() {
        assertNotNull(GlobalContext.get().get<OidcHandler>())
    }

    @Test
    fun `Koin resolves KoboSyncHandler`() {
        assertNotNull(GlobalContext.get().get<KoboSyncHandler>())
    }

    @Test
    fun `Koin resolves KOReaderSyncHandler`() {
        assertNotNull(GlobalContext.get().get<KOReaderSyncHandler>())
    }

    @Test
    fun `Koin resolves FontHandler`() {
        assertNotNull(GlobalContext.get().get<FontHandler>())
    }

    @Test
    fun `Koin resolves ReaderPreferencesHandler`() {
        assertNotNull(GlobalContext.get().get<ReaderPreferencesHandler>())
    }

    @Test
    fun `Koin resolves OpdsHandler`() {
        assertNotNull(GlobalContext.get().get<OpdsHandler>())
    }

    @Test
    fun `Koin resolves ApiTokenHandler`() {
        assertNotNull(GlobalContext.get().get<ApiTokenHandler>())
    }

    @Test
    fun `Koin resolves ExportHandler`() {
        assertNotNull(GlobalContext.get().get<ExportHandler>())
    }

    @Test
    fun `Koin resolves GoodreadsImportHandler`() {
        assertNotNull(GlobalContext.get().get<GoodreadsImportHandler>())
    }

    @Test
    fun `Koin resolves BulkBookHandler`() {
        assertNotNull(GlobalContext.get().get<BulkBookHandler>())
    }

    // ── Routers ─────────────────────────────────────────────────────────

    @Test
    fun `Koin resolves AuthRouter`() {
        assertNotNull(GlobalContext.get().get<AuthRouter>())
    }

    @Test
    fun `Koin resolves OidcRouter`() {
        assertNotNull(GlobalContext.get().get<OidcRouter>())
    }

    @Test
    fun `Koin resolves BookApiRouter`() {
        assertNotNull(GlobalContext.get().get<BookApiRouter>())
    }

    @Test
    fun `Koin resolves LibraryApiRouter`() {
        assertNotNull(GlobalContext.get().get<LibraryApiRouter>())
    }

    @Test
    fun `Koin resolves UserApiRouter`() {
        assertNotNull(GlobalContext.get().get<UserApiRouter>())
    }

    @Test
    fun `Koin resolves AdminApiRouter`() {
        assertNotNull(GlobalContext.get().get<AdminApiRouter>())
    }

    @Test
    fun `Koin resolves MetadataApiRouter`() {
        assertNotNull(GlobalContext.get().get<MetadataApiRouter>())
    }

    @Test
    fun `Koin resolves AudiobookApiRouter`() {
        assertNotNull(GlobalContext.get().get<AudiobookApiRouter>())
    }

    @Test
    fun `Koin resolves DeviceSyncRouter`() {
        assertNotNull(GlobalContext.get().get<DeviceSyncRouter>())
    }

    @Test
    fun `Koin resolves PageRouter`() {
        assertNotNull(GlobalContext.get().get<PageRouter>())
    }

    // ── Top-level ───────────────────────────────────────────────────────

    @Test
    fun `Koin resolves AppHandler`() {
        assertNotNull(GlobalContext.get().get<AppHandler>())
    }
}
