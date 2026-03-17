package org.booktower.services

import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID

private val logger = LoggerFactory.getLogger("booktower.LibraryHealthService")

data class BookHealthIssue(
    val bookId: String,
    val bookTitle: String,
    val libraryId: String,
    val libraryName: String,
    val issue: String,        // "file_missing", "cover_missing", "no_metadata", "zero_size"
    val filePath: String,
)

data class LibraryHealthReport(
    val libraryId: String,
    val libraryName: String,
    val libraryPath: String,
    val pathAccessible: Boolean,
    val totalBooks: Int,
    val issues: List<BookHealthIssue>,
    val issueCount: Int = issues.size,
)

data class HealthSummary(
    val userId: String,
    val libraries: List<LibraryHealthReport>,
    val totalIssues: Int = libraries.sumOf { it.issueCount },
)

class LibraryHealthService(private val jdbi: Jdbi) {

    fun check(userId: UUID): HealthSummary {
        val libs = jdbi.withHandle<List<Map<String, Any?>>, Exception> { h ->
            h.createQuery("SELECT id, name, path FROM libraries WHERE user_id = ? ORDER BY name")
                .bind(0, userId.toString())
                .mapToMap().list()
        }

        val reports = libs.map { lib ->
            val libId = lib["id"] as String
            val libName = lib["name"] as String
            val libPath = lib["path"] as String
            val pathOk = File(libPath).let { it.exists() && it.isDirectory }

            val books = jdbi.withHandle<List<Map<String, Any?>>, Exception> { h ->
                h.createQuery(
                    """SELECT id, title, file_path, file_size, cover_path, author, isbn
                       FROM books WHERE library_id = ?""",
                )
                    .bind(0, libId).mapToMap().list()
            }

            val issues = mutableListOf<BookHealthIssue>()
            for (book in books) {
                val bookId = book["id"] as String
                val bookTitle = book["title"] as? String ?: "(untitled)"
                val filePath = book["file_path"] as? String ?: ""
                val fileSize = (book["file_size"] as? Number)?.toLong() ?: 0L
                val coverPath = book["cover_path"] as? String
                val author = book["author"] as? String
                val isbn = book["isbn"] as? String

                fun issue(kind: String) = BookHealthIssue(bookId, bookTitle, libId, libName, kind, filePath)

                if (filePath.isBlank() || !File(filePath).exists()) issues += issue("file_missing")
                else if (fileSize == 0L) issues += issue("zero_size")

                if (coverPath.isNullOrBlank() || !File(coverPath).exists()) issues += issue("cover_missing")
                if (author.isNullOrBlank() && isbn.isNullOrBlank()) issues += issue("no_metadata")
            }

            LibraryHealthReport(
                libraryId = libId,
                libraryName = libName,
                libraryPath = libPath,
                pathAccessible = pathOk,
                totalBooks = books.size,
                issues = issues,
            )
        }

        val summary = HealthSummary(userId = userId.toString(), libraries = reports)
        logger.info("Health check for user $userId: ${summary.totalIssues} issues across ${libs.size} libraries")
        return summary
    }
}
