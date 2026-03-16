package org.booktower.services

import org.jdbi.v3.core.Jdbi
import org.mindrot.jbcrypt.BCrypt
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

private val logger = LoggerFactory.getLogger("booktower.PasswordResetService")
private val rng = SecureRandom()

class PasswordResetService(private val jdbi: Jdbi) {

    companion object {
        const val TOKEN_EXPIRY_HOURS = 24L
    }

    /**
     * Creates a password-reset token for the user identified by [email].
     * Returns the raw token (shown once, never stored) or null if the email is unknown.
     * The raw token is also logged at INFO level so a self-hosted admin can share it.
     */
    fun createToken(email: String): String? {
        val userId = jdbi.withHandle<String?, Exception> { handle ->
            handle.createQuery("SELECT id FROM users WHERE email = ?")
                .bind(0, email.trim().lowercase())
                .mapTo(String::class.java)
                .firstOrNull()
        } ?: return null

        // Purge old unused tokens for this user to keep the table clean
        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate("DELETE FROM password_reset_tokens WHERE user_id = ? AND used_at IS NULL")
                .bind(0, userId).execute()
        }

        val rawToken = generateToken()
        val tokenHash = hashToken(rawToken)
        val now = Instant.now()
        val expiresAt = now.plus(TOKEN_EXPIRY_HOURS, ChronoUnit.HOURS)

        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate(
                "INSERT INTO password_reset_tokens (id, user_id, token_hash, expires_at, created_at) VALUES (?,?,?,?,?)",
            )
                .bind(0, UUID.randomUUID().toString())
                .bind(1, userId)
                .bind(2, tokenHash)
                .bind(3, expiresAt.toString())
                .bind(4, now.toString())
                .execute()
        }

        logger.info("Password reset token created for user $userId — token valid ${TOKEN_EXPIRY_HOURS}h")
        return rawToken
    }

    /**
     * Validates [rawToken] and returns the associated userId if valid and unused.
     */
    fun validateToken(rawToken: String): String? {
        val tokenHash = hashToken(rawToken)
        val row = jdbi.withHandle<Triple<String, String, String?>?, Exception> { handle ->
            handle.createQuery(
                "SELECT id, user_id, expires_at FROM password_reset_tokens WHERE token_hash = ? AND used_at IS NULL",
            )
                .bind(0, tokenHash)
                .map { r ->
                    Triple(
                        r.getColumn("id", String::class.java),
                        r.getColumn("user_id", String::class.java),
                        r.getColumn("expires_at", String::class.java),
                    )
                }
                .firstOrNull()
        } ?: return null

        val (_, userId, expiresAt) = row
        if (expiresAt != null && Instant.parse(expiresAt).isBefore(Instant.now())) {
            return null  // expired
        }
        return userId
    }

    /**
     * Resets the password using [rawToken]. Returns true on success.
     */
    fun resetPassword(rawToken: String, newPassword: String): Boolean {
        val tokenHash = hashToken(rawToken)
        val now = Instant.now()
        val row = jdbi.withHandle<Pair<String, String>?, Exception> { handle ->
            handle.createQuery(
                "SELECT id, user_id FROM password_reset_tokens WHERE token_hash = ? AND used_at IS NULL AND expires_at > ?",
            )
                .bind(0, tokenHash)
                .bind(1, now.toString())
                .map { r ->
                    Pair(
                        r.getColumn("id", String::class.java),
                        r.getColumn("user_id", String::class.java),
                    )
                }
                .firstOrNull()
        } ?: return false

        val (tokenId, userId) = row
        val newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt())

        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate("UPDATE users SET password_hash = ?, updated_at = ? WHERE id = ?")
                .bind(0, newHash).bind(1, now.toString()).bind(2, userId).execute()
            handle.createUpdate("UPDATE password_reset_tokens SET used_at = ? WHERE id = ?")
                .bind(0, now.toString()).bind(1, tokenId).execute()
        }

        logger.info("Password reset successfully for user $userId")
        return true
    }

    /** Returns all active (unused, unexpired) tokens for admin display. */
    fun listActiveTokens(): List<Triple<String, String, String>> {
        val now = Instant.now().toString()
        return jdbi.withHandle<List<Triple<String, String, String>>, Exception> { handle ->
            handle.createQuery(
                """
                SELECT prt.id, u.username, prt.expires_at
                FROM password_reset_tokens prt
                INNER JOIN users u ON prt.user_id = u.id
                WHERE prt.used_at IS NULL AND prt.expires_at > ?
                ORDER BY prt.created_at DESC
                """,
            )
                .bind(0, now)
                .map { r ->
                    Triple(
                        r.getColumn("id", String::class.java),
                        r.getColumn("username", String::class.java),
                        r.getColumn("expires_at", String::class.java),
                    )
                }.list()
        }
    }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        rng.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hashToken(raw: String): String {
        // SHA-256 hex for fast indexed lookup — not bcrypt (tokens are long random, bcrypt unnecessary)
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
