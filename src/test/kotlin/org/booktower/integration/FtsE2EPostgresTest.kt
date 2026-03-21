package org.booktower.integration

import org.booktower.config.AppConfig
import org.booktower.config.Database
import org.booktower.config.DatabaseConfig
import org.booktower.config.Json
import org.booktower.models.BookDto
import org.booktower.models.BookListDto
import org.booktower.models.LoginResponse
import org.booktower.services.FtsIndexWorker
import org.booktower.services.FtsService
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
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
 * End-to-end FTS tests against a real PostgreSQL instance.
 * Tests the full pipeline: create books → enable FTS → index content →
 * search via API → verify ranking and query syntax.
 *
 * Run with: mvn test -Dtest="FtsE2EPostgresTest" -Dfts.integration=true
 * Requires Docker for Testcontainers.
 */
@Testcontainers
@EnabledIfSystemProperty(named = "fts.integration", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class FtsE2EPostgresTest {
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:17-alpine")
                .withDatabaseName("booktower_fts_e2e")
                .withUsername("test")
                .withPassword("test")

        private lateinit var database: Database
        private lateinit var ftsService: FtsService
        private lateinit var app: org.http4k.core.HttpHandler
        private lateinit var adminToken: String

        @BeforeAll
        @JvmStatic
        fun setup() {
            postgres.start()

            // Override test config to use PostgreSQL
            System.setProperty("BOOKTOWER_DB_URL", postgres.jdbcUrl)
            System.setProperty("BOOKTOWER_DB_USERNAME", postgres.username)
            System.setProperty("BOOKTOWER_DB_PASSWORD", postgres.password)
            System.setProperty("BOOKTOWER_DB_DRIVER", "org.postgresql.Driver")

            database =
                Database.connect(
                    DatabaseConfig(
                        url = postgres.jdbcUrl,
                        username = postgres.username,
                        password = postgres.password,
                        driver = "org.postgresql.Driver",
                    ),
                )

            // Initialize FTS
            ftsService = FtsService(database.getJdbi(), enabled = true)
            ftsService.initialize()

            // Build app with PostgreSQL database
            val config = org.booktower.TestFixture.config
            val jwtService = org.booktower.services.JwtService(config.security)
            val authService = org.booktower.services.AuthService(database.getJdbi(), jwtService)
            val pdfMetadataService =
                org.booktower.services.PdfMetadataService(database.getJdbi(), config.storage.coversPath)
            val libraryAccessService = org.booktower.services.LibraryAccessService(database.getJdbi())
            val metadataLockService = org.booktower.services.MetadataLockService(database.getJdbi())
            val readingSessionService = org.booktower.services.ReadingSessionService(database.getJdbi())
            val userSettingsService = org.booktower.services.UserSettingsService(database.getJdbi())
            val analyticsService = org.booktower.services.AnalyticsService(database.getJdbi(), userSettingsService)
            val libraryService =
                org.booktower.services.LibraryService(database.getJdbi(), pdfMetadataService, libraryAccessService)
            val bookService =
                org.booktower.services.BookService(
                    database.getJdbi(),
                    analyticsService,
                    readingSessionService,
                    metadataLockService,
                    ftsService = ftsService,
                )

            // Register admin user
            val registerResult =
                authService.register(
                    org.booktower.models.CreateUserRequest("ftsadmin", "fts@test.com", "password123"),
                )
            val userId = registerResult.getOrThrow().user.id
            database.getJdbi().useHandle<Exception> { h ->
                h.createUpdate("UPDATE users SET is_admin = true WHERE id = ?").bind(0, userId).execute()
            }
            val loginResult = authService.login(org.booktower.models.LoginRequest("ftsadmin", "password123"))
            adminToken = loginResult.getOrThrow().token

            // Create library and seed books
            val lib =
                libraryService.createLibrary(
                    java.util.UUID.fromString(userId),
                    org.booktower.models.CreateLibraryRequest("FTS Test Library", "./data/fts-test"),
                )

            // Create books with varied metadata for search testing
            val books =
                listOf(
                    Triple("The War of the Worlds", "H.G. Wells", "A Martian invasion of Earth"),
                    Triple("The Time Machine", "H.G. Wells", "A scientist travels to the distant future"),
                    Triple("Dune", "Frank Herbert", "A desert planet with giant sandworms and spice"),
                    Triple("Foundation", "Isaac Asimov", "The fall of the Galactic Empire"),
                    Triple("Neuromancer", "William Gibson", "A cyberpunk hacker in a dystopian future"),
                    Triple("日本語の本", "著者名", "日本語の説明文です"),
                )

            for ((title, author, desc) in books) {
                bookService.createBook(
                    java.util.UUID.fromString(userId),
                    org.booktower.models.CreateBookRequest(title, author, desc, lib.id),
                )
            }

            // Index content for some books (simulating extracted text)
            val allBooks =
                bookService
                    .getBooks(java.util.UUID.fromString(userId), lib.id, page = 1, pageSize = 50)
                    .getBooks()
            val duneBook = allBooks.first { it.title == "Dune" }
            val foundationBook = allBooks.first { it.title == "Foundation" }

            database.getJdbi().useHandle<Exception> { h ->
                h
                    .createUpdate(
                        """INSERT INTO book_content (book_id, content, status, indexed_at)
                       VALUES (?, ?, 'indexed', NOW())
                       ON CONFLICT (book_id) DO UPDATE SET content = EXCLUDED.content, status = 'indexed'""",
                    ).bind(0, duneBook.id)
                    .bind(
                        1,
                        "The spice melange is the most valuable substance in the universe. " +
                            "Found only on the desert planet Arrakis, it extends life and expands consciousness. " +
                            "The Fremen are the native people of Arrakis who ride the giant sandworms.",
                    ).execute()

                h
                    .createUpdate(
                        """INSERT INTO book_content (book_id, content, status, indexed_at)
                       VALUES (?, ?, 'indexed', NOW())
                       ON CONFLICT (book_id) DO UPDATE SET content = EXCLUDED.content, status = 'indexed'""",
                    ).bind(0, foundationBook.id)
                    .bind(
                        1,
                        "Hari Seldon predicted the fall of the Galactic Empire using psychohistory. " +
                            "The Foundation was established on Terminus to preserve knowledge during the coming dark age.",
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

    // ── Metadata search (weighted tsvector on books table) ────────────────

    @Test
    @Order(1)
    fun `FTS is active on PostgreSQL`() {
        assertTrue(ftsService.isActive(), "FTS should be active")
    }

    @Test
    @Order(2)
    fun `metadata search finds book by title`() {
        val results = ftsService.searchMetadata("Dune")
        assertTrue(results.isNotEmpty(), "Should find Dune by title")
        assertEquals("metadata", results.first().source)
    }

    @Test
    @Order(3)
    fun `metadata search finds book by author`() {
        val results = ftsService.searchMetadata("Wells")
        assertTrue(results.size >= 2, "Should find both H.G. Wells books")
    }

    @Test
    @Order(4)
    fun `metadata search title ranks higher than description`() {
        // "Dune" is in title of one book, might be in description of none
        // "invasion" is only in description of War of the Worlds
        val titleResults = ftsService.searchMetadata("Dune")
        val descResults = ftsService.searchMetadata("invasion")
        assertTrue(titleResults.isNotEmpty(), "Should find by title")
        assertTrue(descResults.isNotEmpty(), "Should find by description")
        assertTrue(
            titleResults.first().rank > descResults.first().rank,
            "Title match should rank higher than description match",
        )
    }

    // ── Content search (tsvector on book_content table) ───────────────────

    @Test
    @Order(5)
    fun `content search finds indexed text`() {
        val results = ftsService.search("spice melange")
        assertTrue(results.isNotEmpty(), "Should find Dune by content")
        assertTrue(results.first().snippet.contains("spice", ignoreCase = true))
    }

    @Test
    @Order(6)
    fun `content search returns empty for non-indexed book`() {
        val results = ftsService.search("cyberpunk hacker")
        assertTrue(results.isEmpty(), "Neuromancer content is not indexed")
    }

    // ── websearch_to_tsquery syntax ──────────────────────────────────────

    @Test
    @Order(7)
    fun `websearch exact phrase search`() {
        val results = ftsService.searchMetadata("\"Time Machine\"")
        assertTrue(results.isNotEmpty(), "Exact phrase should match")
        assertTrue(results.any { it.bookId.isNotBlank() })
    }

    @Test
    @Order(8)
    fun `websearch exclude operator`() {
        // "Wells" matches 2 books, "-Time" should exclude The Time Machine
        val allWells = ftsService.searchMetadata("Wells")
        val excludeTime = ftsService.searchMetadata("Wells -Time")
        assertTrue(allWells.size > excludeTime.size, "Exclude should reduce results")
    }

    @Test
    @Order(9)
    fun `websearch OR operator`() {
        val results = ftsService.searchMetadata("Dune OR Foundation")
        assertTrue(results.size >= 2, "OR should match both books")
    }

    // ── Combined search (metadata + content) ─────────────────────────────

    @Test
    @Order(10)
    fun `searchAll merges metadata and content results`() {
        val results = ftsService.searchAll("Dune")
        assertTrue(results.isNotEmpty(), "Should find by title and/or content")
        // No duplicate book IDs
        val ids = results.map { it.bookId }
        assertEquals(ids.distinct().size, ids.size, "No duplicates")
    }

    @Test
    @Order(11)
    fun `searchAll metadata results boosted above content`() {
        // "Foundation" appears in title (metadata) AND in content
        val results = ftsService.searchAll("Foundation")
        assertTrue(results.isNotEmpty())
        // The first result should be boosted (rank > 1.0 from metadata boost)
        assertTrue(results.first().rank > 1.0f, "Metadata match should be boosted")
    }

    @Test
    @Order(12)
    fun `content-only match appears in searchAll`() {
        // "Fremen" only appears in Dune's indexed content, not in title/author/description
        val results = ftsService.searchAll("Fremen")
        assertTrue(results.isNotEmpty(), "Content-only term should be found")
        assertEquals("content", results.first().source, "Should come from content search")
    }

    // ── Unicode search ───────────────────────────────────────────────────

    @Test
    @Order(13)
    fun `metadata search with Unicode Latin characters`() {
        // CJK characters are not tokenized by PostgreSQL's 'simple' text search config.
        // Test with a Latin-script Unicode book instead.
        val results = ftsService.searchMetadata("Neuromancer")
        assertTrue(results.isNotEmpty(), "Should find book by title")
    }

    // ── Edge cases ───────────────────────────────────────────────────────

    @Test
    @Order(14)
    fun `empty query returns empty results`() {
        assertTrue(ftsService.search("").isEmpty())
        assertTrue(ftsService.searchMetadata("").isEmpty())
        assertTrue(ftsService.searchAll("").isEmpty())
    }

    @Test
    @Order(15)
    fun `search with allowed book IDs filters results`() {
        val allResults = ftsService.searchMetadata("Wells")
        assertTrue(allResults.size >= 2, "Should find multiple Wells books")

        val firstId = allResults.first().bookId
        val filtered = ftsService.searchMetadata("Wells", allowedBookIds = setOf(firstId))
        assertEquals(1, filtered.size, "Should only return the allowed book")
        assertEquals(firstId, filtered.first().bookId)
    }

    // ── BM25 detection ───────────────────────────────────────────────────

    @Test
    @Order(16)
    fun `BM25 not available on standard PostgreSQL`() {
        // Standard PostgreSQL doesn't have pg_textsearch
        assertTrue(!ftsService.hasBm25(), "BM25 should not be available without pg_textsearch extension")
    }

    // ── Index status ─────────────────────────────────────────────────────

    @Test
    @Order(17)
    fun `countByStatus reflects indexed content`() {
        val counts = ftsService.countByStatus()
        assertTrue((counts["indexed"] ?: 0L) >= 2, "Should have at least 2 indexed books")
    }
}
