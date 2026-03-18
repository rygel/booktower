package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.config.Json
import org.booktower.config.SmtpConfig
import org.booktower.config.WeblateConfig
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
import org.booktower.handlers.GoodreadsImportHandler
import org.booktower.handlers.LibraryHandler2
import org.booktower.handlers.OpdsHandler
import org.booktower.handlers.PageHandler
import org.booktower.handlers.UserSettingsHandler
import org.booktower.models.LoginResponse
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
import org.booktower.services.AuthService
import org.booktower.services.BookService
import org.booktower.services.BookmarkService
import org.booktower.services.CalibreConversionService
import org.booktower.services.ComicService
import org.booktower.services.CoverCandidate
import org.booktower.services.EmailService
import org.booktower.services.EpubMetadataService
import org.booktower.services.ExportService
import org.booktower.services.GoodreadsImportService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.booktower.services.MagicShelfService
import org.booktower.services.MetadataFetchService
import org.booktower.services.PdfMetadataService
import org.booktower.services.PasswordResetService
import org.booktower.services.ReadingSessionService
import org.booktower.services.SeedService
import org.booktower.services.UserSettingsService
import org.booktower.weblate.WeblateHandler
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
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
        val authHandler = AuthHandler2(authService, userSettingsService, passwordResetService, EmailService(SmtpConfig("", 587, "", "", "", true)), "http://localhost:9999", true, null, false)
        val libraryHandler = LibraryHandler2(libraryService, null, storage)
        val bookHandler = BookHandler2(bookService, readingSessionService)
        val bookmarkHandler = BookmarkHandler(bookmarkService)
        val calibreService = CalibreConversionService(java.io.File(storage.tempPath, "calibre-cache"))
        val fileHandler = FileHandler(bookService, pdfMetadataService, epubMetadataService, storage, calibreService = calibreService)
        val settingsHandler = UserSettingsHandler(userSettingsService)
        val adminHandler = AdminHandler(adminService, TestFixture.templateRenderer, passwordResetService, seedService, EmailService(SmtpConfig("", 587, "", "", "", true)), "http://localhost:9999", null, null, null, null, null)
        val pageHandler = PageHandler(jwtService, authService, libraryService, bookService, bookmarkService, userSettingsService, analyticsService, annotationService, MetadataFetchService(), magicShelfService, TestFixture.templateRenderer, readingSessionService, null)
        val opdsHandler = OpdsHandler(authService, libraryService, bookService, storage, apiTokenService, null)
        val apiTokenHandler = ApiTokenHandler(apiTokenService, jwtService)
        val exportHandler = ExportHandler(exportService, jwtService)
        val goodreadsImportHandler = GoodreadsImportHandler(goodreadsImportService, jwtService)
        val bulkBookHandler = BulkBookHandler(bookService)
        val backgroundTaskService = org.booktower.services.BackgroundTaskService()
        val backgroundTaskHandler = org.booktower.handlers.BackgroundTaskHandler(backgroundTaskService)

        // Filters
        val authFilter = jwtAuthFilter(jwtService) { userId: java.util.UUID -> authService.getUserById(userId) != null }
        val filters = FilterSet(auth = authFilter, admin = authFilter.then(adminFilter()), authRateLimit = RateLimitFilter(maxRequests = 10, windowSeconds = 60))

        // Routers — inject stubCoverService into BookApiRouter
        val authRouter = AuthRouter(authHandler, filters)
        val oidcRouter = OidcRouter(null)
        val pageRouter = PageRouter(filters, pageHandler, adminHandler, jwtService, TestFixture.templateRenderer, true)
        val bookApiRouter = BookApiRouter(
            filters, bookHandler, bulkBookHandler, bookmarkHandler, fileHandler,
            bookService, comicService, storage, magicShelfService,
            null, null, null, stubCoverService, null, null, null, null, null, null, null, null,
        )
        val libraryApiRouter = LibraryApiRouter(filters, libraryHandler, libraryService, null, null)
        val userApiRouter = UserApiRouter(filters, settingsHandler, bookService, userSettingsService, null, null, null, null, null, null, null, null, backgroundTaskHandler, apiTokenHandler, exportHandler, goodreadsImportHandler)
        val adminApiRouter = AdminApiRouter(filters, adminHandler, backgroundTaskHandler, WeblateHandler(WeblateConfig("", "", "", false)), null, null, null, null)
        val metadataApiRouter = MetadataApiRouter(filters, MetadataFetchService(), bookService, null, null, null)
        val audiobookApiRouter = AudiobookApiRouter(filters, null, null, null, storage)
        val deviceSyncRouter = DeviceSyncRouter(filters, null, null, null, opdsHandler)

        val appHandler = AppHandler(fileHandler, storage, false, authRouter, oidcRouter, pageRouter, bookApiRouter, libraryApiRouter, userApiRouter, adminApiRouter, metadataApiRouter, audiobookApiRouter, deviceSyncRouter)
        return globalErrorFilter().then(appHandler.routes())
    }

    private fun registerAndGetToken(prefix: String = "cov"): String {
        val u = "${prefix}_${System.nanoTime()}"
        val resp =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$u","email":"$u@test.com","password":"password123"}"""),
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
        val libId = Json.mapper.readTree(libResp.bodyString()).get("id").asText()
        val bookResp =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Dune","author":"Frank Herbert","description":null,"libraryId":"$libId"}"""),
            )
        val bookId = Json.mapper.readTree(bookResp.bodyString()).get("id").asText()
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
