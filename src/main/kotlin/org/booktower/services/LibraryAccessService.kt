package org.booktower.services

import org.jdbi.v3.core.Jdbi
import java.time.Instant
import java.util.UUID

data class LibraryAccessEntry(
    val libraryId: String,
    val grantedAt: String,
)

class LibraryAccessService(
    private val jdbi: Jdbi,
) {
    /** Whether a user is in restricted mode (must have explicit grants to see libraries). */
    fun isRestricted(userId: UUID): Boolean =
        jdbi.withHandle<Boolean, Exception> { h ->
            h
                .createQuery("SELECT is_library_restricted FROM users WHERE id = ?")
                .bind(0, userId.toString())
                .mapTo(java.lang.Boolean::class.java)
                .firstOrNull()
                ?.booleanValue() ?: false
        }

    /** Sets restricted mode on a user. */
    fun setRestricted(
        userId: UUID,
        restricted: Boolean,
    ) {
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate("UPDATE users SET is_library_restricted = ? WHERE id = ?")
                .bind(0, restricted)
                .bind(1, userId.toString())
                .execute()
        }
    }

    /** Lists all library IDs explicitly granted to a user. */
    fun listGrantedLibraries(userId: UUID): List<LibraryAccessEntry> =
        jdbi.withHandle<List<LibraryAccessEntry>, Exception> { h ->
            h
                .createQuery("SELECT library_id, granted_at FROM user_library_access WHERE user_id = ? ORDER BY granted_at")
                .bind(0, userId.toString())
                .map { row ->
                    LibraryAccessEntry(
                        libraryId = row.getColumn("library_id", String::class.java),
                        grantedAt = row.getColumn("granted_at", String::class.java),
                    )
                }.list()
        }

    /** Grants access to a library for a user. Idempotent. */
    fun grantAccess(
        userId: UUID,
        libraryId: UUID,
    ) {
        val exists =
            jdbi.withHandle<Boolean, Exception> { h ->
                h
                    .createQuery("SELECT COUNT(*) FROM user_library_access WHERE user_id = ? AND library_id = ?")
                    .bind(0, userId.toString())
                    .bind(1, libraryId.toString())
                    .mapTo(Int::class.java)
                    .firstOrNull()!! > 0
            }
        if (!exists) {
            jdbi.useHandle<Exception> { h ->
                h
                    .createUpdate(
                        "INSERT INTO user_library_access (user_id, library_id, granted_at) VALUES (?, ?, ?)",
                    ).bind(0, userId.toString())
                    .bind(1, libraryId.toString())
                    .bind(2, Instant.now().toString())
                    .execute()
            }
        }
    }

    /** Revokes access to a library for a user. */
    fun revokeAccess(
        userId: UUID,
        libraryId: UUID,
    ) {
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate("DELETE FROM user_library_access WHERE user_id = ? AND library_id = ?")
                .bind(0, userId.toString())
                .bind(1, libraryId.toString())
                .execute()
        }
    }

    /** Returns the set of library IDs accessible to a user (all if not restricted, granted set if restricted). */
    fun getAccessibleLibraryIds(userId: UUID): Set<String>? {
        if (!isRestricted(userId)) return null // null = unrestricted (all libraries)
        return listGrantedLibraries(userId).map { it.libraryId }.toSet()
    }
}
