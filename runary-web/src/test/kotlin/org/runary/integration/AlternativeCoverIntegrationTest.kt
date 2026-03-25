package org.runary.integration

import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.runary.TestFixture
import org.runary.config.Json
import org.runary.config.SmtpConfig
import org.runary.config.WeblateConfig
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
import org.runary.handlers.BrowsePageHandler
import org.runary.handlers.BulkBookHandler
import org.runary.handlers.DiscoveryPageHandler
import org.runary.handlers.ExportHandler
import org.runary.handlers.FileHandler
import org.runary.handlers.GoodreadsImportHandler
import org.runary.handlers.LibraryHandler2
import org.runary.handlers.OpdsHandler
import org.runary.handlers.PageHandler
import org.runary.handlers.SettingsPageHandler
import org.runary.handlers.StatsPageHandler
import org.runary.handlers.UserSettingsHandler
import org.runary.models.LoginResponse
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
import org.runary.services.AuthService
import org.runary.services.BookService
import org.runary.services.BookmarkService
import org.runary.services.CalibreConversionService
import org.runary.services.ComicService
import org.runary.services.CoverCandidate
import org.runary.services.EmailService
import org.runary.services.EpubMetadataService
import org.runary.services.ExportService
import org.runary.services.GoodreadsImportService
import org.runary.services.JwtService
import org.runary.services.LibraryService
import org.runary.services.MagicShelfService
import org.runary.services.MetadataFetchService
import org.runary.services.PasswordResetService
import org.runary.services.PdfMetadataService
import org.runary.services.ReadingSessionService
import org.runary.services.SeedService
import org.runary.services.UserSettingsService
import org.runary.weblate.WeblateHandler
import java.nio.file.Path

class AlternativeCoverIntegrationTest {
    @TempDir
    lateinit var coversDir: Path

    private val config = TestFixture.config
    private val jdbi = TestFixture.database.getJdbi()

    private val stubCoverService =
        object : AlternativeCoverService() {
            override fun fetchCandidates(
                title: String,
                author: String?,
                isbn: String?,
            ): List<CoverCandidate> =
                listOf(
                    CoverCandidate("https://covers.openlibrary.org/b/isbn/9780441013593-L.jpg", "openlibrary"),
                    CoverCandidate("https://books.google.com/cover?id=abc123", "googlebooks"),
                )

            override fun downloadBytes(url: String): ByteArray? =
                if (url.startsWith("https://")) {
                    byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
                } else {
                    null
                }
        }

    private lateinit var app: HttpHandler

    @BeforeEach
    fun setup() {
        app = buildApp()
    }

    @Suppress("LongMethod")
    private fun buildApp(): HttpHandler {
        val jwtService = JwtService(config.security)
        val authService = AuthService(jdbi, jwtService)
        val pdfMetadataService = PdfMetadataService(jdbi, coversDir.toString())
        val libraryService = LibraryService(jdbi, pdfMetadataService)
        val bookmarkService = BookmarkService(jdbi)
        val userSettingsService = UserSettingsService(jdbi)
        val analyticsService = AnalyticsService(jdbi, userSettingsService)
        val readingSessionService = ReadingSessionService(jdbi)
        val bookService = BookService(jdbi, analyticsService, readingSessionService)
        val adminService = AdminService(jdbi)
        val annotationService = AnnotationService(jdbi)
        val magicShelfService = MagicShelfService(jdbi, bookService)
        val passwordResetService = PasswordResetService(jdbi)
        val apiTokenService = ApiTokenService(jdbi)
        val exportService = ExportService(jdbi)
        val epubMetadataService = EpubMetadataService(jdbi, coversDir.toString())
        val comicService = ComicService()
        val goodreadsImportService = GoodreadsImportService(bookService)
        val seedService = SeedService(bookService, libraryService, coversDir.toString(), config.storage.booksPath)
        val storage = config.storage.copy(coversPath = coversDir.toString())

        // Handlers
        val authHandler =
            AuthHandler2(
                authService,
                userSettingsService,
                passwordResetService,
                EmailService(SmtpConfig("", 587, "", "", "", true)),
                "http://localhost:9999",
                true,
                null,
                false,
            )
        val libraryHandler = LibraryHandler2(libraryService, null, storage)
        val bookHandler = BookHandler2(bookService, readingSessionService)
        val bookmarkHandler = BookmarkHandler(bookmarkService)
        val calibreService = CalibreConversionService(java.io.File(storage.tempPath, "calibre-cache"))
        val fileHandler = FileHandler(bookService, pdfMetadataService, epubMetadataService, storage, calibreService = calibreService)
        val settingsHandler = UserSettingsHandler(userSettingsService)
        val adminHandler =
            AdminHandler(
                adminService,
                jwtService,
                authService,
                TestFixture.templateRenderer,
                passwordResetService,
                seedService,
                EmailService(
                    SmtpConfig("", 587, "", "", "", true),
                ),
                "http://localhost:9999",
                null,
                null,
                null,
                null,
                null,
            )
        val handlers =
            TestPageHandlers.create(
                jwtService,
                authService,
                libraryService,
                bookService,
                bookmarkService,
                userSettingsService,
                analyticsService,
                annotationService,
                MetadataFetchService(),
                magicShelfService,
                TestFixture.templateRenderer,
                readingSessionService = readingSessionService,
            )
        val pageHandler = handlers.pageHandler
        val browsePageHandler = handlers.browsePageHandler
        val statsPageHandler = handlers.statsPageHandler
        val settingsPageHandler = handlers.settingsPageHandler
        val discoveryPageHandler = handlers.discoveryPageHandler
        val opdsHandler = OpdsHandler(authService, libraryService, bookService, storage, apiTokenService, null)
        val apiTokenHandler = ApiTokenHandler(apiTokenService, jwtService)
        val exportHandler = ExportHandler(exportService, jwtService)
        val goodreadsImportHandler = GoodreadsImportHandler(goodreadsImportService, jwtService)
        val bulkBookHandler = BulkBookHandler(bookService)
        val backgroundTaskService = org.runary.services.BackgroundTaskService()
        val backgroundTaskHandler = org.runary.handlers.BackgroundTaskHandler(backgroundTaskService)

        // Filters
        val userExistsCheck = { userId: java.util.UUID -> authService.getUserById(userId) != null }
        val authFilter = jwtAuthFilter(jwtService, userExistsCheck)
        val filters =
            FilterSet(
                auth = authFilter,
                admin = authFilter.then(adminFilter()),
                authRateLimit = RateLimitFilter(maxRequests = 10, windowSeconds = 60),
                optionalAuth = org.runary.filters.optionalAuthFilter(jwtService, userExistsCheck),
            )

        // Routers — inject stubCoverService into BookApiRouter
        val authRouter = AuthRouter(authHandler, filters)
        val oidcRouter = OidcRouter(null)
        val pageRouter = PageRouter(filters, pageHandler, adminHandler, jwtService, TestFixture.templateRenderer, true, null, null, browsePageHandler, statsPageHandler, settingsPageHandler, discoveryPageHandler)
        val bookApiRouter =
            BookApiRouter(
                filters,
                bookHandler,
                bulkBookHandler,
                bookmarkHandler,
                fileHandler,
                bookService,
                comicService,
                storage,
                magicShelfService,
                null,
                null,
                null,
                stubCoverService,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
            )
        val libraryApiRouter = LibraryApiRouter(filters, libraryHandler, libraryService, null, null)
        val userApiRouter =
            UserApiRouter(filters, settingsHandler, bookService, userSettingsService, null, null, null, null, null, null, null, backgroundTaskHandler, apiTokenHandler, exportHandler, goodreadsImportHandler)
        val adminApiRouter =
            AdminApiRouter(
                filters,
                adminHandler,
                backgroundTaskHandler,
                WeblateHandler(WeblateConfig("", "", "", false)),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
            )
        val metadataApiRouter = MetadataApiRouter(filters, MetadataFetchService(), bookService, null, null, null)
        val audiobookApiRouter = AudiobookApiRouter(filters, null, null, null, storage)
        val deviceSyncRouter = DeviceSyncRouter(filters, null, null, opdsHandler)

        val appHandler =
            AppHandler(fileHandler, storage, false, authRouter, oidcRouter, pageRouter, bookApiRouter, libraryApiRouter, userApiRouter, adminApiRouter, metadataApiRouter, audiobookApiRouter, deviceSyncRouter)
        return globalErrorFilter().then(appHandler.routes())
    }

