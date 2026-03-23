package org.runary.services

import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger("runary.DuplicateDetectionService")

data class DuplicateGroup(
    val reason: String,
    val matchValue: String,
    val books: List<DuplicateBookEntry>,
)

data class DuplicateBookEntry(
    val id: String,
    val title: String,
    val author: String?,
    val libraryId: String,
    val libraryName: String,
    val filePath: String,
    val fileSize: Long,
    val addedAt: String,
)

data class MergeResult(
    val keptBookId: String,
    val deletedBookIds: List<String>,
)

class DuplicateDetectionService(
    private val jdbi: Jdbi,
) {
    /**
     * Merges duplicate books: keeps [keepBookId] and deletes [deleteBookIds].
     * Before deleting, copies any non-blank metadata from deleted books into
     * the kept book (only fills blanks — never overwrites existing data).
     * Returns null if any book is not found or not owned by [userId].
     */
    fun merge(
        userId: UUID,
        keepBookId: UUID,
        deleteBookIds: List<UUID>,
    ): MergeResult? {
        if (deleteBookIds.isEmpty()) return null

        // Verify all books belong to this user
        val allIds = (deleteBookIds + keepBookId).map { it.toString() }
        val owned =
            jdbi.withHandle<Set<String>, Exception> { h ->
                val placeholders = allIds.indices.joinToString(",") { "?" }
                val q =
                    h.createQuery(
                        """
                    SELECT b.id FROM books b
                    INNER JOIN libraries l ON b.library_id = l.id
                    WHERE l.user_id = ? AND b.id IN ($placeholders)
                    """,
                    )
                q.bind(0, userId.toString())
                allIds.forEachIndexed { i, id -> q.bind(i + 1, id) }
                q.mapTo(String::class.java).toSet()
            }
        if (owned.size != allIds.size) return null

        // Fill missing metadata on kept book from deleted books
        val keptMeta = getBookMeta(keepBookId.toString()) ?: return null
        for (deleteId in deleteBookIds) {
            val donorMeta = getBookMeta(deleteId.toString()) ?: continue
            fillMissingFields(keepBookId.toString(), keptMeta, donorMeta)
        }

        // Move bookmarks, annotations, reading sessions from deleted books to kept book
        jdbi.useHandle<Exception> { h ->
            for (deleteId in deleteBookIds) {
                val did = deleteId.toString()
                val kid = keepBookId.toString()
                h
                    .createUpdate("UPDATE bookmarks SET book_id = ? WHERE book_id = ?")
                    .bind(0, kid)
                    .bind(1, did)
                    .execute()
                h
                    .createUpdate("UPDATE book_annotations SET book_id = ? WHERE book_id = ?")
                    .bind(0, kid)
                    .bind(1, did)
                    .execute()
                h
                    .createUpdate("UPDATE reading_sessions SET book_id = ? WHERE book_id = ?")
                    .bind(0, kid)
                    .bind(1, did)
                    .execute()
                h.createUpdate("DELETE FROM books WHERE id = ?").bind(0, did).execute()
            }
        }

        val deletedIds = deleteBookIds.map { it.toString() }
        logger.info("Merged duplicates: kept=$keepBookId, deleted=$deletedIds")
        return MergeResult(keepBookId.toString(), deletedIds)
    }

    private fun getBookMeta(bookId: String): Map<String, Any?>? =
        jdbi.withHandle<Map<String, Any?>?, Exception> { h ->
            h
                .createQuery("SELECT * FROM books WHERE id = ?")
                .bind(0, bookId)
                .mapToMap()
                .firstOrNull()
        }

    private fun fillMissingFields(
        keepId: String,
        kept: Map<String, Any?>,
        donor: Map<String, Any?>,
    ) {
        val fields = listOf("isbn", "description", "publisher", "published_date", "page_count", "series", "series_index", "language", "cover_path")
        val updates = mutableListOf<Pair<String, Any>>()
        for (field in fields) {
            val keptVal = kept[field]
            val donorVal = donor[field]
            if ((keptVal == null || keptVal.toString().isBlank()) && donorVal != null && donorVal.toString().isNotBlank()) {
                updates += field to donorVal
            }
        }
        if (updates.isEmpty()) return

        jdbi.useHandle<Exception> { h ->
            val setClauses = updates.joinToString(", ") { "${it.first} = ?" }
            val q = h.createUpdate("UPDATE books SET $setClauses WHERE id = ?")
            updates.forEachIndexed { i, (_, v) -> q.bind(i, v) }
            q.bind(updates.size, keepId)
            q.execute()
        }
    }

    /**
     * Returns all duplicate groups for the given user's libraries.
     * Checks three signals in priority order:
     *  1. Identical ISBN (non-blank)
     *  2. Identical file hash (non-null)
     *  3. Same normalised title + same normalised author
     */
    fun findDuplicates(userId: UUID): List<DuplicateGroup> {
        val groups = mutableListOf<DuplicateGroup>()
        val seenBookIds = mutableSetOf<String>()

        groups += findByIsbn(userId, seenBookIds)
        groups += findByFileHash(userId, seenBookIds)
        groups += findByTitleAuthor(userId, seenBookIds)

        logger.info("Duplicate scan for user $userId: ${groups.size} groups found")
        return groups
    }

    private fun findByIsbn(
        userId: UUID,
        seen: MutableSet<String>,
    ): List<DuplicateGroup> {
        val rows =
            jdbi.withHandle<List<Map<String, Any?>>, Exception> { handle ->
                handle
                    .createQuery(
                        """
                SELECT b.id, b.title, b.author, b.isbn, b.library_id, l.name AS library_name,
                       b.file_path, b.file_size, b.added_at
                FROM books b
                INNER JOIN libraries l ON b.library_id = l.id
                WHERE l.user_id = ? AND b.isbn IS NOT NULL AND b.isbn <> ''
                ORDER BY b.isbn, b.added_at
                """,
                    ).bind(0, userId.toString())
                    .mapToMap()
                    .list()
            }
        return groupBy(rows, "isbn", "isbn", seen)
    }

    private fun findByFileHash(
        userId: UUID,
        seen: MutableSet<String>,
    ): List<DuplicateGroup> {
        val rows =
            jdbi.withHandle<List<Map<String, Any?>>, Exception> { handle ->
                handle
                    .createQuery(
                        """
                SELECT b.id, b.title, b.author, b.file_hash AS isbn, b.library_id, l.name AS library_name,
                       b.file_path, b.file_size, b.added_at
                FROM books b
                INNER JOIN libraries l ON b.library_id = l.id
                WHERE l.user_id = ? AND b.file_hash IS NOT NULL AND b.file_hash <> ''
                ORDER BY b.file_hash, b.added_at
                """,
                    ).bind(0, userId.toString())
                    .mapToMap()
                    .list()
            }
        return groupBy(rows, "isbn", "file_hash", seen)
    }

    private fun findByTitleAuthor(
        userId: UUID,
        seen: MutableSet<String>,
    ): List<DuplicateGroup> {
        val rows =
            jdbi.withHandle<List<Map<String, Any?>>, Exception> { handle ->
                handle
                    .createQuery(
                        """
                SELECT b.id, b.title, b.author, b.library_id, l.name AS library_name,
                       b.file_path, b.file_size, b.added_at
                FROM books b
                INNER JOIN libraries l ON b.library_id = l.id
                WHERE l.user_id = ?
                ORDER BY b.title, b.author, b.added_at
                """,
                    ).bind(0, userId.toString())
                    .mapToMap()
                    .list()
            }

        // Group by normalised(title)+normalised(author)
        val grouped =
            rows
                .groupBy { row ->
                    val title = (row["title"] as? String ?: "").normalise()
                    val author = (row["author"] as? String ?: "").normalise()
                    "$title||$author"
                }.filter { it.value.size > 1 && it.key != "||" }

        return grouped.mapNotNull { (key, group) ->
            val newBooks = group.filter { (it["id"] as String) !in seen }
            if (newBooks.size < 2) return@mapNotNull null
            newBooks.forEach { seen += it["id"] as String }
            DuplicateGroup(
                reason = "title_author",
                matchValue = key.replace("||", " / "),
                books = newBooks.map { it.toEntry() },
            )
        }
    }

    private fun groupBy(
        rows: List<Map<String, Any?>>,
        keyCol: String,
        reason: String,
        seen: MutableSet<String>,
    ): List<DuplicateGroup> {
        return rows
            .groupBy { it[keyCol] as? String ?: "" }
            .filter { it.key.isNotBlank() && it.value.size > 1 }
            .mapNotNull { (matchValue, group) ->
                val newBooks = group.filter { (it["id"] as String) !in seen }
                if (newBooks.size < 2) return@mapNotNull null
                newBooks.forEach { seen += it["id"] as String }
                DuplicateGroup(
                    reason = reason,
                    matchValue = matchValue,
                    books = newBooks.map { it.toEntry() },
                )
            }
    }

    private fun Map<String, Any?>.toEntry() =
        DuplicateBookEntry(
            id = this["id"] as String,
            title = this["title"] as? String ?: "",
            author = this["author"] as? String,
            libraryId = this["library_id"] as String,
            libraryName = this["library_name"] as? String ?: "",
            filePath = this["file_path"] as? String ?: "",
            fileSize = (this["file_size"] as? Number)?.toLong() ?: 0L,
            addedAt = this["added_at"]?.toString() ?: "",
        )

    private fun String.normalise() =
        this
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), "")
            .replace(Regex("\\s+"), " ")
}
