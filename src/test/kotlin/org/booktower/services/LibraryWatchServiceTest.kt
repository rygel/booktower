package org.booktower.services

import org.booktower.TestFixture
import org.booktower.models.CreateLibraryRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LibraryWatchServiceTest {
    private val jdbi = TestFixture.database.getJdbi()
    private val config = TestFixture.config
    private val pdfMetadataService = PdfMetadataService(jdbi, config.storage.coversPath)
    private val libraryService = LibraryService(jdbi, pdfMetadataService)
    private val watchService = LibraryWatchService(jdbi, libraryService)
    private val userId = UUID.randomUUID()
    private val tempDirs = mutableListOf<File>()

    @BeforeEach
    fun setup() {
        // Create a test user
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    "INSERT INTO users (id, username, email, password_hash, created_at, updated_at, is_admin) VALUES (?,?,?,?,?,?,false)",
                ).bind(0, userId.toString())
                .bind(1, "watchtest_${System.nanoTime()}")
                .bind(2, "wt_${System.nanoTime()}@test.com")
                .bind(3, "hash")
                .bind(
                    4,
                    java.time.Instant
                        .now()
                        .toString(),
                ).bind(
                    5,
                    java.time.Instant
                        .now()
                        .toString(),
                ).execute()
        }
        watchService.start()
    }

    @AfterEach
    fun teardown() {
        watchService.stop()
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tempLibraryDir(): File {
        val dir = Files.createTempDirectory("bt-watch-test-").toFile()
        tempDirs += dir
        return dir
    }

    @Test
    fun `watcher detects new PDF file and scans it into library`() {
        val dir = tempLibraryDir()
        val library = libraryService.createLibrary(userId, CreateLibraryRequest("Watch Test", dir.absolutePath))
        val libId = UUID.fromString(library.id)
        watchService.registerLibrary(userId, libId, dir.absolutePath)

        // Drop a fake PDF (just needs the extension)
        val pdf = File(dir, "newbook.pdf")
        pdf.writeBytes(ByteArray(100) { it.toByte() })

        // Give the watcher time to detect and scan.
        // macOS WatchService uses polling (up to ~10s per poll interval); allow extra time.
        Thread.sleep(5000)

        val books =
            jdbi.withHandle<List<String>, Exception> { handle ->
                handle
                    .createQuery("SELECT title FROM books WHERE library_id = ?")
                    .bind(0, library.id)
                    .mapTo(String::class.java)
                    .list()
            }
        assertTrue(books.any { it.contains("newbook") }, "Expected newbook to be scanned in, got: $books")
    }

    @Test
    fun `watcher marks book as missing when file is deleted`() {
        val dir = tempLibraryDir()
        val library = libraryService.createLibrary(userId, CreateLibraryRequest("Watch Delete Test", dir.absolutePath))
        val libId = UUID.fromString(library.id)

        // Pre-create a file and scan it in
        val epub = File(dir, "deleteme.epub")
        epub.writeBytes(ByteArray(50) { 0 })
        libraryService.scanLibrary(userId, libId)

        val bookId =
            jdbi.withHandle<String?, Exception> { handle ->
                handle
                    .createQuery("SELECT id FROM books WHERE library_id = ? AND file_path = ?")
                    .bind(0, library.id)
                    .bind(1, epub.absolutePath)
                    .mapTo(String::class.java)
                    .firstOrNull()
            }
        assertNotNull(bookId, "Book should exist after scan")

        watchService.registerLibrary(userId, libId, dir.absolutePath)

        // Delete the file
        epub.delete()

        // macOS WatchService uses polling (up to ~10s per poll interval); allow extra time.
        Thread.sleep(12000)

        val missing =
            jdbi.withHandle<String?, Exception> { handle ->
                handle
                    .createQuery("SELECT CAST(file_missing AS VARCHAR) FROM books WHERE id = ?")
                    .bind(0, bookId)
                    .mapTo(String::class.java)
                    .firstOrNull()
            }
        assertTrue(missing == "TRUE" || missing == "true" || missing == "1", "Book should be marked as missing, got: $missing")
    }

    @Test
    fun `watcher ignores non-book file types`() {
        val dir = tempLibraryDir()
        val library = libraryService.createLibrary(userId, CreateLibraryRequest("Watch Ignore Test", dir.absolutePath))
        val libId = UUID.fromString(library.id)
        watchService.registerLibrary(userId, libId, dir.absolutePath)

        File(dir, "readme.txt").writeText("not a book")
        File(dir, "cover.jpg").writeBytes(ByteArray(10))

        Thread.sleep(1500)

        val count =
            jdbi.withHandle<Int, Exception> { handle ->
                handle
                    .createQuery("SELECT COUNT(*) FROM books WHERE library_id = ?")
                    .bind(0, library.id)
                    .mapTo(Int::class.java)
                    .first()
            }
        assertEquals(0, count, "Non-book files should not be scanned in")
    }
}
