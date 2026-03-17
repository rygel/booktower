package org.booktower.integration

import org.booktower.config.Database
import org.booktower.config.DatabaseConfig
import org.booktower.services.BackgroundTaskService
import org.booktower.services.FtsIndexWorker
import org.booktower.services.FtsService
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
 * FTS integration tests require a real PostgreSQL instance.
 * Run with: mvn test -Dtest="FtsIntegrationTest" -Dfts.integration=true
 * (Docker must be available for Testcontainers.)
 */
@Testcontainers
@EnabledIfSystemProperty(named = "fts.integration", matches = "true")
class FtsIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("booktower_fts_test")
            .withUsername("test")
            .withPassword("test")

        private lateinit var database: Database
        private lateinit var ftsService: FtsService
        private lateinit var worker: FtsIndexWorker

        @BeforeAll
        @JvmStatic
        fun setup() {
            postgres.start()
            database = Database.connect(
                DatabaseConfig(
                    url      = postgres.jdbcUrl,
                    username = postgres.username,
                    password = postgres.password,
                    driver   = "org.postgresql.Driver",
                ),
            )
            ftsService = FtsService(database.getJdbi(), enabled = true)
            ftsService.initialize()
            assertTrue(ftsService.isActive(), "FTS should be active on PostgreSQL")

            worker = FtsIndexWorker(
                ftsService            = ftsService,
                backgroundTaskService = BackgroundTaskService(),
                throttleMs            = 0,
            )

            // Seed a test book row (books table required by FK)
            database.getJdbi().useHandle<Exception> { h ->
                h.execute(
                    """
                    CREATE TABLE IF NOT EXISTS users (
                        id CHAR(36) PRIMARY KEY, username VARCHAR(100), email VARCHAR(200),
                        password_hash VARCHAR(255), is_admin BOOLEAN DEFAULT FALSE,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """,
                )
                h.execute(
                    """
                    CREATE TABLE IF NOT EXISTS libraries (
                        id CHAR(36) PRIMARY KEY, user_id CHAR(36), name VARCHAR(200), path VARCHAR(500),
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """,
                )
                h.execute(
                    """
                    CREATE TABLE IF NOT EXISTS books (
                        id CHAR(36) PRIMARY KEY, library_id CHAR(36), title VARCHAR(500),
                        author VARCHAR(500), description TEXT, file_path VARCHAR(1000),
                        file_size BIGINT, added_at TIMESTAMP, updated_at TIMESTAMP
                    )
                    """,
                )
                val userId  = UUID.randomUUID().toString()
                val libId   = UUID.randomUUID().toString()
                h.execute("INSERT INTO users (id, username, email, password_hash) VALUES ('$userId','t','t@t','x')")
                h.execute("INSERT INTO libraries (id, user_id, name, path) VALUES ('$libId','$userId','L','/tmp')")
                h.execute("INSERT INTO books (id, library_id, title, file_path, file_size, added_at, updated_at) VALUES ('book-001','$libId','Dune','/tmp/dune.epub',1000,NOW(),NOW())")
                h.execute("INSERT INTO books (id, library_id, title, file_path, file_size, added_at, updated_at) VALUES ('book-002','$libId','Foundation','/tmp/foundation.epub',1000,NOW(),NOW())")
            }
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
        // Index directly (bypassing file I/O)
        database.getJdbi().useHandle<Exception> { h ->
            h.execute(
                "INSERT INTO book_content (book_id, content, status, indexed_at) " +
                "VALUES ('book-001', 'The spice must flow on the desert planet Arrakis', 'pending', NOW()) " +
                "ON CONFLICT (book_id) DO UPDATE SET content = EXCLUDED.content, status = 'pending'",
            )
            h.execute(
                "INSERT INTO book_content (book_id, content, status, indexed_at) " +
                "VALUES ('book-002', 'The Galactic Empire spans a million worlds of the galaxy', 'pending', NOW()) " +
                "ON CONFLICT (book_id) DO UPDATE SET content = EXCLUDED.content, status = 'pending'",
            )
            // Manually trigger tsvector update by setting status to indexed
            h.execute("UPDATE book_content SET status = 'indexed' WHERE book_id IN ('book-001','book-002')")
        }

        val spiceResults = ftsService.search("spice")
        assertEquals(1, spiceResults.size, "Should find 1 book about spice")
        assertEquals("book-001", spiceResults.first().bookId)
        assertTrue(spiceResults.first().snippet.contains("spice", ignoreCase = true))

        val galaxyResults = ftsService.search("galaxy empire")
        assertEquals(1, galaxyResults.size, "Should find 1 book about the galaxy")
        assertEquals("book-002", galaxyResults.first().bookId)
    }

    @Test
    fun `search returns empty for no match`() {
        val results = ftsService.search("xyzzy_no_such_word_anywhere")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `search with allowed book ids filters results`() {
        val results = ftsService.search("spice", allowedBookIds = setOf("book-002"))
        assertTrue(results.isEmpty(), "book-001 is not in allowedBookIds, should not be returned")
    }

    @Test
    fun `enqueue and fetchPending round trip`() {
        // Use a book that is not yet in book_content
        database.getJdbi().useHandle<Exception> { h ->
            h.execute("DELETE FROM book_content WHERE book_id = 'book-001'")
        }
        ftsService.enqueue("book-001")
        val pending = ftsService.fetchPending(10)
        assertTrue(pending.any { it.bookId == "book-001" })
    }

    @Test
    fun `countByStatus returns correct counts`() {
        database.getJdbi().useHandle<Exception> { h ->
            h.execute("DELETE FROM book_content")
            h.execute("INSERT INTO book_content (book_id, status) VALUES ('book-001', 'pending')")
            h.execute("INSERT INTO book_content (book_id, status) VALUES ('book-002', 'indexed')")
        }
        val counts = ftsService.countByStatus()
        assertEquals(1L, counts["pending"])
        assertEquals(1L, counts["indexed"])
    }
}
