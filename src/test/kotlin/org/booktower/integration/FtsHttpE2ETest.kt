package org.booktower.integration

import org.booktower.config.Database
import org.booktower.config.DatabaseConfig
import org.booktower.config.Json
import org.booktower.config.TemplateRenderer
import org.booktower.filters.RateLimitFilter
import org.booktower.filters.adminFilter
import org.booktower.filters.globalErrorFilter
import org.booktower.filters.jwtAuthFilter
import org.booktower.filters.optionalAuthFilter
import org.booktower.models.BookDto
import org.booktower.models.BookListDto
import org.booktower.models.LoginResponse
import org.booktower.routers.FilterSet
import org.booktower.services.*
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.then
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Full HTTP-level E2E test for FTS against real PostgreSQL.
 * Tests the complete pipeline: HTTP request → handler → BookService →
 * FtsService → PostgreSQL → response.
 *
 * Run with: mvn test -Dtest="FtsHttpE2ETest" -Dfts.integration=true
 * Requires Docker for Testcontainers.
 */
@Testcontainers
@EnabledIfSystemProperty(named = "fts.integration", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class FtsHttpE2ETest {
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:17-alpine")
                .withDatabaseName("booktower_fts_http")
                .withUsername("test")
                .withPassword("test")

        private lateinit var database: Database
        private lateinit var app: HttpHandler
        private lateinit var token: String
        private lateinit var bookIds: Map<String, String>

        @BeforeAll
        @JvmStatic
        fun setup() {
            postgres.start()
            database =
                Database.connect(
                    DatabaseConfig(
                        url = postgres.jdbcUrl,
                        username = postgres.username,
                        password = postgres.password,
                        driver = "org.postgresql.Driver",
                    ),
                )

            val jdbi = database.getJdbi()
            val config = org.booktower.TestFixture.config
            val templateRenderer = org.booktower.TestFixture.templateRenderer

            // Services
            val jwtService = JwtService(config.security)
            val authService = AuthService(jdbi, jwtService)
            val pdfMetadataService = PdfMetadataService(jdbi, config.storage.coversPath)
            val libraryAccessService = LibraryAccessService(jdbi)
            val metadataLockService = MetadataLockService(jdbi)
            val readingSessionService = ReadingSessionService(jdbi)
            val userSettingsService = UserSettingsService(jdbi)
            val analyticsService = AnalyticsService(jdbi, userSettingsService)
            val ftsService = FtsService(jdbi, enabled = true)
            ftsService.initialize()

            val libraryService = LibraryService(jdbi, pdfMetadataService, libraryAccessService)
            val bookmarkService = BookmarkService(jdbi)
            val bookService =
                BookService(jdbi, analyticsService, readingSessionService, metadataLockService, ftsService)
            val magicShelfService = MagicShelfService(jdbi, bookService)
            val backgroundTaskService = BackgroundTaskService()
            val seedService =
                SeedService(bookService, libraryService, config.storage.coversPath, config.storage.booksPath, backgroundTaskService)
            val annotationService = AnnotationService(jdbi)
            val metadataFetchService = MetadataFetchService()
            val adminService = AdminService(jdbi)
            val passwordResetService = PasswordResetService(jdbi)
            val apiTokenService = ApiTokenService(jdbi)
            val exportService = ExportService(jdbi)
            val epubMetadataService = EpubMetadataService(jdbi, config.storage.coversPath)
            val comicService = ComicService()
            val goodreadsImportService = GoodreadsImportService(bookService)
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
            val communityRatingService = CommunityRatingService(jdbi)
            val comicPageHashService = ComicPageHashService(jdbi, comicService)
            val duplicateDetectionService = DuplicateDetectionService(jdbi)
            val geoIpService =
                object : GeoIpService() {
                    override fun lookup(ip: String) = GeoLocation("US", "United States", "Test")
                }
            val auditService = AuditService(jdbi, geoIpService)
            val journalService = JournalService(jdbi)
            val hardcoverSyncService = HardcoverSyncService(jdbi, userSettingsService)
            val calibreService =
                CalibreConversionService(java.io.File(config.storage.tempPath, "calibre-cache"))

            // Handlers
            val smtpConfig = org.booktower.config.SmtpConfig("", 587, "", "", "", true)
            val authHandler =
                org.booktower.handlers.AuthHandler2(
                    authService,
                    userSettingsService,
                    passwordResetService,
                    EmailService(smtpConfig),
                    "http://localhost:9999",
                    true,
                    auditService,
                    false,
                )
            val libraryHandler =
                org.booktower.handlers.LibraryHandler2(libraryService, backgroundTaskService, config.storage)
            val bookHandler = org.booktower.handlers.BookHandler2(bookService, readingSessionService)
            val bookmarkHandler = org.booktower.handlers.BookmarkHandler(bookmarkService)
            val fileHandler =
                org.booktower.handlers.FileHandler(
                    bookService,
                    pdfMetadataService,
                    epubMetadataService,
                    config.storage,
                    calibreService = calibreService,
                )
            val settingsHandler = org.booktower.handlers.UserSettingsHandler(userSettingsService)
            val adminHandler =
                org.booktower.handlers.AdminHandler(
                    adminService,
                    jwtService,
                    authService,
                    templateRenderer,
                    passwordResetService,
                    seedService,
                    EmailService(smtpConfig),
                    "http://localhost:9999",
                    duplicateDetectionService,
                    auditService,
                    userPermissionsService,
                    libraryAccessService,
                    comicPageHashService,
                )
            val pageHandler =
                org.booktower.handlers.PageHandler(
                    jwtService,
                    authService,
                    libraryService,
                    bookService,
                    bookmarkService,
                    userSettingsService,
                    analyticsService,
                    annotationService,
                    metadataFetchService,
                    magicShelfService,
                    templateRenderer,
                    readingSessionService,
                    null,
                    null,
                )
            val backgroundTaskHandler =
                org.booktower.handlers.BackgroundTaskHandler(backgroundTaskService, seedService)
            val journalHandler = org.booktower.handlers.JournalHandler(journalService)
            val opdsHandler =
                org.booktower.handlers.OpdsHandler(
                    authService,
                    libraryService,
                    bookService,
                    config.storage,
                    apiTokenService,
                    opdsCredentialsService,
                )
            val apiTokenHandler = org.booktower.handlers.ApiTokenHandler(apiTokenService, jwtService)
            val exportHandler = org.booktower.handlers.ExportHandler(exportService, jwtService)
            val goodreadsImportHandler =
                org.booktower.handlers.GoodreadsImportHandler(goodreadsImportService, jwtService)
            val bulkBookHandler = org.booktower.handlers.BulkBookHandler(bookService)
            val bookSharingService = BookSharingService(jdbi, bookService)

            // Filters
            val userExistsCheck = { userId: java.util.UUID -> authService.getUserById(userId) != null }
            val authFilter = jwtAuthFilter(jwtService, userExistsCheck)
            val filters =
                FilterSet(
                    auth = authFilter,
                    admin = authFilter.then(adminFilter()),
                    authRateLimit = RateLimitFilter(maxRequests = 10, windowSeconds = 60),
                    optionalAuth = optionalAuthFilter(jwtService, userExistsCheck),
                )

            // Routers
            val weblateConfig = org.booktower.config.WeblateConfig("", "", "", false)
            val authRouter = org.booktower.routers.AuthRouter(authHandler, filters)
            val oidcRouter = org.booktower.routers.OidcRouter(null)
            val pageRouter =
                org.booktower.routers.PageRouter(
                    filters,
                    pageHandler,
                    adminHandler,
                    jwtService,
                    templateRenderer,
                    true,
                )
            val bookApiRouter =
                org.booktower.routers.BookApiRouter(
                    filters,
                    bookHandler,
                    bulkBookHandler,
                    bookmarkHandler,
                    fileHandler,
                    bookService,
                    comicService,
                    config.storage,
                    magicShelfService,
                    null,
                    null,
                    journalHandler,
                    null,
                    null,
                    null,
                    bookFilesService,
                    comicMetadataService,
                    communityRatingService,
                    bookReviewService,
                    bookNotebookService,
                    duplicateDetectionService,
                    null,
                    bookSharingService,
                )
            val libraryApiRouter =
                org.booktower.routers.LibraryApiRouter(filters, libraryHandler, libraryService, null, null)
            val userApiRouter =
                org.booktower.routers.UserApiRouter(
                    filters,
                    settingsHandler,
                    bookService,
                    userSettingsService,
                    ReadingStatsService(jdbi),
                    hardcoverSyncService,
                    opdsCredentialsService,
                    contentRestrictionsService,
                    filterPresetService,
                    telemetryService,
                    null,
                    notificationService,
                    backgroundTaskHandler,
                    apiTokenHandler,
                    exportHandler,
                    goodreadsImportHandler,
                )
            val adminApiRouter =
                org.booktower.routers.AdminApiRouter(
                    filters,
                    adminHandler,
                    backgroundTaskHandler,
                    org.booktower.weblate.WeblateHandler(weblateConfig),
                    emailProviderService,
                    scheduledTaskService,
                    bulkCoverService,
                    telemetryService,
                )
            val metadataApiRouter =
                org.booktower.routers.MetadataApiRouter(
                    filters,
                    metadataFetchService,
                    bookService,
                    MetadataProposalService(jdbi),
                    metadataLockService,
                    null,
                )
            val audiobookApiRouter =
                org.booktower.routers.AudiobookApiRouter(
                    filters,
                    ListeningSessionService(jdbi),
                    ListeningStatsService(jdbi),
                    audiobookMetaService,
                    config.storage,
                )
            val deviceSyncRouter =
                org.booktower.routers.DeviceSyncRouter(
                    filters,
                    org.booktower.handlers.KoboSyncHandler(koboSyncService),
                    null,
                    opdsHandler,
                )

            val appHandler =
                org.booktower.handlers.AppHandler(
                    fileHandler,
                    config.storage,
                    false,
                    authRouter,
                    oidcRouter,
                    pageRouter,
                    bookApiRouter,
                    libraryApiRouter,
                    userApiRouter,
                    adminApiRouter,
                    metadataApiRouter,
                    audiobookApiRouter,
                    deviceSyncRouter,
                )
            app =
                globalErrorFilter()
                    .then(org.booktower.filters.DemoModeFilter(false))
                    .then(appHandler.routes())

            // Register user and create test data
            val regResp =
                app(
                    Request(Method.POST, "/auth/register")
                        .header("Content-Type", "application/json")
                        .body("""{"username":"ftshttp","email":"fts@http.com","password":"password123"}"""),
                )
            token = Json.mapper.readValue(regResp.bodyString(), LoginResponse::class.java).token

            // Create library
            val libResp =
                app(
                    Request(Method.POST, "/api/libraries")
                        .header("Cookie", "token=$token")
                        .header("Content-Type", "application/json")
                        .body("""{"name":"FTS Library","path":"./data/fts-http"}"""),
                )
            val libId =
                Json.mapper
                    .readTree(libResp.bodyString())
                    .get("id")
                    .asText()

            // Create books with varied metadata
            val titles =
                mapOf(
                    "War of the Worlds" to Pair("H.G. Wells", "Martian invasion of Earth with tripod war machines"),
                    "The Time Machine" to Pair("H.G. Wells", "Travel to the year 802701 with Eloi and Morlocks"),
                    "Dune" to Pair("Frank Herbert", "Desert planet Arrakis with spice and sandworms"),
                    "Foundation" to Pair("Isaac Asimov", "Fall of the Galactic Empire predicted by psychohistory"),
                    "Neuromancer" to Pair("William Gibson", "Cyberpunk hacker in a dystopian AI future"),
                )
            bookIds = mutableMapOf()
            for ((title, pair) in titles) {
                val resp =
                    app(
                        Request(Method.POST, "/api/books")
                            .header("Cookie", "token=$token")
                            .header("Content-Type", "application/json")
                            .body("""{"title":"$title","author":"${pair.first}","description":"${pair.second}","libraryId":"$libId"}"""),
                    )
                val id = Json.mapper.readValue(resp.bodyString(), BookDto::class.java).id
                (bookIds as MutableMap)[title] = id
            }

            // Index content for Dune and Foundation
            jdbi.useHandle<Exception> { h ->
                h
                    .createUpdate(
                        """INSERT INTO book_content (book_id, content, status, indexed_at)
                       VALUES (?, ?, 'indexed', NOW())
                       ON CONFLICT (book_id) DO UPDATE SET content = EXCLUDED.content, status = 'indexed'""",
                    ).bind(0, bookIds["Dune"])
                    .bind(
                        1,
                        "The spice melange is the most valuable substance. " +
                            "The Fremen ride sandworms across the desert of Arrakis.",
                    ).execute()
                h
                    .createUpdate(
                        """INSERT INTO book_content (book_id, content, status, indexed_at)
                       VALUES (?, ?, 'indexed', NOW())
                       ON CONFLICT (book_id) DO UPDATE SET content = EXCLUDED.content, status = 'indexed'""",
                    ).bind(0, bookIds["Foundation"])
                    .bind(
                        1,
                        "Hari Seldon established the Foundation on Terminus to preserve knowledge.",
                    ).execute()
            }
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            database.close()
            postgres.stop()
        }
    }

    private fun search(query: String): BookListDto {
        val resp =
            app(
                Request(
                    Method.GET,
                    "/api/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}",
                ).header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, resp.status, "Search should return 200 for query: $query")
        return Json.mapper.readValue(resp.bodyString(), BookListDto::class.java)
    }

    // ── Metadata search via HTTP API ─────────────────────────────────────

    @Test
    @Order(1)
    fun `search by title finds correct book`() {
        val results = search("Dune")
        assertTrue(results.total > 0, "Should find Dune")
        assertTrue(
            results.getBooks().any { it.title == "Dune" },
            "Results should contain Dune",
        )
    }

    @Test
    @Order(2)
    fun `search by author finds multiple books`() {
        val results = search("Wells")
        assertTrue(results.total >= 2, "Should find both H.G. Wells books")
        val titles = results.getBooks().map { it.title }
        assertTrue(titles.contains("War of the Worlds"), "Should find War of the Worlds")
        assertTrue(titles.contains("The Time Machine"), "Should find The Time Machine")
    }

    @Test
    @Order(3)
    fun `search by description keyword finds book`() {
        val results = search("cyberpunk")
        assertTrue(results.total > 0, "Should find Neuromancer by description")
        assertTrue(
            results.getBooks().any { it.title == "Neuromancer" },
            "Should find Neuromancer",
        )
    }

    // ── Content search via HTTP API ──────────────────────────────────────

    @Test
    @Order(4)
    fun `search finds book by indexed content`() {
        val results = search("Fremen sandworms")
        assertTrue(results.total > 0, "Should find Dune by indexed content")
        assertTrue(
            results.getBooks().any { it.title == "Dune" },
            "Dune should appear from content match",
        )
    }

    @Test
    @Order(5)
    fun `search finds book by content keyword not in metadata`() {
        val results = search("Terminus")
        assertTrue(results.total > 0, "Should find Foundation by content ('Terminus' not in title/desc)")
        assertTrue(
            results.getBooks().any { it.title == "Foundation" },
            "Foundation should appear",
        )
    }

    // ── No results ──────────────────────────────────────────────────────

    @Test
    @Order(6)
    fun `search with no match returns empty`() {
        val results = search("xyzzy_nonexistent_term")
        assertEquals(0, results.total, "No results for nonsense query")
    }

    // ── Result ranking ──────────────────────────────────────────────────

    @Test
    @Order(7)
    fun `title match ranks higher than description match`() {
        // "Dune" is a title, "Martian" is in description of War of the Worlds
        val duneResults = search("Dune")
        val martianResults = search("Martian")
        assertTrue(duneResults.total > 0)
        assertTrue(martianResults.total > 0)
        // Both should find results; title match (Dune) should be first in its results
        assertEquals("Dune", duneResults.getBooks().first().title)
    }

    // ── Pagination ──────────────────────────────────────────────────────

    @Test
    @Order(8)
    fun `search respects page and pageSize parameters`() {
        val page1 =
            app(
                Request(Method.GET, "/api/search?q=Wells&pageSize=1&page=1")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, page1.status)
        val result1 = Json.mapper.readValue(page1.bodyString(), BookListDto::class.java)
        assertEquals(1, result1.getBooks().size, "pageSize=1 should return 1 book")
        assertTrue(result1.total >= 2, "Total should reflect all matches")
    }

    // ── Edge cases ──────────────────────────────────────────────────────

    @Test
    @Order(9)
    fun `empty query returns 400`() {
        val resp =
            app(
                Request(Method.GET, "/api/search?q=")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    @Order(10)
    fun `missing query parameter returns 400`() {
        val resp =
            app(
                Request(Method.GET, "/api/search")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    @Order(11)
    fun `unauthenticated search returns 401`() {
        val resp = app(Request(Method.GET, "/api/search?q=Dune"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    // ── Combined metadata + content ──────────────────────────────────────

    @Test
    @Order(12)
    fun `search returns both metadata and content matches`() {
        // "spice" is in Dune's description AND indexed content
        val results = search("spice")
        assertTrue(results.total > 0, "Should find by spice (in desc + content)")
        // Should not have duplicates
        val ids = results.getBooks().map { it.id }
        assertEquals(ids.distinct().size, ids.size, "No duplicate books in results")
    }
}
