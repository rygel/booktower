package org.booktower.services

import org.booktower.models.UserAdminDto
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.result.RowView
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

private val logger = LoggerFactory.getLogger("booktower.AdminService")

class AdminService(
    private val jdbi: Jdbi,
) {
    fun listUsers(): List<UserAdminDto> =
        jdbi.withHandle<List<UserAdminDto>, Exception> { handle ->
            handle
                .createQuery(
                    "SELECT id, username, email, created_at, is_admin FROM users ORDER BY created_at DESC",
                ).map { row -> mapUserAdminDto(row) }
                .list()
        }

    fun getUserById(userId: UUID): UserAdminDto? =
        jdbi.withHandle<UserAdminDto?, Exception> { handle ->
            handle
                .createQuery(
                    "SELECT id, username, email, created_at, is_admin FROM users WHERE id = ?",
                ).bind(0, userId.toString())
                .map { row -> mapUserAdminDto(row) }
                .firstOrNull()
        }

    fun setAdmin(
        userId: UUID,
        isAdmin: Boolean,
    ): Boolean {
        val updated =
            jdbi.withHandle<Int, Exception> { handle ->
                handle
                    .createUpdate("UPDATE users SET is_admin = ?, updated_at = ? WHERE id = ?")
                    .bind(0, isAdmin)
                    .bind(1, Instant.now().toString())
                    .bind(2, userId.toString())
                    .execute()
            }
        if (updated > 0) logger.info("Admin status for $userId set to $isAdmin")
        return updated > 0
    }

    fun deleteUser(
        actorId: UUID,
        userId: UUID,
    ): Boolean {
        require(actorId != userId) { "Cannot delete your own account" }
        val deleted =
            jdbi.withHandle<Int, Exception> { handle ->
                handle
                    .createUpdate("DELETE FROM users WHERE id = ?")
                    .bind(0, userId.toString())
                    .execute()
            }
        if (deleted > 0) logger.info("User $userId deleted by admin $actorId")
        return deleted > 0
    }

    private fun mapUserAdminDto(row: RowView): UserAdminDto =
        UserAdminDto(
            id = row.getColumn("id", String::class.java),
            username = row.getColumn("username", String::class.java),
            email = row.getColumn("email", String::class.java),
            createdAt = row.getColumn("created_at", String::class.java),
            isAdmin = row.getColumn("is_admin", java.lang.Boolean::class.java)?.booleanValue() ?: false,
        )

    /**
     * Deletes ALL data from the database except the Flyway schema history.
     * The calling user's account is preserved so they can still log in.
     */
    fun resetDatabase(preserveUserId: UUID) {
        jdbi.useHandle<Exception> { handle ->
            // Order matters: delete child tables first to respect foreign key constraints
            val tables =
                listOf(
                    "reading_sessions",
                    "reading_progress",
                    "book_status",
                    "book_ratings",
                    "book_tags",
                    "book_authors",
                    "book_categories",
                    "book_moods",
                    "book_reviews",
                    "book_notebooks",
                    "book_formats",
                    "bookmarks",
                    "annotations",
                    "journal_entries",
                    "metadata_proposals",
                    "metadata_locks",
                    "notifications",
                    "filter_presets",
                    "user_settings",
                    "kobo_devices",
                    "koreader_devices",
                    "opds_credentials",
                    "refresh_tokens",
                    "api_tokens",
                    "password_reset_tokens",
                    "audit_log",
                    "telemetry_events",
                    "scheduled_task_history",
                    "listening_sessions",
                    "community_ratings",
                    "comic_page_hashes",
                    "book_content",
                    "email_recipients",
                    "email_providers",
                    "scheduled_tasks",
                    "user_permissions",
                    "library_access",
                    "books",
                    "libraries",
                )
            tables.forEach { table ->
                try {
                    handle.execute("DELETE FROM $table")
                } catch (e: Exception) {
                    // Table might not exist — skip
                }
            }
            // User accounts are preserved — only content data is deleted
            logger.info("Database reset by admin $preserveUserId — all content data deleted, user accounts preserved")
        }
    }
}
