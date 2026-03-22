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
    val source: String = "content",
)

data class PendingBook(
    val bookId: String,
    val filePath: String,
    val format: String,
    val language: String? = null,
)

/** Maps ISO 639-1/IETF language tags to PostgreSQL text search config names. */
private val LANGUAGE_TO_PG_CONFIG =
    mapOf(
        "ar" to "arabic",
        "hy" to "armenian",
        "eu" to "basque",
        "ca" to "catalan",
        "da" to "danish",
        "nl" to "dutch",
        "en" to "english",
        "fi" to "finnish",
        "fr" to "french",
        "de" to "german",
        "el" to "greek",
        "hi" to "hindi",
        "hu" to "hungarian",
        "id" to "indonesian",
        "ga" to "irish",
        "it" to "italian",
        "lt" to "lithuanian",
        "ne" to "nepali",
        "nb" to "norwegian",
        "nn" to "norwegian",
        "no" to "norwegian",
        "pt" to "portuguese",
        "ro" to "romanian",
        "ru" to "russian",
        "sr" to "serbian",
        "es" to "spanish",
        "sv" to "swedish",
        "ta" to "tamil",
        "tr" to "turkish",
        "yi" to "yiddish",
    )

/** Resolve a book language string to a PostgreSQL text search config name. */
fun pgTsConfig(language: String?): String {
    if (language.isNullOrBlank()) return "english"
    val tag = language.lowercase().trim()
    // Try exact match first (e.g. "en", "fr")
    LANGUAGE_TO_PG_CONFIG[tag]?.let { return it }
    // Try prefix (e.g. "en-US" → "en", "pt-BR" → "pt")
    val prefix = tag.substringBefore('-').substringBefore('_')
    return LANGUAGE_TO_PG_CONFIG[prefix] ?: "simple"
}

/**
 * Full-text search service with two backends:
 *
 * 1. **Built-in PostgreSQL FTS** (default): uses tsvector/tsquery with
 *    `websearch_to_tsquery` for natural query syntax, weighted metadata
 *    search (title > author > description > content), and multi-language.
 *
 * 2. **pg_textsearch BM25** (optional): if the pg_textsearch extension is
 *    installed on PostgreSQL 17+, uses BM25 ranking for content search
 *    with statistically better relevance scoring.
 *
 * H2 (dev/test): FTS is disabled — search falls back to LIKE queries.
 */