    private fun registerAndGetToken(prefix: String = "cov"): String {
        val u = "${prefix}_${System.nanoTime()}"
        val resp =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$u","email":"$u@test.com","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
            )
        return Json.mapper.readValue(resp.bodyString(), LoginResponse::class.java).token
    }

    private fun createLibraryAndBook(token: String): Pair<String, String> {
        val libResp =
            app(
                Request(Method.POST, "/api/libraries")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"CovLib ${System.nanoTime()}","path":"./data/cov-${System.nanoTime()}"}"""),
            )
        val libId =
            Json.mapper
                .readTree(libResp.bodyString())
                .get("id")
                .asText()
        val bookResp =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Dune","author":"Frank Herbert","description":null,"libraryId":"$libId"}"""),
            )
        val bookId =
            Json.mapper
                .readTree(bookResp.bodyString())
                .get("id")
                .asText()
        return libId to bookId
    }

    @Test
    fun `GET api books id covers alternatives returns candidate list`() {
        val token = registerAndGetToken()
        val (_, bookId) = createLibraryAndBook(token)
        val resp =
            app(
                Request(Method.GET, "/api/books/$bookId/covers/alternatives")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, resp.status)
        val covers = Json.mapper.readTree(resp.bodyString()).get("covers")
        assertEquals(2, covers.size())
        assertEquals("openlibrary", covers[0].get("source").asText())
        assertEquals("googlebooks", covers[1].get("source").asText())
    }

    @Test
    fun `POST api books id cover apply-url downloads and saves cover`() {
        val token = registerAndGetToken()
        val (_, bookId) = createLibraryAndBook(token)
        val resp =
            app(
                Request(Method.POST, "/api/books/$bookId/cover/apply-url")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"url":"https://covers.openlibrary.org/b/isbn/9780441013593-L.jpg"}"""),
            )
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assert(tree.get("coverUrl").asText().startsWith("/covers/")) { "coverUrl should start with /covers/" }
    }

    @Test
    fun `POST api books id cover apply-url returns 400 when url missing`() {
        val token = registerAndGetToken()
        val (_, bookId) = createLibraryAndBook(token)
        val resp =
            app(
                Request(Method.POST, "/api/books/$bookId/cover/apply-url")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{}"""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `GET api books id covers alternatives requires authentication`() {
        val token = registerAndGetToken()
        val (_, bookId) = createLibraryAndBook(token)
        val resp = app(Request(Method.GET, "/api/books/$bookId/covers/alternatives"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `POST api books id cover apply-url requires authentication`() {
        val resp =
            app(
                Request(Method.POST, "/api/books/some-id/cover/apply-url")
                    .header("Content-Type", "application/json")
                    .body("""{"url":"https://example.com/cover.jpg"}"""),
            )
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
