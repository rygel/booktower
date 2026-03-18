package org.booktower.services

import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant

private val ftsLog = LoggerFactory.getLogger("booktower.FtsService")

data class FtsMatch(
    val bookId: String,
    val snippet: String,
    val rank: Float,
)

data class PendingBook(
    val bookId: String,
    val filePath: String,
    val format: String,
)

class FtsService(
    private val jdbi: Jdbi,
    private val enabled: Boolean,
) {
    @Volatile private var active = false

    fun initialize() {
        if (!enabled) {
            ftsLog.info("Full-text search disabled (set BOOKTOWER_FTS_ENABLED=true to enable)")
            return
        }
        val isPostgres =
            jdbi.withHandle<Boolean, Exception> { h ->
                h.connection.metaData.databaseProductName
                    .lowercase()
                    .contains("postgresql")
            }
        if (!isPostgres) {
            ftsLog.warn("BOOKTOWER_FTS_ENABLED=true but database is not PostgreSQL — FTS disabled")
            return
        }
        applyPgSchema()
        active = true
        ftsLog.info("Full-text search enabled (PostgreSQL)")
    }

    fun isActive(): Boolean = active

    fun enqueue(bookId: String) {
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate(
                    "INSERT INTO book_content (book_id, status) VALUES (?, 'pending') " +
                        "ON CONFLICT (book_id) DO NOTHING",
                ).bind(0, bookId)
                .execute()
        }
    }

    fun indexBook(
        bookId: String,
        filePath: String,
        format: String,
    ): Boolean {
        val file = File(filePath)
        if (!file.exists()) {
            markFailed(bookId, "File not found: $filePath")
            return false
        }
        val content =
            when (format.lowercase()) {
                "epub" -> {
                    EpubTextExtractor.extract(file)
                }

                "pdf" -> {
                    PdfTextExtractor.extract(file)
                }

                else -> {
                    markFailed(bookId, "Unsupported format: $format")
                    return false
                }
            }
        if (content == null) {
            markFailed(bookId, "Text extraction returned null")
            return false
        }
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate(
                    """
                INSERT INTO book_content (book_id, content, status, indexed_at)
                VALUES (?, ?, 'indexed', ?)
                ON CONFLICT (book_id) DO UPDATE
                  SET content = EXCLUDED.content,
                      status = 'indexed',
                      indexed_at = EXCLUDED.indexed_at,
                      error_msg = NULL
                """,
                ).bind(0, bookId)
                .bind(1, content)
                .bind(2, Instant.now().toString())
                .execute()
        }
        return true
    }

    fun search(
        query: String,
        allowedBookIds: Collection<String>? = null,
    ): List<FtsMatch> {
        if (!active || query.isBlank()) return emptyList()
        return try {
            jdbi.withHandle<List<FtsMatch>, Exception> { h ->
                val bookIdFilter =
                    if (!allowedBookIds.isNullOrEmpty()) {
                        "AND bc.book_id IN (${allowedBookIds.joinToString(",") { "?" }})"
                    } else {
                        ""
                    }
                val q =
                    h
                        .createQuery(
                            """
                    SELECT bc.book_id,
                           ts_headline('english', bc.content,
                               plainto_tsquery('english', :q),
                               'StartSel=**, StopSel=**, MaxWords=20, MinWords=10, ShortWord=3'
                           ) AS snippet,
                           ts_rank(bc.search_vector, plainto_tsquery('english', :q)) AS rank
                    FROM book_content bc
                    WHERE bc.status = 'indexed'
                      AND bc.search_vector @@ plainto_tsquery('english', :q)
                      $bookIdFilter
                    ORDER BY rank DESC
                    LIMIT 50
                    """,
                        ).bind("q", query)
                var idx = 0
                allowedBookIds?.forEach { q.bind(idx++, it) }
                q
                    .map { row ->
                        FtsMatch(
                            bookId = row.getColumn("book_id", String::class.java),
                            snippet = row.getColumn("snippet", String::class.java) ?: "",
                            rank = (row.getColumn("rank", java.lang.Float::class.java) ?: 0f).toFloat(),
                        )
                    }.list()
            }
        } catch (e: Exception) {
            ftsLog.warn("FTS search error: ${e.message}")
            emptyList()
        }
    }

    fun fetchPending(limit: Int = 5): List<PendingBook> =
        jdbi.withHandle<List<PendingBook>, Exception> { h ->
            h
                .createQuery(
                    """
                SELECT bc.book_id, b.file_path,
                       CASE
                           WHEN LOWER(b.file_path) LIKE '%.epub' THEN 'epub'
                           WHEN LOWER(b.file_path) LIKE '%.pdf'  THEN 'pdf'
                           ELSE 'unknown'
                       END AS format
                FROM book_content bc
                JOIN books b ON b.id = bc.book_id
                WHERE bc.status = 'pending'
                LIMIT ?
                """,
                ).bind(0, limit)
                .map { row ->
                    PendingBook(
                        bookId = row.getColumn("book_id", String::class.java),
                        filePath = row.getColumn("file_path", String::class.java) ?: "",
                        format = row.getColumn("format", String::class.java) ?: "unknown",
                    )
                }.list()
        }

    fun countByStatus(): Map<String, Long> =
        jdbi.withHandle<Map<String, Long>, Exception> { h ->
            h
                .createQuery("SELECT status, COUNT(*) AS n FROM book_content GROUP BY status")
                .map { row ->
                    (row.getColumn("status", String::class.java) ?: "unknown") to
                        ((row.getColumn("n", Long::class.java)) ?: 0L)
                }.associate { it }
        }

    private fun markFailed(
        bookId: String,
        msg: String,
    ) {
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate(
                    "UPDATE book_content SET status = 'failed', error_msg = ? WHERE book_id = ?",
                ).bind(0, msg.take(500))
                .bind(1, bookId)
                .execute()
        }
    }

    private fun applyPgSchema() {
        jdbi.useHandle<Exception> { h ->
            h.execute("ALTER TABLE book_content ADD COLUMN IF NOT EXISTS search_vector tsvector")
            h.execute(
                "CREATE INDEX IF NOT EXISTS idx_book_content_fts ON book_content USING GIN(search_vector)",
            )
            h.execute(
                """
                CREATE OR REPLACE FUNCTION book_content_fts_update() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
                BEGIN
                    NEW.search_vector := to_tsvector('english', COALESCE(NEW.content, ''));
                    RETURN NEW;
                END;
                ${'$'}${'$'}
                """,
            )
            h.execute("DROP TRIGGER IF EXISTS book_content_fts_trigger ON book_content")
            h.execute(
                """
                CREATE TRIGGER book_content_fts_trigger
                BEFORE INSERT OR UPDATE OF content ON book_content
                FOR EACH ROW EXECUTE FUNCTION book_content_fts_update()
                """,
            )
        }
        ftsLog.info("PostgreSQL FTS schema applied (tsvector + GIN index + trigger)")
    }
}
