package org.booktower.services

import org.jdbi.v3.core.Jdbi
import java.time.Instant
import java.util.UUID

data class NotificationDto(
    val id: String,
    val userId: String,
    val type: String,
    val title: String,
    val body: String?,
    val isRead: Boolean,
    val createdAt: String,
)

class NotificationService(private val jdbi: Jdbi) {

    fun list(userId: UUID, unreadOnly: Boolean = false): List<NotificationDto> =
        jdbi.withHandle<List<NotificationDto>, Exception> { h ->
            val sql = buildString {
                append("SELECT id, user_id, type, title, body, is_read, created_at FROM notifications WHERE user_id = ?")
                if (unreadOnly) append(" AND is_read = FALSE")
                append(" ORDER BY created_at DESC")
            }
            h.createQuery(sql).bind(0, userId.toString())
                .map { row -> mapRow(row) }
                .list()
        }

    fun publish(userId: UUID, type: String, title: String, body: String? = null): NotificationDto {
        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        jdbi.useHandle<Exception> { h ->
            h.createUpdate(
                "INSERT INTO notifications (id, user_id, type, title, body, is_read, created_at) VALUES (?, ?, ?, ?, ?, FALSE, ?)",
            ).bind(0, id).bind(1, userId.toString()).bind(2, type).bind(3, title).bind(4, body).bind(5, now).execute()
        }
        return NotificationDto(id = id, userId = userId.toString(), type = type, title = title, body = body, isRead = false, createdAt = now)
    }

    fun markRead(userId: UUID, notificationId: String): Boolean {
        val rows = jdbi.withHandle<Int, Exception> { h ->
            h.createUpdate("UPDATE notifications SET is_read = TRUE WHERE id = ? AND user_id = ?")
                .bind(0, notificationId).bind(1, userId.toString()).execute()
        }
        return rows > 0
    }

    fun markAllRead(userId: UUID): Int =
        jdbi.withHandle<Int, Exception> { h ->
            h.createUpdate("UPDATE notifications SET is_read = TRUE WHERE user_id = ? AND is_read = FALSE")
                .bind(0, userId.toString()).execute()
        }

    fun delete(userId: UUID, notificationId: String): Boolean {
        val rows = jdbi.withHandle<Int, Exception> { h ->
            h.createUpdate("DELETE FROM notifications WHERE id = ? AND user_id = ?")
                .bind(0, notificationId).bind(1, userId.toString()).execute()
        }
        return rows > 0
    }

    fun unreadCount(userId: UUID): Int =
        jdbi.withHandle<Int, Exception> { h ->
            h.createQuery("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = FALSE")
                .bind(0, userId.toString())
                .mapTo(java.lang.Integer::class.java)
                .first()?.toInt() ?: 0
        }

    private fun mapRow(row: org.jdbi.v3.core.result.RowView) = NotificationDto(
        id = row.getColumn("id", String::class.java),
        userId = row.getColumn("user_id", String::class.java),
        type = row.getColumn("type", String::class.java),
        title = row.getColumn("title", String::class.java),
        body = row.getColumn("body", String::class.java),
        isRead = row.getColumn("is_read", java.lang.Boolean::class.java) == true,
        createdAt = row.getColumn("created_at", String::class.java),
    )
}
