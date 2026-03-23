package org.runary.integration

import org.runary.TestFixture
import org.runary.services.ComicPageHashService
import org.runary.services.ComicService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ComicPageHashIntegrationTest : IntegrationTestBase() {
    private val jdbi = TestFixture.database.getJdbi()
    private val service = ComicPageHashService(jdbi, ComicService())

    // ── aHash unit tests ────────────────────────────────────────────────────

    @Test
    fun `aHash returns same hash for identical image bytes`() {
        val bytes = solidColorJpeg(Color.RED, 200, 300)
        val h1 = service.aHash(bytes)
        val h2 = service.aHash(bytes)
        assertNotNull(h1)
        assertEquals(h1, h2)
    }

    @Test
    fun `aHash returns different hash for structurally different images`() {
        // Checkerboard (high frequency) vs horizontal gradient (low frequency): very different hashes
        val checker = service.aHash(checkerboardJpeg(128, 128))
        val gradient = service.aHash(gradientJpeg(128, 128))
        assertNotNull(checker)
        assertNotNull(gradient)
        val dist = service.hammingDistance(checker!!, gradient!!)
        assertTrue(dist > 5, "Expected meaningful Hamming distance between checker and gradient but got $dist")
    }

    @Test
    fun `aHash returns null for invalid bytes`() {
        val result = service.aHash("not an image".toByteArray())
        assertEquals(null, result)
    }

    @Test
    fun `hammingDistance is 0 for identical hashes`() {
        assertEquals(0, service.hammingDistance(0xABCDEFL, 0xABCDEFL))
    }

    @Test
    fun `hammingDistance counts differing bits`() {
        // 0b0000 vs 0b1111 → 4 bits differ
        assertEquals(4, service.hammingDistance(0b0000L, 0b1111L))
    }

    // ── DB round-trip ───────────────────────────────────────────────────────

    @Test
    fun `enqueue and fetchPending round-trip`() {
        val (_, libraryId) = createUserAndLibrary()
        val bookId = insertComic(libraryId)
        service.enqueue(bookId)

        val pending = service.fetchPending(10)
        assertTrue(pending.any { it.bookId == bookId }, "Enqueued book should appear in pending")
    }

    @Test
    fun `enqueue is idempotent`() {
        val (_, libraryId) = createUserAndLibrary()
        val bookId = insertComic(libraryId)
        service.enqueue(bookId)
        service.enqueue(bookId) // second call must not throw
        assertEquals(1, service.fetchPending(100).count { it.bookId == bookId })
    }

    @Test
    fun `markStatus transitions to indexed`() {
        val (_, libraryId) = createUserAndLibrary()
        val bookId = insertComic(libraryId)
        service.enqueue(bookId)
        service.markStatus(bookId, "indexed")

        val pending = service.fetchPending(10)
        assertTrue(pending.none { it.bookId == bookId }, "Indexed book must not appear in pending")
    }

    @Test
    fun `findDuplicatePages detects identical hashes across books`() {
        val (userId, libraryId) = createUserAndLibrary()
        val book1 = insertComic(libraryId)
        val book2 = insertComic(libraryId)
        val sharedHash = 0x1234567890ABCDEFL

        // Raw SQL is acceptable here: comic_page_hashes are normally populated by the background
        // indexing pipeline, and there is no service method for direct hash insertion in tests.
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate("INSERT INTO comic_page_hashes (book_id, page_index, phash) VALUES (?, 0, ?)")
                .bind(0, book1)
                .bind(1, sharedHash)
                .execute()
            h
                .createUpdate("INSERT INTO comic_page_hashes (book_id, page_index, phash) VALUES (?, 0, ?)")
                .bind(0, book2)
                .bind(1, sharedHash)
                .execute()
        }

        val groups = service.findDuplicatePages(UUID.fromString(userId))
        assertEquals(1, groups.size)
        assertEquals(2, groups[0].pages.size)
        assertTrue(groups[0].pages.any { it.bookId == book1 })
        assertTrue(groups[0].pages.any { it.bookId == book2 })
    }

    @Test
    fun `findDuplicatePages returns empty when no duplicates`() {
        val (userId, libraryId) = createUserAndLibrary()
        val book1 = insertComic(libraryId)
        val book2 = insertComic(libraryId)

        // Raw SQL is acceptable here: comic_page_hashes are normally populated by the background
        // indexing pipeline, and there is no service method for direct hash insertion in tests.
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate("INSERT INTO comic_page_hashes (book_id, page_index, phash) VALUES (?, 0, ?)")
                .bind(0, book1)
                .bind(1, 0x111L)
                .execute()
            h
                .createUpdate("INSERT INTO comic_page_hashes (book_id, page_index, phash) VALUES (?, 0, ?)")
                .bind(0, book2)
                .bind(1, 0x222L)
                .execute()
        }

        val groups = service.findDuplicatePages(UUID.fromString(userId))
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `findDuplicatePages does not cross user boundaries`() {
        val (user1, lib1) = createUserAndLibrary()
        val (_, lib2) = createUserAndLibrary()
        val book1 = insertComic(lib1)
        val book2 = insertComic(lib2)
        val sharedHash = 0xDEADBEEFL

        // Raw SQL is acceptable here: comic_page_hashes are normally populated by the background
        // indexing pipeline, and there is no service method for direct hash insertion in tests.
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate("INSERT INTO comic_page_hashes (book_id, page_index, phash) VALUES (?, 0, ?)")
                .bind(0, book1)
                .bind(1, sharedHash)
                .execute()
            h
                .createUpdate("INSERT INTO comic_page_hashes (book_id, page_index, phash) VALUES (?, 0, ?)")
                .bind(0, book2)
                .bind(1, sharedHash)
                .execute()
        }

        val groups = service.findDuplicatePages(UUID.fromString(user1))
        assertTrue(groups.isEmpty(), "Should not see duplicates across different users")
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** Tracks the token for each library owner so insertComic can create books via API. */
    private val libraryTokens = mutableMapOf<String, String>()

    private fun createUserAndLibrary(): Pair<String, String> {
        val userId = createTestUser()
        val jwtService = org.runary.services.JwtService(TestFixture.config.security)
        val authService = org.runary.services.AuthService(jdbi, jwtService)
        val user = authService.getUserById(UUID.fromString(userId))!!
        val token = jwtService.generateToken(user)
        val libId = createLibrary(token, "L_${System.nanoTime()}")
        libraryTokens[libId] = token
        return userId to libId
    }

    private fun insertComic(libraryId: String): String {
        val token = libraryTokens[libraryId] ?: error("No token found for library $libraryId — call createUserAndLibrary first")
        val bookId = createBook(token, libraryId, "Comic_${System.nanoTime()}")
        // Set file_path to simulate a scanned comic file.
        // Raw SQL is acceptable here: file_path is internal state set during library scanning,
        // and there is no BookService method to update just file_path on an existing book.
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate("UPDATE books SET file_path = ? WHERE id = ?")
                .bind(0, "/tmp/$bookId.cbz")
                .bind(1, bookId)
                .execute()
        }
        return bookId
    }

    private fun solidColorJpeg(
        color: Color,
        width: Int,
        height: Int,
    ): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = color
        g.fillRect(0, 0, width, height)
        g.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "jpeg", out)
        return out.toByteArray()
    }

    /** Black-and-white checkerboard — high-frequency content. */
    private fun checkerboardJpeg(
        width: Int,
        height: Int,
    ): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                img.setRGB(x, y, if ((x / 8 + y / 8) % 2 == 0) 0xFFFFFF else 0x000000)
            }
        }
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "jpeg", out)
        return out.toByteArray()
    }

    /** Left-to-right black→white gradient — low-frequency content. */
    private fun gradientJpeg(
        width: Int,
        height: Int,
    ): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val v = (x * 255 / (width - 1))
                img.setRGB(x, y, (v shl 16) or (v shl 8) or v)
            }
        }
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "jpeg", out)
        return out.toByteArray()
    }
}
