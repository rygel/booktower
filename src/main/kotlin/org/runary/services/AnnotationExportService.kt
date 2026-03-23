package org.runary.services

import org.jdbi.v3.core.Jdbi
import java.util.UUID

data class ExportedAnnotation(
    val bookTitle: String,
    val bookAuthor: String?,
    val page: Int,
    val selectedText: String,
    val color: String,
    val createdAt: String,
)

data class ExportedBookmark(
    val bookTitle: String,
    val bookAuthor: String?,
    val page: Int,
    val title: String?,
    val note: String?,
    val createdAt: String,
)

/**
 * Exports annotations and bookmarks in various formats.
 * Supports Markdown, JSON, and Readwise CSV.
 */
class AnnotationExportService(
    private val jdbi: Jdbi,
) {
    fun getAnnotations(userId: UUID): List<ExportedAnnotation> =
        jdbi.withHandle<List<ExportedAnnotation>, Exception> { h ->
            h
                .createQuery(
                    """
                SELECT ba.page, ba.selected_text, ba.color, ba.created_at,
                       b.title AS book_title, b.author AS book_author
                FROM book_annotations ba
                INNER JOIN books b ON ba.book_id = b.id
                INNER JOIN libraries l ON b.library_id = l.id
                WHERE ba.user_id = ? AND l.user_id = ?
                ORDER BY b.title, ba.page
                """,
                ).bind(0, userId.toString())
                .bind(1, userId.toString())
                .map { row ->
                    ExportedAnnotation(
                        bookTitle = row.getColumn("book_title", String::class.java) ?: "",
                        bookAuthor = row.getColumn("book_author", String::class.java),
                        page = row.getColumn("page", Int::class.javaObjectType) ?: 0,
                        selectedText = row.getColumn("selected_text", String::class.java) ?: "",
                        color = row.getColumn("color", String::class.java) ?: "yellow",
                        createdAt = row.getColumn("created_at", String::class.java) ?: "",
                    )
                }.list()
        }

    fun getBookmarks(userId: UUID): List<ExportedBookmark> =
        jdbi.withHandle<List<ExportedBookmark>, Exception> { h ->
            h
                .createQuery(
                    """
                SELECT bb.page, bb.title, bb.note, bb.created_at,
                       b.title AS book_title, b.author AS book_author
                FROM bookmarks bb
                INNER JOIN books b ON bb.book_id = b.id
                INNER JOIN libraries l ON b.library_id = l.id
                WHERE bb.user_id = ? AND l.user_id = ?
                ORDER BY b.title, bb.page
                """,
                ).bind(0, userId.toString())
                .bind(1, userId.toString())
                .map { row ->
                    ExportedBookmark(
                        bookTitle = row.getColumn("book_title", String::class.java) ?: "",
                        bookAuthor = row.getColumn("book_author", String::class.java),
                        page = row.getColumn("page", Int::class.javaObjectType) ?: 0,
                        title = row.getColumn("title", String::class.java),
                        note = row.getColumn("note", String::class.java),
                        createdAt = row.getColumn("created_at", String::class.java) ?: "",
                    )
                }.list()
        }

    /** Export all annotations and bookmarks as Markdown. */
    fun toMarkdown(userId: UUID): String {
        val annotations = getAnnotations(userId)
        val bookmarks = getBookmarks(userId)

        return buildString {
            appendLine("# Runary — Highlights & Notes")
            appendLine()

            if (annotations.isNotEmpty()) {
                appendLine("## Highlights")
                appendLine()
                var lastBook = ""
                for (a in annotations) {
                    if (a.bookTitle != lastBook) {
                        appendLine("### ${a.bookTitle}")
                        if (!a.bookAuthor.isNullOrBlank()) appendLine("*${a.bookAuthor}*")
                        appendLine()
                        lastBook = a.bookTitle
                    }
                    appendLine("> ${a.selectedText}")
                    appendLine("— Page ${a.page}")
                    appendLine()
                }
            }

            if (bookmarks.isNotEmpty()) {
                appendLine("## Bookmarks")
                appendLine()
                var lastBook = ""
                for (b in bookmarks) {
                    if (b.bookTitle != lastBook) {
                        appendLine("### ${b.bookTitle}")
                        if (!b.bookAuthor.isNullOrBlank()) appendLine("*${b.bookAuthor}*")
                        appendLine()
                        lastBook = b.bookTitle
                    }
                    val label = b.title ?: "Page ${b.page}"
                    appendLine("- **$label** (p. ${b.page})")
                    if (!b.note.isNullOrBlank()) appendLine("  ${b.note}")
                    appendLine()
                }
            }
        }
    }

    /** Export annotations in Readwise CSV format. */
    fun toReadwiseCsv(userId: UUID): String {
        val annotations = getAnnotations(userId)
        return buildString {
            appendLine("Highlight,Title,Author,Page,Note,Color,Date")
            for (a in annotations) {
                val text = csvEscape(a.selectedText)
                val title = csvEscape(a.bookTitle)
                val author = csvEscape(a.bookAuthor ?: "")
                appendLine("$text,$title,$author,${a.page},,${a.color},${a.createdAt.take(10)}")
            }
        }
    }

    private fun csvEscape(s: String): String {
        val escaped = s.replace("\"", "\"\"")
        return if (escaped.contains(',') || escaped.contains('\n') || escaped.contains('"')) {
            "\"$escaped\""
        } else {
            escaped
        }
    }
}
