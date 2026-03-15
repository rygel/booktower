package org.booktower.services

import org.booktower.models.UserAdminDto
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.result.RowView
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

private val logger = LoggerFactory.getLogger("booktower.AdminService")

class AdminService(private val jdbi: Jdbi) {
    fun listUsers(): List<UserAdminDto> {
        return jdbi.withHandle<List<UserAdminDto>, Exception> { handle ->
            handle.createQuery(
                "SELECT id, username, email, created_at, is_admin FROM users ORDER BY created_at DESC",
            )
                .map { row -> mapUserAdminDto(row) }
                .list()
        }
    }

    fun setAdmin(userId: UUID, isAdmin: Boolean): Boolean {
        val updated = jdbi.withHandle<Int, Exception> { handle ->
            handle.createUpdate("UPDATE users SET is_admin = ?, updated_at = ? WHERE id = ?")
                .bind(0, isAdmin)
                .bind(1, Instant.now().toString())
                .bind(2, userId.toString())
                .execute()
        }
        if (updated > 0) logger.info("Admin status for $userId set to $isAdmin")
        return updated > 0
    }

    fun deleteUser(actorId: UUID, userId: UUID): Boolean {
        require(actorId != userId) { "Cannot delete your own account" }
        val deleted = jdbi.withHandle<Int, Exception> { handle ->
            handle.createUpdate("DELETE FROM users WHERE id = ?")
                .bind(0, userId.toString())
                .execute()
        }
        if (deleted > 0) logger.info("User $userId deleted by admin $actorId")
        return deleted > 0
    }

    private fun mapUserAdminDto(row: RowView): UserAdminDto {
        return UserAdminDto(
            id = row.getColumn("id", String::class.java),
            username = row.getColumn("username", String::class.java),
            email = row.getColumn("email", String::class.java),
            createdAt = row.getColumn("created_at", String::class.java),
            isAdmin = row.getColumn("is_admin", java.lang.Boolean::class.java)?.booleanValue() ?: false,
        )
    }
}
