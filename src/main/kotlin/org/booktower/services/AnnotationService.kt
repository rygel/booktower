package org.booktower.services

import org.booktower.models.AnnotationDto
import org.jdbi.v3.core.Jdbi
import java.time.Instant
import java.util.UUID

class AnnotationService(private val jdbi: Jdbi) {

    fun getAnnotations(userId: UUID, bookId: UUID, page: Int? = null): List<AnnotationDto> =
        jdbi.withHandle<List<AnnotationDto>, Exception> { handle ->
            val pageSql = if (page != null) " AND page = ?" else ""
            val q = handle.createQuery(
                "SELECT * FROM book_annotations WHERE user_id = ? AND book_id = ?$pageSql ORDER BY page ASC, created_at ASC"
            )
                .bind(0, userId.toString())
                .bind(1, bookId.toString())
            if (page != null) q.bind(2, page)
            q.map { rs, _ ->
                AnnotationDto(
                    id = rs.getString("id"),
                    bookId = rs.getString("book_id"),
                    page = rs.getInt("page"),
                    selectedText = rs.getString("selected_text"),
                    color = rs.getString("color"),
                    createdAt = rs.getString("created_at") ?: "",
                )
            }.list()
        }

    fun createAnnotation(
        userId: UUID,
        bookId: UUID,
        page: Int,
        selectedText: String,
        color: String,
    ): AnnotationDto {
        val id = UUID.randomUUID()
        jdbi.withHandle<Unit, Exception> { handle ->
            handle.execute(
                """INSERT INTO book_annotations (id, user_id, book_id, page, selected_text, color, created_at)
                   VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)""",
                id.toString(), userId.toString(), bookId.toString(),
                page, selectedText.take(2000), color.take(20),
            )
        }
        return AnnotationDto(
            id = id.toString(),
            bookId = bookId.toString(),
            page = page,
            selectedText = selectedText.take(2000),
            color = color.take(20),
            createdAt = Instant.now().toString(),
        )
    }

    fun deleteAnnotation(userId: UUID, annotationId: UUID): Boolean =
        jdbi.withHandle<Int, Exception> { handle ->
            handle.execute(
                "DELETE FROM book_annotations WHERE id = ? AND user_id = ?",
                annotationId.toString(), userId.toString(),
            )
        } > 0
}
