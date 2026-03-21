package org.booktower.integration

import org.booktower.config.Database
import org.booktower.config.DatabaseConfig
import org.booktower.models.CreateBookRequest
import org.booktower.models.CreateLibraryRequest
import org.booktower.models.CreateUserRequest
import org.booktower.services.AuthService
import org.booktower.services.BackgroundTaskService
import org.booktower.services.BookService
import org.booktower.services.FtsIndexWorker
import org.booktower.services.FtsService
import org.booktower.services.JwtService
import org.booktower.services.LibraryAccessService
import org.booktower.services.LibraryService
import org.booktower.services.PdfMetadataService
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

/**
 * FTS service-level tests against real PostgreSQL.
 * Tests FtsService internals: enqueue, fetchPending, countByStatus, search.
 *
 * Uses Database.connect() which runs Flyway migrations automatically.
 * Test data created via service layer (AuthService, LibraryService, BookService).
 *
 * Run with: mvn test -Dtest="FtsServiceTest" -Dfts.integration=true
 * Requires Docker for Testcontainers.
 */
@Testcontainers
@EnabledIfSystemProperty(named = "fts.integration", matches = "true")
class FtsServiceTest {
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:17-alpine")
                .withDatabaseName("booktower_fts_test")
                .withUsername("test")
                .withPassword("test")

        private lateinit var database: Database
        private lateinit var ftsService: FtsService
        private lateinit var worker: FtsIndexWorker
        private lateinit var book1Id: String
        private lateinit var book2Id: String

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

            ftsService = FtsService(jdbi, enabled = true)
            ftsService.initialize()
            assertTrue(ftsService.isActive(), "FTS should be active on PostgreSQL")

            worker =
                FtsIndexWorker(
                    ftsService = ftsService,
                    backgroundTaskService = BackgroundTaskService(),
                    throttleMs = 0,
                )

            // Create test data via services (not raw SQL)
            val jwtService = JwtService(config.security)
            val authService = AuthService(jdbi, jwtService)
            val pdfMetadataService = PdfMetadataService(jdbi, config.storage.coversPath)
            val libraryAccessService = LibraryAccessService(jdbi)
            val libraryService = LibraryService(jdbi, pdfMetadataService, libraryAccessService)
            val bookService = BookService(jdbi)

            val userResult = authService.register(CreateUserRequest("ftstest", "fts@test.com", "password123"))
            val userId = UUID.fromString(userResult.getOrThrow().user.id)

            val lib = libraryService.createLibrary(userId, CreateLibraryRequest("FTS Test Lib", "/tmp/fts"))

            val book1 =
                bookService.createBook(userId, CreateBookRequest("Dune", "Frank Herbert", "Desert planet", lib.id))
            book1Id = book1.getOrThrow().id

            val book2 =
                bookService.createBook(userId, CreateBookRequest("Foundation", "Isaac Asimov", "Galactic Empire", lib.id))
            book2Id = book2.getOrThrow().id
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            database.close()
            postgres.stop()
        }
    }

    @Test
    fun `indexed content is found by full-text search`() {
        // Index content via the service (simulating what FtsIndexWorker does after extraction)
        database.getJdbi().useHandle<Exception> { h ->
            h
                .createUpdate(
                    """INSERT INTO book_content (book_id, content, status, indexed_at)
                   VALUES (?, ?, 'indexed', NOW())
                   ON CONFLICT (book_id) DO UPDATE SET content = EXCLUDED.content, status = 'indexed'""",
                ).bind(0, book1Id)
                .bind(1, "The spice must flow on the desert planet Arrakis")
                .execute()
            h
                .createUpdate(
                    """INSERT INTO book_content (book_id, content, status, indexed_at)
                   VALUES (?, ?, 'indexed', NOW())
                   ON CONFLICT (book_id) DO UPDATE SET content = EXCLUDED.content, status = 'indexed'""",
                ).bind(0, book2Id)
                .bind(1, "The Galactic Empire spans a million worlds of the galaxy")
                .execute()
        }

        val spiceResults = ftsService.search("spice")
        assertEquals(1, spiceResults.size, "Should find 1 book about spice")
        assertEquals(book1Id, spiceResults.first().bookId)
        assertTrue(spiceResults.first().snippet.contains("spice", ignoreCase = true))

        val galaxyResults = ftsService.search("galaxy empire")
        assertEquals(1, galaxyResults.size, "Should find 1 book about the galaxy")
        assertEquals(book2Id, galaxyResults.first().bookId)
    }

    @Test
    fun `search returns empty for no match`() {
        val results = ftsService.search("xyzzy_no_such_word_anywhere")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `search with allowed book ids filters results`() {
        val results = ftsService.search("spice", allowedBookIds = setOf(book2Id))
        assertTrue(results.isEmpty(), "book1 is not in allowedBookIds, should not be returned")
    }

    @Test
    fun `enqueue and fetchPending round trip`() {
        database.getJdbi().useHandle<Exception> { h ->
            h.createUpdate("DELETE FROM book_content WHERE book_id = ?").bind(0, book1Id).execute()
        }
        ftsService.enqueue(book1Id)
        val pending = ftsService.fetchPending(10)
        assertTrue(pending.any { it.bookId == book1Id })
    }

    @Test
    fun `countByStatus returns correct counts`() {
        database.getJdbi().useHandle<Exception> { h ->
            h.createUpdate("DELETE FROM book_content").execute()
            h
                .createUpdate("INSERT INTO book_content (book_id, status) VALUES (?, 'pending')")
                .bind(0, book1Id)
                .execute()
            h
                .createUpdate("INSERT INTO book_content (book_id, status) VALUES (?, 'indexed')")
                .bind(0, book2Id)
                .execute()
        }
        val counts = ftsService.countByStatus()
        assertEquals(1L, counts["pending"])
        assertEquals(1L, counts["indexed"])
    }
}