class FtsService(
    private val jdbi: Jdbi,
    private val enabled: Boolean,
) {
    @Volatile private var active = false

    @Volatile private var hasBm25 = false

    @Volatile private var hasMetadataVector = false

    @Volatile private var hasTrgm = false

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
        detectMetadataVector()
        detectBm25()
        detectTrgm()
        active = true
        ftsLog.info(
            "Full-text search enabled (PostgreSQL) — metadata=$hasMetadataVector, bm25=$hasBm25, trgm=$hasTrgm",
        )
    }

    fun isActive(): Boolean = active

    fun hasBm25(): Boolean = hasBm25

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
        language: String? = null,
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
        indexContent(bookId, content, language)
        return true
    }

    /**
     * Index pre-extracted text content for a book.
     * The [language] parameter selects the PostgreSQL text search config
     * for language-aware stemming (e.g. "en" → "english", "fr" → "french").
     */
    fun indexContent(
        bookId: String,
        content: String,
        language: String? = null,
    ) {
        val config = pgTsConfig(language)
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate(
                    """
                INSERT INTO book_content (book_id, content, fts_config, status, indexed_at)
                VALUES (?, ?, ?, 'indexed', ?)
                ON CONFLICT (book_id) DO UPDATE
                  SET content = EXCLUDED.content,
                      fts_config = EXCLUDED.fts_config,
                      status = 'indexed',
                      indexed_at = EXCLUDED.indexed_at,
                      error_msg = NULL
                """,
                ).bind(0, bookId)
                .bind(1, content)
                .bind(2, config)
                .bind(3, Instant.now().toString())
                .execute()
        }
    }

    /**
     * Search book metadata using weighted tsvector (title > author > description).
     * Uses websearch_to_tsquery for natural query syntax: "exact phrase", -exclude, OR.
     */
    fun searchMetadata(
        query: String,
        allowedBookIds: Collection<String>? = null,
        language: String? = null,
    ): List<FtsMatch> {
        if (!active || !hasMetadataVector || query.isBlank()) return emptyList()
        val tsConfig = pgTsConfig(language)
        return try {
            jdbi.withHandle<List<FtsMatch>, Exception> { h ->
                val bookIdFilter =
                    if (!allowedBookIds.isNullOrEmpty()) {
                        val placeholders = allowedBookIds.indices.joinToString(",") { ":bid$it" }
                        "AND b.id IN ($placeholders)"
                    } else {
                        ""
                    }
                val q =
                    h
                        .createQuery(
                            """
                        SELECT b.id AS book_id,
                               ts_headline('simple', COALESCE(b.title, '') || ' ' || COALESCE(b.author, ''),
                                   websearch_to_tsquery('simple', :q),
                                   'StartSel=**, StopSel=**, MaxWords=20, MinWords=5'
                               ) AS snippet,
                               ts_rank_cd(b.metadata_vector, websearch_to_tsquery('simple', :q),
                                   32 /* rank normalization: divide by rank + 1 */
                               ) AS rank
                        FROM books b
                        WHERE b.metadata_vector @@ websearch_to_tsquery('simple', :q)
                          $bookIdFilter
                        ORDER BY rank DESC
                        LIMIT 50
                        """,
                        ).bind("q", query)
                allowedBookIds?.forEachIndexed { idx, id -> q.bind("bid$idx", id) }
                q
                    .map { row ->
                        FtsMatch(
                            bookId = row.getColumn("book_id", String::class.java),
                            snippet = row.getColumn("snippet", String::class.java) ?: "",
                            rank = (row.getColumn("rank", java.lang.Float::class.java) ?: 0f).toFloat(),
                            source = "metadata",
                        )
                    }.list()
            }
        } catch (e: Exception) {
            ftsLog.warn("Metadata FTS search error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Search book content. Uses pg_textsearch BM25 if available,
     * otherwise falls back to built-in tsvector search.
     */
    fun search(
        query: String,
        allowedBookIds: Collection<String>? = null,
        language: String? = null,
    ): List<FtsMatch> {
        if (!active || query.isBlank()) return emptyList()
        return if (hasBm25) {
            searchBm25(query, allowedBookIds)
        } else {
            searchTsvector(query, allowedBookIds, language)
        }
    }

    /**
     * Combined search: metadata + content, deduplicated and re-ranked.
     * Metadata matches rank higher than content matches.
     */
    fun searchAll(
        query: String,
        allowedBookIds: Collection<String>? = null,
        language: String? = null,
    ): List<FtsMatch> {
        // For CJK queries, use trigram search (tsvector can't tokenize CJK characters)
        if (containsCjk(query) && hasTrgm) {
            return searchTrigram(query, allowedBookIds)
        }

        val metadataResults = searchMetadata(query, allowedBookIds, language)
        val contentResults = search(query, allowedBookIds, language)

        // Merge: metadata results first (boosted rank), then content results not in metadata
        val metadataIds = metadataResults.map { it.bookId }.toSet()
        val boostedMetadata = metadataResults.map { it.copy(rank = it.rank + 1.0f) }
        val uniqueContent = contentResults.filter { it.bookId !in metadataIds }

        return (boostedMetadata + uniqueContent)
            .sortedByDescending { it.rank }
            .take(50)
    }

    private fun searchTsvector(
        query: String,
        allowedBookIds: Collection<String>?,
        language: String? = null,
    ): List<FtsMatch> =
        try {
            jdbi.withHandle<List<FtsMatch>, Exception> { h ->
                val bookIdFilter =
                    if (!allowedBookIds.isNullOrEmpty()) {
                        "AND bc.book_id IN (${allowedBookIds.indices.joinToString(",") { ":bid$it" }})"
                    } else {
                        ""
                    }
                // Use each book's fts_config for ts_headline so highlighting
                // uses the same tokenization as indexing. The query tsquery
                // uses the book's config too via a subquery for proper stemming.
                val q =
                    h
                        .createQuery(
                            """
                        SELECT bc.book_id,
                               ts_headline(bc.fts_config::regconfig, bc.content,
                                   websearch_to_tsquery(bc.fts_config::regconfig, :q),
                                   'StartSel=**, StopSel=**, MaxWords=20, MinWords=10, ShortWord=3'
                               ) AS snippet,
                               ts_rank(bc.search_vector, websearch_to_tsquery(bc.fts_config::regconfig, :q)) AS rank
                        FROM book_content bc
                        WHERE bc.status = 'indexed'
                          AND bc.search_vector @@ websearch_to_tsquery(bc.fts_config::regconfig, :q)
                          $bookIdFilter
                        ORDER BY rank DESC
                        LIMIT 50
                        """,
                        ).bind("q", query)
                allowedBookIds?.forEachIndexed { idx, id -> q.bind("bid$idx", id) }
                q
                    .map { row ->
                        FtsMatch(
                            bookId = row.getColumn("book_id", String::class.java),
                            snippet = row.getColumn("snippet", String::class.java) ?: "",
                            rank = (row.getColumn("rank", java.lang.Float::class.java) ?: 0f).toFloat(),
                            source = "content",
                        )
                    }.list()
            }
        } catch (e: Exception) {
            ftsLog.warn("Content FTS search error: ${e.message}")
            emptyList()
        }

    private fun searchBm25(
        query: String,
        allowedBookIds: Collection<String>?,
    ): List<FtsMatch> =
        try {
            jdbi.withHandle<List<FtsMatch>, Exception> { h ->
                val bookIdFilter =
                    if (!allowedBookIds.isNullOrEmpty()) {
                        "AND bc.book_id IN (${allowedBookIds.indices.joinToString(",") { ":bid$it" }})"
                    } else {
                        ""
                    }
                val q =
                    h
                        .createQuery(
                            """
                        SELECT bc.book_id,
                               LEFT(bc.content, 200) AS snippet,
                               -(bc.content <@> :q) AS rank
                        FROM book_content bc
                        WHERE bc.status = 'indexed'
                          $bookIdFilter
                        ORDER BY bc.content <@> :q
                        LIMIT 50
                        """,
                        ).bind("q", query)
                allowedBookIds?.forEachIndexed { idx, id -> q.bind("bid$idx", id) }
                q
                    .map { row ->
                        FtsMatch(
                            bookId = row.getColumn("book_id", String::class.java),
                            snippet = row.getColumn("snippet", String::class.java) ?: "",
                            rank = (row.getColumn("rank", java.lang.Float::class.java) ?: 0f).toFloat(),
                            source = "bm25",
                        )
                    }.list()
            }
        } catch (e: Exception) {
            ftsLog.warn("BM25 search error (falling back to tsvector): ${e.message}")
            searchTsvector(query, allowedBookIds)
        }

    fun fetchPending(limit: Int = 5): List<PendingBook> =
        jdbi.withHandle<List<PendingBook>, Exception> { h ->
            h
                .createQuery(
                    """
                SELECT bc.book_id, b.file_path, b.language,
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
                        language = row.getColumn("language", String::class.java),
                    )
                }.list()
        }

    fun countByStatus(): Map<String, Long> =
        jdbi.withHandle<Map<String, Long>, Exception> { h ->
            h
                .createQuery("SELECT status, COUNT(*) AS n FROM book_content GROUP BY status")
                .map { row ->
                    (row.getColumn("status", String::class.java) ?: "unknown") to
                        ((row.getColumn("n", java.lang.Long::class.java))?.toLong() ?: 0L)
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
            h.execute("ALTER TABLE book_content ADD COLUMN IF NOT EXISTS fts_config VARCHAR(30) DEFAULT 'english'")
            h.execute(
                "CREATE INDEX IF NOT EXISTS idx_book_content_fts ON book_content USING GIN(search_vector)",
            )
            // Trigger uses per-book fts_config column for language-aware tokenization
            h.execute(
                """
                CREATE OR REPLACE FUNCTION book_content_fts_update() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
                BEGIN
                    NEW.search_vector := to_tsvector(NEW.fts_config::regconfig, COALESCE(NEW.content, ''));
                    RETURN NEW;
                END;
                ${'$'}${'$'}
                """,
            )
            h.execute("DROP TRIGGER IF EXISTS book_content_fts_trigger ON book_content")
            h.execute(
                """
                CREATE TRIGGER book_content_fts_trigger
                BEFORE INSERT OR UPDATE OF content, fts_config ON book_content
                FOR EACH ROW EXECUTE FUNCTION book_content_fts_update()
                """,
            )
            // Metadata tsvector on books table (weighted: title A, author B, series B, description C)
            h.execute("ALTER TABLE books ADD COLUMN IF NOT EXISTS metadata_vector tsvector")
            h.execute("CREATE INDEX IF NOT EXISTS idx_books_metadata_fts ON books USING GIN(metadata_vector)")
            h.execute(
                """
                CREATE OR REPLACE FUNCTION books_metadata_fts_update() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
                BEGIN
                    NEW.metadata_vector :=
                        setweight(to_tsvector('simple', COALESCE(NEW.title, '')), 'A') ||
                        setweight(to_tsvector('simple', COALESCE(NEW.author, '')), 'B') ||
                        setweight(to_tsvector('simple', COALESCE(NEW.series, '')), 'B') ||
                        setweight(to_tsvector('simple', COALESCE(NEW.description, '')), 'C');
                    RETURN NEW;
                END;
                ${'$'}${'$'}
                """,
            )
            h.execute("DROP TRIGGER IF EXISTS books_metadata_fts_trigger ON books")
            h.execute(
                """
                CREATE TRIGGER books_metadata_fts_trigger
                BEFORE INSERT OR UPDATE OF title, author, series, description ON books
                FOR EACH ROW EXECUTE FUNCTION books_metadata_fts_update()
                """,
            )
            // Backfill existing rows
            h.execute(
                """
                UPDATE books SET metadata_vector =
                    setweight(to_tsvector('simple', COALESCE(title, '')), 'A') ||
                    setweight(to_tsvector('simple', COALESCE(author, '')), 'B') ||
                    setweight(to_tsvector('simple', COALESCE(series, '')), 'B') ||
                    setweight(to_tsvector('simple', COALESCE(description, '')), 'C')
                WHERE metadata_vector IS NULL
                """,
            )
        }
        ftsLog.info("PostgreSQL FTS schema applied (content + metadata tsvector + GIN indexes)")
    }

    private fun detectMetadataVector() {
        hasMetadataVector =
            try {
                jdbi.withHandle<Boolean, Exception> { h ->
                    h
                        .createQuery(
                            "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'books' AND column_name = 'metadata_vector'",
                        ).mapTo(Int::class.java)
                        .one() > 0
                }
            } catch (e: Exception) {
                false
            }
        if (hasMetadataVector) {
            ftsLog.info("Metadata tsvector column detected — weighted metadata search enabled")
        }
    }

    private fun detectBm25() {
        hasBm25 =
            try {
                val available =
                    jdbi.withHandle<Boolean, Exception> { h ->
                        h
                            .createQuery("SELECT COUNT(*) FROM pg_available_extensions WHERE name = 'pg_textsearch'")
                            .mapTo(Int::class.java)
                            .one() > 0
                    }
                if (!available) return

                // Ensure extension is created and BM25 index exists
                jdbi.useHandle<Exception> { h ->
                    h.execute("CREATE EXTENSION IF NOT EXISTS pg_textsearch")
                    h.execute(
                        """
                        CREATE INDEX IF NOT EXISTS idx_book_content_bm25
                        ON book_content USING bm25(content)
                        WITH (text_config='english')
                        """,
                    )
                }
                ftsLog.info("pg_textsearch extension enabled — BM25 index created on book_content")
                true
            } catch (e: Exception) {
                ftsLog.debug("pg_textsearch not available: ${e.message}")
                false
            }
        if (hasBm25) {
            ftsLog.info("pg_textsearch BM25 ranking enabled")
        }
    }

    private fun detectTrgm() {
        hasTrgm =
            try {
                jdbi.useHandle<Exception> { h ->
                    h.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm")
                    // Create trigram indexes for CJK search
                    h.execute("CREATE INDEX IF NOT EXISTS idx_books_title_trgm ON books USING GIN(title gin_trgm_ops)")
                    h.execute("CREATE INDEX IF NOT EXISTS idx_books_author_trgm ON books USING GIN(author gin_trgm_ops)")
                }
                ftsLog.info("pg_trgm enabled — CJK/trigram search available")
                true
            } catch (e: Exception) {
                ftsLog.debug("pg_trgm not available: ${e.message}")
                false
            }
    }

    /** Returns true if the query contains CJK (Chinese/Japanese/Korean) characters. */
    private fun containsCjk(query: String): Boolean =
        query.any { c ->
            Character.UnicodeBlock.of(c) in
                setOf(
                    Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
                    Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
                    Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
                    Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
                    Character.UnicodeBlock.HIRAGANA,
                    Character.UnicodeBlock.KATAKANA,
                    Character.UnicodeBlock.HANGUL_SYLLABLES,
                    Character.UnicodeBlock.HANGUL_JAMO,
                )
        }

    /**
     * Trigram search for CJK queries. Uses pg_trgm similarity matching
     * which works with any script (doesn't need word boundaries).
     */
    fun searchTrigram(
        query: String,
        allowedBookIds: Collection<String>? = null,
    ): List<FtsMatch> {
        if (!active || !hasTrgm || query.isBlank()) return emptyList()
        return try {
            jdbi.withHandle<List<FtsMatch>, Exception> { h ->
                val bookIdFilter =
                    if (!allowedBookIds.isNullOrEmpty()) {
                        val placeholders = allowedBookIds.indices.joinToString(",") { ":bid$it" }
                        "AND b.id IN ($placeholders)"
                    } else {
                        ""
                    }
                val q =
                    h
                        .createQuery(
                            """
                        SELECT b.id AS book_id,
                               b.title AS snippet,
                               GREATEST(
                                   similarity(b.title, :q),
                                   similarity(COALESCE(b.author, ''), :q),
                                   similarity(COALESCE(b.description, ''), :q)
                               ) AS rank
                        FROM books b
                        WHERE (
                            b.title % :q
                            OR b.author % :q
                            OR b.description % :q
                        )
                        $bookIdFilter
                        ORDER BY rank DESC
                        LIMIT 50
                        """,
                        ).bind("q", query)
                allowedBookIds?.forEachIndexed { idx, id -> q.bind("bid$idx", id) }
                q
                    .map { row ->
                        FtsMatch(
                            bookId = row.getColumn("book_id", String::class.java),
                            snippet = row.getColumn("snippet", String::class.java) ?: "",
                            rank = (row.getColumn("rank", java.lang.Float::class.java) ?: 0f).toFloat(),
                            source = "trigram",
                        )
                    }.list()
            }
        } catch (e: Exception) {
            ftsLog.warn("Trigram search error: ${e.message}")
            emptyList()
        }
    }
}
