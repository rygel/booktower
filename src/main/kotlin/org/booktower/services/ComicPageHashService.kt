package org.booktower.services

import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.UUID
import javax.imageio.ImageIO

private val log = LoggerFactory.getLogger("booktower.ComicPageHashService")

data class PageDuplicateGroup(
    val hashValue: Long,
    val pages: List<PageDuplicateEntry>,
)

data class PageDuplicateEntry(
    val bookId: String,
    val bookTitle: String,
    val libraryName: String,
    val pageIndex: Int,
)

data class PendingHashBook(
    val bookId: String,
    val filePath: String,
)

class ComicPageHashService(
    private val jdbi: Jdbi,
    private val comicService: ComicService,
) {
    /** Enqueue a comic book for page hashing. No-op if already queued. */
    fun enqueue(bookId: String) {
        jdbi.useHandle<Exception> { h ->
            val exists =
                h
                    .createQuery("SELECT COUNT(*) FROM comic_hash_queue WHERE book_id = ?")
                    .bind(0, bookId)
                    .mapTo(Int::class.java)
                    .one() > 0
            if (!exists) {
                h
                    .createUpdate(
                        "INSERT INTO comic_hash_queue (book_id, status, queued_at) VALUES (?, 'pending', ?)",
                    ).bind(0, bookId)
                    .bind(1, Instant.now().toString())
                    .execute()
            }
        }
    }

    /** Fetch up to [limit] pending books. */
    fun fetchPending(limit: Int): List<PendingHashBook> =
        jdbi.withHandle<List<PendingHashBook>, Exception> { h ->
            h
                .createQuery(
                    """SELECT q.book_id, b.file_path
                   FROM comic_hash_queue q
                   JOIN books b ON q.book_id = b.id
                   WHERE q.status = 'pending'
                   ORDER BY q.queued_at
                   LIMIT ?""",
                ).bind(0, limit)
                .map { row ->
                    PendingHashBook(
                        bookId = row.getColumn("book_id", String::class.java),
                        filePath = row.getColumn("file_path", String::class.java),
                    )
                }.list()
        }

    /** Compute and store aHash for every page in one comic book. */
    fun indexBook(
        bookId: String,
        filePath: String,
    ) {
        val pages = comicService.listPages(filePath)
        if (pages.isEmpty()) {
            markStatus(bookId, "indexed")
            return
        }
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate("DELETE FROM comic_page_hashes WHERE book_id = ?")
                .bind(0, bookId)
                .execute()
            var hashed = 0
            for (page in pages) {
                val bytes = comicService.getPage(filePath, page.index) ?: continue
                val hash = aHash(bytes) ?: continue
                h
                    .createUpdate(
                        "INSERT INTO comic_page_hashes (book_id, page_index, phash) VALUES (?, ?, ?)",
                    ).bind(0, bookId)
                    .bind(1, page.index)
                    .bind(2, hash)
                    .execute()
                hashed++
            }
            log.debug("Hashed $hashed/${pages.size} pages for book $bookId")
        }
        markStatus(bookId, "indexed")
    }

    fun markStatus(
        bookId: String,
        status: String,
        error: String? = null,
    ) {
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate(
                    "UPDATE comic_hash_queue SET status = ?, indexed_at = ?, error_msg = ? WHERE book_id = ?",
                ).bind(0, status)
                .bind(1, Instant.now().toString())
                .bind(2, error)
                .bind(3, bookId)
                .execute()
        }
    }

    /**
     * Returns all page pairs that share an identical aHash across different books
     * belonging to [userId]'s libraries.
     */
    fun findDuplicatePages(userId: UUID): List<PageDuplicateGroup> {
        val rows =
            jdbi.withHandle<List<Map<String, Any?>>, Exception> { h ->
                h
                    .createQuery(
                        """SELECT h1.phash,
                          h1.book_id AS book1_id, h1.page_index AS page1_idx, b1.title AS title1, l1.name AS lib1,
                          h2.book_id AS book2_id, h2.page_index AS page2_idx, b2.title AS title2, l2.name AS lib2
                   FROM comic_page_hashes h1
                   JOIN comic_page_hashes h2 ON h1.phash = h2.phash AND h1.book_id < h2.book_id
                   JOIN books b1 ON h1.book_id = b1.id
                   JOIN books b2 ON h2.book_id = b2.id
                   JOIN libraries l1 ON b1.library_id = l1.id
                   JOIN libraries l2 ON b2.library_id = l2.id
                   WHERE l1.user_id = ? AND l2.user_id = ?
                   ORDER BY h1.phash""",
                    ).bind(0, userId.toString())
                    .bind(1, userId.toString())
                    .mapToMap()
                    .list()
            }

        val grouped = linkedMapOf<Long, LinkedHashSet<PageDuplicateEntry>>()
        for (row in rows) {
            val hash = (row["phash"] as Number).toLong()
            val set = grouped.getOrPut(hash) { linkedSetOf() }
            set +=
                PageDuplicateEntry(
                    bookId = row["book1_id"] as String,
                    bookTitle = row["title1"] as? String ?: "",
                    libraryName = row["lib1"] as? String ?: "",
                    pageIndex = (row["page1_idx"] as Number).toInt(),
                )
            set +=
                PageDuplicateEntry(
                    bookId = row["book2_id"] as String,
                    bookTitle = row["title2"] as? String ?: "",
                    libraryName = row["lib2"] as? String ?: "",
                    pageIndex = (row["page2_idx"] as Number).toInt(),
                )
        }
        return grouped.map { (hash, entries) -> PageDuplicateGroup(hash, entries.toList()) }
    }

    fun countByStatus(): Map<String, Int> =
        jdbi.withHandle<Map<String, Int>, Exception> { h ->
            h
                .createQuery("SELECT status, COUNT(*) AS cnt FROM comic_hash_queue GROUP BY status")
                .map { row ->
                    row.getColumn("status", String::class.java) to
                        (row.getColumn("cnt", Int::class.java) ?: 0)
                }.list()
                .toMap()
        }

    // ── Average hash (aHash) ─────────────────────────────────────────────────

    /**
     * Computes a 64-bit average hash of [bytes] (JPEG / PNG / GIF / BMP / WebP).
     * Scale to 8×8 grayscale, threshold each pixel against the mean → 64-bit Long.
     * Returns null when the image cannot be decoded.
     */
    internal fun aHash(bytes: ByteArray): Long? =
        try {
            val img = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
            val gray = BufferedImage(8, 8, BufferedImage.TYPE_BYTE_GRAY)
            val g2d = gray.createGraphics()
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2d.drawImage(img, 0, 0, 8, 8, null)
            g2d.dispose()
            img.flush()
            val pixels = IntArray(64) { i -> gray.raster.getSample(i % 8, i / 8, 0) }
            val avg = pixels.average()
            var hash = 0L
            for (i in 0..63) {
                if (pixels[i] >= avg) hash = hash or (1L shl i)
            }
            hash
        } catch (e: Exception) {
            log.debug("aHash decode failed: ${e.message}")
            null
        }

    /** Hamming distance between two 64-bit hashes. Values ≤ 10 indicate near-duplicates. */
    internal fun hammingDistance(
        a: Long,
        b: Long,
    ): Int = java.lang.Long.bitCount(a xor b)
}
