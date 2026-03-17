package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.config.Json
import org.booktower.config.OidcConfig
import org.booktower.filters.DemoModeFilter
import org.booktower.filters.GlobalErrorFilter
import org.booktower.config.WeblateConfig
import org.booktower.handlers.AppHandler
import org.booktower.services.OidcService
import org.booktower.models.BookDto
import org.booktower.models.LibraryDto
import org.booktower.models.LoginResponse
import org.booktower.services.AdminService
import org.booktower.services.AnalyticsService
import org.booktower.services.AnnotationService
import org.booktower.services.ApiTokenService
import org.booktower.services.AuthService
import org.booktower.services.ComicService
import org.booktower.services.EpubMetadataService
import org.booktower.services.ExportService
import org.booktower.services.GoodreadsImportService
import org.booktower.services.SeedService
import org.booktower.services.ReadingSessionService
import org.booktower.services.MagicShelfService
import org.booktower.services.MetadataFetchService
import org.booktower.services.BookmarkService
import org.booktower.services.BookService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.booktower.config.SmtpConfig
import org.booktower.services.EmailService
import org.booktower.services.PasswordResetService
import org.booktower.services.PdfMetadataService
import org.booktower.services.LibraryAccessService
import org.booktower.services.MetadataLockService
import org.booktower.services.HardcoverSyncService
import org.booktower.services.KoboSyncService
import org.booktower.services.OpdsCredentialsService
import org.booktower.services.BookFilesService
import org.booktower.services.EmailProviderService
import org.booktower.services.ScheduledTaskService
import org.booktower.services.BulkCoverService
import org.booktower.services.ComicMetadataService
import org.booktower.services.ContentRestrictionsService
import org.booktower.services.BookReviewService
import org.booktower.services.AudiobookMetaService
import org.booktower.services.FilterPresetService
import org.booktower.services.TelemetryService
import org.booktower.services.BookNotebookService
import org.booktower.services.GeoIpService
import org.booktower.services.GeoLocation
import org.booktower.services.NotificationService
import org.booktower.services.ListeningSessionService
import org.booktower.services.ListeningStatsService
import org.booktower.services.MetadataProposalService
import org.booktower.services.ReadingStatsService
import org.booktower.services.UserPermissionsService
import org.booktower.services.UserSettingsService
import org.booktower.weblate.WeblateHandler
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.then
import org.junit.jupiter.api.BeforeEach

abstract class IntegrationTestBase {
    protected lateinit var app: HttpHandler

    @BeforeEach
    open fun setupApp() {
        app = buildApp()
    }

    fun buildApp(registrationOpen: Boolean = true, demoMode: Boolean = false, oidcForceOnly: Boolean = false): HttpHandler {
        val config = TestFixture.config
        val jdbi = TestFixture.database.getJdbi()
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
        val seedService = SeedService(bookService, libraryService, config.storage.coversPath, config.storage.booksPath)
        val userPermissionsService = UserPermissionsService(jdbi)
        val koboSyncService = KoboSyncService(jdbi, bookService, "http://localhost:9999", userSettingsService)
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
        val geoIpService = object : GeoIpService() {
            override fun lookup(ip: String): GeoLocation =
                GeoLocation(countryCode = "US", countryName = "United States", city = "Test City")
        }
        val auditService = org.booktower.services.AuditService(jdbi, geoIpService)
        val appHandler = AppHandler(
            authService, libraryService, bookService, bookmarkService,
            userSettingsService, pdfMetadataService, epubMetadataService, adminService, jwtService, config.storage,
            TestFixture.templateRenderer,
            WeblateHandler(WeblateConfig("", "", "", false)),
            analyticsService,
            annotationService,
            metadataFetchService,
            magicShelfService,
            passwordResetService,
            EmailService(SmtpConfig("", 587, "", "", "", true)),
            "http://localhost:9999",
            registrationOpen,
            apiTokenService,
            exportService,
            comicService,
            goodreadsImportService,
            readingSessionService,
            seedService,
            userPermissionsService = userPermissionsService,
            libraryAccessService = libraryAccessService,
            metadataLockService = metadataLockService,
            readingStatsService = readingStatsService,
            listeningSessionService = listeningSessionService,
            listeningStatsService = listeningStatsService,
            metadataProposalService = metadataProposalService,
            hardcoverSyncService = hardcoverSyncService,
            koboSyncService = koboSyncService,
            opdsCredentialsService = opdsCredentialsService,
            bookFilesService = bookFilesService,
            emailProviderService = emailProviderService,
            scheduledTaskService = scheduledTaskService,
            bulkCoverService = bulkCoverService,
            comicMetadataService = comicMetadataService,
            contentRestrictionsService = contentRestrictionsService,
            bookReviewService = bookReviewService,
            bookNotebookService = bookNotebookService,
            notificationService = notificationService,
            auditService = auditService,
            audiobookMetaService = audiobookMetaService,
            filterPresetService = filterPresetService,
            telemetryService = telemetryService,
            communityRatingService = communityRatingService,
            demoMode = demoMode,
            oidcService = if (oidcForceOnly) OidcService(OidcConfig(enabled = true, forceOnlyMode = true)) else null,
        )
        return GlobalErrorFilter().then(DemoModeFilter(demoMode)).then(appHandler.routes())
    }

    protected open fun createMetadataFetchService(): MetadataFetchService = MetadataFetchService()
    protected open fun createCommunityRatingService(jdbi: org.jdbi.v3.core.Jdbi): org.booktower.services.CommunityRatingService =
        org.booktower.services.CommunityRatingService(jdbi)

    protected fun registerAndGetToken(prefix: String = "test"): String {
        val username = "${prefix}_${System.nanoTime()}"
        val response = app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
        )
        return Json.mapper.readValue(response.bodyString(), LoginResponse::class.java).token
    }

    protected fun createLibrary(token: String, nameSuffix: String = ""): String {
        val name = if (nameSuffix.isNotBlank()) nameSuffix else "Lib ${System.nanoTime()}"
        val response = app(
            Request(Method.POST, "/api/libraries")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"name":"$name","path":"./data/test-${System.nanoTime()}"}"""),
        )
        return Json.mapper.readValue(response.bodyString(), LibraryDto::class.java).id
    }

    protected fun createBook(token: String, libId: String, title: String = "Book ${System.nanoTime()}"): String {
        val response = app(
            Request(Method.POST, "/api/books")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"$title","author":null,"description":null,"libraryId":"$libId"}"""),
        )
        return Json.mapper.readValue(response.bodyString(), BookDto::class.java).id
    }
}
