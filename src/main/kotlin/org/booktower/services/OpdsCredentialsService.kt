package org.booktower.services

import org.jdbi.v3.core.Jdbi
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant
import java.util.UUID

data class OpdsCredentialsInfo(
    val userId: UUID,
    val opdsUsername: String,
)

class OpdsCredentialsService(private val jdbi: Jdbi) {

    /** Returns true if the user has custom OPDS credentials configured. */
    fun hasCredentials(userId: UUID): Boolean =
        jdbi.withHandle<Int, Exception> { h ->
            h.createQuery("SELECT COUNT(*) FROM opds_credentials WHERE user_id = ?")
                .bind(0, userId.toString()).mapTo(Int::class.java).firstOrNull() ?: 0
        } > 0

    /** Get the configured OPDS credentials info (without password hash). */
    fun getCredentials(userId: UUID): OpdsCredentialsInfo? =
        jdbi.withHandle<OpdsCredentialsInfo?, Exception> { h ->
            h.createQuery("SELECT opds_username FROM opds_credentials WHERE user_id = ?")
                .bind(0, userId.toString())
                .map { row ->
                    OpdsCredentialsInfo(
                        userId = userId,
                        opdsUsername = row.getColumn("opds_username", String::class.java),
                    )
                }.firstOrNull()
        }

    /** Set or update OPDS-specific credentials for a user. */
    fun setCredentials(userId: UUID, opdsUsername: String, password: String) {
        require(opdsUsername.isNotBlank()) { "OPDS username cannot be blank" }
        require(password.length >= 8) { "OPDS password must be at least 8 characters" }
        val hash = BCrypt.hashpw(password, BCrypt.gensalt())
        val now = Instant.now().toString()
        val existing = hasCredentials(userId)
        if (existing) {
            jdbi.useHandle<Exception> { h ->
                h.createUpdate(
                    "UPDATE opds_credentials SET opds_username = ?, password_hash = ?, updated_at = ? WHERE user_id = ?",
                ).bind(0, opdsUsername).bind(1, hash).bind(2, now).bind(3, userId.toString()).execute()
            }
        } else {
            jdbi.useHandle<Exception> { h ->
                h.createUpdate(
                    "INSERT INTO opds_credentials (user_id, opds_username, password_hash, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                ).bind(0, userId.toString()).bind(1, opdsUsername).bind(2, hash).bind(3, now).bind(4, now).execute()
            }
        }
    }

    /** Remove OPDS credentials; user falls back to main account credentials. */
    fun clearCredentials(userId: UUID): Boolean {
        val rows = jdbi.withHandle<Int, Exception> { h ->
            h.createUpdate("DELETE FROM opds_credentials WHERE user_id = ?")
                .bind(0, userId.toString()).execute()
        }
        return rows > 0
    }

    /**
     * Authenticate via OPDS-specific credentials.
     * Returns the user ID if valid, null otherwise.
     */
    fun authenticate(opdsUsername: String, password: String): UUID? {
        val row = jdbi.withHandle<Pair<String, String>?, Exception> { h ->
            h.createQuery("SELECT user_id, password_hash FROM opds_credentials WHERE opds_username = ?")
                .bind(0, opdsUsername)
                .map { r ->
                    Pair(
                        r.getColumn("user_id", String::class.java),
                        r.getColumn("password_hash", String::class.java),
                    )
                }.firstOrNull()
        } ?: return null
        return if (BCrypt.checkpw(password, row.second)) UUID.fromString(row.first) else null
    }
}
