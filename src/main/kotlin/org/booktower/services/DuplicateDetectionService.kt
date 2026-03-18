package org.booktower.services

import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger("booktower.DuplicateDetectionService")

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

class DuplicateDetectionService(
    private val jdbi: Jdbi,
) {
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
