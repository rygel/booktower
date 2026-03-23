package org.booktower.integration

import org.booktower.config.Database
import org.booktower.config.DatabaseConfig
import org.booktower.config.Json
import org.booktower.filters.DemoModeFilter
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
import org.http4k.core.Body
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Full HTTP-level E2E test for FTS against real PostgreSQL.
 * Tests the complete pipeline with NO faking:
 *   1. Upload a real EPUB via HTTP
 *   2. FtsIndexWorker extracts text from the EPUB
 *   3. Search via /api/search finds content from inside the book
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
            PostgreSQLContainer("postgres:17.4-alpine")
                .withDatabaseName("booktower_fts_http")
                .withUsername("test")
                .withPassword("test")

        private lateinit var database: Database
        private lateinit var ftsService: FtsService
        private lateinit var ftsWorker: FtsIndexWorker
        private lateinit var app: HttpHandler
        private lateinit var token: String
        private lateinit var epubBookId: String
        private lateinit var noFileBookId: String
        private lateinit var pdfBookId: String

        /** Creates a valid PDF with searchable text using Apache PDFBox. */
        private fun pdfWithContent(text: String): ByteArray {
            val baos = ByteArrayOutputStream()
            org.apache.pdfbox.pdmodel.PDDocument().use { doc ->
                val page =
                    org.apache.pdfbox.pdmodel
                        .PDPage()
                doc.addPage(page)
                org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(
                        org.apache.pdfbox.pdmodel.font.PDType1Font(
                            org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA,
                        ),
                        12f,
                    )
                    cs.newLineAtOffset(50f, 700f)
                    // Split long text into lines
                    text.chunked(80).forEach { line ->
                        cs.showText(line)
                        cs.newLineAtOffset(0f, -15f)
                    }
                    cs.endText()
                }
                doc.save(baos)
            }
            return baos.toByteArray()
        }

        /** Builds a valid EPUB with known searchable content. */
        private fun epubWithContent(
            title: String,
            bodyText: String,
        ): ByteArray {
            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { zip ->
                zip.setMethod(ZipOutputStream.STORED)
                val mimeBytes = "application/epub+zip".toByteArray()
                val mimeEntry = ZipEntry("mimetype")
                mimeEntry.size = mimeBytes.size.toLong()
                mimeEntry.compressedSize = mimeBytes.size.toLong()
                mimeEntry.crc = CRC32().also { it.update(mimeBytes) }.value
                zip.putNextEntry(mimeEntry)
                zip.write(mimeBytes)
                zip.closeEntry()

                zip.setMethod(ZipOutputStream.DEFLATED)
                zip.putNextEntry(ZipEntry("META-INF/container.xml"))
                zip.write(
                    """<?xml version="1.0"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                    </container>""".toByteArray(),
                )
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
                zip.write(
                    """<?xml version="1.0" encoding="utf-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>$title</dc:title>
                        <dc:identifier id="id">fts-test-001</dc:identifier>
                      </metadata>
                      <manifest>
                        <item id="ch1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                        <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                      </manifest>
                      <spine toc="ncx"><itemref idref="ch1"/></spine>
                    </package>""".toByteArray(),
                )
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("OEBPS/chapter1.xhtml"))
                zip.write(
                    """<?xml version="1.0" encoding="utf-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <head><title>$title</title></head>
                      <body><p>$bodyText</p></body>
                    </html>""".toByteArray(),
                )
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
                zip.write(
                    """<?xml version="1.0" encoding="utf-8"?>
                    <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                      <head><meta name="dtb:uid" content="fts-test-001"/></head>
                      <docTitle><text>$title</text></docTitle>
                      <navMap><navPoint id="np1" playOrder="1">
                        <navLabel><text>Chapter One</text></navLabel>
                        <content src="chapter1.xhtml"/>
                      </navPoint></navMap>
                    </ncx>""".toByteArray(),
                )
                zip.closeEntry()
            }
            return baos.toByteArray()
        }

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

            // Core services
            val jwtService = JwtService(config.security)
            val authService = AuthService(jdbi, jwtService)
            val pdfMetadataService = PdfMetadataService(jdbi, config.storage.coversPath)
            val libraryAccessService = LibraryAccessService(jdbi)
            val metadataLockService = MetadataLockService(jdbi)
            val readingSessionService = ReadingSessionService(jdbi)
            val userSettingsService = UserSettingsService(jdbi)
            val analyticsService = AnalyticsService(jdbi, userSettingsService)

            ftsService = FtsService(jdbi, enabled = true)
            ftsService.initialize()

            val libraryService = LibraryService(jdbi, pdfMetadataService, libraryAccessService, ftsService = ftsService)
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
            val bookSharingService = BookSharingService(jdbi, bookService)

            // Handlers — FileHandler gets ftsService for enqueue on upload
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
                    ftsService = ftsService,
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
                    null, // libraryWatchService
                    null, // bookLinkService
                    null, // bookSharingService
                    backgroundTaskService,
                    null, // libraryStatsService
                    null, // webhookService
                    null, // readingTimelineService
                    null, // discoveryService
                    null, // readingListService
                    null, // wishlistService
                    null, // collectionService
                    null, // koboSyncService
                    null, // koreaderSyncService
                    null, // filterPresetService
                    null, // scheduledTaskService
                    null, // opdsCredentialsService
                    null, // contentRestrictionsService
                    null, // readingSpeedService
                    null, // libraryHealthService
                    null, // hardcoverSyncService
                    null, // bookDeliveryService
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
                org.booktower.routers.PageRouter(filters, pageHandler, adminHandler, jwtService, templateRenderer, true)
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
                    null,
                    null,
                    null,
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
            app = globalErrorFilter().then(DemoModeFilter(false)).then(appHandler.routes())

            // Start FTS index worker with fast polling for tests
            ftsWorker = FtsIndexWorker(ftsService, backgroundTaskService, throttleMs = 0, pollIntervalMs = 200)
            ftsWorker.start()

            // Register user
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

            // Create book and upload EPUB with unique searchable content
            val createResp =
                app(
                    Request(Method.POST, "/api/books")
                        .header("Cookie", "token=$token")
                        .header("Content-Type", "application/json")
                        .body("""{"title":"Placeholder Title","author":"Test Author","description":"A test book","libraryId":"$libId"}"""),
                )
            epubBookId = Json.mapper.readValue(createResp.bodyString(), BookDto::class.java).id

            // Upload EPUB — this triggers FTS enqueue via FileHandler
            val epubBytes =
                epubWithContent(
                    "The Quantum Butterfly Effect",
                    "Professor Elara Moonwhisper discovered that quantum entanglement " +
                        "could be harnessed through crystalline metamaterials to create " +
                        "instantaneous communication across galactic distances. Her research " +
                        "at the Zephyrion Institute revolutionized interstellar travel.",
                )
            app(
                Request(Method.POST, "/api/books/$epubBookId/upload")
                    .header("Cookie", "token=$token")
                    .header("X-Filename", "quantum-butterfly.epub")
                    .header("Content-Type", "application/octet-stream")
                    .body(Body(ByteArrayInputStream(epubBytes), epubBytes.size.toLong())),
            )

            // Create and upload a PDF with unique searchable content
            val pdfCreateResp =
                app(
                    Request(Method.POST, "/api/books")
                        .header("Cookie", "token=$token")
                        .header("Content-Type", "application/json")
                        .body("""{"title":"PDF Placeholder","author":"PDF Author","description":"A PDF test","libraryId":"$libId"}"""),
                )
            pdfBookId = Json.mapper.readValue(pdfCreateResp.bodyString(), BookDto::class.java).id

            val pdfBytes =
                pdfWithContent(
                    "The Obsidian Archipelago was home to the Velanthor civilization. " +
                        "Their chromatic lightships traversed the nebulae using prismatic sails " +
                        "powered by harvested starlight from the Korynthian binary system.",
                )
            app(
                Request(Method.POST, "/api/books/$pdfBookId/upload")
                    .header("Cookie", "token=$token")
                    .header("X-Filename", "obsidian-archipelago.pdf")
                    .header("Content-Type", "application/octet-stream")
                    .body(Body(ByteArrayInputStream(pdfBytes), pdfBytes.size.toLong())),
            )

            // Create another book WITHOUT a file (for comparison)
            val noFileResp =
                app(
                    Request(Method.POST, "/api/books")
                        .header("Cookie", "token=$token")
                        .header("Content-Type", "application/json")
                        .body("""{"title":"Unrelated Book","author":"Other Author","description":"Nothing about quantum","libraryId":"$libId"}"""),
                )
            noFileBookId = Json.mapper.readValue(noFileResp.bodyString(), BookDto::class.java).id

            // Wait for FTS indexer to process both EPUB and PDF
            val deadline = System.currentTimeMillis() + 15_000
            while (System.currentTimeMillis() < deadline) {
                val counts = ftsService.countByStatus()
                if ((counts["indexed"] ?: 0L) >= 2) break
                Thread.sleep(500)
            }
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            ftsWorker.stop()
            database.close()
            postgres.stop()
        }
    }

    private fun search(query: String): BookListDto {
        val resp =
            app(
                Request(Method.GET, "/api/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, resp.status, "Search should return 200 for query: $query")
        return Json.mapper.readValue(resp.bodyString(), BookListDto::class.java)
    }

    // ── Real EPUB content extraction + search ────────────────────────────

    @Test
    @Order(1)
    fun `FTS indexed both EPUB and PDF`() {
        val counts = ftsService.countByStatus()
        assertTrue((counts["indexed"] ?: 0L) >= 2, "Both EPUB and PDF should be indexed: $counts")
    }

    @Test
    @Order(2)
    fun `search finds book by content extracted from EPUB`() {
        // "Moonwhisper" only exists inside the EPUB chapter text
        val results = search("Moonwhisper")
        assertTrue(results.total > 0, "Should find book by content extracted from uploaded EPUB")
        assertTrue(
            results.getBooks().any { it.id == epubBookId },
            "The uploaded EPUB book should be in results",
        )
    }

    @Test
    @Order(3)
    fun `search finds book by another content-only term`() {
        // "Zephyrion" only exists inside the EPUB
        val results = search("Zephyrion")
        assertTrue(results.total > 0, "Should find by 'Zephyrion' from EPUB content")
        assertTrue(results.getBooks().any { it.id == epubBookId })
    }

    @Test
    @Order(4)
    fun `search finds book by metadata title`() {
        // EPUB metadata extraction updates the title to "The Quantum Butterfly Effect"
        val results = search("Quantum Butterfly")
        assertTrue(results.total > 0, "Should find by title from EPUB metadata")
    }

    @Test
    @Order(5)
    fun `content-only term does not match unrelated book`() {
        val results = search("Moonwhisper")
        val ids = results.getBooks().map { it.id }
        assertTrue(noFileBookId !in ids, "Unrelated book without EPUB should not appear")
    }

    @Test
    @Order(6)
    fun `search by description keyword works via metadata FTS`() {
        val results = search("quantum")
        assertTrue(results.total > 0, "Should find by 'quantum' in title or content")
    }

    // ── Edge cases ──────────────────────────────────────────────────────

    @Test
    @Order(7)
    fun `search for nonsense returns empty`() {
        val results = search("xyzzy_completely_nonexistent_word")
        assertEquals(0, results.total)
    }

    @Test
    @Order(8)
    fun `empty query returns 400`() {
        val resp =
            app(Request(Method.GET, "/api/search?q=").header("Cookie", "token=$token"))
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    @Order(9)
    fun `unauthenticated search returns 401`() {
        val resp = app(Request(Method.GET, "/api/search?q=test"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    @Order(10)
    fun `no duplicate books in combined search results`() {
        // "quantum" might match title metadata AND EPUB content
        val results = search("quantum")
        val ids = results.getBooks().map { it.id }
        assertEquals(ids.distinct().size, ids.size, "No duplicate book IDs")
    }

    // ── Real PDF content extraction + search ─────────────────────────────

    @Test
    @Order(11)
    fun `search finds PDF book by extracted content`() {
        // "Velanthor" only exists inside the PDF text
        val results = search("Velanthor")
        assertTrue(results.total > 0, "Should find PDF book by extracted content")
        assertTrue(
            results.getBooks().any { it.id == pdfBookId },
            "The uploaded PDF book should be in results",
        )
    }

    @Test
    @Order(12)
    fun `search finds PDF by another content-only term`() {
        // "Korynthian" only exists inside the PDF
        val results = search("Korynthian")
        assertTrue(results.total > 0, "Should find by 'Korynthian' from PDF content")
        assertTrue(results.getBooks().any { it.id == pdfBookId })
    }

    @Test
    @Order(13)
    fun `PDF content term does not match EPUB book`() {
        // "Velanthor" is only in the PDF, not the EPUB
        val results = search("Velanthor")
        assertTrue(
            results.getBooks().none { it.id == epubBookId },
            "EPUB book should not match PDF-only content",
        )
    }

    @Test
    @Order(14)
    fun `EPUB content term does not match PDF book`() {
        // "Moonwhisper" is only in the EPUB, not the PDF
        val results = search("Moonwhisper")
        assertTrue(
            results.getBooks().none { it.id == pdfBookId },
            "PDF book should not match EPUB-only content",
        )
    }
}
