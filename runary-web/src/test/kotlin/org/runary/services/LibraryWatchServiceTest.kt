package org.runary.services

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.runary.TestFixture
import org.runary.models.CreateLibraryRequest
import org.runary.models.CreateUserRequest
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
    private val libraryAccessService = LibraryAccessService(jdbi)
    private val libraryService = LibraryService(jdbi, pdfMetadataService, libraryAccessService)
    private val watchService = LibraryWatchService(jdbi, libraryService)
    private lateinit var userId: UUID
    private val tempDirs = mutableListOf<File>()

    @BeforeEach
    fun setup() {
        val jwtService = JwtService(config.security)
        val authService = AuthService(jdbi, jwtService)
        val username = "watchtest_${System.nanoTime()}"
        val result = authService.register(CreateUserRequest(username, "$username@test.com", org.runary.TestPasswords.DEFAULT))
        userId = UUID.fromString(result.getOrThrow().user.id)
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

    /** Poll the database until a condition is met, checking every 200ms up to [timeoutMs]. */
    private fun <T> awaitCondition(
        timeoutMs: Long = 15_000,
        poll: () -> T,
        check: (T) -> Boolean,
    ): T {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val result = poll()
            if (check(result)) return result
            Thread.sleep(200)
        }
        return poll() // final attempt — let the assertion handle the failure
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

        val books =
            awaitCondition(
                poll = {
                    jdbi.withHandle<List<String>, Exception> { handle ->
                        handle
                            .createQuery("SELECT title FROM books WHERE library_id = ?")
                            .bind(0, library.id)
                            .mapTo(String::class.java)
                            .list()
                    }
                },
                check = { it.any { title -> title.contains("newbook") } },
            )
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

        val missing =
            awaitCondition(
                poll = {
                    jdbi.withHandle<String?, Exception> { handle ->
                        handle
                            .createQuery("SELECT CAST(file_missing AS VARCHAR) FROM books WHERE id = ?")
                            .bind(0, bookId)
                            .mapTo(String::class.java)
                            .firstOrNull()
                    }
                },
                check = { it == "TRUE" || it == "true" || it == "1" },
            )
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

        // Wait a bit then verify nothing was scanned — short sleep is fine here since
        // we're asserting the ABSENCE of an event, not waiting for one.
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
