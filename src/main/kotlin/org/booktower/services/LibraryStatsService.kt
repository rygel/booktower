package org.booktower.services

import org.jdbi.v3.core.Jdbi
import java.util.UUID

data class LibraryStats(
    val totalBooks: Int,
    val totalLibraries: Int,
    val totalStorageBytes: Long,
    val formatBreakdown: Map<String, Int>,
    val languageBreakdown: Map<String, Int>,
    val topAuthors: List<AuthorCount>,
    val topTags: List<TagCount>,
    val statusBreakdown: Map<String, Int>,
    val booksAddedPerMonth: List<MonthCount>,
    val averagePageCount: Int,
    val booksWithFiles: Int,
    val booksWithCovers: Int,
    val booksWithIsbn: Int,
)

data class AuthorCount(
    val author: String,
    val count: Int,
)

data class TagCount(
    val tag: String,
    val count: Int,
)

data class MonthCount(
    val month: String,
    val count: Int,
)

/**
 * Computes library-wide statistics for admin dashboards.
 */
class LibraryStatsService(
    private val jdbi: Jdbi,
) {
    fun getStats(userId: UUID): LibraryStats {
        val uid = userId.toString()
        return jdbi.withHandle<LibraryStats, Exception> { h ->
            val totalBooks =
                h
                    .createQuery("SELECT COUNT(*) FROM books b INNER JOIN libraries l ON b.library_id = l.id WHERE l.user_id = ?")
                    .bind(0, uid)
                    .mapTo(Int::class.javaObjectType)
                    .one()

            val totalLibraries =
                h
                    .createQuery("SELECT COUNT(*) FROM libraries WHERE user_id = ?")
                    .bind(0, uid)
                    .mapTo(Int::class.javaObjectType)
                    .one()

            val totalStorage =
                h
                    .createQuery(
                        "SELECT COALESCE(SUM(b.file_size), 0) FROM books b INNER JOIN libraries l ON b.library_id = l.id WHERE l.user_id = ?",
                    ).bind(0, uid)
                    .mapTo(Long::class.java)
                    .one()

            val formatBreakdown =
                h
                    .createQuery(
                        """
                    SELECT COALESCE(b.book_format, 'UNKNOWN') AS fmt, COUNT(*) AS cnt
                    FROM books b INNER JOIN libraries l ON b.library_id = l.id
                    WHERE l.user_id = ? GROUP BY fmt ORDER BY cnt DESC
                    """,
                    ).bind(0, uid)
                    .map { row ->
                        (row.getColumn("fmt", String::class.java) ?: "UNKNOWN") to
                            (row.getColumn("cnt", Int::class.javaObjectType) ?: 0)
                    }.associate { it }

            val languageBreakdown =
                h
                    .createQuery(
                        """
                    SELECT COALESCE(b.language, 'unknown') AS lang, COUNT(*) AS cnt
                    FROM books b INNER JOIN libraries l ON b.library_id = l.id
                    WHERE l.user_id = ? AND b.language IS NOT NULL AND b.language <> ''
                    GROUP BY lang ORDER BY cnt DESC LIMIT 20
                    """,
                    ).bind(0, uid)
                    .map { row ->
                        (row.getColumn("lang", String::class.java) ?: "unknown") to
                            (row.getColumn("cnt", Int::class.javaObjectType) ?: 0)
                    }.associate { it }

            val topAuthors =
                h
                    .createQuery(
                        """
                    SELECT b.author, COUNT(*) AS cnt
                    FROM books b INNER JOIN libraries l ON b.library_id = l.id
                    WHERE l.user_id = ? AND b.author IS NOT NULL AND b.author <> ''
                    GROUP BY b.author ORDER BY cnt DESC LIMIT 20
                    """,
                    ).bind(0, uid)
                    .map { row ->
                        AuthorCount(
                            row.getColumn("author", String::class.java) ?: "",
                            row.getColumn("cnt", Int::class.javaObjectType) ?: 0,
                        )
                    }.list()

            val topTags =
                h
                    .createQuery(
                        """
                    SELECT bt.tag, COUNT(*) AS cnt
                    FROM book_tags bt
                    WHERE bt.user_id = ?
                    GROUP BY bt.tag ORDER BY cnt DESC LIMIT 20
                    """,
                    ).bind(0, uid)
                    .map { row ->
                        TagCount(
                            row.getColumn("tag", String::class.java) ?: "",
                            row.getColumn("cnt", Int::class.javaObjectType) ?: 0,
                        )
                    }.list()

            val statusBreakdown =
                h
                    .createQuery(
                        """
                    SELECT bs.status, COUNT(*) AS cnt
                    FROM book_status bs
                    WHERE bs.user_id = ?
                    GROUP BY bs.status ORDER BY cnt DESC
                    """,
                    ).bind(0, uid)
                    .map { row ->
                        (row.getColumn("status", String::class.java) ?: "UNKNOWN") to
                            (row.getColumn("cnt", Int::class.javaObjectType) ?: 0)
                    }.associate { it }

            val booksAddedPerMonth =
                h
                    .createQuery(
                        """
                    SELECT SUBSTRING(CAST(b.added_at AS VARCHAR(30)), 1, 7) AS added_month, COUNT(*) AS cnt
                    FROM books b INNER JOIN libraries l ON b.library_id = l.id
                    WHERE l.user_id = ?
                    GROUP BY added_month ORDER BY added_month DESC LIMIT 12
                    """,
                    ).bind(0, uid)
                    .map { row ->
                        MonthCount(
                            row.getColumn("added_month", String::class.java) ?: "",
                            row.getColumn("cnt", Int::class.javaObjectType) ?: 0,
                        )
                    }.list()
                    .reversed()

            val avgPageCount =
                h
                    .createQuery(
                        """
                    SELECT COALESCE(AVG(b.page_count), 0)
                    FROM books b INNER JOIN libraries l ON b.library_id = l.id
                    WHERE l.user_id = ? AND b.page_count IS NOT NULL AND b.page_count > 0
                    """,
                    ).bind(0, uid)
                    .mapTo(Int::class.javaObjectType)
                    .one()

            val booksWithFiles =
                h
                    .createQuery(
                        """
                    SELECT COUNT(*) FROM books b INNER JOIN libraries l ON b.library_id = l.id
                    WHERE l.user_id = ? AND b.file_path IS NOT NULL AND b.file_path <> ''
                    """,
                    ).bind(0, uid)
                    .mapTo(Int::class.javaObjectType)
                    .one()

            val booksWithCovers =
                h
                    .createQuery(
                        """
                    SELECT COUNT(*) FROM books b INNER JOIN libraries l ON b.library_id = l.id
                    WHERE l.user_id = ? AND b.cover_path IS NOT NULL AND b.cover_path <> ''
                    """,
                    ).bind(0, uid)
                    .mapTo(Int::class.javaObjectType)
                    .one()

            val booksWithIsbn =
                h
                    .createQuery(
                        """
                    SELECT COUNT(*) FROM books b INNER JOIN libraries l ON b.library_id = l.id
                    WHERE l.user_id = ? AND b.isbn IS NOT NULL AND b.isbn <> ''
                    """,
                    ).bind(0, uid)
                    .mapTo(Int::class.javaObjectType)
                    .one()

            LibraryStats(
                totalBooks = totalBooks,
                totalLibraries = totalLibraries,
                totalStorageBytes = totalStorage,
                formatBreakdown = formatBreakdown,
                languageBreakdown = languageBreakdown,
                topAuthors = topAuthors,
                topTags = topTags,
                statusBreakdown = statusBreakdown,
                booksAddedPerMonth = booksAddedPerMonth,
                averagePageCount = avgPageCount,
                booksWithFiles = booksWithFiles,
                booksWithCovers = booksWithCovers,
                booksWithIsbn = booksWithIsbn,
            )
        }
    }
}
